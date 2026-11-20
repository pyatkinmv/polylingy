# Checking a disputed word or form

A course for a language the author cannot check (the Buryat course is the case) needs one procedure for "is this form right?". It serves reviewers' findings, questions for a native speaker and your own doubts. Decide only when the evidence is clear; otherwise write the question down for a native speaker.

## Order of sources

1. **The course's own books** (for Buryat `build-notes/buryat/intro/nk.txt`, `sk.txt`): is the word or form there, in which context? A form the books print is kept unless several other sources contradict it.
2. **The dictionary** (`build-notes/buryat/dictionary/burdict.db`, `find.py`; Shagdarov and Cheremisov): headword, meaning, derived forms, examples. It decides meanings and the spelling of headwords (түхэриг = рубль; гудамжа; самолёт as the headword).
3. **The corpora** (`build-notes/buryat/corpus/`, `corpus.py`, README beside them):
   - the **monolingual** one (215 000 lines of OCR-scanned book text) answers *which form is used and how often* (`count`, `forms`, `grep`);
   - the **parallel** one (120 000 Buryat–Russian pairs, with the source of each pair: dictionary examples, books, the Bible, news) answers *what a word or phrase means and how it is translated* (`par`, `parcount`). Pairs from the newspaper translated by a model are excluded by default: the Russian side is machine-made.
4. **Websites** (Toli `buryat-lang.ru`, Wiktionary, a web search): last, and only to confirm; coverage is poor and search by form is slow. A search that returns nothing proves nothing.
5. **A native speaker**: everything the sources cannot settle. Write it in the revision log (`## Для носителя`), not only in the chat.

## What the corpora can and cannot decide

| Question | Source |
|---|---|
| Does this form exist? Is one variant far more frequent (hundreds against none)? | Monolingual corpus. Decides; change the course. |
| Which postposition or case goes with which kind of noun (place or time)? | Monolingual (`grep` with the word before), confirmed by the parallel pairs. |
| **Meaning of a word** (is үнэгүй "cheap" or "free"? is түхэриг a rouble or a tugrik?) | **Parallel corpus and dictionary only.** A monolingual context can mislead: үнэгүй looked like "free" there and is "cheaper, cheap" in 14 of 21 translated pairs. |
| Antonym pairs (сэбэр — бузар, not сэбэр — муухай; тарган — туранхай, not нариихан) | Parallel corpus and the dictionary: look at what each word translates to. A pair printed side by side in a text (the Bible: "сэбэр бэшэ, бузар") is strong evidence. |
| Two forms both frequent (тэрэниие 1345, тэрээниие 557) | Both are right; keep the books' form, note the other. |
| A rule of vowel harmony | Counts alone do not describe it (by the first vowel 78 per cent, by the last 72); confirm every form the course shows, do not rewrite the rule. |
| Anything rare (fewer than about 10 hits) or not found | Does not decide: the text is noisy and literary. Ask a native speaker. |

Do not use sentences or quotations from the corpora in the course (exercises, examples, theory) and do not copy them as they are: the corpora may contain inaccuracies (OCR, machine translation, dated language) and nobody on the project can verify Buryat independently. They check forms and meanings; examples come from the books or are composed and marked as composed. Using corpus text in the course is possible only after the user has validated it.

## Procedure

1. Write the question as a pair or a set of forms ("автобусоор / автобусаар", "хойно / һүүлдэ after a noun", "what does үнэгүй mean").
2. Forms: `corpus.py count`, `forms`, `grep`. Meanings: `corpus.py par` (`--src словарь` for dictionary pairs only, `--ru` to search the Russian side), `parcount`.
3. Compare with the books and the dictionary. Record numbers, not impressions: "гэрэй хойно 21, гэрэй һүүлдэ 0"; "түхэриг: 423 pairs with рубл, 0 with тугрик".
4. Decide: **clear** (the course changes, the numbers go into the log), **confirmed** (the course is right; list the form in `corpus-findings.md`) or **open** (native speaker; say what the corpora did not settle).
5. Apply the change in the generator (never in the built file), rebuild, run `sanity.py` and `checkCourse`, then add the result to `build-notes/buryat/corpus-findings.md` and `revision-log.md`.
6. If you later learn that a decision was wrong, say so in the findings (the first version of the үнэгүй decision relied on the monolingual corpus only and was reversed by the parallel one).

When a reviewer is unsure ("form X may be wrong"), run this procedure before triage: a finding that the corpora and the books both contradict is rejected with the numbers; one that they confirm and the books do not is fixed.
