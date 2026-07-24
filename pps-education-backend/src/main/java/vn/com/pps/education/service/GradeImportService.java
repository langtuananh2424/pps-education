package vn.com.pps.education.service;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.common.ExcelExportHelper;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.GradeComponent;
import vn.com.pps.education.domain.GradePeriod;
import vn.com.pps.education.domain.GradePeriodResult;
import vn.com.pps.education.domain.ImportJob;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.EnterGradePeriodResultRequest;
import vn.com.pps.education.dto.EnterGradeRequest;
import vn.com.pps.education.dto.GradeImportResponse;
import vn.com.pps.education.exception.GradeImportColumnMismatchException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.GradeComponentRepository;
import vn.com.pps.education.repository.GradePeriodRepository;
import vn.com.pps.education.repository.ImportJobRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * UC-53: Nhập điểm thi qua Excel (FR-ACA-03, bổ sung ngoài SDD gốc, đã
 * xác nhận với người dùng). Xem docs/uc/phan-he-06-hoc-thuat.md.
 *
 * Service riêng (không nhét vào GradeService) theo SRP — cùng lý do
 * EmployeeBatchImportService tách khỏi EmployeeService (UC-51).
 *
 * Định dạng file .xlsx: dòng 1 = header, dữ liệu từ dòng 2; cột A = mã
 * học viên (students.student_code); các cột sau = tên thành phần điểm
 * (map với grade_components.name/code/skills.name của đúng kỳ đánh giá,
 * chuẩn hoá hoa-thường/khoảng trắng/dấu tiếng Việt) hoặc cột đặc biệt
 * Overall ("overall"/"tổng điểm"/"điểm tổng") và Level ("level"/
 * "cấp độ"/"trình độ"). Ô trống = bỏ qua (không nhập điểm đó).
 *
 * Transaction: 1 @Transactional bọc toàn bộ vòng lặp (giống hệt pattern
 * EmployeeBatchImportService — đã thử tách mỗi dòng thành 1 transaction
 * REQUIRES_NEW riêng để cô lập lỗi tốt hơn, nhưng revert lại vì
 * REQUIRES_NEW mở connection MỚI nên không thấy được dữ liệu chưa commit
 * của transaction test bao ngoài (@Transactional rollback-per-test) —
 * lộ ra qua GradeImportServiceTest thất bại toàn bộ dù dữ liệu hợp lệ).
 * Để tránh 1 dòng lỗi vẫn để lọt 1 phần ghi xuống DB (VD 2 kỹ năng, kỹ
 * năng 1 ghi xong thì kỹ năng 2 mới lỗi điểm ngoài khoảng), validate hết
 * toàn bộ điểm của 1 dòng theo max_score TRƯỚC khi gọi ghi bất kỳ điểm
 * nào của dòng đó (xem validateScoresInRange).
 */
@Service
public class GradeImportService {

    private static final int HEADER_ROW_INDEX = 0;
    private static final int FIRST_DATA_ROW_INDEX = 1;
    private static final Set<String> OVERALL_ALIASES = Set.of("overall", "tong diem", "diem tong", "diem tong ket");
    private static final Set<String> LEVEL_ALIASES = Set.of("level", "cap do", "trinh do", "xep loai");
    /**
     * Cột hiển thị/tham chiếu (Họ và tên, Lớp) do buildTemplate() tự thêm
     * vào file mẫu cho dễ đọc (bổ sung ngoài SDD gốc, đã xác nhận với người
     * dùng 2026-07-24) — KHÔNG map vào điểm nào, mapHeader() phải bỏ qua
     * (không coi là "cột không khớp"), nếu không thì chính file mẫu tự
     * sinh ra, điền điểm rồi upload lại sẽ tự báo lỗi mismatch.
     */
    private static final Set<String> IGNORED_ALIASES = Set.of(
            "ho va ten", "full name", "ten hoc sinh", "lop", "ten lop", "class");

    private final ImportJobRepository importJobRepository;
    private final GradePeriodRepository gradePeriodRepository;
    private final GradeComponentRepository gradeComponentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final GradeService gradeService;

