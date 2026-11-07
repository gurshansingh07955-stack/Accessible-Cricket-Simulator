# My Player, part three: the last choices, the summary, the career home and the random club teams

After the batting position (and the bowling phase for a bowling role) the career asks: the batting NUMBER inside the chosen position
(top order 1, 2 or 3; middle order 4, 5 or 6; low order 7, 8 or 9; lower order 10 or 11), the fielding position (16 positions;
a wicketkeeper-batsman keeps wicket and is not asked), and the career difficulty: Very easy, Easy, Medium, Hard, Legend. The difficulty is
stored and will scale how much form the selectors ask for in the later parts (`CareerWorld.selectionFactor`: 0.6, 0.8, 1.0, 1.25, 1.6).
Then a summary of every choice; Start my career makes the career and opens the career's HOME PAGE: My club squad, Clubs of the state
(every district club and its squad), Matches (switched off: the next parts), Player profile (details, ratings, delete).

Random club teams (`CareerGenerator`): every district club of the state gets 15 players (two keepers, four batsmen, two batting and two bowling
all-rounders, five bowlers) with a role, hands, bowling style, and batting, bowling and fielding ratings of about 20 to 62. Names come from the
state's name pool (Punjabi, Hindi-belt, Marathi, Gujarati, Bengali, Odia, North-eastern, Telugu, Kannada, Tamil, Malayali, Konkani, Kashmiri;
English, Welsh; Australian; New Zealand; Afrikaans, African, mixed South African; Pakistani Punjabi, Pashtun, Sindhi, Baloch, Kashmiri), and no
two players of a state share a name. The player takes the place of a team-mate of his role in his own club and starts with modest ratings
(about 40 to 48 in his strength).

Not built yet: the domestic season (league matches between the clubs, then the state and domestic tournaments), selection, progress.
