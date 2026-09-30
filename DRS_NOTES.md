# DRS (Decision Review System) — design notes

Code: `logic/DrsSystem.kt` (rules, odds, AI, the tracking report),
`ui/match/DrsScreens.kt` (the review screen), `MatchScreen.kt`
(`advanceOneBall` / `commitBall` / `pendingReview`),
`MatchSimulation.overturnDismissal`, and `MatchState.drsReviewsUsed`.

## Rules as built

- **What can be reviewed:** the *batter's* dismissal, and only LBW, run out
  and stumped. Bowled and caught never can.
- **Run out / stumped are not in the game yet** (`DismissalType` is still
  `BOWLED, CAUGHT, LBW`). `DrsSystem.isReviewable` matches by enum **name**
  (`RUN_OUT`/`RUNOUT`, `STUMPED`/`STUMP_OUT`/`STUMPING`), so adding those
  entries to `DismissalType` makes them reviewable with no change to DRS.
  Each already has its own tracking sequence. When run outs are added,
  note that `simulateOneBall` still treats the **striker** as the batter who
  is out; a run out of the non-striker will need that generalised, and
  `DrsCase.batterName` in `MatchScreen.advanceOneBall` with it.
- **Two reviews per innings** for the batting side (T20; ODI also 2, Test 3
  — see `reviewsPerInnings`). Stored as reviews **used**
  (`MatchState.drsReviewsUsed`), reset in `switchInnings`.
  - Decision **overturned** → review **kept**.
  - **Umpire's Call** (LBW only) → out stands, review **kept**.
  - Decision **stands** → review **lost**.
- **User's batter out:** two buttons, "DRS review" and "Choose next batsman"
  (or "Accept decision" if it was the last wicket), with **10 seconds**. Run
  out of time = no review, nothing lost. With no reviews left the offer is
  skipped and it is announced.
- **AI's batter out:** it takes 1.5s to just under 5s to decide (whether or
  not it asks), then either asks or lets it go. No reviews left = it never asks.
- **The review itself:** always **10 seconds**, seven steps 1.5s apart (no-ball
  check, UltraEdge, ball tracking of pitching / impact / wickets, third
  umpire), then the verdict.
- **After a review:** overturned → "Continue" (user faces the next ball).
  Stands → "Choose next batsman" (or "Continue" if the innings is over). For
  the AI's reviews it is always "Continue".

## Odds — the batter's timing, not the ball

`DrsSystem.survivalChance(BattingDecision.timingTier)`:

| Timing  | Overturned |
|---------|-----------|
| Perfect | 70% |
| Ideal   | 60% |
| Good    | 50% |
| Bad     | 35% |
| Very Bad| 25% |

Never the bowler's quality tier (same enum, different meaning). The same table
applies to the AI's batters, using the timing tier the AI's own batting
decision produced.

## Tunables (all in `DrsSystem`)

`USER_DECISION_WINDOW_MS`, `AI_DECISION_MIN_MS` / `AI_DECISION_LIMIT_MS`,
`REVIEW_DURATION_MS`, `UMPIRES_CALL_SHARE` (0.25 — share of *non-overturned*
LBW reviews that come back Umpire's Call; **0.0 turns it off**),
`AI_REQUEST_BASE` / `AI_REQUEST_CAP` / `AI_LAST_REVIEW_FACTOR` (how keen the AI
is: 0.15 + survival chance, capped at 0.9, ×0.7 on its last review).

## How an overturn works

A ball is always simulated first, wicket included. If it is reviewable it is
**held back** (`pendingReview`), not applied. On the verdict it is committed
as simulated, or as simulated with `drsReviewsUsed + 1` (lost), or **replayed
from the state before the ball as a not-out** (`overturnDismissal` hands
`simulateOneBall` a not-out outcome via `outcomeOverride`, with the same
delivery and batting decision). So an overturn never has to undo a wicket, a
replacement batsman or an over change; all of that just doesn't happen.

## Audio

Six new sound effects (`RecordedAsset.DRS_*` in `AudioAssets.kt`, played from
`SoundEngine.kt`'s new "Decision Review System" section), and 16 new
commentary lines (`CommentaryCategory.REVIEW_*` in `CommentaryLibrary.kt`),
all generated via the Floot project's existing ElevenLabs pipeline
(`endpoints/sfx_generate_POST.ts` and `commentary_tts_generate_POST.ts`) and
uploaded to the same `$BASE_URL` host as every other recording. Along with
these, the 30 commentary clips this file's own doc comment used to list as
"pending generation" (partnership 150/200, bowler 3/5/10-wicket hauls, every
hat-trick line) were generated in the same pass — `CommentaryLibrary.kt` now
references 206 clips in total, and all 206 exist.

- **Third umpire check** (`DRS_THIRD_UMPIRE_CHECK`) — a short electronic
  scanning tone, played once via `playDrsReviewCheck()` right as the
  10-second process stage begins.
- **Heartbeat** (`DRS_HEARTBEAT`) and **background music**
  (`DRS_BGM`, deliberately quiet — `DRS_BGM_GAIN` sits well under
  `DRS_HEARTBEAT_GAIN`) — both generated at ~11s (the review is fixed at
  10s) and played as plain, non-looping `MediaPlayer`s via
  `startDrsReviewAudio()`, which also ducks the crowd; `stopDrsReviewAudio()`
  stops both and un-ducks, called when the process stage ends.
- **Firecracker** (`DRS_FIRECRACKER`), **lose** (`DRS_LOSE`), **Umpire's Call
  against** (`DRS_UMPIRES_CALL_AGAINST`) — one-shot recordings via the same
  SoundPool path as `THUNDER`/`BAT_HIT`. Which one plays is decided by who
  the verdict FAVORS, not by the verdict alone (see `DrsResultStage` in
  `DrsScreens.kt`): a decision standing favors whichever side is bowling; an
  overturn favors whichever side is batting. So `OVERTURNED` plays
  firecracker for the user's own review but `drsLose` for the AI's;
  `STANDS` is the reverse; `UMPIRES_CALL` plays firecracker when it favors
  the user and the distinct `drsUmpiresCallAgainst` when it doesn't (a
  separate sound from `drsLose`, as asked for).
- **Commentary** — `REVIEW_REQUESTED` fires once, at the same moment as the
  third-umpire tone and the heartbeat/bgm start. `REVIEW_OVERTURNED` /
  `REVIEW_UMPIRES_CALL` / `REVIEW_STANDS` fire on the verdict, chosen purely
  by what happened — same convention as every other category in
  `CommentaryLibrary` (a wicket line plays the same regardless of which side
  is happy about it); the SFX above is the part that reacts to who it
  favors, kept deliberately separate from the commentary category choice.

Regenerating any of this later: the endpoints take a shared secret (see the
schema files) and accept up to 6 commentary lines / 12 sounds per call.

## Not done / open

- **Untested on a device**, and **not compiled locally** — CI is the first
  compile. TalkBack behaviour of the timer and the spoken steps is a guess.
- The **fielding side cannot review** a not-out decision; only the batting side
  reviews, as specified.
- A review in progress is **not saved**; a resumed match replays that ball.
- There is no on-screen drawing of the ball path (text only, by design, for
  screen-reader use).
