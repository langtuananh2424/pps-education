package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.GeneratedReport;
import vn.com.pps.education.domain.ReportTemplate;
import vn.com.pps.education.domain.ReportTemplateFieldMapping;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.GeneratedReportResponse;
import vn.com.pps.education.dto.ReportPeriodSelector;
import vn.com.pps.education.exception.MissingReportDataException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.GeneratedReportRepository;
import vn.com.pps.education.repository.ReportTemplateFieldMappingRepository;
import vn.com.pps.education.repository.ReportTemplateRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * UC-68: Xuất báo cáo từ mẫu (FR-ACA-08 — bổ sung ngoài SDD gốc, đã xác
 * nhận với người dùng). Xem docs/uc/phan-he-06-hoc-thuat.md để biết đầy đủ
 * Main Flow/Alternate Flow/Postcondition.
 *
 * 3 phạm vi xuất (xem Javadoc GenerateReportRequest):
 * <ul>
 *   <li>SINGLE — 1 tài liệu/học sinh (TRANSCRIPT, STUDENT_PROFILE).</li>
 *   <li>BULK_CLASS — nhiều tài liệu/học sinh của 1 lớp, gộp ZIP.</li>
 *   <li>CLASS_SESSION — 1 tài liệu/buổi học, dùng bảng động liệt kê học
 *       sinh (DAILY_REPORT — xem {@link DocxMergeEngine} phần xử lý
 *       {@code [[TABLE:x]]}).</li>
 * </ul>
 * GRADE_REPORT chưa triển khai resolver — dùng lại cơ chế period selector
 * của TranscriptReportDataResolver khi cần.
 */
@Service
public class ReportGenerationService {

    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final ReportTemplateRepository reportTemplateRepository;
    private final ReportTemplateFieldMappingRepository fieldMappingRepository;
    private final GeneratedReportRepository generatedReportRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final StudentRepository studentRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final ClassSessionRepository classSessionRepository;
    private final UserRepository userRepository;
    private final MediaStorageService mediaStorageService;
    private final DocxMergeEngine docxMergeEngine;
    private final PdfMergeEngine pdfMergeEngine;
    private final HtmlMergeEngine htmlMergeEngine;
    private final ReportGeneratorFactory reportGeneratorFactory;

    public ReportGenerationService(ReportTemplateRepository reportTemplateRepository,
                                    ReportTemplateFieldMappingRepository fieldMappingRepository,
                                    GeneratedReportRepository generatedReportRepository,
                                    SchoolClassRepository schoolClassRepository,
                                    StudentRepository studentRepository,
                                    ClassEnrollmentRepository classEnrollmentRepository,
                                    ClassSessionRepository classSessionRepository,
                                    UserRepository userRepository,
                                    MediaStorageService mediaStorageService,
                                    DocxMergeEngine docxMergeEngine,
                                    PdfMergeEngine pdfMergeEngine,
                                    HtmlMergeEngine htmlMergeEngine,
                                    ReportGeneratorFactory reportGeneratorFactory) {
        this.reportTemplateRepository = reportTemplateRepository;
        this.fieldMappingRepository = fieldMappingRepository;
        this.generatedReportRepository = generatedReportRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.studentRepository = studentRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.classSessionRepository = classSessionRepository;
        this.userRepository = userRepository;
        this.mediaStorageService = mediaStorageService;
        this.docxMergeEngine = docxMergeEngine;
        this.pdfMergeEngine = pdfMergeEngine;
        this.htmlMergeEngine = htmlMergeEngine;
        this.reportGeneratorFactory = reportGeneratorFactory;
    }

