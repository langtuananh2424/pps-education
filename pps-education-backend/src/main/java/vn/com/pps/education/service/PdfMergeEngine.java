package vn.com.pps.education.service;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDVariableText;
import org.springframework.stereotype.Service;
import vn.com.pps.education.domain.ReportTemplateFieldMapping;
import vn.com.pps.education.exception.MissingReportDataException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/**
 * UC-68 bước 3-4: điền dữ liệu vào mẫu .pdf dạng PDF Form (AcroForm) —
 * khác {@link DocxMergeEngine} (tìm/thay text trong file DOCX), PDF không
 * có API "thay text tại vị trí" đáng tin cậy nên chỉ hỗ trợ field đã có
 * sẵn trong form (đã xác nhận với người dùng 2026-08-09). Chỉ hỗ trợ
 * placeholder FIELD — FORMULA/TABLE không áp dụng cho PDF (AcroForm không
 * có khái niệm biểu thức hay lặp field).
 *
 * Luôn ép dùng font Noto Sans nhúng sẵn ({@code resources/fonts/NotoSans-Regular.ttf},
 * license SIL Open Font License — được phép nhúng tự do) khi điền giá
 * trị, KHÔNG dùng font mặc định của field trong file mẫu gốc — bổ sung
 * ngoài SDD gốc, đã xác nhận với người dùng (2026-08-09). Lý do: font
 * chuẩn 14 (Helvetica...) không nhúng, chỉ hỗ trợ WinAnsiEncoding, ném
 * lỗi ngay khi field.setValue() gặp ký tự có dấu tiếng Việt (VD "ệ") —
 * phát hiện qua test round-trip với dữ liệu tiếng Việt thật. Ép font ở
 * đây đảm bảo đúng bất kể admin chọn font gì khi tạo field trong Word/
 * Acrobat.
 *
 * Sau khi điền, form bị "flatten" (khoá lại thành text tĩnh, không còn
 * chỉnh sửa được) — tránh người nhận vô tình sửa lại giá trị đã điền.
 */
@Service
class PdfMergeEngine {

    private static final String FONT_RESOURCE_PATH = "/fonts/NotoSans-Regular.ttf";
    private static final String FONT_RESOURCE_NAME = "PPSNotoSans";
    private static final String DEFAULT_APPEARANCE = "/" + FONT_RESOURCE_NAME + " 11 Tf 0 g";

    byte[] merge(byte[] templateBytes, List<ReportTemplateFieldMapping> mappings, Map<String, Object> context) {
        try (PDDocument document = PDDocument.load(templateBytes);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
            if (acroForm == null) {
                throw new IllegalArgumentException("File mẫu .pdf không phải PDF Form (không có AcroForm để điền).");
            }
            registerUnicodeFont(document, acroForm);
            for (ReportTemplateFieldMapping mapping : mappings) {
                if (mapping.getFieldType() != ReportTemplateFieldMapping.FieldType.FIELD) {
                    continue;
                }
                fillField(acroForm, mapping, context);
            }
            acroForm.flatten();
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Không tạo được file báo cáo PDF từ mẫu.", ex);
        }
    }

    private void registerUnicodeFont(PDDocument document, PDAcroForm acroForm) throws IOException {
        try (InputStream fontStream = getClass().getResourceAsStream(FONT_RESOURCE_PATH)) {
            if (fontStream == null) {
                throw new IllegalStateException("Không tìm thấy font " + FONT_RESOURCE_PATH + " trong resources.");
            }
            PDFont font = PDType0Font.load(document, fontStream, true);
            PDResources resources = acroForm.getDefaultResources();
            if (resources == null) {
                resources = new PDResources();
            }
            resources.put(COSName.getPDFName(FONT_RESOURCE_NAME), font);
            acroForm.setDefaultResources(resources);
        }
    }

    private void fillField(PDAcroForm acroForm, ReportTemplateFieldMapping mapping, Map<String, Object> context) throws IOException {
        PDField field = acroForm.getField(mapping.getPlaceholderKey());
        if (field == null) {
            throw new IllegalArgumentException("Không tìm thấy field '" + mapping.getPlaceholderKey() + "' trong file mẫu PDF.");
        }
        Object value = context.get(mapping.getDataPath());
        if (value == null) {
            throw new MissingReportDataException(
                    "Thiếu dữ liệu cho field '" + mapping.getPlaceholderKey()
                            + "' (data_path='" + mapping.getDataPath() + "').");
        }
        if (field instanceof PDVariableText variableText) {
            variableText.setDefaultAppearance(DEFAULT_APPEARANCE);
        }
        field.setValue(MergeValueFormatter.valueToText(value));
    }
}
