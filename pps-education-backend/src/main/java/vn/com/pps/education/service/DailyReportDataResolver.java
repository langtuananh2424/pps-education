package vn.com.pps.education.service;

import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.AttendanceMark;
import vn.com.pps.education.domain.AttendanceSession;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.HomeworkSkillBatch;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final StudentCommentService studentCommentService;

    public DailyReportDataResolver(ClassSessionRepository classSessionRepository,
                                     ClassEnrollmentRepository classEnrollmentRepository,
                                     AttendanceSessionRepository attendanceSessionRepository,
                                     AttendanceMarkRepository attendanceMarkRepository,
                                     StudentCommentRepository studentCommentRepository,
                                     StudentCommentService studentCommentService) {
        this.classSessionRepository = classSessionRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.attendanceMarkRepository = attendanceMarkRepository;
        this.studentCommentRepository = studentCommentRepository;
        this.studentCommentService = studentCommentService;
    }

    @Override
    public ReportTemplate.TemplateType supports() {
        return ReportTemplate.TemplateType.DAILY_REPORT;
    }

    /**
     * Key công bố: CLASS_NAME, CLASS_DATE, TEACHER_NAME,
     * ASSISTANT_TEACHER_NAME, LESSON_TOPIC, TOTAL_STUDENTS, PRESENT_COUNT,
     * ABSENT_COUNT, ABSENT_STUDENT_NAMES, MISSING_HOMEWORK_STUDENT_NAMES,
     * HOMEWORK_CONTENT, GENERATED_DATE, và bảng động {@code [[TABLE:STUDENTS]]}.
     *
     * Quy tắc đếm (đã xác nhận với người dùng 2026-09-28): ABSENT_COUNT =
     * Vắng (ABSENT) + Vắng có phép (EXCUSED); PRESENT_COUNT = Có mặt
     * (PRESENT) + Đi trễ (LATE) + Về sớm (EARLY_LEAVE). Học sinh chưa được
     * điểm danh không nằm trong cả 2 con số. ABSENT_STUDENT_NAMES liệt kê
     * đúng các học sinh được đếm trong ABSENT_COUNT.
     *
     * BTVN (đã xác nhận với người dùng 2026-09-28): MISSING_HOMEWORK_STUDENT_NAMES
     * = học sinh có "BTVN buổi trước" kênh online Ngữ pháp/Nghe là "Chưa làm
     * bài" (xem StudentCommentService#studentIdsWithUndoneGrammarHomework);
     * HOMEWORK_CONTENT = BTVN giao cho buổi sau, gộp mọi kênh, bỏ trùng giữa
     * các học sinh, mỗi mục 1 dòng. 3 key danh sách này để rỗng khi không có
     * mục nào (trường hợp bình thường, không phải thiếu dữ liệu — UC-68 A1).
     */
    @Override
    public Map<String, Object> buildContext(ReportGenerationParams params) {
        if (params.classSessionId() == null) {
            throw new IllegalArgumentException("DAILY_REPORT cần classSessionId (báo cáo gắn theo 1 buổi học).");
        }
        ClassSession session = classSessionRepository.findById(params.classSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("error.dailyReport.classSessionNotFound",
                        new Object[]{params.classSessionId()}, "Không tìm thấy buổi học id=" + params.classSessionId()));

        Map<String, Object> context = new HashMap<>();
        context.put("CLASS_NAME", session.getSchoolClass().getName());
        context.put("CLASS_DATE", session.getSessionDate().format(DATE_FORMAT));
        context.put("TEACHER_NAME", session.getActualTeacherName() != null
                ? session.getActualTeacherName() : session.getPrimaryTeacher().getFullName());
        // Buổi không có trợ giảng là trường hợp bình thường, không phải thiếu dữ liệu (UC-68 A1) — điền rỗng như LESSON_TOPIC.
        context.put("ASSISTANT_TEACHER_NAME", session.getAssistantTeacher() != null
                ? session.getAssistantTeacher().getFullName() : "");
        context.put("LESSON_TOPIC", session.getLessonContent() != null ? session.getLessonContent() : "");
        context.put("GENERATED_DATE", LocalDate.now().format(DATE_FORMAT));

        List<ClassEnrollment> activeEnrollments = classEnrollmentRepository
                .findBySchoolClassIdAndStatus(session.getSchoolClass().getId(), ClassEnrollment.Status.ACTIVE);
        context.put("TOTAL_STUDENTS", activeEnrollments.size());

        Map<Long, AttendanceMark> markByStudentId = findAttendanceMarksByStudentId(session);
        Map<Long, StudentComment> commentByStudentId = studentCommentRepository.findByClassSessionId(session.getId()).stream()
                .collect(Collectors.toMap(c -> c.getStudent().getId(), Function.identity(), (a, b) -> a));

        long absentCount = markByStudentId.values().stream()
                .filter(m -> isAbsent(m.getStatus())).count();
        long presentCount = markByStudentId.values().stream()
                .filter(m -> !isAbsent(m.getStatus())).count();
        context.put("ABSENT_COUNT", absentCount);
        context.put("PRESENT_COUNT", presentCount);
        context.put("ABSENT_STUDENT_NAMES", joinSortedNames(markByStudentId.values().stream()
                .filter(m -> isAbsent(m.getStatus()))
                .map(m -> m.getStudent().getUser().getFullName())));

        List<Long> activeStudentIds = activeEnrollments.stream().map(e -> e.getStudent().getId()).toList();
        Set<Long> missingHomeworkIds = studentCommentService.studentIdsWithUndoneGrammarHomework(session, activeStudentIds);
        context.put("MISSING_HOMEWORK_STUDENT_NAMES", joinSortedNames(activeEnrollments.stream()
                .filter(e -> missingHomeworkIds.contains(e.getStudent().getId()))
                .map(e -> e.getStudent().getUser().getFullName())));

        List<ClassEnrollment> sortedEnrollments = activeEnrollments.stream()
                .sorted(Comparator.comparing(e -> e.getStudent().getUser().getFullName()))
                .toList();
        // Duyệt theo thứ tự tên A-Z để thứ tự dòng BTVN ổn định giữa các lần xuất.
        context.put("HOMEWORK_CONTENT", homeworkContent(sortedEnrollments.stream()
                .map(e -> commentByStudentId.get(e.getStudent().getId()))
                .filter(java.util.Objects::nonNull)
                .toList()));

        List<Map<String, Object>> studentRows = sortedEnrollments.stream()
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

    private String joinSortedNames(Stream<String> names) {
        return names.sorted().collect(Collectors.joining(", "));
    }

    /**
     * BTVN giao cho buổi sau, gộp mọi kênh — offline chữ tự do (homeworkNext/Reading/Writing) giữ
     * nguyên văn GV gõ, online ghi tên Đề/Video đã giao (chỉ có sau khi Gửi nhận xét, xem V127).
     */
    private String homeworkContent(Collection<StudentComment> comments) {
        Set<String> lines = new LinkedHashSet<>();
        for (StudentComment c : comments) {
            addLine(lines, null, c.getHomeworkNext());
            addLine(lines, "Reading", c.getHomeworkNextReading());
            addLine(lines, "Writing", c.getHomeworkNextWriting());
            addLine(lines, "Online", batchTitle(c.getHomeworkNextGrammarBatch()));
            addLine(lines, "Online Reading", batchTitle(c.getHomeworkNextReadingBatch()));
            addLine(lines, "Online Writing", batchTitle(c.getHomeworkNextWritingBatch()));
            if (c.getHomeworkNextReviewVideoAssignment() != null) {
                addLine(lines, "Video", c.getHomeworkNextReviewVideoAssignment().getReviewVideoSet().getTitle());
            }
        }
        return String.join("\n", lines);
    }

    private void addLine(Set<String> lines, String prefix, String value) {
        if (value != null && !value.isBlank()) {
            lines.add("- " + (prefix != null ? prefix + ": " : "") + value.strip());
        }
    }

    private String batchTitle(HomeworkSkillBatch batch) {
        return batch != null ? batch.getExam().getTitle() : null;
    }

    private boolean isAbsent(AttendanceMark.Status status) {
        return status == AttendanceMark.Status.ABSENT || status == AttendanceMark.Status.EXCUSED;
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
