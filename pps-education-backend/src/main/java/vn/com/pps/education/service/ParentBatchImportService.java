package vn.com.pps.education.service;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import java.security.SecureRandom;
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
 *
 * Mật khẩu tài khoản phụ huynh (bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng qua nhiều vòng hỏi): trước đây tài khoản tạo mới qua luồng
 * này không có mật khẩu nào (chỉ dự kiến đăng nhập Google, giống
 * LeadService.findOrCreateParent — UC-01 A4) — nhưng email lưu là
 * placeholder theo số điện thoại, không khớp email Google thật của phụ
 * huynh, nên tài khoản thực tế không đăng nhập được bằng cách nào. Từ
 * nay, CHỈ khi thực sự tạo User mới (nhánh orElseGet — không áp dụng khi
 * DÙNG LẠI Parent đã tồn tại từ dòng khác/từ UC-34), hệ thống tự sinh mật
 * khẩu tạm ngẫu nhiên — giống hệt pattern UC-51 EmployeeBatchImportService
 * — hash lưu password_hash, CHỈ trả plaintext 1 lần trong response của
 * chính lần gọi importParents() (KHÔNG lưu vào import_jobs). LeadService.
 * findOrCreateParent (UC-34) CHƯA đổi theo — vẫn không đặt mật khẩu, đợi
 * xác nhận riêng vì đó là 1 UC/luồng nghiệp vụ khác (chuyển đổi Lead), có
 * thể gây thiếu nhất quán tạm thời giữa 2 nguồn tạo phụ huynh. Nếu cần
 * đăng nhập Google về sau, Quản trị viên có thể sửa lại email placeholder
 * sang email Google thật qua UC-55.
 */
@Service
public class ParentBatchImportService {

    private static final int HEADER_ROW_INDEX = 0;
    private static final int FIRST_DATA_ROW_INDEX = 1;
    private static final int COLUMN_COUNT = 6;
    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ImportJobRepository importJobRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final StudentService studentService;
    private final PasswordEncoder passwordEncoder;

    public ParentBatchImportService(ImportJobRepository importJobRepository,
                                     StudentRepository studentRepository,
                                     ParentRepository parentRepository,
                                     UserRepository userRepository,
                                     RoleRepository roleRepository,
                                     UserRoleRepository userRoleRepository,
                                     StudentService studentService,
                                     PasswordEncoder passwordEncoder) {
        this.importJobRepository = importJobRepository;
        this.studentRepository = studentRepository;
        this.parentRepository = parentRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.studentService = studentService;
        this.passwordEncoder = passwordEncoder;
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
                    RowCredential credential = importRow(row, formatter, actor, actorUserId);
                    successRows++;
                    // Chỉ có credential (không null) khi dòng này thực sự tạo User phụ huynh MỚI.
                    if (credential != null) {
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("row", rowIndex + 1);
                        entry.put("username", credential.username());
                        entry.put("temporaryPassword", credential.temporaryPassword());
                        credentials.add(entry);
                    }
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
    public ParentBatchImportResponse getJob(Long id) {
        ImportJob job = importJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy import job id=" + id));
        return toResponse(job, List.of());
    }

    // ===================== Helpers =====================

    /** Mật khẩu tạm (plaintext, 1 lần) sinh ra khi dòng này tạo User phụ huynh MỚI. */
    private record RowCredential(String username, String temporaryPassword) {}

    /** A2: 1 dòng lỗi/trùng lặp không chặn các dòng khác. Trả về credential nếu dòng này tạo User mới, null nếu dùng lại Parent đã tồn tại. */
    private RowCredential importRow(Row row, DataFormatter formatter, User actor, Long actorUserId) {
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

        ParentAndCredential result = findOrCreateParent(fullName.trim(), phone.trim(), actor, actorUserId);

        studentService.linkParent(student.getId(), new LinkParentRequest(
                result.parent().getId(), relationship.name(), parseBoolean(primaryContactText), parseBoolean(financialResponsibleText), null));

        return result.temporaryPassword() == null ? null : new RowCredential(result.username(), result.temporaryPassword());
    }

    /** parent + username luôn có; temporaryPassword chỉ khác null khi vừa tạo User MỚI ở nhánh orElseGet bên dưới. */
    private record ParentAndCredential(Parent parent, String username, String temporaryPassword) {}

    /**
     * Tái dùng NGUYÊN XI cơ chế LeadService.findOrCreateParent (UC-34) để
     * tìm/tạo User+Parent — không phát minh quy tắc mới ở phần đó. Chỉ bổ
     * sung việc sinh mật khẩu tạm khi thực sự tạo User mới (bổ sung ngoài
     * SDD gốc, đã xác nhận với người dùng — xem Javadoc đầu file); LeadService
     * chưa đổi theo nên vẫn không đặt mật khẩu ở nhánh UC-34.
     */
    private ParentAndCredential findOrCreateParent(String fullName, String phone, User actor, Long actorUserId) {
        String[] tempPasswordHolder = new String[1];
        User parentUser = userRepository.findByPhone(phone).orElseGet(() -> {
            String tempPassword = generateTempPassword();
            User newUser = new User();
            newUser.setUsername(generateUsername(phone));
            newUser.setEmail(generatePlaceholderEmail(phone));
            newUser.setFullName(fullName);
            newUser.setPhone(phone);
            newUser.setPasswordHash(passwordEncoder.encode(tempPassword));
            newUser.setStatus(User.Status.ACTIVE);
            User saved = userRepository.save(newUser);
            assignRole(saved, "PARENT", actor);
            tempPasswordHolder[0] = tempPassword;
            return saved;
        });

        Parent parent = parentRepository.findByUserId(parentUser.getId())
                .orElseGet(() -> {
                    var response = studentService.createParent(
                            new CreateParentRequest(parentUser.getId(), null, null, null, null, null), actorUserId);
                    return parentRepository.findById(response.id()).orElseThrow();
                });
        return new ParentAndCredential(parent, parentUser.getUsername(), tempPasswordHolder[0]);
    }

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
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
        return toResponse(job, List.of());
    }

    private ParentBatchImportResponse toResponse(ImportJob job, List<Map<String, Object>> credentials) {
        return new ParentBatchImportResponse(
                job.getId(), job.getSourceFileName(), job.getTotalRows(), job.getSuccessRows(), job.getFailedRows(),
                job.getStatus().name(), job.getErrorSummary(), credentials);
    }
}
