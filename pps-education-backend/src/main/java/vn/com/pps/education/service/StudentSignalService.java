package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.common.StudentSignalInsight;
import vn.com.pps.education.domain.AttendanceMark;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.HomeworkParentMeetingInvite;
import vn.com.pps.education.domain.StudentComment;
import vn.com.pps.education.repository.AttendanceMarkRepository;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.HomeworkParentMeetingInviteRepository;
import vn.com.pps.education.repository.StudentCommentRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * UC-74 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-30) — tín hiệu ngoài lời giáo viên cho trợ lý
 * nhận xét, gom theo lô cho cả lớp: điểm danh buổi này + chuyên cần {@link StudentSignalInsight#ATTENDANCE_WINDOW} buổi gần nhất, lời mời họp phụ huynh vì
 * thiếu BTVN (chỉ để chỉnh giọng văn), độ tuổi (chỉ để chỉnh giọng văn) và nhận xét gần nhất của buổi
 * KHÁC Loại giáo viên (chỉ để giữ nhất quán/tránh lặp, không nhắc giáo viên kia). Chỉ đọc, không ghi DB. Quy tắc:
 * xem {@link StudentSignalInsight}.
 */
@Service
public class StudentSignalService {

    static final int INVITE_LOOKBACK_DAYS = 30;
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<ClassSession.Status> EXCLUDED_STATUSES =
            List.of(ClassSession.Status.CANCELLED, ClassSession.Status.RESCHEDULED);

    private final ClassSessionRepository classSessionRepository;
    private final AttendanceMarkRepository attendanceMarkRepository;
    private final HomeworkParentMeetingInviteRepository inviteRepository;
    private final StudentCommentRepository studentCommentRepository;

    public StudentSignalService(ClassSessionRepository classSessionRepository,
                                AttendanceMarkRepository attendanceMarkRepository,
                                HomeworkParentMeetingInviteRepository inviteRepository,
                                StudentCommentRepository studentCommentRepository) {
        this.classSessionRepository = classSessionRepository;
        this.attendanceMarkRepository = attendanceMarkRepository;
        this.inviteRepository = inviteRepository;
        this.studentCommentRepository = studentCommentRepository;
    }

    /**
     * Tín hiệu của 1 học sinh.
     *
     * @param attendance          ý về điểm danh/chuyên cần (được viết vào nhận xét).
     * @param toneHints           gợi ý giọng văn (KHÔNG viết vào nhận xét).
     * @param otherTeacherComment nhận xét gần nhất ở buổi khác Loại giáo viên của cùng lớp, {@code null} nếu không có.
     */
    public record Signals(List<String> attendance, List<String> toneHints,
                          OtherTeacherComment otherTeacherComment) {
        public static final Signals EMPTY = new Signals(List.of(), List.of(), null);
    }

    public record OtherTeacherComment(LocalDate date, String content) {
    }

    /**
     * UC-74 bước 4-6 (bổ sung 2026-09-30).
     *
     * @param enrollments ghi danh ACTIVE của các học sinh cần soạn (studentId → ghi danh, lấy ngày vào lớp + ngày sinh).
     * @param todayMarks  điểm danh buổi này (studentId → dấu điểm danh), có thể thiếu học sinh chưa điểm danh.
     */
    @Transactional(readOnly = true)
    public Map<Long, Signals> describe(ClassSession session, Map<Long, ClassEnrollment> enrollments, Map<Long, AttendanceMark> todayMarks) {
        if (enrollments.isEmpty()) {
            return Map.of();
        }
        List<Long> studentIds = List.copyOf(enrollments.keySet());
        Long classId = session.getSchoolClass().getId();

        // Chuyên cần: buổi này + các buổi trước của lớp (mọi Loại giáo viên), mới nhất trước.
        List<Long> sessionOrder = new ArrayList<>();
        sessionOrder.add(session.getId());
        classSessionRepository.findSessionsBeforeOrderedDesc(classId, session.getSessionDate(), session.getId(), EXCLUDED_STATUSES)
                .stream().limit(StudentSignalInsight.ATTENDANCE_WINDOW - 1)
                .forEach(s -> sessionOrder.add(s.getId()));
        Map<Long, Map<Long, AttendanceMark>> marksByStudent = new HashMap<>();
        for (AttendanceMark mark : attendanceMarkRepository.findByClassSessionIdInAndStudentIdIn(sessionOrder, studentIds)) {
            marksByStudent.computeIfAbsent(mark.getStudent().getId(), k -> new HashMap<>())
                    .put(mark.getAttendanceSession().getClassSession().getId(), mark);
        }
        todayMarks.forEach((studentId, mark) -> marksByStudent.computeIfAbsent(studentId, k -> new HashMap<>()).put(session.getId(), mark));

        // Lời mời họp phụ huynh vì thiếu BTVN (chờ duyệt/đã duyệt, 30 ngày) — chỉ chỉnh giọng văn.
        OffsetDateTime inviteFrom = session.getSessionDate().minusDays(INVITE_LOOKBACK_DAYS).atStartOfDay(APP_ZONE).toOffsetDateTime();
        Map<Long, Set<String>> inviteChannels = new HashMap<>();
        for (HomeworkParentMeetingInvite invite : inviteRepository.findByStudentIdInAndSchoolClassIdAndStatusInAndCreatedAtGreaterThanEqual(
                studentIds, classId, List.of(HomeworkParentMeetingInvite.Status.PENDING, HomeworkParentMeetingInvite.Status.APPROVED), inviteFrom)) {
            inviteChannels.computeIfAbsent(invite.getStudent().getId(), k -> new LinkedHashSet<>()).add(invite.getChannelLabel());
        }

        Map<Long, OtherTeacherComment> otherTeacherComments = otherTeacherComments(session, studentIds);

        Map<Long, Signals> result = new HashMap<>();
        for (Map.Entry<Long, ClassEnrollment> entry : enrollments.entrySet()) {
            Long studentId = entry.getKey();
            ClassEnrollment enrollment = entry.getValue();
            Map<Long, AttendanceMark> marks = marksByStudent.getOrDefault(studentId, Map.of());

            List<String> attendance = new ArrayList<>();
            AttendanceMark today = marks.get(session.getId());
            if (today != null) {
                StudentSignalInsight.today(today.getStatus().name(), today.getMinutesLate(), today.getMinutesEarlyLeave())
                        .ifPresent(attendance::add);
            }
            List<String> history = sessionOrder.stream().map(marks::get).filter(m -> m != null && m.getStatus() != null)
                    .map(m -> m.getStatus().name()).toList();
            StudentSignalInsight.attendanceHistory(history).ifPresent(attendance::add);

            List<String> toneHints = new ArrayList<>();
            Set<String> channels = inviteChannels.get(studentId);
            if (channels != null) {
                toneHints.add("trung tâm đang trao đổi với phụ huynh vì con chưa làm đủ BTVN (" + String.join(", ", channels)
                        + "): KHÔNG khen phần BTVN này, giọng nhắc nhở nhẹ nhàng mang tính đồng hành, KHÔNG nhắc chuyện mời họp/trao đổi với phụ huynh");
            }
            StudentSignalInsight.ageTone(enrollment.getStudent().getDateOfBirth(), session.getSessionDate()).ifPresent(toneHints::add);

            Signals signals = new Signals(List.copyOf(attendance), List.copyOf(toneHints),
                    otherTeacherComments.get(studentId));
            if (!signals.equals(Signals.EMPTY)) {
                result.put(studentId, signals);
            }
        }
        return result;
    }

    /** Nhận xét (có nội dung, chưa bị từ chối) ở buổi gần nhất TRƯỚC buổi này của Loại giáo viên kia, cùng lớp. */
    private Map<Long, OtherTeacherComment> otherTeacherComments(ClassSession session, List<Long> studentIds) {
        if (session.getTeacherType() == null) {
            return Map.of();
        }
        ClassSession.TeacherType other = session.getTeacherType() == ClassSession.TeacherType.FOREIGN
                ? ClassSession.TeacherType.VIETNAMESE : ClassSession.TeacherType.FOREIGN;
        List<ClassSession> candidates = classSessionRepository.findSessionsBeforeWithTeacherTypeOrderedDesc(
                session.getSchoolClass().getId(), session.getSessionDate(), session.getId(), other, EXCLUDED_STATUSES);
        if (candidates.isEmpty()) {
            return Map.of();
        }
        ClassSession otherSession = candidates.get(0);
        Map<Long, OtherTeacherComment> result = new HashMap<>();
        for (StudentComment comment : studentCommentRepository.findByClassSessionIdAndStudentIdIn(otherSession.getId(), studentIds)) {
            if (comment.getContent() != null && !comment.getContent().isBlank() && comment.getStatus() != StudentComment.Status.REJECTED) {
                result.putIfAbsent(comment.getStudent().getId(),
                        new OtherTeacherComment(otherSession.getSessionDate(), comment.getContent().trim()));
            }
        }
        return result;
    }
}
