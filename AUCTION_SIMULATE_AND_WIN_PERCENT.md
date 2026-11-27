# IPL auction: Simulate with your own team, and win percent in the first innings (patch 153)

## 1. "Same players again and again" when simulating (IplAuction.SIMULATE_WITH_USER_TEAM)
Cause (found after patch 152): `autoLot`, used by "Simulate the rest of the auction", ran the lot with `userOut = true`, and `aiRespond` always left the user's team out. So in a simulation the
user's team never bid for anybody; at the end `fillSquads` topped it up with the best of what was left, at base price, which is nearly the same every time. The other nine teams were
already varied (measured on the auction twin: the same team's notable players overlap about 7% between two auctions).
Now `runAll` uses `autoLotAll`: the user's team bids like the other nine, with the same team brain (core XI, targets, purse management, taste). "Skip the rest of this set" and "Pass" are unchanged
(they mean the user sits those players out). The button now reads "Simulate the rest of the auction (your team bids too)".
Patch 152's per-auction variety was measured too, on the twin with sets built like the real ones (chunks of 8): it does not harm balance (purse left about 1.9 cr a team, XI spread 1.6).

## 2. Win percent in both innings, changing every ball (logic/WinProbability.kt)
The line "Win probability" on the match screen used a coarse table (five required-run-rate bands) and only in the chase; in the first innings it was a constant 50, so nothing was shown.
Now it is shown in both innings of a limited-overs match and it is a smooth estimate (see WinProbability.kt): par for the format and ground, the wickets in hand against a normal innings,
the projected total against par (first innings) or the runs the balls left are worth against the runs needed (the chase). The line names both teams: "Win probability: India 54 percent, Australia 46 percent."
Checked on example matches in node: 50 percent at the start of the first innings; at 80/2 after 10 overs of a T20, a dot ball moves it about 1 point, a six about +6, a wicket about -10.
Test matches: a figure only for the last innings of a chase (the old estimate). The crowd's tension still uses the old function. It does not know the strength of the two teams, only the state of the match.
Dial: WinProbability.FIRST_INNINGS (false hides the first-innings figure).
