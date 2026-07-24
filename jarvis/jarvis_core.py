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

# Tool + Systemprompt liegen in tools_base (ohne Anthropic-Abhängigkeit),
# damit die kostenlose Version denselben Werkzeug-Code nutzen kann.
from tools_base import Tool, default_system_prompt  # noqa: F401 (re-export)
from tools_power import is_unrestricted
from safety import Guard

try:
    import anthropic
except ImportError as e:  # pragma: no cover
    raise SystemExit(
        "Das Paket 'anthropic' fehlt. Bitte installieren:\n"
        "    pip install anthropic\n"
    ) from e


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
    # Optionales Langzeitgedächtnis (memory.Memory); passt Verhalten dauerhaft an
    memory: object = None
    # Vollzugriff-Modus? (Schutz für System/App-Dateien bleibt trotzdem aktiv)
    unrestricted: Callable[[], bool] = is_unrestricted
    # Callback zur Bestätigung gefährlicher Aktionen: (tool, params) -> bool
    confirm: Callable[[Tool, dict], bool] = lambda tool, params: True
    # Callback, um dem Nutzer live zu zeigen, was gerade passiert
    on_status: Callable[[str], None] = lambda msg: None

    def __post_init__(self) -> None:
        # anthropic() findet den Key aus ANTHROPIC_API_KEY oder ant-Profil,
        # wenn api_key None ist.
        self._client = anthropic.Anthropic(api_key=self.api_key) if self.api_key \
            else anthropic.Anthropic()
        self._messages: list[dict] = []
        if self.system_prompt is None:
            self.system_prompt = default_system_prompt(
                self.platform_name, [t.name for t in self.tools]
            )
        # Gedächtnis anhängen: Merk-Werkzeuge + Anweisung im Systemprompt
        self._base_system = self.system_prompt
        if self.memory is not None:
            from memory import memory_tools, MEMORY_INSTRUCTION
            self.tools = list(self.tools) + memory_tools(self.memory)
            self._base_system = self.system_prompt + "\n\n" + MEMORY_INSTRUCTION
        self._by_name = {t.name: t for t in self.tools}
        self._guard = Guard(self.unrestricted)

    def _system_now(self) -> str:
        """Aktueller Systemprompt inkl. gemerkter Nutzer-Fakten."""
        if self.memory is not None:
            block = self.memory.as_prompt_block()
            if block:
                return self._base_system + "\n\n" + block
        return self._base_system

    # -- interne Werkzeug-Ausführung -------------------------------------
    def _run_tool(self, name: str, params: dict) -> tuple[str, bool]:
        tool = self._by_name.get(name)
        if tool is None:
            return f"Unbekanntes Werkzeug: {name}", True
        decision, reason = self._guard.decide(tool, params)
        if decision == "block":
            return f"⛔ Aus Sicherheitsgründen blockiert: {reason}", True
        if decision == "ask" and not self.confirm(tool, params):
            return "Abgebrochen: Der Nutzer hat diese Aktion nicht bestätigt.", True
        try:
            self.on_status(f"[Werkzeug] {name}({json.dumps(params, ensure_ascii=False)})")
            result = tool.handler(params)
            self._guard.record(tool, params)
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
                system=self._system_now(),
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
