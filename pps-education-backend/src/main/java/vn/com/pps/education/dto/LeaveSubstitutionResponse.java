package vn.com.pps.education.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** UC-11 Mở rộng: trang lịch sử dạy thay. */
public record LeaveSubstitutionResponse(
        Long id,
        Long leaveRequestId,
        Long classSessionId,
        LocalDate sessionDate,
        Long classId,
        String className,
        Long originalTeacherId,
        String originalTeacherName,
        Long substituteTeacherId,
        String substituteTeacherName,
        OffsetDateTime revokedAt,
        OffsetDateTime createdAt
) {}
