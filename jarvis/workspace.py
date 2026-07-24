"""
JARVIS – Werkstatt (eigener Arbeitsbereich mit Versionsverlauf)
===============================================================

Ein fester Ordner (~/.jarvis/workspace), in den Jarvis seine fertigen Dateien
und Projekte legt. Beim Überschreiben einer Datei wird die vorherige Version
NICHT gelöscht, sondern in einem versteckten Verlauf (.versions) aufbewahrt.
So kann man über das „⋮"-Menü in der GUI jederzeit frühere Varianten ansehen
und wiederherstellen.
"""

from __future__ import annotations

import shutil
import time
from pathlib import Path

WS = Path.home() / ".jarvis" / "workspace"
VER = WS / ".versions"


def workspace_dir() -> Path:
    WS.mkdir(parents=True, exist_ok=True)
    return WS


def _safe(relpath: str) -> Path:
    """Löst einen Pfad INNERHALB der Werkstatt auf (verhindert Ausbruch)."""
    base = WS.resolve()
    p = (WS / relpath).resolve()
    if base != p and base not in p.parents:
        raise ValueError("Pfad liegt außerhalb der Werkstatt.")
    return p


def _version_dir(relpath: str) -> Path:
    return VER / relpath


def _archive(relpath: str) -> None:
    """Sichert die aktuelle Datei-Version in den Verlauf."""
    p = _safe(relpath)
    if not p.exists() or not p.is_file():
        return
    vd = _version_dir(relpath)
    vd.mkdir(parents=True, exist_ok=True)
    stamp = time.strftime("%Y%m%d_%H%M%S")
    target = vd / stamp
    i = 1
    while target.exists():  # eindeutigen Namen sicherstellen (schnelle Speicherungen)
        target = vd / f"{stamp}_{i:03d}"
        i += 1
    shutil.copy2(p, target)


# ----------------------------------------------------------------------------
# Öffentliche Operationen
# ----------------------------------------------------------------------------
def save(relpath: str, content: str) -> str:
    workspace_dir()
    p = _safe(relpath)
    existed = p.exists()
    if existed:
        _archive(relpath)  # alte Variante aufbewahren
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8")
    n = len(list_versions(relpath))
    hint = f" (frühere Varianten: {n})" if n else ""
    verb = "überschrieben" if existed else "erstellt"
    return f"Datei {verb}: {relpath}{hint}"


def read(relpath: str) -> str:
    p = _safe(relpath)
    if not p.exists():
        return f"Datei nicht gefunden: {relpath}"
    return p.read_text(encoding="utf-8", errors="replace")


def delete(relpath: str) -> str:
    _archive(relpath)
    p = _safe(relpath)
    if p.exists():
        p.unlink()
        return f"Gelöscht (Verlauf behalten): {relpath}"
    return f"Datei nicht gefunden: {relpath}"


def list_files() -> list[str]:
    workspace_dir()
    files = []
    for p in sorted(WS.rglob("*")):
        if p.is_file() and VER not in p.parents and p != VER:
            files.append(str(p.relative_to(WS)))
    return files


def list_versions(relpath: str) -> list[str]:
    vd = _version_dir(relpath)
    if not vd.exists():
        return []
    return sorted((f.name for f in vd.iterdir() if f.is_file()), reverse=True)


def read_version(relpath: str, version_id: str) -> str:
    f = _version_dir(relpath) / version_id
    if not f.exists():
        return f"Variante nicht gefunden: {version_id}"
    return f.read_text(encoding="utf-8", errors="replace")


def restore_version(relpath: str, version_id: str) -> str:
    f = _version_dir(relpath) / version_id
    if not f.exists():
        return f"Variante nicht gefunden: {version_id}"
    _archive(relpath)  # aktuellen Stand ebenfalls sichern
    p = _safe(relpath)
    p.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(f, p)
    return f"Variante {version_id} wiederhergestellt für {relpath}"
