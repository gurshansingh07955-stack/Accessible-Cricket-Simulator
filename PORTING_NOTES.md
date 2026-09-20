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
  since both `StadiumData.kt` and `MatchEngine.kt` depend on it.
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
  type alias, same pattern as `PitchType.kt`, needed by `WeatherSystem.kt`
  and `MatchEngine.kt`.
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
- **`app/src/main/java/com/cricketsim/logic/FieldingSector.kt`** — small
  standalone enum (12 fielding positions) split out of
  `helpers/fieldingSystem.tsx`'s type alias, since `BattingSystem.kt`
  needed it for `ShotDirection` ahead of the full `FieldingSystem.kt`
  port. Same split-out pattern as `PitchType.kt`/`MatchFormat.kt`.
- **`app/src/main/java/com/cricketsim/logic/BattingSystem.kt`**
  (from `helpers/battingSystem.tsx`) — shot-choice compatibility
  (`computeShotCompatibility`, with the full per-shot excellent/good
  tables and the sweep/reverse-sweep pace-vs-spin branch), footwork
  match (`computeFootworkMatch`), the timing minigame
  (`computeTimingIntervalMs`, `computeTimingTier`, thresholds
  0.16/0.30/0.48/0.68), probability shaping
  (`applyBattingDecisionToProbabilities` + per-dimension `apply*`
  helpers, including the defensive-shot hard cap and step-out nuance),
  the AI batting decision generator (`generateAiBattingDecision` —
  preserves both of the web source's documented rebalance passes
  verbatim in comments, since they explain WHY specific constants sit
  where they do), and shot-direction-for-fielding
  (`getShotDirection`). Reuses `BowlingSystem.kt`'s `DeliveryLength`
  sealed interface directly (via a `CompatibleLength` typealias) rather
  than redefining an equivalent union, since it's exactly the same
  `BowlingLength | MisExecutedLength` union the web source uses. All
  numeric constants verified to match the web source exactly.
- **`app/src/main/java/com/cricketsim/logic/FieldingSystem.kt`**
  (from `helpers/fieldingSystem.tsx`) — position labels/coordinates
  (`getPositionLabel`, `getPositionCoords`), drop-to-spot resolution
  (`resolveDropToSpot` — currently unused by the UI, same as in the web
  source), default outfielder/placement setup (`getFieldingPlayers`,
  `createDefaultFieldPlacements`), the AI field-template system
  (`generateAiFieldPlacements` with the 6 named templates: powerplay /
  attacking / balanced / containing / bouncer_trap / yorker_trap), depth
  toggling (`toggleDepth`), and legality checks (`isFieldLegal`,
  `getIllegalFieldReason`, the 3/5 deep-fielder caps). All numeric
  constants (sector angles, depth radii, the 0.3 attacking/containing
  situational-bias thresholds, the 3/5 legality caps) verified to match
  the web source exactly.
- **`app/src/main/java/com/cricketsim/logic/DismissalType.kt`**,
  **`TossDecision.kt`** — small standalone enums split out of inline
  union types in `helpers/matchState.tsx`.
- **`app/src/main/java/com/cricketsim/logic/BallOutcome.kt`** — full
  port of `helpers/matchState.tsx`'s `BallOutcome` interface.
- **`app/src/main/java/com/cricketsim/logic/MatchEngine.kt`**
  (from `helpers/matchEngine.tsx`) — `simulateToss`, `getOutcomeProbabilities`
  (base rating/pitch-driven probabilities), `applyFieldPlacementToProbabilities`
  (the field-placement tactical layer — aerial shots into a deep
  fielder becoming catches, grounded shots getting cut off by a short/
  close fielder, "finding the gap" when no fielder covers the shot),
  `simulateBall` (illegal-field and beamer no-ball short-circuits,
  difficulty modifier, stadium effects, bowling+batting decision
  layering, outcome sampling, dismissal-type resolution),
  `generateCommentary` (every phrase pool, keyed off the same
  dismissalType/isEdge fields the AI voice commentary will eventually
  use), and `calculateWinProbability`. Reuses `WeatherSystem.kt`'s
  `StadiumMatchEffects` directly instead of redefining an identical
  `StadiumEffects` type, since Kotlin has no circular-import constraint
  forcing the web source's duplication (documented in the file header).
  All numeric constants (base probabilities, pitch/difficulty
  modifiers, field-placement multipliers, win-probability RRR
  thresholds) verified to match the web source exactly.
