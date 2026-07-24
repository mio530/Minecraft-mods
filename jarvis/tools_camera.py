"""
JARVIS – Kamera-Werkzeuge (Desktop: Windows / Linux / macOS)
============================================================

- take_photo : nimmt ein Foto mit der Webcam auf und speichert es
- look       : nimmt ein Foto auf und BESCHREIBT, was darauf zu sehen ist
               (Jarvis "sieht" durch die Kamera) – über ein Vision-Modell

Desktop-Aufnahme über OpenCV (pip install opencv-python). Fehlt es, gibt das
Werkzeug einen hilfreichen Hinweis zurück, statt abzustürzen.

Die Bildbeschreibung (look) nutzt automatisch das verfügbare Modell:
  • Claude (wenn ANTHROPIC_API_KEY gesetzt) – sehr gut
  • Groq  (GROQ_API_KEY, Vision-Modell)     – kostenlos, Cloud
  • Ollama (lokales Vision-Modell wie 'llava') – kostenlos, offline
Modell überschreibbar per Umgebungsvariable JARVIS_VISION_MODEL.
"""

from __future__ import annotations

import base64
import os
import time
from pathlib import Path

from tools_base import Tool


def _photo_dir() -> Path:
    d = Path.home() / ".jarvis" / "photos"
    d.mkdir(parents=True, exist_ok=True)
    return d


def _capture(camera: int, path: str) -> str | None:
    """Nimmt ein Bild auf und speichert es. Gibt Fehlertext zurück oder None bei Erfolg."""
    try:
        import cv2
    except ImportError:
        return "Kamera-Zugriff braucht OpenCV:  pip install opencv-python"
    cap = cv2.VideoCapture(camera)
    if not cap.isOpened():
        cap.release()
        return f"Kamera {camera} konnte nicht geöffnet werden."
    for _ in range(5):  # ein paar Frames zum "Aufwärmen" (Belichtung)
        cap.read()
    ok, frame = cap.read()
    cap.release()
    if not ok:
        return "Kein Bild von der Kamera erhalten."
    cv2.imwrite(os.path.expanduser(path), frame)
    return None


# ----------------------------------------------------------------------------
# Bildbeschreibung – "Jarvis sieht"
# ----------------------------------------------------------------------------
def describe_image(path: str, prompt: str = "Beschreibe kurz und präzise, was auf diesem Bild zu sehen ist.") -> str:
    path = os.path.expanduser(path)
    try:
        b64 = base64.b64encode(open(path, "rb").read()).decode()
    except Exception as exc:  # noqa: BLE001
        return f"Bild konnte nicht gelesen werden: {exc}"

    # 1) Claude-Vision
    if os.environ.get("ANTHROPIC_API_KEY"):
        try:
            import anthropic
            c = anthropic.Anthropic()
            msg = c.messages.create(
                model="claude-opus-4-8", max_tokens=512,
                messages=[{"role": "user", "content": [
                    {"type": "image", "source": {"type": "base64",
                     "media_type": "image/jpeg", "data": b64}},
                    {"type": "text", "text": prompt},
                ]}],
            )
            return "".join(b.text for b in msg.content
                           if getattr(b, "type", None) == "text").strip()
        except Exception as exc:  # noqa: BLE001
            return f"(Claude-Vision-Fehler: {exc}) Bild gespeichert: {path}"

    # 2) OpenAI-kompatibel (Groq oder Ollama)
    model = os.environ.get("JARVIS_VISION_MODEL")
    if os.environ.get("GROQ_API_KEY"):
        base = "https://api.groq.com/openai/v1"
        key = os.environ["GROQ_API_KEY"]
        model = model or "llama-3.2-11b-vision-preview"
    else:
        base = os.environ.get("OLLAMA_HOST", "http://localhost:11434") + "/v1"
        key = "ollama"
        model = model or "llava"
    try:
        from openai import OpenAI
        client = OpenAI(base_url=base, api_key=key)
        r = client.chat.completions.create(
            model=model, max_tokens=512,
            messages=[{"role": "user", "content": [
                {"type": "text", "text": prompt},
                {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64}"}},
            ]}],
        )
        return (r.choices[0].message.content or "").strip()
    except Exception as exc:  # noqa: BLE001
        return (f"(Bildbeschreibung nicht verfügbar: {exc}). Das Foto ist gespeichert "
                f"unter: {path}. Für 'sehen' ein Vision-Modell einrichten "
                f"(z.B. 'ollama pull llava' oder ANTHROPIC_API_KEY setzen).")


# ----------------------------------------------------------------------------
# Handler
# ----------------------------------------------------------------------------
def _take_photo(params: dict) -> str:
    path = params.get("path") or str(
        _photo_dir() / f"foto_{time.strftime('%Y%m%d_%H%M%S')}.jpg")
    err = _capture(int(params.get("camera", 0)), path)
    if err:
        return err
    return f"Foto aufgenommen und gespeichert: {os.path.expanduser(path)}"


def _look(params: dict) -> str:
    tmp = str(_photo_dir() / f"look_{time.strftime('%Y%m%d_%H%M%S')}.jpg")
    err = _capture(int(params.get("camera", 0)), tmp)
    if err:
        return err
    prompt = params.get("prompt", "Beschreibe kurz und präzise, was auf diesem Bild zu sehen ist.")
    return describe_image(tmp, prompt)


def camera_tools() -> list[Tool]:
    return [
        Tool(
            name="take_photo",
            description=(
                "Nimmt ein Foto mit der Webcam auf und speichert es. Optional: "
                "camera (Index, Standard 0) und path (Speicherort)."
            ),
            input_schema={
                "type": "object",
                "properties": {
                    "camera": {"type": "integer"},
                    "path": {"type": "string"},
                },
                "required": [],
            },
            handler=_take_photo,
            dangerous=True,  # Kamera = Privatsphäre -> nachfragen
        ),
        Tool(
            name="look",
            description=(
                "Schaut durch die Kamera und beschreibt, was zu sehen ist "
                "(nimmt ein Foto auf und analysiert es mit einem Vision-Modell). "
                "Nutze das, wenn der Nutzer fragt 'was siehst du', 'schau mal' o.ä. "
                "Optional 'prompt' für eine gezielte Frage zum Bild."
            ),
            input_schema={
                "type": "object",
                "properties": {
                    "camera": {"type": "integer"},
                    "prompt": {"type": "string"},
                },
                "required": [],
            },
            handler=_look,
            dangerous=True,
        ),
    ]
