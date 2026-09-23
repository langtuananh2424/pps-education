# E2E — Playwright (Plan link hoá thông báo)

Bộ test verify **bấm 1 thông báo ở quả chuông mở đúng đích** (app admin `:3000`
+ Portal Học sinh/Phụ huynh `:3001`) — xem "Plan: Link hoá thông báo"
(2026-09-22).

## Nguyên tắc

- **Không insert SQL trực tiếp.** Mọi tài khoản/bản ghi test (Phụ huynh + 2
  con, điểm danh, điểm, task, đơn nghỉ phép) dựng qua **API thật**
  (`tests/api.ts`, gọi `POST /api/...` bằng `APIRequestContext` của
  Playwright) — đi đúng luồng nghiệp vụ (mark → submit, enter → submit →
  publish...) nên notification sinh ra có `entityType/entityId/metadata`
  giống hệt thao tác qua UI thật.
- **Login qua form thật** (không inject token vào localStorage) — test đi
  đúng luồng người dùng thật sẽ trải qua.
- Tài khoản demo dùng để đăng nhập chỉ giới hạn ở 11 tài khoản cố định của
  `DevUserSeeder` (mật khẩu `Dev@123456`) — không đoán mật khẩu tài khoản
  khác đã có sẵn trong DB dev (VD `ph.nguyen`, tạo thủ công ngoài seeder).
  Phụ huynh/học sinh dùng để test luôn được **tạo mới qua API** với mật khẩu
  tự đặt.

## Chạy

Yêu cầu **backend đã chạy sẵn** ở `localhost:8080` (Postgres + backend qua
Docker — xem `CONTRIBUTING.md`/`README.md` gốc repo):

```bash
cd .. && docker compose up -d backend
```

Playwright tự khởi động 2 dev server (admin `npm run dev` :3000, user
`npm run dev` https:3001) qua `webServer` trong `playwright.config.ts` —
không cần tự chạy trước, nhưng nếu 1 trong 2 đã chạy sẵn (`preview_start`),
Playwright tái dùng (`reuseExistingServer: true`).

```bash
npm install
npx playwright install chromium   # 1 lần
npm test                          # cả 2 project (admin + user)
npx playwright test admin.spec.ts # chỉ app admin
npx playwright test user.spec.ts  # chỉ Portal
npm run test:headed               # xem trình duyệt chạy trực tiếp
npm run report                    # mở HTML report lần chạy gần nhất
```

## Phạm vi hiện tại (Đợt 1)

| Test | App | Loại thông báo | Verify |
|---|---|---|---|
| `admin.spec.ts` | admin | `TASK_ASSIGNED` | `navigate()` tới `/task-workflow?taskId=` + `AssignmentDetailModal` mở đúng task |
| `admin.spec.ts` | admin | `LEAVE_REQUEST_STATUS` (chờ duyệt) | `navigate()` tới `/hrm/leaves?leaveRequestId=` + đúng dòng hiện trong hàng chờ duyệt |
| `user.spec.ts` | Portal | `ATTENDANCE_MARK` | Phụ huynh đang xem con A, bấm thông báo của con B → đổi đúng con + tab "Lịch học & Chuyên cần" |
| `user.spec.ts` | Portal | `GRADE_PUBLISHED` | Phụ huynh đang xem con B, bấm thông báo của con A → đổi đúng con + tab "Khảo thí & Điểm số" |

Chưa cover: `PARTNER_FEEDBACK`, `EXAM_INTEGRITY_VIOLATION`,
`COMMENT_PENDING_APPROVAL`, `STUDENT_ATTITUDE_ESCALATION_PENDING_APPROVAL`
(admin) và `HOMEWORK_DUE_SOON_REMINDER`/`HOMEWORK_MISS_*` (Portal — nguồn
gốc là scheduled job, không có endpoint kích hoạt thủ công qua API) — có
thể bổ sung theo mẫu `tests/api.ts` khi cần.

## Dữ liệu test tồn dư

Mỗi lần chạy tạo tài khoản mới (username có timestamp, VD
`e2e_parent_<ts>`) — không tự xoá sau khi chạy (backend chưa có API xoá
Parent/Student). Chấp nhận được ở DB dev cục bộ; **không chạy suite này
nhắm vào staging/production**.
