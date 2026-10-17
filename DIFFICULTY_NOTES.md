# Difficulty and bowling balance

Written 2026-10-16. Switch: `MatchEngine.BALANCED_USER_BOWLING` (true = fixed, false = the old rules exactly).

## The problem
Players bowling could not get wickets. `node tools/difficulty_twin.js` is a twin of `MatchEngine.simulateBall`
for "computer batting v the user's bowling" (T20, balanced pitch, bowler 78, computer batters 82 down to 40).
Field placement is not modelled, so the real game gets slightly more wickets than the table shows.

Old rules, an average bowler (runs / wickets per innings): Easy 181 / 5.1, Medium 245 / 3.4, Hard 284 / 1.9, Hardest 310 / 1.0.

## The loopholes
1. **Additive difficulty.** The user-bowling branch did `wicket += modifier * 0.3`. On Hardest that is 0.05 - 0.06,
   below zero, so every later multiplier floored it at 0.001. Hard and Hardest were nearly wicketless and the batters
   hit far more boundaries.
2. **Perfect-information batter.** `generateAiBattingDecision` chose footwork and shot from the REAL length of the ball
   (after the bowler's mis-execution) and the quality tier. He nearly always played a shot rated Excellent for it:
   wicket chance x0.22, fours x1.9, sixes x2.0. Even "Forward Defense" is Excellent on a good length.
3. **Multiplier imbalance.** Best case for the bowling side is about x2.4 on the wicket chance (line 1.2, length 1.2,
   variation 1.1, swing 1.1, perfect quality 1.3). The batting side stacks footwork 0.7 x shot 0.22 x timing 0.4 = 0.06.
4. Bowling quality is hard to reach: only the exact centre of a length band scores 100, so Perfect (85+) is a narrow
   target, Good (50 to 69) gives nothing, and Bad / Very Bad actively help the batter. Not changed yet.
5. Collapse brake: `AiSituation.battingBias` drops to -0.55 .. -0.9 after two early wickets, so batters defend and
   the next wickets get harder. Not changed (it is realistic), but it is why wickets rarely come in clusters.

## The fix (only the computer batter against the user's bowling)
- Difficulty is multiplicative: wicket chance x1.25 / 1.0 / 0.8 / 0.62 and boundaries x0.9 / 1.0 / 1.12 / 1.25
  for Easy / Medium / Hard / Hardest.
- The batter misreads the length: 15% of balls for a top batter, up to 45% for a weak one (he reads a neighbouring length).
- The computer batter's wicket chance x1.9 and boundaries x0.6 (`AI_BATTER_WICKET_FACTOR`, `AI_BATTER_BOUNDARY_FACTOR`).

Untouched: the user's batting, the computer's bowling, computer-v-computer matches (`AiBallModel`).

New results, an average bowler: Easy 167 / 7.6, Medium 184 / 6.6, Hard 198 / 5.7, Hardest 211 / 4.7.
A skilled bowler gets about half a wicket more and 30 fewer runs; a novice about half a wicket less.
