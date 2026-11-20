---
name: review-english
description: Check a Polylingy English course (grammar and usage drills made from cards or by a model) for wrong language, not for format: wrong or doubled keys, misspelled forms, false claims in explanations, a model's drafts left in the text, filler blocks. Scripts first, then isolated reviewers who are English experts. Use before releasing or updating English Usage Exercises, and again after fixing.
---

# Review of an English course

`checkCourse` proves that the file is valid. This proves that **the English is right and that every exercise has one answer**. It is a different job from
`review-course` (made for the Buryat course: pictures, scripts, readings, books); do not use that one here.

What this course is: cards of near-synonyms and grammar contrasts, 10–30 sentences each, a blank per sentence (`choice` with the key first in `{{a|b|c}}`,
`gaps` with `{{answer}}[[hint]]`), an explanation per topic and per sentence. It was made by a model from a deck, so its defects are the model's: it is
fluent and sure of itself when it is wrong.

## What a reviewer checks (decided with the author; do not widen it)

1. **One right answer.** Does another option of the list also fit the sentence? This is the commonest defect, above all in pairs rated "close synonyms".
   *Decision: replace such an exercise with a sentence where the difference shows (a fixed collocation, a clear context); do not accept both.* A topic whose pair
   differs only by habit keeps the exercises where the habit is firm (`inherent risk`, `intrinsic motivation`) and loses the rest.
2. **Facts in the explanations** (topic and exercise): untrue, overstated (`always`, `never`, `the only`, `cannot`) or invented claims (`psychologists always
   say…`, a "C2 idiom" tag, a claimed origin). Say if a rule has exceptions the text denies.
3. **Form of the word and the text**: the glued ending (`{{confine}}ed` gives *confineed*), the form the sentence needs (`The director {{say}} that`), a
   misspelled option, stray quotes, a broken sentence, a verb that does not agree.
4. **Natural and at the level of the topic**: would a native say it; is the sentence too rare, artificial or odd to teach with; is the vocabulary far above the
   rest of the topic.
