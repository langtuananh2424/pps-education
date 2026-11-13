package vn.com.pps.education.lms.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.exception.AttemptNotEditableException;
import vn.com.pps.education.exception.ListeningHintNotUnlockedException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.lms.domain.Exercise;
import vn.com.pps.education.lms.domain.ExerciseAttempt;
import vn.com.pps.education.lms.domain.ExerciseQuestion;
import vn.com.pps.education.lms.domain.ExerciseSection;
import vn.com.pps.education.lms.domain.ExerciseSectionResult;
import vn.com.pps.education.lms.domain.ListeningHintEvent;
import vn.com.pps.education.lms.domain.Question;
import vn.com.pps.education.lms.domain.StudentAnswer;
import vn.com.pps.education.lms.dto.ExerciseSectionRequest;
import vn.com.pps.education.lms.dto.ExerciseSectionResponse;
import vn.com.pps.education.lms.dto.SectionResultResponse;
import vn.com.pps.education.lms.dto.SectionResultsResponse;
import vn.com.pps.education.lms.dto.TapescriptViewResponse;
import vn.com.pps.education.lms.repository.ExerciseAttemptRepository;
import vn.com.pps.education.lms.repository.ExerciseQuestionRepository;
import vn.com.pps.education.lms.repository.ExerciseRepository;
import vn.com.pps.education.lms.repository.ExerciseSectionRepository;
import vn.com.pps.education.lms.repository.ExerciseSectionResultRepository;
import vn.com.pps.education.lms.repository.ListeningHintEventRepository;
import vn.com.pps.education.lms.repository.StudentAnswerRepository;
import vn.com.pps.education.student.domain.Student;
import vn.com.pps.education.student.repository.StudentRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * V226 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-11-09) — phương pháp "Nghe ép" cho UC-40
 * (soạn bài tập con), UC-24/UC-27 (làm bài). Xem mục "Bổ sung V226" trong docs/uc/phan-he-07-lms-portal.md
 * và mục i-bis-2 trong docs/sdd-groups/09-lms-and-portal.md.
 *
 * Tách riêng khỏi {@link ExerciseAttemptService} (đã 1200+ dòng): ExerciseAttemptService chỉ gọi vào đây ở
 * đúng 4 điểm — startAttempt (mang bài đã đạt sang lượt mới), gradeAndFinalize (ghi kết quả từng bài),
 * applyPassOutcome (đạt = mọi bài đạt) và toResponse (ẩn đúng/sai). Mọi quy tắc riêng của Nghe ép nằm ở đây.
 *
 * shortcut: lượt làm lại có bài đã đạt mà bài đó mở lại tự nguyện nhưng điểm thấp hơn, lượt kế tiếp mang sang
 * CHÍNH bài làm thấp đó (không tìm lại bài làm đạt) — điểm đạt/điểm cao nhất trong exercise_section_results
 * không bị ảnh hưởng, chỉ nội dung hiển thị lại; nâng cấp nếu giáo viên phàn nàn.
 */
@Service
public class ForcedListeningService {

    /** Số bài tập con tối đa trong 1 Bài (mô tả nghiệp vụ: thường 4, tối đa 12, hiện hết trên 1 trang). */
    static final int MAX_SECTIONS = 12;

    private final ExerciseRepository exerciseRepository;
    private final ExerciseQuestionRepository exerciseQuestionRepository;
    private final ExerciseSectionRepository sectionRepository;
    private final ExerciseSectionResultRepository resultRepository;
    private final StudentAnswerRepository studentAnswerRepository;
    private final ExerciseAttemptRepository exerciseAttemptRepository;
    private final ListeningHintEventRepository listeningHintEventRepository;
    private final StudentRepository studentRepository;
    private final PassThresholdResolver passThresholdResolver;

