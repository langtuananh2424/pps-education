package vn.com.pps.education.academic.dto;

/** Tổng hợp nộp báo cáo theo giáo viên (V207). onTimeRate = % buổi đã tới hạn được nộp đúng hạn (null nếu chưa có buổi nào tới hạn). */
public record SessionReportTeacherSummary(
        Long teacherUserId,
        String teacherName,
        int sessionCount,
        int onTimeCount,
        int lateCount,
        int missingCount,
        int rejectionCount,
        int resubmitLateCount,
        Double onTimeRate
) {}
