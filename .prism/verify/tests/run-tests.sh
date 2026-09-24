#!/bin/sh
# Minimal POSIX test runner for the prism-verify hooks.
# Usage: sh tests/run-tests.sh [test_file.sh ...]
# With no arguments it runs every tests/test_*.sh.
set -u

TESTS_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
HOOK_DIR=$(dirname "$TESTS_DIR")
export HOOK_DIR

# What the interpreter is called here, because this runner executes the Python
# suites itself. Sourced explicitly rather than inherited: the suites are
# SOURCED into this shell, so test_affected.sh -- early in alphabetical order --
# was exporting PRISM_PY as a side effect and the runner was reading a value it
# got by accident from a test. That works until the alphabet changes.
. "$HOOK_DIR/lib/platform.sh"

PRISM_TEST_PASSED=0
PRISM_TEST_FAILED=0
PRISM_TEST_SKIPPED=0

# skip_case <reason> -- a case that CANNOT hold on this platform.
#
# Not a pass and not a failure. The shell suite had no third answer, so a case
# whose premise does not exist here had to be written as one of the two, and both
# are lies: NTFS has no executable bit, so "setup repairs a hook git would skip"
# asserts a repair that can never fire. Reported by name, and counted, because a
# silently-absent assertion is the failure mode the two guards below exist for.
skip_case() {
    PRISM_TEST_SKIPPED=$((PRISM_TEST_SKIPPED + 1))
    printf 'SKIP %s\n' "$1"
}

assert_eq() {
    # assert_eq <expected> <actual> <label>
    if [ "$1" = "$2" ]; then
        PRISM_TEST_PASSED=$((PRISM_TEST_PASSED + 1))
    else
        PRISM_TEST_FAILED=$((PRISM_TEST_FAILED + 1))
        printf 'FAIL %s\n  expected: [%s]\n  actual:   [%s]\n' "$3" "$1" "$2"
        # Same reason as assert_contains: two strings that render identically
        # and compare unequal differ by a byte the terminal will not show.
        if [ "$(printf '%s' "$1" | tr -d '\r\t ')" = "$(printf '%s' "$2" | tr -d '\r\t ')" ]; then
            printf '    (they differ only in whitespace or control bytes)\n'
            printf '    expected bytes:\n'
            prism_show_bytes "$1"
            printf '    actual bytes:\n'
            prism_show_bytes "$2"
        fi
    fi
}

assert_ne() {
    # assert_ne <unwanted> <actual> <label>
    if [ "$1" != "$2" ]; then
        PRISM_TEST_PASSED=$((PRISM_TEST_PASSED + 1))
    else
        PRISM_TEST_FAILED=$((PRISM_TEST_FAILED + 1))
        printf 'FAIL %s\n  expected anything but: [%s]\n' "$3" "$1"
    fi
}

# WHEN A CONTAINS-ASSERTION FAILS, SHOW THE BYTES.
#
# A failure reported as text is unreadable when the cause is a byte you cannot
# see. Measured on Windows: `expected to contain: [:core:domain:assemble]` /
# `in: [staticAnalysis :core:domain:assemble ...]` -- the needle plainly inside
# the haystack, which a `case` glob cannot miss. Rendered as text those two
# strings are identical to the passing ones; the difference is invisible.
#
# So on failure only, both sides are dumped with `od -c`, which is POSIX and
# shows \r, \t and any stray control character by name. Only on failure,
# because on a green run this would be tens of megabytes of octal.
prism_show_bytes() {
    printf '%s' "$1" | od -c | sed 's/^/      /' | head -12
}

assert_contains() {
    # assert_contains <haystack> <needle> <label>
    case "$1" in
        *"$2"*)
            PRISM_TEST_PASSED=$((PRISM_TEST_PASSED + 1))
            ;;
        *)
            PRISM_TEST_FAILED=$((PRISM_TEST_FAILED + 1))
            printf 'FAIL %s\n  expected to contain: [%s]\n  in: [%s]\n' "$3" "$2" "$1"
            # THE ONE-LINE DIAGNOSIS FIRST. Twelve lines of octal from the top of
            # a long haystack usually stop short of the interesting part, and the
            # reader still has to compare two dumps by eye. This answers the only
            # question that matters -- is the needle there, modulo bytes nobody
            # can see -- before any dump is offered.
            prism_ac_hay=$(printf '%s' "$1" | tr -d '\r\t\n ')
            prism_ac_needle=$(printf '%s' "$2" | tr -d '\r\t\n ')
            case "$prism_ac_hay" in
                *"$prism_ac_needle"*)
                    printf '    DIAGNOSIS: the needle IS present once \\r, \\t, \\n and\n'
                    printf '    spaces are removed, so the difference is an invisible byte\n'
                    printf '    rather than the content. The lines holding it, with CR shown as @:\n'
                    printf '%s' "$1" | tr '\r' '@' \
                        | grep -F "$(printf '%s' "$2" | cut -c1-12)" \
                        | sed 's/^/      /' | head -6
                    ;;
                *)
                    printf '    DIAGNOSIS: the needle is genuinely absent, not merely\n'
                    printf '    obscured -- this is a content difference.\n'
                    ;;
            esac
            printf '    needle bytes:\n'
            prism_show_bytes "$2"
            ;;
    esac
}

