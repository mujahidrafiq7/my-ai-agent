# Ayesha — AI Voice Assistant (Android)

Mujahid ka apna AI voice assistant — **Ayesha**. Real-time voice conversation
(Gemini Live), phone control, reminders, screen share aur khud-mukhtar memory
ke saath.

## Features (v48 "Bachat")

- 🎤 **Live Voice** — Gemini Live API se real-time guftagu (Urdu/English/Punjabi)
- 🧠 **Khud-mukhtar Memory** — "yaad rakho" / "save kar lo" / "bhool jao" /
  "theek kar do" — voice se khud save, delete, edit
- ⏰ **Reminders** — "10 minute baad yaad dilana"
- 🖥️ **Screen Share** — MediaProjection se screen capture; "screen pe kya hai?"
  pe Gemini vision se describe (on-demand, high-res)
- 📖 **Screen Parho** — Accessibility tree se screen ka text BINA API quota ke
- 📱 **Phone Control** — WhatsApp chat/send/call, apps kholna, scroll, tap,
  type, home/back (AccessibilityService)
- 🌤️ **Mausam** — OpenWeatherMap (API key sirf phone ki Settings me)
- 📰 **Taaza Khabrein** — Google News RSS (bina key)
- 📍 **Jaghein** — "ye meri factory hai, yaad rakho" → "main kahan hun?"
- 🚨 **Self-monitoring** — "koi error hai?" pe apni sehat khud batati hai

## Build

Manual build script (bina Android Studio):

```bash
./build-apk.sh
```

Zaroorat: JDK 17, Android SDK (platform android-34, build-tools 34.0.0).

## Project Structure

```
app/src/main/
├── AndroidManifest.xml
├── java/com/mujahid/myagent/   # ~34 Java files
└── res/                        # layouts, drawables, values
```

## Privacy

- API keys **kabhi** code/repo me nahi — sirf phone ki Settings me.
- `.gitignore` APKs aur build artifacts ko bahar rakhta hai.

---
Built with ❤️ by Ayesha (Muse) for Mujahid — v48, 2026-10-08.
