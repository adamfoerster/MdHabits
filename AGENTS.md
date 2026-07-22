# AGENTS.md

Guidance for AI agents (and humans) working in this repository. Read this fully before
making changes.

## What this project is

MdHabits is a Kotlin Multiplatform habit journal targeting **Android and iOS**, with a
shared Compose Multiplatform UI. Its concept: one year, one theme, three objectives;
completing habits earns points that are spent on rewards. Data is meant to live as
Markdown notes in a user-chosen folder, so the points balance is always **derived** from
an append-only ledger rather than stored — this keeps the app resilient to manual edits.

## Project structure

```
MdHabits/
├── androidApp/                  Android entry point (MainActivity, app Gradle module)
│   └── build.gradle.kts         ← Android versionCode / versionName live here
├── iosApp/                      iOS entry point (SwiftUI shell hosting Compose)
│   └── iosApp.xcodeproj/        ← iOS MARKETING_VERSION / CURRENT_PROJECT_VERSION live here
├── shared/                      All shared code (logic + Compose UI)
│   └── src/
│       ├── commonMain/kotlin/com/adamfoerster/mdhabits/
│       │   ├── core/            datetime (WeekCalculator), i18n (Strings), settings, ids
│       │   ├── domain/          model/ (data classes), repository/ (interfaces), usecase/
│       │   ├── data/
│       │   │   ├── markdown/    Markdown-backed repositories + frontmatter/entity codecs
│       │   │   └── repo/        InMemory* repository implementations (used in ViewModel tests)
│       │   ├── di/              Koin modules (Modules.kt) + expect platformModule()
│       │   ├── storage/         VaultPicker + VaultFileSystem (per-platform file access)
│       │   └── ui/
│       │       ├── theme/       Theme.kt — Paper palette + PaperFonts (Caveat/Karla/Lora)
│       │       ├── components/  Reusable design widgets (Paper.kt, PaperIcons.kt, EntityFormSheet.kt)
│       │       ├── navigation/  Routes.kt (type-safe destinations), MainScaffold.kt (tab bar)
│       │       ├── onboarding/  OnboardingFlow + OnboardingViewModel
│       │       └── screens/     home/ redeem/ register/ settings/ theme/ review/ (Screen + ViewModel each)
│       ├── commonMain/composeResources/font/   Bundled TTFs (Caveat, Karla, Lora)
│       ├── androidMain/         Android actuals (platformModule, settings, locale)
│       ├── iosMain/             iOS actuals (MainViewController, platformModule, settings, locale)
│       ├── commonTest/          Shared unit tests (run on every target)
│       ├── androidHostTest/     JVM-hosted tests
│       └── iosTest/             iOS-target tests
├── gradle/libs.versions.toml    Version catalog (dependency + SDK versions)
└── README.md                    Public overview + release notes
```

### Architecture conventions

- **Layering:** `ui` → `domain` (usecases/repositories) → `data`. Nothing above the
  repository interfaces knows about files or Markdown.
- **MVVM:** every screen is a `*Screen` composable + a `*ViewModel` (Koin-injected via
  `koinViewModel()`), exposing a `StateFlow` of an immutable UI-state data class.
- **Points are derived**, never stored: sum the `PointsEvent.delta` entries in the ledger.
- **Design system:** use the shared widgets and the `Paper`/`PaperFonts` tokens in
  `ui/theme/Theme.kt` and `ui/components/` — do not hardcode colors, fonts, or one-off
  paddings. The app is intentionally light-only.
- **i18n:** all user-facing strings go through `core/i18n/Strings.kt` in **all three
  languages** (English, Portuguese, Spanish). Never hardcode display text in a screen.
- **DI:** register new ViewModels/use cases/repositories in `di/Modules.kt`.

## Required workflow for every change

Do all of the following, in order, for any change to app behavior:

1. **Add or update tests.** Put shared-logic tests in `shared/src/commonTest`. Cover the
   new behavior (a use case, a ViewModel state transition, a calculator, etc.). Prefer
   testing `domain`/`core` and ViewModel logic over UI.
