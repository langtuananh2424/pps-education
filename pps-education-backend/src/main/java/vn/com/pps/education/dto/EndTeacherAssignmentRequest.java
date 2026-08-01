package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** UC-18 bổ sung — kết thúc phụ trách của 1 giáo viên với 1 lớp (giáo viên đổi theo kỳ). */
public record EndTeacherAssignmentRequest(
        @NotNull LocalDate assignedTo
) {}
