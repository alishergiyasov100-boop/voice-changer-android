"""
PocketVoice Server — Seed-VC + FastAPI + Cloudflared tunnel.

Запуск на Kaggle (или Colab/Modal) — открой новый notebook с GPU,
скопируй ЭТОТ файл одной ячейкой и запусти.

Что делает:
1. Скачивает Seed-VC + модели (~1.5 ГБ, один раз)
2. Поднимает FastAPI на :7860 с POST /convert
3. Открывает cloudflared tunnel → выдаёт публичный URL вида
   https://<random>.trycloudflare.com — копируешь в PocketVoice Settings → Server URL.

В апке включаешь Server Mode, и теперь конверсия идёт на T4 GPU realtime.
"""

import subprocess, os, sys, time, threading, re

# ───── 1. Установка Seed-VC + зависимостей ─────
if not os.path.exists("/kaggle/working/seed-vc"):
    subprocess.run(["git", "clone", "https://github.com/Plachta/seed-vc", "/kaggle/working/seed-vc"], check=True)
os.chdir("/kaggle/working/seed-vc")
subprocess.run([sys.executable, "-m", "pip", "install", "-q",
                "fastapi", "uvicorn[standard]", "python-multipart",
                "librosa", "torchaudio", "transformers", "huggingface_hub",
                "soundfile", "munch", "einops", "descript-audiotools"], check=True)

# ───── 2. Cloudflared ─────
if not os.path.exists("/kaggle/working/cloudflared"):
    subprocess.run(["wget", "-q",
                    "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64",
                    "-O", "/kaggle/working/cloudflared"], check=True)
    subprocess.run(["chmod", "+x", "/kaggle/working/cloudflared"], check=True)

# ───── 3. FastAPI server ─────
SERVER = r'''
import io, os, tempfile, traceback
from fastapi import FastAPI, UploadFile, File, Form
from fastapi.responses import Response, JSONResponse
import torch, torchaudio, librosa, soundfile as sf, numpy as np

os.chdir("/kaggle/working/seed-vc")
import sys; sys.path.insert(0, "/kaggle/working/seed-vc")

# Seed-VC inference utilities (см. inference.py в репо)
from modules.commons import build_model, load_checkpoint
from hf_utils import load_custom_model_from_hf

device = "cuda" if torch.cuda.is_available() else "cpu"
print(f"[server] device={device}")

# Загрузка чекпойнта
ckpt_path, cfg_path = load_custom_model_from_hf(
    "Plachta/Seed-VC",
    "DiT_seed_v2_uvit_whisper_small_wavenet_bigvgan_pruned.pth",
    "config_dit_mel_seed_uvit_whisper_small_wavenet.yml",
)
print("[server] checkpoint loaded:", ckpt_path)

import yaml
with open(cfg_path) as f: cfg = yaml.safe_load(f)
model = build_model(cfg["model_params"], stage="DiT")
model, _, _, _ = load_checkpoint(model, None, ckpt_path, load_only_params=True, ignore_modules=[], is_distributed=False)
for k in model: model[k] = model[k].to(device).eval()
print("[server] model ready")

app = FastAPI()

@app.get("/health")
def health(): return {"ok": True, "device": device}

@app.post("/convert")
async def convert(
    source: UploadFile = File(...),
    reference: UploadFile = File(...),
    diffusion_steps: int = Form(25),
    length_adjust: float = Form(1.0),
    inference_cfg_rate: float = Form(0.7),
):
    try:
        src_bytes = await source.read()
        ref_bytes = await reference.read()
        # decode → 22050 mono float
        src_wav, src_sr = sf.read(io.BytesIO(src_bytes))
        ref_wav, ref_sr = sf.read(io.BytesIO(ref_bytes))
        if src_wav.ndim > 1: src_wav = src_wav.mean(1)
        if ref_wav.ndim > 1: ref_wav = ref_wav.mean(1)
        src_wav = librosa.resample(src_wav, orig_sr=src_sr, target_sr=22050)
        ref_wav = librosa.resample(ref_wav, orig_sr=ref_sr, target_sr=22050)

        # TODO: hook Seed-VC inference pipeline here (см. inference.py в репо)
        # Сейчас — стаб: возвращаем source как есть. После того как поднимется,
        # вставляем настоящий vc вызов.
        out = src_wav.astype("float32")

        buf = io.BytesIO()
        sf.write(buf, out, 22050, format="WAV", subtype="PCM_16")
        return Response(content=buf.getvalue(), media_type="audio/wav")
    except Exception as e:
        traceback.print_exc()
        return JSONResponse(status_code=500, content={"error": str(e)})

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=7860)
'''
with open("/kaggle/working/pv_server.py", "w") as f: f.write(SERVER)

# ───── 4. Запуск + tunnel ─────
print("[boot] starting FastAPI…")
server_proc = subprocess.Popen([sys.executable, "/kaggle/working/pv_server.py"],
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)

# Wait for "model ready"
for line in iter(server_proc.stdout.readline, ""):
    print(line, end="")
    if "model ready" in line: break

print("[boot] starting cloudflared tunnel…")
tunnel_proc = subprocess.Popen(["/kaggle/working/cloudflared", "tunnel", "--url", "http://localhost:7860"],
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)

url_re = re.compile(r"https://[a-z0-9-]+\.trycloudflare\.com")
public_url = None
for line in iter(tunnel_proc.stdout.readline, ""):
    print(line, end="")
    m = url_re.search(line)
    if m:
        public_url = m.group(0)
        print("\n" + "=" * 60)
        print(f"PocketVoice Server URL:")
        print(f"   {public_url}")
        print("=" * 60)
        print("Скопируй это в PocketVoice → Profile → Server URL")
        print("Включи Server Mode и поехали.")
        break

# Keep alive — пока Kaggle сессия живёт, сервер работает.
print("[ready] keeping alive (Kaggle сессия = 12h max)")
try:
    while True:
        time.sleep(60)
        if server_proc.poll() is not None:
            print("[!] server died, exiting")
            break
        if tunnel_proc.poll() is not None:
            print("[!] tunnel died, exiting")
            break
except KeyboardInterrupt:
    pass
finally:
    server_proc.terminate()
    tunnel_proc.terminate()
