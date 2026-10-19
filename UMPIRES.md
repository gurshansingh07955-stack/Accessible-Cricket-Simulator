# Appeals, the umpires and the third umpire

## The LBW appeal
- **You are bowling** and the ball hits the batter's pad in front of the stumps: a screen says so and asks you to appeal. You have six
  seconds. Appeal: the sound of many fielders appealing plays (six different recordings, one at random), then the umpire gives it
  out and the batter may review as before. Do not appeal: the umpire says "Not out. Play on." and the batter survives that ball.
- **You are batting** and the fielders hit you on the pad: the computer side appeals by itself (the same sound), the umpire gives
  you out, and you may review as before.

## The umpires' voices (recorded, ElevenLabs)
Two voices, both different from the commentators and from the auctioneer:
- the ON-FIELD umpire (um_...; "George", a warm mature British voice): Out! / Not out / No ball! / Wide ball! / Free hit! and the
  referral to the third umpire. Wide and no-ball (with the free hit) are called at the ball; Out and Not out after an LBW appeal.
- the THIRD umpire (tu_...; "Bill", a calm level voice): every line of a review, as a TV third umpire speaks it, and then the
  verdict. A review now takes as long as he takes: each check is spoken and the next waits until he has finished, so the voice and
  the screen stay in step. The verdict is spoken by him ("Not out. The decision is overturned.", "Umpire's call. The on-field decision
  stands. Out.", "Out. The on-field decision stands."); the explanation and the review count follow in the phone's voice.
- If a clip is missing, or sound effects are off, the phone's own speech says the line instead, so nothing is ever silent.

The lines are in audio/UmpireLines.kt (the single source: tools/umpire_clip_ids.py reads it to know what to download). The clips
are generated on the web app by the umpire_tts_generate endpoint (voices and their settings are in that endpoint's source) and
downloaded by "Fetch audio into the app".
