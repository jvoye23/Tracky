# Tests for lib/platform.sh -- the resolver layer.
# Sourced by run-tests.sh.
#
# platform.sh answers four questions that have a different answer on Windows
# than on macOS: what is the interpreter called, what is the Gradle wrapper
# called, what can hash, and how is a path spelled. Only one of those four can
# be observed on the machine this suite usually runs on, so most of what follows
# forces the Windows branch and asserts the branch rather than the platform.
#
# What CANNOT be tested here, stated rather than implied: the cygpath branch of
# prism_native_path, because cygpath ships with Git for Windows and does not
# exist on macOS. These cases exercise the substitution fallback underneath it.
# The cygpath path is covered by the Windows checkpoint, by hand.

PLATFORM_LIB="$HOOK_DIR/lib/platform.sh"

platform_tmp=$(mktemp -d)

# The code, without the prose. Two assertions below say a construct must not
# appear in this file, and both first failed against the COMMENT that explains
# why it is absent -- which would have forced the explanation out of the file to
# keep the guard green. Stripping comment lines keeps both.
platform_code() {
    grep -v '^[[:space:]]*#' "$PLATFORM_LIB"
}

# Each case runs in its own subshell for the reason test_config.sh does, plus
# one of its own: platform.sh guards itself with PRISM_PLATFORM_LOADED so that
# ten callers sourcing it do not each re-run an interpreter. A leaked guard from
# an earlier suite would make every case below assert against a stale answer.
platform_run() {
    (
        unset PRISM_PLATFORM_LOADED
        . "$PLATFORM_LIB"
        eval "$1"
    ) 2>/dev/null
}

# Same, with stderr kept: the refusals have to say why.
platform_stderr() {
    (
        unset PRISM_PLATFORM_LOADED
        . "$PLATFORM_LIB"
        eval "$1"
    ) 2>&1 >/dev/null
}

# --- the interpreter --------------------------------------------------------

assert_ne "" "$(platform_run 'printf "%s" "$PRISM_PY"')" \
    "PRISM_PY is resolved to something"

assert_eq "0" "$(platform_run '"$PRISM_PY" -c "raise SystemExit(0)" >/dev/null 2>&1; printf "%s" "$?"')" \
    "PRISM_PY names an interpreter that actually runs"

