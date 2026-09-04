package vn.com.pps.education.service;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ImportJob;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * UC-35 (Alternate Flow A2) — xử lý 1 dòng Excel nhập học trong TRANSACTION
 * RIÊNG (REQUIRES_NEW), tách khỏi transaction của
 * StudentBatchImportService.importStudents(). Lý do bắt buộc phải tách:
 * classService.enroll() là 1 method @Transactional (REQUIRED) riêng — nếu
 * chạy chung transaction vật lý với job cha (như code cũ), exception ném ra
 * từ enroll() đánh dấu CẢ transaction cha là rollback-only ngay khi vượt
 * qua ranh giới @Transactional của nó, bất kể caller có catch hay không.
 * Hệ quả: job luôn kết thúc bằng UnexpectedRollbackException (500, không rõ
 * nguyên nhân) ở bước commit thay vì đúng hành vi A2 "1 dòng lỗi không chặn
 * dòng khác" — xem sự cố 500 /api/student-imports ngày 2026-09-03. Tách
 * REQUIRES_NEW để 1 dòng lỗi chỉ rollback đúng dòng đó, các dòng khác (kể cả
 * chạy trước/sau) không bị ảnh hưởng.
 */
@Service
public class StudentBatchImportRowService {

    private static final DateTimeFormatter DOB_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StudentRepository studentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final ClassService classService;
    private final PasswordEncoder passwordEncoder;

    public StudentBatchImportRowService(StudentRepository studentRepository,
                                         SchoolClassRepository schoolClassRepository,
                                         ClassEnrollmentRepository classEnrollmentRepository,
                                         UserRepository userRepository,
                                         RoleRepository roleRepository,
                                         UserRoleRepository userRoleRepository,
                                         ClassService classService,
                                         PasswordEncoder passwordEncoder) {
        this.studentRepository = studentRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.classService = classService;
        this.passwordEncoder = passwordEncoder;
    }

    /** Mật khẩu tạm (plaintext, 1 lần) sinh ra khi tạo tài khoản học sinh cho 1 dòng hợp lệ. */
    public record RowCredential(String username, String temporaryPassword, String fullName) {}

    /**
     * A2: 1 dòng lỗi/trùng lặp không chặn các dòng khác — ném exception để
     * caller (StudentBatchImportService) bắt và ghi vào error_summary. Chạy
     * trong transaction riêng (REQUIRES_NEW, xem Javadoc class) nên exception
     * ở đây chỉ rollback đúng dòng này.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RowCredential importRow(Row row, DataFormatter formatter, ImportJob job, User actor) {
        String fullName = cell(row, formatter, 0);
        String username = cell(row, formatter, 1);
        String dobText = cell(row, formatter, 2);
        String genderText = cell(row, formatter, 3);
        String originalSchool = cell(row, formatter, 4);
        String originalClass = cell(row, formatter, 5);
        String classCode = cell(row, formatter, 6);
        String studentCode = cell(row, formatter, 7);

        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Thiếu họ và tên (cột A).");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Thiếu username (cột B).");
        }
        if (dobText == null || dobText.isBlank()) {
            throw new IllegalArgumentException("Thiếu ngày sinh (cột C).");
        }
        if (classCode == null || classCode.isBlank()) {
            throw new IllegalArgumentException("Thiếu mã lớp (cột G).");
        }
        if (studentCode == null || studentCode.isBlank()) {
            throw new IllegalArgumentException("Thiếu mã học sinh (cột H).");
        }
        LocalDate dob;
        try {
            dob = LocalDate.parse(dobText.trim(), DOB_FORMAT);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Ngày sinh sai định dạng (cần dd/MM/yyyy): " + dobText);
        }
        SchoolClass schoolClass = schoolClassRepository.findByClassCode(classCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lớp mã=" + classCode));

        if (userRepository.findByUsername(username.trim()).isPresent()) {
            throw new IllegalArgumentException("Username đã tồn tại: " + username);
        }
        if (studentRepository.findByStudentCode(studentCode.trim()).isPresent()) {
            throw new IllegalArgumentException("Mã học sinh đã tồn tại: " + studentCode);
        }
        if (!studentRepository.findByFullNameAndDateOfBirth(fullName.trim(), dob).isEmpty()) {
            throw new IllegalArgumentException("Trùng lặp: đã có học sinh " + fullName + " sinh " + dobText + ".");
        }

        String tempPassword = generateTempPassword();
        User studentUser = new User();
        studentUser.setUsername(username.trim());
        studentUser.setEmail("import" + job.getId() + "-r" + row.getRowNum() + "@placeholder.pps.edu.vn");
        studentUser.setFullName(fullName.trim());
        studentUser.setPasswordHash(passwordEncoder.encode(tempPassword));
        studentUser.setStatus(User.Status.ACTIVE);
        studentUser = userRepository.save(studentUser);
        assignRole(studentUser, "STUDENT", actor);

        Student student = new Student();
        student.setUser(studentUser);
        student.setStudentCode(studentCode.trim());
        student.setDateOfBirth(dob);
        if (genderText != null && !genderText.isBlank()) {
            student.setGender(parseGender(genderText.trim()));
        }
        student.setPrimarySite(schoolClass.getSite());
        student.setOriginalSchool(originalSchool);
        student.setOriginalClass(originalClass);
        student.setEnrollmentDate(LocalDate.now());
        student = studentRepository.save(student);

        var enrollment = classService.enroll(schoolClass.getId(),
                new EnrollStudentRequest(student.getId(), LocalDate.now()), actor.getId());
        ClassEnrollment enrollmentEntity = classEnrollmentRepository.findById(enrollment.id()).orElseThrow();
        enrollmentEntity.setImportJobId(job.getId());
        classEnrollmentRepository.save(enrollmentEntity);

        return new RowCredential(studentUser.getUsername(), tempPassword, studentUser.getFullName());
    }

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    private Student.Gender parseGender(String text) {
        return switch (text.toLowerCase()) {
            case "nam", "male", "m" -> Student.Gender.MALE;
            case "nữ", "nu", "female", "f" -> Student.Gender.FEMALE;
            default -> Student.Gender.OTHER;
        };
    }

    private String cell(Row row, DataFormatter formatter, int index) {
        var cell = row.getCell(index);
        return cell == null ? null : formatter.formatCellValue(cell).trim();
    }

    private void assignRole(User user, String roleCode, User actor) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(actor);
        userRoleRepository.save(userRole);
    }
}
