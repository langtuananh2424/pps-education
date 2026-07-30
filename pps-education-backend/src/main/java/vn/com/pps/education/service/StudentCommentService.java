package vn.com.pps.education.service;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.common.ExcelExportHelper;
import vn.com.pps.education.domain.ApprovalFlow;
import vn.com.pps.education.domain.AttendanceMark;
import vn.com.pps.education.domain.AttendanceSession;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.Exercise;
import vn.com.pps.education.domain.ExerciseAssignment;
import vn.com.pps.education.domain.GradePeriod;
import vn.com.pps.education.domain.ImportJob;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.ReviewVideoAssignment;
import vn.com.pps.education.domain.ReviewVideoSet;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.StudentComment;
import vn.com.pps.education.domain.StudentCommentHistory;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.ClassSessionLessonContentResponse;
import vn.com.pps.education.dto.CreateStudentCommentRequest;
import vn.com.pps.education.dto.DailyCommentImportResponse;
import vn.com.pps.education.dto.DecideCommentsRequest;
import vn.com.pps.education.dto.EnterAttendanceMarkRequest;
import vn.com.pps.education.dto.MarkAttendanceRequest;
import vn.com.pps.education.dto.StudentCommentResponse;
import vn.com.pps.education.dto.SubmitCommentsRequest;
import vn.com.pps.education.dto.UpdateStudentCommentRequest;
import vn.com.pps.education.exception.ApprovalAlreadyDecidedException;
import vn.com.pps.education.exception.HomeworkNextConflictException;
import vn.com.pps.education.exception.InvalidCommentContextException;
import vn.com.pps.education.exception.MissingLessonContentException;
import vn.com.pps.education.exception.NoUpcomingClassSessionException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.StudentCommentNotEditableException;
import vn.com.pps.education.repository.ApprovalFlowRepository;
import vn.com.pps.education.repository.AttendanceMarkRepository;
import vn.com.pps.education.repository.AttendanceSessionRepository;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.ExerciseRepository;
import vn.com.pps.education.repository.GradePeriodRepository;
import vn.com.pps.education.repository.ImportJobRepository;
import vn.com.pps.education.repository.ReviewVideoAssignmentRepository;
import vn.com.pps.education.repository.ReviewVideoSetRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.ClassTeacherRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.StudentCommentHistoryRepository;
import vn.com.pps.education.repository.StudentCommentRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * UC-21: Viết nhận xét học sinh (FR-ACA-04) + UC-22: Duyệt nhận xét
 * (FR-LMS-09). Xem docs/uc/phan-he-06-hoc-thuat.md và
 * docs/diagrams/activity/ActivityDiagram-DuyetNhanXet.mmd.
 *
 * Dùng lại ApprovalFlow (entity_type=STUDENT_COMMENT), giống pattern
 * UC-19/20 (GradeService) — mỗi nhận xét submit riêng lẻ có 1 approval_flow
 * riêng, submit theo lô chia sẻ 1 batchId.
 *
 * <p><b>Nhận xét Hàng ngày (comment_type=DAILY) — bổ sung ngoài SDD gốc:</b>
 * quyết định 2026-07-24 (bỏ hẳn bước Nháp, ghi xong tự động chuyển
 * PENDING/APPROVED ngay) đã bị THAY THẾ bởi quyết định 2026-07-29 sau khi
 * dùng thực tế thấy thiếu bước xem lại trước khi gửi duyệt:</p>
 * <ul>
 *   <li>DAILY nay dùng chung 100% luồng DRAFT→submit (UC-21 Main Flow bước
 *       4, {@code submitComments})→PENDING→duyệt (UC-22) với MID_TERM/
 *       END_TERM — {@code writeComment}/{@code updateComment}/
 *       {@code importComments} (Excel) chỉ tạo/sửa ở trạng thái DRAFT,
 *       không còn tự động route trạng thái nào. Actor có
 *       {@code academic.comment.approve} không còn được ghi/sửa thẳng ra
 *       APPROVED bỏ qua chờ duyệt nữa — muốn Gửi phải qua đúng
 *       {@code submitComments()}, vốn luôn yêu cầu actor là GV được phân
 *       công lớp ({@code requireAssignedTeacher}, không đổi) — Quản lý
 *       điểm trường không kiêm GV lớp đó tự viết 1 nhận xét DAILY thì
 *       không tự Gửi được, phải nhờ đúng GV lớp Gửi (đánh đổi đã xác nhận
 *       với người dùng, giữ code đơn giản/đồng nhất với MID_TERM/END_TERM
 *       thay vì mở lại rào riêng cho DAILY).</li>
 *   <li>Excel import (importRow): dòng ứng với nhận xét đang DRAFT/REJECTED
 *       thì sửa được (về lại DRAFT); dòng ứng với nhận xét đã PENDING/
 *       APPROVED thì báo lỗi riêng dòng đó (không chặn dòng khác, đúng
 *       pattern UC-35/50/51/53) — không cho Excel âm thầm ghi đè, bỏ qua
 *       quy trình duyệt.</li>
 *   <li>Hạn ghi/sửa: mặc định 7 ngày kể từ NGÀY BUỔI HỌC diễn ra
 *       (system_settings.academic.comment_edit_window_days — xem
 *       AcademicSettingsService), actor có {@code academic.comment.approve}
 *       bỏ qua hạn này khi ghi/sửa — KHÔNG đổi từ 2026-07-24, đây là quyền
 *       quản trị độc lập với chuyện route trạng thái ở trên.</li>
 *   <li>Excel round-trip theo buổi học (buildTemplate/importComments) —
 *       điền sẵn học sinh ACTIVE của lớp, cột Điểm danh cho phép sửa luôn
 *       điểm danh khi import lại (tái dùng nguyên StudentAttendanceService.
 *       markAttendance, không viết lại logic điểm danh).</li>
 *   <li><b>V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 *       2026-07-30) — điểm giao bài duy nhất:</b> chọn 1 Exercise/
 *       ReviewVideoSet làm "BTVN buổi sau" khi viết/sửa comment DAILY
 *       (writeComment/updateComment/importRow) không còn là chọn lại 1
 *       bản đã giao sẵn (V55) — giờ TỰ ĐỘNG tạo bản giao
 *       ({@code ExerciseAssignment}/{@code ReviewVideoAssignment}) cho
 *       TOÀN BỘ học sinh ACTIVE của lớp, hạn nộp = buổi học kế tiếp
 *       ({@code resolveNextSessionDueAt}). Mọi comment DAILY cùng 1 buổi
 *       học phải chọn CÙNG 1 lựa chọn mỗi kênh
 *       ({@code requireNoHomeworkConflict}, 409 nếu khác); sửa lựa chọn
 *       khi còn DRAFT hủy bản cũ + tạo bản mới ngay; comment bị từ chối
 *       (REJECTED, UC-22) KHÔNG ảnh hưởng bài đã giao (2 việc độc lập,
 *       không đổi). CHỈ áp dụng commentType=DAILY — MID_TERM/END_TERM
 *       không có "buổi kế tiếp" nên không cho chọn (xem
 *       requireNoHomeworkNextForNonDaily). "Soạn & Giao đề" (UC-40) và
 *       "Kho Video Ôn tập" (UC-23) không còn tự giao lớp — xem Javadoc
 *       ExerciseService/ReviewVideoService.</li>
 * </ul>
 */
@Service
public class StudentCommentService {

    private static final int COLUMN_COUNT = 12;
    private static final int COL_DATE = 0;
    private static final int COL_STUDENT_CODE = 1;
    private static final int COL_FULL_NAME = 2;
    /** "Bài học hôm nay" — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29 (chuyển từ Điểm danh sang Nhận xét). */
    private static final int COL_LESSON_CONTENT = 3;
    private static final int COL_ATTENDANCE = 4;
    private static final int COL_ATTITUDE = 5;
    private static final int COL_HOMEWORK_GRAMMAR_PREVIOUS = 6;
    private static final int COL_HOMEWORK_SPEAKING_PREVIOUS = 7;
    private static final int COL_CONTENT = 8;
    private static final int COL_HOMEWORK_GRAMMAR_NEXT = 9;
    private static final int COL_HOMEWORK_VIDEO_NEXT = 10;
    private static final int COL_NOTE = 11;

