package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ClassEnrollmentHistory;
import vn.com.pps.education.domain.Parent;
import vn.com.pps.education.domain.ParentHistory;
import vn.com.pps.education.domain.ParentStudent;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.StudentHistory;
import vn.com.pps.education.domain.StudentTransferHistory;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.CreateParentRequest;
import vn.com.pps.education.dto.CreateStudentRequest;
import vn.com.pps.education.dto.LinkParentRequest;
import vn.com.pps.education.dto.ParentResponse;
import vn.com.pps.education.dto.ParentStudentResponse;
import vn.com.pps.education.dto.RecordTransferRequest;
import vn.com.pps.education.dto.StudentResponse;
import vn.com.pps.education.dto.StudentTransferHistoryResponse;
import vn.com.pps.education.dto.UpdateOwnParentProfileRequest;
import vn.com.pps.education.dto.UpdateOwnStudentProfileRequest;
import vn.com.pps.education.dto.UpdateParentRequest;
import vn.com.pps.education.dto.UpdateStudentRequest;
import vn.com.pps.education.exception.ClassEnrollmentAlreadyActiveException;
import vn.com.pps.education.exception.DuplicateStudentCodeException;
import vn.com.pps.education.exception.ParentAlreadyExistsException;
import vn.com.pps.education.exception.ParentStudentLinkAlreadyExistsException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.exception.StudentAlreadyExistsException;
import vn.com.pps.education.exception.StudentContactRoleConflictException;
import vn.com.pps.education.repository.ClassEnrollmentHistoryRepository;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ParentHistoryRepository;
import vn.com.pps.education.repository.ParentRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentHistoryRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.StudentTransferHistoryRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;

