"""
JARVIS – Wächter-Modus (Sentry)  [Desktop: Windows / Linux]
===========================================================

Wenn aktiv, beobachtet Jarvis im Hintergrund die Webcam. Erkennt er eine
FREMDE Person (nicht den angelernten Besitzer), dann:
  1. sperrt er den PC (Jarvis-Sperrfenster, Vollbild)
  2. schickt einen ALARM als Push-Benachrichtigung aufs Handy (über ntfy.sh)
  3. wartet aufs Entsperren – per Passwort am PC ODER per Fingerabdruck am Handy

Verbindung PC <-> Handy: ntfy.sh (kostenlos, kein Server, kein Account).
  • Alarm-Kanal :  https://ntfy.sh/<topic>        (Handy abonniert -> Push)
  • Steuer-Kanal:  https://ntfy.sh/<topic>-ctl     (Handy -> PC, zum Entsperren)

Damit PC und Handy zusammenfinden, müssen auf BEIDEN Geräten dasselbe
Topic + Token gesetzt sein (Umgebungsvariablen oder ~/.jarvis/sentry.json):
  JARVIS_NTFY_TOPIC   (z.B. jarvis-a1b2c3)
  JARVIS_SENTRY_TOKEN (Geheimnis, damit nicht jeder entsperren kann)

Ehrliche Grenze: Das Jarvis-Sperrfenster ist eine App-Sperre, kein echter
Betriebssystem-Login. Für maximale Sicherheit zusätzlich JARVIS_OS_LOCK=1
setzen – dann wird auch der echte OS-Sperrbildschirm ausgelöst (der nur mit
deinem Konto-Passwort am PC aufgeht; die App-Entsperrung entfernt dann nur das
Jarvis-Fenster).
"""

from __future__ import annotations

import hashlib
import json
import os
import platform
import secrets
import subprocess
import threading
import time
import urllib.request
from pathlib import Path

CONFIG = Path.home() / ".jarvis" / "sentry.json"
IS_WIN = platform.system().lower() == "windows"


# ----------------------------------------------------------------------------
# Konfiguration (Topic, Token, Passwort-Hash)
# ----------------------------------------------------------------------------
def _load() -> dict:
    if CONFIG.exists():
        try:
            return json.loads(CONFIG.read_text(encoding="utf-8"))
        except Exception:
            return {}
    return {}


def _save(cfg: dict) -> None:
    CONFIG.parent.mkdir(parents=True, exist_ok=True)
    CONFIG.write_text(json.dumps(cfg, indent=2), encoding="utf-8")


def get_topic() -> str:
    env = os.environ.get("JARVIS_NTFY_TOPIC")
    if env:
        return env
    cfg = _load()
    if not cfg.get("topic"):
        cfg["topic"] = "jarvis-" + secrets.token_hex(4)
        _save(cfg)
    return cfg["topic"]


def get_token() -> str:
    env = os.environ.get("JARVIS_SENTRY_TOKEN")
    if env:
        return env
    cfg = _load()
    if not cfg.get("token"):
        cfg["token"] = secrets.token_hex(8)
        _save(cfg)
    return cfg["token"]


def has_password() -> bool:
    return bool(_load().get("pw_hash"))


def set_password(pw: str) -> None:
    cfg = _load()
    cfg["pw_hash"] = hashlib.sha256(pw.encode()).hexdigest()
    _save(cfg)


def verify_password(pw: str) -> bool:
    h = _load().get("pw_hash")
    return bool(h) and hashlib.sha256(pw.encode()).hexdigest() == h


# ----------------------------------------------------------------------------
# ntfy: Alarm senden / auf Entsperr-Signal hören / Entsperren senden
# ----------------------------------------------------------------------------
def send_alarm(title: str, message: str) -> None:
    topic = get_topic()
    req = urllib.request.Request(
        f"https://ntfy.sh/{topic}", data=message.encode("utf-8"), method="POST",
        headers={"Title": title, "Priority": "urgent", "Tags": "rotating_light,warning"},
    )
    try:
        urllib.request.urlopen(req, timeout=10)
    except Exception:
        pass


def send_unlock() -> None:
    """Vom Handy aufgerufen: schickt das (authentifizierte) Entsperr-Signal an den PC."""
    topic = get_topic() + "-ctl"
    req = urllib.request.Request(
        f"https://ntfy.sh/{topic}", data=f"UNLOCK {get_token()}".encode(), method="POST",
    )
    urllib.request.urlopen(req, timeout=10)


