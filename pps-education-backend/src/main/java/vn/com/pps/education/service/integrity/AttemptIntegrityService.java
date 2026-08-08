package vn.com.pps.education.service.integrity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AttemptIntegrityEvent;
import vn.com.pps.education.domain.AttemptIntegrityEvent.AttemptType;
import vn.com.pps.education.domain.ClassTeacher;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.ParentStudent;
import vn.com.pps.education.dto.IntegrityEventBatchResponse;
import vn.com.pps.education.dto.IntegritySummaryResponse;
import vn.com.pps.education.dto.RecordIntegrityEventsRequest;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.AttemptIntegrityEventRepository;
import vn.com.pps.education.repository.ClassTeacherRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.service.ExerciseAttemptService;
import vn.com.pps.education.service.NotificationService;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — giám sát
 * học sinh thoát ra ngoài (đổi tab/thu nhỏ/thoát fullscreen) khi đang làm
 * bài, báo phụ huynh VÀ giáo viên phụ trách lớp khi vượt ngưỡng cấu hình
 * (system_settings.integrity.*, xem migration V70). Xem
 * docs/uc/phan-he-07-lms-portal.md (blockquote "Bổ sung" ở UC-24/UC-27/
 * UC-23b) để biết đầy đủ ngữ cảnh.
 */
@Service
public class AttemptIntegrityService {

    private final List<AttemptIntegrityContextResolver> resolvers;
    private final AttemptIntegrityEventRepository attemptIntegrityEventRepository;
    private final IntegritySettings integritySettings;
    private final NotificationService notificationService;
    private final ParentStudentRepository parentStudentRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final ExerciseAttemptService exerciseAttemptService;

    public AttemptIntegrityService(List<AttemptIntegrityContextResolver> resolvers,
                                    AttemptIntegrityEventRepository attemptIntegrityEventRepository,
                                    IntegritySettings integritySettings,
                                    NotificationService notificationService,
                                    ParentStudentRepository parentStudentRepository,
                                    ClassTeacherRepository classTeacherRepository,
                                    ExerciseAttemptService exerciseAttemptService) {
        this.resolvers = resolvers;
        this.attemptIntegrityEventRepository = attemptIntegrityEventRepository;
        this.integritySettings = integritySettings;
        this.notificationService = notificationService;
        this.parentStudentRepository = parentStudentRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.exerciseAttemptService = exerciseAttemptService;
    }

    /**
     * Ghi 1 lô sự kiện "đã thoát ra ngoài rồi quay lại" cho 1 attempt của
     * ĐÚNG học sinh đang gửi (actorUserId). Sự kiện ngắn hơn
     * min_violation_duration_seconds bị lọc bỏ (nhiễu — VD popup xin
     * quyền, thoáng 1 nhịp). Khi tổng số lần HOẶC tổng thời lượng vượt
     * ngưỡng cấu hình VÀ chưa từng báo cho đúng attempt này, báo phụ
     * huynh + giáo viên phụ trách lớp (nếu xác định được lớp) đúng 1 lần.
     */
    @Transactional
    public IntegrityEventBatchResponse recordEvents(AttemptType type, Long attemptId,
                                                      RecordIntegrityEventsRequest request, Long actorUserId) {
        if (!integritySettings.isMonitoringEnabled()) {
            return new IntegrityEventBatchResponse(0, 0, 0, false, false);
        }
        AttemptContext ctx = resolverFor(type).resolveForOwner(attemptId, actorUserId);

        int minDurationSeconds = integritySettings.minViolationDurationSeconds();
        OffsetDateTime now = OffsetDateTime.now();
        int savedCount = 0;
        for (RecordIntegrityEventsRequest.Event event : request.events()) {
            long durationSeconds = Duration.between(event.startedAt(), event.endedAt()).getSeconds();
            if (durationSeconds < minDurationSeconds) {
                continue;
            }
            AttemptIntegrityEvent entity = new AttemptIntegrityEvent();
            entity.setAttemptType(type);
            entity.setAttemptId(attemptId);
            entity.setStudent(ctx.student());
            entity.setSchoolClass(ctx.schoolClass());
            entity.setEventType(AttemptIntegrityEvent.EventType.valueOf(event.eventType()));
            entity.setStartedAt(event.startedAt());
            entity.setEndedAt(event.endedAt());
            entity.setDurationSeconds((int) durationSeconds);
            entity.setClientReportedAt(now);
            entity.setUserAgent(event.userAgent());
            attemptIntegrityEventRepository.save(entity);
            savedCount++;
        }

        List<AttemptIntegrityEvent> allEvents = attemptIntegrityEventRepository.findByAttemptTypeAndAttemptId(type, attemptId);
        int totalCount = allEvents.size();
        int totalDurationSeconds = allEvents.stream().mapToInt(AttemptIntegrityEvent::getDurationSeconds).sum();

        boolean alreadyNotified = attemptIntegrityEventRepository
                .existsByAttemptTypeAndAttemptIdAndNotifiedAtIsNotNull(type, attemptId);
        boolean crossedThreshold = totalCount >= integritySettings.notifyViolationCountThreshold()
                || totalDurationSeconds >= integritySettings.notifyCumulativeDurationSecondsThreshold();
        boolean notifyNow = !alreadyNotified && crossedThreshold && savedCount > 0;
        boolean attemptStopped = false;
        if (notifyNow) {
            AttemptIntegrityEvent triggeringEvent = allEvents.get(allEvents.size() - 1);
            triggeringEvent.setNotifiedAt(now);
            attemptIntegrityEventRepository.save(triggeringEvent);
            notifyParentsAndTeachers(ctx, totalCount, totalDurationSeconds, actorUserId);

            // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06: dừng bài ép khi vượt
            // ngưỡng — chỉ áp dụng EXERCISE (Video REFLEX không có "đang làm dở" ở backend để dừng
            // giữa chừng, xem Javadoc lớp này).
            if (type == AttemptType.EXERCISE) {
                exerciseAttemptService.forceStopByIntegrityViolation(attemptId);
                attemptStopped = true;
            }
        }

        return new IntegrityEventBatchResponse(savedCount, totalCount, totalDurationSeconds, notifyNow, attemptStopped);
    }

