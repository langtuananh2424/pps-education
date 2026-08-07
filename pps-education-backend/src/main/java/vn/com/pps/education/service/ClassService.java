package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AcademicYear;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ClassEnrollmentHistory;
import vn.com.pps.education.domain.ClassHistory;
import vn.com.pps.education.domain.ClassTeacher;
import vn.com.pps.education.domain.ClassTeacherHistory;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.CurriculumSubject;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.SiteTeacher;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import java.util.ArrayList;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassEnrollmentResponse;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.ClassTeacherResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.EndTeacherAssignmentRequest;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.PromoteClassRequest;
import vn.com.pps.education.dto.PromoteClassResponse;
import vn.com.pps.education.dto.UpdateClassRequest;
import vn.com.pps.education.dto.WithdrawEnrollmentRequest;
import vn.com.pps.education.exception.ClassEnrollmentAlreadyActiveException;
import vn.com.pps.education.exception.CurriculumNotActiveException;
import vn.com.pps.education.exception.CurriculumNotAvailableForSiteException;
import vn.com.pps.education.exception.DuplicateClassCodeException;
import vn.com.pps.education.exception.LinkedClassRequiresPartnerSiteException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.AcademicYearRepository;
import vn.com.pps.education.repository.ClassEnrollmentHistoryRepository;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ClassHistoryRepository;
import vn.com.pps.education.repository.ClassTeacherHistoryRepository;
import vn.com.pps.education.repository.ClassTeacherRepository;
import vn.com.pps.education.repository.CurriculumRepository;
import vn.com.pps.education.repository.CurriculumSubjectRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.SiteTeacherRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
    private final AcademicYearRepository academicYearRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final ClassHistoryRepository classHistoryRepository;
    private final ClassTeacherHistoryRepository classTeacherHistoryRepository;
    private final ClassEnrollmentHistoryRepository classEnrollmentHistoryRepository;
    private final CurriculumRepository curriculumRepository;
    private final CurriculumSubjectRepository curriculumSubjectRepository;
    private final SiteRepository siteRepository;
    private final SiteTeacherRepository siteTeacherRepository;
    private final SiteManagerRepository siteManagerRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final PermissionEvaluationService permissionEvaluationService;

    public ClassService(SchoolClassRepository schoolClassRepository,
                         AcademicYearRepository academicYearRepository,
                         ClassTeacherRepository classTeacherRepository,
                         ClassEnrollmentRepository classEnrollmentRepository,
                         ClassHistoryRepository classHistoryRepository,
                         ClassTeacherHistoryRepository classTeacherHistoryRepository,
                         ClassEnrollmentHistoryRepository classEnrollmentHistoryRepository,
                         CurriculumRepository curriculumRepository,
                         CurriculumSubjectRepository curriculumSubjectRepository,
                         SiteRepository siteRepository,
                         SiteTeacherRepository siteTeacherRepository,
                         SiteManagerRepository siteManagerRepository,
                         StudentRepository studentRepository,
                         UserRepository userRepository,
                         PermissionEvaluationService permissionEvaluationService) {
        this.schoolClassRepository = schoolClassRepository;
        this.academicYearRepository = academicYearRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.classHistoryRepository = classHistoryRepository;
        this.classTeacherHistoryRepository = classTeacherHistoryRepository;
        this.classEnrollmentHistoryRepository = classEnrollmentHistoryRepository;
        this.curriculumRepository = curriculumRepository;
        this.curriculumSubjectRepository = curriculumSubjectRepository;
        this.siteRepository = siteRepository;
        this.siteTeacherRepository = siteTeacherRepository;
        this.siteManagerRepository = siteManagerRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.permissionEvaluationService = permissionEvaluationService;
    }

    /**
     * Hỗ trợ dropdown FE: lọc lớp theo trường (siteId) và/hoặc chương trình
     * (curriculumId/classCategory). Nếu actor không có quyền
     * academic.class.manage (VD giáo viên), chỉ trả về lớp thuộc (các) điểm
     * trường actor đang được gán qua site_teachers (bổ sung ngoài SDD gốc,
     * đã xác nhận với người dùng — xem docs/sdd-groups/03-co-so-vat-chat-and-diem-truong.md).
     */
    @Transactional(readOnly = true)
    public List<ClassResponse> search(String query, Long siteId, Long curriculumId, String classCategory,
                                       Long academicYearId, Long actorUserId) {
        List<Long> allowedSiteIds = resolveAllowedSiteIds(actorUserId);
        boolean restrictSites = allowedSiteIds != null;
        // Luôn truyền 1 list cụ thể (không bao giờ null/rỗng) cho tham số IN — tránh
        // vấn đề Hibernate không xử lý được collection param null/rỗng; site id âm
        // không tồn tại thật nên khi restrict mà giáo viên chưa được gán site nào,
        // truy vấn tự nhiên trả rỗng.
        List<Long> siteIdsForQuery = allowedSiteIds == null || allowedSiteIds.isEmpty() ? List.of(-1L) : allowedSiteIds;
        List<SchoolClass> classes = query == null || query.isBlank()
                ? schoolClassRepository.search(siteId, curriculumId, classCategory, academicYearId, restrictSites, siteIdsForQuery)
                : schoolClassRepository.searchByQuery(query.trim(), siteId, curriculumId, classCategory, academicYearId, restrictSites, siteIdsForQuery);
        return classes.stream().map(this::toResponse).toList();
    }

    /**
     * null = không giới hạn (actor có academic.class.manage HOẶC
     * academic.class.view-all); danh sách rỗng = không thấy lớp nào. Hợp
     * nhất site_teachers (Giáo viên) VÀ site_managers (Quản lý điểm trường
     * — bổ sung ngoài SDD gốc, đã xác nhận với người dùng; trước đây bỏ sót
     * khiến Quản lý điểm trường không kiêm giáo viên luôn nhận danh sách
     * rỗng dù có quyền xem lớp thuộc site mình phụ trách, khớp Precondition
     * UC-19 "Quản lý điểm trường phụ trách đúng điểm trường của lớp").
     *
     * academic.class.view-all (V64, bổ sung ngoài SDD gốc, đã xác nhận với
     * người dùng 2026-07-30) — permission RIÊNG cho "được xem mọi lớp",
     * tách khỏi academic.class.manage (UC-18, ý nghĩa gốc là "được xếp
     * lớp") để không phải cấp nhầm quyền thao tác UC-18 chỉ để xem danh
     * sách lớp ở các màn khác (Sổ điểm/Điểm danh/Nhận xét/Soạn & giao đề).
     */
    private List<Long> resolveAllowedSiteIds(Long actorUserId) {
        if (permissionEvaluationService.hasPermission(actorUserId, "academic.class.manage")
                || permissionEvaluationService.hasPermission(actorUserId, "academic.class.view-all")) {
            return null;
        }
        return Stream.concat(
                siteTeacherRepository.findByTeacherIdAndAssignedToIsNull(actorUserId).stream()
                        .map(st -> st.getSite().getId()),
                siteManagerRepository.findByUserIdAndRoleTypeAndAssignedToIsNull(actorUserId, SiteManager.RoleType.SITE_MANAGER).stream()
                        .map(sm -> sm.getSite().getId()))
                .distinct().toList();
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
        schoolClass.setAcademicYear(resolveAcademicYear(request.academicYearId()));
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
        schoolClass.setAcademicYear(resolveAcademicYear(request.academicYearId()));
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

        ensureTeacherAssignedToSite(schoolClass.getSite(), teacher, actor);
        return toResponse(classTeacher);
    }

    /**
     * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng, xem
     * docs/sdd-groups/03-co-so-vat-chat-and-diem-truong.md): khi gán giáo
     * viên vào 1 lớp, tự động tạo liên kết giáo viên↔site của lớp đó qua
     * site_teachers nếu chưa có — im lặng bỏ qua nếu đã có sẵn.
     */
    private void ensureTeacherAssignedToSite(Site site, User teacher, User actor) {
        if (siteTeacherRepository.existsBySiteIdAndTeacherIdAndAssignedToIsNull(site.getId(), teacher.getId())) {
            return;
        }
        SiteTeacher link = new SiteTeacher();
        link.setSite(site);
        link.setTeacher(teacher);
        link.setAssignedFrom(LocalDate.now());
        link.setAssignedBy(actor);
        siteTeacherRepository.save(link);
    }

    /**
     * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-07-31, UC-18)
     * — kết thúc phụ trách của 1 giáo viên với lớp (giáo viên lớp đổi
     * theo kỳ). Không xóa cứng bản ghi `class_teachers` — chỉ đặt
     * `assignedTo`, giữ nguyên lịch sử phụ trách trước đó (mirror cách
     * `StudentService.recordTransfer` không xóa `ClassEnrollment` cũ).
     */
    @Transactional
    public ClassTeacherResponse endTeacherAssignment(Long classId, Long classTeacherId,
                                                        EndTeacherAssignmentRequest request, Long actorUserId) {
        getClassOrThrow(classId);
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));
        ClassTeacher classTeacher = classTeacherRepository.findById(classTeacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phân công giáo viên id=" + classTeacherId));
        if (!classTeacher.getSchoolClass().getId().equals(classId)) {
            throw new ResourceNotFoundException("Không tìm thấy phân công giáo viên id=" + classTeacherId + " trong lớp id=" + classId);
        }
        if (classTeacher.getAssignedTo() != null) {
            throw new IllegalArgumentException("Phân công giáo viên id=" + classTeacherId + " đã kết thúc từ " + classTeacher.getAssignedTo());
        }

        classTeacher.setAssignedTo(request.assignedTo());
        classTeacher = classTeacherRepository.save(classTeacher);

        ClassTeacherHistory history = new ClassTeacherHistory();
        history.setClassTeacher(classTeacher);
        history.setChangedBy(actor);
        history.setAction(ClassTeacherHistory.Action.UPDATED);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("assignedTo", classTeacher.getAssignedTo().toString());
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
        enrollment.setAcademicYear(schoolClass.getAcademicYear());
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

    /**
     * Chuyển lớp hàng loạt cuối năm học (bổ sung ngoài SDD gốc, đã xác nhận
     * với người dùng 2026-08-07) — model theo StudentService.recordTransfer
     * (UC-13 A2: đánh dấu ghi danh cũ TRANSFERRED, tạo ghi danh mới ACTIVE)
     * áp dụng lặp lại cho toàn bộ học sinh ACTIVE (Student.status=ACTIVE +
     * ClassEnrollment.status=ACTIVE) của 1 lớp. Lớp mới giữ nguyên site +
     * classType của lớp cũ; classCode/name/curriculum/academicYear/ngày
     * tháng/sĩ số nhập tay qua request. Giáo viên KHÔNG copy sang lớp mới.
     * Học sinh không ở trạng thái ACTIVE bị bỏ lại (skip, ghi rõ lý do).
     */
    @Transactional
    public PromoteClassResponse promoteClass(Long oldClassId, PromoteClassRequest request, Long actorUserId) {
        SchoolClass oldClass = getClassOrThrow(oldClassId);

        if (schoolClassRepository.findByClassCode(request.classCode()).isPresent()) {
            throw new DuplicateClassCodeException("Mã lớp đã tồn tại: " + request.classCode());
        }
        AcademicYear academicYear = academicYearRepository.findById(request.academicYearId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy năm học id=" + request.academicYearId()));
        Curriculum newCurriculum = curriculumRepository.findByIdAndDeletedAtIsNull(request.curriculumId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khung chương trình id=" + request.curriculumId()));
        if (newCurriculum.getStatus() != Curriculum.Status.ACTIVE) {
            throw new CurriculumNotActiveException(
                    "Khung chương trình id=" + newCurriculum.getId() + " chưa ở trạng thái ACTIVE.");
        }
        // UC-17 Postcondition -- khung tùy biến (site_id NOT NULL) chỉ dùng được cho đúng điểm trường đó (site giữ nguyên từ lớp cũ).
        if (newCurriculum.getSite() != null && !newCurriculum.getSite().getId().equals(oldClass.getSite().getId())) {
            throw new CurriculumNotAvailableForSiteException(
                    "Khung chương trình id=" + newCurriculum.getId() + " chỉ áp dụng cho điểm trường id="
                            + newCurriculum.getSite().getId() + ", không dùng được cho điểm trường id=" + oldClass.getSite().getId() + ".");
        }

        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        SchoolClass newClass = new SchoolClass();
        newClass.setClassCode(request.classCode());
        newClass.setName(request.name());
        newClass.setSite(oldClass.getSite());
        newClass.setCurriculum(newCurriculum);
        newClass.setClassType(oldClass.getClassType());
        newClass.setClassCategory(newCurriculum.getClassCategory().name());
        newClass.setMaxStudents(request.maxStudents());
        newClass.setMinStudents(request.minStudents());
        newClass.setStartDate(request.startDate());
        newClass.setEndDate(request.endDate());
        newClass.setAcademicYear(academicYear);
        newClass.setCreatedBy(actor);
        newClass = schoolClassRepository.save(newClass);
        writeClassHistory(newClass, actor, ClassHistory.Action.CREATED);

        List<ClassEnrollment> activeEnrollments = classEnrollmentRepository
                .findBySchoolClassIdAndStatus(oldClassId, ClassEnrollment.Status.ACTIVE);

        int movedCount = 0;
        List<PromoteClassResponse.SkippedStudentInfo> skipped = new ArrayList<>();
        for (ClassEnrollment oldEnrollment : activeEnrollments) {
            Student student = oldEnrollment.getStudent();
            if (student.getStatus() != Student.Status.ACTIVE) {
                skipped.add(new PromoteClassResponse.SkippedStudentInfo(
                        student.getId(), student.getStudentCode(), student.getUser().getFullName(),
                        "Học sinh không ở trạng thái ACTIVE: " + student.getStatus()));
                continue;
            }

            oldEnrollment.setStatus(ClassEnrollment.Status.TRANSFERRED);
            oldEnrollment.setWithdrawnDate(request.startDate());
            oldEnrollment.setWithdrawReason("Chuyển lớp hàng loạt lên " + newClass.getClassCode());
            oldEnrollment = classEnrollmentRepository.save(oldEnrollment);
            writeEnrollmentHistory(oldEnrollment, actor, ClassEnrollmentHistory.Action.UPDATED);

            ClassEnrollment newEnrollment = new ClassEnrollment();
            newEnrollment.setSchoolClass(newClass);
            newEnrollment.setStudent(student);
            newEnrollment.setEnrolledDate(request.startDate());
            newEnrollment.setEnrolledBy(actor);
            newEnrollment.setAcademicYear(newClass.getAcademicYear());
            newEnrollment = classEnrollmentRepository.save(newEnrollment);
            writeEnrollmentHistory(newEnrollment, actor, ClassEnrollmentHistory.Action.CREATED);

            movedCount++;
        }

        return new PromoteClassResponse(toResponse(newClass), oldClassId, movedCount, skipped.size(), skipped);
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

    /** academicYearId nullable — CreateClassRequest/UpdateClassRequest vẫn để tùy chọn như trước V103. */
    private AcademicYear resolveAcademicYear(Long academicYearId) {
        if (academicYearId == null) {
            return null;
        }
        return academicYearRepository.findById(academicYearId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy năm học id=" + academicYearId));
    }

    private ClassResponse toResponse(SchoolClass c) {
        return new ClassResponse(
                c.getId(), c.getClassCode(), c.getName(),
                c.getSite().getId(), c.getSite().getName(),
                c.getCurriculum().getId(), c.getCurriculum().getCode(),
                c.getClassType().name(), c.getClassCategory(),
                c.getMaxStudents(), c.getMinStudents(), c.getStartDate(), c.getEndDate(),
                c.getAcademicYear() == null ? null : c.getAcademicYear().getId(),
                c.getAcademicYear() == null ? null : c.getAcademicYear().getCode(),
                c.getStatus().name());
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
                e.getStudent().getStudentCode(), e.getStudent().getDateOfBirth(), e.getEnrolledDate(), e.getWithdrawnDate(),
                e.getStatus().name(), e.getWithdrawReason(),
                e.getAcademicYear() == null ? null : e.getAcademicYear().getId(),
                e.getAcademicYear() == null ? null : e.getAcademicYear().getCode());
    }
}
