# My Player, part twenty-one: old matches are resumed after an update, and a crash cannot repeat

**Why a half-played match is no longer thrown away.** Patch 106 discarded every saved match made by an older build. That was the wrong answer: an update replaces the whole app, so old code cannot run again, but the new code can read an old save. Now it does.

**Repair** (`SnapshotRepair`). A saved match is JSON. When a class has gained a list or a map since the match was saved, Gson would read it as null and the game would crash at the first use. Before the saved match is read, the JSON is walked against the classes as they are now, and an empty list or map is added wherever one is missing
(numbers and true/false already read as 0 and false; fields the new code no longer has are ignored). This covers the usual kind of change. Rule for future changes: a new field on a saved class should be nullable or have a safe default (a list or map is repaired automatically).

**Crash guard** (`CrashGuard`, `MatchSaveStore.markResume`). A resumed match marks itself while its screen is open and clears the mark when the screen is left normally. If the game crashes while a resumed match is open, a flag is written; the next time that saved match is loaded, the flag shows that resuming it crashed the game, and only that
saved match is discarded, instead of resuming it and crashing again for ever. A match whose screen was only closed by the system (no crash) is kept. This is the safety net for a change the repair cannot cover.

**The install-time stamp** (patch 106) is still written into each save but no longer discards anything. A match saved by the build of patch 106 resumes as normal.
