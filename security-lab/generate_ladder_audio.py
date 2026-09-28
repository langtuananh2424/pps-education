"""
Tao audio 3 bac cho phan NOI cua Reflex (xem reflex_attempt_ladder.json):
moi cau hoi, ca 3 file deu doc lai DUNG bai viet lan 3 (Reflex V2 bat buoc noi
dung bai noi khop bai viet >= 45% tu noi dung, khong thi 422 -- xem
ReflexContentOverlap), chi khac do troi chay:

  s1: moi cau mo dau "Umm", lap lai tu dau, ngat 1.2s giua cac cau, doc cham
  s2: 1 lan "Umm" dau bai, ngat 0.6s giua cac cau
  s3: doc luu loat, khong ngat them

Khoang lang la IM LANG THAT chen bang ffmpeg (he thong do bang SpeechMeter tren
file am thanh, khong phai dau phay trong van ban). Moi file bi cat o 20 giay
-- dung gioi han ghi am max_recording_seconds=20 cua 9 cau hoi nay tren giao
dien that.

Chay: python3 generate_ladder_audio.py  -> reflex_ladder_audio/q46/q46_s1.mp3 ...
"""

import asyncio
import json
import os
import re
import shutil
import subprocess
import tempfile

import edge_tts

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
LADDER_FILE = os.path.join(SCRIPT_DIR, "reflex_attempt_ladder.json")
OUT_DIR = os.path.join(SCRIPT_DIR, "reflex_ladder_audio")
MAX_SECONDS = 20
SAMPLE_RATE = 24000

FFMPEG = shutil.which("ffmpeg") or os.path.expandvars(
    r"%LOCALAPPDATA%\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe"
    r"\ffmpeg-9.0-full_build\bin\ffmpeg.exe")

LEVELS = {
    # (rate, khoang lang giua cau (s), cach bien doi tung cau)
    # Luot thu 2026-09-24 ban 2: s1 cu (ngat 1.2s) duoc ~60%, s2 cu (ngat 0.6s) ~65%, ~1/4 dat som
    # -> day ca 2 bac nang hon 1 nac: s2 = s1 cu, s1 = cham + ngat lau hon + ap ung dau cau.
    # Luot thu ban 3: voi giong Viet, muc ngap ngung gan nhu KHONG doi diem (luu loat hay ngap ngung
    # deu ~60-67%, tieu chi Phat am quyet dinh) nhung ngap ngung nang lam audio bi cat o 20s -> 422
    # (cau 46). Nen chi ngap ngung NHE o s1, s2 = giong Viet luu loat, s3 = giong Anh (LEVEL_VOICE_OVERRIDE).
    "s1": ("-10%", 0.7, lambda i, s: f"Umm, {s.split()[0]}, {s}"),
    "s2": ("+0%", 0.3, lambda i, s: s),
    "s3": ("+0%", 0.0, None),
}

# s1/s2 dung giong Viet (vi-VN) doc tieng Anh -> phat am kem, mo phong hoc sinh chua luyen.
# s3 doi sang giong tieng Anh tre em: luot thu 2026-09-24 cho thay giong Viet doc LUU LOAT van
# chi duoc ~60-65% (bi tru tieu chi Phat am), khong bao gio dat -- lan 3 can mo phong hoc sinh
# da sua ca phat am, khong chi do troi chay.
LEVEL_VOICE_OVERRIDE = {"s3": "en-US-AnaNeural"}


# --- Ban sao Python cua common/ReflexContentOverlap.java (backend) -- de tu kiem tra truoc khi upload
# rang phan loi con lai sau khi cat 20s van du khop bai viet, khong bi 422 "noi khac bai viet".
_STOP = set(("a an the and but or so because when if then to of in on at for with from by up about into "
             "i you he she it we they me him her us them my your his its our their this that these those "
             "is am are was were be been being do does did have has had can will would could should very too "
             "also not no yes um uh").split())


def _norm(s):
    s = re.sub(r"[-’']", "", (s or "").lower())
    return [w for w in re.sub(r"[^a-z\s]", " ", s).split() if w]


def _lev(a, b):
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def _similar(w, t):
    return w == t or (len(w) >= 4 and len(t) >= 3 and _lev(w, t) / max(len(w), len(t)) <= 0.34)


def content_overlap(written, transcript):
    said = _norm(transcript)
    pool = said + [said[i - 1] + said[i] for i in range(1, len(said))]
    words = list(dict.fromkeys(w for w in _norm(written) if w not in _STOP))
    if not words:
        return 1.0
    return sum(1 for w in words if any(_similar(w, t) for t in pool)) / len(words)


def min_overlap_for(written):
    n = len(set(w for w in _norm(written) if w not in _STOP))
    return 0.25 if n <= 6 else 0.35 if n <= 10 else 0.45


