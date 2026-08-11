package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.domain.ReportTemplate;
import vn.com.pps.education.domain.ReportTemplateFieldMapping;
import vn.com.pps.education.domain.ReportTemplateHistory;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AvailableReportFieldResponse;
import vn.com.pps.education.dto.CreateReportTemplateRequest;
import vn.com.pps.education.dto.FieldMappingItemRequest;
import vn.com.pps.education.dto.ReportTemplateFieldMappingResponse;
import vn.com.pps.education.dto.ReportTemplateResponse;
import vn.com.pps.education.dto.UpdateFieldMappingsRequest;
import vn.com.pps.education.dto.UpdateReportTemplateRequest;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ReportTemplateFieldMappingRepository;
import vn.com.pps.education.repository.ReportTemplateHistoryRepository;
import vn.com.pps.education.repository.ReportTemplateRepository;
import vn.com.pps.education.repository.UserRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/**
 * UC-67: Quản lý mẫu báo cáo (FR-ACA-08 — bổ sung ngoài SDD gốc, đã xác
 * nhận với người dùng). Xem docs/uc/phan-he-06-hoc-thuat.md để biết đầy đủ
 * Main Flow/Alternate Flow/Postcondition.
 *
 * Nhận 3 định dạng:
 * <ul>
 *   <li>.docx — đầy đủ FIELD/FORMULA/TABLE (xem PlaceholderDetectionService#detect).</li>
 *   <li>.pdf — chỉ FIELD, dùng cơ chế PDF Form/AcroForm, placeholder_key = tên
 *       field (không dấu ngoặc), không hỗ trợ công thức/bảng động (đã xác
 *       nhận với người dùng 2026-08-09, xem PlaceholderDetectionService#detectPdfFields).</li>
 *   <li>.html — đầy đủ FIELD/FORMULA/TABLE giống .docx (cùng cú pháp
 *       {@code [FIELD]}/{@code [[formula]]}/{@code [[TABLE:x]]}, dùng lại
 *       nguyên PlaceholderDetectionService#detect trên text HTML thô) —
 *       nhưng UC-68 luôn xuất PDF (không xuất lại HTML), xem HtmlMergeEngine.
 *       Đã xác nhận với người dùng 2026-08-09.</li>
 * </ul>
 * Không có bước duyệt (đã xác nhận với người dùng 2026-08-09): mẫu dùng
 * được ngay sau khi tạo/cấu hình field mapping.
 */
