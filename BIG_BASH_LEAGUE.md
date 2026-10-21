# Big Bash League 2026-27 (tournament), part 1

Tournament > League tournaments > Big Bash League 2026-27 (BBL|16). Same pages, saving and tabs as the CPL.

**Real facts used** (published by 15 October 2026): eight teams (Adelaide Strikers, Brisbane Heat, Hobart Hurricanes, Melbourne
Renegades, Melbourne Stars, Perth Scorchers, Sydney Sixers, Sydney Thunder), the 115 players confirmed so far (12 to 17 per team;
the clubs still had places open), and the **real fixture list**: 40 league matches in the published order at the real grounds
(including the Chennai opener), then the Qualifier (1st v 2nd), the Knockout (3rd v 4th), the Challenger (Qualifier loser v Knockout
winner) and the Final: 44 matches, T20. The league fixtures are the same every time.

**Grounds.** Ten of the eleven were in the game. Added to `StadiumData`: ENGIE Stadium (Sydney Showground), Junction Oval, and the
University of Tasmania Stadium (York Park), with estimated pitch and weather figures. The finals grounds were not announced; each final
is played at the home ground of the higher-placed side (an assumption).

**Players.** Anyone already in the game (roster, CPL, Abu Dhabi T10, LPL) is copied with his ratings (41 of the 115). The rest are
new with **estimated ratings** (the `nw(...)` lines in `logic/BigBashLeague.kt`). `Matt Short` is linked to the game's `Matthew Short`.

**Points.** The game's table gives 2 points a win (the real league's points rules were not checked).

**Power Surge (part 2).** Big Bash matches have the Power Surge (`logic/PowerSurge.kt`): two overs with the powerplay field limit
(at most three fielders in the deep). The BATTING side calls it at the start of any over from the 11th, once an innings.
- You batting: a **Power Surge** button appears on **Match info** (like the Impact Player) at those moments.
- The computer batting: it decides at the start of each over from the 11th (`PowerSurge.aiShouldTake`) and a **pop-up** tells you,
  because you must bring your field in before you bowl.
- Assumption: an uncalled Power Surge starts by itself at the start of the 19th over (the published rule has no deadline).
- The Big Bash powerplay is four overs (`PowerSurge.BBL_POWERPLAY_BALLS`), not six.
- Computer-v-computer matches use the usual choice (overs 15 and 16) inside `AiBallModel`.
**Not built:** the designated batter and fielder, and the over-rate timer rule.
