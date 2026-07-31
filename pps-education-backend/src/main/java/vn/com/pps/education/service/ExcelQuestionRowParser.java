package vn.com.pps.education.service;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-40 (bổ sung, đã xác nhận với người dùng) — đọc file mẫu Excel soạn đề
 * nhanh. Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-07-30): đổi từ đọc theo VỊ TRÍ cột cố định sang đọc theo TÊN
 * header (dòng 1) — chấp nhận cả tiếng Việt lẫn tiếng Anh (xem
 * {@link QuestionImportFieldAliases}), thứ tự cột không còn quan trọng.
 * Thiếu header "Nội dung"/"Loại câu hỏi" (bắt buộc để biết đọc cột nào) →
 * lỗi ngay cả file, không đọc được dòng nào. Header khác thiếu → field đó
 * luôn null mọi dòng (validate hiện có ở QuestionImportService xử lý).
 */
@Service
public class ExcelQuestionRowParser implements QuestionRowParser {

    private static final int HEADER_ROW_INDEX = 0;
    private static final int FIRST_DATA_ROW_INDEX = 1;

    @Override
    public boolean supports(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".xlsx");
    }

    @Override
    public List<ParsedQuestionRow> parse(InputStream inputStream) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            Row headerRow = sheet.getRow(HEADER_ROW_INDEX);
            if (headerRow == null) {
                throw new IllegalArgumentException("File rỗng hoặc thiếu dòng tiêu đề (header).");
            }
            Map<String, Integer> fieldToColumn = resolveHeaderColumns(headerRow, formatter);
            if (!fieldToColumn.containsKey("content") || !fieldToColumn.containsKey("kind")) {
                throw new IllegalArgumentException(
                        "Thiếu cột bắt buộc: \"Nội dung\"/\"Content\" hoặc \"Loại câu hỏi\"/\"Question Type\" — "
                                + "không xác định được cột nào chứa dữ liệu gì.");
            }

            List<ParsedQuestionRow> rows = new ArrayList<>();
            for (int rowIndex = FIRST_DATA_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row, formatter, fieldToColumn)) {
                    continue;
                }
                rows.add(new ParsedQuestionRow(
                        rowIndex + 1,
                        field(row, formatter, fieldToColumn, "kind"),
                        field(row, formatter, fieldToColumn, "difficulty"),
                        field(row, formatter, fieldToColumn, "content"),
                        field(row, formatter, fieldToColumn, "choiceA"),
                        field(row, formatter, fieldToColumn, "choiceB"),
                        field(row, formatter, fieldToColumn, "choiceC"),
                        field(row, formatter, fieldToColumn, "choiceD"),
                        field(row, formatter, fieldToColumn, "correctAnswer"),
                        field(row, formatter, fieldToColumn, "audioUrl"),
                        field(row, formatter, fieldToColumn, "imageUrl"),
                        field(row, formatter, fieldToColumn, "referencePassage"),
                        field(row, formatter, fieldToColumn, "defaultPoints"),
                        field(row, formatter, fieldToColumn, "explanation"),
                        field(row, formatter, fieldToColumn, "tags")
                ));
            }
            return rows;
        } catch (IOException | RuntimeException ex) {
            throw new UncheckedIOException("File sai định dạng Excel (.xlsx): " + ex.getMessage(), new IOException(ex));
        }
    }

    /** Map tên field nội bộ -> chỉ số cột, tra theo header thực tế (bỏ qua header lạ không nhận diện được). */
    private Map<String, Integer> resolveHeaderColumns(Row headerRow, DataFormatter formatter) {
        Map<String, Integer> fieldToColumn = new HashMap<>();
        for (int col = 0; col < headerRow.getLastCellNum(); col++) {
            String headerText = cellRaw(headerRow, formatter, col);
            if (headerText == null) {
                continue;
            }
            String field = QuestionImportFieldAliases.resolveField(headerText);
            if (field != null) {
                fieldToColumn.putIfAbsent(field, col);
            }
        }
        return fieldToColumn;
    }

    private String field(Row row, DataFormatter formatter, Map<String, Integer> fieldToColumn, String field) {
        Integer col = fieldToColumn.get(field);
        return col == null ? null : cellRaw(row, formatter, col);
    }

    private String cellRaw(Row row, DataFormatter formatter, int index) {
        var c = row.getCell(index);
        if (c == null) {
            return null;
        }
        String value = formatter.formatCellValue(c).trim();
        return value.isEmpty() ? null : value;
    }

    private boolean isBlankRow(Row row, DataFormatter formatter, Map<String, Integer> fieldToColumn) {
        for (Integer col : fieldToColumn.values()) {
            if (cellRaw(row, formatter, col) != null) {
                return false;
            }
        }
        return true;
    }
}
