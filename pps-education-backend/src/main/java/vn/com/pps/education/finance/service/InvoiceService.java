package vn.com.pps.education.finance.service;

import vn.com.pps.education.notification.service.NotificationService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.academic.domain.ClassEnrollment;
import vn.com.pps.education.finance.domain.Invoice;
import vn.com.pps.education.finance.domain.InvoiceHistory;
import vn.com.pps.education.finance.domain.InvoiceItem;
import vn.com.pps.education.finance.domain.InvoiceScholarshipApplication;
import vn.com.pps.education.notification.domain.Notification;
import vn.com.pps.education.student.domain.Parent;
import vn.com.pps.education.student.domain.ParentStudent;
import vn.com.pps.education.finance.domain.Payment;
import vn.com.pps.education.finance.domain.PaymentHistory;
import vn.com.pps.education.finance.domain.Scholarship;
import vn.com.pps.education.academic.domain.SchoolClass;
import vn.com.pps.education.student.domain.Student;
import vn.com.pps.education.finance.domain.TuitionPlan;
import vn.com.pps.education.finance.domain.TuitionPlanAssignment;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.finance.dto.BankWebhookPaymentRequest;
import vn.com.pps.education.finance.dto.CancelInvoiceRequest;
import vn.com.pps.education.finance.dto.GenerateInvoicesRequest;
import vn.com.pps.education.finance.dto.InvoiceHistoryResponse;
import vn.com.pps.education.finance.dto.InvoiceItemResponse;
import vn.com.pps.education.finance.dto.InvoiceResponse;
import vn.com.pps.education.finance.dto.PaymentResponse;
import vn.com.pps.education.finance.dto.RecordManualPaymentRequest;
import vn.com.pps.education.exception.NotAuthorizedForPortalAccessException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.academic.repository.ClassEnrollmentRepository;
import vn.com.pps.education.finance.repository.InvoiceHistoryRepository;
import vn.com.pps.education.finance.repository.InvoiceItemRepository;
import vn.com.pps.education.finance.repository.InvoiceRepository;
import vn.com.pps.education.finance.repository.InvoiceScholarshipApplicationRepository;
import vn.com.pps.education.student.repository.ParentRepository;
import vn.com.pps.education.student.repository.ParentStudentRepository;
import vn.com.pps.education.finance.repository.PaymentHistoryRepository;
import vn.com.pps.education.finance.repository.PaymentRepository;
import vn.com.pps.education.finance.repository.ScholarshipRepository;
import vn.com.pps.education.system.repository.SystemSettingRepository;
import vn.com.pps.education.finance.repository.TuitionPlanAssignmentRepository;
import vn.com.pps.education.auth.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.temporal.ChronoField;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * UC-30: Xem hóa đơn & thanh toán học phí (FR-FIN-01, FR-FIN-02). Xem
 * docs/uc/phan-he-08-tai-chinh.md và
 * docs/srs.md#sơ-đồ-luồng-hoạt-động-chức-năng-thu-học-phí-và-đối-soát-qr.
 *
 * qr_code_data (Main Flow bước 3) là chuỗi placeholder tự tạo (KHÔNG đúng
 * chuẩn EMVCo VietQR thật) từ system_settings finance.bank_* — đã xác
 * nhận với user vì chưa có cấu hình tài khoản ngân hàng thật/thư viện QR
 * trong dự án.
 */
@Service
public class InvoiceService {

