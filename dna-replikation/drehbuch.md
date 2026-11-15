# DNA-Replikation – Drehbuch (8 Min.)

## Dateien
- `stationen/00…07_*.litematic`: eine Szene pro Datei
- `dna_replikation_gesamt.litematic`: alles in einem (Intro/Ende)
- `einblendungen.srt`: Texteinblendungen für DaVinci
- `vorschau/*.png`: Vorderansicht jeder Station

## Setup Minecraft
- Fabric 1.21.x + Litematica, Superflat, Creative
- `/time set noon`, `/gamerule doDaylightCycle false`, `/gamerule doWeatherCycle false`
- Litematica: `M` → *Load Schematics* → *Paste* (Creative). Pro Station: platzieren, filmen, löschen (`M` → *Loaded Schematics* → entfernen + Blöcke weg mit `//undo` bzw. neue Fläche nutzen)
- Tipp: alle 8 Stationen nebeneinander auf Abstand 100 Blöcke einfügen, dann ohne Umbau filmen
- Aufnahme: OBS 1080p/30 fps, `F1` (HUD aus), FOV 70, langsam fliegen. Vorderseite = Seite mit den Enzymen
- Jede Einstellung 2× aufnehmen, 3 s Ruhe am Anfang/Ende (Schnittreserve)

## Farbcode
| Block | Bedeutung |
|---|---|
| Blau / Cyan | alter Strang 1 / 2 |
| Rot | neuer Leitstrang |
| Orange | neuer Folgestrang (Okazaki-Fragmente) |
| Beton / Terrakotta im Rückgrat | Zucker (Desoxyribose) / Phosphat |
| Lime / Gelb / Lila / Pink | Adenin / Thymin / Guanin / Cytosin (Purine A,G größer) |
| Braun | Uracil (nur im RNA-Primer) |
| Weißes Glas, gestrichelt | Wasserstoffbrücken (A–T: 2, G–C: 3) |
| Weißes Rückgrat | RNA-Primer |
| Glowstone / grünes Froschlicht | 5'-Ende / 3'-Ende |
| Dunkler Ring | Topoisomerase |
| Gold | Helikase |
| Diamant | Primase |
| Smaragd | DNA-Polymerase |
| Seelaterne | Ligase |

## Szenen (≈ 950 Wörter, ruhig gesprochen ≈ 8:00)

### 1 · Intro (0:00–0:35) · `dna_replikation_gesamt`
Kamera: Totale von vorn, langsam von unten nach oben schwenken.
> Hallo zusammen. In unserem Video geht es um die DNA-Replikation, also darum, wie eine Zelle ihre DNA vor der Zellteilung identisch verdoppelt. Damit jede Tochterzelle die vollständige Erbinformation bekommt, muss die DNA vorher exakt kopiert werden. Wir haben den ganzen Ablauf in Minecraft nachgebaut. Unten seht ihr die ursprüngliche DNA, in der Mitte die Replikationsgabel mit den Enzymen und oben die beiden fertigen Kopien. Wir gehen das jetzt Station für Station durch.

### 2 · Bausteine (0:35–1:20) · `00_nukleotide`
Kamera: nah, von unten nach oben an den 5 Nukleotiden entlang.
> Zuerst die Bausteine. Die DNA besteht aus Nukleotiden. Ein Nukleotid hat drei Teile: eine Phosphatgruppe, hier der Terrakotta-Block, einen Zucker, die Desoxyribose, hier der Betonblock, und eine Base. Es gibt vier Basen: Adenin in Grün, Thymin in Gelb, Guanin in Lila und Cytosin in Pink. Adenin und Guanin sind größer, sie gehören zu den Purinen. Thymin und Cytosin sind kleiner, das sind die Pyrimidine. Das weiße Nukleotid ganz oben ist ein RNA-Nukleotid mit der Base Uracil in Braun. Das brauchen wir später für den Primer.

