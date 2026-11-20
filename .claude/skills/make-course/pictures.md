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

**What gets a generated picture (the visual test).** For every theory section go through its examples and ask of each: *can a learner point at it?* A thing, an
animal, a place, a person doing something, a state they can see (hot tea with steam, a tall boy by a low fence, wet ground after rain, a big family) is drawn with the
image model and shown beside the example (cards in a grid: the sentence with its translation above, a large picture below). Something abstract (a judgement such as
"a good person", a grammar relation, a feeling with no visible sign) is left as text or a code scheme, and a symbol (a smiley for «character») does not stand in for the
meaning. A list of four or more concrete examples with no picture is a defect, not a style: `sanity.py` warns «ВИЗУАЛ» for a theory section like this, and for a unit
with no model picture at all. Each warning is answered either by a picture or by a written reason in `build-notes/buryat/course2/visual_reasons.json` (key `unit` or `unit|section title`); that file is the unit picture list for this check; each warning is answered in the unit's picture list either by a picture or by a reason ("abstract", "one sentence says it"). The warning
is a floor, not the target: the pass is not finished because the warnings went away. Adjectives, verbs of action and qualities of things are not exceptions (the unit
17 list of adjectives had no pictures for three units of review because only nouns were on the checklist).

**Why this kept slipping through.** Pictures are judged by nobody who reads text: `checkCourse` and `lint.py` check the file, `sanity.py` checked what a picture
*was* (size, keys, approval), never whether one was *missing*, and the isolated reviewers see only a text dump and are told they are blind to pictures. So the visual
pass depends on the author alone; therefore it is a written list per unit, the sanity warning above, and a question in the reviewer prompt about sections where a
picture would help.

**Reuse before drawing.** Before ordering any picture, look at what the course already has (`build-notes/buryat/pictures/out/`, the `IMG` table in `c2_common.py`, the sheets `sheet_*.png`) and take from it: an object drawn for one unit serves every later unit (the horse of unit 4 is the horse of unit 16). Draw only what is missing, and write down which pictures were reused and which were drawn in the unit's picture list. Generated objects are also parts of schemes (`diagram_kit.paste`), so one drawing can serve a scheme, a card and an exercise.

## Heroes in pictures: reference, sheets, single pictures

Characters and their rules: `characters.md`. This is how their pictures are made.

**Reference portrait first.** Every hero has one portrait (head and shoulders, plain background) that is the reference. Any later picture of that hero is drawn with the portrait
(or a crop of the hero from an existing scene) given to the model as an **input image** (`meta["refs"]` in `pictures/gen.py`); the prompt says "the same person as in the attached
picture", then the cast line (`identity_en`). It keeps the face, hair and age close; it does not keep small details (a pendant, a scarf), so do not rely on them.

**Sheets or single pictures.** A **sheet** is one model call that draws a grid of panels (3 by 2) of the same hero in different scenes, which `pictures/cut_sheet.py` cuts into
separate pictures (`sc_*`, one file each). The learner never sees the sheet.
- Sheet: ordinary scenes of one hero where the exact objects do not matter (studies, travels, waits, drinks tea); the same person with different expressions for dialogues;
  a family or a set of same-kind objects drawn together once. One call gives six pictures and the best likeness inside the sheet. A panel is about 330 px: for small and medium
  cards, not for large pictures (a 2 by 2 sheet at 1:1 gives about 500 px).
- Single picture (with the portrait as input): the scene must match a sentence of the lesson (these objects, this action), a crowd, an exact number of things, a large picture,
  or a panel of a sheet that failed.
- After cutting, look at every panel at its size and at the exercise using it; redo a bad panel alone, with the portrait as input.

**Reuse before drawing.** A hero scene already in the picture index (`course2/pictures_index.json`) is used again; a sheet panel that no unit uses yet is the first choice for the next
card of that hero. Draw a new picture only when no existing one fits the sentence.

**Cast portraits and cards.** Every character of the cast has one head-and-shoulders portrait (`own_<id>`), drawn with the family scene or another reference and the style of the first portraits. A portrait is shown on a card where the character first appears (an introduction card with one line of the unit's own grammar and a Russian line about who this is). Cast portraits do not count towards the per-unit limit of generated pictures and need no exercise (sanity treats them like the dialogue portraits); scenes of the heroes (`sc_*`) do need an exercise.

**Consistency of the cast.** Pictures of a hero in different places (a card, a family scene, a dialogue) must look like the same person: take the figure from the family scene or
the portrait as the reference, not from memory. A card that disagrees with its scene (a grandfather with long hair in one place and short in another) is redrawn.

**Picture index.** `course2/pictures_index.json` and `pictures_index.html` are built by `build_picture_index.py` at every course build, from `image_info.json` (description, how made),
`pictures/image_meta.json` (who, references, sheet; written by `gen.py`), `image_who.json` (who for older pictures) and the course (where each picture is used). Nothing is kept by hand
except `who`: a Gemini picture with people must have it (`sanity.py` «КТО»). Add a new picture by generating it with `gen.py` (its meta is written), adding its description to
`describe_gemini.py`, and running the build.

**Guard.** `pictures/gen.py` refuses a prompt with people that does not say "Buryat" (the setting block), and keeps the previous raw answer when a picture is regenerated.

**Checked, not just written: `sanity.py` warns «КАРТИНКА-УПР» for every model (Gemini) picture that stands in a unit's theory and in none of its exercises (hero portraits of dialogues are exempt). Answer by an exercise, or by a reason in `build-notes/buryat/course2/picture_exercise_reasons.json` (key `unit|picture`). Good forms: `match` with the pictures on the left and words or sentences on the right (add one near-miss in `extra`), `choice` with the picture as `aside` (`top`, `large`) and a question the picture answers, `pictures`. The picture must not show the answer (no caption with the key).**

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
- **Local colour is a base, not a costume.** Keep one file of sights, nature and culture of the place (Buryat: `build-notes/buryat/local-color.md`) and take from it **at most one or two elements per section**: a recognisable sight in a picture (the giant head of Lenin in Ulan-Ude), a place or dish as an example of a rule, a short `fact`. The course stays modern and neutral; people live in ordinary houses and wear ordinary clothes. The first Buryat course drowned in yurts and costumes: a yurt, a festive dress or a ritual appears only when that is the topic. Test: would a local reader recognise their everyday life, or a tourist postcard? If an example fits any language, keep it plain and add one local detail (a name, a place, a dish). Never invent names of local things: check them like any other word.
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
