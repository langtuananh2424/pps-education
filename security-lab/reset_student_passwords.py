"""
Đổi mật khẩu hàng loạt cho các tài khoản học sinh test trên STAGING, dùng
đúng API thật UC-45 A4 (Quản trị viên đổi mật khẩu cho tài khoản khác --
PUT /api/users/{userId}/password, quyền `user.update`) thay vì sửa thẳng
password_hash trong DB -- lý do:

  - API tự hash bằng đúng BCryptPasswordEncoder đang chạy (SecurityConfig.java) --
    tự tay UPDATE password_hash dễ sai định dạng bcrypt ($2a$/$2b$, cost
    factor) hoặc lệch với cấu hình encoder thật nếu sau này đổi.
  - API tự thu hồi TOÀN BỘ refresh token đang active của tài khoản đó
    (UserAccountService#applyNewPassword) -- UPDATE thẳng DB sẽ để sót
    session cũ vẫn dùng được, dữ liệu load test dễ lẫn giữa mật khẩu cũ/mới.

CHỈ CHẠY NHẮM VÀO STAGING/TEST, KHÔNG PHẢI PRODUCTION -- script sẽ hỏi xác
nhận (gõ đúng "CONFIRM") trước khi đổi, in rõ TARGET_URL + số tài khoản bị
ảnh hưởng để bạn soát lại trước khi bấm tiếp.

## Chuẩn bị

1. Chạy `sql_get_usernames_from_student_codes.sql` trong pgAdmin (nhắm DB
   staging), xuất kết quả ra CSV (nút Export hoặc "Copy with Headers" rồi
   dán vào file .csv) -- cần tối thiểu cột `user_id` (và `username` để đối
   chiếu log cho dễ đọc).
2. Tài khoản ADMIN_USERNAME/ADMIN_PASSWORD dùng để CHẠY script này phải có
   quyền `user.update` (Quản trị viên/vai trò được gán quyền đó) -- không
   phải tài khoản học sinh.

## Chạy

    pip install requests

    TARGET_URL=https://admin-staging.ppsvietnam.edu.vn \
    ADMIN_USERNAME=<tài_khoản_admin> ADMIN_PASSWORD='<mật_khẩu_admin>' \
    NEW_STUDENT_PASSWORD='<mật_khẩu_mới_dùng_chung_cho_test>' \
    STUDENT_LIST_CSV=./ket-qua-truy-van.csv \
    python3 reset_student_passwords.py

Hoặc bỏ qua CSV, liệt kê thẳng user_id (khi chỉ có vài tài khoản):

    STUDENT_USER_IDS=101,102,103 python3 reset_student_passwords.py
"""

import csv
import os
import sys
from datetime import datetime

import requests

BASE_URL = os.environ.get("TARGET_URL", "").rstrip("/")
ADMIN_USERNAME = os.environ.get("ADMIN_USERNAME", "")
ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD", "")
NEW_STUDENT_PASSWORD = os.environ.get("NEW_STUDENT_PASSWORD", "")
STUDENT_LIST_CSV = os.environ.get("STUDENT_LIST_CSV", "")
STUDENT_USER_IDS_RAW = os.environ.get("STUDENT_USER_IDS", "")
REQUEST_TIMEOUT_SECONDS = float(os.environ.get("REQUEST_TIMEOUT_SECONDS", "30"))

if not BASE_URL or not ADMIN_USERNAME or not ADMIN_PASSWORD or not NEW_STUDENT_PASSWORD:
    print(
        "Thiếu tham số bắt buộc -- cần TARGET_URL, ADMIN_USERNAME, ADMIN_PASSWORD, "
        "NEW_STUDENT_PASSWORD. Xem hướng dẫn ở đầu file.",
        file=sys.stderr,
    )
    sys.exit(1)

if len(NEW_STUDENT_PASSWORD) < 8 or len(NEW_STUDENT_PASSWORD) > 72:
    # Khớp @Size(min=8, max=72) của AdminChangePasswordRequest -- 72 là giới
    # hạn cứng của BCrypt (byte vượt quá bị cắt âm thầm), báo sớm ở đây thay
    # vì để cả 50 request cùng fail vì 400.
    print("NEW_STUDENT_PASSWORD phải từ 8 đến 72 ký tự (giới hạn của BCrypt).", file=sys.stderr)
    sys.exit(1)


