package vn.com.pps.education.academic.dto;

/**
 * 1 buổi học trong ngày trên dashboard Trưởng phòng đào tạo (V203). checkInState:
 * CANCELLED (buổi huỷ) / ON_TIME / LATE (đã nhận lớp) / MISSING (đã tới giờ, chưa nhận lớp) /
 * NOT_STARTED (chưa tới giờ).
 */
public record AcademicDashboardSessionItem(
        Long sessionId,
        Long classId,
        String className,
        String classCode,
        String siteName,
        String teacherName,
        String startTime,
        String endTime,
        String checkInState
) {}