- **`app/src/main/java/com/cricketsim/logic/MatchStats.kt`**
  (from `helpers/matchStats.tsx` — discovered mid-session as an
  undocumented dependency of `matchState.tsx`, see git history) —
  `BatsmanStats`/`BowlerStats`/`Partnership`/`InningsData`, strike-rate/
  economy calculators, and every innings-data update helper
  (`updateBatsmanStats`, `updateBowlerStats`, `recordDismissal`,
  `updatePartnership`, `endPartnership`, `addBowlerIfNotExists`,
  `getLastSixBalls`, `updateInningsDataForBall`). Uses `DismissalType`
  instead of a bare `String` for `BatsmanStats.dismissalType` (a
  documented, behavior-preserving type tightening — every real call
  site already passes that domain).

  **⚠️ Two DELIBERATE ADDITIONS BEYOND THE WEB SOURCE** (added when
  the Android scorecard was built; documented in the file's own
  header). The web source has two gaps its own scorecard visibly works
  around — a hardcoded `false` for maiden detection, and a Fall of
  Wickets table that shows the batsman's own runs and numbers wickets
  by batting order because the team score at each fall was never
  recorded. Both are fixed here, additively — nothing the web source
  computes has changed, so every figure it produced is still produced
  identically:
  - **Maidens:** new `InningsData.currentOverRuns` tallies what the
    over in progress has cost (runs plus wides/no-balls, which count
    against the bowler). `updateInningsDataForBall` credits a maiden
    when a legal delivery completes the over with that tally still at
    zero, and resets the tally on every completed over. Only wides and
    no-balls exist as extras in this game, so no bye/leg-bye handling is
    needed, and bowler changes only happen at over boundaries, so one
    tally per innings suffices.
  - **Fall of wickets:** new `FallOfWicket` data class and
    `InningsData.fallOfWickets`, appended by `recordDismissal` with the
    wicket number (in the order wickets actually fell), the batsman's
    runs and balls, the TEAM score and the overs. `recordDismissal`
    runs after `updateInningsDataForBall` has already added the
    dismissal ball to the totals, which is the order
    `MatchStateMachine.applyBallOutcome` then `recordWicketFall`
    already calls them in, so the figures are the score immediately
    after the dismissal ball.
  Both new `InningsData` fields have defaults, so nothing constructing
  an `InningsData` had to change. If the web source is ever re-synced
  wholesale, re-apply these two additions rather than reverting them.
- **`app/src/main/java/com/cricketsim/logic/MatchState.kt`**
  (from `helpers/matchState.tsx`) — **the FULL match state machine**,
  replacing the earlier partial version. `createNewMatch`,
  `rotateStrike`, `getMaxOversPerBowler`, `getAvailableBatsmen`,
  `getEligibleBowlers`, `setOpeners`, `selectBowler` (with
  field-placement carry-over across a bowler change),
  `changeBowler` (AI bowler-rotation scoring), `recordWicketFall`,
  `bringInNewBatsman`, `applyBallOutcome`, `switchInnings` (DLS target
  revision), `setFieldPlacements`, `applyRainInterruption`,
  `resumeFromRainDelay`. All state-transition logic (rain-interruption
  overs math, DLS revision, field-placement carry-over, bowler-rotation
  scoring) verified to match the web source exactly.

  **Deliberately NOT ported** (documented in the file header —
  see "Not ported" section below too): `saveMatch` / `loadMatch` /
  `clearMatch` / `hasActiveMatch` and the old-save backfill logic in
  `loadMatch`, since these are inherently backed by browser
  `localStorage`. Android persistence (`DataStore` or `Room`) is a real
  platform layer to design, not a translation — a future session needs
  to wire loading/saving around the pure state-transition functions
  that ARE here. `nanoid()` (for `MatchState.id`) is substituted with
  `java.util.UUID`, the same pattern `CricketData.kt` already uses for
  player ids.

