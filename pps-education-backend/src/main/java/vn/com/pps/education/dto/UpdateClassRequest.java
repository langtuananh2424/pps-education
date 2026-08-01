package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** UC-18: cập nhật thông tin hành chính của lớp học đã có. */
public record UpdateClassRequest(
        @NotBlank String name,
        @NotNull Integer maxStudents,
        Integer minStudents,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        String academicYear,
        @NotBlank String status
) {}
