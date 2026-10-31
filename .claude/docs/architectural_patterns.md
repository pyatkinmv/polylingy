# Architectural Patterns

Conventions used across the codebase. The big picture is in `docs/architecture.md`.

## 1. Ports and Adapters

`core` declares what it needs from the outside world as interfaces in `core/.../ports/Ports.kt` (`CourseRepository`, `EventLog`, `Clock`). Implementations live outside the core: `client` provides SQLite ones (`LocalStorage`), tests provide in-memory ones (`InMemoryCourseRepository`, `InMemoryEventLog`, `FixedClock` in `core/src/commonTest/.../Fixtures.kt`).

Rule: `core` has no UI, SQL, file or network code. If something platform-specific is needed, add a port.

## 2. Swappable Rules

Learning rules are interfaces with default implementations, bundled in `LearningRules`:

- `RepetitionPolicy` → `Sm2Policy` — schedules the next review after graduation
- `GraduationPolicy` → `ConsecutiveCorrectPolicy` — pure: `(Streak, correct) -> StreakOutcome`
- `CorrectnessRule` → `RatingThenAutoCheck` — whether an answer counts as correct
- `TopicPicker` → `RandomTopicPicker`, `FixedTopicPicker`

Rules are pure functions of their inputs; state (streaks, progress) is always derived by `replay()`.

## 3. Journal Plus Replay

Answers are appended as `EventEnvelope`s and never changed. `replay(courses, events, rules)` folds the journal into per-topic `TopicState`. Use cases call it on demand (see `StudyService`). Events for content that no longer exists are ignored. Ordering is by instant with a stable sort, so equal instants keep journal order.

## 4. Use-Case Services

Screens never touch repositories for business decisions. They call:

- `StudyService` — course counters, next exercise, submitting answers
- `CourseService` — every course edit (validates names, bumps `revision`)

## 5. Manual Dependency Injection

`AppContainer` builds every long-lived object once (storage, clock, `Random`, services). ViewModels receive what they need through constructors; `App.kt` creates them with `viewModel { … }`. No DI framework.

Clock and `Random` are injected so tests are repeatable.

## 6. ViewModels With StateFlow

Each screen has a ViewModel exposing `StateFlow` state (`CourseListViewModel`, `StudyViewModel` with a sealed `StudyState`). Actions are plain methods; operations are synchronous (the data is small). Errors meant for the user are returned as messages (`String?`) rather than thrown into the UI.

## 7. Platform Code at the Edges

Platform-specific parts sit in `desktopMain` behind small interfaces: `DesktopStorage` (DB path and driver), `CourseFiles` / `DesktopCourseFiles` (native file dialogs), `Main.kt` (window). Screens and ViewModels are in `commonMain`.

## 8. Testing Helpers

- Core rules: `core/src/commonTest/.../Fixtures.kt` builders (`course`, `topic`, `answer`, `at`)
- SM-2 guard: `core/src/jvmTest/.../Sm2GoldenTest.kt` with golden data from the first implementation of the rules
- UI: `app/src/desktopTest/.../ui/UiTestSupport.kt` — `appTest(seed) { click(…); menu(…); text(…); screenshot(…) }`

See `docs/testing.md` for the workflow.
