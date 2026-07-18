package vn.com.pps.education.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** UC-15 Main Flow bước 1-2: điểm danh cả lớp cho 1 buổi học, theo chế độ SESSION_LEVEL hoặc PERIOD_LEVEL. */
public record MarkAttendanceRequest(
        @NotBlank String mode,
        @NotEmpty List<@Valid EnterAttendanceMarkRequest> marks
) {}
