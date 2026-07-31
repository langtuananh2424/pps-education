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
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.ClassEnrollmentBatchImportResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ImportJobRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-65: Ghi danh học sinh theo lô (bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng 2026-07-31) — Quản lý lớp học → chọn 1 lớp → tab Học sinh
 * chưa có cách ghi danh hàng loạt qua Excel (trước đó chỉ có tích chọn tay
 * từng em qua danh sách toàn hệ thống, xem EnrollStudentForm ở
 * ClassDetailPanel.tsx). Khác UC-35/UC-50: KHÔNG tạo học sinh/tài khoản
 * mới — mọi dòng BẮT BUỘC khớp 1 học sinh ĐÃ TỒN TẠI SẴN (tra theo
 * student_code), chỉ tạo bản ghi class_enrollments cho ĐÚNG 1 lớp đã chọn
 * (classId truyền qua path, không phải cột trong file).
 *
 * Định dạng file Excel (.xlsx) — 2 cột: A=Mã học sinh (bắt buộc), B=Ngày
 * ghi danh (yyyy-MM-dd, tùy chọn — để trống mặc định hôm nay, khớp định
 * dạng cột "Ngày" của StudentCommentService thay vì dd/MM/yyyy của
 * StudentBatchImportService, vì đây là ngày sự kiện chứ không phải ngày
 * sinh). Dòng 1 = header, dữ liệu từ dòng 2.
 *
 * Tái dùng NGUYÊN VẸN ClassService.enroll() cho từng dòng — không viết lại
 * validate (học sinh đã ghi danh ACTIVE trong lớp, lớp tồn tại...), khớp
 * đúng bất biến "1 nghiệp vụ chỉ 1 nơi định nghĩa".
 */
@Service
public class ClassEnrollmentBatchImportService {

    private static final int HEADER_ROW_INDEX = 0;
    private static final int FIRST_DATA_ROW_INDEX = 1;
    private static final int COLUMN_COUNT = 2;
    private static final int COL_STUDENT_CODE = 0;
    private static final int COL_ENROLLED_DATE = 1;

    private final ImportJobRepository importJobRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final ClassService classService;

    public ClassEnrollmentBatchImportService(ImportJobRepository importJobRepository,
                                              StudentRepository studentRepository,
                                              UserRepository userRepository,
                                              ClassService classService) {
        this.importJobRepository = importJobRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.classService = classService;
    }

    /** Main Flow: đọc file, ghi danh từng dòng vào ĐÚNG classId truyền vào. A1: file sai định dạng → FAILED ngay. A2: 1 dòng lỗi không chặn dòng khác. */
    @Transactional
    public ClassEnrollmentBatchImportResponse importEnrollments(Long classId, MultipartFile file, Long actorUserId) {
        // Class không tồn tại là lỗi của chính request (path variable), không phải lỗi 1 dòng trong file —
        // để ném thẳng ra ngoài (404), không nuốt vào errorSummary.
        classService.getById(classId);
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        ImportJob job = new ImportJob();
        job.setImportType(ImportJob.ImportType.CLASS_ENROLLMENTS);
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
                    importRow(classId, row, formatter, actorUserId);
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
    public ClassEnrollmentBatchImportResponse getJob(Long id) {
        ImportJob job = importJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy import job id=" + id));
        return toResponse(job);
    }

    /** File mẫu ghi danh theo lô (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31) — đúng 2 cột theo thứ tự importRow() đọc. */
    public byte[] buildTemplate() {
        List<String> headers = List.of("Mã học sinh*", "Ngày ghi danh (yyyy-MM-dd)");
        List<String> notes = List.of(
                "Mã học sinh (cột A) phải khớp đúng 1 học sinh ĐÃ TỒN TẠI SẴN trong hệ thống — không tạo học sinh mới.",
                "Ngày ghi danh (cột B) để trống thì mặc định lấy ngày hôm nay.",
                "Học sinh đã ghi danh ACTIVE sẵn trong lớp này sẽ bị báo lỗi ở dòng tương ứng, các dòng khác không bị ảnh hưởng.");
        return ExcelExportHelper.buildWorkbook("Ghi danh học sinh", headers, List.of(), notes);
    }

    // ===================== Helpers =====================

    private void importRow(Long classId, Row row, DataFormatter formatter, Long actorUserId) {
        String studentCode = cell(row, formatter, COL_STUDENT_CODE);
        String enrolledDateText = cell(row, formatter, COL_ENROLLED_DATE);

        if (studentCode == null || studentCode.isBlank()) {
            throw new IllegalArgumentException("Thiếu mã học sinh (cột A).");
        }
        Student student = studentRepository.findByStudentCode(studentCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy học sinh mã=" + studentCode));

        LocalDate enrolledDate;
        if (enrolledDateText == null || enrolledDateText.isBlank()) {
            enrolledDate = LocalDate.now();
        } else {
            try {
                enrolledDate = LocalDate.parse(enrolledDateText.trim());
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("Ngày ghi danh sai định dạng (cần yyyy-MM-dd): " + enrolledDateText);
            }
        }

        classService.enroll(classId, new EnrollStudentRequest(student.getId(), enrolledDate), actorUserId);
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
    private ClassEnrollmentBatchImportResponse failJob(ImportJob job, String reason) {
        job.setStatus(ImportJob.Status.FAILED);
        job.setErrorSummary(List.of(rowError(0, reason)));
        job.setFinishedAt(OffsetDateTime.now());
        job = importJobRepository.save(job);
        return toResponse(job);
    }

    private ClassEnrollmentBatchImportResponse toResponse(ImportJob job) {
        return new ClassEnrollmentBatchImportResponse(
                job.getId(), job.getSourceFileName(), job.getTotalRows(), job.getSuccessRows(), job.getFailedRows(),
                job.getStatus().name(), job.getErrorSummary());
    }
}
