package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ListeningPracticeAttempt;
import vn.com.pps.education.domain.ListeningPracticeGrading;
import vn.com.pps.education.domain.ListeningPracticeItem;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.GradeListeningAttemptRequest;
import vn.com.pps.education.dto.ListeningPracticeGradingResponse;
import vn.com.pps.education.dto.PendingListeningGradingResponse;
import vn.com.pps.education.exception.AnswerNotManuallyGradableException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ListeningPracticeAttemptRepository;
import vn.com.pps.education.repository.ListeningPracticeGradingRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * UC-26: Chấm thủ công lượt luyện Nói — hàng chờ TÁCH RIÊNG khỏi
 * ManualGradingController/ManualGradingService (UC-41), theo xác nhận
 * của người dùng (domain luyện nghe-nói độc lập, không dùng chung bảng
 * exercise_attempts/student_answers). Cấu trúc đơn giản hơn
 * ManualGradingService vì 1 attempt chỉ có 1 điểm (không phải nhiều câu
 * hỏi con) — không cần tính lại tổng điểm sau khi chấm.
 *
 * Authorization qua @PreAuthorize("hasPermission(null,'lms.grading.manage')")
 * ở ListeningPracticeGradingController — tái dùng đúng permission đã có
 * của UC-41 (mô tả gốc "Chấm bài thủ công" đủ tổng quát, đúng ngữ nghĩa
 * cho cả 2 domain).
 */
@Service
public class ListeningPracticeGradingService {

    private final ListeningPracticeAttemptRepository practiceAttemptRepository;
    private final ListeningPracticeGradingRepository practiceGradingRepository;
    private final UserRepository userRepository;

    public ListeningPracticeGradingService(ListeningPracticeAttemptRepository practiceAttemptRepository,
                                            ListeningPracticeGradingRepository practiceGradingRepository,
                                            UserRepository userRepository) {
        this.practiceAttemptRepository = practiceAttemptRepository;
        this.practiceGradingRepository = practiceGradingRepository;
        this.userRepository = userRepository;
    }

    /** Mọi lượt luyện Nói đã nộp, chưa chấm. */
    @Transactional(readOnly = true)
    public List<PendingListeningGradingResponse> listPendingGrading() {
        return practiceAttemptRepository
                .findByStatusAndPracticeItem_Mode(ListeningPracticeAttempt.Status.SUBMITTED, ListeningPracticeItem.Mode.SPEAKING)
                .stream().map(this::toPendingResponse).toList();
    }

    /** Chấm 1 lượt — 1 attempt tối đa 1 lần chấm, sửa thì update tại chỗ (không cần versioning). */
    @Transactional
    public ListeningPracticeGradingResponse gradeAttempt(Long attemptId, GradeListeningAttemptRequest request, Long actorUserId) {
        User actor = userOrThrow(actorUserId);
        ListeningPracticeAttempt attempt = practiceAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("error.listeningPracticeGrading.attemptNotFound",
                        new Object[]{attemptId}, "Không tìm thấy lượt luyện id=" + attemptId));
        if (attempt.getPracticeItem().getMode() != ListeningPracticeItem.Mode.SPEAKING) {
            throw new AnswerNotManuallyGradableException(
                    "Lượt luyện này không thuộc chế độ Nói — không chấm thủ công.");
        }

        ListeningPracticeGrading grading = practiceGradingRepository.findByPracticeAttemptId(attemptId)
                .orElseGet(ListeningPracticeGrading::new);
        grading.setPracticeAttempt(attempt);
        grading.setGrader(actor);
        grading.setScore(request.score());
        grading.setMaxScore(request.maxScore());
        grading.setFeedback(request.feedback());
        grading.setGradedAt(OffsetDateTime.now());
        grading = practiceGradingRepository.save(grading);

        attempt.setStatus(ListeningPracticeAttempt.Status.GRADED);
        practiceAttemptRepository.save(attempt);

        return toResponse(grading);
    }

    // ===================== Helpers =====================

    private User userOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.listeningPracticeGrading.userNotFound",
                        new Object[]{id}, "Không tìm thấy tài khoản id=" + id));
    }

    private PendingListeningGradingResponse toPendingResponse(ListeningPracticeAttempt a) {
        return new PendingListeningGradingResponse(
                a.getId(), a.getPracticeItem().getId(), a.getPracticeItem().getTitle(),
                a.getStudent().getId(), a.getStudent().getUser().getFullName(),
                a.getAudioAnswerUrl(), a.getPracticeItem().getScriptText());
    }

    private ListeningPracticeGradingResponse toResponse(ListeningPracticeGrading g) {
        return new ListeningPracticeGradingResponse(
                g.getId(), g.getPracticeAttempt().getId(), g.getGrader().getId(), g.getScore(), g.getMaxScore(),
                g.getFeedback(), g.getGradedAt());
    }
}
