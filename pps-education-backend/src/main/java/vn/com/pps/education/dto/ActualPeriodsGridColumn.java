package vn.com.pps.education.dto;

import java.time.LocalDate;

/**
 * 1 cột của lưới "số tiết thực tế" (bổ sung ngoài SDD gốc, xác nhận
 * 2026-08-20) — mirror EnrollmentMovementGridColumn, key dùng làm khoá tra
 * cứu trong {@link ActualPeriodsGridRow#actualPeriodsByColumnKey()}.
 */
public record ActualPeriodsGridColumn(String key, String label, LocalDate startDate, LocalDate endDate) {
}
