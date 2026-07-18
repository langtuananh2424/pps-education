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
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.StudentBatchImportResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC-35: Nhập học theo lô cho lớp liên kết — Main Flow (bước 1-6), A1
 * (file sai định dạng), A2 (một phần dòng lỗi/trùng lặp). Xem
 * docs/uc/phan-he-09-crm.md.
 */
@Transactional
class StudentBatchImportServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private StudentBatchImportService studentBatchImportService;

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

    private User staff;
    private ClassResponse schoolClass;

    @BeforeEach
    void setUp() {
        User headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        staff = newUser("giaovu");
        assignRole(staff, "STAFF");

        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null), headAcademic.getId());
        CurriculumResponse activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), headAcademic.getId());

        Site site = newSite();
        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "LINKED", 40, null,
                        LocalDate.now(), null, null, null), headAcademic.getId());
    }

    @Test
    void importStudents_UC35_MainFlow_createsStudentsForValidRows() throws IOException {
        byte[] file = buildWorkbook(new String[][]{
                {"Nguyễn Văn A", "01/01/2015", "Nam", "TH Kim Đồng", "Lớp 3A", schoolClass.classCode(), studentCode()},
                {"Trần Thị B", "15/06/2015", "Nữ", "TH Kim Đồng", "Lớp 3A", schoolClass.classCode(), studentCode()},
        });

        StudentBatchImportResponse result = studentBatchImportService.importStudents(
                new MockMultipartFile("file", "danh_sach.xlsx", "application/vnd.openxmlformats", file), staff.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.successRows()).isEqualTo(2);
        assertThat(result.failedRows()).isEqualTo(0);
    }

    @Test
    void importStudents_UC35_A2_partialSuccessSkipsDuplicateAndInvalidRows() throws IOException {
        String duplicateCode = studentCode();
        byte[] first = buildWorkbook(new String[][]{
                {"Lê Văn C", "10/09/2015", "Nam", "TH ABC", "Lớp 3B", schoolClass.classCode(), duplicateCode},
        });
        studentBatchImportService.importStudents(
                new MockMultipartFile("file", "lan1.xlsx", "application/vnd.openxmlformats", first), staff.getId());

        byte[] second = buildWorkbook(new String[][]{
                {"Lê Văn C", "10/09/2015", "Nam", "TH ABC", "Lớp 3B", schoolClass.classCode(), duplicateCode}, // trùng mã học sinh
                {"", "10/09/2015", "Nam", "TH ABC", "Lớp 3B", schoolClass.classCode(), studentCode()},        // thiếu họ tên
                {"Phạm Thị D", "20/11/2015", "Nữ", "TH ABC", "Lớp 3B", schoolClass.classCode(), studentCode()}, // hợp lệ
        });

        StudentBatchImportResponse result = studentBatchImportService.importStudents(
                new MockMultipartFile("file", "lan2.xlsx", "application/vnd.openxmlformats", second), staff.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.totalRows()).isEqualTo(3);
        assertThat(result.successRows()).isEqualTo(1);
        assertThat(result.failedRows()).isEqualTo(2);
        assertThat(result.errorSummary()).hasSize(2);
    }

    @Test
    void importStudents_UC35_A2_rejectsMissingStudentCode() throws IOException {
        byte[] file = buildWorkbook(new String[][]{
                {"Thiếu Mã", "10/09/2015", "Nam", "TH ABC", "Lớp 3B", schoolClass.classCode(), ""},
        });

        StudentBatchImportResponse result = studentBatchImportService.importStudents(
                new MockMultipartFile("file", "thieu_ma.xlsx", "application/vnd.openxmlformats", file), staff.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errorSummary().get(0).get("reason")).isEqualTo("Thiếu mã học sinh (cột G).");
    }

    @Test
    void importStudents_UC35_A1_marksFailedForCorruptFile() {
        byte[] garbage = "not an excel file".getBytes();

        StudentBatchImportResponse result = studentBatchImportService.importStudents(
                new MockMultipartFile("file", "broken.xlsx", "application/vnd.openxmlformats", garbage), staff.getId());

        assertThat(result.status()).isEqualTo("FAILED");
    }

    private byte[] buildWorkbook(String[][] rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("HocSinh");
            Row header = sheet.createRow(0);
            String[] headers = {"Họ và tên", "Ngày sinh", "Giới tính", "Trường đang học", "Lớp đang học", "Mã lớp PPS", "Mã học sinh"};
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
        return "CUR-" + SEQ.incrementAndGet();
    }

    private String classCode() {
        return "CLS-" + SEQ.incrementAndGet();
    }

    private String studentCode() {
        return "HSIMP" + SEQ.incrementAndGet();
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
        s.setCode("SITE-" + SEQ.incrementAndGet());
        s.setName("Test Site");
        s.setSiteType(Site.SiteType.PARTNER);
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
