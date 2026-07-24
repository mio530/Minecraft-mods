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
| `tools_windows.py` / `tools_linux.py` / `tools_android.py` | plattform-spezifische Werkzeuge |
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
