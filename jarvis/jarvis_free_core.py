"""
JARVIS – Kostenloses Gehirn
===========================

Nutzt statt Claude eine KOSTENLOSE KI über die OpenAI-kompatible Schnittstelle.
Zwei Backends, beide gratis:

  1. OLLAMA  – ein KI-Modell, das LOKAL auf deinem Gerät läuft.
               100% kostenlos, offline, privat. Braucht etwas Hardware.
               Installation: https://ollama.com  ->  danach:  ollama pull llama3.1
  2. GROQ    – kostenloser Cloud-Tarif, sehr schnell, kein lokales Modell nötig.
               Kostenlosen Key holen: https://console.groq.com  (nur Anmeldung)

Zusätzlich gibt es einen OFFLINE-NOTFALLMODUS ganz ohne KI: ein einfacher
Befehls-Interpreter, der ein paar direkte Kommandos (Systeminfo, Akku,
Taschenlampe, "öffne ...") auf die Werkzeuge abbildet.

Konfiguration über Umgebungsvariablen:
  JARVIS_BACKEND   auto (Standard) | ollama | groq | offline
  OLLAMA_HOST      Standard http://localhost:11434
  OLLAMA_MODEL     Standard llama3.1
  GROQ_API_KEY     dein kostenloser Groq-Key
  GROQ_MODEL       Standard llama-3.3-70b-versatile
"""

from __future__ import annotations

import json
import os
import platform
import urllib.request
from dataclasses import dataclass, field
from typing import Callable, Optional

from tools_base import Tool, default_system_prompt


# ----------------------------------------------------------------------------
# Backend-Erkennung
# ----------------------------------------------------------------------------
def _ollama_reachable(host: str) -> bool:
    try:
        urllib.request.urlopen(host + "/api/tags", timeout=2)
        return True
    except Exception:
        return False


def detect_backend() -> str:
    """Ermittelt automatisch das beste verfügbare kostenlose Backend."""
    choice = os.environ.get("JARVIS_BACKEND", "auto").lower()
    if choice in ("ollama", "groq", "offline"):
        return choice
    # auto
    if os.environ.get("GROQ_API_KEY"):
        return "groq"
    if _ollama_reachable(os.environ.get("OLLAMA_HOST", "http://localhost:11434")):
        return "ollama"
    return "offline"


