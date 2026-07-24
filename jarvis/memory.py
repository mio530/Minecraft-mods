"""
JARVIS – Dauerhaftes Gedächtnis
===============================

Jarvis merkt sich über Sitzungen hinweg, was er über den Nutzer lernt
(Name, Vorlieben, Gewohnheiten, bevorzugte Antwortlänge, wiederkehrende
Aufgaben ...) und passt sein Verhalten daran an.

- Gespeichert wird lokal in  ~/.jarvis/memory.json  (bleibt privat auf dem Gerät).
- Beim Start wird das Gedächtnis in den Systemprompt geladen -> die KI "kennt"
  dich sofort wieder.
- Über die Werkzeuge remember / recall / forget kann die KI aktiv Dinge merken,
  nachschlagen und wieder vergessen.

Hinweis: Keine Passwörter/Geheimnisse ins Gedächtnis schreiben.
"""

from __future__ import annotations

import json
import time
from pathlib import Path

from tools_base import Tool


# Anweisung, die dem Systemprompt hinzugefügt wird, wenn ein Gedächtnis aktiv ist
MEMORY_INSTRUCTION = (
    "GEDÄCHTNIS: Du hast ein dauerhaftes Gedächtnis über Sitzungen hinweg. "
    "Wenn du etwas STABILES über den Nutzer lernst – Name, Vorlieben, "
    "Gewohnheiten, bevorzugte Sprache und Antwortlänge, wiederkehrende Aufgaben, "
    "wie er angesprochen werden möchte – speichere es knapp mit dem Werkzeug "
    "'remember'. Passe dein Verhalten an das an, was du bereits weißt. Nutze "
    "'recall', um nachzusehen, und 'forget', wenn etwas veraltet ist oder der "
    "Nutzer es wünscht. Speichere niemals Passwörter oder Geheimnisse. Erwähne "
    "das Speichern nur beiläufig, unterbrich den Gesprächsfluss nicht."
)


class Memory:
    """Einfaches, dateibasiertes Langzeitgedächtnis."""

    def __init__(self, path: str | None = None):
        self.path = Path(path) if path else Path.home() / ".jarvis" / "memory.json"
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.items: list[dict] = self._load()

    # -- Persistenz -------------------------------------------------------
    def _load(self) -> list[dict]:
        if self.path.exists():
            try:
                return json.loads(self.path.read_text(encoding="utf-8"))
            except Exception:
                return []
        return []

    def _save(self) -> None:
        self.path.write_text(
            json.dumps(self.items, ensure_ascii=False, indent=2), encoding="utf-8"
        )

    # -- Operationen ------------------------------------------------------
    def add(self, text: str, category: str = "allgemein") -> int:
        text = (text or "").strip()
        if not text:
            return -1
        # Duplikate vermeiden
        for it in self.items:
            if it["text"].lower() == text.lower():
                return it["id"]
        nid = max((it["id"] for it in self.items), default=0) + 1
        self.items.append({
            "id": nid,
            "text": text,
            "category": (category or "allgemein").strip(),
            "date": time.strftime("%Y-%m-%d"),
        })
        self._save()
        return nid

    def list(self, query: str | None = None) -> list[dict]:
        if query:
            q = query.lower()
            return [it for it in self.items
                    if q in it["text"].lower() or q in it["category"].lower()]
        return list(self.items)

    def remove(self, ident: str) -> int:
        s = str(ident).strip().lower()

        def match(it: dict) -> bool:
            return str(it["id"]) == s or s in it["text"].lower()

        before = len(self.items)
        self.items = [it for it in self.items if not match(it)]
        self._save()
        return before - len(self.items)

    def clear(self) -> int:
        n = len(self.items)
        self.items = []
        self._save()
        return n

    # -- Für den Systemprompt --------------------------------------------
    def as_prompt_block(self) -> str:
        if not self.items:
            return ""
        lines = [f"- [{it['category']}] {it['text']}" for it in self.items]
        return (
            "WAS DU ÜBER DEN NUTZER WEISST (aus früheren Gesprächen – passe dein "
            "Verhalten daran an):\n" + "\n".join(lines)
        )


# ----------------------------------------------------------------------------
# Werkzeuge, die die KI zum Merken/Nachschlagen/Vergessen benutzt
# ----------------------------------------------------------------------------
def memory_tools(memory: Memory) -> list[Tool]:
    def _remember(params: dict) -> str:
        nid = memory.add(params["text"], params.get("category", "allgemein"))
        return f"Gemerkt (#{nid}): {params['text']}" if nid > 0 else "Nichts zu merken."

    def _recall(params: dict) -> str:
        items = memory.list(params.get("query"))
        if not items:
            return "Ich habe dazu noch keine Notizen."
        return "\n".join(f"#{it['id']} [{it['category']}] {it['text']}" for it in items)

    def _forget(params: dict) -> str:
        n = memory.remove(params["what"])
        return f"{n} Notiz(en) vergessen." if n else "Keine passende Notiz gefunden."

    return [
        Tool(
            name="remember",
            description=(
                "Merkt sich dauerhaft eine Tatsache über den Nutzer (Vorliebe, "
                "Gewohnheit, Name, wie er Antworten mag). Nutze das, wenn du etwas "
                "Stabiles lernst, das künftig hilfreich ist."
            ),
            input_schema={
                "type": "object",
                "properties": {
                    "text": {"type": "string", "description": "Die zu merkende Tatsache, knapp formuliert."},
                    "category": {"type": "string", "description": "z.B. 'vorliebe', 'name', 'gewohnheit', 'stil'."},
                },
                "required": ["text"],
            },
            handler=_remember,
        ),
        Tool(
            name="recall",
            description="Schlägt im dauerhaften Gedächtnis nach (optional gefiltert per query).",
            input_schema={
                "type": "object",
                "properties": {"query": {"type": "string"}},
                "required": [],
            },
            handler=_recall,
        ),
        Tool(
            name="forget",
            description="Vergisst eine Notiz – per Nummer (id) oder per Textausschnitt.",
            input_schema={
                "type": "object",
                "properties": {"what": {"type": "string"}},
                "required": ["what"],
            },
            handler=_forget,
        ),
    ]
