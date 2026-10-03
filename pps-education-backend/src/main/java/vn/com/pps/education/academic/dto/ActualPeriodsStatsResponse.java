package vn.com.pps.education.academic.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Báo cáo "Số tiết thực tế theo lớp" (bổ sung ngoài SDD gốc, xác nhận với
 * người dùng 2026-08-20) — số tiết đã dạy thực tế (chỉ tính buổi
 * COMPLETED, xác nhận 2026-10-01 — UC-48 A5) của từng lớp trong 1 điểm trường, theo khoảng
 * thời gian tuỳ chọn (tuần/tháng/kỳ/năm — xem periodType).
 */
public record ActualPeriodsStatsResponse(String periodType, String periodLabel, LocalDate startDate, LocalDate endDate,
                                          Long siteId, String siteName, List<ActualPeriodsClassRow> classes,
                                          long totalActualPeriods) {
}
