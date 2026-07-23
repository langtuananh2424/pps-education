package vn.com.pps.education.service;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ImportJob;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.StudentBatchImportResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ImportJobRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-35: Nhập học theo lô cho lớp liên kết (FR-CRM-04). Xem
 * docs/uc/phan-he-09-crm.md + docs/sdd-groups/08-tuyen-sinh-and-crm.md
 * ("Không có bảng riêng — tái sử dụng import_jobs với import_type=STUDENTS").
 *
 * Định dạng file Excel (.xlsx) — KHÔNG có mẫu cụ thể nào trong SRS/SDD
 * ("đúng định dạng mẫu" chỉ nói chung chung), tự định nghĩa cột theo thứ
 * tự: A=Họ và tên, B=Username (bắt buộc — người dùng tự nhập, giống
 * EmployeeBatchImportService, không tự sinh), C=Ngày sinh (dd/MM/yyyy),
 * D=Giới tính (Nam/Nữ/Khác, tùy chọn), E=Trường đang học (tùy chọn),
 * F=Lớp đang học (tùy chọn), G=Mã lớp PPS cần ghi danh (class_code, bắt
 * buộc), H=Mã học sinh (student_code, bắt buộc — người dùng tự nhập,
 * không tự sinh, đồng bộ UC-13/UC-34). Dòng 1 = header, dữ liệu từ dòng 2.
 *
 * Không có file storage (S3/blob) trong dự án — file chỉ được parse
 * trong bộ nhớ (MultipartFile), KHÔNG lưu trữ lâu dài; source_file_url
 * (NOT NULL theo SDD) chỉ ghi placeholder tên file, không phải URL truy
 * cập được thật — note gap tương tự các chỗ khác thiếu hạ tầng file thật.
 *
 * Dedup (Main Flow bước 3): SDD nói "theo CCCD/họ tên+ngày sinh" nhưng
 * schema students không có cột CCCD — dùng họ tên+ngày sinh. Từ khi
 * student_code chuyển sang nhập tay (cột G), kiểm tra thêm trùng theo
 * student_code — khớp đúng nghĩa "mã học sinh" trong câu SDD trên mà
 * trước đây chưa có dữ liệu để check (đóng khoảng lệch cũ).
 *
 * Mật khẩu tài khoản học sinh (bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng qua nhiều vòng hỏi): trước đây tài khoản tạo qua import
 * không có mật khẩu nào (chỉ dự kiến đăng nhập Google — nhưng email lưu
 * là placeholder không khớp email Google thật của học sinh, nên tài
 * khoản thực tế không đăng nhập được bằng cách nào). Từ nay hệ thống tự
 * sinh mật khẩu tạm ngẫu nhiên mỗi dòng — giống hệt pattern UC-51
 * EmployeeBatchImportService — hash lưu password_hash, CHỈ trả plaintext
 * 1 lần trong response của chính lần gọi importStudents() (KHÔNG lưu vào
 * import_jobs). Nếu cần đăng nhập Google về sau, Quản trị viên có thể sửa
 * lại email placeholder sang email Google thật qua UC-55.
 */
@Service
public class StudentBatchImportService {

    private static final DateTimeFormatter DOB_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int HEADER_ROW_INDEX = 0;
    private static final int FIRST_DATA_ROW_INDEX = 1;
    private static final int COLUMN_COUNT = 8;
    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ImportJobRepository importJobRepository;
    private final StudentRepository studentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final ClassService classService;
    private final PasswordEncoder passwordEncoder;

    public StudentBatchImportService(ImportJobRepository importJobRepository,
                                      StudentRepository studentRepository,
                                      SchoolClassRepository schoolClassRepository,
                                      ClassEnrollmentRepository classEnrollmentRepository,
                                      UserRepository userRepository,
                                      RoleRepository roleRepository,
                                      UserRoleRepository userRoleRepository,
                                      ClassService classService,
                                      PasswordEncoder passwordEncoder) {
        this.importJobRepository = importJobRepository;
        this.studentRepository = studentRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.classService = classService;
        this.passwordEncoder = passwordEncoder;
    }

    /** Main Flow bước 1-6. A1: file sai định dạng hoàn toàn → status=FAILED ngay, không xử lý dòng nào. */
    @Transactional
    public StudentBatchImportResponse importStudents(MultipartFile file, Long actorUserId) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user id=" + actorUserId));

        ImportJob job = new ImportJob();
        job.setImportType(ImportJob.ImportType.STUDENTS);
        job.setSourceFileName(file.getOriginalFilename() == null ? "unnamed.xlsx" : file.getOriginalFilename());
        job.setSourceFileUrl("in-memory://" + job.getSourceFileName());
        job.setUploadedBy(actor);
        job.setStatus(ImportJob.Status.PROCESSING);
        job.setStartedAt(OffsetDateTime.now());
        job = importJobRepository.save(job);

