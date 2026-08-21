package vn.com.pps.education.dto;

import java.util.Map;

/** 1 hàng (1 lớp) của lưới "số tiết thực tế" (bổ sung ngoài SDD gốc, xác nhận 2026-08-20). */
public record ActualPeriodsGridRow(Long classId, String classCode, String className,
                                    Map<String, Long> actualPeriodsByColumnKey) {
}
