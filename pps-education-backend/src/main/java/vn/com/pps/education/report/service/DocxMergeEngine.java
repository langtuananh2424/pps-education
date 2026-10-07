package vn.com.pps.education.report.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlException;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;
import org.springframework.stereotype.Service;
import vn.com.pps.education.report.domain.ReportTemplateFieldMapping;
import vn.com.pps.education.exception.MissingReportDataException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * UC-68 bước 3-4: thay thế toàn bộ placeholder trong 1 file .docx bằng
 * giá trị thực tế (đọc từ {@link ReportDataResolver#buildContext}) — cơ
 * chế Mail Merge. Hỗ trợ 3 dạng placeholder (xem
 * {@link ReportTemplateFieldMapping.FieldType}):
 * <ul>
 *   <li>FIELD/FORMULA: gộp toàn bộ run trong 1 đoạn văn thành 1 chuỗi
 *       trước khi thay thế (Word thường tách 1 câu thành nhiều run do bôi
 *       đen/gợi ý chính tả — nếu chỉ thay từng run riêng lẻ, placeholder bị
 *       cắt giữa 2 run sẽ không khớp được). Định dạng riêng của các run bị
 *       gộp (trừ run đầu) trong đoạn văn có placeholder sẽ mất — chấp nhận
 *       đánh đổi để đảm bảo tìm/thay đúng.</li>
 *   <li>TABLE (bảng động, VD {@code [[TABLE:STUDENTS]]...[[/TABLE:STUDENTS]]}):
 *       actor tạo mẫu đặt 2 dòng marker (mỗi marker 1 dòng riêng trong
 *       bảng Word) bao quanh 1 (hoặc nhiều) dòng mẫu chứa placeholder đơn
 *       giản (VD {@code [STUDENT_NAME]}). Engine tìm đúng 2 dòng marker,
 *       nhân bản (những) dòng mẫu ở giữa — mỗi phần tử trong danh sách dữ
 *       liệu (context key = chính placeholderKey, VD
 *       {@code context.get("[[TABLE:STUDENTS]]")} phải là
 *       {@code List<Map<String,Object>>}) tạo ra 1 bản sao, điền giá trị
 *       theo đúng tên field trong map (không dấu ngoặc), rồi xoá 2 dòng
 *       marker + dòng mẫu gốc.</li>
 * </ul>
 */
@Service
class DocxMergeEngine {

    private final HtmlMergeEngine htmlMergeEngine;

    public DocxMergeEngine() {
        this(new HtmlMergeEngine());
    }

    DocxMergeEngine(HtmlMergeEngine htmlMergeEngine) {
        this.htmlMergeEngine = htmlMergeEngine;
    }

    byte[] merge(byte[] templateBytes, List<ReportTemplateFieldMapping> mappings, Map<String, Object> context) {
        List<ReportTemplateFieldMapping> tableMappings = mappings.stream()
                .filter(m -> m.getFieldType() == ReportTemplateFieldMapping.FieldType.TABLE).toList();
        List<ReportTemplateFieldMapping> scalarMappings = mappings.stream()
                .filter(m -> m.getFieldType() != ReportTemplateFieldMapping.FieldType.TABLE).toList();

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(templateBytes));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (ReportTemplateFieldMapping tableMapping : tableMappings) {
                mergeTableRows(document, tableMapping, context);
            }
            // Xóa các paragraph ngoài bảng còn chứa TABLE marker (VD: label [[TABLE:STUDENTS]]
            // được đặt riêng trong paragraph để làm tiêu đề — không phải dòng trong bảng Word,
            // nên mergeTableRows() không tự xóa chúng).
            removeTableMarkerParagraphs(document, tableMappings);

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                mergeParagraph(paragraph, scalarMappings, context);
            }
            for (XWPFTable table : document.getTables()) {
                table.getRows().forEach(row -> row.getTableCells().forEach(cell ->
                        cell.getParagraphs().forEach(p -> mergeParagraph(p, scalarMappings, context))));
            }
            document.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Không tạo được file báo cáo từ mẫu.", ex);
        }
    }

    /**
     * Xóa các paragraph cấp tài liệu (không nằm trong bảng) chứa TABLE marker
     * (mở hoặc đóng) — thường là label trang trí template không bị xử lý bởi mergeTableRows().
     */
    private void removeTableMarkerParagraphs(XWPFDocument document, List<ReportTemplateFieldMapping> tableMappings) {
        if (tableMappings.isEmpty()) return;
        java.util.Set<String> tableMarkers = new java.util.HashSet<>();
        for (ReportTemplateFieldMapping m : tableMappings) {
            String openMarker = m.getPlaceholderKey(); // "[[TABLE:X]]"
            String tableName = extractTableName(openMarker);
            tableMarkers.add(openMarker);
            tableMarkers.add("[[/TABLE:" + tableName + "]]");
        }
        List<XWPFParagraph> paragraphs = document.getParagraphs();
        for (int i = paragraphs.size() - 1; i >= 0; i--) {
            XWPFParagraph p = paragraphs.get(i);
            String text = p.getText();
            if (tableMarkers.stream().anyMatch(text::contains)) {
                int pos = document.getPosOfParagraph(p);
                if (pos >= 0) {
                    document.removeBodyElement(pos);
                }
            }
        }
    }


    // ===================== FIELD / FORMULA (đoạn văn) =====================

    private void mergeParagraph(XWPFParagraph paragraph, List<ReportTemplateFieldMapping> mappings, Map<String, Object> context) {
        replaceInParagraph(paragraph, text -> {
            String replaced = text;
            for (ReportTemplateFieldMapping mapping : mappings) {
                if (replaced.contains(mapping.getPlaceholderKey())) {
                    replaced = replaced.replace(mapping.getPlaceholderKey(), resolveScalarValue(mapping, context));
                }
            }
            return replaced;
        });
    }

    private String resolveScalarValue(ReportTemplateFieldMapping mapping, Map<String, Object> context) {
        return switch (mapping.getFieldType()) {
            case FIELD -> PlaceholderValueResolver.resolveField(mapping, context);
            case FORMULA -> MergeValueFormatter.formatNumber(
                    PlaceholderValueResolver.evaluateFormula(mapping.getPlaceholderKey(), context));
            case TABLE -> throw new IllegalStateException(
                    "Bảng động '" + mapping.getPlaceholderKey() + "' phải được xử lý ở mergeTableRows(), không tới đây.");
        };
    }

    // ===================== TABLE (bảng động) =====================

    private void mergeTableRows(XWPFDocument document, ReportTemplateFieldMapping tableMapping, Map<String, Object> context) {
        String openMarker = tableMapping.getPlaceholderKey();
        String tableName = extractTableName(openMarker);
        String closeMarker = "[[/TABLE:" + tableName + "]]";

        Object rowsDataObj = context.get(openMarker);
        if (!(rowsDataObj instanceof List<?> rowsData)) {
            throw new MissingReportDataException("error.missingReportData.dynamicTable", new Object[]{openMarker},
                    "Thiếu dữ liệu bảng động cho '" + openMarker + "'.");
        }

        for (XWPFTable table : document.getTables()) {
            int openIdx = findRowIndexContaining(table, openMarker);
            int closeIdx = findRowIndexContaining(table, closeMarker);
            if (openIdx < 0 || closeIdx < 0) {
                continue;
            }
            if (closeIdx <= openIdx) {
                throw new IllegalArgumentException(
                        "Marker '" + closeMarker + "' phải nằm SAU '" + openMarker + "' trong bảng.");
            }
            // Copy phòng thủ: table.getRows().subList(...) là view sống trên list gốc — addRow()
            // ngay dưới đây làm thay đổi cấu trúc list gốc, subList sẽ ném ConcurrentModificationException.
            List<XWPFTableRow> templateRows = new java.util.ArrayList<>(table.getRows().subList(openIdx + 1, closeIdx));
            if (templateRows.isEmpty()) {
                throw new IllegalArgumentException("Không có dòng mẫu nào giữa 2 marker của '" + openMarker + "'.");
            }

            int insertPos = closeIdx;
            for (Object rowObj : rowsData) {
                if (!(rowObj instanceof Map<?, ?> rowMap)) {
                    throw new IllegalStateException("Dữ liệu bảng động '" + openMarker + "' phải là danh sách Map.");
                }
                for (XWPFTableRow templateRow : templateRows) {
                    // Điền dữ liệu TRƯỚC khi addRow(): table.addRow() copy nội dung XML của row vào
                    // CTTbl tại thời điểm gọi (XmlBeans array-setter semantics) — sửa SAU khi addRow()
                    // chỉ mutate 1 object đã tách rời (detached), không còn phản ánh vào cây XML thật.
                    XWPFTableRow newRow = buildRowCopy(table, templateRow);
                    mergeRowPlaceholders(newRow, castRowMap(rowMap));
                    table.addRow(newRow, insertPos);
                    insertPos++;
                }
            }

            int newCloseIdx = closeIdx + rowsData.size() * templateRows.size();
            table.removeRow(newCloseIdx); // dòng marker đóng
            for (int i = closeIdx - 1; i >= openIdx; i--) {
                table.removeRow(i); // dòng mẫu gốc + dòng marker mở
            }
            return; // 1 mapping TABLE chỉ khớp đúng 1 bảng trong file
        }
        throw new IllegalArgumentException("Không tìm thấy cặp marker '" + openMarker + "'/'" + closeMarker + "' trong file mẫu.");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castRowMap(Map<?, ?> rowMap) {
        return (Map<String, Object>) rowMap;
    }

    private XWPFTableRow buildRowCopy(XWPFTable table, XWPFTableRow templateRow) {
        try {
            CTRow newCtRow = CTRow.Factory.parse(templateRow.getCtRow().newInputStream());
            return new XWPFTableRow(newCtRow, table);
        } catch (XmlException | IOException ex) {
            throw new UncheckedIOException("Không nhân bản được dòng bảng động.", new IOException(ex));
        }
    }

    /**
     * Thao tác ở cấp {@link XWPFTableCell} (get/setText) thay vì cấp
     * {@link XWPFRun} như {@link #replaceInParagraph} — dòng vừa nhân bản
     * qua {@link #cloneRow} được dựng lại từ XML parse lại
     * ({@code CTRow.Factory.parse}), khiến {@code XWPFParagraph.getRuns()}
     * không nhận diện đúng run hiện có (danh sách run nội bộ rỗng dù XML
     * vẫn còn nội dung) — API cấp cell đọc/ghi thẳng text XML nên không bị
     * ảnh hưởng, đã xác minh qua test round-trip.
     */
    private void mergeRowPlaceholders(XWPFTableRow row, Map<String, Object> rowMap) {
        for (XWPFTableCell cell : row.getTableCells()) {
            String original = cell.getText();
            String replaced = original;
            for (Map.Entry<String, Object> entry : rowMap.entrySet()) {
                String placeholder = "[" + entry.getKey() + "]";
                if (replaced.contains(placeholder)) {
                    replaced = replaced.replace(placeholder, MergeValueFormatter.valueToText(entry.getValue()));
                }
            }
            if (!replaced.equals(original)) {
                cell.setText(replaced);
            }
        }
    }

    private int findRowIndexContaining(XWPFTable table, String markerText) {
        List<XWPFTableRow> rows = table.getRows();
        for (int i = 0; i < rows.size(); i++) {
            StringBuilder rowText = new StringBuilder();
            for (XWPFTableCell cell : rows.get(i).getTableCells()) {
                rowText.append(cell.getText());
            }
            if (rowText.toString().contains(markerText)) {
                return i;
            }
        }
        return -1;
    }

    private String extractTableName(String openMarker) {
        // "[[TABLE:STUDENTS]]" -> "STUDENTS"
        return openMarker.substring("[[TABLE:".length(), openMarker.length() - "]]".length());
    }

    // ===================== Helpers dùng chung =====================

    private void replaceInParagraph(XWPFParagraph paragraph, UnaryOperator<String> replacer) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.isEmpty()) {
            return;
        }
        String originalText = paragraph.getText();
        String replacedText = replacer.apply(originalText);
        if (!replacedText.equals(originalText)) {
            // Giá trị nhiều dòng (VD HOMEWORK_CONTENT) — "\n" trong <w:t> Word hiển thị thành khoảng
            // trắng, phải tách thành <w:br/> để xuống dòng thật trong cùng đoạn văn.
            String[] lines = replacedText.split("\n", -1);
            XWPFRun firstRun = runs.get(0);
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText[] tArray = firstRun.getCTR().getTArray();
            if (tArray.length > 0) {
                tArray[0].setStringValue(lines[0]);
                for (int t = tArray.length - 1; t >= 1; t--) {
                    firstRun.getCTR().removeT(t);
                }
            } else {
                firstRun.setText(lines[0], 0);
            }
            for (int l = 1; l < lines.length; l++) {
                firstRun.addBreak();
                firstRun.setText(lines[l]);
            }
            for (int i = runs.size() - 1; i >= 1; i--) {
                paragraph.removeRun(i);
            }
        }
    }

    /**
     * Convert DOCX đã merge sang PDF (dùng khi actor chọn outputFormat=PDF cho mẫu
     * .docx, xem ReportGenerationService#mergeTemplate). Dựng lại HTML tương đương
     * giữ nguyên định dạng gốc ở mức run/cell (bold/italic/underline/màu chữ/cỡ chữ/
     * màu nền ô) đọc trực tiếp từ {@link XWPFRun}/{@link XWPFTableCell} — KHÔNG chỉ
     * lấy plain text như bản trước (bổ sung 2026-08-10, sửa bug: bản cũ ép cứng dòng
     * đầu MỌI bảng thành header màu cam bất kể bảng đó có phải header thật hay
     * không, và bỏ hết bold/màu/cỡ chữ gốc trong file mẫu).
     */
    byte[] convertToPdf(byte[] docxBytes) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
            html.append("<style>");
            html.append("body { font-family: ").append(cssFontFamily(defaultFontName(doc))).append("; margin: 25px; color: #1e293b; font-size: 13px; line-height: 1.6; }");
            html.append("p { margin: 4px 0; }");
            html.append("table { width: 100%; border-collapse: collapse; margin-top: 12px; margin-bottom: 12px; }");
            html.append("td { border: 1px solid #cbd5e1; padding: 7px 10px; text-align: left; vertical-align: top; font-size: 12px; }");
            html.append("</style></head><body>");

            for (org.apache.poi.xwpf.usermodel.IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph p) {
                    html.append(renderParagraph(p));
                } else if (element instanceof XWPFTable table) {
                    html.append(renderTable(table));
                }
            }
            html.append("</body></html>");
            return htmlMergeEngine.renderToPdf(html.toString(), false);
        } catch (IOException ex) {
            throw new UncheckedIOException("Không convert được DOCX sang PDF", ex);
        }
    }

    /**
     * Giữ cấu trúc bảng gốc: ô gộp ngang (gridSpan → colspan), gộp dọc (vMerge → rowspan, ô tiếp
     * nối bị bỏ), độ rộng cột theo tblGrid, căn lề đoạn văn và căn dọc của ô — nếu không, tiêu đề
     * gộp ô bị co về 1 cột và ô gộp dọc làm lệch cả hàng.
     */
    /** Font mặc định của tài liệu: style Normal, không có thì docDefaults. */
    private String defaultFontName(XWPFDocument doc) {
        try {
            var normal = doc.getStyles() != null ? doc.getStyles().getStyle("Normal") : null;
            if (normal != null && normal.getCTStyle().getRPr() != null) {
                for (var f : normal.getCTStyle().getRPr().getRFontsList()) {
                    if (f.getAscii() != null) return f.getAscii();
                }
            }
            var defaults = doc.getStyle() != null ? doc.getStyle().getDocDefaults() : null;
            if (defaults != null && defaults.getRPrDefault() != null && defaults.getRPrDefault().getRPr() != null) {
                for (var f : defaults.getRPrDefault().getRPr().getRFontsList()) {
                    if (f.getAscii() != null) return f.getAscii();
                }
            }
        } catch (Exception ignored) {
            // lấy font mặc định chỉ là tối ưu hiển thị — lỗi đọc style thì dùng Noto Sans
        }
        return null;
    }

    /** Times New Roman và các font có chân → Tinos (đã nhúng); còn lại → Noto Sans (đủ dấu tiếng Việt). */
    private String cssFontFamily(String wordFont) {
        String f = wordFont == null ? "" : wordFont.toLowerCase(java.util.Locale.ROOT);
        boolean serif = f.contains("times") || f.contains("cambria") || f.contains("georgia")
                || f.contains("garamond") || f.contains("palatino") || f.contains("book antiqua")
                || f.contains("tinos") || (f.contains("serif") && !f.contains("sans"));
        return serif ? "'" + HtmlMergeEngine.SERIF_FONT_FAMILY + "', serif" : "'PPSNotoSans', sans-serif";
    }

    private String renderTable(XWPFTable table) {
        StringBuilder sb = new StringBuilder("<table style=\"table-layout:fixed;\">");
        appendColGroup(sb, table);
        List<XWPFTableRow> rows = table.getRows();
        for (int r = 0; r < rows.size(); r++) {
            sb.append("<tr>");
            int gridCol = 0;
            for (XWPFTableCell cell : rows.get(r).getTableCells()) {
                int colSpan = gridSpan(cell);
                if (isVMergeContinuation(cell)) {
                    gridCol += colSpan;
                    continue;
                }
                int rowSpan = isVMergeRestart(cell) ? countRowSpan(rows, r, gridCol) : 1;
                StringBuilder style = new StringBuilder();
                String bg = cell.getColor();
                if (bg != null && !bg.equalsIgnoreCase("auto")) {
                    style.append("background-color:#").append(bg).append(";");
                }
                org.apache.poi.xwpf.usermodel.XWPFTableCell.XWPFVertAlign va = cell.getVerticalAlignment();
                if (va == org.apache.poi.xwpf.usermodel.XWPFTableCell.XWPFVertAlign.CENTER) {
                    style.append("vertical-align:middle;");
                } else if (va == org.apache.poi.xwpf.usermodel.XWPFTableCell.XWPFVertAlign.BOTTOM) {
                    style.append("vertical-align:bottom;");
                }
                String content = cell.getParagraphs().stream()
                        .map(p -> {
                            String runs = renderRuns(p);
                            if (runs.isEmpty()) {
                                return runs;
                            }
                            return switch (p.getAlignment()) {
                                case CENTER -> "<div style=\"text-align:center;\">" + runs + "</div>";
                                case RIGHT -> "<div style=\"text-align:right;\">" + runs + "</div>";
                                default -> runs;
                            };
                        })
                        .filter(s -> !s.isEmpty())
                        .collect(java.util.stream.Collectors.joining("<br/>"));
                sb.append("<td");
                if (colSpan > 1) sb.append(" colspan=\"").append(colSpan).append("\"");
                if (rowSpan > 1) sb.append(" rowspan=\"").append(rowSpan).append("\"");
                if (style.length() > 0) sb.append(" style=\"").append(style).append("\"");
                sb.append(">").append(content).append("</td>");
                gridCol += colSpan;
            }
            sb.append("</tr>");
        }
        return sb.append("</table>").toString();
    }

    private void appendColGroup(StringBuilder sb, XWPFTable table) {
        var grid = table.getCTTbl().getTblGrid();
        if (grid == null || grid.sizeOfGridColArray() == 0) {
            return;
        }
        long total = 0;
        for (var col : grid.getGridColList()) {
            total += col.getW() != null ? ((java.math.BigInteger) col.getW()).longValue() : 0;
        }
        if (total <= 0) {
            return;
        }
        sb.append("<colgroup>");
        for (var col : grid.getGridColList()) {
            long w = col.getW() != null ? ((java.math.BigInteger) col.getW()).longValue() : 0;
            sb.append("<col style=\"width:").append(String.format(java.util.Locale.ROOT, "%.2f", w * 100.0 / total)).append("%\"/>");
        }
        sb.append("</colgroup>");
    }

    private int gridSpan(XWPFTableCell cell) {
        var pr = cell.getCTTc().getTcPr();
        return pr != null && pr.getGridSpan() != null ? ((java.math.BigInteger) pr.getGridSpan().getVal()).intValue() : 1;
    }

    private boolean isVMergeRestart(XWPFTableCell cell) {
        var pr = cell.getCTTc().getTcPr();
        return pr != null && pr.getVMerge() != null
                && pr.getVMerge().getVal() == org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge.RESTART;
    }

    private boolean isVMergeContinuation(XWPFTableCell cell) {
        var pr = cell.getCTTc().getTcPr();
        return pr != null && pr.getVMerge() != null
                && pr.getVMerge().getVal() != org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge.RESTART;
    }

    /** Đếm số hàng ô gộp dọc bắt đầu ở hàng {@code startRow}, cột lưới {@code gridCol} kéo dài qua. */
    private int countRowSpan(List<XWPFTableRow> rows, int startRow, int gridCol) {
        int span = 1;
        for (int r = startRow + 1; r < rows.size(); r++) {
            XWPFTableCell found = null;
            int col = 0;
            for (XWPFTableCell c : rows.get(r).getTableCells()) {
                if (col == gridCol) {
                    found = c;
                    break;
                }
                col += gridSpan(c);
            }
            if (found == null || !isVMergeContinuation(found)) {
                break;
            }
            span++;
        }
        return span;
    }

    private String renderParagraph(XWPFParagraph p) {
        String content = renderRuns(p);
        if (content.isEmpty()) {
            return "<p>&nbsp;</p>";
        }
        String alignStyle = switch (p.getAlignment()) {
            case CENTER -> " style=\"text-align: center;\"";
            case RIGHT -> " style=\"text-align: right;\"";
            default -> "";
        };
        return "<p" + alignStyle + ">" + content + "</p>";
    }

    /** Giữ nguyên bold/italic/underline/màu chữ/cỡ chữ của từng {@link XWPFRun} trong 1 đoạn văn. */
    private String renderRuns(XWPFParagraph p) {
        return p.getRuns().stream().map(this::renderRun).collect(java.util.stream.Collectors.joining());
    }

    private String renderRun(XWPFRun run) {
        String text = run.text();
        if (text == null || text.isEmpty()) {
            return "";
        }
        String escaped = escapeHtml(text);
        StringBuilder styleAttrs = new StringBuilder();
        if (run.getFontFamily() != null) {
            styleAttrs.append("font-family:").append(cssFontFamily(run.getFontFamily())).append(";");
        }
        String color = run.getColor();
        if (color != null && !color.equalsIgnoreCase("auto")) {
            styleAttrs.append("color:#").append(color).append(";");
        }
        Double sizePt = run.getFontSizeAsDouble();
        if (sizePt != null && sizePt > 0) {
            styleAttrs.append("font-size:").append(sizePt).append("pt;");
        }
        String content = styleAttrs.isEmpty()
                ? escaped
                : "<span style=\"" + styleAttrs + "\">" + escaped + "</span>";
        if (run.isBold()) {
            content = "<b>" + content + "</b>";
        }
        if (run.isItalic()) {
            content = "<i>" + content + "</i>";
        }
        if (run.getUnderline() != org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE) {
            content = "<u>" + content + "</u>";
        }
        return content;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("\n", "<br/>");
    }

}
