# My Player, part twenty: no "Earlier matches", and no crash from a match saved before an update

**Career record.** The "Earlier matches" tab is gone. Records kept before the full record began had no tournament detail, so Club, State and League totals from before are no longer shown; the Test, ODI and T20 international totals still are (they have a tab).

**A half-played match never survives an update** (`MatchSaveStore`). Every saved match now carries the time the app was installed or updated (`buildStamp`). When a saved match is loaded and it was saved by another build, it is discarded (and its file deleted), so the game can never try to resume a match whose data
no longer fits the new code, which could crash it. This covers every saved match in the game: a normal match, the tournaments, the IPL, the customised tournaments and the career. Tournaments, seasons and careers are not affected: they are kept across updates. The first time after this update, a match half played with an older build is discarded once.
If the install time cannot be read, nothing is discarded. Finish a match before updating the game.