assert_not_contains() {
    # assert_not_contains <haystack> <needle> <label>
    case "$1" in
        *"$2"*)
            PRISM_TEST_FAILED=$((PRISM_TEST_FAILED + 1))
            printf 'FAIL %s\n  expected NOT to contain: [%s]\n  in: [%s]\n' "$3" "$2" "$1"
            ;;
        *)
            PRISM_TEST_PASSED=$((PRISM_TEST_PASSED + 1))
            ;;
    esac
}

# Added in 0.6.2, when handoff.py became the one thing in PRISM that deletes a
# file in a consumer's repository. "It is still there" and "it is gone" are the
# assertions that feature is made of, and spelling them as assert_eq on a
# subshell hid which half of the pair had failed.
assert_path_exists() {
    # assert_path_exists <path> <label>
    if [ -e "$1" ]; then
        PRISM_TEST_PASSED=$((PRISM_TEST_PASSED + 1))
    else
        PRISM_TEST_FAILED=$((PRISM_TEST_FAILED + 1))
        printf 'FAIL %s\n  expected to exist: [%s]\n' "$2" "$1"
    fi
}

assert_path_absent() {
    # assert_path_absent <path> <label>
    if [ -e "$1" ]; then
        PRISM_TEST_FAILED=$((PRISM_TEST_FAILED + 1))
        printf 'FAIL %s\n  expected to be gone: [%s]\n' "$2" "$1"
    else
        PRISM_TEST_PASSED=$((PRISM_TEST_PASSED + 1))
    fi
}

# A SPACE IN THE PATH USED TO SILENTLY RUN NOTHING.
#
# This was `files=$(ls "$TESTS_DIR"/test_*.sh)` followed by `for file in $files`,
# and an unquoted expansion word-splits. Run from a directory whose path contains
# a space -- `~/Documents/prism test/` -- every filename split at the space, the
# loop sourced `.../Documents/prism` (which does not exist), and the run ended:
#
#     --- prism
#     run-tests.sh: line 156: /Users/.../prism: No such file or directory
#
# and then it EXITED 0. Zero suites executed, zero failures counted, success
# reported. That is the silent green this framework exists to prevent, in the
# thing that verifies the framework.
#
# Found on Windows, where a space in a path is ordinary -- `My Documents`,
# `Program Files`, a user's full name -- but the defect was never
# Windows-specific and had been there the whole time.
#
# Fixed by never building a list of names as a string: the glob is iterated
# directly, so each pathname stays one word no matter what is in it.
prism_run_explicit="no"
if [ "$#" -gt 0 ]; then
    prism_run_explicit="yes"
fi

# EVERY ASSERTION A TEST CALLS MUST EXIST. `assert_ne` was called by
# test_preflight_detekt.sh for several releases and was never defined: sh
# printed "command not found" to stderr, the assertion did not run, and the
# suite still reported "0 failed". A test that cannot run and does not fail is
# the same silent green this whole framework exists to prevent, one level down.
#
# Checked BEFORE the files are sourced, so the run stops rather than reporting a
# total that quietly omits an assertion.
prism_undefined_assertions() {
    used=$(grep -ohE '\bassert_[a-z_]+' "$TESTS_DIR"/test_*.sh 2>/dev/null | sort -u)
    for name in $used; do
        if ! command -v "$name" >/dev/null 2>&1; then
            printf '%s ' "$name"
        fi
    done
}
# EVERY TEST CLASS MUST BE REACHABLE. test_coverage.py carried an
# `if __name__ == "__main__": unittest.main()` block in the MIDDLE of the file,
# with a class defined after it. Run as a script -- which is exactly how this
# runner runs the python suites -- the interpreter never reaches that class, so
# four assertions about the coverage verdict did not exist. The suite reported
# "55 passed" and none of them was those. Same silent green as an undefined
# assertion helper, one level up: the total was honest about what it ran and said
# nothing about what it did not.
prism_unreachable_classes() {
    for file in "$TESTS_DIR"/test_*.py; do
        [ -f "$file" ] || continue
        # The FIRST call, not the last: running as a script the interpreter
        # stops at whichever comes first, so a second one at the end of the file
        # rescues nothing.
        first_main=$(grep -n 'unittest.main(' "$file" | head -1 | cut -d: -f1)
        last_class=$(grep -n '^class ' "$file" | tail -1 | cut -d: -f1)
        [ -n "$first_main" ] || continue
        [ -n "$last_class" ] || continue
        if [ "$first_main" -lt "$last_class" ]; then
            printf '%s ' "$(basename "$file")"
        fi
    done
}
unreachable=$(prism_unreachable_classes)
if [ -n "$unreachable" ]; then
    printf 'FAIL test class(es) defined AFTER unittest.main() in: %s\n' "$unreachable"
    printf '  Run as a script, the interpreter exits at that call and never\n'
    printf '  defines them. Those tests do not run and nothing fails.\n'
    exit 1
