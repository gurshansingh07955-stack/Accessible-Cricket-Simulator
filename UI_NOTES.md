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
  `Screen.Settings(returnTo)` is the settings route from the first
  screen; `Screen.Match` carries an optional `resume` snapshot.
- **`FormatSelectionScreen`** — step 1 and the app's first screen: a
  single-column `LazyColumn` using `Modifier.selectable(...,
  role = Role.RadioButton)`. **When there is a saved match, a Resume saved
  match button goes FIRST, straight after the heading** — one button that
  reads as one item, its second line describing the match (teams, format,
  innings, score), so a screen-reader user knows what they are resuming
  before committing and continuing a match in progress is the fastest
  thing on the screen. A **Settings** button sits after Continue.
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
  live-announced result readout when the AI wins. Plays the coin-flip
  sound when the coin is called and queues the toss commentary
  (`TOSS_BAT` / `TOSS_BOWL`) once the decision is known, as the web does.

**Saving and resuming a match** (`app/src/main/java/com/cricketsim/persistence/`)
— the Android replacement for the web's `saveMatch` / `loadMatch` /
`clearMatch` / `hasActiveMatch` (browser `localStorage`), and a stated
requirement in the web project's own brief ("resume match option"):
- **`MatchSaveStore.kt`** — a `MatchSnapshot` (the whole `MatchState`,
  which already contains both XIs, the score, both innings' statistics, the
  field, weather, rain state and every pending pick, plus what the match
  SCREEN keeps: the stadium and toss it was created with, the recent
  commentary, and whether the innings-break screen is still to be shown) is
  written as JSON to one file in private storage (`saved_match.json`) using
  Gson's reflection over the existing data classes, **so the logic layer
  needed no annotations or changes**. Reflection can't handle one thing,
  the `DeliveryLength` sealed interface (two enums, held by `BallOutcome`),
  so it has an explicit adapter that records which enum it is.
- **Safety.** Writes go to a temp file that is renamed over the real one,
  so a crash or kill mid-save leaves the last good save intact. Save, load
  and clear share a lock. **Loading never throws**: a missing, corrupt or
  old-version save is simply "no saved match". A `version` number guards
  the shape — **bump `MatchSaveStore.CURRENT_VERSION` whenever any class in
  `MatchState`'s tree changes shape**; older saves are then discarded
  (deliberately not migrated: the web's `loadMatch` has a block of backfill
  code for old saves, which is not worth carrying for a game whose matches
  last an hour).
- **Autosave (in `MatchScreen`).** After every change, off the main thread;
  a finished match deletes its save. **A NEW match does not save until its
  first ball has been bowled (or it reaches the second innings)**, so
  opening a new match by mistake cannot overwrite a saved one — only
  actually playing it does.
- **Resume.** `MatchScreen(resume = snapshot)` just seeds the state the
  screen would otherwise create fresh, so everything downstream comes back
  for free: a pending opener/batsman/bowler pick, a rain delay, the field,
  the innings break. The first screen re-reads the save every time it is
  (re)entered, off the main thread, so the summary is current after leaving
  a match.
- **Not saved:** which sub-screen was open (a delivery in progress, the
  field screen, the scorecard) — a resumed match opens on its ordinary
  screen at the state after the last completed action; and the audio
  director's running tallies (back-to-back boundaries, a bowler's wicket
  streak), so a hat-trick or run of fours straddling a resume goes
  uncommented.
- **Leave match** is now **Save and leave**: the confirmation says the
  match is saved automatically and how to get back to it (Resume saved
  match on the first screen), Keep playing is first, and leaving returns to
  the first screen instead of the toss. Rotation no longer loses the match
  either (`configChanges`), and now neither does the process being killed.

**Audio and settings** (`app/src/main/java/com/cricketsim/audio/` and
`ui/settings/`) — a rewrite around Android's audio APIs of the web's
`audioManager.tsx`, `commentaryVoice.tsx` and `gameSettings.tsx`, plus
the audio half of `match.tsx`'s per-ball logic. Design constraint that
shaped all of it: **binary audio can't be copied into this repo with
the text-based tooling used to build it**, and the web's real recordings
live on the web app's own hosting. So:
- **`Synth.kt`** — every sound the web *synthesizes* is synthesized here
  too, as generated 16-bit PCM: the timing tick (with its accented final
  pulse), bat hit, boundary arpeggio, wicket sweep, delivery whoosh and
  noise crowd cheer — same waveforms, frequencies and envelopes. Plus
  stand-ins the web doesn't need because it always has its recordings: a
  coin clink, thunder, a rain bed and a crowd murmur bed. The recordings
  are better and are used whenever available; the stand-ins mean the game
  is never silent (the web is silent for a missing cheer).
