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
import java.util.List;

/**
 * UC-40 (bổ sung, đã xác nhận với người dùng): đọc file mẫu Excel soạn đề
 * nhanh — cột CỐ ĐỊNH theo vị trí (không so khớp theo tên header như
 * GradeImportService, vì bộ cột câu hỏi không đổi theo ngữ cảnh như
 * grade_components đổi theo từng kỳ đánh giá): A=Loại câu hỏi, B=Độ khó,
 * C=Nội dung, D-G=Đáp án A-D, H=Đáp án đúng, I=URL Audio, J=URL Hình ảnh,
 * K=Transcript/Từ khóa phát âm, L=Điểm mặc định, M=Giải thích, N=Tags.
 * Dòng 1 = header (bỏ qua khi đọc), dữ liệu từ dòng 2.
 */
@Service
public class ExcelQuestionRowParser implements QuestionRowParser {

    private static final int FIRST_DATA_ROW_INDEX = 1;
    private static final int COLUMN_COUNT = 14;

    @Override
    public boolean supports(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".xlsx");
    }

    @Override
    public List<ParsedQuestionRow> parse(InputStream inputStream) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            List<ParsedQuestionRow> rows = new ArrayList<>();
            for (int rowIndex = FIRST_DATA_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row, formatter)) {
                    continue;
                }
                rows.add(new ParsedQuestionRow(
                        rowIndex + 1,
                        cell(row, formatter, 0),
                        cell(row, formatter, 1),
                        cell(row, formatter, 2),
                        cell(row, formatter, 3),
                        cell(row, formatter, 4),
                        cell(row, formatter, 5),
                        cell(row, formatter, 6),
                        cell(row, formatter, 7),
                        cell(row, formatter, 8),
                        cell(row, formatter, 9),
                        cell(row, formatter, 10),
                        cell(row, formatter, 11),
                        cell(row, formatter, 12),
                        cell(row, formatter, 13)
                ));
            }
            return rows;
        } catch (IOException | RuntimeException ex) {
            throw new UncheckedIOException("File sai định dạng Excel (.xlsx): " + ex.getMessage(), new IOException(ex));
        }
    }

    private String cell(Row row, DataFormatter formatter, int index) {
        var c = row.getCell(index);
        if (c == null) {
            return null;
        }
        String value = formatter.formatCellValue(c).trim();
        return value.isEmpty() ? null : value;
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        for (int i = 0; i < COLUMN_COUNT; i++) {
            if (cell(row, formatter, i) != null) {
                return false;
            }
        }
        return true;
    }
}
