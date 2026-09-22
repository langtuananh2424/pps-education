package vn.com.pps.education.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.common.AiTokenUsage;
import vn.com.pps.education.domain.AiGradingTokenUsage;
import vn.com.pps.education.domain.ReviewVideoAssignment;
import vn.com.pps.education.domain.ReviewVideoQuestion;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.repository.AiGradingTokenUsageRepository;

/**
 * V192 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22) — ghi 1 dòng chi phí token cho mỗi
 * lệnh gọi AI, phục vụ trang Quản trị hệ thống → Sử dụng token AI.
 *
 * <h3>Vì sao là bean RIÊNG chứ không phải vài dòng trong {@link ReflexSequentialGradingService}</h3>
 * Vì {@link Propagation#REQUIRES_NEW}: luồng nộp ghi âm ném {@link vn.com.pps.education.exception.ReflexAudioRejectedException}
 * (bản ghi không đọc được / nói khác bài viết) làm ROLLBACK cả giao dịch chấm — nhưng lượt phiên âm mù
 * TRƯỚC đó đã gọi AI thật và đã tốn tiền thật. Ghi cùng giao dịch thì chi phí đó bị rollback mất, trang
 * quản trị sẽ báo thấp hơn hoá đơn đúng ở những ca lỗi mà người ta cần nhìn nhất. Giao dịch riêng giữ
 * lại dòng chi phí kể cả khi việc chấm bị huỷ.
 *
 * REQUIRES_NEW chỉ có hiệu lực khi gọi QUA proxy Spring từ bean khác — đó cũng là lý do không gộp
 * phương thức này vào chính service chấm (tự gọi trong cùng class sẽ bỏ qua proxy, mất luôn tác dụng).
 *
 * Lỗi ghi chi phí KHÔNG bao giờ được phép làm hỏng việc chấm bài của học sinh — mọi ngoại lệ bị nuốt và
 * chỉ ghi log.
 */
@Service
public class AiGradingTokenUsageRecorder implements AiUsageSink {

    private static final Logger log = LoggerFactory.getLogger(AiGradingTokenUsageRecorder.class);

    private final AiGradingTokenUsageRepository repository;
    private final AiTokenUsageStream stream;

    public AiGradingTokenUsageRecorder(AiGradingTokenUsageRepository repository, AiTokenUsageStream stream) {
        this.repository = repository;
        this.stream = stream;
    }

    /**
     * Ghi 1 lệnh gọi CÓ ngữ cảnh nghiệp vụ (biết học sinh/bài/câu hỏi/bước chấm).
     *
     * @param usage {@code null} khi caller không có gì để ghi (VD AI lỗi trước khi kịp gọi) — bỏ qua im
     *              lặng, không ghi dòng rỗng làm nhiễu số liệu.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AiGradingTokenUsage.Step step, String operation, String requestedModel, AiTokenUsage usage,
                       Student student, ReviewVideoAssignment assignment, ReviewVideoQuestion question) {
        if (usage == null) {
            return;
        }
        try {
            repository.save(build(step, operation, requestedModel, usage, student, assignment, question, true));
            stream.publish(step, usage, student);
        } catch (RuntimeException e) {
            log.warn("AiGradingTokenUsageRecorder: ghi chi phí token thất bại (bước {}). {}", step, e.getMessage());
        }
    }

    /**
     * Lượt bị LOẠI kết quả (sai model / nội dung rỗng) — token đã tốn thật nhưng client tầng dưới không
     * biết học sinh nào, nên ghi KHÔNG ngữ cảnh với {@code accepted = false}. Xem {@link AiUsageSink}.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejected(String operation, String requestedModel, AiTokenUsage usage) {
        if (usage == null) {
            return;
        }
        try {
            AiGradingTokenUsage row = build(AiGradingTokenUsage.Step.OTHER, operation, requestedModel, usage,
                    null, null, null, false);
            repository.save(row);
            stream.publish(AiGradingTokenUsage.Step.OTHER, usage, null);
        } catch (RuntimeException e) {
            log.warn("AiGradingTokenUsageRecorder: ghi chi phí token (lượt bị loại) thất bại. {}", e.getMessage());
        }
    }

    private AiGradingTokenUsage build(AiGradingTokenUsage.Step step, String operation, String requestedModel,
                                      AiTokenUsage usage, Student student, ReviewVideoAssignment assignment,
                                      ReviewVideoQuestion question, boolean accepted) {
        AiGradingTokenUsage row = new AiGradingTokenUsage();
        row.setStep(step);
        row.setOperation(operation);
        row.setRequestedModel(requestedModel);
        row.setServedModel(usage.servedModel());
        row.setAudioAttached(usage.audioAttached());
        row.setPromptTokens(usage.promptTokens());
        row.setCachedTokens(usage.cachedTokens());
        row.setCompletionTokens(usage.completionTokens());
        row.setReasoningTokens(usage.reasoningTokens());
        row.setElapsedMs(usage.elapsedMs());
        row.setAccepted(accepted);
        row.setStudent(student);
        row.setReviewVideoAssignment(assignment);
        row.setReviewVideoQuestion(question);
        return row;
    }
}
