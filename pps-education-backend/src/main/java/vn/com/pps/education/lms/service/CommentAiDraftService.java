package vn.com.pps.education.lms.service;

import vn.com.pps.education.student.service.StudentAttitudeAlertTrackingService;
import vn.com.pps.education.student.service.StudentCommentService;
import vn.com.pps.education.student.service.StudentSignalService;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.common.CommentAiDraftMetrics;
import vn.com.pps.education.common.CommentPatternCheck;
import vn.com.pps.education.common.CommentRuleCheck;
import vn.com.pps.education.common.CommentSimilarity;
import vn.com.pps.education.common.HomeworkScoreInsight;
import vn.com.pps.education.common.StudentSignalInsight;
import vn.com.pps.education.academic.domain.AttendanceMark;
import vn.com.pps.education.academic.domain.ClassEnrollment;
import vn.com.pps.education.academic.domain.ClassSession;
import vn.com.pps.education.student.domain.Student;
import vn.com.pps.education.student.domain.StudentComment;
import vn.com.pps.education.student.dto.AutoProgressPreviewResponse;
import vn.com.pps.education.lms.dto.CommentAiDraftJobResponse;
import vn.com.pps.education.lms.dto.CommentAiDraftResult;
import vn.com.pps.education.lms.dto.HomeworkScoreInput;
import vn.com.pps.education.lms.dto.ReviseCommentAiDraftRequest;
import vn.com.pps.education.exception.CommentAiDraftFailedException;
import vn.com.pps.education.exception.CommentAiDraftRejectedException;
import vn.com.pps.education.academic.repository.AttendanceMarkRepository;
import vn.com.pps.education.academic.repository.AttendanceSessionRepository;
import vn.com.pps.education.academic.repository.ClassEnrollmentRepository;
import vn.com.pps.education.student.repository.StudentCommentRepository;

