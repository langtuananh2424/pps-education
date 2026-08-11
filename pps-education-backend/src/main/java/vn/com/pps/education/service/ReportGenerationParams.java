package vn.com.pps.education.service;

import vn.com.pps.education.dto.ReportPeriodSelector;

import java.util.List;

/**
 * UC-68: tham số xác định đối tượng + phạm vi thời gian cần resolve dữ
 * liệu cho 1 báo cáo. Field nào không cần thì resolver bỏ qua:
 * classId/periods dùng cho resolver theo lớp/kỳ đánh giá (VD
 * TranscriptReportDataResolver); classSessionId dùng cho resolver theo 1
 * buổi học cụ thể (VD DailyReportDataResolver — báo cáo 1 tài liệu/buổi,
 * không phải 1 tài liệu/học sinh).
 */
public record ReportGenerationParams(Long studentId, Long classId, List<ReportPeriodSelector> periods, Long classSessionId) {

    public ReportGenerationParams {
        if (periods == null) {
            periods = List.of();
        }
    }

    public static ReportGenerationParams of(Long studentId, Long classId) {
        return new ReportGenerationParams(studentId, classId, List.of(), null);
    }
}
