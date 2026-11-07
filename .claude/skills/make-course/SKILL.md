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
| sound | `audio`: `audio/….mp3` | "listen and choose": a big play button over the body |
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
- ids may be left out (import adds them); `formatVersion` is `6`.
- **Critical: no meta-comments in the course.** Theory, instructions, tips, notes, descriptions and titles never mention
  the source: no "the book says", "as in the book", "the textbook suggests", "not carried over", "on the book's website",
  "the Activity Book", unit or page numbers of the source, and nothing about what the course copies, adapts or leaves out
  or what the app does itself ("in the app the schedule does it"). The learner sees one course that speaks for itself.
  What the app does by itself (flashcards, repeating, scheduling) or cannot do (handwriting, the book's photos) is left
  out silently, or becomes a plain hint ("say each word aloud"). Notes on what was carried over go in the chat or in a
  report file, never in `course.json`.
- Never copy copyrighted text verbatim from a book: write your own sentences after its structure.
- Reading aids over words: `[老师](lǎoshī)`; write annotated words of Chinese/Japanese without spaces between them
  (except in `order` answers, where spaces separate the tiles). Dialogues: the `dialogue` block, a sound per line.
- Never annotate what an exercise asks about: a question on how a word reads shows the bare word, and a "type the
  word" item has no hint that spells it out — an annotation or hint there gives the answer away.
- A book that prints a recording beside a section (a headphones icon with "1:03") gets a `track` block at that place:
  one `audio` file, the book's own track, with the title the book gives it; nothing is cut up. A video the book
  sends the reader to is a `video` block with its `https://` address; exercises about it use what the book prints
  (a transcript, a list of what is said), never the video itself. A track that voices a whole exercise goes in that
  exercise's `audio` layer, and its items follow the order of the recording.
- Arabic and other right-to-left scripts: write the text as the book does, with the vowel marks it prints; the app finds
  the direction by itself. Build a word from letters with an `order` answer whose words are the letters (`"ب ي ت"`).
- Sections of theory: give a `label` (A, B, C) only when the source is lettered so; otherwise only a `title`.
- Translations (`translation` of a dialogue line or an example) show whenever they are written: put them in theory,
  where they help understanding; never where an exercise asks for the meaning or a translation.
- Several books of one course (textbook, workbook, reference): one unit per lesson; theory from the textbook plus the
  reference's explanations and translations for that lesson; topics by skill (words, each grammar point, phonetics,
  characters), each holding the exercises of all books on that skill.
- Sounds: `.mp3`/`.wav` in the zip under `audio/`; a sound belongs to a whole word, line or exercise.
- Every topic should have at least one exercise without typing (`choice`, `match`, `order`) when learners may turn on
  "avoid typing"; a topic with only typing exercises still works.

## 3. Check before handing over

```bash
./gradlew :client:checkCourse -Pcourse=path/to/course.json
```

It runs exactly the checks of import (shape, markup, blanks, links, model answers, files in the zip) and prints
every problem with its path, like `units[0].topics[2].exercises[1].items[0].keys: …`. Fix and re-run until it says
`OK`. Only then give the file to the user (or import it).