assert_eq "ok" "$(platform_run '"$PRISM_PY" -c "import sys; print(\"ok\" if sys.version_info >= (3, 8) else \"old\")"')" \
    "PRISM_PY is 3.8 or newer"

assert_eq "yes" "$(platform_run 'prism_have_python && printf yes || printf no')" \
    "prism_have_python agrees on a machine that has one"

# PRISM_PY must stay a SINGLE token, because every call site quotes it and a
# Windows repository path contains spaces more often than a macOS one. This is
# why `py -3` is resolved through to sys.executable rather than carried as two
# words.
assert_eq "1" "$(platform_run 'set -- $PRISM_PY; printf "%s" "$#"')" \
    "PRISM_PY is one word, so it can be quoted at the call site"

# --- prism_python_works refuses what merely exists --------------------------

# Windows' App Execution Alias is a real executable at a real path that opens the
# Microsoft Store instead of running your code, and a `python` that is still
# Python 2 is a real interpreter that cannot run the engine. Both pass
# `command -v`. Neither may pass this.
printf '#!/bin/sh\nexit 9\n' > "$platform_tmp/store-stub"
chmod +x "$platform_tmp/store-stub"
printf '#!/bin/sh\nprintf "Python 2.7.18\\n"\nexit 0\n' > "$platform_tmp/python2-stub"
chmod +x "$platform_tmp/python2-stub"

assert_eq "no" \
    "$(platform_run "prism_python_works '$platform_tmp/store-stub' && printf yes || printf no")" \
    "a stub that exists and refuses to run code is not an interpreter"

assert_eq "no" \
    "$(platform_run "prism_python_works '$platform_tmp/python2-stub' && printf yes || printf no")" \
    "a stub that ignores -c and exits 0 is not an interpreter either"

assert_eq "no" \
    "$(platform_run 'prism_python_works no-such-interpreter-anywhere && printf yes || printf no')" \
    "a name that is not on PATH is not an interpreter"

assert_eq "no" \
    "$(platform_run 'prism_python_works "" && printf yes || printf no')" \
    "the empty string is not an interpreter"

# --- PRISM_PYTHON redirects the answer and cannot disable the question -------

# The same rule config.sh states for PRISM_CONFIG: an override may only redirect
# the read. An override that does not run is refused, not accepted on trust --
# otherwise `PRISM_PYTHON=/nonexistent` would be a skip switch for every gate.
# Against the interpreter this machine actually resolved, not against the literal
# `python3`: on a machine whose Python is called `python`, `command -v python3` is
# empty, so the assertion would have compared "" with "" and tested nothing.
platform_real_py=$(platform_run 'command -v "$PRISM_PY" 2>/dev/null || printf "%s" "$PRISM_PY"')
assert_eq "$platform_real_py" \
    "$(PRISM_PYTHON="$platform_real_py" platform_run 'printf "%s" "$PRISM_PY"')" \
    "a working PRISM_PYTHON is honoured"

assert_eq "yes" \
    "$(PRISM_PYTHON="$platform_tmp/store-stub" platform_run 'prism_have_python && printf yes || printf no')" \
    "a broken PRISM_PYTHON falls through to a working interpreter, it does not disable anything"

assert_ne "$platform_tmp/store-stub" \
    "$(PRISM_PYTHON="$platform_tmp/store-stub" platform_run 'printf "%s" "$PRISM_PY"')" \
    "a broken PRISM_PYTHON is not taken on trust"

# --- hashing ----------------------------------------------------------------

# The one fixed digest in this file. Both fingerprints in affected.sh depend on
# this function agreeing with itself across machines: a coverage report is
# "still describes the code" or "stale" by comparing one of these to a stored
# one, so a hasher that answered differently on Windows would mark every
# recorded report stale and produce no verdict at all.
SHA_ABC=ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad

assert_eq "$SHA_ABC" "$(platform_run 'printf abc | prism_sha256')" \
    "prism_sha256 agrees with the published SHA-256 of abc"

assert_eq "$SHA_ABC" \
    "$(platform_run 'PRISM_SHA256_TOOL=python; printf abc | prism_sha256')" \
    "the Python fallback produces the identical digest"

# Python is already a hard prerequisite of every gate, so the fallback cannot be
# absent -- which is what makes this function unable to fail for want of a tool.
assert_contains "$(cat "$PLATFORM_LIB")" 'hashlib.sha256' \
    "there is a hasher that cannot be missing"

# Resolved at load time, not memoised inside the function: both call sites run
# it inside a command substitution, and a subshell discards a memo. Under set -u
# a caller reading the name would then abort.
assert_ne "" "$(platform_run 'printf "%s" "$PRISM_SHA256_TOOL"')" \
    "PRISM_SHA256_TOOL is defined at source time, so set -u is safe"

# --- the Gradle wrapper -----------------------------------------------------

assert_eq "gradlew" "$(platform_run 'PRISM_IS_WINDOWS=no; prism_gradlew_name /repo')" \
    "a POSIX machine runs gradlew"

assert_eq "./gradlew" "$(platform_run 'PRISM_IS_WINDOWS=no; prism_gradlew_cmd /repo')" \
    "and is told to type ./gradlew, where the leading ./ is required"

: > "$platform_tmp/gradlew.bat"
assert_eq "gradlew.bat" \
    "$(platform_run "PRISM_IS_WINDOWS=yes; prism_gradlew_name '$platform_tmp'")" \
    "a Windows machine runs gradlew.bat when the repository carries one"

assert_eq "gradlew.bat" \
    "$(platform_run "PRISM_IS_WINDOWS=yes; prism_gradlew_cmd '$platform_tmp'")" \
    "and is told to type gradlew.bat, where a leading ./ would be wrong"

# `gradle wrapper` writes both scripts, but a repository that committed only the
# POSIX one is still a repository PRISM has to work in. Checked against the
# filesystem rather than assumed from the platform.
mkdir -p "$platform_tmp/posix-only"
: > "$platform_tmp/posix-only/gradlew"
assert_eq "gradlew" \
    "$(platform_run "PRISM_IS_WINDOWS=yes; prism_gradlew_name '$platform_tmp/posix-only'")" \
    "a repository with no gradlew.bat falls back rather than naming a missing file"

assert_eq "/repo/gradlew" \
    "$(platform_run 'PRISM_IS_WINDOWS=no; prism_gradlew_path /repo')" \
    "prism_gradlew_path joins the root and the name"

# --- path spelling ----------------------------------------------------------

assert_eq "/Users/me/repo" \
    "$(platform_run 'PRISM_IS_WINDOWS=no; prism_native_path /Users/me/repo')" \
    "a POSIX path is returned untouched"

assert_eq "/c/Users/me/repo" \
    "$(platform_run 'PRISM_IS_WINDOWS=yes; prism_native_path "C:\\Users\\me\\repo"')" \
    "a backslashed Windows path becomes the MSYS spelling"

assert_eq "/c/Users/me/repo" \
    "$(platform_run 'PRISM_IS_WINDOWS=yes; prism_native_path "C:/Users/me/repo"')" \
    "git rev-parse --show-toplevel output becomes the same MSYS spelling"

assert_eq "/c/Users/me/repo" \
    "$(platform_run 'PRISM_IS_WINDOWS=yes; prism_native_path "/c/Users/me/repo"')" \
    "an MSYS path is already canonical and survives unchanged"

# The point of the whole function: the three spellings of one directory converge,
# so `${file#"$PRISM_ROOT"/}` strips something.
assert_eq "same" \
    "$(platform_run 'PRISM_IS_WINDOWS=yes
       a=$(prism_native_path "C:\\r"); b=$(prism_native_path "C:/r"); c=$(prism_native_path "/c/r")
       if [ "$a" = "$b" ] && [ "$b" = "$c" ]; then printf same; else printf "$a|$b|$c"; fi')" \
    "all three spellings of one directory converge"

assert_eq "" "$(platform_run 'PRISM_IS_WINDOWS=yes; prism_native_path ""')" \
    "an empty path is empty, not a slash"

# A drive letter is case-insensitive on Windows but a shell string comparison is
# not, so the canonical form has to pick one case and always produce it.
assert_eq "$(platform_run 'PRISM_IS_WINDOWS=yes; prism_native_path "c:/r"')" \
    "$(platform_run 'PRISM_IS_WINDOWS=yes; prism_native_path "C:/r"')" \
    "the drive letter is normalised, so C: and c: are one directory"

# --- finding a program ------------------------------------------------------
#
# Measured on Windows 11 ARM: bootstrap.sh reported every agent CLI as "not
# found". `command -v claude` under MSYS appends `.exe` and nothing else, so an
# npm-installed `claude.cmd` is invisible to it -- npm writes `claude`,
# `claude.cmd` and `claude.ps1` side by side and which one a shell can see
# depends on the shell.
#
# What this prints is the name to EXECUTE, not a yes/no, because the caller has
# to run what it found: `exec claude` fails where `exec claude.cmd` works.

assert_eq "git" "$(platform_run 'prism_resolve_command git')" \
    "a command that is simply there resolves to its own name"

assert_eq "1" \
    "$(platform_run 'prism_resolve_command no-such-cli-anywhere >/dev/null 2>&1; printf "%s" "$?"')" \
    "a command that is nowhere fails rather than printing something"

assert_eq "1" \
    "$(platform_run 'prism_resolve_command "" >/dev/null 2>&1; printf "%s" "$?"')" \
    "the empty string is not a command"

# The .cmd case, which is the whole reason this function exists.
le_cmd_dir=$(mktemp -d)
printf '#!/bin/sh\nexit 0\n' > "$le_cmd_dir/faketool.cmd"
chmod +x "$le_cmd_dir/faketool.cmd"

assert_eq "faketool.cmd" \
    "$(PATH="$le_cmd_dir:$PATH" platform_run 'PRISM_IS_WINDOWS=yes; prism_resolve_command faketool')" \
    "on Windows a .cmd shim resolves, and resolves to the name that runs"

# And it must NOT invent one on a platform where a bare `faketool` genuinely does
# not exist -- a POSIX shell cannot execute faketool.cmd, so claiming it can
# would turn a clear "not found" into an exec failure further down.
assert_eq "1" \
    "$(PATH="$le_cmd_dir:$PATH" platform_run 'PRISM_IS_WINDOWS=no; prism_resolve_command faketool >/dev/null 2>&1; printf "%s" "$?"')" \
    "on POSIX a .cmd shim is not a command, and is not pretended into one"

rm -rf "$le_cmd_dir"

# --- temporary files --------------------------------------------------------

platform_file=$(platform_run 'prism_mktemp prism-selftest')
assert_path_exists "$platform_file" "prism_mktemp creates the file"
assert_contains "$platform_file" "prism-selftest" "prism_mktemp honours the prefix"
rm -f "$platform_file"

platform_dir=$(platform_run 'prism_mktemp_d prism-selftest')
assert_path_exists "$platform_dir" "prism_mktemp_d creates the directory"
rmdir "$platform_dir" 2>/dev/null

# `mktemp -t <prefix>` is the BSD spelling and means something else to GNU
# coreutils, which is what Git for Windows ships. An explicit template is what
# both agree on, so the template must be what this file uses.
assert_not_contains "$(platform_code)" "mktemp -t" \
    "no BSD-only mktemp -t survives in the portability file"

assert_ne "" "$(platform_run 'prism_tmpdir')" \
    "prism_tmpdir always answers"

assert_eq "0" \
    "$(platform_run 'unset TMPDIR TMP TEMP; d=$(prism_mktemp_d prism-notmp) && rmdir "$d"; printf "%s" "$?"')" \
    "a shell carrying none of TMPDIR, TMP or TEMP still gets a temporary directory"

# --- the file's own portability ---------------------------------------------

# Found by running it: `sed 's|^\([A-Za-z]\):/|/\L\1/|'` is one line and \L is a
# GNU extension. Under BSD sed it emits a literal L and produces /LC/Users, so
# the fallback inside the portability file was itself unportable. Asserted rather
# than remembered.
assert_not_contains "$(platform_code)" '\L' \
    "no GNU-only sed construct in the file whose job is portability"

assert_not_contains "$(platform_code)" 'SKIP' \
    "platform.sh carries no skip switch"

# The guard is what keeps ten callers from each spawning an interpreter.
assert_contains "$(cat "$PLATFORM_LIB")" 'PRISM_PLATFORM_LOADED' \
    "sourcing is guarded, so repeated sources cost nothing"

rm -rf "$platform_tmp"
