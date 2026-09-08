// Load test DIỆN RỘNG cho PPS Education — nhiều phân hệ, ramp 100 → 1000 VU.
//
// Đây là HIỆU NĂNG test (đo capacity + tìm điểm gãy), KHÔNG phải tấn công.
// Chỉ chạy nhắm vào hệ thống CỦA BẠN, từ máy/mạng do bạn kiểm soát, sau khi
// đã báo trước cho team giữ staging và chọn khung giờ ít ảnh hưởng.
//
// Khác gì `loadtest_login_flow.js`? File kia đo riêng luồng auth (BCrypt).
// File này giữ auth ở mức nền thấp rồi dồn tải vào các API đọc/ghi thật của
// nhiều phân hệ — sát với hành vi người dùng trong giờ cao điểm hơn.
//
// ─────────────────────────────────────────────────────────────────────────
// BẮT BUỘC ĐỌC TRƯỚC KHI CHẠY
//
//  1. HikariCP = 10 connection/instance (mặc định Spring Boot, không override
//     trong application.yml). Từ ~10 VU đồng thời trở lên, request XẾP HÀNG
//     chờ connection chứ không phải thiếu CPU. Ở 1000 VU, gần như chắc chắn
//     nút thắt là POOL, không phải CPU/RAM — đừng đọc nhầm nguyên nhân.
//  2. access-token-ttl = 15 phút. Bài test dài ~17 phút nên token PHẢI được
//     làm mới giữa chừng — script tự xử lý khi gặp 401 (xem `authedGet`).
//  3. max-failed-attempts = 5 → sai mật khẩu 5 lần là khoá tài khoản test 15
//     phút (423) và mọi số liệu sau đó vô nghĩa. `setup()` kiểm tra đăng nhập
//     MỘT LẦN và dừng cả bài test ngay nếu sai, thay vì để 1000 VU cùng thử.
//  4. Tài khoản test cần đủ quyền (`hasPermission(...)` trên hầu hết endpoint).
//     Thiếu quyền → 403. Script tách riêng metric `permission_denied` để 403
//     KHÔNG bị tính lẫn vào tỉ lệ lỗi capacity — thấy metric này > 0 nghĩa là
//     lỗi cấu hình tài khoản, không phải server yếu.
//  5. /actuator/env, /actuator/metrics không expose (NFR-SEC-03). Theo dõi
//     phía server song song bằng log ứng dụng / `docker stats` / APM.
//  6. 1000 VU từ 1 máy Windows dễ cạn ephemeral port (mặc định ~16k, TIME_WAIT
//     240s). Nếu thấy lỗi `connectex: Only one usage of each socket address`,
//     đó là giới hạn MÁY CHẠY K6, không phải server. Xem LOADTEST.md.
//
// Chạy:
//   TARGET_URL=https://your-staging-host \
//   TEST_USERNAME=loadtest_user TEST_PASSWORD='...' \
//   k6 run loadtest_api_suite.js
//
// Tuỳ chọn env:
//   VU_PEAK=1000     đỉnh tải (mặc định 1000)
//   WRITE_RPS=5      số thao tác ghi mỗi giây (mặc định 5, xem mục GHI DỮ LIỆU)
//   SKIP_WRITES=1    tắt hẳn phần ghi, chỉ chạy đọc
// ─────────────────────────────────────────────────────────────────────────

import http from "k6/http";
import { check, group, sleep, fail } from "k6";
import { Rate, Counter, Trend } from "k6/metrics";
import exec from "k6/execution";

const BASE_URL = (__ENV.TARGET_URL || "http://localhost:8080").replace(/\/+$/, "");
const USERNAME = __ENV.TEST_USERNAME || "";
const PASSWORD = __ENV.TEST_PASSWORD || "";
const VU_PEAK = Number(__ENV.VU_PEAK || 1000);
const WRITE_RPS = Number(__ENV.WRITE_RPS || 5);
const SKIP_WRITES = __ENV.SKIP_WRITES === "1";

