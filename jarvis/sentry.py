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


def send_photo(path: str, title: str = "⚠️ JARVIS", message: str = "Eindringling erkannt") -> None:
    """Sendet ein Foto als Anhang an den Alarm-Kanal (Handy zeigt es an)."""
    try:
        data = open(os.path.expanduser(path), "rb").read()
    except Exception:
        return
    req = urllib.request.Request(
        f"https://ntfy.sh/{get_topic()}", data=data, method="PUT",
        headers={"Filename": "intruder.jpg", "Title": title, "Message": message,
                 "Priority": "urgent", "Tags": "rotating_light,camera"},
    )
    try:
        urllib.request.urlopen(req, timeout=15)
    except Exception:
        pass


def get_voice_phrase() -> str:
    """Das gesprochene Entsperr-Wort (Standard 'entsperren')."""
    return (os.environ.get("JARVIS_UNLOCK_PHRASE")
            or _load().get("voice_phrase") or "entsperren").lower()


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
# Helligkeit + Bewegung (Dunkel-Schutz & Lebend-Check gegen Foto-Täuschung)
# ----------------------------------------------------------------------------
def _min_brightness() -> float:
    try:
        return float(os.environ.get("JARVIS_SENTRY_MIN_BRIGHTNESS", "35"))
    except ValueError:
        return 35.0


def brightness(image_path: str) -> float:
    """Mittlere Helligkeit 0..255 (klein = dunkel). 255 wenn OpenCV fehlt."""
    try:
        import cv2
    except ImportError:
        return 255.0
    img = cv2.imread(os.path.expanduser(image_path))
    if img is None:
        return 0.0
    return float(cv2.cvtColor(img, cv2.COLOR_BGR2GRAY).mean())


def is_too_dark(image_path: str) -> bool:
    return brightness(image_path) < _min_brightness()


def has_motion(capture_fn, samples: int = 4, gap: float = 0.3) -> bool:
    """True, wenn sich über ~1 Sekunde etwas bewegt (echte Person, kein Foto)."""
    try:
        import cv2
    except ImportError:
        return False
    try:
        threshold = float(os.environ.get("JARVIS_MOTION_THRESHOLD", "6"))
    except ValueError:
        threshold = 6.0
    tmp = str(Path.home() / ".jarvis" / "motion.jpg")
    prev = None
    moving = 0
    for i in range(samples):
        if capture_fn(tmp):
            return False
        img = cv2.imread(tmp, cv2.IMREAD_GRAYSCALE)
        if img is None:
            return False
        if prev is not None:
            if prev.shape != img.shape:
                img = cv2.resize(img, (prev.shape[1], prev.shape[0]))
            if cv2.absdiff(prev, img).mean() > threshold:
                moving += 1
        prev = img
        if i < samples - 1:
            time.sleep(gap)
    return moving >= 2  # Bewegung in mind. 2 Intervallen ~= 1 Sekunde


# ----------------------------------------------------------------------------
# Persönliches Bewegungsmuster (für präzisere Wiedererkennung)
# ----------------------------------------------------------------------------
MOTION_PROFILE = Path.home() / ".jarvis" / "motion_profile.json"


def _motion_series(capture_fn, samples: int, gap: float):
    """Bewegungs-Stärke zwischen aufeinanderfolgenden Bildern (Liste) oder None."""
    try:
        import cv2
    except ImportError:
        return None
    tmp = str(Path.home() / ".jarvis" / "motion.jpg")
    prev = None
    scores = []
    for i in range(samples):
        if capture_fn(tmp):
            return None
        img = cv2.imread(tmp, cv2.IMREAD_GRAYSCALE)
        if img is None:
            return None
        if prev is not None:
            if prev.shape != img.shape:
                img = cv2.resize(img, (prev.shape[1], prev.shape[0]))
            scores.append(float(cv2.absdiff(prev, img).mean()))
        prev = img
        if i < samples - 1:
            time.sleep(gap)
    return scores


def save_motion_profile(profile: dict) -> None:
    MOTION_PROFILE.parent.mkdir(parents=True, exist_ok=True)
    MOTION_PROFILE.write_text(json.dumps(profile), encoding="utf-8")


def load_motion_profile() -> dict | None:
    if MOTION_PROFILE.exists():
        try:
            return json.loads(MOTION_PROFILE.read_text(encoding="utf-8"))
        except Exception:
            return None
    return None


def enroll_motion(capture_fn) -> str:
    """Lernt an, WIE sich der Besitzer bewegt (~2,5 s Aufnahme)."""
    scores = _motion_series(capture_fn, samples=10, gap=0.25)
    if not scores:
        return "Konnte die Bewegung nicht aufzeichnen (Kamera/OpenCV vorhanden?)."
    import statistics
    profile = {
        "mean": statistics.mean(scores),
        "std": statistics.pstdev(scores),
        "n": len(scores),
    }
    save_motion_profile(profile)
    return (f"Bewegungsmuster gemerkt (Ø {profile['mean']:.1f}). Ich erkenne dich "
            f"jetzt genauer – nicht nur am Gesicht, sondern auch an der Bewegung.")


def matches_owner_motion(capture_fn) -> bool:
    """Lebendig (Bewegung vorhanden) UND – falls angelernt – passt zum Muster."""
    try:
        threshold = float(os.environ.get("JARVIS_MOTION_THRESHOLD", "6"))
    except ValueError:
        threshold = 6.0
    scores = _motion_series(capture_fn, samples=4, gap=0.3)
    if not scores:
        return False
    if sum(1 for s in scores if s > threshold) < 2:  # ~1 s Bewegung nötig
        return False
    profile = load_motion_profile()
    if not profile:
        return True  # kein Muster angelernt -> reine Lebend-Prüfung genügt
    import statistics
    live_mean = statistics.mean(scores)
    # großzügiges Band um das gelernte Mittel (nicht zu wenig, nicht extrem anders)
    lo = profile["mean"] * 0.3
    hi = profile["mean"] * 3.0 + 5
    return lo <= live_mean <= hi


def check_owner_live(capture_fn) -> bool:
    """Ist gerade der Besitzer da – hell genug, richtiges Gesicht UND passende Bewegung?"""
    tmp = str(Path.home() / ".jarvis" / "owner_probe.jpg")
    if capture_fn(tmp):
        return False
    if is_too_dark(tmp):          # zu dunkel -> unsicher, nicht auto-entsperren
        return False
    if classify_person(tmp) != "owner":
        return False
    return matches_owner_motion(capture_fn)  # ~1 s Bewegung + persönliches Muster


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
            # Im Dunkeln nicht sperren (zu unsicher) – abwarten
            if is_too_dark(self._tmp):
                strangers = 0
                self.on_status("Wächter: zu dunkel – ich sperre nicht (unsicher)")
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
