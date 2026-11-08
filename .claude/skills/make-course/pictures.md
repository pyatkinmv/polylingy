# Pictures of a course

Pictures are **drawn, not copied**: the source's artwork never goes into the course. Every picture of the source is
accounted for; a picture that the learner does not need is skipped *explicitly*, with a reason, never forgotten.

## For each picture of the source

1. Cut the original out of the source page and keep it in `build-notes/pictures/` (git-ignored) for comparison only.
2. Write a text description and a drawing prompt that say what the learner must see and what to leave out.
3. Draw the new picture (code, or any generator the user agrees to) from that description. The course gets only this
   one, under `images/…` in the zip. The description becomes the `alt` text (required).
4. If a picture shows several things the task needs (numbered family trees, speech bubbles of people), draw it as one
   composite so the numbers or labels in the exercise match it.

## The folder `build-notes/pictures/`

One triple per picture — **original crop**, **our picture**, **description / prompt** — plus `index.html`, which shows
all triples in one table with their numbers and where each is used (unit, topic, exercise), and a skipped list with
the reason for each. Regenerate the folder whenever a picture or a description changes; the index must always match
the zip. Keep the extraction and drawing scripts together (ask the user where; the session scratchpad is not enough
if the work continues later).

## Rules

- Draw a picture only where it serves the learner: a task that needs it, or a scene that explains the theory. Not a
  picture for every decorative photo.
- No text of the language in a picture unless the task needs it; text in a picture is not annotated.
- Names, faces and brands of the source's characters are not reproduced.
- After drawing, look at every picture yourself, at the size the app shows it, and at the exercise using it.
