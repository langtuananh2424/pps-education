package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.common.CommentPatternCheck;
import vn.com.pps.education.common.CommentRuleCheck;
import vn.com.pps.education.common.HomeworkScoreInsight;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.StudentComment;
import vn.com.pps.education.dto.AutoProgressPreviewResponse;
import vn.com.pps.education.dto.CommentAiInstructionJobResponse;
import vn.com.pps.education.dto.CommentAiRejectionReasonJobResponse;
import vn.com.pps.education.dto.CommentAiRejectionReasonResult;
import vn.com.pps.education.dto.CommentAiInstructionResult;
import vn.com.pps.education.dto.CommentAiReviewJobResponse;
import vn.com.pps.education.dto.CommentAiReviewRequest;
import vn.com.pps.education.dto.CommentAiReviewResult;
import vn.com.pps.education.dto.CommentAiSuggestionJobResponse;
import vn.com.pps.education.dto.CommentAiSuggestionRequest;
import vn.com.pps.education.dto.CommentAiSuggestionResult;
import vn.com.pps.education.dto.CommentAttitudeAlertPreviewResponse;
import vn.com.pps.education.exception.CommentAiDraftFailedException;
import vn.com.pps.education.exception.CommentAiDraftRejectedException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.StudentCommentRepository;

import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UC-75: Trợ lý AI soát nhận xét chờ duyệt (mở rộng UC-22/FR-LMS-09 — bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng 2026-09-29). Xem docs/uc/phan-he-06-hoc-thuat.md để biết đầy đủ Main Flow/Alternate Flow.
 *
 * <p>Nguyên tắc: trợ lý chỉ GỢI Ý — không duyệt, không từ chối, không tự sửa. Service này không ghi DB: chỉ
 * đọc nhận xét (cùng rào với duyệt/sửa nội dung PENDING của UC-22) rồi giao phần gọi AI cho
 * {@link AiJobRegistry}. Bản sửa AI đề xuất chỉ được lưu khi Quản lý bấm "Áp dụng", qua đúng
 * {@code StudentCommentService#updatePendingCommentContent} sẵn có.</p>
 *
 * <p>Soát gồm 2 lớp: kiểm tra bằng code (không tốn AI, luôn chạy — chữ số, quá dài, trống, nhắc họ tên bạn
 * cùng lớp, trùng lặp trong buổi và với buổi trước) và kiểm tra theo rubric bằng AI (theo lô từng buổi). AI
 * lỗi ở lô nào thì lô đó vẫn có kết quả kiểm tra bằng code (UC-75 A4).</p>
 */
@Service
public class CommentAiReviewService {

    static final String REVIEW_PROMPT = "comment-ai-review-system-prompt.txt";
    static final String SUGGEST_PROMPT = "comment-ai-suggest-system-prompt.txt";
    static final String INSTRUCTION_PROMPT = "comment-ai-manager-instruction-system-prompt.txt";
    static final String REJECTION_REASON_PROMPT = "comment-ai-rejection-reason-system-prompt.txt";
    private static final int MAX_SPELLING_HINT_LENGTH = 800;

    static final String SOURCE_RULE = "RULE";
    static final String SOURCE_AI = "AI";
    private static final Set<String> AI_ISSUE_TYPES = Set.of(
            "OTHER_STUDENT", "HOMEWORK_OR_SCORE", "HARSH_WORDING", "ATTITUDE_MISMATCH", "FORBIDDEN_TOPIC", "OTHER",
            "HOMEWORK_MISMATCH");
    /** AI trả loại này là LƯU Ý (không phải lỗi nội dung) — xem {@link CommentAiReviewResult.Review#notices()}. */
    private static final Set<String> AI_NOTICE_TYPES = Set.of("HOMEWORK_MISMATCH");
    /** Nhãn ngắn từng loại lỗi cho câu tóm tắt lô (UC-75 bổ sung 2026-09-29). */
    private static final Map<String, String> ISSUE_LABELS = Map.ofEntries(
            Map.entry("CONTAINS_DIGITS", "có chữ số"), Map.entry("TOO_LONG", "quá dài"), Map.entry("EMPTY", "để trống"),
            Map.entry("OTHER_STUDENT_NAME", "nhắc bạn khác"), Map.entry("OTHER_STUDENT", "nhắc bạn khác"),
            Map.entry("LESSON_TITLE", "nhắc tên bài học"), Map.entry("SIMILAR_IN_SESSION", "giống bạn khác trong buổi"),
            Map.entry("SIMILAR_TO_PREVIOUS", "giống buổi trước"), Map.entry("HOMEWORK_OR_SCORE", "ghi điểm/hạn nộp"),
            Map.entry("HARSH_WORDING", "từ ngữ nặng"), Map.entry("ATTITUDE_MISMATCH", "Thái độ không khớp nội dung"),
            Map.entry("FORBIDDEN_TOPIC", "chủ đề không nên nhắc"), Map.entry("OTHER", "lỗi khác"));
    /** Rubric nhận xét đặt mức khoảng 400 ký tự — chỉ cảnh báo khi vượt rõ rệt. */
    static final int MAX_CONTENT_LENGTH = 500;
    private static final int REVIEW_BATCH_SIZE = 15;
    /**
     * Mục rubric mỗi bước cần (bổ sung 2026-10-01, xem {@link CommentAiJsonCaller#selectRubricSections}): soát lỗi và
     * soạn lý do từ chối cần tiêu chí Thái độ (mục 1, để bắt Thái độ lệch nội dung) + cấu trúc/văn phong/điều cấm
     * (mục 2-4); đề xuất sửa và sửa theo yêu cầu Quản lý chỉ đổi câu chữ, không đổi Thái độ (mục 2-4). Mọi bước UC-75
     * giữ nhiệt độ 0 — soát và sửa tối thiểu cần ổn định.
     */
    static final Set<Integer> REVIEW_RUBRIC_SECTIONS = Set.of(1, 2, 3, 4);
    static final Set<Integer> EDIT_RUBRIC_SECTIONS = Set.of(2, 3, 4);

    private final StudentCommentService studentCommentService;
    private final StudentAttitudeAlertTrackingService attitudeAlertTrackingService;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final StudentCommentRepository studentCommentRepository;
    private final CommentAiJsonCaller jsonCaller;
    private final AiJobRegistry jobRegistry;
    private final NineRouterAiClient aiClient;
    private final String model;
    private final long maxAudioBytes;
    private final int previousCommentCount;
    private final int previousLookbackDays;
    private final double similarityThreshold;
    private final double maxPatternShare;

    public CommentAiReviewService(StudentCommentService studentCommentService,
                                  StudentAttitudeAlertTrackingService attitudeAlertTrackingService,
                                  ClassEnrollmentRepository classEnrollmentRepository,
                                  StudentCommentRepository studentCommentRepository,
                                  CommentAiJsonCaller jsonCaller,
                                  AiJobRegistry jobRegistry,
                                  NineRouterAiClient aiClient,
                                  @Value("${app.ai-comment-draft.model:comment-pps}") String model,
                                  @Value("${app.ai-comment-draft.previous-comment-count:3}") int previousCommentCount,
                                  @Value("${app.ai-comment-draft.previous-lookback-days:120}") int previousLookbackDays,
                                  @Value("${app.ai-comment-draft.similarity-threshold:0.5}") double similarityThreshold,
                                  @Value("${app.ai-comment-draft.max-audio-bytes:20971520}") long maxAudioBytes,
                                  @Value("${app.ai-comment-draft.max-pattern-share:0.3}") double maxPatternShare) {
        this.studentCommentService = studentCommentService;
        this.attitudeAlertTrackingService = attitudeAlertTrackingService;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.studentCommentRepository = studentCommentRepository;
        this.jsonCaller = jsonCaller;
        this.jobRegistry = jobRegistry;
        this.aiClient = aiClient;
        this.model = model;
        this.maxAudioBytes = maxAudioBytes;
        this.previousCommentCount = previousCommentCount;
        this.previousLookbackDays = previousLookbackDays;
        this.similarityThreshold = similarityThreshold;
        this.maxPatternShare = maxPatternShare;
    }

    /** Bản chụp 1 nhận xét chờ duyệt (đọc trong request, dùng ở luồng nền — không chạm entity JPA). */
    /**
     * @param homeworkData  kết quả BTVN buổi trước đã quy ra lời từ điểm đã lưu trên dòng (null nếu không có) — để
     *                      AI đối chiếu nhận xét có nói ngược dữ liệu không (HOMEWORK_MISMATCH).
     * @param attitudeAlert lời nhắc duyệt dòng này sẽ báo phụ huynh (null nếu không phải Yếu/Trung bình).
     */
    record ReviewItem(Long commentId, Long classSessionId, String studentFullName, String attitude, String content,
                      LocalDate commentDate, String lessonContent, List<String> classmateNames,
                      List<CommentAiDraftService.PreviousComment> previousComments, String homeworkData,
                      CommentAttitudeAlertPreviewResponse.Item attitudeAlert) {
        ReviewItem(Long commentId, Long classSessionId, String studentFullName, String attitude, String content,
                   LocalDate commentDate, String lessonContent, List<String> classmateNames,
                   List<CommentAiDraftService.PreviousComment> previousComments) {
            this(commentId, classSessionId, studentFullName, attitude, content, commentDate, lessonContent, classmateNames,
                    previousComments, null, null);
        }
    }

    // ---- Điểm vào từ Controller ----

    /** UC-75 Main Flow bước 1-2: kiểm tra rào (A1, A2) trong request, chạy nền bước 3-5. */
    @Transactional(readOnly = true)
    public CommentAiReviewJobResponse startReview(CommentAiReviewRequest request, Long actorUserId) {
        List<ReviewItem> items = loadItems(request.commentIds(), actorUserId);
        return toReviewResponse(jobRegistry.submit(actorUserId, AiJobRegistry.Lane.REVIEW, () -> review(items)));
    }

    public CommentAiReviewJobResponse getReview(String jobId, Long actorUserId) {
        return toReviewResponse(jobRegistry.get(jobId, actorUserId, CommentAiReviewResult.class));
    }

    /** UC-75 Main Flow bước 6: đề xuất bản sửa cho 1 nhận xét (chưa ghi DB). */
    @Transactional(readOnly = true)
    public CommentAiSuggestionJobResponse startSuggestion(Long commentId, CommentAiSuggestionRequest request, Long actorUserId) {
        ReviewItem item = loadItems(List.of(commentId), actorUserId).get(0);
        List<String> issues = request == null || request.issues() == null ? List.of()
                : request.issues().stream().filter(i -> i != null && !i.isBlank()).toList();
        return toSuggestionResponse(jobRegistry.submit(actorUserId, AiJobRegistry.Lane.REVIEW, () -> suggest(item, issues)));
    }

    public CommentAiSuggestionJobResponse getSuggestion(String jobId, Long actorUserId) {
        return toSuggestionResponse(jobRegistry.get(jobId, actorUserId, CommentAiSuggestionResult.class));
    }

    /**
     * UC-75 Main Flow bước 9 (bổ sung 2026-09-29) — Quản lý ra yêu cầu sửa bằng audio (≤ 5 phút, STT tiếng
     * Việt) và/hoặc chữ cho các nhận xét chờ duyệt đang xem (thường 1 lớp). Đầu vào không hợp lệ bị từ chối
     * ngay (A7), cùng quy tắc với trợ lý soạn nháp UC-74.
     */
    @Transactional(readOnly = true)
    public CommentAiInstructionJobResponse startInstruction(List<Long> commentIds, MultipartFile audio, String note, Long actorUserId) {
        boolean hasAudio = audio != null && !audio.isEmpty();
        boolean hasNote = note != null && !note.isBlank();
        if (!hasAudio && !hasNote) {
            throw new CommentAiDraftRejectedException("Cần gửi audio hoặc yêu cầu dạng chữ cho trợ lý.");
        }
        if (commentIds == null || commentIds.isEmpty() || commentIds.size() > 300) {
            throw new CommentAiDraftRejectedException("Chọn lớp có nhận xét chờ duyệt trước khi gửi yêu cầu.");
        }
        byte[] audioBytes = null;
        String mimeType = null;
        if (hasAudio) {
            if (audio.getSize() > maxAudioBytes) {
                throw new CommentAiDraftRejectedException("File audio quá lớn (tối đa " + (maxAudioBytes / (1024 * 1024))
                        + " MB) — mỗi lần gửi tối đa 5 phút.");
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
        List<ReviewItem> items = loadItems(commentIds, actorUserId);
        byte[] finalAudio = audioBytes;
        String finalMimeType = mimeType;
        String finalNote = hasNote ? note.trim() : null;
        return toInstructionResponse(jobRegistry.submit(actorUserId, AiJobRegistry.Lane.REVIEW, () -> instruct(items, finalAudio, finalMimeType, finalNote)));
    }

    public CommentAiInstructionJobResponse getInstruction(String jobId, Long actorUserId) {
        return toInstructionResponse(jobRegistry.get(jobId, actorUserId, CommentAiInstructionResult.class));
    }

    /**
     * UC-75 (bổ sung 2026-09-29) — nhắc ngay trên bảng chờ duyệt dòng nào duyệt sẽ gửi cảnh báo thái độ cho phụ
     * huynh (và dòng nào chạm mốc cảnh báo 3 buổi liên tiếp). Chỉ đọc, không gọi AI; cùng rào với soát.
     */
    @Transactional(readOnly = true)
    public CommentAttitudeAlertPreviewResponse previewAttitudeAlerts(CommentAiReviewRequest request, Long actorUserId) {
        List<StudentComment> comments = studentCommentService.requirePendingCommentsForAiReview(request.commentIds(), actorUserId);
        List<CommentAttitudeAlertPreviewResponse.Item> items = new ArrayList<>(attitudeAlerts(comments).values());
        items.sort(Comparator.comparing(CommentAttitudeAlertPreviewResponse.Item::commentId));
        return new CommentAttitudeAlertPreviewResponse(items);
    }

    /**
     * UC-75 (bổ sung 2026-09-29) — AI soạn sẵn lý do từ chối gửi giáo viên cho 1 nhận xét chờ duyệt. Chỉ trả
     * văn bản: Quản lý sửa rồi tự bấm Từ chối qua đúng chức năng của UC-22 — trợ lý không tự từ chối.
     */
    @Transactional(readOnly = true)
    public CommentAiRejectionReasonJobResponse startRejectionReason(Long commentId, CommentAiSuggestionRequest request, Long actorUserId) {
        ReviewItem item = loadItems(List.of(commentId), actorUserId).get(0);
        List<String> issues = request == null || request.issues() == null ? List.of()
                : request.issues().stream().filter(i -> i != null && !i.isBlank()).toList();
        return toRejectionReasonResponse(jobRegistry.submit(actorUserId, AiJobRegistry.Lane.REVIEW, () -> rejectionReason(item, issues)));
    }

    public CommentAiRejectionReasonJobResponse getRejectionReason(String jobId, Long actorUserId) {
        return toRejectionReasonResponse(jobRegistry.get(jobId, actorUserId, CommentAiRejectionReasonResult.class));
    }

    /**
     * UC-75 bước 2: rào của UC-22 (A1 không có quyền/không phụ trách điểm trường, A2 nhận xét không còn chờ
     * duyệt) + chụp tên các bạn cùng lớp và N nhận xét trước của từng học sinh.
     */
    List<ReviewItem> loadItems(List<Long> commentIds, Long actorUserId) {
        List<StudentComment> comments = studentCommentService.requirePendingCommentsForAiReview(commentIds, actorUserId);

        Map<Long, List<String>> classmatesByClass = new HashMap<>();
        for (Long classId : comments.stream().map(c -> c.getSchoolClass().getId()).collect(Collectors.toSet())) {
            classmatesByClass.put(classId, classEnrollmentRepository.findBySchoolClassIdAndStatus(classId, ClassEnrollment.Status.ACTIVE)
                    .stream().map(e -> e.getStudent().getUser().getFullName()).toList());
        }

        List<Long> studentIds = comments.stream().map(c -> c.getStudent().getId()).distinct().toList();
        LocalDate latest = comments.stream().map(StudentComment::getCommentDate).max(Comparator.naturalOrder()).orElseThrow();
        LocalDate earliest = comments.stream().map(StudentComment::getCommentDate).min(Comparator.naturalOrder()).orElseThrow();
        Set<Long> reviewedIds = new HashSet<>(commentIds);
        Map<Long, List<StudentComment>> historyByStudent = studentCommentRepository.findRecentByStudentIds(studentIds,
                        StudentComment.Status.REJECTED, earliest.minusDays(previousLookbackDays), latest).stream()
                .filter(c -> !reviewedIds.contains(c.getId()) && c.getContent() != null && !c.getContent().isBlank())
                .collect(Collectors.groupingBy(c -> c.getStudent().getId()));

        Map<Long, CommentAttitudeAlertPreviewResponse.Item> alerts = attitudeAlerts(comments);
        Map<Long, AutoProgressPreviewResponse> autoProgress = studentCommentService.previousAutoProgressOf(comments);
        List<ReviewItem> items = new ArrayList<>();
        for (StudentComment comment : comments) {
            List<CommentAiDraftService.PreviousComment> previous = historyByStudent.getOrDefault(comment.getStudent().getId(), List.of())
                    .stream().filter(c -> c.getCommentDate().isBefore(comment.getCommentDate()))
                    .limit(previousCommentCount)
                    .map(c -> new CommentAiDraftService.PreviousComment(c.getCommentDate(), c.getContent().trim()))
                    .toList();
            String studentName = comment.getStudent().getUser().getFullName();
            List<String> classmates = classmatesByClass.getOrDefault(comment.getSchoolClass().getId(), List.of()).stream()
                    .filter(name -> !name.equals(studentName)).toList();
            items.add(new ReviewItem(comment.getId(), comment.getClassSession() == null ? null : comment.getClassSession().getId(),
                    studentName, comment.getAttitude() == null ? null : comment.getAttitude().name(),
                    comment.getContent(), comment.getCommentDate(),
                    comment.getClassSession() == null ? null : comment.getClassSession().getLessonContent(),
                    classmates, previous, homeworkData(comment, autoProgress.get(comment.getId())), alerts.get(comment.getId())));
        }
        items.sort(Comparator.comparing(ReviewItem::commentId));
        return items;
    }

    /**
     * Mô phỏng (chỉ đọc) {@code StudentAttitudeAlertTrackingService#evaluateAndNotify} nếu duyệt các dòng theo thứ tự
     * ngày: mỗi dòng Yếu/Trung bình báo phụ huynh; chuỗi (tính từ số buổi liên tiếp đã duyệt) chạm mốc thì tạo
     * cảnh báo escalation rồi về 0; dòng Thái độ khác đưa chuỗi về 0; dòng chưa có Thái độ không ảnh hưởng.
     */
    Map<Long, CommentAttitudeAlertPreviewResponse.Item> attitudeAlerts(List<StudentComment> comments) {
        int threshold = StudentAttitudeAlertTrackingService.escalationThreshold();
        Map<Long, CommentAttitudeAlertPreviewResponse.Item> result = new HashMap<>();
        Map<Long, List<StudentComment>> byClass = comments.stream().collect(Collectors.groupingBy(c -> c.getSchoolClass().getId()));
        for (List<StudentComment> classComments : byClass.values()) {
            boolean anyLow = classComments.stream().anyMatch(c -> isLow(c.getAttitude()));
            if (!anyLow) {
                continue;
            }
            SchoolClass schoolClass = classComments.get(0).getSchoolClass();
            Map<Long, List<StudentComment>> byStudent = classComments.stream().collect(Collectors.groupingBy(c -> c.getStudent().getId()));
            Map<Long, Integer> streaks = attitudeAlertTrackingService.currentLowStreaks(schoolClass, byStudent.keySet());
            byStudent.forEach((studentId, studentComments) -> {
                int running = streaks.getOrDefault(studentId, 0);
                int earlierPendingLow = 0;
                List<StudentComment> ordered = studentComments.stream()
                        .sorted(Comparator.comparing(StudentComment::getCommentDate).thenComparing(StudentComment::getId)).toList();
                for (StudentComment comment : ordered) {
                    StudentComment.Attitude attitude = comment.getAttitude();
                    if (attitude == null) {
                        continue;
                    }
                    if (!isLow(attitude)) {
                        running = 0;
                        continue;
                    }
                    running++;
                    boolean escalation = running >= threshold;
                    String label = CommentAiDraftService.attitudeLabel(attitude.name());
                    String message = escalation
                            ? "Buổi " + label + " thứ " + running + " liên tiếp — duyệt dòng này sẽ báo phụ huynh và tạo cảnh báo "
                                    + threshold + " buổi liên tiếp (chờ Quản lý duyệt gửi phụ huynh)."
                            : "Duyệt dòng này sẽ gửi cảnh báo thái độ \"" + label + "\" cho phụ huynh"
                                    + (running > 1 ? " — đã " + running + " buổi Yếu/Trung bình liên tiếp, thêm " + (threshold - running)
                                            + " buổi nữa sẽ chạm mốc cảnh báo " + threshold + " buổi." : ".");
                    if (earlierPendingLow > 0) {
                        // Nhãn tính như khi duyệt các buổi theo thứ tự ngày (decideComments cũng xử lý theo ngày);
                        // duyệt riêng dòng này trước các buổi cũ hơn thì chuỗi thực tế sẽ ngắn hơn.
                        message += " (Đã tính cả " + earlierPendingLow + " buổi Yếu/Trung bình trước đó đang chờ duyệt — nên duyệt theo thứ tự ngày.)";
                    }
                    result.put(comment.getId(), new CommentAttitudeAlertPreviewResponse.Item(comment.getId(), running, escalation, message));
                    earlierPendingLow++;
                    if (escalation) {
                        running = 0;
                    }
                }
            });
        }
        return result;
    }

    private static boolean isLow(StudentComment.Attitude attitude) {
        return attitude == StudentComment.Attitude.WEAK || attitude == StudentComment.Attitude.AVERAGE;
    }

    /**
     * Kết quả BTVN buổi trước quy ra lời (mọi mức, kể cả "làm được") — không đưa con số cho AI. Mirror các cột Quản
     * lý thấy trên bảng duyệt: điểm nhập tay đã lưu trên dòng; kênh online chưa nhập tay thì lấy % tự động
     * (bổ sung 2026-09-29), Reading/Writing online luôn là % tự động. Bổ sung 2026-09-30: mô tả theo từng kỹ năng
     * (cùng cách quy kênh → kỹ năng với UC-74) để bắt được mâu thuẫn cụ thể, VD nhận xét khen kỹ năng nghe nhưng BTVN
     * nghe chưa hoàn thành.
     *
     * @param auto % tự động BTVN buổi trước của nhận xét này, {@code null} nếu không có.
     */
    static String homeworkData(StudentComment comment, AutoProgressPreviewResponse auto) {
        boolean foreign = comment.getClassSession() != null
                && comment.getClassSession().getTeacherType() == ClassSession.TeacherType.FOREIGN;
        String mainSkill = HomeworkScoreInsight.mainChannelSkill(foreign);
        String videoSkill = HomeworkScoreInsight.videoChannelSkill(foreign);
        List<HomeworkScoreInsight.Channel> channels = new ArrayList<>();
        channels.add(new HomeworkScoreInsight.Channel(mainSkill, "bài tập",
                firstNonBlank(comment.getHomeworkPreviousScore(), auto == null ? null : auto.grammarPreviousProgress())));
        channels.add(new HomeworkScoreInsight.Channel(videoSkill, "video ôn tập",
                firstNonBlank(comment.getHomeworkPreviousSpeakingScore(), auto == null ? null : auto.videoPreviousProgress())));
        channels.add(new HomeworkScoreInsight.Channel(HomeworkScoreInsight.SKILL_READING, "bài trên giấy", comment.getHomeworkPreviousReadingScore()));
        channels.add(new HomeworkScoreInsight.Channel(HomeworkScoreInsight.SKILL_WRITING, "bài trên giấy", comment.getHomeworkPreviousWritingScore()));
        if (auto != null) {
            channels.add(new HomeworkScoreInsight.Channel(HomeworkScoreInsight.SKILL_READING, "bài online", auto.readingPreviousProgress()));
            channels.add(new HomeworkScoreInsight.Channel(HomeworkScoreInsight.SKILL_WRITING, "bài online", auto.writingPreviousProgress()));
        }
        return HomeworkScoreInsight.describe(channels, true, java.util.OptionalInt.empty(), java.util.OptionalInt.empty(), 0);
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    // ---- Luồng nền ----

    /** UC-75 Main Flow bước 3-5. */
    CommentAiReviewResult review(List<ReviewItem> items) {
        Map<Long, List<CommentAiReviewResult.Issue>> issuesById = new LinkedHashMap<>();
        Map<Long, List<CommentAiReviewResult.Notice>> noticesById = new LinkedHashMap<>();
        items.forEach(item -> {
            issuesById.put(item.commentId(), new ArrayList<>(ruleIssues(item, items)));
            List<CommentAiReviewResult.Notice> notices = new ArrayList<>();
            if (item.attitudeAlert() != null) {
                notices.add(new CommentAiReviewResult.Notice("ATTITUDE_ALERT", SOURCE_RULE, item.attitudeAlert().message()));
            }
            noticesById.put(item.commentId(), notices);
        });
        // Bổ sung 2026-09-29 — giáo viên dùng chung 1 khuôn câu cho cả buổi (câu mở/kết, cụm sáo mòn): lưu ý, không chặn duyệt.
        items.stream().collect(Collectors.groupingBy(i -> i.classSessionId() == null ? -1L : i.classSessionId(), LinkedHashMap::new, Collectors.toList()))
                .values().forEach(sessionItems -> {
                    CommentPatternCheck.Result patterns = CommentPatternCheck.check(sessionItems.stream()
                            .map(i -> new CommentPatternCheck.Entry(i.commentId(), i.studentFullName(), i.content())).toList(), maxPatternShare);
                    for (ReviewItem item : sessionItems) {
                        boolean similarFlagged = issuesById.get(item.commentId()).stream().anyMatch(i -> "SIMILAR_IN_SESSION".equals(i.type()));
                        if (patterns.all().contains(item.commentId()) && !similarFlagged) {
                            noticesById.get(item.commentId()).add(new CommentAiReviewResult.Notice("REPEATED_PATTERN", SOURCE_RULE,
                                    CommentRuleCheck.repeatedPatternMessage(patterns, item.commentId())));
                        }
                    }
                });

        boolean aiComplete = true;
        Map<Long, List<ReviewItem>> bySession = items.stream()
                .collect(Collectors.groupingBy(i -> i.classSessionId() == null ? -1L : i.classSessionId(), LinkedHashMap::new, Collectors.toList()));
        for (List<ReviewItem> sessionItems : bySession.values()) {
            for (int from = 0; from < sessionItems.size(); from += REVIEW_BATCH_SIZE) {
                List<ReviewItem> batch = sessionItems.subList(from, Math.min(sessionItems.size(), from + REVIEW_BATCH_SIZE));
                Map<Long, List<CommentAiReviewResult.Issue>> aiIssues = aiReviewBatch(batch);
                if (aiIssues == null) {
                    aiComplete = false;
                    continue;
                }
                aiIssues.forEach((id, list) -> {
                    List<CommentAiReviewResult.Issue> existing = issuesById.get(id);
                    boolean hasRuleOtherStudent = existing.stream().anyMatch(i -> "OTHER_STUDENT_NAME".equals(i.type()));
                    for (CommentAiReviewResult.Issue issue : list) {
                        if (AI_NOTICE_TYPES.contains(issue.type())) {
                            noticesById.get(id).add(new CommentAiReviewResult.Notice(issue.type(), issue.source(), issue.message()));
                        } else if (!(hasRuleOtherStudent && "OTHER_STUDENT".equals(issue.type()))) {
                            existing.add(issue);
                        }
                    }
                });
            }
        }

        List<CommentAiReviewResult.Review> reviews = items.stream()
                .map(i -> new CommentAiReviewResult.Review(i.commentId(), i.studentFullName(), List.copyOf(issuesById.get(i.commentId())),
                        List.copyOf(noticesById.get(i.commentId()))))
                .toList();
        CommentAiReviewResult.Summary summary = summarize(items, reviews);
        int flagged = items.size() - summary.cleanCount();
        return new CommentAiReviewResult(summaryMessage(items.size(), summary, aiComplete), items.size(), flagged, aiComplete,
                reviews, summary);
    }

    /** UC-75 (bổ sung 2026-09-29) — tóm tắt cả lô bằng code: đếm dòng theo loại lỗi và dòng sẽ báo phụ huynh. */
    static CommentAiReviewResult.Summary summarize(List<ReviewItem> items, List<CommentAiReviewResult.Review> reviews) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        int clean = 0;
        int homeworkMismatch = 0;
        int repeatedPattern = 0;
        for (CommentAiReviewResult.Review review : reviews) {
            if (review.issues().isEmpty()) {
                clean++;
            }
            // Đếm theo dòng: 1 dòng có 2 lỗi cùng nhóm nhãn chỉ tính 1 lần.
            review.issues().stream().map(i -> "OTHER_STUDENT".equals(i.type()) ? "OTHER_STUDENT_NAME" : i.type()).distinct()
                    .forEach(type -> counts.merge(type, 1, Integer::sum));
            if (review.notices().stream().anyMatch(n -> "HOMEWORK_MISMATCH".equals(n.type()))) {
                homeworkMismatch++;
            }
            if (review.notices().stream().anyMatch(n -> "REPEATED_PATTERN".equals(n.type()))) {
                repeatedPattern++;
            }
        }
        List<CommentAiReviewResult.IssueCount> issueCounts = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> new CommentAiReviewResult.IssueCount(e.getKey(), e.getValue())).toList();
        int parentAlerts = (int) items.stream().filter(i -> i.attitudeAlert() != null).count();
        int escalations = (int) items.stream().filter(i -> i.attitudeAlert() != null && i.attitudeAlert().escalation()).count();
        return new CommentAiReviewResult.Summary(clean, issueCounts, parentAlerts, escalations, homeworkMismatch, repeatedPattern);
    }

    private static String summaryMessage(int total, CommentAiReviewResult.Summary summary, boolean aiComplete) {
        int flagged = total - summary.cleanCount();
        StringBuilder message = new StringBuilder("Đã soát ").append(total).append(" nhận xét: ");
        if (flagged == 0) {
            message.append("không phát hiện lỗi nào.");
        } else {
            message.append(flagged).append(" dòng có cảnh báo, ").append(summary.cleanCount()).append(" dòng ổn (")
                    .append(summary.issueCounts().stream()
                            .map(c -> c.count() + " " + ISSUE_LABELS.getOrDefault(c.type(), c.type()))
                            .collect(Collectors.joining(", ")))
                    .append(").");
        }
        if (summary.parentAlertCount() > 0) {
            message.append(" ").append(summary.parentAlertCount()).append(" dòng Yếu/Trung bình sẽ báo phụ huynh khi duyệt");
            if (summary.escalationCount() > 0) {
                message.append(", trong đó ").append(summary.escalationCount()).append(" dòng chạm mốc cảnh báo ")
                        .append(StudentAttitudeAlertTrackingService.escalationThreshold()).append(" buổi liên tiếp");
            }
            message.append(".");
        }
        if (summary.repeatedPatternCount() > 0) {
            message.append(" ").append(summary.repeatedPatternCount())
                    .append(" dòng dùng chung khuôn câu với nhiều bạn (câu mở/kết hoặc cụm lặp lại) — nên nhắc giáo viên đa dạng cách viết.");
        }
        if (summary.homeworkMismatchCount() > 0) {
            message.append(" ").append(summary.homeworkMismatchCount()).append(" dòng nhắc BTVN có vẻ ngược dữ liệu điểm — nên xem lại.");
        }
        if (!aiComplete) {
            message.append(" Phần kiểm tra theo rubric bằng AI bị lỗi ở một số dòng — các dòng đó chỉ có kết quả kiểm tra tự động.");
        }
        return message.toString();
    }

    /** UC-75 bước 3 — kiểm tra bằng code, không gọi AI. */
    List<CommentAiReviewResult.Issue> ruleIssues(ReviewItem item, List<ReviewItem> all) {
        List<CommentAiReviewResult.Issue> issues = new ArrayList<>();
        String content = item.content() == null ? "" : item.content().trim();
        if (content.isEmpty()) {
            issues.add(rule("EMPTY", "Nhận xét đang để trống."));
            return issues;
        }
        if (CommentRuleCheck.containsDigits(content)) {
            issues.add(rule("CONTAINS_DIGITS", "Nhận xét có chữ số — kiểm tra có nhắc điểm/số liệu không."));
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            issues.add(rule("TOO_LONG", "Nhận xét dài " + content.length() + " ký tự (rubric khoảng 400)."));
        }
        if (CommentRuleCheck.mentionsLessonTitle(content, item.lessonContent())) {
            issues.add(rule("LESSON_TITLE", "Nhắc tên bài học \"" + item.lessonContent().trim() + "\" — thường không ghi tên bài vào nhận xét."));
        }
        String normalizedContent = normalize(content);
        for (String classmate : item.classmateNames()) {
            if (classmate != null && !classmate.isBlank() && normalizedContent.contains(normalize(classmate))) {
                issues.add(rule("OTHER_STUDENT_NAME", "Nhắc họ tên bạn khác trong lớp: \"" + classmate + "\"."));
            }
        }
        CommentRuleCheck.Match inSession = CommentRuleCheck.bestMatch(content, all.stream()
                .filter(other -> other != item && other.classSessionId() != null && other.classSessionId().equals(item.classSessionId()))
                .toList(), ReviewItem::content, ReviewItem::studentFullName);
        if (inSession.atLeast(similarityThreshold)) {
            issues.add(rule("SIMILAR_IN_SESSION", CommentRuleCheck.similarInSessionMessage(inSession)));
        }
        CommentRuleCheck.Match previous = CommentRuleCheck.bestMatch(content, item.previousComments(),
                CommentAiDraftService.PreviousComment::content, p -> String.valueOf(p.date()));
        if (previous.atLeast(similarityThreshold)) {
            issues.add(rule("SIMILAR_TO_PREVIOUS", CommentRuleCheck.similarToPreviousMessage(previous)));
        }
        return issues;
    }

    /** UC-75 bước 4 — kiểm tra theo rubric bằng AI cho 1 lô cùng buổi; {@code null} nếu AI lỗi (A4). */
    private Map<Long, List<CommentAiReviewResult.Issue>> aiReviewBatch(List<ReviewItem> batch) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("classmates", batch.get(0).classmateNames().isEmpty() ? List.of()
                : new ArrayList<>(new java.util.LinkedHashSet<>(batch.stream().flatMap(i -> i.classmateNames().stream()).toList())));
        List<Map<String, Object>> comments = new ArrayList<>();
        for (ReviewItem item : batch) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("commentId", item.commentId());
            entry.put("studentFullName", item.studentFullName());
            entry.put("attitude", CommentAiDraftService.attitudeLabel(item.attitude()));
            entry.put("content", item.content());
            if (item.homeworkData() != null) {
                entry.put("homework", item.homeworkData());
            }
            comments.add(entry);
        }
        payload.put("comments", comments);
        JsonNode response = jsonCaller.callJson(REVIEW_PROMPT, payload, model, REVIEW_RUBRIC_SECTIONS, 0);
        if (response == null) {
            return null;
        }
        Set<Long> batchIds = batch.stream().map(ReviewItem::commentId).collect(Collectors.toSet());
        Map<Long, List<CommentAiReviewResult.Issue>> result = new HashMap<>();
        for (JsonNode review : response.path("reviews")) {
            long id = review.path("commentId").asLong(0);
            if (!batchIds.contains(id)) {
                continue;
            }
            for (JsonNode issue : review.path("issues")) {
                String type = issue.path("type").asText("").trim().toUpperCase(Locale.ROOT);
                String message = issue.path("message").asText("").trim();
                if (!message.isEmpty()) {
                    result.computeIfAbsent(id, k -> new ArrayList<>()).add(new CommentAiReviewResult.Issue(
                            AI_ISSUE_TYPES.contains(type) ? type : "OTHER", SOURCE_AI, message));
                }
            }
        }
        return result;
    }

    /** UC-75 bước 6 (chạy nền) — AI lỗi thì job FAILED (A4), nhận xét gốc giữ nguyên. */
    CommentAiSuggestionResult suggest(ReviewItem item, List<String> issues) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("studentFullName", item.studentFullName());
        payload.put("attitude", CommentAiDraftService.attitudeLabel(item.attitude()));
        payload.put("teacherPronoun", CommentAiDraftService.detectPronoun(item.content()));
        payload.put("content", item.content());
        payload.put("issues", issues.isEmpty() ? ruleIssues(item, List.of(item)).stream().map(CommentAiReviewResult.Issue::message).toList() : issues);
        JsonNode response = jsonCaller.callJson(SUGGEST_PROMPT, payload, model, EDIT_RUBRIC_SECTIONS, 0);
        String suggested = response == null ? "" : response.path("content").asText("").trim();
        if (suggested.isEmpty()) {
            throw new CommentAiDraftFailedException("Trợ lý chưa đề xuất được bản sửa (AI lỗi hoặc quá thời gian) — vui lòng thử lại.");
        }
        List<String> warnings = new ArrayList<>(recheck(item, suggested));
        if (normalize(suggested).equals(normalize(item.content() == null ? "" : item.content()))) {
            warnings.add("Bản sửa giống hệt bản gốc — có thể không cần sửa.");
        }
        return new CommentAiSuggestionResult(item.commentId(), item.content(), suggested,
                response.path("explanation").asText("").trim(), warnings);
    }

    /** UC-75 (chạy nền) — AI lỗi thì job FAILED, Quản lý vẫn tự nhập lý do như cũ. */
    CommentAiRejectionReasonResult rejectionReason(ReviewItem item, List<String> issues) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("studentFullName", item.studentFullName());
        payload.put("attitude", CommentAiDraftService.attitudeLabel(item.attitude()));
        payload.put("content", item.content());
        payload.put("issues", issues.isEmpty() ? ruleIssues(item, List.of(item)).stream().map(CommentAiReviewResult.Issue::message).toList() : issues);
        JsonNode response = jsonCaller.callJson(REJECTION_REASON_PROMPT, payload, model, REVIEW_RUBRIC_SECTIONS, 0);
        String reason = response == null ? "" : response.path("reason").asText("").trim();
        if (reason.isEmpty()) {
            throw new CommentAiDraftFailedException("Trợ lý chưa soạn được lý do từ chối (AI lỗi hoặc quá thời gian) — vui lòng thử lại hoặc tự nhập.");
        }
        return new CommentAiRejectionReasonResult(item.commentId(), reason);
    }

    /** UC-75 bước 9 (chạy nền) — chỉ trả bản sửa đề xuất; STT/AI lỗi thì job FAILED (A4). */
    CommentAiInstructionResult instruct(List<ReviewItem> items, byte[] audio, String mimeType, String note) {
        String transcript = "";
        if (audio != null) {
            String hint = "Học sinh: " + items.stream().map(ReviewItem::studentFullName).distinct().collect(Collectors.joining(", ")) + ".";
            transcript = aiClient.transcribe(audio, mimeType, null,
                    hint.length() > MAX_SPELLING_HINT_LENGTH ? hint.substring(0, MAX_SPELLING_HINT_LENGTH) : hint,
                    CommentAiDraftService.STT_LANGUAGE);
            if (transcript == null || transcript.isBlank()) {
                throw new CommentAiDraftFailedException(
                        "Không chuyển được audio thành văn bản (dịch vụ nhận dạng giọng nói lỗi hoặc audio không có tiếng nói) — vui lòng thử lại.");
            }
        }
        StringBuilder instruction = new StringBuilder();
        if (!transcript.isBlank()) {
            instruction.append(transcript.trim());
        }
        if (note != null && !note.isBlank()) {
            instruction.append(instruction.isEmpty() ? "" : "\n").append(note.trim());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instruction", instruction.toString());
        List<Map<String, Object>> comments = new ArrayList<>();
        for (ReviewItem item : items) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("commentId", item.commentId());
            entry.put("studentFullName", item.studentFullName());
            // Ngày + buổi học giúp phân biệt khi cùng 1 tên có nhiều nhận xét (trùng tên hoặc nhiều buổi) — xem rule 4 prompt.
            entry.put("commentDate", item.commentDate() == null ? null : item.commentDate().toString());
            entry.put("classSessionId", item.classSessionId());
            entry.put("attitude", CommentAiDraftService.attitudeLabel(item.attitude()));
            entry.put("content", item.content());
            comments.add(entry);
        }
        payload.put("comments", comments);
        JsonNode response = jsonCaller.callJson(INSTRUCTION_PROMPT, payload, model, EDIT_RUBRIC_SECTIONS, 0);
        if (response == null) {
            throw new CommentAiDraftFailedException("Trợ lý chưa xử lý được yêu cầu (AI lỗi hoặc quá thời gian) — vui lòng thử lại.");
        }
        Map<Long, ReviewItem> byId = items.stream().collect(Collectors.toMap(ReviewItem::commentId, i -> i));
        Map<Long, CommentAiInstructionResult.Change> changes = new LinkedHashMap<>();
        for (JsonNode node : response.path("changes")) {
            ReviewItem item = byId.get(node.path("commentId").asLong(0));
            String content = node.path("content").asText("").trim();
            if (item == null || content.isEmpty() || changes.containsKey(item.commentId())
                    || normalize(content).equals(normalize(item.content() == null ? "" : item.content()))) {
                continue;
            }
            List<String> warnings = recheck(item, content);
            changes.put(item.commentId(), new CommentAiInstructionResult.Change(item.commentId(), item.studentFullName(),
                    item.content(), content, warnings));
        }
        String message = response.path("assistantMessage").asText("").trim();
        if (message.isEmpty()) {
            message = changes.isEmpty() ? "Không có nhận xét nào cần sửa theo yêu cầu này." : "Đã đề xuất sửa " + changes.size() + " nhận xét.";
        }
        return new CommentAiInstructionResult(transcript, message, List.copyOf(changes.values()));
    }

    /**
     * UC-75 A5 (bổ sung 2026-09-29) — chạy lại đúng bộ kiểm tra tự động trên BẢN SỬA trước khi hiện cho Quản lý,
     * để bản sửa không vô tình phát sinh lỗi mới (chữ số, tên bạn khác, tên bài học, giống buổi trước...). Không tốn AI.
     */
    List<String> recheck(ReviewItem original, String revisedContent) {
        ReviewItem revised = new ReviewItem(original.commentId(), original.classSessionId(), original.studentFullName(),
                original.attitude(), revisedContent, original.commentDate(), original.lessonContent(),
                original.classmateNames(), original.previousComments());
        return ruleIssues(revised, List.of(revised)).stream()
                .map(issue -> "Bản sửa: " + issue.message())
                .toList();
    }

    // ---- Tiện ích ----

    private static CommentAiReviewResult.Issue rule(String type, String message) {
        return new CommentAiReviewResult.Issue(type, SOURCE_RULE, message);
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.forLanguageTag("vi")).replaceAll("\\s+", " ").trim();
    }

    private static CommentAiReviewJobResponse toReviewResponse(AiJobRegistry.Snapshot<CommentAiReviewResult> snapshot) {
        return new CommentAiReviewJobResponse(snapshot.jobId(), snapshot.status(), snapshot.errorMessage(), snapshot.result());
    }

    private static CommentAiInstructionJobResponse toInstructionResponse(AiJobRegistry.Snapshot<CommentAiInstructionResult> snapshot) {
        return new CommentAiInstructionJobResponse(snapshot.jobId(), snapshot.status(), snapshot.errorMessage(), snapshot.result());
    }

    private static CommentAiRejectionReasonJobResponse toRejectionReasonResponse(AiJobRegistry.Snapshot<CommentAiRejectionReasonResult> snapshot) {
        return new CommentAiRejectionReasonJobResponse(snapshot.jobId(), snapshot.status(), snapshot.errorMessage(), snapshot.result());
    }

    private static CommentAiSuggestionJobResponse toSuggestionResponse(AiJobRegistry.Snapshot<CommentAiSuggestionResult> snapshot) {
        return new CommentAiSuggestionJobResponse(snapshot.jobId(), snapshot.status(), snapshot.errorMessage(), snapshot.result());
    }
}
