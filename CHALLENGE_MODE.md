# Challenge mode (patch 161)

Home > **Challenge mode** (after Tournament). **Five new challenges every day**, new at midnight (the phone's date); each is a T10, T20 or ODI between two international teams at a random ground, with both Playing XIs and the toss decided by the game (the XIs are the game's auto-pick; there are no XI or toss screens). **Winning the match completes the challenge.** Every day has at least one T10, one T20 and one ODI.

Four kinds (`ChallengeKind`):

* **Chase from the start**: "Chase 216 in 50 overs". The other side has batted; the match opens at the innings break and you bat.
* **Defend from the start**: "Defend 180 in 50 overs". You have batted; you bowl.
* **Chase from the middle**: "Pakistan made 287 for 6 in 50 overs. India are 128 for 4 after 25 overs and need 160 more from 150 balls (6.4 an over). Take over the chase."
* **Defend from the middle**: the same, with you bowling.

Each challenge page shows the situation, the teams, the ground, the toss and **your chance of winning at the start** (`WinProbability`), then Start challenge (or Try again, or Resume challenge and Start again from the beginning for a match left half way). The list shows Completed / Not completed / Not played, today's count and the all-time count.

**How a challenge is made** (`logic/Challenges.kt`). `ChallengePlanner.plan(date)` picks the five from the date alone (the same all day): kind, format, two teams, ground, and a planned first-innings total near the par for that format and ground (T10 about 0.62 of a T20, ODI about 1.78; defended totals run from about 0.78 to 1.08 of par, chased ones from 0.95 to 1.30). `ChallengeBuilder.build` then really plays the first innings with the computer on both sides (`MatchFastForward`, the engine behind "Simulate match"), again and again until the total is within 7 percent of the plan (10 seconds at most, then the closest try is kept), and for the "middle" kinds plays the second innings on to the planned over, retrying until it is a real contest (at most 6 wickets down, a required rate between 3 and 11 an over in an ODI, 15 in a T20, 18 in a T10). So the scorecards, bowling figures and batters at the crease are real, and **the numbers in a challenge are the ones the simulation produced, close to but not exactly the planned total**. The built match is saved as a start snapshot (`challenge_start_<id>.json`) so Try again always begins from the same position; the match being played saves itself (`challenge_play_<id>.json`).

* The list opens at once; the challenges of a new day are built one after another in the background (a few seconds each; a row says "Preparing this challenge." and is announced when ready). Building waits while a challenge is being played. A challenge that could not be built says so and is tried again the next time the screen opens.
* Saved in `challenges.json` (`persistence/ChallengeStore.kt`): today's challenges, results, tries, and the all-time totals. A new day deletes the old day's saved matches.
* **Not built**: very extreme targets (a first innings of 400 needs a stronger batting side than the ball model makes by chance), rewards or streaks, challenges that start inside the last overs, choosing the XI or the toss.
