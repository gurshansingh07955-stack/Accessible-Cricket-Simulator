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
  device. **Feature-complete; compiles and builds successfully; a first
  real-device TalkBack pass is underway (see section 7).**

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

**Build status: compiles cleanly and produces a working debug APK.** The
build workflow (section 3) is set up and green; every push that touches
`app/**` or `build.gradle.kts` produces a fresh, installable APK
artifact. The normal first-compile debugging pass this document used to
warn about (section 3) has already happened and is done.

**Where the real work is now:** a first real on-device TalkBack pass has
happened (installed via the build workflow's APK artifact) and found
genuine issues, some already fixed, some gesture work still in progress
— see section 7 for the full current picture. The single biggest
remaining gap is still what section 7 (accessibility principles) has
always said it would be: nothing was accessible-in-practice until an
actual screen reader on an actual device confirmed it, and that process
is now underway rather than still 100% ahead of us.

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
used repeatedly in this project):** GitHub's own "create new file" web
page accepts the target file name and its entire contents as URL query
parameters, so a single link can arrive with everything already typed in
— the user only has to click **Commit changes** twice (once to open the
commit dialog, once to confirm). This works because the *user's own
browser session* is what creates the file, not the API token.

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

**IMPORTANT — this `/new/...` prefilled-link trick only works for
creating a file that doesn't exist yet.** GitHub's `/edit/...` page for an
EXISTING file does NOT honor a `value=` query parameter the same way —
it silently ignores it and just shows the file's real current content,
which looks like the link "didn't work" but is actually a completely
different page not designed for this. Editing an existing workflow file
this way, twice, produced two bad commits (whitespace-only content typed
by hand while looking for a "dirty flag" workaround) before this was
understood. **To change an EXISTING file under `.github/workflows/`
without the write permission from option A: delete it, then recreate it
with the `/new/...` link above.** Two links, two confirmations:
```
https://github.com/gurshansingh07955-stack/Accessible-Cricket-Simulator/delete/main/.github/workflows/<name>.yml
```
(click **Commit changes**, no typing) — confirm the delete landed (a
`get_file_contents` 404, or the GitHub API run above) — THEN send the
`/new/...` link with the corrected content. This has been used
successfully.

**Once a workflow file already exists** (like `fetch-audio.yml` does
now), pushing changes to files it lists under `on: push: paths:` — even
files OUTSIDE `.github/workflows/`, like `tools/fetch_audio.sh` — *does*
auto-trigger it, and that kind of push is NOT blocked by the 403 (only
writes inside `.github/workflows/` itself are). This was used deliberately
when the audio system was reworked: rewriting `tools/fetch_audio.sh` and
pushing it re-ran the existing `fetch-audio.yml` with zero workflow edits.
**Prefer this over touching a workflow file at all, whenever the trigger
file is something you can already write to.** This is exactly how the
debug keystore fix in section 3.1 below was iterated on (editing
`app/build.gradle.kts`, which is NOT under `.github/workflows/` and so
pushes normally) without touching the workflow at all.

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
successful, and how every `assembleDebug` run since has been confirmed
too — **the workflow now exists, is green, and produces a real APK
artifact on every push that touches `app/**` or `build.gradle.kts`** (see
section 3.1). Always re-check the LATEST run rather than assuming a fix
that "should" work actually built successfully.

### 3.1 — The debug keystore: every build must share ONE signing key

**Symptom this fixes:** installing a freshly-built APK over a
previously-installed one fails with a plain **"App not installed"**
dialog, with no other explanation shown.

**Cause:** Android refuses to install an APK over an existing one with the
same package name unless both are signed with the SAME key. Left
unconfigured, Android Gradle Plugin's debug build type auto-generates a
`debug.keystore` **per machine** — since every GitHub Actions run is a
fresh, disposable VM, every CI-built APK before this fix was signed with
a genuinely different, one-off key, so installing build N+1 over build N
always failed unless the device first uninstalled build N.

