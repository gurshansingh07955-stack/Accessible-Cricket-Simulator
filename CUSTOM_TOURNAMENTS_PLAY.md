# Custom tournaments, part 2: play them (patch 053)

**Tournament > Custom tournaments** lists the tournaments saved in Advanced Customisation. Open one for its start
page (new tournament or resume): pick your team, Playing XI, toss and match, tables, venues and stats work as in
every other tournament. Its own save files are kept per tournament.

## Schedules
Round Robin and Double Round Robin (the team on top of the table wins), Round Robin and Final, Round Robin +
Semi-finals + Final (1st v 4th, 2nd v 3rd), Round Robin + IPL-style Playoffs (the game's own Qualifier 1,
Eliminator, Qualifier 2, Final), Knockout (random draw; byes in a Preliminary round when the number of teams is not a
power of two) and Two Groups + Semi-finals + Final. Matches are spread over the chosen stadiums in turn.

## Rules inside matches (`MatchState.rules`, `TournamentRules`)
* **Overs**: the format's overs (custom formats too). A bowler's limit is the format's (a custom format: a fifth of the
  innings, rounded up).
* **Fielding restrictions**: how many balls and how many fielders may stay outside the circle (the cap is held in
  `FieldingSystem.powerplayDeepCap`, reset to 3 whenever a normal match starts).
* **DRS**: off, 1 to 4 reviews an innings, or unlimited (spoken as "unlimited reviews").
* **Rain**: switched off, no rain interruption happens.
* **Edit Tournament**: a game tournament given another format plays it from now on (its fixtures stay as they are),
  and a renamed tournament shows its new name on its start page. Tournaments already in progress keep the format
  they started with only until their next match.
* Computer-v-computer matches use the same rules; a 40-over or longer innings uses the ODI scoring setting of the
  ball model, shorter ones the T20 one.

## Things that are approximations
A custom format picks its engine constants (the odds of a catch, DLS, drinks) from the game format nearest in overs;
the AI's opening field in the first over uses the restriction exactly, the user's own field is checked against the cap.
