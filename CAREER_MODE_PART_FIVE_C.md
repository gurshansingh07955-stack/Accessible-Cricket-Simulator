# My Player, part five c: a debut, and T20, 50-over and 3-day club leagues

**Selection fixed.** A player's FIRST match is always a debut. After that he is ranked against the club-mates of his own role (batsmen against batsmen, bowlers
against bowlers), because an XI has a fixed number of places per role (one keeper, three batsmen, two batting and two bowling all-rounders, three bowlers). The difficulty
widens or narrows those places. His first three matches count one place in his favour and so does being 19 or younger; form still moves him up or down by up to three
places. A newcomer's starting ratings are also a little higher (nearer the middle of his club).

**Three leagues.** The season is now three competitions between the district clubs of the state, each its own table and statistics: a T20 League (20 overs), a 50-Over League and
a 3-Day League (three-day matches, two innings each, played like a Test: sessions, declarations, follow-on, new ball, rain). Each is a single round robin; the matches take
turns week by week (T20 on a Saturday, 50-over the next week, 3-day Friday to Sunday the week after). The Points table tab has a T20 / 50-over / 3-day chooser, then Points table
and Stats (All, My team). A drawn 3-day match is a point each. A career's competitions are played by `CareerSeason.rulesFor`.

**Playing a 3-day match as the player.** The same rules as the shorter ones: he takes over only his own batting and bowling. His captain declares, enforces the follow-on and takes the
new ball for him (`careerPlayerId` switches those prompts off in `TestMatch`); lunch, tea and stumps still stop the game; the balls that are not his are played by the game, 0.9 seconds
apart in a Test, and Skip to my turn speeds that up.

An old career's single-league season (part four) is not carried over: its leagues are made afresh the first time the hub is opened. Its form and ratings are kept.
