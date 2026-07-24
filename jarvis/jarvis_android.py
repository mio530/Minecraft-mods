#!/usr/bin/env python3
"""
JARVIS für ANDROID via Termux  (Claude-Version, kostenpflichtig)
================================================================

Für die KOSTENLOSE Variante siehe jarvis_free.py (Groq empfohlen am Handy).

EINRICHTUNG (einmalig):
  1. Installiere "Termux" UND "Termux:API" aus F-Droid (nicht Play Store).
  2. In Termux:
        pkg update && pkg upgrade
        pkg install python termux-api
        pip install anthropic
        termux-setup-storage
  3. export ANTHROPIC_API_KEY="sk-ant-..."
  4. python jarvis_android.py
"""

from __future__ import annotations

from jarvis_core import Jarvis, Tool
from tools_android import android_tools, speak, listen, HAVE_TERMUX_API
from tools_power import is_unrestricted
from memory import Memory


def confirm(tool: Tool, params: dict) -> bool:
    print(f"\n⚠️  Jarvis möchte '{tool.name}' ausführen:")
    for k, v in params.items():
        print(f"     {k} = {v}")
    return input("     Erlauben? [j/N] ").strip().lower() in ("j", "ja", "y", "yes")


def main() -> None:
    jarvis = Jarvis(
        tools=android_tools(),
        platform_name="Android (Termux)",
        memory=Memory(),
        confirm=confirm,
        on_status=lambda msg: print(f"   … {msg}"),
    )

    print("=" * 60)
    if is_unrestricted():
        print("  🔓 VOLLZUGRIFF AKTIV – Jarvis handelt OHNE Rückfragen!")
    print("  J.A.R.V.I.S.  –  Android / Termux (Claude)")
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
        speak(jarvis.ask(user))


if __name__ == "__main__":
    main()
