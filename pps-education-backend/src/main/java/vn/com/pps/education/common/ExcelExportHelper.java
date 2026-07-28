package vn.com.pps.education.common;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

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
        return buildWorkbook(sheetName, headers, rows, notes, null);
    }

    public static byte[] buildWorkbook(String sheetName, List<String> headers, List<List<Object>> rows) {
        return buildWorkbook(sheetName, headers, rows, null, null);
    }

    /**
     * Như trên, cộng thêm dropdown (Excel Data Validation) cho các cột có
     * tập giá trị cố định — {@code columnDropdowns} ánh xạ chỉ số cột (0-based,
     * theo {@code headers}) sang danh sách giá trị hợp lệ. Áp cho toàn bộ
     * dòng dữ liệu (dòng 1..rows.size(), không tính header) để kéo dòng có
     * sẵn xuống dòng mới vẫn giữ dropdown. Không strict-enforce (không gọi
     * setShowErrorBox) vì backend còn chấp nhận thêm biến thể viết thường/
     * không dấu/tiếng Anh khi import (VD parseAttendanceStatus) — dropdown
     * chỉ để gợi ý, chặn cứng sẽ từ chối oan các biến thể đó.
     */
    public static byte[] buildWorkbook(String sheetName, List<String> headers, List<List<Object>> rows,
                                        List<String> notes, Map<Integer, List<String>> columnDropdowns) {
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

            if (columnDropdowns != null && !rows.isEmpty()) {
                addColumnDropdowns(workbook, sheet, rows.size(), columnDropdowns);
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

    /** Ngưỡng an toàn dưới giới hạn ~255 ký tự tổng của Excel explicit list — vượt ngưỡng chuyển sang named-range (xem namedRangeConstraint). */
    private static final int EXPLICIT_LIST_SAFE_LENGTH = 200;

    private static void addColumnDropdowns(XSSFWorkbook workbook, XSSFSheet sheet, int rowCount,
                                            Map<Integer, List<String>> columnDropdowns) {
        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        for (Map.Entry<Integer, List<String>> entry : columnDropdowns.entrySet()) {
            int col = entry.getKey();
            List<String> values = entry.getValue();
            if (values.isEmpty()) {
                continue;
            }
            CellRangeAddressList range = new CellRangeAddressList(1, rowCount, col, col);
            DataValidationConstraint constraint = String.join(",", values).length() > EXPLICIT_LIST_SAFE_LENGTH
                    ? namedRangeConstraint(workbook, dvHelper, col, values)
                    : dvHelper.createExplicitListConstraint(values.toArray(new String[0]));
            DataValidation validation = dvHelper.createValidation(constraint, range);
            // true ở đây MỚI là hiện mũi tên dropdown trong ô — quirk đã biết của
            // XSSFDataValidation (POI đảo ngược cờ này so với tên gọi để khớp OOXML).
            validation.setSuppressDropDownArrow(true);
            sheet.addValidationData(validation);
        }
    }

    /**
     * Danh sách dài (VD nhãn bài tập/video đã gán cho 1 lớp — cột động
     * theo lớp, UC-21 mở rộng) vượt ngưỡng an toàn của Excel explicit
     * list (~255 ký tự tổng) — dựng 1 sheet ẩn chứa từng giá trị 1 dòng,
     * dropdown trỏ formula vào range đó (không giới hạn độ dài).
     */
    private static DataValidationConstraint namedRangeConstraint(XSSFWorkbook workbook, DataValidationHelper dvHelper,
                                                                   int col, List<String> values) {
        String hiddenSheetName = "_dd" + col;
        XSSFSheet hidden = workbook.createSheet(hiddenSheetName);
        for (int i = 0; i < values.size(); i++) {
            hidden.createRow(i).createCell(0).setCellValue(values.get(i));
        }
        workbook.setSheetHidden(workbook.getSheetIndex(hidden), true);
        String formula = "'" + hiddenSheetName + "'!$A$1:$A$" + values.size();
        return dvHelper.createFormulaListConstraint(formula);
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
