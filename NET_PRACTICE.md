# Net practice (patches 135 and 136)

Home > Net practice (placed before Settings). Batting practice (patch 135) and bowling practice (patch 136).

## Batting practice
Net practice > Batting practice > Spin or Fast. Fast asks for a speed band (110 to 120, 120 to 130, 130 to 140, 140 to 150, 150 and above). Then the first and second batsman, chosen from the game's batsmen rated 90 or more (read live from the squads, so rating edits count). Then the instructions page, then the session.

- Spin bowlers: Kuldeep Yadav, Ravindra Jadeja, Rashid Khan, Sikandar Raza, Nathan Lyon, Adam Zampa. Fast bowlers: every pace bowler rated 90 or more. A bowler is chosen at random each over, never the same twice running.
- The batsmen take turns, an over each (six legal balls). Wides and no-balls do not count towards the six.
- Balls are real: footwork on "Face next ball" (or the Settings alternatives), then the real BattingScreen, then MatchEngine.simulateBall at the Settings difficulty. The state given to the engine never advances: a wicket is counted and the batter carries on.
- After every ball the coach speaks: the match's own ball summary, then footwork, shot choice and a timing fix (early or late). Before every ball, with "Coach tips" on, he names the foot and the shots that suit the ball.
- Stop practice gives a summary: runs, balls, outs, fours, sixes, perfect timings, shots that suited the ball, early or late habit.

## Bowling practice
Net practice > Bowling practice > Fast or Spin > choose the bowler (fast: every pace bowler rated 90 or more; spin: the six spinners above) > instructions > the session.

- The batsman is chosen at random from the 90-and-over batsmen, a new one every over (six legal balls). The computer batter plays exactly as in a T20 match (`BattingSystem.generateAiBattingDecision`, never leaving).
- The ball is bowled on the real PitchingScreen and resolved by MatchEngine.simulateBall at the Settings difficulty (so the Easy and Medium bad-shot wicket help applies).
- The user sets the field with the real FieldingScreen (presets, then moves). The Field rules button switches the practice between normal rules (at most 5 in the deep) and powerplay rules (at most 3). An illegal field makes every ball a no ball with a free hit after it, exactly as in a match, and the coach says so.
- While dragging the length, with "Coach tips" on, the game says the ball quality as it changes (Perfect Ball, Ideal Ball, Good Ball, Bad Ball, Very Bad Ball), and says it again when the length is set (it uses the speed on the slider at that moment). `PitchingScreen` has a new parameter `coach` (false in a match, so a match is unchanged).
- After every ball the coach gives the ball's quality, the length aimed for and the length bowled, and the two biggest losses of quality the game scores: where the drag ended (length), an overshoot pulled back (smoothness), a speed too high to control (speed). The summary names the bowler's biggest loss across the session.

## Where things are
- `logic/NetPractice.kt`: the lists, the practice states, the deliveries (`deliveryFor`), the coach (`coachTip`, `feedback`, `bowlFeedback`) and the summaries.
- `ui/practice/NetPracticeScreens.kt`: `NetPracticeFlow` and every page.
- `NetPractice.NET_PRACTICE_ENABLED` switches the Home button off. `NetPractice.MIN_RATING` is the 90.

## Known limits
- Batting: the coach's tip is delivered through the "Field changes" slot of the delivery announcement, so the screen reader says "Field changes: Coach tip: ..." (a later patch can give it its own label).
- Fast bands use a stock delivery only (a slower ball would not fit a fast band).
- The practice ground is a balanced pitch with the bowler's national side in the field. Bowling practice has no powerplay switch other than the Field rules button.
- The live ball quality while dragging is the quality the ball would have at the length and speed at that moment; changing the speed afterwards can change it.
