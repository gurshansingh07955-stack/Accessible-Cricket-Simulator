# UI Notes

Tracks progress building the actual Android UI on top of the fully-
ported logic layer (see PORTING_NOTES.md). This is **fresh design
work, not a port** — the web app's screens are built around ARIA live
regions and DOM-specific gesture handling that has no Compose
equivalent. The web app (Floot project) remains the reference for
*what* each screen needs to do and *why* (its accessibility
principles), never for literal code to translate.

## Accessibility principles carried over from the web app (non-negotiable)

Per the original project handoff, these apply with zero exceptions:
- Single-swipe, list-based navigation over spatial/grid layouts, even
  where a visual design might otherwise suggest a grid (e.g. a fielding
  diagram, a grid of format cards).
- Careful, deliberate semantics — on Android this means
  `Modifier.semantics`, `Role`, `LiveRegionMode`, explicit
  `contentDescription`, and focus order via `FocusRequester` — chosen
  as carefully as the web app's ARIA `role="alert"` vs `role="status"`
  distinctions were.
- Prefer Compose's own built-in accessible components/modifiers
  (`Modifier.selectable`, `Modifier.toggleable`, standard `Button`/
  `RadioButton`/`Checkbox`) over hand-rolled `Modifier.semantics {}`
  blocks wherever they fit — they're Google's own
  accessibility-tested implementations of common patterns, less likely
  to develop the kind of subtle cross-screen-reader inconsistency the
  web app's custom ARIA code had to specifically work around.
- Custom gesture surfaces (the eventual pitching/batting/fielding
  drag-and-tap screens) will need their own deliberate TalkBack-
  specific design, same as the web app's PitcherScreen/
  BattingShotScreen/FieldingScreen needed custom ARIA handling — this
  is the highest-risk, highest-effort remaining UI work and should be
  tackled with real device/TalkBack testing when possible, not just
  code review.

## Done

**The entire pre-match setup flow is complete** (format -> stadium ->
team -> playing XI -> toss), all real screens:

