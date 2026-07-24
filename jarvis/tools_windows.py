"""Windows-spezifische Werkzeuge für Jarvis (ohne KI-Abhängigkeit)."""

from __future__ import annotations

import subprocess

from tools_base import Tool
from tools_common import common_tools
from tools_camera import camera_tools


def _open_app(params: dict) -> str:
    target = params["target"]
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
    percent = int(params["percent"])
    presses = max(0, min(50, percent // 2))
    ps = (
        "$o=New-Object -ComObject WScript.Shell; "
        "1..{n} | ForEach-Object {{ $o.SendKeys([char]174) }}; "
        "1..{p} | ForEach-Object {{ $o.SendKeys([char]175) }}"
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
    return common_tools() + camera_tools() + [
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
