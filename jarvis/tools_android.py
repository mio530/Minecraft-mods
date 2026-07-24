"""
Android-/Termux-spezifische Werkzeuge für Jarvis (ohne KI-Abhängigkeit).

Enthält auch die Sprachein-/ausgabe über termux-api (speak/listen),
da diese die gleichen Termux-Aufrufe nutzt.
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import time

from tools_base import Tool
from tools_common import common_tools
from tools_camera import describe_image
from tools_face import enroll_owner, recognize_against_owner


HAVE_TERMUX_API = shutil.which("termux-battery-status") is not None


def _tapi(args: list[str], stdin: str | None = None, timeout: int = 30) -> str:
    proc = subprocess.run(
        args, input=stdin, capture_output=True, text=True, timeout=timeout
    )
    return (proc.stdout or proc.stderr or "").strip()


# -- Werkzeug-Handler --------------------------------------------------------
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
    _tapi(["termux-sms-send", "-n", number], stdin=params["message"])
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
    _tapi(["termux-notification", "--title", title, "--content", params["message"]])
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


def _photo_path() -> str:
    d = os.path.expanduser("~/.jarvis/photos")
    os.makedirs(d, exist_ok=True)
    return os.path.join(d, f"foto_{time.strftime('%Y%m%d_%H%M%S')}.jpg")


def _take_photo(params: dict) -> str:
    path = params.get("path") or _photo_path()
    cam = str(params.get("camera", 0))  # 0 = Rückseite, 1 = Frontkamera
    _tapi(["termux-camera-photo", "-c", cam, os.path.expanduser(path)], timeout=30)
    return f"Foto aufgenommen: {os.path.expanduser(path)}"


def _look(params: dict) -> str:
    path = _photo_path()
    cam = str(params.get("camera", 0))
    _tapi(["termux-camera-photo", "-c", cam, path], timeout=30)
    if not os.path.exists(path):
        return "Kein Foto von der Kamera erhalten."
    prompt = params.get("prompt", "Beschreibe kurz und präzise, was auf diesem Bild zu sehen ist.")
    return describe_image(path, prompt)


def _enroll_face(params: dict) -> str:
    path = _photo_path()
    cam = str(params.get("camera", 1))  # 1 = Frontkamera zum Anlernen
    _tapi(["termux-camera-photo", "-c", cam, path], timeout=30)
    if not os.path.exists(path):
        return "Kein Foto von der Kamera erhalten."
    return enroll_owner(path, params.get("name", ""))


def _recognize_me(params: dict) -> str:
    path = _photo_path()
    cam = str(params.get("camera", 1))
    _tapi(["termux-camera-photo", "-c", cam, path], timeout=30)
    if not os.path.exists(path):
        return "Kein Foto von der Kamera erhalten."
    return recognize_against_owner(path)


def _learn_movement(params: dict) -> str:
    import sentry
    cam = str(params.get("camera", 1))

    def cap(p):
        _tapi(["termux-camera-photo", "-c", cam, os.path.expanduser(p)], timeout=30)
        return None if os.path.exists(os.path.expanduser(p)) else "kein Bild"

    return sentry.enroll_motion(cap)


def android_tools() -> list[Tool]:
    if not HAVE_TERMUX_API:
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
        Tool("take_photo", "Nimmt ein Foto mit der Handy-Kamera auf (camera 0=hinten, 1=vorne).",
             {"type": "object",
              "properties": {"camera": {"type": "integer"}, "path": {"type": "string"}},
              "required": []}, _take_photo, dangerous=True),
        Tool("look", "Schaut durch die Handy-Kamera und beschreibt, was zu sehen ist.",
             {"type": "object",
              "properties": {"camera": {"type": "integer"}, "prompt": {"type": "string"}},
              "required": []}, _look, dangerous=True),
        Tool("enroll_face", "Lernt das Gesicht des Nutzers an (Frontkamera). Optional 'name'.",
             {"type": "object",
              "properties": {"name": {"type": "string"}, "camera": {"type": "integer"}},
              "required": []}, _enroll_face, dangerous=True),
        Tool("recognize_me", "Prüft per Frontkamera, ob es der bekannte Nutzer ist.",
             {"type": "object", "properties": {"camera": {"type": "integer"}},
              "required": []}, _recognize_me, dangerous=True),
        Tool("learn_movement", "Lernt an, wie sich der Nutzer bewegt (genauere Erkennung).",
             {"type": "object", "properties": {"camera": {"type": "integer"}},
              "required": []}, _learn_movement, dangerous=True),
    ]


# -- Sprachein-/ausgabe über Termux:API --------------------------------------
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