**Fix, already in place:**
- `tools/debug-keystore.b64` — a fixed debug keystore, checked into the
  repo as **base64 text** (deliberately not as a raw binary `.jks`/
  `.keystore` file — GitHub's file-contents API round-trips plain text
  reliably; a binary file pushed the same way risks silent corruption
  that would only surface later, at signing time). Uses the standard,
  well-known Android debug credentials (`androiddebugkey` / `android` /
  `android`) — this key has no real secrecy value, same as any debug
  keystore.
- `.github/workflows/build-apk.yml` decodes it before the Gradle build:
  `base64 -d tools/debug-keystore.b64 > app/debug.keystore`.
- `app/build.gradle.kts` has a `signingConfigs { getByName("debug") { ... } }`
  block pointing at `app/debug.keystore` with those same credentials,
  overriding AGP's implicit per-machine default.

**Result:** every build, CI or local, signs with the same key, so a new
APK installs directly over an old one — no more uninstall-then-reinstall
step, and (as a side effect) the app's save data now survives an update
instead of being wiped by a forced uninstall.

**If this ever needs regenerating** (e.g. rotating the key for some
reason): `keytool -genkeypair -keystore debug.keystore -storepass android
-alias androiddebugkey -keypass android -keyalg RSA -keysize 2048
-validity 10950 -dname "CN=Android Debug,O=Android,C=US"`, then
`base64 -w0 debug.keystore` and commit that string as the new
`tools/debug-keystore.b64` content. Everyone who has an APK from the old
key will need to uninstall once when the key changes — same tradeoff as
any signing-key rotation.

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

## 7. TalkBack testing so far, and the full gesture redesign status

A first real on-device TalkBack pass has happened, using an APK pulled
from the build workflow's artifact (section 3). It found real bugs; this
section is the complete, current record of what was found and what's
been done about each, plus the full state of the gesture-input redesign
that's now in progress. **Read `GESTURE_REDESIGN.md` for the original
plan in full** — this section is the living summary of what's actually
true today; that document is the historical plan text with a status
banner pointing back here.

### 7.1 — Fixed: system back button closed the whole app

**Symptom:** pressing the system back button, on ANY screen, anywhere in
the app, including mid-match, closed the entire app instead of
navigating anywhere.

**Cause:** there was no `BackHandler` anywhere in the codebase at all —
every screen's "back" affordance only ever existed as an on-screen
button, wired to that screen's own `onBack` lambda; the system back
button had nothing intercepting it, so it always fell through to
Android's default behavior (finish the Activity).

**Fix:** every screen and sub-view now has a `BackHandler` that does
EXACTLY what that screen's own on-screen back/leave button already does
— never a different behavior. `MainActivity.kt`'s `CricketSimApp` wires
one per top-level screen (Home has none on purpose: the app's own root
screen is the one place "close the app" is the correct default).
`MatchScreen.kt` wires one per sub-view (pitching, batting, fielding, the
scorecard, the settings overlay, the leave-confirmation dialog) PLUS one
on the ordinary match screen itself, which opens the leave confirmation
— exactly like tapping the Leave match button — rather than leaving a
live match on an unconfirmed back press.

### 7.2 — Fixed: TalkBack reading/traversal order on the match screen

**Symptom:** swiping through the match screen with TalkBack visited
elements in an order that didn't match how a player actually needs to
use the screen — Leave match/Settings/Scorecard were at the very bottom
(reachable only after swiping through the entire live match state every
single time), and the striker/non-striker/bowler lines and the main
action button weren't grouped the way real usage needs.

**Fix — the reading order is now, confirmed correct by the user:**

**While BOWLING:** heading → Leave match → Settings → Scorecard → Set
field → striker line → non-striker line → bowler line → Bowl → (live
status messages, last ball, score, run rates, earlier-innings list).

**While BATTING:** heading → Leave match → Settings → Scorecard → Face
next ball → striker line → non-striker line → bowler line → Hear the
field → (the same trailing status/score/history block).

