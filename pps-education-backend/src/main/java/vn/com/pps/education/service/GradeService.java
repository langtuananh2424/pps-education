package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ApprovalFlow;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.GradeComponent;
import vn.com.pps.education.domain.GradeComponentHistory;
import vn.com.pps.education.domain.GradeEntry;
import vn.com.pps.education.domain.GradeEntryHistory;
import vn.com.pps.education.domain.GradePeriod;
import vn.com.pps.education.domain.GradePeriodHistory;
import vn.com.pps.education.domain.GradePeriodResult;
import vn.com.pps.education.domain.ImportJob;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateGradeComponentRequest;
import vn.com.pps.education.dto.CreateGradePeriodRequest;
import vn.com.pps.education.dto.DecideGradesRequest;
import vn.com.pps.education.dto.EnterGradePeriodResultRequest;
import vn.com.pps.education.dto.EnterGradeRequest;
import vn.com.pps.education.dto.GradeComponentResponse;
import vn.com.pps.education.dto.GradeEntryResponse;
import vn.com.pps.education.dto.GradePeriodResponse;
import vn.com.pps.education.dto.GradePeriodResultResponse;
import vn.com.pps.education.dto.PeriodAverageResponse;
import vn.com.pps.education.dto.SubmitGradesRequest;
import vn.com.pps.education.dto.UpdateGradeComponentRequest;
import vn.com.pps.education.dto.UpdateGradePeriodRequest;
import vn.com.pps.education.domain.Skill;
import vn.com.pps.education.exception.ApprovalAlreadyDecidedException;
import vn.com.pps.education.exception.GradeComponentLockedException;
import vn.com.pps.education.exception.GradeComponentWeightExceededException;
import vn.com.pps.education.exception.GradeEntryNotEditableException;
import vn.com.pps.education.exception.GradePeriodWeightExceededException;
import vn.com.pps.education.exception.InvalidGradeScoreException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.domain.CurriculumSubject;
import vn.com.pps.education.repository.ApprovalFlowRepository;
import vn.com.pps.education.repository.ClassTeacherRepository;
import vn.com.pps.education.repository.CurriculumRepository;
import vn.com.pps.education.repository.CurriculumSubjectRepository;
import vn.com.pps.education.repository.GradeComponentHistoryRepository;
import vn.com.pps.education.repository.GradeComponentRepository;
import vn.com.pps.education.repository.GradeEntryHistoryRepository;
import vn.com.pps.education.repository.GradeEntryRepository;
import vn.com.pps.education.repository.GradePeriodHistoryRepository;
import vn.com.pps.education.repository.GradePeriodRepository;
import vn.com.pps.education.repository.GradePeriodResultRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SkillRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * UC-19: Nhập điểm (FR-ACA-03) + UC-20: Duyệt điểm (FR-ACA-03).
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
 * Dùng lại ApprovalFlow (entity_type=GRADE_ENTRY) — mỗi grade_entry có 1
 * approval_flow riêng, nhiều entry submit cùng lúc (theo lô) chia sẻ 1
 * batchId, giống pattern student_comments mô tả trong SDD.
 *
 * Cấu hình sổ điểm (HEAD_ACADEMIC) qua
 * @PreAuthorize("hasPermission(null,'academic.grade.manage')") ở
 * GradeController (Hybrid PBAC — V28). Nhập điểm (TEACHER)/duyệt điểm
 * (SITE_MANAGER) vẫn dùng requireAssignedTeacher/requireSiteManagerForSite —
 * row-level scope check (đúng lớp/site cụ thể), không phải role-hardcode.
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
    private final ApprovalFlowRepository approvalFlowRepository;
    private final CurriculumRepository curriculumRepository;
    private final CurriculumSubjectRepository curriculumSubjectRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final StudentRepository studentRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final SiteManagerRepository siteManagerRepository;
    private final SkillRepository skillRepository;
    private final UserRepository userRepository;

    public GradeService(GradePeriodRepository gradePeriodRepository,
                         GradeComponentRepository gradeComponentRepository,
                         GradeEntryRepository gradeEntryRepository,
                         GradePeriodResultRepository gradePeriodResultRepository,
                         GradePeriodHistoryRepository gradePeriodHistoryRepository,
                         GradeComponentHistoryRepository gradeComponentHistoryRepository,
                         GradeEntryHistoryRepository gradeEntryHistoryRepository,
                         ApprovalFlowRepository approvalFlowRepository,
                         CurriculumRepository curriculumRepository,
                         CurriculumSubjectRepository curriculumSubjectRepository,
                         SchoolClassRepository schoolClassRepository,
                         StudentRepository studentRepository,
                         ClassTeacherRepository classTeacherRepository,
                         SiteManagerRepository siteManagerRepository,
                         SkillRepository skillRepository,
                         UserRepository userRepository) {
        this.gradePeriodRepository = gradePeriodRepository;
        this.gradeComponentRepository = gradeComponentRepository;
        this.gradeEntryRepository = gradeEntryRepository;
        this.gradePeriodResultRepository = gradePeriodResultRepository;
        this.gradePeriodHistoryRepository = gradePeriodHistoryRepository;
        this.gradeComponentHistoryRepository = gradeComponentHistoryRepository;
        this.gradeEntryHistoryRepository = gradeEntryHistoryRepository;
        this.approvalFlowRepository = approvalFlowRepository;
        this.curriculumRepository = curriculumRepository;
        this.curriculumSubjectRepository = curriculumSubjectRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.studentRepository = studentRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.siteManagerRepository = siteManagerRepository;
        this.skillRepository = skillRepository;
        this.userRepository = userRepository;
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
     * A2 bước 2: tổng weightInPeriod của kỳ (kể cả thành phần mới) ≤ 100.
     */
    @Transactional
    public GradeComponentResponse addGradeComponent(Long gradePeriodId, CreateGradeComponentRequest request, Long actorUserId) {
        GradePeriod period = gradePeriodRepository.findById(gradePeriodId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ đánh giá id=" + gradePeriodId));
        User actor = getUserOrThrow(actorUserId);

        BigDecimal currentTotal = gradeComponentRepository.findByGradePeriodIdOrderByDisplayOrder(gradePeriodId)
                .stream().map(GradeComponent::getWeightInPeriod).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (currentTotal.add(request.weightInPeriod()).compareTo(new BigDecimal("100")) > 0) {
            throw new GradeComponentWeightExceededException(
                    "Tổng weightInPeriod các thành phần điểm của kỳ id=" + gradePeriodId + " sẽ vượt quá 100.");
        }

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
        component.setWeightInPeriod(request.weightInPeriod());
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

    /** SDD: nếu đã có grade_entries cho component này, cấm sửa weightInPeriod/maxScore. */
    @Transactional
    public GradeComponentResponse updateGradeComponent(Long id, UpdateGradeComponentRequest request, Long actorUserId) {
        GradeComponent component = gradeComponentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thành phần điểm id=" + id));
        User actor = getUserOrThrow(actorUserId);

        boolean weightChanged = component.getWeightInPeriod().compareTo(request.weightInPeriod()) != 0;
        BigDecimal newMaxScore = request.maxScore() == null ? component.getMaxScore() : request.maxScore();
        boolean maxScoreChanged = component.getMaxScore().compareTo(newMaxScore) != 0;
        if ((weightChanged || maxScoreChanged) && gradeEntryRepository.countByGradeComponentId(id) > 0) {
            throw new GradeComponentLockedException(
                    "Thành phần điểm id=" + id + " đã có điểm nhập — không được sửa weightInPeriod/maxScore.");
        }

        component.setName(request.name());
        component.setWeightInPeriod(request.weightInPeriod());
        component.setMaxScore(newMaxScore);
        component.setPassThreshold(request.passThreshold());
        component.setDisplayOrder(request.displayOrder() == null ? component.getDisplayOrder() : request.displayOrder());
        component = gradeComponentRepository.save(component);

        writeGradeComponentHistory(component, actor, GradeComponentHistory.Action.UPDATED);
        return toResponse(component);
    }

    // ===================== UC-19: Nhập điểm (TEACHER) =====================

    /** Main Flow bước 1-3, A1: nhập/sửa điểm 1 học sinh cho 1 thành phần điểm. */
    @Transactional
    public GradeEntryResponse enterGrade(Long classId, Long gradeComponentId, EnterGradeRequest request, Long actorUserId) {
        SchoolClass schoolClass = getClassOrThrow(classId);
        requireAssignedTeacher(classId, actorUserId);
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
            if (entry.getStatus() != GradeEntry.Status.DRAFT && entry.getStatus() != GradeEntry.Status.REJECTED) {
                throw new GradeEntryNotEditableException(
                        "Bản ghi điểm id=" + entry.getId() + " đang ở trạng thái " + entry.getStatus()
                                + " — chỉ sửa được khi DRAFT hoặc REJECTED.");
            }
            action = GradeEntryHistory.Action.UPDATED;
            // A2 -- điểm bị từ chối, Giáo viên sửa lại thì quay về DRAFT để submit lại (UC-19).
            entry.setStatus(GradeEntry.Status.DRAFT);
            entry.setApprovalFlow(null);
        }
        entry.setScore(request.score());
        entry.setAbsenceFlag(request.absenceFlag());
        entry.setTeacherNote(request.teacherNote());
        entry = gradeEntryRepository.save(entry);

        writeGradeEntryHistory(entry, actor, action);
        return toResponse(entry);
    }

    @Transactional(readOnly = true)
    public List<GradeEntryResponse> listEntries(Long classId, Long gradeComponentId) {
        return gradeEntryRepository.findBySchoolClassIdAndGradeComponentIdOrderByStudentId(classId, gradeComponentId)
                .stream().map(this::toResponse).toList();
    }

    /**
     * Main Flow bước 4-6: submit từng bản ghi hoặc theo lô (batch_id) sang
     * Chờ duyệt. Từ UC-53: có thể submit kèm/riêng các bản ghi Overall/Level
     * (gradePeriodResultIds) — cùng batch_id khi submit chung 1 lần gọi.
     */
    @Transactional
    public List<GradeEntryResponse> submitGrades(Long classId, SubmitGradesRequest request, Long actorUserId) {
        requireAssignedTeacher(classId, actorUserId);
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
        UUID batchId = entries.size() + results.size() > 1 ? UUID.randomUUID() : null;
        OffsetDateTime now = OffsetDateTime.now();

        for (GradeEntry entry : entries) {
            if (!entry.getSchoolClass().getId().equals(classId)) {
                throw new ResourceNotFoundException("Bản ghi điểm id=" + entry.getId() + " không thuộc lớp id=" + classId);
            }
            if (entry.getStatus() != GradeEntry.Status.DRAFT) {
                throw new GradeEntryNotEditableException(
                        "Bản ghi điểm id=" + entry.getId() + " đang ở trạng thái " + entry.getStatus() + " — chỉ submit được khi DRAFT.");
            }
            ApprovalFlow flow = new ApprovalFlow();
            flow.setEntityType(ApprovalFlow.EntityType.GRADE_ENTRY);
            flow.setEntityId(entry.getId());
            flow.setStatus(ApprovalFlow.Status.PENDING);
            flow.setSubmittedBy(actor);
            flow.setBatchId(batchId);
            flow = approvalFlowRepository.save(flow);
            entry.setApprovalFlow(flow);
            entry.setStatus(GradeEntry.Status.PENDING);
            entry.setSubmittedAt(now);
        }
        for (GradePeriodResult result : results) {
            if (!result.getSchoolClass().getId().equals(classId)) {
                throw new ResourceNotFoundException("Bản ghi Overall/Level id=" + result.getId() + " không thuộc lớp id=" + classId);
            }
            if (result.getStatus() != GradePeriodResult.Status.DRAFT) {
                throw new GradeEntryNotEditableException(
                        "Bản ghi Overall/Level id=" + result.getId() + " đang ở trạng thái " + result.getStatus() + " — chỉ submit được khi DRAFT.");
            }
            ApprovalFlow flow = new ApprovalFlow();
            flow.setEntityType(ApprovalFlow.EntityType.GRADE_PERIOD_RESULT);
            flow.setEntityId(result.getId());
            flow.setStatus(ApprovalFlow.Status.PENDING);
            flow.setSubmittedBy(actor);
            flow.setBatchId(batchId);
            flow = approvalFlowRepository.save(flow);
            result.setApprovalFlow(flow);
            result.setStatus(GradePeriodResult.Status.PENDING);
            result.setSubmittedAt(now);
        }
        gradePeriodResultRepository.saveAll(results);
        List<GradeEntry> saved = gradeEntryRepository.saveAll(entries);
        saved.forEach(e -> writeGradeEntryHistory(e, actor, GradeEntryHistory.Action.UPDATED));
        return saved.stream().map(this::toResponse).toList();
    }

    /** UC-19 Postcondition: điểm trung bình học phần tạm thời (trọng số theo weightInPeriod, chỉ trên thành phần đã có điểm). */
    @Transactional(readOnly = true)
    public PeriodAverageResponse getPeriodAverage(Long classId, Long studentId, Long gradePeriodId) {
        List<GradeComponent> components = gradeComponentRepository.findByGradePeriodIdOrderByDisplayOrder(gradePeriodId);
        List<GradeEntry> entries = gradeEntryRepository.findBySchoolClassIdAndStudentId(classId, studentId).stream()
                .filter(e -> e.getGradeComponent().getGradePeriod().getId().equals(gradePeriodId))
                .toList();
        Map<Long, GradeEntry> byComponentId = entries.stream()
                .collect(Collectors.toMap(e -> e.getGradeComponent().getId(), e -> e));

        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weightSum = BigDecimal.ZERO;
        int entered = 0;
        for (GradeComponent component : components) {
            GradeEntry entry = byComponentId.get(component.getId());
            if (entry == null) {
                continue;
            }
            entered++;
            weightedSum = weightedSum.add(entry.getScore().multiply(component.getWeightInPeriod()));
            weightSum = weightSum.add(component.getWeightInPeriod());
        }
        BigDecimal average = weightSum.signum() == 0
                ? null
                : weightedSum.divide(weightSum, 2, RoundingMode.HALF_UP);
        return new PeriodAverageResponse(classId, studentId, gradePeriodId, average, entered, components.size());
    }

    // ===================== UC-53: Overall/Level theo kỳ đánh giá =====================

    /**
     * UC-53 Main Flow bước 3 (nhánh Overall/Level): lưu nguyên giá trị GV đã
     * tính sẵn — hệ thống KHÔNG tự tính lại công thức. Chỉ sửa được khi
     * DRAFT/REJECTED (giống enterGrade), sửa xong quay về DRAFT.
     */
    @Transactional
    public GradePeriodResultResponse enterPeriodResult(Long classId, Long studentId, Long gradePeriodId,
                                                       EnterGradePeriodResultRequest request, Long actorUserId) {
        requireAssignedTeacher(classId, actorUserId);
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
        } else if (result.getStatus() != GradePeriodResult.Status.DRAFT
                && result.getStatus() != GradePeriodResult.Status.REJECTED) {
            throw new GradeEntryNotEditableException(
                    "Bản ghi Overall/Level id=" + result.getId() + " đang ở trạng thái " + result.getStatus()
                            + " — chỉ sửa được khi DRAFT hoặc REJECTED.");
        }
        result.setStatus(GradePeriodResult.Status.DRAFT);
        result.setApprovalFlow(null);
        result.setOverallScore(request.overallScore());
        if (request.scaleType() != null) {
            result.setScaleType(GradePeriodResult.ScaleType.valueOf(request.scaleType()));
        }
        result.setLevel(request.level());
        result.setSource(source);
        result.setImportJob(importJob);
        result.setEnteredBy(actor);
        result.setEnteredAt(OffsetDateTime.now());
        return gradePeriodResultRepository.save(result);
    }

    @Transactional(readOnly = true)
    public List<GradePeriodResultResponse> listPeriodResults(Long classId, Long gradePeriodId) {
        return gradePeriodResultRepository.findBySchoolClassIdAndGradePeriodIdOrderByStudentId(classId, gradePeriodId)
                .stream().map(this::toResponse).toList();
    }

    // ===================== UC-20: Duyệt điểm (SITE_MANAGER) =====================

    /** Main Flow bước 1: danh sách điểm Chờ duyệt của các điểm trường actor phụ trách. */
    @Transactional(readOnly = true)
    public List<GradeEntryResponse> listPendingForSite(Long actorUserId) {
        List<Long> siteIds = siteManagerRepository
                .findByUserIdAndRoleTypeAndAssignedToIsNull(actorUserId, SiteManager.RoleType.SITE_MANAGER).stream()
                .map(sm -> sm.getSite().getId()).toList();
        return siteIds.stream()
                .flatMap(siteId -> gradeEntryRepository.findByStatusAndSiteId(GradeEntry.Status.PENDING, siteId).stream())
                .map(this::toResponse)
                .toList();
    }

    /**
     * Main Flow bước 2-5, A1 (duyệt tách lẻ — truyền đúng 1 id): quyết định
     * APPROVED (công khai cho Phụ huynh) hoặc REJECTED (trả về Giáo viên —
     * UC-19 A2).
     */
    @Transactional
    public List<GradeEntryResponse> decideGrades(DecideGradesRequest request, Long actorUserId) {
        User actor = getUserOrThrow(actorUserId);
        ApprovalFlow.Decision decision = ApprovalFlow.Decision.valueOf(request.decision());
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

        OffsetDateTime now = OffsetDateTime.now();
        for (GradeEntry entry : entries) {
            requireSiteManagerForSite(entry.getSchoolClass().getSite().getId(), actorUserId);
            if (entry.getStatus() != GradeEntry.Status.PENDING) {
                throw new ApprovalAlreadyDecidedException(
                        "Bản ghi điểm id=" + entry.getId() + " đã được quyết định (" + entry.getStatus() + ").");
            }
            ApprovalFlow flow = entry.getApprovalFlow();
            flow.setDecision(decision);
            flow.setApprover(actor);
            flow.setComment(request.comment());
            flow.setDecidedAt(now);

            if (decision == ApprovalFlow.Decision.APPROVED) {
                flow.setStatus(ApprovalFlow.Status.APPROVED);
                entry.setStatus(GradeEntry.Status.APPROVED);
                entry.setApprovedBy(actor);
                entry.setApprovedAt(now);
            } else {
                flow.setStatus(ApprovalFlow.Status.REJECTED);
                entry.setStatus(GradeEntry.Status.REJECTED);
            }
        }
        for (GradePeriodResult result : results) {
            requireSiteManagerForSite(result.getSchoolClass().getSite().getId(), actorUserId);
            if (result.getStatus() != GradePeriodResult.Status.PENDING) {
                throw new ApprovalAlreadyDecidedException(
                        "Bản ghi Overall/Level id=" + result.getId() + " đã được quyết định (" + result.getStatus() + ").");
            }
            ApprovalFlow flow = result.getApprovalFlow();
            flow.setDecision(decision);
            flow.setApprover(actor);
            flow.setComment(request.comment());
            flow.setDecidedAt(now);

            if (decision == ApprovalFlow.Decision.APPROVED) {
                flow.setStatus(ApprovalFlow.Status.APPROVED);
                result.setStatus(GradePeriodResult.Status.APPROVED);
                result.setApprovedBy(actor);
                result.setApprovedAt(now);
            } else {
                flow.setStatus(ApprovalFlow.Status.REJECTED);
                result.setStatus(GradePeriodResult.Status.REJECTED);
            }
        }
        gradePeriodResultRepository.saveAll(results);
        List<GradeEntry> saved = gradeEntryRepository.saveAll(entries);
        saved.forEach(e -> writeGradeEntryHistory(e, actor, GradeEntryHistory.Action.UPDATED));
        return saved.stream().map(this::toResponse).toList();
    }

    // ===================== Helpers =====================

    private void requireAssignedTeacher(Long classId, Long actorUserId) {
        if (!classTeacherRepository.existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(classId, actorUserId)) {
            throw new NotAssignedTeacherForClassException(
                    "Tài khoản id=" + actorUserId + " không được phân công giảng dạy lớp id=" + classId + ".");
        }
    }

    private void requireSiteManagerForSite(Long siteId, Long actorUserId) {
        if (!siteManagerRepository.existsBySiteIdAndUserIdAndRoleTypeAndAssignedToIsNull(
                siteId, actorUserId, SiteManager.RoleType.SITE_MANAGER)) {
            throw new NotSiteManagerForSiteException(
                    "Tài khoản id=" + actorUserId + " không được gán phụ trách điểm trường id=" + siteId + ".");
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
        snapshot.put("weightInPeriod", component.getWeightInPeriod());
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
                c.getCode().name(), c.getName(), c.getWeightInPeriod(), c.getMaxScore(), c.getPassThreshold(),
                c.getScaleType().name(), c.getDisplayOrder());
    }

    private GradeEntryResponse toResponse(GradeEntry e) {
        return new GradeEntryResponse(
                e.getId(), e.getSchoolClass().getId(), e.getStudent().getId(), e.getStudent().getUser().getFullName(),
                e.getStudent().getStudentCode(), e.getGradeComponent().getId(), e.getScore(), e.isAbsenceFlag(),
                e.getTeacherNote(), e.getStatus().name(), e.getEnteredBy().getId(), e.getSubmittedAt(),
                e.getApprovedBy() == null ? null : e.getApprovedBy().getId(), e.getApprovedAt());
    }

    private GradePeriodResultResponse toResponse(GradePeriodResult r) {
        return new GradePeriodResultResponse(
                r.getId(), r.getSchoolClass().getId(), r.getStudent().getId(),
                r.getStudent().getUser().getFullName(), r.getStudent().getStudentCode(),
                r.getGradePeriod().getId(), r.getOverallScore(), r.getScaleType().name(), r.getLevel(),
                r.getSource().name(), r.getImportJob() == null ? null : r.getImportJob().getId(),
                r.getStatus().name(), r.getEnteredBy().getId(), r.getSubmittedAt(),
                r.getApprovedBy() == null ? null : r.getApprovedBy().getId(), r.getApprovedAt());
    }
}
