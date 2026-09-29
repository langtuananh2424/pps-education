package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.common.CommentSimilarity;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.StudentComment;
import vn.com.pps.education.dto.CommentAiInstructionJobResponse;
import vn.com.pps.education.dto.CommentAiInstructionResult;
import vn.com.pps.education.dto.CommentAiReviewJobResponse;
import vn.com.pps.education.dto.CommentAiReviewRequest;
import vn.com.pps.education.dto.CommentAiReviewResult;
import vn.com.pps.education.dto.CommentAiSuggestionJobResponse;
import vn.com.pps.education.dto.CommentAiSuggestionRequest;
import vn.com.pps.education.dto.CommentAiSuggestionResult;
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
import java.util.regex.Pattern;
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
    private static final int MAX_SPELLING_HINT_LENGTH = 800;

    static final String SOURCE_RULE = "RULE";
    static final String SOURCE_AI = "AI";
    private static final Set<String> AI_ISSUE_TYPES = Set.of(
            "OTHER_STUDENT", "HOMEWORK_OR_SCORE", "HARSH_WORDING", "ATTITUDE_MISMATCH", "FORBIDDEN_TOPIC", "OTHER");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    /** Rubric nhận xét đặt mức khoảng 400 ký tự — chỉ cảnh báo khi vượt rõ rệt. */
    static final int MAX_CONTENT_LENGTH = 500;
    private static final int REVIEW_BATCH_SIZE = 15;

    private final StudentCommentService studentCommentService;
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

    public CommentAiReviewService(StudentCommentService studentCommentService,
                                  ClassEnrollmentRepository classEnrollmentRepository,
                                  StudentCommentRepository studentCommentRepository,
                                  CommentAiJsonCaller jsonCaller,
                                  AiJobRegistry jobRegistry,
                                  NineRouterAiClient aiClient,
                                  @Value("${app.ai-comment-draft.model:comment-pps}") String model,
                                  @Value("${app.ai-comment-draft.previous-comment-count:3}") int previousCommentCount,
                                  @Value("${app.ai-comment-draft.previous-lookback-days:120}") int previousLookbackDays,
                                  @Value("${app.ai-comment-draft.similarity-threshold:0.5}") double similarityThreshold,
                                  @Value("${app.ai-comment-draft.max-audio-bytes:20971520}") long maxAudioBytes) {
        this.studentCommentService = studentCommentService;
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
    }

    /** Bản chụp 1 nhận xét chờ duyệt (đọc trong request, dùng ở luồng nền — không chạm entity JPA). */
    record ReviewItem(Long commentId, Long classSessionId, String studentFullName, String attitude, String content,
                      LocalDate commentDate, String lessonContent, List<String> classmateNames,
                      List<CommentAiDraftService.PreviousComment> previousComments) {
    }

    // ---- Điểm vào từ Controller ----

    /** UC-75 Main Flow bước 1-2: kiểm tra rào (A1, A2) trong request, chạy nền bước 3-5. */
    @Transactional(readOnly = true)
    public CommentAiReviewJobResponse startReview(CommentAiReviewRequest request, Long actorUserId) {
        List<ReviewItem> items = loadItems(request.commentIds(), actorUserId);
        return toReviewResponse(jobRegistry.submit(actorUserId, () -> review(items)));
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
        return toSuggestionResponse(jobRegistry.submit(actorUserId, () -> suggest(item, issues)));
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
        return toInstructionResponse(jobRegistry.submit(actorUserId, () -> instruct(items, finalAudio, finalMimeType, finalNote)));
    }

    public CommentAiInstructionJobResponse getInstruction(String jobId, Long actorUserId) {
        return toInstructionResponse(jobRegistry.get(jobId, actorUserId, CommentAiInstructionResult.class));
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
                    classmates, previous));
        }
        items.sort(Comparator.comparing(ReviewItem::commentId));
        return items;
    }

    // ---- Luồng nền ----

    /** UC-75 Main Flow bước 3-5. */
    CommentAiReviewResult review(List<ReviewItem> items) {
        Map<Long, List<CommentAiReviewResult.Issue>> issuesById = new LinkedHashMap<>();
        items.forEach(item -> issuesById.put(item.commentId(), new ArrayList<>(ruleIssues(item, items))));

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
                    list.stream()
                            .filter(i -> !(hasRuleOtherStudent && "OTHER_STUDENT".equals(i.type())))
                            .forEach(existing::add);
                });
            }
        }

        List<CommentAiReviewResult.Review> reviews = items.stream()
                .map(i -> new CommentAiReviewResult.Review(i.commentId(), i.studentFullName(), List.copyOf(issuesById.get(i.commentId()))))
                .toList();
        int flagged = (int) reviews.stream().filter(r -> !r.issues().isEmpty()).count();
        StringBuilder message = new StringBuilder("Đã soát ").append(items.size()).append(" nhận xét: ");
        message.append(flagged == 0 ? "không phát hiện lỗi nào." : flagged + " dòng có cảnh báo, " + (items.size() - flagged) + " dòng ổn.");
        if (!aiComplete) {
            message.append(" Phần kiểm tra theo rubric bằng AI bị lỗi ở một số dòng — các dòng đó chỉ có kết quả kiểm tra tự động.");
        }
        return new CommentAiReviewResult(message.toString(), items.size(), flagged, aiComplete, reviews);
    }

    /** UC-75 bước 3 — kiểm tra bằng code, không gọi AI. */
    List<CommentAiReviewResult.Issue> ruleIssues(ReviewItem item, List<ReviewItem> all) {
        List<CommentAiReviewResult.Issue> issues = new ArrayList<>();
        String content = item.content() == null ? "" : item.content().trim();
        if (content.isEmpty()) {
            issues.add(rule("EMPTY", "Nhận xét đang để trống."));
            return issues;
        }
        if (DIGIT.matcher(content).find()) {
            issues.add(rule("CONTAINS_DIGITS", "Nhận xét có chữ số — kiểm tra có nhắc điểm/số liệu không."));
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            issues.add(rule("TOO_LONG", "Nhận xét dài " + content.length() + " ký tự (rubric khoảng 400)."));
        }
        if (CommentAiDraftService.mentionsLessonTitle(content, item.lessonContent())) {
            issues.add(rule("LESSON_TITLE", "Nhắc tên bài học \"" + item.lessonContent().trim() + "\" — thường không ghi tên bài vào nhận xét."));
        }
        String normalizedContent = normalize(content);
        for (String classmate : item.classmateNames()) {
            if (classmate != null && !classmate.isBlank() && normalizedContent.contains(normalize(classmate))) {
                issues.add(rule("OTHER_STUDENT_NAME", "Nhắc họ tên bạn khác trong lớp: \"" + classmate + "\"."));
            }
        }
        double bestSession = 0;
        String bestName = null;
        for (ReviewItem other : all) {
            if (other == item || other.classSessionId() == null || !other.classSessionId().equals(item.classSessionId())) {
                continue;
            }
            double similarity = CommentSimilarity.similarity(content, other.content());
            if (similarity > bestSession) {
                bestSession = similarity;
                bestName = other.studentFullName();
            }
        }
        if (bestSession >= similarityThreshold) {
            issues.add(rule("SIMILAR_IN_SESSION", "Giống nhận xét của " + bestName + " " + Math.round(bestSession * 100) + "%."));
        }
        double bestPrevious = 0;
        LocalDate bestDate = null;
        for (CommentAiDraftService.PreviousComment previous : item.previousComments()) {
            double similarity = CommentSimilarity.similarity(content, previous.content());
            if (similarity > bestPrevious) {
                bestPrevious = similarity;
                bestDate = previous.date();
            }
        }
        if (bestPrevious >= similarityThreshold) {
            issues.add(rule("SIMILAR_TO_PREVIOUS", "Giống nhận xét buổi " + bestDate + " " + Math.round(bestPrevious * 100) + "%."));
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
            comments.add(entry);
        }
        payload.put("comments", comments);
        JsonNode response = jsonCaller.callJson(REVIEW_PROMPT, payload, model);
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
        JsonNode response = jsonCaller.callJson(SUGGEST_PROMPT, payload, model);
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
            entry.put("attitude", CommentAiDraftService.attitudeLabel(item.attitude()));
            entry.put("content", item.content());
            comments.add(entry);
        }
        payload.put("comments", comments);
        JsonNode response = jsonCaller.callJson(INSTRUCTION_PROMPT, payload, model);
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

    private static CommentAiSuggestionJobResponse toSuggestionResponse(AiJobRegistry.Snapshot<CommentAiSuggestionResult> snapshot) {
        return new CommentAiSuggestionJobResponse(snapshot.jobId(), snapshot.status(), snapshot.errorMessage(), snapshot.result());
    }
}
