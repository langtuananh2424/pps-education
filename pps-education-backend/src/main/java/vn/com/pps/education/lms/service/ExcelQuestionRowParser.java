package vn.com.pps.education.lms.service;

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
 *
 * Nhiều sheet (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-10-05 — mẫu Excel giờ tách 7 sheet theo nhóm loại câu hỏi thay vì 1
 * sheet trộn 17 loại, xem QuestionImportPanel.tsx): đọc TUẦN TỰ qua MỌI
 * sheet trong file (không còn cố định sheet 0), gộp chung kết quả theo
 * đúng thứ tự sheet rồi thứ tự dòng trong sheet. Sheet thiếu header
 * "kind"/"content" bị BỎ QUA (coi là sheet phụ, VD "Hướng dẫn") CHỈ KHI
 * file có từ 2 sheet trở lên; file ĐÚNG 1 sheet thiếu header đó vẫn throw
 * y hệt hành vi cũ — nên file 1-sheet hiện có của giáo viên (100% file
 * trước 2026-10-05) chạy qua vòng lặp đúng 1 lần, hành vi giống hệt trước
 * khi đổi, không có rủi ro backward-compat.
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
            DataFormatter formatter = new DataFormatter();
            boolean multiSheet = workbook.getNumberOfSheets() > 1;
            List<ParsedQuestionRow> rows = new ArrayList<>();
            int validSheetCount = 0;
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                Row headerRow = sheet.getRow(HEADER_ROW_INDEX);
                Map<String, Integer> fieldToColumn = headerRow == null ? Map.of() : resolveHeaderColumns(headerRow, formatter);
                boolean hasRequiredHeaders = fieldToColumn.containsKey("content") && fieldToColumn.containsKey("kind");
                if (!hasRequiredHeaders) {
                    if (multiSheet) {
                        // Sheet phụ không phải dữ liệu câu hỏi (VD "Hướng dẫn") — bỏ qua, không throw.
                        continue;
                    }
                    throw new IllegalArgumentException(
                            "Thiếu cột bắt buộc: \"Nội dung\"/\"Content\" hoặc \"Loại câu hỏi\"/\"Question Type\" — "
                                    + "không xác định được cột nào chứa dữ liệu gì.");
                }
                validSheetCount++;
                // sheetLabel null khi file chỉ 1 sheet — giữ nguyên format báo lỗi cũ "dòng N" (không
                // đổi behavior hiển thị cho file giáo viên đã có từ trước khi tính năng nhiều sheet ra đời).
                String sheetLabel = multiSheet ? sheet.getSheetName() : null;
                for (int rowIndex = FIRST_DATA_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null || isBlankRow(row, formatter, fieldToColumn)) {
                        continue;
                    }
                    rows.add(new ParsedQuestionRow(
                            rowIndex + 1,
                            sheetLabel,
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
            }
            if (multiSheet && validSheetCount == 0) {
                throw new IllegalArgumentException(
                        "Không tìm thấy sheet nào có cột bắt buộc \"Nội dung\"/\"Content\" hoặc "
                                + "\"Loại câu hỏi\"/\"Question Type\" — không xác định được sheet nào chứa dữ liệu câu hỏi.");
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