// BYPASS_CACHE=1 → gắn `Cache-Control: no-cache` vào mọi request đọc, ép
// Cloudflare đi thẳng origin. Dùng để chạy phép đo ĐỐI CHỨNG: cùng một bài
// test, một lần qua cache và một lần không, mới biết cache hấp thụ được bao
// nhiêu. Chỉ nhìn con số của lần chạy có cache thì không kết luận được gì.
const BYPASS_CACHE = __ENV.BYPASS_CACHE === "1";

// MODE=browse   (mặc định) — mô phỏng người dùng thật, ramping-vus.
// MODE=capacity            — TÌM GIỚI HẠN: ramping-arrival-rate.
//
// Vì sao phải tách 2 chế độ: với ramping-vus, khi server chậm lại thì mỗi VU
// tự động gửi ít request hơn (nó đang ngồi chờ response) -> tải tự co lại
// đúng bằng mức server chịu được và bạn KHÔNG BAO GIỜ thấy điểm gãy, chỉ thấy
// latency tăng dần. ramping-arrival-rate thì ép đủ số request/giây bất kể
// server trả lời nhanh hay chậm, nên trần năng lực lộ ra rõ ràng: tới ngưỡng
// nào đó k6 báo "dropped_iterations" (không kịp phát tải) và lỗi tăng vọt.
const MODE = __ENV.MODE || "browse";
const RPS_PEAK = Number(__ENV.RPS_PEAK || 3000);

// Mọi bản ghi do bài test sinh ra đều mang tiền tố này để dọn được bằng SQL
// sau khi chạy (xem LOADTEST.md, mục "Dọn dữ liệu sau khi test").
const MARKER = "LOADTEST";

// Offset ngẫu nhiên MỘT LẦN cho cả lần chạy, để dải số điện thoại sinh ra
// không đè lên dải của lần chạy trước (nếu chưa kịp dọn bằng SQL).
const RUN_OFFSET = Math.floor(Math.random() * 100000000);

// ── Metric riêng ─────────────────────────────────────────────────────────
// Tách bạch 3 loại "không phải 200" vì ý nghĩa hoàn toàn khác nhau:
const permissionDenied = new Rate("permission_denied");   // 403 → sai cấu hình quyền
const authExpired = new Counter("auth_token_refreshed");  // 401 → token hết hạn, đã tự login lại
const serverErrors = new Rate("server_errors");           // 5xx/timeout → ĐÂY mới là giới hạn capacity
const writeOpsOk = new Counter("write_ops_succeeded");
const writeOpsLeaked = new Counter("write_ops_leaked");   // tạo được nhưng KHÔNG xoá được → còn rác trong DB
const loginTrend = new Trend("login_duration", true);

// Đo THẬT tỉ lệ Cloudflare hấp thụ, đọc từ header `CF-Cache-Status`:
//   HIT/STALE/UPDATING/REVALIDATED = Cloudflare trả, origin KHÔNG bị chạm
//   MISS/EXPIRED/BYPASS/DYNAMIC    = đi thẳng xuống Spring Boot
// `DYNAMIC` là giá trị sẽ thấy ở gần như toàn bộ endpoint /api/** — nghĩa là
// Cloudflare còn không coi response đó là ứng viên để cache (xem LOADTEST.md).
const cfCacheHit = new Rate("cf_cache_hit");

