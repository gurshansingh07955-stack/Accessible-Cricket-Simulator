#!/usr/bin/env python3
"""Prints every clip id of the toss presentation (patch 132), one per line, for tools/fetch_audio.sh.

The clips are made by the web app's toss_tts_generate endpoint (the presenter, the user's captain in the career press conference's player voice, the opposition
captain in another voice) and live at /_cdn/toss/<id>.mp3. The ids are the ones ui/match/TossShow.kt builds; the referee numbers follow logic/MatchReferees.kt (14 names).
"""
ids = []
ids += [f"toss_w_{i}" for i in range(1, 5)]                      # the welcome
ids += [f"toss_ref_{i}" for i in range(1, 15)]                   # "Today's match referee is ..." (one per referee)
ids += [f"toss_inv_call_{i}" for i in range(1, 4)]               # the invitation to call heads or tails
ids += [f"toss_inv_flip_{i}" for i in range(1, 4)]               # the invitation to the user to flip the coin
ids += [f"toss_res_h_{i}" for i in range(1, 3)]                  # "It's heads"
ids += [f"toss_res_t_{i}" for i in range(1, 3)]                  # "It's tails"
for group in ("qw_user", "qw_opp", "qs_user", "qs_opp"):         # the question to the winner and to the other captain
    ids += [f"toss_{group}_{i}" for i in range(1, 4)]
ids += [f"toss_end_{i}" for i in range(1, 3)]                    # the closing
for who in ("user", "opp"):                                      # the captains: the call, the decision, the answer after losing
    ids += [f"toss_{who}_heads", f"toss_{who}_tails"]
    ids += [f"toss_{who}_bat_{i}" for i in (1, 2)] + [f"toss_{who}_bowl_{i}" for i in (1, 2)]
    ids += [f"toss_{who}_lose_{i}" for i in (1, 2, 3)]
print("\n".join(ids))
