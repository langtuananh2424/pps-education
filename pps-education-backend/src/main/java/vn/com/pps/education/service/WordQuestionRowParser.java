package vn.com.pps.education.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UC-40 (bổ sung, đã xác nhận với người dùng): đọc file mẫu Word soạn đề
 * nhanh — mẫu CỨNG (không AI/OCR nhận diện tự do, đã xác nhận đánh đổi độ
 * phức tạp/rủi ro sai sót). Mỗi câu hỏi là 1 block, các block cách nhau 1
 * dòng chỉ chứa "---". Dòng đầu block bắt buộc dạng "[LOAI_CAU_HOI]"; các
 * dòng sau là "Nhãn: giá trị" (nhãn so khớp không phân biệt hoa/thường/dấu,
 * chấp nhận cả tiếng Việt lẫn tiếng Anh — xem
 * {@link QuestionImportFieldAliases}, Kho đề bổ sung 2026-07-30) hoặc dòng
 * đáp án "A. ..." .. "D. ...". Dòng không khớp nhãn/đáp án nào được nối
 * thêm vào field đang đọc dở gần nhất (cho phép 1 đoạn văn/nội dung trải
 * nhiều paragraph Word).
 */
@Service
public class WordQuestionRowParser implements QuestionRowParser {

    private static final Pattern KIND_PATTERN = Pattern.compile("^\\[(.+)]$");
    private static final Pattern CHOICE_PATTERN = Pattern.compile("^([A-Da-d])[.)]\\s*(.+)$");
    private static final Pattern LABEL_PATTERN = Pattern.compile("^([^:]{1,40}):\\s*(.*)$");

    @Override
    public boolean supports(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".docx");
    }

    @Override
    public List<ParsedQuestionRow> parse(InputStream inputStream) {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            List<ParsedQuestionRow> rows = new ArrayList<>();
            MutableRow current = null;
            int blockNumber = 0;
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText() == null ? "" : paragraph.getText().trim();
                if (text.isEmpty()) {
                    continue;
                }
                if (text.equals("---")) {
                    if (current != null) {
                        rows.add(current.toParsedRow());
                    }
                    current = null;
                    continue;
                }
                if (current == null) {
                    Matcher kindMatcher = KIND_PATTERN.matcher(text);
                    blockNumber++;
                    current = new MutableRow(blockNumber);
                    if (kindMatcher.matches()) {
                        current.kind = kindMatcher.group(1).trim();
                        continue;
                    }
                    // Dòng đầu block không đúng cú pháp [LOAI] — vẫn tạo block để
                    // QuestionImportService báo lỗi "loại câu hỏi không hợp lệ" rõ
                    // ràng thay vì âm thầm bỏ qua cả block.
                }
                applyLine(current, text);
            }
            if (current != null) {
                rows.add(current.toParsedRow());
            }
            return rows;
        } catch (IOException | RuntimeException ex) {
            throw new UncheckedIOException("File sai định dạng Word (.docx): " + ex.getMessage(), new IOException(ex));
        }
    }

    private void applyLine(MutableRow row, String text) {
        Matcher choiceMatcher = CHOICE_PATTERN.matcher(text);
        if (choiceMatcher.matches()) {
            String letter = choiceMatcher.group(1).toUpperCase(Locale.ROOT);
            String value = choiceMatcher.group(2).trim();
            switch (letter) {
                case "A" -> row.choiceA = value;
                case "B" -> row.choiceB = value;
                case "C" -> row.choiceC = value;
                default -> row.choiceD = value;
            }
            row.lastField = "choice" + letter;
            return;
        }
        Matcher labelMatcher = LABEL_PATTERN.matcher(text);
        if (labelMatcher.matches()) {
            String value = labelMatcher.group(2).trim();
            // choiceA-D không nhận qua nhãn "Nhãn: giá trị" (chỉ qua dòng "A. ..."-"D. ..." ở trên) nên bỏ qua ở đây dù có trong alias map.
            String field = QuestionImportFieldAliases.resolveField(labelMatcher.group(1));
            if (field != null && !field.startsWith("choice")) {
                row.set(field, value);
                row.lastField = field;
                return;
            }
            // Nhãn lạ (VD người dùng gõ sai chính tả) — coi như dòng nối tiếp field gần nhất thay vì mất trắng nội dung.
        }
        row.append(row.lastField, text);
    }

    private static final class MutableRow {
        final int blockNumber;
        String kind;
        String difficulty;
        String content;
        String choiceA;
        String choiceB;
        String choiceC;
        String choiceD;
        String correctAnswer;
        String audioUrl;
        String imageUrl;
        String referencePassage;
        String defaultPoints;
        String explanation;
        String tags;
        String lastField = "content";

        MutableRow(int blockNumber) {
            this.blockNumber = blockNumber;
        }

        void set(String field, String value) {
            switch (field) {
                case "content" -> content = value;
                case "correctAnswer" -> correctAnswer = value;
                case "difficulty" -> difficulty = value;
                case "defaultPoints" -> defaultPoints = value;
                case "explanation" -> explanation = value;
                case "audioUrl" -> audioUrl = value;
                case "imageUrl" -> imageUrl = value;
                case "referencePassage" -> referencePassage = value;
                case "tags" -> tags = value;
                default -> { }
            }
        }

        void append(String field, String extra) {
            switch (field) {
                case "content" -> content = content == null ? extra : content + "\n" + extra;
                case "correctAnswer" -> correctAnswer = correctAnswer == null ? extra : correctAnswer + "\n" + extra;
                case "explanation" -> explanation = explanation == null ? extra : explanation + "\n" + extra;
                case "referencePassage" -> referencePassage = referencePassage == null ? extra : referencePassage + "\n" + extra;
                case "choiceA" -> choiceA = choiceA == null ? extra : choiceA + " " + extra;
                case "choiceB" -> choiceB = choiceB == null ? extra : choiceB + " " + extra;
                case "choiceC" -> choiceC = choiceC == null ? extra : choiceC + " " + extra;
                case "choiceD" -> choiceD = choiceD == null ? extra : choiceD + " " + extra;
                default -> content = content == null ? extra : content + "\n" + extra;
            }
        }

        ParsedQuestionRow toParsedRow() {
            return new ParsedQuestionRow(blockNumber, kind, difficulty, content, choiceA, choiceB, choiceC, choiceD,
                    correctAnswer, audioUrl, imageUrl, referencePassage, defaultPoints, explanation, tags);
        }
    }
}
