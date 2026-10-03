package vn.com.pps.education.lms.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.lms.domain.Exercise;
import vn.com.pps.education.lms.domain.ExerciseQuestion;
import vn.com.pps.education.lms.domain.Question;
import vn.com.pps.education.lms.domain.QuestionChoice;
import vn.com.pps.education.lms.repository.ExerciseQuestionRepository;
import vn.com.pps.education.lms.repository.ExerciseRepository;
import vn.com.pps.education.lms.repository.QuestionChoiceRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Xuất Excel toàn bộ câu hỏi của 1 Bài (Exercise) — bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-10-03. File xuất ra dùng ĐÚNG định dạng file mẫu import ({@link ExcelQuestionRowParser}, 14 cột
 * header tiếng Việt, mỗi câu 1 dòng, cột "Loại câu hỏi" là mã {@code kind} của {@link QuestionImportService})
 * nên có thể import ngược lại vào Ngân hàng câu hỏi/Bài khác.
 *
 * <p>Ánh xạ ngược Question → kind (đối xứng với {@code QuestionImportService#mapToRequest}):
 * MULTIPLE_CHOICE → TRAC_NGHIEM / TRAC_NGHIEM_VOICE (có audio) / NGHE_CHON_HINH (có audio + ảnh theo đáp án);
 * FILL_IN_BLANK → DIEN_TU / NGHE_DIEN_TU (có audio); ESSAY → TU_LUAN; SPEAKING → SPEAKING / NGHE_NOP_AUDIO
 * (skill LISTENING); WORD_BANK → DIEN_TU_DOAN_VAN (inputMode=text) / DIEN_TU_HOP_TU_VUNG_ANH (có ảnh) /
 * DIEN_TU_HOP_TU_VUNG; SENTENCE_BUILDING → SAP_XEP_CAU / SAP_XEP_CHU_CAI (có ảnh).
 * Các câu FILL_IN_BLANK liên tiếp cùng groupKey (không audio) gộp lại thành 1 dòng DIEN_TU_NHOM như lúc import.
 * Câu đọc hiểu dùng chung đoạn văn/audio xuất thành các dòng liền nhau cùng giá trị — import tự gộp nhóm
 * lại ({@code computeAutoGroupKeys}), nên không cần kind DOC_HIEU_LUOI/DOC_DIEN_TU.
 *
 * <p>Câu không có dạng import tương ứng (MULTIPLE_ANSWER, TRUE_FALSE) không đưa vào sheet chính mà liệt kê ở
 * sheet "Không xuất được" để không mất dấu âm thầm. Cột "Điểm" lấy điểm của câu TRONG Bài
 * ({@code exercise_questions.points}), không phải điểm mặc định của ngân hàng.
 */
@Service
public class ExerciseQuestionExportService {

    static final String[] HEADERS = {
            "Loại câu hỏi", "Độ khó", "Nội dung", "Đáp án A", "Đáp án B", "Đáp án C", "Đáp án D",
            "Đáp án đúng", "URL Audio", "URL Hình ảnh", "Đoạn văn tham chiếu", "Điểm", "Giải thích", "Tags"
    };
    private static final String SKIPPED_SHEET = "Không xuất được";
    private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** Kết quả xuất: tên file gợi ý + nội dung .xlsx. */
    public record ExportedFile(String filename, String contentType, byte[] content) {
    }

    private final ExerciseRepository exerciseRepository;
    private final ExerciseQuestionRepository exerciseQuestionRepository;
    private final QuestionChoiceRepository questionChoiceRepository;

    public ExerciseQuestionExportService(ExerciseRepository exerciseRepository,
                                         ExerciseQuestionRepository exerciseQuestionRepository,
                                         QuestionChoiceRepository questionChoiceRepository) {
        this.exerciseRepository = exerciseRepository;
        this.exerciseQuestionRepository = exerciseQuestionRepository;
        this.questionChoiceRepository = questionChoiceRepository;
    }

    @Transactional(readOnly = true)
    public ExportedFile exportExercise(Long exerciseId) {
        Exercise exercise = exerciseRepository.findById(exerciseId)
                .orElseThrow(() -> new ResourceNotFoundException("error.exercise.notFoundById",
                        new Object[]{exerciseId}, "Không tìm thấy đề id=" + exerciseId));
        List<ExerciseQuestion> items = exerciseQuestionRepository.findByExerciseIdOrderByDisplayOrder(exerciseId);
        Map<Long, List<QuestionChoice>> choicesByQuestion = new HashMap<>();
        for (ExerciseQuestion item : items) {
            Long questionId = item.getQuestion().getId();
            choicesByQuestion.computeIfAbsent(questionId, questionChoiceRepository::findByQuestionIdOrderByDisplayOrder);
        }
        return new ExportedFile("bai-" + safeFilenamePart(exercise.getCode()) + ".xlsx", XLSX_CONTENT_TYPE,
                buildWorkbook(items, choicesByQuestion));
    }

    /** Tách riêng khỏi truy vấn DB để test không cần database. */
    byte[] buildWorkbook(List<ExerciseQuestion> items, Map<Long, List<QuestionChoice>> choicesByQuestion) {
        List<String[]> rows = new ArrayList<>();
        List<String[]> skipped = new ArrayList<>();

        int index = 0;
        while (index < items.size()) {
            ExerciseQuestion item = items.get(index);
            Question question = item.getQuestion();

            int groupEnd = fillInBlankGroupEnd(items, index);
            if (groupEnd > index + 1) {
                rows.add(toFillInBlankGroupRow(items.subList(index, groupEnd)));
                index = groupEnd;
                continue;
            }

            List<QuestionChoice> choices = choicesByQuestion.getOrDefault(question.getId(), List.of());
            String[] row = toRow(item, choices);
            if (row == null) {
                skipped.add(new String[]{String.valueOf(index + 1), question.getQuestionType().name(), question.getContent()});
            } else {
                rows.add(row);
            }
            index++;
        }
        return writeWorkbook(rows, skipped);
    }

    private String[] toRow(ExerciseQuestion item, List<QuestionChoice> choices) {
        Question q = item.getQuestion();
        String kind;
        String choiceA = null;
        String choiceB = null;
        String choiceC = null;
        String choiceD = null;
        String correct = null;
        String audio = q.getAudioUrl();
        String image = q.getImageUrl();
        String passage = q.getReferencePassage();

        switch (q.getQuestionType()) {
            case MULTIPLE_CHOICE -> {
                if (choices.size() < 2 || choices.size() > 4
                        || choices.stream().filter(QuestionChoice::isCorrect).count() != 1) {
                    return null;
                }
                boolean pictureChoice = isPresent(audio) && choices.stream().anyMatch(c -> isPresent(c.getImageUrl()));
                kind = pictureChoice ? "NGHE_CHON_HINH" : isPresent(audio) ? "TRAC_NGHIEM_VOICE" : "TRAC_NGHIEM";
                String[] texts = new String[4];
                List<String> images = new ArrayList<>();
                for (int i = 0; i < choices.size(); i++) {
                    QuestionChoice c = choices.get(i);
                    String label = String.valueOf((char) ('A' + i));
                    // Nghe chọn hình: content mặc định = chữ cái nhãn → để trống cột đáp án cho gọn, import tự điền lại.
                    texts[i] = pictureChoice && label.equals(c.getContent()) ? null : c.getContent();
                    images.add(nullToEmpty(c.getImageUrl()));
                    if (c.isCorrect()) {
                        correct = label;
                    }
                }
                choiceA = texts[0];
                choiceB = texts[1];
                choiceC = texts[2];
                choiceD = texts[3];
                if (pictureChoice) {
                    image = String.join("|", images);
                }
            }
            case FILL_IN_BLANK -> {
                if (!isPresent(q.getCorrectAnswerText())) {
                    return null;
                }
                kind = isPresent(audio) ? "NGHE_DIEN_TU" : "DIEN_TU";
                correct = q.getCorrectAnswerText();
            }
            case ESSAY -> kind = "TU_LUAN";
            case SPEAKING -> kind = q.getSkill() == Question.Skill.LISTENING ? "NGHE_NOP_AUDIO" : "SPEAKING";
            case WORD_BANK -> {
                List<String> blanks = stringList(structured(q, "blanks"));
                if (blanks.isEmpty()) {
                    return null;
                }
                boolean textMode = "text".equals(structured(q, "inputMode"));
                kind = textMode ? "DIEN_TU_DOAN_VAN" : isPresent(image) ? "DIEN_TU_HOP_TU_VUNG_ANH" : "DIEN_TU_HOP_TU_VUNG";
                correct = String.join("|", blanks);
                List<String> options = stringList(structured(q, "wordBankOptions"));
                if (!options.isEmpty()) {
                    passage = String.join(", ", options);
                }
            }
            case SENTENCE_BUILDING -> {
                List<String> chunks = stringList(structured(q, "chunks"));
                if (chunks.size() < 2) {
                    return null;
                }
                kind = isPresent(image) ? "SAP_XEP_CHU_CAI" : "SAP_XEP_CAU";
                correct = String.join("|", chunks);
            }
            default -> {
                return null;
            }
        }

        return new String[]{kind, difficulty(q), q.getContent(), choiceA, choiceB, choiceC, choiceD, correct,
                audio, image, passage, plain(item.getPoints()), q.getExplanation(), tags(q)};
    }

    /** Chỉ số kết thúc (exclusive) của chuỗi FILL_IN_BLANK cùng groupKey liên tiếp bắt đầu tại {@code start}; = start+1 nếu không gộp được. */
    private int fillInBlankGroupEnd(List<ExerciseQuestion> items, int start) {
        Question first = items.get(start).getQuestion();
        if (!isGroupableFillInBlank(first)) {
            return start + 1;
        }
        int end = start + 1;
        while (end < items.size()) {
            Question next = items.get(end).getQuestion();
            if (!isGroupableFillInBlank(next) || !Objects.equals(first.getGroupKey(), next.getGroupKey())) {
                break;
            }
            end++;
        }
        return end;
    }

    private boolean isGroupableFillInBlank(Question q) {
        return q.getQuestionType() == Question.QuestionType.FILL_IN_BLANK
                && isPresent(q.getGroupKey()) && !isPresent(q.getAudioUrl())
                && isPresent(q.getCorrectAnswerText())
                && !q.getContent().contains("|") && !q.getCorrectAnswerText().contains("|")
                && (q.getImageUrl() == null || !q.getImageUrl().contains("|"));
    }

    private String[] toFillInBlankGroupRow(List<ExerciseQuestion> group) {
        Question first = group.get(0).getQuestion();
        List<String> contents = new ArrayList<>();
        List<String> answers = new ArrayList<>();
        List<String> images = new ArrayList<>();
        boolean anyImage = false;
        for (ExerciseQuestion item : group) {
            Question q = item.getQuestion();
            contents.add(q.getContent());
            answers.add(q.getCorrectAnswerText());
            images.add(nullToEmpty(q.getImageUrl()));
            anyImage |= isPresent(q.getImageUrl());
        }
        String passage = first.getReferencePassage();
        if (!isPresent(passage)) {
            List<String> wordBox = stringList(structured(first, "wordBox"));
            passage = wordBox.isEmpty() ? null : String.join(", ", wordBox);
        }
        return new String[]{"DIEN_TU_NHOM", difficulty(first), String.join("|", contents), null, null, null, null,
                String.join("|", answers), null, anyImage ? String.join("|", images) : null, passage,
                plain(group.get(0).getPoints()), first.getExplanation(), tags(first)};
    }

    private byte[] writeWorkbook(List<String[]> rows, List<String[]> skipped) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(workbook);
            Sheet sheet = workbook.createSheet("Câu hỏi");
            writeRow(sheet.createRow(0), HEADERS, header);
            for (int i = 0; i < rows.size(); i++) {
                writeRow(sheet.createRow(i + 1), rows.get(i), null);
            }
            for (int col = 0; col < HEADERS.length; col++) {
                sheet.setColumnWidth(col, col == 2 || col == 10 || col == 12 ? 12000 : 5000);
            }
            sheet.createFreezePane(0, 1);

            if (!skipped.isEmpty()) {
                // Sheet thứ 2 — parser import chỉ đọc sheet đầu (getSheetAt(0)) nên không ảnh hưởng import lại.
                Sheet skippedSheet = workbook.createSheet(SKIPPED_SHEET);
                writeRow(skippedSheet.createRow(0), new String[]{"Thứ tự trong Bài", "Loại câu hỏi (hệ thống)", "Nội dung"}, header);
                for (int i = 0; i < skipped.size(); i++) {
                    writeRow(skippedSheet.createRow(i + 1), skipped.get(i), null);
                }
                skippedSheet.setColumnWidth(0, 5000);
                skippedSheet.setColumnWidth(1, 7000);
                skippedSheet.setColumnWidth(2, 16000);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Không tạo được file Excel.", ex);
        }
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        Font bold = workbook.createFont();
        bold.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(bold);
        return style;
    }

    private void writeRow(Row row, String[] values, CellStyle style) {
        for (int col = 0; col < values.length; col++) {
            if (values[col] == null) {
                continue;
            }
            Cell cell = row.createCell(col);
            cell.setCellValue(values[col]);
            if (style != null) {
                cell.setCellStyle(style);
            }
        }
    }

    private Object structured(Question q, String key) {
        return q.getStructuredContent() == null ? null : q.getStructuredContent().get(key);
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(v -> v == null ? "" : v.toString()).toList();
    }

    private String difficulty(Question q) {
        return q.getDifficulty() == null ? null : q.getDifficulty().name();
    }

    private String tags(Question q) {
        return q.getTags() == null || q.getTags().isEmpty() ? null : String.join(",", q.getTags());
    }

    private String plain(java.math.BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private boolean isPresent(String s) {
        return s != null && !s.isBlank();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String safeFilenamePart(String code) {
        return code == null ? "export" : code.replaceAll("[^A-Za-z0-9._-]", "_").toLowerCase(Locale.ROOT);
    }
}
