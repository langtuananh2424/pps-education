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
import vn.com.pps.education.domain.GradePeriod;
import vn.com.pps.education.domain.ImportJob;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.StudentComment;
import vn.com.pps.education.domain.StudentCommentHistory;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateStudentCommentRequest;
import vn.com.pps.education.dto.DailyCommentImportResponse;
import vn.com.pps.education.dto.DecideCommentsRequest;
import vn.com.pps.education.dto.EnterAttendanceMarkRequest;
import vn.com.pps.education.dto.MarkAttendanceRequest;
import vn.com.pps.education.dto.StudentCommentResponse;
import vn.com.pps.education.dto.SubmitCommentsRequest;
import vn.com.pps.education.dto.UpdateStudentCommentRequest;
import vn.com.pps.education.exception.ApprovalAlreadyDecidedException;
import vn.com.pps.education.exception.InvalidCommentContextException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.StudentCommentNotEditableException;
import vn.com.pps.education.repository.ApprovalFlowRepository;
import vn.com.pps.education.repository.AttendanceMarkRepository;
import vn.com.pps.education.repository.AttendanceSessionRepository;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.GradePeriodRepository;
import vn.com.pps.education.repository.ImportJobRepository;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * UC-21: Viết nhận xét học sinh (FR-ACA-04) + UC-22: Duyệt nhận xét
 * (FR-LMS-09). Xem docs/uc/phan-he-06-hoc-thuat.md và
 * docs/diagrams/activity/ActivityDiagram-DuyetNhanXet.mmd.
 *
 * Dùng lại ApprovalFlow (entity_type=STUDENT_COMMENT), giống pattern
 * UC-19/20 (GradeService) — mỗi nhận xét submit riêng lẻ có 1 approval_flow
 * riêng, submit theo lô chia sẻ 1 batchId.
 *
 * <p><b>Nhận xét Hàng ngày kiểu mới (comment_type=DAILY CHỈ — Giữa/Cuối kỳ
 * giữ nguyên 100% luồng DRAFT→submit→PENDING ở trên) — bổ sung ngoài SDD
 * gốc, đã xác nhận với người dùng 2026-07-24 (kết luận họp):</b>
 * <ul>
 *   <li>Ghi (tạo/sửa) 1 nhận xét DAILY = tự động chuyển PENDING (actor chỉ
 *       có {@code academic.comment.write}, chờ Quản lý điểm trường duyệt
 *       — UC-22 không đổi) hoặc APPROVED ngay (actor có
 *       {@code academic.comment.approve} — SITE_MANAGER/SUPER_ADMIN, bỏ
 *       qua bước chờ duyệt) — không còn bước DRAFT→submit riêng cho DAILY,
 *       kể cả sửa lại sau khi REJECTED. Không tạo permission mới, tái dùng
 *       đúng 2 permission đã có (V44).</li>
 *   <li>Hạn nhập/sửa: mặc định 7 ngày kể từ NGÀY BUỔI HỌC diễn ra
 *       (system_settings.academic.comment_edit_window_days — xem
 *       AcademicSettingsService), actor có {@code academic.comment.approve}
 *       bỏ qua hạn này (cùng 1 permission gánh cả 2 "quyền quản trị").</li>
 *   <li>Excel round-trip theo buổi học (buildTemplate/importComments) —
 *       điền sẵn học sinh ACTIVE của lớp, cột Điểm danh cho phép sửa luôn
 *       điểm danh khi import lại (tái dùng nguyên StudentAttendanceService.
 *       markAttendance, không viết lại logic điểm danh).</li>
 * </ul>
 */
@Service
public class StudentCommentService {

