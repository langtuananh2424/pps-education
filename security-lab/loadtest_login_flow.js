// Load test THUC TE cho luong dang nhap PPS Education (UC-01):
// login -> /me (dung access token) -> refresh -> logout.
//
// Day la HIEU NANG test (do capacity), khong phai tan cong. Chi chay
// nham vao he thong CUA BAN, tu may/mang do ban kiem soat, sau khi da
// bao truoc cho team lien quan va chon khung gio phu hop.
//
// Truoc khi chay -- LUU Y VE NANG LUC HE THONG:
//   - HikariCP dung mac dinh Spring Boot = 10 connection/instance (khong
//     override trong application.yml). Neu VUs dong thoi > ~10 va moi
//     request deu cham DB, request se XEP HANG cho connection chu khong
//     phai do thieu CPU -- dung nham lan 2 nguyen nhan nay khi doc ket qua.
//   - LUU Y: 100 VU KHONG co nghia 100 request dong thoi cham DB -- vi
//     script co sleep() giua cac buoc, so request dang xu ly thuc te tai
//     1 thoi diem (Little's Law: L = throughput x avg_duration) o lan
//     test 100 VU tren staging thuc te chi ~6 (46.9 req/s x 0.13s) --
//     con xa tran 10 connection. Muon that su cham tran, can VU cao hon
//     nhieu (nac 200 duoi day) hoac giam sleep() de tang mat do request.
//   - max-failed-attempts=5 (app.security.brute-force): PHAI dung dung
//     TEST_PASSWORD, sai qua 5 lan se tu khoa tai khoan test 15 phut va
//     lam sai lech ket qua (tat ca request sau do tra ve 423, khong con
//     do dung capacity nua).
//   - /actuator/env, /actuator/metrics KHONG duoc expose (NFR-SEC-03) --
//     theo doi hieu nang phia server bang cong cu khac (log, APM,
//     docker stats/CloudWatch...) song song luc chay k6.
//
// Chay:
//   TARGET_URL=https://your-staging-host \
//   TEST_USERNAME=loadtest_user TEST_PASSWORD='...' \
//   k6 run loadtest_login_flow.js

import http from "k6/http";
import { check, sleep, group } from "k6";

const BASE_URL = __ENV.TARGET_URL || "https://REPLACE-WITH-YOUR-STAGING-URL";
const USERNAME = __ENV.TEST_USERNAME || "REPLACE_WITH_SEEDED_TEST_ACCOUNT";
const PASSWORD = __ENV.TEST_PASSWORD || "REPLACE_WITH_TEST_PASSWORD";

export const options = {
  scenarios: {
    // Ramp tu tu thay vi nhay thang len tai dinh -- de quan sat diem
    // hieu nang bat dau suy giam (VD do het HikariCP pool) truoc khi
    // dat toi tai muc tieu, thay vi lam server "giat minh".
    ramping_login_flow: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "30s", target: 5 },    // warm-up
        { duration: "1m", target: 10 },    // xap xi tran HikariCP mac dinh -- diem dang chu y
        { duration: "1m", target: 100 },   // da test thuc te tren staging: 0% loi, p(95)=230ms -- chua cham tran
        { duration: "1m", target: 200 },   // nac moi -- tang dan de tim diem bat dau suy giam thuc su
        { duration: "30s", target: 0 },    // ramp-down
      ],
    },
  },
  thresholds: {
    // Dung test SOM neu tai vuot xa nang luc thuc te -- tranh keo dai
    // tinh trang qua tai ngoai y muon.
    http_req_failed: ["rate<0.10"],
    http_req_duration: ["p(95)<3000"],
  },
};

export default function () {
  let accessToken, refreshToken;

  group("login", () => {
    const res = http.post(
      `${BASE_URL}/api/auth/login`,
      JSON.stringify({ usernameOrEmail: USERNAME, password: PASSWORD }),
      { headers: { "Content-Type": "application/json" } }
    );
    const ok = check(res, {
      "login status 200": (r) => r.status === 200,
      "login khong bi khoa (423)": (r) => r.status !== 423,
    });
    if (res.status === 423) {
      // Dung ngay -- tai khoan test da bi khoa do brute-force lockout,
      // chay tiep chi tao du lieu sai lech.
      throw new Error("Tai khoan test bi khoa (423) -- kiem tra lai TEST_PASSWORD, dung chay tiep.");
    }
    if (ok) {
      const body = res.json();
      accessToken = body.accessToken;
      refreshToken = body.refreshToken;
    }
  });

  if (!accessToken) {
    sleep(1);
    return;
  }

  group("authenticated_profile", () => {
    const res = http.get(`${BASE_URL}/api/auth/me`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    check(res, { "/me status 200": (r) => r.status === 200 });
  });

  sleep(1); // khoang nghi giua request, mo phong hanh vi nguoi dung that

  group("refresh_token", () => {
    const res = http.post(
      `${BASE_URL}/api/auth/refresh`,
      JSON.stringify({ refreshToken }),
      { headers: { "Content-Type": "application/json" } }
    );
    check(res, { "refresh status 200": (r) => r.status === 200 });
    if (res.status === 200) {
      refreshToken = res.json().refreshToken;
    }
  });

  group("logout", () => {
    const res = http.post(
      `${BASE_URL}/api/auth/logout`,
      JSON.stringify({ refreshToken }),
      { headers: { "Content-Type": "application/json" } }
    );
    check(res, { "logout status 204": (r) => r.status === 204 });
  });

  sleep(1);
}
