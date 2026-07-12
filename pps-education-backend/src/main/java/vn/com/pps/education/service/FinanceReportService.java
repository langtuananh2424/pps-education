package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Invoice;
import vn.com.pps.education.domain.OperatingExpense;
import vn.com.pps.education.domain.Payment;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.dto.ChainFinancialReportResponse;
import vn.com.pps.education.dto.FinancialReportResponse;
import vn.com.pps.education.exception.NotExecutiveException;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.repository.InvoiceRepository;
import vn.com.pps.education.repository.OperatingExpenseRepository;
import vn.com.pps.education.repository.PaymentRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRoleRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * UC-32: Xem báo cáo tài chính (FR-FIN-04). Xem
 * docs/uc/phan-he-08-tai-chinh.md. Precondition chỉ nêu actor
 * (Quản lý điểm trường / Ban giám đốc), không nêu tên quyền cụ thể — dùng
 * role-check trong Service như GradeService (UC-20), không permission-based
 * @PreAuthorize.
 */
@Service
public class FinanceReportService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final OperatingExpenseRepository operatingExpenseRepository;
    private final SiteRepository siteRepository;
    private final SiteManagerRepository siteManagerRepository;
    private final UserRoleRepository userRoleRepository;

    public FinanceReportService(InvoiceRepository invoiceRepository,
                                 PaymentRepository paymentRepository,
                                 OperatingExpenseRepository operatingExpenseRepository,
                                 SiteRepository siteRepository,
                                 SiteManagerRepository siteManagerRepository,
                                 UserRoleRepository userRoleRepository) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.operatingExpenseRepository = operatingExpenseRepository;
        this.siteRepository = siteRepository;
        this.siteManagerRepository = siteManagerRepository;
        this.userRoleRepository = userRoleRepository;
    }

    /** Main Flow bước 2: Quản lý điểm trường chỉ xem (các) điểm trường mình phụ trách. */
    @Transactional(readOnly = true)
    public List<FinancialReportResponse> getMySiteReports(LocalDate from, LocalDate to, Long actorUserId) {
        List<Long> siteIds = siteManagerRepository
                .findByUserIdAndRoleTypeAndAssignedToIsNull(actorUserId, SiteManager.RoleType.SITE_MANAGER).stream()
                .map(sm -> sm.getSite().getId()).toList();
        if (siteIds.isEmpty()) {
            throw new NotSiteManagerForSiteException(
                    "Tài khoản id=" + actorUserId + " không được gán phụ trách điểm trường nào.");
        }
        return siteIds.stream().map(siteId -> buildSiteReport(siteId, from, to)).toList();
    }

    /** Main Flow bước 3: Ban giám đốc xem báo cáo tổng hợp toàn chuỗi, chi tiết theo từng điểm trường. */
    @Transactional(readOnly = true)
    public ChainFinancialReportResponse getChainReport(LocalDate from, LocalDate to, Long actorUserId) {
        requireExecutive(actorUserId);
        List<FinancialReportResponse> bySite = siteRepository.findAll().stream()
                .map(site -> buildSiteReport(site.getId(), from, to))
                .toList();
        BigDecimal totalRevenue = bySite.stream().map(FinancialReportResponse::totalRevenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOutstanding = bySite.stream().map(FinancialReportResponse::totalOutstanding).reduce(BigDecimal.ZERO, BigDecimal::add);
        // Chi dùng chung nhiều điểm trường (site_id NULL, UC-31 A1) không gắn được vào 1 site cụ thể - cộng riêng vào tổng toàn chuỗi.
        BigDecimal siteExpense = bySite.stream().map(FinancialReportResponse::totalExpense).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sharedExpense = operatingExpenseRepository.findByExpenseDateBetween(from, to).stream()
                .filter(e -> e.getSite() == null)
                .map(OperatingExpense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ChainFinancialReportResponse(from, to, totalRevenue, siteExpense.add(sharedExpense), totalOutstanding, bySite);
    }

    private FinancialReportResponse buildSiteReport(Long siteId, LocalDate from, LocalDate to) {
        Site site = siteRepository.findById(siteId).orElseThrow();
        OffsetDateTime fromDateTime = from.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime toDateTime = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();

        BigDecimal revenue = paymentRepository.findBySiteIdAndStatusAndPaidAtBetween(siteId, Payment.Status.CONFIRMED, fromDateTime, toDateTime)
                .stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expense = operatingExpenseRepository.findBySiteIdAndExpenseDateBetween(siteId, from, to)
                .stream().map(OperatingExpense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = invoiceRepository.findBySiteIdAndIssueDateBetween(siteId, from, to)
                .stream().map(Invoice::getOutstandingAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new FinancialReportResponse(siteId, site.getName(), from, to, revenue, expense, outstanding);
    }

    private void requireExecutive(Long actorUserId) {
        boolean isExecutive = userRoleRepository.findByUserId(actorUserId).stream()
                .map(ur -> ur.getRole().getCode())
                .collect(Collectors.toSet())
                .contains("EXECUTIVE");
        if (!isExecutive) {
            throw new NotExecutiveException("Tài khoản id=" + actorUserId + " không có role EXECUTIVE để xem báo cáo tổng hợp toàn chuỗi.");
        }
    }
}