    public ForcedListeningService(ExerciseRepository exerciseRepository,
                                  ExerciseQuestionRepository exerciseQuestionRepository,
                                  ExerciseSectionRepository sectionRepository,
                                  ExerciseSectionResultRepository resultRepository,
                                  StudentAnswerRepository studentAnswerRepository,
                                  ExerciseAttemptRepository exerciseAttemptRepository,
                                  ListeningHintEventRepository listeningHintEventRepository,
                                  StudentRepository studentRepository,
                                  PassThresholdResolver passThresholdResolver) {
        this.exerciseRepository = exerciseRepository;
        this.exerciseQuestionRepository = exerciseQuestionRepository;
        this.sectionRepository = sectionRepository;
        this.resultRepository = resultRepository;
        this.studentAnswerRepository = studentAnswerRepository;
        this.exerciseAttemptRepository = exerciseAttemptRepository;
        this.listeningHintEventRepository = listeningHintEventRepository;
        this.studentRepository = studentRepository;
        this.passThresholdResolver = passThresholdResolver;
    }

    // ===================== Soạn bài tập con (UC-40) =====================

    @Transactional(readOnly = true)
    public List<ExerciseSectionResponse> listSections(Long exerciseId) {
        Exercise exercise = forcedExerciseOrThrow(exerciseId);
        return sectionRepository.findByExerciseIdOrderByDisplayOrder(exerciseId).stream()
                .map(s -> toResponse(s, exercise)).toList();
    }

    @Transactional
    public ExerciseSectionResponse createSection(Long exerciseId, ExerciseSectionRequest request) {
        Exercise exercise = draftForcedExerciseOrThrow(exerciseId);
        List<ExerciseSection> existing = sectionRepository.findByExerciseIdOrderByDisplayOrder(exerciseId);
        if (existing.size() >= MAX_SECTIONS) {
            throw new IllegalArgumentException("Mỗi Bài Nghe ép tối đa " + MAX_SECTIONS + " bài tập con.");
        }
        ExerciseSection section = new ExerciseSection();
        section.setExercise(exercise);
        section.setTitle(request.title());
        section.setDisplayOrder(request.displayOrder() != null ? request.displayOrder()
                : existing.stream().mapToInt(ExerciseSection::getDisplayOrder).max().orElse(0) + 1);
        section.setPassThresholdPercent(request.passThresholdPercent());
        return toResponse(sectionRepository.save(section), exercise);
    }

    @Transactional
    public ExerciseSectionResponse updateSection(Long exerciseId, Long sectionId, ExerciseSectionRequest request) {
        Exercise exercise = draftForcedExerciseOrThrow(exerciseId);
        ExerciseSection section = sectionOfExerciseOrThrow(exerciseId, sectionId);
        section.setTitle(request.title());
        if (request.displayOrder() != null) {
            section.setDisplayOrder(request.displayOrder());
        }
        section.setPassThresholdPercent(request.passThresholdPercent());
        return toResponse(sectionRepository.save(section), exercise);
    }

    @Transactional
    public void deleteSection(Long exerciseId, Long sectionId) {
        draftForcedExerciseOrThrow(exerciseId);
        ExerciseSection section = sectionOfExerciseOrThrow(exerciseId, sectionId);
        if (exerciseQuestionRepository.countBySectionId(sectionId) > 0) {
            throw new IllegalArgumentException("Bài tập con còn câu hỏi — gỡ hết câu hỏi khỏi bài tập con trước khi xóa.");
        }
        sectionRepository.delete(section);
    }

    /**
     * Gọi từ {@code ExerciseService#addQuestion}. Bài thường: không được truyền sectionId. Bài Nghe ép:
     * sectionId bắt buộc, thuộc đúng Bài, và câu hỏi phải tự chấm được (không ESSAY/SPEAKING). Trả về bài
     * tập con để gắn vào {@code ExerciseQuestion} (NULL với Bài thường).
     */
    @Transactional(readOnly = true)
    public ExerciseSection resolveSectionForNewQuestion(Exercise exercise, Long sectionId, Question question) {
        if (!exercise.isForcedListening()) {
            if (sectionId != null) {
                throw new IllegalArgumentException("Chỉ Bài Nghe ép mới chia bài tập con.");
            }
            return null;
        }
        if (sectionId == null) {
            throw new IllegalArgumentException("Bài Nghe ép: phải chọn bài tập con cho câu hỏi.");
        }
        if (!ExerciseAttemptService.AUTO_GRADABLE_TYPES.contains(question.getQuestionType())) {
            throw new IllegalArgumentException("Bài Nghe ép chỉ nhận câu tự chấm (trắc nghiệm, điền từ, xếp từ).");
        }
        return sectionOfExerciseOrThrow(exercise.getId(), sectionId);
    }

