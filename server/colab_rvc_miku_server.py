"""
PocketVoice RVC сервер с предзагруженной Miku на Colab T4 GPU.

ИНСТРУКЦИЯ:
1. colab.research.google.com → New notebook
2. Runtime → Change runtime type → T4 GPU → Save
3. Скопируй этот файл ОДНОЙ ячейкой → Shift+Enter
4. Жди 8-12 мин (RVC + Miku модель ~3 ГБ суммарно)
5. На последней строке появится https://<random>.trycloudflare.com URL
6. Скопируй в PocketVoice → Профиль → Server URL → Сохранить → включи Server Mode
7. Запись → конверсия идёт на T4 GPU + Miku голос → realtime

Не закрывай вкладку Colab. Простой 6ч = отрубится, надо переоткрыть.
"""

import os, sys, subprocess, time, threading, re

# ────────── 1. RVC ──────────
os.chdir("/content")
if not os.path.exists("/content/RVC"):
    print("[setup 1/5] cloning RVC…")
    subprocess.run(["git", "clone", "-q", "--depth=1",
                    "https://github.com/RVC-Project/Retrieval-based-Voice-Conversion-WebUI",
                    "/content/RVC"], check=True)

os.chdir("/content/RVC")

# ────────── 2. Dependencies ──────────
print("[setup 2/5] installing deps (~5 min)…")
subprocess.run([sys.executable, "-m", "pip", "install", "-q",
    "torch", "torchaudio", "numpy<2.0", "librosa", "scipy", "soundfile",
    "praat-parselmouth", "fairseq @ git+https://github.com/One-sixth/fairseq.git",
    "faiss-cpu", "pyworld",
    "fastapi", "uvicorn[standard]", "python-multipart",
    "huggingface_hub"], check=True)

