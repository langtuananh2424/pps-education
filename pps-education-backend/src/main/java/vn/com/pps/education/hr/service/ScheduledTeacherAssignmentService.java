package vn.com.pps.education.hr.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.academic.domain.ClassSession;
import vn.com.pps.education.academic.domain.ClassTeacher;
import vn.com.pps.education.academic.domain.ClassTeacherHistory;
import vn.com.pps.education.academic.domain.SchoolClass;
import vn.com.pps.education.facility.domain.SiteTeacher;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.academic.repository.ClassSessionRepository;
import vn.com.pps.education.academic.repository.ClassTeacherHistoryRepository;
import vn.com.pps.education.academic.repository.ClassTeacherRepository;
import vn.com.pps.education.facility.repository.SiteTeacherRepository;
import vn.com.pps.education.system.repository.SystemSettingRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Phân công "Dạy theo lịch" (ClassTeacher.TeacherRole.SCHEDULED — V209, bổ sung ngoài SDD gốc, xác
 * nhận với người dùng 2026-10-02). Từ 2026-08-13 giáo viên của buổi học được chọn tay trên Lịch làm
 * việc (UC-48/56/57) và KHÔNG còn gắn với class_teachers — giáo viên mới chỉ được xếp lịch thì không
 * thấy lớp (danh sách lớp, sổ điểm, nhận xét... đều kiểm tra class_teachers) và không có điểm trường
 * (site_teachers). Service này giữ 2 nguồn đồng bộ:
 * <ul>
 *   <li>Xếp/sửa buổi học: giáo viên chính/phụ/CM của buổi chưa có phân công nào đang hiệu lực ở lớp →
 *       tạo class_teachers SCHEDULED + site_teachers (nếu chưa có).</li>
 *   <li>Job hằng đêm: kết thúc phân công SCHEDULED khi giáo viên không còn buổi nào (không huỷ/không
 *       dời) ở lớp từ (hôm nay − academic.scheduled_teacher_revoke_days) trở đi — vừa thu hồi khi giáo
 *       viên luân phiên rời lớp, vừa chừa thời gian nhập nốt điểm/nhận xét buổi đã dạy.</li>
 * </ul>
 * Phân công gán tay (PRIMARY/ASSISTANT/CM qua UC-18, SUBSTITUTE qua UC-10) không bao giờ bị đụng tới.
 * Xem docs/uc/phan-he-06-hoc-thuat.md (UC-48, mục "Tự gán giáo viên theo lịch").
 */