The reasoning, straight from how a real player actually uses each role:
Leave match/Settings/Scorecard are navigation, so they come first,
always reachable without swiping through match state. Set field comes
BEFORE Bowl on the bowling side because setting the field is a
pre-delivery decision; Hear the field comes AFTER Face next ball on the
batting side because it's read-only supplementary information,
secondary to the actual action. Because Compose's TalkBack traversal
order follows visual/composition order, this required physically
reordering the Column in `MatchScreen.kt`, not just relabeling anything
— see that file's own doc comment for the exact current layout.

### 7.3 — Fixed: "App not installed" when updating the APK

Covered in full in section 3.1 — a fresh, disposable signing key per CI
machine meant every build was signed differently. Now fixed with a
committed, stable debug keystore; installs update in place from here on.

### 7.4 — Gesture redesign: two-finger swipe vocabulary for bowling

**Why this exists at all:** the ORIGINAL pitching/batting gesture design
(a five-step wizard of single-finger list-picks, one per axis) worked
but felt nothing like the web app's real continuous drag-based feel, and
reduced bowling to a fully deterministic settings picker with no skill
at the moment of delivery. `GESTURE_REDESIGN.md` is the plan that came
out of that discussion: replace the discrete per-axis list-picks with a
two-finger swipe vocabulary, closer to the web's continuous drag, while
staying TalkBack-safe (TalkBack intercepts single-finger touch for its
own explore/double-tap model, but a second finger changes what Android
delivers to the app — see 7.4.1).

**Current status: bowling is done, with three deviations from the
written plan, all discovered through the same real-device TalkBack
pass:**

**Angle/Line/Variation/Length now live on ONE screen** (`PitchingScreen.kt`'s
`AimStep`), each assigned its own compass direction, since a real bowler
sets all four before ever releasing the ball:
- Two-finger swipe **left** cycles Angle forward through its list
  (wrapping), re-firing repeatedly while the swipe continues — like a
  volume rocker, not a one-shot gesture.
- Two-finger swipe **right** cycles Line the same way.
- Two-finger swipe **up** cycles Variation (this bowler's pace-or-spin
  list) the same way.
