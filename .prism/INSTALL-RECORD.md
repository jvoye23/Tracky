# PRISM install record — Tracky

PRISM 0.6.3+8bceece, installed 2026-09-24 by Claude Code (Opus 5.5) on branch
`46-Setup-Prism-Framework`. Preflight: PASS (python3 3.9.6, Kotlin 2.4.20, no
prior detekt). Final `prism-doctor`: PASS, 12 checks.

## 1. What landed

### Theirs (Tracky's files from now on)

| Path | What happened |
|---|---|
| `tooling/prism-rules/`, `tooling/konsist/` | placed (154 files); Kotlin plugin switched to the unversioned `id("org.jetbrains.kotlin.jvm")` |
| `tooling/konsist/.../ProjectScope.kt` | **edited after placement**: `productionFiles` / `testFiles` also treat KMP source sets ending in `Main` (`commonMain`, `androidMain`, …) as production — see *Deviations* |
| `build-logic/` | created from the payload (Tracky had no build-logic); it is the whole included build, so no `prism.buildLogic.ktlintTask` property |
| `build-logic/src/main/kotlin/DetektConventionPlugin.kt` | **edited**: on a KMP module, every `src/<sourceSet>/{kotlin,java}` dir is added to the plain `detekt` task's source |
| `build-logic/src/main/kotlin/StaticAnalysisConventionPlugin.kt` | **edited**: a module with `com.android.kotlin.multiplatform.library` wires `detektMainAndroid` into `staticAnalysis` and `detektBaselineMainAndroid` into `prismBaseline` |
| `detekt.yml` | new, rendered |
| `.editorconfig` | new (root) |
| `.gitignore` | PRISM block appended; `.gradle/` and `.kotlin/` dropped as already covered |
| `.gitattributes` | created |
| `gradle/libs.versions.toml` | added 8 versions, 14 libraries, 1 plugin (`kotlin-jvm`); no existing alias touched |
| `settings.gradle.kts` | `includeBuild("build-logic")`, `:tooling:prism-rules`, `:tooling:konsist` |
| `build.gradle.kts` | `alias(libs.plugins.kotlin.jvm) apply false`, `id("prism.static-analysis")` |
| `composeApp/build.gradle.kts`, `androidApp/build.gradle.kts` | `prism.ktlint`, `prism.detekt`, `prism.jacoco` |
| `prism`, `prism.cmd` | placed at the root, executable |
| 403 source files in `composeApp/` / `androidApp/` | reformatted by `./gradlew ktlintFormat` (+19,381 / −15,737) |
| `.claude/settings.json` | new; PRISM's four hooks (PreToolUse/Bash, PostToolUse/Edit\|Write, Stop, SubagentStop) |
| `.claude/agents/`, `.claude/skills/` | **symlinks into `.claude/pl-coding-skills`** (a clone of PL-Coding-GmbH/Skills). Placed through them at the human's choice: `skills/finalize/`, `skills/prism-burndown/` new; `bug-reviewer`, `issue-validator`, `security-reviewer`, `skip-checker` unchanged; **KEPT** (theirs stays, PRISM's beside it as `*.prism-new`): `agents/convention-reviewer.md`, `agents/mutation-worker.md`, `agents/verify-audit.md`, `skills/verify/SKILL.md` |

No `detekt.baseline.xml` or `ktlint.baseline.xml` exists: forced enforce records nothing.

### PRISM's

`.prism/` — `verify/`, `assets/`, `adapters/{git,lib,harnesses.json,README.md}`,
`templates/`, `prism.json`, `VERSION`, `VERIFY.md`, `RULES.md`, `INSTALL*.md`,
this file. `.git/hooks/pre-push` installed. No `.prism/scope.json`.

`classdirs.py sweep`: no superseded class-output directories, nothing removed.

## 2. Parameters

