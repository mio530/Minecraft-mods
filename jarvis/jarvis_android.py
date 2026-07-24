#!/usr/bin/env python3
"""
JARVIS für ANDROID (via Termux)
===============================

Android erlaubt keinen freien Systemzugriff wie ein PC. Der praktikable,
ECHTE Weg ist Termux + Termux:API – damit greift Jarvis auf echte
Gerätefunktionen zu: Akku, SMS, Standort, Taschenlampe, Vibration,
Zwischenablage, Benachrichtigungen, Sprachein-/ausgabe, App starten.

EINRICHTUNG (einmalig):
  1. Installiere "Termux" UND "Termux:API" aus F-Droid (nicht Play Store,
     die Play-Version ist veraltet).
  2. In Termux ausführen:
        pkg update && pkg upgrade
        pkg install python termux-api
        pip install anthropic
        termux-setup-storage        # für Dateizugriff
  3. API-Key setzen:
        export ANTHROPIC_API_KEY="sk-ant-..."
  4. Starten:
        python jarvis_android.py

Sprachausgabe = termux-tts-speak, Spracheingabe = termux-speech-to-text.
Fehlt Termux:API, läuft Jarvis im Textmodus weiter.
"""

from __future__ import annotations

import json
import shutil
import subprocess

from jarvis_core import Jarvis, Tool
from tools_common import common_tools


HAVE_TERMUX_API = shutil.which("termux-battery-status") is not None


def _tapi(args: list[str], stdin: str | None = None, timeout: int = 30) -> str:
    """Ruft einen termux-api-Befehl auf und gibt die Ausgabe zurück."""
    proc = subprocess.run(
        args, input=stdin, capture_output=True, text=True, timeout=timeout
    )
    return (proc.stdout or proc.stderr or "").strip()


# ----------------------------------------------------------------------------
# Android-/Termux-Werkzeuge
# ----------------------------------------------------------------------------
def _battery(_params: dict) -> str:
    raw = _tapi(["termux-battery-status"])
    try:
        b = json.loads(raw)
        return (f"Akku: {b.get('percentage')}%, Status: {b.get('status')}, "
                f"Temperatur: {b.get('temperature')}°C")
    except Exception:
        return raw or "Akku-Status nicht verfügbar."


def _location(_params: dict) -> str:
    raw = _tapi(["termux-location", "-p", "network"], timeout=45)
    try:
        loc = json.loads(raw)
        return (f"Standort: {loc.get('latitude')}, {loc.get('longitude')} "
                f"(±{loc.get('accuracy')} m)")
    except Exception:
        return raw or "Standort nicht verfügbar."


def _send_sms(params: dict) -> str:
    number = params["number"]
    message = params["message"]
    _tapi(["termux-sms-send", "-n", number], stdin=message)
    return f"SMS an {number} gesendet."


def _make_call(params: dict) -> str:
    number = params["number"]
    _tapi(["termux-telephony-call", number])
    return f"Rufe {number} an."


def _torch(params: dict) -> str:
    on = bool(params.get("on", True))
    _tapi(["termux-torch", "on" if on else "off"])
    return "Taschenlampe an." if on else "Taschenlampe aus."


def _vibrate(params: dict) -> str:
    ms = int(params.get("duration_ms", 500))
    _tapi(["termux-vibrate", "-d", str(ms)])
    return f"Vibriere {ms} ms."


def _notify(params: dict) -> str:
    title = params.get("title", "Jarvis")
    content = params["message"]
    _tapi(["termux-notification", "--title", title, "--content", content])
    return "Benachrichtigung angezeigt."


def _clipboard_get(_params: dict) -> str:
    return _tapi(["termux-clipboard-get"]) or "(Zwischenablage leer)"


def _clipboard_set(params: dict) -> str:
    _tapi(["termux-clipboard-set"], stdin=params["text"])
    return "In die Zwischenablage kopiert."


def _open_url(params: dict) -> str:
    url = params["url"]
    _tapi(["termux-open-url", url])
    return f"Öffne {url}."


def _contacts(_params: dict) -> str:
    raw = _tapi(["termux-contact-list"])
    try:
        contacts = json.loads(raw)
        lines = [f"{c.get('name')}: {c.get('number')}" for c in contacts[:50]]
        return "\n".join(lines) or "(keine Kontakte)"
    except Exception:
        return raw or "Kontakte nicht verfügbar."


