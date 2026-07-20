package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.CurriculumSubject;
import vn.com.pps.education.domain.Exercise;
import vn.com.pps.education.domain.ExerciseAssignment;
import vn.com.pps.education.domain.ExerciseQuestion;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.Question;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AddExerciseQuestionRequest;
import vn.com.pps.education.dto.AssignExerciseRequest;
import vn.com.pps.education.dto.CreateExerciseRequest;
import vn.com.pps.education.dto.ExerciseAssignmentResponse;
import vn.com.pps.education.dto.ExerciseQuestionResponse;
import vn.com.pps.education.dto.ExerciseResponse;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ClassTeacherRepository;
import vn.com.pps.education.repository.CurriculumRepository;
import vn.com.pps.education.repository.CurriculumSubjectRepository;
import vn.com.pps.education.repository.ExerciseAssignmentRepository;
import vn.com.pps.education.repository.ExerciseQuestionRepository;
import vn.com.pps.education.repository.ExerciseRepository;
import vn.com.pps.education.repository.QuestionRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;

import java.util.List;

/**
 * UC-40: Soạn & giao đề kiểm tra (FR-LMS-10) — phần lắp đề + giao đề.
 * Xem docs/uc/phan-he-07-lms-portal.md. Dùng chung Question (ngân hàng)
 * từ QuestionBankService, không gộp 2 Service (xem Javadoc đó).
 *
 * createExercise/addQuestion/publishExercise (TEACHER) qua
 * @PreAuthorize("hasPermission(null,'lms.exercise.manage')") ở
 * ExerciseController (Hybrid PBAC — V28). assignExercise vẫn dùng
 * requireAssignedTeacher — row-level scope check (đúng lớp cụ thể).
 */
@Service
public class ExerciseService {

