# My Player, part eight: the international level

**The road.** District club, then the state squad, then the national team. The Home tab now has a **Your pathway** section (a heading, then plain sentences) that says where the player is, what the next step is,
his rank in his role against the squad he is aiming for, the number of places the selectors take, and roughly how many rating points he is short. This answers the complaint most players have about career modes
(nobody says how far away selection is). It is worked out by `CareerInternational.pathway` off the main thread.

**The call-up.** After a state season the national selectors decide (`CareerInternational.decision`): his ratings in his role against the national squad's players of that role (top three batsmen, bowlers, top two
all-rounders, one keeper, widened or narrowed by the career difficulty), his form (the average impact of his last five matches against 25, a place for every ten, up to three either way), youth (21 or younger)
and, for a player already in the national squad, two places of loyalty. They also want to have seen `MIN_STATE_MATCHES` (8) state matches. Called up, the level becomes **International**; dropped, he goes back to the
state squad (and from there, if the state selectors drop him too, to district cricket). The reasons are spoken in the new-season message.

**The season.** Four series, each a small two-team `TournamentState` (id `careerintl_<career>_s<season>_<n>_<t20|odi|test>`): a Test series (two or three matches, five against the country's old rival), a one-day series
(three or five), two T20 series (three or five, then three). Opponents are drawn from the ten Test countries, a different one every series and every season; the series alternate home and away (home series are
played at the grounds of the player's country, away series at the opponent's). The national teams are the game's own, the player written into his country's squad in the place of its weakest player of his role.
Rules: T20 and one-day series use the format's own rules (with DRS); a Test is five days (`TestMatch.rulesForDays(5)`). A Test series' table shows points, wins and draws.

**Selection for a match** (`matchDecision`): a Test XI takes four batsmen and four bowlers, a one-day or T20 XI three of each, two all-rounders in a Test and three otherwise, one keeper. His first international match is always
a debut; the first three matches and being 19 or younger count a place in his favour; form moves him up or down by up to three places.

**Where it is wired.** `TournamentRules.rulesFor` (MatchRules.kt) answers for ids that start with `careerintl_` (they must not start with `career_`, which means a club league); `CareerSeason.competitionOf`, `champion` and
`nextSeason` know about the series; `CareerProfile` has `isInternational`, `atOrAboveState` and `internationalMatches`; the hub creates the international season at the start of a season at that level.

**Not built yet (the next parts).** International tournaments inside the season (the T20 World Cup, the Champions Trophy, the Asia Cup), the franchise leagues (the IPL, the Big Bash and the rest, with an auction or
draft decided by form), a player ranking page, season awards, retirement and a career summary, a captaincy path for the national side, picking a format to specialise in.

**Dials.** `MIN_STATE_MATCHES`, `squadSlots` and `matchSlots` in `CareerInternational.kt`; the rating growth in `CareerProgress`.