# ----------------------------------------------------------------------------
# Das kostenlose Gehirn
# ----------------------------------------------------------------------------
@dataclass
class JarvisFree:
    tools: list[Tool]
    backend: Optional[str] = None
    platform_name: str = field(default_factory=lambda: platform.system())
    system_prompt: Optional[str] = None
    max_tokens: int = 2048
    memory: object = None  # optionales Langzeitgedächtnis (memory.Memory)
    confirm: Callable[[Tool, dict], bool] = lambda tool, params: True
    on_status: Callable[[str], None] = lambda msg: None

    def __post_init__(self) -> None:
        self.backend = self.backend or detect_backend()
        self._messages: list[dict] = []
        if self.system_prompt is None:
            self.system_prompt = default_system_prompt(
                self.platform_name, [t.name for t in self.tools]
            )
        self._base_system = self.system_prompt
        if self.memory is not None:
            from memory import memory_tools, MEMORY_INSTRUCTION
            self.tools = list(self.tools) + memory_tools(self.memory)
            self._base_system = self.system_prompt + "\n\n" + MEMORY_INSTRUCTION
        self._by_name = {t.name: t for t in self.tools}
        self._model = None
        if self.backend in ("ollama", "groq"):
            self._setup_openai_client()

    def _system_now(self) -> str:
        """Aktueller Systemprompt inkl. gemerkter Nutzer-Fakten."""
        if self.memory is not None:
            block = self.memory.as_prompt_block()
            if block:
                return self._base_system + "\n\n" + block
        return self._base_system

    def _setup_openai_client(self) -> None:
        try:
            from openai import OpenAI
        except ImportError as e:
            raise SystemExit(
                "Für die kostenlose KI wird das Paket 'openai' gebraucht:\n"
                "    pip install openai\n"
                "(Es spricht sowohl mit Ollama als auch mit Groq.)"
            ) from e

        if self.backend == "groq":
            self._client = OpenAI(
                base_url="https://api.groq.com/openai/v1",
                api_key=os.environ.get("GROQ_API_KEY", ""),
            )
            self._model = os.environ.get("GROQ_MODEL", "llama-3.3-70b-versatile")
        else:  # ollama
            host = os.environ.get("OLLAMA_HOST", "http://localhost:11434")
            self._client = OpenAI(base_url=host + "/v1", api_key="ollama")
            self._model = os.environ.get("OLLAMA_MODEL", "llama3.1")

    # -- Werkzeug-Ausführung ---------------------------------------------
    def _run_tool(self, name: str, params: dict) -> str:
        tool = self._by_name.get(name)
        if tool is None:
            return f"Unbekanntes Werkzeug: {name}"
        if tool.dangerous and not self.confirm(tool, params):
            return "Abgebrochen: Der Nutzer hat diese Aktion nicht bestätigt."
        try:
            self.on_status(f"[Werkzeug] {name}({json.dumps(params, ensure_ascii=False)})")
            result = tool.handler(params) or "(kein Rückgabewert)"
            if len(result) > 8000:
                result = result[:8000] + "\n... (gekürzt)"
            return result
        except Exception as exc:  # noqa: BLE001
            return f"Fehler bei der Ausführung: {exc}"

    # -- öffentliche API --------------------------------------------------
    def ask(self, user_text: str) -> str:
        if self.backend == "offline":
            return self._offline_reply(user_text)

        self._messages.append({"role": "user", "content": user_text})
        openai_tools = [t.to_openai() for t in self.tools]

        for _ in range(15):
            try:
                response = self._client.chat.completions.create(
                    model=self._model,
                    messages=[{"role": "system", "content": self._system_now()}] + self._messages,
                    tools=openai_tools,
                    max_tokens=self.max_tokens,
                )
            except Exception as exc:  # noqa: BLE001
                return f"Fehler beim KI-Aufruf ({self.backend}): {exc}"

            msg = response.choices[0].message

            if not msg.tool_calls:
                text = (msg.content or "").strip()
                self._messages.append({"role": "assistant", "content": text})
                return text or "(keine Antwort)"

            # Assistant-Nachricht mit Werkzeugaufrufen speichern
            self._messages.append({
                "role": "assistant",
                "content": msg.content or "",
                "tool_calls": [
                    {
                        "id": tc.id,
                        "type": "function",
                        "function": {"name": tc.function.name, "arguments": tc.function.arguments},
                    }
                    for tc in msg.tool_calls
                ],
            })
            # Jedes Werkzeug ausführen, Ergebnis zurückgeben
            for tc in msg.tool_calls:
                try:
                    args = json.loads(tc.function.arguments or "{}")
                except json.JSONDecodeError:
                    args = {}
                output = self._run_tool(tc.function.name, args)
                self._messages.append({
                    "role": "tool",
                    "tool_call_id": tc.id,
                    "content": output,
                })

        return "Ich habe zu viele Schritte gebraucht und breche hier ab, Sir."

    def reset(self) -> None:
        self._messages.clear()

    # -- Offline-Notfallmodus (ohne KI) ----------------------------------
    def _offline_reply(self, text: str) -> str:
        """Sehr einfacher Befehls-Interpreter ohne KI.
        Erkennt nur ein paar direkte Kommandos anhand von Schlüsselwörtern."""
        t = text.lower().strip()

        def has(tool: str) -> bool:
            return tool in self._by_name

        if any(w in t for w in ("systeminfo", "system info", "geräteinfo", "speicher")) and has("system_info"):
            return self._run_tool("system_info", {})
        if "akku" in t and has("battery"):
            return self._run_tool("battery", {})
        if "standort" in t and has("location"):
            return self._run_tool("location", {})
        if "taschenlampe" in t and has("torch"):
            return self._run_tool("torch", {"on": "aus" not in t and "off" not in t})
        if t.startswith(("öffne ", "offne ", "open ")) and has("open_app"):
            target = text.split(" ", 1)[1].strip()
            return self._run_tool("open_app", {"target": target})
        if any(w in t for w in ("führe aus", "fuehre aus", "shell", "befehl:")) and has("run_shell"):
            cmd = text.split(":", 1)[-1].strip() if ":" in text else text
            return self._run_tool("run_shell", {"command": cmd})

        return (
            "Offline-Modus (keine KI aktiv). Ich verstehe nur einfache Kommandos wie "
            "'Systeminfo', 'Akku', 'Standort', 'Taschenlampe an/aus', 'öffne <Programm>' "
            "oder 'Befehl: <shell>'. Für echtes Verständnis starte Ollama oder setze "
            "einen GROQ_API_KEY (siehe README)."
        )
