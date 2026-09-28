"""
Test luồng Video phản xạ tuần tự (UC-23b V2: viết -> AI chấm ngữ pháp -> đạt ->
ghi âm -> AI chấm nội dung -> đạt -> mở câu tiếp theo) qua API THẬT của PPS
Education, cho cả lớp 50 tài khoản test -- xem ReflexSequentialGradingService.

Quy trình mô phỏng ĐÚNG như đã xác nhận với người dùng: với MỖI câu hỏi Reflex,
học sinh viết tối đa MAX_WRITING_ATTEMPTS lần (mặc định 3) cho tới khi ĐẠT hoặc
hết lượt; nếu đạt viết mới chuyển sang nói tối đa MAX_SPEAKING_ATTEMPTS lần
(mặc định 3) cho tới khi đạt hoặc hết lượt; xong (đạt hay không) thì chuyển
sang câu hỏi tiếp theo trong danh sách REFLEX_QUESTION_IDS.

## Khác biệt quan trọng so với loadtest_writing_submissions.py (UC-40/41)

- Reflex dùng bảng reflex_question_progress -- MỖI (học sinh, câu hỏi) chỉ có
  1 dòng tiến trình, nộp lại là GHI ĐÈ (progress.answerText/audioUrl bị thay),
  không tạo nhiều bản ghi lịch sử như exercise_attempts. Vì vậy MUỐN test đủ
  "3 lần viết x 3 lần nói" phải dùng NHIỀU CÂU HỎI khác nhau (REFLEX_QUESTION_IDS),
  không phải nộp lại 1 câu 3 lần.
- Writing bước 1 chỉ chấm NGỮ PHÁP (không chấm nội dung/đúng đề) -- Speaking
  bước 2 mới chấm NỘI DUNG. Mẫu văn bản mặc định dưới đây (bad_grammar/short/
  good_grammar) mang tính minh hoạ CHUNG CHUNG (không biết trước prompt thật
  của REFLEX_QUESTION_IDS) -- CHỈNH LẠI qua WRITING_VARIANTS_FILE nếu muốn sát
  đề thật hơn.
- submitSpokenAnswer có thể trả HTTP 422 (ReflexAudioRejectedException — bản
  ghi không đọc được hoặc nói khác nội dung đã viết) — giao dịch ROLLBACK,
  KHÔNG tính lượt phía server. Script vẫn đếm vào vòng lặp local (tối đa
  MAX_SPEAKING_ATTEMPTS lần thử), nhưng ghi log riêng, không lẫn vào lỗi thật.
- SubmitReflexWrittenAnswerRequest.answerText có @NotBlank — nộp rỗng sẽ bị
  chặn ở tầng validation (HTTP 400) NGAY, không rơi vào "chưa chấm được" như
  ESSAY của UC-40/41. Vì vậy KHÔNG có category "blank" ở đây theo mặc định.

## Nội dung "bậc thang" 3 lần (2026-09-24)

Mục đích (đã xác nhận với người dùng): mô phỏng học sinh thật cải thiện qua
từng lần, VÀ dùng tối đa số lần làm lại để ước lượng trần token.

- **Viết** — `reflex_attempt_ladder.json`: mỗi câu có 3 bản — lần 1 đúng chủ
  đề nhưng nhiều lỗi ngữ pháp (trượt), lần 2 còn ~2 lỗi (vẫn trượt), lần 3
  chuẩn (đạt).
- **Nói** — `results/reflex_ladder_audio_map.csv` (sinh bởi
  `generate_ladder_audio.py` + `upload_reflex_audio.py`): cả 3 lần đều ĐỌC LẠI
  ĐÚNG bài viết lần 3, vì Reflex V2 bắt buộc bài nói khớp bài viết >= 45% từ
  nội dung (`ReflexContentOverlap`), nói khác nội dung sẽ bị 422 và không
  được chấm. Chỉ khác độ trôi chảy: s1 ngập ngừng + ngắt 1.2s → s2 ngắt
  0.6s → s3 lưu loát. Cắt ở 20s = giới hạn ghi âm của câu hỏi.
- Dừng khi đạt (giống học sinh thật) — nếu AI cho đạt sớm hơn dự kiến, phần
  thống kê "số lần thử thực tế" sẽ phản ánh đúng.
- Câu không có trong file bậc thang → dùng nội dung mặc định cũ; thiếu audio
  bậc thang → dùng `REFLEX_AUDIO_URLS` (xoay vòng); không có cả hai → chỉ
  chạy phần viết.

## Chuẩn bị trước khi chạy (bắt buộc, người dùng tự làm)

1. 1 bộ Video phản xạ TEST riêng (không phải bộ đang giao thật cho lớp), đã
   giao (UC-21) cho lớp của 50 tài khoản test -- lấy REFLEX_ASSIGNMENT_ID và
   danh sách REFLEX_QUESTION_IDS (id các câu REFLEX thuộc bộ đó, ĐÚNG thứ tự
   nếu muốn mô phỏng học sinh làm tuần tự theo displayOrder thật).
2. 50 tài khoản học sinh test (đã dùng ở loadtest_writing_submissions.py) --
   file students.csv cùng thư mục, dùng lại y hệt.

KHÔNG dùng bộ Video phản xạ/lần giao đang giao thật cho học sinh thật --
reflex_question_progress KHÔNG có cột đánh dấu "đây là dữ liệu test" (giống
lưu ý ở loadtest_reflex_ai_grading.js) -- request ở đây sẽ GHI ĐÈ trực tiếp
tiến trình thật của đúng (học sinh, câu hỏi, lần giao) nếu dùng nhầm bộ thật.

## Chạy (PowerShell)

    $env:TARGET_URL = "https://admin-staging.ppsvietnam.edu.vn"
    $env:TEST_PASSWORD = "<mật_khẩu_dùng_chung>"
    $env:REFLEX_ASSIGNMENT_ID = "123"
    $env:REFLEX_QUESTION_IDS = "501,502,503,504,505"
    python3 loadtest_reflex_writing_speaking.py

Bật thêm phần nói (khi đã có audio):

    $env:REFLEX_AUDIO_URLS = "https://files-staging.ppsvietnam.edu.vn/.../sample1.webm,https://.../sample2.webm"

Tuỳ chọn khác: MAX_WRITING_ATTEMPTS (mặc định 3), MAX_SPEAKING_ATTEMPTS (mặc
định 3), MAX_CONCURRENT_STUDENTS (mặc định 5), ROUND_DELAY_SECONDS (nghỉ giữa
các lượt nộp của CÙNG 1 học sinh, mặc định 2), WRITING_VARIANTS_FILE (JSON
list nội dung viết tự cung cấp, thay mẫu mặc định), STUDENT_LIST_CSV /
STUDENT_USERNAMES (giống loadtest_writing_submissions.py), OUTPUT_DIR.

## Ghi realtime (2026-09-24, theo yêu cầu người dùng sau sự cố mất dữ liệu

khi dừng tiến trình giữa chừng 2026-09-23 -- phải truy DB thủ công để dựng
lại số liệu). CSV chi tiết giờ được ghi NGAY TỪNG DÒNG (kèm flush + fsync)
ngay khi có kết quả, KHÔNG đợi tới lúc toàn bộ học sinh chạy xong mới ghi 1
lần -- nếu bạn dừng tiến trình giữa chừng, file CSV trong `results/` vẫn có
đủ mọi dòng đã chạy tính tới lúc dừng, mở xem được ngay cả khi script vẫn
đang chạy.
"""

