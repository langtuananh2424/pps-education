package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Chuyển lớp hàng loạt cuối năm học (bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng 2026-08-07) — tạo lớp mới kế thừa site/class_type của lớp cũ,
 * mã lớp/curriculum/năm học nhập tay. Xem ClassService#promoteClass.
 */
public record PromoteClassRequest(
        @NotBlank String classCode,
        @NotBlank String name,
        @NotNull Long curriculumId,
        @NotNull Long academicYearId,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @NotNull Integer maxStudents,
        Integer minStudents
) {}