- Two-finger swipe **down** is the one CONTINUOUS gesture: once
  recognized as the dominant direction, it locks into a drag that maps
  live vertical position to a length band, with a continuously
  pitch-shifted TONE tracking position (`SoundEngine.startAimTone` /
  `updateAimTone` — a short looping sine whose playback rate is swept
  live, reusing the exact `AudioTrack.setPlaybackRate` mechanism the
  crowd bed's tension creep already used) and a SPOKEN announcement only
  when the drag crosses into a new length band — never a continuous
  spoken readout, which would overrun TalkBack's speech queue. Lifting
  the fingers commits whichever band the drag last landed in.

**Deviation 1 — the gesture detector tracks ONE active pointer, not two
simultaneous ones.** The very first implementation waited for two
pointers to be pressed at once before tracking anything, which is
exactly how a genuine two-finger touch looks WITHOUT a screen reader
running. With TalkBack's touch exploration active, that condition never
arrives, and the result was "I swiped with two fingers and nothing
happened" — a real bug the on-device pass caught immediately. **The
actual behavior:** Android's touch exploration controller only enters
"passthrough" mode once a second finger touches down, and passthrough
DROPS the first finger and forwards only the additional finger(s)'
movement, rewritten as an ordinary single-pointer stream — a real
two-finger swipe is delivered to the app looking like a ONE-finger drag.
`detectAimGesture` in `PitchingScreen.kt` now tracks whichever single
pointer is currently active rather than requiring two at once, which is
correct in both cases: under TalkBack, that lone pointer already IS the
translated second finger (the user still physically swipes with two
fingers; only the plumbing changes what the app receives), and without
TalkBack running, a single real finger works identically — a reasonable
fallback for sighted testing, not a design compromise. This never
conflicts with TalkBack's own single-finger touch exploration (speaking
whatever's under the finger), because that only happens with ONE finger
down; the app's handler only ever receives events once real passthrough
(a genuine second finger) has begun. **This is the single most important
fact for whoever builds batting's or fielding's gestures next** — plan
every gesture detector around a single translated pointer stream from
the start, not two simultaneous ones.

**Deviation 2 — Speed is a real Compose `Slider`, not a three-tier list
pick.** This is what "make it a slider like the web version" actually
meant, and `Slider` has first-class TalkBack support out of the box (it
announces as adjustable; swipe up/down or the standard increment/
decrement actions adjust it) — no custom gesture code needed for this
one, unlike the discrete axes above.

**Deviation 3 — the timing/release mechanic is gone entirely, replaced
by computing quality the same way the web app does.** The original
pitching screen (before ANY of this redesign) used a haptic-pulse timed
release as a screen-reader-friendly INVENTED substitute for the web's
own mechanic — the web has no timing step at all; it computes bowling
quality directly from how precisely and smoothly the player's drag
itself lands (LENGTH PRECISION: distance from the drag's release point
to the intended zone's center; SMOOTHNESS PENALTY: whether the drag
overshot past its own final resting point and had to be pulled back —
both already implemented, unchanged, in
`BowlingSystem.computeBowlingQuality`). Once length became a real
two-finger drag instead of a discrete pick, that same web mechanic had a
genuine non-visual equivalent again: the length drag now simultaneously
expresses BOTH the intended length (via `classifyLength` on where it
lands) AND its own execution quality (via how centered on the band, and
how smooth, the drag was), with no separate timing step — exactly
matching the web. `PitchingScreen.kt` now tracks the drag's live
`verticalFraction` and `maxVerticalFractionReached` (for overshoot
detection) throughout the length drag, and feeds both straight into
`computeBowlingQuality` once speed is chosen; there is no Release step
left in the pitching flow at all. This was `GESTURE_REDESIGN.md` section
2.1's deliberately-left-open fork ("repurpose the timing tap" vs. "drop
it entirely") — resolved as "drop it."

**Not yet reconciled:** `GESTURE_REDESIGN.md` section 3 (batting) still
describes a two-finger shot-selection drag PLUS a separate timing/
release tap kept as-is. Given bowling's timing mechanic was dropped
rather than fixed, whether batting's timing tap should go the same way
is an open question for whoever picks up batting — batting doesn't have
an equivalent "the drag itself already encodes quality" opportunity the
way bowling's length axis did, since shot selection and footwork/intent
aren't a single continuous quality signal, so this may not resolve the
same way. Decide this deliberately rather than copying bowling's
resolution by default.

### 7.4.1 — Not yet started: batting, fielding, and the bundled fixes

Per `GESTURE_REDESIGN.md`'s own suggested order:
- **Batting's gesture redesign** (footwork tap/hold, shot-selection
  drag, four-way intent swipe) — not started. Build its gesture detector
  around the single-pointer-under-passthrough model from the start (see
  7.4's Deviation 1) rather than rediscovering it the same way bowling
  did.
- **Widening the Perfect timing window** — moot for bowling (no timing
  step left); still open for batting's existing timing tap, and depends
  on the "not yet reconciled" question above being settled first.
- **Crowd ambience starting at the toss screen** instead of later — small,
  independent, not started.
- **Vibration not firing/lagging on some devices** (reported: Motorola
  Edge 70 Fusion) — needs investigation on a real device, not started.
- **Fielding's redesign** (Android's Accessibility Drag-and-Drop
  framework) — separate, larger effort, not started; the existing
  two-phase list-pick plan for it is unchanged.

---

## 8. Accessibility principles — carry these into any new session

