# South African domestic cricket 2026-27, part one

Tournament > Domestic tournament now also lists the **CSA 4-Day Series Division 1 2026-27**, the **CSA Pro50 Cup 2026-27** and the **CSA Pro20 Cup 2026-27**. Code: `logic/SaDomestic.kt`, `ui/tournament/SaTable.kt`. Own saved tournament each. **Part two (not yet built):** the Division 2 competitions (the 3-Day Series and the Pro50 Shield) and the women's competitions are not included.

## What is real
- **Season structure**: CSA changed its domestic season for 2026-27. The T20 Challenge and the T20 Knockout became one 16-team Pro20 Cup (eight Division 1 teams, seven Division 2 teams and CSA Emerging), the one-day competition is the Pro50 Cup, and the first-class competition is the 4-Day Series (Division 1: eight teams).
- **Fixtures**: Cricinfo and CSA's lists as of 31 October 2026, in the published order. The season is already under way in real life; the game starts from the beginning and simulates every result. 4-Day Series: 40 league matches and a five-day Final (26-30 April). Pro50 Cup: 28 matches (the published list has no Final yet: the game plays one between the top two, an assumption). Pro20 Cup: 56 pool matches, then the Super Eights (every qualifier from one pool plays every qualifier from the other, 16 matches), semi-finals and the Final, all at Newlands (14 November in real life).
- **Home sides**: the published 4-Day list is in home-first order (the first eight, already played, were corrected from CSA's list). For the Pro20 Cup the venue is the real one for each match; for the Pro50 Cup venues were not all published, so the first-named team hosts.
- **Teams and squads**: the 15 contracted squads of 2026-27 (Club Cricket SA, 20 October 2026), including the Cricket South Africa contract players (they play when free in real life). Players already in the game keep their ratings; the rest are new with **estimated ratings**, and many have a guessed role. **SA Emerging has no published squad**: it is made from the young high-performance-contract players of the other teams.
- **Teams in the Pro20 Cup**: Pool A: Titans, Western Province, Knights, Boland, Mpumalanga, Easterns, KZN Inland, SA Emerging. Pool B: Warriors, North West, Northern Cape, Border, Lions, Dolphins, Limpopo, South Western Districts.
- **Grounds**: Newlands, the Wanderers, SuperSport Park, Kingsmead, St George's Park, Boland Park, Senwes Park, Kimberley and East London were in the game; **added** (estimated ratings): Mangaung Oval, University of Pretoria, White River, Willowmoore Park (Benoni), Oudtshoorn, Polokwane, Pietermaritzburg.

## Points
- 4-Day Series (CSA): win 16, tie 8, draw 6, plus batting bonus (1 point at 150 runs, then 0.02 a run) and bowling bonus (1 for 3 wickets, then 1 for each two more), from the first innings only; the real rule counts only the first 100 overs. Kept in hundredths; the Final is decided on the first innings if drawn.
- Pro50 and Pro20: 4 for a win, 2 for a tie or no result (the real bonus points are not built; the Pro20 value is an assumption). A tied Pro50 league match is shared; a Pro20 tie goes to a Super Over. No DRS.

## Not built
Division 2 (part two), the real results so far, bonus points in the one-day cups, overs limits in the first 100 overs.
