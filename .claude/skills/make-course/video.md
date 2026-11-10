# Videos in a course

Rules agreed with the user (Buryat course, 2026-11). A video is always part of the **theory**; exercises never carry a video "to look at before answering".

## Roles

| Role | What it is | Example |
|---|---|---|
| **Explainer** | a fragment ("watch from X to Y") of a lesson that explains the rule or the words of the section | a teacher shows how counting works |
| **Song or speech** | the song itself (or a short clip of speech, a cartoon), made to be listened to | Altargana, the anthem |
| **Analysis of a song** | a teacher takes the song apart | a breakdown of Altargana |

A song may have two videos: the song and its analysis. Both are good; they count separately.

## Limits

- **1 to 3 video items per unit, mostly 2**; an item is a clip, a fragment or a song. A unit may have none.
- Short: a whole song, or a fragment, not a long lesson. Long videos only as a fragment "from X to Y".
- Time marks come from the transcript and are approximate: check them by watching before they go into the course.

## Where they live

1. **Theory:** a `video` block, in its own small section named after the video ("Video: …") **with an `id`**, so that a link from an exercise lights up exactly that section.
2. **Explainer:** the exercise that checks what the fragment says has an `explanation` (shown after the check, also when the answer is right) with a short comment (why it is so, where to look in the video) and a `seeAlso` link to that section. The exercise has no `video` layer.
3. **Song, short clip, speech:** the video is in the theory too, and the theory holds **everything the exercises need**:
   - the text of the song in the course script, checked against a reliable source;
   - the translation;
   - the words with meanings (each in the lexicon of the course, checked as any word: textbook, dictionary, the site Toli);
   - a short comment on the song (what it is about, the forms the course has already taught).
   Exercises are then built from this text: fill in a word of a verse, match a word and its meaning, put the lines in order, listen and choose. A song with its own topic has text and comment in the theory; without them the song does not go into the course.

## Finding the text of a song

- Search the internet for the text and the translation; compare it with the words that the analysing video says (its transcript is good Russian, the song itself is badly recognised).
- **If no reliable text is found, stop and tell the user.** Never write a Buryat text from the subtitles or from memory.
- The words must pass the same check as all course words (`lexicon.txt`, `verify_lexicon.py`, Toli).