    /** Gọi từ {@code ExerciseService#publishExercise}: Bài Nghe ép phải có 1..12 bài tập con, mỗi bài ≥ 1 câu, mọi câu đều thuộc 1 bài. */
    @Transactional(readOnly = true)
    public void validateForPublish(Exercise exercise) {
        if (!exercise.isForcedListening()) {
            return;
        }
        List<ExerciseSection> sections = sectionRepository.findByExerciseIdOrderByDisplayOrder(exercise.getId());
        if (sections.isEmpty() || sections.size() > MAX_SECTIONS) {
            throw new IllegalArgumentException("Bài Nghe ép phải có từ 1 đến " + MAX_SECTIONS + " bài tập con.");
        }
        List<ExerciseQuestion> questions = exerciseQuestionRepository.findByExerciseIdOrderByDisplayOrder(exercise.getId());
        if (questions.stream().anyMatch(eq -> eq.getSection() == null)) {
            throw new IllegalArgumentException("Bài Nghe ép: còn câu hỏi chưa thuộc bài tập con nào.");
        }
        Set<Long> used = questions.stream().map(eq -> eq.getSection().getId()).collect(Collectors.toSet());
        for (ExerciseSection s : sections) {
            if (!used.contains(s.getId())) {
                throw new IllegalArgumentException("Bài tập con \"" + s.getTitle() + "\" chưa có câu hỏi nào.");
            }
        }
    }

    // ===================== Làm bài (UC-24/UC-27) =====================

    /**
     * Gọi từ {@code ExerciseAttemptService#startAttempt} cho Bài Nghe ép khi có lượt trước (A1/A2). Bài đã đạt
     * (và không nằm trong {@code reopenSectionIds}) được mang NGUYÊN BÀI — mọi câu, kể cả câu sai hoặc bỏ trống
     * — sang lượt mới và khoá; bài chưa đạt hoặc được mở lại tự nguyện thì mở sạch toàn bộ câu như làm mới.
     */
    @Transactional
    public void carryOverPassedSections(ExerciseAttempt newAttempt, ExerciseAttempt lastAttempt, List<Long> reopenSectionIds) {
        Exercise exercise = newAttempt.getExercise();
        Set<Long> reopen = reopenSectionIds == null ? Set.of() : new HashSet<>(reopenSectionIds);
        Set<Long> sectionIds = sectionRepository.findByExerciseIdOrderByDisplayOrder(exercise.getId()).stream()
                .map(ExerciseSection::getId).collect(Collectors.toSet());
        if (!sectionIds.containsAll(reopen)) {
            throw new IllegalArgumentException("Bài tập con muốn làm lại không thuộc Bài này.");
        }
        Set<Long> passed = resultRepository
                .findByExerciseAssignmentIdAndStudentId(newAttempt.getExerciseAssignment().getId(), newAttempt.getStudent().getId())
                .stream().filter(ExerciseSectionResult::isPassed)
                .map(r -> r.getExerciseSection().getId())
                .filter(id -> !reopen.contains(id))
                .collect(Collectors.toSet());
        if (passed.isEmpty()) {
            return;
        }
        Map<Long, StudentAnswer> previousByQuestion = studentAnswerRepository.findByExerciseAttemptId(lastAttempt.getId()).stream()
                .collect(Collectors.toMap(a -> a.getQuestion().getId(), Function.identity(), (a, b) -> a));
        for (ExerciseQuestion eq : exerciseQuestionRepository.findByExerciseIdOrderByDisplayOrder(exercise.getId())) {
            if (eq.getSection() == null || !passed.contains(eq.getSection().getId())) {
                continue;
            }
            StudentAnswer previous = previousByQuestion.get(eq.getQuestion().getId());
            StudentAnswer carried = new StudentAnswer();
            carried.setExerciseAttempt(newAttempt);
            carried.setQuestion(eq.getQuestion());
            carried.setAutoGradable(true);
            if (previous != null) {
                carried.setAnswerText(previous.getAnswerText());
                carried.setSelectedChoiceIds(previous.getSelectedChoiceIds());
                carried.setStructuredAnswer(previous.getStructuredAnswer());
            }
            // Cả câu bỏ trống cũng có dòng khoá, để saveAnswer chặn sửa mọi câu của bài đã đạt (không chỉ câu đã trả lời).
            carried.setCarriedOverFromPreviousAttempt(true);
            studentAnswerRepository.save(carried);
        }
    }

