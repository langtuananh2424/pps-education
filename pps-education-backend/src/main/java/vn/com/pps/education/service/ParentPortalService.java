package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AttendanceMark;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.ExerciseAssignment;
import vn.com.pps.education.domain.ReviewVideoAssignment;
import vn.com.pps.education.domain.GradeEntry;
import vn.com.pps.education.domain.GradePeriodResult;
import vn.com.pps.education.domain.Parent;
import vn.com.pps.education.domain.ParentStudent;
import vn.com.pps.education.domain.StudentComment;
import vn.com.pps.education.dto.AttendanceMarkResponse;
import vn.com.pps.education.dto.ChildResponse;
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.dto.GradeEntryResponse;
import vn.com.pps.education.dto.GradePeriodResultResponse;
import vn.com.pps.education.dto.HomeworkProgressResponse;
import vn.com.pps.education.dto.StudentCommentResponse;
import vn.com.pps.education.exception.NotAuthorizedForPortalAccessException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.AttendanceMarkRepository;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.GradeEntryRepository;
import vn.com.pps.education.repository.GradePeriodResultRepository;
import vn.com.pps.education.repository.ParentRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.StudentCommentRepository;
import vn.com.pps.education.repository.StudentRepository;

import java.util.List;

/**
 * UC-25: Xem Portal Phụ huynh (FR-LMS-03, FR-LMS-07). Xem
 * docs/uc/phan-he-07-lms-portal.md. Read-only, không có bảng riêng — mọi
 * dữ liệu lấy từ các Repository đã có (SDD > LMS & Portal > Portal Phụ
 * huynh): bảng điểm (grade_entries — V44: chỉ hiển thị OFFICIAL, đã Quản
 * lý điểm trường duyệt — xem GradeService lớp Javadoc), chuyên cần
 * (attendance_marks join class_sessions), nhận xét/cảnh báo
 * (student_comments APPROVED), lịch học (class_sessions). Main Flow bước 5
 * (thông báo khẩn) không cần endpoint riêng — GET /api/notifications
 * (NotificationController) đã tự phục vụ mọi user kể cả Phụ huynh, không
 * viết lại.
 *
 * A1 (dữ liệu chưa duyệt không hiển thị) đã nằm sẵn trong các query WHERE
 * status = OFFICIAL (điểm) / status=APPROVED (nhận xét) — không cần
 * nhánh riêng.
 */
@Service
public class ParentPortalService {

    private final ParentRepository parentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final StudentRepository studentRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final GradeEntryRepository gradeEntryRepository;
    private final GradePeriodResultRepository gradePeriodResultRepository;
    private final AttendanceMarkRepository attendanceMarkRepository;
    private final StudentCommentRepository studentCommentRepository;
    private final ClassSessionRepository classSessionRepository;
    private final HomeworkProgressService homeworkProgressService;

    public ParentPortalService(ParentRepository parentRepository,
                                ParentStudentRepository parentStudentRepository,
                                StudentRepository studentRepository,
                                ClassEnrollmentRepository classEnrollmentRepository,
                                GradeEntryRepository gradeEntryRepository,
                                GradePeriodResultRepository gradePeriodResultRepository,
                                AttendanceMarkRepository attendanceMarkRepository,
                                StudentCommentRepository studentCommentRepository,
                                ClassSessionRepository classSessionRepository,
                                HomeworkProgressService homeworkProgressService) {
        this.parentRepository = parentRepository;
        this.parentStudentRepository = parentStudentRepository;
        this.studentRepository = studentRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.gradeEntryRepository = gradeEntryRepository;
        this.gradePeriodResultRepository = gradePeriodResultRepository;
        this.attendanceMarkRepository = attendanceMarkRepository;
        this.studentCommentRepository = studentCommentRepository;
        this.classSessionRepository = classSessionRepository;
        this.homeworkProgressService = homeworkProgressService;
    }

    /** Main Flow bước 2: nếu Phụ huynh có nhiều con, hiển thị lựa chọn tách riêng dữ liệu. */
    @Transactional(readOnly = true)
    public List<ChildResponse> listMyChildren(Long actorUserId) {
        Parent parent = parentOrThrow(actorUserId);
        return parentStudentRepository.findByParentId(parent.getId()).stream()
                .map(ParentStudent::getStudent)
                .map(s -> new ChildResponse(s.getId(), s.getUser().getFullName(), s.getStudentCode()))
                .toList();
    }

    /** Main Flow bước 3: bảng điểm đã duyệt (UC-20 V44 — chỉ OFFICIAL, không hiển thị SUBMITTED/REJECTED). */
    @Transactional(readOnly = true)
    public List<GradeEntryResponse> listGrades(Long studentId, Long classId, Long actorUserId) {
        requireAccessToChildClass(studentId, classId, actorUserId);
        return gradeEntryRepository.findBySchoolClassIdAndStudentIdAndStatusIn(classId, studentId, List.of(GradeEntry.Status.OFFICIAL))
                .stream().map(this::toResponse).toList();
    }

