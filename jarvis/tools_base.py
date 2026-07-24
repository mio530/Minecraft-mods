"""
Basis-Definitionen für Jarvis-Werkzeuge.

Dieses Modul hat KEINE Abhängigkeit zu Claude/Anthropic oder Ollama – dadurch
können sowohl die Claude-Version (jarvis_core.py) als auch die kostenlose
Version (jarvis_free_core.py) dieselben Werkzeuge nutzen.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Callable


@dataclass
class Tool:
    """Ein Werkzeug, das Jarvis benutzen kann.

    name         : eindeutiger Name (z.B. "run_shell")
    description  : sagt der KI, WANN das Werkzeug benutzt werden soll
    input_schema : JSON-Schema der Parameter
    handler      : Funktion (dict -> str), die das Werkzeug ausführt
    dangerous    : wenn True, wird vor Ausführung eine Bestätigung verlangt
    """

    name: str
    description: str
    input_schema: dict
    handler: Callable[[dict], str]
    dangerous: bool = False

    def to_api(self) -> dict:
        """Format für die Anthropic-Messages-API."""
        return {
            "name": self.name,
            "description": self.description,
            "input_schema": self.input_schema,
        }

    def to_openai(self) -> dict:
        """Format für die OpenAI-kompatible API (Ollama, Groq, ...)."""
        return {
            "type": "function",
            "function": {
                "name": self.name,
                "description": self.description,
                "parameters": self.input_schema,
            },
        }


def default_system_prompt(platform_name: str, tool_names: list[str]) -> str:
    return (
        "Du bist JARVIS, ein persönlicher KI-Assistent im Stil des Assistenten "
        "aus den Iron-Man-Filmen. Du bist präzise, ruhig, hilfsbereit und leicht "
        "trocken-humorvoll. Du sprichst den Nutzer respektvoll an (z.B. 'Sir' oder "
        "beim Namen, wenn bekannt).\n\n"
        f"Du läufst auf: {platform_name}.\n"
        "Du hast ECHTEN Zugriff auf dieses Gerät über deine Werkzeuge: "
        f"{', '.join(tool_names)}.\n\n"
        "REGELN:\n"
        "- Antworte in der Sprache des Nutzers (meist Deutsch).\n"
        "- Deine Antworten werden oft laut vorgelesen. Halte sie deshalb KURZ und "
        "natürlich gesprochen. Keine Code-Blöcke oder Aufzählungslisten vorlesen, "
        "wenn eine knappe Zusammenfassung reicht.\n"
        "- Wenn eine Aufgabe ein Werkzeug braucht (Programm öffnen, Datei lesen/"
        "schreiben, Befehl ausführen, Systeminfo, Gerätefunktion), NUTZE das "
        "Werkzeug wirklich, statt nur zu beschreiben, wie man es täte.\n"
        "- Führe pro Antwort so viele Werkzeug-Schritte aus, wie nötig, und melde "
        "dann kurz das Ergebnis.\n"
        "- Sei vorsichtig bei irreversiblen Aktionen (löschen, überschreiben, "
        "herunterfahren). Der Nutzer wird dafür ggf. um Bestätigung gebeten – "
        "erkläre kurz, was passieren wird.\n"
        "- Wenn du etwas nicht sicher weißt, sag es ehrlich."
    )
