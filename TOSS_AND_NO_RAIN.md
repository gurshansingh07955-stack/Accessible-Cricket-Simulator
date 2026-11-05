# No rain in domestic tournaments, and who calls the coin

**Rain.** Every domestic tournament (Indian, English, Australian, South African, New Zealand, West Indies, Pakistani,
Bangladeshi) plays without rain: `TournamentRules.rulesFor` forces `rainEnabled = false` on each. Match info then hides
the chance of rain and never shows "light rain". Custom tournaments keep their own rain switch.

**The toss.** `TossScreen` has two modes: the user calls heads or tails (the other captain flips), or the user flips
(a "Flip the coin" button; the other captain calls it). After the coin lands the screen says what was called and what
the coin showed. Who does what: `logic/TossRoles.kt`. At a ground that is one side's home, the HOME side calls and the
visitors flip (`TossRoles.HOME_TEAM_CALLS`; set it to false for the real-cricket custom, where the home captain
flips). At a neutral ground (neither or both sides at home, any custom tournament) it is settled 50-50 for each toss.
A tournament side is at home on its own home ground (`CplData.homeStadiumId`) or when the ground lists it.
