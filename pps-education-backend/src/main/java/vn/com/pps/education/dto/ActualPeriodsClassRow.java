package vn.com.pps.education.dto;

/**
 * 1 hàng (1 lớp) của báo cáo "Số tiết thực tế theo lớp" (bổ sung ngoài SDD
 * gốc, xác nhận với người dùng 2026-08-20).
 */
public record ActualPeriodsClassRow(Long classId, String classCode, String className, long actualPeriods) {
}
