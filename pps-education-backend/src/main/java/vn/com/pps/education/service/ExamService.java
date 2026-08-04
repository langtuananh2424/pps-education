package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.Exam;
import vn.com.pps.education.domain.ExamClassAssignment;
import vn.com.pps.education.domain.SchoolClass;
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
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.UserRepository;

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
    private final ExerciseService exerciseService;
    private final CurriculumRepository curriculumRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final UserRepository userRepository;

    public ExamService(ExamRepository examRepository,
                        ExamClassAssignmentRepository examClassAssignmentRepository,
                        ExerciseService exerciseService,
                        CurriculumRepository curriculumRepository,
                        SchoolClassRepository schoolClassRepository,
                        ClassTeacherRepository classTeacherRepository,
                        UserRepository userRepository) {
        this.examRepository = examRepository;
        this.examClassAssignmentRepository = examClassAssignmentRepository;
        this.exerciseService = exerciseService;
        this.curriculumRepository = curriculumRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.userRepository = userRepository;
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
            exams = examRepository.findByCurriculumIdAndTeacherType(curriculumId, type);
        } else if (curriculumId != null) {
            exams = examRepository.findByCurriculumId(curriculumId);
        } else if (type != null) {
            exams = examRepository.findByTeacherType(type);
        } else {
            exams = examRepository.findAll();
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

    // ===================== Helpers =====================

    private void requireAssignedTeacher(Long classId, Long actorUserId) {
        if (!classTeacherRepository.existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(classId, actorUserId)) {
            throw new NotAssignedTeacherForClassException(
                    "Tài khoản id=" + actorUserId + " không được phân công giảng dạy lớp id=" + classId + ".");
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
        return examRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Đề id=" + id));
    }

    private ExamResponse toResponse(Exam exam) {
        return new ExamResponse(exam.getId(), exam.getUuid(), exam.getCode(), exam.getTitle(),
                exam.getCurriculum().getId(), exam.getCurriculum().getCode(), exam.getCreatedBy().getId(),
                exam.getTeacherType().name(), exam.getExamType().name());
    }

    private ClassResponse toResponse(SchoolClass c) {
        return new ClassResponse(c.getId(), c.getClassCode(), c.getName(),
                c.getSite().getId(), c.getSite().getName(),
                c.getCurriculum().getId(), c.getCurriculum().getCode(),
                c.getClassType().name(), c.getClassCategory(),
                c.getMaxStudents(), c.getMinStudents(), c.getStartDate(), c.getEndDate(),
                c.getAcademicYear(), c.getStatus().name());
    }
}
