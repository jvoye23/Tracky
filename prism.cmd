@echo off
rem PRISM, from cmd.exe and PowerShell.
rem
rem THIS FILE HOLDS NO LOGIC. It finds a POSIX shell and re-execs the engine
rem beside it -- `prism`, the same 700-line script macOS and Linux run. There is
rem one implementation of every verb, so this and it cannot drift.
rem
rem Git for Windows is not an extra dependency. git is already required, and the
rem pre-push hook PRISM installs is `#!/bin/sh` run by git's own bundled sh, so a
rem machine that can use PRISM at all already has the shell this needs.
rem
rem The exit status is propagated unchanged. `prism doctor` answers 2 for an
rem undetermined verdict and callers depend on it; a shim that swallowed that
rem would turn every refusal into a pass.

setlocal EnableExtensions

set "PRISM_ENGINE=%~dp0prism"
if not exist "%PRISM_ENGINE%" (
    echo prism: no engine beside this script.>&2
    echo   expected: %PRISM_ENGINE%>&2
    echo   prism.cmd must sit at the repository root, next to .prism\.>&2
    exit /b 2
)

set "PRISM_SH_FOUND="

rem 1. An explicit override, for a machine with an unusual layout. Like every
rem    other override in this framework it can only redirect the answer: a
rem    PRISM_SH that is not there falls through rather than disabling the search.
if defined PRISM_SH if exist "%PRISM_SH%" set "PRISM_SH_FOUND=%PRISM_SH%"

rem 2. sh on PATH, which is the case when the terminal was started from Git Bash.
if not defined PRISM_SH_FOUND (
    for /f "delims=" %%G in ('where.exe sh.exe 2^>nul') do (
        if not defined PRISM_SH_FOUND set "PRISM_SH_FOUND=%%G"
    )
)

rem 3. Derived from git itself rather than guessed. `where git` answers
rem    <install>\cmd\git.exe, and sh.exe lives one level up in bin or usr\bin --
rem    so this finds a Git for Windows installed anywhere, including a portable
rem    one on another drive.
if not defined PRISM_SH_FOUND (
    for /f "delims=" %%G in ('where.exe git.exe 2^>nul') do (
        if not defined PRISM_SH_FOUND (
            if exist "%%~dpG..\bin\sh.exe" set "PRISM_SH_FOUND=%%~dpG..\bin\sh.exe"
        )
        if not defined PRISM_SH_FOUND (
            if exist "%%~dpG..\usr\bin\sh.exe" set "PRISM_SH_FOUND=%%~dpG..\usr\bin\sh.exe"
        )
    )
)

rem 4. The default install locations, last.
if not defined PRISM_SH_FOUND (
    if exist "%ProgramFiles%\Git\bin\sh.exe" set "PRISM_SH_FOUND=%ProgramFiles%\Git\bin\sh.exe"
)
if not defined PRISM_SH_FOUND (
    if exist "%ProgramFiles(x86)%\Git\bin\sh.exe" set "PRISM_SH_FOUND=%ProgramFiles(x86)%\Git\bin\sh.exe"
)

if not defined PRISM_SH_FOUND (
    echo prism: no POSIX shell found, and PRISM's gates are shell scripts.>&2
    echo.>&2
    echo Install Git for Windows, which ships the sh.exe this needs:>&2
    echo   https://git-scm.com/download/win>&2
    echo.>&2
    echo git is already required -- the pre-push hook PRISM installs is run by>&2
    echo git's own sh -- so this is not an extra dependency, only a missing one.>&2
    echo If sh.exe is somewhere unusual, point PRISM_SH at it.>&2
    exit /b 2
)

"%PRISM_SH_FOUND%" "%PRISM_ENGINE%" %*
exit /b %ERRORLEVEL%
