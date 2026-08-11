package vn.com.pps.education.service;

import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.AttendanceMark;
import vn.com.pps.education.domain.AttendanceSession;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.ReportTemplate;
import vn.com.pps.education.domain.StudentComment;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.AttendanceMarkRepository;
import vn.com.pps.education.repository.AttendanceSessionRepository;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.StudentCommentRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * UC-68: resolver cho {@link ReportTemplate.TemplateType#DAILY_REPORT}
 * (Báo cáo ngày) — nguồn dữ liệu: class_sessions, attendance_marks,
 * student_comments (loại DAILY), class_enrollments (xem "Dữ liệu nguồn
 * theo loại báo cáo" trong UC-67, docs/uc/phan-he-06-hoc-thuat.md).
 *
 * Khác các resolver khác — 1 tài liệu ứng với 1 buổi học (scope
 * CLASS_SESSION, không phải SINGLE theo học sinh), publish thêm 1 bảng
 * động {@code [[TABLE:STUDENTS]]} (danh sách toàn bộ học sinh ACTIVE của
 * lớp tại thời điểm buổi học, sắp theo tên A-Z — đã xác nhận với người
 * dùng 2026-08-09) với 3 field con: STUDENT_NAME, ATTENDANCE_STATUS,
 * STUDENT_COMMENT. Template PHẢI dùng đúng tên bảng "STUDENTS" (không có
 * quy ước nào khác được hỗ trợ ở giai đoạn này).
 */
@Component
public class DailyReportDataResolver implements ReportDataResolver {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String TABLE_STUDENTS_KEY = "[[TABLE:STUDENTS]]";

    private final ClassSessionRepository classSessionRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AttendanceMarkRepository attendanceMarkRepository;
    private final StudentCommentRepository studentCommentRepository;

    public DailyReportDataResolver(ClassSessionRepository classSessionRepository,
                                     ClassEnrollmentRepository classEnrollmentRepository,
                                     AttendanceSessionRepository attendanceSessionRepository,
                                     AttendanceMarkRepository attendanceMarkRepository,
                                     StudentCommentRepository studentCommentRepository) {
        this.classSessionRepository = classSessionRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.attendanceMarkRepository = attendanceMarkRepository;
        this.studentCommentRepository = studentCommentRepository;
    }

    @Override
    public ReportTemplate.TemplateType supports() {
        return ReportTemplate.TemplateType.DAILY_REPORT;
    }

    /**
     * Key công bố: CLASS_NAME, CLASS_DATE, TEACHER_NAME, LESSON_TOPIC,
     * TOTAL_STUDENTS, ABSENT_COUNT, GENERATED_DATE, và bảng động
     * {@code [[TABLE:STUDENTS]]}.
     */
    @Override
    public Map<String, Object> buildContext(ReportGenerationParams params) {
        if (params.classSessionId() == null) {
            throw new IllegalArgumentException("DAILY_REPORT cần classSessionId (báo cáo gắn theo 1 buổi học).");
        }
        ClassSession session = classSessionRepository.findById(params.classSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy buổi học id=" + params.classSessionId()));

        Map<String, Object> context = new HashMap<>();
        context.put("CLASS_NAME", session.getSchoolClass().getName());
        context.put("CLASS_DATE", session.getSessionDate().format(DATE_FORMAT));
        context.put("TEACHER_NAME", session.getActualTeacherName() != null
                ? session.getActualTeacherName() : session.getPrimaryTeacher().getFullName());
        context.put("LESSON_TOPIC", session.getLessonContent() != null ? session.getLessonContent() : "");
        context.put("GENERATED_DATE", LocalDate.now().format(DATE_FORMAT));

        List<ClassEnrollment> activeEnrollments = classEnrollmentRepository
                .findBySchoolClassIdAndStatus(session.getSchoolClass().getId(), ClassEnrollment.Status.ACTIVE);
        context.put("TOTAL_STUDENTS", activeEnrollments.size());

        Map<Long, AttendanceMark> markByStudentId = findAttendanceMarksByStudentId(session);
        Map<Long, StudentComment> commentByStudentId = studentCommentRepository.findByClassSessionId(session.getId()).stream()
                .collect(Collectors.toMap(c -> c.getStudent().getId(), Function.identity(), (a, b) -> a));

        long absentCount = markByStudentId.values().stream()
                .filter(m -> m.getStatus() == AttendanceMark.Status.ABSENT).count();
        context.put("ABSENT_COUNT", absentCount);

        List<Map<String, Object>> studentRows = activeEnrollments.stream()
                .sorted(Comparator.comparing(e -> e.getStudent().getUser().getFullName()))
                .map(enrollment -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("STUDENT_CODE", enrollment.getStudent().getStudentCode());
                    row.put("STUDENT_NAME", enrollment.getStudent().getUser().getFullName());
                    AttendanceMark mark = markByStudentId.get(enrollment.getStudent().getId());
                    row.put("ATTENDANCE_STATUS", mark != null ? attendanceStatusLabel(mark.getStatus()) : "Chưa điểm danh");
                    StudentComment comment = commentByStudentId.get(enrollment.getStudent().getId());
                    row.put("STUDENT_COMMENT", comment != null ? comment.getContent() : "");
                    return row;
                }).toList();
        context.put(TABLE_STUDENTS_KEY, studentRows);

        return context;
    }

    private Map<Long, AttendanceMark> findAttendanceMarksByStudentId(ClassSession session) {
        Optional<AttendanceSession> attendanceSession = attendanceSessionRepository.findByClassSessionId(session.getId());
        if (attendanceSession.isEmpty()) {
            return Map.of();
        }
        return attendanceMarkRepository.findByAttendanceSessionId(attendanceSession.get().getId()).stream()
                .collect(Collectors.toMap(m -> m.getStudent().getId(), Function.identity()));
    }

    private String attendanceStatusLabel(AttendanceMark.Status status) {
        return switch (status) {
            case PRESENT -> "Có mặt";
            case ABSENT -> "Vắng";
            case EXCUSED -> "Vắng có phép";
            case LATE -> "Đi trễ";
            case EARLY_LEAVE -> "Về sớm";
        };
    }
}
