# Architectural Patterns

Conventions used across the codebase. The big picture is in `docs/architecture.md`.

## 1. Ports and Adapters

`core` declares what it needs from the outside world as interfaces in `core/.../ports/Ports.kt` (`CourseRepository`, `EventLog`, `Clock`). Implementations live outside the core: `client` provides SQLite ones (`LocalStorage`), tests provide in-memory ones (`InMemoryCourseRepository`, `InMemoryEventLog`, `FixedClock` in `core/src/commonTest/.../Fixtures.kt`).

Rule: `core` has no UI, SQL, file or network code. If something platform-specific is needed, add a port.

## 2. Swappable Rules

A course's policies are four interfaces in `core/.../learning/Policies.kt`, tied by the grade type `G` in `CourseRules<M, G>`
so a mismatched set doesn't compile:

- `GradingRule<G>` → `AllCorrect`, `Threshold` — an answer's item results to a grade
- `MasteryPolicy<G>` → `Consecutive` — is the round mastered
- `Scheduler<M, G>` → `Once`, `Ladder`, `Sm2Scheduler` — memory between rounds and the next review date (null = learned for good)
- `TopicPicker` → `ReviewsFirst`, `RandomOrder` — what to show next

A course names a preset (`quick`, `ladder`, `sm2`) and may replace parts (`RulesSpec`, `Presets.kt`). Checking answers is
separate: `ExerciseChecker` per exercise type and `TextMatcher` (`core/.../rules/`). Rules are pure; state is always derived
by `replay()`.

Content types follow the same idea: a new exercise type is a class in `content` with `validate()`, a view in
`content-compose`, a checker in `core` and a sample in tests — the compiler points at each `when` that needs it.

## 3. Journal Plus Replay

Answers are appended as `EventEnvelope`s (`AnswerSubmitted` keeps the response and per-item results, not a grade) and never changed. `replay(course, events, rules)` folds the journal into per-topic `TopicState` (memory + current round). Use cases call it on demand (see `StudyService`). Events for content that no longer exists are ignored. Ordering is by instant with a stable sort, so equal instants keep journal order.

## 4. Use-Case Services

Screens never touch repositories for business decisions. They call:

- `StudyService` — topic statuses and `ProgressCounts`, next exercise, practising a topic, answering, theory first
- `CourseService` — import, name and description, delete (unique names, bumps `revision`)
- `CourseLibrary` (client) — import and export of files with pictures

## 5. Manual Dependency Injection

`AppContainer` builds every long-lived object once (storage, clock, `Random`, services). ViewModels receive what they need through constructors; `App.kt` creates them with `viewModel { … }`. No DI framework.

Clock and `Random` are injected so tests are repeatable.

## 6. ViewModels With StateFlow

Each screen has a ViewModel exposing `StateFlow` state (`CourseListViewModel`, `CourseViewModel`, `StudyViewModel` with a sealed `StudyState`). Actions are plain methods; operations are synchronous (the data is small). Errors meant for the user come back as `Result` or outcome types rather than thrown into the UI. Screens never see the nav controller: they get a `Navigator`.

## 7. Platform Code at the Edges

Platform-specific parts sit in `desktopMain` behind small interfaces: `DesktopStorage` (DB path and driver), `CourseArchive` (zip), `FileImageStore` / `HttpImageFetcher` (pictures), `CourseFiles` / `DesktopCourseFiles` (native file dialogs), `ImageDecoder` (Skia), `Main.kt` (window). Screens and ViewModels are in `commonMain`.

## 8. Testing Helpers

- Core rules: `core/src/commonTest/.../Fixtures.kt` builders (`course`, `topic`, `answer`, `at`)
- SM-2 guard: `core/src/jvmTest/.../Sm2GoldenTest.kt` with golden data from the first implementation of the rules
- Content: `content/src/commonTest/.../Samples.kt`, `core/src/commonTest/.../content/CourseSamples.kt` — files as an author writes them
- UI: `app/src/desktopTest/.../ui/UiTestSupport.kt` — `appTest(seed) { click(…); menu(…); fill(…); text(…); screenshot(…) }`
- Examples: `examples/` courses must always import cleanly (`CourseLibraryTest.theExampleCoursesImportWithoutWarnings`)

See `docs/testing.md` for the workflow.
