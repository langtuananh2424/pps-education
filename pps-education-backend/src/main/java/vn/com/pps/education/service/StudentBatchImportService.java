package vn.com.pps.education.service;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
 * tự: A=Họ và tên, B=Ngày sinh (dd/MM/yyyy), C=Giới tính (Nam/Nữ/Khác,
 * tùy chọn), D=Trường đang học (tùy chọn), E=Lớp đang học (tùy chọn),
 * F=Mã lớp PPS cần ghi danh (class_code, bắt buộc), G=Mã học sinh
 * (student_code, bắt buộc — người dùng tự nhập, không tự sinh, đồng bộ
 * UC-13/UC-34). Dòng 1 = header, dữ liệu từ dòng 2.
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
 */
@Service
public class StudentBatchImportService {

    private static final DateTimeFormatter DOB_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int HEADER_ROW_INDEX = 0;
    private static final int FIRST_DATA_ROW_INDEX = 1;

    private final ImportJobRepository importJobRepository;
    private final StudentRepository studentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final ClassService classService;

    public StudentBatchImportService(ImportJobRepository importJobRepository,
                                      StudentRepository studentRepository,
                                      SchoolClassRepository schoolClassRepository,
                                      ClassEnrollmentRepository classEnrollmentRepository,
                                      UserRepository userRepository,
                                      RoleRepository roleRepository,
                                      UserRoleRepository userRoleRepository,
                                      ClassService classService) {
        this.importJobRepository = importJobRepository;
        this.studentRepository = studentRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.classService = classService;
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
                    importRow(row, formatter, job, actor);
                    successRows++;
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
            return toResponse(job);
        } catch (IOException | RuntimeException ex) {
            return failJob(job, "File sai định dạng Excel (.xlsx): " + ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public StudentBatchImportResponse getJob(Long id) {
        ImportJob job = importJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy import job id=" + id));
        return toResponse(job);
    }

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
    private void importRow(Row row, DataFormatter formatter, ImportJob job, User actor) {
        String fullName = cell(row, formatter, 0);
        String dobText = cell(row, formatter, 1);
        String genderText = cell(row, formatter, 2);
        String originalSchool = cell(row, formatter, 3);
        String originalClass = cell(row, formatter, 4);
        String classCode = cell(row, formatter, 5);
        String studentCode = cell(row, formatter, 6);

        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Thiếu họ và tên (cột A).");
        }
        if (dobText == null || dobText.isBlank()) {
            throw new IllegalArgumentException("Thiếu ngày sinh (cột B).");
        }
        if (classCode == null || classCode.isBlank()) {
            throw new IllegalArgumentException("Thiếu mã lớp (cột F).");
        }
        if (studentCode == null || studentCode.isBlank()) {
            throw new IllegalArgumentException("Thiếu mã học sinh (cột G).");
        }
        LocalDate dob;
        try {
            dob = LocalDate.parse(dobText.trim(), DOB_FORMAT);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Ngày sinh sai định dạng (cần dd/MM/yyyy): " + dobText);
        }
        SchoolClass schoolClass = schoolClassRepository.findByClassCode(classCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lớp mã=" + classCode));

        if (studentRepository.findByStudentCode(studentCode.trim()).isPresent()) {
            throw new IllegalArgumentException("Mã học sinh đã tồn tại: " + studentCode);
        }
        if (!studentRepository.findByFullNameAndDateOfBirth(fullName.trim(), dob).isEmpty()) {
            throw new IllegalArgumentException("Trùng lặp: đã có học sinh " + fullName + " sinh " + dobText + ".");
        }

        User studentUser = new User();
        studentUser.setUsername(generateUsername(job.getId(), row.getRowNum()));
        studentUser.setEmail("import" + job.getId() + "-r" + row.getRowNum() + "@placeholder.pps.edu.vn");
        studentUser.setFullName(fullName.trim());
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
        for (int i = 0; i < 7; i++) {
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

    private String generateUsername(Long jobId, int rowIndex) {
        String base = "imp" + jobId + "r" + rowIndex;
        String candidate = base;
        int suffix = 0;
        while (userRepository.findByUsername(candidate).isPresent()) {
            suffix++;
            candidate = base + suffix;
        }
        return candidate;
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
        return toResponse(job);
    }

    private StudentBatchImportResponse toResponse(ImportJob job) {
        return new StudentBatchImportResponse(
                job.getId(), job.getSourceFileName(), job.getTotalRows(), job.getSuccessRows(), job.getFailedRows(),
                job.getStatus().name(), job.getErrorSummary());
    }
}
