package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

/** UC-10 bước 3: 1 buổi học của Giáo viên + giáo viên dạy thay được chọn. */
public record SubstituteAssignmentRequest(
        @NotNull Long classSessionId,
        @NotNull Long substituteTeacherId
) {}
