// Load test THỰC TẾ cho luồng chấm AI tuần tự của Video phản xạ (UC-23b V2):
// viết -> AI chấm ngữ pháp -> đạt -> ghi âm -> AI chấm nội dung -> đạt ->
// mở câu tiếp theo. Xem docs/uc/phan-he-07-lms-portal.md (bổ sung V139) và
// ReflexSequentialGradingService.java.
//
// Đây là HIỆU NĂNG test (đo capacity), KHÔNG phải tấn công. Chỉ chạy nhắm
// vào staging/production CỦA BẠN, từ máy do bạn kiểm soát, đã báo trước cho
// người liên quan (xem LOADTEST.md mục "Checklist an toàn").
//
// MỤC TIÊU RIÊNG của bài test này (khác _api_suite/_login_flow): 2 endpoint
// dưới đây gọi ra NGOÀI hạ tầng (9Router -> AI provider thật), không chỉ
// chạm DB/CPU nội bộ -- nút thắt cần tìm là:
//   - app.ai-grading.nine-router-max-concurrent (Semaphore trong
//     NineRouterAiClient.java, mặc định 5) -- request vượt ngưỡng phải XẾP
//     HÀNG chờ thay vì gọi thẳng 9Router.
//   - HikariCP pool (DB_POOL_SIZE) -- mỗi lượt chấm giữ 1 connection SUỐT
//     thời gian chờ AI trả lời (@Transactional bọc quanh cả cuộc gọi AI).
//
// LƯU Ý QUAN TRỌNG -- API KHÔNG báo lỗi qua HTTP status khi AI chấm thất
// bại: vẫn trả 200 với writingFeedback/speakingFeedback =
// "Không chấm được tự động — vui lòng thử nộp lại." (xem hằng số
// AI_GRADING_FAILED_FEEDBACK trong ReflexSequentialGradingService.java).
// Script này TỰ PARSE response để đếm riêng (metric ai_grading_failed_rate)
// -- chỉ nhìn http_req_failed sẽ đánh giá SAI mức độ nghẽn thật.
//
// LƯU Ý DỮ LIỆU (khác _api_suite -- ở đó dùng tiền tố "LOADTEST" để dọn
// bằng SQL sau test): request ở đây GHI ĐÈ tiến trình thật của
// (học sinh, câu hỏi, lần giao) trong bảng reflex_question_progress, không
// có cột nào đánh dấu "đây là dữ liệu test" để dọn tự động. BẮT BUỘC dùng
// 1 tài khoản học sinh test riêng + 1 bộ Video phản xạ test riêng (không
// phải bộ đang giao thật cho lớp học sinh thật) -- nếu không, điểm/nhận
// xét thật của học sinh thật sẽ bị ghi đè bởi dữ liệu rác của bài load
// test.
//
// Chạy (2 lần riêng biệt -- xem lý do ở STEP bên dưới):
//   TARGET_URL=https://REPLACE-WITH-YOUR-STAGING-URL \
//   TEST_USERNAME=<tài_khoản_học_sinh_test> TEST_PASSWORD='<mật_khẩu>' \
//   REFLEX_ASSIGNMENT_ID=<id_lần_giao_test> \
//   REFLEX_QUESTION_IDS=<id1,id2,id3> \
//   STEP=writing \
//   k6 run loadtest_reflex_ai_grading.js
//
// Rồi đổi STEP=speaking (thêm REFLEX_AUDIO_URL) sau khi bước writing đã
// chạy xong (xem giải thích "Vì sao 2 lần chạy" bên dưới). Hoặc dùng
// `run_loadtest.bat reflex-writing` / `run_loadtest.bat reflex-speaking`
// (đã bọc sẵn tham số, xem LOADTEST.md).

import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Trend, Counter } from "k6/metrics";