2. **Make the tests pass.** Run and confirm green:
   - `./gradlew :shared:testAndroidHostTest`
   - `./gradlew :shared:iosSimulatorArm64Test` (when touching shared or iOS code)
   Also confirm it still compiles for both platforms:
   - `./gradlew :shared:assembleAndroidMain`
   - `./gradlew :shared:compileKotlinIosSimulatorArm64`
   Do **not** mark work complete with failing or skipped tests.
3. **Update the release notes** in [README.md](./README.md). Add a bullet under the
   current unreleased version, or open a new version section (newest first) describing
   the user-visible change.
4. **Bump the version** on both platforms (see below). Keep Android and iOS in sync.

### Versioning

The project uses [Semantic Versioning](https://semver.org/): `MAJOR.MINOR.PATCH`.
The current version is **0.1.0**. Bump it with every released change:

- **PATCH** (`0.1.0` → `0.1.1`): bug fixes, no new user-facing feature.
- **MINOR** (`0.1.0` → `0.2.0`): new feature, backward compatible.
- **MAJOR** (`0.1.0` → `1.0.0`): breaking change / milestone.

Update **both** platforms:

- **Android** — `androidApp/build.gradle.kts`:
  - `versionName` → the new semver string (e.g. `"0.2.0"`).
  - `versionCode` → increment by 1 (monotonic integer; never reuse).
- **iOS** — `iosApp/iosApp.xcodeproj/project.pbxproj` (both the Debug and Release build
  configurations of the app target):
  - `MARKETING_VERSION` → the new semver string (matches Android `versionName`).
  - `CURRENT_PROJECT_VERSION` → increment by 1 (matches Android `versionCode`).

Keep the README release-notes heading, `versionName`, and `MARKETING_VERSION` identical.

## Build & run reference

| Task | Command |
| --- | --- |
| Android app | `./gradlew :androidApp:assembleDebug` |
| Shared framework (Android) | `./gradlew :shared:assembleAndroidMain` |
| iOS compile check | `./gradlew :shared:compileKotlinIosSimulatorArm64` |
| Shared tests (JVM host) | `./gradlew :shared:testAndroidHostTest` |
| iOS simulator tests | `./gradlew :shared:iosSimulatorArm64Test` |
| iOS app | open `iosApp/` in Xcode and run |

## Notes & gotchas

- `kotlinx-datetime` is pinned to `0.8.0-0.6.x-compat` in the version catalog because the
  standard 0.6.2 breaks iOS/native on Kotlin 2.4. Don't "upgrade" it blindly.
- Repositories are Markdown-backed (`data/markdown/Markdown*`): each entity is one note
  with YAML frontmatter (values written as JSON literals — valid YAML 1.2 — so parsing
  stays unambiguous while Obsidian shows them as properties). File access goes through
  the `storage/VaultFileSystem` interface (Android SAF tree / iOS NSFileManager, each
  with an app-private fallback when no vault is chosen). Keep new features behind the
  repository interfaces; the `data/repo/InMemory*` implementations remain for
  ViewModel tests.
- Markdown loading must stay lenient: a note the app can't decode is skipped, never
  fatal, so manual edits in Obsidian can't brick the app.
- All per-week data (task instances, weekly review, points ledger) lives in ONE note,
  `weeks/<weekId>.md`, owned by the shared `MarkdownWeekStore` singleton — the task,
  review, and ledger repositories must all go through it, never write that file
  directly. The store also migrates pre-0.7.0 vaults (separate `ledger/` and `reviews/`
  notes) on load. The Home weekly-review button shows only while the current week's
  note doesn't exist; submitting the review writes the journal into the previous week's
  note and creates the new one.
- `VaultFileSystem` implementations resolve the storage root from `AppSettings.vaultRef`
  on **every call**. Any flow that changes the vault (onboarding finish, Settings folder
  pick) must go through `storage/VaultMigrator` *before* writing notes — persisting the
  selection after writing silently sends the notes to the previous root (this was the
  0.4.1 data-loss bug).
