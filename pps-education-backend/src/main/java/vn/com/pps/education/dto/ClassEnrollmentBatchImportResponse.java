package vn.com.pps.education.dto;

import java.util.List;
import java.util.Map;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — ghi danh
 * học sinh ĐÃ TỒN TẠI SẴN vào 1 lớp theo lô qua Excel (mirror
 * ParentBatchImportResponse/StudentBatchImportResponse). Không tạo học
 * sinh/tài khoản mới (khác UC-35/UC-50) — chỉ tạo class_enrollments cho
 * học sinh đã có sẵn, nên không có generatedCredentials.
 */
public record ClassEnrollmentBatchImportResponse(
        Long id,
        String sourceFileName,
        Integer totalRows,
        int successRows,
        int failedRows,
        String status,
        List<Map<String, Object>> errorSummary
) {}
