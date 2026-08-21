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
        Long academicYearId,
        @NotBlank String status,
        /** Đổi màu hiển thị trên lịch làm việc dạng lưới — để trống (null) thì giữ nguyên màu cũ (bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-08-21). */
        String color
) {}
