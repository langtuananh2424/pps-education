package vn.com.pps.education.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AcademicTerm;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateGradeComponentSetupRequest;
import vn.com.pps.education.dto.CreateGradeEvaluationComponentRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.GradeComponentSetupResponse;
import vn.com.pps.education.dto.GradeEntryResponse;
import vn.com.pps.education.dto.GradeEvaluationComponentResponse;
import vn.com.pps.education.dto.GradeEvaluationResultResponse;
import vn.com.pps.education.dto.GradeImportResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.exception.GradeImportColumnMismatchException;
import vn.com.pps.education.repository.AcademicTermRepository;
import vn.com.pps.education.repository.ImportJobRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-53: Nhập điểm thi qua Excel — Main Flow (map header theo tên, ghi
 * điểm + Overall/Level/Nhận xét/Ghi chú ở DRAFT), A1 (cột không khớp,
 * dừng toàn bộ), A2 (lỗi 1 dòng không chặn dòng khác), A3 (file sai định
 * dạng). V95 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): gắn
 * theo GradeComponentSetup (lớp + kỳ học + Giữa/Cuối kỳ) thay gradePeriodId
 * theo curriculum; roster tính theo rosterAsOfDate thay vì chỉ lọc
 * status=ACTIVE. Xem docs/uc/phan-he-06-hoc-thuat.md.
 */