    private final StudentCommentRepository studentCommentRepository;
    private final StudentCommentHistoryRepository studentCommentHistoryRepository;
    private final ApprovalFlowRepository approvalFlowRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final StudentRepository studentRepository;
    private final ClassSessionRepository classSessionRepository;
    private final GradePeriodRepository gradePeriodRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final SiteManagerRepository siteManagerRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final PermissionEvaluationService permissionEvaluationService;
    private final AcademicSettingsService academicSettingsService;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AttendanceMarkRepository attendanceMarkRepository;
    private final ImportJobRepository importJobRepository;
    private final StudentAttendanceService studentAttendanceService;
    private final ReviewVideoSetRepository reviewVideoSetRepository;
    private final HomeworkProgressService homeworkProgressService;
    private final ExerciseRepository exerciseRepository;
    private final ReviewVideoAssignmentRepository reviewVideoAssignmentRepository;
    private final ExerciseService exerciseService;
    private final ReviewVideoService reviewVideoService;

    public StudentCommentService(StudentCommentRepository studentCommentRepository,
                                  StudentCommentHistoryRepository studentCommentHistoryRepository,
                                  ApprovalFlowRepository approvalFlowRepository,
                                  SchoolClassRepository schoolClassRepository,
                                  StudentRepository studentRepository,
                                  ClassSessionRepository classSessionRepository,
                                  GradePeriodRepository gradePeriodRepository,
                                  ClassTeacherRepository classTeacherRepository,
                                  SiteManagerRepository siteManagerRepository,
                                  UserRepository userRepository,
                                  NotificationService notificationService,
                                  PermissionEvaluationService permissionEvaluationService,
                                  AcademicSettingsService academicSettingsService,
                                  ClassEnrollmentRepository classEnrollmentRepository,
                                  AttendanceSessionRepository attendanceSessionRepository,
                                  AttendanceMarkRepository attendanceMarkRepository,
                                  ImportJobRepository importJobRepository,
                                  StudentAttendanceService studentAttendanceService,
                                  ReviewVideoSetRepository reviewVideoSetRepository,
                                  HomeworkProgressService homeworkProgressService,
                                  ExerciseRepository exerciseRepository,
                                  ReviewVideoAssignmentRepository reviewVideoAssignmentRepository,
                                  ExerciseService exerciseService,
                                  ReviewVideoService reviewVideoService) {
        this.studentCommentRepository = studentCommentRepository;
        this.studentCommentHistoryRepository = studentCommentHistoryRepository;
        this.approvalFlowRepository = approvalFlowRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.studentRepository = studentRepository;
        this.classSessionRepository = classSessionRepository;
        this.gradePeriodRepository = gradePeriodRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.siteManagerRepository = siteManagerRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.permissionEvaluationService = permissionEvaluationService;
        this.academicSettingsService = academicSettingsService;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.attendanceMarkRepository = attendanceMarkRepository;
        this.importJobRepository = importJobRepository;
        this.studentAttendanceService = studentAttendanceService;
        this.reviewVideoSetRepository = reviewVideoSetRepository;
        this.homeworkProgressService = homeworkProgressService;
        this.exerciseRepository = exerciseRepository;
        this.reviewVideoAssignmentRepository = reviewVideoAssignmentRepository;
        this.exerciseService = exerciseService;
        this.reviewVideoService = reviewVideoService;
    }

    // ===================== UC-21: Viết nhận xét (TEACHER) =====================

