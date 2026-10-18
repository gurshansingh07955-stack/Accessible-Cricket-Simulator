# Your own sounds

Put them in `tools/user_sounds/` (any format: mp3, wav, ogg, m4a). "Fetch audio into the app" converts each to
mp3 and packs it as `us_<name>.mp3`; "Build APK" then ships it. Every sound is optional: if a file is missing the
game plays the sound it played before.

| File name | Plays when |
|---|---|
| `background_loop` | The crowd sound, from the toss to the end of the match (instead of the old crowd recording). Volume: Settings > "Crowd volume", as before |
| `mainmenu` | The background music of the menus: the home screen, setup screens, Settings, the tournament and IPL lists. It fades out when the crowd starts (toss, anthems, match) and back in after, and stays off in the auction hall. Volume: Settings > "Background music volume" |
| `bat_swing` | The batter chooses his intent (the two-finger swipe, or the Intent list in menu batting) |
| `bowler_steps` | A delivery starts: the bowler runs in, as the batter's choices open (replaces the old whoosh) |
| `bowl_release` | The shot, its intent and its timing are all done: the ball is released and played |
| `bowled` | Bowled, stumped or run out (caught and lbw keep the old wicket sound) |
| `defensive_shot` | A dot ball with no edge |
| `edge_of_bat` | Any ball that takes an edge (including an edged four or six) |
| `four_runs` | A four that is not off the edge |
| `six_runs` | A six that is not off the edge |
| `runs_1_2_3` | 1, 2 or 3 runs with no edge |
| `noball` | A no-ball: it takes turns at random with the old siren |
| `menuopen` | A menu or settings screen opens |

Dials, all in `SoundEngine.kt`: `USER_SOUND_GAIN` (loudness of the short sounds), `BACKGROUND_LOOP_GAIN` and
`BACKGROUND_DUCK_FACTOR` (the crowd loop at 100% and how far it dips under speech), `MENU_MUSIC_GAIN` (the music at 100%),
`USE_USER_SOUNDS`, `USE_BACKGROUND_LOOP` and `USE_MENU_MUSIC` (switches that bring the old sounds back).

The loop and the music are played as normal looping tracks, so a tiny gap at the loop point is possible on some
phones; a file whose end flows into its start sounds best.
