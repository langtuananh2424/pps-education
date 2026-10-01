"""
Bắn bài nộp Writing (ESSAY) cho cả lớp qua API THẬT của PPS Education, để
thống kê hệ thống chấm AI (WritingAiGradingService, V138 -- xem
ExerciseAttemptService#gradeEssayWithAi) phản hồi ra sao với nhiều DẠNG bài
khác nhau (ngắn/dài/lạc đề/để trống/...).

Đây KHÔNG phải load test hiệu năng (xem loadtest_reflex_ai_grading.js cho
mục đích đó) -- mục tiêu ở đây là CHẤT LƯỢNG/HÀNH VI chấm bài theo từng loại
input, không phải tìm trần capacity. Mặc định chạy với độ đồng thời thấp
(MAX_CONCURRENT_STUDENTS=5, khớp app.ai-grading.nine-router-max-concurrent
mặc định) để không làm nhiễu kết quả bởi nghẽn hàng đợi.

## Chuẩn bị trước khi chạy (bắt buộc, người dùng tự làm)

1. Một Bài (Exercise) có skillCategory=WRITING, gồm ĐÚNG 1 câu hỏi loại
   ESSAY -- lấy questionId của câu đó (QUESTION_ID).
2. Bài đó đã được giao (UC-21, deliverToClass) cho 1 lớp TEST riêng --
   lấy exerciseId (EXERCISE_ID) và id lần giao (ASSIGNMENT_ID).
   allowRetake=true và maxAttempts >= SUBMISSIONS_PER_STUDENT (mặc định 3),
   nếu không startAttempt() sẽ báo lỗi "hết lượt làm" từ lần nộp thứ 2/3.
3. ~50 tài khoản HỌC SINH TEST (không phải học sinh thật) dùng CHUNG 1 mật
   khẩu, đã enrolled ACTIVE vào đúng lớp được giao Bài ở bước 2.

KHÔNG dùng Bài/lần giao đang giao thật cho học sinh thật -- StudentAnswerGrading
không có cột đánh dấu "đây là dữ liệu test" để dọn tự động (giống lưu ý ở
loadtest_reflex_ai_grading.js cho reflex_question_progress); điểm/nhận xét AI
sinh ra ở đây sẽ nằm lẫn vào dữ liệu chấm bài thật của lớp đó.

## Lưu ý QUAN TRỌNG về dạng "để trống" (blank)

WritingAiGradingService.grade() trả `null` ngay khi essayText rỗng/blank,
KHÔNG gọi AI (xem WritingAiGradingService.java:166-168) -- câu trả lời đó
sẽ CHỜ CHẤM TAY (UC-41) mãi mãi qua đường tự động, gradingScore/gradingFeedback
luôn là null. Đây là hành vi thiết kế của hệ thống, không phải lỗi của script
-- thống kê ở dưới sẽ tự tách riêng "chưa được chấm" cho dạng này, đừng hiểu
nhầm là "0 điểm".

## Danh sách học sinh -- cách đơn giản nhất

Đặt file `students.csv` NẰM CÙNG THƯ MỤC với script này (xem mẫu định dạng ở
`students.csv.sample`, cột bắt buộc là `username`) -- script TỰ ĐỘNG đọc file
này nếu không đặt STUDENT_USERNAMES/STUDENT_LIST_CSV. Copy nội dung từ
`security-lab/results/students_*.csv` (xuất từ `sql_get_usernames_from_student_codes.sql`)
vào đây là dùng được ngay, không cần gõ tay danh sách username mỗi lần chạy.

## Chạy (bash/Linux/macOS -- xem PowerShell bên dưới nếu chạy trên Windows)

    pip install requests   # nếu máy chưa có

    TARGET_URL=https://admin-staging.ppsvietnam.edu.vn \
    TEST_PASSWORD='<mật_khẩu_dùng_chung>' \
    EXERCISE_ID=123 ASSIGNMENT_ID=456 QUESTION_ID=789 \
    python3 loadtest_writing_submissions.py

(giả sử đã có sẵn `students.csv` cùng thư mục -- nếu chưa, thêm
STUDENT_USERNAME_PREFIX=hs.test STUDENT_COUNT=50, hoặc STUDENT_USERNAMES=...)

## Chạy trên Windows PowerShell

PowerShell KHÔNG hiểu cú pháp "VAR=value" nối dòng kiểu bash (dùng ký tự
backslash cuối dòng) -- mỗi biến môi trường phải đặt bằng $env:VAR = "value"
trên 1 dòng riêng, TRƯỚC khi gọi script:

    $env:TARGET_URL = "https://admin-staging.ppsvietnam.edu.vn"
    $env:TEST_PASSWORD = "<mật_khẩu_dùng_chung>"
    $env:EXERCISE_ID = "123"
    $env:ASSIGNMENT_ID = "456"
    $env:QUESTION_ID = "789"
    python3 loadtest_writing_submissions.py

Tùy chọn khác (xem đủ trong CONFIG bên dưới): SUBMISSIONS_PER_STUDENT (mặc
định 3), MAX_CONCURRENT_STUDENTS (mặc định 5), ROUND_DELAY_SECONDS (nghỉ
giữa các lần nộp của CÙNG 1 học sinh, mặc định 2), SUBMISSIONS_FILE (file
JSON tự cung cấp nội dung bài thay cho mẫu dựng sẵn -- xem
`load_categories()`), OUTPUT_DIR (mặc định `results/`).

## Ghi realtime (2026-09-24, theo yêu cầu người dùng sau sự cố mất dữ liệu
## khi dừng loadtest_reflex_writing_speaking.py giữa chừng)

CSV chi tiết được ghi NGAY TỪNG DÒNG (kèm flush + fsync), KHÔNG đợi tới lúc
toàn bộ 50 học sinh chạy xong mới ghi 1 lần như bản trước -- nếu bạn dừng
tiến trình giữa chừng (Ctrl+C, hoặc kill), file CSV trong `results/` vẫn có
đầy đủ mọi dòng đã chạy tính tới thời điểm dừng, mở xem được ngay cả khi
script vẫn đang chạy. Không cần truy DB thủ công để dựng lại số liệu như
trước nữa.

Kết quả: 1 file CSV chi tiết từng lượt nộp + in thống kê tổng hợp theo từng
dạng bài (tỷ lệ được AI chấm, điểm trung bình, thời gian phản hồi) ra màn hình.
"""