    /**
     * Gọi từ {@code ExerciseAttemptService#gradeAndFinalize} SAU KHI các câu đã chấm: cập nhật
     * {@code exercise_section_results} cho từng bài tập con ĐANG MỞ (bài mang sang từ lượt trước — mọi câu có
     * cờ carried — bỏ qua). Xem Alternate Flow A1-A3 trong mục "Bổ sung V226" của docs/uc/phan-he-07-lms-portal.md.
     */
    @Transactional
    public void recordSubmit(ExerciseAttempt attempt, List<StudentAnswer> answers, List<ExerciseQuestion> exerciseQuestions) {
        Exercise exercise = attempt.getExercise();
        Student student = attempt.getStudent();
        Map<Long, StudentAnswer> answerByQuestion = answers.stream()
                .collect(Collectors.toMap(a -> a.getQuestion().getId(), Function.identity(), (a, b) -> a));
        Map<Long, List<ExerciseQuestion>> bySection = exerciseQuestions.stream()
                .filter(eq -> eq.getSection() != null)
                .collect(Collectors.groupingBy(eq -> eq.getSection().getId(), LinkedHashMap::new, Collectors.toList()));
        OffsetDateTime now = OffsetDateTime.now();

        for (ExerciseSection section : sectionRepository.findByExerciseIdOrderByDisplayOrder(exercise.getId())) {
            List<ExerciseQuestion> questions = bySection.getOrDefault(section.getId(), List.of());
            boolean carried = questions.stream().anyMatch(eq -> {
                StudentAnswer a = answerByQuestion.get(eq.getQuestion().getId());
                return a != null && a.isCarriedOverFromPreviousAttempt();
            });
            if (questions.isEmpty() || carried) {
                continue;
            }

            BigDecimal maxPoints = questions.stream().map(ExerciseQuestion::getPoints).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal got = BigDecimal.ZERO;
            int answered = 0;
            List<Long> wrongQuestionIds = new ArrayList<>();
            for (ExerciseQuestion eq : questions) {
                StudentAnswer a = answerByQuestion.get(eq.getQuestion().getId());
                if (a == null || !hasContent(a)) {
                    continue;
                }
                answered++;
                if (a.getAutoScore() != null) {
                    got = got.add(a.getAutoScore());
                }
                if (Boolean.FALSE.equals(a.getCorrect())) {
                    wrongQuestionIds.add(eq.getQuestion().getId());
                }
            }
            BigDecimal percent = maxPoints.signum() <= 0 ? BigDecimal.ZERO
                    : got.divide(maxPoints, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

            ExerciseSectionResult result = resultRepository
                    .findByExerciseAssignmentIdAndStudentIdAndExerciseSectionId(attempt.getExerciseAssignment().getId(), student.getId(), section.getId())
                    .orElseGet(() -> {
                        ExerciseSectionResult r = new ExerciseSectionResult();
                        r.setExerciseAssignment(attempt.getExerciseAssignment());
                        r.setStudent(student);
                        r.setExerciseSection(section);
                        return r;
                    });
            result.setLatestScorePercent(percent);
            boolean reachedThreshold = percent.compareTo(passThresholdResolver.thresholdFor(section, student)) >= 0;

            if (result.isPassed()) {
                // A2: chỉ lượt đạt mức cần đạt và cao hơn mới nâng điểm cao nhất; lượt thấp hơn bị bỏ qua.
                if (reachedThreshold && percent.compareTo(result.getBestScorePercent()) > 0) {
                    result.setBestScorePercent(percent);
                }
            } else if (reachedThreshold) {
                result.setPassed(true);
                result.setBestScorePercent(percent);
                result.setFirstPassedAt(now);
            } else if (answered > 0) {
                // A3: nộp trắng không tính là lượt chưa đạt.
                result.setFailedAttemptsCount(result.getFailedAttemptsCount() + 1);
                unlockTapescripts(result, exercise, wrongQuestionIds);
            }
            resultRepository.save(result);
        }
    }

    /** A3: mở thêm tapescript các câu SAI (có trả lời) đầu tiên của lượt mới nhất cho tới khi đủ số câu theo số lượt chưa đạt. */
    private void unlockTapescripts(ExerciseSectionResult result, Exercise exercise, List<Long> wrongQuestionIds) {
        int target = unlockedCount(result.getFailedAttemptsCount(),
                exercise.getTapescriptFirstUnlockAttempts(), exercise.getTapescriptUnlockStep());
        List<Long> unlocked = new ArrayList<>(result.getUnlockedQuestionIds());
        for (Long questionId : wrongQuestionIds) {
            if (unlocked.size() >= target) {
                break;
            }
            if (!unlocked.contains(questionId)) {
                unlocked.add(questionId);
            }
        }
        result.setUnlockedQuestionIds(unlocked);
    }

    /** Số câu được mở sau {@code failed} lượt chưa đạt: 0 nếu chưa tới mốc đầu, sau đó cứ {@code step} lượt mở thêm 1 câu. */
    static int unlockedCount(int failed, int first, int step) {
        return failed < first ? 0 : (failed - first) / step + 1;
    }

    /** Số lượt chưa đạt nữa thì mở thêm 1 câu. */
    static int attemptsUntilNextUnlock(int failed, int first, int step) {
        return first + unlockedCount(failed, first, step) * step - failed;
    }

    /** Học sinh coi như có trả lời khi có nội dung — câu để trắng/ô rỗng không tính. */
    private static boolean hasContent(StudentAnswer a) {
        return (a.getAnswerText() != null && !a.getAnswerText().isBlank())
                || (a.getSelectedChoiceIds() != null && !a.getSelectedChoiceIds().isEmpty())
                || (a.getStructuredAnswer() != null && a.getStructuredAnswer().stream().anyMatch(s -> s != null && !s.isBlank()));
    }

    /** A5: lượt đạt khi MỌI bài tập con của Bài đều đã đạt (cho lần giao này). */
    @Transactional(readOnly = true)
    public boolean allSectionsPassed(ExerciseAttempt attempt) {
        if (attempt.getExerciseAssignment() == null) {
            return false;
        }
        List<ExerciseSection> sections = sectionRepository.findByExerciseIdOrderByDisplayOrder(attempt.getExercise().getId());
        if (sections.isEmpty()) {
            return false;
        }
        Set<Long> passed = resultRepository
                .findByExerciseAssignmentIdAndStudentId(attempt.getExerciseAssignment().getId(), attempt.getStudent().getId())
                .stream().filter(ExerciseSectionResult::isPassed).map(r -> r.getExerciseSection().getId()).collect(Collectors.toSet());
        return sections.stream().allMatch(s -> passed.contains(s.getId()));
    }

    /**
     * A5: chỉ cho "Xem đáp án" khi Bài là Nghe ép (không phụ thuộc show_correct_answers), lượt đang xét
     * là lượt GẦN NHẤT của lần giao này (cờ khoá làm lại nằm trên lượt gần nhất — xem startAttempt) và mọi bài đã đạt.
     */
    @Transactional(readOnly = true)
    public void requireCanReveal(ExerciseAttempt attempt) {
        Exercise exercise = attempt.getExercise();
        if (!isLatestAttempt(attempt)) {
            throw new AttemptNotEditableException("error.forcedListening.revealLatestOnly", new Object[]{},
                    "Chỉ xem đáp án được ở lượt làm gần nhất.");
        }
        if (!allSectionsPassed(attempt)) {
            throw new AttemptNotEditableException("error.forcedListening.revealNeedAllPassed", new Object[]{},
                    "Phải đạt mọi bài tập con mới xem được đáp án.");
        }
    }

    private boolean isLatestAttempt(ExerciseAttempt attempt) {
        return exerciseAttemptRepository
                .findByExerciseAssignmentIdAndStudentIdOrderByAttemptNumberDesc(attempt.getExerciseAssignment().getId(), attempt.getStudent().getId())
                .stream().findFirst().map(a -> a.getId().equals(attempt.getId())).orElse(false);
    }

    // ===================== Bảng điểm & tapescript =====================

    @Transactional(readOnly = true)
    public SectionResultsResponse getSectionResults(Long attemptId, Long actorUserId) {
        ExerciseAttempt attempt = ownedForcedAttemptOrThrow(attemptId, actorUserId);
        Exercise exercise = attempt.getExercise();
        Student student = attempt.getStudent();
        Map<Long, ExerciseSectionResult> resultBySection = resultRepository
                .findByExerciseAssignmentIdAndStudentId(attempt.getExerciseAssignment().getId(), student.getId()).stream()
                .collect(Collectors.toMap(r -> r.getExerciseSection().getId(), Function.identity(), (a, b) -> a));

        boolean revealed = isRevealed(attempt);
        // Đã đạt hết + xem đáp án: mở tapescript của TẤT CẢ câu (theo yêu cầu người dùng 2026-11-12).
        Map<Long, List<Long>> questionIdsBySection = revealed
                ? exerciseQuestionRepository.findByExerciseIdOrderByDisplayOrder(exercise.getId()).stream()
                        .filter(x -> x.getSection() != null)
                        .collect(Collectors.groupingBy(x -> x.getSection().getId(), Collectors.mapping(x -> x.getQuestion().getId(), Collectors.toList())))
                : Map.of();
        List<SectionResultResponse> sections = new ArrayList<>();
        for (ExerciseSection s : sectionRepository.findByExerciseIdOrderByDisplayOrder(exercise.getId())) {
            ExerciseSectionResult r = resultBySection.get(s.getId());
            int failed = r == null ? 0 : r.getFailedAttemptsCount();
            boolean passed = r != null && r.isPassed();
            sections.add(new SectionResultResponse(
                    s.getId(), s.getTitle(), s.getDisplayOrder(), passThresholdResolver.thresholdFor(s, student), passed,
                    r == null ? null : r.getLatestScorePercent(), r == null ? null : r.getBestScorePercent(), failed,
                    passed ? null : attemptsUntilNextUnlock(failed, exercise.getTapescriptFirstUnlockAttempts(), exercise.getTapescriptUnlockStep()),
                    revealed ? questionIdsBySection.getOrDefault(s.getId(), List.of())
                            : r == null ? List.of() : List.copyOf(r.getUnlockedQuestionIds())));
        }
        boolean allPassed = !sections.isEmpty() && sections.stream().allMatch(SectionResultResponse::passed);
        BigDecimal overall = allPassed
                ? sections.stream().map(SectionResultResponse::bestScorePercent).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(sections.size()), 2, RoundingMode.HALF_UP)
                : null;
        return new SectionResultsResponse(sections, allPassed, overall,
                allPassed && !revealed, revealed);
    }