        List<Map<String, Object>> errors = new ArrayList<>();
        List<Map<String, Object>> credentials = new ArrayList<>();
        try (InputStream inputStream = file.getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getRow(HEADER_ROW_INDEX) == null) {
                return failJob(job, "File rỗng hoặc thiếu dòng tiêu đề.");
            }

            int totalRows = 0;
            int successRows = 0;
            DataFormatter formatter = new DataFormatter();
            for (int rowIndex = FIRST_DATA_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row, formatter)) {
                    continue;
                }
                totalRows++;
                try {
                    RowCredential credential = importRow(row, formatter, job, actor);
                    successRows++;
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("row", rowIndex + 1);
                    entry.put("username", credential.username());
                    entry.put("temporaryPassword", credential.temporaryPassword());
                    credentials.add(entry);
                } catch (RuntimeException ex) {
                    errors.add(rowError(rowIndex + 1, ex.getMessage()));
                }
            }

            job.setTotalRows(totalRows);
            job.setSuccessRows(successRows);
            job.setFailedRows(errors.size());
            job.setErrorSummary(errors);
            job.setStatus(errors.isEmpty() ? ImportJob.Status.COMPLETED : ImportJob.Status.PARTIAL_SUCCESS);
            job.setFinishedAt(OffsetDateTime.now());
            job = importJobRepository.save(job);
            return toResponse(job, credentials);
        } catch (IOException | RuntimeException ex) {
            return failJob(job, "File sai định dạng Excel (.xlsx): " + ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public StudentBatchImportResponse getJob(Long id) {
        ImportJob job = importJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy import job id=" + id));
        return toResponse(job, List.of());
    }

    /** Mật khẩu tạm (plaintext, 1 lần) sinh ra khi tạo tài khoản học sinh cho 1 dòng hợp lệ. */
    private record RowCredential(String username, String temporaryPassword) {}

    // ===================== Helpers =====================

    /**
     * A2: 1 dòng lỗi/trùng lặp không chặn các dòng khác — ném exception để
     * caller bắt và ghi vào error_summary. Mọi validate/dedup throw đều nằm
     * TRƯỚC khi tạo bản ghi (user/student/enrollment) trong method này, nên
     * dòng lỗi không để lại dữ liệu mồ côi — trừ 1 trường hợp hẹp:
     * classService.enroll() ở cuối vẫn có thể throw sau khi user+student đã
     * lưu; toàn bộ job dùng chung 1 transaction (không tách REQUIRES_NEW
     * theo dòng) nên trường hợp đó sẽ để lại user/student không có
     * enrollment. Chấp nhận rủi ro hẹp này (lớp đã tồn tại + dedup đã qua
     * nên enroll hiếm khi fail) thay vì thêm phức tạp tách bean riêng cho
     * REQUIRES_NEW.
     */
    private RowCredential importRow(Row row, DataFormatter formatter, ImportJob job, User actor) {
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
        // student.setStudentCode(generateStudentCode()); // cũ: hệ thống tự sinh mã học sinh
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

        return new RowCredential(studentUser.getUsername(), tempPassword);
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

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        for (int i = 0; i < COLUMN_COUNT; i++) {
            String value = cell(row, formatter, i);
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private void assignRole(User user, String roleCode, User actor) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(actor);
        userRoleRepository.save(userRole);
    }

    // Cũ: hệ thống tự sinh mã học sinh — đã đổi sang nhập tay qua cột G
    // (đồng bộ UC-13/UC-34), giữ lại đây để tham chiếu nếu cần khôi phục.
    // private String generateStudentCode() {
    //     String prefix = "HS" + Year.now().getValue() + "-";
    //     long sequence = studentRepository.countByStudentCodeStartingWith(prefix) + 1;
    //     return prefix + String.format("%04d", sequence);
    // }

    private Map<String, Object> rowError(int rowNumber, String reason) {
        Map<String, Object> error = new HashMap<>();
        error.put("row", rowNumber);
        error.put("reason", reason);
        return error;
    }

    /** A1: file sai định dạng hoàn toàn — không xử lý dòng nào, đánh dấu FAILED ngay. */
    private StudentBatchImportResponse failJob(ImportJob job, String reason) {
        job.setStatus(ImportJob.Status.FAILED);
        job.setErrorSummary(List.of(rowError(0, reason)));
        job.setFinishedAt(OffsetDateTime.now());
        job = importJobRepository.save(job);
        return toResponse(job, List.of());
    }

    private StudentBatchImportResponse toResponse(ImportJob job, List<Map<String, Object>> credentials) {
        return new StudentBatchImportResponse(
                job.getId(), job.getSourceFileName(), job.getTotalRows(), job.getSuccessRows(), job.getFailedRows(),
                job.getStatus().name(), job.getErrorSummary(), credentials);
    }
}
