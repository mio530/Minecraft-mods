# J.A.R.V.I.S. – dein persönlicher KI-Assistent

Ein **echter, funktionierender** Sprach-KI-Assistent im Stil von Jarvis aus Iron Man –
für **Windows**, **Linux** und **Android**. Er hat **echten Zugriff auf dein Gerät**:
Programme öffnen, Befehle ausführen, Dateien lesen/schreiben, Systeme steuern und
(am Handy) Akku, SMS, Standort, Taschenlampe usw.

> **Ehrliche Einordnung:** Ein allmächtiges Film-Jarvis (eine bewusste AGI, die *alles*
> steuert) gibt es real nicht. Das hier ist aber das Nächste, was heute wirklich
> machbar ist: ein KI-Agent, der über Sprache gesteuert wird und **echte Werkzeuge auf
> deinem System ausführt**.

## Zwei Versionen – such dir eine aus

| | **Version A: Claude** | **Version B: KOSTENLOS** |
|---|---|---|
| Startdatei | `jarvis_windows.py` / `jarvis_linux.py` / `jarvis_android.py` | **`jarvis_free.py`** (alle Plattformen) |
| Gehirn | Claude Opus 4.8 (Cloud) | **Ollama** (lokal) oder **Groq** (Gratis-Cloud) |
| Kosten | pro Nutzung (API-Key nötig) | **0 €** |
| Klugheit | sehr hoch | gut bis sehr gut (je nach Modell) |
| Offline? | nein | ja (mit Ollama) |

**Wenn du „kostenfrei" willst → nimm `jarvis_free.py`.** Details unten unter
„Kostenlose Version".

---

## Was du brauchst

1. **Python 3.9+** (auf dem PC; am Handy über Termux).
2. **Einen Anthropic-API-Key** – hol dir einen unter <https://console.anthropic.com>.
   Der Key steuert die Kosten (Bezahlung pro Nutzung). Setze ihn als Umgebungsvariable:

   | Plattform | Befehl |
   |-----------|--------|
   | Linux/macOS/Termux | `export ANTHROPIC_API_KEY="sk-ant-..."` |
   | Windows (CMD) | `set ANTHROPIC_API_KEY=sk-ant-...` |
   | Windows (PowerShell) | `$env:ANTHROPIC_API_KEY="sk-ant-..."` |

---

## Dateien

| Datei | Zweck |
|-------|-------|
| `tools_base.py` | Werkzeug-Grunddefinition (ohne KI-Abhängigkeit) |
| `tools_common.py` | Werkzeuge für alle Plattformen (Shell, Dateien, Web, Systeminfo) |
| `tools_power.py` | Voller Gerätezugriff (umschreiben, verschieben, löschen, Prozesse, Code) |
| `safety.py` | Schutz-Schicht: schützt System/Apps, fragt bei fremden Dateien |
| `sentry.py` | Wächter-Modus: Kamera-Überwachung, PC-Sperre, Handy-Alarm |
| `jarvis_sentry_android.py` | Handy-Begleiter: Alarm empfangen + per Fingerabdruck entsperren |
| `tools_windows.py` / `tools_linux.py` / `tools_android.py` | plattform-spezifische Werkzeuge |
| `memory.py` | Dauerhaftes Gedächtnis (merkt sich dein Verhalten) |
| `workspace.py` | Werkstatt: eigener Arbeitsbereich mit Versionsverlauf |
| `tools_git.py` | Git-/GitHub-Anbindung (Repo anlegen, committen, pushen) |
| `voice.py` | Sprachein-/ausgabe für Desktop |
| `jarvis_core.py` | Gehirn der **Claude**-Version |
| `jarvis_free_core.py` | Gehirn der **kostenlosen** Version (Ollama/Groq/Offline) |
| **`jarvis_windows.py`** / **`jarvis_linux.py`** / **`jarvis_android.py`** | **Start: Claude-Version** |
| **`jarvis_free.py`** | **Start: kostenlose Version (alle Plattformen, Konsole)** |
| **`jarvis_gui.py`** | **Start: grafische Oberfläche (Tippen + Stimme)** |

