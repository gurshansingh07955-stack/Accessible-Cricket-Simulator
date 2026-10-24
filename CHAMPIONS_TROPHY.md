# Champions Trophy 2025 (patch 046)

Tournament > International tournament > Champions Trophy 2025. The first 50-over tournament in the game.

* **8 teams, 2 groups of 4**: Group A India, Pakistan, Bangladesh, New Zealand; Group B Afghanistan, Australia,
  England, South Africa. Each team plays the other three once (12 group matches, the real fixtures in the real
  order at the real grounds: Karachi, Lahore, Rawalpindi and Dubai, where India played all their matches).
* **Semi-finals**: winner of Group A v runner-up of Group B, winner of Group B v runner-up of Group A, then the
  **Final**. Any match India play is in Dubai; otherwise semi-final 1 is in Karachi, semi-final 2 and the Final
  in Lahore (an assumption for the cases the real tournament did not have).
* **Squads**: the final 15-man squads (replacements included). Five players are new to the game with
  ESTIMATED ratings (Nahid Rana, Nathan Smith, Mohammad Hasnain, Tayyab Tahir, Naveed Zadran); everyone else
  keeps the rating the game already gave them (the ratings are the game's T20-based ones).
* **50 overs a side.** A new ground was added: National Stadium, Karachi (ratings estimated).
* **Computer v computer matches** use an ODI setting in `AiBallModel` (`ODI_*` dials), measured with a node
  model of the ball dice: about 295 runs (twin scale; the same model puts the T20 at 165 where the game plays
  about 150) and 7 to 8 wickets a side, a chase won about half the time. These numbers come from a model, not
  from play: tell me if scores feel too high or low and I will turn the dials.
* Points: 2 for a win, 1 for no result; ties are settled by a Super Over; the table orders by points, wins, then
  net run rate over 50 overs (a side bowled out counts the full 50). The ICC's other tie-breakers are not applied.
* Not built: rain rules and DLS in simulated matches, the real results (the game simulates them).
