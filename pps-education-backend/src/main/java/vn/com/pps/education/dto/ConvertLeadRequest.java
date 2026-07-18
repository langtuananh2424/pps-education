package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-34 Main Flow: mã học sinh (student_code) do người dùng tự nhập khi chuyển đổi lead, không tự sinh. */
public record ConvertLeadRequest(
        @NotBlank String studentCode
) {}
