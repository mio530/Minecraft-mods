"""Linux-spezifische Werkzeuge für Jarvis (ohne KI-Abhängigkeit)."""

from __future__ import annotations

import shutil
import subprocess

from tools_base import Tool
from tools_common import common_tools
from tools_camera import camera_tools


def _open_app(params: dict) -> str:
    target = params["target"]
    if shutil.which("xdg-open") and ("://" in target or "/" in target or "." in target):
        subprocess.Popen(["xdg-open", target])
        return f"Öffne '{target}' mit der Standardanwendung."
    subprocess.Popen(target, shell=True)
    return f"Starte '{target}'."


def _notify(params: dict) -> str:
    title = params.get("title", "Jarvis")
    message = params["message"]
    if shutil.which("notify-send"):
        subprocess.run(["notify-send", title, message])
        return "Benachrichtigung angezeigt."
    return f"(notify-send fehlt) {title}: {message}"


def _set_volume(params: dict) -> str:
    percent = max(0, min(150, int(params["percent"])))
    if shutil.which("pactl"):
        subprocess.run(["pactl", "set-sink-volume", "@DEFAULT_SINK@", f"{percent}%"])
        return f"Lautstärke auf {percent}% gesetzt."
    if shutil.which("amixer"):
        subprocess.run(["amixer", "set", "Master", f"{percent}%"])
        return f"Lautstärke auf {percent}% gesetzt."
    return "Kein Lautstärke-Tool (pactl/amixer) gefunden."


def linux_tools() -> list[Tool]:
    return common_tools() + camera_tools() + [
        Tool(
            name="open_app",
            description=(
                "Öffnet ein Programm, eine Datei oder eine URL auf dem Linux-Desktop. "
                "Beispiele: 'firefox', 'https://youtube.com', '/home/user/bild.png'."
            ),
            input_schema={
                "type": "object",
                "properties": {"target": {"type": "string"}},
                "required": ["target"],
            },
            handler=_open_app,
        ),
        Tool(
            name="notify",
            description="Zeigt eine Desktop-Benachrichtigung an.",
            input_schema={
                "type": "object",
                "properties": {
                    "title": {"type": "string"},
                    "message": {"type": "string"},
                },
                "required": ["message"],
            },
            handler=_notify,
        ),
        Tool(
            name="set_volume",
            description="Stellt die System-Lautstärke ein (0–150 Prozent).",
            input_schema={
                "type": "object",
                "properties": {"percent": {"type": "integer"}},
                "required": ["percent"],
            },
            handler=_set_volume,
        ),
    ]
