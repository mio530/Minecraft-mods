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
from tkinter import messagebox, simpledialog, ttk

from tools_base import Tool
from tools_power import is_unrestricted
from memory import Memory
import sentry


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

        # Wächter-Modus (Kamera-Überwachung + Sperre + Handy-Alarm)
        self.sentry = None
        self._lock_win = None
        self._lock_active = False
        self._lock_pw_entry = None
        self.sentry_unlock_stop = threading.Event()

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
        tk.Button(top, text="📁 Werkstatt", command=self._open_workspace, bg=BG2, fg=FG,
                  bd=0, activebackground=ACCENT, activeforeground=BG,
                  font=("Segoe UI", 10)).pack(side="left", padx=4)

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

        self.sentry_var = tk.BooleanVar(value=False)
        tk.Checkbutton(top, text="🛡️ Wächter", variable=self.sentry_var,
                       command=self._toggle_sentry, bg=BG2, fg=WARN_COL,
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

    # ---------------------------------------------------------- Werkstatt
    def _open_workspace(self) -> None:
        import workspace
        win = tk.Toplevel(self.root)
        win.title("Werkstatt – fertige Dateien")
        win.geometry("560x460")
        win.configure(bg=BG)
        tk.Label(win, text=f"📁 {workspace.workspace_dir()}", bg=BG, fg=SYS_COL,
                 font=("Segoe UI", 9)).pack(fill="x", padx=10, pady=(8, 4))
        listbox = tk.Frame(win, bg=BG)
        listbox.pack(fill="both", expand=True, padx=10, pady=6)

        def refresh():
            for w in listbox.winfo_children():
                w.destroy()
            files = workspace.list_files()
            if not files:
                tk.Label(listbox, text="(noch keine Dateien)", bg=BG, fg=SYS_COL).pack(pady=20)
                return
            for rel in files:
                row = tk.Frame(listbox, bg=BG2)
                row.pack(fill="x", pady=2)
                nver = len(workspace.list_versions(rel))
                tag = f"  ⟳ {nver}" if nver else ""
                tk.Label(row, text=rel + tag, bg=BG2, fg=FG, anchor="w",
                         font=("Segoe UI", 11)).pack(side="left", fill="x", expand=True,
                                                     padx=8, pady=6)
                btn = tk.Button(row, text="⋮", bg=BG2, fg=ACCENT, bd=0,
                                font=("Segoe UI", 14), activebackground=ACCENT,
                                activeforeground=BG, width=3)
                btn.configure(command=lambda r=rel, b=btn: self._file_menu(win, r, b, refresh))
                btn.pack(side="right", padx=4)

        self._ws_refresh = refresh
        tk.Button(win, text="Aktualisieren", command=refresh, bg=BG2, fg=FG, bd=0,
                  font=("Segoe UI", 10)).pack(pady=(0, 8))
        refresh()

    def _file_menu(self, parent, relpath, btn, refresh) -> None:
        import workspace
        m = tk.Menu(parent, tearoff=0)
        m.add_command(label="Anzeigen", command=lambda: self._view_text(
            relpath, workspace.read(relpath)))
        m.add_command(label="Frühere Varianten …",
                      command=lambda: self._view_versions(relpath, refresh))
        m.add_separator()
        m.add_command(label="Löschen", command=lambda: (workspace.delete(relpath), refresh()))
        m.post(btn.winfo_rootx(), btn.winfo_rooty() + btn.winfo_height())

    def _view_versions(self, relpath, refresh) -> None:
        import workspace
        versions = workspace.list_versions(relpath)
        win = tk.Toplevel(self.root)
        win.title(f"Frühere Varianten – {relpath}")
        win.geometry("460x360")
        win.configure(bg=BG)
        if not versions:
            tk.Label(win, text="Keine früheren Varianten vorhanden.", bg=BG, fg=SYS_COL).pack(pady=20)
            return
        tk.Label(win, text="Neueste zuerst. Auswählen und wiederherstellen oder ansehen:",
                 bg=BG, fg=SYS_COL, font=("Segoe UI", 9)).pack(fill="x", padx=10, pady=8)
        lb = tk.Listbox(win, bg=BG2, fg=FG, bd=0, font=("Consolas", 10),
                        selectbackground=ACCENT, selectforeground=BG)
        for v in versions:
            lb.insert("end", v)
        lb.pack(fill="both", expand=True, padx=10, pady=6)
        lb.selection_set(0)

        def sel():
            i = lb.curselection()
            return versions[i[0]] if i else None

        def view():
            v = sel()
            if v:
                self._view_text(f"{relpath} @ {v}", workspace.read_version(relpath, v))

        def restore():
            v = sel()
            if v and messagebox.askyesno("Wiederherstellen",
                                         f"Variante {v} für {relpath} wiederherstellen?\n"
                                         "(Der aktuelle Stand wird ebenfalls im Verlauf gesichert.)"):
                workspace.restore_version(relpath, v)
                refresh()
                win.destroy()

        bar = tk.Frame(win, bg=BG)
        bar.pack(fill="x", padx=10, pady=8)
        tk.Button(bar, text="Ansehen", command=view, bg=BG2, fg=FG, bd=0,
                  font=("Segoe UI", 10), padx=10).pack(side="left")
        tk.Button(bar, text="Wiederherstellen", command=restore, bg=ACCENT, fg=BG, bd=0,
                  font=("Segoe UI", 10, "bold"), padx=10).pack(side="right")

    def _view_text(self, title, text) -> None:
        win = tk.Toplevel(self.root)
        win.title(str(title))
        win.geometry("640x480")
        win.configure(bg=BG)
        t = tk.Text(win, bg=BG, fg=FG, wrap="none", bd=0, font=("Consolas", 10),
                    insertbackground=FG)
        t.insert("1.0", text)
        t.configure(state="disabled")
        t.pack(fill="both", expand=True, padx=8, pady=8)

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

    # ------------------------------------------------- Wächter-Modus
    def _toggle_sentry(self) -> None:
        if self.sentry_var.get():
            self._start_sentry()
        else:
            self._stop_sentry()

    def _start_sentry(self) -> None:
        from tools_face import is_enrolled
        if not is_enrolled():
            self.sentry_var.set(False)
            self._add("warn", "Der Wächter braucht dein Gesicht. Sag zuerst "
                              "„Merk dir mein Gesicht“ und aktiviere ihn dann.")
            return
        # Entsperr-Passwort sicherstellen
        if not sentry.has_password():
            pw = simpledialog.askstring(
                "Entsperr-Passwort festlegen",
                "Lege ein Passwort fest, mit dem du den PC wieder entsperrst:",
                show="*", parent=self.root)
            if not pw:
                self.sentry_var.set(False)
                return
            sentry.set_password(pw)
        # Topic/Token fürs Handy anzeigen
        topic, token = sentry.get_topic(), sentry.get_token()
        messagebox.showinfo(
            "Wächter aktiv – Handy verbinden",
            "Damit der Alarm aufs Handy kommt, abonniere in der ntfy-App das Topic:\n\n"
            f"    {topic}\n\n"
            "Für das Entsperren per Fingerabdruck auf dem Handy setze dort:\n"
            f"    JARVIS_NTFY_TOPIC={topic}\n"
            f"    JARVIS_SENTRY_TOKEN={token}\n\n"
            "und starte  jarvis_sentry_android.py .\n\n"
            f"Entsperren am PC: Passwort ODER sag laut „{sentry.get_voice_phrase()}“.")
        self.sentry_unlock_stop.clear()
        threading.Thread(
            target=sentry.listen_for_unlock,
            args=(self.sentry_unlock_stop,
                  lambda: self.root.after(0, self._remote_unlock)),
            daemon=True).start()
        self.sentry = sentry.Sentry(
            capture_fn=lambda p: __import__("tools_camera").capture(0, p),
            on_intrusion=lambda: self.root.after(0, self._lockdown),
            on_status=self._status_async,
        )
        self.sentry.start()
        self._add("system", "🛡️ Wächter aktiv. Ich sperre den PC bei einer fremden "
                            "Person und alarmiere dein Handy.")

    def _stop_sentry(self) -> None:
        if self.sentry:
            self.sentry.stop()
            self.sentry = None
        self.sentry_unlock_stop.set()
        self._add("system", "🛡️ Wächter ausgeschaltet.")

    def _virtual_geometry(self):
        """(x, y, w, h) der GESAMTEN Bildschirmfläche über alle Monitore."""
        import sys
        if sys.platform.startswith("win"):
            try:
                import ctypes
                u = ctypes.windll.user32
                x, y = u.GetSystemMetrics(76), u.GetSystemMetrics(77)  # X/Y VIRTUALSCREEN
                w, h = u.GetSystemMetrics(78), u.GetSystemMetrics(79)  # CX/CY VIRTUALSCREEN
                if w and h:
                    return x, y, w, h
            except Exception:
                pass
        try:  # X11: virtueller Wurzel-Bereich umfasst alle Monitore
            w, h = self.root.winfo_vrootwidth(), self.root.winfo_vrootheight()
            x, y = self.root.winfo_vrootx(), self.root.winfo_vrooty()
            if w and h:
                return x, y, w, h
        except Exception:
            pass
        return 0, 0, self.root.winfo_screenwidth(), self.root.winfo_screenheight()

    def _lockdown(self) -> None:
        if self._lock_win is not None:
            return
        threading.Thread(
            target=sentry.send_alarm,
            args=("⚠️ JARVIS ALARM",
                  "Unbekannte Person am PC erkannt – Rechner wurde gesperrt."),
            daemon=True).start()
        if os.environ.get("JARVIS_OS_LOCK", "").lower() in ("1", "true", "yes"):
            sentry.os_lock()

        win = tk.Toplevel(self.root)
        win.configure(bg="#1a0000")
        win.protocol("WM_DELETE_WINDOW", lambda: None)
        try:
            win.overrideredirect(True)  # randlos, nicht verschieb-/schließbar
        except Exception:
            pass
        win.attributes("-topmost", True)
        # ALLE Monitore abdecken (2. und 3. Monitor inklusive)
        x, y, w, h = self._virtual_geometry()
        win.geometry(f"{w}x{h}+{x}+{y}")
        for seq in ("<Alt-F4>", "<Alt-Tab>", "<Escape>", "<Super_L>", "<Super_R>",
                    "<Control-w>", "<Control-q>"):
            win.bind_all(seq, lambda e: "break")
        win.bind("<FocusOut>", lambda e: self._reassert_lock())

        # Inhalt zentriert auf dem HAUPTmonitor (nicht in der Mitte aller Monitore)
        pw_scr, ph_scr = self.root.winfo_screenwidth(), self.root.winfo_screenheight()
        cx, cy = (0 - x) + pw_scr // 2, (0 - y) + ph_scr // 2
        frame = tk.Frame(win, bg="#1a0000")
        frame.place(x=cx, y=cy, anchor="center")

        tk.Label(frame, text="🔒 GESPERRT", bg="#1a0000", fg="#ff4444",
                 font=("Segoe UI", 40, "bold")).pack(pady=(0, 10))
        tk.Label(frame, text="Unbefugter Zugriff erkannt.\nEntsperrt automatisch, wenn "
                             "ich DICH (in Bewegung) erkenne –\noder: Passwort eingeben, "
                             "„entsperren“ sagen, Fingerabdruck am Handy.",
                 bg="#1a0000", fg=FG, font=("Segoe UI", 14), justify="center").pack(pady=10)
        pw_entry = tk.Entry(frame, show="*", font=("Segoe UI", 16), justify="center",
                            bg=BG2, fg=FG, insertbackground=ACCENT)
        pw_entry.pack(pady=16, ipady=6)
        err_lbl = tk.Label(frame, text="", bg="#1a0000", fg="#ff4444")
        err_lbl.pack()

        def try_unlock():
            if sentry.verify_password(pw_entry.get()):
                self._do_unlock()
            else:
                err_lbl.configure(text="Falsches Passwort.")
                pw_entry.delete(0, "end")

        pw_entry.bind("<Return>", lambda e: try_unlock())
        tk.Button(frame, text="Entsperren", command=try_unlock, bg=ACCENT, fg=BG,
                  bd=0, font=("Segoe UI", 12, "bold"), padx=16, pady=4).pack(pady=8)

        self._lock_win = win
        self._lock_pw_entry = pw_entry
        self._lock_active = True
        try:
            win.grab_set_global()
        except Exception:
            win.grab_set()
        pw_entry.focus_force()
        self._reassert_lock()
        # Foto-/Live-Feed ans Handy + Sprach-Entsperrung
        threading.Thread(target=self._lock_camera_loop, daemon=True).start()
        if self.voice and self.voice.voice_input:
            threading.Thread(target=self._lock_voice_loop, daemon=True).start()

    def _lock_camera_loop(self) -> None:
        """Live-Feed ans Handy + Auto-Entsperren, wenn der Besitzer (lebendig) zurückkommt."""
        import tools_camera
        capture_fn = lambda p: tools_camera.capture(0, p)
        path = os.path.expanduser("~/.jarvis/photos/intruder.jpg")
        first = True
        auto = os.environ.get("JARVIS_AUTO_UNLOCK", "1").lower() not in ("0", "false", "no")
        while self._lock_active:
            if capture_fn(path) is None:
                sentry.send_photo(path, "⚠️ JARVIS",
                                  "Eindringling" if first else "Live-Bild vom PC")
                first = False
            # Auto-Entsperren: Besitzer erkannt UND ~1 Sek. Bewegung (kein Foto)
            if auto and self._lock_active and sentry.check_owner_live(capture_fn):
                self.root.after(0, self._auto_unlock)
                return
            for _ in range(6):  # ~3 s Pause (check_owner_live dauert selbst ~1-2 s)
                if not self._lock_active:
                    return
                time.sleep(0.5)

    def _auto_unlock(self) -> None:
        if self._lock_win is not None:
            self._add("system", "🔓 Willkommen zurück – ich habe dich erkannt und entsperrt.")
            self._do_unlock()

    def _lock_voice_loop(self) -> None:
        """Hört auf das Entsperr-Wort, während gesperrt ist."""
        phrase = sentry.get_voice_phrase()
        while self._lock_active:
            if not self.mic_lock.acquire(blocking=False):
                time.sleep(0.3)
                continue
            try:
                text, _err = self.voice.recognize_once(timeout=5, phrase_time_limit=3)
            finally:
                self.mic_lock.release()
            if not self._lock_active:
                return
            if text and phrase in text.lower():
                self.root.after(0, self._voice_unlock)

    def _voice_unlock(self) -> None:
        if self._lock_win is not None:
            self._add("system", "🔓 Per Stimme entsperrt.")
            self._do_unlock()

    def _reassert_lock(self) -> None:
        """Hält die Sperre oben und im Fokus, solange sie aktiv ist."""
        if not getattr(self, "_lock_active", False) or self._lock_win is None:
            return
        try:
            self._lock_win.lift()
            self._lock_win.attributes("-topmost", True)
            self._lock_win.focus_force()
            try:
                self._lock_win.grab_set_global()
            except Exception:
                self._lock_win.grab_set()
            if getattr(self, "_lock_pw_entry", None) is not None:
                self._lock_pw_entry.focus_set()
        except Exception:
            pass
        self.root.after(700, self._reassert_lock)

    def _remote_unlock(self) -> None:
        if self._lock_win is not None:
            self._add("system", "🔓 Entsperrt per Fingerabdruck vom Handy.")
            self._do_unlock()

    def _do_unlock(self) -> None:
        self._lock_active = False  # stoppt die Reassert-Schleife
        if self._lock_win is not None:
            try:
                self._lock_win.grab_release()
                self._lock_win.destroy()
            except Exception:
                pass
            self._lock_win = None
        if self.sentry:
            self.sentry.resume()
        self._set_status("Entsperrt · Wächter läuft weiter")

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
            if self.convo_active or self.busy or self._lock_active:
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
