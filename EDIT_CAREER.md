# Edit career (patches 143 and 145)

My Player > Resume career > Player profile > **Edit career** (above Delete this career).

**Cannot be changed:** name, age, country, state, club, school, college, gender, and (patch 145) the **ratings**: batting, bowling and fielding ratings are earned by playing.
**Can be changed:** profile photo, jersey number, role, batting hand, bowling arm, bowling style, bowling phase, batting position and number, fielding position, career difficulty.

- `logic/CareerEdit.kt`: `Draft`, `fix` (makes the choices agree: a batsman does not bowl, a wicketkeeper-batsman keeps wicket, a number belongs to its position, a style to its arm), `apply` (career AND his entry in his club's squad; ratings untouched). Switch: `CareerEdit.ENABLED`.
- `ui/career/CareerEditScreens.kt`: the list, one page of choices per row, "Save changes" under the heading, confirm before leaving with unsaved changes.
- A competition already under way picks up the new role and bowling style before his next match (`CareerSeason.withUserPlayer`).

# Knockout matches in career mode (patch 145)

Bug: when a player's last league match was played, the other teams' matches were simulated with `SimMode.ALL`, which also simulated the knockout match (quarter-final, semi-final, play-off, Final) that his team reached, without him. Now `CareerSeason.advance` runs them with `SimMode.UNTIL_MINE`: they stop at his knockout match and he plays it. The Play tab's button after his last league match is now "Play on" (it plays the others' matches, then sets up his knockout match, or ends the season if his team is out). "Simulate the whole season" still goes through everything, but now plays his knockout matches through his own career (stats, form, ratings) instead of skipping him.
