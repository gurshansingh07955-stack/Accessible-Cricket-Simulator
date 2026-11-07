# My Player, part five: selection, form and growth

**Selection.** Before each match the selectors decide whether the player is in the club's XI (`CareerSelection`). He is ranked against the other
players of his club by his value in his role; the selectors take the top N, where N depends on the career difficulty (Very easy takes almost
everyone, Easy 14, Medium 11, Hard 9, Legend 7). His recent form (the average impact of his last three matches against 25 points, a place for
every ten, up to three places either way) moves him up or down. The reason is spoken on the Game play tab and the Home tab.

**Playing.** If he is selected, Play my match skips the Playing XI screen: the selectors name the XI with him in it, then the toss and the match. If he
is left out he cannot play the match, only simulate it; in a simulated match he is in the XI or out of it by the same decision.
FOR NOW the player who is in the XI still controls the whole team in a match he plays; taking over only his own batting and bowling is the next part.

**Form and growth.** After every match he plays (played or simulated) `CareerProgress` records his impact points (the game's Player of the Match
points: runs, wickets, catches, a win bonus) as his form, and grows his ratings: more after a good match, much more when young (a 16-year-old at full
speed, a 30-year-old barely), shared between batting, bowling and fielding by role. The Home tab shows the growth since the start and the matches
played. Dials: `CareerProgress.BASE_GROWTH`, `IMPACT_PAR`; `CareerWorld.selectionFactor`.
