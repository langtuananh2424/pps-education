package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** Bổ sung ngoài UC cụ thể — xem Javadoc CreateDepartmentRequest. Mã phòng ban (code) không đổi được sau khi tạo. */
public record UpdateDepartmentRequest(
        @NotBlank String name,
        Long headUserId,
        Long parentDepartmentId
) {}
