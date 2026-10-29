package vn.com.pps.education.lms.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Chuẩn hoá cột "URL Audio"/"URL Hình ảnh" khi import câu hỏi từ Excel/Word: cho phép GV ghi ĐƯỜNG DẪN
 * TƯƠNG ĐỐI thay vì link tuyệt đối, hệ thống tự ghép với {@code app.media.r2.public-base-url} của môi
 * trường đang chạy (staging/production). Lý do: file Excel cũ lưu cả host (VD link r2.dev), nên đổi nơi
 * lưu/domain là chết hàng loạt link, và cùng 1 file Excel không dùng chung được giữa staging và
 * production.
 *
 * <ul>
 *   <li>Link tuyệt đối ({@code http://}, {@code https://}, hoặc chứa {@code ://}) → giữ nguyên (tương thích
 *       ngược với Excel cũ).</li>
 *   <li>{@code lms/questions/images/x.png} (bắt đầu bằng {@code lms/}) → {@code {base}/lms/questions/images/x.png}.</li>
 *   <li>{@code images/x.png} hoặc {@code audio/x.mp3} → {@code {base}/lms/questions/images/x.png}.</li>
 *   <li>Chỉ tên file {@code x.png} → thêm thư mục mặc định theo cột: {@code lms/questions/images/} cho ảnh,
 *       {@code lms/questions/audio/} cho audio.</li>
 * </ul>
 * Ô có nhiều giá trị phân tách {@code |} (ảnh từng đáp án) được xử lý từng phần tử, giữ nguyên phần tử trống
 * (vị trí ảnh bị bỏ trống — xem QuestionImportService#splitPipeKeepBlanks).
 */
@Component
class ImportMediaUrlResolver {

    private static final String QUESTION_FOLDER = "lms/questions/";

    private final String publicBaseUrl;

    ImportMediaUrlResolver(@Value("${app.media.r2.public-base-url}") String publicBaseUrl) {
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
    }

    String resolveImage(String cell) {
        return resolve(cell, "images/");
    }

    String resolveAudio(String cell) {
        return resolve(cell, "audio/");
    }

    private String resolve(String cell, String defaultSubfolder) {
        if (cell == null || cell.isBlank()) {
            return cell;
        }
        String[] parts = cell.split("\\|", -1);
        List<String> out = new ArrayList<>(parts.length);
        for (String part : parts) {
            out.add(resolveOne(part.trim(), defaultSubfolder));
        }
        return String.join("|", out);
    }

    private String resolveOne(String value, String defaultSubfolder) {
        if (value.isEmpty() || value.contains("://") || value.startsWith("data:")) {
            return value;
        }
        String path = value.replace('\\', '/');
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.startsWith("lms/")) {
            return publicBaseUrl + "/" + path;
        }
        if (path.contains("/")) {
            return publicBaseUrl + "/" + QUESTION_FOLDER + path;
        }
        return publicBaseUrl + "/" + QUESTION_FOLDER + defaultSubfolder + path;
    }

    private static String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
