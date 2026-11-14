# Career Mode, part twenty-three

Three fixes (patch 109).

1. **View scorecard in the Calendar.** Every played match (the player's own and the other clubs') has a "View scorecard" button under its result. The scorecard opens full screen; Back returns to the Calendar.
2. **The toss.** `TossRoles.CAREER_HOME_CALLS = false`: in Career Mode who calls the coin is chosen by chance, 50-50, each match (before, the fixture's home/away mark decided it, and the player's team was mostly marked away, so he mostly flipped). Set it to true to bring the old rule back. The captain's toss (`TossScreen`) and the screen for a player who is not the captain (`CareerTossScreen`) both follow it.
3. **Team names in results.** A result line used `CplData.shortName(teamId)`, which returns the id for a team without a short code (a career club), so the line read like `career_..._c3 125/10`. `CplSimulator.teamLabel` now falls back to the team name, and `CareerSeason.readable` replaces ids in results that were saved earlier (Calendar, latest result, the lines after a simulated match).