- **`AudioAssets.kt`** — the seven recordings (crowd bed, small cheer, big
  roar, coin flip, bat hit, rain, thunder) are looked for in this order:
  `res/raw/<name>.mp3` in the APK, a previous download in app storage,
  then a download from the web host. **Verified:** all seven answer HTTP
  200 (`audio/mpeg`) on the web app's public host.
- **Bundling the audio: `tools/fetch_audio.sh`.** The recordings and the
  commentary clips are binary, so instead of copying them this repo has a
  script that downloads them from the web host into
  `app/src/main/res/raw/` (the 7 recordings, named `crowd_ambience`,
  `small_crowd_cheer`, `big_crowd_roar`, `coin_flip`, `bat_hit`,
  `rain_ambience`, `thunder_crack`) and `app/src/main/assets/commentary/`
  (the clips). It reads the clip list from `CommentaryLibrary.kt`'s
  `audioUrl` values, so it can't drift from the code; it is safe to re-run
  (existing files are kept, so clips generated later are picked up); and
  it writes `tools/audio_fetch_report.txt` (no timestamps, so a run with
  nothing new commits nothing). It is meant to be run by a GitHub Actions
  workflow on GitHub's own servers, which commits the result with the
  repo's own token — no personal token needed. **The workflow file is
  staged as `tools/fetch-audio.workflow.yml`** because the access token
  used to write this repo can't create files under `.github/workflows`
  (GitHub answers 403); moving it there once, on github.com, activates it
  and it then runs itself. It can also be run by hand:
  `bash tools/fetch_audio.sh`. **If `tools/audio_fetch_report.txt` exists
  in the repo, the files have been fetched.**
- **`SoundEngine.kt`** — recordings through `SoundPool`, synthesized
  sounds through `AudioTrack`, loops (crowd, rain) through `MediaPlayer`
  or a looping `AudioTrack`. Outcome sounds follow the web's mapping:
  wicket = sweep then a big roar 800ms later; six = arpeggio then a big
  roar 500ms later; four/five = arpeggio then a small cheer; else bat hit.
  The crowd bed's volume combines tension (match closeness), ducking,
  swelling (after a boundary or wicket) and the crowd-volume setting,
  with the web's constants, and its playback rate creeps up with tension.
  **One improvement over the web:** ducking is a *count* of things asking
  for quiet, not one flag — on the web a short effect-duck ending could
  un-duck the crowd in the middle of a commentary sequence. Rain plays a
  thunder crack and a looping bed and ducks the crowd while it runs.
  Commentary clips are queued so a wicket's comment, an over-complete
  comment and an innings-break comment play as one conversation, ducking
  the crowd for the whole sequence. **Each clip plays from the copy
  bundled in `assets/commentary/` when there is one (instant, offline) and
  is otherwise streamed from the web host**; a clip that can't be played
  either way is skipped (a watchdog stops a hung one blocking the queue).
  **Of the 190 clips the library references, 160 exist on the web app
  (about 9.3 MB); the other 30 were never generated there** — partnership
  150/200, bowler 3/5/10-wicket hauls, and every hat-trick line — so those
  moments are silent, exactly as on the web, until they are generated
  there and the fetch script is re-run. **There is deliberately no
  text-to-speech fallback for the AI clips** — the same line is already on
  screen and read by TalkBack, and a second synthetic voice would talk
  over it.
