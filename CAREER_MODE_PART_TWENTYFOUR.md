# Career Mode, part twenty-four (the news and the press conference)

## First part (patch 110): the news after every match
- `logic/CareerNews.kt`. After a match of the player's (played or simulated): the result, what he did (a hundred, a fifty, a five-wicket haul, three or more wickets, 30 or more runs, Player of the Match) and, in a final, the title. After every step of the season: the real results of the other matches just played in his competitions (knockout matches first, at most two) and one or two made-up headlines about world cricket (an international result, a star's performance, a series, a squad, an injury scare). The made-up ones are not real results; `GENERATED_WORLD_NEWS = false` removes them. `NEWS_AFTER_EVERY_MATCH = false` brings back the old way.
- `CareerProfile.newsFeed` (`NewsItem(text, mine)`, the last 80 lines). The season-end lines of `CareerMedia` are put in the feed too; older saves keep their `media` lines, shown first.
- Career tab > In the news: two tabs, All and My news, the newest first.

## Still to come
- Second part: the press conference (after a Player of the Match or Player of the Series in international matches and tournaments and in league matches): a pop-up, 5 or 6 questions with several variants, 4 or 5 answers each, in text.
- Third part: its audio (ElevenLabs: reporters' voices, the player's voices, crowd and camera sound effects) and the player speaking the chosen answer.
