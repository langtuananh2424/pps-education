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
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.ParentBatchImportResponse;
import vn.com.pps.education.repository.ParentRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC-50: Nhập phụ huynh theo lô, liên kết học sinh có sẵn — Main Flow
 * (bước 1-6), A1 (file sai định dạng), A2 (một phần dòng lỗi). Xem
 * docs/uc/phan-he-05-hoc-sinh.md.
 */
@Transactional
class ParentBatchImportServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ParentBatchImportService parentBatchImportService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private ParentStudentRepository parentStudentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User staff;

    @BeforeEach
    void setUp() {
        staff = newUser("giaovu");
    }

    @Test
    void importParents_UC50_MainFlow_createsParentAndLinksToExistingStudent() throws IOException {
        Student student = newStudent("Học Sinh Một");
        String phone = newPhone();
        byte[] file = buildWorkbook(new String[][]{
                {"Nguyễn Văn Cha", username(), phone, "Cha", student.getStudentCode(), "Có", "Có"},
        });

        ParentBatchImportResponse result = parentBatchImportService.importParents(
                new MockMultipartFile("file", "phu_huynh.xlsx", "application/vnd.openxmlformats", file), staff.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.successRows()).isEqualTo(1);

        User parentUser = userRepository.findByPhone(phone).orElseThrow();
        var parent = parentRepository.findByUserId(parentUser.getId()).orElseThrow();
        var link = parentStudentRepository.findByParentIdAndStudentId(parent.getId(), student.getId()).orElseThrow();
        assertThat(link.getRelationship().name()).isEqualTo("FATHER");
        assertThat(link.isPrimaryContact()).isTrue();
        assertThat(link.isFinancialResponsible()).isTrue();
    }

    @Test
    void importParents_UC50_Postcondition_generatesWorkingTemporaryPasswordForNewParentAccount() throws IOException {
        Student student = newStudent("Học Sinh Mật Khẩu");
        String phone = newPhone();
        byte[] file = buildWorkbook(new String[][]{
                {"Nguyễn Văn Mật Khẩu", username(), phone, "Cha", student.getStudentCode(), "", ""},
        });

        ParentBatchImportResponse result = parentBatchImportService.importParents(
                new MockMultipartFile("file", "phu_huynh.xlsx", "application/vnd.openxmlformats", file), staff.getId());

        assertThat(result.generatedCredentials()).hasSize(1);
        String username = (String) result.generatedCredentials().get(0).get("username");
        String tempPassword = (String) result.generatedCredentials().get(0).get("temporaryPassword");
        User created = userRepository.findByUsername(username).orElseThrow();
        assertThat(passwordEncoder.matches(tempPassword, created.getPasswordHash())).isTrue();

        // Tra cứu lại job sau đó -- KHÔNG còn thấy mật khẩu tạm (tránh lộ plaintext qua tra cứu lại).
        ParentBatchImportResponse reFetched = parentBatchImportService.getJob(result.id());
        assertThat(reFetched.generatedCredentials()).isEmpty();
    }

    @Test
    void importParents_UC50_sameParentTwoChildren_onlyFirstRowGeneratesCredential() throws IOException {
        Student child1 = newStudent("Con Mật Khẩu Một");
        Student child2 = newStudent("Con Mật Khẩu Hai");
        String phone = newPhone();
        String username = username();
        byte[] file = buildWorkbook(new String[][]{
                {"Trần Thị Mật Khẩu", username, phone, "Mẹ", child1.getStudentCode(), "", ""},
                {"Trần Thị Mật Khẩu", username, phone, "Mẹ", child2.getStudentCode(), "", ""},
        });

        ParentBatchImportResponse result = parentBatchImportService.importParents(
                new MockMultipartFile("file", "phu_huynh.xlsx", "application/vnd.openxmlformats", file), staff.getId());

        assertThat(result.successRows()).isEqualTo(2);
        // Dòng 2 dùng lại đúng 1 Parent đã tạo ở dòng 1 -- không sinh thêm credential trùng cho cùng 1 tài khoản.
        assertThat(result.generatedCredentials()).hasSize(1);
        // Header = dòng Excel 1 -- dòng dữ liệu đầu tiên (con1) là dòng Excel 2.
        assertThat(result.generatedCredentials().get(0).get("row")).isEqualTo(2);
    }

    @Test
    void importParents_UC50_MainFlow_sameParentTwoChildren_reusesOneParentRecord() throws IOException {
        Student child1 = newStudent("Con Một");
        Student child2 = newStudent("Con Hai");
        String phone = newPhone();
        String username = username();
        byte[] file = buildWorkbook(new String[][]{
                {"Trần Thị Mẹ", username, phone, "Mẹ", child1.getStudentCode(), "Có", "Có"},
                {"Trần Thị Mẹ", username, phone, "Mẹ", child2.getStudentCode(), "Không", "Không"},
        });

        ParentBatchImportResponse result = parentBatchImportService.importParents(
                new MockMultipartFile("file", "phu_huynh.xlsx", "application/vnd.openxmlformats", file), staff.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.successRows()).isEqualTo(2);

        User parentUser = userRepository.findByPhone(phone).orElseThrow();
        var parent = parentRepository.findByUserId(parentUser.getId()).orElseThrow();
        assertThat(parentStudentRepository.findByParentId(parent.getId())).hasSize(2);
    }

    @Test
    void importParents_UC50_A2_rollsBackNewAccountWhenLinkParentFailsAfterCreatingBrandNewParent() throws IOException {
        // Bug đã fix: 2 dòng với 2 SĐT MỚI hoàn toàn (chưa tồn tại), cùng chỉ định
        // primaryContact=Có cho CÙNG 1 học sinh -- dòng 2 tạo xong User+Parent mới
        // rồi mới phát hiện xung đột (StudentContactRoleConflictException). Trước
        // khi bọc REQUIRES_NEW, User+Parent của dòng 2 vẫn bị lưu vĩnh viễn dù dòng
        // báo lỗi (phụ huynh mồ côi, mật khẩu tạm mất vĩnh viễn).
        Student student = newStudent("Học Sinh Xung Đột Primary Contact");
        String phone1 = newPhone();
        String phone2 = newPhone();
        String username2 = username();
        byte[] file = buildWorkbook(new String[][]{
                {"Phụ Huynh Một", username(), phone1, "Cha", student.getStudentCode(), "Có", ""},
                {"Phụ Huynh Hai", username2, phone2, "Mẹ", student.getStudentCode(), "Có", ""},
        });

        ParentBatchImportResponse result = parentBatchImportService.importParents(
                new MockMultipartFile("file", "phu_huynh.xlsx", "application/vnd.openxmlformats", file), staff.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.successRows()).isEqualTo(1);
        assertThat(result.failedRows()).isEqualTo(1);

        // Dòng 2 thất bại -- User/Parent theo phone2/username2 KHÔNG được để lại (rollback sạch).
        assertThat(userRepository.findByPhone(phone2)).isEmpty();
        assertThat(userRepository.findByUsername(username2)).isEmpty();
    }

    @Test
    void importParents_UC50_A2_partialSuccessSkipsUnknownStudentCode() throws IOException {
        Student student = newStudent("Học Sinh Hợp Lệ");
        byte[] file = buildWorkbook(new String[][]{
                {"Phụ Huynh Sai Mã", username(), newPhone(), "Cha", "MA-KHONG-TON-TAI", "", ""},
                {"Phụ Huynh Đúng", username(), newPhone(), "Mẹ", student.getStudentCode(), "", ""},
        });

        ParentBatchImportResponse result = parentBatchImportService.importParents(
                new MockMultipartFile("file", "phu_huynh.xlsx", "application/vnd.openxmlformats", file), staff.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.successRows()).isEqualTo(1);
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errorSummary()).hasSize(1);
    }

    @Test
    void importParents_UC50_A2_rejectsMissingUsername() throws IOException {
        Student student = newStudent("Học Sinh Thiếu Username");
        byte[] file = buildWorkbook(new String[][]{
                {"Phụ Huynh Thiếu Username", "", newPhone(), "Cha", student.getStudentCode(), "", ""},
        });

        ParentBatchImportResponse result = parentBatchImportService.importParents(
                new MockMultipartFile("file", "phu_huynh.xlsx", "application/vnd.openxmlformats", file), staff.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errorSummary().get(0).get("reason")).isEqualTo("Thiếu username (cột B).");
    }

    @Test
    void importParents_UC50_A2_rejectsDuplicateUsernameOnlyWhenCreatingNewAccount() throws IOException {
        Student student = newStudent("Học Sinh Trùng Username");
        User existing = newUser("parent.dup.username");
        byte[] file = buildWorkbook(new String[][]{
                {"Phụ Huynh Trùng Username", existing.getUsername(), newPhone(), "Cha", student.getStudentCode(), "", ""},
        });

        ParentBatchImportResponse result = parentBatchImportService.importParents(
                new MockMultipartFile("file", "phu_huynh.xlsx", "application/vnd.openxmlformats", file), staff.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errorSummary().get(0).get("reason")).isEqualTo("Username đã tồn tại: " + existing.getUsername());
    }

    @Test
    void importParents_UC50_A1_marksFailedForCorruptFile() {
        byte[] garbage = "not an excel file".getBytes();

        ParentBatchImportResponse result = parentBatchImportService.importParents(
                new MockMultipartFile("file", "broken.xlsx", "application/vnd.openxmlformats", garbage), staff.getId());

        assertThat(result.status()).isEqualTo("FAILED");
    }

    private byte[] buildWorkbook(String[][] rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("PhuHuynh");
            Row header = sheet.createRow(0);
            String[] headers = {"Họ và tên phụ huynh", "Username", "Số điện thoại", "Quan hệ", "Mã học sinh",
                    "Là người liên hệ chính", "Chịu trách nhiệm tài chính"};
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

    private String newPhone() {
        return "09" + (100000000L + SEQ.incrementAndGet());
    }

    private String username() {
        return "phimp" + SEQ.incrementAndGet();
    }

    private Student newStudent(String fullName) {
        User user = new User();
        user.setUsername("hs" + SEQ.incrementAndGet());
        user.setEmail("hs" + SEQ.incrementAndGet() + "@pps.edu.vn");
        user.setFullName(fullName);
        user.setStatus(User.Status.ACTIVE);
        user = userRepository.save(user);

        Student student = new Student();
        student.setUser(user);
        student.setStudentCode("HSIMP" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2015, 1, 1));
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
}