// ── Danh mục endpoint đọc ────────────────────────────────────────────────
// `weight` mô phỏng tần suất truy cập thực tế: màn hình danh sách/tra cứu
// được mở nhiều hơn màn hình chi tiết chuyên sâu.
// `needs` = id thật lấy từ setup(); endpoint bị bỏ qua nếu không harvest được.
const READ_ENDPOINTS = [
  // Danh mục tra cứu — query nhẹ, gần như chỉ đo overhead JWT filter + permission check
  { name: "departments.list", path: () => "/api/departments", weight: 3 },
  { name: "positions.list", path: () => "/api/positions", weight: 2 },
  { name: "roles.list", path: () => "/api/roles", weight: 2 },
  { name: "leaveTypes.list", path: () => "/api/leave-types", weight: 1 },
  { name: "systemSettings.list", path: () => "/api/system-settings", weight: 1 },

  // Danh sách/tìm kiếm — query nặng hơn, đây là nhóm dễ chạm trần pool nhất
  { name: "students.search", path: () => "/api/students", weight: 10 },
  { name: "classes.search", path: () => "/api/classes", weight: 8 },
  { name: "employees.search", path: () => "/api/employees", weight: 5 },
  { name: "users.search", path: () => "/api/users?page=0&size=20", weight: 4 },
  { name: "curriculums.list", path: () => "/api/curriculums", weight: 3 },
  { name: "books.list", path: () => "/api/books", weight: 2 },

  // Theo người dùng hiện tại — mỗi VU dùng chung 1 tài khoản nên đây là
  // "hot row" trong DB, hữu ích để lộ contention khi khoá bản ghi.
  { name: "auth.me", path: () => "/api/auth/me", weight: 6 },
  { name: "notifications.mine", path: () => "/api/notifications?page=0&size=20", weight: 5 },
  { name: "employees.me", path: () => "/api/employees/me", weight: 3 },
  { name: "tasks.overview", path: () => "/api/tasks/overview", weight: 3 },
  { name: "leads.open", path: () => "/api/leads/open", weight: 2 },

  // Chi tiết — cần id thật, thường kéo theo nhiều JOIN nhất
  { name: "students.detail", path: (d) => `/api/students/${pick(d.studentIds)}`, needs: "studentIds", weight: 6 },
  { name: "students.profile", path: (d) => `/api/students/${pick(d.studentIds)}/profile`, needs: "studentIds", weight: 4 },
  { name: "classes.detail", path: (d) => `/api/classes/${pick(d.classIds)}`, needs: "classIds", weight: 5 },
  { name: "classes.enrollments", path: (d) => `/api/classes/${pick(d.classIds)}/enrollments`, needs: "classIds", weight: 4 },
  { name: "employees.detail", path: (d) => `/api/employees/${pick(d.employeeIds)}`, needs: "employeeIds", weight: 3 },
];

// Bảng quay số theo trọng số — dựng 1 lần lúc init, không tính lại mỗi iteration.
const WEIGHTED_POOL = READ_ENDPOINTS.flatMap((e) => Array(e.weight).fill(e));

function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

// Ghi nhận Cloudflare có phục vụ hộ request này không. Header vẫn đọc được
// bình thường dù đã bật `discardResponseBodies` (chỉ body bị bỏ).
const CF_ABSORBED = ["HIT", "STALE", "UPDATING", "REVALIDATED"];

function recordCacheStatus(res, epTag) {
  const status = res.headers["Cf-Cache-Status"] || res.headers["CF-Cache-Status"];
  if (!status) {
    // Không có header → không đi qua Cloudflare (VD test thẳng localhost).
    // Không tính vào tỉ lệ, tránh làm loãng số liệu bằng mẫu không liên quan.
    return;
  }
  cfCacheHit.add(CF_ABSORBED.includes(status.toUpperCase()), { ep: epTag });
}

// ── Cấu hình scenario ────────────────────────────────────────────────────
// Ramp theo BẬC THANG (tăng rồi GIỮ) chứ không tăng tuyến tính: chỉ đoạn giữ
// tải ổn định mới cho số liệu đọc được. Ramp liên tục sẽ trộn lẫn nhiều mức
// tải vào cùng một percentile và không chỉ ra được điểm gãy nằm ở đâu.
const step = (target) => [
  { duration: "1m", target },
  { duration: "2m", target },
];

