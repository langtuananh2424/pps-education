package vn.com.pps.education.dto;

import java.time.LocalDate;

/**
 * 1 cột của lưới "biến động học sinh" (bổ sung ngoài SDD gốc, xác nhận
 * 2026-08-20) — key dùng làm khoá tra cứu trong
 * {@link EnrollmentMovementGridRow#headcountByColumnKey()} (VD "2026-08" cho
 * tháng, id kỳ dạng chuỗi cho kỳ học, "2026" cho năm); label là tên hiển thị.
 */
public record EnrollmentMovementGridColumn(String key, String label, LocalDate startDate, LocalDate endDate) {
}
