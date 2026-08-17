package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ExpenseCategory;
import vn.com.pps.education.domain.OperatingExpense;
import vn.com.pps.education.domain.OperatingExpenseHistory;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateOperatingExpenseRequest;
import vn.com.pps.education.dto.DecideOperatingExpenseRequest;
import vn.com.pps.education.dto.OperatingExpenseResponse;
import vn.com.pps.education.exception.OperatingExpenseAlreadyDecidedException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ExpenseCategoryRepository;
import vn.com.pps.education.repository.OperatingExpenseHistoryRepository;
import vn.com.pps.education.repository.OperatingExpenseRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-31: Ghi nhận chi vận hành (FR-FIN-03). Xem
 * docs/uc/phan-he-08-tai-chinh.md. Main Flow chỉ tới bước "lưu bản ghi"
 * (status mặc định RECORDED) — không mô tả bước duyệt.
 *
 * Bổ sung (V30, đã xác nhận với user): SRS mô tả actor "Ban giám đốc"
 * "phê duyệt các quyết định quan trọng có tính chiến lược (chi phí lớn...)"
 * — khớp với status APPROVED/REJECTED/approved_by đã có sẵn trong SDD gốc
 * nhưng chưa từng được dùng. Quyền duyệt riêng biệt
 * finance.expense.approve (EXECUTIVE), khác finance.manage (STAFF bộ phận
 * Kế toán — chỉ ghi nhận). rejection_reason là cột mới (V30) vì schema
 * gốc không có chỗ lưu lý do từ chối.
 */
@Service
public class OperatingExpenseService {

    private final OperatingExpenseRepository operatingExpenseRepository;
    private final OperatingExpenseHistoryRepository operatingExpenseHistoryRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final SiteRepository siteRepository;
    private final UserRepository userRepository;

    public OperatingExpenseService(OperatingExpenseRepository operatingExpenseRepository,
                                    OperatingExpenseHistoryRepository operatingExpenseHistoryRepository,
                                    ExpenseCategoryRepository expenseCategoryRepository,
                                    SiteRepository siteRepository,
                                    UserRepository userRepository) {
        this.operatingExpenseRepository = operatingExpenseRepository;
        this.operatingExpenseHistoryRepository = operatingExpenseHistoryRepository;
        this.expenseCategoryRepository = expenseCategoryRepository;
        this.siteRepository = siteRepository;
        this.userRepository = userRepository;
    }

    /** Main Flow bước 1-4. A1: siteId để trống = chi dùng chung nhiều điểm trường. */
    @Transactional
    public OperatingExpenseResponse create(CreateOperatingExpenseRequest request, Long actorUserId) {
        ExpenseCategory category = expenseCategoryRepository.findByCode(request.expenseCategoryCode())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại chi code=" + request.expenseCategoryCode()));
        Site site = request.siteId() == null ? null : siteRepository.findById(request.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm trường id=" + request.siteId()));
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user id=" + actorUserId));

        OperatingExpense expense = new OperatingExpense();
        expense.setExpenseNumber(generateExpenseNumber());
        expense.setExpenseCategory(category);
        expense.setSite(site);
        expense.setExpenseDate(request.expenseDate());
        expense.setAmount(request.amount());
        expense.setDescription(request.description());
        expense.setPaymentMethod(OperatingExpense.PaymentMethod.valueOf(request.paymentMethod()));
        expense.setSupplierName(request.supplierName());
        expense.setReceiptNumber(request.receiptNumber());
        expense.setFileUrl(request.fileUrl());
        expense.setRecordedBy(actor);
        expense = operatingExpenseRepository.save(expense);

        writeHistory(expense, actor, OperatingExpenseHistory.Action.CREATED);
        return toResponse(expense);
    }

    /** UC-31 bổ sung: Ban giám đốc duyệt/từ chối 1 khoản chi đang RECORDED. */
    @Transactional
    public OperatingExpenseResponse decide(Long id, DecideOperatingExpenseRequest request, Long actorUserId) {
        OperatingExpense expense = getExpenseOrThrow(id);
        if (expense.getStatus() != OperatingExpense.Status.RECORDED) {
            throw new OperatingExpenseAlreadyDecidedException(
                    "Khoản chi này đã được quyết định (" + expense.getStatus() + "), không thể duyệt lại.");
        }
        OperatingExpense.Status decision = OperatingExpense.Status.valueOf(request.decision());
        if (decision != OperatingExpense.Status.APPROVED && decision != OperatingExpense.Status.REJECTED) {
            throw new IllegalArgumentException("decision chỉ chấp nhận APPROVED hoặc REJECTED.");
        }
        if (decision == OperatingExpense.Status.REJECTED
                && (request.rejectionReason() == null || request.rejectionReason().isBlank())) {
            throw new IllegalArgumentException("rejectionReason bắt buộc khi từ chối.");
        }
        User actor = getUserOrThrow(actorUserId);

        expense.setStatus(decision);
        expense.setApprovedBy(actor);
        expense.setRejectionReason(decision == OperatingExpense.Status.REJECTED ? request.rejectionReason() : null);
        expense.setUpdatedAt(OffsetDateTime.now());
        expense = operatingExpenseRepository.save(expense);

        writeHistory(expense, actor, OperatingExpenseHistory.Action.UPDATED);
        return toResponse(expense);
    }

    @Transactional(readOnly = true)
    public List<OperatingExpenseResponse> listBySiteAndPeriod(Long siteId, LocalDate from, LocalDate to) {
        List<OperatingExpense> expenses = siteId == null
                ? operatingExpenseRepository.findByExpenseDateBetween(from, to)
                : operatingExpenseRepository.findBySiteIdAndExpenseDateBetween(siteId, from, to);
        return expenses.stream().map(this::toResponse).toList();
    }

    private String generateExpenseNumber() {
        String prefix = "EXP-" + Year.now().getValue() + "-";
        long sequence = operatingExpenseRepository.countByExpenseNumberStartingWith(prefix) + 1;
        return prefix + String.format("%04d", sequence);
    }

    private void writeHistory(OperatingExpense expense, User actor, OperatingExpenseHistory.Action action) {
        OperatingExpenseHistory history = new OperatingExpenseHistory();
        history.setExpense(expense);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> details = new HashMap<>();
        details.put("amount", expense.getAmount().toString());
        details.put("status", expense.getStatus().name());
        if (expense.getRejectionReason() != null) {
            details.put("rejectionReason", expense.getRejectionReason());
        }
        history.setDetails(details);
        operatingExpenseHistoryRepository.save(history);
    }

    private OperatingExpense getExpenseOrThrow(Long id) {
        return operatingExpenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khoản chi vận hành id=" + id));
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user id=" + userId));
    }

    private OperatingExpenseResponse toResponse(OperatingExpense e) {
        return new OperatingExpenseResponse(
                e.getId(), e.getExpenseNumber(), e.getExpenseCategory().getCode(), e.getExpenseCategory().getName(),
                e.getSite() == null ? null : e.getSite().getId(), e.getExpenseDate(), e.getAmount(), e.getDescription(),
                e.getPaymentMethod().name(), e.getSupplierName(), e.getReceiptNumber(), e.getFileUrl(),
                e.getStatus().name(), e.getRecordedBy().getId(),
                e.getApprovedBy() == null ? null : e.getApprovedBy().getId(), e.getRejectionReason());
    }
}