    public GradeImportService(ImportJobRepository importJobRepository,
                              GradePeriodRepository gradePeriodRepository,
                              GradeComponentRepository gradeComponentRepository,
                              SchoolClassRepository schoolClassRepository,
                              StudentRepository studentRepository,
                              UserRepository userRepository,
                              ClassEnrollmentRepository classEnrollmentRepository,
                              GradeService gradeService) {
        this.importJobRepository = importJobRepository;
        this.gradePeriodRepository = gradePeriodRepository;
        this.gradeComponentRepository = gradeComponentRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.gradeService = gradeService;
    }

    /**
     * Main Flow bước 1-4. A1: cột không khớp → GradeImportColumnMismatchException
     * (400), KHÔNG tạo import_job, không ghi dòng nào. A2: lỗi từng dòng ghi
     * vào error_summary, không chặn dòng khác. A3: file sai định dạng hoàn
     * toàn → import_job FAILED ngay.
     */
    @Transactional
    public GradeImportResponse importGrades(Long classId, Long gradePeriodId, MultipartFile file, Long actorUserId) {
        loadAndValidate(classId, gradePeriodId);
        // UC-53 Precondition (mở rộng, xem GradeService#requireCanEnterGrades) — dùng chung logic với UC-19, không lặp lại.
        gradeService.requireCanEnterGrades(classId, actorUserId);
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        List<GradeComponent> components = gradeComponentRepository.findByGradePeriodIdOrderByDisplayOrder(gradePeriodId);

        ImportJob job = null;
        try (InputStream inputStream = file.getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            Row headerRow = sheet.getRow(HEADER_ROW_INDEX);
            if (headerRow == null) {
                return failJob(createJob(file, actor), "File rỗng hoặc thiếu dòng tiêu đề.");
            }

            // Main Flow bước 2 + A1 — map header TRƯỚC khi tạo import_job.
            ColumnMapping mapping = mapHeader(headerRow, formatter, components, gradePeriodId);

            job = createJob(file, actor);
            List<Map<String, Object>> errors = new ArrayList<>();
            int totalRows = 0;
            int successRows = 0;
            for (int rowIndex = FIRST_DATA_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row, formatter, mapping.columnCount())) {
                    continue;
                }
                totalRows++;
                try {
                    importRow(row, formatter, mapping, classId, gradePeriodId, job, actorUserId);
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
        } catch (GradeImportColumnMismatchException ex) {
            throw ex; // A1 — không tạo import_job, không ghi dòng nào.
        } catch (IOException | RuntimeException ex) {
            // A3 — không mở được file .xlsx (hoặc lỗi hệ thống ngoài dự kiến).
            return failJob(job != null ? job : createJob(file, actor),
                    "File sai định dạng Excel (.xlsx): " + ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public GradeImportResponse getJob(Long id) {
        ImportJob job = importJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy import job id=" + id));
        return toResponse(job);
    }

    /**
     * File mẫu để nhập điểm qua Excel (bổ sung ngoài SDD gốc, đã xác nhận
     * với người dùng 2026-07-24) — cột A = mã học sinh (bắt buộc giữ đúng
     * vị trí đầu tiên, importRow() đọc theo vị trí này), cột B/C = họ tên/
     * lớp (chỉ để đọc, mapHeader() bỏ qua qua IGNORED_ALIASES), các cột sau
     * = đúng tên từng thành phần điểm của kỳ đánh giá + Overall/Level.
     * Điền sẵn 1 dòng / học sinh đang ghi danh ACTIVE của lớp, cột điểm để
     * trống sẵn sàng nhập — cùng quyền với importGrades() (Giáo viên được
     * phân công/Trưởng phòng đào tạo/Quản lý điểm trường phụ trách site).
     */
    @Transactional(readOnly = true)
    public byte[] buildTemplate(Long classId, Long gradePeriodId, Long actorUserId) {
        gradeService.requireCanEnterGrades(classId, actorUserId);
        ClassAndPeriod validated = loadAndValidate(classId, gradePeriodId);
        List<GradeComponent> components = gradeComponentRepository.findByGradePeriodIdOrderByDisplayOrder(gradePeriodId);

        List<String> headers = new ArrayList<>();
        headers.add("Mã học sinh*");
        headers.add("Họ và tên");
        headers.add("Lớp");
        components.forEach(component -> headers.add(component.getName()));
        headers.add("Overall");
        headers.add("Level");

        List<ClassEnrollment> enrollments =
                classEnrollmentRepository.findBySchoolClassIdAndStatus(classId, ClassEnrollment.Status.ACTIVE);
        List<List<Object>> rows = new ArrayList<>();
        for (ClassEnrollment enrollment : enrollments) {
            Student student = enrollment.getStudent();
            List<Object> row = new ArrayList<>();
            row.add(student.getStudentCode());
            row.add(student.getUser().getFullName());
            row.add(validated.schoolClass().getName());
            components.forEach(component -> row.add(null));
            row.add(null); // Overall
            row.add(null); // Level
            rows.add(row);
        }
        return ExcelExportHelper.buildWorkbook("Nhập điểm", headers, rows);
    }

    // ===================== Helpers =====================

    private record ColumnMapping(Map<Integer, GradeComponent> componentByColumn,
                                 Integer overallColumn, Integer levelColumn, int columnCount) {
    }

    private record ClassAndPeriod(SchoolClass schoolClass, GradePeriod period) {}

    /** Dùng chung cho importGrades() và buildTemplate() — tránh lặp lại lookup + check khớp curriculum. */
    private ClassAndPeriod loadAndValidate(Long classId, Long gradePeriodId) {
        SchoolClass schoolClass = schoolClassRepository.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học id=" + classId));
        GradePeriod period = gradePeriodRepository.findById(gradePeriodId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ đánh giá id=" + gradePeriodId));
        if (!period.getCurriculum().getId().equals(schoolClass.getCurriculum().getId())) {
            throw new IllegalArgumentException(
                    "Kỳ đánh giá id=" + gradePeriodId + " không thuộc khung chương trình của lớp id=" + classId + ".");
        }
        return new ClassAndPeriod(schoolClass, period);
    }

    /** Main Flow bước 2, A1 — cột nào (ngoài cột A) không khớp thì dừng toàn bộ, liệt kê rõ. */
    private ColumnMapping mapHeader(Row headerRow, DataFormatter formatter, List<GradeComponent> components,
                                    Long gradePeriodId) {
        Map<String, GradeComponent> componentByKey = new HashMap<>();
        for (GradeComponent component : components) {
            componentByKey.put(normalize(component.getName()), component);
            componentByKey.putIfAbsent(normalize(component.getCode().name()), component);
            if (component.getSkill() != null) {
                componentByKey.putIfAbsent(normalize(component.getSkill().getName()), component);
            }
        }

        Map<Integer, GradeComponent> componentByColumn = new LinkedHashMap<>();
        Integer overallColumn = null;
        Integer levelColumn = null;
        List<String> unmatched = new ArrayList<>();
        int lastCell = headerRow.getLastCellNum();
        for (int col = 1; col < lastCell; col++) {
            String header = cell(headerRow, formatter, col);
            if (header == null || header.isBlank()) {
                continue;
            }
            String key = normalize(header);
            if (OVERALL_ALIASES.contains(key)) {
                overallColumn = col;
            } else if (LEVEL_ALIASES.contains(key)) {
                levelColumn = col;
            } else if (componentByKey.containsKey(key)) {
                componentByColumn.put(col, componentByKey.get(key));
            } else if (IGNORED_ALIASES.contains(key)) {
                // Cột hiển thị (Họ và tên/Lớp) do buildTemplate() tự thêm — bỏ qua, không phải lỗi.
            } else {
                unmatched.add(header);
            }
        }
        if (!unmatched.isEmpty()) {
            throw new GradeImportColumnMismatchException(
                    "Các cột không khớp thành phần điểm nào của kỳ đánh giá id=" + gradePeriodId + ": "
                            + String.join(", ", unmatched)
                            + ". Bổ sung thành phần điểm (UC-16/A2) hoặc sửa lại tên cột rồi import lại.");
        }
        return new ColumnMapping(componentByColumn, overallColumn, levelColumn, lastCell);
    }

    /**
     * A2 — 1 dòng lỗi không chặn dòng khác. Validate TOÀN BỘ điểm của dòng
     * (parse + trong khoảng [0,max_score]) trước khi ghi bất kỳ điểm nào —
     * tránh trường hợp dòng bị báo lỗi nhưng vẫn lọt 1 phần ghi xuống DB
     * (VD kỹ năng 1 ghi xong thì kỹ năng 2 mới phát hiện điểm ngoài khoảng).
     */
    private void importRow(Row row, DataFormatter formatter, ColumnMapping mapping,
                           Long classId, Long gradePeriodId, ImportJob job, Long actorUserId) {
        String studentCode = cell(row, formatter, 0);
        if (studentCode == null || studentCode.isBlank()) {
            throw new IllegalArgumentException("Thiếu mã học viên (cột A).");
        }
        Student student = studentRepository.findByStudentCode(studentCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy học viên mã=" + studentCode.trim()));

        Map<GradeComponent, BigDecimal> scores = new LinkedHashMap<>();
        for (Map.Entry<Integer, GradeComponent> entry : mapping.componentByColumn().entrySet()) {
            String raw = cell(row, formatter, entry.getKey());
            if (raw == null || raw.isBlank()) {
                continue;
            }
            GradeComponent component = entry.getValue();
            BigDecimal score = parseScore(raw, component.getName());
            if (score.signum() < 0 || score.compareTo(component.getMaxScore()) > 0) {
                throw new IllegalArgumentException(
                        "Điểm '" + component.getName() + "'=" + score + " ngoài khoảng [0, " + component.getMaxScore() + "].");
            }
            scores.put(component, score);
        }
        BigDecimal overall = null;
        if (mapping.overallColumn() != null) {
            String raw = cell(row, formatter, mapping.overallColumn());
            if (raw != null && !raw.isBlank()) {
                overall = parseScore(raw, "Overall");
            }
        }
        String level = mapping.levelColumn() == null ? null : cell(row, formatter, mapping.levelColumn());
        if (level != null && level.isBlank()) {
            level = null;
        }
        if (scores.isEmpty() && overall == null && level == null) {
            throw new IllegalArgumentException("Dòng không có điểm nào để nhập.");
        }

        for (Map.Entry<GradeComponent, BigDecimal> entry : scores.entrySet()) {
            gradeService.enterGrade(classId, entry.getKey().getId(),
                    new EnterGradeRequest(student.getId(), entry.getValue(), false, null), actorUserId);
        }
        if (overall != null || level != null) {
            gradeService.upsertPeriodResult(classId, student.getId(), gradePeriodId,
                    new EnterGradePeriodResultRequest(overall, null, level), actorUserId,
                    GradePeriodResult.Source.EXCEL_IMPORT, job);
        }
    }

    private BigDecimal parseScore(String raw, String columnName) {
        try {
            return new BigDecimal(raw.trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Điểm không hợp lệ ở cột '" + columnName + "': " + raw);
        }
    }

    /** Chuẩn hoá so khớp header: trim, lowercase, gộp khoảng trắng, bỏ dấu tiếng Việt. */
    private String normalize(String text) {
        String lowered = text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        String decomposed = Normalizer.normalize(lowered, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return decomposed.replace('đ', 'd');
    }

    private String cell(Row row, DataFormatter formatter, int index) {
        var cell = row.getCell(index);
        return cell == null ? null : formatter.formatCellValue(cell).trim();
    }

    private boolean isBlankRow(Row row, DataFormatter formatter, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            String value = cell(row, formatter, i);
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private ImportJob createJob(MultipartFile file, User actor) {
        ImportJob job = new ImportJob();
        job.setImportType(ImportJob.ImportType.GRADES);
        job.setSourceFileName(file.getOriginalFilename() == null ? "unnamed.xlsx" : file.getOriginalFilename());
        job.setSourceFileUrl("in-memory://" + job.getSourceFileName());
        job.setUploadedBy(actor);
        job.setStatus(ImportJob.Status.PROCESSING);
        job.setStartedAt(OffsetDateTime.now());
        return importJobRepository.save(job);
    }

    private Map<String, Object> rowError(int rowNumber, String reason) {
        Map<String, Object> error = new HashMap<>();
        error.put("row", rowNumber);
        error.put("reason", reason);
        return error;
    }

    /** A3 — file sai định dạng hoàn toàn, đánh dấu FAILED ngay, không xử lý dòng nào. */
    private GradeImportResponse failJob(ImportJob job, String reason) {
        job.setStatus(ImportJob.Status.FAILED);
        job.setErrorSummary(List.of(rowError(0, reason)));
        job.setFinishedAt(OffsetDateTime.now());
        job = importJobRepository.save(job);
        return toResponse(job);
    }

    private GradeImportResponse toResponse(ImportJob job) {
        return new GradeImportResponse(
                job.getId(), job.getSourceFileName(), job.getTotalRows(), job.getSuccessRows(),
                job.getFailedRows(), job.getStatus().name(), job.getErrorSummary());
    }
}