// MODE=smoke — kiểm tra nhanh trước khi chạy bài dài: đăng nhập được không,
// harvest được id không, endpoint có trả 2xx không. Chỉ 1 VU / 1 vòng.
//
// PHẢI là một scenario, KHÔNG dùng `k6 run --vus 1 --iterations 1`: cờ CLI đó
// ghi đè TOÀN BỘ khối `scenarios` rồi rơi về executor mặc định, mà executor
// mặc định gọi hàm `export default` — file này không có (chỉ có các hàm đặt
// tên browse/authFlow/writeOps), nên k6 dừng ngay với lỗi
// "function 'default' not found in exports".
const SMOKE_SCENARIO = {
  smoke: {
    executor: "per-vu-iterations",
    vus: 1,
    iterations: 1,
    maxDuration: "1m",
    exec: "browse",
    tags: { scenario: "browse" },
  },
};

export const options = {
  // Ở 1000 VU, giữ body response trong RAM là nguyên nhân phổ biến khiến
  // chính k6 (chứ không phải server) trở thành nút thắt. Chỉ giữ body ở
  // những request thật sự cần đọc (đánh dấu responseType: "text").
  discardResponseBodies: true,

  scenarios: MODE === "smoke" ? SMOKE_SCENARIO : {
    // Tải chính — hình dạng đổi theo MODE (xem ghi chú ở phần khai báo MODE).
    ...(MODE === "capacity"
      ? {
          // Ép RPS tăng dần cho tới khi server không theo kịp. Đọc kết quả:
          //   - `dropped_iterations` > 0  -> k6 không phát đủ tải (giới hạn ở
          //     MÁY CHẠY k6 hoặc maxVUs, KHÔNG phải server) -> tăng maxVUs
          //     hoặc chạy k6 từ máy khỏe hơn rồi đo lại.
          //   - http_req_failed + p95 vọt lên ở mốc RPS nào -> ĐÓ là trần thật.
          capacity_ramp: {
            executor: "ramping-arrival-rate",
            startRate: 100,
            timeUnit: "1s",
            // Mỗi iteration của `browse` gửi 3 request nên VU nhả ra khá nhanh;
            // vẫn cấp dư maxVUs để việc thiếu VU không bị nhầm thành server yếu.
            preAllocatedVUs: 200,
            maxVUs: 3000,
            stages: [
              { duration: "1m", target: Math.round(RPS_PEAK * 0.1) },
              { duration: "2m", target: Math.round(RPS_PEAK * 0.1) },
              { duration: "1m", target: Math.round(RPS_PEAK * 0.25) },
              { duration: "2m", target: Math.round(RPS_PEAK * 0.25) },
              { duration: "1m", target: Math.round(RPS_PEAK * 0.5) },
              { duration: "2m", target: Math.round(RPS_PEAK * 0.5) },
              { duration: "1m", target: Math.round(RPS_PEAK * 0.75) },
              { duration: "2m", target: Math.round(RPS_PEAK * 0.75) },
              { duration: "1m", target: RPS_PEAK },
              { duration: "3m", target: RPS_PEAK },
              { duration: "1m", target: 0 },
            ],
            exec: "browse",
            tags: { scenario: "browse" },
          },
        }
      : {
          browse_ramp: {
            executor: "ramping-vus",
            startVUs: 0,
            gracefulRampDown: "30s",
            stages: [
              { duration: "30s", target: 100 },
              { duration: "2m", target: 100 },
              ...step(Math.round(VU_PEAK * 0.25)),
              ...step(Math.round(VU_PEAK * 0.5)),
              ...step(Math.round(VU_PEAK * 0.75)),
              { duration: "1m", target: VU_PEAK },
              { duration: "3m", target: VU_PEAK },
              { duration: "1m", target: 0 },
            ],
            exec: "browse",
            tags: { scenario: "browse" },
          },
        }),

    // Auth giữ ở mức NỀN THẤP và cố định. Cố ý không ramp: BCrypt đắt gấp
    // hàng chục lần một query đọc, để nó ramp cùng sẽ nuốt hết pool và che
    // mất giới hạn thật của tầng đọc. Giữ cố định thì độ trễ login trở thành
    // "kim chỉ thị" cho biết server đã ngợp tới mức nào.
    auth_baseline: {
      executor: "constant-vus",
      vus: 5,
      duration: "17m",
      exec: "authFlow",
      tags: { scenario: "auth" },
    },

    // Ghi dữ liệu: dùng constant-arrival-rate (KHÔNG theo VU) để tổng số bản
    // ghi sinh ra là con số biết trước và không phụ thuộc mức ramp.
    // 5 ops/s × 17 phút ≈ 5.100 thao tác, phần lớn tự dọn (xem writeOps).
    ...(SKIP_WRITES
      ? {}
      : {
          write_ops: {
            executor: "constant-arrival-rate",
            rate: WRITE_RPS,
            timeUnit: "1s",
            duration: "17m",
            preAllocatedVUs: 20,
            maxVUs: 60,
            exec: "writeOps",
            tags: { scenario: "write" },
          },
        }),
  },

  thresholds: MODE === "smoke" ? {
    // Smoke chỉ chạy 1 vòng: mọi threshold theo tỉ lệ/percentile đều vô nghĩa
    // trên cỡ mẫu đó. Giữ đúng 2 thứ thật sự cần biết trước bài dài.
    "checks{type:read}": ["rate>0.99"],
    permission_denied: ["rate<0.001"],
  } : {
    // ── Van an toàn ─────────────────────────────────────────────────────
    // MODE=browse: dừng sớm khi bắt đầu làm hỏng staging.
    // MODE=capacity: CỐ Ý nới rất rộng — mục đích của bài chạy này là vượt
    // qua điểm gãy để nhìn thấy nó. Vẫn giữ một ngưỡng chặn ở mức rất cao để
    // không nện tiếp 15 phút vào một server đã chết hẳn (lúc đó không còn số
    // liệu nào thu được nữa, chỉ tổn hại thêm).
    server_errors:
      MODE === "capacity"
        ? [{ threshold: "rate<0.90", abortOnFail: true, delayAbortEval: "3m" }]
        : [{ threshold: "rate<0.20", abortOnFail: true, delayAbortEval: "1m" }],
    http_req_failed:
      MODE === "capacity"
        ? [{ threshold: "rate<0.95", abortOnFail: true, delayAbortEval: "3m" }]
        : [{ threshold: "rate<0.35", abortOnFail: true, delayAbortEval: "1m" }],

    // ── Mục tiêu chất lượng (không abort, chỉ báo đỏ ở summary) ─────────
    "http_req_duration{scenario:browse}": ["p(95)<1500", "p(99)<4000"],
    "http_req_duration{scenario:auth}": ["p(95)<2500"], // nới vì có BCrypt
    "checks{type:read}": ["rate>0.98"],

    // 403 là lỗi CẤU HÌNH tài khoản test, phải bằng 0 — nếu đỏ thì kết quả
    // capacity không đáng tin vì nhiều endpoint chỉ trả 403 rẻ tiền.
    permission_denied: ["rate<0.001"],
    // Rác để lại trong DB phải bằng 0.
    write_ops_leaked: ["count<1"],

    // Ép k6 in breakdown TỪNG endpoint ra summary. k6 chỉ hiện metric theo
    // tag khi tag đó có threshold, nên đây là ngưỡng rất rộng đặt cho mục
    // đích hiển thị — cột p(95) mỗi dòng mới là thứ cần đọc.
    ...Object.fromEntries(
      READ_ENDPOINTS.map((e) => [`http_req_duration{ep:${e.name}}`, ["p(95)<30000"]])
    ),
  },
};

