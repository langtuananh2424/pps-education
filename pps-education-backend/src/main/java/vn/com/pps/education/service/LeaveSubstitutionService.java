package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.ClassTeacher;
import vn.com.pps.education.domain.LeaveRequest;
import vn.com.pps.education.domain.LeaveSubstitution;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.dto.LeaveSubstitutionResponse;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.ClassTeacherRepository;
import vn.com.pps.education.repository.LeaveSubstitutionRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * UC-10 bước 3/5 + UC-11 A2 + UC-11 Mở rộng (FR-HRM-03, bổ sung ngoài SDD
 * gốc, đã xác nhận với người dùng 2026-08-05). Xem
 * docs/uc/phan-he-04-nhan-su.md — giáo viên dạy thay theo đơn nghỉ: gán
 * NGAY khi Giáo viên nộp đơn (không đợi duyệt), thu hồi ngay nếu đơn bị Từ
 * chối, hoặc tự động thu hồi sau end_date + 2 ngày nếu đơn Đã duyệt.
 */
@Service
public class LeaveSubstitutionService {

    private static final List<ClassSession.Status> EXCLUDED_SESSION_STATUSES =
            List.of(ClassSession.Status.CANCELLED, ClassSession.Status.RESCHEDULED);

    private final ClassSessionRepository classSessionRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final LeaveSubstitutionRepository leaveSubstitutionRepository;

    public LeaveSubstitutionService(ClassSessionRepository classSessionRepository,
                                     ClassTeacherRepository classTeacherRepository,
                                     LeaveSubstitutionRepository leaveSubstitutionRepository) {
        this.classSessionRepository = classSessionRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.leaveSubstitutionRepository = leaveSubstitutionRepository;
    }

    /** UC-10 bước 3: buổi dạy của 1 Giáo viên trong khoảng nghỉ, chưa hủy/dời lịch. */
    @Transactional(readOnly = true)
    public List<ClassSession> findTeachingSessions(Long teacherId, LocalDate startDate, LocalDate endDate) {
        return classSessionRepository.findByPrimaryTeacherIdAndSessionDateBetweenAndStatusNotIn(
                teacherId, startDate, endDate, EXCLUDED_SESSION_STATUSES);
    }

    /** UC-10 bước 3 (FE): danh sách buổi dạy để Giáo viên chọn dạy thay, dạng response (không lộ Entity). */
    @Transactional(readOnly = true)
    public List<ClassSessionResponse> findTeachingSessionResponses(Long teacherId, LocalDate startDate, LocalDate endDate) {
        return findTeachingSessions(teacherId, startDate, endDate).stream().map(this::toSessionResponse).toList();
    }

    private ClassSessionResponse toSessionResponse(ClassSession s) {
        return new ClassSessionResponse(
                s.getId(), s.getSchoolClass().getId(), s.getSchoolClass().getName(), s.getSessionDate(), s.getStartTime(), s.getEndTime(),
                null, null,
                s.getRoom() == null ? null : s.getRoom().getId(), s.getRoom() == null ? null : s.getRoom().getName(),
                s.getPrimaryTeacher().getId(), s.getPrimaryTeacher().getFullName(),
                s.getAssistantTeacher() == null ? null : s.getAssistantTeacher().getId(),
                s.getAssistantTeacher() == null ? null : s.getAssistantTeacher().getFullName(),
                s.getCmTeacher() == null ? null : s.getCmTeacher().getId(),
                s.getCmTeacher() == null ? null : s.getCmTeacher().getFullName(),
                s.getSessionType().name(), s.getStatus().name(),
                s.getCancellationReason(), s.getRescheduledToSession() == null ? null : s.getRescheduledToSession().getId(),
                s.getLessonContent(), s.getTeacherType() == null ? null : s.getTeacherType().name(),
                s.getActualTeacherName(), null,
                s.getMakeupForSession() == null ? null : s.getMakeupForSession().getId());
    }

