# PPS Education — Monorepo

Hệ thống quản lý trung tâm Anh ngữ PPS English. Tài liệu nghiệp vụ đầy đủ: SRS,
SDD, Đặc tả Use Case IEEE (43 UC) — xem thư mục `docs/` (nếu đã đồng bộ) hoặc
kho tài liệu dự án.

## Cấu trúc repo

```
.
├── pps-education-backend/    # Spring Boot (Controller-Service-Repository, NFR-TECH-02)
├── docker-compose.yml        # Môi trường dev cục bộ (NFR-TECH-06)
└── .github/workflows/        # CI (NFR-TECH-05)
```

Frontend (React, NFR-TECH-03) sẽ được thêm vào dưới dạng thư mục
`pps-education-frontend/` khi bắt đầu **Frontend Phase 1** (xem kế hoạch phân kỳ).

## Chạy môi trường dev

```bash
docker compose up -d --build          # postgres + backend
docker compose --profile tools up -d  # thêm pgadmin (http://localhost:5050)
```

Backend: http://localhost:8080
Swagger UI: http://localhost:8080/swagger-ui.html

Chạy backend trực tiếp bằng Maven (không qua Docker) khi dev:
```bash
docker compose up -d postgres   # chỉ cần Postgres, chạy app trong IDE
cd pps-education-backend
mvn spring-boot:run
```
Không cần set biến môi trường gì thêm — default trong `application.yml` đã
khớp sẵn với `docker-compose.yml`. Xem `.env.example` (root repo) để biết đầy
đủ các biến có thể override (`DB_URL/DB_USERNAME/DB_PASSWORD/JWT_SECRET/
GOOGLE_OAUTH_CLIENT_IDS/...`) và cách dùng khi chạy Maven trực tiếp.

**Máy đã có PostgreSQL native chiếm port 5432?** Docker sẽ báo container chạy
bình thường nhưng app kết nối nhầm sang Postgres native (lỗi `password
authentication failed` dù password đúng) — kiểm tra bằng cách xem log
`docker logs pps-education-db` có ghi nhận lần kết nối vừa thử không, nếu
không thì đúng là bị native Postgres chặn port. Xử lý: copy
`docker-compose.override.yml.example` thành `docker-compose.override.yml`
(đã gitignore), đổi port publish, cập nhật `DB_URL` cho khớp.

## Trạng thái Phase A (Nền tảng) — đã setup trong khung này

- [x] Cấu trúc project Spring Boot (Controller-Service-Repository)
- [x] Docker Compose (Postgres + PostGIS + Backend + pgadmin tùy chọn)
- [x] CI GitHub Actions (build + test trên PR vào `main`)
- [x] Flyway migration V1: bảng nền (`users`, `roles`, `permissions`,
      `user_roles`, `role_permissions`, `user_permission_overrides`,
      `permission_audit_log`, `refresh_tokens`, `login_attempts`,
      `system_settings`, `import_jobs`, `approval_flows`)
- [x] Flyway migration V2: `sites`, `rooms`, `equipment`, `partner_contracts`,
      `partner_school_info`, `site_managers`, `partner_feedbacks` (dữ liệu nền
      Phân hệ 10, kéo sớm vì Phân hệ 6 phụ thuộc)
- [x] Flyway migration V3: `employees`, `employment_contracts`,
      `qualifications`, `commendations` (dữ liệu nền Phân hệ 4, kéo sớm vì
      Phân hệ 6 cần gán Giáo viên vào lớp)
- [x] Flyway migration V4: seed 11 role hệ thống + permission catalog khởi điểm
- [x] Flyway migration V5: bổ sung cột `updated_at` cho bảng `permissions`
      (entity `Permission` kế thừa `BaseAuditEntity`, thiếu sót từ V1)
- [x] Entity + Repository cho nhóm Auth/Permission
- [x] `POST /api/auth/login` — khung UC-01 (Main Flow + A1 sai mật khẩu + A2
      khóa tài khoản sau 5 lần sai + A3 tài khoản INACTIVE)
