# My Player, part five b: playing as your player

In a career match the player takes over ONLY his own batting and bowling. `MatchState.careerPlayerId` marks it. The selectors name the XI (with the
player in it, at the batting number he chose); the openers, the next batter after a wicket, the bowlers and the field are picked by the game for
the player's team, as they are for the opposition.

- Batting: when the player is on strike he faces the ball as in any match (Face next ball, footwork, shot, timing). When a team-mate is on strike
  the game plays the ball itself.
- Bowling: when the player is the bowler, the captain sets the field (you hear what moved; Hear the field shows it) and you bowl (Bowl). When a
  team-mate bowls, the game plays the ball.
- The game plays the other balls by itself with a pause between them so each can be heard; Skip to my turn shortens the pause until the
  player's turn comes. Appeals and umpire decisions only happen for the player's own dismissal or the wickets he takes.
- District matches are 20 overs a side, no DRS, rain possible, and a tie is shared (no Super Over).

How it works: `MatchScreen(careerPlayerId = ...)`; the engine's "the user picks" prompts (openers, bowler, next batter, the user's own field) are
skipped when `careerPlayerId` is set (`MatchState`, `MatchSimulation`); an effect in `MatchScreen` plays the balls that are not the player's.
Not built yet: a captaincy of your own, choosing which of your overs to bowl, changing the batting order mid-innings.
