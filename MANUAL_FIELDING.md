# Manual fielding

**Settings > Manual fielding:** Automatic fielding, Half manual fielding, Full manual fielding. Part one is patch 117 (and its build fix 118), part two patch 119.

| | Automatic | Half manual | Full manual |
|---|---|---|---|
| Catches | the computer (and it sometimes drops one) | you | you |
| Stumpings | the computer | the computer | you |
| Run outs | the computer | the computer | you |
| Singles and doubles | the computer | the computer | you stop them |

Every chance is a full-screen page (`ui/match/ManualFieldingScreens.kt`) that comes by itself when your team is fielding:
1. **Your phone's own voice** (`audio/PhoneSpeech.kt`, always on, with or without TalkBack) says who, where, his fielding rating and what to do. The buzzes start only after it has finished.
2. **Buzzes** (a vibration and a tick each, the last accented: the batting timing system). The gap follows the **speed of the ball** and is shaped by the **timing of the shot** (a perfectly timed shot is 0.85 times the gap, a very bad one 1.2 times; 3 buzzes for a well-timed shot, 5 for a mistimed one that hangs; an edge is 3 quick buzzes; a fielder in the deep has one buzz more).
3. **Your swipe** (two fingers with a screen reader, one or two without) on the last buzz. A better fielder forgives more: the allowed distance from the last buzz is 0.30 + 0.004 x rating of one gap (0.54 at 60, 0.62 at 80), plus 0.05 for a stop.
**Where the mode is told (patch 123):** on the Match info page, as its first section ("Fielding"), not on the match screen. The match screen shows nothing about it any more.

**The 2-second window (patch 121):** after the last buzz the swipe still counts for 2 seconds (`ManualCatch.LATE_GRACE_MS`), so there is time to swipe properly. Only a swipe before the last buzz can be "too early"; inside the 2 seconds nothing is "too late". The way (up, left, right, down) must still be right. Set `LATE_GRACE_MS` to 0 for the strict timing.
4. "Let the computer take it" (the last button) leaves it to the computer.

## What each chance is
- **Catch** (Half and Full): swipe **up** if it comes from straight on, **left** from the left, **right** from the right (which side is worked out from where the fielder stands and where the shot went). A catch put down is **not a wicket**: the batter stays, 0 or 1 run for a catch near the bat, 1 or 2 for one in the deep.
- **Stumping** (Full): the ball was a stumping. The keeper (his rating counts) swipes **down** on the last buzz. Missed: **not out**, no run.
- **Run out** (Full): the ball was a run out. **Down twice** within 0.7 s, the first on the last buzz. Missed: **not out**, the run he ran counts.
- **Stop** (Full): **every ball the computer scores 1, 2 or 3 runs off, except an edge** (a drive or a glance along the ground, a lofted shot that landed safe, a push or a block that ran for a single; patch 119 left out the pushes, the blocks and the lofted shots). The fielder nearest to it in the shot's direction (not the keeper or the bowler; for a push, a fielder close in front of the bat) is yours: swipe up, left or right. Stopped: **one run fewer**. Missed: the runs stand. (This can be a chance on nearly every scoring ball, so a long innings takes longer in Full manual.)
A taken catch, a stumping or a run out carries on exactly as before (appeal, review, wicket).

## Dropped catches in Automatic fielding
Every caught dismissal can be dropped by its fielder, by his fielding rating (`CatchDrops.dropChance`: about 6% at 95, 21% at 60, 34% at 30), at most twice an innings, so an innings has none, one or two. Applies to the computer's fielders and to yours when you field automatically (and when you use "Let the computer take it" on a catch). **Balance:** about 57% of wickets are catches, so a fifth of those now survive: roughly one wicket an innings fewer in T20 than before. Not measured on play. `CatchDrops.AUTO_DROPS = false` switches it off; `MAX_DROPS_PER_INNINGS`, `DROP_BASE`, `DROP_BY_RATING` are the dials. In Full manual the computer's stumpings, run outs and stops are not dropped or changed except by what you do.

## Commentary
Dropped catch, missed stumping, missed run out and a good stop have their own **recorded commentator lines** (`CommentaryCategory.DROPPED_CATCH`, `STUMPING_MISSED`, `RUN_OUT_MISSED`, `GOOD_STOP`: 6, 4, 4 and 4 pairs, clips `dropcatch_*`, `stumpmiss_*`, `runoutmiss_*`, `goodstop_*`) and text lines with the names (the ball's text, read by the phone or a screen reader). `BallOutcome.droppedCatch` and `BallOutcome.fieldingEvent` say which. After this patch run "Fetch audio into the app", then "Build APK".

## Career mode
Manual fielding works **only for your own player, while you are watching**: the chances that come to him (a catch he takes, a stumping if he keeps wicket, a ball he stops). Your team-mates' and the computer's chances are never yours. (Patch 120; patch 117 had given you every team-mate's catches too.) Everywhere else (quick match, tournaments, leagues) all your team's chances are yours. You field while you are watching. "Skip to my turn" hands the fielding to the computer, except for the wicketkeeper: if the career player is his team's keeper he still takes his own catches, his stumpings and anything the ball brings to him. Computer-v-computer tournament matches and the quick sim have no fielding chances.

## Not done
Stopping a boundary (a four along the ground), a keeper's own byes (the game has none), measuring how Full manual changes the scoring.
