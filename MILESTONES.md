# Milestone pop-ups (patch 139)

In every match the user plays, when a batter or a bowler reaches a milestone a pop-up appears straight after the ball, the crowd applauds (the press conference's applause sound) and the same words are spoken a moment after the ball's own announcement. There is one **OK** button and no Back button; the phone's Back does nothing while it is up (it can neither skip the pop-up nor leave the match), so only OK carries on.

- **Batting:** a 50, a 100, then every 50 up to 400: "Fifty! Name reaches 50 in 18 balls. 4 fours and 2 sixes. Strike rate 277.8."
- **Bowling:** the third wicket of an innings and every wicket after it up to the tenth: "Three wickets! Name has taken 3 wickets. 4.5 overs, 29 runs, 3 wickets. Economy 6.00."
- **Hat-trick, double hat-trick, triple hat-trick:** three, four or five of his own balls in a row that are wickets. A wide or no ball is not one of his balls so it does not break the run; a run out is not his wicket. A wicket that is also a hat-trick is one pop-up.
- **Who:** in a career match only the player's own player (`MatchState.careerPlayerId`). In every other match the user plays (a friendly, any tournament, the IPL, a custom tournament, a Super Over) every batter and bowler of both sides, the opposition's too. Matches the computer plays alone (a simulated tournament match, Match info > Simulate match) show nothing.
- **Settings > Milestone pop-ups:** Show (the default, also for people who already have the app) or Do not show.
- Code: `logic/Milestones.kt` (detection and the words), `ui/match/MilestoneScreens.kt` (the pop-up), `MatchScreen.commitBall` (detects, queues); the pop-up is shown before every other match page. While one is waiting the computer's own balls do not auto-advance. `Milestones.MILESTONES_ENABLED` switches the feature off.
- Not built: maiden overs, fielding milestones (catches), a partnership milestone, a match total of wickets across two innings.

# Running on a no ball (patch 139)

With Settings > Running between the wickets on, a no ball that the player hit for 1, 2 or 3 runs is now run on like any other ball (before, it was skipped). The page opens with "It is a no ball, so only a run out can get you out." The no ball's extra run stays in the score; a failed extra run is a run out, which is allowed on a no ball and on a free hit. A no ball he left alone, or hit for nothing or a boundary, still asks nothing, as with every other ball.
