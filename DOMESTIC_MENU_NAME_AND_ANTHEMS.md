# Domestic tournaments by country, the tournament name in matches, and anthems in international tournaments

**Domestic menu.** Tournament > Domestic tournament now opens a list of countries (Indian, English, Australian,
South African, New Zealand, West Indies, Pakistani, Bangladeshi). A country opens only its own tournaments
(`TPage.DomesticIn` in `TournamentScreens.kt`). Back from a tournament returns to its own country's list
(`domesticCountryOf`). A new domestic tournament needs a row in that country's group in `DomesticPage`, its kind
in `domesticCountryOf`, and its name in `tournamentNameForKind`.

**Tournament name in matches.** `MatchState.tournamentName` (null in a normal match) holds the name plus the stage
unless it is a plain league match ("Big Bash League 2026-27, Final"). It is shown under the heading of the match
screen, on Match info, on the scorecard screen, on the result screen, and in the shared and copied scorecard text.
It is filled in by `MatchScreen(tournamentName = ...)`, also for a match resumed from an older save.

**Anthems in international tournaments.** After the toss, the T20 World Cup, Champions Trophy, Asia Cup and Asian
Games play the national anthems with the same `AnthemScreen` as a normal match (`TPage.Anthem`): home side first,
random at a neutral ground, Skip anthems on the screen. A team with no recording is skipped. Recordings added for
Italy, Nepal, Namibia, Malaysia and Canada (Anthems.kt). Still without one: West Indies, Scotland, UAE, Hong Kong,
Japan. Run "Fetch audio into the app", then "Build APK", for the new recordings to be packed.