- **The timing tick.** Every pulse of both rhythm minigames (pitching's 4,
  batting's 5) is now a buzz *and* an audible tick, with the FINAL pulse
  accented (higher, louder) — exactly the web's design. This fixes the
  earlier known problem that all the buzzes felt identical, so the beat to
  act on can now be found by ear. The tick is a pre-built low-latency
  `AudioTrack` replayed by rewinding it, and is generated much louder than
  the web's (which is only a backup to vibration on iOS); on Android it is
  the audible timing channel. Buzzes respect the Vibration setting, ticks
  respect Sound effects.
- **`MatchAudioDirector.kt`** — ports the audio half of
  `match.tsx`'s `handlePlayBall` and neighbours (which, like `AiSituation`,
  live outside `helpers/`): the whoosh at delivery (cutting off any
  commentary still running); outcome sounds, vibration and commentary 400ms
  later (standing in for the ball's travel time); the special-event
  commentary (milestones, partnership milestones, bowler wicket hauls,
  back-to-back fours/sixes, three in an over, hat-tricks, tight/expensive
  overs, over complete) with its running state (previous boundary, fours
  and sixes this over, runs this over, the bowler's wicket streak — legal
  deliveries only, reset each innings); rain start/stop and the second-
  innings DLS line; innings break; match end (the crowd stops, the
  commentary carries on over the result screen); and `computeCrowdTension`
  (second innings: from win probability, near 50 = full tension; first
  innings: creeps up with wickets).
- **`GameSettings.kt` / `GameServices.kt` / `SpokenCommentary.kt`** — the
  web's settings (difficulty, vibration, sound effects, spoken commentary,
  AI commentary mode off/duo/excited/calm, crowd volume) in
  `SharedPreferences`, exposed as Compose state through
  `LocalGameServices` so no screen needed new parameters (which now also
  carries the saved-match store). **Spoken commentary** (the web's browser
  text-to-speech toggle) only speaks when TalkBack is off, because TalkBack
  already reads the live regions; with both, every ball would be read twice
  in two voices. Its engine is created on first use, so a TalkBack user
  never pays for it.
- **`SettingsScreen.kt`** — one linear scrolling column, **Back first**
  (long page), section headings, `toggleable(role = Switch)` rows and
  `selectable(role = RadioButton)` rows so each is one TalkBack item with
  its state, and a slider for crowd volume with a percentage state
  description. Opens from the first screen, and as an **overlay inside a
  match** (a route would leave the match screen and take the live match
  with it). Difficulty is here because the web has it and the match screen
  had it hardcoded to Medium.
- **Manifest / build.** Adds `INTERNET` (downloading recordings, streaming
  commentary), `VIBRATE`, a `<queries>` entry so text-to-speech works on
  Android 11+, and `configChanges` so rotating the phone no longer
  recreates the activity. `kotlinx-coroutines-android` and `gson` are
  explicit dependencies.

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
  surface can supply the user's own decision. `simulateOneBall` returns
  a `BallResult(state, outcome, battingDecision)` — the batting decision
  (the user's or the AI's) is returned because the last-ball line reports
  the shot and timing for both sides. It also owns:
  - **`prepareAiDelivery`** — the AI captain's preparation for a user who
    is batting, in the web's `beginBattingFlow` order: read the
    situation, decide the delivery, **set the field for that delivery**
    now that its actual length is known (bouncer trap for short balls,
    yorker field for full ones, powerplay ring in the powerplay,
    otherwise attacking / balanced / containing by its bowling bias), and
    report who moved where ("Kohli moved from Mid-On to Long-On."). All of
    it happens after the batter's footwork commit and BEFORE they pick a
    shot. **This was a real parity gap, found while porting the audio:**
    an earlier version set the AI's field inside `simulateOneBall`, i.e.
    after the batter had already committed, so the batter was silently
    facing a field they had never been told about, when the web *announces*
    every change precisely so the batter can choose a shot against it.
  - **How the AI thinks.** Every AI decision is shaped by the web's own
    situational-bias formulas, ported in `AiSituation.kt`.
  - **Who picks whom**, matching the web's match.tsx. The user's own
    side is never auto-picked. A wicket in the user's innings sets
    `pendingDismissal`; if it fell on the last ball of an over it also
    sets `deferredOverEnd`, so the strike rotation, the AI's bowler
    change and the rain roll wait for the replacement
    (`completeWicketReplacement`, which takes the stadium, finishes
    them). The end of an over while the user is bowling sets
    `needsBowlerSelection`. Nobody is prompted when the innings or
    chase ends on that very ball.
  - `bowlerChoices` is `getEligibleBowlers` plus a safety net the web
    lacks (an empty list on the web is a dead end).
  - **Rain.** At the end of every over that doesn't also end the
    innings, after the bowling change, it rolls
    `WeatherSystem.shouldTriggerRainInterruption` and applies any
    interruption. One deliberate difference from the web: an over whose
    last ball was a wicket for the user's side (deferred behind the
    new-batsman pick) also rolls, since it is the same over-end, just
    later.
  - `finishMatch` records `matchStatus = COMPLETED` and `winnerId`.