5. **Leftovers**: the generator's own draft in an explanation (`Let's rephrase…`, `Trick question`), traces of the deck (`rated 3/4`, `this exercise`, `card`).

Not a reviewer's business: redesigning the course, merging or moving topics, the order of topics, pictures, style. If they say so anyway, it is a proposal, not a defect.

## Filler blocks

Almost every topic has the same blocks under the same titles: `Key Difference` (rule), `Simple Analogy` (fact), `Quick Test` (warning), `Key Collocations`
(culture), `Zone of Interchangeability` (note). Keep only what earns its place:
- **Key Difference**: keep when it says something the text above it does not; drop when it only repeats it.
- **Key Collocations**: keep when it really lists habitual word pairs that the exercises do not already show; otherwise drop. Its kind must not be `culture`.
- **Simple Analogy**: it is a pair of examples, not an analogy; keep it as `examples` titled "Examples" when they are good, otherwise drop.
- **Quick Test**: keep when the questions are a real decision rule; drop a restatement.
- **Zone of Interchangeability**: keep (it is honest) but check every claim in it.
The reviewer marks each block per topic: `keep`, `retitle`, `drop`, with a reason of one line.

## Steps

0. **Mechanical** (cheap, do it first and fix what is plain):
   `python -X utf8 .claude/skills/review-english/check_english.py <course.json> <report.md>` — glued endings that misspell, stray quotes, drafts and deck traces,
   explanations that admit a second answer, absolutes, a doubled sentence. All of it are candidates; read the lines before changing anything.
1. **Shards.** `python -X utf8 .claude/skills/review-english/dump_english.py <course.json> <folder> [--per-shard 8]`. Each shard shows each topic as questions
   first (no key), then the explanation, then the key.
2. **Reviewers.** One new `Agent` per shard (`general-purpose`, background, the strongest model), no history, with the prompt below. Start them in batches the
   user agreed to (a full run is dozens of reviewers: say the number and the cost first). Never continue an old reviewer; never tell it what was fixed.
3. **Triage** each finding yourself, with your own knowledge of English, before changing anything:
   - *fix*: wrong key, a second answer, bad form, false fact, leftover;
   - *reject*: the reviewer is wrong (say why: a dictionary entry, a usual collocation);
   - *ask the user*: taste, or both of you are unsure.
   There is no corpus and no web search for this course. A doubtful word is settled by your own expertise, stated plainly: "certain" or "unsure". What stays
   unsure goes to the user; do not decide it by a coin.
   **Write the triage down** in `build-notes/anki/review-decisions.md`: what was fixed, rejected and why, what is open.
4. **Fix** by exercise `id` (they are stable: `stamp_ids.py`), never by position; a fix that replaces an exercise keeps the id only when the new one tests the
   same thing. Rebuild, run `./gradlew :client:checkCourse -Pcourse=<file>` and the mechanical script again; bump the course `version`.
5. **Second round** with new reviewers on the fixed shards only. Stop when what is left is taste or "unsure".
6. **Report** to the user: counts by kind, what was fixed, what is rejected and why, what is left to them.

## The prompt (one per shard)

> You are a native speaker of English and an experienced teacher of English as a foreign language (upper-intermediate to advanced learners). You have never seen
> how this course was made and know nothing of its authors or of any earlier review; judge only what is on the page. You may not use the web; rely on your
> own knowledge, and say "unsure" where you are not sure instead of guessing.
>
> Read <path>\shardNN.txt: topics of a drill course, each with its exercises. In a `choice` exercise the options are listed alphabetically and ONE is the right
> answer; in a `gaps` exercise the learner types the word (a hint in brackets), and every answer written after KEY is accepted. A glued ending after a blank is part
> of the printed sentence (`[____]ed`).
>
> For each topic: 1. Solve every exercise from the QUESTIONS part alone, BEFORE you read the key; note every exercise where a second option also fits.
> 2. Read the explanation and the key and check: (a) one right answer; (b) every claim in the topic explanation and in each exercise explanation (untrue,
> overstated, invented); (c) the form of the word and the text (the word the learner types plus the glued ending must be spelled right; the form must suit the
> sentence); (d) is the sentence natural and at the topic's level; (e) leftovers of a model's draft or of the source deck in the text.
> 3. For the repeated blocks (Key Difference, Simple Analogy, Quick Test, Key Collocations, Zone of Interchangeability) say `keep`, `retitle` or `drop` for each,
> with one line why; drop a block that only repeats the text or lists things the exercises already show.
>
> Report, per topic, only real problems: exercise number, what is wrong, a concrete fix (a replacement sentence when the exercise has to go: one blank, one
> answer, a context that excludes the other options). Mark each "certain" or "unsure". No redesign, no proposals about order or merging. Under 700 words per
> topic with problems; one line for a clean topic. Do not modify any file.

## What the reviewers do well, and what they get wrong

- Good: a second fitting option, forms and spelling, an explanation that contradicts its own key, the model's drafts, claims that sound sure but are false.
- Unreliable: naturalness of rare collocations and regional differences (British/American); they call a sentence "unnatural" that is only formal. Check these yourself.
- Their repeats are the best signal: a finding that two reviewers make independently is almost always real.

## Decisions of the author (2026-11-20; they replace the "Filler blocks" rules above where they differ)

- A block stays when its content is what its title says. When it is not, first write content that fits the title; if the topic cannot have it, rename the block to what it
  is, or drop it. Common sense, case by case; the reviewer proposes, the triage decides.
- After `if`, `was` is accepted next to `were` (except the fixed `If I were you`); the topic explanation says why.
- Every exercise with a defect is replaced by a new one (not deleted), and the new ones go through a second round of new reviewers.
- Improvements by general patterns are welcome when they help the learner in many topics; they are added here once chosen.
