# MdHabits

A Kotlin Multiplatform (Android + iOS) habit journal. One year, one theme, three
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

## Running the apps

- **Android:** `./gradlew :androidApp:assembleDebug` (or run the `androidApp` config in your IDE)
- **iOS:** open [`/iosApp`](./iosApp) in Xcode and run, or `./gradlew :shared:assembleAndroidMain` to build the shared framework.

## Running tests

- **Shared logic (JVM/Android host):** `./gradlew :shared:testAndroidHostTest`
- **iOS simulator:** `./gradlew :shared:iosSimulatorArm64Test`

## Contributing

Project structure, conventions, and the required workflow for every change (add tests,
make them pass, update the release notes, bump the version) are documented in
[AGENTS.md](./AGENTS.md). Read it before making changes.

## Release notes

Versions follow [Semantic Versioning](https://semver.org/). Newest first.

### 0.9.0 — Onboarding recognizes existing vaults

- Picking a folder during onboarding that already contains an annual theme note
  (`theme/<year>.md`) now skips the setup steps (values, theme, tasks, rewards) and jumps
  straight to the final step: the folder is an existing vault, so its notes are used as-is
  and nothing is seeded over them. The folder card shows a hint when this happens.

### 0.8.0 — App version in Settings

- Settings has a new "About" section showing the installed app version. The value is
  read from the platform itself (Android `versionName` / iOS `CFBundleShortVersionString`),
  so it always matches the released build.

### 0.7.0 — One note per week + review-driven week start

- Each week now lives in a single Markdown note, `weeks/<weekId>.md` (e.g.
  `weeks/2026-W27.md`): planned/completed tasks in the frontmatter, the weekly-review
  journal as the note body, and the points ledger as bullet lines under a `## Ledger`
  heading. The separate `ledger/` and `reviews/` notes are gone — existing vaults are
  migrated automatically on the first launch (legacy files are folded into the week
  notes and removed).
- The weekly review now closes one week and opens the next: the report, journal, and
  objective ratings are written into the **previous** week's note, and committing tasks
  creates the **new** week's note.
- The weekly-review button on Home only appears while the current week's note doesn't
  exist yet — after the review (or anything else) creates it, the button disappears
  until the next week starts.

### 0.6.0 — Edit everything in Manage

- Tasks, objectives, values, penalties, and rewards can now be edited: tap any item in
  the Manage tab to open it in the form sheet, change its fields, and save. Tasks keep
  their completion history and links; an objective keeps its achieved state; descriptions
  written in Obsidian are preserved.

### 0.5.0 — Task scheduling: daily, weekly, weekday, and ad-hoc tasks

- Tasks now have four frequencies: **daily**, **weekly**, on **certain days of the week**
  (pick the weekdays in the task form), or **ad-hoc** (unscheduled, done whenever). The
  previous "one-off" frequency became ad-hoc; existing notes load unchanged.
- Home is organized into three sections: **Today** (daily tasks plus weekday tasks due
  today), **This week** (weekly tasks), and **Ad-hoc**. Weekday tasks only appear on
  their scheduled days.
- Daily and weekday tasks reset each day: checking one counts (and pays points) for that
  day only, while weekly and ad-hoc completions keep counting for the whole week.

### 0.4.1 — Fix: onboarding data now reaches the vault

- Fixed a bug where everything created during onboarding (annual theme with its
  objectives, tasks, penalties, values, and rewards) was written to the app-private
  fallback folder instead of the vault folder picked in the first step, so the Markdown
  notes never appeared in the vault and the data was gone on the next launch. The vault
  choice is now persisted before any note is written.
- Picking a (new) data folder in Settings now copies the existing notes into it, so data
  created before choosing a folder — or in a previously chosen one — is no longer left
  behind. Notes already present in the new folder are kept, never overwritten.

### 0.4.0 — Multiline theme description

- The annual-theme description in onboarding is now a multiline text area (like the
  weekly-review journal), so the theme's motivation can be written with line breaks.

### 0.3.0 — Language selector

- Choose the app language (English, Português, Español) directly in the app: a selector
  now sits on the first onboarding screen and in a new "Language" section in Settings.
  Languages are listed in their own names, the change applies instantly, and the choice
  persists across launches (before choosing, the app follows the system language).

### 0.2.0 — Markdown storage

Data now lives as Markdown notes in your vault instead of only in memory.

- Everything you create is saved as a Markdown note in the folder you picked during
  onboarding (Android; via the system folder picker) or in the app's `Documents/MdHabits`
  folder (iOS), and is loaded back on the next launch.
- Obsidian-friendly layout: one note per value, task, penalty, and reward (`<id>.md`),
  plus `theme/<year>.md`, `weeks/<week>.md` (planned/completed habits),
  `reviews/<week>.md` (journal as the note body), and an append-only points ledger in
  `ledger/<week>.md` — the balance is always derived from the ledger, never stored.
- Notes stay editable outside the app: frontmatter parsing is lenient, and a note the
  app can't understand is skipped instead of breaking the load.
- If no vault folder is chosen, data is stored in app-private storage so nothing is lost.

### 0.1.0 — Paper journal design

First tracked release. Implements the full "App Hábitos" design across every screen.

- **Onboarding** (6 steps): data folder, personal values, annual theme + three
  objectives, tasks & penalties, rewards, and a "done" confirmation.
- **Home**: notebook-margin layout with the rotated week badge, annual-theme header,
  penalty/redeem actions, recurring vs. one-off task sections with check-to-complete
  and a points toast, and the weekly-review call to action.
- **Redeem**: dark balance card plus reward cards with available / insufficient states.
- **Manage (Cadastros)**: category chips (tasks, objectives, values, penalties,
  rewards) with an "+ new" entity sheet.
- **Settings**: data folder, backup, week start, review reminder, and annual-theme link.
- **Annual theme**: theme title/description with three objective cards showing progress.
- **Weekly review** (2 steps): live week report, journal, proximity scale, and
  next-week task commitments.
- **Design system**: warm paper palette, Caveat/Karla/Lora typography, dashed borders,
  hard-offset buttons, hand-drawn icons, a custom paper tab bar, bottom sheets, and toasts.
- Localized in English, Portuguese, and Spanish.
- Unit tests cover the points ledger, week calculations, and the Home, Redeem, Manage,
  and weekly-review ViewModel logic (shared, run on both Android host and iOS simulator).
