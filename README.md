# Polylingy

A Flutter desktop app for language learning via spaced repetition.

Topics are loaded from user-managed JSON files; progress is stored locally in SQLite — no account or internet connection required.

## Features

- Spaced repetition scheduling (SM-2-inspired)
- JSON-based courses — add your own topics without touching code
- Progress persisted locally in SQLite
- Clean desktop UI (Windows / macOS / Linux)

## Getting Started

### Prerequisites

- [Flutter SDK](https://docs.flutter.dev/get-started/install) with desktop support enabled
- Windows: [Developer Mode](https://learn.microsoft.com/en-us/windows/apps/get-started/enable-your-device-for-development) must be enabled (required for Flutter plugin symlinks)

### Run

```bash
flutter pub get
flutter run -d windows   # or -d macos / -d linux
```

### Add a course

Place a JSON file in `<Documents>/polylingy/courses/`. A sample course is copied there automatically on first run.

Course format:

```json
{
  "id": "my_course",
  "name": "My Course",
  "topics": [
    {
      "subject": "Topic name",
      "generalExplanation": "Optional explanation shown after each answer.",
      "exercises": [
        {
          "task": "Translate: hello",
          "answer": "hola",
          "exampleExplanation": "Used in informal greetings."
        }
      ]
    }
  ]
}
```

## Tech Stack

- **Flutter** — desktop UI
- **Drift** — type-safe SQLite ORM
- **sqlite3_flutter_libs** — native SQLite binaries

## Development

```bash
# Regenerate Drift code after schema changes
dart run build_runner build --delete-conflicting-outputs

# Static analysis
flutter analyze

# Tests
flutter test
```
