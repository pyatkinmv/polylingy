# Sanity check of a generated course

`checkCourse` and `lint.py` only prove the file is valid. This check proves it is **understandable and solvable**.
Do it after generating, and again after every batch of fixes.

## 1. Read it as the learner

Walk through every unit as a learner who has only this course: theory, then each topic, then each exercise — in the
order the app shows them. For every exercise answer these:

- Is the task clear from the instruction alone? Does it say what to build or choose (not just "make a sentence")?
- Can it be solved with what the learner has seen so far (theory, earlier topics, the exercise's own `wordBank`)?
- Is there exactly one right answer? Could a second option also be right (synonyms, both genders, both orders)?
- Are the wrong options plausible but clearly wrong, and not near-duplicates of the right one? Numeric options sort
  themselves; do not rely on position.
- Does the exercise give its answer away (an annotation, a hint, a repeated word in the question)?
- Does it ask about something the course never taught? Does it repeat another exercise of the same topic?
- Is the text short enough to read (every `{{…}}` ≤ 60 characters, instructions one or two sentences)?
- Does the exercise's picture, audio or video exist and match the task?

An exercise that fails is rewritten, moved or dropped — not left with a note.

- Does every Arabic or Hebrew word show real vowel signs? A generator that writes marks as ASCII letters (`a`, `i`, `u`, `o`, `~`) must convert every
  string, including the options of a `choice`; `lint.py` reports Latin letters stuck to such words. Look at the options of listening exercises first.
- After editing a picture's source or a list of pictures, do the alt texts still describe the picture they sit on? (Originals numbered by position and
  numbered in the book differently once swapped two photos.)

- Read the theory as a learner who has only this course: is every term defined before it is used? Is any explanation or example still tied to the
  source's language or context where the course's own would do (see "Carry over by meaning" in `transfer.md`)?

## 2. Read the structure

- Do the unit, topic and exercise titles say what is inside? Are topics whole rounds of a reasonable size?
- Do theory blocks, topics and exercises sit where the learner expects (no theory in a topic, no quiz inside theory)?
- Does anything refer to the source, a page, the app's own behaviour or another unit by number? Run `lint.py`.
- **True/false is not variety.** It is the same task as choosing one of two options, so count it with `choice` and do not add it to a topic for filler: at most one per topic, and only where a statement really is the best way to check the point. Replace extra ones with a different form (typing table, sort, picture, order). **Check item order too:** the items of an exercise (especially a listening one, where each item has its own sound) must not come in the same order as the answers: `1, 2, 3 … 12` for the numbers 1–12 shows the answer by the item's number, so nothing is tested. Shuffle them.
- **Does every picture show what its place is about?** Look at each picture with the heading and the words beside it: a family section needs a family, a sentence about a sleeping grandfather needs a sleeping grandfather. A pretty scene that only matches the course's setting (a yurt in the family unit) is not an illustration, it is decoration that reads as a mistake. Decide what the section says first, then write the prompt for exactly that; people are fine when the subject is people. The automatic keyword check (`KEYS` in `sanity.py`) only proves a word is nearby: its keys must name what the picture depicts, never what happens to be written around it.
- Is every source piece accounted for in the marked-up PDF, and every new exercise flagged `generated`?
- **Do the exercises vary?** Count the forms (type and notable variation: true/false, text with a frame, with a picture, with sound, typing) per unit and per
  topic. By default no single form should take more than ~40% of a topic's exercises or ~30% of the whole course, and a topic of four or more exercises should
  use at least three forms. A course of choice, match and true/false only reads as monotone, however correct it is. When the balance is off, **replace** existing
  exercises with another form that covers the same material of the topic (a typing table instead of one more match, sorting into groups instead of one more
  choice); do not only add, or the topic ends up with the same exercise three times. Use every layer that fits: a frame (`email`, `article`, `newspaper`) for a
  text, a picture next to or inside the exercise, sound, a video before the exercise, a short `explanation` after the answer. Do not force a form that has no
  honest material (no invented sentences in a language you cannot verify); say so and ask. The count: `build-notes/buryat/course2/exercise_stats.py` is an example. **Per unit:** `sanity-report.md` has a reference table of the kinds of exercises per unit and the kinds missing (choice, typing, find the error from unit 10, match, with pictures, groups, order; sound only where there are recordings); fill a gap only when it adds a new kind of work, never to tick a box.

