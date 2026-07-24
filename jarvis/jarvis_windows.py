#!/usr/bin/env python3
"""
JARVIS für WINDOWS  (Claude-Version, kostenpflichtig)
=====================================================

Für die KOSTENLOSE Variante siehe jarvis_free.py (Ollama/Groq).

Starten (PowerShell / CMD):
    set ANTHROPIC_API_KEY=sk-ant-...
    python jarvis_windows.py

Braucht:  pip install anthropic
Optional (Sprache):  pip install SpeechRecognition pyttsx3 pyaudio comtypes
"""

from __future__ import annotations

from jarvis_core import Jarvis, Tool
from tools_windows import windows_tools
from tools_power import is_unrestricted
from memory import Memory
from voice import Voice


def confirm(tool: Tool, params: dict) -> bool:
    if is_unrestricted():
        return True  # Vollzugriff-Modus: ohne Rückfrage
    print(f"\n⚠️  Jarvis möchte '{tool.name}' ausführen:")
    for k, v in params.items():
        print(f"     {k} = {v}")
    return input("     Erlauben? [j/N] ").strip().lower() in ("j", "ja", "y", "yes")


def main() -> None:
    voice = Voice(wake_language="de-DE")
    jarvis = Jarvis(
        tools=windows_tools(),
        platform_name="Windows PC",
        memory=Memory(),
        confirm=confirm,
        on_status=lambda msg: print(f"   … {msg}"),
    )

    print("=" * 60)
    if is_unrestricted():
        print("  🔓 VOLLZUGRIFF AKTIV – Jarvis handelt OHNE Rückfragen!")
    print("  J.A.R.V.I.S.  –  Windows (Claude)")
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
