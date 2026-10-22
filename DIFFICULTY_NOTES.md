# Difficulty and engine balance

Switch: `MatchEngine.BALANCED_ENGINE` (true = balanced, false = the original rules exactly).
Measure it yourself: `node tools/difficulty_twin.js` (a twin of `MatchEngine.simulateBall`, no network needed).

## What players saw
Computer bowlers got no wickets, computer batters hit boundaries off perfect balls, defensive shots sometimes went
for four, 75 to 80 rated bowlers bowled many bad balls, and on Hard / Hardest the user could not score.
All of it is real, and measured (T20, balanced pitch):

| | Before | After |
|---|---|---|
| Perfect shot v perfect ball | 29% four, 12% six (2.4 runs a ball) | 10% four, 4.5% six (1.4 runs a ball) |
| Good shot v perfect ball | 10% four, 4% six | 6% four, 3% six |
| Forward defence (perfect timing) | four about 1 ball in 140 | about 1 in 500 |
| Computer bowler rated 80, bad + very bad balls | 38% | 18% |
| Computer bowler rated 90 | 34% | 10% |
| Computer v computer, equal skill | 234 runs, 3.5 wickets | 166 runs, 6.8 wickets |

User batting, runs / wickets lost (skilled | average player):

| | Before | After |
|---|---|---|
| Easy | 217 / 2.5 \| 160 / 4.1 | 154 / 6.1 \| 106 / 8.4 |
| Medium | 187 / 4.8 \| 129 / 7.7 | 143 / 7.2 \| 94 / 9.1 |
| Hard | 139 / 6.9 \| 86 / 9.3 | 132 / 8.1 \| 81 / 9.6 |
| Hardest | 79 / 8.5 \| 49 / 9.9 | 114 / 9.0 \| 67 / 9.9 |

User bowling, runs conceded / wickets taken (skilled | average player):

| | Before | After |
|---|---|---|
| Easy | 161 / 5.8 \| 177 / 5.5 | 155 / 7.7 \| 183 / 6.9 |
| Medium | 215 / 3.9 \| 239 / 3.6 | 168 / 6.7 \| 198 / 5.9 |
| Hard | 252 / 2.0 \| 278 / 1.9 | 182 / 5.6 \| 211 / 4.9 |
| Hardest | 276 / 1.0 \| 304 / 0.9 | 194 / 4.5 \| 223 / 4.0 |

"Skilled" and "average" are models of how well a human reads the ball, times it and lands his deliveries; real players
will differ. Field placement is not modelled, so the real game gets slightly more wickets than the table says.

## The loopholes (all fixed unless marked)
1. Difficulty was ADDED to the probabilities. On Hardest the wicket chance (user bowling) or the four and six chances
   (user batting) went below zero and were floored at 0.1%. Now difficulty is multiplicative, the same factor on both
   sides inverted: wickets x1.25 / 1.0 / 0.8 / 0.62 and boundaries x0.9 / 1.0 / 1.12 / 1.25 for the bowler's side.
2. The batting layers (footwork, shot fit, intent, timing) multiplied the wicket chance down to a few percent and
   boundaries up 3 to 4 times, and nothing depended on how good the ball was. Now the ball fights back
   (`BattingSystem.applyBallContest`); `CONTEST_STRENGTH` makes it stricter or weaker.
3. Defensive shots: boundaries cut to about a quarter of what they were. A wrong shot's wicket multiplier 17 -> 12.
4. Computer bowler quality ignored his rating (25 + rating x 0.45, flat +/-48 spread). Now 13 + rating x 0.70 with a
   triangular spread, so a 78 bowls about 20% bad or very bad balls, not 40%.
5. Computer batter: saw the real length and quality of every ball (now misjudges 20 to 45% of lengths) and his timing
   luck was a flat spread (now triangular around a slightly lower base).
6. Not changed: only the exact centre of a length band scores a perfect delivery for the USER, so Perfect is a narrow
   target for a human bowler; a Good delivery gives no bonus. Say if you want it widened.
7. Not changed: after two early wickets the computer batters defend heavily (`AiSituation.battingBias`), which keeps
   wickets from falling in clusters.

## Bad shots and more catches (patch 039)
**Bad shots on Easy and Medium** (`MatchEngine.BAD_SHOT_WICKETS`): when the computer batter's timing is BAD or VERY BAD and the user's
ball is GOOD, IDEAL or PERFECT, the chance of that ball being a wicket is raised to at most 70% (attacking shot) or 60%
(defensive shot): the full figure for a VERY BAD shot against a PERFECT ball, scaled to 35% for a BAD shot and to 65% / 40% for an
IDEAL / GOOD ball. A bad or very bad ball changes nothing; Hard and Hardest are untouched. Applying the full 70% / 60% to every
bad shot was measured first: the computer was bowled out in 96 to 99% of innings for 60 to 100 runs on Easy and Medium (about 1
ball in 6 qualifies), so it is scaled. Measured (user bowling, wickets taken, skilled | average | novice):

| | Before | After |
|---|---|---|
| Easy | 7.8 \| 6.8 \| 5.5 | 8.8 \| 7.8 \| 6.2 |
| Medium | 6.5 \| 5.8 \| 4.7 | 8.0 \| 6.9 \| 5.4 |

**More catches, all difficulties** (`MORE_CATCHES`, `CATCH_BOOST`): the mix of bowled / caught / lbw was 1 : 1 : 1, so a third of
wickets were catches. Now 4 : 2 : 1 (57% caught), 8 : 1 : 1 when a fielder is standing in the right place for an aerial shot, and 6 : 2 : 1
for a mistimed bad shot. A placed deep fielder also converts more aerial shots (boost 2.2, was 1.6), which adds a few wickets. The
twin does not model fielders, so that last part is not measured.

## Where to turn the dials
`MatchEngine.difficultyWicketFactor` / `difficultyBoundaryFactor`, `BattingSystem.CONTEST_STRENGTH`, the `0.70` in
`BowlingSystem.generateAiBowlingDecision`, and the misread chance and timing base in `generateAiBattingDecision`.