// ── Đăng nhập ────────────────────────────────────────────────────────────
function login() {
  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ usernameOrEmail: USERNAME, password: PASSWORD }),
    {
      headers: { "Content-Type": "application/json" },
      responseType: "text", // cần đọc token
      tags: { ep: "auth.login" },
    }
  );
  loginTrend.add(res.timings.duration);
  return res;
}

export function setup() {
  if (!USERNAME || !PASSWORD) {
    fail("Thiếu TEST_USERNAME/TEST_PASSWORD — không chạy với credential hardcode.");
  }
  // In đúng tham số CÓ HIỆU LỰC ở mode đang chạy — in "đỉnh tải 1000 VU" khi
  // smoke chỉ chạy 1 VU thì gây hiểu nhầm lúc đọc lại log.
  const peakInfo =
    MODE === "smoke"
      ? "1 VU / 1 vòng (smoke)"
      : MODE === "capacity"
        ? `đỉnh tải: ${RPS_PEAK} RPS`
        : `đỉnh tải: ${VU_PEAK} VU`;
  const writeInfo = MODE === "smoke" || SKIP_WRITES ? "không ghi" : `ghi: ${WRITE_RPS}/s`;
  console.log(`Target: ${BASE_URL} | ${peakInfo} | ${writeInfo}`);

  const res = login();

  // Dừng CẢ bài test ngay tại đây nếu không đăng nhập được. Để 1000 VU cùng
  // đâm vào một endpoint đang lỗi thì không đo được gì mà còn có nguy cơ
  // khoá tài khoản (423) hoặc kích hoạt rate-limit phía trước.
  if (res.status === 423) {
    fail("Tài khoản test đang bị khoá (423) — chờ hết 15 phút hoặc mở khoá trước khi chạy lại.");
  }
  if (res.status === 429) {
    fail("Bị rate-limit (429) ngay từ request đầu — cần whitelist IP máy chạy k6 trước.");
  }
  if (res.status !== 200) {
    fail(`Đăng nhập thất bại: status=${res.status} body=${String(res.body).slice(0, 300)}`);
  }

  const body = res.json();
  const token = body.accessToken;
  const auth = { headers: { Authorization: `Bearer ${token}` }, responseType: "text" };

  // Harvest id thật để các endpoint chi tiết không bắn vào 404 — 404 trả về
  // rất rẻ nên sẽ làm đẹp số liệu một cách giả tạo.
  const harvest = (path, label) => {
    const r = http.get(`${BASE_URL}${path}`, auth);
    if (r.status !== 200) {
      console.warn(`Bỏ qua ${label}: GET ${path} trả ${r.status} — endpoint chi tiết tương ứng sẽ không được test.`);
      return [];
    }
    try {
      const json = r.json();
      const items = Array.isArray(json) ? json : json.content || [];
      return items.map((i) => i.id).filter((id) => id != null).slice(0, 50);
    } catch (e) {
      console.warn(`Bỏ qua ${label}: không parse được response.`);
      return [];
    }
  };

  const data = {
    token,
    studentIds: harvest("/api/students", "students"),
    classIds: harvest("/api/classes", "classes"),
    employeeIds: harvest("/api/employees", "employees"),
  };

  console.log(
    `Harvest: ${data.studentIds.length} student, ${data.classIds.length} class, ${data.employeeIds.length} employee.`
  );
  return data;
}

