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
import vn.com.pps.education.domain.Exam;
import vn.com.pps.education.domain.Exercise;
import vn.com.pps.education.domain.ExerciseAssignment;
import vn.com.pps.education.domain.HomeworkSkillBatch;
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
import vn.com.pps.education.dto.ApplyClassHomeworkRequest;
import vn.com.pps.education.dto.SaveDraftCommentsRequest;
import vn.com.pps.education.dto.SaveDraftCommentsResponse;
import vn.com.pps.education.dto.AutoProgressPreviewResponse;
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
import vn.com.pps.education.dto.StudentCommentHistoryResponse;
import vn.com.pps.education.dto.StudentCommentResponse;
import vn.com.pps.education.dto.SubmitCommentsRequest;
import vn.com.pps.education.dto.UpdateStudentCommentContentRequest;
import vn.com.pps.education.dto.UpdateStudentCommentRequest;
import vn.com.pps.education.exception.ApprovalAlreadyDecidedException;
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
import vn.com.pps.education.repository.ExamRepository;
import vn.com.pps.education.repository.ExerciseAssignmentRepository;
import vn.com.pps.education.repository.ExerciseQuestionRepository;
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
 *       ReviewVideoSet làm "BTVN buổi sau" không còn là chọn lại 1 bản đã
 *       giao sẵn (V55) — TỰ ĐỘNG tạo bản giao
 *       ({@code ExerciseAssignment}/{@code ReviewVideoAssignment}) cho
 *       TOÀN BỘ học sinh ACTIVE của lớp, hạn nộp mặc định = buổi học kế
 *       tiếp ({@code resolveNextSessionDueAt}). Bị từ chối nhận xét
 *       (REJECTED, UC-22) KHÔNG ảnh hưởng bài đã giao (2 việc độc lập).
 *       "Soạn & Giao đề" (UC-40) và "Kho Video Ôn tập" (UC-23) không còn
 *       tự giao lớp — xem Javadoc ExerciseService/ReviewVideoService.
 *       <b>Bổ sung 2026-09-12 (đã xác nhận với người dùng) — ĐÃ THAY THẾ
 *       phần "viết/sửa comment DAILY kích hoạt giao bài" (V65/V127) ở
 *       trên:</b> giao BTVN buổi sau tách hẳn khỏi viết/sửa/gửi Nhận xét,
 *       chỉ còn qua {@link #applyHomeworkToClass} ("Áp dụng cho cả lớp",
 *       có popup xác nhận ở FE) — xem Javadoc method đó để biết đầy đủ lý
 *       do và cơ chế mới (không còn khái niệm "xung đột lựa chọn giữa các
 *       dòng cùng buổi" vì giờ chỉ còn 1 điểm ghi duy nhất).</li>
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

    /**
     * Layout cột "động" từ cột 7 trở đi (2 nhóm BTVN buổi trước/BTVN + Hạn nộp/Thái độ/Nhận xét/Ghi
     * chú) — phụ thuộc {@code classSession.teacherType} (V130, mở rộng V137, bổ sung ngoài SDD gốc, đã
     * xác nhận với người dùng 2026-08-21). Trước V130 đây là các hằng số {@code COL_*} cố định dùng
     * chung mọi buổi; nay buổi VIETNAMESE tách thêm cột "Offline" thành Reading/Writing ở CẢ 2 nhóm
     * (BTVN buổi trước: chấm điểm tay; BTVN: mô tả bài giao) VÀ thêm 2 cột "Online" Reading/Writing
     * (V137, chọn Exercise skillCategory=READING/WRITING, song song cột Online TV+NP/TKN đã có) — buổi
     * FOREIGN (hoặc teacherType=null) giữ NGUYÊN layout cũ (Offline 1 cột/nhóm, Online chỉ 2 cột) để
     * không phá vỡ luồng đang chạy ổn định (đối chiếu solid.md — Open/Closed, thêm biến thể mới không
     * sửa lại biến thể cũ).
     */
    private static final class HomeworkColumns {
        final boolean vietnamese;
        /** VN: điểm Reading buổi trước. FOREIGN: cột "Offline" gộp (đối chiếu, không import) — mirror COL_HOMEWORK_OFFLINE_PREVIOUS cũ. */
        final int previousReadingOrOffline;
        /** VN: điểm Writing buổi trước. FOREIGN: -1 (không có cột này). */
        final int previousWriting;
        /** V137, VN only: % tự động Reading online buổi trước. FOREIGN: -1. */
        final int previousOnlineReading;
        /** V137, VN only: % tự động Writing online buổi trước. FOREIGN: -1. */
        final int previousOnlineWriting;
        final int previousOnlineGrammar;
        final int previousOnlineVideo;
        /** VN: mô tả bài Reading giao buổi sau. FOREIGN: cột "BTVN offline" gộp (chữ tự do) — mirror COL_HOMEWORK_OFFLINE cũ. */
        final int nextReadingOrOffline;
        /** VN: mô tả bài Writing giao buổi sau. FOREIGN: -1 (không có cột này). */
        final int nextWriting;
        /** V137, VN only: chọn Exercise Reading giao online buổi sau. FOREIGN: -1. */
        final int nextOnlineReading;
        /** V137, VN only: chọn Exercise Writing giao online buổi sau. FOREIGN: -1. */
        final int nextOnlineWriting;
        final int nextOnlineGrammar;
        final int nextOnlineVideo;
        final int dueDate;
        final int attitude;
        final int content;
        final int note;
        final int columnCount;

        private HomeworkColumns(ClassSession.TeacherType teacherType) {
            this.vietnamese = teacherType == ClassSession.TeacherType.VIETNAMESE;
            int c = 7;
            previousReadingOrOffline = c++;
            previousWriting = vietnamese ? c++ : -1;
            previousOnlineReading = vietnamese ? c++ : -1;
            previousOnlineWriting = vietnamese ? c++ : -1;
            previousOnlineGrammar = c++;
            previousOnlineVideo = c++;
            nextReadingOrOffline = c++;
            nextWriting = vietnamese ? c++ : -1;
            nextOnlineReading = vietnamese ? c++ : -1;
            nextOnlineWriting = vietnamese ? c++ : -1;
            nextOnlineGrammar = c++;
            nextOnlineVideo = c++;
            dueDate = c++;
            attitude = c++;
            content = c++;
            note = c++;
            columnCount = c;
        }

        static HomeworkColumns of(ClassSession.TeacherType teacherType) {
            return new HomeworkColumns(teacherType);
        }
    }

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

    /**
     * V151 (revert V146, đã xác nhận với người dùng 2026-08-25) — kênh "Ngữ pháp"/"Nghe" dùng CHUNG 1
     * field/1 dropdown (mirror hành vi trước V146) nhưng KHÔNG lặp lại lỗi trộn lẫn kỹ năng đã ghi ở
     * V146 (buổi FOREIGN trước đây không lọc skill_category, Bài VOCAB_GRAMMAR và LISTENING lẫn lộn
     * chung 1 dropdown) — buổi teacherType=FOREIGN vẫn chỉ lọc đúng Bài skillCategory=LISTENING, buổi
     * VIETNAMESE (hoặc chưa xác định) lọc VOCAB_GRAMMAR.
     */
    private Exercise.SkillCategory grammarChannelSkillCategory(ClassSession.TeacherType teacherType) {
        return teacherType == ClassSession.TeacherType.FOREIGN ? Exercise.SkillCategory.LISTENING : Exercise.SkillCategory.VOCAB_GRAMMAR;
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
    private final ExerciseQuestionRepository exerciseQuestionRepository;
    private final ExamRepository examRepository;
    private final ExerciseAssignmentRepository exerciseAssignmentRepository;
    private final ReviewVideoAssignmentRepository reviewVideoAssignmentRepository;
    private final ExerciseService exerciseService;
    private final ReviewVideoService reviewVideoService;
    private final HomeworkSkillBatchService homeworkSkillBatchService;
    private final StudentAttitudeAlertTrackingService attitudeAlertTrackingService;

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
                                  ExerciseQuestionRepository exerciseQuestionRepository,
                                  ExamRepository examRepository,
                                  ExerciseAssignmentRepository exerciseAssignmentRepository,
                                  ReviewVideoAssignmentRepository reviewVideoAssignmentRepository,
                                  ExerciseService exerciseService,
                                  ReviewVideoService reviewVideoService,
                                  HomeworkSkillBatchService homeworkSkillBatchService,
                                  StudentAttitudeAlertTrackingService attitudeAlertTrackingService) {
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
        this.exerciseQuestionRepository = exerciseQuestionRepository;
        this.examRepository = examRepository;
        this.exerciseAssignmentRepository = exerciseAssignmentRepository;
        this.reviewVideoAssignmentRepository = reviewVideoAssignmentRepository;
        this.exerciseService = exerciseService;
        this.reviewVideoService = reviewVideoService;
        this.homeworkSkillBatchService = homeworkSkillBatchService;
        this.attitudeAlertTrackingService = attitudeAlertTrackingService;
    }

    // ===================== UC-21: Viết nhận xét (TEACHER) =====================

    /**
     * Main Flow bước 1-3: lưu nháp DRAFT.
     *
     * Bổ sung ngoài SDD gốc, xác nhận 2026-08-19 — trước đây tạo {@link StudentComment} mới VÔ ĐIỀU
     * KIỆN, không kiểm tra đã có bản ghi DAILY nào cho đúng (classSession, student) này chưa. Race giữa
     * 2 request ghi gần như đồng thời (VD FE gọi lại do timeout, nhiều tab) có thể sinh 2 bản ghi trùng
     * — vỡ {@link #previousComment(ClassSession, Long)}/{@link #buildTemplate(Long, Long)} (đều dùng
     * {@code StudentCommentRepository#findByClassSessionIdAndStudentId}, giả định TỐI ĐA 1 dòng, ném
     * {@code IncorrectResultSizeDataAccessException} khi có 2 — lộ ra FE dưới dạng lỗi 500/mất trắng dữ
     * liệu buổi kế tiếp). Chặn ở đây: DRAFT/REJECTED có sẵn thì SỬA ĐÈ (idempotent, cùng hành vi với
     * {@link #updateComment}) thay vì tạo mới; PENDING/APPROVED có sẵn thì báo lỗi rõ ràng thay vì âm
     * thầm tạo bản ghi trùng.
     */
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
                .orElseThrow(() -> new ResourceNotFoundException("error.studentComment.studentNotFoundById", new Object[]{request.studentId()}, "Không tìm thấy học sinh id=" + request.studentId()));

        StudentComment existing = studentCommentRepository
                .findByClassSessionIdAndStudentId(classSession.getId(), student.getId()).orElse(null);
        if (existing != null && existing.getStatus() != StudentComment.Status.DRAFT
                && existing.getStatus() != StudentComment.Status.REJECTED) {
            throw new StudentCommentNotEditableException(
                    "error.studentCommentNotEditable.alreadyExists", new Object[]{student.getUser().getFullName(), existing.getStatus()},
                    "Học sinh " + student.getUser().getFullName() + " đã có nhận xét cho buổi học này (trạng thái: "
                            + existing.getStatus() + ") — không thể tạo thêm.");
        }

        StudentComment comment = existing != null ? existing : new StudentComment();
        if (existing != null) {
            comment.setApprovalFlow(null);
        }
        comment.setStudent(student);
        comment.setSchoolClass(schoolClass);
        comment.setTeacher(actor);
        comment.setCommentType(StudentComment.CommentType.DAILY);
        comment.setClassSession(classSession);
        comment.setAcademicYear(schoolClass.getAcademicYear());
        comment.setCommentDate(request.commentDate());

        applyContent(comment, request.content(), request.structuredContent(), request.severity(), request.isWarning(),
                request.attitude(), request.homeworkPreviousScore(), request.homeworkPreviousSpeakingScore(),
                request.homeworkPreviousReadingScore(), request.homeworkPreviousWritingScore(),
                request.homeworkNext(), request.homeworkNextReading(), request.homeworkNextWriting(), request.note());
        comment.setStatus(StudentComment.Status.DRAFT);
        comment = studentCommentRepository.save(comment);
        writeHistory(comment, actor, existing != null ? StudentCommentHistory.Action.UPDATED : StudentCommentHistory.Action.CREATED);
        return toResponse(comment);
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — "Lưu nháp" CẢ LỚP trong 1
     * request/1 transaction DUY NHẤT, thay vì FE gọi lặp lại {@link #writeComment}/{@link
     * #updateComment} cho TỪNG học sinh (N request HTTP thật, mỗi request tự chạy lại
     * requireCanWriteDailyComment + tự truy vấn "buổi trước" riêng — chậm rõ rệt trên môi trường
     * deploy có độ trễ mạng, xem Javadoc {@link SaveDraftCommentsRequest}). Mirror ĐÚNG logic
     * find-or-create + rào chặn trùng của writeComment (không tạo 2 bản ghi DAILY trùng
     * classSession+student, PENDING/APPROVED có sẵn thì báo lỗi rõ ràng) — chỉ khác là mọi bước dùng
     * chung 1 lần tra cứu rào/actor/schoolClass/classSession, và tra "học sinh"/"bản ghi đã có" bằng
     * 1 truy vấn BULK cho cả lô thay vì lặp lại theo từng dòng.
     *
     * QUAN TRỌNG — KHÔNG all-or-nothing: mỗi dòng trước đây là 1 request {@code Promise.allSettled}
     * ĐỘC LẬP ở FE — 1 học sinh bị khoá giữa chừng (VD Quản lý điểm trường vừa duyệt/từ chối đúng lúc
     * giáo viên đang gõ) không được chặn các dòng KHÁC lưu thành công. Gộp thành 1 transaction vẫn
     * phải giữ đúng tinh thần đó: lỗi ở 1 dòng chỉ đưa dòng đó vào {@code skipped}, KHÔNG ném exception
     * làm rollback cả batch — chỉ tiền kiểm tra dùng chung ở đầu hàm (lớp bị hủy, hết hạn sửa, không
     * đúng quyền...) mới chặn toàn bộ, vì đó là điều kiện chung cho CẢ buổi học, không phải riêng 1
     * học sinh nào.
     */
    @Transactional
    public SaveDraftCommentsResponse saveDraftBatch(Long classId, Long classSessionId, SaveDraftCommentsRequest request, Long actorUserId) {
        SchoolClass schoolClass = getClassOrThrow(classId);
        if (schoolClass.getStatus() == SchoolClass.Status.CANCELLED) {
            throw new IllegalStateException("Lớp học \"" + schoolClass.getName() + "\" đã bị HỦY — không thể viết nhận xét.");
        }
        User actor = getUserOrThrow(actorUserId);
        ClassSession classSession = getClassSessionOrThrow(classSessionId);
        requireCanWriteDailyComment(classSession, actorUserId);

        List<Long> studentIds = request.rows().stream().map(SaveDraftCommentsRequest.Row::studentId).toList();
        Map<Long, Student> studentsById = studentRepository.findByIdInAndDeletedAtIsNull(studentIds).stream()
                .collect(java.util.stream.Collectors.toMap(Student::getId, s -> s));
        Map<Long, StudentComment> existingByStudentId = studentCommentRepository.findByClassSessionId(classSession.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(c -> c.getStudent().getId(), c -> c, (a, b) -> a));

        List<StudentComment> toSave = new ArrayList<>();
        Map<Long, StudentCommentHistory.Action> actionByStudentId = new HashMap<>();
        List<SaveDraftCommentsResponse.SkippedRow> skipped = new ArrayList<>();
        for (SaveDraftCommentsRequest.Row row : request.rows()) {
            try {
                Student student = studentsById.get(row.studentId());
                if (student == null) {
                    throw new ResourceNotFoundException("error.studentComment.studentNotFoundById", new Object[]{row.studentId()}, "Không tìm thấy học sinh id=" + row.studentId());
                }
                StudentComment existing = existingByStudentId.get(student.getId());
                if (existing != null && existing.getStatus() != StudentComment.Status.DRAFT
                        && existing.getStatus() != StudentComment.Status.REJECTED) {
                    throw new StudentCommentNotEditableException(
                            "error.studentCommentNotEditable.alreadyExists", new Object[]{student.getUser().getFullName(), existing.getStatus()},
                            "Học sinh " + student.getUser().getFullName() + " đã có nhận xét cho buổi học này (trạng thái: "
                                    + existing.getStatus() + ") — không thể tạo thêm.");
                }
                StudentComment comment = existing != null ? existing : new StudentComment();
                if (existing != null) {
                    comment.setApprovalFlow(null);
                }
                comment.setStudent(student);
                comment.setSchoolClass(schoolClass);
                comment.setTeacher(actor);
                comment.setCommentType(StudentComment.CommentType.DAILY);
                comment.setClassSession(classSession);
                comment.setAcademicYear(schoolClass.getAcademicYear());
                comment.setCommentDate(request.commentDate());
                applyContent(comment, row.content(), row.structuredContent(), row.severity(), row.isWarning(),
                        row.attitude(), row.homeworkPreviousScore(), row.homeworkPreviousSpeakingScore(),
                        row.homeworkPreviousReadingScore(), row.homeworkPreviousWritingScore(),
                        row.homeworkNext(), row.homeworkNextReading(), row.homeworkNextWriting(), row.note());
                comment.setStatus(StudentComment.Status.DRAFT);
                actionByStudentId.put(student.getId(), existing != null ? StudentCommentHistory.Action.UPDATED : StudentCommentHistory.Action.CREATED);
                toSave.add(comment);
            } catch (RuntimeException ex) {
                skipped.add(new SaveDraftCommentsResponse.SkippedRow(row.studentId(), ex.getMessage()));
            }
        }

        List<StudentComment> saved = studentCommentRepository.saveAll(toSave);
        Map<Long, Map<Long, StudentComment>> previousCache = previousCommentsByClassSessionAndStudent(saved);
        saved.forEach(c -> writeHistory(c, actor, actionByStudentId.get(c.getStudent().getId()), previousCache));
        return new SaveDraftCommentsResponse(saved.stream().map(this::toResponse).toList(), skipped);
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
                    "error.studentCommentNotEditable.notDraftOrRejected", new Object[]{comment.getStatus()},
                    "Nhận xét này đang ở trạng thái " + comment.getStatus() + " — chỉ sửa được khi Nháp (DRAFT) hoặc Bị từ chối (REJECTED).");
        }

        // 2026-09-12: BTVN online (Exercise/ReviewVideoSet) không còn ở DTO này nữa — sửa Nhận xét ở
        // đây KHÔNG đụng/ghi đè homeworkNextGrammarBatch/homeworkNextReviewVideoAssignment/... (dù
        // comment này đang REJECTED và đã từng có bản giao thật từ lần Gửi trước) — giao BTVN online
        // tách hẳn qua applyHomeworkToClass, xem Javadoc class.
        comment.setApprovalFlow(null);
        applyContent(comment, request.content(), request.structuredContent(), request.severity(), request.isWarning(),
                request.attitude(), request.homeworkPreviousScore(), request.homeworkPreviousSpeakingScore(),
                request.homeworkPreviousReadingScore(), request.homeworkPreviousWritingScore(),
                request.homeworkNext(), request.homeworkNextReading(), request.homeworkNextWriting(), request.note());
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
                .orElseThrow(() -> new ResourceNotFoundException("error.studentComment.userHasNoStudentProfile", new Object[]{actorUserId}, "Tài khoản id=" + actorUserId + " không có hồ sơ học sinh."));
    }

    /** Đã TỪNG ghi danh lớp này (kể cả đã chuyển lớp) — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29. */
    private void requireEnrolled(Long studentId, Long classId) {
        if (!classEnrollmentRepository.existsByStudentIdAndSchoolClassId(studentId, classId)) {
            throw new ResourceNotFoundException("error.studentComment.classNotFoundById", new Object[]{classId}, "Không tìm thấy lớp học id=" + classId);
        }
    }

    /**
     * Main Flow bước 4-5: Gửi (submit).
     *
     * Bổ sung 2026-09-12 (đã xác nhận với người dùng) — method này KHÔNG còn đụng gì tới BTVN buổi
     * sau nữa (đã THAY THẾ toàn bộ đoạn V127 mô tả bên dưới, giữ lại làm dấu vết lịch sử quyết
     * định): trước đây đây là điểm materialize {@link ExerciseAssignment}/{@link ReviewVideoAssignment}
     * DUY NHẤT (gọi resolveGrammarExerciseHomework/resolveVideoHomework/resolveReadingExerciseHomework/
     * resolveWritingExerciseHomework), nhưng vì các hàm đó giao CẢ LỚP
     * ({@code target_student_ids=NULL}) trong khi chỉ nhận xét CÓ NỘI DUNG mới lọt tới đây (FE lọc
     * theo content ở {@code handleSend}), học sinh không viết Nhận xét vẫn thực nhận được bài (giao cả
     * lớp) nhưng dòng {@code StudentComment} của chính học sinh đó không bao giờ được gán FK BTVN —
     * xem lại lịch sử nhận xét của học sinh này thì "mất" thông tin BTVN dù đã nhận bài thật. Giao BTVN
     * online giờ tách hẳn sang endpoint riêng {@code POST .../comments/apply-homework}
     * ({@link #applyHomeworkToClass}), có popup xác nhận ở FE, và tự đảm bảo MỌI học sinh ACTIVE của
     * lớp (kể cả chưa viết Nhận xét) đều có dòng StudentComment phản ánh đúng BTVN đã giao. Method này
     * giờ CHỈ còn 1 việc: validate content/lessonContent rồi chuyển DRAFT→PENDING (tạo ApprovalFlow).
     *
     * ~~V127 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — ĐIỂM GIAO BÀI THẬT SỰ cho
     * "BTVN buổi sau": trước V127, giao bài xảy ra ngay lúc Lưu nháp
     * (writeComment/updateComment/importRow gọi thẳng resolveExerciseHomework/resolveVideoHomework);
     * giờ 3 method đó chỉ validate + lưu tạm lựa chọn vào pendingHomeworkNext* — CHỈ Ở ĐÂY mới thật sự
     * materialize thành ExerciseAssignment/ReviewVideoAssignment.~~ ĐÃ THAY THẾ bởi
     * {@link #applyHomeworkToClass} (xem trên).
     */
    @Transactional
    public List<StudentCommentResponse> submitComments(Long classId, SubmitCommentsRequest request, Long actorUserId) {
        requireAssignedTeacher(classId, actorUserId);
        User actor = getUserOrThrow(actorUserId);
        List<StudentComment> comments = studentCommentRepository.findAllById(request.commentIds());
        if (comments.size() != request.commentIds().size()) {
            throw new ResourceNotFoundException("error.studentComment.commentIdsNotFound", new Object[]{}, "Có nhận xét không tồn tại trong danh sách commentIds.");
        }

        // Vòng 1: validate toàn bộ, không side effect nào (xem Javadoc trên).
        for (StudentComment comment : comments) {
            if (!comment.getSchoolClass().getId().equals(classId)) {
                throw new ResourceNotFoundException("error.studentComment.commentNotInClass", new Object[]{comment.getId(), classId}, "Nhận xét id=" + comment.getId() + " không thuộc lớp id=" + classId);
            }
            if (comment.getStatus() != StudentComment.Status.DRAFT) {
                throw new StudentCommentNotEditableException(
                        "error.studentCommentNotEditable.notDraftForSubmit", new Object[]{comment.getStatus()},
                        "Nhận xét này đang ở trạng thái " + comment.getStatus() + " — chỉ gửi duyệt được khi còn Nháp (DRAFT).");
            }
            // Bổ sung ngoài SDD gốc, xác nhận 2026-08-17 — content không còn @NotBlank ở DTO (lưu nháp
            // được mà chưa cần Nhận xét), nên đây là chốt chặn DUY NHẤT còn lại đảm bảo Nhận xét ở
            // trạng thái Chờ duyệt/Đã duyệt (Phụ huynh xem được) luôn có nội dung.
            if (comment.getContent() == null || comment.getContent().isBlank()) {
                throw new MissingCommentContentException(
                        "error.missingCommentContent.default", new Object[]{comment.getStudent().getUser().getFullName()},
                        "Học sinh " + comment.getStudent().getUser().getFullName()
                                + " chưa có nội dung Nhận xét — cần nhập Nhận xét trước khi gửi duyệt.");
            }
            if (comment.getClassSession().getLessonContent() == null || comment.getClassSession().getLessonContent().isBlank()) {
                throw new MissingLessonContentException("error.missingLessonContent.default", new Object[]{}, "Buổi học này chưa điền bài học hôm nay — không thể gửi duyệt.");
            }
        }

        UUID batchId = comments.size() > 1 ? UUID.randomUUID() : null;
        OffsetDateTime now = OffsetDateTime.now();

        // 2026-09-12: không còn "Vòng 2" giao bài — BTVN online đã tách sang applyHomeworkToClass. Ở
        // đây chỉ còn chuyển trạng thái + tạo ApprovalFlow cho từng nhận xét đã validate ở Vòng 1.
        for (StudentComment comment : comments) {
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
        // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — fix N+1 thật (phản hồi thực tế
        // test trên deploy: Gửi nhận xét cả lớp bị chậm) — truy vấn "nhận xét buổi trước" 1 LẦN cho cả
        // lô thay vì để writeHistory tự truy vấn lại cho TỪNG dòng, xem Javadoc previousCommentsByClassSessionAndStudent.
        Map<Long, Map<Long, StudentComment>> previousCache = previousCommentsByClassSessionAndStudent(saved);
        saved.forEach(c -> writeHistory(c, actor, StudentCommentHistory.Action.UPDATED, previousCache));
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
            throw new ResourceNotFoundException("error.studentComment.commentIdsNotFound", new Object[]{}, "Có nhận xét không tồn tại trong danh sách commentIds.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        for (StudentComment comment : comments) {
            requireSiteManagerForSite(comment.getSchoolClass().getSite().getId(), actorUserId);
            if (comment.getStatus() != StudentComment.Status.PENDING) {
                throw new ApprovalAlreadyDecidedException(
                        "error.approvalAlreadyDecided.comment", new Object[]{comment.getStatus()},
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
        // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — mirror fix N+1 ở submitComments,
        // áp dụng cho "duyệt theo lô" (site manager có thể duyệt gộp nhiều buổi/lớp cùng lúc từ hàng chờ).
        Map<Long, Map<Long, StudentComment>> previousCache = previousCommentsByClassSessionAndStudent(saved);
        saved.forEach(c -> writeHistory(c, actor, StudentCommentHistory.Action.UPDATED, previousCache));
        if (decision == ApprovalFlow.Decision.REJECTED) {
            saved.forEach(this::notifyTeacherRejected);
        } else {
            // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-12: cảnh báo thái độ học
            // tập chỉ tính trên nhận xét ĐÃ DUYỆT — xem StudentAttitudeAlertTrackingService.
            saved.forEach(attitudeAlertTrackingService::evaluateAndNotify);
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
            throw new NotSiteManagerForSiteException("error.notSiteManagerForSite.noCommentApprovalPermission", new Object[]{}, "Tài khoản không có quyền duyệt nhận xét.");
        }
        requireSiteManagerForSite(comment.getSchoolClass().getSite().getId(), actorUserId);

        if (comment.getStatus() != StudentComment.Status.PENDING) {
            throw new StudentCommentNotEditableException(
                    "error.studentCommentNotEditable.notPendingForManagerEdit", new Object[]{comment.getStatus()},
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
                    "error.studentCommentNotEditable.sessionMetaLocked", new Object[]{},
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
    /**
     * V146 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23) — % TỰ ĐỘNG "BTVN buổi
     * trước" cho TOÀN BỘ học sinh ACTIVE của lớp, tính trực tiếp từ buổi TRƯỚC (không cần buổi hiện
     * tại đã có StudentComment nào) — dùng để bảng Nhận xét hàng ngày hiện được % tự động ngay cả khi
     * giáo viên chưa Lưu nháp/Gửi lần nào cho buổi đang xem (xem AutoProgressPreviewResponse). Cùng
     * quyền với buildTemplate (chỉ GV được phân công lớp hoặc actor có academic.comment.approve).
     */
    @Transactional(readOnly = true)
    public List<AutoProgressPreviewResponse> previewAutoProgress(Long classSessionId, Long actorUserId) {
        ClassSession classSession = getClassSessionOrThrow(classSessionId);
        requireCanWriteDailyComment(classSession, actorUserId);
        Long classId = classSession.getSchoolClass().getId();
        List<ClassEnrollment> enrollments = classEnrollmentRepository
                .findBySchoolClassIdAndStatus(classId, ClassEnrollment.Status.ACTIVE);
        return enrollments.stream()
                .map(enrollment -> {
                    Long studentId = enrollment.getStudent().getId();
                    StudentComment previous = previousComment(classSession, studentId);
                    return new AutoProgressPreviewResponse(studentId,
                            grammarPreviousProgressLabel(previous),
                            videoPreviousProgressLabel(previous),
                            readingPreviousProgressLabel(previous),
                            writingPreviousProgressLabel(previous));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] buildTemplate(Long classSessionId, Long actorUserId) {
        ClassSession classSession = getClassSessionOrThrow(classSessionId);
        requireCanWriteDailyComment(classSession, actorUserId);
        Long classId = classSession.getSchoolClass().getId();
        ClassSession.TeacherType sessionTeacherType = classSession.getTeacherType();

        Map<Long, AttendanceMark.Status> attendanceByStudent = currentAttendanceByStudent(classSessionId);
        List<ClassEnrollment> enrollments = classEnrollmentRepository
                .findBySchoolClassIdAndStatus(classId, ClassEnrollment.Status.ACTIVE);

        String grammarLabelText = grammarChannelLabel(sessionTeacherType);
        String videoLabelText = videoChannelLabel(sessionTeacherType);
        HomeworkColumns hc = HomeworkColumns.of(sessionTeacherType);

        // Nhãn cột con chỉ còn tên kênh (không lặp lại tên nhóm) — header gộp ở (các) dòng trên
        // (headerGroups/headerSubGroups bên dưới) đã nói rõ nhóm, mirror đúng cấu trúc header đang dùng
        // ở bảng UI web (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06, mở rộng V130).
        List<String> headers;
        List<ExcelExportHelper.HeaderGroup> headerGroups;
        List<ExcelExportHelper.HeaderGroup> headerSubGroups = null;
        if (hc.vietnamese) {
            headers = List.of("Ngày*", "Mã học viên*", "Họ và tên", "Ngày sinh", "Tên bài học",
                    "Tên giáo viên giảng dạy", "Điểm danh*",
                    "Reading", "Writing", "Reading", "Writing", VIETNAMESE_ONLINE_GRAMMAR_LABEL, VIETNAMESE_ONLINE_VIDEO_LABEL,
                    "Reading", "Writing", "Reading", "Writing", VIETNAMESE_ONLINE_GRAMMAR_LABEL, VIETNAMESE_ONLINE_VIDEO_LABEL,
                    "Hạn nộp bài", "Thái độ học tập", "Nhận xét học sinh*", "Ghi chú");
            headerGroups = List.of(
                    new ExcelExportHelper.HeaderGroup("BTVN buổi trước", hc.previousReadingOrOffline, hc.previousOnlineVideo),
                    new ExcelExportHelper.HeaderGroup("BTVN buổi này", hc.nextReadingOrOffline, hc.nextOnlineVideo));
            headerSubGroups = List.of(
                    new ExcelExportHelper.HeaderGroup("Offline", hc.previousReadingOrOffline, hc.previousWriting),
                    new ExcelExportHelper.HeaderGroup("Online", hc.previousOnlineReading, hc.previousOnlineVideo),
                    new ExcelExportHelper.HeaderGroup("Offline", hc.nextReadingOrOffline, hc.nextWriting),
                    new ExcelExportHelper.HeaderGroup("Online", hc.nextOnlineReading, hc.nextOnlineVideo));
        } else {
            headers = List.of("Ngày*", "Mã học viên*", "Họ và tên", "Ngày sinh", "Tên bài học",
                    "Tên giáo viên giảng dạy", "Điểm danh*",
                    "Offline", grammarLabelText, videoLabelText,
                    "Offline", grammarLabelText, videoLabelText,
                    "Hạn nộp bài", "Thái độ học tập", "Nhận xét học sinh*", "Ghi chú");
            // V130: "BTVN offline" + "BTVN online" cũ (2 header cha tách rời) gộp thành 1 header "BTVN" —
            // thuần cosmetic cho buổi FOREIGN (vẫn 3 cột lá y hệt, không đổi field/vị trí cột).
            headerGroups = List.of(
                    new ExcelExportHelper.HeaderGroup("BTVN buổi trước", hc.previousReadingOrOffline, hc.previousOnlineVideo),
                    new ExcelExportHelper.HeaderGroup("BTVN buổi này", hc.nextReadingOrOffline, hc.nextOnlineVideo));
        }

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
            if (hc.vietnamese) {
                row.add(resolvedReadingPrevious(existing));
                row.add(resolvedWritingPrevious(existing));
                row.add(readingPreviousProgressLabel(previous));
                row.add(writingPreviousProgressLabel(previous));
                row.add(resolvedGrammarPrevious(existing, previous));
                row.add(resolvedSpeakingPrevious(existing, previous));
                row.add(resolvedHomeworkNextReading(existing));
                row.add(resolvedHomeworkNextWriting(existing));
                row.add(resolvedHomeworkOnlineReading(existing));
                row.add(resolvedHomeworkOnlineWriting(existing));
            } else {
                row.add(resolvedOfflinePrevious(previous));
                row.add(resolvedGrammarPrevious(existing, previous));
                row.add(resolvedSpeakingPrevious(existing, previous));
                row.add(resolvedHomeworkOffline(existing));
            }
            row.add(resolvedHomeworkOnlineGrammar(existing));
            row.add(resolvedHomeworkOnlineVideo(existing));
            row.add(resolvedDueAt(classSession, existing));
            row.add(existing == null || existing.getAttitude() == null ? null : attitudeLabel(existing.getAttitude()));
            row.add(existing == null ? null : existing.getContent());
            row.add(existing == null ? null : existing.getNote());
            rows.add(row);
        }
        Map<Integer, List<String>> dropdowns = new LinkedHashMap<>();
        dropdowns.put(COL_ATTENDANCE, Arrays.stream(AttendanceMark.Status.values()).map(this::attendanceLabel).toList());
        dropdowns.put(hc.attitude, Arrays.stream(StudentComment.Attitude.values()).map(this::attitudeLabel).toList());
        // 2026-09-12 (đã xác nhận với người dùng): bỏ hẳn dropdown cho các cột "Online" (Ngữ pháp/Video/
        // Reading/Writing) — Excel không còn giao BTVN online được nữa (chỉ hiển thị tham khảo giá trị
        // đã áp dụng qua "Áp dụng cho cả lớp" trên web), xem Javadoc applyHomeworkToClass/importRow.
        // Sheet "Hướng dẫn" (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06) — thống nhất
        // định dạng dữ liệu trước khi nhập lại lên hệ thống, đặc biệt cột Ngày (dễ bị Excel tự đổi định
        // dạng ngày giờ theo cấu hình máy khi gõ tay).
        List<String> notes = new ArrayList<>(List.of(
                "LƯU Ý TRƯỚC KHI NHẬP LẠI FILE NÀY LÊN HỆ THỐNG:",
                "",
                "1. Cột \"Ngày*\" (" + colLetter(COL_DATE) + "): bắt buộc đúng định dạng yyyy-MM-dd (VD: 2026-08-10), đúng bằng ngày của buổi học đang chọn — sai định dạng hoặc khác ngày sẽ bị từ chối cả dòng.",
                "2. Cột \"Hạn nộp bài\" (" + colLetter(hc.dueDate) + "): CHỈ hiển thị tham khảo (hạn nộp BTVN online đã áp dụng, nếu có) — sửa/xoá cột này KHÔNG được lưu lại khi nhập lên.",
                "3. Cột \"Tên bài học\" (" + colLetter(COL_LESSON_CONTENT) + ") và \"Tên giáo viên giảng dạy\" (" + colLetter(COL_TEACHER_NAME) + "): phải giống nhau giữa mọi học sinh trong lớp (1 giá trị dùng chung cho cả buổi) — khác nhau sẽ bị từ chối CẢ FILE.",
                "4. Nhóm \"BTVN\" (" + colLetter(hc.nextReadingOrOffline) + "-" + colLetter(hc.nextOnlineVideo) + "): chỉ CỘT OFFLINE (chữ tự do) là điền/sửa được — các cột \"Online\" (Ngữ pháp/Video/Reading/Writing) CHỈ hiển thị tham khảo BTVN online đã giao qua \"Áp dụng cho cả lớp\" trên web, sửa/dán vào đây sẽ KHÔNG được lưu.",
                "5. Cột \"Họ và tên\" (" + colLetter(COL_FULL_NAME) + ") và \"Ngày sinh\" (" + colLetter(COL_DOB) + "): chỉ hiển thị để đối chiếu — sửa các cột này KHÔNG được lưu lại khi nhập lên.",
                "6. Cột \"Điểm danh*\" (" + colLetter(COL_ATTENDANCE) + ") và \"Thái độ học tập\" (" + colLetter(hc.attitude) + "): nên chọn đúng trong dropdown cho chắc chắn, dù hệ thống có chấp nhận thêm vài biến thể viết khác."
        ));
        if (hc.vietnamese) {
            notes.add("7. 2 cột con \"Reading\"/\"Writing\" nhóm \"Offline\" của \"BTVN buổi trước\": điểm % giáo viên tự chấm tay — điền tự do (VD \"80%\"), không có dropdown.");
        } else {
            notes.add("7. Cột con \"Offline\" trong nhóm \"BTVN buổi trước\": chỉ hiển thị để đối chiếu (BTVN offline buổi trước đã giao) — sửa cột này KHÔNG được lưu lại khi nhập lên.");
        }
        return ExcelExportHelper.buildWorkbook("Nhận xét", headers, rows, notes, dropdowns, headerGroups, headerSubGroups);
    }

    /** Nhãn cột con "Online" nhóm BTVN buổi trước/BTVN — CHỈ dùng riêng cho Nhận xét học viên buổi teacherType=VIETNAMESE (V130, đã xác nhận với người dùng — KHÔNG đổi grammarChannelLabel/videoChannelLabel dùng chung ở Soạn & giao đề/Kho Video Ôn tập). Field/chức năng bên dưới (chọn Exercise/Video) giữ nguyên, chỉ đổi nhãn hiển thị. */
    private static final String VIETNAMESE_ONLINE_GRAMMAR_LABEL = "Từ vựng + Ngữ pháp";
    private static final String VIETNAMESE_ONLINE_VIDEO_LABEL = "Video TKN";

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
     * "BTVN buổi trước — Offline" (bổ sung ngoài SDD gốc, đã xác nhận với
     * người dùng 2026-08-06) — chỉ có giá trị khi buổi TRƯỚC giao Offline
     * (không có ExerciseAssignment) — mirror resolvedHomeworkOffline nhưng
     * đọc từ {@code previous} thay vì {@code existing}. Loại trừ với
     * resolvedGrammarPrevious (đúng 1 trong 2 khác null, không cả hai).
     */
    private String resolvedOfflinePrevious(StudentComment previous) {
        if (previous == null) {
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

    /**
     * "BTVN offline" — lấy giá trị BTVN offline đã nhập (homeworkNext). Bổ sung ngoài SDD gốc 2026-08-18:
     * giao đồng thời với BTVN online, không còn bị loại trừ hay ẩn đi khi đã chọn BTVN online.
     */
    private String resolvedHomeworkOffline(StudentComment existing) {
        if (existing == null) {
            return null;
        }
        return existing.getHomeworkNext();
    }

    /** "BTVN buổi trước — Offline — Reading" (V130) — buổi teacherType=VIETNAMESE, điểm % chấm tay, không có fallback tự động (bài giấy, BE không track được). */
    private String resolvedReadingPrevious(StudentComment existing) {
        return existing == null ? null : existing.getHomeworkPreviousReadingScore();
    }

    /** Mirror {@link #resolvedReadingPrevious} cho kỹ năng Writing (V130). */
    private String resolvedWritingPrevious(StudentComment existing) {
        return existing == null ? null : existing.getHomeworkPreviousWritingScore();
    }

    /** "BTVN — Offline — Reading" (V130) — buổi teacherType=VIETNAMESE, mirror {@link #resolvedHomeworkOffline} nhưng đọc homeworkNextReading. */
    private String resolvedHomeworkNextReading(StudentComment existing) {
        return existing == null ? null : existing.getHomeworkNextReading();
    }

    /** Mirror {@link #resolvedHomeworkNextReading} cho kỹ năng Writing (V130). */
    private String resolvedHomeworkNextWriting(StudentComment existing) {
        return existing == null ? null : existing.getHomeworkNextWriting();
    }

    /**
     * "BTVN online — {Ngữ pháp/Bài nghe}" — chỉ có giá trị khi ĐÃ giao/chọn online. V127: fallback đọc
     * pendingHomeworkNextExerciseId (chưa Gửi) khi chưa có bản giao thật.
     */
    private String resolvedHomeworkOnlineGrammar(StudentComment existing) {
        if (existing == null) {
            return null;
        }
        if (existing.getHomeworkNextGrammarBatch() != null) {
            return batchLabel(existing.getHomeworkNextGrammarBatch());
        }
        if (existing.getPendingHomeworkNextGrammarExamId() != null) {
            return examRepository.findByIdAndDeletedAtIsNull(existing.getPendingHomeworkNextGrammarExamId())
                    .map(exam -> examSkillGroupLabel(exam, grammarChannelSkillCategory(existing.getClassSession().getTeacherType()))).orElse(null);
        }
        return null;
    }

    /** V127: mirror resolvedHomeworkOnlineGrammar cho kênh Video Ôn tập — thay đoạn tra trực tiếp existing.getHomeworkNextReviewVideoAssignment() inline cũ ở buildTemplate. */
    private String resolvedHomeworkOnlineVideo(StudentComment existing) {
        if (existing == null) {
            return null;
        }
        if (existing.getHomeworkNextReviewVideoAssignment() != null) {
            return videoLabel(existing.getHomeworkNextReviewVideoAssignment().getReviewVideoSet());
        }
        if (existing.getPendingHomeworkNextReviewVideoSetId() != null) {
            return reviewVideoSetRepository.findById(existing.getPendingHomeworkNextReviewVideoSetId()).map(this::videoLabel).orElse(null);
        }
        return null;
    }

    /** V137: mirror resolvedHomeworkOnlineGrammar cho kênh Reading — chỉ có ý nghĩa khi buổi teacherType=VIETNAMESE. */
    private String resolvedHomeworkOnlineReading(StudentComment existing) {
        if (existing == null) {
            return null;
        }
        if (existing.getHomeworkNextReadingBatch() != null) {
            return batchLabel(existing.getHomeworkNextReadingBatch());
        }
        if (existing.getPendingHomeworkNextReadingExamId() != null) {
            return examRepository.findByIdAndDeletedAtIsNull(existing.getPendingHomeworkNextReadingExamId())
                    .map(exam -> examSkillGroupLabel(exam, Exercise.SkillCategory.READING)).orElse(null);
        }
        return null;
    }

    /** Mirror {@link #resolvedHomeworkOnlineReading} cho kỹ năng Writing (V137). */
    private String resolvedHomeworkOnlineWriting(StudentComment existing) {
        if (existing == null) {
            return null;
        }
        if (existing.getHomeworkNextWritingBatch() != null) {
            return batchLabel(existing.getHomeworkNextWritingBatch());
        }
        if (existing.getPendingHomeworkNextWritingExamId() != null) {
            return examRepository.findByIdAndDeletedAtIsNull(existing.getPendingHomeworkNextWritingExamId())
                    .map(exam -> examSkillGroupLabel(exam, Exercise.SkillCategory.WRITING)).orElse(null);
        }
        return null;
    }

    /**
     * "Hạn nộp bài" (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * 2026-08-06) — lấy dueAt từ bản giao Ngữ pháp/Bài nghe hoặc Video (cái
     * nào khác null), format yyyy-MM-dd HH:mm theo múi giờ hệ thống.
     *
     * V127: dùng {@link #effectiveDueAt} (thay vì đọc thẳng 2 assignment) — hiện được cả hạn nộp SẼ
     * giao khi dòng còn DRAFT/REJECTED (chỉ có pendingHomeworkNextDueDate, chưa có bản giao thật).
     */
    private String resolvedDueAt(ClassSession session, StudentComment existing) {
        if (existing == null) {
            return null;
        }
        OffsetDateTime dueAt = effectiveDueAt(session, existing);
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
            // 2026-09-12: "Hạn nộp bài" trong Excel không còn feed vào đâu — Excel không còn giao BTVN
            // online (xem Javadoc importRow/applyHomeworkToClass), nên không còn đọc customDueDate ở đây.

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
                            parsed.homeworkPrevious(), parsed.content(), parsed.homeworkNext(), parsed.note(),
                            parsed.homeworkPreviousSpeaking(), parsed.homeworkPreviousReading(),
                            parsed.homeworkPreviousWriting(), parsed.homeworkNextReading(),
                            parsed.homeworkNextWriting(), actor);
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
                            && parsed.note() == null && parsed.homeworkPreviousSpeaking() == null
                            && parsed.homeworkPreviousReading() == null && parsed.homeworkPreviousWriting() == null
                            && parsed.homeworkNextReading() == null && parsed.homeworkNextWriting() == null;
                    if (!(absent && allBlank)) {
                        if (parsed.content() == null || parsed.content().isBlank()) {
                            throw new IllegalArgumentException(
                                    "Thiếu nhận xét (cột " + colLetter(HomeworkColumns.of(classSession.getTeacherType()).content) + ") — bắt buộc trừ khi học sinh vắng/có phép.");
                        }
                        previewRows.add(new DailyCommentImportPreviewRow(
                                parsed.student().getId(), parsed.attitude(), parsed.homeworkPrevious(),
                                parsed.homeworkPreviousSpeaking(), parsed.homeworkPreviousReading(),
                                parsed.homeworkPreviousWriting(), parsed.content(), parsed.homeworkNext(),
                                parsed.homeworkNextReading(), parsed.homeworkNextWriting(),
                                parsed.note()));
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
        ClassSession.TeacherType sessionTeacherType = classSession.getTeacherType();
        HomeworkColumns hc = HomeworkColumns.of(sessionTeacherType);
        List<Map<String, Object>> errors = new ArrayList<>();
        try (InputStream inputStream = file.getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getRow(0) == null) {
                throw new ImportValidationFailed("File rỗng hoặc thiếu dòng tiêu đề.");
            }
            DataFormatter formatter = new DataFormatter();

            // File mẫu có 2 DÒNG header (buổi FOREIGN: nhóm "BTVN buổi trước"/"BTVN" merge + dòng tên cột
            // con) hoặc 3 DÒNG header (buổi VIETNAMESE, V130: thêm 1 dòng Offline/Online giữa) — mirror
            // đúng headerRowIndex+1 dùng khi buildTemplate() gọi ExcelExportHelper.buildWorkbook. Dữ liệu
            // học sinh bắt đầu ngay sau dòng header cuối (bổ sung ngoài SDD gốc, đã xác nhận với người
            // dùng 2026-08-06, mở rộng 2026-08-21).
            int dataStartRowIndex = hc.vietnamese ? 3 : 2;
            List<ParsedRow> parsedRows = new ArrayList<>();
            int totalRows = 0;
            for (int rowIndex = dataStartRowIndex; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row, formatter, hc.columnCount)) {
                    continue;
                }
                totalRows++;
                try {
                    parsedRows.add(parseRow(row, formatter, rowIndex, classSession, hc));
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

    /**
     * Bổ sung 2026-09-12 (đã xác nhận với người dùng) — không còn field BTVN online (đã từng có
     * grammarExercise/videoSet/readingExercise/writingExercise, mirror {@link DailyCommentImportPreviewRow}):
     * Excel chỉ còn phục vụ Nhận xét/BTVN offline, xem Javadoc {@code StudentCommentService#applyHomeworkToClass}.
     */
    private record ParsedRow(int rowNumber, Student student, AttendanceMark.Status attendance, String attitude,
                              String homeworkPrevious, String content, String homeworkNext, String note,
                              String homeworkPreviousSpeaking,
                              /** V130 — chỉ khác null khi buổi teacherType=VIETNAMESE (xem HomeworkColumns). */
                              String homeworkPreviousReading, String homeworkPreviousWriting,
                              String homeworkNextReading, String homeworkNextWriting,
                              String lessonContent,
                              /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06. */
                              String teacherName,
                              /** Chỉ còn dùng để hiển thị tham khảo ở preview (2026-09-12) — không còn feed vào importRow. */
                              String dueDateText) {}

    private ParsedRow parseRow(Row row, DataFormatter formatter, int rowIndex, ClassSession classSession, HomeworkColumns hc) {
        String dateText = cell(row, formatter, COL_DATE);
        String studentCode = cell(row, formatter, COL_STUDENT_CODE);
        String lessonContentText = cell(row, formatter, COL_LESSON_CONTENT);
        String teacherNameText = cell(row, formatter, COL_TEACHER_NAME);
        String attendanceText = cell(row, formatter, COL_ATTENDANCE);
        String attitudeText = cell(row, formatter, hc.attitude);
        String homeworkPrevious = cell(row, formatter, hc.previousOnlineGrammar);
        String content = cell(row, formatter, hc.content);
        String dueDateText = cell(row, formatter, hc.dueDate);
        String note = cell(row, formatter, hc.note);
        String homeworkPreviousSpeaking = cell(row, formatter, hc.previousOnlineVideo);
        // V130: buổi VIETNAMESE đọc thêm 4 cột Reading/Writing (KHÔNG có "BTVN offline" gộp — đã tách
        // hẳn); buổi FOREIGN giữ nguyên đọc 1 cột "BTVN offline" gộp như trước, 4 field Reading/Writing
        // luôn null.
        String homeworkPreviousReading = hc.vietnamese ? cell(row, formatter, hc.previousReadingOrOffline) : null;
        String homeworkPreviousWriting = hc.vietnamese ? cell(row, formatter, hc.previousWriting) : null;
        String homeworkNextReading = hc.vietnamese ? cell(row, formatter, hc.nextReadingOrOffline) : null;
        String homeworkNextWriting = hc.vietnamese ? cell(row, formatter, hc.nextWriting) : null;
        String homeworkOfflineText = hc.vietnamese ? null : cell(row, formatter, hc.nextReadingOrOffline);

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
        String homeworkNext = blankToNull(homeworkOfflineText);
        return new ParsedRow(rowIndex, student, attendance, attitude,
                blankToNull(homeworkPrevious), blankToNull(content), homeworkNext, blankToNull(note),
                blankToNull(homeworkPreviousSpeaking),
                blankToNull(homeworkPreviousReading), blankToNull(homeworkPreviousWriting),
                blankToNull(homeworkNextReading), blankToNull(homeworkNextWriting),
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
                            String attitude, String homeworkPrevious, String content, String homeworkNext, String note,
                            String homeworkPreviousSpeaking, String homeworkPreviousReading,
                            String homeworkPreviousWriting, String homeworkNextReading, String homeworkNextWriting,
                            User actor) {
        boolean absent = attendance == AttendanceMark.Status.ABSENT || attendance == AttendanceMark.Status.EXCUSED;
        boolean allBlank = attitude == null && homeworkPrevious == null && content == null
                && homeworkNext == null && note == null
                && homeworkPreviousSpeaking == null && homeworkPreviousReading == null
                && homeworkPreviousWriting == null && homeworkNextReading == null && homeworkNextWriting == null;
        if (absent && allBlank) {
            return;
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Thiếu nhận xét (cột " + colLetter(HomeworkColumns.of(classSession.getTeacherType()).content) + ") — bắt buộc trừ khi học sinh vắng/có phép.");
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
                    "error.studentCommentNotEditable.importNotDraftOrRejected", new Object[]{student.getStudentCode(), comment.getStatus()},
                    "Nhận xét học sinh mã=" + student.getStudentCode() + " đang ở trạng thái "
                            + comment.getStatus() + " — chỉ sửa được khi DRAFT hoặc REJECTED.");
        }
        comment.setTeacher(actor);
        comment.setApprovalFlow(null);
        applyContent(comment, content, null, null, false,
                attitude, homeworkPrevious, homeworkPreviousSpeaking, homeworkPreviousReading,
                homeworkPreviousWriting, homeworkNext, homeworkNextReading, homeworkNextWriting, note);
        comment.setStatus(StudentComment.Status.DRAFT);
        comment = studentCommentRepository.save(comment);
        writeHistory(comment, actor, StudentCommentHistory.Action.UPDATED);
    }

    // ===================== Helpers =====================

    /**
     * Bổ sung 2026-09-12 (đã xác nhận với người dùng) — không còn nhận/ghi BTVN online
     * (Exercise/ReviewVideoSet, kể cả pendingHomeworkNext*) — chỉ còn nội dung Nhận xét + BTVN offline
     * (chữ tự do). Giao BTVN online tách hẳn sang {@link #applyHomeworkToClass}.
     */
    private void applyContent(StudentComment comment, String content, Map<String, Object> structuredContent,
                               String severity, boolean isWarning, String attitude, String homeworkPreviousScore,
                               String homeworkPreviousSpeakingScore, String homeworkPreviousReadingScore,
                               String homeworkPreviousWritingScore, String homeworkNext,
                               String homeworkNextReading, String homeworkNextWriting, String note) {
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
        // V130 — chỉ có ý nghĩa khi buổi teacherType=VIETNAMESE (FE/Excel không gửi thì null, không ảnh
        // hưởng buổi FOREIGN vẫn dùng homeworkPreviousScore/homeworkNext như trước).
        comment.setHomeworkPreviousReadingScore(homeworkPreviousReadingScore);
        comment.setHomeworkPreviousWritingScore(homeworkPreviousWritingScore);
        comment.setHomeworkNext(homeworkNext);
        comment.setHomeworkNextReading(homeworkNextReading);
        comment.setHomeworkNextWriting(homeworkNextWriting);
        comment.setNote(note);
    }

    // ===================== BTVN buổi sau — điểm giao bài (UC-21 mở rộng, V65) =====================
    // Bổ sung 2026-09-12 (đã xác nhận với người dùng) — TÁCH HẲN khỏi Viết/Gửi nhận xét: applyHomeworkToClass
    // (dưới đây) là điểm giao BTVN buổi sau DUY NHẤT, gọi từ nút "Áp dụng cho cả lớp" + popup xác nhận ở FE
    // (xem Javadoc submitComments để biết lý do tách). Vì giờ CHỈ CÒN 1 nơi ghi BTVN cho cả buổi (không còn
    // N request/học sinh độc lập như V65/V127 cũ), toàn bộ cơ chế "kiểm tra xung đột giữa các dòng cùng
    // buổi" (requireNoHomeworkConflict/requireNoDueDateConflict/requireNoLateSubmissionConflict + các hàm
    // effective*ChoiceId/Label) đã bỏ hẳn — không chỉ thừa mà còn SAI với model mới: method này áp dụng
    // cùng lúc cho MỌI dòng active của buổi (không có khái niệm "dòng đang sửa" để loại trừ khỏi kiểm tra),
    // nên đổi lựa chọn sẽ luôn "xung đột" với chính các dòng đang giữ lựa chọn CŨ — và một khi đã có học
    // sinh nào đó Gửi (PENDING/APPROVED, không sửa lại được nữa) thì rào này sẽ KHOÁ CỨNG MÃI MÃI, không
    // đổi BTVN được nữa. Lịch sử cơ chế cũ xem lại qua git nếu cần đối chiếu.

    /**
     * "Áp dụng cho cả lớp" — điểm giao BTVN buổi sau DUY NHẤT (UC-21 mở rộng). FE hiện popup xác nhận
     * TRƯỚC khi gọi method này (tránh giao nhầm cả lớp) — coi như Giáo viên đã xác nhận, giao thật
     * ngay, không qua bước "pending" nào nữa (cột {@code pendingHomeworkNext*} trên
     * {@link StudentComment} từ nay không còn ai ghi, giữ lại trong schema nhưng không dùng).
     *
     * Đảm bảo MỌI học sinh ACTIVE của lớp (kể cả chưa từng viết Nhận xét buổi này) đều có 1
     * {@link StudentComment} DRAFT (content rỗng nếu mới tạo) phản ánh đúng BTVN vừa giao — khắc phục
     * bug trước đây: chỉ nhận xét ĐÃ GỬI (có content, qua {@link #submitComments}) mới được gán FK
     * BTVN, trong khi cơ chế giao bài cũ luôn giao CẢ LỚP bất kể ai gửi — học sinh không viết Nhận xét
     * vẫn nhận bài thật nhưng xem lại lịch sử nhận xét của chính họ thì "mất" thông tin BTVN.
     *
     * Bỏ qua (không đụng) học sinh đã có nhận xét PENDING/APPROVED cho buổi này — nhận xét đã gửi/
     * duyệt không sửa lại BTVN nữa (mirror "REJECTED không thu hồi bài đã giao" của cơ chế cũ).
     */
    @Transactional
    public List<StudentCommentResponse> applyHomeworkToClass(Long classSessionId, ApplyClassHomeworkRequest request, Long actorUserId) {
        ClassSession session = getClassSessionOrThrow(classSessionId);
        requireCanWriteDailyComment(session, actorUserId);
        User actor = getUserOrThrow(actorUserId);

        List<ClassEnrollment> enrollments = classEnrollmentRepository
                .findBySchoolClassIdAndStatus(session.getSchoolClass().getId(), ClassEnrollment.Status.ACTIVE);
        if (enrollments.isEmpty()) {
            throw new IllegalStateException("Lớp học này chưa có học sinh đang hoạt động (ACTIVE) — không có ai để giao BTVN.");
        }

        List<StudentComment> editable = new ArrayList<>();
        for (ClassEnrollment enrollment : enrollments) {
            Student student = enrollment.getStudent();
            StudentComment comment = studentCommentRepository
                    .findByClassSessionIdAndStudentId(classSessionId, student.getId()).orElse(null);
            if (comment != null && comment.getStatus() != StudentComment.Status.DRAFT
                    && comment.getStatus() != StudentComment.Status.REJECTED) {
                continue;
            }
            if (comment == null) {
                comment = new StudentComment();
                comment.setStudent(student);
                comment.setSchoolClass(session.getSchoolClass());
                comment.setTeacher(actor);
                comment.setCommentType(StudentComment.CommentType.DAILY);
                comment.setClassSession(session);
                comment.setAcademicYear(session.getSchoolClass().getAcademicYear());
                comment.setCommentDate(session.getSessionDate());
                comment.setContent("");
                comment.setStatus(StudentComment.Status.DRAFT);
            }
            editable.add(comment);
        }
        if (editable.isEmpty()) {
            throw new IllegalStateException("Mọi học sinh ACTIVE của lớp đều đã Gửi/Duyệt nhận xét buổi này — không còn dòng nào để giao BTVN mới.");
        }

        OffsetDateTime dueAt = resolveDueAt(session, request.dueDate());
        boolean lateSubmissionAllowed = Boolean.TRUE.equals(request.lateSubmissionAllowed());
        HomeworkSkillBatch previousGrammarBatch = firstNonNull(editable, StudentComment::getHomeworkNextGrammarBatch);
        ReviewVideoAssignment previousVideoAssignment = firstNonNull(editable, StudentComment::getHomeworkNextReviewVideoAssignment);
        HomeworkSkillBatch previousReadingBatch = firstNonNull(editable, StudentComment::getHomeworkNextReadingBatch);
        HomeworkSkillBatch previousWritingBatch = firstNonNull(editable, StudentComment::getHomeworkNextWritingBatch);

        HomeworkSkillBatch grammarBatch = materializeExamHomework(session, request.grammarExamId(), previousGrammarBatch,
                grammarChannelSkillCategory(session.getTeacherType()), dueAt, lateSubmissionAllowed, actorUserId);
        ReviewVideoAssignment videoAssignment = materializeVideoHomework(session, request.videoSetId(), previousVideoAssignment,
                dueAt, lateSubmissionAllowed, actorUserId);
        HomeworkSkillBatch readingBatch = materializeExamHomework(session, request.readingExamId(), previousReadingBatch,
                Exercise.SkillCategory.READING, dueAt, lateSubmissionAllowed, actorUserId);
        HomeworkSkillBatch writingBatch = materializeExamHomework(session, request.writingExamId(), previousWritingBatch,
                Exercise.SkillCategory.WRITING, dueAt, lateSubmissionAllowed, actorUserId);

        for (StudentComment comment : editable) {
            comment.setHomeworkNextGrammarBatch(grammarBatch);
            comment.setHomeworkNextReviewVideoAssignment(videoAssignment);
            comment.setHomeworkNextReadingBatch(readingBatch);
            comment.setHomeworkNextWritingBatch(writingBatch);
        }
        List<StudentComment> saved = studentCommentRepository.saveAll(editable);
        saved.forEach(c -> writeHistory(c, actor, StudentCommentHistory.Action.UPDATED));
        return saved.stream().map(this::toResponse).toList();
    }

    private <T> T firstNonNull(List<StudentComment> comments, Function<StudentComment, T> getter) {
        return comments.stream().map(getter).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    /**
     * Giao/huỷ BTVN kênh dùng {@link HomeworkSkillBatch} (Ngữ pháp/Bài nghe dùng chung field theo
     * {@code skillCategory} truyền vào, Reading, Writing) — mirror {@link #materializeVideoHomework}
     * cho kênh Video. {@code examId=null} huỷ bản cũ (nếu có), không giao gì; không đổi so với
     * {@code previous} (cùng Exam + cùng hạn nộp + cùng "cho phép nộp muộn") thì giữ nguyên, không tạo
     * lại — khác cơ chế cũ (chỉ so Exam, bỏ sót trường hợp CHỈ đổi hạn nộp mà giữ nguyên đề).
     */
    private HomeworkSkillBatch materializeExamHomework(ClassSession session, Long examId, HomeworkSkillBatch previous,
                                                        Exercise.SkillCategory skillCategory, OffsetDateTime dueAt,
                                                        boolean lateSubmissionAllowed, Long actorUserId) {
        if (examId == null) {
            if (previous != null) {
                homeworkSkillBatchService.cancelBatch(previous);
            }
            return null;
        }
        if (previous != null && previous.getExam().getId().equals(examId)
                && batchDueAt(previous).isEqual(dueAt) && batchLateSubmissionAllowed(previous) == lateSubmissionAllowed) {
            return previous;
        }
        examRepository.findByIdAndDeletedAtIsNull(examId)
                .orElseThrow(() -> new ResourceNotFoundException("error.studentComment.examNotFoundById", new Object[]{examId}, "Không tìm thấy Đề (Lesson) id=" + examId));
        HomeworkSkillBatch batch = homeworkSkillBatchService.assignBatchToClass(
                examId, skillCategory, session.getSchoolClass().getId(), dueAt, lateSubmissionAllowed, actorUserId, session);
        if (previous != null) {
            homeworkSkillBatchService.cancelBatch(previous);
        }
        return batch;
    }

    /** Mirror {@link #materializeExamHomework} cho kênh Video Ôn tập (TKN/Clip phản xạ). */
    private ReviewVideoAssignment materializeVideoHomework(ClassSession session, Long videoSetId, ReviewVideoAssignment previous,
                                                            OffsetDateTime dueAt, boolean lateSubmissionAllowed, Long actorUserId) {
        if (videoSetId == null) {
            if (previous != null) {
                reviewVideoService.cancelAssignment(previous);
            }
            return null;
        }
        if (previous != null && previous.getReviewVideoSet().getId().equals(videoSetId)
                && previous.getDueAt().isEqual(dueAt) && previous.isLateSubmissionAllowed() == lateSubmissionAllowed) {
            return previous;
        }
        reviewVideoSetRepository.findById(videoSetId)
                .orElseThrow(() -> new ResourceNotFoundException("error.studentComment.videoSetNotFoundById", new Object[]{videoSetId}, "Không tìm thấy bộ video id=" + videoSetId));
        ReviewVideoAssignment assignment = reviewVideoService.deliverToClass(videoSetId, session.getSchoolClass().getId(), dueAt, lateSubmissionAllowed, actorUserId, session);
        if (previous != null) {
            reviewVideoService.cancelAssignment(previous);
        }
        return assignment;
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
                session.getSchoolClass().getId(), session.getSessionDate(), session.getId(),
                List.of(ClassSession.Status.CANCELLED, ClassSession.Status.RESCHEDULED), session.getTeacherType());
        if (upcoming.isEmpty()) {
            throw new NoUpcomingClassSessionException(
                    "error.noUpcomingClassSession.default", new Object[]{session.getTeacherType()},
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
     * Hạn nộp HIỆU LỰC của 1 comment — ưu tiên đọc thẳng từ bản giao đã materialize (4 kênh
     * grammar/video/reading/writing), rơi về {@code pendingHomeworkNextDueDate} (dữ liệu lịch sử từ
     * trước 2026-09-12, xem Javadoc {@link StudentComment} — không còn ai ghi field pending nữa nhưng
     * dòng cũ có thể vẫn còn) nếu chưa có bản giao nào, không có gì thì null. Dùng để hiển thị
     * (resolvedDueAt) và trong {@code toResponse}.
     */
    private OffsetDateTime effectiveDueAt(ClassSession session, StudentComment c) {
        if (c.getHomeworkNextGrammarBatch() != null) {
            return batchDueAt(c.getHomeworkNextGrammarBatch());
        }
        if (c.getHomeworkNextReviewVideoAssignment() != null) {
            return c.getHomeworkNextReviewVideoAssignment().getDueAt();
        }
        // V137
        if (c.getHomeworkNextReadingBatch() != null) {
            return batchDueAt(c.getHomeworkNextReadingBatch());
        }
        if (c.getHomeworkNextWritingBatch() != null) {
            return batchDueAt(c.getHomeworkNextWritingBatch());
        }
        if (c.getPendingHomeworkNextGrammarExamId() != null || c.getPendingHomeworkNextReviewVideoSetId() != null
                || c.getPendingHomeworkNextReadingExamId() != null || c.getPendingHomeworkNextWritingExamId() != null) {
            return resolveDueAt(session, c.getPendingHomeworkNextDueDate());
        }
        return null;
    }

    /**
     * V165 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-07) — mirror {@link #effectiveDueAt}
     * cho "Cho phép nộp muộn": chưa chọn kênh nào thì coi như false (không có gì để xung đột).
     */
    private boolean effectiveLateSubmissionAllowed(StudentComment c) {
        if (c.getHomeworkNextGrammarBatch() != null) {
            return batchLateSubmissionAllowed(c.getHomeworkNextGrammarBatch());
        }
        if (c.getHomeworkNextReviewVideoAssignment() != null) {
            return c.getHomeworkNextReviewVideoAssignment().isLateSubmissionAllowed();
        }
        if (c.getHomeworkNextReadingBatch() != null) {
            return batchLateSubmissionAllowed(c.getHomeworkNextReadingBatch());
        }
        if (c.getHomeworkNextWritingBatch() != null) {
            return batchLateSubmissionAllowed(c.getHomeworkNextWritingBatch());
        }
        return Boolean.TRUE.equals(c.getPendingHomeworkNextLateSubmissionAllowed());
    }

    private String videoLabel(ReviewVideoSet s) {
        return s.getTitle() + " (" + s.getCode() + ")";
    }

    /** V150 — nhãn tiếng Việt ngắn cho 1 skillCategory, dùng dựng nhãn hiển thị nhóm kỹ năng (mirror ExerciseService trước khi bỏ merge). */
    private static String skillCategoryLabel(Exercise.SkillCategory skillCategory) {
        return switch (skillCategory) {
            case READING -> "Reading";
            case WRITING -> "Writing";
            case VOCAB_GRAMMAR -> "Ngữ pháp";
            case LISTENING -> "Nghe";
        };
    }

    /**
     * V150 — nhãn hiển thị 1 Lô ĐÃ giao (đọc đúng N Bài THẬT đã chốt lúc giao qua
     * {@code exercise_assignments.homework_batch_id}, KHÔNG tính lại theo trạng thái Published hiện tại
     * — 1 Lesson thêm Bài mới sau khi đã giao không được đổi nhãn của lô CŨ này).
     *
     * V152 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — fix bug thật: phát hiện qua
     * dữ liệu thật 1 Lô KHÔNG có Bài nào gắn vào (0 exercise_assignments, dữ liệu hỏng/sót lại từ lúc
     * code Lô còn đang phát triển dở, hiện tượng đúng ra không thể xảy ra với assignBatchToClass hiện
     * tại — sources rỗng đã bị chặn TRƯỚC khi tạo Lô) — trước đây vẫn dựng nhãn kiểu "(Kỹ năng, 0 bài, 0
     * câu)" gây hiểu nhầm là "Lesson chưa có bài" thay vì "dữ liệu Lô này hỏng". Trả về null khi Lô rỗng,
     * mirror ĐÚNG guard đã có sẵn ở {@link HomeworkProgressService#grammarProgressLabel(List, Long)} —
     * FE (cả Portal học sinh lẫn Nhận xét học viên bên Admin, cùng đọc field
     * {@code homeworkNextExerciseTitle}/tương đương) đã tự hiện "—" khi giá trị null, không cần sửa gì
     * thêm ở FE.
     */
    private String batchLabel(HomeworkSkillBatch batch) {
        List<ExerciseAssignment> assignments = exerciseAssignmentRepository.findByHomeworkBatchId(batch.getId());
        if (assignments.isEmpty()) {
            return null;
        }
        long questionCount = assignments.stream()
                .mapToLong(a -> exerciseQuestionRepository.countByExerciseId(a.getExercise().getId())).sum();
        return batch.getExam().getCode() + " - " + batch.getExam().getTitle()
                + " (" + skillCategoryLabel(batch.getSkillCategory()) + ", " + assignments.size() + " bài, " + questionCount + " câu)";
    }

    /** V150 — hạn nộp chung của 1 Lô (mọi bản giao con trong cùng lô luôn cùng 1 dueAt, xem HomeworkSkillBatchService#assignBatchToClass). */
    private OffsetDateTime batchDueAt(HomeworkSkillBatch batch) {
        return exerciseAssignmentRepository.findByHomeworkBatchId(batch.getId()).stream()
                .map(ExerciseAssignment::getDueAt).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    /** V165 — mirror {@link #batchDueAt}: mọi ExerciseAssignment trong 1 Lô đều cùng 1 lateSubmissionAllowed. */
    private boolean batchLateSubmissionAllowed(HomeworkSkillBatch batch) {
        return exerciseAssignmentRepository.findByHomeworkBatchId(batch.getId()).stream()
                .findFirst().map(ExerciseAssignment::isLateSubmissionAllowed).orElse(false);
    }

    /**
     * V150 — nhãn hiển thị 1 nhóm kỹ năng CHƯA giao (đang chọn/pending, hoặc đang dựng dropdown Excel) —
     * tính LIVE theo Bài PUBLISHED hiện tại của Lesson (khác {@link #batchLabel}, vì chưa có gì để
     * "chốt" — số Bài/câu hiển thị luôn khớp thực tế tại thời điểm xem).
     */
    private String examSkillGroupLabel(Exam exam, Exercise.SkillCategory skillCategory) {
        List<Exercise> sources = exerciseRepository.findByExamIdAndSkillCategoryAndStatus(
                exam.getId(), skillCategory, Exercise.Status.PUBLISHED);
        long questionCount = sources.stream().mapToLong(e -> exerciseQuestionRepository.countByExerciseId(e.getId())).sum();
        return exam.getCode() + " - " + exam.getTitle()
                + " (" + skillCategoryLabel(skillCategory) + ", " + sources.size() + " bài, " + questionCount + " câu)";
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
     * giữ hành vi cũ — đối chiếu buổi liền kề tuyệt đối. Sửa 2026-08-19
     * (đã xác nhận với người dùng, fix bug thật phát hiện qua test dữ liệu
     * thật lớp 6G): loại CANCELLED/RESCHEDULED khỏi "buổi liền trước" —
     * trước đây không lọc, chỉ đúng tình cờ nhờ tiebreak idDesc, xem
     * Javadoc ClassSessionRepository#findSessionsBeforeOrderedDesc/findSessionsBeforeWithTeacherTypeOrderedDesc
     * (V167, 2026-09-05 — 2 method đổi tên + đổi sang @Query để fix bug "2 buổi cùng ngày").
     */
    private StudentComment previousComment(ClassSession classSession, Long studentId) {
        return previousSession(classSession)
                .flatMap(prev -> studentCommentRepository.findByClassSessionIdAndStudentId(prev.getId(), studentId))
                .orElse(null);
    }

    private Optional<ClassSession> previousSession(ClassSession classSession) {
        ClassSession.TeacherType teacherType = classSession.getTeacherType();
        List<ClassSession.Status> excludedStatuses = List.of(ClassSession.Status.CANCELLED, ClassSession.Status.RESCHEDULED);
        List<ClassSession> candidates = teacherType != null
                ? classSessionRepository.findSessionsBeforeWithTeacherTypeOrderedDesc(
                        classSession.getSchoolClass().getId(), classSession.getSessionDate(), classSession.getId(), teacherType, excludedStatuses)
                : classSessionRepository.findSessionsBeforeOrderedDesc(
                        classSession.getSchoolClass().getId(), classSession.getSessionDate(), classSession.getId(), excludedStatuses);
        return candidates.stream().findFirst();
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — fix N+1 THẬT gây chậm khi Gửi/
     * Duyệt nhận xét cho CẢ LỚP cùng lúc (phản hồi thực tế test trên môi trường deploy): trước đây
     * mỗi dòng {@link StudentComment} trong 1 lô gọi RIÊNG {@link #previousComment} — với lớp N học
     * sinh, tốn tới 2N truy vấn SELECT (N lần tìm lại "buổi trước" — dù CÙNG 1 classSession nên kết
     * quả giống hệt nhau mọi lần, cộng N lần tìm "nhận xét buổi trước của từng học sinh"). Hàm này
     * truy vấn "buổi trước" đúng 1 LẦN cho classSession, rồi 1 truy vấn BULK duy nhất
     * ({@link StudentCommentRepository#findByClassSessionIdAndStudentIdIn}) lấy nhận xét buổi trước
     * của TOÀN BỘ học sinh trong lô — dùng cho {@link #writeHistory(StudentComment, User,
     * StudentCommentHistory.Action, Map)} qua submitComments/decideComments.
     */
    private Map<Long, StudentComment> previousCommentsByStudentIdForSession(ClassSession classSession, List<Long> studentIds) {
        return previousSession(classSession)
                .map(prev -> studentCommentRepository.findByClassSessionIdAndStudentIdIn(prev.getId(), studentIds).stream()
                        .collect(java.util.stream.Collectors.toMap(c -> c.getStudent().getId(), c -> c, (a, b) -> a)))
                .orElse(Map.of());
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — mirror
     * {@link #previousCommentsByStudentIdForSession} nhưng cho lô CÓ THỂ gồm NHIỀU classSession khác
     * nhau (VD UC-22 "duyệt theo lô" của Quản lý điểm trường, gộp nhận xét từ nhiều lớp/buổi khác nhau
     * trong hàng chờ) — nhóm theo classSessionId trước, mỗi nhóm chỉ truy vấn 1 lần thay vì N lần theo
     * từng dòng, giữ đúng ngữ nghĩa cũ (khoá ngoài classSessionId+studentId, không gộp nhầm giữa các
     * buổi khác nhau nếu 1 học sinh xuất hiện ở nhiều buổi trong cùng 1 lô duyệt).
     */
    private Map<Long, Map<Long, StudentComment>> previousCommentsByClassSessionAndStudent(List<StudentComment> comments) {
        return comments.stream()
                .collect(java.util.stream.Collectors.groupingBy(c -> c.getClassSession().getId()))
                .entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> previousCommentsByStudentIdForSession(
                        e.getValue().get(0).getClassSession(), e.getValue().stream().map(c -> c.getStudent().getId()).toList())));
    }

    /** % bài ngữ pháp online đã giao ở buổi trước — xem HomeworkProgressService.grammarProgressLabel. V150: cộng dồn cả Lô (N Bài). */
    private String grammarPreviousProgressLabel(StudentComment previous) {
        return previous == null || previous.getHomeworkNextGrammarBatch() == null ? null
                : homeworkProgressService.grammarProgressLabel(
                        exerciseAssignmentRepository.findByHomeworkBatchId(previous.getHomeworkNextGrammarBatch().getId()), previous.getStudent().getId());
    }

    /** % video ôn tập đã giao ở buổi trước — xem HomeworkProgressService.videoProgressLabel. */
    private String videoPreviousProgressLabel(StudentComment previous) {
        return previous == null ? null
                : homeworkProgressService.videoProgressLabel(previous.getHomeworkNextReviewVideoAssignment(), previous.getStudent().getId());
    }

    /** V137: % bài Reading online đã giao ở buổi trước — mirror grammarPreviousProgressLabel. V150: cộng dồn cả Lô. */
    private String readingPreviousProgressLabel(StudentComment previous) {
        return previous == null || previous.getHomeworkNextReadingBatch() == null ? null
                : homeworkProgressService.grammarProgressLabel(
                        exerciseAssignmentRepository.findByHomeworkBatchId(previous.getHomeworkNextReadingBatch().getId()), previous.getStudent().getId());
    }

    /** Mirror {@link #readingPreviousProgressLabel} cho kỹ năng Writing (V137). */
    private String writingPreviousProgressLabel(StudentComment previous) {
        return previous == null || previous.getHomeworkNextWritingBatch() == null ? null
                : homeworkProgressService.grammarProgressLabel(
                        exerciseAssignmentRepository.findByHomeworkBatchId(previous.getHomeworkNextWritingBatch().getId()), previous.getStudent().getId());
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
                    "error.studentCommentNotEditable.outsideEditWindow", new Object[]{windowDays, classSession.getSessionDate(), deadline},
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

    private boolean isBlankRow(Row row, DataFormatter formatter, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
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
                    "error.notAssignedTeacherForClass.default", new Object[]{}, "Bạn không được phân công giảng dạy lớp này.");
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
                    metadata.put("commentCount", comments.size());
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
        metadata.put("commentDate", comment.getCommentDate());
        if (comment.getRejectionReason() != null && !comment.getRejectionReason().isBlank()) {
            metadata.put("reason", comment.getRejectionReason());
        }
        notificationService.notify(comment.getTeacher().getId(), Notification.NotificationType.COMMENT_REJECTED, title, content,
                metadata, "STUDENT_COMMENT", comment.getId(), Notification.Priority.NORMAL, null);
    }

    private void writeHistory(StudentComment comment, User actor, StudentCommentHistory.Action action) {
        writeHistory(comment, actor, action, null);
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — overload nhận thêm
     * {@code previousCache} (xem {@link #previousCommentsByClassSessionAndStudent}) để
     * submitComments/decideComments (ghi lịch sử CẢ LÔ N dòng cùng lúc) không phải tự truy vấn lại
     * "nhận xét buổi trước" cho từng dòng — truyền {@code null} thì giữ nguyên hành vi cũ (tự truy vấn
     * riêng), dùng cho mọi chỗ khác chỉ ghi lịch sử 1 dòng đơn lẻ (Lưu nháp, sửa PENDING...).
     */
    private void writeHistory(StudentComment comment, User actor, StudentCommentHistory.Action action,
                               Map<Long, Map<Long, StudentComment>> previousCache) {
        StudentCommentHistory history = new StudentCommentHistory();
        history.setStudentComment(comment);
        history.setChangedBy(actor);
        history.setAction(action);
        history.setDetails(buildHistorySnapshot(comment, previousCache));
        studentCommentHistoryRepository.save(history);
    }

    /**
     * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-19) — version
     * history kiểu Google Sheets: mỗi lần lưu (Lưu nháp/Gửi nhận xét/Duyệt/
     * Từ chối/sửa PENDING) chụp lại TOÀN BỘ nội dung nhận xét tại đúng thời
     * điểm đó, không chỉ vài field metadata mỏng như bản gốc. Đề mục
     * Exercise/ReviewVideoSet chụp bằng TÊN (denormalize), không chỉ id —
     * để xem lại 1 phiên bản cũ không bị sai lệch nếu đề/video sau này bị
     * đổi tên hoặc xoá.
     */
    private Map<String, Object> buildHistorySnapshot(StudentComment comment) {
        return buildHistorySnapshot(comment, null);
    }

    private Map<String, Object> buildHistorySnapshot(StudentComment comment, Map<Long, Map<Long, StudentComment>> previousCache) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", comment.getStatus().name());
        snapshot.put("content", comment.getContent());
        snapshot.put("severity", comment.getSeverity().name());
        snapshot.put("isWarning", comment.isWarning());
        snapshot.put("attitude", comment.getAttitude() == null ? null : comment.getAttitude().name());
        snapshot.put("homeworkPreviousScore", comment.getHomeworkPreviousScore());
        snapshot.put("homeworkPreviousSpeakingScore", comment.getHomeworkPreviousSpeakingScore());
        // V130 — chỉ khác null với buổi teacherType=VIETNAMESE.
        snapshot.put("homeworkPreviousReadingScore", comment.getHomeworkPreviousReadingScore());
        snapshot.put("homeworkPreviousWritingScore", comment.getHomeworkPreviousWritingScore());
        // Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-19) — khớp đủ cột với bảng chính
        // (Nhận xét học viên có 2 cột "BTVN buổi trước" TỰ ĐỘNG, xem PreviousProgressCell FE) — tính
        // NGAY tại thời điểm lưu (không chờ đọc lại), đúng tinh thần "snapshot đúng lúc đó", dù giá trị
        // có thể trùng ở nhiều phiên bản liên tiếp nếu buổi trước không có gì thay đổi thêm.
        StudentComment previous = previousCache != null
                ? previousCache.getOrDefault(comment.getClassSession().getId(), Map.of()).get(comment.getStudent().getId())
                : previousComment(comment.getClassSession(), comment.getStudent().getId());
        snapshot.put("grammarPreviousProgress", grammarPreviousProgressLabel(previous));
        snapshot.put("videoPreviousProgress", videoPreviousProgressLabel(previous));
        snapshot.put("readingPreviousProgress", readingPreviousProgressLabel(previous));
        snapshot.put("writingPreviousProgress", writingPreviousProgressLabel(previous));
        snapshot.put("homeworkNext", comment.getHomeworkNext());
        snapshot.put("homeworkNextReading", comment.getHomeworkNextReading());
        snapshot.put("homeworkNextWriting", comment.getHomeworkNextWriting());
        snapshot.put("note", comment.getNote());
        snapshot.put("rejectionReason", comment.getRejectionReason());

        HomeworkSkillBatch grammarNext = comment.getHomeworkNextGrammarBatch();
        ReviewVideoAssignment videoNext = comment.getHomeworkNextReviewVideoAssignment();
        snapshot.put("homeworkNextExerciseTitle", grammarNext == null ? null : batchLabel(grammarNext));
        snapshot.put("homeworkNextReviewVideoSetTitle", videoNext == null ? null : videoNext.getReviewVideoSet().getTitle());
        OffsetDateTime dueAt = grammarNext != null ? batchDueAt(grammarNext) : videoNext != null ? videoNext.getDueAt() : null;
        snapshot.put("homeworkNextDueAt", dueAt == null ? null : dueAt.toString());

        Long pendingExerciseId = comment.getPendingHomeworkNextGrammarExamId();
        snapshot.put("pendingHomeworkNextExerciseTitle", pendingExerciseId == null ? null
                : examRepository.findByIdAndDeletedAtIsNull(pendingExerciseId)
                        .map(exam -> examSkillGroupLabel(exam, grammarChannelSkillCategory(comment.getClassSession().getTeacherType()))).orElse(null));
        Long pendingVideoSetId = comment.getPendingHomeworkNextReviewVideoSetId();
        snapshot.put("pendingHomeworkNextReviewVideoSetTitle", pendingVideoSetId == null ? null
                : reviewVideoSetRepository.findById(pendingVideoSetId).map(ReviewVideoSet::getTitle).orElse(null));

        // V137
        HomeworkSkillBatch readingNext = comment.getHomeworkNextReadingBatch();
        HomeworkSkillBatch writingNext = comment.getHomeworkNextWritingBatch();
        snapshot.put("homeworkNextReadingExerciseTitle", readingNext == null ? null : batchLabel(readingNext));
        snapshot.put("homeworkNextWritingExerciseTitle", writingNext == null ? null : batchLabel(writingNext));
        Long pendingReadingId = comment.getPendingHomeworkNextReadingExamId();
        snapshot.put("pendingHomeworkNextReadingExerciseTitle", pendingReadingId == null ? null
                : examRepository.findByIdAndDeletedAtIsNull(pendingReadingId)
                        .map(exam -> examSkillGroupLabel(exam, Exercise.SkillCategory.READING)).orElse(null));
        Long pendingWritingId = comment.getPendingHomeworkNextWritingExamId();
        snapshot.put("pendingHomeworkNextWritingExerciseTitle", pendingWritingId == null ? null
                : examRepository.findByIdAndDeletedAtIsNull(pendingWritingId)
                        .map(exam -> examSkillGroupLabel(exam, Exercise.SkillCategory.WRITING)).orElse(null));

        LocalDateTime pendingDueDate = comment.getPendingHomeworkNextDueDate();
        snapshot.put("pendingHomeworkNextDueDate", pendingDueDate == null ? null : pendingDueDate.toString());
        snapshot.put("pendingHomeworkNextLateSubmissionAllowed", comment.getPendingHomeworkNextLateSubmissionAllowed());

        return snapshot;
    }

    /**
     * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-19) — timeline
     * version history của 1 nhận xét, mới nhất trước. Không rào quyền riêng
     * (giống listComments/listCommentsForClass ở trên — endpoint đọc, không
     * side effect, cùng mức lộ dữ liệu với "Lịch sử nhận xét buổi này" đã
     * hiển thị sẵn cho cả lớp).
     */
    @Transactional(readOnly = true)
    public List<StudentCommentHistoryResponse> listHistory(Long commentId) {
        getCommentOrThrow(commentId);
        return studentCommentHistoryRepository.findByStudentCommentIdOrderByCreatedAtDesc(commentId)
                .stream().map(this::toHistoryResponse).toList();
    }

    private StudentCommentHistoryResponse toHistoryResponse(StudentCommentHistory h) {
        StudentComment comment = h.getStudentComment();
        return new StudentCommentHistoryResponse(
                h.getId(), comment.getId(), comment.getStudent().getId(), comment.getStudent().getUser().getFullName(),
                h.getChangedBy().getId(), h.getChangedBy().getFullName(),
                h.getAction().name(), h.getDetails(), h.getCreatedAt());
    }

    /**
     * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-19) — timeline
     * version history của CẢ BUỔI (mọi học sinh, gộp chung 1 danh sách theo
     * thời gian) — dùng cho nút "Lịch sử phiên bản" ở đầu bảng Nhận xét hàng
     * ngày (kiểu Google Sheets: 1 nút xem lại TOÀN BỘ bảng tại 1 mốc, không
     * phải xem riêng từng dòng học sinh — khác {@link #listHistory} ở trên).
     * FE tự gộp các bản ghi gần nhau thành 1 "phiên bản" (1 lần Lưu nháp/Gửi
     * thường tạo N bản ghi liên tiếp, 1 bản ghi/học sinh — không có sẵn 1
     * batch id dùng chung vì "Lưu nháp" hiện là N request riêng biệt/học
     * sinh, không phải 1 request gộp).
     */
    @Transactional(readOnly = true)
    public List<StudentCommentHistoryResponse> listHistoryForSession(Long classSessionId) {
        return studentCommentHistoryRepository.findByStudentComment_ClassSession_IdOrderByCreatedAtDesc(classSessionId)
                .stream().map(this::toHistoryResponse).toList();
    }

    private StudentComment getCommentOrThrow(Long id) {
        return studentCommentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.studentComment.notFoundById", new Object[]{id}, "Không tìm thấy nhận xét id=" + id));
    }

    private SchoolClass getClassOrThrow(Long id) {
        return schoolClassRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.studentComment.classNotFoundById", new Object[]{id}, "Không tìm thấy lớp học id=" + id));
    }

    private ClassSession getClassSessionOrThrow(Long id) {
        return classSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.studentComment.sessionNotFoundById", new Object[]{id}, "Không tìm thấy buổi học id=" + id));
    }

    /** "Buổi N" hiển thị FE — cùng công thức {@link ClassSessionRepository#countEarlierSessions}. */
    @Transactional(readOnly = true)
    public int sessionNumberOf(Long classSessionId) {
        ClassSession classSession = getClassSessionOrThrow(classSessionId);
        return (int) classSessionRepository.countEarlierSessions(
                classSession.getSchoolClass().getId(), classSession.getSessionDate(), classSession.getId()) + 1;
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.studentComment.accountNotFoundById", new Object[]{id}, "Không tìm thấy tài khoản id=" + id));
    }

    private StudentCommentResponse toResponse(StudentComment c) {
        StudentComment previous = previousComment(c.getClassSession(), c.getStudent().getId());
        HomeworkSkillBatch grammarNext = c.getHomeworkNextGrammarBatch();
        ReviewVideoAssignment videoNext = c.getHomeworkNextReviewVideoAssignment();
        // V137
        HomeworkSkillBatch readingNext = c.getHomeworkNextReadingBatch();
        HomeworkSkillBatch writingNext = c.getHomeworkNextWritingBatch();
        // V127: lựa chọn CHƯA giao (còn DRAFT/REJECTED, chưa Gửi lại) — chỉ có id Lesson (Exam), tự tra
        // nhãn hiển thị cho FE (mirror batchLabel(grammarNext) ở trên, khác chỗ chưa có Lô để đọc thẳng
        // qua đó — V150: id giờ là examId, không còn là exerciseId).
        Long pendingExerciseId = c.getPendingHomeworkNextGrammarExamId();
        String pendingExerciseTitle = pendingExerciseId == null ? null
                : examRepository.findByIdAndDeletedAtIsNull(pendingExerciseId)
                        .map(exam -> examSkillGroupLabel(exam, grammarChannelSkillCategory(c.getClassSession().getTeacherType()))).orElse(null);
        Long pendingVideoSetId = c.getPendingHomeworkNextReviewVideoSetId();
        String pendingVideoSetTitle = pendingVideoSetId == null ? null
                : reviewVideoSetRepository.findById(pendingVideoSetId).map(ReviewVideoSet::getTitle).orElse(null);
        Long pendingReadingId = c.getPendingHomeworkNextReadingExamId();
        String pendingReadingTitle = pendingReadingId == null ? null
                : examRepository.findByIdAndDeletedAtIsNull(pendingReadingId)
                        .map(exam -> examSkillGroupLabel(exam, Exercise.SkillCategory.READING)).orElse(null);
        Long pendingWritingId = c.getPendingHomeworkNextWritingExamId();
        String pendingWritingTitle = pendingWritingId == null ? null
                : examRepository.findByIdAndDeletedAtIsNull(pendingWritingId)
                        .map(exam -> examSkillGroupLabel(exam, Exercise.SkillCategory.WRITING)).orElse(null);
        OffsetDateTime homeworkNextDueAt = grammarNext != null ? batchDueAt(grammarNext)
                : videoNext != null ? videoNext.getDueAt()
                : readingNext != null ? batchDueAt(readingNext)
                : writingNext != null ? batchDueAt(writingNext) : null;
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
                c.getHomeworkPreviousReadingScore(), c.getHomeworkPreviousWritingScore(),
                c.getHomeworkNext(),
                c.getHomeworkNextReading(), c.getHomeworkNextWriting(),
                // V150 — FE (DailyCommentPanel/CommentHistoryList) chỉ dùng field "AssignmentId" này làm
                // khoá tra ngược để tự chọn lại đúng dropdown khi sửa/xem lại 1 comment đã Gửi — không
                // hiển thị trực tiếp cho người dùng. Trả examId (Lesson) thay vì id của chính HomeworkSkillBatch,
                // vì dropdown mới chọn theo (examId, skillCategory) chứ không còn theo 1 exerciseId/bản giao đơn.
                grammarNext == null ? null : grammarNext.getExam().getId(),
                grammarNext == null ? null : batchLabel(grammarNext),
                videoNext == null ? null : videoNext.getId(),
                videoNext == null ? null : videoNext.getReviewVideoSet().getTitle(),
                readingNext == null ? null : readingNext.getExam().getId(),
                readingNext == null ? null : batchLabel(readingNext),
                writingNext == null ? null : writingNext.getExam().getId(),
                writingNext == null ? null : batchLabel(writingNext),
                homeworkNextDueAt, effectiveLateSubmissionAllowed(c),
                pendingExerciseId, pendingExerciseTitle,
                pendingVideoSetId, pendingVideoSetTitle,
                pendingReadingId, pendingReadingTitle,
                pendingWritingId, pendingWritingTitle,
                c.getPendingHomeworkNextDueDate(),
                grammarPreviousProgressLabel(previous), videoPreviousProgressLabel(previous),
                readingPreviousProgressLabel(previous), writingPreviousProgressLabel(previous),
                resolvedOfflinePrevious(previous),
                c.getNote(), c.getClassSession().getLessonContent());
    }
}
