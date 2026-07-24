#!/usr/bin/env python3
"""
JARVIS – Wächter-Begleiter für ANDROID (Termux)
===============================================

Gegenstück zum PC-Wächter. Zwei Funktionen:

  1. ALARM EMPFANGEN (Standard):
        python jarvis_sentry_android.py
     Hört auf den Alarm-Kanal. Erkennt der PC eine fremde Person, kommt hier
     eine laute Benachrichtigung + Vibration + Sprachansage.

  2. PC ENTSPERREN (per Fingerabdruck):
        python jarvis_sentry_android.py unlock
     Fragt den Fingerabdruck ab; bei Erfolg wird der PC entsperrt.

WICHTIG: Auf dem Handy müssen dieselben Werte wie am PC gesetzt sein:
        export JARVIS_NTFY_TOPIC=jarvis-....
        export JARVIS_SENTRY_TOKEN=....
(Die zeigt dir der PC an, wenn du dort den Wächter einschaltest.)

Voraussetzung: Termux + Termux:API  (pkg install termux-api).
"""

from __future__ import annotations

import json
import subprocess
import sys
import urllib.request

import sentry


def _notify(title: str, message: str) -> None:
    subprocess.run(["termux-notification", "--title", title, "--content", message,
                    "--priority", "max"], capture_output=True)
    subprocess.run(["termux-vibrate", "-d", "1500"], capture_output=True)
    try:
        subprocess.run(["termux-tts-speak", "-l", "de", message], timeout=30)
    except Exception:
        pass


def listen_alarms() -> None:
    topic = sentry.get_topic()
    url = f"https://ntfy.sh/{topic}/json"
    print(f"👂 Warte auf Alarme vom PC (Topic: {topic}) … Strg+C zum Beenden.")
    while True:
        try:
            with urllib.request.urlopen(url, timeout=60) as resp:
                for line in resp:
                    try:
                        obj = json.loads(line.decode("utf-8"))
                    except Exception:
                        continue
                    if obj.get("event") == "message":
                        title = obj.get("title", "JARVIS Alarm")
                        msg = obj.get("message", "Alarm vom PC")
                        att = obj.get("attachment") or {}
                        url = att.get("url")
                        if url:
                            msg = f"{msg}\nFoto: {url}"
                            # Foto direkt öffnen (Live-Bild vom Eindringling)
                            subprocess.run(["termux-open", "--content-type", "image/jpeg", url],
                                           capture_output=True)
                        print(f"🚨 {title}: {msg}")
                        _notify(title, msg)
        except KeyboardInterrupt:
            print("\nBeendet.")
            return
        except Exception:
            import time
            time.sleep(3)


def unlock_with_fingerprint() -> None:
    print("👆 Bitte Fingerabdruck auflegen …")
    try:
        out = subprocess.run(["termux-fingerprint"], capture_output=True, text=True,
                             timeout=30).stdout
        result = json.loads(out).get("auth_result", "")
    except Exception as exc:  # noqa: BLE001
        print(f"Fingerabdruck nicht möglich: {exc}")
        return
    if result == "AUTH_RESULT_SUCCESS":
        sentry.send_unlock()
        print("✅ Fingerabdruck ok – Entsperr-Signal an den PC gesendet.")
    else:
        print(f"❌ Nicht authentifiziert ({result}).")


def main() -> None:
    if len(sys.argv) > 1 and sys.argv[1] == "unlock":
        unlock_with_fingerprint()
    else:
        listen_alarms()


if __name__ == "__main__":
    main()
