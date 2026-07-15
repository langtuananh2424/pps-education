package vn.com.pps.education.dto;

import java.util.List;
import java.util.Map;

/**
 * UC-51: Nhập nhân sự theo lô (FR-HRM-05).
 * generatedCredentials chỉ được điền khi trả trực tiếp từ lần gọi
 * importEmployees() — KHÔNG lưu vào import_jobs nên gọi lại getJob() sau
 * đó sẽ không thấy mật khẩu tạm (tránh lộ plaintext qua tra cứu lại).
 */
public record EmployeeBatchImportResponse(
        Long id,
        String sourceFileName,
        Integer totalRows,
        int successRows,
        int failedRows,
        String status,
        List<Map<String, Object>> errorSummary,
        List<Map<String, Object>> generatedCredentials
) {}
