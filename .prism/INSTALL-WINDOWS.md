# PRISM on Windows

Everything in [INSTALL.md](INSTALL.md), [INSTALL-GREENFIELD.md](INSTALL-GREENFIELD.md)
and [INSTALL-BROWNFIELD.md](INSTALL-BROWNFIELD.md) applies here unchanged. This
chapter is the short list of things that are **different**, and the shorter list
of things that will **bite**.

Every claim below was measured on Windows 11 ARM under Git Bash, with a native
Windows Python 3.14. Where something was not measured, it says so.

---

## What you need

| | |
|---|---|
| **Git for Windows** | ships the `sh.exe` the engine runs under. **Not an extra requirement:** the `pre-push` hook PRISM installs is `#!/bin/sh` and git runs it with its own bundled `sh`, so a machine that can use PRISM already has the shell. |
| **A Python 3.8+** | under **any** of `python3`, `python`, or `py -3`. The python.org installer provides `python` and `py` and no `python3`; that is fine. PRISM resolves whichever name works. |
| **A JDK** | whatever Android Studio already installed. |

There is no PowerShell module, no MSI, and nothing to add to PATH.

---

## Installing

Work in **Git Bash**, not PowerShell. Put `prism-verify` in the repository root:

```bash
./prism-verify
```

If that does not run, name the interpreter — this is the ordinary Windows case,
because the file's `#!/usr/bin/env python3` line is a POSIX convention Windows
does not read:

```bash
python prism-verify
```

Everything else is the same as any other platform: preflight, unpack, choose the
agent, hand over.

**If it says `no POSIX shell found`**, it searched four places and none had
`sh.exe`. Point it at yours and tell us where it was:

```bash
PRISM_SH=/c/Program\ Files/Git/bin/sh.exe ./prism-verify
```

---

## Running it afterwards

All three of these work, and all three run the same engine:

```
Git Bash     $ ./prism status
PowerShell   PS> .\prism status
cmd          > prism.cmd status
```

`.\prism` from PowerShell resolves to `prism.cmd`, a shim that finds `sh.exe` and
re-execs the same script macOS and Linux run. It contains no logic of its own —
there is one implementation of every verb, so the platforms cannot drift.

**Exit statuses survive the shim.** `prism doctor` answers `2` for an
undetermined verdict, and `$LASTEXITCODE` carries it back. Anything scripting
PRISM on Windows can rely on that.

Gradle is the one thing you type differently, because `./gradlew` is a POSIX
script and cmd cannot run it:

```
Git Bash     $ ./gradlew staticAnalysis
PowerShell   PS> .\gradlew.bat staticAnalysis
```

PRISM prints whichever form your platform needs in its own messages.

---

## What differs, and what will bite

### There is no `prism.ps1`, and that is deliberate

PowerShell resolves `.\prism` to a `.ps1` **ahead of** a `.cmd`. A PowerShell
shim therefore *shadowed* the working one, and the default `Restricted`
execution policy then refused it:

```
.\prism : File ...\prism.ps1 cannot be loaded because running scripts is
disabled on this system.
```

Shipping it turned the shortest, most obvious command into a security exception.
Removing it made `.\prism status` work with nothing configured. If you want
PowerShell scripts generally, that is your own `Set-ExecutionPolicy` decision and
PRISM does not need it.

### Line endings, pinned twice

`core.autocrlf=true` is what several Windows git installers set, and it rewrites
LF to CRLF on checkout. A `#!/bin/sh` whose first line ends `\r` is not a
shebang — the kernel looks for an interpreter named `/bin/sh\r`:

```
bad interpreter: /bin/sh^M
```

The file most exposed is `.git/hooks/pre-push`, so the failure would land on the
one enforcement path that cannot be bypassed. The install appends a
`.gitattributes` fragment pinning `eol=lf` on everything PRISM executes, and
PRISM writes those files with LF itself. You do not need to change your git
config.

### `git push` gates you with no agent at all

The `pre-push` hook is run by **git's own `sh`**, not your shell's. So from
PowerShell, from cmd, from Git Bash, or from an IDE, the push is gated. This was
confirmed on Windows: a planted violation was refused at `VERIFICATION GATE 0`.

### Agent hooks during a turn — unmeasured

The Stop gate and the PostToolUse ktlint hook are registered as
`sh "$..." --harness … --gate …`, which requires your agent to run hook commands
through a POSIX shell. **`sh` is not on PATH from PowerShell or cmd** — that was
measured — so a harness using either cannot start the hook.

Whether any given harness does is not yet known. The framework's harness
capability matrix records it as **unprobed** rather than guessing — that document
lives in the source repository rather than in your install, because it is about
the five agents rather than about your project.

**What this costs if it turns out badly:** the gates that fire *during* a turn
would not, and you would meet violations at `git push` instead of at the edit
that caused them. Later, and louder, but never absent — that is the Tier 2
behaviour PRISM already ships for two harnesses.

### The executable bit does not exist on NTFS

`chmod +x` is accepted and changes nothing, so PRISM's repair for a
non-executable git hook can never fire there. Git for Windows does not consult
the bit either, so the hook runs regardless. `prism doctor` does not report this
as a fault, and the test suite skips that case by name rather than asserting
something impossible.

---

## Verifying the install

```bash
./prism doctor
sh .prism/verify/tests/run-tests.sh
```

The doctor reports which interpreter it found — `python`, `py` or `python3`,
whichever your machine has.

The suite is PRISM's own, and it runs from the payload with no Gradle project
present. On Windows expect:

```
37 suites, 1141 passed, 1 skipped, 0 failed
```

The skip is the executable-bit case above. **`0 failed` is what matters**, not
the count; macOS reports three more assertions because it can run that case.

---

## If something is wrong

| Symptom | Cause |
|---|---|
| `bad interpreter: /bin/sh^M` | a file reached you with CRLF. `.gitattributes` should prevent it; if it did not, tell us which file. |
| `no POSIX shell found` | `sh.exe` is somewhere the four-step search missed. `PRISM_SH` points at it. |
| `python3: command not found` | you are on a build older than 0.6.3. Every interpreter name is resolved from this release on. |
| `prism: no modules found` | this blames your `settings.gradle.kts` for what was once our bug. On a current build it means what it says. |
| `.ps1 cannot be loaded` | you are invoking a PowerShell script PRISM does not ship. Use `.\prism` or `.\prism.cmd`. |

Quote `./prism-verify --version` in anything you report.
