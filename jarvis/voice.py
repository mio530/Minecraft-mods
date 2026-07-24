"""
Sprachein- und -ausgabe für Jarvis (Desktop: Windows & Linux).

- Ausgabe (TTS): pyttsx3 (offline), fällt auf reine Textausgabe zurück.
- Eingabe (STT): SpeechRecognition + Mikrofon (Google Web Speech API, gratis),
  fällt auf Tastatureingabe zurück.

Alles ist optional: Fehlt ein Paket oder ein Mikrofon, läuft Jarvis einfach
im reinen Textmodus weiter.
"""

from __future__ import annotations


class Voice:
    def __init__(self, wake_language: str = "de-DE", prefer_voice: bool = True):
        self.language = wake_language
        self._tts = None
        self._recognizer = None
        self._mic = None
        self.voice_output = False
        self.voice_input = False

        if prefer_voice:
            self._setup_tts()
            self._setup_stt()

    # -- Ausgabe ----------------------------------------------------------
    def _setup_tts(self) -> None:
        try:
            import pyttsx3

            self._tts = pyttsx3.init()
            self._tts.setProperty("rate", 175)
            # Versuche, eine deutsche Stimme zu wählen
            for v in self._tts.getProperty("voices"):
                if "german" in v.name.lower() or "de" in (v.id or "").lower():
                    self._tts.setProperty("voice", v.id)
                    break
            self.voice_output = True
        except Exception:
            self.voice_output = False

    def say(self, text: str) -> None:
        print(f"\nJARVIS: {text}\n")
        if self.voice_output and self._tts is not None:
            try:
                self._tts.say(text)
                self._tts.runAndWait()
            except Exception:
                pass

    # -- Eingabe ----------------------------------------------------------
    def _setup_stt(self) -> None:
        try:
            import speech_recognition as sr

            self._recognizer = sr.Recognizer()
            self._mic = sr.Microphone()  # wirft, wenn kein Mikrofon/PyAudio
            with self._mic as source:
                self._recognizer.adjust_for_ambient_noise(source, duration=0.5)
            self.voice_input = True
        except Exception:
            self.voice_input = False

    def listen(self, prompt: str = "Du: ") -> str:
        """Nimmt Sprache auf und wandelt sie in Text um.
        Fällt auf Tastatureingabe zurück, wenn kein Mikrofon verfügbar ist."""
        if not self.voice_input:
            return input(prompt).strip()

        import speech_recognition as sr

        print("🎤 (sprich jetzt – oder Enter für Texteingabe)")
        try:
            with self._mic as source:
                audio = self._recognizer.listen(source, timeout=8, phrase_time_limit=15)
            text = self._recognizer.recognize_google(audio, language=self.language)
            print(f"Du (gehört): {text}")
            return text.strip()
        except sr.WaitTimeoutError:
            return ""
        except sr.UnknownValueError:
            print("(nicht verstanden)")
            return ""
        except Exception:
            # Netzwerkfehler o.ä. -> Tastatur
            return input(prompt).strip()
