# My Player, part seven: the domestic level

**Level and seasons.** A career now has a level (district club, then state) and a season number. When all the matches of a season are played, Game play offers Start next season: the player is a
year older and the state selectors decide (`CareerSelection.stateDecision`). Called up, he plays his state team's domestic tournaments; not called up, another club season. Already in the
state squad he gets two places of loyalty; dropped, he goes back to district cricket.

**The call-up.** His ratings, in his role, are ranked against the state squad's players of that role (the squad as the game has it, without him): the selectors take the top three of a role
(one keeper), widened or narrowed by the career difficulty. Form (the average impact of his last five matches against 25, a place for every ten, up to three either way) and being 19 or younger help him.
Domestic players are stronger than club players, so a newcomer usually needs a season or two of growth first.

**The state tournaments.** India: the Syed Mushtaq Ali Trophy (T20), the Vijay Hazare Trophy (50 over) and the Ranji Trophy; England: the Blast, the One-Day Cup and the County Championship; Australia: the One-Day
Cup and the Sheffield Shield; South Africa: Pro20, Pro50 and the 4-Day Series; New Zealand: the Super Smash, the Ford Trophy and the Plunket Shield; Pakistan: the Quaid-e-Azam Trophy Gold or Silver. They are
the game's own tournaments (`CareerDomestic`), played in the tournament order, with the player written into his state team's squad in the place of its weakest player of his role. A state with several
domestic teams (Gujarat, Maharashtra, the Pakistani cities) uses the first one. At State level the player plays as himself exactly as at the club (he faces and bowls his own balls, the game plays the rest);
the club captaincy is a district matter. Match selection works the same way inside the squad: a debut first, then by rank, form and the difficulty.

**Tabs.** Calendar lists every match of the club leagues and your team's matches of the state tournaments, by date; the Points table tab shows each competition's table (a first-class tournament shows the game's
points) and statistics.

Not built yet: more levels (the national side, leagues), retirement, a different state team inside a state with several, the other state tournaments (the Duleep Trophy, the Irani Cup).
