# Quy trình làm việc — PPS Education Backend

## 1. Nhánh

| Nhánh | Vai trò |
|---|---|
| `main` | Production. Chỉ nhận merge qua PR từ `develop` hoặc `hotfix/*`. |
| `develop` | Tích hợp, luôn deploy được lên staging. Chỉ nhận merge qua PR từ `feature/*`. |
| `feature/UC-xx-mo-ta` | 1 nhánh cho 1 đầu việc, tách từ `develop`, xóa sau khi merge. |
| `hotfix/mo-ta` | Sửa khẩn cấp trên production, merge vào cả `main` và `develop`. |
| `release/phase-x` | (Tùy chọn) Cắt cuối mỗi Phase A/B/C để test hồi quy trước khi lên `main`. |

Đặt tên nhánh theo mã UC trong tài liệu Đặc tả Use Case / Kế hoạch phân kỳ để
dễ trace: `feature/UC-15-diem-danh-hoc-sinh`.

**Bảo vệ nhánh** (cấu hình tại GitHub Settings → Branches — cần quyền admin repo):
- `main`, `develop`: chặn push trực tiếp, bắt buộc PR + CI xanh + (khi có ≥2 dev)
  ít nhất 1 approval, bắt buộc branch cập nhật với base trước khi merge.

## 2. Quy trình 1 dev xử lý 1 task

1. Nhận task (mã UC/FR) từ Sprint backlog.
2. `git checkout develop && git pull`
3. `git checkout -b feature/UC-xx-mo-ta`
4. Local dev: `docker compose up -d postgres` rồi chạy app trong IDE (profile `dev`),
   hoặc `docker compose up -d --build` để chạy full container.
5. Code theo layer `controller/service/repository/domain`. Nếu đổi schema:
   thêm file Flyway **mới** `Vn__mo_ta.sql` — không sửa migration cũ (checksum
   sẽ vỡ với DB của người khác đã chạy migration đó).
6. Viết unit test (Service) + integration test (Testcontainers) nếu chạm DB
   — mỗi Alternate Flow của UC là 1 test case riêng (xem
   `.claude/rules/testing.md`).
6b. Đối chiếu code với `.claude/rules/architecture.md`, `solid.md`,
    `business-fidelity.md` — đặc biệt: không lộ Entity qua Controller,
    business logic đúng 100% Main Flow/Alternate Flow/Postcondition của UC.
7. `mvn clean verify` local phải xanh trước khi push.
8. Commit theo [Conventional Commits](https://www.conventionalcommits.org/):
   `feat(auth): ...`, `fix(permission): ...`, `test(student): ...`, `chore: ...`.
9. Push, mở PR vào `develop` (dùng `.github/PULL_REQUEST_TEMPLATE.md`).
10. CI (`backend-ci.yml`) tự chạy. Đợi review (nếu có) → **squash merge** → xóa nhánh.
11. CD (`cd-staging.yml`) tự deploy `develop` lên staging. Tự kiểm tra trên
    staging trước khi đóng task trên bảng Sprint.

## 3. Release lên production

Khi kết thúc 1 Phase (A/B/C) hoặc 1 nhóm tính năng đủ ổn định:

1. (Tùy chọn) Cắt `release/phase-x` từ `develop`, test hồi quy, chỉ sửa bug trên
   nhánh này (không thêm tính năng mới).
2. Mở PR `release/phase-x` (hoặc `develop`) → `main`.
3. Merge → CI chạy lại → `cd-production.yml` build image, gắn tag semver,
   chờ approval (GitHub Environment `production`) → deploy Railway production.
4. Tag Git: `git tag vX.Y.Z && git push --tags`.

## 4. Quy ước migration Flyway

- File đặt tại `src/main/resources/db/migration/`, đặt tên `V{n}__mo_ta.sql`,
  `n` tăng dần, không trùng, không tái sử dụng số đã dùng.
- Không bao giờ sửa nội dung file migration đã merge vào `develop`/`main` —
  nếu cần sửa lỗi, tạo migration mới để "chữa" (ALTER/UPDATE) thay vì sửa file cũ.
- Migration phải chạy được trên DB rỗng từ đầu (`V1` → `Vn`) lẫn trên DB đã có
  dữ liệu — tránh `DROP TABLE`/`DROP COLUMN` phá dữ liệu nếu không thực sự cần.