- **`app/src/main/java/com/cricketsim/logic/CommentaryLibrary.kt`**
  (from `helpers/commentaryLibrary.tsx`) — the full AI voice duo
  commentary library: every category (per-ball outcomes split by
  clean/edge and dismissal type, milestones, back-to-back/triple
  boundaries, tight/expensive overs, partnership milestones, bowler
  wicket hauls, hat-tricks, toss, fielding change, rain, DLS revision),
  every line's id/text/audioUrl, and the categorization/milestone
  helpers (`categorizeBallOutcome`, `getMilestoneCrossed`,
  `getPartnershipMilestoneCrossed`, `getBowlerWicketMilestoneCrossed`,
  `getHatTrickCategory`, `getRandomCommentaryPair`,
  `getAllCommentaryLines`). `categorizeBallOutcome` takes `BallOutcome`
  directly rather than a loose structural type, since its fields are an
  exact match. `audioUrl` values are carried over verbatim for parity
  even though they won't resolve without bundling equivalent audio
  assets on Android (see "Audio" below).

  **🎉 This completes the entire pure game-logic layer** — data,
  weather, bowling, batting, fielding, match engine, match state,
  stats, and commentary are all fully ported and verified against the
  web source. Everything remaining (see "What's next" below) is UI,
  audio, and persistence — genuine platform-specific design work, not
  mechanical translation.

- Android/Gradle project skeleton (Kotlin + Jetpack Compose), with a
  placeholder `MainActivity` that just proves the logic layer loads
  correctly (shows team/player counts) — no real gameplay UI yet.

## What's next

The pure logic layer (everything under `logic/`) is done. The
remaining work is building the actual app on top of it — none of it is
a line-by-line port, since none of it has a Kotlin/Compose equivalent
to translate from directly:

1. **UI** — design and build the Jetpack Compose screens (setup/toss
   flow, the match screen, the three accessible gesture surfaces for
   pitching/batting/fielding, scorecard, playing-XI selection, etc.),
   from scratch, around native TalkBack semantics. This is the single
   largest remaining piece of work. See "Not ported" below for why the
   web app's ARIA-based screens can't be copied. **Progress is tracked
   in `UI_NOTES.md`**, not here.
2. **Persistence** — design an Android save/resume layer (`DataStore`
   or `Room`) and wire it around `MatchStateMachine`'s existing pure
   state-transition functions (`createNewMatch`, `applyBallOutcome`,
   etc.), replacing the omitted `saveMatch`/`loadMatch`/`clearMatch`/
   `hasActiveMatch`.
3. **Audio** — design the `SoundPool` + `MediaPlayer`/`ExoPlayer`
   mixing/ducking layer and either bundle or fetch real audio assets
   for `CommentaryLibrary.kt`'s lines (the current `audioUrl` values
   point at the web app's CDN and won't resolve as-is).
4. **The actual APK build** — hasn't been attempted at all yet: signing,
   testing on a device/emulator, and everything between "code compiles"
   and "installable app".

A future session tackling any of these should start by re-reading the
relevant "Not ported, and NOT a mechanical translation" entry below —
each one records specific reasons the naive translation won't work.

## Not ported, and NOT a mechanical translation when it happens

- **UI** (`pages/*.tsx`, `components/*.tsx`) — the web app's screens use
  custom drag-gesture surfaces (`PitcherScreen`, `BattingShotScreen`,
  `FieldingScreen`) built specifically around ARIA live regions and
  linear swipe navigation for screen readers. There's no 1:1 Compose
  equivalent; this needs to be designed fresh for Android using Compose
  + native TalkBack semantics (`Modifier.semantics`,
  `LiveRegionMode`, etc.), following the same accessibility-first
  PRINCIPLES as the web app without copying its DOM-specific mechanics.
  Also note that match-loop logic living in `pages/match.tsx` rather than
  `helpers/` (situational-bias formulas, the AI's `selectAiNextBatsman`,
  the over-end/wicket/rain orchestration) is part of the UI work, not the
  logic port — see `UI_NOTES.md` and `MatchSimulation.kt`.
- **Audio** (`helpers/audioManager.tsx`) — built on the Web Audio API
  (`AudioContext`, `GainNode`, buffer scheduling). The Android
  equivalent is `SoundPool` (short one-shots) + `MediaPlayer` or
  `ExoPlayer` (looping ambience beds) — different APIs, different
  mental model, needs a real rewrite of the mixing/ducking logic, not a
  line-by-line port.
- **Persistence** (`helpers/matchState.tsx`'s `saveMatch`/`loadMatch`,
  currently backed by browser `localStorage`) — Android equivalent is
  likely `DataStore` or a small `Room` database once this is tackled.
  See the `MatchState.kt` entry above for exactly what was skipped.

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
