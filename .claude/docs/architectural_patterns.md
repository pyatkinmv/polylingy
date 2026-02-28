# Architectural Patterns

Patterns confirmed across multiple files in this codebase.

## 1. Constructor Injection for Dependencies

Repositories are created once at the top (`lib/app.dart:14-15`) and passed down via required constructor parameters — no service locator, no `Provider`, no `InheritedWidget`.

Applies to:
- `HomeScreen({required topicRepo, required progressRepo})` — `lib/screens/home_screen.dart:9-10`
- `StudyScreen({required course, required progressRepo})` — `lib/screens/study_screen.dart:11-12`

When adding a new screen that needs data access, receive repos as constructor args.

## 2. Repository Pattern: Domain Models Over DB Rows

`ProgressRepository` (`lib/data/progress_repository.dart`) translates between Drift-generated `TopicProgressTableData` rows and the domain `TopicProgress` model. Screens and services never touch Drift types directly.

- DB row → domain: `_fromRow()` at `lib/data/progress_repository.dart:12`
- Domain → DB row: `TopicProgressTableCompanion(...)` at `lib/data/progress_repository.dart:33`

Rule: only `ProgressRepository` imports both `database.dart` and `topic_progress.dart`.

## 3. Pure Service Layer

`SrService` (`lib/services/sr_service.dart`) has zero Flutter, Drift, or I/O imports. It takes and returns domain models, making it trivially testable. All side effects (persistence, UI) happen in the screen after calling the service.

Pattern: services receive values, return new values — they do not read or write state.

## 4. Immutable Domain Models with `copyWith`

`TopicProgress` is an immutable value object (`lib/models/topic_progress.dart:16`). Mutations are expressed by returning a new instance via `copyWith`. This is used consistently in `SrService.onCorrect` and `SrService.onIncorrect`.

`Course`, `Topic`, and `Exercise` (`lib/models/course.dart`) are also immutable and constructed via `fromJson` factories.

## 5. Local `StatefulWidget` State (No Global State Management)

Both screens (`HomeScreen`, `StudyScreen`) manage state locally with `setState`. There is no Redux, BLoC, Riverpod, or `ChangeNotifier` in this project. Async data is loaded in `initState` and exposed as nullable fields (`_courses`, `_eligibleIds`, etc.) with loading/error guards in `build`.

When adding screens: follow the same pattern unless complexity clearly warrants a state management library.

## 6. Screen-Local Private Classes

UI sub-components that are only used within one screen are defined as private classes in the same file (prefixed `_`), not extracted to separate files.

Examples:
- `_CourseStats`, `_CourseCard` in `lib/screens/home_screen.dart:121,137`
- `_LabeledText`, `_StudyState` in `lib/screens/study_screen.dart:8,267`

Extract to a separate file only when a widget is reused across screens.

## 7. Enum Serialization via `value` / `fromInt`

`ProgressStatus` stores an `int value` in SQLite and provides a `fromInt` factory for deserialization (`lib/models/topic_progress.dart:1-13`). The mapping lives in the model, not in the DAO or repository.

## 8. Date Precision: Day-Level Only

Dates are always normalized to midnight before comparison or storage:

- `lib/screens/study_screen.dart:79` — `_today()` strips time component
- `lib/services/sr_service.dart:44` — same normalization before filtering
- `lib/screens/home_screen.dart:37` — same in `HomeScreen._load()`

Never compare raw `DateTime.now()` against stored dates — always normalize first.
