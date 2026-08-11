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
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import vn.com.pps.education.domain.ReportTemplateFieldMapping;
import vn.com.pps.education.exception.InvalidTemplatePlaceholderException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-67 bước 2: phát hiện placeholder trong mẫu .docx (trường đơn, công
 * thức, bảng động) — xem docs/uc/phan-he-06-hoc-thuat.md.
 */
class PlaceholderDetectionServiceTest {

    private final PlaceholderDetectionService service = new PlaceholderDetectionService();

    @Test
    void detect_UC67_MainFlow_findsSimpleFieldPlaceholders() {
        String text = "Họ tên học sinh: [STUDENT_NAME]\nLớp: [CLASS_NAME]\n";

        List<PlaceholderDetectionService.DetectedPlaceholder> detected = service.detect(text);

        assertThat(detected).extracting(PlaceholderDetectionService.DetectedPlaceholder::placeholderKey)
                .containsExactlyInAnyOrder("[STUDENT_NAME]", "[CLASS_NAME]");
        assertThat(detected).allSatisfy(p ->
                assertThat(p.fieldType()).isEqualTo(ReportTemplateFieldMapping.FieldType.FIELD));
    }

    @Test
    void detect_UC67_MainFlow_findsFormulaPlaceholderAndConvertsToValidExp4jExpression() {
        String text = "Điểm trung bình: [[[READING]+[LISTENING]+[SPEAKING]+[WRITING]]/4]";

        List<PlaceholderDetectionService.DetectedPlaceholder> detected = service.detect(text);

        assertThat(detected).hasSize(1);
        PlaceholderDetectionService.DetectedPlaceholder formula = detected.get(0);
        assertThat(formula.placeholderKey()).isEqualTo("[[[READING]+[LISTENING]+[SPEAKING]+[WRITING]]/4]");
        assertThat(formula.fieldType()).isEqualTo(ReportTemplateFieldMapping.FieldType.FORMULA);
        // Không throw InvalidTemplatePlaceholderException nghĩa là exp4j parse được biểu thức đã convert.
    }

    @Test
    void detect_UC67_MainFlow_findsTableBlockWithChildFields() {
        String text = "[[TABLE:STUDENTS]]\n[STUDENT_NAME] [ATTENDANCE_STATUS] [STUDENT_COMMENT]\n[[/TABLE:STUDENTS]]";

        List<PlaceholderDetectionService.DetectedPlaceholder> detected = service.detect(text);

        assertThat(detected).hasSize(1);
        PlaceholderDetectionService.DetectedPlaceholder table = detected.get(0);
        assertThat(table.placeholderKey()).isEqualTo("[[TABLE:STUDENTS]]");
        assertThat(table.fieldType()).isEqualTo(ReportTemplateFieldMapping.FieldType.TABLE);
        assertThat(table.description()).contains("STUDENT_NAME", "ATTENDANCE_STATUS", "STUDENT_COMMENT");
    }

    @Test
    void detect_UC67_A3_rejectsFormulaWithUnbalancedBrackets() {
        // [[READING]+[LISTENING] thiếu 1 dấu đóng -> sau khi convert thành exp4j
        // "((READING+LISTENING" không cân bằng ngoặc, exp4j build() phải throw.
        String text = "Sai cú pháp: [[READING]+[LISTENING]";

        assertThatThrownBy(() -> service.detect(text))
                .isInstanceOf(InvalidTemplatePlaceholderException.class);
    }

    @Test
    void classify_boSung_returnsFieldForSimpleLeafToken() {
        assertThat(service.classify("[STUDENT_NAME]")).isEqualTo(ReportTemplateFieldMapping.FieldType.FIELD);
    }

    @Test
    void classify_boSung_returnsTableForTableToken() {
        assertThat(service.classify("[[TABLE:STUDENTS]]")).isEqualTo(ReportTemplateFieldMapping.FieldType.TABLE);
    }

    @Test
    void classify_boSung_returnsFormulaForNestedBracketToken() {
        assertThat(service.classify("[[[READING]+[LISTENING]]/2]")).isEqualTo(ReportTemplateFieldMapping.FieldType.FORMULA);
    }

    @Test
    void extractText_UC67_MainFlow_readsParagraphsAndTableCellsFromDocx() throws IOException {
        byte[] docxBytes = buildDocx("Tiêu đề [STUDENT_NAME]");

        String text = service.extractText(new ByteArrayInputStream(docxBytes));

        assertThat(text).contains("[STUDENT_NAME]");
    }

    @Test
    void extractText_A_rejectsCorruptedOrInvalidDocxContent() {
        ByteArrayInputStream garbage = new ByteArrayInputStream("not a real docx file".getBytes());

        assertThatThrownBy(() -> service.extractText(garbage)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detectPdfFields_UC67_MainFlow_readsAcroFormFieldNamesAsFieldPlaceholders() throws IOException {
        byte[] pdfBytes = buildPdfForm("STUDENT_NAME", "CLASS_NAME");

        List<PlaceholderDetectionService.DetectedPlaceholder> detected = service.detectPdfFields(pdfBytes);

        assertThat(detected).extracting(PlaceholderDetectionService.DetectedPlaceholder::placeholderKey)
                .containsExactlyInAnyOrder("STUDENT_NAME", "CLASS_NAME");
        assertThat(detected).allSatisfy(p ->
                assertThat(p.fieldType()).isEqualTo(ReportTemplateFieldMapping.FieldType.FIELD));
    }

    @Test
    void detectPdfFields_UC67_boSung_returnsEmptyWhenPdfHasNoAcroForm() throws IOException {
        byte[] plainPdf;
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            plainPdf = out.toByteArray();
        }

        assertThat(service.detectPdfFields(plainPdf)).isEmpty();
    }

    @Test
    void detectPdfFields_A_rejectsCorruptedOrInvalidPdfContent() {
        byte[] garbage = "not a real pdf file".getBytes();

        assertThatThrownBy(() -> service.detectPdfFields(garbage)).isInstanceOf(IllegalArgumentException.class);
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

    private byte[] buildDocx(String paragraphText) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(paragraphText);
            document.write(out);
            return out.toByteArray();
        }
    }
}
