package vn.com.pps.education.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.GradeComponent;
import vn.com.pps.education.domain.GradeComponentHistory;
import vn.com.pps.education.domain.GradeEntry;
import vn.com.pps.education.domain.GradeEntryHistory;
import vn.com.pps.education.domain.GradePeriod;
import vn.com.pps.education.domain.GradePeriodEditWindow;
import vn.com.pps.education.domain.GradePeriodHistory;
import vn.com.pps.education.domain.GradePeriodResult;
import vn.com.pps.education.domain.ImportJob;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.ParentStudent;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateGradeComponentRequest;
import vn.com.pps.education.dto.CreateGradePeriodRequest;
import vn.com.pps.education.dto.EnterGradePeriodResultRequest;
import vn.com.pps.education.dto.EnterGradeRequest;
import vn.com.pps.education.dto.GradeComponentResponse;
import vn.com.pps.education.dto.GradeEntryResponse;
import vn.com.pps.education.dto.GradePeriodResponse;
import vn.com.pps.education.dto.GradePeriodResultResponse;
import vn.com.pps.education.dto.PublishGradesRequest;
import vn.com.pps.education.dto.UpdateGradeComponentRequest;
import vn.com.pps.education.dto.UpdateGradePeriodRequest;
import vn.com.pps.education.domain.Skill;
import vn.com.pps.education.exception.GradeAlreadyPublishedException;
import vn.com.pps.education.exception.GradeComponentLockedException;
import vn.com.pps.education.exception.GradeEditWindowExpiredException;
import vn.com.pps.education.exception.GradePeriodWeightExceededException;
import vn.com.pps.education.exception.InvalidGradeScoreException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.domain.CurriculumSubject;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ClassTeacherRepository;
import vn.com.pps.education.repository.CurriculumRepository;
import vn.com.pps.education.repository.CurriculumSubjectRepository;
import vn.com.pps.education.repository.GradeComponentHistoryRepository;
import vn.com.pps.education.repository.GradeComponentRepository;
import vn.com.pps.education.repository.GradeEntryHistoryRepository;
import vn.com.pps.education.repository.GradeEntryRepository;
import vn.com.pps.education.repository.GradePeriodEditWindowRepository;
import vn.com.pps.education.repository.GradePeriodHistoryRepository;
import vn.com.pps.education.repository.GradePeriodRepository;
import vn.com.pps.education.repository.GradePeriodResultRepository;
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
 * UC-19: Nhập điểm (FR-ACA-03) + UC-20: Công bố điểm (FR-ACA-03).
 * Xem docs/uc/phan-he-06-hoc-thuat.md.
 *
 * Gộp cấu hình sổ điểm (grade_periods/grade_components, actor HEAD_ACADEMIC
 * — Precondition UC-19: "công thức đã cấu hình trong khung chương trình")
 * + nhập điểm (actor TEACHER) + công bố điểm (actor SITE_MANAGER) vào 1
 * Service — tất cả đều phục vụ trực tiếp UC-19/20, KHÔNG đặt trong
 * CurriculumService (UC-16/16b/17) để tránh vi phạm SRP (xem
 * .claude/rules/solid.md, ví dụ "AcademicService khổng lồ gộp UC-16 +
 * UC-19/20").
 *
 * V39 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng qua nhiều vòng
 * hỏi): bỏ hẳn luồng duyệt/từ chối qua ApprovalFlow cho điểm. Giáo viên
 * toàn quyền sửa điểm lớp mình trong hạn X ngày kể từ lần đầu nhập cho 1
 * (lớp, kỳ đánh giá) — mốc lưu ở grade_period_edit_windows, số ngày X đọc
 * qua {@link AcademicSettingsService}. Actor có quyền
 * academic.grade.edit.override (mặc định HEAD_ACADEMIC + SITE_MANAGER,
 * gán thêm được cho người khác qua UC-04) bỏ qua hạn này. "Công bố điểm"
 * (permission academic.grade.publish) chỉ còn là quyết định thời điểm
 * Phụ huynh/Học sinh được xem điểm (DRAFT → PUBLISHED) — không còn nhánh
 * từ chối. Sửa điểm sau khi công bố (vẫn trong hạn X ngày, trường hợp
 * phúc khảo) giữ nguyên status=PUBLISHED với giá trị mới — Phụ huynh thấy
 * ngay, không cần công bố lại.
 *
 * Cấu hình sổ điểm (HEAD_ACADEMIC) qua
 * @PreAuthorize("hasPermission(null,'academic.grade.manage')") ở
 * GradeController (Hybrid PBAC — V28).
 *
 * Nhập/import điểm (UC-19/UC-53) dùng requireCanEnterGrades — mở rộng
 * ngoài SDD gốc, đã xác nhận với người dùng: ngoài Giáo viên được phân
 * công giảng dạy lớp, Trưởng phòng đào tạo (quyền academic.grade.manage)
 * hoặc Quản lý điểm trường phụ trách đúng site của lớp cũng được phép
 * nhập/import thay giáo viên khi cần hỗ trợ.
 *
 * Công bố điểm (UC-20) dùng requireGradePublishPermission +
 * requireCanPublishGrades — bổ sung ngoài SDD gốc, đã xác nhận với người
 * dùng: cần quyền academic.grade.publish (role mặc định: SITE_MANAGER,
 * HEAD_ACADEMIC — V38/V39); Quản lý điểm trường vẫn giới hạn row-level đúng
 * site mình phụ trách (site_managers), Trưởng phòng đào tạo (có thêm
 * academic.grade.manage) công bố được mọi site.
 */
