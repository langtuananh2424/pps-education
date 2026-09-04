package vn.com.pps.education.service;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.common.ExcelExportHelper;
import vn.com.pps.education.domain.ImportJob;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AccountExportRequest;
import vn.com.pps.education.dto.StudentBatchImportResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ImportJobRepository;
import vn.com.pps.education.repository.UserRepository;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
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
 *
 * Xử lý từng dòng (importRow) được tách sang StudentBatchImportRowService,
 * chạy transaction REQUIRES_NEW riêng — xem Javadoc class đó để biết lý do
 * (sự cố 500 UnexpectedRollbackException ngày 2026-09-03 khi tất cả các
 * dòng chạy chung 1 transaction với method này).
 */
@Service
public class StudentBatchImportService {

    private static final int HEADER_ROW_INDEX = 0;
    private static final int FIRST_DATA_ROW_INDEX = 1;
    private static final int COLUMN_COUNT = 8;

    private final ImportJobRepository importJobRepository;
    private final ImportJobCommitService importJobCommitService;
    private final UserRepository userRepository;
    private final StudentBatchImportRowService rowService;

    public StudentBatchImportService(ImportJobRepository importJobRepository,
                                      ImportJobCommitService importJobCommitService,
                                      UserRepository userRepository,
                                      StudentBatchImportRowService rowService) {
        this.importJobRepository = importJobRepository;
        this.importJobCommitService = importJobCommitService;
        this.userRepository = userRepository;
        this.rowService = rowService;
    }

    /**
     * Main Flow bước 1-6. A1: file sai định dạng hoàn toàn → status=FAILED
     * ngay, không xử lý dòng nào.
     *
     * KHÔNG đặt @Transactional ở method này (khác quy ước thường dùng "1 UC
     * = 1 transaction boundary" ở architecture.md) — cố tình, vì UC-35 A2
     * yêu cầu mỗi dòng là 1 đơn vị commit độc lập (rowService.importRow()
     * chạy REQUIRES_NEW). Bản ghi import_jobs được tạo bằng
     * importJobCommitService.save() (cũng REQUIRES_NEW) để đảm bảo COMMIT
     * NGAY trước khi vào vòng lặp — nếu không, transaction REQUIRES_NEW của
     * từng dòng (ghi class_enrollments.import_job_id, FK tới import_jobs.id)
     * sẽ không thấy được row job vừa tạo, dù method này có tự mở
     * @Transactional hay không (ambient transaction từ caller, VD test
     * @Transactional, vẫn có thể che khuất commit).
     */
    public StudentBatchImportResponse importStudents(MultipartFile file, Long actorUserId) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.studentBatchImport.userNotFoundById", new Object[]{actorUserId}, "Không tìm thấy user id=" + actorUserId));

        ImportJob job = new ImportJob();
        job.setImportType(ImportJob.ImportType.STUDENTS);
        job.setSourceFileName(file.getOriginalFilename() == null ? "unnamed.xlsx" : file.getOriginalFilename());
        job.setSourceFileUrl("in-memory://" + job.getSourceFileName());
        job.setUploadedBy(actor);
        job.setStatus(ImportJob.Status.PROCESSING);
        job.setStartedAt(OffsetDateTime.now());
        // REQUIRES_NEW (không phải importJobRepository.save() thường) — phải COMMIT NGAY để các
        // transaction REQUIRES_NEW của từng dòng bên dưới thấy được row này (FK import_job_id),
        // bất kể method này đang chạy trong ambient transaction nào của caller hay không.
        job = importJobCommitService.save(job);

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
                    StudentBatchImportRowService.RowCredential credential = rowService.importRow(row, formatter, job, actor);
                    successRows++;
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("row", rowIndex + 1);
                    entry.put("username", credential.username());
                    entry.put("temporaryPassword", credential.temporaryPassword());
                    entry.put("fullName", credential.fullName());
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
                .orElseThrow(() -> new ResourceNotFoundException("error.studentBatchImport.jobNotFoundById", new Object[]{id}, "Không tìm thấy import job id=" + id));
        return toResponse(job, List.of());
    }

    /**
     * File mẫu để nhập học theo lô (bổ sung ngoài SDD gốc, đã xác nhận với
     * người dùng 2026-07-24) — đúng 8 cột theo thứ tự
     * StudentBatchImportRowService.importRow() đọc, trường bắt buộc đánh
     * dấu {@code *} cuối tên cột. Không có mật khẩu (hệ thống tự sinh). Chỉ
     * header, không data mẫu.
     */
    public byte[] buildTemplate() {
        List<String> headers = List.of(
                "Họ và tên*", "Username*", "Ngày sinh (dd/MM/yyyy)*", "Giới tính (Nam/Nữ/Khác)",
                "Trường đang học", "Lớp đang học", "Mã lớp PPS*", "Mã học sinh*");
        return ExcelExportHelper.buildWorkbook("Nhập học", headers, List.of());
    }

    /**
     * Xuất danh sách tài khoản vừa tạo (username + mật khẩu tạm) ra Excel
     * (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-24) — FE
     * gửi lại nguyên {@code generatedCredentials} đã nhận từ importStudents(),
     * BE không lưu/tra cứu lại từ DB (không có nơi nào lưu mật khẩu
     * plaintext) nên chỉ xuất được trong cùng phiên vừa import.
     */
    public byte[] buildAccountsExport(AccountExportRequest request) {
        List<String> headers = List.of("Họ và tên", "Username", "Mật khẩu tạm");
        List<List<Object>> rows = request.accounts().stream()
                .<List<Object>>map(a -> List.of(
                        a.fullName() == null ? "" : a.fullName(), a.username(), a.temporaryPassword()))
                .toList();
        return ExcelExportHelper.buildWorkbook("Tài khoản học sinh", headers, rows);
    }

    // ===================== Helpers =====================

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
