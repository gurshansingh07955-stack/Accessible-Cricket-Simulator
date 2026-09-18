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

- **Navigation scaffold** (`app/src/main/java/com/cricketsim/ui/Screen.kt`)
  — a plain sealed interface switched on in one composable
  (`MainActivity.CricketSimApp`), not the Navigation-Compose library.
  The setup flow is short and linear (format -> stadium -> teams ->
  playing XI -> toss -> match), so a hand-rolled `when` is simpler than
  a nav graph for now. Revisit once persistence (see
  PORTING_NOTES.md's "What's next") makes deep-linking into an
  in-progress match a real requirement.
- **`FormatSelectionScreen`**
  (`app/src/main/java/com/cricketsim/ui/setup/FormatSelectionScreen.kt`)
  — step 1 of the setup flow (matches pages/play-match.tsx's step
  order in the web app). The first real gameplay screen; establishes
  the patterns above in practice: a single-column `LazyColumn` of
  format options, each using `Modifier.selectable(..., role =
  Role.RadioButton)` with the `RadioButton` itself set to `onClick =
  null` so the whole row (not just the small radio circle) is the tap
  target — this also matters for switch-access and limited fine motor
  precision, not only screen-reader use.
- **`StadiumSelectionScreen`**
  (`app/src/main/java/com/cricketsim/ui/setup/StadiumSelectionScreen.kt`)
  — step 2 of the setup flow (matches pages/play-match.tsx's step 2),
  the country-filtered picker over `StadiumData.kt`'s 101 stadiums.
  Implemented as a two-step drill-down (pick a country, then a stadium
  within it) rather than a combined dropdown-plus-inline-list, so both
  steps stay flat, single-swipe lists rather than nesting an expandable
  control inside a list item. Rows use `Modifier.clickable(..., role =
  Role.Button)` rather than `selectable`/`Role.RadioButton` — unlike
  format selection, picking a country or stadium here is a one-way
  navigation action, not a persistent selection sitting next to
  unchosen alternatives on screen.
- **`TeamSelectionScreen`**
  (`app/src/main/java/com/cricketsim/ui/setup/TeamSelectionScreen.kt`)
  — step 3 of the setup flow: pick the user's own team, then the
  opponent, from `CricketData.kt`'s 18 national squads. Same two-step
  drill-down shape and `Modifier.clickable(..., role = Role.Button)`
  pattern as `StadiumSelectionScreen`. The opponent list excludes
  whichever team was just chosen as the user's own, so the user can
  never end up facing their own side.
- **`PlayingXIScreen`**
  (`app/src/main/java/com/cricketsim/ui/setup/PlayingXIScreen.kt`)
  — step 4 of the setup flow: pick the user's Playing XI from their
  full squad, then designate Captain / Vice-Captain / Wicketkeeper
  from that XI (`CricketData.buildMatchSquad`). Four sequential
  sub-steps, each its own flat list: a genuine multi-select squad step
  (`Modifier.toggleable(..., role = Role.Checkbox)`, with an "N of 11
  selected" counter marked `liveRegion = LiveRegionMode.Polite` so a
  screen-reader user hears the running count without navigating back
  to it), then three single-pick-and-advance steps using the same
  `Modifier.clickable(..., role = Role.Button)` pattern as
  `StadiumSelectionScreen`/`TeamSelectionScreen`. Wicketkeeper
  candidates are filtered to specialist keepers within the chosen XI,
  falling back to the full XI only if none exist. The opponent side
  never gets a selection screen — `MainActivity` auto-picks its XI via
  `CricketData.autoSelectPlayingXI` right after the user's XI is
  confirmed, matching the original design intent.
- **`TossPlaceholderScreen`** (same directory) — a deliberate stand-in
  for step 5, not a real screen. Exists only to prove the navigation
  flow works end to end after Playing XI selection, and receives both
  teams as already-finalized 11-player XIs. **Replace, don't extend**
  — the real version needs `MatchEngine.simulateToss` plus a bat/bowl
  decision when the user wins.
- `MainActivity.kt` now hosts this flow (format -> stadium -> team ->
  playing XI -> toss placeholder) instead of the old logic-loading
  status screen.

## Not started (the rest of the setup flow, in the web app's own step order)

1. Real toss screen (replacing `TossPlaceholderScreen`):
   `MatchEngine.simulateToss`, plus a bat/bowl decision when the user
   wins the toss.
2. The match screen itself — by far the largest remaining piece:
   live score display, the three custom gesture surfaces (pitching,
   batting, fielding), scorecard, commentary display, win-probability
   display, rain-delay dialog, wicket/new-batsman flow, bowler-change
   flow. Nothing here has been started.
3. Persistence-dependent UI (resume-match prompt, save indicator) —
   blocked on the Android persistence layer itself (see
   PORTING_NOTES.md).

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
  Galaxy A23).

**Keep this file in sync**, same discipline as PORTING_NOTES.md: update
it in the same session/commit as the UI work, not as an afterthought.
