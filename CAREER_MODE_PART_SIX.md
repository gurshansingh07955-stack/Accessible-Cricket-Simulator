# My Player, part six: the pace of the game, the captains' toss and the captaincy

**Pace.** The balls the game plays for the player (when he is not on strike and not bowling) are now 5.5 seconds apart, so each can be heard and understood.
"Skip to my turn" still shortens the wait (to 0.3 seconds a ball) until his turn comes.

**The toss.** A player who is not the captain no longer takes part in the toss. A screen says who flips the coin, who calls, what it landed on and who chose to bat or
bowl; then Start the match. (The away captain flips and the home captain calls: `TossRoles.HOME_TEAM_CALLS`.)

**Captaincy.** After every match he plays, the player's form (the average impact of his last five matches) is checked (`CareerLeadership`): five matches and an average of
28 or more make him VICE-CAPTAIN; five more as vice-captain and an average of 32 or more make him CAPTAIN (scaled 0.92 to 1.12 by the difficulty). The announcement is
part of the after-match line, and the Home tab shows his rank.
As CAPTAIN he always plays and makes the team decisions in his matches: he picks the XI (Playing XI screen, with himself in it), calls or flips the coin and chooses to bat or bowl
(the ordinary toss screen), picks the openers, the bowlers and the next batter, sets the field, declares, enforces the follow-on and takes the new ball. He still faces and bowls only his own
balls; the game plays his team-mates' balls. `MatchState.careerCaptain` and the `userSelects` property switch those prompts back on.
The vice-captain plays as a player.