    /**
     * UC-10 bước 5: gán giáo viên dạy thay cho 1 buổi học NGAY khi nộp đơn
     * (không đợi duyệt). classTeacherCache dùng chung cho cả lượt nộp đơn để
     * 1 cặp (lớp, giáo viên dạy thay) chỉ tạo 1 dòng class_teachers dù thay
     * nhiều buổi.
     */
    @Transactional
    public void apply(LeaveRequest leaveRequest, ClassSession session, User substituteTeacher, User actor,
                       Map<Long, ClassTeacher> classTeacherCache) {
        User originalTeacher = session.getPrimaryTeacher();

        ClassTeacher classTeacher = classTeacherCache.computeIfAbsent(substituteTeacher.getId(), id -> {
            ClassTeacher ct = new ClassTeacher();
            ct.setSchoolClass(session.getSchoolClass());
            ct.setTeacher(substituteTeacher);
            ct.setTeacherRole(ClassTeacher.TeacherRole.SUBSTITUTE);
            ct.setAssignedFrom(leaveRequest.getStartDate());
            // assignedTo để NULL = đang phụ trách (quy ước SDD); chỉ set khi thu hồi (revoke()).
            ct.setAssignedBy(actor);
            return classTeacherRepository.save(ct);
        });

        session.setPrimaryTeacher(substituteTeacher);
        classSessionRepository.save(session);

        LeaveSubstitution ls = new LeaveSubstitution();
        ls.setLeaveRequest(leaveRequest);
        ls.setClassSession(session);
        ls.setClassTeacher(classTeacher);
        ls.setOriginalTeacher(originalTeacher);
        ls.setSubstituteTeacher(substituteTeacher);
        leaveSubstitutionRepository.save(ls);
    }

    /**
     * UC-11 A2 (từ chối) / UC-11 Mở rộng (job tự thu hồi): trả buổi học về
     * giáo viên chính ban đầu, đóng dòng class_teachers nếu không còn lượt
     * dạy thay nào khác đang mở cho cùng cặp (lớp, giáo viên dạy thay), ghi
     * revoked_at làm log thu hồi.
     */
    @Transactional
    public void revoke(LeaveSubstitution ls, OffsetDateTime at) {
        ClassSession session = ls.getClassSession();
        session.setPrimaryTeacher(ls.getOriginalTeacher());
        classSessionRepository.save(session);

        ls.setRevokedAt(at);
        leaveSubstitutionRepository.save(ls);

        if (!leaveSubstitutionRepository.existsByClassTeacherIdAndRevokedAtIsNull(ls.getClassTeacher().getId())) {
            ClassTeacher classTeacher = ls.getClassTeacher();
            classTeacher.setAssignedTo(at.toLocalDate());
            classTeacherRepository.save(classTeacher);
        }
    }

    /** UC-11 A2: đơn bị Từ chối — thu hồi ngay toàn bộ lượt dạy thay đang mở của đơn đó. */
    @Transactional
    public void revokeAllForLeaveRequest(Long leaveRequestId, OffsetDateTime at) {
        leaveSubstitutionRepository.findByLeaveRequestIdAndRevokedAtIsNull(leaveRequestId)
                .forEach(ls -> revoke(ls, at));
    }

    /** UC-11 Mở rộng: trang lịch sử dạy thay. */
    @Transactional(readOnly = true)
    public List<LeaveSubstitutionResponse> history() {
        return leaveSubstitutionRepository.findByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    private LeaveSubstitutionResponse toResponse(LeaveSubstitution ls) {
        ClassSession session = ls.getClassSession();
        return new LeaveSubstitutionResponse(
                ls.getId(),
                ls.getLeaveRequest().getId(),
                session.getId(),
                session.getSessionDate(),
                session.getSchoolClass().getId(),
                session.getSchoolClass().getName(),
                ls.getOriginalTeacher().getId(),
                ls.getOriginalTeacher().getFullName(),
                ls.getSubstituteTeacher().getId(),
                ls.getSubstituteTeacher().getFullName(),
                ls.getRevokedAt(),
                ls.getCreatedAt());
    }
}
