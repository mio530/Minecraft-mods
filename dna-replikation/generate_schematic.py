#!/usr/bin/env python3
"""DNA-Replikation als Litematica-Schematics (MC Java 1.21.x). Nur stdlib (+ Pillow fuer Vorschau).

Aufruf:  python3 generate_schematic.py          -> stationen/*.litematic + dna_replikation_gesamt.litematic
         python3 generate_schematic.py preview  -> zusaetzlich vorschau/*.png
"""
import gzip, math, os, struct, sys, time

R, P = 6, 30            # Helix-Radius, Blöcke pro Windung (10 Basenpaare/Windung wie echte B-DNA ~10,5)
SCALE = 2               # jeder Block -> 2x2x2

# Rückgrat: Zucker (Beton) + Phosphat (Terrakotta) – Farbe = alter/neuer Strang
COL = {"A": ("blue_concrete", "blue_terracotta"),      # alter Strang 1
       "B": ("cyan_concrete", "cyan_terracotta"),      # alter Strang 2
       "C": ("red_concrete", "red_terracotta"),        # neuer Strang (Leitstrang)
       "D": ("orange_concrete", "orange_terracotta"),  # neuer Strang (Folgestrang)
       "P": ("white_concrete", "white_terracotta")}    # RNA-Primer
BASE = {"A": "lime_concrete", "T": "yellow_concrete", "G": "purple_concrete",
        "C": "pink_concrete", "U": "brown_concrete"}
SIZE = {"A": 4, "G": 3, "T": 3, "U": 3, "C": 2}       # Purine größer als Pyrimidine
COMP = {"A": "T", "T": "A", "G": "C", "C": "G"}
SEQ = "ATGCGTACCTAGGCATTACGGATCCGTAAGCTTGCAATCGGTACCA"
S = lambda k: SEQ[k % len(SEQ)]
END5, END3 = "glowstone", "verdant_froglight"         # 5'-Ende / 3'-Ende

B = {}
def put(x, y, z, n): B[(x, y, z)] = "minecraft:" + n
fl = lambda v: math.floor(v + 1e-9)

def bb(x, c, strand):
    """Rückgrat-Stück 2x2 um Mittelpunkt c=(y,z). x%3: 0,1 = Zucker, 2 = Phosphat."""
    n = COL[strand][1] if x % 3 == 2 else COL[strand][0]
    y0, z0 = fl(c[0] - .5), fl(c[1] - .5)
    for dy in (0, 1):
        for dz in (0, 1): put(x, y0 + dy, z0 + dz, n)

def cap(x, c, kind):
    y0, z0 = fl(c[0] - .5), fl(c[1] - .5)
    for dy in (0, 1):
        for dz in (0, 1): put(x, y0 + dy, z0 + dz, kind)

def rung(x, c1, c2, b1, b2, part="full"):
    """Basenpaar zwischen Rückgrat-Mittelpunkten c1, c2 (Abstand 12). 10 Zellen dazwischen."""
    s1, s2 = SIZE[b1], SIZE[b2]
    pat = [("1", BASE[b1])] * s1
    nb = 10 - s1 - s2
    pat += [("h", "white_stained_glass" if i % 2 == 0 else None) for i in range(nb)]
    pat += [("2", BASE[b2])] * s2
    dy, dz = c2[0] - c1[0], c2[1] - c1[1]
    for i, (who, n) in enumerate(pat):
        if n is None or (part != "full" and who not in (part,)): continue
        t = (1.5 + i) / 12
        py, pz = c1[0] + dy * t, c1[1] + dz * t
        if abs(dy) >= abs(dz): cells = [(fl(py), fl(pz - .5) + k) for k in (0, 1)]
        else: cells = [(fl(py - .5) + k, fl(pz)) for k in (0, 1)]
        for yy, zz in cells:
            for xx in (x, x + 1): put(xx, yy, zz, n)

def pairbase(b, primer):  # Base gegenüber; in RNA-Primer U statt T
    c = COMP[b]
    return "U" if primer and c == "T" else c

