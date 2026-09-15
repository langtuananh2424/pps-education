-- =====================================================================
-- V176: Tách 2 mục đích sử dụng của sites -- "dùng cho xếp lớp/Nhận lớp"
-- vs "dùng cho Chấm công GPS" (bổ sung HOÀN TOÀN ngoài SDD/SRS gốc, đã xác
-- nhận với người dùng 2026-09-14).
--
-- Bối cảnh: trước migration này, trang "Địa điểm chấm công" (HRM) và trang
-- "Điểm trường & HĐ" (Facility) dùng chung 100% dữ liệu sites không lọc gì
-- khác nhau (xem AttendanceSitesPage.tsx) -- mọi điểm trường tạo ra đều vừa
-- xếp được lớp (UC-71 Nhận lớp) vừa tính vào bán kính Chấm công GPS (UC-09
-- A2), không có cách nào thêm 1 địa điểm CHỈ dùng cho chấm công (VD trụ sở
-- văn phòng hành chính, không phải điểm trường) mà không bị lẫn vào danh
-- sách điểm trường xếp lớp.
--
-- Default TRUE cho cả 2 cột để không đổi hành vi của mọi site hiện có
-- (toàn bộ điểm trường đang xếp lớp + đang tính chấm công tiếp tục hoạt
-- động y như cũ ngay sau migration).
ALTER TABLE sites
    ADD COLUMN used_for_classes    BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN used_for_attendance BOOLEAN NOT NULL DEFAULT TRUE;
