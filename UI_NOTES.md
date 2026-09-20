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
- Custom gesture surfaces (the pitching/batting/fielding screens) need
  their own deliberate TalkBack-specific design, same as the web app's
  PitcherScreen/BattingShotScreen/FieldingScreen needed custom ARIA
  handling — this is the highest-risk, highest-effort remaining UI work
  and should be tackled with real device/TalkBack testing when
  possible, not just code review.

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
  decisions, `FieldingSystem` legality, `MatchEngine.simulateBall`,
  `MatchStateMachine.applyBallOutcome` / `recordWicketFall` /
  `bringInNewBatsman` / `rotateStrike` / `changeBowler`), plus
  innings/match-completion detection. Takes optional
  `presetBowlingDecision` / `presetBattingDecision` so a gesture
  surface can supply the user's own decision (and, when the user bats,
  the AI delivery they were already shown). `generateBowlingDecision`
  is split out so a batter can be shown the ball before choosing a
  shot. It also owns the rules for who picks whom, matching the web's
  match.tsx:
  - **The user's own side is never auto-picked.** A wicket in the
    user's innings sets `pendingDismissal`; if it fell on the last ball
    of an over it also sets `deferredOverEnd`, so the strike rotation
    and the AI's bowler change wait for the replacement
    (`completeWicketReplacement` finishes them). The end of an over
    while the user is bowling sets `needsBowlerSelection`. The AI's own
    side is auto-picked as before (next batsman in squad order;
    `changeBowler`). Nobody is prompted when the innings or chase ends
    on that very ball.
  - `bowlerChoices` is `getEligibleBowlers` plus a safety net the web
    lacks: a squad with few genuine bowlers can leave that list EMPTY
    once they've bowled their quota (an empty dialog with no way
    forward on the web), so it widens to anyone under the cap, then to
    anyone but the last bowler.
  - When the AI is bowling, its captain sets a fresh field for every
    delivery once the ball's actual length is known (bouncer trap for
    short balls, yorker field for full ones, powerplay ring in the
    powerplay), via `FieldingSystem.generateAiFieldPlacements(...,
    upcomingLength)` — web parity. The field it set is kept in the
    returned state.
  The user's field carrying over between overs is now simply
  `MatchStateMachine.selectBowler`'s own behaviour (the earlier interim
  workaround is gone). Remaining simplifications: situational bias is
  always 0, the AI's next batsman is just the next in squad order (the
  web's smarter `selectAiNextBatsman` lives in match.tsx, not helpers/),
  and no rain interruption is rolled yet.
- **`MatchScreen.kt`** — puts the three selection screens in front of
  everything else whenever the user's side owes a pick; buttons that
  route to the three gesture surfaces (Bowl + Set field while bowling;
  Face next ball + Hear the field while batting) and the scorecard; a
  polite live region for the outcome of the last Set field; a
  persistent line when the user's own field is illegal; and a
  match-result readout. Reading order follows the web's match screen:
  striker, non-striker, bowler (each with live figures), the action
  buttons, then the last ball, score, target / runs needed, current run
  rate, required run rate and (in a chase) win probability, then
  earlier commentary. The whole screen scrolls. **The last ball and the
  score are both polite live regions** — before, only the score was, so
  a screen-reader user never heard what happened on the ball.
  **Replace, don't extend** — this is a verify-the-wiring screen, not a
  starting point to gradually add features to.
- **`MatchLines.kt`** — every spoken match line as a plain string
  (`MatchLines`) and every scorecard row (`ScorecardLines`), kept out of
  the composables so wording lives in one place. Wording follows the web
  ("Striker: X, 12 runs off 9 balls.", "22 runs needed off 30 balls.",
  "Required run rate: 8.80 runs per over."). Chase figures read
  `state.oversLimit`, so a rain-reduced match shows the revised
  requirement immediately (as on the web). **Win probability is shown
  on request — it is NOT on the web's match screen** (the web only uses
  it to drive crowd-tension audio). It appears after the required run
  rate, in a chase only (in the first innings
  `MatchEngine.calculateWinProbability` returns a constant 50, which
  would be noise). It is a deliberately coarse heuristic — five
  required-run-rate bands scaled down when few wickets are left — so it
  moves in steps, not smoothly. `dismissalSummary` words the wicket
  announcement like the web's Wicket! dialog.
