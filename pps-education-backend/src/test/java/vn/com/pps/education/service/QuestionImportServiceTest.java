package vn.com.pps.education.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateQuestionBankRequest;
import vn.com.pps.education.dto.CreateQuestionRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.QuestionBankResponse;
import vn.com.pps.education.dto.QuestionImportResponse;
import vn.com.pps.education.dto.QuestionResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-40: Soạn đề nhanh qua file mẫu Excel/Word (bổ sung ngoài SDD gốc, đã
 * xác nhận với người dùng 2026-07-30) — Main Flow (tạo câu hỏi vào ngân
 * hàng cho cả 5 loại UI hỗ trợ), A2 (lỗi 1 dòng/block không chặn dòng
 * khác), A3 (file đọc hỏng hoàn toàn). Xem docs/uc/phan-he-07-lms-portal.md.
 */
@Transactional
class QuestionImportServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private QuestionImportService questionImportService;

    @Autowired
    private QuestionBankService questionBankService;

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private SiteRepository siteRepository;

    private User teacher;
    private QuestionBankResponse bank;

    @BeforeEach
    void setUp() {
        User headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null, null, null), headAcademic.getId());
        CurriculumResponse activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), headAcademic.getId());

        newSite();
        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");
        bank = questionBankService.createBank(
                new CreateQuestionBankRequest(bankCode(), "Ngân hàng Ngữ pháp", activeCurriculum.id(), null, "A1"),
                teacher.getId());
    }

    @Test
    void importQuestions_UC40_MainFlow_createsAllFiveKindsFromExcel() throws IOException {
        byte[] file = buildExcel(new String[][]{
                {"TRAC_NGHIEM", "EASY", "What is the capital of France?", "London", "Paris", "Berlin", "Madrid", "B",
                        null, "https://example.com/mc-stem.png", null, "1", "Paris la thu do nuoc Phap.", "geo,easy"},
                {"TRAC_NGHIEM_VOICE", "MEDIUM", "Listen and choose the word you hear.", "ship", "sheep", "chip", "cheap", "B",
                        "https://example.com/a.mp3", null, "sheep", "1", null, null},
                {"DIEN_TU", null, "She ___ (go) to school every day.", null, null, null, null, "goes",
                        null, null, null, "1", null, null},
                {"TU_LUAN", "HARD", "Write a 150-word essay about your hobby.", null, null, null, null, null,
                        null, "https://example.com/scan.png", null, "2", "Cham theo thang diem noi dung.", null},
                {"SPEAKING", null, "Read the following sentence aloud.", null, null, null, null, null,
                        null, null, "enthusiasm, literature", "1", null, null}
        });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "cau-hoi.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.totalRows()).isEqualTo(5);
        assertThat(result.successRows()).isEqualTo(5);
        assertThat(result.failedRows()).isEqualTo(0);
        assertThat(result.createdQuestions()).hasSize(5);

        List<QuestionResponse> saved = questionBankService.listQuestions(bank.id());
        assertThat(saved).hasSize(5);

        QuestionResponse mc = findByContentPrefix(saved, "What is the capital");
        assertThat(mc.questionType()).isEqualTo("MULTIPLE_CHOICE");
        assertThat(mc.skill()).isNull();
        assertThat(mc.choices()).hasSize(4);
        assertThat(mc.choices()).filteredOn(c -> c.content().equals("Paris")).extracting(c -> c.isCorrect()).containsExactly(true);
        // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-09 — fix bug thật: ảnh đề bài
        // (question-level) bị bỏ sót cho TRAC_NGHIEM dù cột "URL Hình ảnh" đã tồn tại chung cho mọi
        // loại câu hỏi và màn xem trước học sinh đã render question.imageUrl không phân biệt questionType.
        assertThat(mc.imageUrl()).isEqualTo("https://example.com/mc-stem.png");

        QuestionResponse voice = findByContentPrefix(saved, "Listen and choose");
        assertThat(voice.skill()).isEqualTo("LISTENING");
        assertThat(voice.audioUrl()).isEqualTo("https://example.com/a.mp3");

        QuestionResponse fillIn = findByContentPrefix(saved, "She ___");
        assertThat(fillIn.questionType()).isEqualTo("FILL_IN_BLANK");
        assertThat(fillIn.correctAnswerText()).isEqualTo("goes");
        assertThat(fillIn.difficulty()).isEqualTo("MEDIUM"); // mặc định khi để trống

        QuestionResponse essay = findByContentPrefix(saved, "Write a 150-word");
        assertThat(essay.questionType()).isEqualTo("ESSAY");
        assertThat(essay.imageUrl()).isEqualTo("https://example.com/scan.png");

        QuestionResponse speaking = findByContentPrefix(saved, "Read the following");
        assertThat(speaking.questionType()).isEqualTo("SPEAKING");
        assertThat(speaking.referencePassage()).isEqualTo("enthusiasm, literature");
    }

    /**
     * Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
     * nhận diện header theo TÊN (không còn vị trí cố định) — cột toàn tiếng
     * Anh, thứ tự XÁO TRỘN so với mẫu Việt vẫn đọc đúng.
     */
    @Test
    void importQuestions_boSung_acceptsEnglishHeadersInShuffledOrder() throws IOException {
        byte[] file = buildExcelWithHeaders(
                new String[]{"Content", "Correct Answer", "Question Type", "Answer B", "Answer A", "Answer D", "Answer C", "Points"},
                new String[][]{
                        {"What is the capital of France?", "B", "TRAC_NGHIEM", "Paris", "London", "Madrid", "Berlin", "1"}
                });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "cau-hoi-en.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.successRows()).isEqualTo(1);
        QuestionResponse saved = questionBankService.listQuestions(bank.id()).get(0);
        assertThat(saved.questionType()).isEqualTo("MULTIPLE_CHOICE");
        assertThat(saved.choices()).filteredOn(c -> c.content().equals("Paris")).extracting(c -> c.isCorrect()).containsExactly(true);
    }

    /** Trộn header tiếng Việt lẫn tiếng Anh trong CÙNG 1 file vẫn đọc đúng từng cột theo alias riêng của nó. */
    @Test
    void importQuestions_boSung_acceptsMixedVietnameseAndEnglishHeaders() throws IOException {
        byte[] file = buildExcelWithHeaders(
                new String[]{"Nội dung", "Question Type", "Đáp án đúng", "Answer A", "Đáp án B", "Answer C", "Đáp án D"},
                new String[][]{
                        {"What is the capital of France?", "TRAC_NGHIEM", "B", "London", "Paris", "Berlin", "Madrid"}
                });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "cau-hoi-mix.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.successRows()).isEqualTo(1);
    }

    /**
     * Bổ sung 2026-09-09 (đã xác nhận với người dùng) — hồi quy cho 1 bug thật: trước đây
     * {@code buildChoices} bắt buộc đủ 4 đáp án A/B/C/D, LỆCH với form soạn tay (2 đáp án được), và
     * khi sửa lần đầu vô tình dùng {@code List.of(...)} với phần tử null (Đáp án C/D để trống) —
     * List.of ném NullPointerException KHÔNG message, khiến cả 15 dòng import lỗi mà cột "reason"
     * rỗng trên UI (không rõ lỗi gì). Test đảm bảo câu trắc nghiệm CHỈ 2 đáp án (C/D để trống) import
     * thành công, không ném NPE.
     */
    @Test
    void importQuestions_boSung_multipleChoiceWithOnlyTwoAnswersImportsSuccessfully() throws IOException {
        byte[] file = buildExcel(new String[][]{
                {"TRAC_NGHIEM", null, "Peter and I are classmates. ___ often do homework together.", "We", "Us", null, null, "A",
                        null, null, null, "1", null, null}
        });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "trac-nghiem-2-dap-an.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.successRows()).isEqualTo(1);
        QuestionResponse saved = questionBankService.listQuestions(bank.id()).get(0);
        assertThat(saved.choices()).hasSize(2);
        assertThat(saved.choices()).filteredOn(c -> c.content().equals("We")).extracting(c -> c.isCorrect()).containsExactly(true);
        assertThat(saved.choices()).filteredOn(c -> c.content().equals("Us")).extracting(c -> c.isCorrect()).containsExactly(false);
    }

    /** Điền "nhảy cóc" (có Đáp án C nhưng Đáp án B để trống) phải báo lỗi rõ ràng, không cho tạo câu hỏi có lỗ hổng vị trí. */
    @Test
    void importQuestions_boSung_multipleChoiceRejectsGapBetweenAnswers() throws IOException {
        byte[] file = buildExcel(new String[][]{
                {"TRAC_NGHIEM", null, "What is the capital of France?", "London", null, "Paris", null, "C",
                        null, null, null, "1", null, null}
        });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "trac-nghiem-nhay-coc.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.successRows()).isEqualTo(0);
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errorSummary().get(0).get("reason").toString()).contains("không được bỏ trống xen giữa");
        assertThat(questionBankService.listQuestions(bank.id())).isEmpty();
    }

    /**
     * Bổ sung 2026-09-09 (đã xác nhận với người dùng) — nhiều câu TRAC_NGHIEM ĐỘC LẬP (đáp án riêng
     * từng câu, khác DOC_HIEU_LUOI dùng chung 1 bộ đáp án) nhưng cùng tham chiếu 1 đoạn văn dài — các
     * dòng LIÊN TIẾP cùng "Đoạn văn tham chiếu" (khớp nguyên văn) tự động gộp chung 1 groupKey để FE
     * hiện đoạn văn 1 lần duy nhất, xem computeAutoGroupKeys().
     */
    @Test
    void importQuestions_boSung_autoGroupsConsecutiveTracNghiemSharingReferencePassage() throws IOException {
        String passage = "Tom lives in a small town. He goes to school every day by bike.";
        byte[] file = buildExcel(new String[][]{
                {"TRAC_NGHIEM", null, "Where does Tom live?", "A big city", "A small town", "A farm", "An island", "B",
                        null, null, passage, "1", null, null},
                {"TRAC_NGHIEM", null, "How does Tom go to school?", "By bus", "By car", "By bike", "On foot", "C",
                        null, null, passage, "1", null, null},
                {"TRAC_NGHIEM", null, "What is the capital of France?", "London", "Paris", "Berlin", "Madrid", "B",
                        null, null, null, "1", null, null}
        });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "doc-hieu-doc-lap.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.successRows()).isEqualTo(3);

        List<QuestionResponse> saved = questionBankService.listQuestions(bank.id());
        QuestionResponse q1 = findByContentPrefix(saved, "Where does Tom live");
        QuestionResponse q2 = findByContentPrefix(saved, "How does Tom go to school");
        QuestionResponse q3 = findByContentPrefix(saved, "What is the capital of France");

        assertThat(q1.groupKey()).isNotNull();
        assertThat(q1.groupKey()).isEqualTo(q2.groupKey());
        assertThat(q1.referencePassage()).isEqualTo(passage);
        assertThat(q2.referencePassage()).isEqualTo(passage);
        assertThat(q3.groupKey()).isNull();
    }

    /** 1 dòng đơn lẻ có "Đoạn văn tham chiếu" (không dòng nào khác dùng chung) — KHÔNG tự tạo groupKey "nhóm 1 người". */
    @Test
    void importQuestions_boSung_doesNotAutoGroupIsolatedTracNghiemWithReferencePassage() throws IOException {
        byte[] file = buildExcel(new String[][]{
                {"TRAC_NGHIEM_VOICE", null, "Listen and choose the word you hear.", "ship", "sheep", "chip", "cheap", "B",
                        "https://example.com/a.mp3", null, "sheep", "1", null, null}
        });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "voice-doc-lap.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        QuestionResponse saved = questionBankService.listQuestions(bank.id()).get(0);
        assertThat(saved.groupKey()).isNull();
    }

    /**
     * Bổ sung 2026-09-09 (đã xác nhận với người dùng) — nhiều câu Nghe ĐỘC LẬP (mỗi câu tự đáp
     * án/kiểu câu hỏi riêng — TRAC_NGHIEM_VOICE trắc nghiệm + NGHE_DIEN_TU điền từ) nhưng cùng dùng 1
     * file audio — các dòng LIÊN TIẾP cùng "URL Audio" (khớp nguyên văn) tự động gộp chung 1 groupKey
     * để FE phát audio DÙNG CHUNG 1 LẦN thay vì lặp lại cho từng câu, xem computeAutoGroupKeys().
     */
    @Test
    void importQuestions_boSung_autoGroupsConsecutiveListeningQuestionsSharingAudioUrl() throws IOException {
        String audioUrl = "https://example.com/listening/unit1-conversation.mp3";
        byte[] file = buildExcel(new String[][]{
                {"TRAC_NGHIEM_VOICE", null, "What is the woman's job?", "Doctor", "Teacher", "Engineer", "Nurse", "B",
                        audioUrl, null, null, "1", null, null},
                {"NGHE_DIEN_TU", null, "She usually ___ to work by bus.", null, null, null, null, "goes",
                        audioUrl, null, null, "1", null, null},
                {"TRAC_NGHIEM_VOICE", null, "Listen and choose the word you hear.", "ship", "sheep", "chip", "cheap", "B",
                        "https://example.com/other-clip.mp3", null, null, "1", null, null}
        });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "nghe-doc-lap.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.successRows()).isEqualTo(3);

        List<QuestionResponse> saved = questionBankService.listQuestions(bank.id());
        QuestionResponse q1 = findByContentPrefix(saved, "What is the woman's job");
        QuestionResponse q2 = findByContentPrefix(saved, "She usually");
        QuestionResponse q3 = findByContentPrefix(saved, "Listen and choose the word you hear");

        assertThat(q1.groupKey()).isNotNull();
        assertThat(q1.groupKey()).isEqualTo(q2.groupKey());
        assertThat(q1.audioUrl()).isEqualTo(audioUrl);
        assertThat(q2.audioUrl()).isEqualTo(audioUrl);
        assertThat(q2.questionType()).isEqualTo("FILL_IN_BLANK");
        assertThat(q3.groupKey()).isNull();
    }

    /**
     * Bổ sung 2026-09-09 (đã xác nhận với người dùng) — "Nghe chọn hình" (VOICE_PICTURE_CHOICE) mở khóa
     * import Excel qua kind NGHE_CHON_HINH: mỗi đáp án 1 ảnh lấy từ "URL Hình ảnh" phân tách "|", "Đáp
     * án A/B/C" (không điền) tự dùng chữ cái làm nhãn mặc định.
     */
    @Test
    void importQuestions_boSung_createsPictureChoiceQuestionFromPipeSeparatedImages() throws IOException {
        byte[] file = buildExcel(new String[][]{
                {"NGHE_CHON_HINH", null, "What time is it?", null, null, null, null, "B",
                        "https://example.com/listen.mp3",
                        "https://example.com/clock-a.png|https://example.com/clock-b.png|https://example.com/clock-c.png",
                        null, "1", null, null}
        });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "nghe-chon-hinh.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        QuestionResponse saved = questionBankService.listQuestions(bank.id()).get(0);
        assertThat(saved.questionType()).isEqualTo("MULTIPLE_CHOICE");
        assertThat(saved.skill()).isEqualTo("LISTENING");
        assertThat(saved.audioUrl()).isEqualTo("https://example.com/listen.mp3");
        assertThat(saved.choices()).hasSize(3);
        assertThat(saved.choices()).extracting("imageUrl").containsExactly(
                "https://example.com/clock-a.png", "https://example.com/clock-b.png", "https://example.com/clock-c.png");
        // Không điền "Đáp án A/B/C" -> tự dùng chữ cái làm nhãn mặc định (mirror ListeningGroupBuilder.tsx).
        assertThat(saved.choices()).extracting("content").containsExactly("A", "B", "C");
        assertThat(saved.choices()).filteredOn("isCorrect", true).extracting("choiceLabel").containsExactly("B");
    }

    /** Thiếu cột bắt buộc (Nội dung/Content) trong header → không đọc được dòng nào, báo lỗi rõ ngay từ đầu file. */
    @Test
    void importQuestions_boSung_rejectsFileMissingRequiredContentHeader() throws IOException {
        byte[] file = buildExcelWithHeaders(
                new String[]{"Question Type", "Correct Answer"},
                new String[][]{{"TRAC_NGHIEM", "B"}});

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "thieu-cot.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorSummary().get(0).get("reason").toString()).contains("Thiếu cột bắt buộc");
    }

    @Test
    void importQuestions_UC40_A2_oneInvalidExcelRowDoesNotBlockOthers() throws IOException {
        byte[] file = buildExcel(new String[][]{
                {"DIEN_TU", null, "She ___ (go) to school every day.", null, null, null, null, "goes",
                        null, null, null, "1", null, null},
                {"TRAC_NGHIEM", "EASY", "Thiếu đáp án đúng.", "A", "B", "C", "D", null,
                        null, null, null, "1", null, null}
        });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "cau-hoi.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.successRows()).isEqualTo(1);
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errorSummary()).hasSize(1);
        assertThat(questionBankService.listQuestions(bank.id())).hasSize(1);
    }

    @Test
    void importQuestions_UC40_A3_corruptFileMarksJobFailed() {
        byte[] garbage = "khong phai file excel".getBytes();

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "hong.xlsx", "application/vnd.openxmlformats", garbage), teacher.getId());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(questionBankService.listQuestions(bank.id())).isEmpty();
    }

    @Test
    void importQuestions_rejectsUnsupportedFileExtension() {
        assertThatThrownBy(() -> questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "cau-hoi.pdf", "application/pdf", "abc".getBytes()), teacher.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".xlsx");
    }

    @Test
    void importQuestions_UC40_A2_oneInvalidWordBlockDoesNotBlockOthers() throws IOException {
        byte[] file = buildWordDocx(List.of(
                "[DIEN_TU]",
                "Nội dung: She ___ (go) to school every day.",
                "Đáp án đúng: goes",
                "---",
                "[TRAC_NGHIEM]",
                "Nội dung: Thiếu đáp án đúng.",
                "A. A",
                "B. B",
                "C. C",
                "D. D",
                "---"
        ));

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "cau-hoi.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", file), teacher.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.successRows()).isEqualTo(1);
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(questionBankService.listQuestions(bank.id())).hasSize(1);
    }

    /** Word — nhãn "Nhãn: giá trị" chấp nhận tiếng Anh song song tiếng Việt (VD "Content:"/"Correct Answer:"). */
    @Test
    void importQuestions_boSung_acceptsEnglishLabelsInWordBlock() throws IOException {
        byte[] file = buildWordDocx(List.of(
                "[TRAC_NGHIEM]",
                "Content: What is the capital of France?",
                "A. London",
                "B. Paris",
                "C. Berlin",
                "D. Madrid",
                "Correct Answer: B",
                "Difficulty: EASY",
                "---"
        ));

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "cau-hoi-en.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.successRows()).isEqualTo(1);
        QuestionResponse saved = questionBankService.listQuestions(bank.id()).get(0);
        assertThat(saved.difficulty()).isEqualTo("EASY");
        assertThat(saved.choices()).filteredOn(c -> c.content().equals("Paris")).extracting(c -> c.isCorrect()).containsExactly(true);
    }

    /**
     * DIEN_TU_NHOM (bổ sung 2026-08-28, đã xác nhận với người dùng — "Cách B") Main Flow: 1 dòng Excel
     * → N Question FILL_IN_BLANK riêng, cùng groupKey, đúng thứ tự nội dung/đáp án/ảnh (giữ vị trí rỗng
     * khi 1 câu không có ảnh), cùng structuredContent.wordBox tham khảo — mirror
     * FillInBlankGroupBuilder.tsx.
     */
    @Test
    void importQuestions_boSung_fillInBlankGroupCreatesSeparateQuestionsSharingGroupKey() throws IOException {
        byte[] file = buildExcel(new String[][]{
                {"DIEN_TU_NHOM", null,
                        "Tom is very ___.|English is my ___ subject.|Our football ___ helps us win the game.",
                        null, null, null, null, "smart|favourite|coach",
                        null, "https://example.com/1.png||https://example.com/3.png",
                        "activity, smart, favourite, coach, geography", "1", "Nhan xet chung", null}
        });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "nhom.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.successRows()).isEqualTo(1);
        assertThat(result.createdQuestions()).hasSize(3);

        List<QuestionResponse> saved = questionBankService.listQuestions(bank.id());
        assertThat(saved).hasSize(3);
        assertThat(saved).allMatch(q -> q.questionType().equals("FILL_IN_BLANK"));
        assertThat(saved).extracting(QuestionResponse::groupKey).doesNotContainNull().containsOnly(saved.get(0).groupKey());
        assertThat(saved).allMatch(q -> q.explanation().equals("Nhan xet chung"));
        assertThat(saved).allSatisfy(q -> assertThat(q.structuredContent())
                .containsEntry("wordBox", List.of("activity", "smart", "favourite", "coach", "geography")));

        QuestionResponse q1 = findByContentPrefix(saved, "Tom is very");
        assertThat(q1.correctAnswerText()).isEqualTo("smart");
        assertThat(q1.imageUrl()).isEqualTo("https://example.com/1.png");

        QuestionResponse q2 = findByContentPrefix(saved, "English is my");
        assertThat(q2.correctAnswerText()).isEqualTo("favourite");
        assertThat(q2.imageUrl()).isNull(); // vị trí giữa rỗng trong "URL Hình ảnh" ("url1||url3")

        QuestionResponse q3 = findByContentPrefix(saved, "Our football");
        assertThat(q3.correctAnswerText()).isEqualTo("coach");
        assertThat(q3.imageUrl()).isEqualTo("https://example.com/3.png");
    }

    /**
     * Bổ sung 2026-09-09 (đã xác nhận với người dùng) — 1 số bài DIEN_TU_NHOM (VD "tìm và sửa lỗi
     * trong đoạn văn") cần hiện ĐÚNG đoạn văn gốc làm ngữ cảnh, không phải hộp từ vựng tham khảo — cột
     * "Đoạn văn tham chiếu" trông như 1 đoạn văn tự nhiên (dài hoặc có dấu kết câu ./!/?) thì dùng làm
     * referencePassage thật cho MỌI câu trong nhóm, KHÔNG dựng wordBox (khác test phía trên dùng danh
     * sách từ ngắn, không dấu kết câu, vẫn giữ hành vi hộp từ như cũ).
     */
    @Test
    void importQuestions_boSung_fillInBlankGroupUsesRealPassageWhenReferenceLooksLikeNaturalText() throws IOException {
        String passage = "Peter goes to school every day. He like his teacher very much and enjoy the class.";
        byte[] file = buildExcel(new String[][]{
                {"DIEN_TU_NHOM", null,
                        "Peter ___ to school every day.|He ___ his teacher very much.|He ___ the class.",
                        null, null, null, null, "goes|likes|enjoys",
                        null, null, passage, "1", null, null}
        });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "sua-loi-doan-van.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        List<QuestionResponse> saved = questionBankService.listQuestions(bank.id());
        assertThat(saved).hasSize(3);
        assertThat(saved).allMatch(q -> passage.equals(q.referencePassage()));
        assertThat(saved).allMatch(q -> q.structuredContent() == null);
    }

    /** A1: số đáp án không khớp số câu — lỗi rõ ràng, KHÔNG tạo câu nào của nhóm (không dở dang). */
    @Test
    void importQuestions_boSung_fillInBlankGroupRejectsAnswerCountMismatch() throws IOException {
        byte[] file = buildExcel(new String[][]{
                {"DIEN_TU_NHOM", null, "Tom is very ___.|English is my ___ subject.",
                        null, null, null, null, "smart",
                        null, null, null, "1", null, null}
        });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "nhom.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.successRows()).isEqualTo(0);
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errorSummary().get(0).get("reason").toString()).contains("không khớp số câu");
        assertThat(questionBankService.listQuestions(bank.id())).isEmpty();
    }

    /** A2: 2 câu trùng nội dung NGAY TRONG cùng 1 nhóm (VD copy nhầm) — DB pre-check không bắt được ca này, cần check riêng. */
    @Test
    void importQuestions_boSung_fillInBlankGroupRejectsDuplicateSentenceWithinSameGroup() throws IOException {
        byte[] file = buildExcel(new String[][]{
                {"DIEN_TU_NHOM", null, "Tom is very ___.|Tom is very ___.",
                        null, null, null, null, "smart|clever",
                        null, null, null, "1", null, null}
        });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "nhom.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.errorSummary().get(0).get("reason").toString()).contains("trùng nội dung trong CÙNG 1 nhóm");
        assertThat(questionBankService.listQuestions(bank.id())).isEmpty();
    }

    /**
     * A3: 1 câu trong nhóm trùng nội dung với câu ĐÃ CÓ SẴN trong ngân hàng (từ trước) — phải pre-check
     * TRƯỚC KHI tạo bất kỳ câu nào của nhóm, không được để lọt câu đứng TRƯỚC câu trùng (ở đây là "Tom
     * is very ___.") ra DB rồi mới phát hiện lỗi ở câu sau — xác nhận đúng thiết kế "validate trước khi
     * ghi" (không dùng transaction REQUIRES_NEW, xem Javadoc existsActiveDuplicate).
     */
    @Test
    void importQuestions_boSung_fillInBlankGroupPreChecksDuplicateBeforeCreatingAnyQuestionInGroup() throws IOException {
        questionBankService.createQuestion(new CreateQuestionRequest(bank.id(), "FILL_IN_BLANK", null, "MEDIUM",
                "English is my ___ subject.", null, null, null, null, "favourite",
                new BigDecimal("1"), null, null, null, null), teacher.getId());

        byte[] file = buildExcel(new String[][]{
                {"DIEN_TU_NHOM", null,
                        "Tom is very ___.|English is my ___ subject.|Our football ___ helps us win the game.",
                        null, null, null, null, "smart|favourite|coach",
                        null, null, null, "1", null, null}
        });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "nhom.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.successRows()).isEqualTo(0);
        assertThat(result.errorSummary().get(0).get("reason").toString()).contains("đã tồn tại trong ngân hàng câu hỏi");
        // Chỉ còn đúng 1 câu tạo tay ban đầu — "Tom is very ___." (câu đứng TRƯỚC câu trùng trong nhóm)
        // KHÔNG được lọt vào DB dở dang.
        assertThat(questionBankService.listQuestions(bank.id())).hasSize(1);
    }

    /** A4: bỏ trống "Đáp án đúng" — lỗi rõ ràng thay vì NPE/lỗi khó hiểu. */
    @Test
    void importQuestions_boSung_fillInBlankGroupRejectsMissingCorrectAnswerColumn() throws IOException {
        byte[] file = buildExcel(new String[][]{
                {"DIEN_TU_NHOM", null, "Tom is very ___.|English is my ___ subject.",
                        null, null, null, null, null,
                        null, null, null, "1", null, null}
        });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "nhom.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.errorSummary().get(0).get("reason").toString()).contains("Đáp án đúng");
        assertThat(questionBankService.listQuestions(bank.id())).isEmpty();
    }

    /**
     * DOC_HIEU_LUOI (bổ sung 2026-09-08, đã xác nhận với người dùng — mirror GridQuestionBuilder.tsx)
     * Main Flow: 1 dòng Excel → N Question MULTIPLE_CHOICE riêng, dùng CHUNG referencePassage + CHUNG 1
     * bộ đáp án A-D, mỗi câu chỉ khác nhau ở isCorrect của bộ đáp án đó theo đúng "Đáp án đúng".
     */
    @Test
    void importQuestions_boSung_gridGroupCreatesSeparateQuestionsSharingPassageAndChoices() throws IOException {
        byte[] file = buildExcel(new String[][]{
                {"DOC_HIEU_LUOI", null,
                        "Who loves museums?|Who thinks their hobby is strange?|Who talks about a sport?",
                        "Tom", "Max", "Anna", null, "B|A|C",
                        null, null, "Tom: ...\n\nMax: ...\n\nAnna: ...", "1", "Doc hieu Unit 1", null}
        });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "luoi.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.successRows()).isEqualTo(1);
        assertThat(result.createdQuestions()).hasSize(3);

        List<QuestionResponse> saved = questionBankService.listQuestions(bank.id());
        assertThat(saved).hasSize(3);
        assertThat(saved).allMatch(q -> q.questionType().equals("MULTIPLE_CHOICE"));
        assertThat(saved).allMatch(q -> q.skill().equals("READING"));
        assertThat(saved).allMatch(q -> q.referencePassage().equals("Tom: ...\n\nMax: ...\n\nAnna: ..."));
        assertThat(saved).extracting(QuestionResponse::groupKey).doesNotContainNull().containsOnly(saved.get(0).groupKey());
        assertThat(saved).allMatch(q -> q.explanation().equals("Doc hieu Unit 1"));
        assertThat(saved).allSatisfy(q -> assertThat(q.choices()).extracting("content").containsExactly("Tom", "Max", "Anna"));

        QuestionResponse q1 = findByContentPrefix(saved, "Who loves museums");
        assertThat(q1.choices()).filteredOn(c -> c.isCorrect()).extracting("content").containsExactly("Max");

        QuestionResponse q2 = findByContentPrefix(saved, "Who thinks their hobby");
        assertThat(q2.choices()).filteredOn(c -> c.isCorrect()).extracting("content").containsExactly("Tom");

        QuestionResponse q3 = findByContentPrefix(saved, "Who talks about a sport");
        assertThat(q3.choices()).filteredOn(c -> c.isCorrect()).extracting("content").containsExactly("Anna");
    }

    /** A1: thiếu "Đoạn văn tham chiếu" — Đọc hiểu lưới bắt buộc phải có đoạn văn dùng chung. */
    @Test
    void importQuestions_boSung_gridGroupRejectsMissingReferencePassage() throws IOException {
        byte[] file = buildExcel(new String[][]{
                {"DOC_HIEU_LUOI", null, "Who loves museums?|Who thinks their hobby is strange?",
                        "Tom", "Max", "Anna", null, "B|A",
                        null, null, null, "1", null, null}
        });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "luoi.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.errorSummary().get(0).get("reason").toString()).contains("Đoạn văn tham chiếu");
        assertThat(questionBankService.listQuestions(bank.id())).isEmpty();
    }

    /** A2: số đáp án đúng không khớp số câu hỏi. */
    @Test
    void importQuestions_boSung_gridGroupRejectsAnswerCountMismatch() throws IOException {
        byte[] file = buildExcel(new String[][]{
                {"DOC_HIEU_LUOI", null, "Who loves museums?|Who thinks their hobby is strange?",
                        "Tom", "Max", "Anna", null, "B",
                        null, null, "Doan van", "1", null, null}
        });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "luoi.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.errorSummary().get(0).get("reason").toString()).contains("không khớp số câu hỏi");
        assertThat(questionBankService.listQuestions(bank.id())).isEmpty();
    }

    /**
     * DOC_DIEN_TU (bổ sung 2026-09-08, đã xác nhận với người dùng — mirror ClozeQuestionBuilder.tsx)
     * Main Flow: 1 dòng Excel → N Question MULTIPLE_CHOICE riêng, dùng CHUNG referencePassage nhưng MỖI
     * câu có bộ đáp án RIÊNG lấy theo vị trí tương ứng ở mỗi cột Đáp án A/B/C — khác Grid ở trên.
     */
    @Test
    void importQuestions_boSung_clozeGroupCreatesSeparateQuestionsWithPerBlankChoices() throws IOException {
        byte[] file = buildExcel(new String[][]{
                {"DOC_DIEN_TU", null, null,
                        "environment|timetable|hours", "equipment|subject|lessons", "job|homework|subjects", null,
                        "B|A|C", null, null, "The school has an excellent (1)___...", "1", null, null}
        });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "cloze.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.createdQuestions()).hasSize(3);

        List<QuestionResponse> saved = questionBankService.listQuestions(bank.id());
        assertThat(saved).hasSize(3);
        assertThat(saved).allMatch(q -> q.questionType().equals("MULTIPLE_CHOICE"));
        assertThat(saved).allMatch(q -> q.skill().equals("READING"));
        assertThat(saved).allMatch(q -> q.referencePassage().equals("The school has an excellent (1)___..."));
        assertThat(saved).extracting(QuestionResponse::groupKey).doesNotContainNull().containsOnly(saved.get(0).groupKey());
        // "Nội dung" để trống -> tự đánh số nhãn từng chỗ trống.
        assertThat(saved).extracting(QuestionResponse::content).containsExactlyInAnyOrder("Chỗ trống 1", "Chỗ trống 2", "Chỗ trống 3");

        QuestionResponse blank1 = findByContentPrefix(saved, "Chỗ trống 1");
        assertThat(blank1.choices()).extracting("content").containsExactly("environment", "equipment", "job");
        assertThat(blank1.choices()).filteredOn(c -> c.isCorrect()).extracting("content").containsExactly("equipment");

        QuestionResponse blank2 = findByContentPrefix(saved, "Chỗ trống 2");
        assertThat(blank2.choices()).filteredOn(c -> c.isCorrect()).extracting("content").containsExactly("timetable");

        QuestionResponse blank3 = findByContentPrefix(saved, "Chỗ trống 3");
        assertThat(blank3.choices()).filteredOn(c -> c.isCorrect()).extracting("content").containsExactly("subjects");
    }

    /** A1: số phương án B không khớp số chỗ trống suy ra từ cột A — lỗi rõ ràng. */
    @Test
    void importQuestions_boSung_clozeGroupRejectsOptionColumnCountMismatch() throws IOException {
        byte[] file = buildExcel(new String[][]{
                {"DOC_DIEN_TU", null, null,
                        "environment|timetable|hours", "equipment|subject", null, null,
                        "B|A|C", null, null, "Doan van co danh so", "1", null, null}
        });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "cloze.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.errorSummary().get(0).get("reason").toString()).contains("không khớp số chỗ trống");
        assertThat(questionBankService.listQuestions(bank.id())).isEmpty();
    }

    /**
     * "Loại câu hỏi mặc định" (bổ sung 2026-08-28, đã xác nhận với người dùng — khớp thói quen "1 Ex
     * chỉ 1 loại câu hỏi" nên cả file thường cùng 1 giá trị, không muốn gõ lại mỗi dòng) Main Flow:
     * cột "Loại câu hỏi" để TRỐNG ở mọi dòng, dùng defaultKind cho toàn bộ.
     */
    @Test
    void importQuestions_boSung_appliesDefaultKindWhenRowKindColumnIsBlank() throws IOException {
        byte[] file = buildExcel(new String[][]{
                {null, "EASY", "What is the capital of France?", "London", "Paris", "Berlin", "Madrid", "B",
                        null, null, null, "1", null, null},
                {null, "MEDIUM", "What is the capital of Germany?", "Paris", "Berlin", "Rome", "Madrid", "B",
                        null, null, null, "1", null, null}
        });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "cau-hoi.xlsx", "application/vnd.openxmlformats", file), teacher.getId(),
                "TRAC_NGHIEM");

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.successRows()).isEqualTo(2);
        List<QuestionResponse> saved = questionBankService.listQuestions(bank.id());
        assertThat(saved).hasSize(2);
        assertThat(saved).allMatch(q -> q.questionType().equals("MULTIPLE_CHOICE"));
    }

    /** Dòng nào TỰ GHI "Loại câu hỏi" riêng vẫn ưu tiên giá trị đó, không bị defaultKind ghi đè. */
    @Test
    void importQuestions_boSung_explicitRowKindOverridesDefaultKind() throws IOException {
        byte[] file = buildExcel(new String[][]{
                {"DIEN_TU", null, "She ___ (go) to school every day.", null, null, null, null, "goes",
                        null, null, null, "1", null, null}
        });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "cau-hoi.xlsx", "application/vnd.openxmlformats", file), teacher.getId(),
                "TRAC_NGHIEM");

        assertThat(result.status()).isEqualTo("COMPLETED");
        QuestionResponse saved = questionBankService.listQuestions(bank.id()).get(0);
        assertThat(saved.questionType()).isEqualTo("FILL_IN_BLANK");
    }

    /** Cột "Loại câu hỏi" trống VÀ chưa chọn loại mặc định — lỗi rõ ràng, không phải NPE. */
    @Test
    void importQuestions_boSung_rejectsBlankRowKindWithoutDefaultKind() throws IOException {
        byte[] file = buildExcel(new String[][]{
                {null, null, "What is the capital of France?", "London", "Paris", "Berlin", "Madrid", "B",
                        null, null, null, "1", null, null}
        });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "cau-hoi.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.errorSummary().get(0).get("reason").toString()).contains("Thiếu loại câu hỏi");
        assertThat(questionBankService.listQuestions(bank.id())).isEmpty();
    }

    /**
     * Round-trip: file mẫu Word tự sinh (buildWordTemplate) phải tự đọc lại
     * được đúng cả 15 loại trong VALID_KINDS — bảo vệ khỏi mẫu và parser
     * lệch cú pháp nhau (giống buildTemplate_roundTrip của
     * GradeImportServiceTest cho UC-53). Số lượng 15 khớp đúng
     * VALID_KINDS/TEMPLATE_BLOCKS sau khi bổ sung NGHE_CHON_HINH ngày
     * 2026-09-09 (trước đó 14 loại kể từ đợt bổ sung DOC_HIEU_LUOI/DOC_DIEN_TU
     * 2026-09-08, xem Javadoc lớp QuestionImportService) — successRows đếm
     * THEO DÒNG (15) nhưng DIEN_TU_NHOM/DOC_HIEU_LUOI/DOC_DIEN_TU mỗi loại
     * tạo ra 3 Question/1 dòng nên tổng câu hỏi thật sự tạo ra là 12 + 3 + 3
     * + 3 = 21, tên method giữ nguyên hậu tố "boSung" theo đúng đợt bổ sung.
     */
    @Test
    void buildWordTemplate_boSung_roundTripsThroughImportAndCreatesAllFifteenKinds() {
        byte[] template = questionImportService.buildWordTemplate();

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "mau.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", template), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.totalRows()).isEqualTo(15);
        assertThat(result.successRows()).isEqualTo(15);
        assertThat(result.failedRows()).isEqualTo(0);
        assertThat(questionBankService.listQuestions(bank.id())).hasSize(21);
    }

    /**
     * Bổ sung 2026-09-09 (đã xác nhận với người dùng) — hồi quy cho 1 bug thật phát hiện qua file Excel
     * người dùng thực tế tải về: {@code excelHeaders} phía FE (lms-question-authoring.json) từng ghép
     * NHIỀU cụm vào 1 header duy nhất (VD "Đoạn văn tham chiếu/Transcript/Từ khóa phát âm/Hộp từ vựng")
     * để hiển thị gợi ý cho GV — nhưng {@code QuestionImportFieldAliases#resolveField} so khớp CHÍNH
     * XÁC TOÀN BỘ chuỗi header đã chuẩn hoá, không phải theo từng cụm/substring, nên header ghép dài
     * KHÔNG khớp bất kỳ alias nào — referencePassage luôn null, âm thầm mất dữ liệu (không lỗi rõ ràng
     * với TRAC_NGHIEM_VOICE vì trường này optional, nhưng DOC_HIEU_LUOI/DOC_DIEN_TU bắt buộc nên sẽ báo
     * lỗi "cần Đoạn văn tham chiếu"). Các test DOC_HIEU_LUOI/DOC_DIEN_TU khác trong file này dùng
     * {@code buildExcel()} với header cố định "Transcript/Từ khóa" (khớp alias) nên KHÔNG bắt được bug
     * này — test này dùng ĐÚNG header thật đang hiển thị cho giáo viên (khớp lms-question-authoring.json
     * sau khi sửa) để đảm bảo không tái diễn.
     */
    @Test
    void importQuestions_boSung_realExcelTemplateHeaderResolvesReferencePassageColumn() throws IOException {
        byte[] file = buildExcelWithHeaders(
                new String[]{"Loại câu hỏi", "Độ khó", "Nội dung", "Đáp án A", "Đáp án B", "Đáp án C", "Đáp án D",
                        "Đáp án đúng", "URL Audio", "URL Hình ảnh", "Đoạn văn tham chiếu", "Điểm", "Giải thích", "Tags"},
                new String[][]{
                        {"DOC_HIEU_LUOI", null, "Who loves museums?|Who plays football?", "Tom", "Max", null, null,
                                "A|B", null, null, "Tom: loves museums.\n\nMax: plays football.", "1", null, null}
                });

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "mau-that.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.successRows()).isEqualTo(1);
        List<QuestionResponse> saved = questionBankService.listQuestions(bank.id());
        assertThat(saved).hasSize(2);
        assertThat(saved).allMatch(q -> q.referencePassage().equals("Tom: loves museums.\n\nMax: plays football."));
    }

    private QuestionResponse findByContentPrefix(List<QuestionResponse> questions, String prefix) {
        return questions.stream().filter(q -> q.content().startsWith(prefix)).findFirst()
                .orElseThrow(() -> new AssertionError("Không tìm thấy câu hỏi bắt đầu bằng: " + prefix));
    }

    private byte[] buildExcel(String[][] rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("CauHoi");
            String[] headers = {"Loại câu hỏi", "Độ khó", "Nội dung", "Đáp án A", "Đáp án B", "Đáp án C", "Đáp án D",
                    "Đáp án đúng", "URL Audio", "URL Hình ảnh", "Transcript/Từ khóa", "Điểm", "Giải thích", "Tags"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    if (rows[r][c] != null) {
                        row.createCell(c).setCellValue(rows[r][c]);
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /** Kho đề — dựng file Excel với header TÙY CHỌN (tên + thứ tự bất kỳ) để test nhận diện theo tên thay vì vị trí cố định. */
    private byte[] buildExcelWithHeaders(String[] headers, String[][] rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("CauHoi");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    if (rows[r][c] != null) {
                        row.createCell(c).setCellValue(rows[r][c]);
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildWordDocx(List<String> lines) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            for (String line : lines) {
                document.createParagraph().createRun().setText(line);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }

    private String curriculumCode() {
        return "CUR-QI-" + SEQ.incrementAndGet();
    }

    private String bankCode() {
        return "QB-QI-" + SEQ.incrementAndGet();
    }

    private void assignRole(User user, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(user);
        userRoleRepository.save(userRole);
    }

    private Site newSite() {
        Site s = new Site();
        s.setCode("SITE-QI-" + SEQ.incrementAndGet());
        s.setName("Test Site");
        s.setSiteType(Site.SiteType.OWNED);
        return siteRepository.save(s);
    }

    private User newUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + System.nanoTime());
        user.setEmail(prefix + "." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }
}
