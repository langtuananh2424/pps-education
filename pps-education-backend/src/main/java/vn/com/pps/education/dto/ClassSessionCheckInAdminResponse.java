package vn.com.pps.education.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * UC-71: Nhận lớp — bổ sung ngoài SDD/SRS gốc, đã xác nhận với người dùng
 * 2026-09-11. Dòng tổng hợp cho bảng admin "Dữ liệu chấm công nhận lớp"
 * (tab riêng biệt với "Dữ liệu chấm công ca làm việc" UC-09), gộp thông tin
 * buổi học + trạng thái nhận lớp TÍNH RA (xem
 * ClassSessionCheckInService#listAdminEffectiveStatus, tái dùng nguyên vẹn
 * listEffectiveStatus đã có — không có logic tính trạng thái mới nào ở đây).
 */
public record ClassSessionCheckInAdminResponse(
        Long classSessionId,
        Long teacherId,
        String teacherFullName,
        String teacherCode,
        Long classId,
        String className,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        Long siteId,
        String siteName,
        OffsetDateTime checkInTime,
        /** NOT_YET_OPEN | PENDING | ON_TIME | LATE | ABSENT. */
        String effectiveStatus
) {}
