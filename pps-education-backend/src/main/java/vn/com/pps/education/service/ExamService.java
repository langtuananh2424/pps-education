package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.Exam;
import vn.com.pps.education.domain.ExamClassAssignment;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.QuestionBank;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateExamRequest;
import vn.com.pps.education.dto.ExamResponse;
import vn.com.pps.education.dto.ExerciseResponse;
import vn.com.pps.education.dto.UpdateExamRequest;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ClassTeacherRepository;
import vn.com.pps.education.repository.CurriculumRepository;
import vn.com.pps.education.repository.ExamClassAssignmentRepository;
import vn.com.pps.education.repository.ExamRepository;
import vn.com.pps.education.repository.QuestionBankRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Kho đề — 2 cấp Đề/Bài (UC-40, bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng 2026-07-30). "Đề" (Exam, VD: IELTS Grade 6) gán 1 khung
 * chương trình CHỈ để lọc/tìm kiếm trong Kho đề, gán được NHIỀU lớp
 * (ExamClassAssignment) — đây mới là điều kiện hiển thị DUY NHẤT cho học
 * sinh xem/làm được các "Bài" ({@link vn.com.pps.education.domain.Exercise},
 * quản lý ở ExerciseService — Service này KHÔNG lặp lại logic map Exercise,
 * ủy quyền cho ExerciseService#listByExam, xem .claude/rules/solid.md mục
 * D). Xem thêm Javadoc ExerciseService (đổi requireCanViewExercise/
 * deliverToClass tương ứng).
 *
 * createExam/updateExam/assignToClass/unassignFromClass (TEACHER) qua
 * @PreAuthorize("hasPermission(null,'lms.exam.create/update/assign')") ở
 * ExamController (Hybrid PBAC).
 */
@Service
public class ExamService {

    private final ExamRepository examRepository;
    private final ExamClassAssignmentRepository examClassAssignmentRepository;
    private final QuestionBankRepository questionBankRepository;
    private final ExerciseService exerciseService;
    private final CurriculumRepository curriculumRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final UserRepository userRepository;
    private final PermissionEvaluationService permissionEvaluationService;

    private static final String PERM_EXAM_MANAGE = "lms.exam.manage";

    public ExamService(ExamRepository examRepository,
                        ExamClassAssignmentRepository examClassAssignmentRepository,
                        QuestionBankRepository questionBankRepository,
                        ExerciseService exerciseService,
                        CurriculumRepository curriculumRepository,
                        SchoolClassRepository schoolClassRepository,
                        ClassTeacherRepository classTeacherRepository,
                        UserRepository userRepository,
                        PermissionEvaluationService permissionEvaluationService) {
        this.examRepository = examRepository;
        this.examClassAssignmentRepository = examClassAssignmentRepository;
        this.questionBankRepository = questionBankRepository;
        this.exerciseService = exerciseService;
        this.curriculumRepository = curriculumRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.userRepository = userRepository;
        this.permissionEvaluationService = permissionEvaluationService;
    }

    @Transactional
    public ExamResponse createExam(CreateExamRequest request, Long actorUserId) {
        User actor = getUserOrThrow(actorUserId);
        Curriculum curriculum = curriculumOrThrow(request.curriculumId());

        Exam exam = new Exam();
        exam.setCode(request.code());
        exam.setTitle(request.title());
        exam.setCurriculum(curriculum);
        exam.setCreatedBy(actor);
        exam.setTeacherType(Exam.TeacherType.valueOf(request.teacherType()));
        exam.setExamType(Exam.ExamType.valueOf(request.examType()));

        // V75: UUID đã sinh ngay khi new Exam(), nên tạo bank trước để INSERT
        // Exam luôn có FK NOT NULL — lỗi bất kỳ rollback cả bank lẫn Exam.
        QuestionBank bank = new QuestionBank();
        bank.setCode("EXAM-" + exam.getUuid());
        bank.setName("Câu hỏi nội bộ - " + exam.getCode());
        bank.setCurriculum(curriculum);
        bank = questionBankRepository.save(bank);
        exam.setQuestionBank(bank);

        exam = examRepository.save(exam);
        return toResponse(exam);
    }

    /**
     * Sửa tiêu đề + teacherType/examType (V74, đã xác nhận với người dùng
     * 2026-08-04) — khung chương trình bất biến sau khi tạo (xem Javadoc
     * UpdateExamRequest).
     */
    @Transactional
    public ExamResponse updateExam(Long id, UpdateExamRequest request, Long actorUserId) {
        Exam exam = getExamOrThrow(id);
        exam.setTitle(request.title());
        exam.setTeacherType(Exam.TeacherType.valueOf(request.teacherType()));
        exam.setExamType(Exam.ExamType.valueOf(request.examType()));
        exam = examRepository.save(exam);
        return toResponse(exam);
    }

    @Transactional(readOnly = true)
    public ExamResponse getExam(Long id, Long actorUserId) {
        return toResponse(getExamOrThrow(id));
    }

    /** teacherType (VIETNAMESE/FOREIGN) tùy chọn — lọc theo GV VN/nước ngoài khi giao bài (V74, đã xác nhận với người dùng 2026-08-04). */
    @Transactional(readOnly = true)
    public List<ExamResponse> listExams(Long curriculumId, String teacherType, Long actorUserId) {
        Exam.TeacherType type = teacherType == null ? null : Exam.TeacherType.valueOf(teacherType);
        List<Exam> exams;
        if (curriculumId != null && type != null) {
            exams = examRepository.findByCurriculumIdAndTeacherTypeAndDeletedAtIsNull(curriculumId, type);
        } else if (curriculumId != null) {
            exams = examRepository.findByCurriculumIdAndDeletedAtIsNull(curriculumId);
        } else if (type != null) {
            exams = examRepository.findByTeacherTypeAndDeletedAtIsNull(type);
        } else {
            exams = examRepository.findByDeletedAtIsNull();
        }
        return exams.stream().map(this::toResponse).toList();
    }