@Service
public class GradeService {

    private final GradePeriodRepository gradePeriodRepository;
    private final GradeComponentRepository gradeComponentRepository;
    private final GradeEntryRepository gradeEntryRepository;
    private final GradePeriodResultRepository gradePeriodResultRepository;
    private final GradePeriodHistoryRepository gradePeriodHistoryRepository;
    private final GradeComponentHistoryRepository gradeComponentHistoryRepository;
    private final GradeEntryHistoryRepository gradeEntryHistoryRepository;
    private final GradePeriodEditWindowRepository gradePeriodEditWindowRepository;
    private final CurriculumRepository curriculumRepository;
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

    public GradeService(GradePeriodRepository gradePeriodRepository,
                         GradeComponentRepository gradeComponentRepository,
                         GradeEntryRepository gradeEntryRepository,
                         GradePeriodResultRepository gradePeriodResultRepository,
                         GradePeriodHistoryRepository gradePeriodHistoryRepository,
                         GradeComponentHistoryRepository gradeComponentHistoryRepository,
                         GradeEntryHistoryRepository gradeEntryHistoryRepository,
                         GradePeriodEditWindowRepository gradePeriodEditWindowRepository,
                         CurriculumRepository curriculumRepository,
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
                         NotificationService notificationService) {
        this.gradePeriodRepository = gradePeriodRepository;
        this.gradeComponentRepository = gradeComponentRepository;
        this.gradeEntryRepository = gradeEntryRepository;
        this.gradePeriodResultRepository = gradePeriodResultRepository;
        this.gradePeriodHistoryRepository = gradePeriodHistoryRepository;
        this.gradeComponentHistoryRepository = gradeComponentHistoryRepository;
        this.gradeEntryHistoryRepository = gradeEntryHistoryRepository;
        this.gradePeriodEditWindowRepository = gradePeriodEditWindowRepository;
        this.curriculumRepository = curriculumRepository;
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
    }

    // ===================== Cấu hình sổ điểm (HEAD_ACADEMIC) =====================

    @Transactional(readOnly = true)
    public List<GradePeriodResponse> listGradePeriods(Long curriculumId) {
        return gradePeriodRepository.findByCurriculumIdOrderByDisplayOrder(curriculumId).stream()
                .map(this::toResponse).toList();
    }

