package vn.com.pps.education.common;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        return buildWorkbook(sheetName, headers, rows, notes, columnDropdowns, null);
    }

    /** Nhóm nhiều cột con dưới 1 header gộp (merge) ở dòng đầu — VD "BTVN online" trải trên 2 cột con "Ngữ pháp"/"Từ Vựng (TKN)". fromCol/toCol 0-based, theo {@code headers}. */
    public record HeaderGroup(String label, int fromCol, int toCol) {
    }

    /**
     * Như {@link #buildWorkbook(String, List, List, List, Map)}, cộng thêm
     * {@code headerGroups} (bổ sung ngoài SDD gốc, đã xác nhận với người
     * dùng 2026-08-06 — cho Nhận xét học viên nhìn giống bảng UI web) — khi
     * khác null/rỗng, chèn thêm 1 DÒNG header gộp phía TRÊN dòng header
     * hiện có (merge theo từng {@link HeaderGroup}, cột không thuộc nhóm
     * nào để trống ở dòng này), dòng dữ liệu dời xuống tương ứng. Header cả
     * 2 dòng được tô nền + kẻ viền cho giống bảng web hơn (khác
     * {@code boldHeaderStyle} mặc định — CHỈ áp dụng khi dùng overload
     * này, 7 luồng import khác gọi overload cũ giữ nguyên style cũ).
     */
    public static byte[] buildWorkbook(String sheetName, List<String> headers, List<List<Object>> rows,
                                        List<String> notes, Map<Integer, List<String>> columnDropdowns,
                                        List<HeaderGroup> headerGroups) {
        return buildWorkbook(sheetName, headers, rows, notes, columnDropdowns, headerGroups, null);
    }

    /**
     * Như trên, cộng thêm {@code headerSubGroups} (V130, bổ sung ngoài SDD
     * gốc, đã xác nhận với người dùng 2026-08-21 — cho Nhận xét học viên
     * buổi teacherType=VIETNAMESE, nhóm "BTVN buổi trước"/"BTVN" tách thêm
     * 1 cấp Offline/Online) — khi khác null/rỗng, chèn thêm 1 DÒNG header
     * gộp CẤP 2 (giữa {@code headerGroups} cấp 1 và dòng tên cột con), tổng
     * 3 dòng header. Cột thuộc {@code headerGroups} nhưng KHÔNG thuộc
     * {@code headerSubGroups} nào thì merge dọc 2 dòng dưới (giống hành vi
     * "cột không nhóm" ở overload 2 cấp). {@code headerSubGroups} khác null
     * mà {@code headerGroups} rỗng là lỗi dùng sai API (bỏ qua, coi như
     * không có subgroup).
     */
    public static byte[] buildWorkbook(String sheetName, List<String> headers, List<List<Object>> rows,
                                        List<String> notes, Map<Integer, List<String>> columnDropdowns,
                                        List<HeaderGroup> headerGroups, List<HeaderGroup> headerSubGroups) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            boolean grouped = headerGroups != null && !headerGroups.isEmpty();
            boolean subGrouped = grouped && headerSubGroups != null && !headerSubGroups.isEmpty();
            CellStyle headerStyle = grouped ? styledHeaderStyle(workbook) : boldHeaderStyle(workbook);

            XSSFSheet sheet = workbook.createSheet(sheetName);
            int headerRowIndex;
            if (subGrouped) {
                headerRowIndex = buildThreeLevelHeader(sheet, headers, headerGroups, headerSubGroups, headerStyle);
            } else if (grouped) {
                headerRowIndex = buildTwoLevelHeader(sheet, headers, headerGroups, headerStyle);
            } else {
                Row headerRow = sheet.createRow(0);
                for (int col = 0; col < headers.size(); col++) {
                    Cell cell = headerRow.createCell(col);
                    cell.setCellValue(headers.get(col));
                    cell.setCellStyle(headerStyle);
                }
                headerRowIndex = 0;
            }
            int dataStartRow = headerRowIndex + 1;
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + dataStartRow);
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
            if (grouped) {
                // autoSizeColumn tính theo nội dung TỪNG cột riêng — không biết cột nào đang bị merge
                // ngang ở dòng nhóm, nên nhãn nhóm dài (VD "BTVN online") có thể bị cắt nếu tổng bề
                // rộng các cột con cộng lại chưa đủ. Nới cột cuối cùng của nhóm bù phần thiếu.
                for (HeaderGroup group : headerGroups) {
                    int combinedWidth = IntStream.rangeClosed(group.fromCol(), group.toCol())
                            .map(sheet::getColumnWidth).sum();
                    int requiredWidth = (group.label().length() + 4) * 256;
                    if (combinedWidth < requiredWidth) {
                        sheet.setColumnWidth(group.toCol(), sheet.getColumnWidth(group.toCol()) + (requiredWidth - combinedWidth));
                    }
                }
                if (subGrouped) {
                    for (HeaderGroup sub : headerSubGroups) {
                        int combinedWidth = IntStream.rangeClosed(sub.fromCol(), sub.toCol())
                                .map(sheet::getColumnWidth).sum();
                        int requiredWidth = (sub.label().length() + 4) * 256;
                        if (combinedWidth < requiredWidth) {
                            sheet.setColumnWidth(sub.toCol(), sheet.getColumnWidth(sub.toCol()) + (requiredWidth - combinedWidth));
                        }
                    }
                }
            }

            if (columnDropdowns != null && !rows.isEmpty()) {
                addColumnDropdowns(workbook, sheet, dataStartRow, rows.size(), columnDropdowns);
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

    /** Dòng nhóm (0) + dòng cột con (1) — logic cũ trước V130, tách ra nguyên vẹn để dùng chung với nhánh 3 cấp mới ({@link #buildThreeLevelHeader}). Trả về headerRowIndex (=1). */
    private static int buildTwoLevelHeader(XSSFSheet sheet, List<String> headers, List<HeaderGroup> headerGroups, CellStyle headerStyle) {
        // Cột KHÔNG thuộc nhóm nào — merge dọc 2 dòng (0:1) để tiêu đề trông liền 1 ô cao,
        // giống hệt rowSpan=2 ở bảng UI web (thead 2 dòng: dòng nhóm + dòng cột con).
        Set<Integer> groupedCols = headerGroups.stream()
                .flatMap(g -> IntStream.rangeClosed(g.fromCol(), g.toCol()).boxed())
                .collect(Collectors.toSet());
        Row groupRow = sheet.createRow(0);
        // Đủ cao để chữ wrap 2 dòng không bị cắt (VD "BTVN online" khi 2 cột con hẹp) — POI
        // không tự tăng chiều cao dòng theo wrapText.
        groupRow.setHeightInPoints(30f);
        for (HeaderGroup group : headerGroups) {
            Cell cell = groupRow.createCell(group.fromCol());
            cell.setCellValue(group.label());
            cell.setCellStyle(headerStyle);
            if (group.toCol() > group.fromCol()) {
                sheet.addMergedRegion(new CellRangeAddress(0, 0, group.fromCol(), group.toCol()));
            }
        }
        for (int col = 0; col < headers.size(); col++) {
            if (groupedCols.contains(col)) {
                continue;
            }
            Cell cell = groupRow.createCell(col);
            cell.setCellValue(headers.get(col));
            cell.setCellStyle(headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 1, col, col));
        }
        Row subHeaderRow = sheet.createRow(1);
        for (int col = 0; col < headers.size(); col++) {
            Cell cell = subHeaderRow.createCell(col);
            if (groupedCols.contains(col)) {
                cell.setCellValue(headers.get(col));
            }
            cell.setCellStyle(headerStyle);
        }
        return 1;
    }

    /**
     * V130 — 3 dòng header: nhóm cấp 1 ({@code headerGroups}, dòng 0), nhóm cấp 2 ({@code headerSubGroups},
     * dòng 1), tên cột con (dòng 2). Cột thuộc nhóm cấp 1 nhưng KHÔNG thuộc nhóm cấp 2 nào — merge dọc
     * dòng 1:2, tên cột hiện ở dòng 1 (mirror "cột không nhóm" của {@link #buildTwoLevelHeader}). Cột
     * hoàn toàn không nhóm — merge dọc cả 3 dòng 0:2. Trả về headerRowIndex (=2).
     */
    private static int buildThreeLevelHeader(XSSFSheet sheet, List<String> headers, List<HeaderGroup> headerGroups,
                                               List<HeaderGroup> headerSubGroups, CellStyle headerStyle) {
        Set<Integer> topGroupedCols = headerGroups.stream()
                .flatMap(g -> IntStream.rangeClosed(g.fromCol(), g.toCol()).boxed())
                .collect(Collectors.toSet());
        Set<Integer> subGroupedCols = headerSubGroups.stream()
                .flatMap(g -> IntStream.rangeClosed(g.fromCol(), g.toCol()).boxed())
                .collect(Collectors.toSet());

        Row row0 = sheet.createRow(0);
        row0.setHeightInPoints(24f);
        Row row1 = sheet.createRow(1);
        row1.setHeightInPoints(24f);
        Row row2 = sheet.createRow(2);

        for (int col = 0; col < headers.size(); col++) {
            Cell c0 = row0.createCell(col);
            Cell c1 = row1.createCell(col);
            Cell c2 = row2.createCell(col);
            c0.setCellStyle(headerStyle);
            c1.setCellStyle(headerStyle);
            c2.setCellStyle(headerStyle);
            if (!topGroupedCols.contains(col)) {
                c0.setCellValue(headers.get(col));
                sheet.addMergedRegion(new CellRangeAddress(0, 2, col, col));
            } else if (!subGroupedCols.contains(col)) {
                c1.setCellValue(headers.get(col));
                sheet.addMergedRegion(new CellRangeAddress(1, 2, col, col));
            } else {
                c2.setCellValue(headers.get(col));
            }
        }
        for (HeaderGroup group : headerGroups) {
            row0.getCell(group.fromCol()).setCellValue(group.label());
            if (group.toCol() > group.fromCol()) {
                sheet.addMergedRegion(new CellRangeAddress(0, 0, group.fromCol(), group.toCol()));
            }
        }
        for (HeaderGroup sub : headerSubGroups) {
            row1.getCell(sub.fromCol()).setCellValue(sub.label());
            if (sub.toCol() > sub.fromCol()) {
                sheet.addMergedRegion(new CellRangeAddress(1, 1, sub.fromCol(), sub.toCol()));
            }
        }
        return 2;
    }

    /** Ngưỡng an toàn dưới giới hạn ~255 ký tự tổng của Excel explicit list — vượt ngưỡng chuyển sang named-range (xem namedRangeConstraint). */
    private static final int EXPLICIT_LIST_SAFE_LENGTH = 200;

    private static void addColumnDropdowns(XSSFWorkbook workbook, XSSFSheet sheet, int dataStartRow, int rowCount,
                                            Map<Integer, List<String>> columnDropdowns) {
        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        for (Map.Entry<Integer, List<String>> entry : columnDropdowns.entrySet()) {
            int col = entry.getKey();
            List<String> values = entry.getValue();
            if (values.isEmpty()) {
                continue;
            }
            CellRangeAddressList range = new CellRangeAddressList(dataStartRow, dataStartRow + rowCount - 1, col, col);
            // Bug thật: Excel explicit-list dùng dấu phẩy làm ký tự phân tách giữa các item — nếu 1 giá
            // trị TỰ NÓ chứa dấu phẩy (VD nhãn "examSkillGroupLabel" dạng "... (Ngữ pháp, 1 bài, 1 câu)"
            // ở StudentCommentService), Excel sẽ tách nhầm 1 giá trị thành nhiều mục trong dropdown. Bắt
            // buộc dùng named-range (formula list, không bị comma-split) bất cứ khi nào có item chứa dấu
            // phẩy, không chỉ dựa vào tổng độ dài như trước.
            boolean anyValueContainsComma = values.stream().anyMatch(v -> v.contains(","));
            DataValidationConstraint constraint = anyValueContainsComma || String.join(",", values).length() > EXPLICIT_LIST_SAFE_LENGTH
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

    /** Header tô nền + kẻ viền + căn giữa — chỉ dùng khi buildWorkbook được gọi kèm headerGroups (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06), cho gần giống bảng UI web hơn boldHeaderStyle mặc định. */
    private static CellStyle styledHeaderStyle(XSSFWorkbook workbook) {
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(boldFont);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }
}
