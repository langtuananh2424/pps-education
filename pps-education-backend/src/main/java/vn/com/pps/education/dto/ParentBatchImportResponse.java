package vn.com.pps.education.dto;

import java.util.List;
import java.util.Map;

/** UC-50: Nhập phụ huynh theo lô, liên kết học sinh có sẵn (FR-STU-04). */
public record ParentBatchImportResponse(
        Long id,
        String sourceFileName,
        Integer totalRows,
        int successRows,
        int failedRows,
        String status,
        List<Map<String, Object>> errorSummary
) {}
