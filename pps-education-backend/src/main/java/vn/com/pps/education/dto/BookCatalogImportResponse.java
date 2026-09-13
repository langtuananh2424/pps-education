package vn.com.pps.education.dto;

import java.util.List;
import java.util.Map;

/**
 * UC-72: Import Excel nhanh mục lục Sách/Unit/Sub Topic/Lesson (exams)/Bài
 * (exercises) của Kho đề. Xem docs/uc/phan-he-07-lms-portal.md (UC-72).
 */
public record BookCatalogImportResponse(
        Long id,
        String sourceFileName,
        Integer totalRows,
        int successRows,
        int failedRows,
        String status,
        List<Map<String, Object>> errorSummary
) {}
