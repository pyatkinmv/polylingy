# Polylingy

A desktop app for language learning with spaced repetition, in the spirit of Anki but built around a textbook:
a course has units with a page of theory, units have topics, and each topic has several exercises of different kinds.
A topic is learned and reviewed as a whole; each time one of its exercises is picked.

Everything runs offline; data stays on your machine.

## Features

- Theory pages built from blocks: sections, situations with pictures, tables, examples, word lists, tips, links
- Eight exercise types: gaps, choice, pictures with gaps, tables with gaps, matching, word order, expanding notes,
  find and fix; any of them can sit in a frame (an email, an article) with a picture next to it
- Answers are checked item by item; one typo per item is forgiven unless you turn that off
- Explanations after every check: per exercise, per topic, and short notes per item, with links into the theory
- Study settings per course, as four questions: when an answer counts, when a topic is done, when it comes back,
  what comes next (conveyor, shuffle or alternate, with a daily limit of new topics); ready sets fill in the first three
- Units and topics can be added, edited (as JSON, with a live preview) and deleted
- Courses imported from and exported to `course.json` or a zip with pictures
- Every answer is kept in a journal; progress is computed from it

## Running

Requires a JDK to run Gradle (21+); the JDK 25 used by the project is downloaded automatically.

```bash
./gradlew :app:run     # run the app
./gradlew check        # all tests, including headless UI tests and coverage
```

Data lives in `<Documents>/polylingy/` (`polylingy.db`, course pictures, logs). A sample course is added on first launch;
more courses to try are in `examples/` — import them with the Import button.

A Windows installer can be built with `./gradlew :app:releaseMsi` (into `app/build/release/`, the version and stage in its name; see `docs/install.md`);
it isn't checked for now.

## Course format

```json
{
  "formatVersion": 3,
  "name": "English Grammar",
  "units": [
    {
      "title": "Present continuous",
      "theory": { "format": "blocks", "blocks": [
        { "type": "section", "label": "A", "title": "Happening now",
          "blocks": [ { "type": "text", "text": "He**’s cycling** to work." } ] } ] },
      "topics": [
        { "title": "Things happening now",
          "exercises": [
            { "type": "gaps", "instruction": "Complete the sentences.",
              "items": [ { "text": "Listen! Somebody {{’s singing|is singing}}[[sing]]." } ] } ] } ]
    }
  ]
}
```

The full format — every block and exercise type, explanations, links, pictures, validation and `formatVersion` —
is in `docs/content-format.md`; its formal description is the JSON Schema `docs/course.schema.json`.
To check a course file the way import does, without the app:

```bash
./gradlew :client:checkCourse -Pcourse=path/to/course.json
```

## Project layout

| Module | Contents |
|---|---|
| `content` | course content: markup, theory blocks, exercise types, validation, strict JSON |
| `content-compose` | drawing theory and exercises with Compose |
| `core` | business logic: course model, journal, checking, course policies, `replay()`, services |
| `client` | storage: SQLite, import/export with pictures |
| `app` | Compose desktop UI |

See `docs/architecture.md` for the design, `docs/learning-model.md` for how learning works and `docs/testing.md`
for how changes are verified.
