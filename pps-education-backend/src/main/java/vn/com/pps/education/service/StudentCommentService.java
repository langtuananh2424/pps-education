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
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.Exercise;
import vn.com.pps.education.domain.ExerciseAssignment;
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
import vn.com.pps.education.dto.ClassSessionTeacherNameResponse;
import vn.com.pps.education.dto.ClassSessionTeacherTypeResponse;
import vn.com.pps.education.dto.CreateStudentCommentRequest;
import vn.com.pps.education.dto.DailyCommentImportPreviewResponse;
import vn.com.pps.education.dto.DailyCommentImportPreviewRow;
import vn.com.pps.education.dto.DailyCommentImportResponse;
import vn.com.pps.education.dto.DecideCommentsRequest;
import vn.com.pps.education.dto.EnterAttendanceMarkRequest;
import vn.com.pps.education.dto.MarkAttendanceRequest;
import vn.com.pps.education.dto.StudentCommentResponse;
import vn.com.pps.education.dto.SubmitCommentsRequest;
import vn.com.pps.education.dto.UpdateStudentCommentContentRequest;
import vn.com.pps.education.dto.UpdateStudentCommentRequest;
import vn.com.pps.education.exception.ApprovalAlreadyDecidedException;
import vn.com.pps.education.exception.HomeworkNextConflictException;
import vn.com.pps.education.exception.MissingCommentContentException;
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
import vn.com.pps.education.repository.AcademicTermRepository;
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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
 * <p><b>Nhận xét Hàng ngày (comment_type=DAILY, biểu mẫu DUY NHẤT còn lại —
 * bỏ hẳn MID_TERM/END_TERM ngày 2026-08-12, đã xác nhận với người dùng, xem
 * Javadoc {@link vn.com.pps.education.domain.StudentComment}) — bổ sung
 * ngoài SDD gốc:</b> quyết định 2026-07-24 (bỏ hẳn bước Nháp, ghi xong tự
 * động chuyển PENDING/APPROVED ngay) đã bị THAY THẾ bởi quyết định
 * 2026-07-29 sau khi dùng thực tế thấy thiếu bước xem lại trước khi gửi
 * duyệt:</p>
 * <ul>
 *   <li>Dùng luồng DRAFT→submit (UC-21 Main Flow bước 4,
 *       {@code submitComments})→PENDING→duyệt (UC-22) —
 *       {@code writeComment}/{@code updateComment}/{@code importComments}
 *       (Excel) chỉ tạo/sửa ở trạng thái DRAFT, không còn tự động route
 *       trạng thái nào. Actor có {@code academic.comment.approve} không
 *       còn được ghi/sửa thẳng ra APPROVED bỏ qua chờ duyệt nữa — muốn Gửi
 *       phải qua đúng {@code submitComments()}, vốn luôn yêu cầu actor là
 *       GV được phân công lớp ({@code requireAssignedTeacher}, không đổi)
 *       — Quản lý điểm trường không kiêm GV lớp đó tự viết 1 nhận xét
 *       DAILY thì không tự Gửi được, phải nhờ đúng GV lớp Gửi (đánh đổi đã
 *       xác nhận với người dùng, giữ code đơn giản).</li>
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
 *       không đổi). "Soạn & Giao đề" (UC-40) và "Kho Video Ôn tập" (UC-23)
 *       không còn tự giao lớp — xem Javadoc ExerciseService/ReviewVideoService.</li>
 * </ul>
 */
@Service
public class StudentCommentService {

    /**
     * Thứ tự cột (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * 2026-08-06) — đồng bộ với thứ tự bảng ở UI web (DailyCommentPanel.tsx):
     * Mã học viên/Họ tên/Ngày sinh → BTVN buổi trước (2 kênh) → BTVN
     * offline → BTVN online (2 kênh) → Hạn nộp bài → Thái độ/Nhận xét/Ghi
     * chú, cộng thêm 3 cột ngữ cảnh buổi học ở đầu (Ngày, Tên bài học, Tên
     * GV giảng dạy) + Điểm danh (chỉ có ở Excel, không có trên UI web).
     */
    private static final String PERM_COMMENT_MANAGE = "academic.comment.manage";
    private static final int COLUMN_COUNT = 17;
    private static final int COL_DATE = 0;
    private static final int COL_STUDENT_CODE = 1;
    private static final int COL_FULL_NAME = 2;
    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — hiển thị đối chiếu, không đọc lại khi import. */
    private static final int COL_DOB = 3;
    /** "Tên bài học" (đổi tên từ "Bài học hôm nay") — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29 (chuyển từ Điểm danh sang Nhận xét). */
    private static final int COL_LESSON_CONTENT = 4;
    /** "Tên giáo viên giảng dạy" — text nhập tay (khác primaryTeacher là FK hệ thống), bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06. */
    private static final int COL_TEACHER_NAME = 5;
    private static final int COL_ATTENDANCE = 6;
    /** "BTVN buổi trước — Offline" (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06) — hiển thị đối chiếu (BTVN offline buổi trước đã giao), KHÔNG đọc lại khi import (mirror COL_DOB). */
    private static final int COL_HOMEWORK_OFFLINE_PREVIOUS = 7;
    private static final int COL_HOMEWORK_GRAMMAR_PREVIOUS = 8;
    private static final int COL_HOMEWORK_SPEAKING_PREVIOUS = 9;
    /** "BTVN offline" — tách khỏi cột gộp cũ (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06), chữ tự do, loại trừ với COL_HOMEWORK_GRAMMAR_NEXT. */
    private static final int COL_HOMEWORK_OFFLINE = 10;
    private static final int COL_HOMEWORK_GRAMMAR_NEXT = 11;
    private static final int COL_HOMEWORK_VIDEO_NEXT = 12;
    /** "Hạn nộp bài" (format yyyy-MM-dd HH:mm) — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06, 1 giá trị chung cả buổi (mirror COL_LESSON_CONTENT). */
    private static final int COL_DUE_DATE = 13;
    private static final int COL_ATTITUDE = 14;
    private static final int COL_CONTENT = 15;
    private static final int COL_NOTE = 16;

    private static final DateTimeFormatter DUE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Cố định múi giờ Việt Nam thay vì {@code ZoneId.systemDefault()} — session_date/start_time/
     * end_time lưu DB là giờ VN thuần (không kèm zone) nên phải quy đổi đúng zone VN khi so sánh
     * với {@code OffsetDateTime.now()}, không phụ thuộc múi giờ JVM đang chạy (biến môi trường TZ
     * của container). Bug thật: TZ chỉ được set qua docker-compose.yml (local dev) — môi trường
     * deploy khác (Railway...) không tự có TZ=Asia/Ho_Chi_Minh, JVM về UTC mặc định của base image,
     * lệch 7 tiếng khiến buổi học đã kết thúc theo giờ VN vẫn bị coi là "chưa kết thúc". Đã từng
     * gặp bug cùng gốc ở UC-09 chấm công (xem comment TZ trong docker-compose.yml) — cố định zone
     * ngay trong code để không còn phụ thuộc cấu hình đúng ở MỌI môi trường chạy sau này.
     */
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * Nhãn 2 kênh BTVN theo Loại giáo viên (bổ sung ngoài SDD gốc, đã xác
     * nhận với người dùng 2026-08-05/06) — mirror ĐÚNG object
     * grammarChannelLabel/videoChannelLabel ở FE (DailyCommentPanel.tsx).
     * teacherType=null (buổi chưa xác định) → fallback "Bài"/"Video" chung
     * chung, cũng khớp fallback FE khi !teacherType.
     */
    private static final Map<ClassSession.TeacherType, String> GRAMMAR_CHANNEL_LABEL = Map.of(
            ClassSession.TeacherType.VIETNAMESE, "Ngữ pháp", ClassSession.TeacherType.FOREIGN, "Bài nghe");
    private static final Map<ClassSession.TeacherType, String> VIDEO_CHANNEL_LABEL = Map.of(
            ClassSession.TeacherType.VIETNAMESE, "Từ Vựng (TKN)", ClassSession.TeacherType.FOREIGN, "Clip phản xạ");