import vn.com.pps.education.domain.ReportTemplatePublishedField;
import vn.com.pps.education.repository.ReportTemplatePublishedFieldRepository;
import vn.com.pps.education.repository.ReportTemplateFieldMappingRepository;
import vn.com.pps.education.repository.ReportTemplateHistoryRepository;
import vn.com.pps.education.repository.ReportTemplateRepository;
import vn.com.pps.education.repository.UserRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportTemplateService {

    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String HTML_CONTENT_TYPE = "text/html";

    private final ReportTemplateRepository reportTemplateRepository;
    private final ReportTemplateFieldMappingRepository fieldMappingRepository;
    private final ReportTemplatePublishedFieldRepository publishedFieldRepository;
    private final ReportTemplateHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final MediaStorageService mediaStorageService;
    private final PlaceholderDetectionService placeholderDetectionService;

    public ReportTemplateService(ReportTemplateRepository reportTemplateRepository,
                                  ReportTemplateFieldMappingRepository fieldMappingRepository,
                                  ReportTemplatePublishedFieldRepository publishedFieldRepository,
                                  ReportTemplateHistoryRepository historyRepository,
                                  UserRepository userRepository,
                                  MediaStorageService mediaStorageService,
                                  PlaceholderDetectionService placeholderDetectionService) {
        this.reportTemplateRepository = reportTemplateRepository;
        this.fieldMappingRepository = fieldMappingRepository;
        this.publishedFieldRepository = publishedFieldRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.mediaStorageService = mediaStorageService;
        this.placeholderDetectionService = placeholderDetectionService;
    }

    /** Main Flow bước 1-4: upload file, tự phát hiện placeholder, lưu mẫu mới. */
    @Transactional
    public ReportTemplateResponse createTemplate(MultipartFile file, CreateReportTemplateRequest request, Long actorUserId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File mẫu không được để trống.");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Tên mẫu báo cáo không được để trống.");
        }
        // A1: định dạng chưa được hỗ trợ — chỉ nhận .docx hoặc .pdf (PDF Form/AcroForm).
        ReportTemplate.FileFormat fileFormat = resolveFileFormat(file.getContentType());

        ReportTemplate.TemplateType templateType = parseTemplateType(request.templateType());
        User actor = userOrThrow(actorUserId);

        List<PlaceholderDetectionService.DetectedPlaceholder> detected = switch (fileFormat) {
            case DOCX -> detectDocxPlaceholders(file);
            case PDF -> placeholderDetectionService.detectPdfFields(readBytes(file));
            case HTML -> placeholderDetectionService.detect(new String(readBytes(file), java.nio.charset.StandardCharsets.UTF_8));
        };

        String fileUrl = mediaStorageService.store(file, "REPORT_TEMPLATE");

        ReportTemplate template = new ReportTemplate();
        template.setName(request.name());
        template.setTemplateType(templateType);
        template.setDescription(request.description());
        template.setFileUrl(fileUrl);
        template.setFileFormat(fileFormat);
        template.setOriginalFilename(file.getOriginalFilename());
        template.setFileSizeBytes(file.getSize());
        template.setPlaceholderKeys(detected.stream().map(PlaceholderDetectionService.DetectedPlaceholder::placeholderKey).toList());
        template.setActive(true);
        template.setCreatedBy(actor);
        template = reportTemplateRepository.save(template);

        writeHistory(template, actor, ReportTemplateHistory.Action.CREATED,
                Map.of("name", template.getName(), "templateType", template.getTemplateType().name(),
                        "placeholderCount", detected.size()));

        return toResponse(template, List.of());
    }

    /** UC-67 bước 5: sửa metadata (tên/mô tả) — không đổi file/placeholder đã phát hiện. */
    @Transactional
    public ReportTemplateResponse updateTemplate(Long id, UpdateReportTemplateRequest request, Long actorUserId) {
        ReportTemplate template = templateOrThrow(id);
        User actor = userOrThrow(actorUserId);
        template.setName(request.name());
        template.setDescription(request.description());
        template = reportTemplateRepository.save(template);

        writeHistory(template, actor, ReportTemplateHistory.Action.UPDATED,
                Map.of("name", template.getName()));

        return toResponse(template, fieldMappingRepository.findByTemplateIdOrderById(template.getId()));
    }

    /** UC-67 bước 3: cấu hình lại toàn bộ ánh xạ placeholder -> nguồn dữ liệu (thay thế danh sách cũ). */
    @Transactional
    public ReportTemplateResponse updateFieldMappings(Long id, UpdateFieldMappingsRequest request, Long actorUserId) {
        ReportTemplate template = templateOrThrow(id);
        User actor = userOrThrow(actorUserId);

        for (FieldMappingItemRequest item : request.mappings()) {
            if (!template.getPlaceholderKeys().contains(item.placeholderKey())) {
                throw new IllegalArgumentException(
                        "Placeholder '" + item.placeholderKey() + "' không tồn tại trong mẫu này.");
            }
        }

        fieldMappingRepository.deleteByTemplateId(template.getId());
        List<ReportTemplateFieldMapping> mappings = request.mappings().stream().map(item -> {
            // PDF (AcroForm) chỉ có FIELD — tên field không dấu ngoặc nên classify() (logic
            // bracket-based dùng chung cho DOCX/HTML) không áp dụng được, xem Javadoc class.
            ReportTemplateFieldMapping.FieldType fieldType = template.getFileFormat() == ReportTemplate.FileFormat.PDF
                    ? ReportTemplateFieldMapping.FieldType.FIELD
                    : placeholderDetectionService.classify(item.placeholderKey());
            // (HTML dùng đúng nguyên logic classify() bracket-based như DOCX — không cần nhánh riêng.)
            if (fieldType == ReportTemplateFieldMapping.FieldType.FIELD
                    && (item.dataPath() == null || item.dataPath().isBlank())) {
                throw new IllegalArgumentException(
                        "Trường '" + item.placeholderKey() + "' cần cấu hình data_path.");
            }
            ReportTemplateFieldMapping mapping = new ReportTemplateFieldMapping();
            mapping.setTemplate(template);
            mapping.setPlaceholderKey(item.placeholderKey());
            mapping.setDataPath(fieldType == ReportTemplateFieldMapping.FieldType.FIELD ? item.dataPath() : null);
            mapping.setFieldType(fieldType);
            mapping.setDescription(item.description());
            return mapping;
        }).toList();
        mappings = fieldMappingRepository.saveAll(mappings);

        writeHistory(template, actor, ReportTemplateHistory.Action.UPDATED,
                Map.of("fieldMappingCount", mappings.size()));

        return toResponse(template, mappings);
    }

    /** UC-67 bước 6 / A4: xoá mềm 1 mẫu — không xoá cứng, không ảnh hưởng báo cáo đã xuất trước đó. */
    @Transactional
    public void deleteTemplate(Long id, Long actorUserId) {
        ReportTemplate template = templateOrThrow(id);
        User actor = userOrThrow(actorUserId);
        template.setActive(false);
        reportTemplateRepository.save(template);
        writeHistory(template, actor, ReportTemplateHistory.Action.DELETED, Map.of());
    }

    @Transactional(readOnly = true)
    public ReportTemplateResponse getById(Long id) {
        ReportTemplate template = templateOrThrow(id);
        return toResponse(template, fieldMappingRepository.findByTemplateIdOrderById(template.getId()));
    }

    @Transactional(readOnly = true)
    public List<ReportTemplateResponse> list(String templateType) {
        List<ReportTemplate> templates = templateType == null || templateType.isBlank()
                ? reportTemplateRepository.findByActiveTrueOrderByNameAsc()
                : reportTemplateRepository.findByTemplateTypeAndActiveTrueOrderByNameAsc(parseTemplateType(templateType));
        return templates.stream()
                .map(t -> toResponse(t, fieldMappingRepository.findByTemplateIdOrderById(t.getId())))
                .toList();
    }

    // ===================== Helpers =====================

    private ReportTemplate.FileFormat resolveFileFormat(String contentType) {
        if (DOCX_CONTENT_TYPE.equals(contentType)) {
            return ReportTemplate.FileFormat.DOCX;
        }
        if (PDF_CONTENT_TYPE.equals(contentType)) {
            return ReportTemplate.FileFormat.PDF;
        }
        if (HTML_CONTENT_TYPE.equals(contentType)) {
            return ReportTemplate.FileFormat.HTML;
        }
        throw new IllegalArgumentException(
                "Định dạng file không được hỗ trợ. Hệ thống chỉ nhận file .docx, .pdf (PDF Form) hoặc .html.");
    }

    private List<PlaceholderDetectionService.DetectedPlaceholder> detectDocxPlaceholders(MultipartFile file) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(file.getBytes())) {
            String text = placeholderDetectionService.extractText(in);
            return placeholderDetectionService.detect(text);
        } catch (IOException ex) {
            throw new UncheckedIOException("Không đọc được nội dung file .docx.", ex);
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("Không đọc được nội dung file .pdf.", ex);
        }
    }

    private ReportTemplate.TemplateType parseTemplateType(String value) {
        try {
            return ReportTemplate.TemplateType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("templateType không hợp lệ: " + value
                    + ". Giá trị hợp lệ: " + java.util.Arrays.toString(ReportTemplate.TemplateType.values()));
        }
    }

    private void writeHistory(ReportTemplate template, User actor, ReportTemplateHistory.Action action, Map<String, Object> details) {
        ReportTemplateHistory history = new ReportTemplateHistory();
        history.setTemplate(template);
        history.setChangedBy(actor);
        history.setAction(action);
        history.setDetails(details);
        historyRepository.save(history);
    }

    private ReportTemplate templateOrThrow(Long id) {
        return reportTemplateRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mẫu báo cáo id=" + id));
    }

    private User userOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + id));
    }

    private ReportTemplateResponse toResponse(ReportTemplate t, List<ReportTemplateFieldMapping> mappings) {
        return new ReportTemplateResponse(
                t.getId(), t.getName(), t.getTemplateType().name(), t.getDescription(), t.getFileUrl(),
                t.getFileFormat().name(), t.getOriginalFilename(), t.getFileSizeBytes(), t.getPlaceholderKeys(),
                t.isActive(), t.getCreatedBy().getId(),
                mappings.stream().map(this::toMappingResponse).toList());
    }

    private ReportTemplateFieldMappingResponse toMappingResponse(ReportTemplateFieldMapping m) {
        return new ReportTemplateFieldMappingResponse(
                m.getId(), m.getPlaceholderKey(), m.getDataPath(), m.getFieldType().name(), m.getDescription());
    }

    /** Danh sách các Data Path chuẩn công bố bởi Backend (Single Source of Truth) từ database table report_template_published_fields. */
    @Transactional(readOnly = true)
    public Map<String, List<AvailableReportFieldResponse>> getAvailableFields() {
        List<ReportTemplatePublishedField> list = publishedFieldRepository.findByActiveTrueOrderByTemplateTypeAscDisplayOrderAscIdAsc();
        return list.stream().collect(Collectors.groupingBy(
                f -> f.getTemplateType().name(),
                Collectors.mapping(
                        f -> new AvailableReportFieldResponse(f.getFieldKey(), f.getFieldLabel(), f.getDescription(), f.getFieldType().name()),
                        Collectors.toList()
                )
        ));
    }
}