    private boolean isRevealed(ExerciseAttempt attempt) {
        return exerciseAttemptRepository
                .findByExerciseAssignmentIdAndStudentIdOrderByAttemptNumberDesc(attempt.getExerciseAssignment().getId(), attempt.getStudent().getId())
                .stream().findFirst().orElse(attempt).isAnswersRevealedEarly();
    }

    /**
     * A3/A4: trả tapescript của ĐÚNG 1 câu đã được mở và ghi 1 dòng {@code listening_hint_events} (1 lượt
     * xem). Gọi được cả sau khi đã nộp (khác V94 chỉ cho xem lúc IN_PROGRESS).
     */
    @Transactional
    public TapescriptViewResponse viewTapescript(Long attemptId, Long questionId, Long actorUserId) {
        ExerciseAttempt attempt = ownedForcedAttemptOrThrow(attemptId, actorUserId);
        ExerciseQuestion eq = exerciseQuestionRepository.findByExerciseIdOrderByDisplayOrder(attempt.getExercise().getId()).stream()
                .filter(x -> x.getQuestion().getId().equals(questionId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("error.listeningHint.questionNotInExercise",
                        new Object[]{questionId, attempt.getExercise().getId()},
                        "Câu hỏi id=" + questionId + " không thuộc đề id=" + attempt.getExercise().getId()));
        boolean unlocked = isRevealed(attempt) || eq.getSection() != null && resultRepository
                .findByExerciseAssignmentIdAndStudentIdAndExerciseSectionId(attempt.getExerciseAssignment().getId(), attempt.getStudent().getId(), eq.getSection().getId())
                .map(r -> r.getUnlockedQuestionIds().contains(questionId)).orElse(false);
        if (!unlocked) {
            throw new ListeningHintNotUnlockedException("error.forcedListening.tapescriptLocked", new Object[]{questionId},
                    "Tapescript của câu này chưa được mở.");
        }
        ListeningHintEvent event = new ListeningHintEvent();
        event.setExerciseAttempt(attempt);
        event.setQuestion(eq.getQuestion());
        event.setStudent(attempt.getStudent());
        listeningHintEventRepository.save(event);
        return new TapescriptViewResponse(questionId, eq.getQuestion().getTapescript());
    }

    // ===================== Helpers =====================

    private ExerciseAttempt ownedForcedAttemptOrThrow(Long attemptId, Long actorUserId) {
        Student student = studentRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.exerciseAttempt.studentProfileNotFound",
                        new Object[]{actorUserId}, "Tài khoản id=" + actorUserId + " không có hồ sơ học sinh."));
        ExerciseAttempt attempt = exerciseAttemptRepository.findById(attemptId)
                .filter(a -> a.getStudent().getId().equals(student.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("error.exerciseAttempt.notFoundById",
                        new Object[]{attemptId}, "Không tìm thấy lượt làm bài id=" + attemptId));
        if (!attempt.getExercise().isForcedListening()) {
            throw new IllegalArgumentException("Lượt làm này không thuộc Bài Nghe ép.");
        }
        return attempt;
    }

