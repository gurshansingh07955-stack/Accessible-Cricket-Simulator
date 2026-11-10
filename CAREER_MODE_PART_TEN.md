# My Player, part ten: league selection

**What it is.** A player at the state or the national level can ask the franchise leagues of the game for offers (Home tab, **League offers**, a heading with the button "Check for league offers"). Each franchise
that would play him makes an offer with its reason in a sentence ("You would rank 2nd among the 5 batsmen of Sydney Sixers, and a T20 XI takes the top 3"). He signs for **one league a season** or turns all the offers down.
The league's season is added to the season's competitions, dated after the rest of the season (10 weeks after the last competition began), so a player who is also an international plays his series and his tournament first.

**How the offers are decided** (`CareerLeagues.offersFor`): every franchise of every league open to him is looked at; where would he rank among its players of his role (his ratings against theirs)? It offers if he would be in its T20 XI:
the top three of a role (one wicketkeeper), widened or narrowed by the career difficulty, a place fewer for an overseas player, a place more or fewer for his T20 form (his last three T20 matches), a place more for an international.
Leagues in order of prestige: IPL, Big Bash, SA20, CPL, MLC, LPL, Abu Dhabi T10; at most two offers from a league, five in all. They want to have seen at least 5 of his matches first (`MIN_MATCHES`).
Real rules used: an Indian player may play only the IPL (the BCCI keeps active players out of other leagues); a Pakistani player is not offered the IPL. The IPL's squads come from a computer-only auction (all ten teams bid) run when the
offers are made; the seed is kept in the offer so the same squads are rebuilt when he accepts.

**Playing it.** The league is the game's own: real teams, fixtures and grounds, the player written into the franchise squad in place of its weakest player of his role (overseas or domestic by his country against the league's).
The franchise selectors rank him among their players of his role (`CareerLeagues.matchDecision`, a place fewer if overseas, his T20 form, his first league match is a debut). His league matches count as league matches, not as state or international matches,
and keep his T20 form. After the season the Game play tab gives his tally and says if he was Player of the Series.

**Not built.** The ILT20 (its XI quotas cannot be honoured when the selectors name the player's XI); the Impact Player and the Power Surge in these matches; the overseas cap checked in the XI the selectors name (respected in the offers and
in his ranking only); clashes between league and national duty (the league is scheduled after the national cricket); pay and contracts; the IPL's real auction with the player in it.
