package vn.com.pps.education.dto;

import java.util.List;

/**
 * UC-68 bước 1-2: chọn mẫu + phạm vi xuất.
 * <ul>
 *   <li>scope="SINGLE": cần studentId (+ classId/periods nếu loại báo cáo
 *       cần dữ liệu theo lớp/kỳ, VD TRANSCRIPT).</li>
 *   <li>scope="BULK_CLASS": cần classId — toàn bộ học sinh ACTIVE của lớp,
 *       gộp 1 file ZIP.</li>
 *   <li>scope="CLASS_SESSION": cần classSessionId — 1 tài liệu duy nhất
 *       cho cả buổi học (VD DAILY_REPORT, dùng bảng động liệt kê học
 *       sinh), không phải 1 tài liệu/học sinh.</li>
 * </ul>
 */
public record GenerateReportRequest(
        Long templateId,
        String scope,
        Long studentId,
        Long classId,
        List<ReportPeriodSelector> periods,
        Long classSessionId,
        String outputFormat
) {
    public GenerateReportRequest(Long templateId, String scope, Long studentId, Long classId,
                                 List<ReportPeriodSelector> periods, Long classSessionId) {
        this(templateId, scope, studentId, classId, periods, classSessionId, null);
    }
}
