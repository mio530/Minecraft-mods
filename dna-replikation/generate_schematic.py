#!/usr/bin/env python3
"""Erzeugt dna_replikation_v6.litematic (Litematica, MC 1.21.x). Nur stdlib."""
import gzip, math, struct, time

R = 5
BB = {"A": "blue_concrete", "B": "cyan_concrete", "C": "red_concrete", "D": "orange_concrete"}
BASE = {"A": "lime_concrete", "T": "yellow_concrete", "G": "purple_concrete", "C": "pink_concrete"}
COMP = {"A": "T", "T": "A", "G": "C", "C": "G"}
SEQ = "ATGGCATCGTACCGATTGCAAGCTTAGCCGTAATCGGCTAACGTTAGCAT"
S = lambda k: SEQ[k % len(SEQ)]
blocks = {}

def put(x, y, z, n): blocks[(x, y, z)] = "minecraft:" + n
def bb(x, y, z, n):  # 2x2 Querschnitt
    for dy in (0, 1):
        for dz in (0, 1): put(x, y + dy, z + dz, n)

def rung(x, s1, s2, b1, lo=1, hi=9):
    """Sprosse von Strang1 zu Strang2 (je (y,z)), b1 = Base an Strang1. Index 1..9."""
    b2 = COMP[b1]
    pat = {}
    if b1 in "AT":
        for i in (1, 2, 3): pat[i] = BASE[b1]
        pat[4] = pat[6] = "white_stained_glass"
        for i in (7, 8, 9): pat[i] = BASE[b2]
    else:
        for i in (1, 2): pat[i] = BASE[b1]
        pat[3] = pat[5] = pat[7] = "white_stained_glass"
        for i in (8, 9): pat[i] = BASE[b2]
    for i, n in pat.items():
        if lo <= i <= hi:
            y = round(s1[0] + (s2[0] - s1[0]) * i / 10)
            z = round(s1[1] + (s2[1] - s1[1]) * i / 10)
            for dx in (0, 1):
                for dz in (0, 1): put(x + dx, y, z + dz, n)

