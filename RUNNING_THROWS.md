# Manual running: throws, relays and overthrows (patch 151)

Before, a run out was always "the fielder hits the stumps", even from the deep. Now (`RunDecisions.resolveAttempt`; `RunDecisions.THROW_MODEL = false` brings the old behaviour back):
- A throw comes in once the runs the ball is worth are being completed (at once when the fielder is close in).
- The fielder either throws **straight at the stumps** or **to the keeper or a fielder nearby**. His confidence comes from his rating and from where he throws from (`directChance`):
  in the deep about 8% to 38% (a poor arm to a great one), in the inner ring 45% to 80%, close in 75% to 95%; the keeper almost always throws at the stumps.
- A ball that was a run out is still a run out (it was simulated that way): only how changes, a direct hit ("A direct hit from the deep!") or a relay ("throws it in from the deep to the keeper, who breaks the stumps").
  The credit goes to the one who broke the stumps.
- An extra run is still a risk: the old chance (`extraRisk`), higher for a direct throw (x1.35), lower for a relay (x0.85).
- **Overthrows**: a throw that does not run him out can go astray (`overthrowChance`: a wild direct throw from the deep most of all, then the inner ring, then close in; a relay rarely) for one run, sometimes two.
  They are added to the runs he completed (byes stay byes), at most three a ball, and the commentary says "Overthrow".
- `RunChance` now knows the keeper and the nearest inner-ring fielder (`relayName`); `RunVerdict` carries the overthrows, who broke the stumps and the words of the throw.
Not measured: how often run outs and overthrows now happen (the run-out chance is kept near the old one; overthrows are new, about a few in a hundred throws).
