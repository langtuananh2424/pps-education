package vn.com.pps.education.dto;

import java.util.List;
import java.util.Map;

/** UC-53: Nhập điểm thi qua Excel (FR-ACA-03) — kết quả tổng hợp import_jobs. */
public record GradeImportResponse(
        Long id,
        String sourceFileName,
        Integer totalRows,
        int successRows,
        int failedRows,
        String status,
        List<Map<String, Object>> errorSummary
) {}
