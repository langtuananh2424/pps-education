package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.Invoice;
import vn.com.pps.education.domain.InvoiceHistory;
import vn.com.pps.education.domain.InvoiceItem;
import vn.com.pps.education.domain.InvoiceScholarshipApplication;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.Parent;
import vn.com.pps.education.domain.ParentStudent;
import vn.com.pps.education.domain.Payment;
import vn.com.pps.education.domain.PaymentHistory;
import vn.com.pps.education.domain.Scholarship;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.TuitionPlan;
import vn.com.pps.education.domain.TuitionPlanAssignment;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.BankWebhookPaymentRequest;
import vn.com.pps.education.dto.GenerateInvoicesRequest;
import vn.com.pps.education.dto.InvoiceItemResponse;
import vn.com.pps.education.dto.InvoiceResponse;
import vn.com.pps.education.dto.PaymentResponse;
import vn.com.pps.education.dto.RecordManualPaymentRequest;
import vn.com.pps.education.exception.NotAuthorizedForPortalAccessException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.InvoiceHistoryRepository;
import vn.com.pps.education.repository.InvoiceItemRepository;
import vn.com.pps.education.repository.InvoiceRepository;
import vn.com.pps.education.repository.InvoiceScholarshipApplicationRepository;
import vn.com.pps.education.repository.ParentRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.PaymentHistoryRepository;
import vn.com.pps.education.repository.PaymentRepository;
import vn.com.pps.education.repository.ScholarshipRepository;
import vn.com.pps.education.repository.SystemSettingRepository;
import vn.com.pps.education.repository.TuitionPlanAssignmentRepository;
import vn.com.pps.education.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.temporal.ChronoField;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user id=" + actorUserId));

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
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user id=" + actorUserId));

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
     */
    @Transactional
    public PaymentResponse confirmBankWebhook(BankWebhookPaymentRequest request) {
        Invoice invoice = invoiceRepository.findByInvoiceNumber(request.invoiceNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn số=" + request.invoiceNumber()));

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

    // ===================== Helpers =====================

    private Invoice invoiceOrThrow(Long id) {
        return invoiceRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn id=" + id));
    }

    private Parent parentOrThrow(Long actorUserId) {
        return parentRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new NotAuthorizedForPortalAccessException(
                        "Tài khoản id=" + actorUserId + " không có hồ sơ phụ huynh."));
    }

    /** NFR-SEC-03: Phụ huynh chỉ xem được hóa đơn của con mình (parent_student), không giới hạn chỉ payer_parent. */
    private void requireLinkedParent(Long studentId, Long actorUserId) {
        Parent parent = parentOrThrow(actorUserId);
        if (parentStudentRepository.findByParentIdAndStudentId(parent.getId(), studentId).isEmpty()) {
            throw new NotAuthorizedForPortalAccessException(
                    "Tài khoản id=" + actorUserId + " không phải phụ huynh liên kết với học sinh id=" + studentId + ".");
        }
    }

    private void notifyPayer(Invoice invoice) {
        Parent payer = invoice.getPayerParent();
        if (payer == null) {
            return;
        }
        notificationService.notify(payer.getUser().getId(), Notification.NotificationType.INVOICE_DUE,
                "Hóa đơn học phí mới",
                "Hóa đơn %s, số tiền %s, hạn thanh toán %s.".formatted(
                        invoice.getInvoiceNumber(), invoice.getTotalAmount(), invoice.getDueDate()));
    }

    private void writeInvoiceHistory(Invoice invoice, User actor, InvoiceHistory.Action action) {
        if (actor == null) {
            return;
        }
        InvoiceHistory history = new InvoiceHistory();
        history.setInvoice(invoice);
        history.setChangedBy(actor);
        history.setAction(action);
        history.setDetails(snapshot(invoice));
        invoiceHistoryRepository.save(history);
    }

    private void writePaymentHistory(Payment payment, User actor, PaymentHistory.Action action) {
        if (actor == null) {
            return;
        }
        PaymentHistory history = new PaymentHistory();
        history.setPayment(payment);
        history.setChangedBy(actor);
        history.setAction(action);
        history.setDetails(Map.of("amount", payment.getAmount().toString(), "status", payment.getStatus().name()));
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
                i.getOutstandingAmount(), i.getStatus().name(), i.getQrCodeData(), items);
    }

    private PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(
                p.getId(), p.getPaymentReference(), p.getInvoice().getId(), p.getAmount(), p.getPaymentMethod().name(),
                p.getPaidAt(), p.getBankTransactionId(), p.getReceiptNumber(), p.getStatus().name(),
                p.getConfirmedBy() == null ? null : p.getConfirmedBy().getId(), p.getConfirmedAt());
    }
}