    private String grammarChannelLabel(ClassSession.TeacherType teacherType) {
        return teacherType == null ? "Bài" : GRAMMAR_CHANNEL_LABEL.get(teacherType);
    }

    private String videoChannelLabel(ClassSession.TeacherType teacherType) {
        return teacherType == null ? "Video" : VIDEO_CHANNEL_LABEL.get(teacherType);
    }

    private final StudentCommentRepository studentCommentRepository;
    private final StudentCommentHistoryRepository studentCommentHistoryRepository;
    private final ApprovalFlowRepository approvalFlowRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final StudentRepository studentRepository;
    private final ClassSessionRepository classSessionRepository;
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

    /** Main Flow bước 1-3: lưu nháp DRAFT. */
    @Transactional
    public StudentCommentResponse writeComment(Long classId, CreateStudentCommentRequest request, Long actorUserId) {
        SchoolClass schoolClass = getClassOrThrow(classId);
        if (schoolClass.getStatus() == SchoolClass.Status.CANCELLED) {
            throw new IllegalStateException("Lớp học \"" + schoolClass.getName() + "\" đã bị HỦY — không thể viết nhận xét.");
        }
        User actor = getUserOrThrow(actorUserId);

        ClassSession classSession = getClassSessionOrThrow(request.classSessionId());
        requireCanWriteDailyComment(classSession, actorUserId);
        Student student = studentRepository.findByIdAndDeletedAtIsNull(request.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh id=" + request.studentId()));

        StudentComment comment = new StudentComment();
        comment.setStudent(student);
        comment.setSchoolClass(schoolClass);
        comment.setTeacher(actor);
        comment.setCommentType(StudentComment.CommentType.DAILY);
        comment.setClassSession(classSession);
        comment.setAcademicYear(schoolClass.getAcademicYear());
        comment.setCommentDate(request.commentDate());

        ExerciseAssignment grammarAssignment = resolveExerciseHomework(classSession, null, request.homeworkNextExerciseId(), null,
                request.homeworkNextDueDate(), actorUserId);
        ReviewVideoAssignment videoAssignment = resolveVideoHomework(classSession, null, request.homeworkNextReviewVideoSetId(), null,
                request.homeworkNextDueDate(), actorUserId);
        applyContent(comment, request.content(), request.structuredContent(), request.severity(), request.isWarning(),
                request.attitude(), request.homeworkPreviousScore(), request.homeworkPreviousSpeakingScore(),
                request.homeworkNext(), grammarAssignment, videoAssignment, request.note());
        comment = studentCommentRepository.save(comment);
        writeHistory(comment, actor, StudentCommentHistory.Action.CREATED);
        return toResponse(comment);
    }

    /**
     * Main Flow bước 2, A1: sửa nội dung khi đang DRAFT hoặc sau khi bị
     * REJECTED (quay lại DRAFT để submit lại).
     */
    @Transactional
    public StudentCommentResponse updateComment(Long id, UpdateStudentCommentRequest request, Long actorUserId) {
        StudentComment comment = getCommentOrThrow(id);
        User actor = getUserOrThrow(actorUserId);

        requireCanWriteDailyComment(comment.getClassSession(), actorUserId);
        if (comment.getStatus() != StudentComment.Status.DRAFT && comment.getStatus() != StudentComment.Status.REJECTED) {
            throw new StudentCommentNotEditableException(
                    "Nhận xét này đang ở trạng thái " + comment.getStatus() + " — chỉ sửa được khi Nháp (DRAFT) hoặc Bị từ chối (REJECTED).");
        }

        ExerciseAssignment grammarAssignment = resolveExerciseHomework(comment.getClassSession(), comment.getId(), request.homeworkNextExerciseId(),
                comment.getHomeworkNextExerciseAssignment(), request.homeworkNextDueDate(), actorUserId);
        ReviewVideoAssignment videoAssignment = resolveVideoHomework(comment.getClassSession(), comment.getId(), request.homeworkNextReviewVideoSetId(),
                comment.getHomeworkNextReviewVideoAssignment(), request.homeworkNextDueDate(), actorUserId);
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
     * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) — TOÀN BỘ nhận xét của cả lớp
     * trong 1 lần gọi, thay cho việc FE gọi {@link #listComments(Long, Long)} N lần (1 lần/học sinh)
     * — mỗi dòng đã có studentId nên FE tự gom theo học sinh, không cần tách theo studentId ở BE.
     */
    @Transactional(readOnly = true)
    public List<StudentCommentResponse> listCommentsForClass(Long classId) {
        return studentCommentRepository.findBySchoolClassIdOrderByCommentDateDesc(classId)
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

    /** Main Flow bước 4-5: Gửi (submit). */
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
                        "Nhận xét này đang ở trạng thái " + comment.getStatus() + " — chỉ gửi duyệt được khi còn Nháp (DRAFT).");
            }
            // Bổ sung ngoài SDD gốc, xác nhận 2026-08-17 — content không còn @NotBlank ở DTO (lưu nháp
            // được mà chưa cần Nhận xét), nên đây là chốt chặn DUY NHẤT còn lại đảm bảo Nhận xét ở
            // trạng thái Chờ duyệt/Đã duyệt (Phụ huynh xem được) luôn có nội dung.
            if (comment.getContent() == null || comment.getContent().isBlank()) {
                throw new MissingCommentContentException(
                        "Học sinh " + comment.getStudent().getUser().getFullName()
                                + " chưa có nội dung Nhận xét — cần nhập Nhận xét trước khi gửi duyệt.");
            }
            if (comment.getClassSession().getLessonContent() == null || comment.getClassSession().getLessonContent().isBlank()) {
                throw new MissingLessonContentException("Buổi học này chưa điền bài học hôm nay — không thể gửi duyệt.");
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
                        "Nhận xét này đã được quyết định (" + comment.getStatus() + ").");
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

    /**
     * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-02) —
     * Quản lý điểm trường sửa trực tiếp nội dung nhận xét đang PENDING thay
     * vì phải Trả về cho Giáo viên. CHỈ cập nhật content, phớt lờ các
     * trường khác (BTVN buổi sau) để tránh side-effect.
     */
    @Transactional
    public StudentCommentResponse updatePendingCommentContent(Long id, UpdateStudentCommentContentRequest request, Long actorUserId) {
        StudentComment comment = getCommentOrThrow(id);
        User actor = getUserOrThrow(actorUserId);

        // Kiểm tra quyền nghiêm ngặt giống decideComments
        if (!permissionEvaluationService.hasPermission(actorUserId, "academic.comment.approve")) {
            throw new NotSiteManagerForSiteException("Tài khoản không có quyền duyệt nhận xét.");
        }
        requireSiteManagerForSite(comment.getSchoolClass().getSite().getId(), actorUserId);

        if (comment.getStatus() != StudentComment.Status.PENDING) {
            throw new StudentCommentNotEditableException(
                    "Nhận xét này đang ở trạng thái " + comment.getStatus() + " — chỉ quản lý mới được sửa khi đang Chờ duyệt (PENDING).");
        }

        comment.setContent(request.content());
        if (request.structuredContent() != null) {
            comment.setStructuredContent(request.structuredContent());
        }

        comment = studentCommentRepository.save(comment);
        writeHistory(comment, actor, StudentCommentHistory.Action.UPDATED);
        return toResponse(comment);
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
        requireSessionNotSent(classSession);
        classSession.setLessonContent(lessonContent);
        classSession = classSessionRepository.save(classSession);
        return new ClassSessionLessonContentResponse(classSession.getId(), classSession.getLessonContent());
    }

    /**
     * "Loại giáo viên" của buổi học (bổ sung ngoài SDD gốc, đã xác nhận với
     * người dùng 2026-08-05) — Nhận xét học viên dùng để lọc/đổi nhãn BTVN
     * buổi sau theo GV Việt Nam/nước ngoài. Dùng chung rào requireCanWriteDailyComment,
     * mirror updateLessonContent.
     */
    @Transactional
    public ClassSessionTeacherTypeResponse updateSessionTeacherType(Long classSessionId, String teacherType, Long actorUserId) {
        ClassSession classSession = getClassSessionOrThrow(classSessionId);
        requireCanWriteDailyComment(classSession, actorUserId);
        requireSessionNotSent(classSession);
        classSession.setTeacherType(ClassSession.TeacherType.valueOf(teacherType));
        classSession = classSessionRepository.save(classSession);
        return new ClassSessionTeacherTypeResponse(classSession.getId(), classSession.getTeacherType().name());
    }

    /**
     * "Tên giáo viên giảng dạy" thực tế của buổi (bổ sung ngoài SDD gốc, đã
     * xác nhận với người dùng 2026-08-06) — KHÁC primaryTeacher (FK hệ
     * thống), text nhập tay dùng khi GV nước ngoài không tự thao tác hệ
     * thống. Dùng chung rào requireCanWriteDailyComment, mirror updateLessonContent.
     */
    @Transactional
    public ClassSessionTeacherNameResponse updateActualTeacherName(Long classSessionId, String actualTeacherName, Long actorUserId) {
        ClassSession classSession = getClassSessionOrThrow(classSessionId);
        requireCanWriteDailyComment(classSession, actorUserId);
        requireSessionNotSent(classSession);
        classSession.setActualTeacherName(actualTeacherName);
        classSession = classSessionRepository.save(classSession);
        return new ClassSessionTeacherNameResponse(classSession.getId(), classSession.getActualTeacherName());
    }

    /**
     * Chặn sửa 3 thông tin dùng CHUNG cả buổi (Loại giáo viên/Bài học hôm
     * nay/Tên giáo viên giảng dạy) khi buổi đã có ít nhất 1 nhận xét ĐANG
     * chờ duyệt/ĐÃ duyệt (bổ sung ngoài SDD gốc, đã xác nhận với người
     * dùng 2026-08-06) — tránh đổi ngược sau khi đã gửi, gây lệch với nội
     * dung đã duyệt/đang chờ duyệt. Mọi nhận xét bị TỪ CHỐI (REJECTED)
     * không tính — buổi coi như "chưa gửi", GV sửa lại bình thường.
     */
    private void requireSessionNotSent(ClassSession session) {
        boolean hasSent = studentCommentRepository.findByClassSessionId(session.getId()).stream()
                .anyMatch(c -> c.getStatus() == StudentComment.Status.PENDING || c.getStatus() == StudentComment.Status.APPROVED);
        if (hasSent) {
            throw new StudentCommentNotEditableException(
                    "Buổi học này đã có nhận xét đang chờ duyệt/đã duyệt — không sửa được "
                            + "Loại giáo viên/Bài học hôm nay/Tên giáo viên giảng dạy nữa. Muốn sửa lại, nhờ Quản lý "
                            + "điểm trường từ chối toàn bộ nhận xét của buổi để mở khoá.");
        }
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
        ClassSession.TeacherType sessionTeacherType = classSession.getTeacherType();

        Map<Long, AttendanceMark.Status> attendanceByStudent = currentAttendanceByStudent(classSessionId);
        List<ClassEnrollment> enrollments = classEnrollmentRepository
                .findBySchoolClassIdAndStatus(classId, ClassEnrollment.Status.ACTIVE);
        List<Exercise> grammarOptions = exerciseRepository.findAvailableForClass(classId, Exercise.Status.PUBLISHED)
                .stream().filter(e -> matchesSessionTeacherType(e, sessionTeacherType)).toList();
        List<ReviewVideoSet> videoOptions = reviewVideoSetRepository.findAvailableForClass(classId, ReviewVideoSet.Status.PUBLISHED)
                .stream().filter(s -> matchesSessionTeacherType(s, sessionTeacherType)).toList();

        String grammarLabelText = grammarChannelLabel(sessionTeacherType);
        String videoLabelText = videoChannelLabel(sessionTeacherType);
        // Nhãn cột con chỉ còn tên kênh (không lặp lại "BTVN buổi trước —"/"BTVN online —") — header gộp
        // ở dòng trên (headerGroups bên dưới) đã nói rõ nhóm, mirror đúng cấu trúc 2 dòng header (nhóm +
        // cột con) đang dùng ở bảng UI web (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06).
        List<String> headers = List.of("Ngày*", "Mã học viên*", "Họ và tên", "Ngày sinh", "Tên bài học",
                "Tên giáo viên giảng dạy", "Điểm danh*",
                "Offline", grammarLabelText, videoLabelText,
                "BTVN offline", grammarLabelText, videoLabelText,
                "Hạn nộp bài", "Thái độ học tập", "Nhận xét học sinh*", "Ghi chú");
        List<ExcelExportHelper.HeaderGroup> headerGroups = List.of(
                new ExcelExportHelper.HeaderGroup("BTVN buổi trước", COL_HOMEWORK_OFFLINE_PREVIOUS, COL_HOMEWORK_SPEAKING_PREVIOUS),
                new ExcelExportHelper.HeaderGroup("BTVN online", COL_HOMEWORK_GRAMMAR_NEXT, COL_HOMEWORK_VIDEO_NEXT));
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
            row.add(student.getDateOfBirth() == null ? null : student.getDateOfBirth().toString());
            row.add(classSession.getLessonContent());
            row.add(classSession.getActualTeacherName());
            row.add(attendance == null ? null : attendanceLabel(attendance));
            row.add(resolvedOfflinePrevious(previous));
            row.add(resolvedGrammarPrevious(existing, previous));
            row.add(resolvedSpeakingPrevious(existing, previous));
            row.add(resolvedHomeworkOffline(existing));
            row.add(resolvedHomeworkOnlineGrammar(existing));
            row.add(existing == null || existing.getHomeworkNextReviewVideoAssignment() == null ? null
                    : videoLabel(existing.getHomeworkNextReviewVideoAssignment().getReviewVideoSet()));
            row.add(resolvedDueAt(existing));
            row.add(existing == null || existing.getAttitude() == null ? null : attitudeLabel(existing.getAttitude()));
            row.add(existing == null ? null : existing.getContent());
            row.add(existing == null ? null : existing.getNote());
            rows.add(row);
        }
        Map<Integer, List<String>> dropdowns = new LinkedHashMap<>();
        dropdowns.put(COL_ATTENDANCE, Arrays.stream(AttendanceMark.Status.values()).map(this::attendanceLabel).toList());
        dropdowns.put(COL_ATTITUDE, Arrays.stream(StudentComment.Attitude.values()).map(this::attitudeLabel).toList());
        dropdowns.put(COL_HOMEWORK_GRAMMAR_NEXT, grammarOptions.stream().map(this::grammarLabel).toList());
        dropdowns.put(COL_HOMEWORK_VIDEO_NEXT, videoOptions.stream().map(this::videoLabel).toList());
        // Sheet "Hướng dẫn" (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06) — thống nhất
        // định dạng dữ liệu trước khi nhập lại lên hệ thống, đặc biệt cột Ngày/Hạn nộp bài (dễ bị Excel
        // tự đổi định dạng ngày giờ theo cấu hình máy khi gõ tay).
        List<String> notes = List.of(
                "LƯU Ý TRƯỚC KHI NHẬP LẠI FILE NÀY LÊN HỆ THỐNG:",
                "",
                "1. Cột \"Ngày*\" (" + colLetter(COL_DATE) + "): bắt buộc đúng định dạng yyyy-MM-dd (VD: 2026-08-10), đúng bằng ngày của buổi học đang chọn — sai định dạng hoặc khác ngày sẽ bị từ chối cả dòng.",
                "2. Cột \"Hạn nộp bài\" (" + colLetter(COL_DUE_DATE) + "): nếu có điền, đúng định dạng yyyy-MM-dd HH:mm (VD: 2026-08-10 17:30) — gõ ĐÚNG y hệt, không để Excel tự chuyển sang định dạng ngày giờ theo máy (dd/mm/yyyy, mm/dd/yyyy...) vì có thể đọc sai khi nhập lại. Nếu Excel tự đổi khi gõ, bôi đen cột này rồi Format Cells > Text trước khi nhập. PHẢI GIỐNG NHAU giữa mọi học sinh trong lớp (chỉ 1 hạn nộp chung cho cả buổi) — khác nhau sẽ bị từ chối CẢ FILE.",
                "3. Cột \"Tên bài học\" (" + colLetter(COL_LESSON_CONTENT) + ") và \"Tên giáo viên giảng dạy\" (" + colLetter(COL_TEACHER_NAME) + "): cũng phải giống nhau giữa mọi học sinh trong lớp (1 giá trị dùng chung cho cả buổi) — khác nhau sẽ bị từ chối CẢ FILE.",
                "4. Cột \"BTVN offline\" (" + colLetter(COL_HOMEWORK_OFFLINE) + ") và nhóm \"BTVN online\" (" + colLetter(COL_HOMEWORK_GRAMMAR_NEXT) + "-" + colLetter(COL_HOMEWORK_VIDEO_NEXT) + "): loại trừ nhau — chỉ điền 1 trong 2 cho mỗi học sinh, không điền cả offline lẫn online.",
                "5. 2 cột con của nhóm \"BTVN online\": chọn đúng 1 giá trị trong danh sách dropdown thả xuống, không tự gõ tên khác — có thể dán uuid thay cho chọn dropdown nếu danh sách quá dài (uuid xem trong Kho đề/Kho Video Ôn tập).",
                "6. Cột \"Họ và tên\" (" + colLetter(COL_FULL_NAME) + "), \"Ngày sinh\" (" + colLetter(COL_DOB) + "), và cột con \"Offline\" trong nhóm \"BTVN buổi trước\": chỉ hiển thị để đối chiếu — sửa các cột này KHÔNG được lưu lại khi nhập lên.",
                "7. Cột \"Điểm danh*\" (" + colLetter(COL_ATTENDANCE) + ") và \"Thái độ học tập\" (" + colLetter(COL_ATTITUDE) + "): nên chọn đúng trong dropdown cho chắc chắn, dù hệ thống có chấp nhận thêm vài biến thể viết khác."
        );
        return ExcelExportHelper.buildWorkbook("Nhận xét", headers, rows, notes, dropdowns, headerGroups);
    }