- [x] Spring Security: JWT stateless filter, BCrypt password encoder
- [x] Unit test + integration test (Testcontainers) cho AuthService — Main
      Flow + A1 + A2 + A3 (xem `AuthServiceTest`, PR #3)

## Việc còn lại của Sprint 1 (UC-01 hoàn chỉnh)

- [ ] `login_attempts` entity + ghi log mỗi lần đăng nhập (thành công/thất bại)
- [ ] `POST /api/auth/login/google` — nhánh Google OAuth (Main Flow bước 4, A4)
      — lưu ý: block cấu hình `spring.security.oauth2.client.registration.google`
      đã bị gỡ khỏi `application.yml` (gây fail cứng lúc khởi động khi chưa có
      credentials thật) — thêm lại cùng lúc implement nhánh này, kèm credentials
      thật qua biến môi trường.
- [ ] `POST /api/auth/refresh`, `POST /api/auth/logout` (thu hồi refresh token)
- [ ] Gửi cảnh báo cho Quản trị viên khi tài khoản bị khóa (FR-AUT-02) — phụ
      thuộc module Notification (Backend Phase B, Phân hệ 3)

## Sprint 2 (UC-02 → UC-05 — Quản trị người dùng & Phân quyền)

- [ ] `PermissionEvaluator` triển khai công thức `effective_permissions`
      (role_permissions hợp user_permission_overrides, ưu tiên override)
- [ ] CRUD danh mục quyền (UC-02), cấu hình nhóm quyền theo role (UC-03)
- [ ] API tùy chỉnh quyền riêng theo tài khoản + ghi `permission_audit_log`
      (UC-04, UC-05)

Chi tiết đầy đủ backlog theo từng Sprint/Phase: xem tài liệu
**"Kế hoạch phân kỳ & Backlog theo FR"**.

## Tài liệu nghiệp vụ & Claude Code

- `docs/srs.md`, `docs/sdd-groups/`, `docs/uc/` — SRS, SDD (tách theo 9 nhóm
  bảng), Đặc tả Use Case IEEE (tách theo 10 phân hệ). Đây là nguồn chân lý
  nghiệp vụ, dùng khi implement hoặc review bất kỳ tính năng nào.
- `CLAUDE.md` — ngữ cảnh dự án cho Claude Code (trỏ tới đúng file cần đọc
  theo từng loại việc, không nạp toàn bộ `docs/` mỗi phiên).
- `.claude/skills/` — skill riêng cho dự án: `pps-uc-lookup` (tra cứu 1 UC
  theo mã), `pps-add-migration` (tạo Flyway migration đúng quy ước).

## Quy trình Git & CI/CD

Xem chi tiết đầy đủ trong [`CONTRIBUTING.md`](./CONTRIBUTING.md): chiến lược
nhánh (`main` / `develop` / `feature/*` / `hotfix/*`), pipeline CI/CD
(`backend-ci.yml`, `cd-staging.yml`, `cd-production.yml`), và quy trình từng
bước cho 1 dev xử lý 1 task.

## Quy ước code

- Package layout: `config / controller / service / repository / domain / dto /
  security / exception / common` — 1 package dùng chung cho toàn bộ phân hệ,
  KHÔNG tách package theo module ở giai đoạn này (repo còn nhỏ); sẽ đánh giá
  lại việc tách package-by-feature khi bắt đầu Backend Phase B.
- Toàn bộ bảng có `created_at/updated_at` kế thừa `BaseAuditEntity`.
- Không tự sinh DDL từ Hibernate (`ddl-auto: validate`) — Flyway là nguồn chân
  lý duy nhất cho schema.
- Nhánh Git: `main` (production) + `develop` + nhánh tính năng
  `feature/UC-xx-mo-ta`, merge qua Pull Request (NFR-TECH-05).
