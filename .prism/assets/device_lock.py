#!/usr/bin/env python3
"""Serialize a device run behind a real mutex, then run it.

Two `connectedDebugAndroidTest` runs for the same test package on one emulator
kill each other. Observed on `:feature:files:presentation`: one run died with
"Instrumentation run failed due to Process crashed" while the competing run
finished green with 68 tests. No test was broken — the only casualty was the
`.ec` file the killed run could not pull back, after which the coverage gate
reported `no-device-run`, which reads as "you never ran it".

Retrying on failure does not fix this: by the time the first run reports a
failure it is already dead and its execution data is already gone. The only
thing that helps is not starting the second run until the first has finished,
which is what this does.

The lock is a kernel lock on a file under the hook's state dir, deliberately
NOT a lock directory: the kernel releases it when the holding process dies, so a
crashed or ^C-ed run cannot leave the device locked forever. macOS ships no
`flock(1)`, hence a script rather than a shell one-liner.

`fcntl.flock` where there is an fcntl, `msvcrt.locking` on Windows, where there
is not one at all. Before that second branch existed this module raised
ImportError at import time, so `./prism coverage` on a module with an
instrumented suite died before it could say anything -- on the one platform where
the whole point of the file, that a dead process cannot hold the device, still
holds exactly as well.

The lock file is shared by every caller — both coverage entry points, every
module — because the resource being protected is the *device*, not the module.

Usage:
    ANDROID_SERIAL="$SERIAL" python3 device_lock.py -- ./gradlew :mod:connectedDebugAndroidTest

Exits with the wrapped command's own exit status, so callers keep treating a
BUILD FAILED exactly as they did before.
"""
import argparse
import errno
import os
import subprocess
import sys
import tempfile
import time

# LF on stdout and stderr whatever the platform -- see lib/scope.py for why.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(newline="\n")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(newline="\n")

try:
    import fcntl
    msvcrt = None
except ImportError:  # Windows has neither fcntl nor flock
    fcntl = None
    import msvcrt

# Windows byte-range locks are MANDATORY, not advisory: a process holding byte 0
# would make the waiting process's read of the holder line fail with
# PermissionError instead of telling it who to wait for. So the lock sits a
# gigabyte past anything this file will ever contain, and the holder line at
# offset 0 stays readable by everyone.
_LOCK_OFFSET = 1 << 30
_LOCK_LENGTH = 1


def _take_lock(handle):
    """True when this process now holds the lock, False when somebody else does.

    Raises for anything that is not contention, because "could not tell" must
    not read as "it is free" -- two runs on one emulator destroy each other's
    coverage data, which is the whole reason this file exists.
    """
    if fcntl is not None:
        try:
            fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
            return True
        except OSError as error:
            if error.errno not in (errno.EACCES, errno.EAGAIN):
                raise
            return False
    handle.seek(_LOCK_OFFSET)
    try:
        msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, _LOCK_LENGTH)
        return True
    except OSError as error:
        if error.errno not in (errno.EACCES, errno.EAGAIN, errno.EDEADLOCK):
            raise
        return False


def _release_lock(handle):
    if fcntl is not None:
        fcntl.flock(handle.fileno(), fcntl.LOCK_UN)
        return
    handle.seek(_LOCK_OFFSET)
    msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, _LOCK_LENGTH)


def _default_lock_path():
    """<repo>/.prism/verify/state/device.lock, or a temp file.

    The engine no longer lives inside the harness's directory, and neither do
    its assets. This path carried no renameable string, so it survived the
    rename pass untouched and pointed at a directory that no install creates.
    """
    try:
        root = subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            capture_output=True, text=True, check=True,
        ).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        root = ""
    if root:
        return os.path.join(
            root, ".prism", "verify", "state", "device.lock"
        )
    # tempfile.gettempdir() rather than TMPDIR-or-/tmp: TMPDIR is not set on
    # Windows and /tmp does not exist there, so the os.makedirs() below failed on
    # the dirname of a path that could never be created.
    return os.path.join(tempfile.gettempdir(), "prism-device.lock")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--lock-file", default=None)
    parser.add_argument(
        "--timeout", type=float, default=3600.0,
        help="seconds to wait for the device before giving up (default 3600)",
    )
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()

    command = args.command
    if command and command[0] == "--":
        command = command[1:]
    if not command:
        parser.error("no command given; pass it after --")

    lock_path = args.lock_file or _default_lock_path()
    os.makedirs(os.path.dirname(lock_path), exist_ok=True)

    handle = open(lock_path, "a+", encoding="utf-8")
    deadline = time.monotonic() + args.timeout
    announced = False
    while True:
        if _take_lock(handle):
            break
        else:
            if not announced:
                handle.seek(0)
                holder = handle.read().strip() or "another run"
                # Said out loud: a silent wait is indistinguishable from a hang,
                # and this one can legitimately last for a whole test suite.
                print(
                    "prism: the device is busy — waiting for [%s]. "
                    "Two connected runs on one emulator destroy each other's "
                    "coverage data, so this waits rather than racing."
                    % holder,
                    file=sys.stderr, flush=True,
                )
                announced = True
            if time.monotonic() >= deadline:
                print(
                    "prism: gave up after %.0fs waiting for the device lock at %s"
                    % (args.timeout, lock_path),
                    file=sys.stderr,
                )
                return 75  # EX_TEMPFAIL: try again, nothing was run
            time.sleep(2.0)

    try:
        handle.seek(0)
        handle.truncate()
        handle.write("pid %d since %s: %s\n"
                     % (os.getpid(), time.strftime("%H:%M:%S"), " ".join(command)))
        handle.flush()
        return subprocess.call(command)
    finally:
        handle.seek(0)
        handle.truncate()
        handle.flush()
        _release_lock(handle)
        handle.close()


if __name__ == "__main__":
    sys.exit(main())
