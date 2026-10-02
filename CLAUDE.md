# PPS Education — Ngữ cảnh dự án cho Claude Code

## Dự án
Hệ thống quản lý trung tâm Anh ngữ PPS English, 10 phân hệ, đang chạy thật
(staging + production). Kiến trúc Backend (Spring Boot, Controller-Service-
Repository) + 2 Frontend React tách biệt. Toàn bộ chi tiết nghiệp vụ nằm
trong `docs/` — đọc file cụ thể theo việc đang làm, KHÔNG cần đọc hết mọi
file mỗi phiên.

## Cấu trúc repo
- `pps-education-backend/` — Spring Boot (JDK 21, Maven, chưa có Maven
  Wrapper). Migration Flyway ở `src/main/resources/db/migration/`.
- `pps-education-frontend/admin/` — React + Vite + TypeScript, app quản trị
  cho nhân sự nội bộ (kể cả Giáo viên), dev ở `:3000`.
- `pps-education-frontend/user/` — React + Vite + TypeScript, Portal Học sinh/
  Phụ huynh, dev ở `:3001` (HTTPS nếu có cert mkcert trong `.certs/`).
- `e2e/` — Playwright E2E chạy trên cả 2 app, dữ liệu dựng qua API thật
  (xem `e2e/README.md`).
- `deploy/` — compose staging/production, nginx, systemd backup, runbook
  server vật lý (xem `deploy/README.md`).
- `scripts/` — `gen-api-md.pl` sinh lại `API.md` từ OpenAPI; script đo chất
  lượng trợ lý nhận xét AI.
- `security-lab/` — script load test/kiểm thử bảo mật, không phải code app.
- `API.md` — bản chụp toàn bộ endpoint, SINH TỰ ĐỘNG, không sửa tay.

## Quy tắc code — `.claude/rules/`
- `architecture.md` — kiến trúc phân lớp Controller→Service→Repository,
  ranh giới DTO/Entity, vị trí business logic, transaction boundary. Luôn
  nạp (không path-scoped) vì là bất biến áp dụng mọi lúc.
- `solid.md` — 5 nguyên tắc SOLID kèm ví dụ cụ thể theo domain PPS
  Education (chỉ nạp khi đọc/sửa file `.java`).
- `business-fidelity.md` — quy trình bắt buộc để code không sai lệch so
  với SRS/SDD/UC (chỉ nạp khi đọc/sửa `service/`, `domain/`, hoặc
  migration `.sql`).
- `testing.md` — mỗi Alternate Flow trong UC phải có 1 test case riêng
  (chỉ nạp khi đọc/sửa file test).

> ⚠️ Rule có `paths:` frontmatter đôi khi không tự nạp đúng như tài liệu mô
> tả (vấn đề đã biết của Claude Code, tùy phiên bản). Nếu nghi ngờ 1 rule
> không được áp dụng, chạy `/context` để kiểm tra danh sách file đã nạp;
> nếu thiếu, chủ động yêu cầu Claude đọc file rule đó trước khi làm việc.

## Tài liệu — đọc theo nhu cầu, đừng đọc hết
- `docs/srs.md` — yêu cầu chức năng (FR) theo 10 phân hệ, 11 tác nhân, ma
  trận Actor × Phân hệ.