    private static final String BANK_NAME_KEY = "finance.bank_name";
    private static final String BANK_ACCOUNT_KEY = "finance.bank_account_number";
    private static final String BANK_BIN_KEY = "finance.bank_bin";

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final InvoiceHistoryRepository invoiceHistoryRepository;
    private final InvoiceScholarshipApplicationRepository invoiceScholarshipApplicationRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final TuitionPlanAssignmentRepository tuitionPlanAssignmentRepository;
    private final ScholarshipRepository scholarshipRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final ParentRepository parentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public InvoiceService(InvoiceRepository invoiceRepository,
                           InvoiceItemRepository invoiceItemRepository,
                           InvoiceHistoryRepository invoiceHistoryRepository,
                           InvoiceScholarshipApplicationRepository invoiceScholarshipApplicationRepository,
                           PaymentRepository paymentRepository,
                           PaymentHistoryRepository paymentHistoryRepository,
                           TuitionPlanAssignmentRepository tuitionPlanAssignmentRepository,
                           ScholarshipRepository scholarshipRepository,
                           ClassEnrollmentRepository classEnrollmentRepository,
                           ParentRepository parentRepository,
                           ParentStudentRepository parentStudentRepository,
                           SystemSettingRepository systemSettingRepository,
                           UserRepository userRepository,
                           NotificationService notificationService) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.invoiceHistoryRepository = invoiceHistoryRepository;
        this.invoiceScholarshipApplicationRepository = invoiceScholarshipApplicationRepository;
        this.paymentRepository = paymentRepository;
        this.paymentHistoryRepository = paymentHistoryRepository;
        this.tuitionPlanAssignmentRepository = tuitionPlanAssignmentRepository;
        this.scholarshipRepository = scholarshipRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.parentRepository = parentRepository;
        this.parentStudentRepository = parentStudentRepository;
        this.systemSettingRepository = systemSettingRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /**
     * Main Flow bước 1: quét class_enrollments ACTIVE của các lớp có
     * tuition_plan_assignments active, sinh invoices + invoice_items, áp
     * dụng scholarship active (A3), sinh QR, gửi thông báo. actorUserId
     * null = cron tự động sinh (created_by NULL theo SDD).
     */
    @Transactional
    public List<InvoiceResponse> generateInvoices(GenerateInvoicesRequest request, Long actorUserId) {
        List<TuitionPlanAssignment> assignments = request.classId() != null
                ? tuitionPlanAssignmentRepository.findBySchoolClassIdAndEffectiveToIsNull(request.classId()).stream().toList()
                : tuitionPlanAssignmentRepository.findByEffectiveToIsNull();

        User actor = actorUserId == null ? null : userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.invoice.userNotFound", new Object[]{actorUserId}, "Không tìm thấy user id=" + actorUserId));

        return assignments.stream()
                .filter(assignment -> assignment.getSchoolClass().getStatus() != SchoolClass.Status.CANCELLED
                        && assignment.getSchoolClass().getStatus() != SchoolClass.Status.COMPLETED)
                .flatMap(assignment -> classEnrollmentRepository
                        .findBySchoolClassIdAndStatus(assignment.getSchoolClass().getId(), ClassEnrollment.Status.ACTIVE).stream()
                        .filter(enrollment -> !invoiceRepository.existsByClassEnrollmentIdAndBillingPeriodFromAndDeletedAtIsNull(
                                enrollment.getId(), request.billingPeriodFrom()))
                        .map(enrollment -> generateOne(assignment, enrollment, request, actor)))
                .toList();
    }

    private InvoiceResponse generateOne(TuitionPlanAssignment assignment, ClassEnrollment enrollment,
                                         GenerateInvoicesRequest request, User actor) {
        TuitionPlan plan = assignment.getTuitionPlan();
        BigDecimal unitPrice = assignment.getPriceOverride() != null ? assignment.getPriceOverride() : effectivePrice(plan);
        BigDecimal quantity = plan.getPricingModel() == TuitionPlan.PricingModel.PER_SESSION && plan.getUnitCount() != null
                ? BigDecimal.valueOf(plan.getUnitCount()) : BigDecimal.ONE;
        BigDecimal subtotal = unitPrice.multiply(quantity);

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(generateInvoiceNumber(request.issueDate()));
        invoice.setStudent(enrollment.getStudent());
        invoice.setClassEnrollment(enrollment);
        invoice.setPayerParent(resolvePayerParent(enrollment.getStudent()));
        invoice.setBillingPeriodFrom(request.billingPeriodFrom());
        invoice.setBillingPeriodTo(request.billingPeriodTo());
        invoice.setIssueDate(request.issueDate());
        invoice.setDueDate(request.dueDate());
        invoice.setSubtotal(subtotal);
        invoice.setStatus(Invoice.Status.ISSUED);
        invoice.setCreatedBy(actor);
        // total_amount NOT NULL ở DB nhưng chưa tính được discount tới khi có invoice.id
        // (invoice_scholarship_applications cần FK) — set tạm = subtotal, cập nhật đúng ở save thứ 2.
        invoice.setTotalAmount(subtotal);
        invoice = invoiceRepository.save(invoice);

        InvoiceItem item = new InvoiceItem();
        item.setInvoice(invoice);
        item.setItemType(InvoiceItem.ItemType.TUITION);
        item.setDescription(plan.getName());
        item.setTuitionPlan(plan);
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        item.setAmount(subtotal);
        invoiceItemRepository.save(item);

        BigDecimal discountTotal = applyActiveScholarships(invoice, enrollment.getStudent(), subtotal, actor);
        invoice.setDiscountTotal(discountTotal);
        invoice.setTotalAmount(subtotal.subtract(discountTotal).add(invoice.getTaxAmount()));
        invoice.setQrCodeData(buildQrCodeData(invoice));
        invoice = invoiceRepository.save(invoice);

        writeInvoiceHistory(invoice, actor, InvoiceHistory.Action.CREATED);
        notifyPayer(invoice);
        return toResponse(invoice);
    }

