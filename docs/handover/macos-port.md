# Handover: porting Polylingy to macOS

For a fresh Claude session (or a person) that takes this over with no history. Read it first, then `CLAUDE.md` (project rules, modules, key files) and `docs/roadmap.md`.
Written on 2026-11-21 at version **0.8.6**; if the version in `gradle.properties` is higher, check what changed since.

## 1. What Polylingy is, in three lines

A desktop app for learning languages by spaced repetition: Kotlin Multiplatform + Compose Multiplatform (desktop/JVM only), SQLite, JDK 25 by Gradle toolchain.
Only the Windows desktop target is built and checked today. The Mac version is **for the user only** (no signing or notarization needed: right-click → Open is enough).

## 2. State of the project (2026-11-21)

- **Version 0.8.6, branch `master`**, everything is committed; the working tree was clean at the end of the last session.
- **Not pushed to GitHub.** `origin` is `https://github.com/pyatkinmv/polylingy.git`; the last push was on 2026-10-31 (version 0.1.0). Local `master` is ~70 commits ahead. Ask the user before pushing.
- **Not in git (ignored):** `build-notes/` (e.g. `build-notes/anki/`: the sources, reports and scripts that produced the English course; the finished course is in git as
  `client/src/desktopMain/resources/bundled/english-usage.zip`), `.env`, the Buryat/Arabic courses (`build-notes/buryat` is a separate repository).
- **The MVP (release 0.8.x alpha) for Windows is almost done** (`docs/roadmap.md`, iteration 1): tasks 1–10 are done. Left: task 11, the release itself:
  build the MSI (`./gradlew :app:releaseMsi`), install it by hand, update over an older one, save the compatibility baseline database of the released version, check
  `docs/install.md` against the real installer. The installer and the real window can't be tested by the agent; the user does that.
- **Course:** the bundled `English Usage Exercises` is version 2 (333 topics, 3801 exercises, all rewritten and reviewed). New ids on every exercise, so progress
  made on the old (v1) course does not carry over; nobody has v1 released, so this does not matter.
- **Defaults** (0.8.6): the ready set `ladder` is a ladder without end (1·3·7·14·30 days, then every gap doubles), every ready set limits new topics to 15 a day.

## 3. How the user wants to work (follow these)

- **Branch before any feature or fix** (`git checkout -b feat/...`); never commit to `master` directly. **Commit and merge only when the user says so**
  ("закоммить и вмержи" = commit, then fast-forward merge into `master`). Commit trailer: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- **Every merged change bumps the version** in `gradle.properties` (PATCH for fixes and small things, MINOR for big features; ask if unsure).
- **UI changes:** add or extend a scenario in `app/src/desktopTest/.../ui/*UiTest.kt`, call `screenshot("name")`, run the tests, open the PNGs in `app/build/screenshots/` and look
  at them. Then run the app so the user can see it. On Windows that is `scripts/relaunch.ps1` (closes the running Polylingy, builds, starts); there is no Mac equivalent yet, see 5.7.
- **Never stop processes other than Polylingy's own.** Match by command line, not by window title.
- **Design on the canvas first** when the user asks for options: https://claude.ai/artifact/GG67cXLE8xTj8radt7grUV (pages r1…r30). The user dislikes complicated UI:
  fewer controls, fixed sensible limits, no jargon. Explain by short examples; answer "why" questions with an explanation, change code only when asked.
- **Save tokens:** no subagents for small things, no deep research when a short answer will do, no long recaps. Say what is done, what is not verified, what is left.

## 4. Why the app is nearly portable already

Compose Multiplatform desktop runs on macOS (Apple Silicon and Intel). `app/build.gradle.kts` uses `compose.desktop.currentOs`, so on a Mac it takes the macOS
natives by itself. `sqlite-jdbc` (SQLite), JLayer (sound), AWT `FileDialog` (import and export of courses) and the bundled font Inter (`app/src/desktopMain/resources/fonts`)
all work on macOS. `Desktop.getDesktop().open(...)` ("Open log folder") works too. The core, the client and the tests have no Windows code.

## 5. What has to change for macOS

Nothing below has been tried: the agent works on Windows and can't run a Mac build. Treat it as a plan, check every step on the Mac.

1. **Data folder** — `client/src/desktopMain/kotlin/com/polylingy/client/DesktopStorage.kt`, `defaultDataDir()`. The installed app uses `%LOCALAPPDATA%\Polylingy data`, falling
   back to `~/.local/share`. On macOS it must be `~/Library/Application Support/Polylingy data` (one branch on `os.name`). `documents()` (`FileSystemView`, used for the
   development folder `~/Documents/polylingy` and the backup mirror `~/Documents/Polylingy backups`) is fine on macOS. Update `CLAUDE.md` ("Storage") and `docs/install.md`.
