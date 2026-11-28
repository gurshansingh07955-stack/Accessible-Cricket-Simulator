# Welcome screens (patch 160)

Shown **once**, the first time the game is opened (`ui/home/WelcomeScreens.kt`, remembered by `persistence/WelcomeStore.kt`; routed from `MainActivity` as `Screen.Welcome`):

1. **Welcome**: what the game is (made for blind and low-vision players, played by touch and ear with TalkBack and gestures; quick matches, series, tournaments, career, net practice, custom teams, players, stadiums and tournaments; commentary, sounds and autosave), with **View credits** (the Credits page, Back returns to the welcome) and **Continue**.
2. **Choose your difficulty**: Easy, Medium, Hard, Hardest as radio rows that say what they mean; the choice is saved at once (the same setting as Settings > Difficulty) and **Continue** moves on.
3. **Tutorial**: **Continue to tutorial** goes straight to net practice (Back from net practice then returns to the home page); **Skip tutorial** goes to the home page.

The welcome is marked as seen when the last choice is made, so leaving the app earlier shows it again next time. Everyone who installs this version sees it once, including players who update an existing install (testers included); clearing the app's data (or uninstalling) shows it again. The system Back button does what the on-screen Back does; on the very first page it closes the app. Each page has a real heading, Back first in the reading order.