Du startest immer **eine** Datei. Alle anderen `.py`-Dateien müssen nur im selben
Ordner liegen.

---

## 🪟 Windows

```powershell
pip install anthropic
pip install SpeechRecognition pyttsx3 pyaudio   # optional, für Sprache
set ANTHROPIC_API_KEY=sk-ant-...
python jarvis_windows.py
```

Kann: Programme/URLs öffnen, PowerShell-Befehle, Dateien, Toast-Benachrichtigungen,
Lautstärke, Sperren/Herunterfahren/Ruhezustand.

---

## 🐧 Linux

```bash
pip install anthropic
pip install SpeechRecognition pyttsx3 pyaudio     # optional, für Sprache
sudo apt install espeak xdg-utils libnotify-bin   # Debian/Ubuntu, für TTS & Öffnen
export ANTHROPIC_API_KEY="sk-ant-..."
python3 jarvis_linux.py
```

Kann: Programme/URLs/Dateien öffnen (`xdg-open`), Shell-Befehle, Dateien,
Desktop-Benachrichtigungen (`notify-send`), Lautstärke (`pactl`/`amixer`).

---

## 🤖 Android (via Termux)

Android lässt keine App frei aufs System zugreifen. Der echte Weg ist **Termux**:

1. Installiere **Termux** *und* **Termux:API** aus **F-Droid**
   (nicht aus dem Play Store – die Version dort ist veraltet).
2. In Termux:
   ```bash
   pkg update && pkg upgrade
   pkg install python termux-api
   pip install anthropic
   termux-setup-storage
   export ANTHROPIC_API_KEY="sk-ant-..."
   python jarvis_android.py
   ```

Kann: Akkustand, Standort (GPS), SMS senden, anrufen, Taschenlampe, Vibration,
Benachrichtigungen, Zwischenablage, Kontakte lesen, URLs öffnen, Shell-Befehle –
plus Sprachein-/ausgabe über `termux-speech-to-text` / `termux-tts-speak`.

---

---

## 🖥️ Grafische Oberfläche (`jarvis_gui.py`) – Tippen **und** Stimme

Ein Fenster (Desktop: Windows / Linux / macOS), in dem du Jarvis auf beide Arten
bedienst:

- **Tippen:** Text ins Eingabefeld, Enter oder „Senden".
- **Stimme (einmal):** 🎤-Knopf drücken und sprechen.
- **🗣️ Gesprächsmodus (freihändig):** Knopf drücken → einfach drauflos reden.
  Jarvis hört zu, **antwortet mit Stimme** und hört danach automatisch wieder zu –
  ein echtes Gespräch ohne Klicken. Sag **„Stopp"** (oder drück „Stoppen"), um
  den Modus zu beenden.
- **👂 Wake-Word „Hey Jarvis":** Wenn der Haken oben aktiv ist (bei vorhandenem
  Mikrofon standardmäßig an), lauscht Jarvis im Hintergrund. Sagst du
  **„Hey Jarvis"**, wechselt er automatisch in den Gesprächsmodus, antwortet
  „Ja, Sir?" und du kannst direkt lossprechen – ganz ohne Klicken.
- **Vorlesen:** Haken „Antwort vorlesen" schaltet die Sprachausgabe an/aus.
- **KI wählen:** oben umschaltbar zwischen *Kostenlos* (Ollama/Groq) und *Claude*
  (Claude erscheint nur, wenn `ANTHROPIC_API_KEY` gesetzt ist).

```bash
pip install openai                              # kostenlose KI
pip install SpeechRecognition pyttsx3 pyaudio   # optional, für Stimme
# Linux zusätzlich, falls tkinter fehlt:  sudo apt install python3-tk
python jarvis_gui.py
```

Ohne Mikrofon/Sprach-Pakete ist der 🎤-Knopf einfach deaktiviert – Tippen geht
immer. Gefährliche Aktionen werden per Popup-Fenster bestätigt.

