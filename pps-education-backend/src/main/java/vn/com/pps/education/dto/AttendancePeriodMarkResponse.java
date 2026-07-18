package vn.com.pps.education.dto;

public record AttendancePeriodMarkResponse(
        Long id,
        Long attendanceMarkId,
        Long sessionPeriodId,
        String status,
        String note
) {}
