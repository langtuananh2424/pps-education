package vn.com.pps.education.dto;

import java.math.BigDecimal;

/**
 * UC-19 Postcondition: "Điểm trung bình học phần được tính toán tự động,
 * hiển thị tạm thời cho Giáo viên". Không có công thức tổng hợp nào khác
 * được đặc tả ngoài weight_in_period của từng thành phần — tính trung
 * bình có trọng số trên các thành phần ĐÃ có điểm (componentsEntered/
 * componentsTotal cho biết mức độ đầy đủ dữ liệu), average=null nếu chưa
 * có thành phần nào được nhập.
 */
public record PeriodAverageResponse(
        Long classId,
        Long studentId,
        Long gradePeriodId,
        BigDecimal average,
        int componentsEntered,
        int componentsTotal
) {}
