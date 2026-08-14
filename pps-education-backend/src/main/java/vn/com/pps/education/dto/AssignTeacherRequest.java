package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** UC-18 Main Flow bước 1-2: điều phối giáo viên phụ trách lớp. */
public record AssignTeacherRequest(
        @NotNull Long teacherUserId,
        String teacherRole,
        Long subjectId,
        LocalDate assignedFrom,
        /** Loại giáo viên (VIETNAMESE/FOREIGN) — chỉ có ý nghĩa khi teacherRole=PRIMARY. */
        String teacherType
) {}
