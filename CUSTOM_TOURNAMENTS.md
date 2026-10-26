# Custom tournaments, part 1: design and edit (patch 052)

Home > Advanced Customisation > **Create Custom Tournaments** (now switched on). Two choices:

## Edit Tournament
Lists every tournament of the game (CPL, IPL 2027, Abu Dhabi T10, LPL, ILT20, Big Bash, MLC, SA20, T20 World Cup,
Champions Trophy, Asia Cup, Asian Games) and every custom tournament, each with its current format. Open one to
change its **name** and **format**. The formats offered are all of the game's (T20, ODI, T10, Five-over, One-over;
Test matches cannot be used in a tournament) and all of the player's custom formats. A game tournament can be
restored to its original; a custom tournament can be deleted.

## Create New Custom Tournament
1. **Name** (keyboard).
2. **Format**: the game's formats and the custom formats.
3. **Schedule**: Round Robin, Double Round Robin, Round Robin and Final, Round Robin + Semi-finals + Final,
   Round Robin + IPL-style Playoffs, Knockout, Two Groups + Semi-finals + Final (each says how many teams it needs).
4. **Teams**, four tabs: International Teams, International Tournament Teams (the World Cup, Champions Trophy,
   Asia Cup and Asian Games squads), League Teams, Custom Teams (a custom team needs 11 players). 2 to 16 teams, and
   the same country cannot be picked twice.
5. **Stadiums**: a country menu, then that country's stadiums (tick any number, from several countries).
6. **DRS and rain**: two switches; with DRS on, 1, 2, 3, 4 or unlimited reviews per innings.
7. **Summary** with the number of matches, stage by stage (for example Round Robin of 8 teams: 28 league matches),
   then Save tournament.

## What is not in this patch
The tournament is saved. **Playing it** (Tournament > Custom tournaments) and the rule changes the tournament
needs inside matches (custom overs, restrictions, DRS and rain settings, and the new formats of the game's own
tournaments) come in the next patch.

Files: `logic/CustomTournament.kt` (schedules, formats, teams, the saved definition), `Customisation.kt` (storage),
`ui/custom/CustomTournamentScreens.kt` (screens).
