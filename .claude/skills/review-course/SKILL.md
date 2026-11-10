---
name: review-course
description: Check a finished or changed Polylingy course with fresh, isolated reviewers (subagents that never saw how the course was made): they do every exercise as a student, then check the language as experts. Use after generating or editing a course, before handing it over, and again after fixing what the first round found.
---

# Isolated review of a course

`checkCourse` and `lint.py` prove that a course file is valid. This proves it is **understandable, solvable and correct in the language** — judged by
readers who know nothing of the author's reasoning, which is exactly what the author cannot do alone.

## Why isolated

An author (or the agent that wrote the course) "sees" what is meant: the key looks natural, the hint looks helpful, the picture looks like the page of the
book. A reviewer that has only the page cannot. So:
- every reviewer is a **new** subagent with **no history**: do not continue an old reviewer with a message, and do not tell it what was fixed or why;
- it gets only a text dump of the unit (and of the earlier units, to know what the learner has been taught), not the generator, the source or the chat;
- one reviewer per unit, started together in the background (the strongest model: they must judge the language), then read their reports as they arrive.

## Steps

1. **Dump.** `python .claude/skills/review-course/dump_course.py <course.zip|json> <folder>` writes `unit1_dump.txt`, `unit2_dump.txt`… The dump shows theory,
   topics and exercises, the key marked (the first option of `{{a|b|c}}` is right, `ORDER` is the right order, `PAIR` the right match), readings as `‹…›`,
   pictures as their `alt`. Reviewers cannot see pictures or hear audio: say so.
2. **Launch** one `Agent` per unit (`general-purpose`, background) with the prompt below. Fill in the language, the audience, the file paths and what to
   judge about the order of topics.
3. **Triage** each finding yourself before changing anything — a reviewer may misread the language, or ask for what the user already decided against:
   - *fix* — objectively wrong (a key with two right answers, a form that does not exist, text the learner cannot read, a hint that gives the answer);
   - *reject* — wrong, or contrary to a rule the user set (say why in the report);
   - *ask the user* — taste and structure (merging exercises, moving topics, removing readings).
   Check anything that touches the source against the source (the book's answer key settles a key; a reviewer's memory does not).
4. **Fix**, rebuild, run `checkCourse` and `lint.py`.
5. **Second round** with new reviewers on the fixed course. Two rounds are usually enough: stop when what is left is taste or "unsure".
6. **Report to the user**: what was found, what was fixed, what was rejected and why, what is left for them to decide, and what the reviewers could not check
   (pictures, audio, video).

## The prompt (one per unit)

> You are a native speaker of <language> and an experienced teacher of it to <audience> beginners. You have never seen how the course below was made and you
> know nothing about its authors or about any earlier review; judge only what is on the page.
>
> Read <path>\unitN_dump.txt — a text dump of UNIT N of a <language> course for <audience> who start from zero (theory first, then topics with exercises).
> <Earlier units are in unit1_dump.txt … in the same folder: read them only to know what the learner has already been taught.> Legend: in `choice` items the text
> `{{a|b|c}}` means the FIRST option is the correct one (the app shuffles them); in `ORDER` the parts are in the correct order; `PAIR` lines are the correct
> matches; `‹…›` is a Latin reading shown above the word; [PICTURE: …] is a description (you cannot see pictures); [AUDIO TRACK] / audio flags mean a recording you
> cannot hear; "added by the course author, not in the source" marks exercises the author invented.
>
> Work in two passes. 1. As a student: go through the theory, then do every exercise using only what the unit and the earlier units taught, BEFORE looking at the
> key. Note anything you could not solve, anything ambiguous, anything where a second option could also be right. 2. As an expert: check every word, sign, form,
> gender, plural, spelling, reading, translation, explanation and key. Say explicitly when you are unsure of a fact rather than guessing.
>
> Also judge (a) the order of topics, including small vocabulary topics and where they stand; (b) whether the theory is clear, correct and in a sensible order;
> (c) exercises that give their answer away (for example a reading shown above the very word the exercise asks about), ask about what was never taught, or have
> unclear instructions; (d) titles that do not say what is inside; (e) anything that looks like a technical glitch (stray Latin letters inside <script> words,
> mixed-up options, empty items, a picture description that does not fit the exercise).
>
> Report a concise list ordered by severity. For each real problem: where (topic, exercise number, item), what is wrong, and a concrete fix. Separate "certain"
> from "unsure". Under 800 words. Do not modify any file.

## What the reviewers do well, and what they get wrong

- Good: keys with two right answers, forms and spellings, words from letters not taught yet, hints that give the answer, technical glitches, wrong or
  contradictory facts between theory and exercises, duplicated exercises.
- Unreliable: they redesign. "Merge these exercises", "move this topic", "remove all readings" are proposals, not defects; a rule the user has set outranks them.
- Blind: pictures (only `alt`), audio and video. Check those yourself, or ask the user. Make sure each `alt` describes the picture it belongs to.
- Their own blind spots show in repeats: a finding that two independent reviewers make is almost always real.