def listen_for_unlock(stop_event: threading.Event, on_unlock) -> None:
    """PC-seitig: hört auf den Steuer-Kanal und ruft on_unlock() bei gültigem Signal."""
    topic = get_topic() + "-ctl"
    expected = f"UNLOCK {get_token()}"
    url = f"https://ntfy.sh/{topic}/json"
    while not stop_event.is_set():
        try:
            with urllib.request.urlopen(url, timeout=60) as resp:
                for line in resp:
                    if stop_event.is_set():
                        return
                    try:
                        obj = json.loads(line.decode("utf-8"))
                    except Exception:
                        continue
                    if obj.get("event") == "message" and obj.get("message", "").strip() == expected:
                        on_unlock()
        except Exception:
            time.sleep(3)  # Netzwerkfehler -> kurz warten, erneut verbinden


# ----------------------------------------------------------------------------
# OS-Sperre (optional, echter Sperrbildschirm)
# ----------------------------------------------------------------------------
def os_lock() -> None:
    try:
        if IS_WIN:
            subprocess.Popen("rundll32.exe user32.dll,LockWorkStation", shell=True)
        else:
            for cmd in ("loginctl lock-session", "xdg-screensaver lock",
                        "gnome-screensaver-command -l", "dm-tool lock", "xflock4"):
                if subprocess.call(cmd, shell=True,
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL) == 0:
                    break
    except Exception:
        pass


# ----------------------------------------------------------------------------
# Gesichtserkennung: Besitzer vs. Fremder
# ----------------------------------------------------------------------------
def detect_faces(image_path: str) -> int:
    """Zählt Gesichter im Bild (lokal, per OpenCV Haar-Cascade)."""
    try:
        import cv2
    except ImportError:
        return -1  # OpenCV fehlt
    img = cv2.imread(os.path.expanduser(image_path))
    if img is None:
        return 0
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    cascade = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml")
    faces = cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(60, 60))
    return len(faces)


def classify_person(image_path: str) -> str:
    """Gibt 'owner' | 'stranger' | 'nobody' | 'unknown' zurück (per Vision-Modell)."""
    from tools_face import is_enrolled, _owner_img
    from tools_camera import vision_query
    if not is_enrolled():
        return "unknown"
    prompt = (
        "Bild 1 ist das Referenzfoto des Besitzers. Bild 2 ist eine Live-Aufnahme. "
        "Antworte NUR mit EINEM Wort: 'OWNER' wenn Bild 2 dieselbe Person wie Bild 1 "
        "zeigt, 'STRANGER' wenn es eine ANDERE Person ist, 'NOBODY' wenn auf Bild 2 "
        "kein Gesicht erkennbar ist."
    )
    res = vision_query([str(_owner_img()), os.path.expanduser(image_path)], prompt).lower()
    if "owner" in res:
        return "owner"
    if "stranger" in res:
        return "stranger"
    if "nobody" in res:
        return "nobody"
    return "unknown"


# ----------------------------------------------------------------------------
# Der Wächter
# ----------------------------------------------------------------------------
class Sentry:
    def __init__(self, capture_fn, on_intrusion, interval: float = 4.0,
                 threshold: int = 2, on_status=lambda m: None):
        self.capture_fn = capture_fn        # (path) -> fehlertext|None
        self.on_intrusion = on_intrusion    # wird bei Fremdem aufgerufen
        self.interval = interval
        self.threshold = threshold
        self.on_status = on_status
        self._stop = threading.Event()
        self._paused = threading.Event()    # gesetzt = pausiert (während Sperre)
        self._tmp = str(Path.home() / ".jarvis" / "sentry_probe.jpg")

    def start(self) -> None:
        self._stop.clear()
        threading.Thread(target=self._loop, daemon=True).start()

    def stop(self) -> None:
        self._stop.set()

    def pause(self) -> None:
        self._paused.set()

    def resume(self) -> None:
        self._paused.clear()

    def _loop(self) -> None:
        strangers = 0
        while not self._stop.is_set():
            if self._paused.is_set():
                time.sleep(0.5)
                continue
            err = self.capture_fn(self._tmp)
            if err:
                self.on_status(f"Wächter: {err}")
                time.sleep(self.interval)
                continue
            n = detect_faces(self._tmp)
            if n <= 0:
                strangers = 0
                time.sleep(self.interval)
                continue
            person = classify_person(self._tmp)
            if person in ("owner", "nobody"):
                strangers = 0
            elif person == "stranger":
                strangers += 1
                self.on_status(f"Wächter: unbekannte Person ({strangers}/{self.threshold})")
                if strangers >= self.threshold:
                    strangers = 0
                    self.pause()
                    self.on_intrusion()
            time.sleep(self.interval)
