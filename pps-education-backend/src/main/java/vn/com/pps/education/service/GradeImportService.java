package vn.com.pps.education.service;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.common.ExcelExportHelper;
import vn.com.pps.education.domain.GradeComponentSetup;
import vn.com.pps.education.domain.GradeEvaluationComponent;
import vn.com.pps.education.domain.GradeEvaluationResult;
import vn.com.pps.education.domain.ImportJob;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.EnterGradeEvaluationResultRequest;
import vn.com.pps.education.dto.EnterGradeRequest;
import vn.com.pps.education.dto.GradeImportResponse;
import vn.com.pps.education.dto.StudentResponse;
import vn.com.pps.education.exception.GradeImportColumnMismatchException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.GradeComponentSetupRepository;
import vn.com.pps.education.repository.GradeEvaluationComponentRepository;
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
 * V95 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): gắn theo
 * {@link GradeComponentSetup} (lớp + kỳ học + Giữa/Cuối kỳ) thay vì
 * gradePeriodId theo curriculum. Định dạng file .xlsx: dòng 1 = header,
 * dữ liệu từ dòng 2; cột A = mã học viên (students.student_code); các
 * cột sau = tên thành phần điểm (map với grade_evaluation_components.
 * name/code/skills.name của đúng setup, chuẩn hoá hoa-thường/khoảng
 * trắng/dấu tiếng Việt) hoặc cột đặc biệt Overall/Level/Nhận xét/Ghi chú
 * (ghi vào GradeEvaluationResult.overallScore/level/comment/note). Ô
 * trống = bỏ qua (không nhập điểm đó).
 *
 * Transaction: 1 @Transactional bọc toàn bộ vòng lặp (giống hệt pattern
 * EmployeeBatchImportService — xem lịch sử comment cũ về REQUIRES_NEW đã
 * revert). Validate hết toàn bộ điểm của 1 dòng theo max_score TRƯỚC khi
 * ghi bất kỳ điểm nào của dòng đó (xem importRow).
 */
@Service
public class GradeImportService {

    private static final int HEADER_ROW_INDEX = 0;
    private static final int FIRST_DATA_ROW_INDEX = 1;
    private static final Set<String> OVERALL_ALIASES = Set.of("overall", "tong diem", "diem tong", "diem tong ket");
    private static final Set<String> LEVEL_ALIASES = Set.of("level", "cap do", "trinh do", "xep loai");
    private static final Set<String> COMMENT_ALIASES = Set.of("nhan xet");
    private static final Set<String> NOTE_ALIASES = Set.of("ghi chu");
    /**
     * Cột hiển thị/tham chiếu (Học Kỳ, Họ và tên, Ngày sinh, Lớp) do
     * buildTemplate() tự thêm vào file mẫu cho dễ đọc (bổ sung ngoài SDD
     * gốc, đã xác nhận với người dùng) — KHÔNG map vào điểm nào,
     * mapHeader() phải bỏ qua (không coi là "cột không khớp").
     */
    private static final Set<String> IGNORED_ALIASES = Set.of(
            "hoc ky", "ma hs", "ma hoc sinh", "ho va ten", "full name", "ten hoc sinh",
            "ngay sinh", "lop", "ten lop", "class");

    private final ImportJobRepository importJobRepository;
    private final GradeComponentSetupRepository gradeComponentSetupRepository;
    private final GradeEvaluationComponentRepository gradeEvaluationComponentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final GradeService gradeService;

    public GradeImportService(ImportJobRepository importJobRepository,
                              GradeComponentSetupRepository gradeComponentSetupRepository,
                              GradeEvaluationComponentRepository gradeEvaluationComponentRepository,
                              SchoolClassRepository schoolClassRepository,
                              StudentRepository studentRepository,
                              UserRepository userRepository,
                              GradeService gradeService) {
        this.importJobRepository = importJobRepository;
        this.gradeComponentSetupRepository = gradeComponentSetupRepository;
        this.gradeEvaluationComponentRepository = gradeEvaluationComponentRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.gradeService = gradeService;
    }

