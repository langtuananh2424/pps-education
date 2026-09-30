# Product

<!-- impeccable:product-schema 1 -->

Phạm vi: chỉ ứng dụng quản trị **PPS Admin 2.0** (`pps-education-frontend/admin`).
Portal Học sinh/Phụ huynh (`pps-education-frontend/user`) nằm ngoài tài liệu này.

## Platform

web

## Users

Nhân sự nội bộ trung tâm Anh ngữ PPS English và đối tác trường liên kết. Không có
nhóm người dùng nổi trội: các vai trò dùng thường xuyên như nhau, mỗi vai trò có
dashboard riêng chỉ hiện chức năng và dữ liệu trong phạm vi quyền (NFR-UI-02).

- **Giáo viên** — điểm danh, viết nhận xét hằng ngày, soạn/giao đề, chấm bài,
  xem lịch dạy; có thể dạy ở nhiều điểm trường.
- **Quản lý điểm trường** — vận hành một hoặc nhiều điểm trường (cơ sở của trung
  tâm hoặc trường liên kết): duyệt nhận xét, điểm, cảnh báo thái độ, thư mời phụ
  huynh, đơn nghỉ phép; đầu mối phản hồi của phụ huynh và trường liên kết.
- **Trưởng phòng đào tạo** — khung chương trình chuẩn, phê duyệt bản tùy biến
  theo điểm trường, xếp lớp, xếp lịch, điều phối giáo viên.
- **Quản lý nhân sự** — hồ sơ, hợp đồng lao động, chấm công, ca làm việc, bảng lương.
- **Nhân viên** (tư vấn tuyển sinh, CSKH, giáo vụ, kế toán) — lead, nhập học,
  hóa đơn, chi phí vận hành.
- **Quản lý vận hành** — hợp đồng liên kết trường, sự kiện tại trường.
- **Ban giám đốc** — báo cáo tổng hợp toàn hệ thống, phê duyệt quyết định chiến lược.
- **Quản trị viên** — tài khoản, vai trò, quyền, cấu hình hệ thống, nhật ký.
- **Đại diện trường liên kết** — chỉ xem báo cáo học sinh trường mình và gửi phản
  hồi; không sửa dữ liệu học thuật.

Vai trò là mặc định theo tổ chức, không phải ràng buộc cứng: một tài khoản có thể
được cấp thêm hoặc tước bớt quyền riêng (FR-PER-03).

## Product Purpose

Một hệ thống duy nhất vận hành trung tâm Anh ngữ đa điểm trường qua 10 phân hệ:
đăng nhập, phân quyền, công việc, nhân sự, học sinh, học thuật, LMS, tài chính,
CRM, cơ sở vật chất (và báo cáo – thống kê). Thành công nghĩa là mỗi vai trò làm
xong việc hằng ngày của mình nhanh, đúng quy trình nghiệp vụ trong SRS/UC, và mọi
thông tin tới phụ huynh đều đã được kiểm soát.

## Positioning

- **Trường liên kết là hạng nhất:** trung tâm dạy cả tại trường công/tư liên kết —
  có hợp đồng liên kết, khung chương trình tùy biến theo điểm trường, và Đại diện
  trường liên kết xem báo cáo của học sinh trường mình.
- **Duyệt trước khi tới phụ huynh:** nhận xét, điểm, cảnh báo thái độ, thư mời phụ
  huynh đều qua Quản lý điểm trường duyệt trước khi gửi.
- **Trợ lý AI trong quy trình:** AI soạn nháp nhận xét hằng ngày từ audio (UC-74)
  và soát nhận xét chờ duyệt (UC-75); lượng token AI được theo dõi.
- **Nhiều điểm trường:** một giáo viên dạy nhiều điểm; quản lý phụ trách nhiều cơ sở
  cùng lúc; dữ liệu luôn lọc theo điểm trường người dùng phụ trách.

## Operating Context

- Việc lặp lại hằng ngày theo buổi học: điểm danh → nhận xét → duyệt → phụ huynh
  nhận thông báo. Điểm danh chỉ sửa được trong khung giờ buổi học.
- Hàng chờ duyệt là trung tâm công việc của Quản lý điểm trường.
- Nhập liệu theo lô bằng Excel phổ biến (nhân sự, phụ huynh, học sinh, điểm, lịch
  học, danh mục sách, video ôn tập).
- Xuất báo cáo từ mẫu Word/HTML/PDF; xuất Excel ở nhiều bảng.
- Chấm công theo địa điểm và ca làm việc.
- Nghiệp vụ nguồn chân lý: `docs/srs.md`, `docs/uc/phan-he-*.md`, `docs/sdd-groups/`.

## Capabilities and Constraints

- Stack hiện có: React 19 + TypeScript + Vite + Tailwind CSS 4, react-router,
  i18next, lucide-react; Backend Spring Boot riêng, giao tiếp HTTPS/REST.
- Ngôn ngữ: tiếng Việt là chính, tiếng Anh là phụ (NFR-UI-03); locale `vi`, `en`.
- Responsive: dùng tốt trên máy tính và trình duyệt di động (NFR-UI-01).
- Thuật ngữ bám SRS/SDD: điểm trường, trường liên kết, khung chương trình, BTVN,
  nhận xét, cảnh báo thái độ, đơn từ, v.v.
- Chưa quyết định: Portal Học sinh/Phụ huynh có dùng chung ngôn ngữ thiết kế với
  Admin 2.0 hay không.

## Brand Commitments

- Tên sản phẩm: **PPS Admin 2.0**, thuộc hệ thống **PPS English / PPS Education**.
- Đã có design system "PPS Admin 2.0" (artifact
  https://claude.ai/artifact/JrkadaS3Wvc2x1Gz2rN7aJ) và canvas màn hình
  "PPS Admin 2.0 — Màn hình" (https://claude.ai/artifact/7e8UTj9Cb8ecxjqbyLBezn).
  Mọi công việc giao diện mới phải dùng design system này.

## Evidence on Hand

- Tài liệu nghiệp vụ đầy đủ: SRS, SDD, đặc tả UC chuẩn IEEE trong `docs/`.
- Tài khoản demo cho 11 vai trò (README gốc, cờ `SEED_DEV_USERS`).
- Không có số liệu người dùng, khảo sát, hay trích dẫn khách hàng — không được bịa.

## Product Principles

1. **Mỗi vai trò chỉ thấy việc của mình.** Dashboard và menu theo phạm vi quyền,
   không phô toàn bộ 10 phân hệ cho mọi người.
2. **Trung thành với nghiệp vụ.** Luồng màn hình bám Main Flow/Alternate Flow của
   UC; không tự thêm hoặc bỏ bước.
3. **Không gì tới phụ huynh mà chưa được duyệt.** Trạng thái chờ duyệt / đã duyệt /
   bị từ chối phải luôn rõ ràng, có lý do khi từ chối.
4. **Ít bước cho việc hằng ngày.** Điểm danh, nhập điểm, nhận xét, duyệt hàng loạt
   phải tối ưu thao tác nhanh (NFR-UI-04).
5. **Điểm trường là ngữ cảnh luôn hiện diện.** Người dùng luôn biết mình đang xem
   dữ liệu của điểm trường nào.

## Accessibility & Inclusion

Cỡ chữ và độ tương phản đảm bảo dễ đọc; các trang tương tác quan trọng (điểm danh,
nhập điểm) hạn chế số bước click (NFR-UI-04). Giao diện song ngữ Việt – Anh.
