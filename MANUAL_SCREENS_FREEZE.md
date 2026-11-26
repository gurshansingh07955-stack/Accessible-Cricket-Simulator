# Manual running, stopping and catching: the screen "freezing" for 5 or 6 seconds (patch 150)

Cause (read from the code; nothing can be timed here):
- The catching and stopping screen (`CatchScreen`) says a long call in the phone's voice (the fielder, his rating, the direction, what to swipe) and IGNORED every touch until it had finished,
  then waited a moment for the buzzes: five or six seconds in which the page looked dead.
- The running screen waited for the whole after-run sentence to be spoken before it passed the result on (after the last run, a run out or a time-out).
- If the phone's voice failed (not started yet, refused the line, no voice on the phone) no "finished" ever came, and the page waited for its long time-out. `PhoneSpeech` could also lose
  the "finished" of a line that was replaced while the engine was still starting.

Changes:
- `PhoneSpeech`: a voice that fails says "finished" at once; a replaced waiting line is told it is over; each line has its own id; `warmUp()` starts the engine early (called by `GameServices`).
- `CatchScreen`: the call is waited for at most 9 seconds, and a swipe or a tap while it talks cuts it short and starts the buzzes straight away.
- `RunDecisionScreen`: the lines that end the page are waited for at most 2 seconds (the "Yes!"/"No!" clip at most 2.5); the questions still wait for their sentence, and a swipe during it still works.
Dials: `ANNOUNCE_MAX_MS` (ManualFieldingScreens.kt), the 2000 ms in RunScreens.kt.
