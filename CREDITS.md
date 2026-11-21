# Credits (patch 137)

Home > Credits, placed after Settings and before About. `ui/home/CreditsScreen.kt`.

Four credits, in this order:
1. Developed by Technical Accessibility World, with a button that opens their YouTube channel (youtube.com/@technicalaccessibilityworl).
2. English commentary, umpires, toss presenter, appeal sounds and some other sound effects: made with ElevenLabs. A paragraph says why ElevenLabs was used (generated voices, nothing copied from a real commentator), and a button opens elevenlabs.io.
3. Hindi commentary: original, recorded professionally by IAS Production Studios, all copyright reserved by them, with a button to their YouTube channel (youtube.com/@anoopsinghspecialtv) for anyone who wants to work with or contact them.
4. Special game supporters: the 17 beta testers, thanked. (No names are listed; add them in `CreditsScreen.kt` if wanted.)

Links are opened with `LocalUriHandler`. A link that cannot be opened (no browser) is announced in a live region. The links are the channels' plain addresses (the "?si=" share-tracking part was left off; it changes nothing).
