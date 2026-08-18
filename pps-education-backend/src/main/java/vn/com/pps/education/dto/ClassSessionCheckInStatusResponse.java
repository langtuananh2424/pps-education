package vn.com.pps.education.dto;

import java.time.OffsetDateTime;

/**
 * Trạng thái nhận lớp TÍNH RA cho 1 buổi học — dùng chung cho cả roster
 * admin (EmployeeScheduleOverviewResponse) lẫn trang GV tự xem, tránh lặp
 * logic tính cửa sổ ở 2 nơi. Xem ClassSessionCheckInService#listEffectiveStatus.
 */
public record ClassSessionCheckInStatusResponse(
        Long classSessionId,
        /** NOT_YET_OPEN | PENDING | ON_TIME | LATE | ABSENT. */
        String effectiveStatus,
        OffsetDateTime checkInTime
) {}
