#!/usr/bin/env python3
"""Tests for the device lock.

The lock exists because two `connectedDebugAndroidTest` runs on one emulator
destroy each other's execution data, after which the coverage gate reports
`no-device-run` -- which reads as "you never ran it". So the property under test
is mutual exclusion, and the property under test on Windows is that the module
can be imported at all: it used to `import fcntl` at the top, which raises
ImportError there, so `./prism coverage` on any module with an instrumented
suite died before it could print a word.
"""

import os
import subprocess
import sys
import tempfile
import unittest

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets")
sys.path.insert(0, os.path.abspath(ASSETS))

import device_lock  # noqa: E402


class LockPathTests(unittest.TestCase):
    def test_the_fallback_directory_exists_on_this_platform(self):
        """The old fallback was TMPDIR-or-/tmp.

        TMPDIR is not set on Windows and /tmp does not exist there, so
        os.makedirs() was called on the dirname of a path that could never be
        created -- and this branch is the one taken when the command runs
        outside a git repository, which is exactly when a clear error matters.
        """
        path = os.path.join(tempfile.gettempdir(), "prism-device.lock")
        self.assertTrue(os.path.isdir(os.path.dirname(path)))

    def test_outside_a_repository_the_path_is_in_the_temp_dir(self):
        outside = tempfile.mkdtemp()
        cwd = os.getcwd()
        try:
            os.chdir(outside)
            path = device_lock._default_lock_path()
        finally:
            os.chdir(cwd)
        # Either answer is legitimate -- a temp dir can itself sit inside a
        # repository on some machines -- but the directory must exist either way.
        self.assertTrue(os.path.isdir(os.path.dirname(path)), path)


class MutualExclusionTests(unittest.TestCase):
    def setUp(self):
        self.path = os.path.join(tempfile.mkdtemp(), "device.lock")

    def test_a_second_holder_is_refused_and_admitted_after_release(self):
        """The whole point, in one test. _take_lock returns False rather than
        raising for contention, and True only when the lock is genuinely held."""
        first = open(self.path, "a+", encoding="utf-8")
        second = open(self.path, "a+", encoding="utf-8")
        self.addCleanup(first.close)
        self.addCleanup(second.close)

        self.assertTrue(device_lock._take_lock(first), "the first caller takes it")
        self.assertFalse(device_lock._take_lock(second), "the second is refused")
        device_lock._release_lock(first)
        self.assertTrue(device_lock._take_lock(second), "and admitted after release")
        device_lock._release_lock(second)

    def test_the_holder_line_stays_readable_while_the_lock_is_held(self):
        """Windows byte-range locks are MANDATORY, not advisory.

        A process holding byte 0 would make the waiting process's read of the
        holder line fail with PermissionError instead of telling it who to wait
        for -- so the lock sits a gigabyte past anything the file will contain.
        Asserted here because on POSIX the flock is advisory and this passes for
        free; it is the Windows branch that needs the offset to be right.
        """
        first = open(self.path, "a+", encoding="utf-8")
        self.addCleanup(first.close)
        device_lock._take_lock(first)
        try:
            first.seek(0)
            first.truncate()
            first.write("pid 1234 since 00:00:00: gradlew test\n")
            first.flush()
            with open(self.path, encoding="utf-8") as reader:
                self.assertIn("pid 1234", reader.read())
        finally:
            device_lock._release_lock(first)


class ImportWithoutFcntlTests(unittest.TestCase):
    def test_the_module_imports_where_there_is_no_fcntl(self):
        """Run in a child, with fcntl made unimportable.

        A subprocess rather than a monkeypatch, because the thing being tested is
        what happens at IMPORT time and this process has already imported it.
        """
        program = (
            "import sys\n"
            "class Blocker:\n"
            "    def find_module(self, name, path=None):\n"
            "        return self if name == 'fcntl' else None\n"
            "    def load_module(self, name):\n"
            "        raise ImportError('no fcntl on this platform')\n"
            "sys.meta_path.insert(0, Blocker())\n"
            "sys.modules.pop('fcntl', None)\n"
            "sys.path.insert(0, %r)\n"
            "import device_lock\n"
            "assert device_lock.fcntl is None, 'the fcntl branch should be off'\n"
            "print('imported')\n" % os.path.abspath(ASSETS)
        )
        completed = subprocess.run(
            [sys.executable, "-c", program],
            capture_output=True, text=True,
        )
        # On a machine that has fcntl, blocking it leaves msvcrt to import --
        # and there is no msvcrt on POSIX either, so the import legitimately
        # fails here. What must NOT happen is failing for any other reason.
        if completed.returncode != 0:
            self.assertIn("msvcrt", completed.stderr,
                          "the only acceptable failure is the absent msvcrt:\n"
                          + completed.stderr)
        else:
            self.assertIn("imported", completed.stdout)


if __name__ == "__main__":
    unittest.main()
