# Career Mode, part twenty-five: the shop

Career tab > Earnings now has two tabs: **Earnings** (the money lines, as before) and **Shop**.

- **Gear:** batting gloves and pads, each in three kinds: Silver, Golden, Diamond. Bought with career earnings (`CareerProfile.money`); buying asks first ("Yes, buy" / "Cancel"). Buying a better kind replaces the old one (no refund); gear does not wear out. You cannot buy what you already have or something worse.
- **Prices (dollars, `CareerShop.price`):** Silver gloves 120, pads 180; Golden gloves 1,500, pads 2,200; Diamond gloves 18,000, pads 26,000. Chosen against what a player earns at each level: a club match fee is 25, state 120, international 1,500 to 4,000, a league or central contract tens of thousands.
- **Protection (`CareerHealth.rollWithGear`):** each working item cuts the chance of an injury after a match by up to 20% (`CUT_PER_ITEM`), gloves and pads together by up to 40% (the chance is never cut below half). How well an item works depends on the level of the match: Silver is made for club cricket (level 1), Golden for state (2), Diamond for international and franchise league cricket (3). Gear at or above the level of the match works fully, one level below at half strength, two levels below at a quarter (`CareerShop.coverage`). A franchise league match counts as level 3. When gear turns an injury into none, the match report says so ("Your gloves took a hard knock and saved you from an injury.").
- **Not changed:** how long an injury keeps him out, and the injury names (they do not depend on the item).
- **Dials:** `SHOP_ENABLED` (false closes the shop), `GEAR_PROTECTS` (false keeps the shop but gear no longer changes the chance), `CUT_PER_ITEM`, the prices.
- The 20% per item and the prices are the game's own numbers, not measured on play.
