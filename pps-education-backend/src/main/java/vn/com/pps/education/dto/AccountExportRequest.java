package vn.com.pps.education.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Xuất file Excel danh sách tài khoản vừa tạo qua import (Student/Parent/
 * Employee) — FE gửi lại NGUYÊN danh sách {@code generatedCredentials} đã
 * nhận được trong response của chính lần gọi import đó (BE không lưu mật
 * khẩu plaintext ở đâu cả, kể cả tạm — xem Javadoc *BatchImportService),
 * nên chỉ tải được trong cùng phiên vừa import.
 */
public record AccountExportRequest(
        @NotEmpty List<@Valid AccountEntry> accounts
) {
    public record AccountEntry(
            @NotBlank String username,
            @NotBlank String temporaryPassword,
            String fullName
    ) {}
}
