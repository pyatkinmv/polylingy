# Before handing a course over

Every item is done, or the answer says why it was not.

1. `python .claude/skills/make-course/lint.py <course.json|zip>` → 0 suspicious texts.
2. `./gradlew :client:checkCourse -Pcourse=<file>` → `OK`.
3. The sanity check (`sanity-check.md`) is done, including the fresh-eyes pass.
4. Reports in `build-notes/`: marked-up PDF of the source, `pictures/` with `index.html`, and — when the user cannot
   read the language — the review file for a native speaker (`transfer.md`, `pictures.md`).
5. Changed code or a new kind of screen: a UI test with `screenshot("name")`, `./gradlew check`, and the PNGs in
   `app/build/screenshots/` opened and looked at (see `docs/testing.md`).
6. Imported into the real database and the app relaunched (CLAUDE.md, "Verifying Changes"):
   - stop only Polylingy's own processes (match by command line), never others;
   - import next to an already-studied copy of the course and leave that copy alone; delete an older copy only when it
     has no journal events and the user allowed it;
   - run `scripts/relaunch.ps1` in the background, check the window came up, and say which version runs.
7. The transfer map and the list of pictures that are not in the course are rebuilt from the final `course.json` and match it.
8. The answer to the user: what was carried over, changed and left out; what is generated; what could not be
   verified; where each report is. Commit only when asked.