    /**
     * UC-68 Main Flow, phạm vi SINGLE (bước 2-6, 1 học sinh). classId/periods:
     * xem Javadoc GenerateReportRequest. classSessionId: chỉ dùng cho
     * STUDENT_COMMENT (nhận xét 1 học sinh gắn theo 1 buổi học cụ thể, xem
     * StudentCommentReportDataResolver) — null với các loại báo cáo khác.
     */
    @Transactional
    public GeneratedReportResponse generateSingle(Long templateId, Long studentId, Long classId,
                                                    List<ReportPeriodSelector> periods, Long classSessionId,
                                                    String outputFormat, Long actorUserId) {
        ReportTemplate template = templateOrThrow(templateId);
        List<ReportTemplateFieldMapping> mappings = fieldMappingRepository.findByTemplateIdOrderById(templateId);
        User actor = userOrThrow(actorUserId);
        Student student = studentOrThrow(studentId);

        Map<String, Object> context = reportGeneratorFactory.getResolver(template.getTemplateType())
                .buildContext(new ReportGenerationParams(studentId, classId, periods, classSessionId));
        byte[] merged = mergeWithContext(template, mappings, context, outputFormat, describeStudent(student));

        boolean isPdf = isPdfOutput(template, outputFormat);
        String ext = isPdf ? ".pdf" : outputExtension(template);
        String contentType = isPdf ? PDF_CONTENT_TYPE : outputContentType(template);

        String fileUrl = mediaStorageService.storeGeneratedFile(
                merged, template.getName() + ext, contentType, MediaModule.REPORT_TEMPLATE);

        GeneratedReport report = new GeneratedReport();
        report.setTemplate(template);
        report.setScope(GeneratedReport.Scope.SINGLE);
        report.setStudent(student);
        report.setFileUrl(fileUrl);
        report.setFileFormat(isPdf ? GeneratedReport.FileFormat.PDF : outputFileFormat(template));
        report.setFileSizeBytes(merged.length);
        report.setGeneratedBy(actor);
        report = generatedReportRepository.save(report);

        return toResponse(report);
    }

    /**
     * UC-68 Main Flow, phạm vi BULK_CLASS (bước 2-6, toàn bộ học sinh
     * ACTIVE của 1 lớp, gộp 1 file ZIP). A1: học sinh thiếu dữ liệu bị bỏ
     * qua khỏi ZIP (không chặn cả lô vì 1 học sinh lỗi — khác SINGLE, nơi
     * thiếu dữ liệu chặn xuất hẳn). classSessionId: chỉ dùng cho
     * STUDENT_COMMENT (xuất hàng loạt nhận xét của TẤT CẢ học sinh trong
     * lớp cho 1 buổi học cụ thể — học sinh chưa có nhận xét ở buổi đó bị
     * bỏ qua khỏi ZIP, giống cơ chế A1) — null với các loại báo cáo khác.
     */
    @Transactional
    public GeneratedReportResponse generateBulkByClass(Long templateId, Long classId,
                                                          List<ReportPeriodSelector> periods, Long classSessionId,
                                                          String outputFormat, Long actorUserId) {
        ReportTemplate template = templateOrThrow(templateId);
        List<ReportTemplateFieldMapping> mappings = fieldMappingRepository.findByTemplateIdOrderById(templateId);
        User actor = userOrThrow(actorUserId);
        SchoolClass schoolClass = classOrThrow(classId);

        List<ClassEnrollment> activeEnrollments =
                classEnrollmentRepository.findBySchoolClassIdAndStatus(classId, ClassEnrollment.Status.ACTIVE);
        if (activeEnrollments.isEmpty()) {
            throw new ResourceNotFoundException("Lớp id=" + classId + " không có học sinh đang ghi danh (ACTIVE).");
        }

        ReportDataResolver resolver = reportGeneratorFactory.getResolver(template.getTemplateType());
        // studentCode -> ly do bi bo qua (giu nguyen message goc tu merge engine/resolver de de chan doan).
        Map<String, String> skippedReasons = new java.util.LinkedHashMap<>();
        boolean isPdf = isPdfOutput(template, outputFormat);
        String itemExt = isPdf ? ".pdf" : outputExtension(template);
        byte[] zipBytes;
        try (ByteArrayOutputStream zipBuffer = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(zipBuffer)) {
            for (ClassEnrollment enrollment : activeEnrollments) {
                Long studentId = enrollment.getStudent().getId();
                try {
                    Map<String, Object> context = resolver.buildContext(new ReportGenerationParams(studentId, classId, periods, classSessionId));
                    byte[] merged = mergeTemplate(template, mappings, context, outputFormat);
                    zip.putNextEntry(new ZipEntry(enrollment.getStudent().getStudentCode() + itemExt));
                    zip.write(merged);
                    zip.closeEntry();
                } catch (MissingReportDataException ex) {
                    skippedReasons.put(enrollment.getStudent().getStudentCode(), ex.getMessage());
                }
            }
            zip.finish();
            zipBytes = zipBuffer.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Không tạo được file ZIP báo cáo.", ex);
        }
        if (skippedReasons.size() == activeEnrollments.size()) {
            throw new MissingReportDataException(
                    "Không xuất được báo cáo cho bất kỳ học sinh nào trong lớp (" + activeEnrollments.size()
                            + " học sinh) — chi tiết:\n" + formatSkippedReasons(skippedReasons));
        }

        String fileUrl = mediaStorageService.storeGeneratedFile(
                zipBytes, template.getName() + "-" + schoolClass.getName() + ".zip", "application/zip", MediaModule.REPORT_TEMPLATE);

        GeneratedReport report = new GeneratedReport();
        report.setTemplate(template);
        report.setScope(GeneratedReport.Scope.BULK_CLASS);
        report.setSchoolClass(schoolClass);
        report.setFileUrl(fileUrl);
        report.setFileFormat(GeneratedReport.FileFormat.ZIP);
        report.setFileSizeBytes(zipBytes.length);
        report.setGeneratedBy(actor);
        report = generatedReportRepository.save(report);

        return toResponse(report);
    }

