# Tests for the line-ending pin.
# Sourced by run-tests.sh.
#
# PRISM places files that something EXECUTES: the `.sh` gates, the pre-push hook,
# prism-doctor, and the `prism` command itself. An executed file cannot survive a
# line-ending rewrite -- a `#!/bin/sh` whose first line ends in \r is not a
# shebang, and the kernel reports `bad interpreter: /bin/sh^M`.
#
# Pinned in two places, because there are two ways CRLF arrives:
#
#   what PRISM WRITES      lib/render.py's write_atomically, newline="\n"
#   what git CHECKS OUT    the .gitattributes fragment, text eol=lf
#
# The second is the one that reaches somebody who did not run the install. The
# first cannot be observed on a POSIX machine at all -- Python only translates on
# Windows -- so it is asserted at the source, the way check 2k asserts the
# resolvers. The second reproduces here exactly, which is what the first half of
# this file measures.

LE_TEMPLATES="$HOOK_DIR/../templates"
LE_FRAGMENT="$LE_TEMPLATES/gitattributes.fragment"

assert_path_exists "$LE_FRAGMENT" "the .gitattributes fragment ships"

# --- the hazard, reproduced ------------------------------------------------
#
# Asserted FIRST and on purpose: if `core.autocrlf=true` did not mangle a
# checkout on the machine running this suite, every assertion below it would pass
# for free and prove nothing. This is the control.
le_repo() {
    le_dir=$(mktemp -d)
    git -C "$le_dir" init -q -b main
    git -C "$le_dir" config core.autocrlf true
    git -C "$le_dir" config user.email t@example.com
    git -C "$le_dir" config user.name t
    printf '%s' "$le_dir"
}

# le_roundtrip <repo> — commit a shebanged script, delete it, check it out again,
# and print `crlf` or `lf` for what came back.
le_roundtrip() {
    printf '#!/bin/sh\nexit 0\n' > "$1/gate.sh"
    git -C "$1" add -A >/dev/null 2>&1
    git -C "$1" commit -qm probe >/dev/null 2>&1
    rm -f "$1/gate.sh"
    git -C "$1" checkout -q -- gate.sh 2>/dev/null
    if od -c "$1/gate.sh" 2>/dev/null | grep -q '\\r'; then
        printf 'crlf'
    else
        printf 'lf'
    fi
}

le_bare=$(le_repo)
assert_eq "crlf" "$(le_roundtrip "$le_bare")" \
    "CONTROL: core.autocrlf mangles a shebang when nothing pins it"
rm -rf "$le_bare"

le_pinned=$(le_repo)
cp "$LE_FRAGMENT" "$le_pinned/.gitattributes"
assert_eq "lf" "$(le_roundtrip "$le_pinned")" \
    "the fragment keeps a .sh file LF through a checkout"
rm -rf "$le_pinned"

# --- the extensionless executables ----------------------------------------
#
# No glob reaches these, so each needs a line of its own. They are the three
# files where CRLF is not a cosmetic problem: prism-doctor and prism-hook are
# run as `sh <path>`, and pre-push is run by git.
le_attrs=$(cat "$LE_FRAGMENT")
for le_name in \
    ".prism/verify/prism-doctor" \
    ".prism/adapters/lib/prism-hook" \
    ".prism/adapters/git/pre-push" \
    "prism" \
    "gradlew"
do
    assert_contains "$le_attrs" "$le_name" \
        "the fragment names $le_name, which no glob covers"
done

assert_contains "$le_attrs" "*.sh" "the fragment covers the gate scripts"
assert_contains "$le_attrs" "*.py" "the fragment covers the engine's Python"

# The other direction. cmd.exe is genuinely sensitive to line endings inside a
# multi-line construct, so normalising a batch file to LF can fail in ways that
# are hard to read.
assert_contains "$le_attrs" "eol=crlf" "batch files are pinned the other way"

# --- what PRISM writes itself ---------------------------------------------
#
# Untestable by observation on POSIX: Python translates \n only on Windows, so a
# missing newline="\n" is invisible here and fatal there. Asserted at the source
# instead. Every text-mode write in the payload that produces a file something
# reads back has to name it.
le_unpinned=""
for le_py in "$HOOK_DIR/lib/render.py" "$HOOK_DIR/lib/place.py" \
             "$HOOK_DIR/lib/scope.py" "$HOOK_DIR/lib/baseline.py" \
             "$HOOK_DIR/lib/coverage.py"
do
    [ -f "$le_py" ] || continue
    # The `with ... as ...:` SHAPE, not the substring `open(..., "w")`.
    #
    # A comment explaining why this rule exists necessarily quotes the thing it
    # forbids, and the looser grep flagged place.py's own docstring for saying
    # `open(path, "w") does that there`. Same false positive as check 2k's
    # assertion lines: a file that explains a rule must be allowed to name it.
    le_hits=$(grep -nE 'with (os\.fdopen|open)\(.*"w".*\) as [A-Za-z_]+:' \
        "$le_py" 2>/dev/null | grep -v 'newline=' | grep -v '"wb"')
    if [ -n "$le_hits" ]; then
        le_unpinned="$le_unpinned
$(basename "$le_py"): $le_hits"
    fi
done
assert_eq "" "$le_unpinned" \
    "every text write in the payload Python names newline (see the list above)"

# And the reason, stated where somebody editing render.py will read it.
assert_contains "$(cat "$HOOK_DIR/lib/render.py")" 'newline="\n"' \
    "write_atomically pins the newline, because it places the gates"
