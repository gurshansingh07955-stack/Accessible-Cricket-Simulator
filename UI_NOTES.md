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

**🎉 The entire pre-match setup flow is complete** (format -> stadium
-> team -> playing XI -> toss), all real screens, all following the
same established accessibility patterns:

- **Navigation scaffold** (`app/src/main/java/com/cricketsim/ui/Screen.kt`)
  — a plain sealed interface switched on in one composable
  (`MainActivity.CricketSimApp`), not the Navigation-Compose library.
  The setup flow is short and linear, so a hand-rolled `when` is
  simpler than a nav graph for now. Revisit once persistence (see
  PORTING_NOTES.md's "What's next") makes deep-linking into an
  in-progress match a real requirement.
- **`FormatSelectionScreen`** — step 1: a single-column `LazyColumn` of
  format options using `Modifier.selectable(..., role =
  Role.RadioButton)`, `RadioButton` itself set to `onClick = null` so
  the whole row (not just the radio circle) is the tap target. This is
  the ONE screen in the flow where a persistent selection sits visibly
  next to unchosen alternatives before a separate Continue button is
  pressed — every other step below picks-and-immediately-advances.
- **`StadiumSelectionScreen`** — step 2: a two-step drill-down (country,
  then stadium) over `StadiumData.kt`'s 101 stadiums. Introduced the
  `Modifier.clickable(..., role = Role.Button)` pattern used by every
  subsequent pick-and-advance list in the flow.
- **`TeamSelectionScreen`** — step 3: pick the user's team, then the
  opponent (excluded from its own list), from `CricketData.kt`'s 18
  squads. Same drill-down/`Role.Button` pattern as stadium selection.
- **`PlayingXIScreen`** — step 4: the most complex screen in the flow.
  A genuine multi-select squad step (`Modifier.toggleable(..., role =
  Role.Checkbox)`, live-announced "N of 11 selected" counter via
  `liveRegion = LiveRegionMode.Polite`), then three chained
  pick-and-advance steps (captain, vice-captain, wicketkeeper — keeper
  candidates filtered to specialists in the XI, falling back to the
  full XI if none exist) using the same `Role.Button` pattern.
  `CricketData.buildMatchSquad` finalizes the result. The opponent
  never gets a selection screen — `MainActivity` auto-picks its XI via
  `CricketData.autoSelectPlayingXI` right after.
- **`TossScreen`** — step 5: call heads/tails
  (`MatchEngine.simulateToss`), then either a bat/bowl choice
  (pick-and-advance, overriding `simulateToss`'s placeholder decision
  per its own doc comment) when the user wins, or a live-announced
  result readout (`liveRegion = LiveRegionMode.Polite` on a heading —
  the one case in the flow where the announced text appears already-
  resolved on screen entry rather than resulting from an action just
  taken on the same screen) when the AI wins.
- **`MatchPlaceholderScreen`** (`app/src/main/java/com/cricketsim/ui/match/`)
  — a deliberate stand-in for the match screen itself, proving the
  ENTIRE setup flow works end to end. **Replace, don't extend** — see
  "Not started" below for everything the real version needs.
- `MainActivity.kt` hosts the complete setup flow instead of the old
  logic-loading status screen.

## Not started

Only one thing is left, but it's the largest and highest-risk piece by
far — **the match screen itself**, replacing `MatchPlaceholderScreen`:
- Live score display (runs/wickets/overs, target/required-rate when
  chasing, win probability via `MatchEngine.calculateWinProbability`)
- The three custom gesture surfaces — pitching (line/length/angle/
  variation/speed), batting (footwork/shot/intent/timing), fielding
  (drag-and-tap placement) — each needs its OWN deliberate TalkBack
  design; nothing here can be copied from the web app's ARIA-specific
  versions (`PitcherScreen`/`BattingShotScreen`/`FieldingScreen`), only
  the underlying mechanics they control (`BowlingSystem.kt`/
  `BattingSystem.kt`/`FieldingSystem.kt`) carry over
- Scorecard (batting/bowling figures, partnerships — `MatchStats.kt`)
- Commentary display, tied to `MatchEngine.generateCommentary` /
  `CommentaryLibrary.kt`
- Rain-delay dialog (`WeatherSystem.shouldTriggerRainInterruption`,
  `MatchStateMachine.applyRainInterruption`)
- Wicket / new-batsman selection flow (`MatchStateMachine.recordWicketFall`,
  `bringInNewBatsman`, `getAvailableBatsmen`)
- Bowler-change flow (`MatchStateMachine.selectBowler`,
  `getEligibleBowlers`)
- Innings-break / match-result screens (`MatchStateMachine.switchInnings`)

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
