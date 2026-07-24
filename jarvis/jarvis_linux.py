#!/usr/bin/env python3
"""
JARVIS für LINUX  (Claude-Version, kostenpflichtig)
===================================================

Für die KOSTENLOSE Variante siehe jarvis_free.py (Ollama/Groq).

Starten:
    export ANTHROPIC_API_KEY="sk-ant-..."
    python3 jarvis_linux.py

Braucht:  pip install anthropic
Optional (Sprache):  pip install SpeechRecognition pyttsx3 pyaudio
System-Tools (Debian/Ubuntu): sudo apt install espeak xdg-utils libnotify-bin
"""

from __future__ import annotations

from jarvis_core import Jarvis, Tool
from tools_linux import linux_tools
from memory import Memory
from voice import Voice


def confirm(tool: Tool, params: dict) -> bool:
    print(f"\n⚠️  Jarvis möchte '{tool.name}' ausführen:")
    for k, v in params.items():
        print(f"     {k} = {v}")
    return input("     Erlauben? [j/N] ").strip().lower() in ("j", "ja", "y", "yes")


def main() -> None:
    voice = Voice(wake_language="de-DE")
    jarvis = Jarvis(
        tools=linux_tools(),
        platform_name="Linux Desktop",
        memory=Memory(),
        confirm=confirm,
        on_status=lambda msg: print(f"   … {msg}"),
    )

    print("=" * 60)
    print("  J.A.R.V.I.S.  –  Linux (Claude)")
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
        voice.say(jarvis.ask(user))


if __name__ == "__main__":
    main()