    private final ExerciseRepository exerciseRepository;
    private final ExerciseQuestionRepository exerciseQuestionRepository;
    private final ExerciseAssignmentRepository exerciseAssignmentRepository;
    private final QuestionRepository questionRepository;
    private final CurriculumRepository curriculumRepository;
    private final CurriculumSubjectRepository curriculumSubjectRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public ExerciseService(ExerciseRepository exerciseRepository,
                            ExerciseQuestionRepository exerciseQuestionRepository,
                            ExerciseAssignmentRepository exerciseAssignmentRepository,
                            QuestionRepository questionRepository,
                            CurriculumRepository curriculumRepository,
                            CurriculumSubjectRepository curriculumSubjectRepository,
                            SchoolClassRepository schoolClassRepository,
                            ClassTeacherRepository classTeacherRepository,
                            ClassEnrollmentRepository classEnrollmentRepository,
                            StudentRepository studentRepository,
                            UserRepository userRepository,
                            NotificationService notificationService) {
        this.exerciseRepository = exerciseRepository;
        this.exerciseQuestionRepository = exerciseQuestionRepository;
        this.exerciseAssignmentRepository = exerciseAssignmentRepository;
        this.questionRepository = questionRepository;
        this.curriculumRepository = curriculumRepository;
        this.curriculumSubjectRepository = curriculumSubjectRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /** Main Flow bước 2: soạn đề (DRAFT), chọn loại SELF_PRACTICE/ASSIGNED/... */
    @Transactional
    public ExerciseResponse createExercise(CreateExerciseRequest request, Long actorUserId) {
        User actor = getUserOrThrow(actorUserId);

        Exercise exercise = new Exercise();
        exercise.setCode(request.code());
        exercise.setTitle(request.title());
        if (request.curriculumId() != null) {
            exercise.setCurriculum(curriculumOrThrow(request.curriculumId()));
        }
        if (request.subjectId() != null) {
            exercise.setSubject(curriculumSubjectOrThrow(request.subjectId()));
        }
        exercise.setExerciseType(Exercise.ExerciseType.valueOf(request.exerciseType()));
        exercise.setTotalPoints(request.totalPoints());
        exercise.setTimeLimitMinutes(request.timeLimitMinutes());
        exercise.setAllowRetake(request.allowRetake());
        exercise.setMaxAttempts(request.maxAttempts());
        exercise.setShowCorrectAnswers(request.showCorrectAnswers());
        exercise.setCreatedBy(actor);
        exercise = exerciseRepository.save(exercise);
        return toResponse(exercise, List.of());
    }

    /** Main Flow bước 1: gắn câu hỏi (từ ngân hàng hoặc vừa soạn) vào đề. */
    @Transactional
    public ExerciseQuestionResponse addQuestion(Long exerciseId, AddExerciseQuestionRequest request, Long actorUserId) {
        Exercise exercise = getExerciseOrThrow(exerciseId);
        Question question = questionRepository.findById(request.questionId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy câu hỏi id=" + request.questionId()));
        if (exerciseQuestionRepository.existsByExerciseIdAndQuestionId(exerciseId, request.questionId())) {
            throw new IllegalArgumentException("Câu hỏi id=" + request.questionId() + " đã có trong đề id=" + exerciseId + ".");
        }

        ExerciseQuestion eq = new ExerciseQuestion();
        eq.setExercise(exercise);
        eq.setQuestion(question);
        eq.setDisplayOrder(request.displayOrder());
        eq.setPoints(request.points());
        eq = exerciseQuestionRepository.save(eq);
        return toResponse(eq);
    }

    /** UC-24/UC-27: HS chỉ xem được đề ASSIGNED nếu có assignment ACTIVE khớp lớp đang học; SELF_PRACTICE/MOCK_TEST/SKILL_PRACTICE mở tự do khi đã PUBLISHED. Staff (lms.exercise.manage) xem được mọi đề. */
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

    /** Main Flow bước 4 (SELF_PRACTICE): xác nhận lưu đề, không cần giao lớp. */
    @Transactional
    public ExerciseResponse publishExercise(Long id, Long actorUserId) {
        Exercise exercise = getExerciseOrThrow(id);
        exercise.setStatus(Exercise.Status.PUBLISHED);
        exercise = exerciseRepository.save(exercise);
        return toResponse(exercise, exerciseQuestionRepository.findByExerciseIdOrderByDisplayOrder(id));
    }

    /** Main Flow bước 3-4 (ASSIGNED): giao đề cho lớp kèm deadline, publish đề, thông báo Học sinh. */
    @Transactional
    public ExerciseAssignmentResponse assignExercise(Long exerciseId, AssignExerciseRequest request, Long actorUserId) {
        Exercise exercise = getExerciseOrThrow(exerciseId);
        if (exercise.getExerciseType() != Exercise.ExerciseType.ASSIGNED) {
            throw new IllegalArgumentException("Đề id=" + exerciseId + " không phải loại ASSIGNED — không giao lớp được.");
        }
        requireAssignedTeacher(request.classId(), actorUserId);
        SchoolClass schoolClass = getClassOrThrow(request.classId());
        User actor = getUserOrThrow(actorUserId);

        ExerciseAssignment assignment = new ExerciseAssignment();
        assignment.setExercise(exercise);
        assignment.setSchoolClass(schoolClass);
        assignment.setAssignedBy(actor);
        if (request.availableFrom() != null) {
            assignment.setAvailableFrom(request.availableFrom());
        }
        assignment.setDueAt(request.dueAt());
        assignment.setLateSubmissionAllowed(request.lateSubmissionAllowed());
        assignment.setLatePenaltyPercent(request.latePenaltyPercent());
        assignment.setTargetStudentIds(request.targetStudentIds());
        assignment = exerciseAssignmentRepository.save(assignment);

        exercise.setStatus(Exercise.Status.PUBLISHED);
        exerciseRepository.save(exercise);

        notifyAssignedStudents(schoolClass, exercise, assignment, request.targetStudentIds());
        return toResponse(assignment);
    }

    // ===================== Helpers =====================

    private void notifyAssignedStudents(SchoolClass schoolClass, Exercise exercise, ExerciseAssignment assignment, List<Long> targetStudentIds) {
        List<ClassEnrollment> enrollments = classEnrollmentRepository
                .findBySchoolClassIdAndStatus(schoolClass.getId(), ClassEnrollment.Status.ACTIVE);
        String title = "Bài kiểm tra mới được giao";
        String content = "Đề \"" + exercise.getTitle() + "\" đã được giao cho lớp " + schoolClass.getName()
                + (exercise.getStatus() == Exercise.Status.PUBLISHED ? "." : ".");
        for (ClassEnrollment enrollment : enrollments) {
            if (targetStudentIds != null && !targetStudentIds.contains(enrollment.getStudent().getId())) {
                continue;
            }
            notificationService.notify(enrollment.getStudent().getUser().getId(),
                    Notification.NotificationType.OTHER, title, content,
                    null, "EXERCISE_ASSIGNMENT", assignment.getId(),
                    Notification.Priority.NORMAL, null);
        }
    }

    private void requireAssignedTeacher(Long classId, Long actorUserId) {
        if (!classTeacherRepository.existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(classId, actorUserId)) {
            throw new NotAssignedTeacherForClassException(
                    "Tài khoản id=" + actorUserId + " không được phân công giảng dạy lớp id=" + classId + ".");
        }
    }

    /** Staff (đã qua @PreAuthorize lms.exercise.manage ở phần lệnh khác) luôn xem được; HS chỉ xem đề mình được phép làm. */
    private void requireCanViewExercise(Exercise exercise, Long actorUserId) {
        var student = studentRepository.findByUserId(actorUserId);
        if (student.isEmpty()) {
            return; // staff/teacher/admin — không hạn chế thêm ở đây
        }
        if (exercise.getStatus() != Exercise.Status.PUBLISHED) {
            throw new ResourceNotFoundException("Không tìm thấy đề id=" + exercise.getId());
        }
        if (exercise.getExerciseType() == Exercise.ExerciseType.ASSIGNED) {
            boolean hasActiveAssignment = classEnrollmentRepository.findByStudentId(student.get().getId()).stream()
                    .filter(e -> e.getStatus() == ClassEnrollment.Status.ACTIVE)
                    .anyMatch(e -> !exerciseAssignmentRepository.findByExerciseIdAndSchoolClassIdAndStatus(
                            exercise.getId(), e.getSchoolClass().getId(), ExerciseAssignment.Status.ACTIVE).isEmpty());
            if (!hasActiveAssignment) {
                throw new ResourceNotFoundException("Không tìm thấy đề id=" + exercise.getId());
            }
        }
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + id));
    }

