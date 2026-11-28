# Overthrows you can run on, and the crowd in net practice (patch 156)

**Overthrows** (`RunDecisions`, `RunScreens.kt`; Settings > Running between the wickets, manual running). Before, a wild throw (patch 151) added its one or two extra runs by itself. Now:

* The run he was taking is completed, then the phone says what happened: the runs completed, the wild throw, **where the ball is now** ("past the stumps and away towards the boundary behind the wicketkeeper", or "got away from <receiver> and is running towards the boundary") and who is chasing it, that another run is safe, and how to answer.
* He answers as in normal running (two-finger swipe up to run again, down to stay, or the Run / Stay buttons; five seconds; no answer = no run). One or two runs are on offer (`RunDecisions.MAX_OVERTHROW_RUNS`); after each run taken the phone says the ball is still loose, and after the last it says the ball is gathered and the page ends.
* The overthrow runs are **safe** (the fielders are far from the ball), so there is no run out on them. Runs taken are counted as overthrow runs in the commentary ("Overthrow: 1 more run.").
* Not built: the fielder retrieving the ball and throwing again; a run out on an overthrow run.

**Net practice crowd** (`NetPracticeScreens.kt`): the crowd bed now starts with a batting or bowling net session and stops when the session is left (it was silent). It uses Settings > sound effects and Crowd volume like a match.