# ────────── 3. RVC assets (HuBERT + RMVPE + pretrained) ──────────
print("[setup 3/5] downloading RVC assets…")
assets_urls = {
    "/content/RVC/assets/hubert/hubert_base.pt":
        "https://huggingface.co/lj1995/VoiceConversionWebUI/resolve/main/hubert_base.pt",
    "/content/RVC/assets/rmvpe/rmvpe.pt":
        "https://huggingface.co/lj1995/VoiceConversionWebUI/resolve/main/rmvpe.pt",
}
import urllib.request
for path, url in assets_urls.items():
    if not os.path.exists(path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        print(f"  {os.path.basename(path)}…")
        urllib.request.urlretrieve(url, path)

# ────────── 4. Miku модель ──────────
print("[setup 4/5] downloading Miku RVC model…")
miku_dir = "/content/voices/miku"
os.makedirs(miku_dir, exist_ok=True)
miku_pth = f"{miku_dir}/miku.pth"
miku_idx = f"{miku_dir}/miku.index"
if not os.path.exists(miku_pth):
    urllib.request.urlretrieve(
        "https://huggingface.co/NoCrypt/miku_RVC/resolve/main/1a_miku_default_rvc_(aple)/miku_default_rvc.pth",
        miku_pth,
    )
if not os.path.exists(miku_idx):
    urllib.request.urlretrieve(
        "https://huggingface.co/NoCrypt/miku_RVC/resolve/main/1a_miku_default_rvc_(aple)/added_IVF4457_Flat_nprobe_1_miku_default_rvc_v2.index",
        miku_idx,
    )
print(f"  miku.pth = {os.path.getsize(miku_pth) // 1024 // 1024} MB")
print(f"  miku.index = {os.path.getsize(miku_idx) // 1024 // 1024} MB")

# ────────── 5. Cloudflared ──────────
if not os.path.exists("/content/cloudflared"):
    print("[setup 5/5] cloudflared…")
    urllib.request.urlretrieve(
        "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64",
        "/content/cloudflared",
    )
    subprocess.run(["chmod", "+x", "/content/cloudflared"], check=True)

# ────────── 6. Server (RVC inference) ──────────
SERVER = r'''
import sys, os, io, traceback, json
sys.path.insert(0, "/content/RVC")
os.chdir("/content/RVC")

import torch, librosa, numpy as np, soundfile as sf
from fastapi import FastAPI, UploadFile, File, Form
from fastapi.responses import Response, JSONResponse
from fastapi.middleware.cors import CORSMiddleware

device = "cuda" if torch.cuda.is_available() else "cpu"
print(f"[server] device={device}")

# Импорт RVC modules
from infer.modules.vc.modules import VC
from configs.config import Config

cfg = Config()
cfg.device = device

vc = VC(cfg)

# Загружаем Miku модель
print("[server] loading Miku model…")
vc.get_vc("/content/voices/miku/miku.pth")
print("[server] miku ready")

app = FastAPI()
app.add_middleware(CORSMiddleware, allow_origins=["*"],
    allow_methods=["*"], allow_headers=["*"])

@app.get("/health")
def health(): return {"ok": True, "device": device, "voice": "miku"}

@app.post("/convert")
async def convert(
    source: UploadFile = File(...),
    reference: UploadFile = File(None),  # ignored — мы используем загруженную Miku
    pitch_shift: int = Form(0),
):
    try:
        src_bytes = await source.read()
        # Сохраняем во временный файл
        src_path = "/tmp/src_input.wav"
        with open(src_path, "wb") as f: f.write(src_bytes)

        # RVC inference (vc_single)
        _, audio_opt = vc.vc_single(
            sid=0,
            input_audio_path=src_path,
            f0_up_key=pitch_shift,
            f0_file=None,
            f0_method="rmvpe",  # best quality
            file_index="/content/voices/miku/miku.index",
            file_index2=None,
            index_rate=0.75,
            filter_radius=3,
            resample_sr=0,
            rms_mix_rate=0.25,
            protect=0.33,
        )

        # audio_opt = (sr, np.array int16)
        sr, audio = audio_opt
        buf = io.BytesIO()
        sf.write(buf, audio, sr, format="WAV", subtype="PCM_16")
        return Response(content=buf.getvalue(), media_type="audio/wav")
    except Exception as e:
        traceback.print_exc()
        return JSONResponse(status_code=500, content={"error": str(e), "trace": traceback.format_exc()[:800]})

if __name__ == "__main__":
    import uvicorn
    print("[server] starting :7860")
    uvicorn.run(app, host="0.0.0.0", port=7860)
'''
with open("/content/pv_rvc_server.py", "w") as f: f.write(SERVER)

# ────────── 7. Запуск + туннель ──────────
print("\n[boot] starting RVC server…")
server_proc = subprocess.Popen([sys.executable, "/content/pv_rvc_server.py"],
    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)

# Ждём готовности
for line in iter(server_proc.stdout.readline, ""):
    print(line, end="")
    if "starting :7860" in line:
        time.sleep(2)
        break
    if "Traceback" in line or "Error" in line:
        # Продолжаем читать ошибку
        pass

print("\n[boot] cloudflared tunnel…")
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
    print(f"║  PocketVoice Server URL (RVC + Miku загружен на GPU):")
    print(f"║  {public_url}")
    print("═" * 64)
    print("\n👉 PocketVoice → Профиль → Server URL вставь URL → Сохранить")
    print("👉 Включи Server Mode свитч")
    print("👉 Запись → Miku голос realtime на T4 GPU\n")
else:
    print("[!] cloudflared не дал URL")

# Keep alive + log tail
def tail(proc, prefix):
    for line in iter(proc.stdout.readline, ""):
        print(f"[{prefix}] {line}", end="")
threading.Thread(target=tail, args=(server_proc, "srv"), daemon=True).start()
threading.Thread(target=tail, args=(tunnel_proc, "tun"), daemon=True).start()

print("[ready] держу. Не закрывай вкладку.")
try:
    while True:
        time.sleep(60)
        if server_proc.poll() is not None: print("[!] server died"); break
        if tunnel_proc.poll() is not None: print("[!] tunnel died"); break
except KeyboardInterrupt:
    print("\n[stop]")
finally:
    server_proc.terminate(); tunnel_proc.terminate()