    /**
     * Main Flow bước 1-4. A1: cột không khớp → GradeImportColumnMismatchException
     * (400), KHÔNG tạo import_job, không ghi dòng nào. A2: lỗi từng dòng ghi
     * vào error_summary, không chặn dòng khác. A3: file sai định dạng hoàn
     * toàn → import_job FAILED ngay.
     */
    @Transactional
    public GradeImportResponse importGrades(Long classId, Long setupId, MultipartFile file, Long actorUserId) {
        loadAndValidate(classId, setupId); // validate setup thuộc đúng lớp — ném lỗi sớm nếu không khớp
        // UC-53 Precondition (mở rộng, xem GradeService#requireCanEnterGrades) — dùng chung logic với UC-19, không lặp lại.
        gradeService.requireCanEnterGrades(classId, actorUserId);
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        List<GradeEvaluationComponent> components = gradeEvaluationComponentRepository.findByGradeComponentSetupIdOrderByDisplayOrder(setupId);

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
            ColumnMapping mapping = mapHeader(headerRow, formatter, components, setupId);

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
                    importRow(row, formatter, mapping, classId, setupId, job, actorUserId);
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
     * với người dùng) — cột A = mã học sinh (bắt buộc giữ đúng vị trí đầu
     * tiên, importRow() đọc theo vị trí này), các cột Học Kỳ/Họ tên/Ngày
     * sinh/Lớp chỉ để đọc (mapHeader() bỏ qua qua IGNORED_ALIASES), các
     * cột sau = đúng tên từng thành phần điểm của setup + Overall/Level/
     * Nhận xét/Ghi chú (V95). Điền sẵn 1 dòng / học sinh theo roster
     * (class_enrollments active tại rosterAsOfDate của setup — xem
     * GradeService#getRoster), cột điểm để trống sẵn sàng nhập — cùng
     * quyền với importGrades().
     */
    @Transactional(readOnly = true)
    public byte[] buildTemplate(Long classId, Long setupId, Long actorUserId) {
        gradeService.requireCanEnterGrades(classId, actorUserId);
        GradeComponentSetup setup = loadAndValidate(classId, setupId);
        List<GradeEvaluationComponent> components = gradeEvaluationComponentRepository.findByGradeComponentSetupIdOrderByDisplayOrder(setupId);

        // Cột A PHẢI là mã học sinh (importRow() đọc cố định vị trí này, giống mọi
        // file GV tự soạn tay) — Học Kỳ/Họ tên/Ngày sinh/Lớp chỉ mang tính hiển thị,
        // đặt SAU cột A, không được đặt trước.
        List<String> headers = new ArrayList<>();
        headers.add("Mã HS*");
        headers.add("Học Kỳ");
        headers.add("Họ và tên");
        headers.add("Ngày sinh");
        headers.add("Lớp");
        components.forEach(component -> headers.add(component.getName()));
        headers.add("Overall");
        headers.add("Level");
        headers.add("Nhận xét");
        headers.add("Ghi chú");

        List<StudentResponse> roster = gradeService.getRoster(setupId);
        List<List<Object>> rows = new ArrayList<>();
        for (StudentResponse student : roster) {
            List<Object> row = new ArrayList<>();
            row.add(student.studentCode());
            row.add(setup.getAcademicTerm().getName() + " - " + setup.getEvaluationType());
            row.add(student.fullName());
            row.add(student.dateOfBirth());
            row.add(setup.getSchoolClass().getName());
            components.forEach(component -> row.add(null));
            row.add(null); // Overall
            row.add(null); // Level
            row.add(null); // Nhận xét
            row.add(null); // Ghi chú
            rows.add(row);
        }
        return ExcelExportHelper.buildWorkbook("Nhập điểm", headers, rows);
    }

    // ===================== Helpers =====================

    private record ColumnMapping(Map<Integer, GradeEvaluationComponent> componentByColumn,
                                 Integer overallColumn, Integer levelColumn, Integer commentColumn,
                                 Integer noteColumn, int columnCount) {
    }

    /** Dùng chung cho importGrades() và buildTemplate() — tránh lặp lại lookup + check khớp lớp. */
    private GradeComponentSetup loadAndValidate(Long classId, Long setupId) {
        SchoolClass schoolClass = schoolClassRepository.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học id=" + classId));
        GradeComponentSetup setup = gradeComponentSetupRepository.findById(setupId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy setup sổ điểm id=" + setupId));
        if (!setup.getSchoolClass().getId().equals(schoolClass.getId())) {
            throw new IllegalArgumentException(
                    "Setup sổ điểm này không thuộc lớp đang chọn.");
        }
        return setup;
    }

    /** Main Flow bước 2, A1 — cột nào (ngoài cột A) không khớp thì dừng toàn bộ, liệt kê rõ. */
    private ColumnMapping mapHeader(Row headerRow, DataFormatter formatter, List<GradeEvaluationComponent> components,
                                    Long setupId) {
        Map<String, GradeEvaluationComponent> componentByKey = new HashMap<>();
        for (GradeEvaluationComponent component : components) {
            componentByKey.put(normalize(component.getName()), component);
            componentByKey.putIfAbsent(normalize(component.getCode().name()), component);
            if (component.getSkill() != null) {
                componentByKey.putIfAbsent(normalize(component.getSkill().getName()), component);
            }
        }

        Map<Integer, GradeEvaluationComponent> componentByColumn = new LinkedHashMap<>();
        Integer overallColumn = null;
        Integer levelColumn = null;
        Integer commentColumn = null;
        Integer noteColumn = null;
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
            } else if (COMMENT_ALIASES.contains(key)) {
                commentColumn = col;
            } else if (NOTE_ALIASES.contains(key)) {
                noteColumn = col;
            } else if (componentByKey.containsKey(key)) {
                componentByColumn.put(col, componentByKey.get(key));
            } else if (IGNORED_ALIASES.contains(key)) {
                // Cột hiển thị (Học Kỳ/Họ và tên/Ngày sinh/Lớp) do buildTemplate() tự thêm — bỏ qua, không phải lỗi.
            } else {
                unmatched.add(header);
            }
        }
        if (!unmatched.isEmpty()) {
            throw new GradeImportColumnMismatchException(
                    "Các cột không khớp thành phần điểm nào của setup sổ điểm này: "
                            + String.join(", ", unmatched)
                            + ". Bổ sung thành phần điểm hoặc sửa lại tên cột rồi import lại.");
        }
        return new ColumnMapping(componentByColumn, overallColumn, levelColumn, commentColumn, noteColumn, lastCell);
    }

