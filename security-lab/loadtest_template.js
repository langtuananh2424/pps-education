// Load-test template (k6 - https://k6.io) -- HIEU NANG, khong phai tan cong.
//
// Muc dich: do throughput/latency cua 1 endpoint duoi tai vua phai, CO
// GIOI HAN ro rang -- khong nham gay qua tai/sap he thong (khong phai DoS).
//
// Cach dung:
//   1. Doi TARGET_URL thanh staging cua ban (chi chay khi ban CO QUYEN
//      chinh thuc va da bao truoc cho team/nguoi lien quan).
//   2. Chay tu may/mang do BAN kiem soat: k6 run loadtest_template.js
//   3. Chon khung gio it anh huong nguoi dung that (vd ngoai gio lam viec).
//
// An toan / gioi han:
//   - VUs (virtual users) va duration duoc gioi han cung, khong tang dan
//     vo han -- sua truc tiep trong file neu can, nhung tang dan tu tu va
//     theo doi dashboard/log server song song.
//   - KHONG dung script nay de gui payload tan cong (SQLi, brute-force)
//     vao staging that -- phan do chi danh cho lab cuc bo trong
//     security-lab/attack_*.py.

import http from "k6/http";
import { check, sleep } from "k6";

const TARGET_URL = "https://REPLACE-WITH-YOUR-STAGING-URL/api/health"; // doi truoc khi chay

export const options = {
  scenarios: {
    steady_load: {
      executor: "constant-vus",
      vus: 10,           // so nguoi dung ao dong thoi -- gioi han thap, tang dan thu cong neu can
      duration: "30s",   // thoi luong test -- gioi han ngan, khong chay vo han
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.05"],   // dung lai neu >5% request loi -- dau hieu dang lam qua tai server that
    http_req_duration: ["p(95)<2000"],
  },
};

export default function () {
  const res = http.get(TARGET_URL);
  check(res, { "status is 200": (r) => r.status === 200 });
  sleep(1); // giu khoang nghi giua cac request, khong flood lien tuc
}
