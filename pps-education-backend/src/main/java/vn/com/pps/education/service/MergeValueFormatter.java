package vn.com.pps.education.service;

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
        return String.format("%.2f", value);
    }
}
