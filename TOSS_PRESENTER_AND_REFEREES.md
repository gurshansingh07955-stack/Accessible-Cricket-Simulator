# Toss presentation and match referees (patch 132)

## Match referees, everywhere
Every match has a **match referee** (`MatchState.referee`, `logic/MatchReferees.kt`, 14 real referees' names, a name and nothing else): **Match info** says "Match referee: X." in the Teams section, in a normal match, a tournament, every level of career mode. A career match always gets the same referee for the same fixture (`MatchReferees.forMatch`, so the toss and Match info agree); any other match picks one at random when it starts; a saved match without one gets one when it is loaded.

## The toss presentation (career mode, international and franchise leagues only)
In a career match of an **international series or tournament** or of a **franchise league** (nowhere else: not state, club, school, college, and not the normal game), the toss (the screen where you call and choose, or, when you are not the captain, the screen that tells you what the captains did) has a **presenter** and both captains speaking, recorded (`ui/match/TossShow.kt`):
1. the presenter welcomes everyone, **names the match referee** ("Today's match referee is ...") and invites the captain to call heads or tails (or you to flip the coin);
2. the caller says "Heads!" or "Tails!", the presenter says what the coin showed and **asks the captain who won the toss** for his decision and thinking;
3. he **answers** (bat or bowl); the presenter **asks the other captain** how he sees it; he answers; the presenter closes.
**Voices:** the presenter is an excited, authoritative commentator; **your captain speaks in the same voice as the player in the career press conference** (Charlie); the **opposition captain in another voice** (Eric). The lines are generic (no team names): after the voices the phone still says who won and what they chose. Continue / Start the match cuts the voices off. With the clips missing, sound effects off, or any other match, the toss goes on exactly as before (and the old toss commentary plays instead of the presentation).

## The presenter's voice
The web app's `toss_tts_generate` endpoint tries the Indian-accented sports commentators "Shaurya" and "Viraj" from the ElevenLabs voice library first, then the premade "Harry - Fierce Warrior". **Library voices need a paid ElevenLabs plan** (a free plan refuses them over the API), so on a free plan the presenter is Harry. Upgrading the plan and running the generation again (same clip ids) switches the whole presentation to the commentator voice. The voices were chosen by description and not heard by the assistant.

## Clips
60 clips (`tools/toss_clip_ids.py`): the presenter's welcome (4), one referee line per referee (14), invitations (3 + 3), coin results (2 + 2), questions (12), closings (2); each captain's call (2), decisions (4) and answers after losing (3). Fetched by "Fetch audio into the app" from `/_cdn/toss/<id>.mp3`.

## Not done
Names of the captains and teams in the spoken lines, the presenter in the toss of the game's own tournaments or the normal match, the referee in the scorecard text.
