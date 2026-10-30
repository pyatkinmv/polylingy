# Polylingy

Flutter desktop app for language learning via spaced repetition. Topics are loaded from user-managed JSON files; progress is stored locally in SQLite.

## Tech Stack

- **Flutter** (Windows / macOS / Linux desktop only — no mobile)
- **Drift** `^2.20.0` — type-safe SQLite ORM with code generation
- **sqlite3_flutter_libs** `^0.5.0` — native SQLite binaries
- **path_provider** `^2.1.0` + **path** `^1.9.0` — filesystem access

Dev tools: `drift_dev`, `build_runner`, `flutter_lints`.

## Key Directories

| Path | Purpose |
|------|---------|
| `lib/models/` | Pure domain models; no Flutter or DB imports |
| `lib/data/` | Drift schema, DAOs, and repository wrappers |
| `lib/services/` | Pure business logic (SR algorithm) |
| `lib/screens/` | Flutter UI; receives repos via constructor injection |
| `assets/` | Bundled sample course JSON, copied to disk on first run |

Key files:
- `lib/main.dart:1` — entry point; creates `AppDatabase`, runs app
- `lib/app.dart:7` — `PolylingyApp`; instantiates repos, wires `HomeScreen`
- `lib/data/database.dart:8` — Drift schema + `ProgressDao`
- `lib/data/database.g.dart` — **generated**, do not edit manually
- `lib/services/repetition_policy.dart` / `sm2_policy.dart` — SR scheduling (`advance`, `eligibleTopicIds`)
- `lib/services/graduation_policy.dart` — when a topic is done within a session (3 correct in a row)
- `lib/services/topic_picker.dart` — which topic to show next
- `lib/screens/home_screen.dart` — wires the concrete policies into `StudyScreen`
- `lib/models/topic_progress.dart:35` — `isEligibleToday` eligibility logic

## File Storage

```
<Documents>/polylingy/
  progress.db        # SQLite database
  courses/           # User-managed JSON course files
    sample_course.json
```

Topic ID format: `"${courseId}::${topicJson.id}"`. Each topic stores its own `id` in the course JSON; `TopicRepository.ensureTopicIds` backfills missing ids with the topic's index (matching legacy `courseId::index` progress rows) and gives new topics a random id, so deleting a topic never shifts other topics' progress.

## Essential Commands

```bash
# Install / update dependencies
flutter pub get

# Regenerate Drift code after schema changes
dart run build_runner build --delete-conflicting-outputs

# Static analysis
flutter analyze

# Run on desktop
flutter run -d windows   # or -d macos / -d linux

# Tests
flutter test
```

> **Windows note**: running the app requires Developer Mode enabled (needed for symlinks created by Flutter plugins).

## Git Workflow

**Before starting any new feature or bug fix, create a branch and work on it for the remainder of the session:**

```bash
git checkout -b <branch-name>   # e.g. feat/streak-counter, fix/review-date-off-by-one
```

Do not commit directly to `main`.

## Additional Docs

Check these when relevant:

| File | When to read |
|------|-------------|
| `.claude/docs/architectural_patterns.md` | Adding features, understanding design decisions, dependency injection conventions |
