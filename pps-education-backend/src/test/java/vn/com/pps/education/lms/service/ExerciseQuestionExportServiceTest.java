package vn.com.pps.education.lms.service;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import vn.com.pps.education.lms.domain.ExerciseQuestion;
import vn.com.pps.education.lms.domain.Question;
import vn.com.pps.education.lms.domain.Question.QuestionType;
import vn.com.pps.education.lms.domain.QuestionChoice;
import vn.com.pps.education.lms.service.QuestionRowParser.ParsedQuestionRow;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Xuất Excel câu hỏi của 1 Bài (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-03) — kiểm tra
 * file xuất ra đọc lại được bằng ĐÚNG parser import ({@link ExcelQuestionRowParser}) và ánh xạ đúng kind.
 * Không cần database: chỉ test {@code buildWorkbook} trên entity dựng tay.
 */
class ExerciseQuestionExportServiceTest {

    private final ExerciseQuestionExportService service = new ExerciseQuestionExportService(null, null, null);
    private final ExcelQuestionRowParser parser = new ExcelQuestionRowParser();

    private long seq = 0;
    private final List<ExerciseQuestion> items = new ArrayList<>();
    private final Map<Long, List<QuestionChoice>> choices = new HashMap<>();

    private Question question(QuestionType type, String content) {
        Question q = new Question();
        q.setId(++seq);
        q.setQuestionType(type);
        q.setContent(content);
        ExerciseQuestion eq = new ExerciseQuestion();
        eq.setQuestion(q);
        eq.setDisplayOrder(items.size() + 1);
        eq.setPoints(new BigDecimal("1.50"));
        items.add(eq);
        return q;
    }

    private void choice(Question q, String label, String content, String imageUrl, boolean correct) {
        QuestionChoice c = new QuestionChoice();
        c.setChoiceLabel(label);
        c.setContent(content);
        c.setImageUrl(imageUrl);
        c.setCorrect(correct);
        choices.computeIfAbsent(q.getId(), k -> new ArrayList<>()).add(c);
    }

    private List<ParsedQuestionRow> exportAndParse() throws IOException {
        byte[] bytes = service.buildWorkbook(items, choices);
        return parser.parse(new ByteArrayInputStream(bytes));
    }

    @Test
    void multipleChoiceRoundTripsAsTracNghiem() throws IOException {
        Question q = question(QuestionType.MULTIPLE_CHOICE, "Capital of France?");
        q.setDifficulty(Question.Difficulty.EASY);
        q.setExplanation("Paris");
        q.setTags(List.of("geo", "easy"));
        choice(q, "A", "London", null, false);
        choice(q, "B", "Paris", null, true);
        choice(q, "C", "Berlin", null, false);

        List<ParsedQuestionRow> rows = exportAndParse();

        assertThat(rows).hasSize(1);
        ParsedQuestionRow r = rows.get(0);
        assertThat(r.kind()).isEqualTo("TRAC_NGHIEM");
        assertThat(r.difficulty()).isEqualTo("EASY");
        assertThat(r.content()).isEqualTo("Capital of France?");
        assertThat(r.choiceA()).isEqualTo("London");
        assertThat(r.choiceB()).isEqualTo("Paris");
        assertThat(r.choiceC()).isEqualTo("Berlin");
        assertThat(r.choiceD()).isNull();
        assertThat(r.correctAnswer()).isEqualTo("B");
        assertThat(r.defaultPoints()).isEqualTo("1.5");
        assertThat(r.explanation()).isEqualTo("Paris");
        assertThat(r.tags()).isEqualTo("geo,easy");
    }

