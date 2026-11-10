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
- A model draws pictures but never text in a script it does not know: it invents letters. Leave bubbles, labels and captions empty in the prompt and draw
  the text yourself over or around the picture.
- Say how many people there are and who they are ("exactly one person, no other people"; "exactly three men"). A prompt that stresses a setting or a look
  tends to fill the picture with a crowd.
- Keep the raw output of the model next to the result, and never delete it before the replacement exists: regenerating costs money and time.
- A key for the model is a secret: read it from an ignored file (`.env`, listed in `.gitignore`), never print it, never write it into a script or the course.
- Numbered originals: one list of the pictures may number them by position on the page and the book by its own numbers. When two such lists meet (alt
  texts, keys, catalog), match by the content of the picture and look at the pairs.
- After drawing, look at every picture yourself, at the size the app shows it, and at the exercise using it.

## Size and place in the course

- Every picture block has a size word, not pixels: `small` (thumbnail), `medium` (the default), `large` (diagrams and composites that must stay readable).
  A picture on its own line never needs to fill the window.
- A picture that belongs to a line of text — a phrase, a caption, a word and its picture — goes into a `situation`: the picture beside the text, as on the
  page of the source. A small picture there is a square thumbnail before the text (a phrase card); `look: "plain"` drops the card when the picture simply
  stands beside a paragraph.
- Pictures that are the sides of a `match` become picture cards automatically; give such pictures a subject that survives a centred crop.

## Cultural context

A course has a cultural setting (the country or region of its language, its everyday life, clothes, food, places). Everything new that is made for
it — pictures, voices, video, example names and situations, even the choice of photos — follows that setting, so the learner meets the language in
the world where it is spoken. Not the default look of the generator (for example, Scandinavian-looking children in a course about Arabic).
- Keep the setting in one file for the whole course (for the Buryat course `build-notes/buryat/pictures/SETTING.md`) and put it into every prompt with people, homes, clothes or food: a unit-by-unit improvisation produces the generator's default (a European family and a log cabin in a Buryat course). Show people pictures to the user as one contact sheet and use them only after approval (a list of approved names that the sanity check reads). Draw neutral, recognisable everyday people with at most one small cultural touch, not a full festive costume, unless costume is the topic.
- Before generating, write down the setting in a line or two (who the people look like, what they wear, what the places look like) and put it into every
  prompt; do the same for regeneration and for any later addition.
- Stylize lightly: natural, friendly, never a caricature or a pile of clichés. An abstract cartoon, an object or an icon needs no marks of culture; people,
  photos, food, buildings and clothes do.
- Vary people and clothes inside the setting. Do not dress every man the same (all in suits) or every woman the same (all in headscarves): the generator
  falls into such patterns once it is told the setting, so say in the prompt what varies (casual and formal clothes, some women with a headscarf and most
  without) and look at the whole set of pictures together, not one by one.
- Check against the original, not only against the setting: a picture must still show what the original shows (the same job, the same number and
  gender of people, the same scene). The setting changes how it looks, not what it says.
- If the setting is not clear from the source, ask the user once instead of guessing.

## Diagrams drawn by code

Schemes, clocks, counts and other pictures that must be exact are drawn by code, not by an image model. Their style, accuracy rules (every fact comes from the course text and its source, words come from data, numbers are computed) and shared helpers are fixed in `build-notes/buryat/pictures/DIAGRAM_STYLE.md` and `build-notes/buryat/course2/diagram_kit.py`; for a new course keep the same structure.
