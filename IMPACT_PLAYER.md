# Impact Player (IPL only)

The IPL season uses the real competition's Impact Player rule (`logic/ImpactPlayer.kt`). The CPL and the normal game do not.

## The rules as built
- Before the toss each side names five substitutes. Your screen, "Name your Impact Players", comes after the Playing XI and
  before the toss (Auto-pick is there). The computer names its five too.
- Once per match, a side may bring one of them on in place of any player of its XI.
- Only while the ball is dead: at the start of an innings, when an over ends, or when a wicket falls.
- No later than the end of the 14th over. Not in a match shortened to fewer than 10 overs a side, not in a Super Over.
- The player who goes off takes no further part (his figures so far stand and count for the series). The Impact Player may
  bat and bowl a full share (a fresh four overs).
- Not a batter at the crease, not the bowler of the over.
- The XI may never have more than four overseas players.
- A replaced captain's role goes to the vice-captain (or the best remaining player); a replaced wicketkeeper is replaced
  behind the stumps by another keeper.
- Not built: the real rule's injury and concussion substitutes; computer-v-computer matches (played by the quick model) do not
  use Impact Players.

## In your match
After a wicket falls you are asked "Use your Impact Player?" once per wicket (until you use it or the 14th over has passed).
At any other allowed moment there is an "Impact Player" button under Scorecard; when it is not available the line there says
why. Choose who comes on, then who goes off, then Confirm. The result screen lists the swaps.

## How the computer chooses
Its five substitutes: the best batter, fast bowler, spinner and all-rounder outside its XI (an Indian player first where the
choice is close), then the best of the rest. When to use it: at each allowed moment it values every player by what he can still
give the side from then on (his batting if the side still has batting to do, bowling if it still has bowling to do, a bowler's
overs left, a batter who has already batted being worth nothing more with the bat) and makes the swap with the biggest gain
if the gain is enough for that stage: a lot early, little by the 12th over, anything at the end of the 14th. So a side that bowled
first typically brings a batter in for a bowler who has finished his overs at the innings break; a side that batted first brings a
bowler in for a batter who has batted. A wicket just fallen makes a batting swap a little easier. A small chance of waiting one more
moment keeps the timing from being predictable.

# Other changes in this update
- The computer batter never leaves a ball in T20 and the other limited-overs formats (`allowLeave`); a Test batter still does.
- A ball outside off stump or outside leg stump can no longer bowl a batter (left or played) and cannot be lbw: if he is out to it
  he is caught (or stumped).
