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
import sys
import urllib.request

from tools_base import Tool
from tools_power import power_tools
import workspace


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
def _ws_save(params: dict) -> str:
    return workspace.save(params["path"], params["content"])


def _ws_read(params: dict) -> str:
    return workspace.read(params["path"])


def _ws_list(_params: dict) -> str:
    files = workspace.list_files()
    return "\n".join(files) if files else "(Werkstatt ist leer)"


def _ws_versions(params: dict) -> str:
    v = workspace.list_versions(params["path"])
    return "\n".join(v) if v else "(keine früheren Varianten)"


def _ws_restore(params: dict) -> str:
    return workspace.restore_version(params["path"], params["version"])


# Ausführen von Werkstatt-Dateien (Auto-Erkennung des Interpreters)
_INTERP = {
    ".py": [sys.executable], ".js": ["node"], ".ts": ["npx", "ts-node"],
    ".sh": ["bash"], ".rb": ["ruby"], ".pl": ["perl"], ".php": ["php"],
    ".go": ["go", "run"],
}


def execute_workspace_file(relpath: str, timeout: int = 60):
    """Führt eine Datei in der Werkstatt aus. Gibt (exit_code, ausgabe) zurück."""
    p = workspace._safe(relpath)
    if not p.exists():
        return 1, f"Datei nicht gefunden: {relpath}"
    args = _INTERP.get(p.suffix.lower())
    if args is None:
        if os.access(p, os.X_OK):
            args = []
        else:
            return 1, (f"Ich weiß nicht, wie ich '{p.suffix}' ausführen soll. "
                       f"Nutze 'workspace_run' mit einem passenden Befehl.")
    cmd = args + [str(p)]
    try:
        proc = subprocess.run(cmd, cwd=str(workspace.workspace_dir()),
                              capture_output=True, text=True, timeout=timeout)
    except FileNotFoundError as exc:
        return 1, f"Interpreter fehlt: {exc}"
    except subprocess.TimeoutExpired:
        return 124, "Zeitüberschreitung."
    out = proc.stdout or ""
    if (proc.stderr or "").strip():
        out += "\n" + proc.stderr
    return proc.returncode, (out.strip() or "(keine Ausgabe)")


def _run_file(params: dict) -> str:
    code, out = execute_workspace_file(params["path"], int(params.get("timeout", 60)))
    return f"Exit-Code: {code}\n{out}"


def _ws_run(params: dict) -> str:
    proc = subprocess.run(params["command"], shell=True,
                          cwd=str(workspace.workspace_dir()),
                          capture_output=True, text=True,
                          timeout=int(params.get("timeout", 120)))
    out = proc.stdout or ""
    if (proc.stderr or "").strip():
        out += "\n" + proc.stderr
    return f"Exit-Code: {proc.returncode}\n{out.strip() or '(keine Ausgabe)'}"


def workspace_tools() -> list[Tool]:
    return [
        Tool("workspace_save",
             "Speichert eine fertige Datei in Jarvis' Werkstatt. Beim Überschreiben "
             "wird die vorherige Variante automatisch im Verlauf aufbewahrt. Nutze "
             "das für fertige Programme/Projekte.",
             {"type": "object",
              "properties": {"path": {"type": "string"}, "content": {"type": "string"}},
              "required": ["path", "content"]}, _ws_save),
        Tool("workspace_read", "Liest eine Datei aus der Werkstatt.",
             {"type": "object", "properties": {"path": {"type": "string"}},
              "required": ["path"]}, _ws_read),
        Tool("workspace_list", "Listet alle Dateien in der Werkstatt auf.",
             {"type": "object", "properties": {}, "required": []}, _ws_list),
        Tool("workspace_versions", "Zeigt die früheren Varianten einer Werkstatt-Datei.",
             {"type": "object", "properties": {"path": {"type": "string"}},
              "required": ["path"]}, _ws_versions),
        Tool("workspace_restore", "Stellt eine frühere Variante einer Datei wieder her.",
             {"type": "object",
              "properties": {"path": {"type": "string"}, "version": {"type": "string"}},
              "required": ["path", "version"]}, _ws_restore),
        Tool("run_file",
             "Führt eine Datei aus der Werkstatt aus (erkennt Python/JS/Bash/… "
             "automatisch) und gibt Exit-Code + Ausgabe zurück. Nutze das zum "
             "Testen; bei Fehlern die Ausgabe lesen und den Code beheben.",
             {"type": "object",
              "properties": {"path": {"type": "string"}, "timeout": {"type": "integer"}},
              "required": ["path"]}, _run_file, dangerous=True),
        Tool("workspace_run",
             "Führt einen beliebigen Befehl IM Werkstatt-Ordner aus (z.B. "
             "'pip install ...', 'npm install', 'go build').",
             {"type": "object",
              "properties": {"command": {"type": "string"}, "timeout": {"type": "integer"}},
              "required": ["command"]}, _ws_run, dangerous=True),
    ]


def common_tools() -> list[Tool]:
    from tools_git import git_tools
    return power_tools() + workspace_tools() + git_tools() + [
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
