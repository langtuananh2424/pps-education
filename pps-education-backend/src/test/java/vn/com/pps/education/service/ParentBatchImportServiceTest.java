package vn.com.pps.education.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
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
                {"Nguyễn Văn Cha", phone, "Cha", student.getStudentCode(), "Có", "Có"},
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
    void importParents_UC50_MainFlow_sameParentTwoChildren_reusesOneParentRecord() throws IOException {
        Student child1 = newStudent("Con Một");
        Student child2 = newStudent("Con Hai");
        String phone = newPhone();
        byte[] file = buildWorkbook(new String[][]{
                {"Trần Thị Mẹ", phone, "Mẹ", child1.getStudentCode(), "Có", "Có"},
                {"Trần Thị Mẹ", phone, "Mẹ", child2.getStudentCode(), "Không", "Không"},
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
    void importParents_UC50_A2_partialSuccessSkipsUnknownStudentCode() throws IOException {
        Student student = newStudent("Học Sinh Hợp Lệ");
        byte[] file = buildWorkbook(new String[][]{
                {"Phụ Huynh Sai Mã", newPhone(), "Cha", "MA-KHONG-TON-TAI", "", ""},
                {"Phụ Huynh Đúng", newPhone(), "Mẹ", student.getStudentCode(), "", ""},
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
            String[] headers = {"Họ và tên phụ huynh", "Số điện thoại", "Quan hệ", "Mã học sinh",
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
