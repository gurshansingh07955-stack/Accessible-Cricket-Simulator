# My Player, part twelve: injuries, ageing, retirement and the career summary

**Injuries** (`CareerHealth.roll`). After each match he plays there is a chance of an injury: about 1.2%, times 1.8 in Test cricket and 1.2 in one-day cricket, 1.4 for bowlers and 1.15 for all-rounders, +6% for every year over 29, and +1/60 for every match played this season (the workload).
Capped at 10%. A minor injury is 2 or 3 matches, a moderate one 4 to 8, a serious one 10 to 18. The match report says what happened. While injured the selectors do not pick him (the reason says what is wrong and for how many matches); you can simulate the match instead.
Each match his side plays without him counts off one. The close season heals six matches. The Home tab's pathway and the Career tab's Fitness section show the state of his fitness; the workload resets every season.

**Ageing** (`CareerHealth.aged`). When a season ends, from 33 his batting and bowling fall by (age - 32) / 3 (1 at 35, 2 at 38, 3 at 41) and his fielding by half of it.

**Retirement** (`CareerHealth.retirementDecision`). At the end of a season: 12% at 36, growing by 12% a year (60% at 40, certain at 44), or 50% from 33 if his main rating is under 40. The draw comes from the career and the season, so reopening the game does not change it. He can also retire himself
(Career tab, Retirement: a confirmation first; offered when the season is finished). A retired career cannot start a season; the Career tab opens with the **career summary**: seasons and ages, the highest level, the record, honours, final ratings and a verdict
(a true great, a career to be proud of, a solid career, a short but honest career) from his matches, honours, runs and wickets.

**Dials.** The injury chances and severities in `CareerHealth.roll`, the decline in `aged`, the retirement ages and chances in `retirementDecision`, the verdict thresholds in `summaryLines`.
