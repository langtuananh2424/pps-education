package vn.com.pps.education.service;

import net.objecthunter.exp4j.ExpressionBuilder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Service;
import vn.com.pps.education.domain.ReportTemplateFieldMapping;
import vn.com.pps.education.exception.InvalidTemplatePlaceholderException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UC-67 bước 2: quét nội dung file mẫu, tự động phát hiện placeholder.
 * DOCX: 3 dạng — trường đơn {@code [FIELD]}, công thức
 * {@code [[[A]+[B]]/2]} (xem {@link ReportTemplateFormulaConverter}), và
 * bảng động {@code [[TABLE:x]]...[[/TABLE:x]]} (xem {@link #detect}).
 * PDF: chỉ 1 dạng — tên field AcroForm (PDF Form có sẵn ô nhập, không có
 * dấu ngoặc, không hỗ trợ công thức/bảng động — đã xác nhận với người
 * dùng 2026-08-09, xem {@link #detectPdfFields}).
 */
@Service
public class PlaceholderDetectionService {

    public record DetectedPlaceholder(String placeholderKey, ReportTemplateFieldMapping.FieldType fieldType, String description) {
    }

    private static final Pattern TABLE_BLOCK_PATTERN =
            Pattern.compile("\\[\\[TABLE:([A-Za-z0-9_]+)\\]\\](.*?)\\[\\[/TABLE:\\1\\]\\]", Pattern.DOTALL);
    private static final Pattern SIMPLE_FIELD_PATTERN = Pattern.compile("\\[([A-Z][A-Z0-9_]*)\\]");
    private static final Pattern LEAF_ONLY_PATTERN = Pattern.compile("^\\[[A-Z][A-Z0-9_]*\\]$");

    /** Trích toàn bộ text (đoạn văn + bảng) trong file .docx bằng Apache POI. PDF dùng {@link #detectPdfFields}. */
    String extractText(InputStream docxStream) {
        try (XWPFDocument document = new XWPFDocument(docxStream)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                sb.append(paragraph.getText()).append('\n');
            }
            for (XWPFTable table : document.getTables()) {
                table.getRows().forEach(row -> row.getTableCells()
                        .forEach(cell -> sb.append(cell.getText()).append('\n')));
            }
            return sb.toString();
        } catch (IOException ex) {
            throw new UncheckedIOException("Không đọc được nội dung file .docx.", ex);
        } catch (RuntimeException ex) {
            // Apache POI ném RuntimeException (VD NotOfficeXmlFileException) khi nội
            // dung file không phải .docx hợp lệ dù Content-Type khai báo đúng.
            throw new IllegalArgumentException("File .docx bị hỏng hoặc không đúng định dạng.", ex);
        }
    }

    /**
     * UC-67 bước 2 (PDF): đọc tên field từ AcroForm — mỗi field terminal
     * (fillable, VD PDTextField) là 1 placeholder FIELD, tên field CHÍNH
     * LÀ placeholder_key (không dấu ngoặc). Không hỗ trợ FORMULA/TABLE cho
     * PDF (đã xác nhận với người dùng — AcroForm không có khái niệm lặp
     * field hay biểu thức).
     */
    List<DetectedPlaceholder> detectPdfFields(byte[] pdfBytes) {
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
            if (acroForm == null) {
                return List.of();
            }
            List<DetectedPlaceholder> result = new ArrayList<>();
            for (PDField field : acroForm.getFieldTree()) {
                if (field instanceof PDTerminalField) {
                    result.add(new DetectedPlaceholder(field.getFullyQualifiedName(), ReportTemplateFieldMapping.FieldType.FIELD, null));
                }
            }
            return result;
        } catch (IOException ex) {
            throw new IllegalArgumentException("File .pdf bị hỏng hoặc không đúng định dạng.", ex);
        }
    }

    /** Phân loại lại 1 placeholder_key đã lưu (dùng khi cấu hình field mapping ở UC-67 bước 3). */
    ReportTemplateFieldMapping.FieldType classify(String placeholderKey) {
        if (placeholderKey.startsWith("[[TABLE:")) {
            return ReportTemplateFieldMapping.FieldType.TABLE;
        }
        if (LEAF_ONLY_PATTERN.matcher(placeholderKey).matches()) {
            return ReportTemplateFieldMapping.FieldType.FIELD;
        }
        return ReportTemplateFieldMapping.FieldType.FORMULA;
    }

    /** UC-67 bước 2: phát hiện toàn bộ placeholder trong text, phân loại FIELD/FORMULA/TABLE. */
    List<DetectedPlaceholder> detect(String text) {
        Map<String, DetectedPlaceholder> result = new LinkedHashMap<>();

        Matcher tableMatcher = TABLE_BLOCK_PATTERN.matcher(text);
        StringBuilder remaining = new StringBuilder();
        int lastEnd = 0;
        while (tableMatcher.find()) {
            remaining.append(text, lastEnd, tableMatcher.start());
            lastEnd = tableMatcher.end();

            String tableName = tableMatcher.group(1);
            String innerContent = tableMatcher.group(2);
            LinkedHashSet<String> innerFields = new LinkedHashSet<>();
            Matcher innerFieldMatcher = SIMPLE_FIELD_PATTERN.matcher(innerContent);
            while (innerFieldMatcher.find()) {
                innerFields.add(innerFieldMatcher.group(1));
            }
            String placeholderKey = "[[TABLE:" + tableName + "]]";
            result.put(placeholderKey, new DetectedPlaceholder(placeholderKey, ReportTemplateFieldMapping.FieldType.TABLE,
                    "Bảng động, các trường con: " + String.join(", ", innerFields)));
        }
        remaining.append(text.substring(lastEnd));

        for (String token : topLevelBracketTokens(remaining.toString())) {
            if (token.startsWith("[[TABLE:") || token.startsWith("[[/TABLE:")) {
                String tableName = token.substring(token.indexOf(":") + 1, token.length() - 2);
                String placeholderKey = "[[TABLE:" + tableName + "]]";
                result.putIfAbsent(placeholderKey, new DetectedPlaceholder(placeholderKey, ReportTemplateFieldMapping.FieldType.TABLE, "Bảng động " + tableName));
            } else if (LEAF_ONLY_PATTERN.matcher(token).matches()) {
                result.putIfAbsent(token, new DetectedPlaceholder(token, ReportTemplateFieldMapping.FieldType.FIELD, null));
            } else {
                validateFormula(token);
                result.putIfAbsent(token, new DetectedPlaceholder(token, ReportTemplateFieldMapping.FieldType.FORMULA, null));
            }
        }
        return new ArrayList<>(result.values());
    }

    /** UC-67 A3: chặn lưu nếu biểu thức không parse được (dấu ngoặc không khớp/toán tử không hợp lệ). */
    void validateFormula(String rawPlaceholder) {
        String expression = ReportTemplateFormulaConverter.toExp4jExpression(rawPlaceholder);
        List<String> variables = new ArrayList<>();
        Matcher m = SIMPLE_FIELD_PATTERN.matcher(rawPlaceholder);
        while (m.find()) {
            variables.add(m.group(1));
        }
        try {
            ExpressionBuilder builder = new ExpressionBuilder(expression);
            if (!variables.isEmpty()) {
                builder.variables(variables.toArray(new String[0]));
            }
            builder.build();
        } catch (RuntimeException ex) {
            throw new InvalidTemplatePlaceholderException("error.invalidTemplatePlaceholder.syntaxError",
                    new Object[]{rawPlaceholder, ex.getMessage()},
                    "Biểu thức công thức sai cú pháp: " + rawPlaceholder + " (" + ex.getMessage() + ")");
        }
    }

    /**
     * Quét text tìm các nhóm dấu ngoặc vuông cân bằng ở mức ngoài cùng
     * (không lồng vào token khác). UC-67 A3: dấu ngoặc không khớp (thiếu
     * đóng) trong toàn bộ nội dung file là lỗi cấu hình mẫu — chặn lưu thay
     * vì âm thầm bỏ qua đoạn placeholder dở dang đó.
     */
    private List<String> topLevelBracketTokens(String text) {
        List<String> tokens = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '[') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == ']') {
                if (depth > 0) {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        tokens.add(text.substring(start, i + 1));
                        start = -1;
                    }
                } else {
                    throw new InvalidTemplatePlaceholderException("error.invalidTemplatePlaceholder.unmatchedClosingBracket",
                            new Object[]{i},
                            "Dấu ']' thừa không khớp dấu '[' nào ở vị trí " + i + " trong nội dung mẫu.");
                }
            }
        }
        if (depth != 0) {
            throw new InvalidTemplatePlaceholderException("error.invalidTemplatePlaceholder.unmatchedOpenBracket",
                    new Object[]{depth},
                    "Dấu ngoặc vuông không khớp: thiếu " + depth + " dấu ']' đóng trong nội dung mẫu.");
        }
        return tokens;
    }
}
