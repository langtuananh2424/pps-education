package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** UC-18 (bổ sung ngoài SDD gốc) — site/code bất biến sau khi tạo (mirror Exam.curriculum), chỉ sửa tên/khoảng thời gian. */
public record UpdateAcademicTermRequest(
        @NotBlank String name,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {}
