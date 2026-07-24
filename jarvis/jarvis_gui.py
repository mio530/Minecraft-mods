#!/usr/bin/env python3
"""
JARVIS – Grafische Oberfläche (Desktop)
=======================================

Ein Fenster, in dem du Jarvis SOWOHL TIPPEN als auch per STIMME steuern kannst.
Funktioniert mit beiden Gehirnen:
  • KOSTENLOS  (Ollama / Groq)  – Standard
  • Claude     (kostenpflichtig) – wenn ANTHROPIC_API_KEY gesetzt ist

Starten:
    pip install openai            # für die kostenlose KI
    # optional für Sprache:
    pip install SpeechRecognition pyttsx3 pyaudio
    python jarvis_gui.py

Bedienung:
    • Text eintippen + Enter (oder "Senden")   -> Tippen
    • 🎤-Knopf drücken und sprechen             -> Stimme
    • Haken "Antwort vorlesen"                  -> Sprachausgabe an/aus

Hinweis: Die GUI ist für den Desktop (Windows / Linux / macOS). Auf Android
nutze bitte die Konsolen-Versionen (jarvis_free.py / jarvis_android.py).
"""

from __future__ import annotations

import os
import platform
import threading
import time

import tkinter as tk
from tkinter import messagebox, ttk

from tools_base import Tool
from tools_power import is_unrestricted
from memory import Memory


# ----------------------------------------------------------------------------
# Plattform-Werkzeuge laden
# ----------------------------------------------------------------------------
def load_tools_and_label():
    system = platform.system().lower()
    is_android = "ANDROID_ROOT" in os.environ or "com.termux" in os.environ.get("PREFIX", "")
    if is_android:
        from tools_android import android_tools
        return android_tools(), "Android (Termux)"
    if system == "windows":
        from tools_windows import windows_tools
        return windows_tools(), "Windows PC"
    if system == "darwin":
        from tools_linux import linux_tools  # macOS nutzt die Unix-Werkzeuge
        return linux_tools(), "macOS"
    from tools_linux import linux_tools
    return linux_tools(), "Linux Desktop"


# ----------------------------------------------------------------------------
# Farben / Theme (Iron-Man-ish)
# ----------------------------------------------------------------------------
BG = "#0d1117"
BG2 = "#161b22"
FG = "#e6edf3"
ACCENT = "#38bdf8"       # Jarvis-Cyan
USER_COL = "#7ee787"
JARVIS_COL = "#38bdf8"
SYS_COL = "#8b949e"
WARN_COL = "#f0883e"


