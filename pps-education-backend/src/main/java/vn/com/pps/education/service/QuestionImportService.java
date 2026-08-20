package vn.com.pps.education.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.domain.ImportJob;
import vn.com.pps.education.domain.QuestionBank;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateQuestionRequest;
import vn.com.pps.education.dto.QuestionChoiceRequest;
import vn.com.pps.education.dto.QuestionImportResponse;
import vn.com.pps.education.dto.QuestionResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ExamRepository;
import vn.com.pps.education.repository.ImportJobRepository;
import vn.com.pps.education.repository.QuestionBankRepository;
import vn.com.pps.education.repository.UserRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * UC-40: Soạn & giao đề kiểm tra (FR-LMS-10) — soạn đề nhanh qua file mẫu
 * Excel/Word (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-07-30, xem docs/uc/phan-he-07-lms-portal.md). Tách khỏi
 * QuestionBankService theo SRP: Service này chỉ lo cơ chế import (đọc
 * file, validate theo loại, gộp lỗi từng dòng), việc GHI 1 Question vào DB
 * vẫn ủy quyền hoàn toàn cho {@link QuestionBankService#createQuestion}
 * (điểm ghi DUY NHẤT, dùng chung với form soạn tay — không lặp lại logic
 * tạo Question ở đây, xem .claude/rules/solid.md mục D).
 *
 * Phạm vi loại câu hỏi: CHỈ 5 loại UI mà QuestionEditorForm.tsx (FE) đã hỗ
 * trợ (TRAC_NGHIEM/TRAC_NGHIEM_VOICE/DIEN_TU/TU_LUAN/SPEAKING) — không mở
 * rộng sang TRUE_FALSE/MULTIPLE_ANSWER (dù Question.QuestionType có 6 giá
 * trị) để câu hỏi tạo qua import luôn sửa lại được bằng form tay sẵn có.
 *
 * Kiến trúc parser theo Open/Closed (xem QuestionRowParser) — Spring tự
 * inject mọi bean implement interface này, chọn theo phần mở rộng file.
 */
@Service
public class QuestionImportService {

    private static final Set<String> VALID_KINDS = Set.of(
            "TRAC_NGHIEM", "TRAC_NGHIEM_VOICE", "DIEN_TU", "TU_LUAN", "SPEAKING");
    private static final Set<String> VALID_DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");

    private final ImportJobRepository importJobRepository;
    private final QuestionBankRepository questionBankRepository;
    private final ExamRepository examRepository;
    private final QuestionBankService questionBankService;
    private final UserRepository userRepository;
    private final List<QuestionRowParser> parsers;

    public QuestionImportService(ImportJobRepository importJobRepository,
                                  QuestionBankRepository questionBankRepository,
                                  ExamRepository examRepository,
                                  QuestionBankService questionBankService,
                                  UserRepository userRepository,
                                  List<QuestionRowParser> parsers) {
        this.importJobRepository = importJobRepository;
        this.questionBankRepository = questionBankRepository;
        this.examRepository = examRepository;
        this.questionBankService = questionBankService;
        this.userRepository = userRepository;
        this.parsers = parsers;
    }

    /**
     * Main Flow bước 1 (nhánh "soạn câu hỏi mới" hàng loạt). A2: lỗi từng
     * dòng ghi vào error_summary, không chặn dòng khác. A3: file đọc hỏng
     * hoàn toàn → import_job FAILED ngay, không tạo câu hỏi nào.
     */
    @Transactional
    public QuestionImportResponse importQuestions(Long bankId, MultipartFile file, Long actorUserId) {
        QuestionBank bank = questionBankRepository.findById(bankId)
                .orElseThrow(() -> new ResourceNotFoundException("error.questionImport.bankNotFound",
                        new Object[]{bankId}, "Không tìm thấy ngân hàng câu hỏi id=" + bankId));
        if (examRepository.existsByQuestionBankId(bankId)) {
            throw new ResourceNotFoundException("error.questionImport.bankNotFound",
                    new Object[]{bankId}, "Không tìm thấy ngân hàng câu hỏi id=" + bankId);
        }
        return importQuestionsIntoBank(bank, file, actorUserId, true);
    }

    /** Primitive dùng chung: Exam internal bank cho phép trùng; generic legacy bank chặn trùng. */
    @Transactional
    QuestionImportResponse importQuestionsIntoBank(QuestionBank bank, MultipartFile file,
                                                   Long actorUserId, boolean rejectActiveDuplicate) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.questionImport.userNotFound",
                        new Object[]{actorUserId}, "Không tìm thấy tài khoản id=" + actorUserId));
        QuestionRowParser parser = parsers.stream()
                .filter(p -> p.supports(file.getOriginalFilename()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Chỉ hỗ trợ file .xlsx hoặc .docx."));

        ImportJob job = createJob(file, actor);
        try {
            List<QuestionRowParser.ParsedQuestionRow> parsedRows;
            try (InputStream inputStream = file.getInputStream()) {
                parsedRows = parser.parse(inputStream);
            }

            List<Map<String, Object>> errors = new ArrayList<>();
            List<Map<String, Object>> createdQuestions = new ArrayList<>();
            for (QuestionRowParser.ParsedQuestionRow row : parsedRows) {
                try {
                    CreateQuestionRequest request = mapToRequest(row, bank.getId());
                    QuestionResponse created = questionBankService.createQuestionInBank(
                            bank, request, actorUserId, rejectActiveDuplicate);
                    Map<String, Object> summary = new LinkedHashMap<>();
                    summary.put("id", created.id());
                    summary.put("content", created.content());
                    summary.put("defaultPoints", created.defaultPoints());
                    createdQuestions.add(summary);
                } catch (RuntimeException ex) {
                    errors.add(rowError(row.rowNumber(), ex.getMessage()));
                }
            }

            job.setTotalRows(parsedRows.size());
            job.setSuccessRows(createdQuestions.size());
            job.setFailedRows(errors.size());
            job.setErrorSummary(errors);
            Map<String, Object> resultDetails = new LinkedHashMap<>();
            resultDetails.put("createdQuestions", createdQuestions);
            job.setResultDetails(resultDetails);
            job.setStatus(errors.isEmpty() ? ImportJob.Status.COMPLETED : ImportJob.Status.PARTIAL_SUCCESS);
            job.setFinishedAt(OffsetDateTime.now());
            job = importJobRepository.save(job);
            return toResponse(job);
        } catch (IOException | RuntimeException ex) {
            // A3 — không đọc được file (sai định dạng/hỏng hoàn toàn).
            return failJob(job, "File sai định dạng hoặc không đọc được: " + ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public QuestionImportResponse getJob(Long id) {
        ImportJob job = importJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.questionImport.jobNotFound",
                        new Object[]{id}, "Không tìm thấy import job id=" + id));
        return toResponse(job);
    }

    /**
     * File mẫu Word (.docx) soạn đề nhanh — tĩnh, không cá nhân hoá theo
     * bank/actor (khác GradeImportService.buildTemplate() cần điền sẵn
     * học sinh thật của 1 lớp) nên không cần tham số. 1 block ví dụ / mỗi
     * loại trong VALID_KINDS.
     */
    public byte[] buildWordTemplate() {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // KHÔNG thêm dòng tiêu đề/hướng dẫn trước block đầu tiên — WordQuestionRowParser coi
            // paragraph không-rỗng đầu tiên (hoặc ngay sau "---") là dòng mở block, dù không đúng
            // cú pháp "[LOAI]" (để báo lỗi rõ ràng thay vì bỏ qua) — 1 dòng tiêu đề ở đây sẽ tự
            // biến thành 1 block lỗi "nuốt" luôn nội dung block TRAC_NGHIEM thật ngay sau nó.
            appendParagraph(document, "[TRAC_NGHIEM]");
            appendParagraph(document, "Nội dung: What is the capital of France?");
            appendParagraph(document, "A. London");
            appendParagraph(document, "B. Paris");
            appendParagraph(document, "C. Berlin");
            appendParagraph(document, "D. Madrid");
            appendParagraph(document, "Đáp án đúng: B");
            appendParagraph(document, "Độ khó: EASY");
            appendParagraph(document, "Điểm: 1");
            appendParagraph(document, "Giải thích: Paris là thủ đô nước Pháp.");
            appendParagraph(document, "---");

            appendParagraph(document, "[TRAC_NGHIEM_VOICE]");
            appendParagraph(document, "Nội dung: Listen and choose the word you hear.");
            appendParagraph(document, "A. ship");
            appendParagraph(document, "B. sheep");
            appendParagraph(document, "C. chip");
            appendParagraph(document, "D. cheap");
            appendParagraph(document, "Đáp án đúng: B");
            appendParagraph(document, "URL Audio: https://example-r2.dev/lms/questions/audio/mau.mp3");
            appendParagraph(document, "Transcript: sheep");
            appendParagraph(document, "---");

            appendParagraph(document, "[DIEN_TU]");
            appendParagraph(document, "Nội dung: She ___ (go) to school every day.");
            appendParagraph(document, "Đáp án đúng: goes");
            appendParagraph(document, "Giải thích: Hiện tại đơn, ngôi thứ 3 số ít.");
            appendParagraph(document, "---");

            appendParagraph(document, "[TU_LUAN]");
            appendParagraph(document, "Nội dung: Write a 150-word essay about your favorite hobby.");
            appendParagraph(document, "Giải thích: Chấm theo thang điểm nội dung/ngữ pháp/từ vựng.");
            appendParagraph(document, "---");

            appendParagraph(document, "[SPEAKING]");
            appendParagraph(document, "Nội dung: Read the following sentence aloud.");
            appendParagraph(document, "Từ khóa phát âm: enthusiasm, literature, variety");
            appendParagraph(document, "Giải thích: Chấm theo độ chính xác phát âm các từ trọng điểm.");
            appendParagraph(document, "---");

            document.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Không tạo được file Word mẫu: " + ex.getMessage(), ex);
        }
    }

    // ===================== Helpers =====================

    private void appendParagraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);
    }

    /**
     * Map 1 dòng thô → CreateQuestionRequest, validate theo đúng quy tắc
     * QuestionEditorForm.tsx (FE) áp dụng cho soạn tay — đảm bảo Excel/Word/
     * form tay không lệch quy tắc nhau (xem Javadoc lớp).
     */
    private CreateQuestionRequest mapToRequest(QuestionRowParser.ParsedQuestionRow row, Long bankId) {
        if (isBlank(row.content())) {
            throw new IllegalArgumentException("Thiếu nội dung câu hỏi.");
        }
        String kind = normalizeToken(row.kind());
        if (!VALID_KINDS.contains(kind)) {
            throw new IllegalArgumentException("Loại câu hỏi không hợp lệ: '" + row.kind()
                    + "' — chỉ chấp nhận TRAC_NGHIEM/TRAC_NGHIEM_VOICE/DIEN_TU/TU_LUAN/SPEAKING.");
        }

        String difficulty = isBlank(row.difficulty()) ? "MEDIUM" : normalizeToken(row.difficulty());
        if (!VALID_DIFFICULTIES.contains(difficulty)) {
            throw new IllegalArgumentException("Độ khó không hợp lệ: '" + row.difficulty() + "' — chỉ chấp nhận EASY/MEDIUM/HARD.");
        }

        BigDecimal defaultPoints = parsePoints(row.defaultPoints());
        List<String> tags = parseTags(row.tags());
        String explanation = blankToNull(row.explanation());

        boolean isChoiceBased = kind.equals("TRAC_NGHIEM") || kind.equals("TRAC_NGHIEM_VOICE");
        List<QuestionChoiceRequest> choices = null;
        String correctAnswerText = null;
        String skill = null;
        String audioUrl = null;
        String imageUrl = null;
        String referencePassage = null;

        if (isChoiceBased) {
            choices = buildChoices(row);
            if (kind.equals("TRAC_NGHIEM_VOICE")) {
                skill = "LISTENING";
                if (isBlank(row.audioUrl())) {
                    throw new IllegalArgumentException("Trắc nghiệm Voice cần URL audio mẫu (đã upload sẵn qua Ngân hàng câu hỏi/API media upload).");
                }
                audioUrl = row.audioUrl().trim();
                referencePassage = blankToNull(row.referencePassage());
            }
        } else if (kind.equals("DIEN_TU")) {
            if (isBlank(row.correctAnswer())) {
                throw new IllegalArgumentException("Điền từ cần đáp án đúng để hệ thống tự chấm.");
            }
            correctAnswerText = row.correctAnswer().trim();
        } else if (kind.equals("TU_LUAN")) {
            imageUrl = blankToNull(row.imageUrl());
        } else { // SPEAKING
            skill = "SPEAKING";
            if (isBlank(row.referencePassage())) {
                throw new IllegalArgumentException("Speaking cần từ khóa/âm vị trọng điểm cần chấm.");
            }
            referencePassage = row.referencePassage().trim();
        }

        String questionType = kind.startsWith("TRAC_NGHIEM") ? "MULTIPLE_CHOICE" : kind.equals("DIEN_TU") ? "FILL_IN_BLANK"
                : kind.equals("TU_LUAN") ? "ESSAY" : "SPEAKING";

        return new CreateQuestionRequest(bankId, questionType, skill, difficulty, row.content().trim(),
                audioUrl, imageUrl, referencePassage, explanation, correctAnswerText, defaultPoints, tags, choices, null, null);
    }

    private List<QuestionChoiceRequest> buildChoices(QuestionRowParser.ParsedQuestionRow row) {
        if (isBlank(row.choiceA()) || isBlank(row.choiceB()) || isBlank(row.choiceC()) || isBlank(row.choiceD())) {
            throw new IllegalArgumentException("Câu trắc nghiệm cần đủ 4 đáp án A/B/C/D.");
        }
        String correctLetter = isBlank(row.correctAnswer()) ? "" : row.correctAnswer().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("A", "B", "C", "D").contains(correctLetter)) {
            throw new IllegalArgumentException("Đáp án đúng phải là 1 trong A/B/C/D (đang có: '" + row.correctAnswer() + "').");
        }
        List<String> contents = List.of(row.choiceA().trim(), row.choiceB().trim(), row.choiceC().trim(), row.choiceD().trim());
        List<QuestionChoiceRequest> choices = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String label = String.valueOf((char) ('A' + i));
            choices.add(new QuestionChoiceRequest(label, contents.get(i), label.equals(correctLetter), i + 1));
        }
        return choices;
    }

    private BigDecimal parsePoints(String raw) {
        if (isBlank(raw)) {
            return new BigDecimal("1.0");
        }
        try {
            BigDecimal points = new BigDecimal(raw.trim().replace(',', '.'));
            if (points.signum() < 0) {
                throw new NumberFormatException("negative");
            }
            return points;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Điểm mặc định không hợp lệ: '" + raw + "'.");
        }
    }

    private List<String> parseTags(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        List<String> tags = Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return tags.isEmpty() ? null : tags;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }

    /** Chuẩn hoá so khớp: trim, bỏ dấu tiếng Việt, upper-case, khoảng trắng/gạch ngang → "_". */
    private String normalizeToken(String raw) {
        String lowered = raw.trim().toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(lowered, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('đ', 'd');
        return decomposed.toUpperCase(Locale.ROOT).trim().replaceAll("[\\s\\-]+", "_");
    }

    private ImportJob createJob(MultipartFile file, User actor) {
        ImportJob job = new ImportJob();
        job.setImportType(ImportJob.ImportType.QUESTIONS);
        job.setSourceFileName(file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename());
        job.setSourceFileUrl("in-memory://" + job.getSourceFileName());
        job.setUploadedBy(actor);
        job.setStatus(ImportJob.Status.PROCESSING);
        job.setStartedAt(OffsetDateTime.now());
        return importJobRepository.save(job);
    }

    private Map<String, Object> rowError(int rowNumber, String reason) {
        Map<String, Object> error = new HashMap<>();
        error.put("row", rowNumber);
        error.put("reason", reason);
        return error;
    }

    private QuestionImportResponse failJob(ImportJob job, String reason) {
        job.setStatus(ImportJob.Status.FAILED);
        job.setErrorSummary(List.of(rowError(0, reason)));
        job.setFinishedAt(OffsetDateTime.now());
        job = importJobRepository.save(job);
        return toResponse(job);
    }

    @SuppressWarnings("unchecked")
    private QuestionImportResponse toResponse(ImportJob job) {
        List<QuestionImportResponse.QuestionImportedRow> createdQuestions = new ArrayList<>();
        if (job.getResultDetails() != null && job.getResultDetails().get("createdQuestions") instanceof List<?> raw) {
            for (Object item : raw) {
                Map<String, Object> map = (Map<String, Object>) item;
                Long id = ((Number) map.get("id")).longValue();
                String content = (String) map.get("content");
                BigDecimal defaultPoints = new BigDecimal(map.get("defaultPoints").toString());
                createdQuestions.add(new QuestionImportResponse.QuestionImportedRow(id, content, defaultPoints));
            }
        }
        return new QuestionImportResponse(job.getId(), job.getSourceFileName(), job.getTotalRows(), job.getSuccessRows(),
                job.getFailedRows(), job.getStatus().name(), job.getErrorSummary(), createdQuestions);
    }
}
