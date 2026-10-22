package vn.com.pps.education.report.service;

/** UC-68: định dạng giá trị số/chuỗi khi điền vào báo cáo — dùng chung cho DocxMergeEngine và PdfMergeEngine. */
final class MergeValueFormatter {

    private MergeValueFormatter() {
    }

    static String valueToText(Object value) {
        return value instanceof Number number ? formatNumber(number.doubleValue()) : String.valueOf(value);
    }

    static String formatNumber(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        // Locale.ROOT bắt buộc — thiếu sẽ dùng locale mặc định của máy chạy JVM (VD máy Windows tiếng Việt
        // ra dấu phẩy thập phân "7,50" thay vì "7.50"), gây lệch DOCX/PDF xuất ra tuỳ máy host, ngoài dự
        // kiến của DocxMergeEngineTest. Mirror DocxMergeEngine.java:423 đã làm đúng cho field khác.
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