- **Navigation scaffold** (`app/src/main/java/com/cricketsim/ui/Screen.kt`)
  — a plain sealed interface switched on in one composable
  (`MainActivity.CricketSimApp`), not the Navigation-Compose library.
  Revisit once persistence (see PORTING_NOTES.md's "What's next")
  makes deep-linking into an in-progress match a real requirement.
- **`FormatSelectionScreen`** — step 1: a single-column `LazyColumn`
  using `Modifier.selectable(..., role = Role.RadioButton)`. The ONE
  screen where a persistent selection sits next to unchosen
  alternatives before a separate Continue button — every step after
  this one picks-and-immediately-advances.
- **`StadiumSelectionScreen`** — step 2: a two-step drill-down
  (country, then stadium) over `StadiumData.kt`'s 101 stadiums.
  Introduced the `Modifier.clickable(..., role = Role.Button)` pattern
  used by every subsequent pick-and-advance list.
- **`TeamSelectionScreen`** — step 3: pick the user's team, then the
  opponent (excluded from its own list), from `CricketData.kt`'s 18
  squads.
- **`PlayingXIScreen`** — step 4: a genuine multi-select squad step
  (`Modifier.toggleable(..., role = Role.Checkbox)`, live-announced "N
  of 11 selected" counter), then three chained pick-and-advance steps
  (captain, vice-captain, wicketkeeper). `CricketData.buildMatchSquad`
  finalizes the result; the opponent's XI is auto-picked via
  `CricketData.autoSelectPlayingXI`.
- **`TossScreen`** — step 5: call heads/tails
  (`MatchEngine.simulateToss`), then a bat/bowl choice when the user
  wins (overriding `simulateToss`'s placeholder decision) or a
  live-announced result readout when the AI wins.

**The match screen has a first slice** (`app/src/main/java/com/cricketsim/ui/match/`),
proving the whole logic layer works end to end inside the real UI —
but this is explicitly scaffolding, not the real design:
- **`MatchSimulation.kt`** — ⚠️ TEMPORARY. Drives one delivery at a
  time through the full pipeline (`BowlingSystem`/`BattingSystem` AI
  decisions for BOTH sides, `FieldingSystem` legality,
  `MatchEngine.simulateBall`, `MatchStateMachine.applyBallOutcome` /
  `recordWicketFall` / `bringInNewBatsman` / `rotateStrike` /
  `changeBowler`), plus innings/match-completion detection. Both sides
  AI-controlled since no gesture surface exists yet to let the user
  play their own side — see the file's own doc comment for the full
  list of simplifications (situational bias always 0, no opener/
  bowler-selection prompts, next batsman always auto-picked).
- **`MatchScreen.kt`** — live score (a `LiveRegionMode.Polite` region,
  since it changes every ball), current batsmen/bowler, a manual
  "Simulate Next Ball" button, a recent-commentary feed, and a
  match-result readout. **Replace, don't extend** — this is a verify-
  the-wiring screen, not a starting point to gradually add features to.
- `MainActivity.kt` hosts the full flow through to this first-slice
  match screen.

**The first of the three custom gesture surfaces exists**
(`app/src/main/java/com/cricketsim/ui/match/PitchingScreen.kt`), but is
**not yet wired into the match loop**:
- **`PitchingScreen`** — a fresh TalkBack-native design, not a port of
  the web app's continuous-drag `PitcherScreen`. Angle, line,
  variation, target length, and speed are all discrete single-swipe
  list picks (same `Role.Button`/`Role.RadioButton` patterns as the
  setup flow). Execution quality — which `BowlingSystem.computeBowlingQuality`
  needs as a continuous `verticalFraction` — comes from a genuine skill
  mechanic instead of a drag: a repeating HAPTIC PULSE timed by
  `BattingSystem.computeTimingIntervalMs(speedKmh)` (deliberately
  reused from the batting timing minigame for a consistent feel), where
  the player releases on the final pulse and timing error shifts the
  release point away from the target band's center (early -> shorter,
  late -> fuller) — able to drift into a neighboring band on a bad
  miss, which `computeBowlingQuality`/`resolveActualLength` then score
  exactly as they would any other `verticalFraction` source. See the
  file's own doc comment for the full list of known v1 simplifications
  (no overshoot penalty yet, no player-controlled swing, haptic-only
  timing cue pending the audio layer).

## Not started

The single largest remaining piece of work is finishing the match
screen and wiring the gesture surfaces into it:

1. **Wire `PitchingScreen` into `MatchScreen`/`MatchSimulation`** — the
   screen itself works standalone but nothing calls it yet. This needs
   `MatchSimulation`'s ball loop to check whether `matchState.bowlingTeam.id
   == userTeam.id` and, if so, pause the automated loop, show
   `PitchingScreen`, and feed its resulting `ResolvedBowlingDecision`
   into `MatchEngine.simulateBall` in place of
   `BowlingSystem.generateAiBowlingDecision`'s output — the AI path
   stays exactly as `MatchSimulation.kt` already has it for whichever
   side the user is NOT bowling for.
2. **Batting gesture surface** — footwork/shot/intent/timing
   (`BattingSystem.kt`). Needs the same fresh-design treatment as
   pitching; the existing `computeTimingIntervalMs`/`computeTimingTier`
   functions are already proven reusable (pitching reuses them too) so
   batting's own timing step can very likely share UI patterns with
   `PitchingScreen`'s release step.
3. **Fielding gesture surface** — placement (`FieldingSystem.kt`). The
   web app's drag-and-drop field map has the least obvious accessible
   analogue of the three; likely wants its own discrete-list redesign
   (e.g. picking a sector then a depth from a list, rather than any
   kind of spatial placement) — worth extra design thought before
   starting.
4. A real win-probability display (`MatchEngine.calculateWinProbability`)
   and required-run-rate display when chasing.
5. Scorecard (batting/bowling figures, partnerships — `MatchStats.kt`).
6. Real commentary/audio, tied to `MatchEngine.generateCommentary` /
   `CommentaryLibrary.kt` (the first slice just shows the plain
   `BallOutcome.commentary` string).
7. Rain-delay dialog (`WeatherSystem.shouldTriggerRainInterruption`,
   `MatchStateMachine.applyRainInterruption`).
8. Wicket / new-batsman selection flow for the user's own team
   (`MatchStateMachine.bringInNewBatsman`, `getAvailableBatsmen` — the
   first slice always auto-picks the next available player).
9. Bowler-selection flow for the user's own team
   (`MatchStateMachine.selectBowler`, `getEligibleBowlers` — the first
   slice always auto-picks via `changeBowler`).
10. Proper innings-break / match-result screens (the first slice just
    shows a plain result sentence).
11. A real situational-bias calculation feeding into the AI decisions
    and `changeBowler`'s rotation scoring (currently hardcoded to 0 —
    neutral — everywhere in `MatchSimulation.kt`).

Also blocked on the Android persistence layer (see PORTING_NOTES.md):
resume-match prompt, save indicator, and anything else that needs a
match to survive beyond one app session.

## Verifying a UI screen

Since there's no source `.tsx` to diff against (this is fresh design,
not a port), "verifying" a screen means something different from
PORTING_NOTES.md's checklist:
- Every interactive element has a sensible TalkBack announcement
  (role, label, and current state/value where relevant) — test by
  reading through what TalkBack would actually say, not just that
  `contentDescription` is non-empty.
- Navigating the screen with only linear swipe gestures (no visual
  reference) reaches every control in a sensible order.
- Any dynamic change a sighted user would notice visually (a value
  updating, an error appearing, a selection changing) has a
  corresponding non-visual signal — a live region, a state change on
  the right semantics node, or equivalent.
- Real device/TalkBack testing before considering a screen done,
  especially for anything with custom gestures — code review alone
  missed real bugs on the web app (see the original handoff's note
  about bugs found via an actual screen-reader user on a Samsung
  Galaxy A23). This matters enormously for the match screen's gesture
  surfaces specifically — treat code review as a first pass only.

**Keep this file in sync**, same discipline as PORTING_NOTES.md: update
it in the same session/commit as the UI work, not as an afterthought.
