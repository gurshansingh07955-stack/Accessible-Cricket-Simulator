#!/usr/bin/env python3
"""Prints every umpire clip id (one per line), read from UmpireLines.kt, so the audio step downloads exactly the
lines the app can ask for. With --json it prints [{"id","text","voice"}] for the generation script."""
import json, re, sys
SRC = "app/src/main/java/com/cricketsim/audio/UmpireLines.kt"
text = open(SRC, encoding="utf-8").read()
pairs = re.findall(r'"((?:um|tu)_[a-z0-9_]+)"\s+to\s+"((?:[^"\\]|\\.)*)"', text)
if "--json" in sys.argv:
    print(json.dumps([{"id": i, "text": t.replace('\\"', '"'), "voice": "field" if i.startswith("um_") else "third"} for i, t in pairs]))
else:
    for i, _ in pairs:
        print(i)
