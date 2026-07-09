# PPS Education — Ngữ cảnh dự án cho Claude Code

## Dự án
Hệ thống quản lý trung tâm Anh ngữ PPS English, 10 phân hệ, kiến trúc
Backend (Spring Boot, Controller-Service-Repository) + Frontend (React) tách
biệt. Toàn bộ chi tiết nghiệp vụ nằm trong `docs/` — đọc file cụ thể theo
việc đang làm, KHÔNG cần đọc hết mọi file mỗi phiên.

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
4. Code theo layer `controller/service/repository/domain/dto` — xem ví dụ
   UC-01 đã có sẵn (`AuthController`/`AuthService`) làm khuôn mẫu.

## Quy trình Git/CI/CD
Xem `CONTRIBUTING.md` — nhánh `main`/`develop`/`feature/UC-xx-...`, PR bắt
buộc CI xanh, không sửa migration Flyway đã tồn tại.

## Quy ước code
- Package dùng chung `vn.com.pps.education.{config,controller,service,repository,domain,dto,security,exception,common}`
  — chưa tách theo module (repo còn nhỏ).
- Không tự sinh DDL từ Hibernate (`ddl-auto: validate`) — Flyway là nguồn
  chân lý schema duy nhất.
- Toàn bộ entity có `created_at/updated_at` kế thừa `BaseAuditEntity`.
- String trong code (biến, log kỹ thuật) dùng tiếng Anh; comment/Javadoc
  nghiệp vụ dùng tiếng Việt bám sát thuật ngữ trong SRS/SDD để dễ đối chiếu.

## Trạng thái hiện tại
Đã xong: Sprint 0 (setup hạ tầng) + khung Sprint 1 (UC-01 đăng nhập, chưa có
nhánh Google OAuth/refresh/logout). Xem mục "Việc còn lại" trong `README.md`.