// ── Quản lý token ở phạm vi từng VU ──────────────────────────────────────
// Mỗi VU giữ token riêng. Khi gặp 401 (token 15 phút hết hạn giữa bài test
// dài 17 phút) thì tự đăng nhập lại — nhưng có ngưỡng chặn để 1000 VU không
// cùng lúc đổ vào /login tạo ra một cơn bão BCrypt.
let vuToken = null;
let lastRelogin = 0;

function tokenOf(data) {
  return vuToken || data.token;
}

function relogin() {
  const now = Date.now();
  if (now - lastRelogin < 30000) {
    // Vừa thử cách đây <30s — ngủ một nhịp có nhiễu ngẫu nhiên thay vì thử
    // lại ngay, tránh mọi VU đồng bộ nhịp với nhau.
    sleep(1 + Math.random() * 2);
    return false;
  }
  lastRelogin = now;
  const res = login();
  if (res.status === 200) {
    vuToken = res.json().accessToken;
    authExpired.add(1);
    return true;
  }
  return false;
}

// GET có xử lý sẵn 401/403/5xx — mọi lời gọi đọc đều đi qua đây để phân loại
// lỗi nhất quán.
function authedGet(data, path, epTag, keepBody = false) {
  const headers = { Authorization: `Bearer ${tokenOf(data)}` };
  if (BYPASS_CACHE) headers["Cache-Control"] = "no-cache";

  const res = http.get(`${BASE_URL}${path}`, {
    headers,
    tags: { ep: epTag },
    responseType: keepBody ? "text" : "none",
  });

  if (res.status === 401 && relogin()) {
    return authedGet(data, path, epTag, keepBody);
  }

  recordCacheStatus(res, epTag);
  permissionDenied.add(res.status === 403);
  serverErrors.add(res.status >= 500 || res.status === 0);

  check(
    res,
    { "read 2xx": (r) => r.status >= 200 && r.status < 300 },
    { type: "read", ep: epTag }
  );
  return res;
}

