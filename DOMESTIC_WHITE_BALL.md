# Syed Mushtaq Ali Trophy 2026-27 and Vijay Hazare Trophy 2026-27

Tournament > Domestic tournament now lists five: the Ranji Trophy, the Duleep Trophy, the Irani Cup, the Syed Mushtaq Ali Trophy (T20) and the Vijay Hazare Trophy (50 overs). File: `logic/DomesticWhiteBall.kt`. Each has its own saved tournament (`saved_tournament_smat_2026_27.json`, `saved_tournament_vht_2026_27.json`) and part-match file.

## What is in the game
- The 38 Ranji Trophy teams (ids `rj_xx`): four Elite groups of eight and a Plate group of six, the **real group fixtures in the real order** (Cricinfo, 31 October 2026). Elite group matches are 28 a group, the Plate group 15. The groups are the real 2026-27 ones for each tournament (Mizoram promoted into the Elite in the T20, Services relegated to its Plate group).
- **T20 (SMAT)**: top two of each Elite group go to the Super League (assumption from 2025-26: Group A = A1, B1, C2, D2; Group B = C1, D1, A2, B2), each Super League group a round robin of four at Nagpur, the two group winners play the Final. The Plate Final is between the top two of the Plate group at Sovima.
- **50 overs (VHT)**: quarter-finals A1 v C2, B1 v D2, C1 v A2, D1 v B2, semi-finals QF1 v QF4 and QF2 v QF3 (assumption, as in the Ranji Trophy), the Final, all at Visakhapatnam. The Plate Final is at Indore.
- Points: 4 for a win, 2 each for a tie or no result (shown in the tables), then wins, then net run rate. No DRS, no rain. In the 50-over event a tied league match is shared; every other tie is settled by a Super Over (assumption).
- Grounds: all already in the game. Mumbai matches are at BKC and the Wankhede, Kolkata ones at Kalyani and Eden Gardens, the Visakhapatnam group at the ACA-VDCA Stadium, Mohali and New Chandigarh (Mullanpur) for group C. In the 50-over event Rajkot, Ranchi and Indore are right; **which of Ahmedabad and Bengaluru (Alur) hosts group B and which group C is an assumption.**

## Squads
The real squads are named a few days before each tournament starts. Until then each team is its **Ranji Trophy squad** (see `RANJI_TROPHY.md`) plus well-known players added from the game's own players (the roster, the IPL pool, the leagues): for example Kohli and Pant for Delhi, Suryakumar and Dube for Mumbai. Which state each player plays for is from general knowledge of last season; a name the game does not have is skipped, nobody is created. `ADD_STARS = false` in `DomesticWhiteBall.kt` gives plain Ranji squads. Edit a player in the game if a squad is wrong. Ratings of Ranji players are the estimates described in `RANJI_TROPHY.md`.

## Not built
The real results (the game simulates them), the real squads, the over-rate and bonus rules, these tournaments in Advanced Customisation.
