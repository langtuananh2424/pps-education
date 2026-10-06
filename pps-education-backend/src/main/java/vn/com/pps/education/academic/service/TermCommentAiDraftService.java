package vn.com.pps.education.academic.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.academic.domain.ClassEnrollment;
import vn.com.pps.education.academic.domain.GradeComponentSetup;
import vn.com.pps.education.academic.domain.GradeEntry;
import vn.com.pps.education.academic.domain.GradeEvaluationComponent;
import vn.com.pps.education.academic.domain.GradeEvaluationResult;
import vn.com.pps.education.academic.dto.ApplyTermCommentAiDraftRequest;
import vn.com.pps.education.academic.dto.ReviseTermCommentAiDraftRequest;
import vn.com.pps.education.academic.dto.TermCommentAiDraftJobResponse;
import vn.com.pps.education.academic.dto.TermCommentAiDraftResult;
import vn.com.pps.education.academic.repository.ClassEnrollmentRepository;
import vn.com.pps.education.academic.repository.GradeComponentSetupRepository;
import vn.com.pps.education.academic.repository.GradeEntryRepository;
import vn.com.pps.education.academic.repository.GradeEvaluationComponentRepository;
import vn.com.pps.education.academic.repository.GradeEvaluationResultRepository;
import vn.com.pps.education.common.CommentSimilarity;
import vn.com.pps.education.exception.CommentAiDraftFailedException;
import vn.com.pps.education.exception.CommentAiDraftRejectedException;
import vn.com.pps.education.lms.service.AiJobRegistry;
import vn.com.pps.education.lms.service.CommentAiJsonCaller;
import vn.com.pps.education.lms.service.NineRouterAiClient;
import vn.com.pps.education.permission.service.PermissionEvaluationService;
import vn.com.pps.education.student.domain.Student;
import vn.com.pps.education.student.dto.GradeEvaluationResultResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.Collator;
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
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * UC-76: Trợ lý AI soạn nháp Nhận xét Giữa kỳ/Cuối kỳ (mở rộng FR-ACA-03 — bổ sung ngoài SDD gốc, đã xác nhận với người
 * dùng 2026-10-05). Xem docs/uc/phan-he-06-hoc-thuat.md để biết đầy đủ Main Flow/Alternate Flow.
 *
 * <p>Giống UC-74 ({@code CommentAiDraftService}): service này KHÔNG ghi DB khi soạn — đọc sổ điểm trong request thành 1
 * bản chụp bất biến ({@link DraftContext}), rồi giao phần gọi AI cho {@link AiJobRegistry} chạy nền. Lưu thật đi qua
 * {@link GradeService#applyAiDraftComments} do chính giáo viên bấm "Áp dụng"; trợ lý không có đường nào để Gửi duyệt.</p>
 *
 * <p>Khác UC-74: đầu vào duy nhất là điểm thành phần + Overall (phạm vi giai đoạn 1, đã xác nhận với người dùng), được
 * phân tích bằng code ({@link TermScoreInsight}) và quy ra lời TRƯỚC khi gửi AI — AI không nhận con số nào, nên không
 * tự tính sai hay chép điểm vào nhận xét. Pipeline: viết theo lô {@code writeBatchSize} học sinh (lô sau biết câu của
 * lô trước), thử lại 1 lần các học sinh bị bỏ sót (A4), đo trùng lặp bằng {@link CommentSimilarity} và viết lại 1 lần
 * dòng vượt ngưỡng (bước 6/A5).</p>
 */
@Service
public class TermCommentAiDraftService {

    private static final Logger log = LoggerFactory.getLogger(TermCommentAiDraftService.class);

    static final String WRITE_PROMPT = "term-comment-ai-write-system-prompt.txt";
    static final String REVISE_PROMPT = "term-comment-ai-revise-system-prompt.txt";
    static final String RUBRIC_FILE = "term-comment-ai-rubric.md";
    static final String STT_LANGUAGE = "vi";
    private static final int MAX_INSTRUCTION_LENGTH = 2000;
    private static final int MAX_HISTORY_TURNS = 6;
    private static final int MAX_AVOID_TEXTS = 30;
    private static final int MAX_PREVIOUS_COMMENTS = 2;
    private static final Pattern DIGIT = Pattern.compile("\\d");

    static final String SKIP_LOCKED = "Nhận xét kỳ đã Gửi duyệt/Đã duyệt";
    static final String SKIP_NO_SCORE = "Chưa có điểm để nhận xét";

    private final GradeService gradeService;
    private final GradeComponentSetupRepository gradeComponentSetupRepository;
    private final GradeEvaluationComponentRepository gradeEvaluationComponentRepository;
    private final GradeEntryRepository gradeEntryRepository;
    private final GradeEvaluationResultRepository gradeEvaluationResultRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final PermissionEvaluationService permissionEvaluationService;
    private final NineRouterAiClient aiClient;
    private final CommentAiJsonCaller jsonCaller;
    private final AiJobRegistry jobRegistry;
    private final Settings settings;

    /**
     * @param model               combo 9Router soạn nhận xét — dùng chung với UC-74 ({@code app.ai-comment-draft.model}).
     * @param similarityThreshold ngưỡng trùng lặp — dùng chung với UC-74 bước 7.
     * @param writeBatchSize      số học sinh mỗi lượt gọi AI — dùng chung với UC-74.
     * @param writeTemperature    nhiệt độ bước viết — dùng chung với UC-74.
     * @param maxAudioBytes       giới hạn dung lượng audio lời giáo viên — dùng chung với UC-74 (FE chặn thêm 5 phút/lần).
     */
    public record Settings(String model, double similarityThreshold, int writeBatchSize, double writeTemperature,
                           long maxAudioBytes, TermScoreInsight.Settings insight) {
    }

    public TermCommentAiDraftService(GradeService gradeService,
                                     GradeComponentSetupRepository gradeComponentSetupRepository,
                                     GradeEvaluationComponentRepository gradeEvaluationComponentRepository,
                                     GradeEntryRepository gradeEntryRepository,
                                     GradeEvaluationResultRepository gradeEvaluationResultRepository,
                                     ClassEnrollmentRepository classEnrollmentRepository,
                                     PermissionEvaluationService permissionEvaluationService,
                                     NineRouterAiClient aiClient,
                                     CommentAiJsonCaller jsonCaller,
                                     AiJobRegistry jobRegistry,
                                     @Value("${app.ai-comment-draft.model:comment-pps}") String model,
                                     @Value("${app.ai-comment-draft.similarity-threshold:0.5}") double similarityThreshold,
                                     @Value("${app.ai-comment-draft.write-batch-size:10}") int writeBatchSize,
                                     @Value("${app.ai-comment-draft.write-temperature:0.7}") double writeTemperature,
                                     @Value("${app.ai-comment-draft.max-audio-bytes:20971520}") long maxAudioBytes,
                                     @Value("${app.ai-term-comment.strong-percent:85}") double strongPercent,
                                     @Value("${app.ai-term-comment.weak-percent:50}") double weakPercent,
                                     @Value("${app.ai-term-comment.relative-gap-points:10}") double relativeGapPoints,
                                     @Value("${app.ai-term-comment.trend-points:10}") double trendPoints,
                                     @Value("${app.ai-term-comment.trend-band:0.5}") double trendBand) {
        this.gradeService = gradeService;
        this.gradeComponentSetupRepository = gradeComponentSetupRepository;
        this.gradeEvaluationComponentRepository = gradeEvaluationComponentRepository;
        this.gradeEntryRepository = gradeEntryRepository;
        this.gradeEvaluationResultRepository = gradeEvaluationResultRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.permissionEvaluationService = permissionEvaluationService;
        this.aiClient = aiClient;
        this.jsonCaller = jsonCaller;
        this.jobRegistry = jobRegistry;
        this.settings = new Settings(model, similarityThreshold, Math.max(1, writeBatchSize),
                Math.max(0, Math.min(1, writeTemperature)), maxAudioBytes,
                new TermScoreInsight.Settings(strongPercent, weakPercent, relativeGapPoints, trendPoints, trendBand));
    }

    // ---- Bản chụp sổ điểm (đọc trong request, dùng ở luồng nền) ----

    record Target(Long studentId, String fullName, TermScoreInsight.Insight insight, String existingComment,
                  List<String> previousComments) {

        String callName() {
            String[] parts = fullName.trim().split("\\s+");
            return parts[parts.length - 1];
        }
    }

    record DraftContext(GradeComponentSetup.EvaluationType evaluationType, List<Target> targets,
                        List<TermCommentAiDraftResult.SkippedStudent> skipped) {
    }

    // ---- API ----

    /** Audio lời giáo viên đã đọc trong request (MultipartFile không dùng được ở luồng nền). */
    record AudioInput(byte[] bytes, String mimeType) {
    }

    /**
     * UC-76 Main Flow bước 1-4: kiểm tra đầu vào + rào + chụp sổ điểm trong request (A1-A3, A9), chạy nền bước 5-7.
     * Lời giáo viên (audio và/hoặc chữ, bổ sung 2026-10-06) là tuỳ chọn — không có thì soạn thuần từ điểm.
     *
     * @param studentIds tuỳ chọn — chỉ soạn các học sinh này; null/rỗng = cả lớp.
     */
    @Transactional(readOnly = true)
    public TermCommentAiDraftJobResponse startDraft(Long classId, Long setupId, MultipartFile audio, String instruction,
                                                    List<Long> studentIds, Long actorUserId) {
        AudioInput audioInput = readAudio(audio);
        String text = normalizeInstruction(instruction);
        DraftContext context = loadContext(classId, setupId, studentIds, actorUserId);
        return toResponse(jobRegistry.submit(actorUserId, AiJobRegistry.Lane.DRAFT, () -> generateDraft(context, audioInput, text)));
    }

    /**
     * UC-76 Main Flow bước 7b (bổ sung 2026-10-06, đã xác nhận với người dùng) — giáo viên trò chuyện với trợ lý để sửa
     * bản nháp: sửa theo yêu cầu (chữ/audio, chỉ các dòng liên quan) hoặc viết lại câu chữ toàn bộ. Chỉ xét lại đúng các
     * học sinh đang có trong bản nháp, với dữ liệu điểm và rào MỚI NHẤT (bản ghi vừa Gửi duyệt sẽ bị bỏ ra).
     */
    @Transactional(readOnly = true)
    public TermCommentAiDraftJobResponse startRevise(Long classId, Long setupId, MultipartFile audio, String instruction,
                                                     ReviseTermCommentAiDraftRequest request, Long actorUserId) {
        AudioInput audioInput = readAudio(audio);
        String text = normalizeInstruction(instruction);
        if (request.mode() == ReviseTermCommentAiDraftRequest.Mode.INSTRUCTION && audioInput == null && text == null) {
            throw new CommentAiDraftRejectedException("Hãy nói hoặc nhập yêu cầu chỉnh sửa cho trợ lý.");
        }
        DraftContext context = loadContext(classId, setupId,
                request.currentRows().stream().map(ReviseTermCommentAiDraftRequest.CurrentRow::studentId).toList(), actorUserId);
        return toResponse(jobRegistry.submit(actorUserId, AiJobRegistry.Lane.DRAFT, () -> revise(context, request, audioInput, text)));
    }

    /** UC-76 A9 — audio quá dung lượng hoặc không phải audio bị từ chối ngay trong request (422). */
    private AudioInput readAudio(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            return null;
        }
        if (audio.getSize() > settings.maxAudioBytes()) {
            throw new CommentAiDraftRejectedException("File audio quá lớn (tối đa "
                    + (settings.maxAudioBytes() / (1024 * 1024)) + " MB) — mỗi lần gửi tối đa 5 phút.");
        }
        String mimeType = audio.getContentType();
        if (mimeType == null || !mimeType.startsWith("audio/")) {
            throw new CommentAiDraftRejectedException("File gửi lên không phải audio.");
        }
        try {
            return new AudioInput(audio.getBytes(), mimeType);
        } catch (IOException e) {
            throw new CommentAiDraftRejectedException("Không đọc được file audio — vui lòng gửi lại.");
        }
    }

    private static String normalizeInstruction(String instruction) {
        if (instruction == null || instruction.isBlank()) {
            return null;
        }
        if (instruction.length() > MAX_INSTRUCTION_LENGTH) {
            throw new CommentAiDraftRejectedException("Yêu cầu quá dài (tối đa " + MAX_INSTRUCTION_LENGTH + " ký tự).");
        }
        return instruction.trim();
    }

    public TermCommentAiDraftJobResponse getJob(String jobId, Long actorUserId) {
        return toResponse(jobRegistry.get(jobId, actorUserId, TermCommentAiDraftResult.class));
    }

    /** UC-76 Main Flow bước 8 — ghi thật do {@link GradeService} sở hữu sổ điểm đảm nhận (1 giao dịch). */
    public List<GradeEvaluationResultResponse> apply(Long classId, Long setupId, ApplyTermCommentAiDraftRequest request,
                                                     Long actorUserId) {
        return gradeService.applyAiDraftComments(classId, setupId, request, actorUserId);
    }

    private static TermCommentAiDraftJobResponse toResponse(AiJobRegistry.Snapshot<TermCommentAiDraftResult> snapshot) {
        return new TermCommentAiDraftJobResponse(snapshot.jobId(), snapshot.status(), snapshot.errorMessage(), snapshot.result());
    }

    // ---- Bước 2-4 ----

    DraftContext loadContext(Long classId, Long setupId, List<Long> onlyStudentIds, Long actorUserId) {
        gradeService.requireCanEnterGrades(classId, actorUserId);
        GradeComponentSetup setup = gradeService.getSetupOfClassOrThrow(classId, setupId);
        boolean canOverride = permissionEvaluationService.hasPermission(actorUserId, "academic.grade.edit.override");
        Set<Long> only = onlyStudentIds == null || onlyStudentIds.isEmpty() ? null : new HashSet<>(onlyStudentIds);

        Scores current = loadScores(classId, setup);
        Scores midTerm = Scores.EMPTY;
        if (setup.getEvaluationType() == GradeComponentSetup.EvaluationType.END_TERM) {
            midTerm = gradeComponentSetupRepository.findBySchoolClassIdAndAcademicTermIdAndEvaluationType(
                            classId, setup.getAcademicTerm().getId(), GradeComponentSetup.EvaluationType.MID_TERM)
                    .map(mid -> loadScores(classId, mid))
                    .orElse(Scores.EMPTY);
        }

        Collator collator = Collator.getInstance(Locale.forLanguageTag("vi"));
        List<Student> students = classEnrollmentRepository.findBySchoolClassIdAndStatus(classId, ClassEnrollment.Status.ACTIVE)
                .stream().map(ClassEnrollment::getStudent)
                .filter(s -> only == null || only.contains(s.getId()))
                .collect(Collectors.toMap(Student::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new))
                .values().stream()
                .sorted(Comparator.comparing((Student s) -> callName(s.getUser().getFullName()), collator)
                        .thenComparing(s -> s.getUser().getFullName(), collator))
                .toList();

        List<Target> targets = new ArrayList<>();
        List<TermCommentAiDraftResult.SkippedStudent> skipped = new ArrayList<>();
        for (Student student : students) {
            String fullName = student.getUser().getFullName();
            GradeEvaluationResult result = current.resultsByStudent().get(student.getId());
            if (!canOverride && result != null && (result.getStatus() == GradeEvaluationResult.Status.SUBMITTED
                    || result.getStatus() == GradeEvaluationResult.Status.OFFICIAL)) {
                skipped.add(new TermCommentAiDraftResult.SkippedStudent(student.getId(), fullName, SKIP_LOCKED));
                continue;
            }
            TermScoreInsight.Insight insight = TermScoreInsight.analyze(
                    current.componentScores(student.getId()), current.overall(student.getId()),
                    midTerm.componentScoresByKey(student.getId()), midTerm.overall(student.getId()), settings.insight());
            if (!insight.hasData()) {
                skipped.add(new TermCommentAiDraftResult.SkippedStudent(student.getId(), fullName, SKIP_NO_SCORE));
                continue;
            }
            targets.add(new Target(student.getId(), fullName, insight, result == null ? null : blankToNull(result.getComment()),
                    previousComments(student.getId(), result)));
        }
        if (targets.isEmpty()) {
            throw new CommentAiDraftRejectedException(skipped.isEmpty()
                    ? "Lớp chưa có học sinh nào đang học để soạn nhận xét."
                    : "Không còn học sinh nào cần soạn nhận xét (chưa có điểm hoặc đã Gửi duyệt hết).");
        }
        return new DraftContext(setup.getEvaluationType(), targets, skipped);
    }

    /** Nhận xét kỳ khác (mọi lớp) gần nhất của chính học sinh — để AI tránh lặp câu chữ (bước 5-6). */
    private List<String> previousComments(Long studentId, GradeEvaluationResult current) {
        return gradeEvaluationResultRepository.findByStudentIdWithContext(studentId).stream()
                .filter(r -> current == null || !r.getId().equals(current.getId()))
                .map(GradeEvaluationResult::getComment)
                .filter(c -> c != null && !c.isBlank())
                .limit(MAX_PREVIOUS_COMMENTS)
                .toList();
    }

    /** Điểm thành phần + Overall của 1 setup cho cả lớp, đã tách khỏi entity JPA. */
    private record Scores(List<GradeEvaluationComponent> components,
                          Map<Long, Map<Long, GradeEntry>> entriesByStudent,
                          Map<Long, GradeEvaluationResult> resultsByStudent,
                          GradeComponentSetup.ScaleType scale) {

        static final Scores EMPTY = new Scores(List.of(), Map.of(), Map.of(), null);

        List<TermScoreInsight.ComponentScore> componentScores(Long studentId) {
            Map<Long, GradeEntry> entries = entriesByStudent.getOrDefault(studentId, Map.of());
            List<TermScoreInsight.ComponentScore> scores = new ArrayList<>();
            for (GradeEvaluationComponent component : components) {
                GradeEntry entry = entries.get(component.getId());
                if (entry == null) {
                    continue;
                }
                scores.add(new TermScoreInsight.ComponentScore(componentKey(component), component.getName(), entry.getScore(),
                        component.getMaxScore(), component.getPassThreshold(), entry.isAbsenceFlag(),
                        component.getScaleType() == GradeEvaluationComponent.ScaleType.BAND
                                || scale == GradeComponentSetup.ScaleType.IELTS));
            }
            return scores;
        }

        Map<String, TermScoreInsight.ComponentScore> componentScoresByKey(Long studentId) {
            Map<String, TermScoreInsight.ComponentScore> map = new HashMap<>();
            componentScores(studentId).forEach(score -> map.putIfAbsent(score.key(), score));
            return map;
        }

        TermScoreInsight.OverallScore overall(Long studentId) {
            GradeEvaluationResult result = resultsByStudent.get(studentId);
            if (result == null || result.getOverallScore() == null || scale == null) {
                return null;
            }
            return switch (scale) {
                case POINT_10 -> new TermScoreInsight.OverallScore(result.getOverallScore(), BigDecimal.TEN, false);
                case PERCENT -> new TermScoreInsight.OverallScore(result.getOverallScore(), BigDecimal.valueOf(100), false);
                case IELTS -> new TermScoreInsight.OverallScore(result.getOverallScore(), BigDecimal.valueOf(9), true);
            };
        }
    }

    private Scores loadScores(Long classId, GradeComponentSetup setup) {
        List<GradeEvaluationComponent> components = gradeEvaluationComponentRepository.findByGradeComponentSetupIdOrderByDisplayOrder(setup.getId());
        Map<Long, Map<Long, GradeEntry>> entries = new HashMap<>();
        for (GradeEvaluationComponent component : components) {
            for (GradeEntry entry : gradeEntryRepository.findBySchoolClassIdAndGradeComponentIdOrderByStudentId(classId, component.getId())) {
                entries.computeIfAbsent(entry.getStudent().getId(), k -> new HashMap<>()).put(component.getId(), entry);
            }
        }
        Map<Long, GradeEvaluationResult> results = gradeEvaluationResultRepository
                .findBySchoolClassIdAndAcademicTermIdAndEvaluationTypeOrderByStudentId(classId, setup.getAcademicTerm().getId(),
                        setup.getEvaluationType())
                .stream().collect(Collectors.toMap(r -> r.getStudent().getId(), Function.identity(), (a, b) -> a));
        return new Scores(components, entries, results, setup.getScaleType());
    }

    /** Ghép Cuối kỳ với Giữa kỳ: theo mã kỹ năng; mã OTHER thì theo kỹ năng danh mục (UC-54), không có thì theo tên. */
    static String componentKey(GradeEvaluationComponent component) {
        if (component.getCode() != null && component.getCode() != GradeEvaluationComponent.ComponentCode.OTHER) {
            return component.getCode().name();
        }
        if (component.getSkill() != null) {
            return "SKILL:" + component.getSkill().getId();
        }
        return "NAME:" + (component.getName() == null ? "" : component.getName().trim().toLowerCase(Locale.ROOT));
    }

    // ---- Bước 5-7 (luồng nền) ----

    TermCommentAiDraftResult generateDraft(DraftContext context) {
        return generateDraft(context, null, null);
    }

    /** Bước 5-7: (chuyển audio thành chữ) → viết theo lô → đo trùng lặp, viết lại 1 lần (A4/A5). */
    TermCommentAiDraftResult generateDraft(DraftContext context, AudioInput audio, String note) {
        String transcript = transcribe(context, audio);
        String teacherText = teacherText(transcript, note);
        Map<Long, String> contents = writeAndDeduplicate(context, context.targets(), new ArrayList<>(), teacherText);
        List<TermCommentAiDraftResult.Row> rows = toRows(context.targets(), contents);
        StringBuilder message = new StringBuilder("Đã soạn nhận xét ")
                .append(evaluationLabel(context)).append(" cho ").append(contents.size()).append(" học sinh");
        message.append(teacherText == null ? " từ điểm thành phần và Overall." : " từ điểm và lời giáo viên.");
        appendReviewHints(message, rows, context.skipped().size());
        return new TermCommentAiDraftResult(context.evaluationType().name(), transcript, message.toString(), rows, context.skipped());
    }

    /** Bước 7b — sửa theo yêu cầu (chỉ các dòng liên quan) hoặc viết lại câu chữ toàn bộ. */
    TermCommentAiDraftResult revise(DraftContext context, ReviseTermCommentAiDraftRequest request, AudioInput audio, String note) {
        Map<Long, String> current = new LinkedHashMap<>();
        Set<Long> targetIds = context.targets().stream().map(Target::studentId).collect(Collectors.toSet());
        for (ReviseTermCommentAiDraftRequest.CurrentRow row : request.currentRows()) {
            if (targetIds.contains(row.studentId())) {
                current.putIfAbsent(row.studentId(), row.content());
            }
        }
        List<Target> targets = context.targets().stream().filter(t -> current.containsKey(t.studentId())).toList();
        if (request.mode() == ReviseTermCommentAiDraftRequest.Mode.REWRITE_ALL) {
            List<String> oldTexts = new ArrayList<>(current.values().stream().filter(c -> c != null && !c.isBlank()).toList());
            Map<Long, String> contents = writeAndDeduplicate(context, targets, oldTexts, null);
            List<TermCommentAiDraftResult.Row> rows = toRows(targets, contents);
            StringBuilder message = new StringBuilder("Đã viết lại câu chữ cho ").append(contents.size()).append(" học sinh.");
            appendReviewHints(message, rows, 0);
            return new TermCommentAiDraftResult(context.evaluationType().name(), null, message.toString(), rows, context.skipped());
        }

        String transcript = transcribe(context, audio);
        String instruction = teacherText(transcript, note);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evaluation", evaluationLabel(context));
        payload.put("instruction", instruction);
        List<ReviseTermCommentAiDraftRequest.ChatTurn> history = request.history() == null ? List.of() : request.history();
        payload.put("history", history.subList(Math.max(0, history.size() - MAX_HISTORY_TURNS), history.size()));
        List<Map<String, Object>> students = new ArrayList<>();
        for (Target target : targets) {
            Map<String, Object> item = studentPayload(target);
            item.put("content", current.get(target.studentId()));
            students.add(item);
        }
        payload.put("students", students);
        JsonNode response = jsonCaller.callJson(REVISE_PROMPT, RUBRIC_FILE, payload, settings.model(),
                CommentAiJsonCaller.ALL_RUBRIC_SECTIONS, 0);
        if (response == null) {
            throw new CommentAiDraftFailedException("Trợ lý chưa xử lý được yêu cầu (AI lỗi hoặc quá thời gian) — vui lòng thử lại.");
        }
        Map<Long, String> contents = new LinkedHashMap<>(current);
        int changed = 0;
        for (JsonNode change : response.path("changes")) {
            long id = change.path("studentId").asLong(0);
            String content = change.path("content").asText("").trim();
            if (contents.containsKey(id) && !content.isEmpty()) {
                contents.put(id, content);
                changed++;
            }
        }
        String message = response.path("assistantMessage").asText("").isBlank()
                ? (changed > 0 ? "Đã cập nhật " + changed + " học sinh theo yêu cầu." : "Không có dòng nào cần sửa theo yêu cầu này.")
                : response.path("assistantMessage").asText().trim();
        contents.values().removeIf(c -> c == null || c.isBlank());
        return new TermCommentAiDraftResult(context.evaluationType().name(), transcript, message, toRows(targets, contents),
                context.skipped());
    }

    /** Bước 5-6 dùng chung cho soạn mới và "Viết lại": viết theo lô, đo trùng lặp, viết lại 1 lần dòng vượt ngưỡng. */
    private Map<Long, String> writeAndDeduplicate(DraftContext context, List<Target> targets, List<String> avoid, String teacherText) {
        Map<Long, String> contents = writeAll(context, targets, avoid, teacherText);
        if (contents.isEmpty()) {
            throw new CommentAiDraftFailedException("Trợ lý chưa viết được nhận xét (AI lỗi hoặc quá thời gian) — vui lòng thử lại.");
        }
        Map<Long, List<String>> similar = similarTexts(targets, contents);
        if (!similar.isEmpty()) {
            List<Target> retry = targets.stream().filter(t -> similar.containsKey(t.studentId())).toList();
            List<String> retryAvoid = new ArrayList<>();
            similar.values().forEach(retryAvoid::addAll);
            contents.forEach((id, text) -> {
                if (!similar.containsKey(id)) {
                    retryAvoid.add(text);
                }
            });
            for (int from = 0; from < retry.size(); from += settings.writeBatchSize()) {
                List<Target> batch = retry.subList(from, Math.min(retry.size(), from + settings.writeBatchSize()));
                writeBatch(context, batch, retryAvoid, teacherText).forEach((id, text) -> {
                    contents.put(id, text);
                    retryAvoid.add(text);
                });
            }
        }
        return contents;
    }

    /** STT lời giáo viên (audio không được lưu lại sau bước này, như UC-74). */
    private String transcribe(DraftContext context, AudioInput audio) {
        if (audio == null) {
            return null;
        }
        String hint = context.targets().stream().map(Target::fullName).collect(Collectors.joining(", "));
        String transcript = aiClient.transcribe(audio.bytes(), audio.mimeType(), null,
                hint.length() > 800 ? hint.substring(0, 800) : hint, STT_LANGUAGE);
        if (transcript == null || transcript.isBlank()) {
            throw new CommentAiDraftFailedException(
                    "Không chuyển được audio thành văn bản (dịch vụ nhận dạng giọng nói lỗi hoặc audio không có tiếng nói) — vui lòng thử lại.");
        }
        return transcript.trim();
    }

    private static String teacherText(String transcript, String note) {
        if (transcript == null) {
            return note;
        }
        return note == null ? transcript : transcript + "\n" + note;
    }

    private static String evaluationLabel(DraftContext context) {
        return context.evaluationType() == GradeComponentSetup.EvaluationType.MID_TERM ? "Giữa kỳ" : "Cuối kỳ";
    }

    private static void appendReviewHints(StringBuilder message, List<TermCommentAiDraftResult.Row> rows, int skippedCount) {
        long warned = rows.stream().filter(r -> !r.warnings().isEmpty()).count();
        if (warned > 0) {
            message.append(" Có ").append(warned).append(" dòng cần xem lại (xem cảnh báo dưới từng dòng).");
        }
        if (skippedCount > 0) {
            message.append(" ").append(skippedCount).append(" học sinh không soạn.");
        }
    }

    /** Viết theo lô; lô lỗi hoặc AI bỏ sót học sinh thì thử lại 1 lần với lô bằng nửa (A4). */
    private Map<Long, String> writeAll(DraftContext context, List<Target> targets, List<String> avoid, String teacherText) {
        Map<Long, String> contents = new LinkedHashMap<>();
        for (int from = 0; from < targets.size(); from += settings.writeBatchSize()) {
            List<Target> batch = targets.subList(from, Math.min(targets.size(), from + settings.writeBatchSize()));
            Map<Long, String> written = writeBatch(context, batch, avoid, teacherText);
            contents.putAll(written);
            avoid.addAll(written.values());
        }
        List<Target> missing = targets.stream().filter(t -> !contents.containsKey(t.studentId())).toList();
        if (!missing.isEmpty()) {
            log.warn("TermCommentAiDraftService: {} học sinh chưa được viết sau lượt đầu — thử lại 1 lần.", missing.size());
            int retryBatchSize = Math.max(1, (settings.writeBatchSize() + 1) / 2);
            for (int from = 0; from < missing.size(); from += retryBatchSize) {
                List<Target> batch = missing.subList(from, Math.min(missing.size(), from + retryBatchSize));
                Map<Long, String> written = writeBatch(context, batch, avoid, teacherText);
                contents.putAll(written);
                avoid.addAll(written.values());
            }
        }
        return contents;
    }

    /** Bước 6: dòng trùng với học sinh đứng TRƯỚC trong lớp hoặc với nhận xét kỳ trước của chính mình. */
    private Map<Long, List<String>> similarTexts(List<Target> targets, Map<Long, String> contents) {
        Map<Long, List<String>> result = new LinkedHashMap<>();
        for (int i = 0; i < targets.size(); i++) {
            String text = contents.get(targets.get(i).studentId());
            if (text == null) {
                continue;
            }
            Set<String> similar = new LinkedHashSet<>();
            for (int j = 0; j < i; j++) {
                String other = contents.get(targets.get(j).studentId());
                if (other != null && CommentSimilarity.similarity(text, other) >= settings.similarityThreshold()) {
                    similar.add(other);
                }
            }
            for (String previous : targets.get(i).previousComments()) {
                if (CommentSimilarity.similarity(text, previous) >= settings.similarityThreshold()) {
                    similar.add(previous);
                }
            }
            if (!similar.isEmpty()) {
                similar.add(text);
                result.put(targets.get(i).studentId(), new ArrayList<>(similar));
            }
        }
        return result;
    }

    private Map<Long, String> writeBatch(DraftContext context, List<Target> batch, List<String> avoid, String teacherText) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evaluation", evaluationLabel(context));
        if (teacherText != null) {
            payload.put("teacherInstruction", teacherText);
        }
        List<Map<String, Object>> students = new ArrayList<>();
        for (Target target : batch) {
            students.add(studentPayload(target));
        }
        payload.put("students", students);
        payload.put("avoidTexts", avoid.subList(Math.max(0, avoid.size() - MAX_AVOID_TEXTS), avoid.size()));
        JsonNode response = jsonCaller.callJson(WRITE_PROMPT, RUBRIC_FILE, payload, settings.model(),
                CommentAiJsonCaller.ALL_RUBRIC_SECTIONS, settings.writeTemperature());
        return parseComments(response, batch);
    }

    /** Dữ liệu 1 học sinh gửi AI — chỉ phân tích điểm đã quy ra lời, không con số. */
    private static Map<String, Object> studentPayload(Target target) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("studentId", target.studentId());
        item.put("fullName", target.fullName());
        item.put("callName", target.callName());
        item.put("skills", target.insight().skills().stream()
                .map(s -> Map.of("name", s.name(), "assessment", s.assessment())).toList());
        if (target.insight().overall() != null) {
            item.put("overall", target.insight().overall());
        }
        if (!target.insight().trend().isEmpty()) {
            item.put("trend", target.insight().trend());
        }
        if (!target.insight().absentParts().isEmpty()) {
            item.put("absentParts", target.insight().absentParts());
        }
        if (!target.previousComments().isEmpty()) {
            item.put("previousComments", target.previousComments());
        }
        return item;
    }

    private static Map<Long, String> parseComments(JsonNode response, List<Target> batch) {
        Map<Long, String> written = new LinkedHashMap<>();
        if (response == null) {
            log.warn("TermCommentAiDraftService: lô viết {} học sinh thất bại (AI lỗi/quá thời gian/kết quả dở dang).", batch.size());
            return written;
        }
        Set<Long> batchIds = batch.stream().map(Target::studentId).collect(Collectors.toSet());
        for (JsonNode node : response.path("comments")) {
            long id = node.path("studentId").asLong(0);
            String content = node.path("content").asText("").trim();
            if (batchIds.contains(id) && !content.isEmpty()) {
                written.putIfAbsent(id, content);
            }
        }
        return written;
    }

    /** Bước 7: dòng xem trước + cảnh báo cuối cùng (A4 NOT_WRITTEN, A5 trùng lặp, A6 có chữ số). */
    private List<TermCommentAiDraftResult.Row> toRows(List<Target> targets, Map<Long, String> contents) {
        List<TermCommentAiDraftResult.Row> rows = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            Target target = targets.get(i);
            String content = contents.get(target.studentId());
            List<TermCommentAiDraftResult.Warning> warnings = new ArrayList<>();
            if (content == null) {
                warnings.add(new TermCommentAiDraftResult.Warning("NOT_WRITTEN",
                        "Trợ lý chưa viết được nhận xét cho học sinh này — bấm \"Viết lại\" hoặc tự viết.", null));
            } else {
                double maxInClass = 0;
                for (int j = 0; j < i; j++) {
                    String other = contents.get(targets.get(j).studentId());
                    if (other != null) {
                        maxInClass = Math.max(maxInClass, CommentSimilarity.similarity(content, other));
                    }
                }
                if (maxInClass >= settings.similarityThreshold()) {
                    warnings.add(new TermCommentAiDraftResult.Warning("SIMILAR_IN_CLASS",
                            "Câu chữ còn giống nhận xét của học sinh khác trong lớp.", maxInClass));
                }
                double maxPrevious = target.previousComments().stream()
                        .mapToDouble(p -> CommentSimilarity.similarity(content, p)).max().orElse(0);
                if (maxPrevious >= settings.similarityThreshold()) {
                    warnings.add(new TermCommentAiDraftResult.Warning("SIMILAR_TO_PREVIOUS",
                            "Câu chữ còn giống nhận xét kỳ trước của chính học sinh này.", maxPrevious));
                }
                if (DIGIT.matcher(content).find()) {
                    warnings.add(new TermCommentAiDraftResult.Warning("HAS_DIGITS",
                            "Nhận xét có chữ số — kiểm tra lại, nhận xét kỳ không ghi điểm.", null));
                }
            }
            rows.add(new TermCommentAiDraftResult.Row(target.studentId(), target.fullName(), target.insight().summary(),
                    target.existingComment(), content, warnings));
        }
        return rows;
    }

    private static String callName(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        return parts[parts.length - 1];
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
