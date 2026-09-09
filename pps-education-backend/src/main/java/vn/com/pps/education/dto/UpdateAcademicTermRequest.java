package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * UC-18 (bổ sung ngoài SDD gốc) — site/code bất biến sau khi tạo (mirror
 * Exam.curriculum), chỉ sửa tên/khoảng thời gian/năm học (V157 — cho sửa
 * lại năm học nếu gán nhầm).
 */
public record UpdateAcademicTermRequest(
        @NotNull Long academicYearId,
        @NotBlank String name,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {}