    private Curriculum curriculumOrThrow(Long id) {
        return curriculumRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khung chương trình id=" + id));
    }

    private CurriculumSubject curriculumSubjectOrThrow(Long id) {
        return curriculumSubjectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học phần id=" + id));
    }

    private SchoolClass getClassOrThrow(Long id) {
        return schoolClassRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học id=" + id));
    }

    private Exercise getExerciseOrThrow(Long id) {
        return exerciseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đề id=" + id));
    }

    private ExerciseResponse toResponse(Exercise e, List<ExerciseQuestion> questions) {
        boolean hasEssayOrSpeaking = questions.stream().anyMatch(eq ->
                eq.getQuestion().getQuestionType() == Question.QuestionType.ESSAY
                        || eq.getQuestion().getQuestionType() == Question.QuestionType.SPEAKING);
        return new ExerciseResponse(
                e.getId(), e.getCode(), e.getTitle(),
                e.getCurriculum() == null ? null : e.getCurriculum().getId(),
                e.getSubject() == null ? null : e.getSubject().getId(),
                e.getExerciseType().name(), e.getTotalPoints(), e.getTimeLimitMinutes(), e.isAllowRetake(),
                e.getMaxAttempts(), e.isShowCorrectAnswers(), e.getStatus().name(), e.getCreatedBy().getId(),
                hasEssayOrSpeaking);
    }

    private ExerciseQuestionResponse toResponse(ExerciseQuestion eq) {
        return new ExerciseQuestionResponse(
                eq.getId(), eq.getExercise().getId(), eq.getQuestion().getId(),
                eq.getQuestion().getQuestionType().name(), eq.getQuestion().getContent(),
                eq.getDisplayOrder(), eq.getPoints());
    }

    private ExerciseAssignmentResponse toResponse(ExerciseAssignment a) {
        return new ExerciseAssignmentResponse(
                a.getId(), a.getExercise().getId(), a.getSchoolClass().getId(), a.getAssignedBy().getId(),
                a.getAvailableFrom(), a.getDueAt(), a.isLateSubmissionAllowed(), a.getLatePenaltyPercent(),
                a.getTargetStudentIds(), a.getStatus().name());
    }
}