const BASE_URL = __ENV.TARGET_URL || "https://REPLACE-WITH-YOUR-STAGING-URL";
const USERNAME = __ENV.TEST_USERNAME || "REPLACE_WITH_SEEDED_TEST_STUDENT_ACCOUNT";
const PASSWORD = __ENV.TEST_PASSWORD || "REPLACE_WITH_TEST_PASSWORD";
const ASSIGNMENT_ID = __ENV.REFLEX_ASSIGNMENT_ID || "";
const QUESTION_IDS = (__ENV.REFLEX_QUESTION_IDS || "")
  .split(",")
  .map((s) => s.trim())
  .filter(Boolean);
const AUDIO_URL = __ENV.REFLEX_AUDIO_URL || "";
const ANSWER_TEXT =
  __ENV.REFLEX_ANSWER_TEXT ||
  "I go to school every day and I like study English very much.";
// "writing" hoặc "speaking" -- KHÔNG trộn 2 loại trong cùng 1 lần chạy, vì
// speaking cần writingPassed=true có sẵn cho đúng combo (xem lý do dưới).
const STEP = __ENV.STEP || "writing";
const VU_PEAK = Number(__ENV.VU_PEAK || 50);

if (!ASSIGNMENT_ID || QUESTION_IDS.length === 0) {
  throw new Error(
    "Thiếu REFLEX_ASSIGNMENT_ID hoặc REFLEX_QUESTION_IDS -- xem hướng dẫn ở đầu file."
  );
}
if (STEP === "speaking" && !AUDIO_URL) {
  throw new Error(
    "STEP=speaking cần REFLEX_AUDIO_URL (link audio thật đã upload sẵn qua API media chung)."
  );
}

const AI_GRADING_FAILED_FEEDBACK = "Không chấm được tự động — vui lòng thử nộp lại.";

// Metric riêng để thấy lỗi "im lặng" (HTTP 200 nhưng AI chấm thất bại) --
// xem ghi chú "LƯU Ý QUAN TRỌNG" ở đầu file.
const aiGradingFailedRate = new Rate("ai_grading_failed_rate");
const questionPassedRate = new Rate("question_passed_rate");
// Bình thường khi STEP=speaking và combo chưa từng đạt writing (400) --
// đếm riêng, KHÔNG lẫn vào lỗi AI thật.
const speakingBlockedCount = new Counter("speaking_blocked_writing_not_passed_count");
const gradingDuration = new Trend("ai_grading_duration_ms", true);

export const options = {
  scenarios: {
    // Ramp từ từ giống loadtest_login_flow.js -- quan sát chỗ bắt đầu suy
    // giảm (semaphore 9Router / HikariCP pool) trước khi lên đỉnh tải.
    ramping_reflex_grading: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "30s", target: 5 }, // khớp nine-router-max-concurrent mặc định
        { duration: "1m", target: 10 },
        { duration: "1m", target: 20 },
        { duration: "1m", target: VU_PEAK }, // nấc dò trần -- tăng qua VU_PEAK nếu vẫn 0% lỗi
        { duration: "30s", target: 0 },
      ],
    },
  },
  thresholds: {
    // Ngưỡng THAM KHẢO ban đầu -- chỉnh lại sau khi có số liệu lần đầu,
    // giống cách tiếp cận của loadtest_login_flow.js.
    http_req_failed: ["rate<0.10"],
    http_req_duration: ["p(95)<15000"],
    ai_grading_failed_rate: ["rate<0.2"],
  },
};

