package vn.com.pps.education.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Department;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.EmployeeBatchImportResponse;
import vn.com.pps.education.repository.DepartmentRepository;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC-51: Nhập nhân sự theo lô — Main Flow (bước 1-6), A1 (file sai định
 * dạng), A2 (một phần dòng lỗi/trùng lặp). Xem docs/uc/phan-he-04-nhan-su.md.
 */
@Transactional
class EmployeeBatchImportServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private EmployeeBatchImportService employeeBatchImportService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User hrManager;
    private Department department;

    @BeforeEach
    void setUp() {
        hrManager = newUser("hr.manager");
        department = newDepartment();
    }

    @Test
    void importEmployees_UC51_MainFlow_createsEmployeesAndReturnsCredentials() throws IOException {
        byte[] file = buildWorkbook(new String[][]{
                {"Nguyễn Văn A", username("nva"), "", "01/01/1990", employeeCode(), "Giáo viên", "GV Tiếng Anh", department.getCode(), "Không", "01/01/2024"},
                {"Trần Thị B", username("ttb"), "", "15/06/1988", employeeCode(), "Nhân viên", "", "", "Có", "01/02/2024"},
        });

        EmployeeBatchImportResponse result = employeeBatchImportService.importEmployees(
                new MockMultipartFile("file", "nhan_su.xlsx", "application/vnd.openxmlformats", file), hrManager.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.successRows()).isEqualTo(2);
        assertThat(result.failedRows()).isEqualTo(0);
        assertThat(result.generatedCredentials()).hasSize(2);

        String firstUsername = (String) result.generatedCredentials().get(0).get("username");
        String firstTempPassword = (String) result.generatedCredentials().get(0).get("temporaryPassword");
        User created = userRepository.findByUsername(firstUsername).orElseThrow();
        assertThat(passwordEncoder.matches(firstTempPassword, created.getPasswordHash())).isTrue();

        Employee employeeWithManagement = employeeRepository.findByUserId(
                userRepository.findByUsername((String) result.generatedCredentials().get(1).get("username")).orElseThrow().getId())
                .orElseThrow();
        assertThat(employeeWithManagement.isManagement()).isTrue();
        assertThat(employeeWithManagement.getDepartment()).isNull();
    }

    @Test
    void importEmployees_UC51_MainFlow_placeholderEmailWhenColumnBlank() throws IOException {
        String lvcUsername = username("lvc");
        byte[] file = buildWorkbook(new String[][]{
                {"Lê Văn C", lvcUsername, "", "10/09/1992", employeeCode(), "Nhân viên", "", "", "", "01/03/2024"},
        });

        employeeBatchImportService.importEmployees(
                new MockMultipartFile("file", "nhan_su.xlsx", "application/vnd.openxmlformats", file), hrManager.getId());

        User created = userRepository.findByUsername(lvcUsername).orElseThrow();
        assertThat(created.getEmail()).endsWith("@placeholder.pps.edu.vn");
    }

    @Test
    void importEmployees_UC51_A2_partialSuccessSkipsDuplicateAndInvalidRows() throws IOException {
        String duplicateCode = employeeCode();
        byte[] first = buildWorkbook(new String[][]{
                {"Phạm Văn D", username("pvd"), "", "01/01/1991", duplicateCode, "Nhân viên", "", "", "", "01/01/2024"},
        });
        employeeBatchImportService.importEmployees(
                new MockMultipartFile("file", "lan1.xlsx", "application/vnd.openxmlformats", first), hrManager.getId());

        byte[] second = buildWorkbook(new String[][]{
                {"Phạm Văn D 2", username("pvd2"), "", "01/01/1991", duplicateCode, "Nhân viên", "", "", "", "01/01/2024"}, // trùng mã nhân sự
                {"Thiếu Mã", "", "", "01/01/1991", employeeCode(), "Nhân viên", "", "", "", "01/01/2024"}, // thiếu username
                {"Sai Phòng Ban", username("saipb"), "", "01/01/1991", employeeCode(), "Nhân viên", "", "DEPT-KHONG-TON-TAI", "", "01/01/2024"}, // phòng ban không tồn tại
                {"Hợp Lệ", username("hople"), "", "01/01/1991", employeeCode(), "Nhân viên", "", "", "", "01/01/2024"}, // hợp lệ
        });

        EmployeeBatchImportResponse result = employeeBatchImportService.importEmployees(
                new MockMultipartFile("file", "lan2.xlsx", "application/vnd.openxmlformats", second), hrManager.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.totalRows()).isEqualTo(4);
        assertThat(result.successRows()).isEqualTo(1);
        assertThat(result.failedRows()).isEqualTo(3);
        assertThat(result.errorSummary()).hasSize(3);
    }

    @Test
    void importEmployees_UC51_A1_marksFailedForCorruptFile() {
        byte[] garbage = "not an excel file".getBytes();

        EmployeeBatchImportResponse result = employeeBatchImportService.importEmployees(
                new MockMultipartFile("file", "broken.xlsx", "application/vnd.openxmlformats", garbage), hrManager.getId());

        assertThat(result.status()).isEqualTo("FAILED");
    }

    @Test
    void getJob_UC51_doesNotReturnCredentialsAgain() throws IOException {
        byte[] file = buildWorkbook(new String[][]{
                {"An Toàn", username("antoan"), "", "01/01/1990", employeeCode(), "Nhân viên", "", "", "", "01/01/2024"},
        });
        EmployeeBatchImportResponse created = employeeBatchImportService.importEmployees(
                new MockMultipartFile("file", "nv.xlsx", "application/vnd.openxmlformats", file), hrManager.getId());
        assertThat(created.generatedCredentials()).isNotEmpty();

        EmployeeBatchImportResponse reFetched = employeeBatchImportService.getJob(created.id());

        assertThat(reFetched.generatedCredentials()).isEmpty();
    }

    private byte[] buildWorkbook(String[][] rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("NhanSu");
            Row header = sheet.createRow(0);
            String[] headers = {"Họ và tên", "Username", "Email", "Ngày sinh", "Mã nhân sự", "Loại nhân sự",
                    "Chức danh", "Mã phòng ban", "Miễn trừ", "Ngày vào làm"};
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

    private String username(String prefix) {
        return prefix + "." + SEQ.incrementAndGet();
    }

    private String employeeCode() {
        return "NVIMP" + SEQ.incrementAndGet();
    }

    private Department newDepartment() {
        Department d = new Department();
        d.setCode("DEPT-IMP-" + SEQ.incrementAndGet());
        d.setName("Phong Test Import");
        return departmentRepository.save(d);
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