import csv
import json
import os
import random
import statistics
import string
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone

import requests

from realtime_sinks import open_google_spreadsheet, open_result_sinks, start_token_listener_if_configured

# ---------------------------------------------------------------------------
# CONFIG -- đọc từ biến môi trường, có giá trị mặc định an toàn (sẽ báo lỗi
# rõ ràng nếu thiếu tham số bắt buộc thay vì âm thầm chạy sai).
# ---------------------------------------------------------------------------

BASE_URL = os.environ.get("TARGET_URL", "").rstrip("/")
PASSWORD = os.environ.get("TEST_PASSWORD", "")

# Danh sách học sinh -- 3 cách, ưu tiên theo thứ tự dưới đây (cách sau chỉ
# dùng khi cách trước không có):
#   1. STUDENT_USERNAMES=user1,user2,...        (gõ tay/dán trực tiếp)
#   2. STUDENT_LIST_CSV=đường_dẫn_tới_file.csv  (cột "username", xem
#      sql_get_usernames_from_student_codes.sql để lấy từ pgAdmin)
#   3. Không đặt gì cả -- TỰ ĐỘNG đọc file `students.csv` NẰM CÙNG THƯ MỤC
#      với script này (xem students.csv.sample để biết định dạng, hoặc đổi
#      tên/copy nội dung từ security-lab/results/students_*.csv vào đây)
#   4. STUDENT_USERNAME_PREFIX+STUDENT_COUNT (sinh username kiểu hs.test01..NN
#      -- chỉ dùng khi tài khoản test đặt tên theo mẫu số thứ tự có sẵn)
_SCRIPT_DIR = os.path.dirname(__file__)
_DEFAULT_STUDENT_LIST_CSV = os.path.join(_SCRIPT_DIR, "students.csv")


