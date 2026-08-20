package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ExerciseAttempt;
import vn.com.pps.education.domain.ListeningHintEvent;
import vn.com.pps.education.domain.ListeningPlayProgress;
import vn.com.pps.education.domain.Question;
import vn.com.pps.education.domain.QuestionChoice;
import vn.com.pps.education.dto.ListeningHintResponse;
import vn.com.pps.education.dto.ListeningPlayProgressResponse;
import vn.com.pps.education.exception.AttemptNotEditableException;
import vn.com.pps.education.exception.ListeningHintNotUnlockedException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ExerciseQuestionRepository;
import vn.com.pps.education.repository.ListeningHintEventRepository;
import vn.com.pps.education.repository.ListeningPlayProgressRepository;
import vn.com.pps.education.repository.QuestionChoiceRepository;
import vn.com.pps.education.repository.QuestionRepository;
import vn.com.pps.education.repository.SystemSettingRepository;

import java.util.List;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — gợi ý
 * tapescript cho câu hỏi Nghe (skill=LISTENING, GV nước ngoài): học sinh
 * phải nghe HẾT audio đủ số lần cấu hình
 * (system_settings.lms.listening_hint_unlock_play_count, mặc định 3, xem
 * migration V94) mới xem được transcript + đáp án đúng + giải thích —
 * trước đây referencePassage bị lộ không điều kiện qua
 * ExerciseService#toResponse(ExerciseQuestion), giờ chỉ lộ qua
 * {@link #getHint} khi đã mở khóa (xem ExerciseService, đã gate cùng đợt).
 *
 * Nhóm "1 audio nhiều câu" (ListeningGroupBuilder, FE, cùng Question.groupKey)
 * dùng CHUNG 1 bộ đếm — {@link #listeningKeyOf} suy ra key theo groupKey
 * nếu có, không thì theo chính questionId, mirror cách Portal
 * (TakeExerciseModal.tsx) gộp hiển thị theo groupKey.
 *
 * Mẫu kiến trúc tái dùng: AttemptIntegrityService (client gửi sự kiện
 * theo attemptId, server đếm + kiểm ngưỡng) — đơn giản hơn ở đây vì không
 * polymorphic attemptType, không cần thông báo phụ huynh/giáo viên.
 */
@Service
public class ListeningHintService {

    private final ExerciseAttemptService exerciseAttemptService;
    private final ListeningPlayProgressRepository listeningPlayProgressRepository;
    private final ListeningHintEventRepository listeningHintEventRepository;
    private final QuestionRepository questionRepository;
    private final ExerciseQuestionRepository exerciseQuestionRepository;
    private final QuestionChoiceRepository questionChoiceRepository;
    private final SystemSettingRepository systemSettingRepository;

    public ListeningHintService(ExerciseAttemptService exerciseAttemptService,
                                 ListeningPlayProgressRepository listeningPlayProgressRepository,
                                 ListeningHintEventRepository listeningHintEventRepository,
                                 QuestionRepository questionRepository,
                                 ExerciseQuestionRepository exerciseQuestionRepository,
                                 QuestionChoiceRepository questionChoiceRepository,
                                 SystemSettingRepository systemSettingRepository) {
        this.exerciseAttemptService = exerciseAttemptService;
        this.listeningPlayProgressRepository = listeningPlayProgressRepository;
        this.listeningHintEventRepository = listeningHintEventRepository;
        this.questionRepository = questionRepository;
        this.exerciseQuestionRepository = exerciseQuestionRepository;
        this.questionChoiceRepository = questionChoiceRepository;
        this.systemSettingRepository = systemSettingRepository;
    }

    /** Gọi mỗi lần audio của 1 câu hỏi Nghe phát tới cuối (sự kiện `ended` ở FE) — tăng bộ đếm theo listeningKey. */
    @Transactional
    public ListeningPlayProgressResponse recordPlay(Long attemptId, Long questionId, Long actorUserId) {
        ExerciseAttempt attempt = exerciseAttemptService.attemptOwnedByActor(attemptId, actorUserId);
        requireInProgress(attempt);
        Question question = requireListeningQuestionInAttempt(attempt, questionId);
        String listeningKey = listeningKeyOf(question);

        ListeningPlayProgress progress = listeningPlayProgressRepository
                .findByExerciseAttemptIdAndListeningKey(attemptId, listeningKey)
                .orElseGet(() -> {
                    ListeningPlayProgress p = new ListeningPlayProgress();
                    p.setExerciseAttempt(attempt);
                    p.setListeningKey(listeningKey);
                    return p;
                });
        progress.setPlayCount(progress.getPlayCount() + 1);
        listeningPlayProgressRepository.save(progress);

        int threshold = hintUnlockThreshold();
        return new ListeningPlayProgressResponse(progress.getPlayCount(), threshold, progress.getPlayCount() >= threshold);
    }

    /** Chỉ trả nội dung gợi ý khi đã nghe hết đủ số lần cấu hình — mỗi lần gọi thành công ghi 1 dòng listening_hint_events (thống kê). */
    @Transactional
    public ListeningHintResponse getHint(Long attemptId, Long questionId, Long actorUserId) {
        ExerciseAttempt attempt = exerciseAttemptService.attemptOwnedByActor(attemptId, actorUserId);
        requireInProgress(attempt);
        Question question = requireListeningQuestionInAttempt(attempt, questionId);
        String listeningKey = listeningKeyOf(question);
        int threshold = hintUnlockThreshold();

        ListeningPlayProgress progress = listeningPlayProgressRepository
                .findByExerciseAttemptIdAndListeningKey(attemptId, listeningKey)
                .orElse(null);
        int playCount = progress == null ? 0 : progress.getPlayCount();
        if (playCount < threshold) {
            throw new ListeningHintNotUnlockedException(
                    "Cần nghe hết audio đủ " + threshold + " lần để xem gợi ý (đã nghe hết " + playCount + " lần).");
        }

        List<Long> correctChoiceIds = question.getQuestionType() == Question.QuestionType.MULTIPLE_CHOICE
                ? questionChoiceRepository.findByQuestionIdOrderByDisplayOrder(question.getId()).stream()
                        .filter(QuestionChoice::isCorrect).map(QuestionChoice::getId).toList()
                : List.of();

        ListeningHintEvent event = new ListeningHintEvent();
        event.setExerciseAttempt(attempt);
        event.setQuestion(question);
        event.setStudent(attempt.getStudent());
        listeningHintEventRepository.save(event);

        return new ListeningHintResponse(question.getReferencePassage(), question.getCorrectAnswerText(), correctChoiceIds, question.getExplanation());
    }

    /** Chặn gọi thẳng API sau khi đã nộp bài — trước đây chỉ FE ẩn nút "?" khi readOnly, gọi thẳng API vẫn mở được gợi ý cho lượt đã nộp (kể cả khi exercise.showCorrectAnswers=false không muốn lộ đáp án sau khi nộp). */
    private void requireInProgress(ExerciseAttempt attempt) {
        if (attempt.getStatus() != ExerciseAttempt.Status.IN_PROGRESS) {
            throw new AttemptNotEditableException("Lượt làm bài này không còn ở trạng thái đang làm (IN_PROGRESS).");
        }
    }

    private Question requireListeningQuestionInAttempt(ExerciseAttempt attempt, Long questionId) {
        if (!exerciseQuestionRepository.existsByExerciseIdAndQuestionId(attempt.getExercise().getId(), questionId)) {
            throw new ResourceNotFoundException("error.listeningHint.questionNotInExercise",
                    new Object[]{questionId, attempt.getExercise().getId()},
                    "Câu hỏi id=" + questionId + " không thuộc đề id=" + attempt.getExercise().getId());
        }
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("error.listeningHint.questionNotFound",
                        new Object[]{questionId}, "Không tìm thấy câu hỏi id=" + questionId));
        if (question.getSkill() != Question.Skill.LISTENING || question.getAudioUrl() == null) {
            throw new ResourceNotFoundException("error.listeningHint.notListeningQuestion",
                    new Object[]{questionId}, "Câu hỏi id=" + questionId + " không phải câu hỏi Nghe có audio.");
        }
        return question;
    }

    /** Nhóm "1 audio nhiều câu" (cùng groupKey) dùng chung 1 key — câu đơn dùng key riêng theo chính nó. */
    private String listeningKeyOf(Question question) {
        return question.getGroupKey() != null ? question.getGroupKey() : ("Q" + question.getId());
    }

    private int hintUnlockThreshold() {
        return systemSettingRepository.findBySettingKey("lms.listening_hint_unlock_play_count")
                .map(s -> s.getSettingValue().asInt())
                .orElse(3);
    }
}
