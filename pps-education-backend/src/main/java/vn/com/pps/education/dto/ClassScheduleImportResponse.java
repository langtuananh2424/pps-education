package vn.com.pps.education.dto;

import java.util.List;
import java.util.Map;

/** UC-57: Nhập lịch học qua Excel (FR-ACA-05) — kết quả tổng hợp import_jobs. */
public record ClassScheduleImportResponse(
        Long id,
        String sourceFileName,
        Integer totalRows,
        int successRows,
        int failedRows,
        String status,
        List<Map<String, Object>> errorSummary
) {}
