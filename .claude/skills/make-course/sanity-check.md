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
  honest material (no invented sentences in a language you cannot verify); say so and ask. The count: `build-notes/buryat/course2/exercise_stats.py` is an example.

## 3. Fresh eyes

Run the skill **`review-course`**: one new reviewer per unit, each knowing nothing of how the course was made, then a second round on the fixed course.
Triage what they report yourself (see the skill); a reviewer may misread the language or ask for what the user decided against.

## 4. What this check cannot do

It cannot verify a language you or the subagent do not read. Say so, and write the review file (see `transfer.md`).

- **Is any exercise trivial?** An exercise is trivial when the answer can be read off the exercise itself without knowing the material: the answer is written in the item text, one part of a pair is contained in the other (a word and the same word with a suffix, a letter that is visible in the word it sorts), the options give it away. Look for it, and rewrite or replace such an exercise with a typed one (the learner has to produce the form, not spot it).
- **Is there typing?** From unit 2 on (unit 1 is a light introduction; the letters of the alphabet are exempt) **every topic has at least one exercise where the learner types** (gaps, table, pictures, expand, fix) and, where the material allows, two. Recognising and producing are different skills: a learner who can choose the right word often cannot write it. Do not repeat the same material in the two typed exercises. Prefer turning a choice into typing over adding one more exercise.
