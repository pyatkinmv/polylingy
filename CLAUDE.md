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
| `content/` | Course content, independent of the rest: text markup, theory blocks, exercise types, `validate()`, strict JSON (`ContentJson`) |
| `content-compose/` | Draws content: `ContentView` (theory blocks), `ExerciseView` (the layers of every exercise, `Framed` for look and aside, a body per type), `ContentTheme`, `ImageLoader`; knows nothing of courses or checking |
| `core/` | Pure business logic, no UI/SQL/IO: course model and file (`CourseJson`), journal events, checking, course policies, `replay()`, ports, `CourseService` / `StudyService` |
| `client/` | Storage adapters: SQLite (`LocalStorage`), course import/export with pictures (`CourseLibrary`), sample course; `desktopMain` has zip, picture files, HTTP and the DB in `<Documents>/polylingy` |
| `app/` | Compose UI: screens, ViewModels, `AppContainer` (manual DI); `desktopMain` has `main()` and native file dialogs |

Dependencies point only towards `core` (and `core` → `content`). Root package: `com.polylingy`.

Version 0.2 is the rewrite to the current model (courses with units and theory, exercise types, course policies); how it went is in `docs/migration-plan.md`. Version 0.3 added exercise layers (`look`, `aside`), `expand` and `fix`, and the formal schema of the course file; 0.4 added sounds (played with JLayer), dialogues, annotations over words and the "How exercises look" settings; 0.5 added the docked player of long recordings, the `track` and `video` blocks and right-to-left text (Arabic); 0.5.1 added kinds of remarks (`tip.kind`), lead paragraphs (`text.role`) and numbered examples; 0.5.5 added `extraKeys` (keys over a text field for letters the keyboard lacks), the `sort` exercise and `audio` of a `choice` item. 0.6 added blocks for richer theory (format 9): `grid` (columns of any blocks), `tip` kinds `fact` and `culture` with `title` and `aside`, a frame (letter, article) round a `situation`, colour highlights `==text==` in text, speech-bubble dialogues with portraits, table `appearance` flags, colour palettes and styles chosen in the course settings (`rules.display.palette`, `rules.display.style`, tones of marks are numbers 1–5), and a showcase course with every element in `examples/theory-showcase.zip`. 0.7 added the exercise layer `passage` (format 10): a page of blocks (a dialogue, a story, a letter) shown above the exercise exactly as in the theory, so an exercise about a text carries the text. 0.7.1 added the "Admin" button of a unit: one page with the whole theory and every exercise of the unit, checked without recording anything (`study/AdminScreen.kt`), with a switch to show the answer key (`Exercise.solvedResponse()`). 0.7.2: a theory table wider than its place is narrowed and its long cells wrap (no sideways scrolling unless even the longest words do not fit). 0.7.8: a chosen option (and a chosen word of `sort`) is the palette's "right" green in every palette; once checked the outline stays and only the inside gets paler (red for a wrong one), so nothing changes colour at the check. 0.7.7: text of a course (theory, exercises, explanations) can be selected with the mouse and copied: `Selectable` in `content-compose/.../Selection.kt` wraps a page and an exercise, what is clicked or typed into and the annotations over words are left out with `DisableSelection`, the highlight is the palette's accent. 0.7.6 (and 0.7.5): the default style is `strict` (marks underlined), the dark palette is just "Midnight", a disabled Check button (`Look.buttonColors`: the same green at 45% with softer white text) stays visible on any palette. 0.7.4 (format 11): a blank carries its own hint, `{{answer}}[[hint]]`, shown in grey inside its empty field; the one `hint` field of a gaps item is gone (`CourseJson.upgrade` moves it into the text). 0.7.3: after a check the topic's explanation is shown under the exercise's own one, headed by the topic's title (before, it only stood in for an exercise without one). 0.8.4: extra practice (`PracticeAnswered` in the journal, `StudyService.practiceNext`, `study/PracticeScreen.kt`): over a course, unit or topic, apart from the plan (progress, schedules, pickers never read it), with the course's own queue, focus and exercise choice and a pass through each topic's exercises; buttons on the course and unit pages when nothing is left, on a resting topic's page and on the done screen; statistics count it as activity, not as progress. 0.8.3: the choice of the exercise of a topic is a setting (`picker.exercise`, question 5 of the study settings): `random-not-last`, `in-order` and `missed-first` (brings back what was missed in an earlier round, the longest ago first, never in the round it was missed in); `ExerciseChoice` now gets the topic's history from the journal (`TopicAnswer`). 0.8.2 (format 13): one `version` of a course replaces `revision` and `courseVersion` (course file and document `version`, table column `course_version`, journal field `courseVersion`, journal schema 2, database schema 3 with `2.sqm`). 0.8.1: the Statistics tab (`stats/StatisticsScreen.kt`, `core/study/Statistics.kt`) and the shared top bar with the tabs "Courses | Statistics" and the streak chip (`ui/AppTabs.kt`): today's goal is the course's limit of new topics, the streak, topics learned, reviews coming up and a year activity map whose squares tell their day (answers, passed, new topics) when pointed at; every number is derived from the journal, and "how things stood on a day" is `replay` of the journal up to that day with the current policies. 0.8.0 alpha (first release candidate, `docs/roadmap.md` iteration 1): data of the installed app apart from the development data, a bundled course, daily database backups, an error dialog and "Open log folder", the window's place remembered, caches that make a journal of 30000 answers cost 40 ms a screen, a compatibility test with a database of an earlier schema (`client/src/desktopTest/resources/compat`), `releaseStage` in `gradle.properties` shown after the version. 0.7.9 (format 12): `courseVersion` in the course file and "Update from file…" in a course's menu: a file with the same course id and a higher `courseVersion` replaces the content and keeps the progress of every topic and exercise that keeps its id (`CourseService.update`, `CourseLibrary.update`); a plain import of a known id is still a copy. The path to the first release is in `docs/roadmap.md`. Example courses for manual checks are in `examples/`.

