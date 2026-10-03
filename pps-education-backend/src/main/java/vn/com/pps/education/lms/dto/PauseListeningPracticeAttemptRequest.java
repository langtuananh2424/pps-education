package vn.com.pps.education.lms.dto;

import jakarta.validation.constraints.NotNull;

/** UC-26 A1: học sinh tạm dừng giữa chừng, lưu vị trí để tiếp tục sau. */
public record PauseListeningPracticeAttemptRequest(
        @NotNull Integer positionSeconds
) {}
