package vn.com.pps.education.service;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.domain.ImportJob;
import vn.com.pps.education.domain.Parent;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.domain.ParentStudent;
import vn.com.pps.education.dto.CreateParentRequest;
import vn.com.pps.education.dto.LinkParentRequest;
import vn.com.pps.education.dto.ParentBatchImportResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ImportJobRepository;
import vn.com.pps.education.repository.ParentRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-50: Nhập phụ huynh theo lô, liên kết học sinh có sẵn (FR-STU-04). Xem
 * docs/uc/phan-he-05-hoc-sinh.md. Chưa có UC/FR nào trong SRS/SDD gốc đặc
 * tả tính năng này trước khi thêm — đã xác nhận với người dùng: mỗi dòng
 * BẮT BUỘC liên kết 1 học sinh đã tồn tại sẵn, không tạo phụ huynh mồ côi.
 *
 * Định dạng file Excel (.xlsx) — tự định nghĩa cột theo thứ tự (giống
 * UC-35): A=Họ và tên phụ huynh, B=Số điện thoại, C=Quan hệ (Cha/Mẹ/Người
 * giám hộ/Khác), D=Mã học sinh (student_code, bắt buộc tồn tại sẵn),
 * E=Là người liên hệ chính (Có/Không, tùy chọn), F=Chịu trách nhiệm tài
 * chính (Có/Không, tùy chọn). Dòng 1 = header, dữ liệu từ dòng 2.
 *
 * Tạo tài khoản/hồ sơ phụ huynh: tái dùng NGUYÊN XI cơ chế đã có ở
 * LeadService.findOrCreateParent (UC-34) — không phát minh quy tắc mới:
 * tìm User theo phone, chưa có thì tạo (username/email placeholder theo
 * phone, gán role PARENT), chưa có Parent thì gọi StudentService.createParent
 * tạo hồ sơ. 2 dòng Excel cùng SĐT (anh chị em ruột, khác con) sẽ DÙNG
 * LẠI đúng 1 Parent, chỉ tạo thêm parent_student cho từng em — không tạo
 * trùng User/Parent.
 *
 * Liên kết parent_student: gọi thẳng StudentService.linkParent (UC-13
 * Main Flow bước 2) để tái dùng nguyên vẹn validate đã có (trùng liên kết
 * — ParentStudentLinkAlreadyExistsException; xung đột primary
 * contact/financial responsible — StudentContactRoleConflictException),
 * không viết lại logic đó.
 */
@Service
public class ParentBatchImportService {

    private static final int HEADER_ROW_INDEX = 0;
    private static final int FIRST_DATA_ROW_INDEX = 1;
    private static final int COLUMN_COUNT = 6;

    private final ImportJobRepository importJobRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final StudentService studentService;

    public ParentBatchImportService(ImportJobRepository importJobRepository,
                                     StudentRepository studentRepository,
                                     ParentRepository parentRepository,
                                     UserRepository userRepository,
                                     RoleRepository roleRepository,
                                     UserRoleRepository userRoleRepository,
                                     StudentService studentService) {
        this.importJobRepository = importJobRepository;
        this.studentRepository = studentRepository;
        this.parentRepository = parentRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.studentService = studentService;
    }

    /** Main Flow bước 1-6. A1: file sai định dạng hoàn toàn → status=FAILED ngay, không xử lý dòng nào. */
    @Transactional
    public ParentBatchImportResponse importParents(MultipartFile file, Long actorUserId) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user id=" + actorUserId));

        ImportJob job = new ImportJob();
        job.setImportType(ImportJob.ImportType.PARENTS);
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
                    importRow(row, formatter, actor, actorUserId);
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
    public ParentBatchImportResponse getJob(Long id) {
        ImportJob job = importJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy import job id=" + id));
        return toResponse(job);
    }

    // ===================== Helpers =====================

