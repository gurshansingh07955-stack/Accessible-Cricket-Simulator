# My Player, part twenty-two: several leagues in a season, and clashes settled by priority

**Several leagues.** The one-league-a-season limit is gone. After "Check for league offers" you can sign for as many offers as you like, one franchise in each league; the other offers stay on the table, and "Look for more league offers" asks again (a league you have signed for makes no second offer).
"Turn down the other offers" or "That is all my leagues this season" stops the search for the season. Contracts add up (`leagueContract`), you can be the captain of more than one franchise (`leagueCaptainTeamIds`), and each franchise you sign for wants to keep you next year (`retainedTeamIds`).

**The leagues fall at different times** (`CareerLeagues.startAt`). Each league starts a number of days after the season begins (Abu Dhabi T10 at once, then the Big Bash after 14 days, ILT20 28, SA20 42, IPL 70, LPL 105, MLC 119, CPL 140) and plays his team's matches about every four days (`CareerSeason.stepDays`;
everything else stays weekly). So leagues overlap each other, and the player's international cricket, a little.

**A clash is settled by priority** (`CareerSeason.priorityOf`, `clashReason`, `selectionFor`). When two of his matches fall on the same day, or a day inside a Test or a three-day match, the competition that comes first is played and the other is played without him:
international cricket (series and tournaments) first; then the leagues, the better league first (IPL, Big Bash, SA20, CPL, MLC and ILT20, LPL, Abu Dhabi T10); then state cricket; then club cricket. Between two competitions of the same kind the one earlier in the season's list wins. The Home tab says
"You cannot play this match: the ... has a match on ..." and offers the simulation; a simulated season reports "You missed it".

**Not changed.** Leagues already signed before this patch keep the dates they were given (ten weeks after the rest of the season); the new dates apply to leagues signed from now on. A match he misses through injury or a clash earns no fee, no form and no record.
