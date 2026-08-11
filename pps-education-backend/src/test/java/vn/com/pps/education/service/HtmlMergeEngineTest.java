package vn.com.pps.education.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import vn.com.pps.education.domain.ReportTemplateFieldMapping;
import vn.com.pps.education.exception.MissingReportDataException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-68 bước 3-4: điền mẫu .html, xuất PDF — xem docs/uc/phan-he-06-hoc-thuat.md.
 */
class HtmlMergeEngineTest {

    private final HtmlMergeEngine engine = new HtmlMergeEngine();

    @Test
    void merge_UC68_MainFlow_replacesFieldPlaceholderAndRendersVietnameseTextInPdf() throws IOException {
        byte[] template = html("<p>Ho ten: [STUDENT_NAME]</p>");
        ReportTemplateFieldMapping mapping = fieldMapping("[STUDENT_NAME]", "STUDENT_NAME");

        byte[] pdf = engine.merge(template, List.of(mapping), Map.of("STUDENT_NAME", "Nguyễn Mạnh Trí"));

        assertThat(extractPdfText(pdf)).contains("Nguyễn Mạnh Trí");
    }

    @Test
    void merge_UC68_MainFlow_evaluatesFormulaPlaceholder() throws IOException {
        String formula = "[[[READING]+[LISTENING]+[SPEAKING]+[WRITING]]/4]";
        byte[] template = html("<p>TB: " + formula + "</p>");
        ReportTemplateFieldMapping mapping = new ReportTemplateFieldMapping();
        mapping.setPlaceholderKey(formula);
        mapping.setFieldType(ReportTemplateFieldMapping.FieldType.FORMULA);

        byte[] pdf = engine.merge(template, List.of(mapping), Map.of("READING", 8, "LISTENING", 7, "SPEAKING", 6, "WRITING", 7));

        assertThat(extractPdfText(pdf)).contains("TB: 7");
    }

    @Test
    void merge_UC68_MainFlow_repeatsTableRowForEachDataItem() throws IOException {
        byte[] template = html(
                "<table><tbody>"
                        + "[[TABLE:STUDENTS]]<tr><td>[STUDENT_NAME]</td><td>[ATTENDANCE_STATUS]</td></tr>[[/TABLE:STUDENTS]]"
                        + "</tbody></table>");
        ReportTemplateFieldMapping tableMapping = new ReportTemplateFieldMapping();
        tableMapping.setPlaceholderKey("[[TABLE:STUDENTS]]");
        tableMapping.setFieldType(ReportTemplateFieldMapping.FieldType.TABLE);
        List<Map<String, Object>> rows = List.of(
                Map.of("STUDENT_NAME", "An", "ATTENDANCE_STATUS", "Có mặt"),
                Map.of("STUDENT_NAME", "Bình", "ATTENDANCE_STATUS", "Vắng"));

        byte[] pdf = engine.merge(template, List.of(tableMapping), Map.of("[[TABLE:STUDENTS]]", rows));

        String text = extractPdfText(pdf);
        assertThat(text).contains("An").contains("Có mặt").contains("Bình").contains("Vắng");
    }

    @Test
    void merge_UC68_A1_throwsMissingReportDataExceptionWhenFieldValueMissing() {
        byte[] template = html("<p>[STUDENT_NAME]</p>");
        ReportTemplateFieldMapping mapping = fieldMapping("[STUDENT_NAME]", "STUDENT_NAME");

        assertThatThrownBy(() -> engine.merge(template, List.of(mapping), Map.of()))
                .isInstanceOf(MissingReportDataException.class);
    }

    @Test
    void merge_UC68_A1_throwsMissingReportDataExceptionWhenTableDataMissing() {
        byte[] template = html("<table>[[TABLE:STUDENTS]]<tr><td>[STUDENT_NAME]</td></tr>[[/TABLE:STUDENTS]]</table>");
        ReportTemplateFieldMapping tableMapping = new ReportTemplateFieldMapping();
        tableMapping.setPlaceholderKey("[[TABLE:STUDENTS]]");
        tableMapping.setFieldType(ReportTemplateFieldMapping.FieldType.TABLE);

        assertThatThrownBy(() -> engine.merge(template, List.of(tableMapping), Map.of()))
                .isInstanceOf(MissingReportDataException.class);
    }

    @Test
    void merge_boSung_throwsWhenTableMarkerNotFoundInTemplate() {
        byte[] template = html("<p>Không có bảng nào ở đây.</p>");
        ReportTemplateFieldMapping tableMapping = new ReportTemplateFieldMapping();
        tableMapping.setPlaceholderKey("[[TABLE:STUDENTS]]");
        tableMapping.setFieldType(ReportTemplateFieldMapping.FieldType.TABLE);

        assertThatThrownBy(() -> engine.merge(template, List.of(tableMapping), Map.of("[[TABLE:STUDENTS]]", List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ReportTemplateFieldMapping fieldMapping(String placeholderKey, String dataPath) {
        ReportTemplateFieldMapping mapping = new ReportTemplateFieldMapping();
        mapping.setPlaceholderKey(placeholderKey);
        mapping.setDataPath(dataPath);
        mapping.setFieldType(ReportTemplateFieldMapping.FieldType.FIELD);
        return mapping;
    }

    private byte[] html(String bodyContent) {
        String full = "<html><head><meta charset=\"UTF-8\"></head><body>" + bodyContent + "</body></html>";
        return full.getBytes(StandardCharsets.UTF_8);
    }

    private String extractPdfText(byte[] pdfBytes) throws IOException {
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            return new PDFTextStripper().getText(document);
        }
    }
}
