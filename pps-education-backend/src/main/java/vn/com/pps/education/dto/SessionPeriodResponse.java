package vn.com.pps.education.dto;

import java.time.LocalTime;

public record SessionPeriodResponse(
        Long id,
        Long classSessionId,
        /** Buổi Sáng/Chiều/Tối — bổ sung ngoài SDD gốc, 2026-08-20. */
        String dayPart,
        int periodNumber,
        LocalTime startTime,
        LocalTime endTime,
        Long teacherId,
        Long subjectId,
        String contentNote
) {}
