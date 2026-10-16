#!/usr/bin/env python3
"""Prints, one per line, the id of every recorded auctioneer clip the IPL auction can ask for.

It is worked out from the game's own source, so it cannot drift from the code:
  - one clip per player in the IPL pool (au_n_<name with only letters, lower case>), exactly as
    IplData.buildPool() names them (a player already in the game uses the roster's spelling);
  - one per price the bidding can reach (au_p_<lakhs>), from nextBid() in IplAuction.kt;
  - the ten teams (full and short name), the five roles, the countries and the fixed phrases.
tools/fetch_audio.sh downloads exactly these from the web app and packs them with the other audio.
"""
import re

BASE = "app/src/main/java/com/cricketsim/logic/"


def norm(s):
    return re.sub(r"[^a-z]", "", s.lower())


data = open(BASE + "IplData.kt", encoding="utf-8").read()
roster_src = open(BASE + "CricketData.kt", encoding="utf-8").read()

roster = {}
for m in re.finditer(r'createPlayer\((\w+),\s*"([^"]+)"', roster_src):
    roster[norm(m.group(2))] = m.group(2)

start = data.index('POOL_DATA = """') + len('POOL_DATA = """')
pool_data = data[start:data.index('""".trimIndent()')]
extras = re.search(r'ROSTER_EXTRAS =\s*"([^"]+)"', data).group(1)

seen = set()
names = []


def add(name):
    key = norm(name)
    if key in seen:
        return
    seen.add(key)
    names.append(name)


for line in pool_data.splitlines():
    f = line.strip().split("|")
    if len(f) < 7:
        continue
    if f[0] == "e":
        add(roster.get(norm(f[1]), f[1]))
    elif len(f) >= 10:
        add(f[1])
for n in extras.split("|"):
    hit = roster.get(norm(n))
    if hit:
        add(hit)


def next_bid(c):
    if c < 100:
        return c + 5
    if c < 200:
        return c + 10
    if c < 500:
        return c + 20
    return c + 25


prices = set()
for base in (30, 50, 75, 100, 125, 150, 200):
    p = base
    while p <= 2800:
        prices.add(p)
        p = next_bid(p)

ids = [
    "au_x_nextup", "au_x_bid", "au_x_baseprice", "au_x_goingonce", "au_x_goingtwice", "au_x_sold",
    "au_x_to", "au_x_for", "au_x_nobids", "au_x_goesunsold",
    "au_r_batter", "au_r_allrounder", "au_r_wicketkeeper", "au_r_fast", "au_r_spin",
]
ids += ["au_c_" + c for c in "ind aus eng rsa nz sl wi ban afg zim ire ned sco nep uae oma usa".split()]
for t in "csk dc gt kkr lsg mi pbks rr rcb srh".split():
    ids += ["au_t_%s_full" % t, "au_t_%s_short" % t]
ids += ["au_p_%d" % p for p in sorted(prices)]
ids += ["au_n_" + norm(n) for n in names]
print("\n".join(ids))
