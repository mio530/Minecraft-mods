"""
JARVIS – Gesichtserkennung ("Jarvis erkennt DICH")
==================================================

Zwei Schritte:
  1. enroll_face : Du lernst dein Gesicht an -> ein Referenzbild wird gespeichert
                   (in ~/.jarvis/face/owner.jpg) samt deinem Namen.
  2. recognize_me: Jarvis nimmt ein Live-Foto auf und vergleicht es mit dem
                   Referenzbild über ein Vision-Modell -> "Ja, das bist du, <Name>"
                   oder "Nein, das ist jemand anderes".

Funktioniert plattformübergreifend, weil die eigentliche Aufnahme von der
Plattform kommt (Desktop: OpenCV, Android: Termux) und der Vergleich das
gemeinsame Vision-Backend (Claude / Groq / Ollama-llava) nutzt.
"""

from __future__ import annotations

import os
import time
from pathlib import Path

from tools_base import Tool
from tools_camera import capture, vision_query


def _face_dir() -> Path:
    d = Path.home() / ".jarvis" / "face"
    d.mkdir(parents=True, exist_ok=True)
    return d


def _owner_img() -> Path:
    return _face_dir() / "owner.jpg"


def _owner_name_file() -> Path:
    return _face_dir() / "owner.txt"


def owner_name() -> str:
    f = _owner_name_file()
    return f.read_text(encoding="utf-8").strip() if f.exists() else ""


def is_enrolled() -> bool:
    return _owner_img().exists()


# ----------------------------------------------------------------------------
# Plattform-neutrale Kernfunktionen (von Desktop UND Android genutzt)
# ----------------------------------------------------------------------------
def enroll_owner(src_path: str, name: str = "") -> str:
    """Speichert das Referenzbild (und optional den Namen) des Besitzers."""
    import shutil
    shutil.copy(os.path.expanduser(src_path), _owner_img())
    if name:
        _owner_name_file().write_text(name.strip(), encoding="utf-8")
    who = f" für {name}" if name else ""
    return (f"Gesicht angelernt{who}. Ich erkenne dich ab jetzt wieder. "
            f"(Referenzbild: {_owner_img()})")


def recognize_against_owner(new_path: str) -> str:
    """Vergleicht new_path mit dem gespeicherten Besitzer-Gesicht."""
    if not is_enrolled():
        return ("Ich habe noch kein Gesicht gespeichert. Sag 'Merk dir mein "
                "Gesicht', dann lerne ich dich an.")
    name = owner_name() or "der Besitzer"
    prompt = (
        f"Bild 1 ist das Referenzfoto von {name}. Bild 2 ist eine gerade "
        f"aufgenommene Live-Aufnahme. Zeigen beide Bilder DIESELBE Person? "
        f"Antworte kurz auf Deutsch: Wenn ja: 'Ja, das bist du, {name}.' Wenn "
        f"nein: 'Nein, das ist nicht {name}.' Gib eine kurze Begründung "
        f"(z.B. Merkmale). Sei bei Unsicherheit ehrlich."
    )
    return vision_query([str(_owner_img()), os.path.expanduser(new_path)], prompt)


# ----------------------------------------------------------------------------
# Desktop-Werkzeuge (Aufnahme via OpenCV)
# ----------------------------------------------------------------------------
def _enroll_face(params: dict) -> str:
    tmp = str(_face_dir() / "enroll_tmp.jpg")
    err = capture(int(params.get("camera", 0)), tmp)
    if err:
        return err
    return enroll_owner(tmp, params.get("name", ""))


def _recognize_me(params: dict) -> str:
    tmp = str(_face_dir() / f"probe_{time.strftime('%H%M%S')}.jpg")
    err = capture(int(params.get("camera", 0)), tmp)
    if err:
        return err
    return recognize_against_owner(tmp)


def face_tools() -> list[Tool]:
    return [
        Tool(
            name="enroll_face",
            description=(
                "Lernt das Gesicht des Nutzers an (nimmt ein Referenzfoto auf). "
                "Nutze das, wenn der Nutzer sagt 'merk dir mein Gesicht' o.ä. "
                "Optional 'name' des Nutzers."
            ),
            input_schema={
                "type": "object",
                "properties": {"name": {"type": "string"}, "camera": {"type": "integer"}},
                "required": [],
            },
            handler=_enroll_face,
            dangerous=True,  # Kamera
        ),
        Tool(
            name="recognize_me",
            description=(
                "Nimmt ein Live-Foto auf und prüft, ob es der bekannte Nutzer ist "
                "(Gesichtsvergleich). Nutze das bei 'erkennst du mich', 'wer bin ich', "
                "'bin ich das'."
            ),
            input_schema={
                "type": "object",
                "properties": {"camera": {"type": "integer"}},
                "required": [],
            },
            handler=_recognize_me,
            dangerous=True,
        ),
    ]