    /**
     * UC-53 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): Overall/Level
     * đã duyệt (OFFICIAL — V44) của 1 kỳ đánh giá — gap trước đây Portal
     * Phụ huynh chỉ có điểm thành phần (listGrades), chưa có Overall/Level.
     */
    @Transactional(readOnly = true)
    public GradePeriodResultResponse getPeriodResult(Long studentId, Long classId, Long gradePeriodId, Long actorUserId) {
        requireAccessToChildClass(studentId, classId, actorUserId);
        GradePeriodResult result = gradePeriodResultRepository
                .findBySchoolClassIdAndStudentIdAndGradePeriodId(classId, studentId, gradePeriodId)
                .filter(r -> r.getStatus() == GradePeriodResult.Status.OFFICIAL)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Chưa có điểm tổng kết đã duyệt cho học sinh id=" + studentId + ", kỳ đánh giá id=" + gradePeriodId + "."));
        return toResponse(result);
    }

    /** Main Flow bước 4: chuyên cần. */
    @Transactional(readOnly = true)
    public List<AttendanceMarkResponse> listAttendance(Long studentId, Long classId, Long actorUserId) {
        requireAccessToChildClass(studentId, classId, actorUserId);
        return attendanceMarkRepository.findByStudentIdAndClassId(studentId, classId).stream().map(this::toResponse).toList();
    }

    /** Main Flow bước 3: nhận xét giáo viên đã duyệt (UC-22), bao gồm cảnh báo (is_warning). */
    @Transactional(readOnly = true)
    public List<StudentCommentResponse> listComments(Long studentId, Long classId, Long actorUserId) {
        requireAccessToChildClass(studentId, classId, actorUserId);
        return studentCommentRepository
                .findBySchoolClassIdAndStudentIdAndStatusOrderByCommentDateDesc(classId, studentId, StudentComment.Status.APPROVED)
                .stream().map(this::toResponse).toList();
    }

    /**
     * UC-25 bước 4 ("tình trạng bài tập" — trước đây chưa có endpoint
     * riêng, đã xác nhận với người dùng 2026-07-29): tiến độ BTVN
     * (Ngữ pháp online/offline + Video Kết nối/Phản xạ) đã giao qua nhận
     * xét hàng ngày đã duyệt — CHỈ xem tiến độ, không phải giao diện làm
     * bài (việc đó thuộc về học sinh — xem StudentPortalController). Mỗi
     * dòng ứng với 1 buổi có giao BTVN (bỏ qua buổi không giao gì).
     */
    @Transactional(readOnly = true)
    public List<HomeworkProgressResponse> listHomeworkProgress(Long studentId, Long classId, Long actorUserId) {
        requireAccessToChildClass(studentId, classId, actorUserId);
        return studentCommentRepository
                .findBySchoolClassIdAndStudentIdAndStatusOrderByCommentDateDesc(classId, studentId, StudentComment.Status.APPROVED)
                .stream()
                .filter(c -> c.getHomeworkNext() != null || c.getHomeworkNextExerciseAssignment() != null
                        || c.getHomeworkNextReviewVideoAssignment() != null)
                .map(this::toHomeworkProgressResponse)
                .toList();
    }

    /** Main Flow bước 3: lịch học của con. */
    @Transactional(readOnly = true)
    public List<ClassSessionResponse> listSchedule(Long studentId, Long classId, Long actorUserId) {
        requireAccessToChildClass(studentId, classId, actorUserId);
        return classSessionRepository.findBySchoolClassIdOrderBySessionDateAsc(classId).stream().map(this::toResponse).toList();
    }

    // ===================== Helpers =====================

    /**
     * NFR-SEC-03: Phụ huynh chỉ xem được dữ liệu của đúng con mình
     * (parent_student) và đúng lớp con đã/đang học (class_enrollments —
     * bao gồm cả lớp cũ, Postcondition UC-25 nối UC-42).
     */
    private void requireAccessToChildClass(Long studentId, Long classId, Long actorUserId) {
        requireLinkedParent(studentId, actorUserId);
        boolean everEnrolled = classEnrollmentRepository.findByStudentId(studentId).stream()
                .anyMatch(e -> e.getSchoolClass().getId().equals(classId));
        if (!everEnrolled) {
            throw new ResourceNotFoundException("Học sinh id=" + studentId + " chưa từng học lớp id=" + classId + ".");
        }
    }

    private void requireLinkedParent(Long studentId, Long actorUserId) {
        if (!studentRepository.existsById(studentId)) {
            throw new ResourceNotFoundException("Không tìm thấy học sinh id=" + studentId);
        }
        Parent parent = parentRepository.findByUserId(actorUserId).orElse(null);
        if (parent == null || parentStudentRepository.findByParentIdAndStudentId(parent.getId(), studentId).isEmpty()) {
            throw new NotAuthorizedForPortalAccessException(
                    "Tài khoản id=" + actorUserId + " không phải phụ huynh liên kết với học sinh id=" + studentId + ".");
        }
    }

