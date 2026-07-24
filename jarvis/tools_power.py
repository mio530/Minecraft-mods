"""
JARVIS – Power-Werkzeuge (voller Gerätezugriff)
===============================================

Mächtige, plattformübergreifende Werkzeuge, mit denen Jarvis auf ALLES auf dem
Gerät zugreifen, es benutzen und umschreiben kann: Dateien bearbeiten,
verschieben, kopieren, löschen; suchen; Rechte ändern; Prozesse auflisten und
beenden; beliebigen Code ausführen; Umgebungsvariablen lesen.

⚠️  Diese Werkzeuge sind mit dangerous=True markiert – sie werden vor Ausführung
    bestätigt, AUSSER der Vollzugriff-Modus ist aktiv (JARVIS_UNRESTRICTED=1
    oder in der GUI der Haken "Vollzugriff"). Nur auf deinen eigenen Geräten
    verwenden.
"""

from __future__ import annotations

import os
import platform
import shutil
import signal
import subprocess
import sys
from pathlib import Path

from tools_base import Tool

IS_WIN = platform.system().lower() == "windows"


def is_unrestricted() -> bool:
    """Vollzugriff (ohne Rückfragen) per Umgebungsvariable aktiviert?"""
    return os.environ.get("JARVIS_UNRESTRICTED", "").strip().lower() in (
        "1", "true", "yes", "ja", "on",
    )


def _p(path: str) -> Path:
    return Path(os.path.expanduser(path))


# ----------------------------------------------------------------------------
# Handler
# ----------------------------------------------------------------------------
def _edit_file(params: dict) -> str:
    """Text in einer Datei ersetzen (umschreiben)."""
    path = _p(params["path"])
    old = params["old"]
    new = params["new"]
    data = path.read_text(encoding="utf-8", errors="replace")
    count = data.count(old)
    if count == 0:
        return f"Text nicht gefunden in {path} – nichts geändert."
    path.write_text(data.replace(old, new), encoding="utf-8")
    return f"{count} Ersetzung(en) in {path} vorgenommen."


def _move_path(params: dict) -> str:
    src, dst = _p(params["src"]), _p(params["dst"])
    shutil.move(str(src), str(dst))
    return f"Verschoben/umbenannt: {src} -> {dst}"


def _copy_path(params: dict) -> str:
    src, dst = _p(params["src"]), _p(params["dst"])
    if src.is_dir():
        shutil.copytree(src, dst, dirs_exist_ok=True)
    else:
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
    return f"Kopiert: {src} -> {dst}"


def _delete_path(params: dict) -> str:
    path = _p(params["path"])
    if not path.exists():
        return f"{path} existiert nicht."
    if path.is_dir():
        shutil.rmtree(path)
        return f"Ordner gelöscht: {path}"
    path.unlink()
    return f"Datei gelöscht: {path}"


def _make_dir(params: dict) -> str:
    path = _p(params["path"])
    path.mkdir(parents=True, exist_ok=True)
    return f"Ordner angelegt: {path}"


def _find_files(params: dict) -> str:
    root = _p(params.get("root", "."))
    pattern = params.get("pattern", "*")
    limit = int(params.get("limit", 200))
    matches = []
    for i, m in enumerate(root.rglob(pattern)):
        if i >= limit:
            matches.append("... (weitere ausgelassen)")
            break
        matches.append(str(m))
    return "\n".join(matches) or "Keine Treffer."


def _search_in_files(params: dict) -> str:
    root = _p(params.get("root", "."))
    query = params["query"]
    glob = params.get("glob", "*")
    limit = int(params.get("limit", 100))
    hits = []
    for f in root.rglob(glob):
        if not f.is_file():
            continue
        try:
            for n, line in enumerate(f.read_text(encoding="utf-8", errors="ignore").splitlines(), 1):
                if query in line:
                    hits.append(f"{f}:{n}: {line.strip()[:200]}")
                    if len(hits) >= limit:
                        return "\n".join(hits) + "\n... (Limit erreicht)"
        except Exception:
            continue
    return "\n".join(hits) or "Keine Treffer."


def _chmod_path(params: dict) -> str:
    path = _p(params["path"])
    if "executable" in params:
        st = path.stat().st_mode
        path.chmod(st | 0o111)
        return f"Ausführbar gemacht: {path}"
    mode = params["mode"]  # z.B. "755"
    path.chmod(int(str(mode), 8))
    return f"Rechte {mode} gesetzt für {path}"


def _list_processes(_params: dict) -> str:
    try:
        if IS_WIN:
            out = subprocess.run(["tasklist"], capture_output=True, text=True, timeout=20).stdout
        else:
            out = subprocess.run(
                ["ps", "-eo", "pid,comm,pcpu,pmem"],
                capture_output=True, text=True, timeout=20,
            ).stdout
    except Exception:
        out = subprocess.run(["ps", "aux"], capture_output=True, text=True).stdout
    lines = out.splitlines()
    return "\n".join(lines[:60]) + ("\n... (gekürzt)" if len(lines) > 60 else "")