    /**
     * A2 — 1 dòng lỗi không chặn dòng khác. Validate TOÀN BỘ điểm của dòng
     * (parse + trong khoảng [0,max_score]) trước khi ghi bất kỳ điểm nào —
     * tránh trường hợp dòng bị báo lỗi nhưng vẫn lọt 1 phần ghi xuống DB.
     */
    private void importRow(Row row, DataFormatter formatter, ColumnMapping mapping,
                           Long classId, Long setupId, ImportJob job, Long actorUserId) {
        String studentCode = cell(row, formatter, 0);
        if (studentCode == null || studentCode.isBlank()) {
            throw new IllegalArgumentException("Thiếu mã học viên (cột A).");
        }
        Student student = studentRepository.findByStudentCode(studentCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy học viên mã=" + studentCode.trim()));

        Map<GradeEvaluationComponent, BigDecimal> scores = new LinkedHashMap<>();
        for (Map.Entry<Integer, GradeEvaluationComponent> entry : mapping.componentByColumn().entrySet()) {
            String raw = cell(row, formatter, entry.getKey());
            if (raw == null || raw.isBlank()) {
                continue;
            }
            GradeEvaluationComponent component = entry.getValue();
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
        String level = blankToNull(mapping.levelColumn() == null ? null : cell(row, formatter, mapping.levelColumn()));
        String comment = blankToNull(mapping.commentColumn() == null ? null : cell(row, formatter, mapping.commentColumn()));
        String note = blankToNull(mapping.noteColumn() == null ? null : cell(row, formatter, mapping.noteColumn()));
        if (scores.isEmpty() && overall == null && level == null && comment == null && note == null) {
            throw new IllegalArgumentException("Dòng không có điểm nào để nhập.");
        }

        for (Map.Entry<GradeEvaluationComponent, BigDecimal> entry : scores.entrySet()) {
            gradeService.enterGrade(classId, entry.getKey().getId(),
                    new EnterGradeRequest(student.getId(), entry.getValue(), false, null), actorUserId);
        }
        if (overall != null || level != null || comment != null || note != null) {
            gradeService.upsertEvaluationResult(classId, student.getId(), setupId,
                    new EnterGradeEvaluationResultRequest(overall, null, level, comment, note, null), actorUserId,
                    GradeEvaluationResult.Source.EXCEL_IMPORT, job);
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    /**
     * GV tự định dạng ô điểm thành "Phần trăm" trong Excel (VD setup dùng thang PERCENT) —
     * DataFormatter trả về chuỗi có dấu "%" (VD "80,00%", giá trị lưu trong ô vẫn là 0.8
     * nhưng DataFormatter đã tự nhân 100 lúc format hiển thị) — bỏ dấu "%" rồi parse tiếp,
     * không cần nhân lại lần nữa.
     */
    private BigDecimal parseScore(String raw, String columnName) {
        String cleaned = raw.trim().replace(',', '.');
        if (cleaned.endsWith("%")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        try {
            return new BigDecimal(cleaned);
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
