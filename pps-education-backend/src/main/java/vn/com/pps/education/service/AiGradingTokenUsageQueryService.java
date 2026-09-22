package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.dto.AiTokenUsageSummaryResponse;
import vn.com.pps.education.repository.AiGradingTokenUsageRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * V192 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22) — đọc số liệu chi phí token AI cho
 * trang Quản trị hệ thống → Sử dụng token AI. Chỉ ĐỌC và gộp số, không có nghiệp vụ chấm bài nào ở đây —
 * tách khỏi {@link AiGradingTokenUsageRecorder} (chỉ GHI) theo nguyên tắc S trong .claude/rules/solid.md:
 * đổi cách hiển thị báo cáo không được đụng tới đường ghi đang chạy trong luồng chấm của học sinh.
 */
@Service
public class AiGradingTokenUsageQueryService {

    /**
     * Ngày trên giao diện là ngày theo giờ Việt Nam, còn {@code created_at} lưu TIMESTAMPTZ — quy đổi ở
     * đây để "hôm nay" trên trang đúng là 00:00-24:00 giờ VN, không lệch 7 tiếng sang mốc UTC.
     */
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AiGradingTokenUsageRepository repository;

    public AiGradingTokenUsageQueryService(AiGradingTokenUsageRepository repository) {
        this.repository = repository;
    }

    /**
     * @param fromDate ngày bắt đầu (bao gồm), {@code null} = 30 ngày trước hôm nay.
     * @param toDate   ngày kết thúc (BAO GỒM cả ngày này — cộng thêm 1 ngày khi so sánh), {@code null} = hôm nay.
     */
    @Transactional(readOnly = true)
    public AiTokenUsageSummaryResponse summarize(LocalDate fromDate, LocalDate toDate) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate resolvedFrom = fromDate == null ? today.minusDays(30) : fromDate;
        LocalDate resolvedTo = toDate == null ? today : toDate;
        OffsetDateTime from = resolvedFrom.atStartOfDay(ZONE).toOffsetDateTime();
        // Cộng 1 ngày + dùng "< to" thay vì "<= to" để lấy TRỌN ngày kết thúc, không bỏ sót lượt chấm
        // xảy ra sau 00:00:00 của chính ngày đó.
        OffsetDateTime to = resolvedTo.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime();

        List<AiGradingTokenUsageRepository.StepUsageRow> steps = repository.aggregateByStep(from, to);
        List<AiGradingTokenUsageRepository.StudentUsageRow> students = repository.aggregateByStudent(from, to);
        List<AiGradingTokenUsageRepository.AssignmentUsageRow> assignments = repository.aggregateByAssignment(from, to);

        // Tổng cộng từ bảng "theo bước" vì đó là chiều DUY NHẤT phủ hết mọi dòng (mọi dòng đều có step,
        // trong khi student/assignment có thể NULL với luồng chấm Writing UC-40/41 và lượt bị loại).
        long callCount = steps.stream().mapToLong(AiGradingTokenUsageRepository.StepUsageRow::getCallCount).sum();
        long attributedCalls = students.stream().mapToLong(AiGradingTokenUsageRepository.StudentUsageRow::getCallCount).sum();

        AiTokenUsageSummaryResponse.Totals totals = new AiTokenUsageSummaryResponse.Totals(
                callCount,
                steps.stream().mapToLong(AiGradingTokenUsageRepository.StepUsageRow::getPromptTokens).sum(),
                steps.stream().mapToLong(AiGradingTokenUsageRepository.StepUsageRow::getCachedTokens).sum(),
                steps.stream().mapToLong(AiGradingTokenUsageRepository.StepUsageRow::getCompletionTokens).sum(),
                steps.stream().mapToLong(AiGradingTokenUsageRepository.StepUsageRow::getReasoningTokens).sum(),
                repository.countRejected(from, to),
                callCount - attributedCalls);

        return new AiTokenUsageSummaryResponse(totals,
                steps.stream().map(r -> new AiTokenUsageSummaryResponse.ByStep(r.getStep(), r.getCallCount(),
                        r.getPromptTokens(), r.getCachedTokens(), r.getCompletionTokens(), r.getReasoningTokens(),
                        Math.round(r.getAvgElapsedMs()))).toList(),
                students.stream().map(r -> new AiTokenUsageSummaryResponse.ByStudent(r.getStudentId(),
                        r.getStudentName(), r.getCallCount(), r.getPromptTokens(), r.getCachedTokens(),
                        r.getCompletionTokens(), r.getReasoningTokens())).toList(),
                assignments.stream().map(r -> new AiTokenUsageSummaryResponse.ByAssignment(r.getAssignmentId(),
                        r.getAssignmentName(), r.getCallCount(), r.getPromptTokens(), r.getCachedTokens(),
                        r.getCompletionTokens(), r.getReasoningTokens())).toList());
    }
}
