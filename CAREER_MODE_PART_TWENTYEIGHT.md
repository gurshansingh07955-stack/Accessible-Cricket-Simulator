# Career Mode, part twenty-eight (managing league contracts)

**Career > League contracts** (listed when he is at the state level or above, has offers on the table or has signed for a league this season).
- **Leagues you have signed for this season:** each with its contract, the league matches he has played out of the matches his team has in the league, and what he would be paid if he left now. **Leave the league** asks first.
- **What leaving does** (`CareerLeagues.leave`): he plays no more matches in that league and his place in the franchise's squad is taken away; the contract is cut to `contract x matches played / his team's league matches` (whole hundreds of dollars, never more than the contract; knockout matches do not raise it) and is paid when the season ends with the other contracts (`CareerFinance.settle`); no prize money for that league. The league has no team of his any more (`TournamentState.userTeamId` is empty), so it leaves his Game play, Calendar and clash checks; it is played out without him as soon as the season moves (`CareerSeason.advance`) and stays on the Points table list. A league he has left is not offered again that season. He cannot leave while a half-played match is saved, or after the league is over.
- **Offers on the table:** each can be turned down on its own.
- **Contracts are now kept league by league** (`CareerProfile.leagueDeals`, `leftLeagues`). A career that signed earlier in the same season shares the season's contracts equally between its leagues.
- **Not built:** a fee for leaving, or a franchise that takes the player back.
