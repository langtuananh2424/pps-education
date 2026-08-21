package vn.com.pps.education.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.CurriculumSubject;
import vn.com.pps.education.domain.Exam;
import vn.com.pps.education.domain.Exercise;
import vn.com.pps.education.domain.ExerciseAssignment;
import vn.com.pps.education.domain.ExerciseQuestion;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.Question;
import vn.com.pps.education.domain.QuestionChoice;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AddExerciseQuestionRequest;
import vn.com.pps.education.dto.CreateExerciseRequest;
import vn.com.pps.education.dto.ExerciseAssignmentResponse;
import vn.com.pps.education.dto.ExerciseQuestionChoiceResponse;
import vn.com.pps.education.dto.ExerciseQuestionResponse;
import vn.com.pps.education.dto.ExerciseResponse;
import vn.com.pps.education.dto.UpdateExerciseQuestionPointsRequest;
import vn.com.pps.education.dto.UpdateExerciseRequest;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ClassTeacherRepository;
import vn.com.pps.education.repository.CurriculumSubjectRepository;
import vn.com.pps.education.repository.ExamClassAssignmentRepository;
import vn.com.pps.education.repository.ExamRepository;
import vn.com.pps.education.repository.ExerciseAssignmentRepository;
import vn.com.pps.education.repository.ExerciseQuestionRepository;
import vn.com.pps.education.repository.ExerciseRepository;
import vn.com.pps.education.repository.QuestionChoiceRepository;
import vn.com.pps.education.repository.QuestionRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * UC-40: Soạn & giao đề kiểm tra (FR-LMS-10) — phần lắp đề + publish.
 * Xem docs/uc/phan-he-07-lms-portal.md. Dùng chung Question (ngân hàng)
 * từ QuestionBankService, không gộp 2 Service (xem Javadoc đó).
 *
 * createExercise/addQuestion/publishExercise (TEACHER) qua
 * @PreAuthorize("hasPermission(null,'lms.exercise.create/update/publish')")
 * ở ExerciseController (Hybrid PBAC — V28/V62).
 *
 * V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
 * "Soạn & Giao đề" KHÔNG còn bước giao lớp/deadline/target students —
 * publishExercise() giờ chỉ đánh dấu đề "đủ điều kiện dùng làm nguồn".
 * Việc giao thật cho lớp (tạo {@link ExerciseAssignment}) chuyển hẳn sang
 * {@code deliverToClass}, gọi TỪ StudentCommentService khi Giáo viên chọn
 * đề này làm "BTVN buổi sau" ở Nhận xét (UC-21) — không còn expose qua
 * ExerciseController nữa (endpoint POST /api/exercises/{id}/assign đã bị
 * xóa). requireAssignedTeacher vẫn giữ nguyên làm row-level scope check.
 *
 * Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
 * tái cấu trúc 2 cấp — mỗi "Bài" (Exercise) thuộc 1 "Đề" ({@link Exam},
 * xem {@code ExamService}). Điều kiện hiển thị/giao cho lớp đổi từ "khớp
 * khung chương trình" sang "Đề đã gán cho lớp"
 * ({@code ExamClassAssignmentRepository}) — ÁP DỤNG CHO MỌI exerciseType
 * (không riêng ASSIGNED), nên SELF_PRACTICE/MOCK_TEST/SKILL_PRACTICE mất
 * cơ chế "mở tự do sau khi Publish" cũ (xem requireCanViewExercise +
 * ExerciseAttemptService#startAttempt, và docs UC-27).
 */
@Service
public class ExerciseService {

    /** Câu hỏi trắc nghiệm/đúng-sai có phương án chọn sẵn (lộ cho HS khi làm bài) — khớp AUTO_GRADABLE_TYPES của ExerciseAttemptService. */
    private static final Set<Question.QuestionType> CHOICE_BASED_TYPES = Set.of(
            Question.QuestionType.MULTIPLE_CHOICE, Question.QuestionType.MULTIPLE_ANSWER, Question.QuestionType.TRUE_FALSE);

    private final ExerciseRepository exerciseRepository;
    private final ExerciseQuestionRepository exerciseQuestionRepository;
    private final ExerciseAssignmentRepository exerciseAssignmentRepository;
    private final ExamRepository examRepository;
    private final ExamClassAssignmentRepository examClassAssignmentRepository;
    private final QuestionRepository questionRepository;
    private final QuestionChoiceRepository questionChoiceRepository;
    private final CurriculumSubjectRepository curriculumSubjectRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final PermissionEvaluationService permissionEvaluationService;
    /** V71: xem Javadoc tương tự ở ReviewVideoService — chạy riêng 1 giao dịch lồng khi thử tạo bản
     * giao, để race thua (bắt DataIntegrityViolationException) chỉ rollback đúng giao dịch con này. */
    private final TransactionTemplate requiresNewTransactionTemplate;

    private static final String PERM_EXAM_MANAGE = "lms.exam.manage";

    public ExerciseService(ExerciseRepository exerciseRepository,
                            ExerciseQuestionRepository exerciseQuestionRepository,
                            ExerciseAssignmentRepository exerciseAssignmentRepository,
                            ExamRepository examRepository,
                            ExamClassAssignmentRepository examClassAssignmentRepository,
                            QuestionRepository questionRepository,
                            QuestionChoiceRepository questionChoiceRepository,
                            CurriculumSubjectRepository curriculumSubjectRepository,
                            SchoolClassRepository schoolClassRepository,
                            ClassTeacherRepository classTeacherRepository,
                            ClassEnrollmentRepository classEnrollmentRepository,
                            StudentRepository studentRepository,
                            UserRepository userRepository,
                            NotificationService notificationService,
                            PermissionEvaluationService permissionEvaluationService,
                            PlatformTransactionManager transactionManager) {
        this.exerciseRepository = exerciseRepository;
        this.exerciseQuestionRepository = exerciseQuestionRepository;
        this.exerciseAssignmentRepository = exerciseAssignmentRepository;
        this.examRepository = examRepository;
        this.examClassAssignmentRepository = examClassAssignmentRepository;
        this.questionRepository = questionRepository;
        this.questionChoiceRepository = questionChoiceRepository;
        this.curriculumSubjectRepository = curriculumSubjectRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.permissionEvaluationService = permissionEvaluationService;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Main Flow bước 2: soạn Bài (DRAFT) trong 1 Đề, chọn loại SELF_PRACTICE/ASSIGNED/... */
    @Transactional
    public ExerciseResponse createExercise(CreateExerciseRequest request, Long actorUserId) {
        User actor = getUserOrThrow(actorUserId);

        Exercise exercise = new Exercise();
        exercise.setCode(request.code());
        exercise.setTitle(request.title());
        exercise.setExam(examOrThrow(request.examId()));
        if (request.subjectId() != null) {
            exercise.setSubject(curriculumSubjectOrThrow(request.subjectId()));
        }
        exercise.setExerciseType(Exercise.ExerciseType.valueOf(request.exerciseType()));
        if (request.skillCategory() != null) {
            exercise.setSkillCategory(Exercise.SkillCategory.valueOf(request.skillCategory()));
        }
        exercise.setTotalPoints(request.totalPoints());
        exercise.setTimeLimitMinutes(request.timeLimitMinutes());
        exercise.setAllowRetake(request.allowRetake());
        exercise.setMaxAttempts(request.maxAttempts());
        exercise.setShowCorrectAnswers(request.showCorrectAnswers());
        if (request.passThresholdPercent() != null) {
            exercise.setPassThresholdPercent(request.passThresholdPercent());
        }
        exercise.setCreatedBy(actor);
        exercise = exerciseRepository.save(exercise);
        return toResponse(exercise, List.of());
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04 — sửa lại thông tin 1 Bài đã soạn
     * (trước đây chỉ tạo được, không sửa được nữa). Không sửa code/examId/exerciseType (cố định từ
     * lúc tạo, giống quy ước ExamService#updateExam) — không giới hạn theo status, mirror updateExam.
     */
    @Transactional
    public ExerciseResponse updateExercise(Long id, UpdateExerciseRequest request, Long actorUserId) {
        Exercise exercise = getExerciseOrThrow(id);
        exercise.setTitle(request.title());
        exercise.setSubject(request.subjectId() == null ? null : curriculumSubjectOrThrow(request.subjectId()));
        exercise.setTotalPoints(request.totalPoints());
        exercise.setAllowRetake(request.allowRetake());
        exercise.setMaxAttempts(request.allowRetake() ? request.maxAttempts() : null);
        exercise.setShowCorrectAnswers(request.showCorrectAnswers());
        if (request.passThresholdPercent() != null) {
            exercise.setPassThresholdPercent(request.passThresholdPercent());
        }
        exercise = exerciseRepository.save(exercise);
        return toResponse(exercise, exerciseQuestionRepository.findByExerciseIdOrderByDisplayOrder(id));
    }

    /**
     * V87 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04) — "Xóa Bài" = lưu trữ
     * (status=ARCHIVED, đã có sẵn trong enum từ đầu nhưng chưa từng có đường gọi tới). KHÔNG xóa cứng
     * vì exercise_questions/exercise_assignments/exercise_attempts/student_answers có thể đã tham
     * chiếu (dữ liệu bài làm thật của học sinh). ARCHIVED tự động chặn học sinh xem/làm tiếp qua
     * {@link #requireCanViewExercise} (yêu cầu status=PUBLISHED) — không cần sửa gì thêm ở đó;
     * {@link #listByExam} cũng đã lọc bỏ Bài ARCHIVED khỏi danh sách GV xem trong Kho đề.
     */
    @Transactional
    public void deleteExercise(Long id, Long actorUserId) {
        Exercise exercise = getExerciseOrThrow(id);
        exercise.setStatus(Exercise.Status.ARCHIVED);
        exerciseRepository.save(exercise);
    }

    /** Main Flow bước 1: gắn câu hỏi (từ ngân hàng hoặc vừa soạn) vào đề. */
    @Transactional
    public ExerciseQuestionResponse addQuestion(Long exerciseId, AddExerciseQuestionRequest request, Long actorUserId) {
        Exercise exercise = getExerciseOrThrow(exerciseId);
        Question question = questionRepository.findById(request.questionId())
                .orElseThrow(() -> new ResourceNotFoundException("error.exercise.questionNotFound",
                        new Object[]{request.questionId()}, "Không tìm thấy câu hỏi id=" + request.questionId()));
        if (!question.getQuestionBank().getId().equals(exercise.getExam().getQuestionBank().getId())) {
            throw new IllegalArgumentException(
                    "Câu hỏi này không thuộc Đề đang chọn.");
        }
        if (exerciseQuestionRepository.existsByExerciseIdAndQuestionId(exerciseId, request.questionId())) {
            throw new IllegalArgumentException("Câu hỏi này đã có trong đề rồi.");
        }
        requireWithinTotalPoints(exercise, exerciseQuestionRepository.findByExerciseIdOrderByDisplayOrder(exerciseId),
                null, request.points());

        ExerciseQuestion eq = new ExerciseQuestion();
        eq.setExercise(exercise);
        eq.setQuestion(question);
        eq.setDisplayOrder(request.displayOrder());
        eq.setPoints(request.points());
        eq = exerciseQuestionRepository.save(eq);
        return toResponse(eq);
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-18 — cho phép Giáo viên sửa lại điểm
     * của 1 câu hỏi ĐÃ gắn vào Bài (trước đây chỉ set 1 lần lúc gắn, không sửa lại được). Mirror
     * removeQuestion: chỉ sửa được khi Bài còn DRAFT (tránh đổi điểm sau khi đã Publish/học sinh có
     * thể đã làm bài, ảnh hưởng ngược lại điểm đã chấm).
     */
    @Transactional
    public ExerciseQuestionResponse updateQuestionPoints(Long exerciseId, Long exerciseQuestionId,
                                                           UpdateExerciseQuestionPointsRequest request, Long actorUserId) {
        Exercise exercise = getExerciseOrThrow(exerciseId);
        if (exercise.getStatus() != Exercise.Status.DRAFT) {
            throw new IllegalArgumentException(
                    "Đề này đã Publish — không sửa điểm câu hỏi được nữa, chỉ sửa được khi còn Nháp (DRAFT).");
        }
        List<ExerciseQuestion> questions = exerciseQuestionRepository.findByExerciseIdOrderByDisplayOrder(exerciseId);
        ExerciseQuestion eq = questions.stream().filter(q -> q.getId().equals(exerciseQuestionId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("error.exercise.questionNotFoundInExercise",
                        new Object[]{exerciseQuestionId, exerciseId},
                        "Không tìm thấy câu hỏi id=" + exerciseQuestionId + " trong đề id=" + exerciseId));
        requireWithinTotalPoints(exercise, questions, exerciseQuestionId, request.points());
        eq.setPoints(request.points());
        eq = exerciseQuestionRepository.save(eq);
        return toResponse(eq);
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-18 — tổng điểm các câu hỏi trong 1
     * Bài không được vượt quá {@code exercises.total_points} đã setup lúc tạo/sửa Bài.
     * {@code excludeQuestionId} dùng khi SỬA điểm 1 câu đã có sẵn — loại điểm cũ của chính câu đó ra
     * khỏi tổng trước khi cộng điểm mới vào, tránh đếm trùng.
     */
    private void requireWithinTotalPoints(Exercise exercise, List<ExerciseQuestion> existingQuestions,
                                           Long excludeQuestionId, BigDecimal newPoints) {
        BigDecimal currentTotal = existingQuestions.stream()
                .filter(q -> excludeQuestionId == null || !q.getId().equals(excludeQuestionId))
                .map(ExerciseQuestion::getPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal newTotal = currentTotal.add(newPoints);
        if (newTotal.compareTo(exercise.getTotalPoints()) > 0) {
            throw new IllegalArgumentException(
                    "Tổng điểm câu hỏi (" + newTotal + ") vượt quá điểm tổng đã setup cho Bài (" + exercise.getTotalPoints() + ").");
        }
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — gỡ 1
     * câu hỏi khỏi Bài. Chỉ cho phép khi Bài còn DRAFT (chưa Publish) —
     * theo đúng quyết định đã chốt, tránh gỡ câu hỏi khỏi Bài đã giao cho
     * học sinh làm (có thể đã có StudentAnswer cho câu đó).
     */
    @Transactional
    public void removeQuestion(Long exerciseId, Long exerciseQuestionId, Long actorUserId) {
        Exercise exercise = getExerciseOrThrow(exerciseId);
        if (exercise.getStatus() != Exercise.Status.DRAFT) {
            throw new IllegalArgumentException(
                    "Đề này đã Publish — không gỡ câu hỏi được nữa, chỉ gỡ được khi còn Nháp (DRAFT).");
        }
        ExerciseQuestion eq = exerciseQuestionRepository.findById(exerciseQuestionId)
                .orElseThrow(() -> new ResourceNotFoundException("error.exercise.questionNotFoundSimple",
                        new Object[]{exerciseQuestionId}, "Không tìm thấy câu hỏi id=" + exerciseQuestionId + " trong đề."));
        if (!eq.getExercise().getId().equals(exerciseId)) {
            throw new ResourceNotFoundException("error.exercise.questionNotFoundInExercise",
                    new Object[]{exerciseQuestionId, exerciseId},
                    "Không tìm thấy câu hỏi id=" + exerciseQuestionId + " trong đề id=" + exerciseId);
        }
        exerciseQuestionRepository.delete(eq);
    }

    /** UC-24/UC-27: HS chỉ xem được đề ASSIGNED nếu có assignment ACTIVE khớp lớp đang học; SELF_PRACTICE/MOCK_TEST/SKILL_PRACTICE mở tự do khi đã PUBLISHED. Actor không phải học sinh (GV/Staff) xem được mọi đề. */
    @Transactional(readOnly = true)
    public ExerciseResponse getExercise(Long id, Long actorUserId) {
        Exercise exercise = getExerciseOrThrow(id);
        requireCanViewExercise(exercise, actorUserId);
        List<ExerciseQuestion> questions = exerciseQuestionRepository.findByExerciseIdOrderByDisplayOrder(id);
        return toResponse(exercise, questions);
    }

    @Transactional(readOnly = true)
    public List<ExerciseQuestionResponse> listQuestions(Long exerciseId, Long actorUserId) {
        Exercise exercise = getExerciseOrThrow(exerciseId);
        requireCanViewExercise(exercise, actorUserId);
        return exerciseQuestionRepository.findByExerciseIdOrderByDisplayOrder(exerciseId).stream()
                .map(this::toResponse).toList();
    }

    /** Bổ sung: GV xem lại danh sách đề đã giao cho 1 lớp (trước đây có repo method nhưng không controller/service nào gọi). */
    @Transactional(readOnly = true)
    public List<ExerciseAssignmentResponse> listAssignmentsForClass(Long classId, Long actorUserId) {
        requireAssignedTeacher(classId, actorUserId);
        return exerciseAssignmentRepository.findBySchoolClassIdAndStatus(classId, ExerciseAssignment.Status.ACTIVE)
                .stream().map(this::toResponse).toList();
    }

    /**
     * Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * 2026-07-30): danh sách Bài đã Publish, thuộc 1 Đề đã gán cho lớp —
     * nguồn cho dropdown "BTVN buổi sau" ở Nhận xét học viên (UC-21). ÁP
     * DỤNG CHO MỌI exerciseType (không riêng ASSIGNED như trước Kho đề).
     */
    @Transactional(readOnly = true)
    public List<ExerciseResponse> listPublishedForClass(Long classId, Long actorUserId) {
        requireAssignedTeacher(classId, actorUserId);
        return exerciseRepository.findAvailableForClass(classId, Exercise.Status.PUBLISHED)
                .stream()
                .map(e -> toResponse(e, exerciseQuestionRepository.findByExerciseIdOrderByDisplayOrder(e.getId())))
                .toList();
    }

    /** Kho đề — danh sách Bài (mọi status, kể cả DRAFT) thuộc 1 Đề, để GV tự quản lý trong ExamController. */
    @Transactional(readOnly = true)
    public List<ExerciseResponse> listByExam(Long examId, Long actorUserId) {
        return exerciseRepository.findByExamId(examId).stream()
                .filter(e -> e.getStatus() != Exercise.Status.ARCHIVED)
                .map(e -> toResponse(e, exerciseQuestionRepository.findByExerciseIdOrderByDisplayOrder(e.getId())))
                .toList();
    }

    /** Main Flow bước 4 (SELF_PRACTICE): xác nhận lưu đề, không cần giao lớp. */
    @Transactional
    public ExerciseResponse publishExercise(Long id, Long actorUserId) {
        Exercise exercise = getExerciseOrThrow(id);
        exercise.setStatus(Exercise.Status.PUBLISHED);
        exercise = exerciseRepository.save(exercise);
        return toResponse(exercise, exerciseQuestionRepository.findByExerciseIdOrderByDisplayOrder(id));
    }

    /**
     * V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
     * giao Bài cho TOÀN BỘ học sinh ACTIVE của 1 lớp (không còn
     * target_student_ids cá nhân hóa — luôn cả lớp), publish Bài, thông
     * báo học sinh. Gọi TỪ {@code StudentCommentService} khi Giáo viên
     * chọn Bài này làm "BTVN buổi sau" — KHÔNG expose qua Controller,
     * requireAssignedTeacher vẫn là rào chặn duy nhất (đúng tinh thần
     * assignExercise cũ, chỉ khác điểm gọi).
     *
     * Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * 2026-07-30): bỏ hẳn rào exerciseType==ASSIGNED — ÁP DỤNG CHO MỌI
     * loại đề. Thay bằng rào phạm vi mới: Đề của Bài này phải đã được gán
     * cho lớp (mirror ReviewVideoService#deliverToClass's inScope check).
     *
     * V70 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31,
     * fix bug thông báo bị gửi lặp N lần): "Gửi nhận xét" hàng loạt cho
     * nhiều học sinh CÙNG buổi, CÙNG chọn 1 Bài → FE gửi N request riêng
     * biệt, mỗi request gọi ĐÚNG đây với CÙNG (exerciseId, classId,
     * dueAt) — dueAt tính từ buổi học (resolveNextSessionDueAt), giống
     * hệt nhau cho mọi học sinh trong cùng buổi. Nếu đã có 1 lần giao
     * ACTIVE khớp CHÍNH XÁC (Bài, lớp, dueAt) — request TRÙNG LẶP trong
     * CÙNG 1 đợt gửi — tái dùng nguyên bản ghi đó, KHÔNG tạo mới, KHÔNG
     * gọi lại notifyAssignedStudents (tránh N thông báo giống hệt nhau
     * cho toàn bộ học sinh lớp). Mirror ReviewVideoService#deliverToClass.
     *
     * V71 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-03,
     * fix race condition của chính cơ chế chống trùng V70): xem Javadoc
     * chi tiết ở ReviewVideoService#deliverToClass — check-rồi-insert ở
     * tầng ứng dụng KHÔNG atomic, đã tái hiện thực tế 2 request đồng thời
     * cùng tạo được 2 bản giao trùng. UNIQUE index DB (exercise_id,
     * class_id, due_at) WHERE status='ACTIVE' làm chốt chặn cuối cùng;
     * INSERT chạy trong giao dịch lồng PROPAGATION_REQUIRES_NEW để thua
     * race chỉ rollback giao dịch con, không kéo theo giao dịch ngoài.
     */
    @Transactional
    public ExerciseAssignment deliverToClass(Long exerciseId, Long classId, OffsetDateTime dueAt, Long actorUserId) {
        return deliverToClass(exerciseId, classId, dueAt, actorUserId, null);
    }

    /**
     * V123 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-14): overload nhận thêm buổi
     * học nguồn (xem Javadoc {@link ExerciseAssignment#getSourceClassSession()}) — chỉ
     * StudentCommentService (đường "BTVN buổi sau", có sẵn ClassSession trong scope) truyền vào;
     * mọi caller khác (test, tương lai nếu có endpoint giao thủ công lại) dùng overload 4 tham số ở
     * trên, sourceClassSession để NULL.
     */
    public ExerciseAssignment deliverToClass(Long exerciseId, Long classId, OffsetDateTime dueAt, Long actorUserId,
                                              ClassSession sourceClassSession) {
        // Cắt về độ chính xác microsecond NGAY từ đầu — cột due_at (TIMESTAMPTZ) của Postgres chỉ lưu
        // tới microsecond, còn OffsetDateTime.now() ở tầng gọi có thể mang độ chính xác nanosecond
        // (phát hiện thực tế 2026-08-06, tái hiện được cả khi chạy 1 mình với DB sạch — KHÔNG phải
        // lỗi rò rỉ dữ liệu giữa các test). Kết hợp với sameDueAt() bên dưới (so theo instant thực,
        // không so cả offset) để so sánh đáng tin cậy giữa dueAt gốc trong bộ nhớ và bản đã
        // round-trip qua DB (JDBC trả TIMESTAMPTZ về offset UTC "Z", khác offset hệ thống VD
        // "+07:00" mà OffsetDateTime.now() tạo ra — cùng 1 thời điểm nhưng Objects.equals() coi là
        // khác nhau vì so cả offset).
        dueAt = dueAt == null ? null : dueAt.truncatedTo(ChronoUnit.MICROS);
        Exercise exercise = getExerciseOrThrow(exerciseId);
        requireAssignedTeacher(classId, actorUserId);
        SchoolClass schoolClass = getClassOrThrow(classId);
        if (schoolClass.getStatus() != SchoolClass.Status.IN_PROGRESS) {
            throw new IllegalStateException("Lớp học \"" + schoolClass.getName()
                    + "\" đang ở trạng thái " + schoolClass.getStatus()
                    + " — chỉ được phép giao bài cho lớp học đang ở trạng thái Đang học.");
        }
        User actor = getUserOrThrow(actorUserId);
        if (!examClassAssignmentRepository.existsByExamIdAndSchoolClassId(exercise.getExam().getId(), classId)) {
            throw new IllegalArgumentException(
                    "Đề của bài này chưa được gán cho lớp — vào Kho đề để gán trước.");
        }

        OffsetDateTime finalDueAt = dueAt;
        Long sourceSessionId = sourceClassSession == null ? null : sourceClassSession.getId();
        List<ExerciseAssignment> activeForExerciseAndClass = exerciseAssignmentRepository
                .findByExerciseIdAndSchoolClassIdAndStatus(exerciseId, classId, ExerciseAssignment.Status.ACTIVE);
        // V128 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — đảo ngược 1 phần quyết
        // định 2026-08-06 ngay bên dưới: "giao lại = huỷ bản cũ + giao bản mới" giờ CHỈ áp dụng khi giao
        // lại từ ĐÚNG CÙNG buổi Nhận xét nguồn (sourceClassSession) — sửa lựa chọn trong lúc còn soạn 1
        // buổi vẫn hành xử như cũ. Giao CÙNG 1 Bài từ 2 buổi Nhận xét KHÁC NHAU (VD buổi trước + buổi
        // sau đều chọn lại đúng Bài đó) giờ là 2 bài tập ĐỘC LẬP, chấm điểm riêng — không đụng bản giao
        // của buổi kia. Lọc về đúng phạm vi buổi nguồn TRƯỚC khi áp toàn bộ logic sameSession/cancel bên
        // dưới, các bản giao từ buổi khác coi như không tồn tại ở đây.
        List<ExerciseAssignment> activeFromSameSession = activeForExerciseAndClass.stream()
                .filter(a -> java.util.Objects.equals(a.getSourceClassSession() == null ? null : a.getSourceClassSession().getId(), sourceSessionId))
                .toList();
        var sameSession = activeFromSameSession.stream().filter(a -> sameDueAt(a.getDueAt(), finalDueAt)).findFirst();
        if (sameSession.isPresent()) {
            return sameSession.get();
        }
        // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — fix bug thật: giao lại (dueAt
        // KHÁC lần giao trước) không hủy bản giao ACTIVE cũ, để lại NHIỀU bản giao ACTIVE cùng lúc cho
        // cùng (Bài, lớp) — học sinh mở "BTVN" thấy điểm/trạng thái của lượt làm CŨ dán nhầm lên bản
        // giao MỚI (toAssignedResponse lấy "lượt gần nhất" theo exerciseId+studentId, không phân biệt
        // bản giao nào). Mirror ĐÚNG cơ chế đã có sẵn ở ReviewVideoService#deliverToClass (V69): "giao
        // lại = 1 lượt MỚI" — hủy mọi bản giao ACTIVE cũ CÙNG buổi nguồn trước khi tạo bản giao mới
        // (V128: chỉ còn "cùng buổi nguồn" — xem ghi chú ở activeFromSameSession).
        activeFromSameSession.forEach(this::cancelAssignment);

        ExerciseAssignment assignment;
        try {
            assignment = requiresNewTransactionTemplate.execute(status -> {
                ExerciseAssignment a = new ExerciseAssignment();
                a.setExercise(exercise);
                a.setSchoolClass(schoolClass);
                a.setAssignedBy(actor);
                a.setDueAt(finalDueAt);
                a.setSourceClassSession(sourceClassSession);
                return exerciseAssignmentRepository.saveAndFlush(a);
            });
        } catch (DataIntegrityViolationException e) {
            // Race condition (V71) — xem Javadoc ReviewVideoService#deliverToClass. Đọc lại bản ghi đã
            // thắng, KHÔNG tạo mới/không báo lại. V128: lọc thêm theo đúng buổi nguồn, tránh vô tình
            // khớp nhầm bản giao (Bài, lớp, dueAt trùng ngẫu nhiên) của 1 buổi Nhận xét khác.
            return exerciseAssignmentRepository
                    .findByExerciseIdAndSchoolClassIdAndStatus(exerciseId, classId, ExerciseAssignment.Status.ACTIVE)
                    .stream()
                    .filter(a -> java.util.Objects.equals(a.getSourceClassSession() == null ? null : a.getSourceClassSession().getId(), sourceSessionId))
                    .filter(a -> sameDueAt(a.getDueAt(), finalDueAt)).findFirst()
                    .orElseThrow(() -> e);
        }

        exercise.setStatus(Exercise.Status.PUBLISHED);
        exerciseRepository.save(exercise);

        notifyAssignedStudents(schoolClass, exercise, assignment);
        return assignment;
    }

    /**
     * So 2 due_at theo INSTANT thực (isEqual), không so cả offset — TIMESTAMPTZ round-trip qua
     * JDBC trả về offset UTC "Z" trong khi OffsetDateTime.now() ở tầng gọi mang offset hệ thống
     * (VD "+07:00"); Objects.equals() coi 2 giá trị cùng 1 thời điểm nhưng khác offset là KHÁC
     * nhau, khiến sameSession không bao giờ khớp dù buổi trùng nhau.
     */
    private static boolean sameDueAt(OffsetDateTime a, OffsetDateTime b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.isEqual(b);
    }

    /** Hủy 1 bản giao (VD Giáo viên đổi lựa chọn "BTVN buổi sau" ở Nhận xét khi comment còn DRAFT — V65). */
    @Transactional
    public void cancelAssignment(ExerciseAssignment assignment) {
        assignment.setStatus(ExerciseAssignment.Status.CANCELLED);
        exerciseAssignmentRepository.save(assignment);
    }

    // ===================== Helpers =====================

    private void notifyAssignedStudents(SchoolClass schoolClass, Exercise exercise, ExerciseAssignment assignment) {
        List<ClassEnrollment> enrollments = classEnrollmentRepository
                .findBySchoolClassIdAndStatus(schoolClass.getId(), ClassEnrollment.Status.ACTIVE);
        String title = "Bài kiểm tra mới được giao";
        String content = "Đề \"" + exercise.getTitle() + "\" đã được giao cho lớp " + schoolClass.getName() + ".";
        for (ClassEnrollment enrollment : enrollments) {
            notificationService.notify(enrollment.getStudent().getUser().getId(),
                    Notification.NotificationType.OTHER, title, content,
                    null, "EXERCISE_ASSIGNMENT", assignment.getId(),
                    Notification.Priority.NORMAL, null);
        }
    }

    /** Quyền lms.exam.manage (V107) vượt rào — quản trị viên thao tác Bài của lớp bất kỳ, không cần được phân công dạy. */
    private void requireAssignedTeacher(Long classId, Long actorUserId) {
        if (permissionEvaluationService.hasPermission(actorUserId, PERM_EXAM_MANAGE)) {
            return;
        }
        if (!classTeacherRepository.existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(classId, actorUserId)) {
            throw new NotAssignedTeacherForClassException(
                    "error.notAssignedTeacherForClass.default", new Object[]{}, "Bạn không được phân công giảng dạy lớp này.");
        }
    }

    /**
     * Actor không phải học sinh (GV/Staff — đã qua @PreAuthorize
     * lms.exercise.x / lms.question-bank.x ở phần lệnh khác) luôn xem
     * được; HS chỉ xem Bài mình được phép làm.
     *
     * Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * 2026-07-30): bỏ nhánh rẽ theo exerciseType — MỌI loại đề (kể cả
     * SELF_PRACTICE/MOCK_TEST/SKILL_PRACTICE) giờ đều cần có
     * ExerciseAssignment ACTIVE khớp lớp đang học, không còn "mở tự do
     * sau khi Publish" (mirror ReviewVideoService#requireStudentCanViewSet
     * đã làm vậy cho CONNECTION/REFLEX từ V65).
     */
    private void requireCanViewExercise(Exercise exercise, Long actorUserId) {
        var student = studentRepository.findByUserId(actorUserId);
        if (student.isEmpty()) {
            return; // staff/teacher/admin — không hạn chế thêm ở đây
        }
        if (exercise.getStatus() != Exercise.Status.PUBLISHED) {
            throw new ResourceNotFoundException("error.exercise.notFoundById",
                    new Object[]{exercise.getId()}, "Không tìm thấy đề id=" + exercise.getId());
        }
        // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06: chấp nhận CẢ ACTIVE lẫn
        // COMPLETED (không chỉ ACTIVE) — mirror ExerciseAttemptService#listMyAssignedExercises (V92).
        // Trước đây học sinh ĐÃ ĐẠT (bản giao tự đóng COMPLETED, xem applyPassOutcome) bị chặn xem lại
        // chính đề mình vừa làm/xem đáp án, dù danh sách BTVN vẫn hiện đúng bài đó.
        boolean hasVisibleAssignment = classEnrollmentRepository.findByStudentId(student.get().getId()).stream()
                .filter(e -> e.getStatus() == ClassEnrollment.Status.ACTIVE)
                .anyMatch(e -> !exerciseAssignmentRepository.findByExerciseIdAndSchoolClassIdAndStatus(
                        exercise.getId(), e.getSchoolClass().getId(), ExerciseAssignment.Status.ACTIVE).isEmpty()
                        || !exerciseAssignmentRepository.findByExerciseIdAndSchoolClassIdAndStatus(
                        exercise.getId(), e.getSchoolClass().getId(), ExerciseAssignment.Status.COMPLETED).isEmpty());
        if (!hasVisibleAssignment) {
            throw new ResourceNotFoundException("error.exercise.notFoundById",
                    new Object[]{exercise.getId()}, "Không tìm thấy đề id=" + exercise.getId());
        }
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.exercise.userNotFound",
                        new Object[]{id}, "Không tìm thấy tài khoản id=" + id));
    }

    /** V87 — không lộ Đề đã "xóa" (deleted_at), cùng pattern ExamService#getExamOrThrow. */
    private Exam examOrThrow(Long id) {
        return examRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.exercise.examNotFound",
                        new Object[]{id}, "Không tìm thấy Đề id=" + id));
    }

    private CurriculumSubject curriculumSubjectOrThrow(Long id) {
        return curriculumSubjectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.exercise.subjectNotFound",
                        new Object[]{id}, "Không tìm thấy học phần id=" + id));
    }

    private SchoolClass getClassOrThrow(Long id) {
        return schoolClassRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.exercise.classNotFound",
                        new Object[]{id}, "Không tìm thấy lớp học id=" + id));
    }

    private Exercise getExerciseOrThrow(Long id) {
        return exerciseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.exercise.notFoundById",
                        new Object[]{id}, "Không tìm thấy đề id=" + id));
    }

    private ExerciseResponse toResponse(Exercise e, List<ExerciseQuestion> questions) {
        boolean hasEssayOrSpeaking = questions.stream().anyMatch(eq ->
                eq.getQuestion().getQuestionType() == Question.QuestionType.ESSAY
                        || eq.getQuestion().getQuestionType() == Question.QuestionType.SPEAKING);
        return new ExerciseResponse(
                e.getId(), e.getUuid(), e.getCode(), e.getTitle(),
                e.getExam().getId(), e.getExam().getCode(), e.getExam().getTitle(), e.getExam().getTeacherType().name(),
                e.getSubject() == null ? null : e.getSubject().getId(),
                e.getExerciseType().name(), e.getSkillCategory() == null ? null : e.getSkillCategory().name(),
                e.getTotalPoints(), e.getTimeLimitMinutes(), e.isAllowRetake(),
                e.getMaxAttempts(), e.isShowCorrectAnswers(), e.getPassThresholdPercent(), e.getStatus().name(),
                e.getCreatedBy().getId(), hasEssayOrSpeaking);
    }

    private ExerciseQuestionResponse toResponse(ExerciseQuestion eq) {
        Question question = eq.getQuestion();
        List<ExerciseQuestionChoiceResponse> choices = CHOICE_BASED_TYPES.contains(question.getQuestionType())
                ? questionChoiceRepository.findByQuestionIdOrderByDisplayOrder(question.getId()).stream()
                        .map(this::toChoiceResponse).toList()
                : List.of();
        // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06: referencePassage của câu Nghe
        // (skill=LISTENING) là transcript/đáp án gợi ý — trước đây trả không điều kiện ở đây khiến học
        // sinh đọc được ngay qua DevTools/Network dù FE chưa render ra UI. Giờ chỉ lộ qua endpoint
        // GET /api/attempts/{id}/listening-hint sau khi đã nghe hết đủ số lần cấu hình (xem
        // ListeningHintService). Giữ nguyên hành vi cũ cho câu KHÔNG phải LISTENING (VD đoạn văn Đọc
        // hiểu — Lưới cần hiện ngay, không phải đáp án).
        String referencePassage = question.getSkill() == Question.Skill.LISTENING ? null : question.getReferencePassage();
        return new ExerciseQuestionResponse(
                eq.getId(), eq.getExercise().getId(), question.getId(),
                question.getQuestionType().name(), question.getContent(),
                eq.getDisplayOrder(), eq.getPoints(), choices,
                question.getSkill() == null ? null : question.getSkill().name(),
                question.getAudioUrl(), referencePassage,
                shuffledStructuredContent(question), question.getGroupKey());
    }

    /**
     * V85 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04) — QUAN TRỌNG: Question.
     * structuredContent của WORD_BANK/SENTENCE_BUILDING lưu ĐÚNG thứ tự đáp án (chính là đáp án
     * đúng). Endpoint này (GET /api/exercises/{id}/questions) là endpoint HỌC SINH gọi để làm bài
     * (TakeExerciseModal) — TUYỆT ĐỐI không được trả nguyên thứ tự gốc, dù UI có tự xáo trộn hiển thị
     * (network response vẫn lộ qua devtools). Trả về bản sao đã xáo trộn ngẫu nhiên mỗi lần gọi —
     * word bank/khối câu chỉ cần lộ TẬP hợp từ, không lộ thứ tự đúng. Cùng nguyên tắc với choices ở
     * trên (không kèm isCorrect) — xem Javadoc ExerciseQuestionResponse.
     */
    private Map<String, Object> shuffledStructuredContent(Question question) {
        Map<String, Object> raw = question.getStructuredContent();
        if (raw == null) {
            return null;
        }
        Map<String, Object> shuffled = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (entry.getValue() instanceof List<?> list) {
                List<Object> copy = new ArrayList<>(list);
                Collections.shuffle(copy);
                shuffled.put(entry.getKey(), copy);
            } else {
                shuffled.put(entry.getKey(), entry.getValue());
            }
        }
        return shuffled;
    }

    /** Map phương án cho HS chọn — KHÔNG lộ is_correct (xem ExerciseQuestionChoiceResponse). */
    private ExerciseQuestionChoiceResponse toChoiceResponse(QuestionChoice c) {
        return new ExerciseQuestionChoiceResponse(c.getId(), c.getChoiceLabel(), c.getContent(), c.getDisplayOrder());
    }

    private ExerciseAssignmentResponse toResponse(ExerciseAssignment a) {
        return new ExerciseAssignmentResponse(
                a.getId(), a.getUuid(), a.getExercise().getId(), a.getExercise().getTitle(), a.getExercise().getCode(),
                a.getSchoolClass().getId(), a.getAssignedBy().getId(),
                a.getAvailableFrom(), a.getDueAt(), a.isLateSubmissionAllowed(), a.getLatePenaltyPercent(),
                a.getTargetStudentIds(), a.getStatus().name());
    }
}
