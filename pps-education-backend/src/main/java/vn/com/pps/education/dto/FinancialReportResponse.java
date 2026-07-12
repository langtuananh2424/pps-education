package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** UC-32 Main Flow bước 2: báo cáo Thu/Chi/Công nợ của 1 điểm trường trong 1 kỳ. */
public record FinancialReportResponse(
        Long siteId,
        String siteName,
        LocalDate periodFrom,
        LocalDate periodTo,
        BigDecimal totalRevenue,
        BigDecimal totalExpense,
        BigDecimal totalOutstanding
) {}
