# Architectural Patterns

Patterns confirmed across multiple files in this codebase.

## 1. Constructor Injection for Dependencies

Repositories are created once at the top (`lib/app.dart:14-15`) and passed down via required constructor parameters — no service locator, no `Provider`, no `InheritedWidget`.

Applies to:
- `HomeScreen({required topicRepo, required progressRepo})` — `lib/screens/home_screen.dart`
- `StudyScreen({required course, required progressRepo, required topicRepo, required policy, required topicPicker, required graduationPolicy})` — `lib/screens/study_screen.dart`

When adding a new screen that needs data access, receive repos as constructor args. Learning rules (policies) are injected the same way: `HomeScreen` picks the concrete implementations when it opens `StudyScreen`.

## 2. Repository Pattern: Domain Models Over DB Rows

`ProgressRepository` (`lib/data/progress_repository.dart`) translates between Drift-generated `TopicProgressTableData` rows and the domain `TopicProgress` model. Screens and services never touch Drift types directly.

- DB row → domain: `_fromRow()` at `lib/data/progress_repository.dart:12`
- Domain → DB row: `TopicProgressTableCompanion(...)` at `lib/data/progress_repository.dart:33`

Rule: only `ProgressRepository` imports both `database.dart` and `topic_progress.dart`.

## 3. Pure Service Layer

`lib/services/` has zero Flutter, Drift, or I/O imports. Each learning rule is an abstract interface with swappable implementations:

- `RepetitionPolicy` → `Sm2Policy` (`lib/services/sm2_policy.dart`) — schedules the next review after a topic completes a session
- `GraduationPolicy` → `ConsecutiveCorrectPolicy` (`lib/services/graduation_policy.dart`) — decides when a topic is done within a session
- `TopicPicker` → `RandomTopicPicker`, `FixedTopicPicker` (`lib/services/topic_picker.dart`) — chooses the next topic

All side effects (persistence, UI) happen in the screen after calling the service.

Pattern: `RepetitionPolicy` and `TopicPicker` receive values and return new values. `GraduationPolicy` is the exception — it keeps in-memory per-session counters, so create a fresh instance per study session.

## 4. Immutable Domain Models with `copyWith`

`TopicProgress` is an immutable value object (`lib/models/topic_progress.dart:16`). Mutations are expressed by returning a new instance via `copyWith`. This is used consistently in `Sm2Policy.advance`.

`Course`, `Topic`, and `Exercise` (`lib/models/course.dart`) are also immutable and constructed via `fromJson` factories.

## 5. Local `StatefulWidget` State (No Global State Management)

Both screens (`HomeScreen`, `StudyScreen`) manage state locally with `setState`. There is no Redux, BLoC, Riverpod, or `ChangeNotifier` in this project. Async data is loaded in `initState` and exposed as nullable fields (`_courses`, `_eligibleIds`, etc.) with loading/error guards in `build`.

When adding screens: follow the same pattern unless complexity clearly warrants a state management library.

## 6. Screen-Local Private Classes

UI sub-components that are only used within one screen are defined as private classes in the same file (prefixed `_`), not extracted to separate files.

Examples:
- `_CourseStats`, `_CourseCard` in `lib/screens/home_screen.dart`
- `_StudyState`, `_ResultMode`, `_ExplanationBox`, `_FormattedContent` in `lib/screens/study_screen.dart`

Extract to a separate file only when a widget is reused across screens.

## 7. Enum Serialization via `value` / `fromInt`

`ProgressStatus` stores an `int value` in SQLite and provides a `fromInt` factory for deserialization (`lib/models/topic_progress.dart:1-13`). The mapping lives in the model, not in the DAO or repository.

## 8. Date Precision: Day-Level Only

Dates are always normalized to midnight before comparison or storage:

- `lib/screens/study_screen.dart` — `_today()` strips time component and is passed to every policy call
- `lib/screens/home_screen.dart` — same in `HomeScreen._load()`
- Policies (`Sm2Policy`, `TopicProgress.isEligibleToday`) expect an already-normalized `today` and do not normalize themselves

Never compare raw `DateTime.now()` against stored dates — always normalize first.
