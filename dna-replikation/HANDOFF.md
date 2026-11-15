# Übergabe: DNA-Replikation-Projekt (für neuen Claude-Code-Chat)

## Zum Einfügen in den neuen Chat
> Lies `dna-replikation/HANDOFF.md` im Repo `mio530/minecraft-mods` (Branch `claude/jolly-ptolemy-8k5mi1`) und mach dort weiter. Antworte kurz, Deutsch, nur Key-Infos.

## Nutzer & Präferenzen
- Schüler, Bio-Präsentation vor der Klasse: **8-Minuten-Video zur DNA-Replikation**, in Minecraft gefilmt, auf Basis der Gruppenarbeit („Fünf Briefe, fünf Entdeckungen“, „Das Modell bauen“, „Sicherung“, „Bausteine molekular“, Arbeitsblatt/Material DNA-Replikation).
- Antworten **kurz und knapp**, Deutsch. Bei Minecraft-Code immer **leichte .jar ohne Performance-Verlust**.
- Vor größeren Änderungen nachfragen, wenn Infos fehlen.

## Setup des Nutzers
- PC: Ryzen 7 7800X3D, RTX 3070, 64 GB RAM (Minecraft 8 GB zugewiesen).
- Minecraft **Java 1.21.11, Fabric**, Mods: Sodium 0.8.14, Iris 1.10.8, ImmediatelyFast, **Litematica** (+ eigener VisionMod aus diesem Repo).
- Aufnahme: **OBS**. Schnitt: **DaVinci Resolve Pro**.
- Gelöst: Render Distance war effektiv 2 (F3 zeigte `SD: 2`) – lag an der Server-/Weltseite, läuft jetzt.

## Repo
- `mio530/minecraft-mods`, Branch `claude/jolly-ptolemy-8k5mi1` (kein PR).
- Repo enthält sonst den Fabric-Client-Mod „VisionMod“ (`src/`, ClickGUI/ESP) – nicht Teil dieses Projekts.

## Dateien in `dna-replikation/`
| Datei | Inhalt |
|---|---|
| `generate_schematic.py` | Generator (nur stdlib, Pillow nur für Vorschau). `python3 generate_schematic.py [preview]` |
| `stationen/00…07_*.litematic` | 8 Stationen (eine pro Szene) |
| `dna_replikation_gesamt.litematic` | Gesamtmodell 70×210×44 |
| `vorschau/*.png` | Vorderansicht jeder Datei |
| `drehbuch.md` | 10 Szenen mit Zeit, Station, Kamera, Sprechtext (~950 Wörter ≈ 8 Min.), Farb-Legende, DaVinci-Anleitung |
| `einblendungen.srt` | 18 Texteinblendungen mit Zeitmarken (DaVinci: File → Import → Subtitle) |

Stationen: 00 Nukleotide (A,T,G,C,U) · 01 Eltern-DNA · 02 Topoisomerase · 03 Helikase/Gabel · 04 Primase/RNA-Primer · 05 Polymerase (Leitstrang + 1. Okazaki-Fragment) · 06 Okazaki-Fragmente + Ligase · 07 Ergebnis (2 Tochterhelices).

## Anforderungen an die Schematics (vom Nutzer festgelegt)
- Format **Litematica `.litematic`** (Version 6, DataVersion 3955; Litematica lädt es in 1.21.11).
- **Hochkant**: Eltern-DNA **unten**, duplizierte Tochterstränge **oben** (Drehung, keine Spiegelung → Helix bleibt rechtsgängig).
- **2-fach skaliert** (jeder Block → 2×2×2), möglichst **simpel/gut erkennbar**.
- Vorbild: Lehrbuch-Grafik „DNA replication“ (Topoisomerase-Ring, Helikase, Primase, RNA-Primer, Polymerase, Okazaki-Fragmente, Leit-/Folgestrang).
- Einzelne **Stationen** zum Filmen + Gesamtmodell.
- Neue Versionen bekommen **neue Dateinamen**, damit Litematica keine alte Platzierung zeigt.

## Farbcode
Blau/Cyan = alte Stränge · Rot = neuer Leitstrang · Orange = neuer Folgestrang · Beton/Terrakotta im Rückgrat = Zucker/Phosphat · Lime/Gelb/Lila/Pink/Braun = A/T/G/C/U (Purine größer) · weißes Glas gestrichelt = H-Brücken (A–T 2, G–C 3) · weißes Rückgrat = RNA-Primer · Glowstone/grünes Froschlicht = 5'/3'-Ende · Crying Obsidian-Ring = Topoisomerase · Gold = Helikase · Diamant = Primase · Smaragd = DNA-Polymerase · Seelaterne = Ligase.
Vorderseite (Enzyme davor) = +Z. Von vorn: Leitstrang links, Folgestrang rechts.

## Geometrie (im Generator, vor Drehung: x = Längsachse)
Radius 6, 30 Blöcke/Windung, Basenpaar alle 3 Blöcke. Eltern-Helix x 0–30 · Gabel/offene Zone x 30–71 · Tochterhelices x 72–102. Leitstrang ab x 45, Folgestrang-Fragmente 45–53 (Primer 45–50), Lücke 54–56, 57–65, 66+; Ligase bei 64–67.

## Verlauf (Kurz)
1. Fahrplan + .litematic v1 (liegend) → hochkant → umgedreht (Töchter oben) → 2× größer → vereinfacht → nach Vorbild-Bild erweitert (v6) → 7 Stationen.
2. Jetzt v2: genauere Stationen + Drehbuch + SRT (aktueller Stand).

## Offen / Ideen
- Nicht in Minecraft getestet, ob alle Stationen sauber laden (nur Vorschau-PNGs geprüft).
- Topoisomerase, Primase, Primer/Uracil, Okazaki, Purine/Pyrimidine gehen über die Unterrichtsnotizen hinaus → ggf. mit Lehrer abklären/weglassen.
- Mögliche Wünsche: Beschriftungen als Blöcke/Schilder, SSB-Proteine, Anpassung an echte Sprechdauer.
