# Revising a course in batches of three units

The path for reviewing an existing course unit by unit, so that it is the same every time and survives a lost context. It ties together the other documents; it does
not replace them. The state of the work (which units are done, what was decided, what is postponed) lives in `build-notes/<course>/revision-log.md`: **read it first,
update it at the end of every batch.** The older `review-decisions.md` is linked from it and is no longer extended; the log holds the single list "For the native speaker" and the open questions.

## Principles (the user's, do not renegotiate)

- **Explain means explain.** A question "why / what is this / can we" gets an explanation and options; nothing is changed until "делай".
- **Change the generator, not the file:** `course2/c2_pass4.py` (or `c2_uN.py`); rebuild with `make_course2.py`. Never edit `course.json` or the zip.
- **One before/after picture per fix, side by side:** «было» on the left, «стало» on the right. Number of fixes = number of pictures. Scripts: `shots/shots*.py`
  (`shots7.py` is the model: «было» from the old zip, «стало» from the new one).
- **Three units at a time, every layer** (below). Do not finish one layer and forget the next: in units 4–6 the visual elements were first missed.
- **Nothing invented, nothing about the source book in the course text** (no "в книге", "учебник", "не перенесено"); see `transfer.md`, `sanity-check.md`.
- **Import into the app only when asked.** Replacing a course loses its journal: check the number of journal events of that course first (the courses are in
  `<Documents>/polylingy/polylingy.db`, `course_document`; events carry `event.courseId`). The app must be closed.
- **Commit only when asked.** Course files in `build-notes/` are git-ignored; skills and docs are committed.

## The layers to pass for every unit

| Layer | What to look at | Where the rule is |
|---|---|---|
| Meaning | each paragraph, rule, table and example: one sentence "what can the learner do after this"; contradictions; no history for beginners | `SKILL.md` §2a, skill `review-course` ("purpose pass") |
| Theory elements | grid, tip kinds (`fact`, `culture`, `rule`), situation frames, `==marks==` (tones 1–5), table `appearance`, dialogues, pictures/schemes where they help, videos in theory only | `SKILL.md` §3, `theory-blocks-format-9` in memory, `pictures.md` |
| Exercises | every exercise covers the whole topic (the picker is random); variety of types; wall size; distractors; no answer on view; a picture beside an exercise is `top` and `large` (see `pictures.md`); no ambiguous keys; an introduction in each topic | `SKILL.md` §2, §"Size of a unit", `sanity-check.md` |
| Pictures and schemes | run `sanity.py` and answer every «ВИЗУАЛ» (a picture or a reason) and «КАРТИНКА-УПР» (an exercise on each model picture or a reason) warning; the visual test in `pictures.md`; the picture must be readable without explanation: every word in it has a Russian translation beside it; a symbol (a face for «character») is not enough to teach a word; a picture does not repeat what the table or another picture beside it already shows; a scene shows the person who acts, not only the object (a sentence «the girl took her book» needs the girl); no square-resizing of wide scenes in `process.py` | `pictures.md`, learner feedback on unit 17–19 |
| Characters | the people of the unit are from the cast file (`ИМЯ`, `ПЕРСОНАЖ`, `КТО` in `sanity.py`); no one-off names; a new person only by changing the cast (`characters.md`); hero pictures made from the portrait, a sheet for ordinary scenes, a single picture for a scene tied to a sentence | `characters.md`, `pictures.md` |
| Disputed forms | before triage of a reviewer's doubt about a word or form, check it by `checking-forms.md` (the corpora: monolingual for forms and frequencies, parallel Buryat–Russian for meanings); change the course only when the evidence is clear; the rest goes to the native-speaker list | `checking-forms.md` |
| Words | learned words only (units introduced earlier), every theory word used in an exercise of its topic, nothing used before its topic | `sanity.py` warnings "введено в юните…" |
| Dialogues and heroes | full width, not in `grid`, two portraits (Сэсэг, Баяр, Дарима in unit 17), a translation on every line, no orphan examples | `SKILL.md` §2b |
| Look at the screenshot's edges | before sending a before/after picture, look at its right edge and at every grid: a table with three or more columns in a two-column grid is cut off on the right (`sanity.py` fails on it); read the cells, do not only see that the picture is there |
| Checks | `checkCourse`, `lint.py`, `sanity.py` (+ `sanity_variety.py`), `image_info.json` (description, made), `pictures_approved.json` | `sanity-check.md` |
| Independent review | new subagents with no history, one per unit, second round after the fixes | skill `review-course` |

## Steps of a batch

1. **Read** `revision-log.md` and this file. Name the three units.
2. **Measure.** Build the current zip (`python -X utf8 course2/make_course2.py`), run `sanity.py` and take a text dump (`.claude/skills/review-course/dump_course.py`);
   look at the units as the learner sees them (`student_view.py`, screenshots through `ShotsUiTest`).
3. **Ideas with priorities.** For every layer a short list of what is wrong or could be better: *important*, *desirable*, *taste*. Put them in
   `build-notes/<course>/revision-<units>.md` (model: `variety-review-u1-3.md`). Questions where the user decides go in the same file, with options and a recommendation;
   for the user explain by **questions and examples**, with screenshots of the places, not abstractions.
4. **The user chooses.** Do not start before the answer. Say what was not verified (the language, pictures, audio).
5. **Fix in the generator.** Items on the user's list only; the "before" shots are taken from the current zip *before* the change.
6. **Rebuild and check:** `make_course2.py`, `./gradlew :client:checkCourse -Pcourse=<zip>`, `python -X utf8 .claude/skills/make-course/lint.py <zip>`, `sanity.py`.
   Zero errors; warnings in the touched units are explained or fixed.
7. **Before/after pictures**, side by side, one per fix; look at them yourself before sending (an empty crop, a cut table).
8. **Independent reviewers**, one per changed unit (not the untouched ones). Triage every finding: fix, reject (with the source), or ask the user. Check source claims in the
   sources, not in a reviewer's memory.
9. **Update `revision-log.md`** and open questions; save a memory note only for new user preferences.
10. **Import** into the app only on request; report what was replaced and how many journal events it had.

## Closing a batch

Say plainly: what was changed (count = number of pictures), what the checks showed, what the reviewers could not check, what is postponed and why, which units are next.