2. **Packaging** — `app/build.gradle.kts`, block `compose.desktop.application.nativeDistributions`: today `targetFormats(TargetFormat.Msi)` and a `windows { … }` block. Add
   `TargetFormat.Dmg` and a `macOS { … }` block: `iconFile` (an `.icns`; make it from `app/src/desktopMain/resources/polylingy.png`, `iconutil` on the Mac does it),
   `bundleID` (e.g. `com.polylingy.app`), `dockName = "Polylingy"`. **The DMG version must start with a number ≥ 1**: `0.8.6` is rejected by `jpackage`; set `dmgPackageVersion`
   (e.g. `1.<minor>.<patch>` of the app version, so 0.8.6 → `1.8.6`) and keep `packageVersion` for Windows. Keep `modules(...)` and `jvmArgs += "--enable-native-access=ALL-UNNAMED"` as they are.
   Make the task names work per OS (`packageMsi` on Windows, `packageDmg` on macOS); `releaseMsi` renames the MSI to `Polylingy-<version>-<stage>.msi`, add a twin for the DMG.
3. **`jpackage.app-path`** (how `DesktopStorage.isInstalled()` tells an installed app from a run from the sources): jpackage sets it for all platforms; check on the Mac that
   it is set in the installed `.app` and not in `./gradlew :app:run`.
4. **Fonts and text:** Inter is bundled; `InterFont.kt` says it draws "with subpixel smoothing like text in a browser on Windows". Look at the real text on a Mac and adjust only if it looks wrong.
5. **Keyboard shortcuts:** Compose maps Ctrl to Cmd for the standard ones (copy, paste, select all). The app has its own key handling in
   `content-compose/src/commonMain/kotlin/com/polylingy/content/compose/Exercises.kt` (around line 213, `onPreviewKeyEvent`: Enter and the extra keys over a text field);
   check Enter / Return and the extra keys on a Mac keyboard layout.
6. **Window and menu bar:** the window's place is remembered (`setting` table); check that it comes back, that Cmd+Q and Cmd+W work, that the Dock shows the right name and icon.
7. **Developer scripts:** `scripts/relaunch.ps1` is PowerShell. A `scripts/relaunch.sh` for the Mac would close the running Polylingy (match by the command line `com.polylingy.app.MainKt`
   or by the installed `.app` path), build and start `./gradlew :app:run`.
8. **Tests:** the Compose UI tests (`app/src/desktopTest/...`) use Skiko and should run on macOS; screenshots may differ a little in anti-aliasing and font rendering,
   so they are for the eye, not for exact comparison. Two selection tests are already skipped when the system clipboard is busy (`skippedWhenBusy`).

## 6. Build and run on the Mac (to be checked)

```bash
uname -m                                 # arm64 = Apple Silicon; x86_64 = Intel (one DMG per architecture, jpackage has no universal build)
./gradlew :app:run                       # JDK 25 is provisioned by the Gradle toolchain
./gradlew check                          # all tests; screenshots in app/build/screenshots/
./gradlew :app:packageDmg                # -> app/build/compose/binaries/main/dmg/ (after step 5.2)
```

An unsigned DMG: Gatekeeper says "unidentified developer"; open it with right-click → Open (the user is the only user). Signing and notarization need an Apple Developer ID ($99 a year)
and are not planned.

## 7. What the agent can't verify without a Mac

The real window and the Dock, the `.icns`, the DMG itself and the installed `.app`, the data folder of the installed app, keyboard layout, native file dialogs, sound
output, font rendering. Ask the user to run the checklist below and report what they see.

### Checklist on the Mac

- [ ] `./gradlew :app:run` starts, the course list shows `English Usage Exercises`, version label at the bottom.
- [ ] Study an exercise: type an answer, Check, Next; the extra keys over a text field work.
- [ ] Import a course file and export one (native dialogs).
- [ ] Study settings (the ladder: chips, the check "After the last step…", "Last step: …").
- [ ] Statistics tab, Extra practice.
- [ ] Sound of a course with audio plays (`examples/greetings` or any course with `audio`).
- [ ] Quit and start again: window position, data and progress remain.
- [ ] Installed DMG: the data is under `~/Library/Application Support/Polylingy data`, not in Documents; backups appear in `backups/` and in `~/Documents/Polylingy backups`.
- [ ] Update over an older installed version keeps progress (same as the Windows check in `docs/install.md`).

## 8. Open questions for the user

- Which Mac (Apple Silicon or Intel)? Decides which DMG to build.
- Build on the MacBook itself, or later through GitHub Actions (`macos-latest`)? Needs the repository pushed to GitHub first.
- When to do it: the plan is to release the Windows MSI first (roadmap task 11) and port to the Mac on a separate branch, e.g. `feat/macos`.