function login() {
  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ usernameOrEmail: USERNAME, password: PASSWORD }),
    { headers: { "Content-Type": "application/json" }, tags: { name: "login" } }
  );
  const ok = check(res, {
    "login status 200": (r) => r.status === 200,
    "login không bị khoá (423)": (r) => r.status !== 423,
  });
  if (res.status === 423) {
    // Dừng ngay -- max-failed-attempts=5 (app.security.brute-force) đã
    // khoá tài khoản test 15 phút, chạy tiếp chỉ tạo số liệu sai lệch.
    throw new Error("Tài khoản test bị khoá (423) -- kiểm tra lại TEST_PASSWORD, đừng chạy tiếp.");
  }
  if (res.status === 409) {
    // requireNoActiveSessionForStudent() (AuthService.java) -- tài khoản HỌC SINH này còn 1 refresh
    // token active (chưa revoke/hết hạn, TTL 14 ngày). Có thể do lần chạy TRƯỚC bị Ctrl+C giữa chừng,
    // chưa kịp gọi teardown()/logout() -- xem SQL gỡ session kẹt trong LOADTEST.md, hoặc chờ
    // teardown() ở lần chạy này tự thu hồi khi xong (nếu để chạy hết, không Ctrl+C).
    throw new Error(
      "Tài khoản học sinh này đang có phiên đăng nhập ACTIVE khác (HTTP 409) -- có thể do lần chạy " +
        "trước bị Ctrl+C nên chưa kịp logout. Xem LOADTEST.md mục \"Load test riêng cho chấm AI Video " +
        "phản xạ\" để gỡ session kẹt bằng SQL trước khi chạy lại."
    );
  }
  if (!ok) {
    throw new Error(`Login thất bại: HTTP ${res.status} ${res.body}`);
  }
  const body = res.json();
  return { accessToken: body.accessToken, refreshToken: body.refreshToken };
}

// setup() chạy 1 lần, KHÔNG tính vào metric tải -- chỉ để đăng nhập trước,
// giống loadtest_api_suite.js.
export function setup() {
  return login();
}

// teardown() chạy 1 lần SAU khi hết mọi VU -- BẮT BUỘC logout để thu hồi
// refresh token, nếu không lần chạy TIẾP THEO cho cùng tài khoản học sinh
// này sẽ luôn bị chặn HTTP 409 (xem requireNoActiveSessionForStudent() ở
// AuthService.java) -- CHÚ Ý: teardown() KHÔNG chạy nếu bạn Ctrl+C giữa
// bài test (k6 không đảm bảo chạy teardown khi bị ngắt tín hiệu) -- khi đó
// phải gỡ session kẹt bằng SQL thủ công (xem LOADTEST.md).
export function teardown(data) {
  const res = http.post(
    `${BASE_URL}/api/auth/logout`,
    JSON.stringify({ refreshToken: data.refreshToken }),
    { headers: { "Content-Type": "application/json" }, tags: { name: "logout" } }
  );
  check(res, { "logout status 204": (r) => r.status === 204 });
}

export default function (data) {
  const questionId = QUESTION_IDS[(__VU + __ITER) % QUESTION_IDS.length];
  const url = `${BASE_URL}/api/review-video-questions/${questionId}/reflex-progress/${STEP}?assignmentId=${ASSIGNMENT_ID}`;
  const payload =
    STEP === "writing"
      ? JSON.stringify({ answerText: ANSWER_TEXT })
      : JSON.stringify({ audioUrl: AUDIO_URL });
  const headers = {
    "Content-Type": "application/json",
    Authorization: `Bearer ${data.accessToken}`,
  };

  const start = Date.now();
  const res = http.put(url, payload, { headers, tags: { name: `reflex_${STEP}` } });
  const elapsed = Date.now() - start;

  if (res.status === 400) {
    // Chỉ mong đợi ở STEP=speaking khi combo (học sinh, câu hỏi) chưa từng
    // đạt writing trước đó -- xem "Vì sao 2 lần chạy" ở đầu file.
    speakingBlockedCount.add(1);
  } else {
    const ok = check(res, { [`${STEP} status 200`]: (r) => r.status === 200 });
    if (ok) {
      gradingDuration.add(elapsed);
      const body = res.json();
      const feedback = STEP === "writing" ? body.writingFeedback : body.speakingFeedback;
      aiGradingFailedRate.add(feedback === AI_GRADING_FAILED_FEEDBACK ? 1 : 0);
      questionPassedRate.add(body.questionPassed ? 1 : 0);
    }
  }

  sleep(1); // khoảng nghỉ giữa các lượt của cùng 1 VU, mô phỏng hành vi thật
}
