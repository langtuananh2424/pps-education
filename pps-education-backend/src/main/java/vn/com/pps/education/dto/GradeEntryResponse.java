package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record GradeEntryResponse(
        Long id,
        Long classId,
        Long studentId,
        String studentFullName,
        String studentCode,
        Long gradeComponentId,
        BigDecimal score,
        boolean absenceFlag,
        String teacherNote,
        String status,
        Long enteredBy,
        Long publishedBy,
        OffsetDateTime publishedAt
) {}
