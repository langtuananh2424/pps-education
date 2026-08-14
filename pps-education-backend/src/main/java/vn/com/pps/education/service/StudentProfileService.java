package vn.com.pps.education.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AcademicTerm;
import vn.com.pps.education.domain.AttendanceMark;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.ExerciseAttempt;
import vn.com.pps.education.domain.GradeEntry;
import vn.com.pps.education.domain.GradeEvaluationResult;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.StudentComment;
import vn.com.pps.education.dto.StudentProfileAttendanceResponse;
import vn.com.pps.education.dto.StudentProfileCommentResponse;
import vn.com.pps.education.dto.StudentProfileEnrollmentResponse;
import vn.com.pps.education.dto.StudentProfileGradeResultResponse;
import vn.com.pps.education.dto.StudentProfileHomeworkResponse;
import vn.com.pps.education.dto.StudentProfileResponse;
import vn.com.pps.education.dto.StudentProfileSkillScoreResponse;
import vn.com.pps.education.dto.StudentProfileStudentResponse;
import vn.com.pps.education.repository.AcademicTermRepository;
import vn.com.pps.education.repository.AttendanceMarkRepository;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.ExerciseAttemptRepository;
import vn.com.pps.education.repository.GradeEntryRepository;
import vn.com.pps.education.repository.GradeEvaluationResultRepository;
import vn.com.pps.education.repository.StudentCommentRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng — API tổng hợp hồ sơ
 * học tập 1 học sinh (FR-REP-04, xem docs/sdd-groups/06-hoc-thuat.md cho
 * các bảng nguồn). Thay thế cách FE trước đây tự "fan-out" ~200-300 request
 * nhỏ (list lớp → với mỗi lớp list ghi danh/sổ điểm/nhận xét/buổi học...)
 * để dựng 1 hồ sơ — chậm không chấp nhận được khi deploy thật (mỗi request
 * cộng thêm round-trip mạng thật, khác hẳn localhost gần như 0 độ trễ).
 * Service này gộp lại thành ~10 truy vấn JOIN FETCH hiệu quả, không lặp lại
 * logic nghiệp vụ nào khác — mọi bảng nguồn (class_enrollments, grade_*,
 * student_comments, attendance_marks) đều đã có Service riêng ghi/duyệt dữ
 * liệu (UC-18/19/20/21/22/15); đây thuần là 1 view TÍNH RA, không ghi.
 * <p>
 * Quyền: dùng chung {@code student.profile.view} + rào site-scope của
 * {@link StudentService#getAccessibleStudentOrThrow} — không tự định nghĩa
 * rào riêng, tránh lệch phạm vi truy cập so với các API học sinh khác.
 */
@Service
public class StudentProfileService {

    /** Giới hạn số buổi điểm danh gần nhất trả về — tránh 1 học sinh nhiều năm kéo về hàng nghìn dòng không cần thiết cho hiển thị. */
    private static final int ATTENDANCE_LIMIT = 150;

    private final StudentService studentService;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final GradeEvaluationResultRepository gradeEvaluationResultRepository;
    private final GradeEntryRepository gradeEntryRepository;
    private final StudentCommentRepository studentCommentRepository;
    private final AttendanceMarkRepository attendanceMarkRepository;
    private final ClassSessionRepository classSessionRepository;
    private final AcademicTermRepository academicTermRepository;
    private final ExerciseAttemptRepository exerciseAttemptRepository;

    public StudentProfileService(StudentService studentService,
                                  ClassEnrollmentRepository classEnrollmentRepository,
                                  GradeEvaluationResultRepository gradeEvaluationResultRepository,
                                  GradeEntryRepository gradeEntryRepository,
                                  StudentCommentRepository studentCommentRepository,
                                  AttendanceMarkRepository attendanceMarkRepository,
                                  ClassSessionRepository classSessionRepository,
                                  AcademicTermRepository academicTermRepository,
                                  ExerciseAttemptRepository exerciseAttemptRepository) {
        this.studentService = studentService;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.gradeEvaluationResultRepository = gradeEvaluationResultRepository;
        this.gradeEntryRepository = gradeEntryRepository;
        this.studentCommentRepository = studentCommentRepository;
        this.attendanceMarkRepository = attendanceMarkRepository;
        this.classSessionRepository = classSessionRepository;
        this.academicTermRepository = academicTermRepository;
        this.exerciseAttemptRepository = exerciseAttemptRepository;
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse getProfile(Long studentId, Long actorUserId) {
        Student student = studentService.getAccessibleStudentOrThrow(studentId, actorUserId);

        List<ClassEnrollment> enrollments = classEnrollmentRepository.findByStudentIdWithClassContext(studentId);
        List<GradeEvaluationResult> gradeResults = gradeEvaluationResultRepository.findByStudentIdWithContext(studentId);
        List<GradeEntry> gradeEntries = gradeEntryRepository.findByStudentIdWithContext(studentId);
        List<StudentComment> comments = studentCommentRepository.findByStudentIdWithContext(studentId);
        List<AttendanceMark> attendanceMarks =
                attendanceMarkRepository.findByStudentIdWithContext(studentId, PageRequest.of(0, ATTENDANCE_LIMIT));
        List<ExerciseAttempt> homeworkAttempts = exerciseAttemptRepository.findByStudentIdWithContext(studentId);

        Map<Long, List<AcademicTerm>> termsBySite = new HashMap<>();
        Map<Long, Integer> sessionNumberBySessionId = computeSessionNumbers(comments, attendanceMarks);

        List<StudentProfileEnrollmentResponse> enrollmentDtos = enrollments.stream().map(this::toEnrollmentDto).toList();
        List<StudentProfileGradeResultResponse> gradeResultDtos = gradeResults.stream().map(this::toGradeResultDto).toList();
        List<StudentProfileSkillScoreResponse> skillScoreDtos = gradeEntries.stream().map(this::toSkillScoreDto).toList();
        List<StudentProfileCommentResponse> commentDtos = comments.stream()
                .map(c -> toCommentDto(c, termsBySite, sessionNumberBySessionId))
                .toList();
        List<StudentProfileAttendanceResponse> attendanceDtos = attendanceMarks.stream()
                .map(m -> toAttendanceDto(m, termsBySite, sessionNumberBySessionId))
                .toList();
        List<StudentProfileHomeworkResponse> homeworkDtos = homeworkAttempts.stream().map(this::toHomeworkDto).toList();

        return new StudentProfileResponse(
                toStudentDto(student), enrollmentDtos, gradeResultDtos, skillScoreDtos, commentDtos, attendanceDtos, homeworkDtos);
    }

    // ===================== Helpers =====================

    /**
     * "Buổi N" hiển thị — cùng công thức {@link ClassSessionRepository#countEarlierSessions}
     * (đếm buổi đứng trước, kể cả CANCELLED) nhưng tính 1 lần theo LÔ cho mỗi
     * lớp liên quan (thay vì 1 truy vấn COUNT/buổi) — số lớp liên quan tới 1
     * học sinh luôn nhỏ (vài lớp), nên đây vẫn chỉ thêm vài truy vấn.
     */
    private Map<Long, Integer> computeSessionNumbers(List<StudentComment> comments, List<AttendanceMark> attendanceMarks) {
        Set<Long> classIds = new HashSet<>();
        comments.stream().filter(c -> c.getClassSession() != null).forEach(c -> classIds.add(c.getSchoolClass().getId()));
        attendanceMarks.forEach(m -> classIds.add(m.getAttendanceSession().getClassSession().getSchoolClass().getId()));

        Map<Long, Integer> result = new HashMap<>();
        for (Long classId : classIds) {
            List<ClassSession> ordered = classSessionRepository.findBySchoolClassIdOrderBySessionDateAsc(classId);
            for (int i = 0; i < ordered.size(); i++) {
                result.put(ordered.get(i).getId(), i + 1);
            }
        }
        return result;
    }

    /**
     * Nhận xét DAILY và điểm danh không có academicTermId trực tiếp — suy kỳ
     * theo ngày rơi vào đúng khoảng [startDate, endDate] của kỳ nào thuộc
     * site đó (cache theo site, tải 1 lần/site — mirror cách "Hồ sơ lớp/học
     * sinh theo kỳ" đã TÍNH RA từ ngày tháng ở docs/sdd-groups/06-hoc-thuat.md).
     */
    private AcademicTerm resolveTermForDate(Long siteId, LocalDate date, Map<Long, List<AcademicTerm>> termsBySite) {
        List<AcademicTerm> terms = termsBySite.computeIfAbsent(siteId, academicTermRepository::findBySiteIdOrderByStartDateDesc);
        return terms.stream()
                .filter(t -> !date.isBefore(t.getStartDate()) && !date.isAfter(t.getEndDate()))
                .findFirst()
                .orElse(null);
    }

    private StudentProfileStudentResponse toStudentDto(Student s) {
        return new StudentProfileStudentResponse(
                s.getId(), s.getUser().getFullName(), s.getStudentCode(), s.getDateOfBirth(),
                s.getGender() == null ? null : s.getGender().name(), s.getPortraitUrl(),
                s.getPrimarySite() == null ? null : s.getPrimarySite().getId(),
                s.getPrimarySite() == null ? null : s.getPrimarySite().getName(),
                s.getStatus().name(), s.getEnrollmentDate());
    }

    private StudentProfileEnrollmentResponse toEnrollmentDto(ClassEnrollment ce) {
        return new StudentProfileEnrollmentResponse(
                ce.getId(), ce.getSchoolClass().getId(), ce.getSchoolClass().getName(), ce.getSchoolClass().getClassCode(),
                ce.getEnrolledDate(), ce.getWithdrawnDate(), ce.getStatus().name());
    }

    private StudentProfileGradeResultResponse toGradeResultDto(GradeEvaluationResult r) {
        return new StudentProfileGradeResultResponse(
                r.getId(), r.getSchoolClass().getId(), r.getSchoolClass().getName(),
                r.getAcademicTerm().getId(), r.getAcademicTerm().getName(),
                r.getSchoolClass().getAcademicYear() == null ? null : r.getSchoolClass().getAcademicYear().getName(),
                r.getEvaluationType().name(), r.getOverallScore(), r.getScaleType().name(), r.getLevel(),
                r.getComment(), r.getNote(), r.getStatus().name());
    }

    private StudentProfileSkillScoreResponse toSkillScoreDto(GradeEntry e) {
        return new StudentProfileSkillScoreResponse(
                e.getSchoolClass().getId(), e.getSchoolClass().getName(),
                e.getAcademicTerm().getId(), e.getAcademicTerm().getName(),
                e.getSchoolClass().getAcademicYear() == null ? null : e.getSchoolClass().getAcademicYear().getName(),
                e.getEvaluationType().name(), e.getGradeComponent().getCode().name(), e.getGradeComponent().getName(),
                e.getScore(), e.getGradeComponent().getMaxScore());
    }

    private StudentProfileCommentResponse toCommentDto(StudentComment c, Map<Long, List<AcademicTerm>> termsBySite,
                                                         Map<Long, Integer> sessionNumberBySessionId) {
        AcademicTerm term = resolveTermForDate(c.getSchoolClass().getSite().getId(), c.getCommentDate(), termsBySite);
        Long classSessionId = c.getClassSession().getId();
        return new StudentProfileCommentResponse(
                c.getId(), c.getSchoolClass().getId(), c.getSchoolClass().getName(), c.getCommentType().name(), c.getCommentDate(),
                c.getContent(), c.getSeverity().name(), c.isWarning(), c.getAttitude() == null ? null : c.getAttitude().name(),
                c.getStatus().name(), term == null ? null : term.getId(), term == null ? null : term.getName(),
                c.getAcademicYear() == null ? null : c.getAcademicYear().getName(),
                classSessionId, sessionNumberBySessionId.get(classSessionId), c.getClassSession().getSessionDate());
    }

    private StudentProfileAttendanceResponse toAttendanceDto(AttendanceMark m, Map<Long, List<AcademicTerm>> termsBySite,
                                                              Map<Long, Integer> sessionNumberBySessionId) {
        ClassSession session = m.getAttendanceSession().getClassSession();
        AcademicTerm term = resolveTermForDate(session.getSchoolClass().getSite().getId(), session.getSessionDate(), termsBySite);
        return new StudentProfileAttendanceResponse(
                m.getId(), session.getId(), session.getSchoolClass().getId(), session.getSchoolClass().getName(),
                session.getSessionDate(), sessionNumberBySessionId.get(session.getId()),
                term == null ? null : term.getName(),
                session.getSchoolClass().getAcademicYear() == null ? null : session.getSchoolClass().getAcademicYear().getName(),
                m.getStatus().name(), m.getMinutesLate(), m.getMinutesEarlyLeave());
    }

    /** Tính % giống hệt ExerciseAttemptService (dùng lại static helper cùng package) để không lệch công thức làm tròn/ngưỡng đạt. */
    private StudentProfileHomeworkResponse toHomeworkDto(ExerciseAttempt a) {
        BigDecimal percentage = a.getTotalScore() == null ? null
                : ExerciseAttemptService.percentageOf(a.getTotalScore(), a.getExercise().getTotalPoints());
        return new StudentProfileHomeworkResponse(
                a.getId(), a.getExercise().getId(), a.getExercise().getCode(), a.getExercise().getTitle(),
                a.getExerciseAssignment().getId(), a.getExerciseAssignment().getSchoolClass().getId(),
                a.getExerciseAssignment().getSchoolClass().getName(), a.getExerciseAssignment().getDueAt(),
                a.getAttemptNumber(), a.getStartedAt(), a.getSubmittedAt(),
                a.getTotalScore(), a.getExercise().getTotalPoints(), percentage, a.getPassed(), a.getStatus().name());
    }
}
