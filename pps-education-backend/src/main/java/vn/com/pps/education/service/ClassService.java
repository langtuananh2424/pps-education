package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ClassEnrollmentHistory;
import vn.com.pps.education.domain.ClassHistory;
import vn.com.pps.education.domain.ClassTeacher;
import vn.com.pps.education.domain.ClassTeacherHistory;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.CurriculumSubject;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassEnrollmentResponse;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.ClassTeacherResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.UpdateClassRequest;
import vn.com.pps.education.dto.WithdrawEnrollmentRequest;
import vn.com.pps.education.exception.ClassEnrollmentAlreadyActiveException;
import vn.com.pps.education.exception.CurriculumNotActiveException;
import vn.com.pps.education.exception.CurriculumNotAvailableForSiteException;
import vn.com.pps.education.exception.DuplicateClassCodeException;
import vn.com.pps.education.exception.LinkedClassRequiresPartnerSiteException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ClassEnrollmentHistoryRepository;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ClassHistoryRepository;
import vn.com.pps.education.repository.ClassTeacherHistoryRepository;
import vn.com.pps.education.repository.ClassTeacherRepository;
import vn.com.pps.education.repository.CurriculumRepository;
import vn.com.pps.education.repository.CurriculumSubjectRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-18: Xếp lớp & gán khóa học (FR-ACA-02).
 * Xem docs/uc/phan-he-06-hoc-thuat.md — Main Flow bước 1-6, A2 (thiếu Điểm
 * trường cho Lớp liên kết).
 *
 * A1 (trùng phòng học, FR-FAC-03) KHÔNG áp dụng ở Service này — bảng
 * `classes` không có cột room_id (phòng học gắn với class_sessions, thuộc
 * phần "Lịch dạy & Điểm danh" chưa triển khai trong phạm vi phiên này).
 *
 * Gộp class_teachers + class_enrollments vào cùng Service với classes
 * (giống cách EmployeeService gộp Employee+Contract+Qualification —
 * cùng 1 UC-18, các bảng phụ trợ trực tiếp).
 *
 * Authorization qua @PreAuthorize("hasPermission(null,'academic.class.manage')")
 * ở ClassController (Hybrid PBAC — V28), không còn role-check trong Service.
 */
@Service
public class ClassService {

    private final SchoolClassRepository schoolClassRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final ClassHistoryRepository classHistoryRepository;
    private final ClassTeacherHistoryRepository classTeacherHistoryRepository;
    private final ClassEnrollmentHistoryRepository classEnrollmentHistoryRepository;
    private final CurriculumRepository curriculumRepository;
    private final CurriculumSubjectRepository curriculumSubjectRepository;
    private final SiteRepository siteRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    public ClassService(SchoolClassRepository schoolClassRepository,
                         ClassTeacherRepository classTeacherRepository,
                         ClassEnrollmentRepository classEnrollmentRepository,
                         ClassHistoryRepository classHistoryRepository,
                         ClassTeacherHistoryRepository classTeacherHistoryRepository,
                         ClassEnrollmentHistoryRepository classEnrollmentHistoryRepository,
                         CurriculumRepository curriculumRepository,
                         CurriculumSubjectRepository curriculumSubjectRepository,
                         SiteRepository siteRepository,
                         StudentRepository studentRepository,
                         UserRepository userRepository) {
        this.schoolClassRepository = schoolClassRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.classHistoryRepository = classHistoryRepository;
        this.classTeacherHistoryRepository = classTeacherHistoryRepository;
        this.classEnrollmentHistoryRepository = classEnrollmentHistoryRepository;
        this.curriculumRepository = curriculumRepository;
        this.curriculumSubjectRepository = curriculumSubjectRepository;
        this.siteRepository = siteRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
    }

