# Polylingy

A desktop app for language learning via spaced repetition, in the spirit of Anki but built around flexible exercises: a course consists of topics, each topic has several exercises with blanks, and a topic is scheduled as a whole.

Everything runs offline; data stays on your machine.

## Features

- Spaced repetition (SM-2 variant): a topic "graduates" after 3 correct answers in a row, and the number of mistakes on the way decides the next interval
- Exercises with blanks (`{0}`, `{1}`), answered comma-separated; automatic check plus your own Correct/Incorrect rating (keys `1` / `2`)
- Explanations per exercise and per topic (plain text or simple HTML)
- Courses imported from and exported to JSON files
- Every answer is kept in a journal; progress is computed from it

## Install

Build the Windows installer and run it (installs for the current user, no admin rights, bundles its own Java):

```bash
./gradlew :app:packageMsi
```

The installer is written to `app/build/compose/binaries/main/msi/`.

## Development

Requires a JDK to run Gradle (21+); the JDK 25 used by the project is downloaded automatically.

```bash
./gradlew :app:run     # run the app
./gradlew check        # all tests, including headless UI tests
```

Data lives in `<Documents>/polylingy/polylingy.db`.

## Course format

```json
{
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
        },
        {
          "task": "red: {0} (masculine) / {1} (feminine)",
          "answer": {"0": "rojo", "1": "roja"},
          "exampleExplanation": "Multiple blanks: answer them comma-separated."
        }
      ]
    }
  ]
}
```

Optional fields: `"generalExplanationFormat": "HTML"` for HTML explanations, and `"id"` (UUID) on the course, topics and exercises — missing ids are generated on import.

## Project layout

| Module | Contents |
|---|---|
| `core` | business logic: models, journal, SM-2 and other rules |
| `client` | storage: SQLite, JSON import/export |
| `app` | Compose UI |

See `docs/architecture.md` for the design and `docs/testing.md` for how changes are verified.