import csv
import json
import os
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone

import requests

from realtime_sinks import open_google_spreadsheet, open_result_sinks, start_token_listener_if_configured

# ---------------------------------------------------------------------------
# CONFIG
# ---------------------------------------------------------------------------

BASE_URL = os.environ.get("TARGET_URL", "").rstrip("/")
PASSWORD = os.environ.get("TEST_PASSWORD", "")

_SCRIPT_DIR = os.path.dirname(__file__)
_DEFAULT_STUDENT_LIST_CSV = os.path.join(_SCRIPT_DIR, "students.csv")


def _load_usernames_from_csv(path):
    with open(path, newline="", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        columns = [c.strip() for c in (reader.fieldnames or [])]
        if "username" not in columns:
            raise ValueError(f"{path}: thieu cot 'username' -- cot dang co: {columns}")
        return [row["username"].strip() for row in reader if row.get("username", "").strip()]


_explicit_usernames = [u.strip() for u in os.environ.get("STUDENT_USERNAMES", "").split(",") if u.strip()]
_student_list_csv = os.environ.get("STUDENT_LIST_CSV", "")

if _explicit_usernames:
    USERNAMES = _explicit_usernames
elif _student_list_csv:
    USERNAMES = _load_usernames_from_csv(_student_list_csv)
elif os.path.isfile(_DEFAULT_STUDENT_LIST_CSV):
    print(f"Khong dat STUDENT_USERNAMES/STUDENT_LIST_CSV -- tu doc danh sach tu {_DEFAULT_STUDENT_LIST_CSV}")
    USERNAMES = _load_usernames_from_csv(_DEFAULT_STUDENT_LIST_CSV)
else:
    USERNAMES = []

REFLEX_ASSIGNMENT_ID = os.environ.get("REFLEX_ASSIGNMENT_ID", "")
REFLEX_QUESTION_IDS = [q.strip() for q in os.environ.get("REFLEX_QUESTION_IDS", "").split(",") if q.strip()]
REFLEX_AUDIO_URLS = [u.strip() for u in os.environ.get("REFLEX_AUDIO_URLS", "").split(",") if u.strip()]
MAX_WRITING_ATTEMPTS = int(os.environ.get("MAX_WRITING_ATTEMPTS", "3"))
MAX_SPEAKING_ATTEMPTS = int(os.environ.get("MAX_SPEAKING_ATTEMPTS", "3"))
MAX_CONCURRENT_STUDENTS = int(os.environ.get("MAX_CONCURRENT_STUDENTS", "5"))
ROUND_DELAY_SECONDS = float(os.environ.get("ROUND_DELAY_SECONDS", "2"))
REQUEST_TIMEOUT_SECONDS = float(os.environ.get("REQUEST_TIMEOUT_SECONDS", "60"))
WRITING_VARIANTS_FILE = os.environ.get("WRITING_VARIANTS_FILE", "")
OUTPUT_DIR = os.environ.get("OUTPUT_DIR", os.path.join(_SCRIPT_DIR, "results"))

if not BASE_URL or not PASSWORD or not USERNAMES or not REFLEX_ASSIGNMENT_ID or not REFLEX_QUESTION_IDS:
    print(
        "Thieu tham so bat buoc -- can TARGET_URL, TEST_PASSWORD, danh sach hoc sinh "
        "(STUDENT_USERNAMES/STUDENT_LIST_CSV/students.csv cung thu muc), "
        "REFLEX_ASSIGNMENT_ID, REFLEX_QUESTION_IDS. Xem huong dan o dau file.",
        file=sys.stderr,
    )
    sys.exit(1)

REFLEX_ASSIGNMENT_ID = int(REFLEX_ASSIGNMENT_ID)

# ---------------------------------------------------------------------------
# Noi dung tung lan thu -- "bac thang" (reflex_attempt_ladder.json, 2026-09-24):
# moi cau hoi, VIET lan 1 = dung chu de nhung nhieu loi ngu phap (truot), lan
# 2 = sua gan het con ~2 loi (van truot), lan 3 = chuan (dat). NOI lan 1 -> 3
# (reflex_ladder_audio_map.csv, sinh boi generate_ladder_audio.py) deu DOC LAI
# DUNG bai viet lan 3 -- Reflex V2 bat buoc bai noi khop bai viet >= 45% tu
# noi dung (ReflexContentOverlap), khac noi dung -> 422 khong duoc cham -- chi
# khac do troi chay: ngap ngung + ngat 1.2s -> ngat 0.6s -> luu loat.
# 2 muc dich (da xac nhan voi nguoi dung): mo phong hoc sinh that cai thien qua
# tung lan, VA dung toi da so lan lam lai de uoc luong tran token.
# Van dung khi dat (hoc sinh that khong nop tiep khi da dat) -- neu AI cho dat
# som hon du kien, thong ke se ghi nhan so lan thuc te.
# Fallback: cau hoi khong co trong ladder -> _DEFAULT_WRITING_VARIANTS / 
# REFLEX_AUDIO_URLS nhu ban dau.
# ---------------------------------------------------------------------------

REFLEX_LADDER_FILE = os.environ.get("REFLEX_LADDER_FILE", os.path.join(_SCRIPT_DIR, "reflex_attempt_ladder.json"))
REFLEX_LADDER_AUDIO_MAP = os.environ.get("REFLEX_LADDER_AUDIO_MAP", os.path.join(OUTPUT_DIR, "reflex_ladder_audio_map.csv"))
LADDER_LEVELS = ["s1", "s2", "s3"]


def load_ladder(path):
    if not os.path.isfile(path):
        return {}
    with open(path, encoding="utf-8") as f:
        return {k: v for k, v in json.load(f).items() if not k.startswith("_")}


def load_ladder_audio(path):
    """results/reflex_ladder_audio_map.csv (filename,url,error) -> {(question_id, "s1"): url}."""
    if not os.path.isfile(path):
        return {}
    mapping = {}
    with open(path, newline="", encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            if row.get("error") or not row.get("url"):
                continue
            stem = os.path.splitext(row["filename"])[0]  # "q46_s1"
            if stem.startswith("q") and "_" in stem:
                qpart, level = stem[1:].split("_", 1)
                mapping[(qpart, level)] = row["url"]
    return mapping


LADDER = load_ladder(REFLEX_LADDER_FILE)
LADDER_AUDIO = load_ladder_audio(REFLEX_LADDER_AUDIO_MAP)


def load_ladder_transcripts(path):
    """results/reflex_ladder_transcripts.csv (sinh boi publish_audio_transcripts_sheet.py) -> {"q46_s1": transcript}."""
    if not os.path.isfile(path):
        return {}
    with open(path, newline="", encoding="utf-8-sig") as f:
        return {row["stem"]: row["transcript"] for row in csv.DictReader(f)}


LADDER_TRANSCRIPTS = load_ladder_transcripts(
    os.environ.get("REFLEX_LADDER_TRANSCRIPTS", os.path.join(OUTPUT_DIR, "reflex_ladder_transcripts.csv")))

_missing_writing = [q for q in REFLEX_QUESTION_IDS if q not in LADDER]
if _missing_writing:
    print(f"Phan VIET: {len(_missing_writing)} cau khong co trong {REFLEX_LADDER_FILE} -> dung noi dung mac dinh: {_missing_writing}")
else:
    print(f"Phan VIET: bac thang 3 lan cho ca {len(REFLEX_QUESTION_IDS)} cau (doc tu {REFLEX_LADDER_FILE}).")

SPEAKING_MODE = "none"
if LADDER_AUDIO and all((q, lv) in LADDER_AUDIO for q in REFLEX_QUESTION_IDS for lv in LADDER_LEVELS):
    SPEAKING_MODE = "ladder"
    print(f"Phan NOI: bac thang 3 lan, audio doc lai bai viet (doc tu {REFLEX_LADDER_AUDIO_MAP}).")
elif REFLEX_AUDIO_URLS:
    SPEAKING_MODE = "flat_list"
    print("Phan NOI: thieu audio bac thang cho 1 so cau, fallback ve REFLEX_AUDIO_URLS.")
else:
    print("Phan NOI: KHONG co audio (thieu ca bac thang lan REFLEX_AUDIO_URLS) -- CHI chay phan VIET.")

_DEFAULT_WRITING_VARIANTS = [
    {"label": "bad_grammar",
     "text": "I is very happy because I go to park yesterday with my friend and we is playing football together."},
    {"label": "short",
     "text": "I like it."},
    {"label": "good_grammar",
     "text": "I feel very happy because I went to the park yesterday with my friend, and we played football together."},
]


def load_writing_variants():
    if not WRITING_VARIANTS_FILE:
        return _DEFAULT_WRITING_VARIANTS
    with open(WRITING_VARIANTS_FILE, "r", encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, list) or not all("label" in d and "text" in d for d in data):
        raise ValueError("WRITING_VARIANTS_FILE phai la JSON list cac {\"label\":..., \"text\":...}")
    return data


WRITING_VARIANTS = load_writing_variants()


def pick_writing_text(question_id, attempt_index):
    """-> {"label", "text"}. Lan thu vuot qua 3 dung lai ban chuan (lan 3)."""
    item = LADDER.get(question_id)
    if item:
        idx = min(attempt_index, len(item["writing"]) - 1)
        return {"label": f"w{idx + 1}", "text": item["writing"][idx]}
    return WRITING_VARIANTS[attempt_index % len(WRITING_VARIANTS)]


def pick_audio(question_id, attempt_index):
    """-> (label, url). ladder: s1/s2/s3 theo lan thu; flat_list: xoay vong REFLEX_AUDIO_URLS."""
    if SPEAKING_MODE == "ladder":
        level = LADDER_LEVELS[min(attempt_index, len(LADDER_LEVELS) - 1)]
        return level, LADDER_AUDIO[(question_id, level)]
    url = REFLEX_AUDIO_URLS[min(attempt_index, len(REFLEX_AUDIO_URLS) - 1)]
    return url, url


# ---------------------------------------------------------------------------
# API PPS Education that (xem ReflexSequentialGradingController.java)
# ---------------------------------------------------------------------------


class ApiError(Exception):
    pass


def login(session, username):
    resp = session.post(
        f"{BASE_URL}/api/auth/login",
        json={"usernameOrEmail": username, "password": PASSWORD},
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    if resp.status_code == 423:
        raise ApiError(f"Tai khoan {username} bi khoa (423) -- kiem tra lai TEST_PASSWORD, dung lai.")
    if resp.status_code == 409:
        raise ApiError(
            f"Tai khoan {username} dang co phien dang nhap ACTIVE khac (409) -- co the lan chay truoc "
            "bi ngat giua chung chua kip logout. Go session ket bang SQL (xem LOADTEST.md) roi chay lai."
        )
    if resp.status_code != 200:
        raise ApiError(f"Login that bai cho {username}: HTTP {resp.status_code} {resp.text}")
    body = resp.json()
    return body["accessToken"], body["refreshToken"]


def logout(session, headers, refresh_token, username):
    try:
        session.post(
            f"{BASE_URL}/api/auth/logout",
            json={"refreshToken": refresh_token},
            headers=headers,
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
    except requests.RequestException as e:
        print(f"[{username}] canh bao: logout loi ({e}) -- co the can go session ket bang SQL sau.")


def submit_written_answer(session, headers, question_id, answer_text):
    resp = session.put(
        f"{BASE_URL}/api/review-video-questions/{question_id}/reflex-progress/writing",
        params={"assignmentId": REFLEX_ASSIGNMENT_ID},
        json={"answerText": answer_text},
        headers=headers,
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    return resp


def submit_spoken_answer(session, headers, question_id, audio_url):
    resp = session.put(
        f"{BASE_URL}/api/review-video-questions/{question_id}/reflex-progress/speaking",
        params={"assignmentId": REFLEX_ASSIGNMENT_ID},
        json={"audioUrl": audio_url},
        headers=headers,
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    return resp


# ---------------------------------------------------------------------------
# 1 hoc sinh -- login 1 lan, lap qua tung cau hoi (viet -> [noi]), logout cuoi
# ---------------------------------------------------------------------------


def run_student(username, csv_writer):
    results = []

    def record(row):
        results.append(row)
        csv_writer.write_row(row)

    session = requests.Session()
    access_token = refresh_token = None
    try:
        access_token, refresh_token = login(session, username)
        headers = {"Authorization": f"Bearer {access_token}"}

        for question_id in REFLEX_QUESTION_IDS:
            writing_passed = False
            for attempt in range(MAX_WRITING_ATTEMPTS):
                variant = pick_writing_text(question_id, attempt)
                row = _blank_row(username, question_id, "writing", attempt + 1, variant["label"])
                row["submitted_content"] = variant["text"]
                try:
                    start = time.monotonic()
                    resp = submit_written_answer(session, headers, question_id, variant["text"])
                    elapsed_ms = (time.monotonic() - start) * 1000
                    row["http_status"] = resp.status_code
                    row["latency_ms"] = round(elapsed_ms, 1)
                    if resp.status_code == 400:
                        row["error"] = f"HTTP 400 (validation): {resp.text}"
                    elif resp.status_code != 200:
                        row["error"] = f"HTTP {resp.status_code}: {resp.text}"
                    else:
                        body = resp.json()
                        row["passed"] = body.get("writingPassed")
                        row["score_percent"] = body.get("writingScorePercent")
                        row["feedback"] = body.get("writingFeedback") or body.get("writingMarkedAnswer")
                        row["server_attempt_count"] = body.get("writingAttemptCount")
                        row["corrected_answer_hint"] = body.get("writingCorrectedAnswer")
                        writing_passed = bool(body.get("writingPassed"))
                except requests.RequestException as e:
                    row["error"] = f"Loi mang/timeout: {e}"

                record(row)
                _print_row(username, row)
                time.sleep(ROUND_DELAY_SECONDS)
                if writing_passed:
                    break

            if not writing_passed:
                print(f"[{username}] cau {question_id}: KHONG dat phan viet sau {MAX_WRITING_ATTEMPTS} lan -- bo qua phan noi, sang cau tiep.")
                continue

            if SPEAKING_MODE == "none":
                continue  # da bao o dau script, khong lap lai canh bao moi cau

            # ladder (s1 -> s2 -> s3) hoac flat_list -- thu lai toi da MAX_SPEAKING_ATTEMPTS lan,
            # dung khi dat. 422 (noi khac bai viet) KHONG tinh luot phia server nhung van tinh 1 lan thu o day.
            speaking_passed = False
            for attempt in range(MAX_SPEAKING_ATTEMPTS):
                label, audio_url = pick_audio(question_id, attempt)
                row = _blank_row(username, question_id, "speaking", attempt + 1, label)
                row["audio_url"] = audio_url
                row["submitted_content"] = LADDER_TRANSCRIPTS.get(f"q{question_id}_{label}")
                try:
                    start = time.monotonic()
                    resp = submit_spoken_answer(session, headers, question_id, audio_url)
                    elapsed_ms = (time.monotonic() - start) * 1000
                    row["http_status"] = resp.status_code
                    row["latency_ms"] = round(elapsed_ms, 1)
                    if resp.status_code == 422:
                        row["error"] = f"HTTP 422 (audio bi tu choi, KHONG tinh luot server): {resp.text}"
                    elif resp.status_code == 400:
                        row["error"] = f"HTTP 400: {resp.text}"
                    elif resp.status_code != 200:
                        row["error"] = f"HTTP {resp.status_code}: {resp.text}"
                    else:
                        body = resp.json()
                        row["passed"] = body.get("speakingPassed")
                        row["score_percent"] = body.get("speakingScorePercent")
                        row["feedback"] = body.get("speakingFeedback") or body.get("speakingTranscript")
                        row["server_attempt_count"] = body.get("speakingAttemptCount")
                        speaking_passed = bool(body.get("speakingPassed"))
                except requests.RequestException as e:
                    row["error"] = f"Loi mang/timeout: {e}"

                record(row)
                _print_row(username, row)
                time.sleep(ROUND_DELAY_SECONDS)
                if speaking_passed:
                    break

            if not speaking_passed:
                print(f"[{username}] cau {question_id}: KHONG dat phan noi sau {MAX_SPEAKING_ATTEMPTS} lan.")

    except ApiError as e:
        print(f"[{username}] BO QUA hoc sinh nay: {e}")
        record(_blank_row(username, None, "login", 0, None, error=f"Login that bai: {e}"))
    finally:
        if refresh_token:
            logout(session, {"Authorization": f"Bearer {access_token}"}, refresh_token, username)
        session.close()

    return results


def _blank_row(username, question_id, step, attempt, variant_or_audio, error=None):
    return {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "student_username": username,
        "question_id": question_id,
        "step": step,
        "attempt": attempt,
        "content_variant_or_audio": variant_or_audio,
        "submitted_content": None,
        "audio_url": None,
        "http_status": None,
        "latency_ms": None,
        "passed": None,
        "score_percent": None,
        "feedback": None,
        "server_attempt_count": None,
        "corrected_answer_hint": None,
        "error": error,
    }


def _print_row(username, row):
    if row["error"]:
        outcome = f"loi: {row['error'][:120]}"
    else:
        outcome = f"passed={row['passed']} score%={row['score_percent']}"
    print(f"[{username}] cau {row['question_id']} {row['step']} lan {row['attempt']} "
          f"({row['content_variant_or_audio']!r}) -> {outcome}")


CSV_FIELDNAMES = [
    "timestamp", "student_username", "question_id", "step", "attempt",
    "content_variant_or_audio", "submitted_content", "audio_url", "http_status", "latency_ms", "passed",
    "score_percent", "feedback", "server_attempt_count", "corrected_answer_hint", "error",
]




def print_summary(all_rows):
    print("\n" + "=" * 78)
    print("THONG KE")
    print("=" * 78)
    for step in ("writing", "speaking"):
        rows = [r for r in all_rows if r["step"] == step]
        if not rows:
            continue
        n = len(rows)
        errors = sum(1 for r in rows if r["error"])
        passed = sum(1 for r in rows if r["passed"] is True)
        # so cau hoi rieng biet co it nhat 1 lan dat step nay
        keys_passed = {(r["student_username"], r["question_id"]) for r in rows if r["passed"] is True}
        keys_total = {(r["student_username"], r["question_id"]) for r in rows}
        print(f"{step:10} : {n} luot nop, {errors} loi, {passed} luot dat, "
              f"{len(keys_passed)}/{len(keys_total)} (hoc sinh,cau hoi) dat duoc step nay "
              f"({round(len(keys_passed)/len(keys_total)*100,1) if keys_total else 0}%)")
    total_errors = sum(1 for r in all_rows if r["error"])
    print(f"\nTong: {len(all_rows)} luot nop (ca viet+noi+loi login), {total_errors} loi.")

    print("\n" + "-" * 78)
    print("THEO TUNG LAN THU (ky vong bac thang: lan 1-2 truot, lan 3 dat)")
    print("-" * 78)
    header = f"{'Buoc':<10}{'Lan':>5}{'So luot':>9}{'Loi':>6}{'Dat':>6}{'%dat':>8}{'Diem TB%':>10}"
    print(header)
    for step in ("writing", "speaking"):
        for attempt in (1, 2, 3):
            rows = [r for r in all_rows if r["step"] == step and r["attempt"] == attempt]
            if not rows:
                continue
            ok = [r for r in rows if not r["error"]]
            passed = sum(1 for r in ok if r["passed"] is True)
            scores = [r["score_percent"] for r in ok if r["score_percent"] is not None]
            avg = f"{sum(scores) / len(scores):.1f}%" if scores else "-"
            pct = f"{passed / len(ok) * 100:.1f}%" if ok else "-"
            print(f"{step:<10}{attempt:>5}{len(rows):>9}{len(rows) - len(ok):>6}{passed:>6}{pct:>8}{avg:>10}")

    # Phan bo so lan thu thuc te moi (hoc sinh, cau hoi) -- dung de nhan voi token TB/luot
    # khi uoc luong chi phi cho 1 lop that.
    print("\nSo lan thu thuc te moi (hoc sinh, cau hoi):")
    for step in ("writing", "speaking"):
        per_combo = {}
        for r in all_rows:
            if r["step"] == step:
                key = (r["student_username"], r["question_id"])
                per_combo[key] = max(per_combo.get(key, 0), r["attempt"])
        if per_combo:
            dist = {n: sum(1 for v in per_combo.values() if v == n) for n in sorted(set(per_combo.values()))}
            mean = sum(per_combo.values()) / len(per_combo)
            print(f"  {step:<9}: TB {mean:.2f} lan/cau -- phan bo {dist}")


def main():
    print(f"Chay test Reflex: {len(USERNAMES)} hoc sinh x {len(REFLEX_QUESTION_IDS)} cau hoi, "
          f"toi da {MAX_WRITING_ATTEMPTS} lan viet + {MAX_SPEAKING_ATTEMPTS} lan noi/cau, "
          f"target={BASE_URL}, assignment={REFLEX_ASSIGNMENT_ID}, "
          f"do dong thoi={MAX_CONCURRENT_STUDENTS}, "
          f"phan noi={SPEAKING_MODE}")

    run_tag = datetime.now().strftime("%Y%m%d-%H%M%S")
    spreadsheet = open_google_spreadsheet()
    # Mo kenh token TRUOC khi ban request dau tien -- SSE chi nhan su kien xay ra luc dang nghe.
    listener = start_token_listener_if_configured(
        BASE_URL, OUTPUT_DIR, run_tag, spreadsheet, _student_list_csv or _DEFAULT_STUDENT_LIST_CSV)
    csv_writer = open_result_sinks(OUTPUT_DIR, "reflex-writing-speaking", CSV_FIELDNAMES, spreadsheet, run_tag)
    all_rows = []
    try:
        with ThreadPoolExecutor(max_workers=MAX_CONCURRENT_STUDENTS) as pool:
            futures = {pool.submit(run_student, username, csv_writer): username for username in USERNAMES}
            for future in as_completed(futures):
                username = futures[future]
                try:
                    all_rows.extend(future.result())
                except Exception as e:  # noqa: BLE001
                    print(f"[{username}] loi khong mong doi: {e}")
                    unexpected_row = _blank_row(username, None, "unexpected", 0, None, error=str(e))
                    all_rows.append(unexpected_row)
                    csv_writer.write_row(unexpected_row)
    finally:
        csv_writer.close()
        if listener is not None:
            listener.stop()

    print(f"\nDa ghi chi tiet vao: {csv_writer.path}")
    print_summary(all_rows)
    if listener is not None:
        listener.print_summary()


if __name__ == "__main__":
    main()