### 3 · Aufbau der DNA (1:20–2:20) · `01_eltern_dna`
Kamera: einmal um die Helix kreisen, dann nah an ein A–T- und ein G–C-Paar, zum Schluss auf die Enden (Glowstone/Froschlicht).
> Das ist die DNA vor der Replikation. DNA steht für Desoxyribonukleinsäure und ist der Träger der Erbinformation. Sie bildet eine Doppelhelix aus zwei Strängen. Außen liegt das Zucker-Phosphat-Rückgrat, hier in Blau und Cyan. Innen liegen die Basen, immer als komplementäre Basenpaare: Adenin mit Thymin über zwei Wasserstoffbrücken, Guanin mit Cytosin über drei. Die Wasserstoffbrücken seht ihr als gestricheltes weißes Glas. Wichtig ist: Die beiden Stränge verlaufen antiparallel. Jeder Strang hat ein 5'-Ende, hier der Glowstone, und ein 3'-Ende, hier das grüne Froschlicht. Beim blauen Strang ist das 5'-Ende unten, beim cyanfarbenen oben. Die Reihenfolge der Basen ist der genetische Code und bestimmt die Erbinformation.

### 4 · Prinzip (2:20–2:55) · `dna_replikation_gesamt`
Kamera: Totale, dann Blick auf die zwei Tochterhelices oben.
> Watson und Crick haben 1953 schon vermutet, wie die Verdopplung funktioniert. Weil die Stränge komplementär sind, enthält jeder Strang die komplette Information für den anderen. Trennt man die Stränge, kann man jeden als Vorlage nutzen und mit neuen Nukleotiden ergänzen. Am Ende hat jede neue DNA einen alten und einen neuen Strang. Das nennt man semikonservative Replikation. Spätere Experimente haben das bestätigt.

### 5 · Topoisomerase (2:55–3:30) · `02_topoisomerase`
Kamera: seitlich auf den Ring, langsam heranfahren.
> Jetzt zum Ablauf. Damit die DNA geöffnet werden kann, muss sie entspiralisiert werden. Wenn man die Helix an einer Stelle aufdreht, verdrillt sie sich davor immer stärker, ähnlich wie ein Telefonkabel. Das Enzym Topoisomerase, hier der dunkle Ring, verhindert das. Es schneidet die DNA kurz an, entspannt sie und verbindet sie wieder. Es arbeitet also vor der Replikationsgabel.

### 6 · Helikase & Replikationsgabel (3:30–4:20) · `03_helikase_gabel`
Kamera: nah auf den Goldblock, dann nach oben an den offenen Einzelsträngen entlang.
> Das Enzym Helikase, hier der Goldblock, öffnet den Doppelstrang. Es spaltet die Wasserstoffbrücken zwischen den Basenpaaren, also zwischen Adenin und Thymin und zwischen Guanin und Cytosin. Dabei wandert es wie ein Reißverschluss an der DNA entlang. Die Stelle, an der sich die Stränge trennen, heißt Replikationsgabel. Darunter seht ihr noch die Doppelhelix, darüber die zwei Einzelstränge. Ihre Basen liegen jetzt frei und dienen als Vorlage. In der Zelle halten kleine Proteine die Einzelstränge auseinander, damit sie sich nicht wieder verbinden.

### 7 · Primase & RNA-Primer (4:20–5:05) · `04_primase_primer`
Kamera: Diamantblock, dann nah auf die weißen Primer, kurz auf einen braunen Uracil-Block.
> Die DNA-Polymerase, die den neuen Strang baut, hat ein Problem: Sie kann nicht von null anfangen, sondern nur an ein vorhandenes Stück anhängen. Deshalb setzt das Enzym Primase, hier der Diamantblock, zuerst einen kurzen Startstrang, den RNA-Primer. Ihr seht ihn als weißes Rückgrat. Weil er aus RNA besteht, steht gegenüber von Adenin kein Thymin, sondern Uracil, der braune Block. Für jeden neuen Strang braucht man mindestens einen Primer.

