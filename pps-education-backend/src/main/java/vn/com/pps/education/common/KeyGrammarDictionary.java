package vn.com.pps.education.common;

import java.util.List;
import java.util.Optional;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22 — dữ liệu đã parse từ 1 file từ điển
 * {@code KeyGrammar_Grade*.md} (gói {@code key-grammar} do người training bàn giao cùng đợt với rubric
 * Writing "v3"), xem {@link vn.com.pps.education.service.KeyGrammarDictionaryLoader}.
 *
 * {@code passIfAtLeast}/{@code grammarCapAtZero}/{@code grammarCapAtOne} đọc TRỰC TIẾP từ khối yaml §6
 * của từng file (không hardcode) — dù ở đợt bàn giao 22/09/2026 cả 5 khối đều cùng giá trị
 * {@code pass_if_at_least=2}/{@code grammar_cap={0:40,1:50}}, để nếu người training sửa số cho 1 khối
 * sau này thì chỉ cần sửa file {@code .md}, không cần sửa code (business-fidelity).
 */
public record KeyGrammarDictionary(String gradeCode, int passIfAtLeast, int grammarCapAtZero, int grammarCapAtOne,
                                    List<KeyGrammarStructure> structures) {

    /** 1 mã cấu trúc trong từ điển — {@code definitionMarkdown} là nguyên văn mục "Định nghĩa từng mã" (§3), đưa thẳng vào prompt chấm. */
    public record KeyGrammarStructure(String id, String name, boolean base, String definitionMarkdown) {
    }

    public Optional<KeyGrammarStructure> find(String id) {
        return structures.stream().filter(s -> s.id().equals(id)).findFirst();
    }
}
