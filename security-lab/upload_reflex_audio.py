"""
Upload toan bo audio test Speaking Reflex (90 file, xem
reflex_speaking_gemini_tts_prompt.md/generate_test_audio.py) len staging qua
POST /api/media/upload (module REVIEW_VIDEO_SUBMISSION -- xem MediaModule.java),
roi ghi ra CSV mapping filename -> URL de dung cho REFLEX_AUDIO_URLS.

Chi can 1 tai khoan da dang nhap la upload duoc (MediaController khong gate
quyen rieng) -- dung lai username dau tien trong students.csv + TEST_PASSWORD.

Chay:
    $env:TARGET_URL = "https://admin-staging.ppsvietnam.edu.vn"
    $env:TEST_PASSWORD = "<mật_khẩu_dùng_chung>"
    python3 upload_reflex_audio.py
"""

import csv
import os
import sys

import requests

BASE_URL = os.environ.get("TARGET_URL", "").rstrip("/")
PASSWORD = os.environ.get("TEST_PASSWORD", "")
AUDIO_DIR = os.environ.get("AUDIO_DIR", os.path.join(os.path.dirname(__file__), "reflex_audio_extracted"))
UPLOAD_USERNAME = os.environ.get("UPLOAD_USERNAME", "")

_SCRIPT_DIR = os.path.dirname(__file__)
_STUDENTS_CSV = os.path.join(_SCRIPT_DIR, "students.csv")

if not UPLOAD_USERNAME and os.path.isfile(_STUDENTS_CSV):
    with open(_STUDENTS_CSV, newline="", encoding="utf-8-sig") as f:
        first_row = next(csv.DictReader(f))
        UPLOAD_USERNAME = first_row["username"]

if not BASE_URL or not PASSWORD or not UPLOAD_USERNAME:
    print("Thieu TARGET_URL / TEST_PASSWORD / UPLOAD_USERNAME (khong doc duoc tu students.csv).", file=sys.stderr)
    sys.exit(1)

if not os.path.isdir(AUDIO_DIR):
    print(f"Khong tim thay thu muc audio: {AUDIO_DIR}", file=sys.stderr)
    sys.exit(1)


def login(session):
    resp = session.post(
        f"{BASE_URL}/api/auth/login",
        json={"usernameOrEmail": UPLOAD_USERNAME, "password": PASSWORD},
        timeout=30,
    )
    if resp.status_code != 200:
        raise RuntimeError(f"Login that bai ({UPLOAD_USERNAME}): HTTP {resp.status_code} {resp.text}")
    body = resp.json()
    return body["accessToken"], body["refreshToken"]


def logout(session, headers, refresh_token):
    try:
        session.post(f"{BASE_URL}/api/auth/logout", json={"refreshToken": refresh_token}, headers=headers, timeout=30)
    except requests.RequestException:
        pass


def collect_files(audio_dir):
    files = []
    for root, _dirs, names in os.walk(audio_dir):
        for name in sorted(names):
            if name.lower().endswith((".mp3", ".wav", ".webm", ".m4a")):
                files.append(os.path.join(root, name))
    return sorted(files)


def main():
    files = collect_files(AUDIO_DIR)
    if not files:
        print(f"Khong tim thay file audio nao trong {AUDIO_DIR}", file=sys.stderr)
        sys.exit(1)
    print(f"Tim thay {len(files)} file, dang nhap bang {UPLOAD_USERNAME}...")

    session = requests.Session()
    access_token, refresh_token = login(session)
    headers = {"Authorization": f"Bearer {access_token}"}

    results = []
    for i, path in enumerate(files, start=1):
        filename = os.path.basename(path)
        with open(path, "rb") as fh:
            resp = session.post(
                f"{BASE_URL}/api/media/upload",
                headers=headers,
                files={"file": (filename, fh, "audio/mpeg")},
                data={"module": "REVIEW_VIDEO_SUBMISSION"},
                timeout=60,
            )
        if resp.status_code == 200:
            url = resp.json()["url"]
            print(f"[{i}/{len(files)}] OK {filename} -> {url}")
            results.append({"filename": filename, "url": url, "error": ""})
        else:
            print(f"[{i}/{len(files)}] LOI {filename}: HTTP {resp.status_code} {resp.text}")
            results.append({"filename": filename, "url": "", "error": f"HTTP {resp.status_code}: {resp.text}"})

    logout(session, headers, refresh_token)

    out_path = os.environ.get("UPLOAD_MAP_CSV") or os.path.join(_SCRIPT_DIR, "results", "reflex_audio_upload_map.csv")
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["filename", "url", "error"])
        w.writeheader()
        w.writerows(results)

    ok = sum(1 for r in results if r["url"])
    print(f"\nXong: {ok}/{len(results)} upload thanh cong. Da ghi: {out_path}")


if __name__ == "__main__":
    main()
