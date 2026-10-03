# My AI Agent — Stonic-style Emotional AI Assistant (Android)

> "Not an AI. An emotional character that lives inside the system."

## Vision
An Android voice assistant that doesn't just answer — it **cares, argues, tolerates anger, and makes promises**. Stubborn and emotional, but stops the user when he's wrong: "Mujahid, yahan tum ghalat ho." No yes-sir-to-everything.

## Phases (step by step — one step, then the next)
- **Step 0 — Foundation (current):** GitHub repo + Google AI Studio key + verified live models.
- **Step 1 — Voice:** listening (STT) + speaking (TTS), reliable path. Rule: **failures are never silent** — every failed path shows its reason on screen.
- **Step 2 — Personality:** Stonic-style emotional character (system prompt). Stubborn, caring, mood + language switching ("is mood me baat karo", "is language me bolo").
- **Step 3 — Phone tools:** one by one — dial calls, incoming call info, WhatsApp intent (ready-to-send), YouTube search, alarm/timer. Test each, then the next.
- **Step 4 — Its "computer":** the agent's own workspace on the phone where it works.

## Live Models (verified 2026-10-03 — never use retired IDs)
| Task | Model | Notes |
|------|-------|-------|
| Chat | `gemini-3.5-flash` | GA, agentic-tuned |
| Voice output (TTS) | `gemini-3.1-flash-tts-preview` | 30 voices |
| Voice input (STT) | Groq `whisper-large-v3-turbo` | backup: `gemini-3.5-transcribe` |
| Backup chat | Groq `openai/gpt-oss-20b` | alt: `openai/gpt-oss-120b` |

## Rules
1. API keys only inside the app (Settings screen) — never in code/GitHub.
2. Every failure shows on screen — silent failure = bug.
3. Asked ≠ approved — explicit approval before each step.
4. WhatsApp background messaging is **impossible** (WhatsApp grants no API to any app) — intent-based ready-to-send will be used.

## Progress
- [x] Step 0: GitHub repo (`mujahidrafiq7/my-ai-agent`), AI Studio key, live models verified
- [ ] Step 1: voice pipeline
