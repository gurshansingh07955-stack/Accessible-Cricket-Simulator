# HANDOFF — Accessible Cricket Simulator (Android)

**Paste this whole document into a new chat to continue work with zero
re-discovery.** It is the single source of truth for where things stand,
how to work in this repo (including the one real gotcha), and what is
actually left to do. `PORTING_NOTES.md` (logic layer) and `UI_NOTES.md`
(everything else) remain the detailed, file-by-file history behind every
claim made here — read them for depth; read this first for orientation.

---

## 1. The two projects — do not confuse them

### The Floot web app (source of truth for gameplay design, fully working)

```
Floot project ID: 1b80fd76-e07a-445c-a677-45aa31f1df6f
Live app:         https://5764.floot.app
```

A complete, accessibility-first cricket simulator, playable entirely by
screen reader. It has never needed changes during the Android port —
everything below was read from it, never written to it. To resume work on
it directly, use the project ID above with Floot's own tools
(`list_files`, `read_file`, `edit_file`, etc.) exactly as every session on
this app has worked.

### The Android port (this repo — the active project)

```
GitHub repo: https://github.com/gurshansingh07955-stack/Accessible-Cricket-Simulator
Owner:       gurshansingh07955-stack
Repo name:   Accessible-Cricket-Simulator
Visibility:  PUBLIC — this matters, see section 4.
```

A from-scratch native rewrite (not a mechanical translation) that ends in
a real, installable APK. GitHub access goes through a custom MCP
connector (shows up in the tool list as a GitHub-flavoured MCP server —
confirm it can read/write this specific repo before starting). The two
files that track status in detail:

- **`PORTING_NOTES.md`** — the pure game-logic layer, file by file against
  the web source. **100% complete.**
- **`UI_NOTES.md`** — everything native to Android: the whole UI, the
  three custom gesture surfaces, audio, settings, save/resume, the home
  and about screens, and every known issue / thing still needing a real
  device. **Feature-complete; nothing has ever been compiled.**

Re-read both live rather than trusting summaries (including this one) to
be pixel-perfect — they are kept genuinely current, but a new session
should still verify before assuming.

---

## 2. Where things actually stand

Everything the app is meant to do is written:

- **Logic layer** — 100% ported and numerically verified against the web
  source (data, weather, bowling, batting, fielding, match engine, match
  state machine, stats, commentary library).
- **UI** — the full pre-match setup flow, all three custom gesture
  surfaces (pitching, batting, fielding, with Normal/Defensive/Attacking
  field presets), the match screen, scorecard, opener/new-batsman/bowler
  selection, rain delays, the innings break and match-result screens, and
  a home screen (Play match / Resume match / Settings / About) with a
  proper theme.
- **AI** — plays with the web's own situational-bias formulas (attacks in
  the powerplay and death overs, protects wickets in a collapse, chases a
  required rate, etc.), not a flat/neutral opponent.
