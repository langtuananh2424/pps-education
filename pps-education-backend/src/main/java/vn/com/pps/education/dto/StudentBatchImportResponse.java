package vn.com.pps.education.dto;

import java.util.List;
import java.util.Map;

/**
 * UC-35: Nhập học theo lô cho lớp liên kết (FR-CRM-04).
 * generatedCredentials chỉ điền khi trả trực tiếp từ lần gọi importStudents()
 * (bổ sung ngoài SDD gốc, đã xác nhận với người dùng — giống pattern UC-51
 * EmployeeBatchImportResponse) — KHÔNG lưu vào import_jobs nên getJob() sau
 * đó không thấy lại mật khẩu tạm.
 */
public record StudentBatchImportResponse(
        Long id,
        String sourceFileName,
        Integer totalRows,
        int successRows,
        int failedRows,
        String status,
        List<Map<String, Object>> errorSummary,
        List<Map<String, Object>> generatedCredentials
) {}