    private Exercise forcedExerciseOrThrow(Long exerciseId) {
        Exercise exercise = exerciseRepository.findById(exerciseId)
                .orElseThrow(() -> new ResourceNotFoundException("error.exerciseAttempt.exerciseNotFound",
                        new Object[]{exerciseId}, "Không tìm thấy đề id=" + exerciseId));
        if (!exercise.isForcedListening()) {
            throw new IllegalArgumentException("Bài này không phải Bài Nghe ép.");
        }
        return exercise;
    }

    private Exercise draftForcedExerciseOrThrow(Long exerciseId) {
        Exercise exercise = forcedExerciseOrThrow(exerciseId);
        if (exercise.getStatus() != Exercise.Status.DRAFT) {
            throw new IllegalArgumentException("Đề này đã Publish — chỉ sửa được bài tập con khi còn Nháp (DRAFT).");
        }
        return exercise;
    }

    private ExerciseSection sectionOfExerciseOrThrow(Long exerciseId, Long sectionId) {
        return sectionRepository.findById(sectionId)
                .filter(s -> s.getExercise().getId().equals(exerciseId))
                .orElseThrow(() -> new ResourceNotFoundException("error.forcedListening.sectionNotFound",
                        new Object[]{sectionId, exerciseId}, "Không tìm thấy bài tập con id=" + sectionId + " trong đề id=" + exerciseId));
    }

    private ExerciseSectionResponse toResponse(ExerciseSection s, Exercise exercise) {
        return new ExerciseSectionResponse(s.getId(), exercise.getId(), s.getTitle(), s.getDisplayOrder(),
                s.getPassThresholdPercent(),
                s.getPassThresholdPercent() != null ? s.getPassThresholdPercent() : exercise.getPassThresholdPercent(),
                exerciseQuestionRepository.countBySectionId(s.getId()));
    }
}
