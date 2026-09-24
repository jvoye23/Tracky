#!/bin/sh
# What platform this is. Sourced, never executed: defines functions and four
# variables, runs nothing that changes the repository.
#
# WHY THIS FILE EXISTS. The engine is POSIX shell and Git for Windows ships a
# POSIX shell, so the scripts themselves are portable -- there is not one
# bashism in the payload. What is not portable is the set of PROGRAMS they
# assume: `python3`, `shasum`, `./gradlew`, and `mktemp -t`. Each of those is a
# name that exists on macOS and does not exist, or does not mean the same thing,
# on a Windows machine that has everything PRISM actually requires.
#
# Before this file, those names were spelled out at roughly sixty call sites.
# Resolving them there would mean sixty chances to spell it differently, so they
# are resolved once, here, and every caller reads the answer.
#
# THE FAILURE MODE THIS INHERITS. This sits on the production decision path with
# config.sh, and it takes that file's rule: an inability to ask the question is
# not an answer of "no". A gate that cannot find an interpreter must DENY and
# say why. It must never quietly become "nothing to check" -- push-gate.sh
# carries the scar from the time a broken python3 allowed EVERY push through.
#
# So when no interpreter can be found, PRISM_PY is set to a name that cannot
# exist, and the reason is printed to stderr once. The caller then fails loudly
# on a name it can search for, rather than on an empty string.

# Sourced from many places -- affected.sh alone is sourced by ten callers, and
# core/prism sources it three times in one run. Resolving PRISM_PY runs an
# interpreter to check its version, so doing that per source would add a process
# spawn to every gate for an answer that cannot change mid-run.
if [ -z "${PRISM_PLATFORM_LOADED:-}" ]; then

PRISM_PLATFORM_LOADED=1

# ---------------------------------------------------------------------------
# Which platform
# ---------------------------------------------------------------------------

# Git for Windows, MSYS2 and Cygwin all report a kernel name of their own. This
# is the only question asked of the platform directly; everything below is
# derived from it or from what is actually on PATH.
case "$(uname -s 2>/dev/null || printf 'unknown')" in
    MINGW*|MSYS*|CYGWIN*) PRISM_IS_WINDOWS=yes ;;
    *)                    PRISM_IS_WINDOWS=no ;;
esac

# ---------------------------------------------------------------------------
# The interpreter
# ---------------------------------------------------------------------------

# prism_python_works <command> -- true when that name runs and is new enough.
#
# "It is on PATH" is not the question. Two things answer to a python name and
# cannot run the engine: a Python 2 (`python` still means 2 on some machines),
# and Windows' App Execution Alias, a stub at %LOCALAPPDATA%\Microsoft\
# WindowsApps\python.exe that exists, is executable, and opens the Microsoft
# Store instead of running your code. Both pass `command -v`.
#
# The candidate has to PRINT A TOKEN, not merely exit 0. An exit status is the
# one thing a wrapper can produce without running anything -- test_platform.sh
# has a stub that ignores -c and exits 0, and an exit-status check accepted it.
# Requiring the token on stdout means the only way to pass is to have executed
# the statement, which is the actual question.
prism_python_works() {
    [ -n "${1:-}" ] || return 1
    command -v "$1" >/dev/null 2>&1 || return 1
    [ "$("$1" -c 'import sys; sys.stdout.write("prism-py-ok" if sys.version_info >= (3, 8) else "prism-py-old")' 2>/dev/null)" \
        = "prism-py-ok" ]
}

