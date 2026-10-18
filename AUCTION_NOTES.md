# IPL auction: how the teams bid, and the speed

## The other nine teams (`IplAuction.maxBid`, switch `SMART_TEAMS`)
Measure it: `node tools/auction_twin.js`. Before, a team's willingness was market value x a little for need x a random
appetite. Nobody had a plan: every team spent the whole purse, squads came out lopsided and every player was chased alike.

On a pool shaped like the real one (60 auctions of ten teams), before and after:

| | Before | After |
|---|---|---|
| Purse left per team | 0.2 crore | 2.1 crore |
| Teams with fewer than 2 keepers | 58% | 0% |
| Teams with fewer than 3 spinners | 100% | 1% |
| Teams with fewer than 4 fast bowlers | 95% | 0% |
| Gap between the best and worst team's XI | 4.7 rating points | 1.5 |
| Average XI strength | 77.9 | 81.0 |
| The ten dearest players cost, on average | 24.6 crore | 24.7 crore |

A team now has a CORE (6 batters, 4 all-rounders, 2 keepers, 5 fast bowlers, 3 spinners, each good enough to play) and
TARGETS the best few players still to come in the kinds it is missing, paying a premium for them. It keeps money back
for the core places still empty, caps how much of its purse one player can take (stars 22%, good players 21%, depth
8%), keeps a squad place for every missing core player, avoids hoarding one kind and does not waste overseas places.
Each team has its own taste (fixed per team) and a small appetite that varies by player.

The dials are the private constants at the top of the "How the other nine teams decide" block.

## Speed (chosen after the team)
Normal is the default and is exactly as before. Fast: the pauses are about 2.4 times shorter (`FAST_BID_WAIT_MS`,
`FAST_COUNT_STEP_MS`, and the smaller quick-mode and hammer pauses in the hall) and the auctioneer's recorded voice plays
at 1.35 times speed at the same pitch (`FAST_VOICE_SPEED`, `AuctionVoice.speed`). The hall still waits for the voice to
finish each call before timing the next step, so voice and bidding stay in step. The speed is saved with the auction
(`IplAuctionState.fast`).
