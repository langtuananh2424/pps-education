package vn.com.pps.education.dto;

import java.util.List;
import java.util.Map;

/**
 * UC-21 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-24) —
 * kết quả tổng hợp import_jobs cho nhận xét Hàng ngày kiểu mới qua Excel.
 */
public record DailyCommentImportResponse(
        Long id,
        String sourceFileName,
        Integer totalRows,
        int successRows,
        int failedRows,
        String status,
        List<Map<String, Object>> errorSummary
) {}
