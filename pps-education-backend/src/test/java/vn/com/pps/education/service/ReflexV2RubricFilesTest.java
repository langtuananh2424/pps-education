package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Port của {@code check-rubrics.js} (người training bàn giao): từng file rubric v2 phải còn ĐỦ mọi checkpoint,
 * mỗi checkpoint đúng 1 dòng bảng, và mọi dòng bảng đủ số cột như dòng tiêu đề. Lý do tồn tại (theo họ): một
 * lần vá bằng regex từng nuốt tiền tố "|**P1**|" làm vỡ bảng — AI vẫn chấm bình thường, không báo lỗi, chỉ là
 * nó không còn nhìn thấy checkpoint đó. Loại lỗi này phải bắt bằng máy. Test này bảo vệ cả việc thay file
 * rubric mới sau này (mỗi lần người training gửi bản cập nhật).
 */
class ReflexV2RubricFilesTest {

    private static final Map<String, List<String>> EXPECT = new LinkedHashMap<>();

    static {
        EXPECT.put("rubric-grade6-speaking.md", List.of("P1", "P2", "P3", "P4", "P5"));
        EXPECT.put("rubric-grade6-writing.md", List.of("G1", "G2", "G3", "G4", "G5"));
        EXPECT.put("rubric-grade7-cambridge-speaking.md", List.of("D1", "D2", "D3", "D4", "D5", "P1", "P2", "P3", "P4", "P5"));
        EXPECT.put("rubric-grade7-cambridge-writing.md", List.of("G1", "G2", "G3", "G4", "G5"));
        EXPECT.put("rubric-grade7-ielts-speaking.md", List.of("F1", "F2", "F3", "F4", "F5", "L1", "L2", "L3", "L4", "L5", "P1", "P2", "P3", "P4", "P5"));
        EXPECT.put("rubric-grade7-ielts-writing.md", List.of("L1", "L2", "L3", "L4", "L5", "R1", "R2", "R3", "R4", "R5"));
        EXPECT.put("rubric-grade8-cambridge-speaking.md", List.of("D1", "D2", "D3", "D4", "D5", "P1", "P2", "P3", "P4", "P5"));
        EXPECT.put("rubric-grade8-cambridge-writing.md", List.of("G1", "G2", "G3", "G4", "G5"));
        EXPECT.put("rubric-grade8-ielts-speaking.md", List.of("F1", "F2", "F3", "F4", "F5", "L1", "L2", "L3", "L4", "L5", "P1", "P2", "P3", "P4", "P5"));
        EXPECT.put("rubric-grade8-ielts-writing.md", List.of("L1", "L2", "L3", "L4", "L5", "R1", "R2", "R3", "R4", "R5"));
        EXPECT.put("rubric-grade9-ielts-speaking.md", List.of("F1", "F2", "F3", "F4", "F5", "L1", "L2", "L3", "L4", "L5", "P1", "P2", "P3", "P4", "P5"));
        EXPECT.put("rubric-grade9-ielts-writing.md", List.of("L1", "L2", "L3", "L4", "L5", "R1", "R2", "R3", "R4", "R5"));
    }

    private static String read(String file) throws Exception {
        try (InputStream in = ReflexV2RubricFilesTest.class.getClassLoader().getResourceAsStream("rubrics-v2/" + file)) {
            assertThat(in).as("thiếu file rubric " + file).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void everyRubricKeepsAllCheckpointRows_exactlyOnce() throws Exception {
        for (Map.Entry<String, List<String>> e : EXPECT.entrySet()) {
            String[] lines = read(e.getKey()).split("\n");
            for (String id : e.getValue()) {
                Pattern row = Pattern.compile("^\\|\\*{0,2}" + id + "\\*{0,2}\\|");
                long hits = java.util.Arrays.stream(lines).filter(l -> row.matcher(l).find()).count();
                assertThat(hits).as(e.getKey() + " — checkpoint " + id).isEqualTo(1);
            }
        }
    }

    @Test
    void everyTableRowHasSameColumnCountAsItsHeader() throws Exception {
        for (String file : EXPECT.keySet()) {
            Integer header = null;
            int lineNo = 0;
            for (String l : read(file).split("\n")) {
                lineNo++;
                if (!l.startsWith("|")) {
                    header = null;
                    continue;
                }
                if (l.matches("^\\|[\\s\\-:|]+\\|?\\s*$")) {
                    continue; // dòng ngăn cách |---|---|
                }
                int cols = l.split("\\|", -1).length;
                if (header == null) {
                    header = cols;
                } else {
                    assertThat(cols).as(file + " dòng " + lineNo + " lệch số cột so với tiêu đề").isEqualTo(header);
                }
            }
        }
    }
}
