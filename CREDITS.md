# Credits (patches 137 and 138)

Home > Credits, after Settings and before About. `ui/home/CreditsScreen.kt`. Five credits, in this order:
1. Developed by Technical Accessibility World, with a button to their YouTube channel (youtube.com/@technicalaccessibilityworl).
2. English commentary, umpires, toss presenter, appeal sounds and some other sound effects: made with ElevenLabs. A paragraph says why (generated voices, nothing copied from a real commentator), and a button opens elevenlabs.io.
3. More sound effects (patch 138): Delta Productions helped with some sound effects, such as the bowled-out sound. Buttons open their game on the Google Play Store (com.fantacycricket.game) and their website (deltaproduction.me).
4. Hindi commentary: original, recorded professionally by IAS Production Studios, all copyright reserved by them, with a button to their YouTube channel (youtube.com/@anoopsinghspecialtv) for anyone who wants to work with or contact them.
5. Special game supporters: the 17 beta testers, thanked. (No names are listed; add them in `CreditsScreen.kt` if wanted.)

Links are opened with `LocalUriHandler`; a link that cannot be opened (no browser) is announced in a live region. The YouTube links are the channels' plain addresses (the "?si=" share-tracking part was left off).