def helix(x0, x1, yc, n1, n2, base_of_strand1, rungs=True):
    for x in range(x0, x1 + 1):
        th = 2 * math.pi * (x - x0) / 30 + math.pi / 2
        p1 = (round(yc + R * math.sin(th)), round(R * math.cos(th)))
        p2 = (round(yc - R * math.sin(th)), round(-R * math.cos(th)))
        bb(x, p1[0], p1[1], n1); bb(x, p2[0], p2[1], n2)
        if rungs and x % 3 == 0 and x < x1:
            rung(x, p1, p2, base_of_strand1(x // 3))

Y = lambda x: min(13, R + max(0, x - 60))

def write(out, name):
    global blocks
    # ---- hochkant drehen (Eltern-DNA unten, Tochterstraenge oben; reine Drehung) ----
    YM = max(y for _, y, _ in blocks)
    blocks = {(YM - y, x, z): n for (x, y, z), n in blocks.items()}

    # ---- Skalierung (jeder Block -> SCALE^3 Bloecke) ----
    SCALE = 2
    blocks = {(x * SCALE + a, y * SCALE + b, z * SCALE + c): n for (x, y, z), n in blocks.items()
              for a in range(SCALE) for b in range(SCALE) for c in range(SCALE)}

    # ---- Litematic schreiben ----
    xs, ys, zs = zip(*blocks)
    mn = (min(xs), min(ys), min(zs)); mx = (max(xs), max(ys), max(zs))
    sx, sy, sz = (mx[i] - mn[i] + 1 for i in range(3))
    pal = ["minecraft:air"] + sorted(set(blocks.values()))
    pid = {n: i for i, n in enumerate(pal)}
    bits = max(2, (len(pal) - 1).bit_length())
    big = 0
    for (x, y, z), n in blocks.items():
        idx = (y - mn[1]) * sz * sx + (z - mn[2]) * sx + (x - mn[0])
        big |= pid[n] << (idx * bits)
    nlongs = -(-sx * sy * sz * bits // 64)
    longs = [((big >> (64 * i)) & (2**64 - 1)) for i in range(nlongs)]
    longs = [v - 2**64 if v >= 2**63 else v for v in longs]

    def s(v): b = v.encode(); return struct.pack(">H", len(b)) + b
    def tag(t, name, payload): return bytes([t]) + s(name) + payload
    def comp(items): return b"".join(items) + b"\0"
    I = lambda n, v: tag(3, n, struct.pack(">i", v))
    L = lambda n, v: tag(4, n, struct.pack(">q", v))
    def vec(n, a, b, c): return tag(10, n, comp([I("x", a), I("y", b), I("z", c)]))
    def lst(n, t, payloads): return tag(9, n, bytes([t]) + struct.pack(">i", len(payloads)) + b"".join(payloads))
    now = int(time.time() * 1000)
    palette = [comp([tag(8, "Name", s(n))]) for n in pal]
    region = comp([
        vec("Position", 0, 0, 0), vec("Size", sx, sy, sz),
        lst("BlockStatePalette", 10, palette),
        tag(12, "BlockStates", struct.pack(">i", len(longs)) + b"".join(struct.pack(">q", v) for v in longs)),
        lst("TileEntities", 10, []), lst("Entities", 10, []),
        lst("PendingBlockTicks", 10, []), lst("PendingFluidTicks", 10, []),
    ])
    meta = comp([tag(8, "Name", s(name)), tag(8, "Author", s("mio")),
                 tag(8, "Description", s("DNA-Replikation: Helix, Gabel, Enzyme, Tochterstraenge")),
                 L("TimeCreated", now), L("TimeModified", now), vec("EnclosingSize", sx, sy, sz),
                 I("RegionCount", 1), I("TotalBlocks", len(blocks)), I("TotalVolume", sx * sy * sz)])
    root = tag(10, "", comp([I("MinecraftDataVersion", 3955), I("Version", 6),
                             tag(10, "Metadata", meta), tag(10, "Regions", comp([tag(10, "DNA", region)]))]))
    with gzip.open(out, "wb") as f: f.write(root)
    print(out, "Groesse", sx, sy, sz, "Bloecke", len(blocks), "Palette", len(pal), "bits", bits)

def build(parent=True, ring=False, fork=False, primase=False, primers=(), leading=False,
          frags=(), poly=False, ligase=False, daughters=False, parent_only_daughters=False):
    blocks.clear()
    if parent and not parent_only_daughters:
        helix(30, 60, 0, BB["A"], BB["B"], S)
    if ring:
        for x in (50, 51):
            for y in range(-11, 12):
                for z in range(-11, 12):
                    if 7.5 <= math.hypot(y + .5, z + .5) <= 10: put(x, y, z, "crying_obsidian")
    if fork:
        for x in range(61, 96):
            bb(x, Y(x), 0, BB["A"]); bb(x, -Y(x) - 1, 0, BB["B"])
        for x in range(61, 64):
            for y in (-1, 0, 1):
                for z in (-1, 0, 1): put(x, y, z, "gold_block")
        if leading:
            for x in range(72, 96): bb(x, 3, 0, BB["C"])
        for lo, hi in frags:
            for x in range(lo, hi + 1): bb(x, -4, 0, BB["D"])
        if ligase:
            for x in (89, 90):
                for dy in (0, 1):
                    for dz in (0, 1): put(x, -4 + dy, dz, "sea_lantern")
        for x in range(63, 96, 3):
            k = x // 3
            up = leading and x >= 72
            lo_new = any(lo <= x <= hi for lo, hi in frags) or (ligase and x == 90)
            if up: rung(x, (Y(x), 0), (3, 0), S(k))
            else: rung(x, (Y(x), 0), (-Y(x) - 1, 0), S(k), 1, 3)
            if lo_new: rung(x, (-3, 0), (-Y(x) - 1, 0), S(k))
            else: rung(x, (Y(x), 0), (-Y(x) - 1, 0), S(k), 7, 9)
        if primase:
            for x in (64, 65):
                for y in (-5, -4, -3):
                    for z in (2, 3): put(x, y, z, "diamond_block")
        for lo in primers:
            for x in (lo, lo + 1): bb(x, -4 if lo != 72 or not leading else -4, 0, "white_concrete")
        if poly:
            for x in range(70, 75):
                for y in range(5, 12):
                    for z in (2, 3, 4): put(x, y, z, "emerald_block")
            for x in range(73, 78):
                for y in range(-12, -5):
                    for z in (2, 3, 4): put(x, y, z, "emerald_block")
    if daughters:
        helix(96, 126, 8, BB["A"], BB["C"], S)
        helix(96, 126, -8, BB["D"], BB["B"], S)

import os, sys
STAT = [
 ("station_1_eltern_dna", "1 Eltern-DNA", dict()),
 ("station_2_topoisomerase", "2 Topoisomerase", dict(ring=True)),
 ("station_3_helikase", "3 Helikase oeffnet", dict(ring=True, fork=True)),
 ("station_4_primase_primer", "4 Primase und Primer", dict(ring=True, fork=True, primase=True, primers=(72, 82))),
 ("station_5_polymerase", "5 Polymerase", dict(ring=True, fork=True, primase=True, leading=True, frags=((72, 78),), primers=(72,), poly=True)),
 ("station_6_ligase", "6 Ligase", dict(ring=True, fork=True, primase=True, leading=True, frags=((72, 78), (82, 88), (91, 95)), ligase=True, poly=True, primers=(72, 82, 91))),
 ("station_7_ergebnis", "7 Ergebnis", dict(parent_only_daughters=True, daughters=True)),
]
if __name__ == "__main__":
    os.makedirs("stationen", exist_ok=True)
    for fn, nm, cfg in STAT:
        build(**cfg); write(f"stationen/{fn}.litematic", nm)
    build(ring=True, fork=True, primase=True, leading=True, frags=((72, 78), (82, 88), (91, 95)), ligase=True,
          poly=True, primers=(72, 82, 91), daughters=True)
    write("dna_replikation_v6.litematic", "DNA-Replikation")