- **`ScorecardScreen.kt`** — the web's tabbed scorecard for a linear
  screen reader: an innings switch (once a first innings exists), then
  Batting / Bowling / Partnerships / Wickets, each row one
  self-contained sentence. Switches are `selectable(role = Role.Tab)`
  in a `selectableGroup()` inside a horizontally scrolling row. Back to
  match comes FIRST in focus order (long scrolling page), and the
  innings total and section switch stay fixed while the rows scroll.
  Partnerships lists the current one first. **Maidens and fall of
  wickets are real now:** bowling rows include maidens, and the wickets
  list reads "Wicket 3: X, 12 runs off 9 balls, caught, bowler Y. Team
  45 for 3 after 5.3 overs.", in the order wickets actually fell. Both
  come from two additions to `MatchStats` in the logic layer, beyond
  the web source (see PORTING_NOTES.md and that file's header).
- **`SelectionScreens.kt`** — the three "who?" decisions, each
  replacing the match screen while pending (like the gesture surfaces)
  rather than floating over it as the web's modal dialogs do:
  `OpenerSelectionScreen` (striker, then non-striker; Back returns to
  the striker step), `NewBatsmanScreen` (after one of your batsmen is
  out) and `BowlerSelectionScreen` (opening bowler, and each new over).
  All plain single-swipe lists that pick-and-advance — no drop-down and
  no separate confirm button, unlike the web (a drop-down inside a modal
  is a poor fit for TalkBack, and one pick per screen is quicker). Each
  row leads with the name, then what makes the choice meaningful:
  batting and bowling ratings, and for bowlers their figures so far plus
  "N overs left" where the format caps overs. `NewBatsmanScreen` puts
  the whole dismissal in its heading ("Wicket! X is out for 12 runs off
  9 balls. Caught, bowled by Y."), so the first thing TalkBack reads is
  the news, then the score, then the list — it replaces the match screen
  at exactly the moment the ball's outcome would otherwise have been
  announced, so it has to carry that news. Back sits above the list
  where there is one.
- `MainActivity.kt` hosts the full flow through to this first-slice
  match screen.

**All three custom gesture surfaces exist AND are wired into the match
loop** (`app/src/main/java/com/cricketsim/ui/match/`):

- **`PitchingScreen`** — a fresh TalkBack-native design, not a port of
  the web app's continuous-drag `PitcherScreen`. Angle, line,
  variation, target length, and speed are all discrete single-swipe
  list picks (same `Role.Button`/`Role.RadioButton` patterns as the
  setup flow). Execution quality — which `BowlingSystem.computeBowlingQuality`
  needs as a continuous `verticalFraction` — comes from a genuine skill
  mechanic instead of a drag: a repeating HAPTIC PULSE (4 buzzes) timed
  by `BattingSystem.computeTimingIntervalMs(speedKmh)`, where the player
  releases on the final buzz and timing error shifts the release point
  away from the target band's center (early -> shorter, late ->
  fuller). **The release step was rewritten** (see "Fixed" below) to
  the same single full-screen-node design as batting's timing step.
  See the file's own doc comment for the remaining v1 simplifications.
- **`BattingScreen`** — same fresh-design approach, mirroring the web
  app's ORDER of decisions: footwork committed BLIND (front/back foot
  list) -> delivery revealed (actual length, line, variation, angle,
  speed, spoken as one heading + polite live region) -> one of 15
  named shots (single-swipe list) -> intent (4-option list, skipped
  for the two defensive shots) -> the web's 5-pulse timing minigame
  scored by `BattingSystem.computeTimingTier` -> a result step with
  tier + early/late feedback + Continue. Design points worth knowing:
  - **No Back after the reveal.** Back exists on the footwork step and
    intent -> shot only; going back from shot to footwork would let a
    player re-pick footwork knowing the ball, defeating the blind
    commit.
  - **The timing step is one full-screen `clickable` node, the only
    focusable element there**, so TalkBack focus lands on it and a
    double-tap anywhere swings. (Under touch exploration a double-tap
    delivers an accessibility click action, not raw pointer events, so
    `pointerInput` would not work.) Its spoken label is just "Timing";
    the instruction rides on the click label. The visible "Buzz n of 5"
    text has cleared semantics on purpose.
  - **Precise timing.** Tap time is measured with
    `SystemClock.elapsedRealtime()` against a fixed theoretical
    schedule (lead-in + 4 intervals), same reasoning as the web.
    Pulses are scheduled against the start time, not chained delays.
  - **Deliberate deviation from the web:** with touch exploration on,
    the lead-in before pulse 1 is 1400ms (web: 550ms) so TalkBack's
    speech finishes before the rhythm starts. Needs tuning on device.
  - Wiring: `MatchScreen` shows it when `matchState.battingTeam.id ==
    userTeam.id`; `generateDelivery` is called once, after the footwork
    commit; the revealed delivery is passed back into
    `simulateOneBall` as `presetBowlingDecision` so the ball shown is
    the ball bowled.