## 2b. Meaning and place: does it belong in this unit?

`sanity.py` proves form (words introduced before use, picture keywords, variety). It cannot tell by itself that a section about animals sits in a unit about the family. That is a decision, so it is made and written down **before** anything is added to a unit, and then checked mechanically where it can be.

Before adding a section, topic or exercise to a unit (a pass that runs after the unit is built, a new recording, a new picture):
- **Name the subject** of the new material in one phrase, and name the unit's subject: its title, subtitle, its sections and topics. They must be the same subject. If the unit already has material on this subject (a topic or section with the word in its title), the new material goes **next to it**; if not, it goes to the unit where that subject is taught, or a new topic is proposed to the user first.
- **A shared form is not a shared subject.** "The same verb ending as in another unit" or "it also has a recording" does not justify the place. Choose by what the learner is studying in that unit: family words, places, time, colours, animals.
- **Grammar the learner has not met** may appear only as ready-made words and phrases, never explained by a rule. Say so in the section.
- **Write the reason** in `revision-log.md` next to the change ("placed in unit N because …"), and in the generator declare the place with `c2_common.declare_place(unit, kind, title, anchors, why)`; `anchors` are word stems that must already occur in the unit's own title, subtitle or in the titles of its other sections and topics.
- `sanity.py` (E16) reads `placement.json` (written by `make_course2.py`) and stops with an error when an anchor is missing from the unit, or the declared section or topic does not exist. It catches the obvious mistakes (animals in the family unit); it does not replace the question above, and a registry entry that is never written is not checked: **every section or topic added after the build must be declared.**

## 3. Fresh eyes

Run the skill **`review-course`**: one new reviewer per unit, each knowing nothing of how the course was made, then a second round on the fixed course.
Triage what they report yourself (see the skill); a reviewer may misread the language or ask for what the user decided against.

## 4. What this check cannot do

It cannot verify a language you or the subagent do not read. Say so, and write the review file (see `transfer.md`).

- **Is any exercise trivial?** An exercise is trivial when the answer can be read off the exercise itself without knowing the material: the answer is written in the item text, one part of a pair is contained in the other (a word and the same word with a suffix, a letter that is visible in the word it sorts), the options give it away. Look for it, and rewrite or replace such an exercise with a typed one (the learner has to produce the form, not spot it).
- **Is there typing?** From unit 2 on (unit 1 is a light introduction; the letters of the alphabet are exempt) **every topic has at least one exercise where the learner types** (gaps, table, pictures, expand, fix) and, where the material allows, two. Recognising and producing are different skills: a learner who can choose the right word often cannot write it. Do not repeat the same material in the two typed exercises. Prefer turning a choice into typing over adding one more exercise.

## 3. Automatic variety and length warnings (`sanity_variety.py`)

Besides the errors, `sanity.py` writes warnings (never errors) that keep a course varied and full: the unit's theory has at least 4 kinds of elements
(table, scheme, picture, card, remark, columns, marks, examples, video, sound, dialogue); no section made only of text and tables, no three tables in a row,
tables under 40% of the blocks; every unit has a remark (`tip`), and a unit about letters, sounds or forms has marks `==…==`; every unit has at least
3 sections, 4 topics, 12 exercises; a language unit has sound or video; long theory has a picture or scheme. Exercises: no wall of more than 10 items
(14 is a wall), no set of distractors reused so that the answer is remembered by place, no option longer than 40 characters (it breaks into letters in the
app), and every exercise of a topic touches at least 60% of the topic's facts (the rule from `docs/learning-model.md`). Fix a warning or knowingly leave it; the report
(`sanity-report.md`) also lists the kinds of elements per unit.

