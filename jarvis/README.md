# J.A.R.V.I.S. – dein persönlicher KI-Assistent

Ein **echter, funktionierender** Sprach-KI-Assistent im Stil von Jarvis aus Iron Man –
für **Windows**, **Linux** und **Android**. Als „Gehirn" nutzt er **Claude (Opus 4.8)**
von Anthropic und hat **echten Zugriff auf dein Gerät**: Programme öffnen, Befehle
ausführen, Dateien lesen/schreiben, Systeme steuern und (am Handy) Akku, SMS, Standort,
Taschenlampe usw.

> **Ehrliche Einordnung:** Ein allmächtiges Film-Jarvis (eine bewusste AGI, die *alles*
> steuert) gibt es real nicht. Das hier ist aber das Nächste, was heute wirklich
> machbar ist: ein KI-Agent, der über Sprache gesteuert wird und **echte Werkzeuge auf
> deinem System ausführt**.

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
| `jarvis_core.py` | Das gemeinsame Gehirn (Claude + Werkzeug-Schleife) |
| `tools_common.py` | Werkzeuge für alle Plattformen (Shell, Dateien, Web, Systeminfo) |
| `voice.py` | Sprachein-/ausgabe für Desktop |
| **`jarvis_windows.py`** | **Startdatei für Windows** |
| **`jarvis_linux.py`** | **Startdatei für Linux** |
| **`jarvis_android.py`** | **Startdatei für Android (Termux)** |

Du startest immer **eine** Datei – die für deine Plattform. Die anderen drei
(`jarvis_core`, `tools_common`, `voice`) müssen nur im selben Ordner liegen.

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
