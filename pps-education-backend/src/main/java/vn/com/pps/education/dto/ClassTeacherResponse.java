package vn.com.pps.education.dto;

import java.time.LocalDate;

public record ClassTeacherResponse(
        Long id,
        Long classId,
        Long teacherUserId,
        String teacherFullName,
        String teacherRole,
        String teacherType,
        Long subjectId,
        LocalDate assignedFrom,
        LocalDate assignedTo
) {}