class JarvisGUI:
    def __init__(self, root: tk.Tk):
        self.root = root
        self.brain = None
        self.brain_lock = threading.Lock()  # serialisiert ask() + TTS
        self.busy = False

        # Freihändiger Gesprächsmodus
        self.convo_active = False
        self.convo_stop = threading.Event()
        self._convo_greet = False

        # Wake-Word ("Hey Jarvis") -> automatisch in den Gesprächsmodus
        self.wake_stop = threading.Event()
        self.mic_lock = threading.Lock()  # nur ein Thread nutzt das Mikrofon

        self.tools, self.label = load_tools_and_label()
        self.memory = Memory()  # dauerhaftes Gedächtnis (~/.jarvis/memory.json)

        # Sprache (nur Desktop)
        self.voice = None
        try:
            from voice import Voice
            self.voice = Voice(wake_language="de-DE")
        except Exception:
            self.voice = None

        self._build_ui()
        self._build_brain()  # baut das Standard-Gehirn (kostenlos)

    # ------------------------------------------------------------------ UI
    def _build_ui(self) -> None:
        self.root.title("J.A.R.V.I.S.")
        self.root.geometry("780x620")
        self.root.configure(bg=BG)
        self.root.minsize(560, 420)

        # Kopfzeile
        top = tk.Frame(self.root, bg=BG2)
        top.pack(fill="x", side="top")
        tk.Label(top, text="◆ J.A.R.V.I.S.", bg=BG2, fg=ACCENT,
                 font=("Segoe UI", 15, "bold")).pack(side="left", padx=12, pady=8)

        tk.Label(top, text="KI:", bg=BG2, fg=SYS_COL).pack(side="left", padx=(10, 2))
        self.backend_var = tk.StringVar(value="Kostenlos (Ollama/Groq)")
        options = ["Kostenlos (Ollama/Groq)"]
        if os.environ.get("ANTHROPIC_API_KEY"):
            options.append("Claude (kostenpflichtig)")
        self.backend_box = ttk.Combobox(top, values=options, textvariable=self.backend_var,
                                        state="readonly", width=22)
        self.backend_box.pack(side="left", padx=4, pady=8)
        self.backend_box.bind("<<ComboboxSelected>>", lambda e: self._build_brain())

        self.speak_var = tk.BooleanVar(value=bool(self.voice and self.voice.voice_output))
        tk.Checkbutton(top, text="Antwort vorlesen", variable=self.speak_var,
                       bg=BG2, fg=FG, selectcolor=BG, activebackground=BG2,
                       activeforeground=FG).pack(side="right", padx=12)

        self.unrestricted_var = tk.BooleanVar(value=is_unrestricted())
        tk.Checkbutton(top, text="🔓 Vollzugriff", variable=self.unrestricted_var,
                       command=self._toggle_unrestricted, bg=BG2, fg=WARN_COL,
                       selectcolor=BG, activebackground=BG2,
                       activeforeground=WARN_COL).pack(side="right", padx=4)

        has_mic = bool(self.voice and self.voice.voice_input)
        self.wake_var = tk.BooleanVar(value=has_mic)  # bei Mikrofon standardmäßig an
        wake_cb = tk.Checkbutton(top, text="👂 „Hey Jarvis“", variable=self.wake_var,
                                 command=self._toggle_wake, bg=BG2, fg=ACCENT,
                                 selectcolor=BG, activebackground=BG2,
                                 activeforeground=ACCENT)
        wake_cb.pack(side="right", padx=4)
        if not has_mic:
            wake_cb.configure(state="disabled")

        # Chatverlauf
        chat_frame = tk.Frame(self.root, bg=BG)
        chat_frame.pack(fill="both", expand=True, padx=10, pady=(8, 4))
        self.chat = tk.Text(chat_frame, bg=BG, fg=FG, wrap="word", bd=0,
                            font=("Segoe UI", 11), state="disabled",
                            padx=10, pady=10, insertbackground=FG)
        scroll = tk.Scrollbar(chat_frame, command=self.chat.yview)
        self.chat.configure(yscrollcommand=scroll.set)
        scroll.pack(side="right", fill="y")
        self.chat.pack(side="left", fill="both", expand=True)
        self.chat.tag_config("user", foreground=USER_COL, font=("Segoe UI", 11, "bold"))
        self.chat.tag_config("jarvis", foreground=JARVIS_COL)
        self.chat.tag_config("sys", foreground=SYS_COL, font=("Segoe UI", 9, "italic"))
        self.chat.tag_config("warn", foreground=WARN_COL, font=("Segoe UI", 9))

        # Eingabezeile
        bottom = tk.Frame(self.root, bg=BG)
        bottom.pack(fill="x", side="bottom", padx=10, pady=10)

        self.convo_btn = tk.Button(bottom, text="🗣️ Gesprächsmodus", command=self.on_convo,
                                   bg=BG2, fg=ACCENT, bd=0, font=("Segoe UI", 10, "bold"),
                                   activebackground=ACCENT, activeforeground=BG, padx=8)
        self.convo_btn.pack(side="left", padx=(0, 6))
        if not (self.voice and self.voice.voice_input):
            self.convo_btn.configure(state="disabled")

        self.mic_btn = tk.Button(bottom, text="🎤", command=self.on_mic,
                                 bg=BG2, fg=ACCENT, bd=0, font=("Segoe UI", 14),
                                 activebackground=ACCENT, activeforeground=BG, width=3)
        self.mic_btn.pack(side="left", padx=(0, 6))
        if not (self.voice and self.voice.voice_input):
            self.mic_btn.configure(state="disabled")

        self.entry = tk.Entry(bottom, bg=BG2, fg=FG, bd=0, font=("Segoe UI", 12),
                              insertbackground=ACCENT)
        self.entry.pack(side="left", fill="x", expand=True, ipady=8, padx=(0, 6))
        self.entry.bind("<Return>", lambda e: self.on_send())
        self.entry.focus_set()

        self.send_btn = tk.Button(bottom, text="Senden", command=self.on_send,
                                  bg=ACCENT, fg=BG, bd=0, font=("Segoe UI", 11, "bold"),
                                  activebackground=JARVIS_COL, padx=14)
        self.send_btn.pack(side="left")

        # Statuszeile
        self.status = tk.Label(self.root, text="", bg=BG2, fg=SYS_COL, anchor="w",
                               font=("Segoe UI", 9), padx=10)
        self.status.pack(fill="x", side="bottom")

    # --------------------------------------------------------------- Gehirn
    def _build_brain(self) -> None:
        choice = "claude" if self.backend_var.get().startswith("Claude") else "free"
        try:
            if choice == "claude":
                from jarvis_core import Jarvis
                self.brain = Jarvis(
                    tools=self.tools, platform_name=self.label, memory=self.memory,
                    unrestricted=lambda: self.unrestricted_var.get(),
                    confirm=self._confirm, on_status=self._status_async,
                )
                self._add("system", f"Claude-Gehirn aktiv · {self.label}")
                self._set_status(f"Bereit · Claude · {len(self.tools)} Werkzeuge")
            else:
                from jarvis_free_core import JarvisFree, detect_backend
                be = detect_backend()
                self.brain = JarvisFree(
                    tools=self.tools, backend=be, platform_name=self.label, memory=self.memory,
                    unrestricted=lambda: self.unrestricted_var.get(),
                    confirm=self._confirm, on_status=self._status_async,
                )
                names = {"ollama": "Ollama (lokal)", "groq": "Groq (Cloud)",
                         "offline": "Offline (keine KI)"}
                self._add("system", f"Kostenloses Gehirn aktiv · {names.get(be, be)} · {self.label}")
                self._set_status(f"Bereit · {names.get(be, be)} · {len(self.tools)} Werkzeuge")
                if be == "offline":
                    self._add("warn", "Kein KI-Backend gefunden. Starte Ollama "
                                      "(ollama pull llama3.1) oder setze GROQ_API_KEY.")
        except SystemExit as exc:
            self.brain = None
            self._add("warn", str(exc))
        except Exception as exc:  # noqa: BLE001
            self.brain = None
            self._add("warn", f"Konnte KI nicht starten: {exc}")

        n = len(self.memory.items)
        if n and self.brain is not None:
            self._add("system", f"🧠 Ich erinnere mich an {n} Sache(n) über dich.")

    # -------------------------------------------------------- Chat-Ausgabe
    def _add(self, tag: str, text: str) -> None:
        prefix = {"user": "Du:  ", "jarvis": "JARVIS:  ", "system": "• ", "warn": "⚠ "}.get(tag, "")
        self.chat.configure(state="normal")
        self.chat.insert("end", prefix + text + "\n\n", tag)
        self.chat.configure(state="disabled")
        self.chat.see("end")

    def _set_status(self, text: str) -> None:
        self.status.configure(text=text)

    def _status_async(self, msg: str) -> None:
        # wird aus dem Worker-Thread gerufen
        self.root.after(0, self._set_status, msg)

    # ------------------------------------------------------ Vollzugriff
    def _toggle_unrestricted(self) -> None:
        if self.unrestricted_var.get():
            ok = messagebox.askyesno(
                "Vollzugriff aktivieren?",
                "Im Vollzugriff-Modus führt Jarvis Befehle (Shell, Code, Prozesse) "
                "ohne Nachfrage aus.\n\nDER SCHUTZ BLEIBT AKTIV:\n"
                "• System- und App-Dateien werden nie verändert oder gelöscht\n"
                "• Fremde oder wichtige Dateien werden weiterhin abgefragt\n"
                "• Neue und selbst erstellte Dateien laufen ohne Nachfrage\n\n"
                "Nur auf deinem eigenen Gerät nutzen. Wirklich aktivieren?",
                icon="warning",
            )
            if not ok:
                self.unrestricted_var.set(False)
                return
            self._add("warn", "🔓 Vollzugriff aktiv – Befehle ohne Rückfrage. "
                              "System/App-Dateien bleiben geschützt.")
        else:
            self._add("system", "🔒 Vollzugriff aus – riskante Aktionen werden wieder abgefragt.")

    # ---------------------------------------------------- Bestätigungsdialog
    def _confirm(self, tool: Tool, params: dict) -> bool:
        """Thread-sicher: fragt im Hauptthread per Popup nach.
        (Der Guard hat schon entschieden, DASS gefragt wird – z.B. bei fremden
        oder wichtigen Dateien, auch im Vollzugriff-Modus.)"""
        result = {"ok": False}
        done = threading.Event()

        def ask():
            lines = "\n".join(f"   {k} = {v}" for k, v in params.items())
            result["ok"] = messagebox.askyesno(
                "Bestätigung erforderlich",
                f"Jarvis möchte '{tool.name}' ausführen:\n\n{lines}\n\nErlauben?",
            )
            done.set()

        self.root.after(0, ask)
        done.wait()
        return result["ok"]

    # ------------------------------------------------- Wake-Word ("Hey Jarvis")
    def _toggle_wake(self) -> None:
        if self.wake_var.get():
            self._start_wake()
        else:
            self.wake_stop.set()
            self._add("system", "👂 „Hey Jarvis“ ausgeschaltet.")

    def _start_wake(self) -> None:
        if not (self.voice and self.voice.voice_input):
            self.wake_var.set(False)
            self._add("warn", "Kein Mikrofon – „Hey Jarvis“ nicht möglich.")
            return
        self.wake_stop.clear()
        threading.Thread(target=self._wake_loop, daemon=True).start()
        self._add("system", "👂 Sage „Hey Jarvis“, um das Gespräch zu starten.")

    def _wake_loop(self) -> None:
        while not self.wake_stop.is_set() and self.wake_var.get():
            # Mikrofon nur nehmen, wenn gerade nichts anderes läuft
            if self.convo_active or self.busy:
                time.sleep(0.3)
                continue
            if not self.mic_lock.acquire(blocking=False):
                time.sleep(0.2)
                continue
            try:
                text, _err = self.voice.recognize_once(timeout=5, phrase_time_limit=3)
            finally:
                self.mic_lock.release()
            if self.wake_stop.is_set():
                break
            if text and "jarvis" in text.lower():
                self.root.after(0, self._wake_triggered)
                time.sleep(0.5)

    def _wake_triggered(self) -> None:
        if self.convo_active or self.busy:
            return
        self._start_convo(greet=True)

    # ------------------------------------------------- Gesprächsmodus (Stimme)
    def on_convo(self) -> None:
        if self.convo_active:
            self._stop_convo()
        else:
            self._start_convo()

    def _start_convo(self, greet: bool = False) -> None:
        if not (self.voice and self.voice.voice_input):
            self._add("warn", "Kein Mikrofon verfügbar – Gesprächsmodus nicht möglich.")
            return
        if self.brain is None:
            self._add("warn", "Kein KI-Gehirn aktiv.")
            return
        self._convo_greet = greet
        self.convo_active = True
        self.convo_stop.clear()
        self.speak_var.set(True)  # Antworten müssen vorgelesen werden
        self.convo_btn.configure(text="■ Stoppen", bg=WARN_COL, fg=BG)
        self._set_convo_controls(active=True)
        self._add("system", "Gesprächsmodus aktiv. Sprich einfach los – "
                            "sag 'Stopp', um zu beenden.")
        if not self.voice.voice_output:
            self._add("warn", "Hinweis: Keine Sprachausgabe verfügbar – "
                              "Antworten erscheinen nur als Text.")
        threading.Thread(target=self._convo_loop, daemon=True).start()

    def _stop_convo(self) -> None:
        self.convo_stop.set()
        self._set_status("Gesprächsmodus wird beendet …")

    def _convo_loop(self) -> None:
        STOP_WORDS = ("stopp", "stop", "beenden", "gesprächsmodus aus",
                      "pause", "danke das war", "ende")
        if self._convo_greet:
            self._convo_greet = False
            with self.brain_lock:
                self.voice.tts("Ja, Sir?")
        while not self.convo_stop.is_set():
            self.root.after(0, self._set_status, "🎤 Ich höre … sprich")
            with self.mic_lock:
                text, err = self.voice.recognize_once(timeout=8, phrase_time_limit=15)
            if self.convo_stop.is_set():
                break
            if not text:
                continue  # Timeout / nichts gehört -> weiter zuhören
            low = text.lower().strip()
            if any(w in low for w in STOP_WORDS):
                self.root.after(0, self._add, "user", text)
                break
            self.root.after(0, self._add, "user", text)
            self.root.after(0, self._set_status, "Denke nach …")
            with self.brain_lock:
                answer = self.brain.ask(text)
            if self.convo_stop.is_set():
                break
            self.root.after(0, self._add, "jarvis", answer)
            self.root.after(0, self._set_status, "🔊 Antworte …")
            with self.brain_lock:
                self.voice.tts(answer)
        self.root.after(0, self._convo_finished)

    def _convo_finished(self) -> None:
        self.convo_active = False
        self.convo_btn.configure(text="🗣️ Gesprächsmodus", bg=BG2, fg=ACCENT)
        self._set_convo_controls(active=False)
        self._add("system", "Gesprächsmodus beendet.")
        self._set_status("Bereit")

    def _set_convo_controls(self, active: bool) -> None:
        """Sperrt/entsperrt die manuellen Bedienelemente während des Gesprächs."""
        state = "disabled" if active else "normal"
        self.send_btn.configure(state=state)
        self.mic_btn.configure(state=state)
        self.entry.configure(state=state)
        self.backend_box.configure(state="disabled" if active else "readonly")

    # ---------------------------------------------------------- Aktionen
    def on_send(self) -> None:
        text = self.entry.get().strip()
        if not text or self.busy or self.convo_active:
            return
        self.entry.delete(0, "end")
        self._handle(text)

    def on_mic(self) -> None:
        if self.busy or self.convo_active or not (self.voice and self.voice.voice_input):
            return
        self._set_busy(True)
        self._set_status("🎤 Höre zu … sprich jetzt")
        self.mic_btn.configure(text="●")
        threading.Thread(target=self._listen_worker, daemon=True).start()

    def _listen_worker(self) -> None:
        with self.mic_lock:
            text, err = self.voice.recognize_once()
        def done():
            self.mic_btn.configure(text="🎤")
            self._set_busy(False)
            if err:
                self._set_status(err)
            if text:
                self._handle(text)
        self.root.after(0, done)

    def _handle(self, text: str) -> None:
        if self.brain is None:
            self._add("warn", "Kein KI-Gehirn aktiv. Prüfe die Einrichtung (siehe README).")
            return
        self._add("user", text)
        self._set_busy(True)
        self._set_status("Denke nach …")
        threading.Thread(target=self._ask_worker, args=(text,), daemon=True).start()

    def _ask_worker(self, text: str) -> None:
        with self.brain_lock:
            answer = self.brain.ask(text)
        self.root.after(0, self._show_answer, answer)
        # Vorlesen (im Worker-Thread, damit die GUI nicht einfriert)
        if self.speak_var.get() and self.voice:
            with self.brain_lock:
                self.voice.tts(answer)

    def _show_answer(self, answer: str) -> None:
        self._add("jarvis", answer)
        self._set_busy(False)
        self._set_status("Bereit")

    def _set_busy(self, value: bool) -> None:
        self.busy = value
        state = "disabled" if value else "normal"
        self.send_btn.configure(state=state)
        if self.voice and self.voice.voice_input:
            self.mic_btn.configure(state=state)


def main() -> None:
    root = tk.Tk()
    app = JarvisGUI(root)
    app._add("system", "Systeme online. Guten Tag, Sir. Tippen oder 🎤 drücken.")
    if app.wake_var.get():
        root.after(600, app._start_wake)  # kurz warten, dann auf „Hey Jarvis“ hören
    root.mainloop()


if __name__ == "__main__":
    main()
