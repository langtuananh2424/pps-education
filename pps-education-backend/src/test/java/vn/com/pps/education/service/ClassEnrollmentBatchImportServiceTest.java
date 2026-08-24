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
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.ClassEnrollmentBatchImportResponse;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-65: Ghi danh học sinh theo lô (bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng 2026-07-31) — Main Flow, A1 (file sai định dạng), A2 (một
 * phần dòng lỗi: mã học sinh không tồn tại / đã ghi danh ACTIVE sẵn).
 */
@Transactional
class ClassEnrollmentBatchImportServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ClassEnrollmentBatchImportService classEnrollmentBatchImportService;

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
    private StudentRepository studentRepository;

    private User headAcademic;
    private ClassResponse schoolClass;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        Site site = newSite();
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null, null, null), headAcademic.getId());
        CurriculumResponse activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), headAcademic.getId());
        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "Lớp ghi danh lô", site.getId(), activeCurriculum.id(), "OPEN",
                        30, null, LocalDate.now(), null, null),
                headAcademic.getId());
    }

    @Test
    void importEnrollments_UC65_MainFlow_enrollsExistingStudentsByCode() throws IOException {
        Student student1 = newStudent();
        Student student2 = newStudent();
        byte[] file = buildWorkbook(new String[][]{
                {student1.getStudentCode(), "2026-08-01"},
                {student2.getStudentCode(), ""},
        });

        ClassEnrollmentBatchImportResponse result = classEnrollmentBatchImportService.importEnrollments(
                schoolClass.id(), new MockMultipartFile("file", "ghi-danh.xlsx", "application/vnd.openxmlformats", file),
                headAcademic.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.successRows()).isEqualTo(2);
        assertThat(result.failedRows()).isEqualTo(0);
        var enrollments = classService.listEnrollments(schoolClass.id());
        assertThat(enrollments).extracting(e -> e.studentId()).containsExactlyInAnyOrder(student1.getId(), student2.getId());
        assertThat(enrollments).filteredOn(e -> e.studentId().equals(student1.getId()))
                .extracting(e -> e.enrolledDate()).containsExactly(LocalDate.of(2026, 8, 1));
    }

    /** Cột "Ngày ghi danh" để trống -- mặc định hôm nay (Javadoc importRow). */
    @Test
    void importEnrollments_boSung_blankEnrolledDateDefaultsToToday() throws IOException {
        Student student = newStudent();
        byte[] file = buildWorkbook(new String[][]{
                {student.getStudentCode(), ""},
        });

        classEnrollmentBatchImportService.importEnrollments(schoolClass.id(),
                new MockMultipartFile("file", "ghi-danh.xlsx", "application/vnd.openxmlformats", file), headAcademic.getId());

        var enrollment = classService.listEnrollments(schoolClass.id()).get(0);
        assertThat(enrollment.enrolledDate()).isEqualTo(LocalDate.now());
    }

    /** A1: file rỗng/thiếu dòng tiêu đề -- FAILED ngay, không xử lý dòng nào. */
    @Test
    void importEnrollments_UC65_A1_failsWholeJobWhenFileEmpty() throws IOException {
        byte[] emptyFile;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Ghi danh học sinh");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            emptyFile = out.toByteArray();
        }

        ClassEnrollmentBatchImportResponse result = classEnrollmentBatchImportService.importEnrollments(
                schoolClass.id(), new MockMultipartFile("file", "rong.xlsx", "application/vnd.openxmlformats", emptyFile),
                headAcademic.getId());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(classService.listEnrollments(schoolClass.id())).isEmpty();
    }

    /** A2: mã học sinh không tồn tại -- báo lỗi đúng dòng, không chặn dòng khác. */
    @Test
    void importEnrollments_UC65_A2_rowErrorForUnknownStudentCodeDoesNotBlockOtherRows() throws IOException {
        Student student = newStudent();
        byte[] file = buildWorkbook(new String[][]{
                {"MA-KHONG-TON-TAI", ""},
                {student.getStudentCode(), ""},
        });

        ClassEnrollmentBatchImportResponse result = classEnrollmentBatchImportService.importEnrollments(
                schoolClass.id(), new MockMultipartFile("file", "ghi-danh.xlsx", "application/vnd.openxmlformats", file),
                headAcademic.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.successRows()).isEqualTo(1);
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errorSummary().get(0).get("reason").toString()).contains("Không tìm thấy học sinh");
        assertThat(classService.listEnrollments(schoolClass.id())).extracting(e -> e.studentId()).containsExactly(student.getId());
    }

    /** A2: học sinh đã ghi danh ACTIVE sẵn trong lớp -- báo lỗi đúng dòng (tái dùng ClassService.enroll validate), không tạo trùng. */
    @Test
    void importEnrollments_UC65_A2_rowErrorWhenStudentAlreadyActivelyEnrolled() throws IOException {
        Student student = newStudent();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());
        byte[] file = buildWorkbook(new String[][]{
                {student.getStudentCode(), ""},
        });

        ClassEnrollmentBatchImportResponse result = classEnrollmentBatchImportService.importEnrollments(
                schoolClass.id(), new MockMultipartFile("file", "ghi-danh.xlsx", "application/vnd.openxmlformats", file),
                headAcademic.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(classService.listEnrollments(schoolClass.id())).hasSize(1);
    }

    /** classId trên URL không tồn tại là lỗi của request, không phải lỗi 1 dòng -- ném thẳng ra ngoài, không nuốt vào errorSummary. */
    @Test
    void importEnrollments_rejectsWhenClassNotFound() throws IOException {
        byte[] file = buildWorkbook(new String[][]{{"HS-BAT-KY", ""}});

        assertThatThrownBy(() -> classEnrollmentBatchImportService.importEnrollments(999_999L,
                new MockMultipartFile("file", "ghi-danh.xlsx", "application/vnd.openxmlformats", file), headAcademic.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void buildTemplate_boSung_hasExpectedHeaders() throws IOException {
        byte[] template = classEnrollmentBatchImportService.buildTemplate();

        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(template))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Mã học sinh*");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Ngày ghi danh (yyyy-MM-dd)");
        }
    }

    // ===================== Helpers =====================

    private byte[] buildWorkbook(String[][] rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Ghi danh học sinh");
            Row header = sheet.createRow(0);
            String[] headers = {"Mã học sinh", "Ngày ghi danh"};
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

    private Site newSite() {
        Site site = new Site();
        site.setCode("SITE-" + SEQ.incrementAndGet());
        site.setName("Test Site");
        site.setSiteType(Site.SiteType.OWNED);
        return siteRepository.save(site);
    }

    private Student newStudent() {
        User user = newUser("student.forbatch");
        Student student = new Student();
        student.setUser(user);
        student.setStudentCode("HS-BATCH-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        return studentRepository.save(student);
    }

    private User newUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + System.nanoTime());
        user.setEmail(prefix + "." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }

    private void assignRole(User user, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        var userRole = new vn.com.pps.education.domain.UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(user);
        userRoleRepository.save(userRole);
    }

    private String curriculumCode() {
        return "CUR-" + SEQ.incrementAndGet();
    }

    private String classCode() {
        return "CLS-" + SEQ.incrementAndGet();
    }
}
