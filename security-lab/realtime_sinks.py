"""
Ghi ket qua load test REALTIME -- dung chung cho loadtest_writing_submissions.py
va loadtest_reflex_writing_speaking.py.

3 thanh phan:
- RealtimeCsvWriter: ghi tung dong ra CSV cuc bo (flush + fsync ngay) --
  NGUON CHAN LY, luon bat.
- GoogleSheetSink: day them tung dong len Google Sheet (tuy chon, chi bat khi
  dat GSHEET_ID + GOOGLE_SA_JSON). Gom dong vao hang doi va day theo lo moi
  GSHEET_FLUSH_SECONDS giay (mac dinh 5) de khong cham quota ghi cua Sheets
  API (~60 lan ghi/phut). Loi mang/quota KHONG lam hong bai test -- dong van
  nam trong CSV cuc bo, lo loi duoc thu lai o lan flush sau.
- TokenUsageListener: dang nhap ADMIN (quyen system.settings.manage), nghe
  kenh SSE GET /api/ai-token-usage/stream (AiTokenUsageController) suot luc
  test chay, ghi moi luot goi AI (step, token, elapsedMs, served model) ra
  CSV + Sheet rieng -- khong can truy bang ai_grading_token_usage bang SQL
  tay nua.

## Bat Google Sheet

1. Google Cloud Console -> tao Service Account -> tao key JSON, tai ve.
2. Bat "Google Sheets API" cho project do.
3. Tao 1 Google Sheet, bam Share, chia se quyen Editor cho email cua service
   account (dang ...@....iam.gserviceaccount.com) -- CHI chia se cho email
   do, khong de "Anyone with the link".
4. Dat bien moi truong:
       $env:GOOGLE_SA_JSON = "D:\\duong\\dan\\service-account.json"
       $env:GSHEET_ID = "<id nam giua /d/ va /edit trong URL sheet>"

Moi lan chay tao 2 tab MOI trong sheet (ket qua + token), khong ghi de tab cu.

Du lieu day len Sheet chi co USERNAME, KHONG day ho ten hoc sinh (su kien
token SSE co ho ten -- chi giu trong CSV cuc bo) -- 50 tai khoan test la tai
khoan hoc sinh that, han che dua ten that ra dich vu ngoai.
"""

import csv
import json
import os
import queue
import threading
import time
from datetime import datetime, timezone

import requests


# ---------------------------------------------------------------------------
# CSV cuc bo
# ---------------------------------------------------------------------------


class RealtimeCsvWriter:
    """Ghi tung dong ngay khi co, kem flush()+fsync() -- neu tien trinh bi
    kill giua chung, moi dong da chay van nam tren dia. Nhieu luong ghi dong
    thoi nen dung Lock de khong xen dong."""

    def __init__(self, path, fieldnames):
        self._lock = threading.Lock()
        self._file = open(path, "w", newline="", encoding="utf-8")
        self._writer = csv.DictWriter(self._file, fieldnames=fieldnames, extrasaction="ignore")
        self._writer.writeheader()
        self._file.flush()
        self.path = path

    def write_row(self, row):
        with self._lock:
            self._writer.writerow(row)
            self._file.flush()
            os.fsync(self._file.fileno())

    def close(self):
        with self._lock:
            self._file.close()


# ---------------------------------------------------------------------------
# Google Sheet
# ---------------------------------------------------------------------------


def _to_cell(v):
    if v is None:
        return ""
    if isinstance(v, bool):
        return "TRUE" if v else "FALSE"
    if isinstance(v, (int, float)):
        return v
    s = str(v)
    return s[:49000]  # gioi han 50k ky tu/o cua Sheets


