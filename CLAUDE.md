# Polylingy

Desktop app for language learning via spaced repetition, written in Kotlin Multiplatform with Compose. Courses are versioned documents; learning is an append-only journal of answers from which progress is derived. Everything is stored locally in SQLite.

Only the desktop (JVM) target is built today, and only Windows is built and checked. Android, iOS, a server with sync and a web client are planned but not started — see `docs/architecture.md`.

## Tech Stack

Versions live in `gradle/libs.versions.toml`.

- **Kotlin Multiplatform** + **Compose Multiplatform** (desktop), Material 3
- **JDK 25** via Gradle toolchain (auto-provisioned; Gradle itself may run on another JDK)
- **SQLDelight** — SQLite with SQL-first, generated Kotlin
- **kotlinx.serialization** (JSON), **kotlinx-datetime**
- **AndroidX ViewModel + Navigation** for Compose Multiplatform
- Tests: `kotlin.test`, Compose `ui-test`

## Modules

| Module | Purpose |
|--------|---------|
| `core/` | Pure business logic, no UI/SQL/IO: content models, journal events, rules (SM-2, graduation, topic picking, answer checking), `replay()`, ports, `StudyService` / `CourseService` |
| `client/` | Storage adapters: SQLite (`LocalStorage`), course file import/export (`CourseFile`), sample course; `desktopMain` opens the DB in `<Documents>/polylingy` |
| `app/` | Compose UI: screens, ViewModels, `AppContainer` (manual DI); `desktopMain` has `main()` and native file dialogs |

Dependencies point only towards `core`. Root package: `com.polylingy`.

Key files:
- `app/src/desktopMain/kotlin/com/polylingy/app/Main.kt` — entry point: opens storage, seeds the sample course, opens the window
- `app/src/commonMain/kotlin/com/polylingy/app/AppContainer.kt` — builds every long-lived object (clock, random, services)
- `core/src/commonMain/kotlin/com/polylingy/core/progress/Replay.kt` — derives all progress from the journal
- `core/src/commonMain/kotlin/com/polylingy/core/rules/` — SM-2, graduation, pickers, answer checking
- `core/src/commonMain/kotlin/com/polylingy/core/journal/Events.kt` — journal event format (`schemaVersion`)
- `client/src/commonMain/sqldelight/` — SQLite schema; Kotlin is generated from it

## Data Rules

- **Journal events are immutable** and only appended. Evolve the format with `schemaVersion`; older versions must stay readable.
- **Progress is never stored** — always `replay()`ed from the journal with the current rules (TODO(perf) noted in `Replay.kt`).
- **Courses are whole documents** with `revision`/`updatedAt`; every edit goes through `CourseService`, which bumps the revision.
- All ids are UUIDs. Topic ids are stable; deleting a topic never affects others.
- Dates for scheduling are `LocalDate` from `ZonedInstant` (instant + zone), never raw `Instant` math.

## Storage

```
<Documents>/polylingy/
  polylingy.db       # course_document, journal_event, setting
```

Courses get in and out via JSON import/export (format compatible with the old Flutter files).

## Essential Commands

```bash
# All tests (core, client, app incl. UI tests); writes screenshots to app/build/screenshots/
./gradlew check

# Run the app
./gradlew :app:run

# Build the Windows installer -> app/build/compose/binaries/main/msi/
./gradlew :app:packageMsi
```

## Verifying Changes

Follow `docs/testing.md`. For any UI change: add or extend a scenario in `app/src/desktopTest/.../ui/*UiTest.kt` (helpers in `UiTestSupport.kt`), call `screenshot("name")` for new or changed screens, run `./gradlew check`, then open the PNGs in `app/build/screenshots/` and review them. Only native file dialogs, the real window and the installer need manual checks.

When launching the app from scripts, match processes by PID or executable path, never by window title — the user may have their own instance open.

## Git Workflow

**Before starting any new feature or bug fix, create a branch and work on it for the remainder of the session:**

```bash
git checkout -b <branch-name>   # e.g. feat/streak-counter, fix/review-date-off-by-one
```

Do not commit directly to `master`.

## Additional Docs

| File | When to read |
|------|-------------|
| `docs/architecture.md` | Target architecture: modules, documents vs journal, replay, sync concept, open questions |
| `docs/testing.md` | How changes are verified: test levels, UI tests, screenshots, manual checks |
| `docs/migration-plan.md` | History of the move from Flutter; parity checklist and what it covers |
| `.claude/docs/architectural_patterns.md` | Code conventions: ports, DI, ViewModels, testing helpers |
