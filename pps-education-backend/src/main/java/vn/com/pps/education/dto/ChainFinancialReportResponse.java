package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** UC-32 Main Flow bước 3: báo cáo tổng hợp toàn chuỗi cho Ban giám đốc, chi tiết theo từng điểm trường. */
public record ChainFinancialReportResponse(
        LocalDate periodFrom,
        LocalDate periodTo,
        BigDecimal totalRevenue,
        BigDecimal totalExpense,
        BigDecimal totalOutstanding,
        List<FinancialReportResponse> bySite
) {}
