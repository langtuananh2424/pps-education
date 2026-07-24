package vn.com.pps.education.common;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Dựng file .xlsx dùng chung cho các endpoint "tải file mẫu" (Student/
 * Parent/Employee/Grade import) và "xuất danh sách tài khoản" — tránh lặp
 * lại boilerplate Apache POI (đã có sẵn trong classpath, dùng để ĐỌC Excel
 * ở *BatchImportService/GradeImportService) ở 7 chỗ khác nhau.
 */
public final class ExcelExportHelper {

    private ExcelExportHelper() {
    }

    /**
     * 1 sheet dữ liệu (header in đậm + các dòng {@code rows}), cộng thêm 1
     * sheet "Hướng dẫn" tuỳ chọn (mỗi phần tử {@code notes} là 1 dòng ghi
     * chú) — dùng cho các trường hợp cần giải thích thêm ngoài tên cột (VD
     * cột chỉ bắt buộc trong 1 số trường hợp).
     */
    public static byte[] buildWorkbook(String sheetName, List<String> headers, List<List<Object>> rows,
                                        List<String> notes) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = boldHeaderStyle(workbook);

            XSSFSheet sheet = workbook.createSheet(sheetName);
            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < headers.size(); col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(headers.get(col));
                cell.setCellStyle(headerStyle);
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<Object> rowValues = rows.get(r);
                for (int col = 0; col < rowValues.size(); col++) {
                    // Bỏ trống hẳn (không tạo Cell) cho giá trị null — để lại ô thật sự
                    // trống trên file .xlsx (VD cột điểm chưa nhập ở buildTemplate()),
                    // thay vì 1 Cell rỗng kiểu BLANK vẫn khiến row.getCell(col) != null.
                    Object value = rowValues.get(col);
                    if (value != null) {
                        writeCell(row.createCell(col), value);
                    }
                }
            }
            for (int col = 0; col < headers.size(); col++) {
                sheet.autoSizeColumn(col);
            }

            if (notes != null && !notes.isEmpty()) {
                XSSFSheet noteSheet = workbook.createSheet("Hướng dẫn");
                for (int i = 0; i < notes.size(); i++) {
                    noteSheet.createRow(i).createCell(0).setCellValue(notes.get(i));
                }
                noteSheet.autoSizeColumn(0);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Không tạo được file Excel: " + ex.getMessage(), ex);
        }
    }

    public static byte[] buildWorkbook(String sheetName, List<String> headers, List<List<Object>> rows) {
        return buildWorkbook(sheetName, headers, rows, null);
    }

    private static void writeCell(Cell cell, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private static CellStyle boldHeaderStyle(XSSFWorkbook workbook) {
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(boldFont);
        return style;
    }
}
