# Simulate match, and the Power Surge field fix (patch 133)

## Simulate match (Match info > Simulate match)
A button on Match info (after Impact Player, Power Surge, new ball and declare), in any match that is in progress (not in a Super Over, not while a batter is being replaced, not in rain). It opens the Simulate page (`ui/match/SimulateMatchScreen.kt`):
- **A Test:** "By session" or "By over". **Any other match:** by over only.
- **By session:** type how many sessions (1 to 15). The first is the end of the session being played (lunch, tea or stumps).
- **By over:** type the overs, then **Choose balls** (0 to 6, 0 at the start): 12 overs and 5 balls is 12.5 overs. Counted from now, inside the innings being played.
- **Simulate** works when the numbers make sense; a line on the page says what will be simulated.
The computer plays **both** sides ball by ball without any screen (`logic/MatchFastForward.kt`): the ball model of the computer-against-computer matches, its own bowling changes, fields, new batters, Power Surges, declarations and the new ball; rain delays, drinks breaks, the DRS and appeals are skipped. In career mode it plays your own batting and bowling too. It **stops early** and leaves the rest to the match screen when the innings ends (the innings break or the result follows as always), when a team declares, at the end of the session asked for, and at the end of the match. Then the match goes on from that point and you choose the next bowler (when you are the one who chooses). Not measured on play: the scoring is the computer-against-computer model's.

## Power Surge: the field and the no-balls (career mode)
When the computer took a Power Surge while the player's side was bowling, the game left the field alone, expecting the user to fix it. In career mode, when the player is not the captain, the computer sets his side's field and he cannot, so the field stayed illegal (too many in the deep) and every ball was a no-ball. Now `PowerSurge.take` brings the field in at once in that case, and `MatchScreen.ensureLegalField` checks it before every ball in that case (also after a powerplay change), making a legal one if needed. The captain, and a normal match, are as before.