    /** A2: 1 dòng lỗi/trùng lặp không chặn các dòng khác. */
    private void importRow(Row row, DataFormatter formatter, User actor, Long actorUserId) {
        String fullName = cell(row, formatter, 0);
        String phone = cell(row, formatter, 1);
        String relationshipText = cell(row, formatter, 2);
        String studentCode = cell(row, formatter, 3);
        String primaryContactText = cell(row, formatter, 4);
        String financialResponsibleText = cell(row, formatter, 5);

        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Thiếu họ và tên phụ huynh (cột A).");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Thiếu số điện thoại (cột B).");
        }
        if (relationshipText == null || relationshipText.isBlank()) {
            throw new IllegalArgumentException("Thiếu quan hệ (cột C).");
        }
        if (studentCode == null || studentCode.isBlank()) {
            throw new IllegalArgumentException("Thiếu mã học sinh (cột D).");
        }
        var relationship = parseRelationship(relationshipText.trim());
        Student student = studentRepository.findByStudentCode(studentCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy học sinh mã=" + studentCode));

        Parent parent = findOrCreateParent(fullName.trim(), phone.trim(), actor, actorUserId);

        studentService.linkParent(student.getId(), new LinkParentRequest(
                parent.getId(), relationship.name(), parseBoolean(primaryContactText), parseBoolean(financialResponsibleText), null));
    }

    /** Tái dùng nguyên xi cơ chế LeadService.findOrCreateParent (UC-34) — không phát minh quy tắc mới. */
    private Parent findOrCreateParent(String fullName, String phone, User actor, Long actorUserId) {
        User parentUser = userRepository.findByPhone(phone).orElseGet(() -> {
            User newUser = new User();
            newUser.setUsername(generateUsername(phone));
            newUser.setEmail(generatePlaceholderEmail(phone));
            newUser.setFullName(fullName);
            newUser.setPhone(phone);
            newUser.setStatus(User.Status.ACTIVE);
            User saved = userRepository.save(newUser);
            assignRole(saved, "PARENT", actor);
            return saved;
        });

        return parentRepository.findByUserId(parentUser.getId())
                .orElseGet(() -> {
                    var response = studentService.createParent(
                            new CreateParentRequest(parentUser.getId(), null, null, null, null, null, null), actorUserId);
                    return parentRepository.findById(response.id()).orElseThrow();
                });
    }

    private ParentStudent.Relationship parseRelationship(String text) {
        return switch (text.toLowerCase()) {
            case "cha", "father", "bố", "bo" -> ParentStudent.Relationship.FATHER;
            case "mẹ", "me", "mother" -> ParentStudent.Relationship.MOTHER;
            case "người giám hộ", "nguoi giam ho", "guardian" -> ParentStudent.Relationship.GUARDIAN;
            case "khác", "khac", "other" -> ParentStudent.Relationship.OTHER;
            default -> throw new IllegalArgumentException(
                    "Quan hệ không hợp lệ (cần Cha/Mẹ/Người giám hộ/Khác): " + text);
        };
    }

    private boolean parseBoolean(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return switch (text.trim().toLowerCase()) {
            case "có", "co", "true", "yes", "x" -> true;
            default -> false;
        };
    }

    private void assignRole(User user, String roleCode, User actor) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(actor);
        userRoleRepository.save(userRole);
    }

    private String generateUsername(String phone) {
        String base = "ph" + phone.replaceAll("[^0-9]", "");
        String candidate = base;
        int suffix = 0;
        while (userRepository.findByUsername(candidate).isPresent()) {
            suffix++;
            candidate = base + suffix;
        }
        return candidate;
    }

    private String generatePlaceholderEmail(String phone) {
        return "parent" + phone.replaceAll("[^0-9]", "") + "@placeholder.pps.edu.vn";
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

    private Map<String, Object> rowError(int rowNumber, String reason) {
        Map<String, Object> error = new HashMap<>();
        error.put("row", rowNumber);
        error.put("reason", reason);
        return error;
    }

    /** A1: file sai định dạng hoàn toàn — không xử lý dòng nào, đánh dấu FAILED ngay. */
    private ParentBatchImportResponse failJob(ImportJob job, String reason) {
        job.setStatus(ImportJob.Status.FAILED);
        job.setErrorSummary(List.of(rowError(0, reason)));
        job.setFinishedAt(OffsetDateTime.now());
        job = importJobRepository.save(job);
        return toResponse(job);
    }

    private ParentBatchImportResponse toResponse(ImportJob job) {
        return new ParentBatchImportResponse(
                job.getId(), job.getSourceFileName(), job.getTotalRows(), job.getSuccessRows(), job.getFailedRows(),
                job.getStatus().name(), job.getErrorSummary());
    }
}
