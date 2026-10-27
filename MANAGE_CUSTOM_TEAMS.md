# Manage teams, gender, add players from other teams (patch 056)

Create Custom Teams and Players > Edit teams now starts with two more choices:

* **Delete custom teams**: **Delete all custom teams**, or **Choose custom teams to delete** (tick the teams, then
  delete the ones chosen). Both ask before deleting.
* **Reset game teams to default**: **Reset all teams**, or **Choose which teams to reset**. A game team counts as
  changed when it was renamed, a player was edited, or players were created or copied into it; resetting removes all of
  that and puts the team back as the game made it. Custom teams are never touched by a reset.

When a player is created there is now a **Gender** choice (Male or Female). It is shown on the player's row; it does not
change how the player plays.

Every team page (game teams and custom teams) has **Add players from other teams** just above Create a new player:
pick International, League or Custom teams, open a team, tick players (from as many teams as you like), then Add. The
players are copied (role, ratings, batting hand, bowling style, gender); the originals stay where they are. A player
whose name is already in the team cannot be added twice.

Files: `Customisation.kt` (reset, delete, copy, gender), `ui/custom/CustomTeamsManagePages.kt` (the new pages),
`ui/custom/CustomTeamsScreens.kt` (the new buttons and the gender choice).
