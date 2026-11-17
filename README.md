# MdHabits

A Kotlin Multiplatform (Android + iOS + Desktop) habit journal. One year, one theme, three
objectives — every habit you complete earns points you trade for rewards. The UI is
built with Compose Multiplatform and follows a warm "paper journal" design language.

The app is designed to store its data as Markdown notes inside a folder you pick (e.g.
an Obsidian vault), so your data stays yours and remains editable outside the app.

## Tech stack

- **Kotlin Multiplatform** — shared logic and UI across Android and iOS
- **Compose Multiplatform** — single UI codebase (`shared/src/commonMain`)
- **Koin** — dependency injection
- **Type-safe Navigation Compose** — screen routing
- **kotlinx-datetime / coroutines / serialization**

Building requires **JDK 25** (Gradle provisions it automatically if missing).

## Running the apps

- **Android:** `./gradlew :androidApp:assembleDebug` (or run the `androidApp` config in your IDE)
- **Desktop:** `./gradlew :desktopApp:run` (package with `./gradlew :desktopApp:packageDistributionForCurrentOS`)
- **iOS:** open [`/iosApp`](./iosApp) in Xcode and run, or `./gradlew :shared:assembleAndroidMain` to build the shared framework.

## Running tests

- **Shared logic (JVM/Android host):** `./gradlew :shared:testAndroidHostTest`
- **Desktop (JVM):** `./gradlew :shared:desktopTest`
- **iOS simulator:** `./gradlew :shared:iosSimulatorArm64Test`

## Contributing

Project structure, conventions, and the required workflow for every change (add tests,
make them pass, update the release notes, bump the version) are documented in
[CLAUDE.md](./CLAUDE.md). Read it before making changes.

## Release notes

See [CHANGELOG.md](./CHANGELOG.md).
