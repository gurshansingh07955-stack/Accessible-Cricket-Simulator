# Rain in Test and first-class matches; share and copy a finished tournament

**Rain in a Test (and every first-class match: Ranji, Duleep, Irani, County Championship, Sheffield Shield, 4-Day Series,
Plunket Shield, West Indies Championship, Quaid-e-Azam, National Cricket League).**
At the end of every over `TestMatch.rainStoppage` rolls for rain, from the match's chance of rain (the weather shown on
Match info; light rain on the day makes it likelier). The dial is `RAIN_FACTOR`: expected stoppages per match = 2 x the chance
of rain, at most `RAIN_MAX_STOPS` (4), never in the last 18 overs. A stoppage loses 8 to 20 overs (half the time), 20 to 40
(a session, a third) or the rest of the day (one in seven). Lost overs run the match CLOCK (`clockBalls` = balls bowled + balls
lost): sessions, lunch, tea, stumps, the day count, the overs that remain and the draw all move with it, so rain makes draws
likelier. A "Rain delay" pop-up (break phase 3) says what was lost and when play resumes; rain sound and commentary play, and
"Resume play" ends it. A drawn result says "Rain cost N overs." Match info shows the overs lost. There is no overs cut and no
DLS in a Test. Computer-v-computer first-class matches roll for rain too (the stoppage is applied and play goes on).

**Share and copy.** The celebration screen (shown once when a tournament ends) has two more buttons: "Share tournament
summary" and "Copy tournament summary". The plain text (`TournamentSummaryText`) has: champions, runners-up, the Final, the
Player of the Match in the Final, the Player of the Series (and the IPL's Orange and Purple Caps), your team's record, the
points table or tables (first-class tournaments show points, drawn and won), and the top five of every statistic.
