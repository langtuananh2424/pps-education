package vn.com.pps.education.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record ClassSessionResponse(
        Long id,
        Long classId,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        Long roomId,
        String roomName,
        Long primaryTeacherId,
        String primaryTeacherName,
        String sessionType,
        String status,
        String cancellationReason,
        Long rescheduledToSessionId,
        String lessonContent
) {}