@Service
public class ScheduledTeacherAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTeacherAssignmentService.class);

    static final String REVOKE_DAYS_KEY = "academic.scheduled_teacher_revoke_days";
    private static final int DEFAULT_REVOKE_DAYS = 30;

    private final ClassTeacherRepository classTeacherRepository;
    private final ClassTeacherHistoryRepository classTeacherHistoryRepository;
    private final SiteTeacherRepository siteTeacherRepository;
    private final ClassSessionRepository classSessionRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final Clock clock;

    public ScheduledTeacherAssignmentService(ClassTeacherRepository classTeacherRepository,
                                             ClassTeacherHistoryRepository classTeacherHistoryRepository,
                                             SiteTeacherRepository siteTeacherRepository,
                                             ClassSessionRepository classSessionRepository,
                                             SystemSettingRepository systemSettingRepository,
                                             Clock clock) {
        this.classTeacherRepository = classTeacherRepository;
        this.classTeacherHistoryRepository = classTeacherHistoryRepository;
        this.siteTeacherRepository = siteTeacherRepository;
        this.classSessionRepository = classSessionRepository;
        this.systemSettingRepository = systemSettingRepository;
        this.clock = clock;
    }

    /**
     * Gọi sau khi lưu 1 buổi học (tạo/dời/sửa nhanh) — chạy trong transaction của caller. Bỏ qua buổi đã
     * huỷ/dời (không còn ai dạy buổi đó).
     */
    public void ensureAssignedForSession(ClassSession session, User actor) {
        if (session.getStatus() == ClassSession.Status.CANCELLED || session.getStatus() == ClassSession.Status.RESCHEDULED) {
            return;
        }
        Map<Long, User> teachersById = new LinkedHashMap<>();
        Stream.of(session.getPrimaryTeacher(), session.getAssistantTeacher(), session.getCmTeacher())
                .filter(Objects::nonNull)
                .forEach(u -> teachersById.putIfAbsent(u.getId(), u));
        teachersById.values().forEach(teacher -> ensureAssigned(session, teacher, actor));
    }

    private void ensureAssigned(ClassSession session, User teacher, User actor) {
        SchoolClass schoolClass = session.getSchoolClass();
        if (!classTeacherRepository.existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(schoolClass.getId(), teacher.getId())) {
            ClassTeacher classTeacher = new ClassTeacher();
            classTeacher.setSchoolClass(schoolClass);
            classTeacher.setTeacher(teacher);
            classTeacher.setTeacherRole(ClassTeacher.TeacherRole.SCHEDULED);
            classTeacher.setAssignedFrom(LocalDate.now(clock));
            classTeacher.setAssignedBy(actor);
            classTeacher = classTeacherRepository.save(classTeacher);

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("teacherUserId", teacher.getId());
            snapshot.put("teacherRole", ClassTeacher.TeacherRole.SCHEDULED.name());
            snapshot.put("reason", "auto-assign from class session");
            snapshot.put("classSessionId", session.getId());
            writeHistory(classTeacher, actor, ClassTeacherHistory.Action.CREATED, snapshot);
        }
        ensureTeacherAssignedToSite(schoolClass, teacher, actor);
    }

    /**
     * Bổ sung ngoài SDD gốc (xem docs/sdd-groups/03-co-so-vat-chat-and-diem-truong.md): giáo viên dạy
     * 1 lớp thì thuộc điểm trường của lớp đó — tạo site_teachers nếu chưa có, im lặng bỏ qua nếu đã có.
     */
    public void ensureTeacherAssignedToSite(SchoolClass schoolClass, User teacher, User actor) {
        if (siteTeacherRepository.existsBySiteIdAndTeacherIdAndAssignedToIsNull(schoolClass.getSite().getId(), teacher.getId())) {
            return;
        }
        SiteTeacher link = new SiteTeacher();
        link.setSite(schoolClass.getSite());
        link.setTeacher(teacher);
        link.setAssignedFrom(LocalDate.now(clock));
        link.setAssignedBy(actor);
        siteTeacherRepository.save(link);
    }

    /**
     * UC-18: giáo vụ gán tay giáo viên vào lớp → phân công "Dạy theo lịch" (nếu có) của đúng giáo
     * viên đó ở lớp được thay bằng phân công gán tay, kết thúc ngay để tab Giáo viên không hiện 2 dòng.
     */
    public void endScheduledAssignmentsSupersededByManual(Long classId, Long teacherId, User actor) {
        for (ClassTeacher scheduled : classTeacherRepository.findBySchoolClassIdAndTeacherIdAndTeacherRoleAndAssignedToIsNull(
                classId, teacherId, ClassTeacher.TeacherRole.SCHEDULED)) {
            endAssignment(scheduled, actor, "superseded by manual assignment");
        }
    }

    /** Job hằng đêm (02:30) — thu hồi phân công "Dạy theo lịch" không còn buổi học trong cửa sổ cho phép. */
    @Scheduled(cron = "${app.scheduled-teacher-revoke.cron:0 30 2 * * *}")
    @Transactional
    public int revokeStaleScheduledAssignments() {
        int revokeDays = readRevokeDays();
        LocalDate sinceDate = LocalDate.now(clock).minusDays(revokeDays);
        List<ClassTeacher> activeScheduled = classTeacherRepository.findByTeacherRoleAndAssignedToIsNull(ClassTeacher.TeacherRole.SCHEDULED);
        int revoked = 0;
        for (ClassTeacher classTeacher : activeScheduled) {
            boolean stillTeaching = classSessionRepository.existsActiveSessionForTeacherSince(
                    classTeacher.getSchoolClass().getId(), classTeacher.getTeacher().getId(), sinceDate);
            if (!stillTeaching) {
                // Không có người thao tác — ghi lịch sử dưới tên người đã tạo phân công (cùng cách ClassStatusSchedulerService).
                endAssignment(classTeacher, classTeacher.getAssignedBy(), "auto-revoke: no class session since " + sinceDate);
                revoked++;
            }
        }
        if (revoked > 0) {
            log.info("Revoked {} scheduled class-teacher assignment(s) with no session since {}.", revoked, sinceDate);
        }
        return revoked;
    }

    private void endAssignment(ClassTeacher classTeacher, User actor, String reason) {
        classTeacher.setAssignedTo(LocalDate.now(clock));
        classTeacherRepository.save(classTeacher);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("assignedTo", classTeacher.getAssignedTo().toString());
        snapshot.put("reason", reason);
        writeHistory(classTeacher, actor, ClassTeacherHistory.Action.UPDATED, snapshot);
    }

    private void writeHistory(ClassTeacher classTeacher, User actor, ClassTeacherHistory.Action action, Map<String, Object> details) {
        ClassTeacherHistory history = new ClassTeacherHistory();
        history.setClassTeacher(classTeacher);
        history.setChangedBy(actor);
        history.setAction(action);
        history.setDetails(details);
        classTeacherHistoryRepository.save(history);
    }

    private int readRevokeDays() {
        return systemSettingRepository.findBySettingKey(REVOKE_DAYS_KEY)
                .map(s -> s.getSettingValue().asInt(DEFAULT_REVOKE_DAYS))
                .map(days -> Math.max(0, days))
                .orElseGet(() -> {
                    log.warn("Missing system setting {} — falling back to {} days.", REVOKE_DAYS_KEY, DEFAULT_REVOKE_DAYS);
                    return DEFAULT_REVOKE_DAYS;
                });
    }
}
