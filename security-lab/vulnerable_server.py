"""
Server "NAIVE" -- mo phong 1 login endpoint viet SAI (KHONG giong code that
trong repo) de lam doi chung. Dung SQLite + string-concatenation SQL,
so sanh mat khau plaintext, khong khoa tai khoan, loi tra ve tiet lo
tai khoan co ton tai hay khong.

CHI chay tren localhost trong sandbox nay, khong ket noi ra ngoai.
"""
import http.server
import json
import sqlite3
import urllib.parse

DB = ":memory:"
_conn = sqlite3.connect(DB, check_same_thread=False)
_conn.execute("CREATE TABLE users (username TEXT, password TEXT)")
_conn.execute("INSERT INTO users VALUES ('admin', 'SuperSecret123')")
_conn.execute("INSERT INTO users VALUES ('giaovien01', 'MatKhau2024')")
_conn.commit()


class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass  # im lang, tu in log rieng trong script tan cong

    def do_POST(self):
        if self.path != "/login":
            self.send_response(404)
            self.end_headers()
            return
        length = int(self.headers.get("Content-Length", 0))
        body = json.loads(self.rfile.read(length) or b"{}")
        username = body.get("username", "")
        password = body.get("password", "")

        # LO HONG 1: noi chuoi truc tiep vao SQL -> SQL Injection
        query = f"SELECT * FROM users WHERE username = '{username}' AND password = '{password}'"
        try:
            cur = _conn.execute(query)
            row = cur.fetchone()
        except sqlite3.Error as e:
            self._json(500, {"error": f"DB error: {e}"})
            return

        if row:
            self._json(200, {"token": "fake-jwt-token", "user": row[0]})
            return

        # LO HONG 2: tiet lo tai khoan co ton tai hay khong
        exists = _conn.execute(
            "SELECT 1 FROM users WHERE username = ?", (username,)
        ).fetchone()
        if exists:
            self._json(401, {"error": "Sai mat khau"})
        else:
            self._json(401, {"error": "Tai khoan khong ton tai"})
        # LO HONG 3: khong co dem so lan sai / khoa tai khoan -> brute-force vo han

    def _json(self, code, obj):
        payload = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)


if __name__ == "__main__":
    http.server.HTTPServer(("127.0.0.1", 8081), Handler).serve_forever()