// ── Kịch bản 1: duyệt API (tải chính) ────────────────────────────────────
export function browse(data) {
  // Mỗi iteration mô phỏng một "màn hình": vài request liên tiếp rồi nghỉ,
  // giống người dùng thật hơn là bắn liên tục không nghỉ.
  const perScreen = 3;
  for (let i = 0; i < perScreen; i++) {
    const ep = pick(WEIGHTED_POOL);
    if (ep.needs && (!data[ep.needs] || data[ep.needs].length === 0)) continue;
    authedGet(data, ep.path(data), ep.name);
  }

  // MODE=capacity KHÔNG nghỉ: với ramping-arrival-rate, nhịp phát tải do
  // executor quyết định, còn think-time chỉ kéo dài mỗi iteration và buộc
  // phải cấp thêm hàng nghìn VU chỉ để ngồi ngủ (chạm maxVUs -> k6 báo
  // dropped_iterations và ta lại tưởng nhầm là server đã tới hạn).
  if (MODE === "capacity") return;

  // think-time có nhiễu: nghỉ cố định làm các VU đồng bộ nhịp với nhau và
  // tạo ra sóng tải răng cưa không giống lưu lượng thật.
  sleep(1 + Math.random() * 2);
}

// ── Kịch bản 2: luồng auth nền ───────────────────────────────────────────
export function authFlow() {
  let accessToken, refreshToken;

  group("login", () => {
    const res = login();
    if (res.status === 423) {
      fail("Tài khoản test bị khoá (423) giữa bài test — dừng, số liệu sau đây không dùng được.");
    }
    serverErrors.add(res.status >= 500 || res.status === 0);
    const ok = check(res, { "login 200": (r) => r.status === 200 }, { type: "auth" });
    if (ok) {
      const body = res.json();
      accessToken = body.accessToken;
      refreshToken = body.refreshToken;
    }
  });

  if (!accessToken) {
    sleep(2);
    return;
  }

  http.get(`${BASE_URL}/api/auth/me`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    tags: { ep: "auth.me.flow" },
  });

  sleep(1);

  group("refresh", () => {
    const res = http.post(`${BASE_URL}/api/auth/refresh`, JSON.stringify({ refreshToken }), {
      headers: { "Content-Type": "application/json" },
      responseType: "text",
      tags: { ep: "auth.refresh" },
    });
    check(res, { "refresh 200": (r) => r.status === 200 }, { type: "auth" });
    if (res.status === 200) refreshToken = res.json().refreshToken;
  });

  group("logout", () => {
    const res = http.post(`${BASE_URL}/api/auth/logout`, JSON.stringify({ refreshToken }), {
      headers: { "Content-Type": "application/json" },
      tags: { ep: "auth.logout" },
    });
    check(res, { "logout 204": (r) => r.status === 204 }, { type: "auth" });
  });

  sleep(2);
}

