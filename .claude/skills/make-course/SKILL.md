---
name: make-course
description: Write or extend a Polylingy course file (course.json or a zip with images/) from a book, notes or other material — the exact format, what is allowed, and how to check the file before importing it. Use whenever the user asks to make, convert, extend or fix a course.
---

# Making a Polylingy course

A course is one JSON document, `course.json` (alone, or in a zip with an `images/` folder). Its format is fixed;
anything outside it is an import error. Do not invent fields, types or values.

## 1. Read the format first

- `docs/course.schema.json` — the formal JSON Schema (format 3): every object, field and allowed value. It is the
  authority on *shape*.
- `docs/content-format.md` — what each part means and how it looks: sections 2 (the course), 3 (text markup),
  4 (theory blocks), 5 (exercise layers and types), 6 (explanations and links), 7 (pictures).
- `examples/advanced-grammar.json` and `examples/irregular-verbs.json` — complete, valid courses to copy from.

## 2. Build exercises from what exists

Every exercise is the same layers; only the body depends on the type:

| Layer | Field | Use it for |
|---|---|---|
| task | `instruction` | the instruction line |
| words to use | `wordBank` (gaps, pictures, expand) | a box of words; read-only |
| frame | `look`: `email` (from, to, subject) or `article` (title, source) | a text that is an email, a blog post, a magazine page |
| picture | `aside`: image + `side` (left/right/top/bottom) + `size` (small/medium/large) | a photo, a speaker (picture with a caption) |
| body | the type's fields | see below |
| after checking | `explanation`, item `note` | why; free text |

Pick the type by what the learner does:

| The learner… | Type |
|---|---|
| types words into blanks (sentences, or one whole text) | `gaps` — a whole text is ONE item with many blanks |
| picks one of a few forms in each place | `choice` — options in the blank `{{right|other}}`, right one first |
| fills a table | `table` |
| matches halves | `match` |
| orders words into a sentence | `order` |
| writes a whole answer from notes | `expand` — model answer in the blank, `keys` in the needed forms |
| finds and corrects mistakes | `fix` — `{{right}}` or `{{wrong => fix|other fix}}` |
| looks at pictures and fills blanks | `pictures` |

If the material has a format none of these covers, do not approximate it with made-up fields: say so to the user and
propose either the nearest existing type or a format change (code, schema, tests and docs — see CLAUDE.md).

Rules that catch most mistakes:
- every `{{…}}` variant ≤ 60 characters (expand answers ≤ 150); `choice` with `options` has exactly one blank;
- an `expand` model answer must itself contain its `keys` in order;
- pictures: `src` is `https://…` or `images/…` inside the zip; `alt` is required;
- ids may be left out (import adds them); `formatVersion` is `3`.
- Never copy copyrighted text verbatim from a book: write your own sentences after its structure.

## 3. Check before handing over

```bash
./gradlew :client:checkCourse -Pcourse=path/to/course.json
```

It runs exactly the checks of import (shape, markup, blanks, links, model answers, files in the zip) and prints
every problem with its path, like `units[0].topics[2].exercises[1].items[0].keys: …`. Fix and re-run until it says
`OK`. Only then give the file to the user (or import it).