class GoogleSheetSink:
    """Day dong len 1 tab Google Sheet theo lo, chay nen. Moi loi chi in canh
    bao -- khong bao gio lam dung bai test."""

    def __init__(self, spreadsheet, tab_name, fieldnames, flush_seconds=5.0):
        self._fieldnames = fieldnames
        self._queue = queue.Queue()
        self._stop = threading.Event()
        self._flush_seconds = flush_seconds
        self.tab_name = tab_name
        self._ws = spreadsheet.add_worksheet(title=tab_name, rows=100, cols=len(fieldnames))
        self._ws.append_row(fieldnames, value_input_option="RAW")
        self._thread = threading.Thread(target=self._run, name=f"gsheet-{tab_name}", daemon=True)
        self._thread.start()

    def write_row(self, row):
        self._queue.put([_to_cell(row.get(f)) for f in self._fieldnames])

    def _drain(self):
        batch = []
        while True:
            try:
                batch.append(self._queue.get_nowait())
            except queue.Empty:
                return batch

    def _flush(self, batch):
        if not batch:
            return []
        try:
            self._ws.append_rows(batch, value_input_option="RAW")
            return []
        except Exception as e:  # noqa: BLE001 -- quota/mang: giu lai, thu lan sau
            print(f"[gsheet:{self.tab_name}] canh bao: day {len(batch)} dong loi ({e}) -- se thu lai.")
            return batch

    def _run(self):
        pending = []
        while not self._stop.is_set():
            self._stop.wait(self._flush_seconds)
            pending = self._flush(pending + self._drain())

    def close(self):
        self._stop.set()
        self._thread.join(timeout=self._flush_seconds + 5)
        leftover = self._flush(self._drain())
        for attempt in range(3):
            if not leftover:
                return
            time.sleep(5 * (attempt + 1))
            leftover = self._flush(leftover)
        if leftover:
            print(f"[gsheet:{self.tab_name}] {len(leftover)} dong KHONG day len duoc -- van co day du trong CSV cuc bo.")


def open_google_spreadsheet():
    """Tra ve spreadsheet neu da cau hinh GSHEET_ID + GOOGLE_SA_JSON, nguoc lai None."""
    sheet_id = os.environ.get("GSHEET_ID", "").strip()
    sa_json = os.environ.get("GOOGLE_SA_JSON", "").strip()
    if not sheet_id or not sa_json:
        print("Google Sheet: TAT (chua dat GSHEET_ID/GOOGLE_SA_JSON) -- chi ghi CSV cuc bo.")
        return None
    try:
        import gspread  # noqa: PLC0415 -- chi can khi bat Sheet
        gc = gspread.service_account(filename=sa_json)
        ss = gc.open_by_key(sheet_id)
        print(f"Google Sheet: BAT -- {ss.url}")
        return ss
    except Exception as e:  # noqa: BLE001
        print(f"Google Sheet: KHONG mo duoc ({e}) -- tiep tuc chi ghi CSV cuc bo.")
        return None


class MultiSink:
    """Ghi 1 dong ra nhieu dich (CSV + Sheet) cung luc."""

    def __init__(self, *sinks):
        self._sinks = [s for s in sinks if s is not None]
        self.path = next((getattr(s, "path") for s in self._sinks if hasattr(s, "path")), None)

    def write_row(self, row):
        for s in self._sinks:
            s.write_row(row)

    def close(self):
        for s in self._sinks:
            s.close()


def open_result_sinks(output_dir, file_prefix, fieldnames, spreadsheet, run_tag):
    os.makedirs(output_dir, exist_ok=True)
    csv_path = os.path.join(output_dir, f"{file_prefix}-{run_tag}.csv")
    print(f"Ghi realtime vao: {csv_path}")
    csv_sink = RealtimeCsvWriter(csv_path, fieldnames)
    sheet_sink = None
    if spreadsheet is not None:
        try:
            sheet_sink = GoogleSheetSink(spreadsheet, f"{file_prefix}-{run_tag}", fieldnames,
                                         float(os.environ.get("GSHEET_FLUSH_SECONDS", "5")))
        except Exception as e:  # noqa: BLE001
            print(f"Google Sheet: tao tab ket qua loi ({e}) -- chi ghi CSV cuc bo.")
    return MultiSink(csv_sink, sheet_sink)


# ---------------------------------------------------------------------------
# Token usage qua SSE
# ---------------------------------------------------------------------------

TOKEN_FIELDNAMES = [
    "received_at", "at", "step", "username", "student_name", "served_model",
    "prompt_tokens", "cached_tokens", "completion_tokens", "reasoning_tokens",
    "total_tokens", "elapsed_ms",
]
# Sheet KHONG nhan student_name (ho ten that) -- xem docstring dau file.
TOKEN_SHEET_FIELDNAMES = [f for f in TOKEN_FIELDNAMES if f != "student_name"]