import java.time.LocalDate;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-13: Quản lý hồ sơ học sinh (FR-STU-01).
 * Xem docs/uc/phan-he-05-hoc-sinh.md — Main Flow bước 1-5.
 *
 * A1 (chuyển điểm trường khác Quản lý điểm trường phụ trách → cập nhật
 * row-level access theo điểm trường): hiện thực qua
 * {@link #resolveAllowedSiteIds(Long)} — student.profile.* được cấp cho CẢ
 * Nhân viên Giáo vụ/STAFF (không giới hạn site) và Quản lý điểm trường
 * (giới hạn theo (các) site được gán qua site_managers), cùng tập permission
 * code (V44, tách từ student.manage) nên không phân biệt được qua hasPermission
 * như ClassService — phân biệt qua có/không có bản ghi site_managers
 * role_type=SITE_MANAGER. recordTransfer() cập nhật students.primary_site_id
 * ngay khi giao dịch hoàn tất (không đổi), mọi truy vấn scoped-theo-site
 * (search/getById/update/create) đọc trực tiếp cột này.
 */
@Service
public class StudentService {

    // private static final String STUDENT_CODE_PREFIX = "HS"; // cũ: dùng cho generateStudentCode (đã comment lại bên dưới)

    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final StudentTransferHistoryRepository transferHistoryRepository;
    private final StudentHistoryRepository studentHistoryRepository;
    private final ParentHistoryRepository parentHistoryRepository;
    private final UserRepository userRepository;
    private final SiteRepository siteRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final ClassEnrollmentHistoryRepository classEnrollmentHistoryRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserAccountService userAccountService;
    private final SiteManagerRepository siteManagerRepository;

    public StudentService(StudentRepository studentRepository,
                           ParentRepository parentRepository,
                           ParentStudentRepository parentStudentRepository,
                           StudentTransferHistoryRepository transferHistoryRepository,
                           StudentHistoryRepository studentHistoryRepository,
                           ParentHistoryRepository parentHistoryRepository,
                           UserRepository userRepository,
                           SiteRepository siteRepository,
                           SchoolClassRepository schoolClassRepository,
                           ClassEnrollmentRepository classEnrollmentRepository,
                           ClassEnrollmentHistoryRepository classEnrollmentHistoryRepository,
                           RoleRepository roleRepository,
                           UserRoleRepository userRoleRepository,
                           UserAccountService userAccountService,
                           SiteManagerRepository siteManagerRepository) {
        this.studentRepository = studentRepository;
        this.parentRepository = parentRepository;
        this.parentStudentRepository = parentStudentRepository;
        this.transferHistoryRepository = transferHistoryRepository;
        this.studentHistoryRepository = studentHistoryRepository;
        this.parentHistoryRepository = parentHistoryRepository;
        this.userRepository = userRepository;
        this.siteRepository = siteRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.classEnrollmentHistoryRepository = classEnrollmentHistoryRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.userAccountService = userAccountService;
        this.siteManagerRepository = siteManagerRepository;
    }

    @Transactional(readOnly = true)
    public List<StudentResponse> search(String query, Long siteId, Long actorUserId) {
        List<Long> allowedSiteIds = resolveAllowedSiteIds(actorUserId);
        boolean restrictSites = allowedSiteIds != null;
        List<Long> siteIdsForQuery = allowedSiteIds == null || allowedSiteIds.isEmpty() ? List.of(-1L) : allowedSiteIds;
        List<Student> students = query == null || query.isBlank()
                ? studentRepository.search(siteId, restrictSites, siteIdsForQuery)
                : studentRepository.searchByQuery(query.trim(), siteId, restrictSites, siteIdsForQuery);
        return students.stream().map(this::toResponse).toList();
    }

    /** null = không giới hạn (actor là STAFF/Giáo vụ); danh sách (kể cả rỗng) = giới hạn theo site_managers. */
    private List<Long> resolveAllowedSiteIds(Long actorUserId) {
        List<SiteManager> assignments = siteManagerRepository
                .findByUserIdAndRoleTypeAndAssignedToIsNull(actorUserId, SiteManager.RoleType.SITE_MANAGER);
        return assignments.isEmpty() ? null : assignments.stream().map(sm -> sm.getSite().getId()).toList();
    }

    private void requireSiteAccessible(Student student, Long actorUserId) {
        List<Long> allowedSiteIds = resolveAllowedSiteIds(actorUserId);
        if (allowedSiteIds == null) {
            return;
        }
        Long siteId = student.getPrimarySite() == null ? null : student.getPrimarySite().getId();
        if (siteId == null || !allowedSiteIds.contains(siteId)) {
            throw new ResourceNotFoundException("Không tìm thấy học sinh id=" + student.getId());
        }
    }

    @Transactional(readOnly = true)
    public StudentResponse getById(Long id, Long actorUserId) {
        Student student = getStudentOrThrow(id);
        requireSiteAccessible(student, actorUserId);
        return toResponse(student);
    }

    /**
     * Package-private — dùng bởi StudentProfileService (API tổng hợp hồ sơ
     * học tập, FR-REP-04, bổ sung ngoài SDD gốc) để tái dùng ĐÚNG rào
     * site-scope của getById (STAFF/Giáo vụ không giới hạn, Quản lý điểm
     * trường chỉ xem học sinh thuộc site mình phụ trách) — tránh trùng lặp
     * logic requireSiteAccessible ở 2 nơi.
     */
    @Transactional(readOnly = true)
    Student getAccessibleStudentOrThrow(Long id, Long actorUserId) {
        Student student = getStudentOrThrow(id);
        requireSiteAccessible(student, actorUserId);
        return student;
    }

    /** UC-63 Main Flow bước 1: học sinh tự xem hồ sơ của chính mình (FR-USR-07). */
    @Transactional(readOnly = true)
    public StudentResponse getMyStudentProfile(Long userId) {
        return toResponse(getStudentByUserIdOrThrow(userId));
    }

    /**
     * UC-63 Main Flow bước 2-4: học sinh tự cập nhật ảnh đại diện của
     * chính mình (FR-USR-07). Chỉ portraitUrl — không tái dùng update()
     * (dành cho UC-13, Nhân viên Giáo vụ sửa toàn bộ hồ sơ học vụ). Xem
     * docs/uc/phan-he-02-phan-quyen.md.
     */
    @Transactional
    public StudentResponse updateMyStudentProfile(Long userId, UpdateOwnStudentProfileRequest request) {
        Student student = getStudentByUserIdOrThrow(userId);
        student.setPortraitUrl(request.portraitUrl());
        student = studentRepository.save(student);

        writeStudentHistory(student, userId, StudentHistory.Action.UPDATED);
        return toResponse(student);
    }

    /**
     * Main Flow bước 1-3: khởi tạo hồ sơ học sinh mới. Nhận ĐÚNG 1 trong 2:
     * userId (tài khoản có sẵn) hoặc newAccount (tạo tài khoản kèm hồ sơ
     * trong cùng transaction, gán role STUDENT — theo mẫu EmployeeService.create).
     * student_code do người dùng tự nhập.
     */
    @Transactional
    public StudentResponse create(CreateStudentRequest request, Long actorUserId) {
        if ((request.userId() == null) == (request.newAccount() == null)) {
            throw new IllegalArgumentException("Cung cấp đúng 1 trong 2: userId (tài khoản có sẵn) hoặc newAccount (tạo tài khoản mới).");
        }
        List<Long> allowedSiteIds = resolveAllowedSiteIds(actorUserId);
        if (allowedSiteIds != null && (request.primarySiteId() == null || !allowedSiteIds.contains(request.primarySiteId()))) {
            throw new NotSiteManagerForSiteException(
                    "Tài khoản id=" + actorUserId + " không được gán phụ trách điểm trường id=" + request.primarySiteId() + ".");
        }
        User user;
        if (request.userId() != null) {
            user = userRepository.findById(request.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + request.userId()));
        } else {
            user = userAccountService.createAccount(request.newAccount());
            assignRole(user, "STUDENT", actorUserId);
        }
        if (studentRepository.findByUserId(user.getId()).isPresent()) {
            throw new StudentAlreadyExistsException("Tài khoản id=" + user.getId() + " đã có hồ sơ học sinh.");
        }

        // A-mới -- mã học sinh do người dùng tự nhập, phải duy nhất (đổi từ tự sinh sang nhập tay, theo yêu cầu).
        if (studentRepository.findByStudentCode(request.studentCode()).isPresent()) {
            throw new DuplicateStudentCodeException("Mã học sinh đã tồn tại: " + request.studentCode());
        }

        Student student = new Student();
        student.setUser(user);
        // student.setStudentCode(generateStudentCode(request.enrollmentDate())); // cũ: hệ thống tự sinh mã học sinh
        student.setStudentCode(request.studentCode());
        student.setDateOfBirth(request.dateOfBirth());
        student.setGender(request.gender() == null ? null : Student.Gender.valueOf(request.gender()));
        student.setPortraitUrl(request.portraitUrl());
        if (request.primarySiteId() != null) {
            student.setPrimarySite(getSiteOrThrow(request.primarySiteId()));
        }
        student.setOriginalSchool(request.originalSchool());
        student.setOriginalClass(request.originalClass());
        student.setEnrollmentDate(request.enrollmentDate());
        student.setNotes(request.notes());
        student = studentRepository.save(student);

        writeStudentHistory(student, actorUserId, StudentHistory.Action.CREATED);
        return toResponse(student);
    }

    /** Main Flow bước 2: cập nhật thông tin cá nhân của hồ sơ đã có, giữ lịch sử phiên bản (bước 5). */
    @Transactional
    public StudentResponse update(Long id, UpdateStudentRequest request, Long actorUserId) {
        Student student = getStudentOrThrow(id);
        requireSiteAccessible(student, actorUserId);
        student.setDateOfBirth(request.dateOfBirth());
        student.setGender(request.gender() == null ? null : Student.Gender.valueOf(request.gender()));
        student.setPortraitUrl(request.portraitUrl());
        student.setOriginalSchool(request.originalSchool());
        student.setOriginalClass(request.originalClass());
        student.setNotes(request.notes());
        student = studentRepository.save(student);

        writeStudentHistory(student, actorUserId, StudentHistory.Action.UPDATED);
        return toResponse(student);
    }

    /**
     * Main Flow bước 2: khởi tạo hồ sơ phụ huynh mới. Nhận ĐÚNG 1 trong 2:
     * userId (tài khoản có sẵn) hoặc newAccount (tạo tài khoản kèm hồ sơ
     * trong cùng transaction, gán role PARENT — theo mẫu EmployeeService.create).
     */
    @Transactional
    public ParentResponse createParent(CreateParentRequest request, Long actorUserId) {
        if ((request.userId() == null) == (request.newAccount() == null)) {
            throw new IllegalArgumentException("Cung cấp đúng 1 trong 2: userId (tài khoản có sẵn) hoặc newAccount (tạo tài khoản mới).");
        }
        User user;
        if (request.userId() != null) {
            user = userRepository.findById(request.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + request.userId()));
        } else {
            user = userAccountService.createAccount(request.newAccount());
            assignRole(user, "PARENT", actorUserId);
        }
        if (parentRepository.findByUserId(user.getId()).isPresent()) {
            throw new ParentAlreadyExistsException("Tài khoản id=" + user.getId() + " đã có hồ sơ phụ huynh.");
        }

        Parent parent = new Parent();
        parent.setUser(user);
        parent.setPortraitUrl(request.portraitUrl());
        parent.setOccupation(request.occupation());
        parent.setWorkplace(request.workplace());
        parent.setAddress(request.address());
        parent.setNotes(request.notes());
        parent = parentRepository.save(parent);

        writeParentHistory(parent, actorUserId, ParentHistory.Action.CREATED);
        return toResponse(parent);
    }

    @Transactional(readOnly = true)
    public List<ParentResponse> searchParents(String query) {
        List<Parent> parents = query == null || query.isBlank()
                ? parentRepository.findAllOrderByUserFullName()
                : parentRepository.searchByQuery(query.trim());
        return parents.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ParentResponse getParentById(Long id) {
        return toResponse(getParentOrThrow(id));
    }

    /** UC-63 Main Flow bước 1: phụ huynh tự xem hồ sơ của chính mình (FR-USR-07). */
    @Transactional(readOnly = true)
    public ParentResponse getMyParentProfile(Long userId) {
        return toResponse(getParentByUserIdOrThrow(userId));
    }

    /**
     * UC-63 Main Flow bước 2-4: phụ huynh tự cập nhật ảnh đại diện +
     * thông tin liên hệ cá nhân của chính mình (FR-USR-07). Không tái
     * dùng updateParent() — whitelist field khác hẳn
     * (UpdateOwnParentProfileRequest), không nhận notes (ghi chú nội bộ
     * do Nhân viên quản lý). Xem docs/uc/phan-he-02-phan-quyen.md.
     */
    @Transactional
    public ParentResponse updateMyParentProfile(Long userId, UpdateOwnParentProfileRequest request) {
        Parent parent = getParentByUserIdOrThrow(userId);
        parent.setPortraitUrl(request.portraitUrl());
        parent.setOccupation(request.occupation());
        parent.setWorkplace(request.workplace());
        parent.setAddress(request.address());
        parent = parentRepository.save(parent);

        writeParentHistory(parent, userId, ParentHistory.Action.UPDATED);
        return toResponse(parent);
    }

    /** Cập nhật thông tin phụ huynh đã có, giữ lịch sử phiên bản (SDD: có parents_history). */
    @Transactional
    public ParentResponse updateParent(Long id, UpdateParentRequest request, Long actorUserId) {
        Parent parent = getParentOrThrow(id);
        parent.setPortraitUrl(request.portraitUrl());
        parent.setOccupation(request.occupation());
        parent.setWorkplace(request.workplace());
        parent.setAddress(request.address());
        parent.setNotes(request.notes());
        parent = parentRepository.save(parent);

        writeParentHistory(parent, actorUserId, ParentHistory.Action.UPDATED);
        return toResponse(parent);
    }

    /** Main Flow bước 2: liên kết 1 phụ huynh (đã có hồ sơ) với học sinh. */
    @Transactional
    public ParentStudentResponse linkParent(Long studentId, LinkParentRequest request) {
        Student student = getStudentOrThrow(studentId);
        Parent parent = getParentOrThrow(request.parentId());

        if (parentStudentRepository.findByParentIdAndStudentId(parent.getId(), student.getId()).isPresent()) {
            throw new ParentStudentLinkAlreadyExistsException(
                    "Phụ huynh id=" + parent.getId() + " đã liên kết với học sinh id=" + student.getId());
        }
        assertContactRoleAvailable(student.getId(), request.isPrimaryContact(), request.isFinancialResponsible());

        ParentStudent link = new ParentStudent();
        link.setParent(parent);
        link.setStudent(student);
        link.setRelationship(ParentStudent.Relationship.valueOf(request.relationship()));
        link.setPrimaryContact(request.isPrimaryContact());
        link.setFinancialResponsible(request.isFinancialResponsible());
        link.setNotes(request.notes());
        return toResponse(parentStudentRepository.save(link));
    }

    /**
     * Tách từ linkParent() để ParentBatchImportService (UC-50) gọi PRE-CHECK
     * trước khi tạo phụ huynh mới — bổ sung ngoài SDD gốc, đã xác nhận với
     * người dùng (fix bug phát hiện khi audit): trước đây import theo lô gọi
     * findOrCreateParent() (tạo User+Parent mới) rồi mới gọi linkParent(), nên
     * nếu 2 dòng SĐT mới khác nhau cùng chỉ định primaryContact=Có cho cùng 1
     * học sinh, dòng 2 tạo xong User+Parent MỚI rồi mới phát hiện xung đột —
     * để lại "phụ huynh mồ côi" (vi phạm bất biến của UC-50) vì cả job dùng
     * chung 1 transaction, dòng lỗi chỉ bị catch chứ không rollback. Do xung
     * đột role chỉ phụ thuộc studentId (không phụ thuộc parentId), gọi được
     * TRƯỚC khi tạo phụ huynh — tránh tạo ra orphan thay vì phải dọn dẹp sau.
     */
    @Transactional(readOnly = true)
    public void assertContactRoleAvailable(Long studentId, boolean primaryContact, boolean financialResponsible) {
        List<ParentStudent> existingLinks = parentStudentRepository.findByStudentId(studentId);
        if (primaryContact && existingLinks.stream().anyMatch(ParentStudent::isPrimaryContact)) {
            throw new StudentContactRoleConflictException(
                    "Học sinh id=" + studentId + " đã có người liên hệ chính (primary contact).");
        }
        if (financialResponsible && existingLinks.stream().anyMatch(ParentStudent::isFinancialResponsible)) {
            throw new StudentContactRoleConflictException(
                    "Học sinh id=" + studentId + " đã có người chịu trách nhiệm tài chính.");
        }
    }

    /** SDD > Học sinh & Phụ huynh > b: đổi giám hộ = xóa cứng record cũ, không lưu lịch sử. */
    @Transactional
    public void unlinkParent(Long studentId, Long parentStudentId) {
        ParentStudent link = parentStudentRepository.findById(parentStudentId)
                .filter(ps -> ps.getStudent().getId().equals(studentId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy liên kết phụ huynh id=" + parentStudentId));
        parentStudentRepository.delete(link);
    }

    @Transactional(readOnly = true)
    public List<ParentStudentResponse> listParents(Long studentId) {
        getStudentOrThrow(studentId);
        return parentStudentRepository.findByStudentId(studentId).stream().map(this::toResponse).toList();
    }

    /**
     * UC-63: học sinh tự tra danh sách phụ huynh liên kết với chính mình
     * (FR-USR-07) — self-service, không cần student.parent.view (quyền đó
     * dành cho STAFF/SITE_MANAGER/SUPER_ADMIN tra cứu học sinh bất kỳ).
     */
    @Transactional(readOnly = true)
    public List<ParentStudentResponse> listMyParents(Long userId) {
        Student student = getStudentByUserIdOrThrow(userId);
        return parentStudentRepository.findByStudentId(student.getId()).stream().map(this::toResponse).toList();
    }

    /**
     * Main Flow bước 4, A1: ghi nhận sự kiện chuyển lớp/chuyển điểm trường.
     * CLASS_CHANGE/BOTH đồng bộ luôn class_enrollments (Phân hệ 6): ghi danh
     * cũ ở fromClassId chuyển TRANSFERRED, tạo ghi danh mới ACTIVE ở
     * toClassId — vì class_enrollments là nguồn sự thật duy nhất cho "lớp
     * hiện tại" của học sinh (UC-42, điểm danh...), không đồng bộ thì lịch
     * sử chuyển lớp và trạng thái ghi danh sẽ lệch nhau ngay sau giao dịch.
     * fromClassId do người dùng chỉ định (không tự suy luận) vì 1 học sinh
     * có thể có nhiều ghi danh ACTIVE đồng thời ở nhiều lớp khác nhau.
     */
    @Transactional
    public StudentTransferHistoryResponse recordTransfer(Long studentId, RecordTransferRequest request, Long actorUserId) {
        Student student = getStudentOrThrow(studentId);
        StudentTransferHistory.TransferType type = StudentTransferHistory.TransferType.valueOf(request.transferType());
        boolean changesSite = type == StudentTransferHistory.TransferType.SITE_CHANGE
                || type == StudentTransferHistory.TransferType.BOTH;
        boolean changesClass = type == StudentTransferHistory.TransferType.CLASS_CHANGE
                || type == StudentTransferHistory.TransferType.BOTH;
        if (changesSite && request.toSiteId() == null) {
            throw new IllegalArgumentException("transferType=" + type + " yêu cầu toSiteId.");
        }
        if (changesClass && request.toClassId() == null) {
            throw new IllegalArgumentException("transferType=" + type + " yêu cầu toClassId.");
        }
        if (changesClass && request.fromClassId() == null) {
            throw new IllegalArgumentException("transferType=" + type + " yêu cầu fromClassId.");
        }
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        Site toSite = changesSite ? getSiteOrThrow(request.toSiteId()) : null;

        ClassEnrollment fromEnrollment = null;
        SchoolClass toClass = null;
        if (changesClass) {
            fromEnrollment = classEnrollmentRepository
                    .findBySchoolClassIdAndStudentIdAndStatus(request.fromClassId(), studentId, ClassEnrollment.Status.ACTIVE)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Học sinh id=" + studentId + " không có ghi danh đang hoạt động ở lớp id=" + request.fromClassId() + "."));
            toClass = getSchoolClassOrThrow(request.toClassId());
            if (toClass.getStatus() != SchoolClass.Status.OPEN_ENROLLMENT && toClass.getStatus() != SchoolClass.Status.IN_PROGRESS) {
                throw new IllegalStateException("Lớp học đích \"" + toClass.getName() + "\" đang ở trạng thái "
                        + toClass.getStatus() + " — chỉ cho phép chuyển tới lớp Đang tuyển sinh hoặc Đang học.");
            }
            if (classEnrollmentRepository
                    .findBySchoolClassIdAndStudentIdAndStatus(request.toClassId(), studentId, ClassEnrollment.Status.ACTIVE)
                    .isPresent()) {
                throw new ClassEnrollmentAlreadyActiveException(
                        "Học sinh id=" + studentId + " đã ghi danh ACTIVE trong lớp id=" + request.toClassId() + ".");
            }
        }

        StudentTransferHistory history = new StudentTransferHistory();
        history.setStudent(student);
        history.setTransferType(type);
        history.setFromClassId(changesClass ? request.fromClassId() : null);
        history.setToClassId(changesClass ? request.toClassId() : null);
        history.setFromSite(student.getPrimarySite());
        history.setToSite(toSite);
        history.setEffectiveDate(request.effectiveDate());
        history.setReason(request.reason());
        history.setApprovedBy(actor);
        history = transferHistoryRepository.save(history);

        if (changesClass) {
            fromEnrollment.setStatus(ClassEnrollment.Status.TRANSFERRED);
            fromEnrollment.setWithdrawnDate(request.effectiveDate());
            fromEnrollment.setWithdrawReason(request.reason());
            fromEnrollment = classEnrollmentRepository.save(fromEnrollment);
            writeEnrollmentHistory(fromEnrollment, actor, ClassEnrollmentHistory.Action.UPDATED);

            ClassEnrollment newEnrollment = new ClassEnrollment();
            newEnrollment.setSchoolClass(toClass);
            newEnrollment.setStudent(student);
            newEnrollment.setEnrolledDate(request.effectiveDate());
            newEnrollment.setEnrolledBy(actor);
            newEnrollment.setAcademicYear(toClass.getAcademicYear());
            newEnrollment = classEnrollmentRepository.save(newEnrollment);
            writeEnrollmentHistory(newEnrollment, actor, ClassEnrollmentHistory.Action.CREATED);
        }

        if (changesSite) {
            // A1: cập nhật primary_site_id ngay khi giao dịch hoàn tất — mọi truy vấn
            // scoped-theo-site đọc trực tiếp cột này, không cần đồng bộ thêm nơi khác.
            student.setPrimarySite(toSite);
            studentRepository.save(student);
        }

        return toResponse(history);
    }

    @Transactional(readOnly = true)
    public List<StudentTransferHistoryResponse> listTransferHistory(Long studentId) {
        getStudentOrThrow(studentId);
        return transferHistoryRepository.findByStudentIdOrderByEffectiveDateDesc(studentId).stream()
                .map(this::toResponse)
                .toList();
    }

    // Cũ: hệ thống tự sinh mã học sinh (VD HS2026-0001) — đã đổi sang nhập tay
    // theo yêu cầu, giữ lại đây để tham chiếu nếu cần khôi phục.
    // /** VD HS2026-0001 (SDD > Học sinh & Phụ huynh > a) — HS + năm nhập học + số thứ tự 4 chữ số. */
    // private String generateStudentCode(LocalDate enrollmentDate) {
    //     String prefix = STUDENT_CODE_PREFIX + Year.from(enrollmentDate).getValue() + "-";
    //     long sequence = studentRepository.countByStudentCodeStartingWith(prefix) + 1;
    //     return prefix + String.format("%04d", sequence);
    // }

    private void writeStudentHistory(Student student, Long actorUserId, StudentHistory.Action action) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));
        StudentHistory history = new StudentHistory();
        history.setStudent(student);
        history.setChangedBy(actor);
        history.setAction(action);
        history.setDetails(studentSnapshot(student));
        studentHistoryRepository.save(history);
    }

    private void writeParentHistory(Parent parent, Long actorUserId, ParentHistory.Action action) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));
        ParentHistory history = new ParentHistory();
        history.setParent(parent);
        history.setChangedBy(actor);
        history.setAction(action);
        history.setDetails(parentSnapshot(parent));
        parentHistoryRepository.save(history);
    }

    /** Gán role cố định (STUDENT/PARENT) ngay khi tạo tài khoản mới qua nhánh newAccount. */
    private void assignRole(User user, String roleCode, Long actorUserId) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(actor);
        userRoleRepository.save(userRole);
    }

    private Map<String, Object> studentSnapshot(Student s) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("studentCode", s.getStudentCode());
        snapshot.put("dateOfBirth", String.valueOf(s.getDateOfBirth()));
        snapshot.put("gender", s.getGender() == null ? null : s.getGender().name());
        snapshot.put("primarySiteId", s.getPrimarySite() == null ? null : s.getPrimarySite().getId());
        snapshot.put("originalSchool", s.getOriginalSchool());
        snapshot.put("originalClass", s.getOriginalClass());
        snapshot.put("status", s.getStatus().name());
        snapshot.put("enrollmentDate", String.valueOf(s.getEnrollmentDate()));
        return snapshot;
    }

    private Map<String, Object> parentSnapshot(Parent p) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("occupation", p.getOccupation());
        snapshot.put("workplace", p.getWorkplace());
        snapshot.put("address", p.getAddress());
        return snapshot;
    }

    private Student getStudentOrThrow(Long id) {
        return studentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh id=" + id));
    }

    /** UC-63 A1: tài khoản chưa có hồ sơ học sinh tương ứng. */
    private Student getStudentByUserIdOrThrow(Long userId) {
        return studentRepository.findByUserId(userId)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản id=" + userId + " không có hồ sơ học sinh."));
    }

    private Parent getParentOrThrow(Long id) {
        return parentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ phụ huynh id=" + id));
    }

    /** UC-63 A1: tài khoản chưa có hồ sơ phụ huynh tương ứng. */
    private Parent getParentByUserIdOrThrow(Long userId) {
        return parentRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản id=" + userId + " không có hồ sơ phụ huynh."));
    }

    private Site getSiteOrThrow(Long id) {
        return siteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm trường id=" + id));
    }

    private SchoolClass getSchoolClassOrThrow(Long id) {
        return schoolClassRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học id=" + id));
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

    private StudentResponse toResponse(Student s) {
        return new StudentResponse(
                s.getId(),
                s.getUser().getId(),
                s.getUser().getFullName(),
                s.getStudentCode(),
                s.getDateOfBirth(),
                s.getGender() == null ? null : s.getGender().name(),
                s.getPortraitUrl(),
                s.getPrimarySite() == null ? null : s.getPrimarySite().getId(),
                s.getPrimarySite() == null ? null : s.getPrimarySite().getName(),
                s.getOriginalSchool(),
                s.getOriginalClass(),
                s.getStatus().name(),
                s.getEnrollmentDate(),
                s.getGraduationDate(),
                s.getNotes());
    }

    private ParentResponse toResponse(Parent p) {
        return new ParentResponse(
                p.getId(), p.getUser().getId(), p.getUser().getFullName(),
                p.getOccupation(), p.getWorkplace(), p.getAddress(), p.getNotes(), p.getPortraitUrl());
    }

    private ParentStudentResponse toResponse(ParentStudent ps) {
        return new ParentStudentResponse(
                ps.getId(), ps.getParent().getId(), ps.getParent().getUser().getFullName(), ps.getParent().getUser().getPhone(),
                ps.getStudent().getId(), ps.getRelationship().name(), ps.isPrimaryContact(), ps.isFinancialResponsible(), ps.getNotes());
    }

    private StudentTransferHistoryResponse toResponse(StudentTransferHistory h) {
        return new StudentTransferHistoryResponse(
                h.getId(), h.getStudent().getId(), h.getTransferType().name(),
                h.getFromClassId(), h.getToClassId(),
                h.getFromSite() == null ? null : h.getFromSite().getId(),
                h.getToSite() == null ? null : h.getToSite().getId(),
                h.getEffectiveDate(), h.getReason(), h.getApprovedBy().getId());
    }
}
