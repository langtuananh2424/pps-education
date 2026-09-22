package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AttendanceMark;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.Exercise;
import vn.com.pps.education.domain.ExerciseAssignment;
import vn.com.pps.education.domain.HomeworkSkillBatch;
import vn.com.pps.education.domain.ReviewVideoAssignment;
import vn.com.pps.education.domain.GradeComponentSetup;
import vn.com.pps.education.domain.GradeEntry;
import vn.com.pps.education.domain.GradeEvaluationResult;
import vn.com.pps.education.domain.Parent;
import vn.com.pps.education.domain.ParentStudent;
import vn.com.pps.education.domain.StudentComment;
import vn.com.pps.education.dto.AttendanceMarkResponse;
import vn.com.pps.education.dto.ChildResponse;
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.dto.GradeEntryResponse;
import vn.com.pps.education.dto.GradeEvaluationResultResponse;
import vn.com.pps.education.dto.HomeworkProgressResponse;
import vn.com.pps.education.dto.HomeworkSkillItemResponse;
import vn.com.pps.education.dto.StudentCommentResponse;
import vn.com.pps.education.exception.NotAuthorizedForPortalAccessException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.AttendanceMarkRepository;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.ExerciseAssignmentRepository;
import vn.com.pps.education.repository.GradeEntryRepository;
import vn.com.pps.education.repository.GradeEvaluationResultRepository;
import vn.com.pps.education.repository.ParentRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.ReviewVideoAssignmentRepository;
import vn.com.pps.education.repository.StudentCommentRepository;
import vn.com.pps.education.repository.StudentRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final GradeEvaluationResultRepository gradeEvaluationResultRepository;
    private final AttendanceMarkRepository attendanceMarkRepository;
    private final StudentCommentRepository studentCommentRepository;
    private final ClassSessionRepository classSessionRepository;
    private final ExerciseAssignmentRepository exerciseAssignmentRepository;
    private final ReviewVideoAssignmentRepository reviewVideoAssignmentRepository;
    private final HomeworkProgressService homeworkProgressService;
    private final HomeworkAlertSettings homeworkAlertSettings;

    public ParentPortalService(ParentRepository parentRepository,
                                ParentStudentRepository parentStudentRepository,
                                StudentRepository studentRepository,
                                ClassEnrollmentRepository classEnrollmentRepository,
                                GradeEntryRepository gradeEntryRepository,
                                GradeEvaluationResultRepository gradeEvaluationResultRepository,
                                AttendanceMarkRepository attendanceMarkRepository,
                                StudentCommentRepository studentCommentRepository,
                                ClassSessionRepository classSessionRepository,
                                ExerciseAssignmentRepository exerciseAssignmentRepository,
                                ReviewVideoAssignmentRepository reviewVideoAssignmentRepository,
                                HomeworkProgressService homeworkProgressService,
                                HomeworkAlertSettings homeworkAlertSettings) {
        this.parentRepository = parentRepository;
        this.parentStudentRepository = parentStudentRepository;
        this.studentRepository = studentRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.gradeEntryRepository = gradeEntryRepository;
        this.gradeEvaluationResultRepository = gradeEvaluationResultRepository;
        this.attendanceMarkRepository = attendanceMarkRepository;
        this.studentCommentRepository = studentCommentRepository;
        this.classSessionRepository = classSessionRepository;
        this.exerciseAssignmentRepository = exerciseAssignmentRepository;
        this.reviewVideoAssignmentRepository = reviewVideoAssignmentRepository;
        this.homeworkProgressService = homeworkProgressService;
        this.homeworkAlertSettings = homeworkAlertSettings;
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
     * + Nhận xét/Ghi chú (V95) đã duyệt (OFFICIAL — V44) của 1 (kỳ học,
     * Giữa/Cuối kỳ) — gap trước đây Portal Phụ huynh chỉ có điểm thành
     * phần (listGrades), chưa có Overall/Level.
     */
    @Transactional(readOnly = true)
    public GradeEvaluationResultResponse getEvaluationResult(Long studentId, Long classId, Long academicTermId,
                                                              String evaluationType, Long actorUserId) {
        requireAccessToChildClass(studentId, classId, actorUserId);
        GradeEvaluationResult result = gradeEvaluationResultRepository
                .findBySchoolClassIdAndStudentIdAndAcademicTermIdAndEvaluationType(
                        classId, studentId, academicTermId, GradeComponentSetup.EvaluationType.valueOf(evaluationType))
                .filter(r -> r.getStatus() == GradeEvaluationResult.Status.OFFICIAL)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.parentPortal.officialResultNotFound", new Object[]{studentId, academicTermId},
                        "Chưa có điểm tổng kết đã duyệt cho học sinh id=" + studentId + ", kỳ học id=" + academicTermId + "."));
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
     * (Ngữ pháp/Đọc hiểu/Viết online/offline + Video Kết nối/Phản xạ) đã
     * giao cho học sinh — CHỈ xem tiến độ, không phải giao diện làm bài
     * (việc đó thuộc về học sinh — xem StudentPortalController). Mỗi dòng
     * ứng với 1 buổi có giao BTVN (bỏ qua buổi không giao gì).
     *
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22 (fix bug
     * thật, THIẾT KẾ LẠI nguồn dữ liệu — bản trước chỉ sửa điều kiện lọc
     * status vẫn không đủ): 4 kênh BTVN ONLINE (Ngữ pháp/Nghe/Đọc/Viết qua
     * {@link HomeworkSkillBatch}, Video qua {@link ReviewVideoAssignment})
     * giờ đọc THẲNG bảng giao theo LỚP (exercise_assignments/
     * review_video_assignments, target_student_ids=NULL=cả lớp) — ĐÚNG
     * NGUỒN Portal Học sinh đang dùng ({@code ExerciseAttemptService#
     * listMyAssignedExercises}/{@code ReviewVideoService}) — thay vì đi
     * qua FK trên {@code StudentComment} như trước.
     *
     * Lý do đổi hẳn nguồn: FK trên StudentComment do
     * {@code StudentCommentService#applyHomeworkToClass} gán, nhưng method
     * đó CHỦ Ý BỎ QUA học sinh điểm danh Vắng/Có phép đúng buổi giao (không
     * tạo dòng, không gán FK) — trong khi bản giao ở exercise_assignments/
     * review_video_assignments vẫn ÁP DỤNG CHO CẢ LỚP (không phân biệt
     * vắng/có mặt hôm đó), Portal Học sinh vẫn cho làm bình thường. Ca thật
     * gặp: học sinh vắng đúng buổi GV giao Video phản xạ qua "Áp dụng cho
     * cả lớp" — học sinh vẫn thấy/làm được, nhưng Cổng phụ huynh trống
     * trơn vì FK chưa từng được gán. Đọc thẳng bảng giao mới ĐẢM BẢO số
     * liệu ở đây luôn khớp với Portal Học sinh và tab "Quá trình học tập"
     * (tránh phụ huynh thắc mắc 2 màn lệch số nhau).
     *
     * Riêng BTVN OFFLINE (chữ tự do gõ CHUNG lúc viết nhận xét —
     * homeworkNext(Reading/Writing)) KHÔNG có bảng giao riêng, vẫn chỉ tồn
     * tại trên StudentComment — merge thêm từ nhận xét APPROVED (mirror
     * UC-25 A2, xem {@link #listComments}) vào ĐÚNG dòng theo buổi
     * (classSessionId) nếu trùng với 1 dòng online ở trên, không thì tự
     * thành 1 dòng riêng.
     */
    @Transactional(readOnly = true)
    public List<HomeworkProgressResponse> listHomeworkProgress(Long studentId, Long classId, Long actorUserId) {
        requireAccessToChildClass(studentId, classId, actorUserId);

        Map<String, HomeworkRow> rows = new LinkedHashMap<>();

        List<ExerciseAssignment> exercises = exerciseAssignmentRepository
                .findBySchoolClassIdAndStatus(classId, ExerciseAssignment.Status.ACTIVE).stream()
                .filter(a -> a.getHomeworkBatch() != null)
                .filter(a -> appliesToStudent(a.getTargetStudentIds(), studentId))
                .toList();
        Map<Long, List<ExerciseAssignment>> byBatchId = exercises.stream()
                .collect(Collectors.groupingBy(a -> a.getHomeworkBatch().getId()));
        for (List<ExerciseAssignment> assignments : byBatchId.values()) {
            List<ExerciseAssignment> sorted = assignments.stream()
                    .sorted(Comparator.comparing(a -> a.getExercise().getId())).toList();
            HomeworkSkillBatch batch = sorted.get(0).getHomeworkBatch();
            HomeworkRow row = rows.computeIfAbsent(
                    rowKey(batch.getSourceClassSession(), "batch", batch.getId()),
                    k -> new HomeworkRow(rowDate(batch.getSourceClassSession(), batch.getCreatedAt()),
                            batch.getSourceClassSession() == null ? null : batch.getSourceClassSession().getId(),
                            batch.getSourceClassSession() == null ? -batch.getId() : batch.getSourceClassSession().getId()));
            if (batch.getSkillCategory() == Exercise.SkillCategory.READING) {
                row.readingBatch = batch;
                row.readingAssignments = sorted;
            } else if (batch.getSkillCategory() == Exercise.SkillCategory.WRITING) {
                row.writingBatch = batch;
                row.writingAssignments = sorted;
            } else {
                // VOCAB_GRAMMAR (buổi VIETNAMESE) hoặc LISTENING (buổi FOREIGN) — dùng CHUNG 1 field
                // "grammar" (mirror StudentCommentService#grammarChannelSkillCategory, chỉ 1 trong 2 áp
                // dụng cho 1 buổi tuỳ teacherType, không bao giờ trùng cả 2 cho cùng 1 buổi).
                row.grammarBatch = batch;
                row.grammarAssignments = sorted;
            }
        }

        List<ReviewVideoAssignment> videos = reviewVideoAssignmentRepository
                .findBySchoolClassIdAndStatus(classId, ReviewVideoAssignment.Status.ACTIVE).stream()
                .filter(a -> appliesToStudent(a.getTargetStudentIds(), studentId))
                .toList();
        for (ReviewVideoAssignment video : videos) {
            HomeworkRow row = rows.computeIfAbsent(
                    rowKey(video.getSourceClassSession(), "video", video.getId()),
                    k -> new HomeworkRow(rowDate(video.getSourceClassSession(), video.getAvailableFrom()),
                            video.getSourceClassSession() == null ? null : video.getSourceClassSession().getId(),
                            video.getSourceClassSession() == null ? -video.getId() : video.getSourceClassSession().getId()));
            row.video = video;
        }

        studentCommentRepository.findBySchoolClassIdAndStudentIdOrderByCommentDateDesc(classId, studentId).stream()
                .filter(c -> c.getStatus() == StudentComment.Status.APPROVED)
                .filter(c -> c.getHomeworkNext() != null || c.getHomeworkNextReading() != null || c.getHomeworkNextWriting() != null)
                .forEach(c -> {
                    HomeworkRow row = rows.computeIfAbsent(
                            rowKey(c.getClassSession(), "comment", c.getId()),
                            k -> new HomeworkRow(c.getCommentDate(), c.getClassSession().getId(), c.getClassSession().getId()));
                    row.grammarOfflineText = c.getHomeworkNext();
                    row.readingOfflineText = c.getHomeworkNextReading();
                    row.writingOfflineText = c.getHomeworkNextWriting();
                });

        return rows.values().stream()
                .sorted(Comparator.comparing((HomeworkRow r) -> r.date).reversed())
                .map(row -> toHomeworkProgressResponse(row, studentId))
                .toList();
    }

    /** NULL/rỗng = giao cả lớp (SDD, mirror ExerciseReportService#... đọc targetStudentIds tương tự). */
    private boolean appliesToStudent(List<Long> targetStudentIds, Long studentId) {
        return targetStudentIds == null || targetStudentIds.isEmpty() || targetStudentIds.contains(studentId);
    }

    /** Khoá gộp 1 dòng theo ĐÚNG buổi học (session) khi có — để 1 buổi vừa giao Ngữ pháp vừa giao Video vẫn gộp thành 1 dòng, khớp UX cũ. Không có session (dữ liệu cũ trước V123) thì tự thành 1 dòng riêng theo loại+id, tránh gộp nhầm 2 bản giao không liên quan. */
    private String rowKey(ClassSession session, String fallbackType, Long fallbackId) {
        return session != null ? "session-" + session.getId() : fallbackType + "-" + fallbackId;
    }

    private LocalDate rowDate(ClassSession session, OffsetDateTime fallback) {
        return session != null ? session.getSessionDate() : fallback.toLocalDate();
    }

    /** 1 dòng BTVN theo buổi (giao diện Cổng phụ huynh) — gom từ 2 nguồn độc lập (bảng giao theo Lớp + nhận xét APPROVED, xem Javadoc listHomeworkProgress). */
    private static final class HomeworkRow {
        final LocalDate date;
        final Long classSessionId;
        final Long id;
        HomeworkSkillBatch grammarBatch;
        List<ExerciseAssignment> grammarAssignments = List.of();
        HomeworkSkillBatch readingBatch;
        List<ExerciseAssignment> readingAssignments = List.of();
        HomeworkSkillBatch writingBatch;
        List<ExerciseAssignment> writingAssignments = List.of();
        ReviewVideoAssignment video;
        String grammarOfflineText;
        String readingOfflineText;
        String writingOfflineText;

        HomeworkRow(LocalDate date, Long classSessionId, Long id) {
            this.date = date;
            this.classSessionId = classSessionId;
            this.id = id;
        }
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
            throw new ResourceNotFoundException("error.parentPortal.studentNeverEnrolledInClass", new Object[]{studentId, classId}, "Học sinh id=" + studentId + " chưa từng học lớp id=" + classId + ".");
        }
    }

    private void requireLinkedParent(Long studentId, Long actorUserId) {
        if (!studentRepository.existsById(studentId)) {
            throw new ResourceNotFoundException("error.parentPortal.studentNotFoundById", new Object[]{studentId}, "Không tìm thấy học sinh id=" + studentId);
        }
        Parent parent = parentRepository.findByUserId(actorUserId).orElse(null);
        if (parent == null || parentStudentRepository.findByParentIdAndStudentId(parent.getId(), studentId).isEmpty()) {
            throw new NotAuthorizedForPortalAccessException(
                    "error.notAuthorizedForPortalAccess.parentNotLinkedToStudent", new Object[]{},
                    "Tài khoản của bạn không phải phụ huynh liên kết với học sinh này.");
        }
    }

    private Parent parentOrThrow(Long actorUserId) {
        return parentRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new NotAuthorizedForPortalAccessException(
                        "error.notAuthorizedForPortalAccess.noParentProfile", new Object[]{},
                        "Tài khoản của bạn không có hồ sơ phụ huynh."));
    }

    private GradeEntryResponse toResponse(GradeEntry e) {
        return new GradeEntryResponse(
                e.getId(), e.getSchoolClass().getId(), e.getStudent().getId(), e.getStudent().getUser().getFullName(),
                e.getStudent().getStudentCode(), e.getGradeComponent().getId(), e.getAcademicTerm().getId(),
                e.getAcademicYear() == null ? null : e.getAcademicYear().getId(),
                e.getAcademicYear() == null ? null : e.getAcademicYear().getCode(),
                e.getEvaluationType().name(),
                e.getScore(), e.isAbsenceFlag(), e.getTeacherNote(), e.getStatus().name(), e.getEnteredBy().getId(),
                e.getPublishedBy() == null ? null : e.getPublishedBy().getId(), e.getPublishedAt(), e.getFinalizedAt());
    }

    private GradeEvaluationResultResponse toResponse(GradeEvaluationResult r) {
        return new GradeEvaluationResultResponse(
                r.getId(), r.getSchoolClass().getId(), r.getStudent().getId(),
                r.getStudent().getUser().getFullName(), r.getStudent().getStudentCode(),
                r.getAcademicTerm().getId(), r.getEvaluationType().name(), r.getOverallScore(), r.getScaleType().name(),
                r.getLevel(), r.getComment(), r.getNote(), r.getDisclaimer(),
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
        // Portal Phụ huynh chưa cần hiển thị chi tiết BTVN online/Reading-Writing (UC-21 mở rộng,
        // V55/V130/V137) — để trống hết, bổ sung khi có yêu cầu. Liệt kê tường minh từng field null (thay
        // vì gộp nhiều null liền nhau) để tránh đếm nhầm khi StudentCommentResponse đổi field sau này.
        return new StudentCommentResponse(
                c.getId(), c.getStudent().getId(), c.getStudent().getUser().getFullName(), c.getStudent().getDateOfBirth(),
                c.getSchoolClass().getId(), c.getTeacher().getId(), c.getCommentType().name(),
                c.getClassSession().getId(),
                c.getAcademicYear() == null ? null : c.getAcademicYear().getId(),
                c.getAcademicYear() == null ? null : c.getAcademicYear().getCode(),
                c.getCommentDate(), c.getContent(), c.getStructuredContent(), c.getSeverity().name(), c.isWarning(),
                c.getStatus().name(), c.getSubmittedAt(), c.getApprovedAt(),
                c.getApprovedBy() == null ? null : c.getApprovedBy().getId(), c.getVisibleToParentAt(), c.getRejectionReason(),
                c.getAttitude() == null ? null : c.getAttitude().name(), c.getHomeworkPreviousScore(),
                c.getHomeworkPreviousSpeakingScore(),
                null /* homeworkPreviousReadingScore */, null /* homeworkPreviousWritingScore */,
                c.getHomeworkNext(),
                null /* homeworkNextReading */, null /* homeworkNextWriting */,
                null /* homeworkNextExerciseAssignmentId */, null /* homeworkNextExerciseTitle */,
                null /* homeworkNextReviewVideoAssignmentId */, null /* homeworkNextReviewVideoSetTitle */,
                null /* homeworkNextReadingExerciseAssignmentId */, null /* homeworkNextReadingExerciseTitle */,
                null /* homeworkNextWritingExerciseAssignmentId */, null /* homeworkNextWritingExerciseTitle */,
                null /* homeworkNextDueAt */,
                false /* homeworkNextLateSubmissionAllowed */,
                null /* pendingHomeworkNextExerciseId */, null /* pendingHomeworkNextExerciseTitle */,
                null /* pendingHomeworkNextReviewVideoSetId */, null /* pendingHomeworkNextReviewVideoSetTitle */,
                null /* pendingHomeworkNextReadingExerciseId */, null /* pendingHomeworkNextReadingExerciseTitle */,
                null /* pendingHomeworkNextWritingExerciseId */, null /* pendingHomeworkNextWritingExerciseTitle */,
                null /* pendingHomeworkNextDueDate */,
                null /* grammarPreviousProgress */, null /* videoPreviousProgress */,
                null /* readingPreviousProgress */, null /* writingPreviousProgress */,
                null /* homeworkPreviousOfflineText */,
                c.getNote(),
                c.getClassSession().getLessonContent());
    }

    private HomeworkProgressResponse toHomeworkProgressResponse(HomeworkRow row, Long studentId) {
        String grammarProgress = homeworkProgressService.grammarProgressLabel(row.grammarAssignments, studentId);
        String readingProgress = homeworkProgressService.grammarProgressLabel(row.readingAssignments, studentId);
        String writingProgress = homeworkProgressService.grammarProgressLabel(row.writingAssignments, studentId);
        ReviewVideoAssignment video = row.video;

        return new HomeworkProgressResponse(
                row.id, row.classSessionId, row.date,
                row.grammarBatch == null ? null : row.grammarBatch.getId(),
                batchTitle(row.grammarBatch, row.grammarAssignments),
                row.grammarBatch == null ? row.grammarOfflineText : null,
                grammarProgress,
                aggregatePassed(grammarProgress, row.grammarAssignments, studentId),
                toItems(row.grammarAssignments, studentId),
                row.grammarBatch == null ? null : row.grammarBatch.getSkillCategory().name(),
                batchDueAt(row.grammarAssignments),
                batchUnitTitle(row.grammarBatch),
                row.readingBatch == null ? null : row.readingBatch.getId(),
                batchTitle(row.readingBatch, row.readingAssignments),
                row.readingBatch == null ? row.readingOfflineText : null,
                readingProgress,
                aggregatePassed(readingProgress, row.readingAssignments, studentId),
                toItems(row.readingAssignments, studentId),
                batchDueAt(row.readingAssignments),
                batchUnitTitle(row.readingBatch),
                row.writingBatch == null ? null : row.writingBatch.getId(),
                batchTitle(row.writingBatch, row.writingAssignments),
                row.writingBatch == null ? row.writingOfflineText : null,
                writingProgress,
                aggregatePassed(writingProgress, row.writingAssignments, studentId),
                toItems(row.writingAssignments, studentId),
                batchDueAt(row.writingAssignments),
                batchUnitTitle(row.writingBatch),
                video == null ? null : video.getId(),
                video == null ? null : video.getReviewVideoSet().getTitle(),
                homeworkProgressService.videoProgressLabel(video, studentId),
                video == null ? null : homeworkProgressService.videoPassed(video, studentId, homeworkAlertSettings.reflexPassThresholdPercent()),
                video == null ? null : video.getReviewVideoSet().getVideoType().name(),
                video == null ? null : video.getReviewVideoSet().getTeacherType().name(),
                video == null ? null : video.getDueAt(),
                video == null || video.getReviewVideoSet().getSubTopic() == null ? null : video.getReviewVideoSet().getSubTopic().getUnit().getTitle());
    }

    /** Hạn nộp của 1 Lô — mọi Bài trong lô dùng CHUNG 1 hạn (gán đồng loạt lúc assignBatchToClass), lấy đại diện Bài đầu tiên. */
    private OffsetDateTime batchDueAt(List<ExerciseAssignment> assignments) {
        return assignments.isEmpty() ? null : assignments.get(0).getDueAt();
    }

    /** Unit chứa Lesson (Exam) của 1 Lô — NULL nếu Đề chưa gắn Sub Topic (Đề cũ trước cấu trúc Sách → Unit → Sub Topic). */
    private String batchUnitTitle(HomeworkSkillBatch batch) {
        if (batch == null || batch.getExam().getSubTopic() == null) {
            return null;
        }
        return batch.getExam().getSubTopic().getUnit().getTitle();
    }

    /**
     * "Đạt"/"Chưa đạt" chỉ có ý nghĩa khi progress THẬT SỰ là 1 con số % (đã có lượt làm được chấm) —
     * bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22 (fix bug thật): trước đây field
     * *Passed trả về {@code false} (không phải {@code null}) ngay cả khi progress còn là nhãn "Chưa làm
     * bài"/"Đang chờ chấm" (vì {@code HomeworkProgressService#grammarPassed} là {@code boolean} nguyên
     * thuỷ, không bao giờ tự null), khiến FE (ProgressBadge) hiện nhầm pill đỏ "Chưa đạt" cho bài CHƯA
     * làm/CHƯA chấm xong thay vì chỉ hiện nhãn trung tính kèm icon đồng hồ.
     */
    private Boolean aggregatePassed(String progress, List<ExerciseAssignment> assignments, Long studentId) {
        if (assignments.isEmpty() || progress == null || !progress.endsWith("%")) {
            return null;
        }
        return homeworkProgressService.grammarPassed(assignments, studentId);
    }

    /** Mirror {@link #aggregatePassed} cho TỪNG Bài lẻ trong lô (xem {@link #toItems}). */
    private Boolean itemPassed(String progress, ExerciseAssignment assignment, Long studentId) {
        if (progress == null || !progress.endsWith("%")) {
            return null;
        }
        return homeworkProgressService.grammarPassed(assignment, studentId);
    }

    /**
     * V150 mở rộng (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22) — trước đây Cổng phụ
     * huynh chỉ thấy % GỘP của cả Lô, không biết bài nào trong lô làm tốt/chưa tốt (VD lô 3 bài, 1 bài
     * điểm rất thấp bị 2 bài kia kéo % gộp lên trông vẫn "ổn"). Trả về % + đạt/chưa đạt của TỪNG Bài để
     * FE hiện phần "Xem chi tiết" mở rộng, không thay đổi cách hiện % gộp ở trên (vẫn giữ để phụ huynh
     * quét nhanh).
     */
    private List<HomeworkSkillItemResponse> toItems(List<ExerciseAssignment> assignments, Long studentId) {
        return assignments.stream()
                .map(a -> {
                    String progress = homeworkProgressService.grammarProgressLabel(a, studentId);
                    return new HomeworkSkillItemResponse(a.getId(), a.getExercise().getTitle(), progress, itemPassed(progress, a, studentId));
                })
                .toList();
    }

    /** Nhãn hiển thị 1 Lô cho Cổng phụ huynh — tên Lesson (Exam) + số bài nếu >1 Bài (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22, thay bản nối tên từng Bài cũ dễ bị tràn/cắt chữ ở FE). */
    private String batchTitle(HomeworkSkillBatch batch, List<ExerciseAssignment> assignments) {
        if (batch == null) {
            return null;
        }
        String examTitle = batch.getExam().getTitle();
        return assignments.size() > 1 ? examTitle + " (" + assignments.size() + " bài)" : examTitle;
    }

    private ClassSessionResponse toResponse(ClassSession s) {
        int sessionNumber = (int) classSessionRepository.countEarlierSessions(
                s.getSchoolClass().getId(), s.getSessionDate(), s.getId()) + 1;
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
                s.getActualTeacherName(), s.getOriginalTeacherName(), sessionNumber,
                s.getMakeupForSession() == null ? null : s.getMakeupForSession().getId(),
                s.getSchoolClass().getColor());
    }
}
