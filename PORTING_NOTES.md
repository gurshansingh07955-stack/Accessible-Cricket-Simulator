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
- **`app/src/main/java/com/cricketsim/logic/PitchType.kt`** — small
  standalone enum split out of `helpers/matchEngine.tsx`'s type alias,
  since both `StadiumData.kt` and the eventual `MatchEngine.kt` depend
  on it.
- **`app/src/main/java/com/cricketsim/logic/StadiumData.kt`**
  (from `helpers/stadiumData.tsx`) — all 101 stadiums, every numeric
  field (rain probability, humidity, temperature, wind, altitude, dew
  factor, avg T20 score, pitch type, boundary size) verified to exactly
  match the web source. One deliberate scope cut: flavor-text
  descriptions were regenerated from pitch type/climate rather than
  copied verbatim from the original, to keep this pass tractable. If
  exact original wording is ever needed, re-derive it from the Floot
  source.
- **`app/src/main/java/com/cricketsim/logic/MatchFormat.kt`** — small
  standalone enum (TEST/T20/ODI) split out of `helpers/matchEngine.tsx`'s
  type alias, same pattern as `PitchType.kt`, needed by
  `WeatherSystem.kt` and the eventual `MatchEngine.kt`/`MatchState.kt`.
- **`app/src/main/java/com/cricketsim/logic/WeatherSystem.kt`**
  (from `helpers/weatherSystem.tsx`) — weather generation
  (`generateWeatherForStadium`, `weatherConditionLabel`), stadium/weather
  match-effect modifiers (`getStadiumMatchEffects`), rain-interruption
  gating (`shouldTriggerRainInterruption`, `pickOversLost`), and the
  simplified DLS resource-percentage curve (`resourcePercent`,
  `computeInningsResourcePercent`, `computeDlsTarget`). All numeric
  constants (jitter spreads, condition thresholds, the Z0 table, the
  0.07 decay rate, the 160/245 G50 values) verified to match the web
  source exactly. Overs counts are modeled as `Int`, matching
  `MatchState.oversLimit: number` / `MatchScore.overs: number` in the
  web source, which both track whole completed overs.
- **`app/src/main/java/com/cricketsim/logic/BowlingSystem.kt`**
  (from `helpers/bowlingSystem.tsx`) — line/length/angle/variation/speed
  system: quality scoring (`computeLengthPrecision`,
  `computeSmoothnessPenalty`, `computeSpeedRiskPenalty`,
  `computeBowlingQuality`, tier thresholds 85/70/50/30), mis-execution
  resolution (`resolveActualLength`), the AI bowling decision generator
  (`generateAiBowlingDecision`), and probability shaping
  (`applyBowlingDecisionToProbabilities` + per-dimension `apply*`
  helpers). Two modeling decisions worth knowing about before touching
  this file again (both documented in the file's own header comment):
  - `BowlingVariation` is ONE flattened enum covering both the web
    source's `PaceVariation` and `SpinVariation` unions (they share the
    "stock" value, so it appears once, not twice).
  - `BowlingLength` and `MisExecutedLength` stay as two SEPARATE enums
    behind a shared `DeliveryLength` sealed interface, preserving the
    web source's real type distinction (`intendedLength` is always a
    real, selectable `BowlingLength`; a `MisExecutedLength` can only
    ever come out of `resolveActualLength` as a RESULT).
  All numeric constants (speed ranges, quality-tier thresholds, penalty
  caps, probability multipliers, mis-execution chances) verified to
  match the web source exactly.
- Android/Gradle project skeleton (Kotlin + Jetpack Compose), with a
  placeholder `MainActivity` that just proves the logic layer loads
  correctly (shows team/player counts) — no real gameplay UI yet.

## Not started yet (logic layer)

In rough priority order for the next session:

1. `helpers/battingSystem.tsx` → `BattingSystem.kt` — shot/intent/
   timing system, AI batting decisions. Natural next target: it's the
   direct counterpart to `BowlingSystem.kt` (just ported) and the two
   are combined every ball in the eventual `MatchEngine.kt`.
2. `helpers/fieldingSystem.tsx` → `FieldingSystem.kt` — field
   placement templates, AI field-setting logic.
3. `helpers/matchEngine.tsx` → `MatchEngine.kt` — the actual
   ball-by-ball outcome simulation; depends on everything above.
4. `helpers/matchState.tsx` → `MatchState.kt` — the match state
   machine (innings transitions, rain interruptions, save/resume).
5. `helpers/commentaryLibrary.tsx` → `CommentaryLibrary.kt` — mostly
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

**IMPORTANT — keep this file in sync:** it went stale once already
(missing `StadiumData.kt` after it was ported). After finishing a file,
update this file in the SAME session/commit rather than leaving it for
later.
