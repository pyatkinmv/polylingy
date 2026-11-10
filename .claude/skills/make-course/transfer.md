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
- Keep the map true. Every "in the course" note names a place; after any change (a removed exercise, a reverted block, a new picture) check
  the notes against `course.json` itself, not against memory. A map that says "carried" about something that is gone hides the loss.
- Every picture of the source that is not in the course goes into a list with its page and the reason (and whether it should come back),
  so the user can judge the omissions; a picture that only decorates is a different case from a picture that an exercise needs.
- Look for the answer key of every exercise before saying there is none. Textbooks put the keys in the back (and workbooks print their own); an exercise
  that "cannot be checked" usually can. The key settles the answer, never a guess from a picture.
- Numbers printed near pictures or items that a recording names ("1 … 2 …") belong to the picture: the learner finds on the page what the voice announces.
  Keep them (draw them again if the picture is redrawn).
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
- A group of new words is a small topic of its own that stands **before the first topic that uses the words** in phrases or conversations, not
  at the end of the unit; words that belong to a later topic open that topic.
- A topic is one **whole round**: everything it names is covered by its exercises, no random subsets of items. A long
  vocabulary is split into several small topics by theme. Every topic has at least one exercise without typing.
- Do not mix different skills in one topic: conversations are their own topic, not the tail of a word topic.
- The app stays course-agnostic: nothing about one language or one book goes into app code. If the course needs
  something the format cannot say, stop and ask (see "Working with the user" in `SKILL.md`).

## Carry over by meaning, not word for word

Polylingy is a platform for courses, not only for languages, and a source is written for a particular audience in a particular language and context.
The course you make has its own: the language the course is written in and the context it is made for (who learns it, what they already know, what
the user asked for). A one-to-one copy or translation is often wrong, so ask for every explanation what the **target course** needs. This is about the
course, not about the user's own language: a course the user wants to keep in the source's language stays in it, and you do not translate it.

- **Language of the explanations.** Decide it once with the user, and keep it. If the course is in a different language from the source, re-explain
  through the course's language, not through the source's.
- **Analogies and examples** (a comparison, a sample sentence, a pun, a mnemonic) are carried over by what they show. Where the course's language or
  context has its own equivalent, use it and drop the source's. Keep the source's only where there is no equivalent, then briefly, as an aside,
  never as the main explanation.
- **Terms.** Introduce each term the first time it is needed, in the course's language, with the source term next to it if it will be met later. A
  term the source used without defining (its reader knew it) is a gap: define it.
- **Assumed knowledge.** Ask of every explanation: what did the source's reader already know that this course's learner does not? Add a short
  explanation where nothing in the course teaches it.
- When the audit finds such places, fix the whole course in one pass, not only the one somebody noticed.

## Readings over words (annotations)

- Follow the source and common sense: does an annotation help the learner here? As a rule — in theory and in
  conversations, including the exercises built from conversations (order the lines, choose your line).
- Never where the exercise asks about the word, its reading or its meaning: an annotation that gives the answer away
  is a bug. In a "choose your line" item the options often differ by a form (gender, ending) that the reading spells out: no readings there;
  in "put the lines in order" they only help to read and stay. Tables of letters, joinings and syllables stay bare.
- Where it is arguable, ask the user; do not decide alone. The learner can hide annotations with the course setting.

## When the user cannot read the language

Write `build-notes/<course>-to-review.md` for a native speaker: every place the user cannot check — vowel marks,
readings, translations, words not from the source, generated exercises — with the unit and topic of each, so the
reviewer can find it. Say in the chat that the file exists. Do not call such a course verified.

## Right-to-left scripts

Write the text as the source does, with the marks it prints; the app finds the direction. Do not wrap Arabic in `*…*`
(italics distort it). Letters of a word go in an `order` answer as separate tiles.