def load_user_ids():
    entries = []  # list of dict {user_id, username(optional), student_code(optional)}
    if STUDENT_LIST_CSV:
        with open(STUDENT_LIST_CSV, newline="", encoding="utf-8-sig") as f:
            reader = csv.DictReader(f)
            if reader.fieldnames is None or "user_id" not in [c.strip() for c in reader.fieldnames]:
                raise ValueError(
                    f"CSV {STUDENT_LIST_CSV!r} phải có cột 'user_id' (xem "
                    "sql_get_usernames_from_student_codes.sql) -- cột đang có: "
                    f"{reader.fieldnames}"
                )
            for row in reader:
                row = {k.strip(): (v.strip() if isinstance(v, str) else v) for k, v in row.items()}
                if not row.get("user_id"):
                    continue
                entries.append(row)
    elif STUDENT_USER_IDS_RAW:
        for uid in STUDENT_USER_IDS_RAW.split(","):
            uid = uid.strip()
            if uid:
                entries.append({"user_id": uid})
    else:
        print(
            "Thiếu STUDENT_LIST_CSV hoặc STUDENT_USER_IDS -- cần 1 trong 2 để biết đổi mật khẩu cho ai.",
            file=sys.stderr,
        )
        sys.exit(1)
    return entries


def login_admin(session):
    resp = session.post(
        f"{BASE_URL}/api/auth/login",
        json={"usernameOrEmail": ADMIN_USERNAME, "password": ADMIN_PASSWORD},
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    if resp.status_code == 423:
        print(f"Tài khoản admin {ADMIN_USERNAME} bị khoá (423) -- kiểm tra lại ADMIN_PASSWORD.", file=sys.stderr)
        sys.exit(1)
    if resp.status_code != 200:
        print(f"Login admin thất bại: HTTP {resp.status_code} {resp.text}", file=sys.stderr)
        sys.exit(1)
    return resp.json()["accessToken"], resp.json()["refreshToken"]


def reset_one(session, headers, user_id):
    resp = session.put(
        f"{BASE_URL}/api/users/{user_id}/password",
        json={"newPassword": NEW_STUDENT_PASSWORD},
        headers=headers,
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    return resp.status_code, (resp.text if resp.status_code != 204 else "")


def main():
    entries = load_user_ids()

    print("=" * 78)
    print(f"TARGET_URL       : {BASE_URL}")
    print(f"Admin thực hiện  : {ADMIN_USERNAME}")
    print(f"Số tài khoản bị đổi mật khẩu: {len(entries)}")
    print("Vài user_id đầu tiên:", ", ".join(str(e["user_id"]) for e in entries[:5]), "...")
    print("=" * 78)
    print(
        "CHỈ chạy tiếp nếu TARGET_URL ở trên là STAGING/TEST, không phải production, "
        "và danh sách user_id là tài khoản học sinh TEST."
    )
    answer = input("Gõ đúng chữ CONFIRM để tiếp tục: ").strip()
    if answer != "CONFIRM":
        print("Đã huỷ, không đổi mật khẩu nào.")
        sys.exit(0)

    session = requests.Session()
    access_token, _refresh_token = login_admin(session)
    headers = {"Authorization": f"Bearer {access_token}"}

    ok_count = 0
    fail_count = 0
    log_rows = []
    for entry in entries:
        user_id = entry["user_id"]
        label = entry.get("username") or entry.get("student_code") or user_id
        status, body = reset_one(session, headers, user_id)
        success = status == 204
        if success:
            ok_count += 1
        else:
            fail_count += 1
        print(f"[{label}] user_id={user_id} -> HTTP {status}{'' if success else ' ' + body}")
        log_rows.append(
            {
                "timestamp": datetime.now().isoformat(),
                "user_id": user_id,
                "username": entry.get("username", ""),
                "student_code": entry.get("student_code", ""),
                "http_status": status,
                "success": success,
                "error": "" if success else body,
            }
        )

    results_dir = os.path.join(os.path.dirname(__file__), "results")
    os.makedirs(results_dir, exist_ok=True)
    log_path = os.path.join(results_dir, f"reset-passwords-{datetime.now().strftime('%Y%m%d-%H%M%S')}.csv")
    with open(log_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(log_rows[0].keys()) if log_rows else [])
        writer.writeheader()
        writer.writerows(log_rows)

    print("=" * 78)
    print(f"Xong: {ok_count} thành công, {fail_count} lỗi. Log chi tiết: {log_path}")
    if fail_count:
        print("Lỗi có thể do: user_id sai/không tồn tại, tài khoản admin thiếu quyền 'user.update', hoặc mật khẩu mới không hợp lệ.")


if __name__ == "__main__":
    main()
