package vn.com.pps.education.dto;

import java.util.List;

/**
 * Lưới "biến động học sinh" (bổ sung ngoài SDD gốc, xác nhận 2026-08-20) —
 * hàng đầu (columns) là các tháng/kỳ/năm, cột đầu (mỗi row) là từng lớp,
 * xem Javadoc EnrollmentMovementReportService#getGrid.
 */
public record EnrollmentMovementGridResponse(String periodType, Long siteId, String siteName,
                                              List<EnrollmentMovementGridColumn> columns,
                                              List<EnrollmentMovementGridRow> rows) {
}
