package vn.com.pps.education.dto;

import java.time.LocalTime;

public record SessionPeriodResponse(
        Long id,
        Long classSessionId,
        int periodNumber,
        LocalTime startTime,
        LocalTime endTime,
        Long teacherId,
        Long subjectId,
        String contentNote
) {}