- **`FieldingScreen`** — the web app's two-phase pick (which fielder,
  then where) as pure single-swipe lists, no custom gesture:
  - OVERVIEW: the nine fielders as "Position — Name" with fielding
    rating, a legality line, and **Set field above the list** so a
    TalkBack user doesn't swipe past nine rows to save. Back is
    labelled "Back (discard changes)".
  - SECTOR: the 12 sectors, each showing who is standing there (with a
    Cancel above the list). The web has one flat list of 23 slots; two
    levels here means at most 12 + 3 swipes instead of up to 23.
  - DEPTH: only when the sector has more than one slot (Mid-On vs
    Long-On; Slip vs Gully); each option says "Empty" or who you'd
    swap with. Moving onto an occupied slot swaps, exactly like the
    web.
  - One polite live region reports the outcome of a move in full (who
    went where, who they swapped with, rating, and whether the field
    just became illegal or legal again). The legality line is
    deliberately not live, so one move isn't announced twice.
  - An illegal field can still be set (web parity) but is called out on
    the fielding screen and, persistently, on the match screen: every
    delivery is a no-ball until fixed.
  - Read-only mode is the web's "browse the AI's field": the same list
    with nothing actionable, reached from Hear the field while batting.
    It shows the field the AI set for the last delivery, since the
    next one is only set once that ball's length is known (as on the
    web).
  - Valid depths per sector are derived from `FieldingSystem.toggleDepth`
    because the source table is private (see `validDepths` in the
    file). Replace with a real accessor if `FieldingSystem` gains one.
  - Wiring: `MatchScreen` passes `matchState.fieldPlacements` in and
    stores the result with `MatchStateMachine.setFieldPlacements`.

## Fixed

**`PitchingScreen` release timing.** `ReleaseStep.handleRelease` used to
estimate elapsed time as `pulsesFired * intervalMs`, which only changed
when a pulse fired: a tap between pulse 3 and 4 scored as maximally
early, and any tap after pulse 4 (up to the 1.5-interval grace timeout)
scored as exactly perfect, so late releases were never penalised. The
step now measures real elapsed milliseconds with
`SystemClock.elapsedRealtime()` against a fixed schedule.

While fixing it, a second, worse problem turned up and was fixed in the
same rewrite: the old Release *button* sat several swipes from the first
TalkBack focus stop, while the entire rhythm plus its auto-miss timeout
finished in roughly 1.0-2.1 seconds (5.5 intervals of 185-380ms) — so
with TalkBack the mechanic was effectively unplayable. The release step
is now a single full-screen `clickable` node, the only focusable
element, with a longer lead-in under touch exploration (same as
batting). The instructions moved to the Speed step (read while
browsing, not during the rhythm), and there is no Back on the release
step any more.

Behaviour changes to be aware of: first buzz is now at 550ms (1400ms
with a screen reader) instead of one interval; the final buzz is the
4th at lead-in + 3 intervals.

The rewrite was diffed against the previous version of the file: only
the release step, the Speed step's instruction text, the doc comment
and the new constants differ.

**Scorecard gaps.** Maidens were never counted (`MatchStats` passed a
hardcoded `false`, as does the web) and the wickets list had no team
score and no true order (the web shows the batsman's own runs, numbered
by batting order). Both are fixed in `MatchStats` with additions that
leave everything the web source computes unchanged — see PORTING_NOTES.md.

**A dead end in bowler selection.** The web's bowler dialog can be empty
when a squad has few genuine bowlers and they've all bowled their quota;
`MatchSimulation.bowlerChoices` widens the list so the match can never
get stuck.

## Known issues / needs a real device

**Nothing written in the last five sessions has been compiled or run** —
`BattingScreen`, `FieldingScreen`, the `PitchingScreen` rewrite,
`MatchLines`, `ScorecardScreen`, `SelectionScreens`, the `MatchStats`
additions and the `MatchScreen`/`MatchSimulation` wiring. They were
written against the APIs as read from the repo (the `BattingSystem`,
`FieldingSector`, `MatchEngine`, `MatchStats` and `MatchState` names and
signatures used were checked against the source, including
`calculateWinProbability(MatchState): Int` and the state machine's
selection functions); expect a first-build pass to fix small things. The
same is true, until proven otherwise, of everything else in the UI
layer. Compiling is deliberately deferred until the code is
feature-complete.

