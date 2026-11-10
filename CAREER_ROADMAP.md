# My Player: the research and the roadmap

Written in the part-nine patch so that nothing found in the research is lost. "Done" means built and working on the phone; "part N" says when it is planned.

## What Cricket 26 and the earlier Big Ant games do (and what players complain about)

Found in the game's own pages, reviews and the Big Ant and PlanetCricket forums:
- A career from club cricket up through state or county cricket to international cricket; menus say how far you are from selection to the next level (Cricket 26 review). **Done (part eight: "Your pathway").**
- Choose an "international affiliation": the national team you aim for, even one different from your domestic country. Not built (the game's six countries pick their own; a later option).
- Contract offers from several clubs at the start and between seasons; contracts and sponsors based on performance and reputation (wishlist). **Franchise offers: part ten.**
- Objectives and XP: players complain that experience does not depend on performance and that objectives (hit ten fours) do not help the career player. Here growth already follows the impact of each match (`CareerProgress`). **Season objectives: part eleven.**
- Training that feels pointless ("I do not train and I am rated in the 90s"). **A training focus for the season: part eleven.**
- "Bland and predictable, you just churn out games": no stakes, no stories. **Series results, Player of the Series lines, honours, records and retirement: parts nine and eleven.**
- No player rankings and no Cricinfo-style statistics. **Career statistics and rankings page: part eleven.**
- Unexplained or silly AI selection (a player always picked and batting at 11): **every selection here gives its reason in a sentence (done); computer squads change over the years: part eleven.**
- Real players never age or retire. **Part eleven (retirement and fresh young players in the national squads).**
- Fatigue, injuries and workload (franchise leagues against national duty): **injuries and workload: part eleven; clashes between league and national duty: part ten.**
- Press conferences before matches (a captain's press conference in Cricket 26). Low value for a screen reader; may come as short text lines after a series.
- Progression pace: the community split between "too slow, thirties before you are capped" and "a satisfying path". Here the pace is a dial: the career difficulty, `CareerProgress.BASE_GROWTH`, and the number of state matches the national selectors want (`MIN_STATE_MATCHES`).

## The real world, used as a rule book
- The ICC calendar 2024 to 2031: a T20 World Cup in every even year, a World Test Championship final and a 50-over event in every odd year (an ODI World Cup in 2027 and 2031, the Champions Trophy in 2029). **Part nine: the career season ends with the T20 World Cup in even seasons and the Champions Trophy in odd ones; for India and Pakistan the Asia Cup in every second odd season.** The game has no ODI World Cup or World Test Championship final yet (a later part may add them).
- Bilateral series of three or five matches in each format, Test series of two to five matches, and old rivalries with five-match series. **Done (part eight).**
- Selection by format: Test, one-day and T20 sides are different; Pakistan's 2026 contracts even move to format-specific tracks (Test, ODI, T20). **Part nine: form is kept in each format and the selectors judge him by it.**
- Franchise leagues: capped and uncapped players, a base price from experience and recent performance, domestic T20 form as a proxy, overseas slots (at most four in an XI, eight in a squad in the IPL), the uncapped players' round, the mini and the mega auction. **Part ten: offers from the leagues the game has, decided by his T20 form, reputation and the franchises' needs.**
- Players choosing leagues over national duty (central contracts declined) and boards blaming leagues for fatigue. **Part ten: league and national duty can clash, and the player chooses.**

## The parts
- Eight: the international level (series, national call-up, pathway). Done.
- Nine: the tournament that closes each international season, form in each format, Player of the Series lines. This patch.
- Ten: league selection (offers, the franchise squads, a league season, clashes with national duty).
- Eleven: career record and rankings, season objectives, a training focus, honours (the Career tab). Done (patch 096).
- Twelve: injuries and workload, ageing, retirement and a career summary (patch 097). Done.
- Thirteen: international captaincy and specialising in a format (patch 098). Done.
- Fourteen: computer squads that age, an ODI World Cup and the World Test Championship final (patch 099). Done.
- Fifteen: contracts and pay, franchise retention, the ILT20 and the papers' verdicts (patch 100). Done.
- Sixteen: an international affiliation (aim for another country's team); real retirements with new named players in the computer squads; ageing for the domestic squads.
- Seventeen: the real 14-team ODI World Cup; the Impact Player and the Power Surge in career matches; the overseas cap checked in the XI the selectors name.
