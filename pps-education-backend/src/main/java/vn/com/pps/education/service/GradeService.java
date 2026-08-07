package vn.com.pps.education.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AcademicTerm;
import vn.com.pps.education.domain.ApprovalFlow;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.GradeComponentSetup;
import vn.com.pps.education.domain.GradeComponentSetupHistory;
import vn.com.pps.education.domain.GradeEntry;
import vn.com.pps.education.domain.GradeEntryHistory;
import vn.com.pps.education.domain.GradeEvaluationComponent;
import vn.com.pps.education.domain.GradeEvaluationComponentHistory;
import vn.com.pps.education.domain.GradeEvaluationResult;
import vn.com.pps.education.domain.GradePeriodEditWindow;
import vn.com.pps.education.domain.ImportJob;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.ParentStudent;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateGradeComponentSetupRequest;
import vn.com.pps.education.dto.CreateGradeEvaluationComponentRequest;
import vn.com.pps.education.dto.EnterGradeEvaluationResultRequest;
import vn.com.pps.education.dto.EnterGradeRequest;
import vn.com.pps.education.dto.GradeComponentSetupResponse;
import vn.com.pps.education.dto.GradeEntryResponse;
import vn.com.pps.education.dto.GradeEvaluationComponentResponse;
import vn.com.pps.education.dto.GradeEvaluationResultResponse;
import vn.com.pps.education.dto.PublishGradesRequest;
import vn.com.pps.education.dto.StudentResponse;
import vn.com.pps.education.dto.SubmitGradesRequest;
import vn.com.pps.education.dto.UpdateGradeComponentSetupRequest;
import vn.com.pps.education.dto.UpdateGradeEvaluationComponentRequest;
import vn.com.pps.education.domain.Skill;
import vn.com.pps.education.exception.GradeAlreadyPublishedException;
import vn.com.pps.education.exception.GradeComponentLockedException;
import vn.com.pps.education.exception.GradeComponentNotDeletableException;
import vn.com.pps.education.exception.GradeComponentSetupNotDeletableException;
import vn.com.pps.education.exception.GradeComponentSetupScaleMismatchException;
import vn.com.pps.education.exception.GradeNotEditableException;
import vn.com.pps.education.exception.InvalidGradeScoreException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.domain.CurriculumSubject;
import vn.com.pps.education.repository.AcademicTermRepository;
import vn.com.pps.education.repository.ApprovalFlowRepository;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ClassTeacherRepository;
import vn.com.pps.education.repository.CurriculumSubjectRepository;
import vn.com.pps.education.repository.GradeComponentSetupHistoryRepository;
import vn.com.pps.education.repository.GradeComponentSetupRepository;
import vn.com.pps.education.repository.GradeEntryHistoryRepository;
import vn.com.pps.education.repository.GradeEntryRepository;
import vn.com.pps.education.repository.GradeEvaluationComponentHistoryRepository;
import vn.com.pps.education.repository.GradeEvaluationComponentRepository;
import vn.com.pps.education.repository.GradeEvaluationResultRepository;
import vn.com.pps.education.repository.GradePeriodEditWindowRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SkillRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-19: Nhập điểm (FR-ACA-03) + UC-20: Duyệt/Từ chối điểm (FR-ACA-03).
 * Xem docs/uc/phan-he-06-hoc-thuat.md.
 *
 * Gộp cấu hình sổ điểm (grade_component_setups/grade_evaluation_components,
 * actor HEAD_ACADEMIC — Precondition UC-19: "công thức đã cấu hình")
 * + nhập điểm (actor TEACHER) + duyệt điểm (actor SITE_MANAGER) vào 1
 * Service — tất cả đều phục vụ trực tiếp UC-19/20, KHÔNG đặt trong
 * CurriculumService (UC-16/16b/17) để tránh vi phạm SRP (xem
 * .claude/rules/solid.md).
 *
 * V95 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng — consolidate
 * vào academic_terms): cấu hình sổ điểm KHÔNG còn gắn theo curriculum
 * (dùng chung nhiều lớp) — mỗi (lớp, kỳ học, Giữa/Cuối kỳ) là 1
 * {@link GradeComponentSetup} riêng, tự quyết định có đánh giá Giữa kỳ/
 * Cuối kỳ/cả hai (VD lớp bổ trợ chỉ tạo setup END_TERM). Danh sách học
 * sinh của 1 setup được TÍNH RA (derived) từ {@code class_enrollments}
 * theo {@code rosterAsOfDate} (GV/Quản trị tự chọn ngày chốt lúc setup),
 * xem {@link #getRoster(Long)}. {@link GradeEvaluationResult} (Overall/
 * Level, đổi tên từ GradePeriodResult) thêm {@code comment}/{@code note}
 * ("Nhận xét"/"Ghi chú") — Quản lý điểm trường sửa được 2 field này (KHÔNG
 * sửa điểm) ngay tại bước duyệt, xem {@link #publishGrades}.
 *
 * V44 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng — thay hẳn luồng
 * "công bố dự kiến + phúc khảo" (V43/UC-62) cũ, đã đối chiếu lại với
 * người dùng 2026-08-06 là ĐÚNG Ý, KHÔNG viết lại): luồng 4 trạng thái
 * DRAFT → SUBMITTED → OFFICIAL / REJECTED, khoá sửa/xoá hoàn toàn theo
 * TRẠNG THÁI (xem {@link #requireEditableState}). Điểm CHỈ hiển thị cho
 * Phụ huynh/Học sinh khi đã OFFICIAL. Bị từ chối (REJECTED) thì Giáo viên
 * hoặc Quản lý sửa lại rồi gửi duyệt lại (SUBMITTED) hoặc Quản lý duyệt
 * thẳng (OFFICIAL). Actor có quyền academic.grade.edit.override (mặc
 * định HEAD_ACADEMIC + SITE_MANAGER, gán thêm được cho người khác qua
 * UC-04) bỏ qua mọi ràng buộc theo trạng thái. Lịch sử gửi/duyệt/từ chối
 * lưu trong {@link ApprovalFlow} (entity_type=GRADE_ENTRY/
 * GRADE_PERIOD_RESULT) — dùng lại đúng bảng approval_flows đã thiết kế
 * sẵn từ V1 cho UC-17.
 *
 * Cấu hình sổ điểm (HEAD_ACADEMIC/SUPER_ADMIN) qua
 * @PreAuthorize("hasPermission(null,'academic.grade.setup.create')") ở
 * GradeController (Hybrid PBAC — V28/V46/V95).
 *
 * Nhập/import điểm (UC-19/UC-53) dùng requireCanEnterGrades — mở rộng
 * ngoài SDD gốc, đã xác nhận với người dùng: ngoài Giáo viên được phân
 * công giảng dạy lớp, Trưởng phòng đào tạo (quyền academic.grade.manage)
 * hoặc Quản lý điểm trường phụ trách đúng site của lớp cũng được phép
 * nhập/import thay giáo viên khi cần hỗ trợ.
 *
 * Duyệt/từ chối điểm (UC-20) dùng requireGradeApprovePermission +
 * requireCanApproveGrades — cần quyền academic.grade.approve (role mặc
 * định: SITE_MANAGER, HEAD_ACADEMIC — V38/V77); Quản lý điểm trường vẫn
 * giới hạn row-level đúng site mình phụ trách (site_managers), Trưởng
 * phòng đào tạo (có thêm academic.grade.manage) duyệt được mọi site.
 */
@Service
public class GradeService {

    private final GradeComponentSetupRepository gradeComponentSetupRepository;
    private final GradeEvaluationComponentRepository gradeEvaluationComponentRepository;
    private final GradeEntryRepository gradeEntryRepository;
    private final GradeEvaluationResultRepository gradeEvaluationResultRepository;
    private final GradeComponentSetupHistoryRepository gradeComponentSetupHistoryRepository;
    private final GradeEvaluationComponentHistoryRepository gradeEvaluationComponentHistoryRepository;
    private final GradeEntryHistoryRepository gradeEntryHistoryRepository;
    private final GradePeriodEditWindowRepository gradePeriodEditWindowRepository;
    private final AcademicTermRepository academicTermRepository;
    private final CurriculumSubjectRepository curriculumSubjectRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final StudentRepository studentRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final SiteManagerRepository siteManagerRepository;
    private final SkillRepository skillRepository;
    private final UserRepository userRepository;
    private final PermissionEvaluationService permissionEvaluationService;
    private final AcademicSettingsService academicSettingsService;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final NotificationService notificationService;
    private final ApprovalFlowRepository approvalFlowRepository;

    public GradeService(GradeComponentSetupRepository gradeComponentSetupRepository,
                         GradeEvaluationComponentRepository gradeEvaluationComponentRepository,
                         GradeEntryRepository gradeEntryRepository,
                         GradeEvaluationResultRepository gradeEvaluationResultRepository,
                         GradeComponentSetupHistoryRepository gradeComponentSetupHistoryRepository,
                         GradeEvaluationComponentHistoryRepository gradeEvaluationComponentHistoryRepository,
                         GradeEntryHistoryRepository gradeEntryHistoryRepository,
                         GradePeriodEditWindowRepository gradePeriodEditWindowRepository,
                         AcademicTermRepository academicTermRepository,
                         CurriculumSubjectRepository curriculumSubjectRepository,
                         SchoolClassRepository schoolClassRepository,
                         StudentRepository studentRepository,
                         ClassTeacherRepository classTeacherRepository,
                         SiteManagerRepository siteManagerRepository,
                         SkillRepository skillRepository,
                         UserRepository userRepository,
                         PermissionEvaluationService permissionEvaluationService,
                         AcademicSettingsService academicSettingsService,
                         ClassEnrollmentRepository classEnrollmentRepository,
                         ParentStudentRepository parentStudentRepository,
                         NotificationService notificationService,
                         ApprovalFlowRepository approvalFlowRepository) {
        this.gradeComponentSetupRepository = gradeComponentSetupRepository;
        this.gradeEvaluationComponentRepository = gradeEvaluationComponentRepository;
        this.gradeEntryRepository = gradeEntryRepository;
        this.gradeEvaluationResultRepository = gradeEvaluationResultRepository;
        this.gradeComponentSetupHistoryRepository = gradeComponentSetupHistoryRepository;
        this.gradeEvaluationComponentHistoryRepository = gradeEvaluationComponentHistoryRepository;
        this.gradeEntryHistoryRepository = gradeEntryHistoryRepository;
        this.gradePeriodEditWindowRepository = gradePeriodEditWindowRepository;
        this.academicTermRepository = academicTermRepository;
        this.curriculumSubjectRepository = curriculumSubjectRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.studentRepository = studentRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.siteManagerRepository = siteManagerRepository;
        this.skillRepository = skillRepository;
        this.userRepository = userRepository;
        this.permissionEvaluationService = permissionEvaluationService;
        this.academicSettingsService = academicSettingsService;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.parentStudentRepository = parentStudentRepository;
        this.notificationService = notificationService;
        this.approvalFlowRepository = approvalFlowRepository;
    }

    // ===================== Cấu hình sổ điểm (HEAD_ACADEMIC/SUPER_ADMIN) =====================

    @Transactional(readOnly = true)
    public List<GradeComponentSetupResponse> listGradeComponentSetups(Long classId, Long academicTermId) {
        List<GradeComponentSetup> setups = academicTermId == null
                ? gradeComponentSetupRepository.findBySchoolClassIdOrderByAcademicTermIdAscEvaluationTypeAsc(classId)
                : gradeComponentSetupRepository.findBySchoolClassIdAndAcademicTermId(classId, academicTermId);
        return setups.stream().map(this::toResponse).toList();
    }

    /** V97: không còn tính OVERALL cả kỳ từ Giữa+Cuối kỳ (bỏ weightInFinal) — mỗi setup tự chọn 1 thang điểm (POINT_10/PERCENT/IELTS), ràng buộc maxScore mọi component trong setup. */
    @Transactional
    public GradeComponentSetupResponse createGradeComponentSetup(Long classId, CreateGradeComponentSetupRequest request, Long actorUserId) {
        SchoolClass schoolClass = getClassOrThrow(classId);
        AcademicTerm academicTerm = academicTermRepository.findById(request.academicTermId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ học id=" + request.academicTermId()));
        User actor = getUserOrThrow(actorUserId);
        GradeComponentSetup.EvaluationType evaluationType = GradeComponentSetup.EvaluationType.valueOf(request.evaluationType());
        GradeComponentSetup.ScaleType scaleType = GradeComponentSetup.ScaleType.valueOf(request.scaleType());

        GradeComponentSetup setup = new GradeComponentSetup();
        setup.setSchoolClass(schoolClass);
        setup.setAcademicTerm(academicTerm);
        setup.setEvaluationType(evaluationType);
        setup.setScaleType(scaleType);
        setup.setRosterAsOfDate(request.rosterAsOfDate());
        setup.setCommentRequired(request.commentRequired());
        setup.setCreatedBy(actor);
        setup = gradeComponentSetupRepository.save(setup);

        writeGradeComponentSetupHistory(setup, actor, GradeComponentSetupHistory.Action.CREATED);
        return toResponse(setup);
    }

    @Transactional
    public GradeComponentSetupResponse updateGradeComponentSetup(Long id, UpdateGradeComponentSetupRequest request, Long actorUserId) {
        GradeComponentSetup setup = gradeComponentSetupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy setup sổ điểm id=" + id));
        User actor = getUserOrThrow(actorUserId);

        setup.setRosterAsOfDate(request.rosterAsOfDate());
        setup.setCommentRequired(request.commentRequired());
        setup.setUpdatedAt(OffsetDateTime.now());
        setup = gradeComponentSetupRepository.save(setup);

        writeGradeComponentSetupHistory(setup, actor, GradeComponentSetupHistory.Action.UPDATED);
        return toResponse(setup);
    }

    /** V97: maxScore của mọi component trong setup phải khớp cận trên thang điểm của setup (POINT_10=10, PERCENT=100, IELTS=9 — band, cho phép nhập lẻ ở grade_entries.score). */
    private BigDecimal expectedMaxScore(GradeComponentSetup.ScaleType scaleType) {
        return switch (scaleType) {
            case POINT_10 -> new BigDecimal("10.00");
            case PERCENT -> new BigDecimal("100.00");
            case IELTS -> new BigDecimal("9.00");
        };
    }

    private void requireMaxScoreMatchesScale(GradeComponentSetup setup, BigDecimal maxScore) {
        BigDecimal expected = expectedMaxScore(setup.getScaleType());
        if (maxScore.compareTo(expected) != 0) {
            throw new GradeComponentSetupScaleMismatchException(
                    "maxScore=" + maxScore + " không khớp thang điểm " + setup.getScaleType()
                            + " của setup id=" + setup.getId() + " (yêu cầu=" + expected + ").");
        }
    }

    /**
     * UC-19 (xoá setup rỗng, bổ sung ngoài SDD gốc — đã xác nhận với người
     * dùng): chỉ xoá được setup RỖNG — chưa có thành phần điểm, chưa có
     * điểm tổng kết, và chưa bắt đầu nhập điểm. Xoá cứng (setup chưa dùng,
     * không có giá trị pháp lý cần giữ).
     */
    @Transactional
    public void deleteGradeComponentSetup(Long id, Long actorUserId) {
        GradeComponentSetup setup = gradeComponentSetupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy setup sổ điểm id=" + id));
        if (gradeEvaluationComponentRepository.countByGradeComponentSetupId(id) > 0) {
            throw new GradeComponentSetupNotDeletableException(
                    "Setup sổ điểm id=" + id + " còn thành phần điểm — xoá từng thành phần trước khi xoá setup.");
        }
        if (gradeEvaluationResultRepository.countBySchoolClassIdAndAcademicTermIdAndEvaluationType(
                setup.getSchoolClass().getId(), setup.getAcademicTerm().getId(), setup.getEvaluationType()) > 0) {
            throw new GradeComponentSetupNotDeletableException(
                    "Setup sổ điểm id=" + id + " đã có điểm tổng kết — không thể xoá.");
        }
        if (gradePeriodEditWindowRepository.existsByGradeComponentSetupId(id)) {
            throw new GradeComponentSetupNotDeletableException(
                    "Setup sổ điểm id=" + id + " đã bắt đầu nhập điểm — không thể xoá.");
        }
        gradeComponentSetupHistoryRepository.deleteByGradeComponentSetupId(id);
        gradeComponentSetupRepository.delete(setup);
    }

    /**
     * V95 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): danh sách
     * học sinh của 1 setup — TÍNH RA (derived) từ class_enrollments theo
     * rosterAsOfDate của setup, không phải bảng snapshot riêng.
     */
    @Transactional(readOnly = true)
    public List<StudentResponse> getRoster(Long setupId) {
        GradeComponentSetup setup = gradeComponentSetupRepository.findById(setupId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy setup sổ điểm id=" + setupId));
        List<ClassEnrollment> enrollments = classEnrollmentRepository
                .findActiveAsOf(setup.getSchoolClass().getId(), setup.getRosterAsOfDate());
        return enrollments.stream().map(ce -> toResponse(ce.getStudent())).toList();
    }

    @Transactional(readOnly = true)
    public List<GradeEvaluationComponentResponse> listGradeEvaluationComponents(Long setupId) {
        return gradeEvaluationComponentRepository.findByGradeComponentSetupIdOrderByDisplayOrder(setupId).stream()
                .map(this::toResponse).toList();
    }

    /**
     * UC-16 Main Flow bước 2 + A2 (bổ sung ngoài SDD gốc, đã xác nhận với
     * người dùng): thêm được thành phần điểm mới vào setup đã tồn tại
     * — không cần qua lại UC-16b/17.
     */
    @Transactional
    public GradeEvaluationComponentResponse addGradeEvaluationComponent(Long setupId, CreateGradeEvaluationComponentRequest request, Long actorUserId) {
        GradeComponentSetup setup = gradeComponentSetupRepository.findById(setupId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy setup sổ điểm id=" + setupId));
        User actor = getUserOrThrow(actorUserId);

        GradeEvaluationComponent component = new GradeEvaluationComponent();
        component.setGradeComponentSetup(setup);
        if (request.subjectId() != null) {
            component.setSubject(curriculumSubjectOrThrow(request.subjectId()));
        }
        if (request.skillId() != null) {
            component.setSkill(skillOrThrow(request.skillId()));
        }
        component.setCode(GradeEvaluationComponent.ComponentCode.valueOf(request.code()));
        component.setName(request.name());
        BigDecimal maxScore = request.maxScore() == null ? expectedMaxScore(setup.getScaleType()) : request.maxScore();
        requireMaxScoreMatchesScale(setup, maxScore);
        component.setMaxScore(maxScore);
        component.setPassThreshold(request.passThreshold());
        if (request.scaleType() != null) {
            component.setScaleType(GradeEvaluationComponent.ScaleType.valueOf(request.scaleType()));
        }
        component.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        component = gradeEvaluationComponentRepository.save(component);

        writeGradeEvaluationComponentHistory(component, actor, GradeEvaluationComponentHistory.Action.CREATED);
        return toResponse(component);
    }

    /** SDD: nếu đã có grade_entries cho component này, cấm sửa maxScore. */
    @Transactional
    public GradeEvaluationComponentResponse updateGradeEvaluationComponent(Long id, UpdateGradeEvaluationComponentRequest request, Long actorUserId) {
        GradeEvaluationComponent component = gradeEvaluationComponentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thành phần điểm id=" + id));
        User actor = getUserOrThrow(actorUserId);

        BigDecimal newMaxScore = request.maxScore() == null ? component.getMaxScore() : request.maxScore();
        boolean maxScoreChanged = component.getMaxScore().compareTo(newMaxScore) != 0;
        if (maxScoreChanged && gradeEntryRepository.countByGradeComponentId(id) > 0) {
            throw new GradeComponentLockedException(
                    "Thành phần điểm id=" + id + " đã có điểm nhập — không được sửa maxScore.");
        }
        if (maxScoreChanged) {
            requireMaxScoreMatchesScale(component.getGradeComponentSetup(), newMaxScore);
        }

        component.setName(request.name());
        component.setMaxScore(newMaxScore);
        component.setPassThreshold(request.passThreshold());
        component.setDisplayOrder(request.displayOrder() == null ? component.getDisplayOrder() : request.displayOrder());
        component = gradeEvaluationComponentRepository.save(component);

        writeGradeEvaluationComponentHistory(component, actor, GradeEvaluationComponentHistory.Action.UPDATED);
        return toResponse(component);
    }

    /**
     * UC-19 (xoá thành phần điểm, bổ sung ngoài SDD gốc — đã xác nhận với
     * người dùng): chỉ xoá được khi CHƯA có điểm nhập nào (grade_entries).
     */
    @Transactional
    public void deleteGradeEvaluationComponent(Long id, Long actorUserId) {
        GradeEvaluationComponent component = gradeEvaluationComponentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thành phần điểm id=" + id));
        if (gradeEntryRepository.countByGradeComponentId(id) > 0) {
            throw new GradeComponentNotDeletableException(
                    "Thành phần điểm id=" + id + " đã có điểm nhập — không thể xoá.");
        }
        gradeEvaluationComponentHistoryRepository.deleteByGradeEvaluationComponentId(id);
        gradeEvaluationComponentRepository.delete(component);
    }

    // ===================== UC-19: Nhập điểm (TEACHER) =====================

    /**
     * Main Flow bước 1-3: nhập/sửa điểm 1 học sinh cho 1 thành phần điểm.
     * V44: bản ghi mới (chưa tồn tại) luôn tạo được (DRAFT). Sửa bản ghi
     * đã tồn tại phải qua {@link #requireEditableState} — DRAFT/REJECTED
     * sửa tự do, SUBMITTED/OFFICIAL bị chặn (trừ
     * academic.grade.edit.override). Sửa lúc REJECTED KHÔNG tự đổi trạng
     * thái (UC-20 A2) — Giáo viên phải gọi {@link #submitGradesForApproval}
     * để gửi duyệt lại, hoặc Quản lý duyệt thẳng qua {@link #publishGrades}.
     */
    @Transactional
    public GradeEntryResponse enterGrade(Long classId, Long gradeEvaluationComponentId, EnterGradeRequest request, Long actorUserId) {
        SchoolClass schoolClass = getClassOrThrow(classId);
        requireCanEnterGrades(classId, actorUserId);
        GradeEvaluationComponent component = gradeEvaluationComponentRepository.findById(gradeEvaluationComponentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thành phần điểm id=" + gradeEvaluationComponentId));
        Student student = studentRepository.findByIdAndDeletedAtIsNull(request.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh id=" + request.studentId()));

        // A1 -- score ngoài [0, max_score], chặn lưu ngay, không để lọt xuống DB.
        if (request.score().signum() < 0 || request.score().compareTo(component.getMaxScore()) > 0) {
            throw new InvalidGradeScoreException(
                    "score=" + request.score() + " ngoài khoảng [0, " + component.getMaxScore() + "].");
        }

        User actor = getUserOrThrow(actorUserId);
        GradeComponentSetup setup = component.getGradeComponentSetup();
        GradeEntry entry = gradeEntryRepository
                .findBySchoolClassIdAndStudentIdAndGradeComponentId(classId, request.studentId(), gradeEvaluationComponentId)
                .orElse(null);
        GradeEntryHistory.Action action = GradeEntryHistory.Action.CREATED;
        if (entry == null) {
            entry = new GradeEntry();
            entry.setSchoolClass(schoolClass);
            entry.setStudent(student);
            entry.setGradeComponent(component);
            entry.setAcademicTerm(setup.getAcademicTerm());
            entry.setEvaluationType(setup.getEvaluationType());
            entry.setEnteredBy(actor);
        } else {
            action = GradeEntryHistory.Action.UPDATED;
            requireEditableState(entry.getStatus(), entry.getId(), actorUserId);
        }
        entry.setScore(request.score());
        entry.setAbsenceFlag(request.absenceFlag());
        entry.setTeacherNote(request.teacherNote());
        entry = gradeEntryRepository.save(entry);

        ensureEditWindowStarted(classId, setup.getId());
        writeGradeEntryHistory(entry, actor, action);
        return toResponse(entry);
    }

    /**
     * UC-19 (xoá điểm nháp, bổ sung ngoài SDD gốc, đã xác nhận với người
     * dùng): chỉ xoá được bản ghi DRAFT (hoặc actor có
     * academic.grade.edit.override) — xoá cứng, không có deleted_at
     * (điểm nháp chưa công bố, chưa có giá trị pháp lý cần giữ lại).
     */
    @Transactional
    public void deleteGradeEntry(Long classId, Long gradeEvaluationComponentId, Long studentId, Long actorUserId) {
        requireCanEnterGrades(classId, actorUserId);
        GradeEntry entry = gradeEntryRepository
                .findBySchoolClassIdAndStudentIdAndGradeComponentId(classId, studentId, gradeEvaluationComponentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy điểm của học sinh id=" + studentId + " cho thành phần id=" + gradeEvaluationComponentId));
        requireEditableState(entry.getStatus(), entry.getId(), actorUserId);
        gradeEntryHistoryRepository.deleteByGradeEntryId(entry.getId());
        gradeEntryRepository.delete(entry);
    }

    @Transactional(readOnly = true)
    public List<GradeEntryResponse> listEntries(Long classId, Long gradeEvaluationComponentId) {
        return gradeEntryRepository.findBySchoolClassIdAndGradeComponentIdOrderByStudentId(classId, gradeEvaluationComponentId)
                .stream().map(this::toResponse).toList();
    }

    // ===================== UC-53: Overall/Level theo (kỳ học, Giữa/Cuối kỳ) =====================

    /**
     * UC-53 Main Flow bước 3 (nhánh Overall/Level): lưu nguyên giá trị GV đã
     * tính sẵn — hệ thống KHÔNG tự tính lại công thức. V95: thêm
     * comment/note (Nhận xét/Ghi chú). Cùng cơ chế khoá theo trạng thái với
     * enterGrade — xem Javadoc ở đó.
     */
    @Transactional
    public GradeEvaluationResultResponse enterEvaluationResult(Long classId, Long studentId, Long setupId,
                                                                EnterGradeEvaluationResultRequest request, Long actorUserId) {
        requireCanEnterGrades(classId, actorUserId);
        GradeEvaluationResult result = upsertEvaluationResult(classId, studentId, setupId, request, actorUserId,
                GradeEvaluationResult.Source.MANUAL, null);
        return toResponse(result);
    }

    /** Nhánh gọi từ GradeImportService (UC-53) — đánh dấu source=EXCEL_IMPORT + importJob, quyền đã check ở caller. */
    @Transactional
    public GradeEvaluationResult upsertEvaluationResult(Long classId, Long studentId, Long setupId,
                                                         EnterGradeEvaluationResultRequest request, Long actorUserId,
                                                         GradeEvaluationResult.Source source, ImportJob importJob) {
        SchoolClass schoolClass = getClassOrThrow(classId);
        Student student = studentRepository.findByIdAndDeletedAtIsNull(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh id=" + studentId));
        GradeComponentSetup setup = gradeComponentSetupRepository.findById(setupId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy setup sổ điểm id=" + setupId));
        User actor = getUserOrThrow(actorUserId);

        GradeEvaluationResult result = gradeEvaluationResultRepository
                .findBySchoolClassIdAndStudentIdAndAcademicTermIdAndEvaluationType(
                        classId, studentId, setup.getAcademicTerm().getId(), setup.getEvaluationType())
                .orElse(null);
        if (result == null) {
            result = new GradeEvaluationResult();
            result.setSchoolClass(schoolClass);
            result.setStudent(student);
            result.setAcademicTerm(setup.getAcademicTerm());
            result.setEvaluationType(setup.getEvaluationType());
        } else {
            requireEditableState(result.getStatus(), result.getId(), actorUserId);
        }
        result.setOverallScore(request.overallScore());
        if (request.scaleType() != null) {
            result.setScaleType(GradeEvaluationResult.ScaleType.valueOf(request.scaleType()));
        }
        result.setLevel(request.level());
        result.setComment(request.comment());
        result.setNote(request.note());
        result.setDisclaimer(request.disclaimer());
        result.setSource(source);
        result.setImportJob(importJob);
        result.setEnteredBy(actor);
        result.setEnteredAt(OffsetDateTime.now());
        result = gradeEvaluationResultRepository.save(result);

        ensureEditWindowStarted(classId, setupId);
        return result;
    }

    /**
     * UC-53 (xoá điểm tổng kết kỳ nháp, bổ sung ngoài SDD gốc, đã xác
     * nhận với người dùng) — cùng ràng buộc với deleteGradeEntry: chỉ
     * DRAFT (hoặc override), xoá cứng.
     */
    @Transactional
    public void deleteEvaluationResult(Long classId, Long studentId, Long setupId, Long actorUserId) {
        requireCanEnterGrades(classId, actorUserId);
        GradeComponentSetup setup = gradeComponentSetupRepository.findById(setupId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy setup sổ điểm id=" + setupId));
        GradeEvaluationResult result = gradeEvaluationResultRepository
                .findBySchoolClassIdAndStudentIdAndAcademicTermIdAndEvaluationType(
                        classId, studentId, setup.getAcademicTerm().getId(), setup.getEvaluationType())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy điểm tổng kết của học sinh id=" + studentId + " cho setup id=" + setupId));
        requireEditableState(result.getStatus(), result.getId(), actorUserId);
        gradeEvaluationResultRepository.delete(result);
    }

    @Transactional(readOnly = true)
    public List<GradeEvaluationResultResponse> listEvaluationResults(Long classId, Long setupId) {
        GradeComponentSetup setup = gradeComponentSetupRepository.findById(setupId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy setup sổ điểm id=" + setupId));
        return gradeEvaluationResultRepository.findBySchoolClassIdAndAcademicTermIdAndEvaluationTypeOrderByStudentId(
                        classId, setup.getAcademicTerm().getId(), setup.getEvaluationType())
                .stream().map(this::toResponse).toList();
    }

    // ===================== UC-61: Học sinh tự xem điểm của mình (bổ sung ngoài SDD gốc, đã xác nhận với người dùng) =====================

    /**
     * UC-61 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng — tái dùng
     * FR-LMS-03/FR-LMS-07 giống UC-25, chỉ khác actor là Học sinh thay vì
     * Phụ huynh). Trả về mọi grade_entries đã OFFICIAL (V44 — đã Quản lý
     * điểm trường duyệt, DRAFT/SUBMITTED/REJECTED không hiển thị), theo
     * (các) lớp học sinh ĐÃ TỪNG ghi danh (kể cả lớp cũ sau khi chuyển
     * lớp). classIdFilter tùy chọn (ngữ cảnh "lớp đang xem" — UC-42).
     */
    @Transactional(readOnly = true)
    public List<GradeEntryResponse> listMyGrades(Long actorUserId, Long classIdFilter) {
        Student student = studentOrThrow(actorUserId);
        List<Long> classIds = classEnrollmentRepository.findByStudentId(student.getId()).stream()
                .map(e -> e.getSchoolClass().getId())
                .distinct()
                .filter(id -> classIdFilter == null || id.equals(classIdFilter))
                .toList();
        return classIds.stream()
                .flatMap(classId -> gradeEntryRepository
                        .findBySchoolClassIdAndStudentIdAndStatusIn(classId, student.getId(), List.of(GradeEntry.Status.OFFICIAL)).stream())
                .map(this::toResponse).toList();
    }

    /**
     * UC-61: Overall/Level đã OFFICIAL (V44) của 1 (kỳ học, Giữa/Cuối kỳ),
     * tự xem — mirror đúng ParentPortalService.getEvaluationResult. Bắt
     * buộc học sinh phải ĐÃ TỪNG có class_enrollment tại đúng classId
     * truy vấn (không lộ dữ liệu của lớp không thuộc về mình).
     */
    @Transactional(readOnly = true)
    public GradeEvaluationResultResponse getMyEvaluationResult(Long actorUserId, Long classId, Long academicTermId, String evaluationType) {
        Student student = studentOrThrow(actorUserId);
        if (!classEnrollmentRepository.existsByStudentIdAndSchoolClassId(student.getId(), classId)) {
            throw new ResourceNotFoundException("Không tìm thấy lớp học id=" + classId);
        }
        GradeEvaluationResult result = gradeEvaluationResultRepository
                .findBySchoolClassIdAndStudentIdAndAcademicTermIdAndEvaluationType(
                        classId, student.getId(), academicTermId, GradeComponentSetup.EvaluationType.valueOf(evaluationType))
                .filter(r -> r.getStatus() == GradeEvaluationResult.Status.OFFICIAL)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Chưa có điểm tổng kết đã duyệt cho kỳ học id=" + academicTermId + "."));
        return toResponse(result);
    }

    private Student studentOrThrow(Long actorUserId) {
        return studentRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản id=" + actorUserId + " không có hồ sơ học sinh."));
    }

    // ===================== UC-19/20: Gửi duyệt + Duyệt/Từ chối điểm (V44) =====================

    /**
     * UC-19 Main Flow bước 4 (V44, bổ sung ngoài SDD gốc, đã xác nhận với
     * người dùng): Giáo viên (hoặc người hỗ trợ hợp lệ — requireCanEnterGrades)
     * gửi duyệt — DRAFT hoặc REJECTED (gửi lại sau khi sửa theo yêu cầu
     * Quản lý) chuyển sang SUBMITTED, mở 1 {@link ApprovalFlow} PENDING mới
     * cho từng bản ghi (entity_type=GRADE_ENTRY/GRADE_PERIOD_RESULT) để
     * Quản lý điểm trường duyệt/từ chối qua {@link #publishGrades}. A3: bản
     * ghi đang SUBMITTED hoặc đã OFFICIAL thì báo lỗi, không cho gửi lại.
     */
    @Transactional
    public List<GradeEntryResponse> submitGradesForApproval(SubmitGradesRequest request, Long actorUserId) {
        User actor = getUserOrThrow(actorUserId);
        List<Long> entryIds = request.gradeEntryIds() == null ? List.of() : request.gradeEntryIds();
        List<Long> resultIds = request.gradeEvaluationResultIds() == null ? List.of() : request.gradeEvaluationResultIds();
        if (entryIds.isEmpty() && resultIds.isEmpty()) {
            throw new IllegalArgumentException("Phải truyền ít nhất 1 gradeEntryIds hoặc gradeEvaluationResultIds.");
        }
        List<GradeEntry> entries = gradeEntryRepository.findAllById(entryIds);
        if (entries.size() != entryIds.size()) {
            throw new ResourceNotFoundException("Có bản ghi điểm không tồn tại trong danh sách gradeEntryIds.");
        }
        List<GradeEvaluationResult> results = gradeEvaluationResultRepository.findAllById(resultIds);
        if (results.size() != resultIds.size()) {
            throw new ResourceNotFoundException("Có bản ghi Overall/Level không tồn tại trong danh sách gradeEvaluationResultIds.");
        }

        for (GradeEntry entry : entries) {
            requireCanEnterGrades(entry.getSchoolClass().getId(), actorUserId);
            requireSubmittable(entry.getStatus().name(), entry.getId());
            entry.setStatus(GradeEntry.Status.SUBMITTED);
            openPendingApprovalFlow(ApprovalFlow.EntityType.GRADE_ENTRY, entry.getId(), actor);
        }
        for (GradeEvaluationResult result : results) {
            requireCanEnterGrades(result.getSchoolClass().getId(), actorUserId);
            requireSubmittable(result.getStatus().name(), result.getId());
            result.setStatus(GradeEvaluationResult.Status.SUBMITTED);
            openPendingApprovalFlow(ApprovalFlow.EntityType.GRADE_PERIOD_RESULT, result.getId(), actor);
        }
        gradeEvaluationResultRepository.saveAll(results);
        List<GradeEntry> saved = gradeEntryRepository.saveAll(entries);
        saved.forEach(e -> writeGradeEntryHistory(e, actor, GradeEntryHistory.Action.UPDATED));
        return saved.stream().map(this::toResponse).toList();
    }

    /**
     * UC-20 Main Flow bước 1: danh sách điểm chờ duyệt (SUBMITTED). Yêu
     * cầu quyền academic.grade.approve. Nếu actor có thêm
     * academic.grade.manage (Trưởng phòng đào tạo) thì thấy MỌI site;
     * ngược lại (Quản lý điểm trường) chỉ thấy (các) điểm trường mình
     * được gán phụ trách.
     */
    @Transactional(readOnly = true)
    public List<GradeEntryResponse> listUnpublishedForSite(Long actorUserId) {
        requireGradeApprovePermission(actorUserId);
        if (permissionEvaluationService.hasPermission(actorUserId, "academic.grade.manage")) {
            return gradeEntryRepository.findByStatusOrderByEnteredAtAsc(GradeEntry.Status.SUBMITTED)
                    .stream().map(this::toResponse).toList();
        }
        List<Long> siteIds = siteManagerRepository
                .findByUserIdAndRoleTypeAndAssignedToIsNull(actorUserId, SiteManager.RoleType.SITE_MANAGER).stream()
                .map(sm -> sm.getSite().getId()).toList();
        return siteIds.stream()
                .flatMap(siteId -> gradeEntryRepository.findByStatusAndSiteId(GradeEntry.Status.SUBMITTED, siteId).stream())
                .map(this::toResponse)
                .toList();
    }

    /**
     * UC-20 Main Flow bước 2-5: Quản lý điểm trường/Trưởng phòng đào tạo
     * duyệt hoặc từ chối. 4a Duyệt: SUBMITTED/REJECTED → OFFICIAL, hiển
     * thị ngay cho Phụ huynh qua Portal (UC-25), thông báo từng Phụ huynh
     * — chấp nhận cả REJECTED để hỗ trợ A2. 4b/A1 Từ chối: chỉ chấp nhận
     * SUBMITTED → REJECTED, ghi nhận người từ chối + thời điểm + lý do
     * (tuỳ chọn), thông báo Giáo viên. A3: bản ghi không còn ở trạng thái
     * phù hợp → báo lỗi ({@link GradeAlreadyPublishedException}).
     *
     * V95 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): khi
     * APPROVE, Quản lý điểm trường được sửa comment/note (Nhận xét/Ghi
     * chú) của GradeEvaluationResult TRƯỚC KHI công bố — KHÔNG sửa được
     * score/overallScore (điểm vẫn là việc riêng của GV). Chỉ áp dụng khi
     * đang SUBMITTED (không áp dụng cho REJECT — REJECT là đẩy nguyên
     * trạng về GV). Ghi log việc sửa vào ApprovalFlow.comment (tận dụng
     * bảng approval_flows có sẵn, không thêm cột audit mới).
     */
    @Transactional
    public List<GradeEntryResponse> publishGrades(PublishGradesRequest request, Long actorUserId) {
        User actor = getUserOrThrow(actorUserId);
        ApprovalFlow.Decision decision = parseDecision(request.action());
        List<Long> entryIds = request.gradeEntryIds() == null ? List.of() : request.gradeEntryIds();
        List<Long> resultIds = request.gradeEvaluationResultIds() == null ? List.of() : request.gradeEvaluationResultIds();
        if (entryIds.isEmpty() && resultIds.isEmpty()) {
            throw new IllegalArgumentException("Phải truyền ít nhất 1 gradeEntryIds hoặc gradeEvaluationResultIds.");
        }
        List<GradeEntry> entries = gradeEntryRepository.findAllById(entryIds);
        if (entries.size() != entryIds.size()) {
            throw new ResourceNotFoundException("Có bản ghi điểm không tồn tại trong danh sách gradeEntryIds.");
        }
        List<GradeEvaluationResult> results = gradeEvaluationResultRepository.findAllById(resultIds);
        if (results.size() != resultIds.size()) {
            throw new ResourceNotFoundException("Có bản ghi Overall/Level không tồn tại trong danh sách gradeEvaluationResultIds.");
        }

        requireGradeApprovePermission(actorUserId);
        Map<Long, String> commentEdits = request.evaluationResultComments() == null ? Map.of() : request.evaluationResultComments();
        Map<Long, String> noteEdits = request.evaluationResultNotes() == null ? Map.of() : request.evaluationResultNotes();
        OffsetDateTime now = OffsetDateTime.now();
        for (GradeEntry entry : entries) {
            requireCanApproveGrades(entry.getSchoolClass().getSite().getId(), actorUserId);
            decideApprovalFlow(ApprovalFlow.EntityType.GRADE_ENTRY, entry.getId(), entry.getStatus().name(),
                    decision, actor, request.rejectReason(), now);
            if (decision == ApprovalFlow.Decision.APPROVED) {
                entry.setStatus(GradeEntry.Status.OFFICIAL);
                entry.setPublishedBy(actor);
                entry.setPublishedAt(now);
            } else {
                entry.setStatus(GradeEntry.Status.REJECTED);
            }
        }
        for (GradeEvaluationResult result : results) {
            requireCanApproveGrades(result.getSchoolClass().getSite().getId(), actorUserId);
            boolean edited = false;
            if (decision == ApprovalFlow.Decision.APPROVED) {
                if (commentEdits.containsKey(result.getId()) && !java.util.Objects.equals(result.getComment(), commentEdits.get(result.getId()))) {
                    result.setComment(commentEdits.get(result.getId()));
                    edited = true;
                }
                if (noteEdits.containsKey(result.getId()) && !java.util.Objects.equals(result.getNote(), noteEdits.get(result.getId()))) {
                    result.setNote(noteEdits.get(result.getId()));
                    edited = true;
                }
            }
            String flowComment = edited
                    ? "Nhận xét/Ghi chú đã được Quản lý điểm trường chỉnh sửa trước khi duyệt."
                    : request.rejectReason();
            decideApprovalFlow(ApprovalFlow.EntityType.GRADE_PERIOD_RESULT, result.getId(), result.getStatus().name(),
                    decision, actor, flowComment, now);
            if (decision == ApprovalFlow.Decision.APPROVED) {
                result.setStatus(GradeEvaluationResult.Status.OFFICIAL);
                result.setPublishedBy(actor);
                result.setPublishedAt(now);
            } else {
                result.setStatus(GradeEvaluationResult.Status.REJECTED);
            }
        }
        List<GradeEvaluationResult> savedResults = gradeEvaluationResultRepository.saveAll(results);
        List<GradeEntry> saved = gradeEntryRepository.saveAll(entries);
        saved.forEach(e -> writeGradeEntryHistory(e, actor, GradeEntryHistory.Action.UPDATED));
        if (decision == ApprovalFlow.Decision.APPROVED) {
            saved.forEach(e -> notifyParentsGradeEntryPublished(e, actor.getId()));
            savedResults.forEach(r -> notifyParentsEvaluationResultPublished(r, actor.getId()));
        } else {
            saved.forEach(e -> notifyTeacherGradeEntryRejected(e, request.rejectReason(), actor.getId()));
            savedResults.forEach(r -> notifyTeacherEvaluationResultRejected(r, request.rejectReason(), actor.getId()));
        }
        return saved.stream().map(this::toResponse).toList();
    }

    // ===================== Helpers =====================

    /**
     * UC-20 Main Flow bước 4a (bổ sung ngoài SDD gốc, đã xác nhận với người
     * dùng): thông báo mọi Phụ huynh liên kết (parent_student) với học sinh
     * có điểm thành phần vừa được duyệt (OFFICIAL).
     */
    void notifyParentsGradeEntryPublished(GradeEntry entry, Long triggeredByUserId) {
        List<ParentStudent> links = parentStudentRepository.findByStudentId(entry.getStudent().getId());
        if (links.isEmpty()) {
            return;
        }
        String title = "Điểm đã được công bố";
        String content = "Điểm thành phần \"" + entry.getGradeComponent().getName() + "\" (lớp "
                + entry.getSchoolClass().getName() + ") của " + entry.getStudent().getUser().getFullName()
                + " đã được công bố: " + entry.getScore() + "/" + entry.getGradeComponent().getMaxScore() + " điểm.";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("classId", entry.getSchoolClass().getId());
        metadata.put("studentId", entry.getStudent().getId());
        metadata.put("gradeEvaluationComponentId", entry.getGradeComponent().getId());
        for (ParentStudent link : links) {
            notificationService.notify(link.getParent().getUser().getId(), Notification.NotificationType.GRADE_PUBLISHED,
                    title, content, metadata, "GRADE_ENTRY", entry.getId(), Notification.Priority.NORMAL, triggeredByUserId);
        }
    }

    /** UC-20 Main Flow bước 4a — cùng cơ chế, cho Overall/Level theo (kỳ học, Giữa/Cuối kỳ) (UC-53). */
    void notifyParentsEvaluationResultPublished(GradeEvaluationResult result, Long triggeredByUserId) {
        List<ParentStudent> links = parentStudentRepository.findByStudentId(result.getStudent().getId());
        if (links.isEmpty()) {
            return;
        }
        String title = "Điểm tổng kết đã được công bố";
        String content = "Điểm tổng kết \"" + result.getAcademicTerm().getName() + " - " + result.getEvaluationType() + "\" (lớp "
                + result.getSchoolClass().getName() + ") của " + result.getStudent().getUser().getFullName()
                + " đã được công bố" + (result.getLevel() == null ? "" : ": Level " + result.getLevel()) + ".";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("classId", result.getSchoolClass().getId());
        metadata.put("studentId", result.getStudent().getId());
        metadata.put("academicTermId", result.getAcademicTerm().getId());
        metadata.put("evaluationType", result.getEvaluationType().name());
        for (ParentStudent link : links) {
            notificationService.notify(link.getParent().getUser().getId(), Notification.NotificationType.GRADE_PUBLISHED,
                    title, content, metadata, "GRADE_PERIOD_RESULT", result.getId(), Notification.Priority.NORMAL, triggeredByUserId);
        }
    }

    /** UC-20 A1/4b: thông báo Giáo viên đã nhập điểm khi bản ghi bị từ chối, kèm lý do nếu có. */
    void notifyTeacherGradeEntryRejected(GradeEntry entry, String rejectReason, Long triggeredByUserId) {
        String title = "Điểm bị từ chối";
        String content = "Điểm thành phần \"" + entry.getGradeComponent().getName() + "\" (lớp "
                + entry.getSchoolClass().getName() + ") của " + entry.getStudent().getUser().getFullName()
                + " đã bị từ chối." + (rejectReason == null || rejectReason.isBlank() ? "" : " Lý do: " + rejectReason);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("classId", entry.getSchoolClass().getId());
        metadata.put("studentId", entry.getStudent().getId());
        metadata.put("gradeEvaluationComponentId", entry.getGradeComponent().getId());
        notificationService.notify(entry.getEnteredBy().getId(), Notification.NotificationType.GRADE_REJECTED,
                title, content, metadata, "GRADE_ENTRY", entry.getId(), Notification.Priority.HIGH, triggeredByUserId);
    }

    /** UC-20 A1/4b — cùng cơ chế, cho Overall/Level theo (kỳ học, Giữa/Cuối kỳ) (UC-53). */
    void notifyTeacherEvaluationResultRejected(GradeEvaluationResult result, String rejectReason, Long triggeredByUserId) {
        String title = "Điểm tổng kết bị từ chối";
        String content = "Điểm tổng kết \"" + result.getAcademicTerm().getName() + " - " + result.getEvaluationType() + "\" (lớp "
                + result.getSchoolClass().getName() + ") của " + result.getStudent().getUser().getFullName()
                + " đã bị từ chối." + (rejectReason == null || rejectReason.isBlank() ? "" : " Lý do: " + rejectReason);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("classId", result.getSchoolClass().getId());
        metadata.put("studentId", result.getStudent().getId());
        metadata.put("academicTermId", result.getAcademicTerm().getId());
        metadata.put("evaluationType", result.getEvaluationType().name());
        notificationService.notify(result.getEnteredBy().getId(), Notification.NotificationType.GRADE_REJECTED,
                title, content, metadata, "GRADE_PERIOD_RESULT", result.getId(), Notification.Priority.HIGH, triggeredByUserId);
    }

    /** UC-19 Main Flow bước 4: mở 1 ApprovalFlow PENDING mới khi gửi duyệt (kể cả gửi lại sau REJECTED). */
    private void openPendingApprovalFlow(ApprovalFlow.EntityType entityType, Long entityId, User actor) {
        ApprovalFlow flow = new ApprovalFlow();
        flow.setEntityType(entityType);
        flow.setEntityId(entityId);
        flow.setStatus(ApprovalFlow.Status.PENDING);
        flow.setSubmittedBy(actor);
        approvalFlowRepository.save(flow);
    }

    /** UC-19 A3: chỉ gửi duyệt được bản ghi DRAFT hoặc REJECTED (gửi lại). */
    private void requireSubmittable(String statusName, Long entityId) {
        if (!"DRAFT".equals(statusName) && !"REJECTED".equals(statusName)) {
            throw new GradeAlreadyPublishedException(
                    "Bản ghi điểm id=" + entityId + " ở trạng thái " + statusName + " không thể gửi duyệt.");
        }
    }

    private ApprovalFlow.Decision parseDecision(String action) {
        if ("APPROVE".equals(action)) {
            return ApprovalFlow.Decision.APPROVED;
        }
        if ("REJECT".equals(action)) {
            return ApprovalFlow.Decision.REJECTED;
        }
        throw new IllegalArgumentException("action phải là APPROVE hoặc REJECT, nhận được: " + action);
    }

    /**
     * UC-20 bước 3-4: quyết định duyệt/từ chối trên đúng ApprovalFlow đang
     * PENDING của bản ghi — nếu không còn PENDING (VD A2: Quản lý tự sửa
     * bản ghi REJECTED rồi duyệt thẳng, không qua gửi duyệt lại), tự mở 1
     * ApprovalFlow mới do chính actor vừa duyệt tạo ra và quyết định luôn.
     * A3: validate trạng thái nguồn hợp lệ trước khi quyết định — Duyệt
     * chấp nhận SUBMITTED/REJECTED, Từ chối chỉ chấp nhận SUBMITTED.
     */
    private void decideApprovalFlow(ApprovalFlow.EntityType entityType, Long entityId, String statusName,
                                     ApprovalFlow.Decision decision, User actor, String comment, OffsetDateTime now) {
        boolean validSource = decision == ApprovalFlow.Decision.APPROVED
                ? ("SUBMITTED".equals(statusName) || "REJECTED".equals(statusName))
                : "SUBMITTED".equals(statusName);
        if (!validSource) {
            throw new GradeAlreadyPublishedException(
                    "Bản ghi điểm id=" + entityId + " ở trạng thái " + statusName
                            + " không thể " + (decision == ApprovalFlow.Decision.APPROVED ? "duyệt" : "từ chối") + ".");
        }
        ApprovalFlow flow = approvalFlowRepository
                .findFirstByEntityTypeAndEntityIdAndStatusOrderBySubmittedAtDesc(entityType, entityId, ApprovalFlow.Status.PENDING)
                .orElse(null);
        if (flow == null) {
            flow = new ApprovalFlow();
            flow.setEntityType(entityType);
            flow.setEntityId(entityId);
            flow.setSubmittedBy(actor);
        }
        flow.setApprover(actor);
        flow.setDecision(decision);
        flow.setComment(comment);
        flow.setDecidedAt(now);
        flow.setStatus(decision == ApprovalFlow.Decision.APPROVED ? ApprovalFlow.Status.APPROVED : ApprovalFlow.Status.REJECTED);
        approvalFlowRepository.save(flow);
    }

    /**
     * UC-19/UC-53 Precondition (mở rộng, bổ sung ngoài SDD gốc, đã xác nhận
     * với người dùng): cho phép nhập/import điểm nếu actor là (a) giáo viên
     * được phân công giảng dạy lớp, HOẶC (b) có quyền academic.grade.manage
     * (Trưởng phòng đào tạo), HOẶC (c) là Quản lý điểm trường phụ trách đúng
     * điểm trường của lớp — hỗ trợ/thay thế giáo viên khi cần. Package-private
     * để GradeImportService (UC-53) tái dùng, không lặp lại logic.
     */
    void requireCanEnterGrades(Long classId, Long actorUserId) {
        if (classTeacherRepository.existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(classId, actorUserId)) {
            return;
        }
        if (permissionEvaluationService.hasPermission(actorUserId, "academic.grade.manage")) {
            return;
        }
        SchoolClass schoolClass = getClassOrThrow(classId);
        if (siteManagerRepository.existsBySiteIdAndUserIdAndRoleTypeAndAssignedToIsNull(
                schoolClass.getSite().getId(), actorUserId, SiteManager.RoleType.SITE_MANAGER)) {
            return;
        }
        throw new NotAssignedTeacherForClassException(
                "Tài khoản id=" + actorUserId + " không được phân công giảng dạy lớp id=" + classId
                        + ", không có quyền academic.grade.manage, và cũng không phải Quản lý điểm trường phụ trách lớp này.");
    }

    /**
     * V44 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): actor
     * sửa/xoá được bản ghi điểm khi (a) có quyền
     * academic.grade.edit.override (bỏ qua mọi ràng buộc theo trạng thái,
     * gán được cho bất kỳ ai qua UC-04 — mặc định HEAD_ACADEMIC +
     * SITE_MANAGER), (b) status=DRAFT, hoặc (c) status=REJECTED — không
     * giới hạn thời gian. SUBMITTED/OFFICIAL luôn bị chặn với actor
     * thường (chờ Quản lý duyệt hoặc đã duyệt xong).
     */
    private void requireEditableState(GradeEntry.Status status, Long entityId, Long actorUserId) {
        requireEditableStateInternal(status.name(), entityId, actorUserId);
    }

    private void requireEditableState(GradeEvaluationResult.Status status, Long entityId, Long actorUserId) {
        requireEditableStateInternal(status.name(), entityId, actorUserId);
    }

    private void requireEditableStateInternal(String statusName, Long entityId, Long actorUserId) {
        if (permissionEvaluationService.hasPermission(actorUserId, "academic.grade.edit.override")) {
            return;
        }
        if ("DRAFT".equals(statusName) || "REJECTED".equals(statusName)) {
            return;
        }
        throw new GradeNotEditableException(
                "Bản ghi điểm id=" + entityId + " ở trạng thái " + statusName + " không thể sửa/xoá.");
    }

    /**
     * V39: đánh dấu mốc "lần đầu nhập điểm" cho (lớp, setup sổ điểm) nếu
     * chưa có — làm gốc tính hạn X ngày (UC-19 A2, chỉ mang tính thông
     * tin, không gắn cron nào — V39/V95). Ghi 1 lần duy nhất, gọi lại sau
     * đó là no-op. Bắt DataIntegrityViolationException để an toàn khi 2
     * bản ghi cùng lô import (UC-53) cùng cố tạo mốc.
     */
    private void ensureEditWindowStarted(Long classId, Long setupId) {
        if (gradePeriodEditWindowRepository.existsBySchoolClassIdAndGradeComponentSetupId(classId, setupId)) {
            return;
        }
        try {
            GradePeriodEditWindow window = new GradePeriodEditWindow();
            window.setSchoolClass(getClassOrThrow(classId));
            window.setGradeComponentSetup(gradeComponentSetupRepository.getReferenceById(setupId));
            window.setFirstEnteredAt(OffsetDateTime.now());
            gradePeriodEditWindowRepository.save(window);
        } catch (DataIntegrityViolationException ignored) {
            // Race trong cùng batch import: bản ghi khác đã tạo mốc trước (UNIQUE class_id+grade_component_setup_id).
        }
    }

    /**
     * UC-20 Precondition (bổ sung ngoài SDD gốc, đã xác nhận với người
     * dùng): actor phải có quyền academic.grade.approve (kiểm tra riêng ở
     * {@link #requireGradeApprovePermission}, gọi 1 lần cho cả lô trước
     * khi lặp từng bản ghi). Nếu actor còn có academic.grade.manage
     * (Trưởng phòng đào tạo) thì duyệt được MỌI site, không giới hạn;
     * ngược lại (Quản lý điểm trường) chỉ duyệt được đúng site mình được
     * gán phụ trách (site_managers, row-level).
     */
    private void requireCanApproveGrades(Long siteId, Long actorUserId) {
        if (permissionEvaluationService.hasPermission(actorUserId, "academic.grade.manage")) {
            return;
        }
        if (!siteManagerRepository.existsBySiteIdAndUserIdAndRoleTypeAndAssignedToIsNull(
                siteId, actorUserId, SiteManager.RoleType.SITE_MANAGER)) {
            throw new NotSiteManagerForSiteException(
                    "Tài khoản id=" + actorUserId + " không được gán phụ trách điểm trường id=" + siteId + ".");
        }
    }

    /** UC-20 Precondition — cổng quyền chung, kiểm tra 1 lần trước khi xử lý cả lô (áp dụng cho cả entries và results). */
    private void requireGradeApprovePermission(Long actorUserId) {
        if (!permissionEvaluationService.hasPermission(actorUserId, "academic.grade.approve")) {
            throw new NotSiteManagerForSiteException(
                    "Tài khoản id=" + actorUserId + " không có quyền academic.grade.approve.");
        }
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + id));
    }

    private SchoolClass getClassOrThrow(Long id) {
        return schoolClassRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học id=" + id));
    }

    private CurriculumSubject curriculumSubjectOrThrow(Long id) {
        return curriculumSubjectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học phần id=" + id));
    }

    private Skill skillOrThrow(Long id) {
        return skillRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỹ năng id=" + id));
    }

    private void writeGradeComponentSetupHistory(GradeComponentSetup setup, User actor, GradeComponentSetupHistory.Action action) {
        GradeComponentSetupHistory history = new GradeComponentSetupHistory();
        history.setGradeComponentSetup(setup);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("academicTermId", setup.getAcademicTerm().getId());
        snapshot.put("evaluationType", setup.getEvaluationType().name());
        snapshot.put("scaleType", setup.getScaleType().name());
        snapshot.put("rosterAsOfDate", setup.getRosterAsOfDate());
        history.setDetails(snapshot);
        gradeComponentSetupHistoryRepository.save(history);
    }

    private void writeGradeEvaluationComponentHistory(GradeEvaluationComponent component, User actor, GradeEvaluationComponentHistory.Action action) {
        GradeEvaluationComponentHistory history = new GradeEvaluationComponentHistory();
        history.setGradeEvaluationComponent(component);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", component.getCode().name());
        snapshot.put("name", component.getName());
        snapshot.put("maxScore", component.getMaxScore());
        history.setDetails(snapshot);
        gradeEvaluationComponentHistoryRepository.save(history);
    }

    private void writeGradeEntryHistory(GradeEntry entry, User actor, GradeEntryHistory.Action action) {
        GradeEntryHistory history = new GradeEntryHistory();
        history.setGradeEntry(entry);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("studentId", entry.getStudent().getId());
        snapshot.put("score", entry.getScore());
        snapshot.put("status", entry.getStatus().name());
        history.setDetails(snapshot);
        gradeEntryHistoryRepository.save(history);
    }

    private GradeComponentSetupResponse toResponse(GradeComponentSetup s) {
        return new GradeComponentSetupResponse(
                s.getId(), s.getSchoolClass().getId(), s.getAcademicTerm().getId(), s.getAcademicTerm().getName(),
                s.getEvaluationType().name(), s.getScaleType().name(), s.getRosterAsOfDate(), s.isCommentRequired());
    }

    private GradeEvaluationComponentResponse toResponse(GradeEvaluationComponent c) {
        return new GradeEvaluationComponentResponse(
                c.getId(), c.getGradeComponentSetup().getId(), c.getSubject() == null ? null : c.getSubject().getId(),
                c.getSkill() == null ? null : c.getSkill().getId(),
                c.getCode().name(), c.getName(), c.getMaxScore(), c.getPassThreshold(),
                c.getScaleType().name(), c.getDisplayOrder());
    }

    private GradeEntryResponse toResponse(GradeEntry e) {
        return new GradeEntryResponse(
                e.getId(), e.getSchoolClass().getId(), e.getStudent().getId(), e.getStudent().getUser().getFullName(),
                e.getStudent().getStudentCode(), e.getGradeComponent().getId(), e.getAcademicTerm().getId(), e.getEvaluationType().name(),
                e.getScore(), e.isAbsenceFlag(), e.getTeacherNote(), e.getStatus().name(), e.getEnteredBy().getId(),
                e.getPublishedBy() == null ? null : e.getPublishedBy().getId(), e.getPublishedAt(), e.getFinalizedAt());
    }

    private GradeEvaluationResultResponse toResponse(GradeEvaluationResult r) {
        return new GradeEvaluationResultResponse(
                r.getId(), r.getSchoolClass().getId(), r.getStudent().getId(),
                r.getStudent().getUser().getFullName(), r.getStudent().getStudentCode(),
                r.getAcademicTerm().getId(), r.getEvaluationType().name(), r.getOverallScore(), r.getScaleType().name(),
                r.getLevel(), r.getComment(), r.getNote(), r.getDisclaimer(),
                r.getSource().name(), r.getImportJob() == null ? null : r.getImportJob().getId(),
                r.getStatus().name(), r.getEnteredBy().getId(),
                r.getPublishedBy() == null ? null : r.getPublishedBy().getId(), r.getPublishedAt(), r.getFinalizedAt());
    }

    private StudentResponse toResponse(Student s) {
        return new StudentResponse(
                s.getId(), s.getUser().getId(), s.getUser().getFullName(), s.getStudentCode(), s.getDateOfBirth(),
                s.getGender() == null ? null : s.getGender().name(), s.getPortraitUrl(),
                s.getPrimarySite() == null ? null : s.getPrimarySite().getId(),
                s.getPrimarySite() == null ? null : s.getPrimarySite().getName(),
                s.getOriginalSchool(), s.getOriginalClass(), s.getStatus().name(),
                s.getEnrollmentDate(), s.getGraduationDate(), s.getNotes());
    }
}
