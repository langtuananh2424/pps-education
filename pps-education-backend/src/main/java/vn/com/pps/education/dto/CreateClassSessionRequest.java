package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/** UC-48: Xếp lịch buổi học (FR-ACA-05) — xem Javadoc ClassSessionService. */
public record CreateClassSessionRequest(
        @NotNull LocalDate sessionDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        Long roomId,
        @NotNull Long primaryTeacherId,
        @NotBlank String sessionType,
        /** Loại giáo viên (VIETNAMESE/FOREIGN) — tùy chọn, hiển thị cho Học sinh/Phụ huynh. */
        String teacherType
) {}