    /** Đổi chỉ số cột 0-based sang chữ cột Excel (A, B, ..., Z, AA, ...) — dùng để tham chiếu đúng cột trong sheet "Hướng dẫn" dù thứ tự cột có đổi sau này. */
    private static String colLetter(int index0) {
        StringBuilder sb = new StringBuilder();
        int n = index0 + 1;
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    /**
     * Lọc Bài/Video theo Loại giáo viên của buổi (bổ sung ngoài SDD gốc, đã
     * xác nhận với người dùng 2026-08-06) — sessionTeacherType=null (buổi
     * chưa xác định loại GV) thì KHÔNG lọc, giữ hành vi an toàn cũ (hiện
     * hết, mirror fallback FE khi !teacherType).
     */
    private boolean matchesSessionTeacherType(Exercise exercise, ClassSession.TeacherType sessionTeacherType) {
        return sessionTeacherType == null || exercise.getExam().getTeacherType().name().equals(sessionTeacherType.name());
    }

    /**
     * Mirror {@link #matchesSessionTeacherType(Exercise, ClassSession.TeacherType)} cho kênh Video. V98
     * (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06) — so trực tiếp
     * {@code ReviewVideoSet.teacherType} (field tường minh mới) thay vì suy diễn từ videoType
     * (CONNECTION=VIETNAMESE/REFLEX=FOREIGN) như trước khi có field này.
     */
    private boolean matchesSessionTeacherType(ReviewVideoSet set, ClassSession.TeacherType sessionTeacherType) {
        return sessionTeacherType == null || set.getTeacherType().name().equals(sessionTeacherType.name());
    }

    /**
     * "BTVN buổi trước — Offline" (bổ sung ngoài SDD gốc, đã xác nhận với
     * người dùng 2026-08-06) — chỉ có giá trị khi buổi TRƯỚC giao Offline
     * (không có ExerciseAssignment) — mirror resolvedHomeworkOffline nhưng
     * đọc từ {@code previous} thay vì {@code existing}. Loại trừ với
     * resolvedGrammarPrevious (đúng 1 trong 2 khác null, không cả hai).
     */
    private String resolvedOfflinePrevious(StudentComment previous) {
        if (previous == null || previous.getHomeworkNextExerciseAssignment() != null) {
            return null;
        }
        return previous.getHomeworkNext();
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

    /** "BTVN offline" (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06, tách khỏi cột gộp cũ) — chỉ có giá trị khi CHƯA giao online. */
    private String resolvedHomeworkOffline(StudentComment existing) {
        if (existing == null || existing.getHomeworkNextExerciseAssignment() != null) {
            return null;
        }
        return existing.getHomeworkNext();
    }

    /** "BTVN online — {Ngữ pháp/Bài nghe}" — chỉ có giá trị khi ĐÃ giao online. */
    private String resolvedHomeworkOnlineGrammar(StudentComment existing) {
        if (existing == null || existing.getHomeworkNextExerciseAssignment() == null) {
            return null;
        }
        return grammarLabel(existing.getHomeworkNextExerciseAssignment().getExercise());
    }

    /**
     * "Hạn nộp bài" (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * 2026-08-06) — lấy dueAt từ bản giao Ngữ pháp/Bài nghe hoặc Video (cái
     * nào khác null), format yyyy-MM-dd HH:mm theo múi giờ hệ thống.
     */
    private String resolvedDueAt(StudentComment existing) {
        if (existing == null) {
            return null;
        }
        OffsetDateTime dueAt = existing.getHomeworkNextExerciseAssignment() != null
                ? existing.getHomeworkNextExerciseAssignment().getDueAt()
                : existing.getHomeworkNextReviewVideoAssignment() != null
                        ? existing.getHomeworkNextReviewVideoAssignment().getDueAt() : null;
        return dueAt == null ? null : dueAt.atZoneSameInstant(APP_ZONE).toLocalDateTime().format(DUE_DATE_FORMAT);
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

        try {
            ParsedImportFile parsedFile = parseImportWorkbook(file, classSession);
            List<ParsedRow> parsedRows = parsedFile.rows();
            List<Map<String, Object>> errors = new ArrayList<>(parsedFile.errors());

            if (parsedFile.lessonContent() != null) {
                classSession.setLessonContent(parsedFile.lessonContent());
                classSessionRepository.save(classSession);
            }
            if (parsedFile.teacherName() != null) {
                classSession.setActualTeacherName(parsedFile.teacherName());
                classSessionRepository.save(classSession);
            }
            LocalDateTime customDueDate = parsedFile.customDueDate();

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
                            parsed.homeworkPreviousSpeaking(), customDueDate, actor);
                    successRows++;
                } catch (RuntimeException ex) {
                    errors.add(rowError(parsed.rowNumber() + 1, ex.getMessage()));
                }
            }

            job.setTotalRows(parsedFile.totalRows());
            job.setSuccessRows(successRows);
            job.setFailedRows(errors.size());
            job.setErrorSummary(errors);
            job.setStatus(errors.isEmpty() ? ImportJob.Status.COMPLETED : ImportJob.Status.PARTIAL_SUCCESS);
            job.setFinishedAt(OffsetDateTime.now());
            job = importJobRepository.save(job);
            return toImportResponse(job);
        } catch (ImportValidationFailed ex) {
            return failJob(job, ex.getMessage());
        } catch (IOException | RuntimeException ex) {
            return failJob(job, "File sai định dạng Excel (.xlsx): " + ex.getMessage());
        }
    }

    /**
     * UC-21 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-14):
     * xem trước file Excel BTVN — CHỈ trả về dữ liệu đã parse để Giáo viên
     * fill vào bảng nhận xét trên UI (nút "Lưu"/autosave sẽ ghi DRAFT sau),
     * KHÔNG ghi StudentComment/Bài học hôm nay/Tên GV giảng dạy/Hạn nộp vào
     * DB — khác {@link #importComments} (ghi thẳng DB, giữ lại cho tương
     * thích ngược, không còn dùng ở UI nhập Excel nữa). Điểm danh vẫn ghi
     * NGAY như importComments (nghiệp vụ độc lập, không thuộc quy trình
     * soạn/duyệt nhận xét — đã xác nhận với người dùng).
     */
    @Transactional
    public DailyCommentImportPreviewResponse previewImportComments(Long classSessionId, MultipartFile file, Long actorUserId) {
        ClassSession classSession = getClassSessionOrThrow(classSessionId);
        requireCanWriteDailyComment(classSession, actorUserId);

        try {
            ParsedImportFile parsedFile = parseImportWorkbook(file, classSession);
            List<ParsedRow> parsedRows = parsedFile.rows();
            List<Map<String, Object>> errors = new ArrayList<>(parsedFile.errors());

            // Mirror nguyên khối ghi điểm danh của importComments — điểm danh vẫn ghi NGAY dù nhận
            // xét/Bài học hôm nay/Tên GV/Hạn nộp chỉ fill ra FE, chưa ghi DB (đã xác nhận với người dùng).
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

            // Mirror điều kiện importRow() (bỏ qua Vắng/Có phép mà mọi cột nhận xét đều trống, bắt
            // buộc có nội dung nếu không) — không ghi comment, chỉ dựng preview row trả về FE.
            List<DailyCommentImportPreviewRow> previewRows = new ArrayList<>();
            int successRows = 0;
            for (ParsedRow parsed : parsedRows) {
                try {
                    AttendanceMark.Status effectiveAttendance = currentAttendance.getOrDefault(
                            parsed.student().getId(), parsed.attendance());
                    if (attendanceWriteFailed && parsed.attendance() != effectiveAttendance) {
                        throw new IllegalArgumentException(
                                "Không sửa được điểm danh: " + attendanceWriteFailedReason);
                    }
                    boolean absent = effectiveAttendance == AttendanceMark.Status.ABSENT
                            || effectiveAttendance == AttendanceMark.Status.EXCUSED;
                    boolean allBlank = parsed.attitude() == null && parsed.homeworkPrevious() == null
                            && parsed.content() == null && parsed.homeworkNext() == null
                            && parsed.grammarExercise() == null && parsed.videoSet() == null
                            && parsed.note() == null && parsed.homeworkPreviousSpeaking() == null;
                    if (!(absent && allBlank)) {
                        if (parsed.content() == null || parsed.content().isBlank()) {
                            throw new IllegalArgumentException(
                                    "Thiếu nhận xét (cột P) — bắt buộc trừ khi học sinh vắng/có phép.");
                        }
                        previewRows.add(new DailyCommentImportPreviewRow(
                                parsed.student().getId(), parsed.attitude(), parsed.homeworkPrevious(),
                                parsed.homeworkPreviousSpeaking(), parsed.content(), parsed.homeworkNext(),
                                parsed.grammarExercise() == null ? null : parsed.grammarExercise().getId(),
                                parsed.videoSet() == null ? null : parsed.videoSet().getId(), parsed.note()));
                    }
                    successRows++;
                } catch (RuntimeException ex) {
                    errors.add(rowError(parsed.rowNumber() + 1, ex.getMessage()));
                }
            }

            return new DailyCommentImportPreviewResponse(parsedFile.totalRows(), successRows, errors.size(), errors,
                    parsedFile.lessonContent(), parsedFile.teacherName(), parsedFile.customDueDate(), previewRows);
        } catch (ImportValidationFailed ex) {
            return new DailyCommentImportPreviewResponse(0, 0, 1, List.of(rowError(0, ex.getMessage())), null, null, null, List.of());
        } catch (IOException | RuntimeException ex) {
            return new DailyCommentImportPreviewResponse(0, 0, 1,
                    List.of(rowError(0, "File sai định dạng Excel (.xlsx): " + ex.getMessage())), null, null, null, List.of());
        }
    }

    /** Báo hiệu 1 lỗi validate CHẶN TOÀN BỘ file (khác lỗi riêng 1 dòng, gom vào errorSummary) — dùng trong {@link #parseImportWorkbook}, bắt riêng ở 2 method gọi để giữ đúng nguyên văn thông báo lỗi (không bị bọc thêm "File sai định dạng Excel"). */
    private static final class ImportValidationFailed extends RuntimeException {
        ImportValidationFailed(String message) {
            super(message);
        }
    }

    private record ParsedImportFile(List<ParsedRow> rows, int totalRows, List<Map<String, Object>> errors,
                                     String lessonContent, String teacherName, LocalDateTime customDueDate) {}

    /**
     * Parse file Excel BTVN thành danh sách {@link ParsedRow} + 3 giá trị dùng CHUNG cả buổi (Bài học
     * hôm nay/Tên GV giảng dạy/Hạn nộp bài) — dùng chung cho {@link #importComments} (ghi thẳng DB) và
     * {@link #previewImportComments} (chỉ trả về FE). Tách ra 2026-08-14 để 2 luồng không lặp lại logic
     * parse (đối chiếu {@code solid.md} — Open/Closed, thêm luồng mới không sửa lại luồng cũ).
     */
    private ParsedImportFile parseImportWorkbook(MultipartFile file, ClassSession classSession) throws IOException {
        Long classId = classSession.getSchoolClass().getId();
        ClassSession.TeacherType sessionTeacherType = classSession.getTeacherType();
        Map<String, Exercise> grammarByLabel = exerciseRepository
                .findAvailableForClass(classId, Exercise.Status.PUBLISHED).stream()
                .filter(e -> matchesSessionTeacherType(e, sessionTeacherType))
                .collect(java.util.stream.Collectors.toMap(this::grammarLabel, e -> e, (a, b) -> a));
        Map<String, ReviewVideoSet> videoByLabel = reviewVideoSetRepository.findAvailableForClass(classId, ReviewVideoSet.Status.PUBLISHED).stream()
                .filter(s -> matchesSessionTeacherType(s, sessionTeacherType))
                .collect(java.util.stream.Collectors.toMap(this::videoLabel, s -> s, (a, b) -> a));

        List<Map<String, Object>> errors = new ArrayList<>();
        try (InputStream inputStream = file.getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getRow(0) == null) {
                throw new ImportValidationFailed("File rỗng hoặc thiếu dòng tiêu đề.");
            }
            DataFormatter formatter = new DataFormatter();

            // File mẫu giờ có 2 DÒNG header (dòng nhóm "BTVN buổi trước"/"BTVN online" merge + dòng
            // tên cột con) — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06, mirror đúng
            // headerRowIndex+1 dùng khi buildTemplate() gọi ExcelExportHelper.buildWorkbook(headerGroups).
            // Dữ liệu học sinh bắt đầu từ dòng 3 (index 2), không phải dòng 2 (index 1) như trước.
            List<ParsedRow> parsedRows = new ArrayList<>();
            int totalRows = 0;
            for (int rowIndex = 2; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
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
                throw new ImportValidationFailed("Bài học hôm nay không đồng nhất giữa các học sinh trong lớp — mọi học sinh phải học cùng 1 bài.");
            }
            String lessonContent = lessonContentValues.size() == 1 ? lessonContentValues.iterator().next() : null;

            // "Tên giáo viên giảng dạy" dùng CHUNG cả buổi, mirror lessonContentValues (bổ sung ngoài
            // SDD gốc, đã xác nhận với người dùng 2026-08-06).
            Set<String> teacherNameValues = parsedRows.stream()
                    .map(ParsedRow::teacherName).filter(v -> v != null && !v.isBlank())
                    .collect(java.util.stream.Collectors.toSet());
            if (teacherNameValues.size() > 1) {
                throw new ImportValidationFailed("Tên giáo viên giảng dạy không đồng nhất giữa các học sinh trong lớp — mọi học sinh trong buổi phải chung 1 GV dạy.");
            }
            String teacherName = teacherNameValues.size() == 1 ? teacherNameValues.iterator().next() : null;

            // "Hạn nộp bài" dùng CHUNG cả buổi, mirror lessonContentValues (bổ sung ngoài SDD gốc, đã
            // xác nhận với người dùng 2026-08-06) — để trống thì BE tự tính = buổi kế tiếp (hành vi cũ).
            Set<String> dueDateValues = parsedRows.stream()
                    .map(ParsedRow::dueDateText).filter(v -> v != null && !v.isBlank())
                    .collect(java.util.stream.Collectors.toSet());
            if (dueDateValues.size() > 1) {
                throw new ImportValidationFailed("Hạn nộp bài không đồng nhất giữa các học sinh trong lớp — mọi học sinh trong buổi phải chung 1 hạn nộp.");
            }
            LocalDateTime customDueDate = null;
            if (dueDateValues.size() == 1) {
                String rawDueDate = dueDateValues.iterator().next();
                try {
                    customDueDate = LocalDateTime.parse(rawDueDate, DUE_DATE_FORMAT);
                } catch (DateTimeParseException ex) {
                    throw new ImportValidationFailed("Hạn nộp bài sai định dạng (cần yyyy-MM-dd HH:mm): " + rawDueDate);
                }
            }

            return new ParsedImportFile(parsedRows, totalRows, errors, lessonContent, teacherName, customDueDate);
        }
    }

    private record ParsedRow(int rowNumber, Student student, AttendanceMark.Status attendance, String attitude,
                              String homeworkPrevious, String content, String homeworkNext,
                              Exercise grammarExercise, ReviewVideoSet videoSet, String note,
                              String homeworkPreviousSpeaking, String lessonContent,
                              /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06. */
                              String teacherName,
                              /** Chưa parse LocalDateTime ở đây — validate/parse đồng nhất cả buổi ở importComments (mirror lessonContent). */
                              String dueDateText) {}

    private ParsedRow parseRow(Row row, DataFormatter formatter, int rowIndex, ClassSession classSession,
                                Map<String, Exercise> grammarByLabel, Map<String, ReviewVideoSet> videoByLabel) {
        String dateText = cell(row, formatter, COL_DATE);
        String studentCode = cell(row, formatter, COL_STUDENT_CODE);
        String lessonContentText = cell(row, formatter, COL_LESSON_CONTENT);
        String teacherNameText = cell(row, formatter, COL_TEACHER_NAME);
        String attendanceText = cell(row, formatter, COL_ATTENDANCE);
        String attitudeText = cell(row, formatter, COL_ATTITUDE);
        String homeworkPrevious = cell(row, formatter, COL_HOMEWORK_GRAMMAR_PREVIOUS);
        String content = cell(row, formatter, COL_CONTENT);
        String homeworkOfflineText = cell(row, formatter, COL_HOMEWORK_OFFLINE);
        String grammarNextText = cell(row, formatter, COL_HOMEWORK_GRAMMAR_NEXT);
        String videoText = cell(row, formatter, COL_HOMEWORK_VIDEO_NEXT);
        String dueDateText = cell(row, formatter, COL_DUE_DATE);
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
            throw new IllegalArgumentException("Thiếu điểm danh (cột G).");
        }
        AttendanceMark.Status attendance = parseAttendanceStatus(attendanceText.trim());

        String attitude = attitudeText == null || attitudeText.isBlank() ? null : parseAttitude(attitudeText.trim()).name();
        // BTVN offline/online giờ TÁCH 2 CỘT riêng biệt (bổ sung ngoài SDD gốc, đã xác nhận với người
        // dùng 2026-08-06, khớp UI web) — cột online dùng resolveByUuidOrLabel CHẶT (khớp dropdown/uuid
        // thì nhận, không khớp thì báo lỗi luôn, không còn âm thầm fallback về offline như trước).
        // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-18 — bỏ ràng buộc loại trừ lẫn
        // nhau: 1 buổi giờ được giao ĐỒNG THỜI cả BTVN offline (chữ tự do) VÀ online (Exercise) cho
        // kênh Ngữ pháp, khớp hành vi đã có sẵn ở writeComment/updateComment (API JSON chưa từng chặn
        // tổ hợp này — chỉ luồng Excel import còn chặn, nay gỡ bỏ cho nhất quán).
        String homeworkNext = blankToNull(homeworkOfflineText);
        Exercise grammarExercise = resolveByUuidOrLabel(blankToNull(grammarNextText), grammarByLabel,
                exerciseRepository::findByUuid, "đề");
        ReviewVideoSet videoSet = resolveByUuidOrLabel(blankToNull(videoText), videoByLabel,
                reviewVideoSetRepository::findByUuid, "bộ video");
        return new ParsedRow(rowIndex, student, attendance, attitude,
                blankToNull(homeworkPrevious), blankToNull(content), homeworkNext,
                grammarExercise, videoSet, blankToNull(note), blankToNull(homeworkPreviousSpeaking),
                blankToNull(lessonContentText), blankToNull(teacherNameText), blankToNull(dueDateText));
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
                            String homeworkPreviousSpeaking, LocalDateTime customDueDate, User actor) {
        boolean absent = attendance == AttendanceMark.Status.ABSENT || attendance == AttendanceMark.Status.EXCUSED;
        boolean allBlank = attitude == null && homeworkPrevious == null && content == null
                && homeworkNext == null && grammarExercise == null && videoSet == null && note == null
                && homeworkPreviousSpeaking == null;
        if (absent && allBlank) {
            return;
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Thiếu nhận xét (cột P) — bắt buộc trừ khi học sinh vắng/có phép.");
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
        // "Hạn nộp bài" — 1 giá trị chung cả buổi, đã validate/parse ở importComments (customDueDate=null thì BE tự tính = buổi kế tiếp).
        ExerciseAssignment grammarAssignment = resolveExerciseHomework(classSession, comment.getId(),
                grammarExercise == null ? null : grammarExercise.getId(),
                comment.getHomeworkNextExerciseAssignment(), customDueDate, actor.getId());
        ReviewVideoAssignment videoAssignment = resolveVideoHomework(classSession, comment.getId(),
                videoSet == null ? null : videoSet.getId(),
                comment.getHomeworkNextReviewVideoAssignment(), customDueDate, actor.getId());
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
        // Bổ sung ngoài SDD gốc, xác nhận 2026-08-17 — content không còn @NotBlank ở DTO (cho lưu nháp
        // độc lập Thái độ/BTVN/Ghi chú), nhưng cột DB student_comments.content vẫn NOT NULL (V15,
        // không ALTER) — ghi "" thay vì null khi FE gửi thiếu, tránh vi phạm ràng buộc DB.
        comment.setContent(content == null ? "" : content);
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
     * @param customDueDate    Nhận xét học viên (bổ sung ngoài SDD gốc, đã
     *                         xác nhận với người dùng 2026-08-05, cho phép
     *                         chọn GIỜ 2026-08-06) — hạn nộp (ngày + giờ)
     *                         Giáo viên tự chọn; null thì giữ hành vi cũ
     *                         (resolveNextSessionDueAt).
     */
    private ExerciseAssignment resolveExerciseHomework(ClassSession session, Long excludeCommentId, Long exerciseId,
                                                        ExerciseAssignment previous, LocalDateTime customDueDate, Long actorUserId) {
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
        OffsetDateTime dueAt = resolveDueAt(session, customDueDate);
        requireNoDueDateConflict(session, excludeCommentId, dueAt);
        ExerciseAssignment assignment = exerciseService.deliverToClass(exerciseId, session.getSchoolClass().getId(), dueAt, actorUserId, session);
        if (previous != null) {
            exerciseService.cancelAssignment(previous);
        }
        return assignment;
    }

    /** V65: mirror resolveExerciseHomework cho kênh Video Ôn tập — xem Javadoc đó. */
    private ReviewVideoAssignment resolveVideoHomework(ClassSession session, Long excludeCommentId, Long videoSetId,
                                                        ReviewVideoAssignment previous, LocalDateTime customDueDate, Long actorUserId) {
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
        OffsetDateTime dueAt = resolveDueAt(session, customDueDate);
        requireNoDueDateConflict(session, excludeCommentId, dueAt);
        ReviewVideoAssignment assignment = reviewVideoService.deliverToClass(videoSetId, session.getSchoolClass().getId(), dueAt, actorUserId, session);
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
     *
     * Sửa lại 2026-08-14: "buổi kế tiếp" phải CÙNG loại giáo viên với
     * buổi đang nhận xét khi buổi đó có xác định teacherType — mirror
     * đúng cách previousComment() tra "buổi trước" (chỉ đối chiếu buổi
     * cùng loại GV, bỏ qua buổi khác loại xen giữa). Trước đây due date
     * mặc định tính theo buổi kế tiếp TUYỆT ĐỐI (bất kể loại GV), lệch
     * với nơi điểm/% thực sự hiển thị — xem Javadoc
     * ClassSessionRepository#findUpcomingSessions.
     */
    private OffsetDateTime resolveNextSessionDueAt(ClassSession session) {
        List<ClassSession> upcoming = classSessionRepository.findUpcomingSessions(
                session.getSchoolClass().getId(), session.getSessionDate(),
                List.of(ClassSession.Status.CANCELLED, ClassSession.Status.RESCHEDULED), session.getTeacherType());
        if (upcoming.isEmpty()) {
            throw new NoUpcomingClassSessionException(
                    "Lớp này chưa có buổi học kế tiếp"
                            + (session.getTeacherType() != null ? " cùng loại giáo viên (" + session.getTeacherType() + ")" : "")
                            + " trong lịch — không thể đặt hạn nộp cho BTVN buổi sau.");
        }
        ClassSession next = upcoming.get(0);
        return next.getSessionDate().atTime(next.getStartTime()).atZone(APP_ZONE).toOffsetDateTime();
    }

    /**
     * Nhận xét học viên (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * 2026-08-05, cho phép chọn GIỜ 2026-08-06): Giáo viên có thể tự chọn
     * hạn nộp (ngày + giờ) thay vì luôn khoá cứng = buổi kế tiếp —
     * customDueDate khác null thì dùng luôn, BỎ QUA điều kiện "lớp phải có
     * buổi kế tiếp" (chỉ áp dụng cho nhánh tự động resolveNextSessionDueAt).
     * customDueDate=null giữ nguyên hành vi cũ.
     */
    private OffsetDateTime resolveDueAt(ClassSession session, LocalDateTime customDueDate) {
        if (customDueDate != null) {
            return customDueDate.atZone(APP_ZONE).toOffsetDateTime();
        }
        return resolveNextSessionDueAt(session);
    }

    /**
     * Nhận xét học viên (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * 2026-08-05): mirror requireNoHomeworkConflict nhưng cho HẠN NỘP — mọi
     * nhận xét DAILY cùng 1 buổi phải khớp cùng 1 hạn nộp (ngày + giờ) cho
     * BTVN buổi sau (dù chọn Ngữ pháp/Bài nghe hay Video Ôn tập), tránh 2
     * học sinh cùng buổi bị áp 2 hạn nộp khác nhau cho cùng 1 lần giao
     * (ExerciseService/ReviewVideoService#deliverToClass dedupe theo
     * (nguồn, lớp, dueAt) — dueAt khác nhau sẽ âm thầm tạo 2 bản giao riêng
     * biệt nếu không chặn ở đây).
     */
    private void requireNoDueDateConflict(ClassSession session, Long excludeCommentId, OffsetDateTime newDueAt) {
        for (StudentComment sibling : studentCommentRepository.findByClassSessionId(session.getId())) {
            if (excludeCommentId != null && sibling.getId().equals(excludeCommentId)) {
                continue;
            }
            OffsetDateTime siblingDueAt = extractHomeworkNextDueAt(sibling);
            if (siblingDueAt != null && !siblingDueAt.equals(newDueAt)) {
                throw new HomeworkNextConflictException(
                        "Hạn nộp BTVN buổi này đã khóa theo " + siblingDueAt
                                + " (chọn cho học sinh " + sibling.getStudent().getUser().getFullName()
                                + ") — không thể đổi sang " + newDueAt + " cho học sinh khác trong cùng buổi.");
            }
        }
    }

    private OffsetDateTime extractHomeworkNextDueAt(StudentComment c) {
        return c.getHomeworkNextExerciseAssignment() != null ? c.getHomeworkNextExerciseAssignment().getDueAt()
                : c.getHomeworkNextReviewVideoAssignment() != null ? c.getHomeworkNextReviewVideoAssignment().getDueAt() : null;
    }

    /** Chấp nhận dán uuid (không giới hạn theo lớp) HOẶC chọn đúng nhãn dropdown (giới hạn theo bài đã gán cho lớp). */
    private <T> T resolveByUuidOrLabel(String text, Map<String, T> byLabel, Function<UUID, Optional<T>> byUuid, String kindLabel) {
        if (text == null) {
            return null;
        }
        UUID uuid = tryParseUuid(text);
        if (uuid != null) {
            return byUuid.apply(uuid)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy " + kindLabel + " tương ứng."));
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

    /** Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30): "Mã Đề - Tên bài" — phân biệt khi 1 lớp có nhiều Đề. */
    private String grammarLabel(Exercise e) {
        return e.getExam().getCode() + " - " + e.getTitle();
    }

    private String videoLabel(ReviewVideoSet s) {
        return s.getTitle() + " (" + s.getCode() + ")";
    }

    /**
     * Dòng nhận xét của CHÍNH học sinh này ở buổi liền TRƯỚC buổi đang xét,
     * cùng lớp — nguồn tra "đã giao gì cho buổi này". Bổ sung ngoài SDD
     * gốc, đã xác nhận với người dùng 2026-08-12: nếu buổi đang xét CÓ xác
     * định {@code teacherType} (VIETNAMESE/FOREIGN), chỉ đối chiếu với
     * buổi liền trước CÙNG loại giáo viên (VD GVNN buổi 6 đối chiếu GVNN
     * buổi 3, bỏ qua buổi GVVN xen giữa) — vì 2 mạch bài GVVN/GVNN thường
     * độc lập nhau, gối theo buổi liền kề tuyệt đối sẽ lẫn lộn tiến độ của
     * 2 mạch. Buổi không xác định teacherType (giá trị cũ/tùy chọn) vẫn
     * giữ hành vi cũ — đối chiếu buổi liền kề tuyệt đối.
     */
    private StudentComment previousComment(ClassSession classSession, Long studentId) {
        ClassSession.TeacherType teacherType = classSession.getTeacherType();
        Optional<ClassSession> previousSession = teacherType != null
                ? classSessionRepository.findFirstBySchoolClassIdAndSessionDateLessThanAndTeacherTypeOrderBySessionDateDescIdDesc(
                        classSession.getSchoolClass().getId(), classSession.getSessionDate(), teacherType)
                : classSessionRepository.findFirstBySchoolClassIdAndSessionDateLessThanOrderBySessionDateDescIdDesc(
                        classSession.getSchoolClass().getId(), classSession.getSessionDate());
        return previousSession
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
        if (permissionEvaluationService.hasPermission(actorUserId, "academic.comment.approve")
                || permissionEvaluationService.hasPermission(actorUserId, PERM_COMMENT_MANAGE)) {
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
            case "yếu", "yeu", "weak" -> StudentComment.Attitude.WEAK;
            case "trung bình", "trung binh", "average" -> StudentComment.Attitude.AVERAGE;
            case "khá", "kha", "fair" -> StudentComment.Attitude.FAIR;
            case "tốt", "tot", "good" -> StudentComment.Attitude.GOOD;
            case "xuất sắc", "xuat sac", "excellent" -> StudentComment.Attitude.EXCELLENT;
            default -> throw new IllegalArgumentException(
                    "Thái độ học tập không hợp lệ (cần Yếu/Trung bình/Khá/Tốt/Xuất sắc): " + text);
        };
    }

    private String attitudeLabel(StudentComment.Attitude attitude) {
        return switch (attitude) {
            case WEAK -> "Yếu";
            case AVERAGE -> "Trung bình";
            case FAIR -> "Khá";
            case GOOD -> "Tốt";
            case EXCELLENT -> "Xuất sắc";
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

    /** Quyền academic.comment.manage (V107) vượt rào — quản trị viên viết/gửi nhận xét của lớp bất kỳ. */
    private void requireAssignedTeacher(Long classId, Long actorUserId) {
        if (permissionEvaluationService.hasPermission(actorUserId, PERM_COMMENT_MANAGE)) {
            return;
        }
        if (!classTeacherRepository.existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(classId, actorUserId)) {
            throw new NotAssignedTeacherForClassException(
                    "Bạn không được phân công giảng dạy lớp này.");
        }
    }

    private void requireSiteManagerForSite(Long siteId, Long actorUserId) {
        if (!siteManagerRepository.existsBySiteIdAndUserIdAndRoleTypeAndAssignedToIsNull(
                siteId, actorUserId, SiteManager.RoleType.SITE_MANAGER)) {
            throw new NotSiteManagerForSiteException(
                    "Bạn không được gán phụ trách điểm trường này.");
        }
    }

    private void notifySiteManagersPending(List<StudentComment> submitted) {
        submitted.stream()
                .collect(java.util.stream.Collectors.groupingBy(c -> c.getSchoolClass().getId()))
                .forEach((schoolClassId, comments) -> {
                    SchoolClass schoolClass = comments.get(0).getSchoolClass();
                    String title = "Nhận xét học sinh chờ duyệt";
                    String content = "Có %d nhận xét học sinh mới (lớp %s) đang chờ bạn duyệt."
                            .formatted(comments.size(), schoolClass.getName());
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("className", schoolClass.getName());
                    siteManagerRepository.findBySiteIdAndRoleTypeAndAssignedToIsNull(schoolClass.getSite().getId(), SiteManager.RoleType.SITE_MANAGER).forEach(sm ->
                            notificationService.notify(sm.getUser().getId(), Notification.NotificationType.COMMENT_PENDING_APPROVAL, title, content,
                                    metadata, "SCHOOL_CLASS", schoolClassId, Notification.Priority.NORMAL, null));
                });
    }

    private void notifyTeacherRejected(StudentComment comment) {
        String title = "Nhận xét học sinh bị từ chối";
        String content = "Nhận xét cho học sinh %s (lớp %s, ngày %s) đã bị từ chối%s."
                .formatted(comment.getStudent().getUser().getFullName(), comment.getSchoolClass().getName(),
                        comment.getCommentDate(),
                        comment.getRejectionReason() == null ? "" : ": " + comment.getRejectionReason());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("studentName", comment.getStudent().getUser().getFullName());
        metadata.put("className", comment.getSchoolClass().getName());
        notificationService.notify(comment.getTeacher().getId(), Notification.NotificationType.COMMENT_REJECTED, title, content,
                metadata, "STUDENT_COMMENT", comment.getId(), Notification.Priority.NORMAL, null);
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
        StudentComment previous = previousComment(c.getClassSession(), c.getStudent().getId());
        ExerciseAssignment grammarNext = c.getHomeworkNextExerciseAssignment();
        ReviewVideoAssignment videoNext = c.getHomeworkNextReviewVideoAssignment();
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
                c.getHomeworkNext(),
                grammarNext == null ? null : grammarNext.getId(),
                grammarNext == null ? null : grammarNext.getExercise().getTitle(),
                videoNext == null ? null : videoNext.getId(),
                videoNext == null ? null : videoNext.getReviewVideoSet().getTitle(),
                grammarNext != null ? grammarNext.getDueAt() : videoNext != null ? videoNext.getDueAt() : null,
                grammarPreviousProgressLabel(previous), videoPreviousProgressLabel(previous),
                resolvedOfflinePrevious(previous),
                c.getNote(), c.getClassSession().getLessonContent());
    }
}