def _load_usernames_from_csv(path):
    with open(path, newline="", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        columns = [c.strip() for c in (reader.fieldnames or [])]
        if "username" not in columns:
            raise ValueError(
                f"{path}: thiếu cột 'username' (xem students.csv.sample) -- cột đang có: {columns}"
            )
        return [row["username"].strip() for row in reader if row.get("username", "").strip()]


_explicit_usernames = [
    u.strip() for u in os.environ.get("STUDENT_USERNAMES", "").split(",") if u.strip()
]
_prefix = os.environ.get("STUDENT_USERNAME_PREFIX", "")
_count = int(os.environ.get("STUDENT_COUNT", "0") or 0)
_student_list_csv = os.environ.get("STUDENT_LIST_CSV", "")

if _explicit_usernames:
    USERNAMES = _explicit_usernames
elif _student_list_csv:
    USERNAMES = _load_usernames_from_csv(_student_list_csv)
elif os.path.isfile(_DEFAULT_STUDENT_LIST_CSV):
    print(f"Không đặt STUDENT_USERNAMES/STUDENT_LIST_CSV -- tự đọc danh sách từ {_DEFAULT_STUDENT_LIST_CSV}")
    USERNAMES = _load_usernames_from_csv(_DEFAULT_STUDENT_LIST_CSV)
elif _prefix and _count:
    width = len(str(_count))
    USERNAMES = [f"{_prefix}{str(i).zfill(width)}" for i in range(1, _count + 1)]
else:
    USERNAMES = []

EXERCISE_ID = os.environ.get("EXERCISE_ID", "")
ASSIGNMENT_ID = os.environ.get("ASSIGNMENT_ID", "")
QUESTION_ID = os.environ.get("QUESTION_ID", "")
SUBMISSIONS_PER_STUDENT = int(os.environ.get("SUBMISSIONS_PER_STUDENT", "3"))
MAX_CONCURRENT_STUDENTS = int(os.environ.get("MAX_CONCURRENT_STUDENTS", "5"))
ROUND_DELAY_SECONDS = float(os.environ.get("ROUND_DELAY_SECONDS", "2"))
REQUEST_TIMEOUT_SECONDS = float(os.environ.get("REQUEST_TIMEOUT_SECONDS", "60"))
SUBMISSIONS_FILE = os.environ.get("SUBMISSIONS_FILE", "")
OUTPUT_DIR = os.environ.get("OUTPUT_DIR", os.path.join(os.path.dirname(__file__), "results"))

if not BASE_URL or not PASSWORD or not USERNAMES or not EXERCISE_ID or not ASSIGNMENT_ID or not QUESTION_ID:
    print(
        "Thiếu tham số bắt buộc -- cần TARGET_URL, TEST_PASSWORD, danh sách học sinh "
        "(STUDENT_USERNAMES, hoặc STUDENT_LIST_CSV, hoặc file students.csv cùng thư mục script, "
        "hoặc STUDENT_USERNAME_PREFIX+STUDENT_COUNT), EXERCISE_ID, ASSIGNMENT_ID, QUESTION_ID. "
        "Xem hướng dẫn ở đầu file.",
        file=sys.stderr,
    )
    sys.exit(1)

EXERCISE_ID = int(EXERCISE_ID)
ASSIGNMENT_ID = int(ASSIGNMENT_ID)
QUESTION_ID = int(QUESTION_ID)

AI_NOT_GRADED_NOTE = "CHƯA ĐƯỢC AI CHẤM (chờ chấm tay UC-41)"


# ---------------------------------------------------------------------------
# Nội dung bài mẫu theo từng dạng -- CHỈNH LẠI cho sát với đề bài thật của
# QUESTION_ID nếu muốn thống kê "lạc đề"/"dài" chính xác hơn (script không
# biết nội dung đề, mẫu dưới đây chỉ mang tính minh hoạ chung chung).
# Dùng SUBMISSIONS_FILE để tự cung cấp nội dung thay cho mẫu này.
# ---------------------------------------------------------------------------

# V197 (2026-09-23) -- 3 mẫu dưới đây soạn RIÊNG cho đúng đề QUESTION_ID=940 đã
# xác nhận với người dùng (email của "Ben" hỏi 3 ý: sở thích hiện tại + lý do,
# tư vấn chọn photography/cooking, gợi ý hobby cho 2 người bạn) -- xem ảnh đề ở
# image_url của question đó. Nếu QUESTION_ID đổi sang đề khác, PHẢI viết lại 3
# mẫu này (hoặc dùng SUBMISSIONS_FILE) cho khớp đề mới, nếu không "long" dễ bị
# AI chấm lạc đề y như "off_topic" vì sai hẳn thể loại/chủ đề.
_LONG_ESSAY = (
    "Hi Ben,\n\n"
    "Thanks so much for your email, it's great to hear that you're doing a "
    "presentation about hobbies next week!\n\n"
    "At the moment, my favourite hobby is playing badminton. I enjoy it so "
    "much because it helps me stay fit, and I love the feeling of hitting "
    "the shuttlecock over the net with my friends after a long day at "
    "school. It's also a great way to relax and forget about homework for "
    "a while.\n\n"
    "About your question, I think you should try photography rather than "
    "cooking this summer. Photography lets you explore your neighbourhood, "
    "capture beautiful moments, and you can even share your photos online "
    "with friends. Cooking is fun too, but it can be more difficult to "
    "practise every day, especially in summer when the kitchen gets really "
    "hot!\n\n"
    "As for hobbies that two friends can enjoy together, I'd suggest "
    "cycling or playing badminton, just like I do. You could also try "
    "building a small vegetable garden together, which is relaxing and "
    "rewarding at the same time.\n\n"
    "Write back soon and let me know what you decide!\n\n"
    "Best,\nAnna"
)

_OFF_TOPIC_ESSAY = (
    "Hi Ben,\n\n"
    "Guess what? Manchester United won 3-0 last weekend and it was an "
    "amazing match! I watched it with my friends and we ordered pizza "
    "together. My favourite player scored two goals and everyone was so "
    "excited.\n\n"
    "I really think football is the best sport in the world. Maybe you "
    "should watch the next match with me!\n\n"
    "Talk soon,\nAnna"
)

_GIBBERISH_ESSAY = "asjdklasjd alksjdlka qwoieuqwoiue zxcvbnm 12903812 !!!??? lorem ipsum blah blah"

_KEYWORD_STUFFING_ESSAY = "good good good good good good good good good good very good good good"


def _default_categories():
    return {
        "short": ["I like study English very much."],
        "long": [_LONG_ESSAY],
        "off_topic": [_OFF_TOPIC_ESSAY],
        "blank": [""],
        "gibberish": [_GIBBERISH_ESSAY],
        "keyword_stuffing": [_KEYWORD_STUFFING_ESSAY],
    }


def load_categories():
    """Trả về dict {category_name: [danh sách nội dung mẫu]}.

    Mặc định dùng mẫu dựng sẵn. Nếu SUBMISSIONS_FILE được đặt, đọc JSON dạng
    {"category_name": ["nội dung 1", "nội dung 2", ...], ...} và GHI ĐÈ/BỔ
    SUNG lên mẫu mặc định (cho phép chỉ override 1-2 category, giữ nguyên
    phần còn lại).
    """
    categories = _default_categories()
    if SUBMISSIONS_FILE:
        with open(SUBMISSIONS_FILE, "r", encoding="utf-8") as f:
            custom = json.load(f)
        for name, texts in custom.items():
            if not isinstance(texts, list) or not all(isinstance(t, str) for t in texts):
                raise ValueError(f"SUBMISSIONS_FILE: category {name!r} phải là list các chuỗi")
            categories[name] = texts
    return categories


CATEGORIES = load_categories()
CATEGORY_NAMES = list(CATEGORIES.keys())


def pick_text(category, round_index):
    variants = CATEGORIES[category]
    return variants[round_index % len(variants)]


# ---------------------------------------------------------------------------
# API PPS Education thật (xem ExerciseAttemptController.java / AuthController)
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
        raise ApiError(f"Tài khoản {username} bị khoá (423) -- kiểm tra lại TEST_PASSWORD, dừng lại.")
    if resp.status_code == 409:
        raise ApiError(
            f"Tài khoản {username} đang có phiên đăng nhập ACTIVE khác (409) -- có thể lần chạy "
            "trước bị ngắt giữa chừng chưa kịp logout. Gỡ session kẹt bằng SQL (xem LOADTEST.md) "
            "rồi chạy lại."
        )
    if resp.status_code != 200:
        raise ApiError(f"Login thất bại cho {username}: HTTP {resp.status_code} {resp.text}")
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
        print(f"[{username}] cảnh báo: logout lỗi ({e}) -- có thể cần gỡ session kẹt bằng SQL sau.")


def start_attempt(session, headers):
    resp = session.post(
        f"{BASE_URL}/api/exercises/{EXERCISE_ID}/attempts",
        params={"assignmentId": ASSIGNMENT_ID},
        headers=headers,
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    if resp.status_code != 200:
        raise ApiError(f"startAttempt lỗi: HTTP {resp.status_code} {resp.text}")
    return resp.json()


def save_answer(session, headers, attempt_id, answer_text):
    resp = session.post(
        f"{BASE_URL}/api/attempts/{attempt_id}/answers",
        json={"questionId": QUESTION_ID, "answerText": answer_text},
        headers=headers,
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    if resp.status_code != 200:
        raise ApiError(f"saveAnswer lỗi: HTTP {resp.status_code} {resp.text}")
    return resp.json()


def submit_attempt(session, headers, attempt_id):
    resp = session.post(
        f"{BASE_URL}/api/attempts/{attempt_id}/submit",
        headers=headers,
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    if resp.status_code != 200:
        raise ApiError(f"submitAttempt lỗi: HTTP {resp.status_code} {resp.text}")
    return resp.json()


def list_answers(session, headers, attempt_id):
    resp = session.get(
        f"{BASE_URL}/api/attempts/{attempt_id}/answers",
        headers=headers,
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    if resp.status_code != 200:
        raise ApiError(f"listAnswers lỗi: HTTP {resp.status_code} {resp.text}")
    return resp.json()


# ---------------------------------------------------------------------------
# 1 học sinh -- login 1 lần, nộp SUBMISSIONS_PER_STUDENT lần, logout ở cuối
# ---------------------------------------------------------------------------


def run_student(username, category_plan, csv_writer):
    """category_plan: list độ dài SUBMISSIONS_PER_STUDENT, mỗi phần tử là tên category."""
    results = []
    session = requests.Session()
    access_token = refresh_token = None
    try:
        access_token, refresh_token = login(session, username)
        headers = {"Authorization": f"Bearer {access_token}"}

        for round_index, category in enumerate(category_plan):
            row = {
                "timestamp": datetime.now(timezone.utc).isoformat(),
                "student_username": username,
                "round": round_index + 1,
                "category": category,
                "attempt_id": None,
                "submit_http_status": None,
                "submit_latency_ms": None,
                "exercise_status": None,
                "grading_score": None,
                "grading_max_score": None,
                "grading_percent": None,
                "grading_feedback": None,
                "graded": False,
                "error": None,
            }
            try:
                text = pick_text(category, round_index)
                attempt = start_attempt(session, headers)
                attempt_id = attempt["id"]
                row["attempt_id"] = attempt_id

                save_answer(session, headers, attempt_id, text)

                start = time.monotonic()
                submitted = submit_attempt(session, headers, attempt_id)
                elapsed_ms = (time.monotonic() - start) * 1000
                row["submit_http_status"] = 200
                row["submit_latency_ms"] = round(elapsed_ms, 1)
                row["exercise_status"] = submitted.get("status")

                answers = list_answers(session, headers, attempt_id)
                answer = next((a for a in answers if a.get("questionId") == QUESTION_ID), None)
                if answer is not None:
                    score = answer.get("gradingScore")
                    max_score = answer.get("gradingMaxScore")
                    row["grading_score"] = score
                    row["grading_max_score"] = max_score
                    if score is not None and max_score not in (None, 0):
                        row["grading_percent"] = round(float(score) / float(max_score) * 100, 1)
                    feedback = answer.get("gradingFeedback")
                    row["graded"] = feedback is not None
                    row["grading_feedback"] = feedback if feedback is not None else AI_NOT_GRADED_NOTE
            except ApiError as e:
                row["error"] = str(e)
            except requests.RequestException as e:
                row["error"] = f"Lỗi mạng/timeout: {e}"

            results.append(row)
            csv_writer.write_row(row)
            if row["error"]:
                outcome = f"lỗi: {row['error']}"
            else:
                outcome = f"graded={row['graded']} score%={row['grading_percent']}"
            print(f"[{username}] round {round_index + 1}/{len(category_plan)} category={category!r} -> {outcome}")
            time.sleep(ROUND_DELAY_SECONDS)
    except ApiError as e:
        print(f"[{username}] BỎ QUA học sinh này: {e}")
        login_error_row = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "student_username": username,
            "round": 0,
            "category": None,
            "attempt_id": None,
            "submit_http_status": None,
            "submit_latency_ms": None,
            "exercise_status": None,
            "grading_score": None,
            "grading_max_score": None,
            "grading_percent": None,
            "grading_feedback": None,
            "graded": False,
            "error": f"Login thất bại: {e}",
        }
        results.append(login_error_row)
        csv_writer.write_row(login_error_row)
    finally:
        if refresh_token:
            logout(session, {"Authorization": f"Bearer {access_token}"}, refresh_token, username)
        session.close()

    return results


def build_category_plan():
    """Xoay vòng đều qua mọi category cho toàn bộ lớp, không chỉ lặp lại 1
    thứ tự cố định cho từng em -- để tổng số lượt của mỗi dạng cân bằng
    trên cả lớp thay vì lệch hẳn về vài dạng đầu tiên."""
    total_submissions = len(USERNAMES) * SUBMISSIONS_PER_STUDENT
    global_order = [CATEGORY_NAMES[i % len(CATEGORY_NAMES)] for i in range(total_submissions)]
    plans = {}
    idx = 0
    for username in USERNAMES:
        plans[username] = global_order[idx: idx + SUBMISSIONS_PER_STUDENT]
        idx += SUBMISSIONS_PER_STUDENT
    return plans


CSV_FIELDNAMES = [
    "timestamp", "student_username", "round", "category", "attempt_id",
    "submit_http_status", "submit_latency_ms", "exercise_status",
    "grading_score", "grading_max_score", "grading_percent",
    "graded", "grading_feedback", "error",
]




def print_summary(all_rows):
    print("\n" + "=" * 78)
    print("THỐNG KÊ THEO DẠNG BÀI")
    print("=" * 78)
    by_category = {}
    for row in all_rows:
        by_category.setdefault(row["category"], []).append(row)

    header = f"{'Dạng bài':<18}{'Số lượt':>8}{'Lỗi':>6}{'Đã chấm':>9}{'%đã chấm':>10}{'Điểm TB%':>10}{'Latency TB(ms)':>16}"
    print(header)
    print("-" * len(header))
    for category, rows in by_category.items():
        if category is None:
            continue
        n = len(rows)
        errors = sum(1 for r in rows if r["error"])
        graded_rows = [r for r in rows if r["graded"]]
        graded_count = len(graded_rows)
        graded_pct = round(graded_count / n * 100, 1) if n else 0.0
        percents = [r["grading_percent"] for r in graded_rows if r["grading_percent"] is not None]
        avg_percent = round(statistics.mean(percents), 1) if percents else None
        latencies = [r["submit_latency_ms"] for r in rows if r["submit_latency_ms"] is not None]
        avg_latency = round(statistics.mean(latencies), 0) if latencies else None
        print(
            f"{category:<18}{n:>8}{errors:>6}{graded_count:>9}{graded_pct:>9}%"
            f"{(str(avg_percent) + '%') if avg_percent is not None else '-':>10}"
            f"{(str(int(avg_latency))) if avg_latency is not None else '-':>16}"
        )

    total_errors = sum(1 for r in all_rows if r["error"])
    print("-" * len(header))
    print(f"Tổng: {len(all_rows)} lượt nộp, {total_errors} lỗi.")
    if any(r["category"] == "blank" for r in all_rows):
        print(
            "\nLưu ý: dạng 'blank' luôn graded=False (xem giải thích ở đầu file) -- "
            "KHÔNG tính là bug, đây là hành vi thiết kế của WritingAiGradingService."
        )


def main():
    print(f"Chạy nộp bài Writing: {len(USERNAMES)} học sinh x {SUBMISSIONS_PER_STUDENT} lượt "
          f"= {len(USERNAMES) * SUBMISSIONS_PER_STUDENT} lượt nộp, target={BASE_URL}, "
          f"exercise={EXERCISE_ID}, assignment={ASSIGNMENT_ID}, question={QUESTION_ID}, "
          f"độ đồng thời={MAX_CONCURRENT_STUDENTS}")
    print(f"Các dạng bài sẽ dùng: {', '.join(CATEGORY_NAMES)}")

    plans = build_category_plan()
    run_tag = datetime.now().strftime("%Y%m%d-%H%M%S")
    spreadsheet = open_google_spreadsheet()
    # Mo kenh token TRUOC khi ban request dau tien -- SSE chi nhan su kien xay ra luc dang nghe.
    listener = start_token_listener_if_configured(
        BASE_URL, OUTPUT_DIR, run_tag, spreadsheet, _student_list_csv or _DEFAULT_STUDENT_LIST_CSV)
    csv_writer = open_result_sinks(OUTPUT_DIR, "writing-submissions", CSV_FIELDNAMES, spreadsheet, run_tag)
    all_rows = []
    try:
        with ThreadPoolExecutor(max_workers=MAX_CONCURRENT_STUDENTS) as pool:
            futures = {pool.submit(run_student, username, plans[username], csv_writer): username for username in USERNAMES}
            for future in as_completed(futures):
                username = futures[future]
                try:
                    all_rows.extend(future.result())
                except Exception as e:  # noqa: BLE001 -- ghi nhận lỗi bất ngờ của 1 học sinh, không dừng cả lớp
                    print(f"[{username}] lỗi không mong đợi: {e}")
                    unexpected_row = {
                        "timestamp": datetime.now(timezone.utc).isoformat(),
                        "student_username": username,
                        "round": 0,
                        "category": None,
                        "attempt_id": None,
                        "submit_http_status": None,
                        "submit_latency_ms": None,
                        "exercise_status": None,
                        "grading_score": None,
                        "grading_max_score": None,
                        "grading_percent": None,
                        "grading_feedback": None,
                        "graded": False,
                        "error": f"Lỗi không mong đợi: {e}",
                    }
                    all_rows.append(unexpected_row)
                    csv_writer.write_row(unexpected_row)
    finally:
        # Ctrl+C van dong duoc file + day not lo con lai len Sheet + logout admin.
        csv_writer.close()
        if listener is not None:
            listener.stop()

    print(f"\nĐã ghi chi tiết vào: {csv_writer.path}")
    print_summary(all_rows)
    if listener is not None:
        listener.print_summary()


if __name__ == "__main__":
    random.seed()  # không dùng random thật sự ở bản này, giữ để dễ mở rộng sau (VD trộn ngẫu nhiên category)
    main()
