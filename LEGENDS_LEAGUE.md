# Legends League Cricket 2026

The league of retired international and veteran stars (LLC season 4, 11 to 27 March 2026, India; Royal Riders Punjab won it), built the same way as the other real leagues (SA20, Major League Cricket and so on). Tournament > League tournaments > Legends League Cricket 2026.

* **Teams** (`logic/LegendsLeague.kt`): Mumbai Spartans, India Capitals, India Tigers, Konark Suryas Odisha, Southern Super Stars, Royal Riders Punjab, with the squads they finished with (12 to 21 players). The league's own site says "India Capitals"; some news sites print "India Captains".
* **Format**: 15 league matches (a single round robin, in the published order), then Semi-final 1 (1st v 4th), Semi-final 2 (2nd v 3rd) and the Final, T20, 18 matches in all. The game's table gives 2 points a win.
* **Grounds** (new in the game, ratings estimated, in `StadiumData.kt`): Haldwani, Coimbatore, Amritsar, Gwalior. The semi-finals and the Final are at Haldwani. The grounds of league matches 14 and 15 were not published in the sources read: Gwalior is an assumption.
* **Rules**: none beyond an XI of eleven. No overseas rule (every player is tagged DOMESTIC).
* **Players**: Ashwin and Isuru Udana were already in the game and keep their ratings. All other players are new with **estimated ratings** (the `nw(...)` lines), and their hand and bowling style are in `PlayerProfiles.kt`. Players named early but not in the final squads (Dhawan, Finch, Wagner, Tye and others) are not included.
* **Advanced Customisation**: the league is in Edit teams (League teams tab), Add players from other teams, Reset game teams, the custom-tournament team picker and Edit Tournament. A player edited in a league team changes only that team.
* **Not built**: the real results (the real tournament had walkovers and disqualifications; the game simulates every result), the league's points rules, career-mode offers from this league.
