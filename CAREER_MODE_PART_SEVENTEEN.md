# My Player, part seventeen: the 14-team ODI World Cup, the Impact Player and Power Surge, the overseas cap

**14-team ODI World Cup** (`odiWorldCup14`, `advanceWc14`; seasons 1, 5, 9 ...). The ten Test nations plus Ireland, Zimbabwe, the Netherlands and Scotland, in two groups of seven (dealt out by strength in a snake, 21 matches a group, 50 overs a side, shown as Group A and Group B tables); the top three of each group go to a
**Super Six** (each plays the three from the other group: 9 matches; no points carried over, a simplification of the real format), then the top four of the Super Six play two semi-finals (1st v 4th, 2nd v 3rd) and the Final. The host country changes every four seasons.

**Impact Player** (IPL, `CareerLeagues.impactFor`). When the player is the captain of an IPL side, the match has the Impact Player rule: the computer names five substitutes for each side and he uses his own from the Match info page, as in the game's IPL. When he is not the captain (he only bats and bowls, the computer decides the rest) no Impact Players
are used by either side, so the sides stay level. The computer's own Impact Player use is the game's (`ImpactPlayer.aiChoose`).

**Power Surge** (Big Bash). Career matches of the Big Bash now include the Power Surge: the game's rule, a Power Surge button on the Match info page when the player's side may take it, a pop-up when the computer takes it.

**Overseas cap in the XI** (`CareerSelection.overseasSafeVictim`). When the selectors put an overseas player into the XI, and the best XI already has the most overseas players allowed (4, or 6 in Major League Cricket), he takes the place of an overseas player instead of a home player.
The ILT20's quotas of UAE and Afghan players are still not checked.
