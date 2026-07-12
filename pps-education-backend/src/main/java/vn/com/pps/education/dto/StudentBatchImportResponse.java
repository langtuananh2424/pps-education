package vn.com.pps.education.dto;

import java.util.List;
import java.util.Map;

public record StudentBatchImportResponse(
        Long id,
        String sourceFileName,
        Integer totalRows,
        int successRows,
        int failedRows,
        String status,
        List<Map<String, Object>> errorSummary
) {}
