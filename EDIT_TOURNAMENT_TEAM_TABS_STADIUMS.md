# Domestic tournaments in Edit Tournament, team tabs and stadiums

**Edit Tournament** now lists the 21 domestic tournaments, by country, after the league and international ones. A domestic tournament
can be renamed (the new name shows on its page, in its matches and in the shared scorecard); its format cannot be changed, because
each one plays by its own competition's rules. `GameTournaments.idForKind` knows the domestic kinds so a rename is found.

**Create Custom Teams and Players > Edit teams** has five tabs: International teams, International tournament teams (the squads of
the four real international tournaments), League teams, Domestic teams (every domestic competition's teams, grouped by country) and
Custom teams. International-tournament squads cannot take new or added players (those join the national team); their players can be
edited, and the edit changes the player in his national team too (edits are kept per team id and player name). Add players from other
teams and Reset game teams know the new groups.

**Create Custom Tournament** has a Domestic Teams tab between League Teams and Custom Teams, and the stadium picker lists EVERY
ground the game has (the IPL's, the domestic competitions', the real international tournaments' and the league grounds), not only
the base list (`AllStadiums`). A custom tournament finds its grounds through `CplData.stadium`, so the new ones play.

`TeamGroups` keeps the built tournament and domestic squads until the player changes something (`Customisation.revision`), because
building them looks the players up across every league.
