package vn.com.pps.education.service;

import vn.com.pps.education.domain.ReportTemplate;

import java.util.Map;

/**
 * UC-68: resolve dữ liệu thực tế cho 1 loại báo cáo (Factory Method — xem
 * {@link ReportGeneratorFactory}). Mỗi implementation ứng với 1
 * {@link ReportTemplate.TemplateType}, trả về context phẳng
 * {@code key -> value} dùng để:
 * <ul>
 *   <li>Resolve placeholder FIELD: key = {@code data_path} người dùng đã
 *       cấu hình ở UC-67 bước 3.</li>
 *   <li>Resolve biến trong placeholder FORMULA: key = tên biến (chữ hoa)
 *       xuất hiện trong biểu thức, VD {@code READING}.</li>
 * </ul>
 * Value nên là {@link String} (cho FIELD hiển thị trực tiếp) hoặc
 * {@link Number} (bắt buộc nếu dùng làm biến FORMULA — xem DocxMergeEngine).
 * Thêm loại báo cáo mới = thêm 1 implementation mới, không sửa
 * implementation cũ (Open/Closed).
 */
public interface ReportDataResolver {

    ReportTemplate.TemplateType supports();

    /** UC-68 A1: nếu 1 giá trị không có sẵn, KHÔNG trả về key đó (bỏ qua) thay vì tự ý điền rỗng/0 — DocxMergeEngine sẽ báo lỗi rõ khi placeholder cần key đó không resolve được. */
    Map<String, Object> buildContext(ReportGenerationParams params);
}
