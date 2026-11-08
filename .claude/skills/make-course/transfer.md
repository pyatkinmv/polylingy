# Carrying a course over from a book

Read this when the course is made from a book, a workbook or other material. The format rules stay in `SKILL.md`.

## Nothing gets lost, nothing is invented silently

- Go through the source page by page (every book of the series) and give every piece — a theory section, a table, a
  recording, a video, a picture, an exercise — one of three fates: **carried over** (where), **changed** (what and
  why) or **left out** (why). Left out silently is allowed only for what the app does itself or cannot do (handwriting,
  flashcards, scheduling); everything else is accounted for.
- The result is a **marked-up PDF of the source** in `build-notes/` (git-ignored): the same pages with colours for
  carried / changed / left out and a short note by each mark. It is a deliverable, not an option. Notes on what was
  carried over live there and in the chat — never in `course.json` (see the no-meta-comments rule in `SKILL.md`).
- Carry data carefully: words, forms, numbers, answer keys, track numbers. Copy them from the source, then re-read
  the generated file against the source once more. Wrong keys and swapped columns are the usual losses.
- The source's own wording is not copied (copyright); its structure, facts and answer keys are.
- A recording printed beside a section is a `track` block there; one that voices a whole exercise is that exercise's
  `audio`. Count the tracks of the source and the tracks of the course: they must match unless one is left out with a
  reason.

## Exercises that are not in the source: `generated`

- Every exercise with no prototype in the source gets `"generated": true`: vocabulary drill, repetition, an exercise
  built from a table. An exercise from the source that was only fixed (a key, a typo) is not generated.
  A source exercise rebuilt so much that nothing is left of its task is generated.
- Theory blocks and pictures carry no flag. The marked-up PDF lists the generated exercises too.

## Units, topics, order

- A unit follows a lesson of the source; theory inside it keeps the **order of the source** (words and phrases come
  before the dialogues that use them).
- A topic is one **whole round**: everything it names is covered by its exercises, no random subsets of items. A long
  vocabulary is split into several small topics by theme. Every topic has at least one exercise without typing.
- Do not mix different skills in one topic: conversations are their own topic, not the tail of a word topic.
- The app stays course-agnostic: nothing about one language or one book goes into app code. If the course needs
  something the format cannot say, stop and ask (see "Working with the user" in `SKILL.md`).

## Readings over words (annotations)

- Follow the source and common sense: does an annotation help the learner here? As a rule — in theory and in
  conversations, including the exercises built from conversations (order the lines, choose your line).
- Never where the exercise asks about the word, its reading or its meaning: an annotation that gives the answer away
  is a bug. Tables of letters, joinings and syllables stay bare.
- Where it is arguable, ask the user; do not decide alone. The learner can hide annotations with the course setting.

## When the user cannot read the language

Write `build-notes/<course>-to-review.md` for a native speaker: every place the user cannot check — vowel marks,
readings, translations, words not from the source, generated exercises — with the unit and topic of each, so the
reviewer can find it. Say in the chat that the file exists. Do not call such a course verified.

## Right-to-left scripts

Write the text as the source does, with the marks it prints; the app finds the direction. Do not wrap Arabic in `*…*`
(italics distort it). Letters of a word go in an `order` answer as separate tiles.