import java.io.IOException;
import java.text.Collator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * UC-74: Trợ lý AI soạn nháp nhận xét hàng ngày từ audio (mở rộng FR-ACA-04 — bổ sung ngoài SDD gốc, đã
 * xác nhận với người dùng 2026-09-28). Xem docs/uc/phan-he-06-hoc-thuat.md để biết đầy đủ Main
 * Flow/Alternate Flow.
 *
 * <p>Service này KHÔNG ghi DB: chỉ đọc dữ liệu buổi học (rào + danh sách học sinh + nhận xét cũ) trong
 * request, rồi giao phần gọi AI cho {@link AiJobRegistry} chạy nền trên 1 bản chụp bất biến
 * ({@link DraftContext} — không chạm entity JPA ngoài transaction). Kết quả là bản xem trước; lưu thật
 * đi qua Lưu nháp của UC-21 ({@code StudentCommentService#saveDraftBatch}) do chính giáo viên bấm — quyền
 * lưu cao nhất của trợ lý là DRAFT, không có đường nào để trợ lý Gửi duyệt.</p>
 *
 * <p>Ranh giới "AI chỉ điền Thái độ + Nhận xét" được chặn bằng kiểu dữ liệu, không chỉ bằng prompt: dòng
 * kết quả ({@link CommentAiDraftResult.Row}) chỉ có 2 trường đó, và Thái độ ngoài 5 giá trị
 * {@link StudentComment.Attitude} bị bỏ khi đọc kết quả AI.</p>
 *
 * <p>Pipeline 3 bước (tách ra để mỗi lệnh gọi AI ngắn, dưới timeout 90s của {@link NineRouterAiClient}):
 * (1) tách ý chung/ý riêng + gắn học sinh (bước 5), (2) viết câu theo lô {@code writeBatchSize} học sinh,
 * mỗi lô biết câu đã viết ở lô trước để tránh lặp (bước 6), (3) đo trùng lặp bằng
 * {@link CommentSimilarity} và viết lại 1 lần các dòng vượt ngưỡng (bước 7).</p>
 */
@Service
public class CommentAiDraftService {

    private static final Logger log = LoggerFactory.getLogger(CommentAiDraftService.class);
    static final String METRICS_LOG_PREFIX = "COMMENT_AI_METRICS";
    private static final com.fasterxml.jackson.databind.ObjectMapper METRICS_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    static final String EXTRACT_PROMPT = "comment-ai-draft-extract-system-prompt.txt";
    static final String WRITE_PROMPT = "comment-ai-draft-write-system-prompt.txt";
    static final String REVISE_PROMPT = "comment-ai-draft-revise-system-prompt.txt";
    /** Rubric nhận xét — chèn vào chỗ {{RUBRIC}} của cả 3 prompt trên, xem {@link CommentAiJsonCaller}. */
    static final String RUBRIC_FILE = CommentAiJsonCaller.RUBRIC_FILE;
    /**
     * Mục rubric mỗi bước cần (bổ sung 2026-10-01, xem {@link CommentAiJsonCaller#selectRubricSections}): tách ý chỉ
     * chọn Thái độ (mục 1); viết câu cần cấu trúc/văn phong/điều cấm/kiểu câu/mẫu câu (mục 2-6); sửa theo yêu cầu có
     * thể đổi cả Thái độ lẫn câu chữ nên nhận cả rubric.
     */
    static final Set<Integer> EXTRACT_RUBRIC_SECTIONS = Set.of(1);
    static final Set<Integer> WRITE_RUBRIC_SECTIONS = Set.of(2, 3, 4, 5, 6);
    static final Set<Integer> REVISE_RUBRIC_SECTIONS = CommentAiJsonCaller.ALL_RUBRIC_SECTIONS;
    /** Giáo viên nói nhận xét bằng tiếng Việt — ép STT nhận dạng tiếng Việt thay vì tự đoán ngôn ngữ. */
    static final String STT_LANGUAGE = "vi";

    static final String SOURCE_CLASS = "CLASS";
    static final String SOURCE_INDIVIDUAL = "INDIVIDUAL";

    private static final Map<String, String> ATTITUDE_LABELS = Map.of(
            "WEAK", "Yếu", "AVERAGE", "Trung bình", "FAIR", "Khá", "GOOD", "Tốt", "EXCELLENT", "Xuất sắc");
    private static final Pattern PRONOUN_THAY = Pattern.compile("(?iu)(?<!\\p{L})thầy(?!\\p{L})");
    private static final Pattern PRONOUN_CO = Pattern.compile("(?iu)(?<!\\p{L})cô(?!\\p{L})");
    private static final int MAX_AVOID_TEXTS = 30;
    private static final int MAX_HISTORY_TURNS = 6;
    private static final int MAX_INSTRUCTION_LENGTH = 2000;
    private static final int MAX_SPELLING_HINT_LENGTH = 800;

    private final StudentCommentService studentCommentService;
    private final StudentAttitudeAlertTrackingService attitudeAlertTrackingService;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AttendanceMarkRepository attendanceMarkRepository;
    private final StudentCommentRepository studentCommentRepository;
    private final HomeworkInsightService homeworkInsightService;
    private final StudentSignalService studentSignalService;
    private final NineRouterAiClient aiClient;
    private final CommentAiJsonCaller jsonCaller;
    private final AiJobRegistry jobRegistry;
    private final Settings settings;

    /**
     * Ngưỡng cấu hình qua {@code app.ai-comment-draft.*} (application.yml).
     *
     * @param model combo 9Router chỉ gồm Claude — Claude không nhận audio nên audio luôn qua STT trước.
     * @param previousCommentCount N nhận xét gần nhất/học sinh đem so trùng (UC-74 bước 6-7).
     * @param similarityThreshold tỷ lệ cụm 3 từ trùng nhau từ mức này trở lên coi là trùng lặp máy móc.
     * @param maxAudioBytes chặn dung lượng ở backend (giới hạn 5 phút kiểm tra ở FE, xem UC-74 A3).
     * @param maxPatternShare tỷ lệ tối đa số học sinh trong buổi được dùng chung 1 kiểu câu mở đầu/câu kết
     *                        (xem {@link CommentPatternCheck}).
     * @param writeTemperature nhiệt độ AI ở bước VIẾT câu nhận xét, gồm cả "Viết lại toàn bộ" (bổ sung 2026-10-01) — tách
     *                         ý và sửa theo yêu cầu luôn ở 0 để chỉ đổi đúng chỗ được yêu cầu.
     */
    public record Settings(String model, int previousCommentCount, int previousLookbackDays,
                           double similarityThreshold, int writeBatchSize, long maxAudioBytes, int homeworkTrendPoints,
                           double maxPatternShare, double writeTemperature) {
    }

    public CommentAiDraftService(StudentCommentService studentCommentService,
                                 StudentAttitudeAlertTrackingService attitudeAlertTrackingService,
                                 ClassEnrollmentRepository classEnrollmentRepository,
                                 AttendanceSessionRepository attendanceSessionRepository,
                                 AttendanceMarkRepository attendanceMarkRepository,
                                 StudentCommentRepository studentCommentRepository,
                                 HomeworkInsightService homeworkInsightService,
                                 StudentSignalService studentSignalService,
                                 NineRouterAiClient aiClient,
                                 CommentAiJsonCaller jsonCaller,
                                 AiJobRegistry jobRegistry,
                                 @Value("${app.ai-comment-draft.model:comment-pps}") String model,
                                 @Value("${app.ai-comment-draft.previous-comment-count:3}") int previousCommentCount,
                                 @Value("${app.ai-comment-draft.previous-lookback-days:120}") int previousLookbackDays,
                                 @Value("${app.ai-comment-draft.similarity-threshold:0.5}") double similarityThreshold,
                                 @Value("${app.ai-comment-draft.write-batch-size:10}") int writeBatchSize,
                                 @Value("${app.ai-comment-draft.max-audio-bytes:20971520}") long maxAudioBytes,
                                 @Value("${app.ai-comment-draft.homework-trend-points:20}") int homeworkTrendPoints,
                                 @Value("${app.ai-comment-draft.max-pattern-share:0.3}") double maxPatternShare,
                                 @Value("${app.ai-comment-draft.write-temperature:0.7}") double writeTemperature) {
        this.studentCommentService = studentCommentService;
        this.attitudeAlertTrackingService = attitudeAlertTrackingService;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.attendanceMarkRepository = attendanceMarkRepository;
        this.studentCommentRepository = studentCommentRepository;
        this.homeworkInsightService = homeworkInsightService;
        this.studentSignalService = studentSignalService;
        this.aiClient = aiClient;
        this.jsonCaller = jsonCaller;
        this.jobRegistry = jobRegistry;
        this.settings = new Settings(model, previousCommentCount, previousLookbackDays, similarityThreshold,
                Math.max(1, writeBatchSize), maxAudioBytes, homeworkTrendPoints, maxPatternShare,
                Math.max(0, Math.min(1, writeTemperature)));
    }

    // ---- Bản chụp dữ liệu buổi học (đọc trong request, dùng ở luồng nền) ----

    /** @param homeworkPercent trung bình % BTVN nhập tay của buổi đó (để nhận biết tăng/giảm rõ), null nếu không có. */
    record PreviousComment(LocalDate date, String content, Integer homeworkPercent) {
        PreviousComment(LocalDate date, String content) {
            this(date, content, null);
        }
    }

    /**
     * @param lowStreak       số buổi Thái độ Yếu/Trung bình liên tiếp đã duyệt hiện tại (UC-74, nhắc chuỗi cảnh báo 3 buổi).
     * @param homeworkDetails thống kê BTVN nhiều buổi bằng lời, theo thứ tự ưu tiên (bổ sung 2026-09-30, xem
     *                        {@link HomeworkInsightService}); rỗng nếu không có gì đáng nhắc.
     * @param signals         điểm danh/chuyên cần, gợi ý giọng văn, thông tin học sinh, nhận xét buổi khác Loại giáo viên
     *                        (bổ sung 2026-09-30, xem {@link StudentSignalService}).
     */
    record RosterStudent(Long id, String fullName, List<PreviousComment> previousComments, int lowStreak, String homeworkNote,
                         List<String> homeworkDetails, StudentSignalService.Signals signals) {
        RosterStudent(Long id, String fullName, List<PreviousComment> previousComments) {
            this(id, fullName, previousComments, 0, null, List.of(), StudentSignalService.Signals.EMPTY);
        }

        RosterStudent(Long id, String fullName, List<PreviousComment> previousComments, int lowStreak) {
            this(id, fullName, previousComments, lowStreak, null, List.of(), StudentSignalService.Signals.EMPTY);
        }

        RosterStudent(Long id, String fullName, List<PreviousComment> previousComments, int lowStreak, String homeworkNote) {
            this(id, fullName, previousComments, lowStreak, homeworkNote, List.of(), StudentSignalService.Signals.EMPTY);
        }

        RosterStudent(Long id, String fullName, List<PreviousComment> previousComments, int lowStreak, String homeworkNote,
                      List<String> homeworkDetails) {
            this(id, fullName, previousComments, lowStreak, homeworkNote, homeworkDetails, StudentSignalService.Signals.EMPTY);
        }

        /** Đưa các tín hiệu ngoài lời giáo viên vào payload gửi AI (chỉ các trường có dữ liệu). */
        void putSignals(Map<String, Object> item) {
            if (!homeworkDetails.isEmpty()) {
                item.put("homeworkDetails", homeworkDetails);
            }
            if (!signals.attendance().isEmpty()) {
                item.put("attendance", signals.attendance());
            }
            if (!signals.toneHints().isEmpty()) {
                item.put("toneHints", signals.toneHints());
            }
            if (signals.otherTeacherComment() != null) {
                item.put("otherTeacherComment", signals.otherTeacherComment().content());
            }
        }

        String callName() {
            String[] parts = fullName.trim().split("\\s+");
            return parts[parts.length - 1];
        }
    }

    record DraftContext(Long classSessionId, String className, LocalDate sessionDate, String lessonContent,
                        List<RosterStudent> roster, List<CommentAiDraftResult.SkippedStudent> skipped) {
        Map<Long, RosterStudent> rosterById() {
            Map<Long, RosterStudent> map = new LinkedHashMap<>();
            roster.forEach(s -> map.put(s.id(), s));
            return map;
        }
    }

    private record Target(RosterStudent student, String attitude, List<String> points, String source, List<Long> sharedWith) {
    }

    private record ExtractionOutcome(CommentAiDraftResult.Extraction extraction,
                                     List<CommentAiDraftResult.UnmatchedMention> unmatched) {
    }

    // ---- Điểm vào từ Controller ----

    /**
     * UC-74 Main Flow bước 1-2: kiểm tra đầu vào + rào (A2-A4) ngay trong request, rồi chạy nền bước 3-8.
     */
    @Transactional(readOnly = true)
    public CommentAiDraftJobResponse startDraft(Long classSessionId, MultipartFile audio, String note,
                                                List<HomeworkScoreInput> homeworkScores, Long actorUserId) {
        boolean hasAudio = audio != null && !audio.isEmpty();
        boolean hasNote = note != null && !note.isBlank();
        if (!hasAudio && !hasNote) {
            throw new CommentAiDraftRejectedException("Cần gửi audio nhận xét hoặc ghi chú dạng chữ cho trợ lý.");
        }
        byte[] audioBytes = null;
        String mimeType = null;
        if (hasAudio) {
            if (audio.getSize() > settings.maxAudioBytes()) {
                throw new CommentAiDraftRejectedException("File audio quá lớn (tối đa "
                        + (settings.maxAudioBytes() / (1024 * 1024)) + " MB) — mỗi lần gửi tối đa 5 phút.");
            }
            mimeType = audio.getContentType();
            if (mimeType == null || !mimeType.startsWith("audio/")) {
                throw new CommentAiDraftRejectedException("File gửi lên không phải audio.");
            }
            try {
                audioBytes = audio.getBytes();
            } catch (IOException e) {
                throw new CommentAiDraftRejectedException("Không đọc được file audio — vui lòng gửi lại.");
            }
        }
        DraftContext context = loadContext(classSessionId, actorUserId, homeworkScores);
        byte[] finalAudio = audioBytes;
        String finalMimeType = mimeType;
        String finalNote = hasNote ? note.trim() : null;
        return toResponse(jobRegistry.submit(actorUserId, AiJobRegistry.Lane.DRAFT, () -> generateDraft(context, finalAudio, finalMimeType, finalNote)));
    }

    /** UC-74 Main Flow bước 9 — giáo viên yêu cầu sửa/viết lại bản nháp. */
    @Transactional(readOnly = true)
    public CommentAiDraftJobResponse startRevise(Long classSessionId, ReviseCommentAiDraftRequest request, Long actorUserId) {
        if (request.mode() == ReviseCommentAiDraftRequest.Mode.INSTRUCTION
                && (request.instruction() == null || request.instruction().isBlank())) {
            throw new CommentAiDraftRejectedException("Hãy nhập yêu cầu chỉnh sửa cho trợ lý.");
        }
        if (request.mode() == ReviseCommentAiDraftRequest.Mode.INSTRUCTION
                && request.instruction().length() > MAX_INSTRUCTION_LENGTH) {
            throw new CommentAiDraftRejectedException("Yêu cầu quá dài (tối đa " + MAX_INSTRUCTION_LENGTH + " ký tự).");
        }
        if (request.mode() == ReviseCommentAiDraftRequest.Mode.REWRITE_ALL && request.extraction() == null) {
            throw new CommentAiDraftRejectedException("Chưa có bản nháp để viết lại — hãy gửi audio trước.");
        }
        DraftContext context = loadContext(classSessionId, actorUserId, request.homeworkScores());
        return toResponse(jobRegistry.submit(actorUserId, AiJobRegistry.Lane.DRAFT, () -> revise(context, request)));
    }

    public CommentAiDraftJobResponse getJob(String jobId, Long actorUserId) {
        return toResponse(jobRegistry.get(jobId, actorUserId, CommentAiDraftResult.class));
    }

    private static CommentAiDraftJobResponse toResponse(AiJobRegistry.Snapshot<CommentAiDraftResult> snapshot) {
        return new CommentAiDraftJobResponse(snapshot.jobId(), snapshot.status(), snapshot.errorMessage(), snapshot.result());
    }

    /**
     * UC-74 bước 2 + 4: rào của Lưu nháp (A2), lọc học sinh Vắng/Có phép hoặc đã Gửi duyệt, và chụp N nhận
     * xét gần nhất/học sinh (mọi lớp) để AI tránh lặp lại (bước 6-7). Buổi GVNN dùng được trợ lý như buổi GV
     * Việt Nam (bỏ chặn A1 cũ — đã xác nhận với người dùng 2026-09-29).
     */
    DraftContext loadContext(Long classSessionId, Long actorUserId) {
        return loadContext(classSessionId, actorUserId, null);
    }

    /**
     * @param homeworkScores điểm BTVN buổi trước giáo viên đang nhập trên bảng (có thể null) — cùng % tự động của
     *                       bài online (backend tính) được quy ra LỜI bằng {@link HomeworkScoreInsight}, chỉ giữ
     *                       kênh nổi bật hoặc tăng/giảm rõ; AI không nhận con số nào.
     */
    DraftContext loadContext(Long classSessionId, Long actorUserId, List<HomeworkScoreInput> homeworkScores) {
        ClassSession session = studentCommentService.requireCanWriteDailyCommentFor(classSessionId, actorUserId);
        Map<Long, AttendanceMark> todayMarks = attendanceSessionRepository.findByClassSessionId(session.getId())
                .map(a -> attendanceMarkRepository.findByAttendanceSessionId(a.getId()).stream()
                        .collect(Collectors.toMap(m -> m.getStudent().getId(), m -> m, (x, y) -> x)))
                .orElseGet(Map::of);
        Map<Long, AttendanceMark.Status> attendance = new HashMap<>();
        todayMarks.forEach((id, mark) -> attendance.put(id, mark.getStatus()));
        Map<Long, ClassEnrollment> eligibleEnrollments = new LinkedHashMap<>();
        Map<Long, StudentComment.Status> existingStatus = studentCommentRepository.findByClassSessionId(session.getId()).stream()
                .collect(Collectors.toMap(c -> c.getStudent().getId(), StudentComment::getStatus, (x, y) -> x));

        List<Student> eligible = new ArrayList<>();
        List<CommentAiDraftResult.SkippedStudent> skipped = new ArrayList<>();
        for (ClassEnrollment enrollment : classEnrollmentRepository.findBySchoolClassIdAndStatus(
                session.getSchoolClass().getId(), ClassEnrollment.Status.ACTIVE)) {
            Student student = enrollment.getStudent();
            String name = student.getUser().getFullName();
            AttendanceMark.Status attendanceStatus = attendance.get(student.getId());
            StudentComment.Status commentStatus = existingStatus.get(student.getId());
            if (attendanceStatus == AttendanceMark.Status.ABSENT || attendanceStatus == AttendanceMark.Status.EXCUSED) {
                skipped.add(new CommentAiDraftResult.SkippedStudent(student.getId(), name,
                        attendanceStatus == AttendanceMark.Status.ABSENT ? "Vắng" : "Có phép"));
            } else if (commentStatus == StudentComment.Status.PENDING || commentStatus == StudentComment.Status.APPROVED) {
                skipped.add(new CommentAiDraftResult.SkippedStudent(student.getId(), name, "Đã gửi duyệt"));
            } else {
                eligible.add(student);
                eligibleEnrollments.putIfAbsent(student.getId(), enrollment);
            }
        }
        if (eligible.isEmpty()) {
            throw new CommentAiDraftRejectedException(
                    "Không còn học sinh nào cần soạn nhận xét ở buổi này (vắng hết hoặc đã gửi duyệt hết).");
        }

        List<Long> ids = eligible.stream().map(Student::getId).toList();
        Map<Long, List<PreviousComment>> previousById = new HashMap<>();
        for (StudentComment comment : studentCommentRepository.findRecentByStudentIds(ids, StudentComment.Status.REJECTED,
                session.getSessionDate().minusDays(settings.previousLookbackDays()), session.getSessionDate())) {
            if (comment.getContent() == null || comment.getContent().isBlank()) {
                continue;
            }
            List<PreviousComment> list = previousById.computeIfAbsent(comment.getStudent().getId(), k -> new ArrayList<>());
            if (list.size() < settings.previousCommentCount()) {
                java.util.OptionalInt homeworkAverage = HomeworkScoreInsight.averagePercent(java.util.Arrays.asList(
                        comment.getHomeworkPreviousScore(), comment.getHomeworkPreviousSpeakingScore(),
                        comment.getHomeworkPreviousReadingScore(), comment.getHomeworkPreviousWritingScore()));
                list.add(new PreviousComment(comment.getCommentDate(), comment.getContent().trim(),
                        homeworkAverage.isPresent() ? homeworkAverage.getAsInt() : null));
            }
        }
        Map<Long, Integer> lowStreaks = attitudeAlertTrackingService.currentLowStreaks(session.getSchoolClass(), ids);
        Map<Long, String> homeworkNotes = homeworkNotes(session.getId(), actorUserId,
                session.getTeacherType() == ClassSession.TeacherType.FOREIGN, homeworkScores, previousById);
        Map<Long, HomeworkScoreInput> manualById = new HashMap<>();
        if (homeworkScores != null) {
            homeworkScores.stream().filter(h -> h != null && h.studentId() != null).forEach(h -> manualById.putIfAbsent(h.studentId(), h));
        }
        Map<Long, List<String>> homeworkDetails = homeworkInsightService.describe(session, ids, manualById);
        Map<Long, StudentSignalService.Signals> signals = studentSignalService.describe(session, eligibleEnrollments, todayMarks);
        // Nhận xét buổi khác Loại giáo viên cũng đem so trùng lặp (bước 7) như các nhận xét cũ khác.
        signals.forEach((id, s) -> {
            if (s.otherTeacherComment() != null) {
                List<PreviousComment> list = previousById.computeIfAbsent(id, k -> new ArrayList<>());
                if (list.stream().noneMatch(p -> p.content().equals(s.otherTeacherComment().content()))) {
                    list.add(new PreviousComment(s.otherTeacherComment().date(), s.otherTeacherComment().content()));
                }
            }
        });
        Collator collator = Collator.getInstance(Locale.forLanguageTag("vi"));
        List<RosterStudent> roster = eligible.stream()
                .map(s -> new RosterStudent(s.getId(), s.getUser().getFullName(), List.copyOf(previousById.getOrDefault(s.getId(), List.of())),
                        lowStreaks.getOrDefault(s.getId(), 0), homeworkNotes.get(s.getId()),
                        List.copyOf(homeworkDetails.getOrDefault(s.getId(), List.of())),
                        signals.getOrDefault(s.getId(), StudentSignalService.Signals.EMPTY)))
                .sorted(Comparator.comparing(RosterStudent::callName, collator).thenComparing(RosterStudent::fullName, collator))
                .toList();
        return new DraftContext(session.getId(), session.getSchoolClass().getName(), session.getSessionDate(),
                session.getLessonContent(), roster, List.copyOf(skipped));
    }

    /**
     * Mỗi cột "BTVN buổi trước" trên bảng quy về đúng 1 kỹ năng (bổ sung 2026-09-30, đã xác nhận với người dùng)
     * để AI nhận xét cụ thể theo kỹ năng: kênh chính (ô Offline + % tự động) là ngữ pháp ở buổi GV Việt Nam, nghe
     * ở buổi GVNN; kênh video là từ vựng / phản xạ nói; Reading/Writing là đọc/viết.
     */
    private Map<Long, String> homeworkNotes(Long classSessionId, Long actorUserId, boolean foreignSession,
                                            List<HomeworkScoreInput> homeworkScores, Map<Long, List<PreviousComment>> previousById) {
        String mainSkill = HomeworkScoreInsight.mainChannelSkill(foreignSession);
        String videoSkill = HomeworkScoreInsight.videoChannelSkill(foreignSession);
        Map<Long, AutoProgressPreviewResponse> autoById = new HashMap<>();
        for (AutoProgressPreviewResponse auto : studentCommentService.previewAutoProgress(classSessionId, actorUserId)) {
            autoById.put(auto.studentId(), auto);
        }
        Map<Long, HomeworkScoreInput> manualById = new HashMap<>();
        if (homeworkScores != null) {
            homeworkScores.stream().filter(h -> h != null && h.studentId() != null).forEach(h -> manualById.putIfAbsent(h.studentId(), h));
        }
        Set<Long> studentIds = new HashSet<>(autoById.keySet());
        studentIds.addAll(manualById.keySet());
        Map<Long, String> notes = new HashMap<>();
        for (Long studentId : studentIds) {
            List<HomeworkScoreInsight.Channel> channels = new ArrayList<>();
            HomeworkScoreInput manual = manualById.get(studentId);
            if (manual != null) {
                channels.add(new HomeworkScoreInsight.Channel(mainSkill, "bài trên giấy", manual.offline()));
                channels.add(new HomeworkScoreInsight.Channel(HomeworkScoreInsight.SKILL_READING, "bài trên giấy", manual.reading()));
                channels.add(new HomeworkScoreInsight.Channel(HomeworkScoreInsight.SKILL_WRITING, "bài trên giấy", manual.writing()));
                channels.add(new HomeworkScoreInsight.Channel(videoSkill, "video ôn tập", manual.speaking()));
            }
            AutoProgressPreviewResponse auto = autoById.get(studentId);
            if (auto != null) {
                channels.add(new HomeworkScoreInsight.Channel(mainSkill, "bài online", auto.grammarPreviousProgress()));
                channels.add(new HomeworkScoreInsight.Channel(videoSkill, "video ôn tập", auto.videoPreviousProgress()));
                channels.add(new HomeworkScoreInsight.Channel(HomeworkScoreInsight.SKILL_READING, "bài online", auto.readingPreviousProgress()));
                channels.add(new HomeworkScoreInsight.Channel(HomeworkScoreInsight.SKILL_WRITING, "bài online", auto.writingPreviousProgress()));
            }
            java.util.OptionalInt previousAverage = previousById.getOrDefault(studentId, List.of()).stream()
                    .map(PreviousComment::homeworkPercent).filter(java.util.Objects::nonNull).findFirst()
                    .map(java.util.OptionalInt::of).orElse(java.util.OptionalInt.empty());
            java.util.OptionalInt currentManualAverage = manual == null ? java.util.OptionalInt.empty()
                    : HomeworkScoreInsight.averagePercent(java.util.Arrays.asList(manual.offline(), manual.speaking(), manual.reading(), manual.writing()));
            String note = HomeworkScoreInsight.describe(channels, false, currentManualAverage, previousAverage, settings.homeworkTrendPoints());
            if (note != null) {
                notes.put(studentId, note);
            }
        }
        return notes;
    }

    // ---- Luồng nền ----

    /** UC-74 Main Flow bước 3-8 (chạy nền, không chạm DB). */
    CommentAiDraftResult generateDraft(DraftContext context, byte[] audio, String mimeType, String note) {
        String transcript = "";
        if (audio != null) {
            transcript = aiClient.transcribe(audio, mimeType, null, spellingHint(context), STT_LANGUAGE);
            if (transcript == null || transcript.isBlank()) {
                throw new CommentAiDraftFailedException(
                        "Không chuyển được audio thành văn bản (dịch vụ nhận dạng giọng nói lỗi hoặc audio không có tiếng nói) — vui lòng thử lại.");
            }
        }
        ExtractionOutcome outcome = extract(context, teacherText(transcript, note));
        List<Target> targets = buildTargets(context, outcome.extraction(), Map.of());
        if (targets.isEmpty()) {
            throw new CommentAiDraftFailedException(
                    "Không tìm thấy ý nhận xét nào trong lời giáo viên — hãy nói rõ nhận xét chung cả lớp hoặc tên học sinh cần nhận xét.");
        }
        Map<Long, String> contents = writeAndDeduplicate(context, targets, List.of(), outcome.extraction().teacherPronoun());
        List<CommentAiDraftResult.Row> rows = toRows(context, targets, contents, outcome.extraction().teacherPronoun());

        long individualCount = targets.stream().filter(t -> SOURCE_INDIVIDUAL.equals(t.source())).count();
        StringBuilder message = new StringBuilder("Đã soạn nhận xét cho ").append(rows.size()).append(" học sinh");
        if (individualCount > 0) {
            message.append(" (").append(individualCount).append(" bạn được nhận xét riêng: ")
                    .append(targets.stream().filter(t -> SOURCE_INDIVIDUAL.equals(t.source()))
                            .map(t -> t.student().fullName()).collect(Collectors.joining(", ")))
                    .append(")");
        }
        message.append(".");
        appendReviewHints(message, rows, outcome.unmatched().size(), context.skipped().size());
        logMetrics("DRAFT", context, rows, outcome.unmatched().size(), outcome.extraction().teacherPronoun());
        return new CommentAiDraftResult(transcript, message.toString(), outcome.extraction(), rows,
                outcome.unmatched(), context.skipped());
    }

    /** UC-74 Main Flow bước 9 — sửa theo yêu cầu (chỉ các dòng liên quan) hoặc viết lại câu chữ toàn bộ. */
    CommentAiDraftResult revise(DraftContext context, ReviseCommentAiDraftRequest request) {
        Map<Long, RosterStudent> rosterById = context.rosterById();
        Map<Long, ReviseCommentAiDraftRequest.CurrentRow> current = new LinkedHashMap<>();
        for (ReviseCommentAiDraftRequest.CurrentRow row : request.currentRows()) {
            if (rosterById.containsKey(row.studentId())) {
                current.putIfAbsent(row.studentId(), row);
            }
        }
        return request.mode() == ReviseCommentAiDraftRequest.Mode.REWRITE_ALL
                ? rewriteAll(context, request, current)
                : reviseByInstruction(context, request, current);
    }

    private CommentAiDraftResult rewriteAll(DraftContext context, ReviseCommentAiDraftRequest request,
                                            Map<Long, ReviseCommentAiDraftRequest.CurrentRow> current) {
        ExtractionOutcome outcome = sanitize(context, request.extraction());
        // Thái độ giáo viên đã chỉnh qua trò chuyện được giữ nguyên — "Viết lại" chỉ đổi câu chữ.
        Map<Long, String> attitudeOverrides = new HashMap<>();
        current.forEach((id, row) -> attitudeOverrides.put(id, normalizeAttitude(row.attitude())));
        List<Target> targets = buildTargets(context, outcome.extraction(), attitudeOverrides);
        if (targets.isEmpty()) {
            throw new CommentAiDraftFailedException("Bản nháp không còn ý nhận xét nào để viết lại — hãy gửi lại audio.");
        }
        List<String> oldTexts = current.values().stream().map(ReviseCommentAiDraftRequest.CurrentRow::content)
                .filter(c -> c != null && !c.isBlank()).toList();
        Map<Long, String> contents = writeAndDeduplicate(context, targets, oldTexts, outcome.extraction().teacherPronoun());
        List<CommentAiDraftResult.Row> rows = toRows(context, targets, contents, outcome.extraction().teacherPronoun());
        StringBuilder message = new StringBuilder("Đã viết lại câu chữ cho ").append(rows.size())
                .append(" học sinh, giữ nguyên ý giáo viên đã nói.");
        appendReviewHints(message, rows, outcome.unmatched().size(), 0);
        logMetrics("REWRITE_ALL", context, rows, outcome.unmatched().size(), outcome.extraction().teacherPronoun());
        return new CommentAiDraftResult(request.transcript(), message.toString(), outcome.extraction(), rows,
                outcome.unmatched(), context.skipped());
    }

    private CommentAiDraftResult reviseByInstruction(DraftContext context, ReviseCommentAiDraftRequest request,
                                                     Map<Long, ReviseCommentAiDraftRequest.CurrentRow> current) {
        Map<Long, RosterStudent> rosterById = context.rosterById();
        Map<Long, String> attitudes = new LinkedHashMap<>();
        Map<Long, String> contents = new LinkedHashMap<>();
        current.forEach((id, row) -> {
            attitudes.put(id, normalizeAttitude(row.attitude()));
            contents.put(id, row.content());
        });
        String currentPronoun = request.extraction() == null ? null : normalizePronoun(request.extraction().teacherPronoun());
        JsonNode response = callJson(REVISE_PROMPT,
                revisePayload(context, request, attitudes, contents, request.instruction().trim(), currentPronoun),
                REVISE_RUBRIC_SECTIONS, 0);
        if (response == null) {
            throw new CommentAiDraftFailedException("Trợ lý chưa xử lý được yêu cầu (AI lỗi hoặc quá thời gian) — vui lòng thử lại.");
        }
        Set<Long> contentChanged = new LinkedHashSet<>();
        int changed = 0;
        for (JsonNode change : response.path("changes")) {
            long id = change.path("studentId").asLong(0);
            if (!rosterById.containsKey(id)) {
                continue;
            }
            boolean touched = false;
            if (change.has("attitude")) {
                String raw = change.path("attitude").isNull() ? "" : change.path("attitude").asText("");
                if (raw.isBlank()) {
                    attitudes.put(id, null);
                    touched = true;
                } else if (normalizeAttitude(raw) != null) {
                    attitudes.put(id, normalizeAttitude(raw));
                    touched = true;
                }
            }
            if (change.hasNonNull("content")) {
                contents.put(id, change.path("content").asText().trim());
                contentChanged.add(id);
                touched = true;
            }
            if (touched) {
                attitudes.putIfAbsent(id, null);
                changed++;
            }
        }
        Set<Long> individualIds = request.extraction() == null || request.extraction().individuals() == null
                ? Set.of()
                : request.extraction().individuals().stream().map(CommentAiDraftResult.IndividualPoints::studentId)
                .collect(Collectors.toSet());
        List<Target> targets = new ArrayList<>();
        for (Long id : attitudes.keySet()) {
            targets.add(new Target(rosterById.get(id), attitudes.get(id), List.of(),
                    individualIds.contains(id) ? SOURCE_INDIVIDUAL : SOURCE_CLASS, List.of()));
        }
        String message = response.path("assistantMessage").asText("").isBlank()
                ? (changed > 0 ? "Đã cập nhật " + changed + " học sinh theo yêu cầu." : "Không có dòng nào cần sửa theo yêu cầu này.")
                : response.path("assistantMessage").asText().trim();
        // Giáo viên yêu cầu đổi cách xưng ("xưng cô") — cập nhật để lượt "Viết lại" sau dùng đúng đại từ mới.
        String newPronoun = normalizePronoun(response.path("teacherPronoun").asText(null));
        CommentAiDraftResult.Extraction extraction = request.extraction();
        if (newPronoun != null && extraction != null) {
            extraction = new CommentAiDraftResult.Extraction(extraction.classAttitude(), extraction.classPoints(),
                    extraction.individuals(), newPronoun);
        }
        String pronoun = newPronoun != null ? newPronoun : currentPronoun;
        rephraseRepeatedAfterRevise(context, request, attitudes, contents, contentChanged, pronoun);
        List<CommentAiDraftResult.Row> rows = toRows(context, targets, contents, pronoun);
        return new CommentAiDraftResult(request.transcript(), message, extraction, rows, List.of(), context.skipped());
    }

    private Map<String, Object> revisePayload(DraftContext context, ReviseCommentAiDraftRequest request, Map<Long, String> attitudes,
                                              Map<Long, String> contents, String instruction, String teacherPronoun) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transcript", request.transcript());
        List<ReviseCommentAiDraftRequest.ChatTurn> history = request.history() == null ? List.of() : request.history();
        payload.put("history", history.subList(Math.max(0, history.size() - MAX_HISTORY_TURNS), history.size()));
        payload.put("instruction", instruction);
        payload.put("teacherPronoun", teacherPronoun);
        List<Map<String, Object>> students = new ArrayList<>();
        for (RosterStudent student : context.roster()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("studentId", student.id());
            item.put("fullName", student.fullName());
            item.put("attitude", attitudes.get(student.id()));
            item.put("content", contents.get(student.id()));
            item.put("previousComments", student.previousComments().stream().map(PreviousComment::content).toList());
            item.put("homework", student.homeworkNote());
            student.putSignals(item);
            students.add(item);
        }
        payload.put("students", students);
        return payload;
    }

    /**
     * UC-74 bước 9 + bước 7 (bổ sung 2026-10-01, đã xác nhận với người dùng): dòng vừa sửa theo yêu cầu mà trùng câu
     * chữ với bạn khác trong buổi hoặc với nhận xét cũ của chính học sinh đó, hoặc lặp kiểu câu mở đầu/câu kết/cụm sáo
     * mòn, được nhờ AI diễn đạt lại ĐÚNG 1 lần — giữ nguyên ý và Thái độ, chỉ nhận thay đổi "content" của đúng các dòng
     * đó. AI lỗi thì giữ bản đã sửa; cảnh báo trên dòng vẫn báo như cũ. Lượt này nhằm đa dạng câu chữ nên dùng nhiệt độ
     * của bước viết.
     */
    private void rephraseRepeatedAfterRevise(DraftContext context, ReviseCommentAiDraftRequest request, Map<Long, String> attitudes,
                                             Map<Long, String> contents, Set<Long> changedIds, String teacherPronoun) {
        if (changedIds.isEmpty()) {
            return;
        }
        Map<Long, RosterStudent> rosterById = context.rosterById();
        CommentPatternCheck.Result patterns = CommentPatternCheck.check(contents.entrySet().stream()
                .map(e -> new CommentPatternCheck.Entry(e.getKey(), rosterById.get(e.getKey()).fullName(), e.getValue()))
                .toList(), settings.maxPatternShare());
        List<Long> repeated = new ArrayList<>();
        for (Long id : changedIds) {
            String text = contents.get(id);
            if (text == null || text.isBlank()) {
                continue;
            }
            boolean similarInSession = CommentRuleCheck.bestMatch(text,
                    contents.entrySet().stream().filter(e -> !e.getKey().equals(id)).toList(),
                    Map.Entry::getValue, e -> rosterById.get(e.getKey()).fullName()).atLeast(settings.similarityThreshold());
            boolean similarToPrevious = CommentRuleCheck.bestMatch(text, rosterById.get(id).previousComments(),
                    PreviousComment::content, p -> String.valueOf(p.date())).atLeast(settings.similarityThreshold());
            if (similarInSession || similarToPrevious || patterns.all().contains(id)) {
                repeated.add(id);
            }
        }
        if (repeated.isEmpty()) {
            return;
        }
        String targetsText = repeated.stream().map(id -> rosterById.get(id).fullName() + " (studentId " + id + ")")
                .collect(Collectors.joining(", "));
        String instruction = "Diễn đạt lại câu chữ nhận xét của: " + targetsText + " — đang trùng câu chữ hoặc lặp kiểu câu mở"
                + " đầu/câu kết với bạn khác trong buổi hoặc với nhận xét cũ của chính bạn đó. Giữ nguyên mọi ý và mức Thái độ,"
                + " chỉ đổi cách diễn đạt; không sửa học sinh nào khác.";
        JsonNode response = callJson(REVISE_PROMPT, revisePayload(context, request, attitudes, contents, instruction, teacherPronoun),
                REVISE_RUBRIC_SECTIONS, settings.writeTemperature());
        if (response == null) {
            log.warn("CommentAiDraftService: diễn đạt lại {} dòng trùng sau khi sửa thất bại — giữ bản đã sửa.", repeated.size());
            return;
        }
        Set<Long> allowed = new HashSet<>(repeated);
        for (JsonNode change : response.path("changes")) {
            long id = change.path("studentId").asLong(0);
            String content = change.path("content").asText("").trim();
            if (allowed.remove(id) && !content.isEmpty()) {
                contents.put(id, content);
            }
        }
    }

    // ---- Bước 5: tách ý ----

    private ExtractionOutcome extract(DraftContext context, String teacherText) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("students", context.roster().stream()
                .map(s -> Map.<String, Object>of("studentId", s.id(), "fullName", s.fullName())).toList());
        payload.put("teacherText", teacherText);
        JsonNode response = callJson(EXTRACT_PROMPT, payload, EXTRACT_RUBRIC_SECTIONS, 0);
        if (response == null) {
            throw new CommentAiDraftFailedException("Trợ lý chưa đọc được lời nhận xét (AI lỗi hoặc quá thời gian) — vui lòng thử lại.");
        }
        List<CommentAiDraftResult.IndividualPoints> individuals = new ArrayList<>();
        for (JsonNode node : response.path("individuals")) {
            individuals.add(new CommentAiDraftResult.IndividualPoints(node.path("studentId").asLong(0),
                    node.path("attitude").asText(null), texts(node.path("points")), node.path("evidence").asText(null),
                    ids(node.path("sharedWith"))));
        }
        String pronoun = normalizePronoun(response.path("teacherPronoun").asText(null));
        CommentAiDraftResult.Extraction raw = new CommentAiDraftResult.Extraction(
                response.path("classAttitude").asText(null), texts(response.path("classPoints")), individuals,
                pronoun != null ? pronoun : detectPronoun(teacherText));
        ExtractionOutcome sanitized = sanitize(context, raw);
        List<CommentAiDraftResult.UnmatchedMention> unmatched = new ArrayList<>(sanitized.unmatched());
        Set<Long> rosterIds = context.rosterById().keySet();
        for (JsonNode node : response.path("unmatched")) {
            String quote = node.path("quote").asText("").trim();
            if (quote.isEmpty()) {
                continue;
            }
            List<Long> candidates = new ArrayList<>();
            node.path("candidateStudentIds").forEach(c -> {
                if (rosterIds.contains(c.asLong())) {
                    candidates.add(c.asLong());
                }
            });
            unmatched.add(new CommentAiDraftResult.UnmatchedMention(quote, candidates));
        }
        return new ExtractionOutcome(sanitized.extraction(), unmatched);
    }

    /**
     * Chỉ giữ học sinh thuộc danh sách cần soạn (bước 4) và Thái độ hợp lệ; ý riêng gắn nhầm học sinh ngoài
     * danh sách chuyển thành "chưa xác định" (A6) thay vì âm thầm bỏ hay gán bừa. Bổ sung 2026-10-02: giáo viên nhắc
     * 1 học sinh nhiều lần mà AI vẫn trả nhiều individual cho cùng học sinh thì GỘP ý (trước đây chỉ giữ lần đầu, mất
     * ý của các lần sau); 2 lần có Thái độ khác nhau thì để trống cho giáo viên tự chọn.
     */
    private ExtractionOutcome sanitize(DraftContext context, CommentAiDraftResult.Extraction raw) {
        Set<Long> rosterIds = context.rosterById().keySet();
        Map<Long, CommentAiDraftResult.IndividualPoints> byStudent = new LinkedHashMap<>();
        List<CommentAiDraftResult.UnmatchedMention> unmatched = new ArrayList<>();
        for (CommentAiDraftResult.IndividualPoints item : raw.individuals() == null ? List.<CommentAiDraftResult.IndividualPoints>of() : raw.individuals()) {
            List<String> points = item.points() == null ? List.of() : item.points().stream().filter(p -> p != null && !p.isBlank()).toList();
            if (item.studentId() == null || !rosterIds.contains(item.studentId())) {
                String quote = item.evidence() != null && !item.evidence().isBlank() ? item.evidence() : String.join("; ", points);
                if (!quote.isBlank()) {
                    unmatched.add(new CommentAiDraftResult.UnmatchedMention(quote, List.of()));
                }
                continue;
            }
            List<Long> sharedWith = item.sharedWith() == null ? List.of() : item.sharedWith().stream()
                    .filter(id -> id != null && !id.equals(item.studentId()) && rosterIds.contains(id)).distinct().toList();
            CommentAiDraftResult.IndividualPoints current = new CommentAiDraftResult.IndividualPoints(item.studentId(),
                    normalizeAttitude(item.attitude()), points, item.evidence(), sharedWith);
            byStudent.merge(item.studentId(), current, CommentAiDraftService::mergeMentions);
        }
        List<CommentAiDraftResult.IndividualPoints> individuals = new ArrayList<>(byStudent.values());
        List<String> classPoints = raw.classPoints() == null ? List.of()
                : raw.classPoints().stream().filter(p -> p != null && !p.isBlank()).toList();
        return new ExtractionOutcome(new CommentAiDraftResult.Extraction(normalizeAttitude(raw.classAttitude()), classPoints,
                individuals, normalizePronoun(raw.teacherPronoun())), unmatched);
    }

    /** Gộp 2 lần nhắc cùng 1 học sinh, giữ thứ tự giáo viên nói. */
    private static CommentAiDraftResult.IndividualPoints mergeMentions(CommentAiDraftResult.IndividualPoints first,
                                                                     CommentAiDraftResult.IndividualPoints next) {
        String attitude = first.attitude() == null ? next.attitude()
                : next.attitude() == null || next.attitude().equals(first.attitude()) ? first.attitude() : null;
        List<String> points = Stream.concat(first.points().stream(), next.points().stream()).distinct().toList();
        String evidence = Stream.of(first.evidence(), next.evidence()).filter(e -> e != null && !e.isBlank()).distinct()
                .collect(Collectors.joining(" … "));
        List<Long> sharedWith = Stream.concat(first.sharedWith().stream(), next.sharedWith().stream()).distinct().toList();
        return new CommentAiDraftResult.IndividualPoints(first.studentId(), attitude, points,
                evidence.isEmpty() ? null : evidence, sharedWith);
    }

    /**
     * Học sinh được nhắc riêng dùng ý riêng (và Thái độ riêng — không nói thì để trống, KHÔNG lấy Thái độ
     * chung vì giáo viên nhắc riêng thường là vì khác cả lớp, A7); các học sinh còn lại dùng ý + Thái độ
     * chung. Nhắc riêng đứng trước để lô đầu tiên viết các câu đặc thù nhất.
     */
    private List<Target> buildTargets(DraftContext context, CommentAiDraftResult.Extraction extraction,
                                      Map<Long, String> attitudeOverrides) {
        Map<Long, CommentAiDraftResult.IndividualPoints> individualById = new HashMap<>();
        extraction.individuals().forEach(i -> individualById.put(i.studentId(), i));
        List<Target> individualTargets = new ArrayList<>();
        List<Target> classTargets = new ArrayList<>();
        for (RosterStudent student : context.roster()) {
            CommentAiDraftResult.IndividualPoints individual = individualById.get(student.id());
            if (individual != null) {
                List<String> points = individual.points().isEmpty() ? extraction.classPoints() : individual.points();
                if (!points.isEmpty()) {
                    String attitude = attitudeOverrides.containsKey(student.id()) ? attitudeOverrides.get(student.id()) : individual.attitude();
                    individualTargets.add(new Target(student, attitude, points, SOURCE_INDIVIDUAL,
                            individual.sharedWith() == null ? List.of() : individual.sharedWith()));
                }
            } else if (!extraction.classPoints().isEmpty()) {
                String attitude = attitudeOverrides.containsKey(student.id()) ? attitudeOverrides.get(student.id()) : extraction.classAttitude();
                classTargets.add(new Target(student, attitude, extraction.classPoints(), SOURCE_CLASS, List.of()));
            }
        }
        individualTargets.addAll(classTargets);
        return individualTargets;
    }

    // ---- Bước 6-7: viết + chống trùng lặp ----

    private Map<Long, String> writeAndDeduplicate(DraftContext context, List<Target> targets, List<String> extraAvoid,
                                                  String teacherPronoun) {
        Map<Long, String> contents = new LinkedHashMap<>();
        List<String> avoid = new ArrayList<>(extraAvoid);
        boolean anyWritten = false;
        for (int from = 0; from < targets.size(); from += settings.writeBatchSize()) {
            List<Target> batch = targets.subList(from, Math.min(targets.size(), from + settings.writeBatchSize()));
            Map<Long, String> written = writeBatch(context, batch, avoid, teacherPronoun);
            anyWritten |= !written.isEmpty();
            contents.putAll(written);
            avoid.addAll(written.values());
        }
        // Lô lỗi (AI lỗi/quá thời gian/kết quả dở dang) hoặc AI bỏ sót học sinh: thử lại 1 lần với lô nhỏ hơn
        // (bằng nửa) trước khi gắn cảnh báo NOT_WRITTEN — lô nhỏ trả lời nhanh hơn, ít bị cắt giữa chừng hơn.
        List<Target> missing = targets.stream().filter(t -> !contents.containsKey(t.student().id())).toList();
        if (!missing.isEmpty()) {
            log.warn("CommentAiDraftService: {} học sinh chưa được viết sau lượt đầu — thử lại 1 lần.", missing.size());
            int retryBatchSize = Math.max(1, (settings.writeBatchSize() + 1) / 2);
            for (int from = 0; from < missing.size(); from += retryBatchSize) {
                List<Target> batch = missing.subList(from, Math.min(missing.size(), from + retryBatchSize));
                Map<Long, String> written = writeBatch(context, batch, avoid, teacherPronoun);
                anyWritten |= !written.isEmpty();
                contents.putAll(written);
                avoid.addAll(written.values());
            }
        }
        if (!anyWritten) {
            throw new CommentAiDraftFailedException("Trợ lý chưa viết được nhận xét (AI lỗi hoặc quá thời gian) — vui lòng thử lại.");
        }

        // Bước 7: dòng trùng với học sinh đứng TRƯỚC trong buổi (hoặc với nhận xét cũ của chính mình) được viết lại 1 lần.
        Map<Long, List<String>> similarTextsById = new LinkedHashMap<>();
        List<Long> order = targets.stream().map(t -> t.student().id()).toList();
        for (int i = 0; i < order.size(); i++) {
            String text = contents.get(order.get(i));
            if (text == null) {
                continue;
            }
            Set<String> similar = new LinkedHashSet<>();
            for (int j = 0; j < i; j++) {
                String other = contents.get(order.get(j));
                if (other != null && CommentSimilarity.similarity(text, other) >= settings.similarityThreshold()) {
                    similar.add(other);
                }
            }
            for (PreviousComment previous : targets.get(i).student().previousComments()) {
                if (CommentSimilarity.similarity(text, previous.content()) >= settings.similarityThreshold()) {
                    similar.add(previous.content());
                }
            }
            if (!similar.isEmpty()) {
                similar.add(text);
                similarTextsById.put(order.get(i), new ArrayList<>(similar));
            }
        }
        // Bổ sung 2026-09-29: lặp KIỂU câu mở đầu/câu kết (liền kề hoặc quá maxPatternShare lớp) cũng viết lại —
        // đưa các câu cùng kiểu vào avoidTexts để AI chọn kiểu khác.
        CommentPatternCheck.Result patterns = CommentPatternCheck.check(patternEntries(targets, contents), settings.maxPatternShare());
        for (Long id : patterns.all()) {
            RosterStudent student = context.rosterById().get(id);
            String text = contents.get(id);
            String openingKey = CommentPatternCheck.openingKey(text, student.fullName());
            String closingKey = CommentPatternCheck.closingKey(text);
            List<String> similar = similarTextsById.computeIfAbsent(id, k -> new ArrayList<>(List.of(text)));
            for (Target other : targets) {
                String otherText = contents.get(other.student().id());
                if (other.student().id().equals(id) || otherText == null || similar.contains(otherText)) {
                    continue;
                }
                boolean sameOpening = patterns.openingIds().contains(id) && openingKey != null
                        && openingKey.equals(CommentPatternCheck.openingKey(otherText, other.student().fullName()));
                boolean sameClosing = patterns.closingIds().contains(id) && closingKey != null
                        && closingKey.equals(CommentPatternCheck.closingKey(otherText));
                boolean samePhrase = patterns.phraseIds().contains(id)
                        && CommentPatternCheck.phrasesIn(otherText).stream().anyMatch(patterns.phrasesById().get(id)::contains);
                if (sameOpening || sameClosing || samePhrase) {
                    similar.add(otherText);
                }
            }
        }
        if (similarTextsById.isEmpty()) {
            return contents;
        }
        List<Target> retry = targets.stream().filter(t -> similarTextsById.containsKey(t.student().id())).toList();
        List<String> retryAvoid = new ArrayList<>();
        similarTextsById.values().forEach(retryAvoid::addAll);
        contents.forEach((id, text) -> {
            if (!similarTextsById.containsKey(id)) {
                retryAvoid.add(text);
            }
        });
        for (int from = 0; from < retry.size(); from += settings.writeBatchSize()) {
            List<Target> batch = retry.subList(from, Math.min(retry.size(), from + settings.writeBatchSize()));
            writeBatch(context, batch, retryAvoid, teacherPronoun).forEach((id, text) -> {
                contents.put(id, text);
                retryAvoid.add(text);
            });
        }
        return contents;
    }

    private static List<CommentPatternCheck.Entry> patternEntries(List<Target> targets, Map<Long, String> contents) {
        return targets.stream()
                .map(t -> new CommentPatternCheck.Entry(t.student().id(), t.student().fullName(), contents.get(t.student().id())))
                .toList();
    }

    private Map<Long, String> writeBatch(DraftContext context, List<Target> batch, List<String> avoid, String teacherPronoun) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("teacherPronoun", teacherPronoun);
        List<Map<String, Object>> students = new ArrayList<>();
        for (Target target : batch) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("studentId", target.student().id());
            item.put("fullName", target.student().fullName());
            item.put("callName", target.student().callName());
            item.put("attitude", target.attitude() == null ? null : ATTITUDE_LABELS.get(target.attitude()));
            item.put("points", target.points());
            item.put("previousComments", target.student().previousComments().stream().map(PreviousComment::content).toList());
            item.put("homework", target.student().homeworkNote());
            target.student().putSignals(item);
            if (!target.sharedWith().isEmpty()) {
                item.put("sharedWithStudentIds", target.sharedWith());
            }
            students.add(item);
        }
        payload.put("students", students);
        payload.put("avoidTexts", avoid.subList(Math.max(0, avoid.size() - MAX_AVOID_TEXTS), avoid.size()));
        JsonNode response = callJson(WRITE_PROMPT, payload, WRITE_RUBRIC_SECTIONS, settings.writeTemperature());
        Map<Long, String> written = new LinkedHashMap<>();
        if (response == null) {
            log.warn("CommentAiDraftService: lô viết {} học sinh thất bại (AI lỗi/quá thời gian/kết quả dở dang).", batch.size());
            return written;
        }
        Set<Long> batchIds = batch.stream().map(t -> t.student().id()).collect(Collectors.toSet());
        for (JsonNode node : response.path("comments")) {
            long id = node.path("studentId").asLong(0);
            String content = node.path("content").asText("").trim();
            if (batchIds.contains(id) && !content.isEmpty()) {
                written.putIfAbsent(id, content);
            }
        }
        return written;
    }

    /** Dựng dòng xem trước + cảnh báo cuối cùng (A6/A8/A9 và dòng AI không trả về). */
    private List<CommentAiDraftResult.Row> toRows(DraftContext context, List<Target> targets, Map<Long, String> contents,
                                                  String teacherPronoun) {
        CommentPatternCheck.Result patterns = CommentPatternCheck.check(patternEntries(targets, contents), settings.maxPatternShare());
        List<CommentAiDraftResult.Row> rows = new ArrayList<>();
        for (Target target : targets) {
            Long id = target.student().id();
            String content = contents.get(id);
            List<CommentAiDraftResult.Warning> warnings = new ArrayList<>();
            if (content == null || content.isBlank()) {
                warnings.add(new CommentAiDraftResult.Warning("NOT_WRITTEN", "Trợ lý chưa viết được nhận xét cho học sinh này.", null));
            } else {
                CommentRuleCheck.Match inSession = CommentRuleCheck.bestMatch(content,
                        targets.stream().filter(other -> other != target).toList(),
                        other -> contents.get(other.student().id()), other -> other.student().fullName());
                if (inSession.atLeast(settings.similarityThreshold())) {
                    warnings.add(new CommentAiDraftResult.Warning("SIMILAR_IN_SESSION",
                            CommentRuleCheck.similarInSessionMessage(inSession), inSession.similarity()));
                }
                CommentRuleCheck.Match previous = CommentRuleCheck.bestMatch(content, target.student().previousComments(),
                        PreviousComment::content, p -> String.valueOf(p.date()));
                if (previous.atLeast(settings.similarityThreshold())) {
                    warnings.add(new CommentAiDraftResult.Warning("SIMILAR_TO_PREVIOUS",
                            CommentRuleCheck.similarToPreviousMessage(previous), previous.similarity()));
                }
                if (CommentRuleCheck.mentionsLessonTitle(content, context.lessonContent())) {
                    warnings.add(new CommentAiDraftResult.Warning("LESSON_TITLE",
                            "Nhận xét nhắc tên bài học — giáo viên thường không ghi tên bài vào nhận xét.", null));
                }
                // Đã cảnh báo trùng cả đoạn thì không nhắc thêm trùng kiểu câu (tránh 2 cảnh báo cho cùng 1 lỗi).
                if (!inSession.atLeast(settings.similarityThreshold()) && patterns.all().contains(id)) {
                    warnings.add(new CommentAiDraftResult.Warning("REPEATED_PATTERN",
                            CommentRuleCheck.repeatedPatternMessage(patterns, id), null));
                }
                CommentAiDraftResult.Warning pronounWarning = pronounMismatchWarning(content, teacherPronoun);
                if (pronounWarning != null) {
                    warnings.add(pronounWarning);
                }
                // Bổ sung 2026-09-30 (đã xác nhận với người dùng): độ tuổi trên hệ thống (ngày sinh) có thể chưa chính
                // xác — dòng nào nhắc tới thì giáo viên phải xác thực lại trước khi gửi.
                if (StudentSignalInsight.mentionsStudentInfo(content)) {
                    warnings.add(new CommentAiDraftResult.Warning("STUDENT_INFO_CHECK",
                            "Nhận xét dùng độ tuổi học sinh trên hệ thống — dữ liệu có thể chưa chính xác, thầy/cô xác thực lại.", null));
                }
            }
            CommentAiDraftResult.Warning attitudeAlert = attitudeAlertWarning(target.attitude(), target.student().lowStreak());
            if (attitudeAlert != null) {
                warnings.add(attitudeAlert);
            }
            rows.add(new CommentAiDraftResult.Row(id, target.student().fullName(), target.attitude(), content,
                    target.source(), warnings));
        }
        return rows;
    }

    /**
     * Ghi 1 dòng log chỉ số (JSON, tiền tố {@value #METRICS_LOG_PREFIX}) cho script {@code scripts/comment-ai-metrics.py}
     * — bổ sung 2026-09-29, phương án A không đổi schema. Không chứa nội dung/tên học sinh; lỗi ghi log không làm
     * hỏng kết quả trả giáo viên.
     */
    private void logMetrics(String event, DraftContext context, List<CommentAiDraftResult.Row> rows, int unmatchedCount,
                            String teacherPronoun) {
        try {
            Set<Long> withHomework = context.roster().stream().filter(s -> s.homeworkNote() != null)
                    .map(RosterStudent::id).collect(Collectors.toSet());
            log.info("{} {}", METRICS_LOG_PREFIX, METRICS_MAPPER.writeValueAsString(
                    CommentAiDraftMetrics.of(event, context.classSessionId(), rows, unmatchedCount, teacherPronoun, withHomework)));
        } catch (Exception e) {
            log.warn("CommentAiDraftService: không ghi được log chỉ số ({})", e.getMessage());
        }
    }

    private void appendReviewHints(StringBuilder message, List<CommentAiDraftResult.Row> rows, int unmatchedCount, int skippedCount) {
        if (skippedCount > 0) {
            message.append(" Bỏ qua ").append(skippedCount).append(" học sinh vắng/đã gửi duyệt.");
        }
        if (unmatchedCount > 0) {
            message.append(" Có ").append(unmatchedCount).append(" câu nhắc tên chưa xác định được học sinh — thầy/cô kiểm tra giúp.");
        }
        long warned = rows.stream().filter(r -> !r.warnings().isEmpty()).count();
        if (warned > 0) {
            message.append(" ").append(warned).append(" dòng cần xem lại (xem cảnh báo).");
        }
    }

    // ---- Tiện ích ----

    private JsonNode callJson(String promptFile, Object payload, Set<Integer> rubricSections, double temperature) {
        return jsonCaller.callJson(promptFile, payload, settings.model(), rubricSections, temperature);
    }

    /**
     * UC-74 (bổ sung 2026-09-29, đã xác nhận với người dùng — thay cho đề xuất "AI tự hạ mức để tránh cảnh báo"):
     * AI KHÔNG tự đổi mức Thái độ; chỉ nhắc giáo viên hệ quả khi mức Yếu/Trung bình được duyệt — mỗi buổi đều báo
     * phụ huynh, và đủ chuỗi liên tiếp thì sinh cảnh báo escalation chờ Quản lý duyệt
     * (xem {@link StudentAttitudeAlertTrackingService}).
     */
    static CommentAiDraftResult.Warning attitudeAlertWarning(String attitude, int lowStreak) {
        if (!"WEAK".equals(attitude) && !"AVERAGE".equals(attitude)) {
            return null;
        }
        int threshold = StudentAttitudeAlertTrackingService.escalationThreshold();
        String message = lowStreak + 1 >= threshold
                ? "Đã " + lowStreak + " buổi Yếu/Trung bình liên tiếp — nếu duyệt mức này sẽ chạm mốc cảnh báo " + threshold
                        + " buổi gửi phụ huynh (cần Quản lý duyệt). Kiểm tra lại mức Thái độ."
                : "Mức Yếu/Trung bình sẽ gửi cảnh báo thái độ cho phụ huynh khi nhận xét được duyệt.";
        return new CommentAiDraftResult.Warning("ATTITUDE_ALERT", message, null);
    }

    /** Nhãn tiếng Việt của mã Thái độ (VD GOOD → "Tốt"), {@code null} nếu mã không hợp lệ. */
    static String attitudeLabel(String code) {
        return code == null ? null : ATTITUDE_LABELS.get(code);
    }

    static String normalizeAttitude(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        return ATTITUDE_LABELS.containsKey(value) ? value : null;
    }

    static String normalizePronoun(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.forLanguageTag("vi"));
        return value.equals("thầy") || value.equals("cô") ? value : null;
    }

    /**
     * Dự phòng khi AI không trả {@code teacherPronoun}: giáo viên tự xưng thế nào thì lời nói chứa đại từ đó
     * nhiều hơn hẳn. Chỉ nhận khi CHỈ có 1 trong 2 đại từ xuất hiện — lẫn cả hai (VD "cô giáo chủ nhiệm có
     * nhắc…") thì không đoán.
     */
    static String detectPronoun(String teacherText) {
        if (teacherText == null) {
            return null;
        }
        String text = java.text.Normalizer.normalize(teacherText, java.text.Normalizer.Form.NFC);
        boolean thay = PRONOUN_THAY.matcher(text).find();
        boolean co = PRONOUN_CO.matcher(text).find();
        if (thay == co) {
            return null;
        }
        return thay ? "thầy" : "cô";
    }

    /**
     * Kiểm tra xưng hô (bổ sung 2026-09-29): chưa xác định được giáo viên xưng thầy hay cô thì nhận xét không được
     * có "thầy"/"cô"; đã xác định thì không được lẫn đại từ còn lại. AI đôi khi vẫn chép theo thói quen dù prompt
     * đã cấm, nên chặn thêm bằng quy tắc để giáo viên thấy ngay trước khi Lưu nháp.
     */
    static CommentAiDraftResult.Warning pronounMismatchWarning(String content, String teacherPronoun) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String text = java.text.Normalizer.normalize(content, java.text.Normalizer.Form.NFC);
        boolean thay = PRONOUN_THAY.matcher(text).find();
        boolean co = PRONOUN_CO.matcher(text).find();
        String pronoun = normalizePronoun(teacherPronoun);
        if (pronoun == null && (thay || co)) {
            return new CommentAiDraftResult.Warning("PRONOUN_MISMATCH",
                    "Nhận xét có \"thầy/cô\" nhưng chưa rõ giáo viên xưng thầy hay cô — kiểm tra lại xưng hô.", null);
        }
        if ("thầy".equals(pronoun) && co || "cô".equals(pronoun) && thay) {
            return new CommentAiDraftResult.Warning("PRONOUN_MISMATCH",
                    "Nhận xét dùng \"" + ("thầy".equals(pronoun) ? "cô" : "thầy") + "\" trong khi giáo viên xưng \""
                            + pronoun + "\" — kiểm tra lại xưng hô.", null);
        }
        return null;
    }

    private static List<Long> ids(JsonNode array) {
        List<Long> result = new ArrayList<>();
        array.forEach(n -> {
            if (n.canConvertToLong() && n.asLong() > 0) {
                result.add(n.asLong());
            }
        });
        return result;
    }

    private static List<String> texts(JsonNode array) {
        List<String> result = new ArrayList<>();
        array.forEach(n -> {
            String text = n.asText("").trim();
            if (!text.isEmpty()) {
                result.add(text);
            }
        });
        return result;
    }

    private static String teacherText(String transcript, String note) {
        StringBuilder text = new StringBuilder();
        if (transcript != null && !transcript.isBlank()) {
            text.append("Lời giáo viên (chép từ audio):\n").append(transcript.trim());
        }
        if (note != null && !note.isBlank()) {
            if (!text.isEmpty()) {
                text.append("\n\n");
            }
            text.append("Ghi chú dạng chữ của giáo viên:\n").append(note.trim());
        }
        return text.toString();
    }

    /** Gợi ý chính tả cho STT (Whisper giới hạn prompt ngắn) — danh sách họ tên học sinh của buổi. */
    private static String spellingHint(DraftContext context) {
        String hint = "Nhận xét lớp " + context.className() + ". Học sinh: "
                + context.roster().stream().map(RosterStudent::fullName).collect(Collectors.joining(", ")) + ".";
        return hint.length() > MAX_SPELLING_HINT_LENGTH ? hint.substring(0, MAX_SPELLING_HINT_LENGTH) : hint;
    }
}