def split_sentences(text):
    return [s.strip() for s in re.split(r"(?<=[.!?])\s+", text) if s.strip()]


def run_ffmpeg(*args):
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", *args], check=True)


async def tts(text, voice, rate, out_mp3, retries=5):
    for attempt in range(1, retries + 1):
        try:
            await edge_tts.Communicate(text=text, voice=voice, rate=rate, pitch="+12Hz").save(out_mp3)
            if os.path.getsize(out_mp3) > 0:
                return
        except Exception as e:  # noqa: BLE001 -- dich vu TTS mien phi hay loi mang thoang qua
            print(f"  [retry {attempt}/{retries}] {os.path.basename(out_mp3)}: {e}")
        await asyncio.sleep(2 * attempt)
    raise RuntimeError(f"TTS that bai: {out_mp3}")


def to_wav(src, dst):
    run_ffmpeg("-i", src, "-ac", "1", "-ar", str(SAMPLE_RATE), "-c:a", "pcm_s16le", dst)


def silence_wav(seconds, dst):
    run_ffmpeg("-f", "lavfi", "-i", f"anullsrc=r={SAMPLE_RATE}:cl=mono", "-t", str(seconds),
               "-c:a", "pcm_s16le", dst)


FFPROBE = os.path.join(os.path.dirname(FFMPEG), "ffprobe" + os.path.splitext(FFMPEG)[1])


def duration(path):
    out = subprocess.run([FFPROBE, "-v", "error", "-show_entries",
                          "format=duration", "-of", "csv=p=0", path], capture_output=True, text=True)
    return float(out.stdout.strip() or 0)


async def build(qid, level, text, voice, workdir):
    rate, pause, transform = LEVELS[level]
    sentences = split_sentences(text)
    parts = [text] if transform is None else [transform(i, s) for i, s in enumerate(sentences)]
    wavs = []
    elapsed = 0.0
    heard = []  # phan loi nam TRONG 20s dau (phan sau bi may ghi am cat mat)
    for i, part in enumerate(parts):
        mp3 = os.path.join(workdir, f"{qid}_{level}_{i}.mp3")
        await tts(part, voice, rate, mp3)
        wav = mp3[:-4] + ".wav"
        to_wav(mp3, wav)
        wavs.append(wav)
        seg = duration(wav)
        if elapsed < MAX_SECONDS:
            words = part.split()
            keep = words if elapsed + seg <= MAX_SECONDS else words[: int(len(words) * (MAX_SECONDS - elapsed) / seg)]
            heard.extend(keep)
        elapsed += seg
        if pause > 0 and i < len(parts) - 1:
            gap = os.path.join(workdir, f"{qid}_{level}_{i}_gap.wav")
            silence_wav(pause, gap)
            wavs.append(gap)
            elapsed += pause
    listfile = os.path.join(workdir, f"{qid}_{level}.txt")
    with open(listfile, "w", encoding="utf-8") as f:
        for w in wavs:
            f.write(f"file '{w.replace(os.sep, '/')}'\n")
    out_dir = os.path.join(OUT_DIR, f"q{qid}")
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, f"q{qid}_{level}.mp3")
    # cat o MAX_SECONDS: giong may ghi am tren giao dien tu dung o gioi han cua cau hoi
    run_ffmpeg("-f", "concat", "-safe", "0", "-i", listfile, "-t", str(MAX_SECONDS),
               "-ac", "1", "-ar", str(SAMPLE_RATE), "-b:a", "64k", out)
    return out, content_overlap(text, " ".join(heard))


async def main():
    with open(LADDER_FILE, encoding="utf-8") as f:
        ladder = {k: v for k, v in json.load(f).items() if not k.startswith("_")}
    with tempfile.TemporaryDirectory() as workdir:
        for qid, item in ladder.items():
            final_text = item["writing"][2]  # bai viet lan 3 = bai se duoc doc lai
            voice = "vi-VN-HoaiMyNeural" if int(qid) % 2 == 0 else "vi-VN-NamMinhNeural"
            threshold = min_overlap_for(final_text)
            for level in ("s1", "s2", "s3"):
                out, overlap = await build(qid, level, final_text, LEVEL_VOICE_OVERRIDE.get(level, voice), workdir)
                # +0.3: uoc luong nay coi moi tu da doc la nghe dung, nhung giong Viet doc sai am lam AI
                # phien am lech them -- luot thu cho thay uoc 50-75% van bi 422 thuc te.
                flag = "" if overlap >= threshold + 0.3 else "  <-- CANH BAO: sat/duoi nguong, de bi 422"
                print(f"q{qid} {level}: {duration(out):.1f}s, khop noi dung {overlap:.0%} (nguong {threshold:.0%}){flag}")


if __name__ == "__main__":
    asyncio.run(main())
