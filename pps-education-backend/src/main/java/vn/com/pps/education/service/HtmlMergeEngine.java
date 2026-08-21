package vn.com.pps.education.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import vn.com.pps.education.domain.ReportTemplateFieldMapping;
import vn.com.pps.education.exception.MissingReportDataException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UC-68 bước 3-4: điền dữ liệu vào mẫu .html, xuất ra PDF (đã xác nhận với
 * người dùng 2026-08-09: mẫu HTML luôn xuất PDF, không xuất lại HTML thô).
 * Khác {@link DocxMergeEngine} (phải nhân bản {@code XWPFTableRow} ở cấp
 * XML để có bảng động), HTML là text thuần nên mọi thao tác — kể cả bảng
 * động — chỉ là ghép chuỗi đơn giản: tìm khối text giữa 2 marker
 * {@code [[TABLE:x]]}/{@code [[/TABLE:x]]}, lặp lại khối đó cho mỗi phần
 * tử dữ liệu, rồi mới thay [FIELD]/[[formula]] còn lại trên toàn bộ HTML.
 *
 * Parse bằng jsoup (khoan dung, chấp nhận HTML5 không chuẩn XHTML — thẻ
 * không tự đóng như {@code <br>}, {@code <img>}) rồi chuyển sang W3C DOM
 * cho openhtmltopdf render — tránh yêu cầu XHTML nghiêm ngặt mặc định của
 * openhtmltopdf mà admin tạo mẫu khó tuân thủ đúng.
 */
@Service
class HtmlMergeEngine {

    private static final String FONT_RESOURCE_PATH = "/fonts/NotoSans-Regular.ttf";
    private static final String FONT_FAMILY = "PPSNotoSans";

    byte[] merge(byte[] templateBytes, List<ReportTemplateFieldMapping> mappings, Map<String, Object> context) {
        String html = new String(templateBytes, StandardCharsets.UTF_8);

        List<ReportTemplateFieldMapping> tableMappings = mappings.stream()
                .filter(m -> m.getFieldType() == ReportTemplateFieldMapping.FieldType.TABLE).toList();
        List<ReportTemplateFieldMapping> scalarMappings = mappings.stream()
                .filter(m -> m.getFieldType() != ReportTemplateFieldMapping.FieldType.TABLE).toList();

        for (ReportTemplateFieldMapping tableMapping : tableMappings) {
            html = mergeTableBlock(html, tableMapping, context);
        }
        html = mergeScalarPlaceholders(html, scalarMappings, context);

        return renderToPdf(html);
    }

    // ===================== TABLE (bảng động) =====================

    private String mergeTableBlock(String html, ReportTemplateFieldMapping tableMapping, Map<String, Object> context) {
        String openMarker = tableMapping.getPlaceholderKey();
        String tableName = extractTableName(openMarker);

        Object rowsDataObj = context.get(openMarker);
        if (!(rowsDataObj instanceof List<?> rowsData)) {
            throw new MissingReportDataException("error.missingReportData.dynamicTable", new Object[]{openMarker},
                    "Thiếu dữ liệu bảng động cho '" + openMarker + "'.");
        }

        Pattern blockPattern = Pattern.compile(
                "\\[\\[TABLE:" + tableName + "\\]\\](.*?)\\[\\[/TABLE:" + tableName + "\\]\\]", Pattern.DOTALL);
        Matcher matcher = blockPattern.matcher(html);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Không tìm thấy cặp marker '[[TABLE:" + tableName + "]]' trong file mẫu.");
        }
        String rowTemplate = matcher.group(1);

        StringBuilder repeated = new StringBuilder();
        for (Object rowObj : rowsData) {
            if (!(rowObj instanceof Map<?, ?> rowMap)) {
                throw new IllegalStateException("Dữ liệu bảng động '" + openMarker + "' phải là danh sách Map.");
            }
            repeated.append(fillRowTemplate(rowTemplate, castRowMap(rowMap)));
        }

        return html.substring(0, matcher.start()) + repeated + html.substring(matcher.end());
    }

    private String fillRowTemplate(String rowTemplate, Map<String, Object> rowMap) {
        String result = rowTemplate;
        for (Map.Entry<String, Object> entry : rowMap.entrySet()) {
            String placeholder = "[" + entry.getKey() + "]";
            if (result.contains(placeholder)) {
                result = result.replace(placeholder, MergeValueFormatter.valueToText(entry.getValue()));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castRowMap(Map<?, ?> rowMap) {
        return (Map<String, Object>) rowMap;
    }

    private String extractTableName(String openMarker) {
        return openMarker.substring("[[TABLE:".length(), openMarker.length() - "]]".length());
    }

    // ===================== FIELD / FORMULA =====================

    private String mergeScalarPlaceholders(String html, List<ReportTemplateFieldMapping> mappings, Map<String, Object> context) {
        String result = html;
        
        // Process FORMULA first so they can be matched exactly by their placeholder key
        // before their inner field placeholders get replaced by FIELD processing.
        List<ReportTemplateFieldMapping> formulaMappings = mappings.stream()
                .filter(m -> m.getFieldType() == ReportTemplateFieldMapping.FieldType.FORMULA).toList();
        for (ReportTemplateFieldMapping mapping : formulaMappings) {
            if (result.contains(mapping.getPlaceholderKey())) {
                String value = MergeValueFormatter.formatNumber(
                        PlaceholderValueResolver.evaluateFormula(mapping.getPlaceholderKey(), context));
                result = result.replace(mapping.getPlaceholderKey(), value);
            }
        }

        // Then process FIELD mappings
        List<ReportTemplateFieldMapping> fieldMappings = mappings.stream()
                .filter(m -> m.getFieldType() == ReportTemplateFieldMapping.FieldType.FIELD).toList();
        for (ReportTemplateFieldMapping mapping : fieldMappings) {
            if (result.contains(mapping.getPlaceholderKey())) {
                String value = PlaceholderValueResolver.resolveField(mapping, context);
                result = result.replace(mapping.getPlaceholderKey(), value);
            }
        }

        return result;
    }

    // ===================== Render PDF =====================

    /**
     * Luôn ép dùng font Noto Sans nhúng sẵn (cùng font đã dùng cho
     * {@link PdfMergeEngine}, xem Javadoc lớp đó) qua CSS {@code * {
     * font-family }} chèn vào đầu HTML — KHÔNG phụ thuộc admin có khai báo
     * font hỗ trợ tiếng Việt trong mẫu hay không. Lý do giống hệt
     * PdfMergeEngine: font hệ thống mặc định openhtmltopdf dùng khi HTML
     * không khai báo @font-face thường không có dấu tiếng Việt.
     */
    byte[] renderToPdf(String html) {
        String htmlWithFont = "<style>*{font-family:'" + FONT_FAMILY + "',sans-serif;}</style>" + html;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document jsoupDoc = Jsoup.parse(htmlWithFont);
            jsoupDoc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
            org.w3c.dom.Document w3cDoc = new W3CDom().fromJsoup(jsoupDoc);

            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(() -> {
                InputStream stream = getClass().getResourceAsStream(FONT_RESOURCE_PATH);
                if (stream == null) {
                    throw new IllegalStateException("Không tìm thấy font " + FONT_RESOURCE_PATH + " trong resources.");
                }
                return stream;
            }, FONT_FAMILY);
            builder.withW3cDocument(w3cDoc, "");
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Không tạo được file báo cáo PDF từ mẫu HTML.", ex);
        }
    }
}