> Hinweis zum Wake-Word: Die Spracherkennung läuft über Google (kostenlos, aber
> es wird dabei kurz Audio ins Netz gesendet). Wenn du das nicht möchtest,
> einfach den Haken **„👂 Hey Jarvis"** ausschalten – dann hört Jarvis nur, wenn
> du selbst den 🎤- oder Gesprächsmodus-Knopf drückst.

> Für **Android** gibt es keine grafische Oberfläche (Termux hat kein Fenster-
> system) – dort die Konsolen-Version `jarvis_free.py` bzw. `jarvis_android.py`.

---

## 💸 Kostenlose Version (`jarvis_free.py`)

Diese Version braucht **keinen kostenpflichtigen Claude-Key**. Sie erkennt dein
Betriebssystem automatisch und benutzt eine gratis KI. Es gibt drei Backends –
Jarvis wählt automatisch das beste verfügbare (`auto`), du kannst aber per
`JARVIS_BACKEND` erzwingen.

### Option 1 – Ollama (lokal, offline, 100 % gratis) — empfohlen für PC
```bash
# 1. Ollama installieren:  https://ollama.com
# 2. Ein Modell laden (unterstützt Funktionsaufrufe):
ollama pull llama3.1          # oder: qwen2.5, mistral-nemo
# 3. Python-Paket:
pip install openai
# 4. Starten:
python jarvis_free.py
```
Vorteile: läuft komplett offline, keine Anmeldung, deine Daten bleiben lokal.
Braucht etwas RAM (llama3.1 ≈ 5 GB). Für schwache Geräte kleineres Modell:
`ollama pull llama3.2` und `export OLLAMA_MODEL=llama3.2`.

### Option 2 – Groq (kostenlose Cloud) — empfohlen fürs Handy / schwache PCs
```bash
# 1. Kostenlosen Key holen:  https://console.groq.com
pip install openai
export GROQ_API_KEY="gsk_..."     # Windows:  set GROQ_API_KEY=gsk_...
python jarvis_free.py
```
Vorteile: sehr schnell, kein lokales Modell nötig. Nachteil: braucht Internet
und Anmeldung (aber gratis, mit fairen Limits).

### Option 3 – Offline-Notfallmodus (ganz ohne KI)
Wenn weder Ollama läuft noch ein Groq-Key gesetzt ist, startet Jarvis in einem
einfachen Kommando-Modus. Er versteht dann nur direkte Befehle wie „Systeminfo",
„Akku", „Standort", „Taschenlampe an", „öffne firefox" oder „Befehl: ls -la".
Kein echtes Sprachverständnis, aber sofort und ohne alles nutzbar.

### Einstellungen (Umgebungsvariablen)
| Variable | Bedeutung | Standard |
|----------|-----------|----------|
| `JARVIS_BACKEND` | `auto` / `ollama` / `groq` / `offline` | `auto` |
| `OLLAMA_HOST` | Adresse des Ollama-Servers | `http://localhost:11434` |
| `OLLAMA_MODEL` | verwendetes Ollama-Modell | `llama3.1` |
| `GROQ_API_KEY` | dein kostenloser Groq-Key | – |
| `GROQ_MODEL` | verwendetes Groq-Modell | `llama-3.3-70b-versatile` |

> **Hinweis:** Lokale/kleinere Modelle sind nicht so zuverlässig beim Werkzeug-
> Einsatz wie Claude. Wenn ein Befehl nicht klappt, formuliere ihn klarer oder
> nutze ein größeres Modell (z. B. Groq `llama-3.3-70b`).

---

## Bedienung

- **Sprich oder tippe** deine Anfrage.
- `reset` – löscht den Gesprächskontext.
- `exit` / `beenden` – beendet Jarvis.

Beispiele:
- „Wie ist mein Akkustand?" (Android)
- „Öffne YouTube."
- „Erstelle auf dem Desktop eine Datei notizen.txt mit meiner Einkaufsliste."
- „Wie viel Speicher habe ich noch frei?"
- „Schick meiner Mutter eine SMS, dass ich später komme." (Android)

