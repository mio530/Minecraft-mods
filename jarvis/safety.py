"""
JARVIS – Schutz-Schicht (Guard)
===============================

Entscheidet vor JEDER Werkzeug-Ausführung, ob sie erlaubt, zu bestätigen oder
komplett verboten ist. Diese Schicht ist IMMER aktiv (auch im Vollzugriff-Modus)
und setzt folgende Regeln um:

  • System-/App-Verzeichnisse werden NIE verändert oder gelöscht  -> BLOCK
  • Dateien, die Jarvis NICHT selbst erstellt hat, werden vor dem Ändern/Löschen
    IMMER abgefragt  -> ASK  (auch im Vollzugriff-Modus)
  • "Wichtig erscheinende" Dateien (z.B. ~/.ssh, .bashrc, Schlüssel) -> ASK
  • Neue Dateien/Ordner ERSTELLEN  -> ALLOW (ohne Nachfrage)
  • Von Jarvis selbst erstellte Dateien ändern  -> ALLOW (ohne Nachfrage)

Für Werkzeuge ohne klaren Datei-Bezug (run_shell, run_python, kill_process,
powershell, SMS, ...) gilt: im Vollzugriff-Modus ALLOW, sonst ASK.
"""

from __future__ import annotations

import os
import platform
from typing import Callable

SYSTEM = platform.system().lower()

# Datei-verändernde Werkzeuge, die pfadgenau geprüft werden
FILE_TOOLS = {
    "edit_file", "write_file", "delete_path", "move_path",
    "copy_path", "make_dir", "chmod_path",
}


def _protected_prefixes() -> list[str]:
    if SYSTEM == "windows":
        sysroot = os.environ.get("SystemRoot", r"C:\Windows")
        return [sysroot, r"C:\Program Files", r"C:\Program Files (x86)",
                r"C:\ProgramData", r"C:\Windows"]
    if SYSTEM == "darwin":
        return ["/System", "/Library", "/Applications", "/usr", "/bin",
                "/sbin", "/opt", "/private", "/var"]
    # Android/Termux erkennen
    prefixes = ["/bin", "/sbin", "/usr", "/lib", "/lib64", "/etc", "/boot",
                "/sys", "/proc", "/dev", "/opt", "/snap", "/var/lib"]
    if "ANDROID_ROOT" in os.environ or "com.termux" in os.environ.get("PREFIX", ""):
        prefixes += ["/system", "/vendor", "/product", "/apex",
                     "/data/app", "/data/data"]
        termux = os.environ.get("PREFIX")  # z.B. /data/data/com.termux/files/usr
        if termux:
            prefixes.append(termux)  # Termux-Laufzeit (Apps/Pakete) schützen
    return prefixes


def _norm(path: str) -> str:
    p = os.path.abspath(os.path.expanduser(path))
    return os.path.normcase(p)


def is_protected(path: str) -> bool:
    """Liegt der Pfad in einem geschützten System-/App-Bereich?"""
    target = _norm(path)
    for pref in _protected_prefixes():
        pref = _norm(pref)
        if target == pref or target.startswith(pref + os.sep):
            return True
    return False


def is_important(path: str) -> bool:
    """Sieht die Datei nach etwas Wichtigem aus (Konfig, Schlüssel, ...)?"""
    p = os.path.expanduser(path)
    name = os.path.basename(p).lower()
    parent = os.path.basename(os.path.dirname(p)).lower()
    if parent in (".ssh", ".gnupg", ".aws", ".config"):
        return True
    important_names = {
        ".bashrc", ".zshrc", ".profile", ".bash_profile", ".gitconfig",
        ".npmrc", ".env", "authorized_keys", "known_hosts", "id_rsa",
        "id_ed25519", "credentials", "boot.ini", "hosts",
    }
    if name in important_names:
        return True
    return os.path.splitext(name)[1] in (".key", ".pem", ".crt", ".p12")


class Guard:
    def __init__(self, unrestricted: Callable[[], bool] = lambda: False):
        self.unrestricted = unrestricted
        self._created: set[str] = set()  # von Jarvis in dieser Sitzung erstellt

    def created_here(self, path: str) -> bool:
        return _norm(path) in self._created

    # -- Welche Pfade verändert ein Werkzeug, und wie? --------------------
    def _mutations(self, name: str, params: dict) -> list[tuple[str, str]]:
        def action_for(path: str, appending: bool = False) -> str:
            if not os.path.exists(os.path.expanduser(path)):
                return "create"
            return "modify" if appending else "overwrite"

        if name == "edit_file":
            return [(params["path"], "modify")]
        if name == "chmod_path":
            return [(params["path"], "modify")]
        if name == "delete_path":
            return [(params["path"], "delete")]
        if name == "make_dir":
            return [(params["path"], "create")]
        if name == "write_file":
            return [(params["path"], action_for(params["path"], bool(params.get("append"))))]
        if name == "copy_path":
            return [(params["dst"], action_for(params["dst"]))]
        if name == "move_path":
            return [(params["src"], "delete"), (params["dst"], action_for(params["dst"]))]
        return []

    # -- Die zentrale Entscheidung ---------------------------------------
    def decide(self, tool, params: dict) -> tuple[str, str]:
        """Gibt (aktion, grund) zurück: 'allow' | 'ask' | 'block'."""
        name = tool.name

        # Ungefährliche Werkzeuge (lesen, suchen, merken ...) immer erlauben
        if not tool.dangerous:
            return "allow", ""

        if name in FILE_TOOLS:
            muts = self._mutations(name, params)
            # 1) Geschützte Bereiche komplett verbieten
            for path, _act in muts:
                if is_protected(path):
                    return "block", f"'{path}' liegt in einem geschützten System-/App-Bereich."
            # 2) Ändern/Löschen/Überschreiben absichern
            for path, act in muts:
                if act in ("modify", "delete", "overwrite"):
                    if not self.created_here(path):
                        return "ask", f"'{path}' wurde nicht von Jarvis erstellt."
                    if is_important(path):
                        return "ask", f"'{path}' wirkt wichtig."
            # 3) Reines Erstellen (oder eigene Datei) -> ohne Nachfrage
            return "allow", ""

        # Werkzeuge ohne klaren Datei-Bezug (Shell, Code, Prozesse, SMS ...)
        if self.unrestricted():
            return "allow", ""
        return "ask", "Bestätigung erforderlich."

    # -- Nach erfolgreicher Ausführung merken, was erstellt wurde ---------
    def record(self, tool, params: dict) -> None:
        name = tool.name
        if name not in FILE_TOOLS:
            return
        for path, act in self._mutations(name, params):
            if act in ("create", "overwrite", "modify"):
                self._created.add(_norm(path))
