# Pitch report before the toss (patch 134)

**Every match with a ground now opens its toss page with the pitch report** (normal matches, every tournament, career mode): `ui/setup/PitchReportScreen.kt`, shown inside `TossScreen` / `CareerTossScreen` before anything else. Back first, then "Continue to the toss", then the report as text (a screen reader reads it section by section): **The pitch** (type, the ground's description, what to expect), **The ground** (city, boundaries, altitude), **Dew**, **Par score** (the average T20 first-innings score there), **The weather** (condition, temperature, humidity, wind, chance of rain, what it means) and **At the toss** (what captains would do and why).

**Not random:** it is written from the ground (`logic/PitchReport.kt`: pitch type and description, boundary size, altitude, dew factor, average score from `logic/StadiumData.kt`) and the weather rolled from that ground's climate. The weather is rolled for the report and **the match keeps the same one** (`logic/MatchWeatherCache.kt`): what is said before the toss is what the match gets (a match with no report, or one more than three hours old, rolls its own as before).

**Commentary:** the same two recorded ElevenLabs voices as the match commentary (the lead commentator excited, the co-commentator calm; the Settings commentary mode decides which of them speak), in turn: welcome, the pitch, the boundaries, the dew, the weather, the advice at the toss. 19 new commentary categories (`PITCH_*`, `BOUNDARY_*`, `DEW_*`, `WEATHER_*`, `ADVICE_*`), 24 pairs, 48 clips, fetched by "Fetch audio into the app" like every other commentary clip. Leaving the screen stops them.

**Crowd:** the toss page already starts the crowd in a normal match, every tournament and career mode, so it is running from the pitch report on; the report asks for it too, to be sure.

**Not done:** the report in the toss of a match the computer plays against itself (nothing is shown there), the report for a format other than the T20 average score, rain-free competitions showing "light rain" in the report (Match info hides it there).