    @Transactional(readOnly = true)
    public IntegritySummaryResponse getSummary(AttemptType type, Long attemptId) {
        resolverFor(type).resolveForReading(attemptId);
        List<AttemptIntegrityEvent> events = attemptIntegrityEventRepository.findByAttemptTypeAndAttemptId(type, attemptId);
        int totalDurationSeconds = events.stream().mapToInt(AttemptIntegrityEvent::getDurationSeconds).sum();
        boolean notified = events.stream().anyMatch(e -> e.getNotifiedAt() != null);
        return new IntegritySummaryResponse(events.size(), totalDurationSeconds, notified);
    }

    private void notifyParentsAndTeachers(AttemptContext ctx, int violationCount, int violationTotalSeconds, Long actorUserId) {
        Long studentId = ctx.student().getId();
        Long schoolClassId = ctx.schoolClass() == null ? null : ctx.schoolClass().getId();
        String title = "Học sinh thoát ra ngoài khi đang làm bài";
        String content = "Học sinh " + ctx.student().getUser().getFullName() + " đã thoát ra ngoài (đổi tab/thu nhỏ) "
                + violationCount + " lần, tổng cộng " + violationTotalSeconds + " giây, khi đang làm " + ctx.attemptLabel() + ".";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("studentId", studentId);
        metadata.put("schoolClassId", schoolClassId);
        metadata.put("violationCount", violationCount);
        metadata.put("violationTotalSeconds", violationTotalSeconds);
        metadata.put("studentName", ctx.student().getUser().getFullName());
        if (ctx.schoolClass() != null) {
            metadata.put("className", ctx.schoolClass().getName());
        }

        for (ParentStudent link : parentStudentRepository.findByStudentId(studentId)) {
            notificationService.notify(link.getParent().getUser().getId(), Notification.NotificationType.EXAM_INTEGRITY_VIOLATION_PARENT,
                    title, content, metadata, "STUDENT", studentId, Notification.Priority.HIGH, actorUserId);
        }

        if (schoolClassId != null) {
            for (ClassTeacher classTeacher : classTeacherRepository.findBySchoolClassIdAndAssignedToIsNull(schoolClassId)) {
                notificationService.notify(classTeacher.getTeacher().getId(), Notification.NotificationType.EXAM_INTEGRITY_VIOLATION,
                        title, content, metadata, "STUDENT", studentId, Notification.Priority.HIGH, actorUserId);
            }
        }
    }

    private AttemptIntegrityContextResolver resolverFor(AttemptType type) {
        return resolvers.stream().filter(r -> r.supports(type)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không hỗ trợ giám sát cho loại bài làm: " + type));
    }
}