These apply with zero exceptions, inherited from the web app and enforced
throughout the Android port (full detail and per-screen rationale in
`UI_NOTES.md`'s opening section):

- **Single-swipe, list-based navigation instead of spatial/grid layouts**,
  even where a visual design might suggest a grid — a fielding screen is
  a list of positions, never a diagram; a country/stadium picker is a
  drill-down list, never a map.
- **Prefer Compose's own built-in accessible components**
  (`Modifier.selectable`, `Modifier.toggleable`, standard `Button`/
  `RadioButton`/`Checkbox`/`Slider`) over hand-rolled `Modifier.semantics {}`
  blocks wherever they fit — they're Google's own accessibility-tested
  implementations, less likely to develop the kind of subtle
  cross-screen-reader inconsistency the web app's custom ARIA code had to
  specifically work around. (Bowling's speed control is a real `Slider`
  for exactly this reason — see section 7.4.)
- **Careful, deliberate semantics** where custom handling is unavoidable —
  `Modifier.semantics`, `Role`, `LiveRegionMode`, explicit
  `contentDescription`, focus order — chosen as carefully as the web app's
  ARIA `role="alert"` vs `role="status"` distinctions were.
- **Custom gesture surfaces need fresh, TalkBack-native design, not
  literal translation — and "two-finger gestures pass through untouched"
  needs a specific, correct mental model, not just the general idea.**
  The web's continuous press-and-drag gestures for pitching/batting/
  fielding have no non-visual equivalent, so the Android port invents
  genuinely new but appropriately accessible mechanics instead of a
  literal translation (bowling's two-finger swipe vocabulary — see
  section 7.4 — is the current example; a haptic-and-audio-pulse timed
  release used to be another, before it was replaced). The specific,
  load-bearing correction from real testing: TalkBack's touch exploration
  does NOT deliver two simultaneously-pressed pointers to the app. A
  second finger triggers "passthrough," which drops the first finger and
  forwards the rest as an ordinary translated SINGLE-pointer stream. Any
  new gesture detector should be built around tracking one active pointer
  from the start — see section 7.4's "Deviation 1" for the full
  explanation and why it also works correctly without a screen reader
  running.
- **Real device/TalkBack testing matters more than code review** for
  anything gesture-related or timing-related — the web app found real
  bugs this way that code review alone missed, and so has this port: see
  section 7 for the first real-device pass's findings, all fixed or in
  progress. **Keep testing screen by screen** — section 7's fixes were
  found in exactly the areas (navigation, gesture input) code review
  alone did not catch. Treat every remaining claim in `UI_NOTES.md`'s
  "Known issues / needs a real device" sections as a real, open risk
  until it's specifically been checked on-device, the same way section
  7's issues were.

---

## 9. Suggested next steps, in order

1. **Continue the real-device TalkBack pass**, screen by screen, per
   `UI_NOTES.md`'s "Known issues / needs a real device" checklist — this
   is now genuinely underway (section 7), not still 100% ahead of us, but
   far from finished. Report findings the same way section 7's were
   captured: symptom, cause, fix, with enough detail that a fresh session
   doesn't need to rediscover any of it.
2. **Batting's gesture redesign** (section 7.4.1) — build its gesture
   detector around the single-active-pointer model from the start (see
   section 7.4's "Deviation 1"), and deliberately decide whether its
   timing tap should be dropped the same way bowling's was, rather than
   assuming the same resolution applies.
3. **Decide on the crowd recording's licence** (section 5) before any
   wider distribution.
4. **Generate the 30 missing commentary clips** on the web app when ready
   (section 5), then re-run the audio-fetch workflow.
5. **The remaining items in `GESTURE_REDESIGN.md`'s suggested order**:
   fielding's Accessibility Drag-and-Drop redesign, the crowd-ambience-
   at-toss trigger change, and the Motorola vibration investigation — all
   independent of each other and of the batting work above.
6. Once the app is confirmed working end to end: a release build (needs
   a signing keystore — the debug keystore in section 3.1 is explicitly
   NOT for this; a release keystore should be generated and held by the
   user themselves, not fabricated by an assistant), and optionally the
   stronger audio-key setup (section 5) if repo-level secrecy starts to
   matter.

**Always re-read `PORTING_NOTES.md`, `UI_NOTES.md`, and
`GESTURE_REDESIGN.md` live before acting** — they are the detailed
record; this document is the map, not the territory.