def helix(x0, x1, yc, s1, s2, primer2=()):
    """Doppelhelix x0..x1; Strang1 beginnt oben (y=yc+R). Strang2-Abschnitte in primer2 sind RNA-Primer."""
    ends = {}
    for x in range(x0, x1 + 1):
        th = 2 * math.pi * (x - x0) / P + math.pi / 2
        c1 = (yc + R * math.sin(th), 1 + R * math.cos(th))
        c2 = (yc - R * math.sin(th), 1 - R * math.cos(th))
        p2 = any(lo <= x <= hi for lo, hi in primer2)
        bb(x, c1, s1); bb(x, c2, "P" if p2 else s2)
        ends[x] = (c1, c2)
        if x % 3 == 0 and x < x1:
            b1 = S(x // 3)
            rung(x, c1, c2, b1, pairbase(b1, p2))
    return ends

# ---------------- Geometrie (vor dem Hochkant-Drehen: x = Längsachse) ----------------
# Eltern-Helix x 0..30 | Gabel/offene Zone x 30..71 | Tochter-Helices x 72..102
# Spiegelebene y = 0.5 (oben = Strang A/C, unten = B/D)
m = lambda y: 1 - y
Ya = lambda x: min(17, 6.5 + max(0, x - 30))    # Mittelpunkt alter Strang A (oben)

def fork(C=(), D=(), helicase=True):
    """Offene Zone x 30..71. C/D: Liste (lo, hi, 'dna'|'primer') der neuen Stränge."""
    def seg(segs, x):
        for lo, hi, kind in segs:
            if lo <= x <= hi: return kind
    for x in range(30, 72):
        a = (Ya(x), 1); b = (m(Ya(x)), 1)
        bb(x, a, "A"); bb(x, b, "B")
        kc, kd = seg(C, x), seg(D, x)
        cC, cD = (Ya(x) - 12, 1), (m(Ya(x) - 12), 1)
        if kc: bb(x, cC, "P" if kc == "primer" else "C")
        if kd: bb(x, cD, "P" if kd == "primer" else "D")
        if x % 3 == 0 and 33 <= x <= 69:
            b = S(x // 3)                       # Base auf A; B trägt COMP
            kc1, kd1 = seg(C, x), seg(D, x)
            if kc1: rung(x, a, cC, b, pairbase(b, kc1 == "primer"))
            else: rung(x, a, cC, b, COMP[b], "1")
            db = b if not (kd1 == "primer" and b == "T") else "U"
            if kd1: rung(x, cD, (m(Ya(x)), 1), db, COMP[b])
            else: rung(x, cD, (m(Ya(x)), 1), b, COMP[b], "2")
    if helicase:
        for x in range(30, 33):
            for y in (-1, 0, 1):
                for z in (-1, 0, 1, 2): put(x, y, z, "gold_block")

def blob(xs, ys, n, zs=(3, 4, 5)):
    for x in xs:
        for y in ys:
            for z in zs: put(x, y, z, n)

def topo():
    for x in (13, 14):
        for y in range(-12, 14):
            for z in range(-11, 14):
                if 8.5 <= math.hypot(y + .5 - .5, z + .5 - 1) <= 10.5: put(x, y, z, "crying_obsidian")

primase = lambda: blob(range(38, 41), range(-10, -7), "diamond_block", (3, 4))
pol_lead = lambda: blob(range(42, 47), range(6, 16), "emerald_block")
pol_lag = lambda: blob(range(51, 56), range(-14, -4), "emerald_block")
ligase = lambda: blob(range(64, 68), range(-14, -4), "sea_lantern")

def parent(ends_low=True):
    e = helix(0, 30, .5, "A", "B")
    if ends_low:
        cap(-1, e[0][0], END5); cap(-1, e[0][1], END3)   # A: 5' unten, B: 3' unten (antiparallel)
    return e

def old_ends_top(x):  # Enden der alten Stränge oben (A 3', B 5')
    cap(x, (Ya(x), 1), END3); cap(x, (m(Ya(x)), 1), END5)

def daughters(primer=False):
    up = helix(72, 102, 11, "A", "C", ((96, 101),) if primer else ())
    lo = helix(72, 102, m(11), "D", "B")
    return up, lo

LAG_FULL = ((45, 50, "primer"), (51, 53, "dna"), (57, 65, "dna"), (66, 71, "dna"))

def st0():  # Nukleotide
    for n, (b, s) in enumerate((("A", "A"), ("T", "A"), ("G", "A"), ("C", "A"), ("U", "P"))):
        x0 = n * 6
        for x in range(x0, x0 + 3): bb(x, (1, 1), s)
        for i in range(SIZE[b]):
            for xx in (x0, x0 + 1):
                for z in (0, 1): put(xx, -1 - i, z, BASE[b])

def st1(): e = parent(); cap(31, e[30][0], END3); cap(31, e[30][1], END5)
def st2(): st1(); topo()
def st3(): parent(); topo(); fork(); old_ends_top(72)
def st4():
    parent(); topo(); fork(C=((51, 56, "primer"),), D=((45, 50, "primer"),)); primase(); old_ends_top(72)
def st5():
    parent(); topo(); fork(C=((45, 50, "dna"), (51, 56, "primer")), D=((45, 50, "primer"), (51, 53, "dna")))
    primase(); pol_lead(); pol_lag(); old_ends_top(72)
    cap(57, (Ya(57) - 12, 1), END5); cap(44, (Ya(44) - 12, 1), END3)          # Leitstrang 5'->3' zur Gabel
    cap(44, (m(Ya(44) - 12), 1), END5); cap(54, (m(Ya(54) - 12), 1), END3)    # Okazaki-Fragment 5'->3' weg von der Gabel
def st6():
    parent(); topo(); fork(C=((45, 71, "dna"),), D=LAG_FULL); primase(); pol_lead(); pol_lag(); ligase()
    old_ends_top(72)
def st7():
    up, lo = daughters(primer=True)
    for (c1, c2), (d1, d2), x in ((up[72], lo[72], 71), (up[102], lo[102], 103)):
        top = x == 103
        cap(x, c1, END3 if top else END5); cap(x, c2, END5 if top else END3)   # A / C
        cap(x, d1, END3 if top else END5); cap(x, d2, END5 if top else END3)   # D / B
def gesamt():
    parent(); topo(); fork(C=((45, 71, "dna"),), D=LAG_FULL); primase(); pol_lead(); pol_lag(); ligase()
    up, lo = daughters(primer=True)
    cap(103, up[102][0], END3); cap(103, up[102][1], END5); cap(103, lo[102][0], END3); cap(103, lo[102][1], END5)

STATIONEN = [
    ("00_nukleotide", "Nukleotide", st0),
    ("01_eltern_dna", "Eltern-DNA", st1),
    ("02_topoisomerase", "Topoisomerase", st2),
    ("03_helikase_gabel", "Helikase + Replikationsgabel", st3),
    ("04_primase_primer", "Primase + RNA-Primer", st4),
    ("05_polymerase", "DNA-Polymerase", st5),
    ("06_okazaki_ligase", "Okazaki-Fragmente + Ligase", st6),
    ("07_ergebnis", "Ergebnis: semikonservativ", st7),
]

# ---------------- Ausgabe ----------------
def final_blocks():
    ym = max(y for _, y, _ in B)
    rot = {(ym - y, x, z): n for (x, y, z), n in B.items()}   # hochkant: Eltern unten, Töchter oben (Drehung)
    return {(x * SCALE + a, y * SCALE + b, z * SCALE + c): n for (x, y, z), n in rot.items()
            for a in range(SCALE) for b in range(SCALE) for c in range(SCALE)}

def write(path, name, blocks):
    xs, ys, zs = zip(*blocks)
    mn = (min(xs), min(ys), min(zs))
    sx, sy, sz = (max(v) - mn[i] + 1 for i, v in enumerate((xs, ys, zs)))
    pal = ["minecraft:air"] + sorted(set(blocks.values()))
    pid = {n: i for i, n in enumerate(pal)}
    bits = max(2, (len(pal) - 1).bit_length())
    big = 0
    for (x, y, z), n in blocks.items():
        big |= pid[n] << ((((y - mn[1]) * sz + (z - mn[2])) * sx + (x - mn[0])) * bits)
    nl = -(-sx * sy * sz * bits // 64)
    longs = [(big >> (64 * i)) & (2**64 - 1) for i in range(nl)]
    longs = [v - 2**64 if v >= 2**63 else v for v in longs]
    s = lambda v: struct.pack(">H", len(v.encode())) + v.encode()
    tag = lambda t, nm, p: bytes([t]) + s(nm) + p
    comp = lambda items: b"".join(items) + b"\0"
    I = lambda nm, v: tag(3, nm, struct.pack(">i", v))
    L = lambda nm, v: tag(4, nm, struct.pack(">q", v))
    vec = lambda nm, a, b, c: tag(10, nm, comp([I("x", a), I("y", b), I("z", c)]))
    lst = lambda nm, t, ps: tag(9, nm, bytes([t]) + struct.pack(">i", len(ps)) + b"".join(ps))
    now = int(time.time() * 1000)
    region = comp([vec("Position", 0, 0, 0), vec("Size", sx, sy, sz),
                   lst("BlockStatePalette", 10, [comp([tag(8, "Name", s(n))]) for n in pal]),
                   tag(12, "BlockStates", struct.pack(">i", nl) + b"".join(struct.pack(">q", v) for v in longs)),
                   lst("TileEntities", 10, []), lst("Entities", 10, []),
                   lst("PendingBlockTicks", 10, []), lst("PendingFluidTicks", 10, [])])
    meta = comp([tag(8, "Name", s(name)), tag(8, "Author", s("mio")),
                 tag(8, "Description", s("DNA-Replikation – " + name)),
                 L("TimeCreated", now), L("TimeModified", now), vec("EnclosingSize", sx, sy, sz),
                 I("RegionCount", 1), I("TotalBlocks", len(blocks)), I("TotalVolume", sx * sy * sz)])
    root = tag(10, "", comp([I("MinecraftDataVersion", 3955), I("Version", 6),
                             tag(10, "Metadata", meta), tag(10, "Regions", comp([tag(10, "DNA", region)]))]))
    with gzip.open(path, "wb") as f: f.write(root)
    print(f"{path}: {sx}x{sy}x{sz}, {len(blocks)} Blöcke")

RGB = {"blue_concrete": (45, 47, 143), "blue_terracotta": (74, 60, 91), "cyan_concrete": (21, 119, 136),
       "cyan_terracotta": (87, 91, 91), "red_concrete": (142, 33, 33), "red_terracotta": (143, 61, 47),
       "orange_concrete": (224, 97, 1), "orange_terracotta": (162, 84, 38), "white_concrete": (207, 213, 214),
       "white_terracotta": (210, 178, 161), "lime_concrete": (94, 169, 24), "yellow_concrete": (241, 175, 21),
       "purple_concrete": (100, 32, 156), "pink_concrete": (214, 101, 143), "brown_concrete": (96, 60, 32),
       "white_stained_glass": (235, 235, 235), "glowstone": (255, 220, 120), "verdant_froglight": (150, 220, 140),
       "gold_block": (246, 208, 61), "crying_obsidian": (60, 10, 90), "diamond_block": (98, 237, 228),
       "emerald_block": (42, 203, 87), "sea_lantern": (172, 199, 190)}

def preview(path, blocks):
    from PIL import Image
    xs, ys, zs = zip(*blocks)
    x0, y0, x1, y1 = min(xs), min(ys), max(xs), max(ys)
    front = {}
    for (x, y, z), n in blocks.items():
        if (x, y) not in front or z > front[(x, y)][0]: front[(x, y)] = (z, n)
    px = 3
    im = Image.new("RGB", ((x1 - x0 + 1) * px, (y1 - y0 + 1) * px), (120, 165, 230))
    for (x, y), (z, n) in front.items():
        c = RGB.get(n.split(":")[1], (255, 0, 255))
        for i in range(px):
            for j in range(px): im.putpixel(((x - x0) * px + i, (y1 - y) * px + j), c)
    im.save(path)

# ---------------- Datapack-Export (/function; baut alle Stationen mit Abstand) ----------------
# Weltkoordinaten: erste Station bei ORIGIN, weitere entlang +X mit GAP Abstand (Boden bei y=BASE_Y).
BASE_Y, GAP, ORIGIN_X, ORIGIN_Z = -60, 10, 0, 0

def _norm(blocks):
    xs, ys, zs = zip(*blocks)
    mn = (min(xs), min(ys), min(zs))
    nb = {(x - mn[0], y - mn[1], z - mn[2]): n for (x, y, z), n in blocks.items()}
    return nb, (max(xs) - mn[0] + 1, max(ys) - mn[1] + 1, max(zs) - mn[2] + 1)

def _fills(blocks, ox, oy, oz):
    """Blöcke zu /fill-Reihen entlang X zusammenfassen (gleicher Block, lückenlos)."""
    rows = {}
    for (x, y, z), n in blocks.items(): rows.setdefault((y, z), []).append((x, n))
    cmds = []
    for (y, z), lst in sorted(rows.items()):
        lst.sort()
        i = 0
        while i < len(lst):
            j = i
            while j + 1 < len(lst) and lst[j + 1][0] == lst[j][0] + 1 and lst[j + 1][1] == lst[i][1]: j += 1
            n = lst[i][1]; wy, wz = oy + y, oz + z
            if lst[i][0] == lst[j][0]:
                cmds.append(f"setblock {ox+lst[i][0]} {wy} {wz} {n}")
            else:
                cmds.append(f"fill {ox+lst[i][0]} {wy} {wz} {ox+lst[j][0]} {wy} {wz} {n}")
            i = j + 1
    return cmds

def datapack():
    import shutil, zipfile, json
    root = "dna_datapack"
    fdir = os.path.join(root, "data", "dna", "function")
    if os.path.isdir(root): shutil.rmtree(root)
    os.makedirs(fdir)
    with open(os.path.join(root, "pack.mcmeta"), "w", encoding="utf-8") as f:
        json.dump({"pack": {"description": "DNA-Replikation: alle Stationen bauen",
                            "pack_format": 81, "min_format": 48, "max_format": 999}}, f, indent=2)
    build, clear, ox, total = [], [], ORIGIN_X, 0
    for fn, name, fdef in STATIONEN:
        B.clear(); fdef(); nb, (sx, sy, sz) = _norm(final_blocks())
        cmds = _fills(nb, ox, BASE_Y, ORIGIN_Z)
        total += len(cmds)
        with open(os.path.join(fdir, fn + ".mcfunction"), "w", encoding="utf-8") as f:
            f.write("\n".join(cmds) + "\n")
        build.append(f"function dna:{fn}")
        clear.append(f"fill {ox} {BASE_Y} {ORIGIN_Z} {ox+sx-1} {BASE_Y+sy-1} {ORIGIN_Z+sz-1} minecraft:air")
        print(f"  dna:{fn}  @x={ox}  {sx}x{sy}x{sz}  {len(cmds)} Befehle")
        ox += sx + GAP
    with open(os.path.join(fdir, "build_all.mcfunction"), "w", encoding="utf-8") as f:
        f.write("say [DNA] Baue alle Stationen ...\n" + "\n".join(build) + "\nsay [DNA] Fertig.\n")
    with open(os.path.join(fdir, "clear_all.mcfunction"), "w", encoding="utf-8") as f:
        f.write("\n".join(clear) + "\n")
    zp = "dna_datapack.zip"
    with zipfile.ZipFile(zp, "w", zipfile.ZIP_DEFLATED) as z:
        for dp, _, files in os.walk(root):
            for fl in files:
                full = os.path.join(dp, fl)
                z.write(full, os.path.relpath(full, root))
    print(f"{zp}: Datapack geschrieben, {total} Befehle gesamt (build_all).")
    print(f"Gesamtbreite in X: {ox - GAP} Blöcke (ORIGIN_X={ORIGIN_X}, GAP={GAP}, BASE_Y={BASE_Y})")

if __name__ == "__main__":
    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    if "datapack" in sys.argv:
        datapack(); sys.exit(0)
    os.makedirs("stationen", exist_ok=True)
    pv = "preview" in sys.argv
    if pv: os.makedirs("vorschau", exist_ok=True)
    for fn, name, f in STATIONEN + [("../dna_replikation_gesamt", "DNA-Replikation gesamt", gesamt)]:
        B.clear(); f(); blk = final_blocks()
        write(f"stationen/{fn}.litematic", name, blk)
        if pv: preview(f"vorschau/{os.path.basename(fn)}.png", blk)