    /**
     * UC-68 Main Flow, phạm vi CLASS_SESSION (1 tài liệu duy nhất cho cả
     * buổi học, VD DAILY_REPORT — dùng bảng động {@code [[TABLE:x]]} liệt
     * kê học sinh, xem {@link DailyReportDataResolver}).
     */
    @Transactional
    public GeneratedReportResponse generateClassSession(Long templateId, Long classSessionId, String outputFormat, Long actorUserId) {
        ReportTemplate template = templateOrThrow(templateId);
        List<ReportTemplateFieldMapping> mappings = fieldMappingRepository.findByTemplateIdOrderById(templateId);
        User actor = userOrThrow(actorUserId);
        ClassSession classSession = classSessionOrThrow(classSessionId);

        Map<String, Object> context = reportGeneratorFactory.getResolver(template.getTemplateType())
                .buildContext(new ReportGenerationParams(null, null, List.of(), classSessionId));
        byte[] merged = mergeWithContext(template, mappings, context, outputFormat, describeSession(classSession));

        boolean isPdf = isPdfOutput(template, outputFormat);
        String ext = isPdf ? ".pdf" : outputExtension(template);
        String contentType = isPdf ? PDF_CONTENT_TYPE : outputContentType(template);

        String fileUrl = mediaStorageService.storeGeneratedFile(
                merged, template.getName() + ext, contentType, MediaModule.REPORT_TEMPLATE);

        GeneratedReport report = new GeneratedReport();
        report.setTemplate(template);
        report.setScope(GeneratedReport.Scope.CLASS_SESSION);
        report.setClassSession(classSession);
        report.setSchoolClass(classSession.getSchoolClass());
        report.setFileUrl(fileUrl);
        report.setFileFormat(isPdf ? GeneratedReport.FileFormat.PDF : outputFileFormat(template));
        report.setFileSizeBytes(merged.length);
        report.setGeneratedBy(actor);
        report = generatedReportRepository.save(report);

        return toResponse(report);
    }

    /**
     * UC-68 A1: bọc {@link #mergeTemplate} để gắn thêm đối tượng đang xuất
     * (học sinh/buổi học) vào message lỗi khi thiếu dữ liệu — tránh actor
     * phải tự suy luận "thiếu dữ liệu ở đâu, cho ai" chỉ từ tên placeholder.
     * Dùng cho SINGLE/CLASS_SESSION (1 đối tượng/lần gọi); BULK_CLASS tự
     * gắn student_code khi gom skippedReasons, không dùng helper này.
     */
    private byte[] mergeWithContext(ReportTemplate template, List<ReportTemplateFieldMapping> mappings,
                                     Map<String, Object> context, String outputFormat, String targetDescription) {
        try {
            return mergeTemplate(template, mappings, context, outputFormat);
        } catch (MissingReportDataException ex) {
            throw new MissingReportDataException(targetDescription + " — " + ex.getMessage());
        }
    }

