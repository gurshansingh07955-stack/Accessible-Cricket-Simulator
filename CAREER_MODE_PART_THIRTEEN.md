# My Player, part thirteen: national captaincy and format specialism

**Format specialism** (Career tab, "Format specialism", for players of the state squad and above). All formats, Test specialist, or White-ball specialist (one-day and T20). A specialist gets a place in his favour with the selectors in his format and a place against him in the other
(`CareerInternational.specialismFit`, in the match decision, and the reason says so). In the franchise leagues a white-ball specialist gets a place more, a Test specialist two places fewer (`CareerLeagues`, both the offers and the selection). It can be changed at any time.

**National captaincy** (`CareerInternational.captaincy`, decided when an international season starts). A leadership score: a point for every ten international matches, his form (-1 to +1), his standing among the squad's best players (top three +1.5, top six +0.5) and age 26 or over (+1).
At 4.0 with 12 international matches he is made **vice-captain**; a vice-captain at 6.0 with 25 matches is made **captain**; a captain or vice-captain whose score falls under 3.0 loses the role. The appointment is announced in the new-season message and added to his honours; the pathway and the Career tab say how far he is from it.
A national captain is always in the XI, picks the XI and does the toss in the national matches, as the club captain does (`CareerProfile.isCaptain`). A captain who is also signed by a franchise league also picks that league's XI (a known simplification).

**Dials.** `VICE_MATCHES`, `CAPTAIN_MATCHES`, the thresholds in `captaincy`, the place values in `specialismFit` and the league graces.