    /** Main Flow bước 1-3: lưu nháp DRAFT — dùng chung cho cả DAILY/MID_TERM/END_TERM (xem Javadoc lớp). */
    @Transactional
    public StudentCommentResponse writeComment(Long classId, CreateStudentCommentRequest request, Long actorUserId) {
        SchoolClass schoolClass = getClassOrThrow(classId);
        User actor = getUserOrThrow(actorUserId);
        StudentComment.CommentType commentType = StudentComment.CommentType.valueOf(request.commentType());

        ClassSession classSession = null;
        GradePeriod gradePeriod = null;
        if (commentType == StudentComment.CommentType.DAILY) {
            if (request.classSessionId() == null || request.gradePeriodId() != null) {
                throw new InvalidCommentContextException(
                        "commentType=DAILY phải có classSessionId và không được có gradePeriodId.");
            }
            classSession = getClassSessionOrThrow(request.classSessionId());
            requireCanWriteDailyComment(classSession, actorUserId);
        } else {
            if (request.gradePeriodId() == null || request.classSessionId() != null) {
                throw new InvalidCommentContextException(
                        "commentType=" + commentType + " phải có gradePeriodId và không được có classSessionId.");
            }
            requireNoHomeworkNextForNonDaily(request.homeworkNextExerciseId(), request.homeworkNextReviewVideoSetId());
            gradePeriod = gradePeriodRepository.findById(request.gradePeriodId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ đánh giá id=" + request.gradePeriodId()));
            requireAssignedTeacher(classId, actorUserId);
        }
        Student student = studentRepository.findByIdAndDeletedAtIsNull(request.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh id=" + request.studentId()));

        StudentComment comment = new StudentComment();
        comment.setStudent(student);
        comment.setSchoolClass(schoolClass);
        comment.setTeacher(actor);
        comment.setCommentType(commentType);
        comment.setClassSession(classSession);
        comment.setGradePeriod(gradePeriod);
        comment.setCommentDate(request.commentDate());

        ExerciseAssignment grammarAssignment = classSession == null ? null
                : resolveExerciseHomework(classSession, null, request.homeworkNextExerciseId(), null, actorUserId);
        ReviewVideoAssignment videoAssignment = classSession == null ? null
                : resolveVideoHomework(classSession, null, request.homeworkNextReviewVideoSetId(), null, actorUserId);
        applyContent(comment, request.content(), request.structuredContent(), request.severity(), request.isWarning(),
                request.attitude(), request.homeworkPreviousScore(), request.homeworkPreviousSpeakingScore(),
                request.homeworkNext(), grammarAssignment, videoAssignment, request.note());
        comment = studentCommentRepository.save(comment);
        writeHistory(comment, actor, StudentCommentHistory.Action.CREATED);
        return toResponse(comment);
    }

    /**
     * Main Flow bước 2, A1: sửa nội dung khi đang DRAFT hoặc sau khi bị
     * REJECTED (quay lại DRAFT để submit lại) — dùng chung cho cả 3 biểu
     * mẫu (DAILY/MID_TERM/END_TERM, xem Javadoc lớp). DAILY khác ở chỗ
     * rào ghi/sửa dùng requireCanWriteDailyComment (hạn X ngày) thay vì
     * requireAssignedTeacher thuần.
     */
    @Transactional
    public StudentCommentResponse updateComment(Long id, UpdateStudentCommentRequest request, Long actorUserId) {
        StudentComment comment = getCommentOrThrow(id);
        User actor = getUserOrThrow(actorUserId);
        boolean isDaily = comment.getCommentType() == StudentComment.CommentType.DAILY;

        if (isDaily) {
            requireCanWriteDailyComment(comment.getClassSession(), actorUserId);
        } else {
            requireNoHomeworkNextForNonDaily(request.homeworkNextExerciseId(), request.homeworkNextReviewVideoSetId());
            requireAssignedTeacher(comment.getSchoolClass().getId(), actorUserId);
        }
        if (comment.getStatus() != StudentComment.Status.DRAFT && comment.getStatus() != StudentComment.Status.REJECTED) {
            throw new StudentCommentNotEditableException(
                    "Nhận xét id=" + id + " đang ở trạng thái " + comment.getStatus() + " — chỉ sửa được khi DRAFT hoặc REJECTED.");
        }

        ExerciseAssignment grammarAssignment = isDaily
                ? resolveExerciseHomework(comment.getClassSession(), comment.getId(), request.homeworkNextExerciseId(),
                        comment.getHomeworkNextExerciseAssignment(), actorUserId)
                : null;
        ReviewVideoAssignment videoAssignment = isDaily
                ? resolveVideoHomework(comment.getClassSession(), comment.getId(), request.homeworkNextReviewVideoSetId(),
                        comment.getHomeworkNextReviewVideoAssignment(), actorUserId)
                : null;
        comment.setApprovalFlow(null);
        applyContent(comment, request.content(), request.structuredContent(), request.severity(), request.isWarning(),
                request.attitude(), request.homeworkPreviousScore(), request.homeworkPreviousSpeakingScore(),
                request.homeworkNext(), grammarAssignment, videoAssignment, request.note());
        comment.setStatus(StudentComment.Status.DRAFT);
        comment = studentCommentRepository.save(comment);
        writeHistory(comment, actor, StudentCommentHistory.Action.UPDATED);
        return toResponse(comment);
    }

    @Transactional(readOnly = true)
    public List<StudentCommentResponse> listComments(Long classId, Long studentId) {
        return studentCommentRepository.findBySchoolClassIdAndStudentIdOrderByCommentDateDesc(classId, studentId)
                .stream().map(this::toResponse).toList();
    }

    /**
     * UC-64 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29):
     * học sinh tự xem nhận xét đã duyệt (APPROVED — UC-22) của chính mình
     * theo lớp đang/đã ghi danh — mirror ParentPortalService.listComments,
     * chỉ khác scope là chính học sinh thay vì quan hệ phụ huynh-con.
     */
    @Transactional(readOnly = true)
    public List<StudentCommentResponse> listMyComments(Long classId, Long actorUserId) {
        Student student = studentOrThrow(actorUserId);
        requireEnrolled(student.getId(), classId);
        return studentCommentRepository
                .findBySchoolClassIdAndStudentIdAndStatusOrderByCommentDateDesc(classId, student.getId(), StudentComment.Status.APPROVED)
                .stream().map(this::toResponse).toList();
    }

    private Student studentOrThrow(Long actorUserId) {
        return studentRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản id=" + actorUserId + " không có hồ sơ học sinh."));
    }

    /** Đã TỪNG ghi danh lớp này (kể cả đã chuyển lớp) — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29. */
    private void requireEnrolled(Long studentId, Long classId) {
        if (!classEnrollmentRepository.existsByStudentIdAndSchoolClassId(studentId, classId)) {
            throw new ResourceNotFoundException("Không tìm thấy lớp học id=" + classId);
        }
    }

    /** Main Flow bước 4-5: Gửi (submit) — dùng chung cho cả DAILY/MID_TERM/END_TERM (xem Javadoc lớp). */
    @Transactional
    public List<StudentCommentResponse> submitComments(Long classId, SubmitCommentsRequest request, Long actorUserId) {
        requireAssignedTeacher(classId, actorUserId);
        User actor = getUserOrThrow(actorUserId);
        List<StudentComment> comments = studentCommentRepository.findAllById(request.commentIds());
        if (comments.size() != request.commentIds().size()) {
            throw new ResourceNotFoundException("Có nhận xét không tồn tại trong danh sách commentIds.");
        }
        UUID batchId = comments.size() > 1 ? UUID.randomUUID() : null;
        OffsetDateTime now = OffsetDateTime.now();

        for (StudentComment comment : comments) {
            if (!comment.getSchoolClass().getId().equals(classId)) {
                throw new ResourceNotFoundException("Nhận xét id=" + comment.getId() + " không thuộc lớp id=" + classId);
            }
            if (comment.getStatus() != StudentComment.Status.DRAFT) {
                throw new StudentCommentNotEditableException(
                        "Nhận xét id=" + comment.getId() + " đang ở trạng thái " + comment.getStatus() + " — chỉ submit được khi DRAFT.");
            }
            if (comment.getCommentType() == StudentComment.CommentType.DAILY
                    && (comment.getClassSession().getLessonContent() == null || comment.getClassSession().getLessonContent().isBlank())) {
                throw new MissingLessonContentException("Buổi học id=" + comment.getClassSession().getId()
                        + " chưa điền bài học hôm nay — không thể gửi duyệt.");
            }
            ApprovalFlow flow = new ApprovalFlow();
            flow.setEntityType(ApprovalFlow.EntityType.STUDENT_COMMENT);
            flow.setEntityId(comment.getId());
            flow.setStatus(ApprovalFlow.Status.PENDING);
            flow.setSubmittedBy(actor);
            flow.setBatchId(batchId);
            flow = approvalFlowRepository.save(flow);
            comment.setApprovalFlow(flow);
            comment.setStatus(StudentComment.Status.PENDING);
            comment.setSubmittedAt(now);
        }
        List<StudentComment> saved = studentCommentRepository.saveAll(comments);
        saved.forEach(c -> writeHistory(c, actor, StudentCommentHistory.Action.UPDATED));
        notifySiteManagersPending(saved);
        return saved.stream().map(this::toResponse).toList();
    }

    // ===================== UC-22: Duyệt nhận xét (SITE_MANAGER) =====================

    /** Main Flow bước 1: danh sách nhận xét Chờ duyệt của các điểm trường actor phụ trách. */
    @Transactional(readOnly = true)
    public List<StudentCommentResponse> listPendingForSite(Long actorUserId) {
        List<Long> siteIds = siteManagerRepository
                .findByUserIdAndRoleTypeAndAssignedToIsNull(actorUserId, SiteManager.RoleType.SITE_MANAGER).stream()
                .map(sm -> sm.getSite().getId()).toList();
        return siteIds.stream()
                .flatMap(siteId -> studentCommentRepository.findByStatusAndSiteId(StudentComment.Status.PENDING, siteId).stream())
                .map(this::toResponse)
                .toList();
    }

    /**
     * Main Flow bước 2-5, A1 (duyệt theo lô — truyền nhiều id cùng lúc):
     * APPROVED → công khai cho Phụ huynh (visible_to_parent_at); REJECTED →
     * trả về Giáo viên sửa (UC-21 A1), kèm thông báo.
     */
    @Transactional
    public List<StudentCommentResponse> decideComments(DecideCommentsRequest request, Long actorUserId) {
        User actor = getUserOrThrow(actorUserId);
        ApprovalFlow.Decision decision = ApprovalFlow.Decision.valueOf(request.decision());
        List<StudentComment> comments = studentCommentRepository.findAllById(request.commentIds());
        if (comments.size() != request.commentIds().size()) {
            throw new ResourceNotFoundException("Có nhận xét không tồn tại trong danh sách commentIds.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        for (StudentComment comment : comments) {
            requireSiteManagerForSite(comment.getSchoolClass().getSite().getId(), actorUserId);
            if (comment.getStatus() != StudentComment.Status.PENDING) {
                throw new ApprovalAlreadyDecidedException(
                        "Nhận xét id=" + comment.getId() + " đã được quyết định (" + comment.getStatus() + ").");
            }
            ApprovalFlow flow = comment.getApprovalFlow();
            flow.setDecision(decision);
            flow.setApprover(actor);
            flow.setComment(request.comment());
            flow.setDecidedAt(now);

            if (decision == ApprovalFlow.Decision.APPROVED) {
                flow.setStatus(ApprovalFlow.Status.APPROVED);
                comment.setStatus(StudentComment.Status.APPROVED);
                comment.setApprovedBy(actor);
                comment.setApprovedAt(now);
                comment.setVisibleToParentAt(now);
            } else {
                flow.setStatus(ApprovalFlow.Status.REJECTED);
                comment.setStatus(StudentComment.Status.REJECTED);
                comment.setRejectionReason(request.comment());
            }
        }
        List<StudentComment> saved = studentCommentRepository.saveAll(comments);
        saved.forEach(c -> writeHistory(c, actor, StudentCommentHistory.Action.UPDATED));
        if (decision == ApprovalFlow.Decision.REJECTED) {
            saved.forEach(this::notifyTeacherRejected);
        }
        return saved.stream().map(this::toResponse).toList();
    }

    // ===================== Nhận xét Hàng ngày kiểu mới — Excel round-trip =====================

    /**
     * "Bài học hôm nay" — chuyển từ Điểm danh sang Nhận xét (bổ sung ngoài
     * SDD gốc, đã xác nhận với người dùng 2026-07-29). Dùng chung rào
     * requireCanWriteDailyComment (GV được phân công + hạn X ngày, bỏ qua
     * nếu có academic.comment.approve) thay vì rào điểm danh cũ.
     */
    @Transactional
    public ClassSessionLessonContentResponse updateLessonContent(Long classSessionId, String lessonContent, Long actorUserId) {
        ClassSession classSession = getClassSessionOrThrow(classSessionId);
        requireCanWriteDailyComment(classSession, actorUserId);
        classSession.setLessonContent(lessonContent);
        classSession = classSessionRepository.save(classSession);
        return new ClassSessionLessonContentResponse(classSession.getId(), classSession.getLessonContent());
    }

    /**
     * File mẫu để nhận xét theo buổi (bổ sung ngoài SDD gốc, đã xác nhận
     * với người dùng 2026-07-24) — điền sẵn học sinh ACTIVE của lớp, ngày
     * buổi học, điểm danh hiện có (nếu đã điểm danh) và nội dung nhận xét
     * đã nhập trước đó (nếu có, để sửa lại). Cùng quyền/hạn với ghi nhận
     * xét (xem requireCanWriteDailyComment).
     */
    @Transactional(readOnly = true)
    public byte[] buildTemplate(Long classSessionId, Long actorUserId) {
        ClassSession classSession = getClassSessionOrThrow(classSessionId);
        requireCanWriteDailyComment(classSession, actorUserId);
        Long classId = classSession.getSchoolClass().getId();

        Map<Long, AttendanceMark.Status> attendanceByStudent = currentAttendanceByStudent(classSessionId);
        List<ClassEnrollment> enrollments = classEnrollmentRepository
                .findBySchoolClassIdAndStatus(classId, ClassEnrollment.Status.ACTIVE);
        Long curriculumId = classSession.getSchoolClass().getCurriculum().getId();
        List<Exercise> grammarOptions = exerciseRepository.findByCurriculumIdAndExerciseTypeAndStatus(
                curriculumId, Exercise.ExerciseType.ASSIGNED, Exercise.Status.PUBLISHED);
        List<ReviewVideoSet> videoOptions = reviewVideoSetRepository.findVisibleForClass(
                classId, curriculumId, ReviewVideoSet.Status.PUBLISHED);

        List<String> headers = List.of("Ngày*", "Mã học viên*", "Họ và tên", "Bài học hôm nay", "Điểm danh*",
                "Thái độ học tập", "BTVN Ngữ pháp buổi trước", "BTVN Nghe-nói buổi trước",
                "Nhận xét học sinh*", "BTVN Ngữ pháp buổi sau", "BTVN Nghe-nói buổi sau", "Ghi chú");
        List<List<Object>> rows = new ArrayList<>();
        for (ClassEnrollment enrollment : enrollments) {
            Student student = enrollment.getStudent();
            StudentComment existing = studentCommentRepository
                    .findByClassSessionIdAndStudentId(classSessionId, student.getId()).orElse(null);
            AttendanceMark.Status attendance = attendanceByStudent.get(student.getId());
            StudentComment previous = previousComment(classSession, student.getId());

            List<Object> row = new ArrayList<>();
            row.add(classSession.getSessionDate().toString());
            row.add(student.getStudentCode());
            row.add(student.getUser().getFullName());
            row.add(classSession.getLessonContent());
            row.add(attendance == null ? null : attendanceLabel(attendance));
            row.add(existing == null || existing.getAttitude() == null ? null : attitudeLabel(existing.getAttitude()));
            row.add(resolvedGrammarPrevious(existing, previous));
            row.add(resolvedSpeakingPrevious(existing, previous));
            row.add(existing == null ? null : existing.getContent());
            row.add(resolvedGrammarNext(existing));
            row.add(existing == null || existing.getHomeworkNextReviewVideoAssignment() == null ? null
                    : videoLabel(existing.getHomeworkNextReviewVideoAssignment().getReviewVideoSet()));
            row.add(existing == null ? null : existing.getNote());
            rows.add(row);
        }
        Map<Integer, List<String>> dropdowns = new LinkedHashMap<>();
        dropdowns.put(COL_ATTENDANCE, Arrays.stream(AttendanceMark.Status.values()).map(this::attendanceLabel).toList());
        dropdowns.put(COL_ATTITUDE, Arrays.stream(StudentComment.Attitude.values()).map(this::attitudeLabel).toList());
        dropdowns.put(COL_HOMEWORK_GRAMMAR_NEXT, grammarOptions.stream().map(this::grammarLabel).toList());
        dropdowns.put(COL_HOMEWORK_VIDEO_NEXT, videoOptions.stream().map(this::videoLabel).toList());
        return ExcelExportHelper.buildWorkbook("Nhận xét", headers, rows, null, dropdowns);
    }

    /** Ghi đè tay thắng — chỉ fallback về % tự động khi chưa có giá trị nhập tay. */
    private String resolvedGrammarPrevious(StudentComment existing, StudentComment previous) {
        if (existing != null && existing.getHomeworkPreviousScore() != null) {
            return existing.getHomeworkPreviousScore();
        }
        return grammarPreviousProgressLabel(previous);
    }

    /** Ghi đè tay thắng — chỉ fallback về % tự động khi chưa có giá trị nhập tay. */
    private String resolvedSpeakingPrevious(StudentComment existing, StudentComment previous) {
        if (existing != null && existing.getHomeworkPreviousSpeakingScore() != null) {
            return existing.getHomeworkPreviousSpeakingScore();
        }
        return videoPreviousProgressLabel(previous);
    }

    /** Đã giao online thì hiện nhãn đề; không thì hiện text offline (homeworkNext) cũ. */
    private String resolvedGrammarNext(StudentComment existing) {
        if (existing == null) {
            return null;
        }
        if (existing.getHomeworkNextExerciseAssignment() != null) {
            return grammarLabel(existing.getHomeworkNextExerciseAssignment().getExercise());
        }
        return existing.getHomeworkNext();
    }

    /**
     * Nhập nhận xét theo buổi qua Excel (bổ sung ngoài SDD gốc, đã xác
     * nhận với người dùng 2026-07-24). Cột Điểm danh cho phép sửa luôn
     * điểm danh (gom các dòng thay đổi thành 1 lần gọi
     * StudentAttendanceService.markAttendance — tái dùng nguyên UC-15,
     * không viết lại). Học sinh Vắng/Có phép mà các cột sau đều trống thì
     * bỏ qua, không tạo nhận xét. Lỗi 1 dòng không chặn dòng khác.
     */
    @Transactional
    public DailyCommentImportResponse importComments(Long classSessionId, MultipartFile file, Long actorUserId) {
        ClassSession classSession = getClassSessionOrThrow(classSessionId);
        requireCanWriteDailyComment(classSession, actorUserId);
        User actor = getUserOrThrow(actorUserId);

        ImportJob job = new ImportJob();
        job.setImportType(ImportJob.ImportType.DAILY_COMMENTS);
        job.setSourceFileName(file.getOriginalFilename() == null ? "unnamed.xlsx" : file.getOriginalFilename());
        job.setSourceFileUrl("in-memory://" + job.getSourceFileName());
        job.setUploadedBy(actor);
        job.setStatus(ImportJob.Status.PROCESSING);
        job.setStartedAt(OffsetDateTime.now());
        job = importJobRepository.save(job);

        Long classId = classSession.getSchoolClass().getId();
        Long curriculumId = classSession.getSchoolClass().getCurriculum().getId();
        Map<String, Exercise> grammarByLabel = exerciseRepository
                .findByCurriculumIdAndExerciseTypeAndStatus(curriculumId, Exercise.ExerciseType.ASSIGNED, Exercise.Status.PUBLISHED).stream()
                .collect(java.util.stream.Collectors.toMap(this::grammarLabel, e -> e, (a, b) -> a));
        Map<String, ReviewVideoSet> videoByLabel = reviewVideoSetRepository.findVisibleForClass(
                classId, curriculumId, ReviewVideoSet.Status.PUBLISHED).stream()
                .collect(java.util.stream.Collectors.toMap(this::videoLabel, s -> s, (a, b) -> a));

        List<Map<String, Object>> errors = new ArrayList<>();
        try (InputStream inputStream = file.getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getRow(0) == null) {
                return failJob(job, "File rỗng hoặc thiếu dòng tiêu đề.");
            }
            DataFormatter formatter = new DataFormatter();

            List<ParsedRow> parsedRows = new ArrayList<>();
            int totalRows = 0;
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row, formatter)) {
                    continue;
                }
                totalRows++;
                try {
                    parsedRows.add(parseRow(row, formatter, rowIndex, classSession, grammarByLabel, videoByLabel));
                } catch (RuntimeException ex) {
                    errors.add(rowError(rowIndex + 1, ex.getMessage()));
                }
            }

            // "Bài học hôm nay" dùng CHUNG cả buổi (không phải theo từng học sinh) — mọi dòng có
            // điền phải khớp giá trị nhau, dòng để trống bỏ qua (case 3: chưa điền cả UI lẫn Excel,
            // validate ở submitComments). Khác 0/khác nhau → chặn TOÀN BỘ file, không import dòng
            // nào (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29).
            Set<String> lessonContentValues = parsedRows.stream()
                    .map(ParsedRow::lessonContent).filter(v -> v != null && !v.isBlank())
                    .collect(java.util.stream.Collectors.toSet());
            if (lessonContentValues.size() > 1) {
                return failJob(job, "Bài học hôm nay không đồng nhất giữa các học sinh trong lớp — mọi học sinh phải học cùng 1 bài.");
            }
            if (lessonContentValues.size() == 1) {
                classSession.setLessonContent(lessonContentValues.iterator().next());
                classSessionRepository.save(classSession);
            }

            // Gom các dòng có điểm danh KHÁC giá trị hiện có thành 1 lần gọi markAttendance
            // duy nhất — tái dùng nguyên rào UC-15 (chỉ trong ngày diễn ra buổi học, trừ khi
            // actor có quyền quản trị điểm danh). Rào này KHÁC hạn 7 ngày của nhận xét — không đổi.
            //
            // Kiểm tra quyền TRƯỚC (canWriteAttendance, không throw) thay vì gọi thẳng
            // markAttendance() rồi bắt exception — markAttendance() là bean @Transactional
            // KHÁC, exception xuyên ranh giới đó đánh dấu transaction NGOÀI (importComments)
            // rollback-only ngay tại proxy dù có catch, khiến commit sau đó ném
            // UnexpectedRollbackException (đã phát hiện qua verify curl thật).
            Map<Long, AttendanceMark.Status> attendanceBeforeImport = currentAttendanceByStudent(classSessionId);
            List<ParsedRow> attendanceChanged = parsedRows.stream()
                    .filter(r -> attendanceBeforeImport.get(r.student().getId()) != r.attendance())
                    .toList();
            boolean attendanceWriteFailed = false;
            String attendanceWriteFailedReason = null;
            if (!attendanceChanged.isEmpty()) {
                if (studentAttendanceService.canWriteAttendance(classSessionId, actorUserId)) {
                    List<EnterAttendanceMarkRequest> marks = attendanceChanged.stream()
                            .map(r -> new EnterAttendanceMarkRequest(r.student().getId(), r.attendance().name(), null, null, null))
                            .toList();
                    studentAttendanceService.markAttendance(classSessionId,
                            new MarkAttendanceRequest("SESSION_LEVEL", marks), actorUserId);
                } else {
                    attendanceWriteFailed = true;
                    attendanceWriteFailedReason = "chỉ điểm danh/sửa được trong ngày diễn ra buổi học "
                            + "hoặc cần được phân công giảng dạy buổi này (quyền quản trị điểm danh mới vượt được rào này).";
                }
            }
            Map<Long, AttendanceMark.Status> currentAttendance = currentAttendanceByStudent(classSessionId);

            int successRows = 0;
            for (ParsedRow parsed : parsedRows) {
                try {
                    AttendanceMark.Status effectiveAttendance = currentAttendance.getOrDefault(
                            parsed.student().getId(), parsed.attendance());
                    if (attendanceWriteFailed && parsed.attendance() != effectiveAttendance) {
                        throw new IllegalArgumentException(
                                "Không sửa được điểm danh: " + attendanceWriteFailedReason);
                    }
                    importRow(classSession, parsed.student(), effectiveAttendance, parsed.attitude(),
                            parsed.homeworkPrevious(), parsed.content(), parsed.homeworkNext(),
                            parsed.grammarExercise(), parsed.videoSet(), parsed.note(),
                            parsed.homeworkPreviousSpeaking(), actor);
                    successRows++;
                } catch (RuntimeException ex) {
                    errors.add(rowError(parsed.rowNumber() + 1, ex.getMessage()));
                }
            }

            job.setTotalRows(totalRows);
            job.setSuccessRows(successRows);
            job.setFailedRows(errors.size());
            job.setErrorSummary(errors);
            job.setStatus(errors.isEmpty() ? ImportJob.Status.COMPLETED : ImportJob.Status.PARTIAL_SUCCESS);
            job.setFinishedAt(OffsetDateTime.now());
            job = importJobRepository.save(job);
            return toImportResponse(job);
        } catch (IOException | RuntimeException ex) {
            return failJob(job, "File sai định dạng Excel (.xlsx): " + ex.getMessage());
        }
    }

    private record ParsedRow(int rowNumber, Student student, AttendanceMark.Status attendance, String attitude,
                              String homeworkPrevious, String content, String homeworkNext,
                              Exercise grammarExercise, ReviewVideoSet videoSet, String note,
                              String homeworkPreviousSpeaking, String lessonContent) {}

    private ParsedRow parseRow(Row row, DataFormatter formatter, int rowIndex, ClassSession classSession,
                                Map<String, Exercise> grammarByLabel, Map<String, ReviewVideoSet> videoByLabel) {
        String dateText = cell(row, formatter, COL_DATE);
        String studentCode = cell(row, formatter, COL_STUDENT_CODE);
        String lessonContentText = cell(row, formatter, COL_LESSON_CONTENT);
        String attendanceText = cell(row, formatter, COL_ATTENDANCE);
        String attitudeText = cell(row, formatter, COL_ATTITUDE);
        String homeworkPrevious = cell(row, formatter, COL_HOMEWORK_GRAMMAR_PREVIOUS);
        String content = cell(row, formatter, COL_CONTENT);
        String grammarNextText = cell(row, formatter, COL_HOMEWORK_GRAMMAR_NEXT);
        String videoText = cell(row, formatter, COL_HOMEWORK_VIDEO_NEXT);
        String note = cell(row, formatter, COL_NOTE);
        String homeworkPreviousSpeaking = cell(row, formatter, COL_HOMEWORK_SPEAKING_PREVIOUS);

        if (dateText == null || dateText.isBlank()) {
            throw new IllegalArgumentException("Thiếu ngày (cột A).");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(dateText.trim());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Ngày sai định dạng (cần yyyy-MM-dd): " + dateText);
        }
        if (!date.equals(classSession.getSessionDate())) {
            throw new IllegalArgumentException(
                    "Ngày (" + date + ") không khớp ngày buổi học (" + classSession.getSessionDate() + ").");
        }
        if (studentCode == null || studentCode.isBlank()) {
            throw new IllegalArgumentException("Thiếu mã học viên (cột B).");
        }
        Student student = studentRepository.findByStudentCode(studentCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy học sinh mã=" + studentCode));
        if (attendanceText == null || attendanceText.isBlank()) {
            throw new IllegalArgumentException("Thiếu điểm danh (cột E).");
        }
        AttendanceMark.Status attendance = parseAttendanceStatus(attendanceText.trim());

        String attitude = attitudeText == null || attitudeText.isBlank() ? null : parseAttitude(attitudeText.trim()).name();
        // Cột gộp "BTVN Ngữ pháp buổi sau": khớp đề trong kho (nhãn dropdown hoặc uuid) thì giao ONLINE,
        // không khớp thì coi là text offline — KHÔNG báo lỗi (khác cột Video vẫn báo lỗi nếu không khớp).
        String grammarNextRaw = blankToNull(grammarNextText);
        Exercise grammarExercise = grammarNextRaw == null ? null
                : resolveGrammarExerciseSoft(grammarNextRaw, grammarByLabel);
        String homeworkNext = (grammarNextRaw != null && grammarExercise == null) ? grammarNextRaw : null;
        ReviewVideoSet videoSet = resolveByUuidOrLabel(blankToNull(videoText), videoByLabel,
                reviewVideoSetRepository::findByUuid, "bộ video");
        return new ParsedRow(rowIndex, student, attendance, attitude,
                blankToNull(homeworkPrevious), blankToNull(content), homeworkNext,
                grammarExercise, videoSet, blankToNull(note), blankToNull(homeworkPreviousSpeaking),
                blankToNull(lessonContentText));
    }

    /** Cột "BTVN Ngữ pháp buổi sau" gộp: khớp uuid/nhãn đề thì trả về đề đó, không khớp trả null (KHÔNG throw) để caller fallback text offline. */
    private Exercise resolveGrammarExerciseSoft(String text, Map<String, Exercise> byLabel) {
        UUID uuid = tryParseUuid(text);
        if (uuid != null) {
            return exerciseRepository.findByUuid(uuid).orElse(null);
        }
        return byLabel.get(text);
    }

    /**
     * Ghi 1 dòng: Vắng/Có phép mà mọi cột sau Điểm danh đều trống thì bỏ
     * qua (chỉ ghi điểm danh, không tạo nhận xét). Dòng ứng với 1 nhận
     * xét đã tồn tại thì chỉ sửa được khi đang DRAFT/REJECTED (giống hệt
     * updateComment) — tránh Excel âm thầm ghi đè 1 dòng đã PENDING/
     * APPROVED, bỏ qua quy trình duyệt.
     */
    private void importRow(ClassSession classSession, Student student, AttendanceMark.Status attendance,
                            String attitude, String homeworkPrevious, String content, String homeworkNext,
                            Exercise grammarExercise, ReviewVideoSet videoSet, String note,
                            String homeworkPreviousSpeaking, User actor) {
        boolean absent = attendance == AttendanceMark.Status.ABSENT || attendance == AttendanceMark.Status.EXCUSED;
        boolean allBlank = attitude == null && homeworkPrevious == null && content == null
                && homeworkNext == null && grammarExercise == null && videoSet == null && note == null
                && homeworkPreviousSpeaking == null;
        if (absent && allBlank) {
            return;
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Thiếu nhận xét (cột I) — bắt buộc trừ khi học sinh vắng/có phép.");
        }

        StudentComment comment = studentCommentRepository
                .findByClassSessionIdAndStudentId(classSession.getId(), student.getId())
                .orElseGet(() -> {
                    StudentComment created = new StudentComment();
                    created.setStudent(student);
                    created.setSchoolClass(classSession.getSchoolClass());
                    created.setCommentType(StudentComment.CommentType.DAILY);
                    created.setClassSession(classSession);
                    created.setCommentDate(classSession.getSessionDate());
                    return created;
                });
        if (comment.getStatus() != StudentComment.Status.DRAFT && comment.getStatus() != StudentComment.Status.REJECTED) {
            throw new StudentCommentNotEditableException(
                    "Nhận xét học sinh mã=" + student.getStudentCode() + " đang ở trạng thái "
                            + comment.getStatus() + " — chỉ sửa được khi DRAFT hoặc REJECTED.");
        }
        ExerciseAssignment grammarAssignment = resolveExerciseHomework(classSession, comment.getId(),
                grammarExercise == null ? null : grammarExercise.getId(),
                comment.getHomeworkNextExerciseAssignment(), actor.getId());
        ReviewVideoAssignment videoAssignment = resolveVideoHomework(classSession, comment.getId(),
                videoSet == null ? null : videoSet.getId(),
                comment.getHomeworkNextReviewVideoAssignment(), actor.getId());
        comment.setTeacher(actor);
        comment.setApprovalFlow(null);
        applyContent(comment, content, null, null, false,
                attitude, homeworkPrevious, homeworkPreviousSpeaking, homeworkNext, grammarAssignment, videoAssignment, note);
        comment.setStatus(StudentComment.Status.DRAFT);
        comment = studentCommentRepository.save(comment);
        writeHistory(comment, actor, StudentCommentHistory.Action.UPDATED);
    }

    // ===================== Helpers =====================

    private void applyContent(StudentComment comment, String content, Map<String, Object> structuredContent,
                               String severity, boolean isWarning, String attitude, String homeworkPreviousScore,
                               String homeworkPreviousSpeakingScore, String homeworkNext,
                               ExerciseAssignment homeworkNextExerciseAssignment,
                               ReviewVideoAssignment homeworkNextReviewVideoAssignment, String note) {
        comment.setContent(content);
        comment.setStructuredContent(structuredContent);
        if (severity != null) {
            comment.setSeverity(StudentComment.Severity.valueOf(severity));
        }
        comment.setWarning(isWarning);
        comment.setAttitude(attitude == null ? null : StudentComment.Attitude.valueOf(attitude));
        comment.setHomeworkPreviousScore(homeworkPreviousScore);
        comment.setHomeworkPreviousSpeakingScore(homeworkPreviousSpeakingScore);
        comment.setHomeworkNext(homeworkNext);
        comment.setHomeworkNextExerciseAssignment(homeworkNextExerciseAssignment);
        comment.setHomeworkNextReviewVideoAssignment(homeworkNextReviewVideoAssignment);
        comment.setNote(note);
    }

    // ===================== BTVN buổi sau — điểm giao bài (UC-21 mở rộng, V65) =====================
    // Thay thế cơ chế "chọn lại 1 bản đã giao sẵn" (V55) — chọn 1 Exercise/
    // ReviewVideoSet ở đây giờ TỰ ĐỘNG giao cho cả lớp, xem Javadoc lớp.

    private void requireNoHomeworkNextForNonDaily(Long exerciseId, Long videoSetId) {
        if (exerciseId != null || videoSetId != null) {
            throw new InvalidCommentContextException(
                    "BTVN buổi sau (Ngữ pháp/Video Ôn tập) chỉ áp dụng cho nhận xét Hàng ngày (DAILY) — "
                            + "không có khái niệm \"buổi kế tiếp\" cho nhận xét theo kỳ (MID_TERM/END_TERM).");
        }
    }

    /**
     * V65: chọn 1 Exercise làm "BTVN Ngữ pháp buổi sau" — tự động giao đề
     * cho TOÀN BỘ học sinh ACTIVE của lớp (không chỉ học sinh đang được
     * nhận xét), hạn nộp = buổi học kế tiếp. exerciseId=null → hủy bản
     * giao cũ (nếu có), không giao gì. Không đổi so với previous (cùng
     * Exercise) → giữ nguyên, không tạo lại.
     *
     * @param excludeCommentId null khi đang tạo comment mới (writeComment/
     *                         importRow dòng mới) — loại trừ chính dòng
     *                         đang sửa khỏi kiểm tra xung đột cùng buổi.
     */
    private ExerciseAssignment resolveExerciseHomework(ClassSession session, Long excludeCommentId, Long exerciseId,
                                                        ExerciseAssignment previous, Long actorUserId) {
        if (exerciseId == null) {
            if (previous != null) {
                exerciseService.cancelAssignment(previous);
            }
            return null;
        }
        if (previous != null && previous.getExercise().getId().equals(exerciseId)) {
            return previous;
        }
        Exercise exercise = exerciseRepository.findById(exerciseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đề id=" + exerciseId));
        requireNoHomeworkConflict(session, excludeCommentId, "Ngữ pháp",
                c -> c.getHomeworkNextExerciseAssignment() == null ? null : c.getHomeworkNextExerciseAssignment().getExercise().getId(),
                c -> c.getHomeworkNextExerciseAssignment() == null ? null : c.getHomeworkNextExerciseAssignment().getExercise().getTitle(),
                exerciseId, exercise.getTitle());
        OffsetDateTime dueAt = resolveNextSessionDueAt(session);
        ExerciseAssignment assignment = exerciseService.deliverToClass(exerciseId, session.getSchoolClass().getId(), dueAt, actorUserId);
        if (previous != null) {
            exerciseService.cancelAssignment(previous);
        }
        return assignment;
    }

    /** V65: mirror resolveExerciseHomework cho kênh Video Ôn tập — xem Javadoc đó. */
    private ReviewVideoAssignment resolveVideoHomework(ClassSession session, Long excludeCommentId, Long videoSetId,
                                                        ReviewVideoAssignment previous, Long actorUserId) {
        if (videoSetId == null) {
            if (previous != null) {
                reviewVideoService.cancelAssignment(previous);
            }
            return null;
        }
        if (previous != null && previous.getReviewVideoSet().getId().equals(videoSetId)) {
            return previous;
        }
        ReviewVideoSet set = reviewVideoSetRepository.findById(videoSetId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bộ video id=" + videoSetId));
        requireNoHomeworkConflict(session, excludeCommentId, "Video Ôn tập",
                c -> c.getHomeworkNextReviewVideoAssignment() == null ? null : c.getHomeworkNextReviewVideoAssignment().getReviewVideoSet().getId(),
                c -> c.getHomeworkNextReviewVideoAssignment() == null ? null : c.getHomeworkNextReviewVideoAssignment().getReviewVideoSet().getTitle(),
                videoSetId, set.getTitle());
        OffsetDateTime dueAt = resolveNextSessionDueAt(session);
        ReviewVideoAssignment assignment = reviewVideoService.deliverToClass(videoSetId, session.getSchoolClass().getId(), dueAt, actorUserId);
        if (previous != null) {
            reviewVideoService.cancelAssignment(previous);
        }
        return assignment;
    }

    /**
     * Câu hỏi mở #1 (đã chốt với người dùng 2026-07-30): mọi nhận xét
     * DAILY cùng 1 buổi học phải chọn CÙNG 1 lựa chọn cho mỗi kênh —
     * dòng đầu tiên chọn X thì các dòng sau (học sinh khác, cùng buổi)
     * chỉ được chọn đúng X hoặc để trống, không được chọn khác X.
     */
    private void requireNoHomeworkConflict(ClassSession session, Long excludeCommentId, String channelLabel,
                                            Function<StudentComment, Long> existingChoiceId,
                                            Function<StudentComment, String> existingChoiceLabel,
                                            Long newChoiceId, String newChoiceLabel) {
        for (StudentComment sibling : studentCommentRepository.findByClassSessionId(session.getId())) {
            if (excludeCommentId != null && sibling.getId().equals(excludeCommentId)) {
                continue;
            }
            Long siblingChoiceId = existingChoiceId.apply(sibling);
            if (siblingChoiceId != null && !siblingChoiceId.equals(newChoiceId)) {
                throw new HomeworkNextConflictException(
                        "BTVN " + channelLabel + " buổi này đã khóa theo lựa chọn \"" + existingChoiceLabel.apply(sibling)
                                + "\" (chọn cho học sinh " + sibling.getStudent().getUser().getFullName()
                                + ") — không thể đổi sang \"" + newChoiceLabel + "\" cho học sinh khác trong cùng buổi.");
            }
        }
    }

    /**
     * Câu hỏi mở #4 (đã chốt với người dùng 2026-07-30): hạn nộp BTVN
     * buổi sau = ngày/giờ buổi học KẾ TIẾP của lớp (tính từ sessionDate
     * của buổi đang nhận xét, không phải "hôm nay" — GV có thể nhập bù
     * buổi cũ). Lớp chưa có buổi kế tiếp → chặn hẳn, không cho giao.
     */
    private OffsetDateTime resolveNextSessionDueAt(ClassSession session) {
        List<ClassSession> upcoming = classSessionRepository.findUpcomingSessions(
                session.getSchoolClass().getId(), session.getSessionDate(),
                List.of(ClassSession.Status.CANCELLED, ClassSession.Status.RESCHEDULED));
        if (upcoming.isEmpty()) {
            throw new NoUpcomingClassSessionException(
                    "Lớp id=" + session.getSchoolClass().getId() + " chưa có buổi học kế tiếp trong lịch — "
                            + "không thể đặt hạn nộp cho BTVN buổi sau.");
        }
        ClassSession next = upcoming.get(0);
        return next.getSessionDate().atTime(next.getStartTime()).atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    /** Chấp nhận dán uuid (không giới hạn theo lớp) HOẶC chọn đúng nhãn dropdown (giới hạn theo bài đã gán cho lớp). */
    private <T> T resolveByUuidOrLabel(String text, Map<String, T> byLabel, Function<UUID, Optional<T>> byUuid, String kindLabel) {
        if (text == null) {
            return null;
        }
        UUID uuid = tryParseUuid(text);
        if (uuid != null) {
            return byUuid.apply(uuid)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy " + kindLabel + " với uuid=" + text));
        }
        T match = byLabel.get(text);
        if (match == null) {
            throw new IllegalArgumentException(
                    "Không khớp " + kindLabel + " \"" + text + "\" — chọn từ dropdown hoặc dán uuid hợp lệ.");
        }
        return match;
    }

    private UUID tryParseUuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String grammarLabel(Exercise e) {
        return e.getTitle() + " (" + e.getCode() + ")";
    }

    private String videoLabel(ReviewVideoSet s) {
        return s.getTitle() + " (" + s.getCode() + ")";
    }

    /** Dòng nhận xét của CHÍNH học sinh này ở buổi liền TRƯỚC buổi đang xét, cùng lớp — nguồn tra "đã giao gì cho buổi này". */
    private StudentComment previousComment(ClassSession classSession, Long studentId) {
        return classSessionRepository.findFirstBySchoolClassIdAndSessionDateLessThanOrderBySessionDateDescIdDesc(
                        classSession.getSchoolClass().getId(), classSession.getSessionDate())
                .flatMap(prev -> studentCommentRepository.findByClassSessionIdAndStudentId(prev.getId(), studentId))
                .orElse(null);
    }

    /** % bài ngữ pháp online đã giao ở buổi trước — xem HomeworkProgressService.grammarProgressLabel. */
    private String grammarPreviousProgressLabel(StudentComment previous) {
        return previous == null ? null
                : homeworkProgressService.grammarProgressLabel(previous.getHomeworkNextExerciseAssignment(), previous.getStudent().getId());
    }

    /** % video ôn tập đã giao ở buổi trước — xem HomeworkProgressService.videoProgressLabel. */
    private String videoPreviousProgressLabel(StudentComment previous) {
        return previous == null ? null
                : homeworkProgressService.videoProgressLabel(previous.getHomeworkNextReviewVideoAssignment(), previous.getStudent().getId());
    }

    /**
     * Rào ghi/sửa nhận xét DAILY. Actor có academic.comment.approve: bỏ
     * qua rào (không cần là GV được phân công, không giới hạn hạn X ngày
     * — quyền quản trị độc lập với chuyện nhận xét route trạng thái gì,
     * xem Javadoc lớp). Ngược lại: phải là GV được phân công lớp (giữ
     * nguyên rào cũ) VÀ còn trong hạn X ngày kể từ ngày buổi học.
     */
    private void requireCanWriteDailyComment(ClassSession classSession, Long actorUserId) {
        if (permissionEvaluationService.hasPermission(actorUserId, "academic.comment.approve")) {
            return;
        }
        requireAssignedTeacher(classSession.getSchoolClass().getId(), actorUserId);
        int windowDays = academicSettingsService.commentEditWindowDays();
        LocalDate deadline = classSession.getSessionDate().plusDays(windowDays);
        if (LocalDate.now().isAfter(deadline)) {
            throw new StudentCommentNotEditableException(
                    "Chỉ nhập/sửa nhận xét trong vòng " + windowDays + " ngày kể từ ngày buổi học ("
                            + classSession.getSessionDate() + "); hạn đã hết ngày " + deadline + ".");
        }
    }

    private Map<Long, AttendanceMark.Status> currentAttendanceByStudent(Long classSessionId) {
        return attendanceSessionRepository.findByClassSessionId(classSessionId)
                .map(session -> attendanceMarkRepository.findByAttendanceSessionId(session.getId()).stream()
                        .collect(java.util.stream.Collectors.toMap(m -> m.getStudent().getId(), AttendanceMark::getStatus)))
                .orElseGet(Map::of);
    }

    private AttendanceMark.Status parseAttendanceStatus(String text) {
        return switch (text.toLowerCase()) {
            case "có mặt", "co mat", "present" -> AttendanceMark.Status.PRESENT;
            case "vắng", "vang", "absent" -> AttendanceMark.Status.ABSENT;
            case "có phép", "co phep", "excused" -> AttendanceMark.Status.EXCUSED;
            case "muộn", "muon", "late" -> AttendanceMark.Status.LATE;
            case "về sớm", "ve som", "early_leave" -> AttendanceMark.Status.EARLY_LEAVE;
            default -> throw new IllegalArgumentException(
                    "Điểm danh không hợp lệ (cần Có mặt/Vắng/Có phép/Muộn/Về sớm): " + text);
        };
    }

    private String attendanceLabel(AttendanceMark.Status status) {
        return switch (status) {
            case PRESENT -> "Có mặt";
            case ABSENT -> "Vắng";
            case EXCUSED -> "Có phép";
            case LATE -> "Muộn";
            case EARLY_LEAVE -> "Về sớm";
        };
    }

    private StudentComment.Attitude parseAttitude(String text) {
        return switch (text.toLowerCase()) {
            case "kém", "kem", "poor" -> StudentComment.Attitude.POOR;
            case "yếu", "yeu", "weak" -> StudentComment.Attitude.WEAK;
            case "trung bình", "trung binh", "average" -> StudentComment.Attitude.AVERAGE;
            case "trung bình khá", "trung binh kha", "above average" -> StudentComment.Attitude.ABOVE_AVERAGE;
            case "khá", "kha", "fair" -> StudentComment.Attitude.FAIR;
            case "tốt", "tot", "good" -> StudentComment.Attitude.GOOD;
            default -> throw new IllegalArgumentException(
                    "Thái độ học tập không hợp lệ (cần Kém/Yếu/Trung bình/Trung bình khá/Khá/Tốt): " + text);
        };
    }

    private String attitudeLabel(StudentComment.Attitude attitude) {
        return switch (attitude) {
            case POOR -> "Kém";
            case WEAK -> "Yếu";
            case AVERAGE -> "Trung bình";
            case ABOVE_AVERAGE -> "Trung bình khá";
            case FAIR -> "Khá";
            case GOOD -> "Tốt";
        };
    }

    private String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }

    private String cell(Row row, DataFormatter formatter, int index) {
        var cell = row.getCell(index);
        return cell == null ? null : formatter.formatCellValue(cell).trim();
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        for (int i = 0; i < COLUMN_COUNT; i++) {
            String value = cell(row, formatter, i);
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> rowError(int rowNumber, String reason) {
        Map<String, Object> error = new HashMap<>();
        error.put("row", rowNumber);
        error.put("reason", reason);
        return error;
    }

    private DailyCommentImportResponse failJob(ImportJob job, String reason) {
        job.setStatus(ImportJob.Status.FAILED);
        job.setErrorSummary(List.of(rowError(0, reason)));
        job.setFinishedAt(OffsetDateTime.now());
        job = importJobRepository.save(job);
        return toImportResponse(job);
    }

    private DailyCommentImportResponse toImportResponse(ImportJob job) {
        return new DailyCommentImportResponse(job.getId(), job.getSourceFileName(), job.getTotalRows(),
                job.getSuccessRows(), job.getFailedRows(), job.getStatus().name(), job.getErrorSummary());
    }

    private void requireAssignedTeacher(Long classId, Long actorUserId) {
        if (!classTeacherRepository.existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(classId, actorUserId)) {
            throw new NotAssignedTeacherForClassException(
                    "Tài khoản id=" + actorUserId + " không được phân công giảng dạy lớp id=" + classId + ".");
        }
    }

    private void requireSiteManagerForSite(Long siteId, Long actorUserId) {
        if (!siteManagerRepository.existsBySiteIdAndUserIdAndRoleTypeAndAssignedToIsNull(
                siteId, actorUserId, SiteManager.RoleType.SITE_MANAGER)) {
            throw new NotSiteManagerForSiteException(
                    "Tài khoản id=" + actorUserId + " không được gán phụ trách điểm trường id=" + siteId + ".");
        }
    }

    private void notifySiteManagersPending(List<StudentComment> submitted) {
        submitted.stream()
                .collect(java.util.stream.Collectors.groupingBy(c -> c.getSchoolClass().getSite().getId()))
                .forEach((siteId, comments) -> {
                    SchoolClass schoolClass = comments.get(0).getSchoolClass();
                    String title = "Nhận xét học sinh chờ duyệt";
                    String content = "Có %d nhận xét học sinh mới (lớp %s) đang chờ bạn duyệt."
                            .formatted(comments.size(), schoolClass.getName());
                    siteManagerRepository.findBySiteIdAndRoleTypeAndAssignedToIsNull(siteId, SiteManager.RoleType.SITE_MANAGER).forEach(sm ->
                            notificationService.notify(sm.getUser().getId(), Notification.NotificationType.COMMENT_APPROVED, title, content));
                });
    }

    private void notifyTeacherRejected(StudentComment comment) {
        String title = "Nhận xét học sinh bị từ chối";
        String content = "Nhận xét cho học sinh %s (lớp %s, ngày %s) đã bị từ chối%s."
                .formatted(comment.getStudent().getUser().getFullName(), comment.getSchoolClass().getName(),
                        comment.getCommentDate(),
                        comment.getRejectionReason() == null ? "" : ": " + comment.getRejectionReason());
        notificationService.notify(comment.getTeacher().getId(), Notification.NotificationType.COMMENT_APPROVED, title, content);
    }

    private void writeHistory(StudentComment comment, User actor, StudentCommentHistory.Action action) {
        StudentCommentHistory history = new StudentCommentHistory();
        history.setStudentComment(comment);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("studentId", comment.getStudent().getId());
        snapshot.put("commentType", comment.getCommentType().name());
        snapshot.put("severity", comment.getSeverity().name());
        snapshot.put("isWarning", comment.isWarning());
        snapshot.put("status", comment.getStatus().name());
        history.setDetails(snapshot);
        studentCommentHistoryRepository.save(history);
    }

    private StudentComment getCommentOrThrow(Long id) {
        return studentCommentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhận xét id=" + id));
    }

    private SchoolClass getClassOrThrow(Long id) {
        return schoolClassRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học id=" + id));
    }

    private ClassSession getClassSessionOrThrow(Long id) {
        return classSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy buổi học id=" + id));
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + id));
    }

    private StudentCommentResponse toResponse(StudentComment c) {
        StudentComment previous = c.getCommentType() == StudentComment.CommentType.DAILY && c.getClassSession() != null
                ? previousComment(c.getClassSession(), c.getStudent().getId()) : null;
        ExerciseAssignment grammarNext = c.getHomeworkNextExerciseAssignment();
        ReviewVideoAssignment videoNext = c.getHomeworkNextReviewVideoAssignment();
        return new StudentCommentResponse(
                c.getId(), c.getStudent().getId(), c.getStudent().getUser().getFullName(), c.getSchoolClass().getId(),
                c.getTeacher().getId(), c.getCommentType().name(),
                c.getClassSession() == null ? null : c.getClassSession().getId(),
                c.getGradePeriod() == null ? null : c.getGradePeriod().getId(),
                c.getCommentDate(), c.getContent(), c.getStructuredContent(), c.getSeverity().name(), c.isWarning(),
                c.getStatus().name(), c.getSubmittedAt(), c.getApprovedAt(),
                c.getApprovedBy() == null ? null : c.getApprovedBy().getId(), c.getVisibleToParentAt(), c.getRejectionReason(),
                c.getAttitude() == null ? null : c.getAttitude().name(), c.getHomeworkPreviousScore(),
                c.getHomeworkPreviousSpeakingScore(),
                c.getHomeworkNext(),
                grammarNext == null ? null : grammarNext.getId(),
                grammarNext == null ? null : grammarNext.getExercise().getTitle(),
                videoNext == null ? null : videoNext.getId(),
                videoNext == null ? null : videoNext.getReviewVideoSet().getTitle(),
                grammarPreviousProgressLabel(previous), videoPreviousProgressLabel(previous),
                c.getNote(), c.getClassSession() == null ? null : c.getClassSession().getLessonContent());
    }
}
