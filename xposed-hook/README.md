# PocketVoice Xposed Hook

Xposed / LSPatch module that hooks `android.media.AudioRecord.read()` in target
messengers (Telegram, Discord, WhatsApp) so the microphone stream can be
intercepted, transformed by an AI voice-changer (e.g. ElevenLabs STS), and
re-injected back into the app.

## Why LSPatch instead of root

Poco X7 Pro / MediaTek MT6899 stock kernel ships without `snd-aloop`, so
system-wide mic replacement via Magisk kernel modules is out. LSPatch embeds
this hook into a repackaged APK of the target app — the OS itself stays clean,
no bootloader unlock, no `SafetyNet`/`Play Integrity` issues for TG/Discord/WA.

## Current status (POC)

- v0.1.0: hook installs, logs every `read()` call size to Xposed log every 50
  frames. **No injection yet — pure observability step.**
- Next step: replace the buffer contents in `afterHookedMethod` with PCM
  received from the PocketVoice AI relay before the app consumes it.

## Build

```bash
./gradlew :xposed-hook:assembleRelease
```

Output: `xposed-hook/build/outputs/apk/release/xposed-hook-release-unsigned.apk`

## Patch a target APK

1. Grab the target's `.apk` (e.g. via `apkeep`/APKMirror).
2. Run the patcher:

```bash
./scripts/patch-apk.sh Telegram.apk
```

3. Install the resulting `Telegram-lspatched.apk` **alongside** original TG
   (different signature — Android treats it as a separate app).
4. Grant `RECORD_AUDIO` permission as usual.
5. Watch the hook fire:

```bash
adb logcat | grep PocketVoiceHook
```

Expected: `PocketVoiceHook org.telegram.messenger read #50, +1920, total=94KB`
during any voice message / call.

## Integration roadmap

- **v0.2** — bind to a local `LocalSocket` server run by the PocketVoice app
  (bound to `abstract:pocketvoice-mic`); forward captured PCM.
- **v0.3** — receive processed PCM back over the same socket and overwrite the
  `read()` result buffer before the app sees it.
- **v0.4** — ElevenLabs STS realtime WebSocket wired into PocketVoice app;
  end-to-end conversion in messenger calls.
