package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.common.CommentSimilarity;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.StudentComment;
import vn.com.pps.education.dto.CommentAiReviewJobResponse;
import vn.com.pps.education.dto.CommentAiReviewRequest;
import vn.com.pps.education.dto.CommentAiReviewResult;
import vn.com.pps.education.dto.CommentAiSuggestionJobResponse;
import vn.com.pps.education.dto.CommentAiSuggestionRequest;
import vn.com.pps.education.dto.CommentAiSuggestionResult;
import vn.com.pps.education.exception.CommentAiDraftFailedException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.StudentCommentRepository;

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
    private final String model;
    private final int previousCommentCount;
    private final int previousLookbackDays;
    private final double similarityThreshold;

    public CommentAiReviewService(StudentCommentService studentCommentService,
                                  ClassEnrollmentRepository classEnrollmentRepository,
                                  StudentCommentRepository studentCommentRepository,
                                  CommentAiJsonCaller jsonCaller,
                                  AiJobRegistry jobRegistry,
                                  @Value("${app.ai-comment-draft.model:comment-pps}") String model,
                                  @Value("${app.ai-comment-draft.previous-comment-count:3}") int previousCommentCount,
                                  @Value("${app.ai-comment-draft.previous-lookback-days:120}") int previousLookbackDays,
                                  @Value("${app.ai-comment-draft.similarity-threshold:0.5}") double similarityThreshold) {
        this.studentCommentService = studentCommentService;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.studentCommentRepository = studentCommentRepository;
        this.jsonCaller = jsonCaller;
        this.jobRegistry = jobRegistry;
        this.model = model;
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
        payload.put("lessonContent", batch.get(0).lessonContent());
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
        payload.put("lessonContent", item.lessonContent());
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
        List<String> warnings = new ArrayList<>();
        if (DIGIT.matcher(suggested).find()) {
            warnings.add("Bản sửa có chữ số — kiểm tra lại trước khi áp dụng.");
        }
        if (normalize(suggested).equals(normalize(item.content() == null ? "" : item.content()))) {
            warnings.add("Bản sửa giống hệt bản gốc — có thể không cần sửa.");
        }
        return new CommentAiSuggestionResult(item.commentId(), item.content(), suggested,
                response.path("explanation").asText("").trim(), warnings);
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

    private static CommentAiSuggestionJobResponse toSuggestionResponse(AiJobRegistry.Snapshot<CommentAiSuggestionResult> snapshot) {
        return new CommentAiSuggestionJobResponse(snapshot.jobId(), snapshot.status(), snapshot.errorMessage(), snapshot.result());
    }
}
