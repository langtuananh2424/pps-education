package vn.com.pps.education.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import vn.com.pps.education.domain.ReportTemplateFieldMapping;
import vn.com.pps.education.exception.MissingReportDataException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-68 bước 3-4: thay placeholder trong file .docx bằng dữ liệu thực tế
 * (Mail Merge) — xem docs/uc/phan-he-06-hoc-thuat.md.
 */
class DocxMergeEngineTest {

    private final DocxMergeEngine engine = new DocxMergeEngine();

    @Test
    void merge_UC68_MainFlow_replacesSimpleFieldPlaceholder() throws IOException {
        byte[] template = buildDocx("Họ tên: [STUDENT_NAME] - Lớp: [CLASS_NAME]");
        ReportTemplateFieldMapping nameMapping = fieldMapping("[STUDENT_NAME]", "STUDENT_NAME");
        ReportTemplateFieldMapping classMapping = fieldMapping("[CLASS_NAME]", "CLASS_NAME");
        Map<String, Object> context = Map.of("STUDENT_NAME", "Nguyễn Mạnh Trí", "CLASS_NAME", "7A1");

        byte[] merged = engine.merge(template, List.of(nameMapping, classMapping), context);

        assertThat(extractText(merged)).isEqualTo("Họ tên: Nguyễn Mạnh Trí - Lớp: 7A1");
    }

    @Test
    void merge_UC68_MainFlow_replacesFormulaPlaceholderWithComputedAverage() throws IOException {
        String formula = "[[[READING]+[LISTENING]+[SPEAKING]+[WRITING]]/4]";
        byte[] template = buildDocx("Điểm TB: " + formula);
        ReportTemplateFieldMapping formulaMapping = new ReportTemplateFieldMapping();
        formulaMapping.setPlaceholderKey(formula);
        formulaMapping.setFieldType(ReportTemplateFieldMapping.FieldType.FORMULA);
        Map<String, Object> context = Map.of("READING", 8, "LISTENING", 7, "SPEAKING", 6, "WRITING", 7);

        byte[] merged = engine.merge(template, List.of(formulaMapping), context);

        // (8+7+6+7)/4 = 7 (số nguyên -> không hiện .00, xem DocxMergeEngine#formatNumber)
        assertThat(extractText(merged)).isEqualTo("Điểm TB: 7");
    }

    @Test
    void merge_UC68_MainFlow_formatsNonIntegerFormulaResultWithTwoDecimals() throws IOException {
        String formula = "[[[READING]+[LISTENING]]/2]";
        byte[] template = buildDocx("TB: " + formula);
        ReportTemplateFieldMapping formulaMapping = new ReportTemplateFieldMapping();
        formulaMapping.setPlaceholderKey(formula);
        formulaMapping.setFieldType(ReportTemplateFieldMapping.FieldType.FORMULA);
        Map<String, Object> context = Map.of("READING", 8, "LISTENING", 7);

        byte[] merged = engine.merge(template, List.of(formulaMapping), context);

        assertThat(extractText(merged)).isEqualTo("TB: 7.50");
    }

    @Test
    void merge_UC68_A1_throwsMissingReportDataExceptionWhenFieldValueMissing() throws IOException {
        byte[] template = buildDocx("Họ tên: [STUDENT_NAME]");
        ReportTemplateFieldMapping mapping = fieldMapping("[STUDENT_NAME]", "STUDENT_NAME");

        assertThatThrownBy(() -> engine.merge(template, List.of(mapping), Map.of()))
                .isInstanceOf(MissingReportDataException.class);
    }

    @Test
    void merge_UC68_A1_throwsMissingReportDataExceptionWhenFormulaVariableMissing() throws IOException {
        String formula = "[[[READING]+[LISTENING]]/2]";
        byte[] template = buildDocx("TB: " + formula);
        ReportTemplateFieldMapping formulaMapping = new ReportTemplateFieldMapping();
        formulaMapping.setPlaceholderKey(formula);
        formulaMapping.setFieldType(ReportTemplateFieldMapping.FieldType.FORMULA);

        assertThatThrownBy(() -> engine.merge(template, List.of(formulaMapping), Map.of("READING", 8)))
                .isInstanceOf(MissingReportDataException.class);
    }

    @Test
    void merge_boSung_leavesParagraphsWithoutPlaceholderUnchanged() throws IOException {
        byte[] template = buildDocx("Đoạn văn không có trường nào cần điền.");

        byte[] merged = engine.merge(template, List.of(), Map.of());

        assertThat(extractText(merged)).isEqualTo("Đoạn văn không có trường nào cần điền.");
    }