    /** Danh sách "Bài" thuộc 1 Đề — mọi status (kể cả DRAFT), GV tự quản lý. */
    @Transactional(readOnly = true)
    public List<ExerciseResponse> listExercises(Long examId, Long actorUserId) {
        getExamOrThrow(examId);
        return exerciseService.listByExam(examId, actorUserId);
    }

    /** Gán Đề cho 1 lớp — điều kiện hiển thị DUY NHẤT cho học sinh lớp đó (xem Javadoc lớp). Idempotent — gán lại không lỗi. */
    @Transactional
    public void assignToClass(Long examId, Long classId, Long actorUserId) {
        Exam exam = getExamOrThrow(examId);
        requireAssignedTeacher(classId, actorUserId);
        SchoolClass schoolClass = getClassOrThrow(classId);
        if (examClassAssignmentRepository.existsByExamIdAndSchoolClassId(examId, classId)) {
            return;
        }
        User actor = getUserOrThrow(actorUserId);
        ExamClassAssignment assignment = new ExamClassAssignment();
        assignment.setExam(exam);
        assignment.setSchoolClass(schoolClass);
        assignment.setAssignedBy(actor);
        examClassAssignmentRepository.save(assignment);
    }

    /** Gỡ Đề khỏi 1 lớp — xóa cứng (join thuần, không phải bản giao cần lưu lịch sử, xem Javadoc ExamClassAssignment). */
    @Transactional
    public void unassignFromClass(Long examId, Long classId, Long actorUserId) {
        getExamOrThrow(examId);
        requireAssignedTeacher(classId, actorUserId);
        examClassAssignmentRepository.findByExamIdAndSchoolClassId(examId, classId)
                .ifPresent(examClassAssignmentRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<ClassResponse> listAssignedClasses(Long examId, Long actorUserId) {
        getExamOrThrow(examId);
        return examClassAssignmentRepository.findByExamId(examId).stream()
                .map(a -> toResponse(a.getSchoolClass()))
                .toList();
    }

    /**
     * V87 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04) — "Xóa Đề": soft-delete qua
     * deleted_at (không xóa cứng — Bài thuộc Đề có thể đã có exercise_assignments/exercise_attempts/
     * student_answers, dữ liệu bài làm thật của học sinh). Chỉ xóa được khi mọi Bài thuộc Đề đã được
     * "xóa" (lưu trữ) trước — {@link ExerciseService#listByExam} đã tự lọc bỏ Bài ARCHIVED, nên rỗng
     * nghĩa là không còn Bài nào đang hoạt động. Gỡ luôn mọi exam_class_assignments — Đề đã xóa không
     * còn hiện ở dropdown "gán lớp" để giao Bài mới (không ảnh hưởng bài đã giao/đang làm dở, xem
     * Javadoc ExerciseService#requireCanViewExercise — chỉ dựa vào ExerciseAssignment, không re-check
     * lại exam_class_assignments mỗi lần học sinh mở bài).
     */
    @Transactional
    public void deleteExam(Long id, Long actorUserId) {
        Exam exam = getExamOrThrow(id);
        if (!exerciseService.listByExam(id, actorUserId).isEmpty()) {
            throw new IllegalArgumentException(
                    "Đề này còn Bài chưa lưu trữ — lưu trữ (xóa) hết Bài trước khi xóa Đề.");
        }
        examClassAssignmentRepository.deleteAll(examClassAssignmentRepository.findByExamId(id));
        exam.setDeletedAt(OffsetDateTime.now());
        examRepository.save(exam);
    }

    // ===================== Helpers =====================

    /** Quyền lms.exam.manage (V107) vượt rào — quản trị viên gán/gỡ Đề cho lớp bất kỳ, không cần được phân công dạy. */
    private void requireAssignedTeacher(Long classId, Long actorUserId) {
        if (permissionEvaluationService.hasPermission(actorUserId, PERM_EXAM_MANAGE)) {
            return;
        }
        if (!classTeacherRepository.existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(classId, actorUserId)) {
            throw new NotAssignedTeacherForClassException(
                    "Bạn không được phân công giảng dạy lớp này.");
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

    private SchoolClass getClassOrThrow(Long id) {
        return schoolClassRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học id=" + id));
    }

    private Exam getExamOrThrow(Long id) {
        return examRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Đề id=" + id));
    }

    private ExamResponse toResponse(Exam exam) {
        return new ExamResponse(exam.getId(), exam.getUuid(), exam.getCode(), exam.getTitle(),
                exam.getCurriculum().getId(), exam.getCurriculum().getCode(), exam.getCreatedBy().getId(),
                exam.getTeacherType().name(), exam.getExamType().name(), exam.getQuestionBank().getId());
    }

    private ClassResponse toResponse(SchoolClass c) {
        return new ClassResponse(c.getId(), c.getClassCode(), c.getName(),
                c.getSite().getId(), c.getSite().getName(),
                c.getCurriculum().getId(), c.getCurriculum().getCode(),
                c.getClassType().name(), c.getClassCategory(),
                c.getMaxStudents(), c.getMinStudents(), c.getStartDate(), c.getEndDate(),
                c.getAcademicYear() == null ? null : c.getAcademicYear().getId(),
                c.getAcademicYear() == null ? null : c.getAcademicYear().getCode(),
                c.getStatus().name());
    }
}
