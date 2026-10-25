# Create Custom Teams and Players (patch 051)

Home > Advanced Customisation > Create Custom Teams and Players. Two choices:

## Create a new custom team
A name (keyboard) and, optionally, a logo chosen from the gallery (the picture is copied into the app and
shrunk to 256 pixels). Saving opens the new team so players can be added.

## Edit teams
Three tabs: **International teams**, **League teams** (CPL, Abu Dhabi T10, LPL, ILT20, BBL, MLC, SA20; the
IPL is made by its auction so it is not here) and **Custom teams**.

* Open a team to see its name and every player, split under Batsmen, Wicketkeepers, All-rounders, Bowlers.
* **Press and hold a team name** to rename the team (a screen reader also offers a "Rename team" action).
* **Edit** next to every player: role, batting hand, bowling hand and style, batting (1-95), bowling (1-97)
  and fielding (1 to the game's highest fielding rating) ratings.
* The last choice in every team is **Create a new player**: name, batting hand, bowling hand, role, bowling
  style (fast, fast-medium, medium-fast, medium, off break, leg break; left-arm bowlers also orthodox and wrist
  spin), then the three ratings, then Save player. A player you created can be deleted from its Edit page.
* A **custom team** also has **Delete this team** (with a confirmation).

## What the edits change
Edits are kept in `customisation.json` and applied when a team is read (`Customisation.applyTo`), so they change
how the game plays (ratings, role, pace or spin) as well as what the screens show.

* An edit is kept per team and player name: editing a player in an international team also changes his copies
  in league and tournament squads that are built from him afterwards; editing him in a league team changes only
  that league team.
* A renamed team shows its new name wherever the team is shown (the three-letter short name does not change).
* A new player created in a game team joins that team (in the international team's own squad and in quick
  matches; the real tournament squads such as the World Cup keep their real squads, but their players' edits apply).
* Saved tournaments already in progress keep the players they started with.
* **Custom teams are not usable in matches yet**: they are saved and editable, and are meant for custom
  tournaments (still to come).
* `Customisation.kt` (data and rules), `ui/custom/CustomTeamsScreens.kt` (screens).
