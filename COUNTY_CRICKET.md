# County cricket: the One-Day Cup 2027 and the Vitality Blast 2027

Tournament > Domestic tournament now also lists the **One-Day Cup 2027** (50 overs) and the **Vitality Blast 2027** (T20). File: `logic/CountyCricket.kt`. Own saved tournament and part-match file each (`saved_tournament_odc_2027.json`, `saved_tournament_blast_2027.json`). The County Championship is the next part and will use the same 18 teams (ids `cc_xx`).

## Fixtures: what is real and what is not
The 2027 fixture lists are **not published yet** (the ECB released the 2026 lists on 25 November 2025, so late November is likely). Only the Blast's Finals Day is confirmed: 17 July 2027 at Edgbaston. The game therefore makes the schedules itself in the published formats. When the real lists come out, a patch can replace the generated order.
- **Groups are the 2026 groups (assumption).** One-Day Cup: A = Leicestershire, Lancashire, Nottinghamshire, Warwickshire, Surrey, Kent, Northamptonshire, Gloucestershire, Somerset; B = Middlesex, Yorkshire, Durham, Worcestershire, Hampshire, Derbyshire, Essex, Glamorgan, Sussex. Blast: North = Notts, Yorkshire, Lancashire, Durham, Derbyshire, Leicestershire; Central and West = Northamptonshire, Somerset, Gloucestershire, Warwickshire, Glamorgan, Worcestershire; South = Hampshire, Surrey, Essex, Kent, Middlesex, Sussex.
- **One-Day Cup**: each county plays the other eight in its group once (72 matches), 4 points a win, 2 for a tie or no result (a tied league match is shared). Top three of each group go through: A2 v B3 and B2 v A3, then A1 and B1 play the quarter-final winners, then the Final at Trent Bridge (as in 2026).
- **Vitality Blast**: 12 group matches each (the other five in the group home and away, one home and one away match against counties outside it), 108 in all, 2 points a win and 1 for a tie or no result; a tie is settled by a Super Over. The top two of each group and the two best thirds reach the quarter-finals; the pairing (1 v 8, 2 v 7, 3 v 6, 4 v 5 by points, higher seed at home) and the semi-final pairing are **assumptions**. Semi-finals and Final on Finals Day at Edgbaston.
- No DRS. Rain works as in the game's other formats.

## Teams, squads, grounds
- 18 counties with the clubs' own squad lists (Wikipedia, 1 November 2026). Team names are the county names (the Blast nicknames are not used). A player already in the game keeps his ratings; the others are **new with estimated ratings** by the county's 2026 strength (Warwickshire strongest). Squads change in the winter: edit a player in the game if one is wrong.
- Grounds: Lord's, The Oval, Edgbaston, Trent Bridge, Headingley, Old Trafford, the Ageas Bowl, Sophia Gardens and Riverside were in the game; **added** (estimated ratings): Derby, Chelmsford, Bristol, Canterbury, Grace Road, Northampton, Hove, Taunton, New Road.

## Not built
Overseas-player limits, the real fixtures and results, the women's competitions, Blast home matches at second grounds (for example Scarborough, Arundel).
