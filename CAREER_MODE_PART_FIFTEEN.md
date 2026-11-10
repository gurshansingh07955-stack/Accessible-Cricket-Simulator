# My Player, part fifteen: money, retention, the ILT20 and the papers

**Money** (`CareerFinance`, shown in the Career tab under **Earnings**, and in the career summary). Match fees are paid as he plays: Test 4,000, one-day 2,500, T20 international or tournament 1,500, state 120, club 25 (a franchise league match pays nothing: the league contract covers it).
When the season is settled (Start next season, or retiring) the contracts and the prizes are paid: a national central contract for an international (60,000, +20,000 for a vice-captain, +40,000 for a captain); the league contract he signed (the league's base times 0.4 plus his main rating over 100, 10% more if retained,
25% more for an international; bases: IPL 400,000, Big Bash 150,000, SA20 120,000, CPL and MLC 100,000, ILT20 90,000, LPL 60,000, Abu Dhabi T10 50,000); prizes when his team wins (20,000 for a tournament or a league, 5,000 for a series, 500 for a club or state title).
The settlement message says what he earned. The figures are invented; the dials are `matchFee`, `BASE`, `centralContract`.

**Retention.** The franchise he last signed for counts a place in his favour when the offers are made, says "Your franchise wants to keep you", and pays 10% more (`CareerLeagues.offersFor`, `lastLeagueTeamId`). Each offer now says the contract offered.

**ILT20.** The International League T20 is now one of the leagues that can make offers (prestige 3). Its XI quotas (two UAE players, two Afghan players) are still not checked in the XI the selectors name, as with the overseas caps of every league.

**The papers** (`CareerMedia`, Career tab, **In the news**). When a season is settled, each finished competition may earn a line: star of the competition, a hundred, a five-wicket haul, 200 runs, 8 wickets, or a poor run over three or more matches. The last fifteen lines are kept.
