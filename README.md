# PPS Education — Monorepo

Hệ thống quản lý trung tâm Anh ngữ PPS English. Tài liệu nghiệp vụ đầy đủ: SRS,
SDD, Đặc tả Use Case IEEE (10 Phân hệ) — xem thư mục [`docs/`](./docs).

## Cấu trúc repo

```
.
├── pps-education-backend/    # Spring Boot (Controller-Service-Repository, NFR-TECH-02)
├── docker-compose.yml        # Môi trường dev cục bộ (NFR-TECH-06)
├── docs/                     # SRS, SDD, Đặc tả Use Case (nguồn chân lý nghiệp vụ)
└── .github/workflows/        # CI/CD (NFR-TECH-05)
```

Frontend (React, NFR-TECH-03) sẽ được thêm vào dưới dạng thư mục
`pps-education-frontend/` khi bắt đầu **Frontend Phase 1** (xem kế hoạch phân kỳ).

## Yêu cầu môi trường

- **JDK 21** (repo chưa có Maven Wrapper — cần cài Maven 3.9+ riêng, hoặc dùng
  Maven đi kèm IDE).
- **Docker Desktop** (chạy Postgres + PostGIS cho dev local; CI dùng
  Testcontainers nên cũng cần Docker nếu muốn chạy `mvn test` full trên máy).
- Không cần cài Node/Frontend ở giai đoạn hiện tại.

## Chạy môi trường dev

```bash
docker compose up -d --build          # postgres + backend
docker compose --profile tools up -d  # thêm pgadmin (http://localhost:5050)
```

Backend: http://localhost:8080
Swagger UI: http://localhost:8080/swagger-ui.html

Chạy backend trực tiếp bằng Maven (không qua Docker) khi dev — cách phổ biến
nhất, cho phép chạy/debug trong IDE:
```bash
docker compose up -d postgres   # chỉ cần Postgres, chạy app trong IDE/terminal
cd pps-education-backend
mvn spring-boot:run
```
Không cần set biến môi trường gì thêm — default trong `application.yml` đã
khớp sẵn với `docker-compose.yml`. Muốn override (VD đổi port DB, bật Google
OAuth test, cấu hình SMTP thật) thì copy `pps-education-backend/.env.example`
thành `pps-education-backend/.env` (Spring Boot tự nạp file này mỗi lần khởi
động nhờ `spring-dotenv` — xem `pom.xml` — không cần export biến môi trường
thủ công, và `.env` đã nằm trong `.gitignore` nên không lo commit nhầm).

**Máy đã có PostgreSQL native chiếm port 5432?** Docker sẽ báo container chạy
bình thường nhưng app kết nối nhầm sang Postgres native (lỗi `password
authentication failed` dù password đúng) — kiểm tra bằng cách xem log
`docker logs pps-education-db` có ghi nhận lần kết nối vừa thử không, nếu
không thì đúng là bị native Postgres chặn port. Xử lý: copy
`docker-compose.override.yml.example` thành `docker-compose.override.yml`
(đã gitignore — Compose tự động áp dụng file này, không cần flag gì thêm),
đổi port publish, cập nhật `DB_URL` trong `.env` cho khớp port mới.

### Tài khoản demo

Khi bật cờ `SEED_DEV_USERS` (xem bên dưới), app tự tạo 11 tài khoản demo —
1 tài khoản cho mỗi role hệ thống — lúc khởi động (code:
`vn.com.pps.education.config.DevUserSeeder`, idempotent nên restart bao
nhiêu lần cũng không tạo trùng). Đăng nhập qua `POST /api/auth/login` với
`usernameOrEmail` là username hoặc email bên dưới, **mật khẩu chung cho tất
cả: `Dev@123456`**.

