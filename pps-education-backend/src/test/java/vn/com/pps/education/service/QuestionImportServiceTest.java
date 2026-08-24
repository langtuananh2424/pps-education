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
                        null, null, null, "1", "Paris la thu do nuoc Phap.", "geo,easy"},
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
     * Round-trip: file mẫu Word tự sinh (buildWordTemplate) phải tự đọc lại
     * được đúng cả 5 loại — bảo vệ khỏi mẫu và parser lệch cú pháp nhau
     * (giống buildTemplate_roundTrip của GradeImportServiceTest cho UC-53).
     */
    @Test
    void buildWordTemplate_boSung_roundTripsThroughImportAndCreatesAllFiveKinds() {
        byte[] template = questionImportService.buildWordTemplate();

        QuestionImportResponse result = questionImportService.importQuestions(bank.id(),
                new MockMultipartFile("file", "mau.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", template), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.successRows()).isEqualTo(5);
        assertThat(result.failedRows()).isEqualTo(0);
        assertThat(questionBankService.listQuestions(bank.id())).hasSize(5);
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