Meaning alarms (warnings): theory wording without a criterion is checked by the author with the "meaning gate" in `SKILL.md` (section 2a); `sanity.py` additionally warns about
learned words in units 1–6 (`артикуляция`, `орфография`…), bold theory words that no exercise of the unit uses, and matrix rows "partly" or "skipped" without a reason.

Dialogue and hero warnings (`sanity_variety.py`): a `dialogue` inside a `grid`, a bubble dialogue without portraits of two speakers, a line without a translation, a hero (Сэсэг, Баяр, Дарима)
with a portrait other than the course-wide one, an `examples` item without a translation (units 6+). The rules are in `SKILL.md`, sections 2b and 2c.


## Checks added during the Buryat course revision (each is a warning or an error in `sanity.py`; answer it, do not silence it)

| Code | What it catches | Answer |
|---|---|---|
| `ВИЗУАЛ` | a theory section with four or more examples and no picture; a unit with no model picture | a picture, or a reason in `visual_reasons.json` |
| `КАРТИНКА-УПР` | a model picture in a unit's theory and in none of its exercises | an exercise on the picture, or a reason in `picture_exercise_reasons.json` |
| `ИМЯ` | a person's name that is not in the cast; the cast larger than its maximum | use a cast character, or add one in the cast script |
| `ПЕРСОНАЖ` | a cast character never used in the course | use the character or drop it from the cast |
| `КТО` | a picture with people with no «who» in the picture index | fill `image_who.json` or `who` in `gen.py` |
| `ГЕОГРАФИЯ` | a hero travels where it is not possible (a train or plane from a place without rail or airport; a station scene for someone arriving from there) | fix the route (check the real map); add places to `geography.json` (the Buryat course: `course2/geography.json`, module `sanity_geo.py`) |
| `ТЕРМИН` | university linguistics in learner text: parts of speech, derived and non-derived stems, "agglutinative", native names of cases and parts of speech | remove it or explain by pattern and example; keep only the terms needed to use a rule (case by Russian name and question, ending); an exception needs a reason in `terms_reasons.json`. The list is `terms_banned.json` |
| `ПЛАШКА` | a lone `note` tip without a title; a `note` that holds a rule; a unit where most lone tips are `note` | use `rule` ("Запомните"), `fact`, `culture`, `warning`, plain text or a table; a reason in `tip_reasons.json` |
| `ТАБЛИЦА` | a table of three or more columns where the longest cells of the columns add up to more than 120 characters | shorten the examples (a list of examples is "for example", keep two or three) or split the table; the right column is cut off in the app |
| `ЧАСТОТА` | a word of the course that does not occur in the corpus at all (a rare form from a book) | drop it, replace it with a frequent one, or write the reason in `freq_reasons.json`; rebuild the list with `corpus/word_freq.py` |
| table in a grid | a table with three or more columns inside a grid (clipped on the right) | one wide table in a normal block |
| aside size | an exercise picture not `top`/`large` | set it |
| self-contained | an exercise that asks «по диалогу / рассказу / письму / голосам» or says «из диалога» and does not carry the text (no `aside` or `look`): the learner would have to look it up in the theory | put the text into the exercise: a picture of the text on top (`aside`, top/large; the Buryat course draws it with `c2_readtext.py`), or a connected cloze in a `look` frame; or drop the reference when each item holds its own sentence |
| `ССЫЛКА` | the explanation of a topic (unit 10 or later) has no `seeAlso` link to a section of the unit's theory | add the link to the section that explains the topic (not a video section); skip only if there is no such section |
| `pictures` alternatives | a `pictures` item with `{{right|wrong}}`: the exercise is typed, and every listed variant counts as right | one answer per item; the distractors go to the exercise's `wordBank` (`c2_picfix.py` does it for the Buryat course) |


Disputed words and forms are not decided by `sanity.py`: follow `checking-forms.md` (books, dictionary `burdict.db`, the corpora (`corpus.py`: monolingual for forms, parallel for meanings), websites, native speaker) and record the result in `corpus-findings.md` and the revision log.
