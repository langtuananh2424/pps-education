package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** class_enrollments Main Flow bổ trợ UC-18: ghi danh học sinh vào lớp. */
public record EnrollStudentRequest(
        @NotNull Long studentId,
        @NotNull LocalDate enrolledDate
) {}
