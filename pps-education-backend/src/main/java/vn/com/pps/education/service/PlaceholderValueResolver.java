package vn.com.pps.education.service;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import vn.com.pps.education.domain.ReportTemplateFieldMapping;
import vn.com.pps.education.exception.MissingReportDataException;

import java.util.List;
import java.util.Map;

/** UC-68: resolve giá trị FIELD/FORMULA từ context — dùng chung cho DocxMergeEngine, PdfMergeEngine, HtmlMergeEngine. */
final class PlaceholderValueResolver {

    private PlaceholderValueResolver() {
    }

    /** UC-68 A1: thiếu dữ liệu cho 1 placeholder (kể cả FIELD) chặn xuất thay vì tự coi là rỗng. */
    static String resolveField(ReportTemplateFieldMapping mapping, Map<String, Object> context) {
        Object value = context.get(mapping.getDataPath());
        if (value == null) {
            throw new MissingReportDataException(
                    "Thiếu dữ liệu cho trường '" + mapping.getPlaceholderKey()
                            + "' (data_path='" + mapping.getDataPath() + "').");
        }
        return MergeValueFormatter.valueToText(value);
    }

    static double evaluateFormula(String rawPlaceholder, Map<String, Object> context) {
        List<String> variableNames = ReportTemplateFormulaConverter.extractVariableNames(rawPlaceholder);
        ExpressionBuilder builder = new ExpressionBuilder(ReportTemplateFormulaConverter.toExp4jExpression(rawPlaceholder));
        if (!variableNames.isEmpty()) {
            builder.variables(variableNames.toArray(new String[0]));
        }
        Expression expression = builder.build();
        for (String variableName : variableNames) {
            Object value = context.get(variableName);
            if (!(value instanceof Number number)) {
                throw new MissingReportDataException(
                        "Thiếu dữ liệu số cho biến '" + variableName + "' trong công thức " + rawPlaceholder + ".");
            }
            expression.setVariable(variableName, number.doubleValue());
        }
        return expression.evaluate();
    }
}