---

## 🧠 Gedächtnis – Jarvis merkt sich dein Verhalten

Jarvis lernt dich über die Zeit kennen und **passt sein Verhalten an**. Wenn er
etwas Dauerhaftes über dich erfährt – deinen Namen, Vorlieben, Gewohnheiten, wie
kurz oder ausführlich du Antworten magst, wiederkehrende Aufgaben – speichert er
das und erinnert sich **beim nächsten Start** daran.

- Gespeichert wird lokal in **`~/.jarvis/memory.json`** (bleibt privat auf deinem
  Gerät, geht über keine Cloud).
- Beim Start lädt Jarvis das Gedächtnis in seinen „Kopf" und begrüßt dich als
  jemanden, den er schon kennt.
- Er hat dafür drei Werkzeuge, die er selbst benutzt:
  - `remember` – merkt sich eine Tatsache
  - `recall` – schlägt nach, was er schon weiß
  - `forget` – vergisst etwas (veraltet oder auf deinen Wunsch)

Du kannst ihm auch direkt Anweisungen geben, z. B.:
- „Merk dir, dass ich dich Boss nennen möchte."
- „Ich mag kurze Antworten – merk dir das."
- „Was weißt du über mich?"  → er liest sein Gedächtnis vor
- „Vergiss, dass ich Kaffee mag."

