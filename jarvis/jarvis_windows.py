#!/usr/bin/env python3
"""
JARVIS für WINDOWS
==================

Starten (PowerShell / CMD):
    set ANTHROPIC_API_KEY=sk-ant-...
    python jarvis_windows.py

Braucht (siehe requirements.txt):
    pip install anthropic
    # optional für Sprache:
    pip install SpeechRecognition pyttsx3 pyaudio comtypes
    (pyttsx3 nutzt unter Windows die eingebaute SAPI5-Sprachausgabe – kein
     extra Programm nötig.)

Jarvis kann auf diesem PC: Programme öffnen, PowerShell/CMD-Befehle ausführen,
Dateien lesen/schreiben, Benachrichtigungen zeigen, Lautstärke steuern,
herunterfahren/sperren, ins Web greifen.
"""

from __future__ import annotations

import subprocess

from jarvis_core import Jarvis, Tool
from tools_common import common_tools
from voice import Voice


# ----------------------------------------------------------------------------
# Windows-spezifische Werkzeuge
# ----------------------------------------------------------------------------
def _open_app(params: dict) -> str:
    target = params["target"]
    # 'start' öffnet Programme, Dateien und URLs mit der Standard-App.
    subprocess.Popen(f'start "" "{target}"', shell=True)
    return f"Öffne '{target}'."


def _powershell(params: dict) -> str:
    script = params["script"]
    proc = subprocess.run(
        ["powershell", "-NoProfile", "-Command", script],
        capture_output=True, text=True, timeout=int(params.get("timeout", 60)),
    )
    out = (proc.stdout or "").strip()
    err = (proc.stderr or "").strip()
    parts = [f"Exit-Code: {proc.returncode}"]
    if out:
        parts.append(f"Ausgabe:\n{out}")
    if err:
        parts.append(f"Fehler:\n{err}")
    return "\n".join(parts)


def _notify(params: dict) -> str:
    title = params.get("title", "Jarvis")
    message = params["message"]
    # Toast-Benachrichtigung über PowerShell/BurntToast-freies Bordmittel
    ps = (
        "[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, "
        "ContentType=WindowsRuntime] > $null; "
        "$t=[Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent("
        "[Windows.UI.Notifications.ToastTemplateType]::ToastText02); "
        f"$t.GetElementsByTagName('text')[0].AppendChild($t.CreateTextNode('{title}'))>$null; "
        f"$t.GetElementsByTagName('text')[1].AppendChild($t.CreateTextNode('{message}'))>$null; "
        "$n=[Windows.UI.Notifications.ToastNotification]::new($t); "
        "[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('Jarvis').Show($n)"
    )
    subprocess.run(["powershell", "-NoProfile", "-Command", ps], capture_output=True)
    return "Benachrichtigung angezeigt."


def _set_volume(params: dict) -> str:
    # Grobe Steuerung über simuliertes Drücken der Lautstärketasten.
    # Für feine Prozentwerte kann Jarvis run_shell mit nircmd o.ä. nutzen.
    percent = int(params["percent"])
    presses = max(0, min(50, percent // 2))
    ps = (
        "$o=New-Object -ComObject WScript.Shell; "
        "1..{n} | ForEach-Object {{ $o.SendKeys([char]174) }}; "  # 174 = Vol-Down (alles runter)
        "1..{p} | ForEach-Object {{ $o.SendKeys([char]175) }}"     # 175 = Vol-Up
    ).format(n=50, p=presses)
    subprocess.run(["powershell", "-NoProfile", "-Command", ps], capture_output=True)
    return f"Lautstärke ungefähr auf {percent}% gesetzt."


def _power(params: dict) -> str:
    action = params["action"]
    cmds = {
        "shutdown": "shutdown /s /t 5",
        "restart": "shutdown /r /t 5",
        "lock": "rundll32.exe user32.dll,LockWorkStation",
        "sleep": "rundll32.exe powrprof.dll,SetSuspendState 0,1,0",
    }
    cmd = cmds.get(action)
    if not cmd:
        return f"Unbekannte Aktion: {action}"
    subprocess.Popen(cmd, shell=True)
    return f"Führe '{action}' aus."


def windows_tools() -> list[Tool]:
    return common_tools() + [
        Tool(
            name="open_app",
            description=(
                "Öffnet ein Programm, eine Datei oder URL unter Windows. "
                "Beispiele: 'notepad', 'chrome', 'https://youtube.com', 'C:\\\\Bild.png'."
            ),
            input_schema={
                "type": "object",
                "properties": {"target": {"type": "string"}},
                "required": ["target"],
            },
            handler=_open_app,
        ),
        Tool(
            name="powershell",
            description=(
                "Führt ein PowerShell-Skript aus. Mächtig für Systemsteuerung, "
                "WLAN, Prozesse, Dienste, Registry usw."
            ),
            input_schema={
                "type": "object",
                "properties": {
                    "script": {"type": "string"},
                    "timeout": {"type": "integer"},
                },
                "required": ["script"],
            },
            handler=_powershell,
            dangerous=True,
        ),
        Tool(
            name="notify",
            description="Zeigt eine Windows-Toast-Benachrichtigung an.",
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
            description="Stellt die System-Lautstärke grob ein (0–100 Prozent).",
            input_schema={
                "type": "object",
                "properties": {"percent": {"type": "integer"}},
                "required": ["percent"],
            },
            handler=_set_volume,
        ),
        Tool(
            name="power",
            description="Steuert den Energiezustand: 'shutdown', 'restart', 'lock' oder 'sleep'.",
            input_schema={
                "type": "object",
                "properties": {
                    "action": {"type": "string", "enum": ["shutdown", "restart", "lock", "sleep"]},
                },
                "required": ["action"],
            },
            handler=_power,
            dangerous=True,
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
        tools=windows_tools(),
        platform_name="Windows PC",
        confirm=confirm,
        on_status=lambda msg: print(f"   … {msg}"),
    )

    print("=" * 60)
    print("  J.A.R.V.I.S.  –  Windows")
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
