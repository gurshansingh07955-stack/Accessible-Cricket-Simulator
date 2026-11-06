# Custom formats of two to five days

**Create Custom Format** now asks, after the name: the number of days (1 to 5, default 1). One day is the limited-overs flow
as before. For two or more days the format plays like a Test match: overs per day (6 to 90), number of innings (two or four),
declarations on or off, follow-on on or off with the runs of lead that allow it (four innings only), and the new ball on or off
with the overs after which it is available. DRS and rain are the tournament's own settings and apply to these formats too
(rain: see TEST_RAIN_AND_TOURNAMENT_SUMMARY.md).

How it works: `CustomFormat` (days, testInnings, declarations, followOnEnabled/Lead, newBallEnabled/Overs) -> `TournamentRules.rulesForFormat`
makes a `MatchRules` with `testDays > 0` -> the match is a Test (`TestMatch.start(rules)` copies the settings into `TestState`).
`TestMatch` runs on the format's own day length: a day is three sessions (a third of the overs each, the last ending on the day's
last over), so lunch, tea, stumps, drinks, rain and the draw all follow it. Two innings: the second innings is the chase of the first
innings' total; a side that does not finish by the end of the last day draws. Computer-v-computer matches use
`CplSimulator.simulateFirstClass(rules = ...)`. In a custom tournament a drawn league match is shared (a point each, the table's
usual no-result rule); a drawn knockout match goes to the side ahead on the first innings.

The Test dials (`SCALE_*`, the new-ball effect, the computer's declaration rule) were written for 90-over days; the computer's
declaration thresholds are scaled by the day length, nothing else was re-measured for short days.
