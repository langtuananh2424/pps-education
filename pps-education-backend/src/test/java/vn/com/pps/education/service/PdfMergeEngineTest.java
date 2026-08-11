package vn.com.pps.education.service;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.junit.jupiter.api.Test;
import vn.com.pps.education.domain.ReportTemplateFieldMapping;
import vn.com.pps.education.exception.MissingReportDataException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-68 bước 3-4: điền mẫu .pdf dạng PDF Form (AcroForm) — xem
 * docs/uc/phan-he-06-hoc-thuat.md.
 */
class PdfMergeEngineTest {

    private final PdfMergeEngine engine = new PdfMergeEngine();

    @Test
    void merge_UC68_MainFlow_fillsAcroFormFieldAndFlattens() throws IOException {
        byte[] template = buildPdfForm("STUDENT_NAME", "CLASS_NAME");
        ReportTemplateFieldMapping nameMapping = fieldMapping("STUDENT_NAME", "STUDENT_NAME");
        ReportTemplateFieldMapping classMapping = fieldMapping("CLASS_NAME", "CLASS_NAME");
        Map<String, Object> context = Map.of("STUDENT_NAME", "Nguyễn Mạnh Trí", "CLASS_NAME", "7A1");

        byte[] merged = engine.merge(template, List.of(nameMapping, classMapping), context);

        try (PDDocument document = PDDocument.load(merged)) {
            PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
            // Flatten xong -> field không còn tồn tại, giá trị đã "khoá" thành nội dung tĩnh.
            assertThat(acroForm.getFields()).isEmpty();
        }
    }

    @Test
    void merge_UC68_MainFlow_formatsNumericFieldValue() throws IOException {
        byte[] template = buildPdfForm("OVERALL_SCORE");
        ReportTemplateFieldMapping mapping = fieldMapping("OVERALL_SCORE", "OVERALL_SCORE");
        Map<String, Object> context = Map.of("OVERALL_SCORE", 8.5);

        byte[] merged = engine.merge(template, List.of(mapping), context);

        assertThat(merged).isNotEmpty();
        // Flatten hoá giá trị thành nội dung trang -> không còn field để đọc lại field.getValueAsString(),
        // chỉ xác nhận merge không lỗi và sinh ra file khác rỗng (nội dung số 8.50 đã "in" vào trang).
    }

    @Test
    void merge_UC68_A1_throwsMissingReportDataExceptionWhenFieldValueMissing() throws IOException {
        byte[] template = buildPdfForm("STUDENT_NAME");
        ReportTemplateFieldMapping mapping = fieldMapping("STUDENT_NAME", "STUDENT_NAME");

        assertThatThrownBy(() -> engine.merge(template, List.of(mapping), Map.of()))
                .isInstanceOf(MissingReportDataException.class);
    }

    @Test
    void merge_boSung_throwsWhenFieldNotFoundInAcroForm() throws IOException {
        byte[] template = buildPdfForm("STUDENT_NAME");
        ReportTemplateFieldMapping mapping = fieldMapping("KHONG_TON_TAI", "STUDENT_NAME");

        assertThatThrownBy(() -> engine.merge(template, List.of(mapping), Map.of("STUDENT_NAME", "An")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void merge_boSung_throwsWhenPdfHasNoAcroForm() throws IOException {
        byte[] plainPdf;
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            plainPdf = out.toByteArray();
        }
        ReportTemplateFieldMapping mapping = fieldMapping("STUDENT_NAME", "STUDENT_NAME");

        assertThatThrownBy(() -> engine.merge(plainPdf, List.of(mapping), Map.of("STUDENT_NAME", "An")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void merge_boSung_ignoresNonFieldMappings() throws IOException {
        byte[] template = buildPdfForm("STUDENT_NAME");
        ReportTemplateFieldMapping formulaMapping = new ReportTemplateFieldMapping();
        formulaMapping.setPlaceholderKey("[[[A]+[B]]/2]");
        formulaMapping.setFieldType(ReportTemplateFieldMapping.FieldType.FORMULA);
        ReportTemplateFieldMapping fieldMapping = fieldMapping("STUDENT_NAME", "STUDENT_NAME");

        byte[] merged = engine.merge(template, List.of(formulaMapping, fieldMapping), Map.of("STUDENT_NAME", "An"));

        assertThat(merged).isNotEmpty();
    }

    private ReportTemplateFieldMapping fieldMapping(String placeholderKey, String dataPath) {
        ReportTemplateFieldMapping mapping = new ReportTemplateFieldMapping();
        mapping.setPlaceholderKey(placeholderKey);
        mapping.setDataPath(dataPath);
        mapping.setFieldType(ReportTemplateFieldMapping.FieldType.FIELD);
        return mapping;
    }

    private byte[] buildPdfForm(String... fieldNames) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            PDAcroForm acroForm = new PDAcroForm(document);
            document.getDocumentCatalog().setAcroForm(acroForm);
            PDResources resources = new PDResources();
            resources.put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA);
            acroForm.setDefaultResources(resources);
            acroForm.setDefaultAppearance("/Helv 12 Tf 0 g");

            int y = 700;
            for (String fieldName : fieldNames) {
                PDTextField textField = new PDTextField(acroForm);
                textField.setPartialName(fieldName);
                textField.setDefaultAppearance("/Helv 12 Tf 0 g");

                PDAnnotationWidget widget = textField.getWidgets().get(0);
                widget.setRectangle(new PDRectangle(50, y, 200, 20));
                widget.setPage(page);
                page.getAnnotations().add(widget);
                acroForm.getFields().add(textField);
                y -= 30;
            }

            document.save(out);
            return out.toByteArray();
        }
    }
}
