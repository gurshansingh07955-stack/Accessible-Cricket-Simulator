# Rain in domestic tournaments, the bowled sound in LBW reviews, louder shot sounds

**Rain.** Every domestic tournament that is limited-overs (Syed Mushtaq Ali, Vijay Hazare, One-Day Cup, Vitality Blast,
Australian One-Day Cup, Pro50, Pro20, Ford Trophy, Super Smash, Super50) has rain again, as in the game's T20 and
ODI matches (one interruption, a revised overs limit and a DLS target). The first-class and four-day tournaments
(Ranji, Duleep, Irani, County Championship, Sheffield Shield, 4-Day Series, Plunket Shield, WI Championship,
Quaid-e-Azam, National Cricket League) still have no rain: the Test engine has no rain model, so a rain break there
needs its own design (lost overs from a day).

**LBW review.** At the step "Wickets: hitting." the third umpire's line is followed by your `bowled` sound
(`SoundEngine.playBowledHit`). "Umpire's call" and "missing" do not play it.

**Louder sounds.** `runs_1_2_3`, `four_runs`, `six_runs` and `bowled` are played at `HIGHLIGHT_GAIN` (1.0, the loudest a
sound can be played) and boosted by `HIGHLIGHT_BOOST_DB` (6 dB, with a limiter) when "Fetch audio into the app" packs them.
Raise or lower the boost in `tools/fetch_audio.sh`.