class TokenUsageListener:
    """Nghe GET /api/ai-token-usage/stream bang tai khoan ADMIN trong luot
    test. Tu ket noi lai khi rot. Chi bat duoc cac luot goi AI xay ra TRONG
    LUC dang nghe -- start() truoc khi bat dau test, stop() sau khi xong."""

    def __init__(self, base_url, admin_username, admin_password, name_to_username,
                 output_dir, run_tag, spreadsheet=None, timeout=30):
        self._base_url = base_url
        self._admin_username = admin_username
        self._admin_password = admin_password
        self._name_to_username = name_to_username
        self._timeout = timeout
        self._stop = threading.Event()
        self._session = requests.Session()
        self._response = None
        self._access = self._refresh = None
        self.events = []
        self._events_lock = threading.Lock()

        csv_path = os.path.join(output_dir, f"token-usage-{run_tag}.csv")
        print(f"Ghi token realtime vao: {csv_path}")
        self._csv = RealtimeCsvWriter(csv_path, TOKEN_FIELDNAMES)
        self._sheet = None
        if spreadsheet is not None:
            try:
                self._sheet = GoogleSheetSink(spreadsheet, f"token-usage-{run_tag}", TOKEN_SHEET_FIELDNAMES,
                                              float(os.environ.get("GSHEET_FLUSH_SECONDS", "5")))
            except Exception as e:  # noqa: BLE001
                print(f"Google Sheet: tao tab token loi ({e}) -- chi ghi CSV cuc bo.")
        self._thread = threading.Thread(target=self._run, name="token-sse", daemon=True)

    def _login(self):
        resp = self._session.post(
            f"{self._base_url}/api/auth/login",
            json={"usernameOrEmail": self._admin_username, "password": self._admin_password},
            timeout=self._timeout,
        )
        if resp.status_code != 200:
            raise RuntimeError(f"Login admin {self._admin_username} that bai: HTTP {resp.status_code} {resp.text}")
        body = resp.json()
        self._access, self._refresh = body["accessToken"], body["refreshToken"]

    def start(self):
        self._login()  # loi dang nhap nem ra NGAY, truoc khi bat dau test
        probe = self._session.get(
            f"{self._base_url}/api/ai-token-usage/summary",
            headers={"Authorization": f"Bearer {self._access}"}, timeout=self._timeout,
        )
        if probe.status_code in (401, 403):
            raise RuntimeError(
                f"Tai khoan {self._admin_username} khong co quyen system.settings.manage "
                f"(HTTP {probe.status_code}) -- khong nghe duoc kenh token."
            )
        self._thread.start()
        time.sleep(1.0)  # cho ket noi SSE mo xong truoc khi test ban request dau tien

    def _handle(self, data):
        try:
            p = json.loads(data)
        except ValueError:
            return
        name = p.get("studentName")
        row = {
            "received_at": datetime.now(timezone.utc).isoformat(),
            "at": p.get("at"),
            "step": p.get("step"),
            "username": self._name_to_username.get(name, "") if name else "",
            "student_name": name,
            "served_model": p.get("servedModel"),
            "prompt_tokens": p.get("promptTokens"),
            "cached_tokens": p.get("cachedTokens"),
            "completion_tokens": p.get("completionTokens"),
            "reasoning_tokens": p.get("reasoningTokens"),
            "total_tokens": sum(int(p.get(k) or 0) for k in ("promptTokens", "completionTokens", "reasoningTokens")),
            "elapsed_ms": p.get("elapsedMs"),
        }
        with self._events_lock:
            self.events.append(row)
        self._csv.write_row(row)
        if self._sheet is not None:
            self._sheet.write_row(row)

    def _run(self):
        backoff = 2
        while not self._stop.is_set():
            try:
                self._response = self._session.get(
                    f"{self._base_url}/api/ai-token-usage/stream",
                    headers={"Authorization": f"Bearer {self._access}", "Accept": "text/event-stream"},
                    stream=True, timeout=(self._timeout, None),
                )
                if self._response.status_code == 401:
                    self._login()  # access token het han giua chung
                    continue
                self._response.raise_for_status()
                # Chuan SSE luon la UTF-8; thieu charset trong Content-Type thi requests tu doan
                # ISO-8859-1 -> ho ten tieng Viet vo font, khong ghep duoc sang username.
                self._response.encoding = "utf-8"
                backoff = 2
                event_name, data_lines = None, []
                # chunk_size=1: mac dinh iter_lines dem 512 byte moi tra dong -- su kien SSE
                # nho (~200 byte) se ket trong dem toi khi server dong ket noi.
                for raw in self._response.iter_lines(chunk_size=1, decode_unicode=True):
                    if self._stop.is_set():
                        break
                    if raw is None:
                        continue
                    line = raw.rstrip("\r")
                    if line == "":
                        if data_lines and event_name in (None, "token-usage"):
                            self._handle("\n".join(data_lines))
                        event_name, data_lines = None, []
                    elif line.startswith("event:"):
                        event_name = line[6:].strip()
                    elif line.startswith("data:"):
                        data_lines.append(line[5:].lstrip())
            except Exception as e:  # noqa: BLE001
                if self._stop.is_set():
                    break
                print(f"[token-sse] mat ket noi ({e}) -- ket noi lai sau {backoff}s.")
                self._stop.wait(backoff)
                backoff = min(backoff * 2, 30)

    def stop(self, grace_seconds=5.0):
        """Doi them grace_seconds de nhan not su kien cua luot cuoi roi dong."""
        self._stop.wait(grace_seconds)
        self._stop.set()
        if self._response is not None:
            try:
                self._response.close()
            except Exception:  # noqa: BLE001
                pass
        self._thread.join(timeout=10)
        try:
            self._session.post(
                f"{self._base_url}/api/auth/logout", json={"refreshToken": self._refresh},
                headers={"Authorization": f"Bearer {self._access}"}, timeout=self._timeout,
            )
        except requests.RequestException:
            pass
        self._csv.close()
        if self._sheet is not None:
            self._sheet.close()

    def print_summary(self):
        with self._events_lock:
            events = list(self.events)
        print("\n" + "-" * 78)
        print(f"TOKEN AI (nghe qua SSE) -- {len(events)} luot goi")
        print("-" * 78)
        by_step = {}
        for e in events:
            by_step.setdefault(e["step"], []).append(e)
        header = f"{'Buoc':<16}{'Luot':>6}{'Prompt TB':>11}{'Output TB':>11}{'Thinking TB':>13}{'Tong':>12}{'Cho TB(s)':>11}"
        print(header)
        for step, rows in sorted(by_step.items()):
            n = len(rows)

            def avg(k):
                return sum(int(r[k] or 0) for r in rows) / n

            total = sum(int(r["total_tokens"] or 0) for r in rows)
            print(f"{step:<16}{n:>6}{avg('prompt_tokens'):>11.0f}{avg('completion_tokens'):>11.0f}"
                  f"{avg('reasoning_tokens'):>13.0f}{total:>12,}{avg('elapsed_ms') / 1000:>11.1f}")
        unmatched = sum(1 for e in events if e["student_name"] and not e["username"])
        if unmatched:
            print(f"Luu y: {unmatched} su kien co ho ten khong khop students.csv (hoc sinh ngoai bai test, "
                  "hoac ai do dang cham bai cung luc) -- van ghi day du trong CSV token.")


def build_name_to_username(students_csv_path):
    """students.csv co cot full_name + username -> {full_name: username}."""
    mapping = {}
    if not os.path.isfile(students_csv_path):
        return mapping
    with open(students_csv_path, newline="", encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            name = (row.get("full_name") or "").strip()
            username = (row.get("username") or "").strip()
            if name and username:
                mapping[name] = username
    return mapping


def start_token_listener_if_configured(base_url, output_dir, run_tag, spreadsheet, students_csv_path):
    admin_user = os.environ.get("ADMIN_USERNAME", "").strip()
    admin_pass = os.environ.get("ADMIN_PASSWORD", "")
    if not admin_user or not admin_pass:
        print("Token AI: TAT (chua dat ADMIN_USERNAME/ADMIN_PASSWORD) -- khong ghi token.")
        return None
    listener = TokenUsageListener(base_url, admin_user, admin_pass, build_name_to_username(students_csv_path),
                                  output_dir, run_tag, spreadsheet)
    listener.start()  # loi quyen/dang nhap -> nem ra, dung script truoc khi test
    print(f"Token AI: BAT -- dang nghe /api/ai-token-usage/stream bang {admin_user}.")
    return listener