# The `py` launcher is the one candidate that is two words (`py -3`), and
# PRISM_PY has to stay a single token so that every call site can quote it.
# Quoting is not optional here: a Windows user's repository path contains
# spaces far more often than a macOS one does.
#
# So rather than carry `py -3` around, ask it which interpreter it would have
# run and carry that path instead. sys.executable is absolute, which also means
# the answer survives a PATH that changes underneath a long gate run.
prism_py_launcher_target() {
    command -v py >/dev/null 2>&1 || return 1
    prism_py_target=$(py -3 -c 'import sys; sys.stdout.write(sys.executable)' \
        2>/dev/null) || return 1
    [ -n "$prism_py_target" ] || return 1
    # A native-Python path is backslashed. Forward slashes are what the shell
    # around it can pass on without treating them as escapes.
    printf '%s' "$prism_py_target" | tr '\\' '/'
}

# PRISM_PYTHON is an override, for a machine with several interpreters and an
# opinion about which one, and for the test suite. Like PRISM_CONFIG in
# config.sh it can only redirect the answer, never disable the question: an
# override that does not run is refused rather than accepted on trust.
if [ -n "${PRISM_PYTHON:-}" ] && prism_python_works "$PRISM_PYTHON"; then
    PRISM_PY="$PRISM_PYTHON"
elif prism_python_works python3; then
    # The macOS and Linux answer, and the answer on a Windows machine whose
    # Python came from the Microsoft Store.
    PRISM_PY=python3
elif prism_python_works python; then
    # The python.org installer -- by far the most common way Python arrives on
    # Windows -- installs python.exe and py.exe and NO python3.exe. Every one of
    # PRISM's ~60 invocation sites named python3, and prism-doctor made
    # `command -v python3` a P0 hard failure, so the doctor refused to run at
    # all on a machine with a perfectly good Python on it.
    PRISM_PY=python
elif PRISM_PY=$(prism_py_launcher_target) && prism_python_works "$PRISM_PY"; then
    : # resolved to an absolute path by the launcher
else
    # Nothing runs. Say so once, here, rather than letting sixty call sites each
    # produce their own unexplained failure -- and set a name that cannot
    # resolve, so what the caller reports is searchable.
    PRISM_PY=prism-no-python-interpreter-found
    printf 'prism: no usable Python interpreter\n' >&2
    printf '  Tried, in order: $PRISM_PYTHON, python3, python, py -3.\n' >&2
    printf '  Every gate parses its configuration with Python, so none of them\n' >&2
    printf '  can verify anything until one of those names runs and reports\n' >&2
    printf '  version 3.8 or newer.\n' >&2
    printf '  On Windows, the python.org installer provides `python` and `py`\n' >&2
    printf '  but no `python3` -- either is enough.\n' >&2
fi

export PRISM_PY

# prism_have_python -- for a caller that reports rather than fails, like the
# doctor's P0 check, which has to distinguish "absent" from "present but broken".
prism_have_python() {
    [ "$PRISM_PY" != "prism-no-python-interpreter-found" ]
}

# PRISM_PY_LF -- the first lines of an INLINE Python program.
#
# The 18 library files under lib/ pin newline="\n" at import. An inline `-c`
# program is not one of them, so on Windows its stdout is still translated:
# `print("a\nb")` emits `a\r\nb\r\n`, `$( )` strips the final newline and keeps
# every \r, and the shell then compares a value that renders identically to the
# one it expected.
#
# Measured on Windows 11 ARM, the last of eight defects this port found:
#   expected:  : t o o l i n g : k o n s i s t \n  : t o o l ...
#   actual:    : t o o l i n g : k o n s i s t \r \n  : t o o l ...
#
# A shell variable rather than text pasted into each program, so the reason
# lives in one place and a call site grows by one token:
#
#     "$PRISM_PY" -c "$PRISM_PY_LF"'
#     import json
#     ...'
#
# Two adjacent quoted strings are one argument, and the single quotes still
# protect the program from the shell.
PRISM_PY_LF='import sys
if hasattr(sys.stdout, "reconfigure"): sys.stdout.reconfigure(newline="\n")
'
export PRISM_PY_LF

# ---------------------------------------------------------------------------
# The Gradle wrapper
# ---------------------------------------------------------------------------