| Username       | Email                     | Role hệ thống  | Vai trò                    |
|----------------|---------------------------|----------------|----------------------------|
| `sysadmin`     | sysadmin@pps.edu.vn       | SYS_ADMIN      | Quản trị viên              |
| `headacademic` | headacademic@pps.edu.vn   | HEAD_ACADEMIC  | Trưởng phòng đào tạo       |
| `sitemanager`  | sitemanager@pps.edu.vn    | SITE_MANAGER   | Quản lý điểm trường        |
| `hrmanager`    | hrmanager@pps.edu.vn      | HR_MANAGER     | Quản lý nhân sự            |
| `staff`        | staff@pps.edu.vn          | STAFF          | Nhân viên                  |
| `opsmanager`   | opsmanager@pps.edu.vn     | OPS_MANAGER    | Quản lý vận hành           |
| `executive`    | executive@pps.edu.vn      | EXECUTIVE      | Ban giám đốc               |
| `partnerrep`   | partnerrep@pps.edu.vn     | PARTNER_REP    | Đại diện trường liên kết   |
| `teacher`      | teacher@pps.edu.vn        | TEACHER        | Giáo viên                  |
| `parent`       | parent@pps.edu.vn         | PARENT         | Phụ huynh                  |
| `student`      | student@pps.edu.vn        | STUDENT        | Học sinh                   |

**Bật/tắt cơ chế seed** — điều khiển bằng biến môi trường `SEED_DEV_USERS`
(map vào property `app.seed.dev-users` trong `application.yml`, mặc định
`false` — cố tình không gắn theo Spring profile `dev` để seed data không
lọt vào test suite/staging/production):

- **Chạy qua docker compose**: đã bật sẵn dòng `SEED_DEV_USERS: "true"`
  trong `docker-compose.yml` (service `backend`) — muốn tắt thì đổi thành
  `"false"` hoặc xóa dòng đó rồi `docker compose up -d --build` lại.
- **Chạy qua `mvn spring-boot:run`**: bật sẵn dòng `SEED_DEV_USERS=true`
  trong `.env.example` — copy thành `.env` là có; muốn tắt thì đổi thành
  `false` hoặc xóa dòng đó trong `.env`.
- Tắt cờ **không xóa** các tài khoản đã seed trước đó — chỉ ngừng tạo mới;
  muốn xóa hẳn thì xóa thủ công trong DB (hoặc xóa volume Postgres tạo lại).

### Chạy test

```bash
cd pps-education-backend
mvn test        # unit + integration test (Testcontainers — cần Docker chạy sẵn)
mvn clean verify
```

## Trạng thái hiện tại

Toàn bộ 10 Phân hệ nghiệp vụ trong [`docs/uc/`](./docs/uc) đã được triển khai
(Controller/Service/Repository + migration Flyway + test cho từng Main
Flow/Alternate Flow của mỗi UC). Chi tiết từng Pull Request xem lịch sử Git/PR
trên GitHub — README không theo dõi changelog chi tiết theo Sprint để tránh
lạc hậu; nguồn chân lý về tiến độ là trạng thái `develop` + PR đã merge.

Các gap nghiệp vụ đã biết (thiếu cơ chế trong SRS/SDD gốc, đã xác nhận với PM
thay vì tự suy đoán) được ghi chú trực tiếp trong Javadoc của Service liên
quan — tìm theo từ khóa "chưa làm"/"gap" trong code nếu cần tra cứu.

## Tài liệu nghiệp vụ & Claude Code

- `docs/srs.md`, `docs/sdd-groups/`, `docs/uc/` — SRS, SDD (tách theo 9 nhóm
  bảng), Đặc tả Use Case IEEE (tách theo 10 phân hệ). Đây là nguồn chân lý
  nghiệp vụ, dùng khi implement hoặc review bất kỳ tính năng nào.
- `CLAUDE.md` — ngữ cảnh dự án cho Claude Code (trỏ tới đúng file cần đọc
  theo từng loại việc, không nạp toàn bộ `docs/` mỗi phiên).
- `.claude/skills/` — skill riêng cho dự án: `pps-uc-lookup` (tra cứu 1 UC
  theo mã), `pps-add-migration` (tạo Flyway migration đúng quy ước),
  `pps-docker-recovery` (khôi phục khi Docker Desktop/Postgres chết giữa
  phiên), `pps-cool-build` (giảm nhiệt CPU trước khi build/test nặng).

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
  lý duy nhất cho schema. Migration mới luôn là file `Vn__mo_ta.sql` mới,
  không sửa migration cũ đã merge.
- Nhánh Git: `main` (production) + `develop` + nhánh tính năng
  `feature/UC-xx-mo-ta`, merge qua Pull Request (NFR-TECH-05).
