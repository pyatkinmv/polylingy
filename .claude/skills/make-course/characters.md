# Characters of a course

A course with people in it (dialogues, examples, pictures) has **one cast**, written down once, reused everywhere, and checked. Not a name per exercise.

## The cast is a file, not a memory

- Each course keeps its cast in one place the generator and the checks read: for the Buryat course `build-notes/buryat/course2/make_cast.py` (writes `cast.json` and
  `cast.md`). Edit the script, run it, never edit the generated files by hand.
- One record per character: name, gender, age, role, home, relations (family tree), **look** (a short stable core), palette, portrait file, units, status, and
  `identity_en` (one English line that goes into every picture prompt of that character).
- Places belong to the cast too (where the heroes live and come from), and **retired names** (names removed from the course) are listed so they do not creep back.
- Size: a small cast, at most 20 for a whole course (`max_cast`). A new person is added only when a unit needs one; a one-off name for one example is not a reason, take
  someone from the cast whose role fits.

## Rules

1. **Reuse before adding.** Every example, dialogue, letter and exercise takes its people from the cast. Before a new name, look for a character whose role fits (a friend,
   a relative, a teacher). Merge near duplicates into one character.
2. **A role in the story and in the grammar.** The person who owns a thing (genitive), who comes with someone (comitative), who is met (accusative), who comes from where
   (ablative) is a character the learner already knows. The story is a set of separate episodes, light, with no romance and no culture facts the sources do not give.
3. **Names come from the sources** (the books, with a native speaker's confirmation) and are checked by a native speaker; the check is recorded in `status`. Do not invent a
   name that sounds right. Where a name has forms that change with the sounds (endings), prefer names whose endings the course already shows.
4. **Places are real places of the course's country or region.** A character's home is a real place in it (for Buryat characters, in Buryatia); other regions appear only as
   places on maps, boards and trips.
4a. **Journeys are possible.** Check every trip against the real map (what runs between the places: rail, road, air) and record the facts per place in the course's geography file (`geography.json`); the `ГЕОГРАФИЯ` check in `sanity.py` reads it. A route that cannot exist (a plane from a village with no airport, a station for someone who came by bus) is a defect, not a detail.
5. **Ages and roles agree.** A first-year student is 18, not 20; a teacher looks like a teacher; a family has a family tree (the grandparents of one hero are not the parents
   of another).
6. **Change the cast deliberately.** Add or change a character in the cast script, then: regenerate what shows them (portrait, sheets, scenes), update the family tree and
   `units`, run `sanity.py` (warnings `ИМЯ`, `ПЕРСОНАЖ`, `КТО`), rebuild, and record the change in the revision log. Never rename a character by replacing a substring: endings and
   the harmony of vowels change with the name.

## Looks

- A character has a **stable core**: face shape, build, hair (colour, length, style), age, and one or two clothes colours. Clothes change with the scene and the season;
  the face and hair do not. No invented tics (moles, glasses, scars) unless a picture already has them: every extra feature is one more thing to keep equal in every picture.
- The cultural setting is in `pictures/SETTING.md` (ordinary modern clothes and homes; at most one small traditional detail; no costume and no yurt unless the topic is
  that). The small detail differs between characters (a scarf, a ring, a pendant), not the same on everyone.
- Every prompt with people starts from the cast line (`identity_en`) plus the setting block; `pictures/gen.py` refuses a people prompt that does not have it.

## Portraits and pictures of heroes

See `pictures.md`: "Sheets or single pictures", "Reference portrait", "Picture index". In short: a portrait first (the reference), then scenes made **from the portrait as an input
image**, six scenes on one sheet where details do not matter, one picture where the scene must match a sentence. The index of pictures records who is on each picture.

## Introducing the cast

Heroes are introduced where they first appear: a heroes card section (`Знакомьтесь`) with the portrait, one line in the grammar the unit already teaches, and a Russian line on who the person is. A family is shown once, with a table of generations. Cast portraits are made for every character (`pictures.md`, "Cast portraits and cards").

## Checks

`sanity.py` (module `sanity_cast.py`): `ИМЯ` (a person's name that is not in the cast; the cast exceeds its maximum), `ПЕРСОНАЖ` (a character in the cast who is never used),
`КТО` (a picture with people whose index entry says nobody). Every warning is answered by a change in the course or in the cast, not by silencing the check.
