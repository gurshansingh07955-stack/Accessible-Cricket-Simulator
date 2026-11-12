# My Player, part eighteen: the Career menu, every league for Indian players, the Champions Trophy for all, franchise captaincy

**The Career tab is a menu.** It lists the pages under four headings: *This season* (Season objectives, Fitness, Training and specialism), *Your career* (Career record, Honours, Rankings, Captaincy), *Money and the papers* (Earnings, In the news), *Changes* (Move to another country, Retirement).
Each button says in one sentence how that page stands ("2 of 3 reached", the fitness line, the first ranking, the career totals, the latest honour). A page opens with **Back to the Career menu** first, then its heading and its sentences.
A retired player's menu has the Career summary first and leaves out the playing pages. The state of the menu is kept while the tab is open.

**Indian players can play every league.** The old rule (only the IPL for an Indian player) is gone; the Pakistani player is still not offered the IPL.

**The Champions Trophy is played by every country** in the seasons 3, 7, 11 ... (the 50-over event of the odd seasons that are not an ODI World Cup season). India and Pakistan also play the **Asia Cup** in those seasons, 8 weeks after the Champions Trophy begins.

**Franchise captaincy** (`CareerLeagues.captainScore`, `CareerSeason.captainIn`). After 10 league matches (`CAPTAIN_MATCHES`) a franchise may offer its captaincy together with its contract: a leadership score of 4.0 or more (a point for every ten league matches, 0.3 for every place of form, his standing in the squad: top two +2, top five +1,
an international +1.5, age 25 or over +1). The offer says so, accepting it makes him the captain of that franchise for the season (an honour is added), and in that league he picks the XI, does the toss and plays the captain's role, as the club and national captains do.
A national captain is no longer the captain of his franchise unless the franchise has made him its captain: who is the captain is now decided for each competition (`CareerSeason.captainIn`).

**Dials.** `CAPTAIN_MATCHES`, the thresholds in `captainScore`.
