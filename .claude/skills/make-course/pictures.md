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

## Visualise where it helps

A course is meant to be pleasant to look at and easy to take in, so theory is not a wall of text and tables. When a unit is written or revised, look through its theory
once and add a visual where it makes something clearer. Do not overdo it, and do not aim at a number: go through the unit, list the places where a picture or a scheme would really help, and draw exactly those; some units need none, some need several, and nothing is drawn just to decorate. Never fall into a habit (the same count in every unit is a sign that you are filling a quota).

**The picture pass is a step, not a mood.** For every unit, before it is built and again before it is handed over, write a short list in the unit's build notes: each theory section, the picture that would help it (scheme / set of objects / scene / map) or the reason why none helps. A unit is not handed over without this list, and "none" must be a reason ("a rule one sentence says"), not an omission. Check these by default, because a learner meets them as things, not as rules:
- words for things the learner can see: transport, tools, materials, animals, clothes, food, buildings, places (a set of objects with the forms beside them: draw the objects with the image model, put the labels with code, see `diagram_kit.paste`);
- a form that shows a relation (who gives, where from, with what): a scheme;
- a new theme of vocabulary: at least one picture or scheme of it.
Before the unit is finished, ask: "what would a learner look at on this page?" If the answer is only text and tables, the pass was not done.

**Reuse before drawing.** Before ordering any picture, look at what the course already has (`build-notes/buryat/pictures/out/`, the `IMG` table in `c2_common.py`, the sheets `sheet_*.png`) and take from it: an object drawn for one unit serves every later unit (the horse of unit 4 is the horse of unit 16). Draw only what is missing, and write down which pictures were reused and which were drawn in the unit's picture list. Generated objects are also parts of schemes (`diagram_kit.paste`), so one drawing can serve a scheme, a card and an exercise.

**Pictures in exercises.** Every picture of a unit's theory that names a thing or a scene is used once more in an exercise of the same unit (the `pictures` type: choose the phrase or form for the picture, or a choice with `aside`), unless that would give the answer away; a picture that has no exercise needs a reason in the unit's picture list. A picture exercise is never the only place of a picture: the theory comes first.

**A picture beside an exercise must be readable.** An exercise `aside` that the learner has to read (a board, a scheme, a diagram with words) is set `side: top`, `size: large`; the default (right, medium) makes it a thumbnail where nothing can be seen. `sanity.py` fails on an `aside` that is not top or bottom and medium or large. If the picture shows the answer (words under tags, captions that are the key), do not put it beside the exercise at all.

**Where a picture earns its place**
- a rule with a choice or a sequence (which ending after which sound, how a form is built, a yes/no answer): a diagram or a decision scheme;
- a form made of parts (word + ending = form), numbers by place, a family or any structure with relations: a scheme;
- a cycle, a scale or a map (hours of the day, the alphabet by kinds of letters, a country): a circle, a strip, a map;
- a scene or an object the word names, or the situation of a phrase: a picture beside the text;
- a table that is hard to read: a scheme that shows the same thing (the table can stay, or be shortened, when the scheme says it all).

**Where it does not**: a list of words that a table already shows well, a rule that one sentence says, anything that would need a fact the text does not have.

**Three ways to place it**: add it; add it and rewrite a little of the nearby text so they do not repeat each other; add it and cut the text or the table that it replaces.
Look at the unit as a reader first and keep the page short.

**Which tool**
- *Code-drawn scheme* (text, numbers, forms, clocks, circles, maps): exact, free, repeatable; the default for anything that must be correct. Follow the project's
  diagram style and its accuracy rules (see below).
- *Image model* (objects, animals, scenes, people): lively, but it costs money and cannot be exact. The number follows the need of the unit, from none to 10: at most 10 per unit is a hard limit (more only after asking the user); below it there is no target and no usual count, decide by the places in the theory where a picture helps; never draw a picture the course
  does not use, and put every generated picture into the **theory** (beside the text it belongs to, not in a block at the bottom); the same picture may then
  reappear in an exercise, preferably not as the key to its answer.

**Accuracy of schemes**: a scheme only shows what the course text and the source already say. Words come from the unit's data, numbers are computed, and anything doubtful
(pronunciation, boundaries the source does not give) is not drawn.

**Every picture carries notes in the course**: `description` (what exactly is drawn, with every word of a scheme) and `made` (the prompt, or the script and data) beside
`alt`; the app does not show them. Without them a person or a tool reading the JSON cannot know what the picture contains. This holds for both kinds: for a picture from an image model `made` is the prompt (and the model); for a picture drawn by code it is the script and its data (`diagram_kit.save` demands both and writes them to `image_info.json`, `pictures/describe_gemini.py` does the same for Gemini pictures: model, aspect ratio, full prompt from `gen.py`).

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
- Keep the setting in one file for the whole course (for the Buryat course `build-notes/buryat/pictures/SETTING.md`) and put it into every prompt with people, homes, clothes or food: a unit-by-unit improvisation produces the generator's default (a European family and a log cabin in a Buryat course). Show new people pictures to the user as a contact sheet for information; the user has waived approval, so they may go into the course at once (add their names to the list of approved names that the sanity check reads). Draw neutral, recognisable everyday people with at most one small cultural touch, not a full festive costume, unless costume is the topic.
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

## Diagrams drawn by code (style and accuracy)

Schemes, clocks, counts and other pictures that must be exact are drawn by code, not by an image model. Their style, accuracy rules (every fact comes from the course text and its source, words come from data, numbers are computed) and shared helpers are fixed in `build-notes/buryat/pictures/DIAGRAM_STYLE.md` and `build-notes/buryat/course2/diagram_kit.py`; for a new course keep the same structure. Code-drawn pictures lie on a **transparent** background and everything free-standing (labels, lines, arrows) is neutral grey, so they follow whatever palette (light now, dark later) the course is shown in; coloured parts sit in chips and cards with their own fill, and their meaning colours stay fixed. Never bake a cream or white page behind a scheme.