    @Test
    void merge_UC68_MainFlow_clonesTableRowForEachDataItemAndRemovesMarkerRows() throws IOException {
        byte[] template = buildDocxWithStudentsTable();
        ReportTemplateFieldMapping tableMapping = tableMapping();
        List<Map<String, Object>> rows = List.of(
                Map.of("STUDENT_NAME", "An", "ATTENDANCE_STATUS", "Có mặt", "STUDENT_COMMENT", "Tốt"),
                Map.of("STUDENT_NAME", "Bình", "ATTENDANCE_STATUS", "Vắng", "STUDENT_COMMENT", ""));
        Map<String, Object> context = Map.of("[[TABLE:STUDENTS]]", rows);

        byte[] merged = engine.merge(template, List.of(tableMapping), context);

        List<List<String>> table = extractTableRows(merged);
        assertThat(table).containsExactly(
                List.of("Header", "", ""),
                List.of("An", "Có mặt", "Tốt"),
                List.of("Bình", "Vắng", ""));
    }

    @Test
    void merge_UC68_A1_throwsMissingReportDataExceptionWhenTableDataMissingFromContext() throws IOException {
        byte[] template = buildDocxWithStudentsTable();
        ReportTemplateFieldMapping tableMapping = tableMapping();

        assertThatThrownBy(() -> engine.merge(template, List.of(tableMapping), Map.of()))
                .isInstanceOf(MissingReportDataException.class);
    }

    @Test
    void merge_boSung_throwsWhenTableMarkersNotFoundInTemplate() throws IOException {
        byte[] template = buildDocx("Không có bảng nào trong file này.");
        ReportTemplateFieldMapping tableMapping = tableMapping();

        assertThatThrownBy(() -> engine.merge(template, List.of(tableMapping), Map.of("[[TABLE:STUDENTS]]", List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void merge_UC68_MainFlow_producesEmptyTableWhenNoDataRows() throws IOException {
        byte[] template = buildDocxWithStudentsTable();
        ReportTemplateFieldMapping tableMapping = tableMapping();

        byte[] merged = engine.merge(template, List.of(tableMapping), Map.of("[[TABLE:STUDENTS]]", List.of()));

        List<List<String>> table = extractTableRows(merged);
        assertThat(table).containsExactly(List.of("Header", "", ""));
    }

    private ReportTemplateFieldMapping tableMapping() {
        ReportTemplateFieldMapping mapping = new ReportTemplateFieldMapping();
        mapping.setPlaceholderKey("[[TABLE:STUDENTS]]");
        mapping.setFieldType(ReportTemplateFieldMapping.FieldType.TABLE);
        return mapping;
    }

    /** Bảng 4 dòng: header (ngoài marker) + dòng mở marker + 1 dòng mẫu (3 cột) + dòng đóng marker. */
    private byte[] buildDocxWithStudentsTable() throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            org.apache.poi.xwpf.usermodel.XWPFTable table = document.createTable(4, 3);
            table.getRow(0).getCell(0).setText("Header");
            table.getRow(1).getCell(0).setText("[[TABLE:STUDENTS]]");
            table.getRow(2).getCell(0).setText("[STUDENT_NAME]");
            table.getRow(2).getCell(1).setText("[ATTENDANCE_STATUS]");
            table.getRow(2).getCell(2).setText("[STUDENT_COMMENT]");
            table.getRow(3).getCell(0).setText("[[/TABLE:STUDENTS]]");
            document.write(out);
            return out.toByteArray();
        }
    }

    private List<List<String>> extractTableRows(byte[] docxBytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            org.apache.poi.xwpf.usermodel.XWPFTable table = document.getTables().get(0);
            return table.getRows().stream()
                    .map(row -> row.getTableCells().stream().map(org.apache.poi.xwpf.usermodel.XWPFTableCell::getText).toList())
                    .toList();
        }
    }

    private ReportTemplateFieldMapping fieldMapping(String placeholderKey, String dataPath) {
        ReportTemplateFieldMapping mapping = new ReportTemplateFieldMapping();
        mapping.setPlaceholderKey(placeholderKey);
        mapping.setDataPath(dataPath);
        mapping.setFieldType(ReportTemplateFieldMapping.FieldType.FIELD);
        return mapping;
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

    private String extractText(byte[] docxBytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : document.getParagraphs()) {
                sb.append(p.getText());
            }
            return sb.toString();
        }
    }
}
