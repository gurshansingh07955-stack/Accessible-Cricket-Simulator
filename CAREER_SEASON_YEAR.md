# Career seasons have years (patch 138)

A career made from patch 138 on starts in **2026** and each new season is the next year: 2026, 2027, 2028 ... Every tournament and series carries its year, and the match dates fall in that year.

- **Only new careers.** `CareerProfile.startYear` (nullable, like every field added to a saved class) is set when a career is created (`CareerSeason.FIRST_SEASON_YEAR`, 2026). A career without it (every earlier one) behaves exactly as before: calendar dates, and the old names ("Ranji Trophy, season 3").
- **The year of a season** is `startYear + season - 1` (`CareerSeason.seasonYear`).
- **Names:** "Vijay Hazare Trophy 2027", "Ranji Trophy 2028", "Indian Premier League 2027", "T20 World Cup 2028" (`CareerSeason.nameWithYear`); a series ends "in 2027" ("India 3-match T20I series v Australia in 2027"); "World Test Championship final 2027: India v Australia". The club leagues already had a year in their names and now show the career's year.
- **Dates:** the season starts on the first Saturday on or after the calendar's date moved into the career's year (`CareerSeason.firstSaturday(career)`), so every date shown ("Saturday 27 November 2027") is in the right year and on the right weekday. Leagues start from the season's first match plus their usual offset, so they follow.
- **A new season's text** now says "The year is 2027." for a new career.
- To change the first year, change `CareerSeason.FIRST_SEASON_YEAR` (it only affects careers made afterwards).
