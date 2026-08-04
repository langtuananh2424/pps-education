package vn.com.pps.education.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ApprovalFlow;
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
import vn.com.pps.education.dto.SubmitGradesRequest;
import vn.com.pps.education.dto.UpdateGradeComponentRequest;
import vn.com.pps.education.dto.UpdateGradePeriodRequest;
import vn.com.pps.education.domain.Skill;
import vn.com.pps.education.exception.GradeAlreadyPublishedException;
import vn.com.pps.education.exception.GradeComponentLockedException;
import vn.com.pps.education.exception.GradeComponentNotDeletableException;
import vn.com.pps.education.exception.GradeNotEditableException;
import vn.com.pps.education.exception.GradePeriodNotDeletableException;
import vn.com.pps.education.exception.GradePeriodWeightExceededException;
import vn.com.pps.education.exception.InvalidGradeScoreException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.domain.CurriculumSubject;
import vn.com.pps.education.repository.ApprovalFlowRepository;
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
 * UC-19: Nhập điểm (FR-ACA-03) + UC-20: Duyệt/Từ chối điểm (FR-ACA-03).
 * Xem docs/uc/phan-he-06-hoc-thuat.md.
 *
 * Gộp cấu hình sổ điểm (grade_periods/grade_components, actor HEAD_ACADEMIC
 * — Precondition UC-19: "công thức đã cấu hình trong khung chương trình")
 * + nhập điểm (actor TEACHER) + duyệt điểm (actor SITE_MANAGER) vào 1
 * Service — tất cả đều phục vụ trực tiếp UC-19/20, KHÔNG đặt trong
 * CurriculumService (UC-16/16b/17) để tránh vi phạm SRP (xem
 * .claude/rules/solid.md, ví dụ "AcademicService khổng lồ gộp UC-16 +
 * UC-19/20").
 *
 * V44 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng — thay hẳn luồng
 * "công bố dự kiến + phúc khảo" (V43/UC-62) cũ): luồng 4 trạng thái
 * DRAFT → SUBMITTED → OFFICIAL / REJECTED, khoá sửa/xoá hoàn toàn theo
 * TRẠNG THÁI (xem {@link #requireEditableState}). Điểm CHỈ hiển thị cho
 * Phụ huynh/Học sinh khi đã OFFICIAL (Quản lý điểm trường duyệt) — không
 * còn khái niệm "công bố dự kiến" hay phúc khảo. Bị từ chối (REJECTED)
 * thì Giáo viên hoặc Quản lý sửa lại rồi gửi duyệt lại (SUBMITTED) hoặc
 * Quản lý duyệt thẳng (OFFICIAL). Actor có quyền
 * academic.grade.edit.override (mặc định HEAD_ACADEMIC + SITE_MANAGER,
 * gán thêm được cho người khác qua UC-04) bỏ qua mọi ràng buộc theo
 * trạng thái. Lịch sử gửi/duyệt/từ chối lưu trong {@link ApprovalFlow}
 * (entity_type=GRADE_ENTRY/GRADE_PERIOD_RESULT) — dùng lại đúng bảng
 * approval_flows đã thiết kế sẵn từ V1 cho UC-17, tránh tự sáng tác thêm
 * cột mới trên grade_entries/grade_period_results.
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
 * Duyệt/từ chối điểm (UC-20) dùng requireGradeApprovePermission +
 * requireCanApproveGrades — cần quyền academic.grade.approve (role mặc
 * định: SITE_MANAGER, HEAD_ACADEMIC — V38/V77); Quản lý điểm trường vẫn
 * giới hạn row-level đúng site mình phụ trách (site_managers), Trưởng
 * phòng đào tạo (có thêm academic.grade.manage) duyệt được mọi site.
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
    private final ApprovalFlowRepository approvalFlowRepository;

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
                         ApprovalFlowRepository approvalFlowRepository) {
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
        this.approvalFlowRepository = approvalFlowRepository;
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

    /**
     * UC-19 (xoá kỳ đánh giá, bổ sung ngoài SDD gốc — đã xác nhận với người
     * dùng 2026-07-22): chỉ xoá được kỳ RỖNG — chưa có thành phần điểm, chưa
     * có điểm tổng kết, và chưa bắt đầu nhập điểm ở lớp nào (không có cửa sổ
     * chỉnh sửa). Muốn xoá kỳ đã có thành phần: xoá từng thành phần trước.
     * Xoá cứng (kỳ chưa dùng, không có giá trị pháp lý cần giữ) — đối xứng
     * cách xoá điểm nháp (deleteGradeEntry). Quyền gate ở Controller.
     */
    @Transactional
    public void deleteGradePeriod(Long id, Long actorUserId) {
        GradePeriod period = gradePeriodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ đánh giá id=" + id));
        if (gradeComponentRepository.countByGradePeriodId(id) > 0) {
            throw new GradePeriodNotDeletableException(
                    "Kỳ đánh giá id=" + id + " còn thành phần điểm — xoá từng thành phần trước khi xoá kỳ.");
        }
        if (gradePeriodResultRepository.countByGradePeriodId(id) > 0) {
            throw new GradePeriodNotDeletableException(
                    "Kỳ đánh giá id=" + id + " đã có điểm tổng kết — không thể xoá.");
        }
        if (gradePeriodEditWindowRepository.existsByGradePeriodId(id)) {
            throw new GradePeriodNotDeletableException(
                    "Kỳ đánh giá id=" + id + " đã bắt đầu nhập điểm — không thể xoá.");
        }
        gradePeriodHistoryRepository.deleteByGradePeriodId(id);
        gradePeriodRepository.delete(period);
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

    /**
     * UC-19 (xoá thành phần điểm, bổ sung ngoài SDD gốc — đã xác nhận với
     * người dùng 2026-07-22): chỉ xoá được khi CHƯA có điểm nhập nào
     * (grade_entries) — đối xứng với rule khoá maxScore. Xoá cứng (thành
     * phần chưa có điểm, không có giá trị pháp lý cần giữ). Quyền gate ở
     * Controller.
     */
    @Transactional
    public void deleteGradeComponent(Long id, Long actorUserId) {
        GradeComponent component = gradeComponentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thành phần điểm id=" + id));
        if (gradeEntryRepository.countByGradeComponentId(id) > 0) {
            throw new GradeComponentNotDeletableException(
                    "Thành phần điểm id=" + id + " đã có điểm nhập — không thể xoá.");
        }
        gradeComponentHistoryRepository.deleteByGradeComponentId(id);
        gradeComponentRepository.delete(component);
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
        if (entry == null) {
            entry = new GradeEntry();
            entry.setSchoolClass(schoolClass);
            entry.setStudent(student);
            entry.setGradeComponent(component);
            entry.setEnteredBy(actor);
        } else {
            action = GradeEntryHistory.Action.UPDATED;
            requireEditableState(entry.getStatus(), entry.getId(), actorUserId);
        }
        entry.setScore(request.score());
        entry.setAbsenceFlag(request.absenceFlag());
        entry.setTeacherNote(request.teacherNote());
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
        if (result == null) {
            result = new GradePeriodResult();
            result.setSchoolClass(schoolClass);
            result.setStudent(student);
            result.setGradePeriod(period);
        } else {
            requireEditableState(result.getStatus(), result.getId(), actorUserId);
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
     * Phụ huynh). Trả về mọi grade_entries đã OFFICIAL (V44 — đã Quản lý
     * điểm trường duyệt, DRAFT/SUBMITTED/REJECTED không hiển thị), theo
     * (các) lớp học sinh ĐÃ TỪNG ghi danh (kể cả lớp cũ sau khi chuyển
     * lớp — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29,
     * mirror đúng ParentPortalService.listGrades/requireAccessToChildClass,
     * không tự tính lại điểm gì). classIdFilter tùy chọn (ngữ cảnh "lớp
     * đang xem" — UC-42).
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
     * UC-61: Overall/Level đã OFFICIAL (V44) của 1 kỳ đánh giá, tự xem —
     * mirror đúng ParentPortalService.getPeriodResult. Bắt buộc học sinh
     * phải ĐÃ TỪNG có class_enrollment tại đúng classId truy vấn (không
     * lộ dữ liệu của lớp không thuộc về mình) — kể cả lớp cũ sau khi
     * chuyển lớp (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * 2026-07-29).
     */
    @Transactional(readOnly = true)
    public GradePeriodResultResponse getMyPeriodResult(Long actorUserId, Long classId, Long gradePeriodId) {
        Student student = studentOrThrow(actorUserId);
        if (!classEnrollmentRepository.existsByStudentIdAndSchoolClassId(student.getId(), classId)) {
            throw new ResourceNotFoundException("Không tìm thấy lớp học id=" + classId);
        }
        GradePeriodResult result = gradePeriodResultRepository
                .findBySchoolClassIdAndStudentIdAndGradePeriodId(classId, student.getId(), gradePeriodId)
                .filter(r -> r.getStatus() == GradePeriodResult.Status.OFFICIAL)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Chưa có điểm tổng kết đã duyệt cho kỳ đánh giá id=" + gradePeriodId + "."));
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

        for (GradeEntry entry : entries) {
            requireCanEnterGrades(entry.getSchoolClass().getId(), actorUserId);
            requireSubmittable(entry.getStatus().name(), entry.getId());
            entry.setStatus(GradeEntry.Status.SUBMITTED);
            openPendingApprovalFlow(ApprovalFlow.EntityType.GRADE_ENTRY, entry.getId(), actor);
        }
        for (GradePeriodResult result : results) {
            requireCanEnterGrades(result.getSchoolClass().getId(), actorUserId);
            requireSubmittable(result.getStatus().name(), result.getId());
            result.setStatus(GradePeriodResult.Status.SUBMITTED);
            openPendingApprovalFlow(ApprovalFlow.EntityType.GRADE_PERIOD_RESULT, result.getId(), actor);
        }
        gradePeriodResultRepository.saveAll(results);
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
     * — chấp nhận cả REJECTED để hỗ trợ A2 (Quản lý tự sửa bản ghi bị từ
     * chối rồi duyệt thẳng, không cần Giáo viên gửi lại). 4b/A1 Từ chối:
     * chỉ chấp nhận SUBMITTED → REJECTED, ghi nhận người từ chối + thời
     * điểm + lý do (tuỳ chọn), thông báo Giáo viên. A3: bản ghi không còn
     * ở trạng thái phù hợp (đã OFFICIAL, hoặc REJECT trên bản ghi không
     * SUBMITTED) → báo lỗi ({@link GradeAlreadyPublishedException}).
     */
    @Transactional
    public List<GradeEntryResponse> publishGrades(PublishGradesRequest request, Long actorUserId) {
        User actor = getUserOrThrow(actorUserId);
        ApprovalFlow.Decision decision = parseDecision(request.action());
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

        requireGradeApprovePermission(actorUserId);
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
        for (GradePeriodResult result : results) {
            requireCanApproveGrades(result.getSchoolClass().getSite().getId(), actorUserId);
            decideApprovalFlow(ApprovalFlow.EntityType.GRADE_PERIOD_RESULT, result.getId(), result.getStatus().name(),
                    decision, actor, request.rejectReason(), now);
            if (decision == ApprovalFlow.Decision.APPROVED) {
                result.setStatus(GradePeriodResult.Status.OFFICIAL);
                result.setPublishedBy(actor);
                result.setPublishedAt(now);
            } else {
                result.setStatus(GradePeriodResult.Status.REJECTED);
            }
        }
        List<GradePeriodResult> savedResults = gradePeriodResultRepository.saveAll(results);
        List<GradeEntry> saved = gradeEntryRepository.saveAll(entries);
        saved.forEach(e -> writeGradeEntryHistory(e, actor, GradeEntryHistory.Action.UPDATED));
        if (decision == ApprovalFlow.Decision.APPROVED) {
            saved.forEach(e -> notifyParentsGradeEntryPublished(e, actor.getId()));
            savedResults.forEach(r -> notifyParentsPeriodResultPublished(r, actor.getId()));
        } else {
            saved.forEach(e -> notifyTeacherGradeEntryRejected(e, request.rejectReason(), actor.getId()));
            savedResults.forEach(r -> notifyTeacherPeriodResultRejected(r, request.rejectReason(), actor.getId()));
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
        metadata.put("gradeComponentId", entry.getGradeComponent().getId());
        for (ParentStudent link : links) {
            notificationService.notify(link.getParent().getUser().getId(), Notification.NotificationType.GRADE_PUBLISHED,
                    title, content, metadata, "GRADE_ENTRY", entry.getId(), Notification.Priority.NORMAL, triggeredByUserId);
        }
    }

    /** UC-20 Main Flow bước 4a — cùng cơ chế, cho Overall/Level theo kỳ đánh giá (UC-53). */
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

    /** UC-20 A1/4b: thông báo Giáo viên đã nhập điểm khi bản ghi bị từ chối, kèm lý do nếu có. */
    void notifyTeacherGradeEntryRejected(GradeEntry entry, String rejectReason, Long triggeredByUserId) {
        String title = "Điểm bị từ chối";
        String content = "Điểm thành phần \"" + entry.getGradeComponent().getName() + "\" (lớp "
                + entry.getSchoolClass().getName() + ") của " + entry.getStudent().getUser().getFullName()
                + " đã bị từ chối." + (rejectReason == null || rejectReason.isBlank() ? "" : " Lý do: " + rejectReason);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("classId", entry.getSchoolClass().getId());
        metadata.put("studentId", entry.getStudent().getId());
        metadata.put("gradeComponentId", entry.getGradeComponent().getId());
        notificationService.notify(entry.getEnteredBy().getId(), Notification.NotificationType.GRADE_REJECTED,
                title, content, metadata, "GRADE_ENTRY", entry.getId(), Notification.Priority.HIGH, triggeredByUserId);
    }

    /** UC-20 A1/4b — cùng cơ chế, cho Overall/Level theo kỳ đánh giá (UC-53). */
    void notifyTeacherPeriodResultRejected(GradePeriodResult result, String rejectReason, Long triggeredByUserId) {
        String title = "Điểm tổng kết bị từ chối";
        String content = "Điểm tổng kết kỳ \"" + result.getGradePeriod().getName() + "\" (lớp "
                + result.getSchoolClass().getName() + ") của " + result.getStudent().getUser().getFullName()
                + " đã bị từ chối." + (rejectReason == null || rejectReason.isBlank() ? "" : " Lý do: " + rejectReason);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("classId", result.getSchoolClass().getId());
        metadata.put("studentId", result.getStudent().getId());
        metadata.put("gradePeriodId", result.getGradePeriod().getId());
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

    private void requireEditableState(GradePeriodResult.Status status, Long entityId, Long actorUserId) {
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