- `docs/diagrams/` — nguồn Mermaid gốc (`.mmd`), đã nhúng sẵn trực tiếp vào
  `docs/srs.md` và `docs/sdd-groups/*.md` (dạng ```` ```mermaid ```` fenced
  block) — không cần mở riêng trừ khi cần sửa sơ đồ. Sửa sơ đồ thì sửa ở
  đây, KHÔNG sửa đoạn nhúng trong srs.md/sdd-groups (sẽ bị ghi đè lần sau).
  3 nhóm chính: `erd/` (ERD theo nhóm bảng), `activity/` (8 luồng nghiệp vụ
  phức tạp), `usecase-actors/` (phân rã use case theo tác nhân); cộng
  `architecture/` (sơ đồ kiến trúc tổng thể hệ thống).
- `docs/sdd-groups/README.md` — mục lục 9 nhóm bảng CSDL, trỏ tới từng file
  `docs/sdd-groups/0N-*.md` (mỗi file 1 nhóm bảng, kèm mô tả cột/kiểu dữ
  liệu/ràng buộc). Đọc đúng nhóm liên quan tới bảng đang cần, không đọc hết.
- `docs/sdd-groups/00-intro-va-kien-truc.md` — kiến trúc tổng thể, tech
  stack, nguyên tắc thiết kế xuyên suốt (NFR).
- `docs/uc/phan-he-NN-*.md` — đặc tả use case đầy đủ chuẩn IEEE (Precondition/
  Main Flow/Alternate Flow/Postcondition) theo từng phân hệ. Khi implement 1
  UC cụ thể, đọc đúng file phân hệ chứa UC đó.
- `PPS_Education_-_Ke_hoach_phan_ky_va_Backlog.docx` (ngoài repo, do PM giữ)
  — kế hoạch Sprint/Phase, mã UC/FR cho từng Sprint.

## Quy tắc khi implement 1 UC
1. Đọc đúng file `docs/uc/phan-he-NN-*.md` chứa UC đó để lấy Precondition/
   Main Flow/Alternate Flow/Postcondition.
2. Đối chiếu bảng CSDL liên quan trong `docs/sdd-groups/` — dùng đúng tên
   bảng/cột/kiểu dữ liệu/ràng buộc đã thiết kế, không tự đặt lại.
3. Nếu cần đổi schema: thêm file Flyway MỚI theo quy ước trong
   `CONTRIBUTING.md` (không sửa migration cũ).
4. Code theo layer `controller/service/repository/domain/dto` — xem
   `AuthController`/`AuthService` (UC-01) hoặc UC cùng phân hệ đã có làm
   khuôn mẫu.

## Quy trình Git/CI/CD
Xem `CONTRIBUTING.md`. Luồng nhánh: `feature/UC-xx-...` (hoặc `fix/...`) →
PR vào `develop` (chỉ chạy local, không deploy) → PR `develop` → `main`
(auto-deploy staging) → PR `main` → `production` (auto-deploy production).
`hotfix/*` merge vào cả `production` và `main`. PR bắt buộc CI xanh
(`backend-ci.yml`, `frontend-ci.yml`), không sửa migration Flyway đã tồn tại.

## Lệnh chạy/kiểm tra
- Backend: `docker compose up -d postgres` rồi `cd pps-education-backend &&
  mvn spring-boot:run`; test `mvn test` / `mvn clean verify` (Testcontainers,
  cần Docker).
- Frontend (mỗi app): `npm ci`, `npm run dev`, `npm run lint` (= `tsc
  --noEmit`), `npm run build`.
- E2E: backend chạy sẵn ở `:8080`, rồi `cd e2e && npm test`.

## Quy ước code
- Package dùng chung `vn.com.pps.education.{config,controller,service,repository,domain,dto,security,exception,common}`
  — chưa tách theo module (repo còn nhỏ).
- Không tự sinh DDL từ Hibernate (`ddl-auto: validate`) — Flyway là nguồn
  chân lý schema duy nhất.
- Toàn bộ entity có `created_at/updated_at` kế thừa `BaseAuditEntity`.
- String trong code (biến, log kỹ thuật) dùng tiếng Anh; comment/Javadoc
  nghiệp vụ dùng tiếng Việt bám sát thuật ngữ trong SRS/SDD để dễ đối chiếu.

## Trạng thái hiện tại
Toàn bộ 10 phân hệ trong `docs/uc/` đã được triển khai (backend + 2
frontend), đang chạy trên staging/production. Việc hiện tại chủ yếu là sửa
lỗi và hoàn thiện theo góp ý người dùng thật — nguồn chân lý về tiến độ là
nhánh `develop` + PR đã merge, không phải file này. Gap nghiệp vụ đã biết
được ghi trong Javadoc Service liên quan (tìm "chưa làm"/"gap").

## Skill riêng của dự án — `.claude/skills/`
`pps-uc-lookup` (tra 1 UC theo mã), `pps-add-migration` (tạo Flyway
migration đúng quy ước), `pps-docker-recovery` (khôi phục Docker/Postgres
chết giữa phiên), `pps-cool-build` (giảm nhiệt máy dev trước build nặng).
