# Player profiles (patch 044)

What changed:

* **Roles fixed.** 15 batsmen who keep wicket are now wicketkeepers (for example Jonny Bairstow, Phil Salt,
  Heinrich Klaasen), and a few all-rounder / bowler / batsman roles were corrected where the game's own
  ratings agree with the real role. Pace/spin flags corrected for 86 bowlers.
* **Squad lists are split under headings**: Batsmen, Wicketkeepers, All-rounders, Bowlers - on the Playing XI
  screen (also used by every tournament), the tournament squad page, the IPL "Check squads" tabs.
* **Every player shows**: role, batting hand and batting rating, bowling style and bowling rating
  (for example "Batsman. Batting: right-handed, rating 95. Bowling: right-arm off break, rating 22.").

Where it lives: `logic/PlayerProfiles.kt` (the table is one line per player: name, hand, bowling code,
role override, style override). Source: Wikipedia cricket infoboxes, cross-checked with the game's ratings,
plus a few entries by hand. Players with no page found show no hand or style.

Switch: `PlayerProfiles.USE_PROFILE_ROLES = false` brings back the old roles and pace/spin flags.
Saved tournaments/auctions keep the old roles in play, but the screens still group them correctly.
