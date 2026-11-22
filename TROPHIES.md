# Trophies (patch 140)

**Home > Trophies** (before Settings): every tournament the player has won in the game, excluding Career mode.

- One button for each competition won, with how many times ("1 trophy"). A competition won once opens the **scorecard of its final** straight away. One won several times opens the list of its wins (the tournament, the team, the date, the final and its result); each win opens its own final's scorecard (the normal scorecard screen).
- A trophy is recorded when the champion is celebrated at the end of any tournament of the game (CPL, IPL, every league, the international tournaments, the domestic tournaments, custom tournaments) and the player's team won: `TournamentScreens`, the Celebration page's Continue, `Trophies.fromState`, `TrophyStore.add`. The scorecard kept is the slim one the tournament keeps for each match (no ball-by-ball). A competition with no final (the County Championship, the Plunket Shield) keeps its team's last match and says "Last match".
- **Earlier wins:** when the Trophies page opens, tournaments already saved on the phone whose champion was the player's team are added (`TrophyStore.backfill`), so trophies won before this update show if the finished tournament is still saved.
- Stored in `trophies.json` (`persistence/TrophyStore.kt`), at most 300.

**Career mode** has its own Trophies page (Career tab, after Honours). It shows only trophies won in the career, each with the final's scorecard. They are recorded when the season is settled (`CareerRecords.settleSeason`) into `CareerProfile.trophies` (nullable, so older careers load unchanged). Only wins from this patch on are kept; earlier wins stay in Honours as text.

Code: `logic/Trophies.kt` (the record, the grouping), `persistence/TrophyStore.kt`, `ui/trophies/TrophyScreens.kt` (`TrophiesScreen` for Home and `TrophyList`, shared with the Career page).