def _kill_process(params: dict) -> str:
    if "pid" in params:
        pid = int(params["pid"])
        if IS_WIN:
            subprocess.run(["taskkill", "/F", "/PID", str(pid)], capture_output=True)
        else:
            os.kill(pid, signal.SIGTERM)
        return f"Prozess {pid} beendet."
    name = params["name"]
    if IS_WIN:
        subprocess.run(["taskkill", "/F", "/IM", name], capture_output=True)
    else:
        subprocess.run(["pkill", "-f", name], capture_output=True)
    return f"Prozess(e) mit Namen '{name}' beendet."


def _run_python(params: dict) -> str:
    """Beliebigen Python-Code ausführen – maximaler programmatischer Zugriff."""
    code = params["code"]
    proc = subprocess.run(
        [sys.executable, "-c", code],
        capture_output=True, text=True, timeout=int(params.get("timeout", 120)),
    )
    parts = [f"Exit-Code: {proc.returncode}"]
    if proc.stdout.strip():
        parts.append(f"Ausgabe:\n{proc.stdout}")
    if proc.stderr.strip():
        parts.append(f"Fehler:\n{proc.stderr}")
    return "\n".join(parts)


def _env(params: dict) -> str:
    name = params.get("name")
    if name:
        return f"{name}={os.environ.get(name, '(nicht gesetzt)')}"
    return "\n".join(f"{k}={v}" for k, v in sorted(os.environ.items()))


# ----------------------------------------------------------------------------
# Werkzeug-Liste
# ----------------------------------------------------------------------------
def power_tools() -> list[Tool]:
    return [
        Tool("edit_file",
             "Ersetzt Text in einer Datei (umschreiben). Gibt an, wie oft ersetzt wurde.",
             {"type": "object",
              "properties": {"path": {"type": "string"},
                             "old": {"type": "string", "description": "zu ersetzender Text"},
                             "new": {"type": "string", "description": "neuer Text"}},
              "required": ["path", "old", "new"]}, _edit_file, dangerous=True),
        Tool("move_path", "Verschiebt oder benennt eine Datei/einen Ordner um.",
             {"type": "object", "properties": {"src": {"type": "string"}, "dst": {"type": "string"}},
              "required": ["src", "dst"]}, _move_path, dangerous=True),
        Tool("copy_path", "Kopiert eine Datei oder einen Ordner.",
             {"type": "object", "properties": {"src": {"type": "string"}, "dst": {"type": "string"}},
              "required": ["src", "dst"]}, _copy_path, dangerous=True),
        Tool("delete_path", "Löscht eine Datei oder einen Ordner (Ordner rekursiv).",
             {"type": "object", "properties": {"path": {"type": "string"}},
              "required": ["path"]}, _delete_path, dangerous=True),
        Tool("make_dir", "Legt einen Ordner an (inkl. Zwischenordner).",
             {"type": "object", "properties": {"path": {"type": "string"}},
              "required": ["path"]}, _make_dir),
        Tool("find_files", "Sucht Dateien/Ordner rekursiv per Muster (Glob, z.B. '*.txt').",
             {"type": "object",
              "properties": {"root": {"type": "string"}, "pattern": {"type": "string"},
                             "limit": {"type": "integer"}},
              "required": []}, _find_files),
        Tool("search_in_files", "Durchsucht Dateiinhalte nach einem Text (grep-artig).",
             {"type": "object",
              "properties": {"root": {"type": "string"}, "query": {"type": "string"},
                             "glob": {"type": "string"}, "limit": {"type": "integer"}},
              "required": ["query"]}, _search_in_files),
        Tool("chmod_path", "Ändert Dateirechte (mode '755') oder macht eine Datei ausführbar.",
             {"type": "object",
              "properties": {"path": {"type": "string"}, "mode": {"type": "string"},
                             "executable": {"type": "boolean"}},
              "required": ["path"]}, _chmod_path, dangerous=True),
        Tool("list_processes", "Listet laufende Prozesse auf.",
             {"type": "object", "properties": {}, "required": []}, _list_processes),
        Tool("kill_process", "Beendet einen Prozess per PID oder Name.",
             {"type": "object",
              "properties": {"pid": {"type": "integer"}, "name": {"type": "string"}},
              "required": []}, _kill_process, dangerous=True),
        Tool("run_python",
             "Führt beliebigen Python-Code aus (maximaler Zugriff auf das Gerät).",
             {"type": "object",
              "properties": {"code": {"type": "string"}, "timeout": {"type": "integer"}},
              "required": ["code"]}, _run_python, dangerous=True),
        Tool("env", "Liest Umgebungsvariablen (alle, oder eine bestimmte per name).",
             {"type": "object", "properties": {"name": {"type": "string"}}, "required": []},
             _env),
    ]
