package vn.com.pps.education.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.BookCatalogImportResponse;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.repository.BookRepository;
import vn.com.pps.education.repository.CurriculumSubTopicRepository;
import vn.com.pps.education.repository.CurriculumUnitRepository;
import vn.com.pps.education.repository.ExamRepository;
import vn.com.pps.education.repository.ExerciseRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC-72: Import Excel nhanh mục lục Sách/Unit/Sub Topic/Lesson (exams)/Bài
 * (exercises) của Kho đề — Main Flow, A1 (thiếu Mã/Tên exercise), A2
 * (chưa có gì để forward-fill), A3 (file hỏng). Xem
 * docs/uc/phan-he-07-lms-portal.md.
 */
@Transactional
class BookCatalogImportServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private BookCatalogImportService bookCatalogImportService;

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private CurriculumUnitRepository curriculumUnitRepository;

    @Autowired
    private CurriculumSubTopicRepository curriculumSubTopicRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    private User teacher;
    private CurriculumResponse curriculum;

    @BeforeEach
    void setUp() {
        User headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");

        CurriculumResponse raw = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null, null, null), headAcademic.getId());
        curriculum = curriculumService.update(raw.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), headAcademic.getId());
    }

    @Test
    void importCatalog_UC72_MainFlow_createsFullHierarchyAndForwardFillsMergedCells() throws IOException {
        // Mirror đúng dữ liệu mẫu người dùng cung cấp: 4 cột đầu (+ cột Loại giáo viên) để trống ở các
        // dòng lặp lại trong cùng 1 Lesson.
        byte[] file = buildWorkbook(new String[][]{
                {"Grade 6 Standard Chaper 1", "UNIT 1: MY NEW SCHOOL", "SUB TOPIC 1: SCHOOL ACTIVITIES", "G6-U1-SUB1-L1", "VIETNAMESE", "G6-U1-SUB1-L1-EX1", "Ex. 1: Choose the correct word."},
                {"", "", "", "", "", "G6-U1-SUB1-L1-EX2", "Ex. 2: Underline the correct word."},
                {"", "", "SUB TOPIC 2: SCHOOL OBJECTS", "G6-U1-SUB2-L2", "FOREIGN", "G6-U1-SUB2-L2-EX1", "Ex. 1: Odd one out."},
        });

        BookCatalogImportResponse result = bookCatalogImportService.importCatalog(
                curriculum.id(), new MockMultipartFile("file", "danh_muc.xlsx", "application/vnd.openxmlformats", file),
                "VIETNAMESE", "HOMEWORK", "SELF_PRACTICE", new BigDecimal("10"), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.totalRows()).isEqualTo(3);
        assertThat(result.successRows()).isEqualTo(3);

        var book = bookRepository.findByCurriculumIdAndTitle(curriculum.id(), "Grade 6 Standard Chaper 1").orElseThrow();
        var unit = curriculumUnitRepository.findByBookIdAndTitle(book.getId(), "UNIT 1: MY NEW SCHOOL").orElseThrow();
        assertThat(curriculumUnitRepository.findByBookIdOrderByDisplayOrder(book.getId())).hasSize(1);
        assertThat(curriculumSubTopicRepository.findByUnitIdOrderByDisplayOrder(unit.getId())).hasSize(2);

        var lesson1 = examRepository.findByCode("G6-U1-SUB1-L1").orElseThrow();
        assertThat(lesson1.getCurriculum().getId()).isEqualTo(curriculum.id());
        assertThat(lesson1.getTeacherType().name()).isEqualTo("VIETNAMESE");
        assertThat(lesson1.getExamType().name()).isEqualTo("HOMEWORK");
        assertThat(exerciseRepository.findByExamId(lesson1.getId())).hasSize(2);

        var lesson2 = examRepository.findByCode("G6-U1-SUB2-L2").orElseThrow();
        assertThat(lesson2.getSubTopic().getTitle()).isEqualTo("SUB TOPIC 2: SCHOOL OBJECTS");
        assertThat(exerciseRepository.findByCode("G6-U1-SUB2-L2-EX1")).isPresent();
    }

    /**
     * Bổ sung 2026-09-13 (đã xác nhận với người dùng) — thực tế 1 Sách thường xen kẽ Lesson lẻ do
     * GVVN dạy/Lesson chẵn do GVNN dạy NGAY TRONG CÙNG 1 file, không thể áp 1 teacherType chung như
     * thiết kế ban đầu. Cột E (Loại giáo viên) cho phép khai riêng từng Lesson, forward-fill trong
     * phạm vi CHÍNH Lesson đó — Lesson kế tiếp không kế thừa nếu tự khai giá trị khác.
     */
    @Test
    void importCatalog_boSung_appliesDifferentTeacherTypePerLessonFromColumnE() throws IOException {
        byte[] file = buildWorkbook(new String[][]{
                {"Sách A", "Unit 1", "Sub Topic 1", "L1", "VIETNAMESE", "L1-EX1", "Bài ngữ pháp"},
                {"", "", "", "L2", "FOREIGN", "L2-EX1", "Bài nghe"},
                {"", "", "", "L2", "", "L2-EX2", "Bài nói (kế thừa FOREIGN của L2)"},
                {"", "", "", "L3", "", "L3-EX1", "Bài dùng lại mặc định VIETNAMESE (cột E để trống hoàn toàn cho Lesson này)"},
        });

        bookCatalogImportService.importCatalog(curriculum.id(),
                new MockMultipartFile("file", "xen_ke.xlsx", "application/vnd.openxmlformats", file),
                "VIETNAMESE", "HOMEWORK", "SELF_PRACTICE", new BigDecimal("10"), teacher.getId());

        assertThat(examRepository.findByCode("L1").orElseThrow().getTeacherType().name()).isEqualTo("VIETNAMESE");
        assertThat(examRepository.findByCode("L2").orElseThrow().getTeacherType().name()).isEqualTo("FOREIGN");
        assertThat(examRepository.findByCode("L3").orElseThrow().getTeacherType().name()).isEqualTo("VIETNAMESE");
    }

    @Test
    void importCatalog_boSung_A_rejectsInvalidTeacherTypeTokenWithoutCorruptingForwardFill() throws IOException {
        byte[] file = buildWorkbook(new String[][]{
                {"Sách A", "Unit 1", "Sub Topic 1", "L1", "VN", "L1-EX1", "Token sai, chỉ dòng này lỗi"},
                {"", "", "", "", "", "L1-EX2", "Dòng này vẫn dùng lại VIETNAMESE đã khai hợp lệ trước đó ở tham số mặc định"},
        });

        BookCatalogImportResponse result = bookCatalogImportService.importCatalog(curriculum.id(),
                new MockMultipartFile("file", "sai_teacher_type.xlsx", "application/vnd.openxmlformats", file),
                "VIETNAMESE", "HOMEWORK", "SELF_PRACTICE", new BigDecimal("10"), teacher.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errorSummary().get(0).get("reason")).isEqualTo(
                "Loại giáo viên không hợp lệ: 'VN' — chỉ chấp nhận VIETNAMESE/FOREIGN, hoặc để trống để dùng lại giá trị của Lesson này/giá trị mặc định.");
        assertThat(examRepository.findByCode("L1").orElseThrow().getTeacherType().name()).isEqualTo("VIETNAMESE");
    }

    @Test
    void importCatalog_UC72_Postcondition_reimportingSameFileReusesExistingRecordsWithoutOverwriting() throws IOException {
        byte[] file = buildWorkbook(new String[][]{
                {"Sách A", "Unit 1", "Sub Topic 1", "L1", "", "L1-EX1", "Bài 1"},
                {"", "", "", "", "", "L1-EX2", "Bài 2"},
        });

        bookCatalogImportService.importCatalog(curriculum.id(),
                new MockMultipartFile("file", "lan1.xlsx", "application/vnd.openxmlformats", file),
                "VIETNAMESE", "HOMEWORK", "SELF_PRACTICE", new BigDecimal("10"), teacher.getId());
        var book = bookRepository.findByCurriculumIdAndTitle(curriculum.id(), "Sách A").orElseThrow();
        var lessonBefore = examRepository.findByCode("L1").orElseThrow();

        // Import lại CÙNG file với mặc định teacherType/examType/exerciseType/totalPoints KHÁC hẳn —
        // Đề/Bài đã tồn tại theo code phải giữ nguyên giá trị cũ, không bị ghi đè.
        BookCatalogImportResponse second = bookCatalogImportService.importCatalog(curriculum.id(),
                new MockMultipartFile("file", "lan2.xlsx", "application/vnd.openxmlformats", file),
                "FOREIGN", "REVIEW", "ASSIGNED", new BigDecimal("20"), teacher.getId());

        assertThat(second.status()).isEqualTo("COMPLETED");
        assertThat(second.successRows()).isEqualTo(2);
        assertThat(curriculumUnitRepository.findByBookIdOrderByDisplayOrder(book.getId())).hasSize(1);
        var lessonAfter = examRepository.findByCode("L1").orElseThrow();
        assertThat(lessonAfter.getId()).isEqualTo(lessonBefore.getId());
        assertThat(lessonAfter.getTeacherType().name()).isEqualTo("VIETNAMESE");
        assertThat(lessonAfter.getExamType().name()).isEqualTo("HOMEWORK");
        assertThat(exerciseRepository.findByExamId(lessonAfter.getId())).hasSize(2);
        assertThat(exerciseRepository.findByCode("L1-EX1").orElseThrow().getTotalPoints())
                .isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    void importCatalog_UC72_A1_reportsRowErrorForMissingExerciseCodeWithoutBlockingOtherRows() throws IOException {
        byte[] file = buildWorkbook(new String[][]{
                {"Sách A", "Unit 1", "Sub Topic 1", "L1", "", "", "Thiếu mã exercise"},
                {"", "", "", "", "", "L1-EX2", "Bài hợp lệ"},
        });

        BookCatalogImportResponse result = bookCatalogImportService.importCatalog(curriculum.id(),
                new MockMultipartFile("file", "a1.xlsx", "application/vnd.openxmlformats", file),
                "VIETNAMESE", "HOMEWORK", "SELF_PRACTICE", new BigDecimal("10"), teacher.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.successRows()).isEqualTo(1);
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errorSummary().get(0).get("reason")).isEqualTo("Thiếu Mã exercise.");
        assertThat(exerciseRepository.findByCode("L1-EX2")).isPresent();
    }

    @Test
    void importCatalog_UC72_A2_reportsRowErrorWhenFirstRowHasNothingToForwardFill() throws IOException {
        byte[] file = buildWorkbook(new String[][]{
                {"", "", "", "", "", "L1-EX1", "Không có dòng trước để dùng lại"},
        });

        BookCatalogImportResponse result = bookCatalogImportService.importCatalog(curriculum.id(),
                new MockMultipartFile("file", "a2.xlsx", "application/vnd.openxmlformats", file),
                "VIETNAMESE", "HOMEWORK", "SELF_PRACTICE", new BigDecimal("10"), teacher.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errorSummary().get(0).get("reason")).isEqualTo("Thiếu Tên sách và chưa có dòng trước đó để dùng lại.");
    }

    @Test
    void importCatalog_UC72_A3_marksFailedForCorruptFile() {
        byte[] garbage = "not an excel file".getBytes();

        BookCatalogImportResponse result = bookCatalogImportService.importCatalog(curriculum.id(),
                new MockMultipartFile("file", "broken.xlsx", "application/vnd.openxmlformats", garbage),
                "VIETNAMESE", "HOMEWORK", "SELF_PRACTICE", new BigDecimal("10"), teacher.getId());

        assertThat(result.status()).isEqualTo("FAILED");
    }

    private byte[] buildWorkbook(String[][] rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("DanhMuc");
            Row header = sheet.createRow(0);
            String[] headers = {"Tên sách", "Tên Unit", "Tên Sub Topic", "Mã Lesson", "Loại giáo viên", "Mã exercise", "Tên exercise"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            var out = new java.io.ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private String curriculumCode() {
        return "CUR-" + SEQ.incrementAndGet();
    }

    private void assignRole(User user, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(user);
        userRoleRepository.save(userRole);
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
