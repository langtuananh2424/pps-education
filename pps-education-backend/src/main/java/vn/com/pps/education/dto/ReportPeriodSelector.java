package vn.com.pps.education.dto;

/**
 * UC-68: 1 "cột kỳ" người dùng chọn khi xuất báo cáo TRANSCRIPT/GRADE_REPORT
 * — xuất linh hoạt theo giữa kỳ, cuối kỳ, cả kỳ (2 selector: giữa+cuối
 * cùng 1 academicTermId), hay cả năm học (nhiều selector, mỗi kỳ 1 label
 * riêng). {@code label} do actor tự đặt (VD "S1", "S2", "MID1") — PHẢI
 * khớp đúng hậu tố mà người tạo mẫu đã dùng khi cấu hình field mapping ở
 * UC-67 (VD placeholder {@code [READING_S1]} cần label "S1"), không do hệ
 * thống áp đặt quy ước cố định.
 */
public record ReportPeriodSelector(
        String label,
        Long academicTermId,
        String evaluationType
) {}