@Transactional
class GradeImportServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private GradeImportService gradeImportService;

    @Autowired
    private GradeService gradeService;

    @Autowired
    private ClassService classService;

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

    @Autowired
    private AcademicTermRepository academicTermRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ImportJobRepository importJobRepository;

    private User headAcademic;
    private User teacher;
    private ClassResponse schoolClass;
    private GradeComponentSetupResponse gradeSetup;
    private GradeEvaluationComponentResponse speaking;
    private GradeEvaluationComponentResponse writing;
    private Student student1;
    private Student student2;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null), headAcademic.getId());
        CurriculumResponse activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), headAcademic.getId());

        Site site = newSite();
        AcademicTerm academicTerm = newAcademicTerm(site);
        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());

        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now()), headAcademic.getId());

        gradeSetup = gradeService.createGradeComponentSetup(schoolClass.id(),
                new CreateGradeComponentSetupRequest(academicTerm.getId(), "MID_TERM", "POINT_10", LocalDate.now(), false),
                headAcademic.getId());
        speaking = gradeService.addGradeEvaluationComponent(gradeSetup.id(),
                new CreateGradeEvaluationComponentRequest(null, null, "SPEAKING", "Nói", new BigDecimal("10.00"), null, null, 1),
                headAcademic.getId());
        writing = gradeService.addGradeEvaluationComponent(gradeSetup.id(),
                new CreateGradeEvaluationComponentRequest(null, null, "WRITING", "Viết", new BigDecimal("10.00"), null, null, 2),
                headAcademic.getId());

        student1 = newStudent();
        student2 = newStudent();
    }

    @Test
    void importGrades_UC53_MainFlow_mapsHeaderAndSavesDraftEntriesAndOverall() throws IOException {
        byte[] file = buildWorkbook(
                new String[]{"Ma hoc vien", "Nói", "Viết", "Tổng điểm", "Cấp độ", "Nhận xét"},
                new String[][]{
                        {student1.getStudentCode(), "8.5", "7.0", "7.6", "B2", "Tiến bộ"},
                        {student2.getStudentCode(), "6.0", "6.5", "6.2", "B1", ""},
                });

        GradeImportResponse result = gradeImportService.importGrades(schoolClass.id(), gradeSetup.id(),
                new MockMultipartFile("file", "diem.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.successRows()).isEqualTo(2);
        assertThat(result.failedRows()).isEqualTo(0);

        var speakingEntries = gradeService.listEntries(schoolClass.id(), speaking.id());
        assertThat(speakingEntries).extracting(GradeEntryResponse::score)
                .anySatisfy(s -> assertThat(s).isEqualByComparingTo("8.5"));
        assertThat(speakingEntries).allSatisfy(e -> assertThat(e.status()).isEqualTo("DRAFT"));

        var results = gradeService.listEvaluationResults(schoolClass.id(), gradeSetup.id());
        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(r -> {
            assertThat(r.status()).isEqualTo("DRAFT");
            assertThat(r.source()).isEqualTo("EXCEL_IMPORT");
        });
        GradeEvaluationResultResponse r1 = results.stream().filter(r -> r.studentId().equals(student1.getId())).findFirst().orElseThrow();
        assertThat(r1.overallScore()).isEqualByComparingTo("7.6");
        assertThat(r1.level()).isEqualTo("B2");
        assertThat(r1.comment()).isEqualTo("Tiến bộ");
    }

    @Test
    void importGrades_UC53_A1_columnMismatchStopsWholeImportAndCreatesNoJob() throws IOException {
        long jobCountBefore = importJobRepository.count();
        byte[] file = buildWorkbook(
                new String[]{"Ma hoc vien", "Nói", "Viết", "Vocabulary"},
                new String[][]{
                        {student1.getStudentCode(), "8.5", "7.0", "9.0"},
                });

        assertThatThrownBy(() -> gradeImportService.importGrades(schoolClass.id(), gradeSetup.id(),
                new MockMultipartFile("file", "diem.xlsx", "application/vnd.openxmlformats", file), teacher.getId()))
                .isInstanceOf(GradeImportColumnMismatchException.class)
                .hasMessageContaining("Vocabulary");

        assertThat(importJobRepository.count()).isEqualTo(jobCountBefore);
        assertThat(gradeService.listEntries(schoolClass.id(), speaking.id())).isEmpty();
    }

    @Test
    void importGrades_UC53_A2_oneInvalidRowDoesNotBlockOthers() throws IOException {
        byte[] file = buildWorkbook(
                new String[]{"Ma hoc vien", "Nói", "Viết"},
                new String[][]{
                        {"MA-KHONG-TON-TAI", "8.0", "7.0"},
                        {student2.getStudentCode(), "6.0", "6.5"},
                });

        GradeImportResponse result = gradeImportService.importGrades(schoolClass.id(), gradeSetup.id(),
                new MockMultipartFile("file", "diem.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.successRows()).isEqualTo(1);
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errorSummary()).hasSize(1);
        assertThat(gradeService.listEntries(schoolClass.id(), speaking.id()))
                .extracting(GradeEntryResponse::studentId).containsExactly(student2.getId());
    }

    /**
     * Bổ sung ngoài SDD gốc: GV tự định dạng ô điểm trong Excel thành "Phần trăm" (setup dùng
     * thang PERCENT) — Excel/DataFormatter trả về chuỗi có dấu "%" (VD "80,00%", dấu phẩy thập
     * phân kiểu VN) thay vì số thuần "80.00", parseScore phải bỏ dấu "%" rồi mới parse, không
     * được coi là điểm không hợp lệ (đã từng bị lỗi thật trên môi trường GV dùng).
     */
    @Test
    void importGrades_UC53_A4_acceptsPercentFormattedScoreCell() throws IOException {
        GradeComponentSetupResponse percentSetup = gradeService.createGradeComponentSetup(schoolClass.id(),
                new CreateGradeComponentSetupRequest(gradeSetup.academicTermId(), "END_TERM", "PERCENT", LocalDate.now(), false),
                headAcademic.getId());
        GradeEvaluationComponentResponse grammar = gradeService.addGradeEvaluationComponent(percentSetup.id(),
                new CreateGradeEvaluationComponentRequest(null, null, "GRAMMAR", "Ngữ pháp", new BigDecimal("100.00"), null, null, 1),
                headAcademic.getId());

        byte[] file = buildWorkbook(
                new String[]{"Ma hoc vien", "Ngữ pháp"},
                new String[][]{
                        {student1.getStudentCode(), "80,00%"},
                });

        GradeImportResponse result = gradeImportService.importGrades(schoolClass.id(), percentSetup.id(),
                new MockMultipartFile("file", "diem.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.successRows()).isEqualTo(1);
        assertThat(gradeService.listEntries(schoolClass.id(), grammar.id()))
                .extracting(GradeEntryResponse::score)
                .anySatisfy(s -> assertThat(s).isEqualByComparingTo("80.00"));
    }

    @Test
    void importGrades_UC53_A3_marksFailedForCorruptFile() {
        byte[] garbage = "not an excel file".getBytes();

        GradeImportResponse result = gradeImportService.importGrades(schoolClass.id(), gradeSetup.id(),
                new MockMultipartFile("file", "broken.xlsx", "application/vnd.openxmlformats", garbage), teacher.getId());

        assertThat(result.status()).isEqualTo("FAILED");
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng — file mẫu tải
     * xuống điền sẵn học sinh theo roster (rosterAsOfDate của setup, V95),
     * cột điểm/Nhận xét/Ghi chú để trống.
     */
    @Test
    void buildTemplate_hasOneRowPerActiveEnrolledStudentWithEmptyScoreColumns() throws IOException {
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student1.getId(), LocalDate.now()), headAcademic.getId());
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student2.getId(), LocalDate.now()), headAcademic.getId());

        byte[] template = gradeImportService.buildTemplate(schoolClass.id(), gradeSetup.id(), teacher.getId());

        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(template))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertThat(cellValues(header, 11)).containsExactly(
                    "Mã HS*", "Học Kỳ", "Họ và tên", "Ngày sinh", "Lớp", "Nói", "Viết", "Overall", "Level", "Nhận xét", "Ghi chú");

            assertThat(sheet.getLastRowNum()).isEqualTo(2); // header + 2 học sinh
            java.util.List<String> studentCodesInTemplate = new java.util.ArrayList<>();
            for (int r = 1; r <= 2; r++) {
                Row row = sheet.getRow(r);
                studentCodesInTemplate.add(row.getCell(0).getStringCellValue());
                assertThat(row.getCell(5)).isNull(); // cột "Nói" để trống
                assertThat(row.getCell(6)).isNull(); // cột "Viết" để trống
            }
            assertThat(studentCodesInTemplate).containsExactlyInAnyOrder(
                    student1.getStudentCode(), student2.getStudentCode());
        }
    }

    /**
     * Round-trip: tải mẫu về, điền điểm vào đúng ô trống, upload lại — PHẢI
     * thành công, không được ném GradeImportColumnMismatchException dù mẫu
     * có thêm cột hiển thị (Học Kỳ/Họ và tên/Ngày sinh/Lớp) mà mapHeader()
     * vốn không biết trước đây.
     */
    @Test
    void buildTemplate_roundTrip_reuploadingFilledTemplateImportsSuccessfully() throws IOException {
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student1.getId(), LocalDate.now()), headAcademic.getId());
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student2.getId(), LocalDate.now()), headAcademic.getId());
        byte[] template = gradeImportService.buildTemplate(schoolClass.id(), gradeSetup.id(), teacher.getId());

        byte[] filled;
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(template))) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                row.createCell(5).setCellValue(8.0); // Nói
                row.createCell(6).setCellValue(7.0); // Viết
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            filled = out.toByteArray();
        }

        GradeImportResponse result = gradeImportService.importGrades(schoolClass.id(), gradeSetup.id(),
                new MockMultipartFile("file", "mau-da-dien.xlsx", "application/vnd.openxmlformats", filled), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.successRows()).isEqualTo(2);
        assertThat(result.failedRows()).isEqualTo(0);
    }

    private java.util.List<String> cellValues(Row row, int count) {
        java.util.List<String> values = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            values.add(row.getCell(i).getStringCellValue());
        }
        return values;
    }

    private byte[] buildWorkbook(String[] headers, String[][] rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Diem");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private String curriculumCode() {
        return "CUR-IMP-" + SEQ.incrementAndGet();
    }

    private String classCode() {
        return "CLS-IMP-" + SEQ.incrementAndGet();
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
        s.setCode("SITE-IMP-" + SEQ.incrementAndGet());
        s.setName("Test Site");
        s.setSiteType(Site.SiteType.OWNED);
        return siteRepository.save(s);
    }

    private AcademicTerm newAcademicTerm(Site site) {
        AcademicTerm term = new AcademicTerm();
        term.setSite(site);
        term.setCode("TERM-IMP-" + SEQ.incrementAndGet());
        term.setName("Kỳ test");
        term.setStartDate(LocalDate.now().minusMonths(1));
        term.setEndDate(LocalDate.now().plusMonths(2));
        term.setCreatedBy(headAcademic);
        return academicTermRepository.save(term);
    }

    private Student newStudent() {
        User user = newUser("student");
        Student s = new Student();
        s.setUser(user);
        s.setStudentCode("HS-IMP-" + SEQ.incrementAndGet());
        s.setDateOfBirth(LocalDate.of(2012, 5, 1));
        s.setEnrollmentDate(LocalDate.now());
        return studentRepository.save(s);
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
