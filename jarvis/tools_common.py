"""
Gemeinsame Werkzeuge, die auf allen Plattformen funktionieren:
Shell ausführen, Dateien lesen/schreiben/auflisten, Web abrufen, Systeminfo.

Diese Werkzeuge geben IMMER einen String zurück (Ergebnis oder Fehlertext),
den Jarvis (Claude) dann interpretiert.
"""

from __future__ import annotations

import os
import platform
import shutil
import subprocess
import urllib.request

from tools_base import Tool
from tools_power import power_tools


# ----------------------------------------------------------------------------
# Handler
# ----------------------------------------------------------------------------
def _run_shell(params: dict) -> str:
    command = params["command"]
    timeout = int(params.get("timeout", 60))
    proc = subprocess.run(
        command,
        shell=True,
        capture_output=True,
        text=True,
        timeout=timeout,
    )
    out = proc.stdout or ""
    err = proc.stderr or ""
    parts = [f"Exit-Code: {proc.returncode}"]
    if out.strip():
        parts.append(f"STDOUT:\n{out}")
    if err.strip():
        parts.append(f"STDERR:\n{err}")
    return "\n".join(parts)


def _read_file(params: dict) -> str:
    path = os.path.expanduser(params["path"])
    max_bytes = int(params.get("max_bytes", 100_000))
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        data = f.read(max_bytes)
    return data if data else "(Datei ist leer)"


def _write_file(params: dict) -> str:
    path = os.path.expanduser(params["path"])
    content = params["content"]
    append = bool(params.get("append", False))
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    mode = "a" if append else "w"
    with open(path, mode, encoding="utf-8") as f:
        f.write(content)
    return f"OK, {len(content)} Zeichen nach {path} geschrieben."


def _list_dir(params: dict) -> str:
    path = os.path.expanduser(params.get("path", "."))
    entries = []
    for name in sorted(os.listdir(path)):
        full = os.path.join(path, name)
        kind = "DIR " if os.path.isdir(full) else "FILE"
        try:
            size = os.path.getsize(full)
        except OSError:
            size = 0
        entries.append(f"{kind} {size:>10}  {name}")
    return "\n".join(entries) or "(leeres Verzeichnis)"


def _fetch_url(params: dict) -> str:
    url = params["url"]
    max_bytes = int(params.get("max_bytes", 20_000))
    req = urllib.request.Request(url, headers={"User-Agent": "Jarvis/1.0"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        raw = resp.read(max_bytes)
    return raw.decode("utf-8", errors="replace")


def _system_info(_params: dict) -> str:
    info = {
        "System": platform.system(),
        "Release": platform.release(),
        "Version": platform.version(),
        "Maschine": platform.machine(),
        "Prozessor": platform.processor(),
        "Python": platform.python_version(),
        "Benutzer": os.environ.get("USER") or os.environ.get("USERNAME", "?"),
        "Arbeitsverzeichnis": os.getcwd(),
        "CPU-Kerne": os.cpu_count(),
    }
    try:
        total, used, free = shutil.disk_usage(os.path.expanduser("~"))
        info["Speicher frei (GB)"] = round(free / 1e9, 1)
    except OSError:
        pass
    return "\n".join(f"{k}: {v}" for k, v in info.items())


# ----------------------------------------------------------------------------
# Werkzeug-Liste
# ----------------------------------------------------------------------------
def common_tools() -> list[Tool]:
    return power_tools() + [
        Tool(
            name="run_shell",
            description=(
                "Führt einen Shell-Befehl auf diesem Gerät aus und gibt Exit-Code, "
                "STDOUT und STDERR zurück. Für Systemaufgaben, Programme starten, "
                "Prozesse prüfen, Paketverwaltung usw. Nutze das für alles, wofür "
                "kein spezialisierteres Werkzeug existiert."
            ),
            input_schema={
                "type": "object",
                "properties": {
                    "command": {"type": "string", "description": "Der auszuführende Befehl."},
                    "timeout": {"type": "integer", "description": "Timeout in Sekunden (Standard 60)."},
                },
                "required": ["command"],
            },
            handler=_run_shell,
            dangerous=True,  # Shell-Zugriff -> immer bestätigen lassen
        ),
        Tool(
            name="read_file",
            description="Liest den Inhalt einer Textdatei. '~' wird zum Home-Verzeichnis erweitert.",
            input_schema={
                "type": "object",
                "properties": {
                    "path": {"type": "string"},
                    "max_bytes": {"type": "integer", "description": "Max. Bytes (Standard 100000)."},
                },
                "required": ["path"],
            },
            handler=_read_file,
        ),
        Tool(
            name="write_file",
            description="Schreibt Text in eine Datei (überschreibt, oder hängt an wenn append=true).",
            input_schema={
                "type": "object",
                "properties": {
                    "path": {"type": "string"},
                    "content": {"type": "string"},
                    "append": {"type": "boolean"},
                },
                "required": ["path", "content"],
            },
            handler=_write_file,
            dangerous=True,  # kann Dateien überschreiben
        ),
        Tool(
            name="list_dir",
            description="Listet Dateien und Ordner in einem Verzeichnis auf.",
            input_schema={
                "type": "object",
                "properties": {"path": {"type": "string", "description": "Standard: aktuelles Verzeichnis."}},
                "required": [],
            },
            handler=_list_dir,
        ),
        Tool(
            name="fetch_url",
            description="Ruft den Inhalt einer URL ab (z.B. eine API oder Webseite). Gibt den rohen Text zurück.",
            input_schema={
                "type": "object",
                "properties": {
                    "url": {"type": "string"},
                    "max_bytes": {"type": "integer"},
                },
                "required": ["url"],
            },
            handler=_fetch_url,
        ),
        Tool(
            name="system_info",
            description="Gibt Informationen über dieses Gerät zurück (OS, CPU, freier Speicher, Benutzer, ...).",
            input_schema={"type": "object", "properties": {}, "required": []},
            handler=_system_info,
        ),
    ]
