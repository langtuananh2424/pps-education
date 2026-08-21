package vn.com.pps.education.service;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.common.ExcelExportHelper;
import vn.com.pps.education.domain.Department;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.ImportJob;
import vn.com.pps.education.domain.Position;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AccountExportRequest;
import vn.com.pps.education.dto.EmployeeBatchImportResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.DepartmentRepository;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.ImportJobRepository;
import vn.com.pps.education.repository.PositionRepository;
import vn.com.pps.education.repository.UserRepository;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-51: Nhập nhân sự theo lô (FR-HRM-05). Xem docs/uc/phan-he-04-nhan-su.md.
 * Chưa có UC/FR nào trong SRS/SDD gốc đặc tả tính năng này trước khi thêm —
 * đã xác nhận với người dùng các quy tắc dưới đây trước khi code (xem UC-51).
 *
 * Định dạng file Excel (.xlsx) — tự định nghĩa cột theo thứ tự (giống
 * UC-35, không có mẫu cụ thể nào khác trong SRS/SDD): A=Họ và tên,
 * B=Username, C=Email (tùy chọn), D=Ngày sinh (dd/MM/yyyy), E=Mã nhân sự,
 * F=Loại nhân sự (Giáo viên/Nhân viên/Quản lý), G=Mã chức vụ (tùy chọn,
 * khớp positions.code), H=Mã phòng ban (tùy chọn), I=Miễn trừ chấm công/
 * duyệt đơn (Có/Không, tùy chọn), J=Ngày vào làm (dd/MM/yyyy). Dòng 1 =
 * header, dữ liệu từ dòng 2.
 *
 * Mật khẩu: KHÔNG đặt trong Excel (rủi ro bảo mật khi file bị chuyển tay/
 * email) — hệ thống tự sinh mật khẩu tạm ngẫu nhiên mỗi dòng, hash lưu
 * password_hash bình thường, CHỈ trả plaintext 1 lần trong response của
 * chính lần gọi importEmployees() (KHÔNG lưu vào import_jobs, KHÔNG trả
 * lại khi gọi getJob() sau đó).
 *
 * Cột G (mã chức vụ) áp dụng UC-08 A5 (FR-HRM-06/UC-52) — dòng nào có mã
 * chức vụ hợp lệ thì tự động gán role mặc định của chức vụ đó; để trống
 * thì không tự gán role nào (Quản lý nhân sự gán tay sau qua UC-46), vì
 * không phải mọi chức vụ đều có role mặc định cấu hình sẵn.
 */
