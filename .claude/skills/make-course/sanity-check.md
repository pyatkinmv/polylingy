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

## 2. Read the structure

- Do the unit, topic and exercise titles say what is inside? Are topics whole rounds of a reasonable size?
- Do theory blocks, topics and exercises sit where the learner expects (no theory in a topic, no quiz inside theory)?
- Does anything refer to the source, a page, the app's own behaviour or another unit by number? Run `lint.py`.
- Is every source piece accounted for in the marked-up PDF, and every new exercise flagged `generated`?

## 3. Fresh eyes

Give the generated course (not the generator, not your reasoning) to a subagent that has not seen how it was made, with
this checklist, and ask for a list of exercises it could not solve or found ambiguous, and for structure problems.
Check each finding yourself before changing anything: a subagent may misread the language. Fix what is real, rerun
`checkCourse`, and note in the chat what you changed and what you left.

## 4. What this check cannot do

It cannot verify a language you or the subagent do not read. Say so, and write the review file (see `transfer.md`).