    /** Hỗ trợ dropdown FE: lọc lớp theo trường (siteId) và/hoặc chương trình (curriculumId/classCategory). */
    @Transactional(readOnly = true)
    public List<ClassResponse> search(String query, Long siteId, Long curriculumId, String classCategory) {
        List<SchoolClass> classes = query == null || query.isBlank()
                ? schoolClassRepository.search(siteId, curriculumId, classCategory)
                : schoolClassRepository.searchByQuery(query.trim(), siteId, curriculumId, classCategory);
        return classes.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ClassResponse getById(Long id) {
        return toResponse(getClassOrThrow(id));
    }

    /** Main Flow bước 3-6, A2: khởi tạo record lớp học thực tế. */
    @Transactional
    public ClassResponse create(CreateClassRequest request, Long actorUserId) {
        if (schoolClassRepository.findByClassCode(request.classCode()).isPresent()) {
            throw new DuplicateClassCodeException("Mã lớp đã tồn tại: " + request.classCode());
        }
        Curriculum curriculum = curriculumRepository.findByIdAndDeletedAtIsNull(request.curriculumId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khung chương trình id=" + request.curriculumId()));
        if (curriculum.getStatus() != Curriculum.Status.ACTIVE) {
            throw new CurriculumNotActiveException(
                    "Khung chương trình id=" + curriculum.getId() + " chưa ở trạng thái ACTIVE.");
        }
        Site site = siteRepository.findById(request.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm trường id=" + request.siteId()));
        // UC-17 Postcondition -- khung tùy biến (site_id NOT NULL) chỉ dùng được cho đúng điểm trường đó.
        if (curriculum.getSite() != null && !curriculum.getSite().getId().equals(site.getId())) {
            throw new CurriculumNotAvailableForSiteException(
                    "Khung chương trình id=" + curriculum.getId() + " chỉ áp dụng cho điểm trường id="
                            + curriculum.getSite().getId() + ", không dùng được cho điểm trường id=" + site.getId() + ".");
        }
        SchoolClass.ClassType classType = SchoolClass.ClassType.valueOf(request.classType());

        // A2 -- Lớp liên kết bắt buộc gán Điểm trường loại Trường liên kết (PARTNER).
        if (classType == SchoolClass.ClassType.LINKED && site.getSiteType() != Site.SiteType.PARTNER) {
            throw new LinkedClassRequiresPartnerSiteException(
                    "Lớp liên kết (LINKED) bắt buộc gán Điểm trường loại PARTNER — điểm trường id=" + site.getId()
                            + " hiện là " + site.getSiteType() + ".");
        }

        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setClassCode(request.classCode());
        schoolClass.setName(request.name());
        schoolClass.setSite(site);
        schoolClass.setCurriculum(curriculum);
        schoolClass.setClassType(classType);
        schoolClass.setClassCategory(curriculum.getClassCategory().name());
        schoolClass.setMaxStudents(request.maxStudents());
        schoolClass.setMinStudents(request.minStudents());
        schoolClass.setStartDate(request.startDate());
        schoolClass.setEndDate(request.endDate());
        schoolClass.setAcademicYear(request.academicYear());
        schoolClass.setSemester(request.semester());
        schoolClass.setCreatedBy(actor);
        schoolClass = schoolClassRepository.save(schoolClass);

        writeClassHistory(schoolClass, actor, ClassHistory.Action.CREATED);
        return toResponse(schoolClass);
    }

    /** Cập nhật thông tin hành chính của lớp học đã có. */
    @Transactional
    public ClassResponse update(Long id, UpdateClassRequest request, Long actorUserId) {
        SchoolClass schoolClass = getClassOrThrow(id);
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        schoolClass.setName(request.name());
        schoolClass.setMaxStudents(request.maxStudents());
        schoolClass.setMinStudents(request.minStudents());
        schoolClass.setStartDate(request.startDate());
        schoolClass.setEndDate(request.endDate());
        schoolClass.setAcademicYear(request.academicYear());
        schoolClass.setSemester(request.semester());
        schoolClass.setStatus(SchoolClass.Status.valueOf(request.status()));
        schoolClass = schoolClassRepository.save(schoolClass);

        writeClassHistory(schoolClass, actor, ClassHistory.Action.UPDATED);
        return toResponse(schoolClass);
    }

    /** Main Flow bước 1-2: điều phối giáo viên phụ trách lớp. */
    @Transactional
    public ClassTeacherResponse assignTeacher(Long classId, AssignTeacherRequest request, Long actorUserId) {
        SchoolClass schoolClass = getClassOrThrow(classId);
        User teacher = userRepository.findById(request.teacherUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + request.teacherUserId()));
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        ClassTeacher classTeacher = new ClassTeacher();
        classTeacher.setSchoolClass(schoolClass);
        classTeacher.setTeacher(teacher);
        classTeacher.setTeacherRole(request.teacherRole() == null
                ? ClassTeacher.TeacherRole.PRIMARY
                : ClassTeacher.TeacherRole.valueOf(request.teacherRole()));
        if (request.subjectId() != null) {
            classTeacher.setSubject(curriculumSubjectOrThrow(request.subjectId()));
        }
        classTeacher.setAssignedFrom(request.assignedFrom());
        classTeacher.setAssignedBy(actor);
        classTeacher = classTeacherRepository.save(classTeacher);

        ClassTeacherHistory history = new ClassTeacherHistory();
        history.setClassTeacher(classTeacher);
        history.setChangedBy(actor);
        history.setAction(ClassTeacherHistory.Action.CREATED);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("teacherUserId", teacher.getId());
        snapshot.put("teacherRole", classTeacher.getTeacherRole().name());
        history.setDetails(snapshot);
        classTeacherHistoryRepository.save(history);

        return toResponse(classTeacher);
    }

    @Transactional(readOnly = true)
    public List<ClassTeacherResponse> listTeachers(Long classId) {
        getClassOrThrow(classId);
        return classTeacherRepository.findBySchoolClassId(classId).stream().map(this::toResponse).toList();
    }

    /** Ghi danh 1 học sinh vào lớp — nền tảng cho UC-42 (chọn lớp đang xem). */
    @Transactional
    public ClassEnrollmentResponse enroll(Long classId, EnrollStudentRequest request, Long actorUserId) {
        SchoolClass schoolClass = getClassOrThrow(classId);
        Student student = studentRepository.findByIdAndDeletedAtIsNull(request.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh id=" + request.studentId()));
        if (classEnrollmentRepository
                .findBySchoolClassIdAndStudentIdAndStatus(classId, request.studentId(), ClassEnrollment.Status.ACTIVE)
                .isPresent()) {
            throw new ClassEnrollmentAlreadyActiveException(
                    "Học sinh id=" + request.studentId() + " đã ghi danh ACTIVE trong lớp id=" + classId + ".");
        }
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        ClassEnrollment enrollment = new ClassEnrollment();
        enrollment.setSchoolClass(schoolClass);
        enrollment.setStudent(student);
        enrollment.setEnrolledDate(request.enrolledDate());
        enrollment.setEnrolledBy(actor);
        enrollment = classEnrollmentRepository.save(enrollment);

        writeEnrollmentHistory(enrollment, actor, ClassEnrollmentHistory.Action.CREATED);
        return toResponse(enrollment);
    }

    /** Rút học sinh khỏi lớp (status ACTIVE -> WITHDRAWN). */
    @Transactional
    public ClassEnrollmentResponse withdraw(Long classId, Long enrollmentId, WithdrawEnrollmentRequest request, Long actorUserId) {
        ClassEnrollment enrollment = classEnrollmentRepository.findById(enrollmentId)
                .filter(e -> e.getSchoolClass().getId().equals(classId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ghi danh id=" + enrollmentId));
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        enrollment.setStatus(ClassEnrollment.Status.WITHDRAWN);
        enrollment.setWithdrawnDate(request.withdrawnDate());
        enrollment.setWithdrawReason(request.reason());
        enrollment = classEnrollmentRepository.save(enrollment);

        writeEnrollmentHistory(enrollment, actor, ClassEnrollmentHistory.Action.UPDATED);
        return toResponse(enrollment);
    }

    @Transactional(readOnly = true)
    public List<ClassEnrollmentResponse> listEnrollments(Long classId) {
        getClassOrThrow(classId);
        return classEnrollmentRepository.findBySchoolClassId(classId).stream().map(this::toResponse).toList();
    }

    private void writeClassHistory(SchoolClass schoolClass, User actor, ClassHistory.Action action) {
        ClassHistory history = new ClassHistory();
        history.setSchoolClass(schoolClass);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("classCode", schoolClass.getClassCode());
        snapshot.put("name", schoolClass.getName());
        snapshot.put("maxStudents", schoolClass.getMaxStudents());
        snapshot.put("status", schoolClass.getStatus().name());
        history.setDetails(snapshot);
        classHistoryRepository.save(history);
    }

    private void writeEnrollmentHistory(ClassEnrollment enrollment, User actor, ClassEnrollmentHistory.Action action) {
        ClassEnrollmentHistory history = new ClassEnrollmentHistory();
        history.setClassEnrollment(enrollment);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("studentId", enrollment.getStudent().getId());
        snapshot.put("status", enrollment.getStatus().name());
        history.setDetails(snapshot);
        classEnrollmentHistoryRepository.save(history);
    }

    private CurriculumSubject curriculumSubjectOrThrow(Long id) {
        return curriculumSubjectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học phần id=" + id));
    }

    private SchoolClass getClassOrThrow(Long id) {
        return schoolClassRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học id=" + id));
    }

    private ClassResponse toResponse(SchoolClass c) {
        return new ClassResponse(
                c.getId(), c.getClassCode(), c.getName(),
                c.getSite().getId(), c.getSite().getName(),
                c.getCurriculum().getId(), c.getCurriculum().getCode(),
                c.getClassType().name(), c.getClassCategory(),
                c.getMaxStudents(), c.getMinStudents(), c.getStartDate(), c.getEndDate(),
                c.getAcademicYear(), c.getSemester(), c.getStatus().name());
    }

    private ClassTeacherResponse toResponse(ClassTeacher t) {
        return new ClassTeacherResponse(
                t.getId(), t.getSchoolClass().getId(), t.getTeacher().getId(), t.getTeacher().getFullName(),
                t.getTeacherRole().name(), t.getSubject() == null ? null : t.getSubject().getId(),
                t.getAssignedFrom(), t.getAssignedTo());
    }

    private ClassEnrollmentResponse toResponse(ClassEnrollment e) {
        return new ClassEnrollmentResponse(
                e.getId(), e.getSchoolClass().getId(), e.getStudent().getId(), e.getStudent().getUser().getFullName(),
                e.getStudent().getStudentCode(), e.getEnrolledDate(), e.getWithdrawnDate(), e.getStatus().name(),
                e.getWithdrawReason());
    }
}
