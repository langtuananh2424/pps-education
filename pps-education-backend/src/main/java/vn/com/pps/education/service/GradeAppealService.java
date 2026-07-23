package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassTeacher;
import vn.com.pps.education.domain.GradeAppealRequest;
import vn.com.pps.education.domain.GradeEntry;
import vn.com.pps.education.domain.GradePeriodResult;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.Parent;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.GradeAppealResponse;
import vn.com.pps.education.dto.SubmitGradeAppealRequest;
import vn.com.pps.education.exception.AppealAlreadyAcceptedException;
import vn.com.pps.education.exception.AppealAlreadyOpenException;
import vn.com.pps.education.exception.GradeNotEditableException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.NotAuthorizedForPortalAccessException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ClassTeacherRepository;
import vn.com.pps.education.repository.GradeAppealRequestRepository;
import vn.com.pps.education.repository.GradeEntryRepository;
import vn.com.pps.education.repository.GradePeriodResultRepository;
import vn.com.pps.education.repository.ParentRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-62: Phúc khảo điểm (FR-ACA-03, bổ sung ngoài SDD gốc, đã xác nhận
 * với người dùng). Xem docs/uc/phan-he-06-hoc-thuat.md. Tách riêng khỏi
 * {@link GradeService} theo SRP (SOLID) — đây là nhóm nghiệp vụ actor
 * Học sinh/Phụ huynh (gửi) + Giáo viên (tiếp nhận), khác nhóm actor
 * Giáo viên/SITE_MANAGER của UC-19/20. Việc SỬA điểm thực tế sau khi
 * tiếp nhận vẫn dùng {@link GradeService#enterGrade}/
 * {@link GradeService#upsertPeriodResult} (không lặp lại logic chấm
 * điểm) — 2 Service chia sẻ chung {@link GradeAppealRequestRepository}.
 */
@Service
public class GradeAppealService {

    private final GradeAppealRequestRepository gradeAppealRequestRepository;
    private final GradeEntryRepository gradeEntryRepository;
    private final GradePeriodResultRepository gradePeriodResultRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public GradeAppealService(GradeAppealRequestRepository gradeAppealRequestRepository,
                               GradeEntryRepository gradeEntryRepository,
                               GradePeriodResultRepository gradePeriodResultRepository,
                               StudentRepository studentRepository,
                               ParentRepository parentRepository,
                               ParentStudentRepository parentStudentRepository,
                               ClassTeacherRepository classTeacherRepository,
                               UserRepository userRepository,
                               NotificationService notificationService) {
        this.gradeAppealRequestRepository = gradeAppealRequestRepository;
        this.gradeEntryRepository = gradeEntryRepository;
        this.gradePeriodResultRepository = gradePeriodResultRepository;
        this.studentRepository = studentRepository;
        this.parentRepository = parentRepository;
        this.parentStudentRepository = parentStudentRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /**
     * Main Flow bước 1-2: Học sinh (chủ sở hữu) hoặc Phụ huynh liên kết
     * gửi yêu cầu phúc khảo trên 1 bản ghi điểm đang PROVISIONAL_PUBLISHED.
     * Đổi status bản ghi -> APPEAL ngay (không chờ GV tiếp nhận), thông
     * báo mọi giáo viên phụ trách lớp — GV phải tiếp nhận
     * ({@link #acceptAppeal}) mới được sửa điểm (xem
     * GradeService#requireEditableState).
     */
    @Transactional
    public GradeAppealResponse submitAppeal(SubmitGradeAppealRequest request, Long actorUserId) {
        GradeAppealRequest.EntityType entityType = parseEntityType(request.entityType());
        User actor = getUserOrThrow(actorUserId);

        SchoolClass schoolClass;
        Student student;
        if (entityType == GradeAppealRequest.EntityType.GRADE_ENTRY) {
            GradeEntry entry = gradeEntryRepository.findById(request.entityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bản ghi điểm id=" + request.entityId()));
            student = entry.getStudent();
            requireOwnership(student.getId(), actorUserId);
            requireOpenForAppeal(entry.getStatus().name(), request.entityId());
            schoolClass = entry.getSchoolClass();
            entry.setStatus(GradeEntry.Status.APPEAL);
            gradeEntryRepository.save(entry);
        } else {
            GradePeriodResult result = gradePeriodResultRepository.findById(request.entityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bản ghi điểm tổng kết id=" + request.entityId()));
            student = result.getStudent();
            requireOwnership(student.getId(), actorUserId);
            requireOpenForAppeal(result.getStatus().name(), request.entityId());
            schoolClass = result.getSchoolClass();
            result.setStatus(GradePeriodResult.Status.APPEAL);
            gradePeriodResultRepository.save(result);
        }

        GradeAppealRequest appeal = new GradeAppealRequest();
        appeal.setEntityType(entityType);
        appeal.setEntityId(request.entityId());
        appeal.setSchoolClass(schoolClass);
        appeal.setStudent(student);
        appeal.setRequestedBy(actor);
        appeal.setReason(request.reason());
        appeal = gradeAppealRequestRepository.save(appeal);

        notifyClassTeachers(appeal);
        return toResponse(appeal);
    }

    /**
     * Main Flow bước 3: Giáo viên phụ trách lớp tiếp nhận yêu cầu — từ
     * đây được quyền sửa điểm của đúng học sinh này (UC-19,
     * GradeService#requireEditableState).
     */
    @Transactional
    public GradeAppealResponse acceptAppeal(Long appealRequestId, Long actorUserId) {
        GradeAppealRequest appeal = gradeAppealRequestRepository.findById(appealRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu phúc khảo id=" + appealRequestId));
        if (!classTeacherRepository.existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(
                appeal.getSchoolClass().getId(), actorUserId)) {
            throw new NotAssignedTeacherForClassException(
                    "Tài khoản id=" + actorUserId + " không được phân công giảng dạy lớp id=" + appeal.getSchoolClass().getId() + ".");
        }
        // A2 -- yêu cầu đã được GV khác tiếp nhận, hoặc đã xử lý xong.
        if (appeal.getStatus() != GradeAppealRequest.Status.PENDING) {
            throw new AppealAlreadyAcceptedException(
                    "Yêu cầu phúc khảo id=" + appealRequestId + " đã được tiếp nhận hoặc xử lý xong trước đó.");
        }
        appeal.setStatus(GradeAppealRequest.Status.ACCEPTED);
        appeal.setAcceptedBy(getUserOrThrow(actorUserId));
        appeal.setAcceptedAt(OffsetDateTime.now());
        appeal = gradeAppealRequestRepository.save(appeal);
        return toResponse(appeal);
    }

    /** GV xem hàng chờ phúc khảo (PENDING) của (các) lớp mình đang phụ trách. */
    @Transactional(readOnly = true)
    public List<GradeAppealResponse> listPendingForMyClasses(Long actorUserId) {
        List<Long> classIds = classTeacherRepository.findByTeacherIdAndAssignedToIsNull(actorUserId).stream()
                .map(ct -> ct.getSchoolClass().getId())
                .toList();
        if (classIds.isEmpty()) {
            return List.of();
        }
        return gradeAppealRequestRepository.findBySchoolClass_IdInAndStatusOrderByCreatedAtAsc(classIds, GradeAppealRequest.Status.PENDING)
                .stream().map(this::toResponse).toList();
    }

    /** Học sinh/Phụ huynh tự xem lịch sử phúc khảo đã gửi. */
    @Transactional(readOnly = true)
    public List<GradeAppealResponse> listMyAppeals(Long actorUserId) {
        return gradeAppealRequestRepository.findByRequestedByIdOrderByCreatedAtDesc(actorUserId)
                .stream().map(this::toResponse).toList();
    }

    // ===================== Helpers =====================

    private GradeAppealRequest.EntityType parseEntityType(String raw) {
        try {
            return GradeAppealRequest.EntityType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("entityType phải là GRADE_ENTRY hoặc GRADE_PERIOD_RESULT, nhận được: " + raw);
        }
    }

    /** Precondition UC-62: actor phải là chính học sinh sở hữu bản ghi, hoặc Phụ huynh liên kết với học sinh đó. */
    private void requireOwnership(Long studentId, Long actorUserId) {
        boolean isOwningStudent = studentRepository.findByUserId(actorUserId)
                .map(s -> s.getId().equals(studentId)).orElse(false);
        if (isOwningStudent) {
            return;
        }
        Parent parent = parentRepository.findByUserId(actorUserId).orElse(null);
        boolean isLinkedParent = parent != null
                && parentStudentRepository.findByParentIdAndStudentId(parent.getId(), studentId).isPresent();
        if (!isLinkedParent) {
            throw new NotAuthorizedForPortalAccessException(
                    "Tài khoản id=" + actorUserId + " không phải học sinh sở hữu hoặc phụ huynh liên kết với học sinh id=" + studentId + ".");
        }
    }

    /**
     * A1 (chưa công bố dự kiến, hoặc đã hết hạn phúc khảo) + A -- đang có
     * yêu cầu phúc khảo khác chưa xử lý xong.
     */
    private void requireOpenForAppeal(String statusName, Long entityId) {
        if ("PROVISIONAL_PUBLISHED".equals(statusName)) {
            return;
        }
        if ("APPEAL".equals(statusName)) {
            throw new AppealAlreadyOpenException(
                    "Bản ghi điểm id=" + entityId + " đang có yêu cầu phúc khảo khác chưa xử lý xong.");
        }
        if ("DRAFT".equals(statusName)) {
            throw new ResourceNotFoundException(
                    "Bản ghi điểm id=" + entityId + " chưa được công bố dự kiến, không thể phúc khảo.");
        }
        throw new GradeNotEditableException(
                "Bản ghi điểm id=" + entityId + " đã Chính thức — đã hết hạn phúc khảo.");
    }

    /** Main Flow bước 2: thông báo TẤT CẢ giáo viên phụ trách lớp (không chỉ primary). */
    private void notifyClassTeachers(GradeAppealRequest appeal) {
        List<ClassTeacher> teachers = classTeacherRepository.findBySchoolClassId(appeal.getSchoolClass().getId());
        String title = "Có yêu cầu phúc khảo điểm mới";
        String content = "Học sinh " + appeal.getStudent().getUser().getFullName() + " (lớp "
                + appeal.getSchoolClass().getName() + ") vừa gửi yêu cầu phúc khảo điểm.";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("classId", appeal.getSchoolClass().getId());
        metadata.put("studentId", appeal.getStudent().getId());
        metadata.put("entityType", appeal.getEntityType().name());
        metadata.put("entityId", appeal.getEntityId());
        for (ClassTeacher ct : teachers) {
            notificationService.notify(ct.getTeacher().getId(), Notification.NotificationType.GRADE_APPEAL_REQUESTED,
                    title, content, metadata, "GRADE_APPEAL_REQUEST", appeal.getId(),
                    Notification.Priority.HIGH, appeal.getRequestedBy().getId());
        }
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + id));
    }

    private GradeAppealResponse toResponse(GradeAppealRequest a) {
        return new GradeAppealResponse(
                a.getId(), a.getEntityType().name(), a.getEntityId(), a.getSchoolClass().getId(),
                a.getStudent().getId(), a.getStudent().getUser().getFullName(), a.getRequestedBy().getId(),
                a.getReason(), a.getStatus().name(),
                a.getAcceptedBy() == null ? null : a.getAcceptedBy().getId(),
                a.getAcceptedAt(), a.getResolvedAt(), a.getCreatedAt());
    }
}
