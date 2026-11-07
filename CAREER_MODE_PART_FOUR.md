# My Player, part four: the career hub with four tabs

The career's home page now has four tabs along the bottom of the screen (next to the phone's own buttons):

- **Home**: the squad information and everything made before (the club squad, the clubs of the state, the player profile, the ratings).
- **Game play**: the next match of the player's club. Play my match (Playing XI, toss, ball by ball, saved if left half-way, with Resume my match), Simulate my match, or
  Simulate the whole season. Before the player's match, the other clubs' earlier matches are simulated so the table is up to date.
- **Calendar**: the whole season round by round, a Saturday each, with the ground, your matches marked, and the results.
- **Points table**: two sub-tabs, Points table (the league table) and Stats; Stats has two more, All (every player) and My team.

The season (`CareerSeason`) is one league of the state's district clubs, each playing the others home and away: a 20-over match a week. It is the game's own
`TournamentState` (saved in `career_season_<id>.json`, a half-played match in `career_match_<id>.json`), so fixtures, results, scorecards, tables and
statistics use the same code as the game's tournaments. It is made the first time a career's hub is opened.

Not built yet: the player taking part as himself (selection into the XI, his own batting and bowling), the difficulty's effect on selection, progress and
ratings growth, the state and domestic tournaments and the other levels.