    @Test
    void mapsEachQuestionTypeToImportKind() throws IOException {
        Question voice = question(QuestionType.MULTIPLE_CHOICE, "Listen");
        voice.setAudioUrl("https://x/a.mp3");
        choice(voice, "A", "ship", null, false);
        choice(voice, "B", "sheep", null, true);

        Question pictures = question(QuestionType.MULTIPLE_CHOICE, "What time?");
        pictures.setAudioUrl("https://x/b.mp3");
        choice(pictures, "A", "A", "https://x/1.png", false);
        choice(pictures, "B", "B", "https://x/2.png", true);

        Question fill = question(QuestionType.FILL_IN_BLANK, "She ___ to school.");
        fill.setCorrectAnswerText("goes");

        Question listenFill = question(QuestionType.FILL_IN_BLANK, "Listen ___");
        listenFill.setCorrectAnswerText("drives");
        listenFill.setAudioUrl("https://x/c.mp3");

        question(QuestionType.ESSAY, "Write an essay");

        Question speaking = question(QuestionType.SPEAKING, "Read aloud");
        speaking.setReferencePassage("enthusiasm");

        Question listenSubmit = question(QuestionType.SPEAKING, "Record");
        listenSubmit.setSkill(Question.Skill.LISTENING);
        listenSubmit.setAudioUrl("https://x/d.mp3");

        Question bank = question(QuestionType.WORD_BANK, "1. ___ 2. ___");
        bank.setStructuredContent(Map.of("blanks", List.of("under", "next to")));

        Question bankPicture = question(QuestionType.WORD_BANK, "1. ___");
        bankPicture.setImageUrl("https://x/p.png");
        bankPicture.setStructuredContent(Map.of("blanks", List.of("under"), "wordBankOptions", List.of("under", "behind")));

        Question passage = question(QuestionType.WORD_BANK, "(1) ___ (2) ___");
        passage.setStructuredContent(Map.of("blanks", List.of("a", ""), "inputMode", "text"));

        Question sentence = question(QuestionType.SENTENCE_BUILDING, "Order");
        sentence.setStructuredContent(Map.of("chunks", List.of("This", "is", "a", "pen")));

        Question letters = question(QuestionType.SENTENCE_BUILDING, "Smile");
        letters.setImageUrl("https://x/s.png");
        letters.setStructuredContent(Map.of("chunks", List.of("s", "m", "i", "l", "e")));

        List<ParsedQuestionRow> rows = exportAndParse();

        assertThat(rows).extracting(ParsedQuestionRow::kind).containsExactly(
                "TRAC_NGHIEM_VOICE", "NGHE_CHON_HINH", "DIEN_TU", "NGHE_DIEN_TU", "TU_LUAN", "SPEAKING",
                "NGHE_NOP_AUDIO", "DIEN_TU_HOP_TU_VUNG", "DIEN_TU_HOP_TU_VUNG_ANH", "DIEN_TU_DOAN_VAN",
                "SAP_XEP_CAU", "SAP_XEP_CHU_CAI");
        assertThat(rows.get(1).imageUrl()).isEqualTo("https://x/1.png|https://x/2.png");
        assertThat(rows.get(1).choiceA()).isNull();
        assertThat(rows.get(1).correctAnswer()).isEqualTo("B");
        assertThat(rows.get(7).correctAnswer()).isEqualTo("under|next to");
        assertThat(rows.get(8).referencePassage()).isEqualTo("under, behind");
        assertThat(rows.get(9).correctAnswer()).isEqualTo("a|");
        assertThat(rows.get(10).correctAnswer()).isEqualTo("This|is|a|pen");
    }

    @Test
    void consecutiveFillInBlankWithSameGroupKeyBecomeOneDienTuNhomRow() throws IOException {
        for (String[] pair : new String[][]{{"Tom is very ___.", "smart"}, {"English is my ___ subject.", "favourite"}}) {
            Question q = question(QuestionType.FILL_IN_BLANK, pair[0]);
            q.setCorrectAnswerText(pair[1]);
            q.setGroupKey("fillblank-import-1");
            q.setStructuredContent(Map.of("wordBox", List.of("smart", "favourite", "coach")));
        }

        List<ParsedQuestionRow> rows = exportAndParse();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).kind()).isEqualTo("DIEN_TU_NHOM");
        assertThat(rows.get(0).content()).isEqualTo("Tom is very ___.|English is my ___ subject.");
        assertThat(rows.get(0).correctAnswer()).isEqualTo("smart|favourite");
        assertThat(rows.get(0).referencePassage()).isEqualTo("smart, favourite, coach");
    }

    @Test
    void questionsWithoutImportKindGoToSkippedSheetAndKeepMainSheetImportable() throws IOException {
        Question normal = question(QuestionType.ESSAY, "Essay");
        Question multiAnswer = question(QuestionType.MULTIPLE_ANSWER, "Pick all that apply");
        choice(multiAnswer, "A", "x", null, true);
        choice(multiAnswer, "B", "y", null, true);

        byte[] bytes = service.buildWorkbook(items, choices);

        assertThat(parser.parse(new ByteArrayInputStream(bytes))).extracting(ParsedQuestionRow::content).containsExactly(normal.getContent());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            Sheet skipped = workbook.getSheetAt(1);
            assertThat(skipped.getRow(1).getCell(0).getStringCellValue()).isEqualTo("2");
            assertThat(skipped.getRow(1).getCell(1).getStringCellValue()).isEqualTo("MULTIPLE_ANSWER");
            assertThat(skipped.getRow(1).getCell(2).getStringCellValue()).isEqualTo("Pick all that apply");
        }
    }

    @Test
    void emptyExerciseStillProducesHeaderOnlySheet() throws IOException {
        byte[] bytes = service.buildWorkbook(List.of(), Map.of());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("Loại câu hỏi");
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isZero();
        }
    }
}