    /** Cấu hình kỳ đánh giá cho khung chương trình. Tổng weightInFinal của các kỳ ACTIVE không được vượt 100 (SDD). */
    @Transactional
    public GradePeriodResponse createGradePeriod(Long curriculumId, CreateGradePeriodRequest request, Long actorUserId) {
        Curriculum curriculum = curriculumRepository.findByIdAndDeletedAtIsNull(curriculumId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khung chương trình id=" + curriculumId));
        User actor = getUserOrThrow(actorUserId);

        BigDecimal currentTotal = gradePeriodRepository.findByCurriculumIdAndStatus(curriculumId, GradePeriod.Status.ACTIVE)
                .stream().map(GradePeriod::getWeightInFinal).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (currentTotal.add(request.weightInFinal()).compareTo(new BigDecimal("100")) > 0) {
            throw new GradePeriodWeightExceededException(
                    "Tổng weightInFinal các kỳ ACTIVE của khung id=" + curriculumId + " sẽ vượt quá 100.");
        }

        GradePeriod period = new GradePeriod();
        period.setCurriculum(curriculum);
        period.setCode(GradePeriod.Code.valueOf(request.code()));
        period.setName(request.name());
        period.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        period.setWeightInFinal(request.weightInFinal());
        period.setStartDate(request.startDate());
        period.setEndDate(request.endDate());
        period = gradePeriodRepository.save(period);

        writeGradePeriodHistory(period, actor, GradePeriodHistory.Action.CREATED);
        return toResponse(period);
    }

    @Transactional
    public GradePeriodResponse updateGradePeriod(Long id, UpdateGradePeriodRequest request, Long actorUserId) {
        GradePeriod period = gradePeriodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ đánh giá id=" + id));
        User actor = getUserOrThrow(actorUserId);

        GradePeriod.Status newStatus = GradePeriod.Status.valueOf(request.status());
        if (newStatus == GradePeriod.Status.ACTIVE) {
            BigDecimal othersTotal = gradePeriodRepository
                    .findByCurriculumIdAndStatus(period.getCurriculum().getId(), GradePeriod.Status.ACTIVE).stream()
                    .filter(p -> !p.getId().equals(id))
                    .map(GradePeriod::getWeightInFinal).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (othersTotal.add(request.weightInFinal()).compareTo(new BigDecimal("100")) > 0) {
                throw new GradePeriodWeightExceededException(
                        "Tổng weightInFinal các kỳ ACTIVE của khung id=" + period.getCurriculum().getId() + " sẽ vượt quá 100.");
            }
        }

        period.setName(request.name());
        period.setDisplayOrder(request.displayOrder() == null ? period.getDisplayOrder() : request.displayOrder());
        period.setWeightInFinal(request.weightInFinal());
        period.setStartDate(request.startDate());
        period.setEndDate(request.endDate());
        period.setStatus(newStatus);
        period = gradePeriodRepository.save(period);

        writeGradePeriodHistory(period, actor, GradePeriodHistory.Action.UPDATED);
        return toResponse(period);
    }

    @Transactional(readOnly = true)
    public List<GradeComponentResponse> listGradeComponents(Long gradePeriodId) {
        return gradeComponentRepository.findByGradePeriodIdOrderByDisplayOrder(gradePeriodId).stream()
                .map(this::toResponse).toList();
    }

    /**
     * UC-16 Main Flow bước 2 + A2 (bổ sung ngoài SDD gốc, đã xác nhận với
     * người dùng): thêm được thành phần điểm mới vào kỳ đánh giá đã tồn tại
     * (kể cả khung ACTIVE, đã có lớp dùng) — không cần qua lại UC-16b/17.
     */
    @Transactional
    public GradeComponentResponse addGradeComponent(Long gradePeriodId, CreateGradeComponentRequest request, Long actorUserId) {
        GradePeriod period = gradePeriodRepository.findById(gradePeriodId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ đánh giá id=" + gradePeriodId));
        User actor = getUserOrThrow(actorUserId);

        GradeComponent component = new GradeComponent();
        component.setGradePeriod(period);
        if (request.subjectId() != null) {
            component.setSubject(curriculumSubjectOrThrow(request.subjectId()));
        }
        if (request.skillId() != null) {
            component.setSkill(skillOrThrow(request.skillId()));
        }
        component.setCode(GradeComponent.ComponentCode.valueOf(request.code()));
        component.setName(request.name());
        if (request.maxScore() != null) {
            component.setMaxScore(request.maxScore());
        }
        component.setPassThreshold(request.passThreshold());
        if (request.scaleType() != null) {
            component.setScaleType(GradeComponent.ScaleType.valueOf(request.scaleType()));
        }
        component.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        component = gradeComponentRepository.save(component);

        writeGradeComponentHistory(component, actor, GradeComponentHistory.Action.CREATED);
        return toResponse(component);
    }

    /** SDD: nếu đã có grade_entries cho component này, cấm sửa maxScore. */
    @Transactional
    public GradeComponentResponse updateGradeComponent(Long id, UpdateGradeComponentRequest request, Long actorUserId) {
        GradeComponent component = gradeComponentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thành phần điểm id=" + id));
        User actor = getUserOrThrow(actorUserId);

        BigDecimal newMaxScore = request.maxScore() == null ? component.getMaxScore() : request.maxScore();
        boolean maxScoreChanged = component.getMaxScore().compareTo(newMaxScore) != 0;
        if (maxScoreChanged && gradeEntryRepository.countByGradeComponentId(id) > 0) {
            throw new GradeComponentLockedException(
                    "Thành phần điểm id=" + id + " đã có điểm nhập — không được sửa maxScore.");
        }

        component.setName(request.name());
        component.setMaxScore(newMaxScore);
        component.setPassThreshold(request.passThreshold());
        component.setDisplayOrder(request.displayOrder() == null ? component.getDisplayOrder() : request.displayOrder());
        component = gradeComponentRepository.save(component);

        writeGradeComponentHistory(component, actor, GradeComponentHistory.Action.UPDATED);
        return toResponse(component);
    }

    // ===================== UC-19: Nhập điểm (TEACHER) =====================

    /**
     * Main Flow bước 1-3: nhập/sửa điểm 1 học sinh cho 1 thành phần điểm.
     * V39: không còn khoá theo status — sửa được bất kể DRAFT/PUBLISHED
     * miễn còn trong hạn X ngày (hoặc có quyền academic.grade.edit.override,
     * xem {@link #requireEditableOrOverride}). Giữ nguyên status hiện có khi
     * sửa (KHÔNG reset về DRAFT) — nếu đã PUBLISHED, giá trị mới hiển thị
     * ngay cho Phụ huynh (trường hợp phúc khảo).
     */
    @Transactional
    public GradeEntryResponse enterGrade(Long classId, Long gradeComponentId, EnterGradeRequest request, Long actorUserId) {
        SchoolClass schoolClass = getClassOrThrow(classId);
        requireCanEnterGrades(classId, actorUserId);
        GradeComponent component = gradeComponentRepository.findById(gradeComponentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thành phần điểm id=" + gradeComponentId));
        Student student = studentRepository.findByIdAndDeletedAtIsNull(request.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh id=" + request.studentId()));

        // A1 -- score ngoài [0, max_score], chặn lưu ngay, không để lọt xuống DB.
        if (request.score().signum() < 0 || request.score().compareTo(component.getMaxScore()) > 0) {
            throw new InvalidGradeScoreException(
                    "score=" + request.score() + " ngoài khoảng [0, " + component.getMaxScore() + "].");
        }

        Long gradePeriodId = component.getGradePeriod().getId();
        requireEditableOrOverride(classId, gradePeriodId, actorUserId);

        User actor = getUserOrThrow(actorUserId);
        GradeEntry entry = gradeEntryRepository
                .findBySchoolClassIdAndStudentIdAndGradeComponentId(classId, request.studentId(), gradeComponentId)
                .orElse(null);
        GradeEntryHistory.Action action = GradeEntryHistory.Action.CREATED;
        if (entry == null) {
            entry = new GradeEntry();
            entry.setSchoolClass(schoolClass);
            entry.setStudent(student);
            entry.setGradeComponent(component);
            entry.setEnteredBy(actor);
        } else {
            action = GradeEntryHistory.Action.UPDATED;
        }
        entry.setScore(request.score());
        entry.setAbsenceFlag(request.absenceFlag());
        entry.setTeacherNote(request.teacherNote());
        entry = gradeEntryRepository.save(entry);

        ensureEditWindowStarted(classId, gradePeriodId);
        writeGradeEntryHistory(entry, actor, action);
        return toResponse(entry);
    }

    @Transactional(readOnly = true)
    public List<GradeEntryResponse> listEntries(Long classId, Long gradeComponentId) {
        return gradeEntryRepository.findBySchoolClassIdAndGradeComponentIdOrderByStudentId(classId, gradeComponentId)
                .stream().map(this::toResponse).toList();
    }

    // ===================== UC-53: Overall/Level theo kỳ đánh giá =====================

    /**
     * UC-53 Main Flow bước 3 (nhánh Overall/Level): lưu nguyên giá trị GV đã
     * tính sẵn — hệ thống KHÔNG tự tính lại công thức. V39: cùng cơ chế
     * hạn chỉnh sửa với enterGrade — xem Javadoc ở đó.
     */
    @Transactional
    public GradePeriodResultResponse enterPeriodResult(Long classId, Long studentId, Long gradePeriodId,
                                                       EnterGradePeriodResultRequest request, Long actorUserId) {
        requireCanEnterGrades(classId, actorUserId);
        GradePeriodResult result = upsertPeriodResult(classId, studentId, gradePeriodId, request, actorUserId,
                GradePeriodResult.Source.MANUAL, null);
        return toResponse(result);
    }

    /** Nhánh gọi từ GradeImportService (UC-53) — đánh dấu source=EXCEL_IMPORT + importJob, quyền đã check ở caller. */
    @Transactional
    public GradePeriodResult upsertPeriodResult(Long classId, Long studentId, Long gradePeriodId,
                                                EnterGradePeriodResultRequest request, Long actorUserId,
                                                GradePeriodResult.Source source, ImportJob importJob) {
        SchoolClass schoolClass = getClassOrThrow(classId);
        Student student = studentRepository.findByIdAndDeletedAtIsNull(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh id=" + studentId));
        GradePeriod period = gradePeriodRepository.findById(gradePeriodId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ đánh giá id=" + gradePeriodId));
        User actor = getUserOrThrow(actorUserId);

        requireEditableOrOverride(classId, gradePeriodId, actorUserId);

        GradePeriodResult result = gradePeriodResultRepository
                .findBySchoolClassIdAndStudentIdAndGradePeriodId(classId, studentId, gradePeriodId)
                .orElse(null);
        if (result == null) {
            result = new GradePeriodResult();
            result.setSchoolClass(schoolClass);
            result.setStudent(student);
            result.setGradePeriod(period);
        }
        result.setOverallScore(request.overallScore());
        if (request.scaleType() != null) {
            result.setScaleType(GradePeriodResult.ScaleType.valueOf(request.scaleType()));
        }
        result.setLevel(request.level());
        result.setSource(source);
        result.setImportJob(importJob);
        result.setEnteredBy(actor);
        result.setEnteredAt(OffsetDateTime.now());
        result = gradePeriodResultRepository.save(result);

        ensureEditWindowStarted(classId, gradePeriodId);
        return result;
    }

    @Transactional(readOnly = true)
    public List<GradePeriodResultResponse> listPeriodResults(Long classId, Long gradePeriodId) {
        return gradePeriodResultRepository.findBySchoolClassIdAndGradePeriodIdOrderByStudentId(classId, gradePeriodId)
                .stream().map(this::toResponse).toList();
    }

    // ===================== UC-61: Học sinh tự xem điểm của mình (bổ sung ngoài SDD gốc, đã xác nhận với người dùng) =====================

    /**
     * UC-61 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng — tái dùng
     * FR-LMS-03/FR-LMS-07 giống UC-25, chỉ khác actor là Học sinh thay vì
     * Phụ huynh). Chỉ trả về grade_entries đã PUBLISHED, theo (các) lớp
     * học sinh đang ghi danh ACTIVE — mirror đúng
     * ParentPortalService.listGrades, không tự tính lại điểm gì.
     * classIdFilter tùy chọn (ngữ cảnh "lớp đang xem" — UC-42).
     */
    @Transactional(readOnly = true)
    public List<GradeEntryResponse> listMyGrades(Long actorUserId, Long classIdFilter) {
        Student student = studentOrThrow(actorUserId);
        List<Long> classIds = classEnrollmentRepository.findByStudentId(student.getId()).stream()
                .filter(e -> e.getStatus() == ClassEnrollment.Status.ACTIVE)
                .map(e -> e.getSchoolClass().getId())
                .filter(id -> classIdFilter == null || id.equals(classIdFilter))
                .toList();
        return classIds.stream()
                .flatMap(classId -> gradeEntryRepository
                        .findBySchoolClassIdAndStudentIdAndStatus(classId, student.getId(), GradeEntry.Status.PUBLISHED).stream())
                .map(this::toResponse).toList();
    }

    /**
     * UC-61: Overall/Level đã công bố (PUBLISHED) của 1 kỳ đánh giá, tự
     * xem — mirror đúng ParentPortalService.getPeriodResult. Bắt buộc học
     * sinh phải có class_enrollment ACTIVE tại đúng classId truy vấn
     * (không lộ dữ liệu của lớp không thuộc về mình).
     */
    @Transactional(readOnly = true)
    public GradePeriodResultResponse getMyPeriodResult(Long actorUserId, Long classId, Long gradePeriodId) {
        Student student = studentOrThrow(actorUserId);
        boolean enrolled = classEnrollmentRepository.findBySchoolClassIdAndStudentIdAndStatus(
                classId, student.getId(), ClassEnrollment.Status.ACTIVE).isPresent();
        if (!enrolled) {
            throw new ResourceNotFoundException("Không tìm thấy lớp học id=" + classId);
        }
        GradePeriodResult result = gradePeriodResultRepository
                .findBySchoolClassIdAndStudentIdAndGradePeriodId(classId, student.getId(), gradePeriodId)
                .filter(r -> r.getStatus() == GradePeriodResult.Status.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Chưa có điểm tổng kết đã công bố cho kỳ đánh giá id=" + gradePeriodId + "."));
        return toResponse(result);
    }

    private Student studentOrThrow(Long actorUserId) {
        return studentRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản id=" + actorUserId + " không có hồ sơ học sinh."));
    }

    // ===================== UC-20: Công bố điểm (SITE_MANAGER + HEAD_ACADEMIC) =====================

    /**
     * Main Flow bước 1: danh sách điểm chưa công bố. Yêu cầu quyền
     * academic.grade.publish (bổ sung ngoài SDD gốc, đã xác nhận với
     * người dùng). Nếu actor có thêm academic.grade.manage (Trưởng phòng
     * đào tạo) thì thấy MỌI site; ngược lại (Quản lý điểm trường) chỉ
     * thấy (các) điểm trường mình được gán phụ trách.
     */
    @Transactional(readOnly = true)
    public List<GradeEntryResponse> listUnpublishedForSite(Long actorUserId) {
        requireGradePublishPermission(actorUserId);
        if (permissionEvaluationService.hasPermission(actorUserId, "academic.grade.manage")) {
            return gradeEntryRepository.findByStatusOrderByEnteredAtAsc(GradeEntry.Status.DRAFT)
                    .stream().map(this::toResponse).toList();
        }
        List<Long> siteIds = siteManagerRepository
                .findByUserIdAndRoleTypeAndAssignedToIsNull(actorUserId, SiteManager.RoleType.SITE_MANAGER).stream()
                .map(sm -> sm.getSite().getId()).toList();
        return siteIds.stream()
                .flatMap(siteId -> gradeEntryRepository.findByStatusAndSiteId(GradeEntry.Status.DRAFT, siteId).stream())
                .map(this::toResponse)
                .toList();
    }

    /**
     * Main Flow bước 2-5: công bố điểm (DRAFT → PUBLISHED) — từ nay Phụ
     * huynh/Học sinh xem được. V39: không còn nhánh từ chối — bản ghi đã
     * PUBLISHED mà công bố lại thì báo lỗi ({@link GradeAlreadyPublishedException}).
     */
    @Transactional
    public List<GradeEntryResponse> publishGrades(PublishGradesRequest request, Long actorUserId) {
        User actor = getUserOrThrow(actorUserId);
        List<Long> entryIds = request.gradeEntryIds() == null ? List.of() : request.gradeEntryIds();
        List<Long> resultIds = request.gradePeriodResultIds() == null ? List.of() : request.gradePeriodResultIds();
        if (entryIds.isEmpty() && resultIds.isEmpty()) {
            throw new IllegalArgumentException("Phải truyền ít nhất 1 gradeEntryIds hoặc gradePeriodResultIds.");
        }
        List<GradeEntry> entries = gradeEntryRepository.findAllById(entryIds);
        if (entries.size() != entryIds.size()) {
            throw new ResourceNotFoundException("Có bản ghi điểm không tồn tại trong danh sách gradeEntryIds.");
        }
        List<GradePeriodResult> results = gradePeriodResultRepository.findAllById(resultIds);
        if (results.size() != resultIds.size()) {
            throw new ResourceNotFoundException("Có bản ghi Overall/Level không tồn tại trong danh sách gradePeriodResultIds.");
        }

        requireGradePublishPermission(actorUserId);
        OffsetDateTime now = OffsetDateTime.now();
        for (GradeEntry entry : entries) {
            requireCanPublishGrades(entry.getSchoolClass().getSite().getId(), actorUserId);
            if (entry.getStatus() == GradeEntry.Status.PUBLISHED) {
                throw new GradeAlreadyPublishedException(
                        "Bản ghi điểm id=" + entry.getId() + " đã được công bố trước đó.");
            }
            entry.setStatus(GradeEntry.Status.PUBLISHED);
            entry.setPublishedBy(actor);
            entry.setPublishedAt(now);
        }
        for (GradePeriodResult result : results) {
            requireCanPublishGrades(result.getSchoolClass().getSite().getId(), actorUserId);
            if (result.getStatus() == GradePeriodResult.Status.PUBLISHED) {
                throw new GradeAlreadyPublishedException(
                        "Bản ghi Overall/Level id=" + result.getId() + " đã được công bố trước đó.");
            }
            result.setStatus(GradePeriodResult.Status.PUBLISHED);
            result.setPublishedBy(actor);
            result.setPublishedAt(now);
        }
        List<GradePeriodResult> savedResults = gradePeriodResultRepository.saveAll(results);
        List<GradeEntry> saved = gradeEntryRepository.saveAll(entries);
        saved.forEach(e -> writeGradeEntryHistory(e, actor, GradeEntryHistory.Action.UPDATED));
        saved.forEach(e -> notifyParentsGradeEntryPublished(e, actor.getId()));
        savedResults.forEach(r -> notifyParentsPeriodResultPublished(r, actor.getId()));
        return saved.stream().map(this::toResponse).toList();
    }

    // ===================== Helpers =====================

    /**
     * UC-20 Main Flow bước 4 (bổ sung ngoài SDD gốc, đã xác nhận với người
     * dùng): thông báo mọi Phụ huynh liên kết (parent_student) với học sinh
     * có điểm thành phần vừa PUBLISHED. triggeredByUserId=null khi gọi từ
     * GradeSchedulerService (tự động, không có actor con người).
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
        metadata.put("gradeComponentId", entry.getGradeComponent().getId());
        for (ParentStudent link : links) {
            notificationService.notify(link.getParent().getUser().getId(), Notification.NotificationType.GRADE_PUBLISHED,
                    title, content, metadata, "GRADE_ENTRY", entry.getId(), Notification.Priority.NORMAL, triggeredByUserId);
        }
    }

    /** UC-20 Main Flow bước 4 — cùng cơ chế, cho Overall/Level theo kỳ đánh giá (UC-53). */
    void notifyParentsPeriodResultPublished(GradePeriodResult result, Long triggeredByUserId) {
        List<ParentStudent> links = parentStudentRepository.findByStudentId(result.getStudent().getId());
        if (links.isEmpty()) {
            return;
        }
        String title = "Điểm tổng kết đã được công bố";
        String content = "Điểm tổng kết kỳ \"" + result.getGradePeriod().getName() + "\" (lớp "
                + result.getSchoolClass().getName() + ") của " + result.getStudent().getUser().getFullName()
                + " đã được công bố" + (result.getLevel() == null ? "" : ": Level " + result.getLevel()) + ".";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("classId", result.getSchoolClass().getId());
        metadata.put("studentId", result.getStudent().getId());
        metadata.put("gradePeriodId", result.getGradePeriod().getId());
        for (ParentStudent link : links) {
            notificationService.notify(link.getParent().getUser().getId(), Notification.NotificationType.GRADE_PUBLISHED,
                    title, content, metadata, "GRADE_PERIOD_RESULT", result.getId(), Notification.Priority.NORMAL, triggeredByUserId);
        }
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
     * UC-19 A2 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): actor
     * sửa được điểm của (lớp, kỳ đánh giá) này nếu (a) có quyền
     * academic.grade.edit.override (ngoại lệ, gán được cho bất kỳ ai qua
     * UC-04 — mặc định HEAD_ACADEMIC + SITE_MANAGER, V39), HOẶC (b) chưa
     * hết hạn X ngày kể từ lần đầu nhập cho (lớp, kỳ) này
     * (grade_period_edit_windows, số ngày X đọc từ
     * {@link AcademicSettingsService#gradeEditWindowDays()}). Nếu (lớp,
     * kỳ) chưa từng có mốc (lần đầu nhập) thì luôn cho phép.
     */
    private void requireEditableOrOverride(Long classId, Long gradePeriodId, Long actorUserId) {
        if (permissionEvaluationService.hasPermission(actorUserId, "academic.grade.edit.override")) {
            return;
        }
        gradePeriodEditWindowRepository.findBySchoolClassIdAndGradePeriodId(classId, gradePeriodId)
                .ifPresent(window -> {
                    int days = academicSettingsService.gradeEditWindowDays();
                    OffsetDateTime deadline = window.getFirstEnteredAt().plusDays(days);
                    if (OffsetDateTime.now().isAfter(deadline)) {
                        throw new GradeEditWindowExpiredException(
                                "Đã hết hạn " + days + " ngày chỉnh sửa điểm của lớp id=" + classId
                                        + ", kỳ đánh giá id=" + gradePeriodId + " (kể từ " + window.getFirstEnteredAt() + ").");
                    }
                });
    }

    /**
     * V39: đánh dấu mốc "lần đầu nhập điểm" cho (lớp, kỳ đánh giá) nếu
     * chưa có — làm gốc tính hạn X ngày (UC-19 A2). Ghi 1 lần duy nhất,
     * gọi lại sau đó là no-op. Bắt DataIntegrityViolationException để
     * an toàn khi 2 bản ghi cùng lô import (UC-53) cùng cố tạo mốc.
     */
    private void ensureEditWindowStarted(Long classId, Long gradePeriodId) {
        if (gradePeriodEditWindowRepository.existsBySchoolClassIdAndGradePeriodId(classId, gradePeriodId)) {
            return;
        }
        try {
            GradePeriodEditWindow window = new GradePeriodEditWindow();
            window.setSchoolClass(getClassOrThrow(classId));
            window.setGradePeriod(gradePeriodRepository.getReferenceById(gradePeriodId));
            window.setFirstEnteredAt(OffsetDateTime.now());
            gradePeriodEditWindowRepository.save(window);
        } catch (DataIntegrityViolationException ignored) {
            // Race trong cùng batch import: bản ghi khác đã tạo mốc trước (UNIQUE class_id+grade_period_id).
        }
    }

    /**
     * UC-20 Precondition (mở rộng, bổ sung ngoài SDD gốc, đã xác nhận với
     * người dùng): actor phải có quyền academic.grade.publish (kiểm tra
     * riêng ở {@link #requireGradePublishPermission}, gọi 1 lần cho cả
     * lô trước khi lặp từng bản ghi). Nếu actor còn có
     * academic.grade.manage (Trưởng phòng đào tạo) thì công bố được MỌI
     * site, không giới hạn; ngược lại (Quản lý điểm trường) chỉ công bố
     * được đúng site mình được gán phụ trách (site_managers, row-level).
     */
    private void requireCanPublishGrades(Long siteId, Long actorUserId) {
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
    private void requireGradePublishPermission(Long actorUserId) {
        if (!permissionEvaluationService.hasPermission(actorUserId, "academic.grade.publish")) {
            throw new NotSiteManagerForSiteException(
                    "Tài khoản id=" + actorUserId + " không có quyền academic.grade.publish.");
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

    private void writeGradePeriodHistory(GradePeriod period, User actor, GradePeriodHistory.Action action) {
        GradePeriodHistory history = new GradePeriodHistory();
        history.setGradePeriod(period);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", period.getCode().name());
        snapshot.put("name", period.getName());
        snapshot.put("weightInFinal", period.getWeightInFinal());
        snapshot.put("status", period.getStatus().name());
        history.setDetails(snapshot);
        gradePeriodHistoryRepository.save(history);
    }

    private void writeGradeComponentHistory(GradeComponent component, User actor, GradeComponentHistory.Action action) {
        GradeComponentHistory history = new GradeComponentHistory();
        history.setGradeComponent(component);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", component.getCode().name());
        snapshot.put("name", component.getName());
        snapshot.put("maxScore", component.getMaxScore());
        history.setDetails(snapshot);
        gradeComponentHistoryRepository.save(history);
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

    private GradePeriodResponse toResponse(GradePeriod p) {
        return new GradePeriodResponse(
                p.getId(), p.getCurriculum().getId(), p.getCode().name(), p.getName(), p.getDisplayOrder(),
                p.getWeightInFinal(), p.getStartDate(), p.getEndDate(), p.getStatus().name());
    }

    private GradeComponentResponse toResponse(GradeComponent c) {
        return new GradeComponentResponse(
                c.getId(), c.getGradePeriod().getId(), c.getSubject() == null ? null : c.getSubject().getId(),
                c.getSkill() == null ? null : c.getSkill().getId(),
                c.getCode().name(), c.getName(), c.getMaxScore(), c.getPassThreshold(),
                c.getScaleType().name(), c.getDisplayOrder());
    }

    private GradeEntryResponse toResponse(GradeEntry e) {
        return new GradeEntryResponse(
                e.getId(), e.getSchoolClass().getId(), e.getStudent().getId(), e.getStudent().getUser().getFullName(),
                e.getStudent().getStudentCode(), e.getGradeComponent().getId(), e.getScore(), e.isAbsenceFlag(),
                e.getTeacherNote(), e.getStatus().name(), e.getEnteredBy().getId(),
                e.getPublishedBy() == null ? null : e.getPublishedBy().getId(), e.getPublishedAt());
    }

    private GradePeriodResultResponse toResponse(GradePeriodResult r) {
        return new GradePeriodResultResponse(
                r.getId(), r.getSchoolClass().getId(), r.getStudent().getId(),
                r.getStudent().getUser().getFullName(), r.getStudent().getStudentCode(),
                r.getGradePeriod().getId(), r.getOverallScore(), r.getScaleType().name(), r.getLevel(),
                r.getSource().name(), r.getImportJob() == null ? null : r.getImportJob().getId(),
                r.getStatus().name(), r.getEnteredBy().getId(),
                r.getPublishedBy() == null ? null : r.getPublishedBy().getId(), r.getPublishedAt());
    }
}