| Parameter | Value | How |
|---|---|---|
| `PACKAGE_ROOT` | `com.jvcs` | ANSWERED (derived): common prefix of `com.jvcs.tracky` (composeApp, 450 files) and `com.jvcs.androidapp` (androidApp, 5 files) |
| `MODULE_PACKAGE_ROOTS` | `composeApp` → `com.jvcs.tracky`, `androidApp` → `com.jvcs.androidapp` | ANSWERED (derived from settings.gradle.kts + packages) |
| `LAYER_PURITY_DOMAIN_PACKAGE` | `com.jvcs.tracky.core.domain` | ANSWERED (derived): the only `.core/.common/.shared.domain` candidate; others were `features.project.domain`, `features.auth.domain` |
| `PALETTE_OBJECT` | `none` | ANSWERED by probe |
| `EXTENDED_TOKEN_HOLDER` | `none` | ANSWERED by probe |
| `THEME_OBJECT` | `TrackyTheme` | ANSWERED by probe |
| `PALETTE_RULES_ACTIVE` | `false` | ANSWERED by probe |
| `THEME_RULES_ACTIVE` | `true` | ANSWERED by probe |
| `BASE_BRANCH` | `main` | ANSWERED: `refs/remotes/origin/HEAD` → `main` |
| `HARNESS` | `claude-code` | ANSWERED: the harness that ran the install |
| `POSTURE` | `enforce` | **CHOSEN by the human**, asked twice — once up front, once again after the counts below were known; both times "force enforce" |
| `COVERAGE_ASSETS_DIR` | `.prism/assets` | DEFAULTED |
| `COVERAGE_DEFAULT_THRESHOLD` | `80` | DEFAULTED (declared, not consumed; live value is `.prism/verify/thresholds.json`) |
| `COVERAGE_CRITICAL_THRESHOLD` | `100` | DEFAULTED (declared, not consumed) |
| `EMULATOR_MIN_SDK` | `26` | DEFAULTED (Tracky's minSdk is 33) |

`.prism/verify/thresholds.json` still carries the shipped example override
`":core:crypto": 100` — a module Tracky does not have, so a dead key nothing reads.

## 3. Probe output (verbatim, post-format run)

```
PRISM design-system probe

  PALETTE_OBJECT         none                     no declaration holds three or more raw Color literals
  EXTENDED_TOKEN_HOLDER  none                     found at composeApp/src/commonMain/kotlin/com/jvcs/tracky/design_system/theme/Theme.kt:315, unused: PALETTE_RULES_ACTIVE=false
  THEME_OBJECT           TrackyTheme              composeApp/src/commonMain/kotlin/com/jvcs/tracky/design_system/theme/Theme.kt:331
  PALETTE_RULES_ACTIVE   false
  THEME_RULES_ACTIVE     true

  245 raw Color literals sit at top level, in files including:
    composeApp/src/commonMain/kotlin/com/jvcs/tracky/design_system/theme/Color.kt:5
    composeApp/src/commonMain/kotlin/com/jvcs/tracky/design_system/theme/Color.kt:6
    composeApp/src/commonMain/kotlin/com/jvcs/tracky/design_system/theme/Color.kt:7
    composeApp/src/commonMain/kotlin/com/jvcs/tracky/design_system/theme/Color.kt:8
    composeApp/src/commonMain/kotlin/com/jvcs/tracky/design_system/theme/Color.kt:9
  There is no declaration to name. PALETTE_RULES_ACTIVE=false is
  the correct answer, and those colours are checked by no rule.
  Record that in .prism/INSTALL-RECORD.md -- it is a gap you chose.
```

Exit 0 (resolved). Re-run: `./prism probe` or `python3 .prism/verify/lib/probe.py --root .`

## 4. What is now checked by no rule

- **Raw-colour use via the 245 top-level `Color` vals in `design_system/theme/Color.kt`.**
  `ThemeColorDirectUse` and `UnwiredThemeColor` are off (`PALETTE_RULES_ACTIVE: false`)
  because there is no palette declaration to name. `RawColorLiteral` catches hex
  literals, not named `val`s — so reading `Color.kt`'s vals directly from
  presentation code is not checked by anything.
- **Coverage on `:composeApp`.** `./prism status` reports it as `no sources`:
  `JacocoConventionPlugin` configures AGP `LibraryExtension`/`ApplicationExtension`
  and the KMP android-library plugin is neither. The coverage gate therefore
  measures only `:androidApp` (5 files). Not fixed at install.
- **Type-resolved detekt rules on non-Android KMP targets.** `detektMainAndroid`
  covers `commonMain` + `androidMain` with type resolution; `iosMain`, `jvmMain`,
  `webMain` and every test source set are read by the plain (syntax-only)
  `detekt` task only.

## Posture — resolved per engine (CHOSEN: force enforce)

No `.prism/scope.json`; `posture: enforce` in `prism.json`; doctor D10 agrees.

| engine | posture | findings at install (post-`ktlintFormat`) |
|---|---|---|
| detekt | enforce | ~3,345 unique (1,808 plain `:composeApp:detekt`, 2,426 `:composeApp:detektMainAndroid`, 10 + 5 in `:androidApp`; the two composeApp tasks overlap) |
| ktlint | enforce | 167 not auto-correctable (129 `package-name`, 10 `filename`, 6 `kdoc`, 6 `no-consecutive-comments`, 5 `no-wildcard-imports`, …) |
| konsist | enforce | 7 of 29 tests fail, 32 violations: tests mirror package (9), repository returns typed result (10), dto package holds only DTOs (5), domain framework-free (3), DTOs in dto package (2), State immutable (2), domain transport-free (1) |
| coverage | enforce | nothing measured; `:androidApp` would DENY (no report), `:composeApp` no sources |

Consequence the human accepted: `./gradlew staticAnalysis` is red and the
pre-push floor refuses every terminal push until these are fixed and coverage
is recorded.

Top detekt rules: VariableNaming 1159, MagicNumber 675, CrammedBlockBody 214,
NoRunBlockingInMain 195, ClassBodyMissingLeadingBlankLine 166,
SingleLetterIdentifier 162, NoNotNullAssertion 131, PackageNaming 129,
LongMethod 40, KtorCallMustUseSafeCall 35, VarCouldBeVal 34,
TextFieldStateInViewModel 28, RawColorLiteral 26.

## Deviations from the shipped payload, and why

The shipped plugins assume `src/main` layouts. Tracky's application code is one
KMP module (`composeApp`: commonMain/androidMain/iosMain/jvmMain/webMain). As
shipped, the first `staticAnalysis` showed `:composeApp:detekt NO-SOURCE`, no
type-resolved detekt task for composeApp, and 19 of 29 konsist tests SKIPPED —
all 450 composeApp files examined by nothing while every check passed. The doctor
does not catch this (it checks that modules *apply* `prism.detekt`, and its
canary lands wherever it finds a source file). The three edits listed in section 1
fix detekt and konsist reach; after them konsist skips 0 and the canary below
lands in `commonMain`.

### Found after the install, while starting the burn-down (same session)

Three more assumptions of a `src/main`/`src/test` layout, fixed in Tracky's files:

1. `DetektConventionPlugin.kt` excludes every path containing `/build/` from all
   `Detekt` and `DetektCreateBaselineTask` tasks. `detektMainAndroid` analyses the
   whole compilation, KSP output included: 1,516 findings in Room's `*_Impl.kt`.
2. `detekt.yml` — every `excludes` naming `'**/test/**'` (72 single-line, 4 list-form)
   also names `'**/*Test/**'`. Test exemptions missed `jvmTest`/`commonTest`/`iosTest`:
   ~920 findings in tests the rules were written to skip.
3. `detekt.yml` — the 11 test-stack rules' `includes` gained the KMP equivalents
   (`commonTest`, `jvmTest`, `androidHostTest`, `androidUnitTest`;
   `androidDeviceTest`, `androidInstrumentedTest`). Before, they ran on no KMP test
   at all — a silent miss, not noise.

**Corrected detekt count: 1,274** (was reported as ~3,345). Top five:
NonAssertKAssertion 334, MagicNumber 152, PackageNaming 129,
ClassBodyMissingLeadingBlankLine 114, SingleLetterIdentifier 85.

Burn-down decision 1 (human, 2026-09-24): **NonAssertKAssertion → migrate to AssertK**,
with the rule narrowed to kotlin.test *assertion* functions, since it also banned
`kotlin.test.Test`/`BeforeTest`/`AfterTest`, which AssertK cannot replace and which
are the only test annotations available in `commonTest`.

## Burn-down to green (same session, 2026-09-24)

Posture stayed **enforce** throughout: no baseline, no `scope.json`. Every finding
was fixed or its rule was taught Tracky's convention. Each rule change below was
the owner's decision, in the order it was made. Work landed as stacked slices of
400 lines or fewer on `46-*` branches.

**End state:** `./gradlew staticAnalysis` green (detekt 0, ktlint 0, konsist 0
failing), 698 JVM tests passing, iOS and androidApp compiling, prism-doctor PASS (12).

### Code decisions (owner)

| Finding | Decision |
|---|---|
| NonAssertKAssertion | migrate tests to AssertK (rule narrowed, see below) |
| MagicNumber | previews exempt; named constants and theme tokens elsewhere |
| PackageNaming | rename the packages (`designsystem`, `projectdetail`, ...) |
| ClassBodyMissingLeadingBlankLine, SingleLetterIdentifier | fix mechanically / rename all |
| CrammedBlockBody | fix by hand |
| SwallowedException, PrintStackTrace | log with Kermit |
| TooGenericExceptionCaught | narrow the catches (behaviour change accepted) |
| ScreenStateOnlyInScreenComposable | children take fields, not the screen state |
| TextFieldState / RawColorLiteral | fixed in previews too |
| CyclomaticComplexMethod, LongMethod, ReturnCount, LoopWithTooManyJumpStatements, LargeClass | refactor, largest first |
| TODOs | tracked in GitHub issue #144 |
| ProjectDao TooManyFunctions (65) | split per table: Project, ProjectTree, SortOrder, Task, SubTask, TaskInterval, SubTaskInterval, StrandedInterval DAOs; the pull merge moved to `ServerTreeWriter` (one writer transaction across DAOs) |
| Project repository layer TooManyFunctions (18) | split by concern: `ProjectOrganizationRepository` (archive/trash/pin/reorder/purge); `LocalProjectOrganizationDataSource` + `LocalServerTreeDataSource` |
| konsist typed results (10 functions) | typed results everywhere; the outbox drains report an unreadable queue |
| konsist framework-free domain | `ConnectivityObserver` / `AppLifecycleObserver` become interfaces; platform implementations in `core.data` |
| konsist DTO / wire-format / test-package rules | fix the code (move DTOs, move `RealtimeTimerConnection` to `core.data.realtime`, move tests beside their subjects) |

### Rule and config changes (Tracky's files now)

- **NonAssertKAssertion**: bans only kotlin.test `assert*`/`fail`/`expect`/`todo`;
  `Test`/`BeforeTest`/`AfterTest` stay allowed.
- **KtorCallMustUseSafeCall**: configurable `safeWrappers` and `safeOverloadArgument` (`'route'`).
- **RootAndScreenInSameFile**: pairs `XScreenRoot` with `XScreen`; only Roots taking a `*ViewModel`.
- **InMemoryRoomNotClosed**, **DispatchersSetMainWithoutReset**: accept `@AfterTest`.
- **ObserveAsEventsRequired**: skips `snapshotFlow` and the `ObserveAsEvents` body.
- **NonAtomicStateFlowAssignment**: reports read-modify-write only.
- **KoinViewModelOnlyInRoot**, **ObserveAsEventsOnlyInRoot**: `rootSuffixes = ['Root','DialogHost','App']`.
- **RoomInJvmUnitTest**: `active: false`, because Tracky's JVM Room tests run a real in-memory database and are deliberate.
- **MagicNumber**: `ignoreAnnotated: ['Preview','PreviewLightDark']`.
- **StartKoinOnlyInAppModule**: excludes `**/di/InitKoin.kt`.
- **InjectDispatcher**: excludes `**/di/**` and `**/PlatformIoDispatcher*.kt`.
- **ScreenStateOnlyInScreenComposable**: `allowedStateTypes` adds `ReorderableListState`, `ReorderableGridState`, `SubTaskDragDropState`, and the Compose `ScrollableState` interface.
- **LongParameterList**: `allowedConstructorParameters: 15` (Koin injection points take 7-14).
- **LargeClass**: test source sets excluded, like the other size rules.
- **konsist MviContractRules "State is an immutable data class"**: applies only in
  packages that hold a ViewModel (Compose state holders in `presentation/util` are mutable by design).

### Still open

- **Push gate 3 (coverage).** `:androidApp` has no instrumentation coverage result and
  its `androidTest` source set does not compile. That was already the case before
  this work: the Compose BOM line in the catalog is commented out, so
  `ui-test-junit4` has no version. `:composeApp` reads as "no sources" to the
  coverage check (KMP layout), so the module holding the tested code is not
  coverage-gated.
- Latent issues found along the way, left for the owner:
  - Unpinning a project re-indexes only the pinned section.
  - `ProjectDetailScreenRoot` shows `UiText.toString()` in its snackbar.
  - `DayDetailUiMapper`'s fallback colour differs from `DailyOverviewScreen`'s.
  - `ColorInfoCard`'s read-only surface is not dark-mode aware.

## Verification (`.prism/VERIFY.md`)

- Part 2: 70 active / 2 inactive = 72, expected 70 (palette pair off, theme on);
  the only rules off are `ThemeColorDirectUse`, `UnwiredThemeColor`, under the
  `PALETTE_RULES_ACTIVE` banner; `themeName: 'TrackyTheme'` resolves to
  `Theme.kt:331`; konsist skipped 0 in every class.
- Part 3B: no scope file; no baseline; `.git/hooks/pre-push` executable;
  build not clean (the chosen consequence above).
- Part 4 canary: `fun prismCanary() { println("canary") }` appended to
  `composeApp/src/commonMain/kotlin/com/jvcs/tracky/Platform.kt`;
  `NoConsoleLogging` reported at `Platform.kt:8:21` by both detekt tasks and
  `staticAnalysis` failed. File restored.

## Where next

`.prism/RULES.md` — what each rule catches. `.prism/INSTALL-BROWNFIELD.md` —
working a repository with history down. `/prism-burndown` — rule-by-rule
burn-down. `detekt.yml` is the one file that turns a rule off.
