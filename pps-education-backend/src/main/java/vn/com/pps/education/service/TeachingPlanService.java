package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.TeachingPlan;
import vn.com.pps.education.domain.TeachingPlanHistory;
import vn.com.pps.education.domain.TeachingPlanItem;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AddTeachingPlanItemRequest;
import vn.com.pps.education.dto.CreateTeachingPlanRequest;
import vn.com.pps.education.dto.TeachingPlanItemResponse;
import vn.com.pps.education.dto.TeachingPlanResponse;
import vn.com.pps.education.dto.UpdateTeachingPlanItemRequest;
import vn.com.pps.education.dto.UpdateTeachingPlanRequest;
import vn.com.pps.education.exception.InvalidTeachingPlanPeriodException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.ClassTeacherRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.TeachingPlanHistoryRepository;
import vn.com.pps.education.repository.TeachingPlanItemRepository;
import vn.com.pps.education.repository.TeachingPlanRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-28: Điền kế hoạch giảng dạy (FR-LMS-08). Xem docs/uc/phan-he-07-lms-portal.md.
 * Không có bước duyệt — lưu (DRAFT) hoặc gửi (PUBLISHED) hiển thị ngay
 * cho Portal trường liên kết (UC-29, đọc trực tiếp qua
 * {@code findBySchoolClassIdAndStatusAndVisibleToPartnerTrueOrderByIdDesc}).
 */
@Service
public class TeachingPlanService {

    private final TeachingPlanRepository teachingPlanRepository;
    private final TeachingPlanItemRepository teachingPlanItemRepository;
    private final TeachingPlanHistoryRepository teachingPlanHistoryRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ClassSessionRepository classSessionRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final UserRepository userRepository;

    public TeachingPlanService(TeachingPlanRepository teachingPlanRepository,
                                TeachingPlanItemRepository teachingPlanItemRepository,
                                TeachingPlanHistoryRepository teachingPlanHistoryRepository,
                                SchoolClassRepository schoolClassRepository,
                                ClassSessionRepository classSessionRepository,
                                ClassTeacherRepository classTeacherRepository,
                                UserRepository userRepository) {
        this.teachingPlanRepository = teachingPlanRepository;
        this.teachingPlanItemRepository = teachingPlanItemRepository;
        this.teachingPlanHistoryRepository = teachingPlanHistoryRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.classSessionRepository = classSessionRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.userRepository = userRepository;
    }

    /** Main Flow bước 1-3: chọn lớp + kỳ lập kế hoạch, nhập nội dung, lưu (DRAFT). */
    @Transactional
    public TeachingPlanResponse createPlan(CreateTeachingPlanRequest request, Long actorUserId) {
        SchoolClass schoolClass = getClassOrThrow(request.classId());
        requireAssignedTeacher(request.classId(), actorUserId);
        User actor = getUserOrThrow(actorUserId);

        TeachingPlan.PlanType planType = TeachingPlan.PlanType.valueOf(request.planType());
        if (planType == TeachingPlan.PlanType.WEEKLY) {
            if (request.weekStartDate() == null || request.weekEndDate() == null) {
                throw new InvalidTeachingPlanPeriodException("plan_type=WEEKLY phải có weekStartDate và weekEndDate.");
            }
        } else if (request.academicYear() == null || request.academicYear().isBlank()) {
            throw new InvalidTeachingPlanPeriodException("plan_type=YEARLY phải có academicYear.");
        }

        TeachingPlan plan = new TeachingPlan();
        plan.setSchoolClass(schoolClass);
        plan.setTeacher(actor);
        plan.setPlanType(planType);
        plan.setAcademicYear(request.academicYear());
        plan.setWeekNumber(request.weekNumber());
        plan.setWeekStartDate(request.weekStartDate());
        plan.setWeekEndDate(request.weekEndDate());
        plan.setSummary(request.summary());
        plan.setObjectives(request.objectives());
        plan.setVisibleToPartner(request.visibleToPartner());
        plan = teachingPlanRepository.save(plan);

        writeHistory(plan, actor, TeachingPlanHistory.Action.CREATED);
        return toResponse(plan);
    }

    /** Main Flow bước 3-4, A1 (cập nhật kế hoạch đã gửi): lưu/gửi (PUBLISHED) — hiển thị ngay cho Portal trường liên kết. */
    @Transactional
    public TeachingPlanResponse updatePlan(Long id, UpdateTeachingPlanRequest request, Long actorUserId) {
        TeachingPlan plan = getPlanOrThrow(id);
        requireAssignedTeacher(plan.getSchoolClass().getId(), actorUserId);
        User actor = getUserOrThrow(actorUserId);

        plan.setSummary(request.summary());
        plan.setObjectives(request.objectives());
        plan.setVisibleToPartner(request.visibleToPartner());
        TeachingPlan.Status newStatus = TeachingPlan.Status.valueOf(request.status());
        if (newStatus == TeachingPlan.Status.PUBLISHED && plan.getStatus() != TeachingPlan.Status.PUBLISHED) {
            plan.setPublishedAt(OffsetDateTime.now());
        }
        plan.setStatus(newStatus);
        plan = teachingPlanRepository.save(plan);

        writeHistory(plan, actor, TeachingPlanHistory.Action.UPDATED);
        return toResponse(plan);
    }

