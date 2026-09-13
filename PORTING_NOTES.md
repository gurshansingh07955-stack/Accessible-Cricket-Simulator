# Porting Notes

Tracks progress porting the Floot/React web app (source of truth for
gameplay design) to native Kotlin. This is a **from-scratch rewrite**,
not a mechanical transpile — see the note at the bottom on why the UI,
audio, and accessibility layers can't just be "translated".

## Done

- **`app/src/main/java/com/cricketsim/logic/CricketData.kt`**
  (from `helpers/cricketData.tsx`)
  - `Player`, `Team` data classes
  - All 18 national squads, faithfully ported player-for-player and
    rating-for-rating (447 players total: 300 across the 10 full-member
    nations at 30 each, 147 across the 8 associate nations at 16-17
    each)
  - Fielding-rating derivation formula + the hand-picked overrides for
    players famous specifically for their fielding (Kohli, Jadeja,
    Glenn Phillips, Cameron Green, David Warner, Andre Russell)
  - `buildMatchSquad()` and `autoSelectPlayingXI()` (playing-XI /
    captain / vice-captain / wicketkeeper selection logic)
- Android/Gradle project skeleton (Kotlin + Jetpack Compose), with a
  placeholder `MainActivity` that just proves the logic layer loads
  correctly (shows team/player counts) — no real gameplay UI yet.

## Not started yet (logic layer)

In rough priority order for the next session:

1. `helpers/stadiumData.tsx` → `StadiumData.kt` — 101 real venues,
   mostly data + a couple of filter helpers. Similar shape/size to
   CricketData.kt.
2. `helpers/weatherSystem.tsx` → `WeatherSystem.kt` — weather
   generation, rain-interruption gating, the simplified DLS
   resource-percentage curve. Small, self-contained, well-tested on the
   web side — good next target.
3. `helpers/bowlingSystem.tsx` → `BowlingSystem.kt` — line/length/
   angle/variation system, AI bowling decisions.
4. `helpers/battingSystem.tsx` → `BattingSystem.kt` — shot/intent/
   timing system, AI batting decisions.
5. `helpers/fieldingSystem.tsx` → `FieldingSystem.kt` — field
   placement templates, AI field-setting logic.
6. `helpers/matchEngine.tsx` → `MatchEngine.kt` — the actual
   ball-by-ball outcome simulation; depends on everything above.
7. `helpers/matchState.tsx` → `MatchState.kt` — the match state
   machine (innings transitions, rain interruptions, save/resume).
8. `helpers/commentaryLibrary.tsx` → `CommentaryLibrary.kt` — mostly
   data (text + audio URLs), low risk, can be ported any time.

## Not ported, and NOT a mechanical translation when it happens

- **UI** (`pages/*.tsx`, `components/*.tsx`) — the web app's screens use
  custom drag-gesture surfaces (`PitcherScreen`, `BattingShotScreen`,
  `FieldingScreen`) built specifically around ARIA live regions and
  linear swipe navigation for screen readers. There's no 1:1 Compose
  equivalent; this needs to be designed fresh for Android using Compose
  + native TalkBack semantics (`Modifier.semantics`,
  `LiveRegionMode`, etc.), following the same accessibility-first
  PRINCIPLES as the web app without copying its DOM-specific mechanics.
- **Audio** (`helpers/audioManager.tsx`) — built on the Web Audio API
  (`AudioContext`, `GainNode`, buffer scheduling). The Android
  equivalent is `SoundPool` (short one-shots) + `MediaPlayer` or
  `ExoPlayer` (looping ambience beds) — different APIs, different
  mental model, needs a real rewrite of the mixing/ducking logic, not a
  line-by-line port.
- **Persistence** (`helpers/matchState.tsx`'s `saveMatch`/`loadMatch`,
  currently backed by browser `localStorage`) — Android equivalent is
  likely `DataStore` or a small `Room` database once this is tackled.

## Verifying a ported file

Each ported `.kt` file should be checked against its source `.tsx` for:
- every exported function/constant has a Kotlin equivalent
- every numeric constant matches exactly (ratings, thresholds, formula
  coefficients) — these encode real game-balance decisions from the web
  app's session history, not arbitrary choices
- doc comments explaining *why* a specific number or override exists
  are preserved, not just the code
