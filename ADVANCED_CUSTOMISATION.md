# Advanced Customisation (patch 050)

Home has a new choice after Tournament: **Advanced Customisation**, with three options:

* **Create Custom Format** (built)
* **Create Custom Teams and Players** (coming soon, shown switched off)
* **Create Custom Tournaments** (coming soon, shown switched off)

## Create Custom Format

A question-by-question flow (every page starts with Back, then a heading; text boxes are focused as the page opens):

1. **Name** (keyboard), up to 30 characters, no two formats with the same name.
2. **Number of overs** (keyboard), 1 to 50.
3. **Fielding restrictions**: Yes or No.
4. If Yes: **overs of restriction** (keyboard, 1 up to the number of overs), then **how many fielders may stay
   outside the circle** (0 to 5).
5. A **summary**, then Save format.

Saved formats are listed on the Create Custom Format page with a Delete button each (with a confirmation).

## Where they live and what they do

* `logic/CustomFormat.kt` (the data), `persistence/CustomFormatStore.kt` (one JSON file, `custom_formats.json`),
  `ui/custom/AdvancedCustomisationScreens.kt` (the screens).
* A custom format is **not** a `MatchFormat`: it is never shown on the Play match format screen, and it is meant to
  work only inside custom tournaments, which are not built yet. For now they can be created, listed and deleted.
* The summary also states each bowler's limit: one fifth of the innings, rounded up (the game's own rule for 20 and
  50 overs). This is an assumption for custom tournaments, easy to change in `CustomFormat.maxOversPerBowler`.
