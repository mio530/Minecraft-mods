#!/usr/bin/env python3
"""
JARVIS für LINUX
================

Starten:
    export ANTHROPIC_API_KEY="sk-ant-..."
    python3 jarvis_linux.py

Braucht (siehe requirements.txt):
    pip install anthropic
    # optional für Sprache:
    pip install SpeechRecognition pyttsx3 pyaudio
    # System-Tools (Debian/Ubuntu): sudo apt install espeak xdg-utils libnotify-bin

Jarvis kann auf diesem PC: Programme öffnen, Befehle ausführen, Dateien lesen/
schreiben, Benachrichtigungen zeigen, Fenster/Prozesse steuern, ins Web greifen.
"""

from __future__ import annotations

import shutil
import subprocess

from jarvis_core import Jarvis, Tool
from tools_common import common_tools
from voice import Voice


# ----------------------------------------------------------------------------
# Linux-spezifische Werkzeuge
# ----------------------------------------------------------------------------
def _open_app(params: dict) -> str:
    target = params["target"]
    # xdg-open öffnet Dateien/URLs mit der Standard-App;
    # sonst als Programmname starten.
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
    percent = int(params["percent"])
    percent = max(0, min(150, percent))
    if shutil.which("pactl"):
        subprocess.run(["pactl", "set-sink-volume", "@DEFAULT_SINK@", f"{percent}%"])
        return f"Lautstärke auf {percent}% gesetzt."
    if shutil.which("amixer"):
        subprocess.run(["amixer", "set", "Master", f"{percent}%"])
        return f"Lautstärke auf {percent}% gesetzt."
    return "Kein Lautstärke-Tool (pactl/amixer) gefunden."


def linux_tools() -> list[Tool]:
    return common_tools() + [
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
    voice = Voice(wake_language="de-DE")
    jarvis = Jarvis(
        tools=linux_tools(),
        platform_name="Linux Desktop",
        confirm=confirm,
        on_status=lambda msg: print(f"   … {msg}"),
    )

    print("=" * 60)
    print("  J.A.R.V.I.S.  –  Linux")
    print("  Sprich oder tippe. 'exit' zum Beenden, 'reset' für neuen Kontext.")
    print(f"  Sprachausgabe: {'an' if voice.voice_output else 'aus'} | "
          f"Spracheingabe: {'an' if voice.voice_input else 'aus'}")
    print("=" * 60)
    voice.say("Systeme online. Guten Tag, Sir. Wie kann ich behilflich sein?")

    while True:
        try:
            user = voice.listen()
        except (KeyboardInterrupt, EOFError):
            break
        if not user:
            continue
        low = user.lower().strip()
        if low in ("exit", "quit", "beenden", "tschüss"):
            voice.say("Bis später, Sir.")
            break
        if low == "reset":
            jarvis.reset()
            voice.say("Kontext gelöscht.")
            continue
        answer = jarvis.ask(user)
        voice.say(answer)


if __name__ == "__main__":
    main()
