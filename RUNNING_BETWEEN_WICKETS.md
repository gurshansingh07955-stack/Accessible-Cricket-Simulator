# Running between the wickets (patch 126)

**Settings > Running between the wickets:** "The computer decides the runs" (the default, as before) or "You decide the runs". Part one of the feature: the user's own batting decisions and the partner's calls. **"Yes!" and "No!" are recorded** (patch 127: one clip each, `run_yes` and `run_no`, in the press conference's player voice, generated through `press_tts_generate`, fetched by `tools/fetch_audio.sh`, played by `SoundEngine.playPress`); the phone's voice says everything else, and says "Yes!" / "No!" itself if a clip is missing. A time-out ("Time is up. No run.") is spoken by the phone. The voice was chosen by description and not heard by the assistant.

## When the user bats (a normal match, or career mode with his player on strike)
A ball that the simulation scores 1, 2 or 3 runs off (not a boundary, a dot, a wide or a no-ball, a wicket that is not a run out) opens a full-screen page (`ui/match/RunScreens.kt`, rules in `logic/RunDecisions.kt`):
1. **The phone's voice** says where the ball went ("along the ground through the covers") and who is closing in (name, fielding rating, position), with a hint of how risky the run is.
2. **Five seconds** (counted from the end of the voice, `RunDecisions.DECISION_SECONDS`): swipe **up with two fingers** to run, **down** to stay. No swipe = no run. "Run" and "Stay, no run" buttons do the same.
3. The answer is spoken ("Yes!" / "No!", with a buzz). After a run: "One run completed", where the ball and the fielder are, and the next decision, up to **three runs** in all.

**What the runs are worth.** The simulated ball says how many runs the shot is worth. Taking those runs gives exactly the simulated ball. Staying back gives fewer. If the simulated ball was a **run out** (the engine's own roll, `MatchEngine.runOutChance`), the last of its runs is the run out: go for it and you are out (the fielder is the one you were told about), hold back and you are not. Going for **more** than the shot is worth is an **extra run** and the fielder can stop it: run-out chance 0.22 for a deep fielder, 0.45 inside the ring, 0.65 close in, plus (rating - 60)/300, plus 0.10 for each further extra run, between 0.12 and 0.85 (`RunDecisions.extraRisk`). The hint is the fielder's distance (worse when the ball really is a run out), or the true chance for an extra run. Not measured on play: these are the game's own numbers; `RunDecisions.MANUAL_RUNNING` false removes the feature.

## Career mode, the player is the non-striker
The computer's striker plays the ball and **calls**: "[Name] calls: Yes! Run!" and the same page asks you to **accept** (up) or **refuse** (down), run by run, only for the runs the shot is worth. A bad call (the simulated run out) is the last run: refuse it and nobody is out. "Skip to my turn" does not ask.

## Not done
Recorded voices for "Yes" and "No" (the phone's voice says them), the striker calling "No" himself, an AI non-striker calling when you bat in career mode, running on a no-ball, measuring how this changes the scoring.