def android_tools() -> list[Tool]:
    if not HAVE_TERMUX_API:
        # Ohne Termux:API bleiben die plattformneutralen Werkzeuge (Shell etc.)
        return common_tools()

    return common_tools() + [
        Tool("battery", "Gibt Akkustand, Ladezustand und Temperatur des Handys zurück.",
             {"type": "object", "properties": {}, "required": []}, _battery),
        Tool("location", "Ermittelt den aktuellen GPS-/Netzwerk-Standort des Handys.",
             {"type": "object", "properties": {}, "required": []}, _location),
        Tool("send_sms", "Sendet eine SMS an eine Telefonnummer.",
             {"type": "object",
              "properties": {"number": {"type": "string"}, "message": {"type": "string"}},
              "required": ["number", "message"]}, _send_sms, dangerous=True),
        Tool("make_call", "Startet einen Telefonanruf an eine Nummer.",
             {"type": "object", "properties": {"number": {"type": "string"}},
              "required": ["number"]}, _make_call, dangerous=True),
        Tool("torch", "Schaltet die Taschenlampe an oder aus (on=true/false).",
             {"type": "object", "properties": {"on": {"type": "boolean"}}, "required": []},
             _torch),
        Tool("vibrate", "Lässt das Handy vibrieren (duration_ms).",
             {"type": "object", "properties": {"duration_ms": {"type": "integer"}},
              "required": []}, _vibrate),
        Tool("notify", "Zeigt eine Handy-Benachrichtigung an.",
             {"type": "object",
              "properties": {"title": {"type": "string"}, "message": {"type": "string"}},
              "required": ["message"]}, _notify),
        Tool("clipboard_get", "Liest die Zwischenablage.",
             {"type": "object", "properties": {}, "required": []}, _clipboard_get),
        Tool("clipboard_set", "Schreibt Text in die Zwischenablage.",
             {"type": "object", "properties": {"text": {"type": "string"}},
              "required": ["text"]}, _clipboard_set),
        Tool("open_url", "Öffnet eine URL im Browser des Handys.",
             {"type": "object", "properties": {"url": {"type": "string"}},
              "required": ["url"]}, _open_url),
        Tool("contacts", "Liest die Kontaktliste des Handys (Name + Nummer).",
             {"type": "object", "properties": {}, "required": []}, _contacts, dangerous=True),
    ]


# ----------------------------------------------------------------------------
# Sprachein-/ausgabe über Termux:API
# ----------------------------------------------------------------------------
def speak(text: str) -> None:
    print(f"\nJARVIS: {text}\n")
    if HAVE_TERMUX_API:
        try:
            subprocess.run(["termux-tts-speak", "-l", "de", text], timeout=60)
        except Exception:
            pass


def listen() -> str:
    if HAVE_TERMUX_API:
        try:
            out = _tapi(["termux-speech-to-text"], timeout=30)
            if out:
                print(f"Du (gehört): {out}")
                return out.strip()
        except Exception:
            pass
    return input("Du: ").strip()


# ----------------------------------------------------------------------------
# Bestätigung gefährlicher Aktionen
# ----------------------------------------------------------------------------
def confirm(tool: Tool, params: dict) -> bool:
    print(f"\n⚠️  Jarvis möchte '{tool.name}' ausführen:")
    for k, v in params.items():
        print(f"     {k} = {v}")
    answer = input("     Erlauben? [j/N] ").strip().lower()
    return answer in ("j", "ja", "y", "yes")


def main() -> None:
    jarvis = Jarvis(
        tools=android_tools(),
        platform_name="Android (Termux)",
        confirm=confirm,
        on_status=lambda msg: print(f"   … {msg}"),
    )

    print("=" * 60)
    print("  J.A.R.V.I.S.  –  Android / Termux")
    print("  Sprich oder tippe. 'exit' zum Beenden, 'reset' für neuen Kontext.")
    print(f"  Termux:API: {'gefunden' if HAVE_TERMUX_API else 'NICHT gefunden (Textmodus)'}")
    print("=" * 60)
    speak("Systeme online. Guten Tag, Sir.")

    while True:
        try:
            user = listen()
        except (KeyboardInterrupt, EOFError):
            break
        if not user:
            continue
        low = user.lower().strip()
        if low in ("exit", "quit", "beenden", "tschüss"):
            speak("Bis später, Sir.")
            break
        if low == "reset":
            jarvis.reset()
            speak("Kontext gelöscht.")
            continue
        answer = jarvis.ask(user)
        speak(answer)


if __name__ == "__main__":
    main()
