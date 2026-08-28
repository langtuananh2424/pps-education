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
 * Phạm vi loại câu hỏi (bổ sung 2026-08-26, đã xác nhận với người dùng — đồng bộ
 * đủ 4 loại Điền từ-Hộp từ vựng/Sắp xếp câu, trước đó CHƯA từng hỗ trợ dù
 * QuestionEditorForm.tsx đã có từ V78): 9 loại UI —
 * TRAC_NGHIEM/TRAC_NGHIEM_VOICE/DIEN_TU/TU_LUAN/SPEAKING/DIEN_TU_HOP_TU_VUNG/
 * DIEN_TU_HOP_TU_VUNG_ANH/SAP_XEP_CAU/SAP_XEP_CHU_CAI — không mở rộng sang
 * TRUE_FALSE/MULTIPLE_ANSWER (dù Question.QuestionType có 6 giá trị) để câu
 * hỏi tạo qua import luôn sửa lại được bằng form tay sẵn có. CỐ Ý vẫn không
 * hỗ trợ INLINE_CHOICE/VOICE_PICTURE_CHOICE (kind ảo phụ thuộc số lượng/ảnh
 * đáp án khó diễn đạt gọn trong 1 dòng bảng tính) — không thuộc phạm vi.
 *
 * Bổ sung 2 loại riêng GV nước ngoài (bổ sung 2026-08-26, đã xác nhận với
 * người dùng — mở khóa import cho tab "Soạn Bài mới" phía GV nước ngoài,
 * đi cùng tính năng khóa kind-picker theo Nhóm kỹ năng, xem
 * skillCategoryKinds.ts phía FE): NGHE_NOP_AUDIO (mirror
 * LISTENING_AUDIO_SUBMISSION ở form tay) và NGHE_DIEN_TU (mirror
 * LISTENING_FILL_IN_BLANK). "Trắc nghiệm Voice" (TRAC_NGHIEM_VOICE) đã
 * hoạt động sẵn từ trước, dùng lại nguyên. "Nghe chọn hình"
 * (VOICE_PICTURE_CHOICE) vẫn KHÔNG đưa vào import — chỉ tồn tại dạng
 * composite ListeningGroupBuilder, cấu trúc ảnh-theo-từng-đáp-án không
 * diễn đạt gọn trong 1 dòng bảng tính.
 *
 * DIEN_TU_NHOM (bổ sung 2026-08-28, đã xác nhận với người dùng — "Cách B", mirror
 * FillInBlankGroupBuilder.tsx phía FE): loại DUY NHẤT mà 1 dòng file tạo ra NHIỀU Question (mỗi câu
 * trong "Nội dung" phân tách bằng dấu | thành 1 Question FILL_IN_BLANK riêng, cùng groupKey) thay vì
 * đúng 1 như mọi loại khác — xem mapToGroupRequests(). Thay thế DIEN_TU_HOP_TU_VUNG khi cần mỗi câu
 * tự có điểm/nhãn riêng thay vì chấm all-or-nothing.
 *
 * Kiến trúc parser theo Open/Closed (xem QuestionRowParser) — Spring tự
 * inject mọi bean implement interface này, chọn theo phần mở rộng file.
 */
@Service
public class QuestionImportService {

    /**
     * Bổ sung 2026-08-28 (đã xác nhận với người dùng — "Cách B") — "DIEN_TU_NHOM": 1 DÒNG file =
     * NGUYÊN 1 Ex. nhiều câu FILL_IN_BLANK (mirror FillInBlankGroupBuilder.tsx phía FE), khác hẳn mọi
     * kind khác ở chỗ 1 dòng tạo ra NHIỀU Question (cùng groupKey) thay vì đúng 1. Xem
     * mapToGroupRequests().
     */
    private static final String KIND_FILL_IN_BLANK_GROUP = "DIEN_TU_NHOM";

