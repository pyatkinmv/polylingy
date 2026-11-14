---
name: make-course
description: Write or extend a Polylingy course file (course.json or a zip with images/) from a book, notes or other material — the exact format, what is allowed, and how to check the file before importing it. Use whenever the user asks to make, convert, extend or fix a course.
---

# Making a Polylingy course

A course is one JSON document, `course.json` (alone, or in a zip with an `images/` folder). Its format is fixed;
anything outside it is an import error. Do not invent fields, types or values.

## 0. Which file to read

| Situation | Read |
|---|---|
| Any course | this file |
| Looking for a book or course to make a course from | `find-source.md` (the search prompt and what to check) |
| Made from a book, workbook or other material | `transfer.md` (nothing lost, `generated`, topics, annotations, review file) |
| The source has pictures, or the course needs some | `pictures.md` |
| The course uses videos from the internet (explainers, songs, speech) | `video.md` (roles, where they live, the theory a song needs) |
| A course was generated or changed | `sanity-check.md`, then the skill `review-course` (isolated reviewers), then `handover.md` |

## Working with the user

- Understand a remark before acting on it: find what is actually wrong, then fix the cause, not only the example.
- Ask before mass changes (many exercises, a new section, a new rule) and before arguable choices such as where
  readings go; do not decide them alone.
- Never add what the user did not ask for (links, extra sections); never remove what they asked for. Pictures are the exception inside limits: a course should look pleasant
  and be easy on the eye, so where a unit's theory is clearer with a picture or a diagram, add one (see `pictures.md`, "Visualise where it helps"), but only there; do not
  illustrate everything.
- Everything made for the course (pictures, voices, video, names, situations) fits the cultural setting of the course; see `pictures.md`, "Cultural context".
- Say plainly what was not verified. The user may not read the language of the course.

## 1. Read the format first

- `docs/course.schema.json` — the formal JSON Schema (format 9): every object, field and allowed value. It is the
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
- **every exercise of a topic covers the whole topic on its own** (all its words, all its facts): the picker shows a random exercise and a topic counts as learned after a few passes in a row, so material that sits in only one exercise may never be seen. Make several complete exercises of different forms; never split the material of a topic between exercises. Splitting a long exercise is fine only when each part still covers everything (or the topic itself is split into smaller topics);
- every `{{…}}` variant ≤ 60 characters (expand answers ≤ 150); `choice` with `options` has exactly one blank;
- an `expand` model answer must itself contain its `keys` in order;
- pictures: `src` is `https://…` or `images/…` inside the zip; `alt` is required;
- ids may be left out (import adds them); `formatVersion` is `9`.

## 2a. Meaning gate: before carrying anything from a source

A paragraph, rule or table is not carried over because it is in the book. For each one write **one sentence: what the learner can do after this that they could not before**
(read, write, say or recognise something). Keep the sentences in `build-notes/<course>/` (like `review-decisions.md`).
- No sentence: do not carry it, or make it a one-line reference note (`tip` kind `note`, title "Справка"), with no exercise of its own.
- **All the examples, or marked "for example".** If the source gives a list of examples for a rule, carry the whole list, or say "for example" and say it is part of a longer
  list. Never take a random part and present it as the whole rule. If the source says "and for other reasons" without giving them, do not make a rule out of it: say what
  the learner can rely on ("learn these words").
- **Contradiction check.** Before writing a rule with "some words" or "usually", look through the unit's own examples for words that break it, and say so ("not like this: …").
  A rule the learner cannot apply (no criterion, no list) is a list of words to learn, not a rule.
- **No history for a beginner.** Old spellings, etymology and "why it is written so" are left out unless they change what the learner reads or writes; at most one line of reference.
- Terms the learner does not know (diphthong, articulation, orthography…) are explained when first used or not used at all; test with the learner's eyes, not the author's.
- Cheap alarms in `sanity.py`: learned words in the early units, theory words that never appear in an exercise, matrix rows "partly" without a reason.

## 3. Theory elements and the look of a course (format 9)

