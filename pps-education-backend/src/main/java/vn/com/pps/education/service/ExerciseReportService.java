package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.common.ExcelExportHelper;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.Exercise;
import vn.com.pps.education.domain.ExerciseAssignment;
import vn.com.pps.education.domain.ExerciseAttempt;
import vn.com.pps.education.domain.ExerciseQuestion;
import vn.com.pps.education.domain.Question;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.StudentAnswer;
import vn.com.pps.education.dto.ExerciseAssignmentQuestionStatsResponse;
import vn.com.pps.education.dto.ExerciseAssignmentStatsResponse;
import vn.com.pps.education.dto.ExerciseAssignmentStudentStatsResponse;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ClassTeacherRepository;
import vn.com.pps.education.repository.ExerciseAssignmentRepository;
import vn.com.pps.education.repository.ExerciseAttemptRepository;
import vn.com.pps.education.repository.ExerciseQuestionRepository;
import vn.com.pps.education.repository.ListeningHintEventRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.StudentAnswerRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UC-66: Thống kê BTVN theo lớp (FR-ACA-07, bổ sung ngoài SDD gốc, đã xác
 * nhận với người dùng 2026-08-05). Xem docs/uc/phan-he-06-hoc-thuat.md.
 * Thuần đọc/báo cáo cho Giáo viên/Quản lý điểm trường — KHÔNG tính lại
 * đạt/chưa đạt hay điểm số, chỉ đọc lại {@code exercise_attempts.passed}/
 * {@code total_score} đã được {@link ExerciseAttemptService#applyPassOutcome}
 * tính sẵn khi lượt làm bài được chấm xong.
 */
@Service
public class ExerciseReportService {

    private static final Set<ExerciseAssignment.Status> REPORTABLE_ASSIGNMENT_STATUSES =
            Set.of(ExerciseAssignment.Status.ACTIVE, ExerciseAssignment.Status.COMPLETED);
    private static final Set<ExerciseAttempt.Status> COMPLETED_ATTEMPT_STATUSES =
            Set.of(ExerciseAttempt.Status.SUBMITTED, ExerciseAttempt.Status.AUTO_GRADED, ExerciseAttempt.Status.FULLY_GRADED);

    private final ExerciseAssignmentRepository exerciseAssignmentRepository;
    private final ExerciseAttemptRepository exerciseAttemptRepository;
    private final StudentAnswerRepository studentAnswerRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final ExerciseQuestionRepository exerciseQuestionRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final SiteManagerRepository siteManagerRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ListeningHintEventRepository listeningHintEventRepository;

    public ExerciseReportService(ExerciseAssignmentRepository exerciseAssignmentRepository,
                                  ExerciseAttemptRepository exerciseAttemptRepository,
                                  StudentAnswerRepository studentAnswerRepository,
                                  ClassEnrollmentRepository classEnrollmentRepository,
                                  ExerciseQuestionRepository exerciseQuestionRepository,
                                  ClassTeacherRepository classTeacherRepository,
                                  SiteManagerRepository siteManagerRepository,
                                  SchoolClassRepository schoolClassRepository,
                                  ListeningHintEventRepository listeningHintEventRepository) {
        this.exerciseAssignmentRepository = exerciseAssignmentRepository;
        this.exerciseAttemptRepository = exerciseAttemptRepository;
        this.studentAnswerRepository = studentAnswerRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.exerciseQuestionRepository = exerciseQuestionRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.siteManagerRepository = siteManagerRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.listeningHintEventRepository = listeningHintEventRepository;
    }

    @Transactional(readOnly = true)
    public List<ExerciseAssignmentStatsResponse> listAssignmentStats(Long classId, Long actorUserId) {
        requireReportScope(classId, actorUserId);

        List<ExerciseAssignment> assignments =
                exerciseAssignmentRepository.findBySchoolClassIdAndStatusIn(classId, REPORTABLE_ASSIGNMENT_STATUSES);
        List<ClassEnrollment> roster = classEnrollmentRepository.findBySchoolClassIdAndStatus(classId, ClassEnrollment.Status.ACTIVE);

        return assignments.stream()
                .map(assignment -> toAssignmentStats(assignment, roster))
                .toList();
    }

    @Transactional(readOnly = true)
    public ExerciseAssignmentStudentStatsResponse getStudentStats(Long assignmentId, Long actorUserId) {
        ExerciseAssignment assignment = getAssignmentOrThrow(assignmentId);
        requireReportScope(assignment.getSchoolClass().getId(), actorUserId);

        List<ClassEnrollment> roster = rosterForAssignment(assignment);
        // V93: lấy lượt được chọn làm chính thức (nếu có), nếu không thì lấy lượt cuối cùng
        Map<Long, ExerciseAttempt> attemptByStudent = selectedOrLatestAttemptByStudent(assignment.getId());

        // Tính tổng số lần làm cho mỗi học sinh
        List<ExerciseAttempt> allAttempts = exerciseAttemptRepository.findByExerciseAssignmentIdOrderByAttemptNumberDesc(assignment.getId());
        Map<Long, Integer> numberOfAttemptsByStudent = allAttempts.stream()
                .collect(Collectors.groupingBy(a -> a.getStudent().getId(), Collectors.collectingAndThen(Collectors.toList(), list -> list.size())));

        List<ExerciseAssignmentStudentStatsResponse.StudentRow> rows = roster.stream()
                .map(enrollment -> toStudentRow(enrollment.getStudent(), attemptByStudent.get(enrollment.getStudent().getId()), assignment.getExercise(), numberOfAttemptsByStudent.getOrDefault(enrollment.getStudent().getId(), 0)))
                .toList();

        return new ExerciseAssignmentStudentStatsResponse(toAssignmentStats(assignment, roster), rows);
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — phân tích "câu hay bị sai" tính
     * theo LƯỢT LÀM ĐẦU TIÊN của mỗi học sinh (không phải lượt mới nhất): học sinh có thể làm lại
     * nhiều lượt (allowRetake) và đã xem gợi ý tapescript (xem ListeningHintService) trước khi làm
     * lại, nên lượt mới nhất trả lời đúng KHÔNG phản ánh đúng độ khó thật của câu hỏi — trước đây dùng
     * latestAttemptByStudent khiến câu học sinh trả lời sai ở lượt 1 (chưa xem gợi ý) biến mất khỏi
     * thống kê ngay khi lượt sau (đã xem gợi ý) trả lời đúng.
     */
    @Transactional(readOnly = true)
    public ExerciseAssignmentQuestionStatsResponse getQuestionStats(Long assignmentId, Long actorUserId) {
        ExerciseAssignment assignment = getAssignmentOrThrow(assignmentId);
        requireReportScope(assignment.getSchoolClass().getId(), actorUserId);

        Map<Long, Student> studentByAttemptId = firstAttemptByStudent(assignment.getId()).values().stream()
                .collect(Collectors.toMap(ExerciseAttempt::getId, ExerciseAttempt::getStudent));
        List<Long> attemptIds = studentByAttemptId.keySet().stream().toList();

        Map<Long, List<StudentAnswer>> answersByQuestionId = attemptIds.isEmpty()
                ? Map.of()
                : studentAnswerRepository.findByExerciseAttemptIdIn(attemptIds).stream()
                    .collect(Collectors.groupingBy(a -> a.getQuestion().getId()));

        List<ExerciseQuestion> questions = exerciseQuestionRepository.findByExerciseIdOrderByDisplayOrder(assignment.getExercise().getId());

        List<ExerciseAssignmentQuestionStatsResponse.QuestionRow> rows = questions.stream()
                .map(eq -> toQuestionRow(eq, answersByQuestionId.getOrDefault(eq.getQuestion().getId(), List.of()), studentByAttemptId))
                .toList();

        return new ExerciseAssignmentQuestionStatsResponse(rows);
    }

    @Transactional(readOnly = true)
    public byte[] exportStudentStatsExcel(Long assignmentId, Long actorUserId) {
        ExerciseAssignmentStudentStatsResponse stats = getStudentStats(assignmentId, actorUserId);
        ExerciseAssignmentStatsResponse header = stats.assignment();

        List<String> headers = List.of("Mã học sinh", "Họ tên", "Trạng thái", "Điểm", "Tổng điểm", "Phần trăm (%)", "Đạt");
        List<List<Object>> rows = stats.students().stream()
                .map(s -> List.<Object>of(
                        s.studentCode(),
                        s.studentFullName(),
                        vietnameseStatus(s.status()),
                        s.totalScore() != null ? s.totalScore() : "",
                        s.totalPoints() != null ? s.totalPoints() : "",
                        s.percentage() != null ? s.percentage() : "",
                        s.passed() == null ? "" : (s.passed() ? "Đạt" : "Chưa đạt")))
                .toList();

        List<String> notes = List.of(
                "Bài: " + header.exerciseTitle() + " (" + header.exerciseCode() + ")",
                "Ngày giao: " + header.availableFrom(),
                "Hạn nộp: " + (header.dueAt() == null ? "Không có hạn" : header.dueAt().toString()),
                "% hoàn thành: " + header.completionPercent(),
                "Tỷ lệ đạt: " + header.passRatePercent());

        return ExcelExportHelper.buildWorkbook("Kết quả BTVN", headers, rows, notes);
    }

    // ===================== Helpers =====================

    private ExerciseAssignmentStatsResponse toAssignmentStats(ExerciseAssignment assignment, List<ClassEnrollment> roster) {
        Exercise exercise = assignment.getExercise();
        int totalStudents = assignment.getTargetStudentIds() != null
                ? assignment.getTargetStudentIds().size() : roster.size();

        // V93: dùng lượt được chọn (nếu có) thay vì lượt cuối cùng
        Map<Long, ExerciseAttempt> attemptByStudent = selectedOrLatestAttemptByStudent(assignment.getId());
        int completedCount = (int) attemptByStudent.values().stream()
                .filter(a -> COMPLETED_ATTEMPT_STATUSES.contains(a.getStatus())).count();
        int passedCount = (int) attemptByStudent.values().stream()
                .filter(a -> Boolean.TRUE.equals(a.getPassed())).count();

        return new ExerciseAssignmentStatsResponse(
                assignment.getId(), exercise.getId(), exercise.getCode(), exercise.getTitle(),
                exercise.getExerciseType().name(), assignment.getAvailableFrom(), assignment.getDueAt(),
                assignment.getStatus().name(), totalStudents, completedCount,
                percentOf(completedCount, totalStudents), passedCount, percentOf(passedCount, totalStudents));
    }

    private ExerciseAssignmentStudentStatsResponse.StudentRow toStudentRow(Student student, ExerciseAttempt latest, Exercise exercise, int numberOfAttempts) {
        if (latest == null) {
            return new ExerciseAssignmentStudentStatsResponse.StudentRow(
                    student.getId(), student.getStudentCode(), student.getUser().getFullName(),
                    "CHUA_LAM", null, null, null, null, null, null, null, 0);
        }
        String status;
        if (latest.getStatus() == ExerciseAttempt.Status.IN_PROGRESS) {
            status = "DANG_LAM";
        } else if (latest.isLateSubmission()) {
            status = "TRE_HAN";
        } else {
            status = "DA_NOP";
        }
        BigDecimal percentage = latest.getTotalScore() == null
                ? null : ExerciseAttemptService.percentageOf(latest.getTotalScore(), exercise.getTotalPoints());
        return new ExerciseAssignmentStudentStatsResponse.StudentRow(
                student.getId(), student.getStudentCode(), student.getUser().getFullName(),
                status, latest.getTotalScore(), exercise.getTotalPoints(), percentage,
                latest.getPassed(), latest.getSubmittedAt(), latest.getAttemptNumber(), latest.getId(), numberOfAttempts);
    }

    private ExerciseAssignmentQuestionStatsResponse.QuestionRow toQuestionRow(ExerciseQuestion eq, List<StudentAnswer> answers,
                                                                               Map<Long, Student> studentByAttemptId) {
        List<StudentAnswer> graded = answers.stream().filter(a -> a.getCorrect() != null).toList();
        List<StudentAnswer> wrong = graded.stream().filter(a -> !a.getCorrect()).toList();

        List<ExerciseAssignmentQuestionStatsResponse.WrongStudent> wrongStudents = wrong.stream()
                .map(a -> studentByAttemptId.get(a.getExerciseAttempt().getId()))
                .filter(s -> s != null)
                .map(s -> new ExerciseAssignmentQuestionStatsResponse.WrongStudent(s.getId(), s.getStudentCode(), s.getUser().getFullName()))
                .toList();

        boolean isListening = eq.getQuestion().getSkill() == Question.Skill.LISTENING;
        int hintUsedCount = isListening ? (int) listeningHintEventRepository.countByQuestionId(eq.getQuestion().getId()) : 0;
        int hintUsedStudentCount = isListening ? (int) listeningHintEventRepository.countDistinctStudentByQuestionId(eq.getQuestion().getId()) : 0;

        return new ExerciseAssignmentQuestionStatsResponse.QuestionRow(
                eq.getQuestion().getId(), eq.getDisplayOrder(), eq.getQuestion().getContent(),
                eq.getQuestion().getQuestionType().name(),
                eq.getQuestion().getSkill() == null ? null : eq.getQuestion().getSkill().name(),
                graded.size(), wrong.size(), percentOf(wrong.size(), graded.size()), wrongStudents,
                hintUsedCount, hintUsedStudentCount);
    }

    private List<ClassEnrollment> rosterForAssignment(ExerciseAssignment assignment) {
        List<ClassEnrollment> roster = classEnrollmentRepository
                .findBySchoolClassIdAndStatus(assignment.getSchoolClass().getId(), ClassEnrollment.Status.ACTIVE);
        List<Long> targetStudentIds = assignment.getTargetStudentIds();
        if (targetStudentIds == null) {
            return roster;
        }
        Set<Long> targetSet = Set.copyOf(targetStudentIds);
        return roster.stream().filter(e -> targetSet.contains(e.getStudent().getId())).toList();
    }

    /** Attempt mới nhất mỗi học sinh (attemptNumber lớn nhất) — mirror ReviewVideoService.listSubmissionsForTeacher. */
    /** V93 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06): lấy lượt được chọn làm chính thức (selectedForGrading=true) nếu có, nếu không thì lấy lượt cuối cùng — tương thích với UC-24/27 khi giáo viên chọn 1 lượt làm "điểm chính thức". */
    private Map<Long, ExerciseAttempt> selectedOrLatestAttemptByStudent(Long assignmentId) {
        List<ExerciseAttempt> attempts = exerciseAttemptRepository.findByExerciseAssignmentIdOrderByAttemptNumberDesc(assignmentId);
        Map<Long, ExerciseAttempt> result = new java.util.LinkedHashMap<>();

        // Pass 1: tìm lượt được chọn (selectedForGrading=true) cho mỗi học sinh
        for (ExerciseAttempt attempt : attempts) {
            Long studentId = attempt.getStudent().getId();
            if (attempt.isSelectedForGrading()) {
                result.put(studentId, attempt);
            }
        }

        // Pass 2: nếu học sinh chưa có lượt được chọn, thêm lượt cuối cùng (attempt đầu tiên vì sorted DESC)
        for (ExerciseAttempt attempt : attempts) {
            Long studentId = attempt.getStudent().getId();
            if (!result.containsKey(studentId)) {
                result.put(studentId, attempt);
            }
        }

        return result;
    }

    private Map<Long, ExerciseAttempt> latestAttemptByStudent(Long assignmentId) {
        List<ExerciseAttempt> attempts = exerciseAttemptRepository.findByExerciseAssignmentIdOrderByAttemptNumberDesc(assignmentId);
        Map<Long, ExerciseAttempt> latest = new java.util.LinkedHashMap<>();
        for (ExerciseAttempt attempt : attempts) {
            latest.putIfAbsent(attempt.getStudent().getId(), attempt);
        }
        return latest;
    }

    /** Lượt làm ĐẦU TIÊN mỗi học sinh (attemptNumber nhỏ nhất) — dùng riêng cho getQuestionStats, xem Javadoc ở đó. */
    private Map<Long, ExerciseAttempt> firstAttemptByStudent(Long assignmentId) {
        List<ExerciseAttempt> attempts = exerciseAttemptRepository.findByExerciseAssignmentIdOrderByAttemptNumberAsc(assignmentId);
        Map<Long, ExerciseAttempt> first = new java.util.LinkedHashMap<>();
        for (ExerciseAttempt attempt : attempts) {
            first.putIfAbsent(attempt.getStudent().getId(), attempt);
        }
        return first;
    }

    private static BigDecimal percentOf(int numerator, int denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private static String vietnameseStatus(String status) {
        return switch (status) {
            case "CHUA_LAM" -> "Chưa làm";
            case "DANG_LAM" -> "Đang làm";
            case "TRE_HAN" -> "Trễ hạn";
            case "DA_NOP" -> "Đã nộp";
            default -> status;
        };
    }

    private ExerciseAssignment getAssignmentOrThrow(Long id) {
        return exerciseAssignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bản giao BTVN id=" + id));
    }

    private void requireReportScope(Long classId, Long actorUserId) {
        if (classTeacherRepository.existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(classId, actorUserId)) {
            return;
        }
        SchoolClass schoolClass = schoolClassRepository.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học id=" + classId));
        if (!siteManagerRepository.existsBySiteIdAndUserIdAndRoleTypeAndAssignedToIsNull(
                schoolClass.getSite().getId(), actorUserId, SiteManager.RoleType.SITE_MANAGER)) {
            throw new NotAssignedTeacherForClassException(
                    "Tài khoản id=" + actorUserId + " không có quyền xem thống kê BTVN của lớp id=" + classId + ".");
        }
    }
}
