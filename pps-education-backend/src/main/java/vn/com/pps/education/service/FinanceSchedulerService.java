package vn.com.pps.education.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Invoice;
import vn.com.pps.education.dto.GenerateInvoicesRequest;
import vn.com.pps.education.repository.InvoiceRepository;
import vn.com.pps.education.repository.SystemSettingRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * UC-30 A1 (cron nightly đánh dấu OVERDUE, độc lập với các bước còn lại)
 * + Main Flow bước 1 (tự động sinh hóa đơn định kỳ — đã xác nhận với
 * user: cron hàng đêm kiểm tra đúng ngày trong tháng đọc từ system_settings
 * finance.invoice_generation_day_of_month, cộng thêm endpoint thủ công
 * trong InvoiceController để Kế toán tự sinh bổ sung khi cần).
 */
@Service
public class FinanceSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(FinanceSchedulerService.class);
    private static final String GENERATION_DAY_KEY = "finance.invoice_generation_day_of_month";
    private static final String DUE_DAYS_KEY = "finance.invoice_due_days";
    private static final List<Invoice.Status> OPEN_STATUSES = List.of(Invoice.Status.ISSUED, Invoice.Status.PARTIAL_PAID);

    private final InvoiceRepository invoiceRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final InvoiceService invoiceService;

    public FinanceSchedulerService(InvoiceRepository invoiceRepository,
                                    SystemSettingRepository systemSettingRepository,
                                    InvoiceService invoiceService) {
        this.invoiceRepository = invoiceRepository;
        this.systemSettingRepository = systemSettingRepository;
        this.invoiceService = invoiceService;
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void runNightlyJob() {
        markOverdue(LocalDate.now());
        generateMonthlyInvoicesIfDue(LocalDate.now());
    }

    /** A1: invoices ISSUED/PARTIAL_PAID có due_date < hôm nay → OVERDUE. */
    private void markOverdue(LocalDate today) {
        List<Invoice> overdue = invoiceRepository.findByStatusInAndDueDateBeforeAndDeletedAtIsNull(OPEN_STATUSES, today);
        for (Invoice invoice : overdue) {
            invoice.setStatus(Invoice.Status.OVERDUE);
        }
        invoiceRepository.saveAll(overdue);
        if (!overdue.isEmpty()) {
            log.info("FinanceSchedulerService: đánh dấu OVERDUE {} hóa đơn quá hạn.", overdue.size());
        }
    }

    /** Main Flow bước 1: chỉ sinh hóa đơn đúng ngày cấu hình trong tháng, cho toàn bộ lớp (classId=null). */
    private void generateMonthlyInvoicesIfDue(LocalDate today) {
        int generationDay = systemSettingRepository.findBySettingKey(GENERATION_DAY_KEY)
                .map(s -> s.getSettingValue().asInt()).orElse(1);
        if (today.getDayOfMonth() != generationDay) {
            return;
        }
        int dueDays = systemSettingRepository.findBySettingKey(DUE_DAYS_KEY).map(s -> s.getSettingValue().asInt()).orElse(15);
        YearMonth month = YearMonth.from(today);
        GenerateInvoicesRequest request = new GenerateInvoicesRequest(
                null, month.atDay(1), month.atEndOfMonth(), today, today.plusDays(dueDays));
        int count = invoiceService.generateInvoices(request, null).size();
        if (count > 0) {
            log.info("FinanceSchedulerService: tự động sinh {} hóa đơn định kỳ cho kỳ {}.", count, month);
        }
    }
}
