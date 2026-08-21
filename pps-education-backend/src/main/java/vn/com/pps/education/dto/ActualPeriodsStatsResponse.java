package vn.com.pps.education.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Báo cáo "Số tiết thực tế theo lớp" (bổ sung ngoài SDD gốc, xác nhận với
 * người dùng 2026-08-20) — số tiết đã dạy thực tế (không tính buổi
 * CANCELLED/RESCHEDULED) của từng lớp trong 1 điểm trường, theo khoảng
 * thời gian tuỳ chọn (tuần/tháng/kỳ/năm — xem periodType).
 */
public record ActualPeriodsStatsResponse(String periodType, String periodLabel, LocalDate startDate, LocalDate endDate,
                                          Long siteId, String siteName, List<ActualPeriodsClassRow> classes,
                                          long totalActualPeriods) {
}
