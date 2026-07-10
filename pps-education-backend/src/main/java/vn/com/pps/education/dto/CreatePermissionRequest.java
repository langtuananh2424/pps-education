package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** UC-02 bước 2: tạo quyền mới. code theo format &lt;module&gt;.&lt;action&gt;, VD class.create, grade.approve. */
public record CreatePermissionRequest(
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9]*(\\.[a-z][a-z0-9_]*)+$",
                message = "code phải theo format <module>.<action>, chỉ chữ thường/số/underscore") String code,
        @NotBlank String name,
        @NotBlank String module,
        String description
) {}