- **`AiSituation.kt`** — the web's AI "brain", ported from
  `pages/match.tsx`: `battingBias`, `bowlingBias`, `selectNextBatsman`,
  and `recentOverRuns` (derived from `state.ballByBall`, which
  `applyBallOutcome` appends to and `switchInnings` resets — checked
  against the source). Every threshold and coefficient is an exact match
  to the web. **Effect on the game:** the AI is now markedly stronger and
  more varied than the flat version, exactly as on the web — but game
  balance was tuned there, not here, so how a match *feels* on Android
  against this AI is untested.
- **`FieldPresets.kt`** — Normal / Defensive / Attacking fields for the
  user's own side (see FieldingScreen below). Not in the web.
- **`MatchScreen.kt`** — decides which screen is showing, in this
  order: (0) the leave-match confirmation, (1) the settings overlay, (2)
  the scorecard, (3) the match result, (4) a rain delay, (5) the innings
  break, (6) a pick the user's own side owes (openers, next batsman,
  bowler), (7) the gesture surfaces and the field screen, (8) the
  ordinary match screen. Rain outranks a pending pick because play has
  stopped. It runs a `MatchAudioDirector` for all the match's sound,
  **autosaves the match** (see above), and reads the difficulty from
  settings. The ordinary screen has buttons for the three gesture surfaces
  (Bowl + Set field while bowling; Face next ball + Hear the field while
  batting), the scorecard, Settings and Leave match. Reading order
  follows the web: striker, non-striker, bowler (each with live
  figures), the action buttons, then the last ball, score, target / runs
  needed, current run rate, required run rate and (in a chase) win
  probability, then earlier commentary. **The last ball and the score are
  both polite live regions.** Text-to-speech (only when TalkBack is off)
  reads each ball, the rain, the innings break and the result. **Replace,
  don't extend** — this is a verify-the-wiring screen.
- **`MatchFlowScreens.kt`** — the full-screen "the match has stopped"
  moments, all with the same shape (a heading that carries the news, a
  few plain lines, then buttons): `RainDelayScreen` (the web's
  `RainDelayDialog` wording; blocks play until Resume; focus left on the
  heading so the news is heard first), `InningsBreakScreen` (an
  addition: target, first-innings total, last ball, chase figures, which
  side you're on), `MatchResultScreen` (the result in the heading, both
  innings' totals and the last ball, which would otherwise never be
  heard), and `ConfirmLeaveScreen` ("Save and leave this match?", Keep
  playing first).
- **`MatchLines.kt`** — every spoken match line as a plain string
  (`MatchLines`) and every scorecard row (`ScorecardLines`). Wording
  follows the web. `ballSummary` is the web's `formatBallOutcomeText`.
  **Win probability is shown on request — it is NOT on the web's match
  screen** (the web only uses it to drive crowd-tension audio, which the
  audio director now does too). It appears after the required run rate,
  in a chase only, and moves in coarse steps.
- **`ScorecardScreen.kt`** — the web's tabbed scorecard for a linear
  screen reader (innings switch, then Batting / Bowling / Partnerships /
  Wickets, one sentence per row, `Role.Tab` selectors, Back first).
  Maidens and fall of wickets are real (two additions to `MatchStats`
  beyond the web source — see PORTING_NOTES.md).
- **`SelectionScreens.kt`** — the three "who?" decisions (openers, next
  batsman, next bowler), each replacing the match screen while pending.
  Plain single-swipe lists that pick-and-advance; the dismissal is in the
  new-batsman heading so the news is heard first.
- `MainActivity.kt` hosts the full flow and owns `GameServices`: it
  provides it through `LocalGameServices`, pauses sound when the app is
  backgrounded, releases it when the activity really finishes, and offers
  the saved match on the first screen. Return to home (a finished match)
  and Save and leave both go to the first screen.

**All three custom gesture surfaces exist AND are wired into the match
loop** (`app/src/main/java/com/cricketsim/ui/match/`):

