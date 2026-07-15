package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ClassEnrollmentHistory;
import vn.com.pps.education.domain.Parent;
import vn.com.pps.education.domain.ParentHistory;
import vn.com.pps.education.domain.ParentStudent;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.StudentHistory;
import vn.com.pps.education.domain.StudentTransferHistory;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateParentRequest;
import vn.com.pps.education.dto.CreateStudentRequest;
import vn.com.pps.education.dto.LinkParentRequest;
import vn.com.pps.education.dto.ParentResponse;
import vn.com.pps.education.dto.ParentStudentResponse;
import vn.com.pps.education.dto.RecordTransferRequest;
import vn.com.pps.education.dto.StudentResponse;
import vn.com.pps.education.dto.StudentTransferHistoryResponse;
import vn.com.pps.education.dto.UpdateParentRequest;
import vn.com.pps.education.dto.UpdateStudentRequest;
import vn.com.pps.education.exception.ClassEnrollmentAlreadyActiveException;
import vn.com.pps.education.exception.ParentAlreadyExistsException;
import vn.com.pps.education.exception.ParentStudentLinkAlreadyExistsException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.StudentAlreadyExistsException;
import vn.com.pps.education.exception.StudentContactRoleConflictException;
import vn.com.pps.education.repository.ClassEnrollmentHistoryRepository;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ParentHistoryRepository;
import vn.com.pps.education.repository.ParentRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentHistoryRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.StudentTransferHistoryRepository;
import vn.com.pps.education.repository.UserRepository;

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
 * row-level access theo điểm trường): codebase hiện chưa có hạ tầng
 * row-level ACL theo site cho bất kỳ resource nào (xem Employee.java —
 * cùng quyết định với UC-08 A1). Vì mọi truy vấn scoped-theo-site trong
 * tương lai sẽ đọc trực tiếp students.primary_site_id (không có bản sao
 * cache riêng), việc recordTransfer() cập nhật cột này NGAY LẬP TỨC đã
 * thỏa mãn yêu cầu — không cần code ACL bổ sung ở đây.
 */
@Service
public class StudentService {

    private static final String STUDENT_CODE_PREFIX = "HS";

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
                           ClassEnrollmentHistoryRepository classEnrollmentHistoryRepository) {
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
    }

    @Transactional(readOnly = true)
    public List<StudentResponse> search(String query) {
        List<Student> students = query == null || query.isBlank()
                ? studentRepository.findAllActive()
                : studentRepository.searchByQuery(query.trim());
        return students.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public StudentResponse getById(Long id) {
        return toResponse(getStudentOrThrow(id));
    }

    /** Main Flow bước 1-3: khởi tạo hồ sơ học sinh mới, hệ thống tự sinh student_code. */
    @Transactional
    public StudentResponse create(CreateStudentRequest request, Long actorUserId) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + request.userId()));
        if (studentRepository.findByUserId(request.userId()).isPresent()) {
            throw new StudentAlreadyExistsException("Tài khoản id=" + request.userId() + " đã có hồ sơ học sinh.");
        }

        Student student = new Student();
        student.setUser(user);
        student.setStudentCode(generateStudentCode(request.enrollmentDate()));
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

    /** Main Flow bước 2: khởi tạo hồ sơ phụ huynh mới cho 1 user đã có sẵn. */
    @Transactional
    public ParentResponse createParent(CreateParentRequest request, Long actorUserId) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + request.userId()));
        if (parentRepository.findByUserId(request.userId()).isPresent()) {
            throw new ParentAlreadyExistsException("Tài khoản id=" + request.userId() + " đã có hồ sơ phụ huynh.");
        }

        Parent parent = new Parent();
        parent.setUser(user);
        parent.setOccupation(request.occupation());
        parent.setWorkplace(request.workplace());
        parent.setAddress(request.address());
        parent.setNotes(request.notes());
        parent = parentRepository.save(parent);

        writeParentHistory(parent, actorUserId, ParentHistory.Action.CREATED);
        return toResponse(parent);
    }

    /** Cập nhật thông tin phụ huynh đã có, giữ lịch sử phiên bản (SDD: có parents_history). */
    @Transactional
    public ParentResponse updateParent(Long id, UpdateParentRequest request, Long actorUserId) {
        Parent parent = parentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ phụ huynh id=" + id));
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
        Parent parent = parentRepository.findById(request.parentId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ phụ huynh id=" + request.parentId()));

        if (parentStudentRepository.findByParentIdAndStudentId(parent.getId(), student.getId()).isPresent()) {
            throw new ParentStudentLinkAlreadyExistsException(
                    "Phụ huynh id=" + parent.getId() + " đã liên kết với học sinh id=" + student.getId());
        }

        List<ParentStudent> existingLinks = parentStudentRepository.findByStudentId(student.getId());
        if (request.isPrimaryContact() && existingLinks.stream().anyMatch(ParentStudent::isPrimaryContact)) {
            throw new StudentContactRoleConflictException(
                    "Học sinh id=" + student.getId() + " đã có người liên hệ chính (primary contact).");
        }
        if (request.isFinancialResponsible() && existingLinks.stream().anyMatch(ParentStudent::isFinancialResponsible)) {
            throw new StudentContactRoleConflictException(
                    "Học sinh id=" + student.getId() + " đã có người chịu trách nhiệm tài chính.");
        }

        ParentStudent link = new ParentStudent();
        link.setParent(parent);
        link.setStudent(student);
        link.setRelationship(ParentStudent.Relationship.valueOf(request.relationship()));
        link.setPrimaryContact(request.isPrimaryContact());
        link.setFinancialResponsible(request.isFinancialResponsible());
        link.setNotes(request.notes());
        return toResponse(parentStudentRepository.save(link));
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

    /** VD HS2026-0001 (SDD > Học sinh & Phụ huynh > a) — HS + năm nhập học + số thứ tự 4 chữ số. */
    private String generateStudentCode(LocalDate enrollmentDate) {
        String prefix = STUDENT_CODE_PREFIX + Year.from(enrollmentDate).getValue() + "-";
        long sequence = studentRepository.countByStudentCodeStartingWith(prefix) + 1;
        return prefix + String.format("%04d", sequence);
    }

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
                p.getOccupation(), p.getWorkplace(), p.getAddress(), p.getNotes());
    }

    private ParentStudentResponse toResponse(ParentStudent ps) {
        return new ParentStudentResponse(
                ps.getId(), ps.getParent().getId(), ps.getParent().getUser().getFullName(), ps.getStudent().getId(),
                ps.getRelationship().name(), ps.isPrimaryContact(), ps.isFinancialResponsible(), ps.getNotes());
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
