package vn.com.pps.education.dto;

import java.math.BigDecimal;

/** UC-53: Overall/Level GV đã tính sẵn — hệ thống chỉ lưu, không tự tính lại. */
public record EnterGradePeriodResultRequest(
        BigDecimal overallScore,
        String scaleType,
        String level
) {}
