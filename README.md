# PocketVoice

Android voice changer — RVC + Miku, server-mode на free Colab T4.

## Use it (один клик в день)

[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/alishergiyasov100-boop/voice-changer-android/blob/main/server/PocketVoice_RVC_Miku.ipynb)

1. Жми бейдж ↑ — открывается notebook
2. Runtime → Change runtime type → **T4 GPU** → Save (один раз)
3. **Run All** (Ctrl/Cmd+F9)
4. Ждёшь ~8-12 мин setup → копируешь `https://*.trycloudflare.com` URL
5. В PocketVoice → Профиль → Server URL → вставь → **Save** → **Server Mode ON**
6. Запись → Miku голос на T4 GPU

**Не закрывай вкладку Colab.** Idle 6h → отрубается, открой и Run All снова.

## Стек

- Android Kotlin/Compose, package `com.korvus.pocketvoice`
- Server: FastAPI на Colab T4 + RVC v2 + `NoCrypt/miku_RVC` checkpoint
- Tunnel: `cloudflared` anonymous (без ngrok-токенов)
- Client: `api/RemoteVoiceServer.kt` → POST `/convert` multipart

## TODO

- On-device RVC (см. `server/colab_rvc_to_onnx.py`) — шаг 1 готов, осталось переписать `onnx/LocalConverter.kt` под RVC pipeline в Kotlin
- HF Spaces inference как fallback когда Colab спит
