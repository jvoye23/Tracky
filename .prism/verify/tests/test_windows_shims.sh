# Tests for prism.cmd — the Windows entry point.
# Sourced by run-tests.sh.
#
# WHAT THIS FILE CANNOT DO, said first. cmd.exe and PowerShell do not exist on
# the machine this suite usually runs on, so the shim is never executed here. Its
# BEHAVIOUR was checked by hand on Windows 11 ARM, and the results are recorded
# as assertions below wherever they can be.
#
# What is checked here is the part that rots silently: that it ships, that every
# step of the search order survives, that the exit status propagates, and above
# all that it contains NO LOGIC beyond finding a shell. The whole argument for a
# shim rather than a port is that there is one implementation of every verb; a
# shim that grew a verb of its own would have quietly ended that.

WS_CMD="$HOOK_DIR/../prism.cmd"
WS_PS1="$HOOK_DIR/../prism.ps1"

assert_path_exists "$WS_CMD" "prism.cmd ships"

# --- AND prism.ps1 DOES NOT, which was measured rather than decided ---------
#
# A .ps1 shipped for two commits. On Windows 11 with the default `Restricted`
# execution policy:
#
#   PS> .\prism status
#   .\prism : File ...\prism.ps1 cannot be loaded because running scripts is
#   disabled on this system.
#
# PowerShell resolves `.\prism` to the .ps1 AHEAD of the .cmd, so merely having
# one turned the shortest invocation into a security exception. Removing it:
#
#   PS> .\prism status
#   prism: no .prism/verify/lib beside this script
#
# It offered nothing the .cmd did not -- same engine, same arguments -- and cost
# the one thing a Windows user would type first. Asserted, not remembered,
# because "add a PowerShell version too" is an obvious-looking improvement that
# would silently undo it.
assert_path_absent "$WS_PS1" \
    "no prism.ps1 ships: PowerShell prefers it over the .cmd and then refuses it"

ws_cmd=$(cat "$WS_CMD" 2>/dev/null)

# --- CRLF, and not by accident ---------------------------------------------
#
# cmd.exe is genuinely sensitive to line endings inside a multi-line construct,
# and this file is full of them. .gitattributes pins *.cmd; this asserts the
# shipped BYTES, because an attribute only governs what git does.
ws_crs=$(tr -cd '\r' < "$WS_CMD" 2>/dev/null | wc -c | tr -d ' ')
assert_ne "0" "$ws_crs" "prism.cmd ships with CRLF line endings"

# --- the search order, every step ------------------------------------------
#
# Each exists because the one before it is not always enough. Losing any makes
# the shim work on the author's machine and fail on somebody else's, which is
# the failure mode this whole port has been about.
for ws_step in "PRISM_SH" "where.exe sh.exe" "where.exe git.exe" "ProgramFiles"; do
    assert_contains "$ws_cmd" "$ws_step" "prism.cmd tries $ws_step"
done

# Derived from git, not guessed: git.exe sits in <install>\cmd and sh.exe one
# level up. That is what finds a portable Git, or one on another drive.
assert_contains "$ws_cmd" 'bin\sh.exe' "prism.cmd derives sh.exe from git's own location"

# --- the exit status survives ----------------------------------------------
#
# `prism doctor` answers 2 for an undetermined verdict and callers depend on it.
# A shim that swallowed the status would turn every refusal into a pass -- this
# framework's own failure mode, one layer above it. Confirmed by hand on
# Windows: $LASTEXITCODE was 2 after the engine refused.
assert_contains "$ws_cmd" "exit /b %ERRORLEVEL%" "prism.cmd propagates the exit status"

# --- no shell, no silence --------------------------------------------------
assert_contains "$ws_cmd" "git-scm.com/download/win" \
    "a missing shell names the one thing that fixes it"
assert_contains "$ws_cmd" "exit /b 2" "and refuses rather than continuing"

# --- NO LOGIC. This is the property the whole design rests on. --------------
#
# Scanned over CODE, not prose: the file explains at length why it holds no
# logic, and "exit status" contains the verb `status`.
ws_cmd_code=$(grep -viE '^\s*rem( |$)' "$WS_CMD" 2>/dev/null)
for ws_verb in "baseline" "coverage" "promote" "detekt" "ktlint" "konsist" "status"; do
    assert_not_contains "$ws_cmd_code" "$ws_verb" "prism.cmd knows nothing about '$ws_verb'"
done

assert_contains "$ws_cmd" '%~dp0prism' "prism.cmd runs the engine beside it"
