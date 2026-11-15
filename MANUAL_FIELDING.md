# Manual fielding (first part, patch 117)

**Settings > Manual fielding:** Automatic fielding (as before), Half manual fielding, Full manual fielding. Full manual works like half manual until its own part arrives (stopping singles and doubles, stumping, run outs).

## Taking the catches yourself (Half and Full manual)
When your team is fielding and the ball is a catch (including a catch back to the bowler), a full-screen page comes by itself (`ui/match/ManualFieldingScreens.kt`, `CatchScreen`):
1. **Your phone's own voice** (`audio/PhoneSpeech.kt`, always on, with or without TalkBack): "Virat Kohli is standing at long-on. Fielding rating 82. The catch is coming from the left. Swipe left with two fingers on the last buzz." The buzzes start only after the voice has finished.
2. **The buzzes** (a vibration and a tick each, the last one accented: the same `vibratePulse` and `playTimingTick` as batting). The gap between them follows the **speed of the ball** (`BattingSystem.computeTimingIntervalMs`) shaped by the **timing of the shot**: a perfectly timed shot (0.85 times the gap, 3 buzzes) arrives sooner than a mistimed one that hangs in the air (1.2 times, 5 buzzes); an edge is 3 quick buzzes; a catch in the deep has one buzz more.
3. **Your swipe** on the last buzz: up if the catch comes from straight on, left if from the left, right if from the right (`CatchDirection`; which side is worked out from where the fielder stands and where the shot went). A wrong direction, too early, too late or no swipe drops the catch. A better fielder forgives more: the allowed distance from the last buzz is 0.30 + 0.004 x rating of one gap (0.35 to 0.72, so 0.54 at a rating of 60 and 0.62 at 80).
4. "Let the computer take it" (the last button) leaves the catch to the computer's fielder, for anyone who cannot swipe.
A taken catch carries on exactly as before (appeal, review, wicket). A dropped one is **not a wicket**: the batter stays, 0 or 1 run for a catch near the bat, 1 or 2 for one in the deep.

## Dropped catches in Automatic fielding
Every caught dismissal can be dropped by its fielder, by his fielding rating (`CatchDrops.dropChance`: about 6% at 95, 21% at 60, 34% at 30), at most twice an innings, so an innings has none, one or two. Applies to the computer's fielders and to yours when you field automatically (and when you use "Let the computer take it"). **Balance:** about 57% of wickets are catches, so a fifth of those now survive: roughly one wicket an innings fewer in T20 than before. Not measured on play. `CatchDrops.AUTO_DROPS = false` switches it off; `MAX_DROPS_PER_INNINGS`, `DROP_BASE`, `DROP_BY_RATING` are the dials. A dropped catch's commentary is text (six wordings, spoken by the phone / screen reader); it has no recorded commentator voice yet.

## Career mode
You take the catches while you are watching. "Skip to my turn" hands the fielding to the computer; a wicketkeeper (the career player is his team's keeper) still takes his own catches while skipping. Computer-v-computer tournament matches and the quick sim do not have catches to drop.

## Not in this part
Stopping singles and doubles, stumping (swipe down with two fingers) and run outs (swipe down twice), the recorded commentators' lines for a dropped catch.