- **`PitchingScreen`** — a fresh TalkBack-native design, not a port of
  the web app's continuous-drag `PitcherScreen`. Angle, line,
  variation, target length, and speed are all discrete single-swipe
  list picks. Execution quality comes from a genuine skill mechanic
  instead of a drag: a repeating rhythm of 4 pulses — each a buzz and an
  audible tick, the final one accented — timed by
  `BattingSystem.computeTimingIntervalMs(speedKmh)`, where the player
  releases on the final pulse and timing error shifts the release point
  away from the target band's center (early -> shorter, late ->
  fuller). The release step is a single full-screen-node design (see
  "Fixed"). See the file's own doc comment for the v1 simplifications.
- **`BattingScreen`** — same fresh-design approach, mirroring the web
  app's ORDER of decisions: footwork committed BLIND -> the delivery
  revealed (actual length, line, variation, angle, speed) **and how the
  AI captain just re-set its field for it** ("Field changes: …", a
  polite live region; absent if nobody moved) -> one of 15 named shots ->
  intent (skipped for the two defensive shots) -> the web's 5-pulse timing
  minigame (buzz + tick, accented fifth) scored by
  `BattingSystem.computeTimingTier` -> a result step with tier +
  early/late feedback + Continue. `generateDelivery` now returns a
  `DeliveryReveal(bowling, fieldNote)`, and the caller is responsible for
  putting the field into the match state. Design points worth knowing:
  - **No Back after the reveal** (re-picking footwork knowing the ball
    would defeat the blind commit).
  - **The timing step is one full-screen `clickable` node, the only
    focusable element there**, so TalkBack focus lands on it and a
    double-tap anywhere swings. (Under touch exploration a double-tap
    delivers an accessibility click action, not raw pointer events, so
    `pointerInput` would not work.)
  - **Precise timing** against a fixed theoretical schedule with
    `SystemClock.elapsedRealtime()`, pulses scheduled against the start
    time, not chained.
  - **Deliberate deviation from the web:** with touch exploration on,
    the lead-in before pulse 1 is 1400ms (web: 550ms) so TalkBack's
    speech finishes before the rhythm starts. Needs tuning on device.
- **`FieldingScreen`** — the web app's two-phase pick (which fielder,
  then where) as pure single-swipe lists, no custom gesture:
  - OVERVIEW: the nine fielders as "Position — Name" with fielding
    rating, a legality line, and **Set field above the list**, then the
    three **presets** (Normal, Defensive, Attacking) — each one item
    reading its name and a one-line description. Applying one changes
    only the LOCAL arrangement, like a manual move. They are built from
    the AI captain's templates (balanced 4 in the deep, containing 5,
    attacking 2), so a preset hands the user the AI captain's
    arrangement, best fielders in the catching spots. **Always legal:**
    balanced and containing exceed a powerplay's cap of 3 in the deep, so
    the least important boundary riders are pulled in until it fits,
    keeping the three distinct.
  - SECTOR: the 12 sectors (the web has one flat list of 23 slots; two
    levels means at most 12 + 3 swipes). DEPTH: only when a sector has
    more than one slot. Moving onto an occupied slot swaps, like the web.
  - One polite live region reports each move or preset in full. An
    illegal field can still be set (web parity) but is called out on both
    screens: every delivery is a no-ball until fixed.
  - Read-only mode is the web's "browse the AI's field", reached from Hear
    the field while batting; it shows the field set for the current or
    last delivery.
  - Valid depths per sector are derived from `FieldingSystem.toggleDepth`
    because the source table is private.

## Fixed

**`PitchingScreen` release timing.** `ReleaseStep.handleRelease` used to
estimate elapsed time as `pulsesFired * intervalMs`, which only changed
when a pulse fired: a tap between pulse 3 and 4 scored as maximally
early, and any tap after pulse 4 (up to the 1.5-interval grace timeout)
scored as exactly perfect. The step now measures real elapsed
milliseconds with `SystemClock.elapsedRealtime()` against a fixed
schedule. A second, worse problem was fixed in the same rewrite: the old
Release *button* sat several swipes from the first TalkBack focus stop
while the whole rhythm plus its auto-miss timeout finished in roughly
1.0-2.1 seconds, so with TalkBack it was effectively unplayable. It is
now a single full-screen `clickable` node, the only focusable element,
with a longer lead-in under touch exploration. Behaviour changes: first
buzz at 550ms (1400ms with a screen reader); the final buzz is the 4th at
lead-in + 3 intervals; no Back on the release step.