TalkBack/device checks for the two timing surfaces (`BattingScreen`,
`PitchingScreen`):
- Does focus land on the single timing node, and does a double-tap
  anywhere act? Does the short spoken label plus click label finish
  before the first buzz (1400ms lead-in)? Does anything get announced
  when the decorative buzz text changes despite its cleared semantics?
- Touch-to-click latency under TalkBack. Both
  `*_INPUT_LATENCY_COMPENSATION_MS` constants are 0; batting's Perfect
  window is only ~30-60ms either side of the beat.
- Haptics: every buzz is the same Compose `LongPress`, which follows
  the system touch-feedback setting and can't distinguish the last
  pulse (web: 50ms vs 90ms). Consider a `Vibrator`-based pulse (needs
  the VIBRATE permission) and an audio tick once the audio layer exists.
- The timing logic is duplicated between `BatTimingStep` and
  `PitchingScreen.ReleaseStep`; extract a shared composable when either
  next changes.

TalkBack checks specific to `BattingScreen`:
- Does TalkBack announce the delivery heading when the shot step
  appears (heading + live region, no explicit focus request)? Double
  read?

TalkBack checks specific to `FieldingScreen`:
- After a move, does the polite status message get spoken once and in
  full when focus lands back on the overview? Does the heading steal it?
- Is 12 sectors + Cancel-above-list comfortable, or would grouping /
  presets serve better? (See "Not started".)
- Does `Back (discard changes)` get pressed by accident? Consider a
  confirm if changes exist.

TalkBack checks specific to the match screen, scorecard and selection
screens:
- The last-ball and score live regions live inside a Column that is
  removed while a gesture or selection screen is showing and re-added
  afterwards. Is their text spoken when they come back (a newly-added
  live region isn't a *change*), and in the right order (outcome, then
  score)? If not, announce explicitly.
- After a wicket the match screen is replaced by `NewBatsmanScreen`, so
  the wicket commentary line is not heard until you return. The screen's
  heading carries the dismissal and the score follows, but check it
  really is enough and that nothing about the ball is lost.
- Do the `Role.Tab` selectors announce as tabs with a selected state, and
  are they reachable inside the horizontally scrolling row?
- Is Back-to-match-first right for the scorecard, or surprising?
- After picking in a selection screen, where does TalkBack focus land on
  the match screen? Ideally on the heading or the first player line.
- The win probability line only changes in steps (five bands, then
  scaled for wickets) — confirm that reads as sensible rather than
  jumpy.

## Not started

All three gesture surfaces exist, both sides' fielding is live, the
scorecard/chase information is in, and the user now picks their own
openers, next batsman and bowlers. Remaining work is finishing the match
screen around them:

1. Optional field presets for the user (attacking / balanced /
   containing) on top of `FieldingSystem.generateAiFieldPlacements`, to
   cut the two-picks-per-fielder cost. NOT in the web app — a gameplay
   decision (it hands the user the AI captain's templates), so ask
   first.
2. Real commentary/audio, tied to `MatchEngine.generateCommentary` /
   `CommentaryLibrary.kt` (the first slice just shows the plain
   `BallOutcome.commentary` string; the web's last-ball line also adds
   ball quality, shot played and timing, which would help a batter
   learn). The audio layer also unlocks the timing tick for both timing
   minigames and the win-probability-driven crowd tension.
3. Rain-delay dialog and rolling the interruption each over
   (`WeatherSystem.shouldTriggerRainInterruption`,
   `MatchStateMachine.applyRainInterruption`, `resumeFromRainDelay`).
4. Proper innings-break / match-result screens (the first slice just
   shows a plain result sentence).
5. A real situational-bias calculation feeding into the AI decisions
   and `changeBowler`'s rotation scoring (currently hardcoded to 0 —
   neutral — everywhere in `MatchSimulation.kt`), plus the web's
   situational `selectAiNextBatsman` for the AI's next batsman. Both
   formulas live in pages/match.tsx, not helpers/. The web's
   required-run-rate-driven bias is the reference.
6. **A build.** Nothing here has ever been compiled; the APK build
   (signing, emulator/device) is still untouched. Deliberately deferred
   until feature-complete; a CI workflow that builds a debug APK on
   every push would then surface compile errors immediately.

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