**Alles zurücksetzen:** einfach die Datei `~/.jarvis/memory.json` löschen (oder
Jarvis sagen „vergiss alles über mich").

> Hinweis: Jarvis speichert **keine Passwörter/Geheimnisse** ins Gedächtnis.

---

## 🛠️ Voller Gerätezugriff

Jarvis kann auf **alles** auf dem Gerät zugreifen, es benutzen und **umschreiben**.
Neben Shell/Dateien/Systeminfo hat er dafür zusätzliche Power-Werkzeuge (auf allen
Plattformen):

| Werkzeug | Was es tut |
|----------|------------|
| `edit_file` | Text in einer Datei **ersetzen/umschreiben** |
| `move_path` / `copy_path` | verschieben, umbenennen, kopieren |
| `delete_path` | Datei oder Ordner löschen |
| `make_dir` | Ordner anlegen |
| `find_files` | Dateien per Muster suchen (z. B. `*.pdf`) |
| `search_in_files` | Dateiinhalte durchsuchen (grep-artig) |
| `chmod_path` | Rechte ändern / ausführbar machen |
| `list_processes` / `kill_process` | Prozesse anzeigen und beenden |
| `run_python` | **beliebigen Python-Code ausführen** (maximaler Zugriff) |
| `env` | Umgebungsvariablen lesen |
| `run_shell` | jeden beliebigen Systembefehl ausführen |

Damit kann er praktisch alles tun, was du selbst am Gerät tun könntest.

### 🛡️ Schutz-Schicht (immer aktiv)

Jarvis hat einen Wächter (`safety.py`), der **vor jeder Aktion** prüft, was
erlaubt ist. Diese Regeln gelten **immer – auch im Vollzugriff-Modus**:

| Situation | Verhalten |
|-----------|-----------|
| System- oder App-Dateien ändern/löschen | ⛔ **blockiert** (nie erlaubt) |
| Datei ändern/löschen, die Jarvis **nicht selbst** erstellt hat | ❓ **fragt nach** |
| „Wichtig" wirkende Dateien (`~/.ssh`, `.bashrc`, Schlüssel …) | ❓ **fragt nach** |
| **Neue** Datei/Ordner **erstellen** | ✅ ohne Nachfrage |
| Datei ändern, die Jarvis **selbst erstellt** hat | ✅ ohne Nachfrage |

So kann Jarvis nichts Wichtiges kaputt machen, arbeitet aber bei eigenen und
neuen Dateien flüssig. Geschützte Bereiche sind u. a. `C:\Windows`,
`C:\Program Files` (Windows); `/usr`, `/etc`, `/bin`, `/System`, `/Applications`
(Linux/macOS); `/system`, `/data/app` und die Termux-Laufzeit (Android).

### 🔓 Vollzugriff-Modus

Standardmäßig fragt Jarvis vor riskanten **Befehlen** (Shell, Code, Prozesse
beenden …) nach. Im Vollzugriff-Modus laufen diese **ohne Nachfrage** – die
Schutz-Schicht oben **bleibt trotzdem aktiv** (System/Apps geschützt, fremde
Dateien werden weiter abgefragt).

- **Konsole:** `export JARVIS_UNRESTRICTED=1` (Windows: `set JARVIS_UNRESTRICTED=1`)
- **GUI:** oben den Haken **„🔓 Vollzugriff"** (mit Sicherheitsabfrage).

> ⚠️ Nur auf deinem eigenen Gerät und mit einem verlässlichen Modell nutzen.

---

## 📷 Kamera – Jarvis kann sehen

Jarvis kann auf die Kamera zugreifen (Desktop-Webcam bzw. Handy-Kamera):

| Werkzeug | Was es tut |
|----------|------------|
| `take_photo` | nimmt ein Foto auf und speichert es (in `~/.jarvis/photos/`) |
| `look` | nimmt ein Foto auf und **beschreibt, was darauf zu sehen ist** |
| `enroll_face` | **lernt dein Gesicht an** (Referenzfoto + Name) |
| `recognize_me` | **erkennt dich wieder** (vergleicht Live-Foto mit deinem Gesicht) |
| `learn_movement` | **lernt, wie du dich bewegst** (für präzisere Wiedererkennung) |

Sag z. B.: „**Schau mal, was siehst du?**", „Mach ein Foto", „Ist jemand im Raum?",
„**Merk dir mein Gesicht, ich bin Mio**", „**Erkennst du mich?**".

**Dich erkennen:** Einmal „merk dir mein Gesicht" sagen – Jarvis speichert ein
Referenzbild (`~/.jarvis/face/owner.jpg`). Danach kann er bei „erkennst du mich?"
ein Live-Foto aufnehmen und per Vision-Modell abgleichen, ob du es bist. (Nutzt
dasselbe Vision-Backend wie `look`.)

- **Desktop (Windows/Linux/macOS):** braucht OpenCV → `pip install opencv-python`
- **Android (Termux):** nutzt die Termux-Kamera (kein Extra-Paket nötig)
- **Sehen (`look`):** die Bildbeschreibung nutzt automatisch ein Vision-Modell –
  Claude (`ANTHROPIC_API_KEY`), Groq (`GROQ_API_KEY` + Vision-Modell) oder ein
  lokales Ollama-Modell wie **llava** (`ollama pull llava`). Modell überschreibbar
  per `JARVIS_VISION_MODEL`. Ohne Vision-Modell wird das Foto trotzdem gespeichert.

> Die Kamera ist privatsphäre-sensibel und wird deshalb **vor der Aufnahme
> abgefragt** (auch im Vollzugriff-Modus für Befehle bleibt das eine bewusste
> Aktion).

---

## 💻 Programmieren + Werkstatt + GitHub

Jarvis kann **programmieren** und legt fertige Dateien/Projekte in seine
**Werkstatt** – einen eigenen Ordner unter `~/.jarvis/workspace`.

### 📁 Werkstatt mit Versionsverlauf („⋮"-Menü)
- Fertige Dateien speichert Jarvis mit `workspace_save`.
- **Beim Überschreiben bleibt die vorherige Variante erhalten** (im versteckten
  `.versions`-Verlauf).
- In der GUI oben auf **„📁 Werkstatt"** klicken → Liste aller Dateien. Neben
  jeder Datei gibt es das **„⋮"-Menü** mit:
  - **▶ Ausführen** → startet die Datei direkt und zeigt die Ausgabe
  - **🔧 Ausführen & fixen lassen** → läuft die Datei; schlägt sie fehl, schickt
    Jarvis den Fehler automatisch an sich selbst und **behebt ihn**
  - **Anzeigen**
  - **Frühere Varianten …** → alle überschriebenen Versionen ansehen und
    **wiederherstellen**
  - **Löschen** (Verlauf bleibt erhalten)

Sag z. B.: „Schreib mir ein Python-Spiel und leg es in die Werkstatt", oder
„Stell die vorige Version von app.py wieder her".

### ▶ Ausführen & 🔧 Selbst-Reparieren
- Jarvis kann Code **selbst ausführen und testen** (`run_file` erkennt Python/JS/
  Bash/… automatisch, `workspace_run` führt beliebige Befehle im Werkstatt-Ordner
  aus, z. B. `pip install …`).
- **Auto-Fix:** Schreibt Jarvis Code, führt er ihn aus, **liest bei Fehlern die
  Meldung, behebt die Ursache und führt erneut aus** – so lange, bis es läuft.
  Am Ende erklärt er kurz, was der Fehler war.
- So kann er auf Anfrage auch **Dinge auf deinem PC/Handy ausführen oder
  reparieren** – Programme installieren, Skripte laufen lassen, Fehler beheben.

### 🔗 GitHub-Anbindung
- Token anlegen: <https://github.com/settings/tokens> (Scope **repo**), dann
  `export GITHUB_TOKEN=ghp_...` (Windows: `set GITHUB_TOKEN=ghp_...`).
- Werkzeuge: `git_init`, `git_commit`, `git_push`, `git_clone`,
  `github_create_repo` und **`github_publish`** (ein Schritt: committen, Repo
  anlegen, verknüpfen, pushen).
- Sag z. B.: „**Lade mein Projekt auf GitHub**" → er legt das Repo an und pusht.

> Braucht **git** installiert (<https://git-scm.com>). Push/Repo-Anlegen sind
> heikle Aktionen und werden vorher abgefragt.

---

## 🛡️ Wächter-Modus (Windows/Linux) – erkennt Fremde und sperrt den PC

Wenn die GUI offen ist, kann Jarvis im **Hintergrund die Webcam überwachen**.
Erkennt er eine **fremde Person** (nicht dein angelerntes Gesicht), dann:

1. **sperrt er den PC** – ein randloses Sperrfenster **übernimmt den Bildschirm
   über ALLE Monitore** (2. und 3. Monitor inklusive): es liegt über allem (auch
   der Taskleiste), fängt Alt+F4 / Alt+Tab / Escape / Windows-Taste ab, greift
   sich alle Eingaben (globaler Grab) und holt sich den Fokus sofort zurück
2. **schickt einen Alarm + ein Foto** des Eindringlings **aufs Handy** und danach
   **fortlaufend Live-Bilder** (~alle 5 s) – mit Ton + Vibration
3. **entsperrt automatisch, wenn er DICH wiedererkennt** – aber nur, wenn du dich
   **~1 Sekunde bewegst** (Lebend-Check, damit ein Foto von dir ihn nicht täuscht).
   Wenn du zusätzlich „**merk dir, wie ich mich bewege**" sagst, lernt er dein
   **Bewegungsmuster** an und gleicht es beim Entsperren mit ab – so unterscheidet
   er dich noch genauer von Fremden.
   Alternativ jederzeit manuell:
   - **Passwort** am PC eintippen
   - laut **„entsperren"** sagen (per `JARVIS_UNLOCK_PHRASE` änderbar)
   - **Fingerabdruck** am Handy

Und: **im Dunkeln sperrt er nicht** – wenn das Bild zu dunkel und die Erkennung
unsicher ist, wartet er lieber ab, statt einen Fehlalarm auszulösen.

### Einrichten
1. In der GUI zuerst dein Gesicht anlernen („Merk dir mein Gesicht").
2. Oben den Haken **„🛡️ Wächter"** setzen. Beim ersten Mal legst du ein
   **Entsperr-Passwort** fest, und Jarvis zeigt dir ein **Topic** + **Token**.
3. **Auf dem Handy** (Termux):
   - Entweder die **ntfy-App** installieren und das Topic abonnieren → du bekommst
     den Alarm als Push (auch wenn Termux zu ist).
   - Für das Entsperren per Fingerabdruck: `pkg install termux-api`, dann
     ```bash
     export JARVIS_NTFY_TOPIC=jarvis-....     # vom PC angezeigt
     export JARVIS_SENTRY_TOKEN=....          # vom PC angezeigt
     python jarvis_sentry_android.py          # lauscht auf Alarme
     python jarvis_sentry_android.py unlock   # Fingerabdruck -> PC entsperren
     ```

Die Verbindung läuft über **ntfy.sh** – kostenlos, ohne Server und ohne Account.

**Feineinstellung (Umgebungsvariablen):**
| Variable | Bedeutung | Standard |
|----------|-----------|----------|
| `JARVIS_AUTO_UNLOCK` | Auto-Entsperren bei Wiedererkennung (`0` = aus) | `1` |
| `JARVIS_SENTRY_MIN_BRIGHTNESS` | Ab welcher Helligkeit (0–255) überhaupt gesperrt wird | `35` |
| `JARVIS_MOTION_THRESHOLD` | Wie viel Bewegung als „lebendig" zählt | `6` |
| `JARVIS_UNLOCK_PHRASE` | gesprochenes Entsperr-Wort | `entsperren` |
| `JARVIS_OS_LOCK` | zusätzlich echten OS-Sperrbildschirm auslösen | aus |

> **Zur Stimm-Entsperrung:** Echte Stimm-Biometrie („nur DEINE Stimme") ist mit
> diesen Mitteln nicht zuverlässig – das „entsperren" wirkt wie ein gesprochenes
> Passwort. Setze mit `JARVIS_UNLOCK_PHRASE` ein geheimes Wort, das ein Fremder
> nicht errät. **Zum Live-Video:** echtes Streaming braucht einen Server; Jarvis
> schickt stattdessen fortlaufend Fotos (Schnappschuss-„Live-Feed").

> **Ehrliche Grenze:** Das Jarvis-Sperrfenster ist eine **App-Sperre**, kein
> echter Betriebssystem-Login. Software kann einen fremden PC nicht per Ferne
> aus dem echten Login-Sperrbildschirm holen – das wäre eine Sicherheitslücke.
> Für **maximale** Sicherheit zusätzlich `JARVIS_OS_LOCK=1` setzen: dann wird
> auch der echte OS-Sperrbildschirm ausgelöst (den öffnest du nur mit deinem
> Konto-Passwort direkt am PC; die Handy-Entsperrung entfernt dann nur noch das
> Jarvis-Fenster).

---

## 🔒 Sicherheit

Jarvis kann echte Befehle ausführen – das ist mächtig und potenziell riskant.
Deshalb:

- **Gefährliche Aktionen** (Shell-Befehle, Dateien überschreiben, herunterfahren,
  SMS/Anrufe, Kontakte lesen) werden **vor der Ausführung abgefragt** – du musst
  mit `j` bestätigen.
- Dein API-Key steht **nur** in der Umgebungsvariable, nie im Code.
- Führe Jarvis nur auf deinen eigenen Geräten aus. Prüfe, was er tun will, bevor du
  bestätigst.

---

## Wie es funktioniert

```
Deine Sprache ──► Text ──► Claude (Opus 4.8) ──► wählt Werkzeug(e)
                                    ▲                     │
                                    │                     ▼
                            Ergebnis zurück ◄──── Jarvis führt es
                                                  auf dem Gerät aus
                                    │
                                    ▼
                            Antwort ──► Sprachausgabe
```

Claude bekommt die Liste der verfügbaren Werkzeuge und entscheidet selbst, welche
es für deine Anfrage braucht. Der Code führt sie aus und gibt das Ergebnis zurück –
so oft, bis die Aufgabe erledigt ist.
