# Test matches: free hit, new ball and dropped catches (patch 149)

1. **New ball (checked, not changed).** `TestMatch.newBallAvailable` needs the ball to be `newBallOvers` (80) overs old, counted in LEGAL balls bowled with it
   (`matchBalls - ballStartBalls`, across innings). The Take the new ball button (Match info) and the computer's `aiShouldTakeNewBall` both depend on it, so the new ball
   cannot be taken before 80 overs (a custom format may choose another number, or switch the new ball off).
2. **No free hit in a Test.** `MatchState.nextFreeHit` was already false in a Test (and for any multi-day custom format, which plays as a Test). What still spoke of a free hit is now
   switched off in a Test: the umpire's "Free hit!" (`MatchAudioDirector`), the commentary line "...a free hit is coming up!" (`CommentaryLibrary.freeHitsApply`), the last-ball
   summary "Free hit next ball." (`MatchLines.ballSummary(freeHitNext)`), and the bowler's notice after he oversteps (`PitchingScreen(freeHitFollowsNoBall)`).
3. **Dropped catch: run on it yourself.** When Settings > Running between the wickets is on and the user is batting, a catch the computer's fielder puts down now asks him
   whether to run (the same swipe or buttons as any ball): the fielder who dropped it is the one he is told about; the ball is worth the runs the drop scored, at least one;
   taking them gives that, staying back gives fewer, going for more can be a run out. (`RunDecisions.build`, `MatchScreen.advanceOneBall`.)