    private Parent parentOrThrow(Long actorUserId) {
        return parentRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new NotAuthorizedForPortalAccessException(
                        "Tài khoản id=" + actorUserId + " không có hồ sơ phụ huynh."));
    }

    private GradeEntryResponse toResponse(GradeEntry e) {
        return new GradeEntryResponse(
                e.getId(), e.getSchoolClass().getId(), e.getStudent().getId(), e.getStudent().getUser().getFullName(),
                e.getStudent().getStudentCode(), e.getGradeComponent().getId(), e.getScore(), e.isAbsenceFlag(),
                e.getTeacherNote(), e.getStatus().name(), e.getEnteredBy().getId(),
                e.getPublishedBy() == null ? null : e.getPublishedBy().getId(), e.getPublishedAt(), e.getFinalizedAt());
    }

    private GradePeriodResultResponse toResponse(GradePeriodResult r) {
        return new GradePeriodResultResponse(
                r.getId(), r.getSchoolClass().getId(), r.getStudent().getId(),
                r.getStudent().getUser().getFullName(), r.getStudent().getStudentCode(),
                r.getGradePeriod().getId(), r.getOverallScore(), r.getScaleType().name(), r.getLevel(),
                r.getSource().name(), r.getImportJob() == null ? null : r.getImportJob().getId(),
                r.getStatus().name(), r.getEnteredBy().getId(),
                r.getPublishedBy() == null ? null : r.getPublishedBy().getId(), r.getPublishedAt(), r.getFinalizedAt());
    }

    private AttendanceMarkResponse toResponse(AttendanceMark m) {
        return new AttendanceMarkResponse(
                m.getId(), m.getAttendanceSession().getId(), m.getAttendanceSession().getClassSession().getId(),
                m.getStudent().getId(), m.getStudent().getUser().getFullName(),
                m.getStudent().getStudentCode(), m.getStatus().name(), m.getMinutesLate(), m.getMinutesEarlyLeave(),
                m.getAbsenceReason(), m.getNotifiedParentAt());
    }

    private StudentCommentResponse toResponse(StudentComment c) {
        return new StudentCommentResponse(
                c.getId(), c.getStudent().getId(), c.getStudent().getUser().getFullName(), c.getStudent().getDateOfBirth(),
                c.getSchoolClass().getId(), c.getTeacher().getId(), c.getCommentType().name(),
                c.getClassSession() == null ? null : c.getClassSession().getId(),
                c.getGradePeriod() == null ? null : c.getGradePeriod().getId(),
                c.getCommentDate(), c.getContent(), c.getStructuredContent(), c.getSeverity().name(), c.isWarning(),
                c.getStatus().name(), c.getSubmittedAt(), c.getApprovedAt(),
                c.getApprovedBy() == null ? null : c.getApprovedBy().getId(), c.getVisibleToParentAt(), c.getRejectionReason(),
                c.getAttitude() == null ? null : c.getAttitude().name(), c.getHomeworkPreviousScore(),
                c.getHomeworkPreviousSpeakingScore(),
                // Portal Phụ huynh chưa cần hiển thị chi tiết BTVN online (UC-21 mở rộng, V55) — để trống, bổ sung khi có yêu cầu.
                c.getHomeworkNext(), null, null, null, null, null, null, null, null, c.getNote(),
                c.getClassSession() == null ? null : c.getClassSession().getLessonContent());
    }

    private HomeworkProgressResponse toHomeworkProgressResponse(StudentComment c) {
        ExerciseAssignment grammar = c.getHomeworkNextExerciseAssignment();
        ReviewVideoAssignment video = c.getHomeworkNextReviewVideoAssignment();
        return new HomeworkProgressResponse(
                c.getId(), c.getClassSession().getId(), c.getCommentDate(),
                grammar == null ? null : grammar.getId(),
                grammar == null ? null : grammar.getExercise().getTitle(),
                grammar == null ? c.getHomeworkNext() : null,
                homeworkProgressService.grammarProgressLabel(grammar, c.getStudent().getId()),
                video == null ? null : video.getId(),
                video == null ? null : video.getReviewVideoSet().getTitle(),
                homeworkProgressService.videoProgressLabel(video, c.getStudent().getId()));
    }

    private ClassSessionResponse toResponse(ClassSession s) {
        int sessionNumber = (int) classSessionRepository.countEarlierSessions(
                s.getSchoolClass().getId(), s.getSessionDate(), s.getId()) + 1;
        return new ClassSessionResponse(
                s.getId(), s.getSchoolClass().getId(), s.getSessionDate(), s.getStartTime(), s.getEndTime(),
                s.getRoom() == null ? null : s.getRoom().getId(), s.getRoom() == null ? null : s.getRoom().getName(),
                s.getPrimaryTeacher().getId(), s.getPrimaryTeacher().getFullName(), s.getSessionType().name(), s.getStatus().name(),
                s.getCancellationReason(), s.getRescheduledToSession() == null ? null : s.getRescheduledToSession().getId(),
                s.getLessonContent(), s.getTeacherType() == null ? null : s.getTeacherType().name(),
                s.getActualTeacherName(), sessionNumber,
                s.getMakeupForSession() == null ? null : s.getMakeupForSession().getId());
    }
}
