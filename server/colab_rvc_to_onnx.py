"""
ОДНА ЯЧЕЙКА для Google Colab — конвертация Miku RVC .pth → ONNX.

ИНСТРУКЦИЯ:
1. colab.research.google.com → New notebook
2. Runtime → Change runtime type → T4 GPU (опционально, CPU тоже норм)
3. Скопируй этот файл одной ячейкой → Shift+Enter
4. Жди ~5 минут (скачает RVC repo + Miku модель + конвертирует)
5. На последней строке появится **link на miku.onnx** — скачай руками или
   автоматом загрузится в твой HF репо если задашь HF_TOKEN ниже

После того как получишь `miku.onnx` (~108 МБ) — отправь мне URL,
я добавлю его в скачку через VoiceHub PocketVoice.
"""

import os, sys, subprocess

# ────────── 1. RVC repo ──────────
if not os.path.exists("/content/RVC"):
    print("[1/5] cloning RVC repo…")
    # Используем RVC-Project — стандартный RVC v2 с export_onnx.py
    subprocess.run(["git", "clone", "-q", "--depth=1",
                    "https://github.com/RVC-Project/Retrieval-based-Voice-Conversion-WebUI",
                    "/content/RVC"], check=True)

os.chdir("/content/RVC")

# ────────── 2. Зависимости ──────────
print("[2/5] installing deps…")
subprocess.run([sys.executable, "-m", "pip", "install", "-q",
                "torch", "torchaudio", "onnx", "onnxruntime",
                "numpy<2.0", "librosa", "huggingface_hub"], check=True)

# ────────── 3. Скачиваем Miku .pth ──────────
import urllib.request
print("[3/5] downloading Miku RVC model from NoCrypt/miku_RVC…")
miku_url = "https://huggingface.co/NoCrypt/miku_RVC/resolve/main/1a_miku_default_rvc_(aple)/miku_default_rvc.pth"
pth_path = "/content/miku.pth"
if not os.path.exists(pth_path):
    urllib.request.urlretrieve(miku_url, pth_path)
print(f"  downloaded: {os.path.getsize(pth_path) // 1024 // 1024} MB")

# ────────── 4. Экспорт в ONNX ──────────
print("[4/5] exporting to ONNX…")
# RVC repo имеет `onnx_export.py` или `export_onnx.py`
# в зависимости от версии. Найдём что есть:
exporter = None
for candidate in [
    "/content/RVC/infer/lib/jit/get_synthesizer.py",
    "/content/RVC/tools/onnx/onnx_export.py",
    "/content/RVC/onnx_export.py",
    "/content/RVC/export_onnx.py",
]:
    if os.path.exists(candidate):
        exporter = candidate
        break

if exporter:
    print(f"  using {exporter}")
else:
    print("[!] export script не найден, делаю руками…")

# Универсальный export через прямой PyTorch (если RVC скрипт сломан)
import torch
sys.path.insert(0, "/content/RVC")

try:
    # RVC v2 synthesizer
    from infer.lib.infer_pack.models_onnx import SynthesizerTrnMsNSFsidM
    cpt = torch.load(pth_path, map_location="cpu")
    cfg = cpt["config"]
    if isinstance(cfg, list):
        # convert config list to model args
        net_g = SynthesizerTrnMsNSFsidM(*cfg, encoder_dim=cpt.get("f0", 1) * 256 + 256)
    else:
        net_g = SynthesizerTrnMsNSFsidM(**cfg)
    net_g.load_state_dict(cpt["weight"], strict=False)
    net_g.eval()
    net_g.remove_weight_norm()

    # Dummy входы для трассировки
    test_phone = torch.rand(1, 200, 768)
    test_phone_lengths = torch.tensor([200]).long()
    test_pitch = torch.randint(size=(1, 200), low=5, high=255)
    test_pitchf = torch.rand(1, 200)
    test_ds = torch.LongTensor([0])
    test_rnd = torch.rand(1, 192, 200)

    onnx_path = "/content/miku.onnx"
    torch.onnx.export(
        net_g, (test_phone, test_phone_lengths, test_pitch, test_pitchf, test_ds, test_rnd),
        onnx_path,
        input_names=["phone", "phone_lengths", "pitch", "pitchf", "ds", "rnd"],
        output_names=["audio"],
        dynamic_axes={
            "phone": {1: "n"}, "phone_lengths": {0: "b"},
            "pitch": {1: "n"}, "pitchf": {1: "n"},
            "rnd": {2: "n"},
        },
        opset_version=17,
    )
    print(f"  exported: {os.path.getsize(onnx_path) // 1024 // 1024} MB")
except Exception as e:
    print(f"[!] прямой export не прошёл: {e}")
    print("Запускаю встроенный export если есть…")
    if exporter:
        subprocess.run([sys.executable, exporter, "--ckpt", pth_path,
                       "--save", "/content/miku.onnx"])

# ────────── 5. Upload в HF (опционально) ──────────
print("[5/5] загрузка в HF…")
HF_TOKEN = ""  # ВСТАВЬ свой HF token чтобы автоматически загрузить
HF_REPO = "KorvusTheExplorer/pocketvoice-rvc-onnx"  # репо для голосов

if HF_TOKEN:
    from huggingface_hub import HfApi, create_repo
    api = HfApi(token=HF_TOKEN)
    try: create_repo(HF_REPO, private=False, exist_ok=True, token=HF_TOKEN)
    except: pass
    api.upload_file(
        path_or_fileobj="/content/miku.onnx",
        path_in_repo="miku_default.onnx",
        repo_id=HF_REPO,
        token=HF_TOKEN,
    )
    print(f"\n✓ Uploaded: https://huggingface.co/{HF_REPO}/resolve/main/miku_default.onnx")
else:
    print("\nHF_TOKEN не задан, .onnx лежит в /content/miku.onnx")
    print("Скачай руками: левая панель Colab → файлы → правая кнопка → Download")
