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
  shot. At the end of an over, when the USER's side is bowling, it
  redoes the bowler change through `MatchStateMachine.selectBowler` so
  the field the user set carries over (`changeBowler` always installs a
  fresh AI-generated field, which would have silently wiped it every
  over). See the file's own doc comment for the full list of
  simplifications (situational bias always 0, no reactive AI field
  placement, no opener/bowler-selection prompts, next batsman always
  auto-picked).
- **`MatchScreen.kt`** — live score (a `LiveRegionMode.Polite` region,
  since it changes every ball), current batsmen/bowler, buttons that
  route to the three gesture surfaces (Bowl + Set field while bowling;
  Face next ball + Hear the field while batting), a polite live region
  for the outcome of the last Set field, a persistent line when the
  user's own field is illegal, a recent-commentary feed, and a
  match-result readout. **Replace, don't extend** — this is a
  verify-the-wiring screen, not a starting point to gradually add
  features to.
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
finished in roughly 1.3-2.1 seconds — so with TalkBack the mechanic was
effectively unplayable. The release step is now a single full-screen
`clickable` node, the only focusable element, with a longer lead-in
under touch exploration (same as batting). The instructions moved to the
Speed step (read while browsing, not during the rhythm), and there is no
Back on the release step any more.

Behaviour changes to be aware of: first buzz is now at 550ms (1400ms
with a screen reader) instead of one interval; the final buzz is the
4th at lead-in + 3 intervals.

## Known issues / needs a real device

**Nothing written in the last two sessions has been compiled or run** —
`BattingScreen`, `FieldingScreen`, the `PitchingScreen` rewrite, and
the `MatchScreen`/`MatchSimulation` wiring. They were written against
the APIs as read from the repo; expect a first-build pass to fix small
things. The same is true, until proven otherwise, of everything else
in the UI layer.

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

## Not started

All three gesture surfaces now exist. Remaining work is finishing the
match screen around them:

1. **Reactive per-delivery AI field** when the AI is bowling — the web
   sets one once each delivery's actual length is known (bouncer trap,
   yorker field); the Kotlin loop uses whatever the last bowler change
   set. `FieldingSystem.generateAiFieldPlacements(..., upcomingLength)`
   is ready; it belongs in `MatchSimulation.simulateOneBall`.
2. Optional field presets for the user (attacking / balanced /
   containing) on top of `generateAiFieldPlacements`, to cut the
   two-picks-per-fielder cost. NOT in the web app — a gameplay decision
   (it hands the user the AI captain's templates), so ask first.
3. A real win-probability display (`MatchEngine.calculateWinProbability`)
   and required-run-rate display when chasing.
4. Scorecard (batting/bowling figures, partnerships — `MatchStats.kt`).
5. Real commentary/audio, tied to `MatchEngine.generateCommentary` /
   `CommentaryLibrary.kt` (the first slice just shows the plain
   `BallOutcome.commentary` string). The audio layer also unlocks the
   timing tick for both timing minigames.
6. Rain-delay dialog (`WeatherSystem.shouldTriggerRainInterruption`,
   `MatchStateMachine.applyRainInterruption`).
7. Wicket / new-batsman selection flow for the user's own team
   (`MatchStateMachine.bringInNewBatsman`, `getAvailableBatsmen` — the
   first slice always auto-picks the next available player).
8. Bowler-selection flow for the user's own team
   (`MatchStateMachine.selectBowler`, `getEligibleBowlers` — the first
   slice auto-picks). `selectBowler` already carries the field over, so
   the interim `MatchSimulation` workaround can go once this exists.
9. Proper innings-break / match-result screens (the first slice just
   shows a plain result sentence).
10. A real situational-bias calculation feeding into the AI decisions
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