    @Transactional(readOnly = true)
    public List<TeachingPlanResponse> listByClass(Long classId) {
        return teachingPlanRepository.findBySchoolClassIdOrderByIdDesc(classId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TeachingPlanResponse getPlan(Long id) {
        return toResponse(getPlanOrThrow(id));
    }

    /** Main Flow bước 2: thêm 1 mục chi tiết (chủ đề/mục tiêu/nội dung dự kiến) vào kế hoạch. */
    @Transactional
    public TeachingPlanItemResponse addItem(Long planId, AddTeachingPlanItemRequest request, Long actorUserId) {
        TeachingPlan plan = getPlanOrThrow(planId);
        requireAssignedTeacher(plan.getSchoolClass().getId(), actorUserId);

        TeachingPlanItem item = new TeachingPlanItem();
        item.setTeachingPlan(plan);
        item.setItemOrder(request.itemOrder());
        item.setPlannedDate(request.plannedDate());
        item.setTopic(request.topic());
        item.setObjectives(request.objectives());
        item.setContentOutline(request.contentOutline());
        item.setSkillsFocus(request.skillsFocus());
        item.setHomeworkNote(request.homeworkNote());
        if (request.classSessionId() != null) {
            ClassSession session = classSessionRepository.findById(request.classSessionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy buổi học id=" + request.classSessionId()));
            item.setClassSession(session);
        }
        item = teachingPlanItemRepository.save(item);
        return toResponse(item);
    }

    @Transactional(readOnly = true)
    public List<TeachingPlanItemResponse> listItems(Long planId) {
        return teachingPlanItemRepository.findByTeachingPlanIdOrderByItemOrder(planId).stream().map(this::toResponse).toList();
    }

    /** UC-28 A1: chỉnh sửa 1 mục chi tiết đã lập trong kế hoạch. */
    @Transactional
    public TeachingPlanItemResponse updateItem(Long planId, Long itemId, UpdateTeachingPlanItemRequest request, Long actorUserId) {
        TeachingPlan plan = getPlanOrThrow(planId);
        requireAssignedTeacher(plan.getSchoolClass().getId(), actorUserId);
        TeachingPlanItem item = getItemOrThrow(planId, itemId);

        item.setItemOrder(request.itemOrder());
        item.setPlannedDate(request.plannedDate());
        item.setTopic(request.topic());
        item.setObjectives(request.objectives());
        item.setContentOutline(request.contentOutline());
        item.setSkillsFocus(request.skillsFocus());
        item.setHomeworkNote(request.homeworkNote());
        if (request.classSessionId() == null) {
            item.setClassSession(null);
        } else {
            ClassSession session = classSessionRepository.findById(request.classSessionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy buổi học id=" + request.classSessionId()));
            item.setClassSession(session);
        }
        item = teachingPlanItemRepository.save(item);
        return toResponse(item);
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

    private SchoolClass getClassOrThrow(Long id) {
        return schoolClassRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học id=" + id));
    }

    private TeachingPlan getPlanOrThrow(Long id) {
        return teachingPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kế hoạch giảng dạy id=" + id));
    }

    private TeachingPlanItem getItemOrThrow(Long planId, Long itemId) {
        TeachingPlanItem item = teachingPlanItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mục kế hoạch id=" + itemId));
        if (!item.getTeachingPlan().getId().equals(planId)) {
            throw new ResourceNotFoundException("Không tìm thấy mục kế hoạch id=" + itemId + " thuộc kế hoạch id=" + planId);
        }
        return item;
    }

    private void writeHistory(TeachingPlan plan, User actor, TeachingPlanHistory.Action action) {
        TeachingPlanHistory history = new TeachingPlanHistory();
        history.setTeachingPlan(plan);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("planType", plan.getPlanType().name());
        snapshot.put("status", plan.getStatus().name());
        snapshot.put("visibleToPartner", plan.isVisibleToPartner());
        history.setDetails(snapshot);
        teachingPlanHistoryRepository.save(history);
    }

    private TeachingPlanResponse toResponse(TeachingPlan p) {
        return new TeachingPlanResponse(
                p.getId(), p.getSchoolClass().getId(), p.getTeacher().getId(), p.getPlanType().name(),
                p.getAcademicYear(), p.getWeekNumber(), p.getWeekStartDate(), p.getWeekEndDate(),
                p.getSummary(), p.getObjectives(), p.getStatus().name(), p.isVisibleToPartner(), p.getPublishedAt());
    }

    private TeachingPlanItemResponse toResponse(TeachingPlanItem i) {
        return new TeachingPlanItemResponse(
                i.getId(), i.getTeachingPlan().getId(), i.getItemOrder(), i.getPlannedDate(), i.getTopic(),
                i.getObjectives(), i.getContentOutline(), i.getSkillsFocus(), i.getHomeworkNote(),
                i.getClassSession() == null ? null : i.getClassSession().getId());
    }
}
