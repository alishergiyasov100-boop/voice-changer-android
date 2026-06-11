"""
PocketVoice Server на Google Colab T4 GPU.

ИНСТРУКЦИЯ:
1. Зайди на https://colab.research.google.com → New Notebook
2. Меню Runtime → Change runtime type → T4 GPU → Save
3. Скопируй этот файл ОДНОЙ ячейкой (Ctrl+V в первой клетке) → Run (Shift+Enter)
4. Жди 5-10 минут (первый раз — качает Seed-VC + модели ~2 ГБ)
5. В выводе появится URL вида:  https://<random>.trycloudflare.com
6. Скопируй URL → PocketVoice → Профиль → Server URL → Сохранить
7. Не закрывай вкладку Colab пока пользуешься (Colab отрубается через 6ч простоя)
"""

import os, sys, subprocess, time, threading, re

# ────────── 1. Clone Seed-VC ──────────
if not os.path.exists("/content/seed-vc"):
    print("[setup] cloning Seed-VC…")
    subprocess.run(["git", "clone", "https://github.com/Plachta/seed-vc", "/content/seed-vc"], check=True)
os.chdir("/content/seed-vc")

# ────────── 2. Install deps ──────────
print("[setup] installing python deps (5-7 min)…")
subprocess.run([sys.executable, "-m", "pip", "install", "-q",
    "-r", "requirements.txt"], check=True)
subprocess.run([sys.executable, "-m", "pip", "install", "-q",
    "fastapi", "uvicorn[standard]", "python-multipart"], check=True)

# ────────── 3. Cloudflared (туннель без аутентификации) ──────────
if not os.path.exists("/content/cloudflared"):
    print("[setup] downloading cloudflared…")
    subprocess.run(["wget", "-q",
        "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64",
        "-O", "/content/cloudflared"], check=True)
    subprocess.run(["chmod", "+x", "/content/cloudflared"], check=True)

# ────────── 4. Поверх Plachta/Seed-VC app_vc.py — обёртка FastAPI ──────────
SERVER = r'''
import sys, os, io, traceback, asyncio
sys.path.insert(0, "/content/seed-vc")
os.chdir("/content/seed-vc")

import torch, librosa, soundfile as sf, numpy as np
from fastapi import FastAPI, UploadFile, File, Form
from fastapi.responses import Response, JSONResponse
from fastapi.middleware.cors import CORSMiddleware

# Импорт inference из seed-vc repo
# В app_vc.py есть функция voice_conversion(...) — используем её
from app_vc import voice_conversion

device = "cuda" if torch.cuda.is_available() else "cpu"
print(f"[server] device={device}")

app = FastAPI()
app.add_middleware(
    CORSMiddleware, allow_origins=["*"],
    allow_methods=["*"], allow_headers=["*"],
)

@app.get("/health")
def health(): return {"ok": True, "device": device}

@app.post("/convert")
async def convert(
    source: UploadFile = File(...),
    reference: UploadFile = File(...),
    diffusion_steps: int = Form(25),
    length_adjust: float = Form(1.0),
    inference_cfg_rate: float = Form(0.7),
    pitch_shift: int = Form(0),
):
    try:
        # Сохраняем загруженные файлы во временные пути (app_vc.voice_conversion ждёт пути)
        src_path = "/tmp/src.wav"
        ref_path = "/tmp/ref.wav"
        with open(src_path, "wb") as f: f.write(await source.read())
        with open(ref_path, "wb") as f: f.write(await reference.read())

        # voice_conversion это generator (streams chunks) — собираем последний (full)
        full = None
        for output in voice_conversion(
            src_path, ref_path,
            diffusion_steps, length_adjust, inference_cfg_rate,
            False, True, pitch_shift,
        ):
            # Каждый yield = (sample_rate, np.array) для streaming или final
            full = output

        if full is None:
            return JSONResponse(status_code=500, content={"error": "voice_conversion returned nothing"})

        sr, audio = full if isinstance(full, tuple) else (22050, full)
        buf = io.BytesIO()
        sf.write(buf, audio, sr, format="WAV", subtype="PCM_16")
        return Response(content=buf.getvalue(), media_type="audio/wav")
    except Exception as e:
        traceback.print_exc()
        return JSONResponse(status_code=500, content={"error": str(e), "trace": traceback.format_exc()[:800]})

if __name__ == "__main__":
    import uvicorn
    print("[server] starting on :7860")
    uvicorn.run(app, host="0.0.0.0", port=7860)
'''
with open("/content/pv_server.py", "w") as f: f.write(SERVER)

# ────────── 5. Запускаем сервер + туннель ──────────
print("[boot] starting FastAPI server…")
server_proc = subprocess.Popen([sys.executable, "/content/pv_server.py"],
    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)

# Ждём пока модели загрузятся (увидим "device=cuda" или "starting on :7860")
ready_re = re.compile(r"starting on :7860")
for line in iter(server_proc.stdout.readline, ""):
    print(line, end="")
    if ready_re.search(line):
        time.sleep(2)
        break

print("\n[boot] starting cloudflared tunnel…")
tunnel_proc = subprocess.Popen(
    ["/content/cloudflared", "tunnel", "--url", "http://localhost:7860", "--no-autoupdate"],
    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)

url_re = re.compile(r"https://[a-z0-9-]+\.trycloudflare\.com")
public_url = None
for line in iter(tunnel_proc.stdout.readline, ""):
    print(line, end="")
    m = url_re.search(line)
    if m:
        public_url = m.group(0)
        break

if public_url:
    print("\n" + "═" * 64)
    print(f"║  PocketVoice Server URL:")
    print(f"║  {public_url}")
    print("═" * 64)
    print("Скопируй URL в PocketVoice → Профиль → Server URL → Сохранить")
    print("Включи 'Server Mode' там же. Готово.\n")
else:
    print("[!] не получил URL от cloudflared")

# ────────── 6. Keep alive (Colab отрубит через 6ч idle) ──────────
def keep_logs(proc, prefix):
    for line in iter(proc.stdout.readline, ""):
        print(f"[{prefix}] {line}", end="")
threading.Thread(target=keep_logs, args=(server_proc, "server"), daemon=True).start()
threading.Thread(target=keep_logs, args=(tunnel_proc, "tunnel"), daemon=True).start()

print("[ready] keeping alive. Не закрывай эту вкладку.")
try:
    while True:
        time.sleep(60)
        if server_proc.poll() is not None:
            print("[!] server died — перезапусти ячейку"); break
        if tunnel_proc.poll() is not None:
            print("[!] tunnel died — перезапусти ячейку"); break
except KeyboardInterrupt:
    print("\n[stop] shutting down")
finally:
    server_proc.terminate(); tunnel_proc.terminate()
