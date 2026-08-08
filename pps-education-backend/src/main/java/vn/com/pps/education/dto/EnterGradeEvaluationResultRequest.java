package vn.com.pps.education.dto;

import java.math.BigDecimal;

/**
 * UC-53: Overall/Level GV đã tính sẵn — hệ thống chỉ lưu, không tự tính
 * lại. V95 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): thêm
 * comment ("Nhận xét") + note ("Ghi chú") — tích hợp nhận xét kỳ vào sổ
 * điểm, thay EnterGradePeriodResultRequest. V100 (bổ sung): thêm
 * disclaimer ("Lưu ý") — thông tin bổ sung đặc thù cho kỳ.
 */
public record EnterGradeEvaluationResultRequest(
        BigDecimal overallScore,
        String scaleType,
        String level,
        String comment,
        String note,
        String disclaimer
) {}