Key files:
- `app/src/desktopMain/kotlin/com/polylingy/app/Main.kt` — entry point: opens storage, seeds the sample course, opens the window
- `app/src/commonMain/kotlin/com/polylingy/app/AppContainer.kt` — builds every long-lived object (clock, random, services)
- `app/src/commonMain/kotlin/com/polylingy/app/App.kt` — routes and `Navigator`; screens in `courses/`, `course/`, `study/`, `edit/` (JSON editor of units and topics), `settings/` (study settings of a course), look in `ui/Look.kt`
- `content/src/commonMain/kotlin/com/polylingy/content/` — `Inline`, `Block`, `Exercise` (its KDoc lists the layers), `Layers.kt` (`Look`, `Aside`), `ContentJson`
- `docs/course.schema.json` — formal JSON Schema of the course file; `SchemaTest` (content) and `CourseSchemaTest` (core) keep it equal to the code
- `core/src/commonMain/kotlin/com/polylingy/core/content/CourseJson.kt` — reading and writing course files, all checks
- `client/src/commonMain/kotlin/com/polylingy/client/CourseLibrary.kt` — import/export with pictures
- `core/src/commonMain/kotlin/com/polylingy/core/rules/TextMatcher.kt`, `ExerciseChecker.kt` — checking answers
- `core/src/commonMain/kotlin/com/polylingy/core/learning/` — the four policies, presets, `replay()` (derives all progress from the journal)
- `core/src/commonMain/kotlin/com/polylingy/core/study/StudyService.kt` — what to show next, answering, progress for screens
- `core/src/commonMain/kotlin/com/polylingy/core/journal/Events.kt` — journal event format (`schemaVersion`)
- `client/src/commonMain/sqldelight/` — SQLite schema; Kotlin is generated from it

## Data Rules

