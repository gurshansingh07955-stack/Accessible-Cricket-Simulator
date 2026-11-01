# Australian domestic cricket 2026-27: the Sheffield Shield and the One-Day Cup

Tournament > Domestic tournament now also lists the **Sheffield Shield 2026-27** (four-day matches) and the **One-Day Cup 2026-27 (Australia)**, 50 overs, the Dean Jones Trophy. Code: `logic/AusDomestic.kt` and `ui/tournament/AusTable.kt`. Own saved tournament each (`saved_tournament_shield2026.json`, `saved_tournament_audc2026.json`) and part-match files.

## What is real
- **Fixtures**: the published lists (cricket.com.au, 7 July 2026), in the real order. Shield: 10 rounds, 30 matches, each state plays ten (the pink-ball round 7 in February included). One-Day Cup: 21 league matches, each state plays seven. Finals: the Shield Final is five days from 25 March (the top two, at the top side's ground); the One-Day Cup Final is on 14 February.
- **The season is already under way in real life** (the Shield began on 7 October, the One-Day Cup on 18 September). The game starts both from the beginning and simulates every result; the real results are not used.
- **Teams**: New South Wales, Victoria, Queensland, South Australia, Western Australia, Tasmania. Squads: the 2026-27 state lists (Cricket Australia, ESPNcricinfo, Cricket NSW as of 3 October 2026), including the national-contract players (in real life they play only when free). Adam Zampa is in Queensland's squad (he switched from NSW in August). A player already in the game keeps his ratings; the rest are new with **estimated ratings**, and several rookies and lesser-known players have a guessed role.
- **Grounds**: the MCG, SCG, Gabba, Adelaide Oval, Junction Oval, Bellerive (Ninja Stadium) and Perth Stadium were in the game; **added** (estimated ratings): Cricket Central (Sydney), the WACA Ground, Allan Border Field and Karen Rolton Oval. NSW v Queensland in round 5 is listed as "SCG or Cricket Central": the game plays it at Cricket Central.

## Rules
- **Shield points**: 6 for an outright win, 3 for a tie, 1 for a draw, 0 for a loss, plus batting bonus (0.01 per run over 200 in the first innings, up to 400 runs) and bowling bonus (0.1 per wicket in the opposition's first innings). The real rule counts only the first 100 overs of an innings; the game counts the whole innings, and the 400-run cap is my reading of the rule. A drawn or tied Final goes to the side with more first-innings bonus points.
- **One-Day Cup points**: 4 for a win, 2 for a tie or no result (the real bonus point, if any, is not built). A tied league match is shared; a tied Final goes to a Super Over. No DRS, rain as in the game's other 50-over matches.
- Tables: Shield by points, then wins, then runs per wicket scored over conceded (assumption for the last tie-break).

## Not built
The real results so far, the over-rate points, injury or national-duty replacements, the One-Day Cup bonus point.