// ── Kịch bản 3: ghi dữ liệu (có giới hạn, tự dọn) ────────────────────────
//
// Nguyên tắc chọn endpoint ghi: ưu tiên cặp CÓ THỂ TỰ HOÀN TÁC trong cùng
// iteration để không để lại rác, kể cả khi bài test bị abort giữa chừng.
//
//  - device-token: POST rồi DELETE ngay → không để lại gì. Đây là phần lớn
//    lưu lượng ghi, đủ để đo chi phí transaction/ghi của tầng persistence.
//  - lead: KHÔNG có endpoint xoá (UC-34 chỉ cho chuyển đổi), nên chạy ở tần
//    suất thấp hơn nhiều và gắn MARKER để dọn bằng SQL sau. Xem LOADTEST.md.
export function writeOps(data) {
  const token = tokenOf(data);
  const headers = { Authorization: `Bearer ${token}`, "Content-Type": "application/json" };
  const uniq = `${MARKER}-${exec.scenario.iterationInTest}-${Date.now()}`;

  // 90% lưu lượng ghi: cặp tạo/xoá tự dọn.
  if (Math.random() < 0.9) {
    const deviceToken = `${uniq}-devtok`;
    const created = http.post(
      `${BASE_URL}/api/notifications/device-token`,
      JSON.stringify({ token: deviceToken, platform: "WEB", deviceId: uniq }),
      { headers, tags: { ep: "notifications.deviceToken.create" } }
    );
    permissionDenied.add(created.status === 403);
    serverErrors.add(created.status >= 500 || created.status === 0);
    const ok = check(created, { "device-token tạo 2xx": (r) => r.status >= 200 && r.status < 300 }, { type: "write" });

    if (ok) {
      const removed = http.del(`${BASE_URL}/api/notifications/device-token/${deviceToken}`, null, {
        headers,
        tags: { ep: "notifications.deviceToken.delete" },
      });
      if (removed.status >= 200 && removed.status < 300) {
        writeOpsOk.add(1);
      } else {
        // Tạo được mà không xoá được → còn rác mang MARKER trong DB.
        writeOpsLeaked.add(1);
      }
    }
    sleep(0.5);
    return;
  }

  // 10% còn lại: tạo lead — không hoàn tác được, phải dọn bằng SQL.
  // `leads` có UNIQUE index trên phone (WHERE deleted_at IS NULL) để tự phát
  // hiện lead trùng — xem V26__crm_lead_core.sql, UC-33 A1. Sinh phone NGẪU
  // NHIÊN sẽ đụng trùng và trả lỗi nghiệp vụ, làm bẩn số liệu lỗi của bài
  // test. Dùng số thứ tự iteration (duy nhất trong 1 lần chạy) + offset theo
  // lần chạy để không đụng dữ liệu của lần chạy trước còn sót.
  const phoneSeq = (RUN_OFFSET + exec.scenario.iterationInTest) % 100000000;
  const created = http.post(
    `${BASE_URL}/api/leads`,
    JSON.stringify({
      fullName: `${MARKER} ${uniq}`,
      phone: `09${String(phoneSeq).padStart(8, "0")}`,
      email: `${uniq}@loadtest.invalid`,
      leadSourceCode: "WEBSITE",
      initialMessage: `Bản ghi do ${MARKER} sinh ra — an toàn để xoá.`,
    }),
    { headers, tags: { ep: "leads.create" } }
  );
  permissionDenied.add(created.status === 403);
  serverErrors.add(created.status >= 500 || created.status === 0);
  if (check(created, { "lead tạo 2xx": (r) => r.status >= 200 && r.status < 300 }, { type: "write" })) {
    writeOpsOk.add(1);
    writeOpsLeaked.add(1); // luôn để lại 1 bản ghi cần dọn
  }
  sleep(0.5);
}

export function teardown(data) {
  // Smoke và SKIP_WRITES không sinh bản ghi nào -> nhắc dọn dữ liệu chỉ là
  // nhiễu, và tệ hơn là làm người đọc tưởng có rác cần xử lý.
  if (MODE === "smoke" || SKIP_WRITES) {
    console.log("Xong. Không có dữ liệu ghi nào được tạo.");
    return;
  }
  console.log(
    `Xong. Dọn dữ liệu còn lại bằng SQL với tiền tố "${MARKER}" — xem LOADTEST.md mục "Dọn dữ liệu sau khi test".`
  );
}
