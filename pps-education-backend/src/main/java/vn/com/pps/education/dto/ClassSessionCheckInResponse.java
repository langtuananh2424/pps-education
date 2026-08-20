package vn.com.pps.education.dto;

import java.time.OffsetDateTime;

public record ClassSessionCheckInResponse(
        Long id,
        Long classSessionId,
        Long teacherId,
        String teacherName,
        OffsetDateTime checkInTime,
        /** ON_TIME | LATE. */
        String status
) {}
