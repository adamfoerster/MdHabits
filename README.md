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
[AGENTS.md](./AGENTS.md). Read it before making changes.

## Release notes

Versions follow [Semantic Versioning](https://semver.org/). Newest first.

### 0.18.0 — Readable week notes that close themselves

- **New week note format.** Task instances and health values moved out of the frontmatter into
  `## Health` and `## Instances` sections of the note body, next to `## Ledger`. Instances are
  checklist items and every instance and ledger entry links to its task, penalty, reward or theme
  note (`[[MdHabits/tasks/…|Orar]]`), so a week reads naturally in Obsidian. Ledger entries now
  carry the name of what they refer to instead of a sentence ("Orar" rather than "Concluída: Orar").
  Older notes are not read anymore: the vault was converted once.
- **Points checkpoints.** Each week records `pointsAtWeekStart`, kept up to date while the week is
  open. More than 7 days after a week ends, MdHabits closes it: it writes `pointsAtWeekEnd`, marks
  `closed: true`, and from then on reads only its frontmatter — the balance builds on that
  checkpoint instead of re-adding the whole history, and a closed week is never written again. The
  report of a past week still shows everything, reading the closed note on demand.
- Missed habit days are charged only in weeks that are still open, and a completed ad-hoc task is
  now stamped on the task itself (`done_on`), so it stays done after its week closes.

### 0.17.0 — Live sync with your vault

- **Changes from other devices show up while the app is open.** MdHabits now watches the vault
  folder while it is in the foreground and reloads any note that changed on disk — e.g. a task
  checked off on another device and synced by Syncthing, or a note edited in Obsidian. It keeps
  each note's last-modified time in memory and reloads a note whenever that time changes, so an
  edit to an old week is picked up too (even when the sync tool carries over an older timestamp).
  Notes deleted elsewhere disappear; a note left broken by a manual edit keeps its last good
  version until it is fixed.
- **Syncthing conflict copies are ignored.** When both devices changed the same note, Syncthing
  keeps the losing version beside it as `….sync-conflict-….md`; MdHabits used to read that copy as
  the same week and could show its stale state instead of the real note. Week notes are now only
  read from their own `<week>.md` file.

### 0.16.0 — Delete tasks, task filters and a cleaner list

- **Delete a task.** Open a task on the task management screen and tap "Delete task" (it asks to
  confirm). The task is removed for good; points it already earned stay in your history.
- **Frequency filters** on the task management screen: show only All, Ad-hoc, Weekly, Daily,
  Days of week, or Habit tasks. Days of week tasks get their own chip so they stay reachable.
- The **"+ New" button** on the task management screen is now a larger, filled button that is easier
  to see and tap.
- **Completed ad-hoc tasks are hidden** from the task management screen: once an ad-hoc task has been
  checked off it is done for good, so it no longer clutters the list.

### 0.14.0 — Health Connect: tasks that check themselves off

- **Health goals on tasks (Android).** A task can now carry a health goal — at least N steps, at
  least N hours of sleep, or at most N kg — and MdHabits checks it off by itself on the days
  Health Connect shows the goal was met. Turn it on in Settings → Health Connect (it asks for
  read access to steps, sleep, and weight), then pick the goal when creating or editing a task.
- The sync runs every time Home opens (and on demand via "Sync now"). Daily, weekday, and habit
  tasks are completed for today and yesterday, so a late walk still counts the next morning;
  weekly tasks once any day of the week met the goal; ad-hoc tasks once. It only ever checks
  tasks off, never unchecks them, and runs before missed habits are charged.
- Home shows today's value under each task with a goal (e.g. "Steps: 6240 · ≥ 8000").
- The daily values are written into the week's note (`weeks/<weekId>.md`, a `health` property),
  so they show up in Obsidian. Nothing is sent anywhere; the app never writes to Health Connect.
- Health goals are Android-only for now; iOS and Desktop hide them.
- **Fix:** the create/edit sheets now scroll, so the Save button stays reachable when the form is
  taller than the screen (e.g. a task with a health goal and several links, or with the keyboard open).

### 0.13.1 — Ad-hoc tasks stay done

- A **completed ad-hoc task no longer comes back** in later weeks. Until now its completion was only
  remembered for the week it was made in, so it reappeared on Home (and in the weekly review) after
  the week turned. Completion now counts from any week, finished ad-hoc tasks are left out of the
  review's planning list, and checking one off a second time can't credit its points twice.

### 0.13.0 — Desktop app

- MdHabits now runs on the **desktop** (Windows, macOS and Linux) with the same UI and features as
  the phone apps. Your vault is any folder you choose with the system folder dialog, and until you
  pick one notes are kept in `~/.mdhabits/vault`. Run it with `./gradlew :desktopApp:run`.

### 0.12.1 — New app icon

- MdHabits has its **own icon**: a check mark inside a circle, on the app's paper tones. On iOS it
  follows the Home Screen style: a dark version in dark mode and a tinted one when icons are tinted.
  On Android it is an adaptive icon, and on Android 13+ it also supports themed icons.

### 0.12.0 — Reports of past weeks

- Home's week badge now opens a **calendar of past weeks**. It shows one month at a time, a row per
  ISO week, and only the weeks your journal has records for can be opened — the rest are there for
  orientation. The month arrows stop at the current month and at your oldest recorded week.
- Picking a week opens its **report, read-only**: the week's stats, everything the ledger recorded
  (tasks completed, objectives, slips, redemptions) and the journal written in that week's review.
  A closed week is history — nothing on that screen can be edited.
- The report's **balance is now the one the week closed with** instead of today's balance, so a past
  week reads as it was. This also fixes the figure shown in the weekly review.

### 0.11.0 — Habits: the task type you lose points for missing

- New **habit** frequency for tasks. A habit is due every day and its points work the other way
  around: finishing it earns the points as usual, but a day that ends without it done *costs* them.
  Home shows a habit's points as `±N`, and the task form explains the trade when you pick it.
- On every app open MdHabits sweeps the **last four ISO weeks** (the current one included) and
  charges every day a habit went undone. Today is never charged — you have until the end of the
  day — and neither are days before the task became a habit, so adding one can't bill your past.
  Each missed day is charged only once, and the charges show up in the week's report as slips.
- Week notes now record **every day** a task was completed on (`dates` in each instance), not just
  the last one, which is what lets the sweep tell a missed day from a done one. Notes written by
  earlier versions keep working: their single completion stamp is read as that one day.

### 0.10.0 — mdPrayer integration

- Optional integration with mdPrayer in Settings: point it at the same folder mdPrayer
  stores its data in, link one habit to mdPrayer's daily prayer log, and MdHabits marks
  that habit done for the days mdPrayer recorded a fully-prayed day in the current week.
  Syncs automatically on app open and on demand via a "Sync now" button.
- The iOS folder picker is now fully implemented (it previously always canceled),
  using `UIDocumentPickerViewController` and a security-scoped bookmark — needed for
  the mdPrayer integration, and usable for the main data folder as well.

### 0.9.1 — Ad-hoc tasks drop off once completed

- Completing an ad-hoc (unscheduled) task now removes it from the Home task list right
  away instead of leaving it checked off in the Ad-hoc section. It still counts toward
  the week's points balance and the "done of" progress total.

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