    private String describeStudent(Student student) {
        return "Học sinh " + student.getStudentCode() + " (" + student.getUser().getFullName() + ")";
    }

    private String describeSession(ClassSession session) {
        return "Buổi học " + session.getSessionDate() + " — lớp " + session.getSchoolClass().getName();
    }

    /** Gom message lỗi của từng học sinh bị bỏ qua (BULK_CLASS) thành danh sách dễ đọc, kèm mã học sinh. */
    private String formatSkippedReasons(Map<String, String> skippedReasons) {
        StringBuilder sb = new StringBuilder();
        skippedReasons.forEach((studentCode, reason) -> sb.append("- ").append(studentCode).append(": ").append(reason).append('\n'));
        return sb.toString().stripTrailing();
    }

    private byte[] mergeTemplate(ReportTemplate template, List<ReportTemplateFieldMapping> mappings, Map<String, Object> context, String outputFormat) {
        byte[] templateBytes = mediaStorageService.download(template.getFileUrl());
        List<ReportTemplateFieldMapping> effectiveMappings = mappings.isEmpty()
                ? autoMappingsFromPlaceholderKeys(template)
                : mappings;
        byte[] merged = switch (template.getFileFormat()) {
            case DOCX -> docxMergeEngine.merge(templateBytes, effectiveMappings, context);
            case PDF -> pdfMergeEngine.merge(templateBytes, effectiveMappings, context);
            case HTML -> htmlMergeEngine.merge(templateBytes, effectiveMappings, context); // luôn xuất PDF, xem Javadoc HtmlMergeEngine
        };
        if ("PDF".equalsIgnoreCase(outputFormat) && template.getFileFormat() == ReportTemplate.FileFormat.DOCX) {
            return docxMergeEngine.convertToPdf(merged);
        }
        return merged;
    }

    private boolean isPdfOutput(ReportTemplate template, String outputFormat) {
        if ("PDF".equalsIgnoreCase(outputFormat)) return true;
        return template.getFileFormat() != ReportTemplate.FileFormat.DOCX;
    }

    /**
     * Khi bảng field_mappings chưa được cấu hình (rỗng), tự động suy ra
     * mapping từ placeholder_keys đã phát hiện lúc upload:
     * <ul>
     *   <li>[[TABLE:X]] → type TABLE, data_path = null (context key = "[[TABLE:X]]")</li>
     *   <li>[FIELD_NAME] → type FIELD, data_path = "FIELD_NAME" (strip dấu ngoặc)</li>
     * </ul>
     * Áp dụng quy ước tự nhiên: resolver đặt context key = tên field không ngoặc
     * (VD context.put("CLASS_NAME", ...), context.put("[[TABLE:STUDENTS]]", ...)).
     */
    private List<ReportTemplateFieldMapping> autoMappingsFromPlaceholderKeys(ReportTemplate template) {
        return template.getPlaceholderKeys().stream().map(key -> {
            ReportTemplateFieldMapping m = new ReportTemplateFieldMapping();
            m.setTemplate(template);
            m.setPlaceholderKey(key);
            if (key.startsWith("[[TABLE:")) {
                m.setFieldType(ReportTemplateFieldMapping.FieldType.TABLE);
                m.setDataPath(null);
            } else if (key.startsWith("[") && key.endsWith("]") && !key.startsWith("[[")) {
                // [FIELD_NAME] -> data_path = "FIELD_NAME"
                m.setFieldType(ReportTemplateFieldMapping.FieldType.FIELD);
                m.setDataPath(key.substring(1, key.length() - 1));
            } else {
                // Công thức - vẫn tạo FORMULA mapping, data_path = null
                m.setFieldType(ReportTemplateFieldMapping.FieldType.FORMULA);
                m.setDataPath(null);
            }
            return m;
        }).toList();
    }