**Scorecard gaps.** Maidens were never counted and the wickets list had
no team score and no true order. Both fixed in `MatchStats` with additive
changes — see PORTING_NOTES.md.

**A dead end in bowler selection** (the web's list can be empty) —
`MatchSimulation.bowlerChoices` widens it.

**The end of the match was silent** — the result and innings-break
screens now carry the last ball themselves.

**A flat, situation-blind AI** — fixed by porting the web's formulas
(`AiSituation.kt`).

**Leaving a match threw it away** — first behind a confirmation, and now
the match is autosaved and Leave match is Save and leave.

**The batter was never told the AI's field.** See `prepareAiDelivery`
above: the AI's per-delivery field was set silently after the batter had
committed; it is now set and announced before the shot is chosen.

**All the timing buzzes felt identical** — each pulse now also ticks, with
the final pulse accented, so the beat can be found by ear.

**Rotating the phone destroyed the match** — `configChanges` in the
manifest; and the process being killed no longer does either, now that the
match is autosaved.

## Known issues / needs a real device

**Nothing written in the last ten sessions has been compiled or run** —
everything in `audio/`, `persistence/`, `ui/settings/`, `BattingScreen`,
`FieldingScreen` (incl. presets), the `PitchingScreen` rewrite,
`MatchLines`, `ScorecardScreen`, `SelectionScreens`, `MatchFlowScreens`,
`AiSituation`, `FieldPresets`, the `MatchStats` additions and the
`MatchScreen`/`MatchSimulation` wiring. They were written against the APIs
as read from the repo; expect a first-build pass to fix small things.
Compiling is deliberately deferred until the code is feature-complete —
which it now is.

**Saving and resuming — what to check and what to know:**
- **Reflection is the risk.** Gson builds Kotlin data classes without
  running their constructors, so a field missing from the JSON would be null
  in a non-null property. That can't happen for a file this code wrote
  itself under the same version, and `load()` checks the critical pieces,
  but the whole save/load round trip has never run: a `Stadium`,
  `TossResult` or `WeatherSnapshot` with an unexpected field type, or a
  second sealed type hiding in `MatchState`'s tree, would only show up
  then. **The first real test is: play a few balls, leave, resume, and
  check the score, the batsmen, the bowler and the field are all as they
  were.**
- Turning on **release minification** later would rename the classes Gson
  reads by field name, breaking every save (keep rules for
  `com.cricketsim.logic`).
- **Only one match can be saved.** Playing a new match's first ball
  overwrites it. There is no Discard button: a save that somehow can't be
  resumed can only be replaced by playing a new match (a corrupt file is
  ignored, not fatal).
- Resume always opens on the ordinary match screen. If the app was killed
  mid-delivery, that ball simply hasn't happened, except that a batter's
  footwork commit has already made the AI set its field for it, which
  is harmless.
- The saved match survives an app update only while `CURRENT_VERSION`
  is unchanged.

**Audio assets — status and what's left:**
- **Getting the files into the repo is a one-time step for the repo
  owner.** `tools/fetch_audio.sh` is committed and works; the workflow
  that runs it on GitHub is staged as `tools/fetch-audio.workflow.yml`
  and must be moved to `.github/workflows/fetch-audio.yml` on github.com
  (the token used to write this repo is refused write access to that
  folder). It then runs itself and commits the files. Check for
  `tools/audio_fetch_report.txt` to see whether it has happened. Until
  then the recordings download to the phone on first run and the clips
  stream, so nothing is broken — it just needs a connection.
- **Check the crowd recording's licence** before it is committed and
  shipped: its file name suggests a third-party stock source, and
  bundling puts it in this repo's history and in every APK.
- **30 commentary clips don't exist anywhere yet** (partnership 150/200,
  bowler 3/5/10-wicket hauls, every hat-trick line). The web app has an
  endpoint that generates clips (`commentary_tts_generate`); generating
  them there, then re-running the fetch, is what fills the gap. Until
  then those moments have no voice, as on the web.
- Bundled audio adds about 10 MB to the APK (1.0 MB recordings, 9.3 MB
  clips), and is committed to git history permanently.

**Audio device checks:**
- **Commentary vs TalkBack.** The AI voice clips play at about the same
  moment TalkBack reads the last-ball live region. The web has the same
  overlap and was tested with a real screen-reader user, and TalkBack's
  audio ducking should help, but it needs real ears. The setting to turn
  the voice commentary off (or to one voice) exists for a reason.
