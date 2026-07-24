"""
JARVIS – GitHub-/Git-Werkzeuge
==============================

Erlaubt Jarvis, Projekte mit Git zu verwalten und mit GitHub zu verknüpfen.
Standard-Arbeitsordner ist die Werkstatt (~/.jarvis/workspace).

Für GitHub (Repo anlegen / pushen) wird ein Personal Access Token gebraucht:
    export GITHUB_TOKEN=ghp_....     (Windows:  set GITHUB_TOKEN=ghp_...)
Token erstellen: https://github.com/settings/tokens  (Scope: repo)
"""

from __future__ import annotations

import json
import os
import subprocess
import urllib.error
import urllib.request
from pathlib import Path

from tools_base import Tool
import workspace


def _cwd(params: dict) -> str:
    return params.get("dir") or str(workspace.workspace_dir())


def _run(args: list[str], cwd: str) -> str:
    try:
        p = subprocess.run(args, cwd=cwd, capture_output=True, text=True, timeout=180)
    except FileNotFoundError:
        return "Git ist nicht installiert. Bitte Git installieren (https://git-scm.com)."
    out = (p.stdout or "").strip()
    err = (p.stderr or "").strip()
    txt = f"Exit-Code: {p.returncode}"
    if out:
        txt += f"\n{out}"
    if err:
        txt += f"\n{err}"
    return txt


def _token_url(clone_url: str, token: str) -> str:
    # https://github.com/owner/repo.git -> https://<token>@github.com/owner/repo.git
    return clone_url.replace("https://", f"https://{token}@", 1)


# ----------------------------------------------------------------------------
# Handler
# ----------------------------------------------------------------------------
def _git_status(params: dict) -> str:
    return _run(["git", "status", "--short", "--branch"], _cwd(params))


def _git_init(params: dict) -> str:
    return _run(["git", "init"], _cwd(params))


def _git_commit(params: dict) -> str:
    cwd = _cwd(params)
    _run(["git", "add", "-A"], cwd)
    return _run(["git", "commit", "-m", params["message"]], cwd)


def _git_log(params: dict) -> str:
    return _run(["git", "log", "--oneline", "-15"], _cwd(params))


def _git_clone(params: dict) -> str:
    url = params["url"]
    return _run(["git", "clone", url], str(workspace.workspace_dir()))


def _git_push(params: dict) -> str:
    cwd = _cwd(params)
    args = ["git", "push"]
    if params.get("set_upstream"):
        args += ["-u", "origin", params.get("branch", "HEAD")]
    return _run(args, cwd)


def _github_create_repo(params: dict) -> str:
    token = os.environ.get("GITHUB_TOKEN")
    if not token:
        return ("Kein GITHUB_TOKEN gesetzt. Erstelle einen Token unter "
                "https://github.com/settings/tokens (Scope 'repo') und setze "
                "GITHUB_TOKEN.")
    data = json.dumps({"name": params["name"],
                       "private": bool(params.get("private", True))}).encode()
    req = urllib.request.Request(
        "https://api.github.com/user/repos", data=data, method="POST",
        headers={"Authorization": f"token {token}", "User-Agent": "Jarvis",
                 "Accept": "application/vnd.github+json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            obj = json.loads(r.read())
        return f"Repo erstellt: {obj.get('html_url')}"
    except urllib.error.HTTPError as e:
        return f"GitHub-Fehler {e.code}: {e.read().decode()[:200]}"


def _github_publish(params: dict) -> str:
    """Ein Schritt: Werkstatt committen, GitHub-Repo anlegen, verknüpfen, pushen."""
    token = os.environ.get("GITHUB_TOKEN")
    if not token:
        return ("Kein GITHUB_TOKEN gesetzt. Token unter "
                "https://github.com/settings/tokens (Scope 'repo') anlegen und setzen.")
    cwd = _cwd(params)
    name = params["name"]
    message = params.get("message", "Initial commit by Jarvis")
    # 1) lokales Repo
    if not (Path(cwd) / ".git").exists():
        _run(["git", "init"], cwd)
    _run(["git", "add", "-A"], cwd)
    _run(["git", "commit", "-m", message], cwd)
    # 2) Repo auf GitHub anlegen
    data = json.dumps({"name": name, "private": bool(params.get("private", True))}).encode()
    req = urllib.request.Request(
        "https://api.github.com/user/repos", data=data, method="POST",
        headers={"Authorization": f"token {token}", "User-Agent": "Jarvis",
                 "Accept": "application/vnd.github+json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            obj = json.loads(r.read())
    except urllib.error.HTTPError as e:
        return f"GitHub-Fehler {e.code}: {e.read().decode()[:200]}"
    clone_url = obj.get("clone_url", "")
    html_url = obj.get("html_url", "")
    # 3) Remote (mit Token) setzen und pushen
    _run(["git", "remote", "remove", "origin"], cwd)
    _run(["git", "remote", "add", "origin", _token_url(clone_url, token)], cwd)
    push = _run(["git", "push", "-u", "origin", "HEAD"], cwd)
    return f"Veröffentlicht auf GitHub: {html_url}\n{push}"


def git_tools() -> list[Tool]:
    d = {"dir": {"type": "string", "description": "Projektordner (Standard: Werkstatt)."}}
    return [
        Tool("git_status", "Zeigt den Git-Status des Projekts.",
             {"type": "object", "properties": dict(d), "required": []}, _git_status),
        Tool("git_init", "Initialisiert ein Git-Repository im Projektordner.",
             {"type": "object", "properties": dict(d), "required": []}, _git_init),
        Tool("git_commit", "Fügt alle Änderungen hinzu und committet sie (message).",
             {"type": "object",
              "properties": {**d, "message": {"type": "string"}},
              "required": ["message"]}, _git_commit),
        Tool("git_log", "Zeigt die letzten Commits.",
             {"type": "object", "properties": dict(d), "required": []}, _git_log),
        Tool("git_clone", "Klont ein GitHub-Repository in die Werkstatt (url).",
             {"type": "object", "properties": {"url": {"type": "string"}},
              "required": ["url"]}, _git_clone, dangerous=True),
        Tool("git_push", "Pusht Commits zum verknüpften GitHub-Repo.",
             {"type": "object",
              "properties": {**d, "set_upstream": {"type": "boolean"},
                             "branch": {"type": "string"}},
              "required": []}, _git_push, dangerous=True),
        Tool("github_create_repo", "Legt ein neues GitHub-Repository an (name, private).",
             {"type": "object",
              "properties": {"name": {"type": "string"}, "private": {"type": "boolean"}},
              "required": ["name"]}, _github_create_repo, dangerous=True),
        Tool("github_publish",
             "Ein Schritt: Werkstatt committen, GitHub-Repo anlegen, verknüpfen und "
             "pushen (name, message, private). Nutze das für 'lade das auf GitHub'.",
             {"type": "object",
              "properties": {**d, "name": {"type": "string"},
                             "message": {"type": "string"}, "private": {"type": "boolean"}},
              "required": ["name"]}, _github_publish, dangerous=True),
    ]
