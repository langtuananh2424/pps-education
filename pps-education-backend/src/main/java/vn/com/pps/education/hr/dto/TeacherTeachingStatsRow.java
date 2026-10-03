package vn.com.pps.education.hr.dto;

import java.util.List;

/**
 * 1 giáo viên trong "Thống kê giảng dạy theo giáo viên" (V203 — bổ sung ngoài SDD gốc, xác nhận với
 * người dùng 2026-09-30). onTimeRate = % buổi đã diễn ra có nhận lớp đúng giờ (null khi chưa có buổi
 * nào diễn ra). Dòng tổng cộng có teacherUserId = null.
 * reportOnTime/Late/MissingCount (V207): số buổi giáo viên nộp báo cáo (gửi duyệt nhận xét) đúng hạn / muộn /
 * quá hạn chưa nộp — null khi khoảng ngày dài hơn SessionReportTrackingService.MAX_RANGE_DAYS.
 * V209 (xác nhận với người dùng 2026-10-02): tách số tiết theo vai trò trong buổi học —
 * taughtPeriods = tiết dạy với vai trò GIÁO VIÊN CHÍNH (ý nghĩa cũ giữ nguyên), assistantPeriods = tiết
 * với vai trò GIÁO VIÊN PHỤ, cmPeriods = tiết với vai trò CM, totalPeriods = tổng 3 loại. roles = các vai
 * trò (PRIMARY/ASSISTANT/CM) giáo viên có trong khoảng ngày (rỗng ở dòng tổng). Số lớp/buổi/nhận lớp/báo
 * cáo vẫn chỉ tính theo vai trò giáo viên chính.
 */
public record TeacherTeachingStatsRow(
        Long teacherUserId,
        String teacherName,
        String employeeCode,
        List<String> roles,
        long classCount,
        long scheduledSessions,
        long heldSessions,
        long cancelledSessions,
        long taughtPeriods,
        long assistantPeriods,
        long cmPeriods,
        long totalPeriods,
        long onTimeCheckIns,
        long lateCheckIns,
        long missingCheckIns,
        Double onTimeRate,
        Integer reportOnTimeCount,
        Integer reportLateCount,
        Integer reportMissingCount
) {}
