"""
JARVIS – Kern / Core
====================

Das "Gehirn" von Jarvis. Nutzt Claude (Opus 4.8) mit echtem Tool-Zugriff.
Dieser Kern ist plattform-neutral: Die konkreten Werkzeuge (Shell, Dateien,
Apps öffnen, Geräte-APIs) werden von der jeweiligen Plattform-Datei
(jarvis_windows.py / jarvis_linux.py / jarvis_android.py) hineingereicht.

Er führt eine Tool-Use-Schleife aus: Claude entscheidet, welches Werkzeug
benutzt wird -> wir führen es lokal aus -> Ergebnis zurück an Claude ->
solange, bis Claude fertig ist.

Sicherheit: Gefährliche/irreversible Aktionen werden vor der Ausführung
über einen Bestätigungs-Callback abgefragt.
"""

from __future__ import annotations

import json
import platform
from dataclasses import dataclass, field
from typing import Callable, Optional

try:
    import anthropic
except ImportError as e:  # pragma: no cover
    raise SystemExit(
        "Das Paket 'anthropic' fehlt. Bitte installieren:\n"
        "    pip install anthropic\n"
    ) from e


# ----------------------------------------------------------------------------
# Werkzeug-Definition
# ----------------------------------------------------------------------------
@dataclass
class Tool:
    """Ein Werkzeug, das Jarvis benutzen kann.

    name         : eindeutiger Name (z.B. "run_shell")
    description  : sagt Claude, WANN das Werkzeug benutzt werden soll
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
        return {
            "name": self.name,
            "description": self.description,
            "input_schema": self.input_schema,
        }


# ----------------------------------------------------------------------------
# Standard-Systemprompt (Jarvis-Persönlichkeit)
# ----------------------------------------------------------------------------
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


# ----------------------------------------------------------------------------
# Das Gehirn
# ----------------------------------------------------------------------------
@dataclass
class Jarvis:
    tools: list[Tool]
    api_key: Optional[str] = None
    model: str = "claude-opus-4-8"
    platform_name: str = field(default_factory=lambda: platform.system())
    system_prompt: Optional[str] = None
    max_tokens: int = 4096
    # Callback zur Bestätigung gefährlicher Aktionen: (tool, params) -> bool
    confirm: Callable[[Tool, dict], bool] = lambda tool, params: True
    # Callback, um dem Nutzer live zu zeigen, was gerade passiert
    on_status: Callable[[str], None] = lambda msg: None

    def __post_init__(self) -> None:
        # anthropic() findet den Key aus ANTHROPIC_API_KEY oder ant-Profil,
        # wenn api_key None ist.
        self._client = anthropic.Anthropic(api_key=self.api_key) if self.api_key \
            else anthropic.Anthropic()
        self._by_name = {t.name: t for t in self.tools}
        self._messages: list[dict] = []
        if self.system_prompt is None:
            self.system_prompt = default_system_prompt(
                self.platform_name, [t.name for t in self.tools]
            )

    # -- interne Werkzeug-Ausführung -------------------------------------
    def _run_tool(self, name: str, params: dict) -> tuple[str, bool]:
        tool = self._by_name.get(name)
        if tool is None:
            return f"Unbekanntes Werkzeug: {name}", True
        if tool.dangerous and not self.confirm(tool, params):
            return "Abgebrochen: Der Nutzer hat diese Aktion nicht bestätigt.", True
        try:
            self.on_status(f"[Werkzeug] {name}({json.dumps(params, ensure_ascii=False)})")
            result = tool.handler(params)
            if result is None:
                result = "(kein Rückgabewert)"
            # Sehr lange Ausgaben kürzen, damit das Kontextfenster nicht explodiert
            if len(result) > 15000:
                result = result[:15000] + "\n... (Ausgabe gekürzt)"
            return result, False
        except Exception as exc:  # noqa: BLE001 - Fehler an Claude zurückgeben
            return f"Fehler bei der Ausführung: {exc}", True

    # -- öffentliche API --------------------------------------------------
    def ask(self, user_text: str) -> str:
        """Eine Nutzer-Eingabe verarbeiten und die finale Antwort zurückgeben."""
        self._messages.append({"role": "user", "content": user_text})
        api_tools = [t.to_api() for t in self.tools]

        # Tool-Use-Schleife
        for _ in range(25):  # harte Obergrenze gegen Endlosschleifen
            response = self._client.messages.create(
                model=self.model,
                max_tokens=self.max_tokens,
                system=self.system_prompt,
                thinking={"type": "adaptive"},
                tools=api_tools,
                messages=self._messages,
            )
            self._messages.append({"role": "assistant", "content": response.content})

            if response.stop_reason == "refusal":
                return "Diese Anfrage kann ich leider nicht bearbeiten."

            if response.stop_reason != "tool_use":
                # Fertig – finalen Text einsammeln
                return "".join(
                    block.text for block in response.content
                    if getattr(block, "type", None) == "text"
                ).strip() or "(keine Antwort)"

            # Alle angeforderten Werkzeuge ausführen, Ergebnisse sammeln
            tool_results = []
            for block in response.content:
                if getattr(block, "type", None) != "tool_use":
                    continue
                output, is_error = self._run_tool(block.name, block.input or {})
                tool_results.append({
                    "type": "tool_result",
                    "tool_use_id": block.id,
                    "content": output,
                    "is_error": is_error,
                })
            self._messages.append({"role": "user", "content": tool_results})

        return "Ich habe zu viele Schritte gebraucht und breche hier ab, Sir."

    def reset(self) -> None:
        """Gesprächsverlauf löschen (neuer Kontext)."""
        self._messages.clear()
