package vn.com.pps.education.dto;

import java.util.List;

/**
 * Lưới "số tiết thực tế" (bổ sung ngoài SDD gốc, xác nhận 2026-08-20) —
 * hàng đầu (columns) là các tháng/kỳ/năm, cột đầu (mỗi row) là từng lớp,
 * xem Javadoc ActualPeriodsReportService#getGrid.
 */
public record ActualPeriodsGridResponse(String periodType, Long siteId, String siteName,
                                         List<ActualPeriodsGridColumn> columns, List<ActualPeriodsGridRow> rows) {
}
