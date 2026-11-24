# TalkBack smoothness (patch 144)

Cause found: slow work done on the MAIN thread while a screen opened, so the screen (and TalkBack with it) froze and answered late.

1. **Edit teams > Domestic / League / International tournament teams.** The squads of every domestic competition (ten groups), the seven leagues and the four
   international tournaments were built on the main thread the first time a tab was opened, and again after every edit (`Customisation.revision` moves on).
   Now they are built in the background: the tab opens at once with "Loading the teams. Please wait." (spoken politely) and the list appears when ready.
   They are also built in the background as soon as Create Custom Teams and Players opens, and again after each edit, so the tab is usually ready before it is tapped.
   League squads are now kept like the others (`LeagueGroupsCache`). The team page, the player editor, Add players from other teams and the Reset game teams list
   look their teams up the same way (`rememberTeamGroups`, `rememberTeamLookup` in `CustomTeamsScreens.kt`).
2. **Tournament menu.** `TournamentFlow` built the squads of eleven tournaments (CPL, Abu Dhabi T10, LPL, ILT20, BBL, MLC, SA20, World Cup, Champions Trophy, Asia Cup,
   Asian Games) on the main thread every time Tournament was opened. Now each is built only when it is needed, in the background; the menus and lists open at once and a
   tournament's own pages show "Loading" for the moment its squads take.
3. After editing a career the Home tab recalculates its pathway and rank lines (patch 143).

Not measured: nothing can be run or timed here, so how much faster each screen feels is for the player to say. If a screen still lags, name it.
Dials: none; the old behaviour is in git history (patch 144 is one self-contained change).