- **Output latency.** The timing tick goes through `AudioTrack`; Android
  audio output has latency the web doesn't (tens of ms, device-dependent),
  and the timing score is measured against the SCHEDULED pulse, so a
  player tapping on the sound will be systematically late. The buzz is
  unaffected. If it matters, use the two `*_INPUT_LATENCY_COMPENSATION_MS`
  constants (both still 0).
- **Loudness.** The crowd gain is the web's (0.09-0.2, times the crowd
  volume and a 0.5 master), so it is quiet by design; the synthesized bed
  is normalised to the same scale. It may be too quiet on a phone speaker.
- Until the recordings are bundled, the first ball or two may use
  synthesized stand-ins while they download.
- Does `MediaPlayer` speed change (crowd tension) behave, on a looping
  player, without restarting it?
- With TalkBack off, does the spoken commentary read once, cleanly? With
  TalkBack on, does it stay silent?
- **Field-change announcements can be long** (up to nine sentences when
  the AI resets its whole field), read while the batter is trying to
  choose a shot. The web announces them all too; consider summarising a
  big reset.

**Game balance on Android is untested.** The AI now uses the web's
situational formulas.

TalkBack/device checks for the two timing surfaces (`BattingScreen`,
`PitchingScreen`):
- Does focus land on the single timing node, and does a double-tap
  anywhere act? Does the short spoken label plus click label finish
  before the first buzz (1400ms lead-in)? Does anything get announced
  when the decorative buzz text changes despite its cleared semantics?
- Touch-to-click latency under TalkBack (batting's Perfect window is only
  ~30-60ms either side of the beat).
- The timing logic is duplicated between `BatTimingStep` and
  `PitchingScreen.ReleaseStep`; extract a shared composable when either
  next changes.

TalkBack checks specific to `BattingScreen`:
- Does TalkBack announce the delivery heading and the "Field changes" line
  when the shot step appears (heading + live regions, no explicit focus
  request)? In the right order, without double-reading?

TalkBack checks specific to `FieldingScreen`:
- After a move or a preset, does the polite status message get spoken
  once and in full when focus lands back on the overview?
- Does each preset button (two `Text`s inside a `Button`) read as ONE
  item with its description, or as two stops?
- The editable overview has six stops before the fielder list. Too many?
- Does `Back (discard changes)` get pressed by accident?

TalkBack checks specific to the match screen, scorecard, selection
screens, settings, the first screen and the "play has stopped" screens:
- Does the Resume saved match button read as ONE item with its summary
  (teams, format, innings, score), and is it what TalkBack lands on first
  after the heading?
- The last-ball and score live regions live inside a Column that is
  removed while another screen is showing and re-added afterwards. Is
  their text spoken when they come back, and in the right order? The same
  applies to the "Play resumes." notice, and to a resumed match.
- After a wicket the match screen is replaced by `NewBatsmanScreen`, so
  the wicket commentary line is not heard until you return. Is the
  heading enough?
- Does the heading of each of the rain, innings-break, result and
  leave-confirmation screens get read on arrival?
- Do the `Role.Tab` selectors, `Role.Switch` rows, `Role.RadioButton`
  rows and the crowd-volume slider announce with their roles and state?
- After picking in a selection screen, where does TalkBack focus land?
- The win probability line only changes in steps.

## Not started

Everything the web app does is now in, with sound and saving. Remaining:

1. **A build.** Nothing here has ever been compiled; the APK build
   (signing, emulator/device) is still untouched. Deliberately deferred
   until feature-complete — which it now is. A CI workflow that builds a
   debug APK on every push would surface compile errors immediately (and,
   like the audio workflow, would need the same one-time move into
   `.github/workflows`, because of the token's restriction).
2. **Finish shipping the audio** (see Known issues): move the workflow
   into place so the files get bundled, check the crowd recording's
   licence, and generate the 30 missing commentary clips on the web app.
3. Real-device tuning of everything flagged above — the save/resume round
   trip, audio latency and loudness, TalkBack behaviour of every screen,
   game balance.
4. Small things deferred on purpose: a Discard saved match button, more
   than one saved match, the field-change announcement summary, extracting
   the duplicated timing logic.

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
