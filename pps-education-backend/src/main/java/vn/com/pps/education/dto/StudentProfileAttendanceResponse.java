package vn.com.pps.education.dto;

import java.time.LocalDate;

/** Bổ sung ngoài SDD gốc — 1 lượt điểm danh trong StudentProfileResponse (FR-REP-04), giới hạn số buổi gần nhất — xem StudentProfileService. */
public record StudentProfileAttendanceResponse(
        Long id,
        Long classSessionId,
        Long classId,
        String className,
        LocalDate sessionDate,
        Integer sessionNumber,
        String academicTermName,
        String academicYear,
        String status,
        Integer minutesLate,
        Integer minutesEarlyLeave
) {
}
