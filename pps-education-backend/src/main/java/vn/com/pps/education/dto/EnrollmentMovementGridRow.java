package vn.com.pps.education.dto;

import java.util.Map;

/**
 * 1 hàng (1 lớp) của lưới "biến động học sinh" (bổ sung ngoài SDD gốc, xác
 * nhận 2026-08-20) — headcountByColumnKey là sĩ số CUỐI đoạn (closingHeadcount,
 * cùng công thức computeRow) của lớp này tại từng cột (tháng/kỳ/năm), tra
 * theo key của EnrollmentMovementGridColumn tương ứng.
 */
public record EnrollmentMovementGridRow(Long classId, String classCode, String className,
                                         Map<String, Integer> headcountByColumnKey) {
}
