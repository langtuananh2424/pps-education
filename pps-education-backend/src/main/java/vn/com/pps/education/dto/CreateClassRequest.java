package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** UC-18 Main Flow bước 3-4: khởi tạo record lớp học thực tế. */
public record CreateClassRequest(
        @NotBlank String classCode,
        @NotBlank String name,
        @NotNull Long siteId,
        @NotNull Long curriculumId,
        @NotBlank String classType,
        @NotNull Integer maxStudents,
        Integer minStudents,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        Long academicYearId
) {}
