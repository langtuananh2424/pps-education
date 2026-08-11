package vn.com.pps.education.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UC-67 bước 2/3: chuyển cú pháp công thức placeholder dùng riêng dấu
 * ngoặc vuông làm cả toán tử nhóm (thay cho dấu ngoặc đơn) lẫn ký hiệu
 * trường dữ liệu — VD {@code [[[READING]+[LISTENING]+[SPEAKING]+[WRITING]]/4]}
 * — sang biểu thức chuẩn cho exp4j: {@code ((READING+LISTENING+SPEAKING+WRITING)/4)}.
 *
 * Quy tắc: 1 cặp {@code [...]} là "lá" (tên trường) khi nội dung giữa nó
 * không chứa dấu ngoặc vuông lồng bên trong và khớp {@code [A-Z][A-Z0-9_]*}
 * — khi đó thay bằng chính tên trường (biến số cho exp4j), không phát sinh
 * dấu ngoặc. Mọi cặp {@code [...]} còn lại (có lồng bên trong) đóng vai trò
 * gom nhóm toán tử — thay {@code [} bằng {@code (} và {@code ]} bằng {@code )}.
 */
final class ReportTemplateFormulaConverter {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\[([A-Z][A-Z0-9_]*)\\]");

    private ReportTemplateFormulaConverter() {
    }

    /** Tên các biến (lá {@code [FIELD]}) xuất hiện trong 1 placeholder công thức, theo đúng thứ tự xuất hiện, không trùng lặp. */
    static List<String> extractVariableNames(String raw) {
        List<String> names = new ArrayList<>();
        Matcher m = VARIABLE_PATTERN.matcher(raw);
        while (m.find()) {
            String name = m.group(1);
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    static String toExp4jExpression(String raw) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        int n = raw.length();
        while (i < n) {
            char c = raw.charAt(i);
            if (c == '[') {
                int j = i + 1;
                while (j < n && raw.charAt(j) != '[' && raw.charAt(j) != ']') {
                    j++;
                }
                if (j < n && raw.charAt(j) == ']') {
                    String token = raw.substring(i + 1, j);
                    if (token.matches("[A-Z][A-Z0-9_]*")) {
                        out.append(token);
                        i = j + 1;
                        continue;
                    }
                }
                out.append('(');
                i++;
            } else if (c == ']') {
                out.append(')');
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
