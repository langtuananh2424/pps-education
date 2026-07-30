package vn.com.pps.education.dto;

import java.time.OffsetDateTime;

public record AttendanceMarkResponse(
        Long id,
        Long attendanceSessionId,
        Long classSessionId, // UC-64 tự xem (học sinh/phụ huynh): nối đúng điểm danh vào buổi học cụ thể
        Long studentId,
        String studentFullName,
        String studentCode,
        String status,
        Integer minutesLate,
        Integer minutesEarlyLeave,
        String absenceReason,
        OffsetDateTime notifiedParentAt
) {}