    private static final Set<String> VALID_KINDS = Set.of(
            "TRAC_NGHIEM", "TRAC_NGHIEM_VOICE", "DIEN_TU", "TU_LUAN", "SPEAKING",
            "DIEN_TU_HOP_TU_VUNG", "DIEN_TU_HOP_TU_VUNG_ANH", "SAP_XEP_CAU", "SAP_XEP_CHU_CAI",
            "NGHE_NOP_AUDIO", "NGHE_DIEN_TU", KIND_FILL_IN_BLANK_GROUP);
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
        return importQuestions(bankId, file, actorUserId, null);
    }

    /**
     * Bổ sung 2026-08-28 (đã xác nhận với người dùng) — {@code defaultKind}: GV chọn 1 lần ở panel
     * Import (khớp thói quen thật "1 Ex chỉ 1 loại câu hỏi") thay vì phải gõ lại cột "Loại câu hỏi"
     * ở MỌI dòng trong file — dòng nào cột đó để trống thì dùng defaultKind, dòng nào tự ghi giá trị
     * riêng thì vẫn ưu tiên giá trị đó (không ép cả file cùng 1 loại). Null/rỗng = giữ hành vi cũ
     * (bắt buộc mỗi dòng tự ghi loại). Xem resolveKind().
     */
    @Transactional
    public QuestionImportResponse importQuestions(Long bankId, MultipartFile file, Long actorUserId, String defaultKind) {
        QuestionBank bank = questionBankRepository.findById(bankId)
                .orElseThrow(() -> new ResourceNotFoundException("error.questionImport.bankNotFound",
                        new Object[]{bankId}, "Không tìm thấy ngân hàng câu hỏi id=" + bankId));
        if (examRepository.existsByQuestionBankId(bankId)) {
            throw new ResourceNotFoundException("error.questionImport.bankNotFound",
                    new Object[]{bankId}, "Không tìm thấy ngân hàng câu hỏi id=" + bankId);
        }
        return importQuestionsIntoBank(bank, file, actorUserId, true, defaultKind);
    }

    /** Primitive dùng chung: Exam internal bank cho phép trùng; generic legacy bank chặn trùng. */
    @Transactional
    QuestionImportResponse importQuestionsIntoBank(QuestionBank bank, MultipartFile file,
                                                   Long actorUserId, boolean rejectActiveDuplicate, String defaultKind) {
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
            // Bổ sung 2026-08-28 — đếm THEO DÒNG file (khớp totalRows/parsedRows.size()), KHÔNG đếm
            // theo số Question tạo ra: DIEN_TU_NHOM có thể tạo N Question từ ĐÚNG 1 dòng, nếu đếm theo
            // createdQuestions.size() thì successRows sẽ vượt quá totalRows (sai số liệu báo cáo).
            int successRowCount = 0;
            for (QuestionRowParser.ParsedQuestionRow row : parsedRows) {
                try {
                    String kind = resolveKind(row, defaultKind);
                    // DIEN_TU_NHOM là kind DUY NHẤT mà 1 dòng tạo ra NHIỀU Question (mapToGroupRequests
                    // tự validate + pre-check trùng TRƯỚC KHI tạo bất kỳ câu nào, xem Javadoc ở đó) —
                    // mọi kind khác vẫn 1 dòng = 1 Question như cũ.
                    List<CreateQuestionRequest> requests = KIND_FILL_IN_BLANK_GROUP.equals(kind)
                            ? mapToGroupRequests(row, bank.getId(), rejectActiveDuplicate)
                            : List.of(mapToRequest(row, bank.getId(), kind));
                    for (CreateQuestionRequest request : requests) {
                        QuestionResponse created = questionBankService.createQuestionInBank(
                                bank, request, actorUserId, rejectActiveDuplicate);
                        Map<String, Object> summary = new LinkedHashMap<>();
                        summary.put("id", created.id());
                        summary.put("content", created.content());
                        summary.put("defaultPoints", created.defaultPoints());
                        createdQuestions.add(summary);
                    }
                    successRowCount++;
                } catch (RuntimeException ex) {
                    errors.add(rowError(row.rowNumber(), ex.getMessage()));
                }
            }

            job.setTotalRows(parsedRows.size());
            job.setSuccessRows(successRowCount);
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

    /** Nguồn chân lý DUY NHẤT nội dung từng block mẫu — dùng chung cho cả template đủ (legacy) lẫn
     * template lọc theo Nhóm kỹ năng (xem buildWordTemplate(String, String) bên dưới). Giữ nguyên
     * ĐÚNG THỨ TỰ như trước khi tách (LinkedHashMap) để không đổi hành vi endpoint legacy. */
    private static final Map<String, List<String>> TEMPLATE_BLOCKS = buildTemplateBlocks();

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — mapping Kind ↔ Nhóm kỹ năng dùng
     * để lọc template, mirror CHÍNH XÁC bảng VIETNAMESE_SKILL_KINDS/FOREIGN_LISTENING_KINDS ở FE
     * (skillCategoryKinds.ts). READING không có entry — Cloze/Grid chỉ là composite builder, chưa
     * từng import được (xem Javadoc lớp) nên không có block nào khớp — hợp lý vì FE cũng ẩn hẳn tab
     * Excel/Word khi chọn Reading. "SPEAKING" (oral, không phải Nghe) không thuộc Nhóm kỹ năng nào
     * trong bảng khóa này — mirror việc FE cũng không đưa "SPEAKING" vào allowedKinds cho GV nào cả.
     */
    private static final Map<String, Set<String>> SKILL_CATEGORY_KIND_TOKENS = Map.of(
            "VOCAB_GRAMMAR", Set.of("TRAC_NGHIEM", "TRAC_NGHIEM_VOICE", "DIEN_TU", KIND_FILL_IN_BLANK_GROUP,
                    "DIEN_TU_HOP_TU_VUNG", "DIEN_TU_HOP_TU_VUNG_ANH", "SAP_XEP_CAU", "SAP_XEP_CHU_CAI"),
            "WRITING", Set.of("TU_LUAN"),
            "LISTENING", Set.of("TRAC_NGHIEM_VOICE", "NGHE_NOP_AUDIO", "NGHE_DIEN_TU"));

    /**
     * File mẫu Word (.docx) soạn đề nhanh — tĩnh, không cá nhân hoá theo
     * bank/actor (khác GradeImportService.buildTemplate() cần điền sẵn
     * học sinh thật của 1 lớp) nên không cần tham số. 1 block ví dụ / mỗi
     * loại trong VALID_KINDS — endpoint legacy `/api/question-imports/template.docx`,
     * không có ngữ cảnh Bài/Nhóm kỹ năng nên in ĐỦ TẤT CẢ.
     */
    public byte[] buildWordTemplate() {
        return buildWordTemplate(null, null, null);
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — template dùng ở tab "Soạn Bài
     * mới" (ExamQuestionController), lọc chỉ còn các block khớp Nhóm kỹ năng đã chọn ở bước 1, tránh
     * giáo viên import nhầm loại không khớp Nhóm kỹ năng của Bài. {@code skillCategory}/
     * {@code teacherType} = null (hoặc không khớp mapping) → in đủ tất cả (giữ hành vi cũ).
     */
    public byte[] buildWordTemplate(String skillCategory, String teacherType) {
        return buildWordTemplate(skillCategory, teacherType, null);
    }

    /**
     * Bổ sung 2026-08-28 (đã xác nhận với người dùng) — {@code kindFilter}: khi GV đã chọn "Loại câu
     * hỏi mặc định" ở panel Import (khớp thói quen "1 Ex chỉ 1 loại câu hỏi"), CHỈ in đúng 1 block
     * khớp loại đó — ưu tiên CAO HƠN skillCategory/teacherType (GV đã chọn cụ thể rồi thì không cần cả
     * bảng tra cứu nhiều loại nữa, chỉ cần 1 ví dụ đúng loại để copy xuống nhiều dòng). Loại không hợp
     * lệ (gõ tay URL/token lạ) → coi như không lọc theo kind, KHÔNG throw lỗi (tải file mẫu không nên
     * hard-fail vì query param sai, chỉ ảnh hưởng UX chứ không phải dữ liệu).
     */
    public byte[] buildWordTemplate(String skillCategory, String teacherType, String kindFilter) {
        Set<String> allowedTokens;
        if (!isBlank(kindFilter) && VALID_KINDS.contains(normalizeToken(kindFilter))) {
            allowedTokens = Set.of(normalizeToken(kindFilter));
        } else {
            // SKILL_CATEGORY_KIND_TOKENS là Map.of(...) (immutable) - .get(null) ném NullPointerException
            // thay vì trả null như HashMap. Guard skillCategory == null trước khi tra map để đúng hành vi
            // đã ghi ở Javadoc (null -> in đủ tất cả) - bug thật phát hiện qua CI (2026-08-27), không phải
            // đổi ý định thiết kế.
            allowedTokens = "FOREIGN".equals(teacherType)
                    ? SKILL_CATEGORY_KIND_TOKENS.get("LISTENING")
                    : (skillCategory == null ? null : SKILL_CATEGORY_KIND_TOKENS.get(skillCategory));
        }
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (Map.Entry<String, List<String>> block : TEMPLATE_BLOCKS.entrySet()) {
                if (allowedTokens != null && !allowedTokens.contains(block.getKey())) {
                    continue;
                }
                for (String line : block.getValue()) {
                    appendParagraph(document, line);
                }
            }
            document.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Không tạo được file Word mẫu: " + ex.getMessage(), ex);
        }
    }

    // ===================== Helpers =====================

    private static Map<String, List<String>> buildTemplateBlocks() {
        Map<String, List<String>> blocks = new LinkedHashMap<>();
        // KHÔNG thêm dòng tiêu đề/hướng dẫn trước block đầu tiên — WordQuestionRowParser coi
        // paragraph không-rỗng đầu tiên (hoặc ngay sau "---") là dòng mở block, dù không đúng
        // cú pháp "[LOAI]" (để báo lỗi rõ ràng thay vì bỏ qua) — 1 dòng tiêu đề ở đây sẽ tự
        // biến thành 1 block lỗi "nuốt" luôn nội dung block TRAC_NGHIEM thật ngay sau nó.
        blocks.put("TRAC_NGHIEM", List.of(
                "[TRAC_NGHIEM]",
                "Nội dung: What is the capital of France?",
                "A. London", "B. Paris", "C. Berlin", "D. Madrid",
                "Đáp án đúng: B",
                "Độ khó: EASY",
                "Điểm: 1",
                "Giải thích: Paris là thủ đô nước Pháp.",
                "---"));
        blocks.put("TRAC_NGHIEM_VOICE", List.of(
                "[TRAC_NGHIEM_VOICE]",
                "Nội dung: Listen and choose the word you hear.",
                "A. ship", "B. sheep", "C. chip", "D. cheap",
                "Đáp án đúng: B",
                "URL Audio: https://example-r2.dev/lms/questions/audio/mau.mp3",
                "Transcript: sheep",
                "---"));
        blocks.put("DIEN_TU", List.of(
                "[DIEN_TU]",
                "Nội dung: She ___ (go) to school every day.",
                "Đáp án đúng: goes",
                "Giải thích: Hiện tại đơn, ngôi thứ 3 số ít.",
                "---"));
        blocks.put("TU_LUAN", List.of(
                "[TU_LUAN]",
                "Nội dung: Write a 150-word essay about your favorite hobby.",
                "Giải thích: Chấm theo thang điểm nội dung/ngữ pháp/từ vựng.",
                "---"));
        blocks.put("DIEN_TU_HOP_TU_VUNG", List.of(
                "[DIEN_TU_HOP_TU_VUNG]",
                "Nội dung: She ___ to school every day. He ___ football on Sundays.",
                "Đáp án đúng: goes|plays",
                "Giải thích: Mỗi chỗ trống 1 từ, phân tách bằng dấu | theo ĐÚNG thứ tự.",
                "---"));
        blocks.put("DIEN_TU_HOP_TU_VUNG_ANH", List.of(
                "[DIEN_TU_HOP_TU_VUNG_ANH]",
                "Nội dung: 1. The cat is ___ the bed. 2. The ball is ___ the box.",
                "Đáp án đúng: under|next to",
                "URL Hình ảnh: https://example-r2.dev/lms/questions/images/mau-phong.png",
                "Transcript: under, next to, behind, in front of, on",
                "Giải thích: Cột Transcript dùng làm hộp từ vựng hiển thị cho học sinh (có thể thêm từ nhiễu), để trống thì hộp từ = chính đáp án đúng.",
                "---"));
        blocks.put(KIND_FILL_IN_BLANK_GROUP, List.of(
                "[DIEN_TU_NHOM]",
                "Nội dung: Tom is very ___.|English is my ___ subject.|Our football ___ helps us win the game.",
                "Đáp án đúng: smart|favourite|coach",
                "Transcript: activity, advanced, beginner, classmate, smart, coach, competition, course, favourite, geography, history, practice",
                "Giải thích: Mỗi câu phân tách bằng dấu | trong \"Nội dung\", ĐÚNG thứ tự khớp \"Đáp án đúng\" (cũng phân tách bằng dấu |, phải cùng số lượng). Cột Transcript (tùy chọn) = hộp từ vựng THAM KHẢO hiện chung 1 lần, không phải đáp án. URL Hình ảnh (tùy chọn) = ảnh riêng từng câu, phân tách bằng dấu | cùng số lượng với Nội dung (để trống 1 vị trí = câu đó không có ảnh). Mỗi câu tạo thành 1 câu hỏi riêng, tự có điểm/nhãn \"Câu N.\" riêng — không chấm all-or-nothing như Điền từ - Hộp từ vựng.",
                "---"));
        blocks.put("SAP_XEP_CAU", List.of(
                "[SAP_XEP_CAU]",
                "Nội dung: Sắp xếp thành câu hoàn chỉnh.",
                "Đáp án đúng: This|is|a|pen",
                "Giải thích: Mỗi khối từ/cụm 1 phần tử, phân tách bằng dấu | theo ĐÚNG thứ tự câu hoàn chỉnh.",
                "---"));
        blocks.put("SAP_XEP_CHU_CAI", List.of(
                "[SAP_XEP_CHU_CAI]",
                "Nội dung: Sắp xếp chữ cái thành từ đúng (nghĩa: nụ cười).",
                "Đáp án đúng: s|m|i|l|e",
                "URL Hình ảnh: https://example-r2.dev/lms/questions/images/mau-smile.png",
                "Giải thích: Mỗi chữ cái 1 phần tử, phân tách bằng dấu | theo ĐÚNG thứ tự tạo thành từ đúng.",
                "---"));
        blocks.put("SPEAKING", List.of(
                "[SPEAKING]",
                "Nội dung: Read the following sentence aloud.",
                "Từ khóa phát âm: enthusiasm, literature, variety",
                "Giải thích: Chấm theo độ chính xác phát âm các từ trọng điểm.",
                "---"));
        blocks.put("NGHE_NOP_AUDIO", List.of(
                "[NGHE_NOP_AUDIO]",
                "Nội dung: Listen to the audio and record your answer.",
                "URL Audio: https://example-r2.dev/lms/questions/audio/mau-nghe.mp3",
                "Giải thích: Học sinh nộp audio, chấm tay ở Hàng chờ chấm bài.",
                "---"));
        blocks.put("NGHE_DIEN_TU", List.of(
                "[NGHE_DIEN_TU]",
                "Nội dung: Listen and fill in the blank: She usually ___ to work.",
                "URL Audio: https://example-r2.dev/lms/questions/audio/mau-nghe-dien-tu.mp3",
                "Đáp án đúng: drives",
                "Giải thích: Hệ thống tự chấm theo đáp án đúng.",
                "---"));
        return blocks;
    }

    private void appendParagraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);
    }

    /**
     * Bổ sung 2026-08-28 (đã xác nhận với người dùng) — gộp 1 chỗ DUY NHẤT việc chọn kind thật của 1
     * dòng (tự ghi hay dùng defaultKind) + validate VALID_KINDS, dùng chung cho cả nhánh 1-dòng-1-câu
     * (mapToRequest) lẫn nhánh DIEN_TU_NHOM (mapToGroupRequests không cần biết kind vì luôn cố định
     * FILL_IN_BLANK, nhưng vẫn cần hàm này để BIẾT đây có phải DIEN_TU_NHOM hay không trước khi rẽ
     * nhánh — xem importQuestionsIntoBank).
     */
    private String resolveKind(QuestionRowParser.ParsedQuestionRow row, String defaultKind) {
        String raw = isBlank(row.kind()) ? defaultKind : row.kind();
        if (isBlank(raw)) {
            throw new IllegalArgumentException(
                    "Thiếu loại câu hỏi — cột \"Loại câu hỏi\" đang trống và chưa chọn loại mặc định ở panel Import.");
        }
        String kind = normalizeToken(raw);
        if (!VALID_KINDS.contains(kind)) {
            throw new IllegalArgumentException("Loại câu hỏi không hợp lệ: '" + raw
                    + "' — chỉ chấp nhận TRAC_NGHIEM/TRAC_NGHIEM_VOICE/DIEN_TU/DIEN_TU_NHOM/TU_LUAN/SPEAKING/"
                    + "DIEN_TU_HOP_TU_VUNG/DIEN_TU_HOP_TU_VUNG_ANH/SAP_XEP_CAU/SAP_XEP_CHU_CAI/"
                    + "NGHE_NOP_AUDIO/NGHE_DIEN_TU.");
        }
        return kind;
    }

    /**
     * Map 1 dòng thô → CreateQuestionRequest, validate theo đúng quy tắc
     * QuestionEditorForm.tsx (FE) áp dụng cho soạn tay — đảm bảo Excel/Word/
     * form tay không lệch quy tắc nhau (xem Javadoc lớp). {@code kind} đã được resolveKind() chọn +
     * validate sẵn (tự ghi hay dùng defaultKind) — hàm này không tự suy ra kind nữa.
     */
    private CreateQuestionRequest mapToRequest(QuestionRowParser.ParsedQuestionRow row, Long bankId, String kind) {
        if (isBlank(row.content())) {
            throw new IllegalArgumentException("Thiếu nội dung câu hỏi.");
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
        Map<String, Object> structuredContent = null;

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
        } else if (kind.equals("DIEN_TU_HOP_TU_VUNG") || kind.equals("DIEN_TU_HOP_TU_VUNG_ANH")) {
            // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — tái dùng cột "Đáp án đúng"
            // làm danh sách blanks CÓ THỨ TỰ, phân tách bằng dấu | (không thêm cột mới, mirror cách
            // "Transcript/Từ khóa phát âm" đã mang nhiều nghĩa tùy loại từ trước).
            if (isBlank(row.correctAnswer())) {
                throw new IllegalArgumentException("Điền từ - Hộp từ vựng cần danh sách đáp án đúng theo thứ tự chỗ trống, phân tách bằng dấu | (VD: went|to|school).");
            }
            Map<String, Object> sc = new LinkedHashMap<>();
            sc.put("blanks", splitOrdered(row.correctAnswer()));
            if (kind.equals("DIEN_TU_HOP_TU_VUNG_ANH")) {
                imageUrl = blankToNull(row.imageUrl());
                // Tái dùng cột "Transcript/Từ khóa phát âm" (referencePassage) làm hộp từ vựng — tùy
                // chọn, để trống thì hộp từ = chính blanks (mirror hành vi mặc định của form tay).
                List<String> wordBankOptions = parseTags(row.referencePassage());
                if (wordBankOptions != null) {
                    sc.put("wordBankOptions", wordBankOptions);
                }
            }
            structuredContent = sc;
        } else if (kind.equals("SAP_XEP_CAU") || kind.equals("SAP_XEP_CHU_CAI")) {
            // Cùng cơ chế tái dùng cột "Đáp án đúng" — mỗi khối/chữ cái phân tách bằng dấu |.
            List<String> chunks = isBlank(row.correctAnswer()) ? List.of() : splitOrdered(row.correctAnswer());
            if (chunks.size() < 2) {
                throw new IllegalArgumentException("Sắp xếp câu/chữ cái cần tối thiểu 2 khối theo đúng thứ tự, phân tách bằng dấu | (VD: This is|a|pen).");
            }
            structuredContent = Map.of("chunks", chunks);
            if (kind.equals("SAP_XEP_CHU_CAI")) {
                imageUrl = blankToNull(row.imageUrl());
            }
        } else if (kind.equals("SPEAKING")) {
            skill = "SPEAKING";
            if (isBlank(row.referencePassage())) {
                throw new IllegalArgumentException("Speaking cần từ khóa/âm vị trọng điểm cần chấm.");
            }
            referencePassage = row.referencePassage().trim();
        } else if (kind.equals("NGHE_NOP_AUDIO")) {
            // Mirror LISTENING_AUDIO_SUBMISSION ở form tay (isVoiceOrListeningAudio) — bắt buộc audio,
            // KHÔNG bắt buộc referencePassage (chấm tay ở "Hàng chờ chấm bài", khác Speaking thường
            // cần từ khóa phát âm để hỗ trợ tự động chấm).
            skill = "LISTENING";
            if (isBlank(row.audioUrl())) {
                throw new IllegalArgumentException("Nghe & nộp audio cần URL audio mẫu (đã upload sẵn qua Ngân hàng câu hỏi/API media upload).");
            }
            audioUrl = row.audioUrl().trim();
        } else { // NGHE_DIEN_TU — mirror LISTENING_FILL_IN_BLANK ở form tay, bắt buộc cả audio lẫn đáp án.
            skill = "LISTENING";
            if (isBlank(row.audioUrl())) {
                throw new IllegalArgumentException("Nghe điền từ cần URL audio mẫu (đã upload sẵn qua Ngân hàng câu hỏi/API media upload).");
            }
            if (isBlank(row.correctAnswer())) {
                throw new IllegalArgumentException("Nghe điền từ cần đáp án đúng để hệ thống tự chấm.");
            }
            audioUrl = row.audioUrl().trim();
            correctAnswerText = row.correctAnswer().trim();
        }

        String questionType = kind.startsWith("TRAC_NGHIEM") ? "MULTIPLE_CHOICE"
                : kind.equals("DIEN_TU") || kind.equals("NGHE_DIEN_TU") ? "FILL_IN_BLANK"
                : kind.equals("TU_LUAN") ? "ESSAY"
                : kind.startsWith("DIEN_TU_HOP_TU_VUNG") ? "WORD_BANK"
                : kind.startsWith("SAP_XEP") ? "SENTENCE_BUILDING"
                : "SPEAKING";

        return new CreateQuestionRequest(bankId, questionType, skill, difficulty, row.content().trim(),
                audioUrl, imageUrl, referencePassage, explanation, correctAnswerText, defaultPoints, tags, choices, structuredContent, null);
    }

    /**
     * DIEN_TU_NHOM (bổ sung 2026-08-28, đã xác nhận với người dùng — "Cách B"): 1 dòng file → N
     * CreateQuestionRequest (1/câu), mirror ĐÚNG FillInBlankGroupBuilder.tsx phía FE — mỗi câu 1
     * FILL_IN_BLANK riêng, cùng groupKey, cùng "hộp từ vựng" tham khảo (structuredContent.wordBox) nếu
     * có, KHÔNG chấm all-or-nothing như DIEN_TU_HOP_TU_VUNG (WORD_BANK).
     *
     * Validate + pre-check trùng nội dung TOÀN BỘ nhóm TRƯỚC KHI trả về (không tạo Question nào ở đây
     * cả — việc tạo vẫn ở vòng lặp gọi hàm này, xem importQuestionsIntoBank) để không bao giờ xảy ra
     * trạng thái tạo dở dang N-1/N câu rồi mới lỗi ở câu cuối (xem Javadoc existsActiveDuplicate ở
     * QuestionBankService — lý do KHÔNG dùng transaction REQUIRES_NEW để tự rollback theo dòng).
     */
    private List<CreateQuestionRequest> mapToGroupRequests(QuestionRowParser.ParsedQuestionRow row, Long bankId, boolean rejectActiveDuplicate) {
        if (isBlank(row.content())) {
            throw new IllegalArgumentException("Thiếu nội dung câu hỏi.");
        }
        List<String> sentences = splitPipeKeepBlanks(row.content());
        for (int i = 0; i < sentences.size(); i++) {
            if (sentences.get(i).isEmpty()) {
                throw new IllegalArgumentException("Nhiều câu điền từ: câu thứ " + (i + 1) + " (theo dấu |) bị rỗng trong \"Nội dung\".");
            }
        }
        if (isBlank(row.correctAnswer())) {
            throw new IllegalArgumentException("Nhiều câu điền từ cần \"Đáp án đúng\" cho từng câu, phân tách bằng dấu | theo ĐÚNG thứ tự (VD: smart|favourite|coach).");
        }
        List<String> answers = splitPipeKeepBlanks(row.correctAnswer());
        if (answers.size() != sentences.size()) {
            throw new IllegalArgumentException("Nhiều câu điền từ: số đáp án (" + answers.size()
                    + ") không khớp số câu (" + sentences.size() + ") — mỗi câu trong \"Nội dung\" cần đúng 1 đáp án tương ứng trong \"Đáp án đúng\", cùng phân tách bằng dấu |.");
        }
        for (int i = 0; i < answers.size(); i++) {
            if (answers.get(i).isEmpty()) {
                throw new IllegalArgumentException("Nhiều câu điền từ: câu thứ " + (i + 1) + " thiếu đáp án đúng trong \"Đáp án đúng\".");
            }
        }
        List<String> images = null;
        if (!isBlank(row.imageUrl())) {
            images = splitPipeKeepBlanks(row.imageUrl());
            if (images.size() != sentences.size()) {
                throw new IllegalArgumentException("Nhiều câu điền từ: số ảnh minh họa (" + images.size()
                        + ") không khớp số câu (" + sentences.size() + ") trong \"URL Hình ảnh\" — để trống 1 vị trí (VD url1||url3) nếu câu đó không có ảnh, nhưng vẫn phải đủ số lượng dấu |.");
            }
        }
        // Trùng NGAY TRONG cùng 1 nhóm (VD copy nhầm 2 câu giống hệt nhau) — DB pre-check bên dưới
        // không phát hiện được ca này vì lúc kiểm tra chưa câu nào được tạo cả.
        if (Set.copyOf(sentences).size() != sentences.size()) {
            throw new IllegalArgumentException("Nhiều câu điền từ: có 2 câu trùng nội dung trong CÙNG 1 nhóm — mỗi câu phải khác nhau.");
        }
        if (rejectActiveDuplicate) {
            for (String sentence : sentences) {
                if (questionBankService.existsActiveDuplicate(bankId, sentence)) {
                    throw new IllegalArgumentException("Nhiều câu điền từ: câu \"" + sentence + "\" đã tồn tại trong ngân hàng câu hỏi (trùng nội dung) — không thể tạo trùng.");
                }
            }
        }

        String difficulty = isBlank(row.difficulty()) ? "MEDIUM" : normalizeToken(row.difficulty());
        if (!VALID_DIFFICULTIES.contains(difficulty)) {
            throw new IllegalArgumentException("Độ khó không hợp lệ: '" + row.difficulty() + "' — chỉ chấp nhận EASY/MEDIUM/HARD.");
        }
        BigDecimal defaultPoints = parsePoints(row.defaultPoints());
        List<String> tags = parseTags(row.tags());
        String explanation = blankToNull(row.explanation());
        // Transcript/Từ khóa phát âm/Hộp từ vựng (referencePassage) tái dùng làm hộp từ vựng THAM
        // KHẢO tĩnh (giống DIEN_TU_HOP_TU_VUNG_ANH tái dùng cột này làm wordBankOptions) — dùng CHUNG
        // giá trị này cho MỌI câu trong nhóm (mirror cách FillInBlankGroupBuilder.tsx đặt cùng
        // structuredContent.wordBox lên mọi Question tạo ra).
        List<String> wordBox = parseTags(row.referencePassage());
        Map<String, Object> structuredContent = wordBox == null ? null : Map.of("wordBox", wordBox);
        String groupKey = "fillblank-import-" + System.currentTimeMillis() + "-" + row.rowNumber();

        List<CreateQuestionRequest> requests = new ArrayList<>();
        for (int i = 0; i < sentences.size(); i++) {
            String imageUrl = images == null || images.get(i).isEmpty() ? null : images.get(i);
            requests.add(new CreateQuestionRequest(bankId, "FILL_IN_BLANK", null, difficulty, sentences.get(i),
                    null, imageUrl, null, explanation, answers.get(i), defaultPoints, tags, null, structuredContent, groupKey));
        }
        return requests;
    }

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — tách 1 chuỗi thành danh sách CÓ THỨ TỰ theo dấu |, dùng cho blanks/chunks. */
    private List<String> splitOrdered(String raw) {
        return Arrays.stream(raw.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /**
     * Bổ sung 2026-08-28 (đã xác nhận với người dùng) — như splitOrdered nhưng GIỮ NGUYÊN vị trí rỗng
     * (không filter) — cần cho DIEN_TU_NHOM vì "URL Hình ảnh" cho phép 1 vài câu KHÔNG có ảnh (vị trí
     * rỗng giữa 2 dấu |), lọc bỏ sẽ làm lệch thứ tự so với "Nội dung"/"Đáp án đúng".
     */
    private List<String> splitPipeKeepBlanks(String raw) {
        return Arrays.stream(raw.split("\\|", -1)).map(String::trim).toList();
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
            choices.add(new QuestionChoiceRequest(label, contents.get(i), null, label.equals(correctLetter), i + 1));
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
