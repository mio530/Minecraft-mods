#!/usr/bin/env python3
"""
JARVIS – KOSTENLOSE Version (ein Launcher für alle Plattformen)
===============================================================

Nutzt statt Claude eine gratis KI:
  • OLLAMA  – lokal auf deinem Gerät (offline, 100% kostenlos)
  • GROQ    – kostenloser Cloud-Tarif (nur Anmeldung, kein lokales Modell)
  • OFFLINE – Notfallmodus ganz ohne KI (nur einfache Kommandos)

Diese Datei erkennt automatisch dein Betriebssystem (Windows / Linux / Android)
und lädt die passenden Werkzeuge.

--- SCHNELLSTART mit Ollama (empfohlen für PC) --------------------------------
  1. Ollama installieren:  https://ollama.com
  2. Ein Modell laden:     ollama pull llama3.1
  3. pip install openai
  4. python jarvis_free.py

--- SCHNELLSTART mit Groq (empfohlen fürs Handy / schwache PCs) ---------------
  1. Kostenlosen Key holen: https://console.groq.com
  2. pip install openai
  3. Linux/Termux:  export GROQ_API_KEY="gsk_..."
     Windows:       set GROQ_API_KEY=gsk_...
  4. python jarvis_free.py
"""

from __future__ import annotations

import os
import platform

from tools_base import Tool
from jarvis_free_core import JarvisFree, detect_backend
from memory import Memory


# ----------------------------------------------------------------------------
# Plattform erkennen und passende Werkzeuge + Sprache laden
# ----------------------------------------------------------------------------
def load_platform():
    """Gibt (tools, speak, listen, platform_label) zurück."""
    system = platform.system().lower()
    is_android = "ANDROID_ROOT" in os.environ or "com.termux" in os.environ.get("PREFIX", "")

    if is_android:
        from tools_android import android_tools, speak, listen
        return android_tools(), speak, listen, "Android (Termux)"

    if system == "windows":
        from tools_windows import windows_tools
        from voice import Voice
        voice = Voice(wake_language="de-DE")
        return windows_tools(), voice.say, voice.listen, "Windows PC"

    # Standard: Linux / macOS
    from tools_linux import linux_tools
    from voice import Voice
    voice = Voice(wake_language="de-DE")
    return linux_tools(), voice.say, voice.listen, "Linux Desktop"


def confirm(tool: Tool, params: dict) -> bool:
    print(f"\n⚠️  Jarvis möchte '{tool.name}' ausführen:")
    for k, v in params.items():
        print(f"     {k} = {v}")
    return input("     Erlauben? [j/N] ").strip().lower() in ("j", "ja", "y", "yes")


BACKEND_INFO = {
    "ollama": "Ollama (lokal, kostenlos)",
    "groq": "Groq (Cloud, kostenloser Tarif)",
    "offline": "Offline-Notfallmodus (keine KI – nur einfache Kommandos)",
}


def main() -> None:
    tools, speak, listen, label = load_platform()
    backend = detect_backend()

    jarvis = JarvisFree(
        tools=tools,
        backend=backend,
        platform_name=label,
        memory=Memory(),
        confirm=confirm,
        on_status=lambda msg: print(f"   … {msg}"),
    )

    print("=" * 60)
    print("  J.A.R.V.I.S.  –  KOSTENLOSE Version")
    print(f"  Plattform: {label}")
    print(f"  KI-Backend: {BACKEND_INFO.get(backend, backend)}")
    print("  Sprich oder tippe. 'exit' zum Beenden, 'reset' für neuen Kontext.")
    print("=" * 60)

    if backend == "offline":
        print("  ℹ️  Kein KI-Backend gefunden. Starte Ollama (ollama pull llama3.1)")
        print("      oder setze einen GROQ_API_KEY für echtes Sprachverständnis.\n")

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
