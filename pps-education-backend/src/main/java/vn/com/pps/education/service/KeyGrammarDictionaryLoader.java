package vn.com.pps.education.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.com.pps.education.common.KeyGrammarDictionary;
import vn.com.pps.education.common.WritingV3Grade;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22 — đọc + parse từ điển Key Grammar (filter
 * 2, gói {@code key-grammar} do người training bàn giao) từ {@code resources/keygrammar/*.md}, mirror
 * cách đặt tên file/cache của {@link RubricByGradeTrackLoader} (khoá theo {@link WritingV3Grade#code()}
 * thay vì gradeLevel/track trực tiếp — 2 lớp này đã tương đương 1-1, xem {@link WritingV3Grade#forGradeTrack}).
 *
 * Khối 9 KHÔNG có từ điển (bảng "Mã khối và file" trong {@code 00_DAC_TA_GIAO_NHAN.md}: "— không có filter
 * 2") — {@link #load} trả {@code null}, caller coi như Bài Khối 9 không gắn được Key Grammar.
 *
 * Chỉ parse những gì thật sự cần dùng lúc chấm/hiển thị UI: danh sách {@code structures} (id/name/base,
 * đọc từ khối yaml §6) kèm định nghĩa nguyên văn từng mã (đọc từ mục "Định nghĩa từng mã" ở §3, đưa vào
 * prompt theo đúng yêu cầu {@code 00_DAC_TA_GIAO_NHAN.md} mục 3: "chỉ đưa vào §2 và định nghĩa của đúng
 * các mã được giao trong §3 — không đưa cả file"). KHÔNG parse bảng Unit→Sub-topic (§4) — UI đã đơn giản
 * hoá thành chọn tay cho mọi khối (đã xác nhận với người dùng 2026-09-22), không cần gợi ý tự động.
 */
@Component
public class KeyGrammarDictionaryLoader {

    private static final Logger log = LoggerFactory.getLogger(KeyGrammarDictionaryLoader.class);

    /** Khớp đúng tên file đã copy vào resources/keygrammar/ — mirror suffix scheme của RubricByGradeTrackLoader. */
    private static final Map<String, String> FILE_SUFFIX_BY_GRADE_CODE = Map.of(
            "g6", "grade6-shared",
            "g7", "grade7-ielts",
            "g7b1", "grade7-cambridge",
            "g8", "grade8-ielts",
            "g8b1", "grade8-cambridge"
    );

    private static final Pattern YAML_BLOCK = Pattern.compile("```yaml\\n(.*?)```", Pattern.DOTALL);
    private static final Pattern PASS_AT_LEAST = Pattern.compile("pass_if_at_least:\\s*(\\d+)");
    private static final Pattern GRAMMAR_CAP = Pattern.compile("grammar_cap:\\s*\\{\\s*0:\\s*(\\d+)\\s*,\\s*1:\\s*(\\d+)");
    private static final Pattern STRUCTURE_ENTRY = Pattern.compile(
            "-\\s+id:\\s*(\\S+)\\s*\\n\\s+name:\\s*\"([^\"]*)\"\\s*\\n\\s+base:\\s*(true|false)");
    private static final Pattern DEFINITION_ENTRY = Pattern.compile(
            "####\\s+`([a-zA-Z0-9_]+)`[^\\n]*\\n(.*?)(?=\\n####\\s+`|\\n##\\s+§|\\z)", Pattern.DOTALL);

    /** Cache theo grade_code — chỉ đọc + parse mỗi khối 1 lần trong suốt vòng đời ứng dụng, mirror RubricByGradeTrackLoader. */
    private final Map<String, KeyGrammarDictionary> cache = new ConcurrentHashMap<>();

    /** @return {@code null} nếu khối không có từ điển Key Grammar (Khối 9) hoặc file bị thiếu/lỗi trên classpath. */
    public KeyGrammarDictionary load(WritingV3Grade grade) {
        if (grade == null) {
            return null;
        }
        String suffix = FILE_SUFFIX_BY_GRADE_CODE.get(grade.code());
        if (suffix == null) {
            return null;
        }
        return cache.computeIfAbsent(grade.code(), code -> parse(code, suffix));
    }

    private KeyGrammarDictionary parse(String gradeCode, String suffix) {
        String classpath = "keygrammar/key-grammar-" + suffix + ".md";
        String content = readClasspathFile(classpath);
        if (content == null) {
            return null;
        }
        Matcher yamlM = YAML_BLOCK.matcher(content);
        if (!yamlM.find()) {
            log.warn("KeyGrammarDictionaryLoader: {} không có khối yaml §6 — không dùng được.", classpath);
            return null;
        }
        String yaml = yamlM.group(1);

        Matcher passM = PASS_AT_LEAST.matcher(yaml);
        int passIfAtLeast = passM.find() ? Integer.parseInt(passM.group(1)) : 2;

        Matcher capM = GRAMMAR_CAP.matcher(yaml);
        int capAtZero = 40;
        int capAtOne = 50;
        if (capM.find()) {
            capAtZero = Integer.parseInt(capM.group(1));
            capAtOne = Integer.parseInt(capM.group(2));
        }

        Map<String, String> definitions = new java.util.HashMap<>();
        Matcher defM = DEFINITION_ENTRY.matcher(content);
        while (defM.find()) {
            definitions.put(defM.group(1), defM.group(0).trim());
        }

        List<KeyGrammarDictionary.KeyGrammarStructure> structures = new ArrayList<>();
        Matcher structM = STRUCTURE_ENTRY.matcher(yaml);
        while (structM.find()) {
            String id = structM.group(1);
            String name = structM.group(2);
            boolean base = Boolean.parseBoolean(structM.group(3));
            structures.add(new KeyGrammarDictionary.KeyGrammarStructure(id, name, base, definitions.get(id)));
        }
        if (structures.isEmpty()) {
            log.warn("KeyGrammarDictionaryLoader: {} không parse được cấu trúc nào ở khối yaml §6.", classpath);
            return null;
        }
        return new KeyGrammarDictionary(gradeCode, passIfAtLeast, capAtZero, capAtOne, structures);
    }

    private String readClasspathFile(String classpath) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(classpath)) {
            if (in == null) {
                log.warn("KeyGrammarDictionaryLoader: không tìm thấy {} trên classpath.", classpath);
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("KeyGrammarDictionaryLoader: đọc {} thất bại. {}", classpath, e.getMessage());
            return null;
        }
    }
}
