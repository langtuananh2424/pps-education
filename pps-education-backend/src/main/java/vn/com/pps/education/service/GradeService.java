package vn.com.pps.education.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.GradeAppealRequest;
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
import vn.com.pps.education.exception.GradeNotEditableException;
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
import vn.com.pps.education.repository.GradeAppealRequestRepository;
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
 * UC-19: Nhập điểm (FR-ACA-03) + UC-20: Công bố điểm dự kiến (FR-ACA-03).
 * Xem docs/uc/phan-he-06-hoc-thuat.md. Phần "sửa điểm trong lúc phúc
 * khảo" (UC-62) cũng nằm ở đây (enterGrade/upsertPeriodResult dùng
 * chung) — chỉ việc gửi/tiếp nhận yêu cầu phúc khảo tách sang
 * {@link GradeAppealService} riêng (SRP).
 *
 * Gộp cấu hình sổ điểm (grade_periods/grade_components, actor HEAD_ACADEMIC
 * — Precondition UC-19: "công thức đã cấu hình trong khung chương trình")
 * + nhập điểm (actor TEACHER) + công bố điểm (actor SITE_MANAGER) vào 1
 * Service — tất cả đều phục vụ trực tiếp UC-19/20, KHÔNG đặt trong
 * CurriculumService (UC-16/16b/17) để tránh vi phạm SRP (xem
 * .claude/rules/solid.md, ví dụ "AcademicService khổng lồ gộp UC-16 +
 * UC-19/20").
 *
 * V43 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng — sửa đổi lần 2
 * sau V39): luồng 4 trạng thái DRAFT → PROVISIONAL_PUBLISHED → APPEAL →
 * OFFICIAL, khoá sửa/xoá hoàn toàn theo TRẠNG THÁI (không còn theo hạn X
 * ngày như V39 — xem {@link #requireEditableState}). X ngày
 * (grade_period_edit_windows, {@link AcademicSettingsService#gradeEditWindowDays()})
 * giờ CHỈ còn ý nghĩa "độ trễ tự động công bố điểm dự kiến" (UC-20 A3),
 * không còn là hạn chỉnh sửa. Y ngày mới
 * ({@link AcademicSettingsService#gradeAppealWindowDays()}) là hạn phúc
 * khảo kể từ lúc công bố điểm dự kiến — hết hạn thì GradeSchedulerService
 * tự động chuyển OFFICIAL bất kể còn PROVISIONAL_PUBLISHED hay APPEAL.
 * Actor có quyền academic.grade.edit.override (mặc định HEAD_ACADEMIC +
 * SITE_MANAGER, gán thêm được cho người khác qua UC-04) bỏ qua mọi ràng
 * buộc theo trạng thái. "Công bố điểm dự kiến" (permission
 * academic.grade.publish) chỉ còn là quyết định thời điểm Phụ huynh/Học
 * sinh được xem điểm (DRAFT → PROVISIONAL_PUBLISHED) — không còn nhánh
 * từ chối.
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
 * Công bố điểm dự kiến (UC-20) dùng requireGradePublishPermission +
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
    private final GradeAppealRequestRepository gradeAppealRequestRepository;

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
                         NotificationService notificationService,
                         GradeAppealRequestRepository gradeAppealRequestRepository) {
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
        this.gradeAppealRequestRepository = gradeAppealRequestRepository;
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
     * V43: bản ghi mới (chưa tồn tại) luôn tạo được (DRAFT). Sửa bản ghi
     * đã tồn tại phải qua {@link #requireEditableState} — DRAFT sửa tự
     * do, APPEAL chỉ sửa được nếu actor là người đã tiếp nhận yêu cầu
     * phúc khảo (UC-62), PROVISIONAL_PUBLISHED/OFFICIAL bị chặn (trừ
     * academic.grade.edit.override). Sửa xong lúc đang APPEAL tự động
     * quay lại PROVISIONAL_PUBLISHED + đóng yêu cầu phúc khảo (RESOLVED).
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

        User actor = getUserOrThrow(actorUserId);
        GradeEntry entry = gradeEntryRepository
                .findBySchoolClassIdAndStudentIdAndGradeComponentId(classId, request.studentId(), gradeComponentId)
                .orElse(null);
        GradeEntryHistory.Action action = GradeEntryHistory.Action.CREATED;
        boolean wasAppeal = false;
        if (entry == null) {
            entry = new GradeEntry();
            entry.setSchoolClass(schoolClass);
            entry.setStudent(student);
            entry.setGradeComponent(component);
            entry.setEnteredBy(actor);
        } else {
            action = GradeEntryHistory.Action.UPDATED;
            requireEditableState(entry.getStatus(), entry.getId(), actorUserId);
            wasAppeal = entry.getStatus() == GradeEntry.Status.APPEAL;
        }
        entry.setScore(request.score());
        entry.setAbsenceFlag(request.absenceFlag());
        entry.setTeacherNote(request.teacherNote());
        if (wasAppeal) {
            resolveAcceptedAppeal(GradeAppealRequest.EntityType.GRADE_ENTRY, entry.getId());
            entry.setStatus(GradeEntry.Status.PROVISIONAL_PUBLISHED);
        }
        entry = gradeEntryRepository.save(entry);

        ensureEditWindowStarted(classId, component.getGradePeriod().getId());
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
    public void deleteGradeEntry(Long classId, Long gradeComponentId, Long studentId, Long actorUserId) {
        requireCanEnterGrades(classId, actorUserId);
        GradeEntry entry = gradeEntryRepository
                .findBySchoolClassIdAndStudentIdAndGradeComponentId(classId, studentId, gradeComponentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy điểm của học sinh id=" + studentId + " cho thành phần id=" + gradeComponentId));
        requireEditableState(entry.getStatus(), entry.getId(), actorUserId);
        gradeEntryHistoryRepository.deleteByGradeEntryId(entry.getId());
        gradeEntryRepository.delete(entry);
    }

    @Transactional(readOnly = true)
    public List<GradeEntryResponse> listEntries(Long classId, Long gradeComponentId) {
        return gradeEntryRepository.findBySchoolClassIdAndGradeComponentIdOrderByStudentId(classId, gradeComponentId)
                .stream().map(this::toResponse).toList();
    }

    // ===================== UC-53: Overall/Level theo kỳ đánh giá =====================

    /**
     * UC-53 Main Flow bước 3 (nhánh Overall/Level): lưu nguyên giá trị GV đã
     * tính sẵn — hệ thống KHÔNG tự tính lại công thức. V43: cùng cơ chế
     * khoá theo trạng thái với enterGrade — xem Javadoc ở đó.
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

        GradePeriodResult result = gradePeriodResultRepository
                .findBySchoolClassIdAndStudentIdAndGradePeriodId(classId, studentId, gradePeriodId)
                .orElse(null);
        boolean wasAppeal = false;
        if (result == null) {
            result = new GradePeriodResult();
            result.setSchoolClass(schoolClass);
            result.setStudent(student);
            result.setGradePeriod(period);
        } else {
            requireEditableState(result.getStatus(), result.getId(), actorUserId);
            wasAppeal = result.getStatus() == GradePeriodResult.Status.APPEAL;
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
        if (wasAppeal) {
            resolveAcceptedAppeal(GradeAppealRequest.EntityType.GRADE_PERIOD_RESULT, result.getId());
            result.setStatus(GradePeriodResult.Status.PROVISIONAL_PUBLISHED);
        }
        result = gradePeriodResultRepository.save(result);

        ensureEditWindowStarted(classId, gradePeriodId);
        return result;
    }

    /**
     * UC-53 (xoá điểm tổng kết kỳ nháp, bổ sung ngoài SDD gốc, đã xác
     * nhận với người dùng) — cùng ràng buộc với deleteGradeEntry: chỉ
     * DRAFT (hoặc override), xoá cứng, không có bảng history riêng cho
     * grade_period_results nên không cần dọn gì thêm trước khi xoá.
     */
    @Transactional
    public void deletePeriodResult(Long classId, Long studentId, Long gradePeriodId, Long actorUserId) {
        requireCanEnterGrades(classId, actorUserId);
        GradePeriodResult result = gradePeriodResultRepository
                .findBySchoolClassIdAndStudentIdAndGradePeriodId(classId, studentId, gradePeriodId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy điểm tổng kết của học sinh id=" + studentId + " cho kỳ đánh giá id=" + gradePeriodId));
        requireEditableState(result.getStatus(), result.getId(), actorUserId);
        gradePeriodResultRepository.delete(result);
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
     * Phụ huynh). Trả về mọi grade_entries đã công bố dự kiến trở lên
     * (khác DRAFT — V43: PROVISIONAL_PUBLISHED/APPEAL/OFFICIAL đều hiển
     * thị, kể cả đang phúc khảo), theo (các) lớp học sinh đang ghi danh
     * ACTIVE — mirror đúng ParentPortalService.listGrades, không tự tính
     * lại điểm gì. classIdFilter tùy chọn (ngữ cảnh "lớp đang xem" — UC-42).
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
                        .findBySchoolClassIdAndStudentIdAndStatusNot(classId, student.getId(), GradeEntry.Status.DRAFT).stream())
                .map(this::toResponse).toList();
    }

    /**
     * UC-61: Overall/Level đã công bố dự kiến trở lên (khác DRAFT — V43)
     * của 1 kỳ đánh giá, tự xem — mirror đúng
     * ParentPortalService.getPeriodResult. Bắt buộc học sinh phải có
     * class_enrollment ACTIVE tại đúng classId truy vấn (không lộ dữ
     * liệu của lớp không thuộc về mình).
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
                .filter(r -> r.getStatus() != GradePeriodResult.Status.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Chưa có điểm tổng kết đã công bố cho kỳ đánh giá id=" + gradePeriodId + "."));
        return toResponse(result);
    }

    private Student studentOrThrow(Long actorUserId) {
        return studentRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản id=" + actorUserId + " không có hồ sơ học sinh."));
    }

    // ===================== UC-20: Công bố điểm dự kiến (SITE_MANAGER + HEAD_ACADEMIC) =====================

    /**
     * Main Flow bước 1: danh sách điểm chưa công bố dự kiến (còn DRAFT).
     * Yêu cầu quyền academic.grade.publish (bổ sung ngoài SDD gốc, đã xác
     * nhận với người dùng). Nếu actor có thêm academic.grade.manage
     * (Trưởng phòng đào tạo) thì thấy MỌI site; ngược lại (Quản lý điểm
     * trường) chỉ thấy (các) điểm trường mình được gán phụ trách.
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
     * Main Flow bước 2-5: công bố điểm dự kiến (DRAFT → PROVISIONAL_PUBLISHED)
     * — từ nay Phụ huynh/Học sinh xem được, và bắt đầu tính hạn Y ngày
     * phúc khảo (UC-62) kể từ publishedAt. V43: không còn nhánh từ chối —
     * bản ghi không còn DRAFT (đã PROVISIONAL_PUBLISHED/APPEAL/OFFICIAL)
     * mà công bố lại thì báo lỗi ({@link GradeAlreadyPublishedException}).
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
            if (entry.getStatus() != GradeEntry.Status.DRAFT) {
                throw new GradeAlreadyPublishedException(
                        "Bản ghi điểm id=" + entry.getId() + " đã được công bố dự kiến trước đó.");
            }
            entry.setStatus(GradeEntry.Status.PROVISIONAL_PUBLISHED);
            entry.setPublishedBy(actor);
            entry.setPublishedAt(now);
        }
        for (GradePeriodResult result : results) {
            requireCanPublishGrades(result.getSchoolClass().getSite().getId(), actorUserId);
            if (result.getStatus() != GradePeriodResult.Status.DRAFT) {
                throw new GradeAlreadyPublishedException(
                        "Bản ghi Overall/Level id=" + result.getId() + " đã được công bố dự kiến trước đó.");
            }
            result.setStatus(GradePeriodResult.Status.PROVISIONAL_PUBLISHED);
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
     * V43 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng — thay hẳn
     * cơ chế hạn X ngày của V39): actor sửa/xoá được bản ghi điểm khi (a)
     * có quyền academic.grade.edit.override (bỏ qua mọi ràng buộc theo
     * trạng thái, gán được cho bất kỳ ai qua UC-04 — mặc định HEAD_ACADEMIC
     * + SITE_MANAGER), (b) status=DRAFT (không giới hạn thời gian), hoặc
     * (c) status=APPEAL VÀ actor chính là người đã tiếp nhận (ACCEPTED)
     * yêu cầu phúc khảo (UC-62) của đúng bản ghi này.
     * PROVISIONAL_PUBLISHED/OFFICIAL luôn bị chặn với actor thường.
     */
    private void requireEditableState(GradeEntry.Status status, Long entityId, Long actorUserId) {
        requireEditableStateInternal(status.name(), GradeAppealRequest.EntityType.GRADE_ENTRY, entityId, actorUserId);
    }

    private void requireEditableState(GradePeriodResult.Status status, Long entityId, Long actorUserId) {
        requireEditableStateInternal(status.name(), GradeAppealRequest.EntityType.GRADE_PERIOD_RESULT, entityId, actorUserId);
    }

    private void requireEditableStateInternal(String statusName, GradeAppealRequest.EntityType entityType,
                                                Long entityId, Long actorUserId) {
        if (permissionEvaluationService.hasPermission(actorUserId, "academic.grade.edit.override")) {
            return;
        }
        if ("DRAFT".equals(statusName)) {
            return;
        }
        if ("APPEAL".equals(statusName)) {
            boolean acceptedByActor = gradeAppealRequestRepository
                    .findFirstByEntityTypeAndEntityIdAndStatus(entityType, entityId, GradeAppealRequest.Status.ACCEPTED)
                    .map(req -> req.getAcceptedBy() != null && req.getAcceptedBy().getId().equals(actorUserId))
                    .orElse(false);
            if (acceptedByActor) {
                return;
            }
            throw new GradeNotEditableException(
                    "Bản ghi điểm id=" + entityId + " đang Phúc khảo — chỉ giáo viên đã tiếp nhận yêu cầu phúc khảo này mới được sửa.");
        }
        throw new GradeNotEditableException(
                "Bản ghi điểm id=" + entityId + " ở trạng thái " + statusName + " không thể sửa/xoá.");
    }

    /**
     * UC-62: sửa điểm xong trong lúc đang Phúc khảo = tự động coi là xử
     * lý xong — đóng yêu cầu phúc khảo ACCEPTED (RESOLVED). publishedAt
     * của bản ghi điểm GIỮ NGUYÊN (không reset hạn Y ngày, xem
     * GradeService lớp Javadoc). Gọi từ enterGrade/upsertPeriodResult
     * TRƯỚC khi set lại status=PROVISIONAL_PUBLISHED.
     */
    private void resolveAcceptedAppeal(GradeAppealRequest.EntityType entityType, Long entityId) {
        gradeAppealRequestRepository
                .findFirstByEntityTypeAndEntityIdAndStatus(entityType, entityId, GradeAppealRequest.Status.ACCEPTED)
                .ifPresent(req -> {
                    req.setStatus(GradeAppealRequest.Status.RESOLVED);
                    req.setResolvedAt(OffsetDateTime.now());
                    gradeAppealRequestRepository.save(req);
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
                e.getPublishedBy() == null ? null : e.getPublishedBy().getId(), e.getPublishedAt(), e.getFinalizedAt());
    }

    private GradePeriodResultResponse toResponse(GradePeriodResult r) {
        return new GradePeriodResultResponse(
                r.getId(), r.getSchoolClass().getId(), r.getStudent().getId(),
                r.getStudent().getUser().getFullName(), r.getStudent().getStudentCode(),
                r.getGradePeriod().getId(), r.getOverallScore(), r.getScaleType().name(), r.getLevel(),
                r.getSource().name(), r.getImportJob() == null ? null : r.getImportJob().getId(),
                r.getStatus().name(), r.getEnteredBy().getId(),
                r.getPublishedBy() == null ? null : r.getPublishedBy().getId(), r.getPublishedAt(), r.getFinalizedAt());
    }
}
