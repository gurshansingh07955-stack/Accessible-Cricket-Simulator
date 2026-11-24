# Edit career (patch 143)

My Player > Resume career > Player profile now has an **Edit career** button (above Delete this career).

**Cannot be changed:** name, age, country, state, club, school, college (and gender, which is Male for now).
**Can be changed:** profile photo, jersey number, role, batting hand, bowling arm, bowling style, bowling phase, batting position and number,
fielding position, career difficulty, and the batting, bowling and fielding ratings (1 to 95).

How it works
- `logic/CareerEdit.kt`: `Draft` (the choices), `fix` (makes them agree: a batsman does not bowl, a wicketkeeper-batsman keeps wicket, a number belongs to its position, a style to its arm), `apply` (writes them into the career AND into his entry of his club's squad). Switch: `CareerEdit.ENABLED`.
- `ui/career/CareerEditScreens.kt`: the list of rows (each reads its value), one page of plain choices (or a number box) per row, "Save changes" just under the heading, a confirm before leaving with unsaved changes. The photo is saved when chosen.
- A role that adds bowling leads through bowling arm, style and phase; a role that takes the wicketkeeping away asks for a fielding position.
- The ratings he started with move with the ratings, so "growth since the start" keeps counting only what he gains by playing.
- A competition already under way picks up the new role and bowling style before his next match (`CareerSeason.withUserPlayer`); the Home tab's pathway and rank lines are recalculated after an edit.
- Not changed here: the level he plays at, his captaincies, his money, his record and his honours.