    /** A3: áp dụng mọi scholarship ACTIVE của học sinh còn hiệu lực tại issue_date. */
    private BigDecimal applyActiveScholarships(Invoice invoice, Student student, BigDecimal subtotal, User actor) {
        BigDecimal totalDiscount = BigDecimal.ZERO;
        for (Scholarship scholarship : scholarshipRepository.findActiveForStudentOn(student.getId(), invoice.getIssueDate())) {
            if (scholarship.getApplicableScope() == Scholarship.ApplicableScope.ONE_TIME
                    && !invoiceScholarshipApplicationRepository.findByInvoiceId(invoice.getId()).isEmpty()) {
                continue;
            }
            BigDecimal discount = scholarship.getDiscountType() == Scholarship.DiscountType.PERCENTAGE
                    ? subtotal.multiply(scholarship.getDiscountValue()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                    : scholarship.getDiscountValue();
            if (scholarship.getMaxAmount() != null && discount.compareTo(scholarship.getMaxAmount()) > 0) {
                discount = scholarship.getMaxAmount();
            }
            InvoiceScholarshipApplication application = new InvoiceScholarshipApplication();
            application.setInvoice(invoice);
            application.setScholarship(scholarship);
            application.setDiscountAmount(discount);
            application.setAppliedBy(actor != null ? actor : scholarship.getApprovedBy());
            invoiceScholarshipApplicationRepository.save(application);
            totalDiscount = totalDiscount.add(discount);
        }
        return totalDiscount;
    }

    private Parent resolvePayerParent(Student student) {
        return parentStudentRepository.findByStudentId(student.getId()).stream()
                .filter(ParentStudent::isFinancialResponsible)
                .map(ParentStudent::getParent)
                .findFirst().orElse(null);
    }

    private BigDecimal effectivePrice(TuitionPlan plan) {
        return plan.getPricingModel() == TuitionPlan.PricingModel.COURSE || plan.getPricePerUnit() == null
                ? plan.getBasePrice() : plan.getPricePerUnit();
    }

    private String buildQrCodeData(Invoice invoice) {
        String bankName = settingText(BANK_NAME_KEY, "PPS Bank");
        String accountNumber = settingText(BANK_ACCOUNT_KEY, "0000000000");
        String bin = settingText(BANK_BIN_KEY, "970000");
        return "BANK:%s|BIN:%s|ACC:%s|AMOUNT:%s|REF:%s".formatted(
                bankName, bin, accountNumber, invoice.getTotalAmount(), invoice.getInvoiceNumber());
    }

    private String settingText(String key, String fallback) {
        return systemSettingRepository.findBySettingKey(key).map(s -> s.getSettingValue().asText()).orElse(fallback);
    }

    // ===================== Main Flow bước 2-3: Phụ huynh xem hóa đơn =====================

    @Transactional(readOnly = true)
    public List<InvoiceResponse> listMyInvoices(Long actorUserId) {
        Parent parent = parentOrThrow(actorUserId);
        List<Long> studentIds = parentStudentRepository.findByParentId(parent.getId()).stream()
                .map(ps -> ps.getStudent().getId()).toList();
        return studentIds.stream()
                .flatMap(studentId -> invoiceRepository.findByStudentIdAndDeletedAtIsNullOrderByIssueDateDesc(studentId).stream())
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(Long invoiceId, Long actorUserId) {
        Invoice invoice = invoiceOrThrow(invoiceId);
        requireLinkedParent(invoice.getStudent().getId(), actorUserId);
        return toResponse(invoice);
    }

    // ===================== Main Flow bước 4-7, A2: Thanh toán =====================

    /** A2: Kế toán ghi nhận thủ công (tiền mặt/chuyển khoản thông thường). */
    @Transactional
    public PaymentResponse recordManualPayment(Long invoiceId, RecordManualPaymentRequest request, Long actorUserId) {
        Invoice invoice = invoiceOrThrow(invoiceId);
        if (invoice.getStatus() == Invoice.Status.CANCELLED) {
            throw new IllegalArgumentException("Hóa đơn số=" + invoice.getInvoiceNumber() + " đã hủy, không thể ghi nhận thanh toán.");
        }
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.invoice.userNotFound", new Object[]{actorUserId}, "Không tìm thấy user id=" + actorUserId));

        Payment payment = new Payment();
        payment.setPaymentReference(generatePaymentReference());
        payment.setInvoice(invoice);
        payment.setAmount(request.amount());
        payment.setPaymentMethod(Payment.PaymentMethod.valueOf(request.paymentMethod()));
        payment.setPaidAt(request.paidAt() != null ? request.paidAt() : OffsetDateTime.now());
        payment.setReceiptNumber(request.receiptNumber());
        payment.setStatus(Payment.Status.CONFIRMED);
        payment.setConfirmedBy(actor);
        payment.setConfirmedAt(OffsetDateTime.now());
        payment = paymentRepository.save(payment);
        writePaymentHistory(payment, actor, PaymentHistory.Action.CREATED);

        applyPaymentToInvoice(invoice, request.amount(), actor);
        return toResponse(payment);
    }

    /**
     * Main Flow bước 5-6: webhook ngân hàng xác nhận giao dịch thành công
     * (A13 — tự động, không có actor người dùng). Đối chiếu qua
     * invoiceNumber (đã gắn trong qr_code_data khi hiển thị QR).
     *
     * Bổ sung ngoài SDD gốc (rà soát bảo mật 2026-09-28, đã xác nhận với người dùng) — SDD không đặc tả
     * các ràng buộc an toàn cho webhook, bổ sung tối thiểu:
     * - Idempotent theo bankTransactionId: ngân hàng gửi lại cùng 1 giao dịch (retry) -> trả lại Payment
     *   đã ghi, KHÔNG cộng tiền lần 2 (kèm UNIQUE index ở V197).
     * - Không gạch nợ vào hóa đơn đã hủy (CANCELLED) hoặc đã xóa mềm - để Kế toán xử lý thủ công.
     * - Khoá dòng hóa đơn trước khi cộng paid_amount (xem InvoiceRepository#findForUpdateByInvoiceNumber).
     * (amount > 0 kiểm tra ở BankWebhookPaymentRequest.)
     */
    @Transactional
    public PaymentResponse confirmBankWebhook(BankWebhookPaymentRequest request) {
        Optional<Payment> existing = paymentRepository.findByBankTransactionId(request.bankTransactionId());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        Invoice invoice = invoiceRepository.findForUpdateByInvoiceNumber(request.invoiceNumber())
                .filter(i -> i.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("error.invoice.notFoundByNumber", new Object[]{request.invoiceNumber()}, "Không tìm thấy hóa đơn số=" + request.invoiceNumber()));
        if (invoice.getStatus() == Invoice.Status.CANCELLED) {
            throw new IllegalArgumentException("Hóa đơn số=" + request.invoiceNumber() + " đã hủy, không thể ghi nhận thanh toán tự động.");
        }

        Payment payment = new Payment();
        payment.setPaymentReference(generatePaymentReference());
        payment.setInvoice(invoice);
        payment.setAmount(request.amount());
        payment.setPaymentMethod(Payment.PaymentMethod.QR_BANK);
        payment.setPaidAt(request.paidAt() != null ? request.paidAt() : OffsetDateTime.now());
        payment.setBankTransactionId(request.bankTransactionId());
        payment.setStatus(Payment.Status.CONFIRMED);
        payment.setConfirmedAt(OffsetDateTime.now());
        payment = paymentRepository.save(payment);
        writePaymentHistory(payment, null, PaymentHistory.Action.CREATED);

        applyPaymentToInvoice(invoice, request.amount(), null);
        return toResponse(payment);
    }

    /** Bước 6-7: đối chiếu paid_amount với total_amount, cập nhật trạng thái hóa đơn. */
    private void applyPaymentToInvoice(Invoice invoice, BigDecimal amount, User actor) {
        invoice.setPaidAmount(invoice.getPaidAmount().add(amount));
        invoice.setStatus(invoice.getPaidAmount().compareTo(invoice.getTotalAmount()) >= 0
                ? Invoice.Status.PAID : Invoice.Status.PARTIAL_PAID);
        invoiceRepository.save(invoice);
        writeInvoiceHistory(invoice, actor, InvoiceHistory.Action.UPDATED);
    }

    // ===================== A1: cron đánh dấu quá hạn =====================

    /**
     * A1: hóa đơn ISSUED/PARTIAL_PAID có due_date trước hôm nay chuyển OVERDUE, ghi lịch sử (hệ thống).
     * Gọi từ FinanceSchedulerService (cron hàng đêm). Trả về số hóa đơn đã chuyển.
     */
    @Transactional
    public int markOverdueInvoices(LocalDate today) {
        List<Invoice> overdue = invoiceRepository.findByStatusInAndDueDateBeforeAndDeletedAtIsNull(
                List.of(Invoice.Status.ISSUED, Invoice.Status.PARTIAL_PAID), today);
        for (Invoice invoice : overdue) {
            invoice.setStatus(Invoice.Status.OVERDUE);
            invoiceRepository.save(invoice);
            writeInvoiceHistory(invoice, null, InvoiceHistory.Action.UPDATED, Map.of("reason", "OVERDUE"));
        }
        return overdue.size();
    }

    // ===================== Phía Kế toán: tra cứu, hủy hóa đơn (bổ sung 2026-10-03) =====================

    /**
     * Danh sách hóa đơn cho màn Thu phí & hóa đơn (quyền finance.invoice.view). Kỳ phát hành from-to bắt
     * buộc để giới hạn số dòng; keyword khớp số hóa đơn, mã hoặc họ tên học sinh (không phân biệt hoa thường).
     */
    @Transactional(readOnly = true)
    public List<InvoiceResponse> searchInvoices(LocalDate from, LocalDate to, String status, Long siteId, Long classId,
                                                String keyword) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("Khoảng ngày phát hành không hợp lệ.");
        }
        Invoice.Status statusFilter = status == null || status.isBlank() ? null : Invoice.Status.valueOf(status);
        String needle = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return invoiceRepository.searchForStaff(from, to, statusFilter, siteId, classId).stream()
                .filter(i -> needle.isEmpty()
                        || i.getInvoiceNumber().toLowerCase(Locale.ROOT).contains(needle)
                        || i.getStudent().getStudentCode().toLowerCase(Locale.ROOT).contains(needle)
                        || i.getStudent().getUser().getFullName().toLowerCase(Locale.ROOT).contains(needle))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceForStaff(Long invoiceId) {
        return toResponse(invoiceOrThrow(invoiceId));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listPayments(Long invoiceId) {
        invoiceOrThrow(invoiceId);
        return paymentRepository.findByInvoiceId(invoiceId).stream()
                .sorted(Comparator.comparing(Payment::getPaidAt))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InvoiceHistoryResponse> listHistory(Long invoiceId) {
        invoiceOrThrow(invoiceId);
        return invoiceHistoryRepository.findByInvoiceIdOrderByCreatedAtAsc(invoiceId).stream()
                .map(h -> new InvoiceHistoryResponse(h.getId(), h.getAction().name(),
                        h.getChangedBy() == null ? null : h.getChangedBy().getId(),
                        h.getChangedBy() == null ? null : h.getChangedBy().getFullName(),
                        h.getDetails(), h.getCreatedAt()))
                .toList();
    }

    /**
     * Hủy hóa đơn phát hành sai (quyền finance.invoice.cancel, V210). Chỉ hủy được khi chưa có khoản thu
     * nào — hóa đơn đã thu tiền phải xử lý hoàn tiền riêng, không hủy để tránh lệch sổ.
     */
    @Transactional
    public InvoiceResponse cancelInvoice(Long invoiceId, CancelInvoiceRequest request, Long actorUserId) {
        Invoice invoice = invoiceOrThrow(invoiceId);
        if (invoice.getStatus() == Invoice.Status.CANCELLED) {
            throw new IllegalArgumentException("Hóa đơn số=" + invoice.getInvoiceNumber() + " đã hủy trước đó.");
        }
        if (invoice.getPaidAmount().signum() > 0) {
            throw new IllegalArgumentException("Hóa đơn số=" + invoice.getInvoiceNumber()
                    + " đã có khoản thu, không thể hủy.");
        }
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.invoice.userNotFound", new Object[]{actorUserId}, "Không tìm thấy user id=" + actorUserId));
        invoice.setStatus(Invoice.Status.CANCELLED);
        invoice = invoiceRepository.save(invoice);
        writeInvoiceHistory(invoice, actor, InvoiceHistory.Action.UPDATED, Map.of("reason", request.reason().trim()));
        return toResponse(invoice);
    }

    // ===================== Helpers =====================

    /**
     * Hóa đơn mà Phụ huynh liên kết được phép thanh toán online qua cổng QR: còn nợ và chưa hủy. Dùng bởi
     * InvoicePaymentLinkService khi sinh link/QR.
     */
    @Transactional(readOnly = true)
    public Invoice requirePayableInvoice(Long invoiceId, Long actorUserId) {
        Invoice invoice = invoiceOrThrow(invoiceId);
        requireLinkedParent(invoice.getStudent().getId(), actorUserId);
        if (invoice.getStatus() == Invoice.Status.CANCELLED || invoice.getStatus() == Invoice.Status.DRAFT) {
            throw new IllegalArgumentException("Hóa đơn số=" + invoice.getInvoiceNumber() + " không thể thanh toán ở trạng thái " + invoice.getStatus() + ".");
        }
        if (invoice.getOutstandingAmount().signum() <= 0) {
            throw new IllegalArgumentException("Hóa đơn số=" + invoice.getInvoiceNumber() + " đã thanh toán đủ.");
        }
        return invoice;
    }

    private Invoice invoiceOrThrow(Long id) {
        return invoiceRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.invoice.notFoundById", new Object[]{id}, "Không tìm thấy hóa đơn id=" + id));
    }

    private Parent parentOrThrow(Long actorUserId) {
        return parentRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new NotAuthorizedForPortalAccessException(
                        "error.notAuthorizedForPortalAccess.noParentProfile", new Object[]{},
                        "Tài khoản của bạn không có hồ sơ phụ huynh."));
    }

    /** NFR-SEC-03: Phụ huynh chỉ xem được hóa đơn của con mình (parent_student), không giới hạn chỉ payer_parent. */
    private void requireLinkedParent(Long studentId, Long actorUserId) {
        Parent parent = parentOrThrow(actorUserId);
        if (parentStudentRepository.findByParentIdAndStudentId(parent.getId(), studentId).isEmpty()) {
            throw new NotAuthorizedForPortalAccessException(
                    "error.notAuthorizedForPortalAccess.parentNotLinkedToStudent", new Object[]{},
                    "Tài khoản của bạn không phải phụ huynh liên kết với học sinh này.");
        }
    }

    private void notifyPayer(Invoice invoice) {
        Parent payer = invoice.getPayerParent();
        if (payer == null) {
            return;
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("invoiceNumber", invoice.getInvoiceNumber());
        metadata.put("amount", invoice.getTotalAmount());
        metadata.put("dueDate", invoice.getDueDate());
        notificationService.notify(payer.getUser().getId(), Notification.NotificationType.INVOICE_DUE,
                "Hóa đơn học phí mới",
                "Hóa đơn %s, số tiền %s, hạn thanh toán %s.".formatted(
                        invoice.getInvoiceNumber(), invoice.getTotalAmount(), invoice.getDueDate()),
                metadata, "INVOICE", invoice.getId(), Notification.Priority.NORMAL, null);
    }

    /**
     * actor NULL = hệ thống tự động (cron sinh hóa đơn/đánh dấu OVERDUE, webhook ngân hàng) — vẫn ghi lịch
     * sử với changed_by NULL (V210) để đối soát được mọi thay đổi, không chỉ thay đổi do người thao tác.
     */
    private void writeInvoiceHistory(Invoice invoice, User actor, InvoiceHistory.Action action) {
        writeInvoiceHistory(invoice, actor, action, Map.of());
    }

    private void writeInvoiceHistory(Invoice invoice, User actor, InvoiceHistory.Action action, Map<String, Object> extra) {
        InvoiceHistory history = new InvoiceHistory();
        history.setInvoice(invoice);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> details = snapshot(invoice);
        details.put("source", actor == null ? "SYSTEM" : "USER");
        details.putAll(extra);
        history.setDetails(details);
        invoiceHistoryRepository.save(history);
    }

    private void writePaymentHistory(Payment payment, User actor, PaymentHistory.Action action) {
        PaymentHistory history = new PaymentHistory();
        history.setPayment(payment);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> details = new HashMap<>();
        details.put("amount", payment.getAmount().toString());
        details.put("status", payment.getStatus().name());
        details.put("paymentMethod", payment.getPaymentMethod().name());
        details.put("source", actor == null ? "SYSTEM" : "USER");
        if (payment.getBankTransactionId() != null) {
            details.put("bankTransactionId", payment.getBankTransactionId());
        }
        history.setDetails(details);
        paymentHistoryRepository.save(history);
    }

    private Map<String, Object> snapshot(Invoice invoice) {
        Map<String, Object> details = new HashMap<>();
        details.put("status", invoice.getStatus().name());
        details.put("totalAmount", invoice.getTotalAmount().toString());
        details.put("paidAmount", invoice.getPaidAmount().toString());
        return details;
    }

    private String generateInvoiceNumber(LocalDate issueDate) {
        String prefix = "INV-" + Year.from(issueDate).getValue() + "-"
                + String.format("%02d", issueDate.get(ChronoField.MONTH_OF_YEAR)) + "-";
        long sequence = invoiceRepository.countByInvoiceNumberStartingWith(prefix) + 1;
        return prefix + String.format("%04d", sequence);
    }

    private String generatePaymentReference() {
        String prefix = "PAY-" + Year.now().getValue() + "-";
        long sequence = paymentRepository.countByPaymentReferenceStartingWith(prefix) + 1;
        return prefix + String.format("%06d", sequence);
    }

    private InvoiceResponse toResponse(Invoice i) {
        SchoolClass schoolClass = i.getClassEnrollment() == null ? null : i.getClassEnrollment().getSchoolClass();
        List<InvoiceItemResponse> items = invoiceItemRepository.findByInvoiceId(i.getId()).stream()
                .map(it -> new InvoiceItemResponse(it.getId(), it.getItemType().name(), it.getDescription(),
                        it.getQuantity(), it.getUnitPrice(), it.getAmount()))
                .toList();
        return new InvoiceResponse(
                i.getId(), i.getInvoiceNumber(), i.getStudent().getId(), i.getStudent().getUser().getFullName(),
                i.getStudent().getStudentCode(), i.getClassEnrollment() == null ? null : i.getClassEnrollment().getId(),
                i.getPayerParent() == null ? null : i.getPayerParent().getId(),
                i.getBillingPeriodFrom(), i.getBillingPeriodTo(), i.getIssueDate(), i.getDueDate(),
                i.getSubtotal(), i.getDiscountTotal(), i.getTaxAmount(), i.getTotalAmount(), i.getPaidAmount(),
                i.getOutstandingAmount(), i.getStatus().name(), i.getQrCodeData(), items,
                schoolClass == null ? null : schoolClass.getId(), schoolClass == null ? null : schoolClass.getName(),
                schoolClass == null || schoolClass.getSite() == null ? null : schoolClass.getSite().getId(),
                schoolClass == null || schoolClass.getSite() == null ? null : schoolClass.getSite().getName());
    }

    private PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(
                p.getId(), p.getPaymentReference(), p.getInvoice().getId(), p.getAmount(), p.getPaymentMethod().name(),
                p.getPaidAt(), p.getBankTransactionId(), p.getReceiptNumber(), p.getStatus().name(),
                p.getConfirmedBy() == null ? null : p.getConfirmedBy().getId(), p.getConfirmedAt(),
                p.getConfirmedBy() == null ? null : p.getConfirmedBy().getFullName());
    }
}
