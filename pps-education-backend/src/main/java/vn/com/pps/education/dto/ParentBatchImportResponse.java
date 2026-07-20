package vn.com.pps.education.dto;

import java.util.List;
import java.util.Map;

/**
 * UC-50: Nhập phụ huynh theo lô, liên kết học sinh có sẵn (FR-STU-04).
 * generatedCredentials chỉ điền khi có tài khoản MỚI được tạo trong đúng lần
 * gọi importParents() này (bổ sung ngoài SDD gốc, đã xác nhận với người
 * dùng — giống pattern UC-51 EmployeeBatchImportResponse) — KHÔNG lưu vào
 * import_jobs nên getJob() sau đó không thấy lại mật khẩu tạm. Dòng dùng
 * lại Parent đã tồn tại (VD anh chị em ruột cùng SĐT) không sinh credential
 * mới cho dòng đó.
 */
public record ParentBatchImportResponse(
        Long id,
        String sourceFileName,
        Integer totalRows,
        int successRows,
        int failedRows,
        String status,
        List<Map<String, Object>> errorSummary,
        List<Map<String, Object>> generatedCredentials
) {}