fi

undefined=$(prism_undefined_assertions)
if [ -n "$undefined" ]; then
    printf 'FAIL undefined assertion helper(s) called by the shell suite: %s\n' \
        "$undefined"
    printf '  Every assert_* a test calls must be defined in this file, or the\n'
    printf '  assertion silently does not run and the suite still reports 0 failed.\n'
    exit 1
fi

prism_suites_run=0
if [ "$prism_run_explicit" = "yes" ]; then
    for file in "$@"; do
        [ -f "$file" ] || { printf 'FAIL no such test file: %s\n' "$file"; exit 1; }
        printf '\n--- %s\n' "$(basename "$file")"
        prism_suites_run=$((prism_suites_run + 1))
        # shellcheck disable=SC1090
        . "$file"
    done
else
    for file in "$TESTS_DIR"/test_*.sh; do
        [ -f "$file" ] || continue
        printf '\n--- %s\n' "$(basename "$file")"
        prism_suites_run=$((prism_suites_run + 1))
        # PER-SUITE COUNTS, so two machines can be compared line by line.
        #
        # Windows and macOS ran the same archive to 1141 and 1144. Two of the
        # three were the exec-bit skip; the third took a request and a round
        # trip to locate, for a difference that was never a failure. A total is
        # not enough to tell "this platform skipped something" from "this
        # platform is missing an assertion".
        prism_suite_before=$((PRISM_TEST_PASSED + PRISM_TEST_FAILED + PRISM_TEST_SKIPPED))
        # shellcheck disable=SC1090
        . "$file"
        printf '    %s assertions\n' \
            "$((PRISM_TEST_PASSED + PRISM_TEST_FAILED + PRISM_TEST_SKIPPED - prism_suite_before))"
    done
fi

# The python suites are counted here too, so the total this script prints is
# the project's whole hook-test count rather than only the shell half. They
# were previously run by hand, which made "the suite passes" ambiguous about
# which suite. Skipped entirely when a specific file was named.
if [ "$prism_run_explicit" = "no" ]; then
    for file in "$TESTS_DIR"/test_*.py; do
        [ -f "$file" ] || continue
        printf '\n--- %s\n' "$(basename "$file")"
        prism_suites_run=$((prism_suites_run + 1))
        output=$("$PRISM_PY" "$file" 2>&1)
        status=$?
        printf '    %s assertions\n' \
            "$(printf '%s\n' "$output" | sed -n 's/^Ran \([0-9]*\) test.*/\1/p')"
        count=$(printf '%s\n' "$output" | sed -n 's/^Ran \([0-9]*\) test.*/\1/p')
        [ -n "$count" ] || count=0
        if [ "$status" -eq 0 ]; then
            PRISM_TEST_PASSED=$((PRISM_TEST_PASSED + count))
        else
            PRISM_TEST_FAILED=$((PRISM_TEST_FAILED + count))
            printf '%s\n' "$output"
        fi
    done
fi

# A RUN THAT EXECUTED NOTHING IS NOT A PASS.
#
# The count above is honest about what ran and says nothing about what did not,
# so "0 passed, 0 failed" and a clean exit is what a broken runner looks like --
# and is what the space-in-the-path defect produced for as long as it existed.
# The same argument as prism_undefined_assertions and prism_unreachable_classes
# above, one level up: this file's own verdict needs a floor.
if [ "$prism_suites_run" -eq 0 ]; then
    printf '\nFAIL no test suite ran at all.\n'
    printf '  %s holds no test_*.sh or test_*.py, or the path could not be read.\n' \
        "$TESTS_DIR"
    exit 1
fi

if [ "$PRISM_TEST_PASSED" -eq 0 ] && [ "$PRISM_TEST_FAILED" -eq 0 ]; then
    printf '\nFAIL %s suite(s) ran and produced no assertions at all.\n' \
        "$prism_suites_run"
    printf '  A suite that asserts nothing cannot fail, so this is not a pass.\n'
    exit 1
fi

# THE SUITE COUNT GOES FIRST. Two callers -- verify-payload check 6 and
# build-zip step 6 -- read this with `case "$line" in *"0 failed")`, so the line
# must END there. Appending "(38 suites)" broke both immediately, which is the
# checks doing their job on a change to the thing they check.
# Skips before the failure count, because two callers read this line with
# `case "$line" in *"0 failed")` and it has to END there.
if [ "$PRISM_TEST_SKIPPED" -gt 0 ]; then
    printf '\n%s suites, %s passed, %s skipped, %s failed\n' \
        "$prism_suites_run" "$PRISM_TEST_PASSED" "$PRISM_TEST_SKIPPED" \
        "$PRISM_TEST_FAILED"
else
    printf '\n%s suites, %s passed, %s failed\n' \
        "$prism_suites_run" "$PRISM_TEST_PASSED" "$PRISM_TEST_FAILED"
fi
[ "$PRISM_TEST_FAILED" -eq 0 ] || exit 1
