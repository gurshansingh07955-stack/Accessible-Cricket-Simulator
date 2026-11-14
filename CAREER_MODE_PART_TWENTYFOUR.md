# Career Mode, part twenty-four (the news and the press conference)

## First part (patch 110): the news after every match
- `logic/CareerNews.kt`. After a match of the player's (played or simulated): the result, what he did (a hundred, a fifty, a five-wicket haul, three or more wickets, 30 or more runs, Player of the Match) and, in a final, the title. After every step of the season: the real results of the other matches just played in his competitions (knockout matches first, at most two) and one or two made-up headlines about world cricket (an international result, a star's performance, a series, a squad, an injury scare). The made-up ones are not real results; `GENERATED_WORLD_NEWS = false` removes them. `NEWS_AFTER_EVERY_MATCH = false` brings back the old way.
- `CareerProfile.newsFeed` (`NewsItem(text, mine)`, the last 80 lines). The season-end lines of `CareerMedia` are put in the feed too; older saves keep their `media` lines, shown first.
- Career tab > In the news: two tabs, All and My news, the newest first.

## Second part (patch 111): the press conference, in text
- `logic/CareerPress.kt` (the questions and the rules) and `ui/career/CareerPressScreen.kt` (the page).
- **When:** after a Player of the Match award (a match he played) or a Player of the Series award (the competition has just ended) in an international match, an international tournament or a franchise league. Not at club or state level. `CareerProfile.pendingPress` keeps it until it is held, so it also comes after a simulated match, or after the app was closed. The newest award replaces an older one that was not held yet. `PRESS_CONFERENCES = false` switches it off.
- **What:** six questions: an opener, the award, what he did (batting, bowling or both), the match (a win or a defeat), and two more from pressure (always asked in a final or knockout), the team, what comes next and his life. 31 questions in all, each with four or five answers in a tone: Humble, Team first, Confident, Bold, Careful. The context (award, what he did, win or defeat, big occasion) picks which variant is asked; the trigger's `seed` fixes the six.
- **Question and answer texts never hold a name or a number**, so their recordings (third part) are always right. Every answer has a stable id: `<question id>_a` to `_e` (for example `pq_open_1_c`).
- **What it changes:** `pressMood` (what the papers make of the answers; each answer carries its own mood) and one line in the news ("Press conference: ..."). It does not touch ratings or selection. Skipping costs one point of mood.
- **Page:** Skip (first button), the opening words, then for each question the reporter's question, the answers (each with its tone), what he answered, and Next question or Finish.

## Third part (still to come): the audio
- ElevenLabs: reporters' voices (several), the player's voices for the answers (new voices), press-room sound effects (camera clicks, murmur, a microphone being handed over); the player speaks the answer he chooses, the reporter's question is played before it is shown as text.
