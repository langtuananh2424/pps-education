"""
Server "HARDENED" -- mo phong DUNG logic that trong
pps-education-backend/.../service/AuthService.java (UC-01):

  - Truy van tham so hoa (khong noi chuoi SQL)               -> chan SQL Injection
  - Bam mat khau bang bcrypt, so sanh bang passwordEncoder    -> chan do password
  - Thong bao loi CHUNG CHUNG du sai username hay sai password -> chan user enumeration
    (xem AuthService.login(): "Sai tai khoan hoac mat khau." cho ca 2 truong hop)
  - Dem so lan sai, khoa tai khoan sau N lan (app.security.brute-force
    trong application.yml that: max-failed-attempts=5, lock-duration-minutes=15)
    -> chan brute-force

Day la ban mo phong bang Python de demo (sandbox nay khong co Docker/Postgres
de chay that Spring Boot那), khong phai chay truc tiep code Java, nhung
tai hien dung hanh vi bao ve cua AuthService that.
"""
import http.server
import json
import sqlite3
import time

import bcrypt

DB = ":memory:"
_conn = sqlite3.connect(DB, check_same_thread=False)
_conn.execute("CREATE TABLE users (username TEXT, password_hash TEXT, "
              "failed_count INTEGER DEFAULT 0, locked_until REAL DEFAULT 0)")


def _hash(pw: str) -> str:
    return bcrypt.hashpw(pw.encode(), bcrypt.gensalt()).decode()


_conn.execute("INSERT INTO users VALUES (?, ?, 0, 0)", ("admin", _hash("SuperSecret123")))
_conn.execute("INSERT INTO users VALUES (?, ?, 0, 0)", ("giaovien01", _hash("MatKhau2024")))
_conn.commit()

MAX_FAILED_ATTEMPTS = 5     # app.security.brute-force.max-failed-attempts (application.yml)
LOCK_DURATION_SECONDS = 15  # rut gon tu 15 phut that -> 15 giay de demo nhanh

GENERIC_ERROR = "Sai tai khoan hoac mat khau."  # giong het AuthService.login()


class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass

    def do_POST(self):
        if self.path != "/login":
            self.send_response(404)
            self.end_headers()
            return
        length = int(self.headers.get("Content-Length", 0))
        body = json.loads(self.rfile.read(length) or b"{}")
        username = body.get("username", "")
        password = body.get("password", "")

        # Truy van THAM SO HOA -- giong userRepository.findByUsername(...) (Spring Data JPA)
        row = _conn.execute(
            "SELECT username, password_hash, failed_count, locked_until FROM users WHERE username = ?",
            (username,),
        ).fetchone()

        # A1 that trong AuthService: khong tiet lo tai khoan co ton tai hay khong
        if row is None:
            self._json(401, {"error": GENERIC_ERROR})
            return

        uname, pw_hash, failed_count, locked_until = row

        # A2 that trong AuthService: tai khoan dang bi khoa
        if locked_until and time.time() < locked_until:
            self._json(423, {"error": f"Tai khoan dang tam khoa. Thu lai sau {int(locked_until - time.time())}s"})
            return

        if not bcrypt.checkpw(password.encode(), pw_hash.encode()):
            failed_count += 1
            locked = 0
            if failed_count >= MAX_FAILED_ATTEMPTS:
                locked = time.time() + LOCK_DURATION_SECONDS
            _conn.execute(
                "UPDATE users SET failed_count = ?, locked_until = ? WHERE username = ?",
                (failed_count, locked, uname),
            )
            _conn.commit()
            self._json(401, {"error": GENERIC_ERROR})
            return

        _conn.execute("UPDATE users SET failed_count = 0, locked_until = 0 WHERE username = ?", (uname,))
        _conn.commit()
        self._json(200, {"token": "real-jwt-would-go-here", "user": uname})

    def _json(self, code, obj):
        payload = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)


if __name__ == "__main__":
    http.server.HTTPServer(("127.0.0.1", 8082), Handler).serve_forever()
