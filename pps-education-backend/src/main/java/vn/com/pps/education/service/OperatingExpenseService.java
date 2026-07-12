package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ExpenseCategory;
import vn.com.pps.education.domain.OperatingExpense;
import vn.com.pps.education.domain.OperatingExpenseHistory;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateOperatingExpenseRequest;
import vn.com.pps.education.dto.OperatingExpenseResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ExpenseCategoryRepository;
import vn.com.pps.education.repository.OperatingExpenseHistoryRepository;
import vn.com.pps.education.repository.OperatingExpenseRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;

/**
 * UC-31: Ghi nhận chi vận hành (FR-FIN-03). Xem
 * docs/uc/phan-he-08-tai-chinh.md. Main Flow chỉ tới bước "lưu bản ghi"
 * (status mặc định RECORDED) — không có bước duyệt nào được mô tả trong
 * Main/Alternate Flow dù cột approved_by/status APPROVED/REJECTED tồn tại
 * trong SDD, nên KHÔNG tự viết endpoint duyệt (business-fidelity: không
 * bịa luồng không có trong UC).
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

        OperatingExpenseHistory history = new OperatingExpenseHistory();
        history.setExpense(expense);
        history.setChangedBy(actor);
        history.setAction(OperatingExpenseHistory.Action.CREATED);
        history.setDetails(Map.of("amount", expense.getAmount().toString(), "status", expense.getStatus().name()));
        operatingExpenseHistoryRepository.save(history);

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

    private OperatingExpenseResponse toResponse(OperatingExpense e) {
        return new OperatingExpenseResponse(
                e.getId(), e.getExpenseNumber(), e.getExpenseCategory().getCode(), e.getExpenseCategory().getName(),
                e.getSite() == null ? null : e.getSite().getId(), e.getExpenseDate(), e.getAmount(), e.getDescription(),
                e.getPaymentMethod().name(), e.getSupplierName(), e.getReceiptNumber(), e.getFileUrl(),
                e.getStatus().name(), e.getRecordedBy().getId());
    }
}