### 8 · DNA-Polymerase & Leitstrang (5:05–5:55) · `05_polymerase`
Kamera: linke Seite: Polymerase → roter Strang → 5'/3'-Markierungen.
> Jetzt kommt die DNA-Polymerase, die smaragdgrünen Blöcke. Freie Nukleotide aus dem Zellkern lagern sich komplementär an die Vorlage an, und die Polymerase verknüpft sie zu einem neuen Strang. Ganz wichtig: Die Polymerase kann nur in eine Richtung arbeiten, nämlich von 5' nach 3'. Der neue Strang ist dabei antiparallel zur Vorlage. Am linken Strang zeigt diese Richtung genau zur Gabel hin. Die Polymerase kann hier einfach der Helikase folgen und den neuen roten Strang durchgehend bauen. Das ist der Leitstrang.

### 9 · Folgestrang, Okazaki-Fragmente, Ligase (5:55–7:00) · `06_okazaki_ligase`
Kamera: rechte Seite von unten nach oben: Primer → Fragment → Lücke mit Polymerase → Seelaterne.
> Am rechten Strang ist es komplizierter. Weil die Stränge antiparallel sind, zeigt die 5'-nach-3'-Richtung hier von der Gabel weg. Die Polymerase kann also nicht einfach hinterherlaufen. Stattdessen setzt die Primase immer wieder einen neuen Primer, sobald sich die Gabel ein Stück weiter öffnet. Von dort baut die Polymerase ein kurzes Stück, bis sie auf das vorherige Stück trifft. Diese Stücke heißen Okazaki-Fragmente, hier in Orange. Der Strang heißt Folgestrang, weil er immer hinterherhinkt. Danach werden die RNA-Primer entfernt und durch DNA ersetzt. Beim mittleren Fragment ist das schon passiert. Zum Schluss verbindet die Ligase, die Seelaterne, die Fragmente zu einem durchgehenden Strang.

### 10 · Ergebnis & Fehlerkorrektur (7:00–8:00) · `07_ergebnis`, dann `dna_replikation_gesamt`
Kamera: um die zwei Tochterhelices kreisen, letzte 15 s Totale vom Gesamtmodell, langsam rauszoomen.
> Am Ende entstehen zwei DNA-Doppelstränge, die genau gleich sind, und zwar gleich wie die ursprüngliche DNA. Jeder besteht aus einem alten und einem neuen Strang: Blau mit Rot und Cyan mit Orange. Daran sieht man, warum es semikonservativ heißt: Die Hälfte jedes Moleküls bleibt erhalten. Ganz oben links seht ihr noch den Primer des Leitstrangs, der auch noch ersetzt wird. Fehler beim Kopieren könnten zu Schäden in den Tochterzellen führen. Deshalb kontrolliert die Polymerase ihre Arbeit, und Reparatur-Enzyme ersetzen falsch gepaarte Nukleotide. So passiert nur etwa ein Fehler pro einer Milliarde Nukleotide. Zusammengefasst: entspiralisieren, öffnen, Primer setzen, neue Stränge bauen, Lücken schließen. Danke fürs Zuschauen!

## DaVinci Resolve
1. Neues Projekt: 1920×1080, 30 fps. Clips in den Media Pool.
2. Voice-over zuerst aufnehmen (Fairlight → Spur armen → Record) oder Audio importieren. Timeline nach den Zeitmarken oben.
3. Clips pro Szene über die Sprache legen, Überlänge trimmen (Blade `B`), Übergänge: Cross Dissolve 0,5 s.
4. Einblendungen: *File → Import → Subtitle* → `einblendungen.srt` → auf die Subtitle-Spur ziehen. Oder als Vorlage für *Text+*-Titel nutzen (schöner, frei positionierbar).
5. Farbcode-Legende in Szene 3 als Bild/Text+ einblenden.
6. Export: *Deliver* → H.264, 1080p, Qualität „Hoch“.
