# PocketVoice

Android voice changer — RVC + Miku, server-mode на free Colab T4.

## 🎤 LIVE-TALK (рекомендуется) — w-okada в браузере

[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/alishergiyasov100-boop/voice-changer-android/blob/main/server/PocketVoice_LiveTalk_Miku.ipynb)

1. Жми бейдж ↑ — открывается notebook
2. Runtime → Change runtime type → **T4 GPU** → Save (один раз)
3. **Run All** (Ctrl/Cmd+F9), жди 10-15 мин
4. Копируешь `https://*.trycloudflare.com` URL и открываешь в Chrome на Poco
5. Model Slot 0 уже = Miku → нажми **Start** → разреши mic → говори
6. Latency 100-300мс, **realtime live-talk**

Без ngrok токенов. Без аккаунтов. Только Google для Colab.

## 📼 BATCH (PocketVoice Android app) — запись → конверт

[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/alishergiyasov100-boop/voice-changer-android/blob/main/server/PocketVoice_RVC_Miku.ipynb)

1. Жми бейдж ↑
2. **T4 GPU** → **Run All** → ждёшь 8-12 мин
3. Копируешь URL → в PocketVoice → Профиль → Server URL → **Save** → **Server Mode ON**
4. Запись → конверт в Miku ~3-5 сек/фраза

**Не закрывай вкладку Colab.** Idle 6h → отрубается, открой и Run All снова.

## Стек

- Android Kotlin/Compose, package `com.korvus.pocketvoice`
- Server: FastAPI на Colab T4 + RVC v2 + `NoCrypt/miku_RVC` checkpoint
- Tunnel: `cloudflared` anonymous (без ngrok-токенов)
- Client: `api/RemoteVoiceServer.kt` → POST `/convert` multipart

## TODO

- On-device RVC (см. `server/colab_rvc_to_onnx.py`) — шаг 1 готов, осталось переписать `onnx/LocalConverter.kt` под RVC pipeline в Kotlin
- HF Spaces inference как fallback когда Colab спит
