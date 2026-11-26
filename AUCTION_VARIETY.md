# IPL auction: different targets in every auction (patch 152)

Problem: choosing the same team and pressing Simulate gave almost the same squads every time. The seed of each auction did change, but the other nine teams planned the same way every time:
every team ranked the players still to come by rating alone (so they all aimed at the same top names, in the same order) and each team's "taste" was fixed forever by its name.
Only a +-8% appetite moved with the seed.

Now (`IplAuction.VARIETY`, 1.0; 0 = the old behaviour exactly):
- **Taste changes with every auction** (`taste(state, ...)`): each team's liking for batters, all-rounders, keepers, fast bowlers and spinners is drawn again from the auction's seed.
- **Each team has its own view of who is best** (`teamRank`): the ranking of the players still to come in a kind is shaken by up to +-10% of rating for every team and every player, so the targets differ team by team and auction by auction.
- **The players inside each set come up in a random order** (`buildSets(pool, seed)`; the sets themselves and their order are unchanged).
Saved auctions keep their own sets; the career-mode league auctions get the variety too (their seed comes from the career and season).
Not measured: the auction twin (`tools/auction_twin.js`) still mirrors the old rules. If teams end up lopsided or purses unspent, lower VARIETY (or RANK_NOISE).