Theory is made of a few composable blocks; pick the block by what the learner needs to see (details: `docs/content-format.md` §4, showcase course `examples/theory-showcase.zip`):
- `tip` with `kind` `tip`/`note`/`rule`/`warning` for remarks; `fact` and `culture` (with `title`, and an `aside` picture) for a fact or a cultural note;
- `grid` (2–4 columns of any blocks) for side by side; `situation` with a `frame` (email, article, …); `dialogue` with `look: bubbles` and `portraits`;
- `table` with `appearance` (fullWidth/onlyAsWideAsContent, card/none background, highlighted header row, `highlightedColumns`, striped rows, lines between rows); short tables: `onlyAsWideAsContent`;
- `==mark==` / `=={2}mark==` in text: tones are numbers 1–5, roles not colours; give a mark one meaning across the course (root, ending, the pattern).
- Colours are never written in a course. The learner picks a palette and a style in the course settings; the file may only name defaults in `rules.display.palette` and `rules.display.style`. Pictures drawn by code are transparent with meaning colours fixed (`pictures.md`).
- **Critical: no meta-comments in the course.** Theory, instructions, tips, notes, descriptions and titles never mention
  the source: no "the book says", "as in the book", "the textbook suggests", "not carried over", "on the book's website",
  "the Activity Book", unit or page numbers of the source, and nothing about what the course copies, adapts or leaves out
  or what the app does itself ("in the app the schedule does it"). The learner sees one course that speaks for itself.
  What the app does by itself (flashcards, repeating, scheduling) or cannot do (handwriting, the book's photos) is left
  out silently, or becomes a plain hint ("say each word aloud"). Notes on what was carried over go in the chat or in a
  report file, never in `course.json`.
- **Never add resources of the source to the course**: no links to its website, presentations, transcripts, worksheets,
  flashcards (Memrise etc.), a grammar book or other books of the series, no handwriting practice or videos about
  writing by hand, no "materials" section. Files the user asks to look at (a presentation) are read and only what the
  lesson needs is used (words become an exercise); no link stays. A `video` block is named by its content
  ("Видео: приветствия"), never by its origin or page. Pictures are drawn only where they carry the lesson.
- After generating a course run `python .claude/skills/make-course/lint.py <course.json|zip>`: it must report 0
  suspicious texts (source names, pages, "сайт", "материалы", "прописи" and the like).
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

## Size of a unit (a floor, not a target)

A unit is one study session. Aim for **5–6 topics, about 20 exercises, theory of at least 3000 characters, 35–60 new words**. If the source gives less, say why in the build notes, but do not leave it short by default. What fills a unit, in this order: (1) the full rules of the lesson from the source, not only a scheme of them (conditions, exceptions, examples); (2) a topic with the vocabulary of the lesson's theme; (3) a topic "Читаем": a short text or dialogue of our own, built from the forms of the unit, with questions; (4) a mixed topic that makes the learner tell this unit's form from earlier ones. Pictures follow the need of the unit, not a quota, but the **picture pass** (`pictures.md`, "The picture pass is a step") is mandatory: write the list of sections with a picture or the reason for none, before building and before handing over; take existing pictures first, and give each theory picture one exercise (`pictures.md`, "Reuse before drawing", "Pictures in exercises"). Before handing a unit over, read its `unitN_student.txt` (what the learner sees) and solve the exercises yourself.

## 3. Check before handing over

A valid file is not yet a good course: follow `sanity-check.md` and `handover.md` too. Vary the exercise forms (no form over ~40% of a topic) and give the answers an `explanation`: see `sanity-check.md`, section 2.

```bash
./gradlew :client:checkCourse -Pcourse=path/to/course.json
```

It runs exactly the checks of import (shape, markup, blanks, links, model answers, files in the zip) and prints
every problem with its path, like `units[0].topics[2].exercises[1].items[0].keys: …`. Fix and re-run until it says
`OK`. Only then give the file to the user (or import it).

**Open questions and review decisions go to one file** (`review-decisions.md` in the build notes), not only to the chat: what the reviewers found, what was accepted or rejected (and why), what needs a native speaker. Forms derived by a rule are listed by a script from the lexicon marks. Update it after every unit.