# Resolved lazily, unlike the interpreter, because it depends on the repository
# and not only on the machine -- and platform.sh is sourced by two files before
# PRISM_ROOT exists. `gradle wrapper` writes both scripts, but a repository that
# committed only one of them is still a repository PRISM has to work in, so the
# preference is checked against the filesystem rather than assumed.
prism_gradlew_name() {
    if [ "$PRISM_IS_WINDOWS" = yes ]; then
        if [ -z "${1:-}" ] || [ -f "$1/gradlew.bat" ]; then
            printf 'gradlew.bat'
            return 0
        fi
    fi
    printf 'gradlew'
}

# prism_gradlew_path <root> -- what to execute.
prism_gradlew_path() {
    printf '%s/%s' "$1" "$(prism_gradlew_name "$1")"
}

# prism_gradlew_cmd [root] -- what to print at a human.
#
# Separate from the path because these two differ in a way that matters to
# somebody reading a denial. On a POSIX shell the leading `./` is required. In
# cmd and PowerShell it is wrong, and `gradlew.bat` alone is what works. A gate
# that denies a push and then prints a command the reader cannot run has not
# told them anything.
prism_gradlew_cmd() {
    if [ "$(prism_gradlew_name "${1:-}")" = "gradlew.bat" ]; then
        printf 'gradlew.bat'
    else
        printf './gradlew'
    fi
}

# ---------------------------------------------------------------------------
# Hashing
# ---------------------------------------------------------------------------

# prism_sha256 -- reads stdin, prints the hex digest and nothing else.
#
# `shasum` is a Perl script that arrives with macOS. Linux and Git for Windows
# ship `sha256sum` instead and no `shasum` at all, which took out both of
# affected.sh's fingerprints -- the one the coverage gate uses to decide whether
# a report still describes the code, and the one the Stop gate uses for the same
# question. A fingerprint that cannot be computed is not a stale report, it is
# no verdict.
#
# The last candidate is the reason this cannot fail: Python is already a hard
# prerequisite of every gate, so a machine that can run PRISM at all can hash.
# Resolved at source time, not on first use. Both call sites invoke this inside
# a command substitution -- `... | prism_sha256` in a `$(...)` -- which is a
# subshell, so a memo written in the function body is discarded the moment it
# returns: the probe would run again on every call, and any caller reading the
# variable under `set -u` would abort on an unset name. Two `command -v` calls at
# load time cost less and leave the answer reportable.
if command -v sha256sum >/dev/null 2>&1; then
    PRISM_SHA256_TOOL=sha256sum
elif command -v shasum >/dev/null 2>&1; then
    PRISM_SHA256_TOOL=shasum
else
    PRISM_SHA256_TOOL=python
fi
export PRISM_SHA256_TOOL

prism_sha256() {
    case "$PRISM_SHA256_TOOL" in
        sha256sum) sha256sum | cut -d' ' -f1 ;;
        shasum)    shasum -a 256 | cut -d' ' -f1 ;;
        # No PRISM_PY_LF here, and deliberately: this writes a hex digest with
        # sys.stdout.write and no newline at all, so there is nothing for a
        # platform to translate. Check 2m names it as the one exemption.
        *)         "$PRISM_PY" -c 'import hashlib,sys; sys.stdout.write(hashlib.sha256(sys.stdin.buffer.read()).hexdigest())' ;;
    esac
}

# ---------------------------------------------------------------------------
# Temporary files
# ---------------------------------------------------------------------------

# prism_tmpdir -- where temporary files go.
#
# TMPDIR is the POSIX name and is set under MSYS too, but a Windows shell may
# carry only TMP or TEMP, and /tmp does not exist outside the MSYS root.
prism_tmpdir() {
    for prism_tmp_candidate in "${TMPDIR:-}" "${TMP:-}" "${TEMP:-}" /tmp; do
        if [ -n "$prism_tmp_candidate" ] && [ -d "$prism_tmp_candidate" ]; then
            printf '%s' "${prism_tmp_candidate%/}"
            return 0
        fi
    done
    printf '.'
}