- **Journal events are immutable** and only appended. Evolve the format with `schemaVersion`; older versions must stay readable.
- **Progress is never stored** — always `replay()`ed from the journal with the current rules (TODO(perf) noted in `Replay.kt`).
- **Courses are whole documents** with `version`/`updatedAt`; every edit made in the app goes through `CourseService`, which makes it the next `version` (an import or an update from a file keeps the file's version; a file updates a course only with a higher one). The journal remembers the version of each answer (`courseVersion`; `courseRevision` in entries of schema 1 is read by an alias); the table column is `course_version`.
- **Course settings** (the policies) live apart from the document, in `course_settings`: the file's `rules` only seed them on import, export writes the current ones; changing them never bumps the version.
- All ids are UUIDs. Topic ids are stable; deleting a topic never affects others.
- Dates for scheduling are `LocalDate` from `ZonedInstant` (instant + zone), never raw `Instant` math.

## Storage

```
<data dir>/
  polylingy.db       # course_document, course_settings, journal_event, setting (the window's place)
  backups/           # polylingy-<date>.db (first start of a day, last 14) and …-before-schema-<n>.db (before a migration, last 5)
  images/<courseId>/ # pictures and sounds of each course: images/…, audio/… from its archive, remote/<hash> downloaded by URL
  logs/              # polylingy.log, one file per day, kept 14 days
```

The data dir depends on how the app runs (`DesktopStorage.defaultDataDir`): the **installed** app (`jpackage.app-path` is set) uses `%LOCALAPPDATA%\Polylingy data`
(not Documents: OneDrive syncs it file by file) and copies each daily backup also to `<Documents>/Polylingy backups`; a run **from the sources** (Gradle, IDE,
tests) uses `<Documents>/polylingy`, so development never touches the installed app's data. `-Dpolylingy.dataDir=<folder>` overrides both. The installed app
starts with its bundled course `English Usage Exercises` (`client/src/desktopMain/resources/bundled/`), not the sample; `-Dpolylingy.bundled=true` does the
same in a run from the sources. A newer version of the bundled course in a new installer updates the stored one (`CourseLibrary.seedBundled`), progress kept.

Courses get in and out as files: `course.json` alone or a zip with `course.json` and `images/` (`docs/content-format.md`). Data from 0.1 is not supported: its database was thrown away.

## Essential Commands

```bash
# All tests (core, client, app incl. UI tests); writes screenshots to app/build/screenshots/
./gradlew check

# Run the app
./gradlew :app:run

# Build the Windows installer -> app/build/compose/binaries/main/msi/
./gradlew :app:packageMsi

# The same, copied to app/build/release/Polylingy-<version>-<stage>.msi (the stage is releaseStage in gradle.properties)
./gradlew :app:releaseMsi

# Check a course file as import does (comma-separated for several)
./gradlew :client:checkCourse -Pcourse=examples/advanced-grammar.json
```

## Verifying Changes

Follow `docs/testing.md`. For any UI change: add or extend a scenario in `app/src/desktopTest/.../ui/*UiTest.kt` (helpers in `UiTestSupport.kt`), call `screenshot("name")` for new or changed screens, run `./gradlew check`, then open the PNGs in `app/build/screenshots/` and review them. Only native file dialogs, the real window and the installer need manual checks.

**After every change, relaunch the app so the user can see it:** run `scripts/relaunch.ps1` in the background. It closes any running Polylingy (dev run or `Polylingy.exe`), then builds and starts the current code. Check that the window came up (process `java` with `com.polylingy.app.MainKt` in its command line, window title `Polylingy`) and say which version is now running.

Never stop processes other than Polylingy's own — match them by command line or executable path, not by window title.

## Versioning

The app version lives only in `gradle.properties` (`version=0.MINOR.PATCH`). It is generated into `APP_VERSION` (shown as small text on the course list) and used as the installer version.

The version is shown with the stage (`releaseStage=alpha` in `gradle.properties` gives "0.8.0 alpha", generated as `APP_VERSION_LABEL`); the MSI itself carries only the numbers. Every change merged to `master` bumps the version, as part of that change:
- **PATCH** (`0.1.0` → `0.1.1`) — fixes and small changes within the same scope;
- **MINOR** (`0.1.x` → `0.2.0`) — a big feature, a large change, or a substantial batch of accumulated changes. PATCH resets to 0.

When unsure which one applies, ask the user.

## Git Workflow

**Before starting any new feature or bug fix, create a branch and work on it for the remainder of the session:**

```bash
git checkout -b <branch-name>   # e.g. feat/streak-counter, fix/review-date-off-by-one
```

Do not commit directly to `master`.

## Course Format Changes

The course file format is fixed by code, tests and docs together; change all of them in one go:
- the model in `content` (or `core`) with `validate()`, and `formatVersion` + its history in `docs/content-format.md` §9 when the JSON can say something new;
- `docs/course.schema.json` — the schema tests fail until it has exactly the new fields and types;
- a sample in `content/src/commonTest/.../Samples.kt` (and `examples/` when it shows the feature);
- the format description in `docs/content-format.md`.

To write a course (by hand or from a book), follow `.claude/skills/make-course/SKILL.md`; it points to `transfer.md` (carrying over from a book, `generated`, review file), `pictures.md`, `characters.md` (the cast of a course: one file, reuse, looks; for the Buryat course in the separate repository `build-notes/buryat`), `checking-forms.md` (how to settle a disputed word or form: books, dictionary, the monolingual and parallel corpora in `build-notes/buryat/corpus`, websites, native speaker), `sanity-check.md` and `handover.md` in the same folder; where it helps the learner add a visual (a code-drawn scheme or a picture; not everywhere, see `pictures.md` "Visualise where it helps"); after generating or editing a course, run the skill `review-course` (isolated reviewers).

## Additional Docs

| File | When to read |
|------|-------------|
| `docs/architecture.md` | Architecture: modules, documents vs journal, replay, behaviour decisions, known debts, sync concept, open questions |
| `docs/install.md` | Installing, updating, where the data is, backups, moving to another computer |
| `docs/roadmap.md` | What is left before the first release (iteration 1, MVP 0.8.0 alpha) and what follows |
| `docs/learning-model.md` | Learning model: units, rounds, grades, course policies, checking and typos |
| `docs/migration-plan.md` | How 0.2 was built (done): stages, invariants, decisions, open questions |
| `docs/content-format.md` | Course format: theory blocks, exercise layers and types, explanations, links, import, validation, schema, `formatVersion` |
| `docs/course.schema.json` | Formal JSON Schema of `course.json`, format 10 |
| `docs/testing.md` | How changes are verified: test levels, UI tests, screenshots, manual checks |
| `.claude/docs/architectural_patterns.md` | Code conventions: ports, DI, ViewModels, testing helpers |
