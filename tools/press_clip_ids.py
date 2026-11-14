#!/usr/bin/env python3
"""Prints every press conference clip id (one per line), worked out from CareerPress.kt so the audio step downloads exactly the clips the app can ask
for: every question once for each of the four reporters (<question id>_r0 .. _r3) and every answer once in the player's voice (<question id>_a .. _e).
With --json it prints [{"id","text","voice"}] for the generation script (voice is r0..r3 or player)."""
import json, re, sys
SRC = "app/src/main/java/com/cricketsim/logic/CareerPress.kt"
text = open(SRC, encoding="utf-8").read()

def unescape(s):
    return s.replace('\\"', '"').replace('\\\\', '\\')

clips = []
for seg in text.split('q("pq_')[1:]:
    m = re.match(r'([a-z0-9_]+)", "([a-z]+)", "((?:[^"\\]|\\.)*)"', seg)
    if not m:
        continue
    qid = "pq_" + m.group(1)
    qtext = unescape(m.group(3))
    for v in range(4):
        clips.append((f"{qid}_r{v}", qtext, f"r{v}"))
    for i, a in enumerate(re.finditer(r'\ba\("[A-Za-z ]+", -?\d+, "((?:[^"\\]|\\.)*)"\)', seg)):
        clips.append((f"{qid}_{'abcde'[i]}", unescape(a.group(1)), "player"))

if "--json" in sys.argv:
    print(json.dumps([{"id": i, "text": t, "voice": v} for i, t, v in clips]))
else:
    for i, _, _ in clips:
        print(i)