    private static final int COLUMN_COUNT = 9;
    private static final int COL_DATE = 0;
    private static final int COL_STUDENT_CODE = 1;
    private static final int COL_FULL_NAME = 2;
    private static final int COL_ATTENDANCE = 3;
    private static final int COL_ATTITUDE = 4;
    private static final int COL_HOMEWORK_PREVIOUS = 5;
    private static final int COL_CONTENT = 6;
    private static final int COL_HOMEWORK_NEXT = 7;
    private static final int COL_NOTE = 8;

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
                                  StudentAttendanceService studentAttendanceService) {
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
    }

    // ===================== UC-21: Viết nhận xét (TEACHER) =====================

    /**
     * Main Flow bước 1-3 (MID_TERM/END_TERM — lưu nháp DRAFT). Nhận xét
     * Hàng ngày kiểu mới (DAILY): ghi xong tự động chuyển PENDING/APPROVED
     * ngay (xem Javadoc lớp) — không còn dừng ở DRAFT.
     */
    @Transactional
    public StudentCommentResponse writeComment(Long classId, CreateStudentCommentRequest request, Long actorUserId) {
        SchoolClass schoolClass = getClassOrThrow(classId);
        User actor = getUserOrThrow(actorUserId);
        StudentComment.CommentType commentType = StudentComment.CommentType.valueOf(request.commentType());

        ClassSession classSession = null;
        GradePeriod gradePeriod = null;
        boolean isApprover = false;
        if (commentType == StudentComment.CommentType.DAILY) {
            if (request.classSessionId() == null || request.gradePeriodId() != null) {
                throw new InvalidCommentContextException(
                        "commentType=DAILY phải có classSessionId và không được có gradePeriodId.");
            }
            classSession = getClassSessionOrThrow(request.classSessionId());
            isApprover = requireCanWriteDailyComment(classSession, actorUserId);
        } else {
            if (request.gradePeriodId() == null || request.classSessionId() != null) {
                throw new InvalidCommentContextException(
                        "commentType=" + commentType + " phải có gradePeriodId và không được có classSessionId.");
            }
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
        applyContent(comment, request.content(), request.structuredContent(), request.severity(), request.isWarning(),
                request.attitude(), request.homeworkPreviousScore(), request.homeworkNext(), request.note());
        comment = studentCommentRepository.save(comment);
        writeHistory(comment, actor, StudentCommentHistory.Action.CREATED);

        if (commentType == StudentComment.CommentType.DAILY) {
            comment = routeDailyComment(comment, actor, isApprover);
        }
        return toResponse(comment);
    }

    /**
     * MID_TERM/END_TERM Main Flow bước 2, A1: sửa nội dung khi đang DRAFT
     * hoặc sau khi bị REJECTED (quay lại DRAFT để submit lại) — không đổi.
     * DAILY: sửa được bất kỳ trạng thái nào trong hạn X ngày, ghi xong lại
     * tự động chuyển PENDING/APPROVED ngay (xem Javadoc lớp).
     */
    @Transactional
    public StudentCommentResponse updateComment(Long id, UpdateStudentCommentRequest request, Long actorUserId) {
        StudentComment comment = getCommentOrThrow(id);
        User actor = getUserOrThrow(actorUserId);
        boolean isDaily = comment.getCommentType() == StudentComment.CommentType.DAILY;

        boolean isApprover = false;
        if (isDaily) {
            isApprover = requireCanWriteDailyComment(comment.getClassSession(), actorUserId);
        } else {
            requireAssignedTeacher(comment.getSchoolClass().getId(), actorUserId);
            if (comment.getStatus() != StudentComment.Status.DRAFT && comment.getStatus() != StudentComment.Status.REJECTED) {
                throw new StudentCommentNotEditableException(
                        "Nhận xét id=" + id + " đang ở trạng thái " + comment.getStatus() + " — chỉ sửa được khi DRAFT hoặc REJECTED.");
            }
        }

        comment.setApprovalFlow(null);
        applyContent(comment, request.content(), request.structuredContent(), request.severity(), request.isWarning(),
                request.attitude(), request.homeworkPreviousScore(), request.homeworkNext(), request.note());
        if (isDaily) {
            comment = studentCommentRepository.save(comment);
            comment = routeDailyComment(comment, actor, isApprover);
        } else {
            comment.setStatus(StudentComment.Status.DRAFT);
            comment = studentCommentRepository.save(comment);
            writeHistory(comment, actor, StudentCommentHistory.Action.UPDATED);
        }
        return toResponse(comment);
    }

    @Transactional(readOnly = true)
    public List<StudentCommentResponse> listComments(Long classId, Long studentId) {
        return studentCommentRepository.findBySchoolClassIdAndStudentIdOrderByCommentDateDesc(classId, studentId)
                .stream().map(this::toResponse).toList();
    }

    /** Main Flow bước 4-5 (chỉ còn ý nghĩa với MID_TERM/END_TERM — DAILY tự route ngay khi ghi, xem Javadoc lớp). */
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

        List<String> headers = List.of("Ngày*", "Mã học viên*", "Họ và tên", "Điểm danh*",
                "Thái độ học tập", "BTVN buổi trước", "Nhận xét học sinh*", "BTVN buổi sau", "Ghi chú");
        List<List<Object>> rows = new ArrayList<>();
        for (ClassEnrollment enrollment : enrollments) {
            Student student = enrollment.getStudent();
            StudentComment existing = studentCommentRepository
                    .findByClassSessionIdAndStudentId(classSessionId, student.getId()).orElse(null);
            AttendanceMark.Status attendance = attendanceByStudent.get(student.getId());

            List<Object> row = new ArrayList<>();
            row.add(classSession.getSessionDate().toString());
            row.add(student.getStudentCode());
            row.add(student.getUser().getFullName());
            row.add(attendance == null ? null : attendanceLabel(attendance));
            row.add(existing == null || existing.getAttitude() == null ? null : attitudeLabel(existing.getAttitude()));
            row.add(existing == null ? null : existing.getHomeworkPreviousScore());
            row.add(existing == null ? null : existing.getContent());
            row.add(existing == null ? null : existing.getHomeworkNext());
            row.add(existing == null ? null : existing.getNote());
            rows.add(row);
        }
        return ExcelExportHelper.buildWorkbook("Nhận xét", headers, rows);
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
        boolean isApprover = requireCanWriteDailyComment(classSession, actorUserId);
        User actor = getUserOrThrow(actorUserId);

        ImportJob job = new ImportJob();
        job.setImportType(ImportJob.ImportType.DAILY_COMMENTS);
        job.setSourceFileName(file.getOriginalFilename() == null ? "unnamed.xlsx" : file.getOriginalFilename());
        job.setSourceFileUrl("in-memory://" + job.getSourceFileName());
        job.setUploadedBy(actor);
        job.setStatus(ImportJob.Status.PROCESSING);
        job.setStartedAt(OffsetDateTime.now());
        job = importJobRepository.save(job);

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
                    parsedRows.add(parseRow(row, formatter, rowIndex, classSession));
                } catch (RuntimeException ex) {
                    errors.add(rowError(rowIndex + 1, ex.getMessage()));
                }
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
                            parsed.homeworkPrevious(), parsed.content(), parsed.homeworkNext(), parsed.note(),
                            actor, isApprover);
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
                              String homeworkPrevious, String content, String homeworkNext, String note) {}

    private ParsedRow parseRow(Row row, DataFormatter formatter, int rowIndex, ClassSession classSession) {
        String dateText = cell(row, formatter, COL_DATE);
        String studentCode = cell(row, formatter, COL_STUDENT_CODE);
        String attendanceText = cell(row, formatter, COL_ATTENDANCE);
        String attitudeText = cell(row, formatter, COL_ATTITUDE);
        String homeworkPrevious = cell(row, formatter, COL_HOMEWORK_PREVIOUS);
        String content = cell(row, formatter, COL_CONTENT);
        String homeworkNext = cell(row, formatter, COL_HOMEWORK_NEXT);
        String note = cell(row, formatter, COL_NOTE);

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
            throw new IllegalArgumentException("Thiếu điểm danh (cột D).");
        }
        AttendanceMark.Status attendance = parseAttendanceStatus(attendanceText.trim());

        String attitude = attitudeText == null || attitudeText.isBlank() ? null : parseAttitude(attitudeText.trim()).name();
        return new ParsedRow(rowIndex, student, attendance, attitude,
                blankToNull(homeworkPrevious), blankToNull(content), blankToNull(homeworkNext), blankToNull(note));
    }

    /** Ghi 1 dòng: Vắng/Có phép mà mọi cột sau Điểm danh đều trống thì bỏ qua (chỉ ghi điểm danh, không tạo nhận xét). */
    private void importRow(ClassSession classSession, Student student, AttendanceMark.Status attendance,
                            String attitude, String homeworkPrevious, String content, String homeworkNext, String note,
                            User actor, boolean isApprover) {
        boolean absent = attendance == AttendanceMark.Status.ABSENT || attendance == AttendanceMark.Status.EXCUSED;
        boolean allBlank = attitude == null && homeworkPrevious == null && content == null
                && homeworkNext == null && note == null;
        if (absent && allBlank) {
            return;
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Thiếu nhận xét (cột G) — bắt buộc trừ khi học sinh vắng/có phép.");
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
        comment.setTeacher(actor);
        comment.setApprovalFlow(null);
        applyContent(comment, content, null, null, false,
                attitude, homeworkPrevious, homeworkNext, note);
        comment = studentCommentRepository.save(comment);
        writeHistory(comment, actor, StudentCommentHistory.Action.UPDATED);
        routeDailyComment(comment, actor, isApprover);
    }

    // ===================== Helpers =====================

    private void applyContent(StudentComment comment, String content, Map<String, Object> structuredContent,
                               String severity, boolean isWarning, String attitude, String homeworkPreviousScore,
                               String homeworkNext, String note) {
        comment.setContent(content);
        comment.setStructuredContent(structuredContent);
        if (severity != null) {
            comment.setSeverity(StudentComment.Severity.valueOf(severity));
        }
        comment.setWarning(isWarning);
        comment.setAttitude(attitude == null ? null : StudentComment.Attitude.valueOf(attitude));
        comment.setHomeworkPreviousScore(homeworkPreviousScore);
        comment.setHomeworkNext(homeworkNext);
        comment.setNote(note);
    }

    /**
     * Ghi (tạo/sửa) 1 nhận xét DAILY xong → tự động chuyển PENDING (tạo
     * ApprovalFlow, chờ Quản lý điểm trường — UC-22) hoặc APPROVED ngay
     * (actor có academic.comment.approve) — xem Javadoc lớp.
     */
    private StudentComment routeDailyComment(StudentComment comment, User actor, boolean isApprover) {
        OffsetDateTime now = OffsetDateTime.now();
        if (isApprover) {
            comment.setStatus(StudentComment.Status.APPROVED);
            comment.setApprovedBy(actor);
            comment.setApprovedAt(now);
            comment.setVisibleToParentAt(now);
            comment.setSubmittedAt(now);
            comment = studentCommentRepository.save(comment);
            writeHistory(comment, actor, StudentCommentHistory.Action.UPDATED);
            return comment;
        }
        ApprovalFlow flow = new ApprovalFlow();
        flow.setEntityType(ApprovalFlow.EntityType.STUDENT_COMMENT);
        flow.setEntityId(comment.getId());
        flow.setStatus(ApprovalFlow.Status.PENDING);
        flow.setSubmittedBy(actor);
        flow = approvalFlowRepository.save(flow);
        comment.setApprovalFlow(flow);
        comment.setStatus(StudentComment.Status.PENDING);
        comment.setSubmittedAt(now);
        comment = studentCommentRepository.save(comment);
        writeHistory(comment, actor, StudentCommentHistory.Action.UPDATED);
        notifySiteManagersPending(List.of(comment));
        return comment;
    }

    /**
     * Actor có academic.comment.approve: bỏ qua rào (không cần là GV được
     * phân công, không giới hạn hạn X ngày) — trả true (dùng để route
     * APPROVED thẳng). Ngược lại: phải là GV được phân công lớp (giữ
     * nguyên rào cũ) VÀ còn trong hạn X ngày kể từ ngày buổi học — trả
     * false (route PENDING).
     */
    private boolean requireCanWriteDailyComment(ClassSession classSession, Long actorUserId) {
        if (permissionEvaluationService.hasPermission(actorUserId, "academic.comment.approve")) {
            return true;
        }
        requireAssignedTeacher(classSession.getSchoolClass().getId(), actorUserId);
        int windowDays = academicSettingsService.commentEditWindowDays();
        LocalDate deadline = classSession.getSessionDate().plusDays(windowDays);
        if (LocalDate.now().isAfter(deadline)) {
            throw new StudentCommentNotEditableException(
                    "Chỉ nhập/sửa nhận xét trong vòng " + windowDays + " ngày kể từ ngày buổi học ("
                            + classSession.getSessionDate() + "); hạn đã hết ngày " + deadline + ".");
        }
        return false;
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
            case "trung bình", "trung binh", "average" -> StudentComment.Attitude.AVERAGE;
            case "tốt", "tot", "good" -> StudentComment.Attitude.GOOD;
            default -> throw new IllegalArgumentException(
                    "Thái độ học tập không hợp lệ (cần Kém/Trung bình/Tốt): " + text);
        };
    }

    private String attitudeLabel(StudentComment.Attitude attitude) {
        return switch (attitude) {
            case POOR -> "Kém";
            case AVERAGE -> "Trung bình";
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
        return new StudentCommentResponse(
                c.getId(), c.getStudent().getId(), c.getStudent().getUser().getFullName(), c.getSchoolClass().getId(),
                c.getTeacher().getId(), c.getCommentType().name(),
                c.getClassSession() == null ? null : c.getClassSession().getId(),
                c.getGradePeriod() == null ? null : c.getGradePeriod().getId(),
                c.getCommentDate(), c.getContent(), c.getStructuredContent(), c.getSeverity().name(), c.isWarning(),
                c.getStatus().name(), c.getSubmittedAt(), c.getApprovedAt(),
                c.getApprovedBy() == null ? null : c.getApprovedBy().getId(), c.getVisibleToParentAt(), c.getRejectionReason(),
                c.getAttitude() == null ? null : c.getAttitude().name(), c.getHomeworkPreviousScore(),
                c.getHomeworkNext(), c.getNote());
    }
}
