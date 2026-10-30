## Tóm tắt

Thêm trang **Quản trị hệ thống, Cấu hình chấm phát âm** (`/system-admin/pps-config`) để chỉnh các tham số của bộ chấm PPS Hybrid v2 mà không cần deploy. Trước đây `PpsConfigHolder` luôn trả giá trị mặc định cứng trong mã; chỗ cắm cho trang này đã được chừa sẵn.

## Thay đổi

**Backend**
- Migration `V225`: bảng `pps_scoring_config` (một dòng, chỉ lưu các tham số khác mặc định) và `pps_scoring_config_history` (giá trị trước, giá trị sau, người sửa, ghi chú).
- `PpsScoringConfigService`: đọc, lưu, khôi phục mặc định, xuất, lịch sử. Dùng lại `PpsConfig.validate` (khoảng cho phép, các nhóm trọng số cộng bằng 1, ràng buộc giữa các tham số) nên cùng một bộ luật với lab. Lưu xong `PpsConfigHolder` đổi ngay (sau khi giao dịch chốt); backend khởi động nạp từ DB và nhiều bản backend đồng bộ qua việc đọc lại sau tối đa 60 giây. Cấu hình hỏng trong DB không làm backend hỏng: ghi cảnh báo và dùng mặc định.
- `PpsScoringConfigController` (`/api/pps-scoring-config`): xem và lịch sử cần `system.settings.view`; lưu, khôi phục cần `system.settings.manage`. **Không thêm quyền mới.**

**Frontend (admin)**
- Trang vẽ biểu mẫu từ danh sách tham số backend trả về (nhãn, nhóm, mô tả, khoảng cho phép, bước nhập, mặc định), nên thêm tham số mới ở backend là trang tự có.
- Báo lỗi ngay khi gõ (ngoài khoảng, trọng số không cộng bằng 1, `phone.top` và `phone.floor`, ngắt dài và ngắt ngắn), đánh dấu "khác mặc định" và "chưa lưu", đặt từng tham số về mặc định, ghi chú thay đổi, hoàn tác, khôi phục mặc định (có hỏi lại), xuất và nhập JSON (nhập chỉ điền biểu mẫu, phải bấm Lưu), xem lịch sử.
- Menu `Cấu hình chấm phát âm` trong nhóm Quản trị hệ thống; i18n vi và en.

**Tài liệu:** `deploy/README.md` mục 5b.

## Lưu ý

- Lưu là lưu TOÀN BỘ cấu hình trên trang: tham số không đổi sẽ về mặc định. Biểu mẫu luôn hiện đủ mọi tham số nên không bất ngờ.
- Các tham số nhóm "Nói tự do" hiện chưa có tác dụng vì `SpontaneousScorer` cố ý không được nối vào luồng chấm; trang vẫn cho chỉnh để khớp lab.
- Chỉnh ở đây ảnh hưởng điểm học viên khi `PRONUNCIATION_PROVIDER=gopt`. Nên thử trên staging.
- Chưa xem trang bằng trình duyệt thật với backend chạy; đã kiểm tra bằng `tsc`, `vite build` và test logic. Cần xem tay trên staging sau khi deploy.

## Kiểm thử

- Backend: 11 test mới cho `PpsScoringConfigService` (lưu, chỉ lưu giá trị khác mặc định, tham số vắng về mặc định, từ chối giá trị sai kèm tên tham số và không lưu gì, không ghi lịch sử khi không đổi, khôi phục mặc định, xuất rồi nhập lại, nạp lúc khởi động và bỏ qua khi DB không đổi, cấu hình hỏng dùng mặc định, lịch sử) cùng `PpsConfigTest` đạt với JDK 21. Migration chưa chạy thử với Postgres trên máy, nhờ job `check-migrations` và `build-and-test` của CI.
- Frontend: `tsc --noEmit` và `vite build` đạt; script kiểm tra logic biểu mẫu (11 ca) đạt.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