# prism_mktemp <prefix> / prism_mktemp_d <prefix>
#
# `mktemp -t prism-doctor-report` is the BSD spelling, where -t takes a PREFIX.
# GNU coreutils and Git for Windows read that argument as a TEMPLATE and want
# trailing X's, so the BSD form either warns or produces a name nobody expected.
# An explicit template is the spelling both agree on.
prism_mktemp() {
    mktemp "$(prism_tmpdir)/${1:-prism}.XXXXXX"
}

prism_mktemp_d() {
    mktemp -d "$(prism_tmpdir)/${1:-prism}.XXXXXX"
}

# ---------------------------------------------------------------------------
# Finding a program
# ---------------------------------------------------------------------------

# prism_resolve_command <name> -- prints the name that actually runs, or fails.
#
# `command -v claude` under MSYS appends `.exe` and nothing else, so a CLI
# installed as a `.cmd` shim is invisible to it. npm does exactly that: a global
# install writes `claude`, `claude.cmd` and `claude.ps1` side by side, and which
# of them a given shell can see depends on the shell.
#
# The name this prints is the name to EXECUTE, not just a yes/no, because the
# caller has to run the thing it found -- `exec claude` fails where
# `exec claude.cmd` works.
#
# This does not rescue a CLI that is not on PATH at all. Measured on Windows 11
# ARM: `claude` was visible to PowerShell and absent from Git Bash's PATH
# entirely, which is a different problem and not one a lookup can fix.
prism_resolve_command() {
    [ -n "${1:-}" ] || return 1
    if command -v "$1" >/dev/null 2>&1; then
        printf '%s' "$1"
        return 0
    fi
    [ "$PRISM_IS_WINDOWS" = yes ] || return 1
    for prism_rc_suffix in .cmd .bat .exe .com; do
        if command -v "$1$prism_rc_suffix" >/dev/null 2>&1; then
            printf '%s' "$1$prism_rc_suffix"
            return 0
        fi
    done
    return 1
}

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------

# prism_native_path <path> -- one spelling, so two paths can be compared.
#
# On Windows the same directory has three names, and PRISM was handling all
# three at once without noticing:
#
#   C:\Users\me\repo    what a native program prints
#   C:/Users/me/repo    what `git rev-parse --show-toplevel` prints
#   /c/Users/me/repo    what MSYS `pwd` prints
#
# core/prism took PRISM_HOME from `pwd` and affected.sh took PRISM_ROOT from
# git, then stripped one as a prefix of the other -- `${file#"$PRISM_ROOT"/}`.
# Different spellings of the same directory strip nothing, so the "relative"
# path stayed absolute and every path-keyed lookup missed.
#
# The MSYS spelling is the canonical one here, because it is the one the shell
# and the utilities around it can glob, test and pass to `find`.
prism_native_path() {
    [ -n "${1:-}" ] || return 0
    if [ "$PRISM_IS_WINDOWS" != yes ]; then
        printf '%s' "$1"
        return 0
    fi
    # cygpath ships with Git for Windows and knows about mount points, so it is
    # right where a substitution would only be nearly right.
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -u "$1" 2>/dev/null && return 0
    fi
    # Pure POSIX, deliberately. `sed 's/...\L\1.../'` would be one line, and
    # \L is a GNU extension -- on BSD sed it emits a literal L, so the fallback
    # in the portability file would itself have been unportable.
    prism_np=$(printf '%s' "$1" | tr '\\' '/')
    case "$prism_np" in
        [A-Za-z]:/*)
            prism_np_drive=$(printf '%s' "$prism_np" | cut -c1 | tr 'A-Z' 'a-z')
            printf '/%s%s' "$prism_np_drive" "${prism_np#?:}"
            ;;
        *)
            printf '%s' "$prism_np"
            ;;
    esac
}

fi  # PRISM_PLATFORM_LOADED
