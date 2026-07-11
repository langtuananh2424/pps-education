package vn.com.pps.education.dto;

import java.time.OffsetDateTime;

public record AttendanceMarkResponse(
        Long id,
        Long attendanceSessionId,
        Long studentId,
        String studentFullName,
        String studentCode,
        String status,
        Integer minutesLate,
        Integer minutesEarlyLeave,
        String absenceReason,
        OffsetDateTime notifiedParentAt
) {}