@Service
public class EmployeeBatchImportService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int HEADER_ROW_INDEX = 0;
    private static final int FIRST_DATA_ROW_INDEX = 1;
    private static final int COLUMN_COUNT = 10;
    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ImportJobRepository importJobRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PositionRoleSyncService positionRoleSyncService;

    public EmployeeBatchImportService(ImportJobRepository importJobRepository,
                                       EmployeeRepository employeeRepository,
                                       DepartmentRepository departmentRepository,
                                       PositionRepository positionRepository,
                                       UserRepository userRepository,
                                       PasswordEncoder passwordEncoder,
                                       PositionRoleSyncService positionRoleSyncService) {
        this.importJobRepository = importJobRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.positionRoleSyncService = positionRoleSyncService;
    }

    /** Main Flow bước 1-6. A1: file sai định dạng hoàn toàn → status=FAILED ngay, không xử lý dòng nào. */
    @Transactional
    public EmployeeBatchImportResponse importEmployees(MultipartFile file, Long actorUserId) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.employeeBatchImport.userNotFound", new Object[]{actorUserId}, "Không tìm thấy user id=" + actorUserId));

        ImportJob job = new ImportJob();
        job.setImportType(ImportJob.ImportType.EMPLOYEES);
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
                    String tempPassword = importRow(row, formatter, job, actorUserId);
                    successRows++;
                    Map<String, Object> credential = new HashMap<>();
                    credential.put("row", rowIndex + 1);
                    credential.put("username", cell(row, formatter, 1));
                    credential.put("temporaryPassword", tempPassword);
                    credential.put("fullName", cell(row, formatter, 0));
                    credentials.add(credential);
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
    public EmployeeBatchImportResponse getJob(Long id) {
        ImportJob job = importJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.employeeBatchImport.jobNotFound", new Object[]{id}, "Không tìm thấy import job id=" + id));
        return toResponse(job, List.of());
    }

    /**
     * File mẫu để nhập nhân sự theo lô (bổ sung ngoài SDD gốc, đã xác nhận
     * với người dùng 2026-07-24) — đúng 10 cột theo thứ tự importRow() đọc
     * phía trên. Không có cột mật khẩu (hệ thống tự sinh).
     */
    public byte[] buildTemplate() {
        List<String> headers = List.of(
                "Họ và tên*", "Username*", "Email", "Ngày sinh (dd/MM/yyyy)*", "Mã nhân sự*",
                "Loại nhân sự (Giáo viên/Nhân viên/Quản lý)*", "Mã chức vụ", "Mã phòng ban",
                "Miễn trừ chấm công/duyệt đơn (Có/Không)", "Ngày vào làm (dd/MM/yyyy)*");
        return ExcelExportHelper.buildWorkbook("Nhập nhân sự", headers, List.of());
    }

    /**
     * Xuất danh sách tài khoản nhân sự vừa tạo ra Excel (bổ sung ngoài SDD
     * gốc, đã xác nhận với người dùng 2026-07-24) — xem Javadoc tương ứng ở
     * StudentBatchImportService.buildAccountsExport().
     */
    public byte[] buildAccountsExport(AccountExportRequest request) {
        List<String> headers = List.of("Họ và tên", "Username", "Mật khẩu tạm");
        List<List<Object>> rows = request.accounts().stream()
                .<List<Object>>map(a -> List.of(
                        a.fullName() == null ? "" : a.fullName(), a.username(), a.temporaryPassword()))
                .toList();
        return ExcelExportHelper.buildWorkbook("Tài khoản nhân sự", headers, rows);
    }

    // ===================== Helpers =====================

    /** A2: 1 dòng lỗi/trùng lặp không chặn các dòng khác. Trả về mật khẩu tạm (plaintext) của dòng vừa tạo. */
    private String importRow(Row row, DataFormatter formatter, ImportJob job, Long actorUserId) {
        String fullName = cell(row, formatter, 0);
        String username = cell(row, formatter, 1);
        String email = cell(row, formatter, 2);
        String dobText = cell(row, formatter, 3);
        String employeeCode = cell(row, formatter, 4);
        String employeeTypeText = cell(row, formatter, 5);
        String positionCode = cell(row, formatter, 6);
        String departmentCode = cell(row, formatter, 7);
        String managementText = cell(row, formatter, 8);
        String hireDateText = cell(row, formatter, 9);

        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Thiếu họ và tên (cột A).");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Thiếu username (cột B).");
        }
        if (dobText == null || dobText.isBlank()) {
            throw new IllegalArgumentException("Thiếu ngày sinh (cột D).");
        }
        if (employeeCode == null || employeeCode.isBlank()) {
            throw new IllegalArgumentException("Thiếu mã nhân sự (cột E).");
        }
        if (employeeTypeText == null || employeeTypeText.isBlank()) {
            throw new IllegalArgumentException("Thiếu loại nhân sự (cột F).");
        }
        if (hireDateText == null || hireDateText.isBlank()) {
            throw new IllegalArgumentException("Thiếu ngày vào làm (cột J).");
        }
        LocalDate dob = parseDate(dobText, "Ngày sinh sai định dạng (cần dd/MM/yyyy): " + dobText);
        LocalDate hireDate = parseDate(hireDateText, "Ngày vào làm sai định dạng (cần dd/MM/yyyy): " + hireDateText);
        Employee.EmployeeType employeeType = parseEmployeeType(employeeTypeText.trim());

        if (userRepository.findByUsername(username.trim()).isPresent()) {
            throw new IllegalArgumentException("Username đã tồn tại: " + username);
        }
        if (employeeRepository.findByEmployeeCode(employeeCode.trim()).isPresent()) {
            throw new IllegalArgumentException("Mã nhân sự đã tồn tại: " + employeeCode);
        }
        String resolvedEmail;
        if (email == null || email.isBlank()) {
            resolvedEmail = generatePlaceholderEmail(job.getId(), row.getRowNum());
        } else {
            resolvedEmail = email.trim();
            if (userRepository.findByEmail(resolvedEmail).isPresent()) {
                throw new IllegalArgumentException("Email đã tồn tại: " + resolvedEmail);
            }
        }
        Department department = null;
        if (departmentCode != null && !departmentCode.isBlank()) {
            department = departmentRepository.findByCode(departmentCode.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phòng ban mã=" + departmentCode));
        }
        Position position = null;
        if (positionCode != null && !positionCode.isBlank()) {
            position = positionRepository.findByCode(positionCode.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chức vụ mã=" + positionCode));
        }

        String tempPassword = generateTempPassword();
        User user = new User();
        user.setUsername(username.trim());
        user.setEmail(resolvedEmail);
        user.setFullName(fullName.trim());
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setStatus(User.Status.ACTIVE);
        user = userRepository.save(user);

        Employee employee = new Employee();
        employee.setUser(user);
        employee.setEmployeeCode(employeeCode.trim());
        employee.setDateOfBirth(dob);
        employee.setEmployeeType(employeeType);
        employee.setPosition(position);
        employee.setDepartment(department);
        employee.setManagement(parseBoolean(managementText));
        employee.setDefaultShiftRequired(true);
        employee.setHireDate(hireDate);
        employeeRepository.save(employee);

        // UC-08 A5 (FR-HRM-06/UC-52) -- hồ sơ vừa tạo nên chưa từng có chức vụ (oldPositionId = null).
        positionRoleSyncService.sync(user, null, position, actorUserId);

        return tempPassword;
    }

    private LocalDate parseDate(String text, String errorMessage) {
        try {
            return LocalDate.parse(text.trim(), DATE_FORMAT);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private Employee.EmployeeType parseEmployeeType(String text) {
        return switch (text.toLowerCase()) {
            case "giáo viên", "giao vien", "teacher" -> Employee.EmployeeType.TEACHER;
            case "quản lý", "quan ly", "manager" -> Employee.EmployeeType.MANAGER;
            case "nhân viên", "nhan vien", "staff" -> Employee.EmployeeType.STAFF;
            default -> throw new IllegalArgumentException(
                    "Loại nhân sự không hợp lệ (cần Giáo viên/Nhân viên/Quản lý): " + text);
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

    private String generatePlaceholderEmail(Long jobId, int rowIndex) {
        return "impemp" + jobId + "-r" + rowIndex + "@placeholder.pps.edu.vn";
    }

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    private Map<String, Object> rowError(int rowNumber, String reason) {
        Map<String, Object> error = new HashMap<>();
        error.put("row", rowNumber);
        error.put("reason", reason);
        return error;
    }

    /** A1: file sai định dạng hoàn toàn — không xử lý dòng nào, đánh dấu FAILED ngay. */
    private EmployeeBatchImportResponse failJob(ImportJob job, String reason) {
        job.setStatus(ImportJob.Status.FAILED);
        job.setErrorSummary(List.of(rowError(0, reason)));
        job.setFinishedAt(OffsetDateTime.now());
        job = importJobRepository.save(job);
        return toResponse(job, List.of());
    }

    private EmployeeBatchImportResponse toResponse(ImportJob job, List<Map<String, Object>> credentials) {
        return new EmployeeBatchImportResponse(
                job.getId(), job.getSourceFileName(), job.getTotalRows(), job.getSuccessRows(), job.getFailedRows(),
                job.getStatus().name(), job.getErrorSummary(), credentials);
    }
}