- **Audio** — fully built and **bundled into the repo**: 7 sound
  recordings + 160 of 190 AI commentary clips are packed into one
  encrypted resource and play offline (see section 5 for the 30 that
  aren't there yet, and the key).
- **Settings** — difficulty, sound, vibration, spoken commentary (for
  when TalkBack is off), AI commentary voice mode, crowd volume.
- **Persistence** — a match autosaves after every ball and can be resumed
  from the home screen; leaving a match no longer discards it.
- **Security/compatibility hardening** — done in the same session that
  produced this document (see section 6).

**The one thing genuinely not done: nothing has ever been compiled.**
Every file above was written against the platform APIs as read from
documentation/source, not against a running build. A first compile pass
should be expected to turn up small, normal errors (a wrong import, an
argument order, a type mismatch) — this is completely expected for a
codebase this size that has never touched a compiler, not a sign
something is fundamentally wrong.

---

## 3. How to compile and build this — read before starting

**There is no Gradle wrapper committed to this repo** (`gradlew`, `gradlew.bat`,
`gradle/wrapper/`). Any CI workflow or local setup must either generate one
first, or install/point at a system Gradle directly. The project's own
AGP version is 8.5.2 (declared in the root `build.gradle.kts`), which
needs **Gradle 8.7 or newer** — the official `gradle/actions/setup-gradle`
GitHub Action with a `gradle-version` input is the simplest way to get a
working `gradle` command in CI without generating a wrapper first.

### Recommended: a GitHub Actions build workflow

This was the planned next step (not done yet as of this document, since
the user asked for this handoff first). A new session should set up a
workflow that:

1. Checks out the repo.
2. Sets up JDK 17 (Temurin) via `actions/setup-java@v4`.
3. Sets up the Android SDK via `android-actions/setup-android@v3`
   (accepts licences; AGP downloads whichever `compileSdk`/build-tools it
   needs on demand once licences are accepted).
4. Sets up Gradle via `gradle/actions/setup-gradle@v4` with
   `gradle-version: '8.7'` (or newer).
5. Runs `gradle assembleDebug --no-daemon --stacktrace`, capturing all
   output to a log file, and **treats a non-zero exit as "continue anyway"**
   (`continue-on-error: true` on that step) so the next step always runs.
6. **Writes the last few hundred lines of that log to a plain text file in
   the repo** (e.g. `tools/build_report.txt`) and commits it. This is the
   important part: an assistant with only public-API file-reading access
   (like the one writing this document) **cannot read Actions run logs**
   (they require authentication even on a public repo), but it **can**
   read any file committed to the repo. Committing the log as a plain file
   is what lets a new chat actually see compile errors and fix them
   without the user needing to copy-paste anything.
7. Uploads the built APK (if it succeeded) as a **build artifact** via
   `actions/upload-artifact@v4` — NOT committed into git — so the user can
   download and side-load it from the Actions run page without bloating
   the repository with a large binary on every push.

### The one real gotcha: creating/editing files under `.github/workflows/`

**The GitHub connector's access token cannot create OR update files under
`.github/workflows/` via the API.** Every attempt returns:

```
403 Resource not accessible by integration
```

This happened twice already in this project (once for the audio-fetch
workflow, and it will happen again for a build workflow) and is almost
certainly the token missing the "Workflows: Read and write" permission
(fine-grained PAT) or the `workflow` scope (classic PAT). **Two ways
through it, in order of preference:**

**A. Ask the user to grant the permission** (fastest for repeated work in
the same session):
- Fine-grained tokens: https://github.com/settings/personal-access-tokens
  → open the token → Repository permissions → **Workflows** → **Read and
  write** → save.
- Classic tokens: https://github.com/settings/tokens → open the token →
  tick **workflow** → Update token.
- Once granted, `push_files`/`create_or_update_file` on `.github/workflows/*`
  work normally like any other path.

**B. The pre-filled-link technique (works with zero permission changes,
used twice successfully in this project):** GitHub's own "create new
file" web page accepts the target file name and its entire contents as
URL query parameters, so a single link can arrive with everything already
typed in — the user only has to click **Commit changes** twice (once to
open the commit dialog, once to confirm). This works because the *user's
own browser session* is what creates the file, not the API token.

How to build one (verified working method — do this with the bash tool,
not by hand, to avoid any encoding mistake):

```python
import urllib.parse
content = open('/path/to/the/workflow.yml').read()   # the exact file content
base = "https://github.com/gurshansingh07955-stack/Accessible-Cricket-Simulator/new/main"
url = (
    base
    + "?filename=" + urllib.parse.quote(".github/workflows/whatever.yml", safe="/")
    + "&value=" + urllib.parse.quote(content, safe="")
)
# ALWAYS verify the round trip before handing the link to the user:
q = urllib.parse.parse_qs(urllib.parse.urlparse(url).query)
assert q["value"][0] == content
assert q["filename"][0] == ".github/workflows/whatever.yml"
print(url)
```

Give the user the link and exactly two instructions: "the file name and
content should already be filled in — don't type anything — scroll down
and click **Commit changes...**, then **Commit changes** again to
confirm." The commit fires the workflow automatically if its own trigger
includes a `push` on the file just created.

**Once a workflow file already exists** (like `fetch-audio.yml` does
now), pushing changes to files it lists under `on: push: paths:` — even
files OUTSIDE `.github/workflows/`, like `tools/fetch_audio.sh` — *does*
auto-trigger it, and that kind of push is NOT blocked by the 403 (only
writes inside `.github/workflows/` itself are). This was used deliberately
when the audio system was reworked: rewriting `tools/fetch_audio.sh` and
pushing it re-ran the existing `fetch-audio.yml` with zero workflow edits.
**Prefer this over touching a workflow file at all, whenever the trigger
file is something you can already write to.**

**After using either method, verify the run actually happened** — don't
trust that a push "probably" triggered something. The repo is public, so
its Actions run list is readable without authentication:

```javascript
// Run this via the Floot VM's run_code_in_vm tool (it has outbound network
// access; the bash_tool in this environment does NOT).
const repo = "gurshansingh07955-stack/Accessible-Cricket-Simulator";
const H = { "User-Agent": "check", "Accept": "application/vnd.github+json" };
const r = await fetch(`https://api.github.com/repos/${repo}/actions/runs?per_page=5`, { headers: H });
const data = await r.json();
for (const run of data.workflow_runs || []) {
  console.log(run.id, run.name, run.status, run.conclusion, run.head_sha.slice(0,7));
}
```
This was how both the audio-fetch runs in this project were confirmed
successful (and how a compile-report workflow's success/failure should be
confirmed too, before assuming the committed report file is fresh).

### Local alternative (no CI at all)

Android Studio (any recent version compatible with AGP 8.5.2 / Kotlin
1.9.24) can open this repo directly — Android Studio generates the
Gradle wrapper itself on first sync if one is missing, and has its own
bundled Android SDK, so this sidesteps the CI-specific setup above
entirely. This is the fastest path if the user has a development machine
available and doesn't want to wait on CI.

---

## 4. Why the repo being PUBLIC matters everywhere

This repo is public on GitHub. Two concrete consequences that already
shaped decisions in this project, and will keep mattering:

1. **Anything committed to it is world-readable, permanently** (even
   after a later commit removes it, it stays in git history unless the
   history itself is rewritten). This is why the audio encryption key
   (section 5) does not provide real secrecy — it is sitting in
   `tools/fetch_audio.sh`'s own commit history for anyone to read.
2. **An assistant with only unauthenticated, public-API access (reading
   raw file contents, listing Actions runs, etc.) can verify real,
   current facts about the repo without needing the user to paste
   anything** — this is how audio file sizes were checked against the
   live host, how workflow run outcomes were confirmed, and how this very
   document's facts were checked before being written. **A new session
   should keep using this:** prefer checking a claim (via the Floot VM's
   `run_code_in_vm`, which has outbound network access, or via the GitHub
   MCP tools' `get_file_contents`) over asserting it from memory.

---

## 5. Audio — exact state, the key, and what's left

### The encryption key (so it can be changed later)

```
KEY_HEX = df49894bbeb5bc80ee17563f080390e2eae82814a6d58bcf8315519abc24ec66
IV_HEX  = 48b415240d5285efe644906d4b4a3b2a
```

These two values **must always match exactly** in two places, or every
bundled sound will silently fail to decrypt (the code treats a decrypt
failure as "nothing bundled", not a crash — see `AudioPack.kt` — so a
mismatch would NOT show an error, it would just make everything play a
synthesized stand-in or stream from the network instead):

1. `tools/fetch_audio.sh` — the `KEY_HEX`/`IV_HEX` shell variables, used to
   `openssl enc` the pack when it's built.
2. `app/build.gradle.kts` — the same two strings, as the fallback values
   in the `buildConfigField(...)` calls for `AUDIO_PACK_KEY_HEX` /
   `AUDIO_PACK_IV_HEX` (read into `BuildConfig`, then into `AudioPack.kt`).

**To change the key:** edit both places to the same new 64-hex-char
(32-byte) key and 32-hex-char (16-byte) IV, then re-run the fetch
workflow (Actions tab → "Fetch audio into the app" → Run workflow) so the
pack gets rebuilt with the new key. Generate a fresh pair safely with:
```python
import secrets
print(secrets.token_hex(32))  # key
print(secrets.token_hex(16))  # iv
```
**Verify the length is exactly 64 / 32 programmatically before using it**
— do not hand-count hex characters; this project already caught itself
nearly using a mis-transcribed value this way.

### What this key actually protects (and doesn't)

Full honest explanation lives in `AudioPack.kt`'s doc comment; in short:
it stops a **casual** `unzip app.apk` / `adb pull` / generic asset
scraper from handing someone a folder of playable MP3s. It does **not**
stop a **motivated** person — the key ships inside the app to decrypt
on-device (true of any on-device asset protection, on any platform), and
because this repo is public, anyone can also just read the key straight
off GitHub without ever touching the APK. **The genuinely stronger
upgrade**, if this ever matters more than shipping fast: move the key to
a GitHub Actions **secret** (Settings → Secrets and variables → Actions →
New repository secret) that is never committed, and pass it into the
build as `-PaudioPackKeyHex=... -PaudioPackIvHex=...` (Gradle properties
sourced from `${{ secrets.WHATEVER }}` env vars in a workflow) — this is
already wired up on the Kotlin/Gradle side to prefer a passed-in property
over the committed fallback, so this upgrade needs no code changes, only
a secret and a workflow env var.

### The 30 commentary clips that don't exist yet

Of the 190 clips `CommentaryLibrary.kt` can reference, only 160 have ever
been generated on the web app; these 30 do not exist there at all (not a
bundling problem — there is nothing to bundle):

```
bowler10wkts_1_calm.mp3        bowler10wkts_1_excited.mp3
bowler10wkts_2_calm.mp3        bowler10wkts_2_excited.mp3
bowler3wkts_1_calm.mp3         bowler3wkts_1_excited.mp3
bowler3wkts_2_calm.mp3         bowler3wkts_2_excited.mp3
bowler5wkts_1_calm.mp3         bowler5wkts_1_excited.mp3
bowler5wkts_2_calm.mp3         bowler5wkts_2_excited.mp3
doublehattrick_1_calm.mp3      doublehattrick_1_excited.mp3
doublehattrick_2_calm.mp3      doublehattrick_2_excited.mp3
hattrick_1_calm.mp3            hattrick_1_excited.mp3
hattrick_2_calm.mp3            hattrick_2_excited.mp3
onhattrick_1_calm.mp3          onhattrick_1_excited.mp3
onhattrick_2_calm.mp3          onhattrick_2_excited.mp3
partnership150_2_calm.mp3      partnership150_2_excited.mp3
partnership200_1_calm.mp3      partnership200_1_excited.mp3
partnership200_2_calm.mp3      partnership200_2_excited.mp3
```
(the exact live list is always in `tools/audio_fetch_report.txt`, which
gets rewritten every time the fetch workflow runs — treat that file, not
this document, as authoritative if they ever diverge.)

These are bowler wicket-haul milestones (3/5/10 wickets), hat-trick and
double-hat-trick moments, and two partnership milestones (150, 200) —
all genuinely rare in-match events, which is presumably why they were
never prioritised for generation on the web app. **The user said they
will generate these later** on the Floot web app (it has a
`commentary_tts_generate`-style capability referenced in the codebase);
once they exist there, re-running `tools/fetch_audio.sh` (by hand, or by
re-running the "Fetch audio into the app" workflow) will pick them up
automatically — nothing else needs to change. Until then, those specific
moments simply have no AI voice line (exactly as on the web app itself,
which has the same gap).

### The crowd ambience recording's licence — needs a human decision

`app/src/main/res/raw/audio_pack.bin` bundles a recording whose original
file name (visible in `tools/fetch_audio.sh`'s download list) is:
```
arunangshubanerjee-live-football-match-stadium-crowd-cheering-5634.mp3
```
That naming pattern (uploader handle + descriptive title + a numeric ID)
is characteristic of a stock-audio marketplace (e.g. Pixabay/Freesound-
style attribution or licensed stock audio), not something bespoke to this
project. **This has not been verified either way** — nobody has actually
checked what licence this specific file carries or whether it permits
redistribution inside a bundled, closed-source APK. **This needs a human
to check before the app is distributed**, because it is now committed to
the repo and would ship inside every APK. If the licence turns out not to
permit this:
- Remove `CROWD_BED` from `tools/fetch_audio.sh`'s download list (the one
  line with that URL) and re-run the fetch workflow to rebuild the pack
  without it.
- Nothing else breaks: `AudioPack.bytesFor` returning null for it just
  makes `SoundEngine` fall back to the synthesized crowd bed
  (`Synth.crowdBed()`), which already exists and was built specifically
  so nothing is ever silent.

---

## 6. Security and compatibility — what was actually done

(Full detail in `UI_NOTES.md`'s relevant sections; this is the summary.)

- **32-bit devices:** already fully supported, no changes were needed.
  There is no native/NDK code anywhere in this app — no bundled `.so`
  libraries — so a single APK runs unmodified on `armeabi-v7a`/`x86`
  (32-bit) exactly as on 64-bit architectures. Documented directly in
  `app/build.gradle.kts` so this doesn't get re-investigated later.
- **Minimum Android version:** `minSdk = 24` (Android 7.0), which is
  already below both Android 8.0 and Android 9.0 — the requirement was
  already satisfied before this conversation; nothing needed changing.
- **`android:allowBackup="false"`** in the manifest — stops `adb backup`
  and the legacy full-data-backup extraction path outright, on every API
  level.
- **Release build hardening:** `isMinifyEnabled = true` and
  `isShrinkResources = true` for the `release` build type, with
  `app/proguard-rules.pro` keeping the Gson-reflected `logic`/`persistence`
  packages by name (otherwise every saved match would silently fail to
  load back after obfuscation renames the fields Gson looks for).
- **`app/src/main/res/raw/keep.xml`** — stops the resource shrinker from
  deleting `audio_pack.bin`. It is only ever found via a *dynamic*
  `getIdentifier("audio_pack", "raw", ...)` lookup (so the app still works
  correctly before the pack exists), which the shrinker cannot see as a
  "use" of the resource — without this file, a release build would
  compile and run perfectly, with every single sound silently falling
  back to its synthesized stand-in, because the entire audio pack had
  been quietly stripped from the APK. This was caught by reasoning through
  the interaction, not by testing (nothing has been built yet) — it is
  exactly the kind of subtle thing worth re-verifying once a release build
  is actually produced.
- **Audio bundling** — see section 5 in full.

---

## 7. Accessibility principles — carry these into any new session

These apply with zero exceptions, inherited from the web app and enforced
throughout the Android port (full detail and per-screen rationale in
`UI_NOTES.md`'s opening section):

- **Single-swipe, list-based navigation instead of spatial/grid layouts**,
  even where a visual design might suggest a grid — a fielding screen is
  a list of positions, never a diagram; a country/stadium picker is a
  drill-down list, never a map.
- **Prefer Compose's own built-in accessible components**
  (`Modifier.selectable`, `Modifier.toggleable`, standard `Button`/
  `RadioButton`/`Checkbox`) over hand-rolled `Modifier.semantics {}` blocks
  wherever they fit — they're Google's own accessibility-tested
  implementations, less likely to develop the kind of subtle
  cross-screen-reader inconsistency the web app's custom ARIA code had to
  specifically work around.
- **Careful, deliberate semantics** where custom handling is unavoidable —
  `Modifier.semantics`, `Role`, `LiveRegionMode`, explicit
  `contentDescription`, focus order — chosen as carefully as the web app's
  ARIA `role="alert"` vs `role="status"` distinctions were.
- **Custom gesture surfaces need fresh, TalkBack-native design, not
  literal translation.** The web's continuous press-and-drag gestures for
  pitching/batting/fielding have no non-visual equivalent; the Android
  port's approach has been to turn every discrete choice into a
  single-swipe list pick, and to invent genuinely new but appropriately
  accessible mechanics (a haptic-and-audio-pulse timed release, for
  instance) for whatever can't be a list pick without losing all sense of
  skill.
- **Real device/TalkBack testing matters more than code review** for
  anything gesture-related or timing-related — the web app found real
  bugs this way that code review alone missed. **This remains completely
  untested on Android** — every screen, every live region, every custom
  gesture surface has been written to the best of the writer's
  understanding of TalkBack's behaviour, but none of it has been heard by
  an actual screen reader on an actual device. This is the single biggest
  gap between "the code is feature-complete" and "the app is actually
  accessible in practice" — treat every claim in `UI_NOTES.md`'s "Known
  issues / needs a real device" sections as a real, open risk, not a
  formality.

---

## 8. Suggested next steps, in order

1. **Set up the build workflow** (section 3) and get a first successful
   `assembleDebug`. Expect and budget time for a normal first-compile
   debugging pass — fix whatever the committed build report shows, push,
   re-check the report, repeat.
2. **Install the debug APK on a real Android device with TalkBack turned
   on**, and go through `UI_NOTES.md`'s "Known issues / needs a real
   device" checklist screen by screen. This is where the project's
   biggest untested risk lives (see section 7).
3. **Decide on the crowd recording's licence** (section 5) before any
   wider distribution.
4. **Generate the 30 missing commentary clips** on the web app when ready
   (section 5), then re-run the audio-fetch workflow.
5. Once the app is confirmed working end to end: a release build (needs a
   signing keystore — not yet created, and shouldn't be fabricated by an
   assistant; the user should generate and hold this themselves), and
   optionally the stronger audio-key setup (section 5) if repo-level
   secrecy starts to matter.

**Always re-read `PORTING_NOTES.md` and `UI_NOTES.md` live before acting**
— they are the detailed record; this document is the map, not the
territory.