    /** Mẫu HTML luôn xuất PDF (đã xác nhận với người dùng 2026-08-09) — xem Javadoc HtmlMergeEngine. */
    private String outputExtension(ReportTemplate template) {
        return template.getFileFormat() == ReportTemplate.FileFormat.DOCX ? ".docx" : ".pdf";
    }

    private String outputContentType(ReportTemplate template) {
        return template.getFileFormat() == ReportTemplate.FileFormat.DOCX ? DOCX_CONTENT_TYPE : PDF_CONTENT_TYPE;
    }

    private GeneratedReport.FileFormat outputFileFormat(ReportTemplate template) {
        return template.getFileFormat() == ReportTemplate.FileFormat.DOCX
                ? GeneratedReport.FileFormat.DOCX
                : GeneratedReport.FileFormat.PDF;
    }

    private ReportTemplate templateOrThrow(Long id) {
        return reportTemplateRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mẫu báo cáo id=" + id));
    }

    private SchoolClass classOrThrow(Long id) {
        return schoolClassRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp id=" + id));
    }

    private Student studentOrThrow(Long id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh id=" + id));
    }

    private ClassSession classSessionOrThrow(Long id) {
        return classSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy buổi học id=" + id));
    }

    private User userOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + id));
    }

    @Transactional(readOnly = true)
    public org.springframework.http.ResponseEntity<byte[]> downloadReportFile(Long reportId) {
        GeneratedReport report = generatedReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy báo cáo id=" + reportId));
        byte[] bytes = mediaStorageService.download(report.getFileUrl());
        String ext = report.getFileFormat() == GeneratedReport.FileFormat.ZIP ? ".zip"
                : (report.getFileFormat() == GeneratedReport.FileFormat.PDF ? ".pdf" : ".docx");
        String contentType = report.getFileFormat() == GeneratedReport.FileFormat.ZIP ? "application/zip"
                : (report.getFileFormat() == GeneratedReport.FileFormat.PDF ? PDF_CONTENT_TYPE : DOCX_CONTENT_TYPE);
        String filename = buildReportFilename(report) + ext;
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                .body(bytes);
    }

    private String buildReportFilename(GeneratedReport report) {
        String templateName = sanitizeFilename(report.getTemplate().getName());
        if (report.getScope() == GeneratedReport.Scope.CLASS_SESSION && report.getClassSession() != null) {
            String className = sanitizeFilename(report.getClassSession().getSchoolClass().getName());
            String dateStr = report.getClassSession().getSessionDate().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            return "BaoCaoNgay_" + className + "_" + dateStr;
        } else if (report.getScope() == GeneratedReport.Scope.SINGLE && report.getStudent() != null) {
            String studentName = sanitizeFilename(report.getStudent().getUser().getFullName());
            String studentCode = report.getStudent().getStudentCode();
            return "BaoCao_" + studentName + "_" + studentCode;
        } else if (report.getScope() == GeneratedReport.Scope.BULK_CLASS && report.getSchoolClass() != null) {
            String className = sanitizeFilename(report.getSchoolClass().getName());
            return "BaoCaoLop_" + className + "_" + templateName;
        }
        return templateName;
    }

    private String sanitizeFilename(String input) {
        if (input == null) return "BaoCao";
        String normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[^a-zA-Z0-9_-]", "_")
                .trim()
                .replaceAll("_+", "_");
        return normalized.isEmpty() ? "BaoCao" : normalized;
    }

    private GeneratedReportResponse toResponse(GeneratedReport r) {
        return new GeneratedReportResponse(
                r.getId(), r.getTemplate().getId(), r.getScope().name(),
                r.getStudent() != null ? r.getStudent().getId() : null,
                r.getSchoolClass() != null ? r.getSchoolClass().getId() : null,
                r.getClassSession() != null ? r.getClassSession().getId() : null,
                r.getFileUrl(), r.getFileFormat().name(), r.getFileSizeBytes(), r.getGeneratedBy().getId());
    }
}
