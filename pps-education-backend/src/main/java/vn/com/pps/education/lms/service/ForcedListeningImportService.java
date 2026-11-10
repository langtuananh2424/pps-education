package vn.com.pps.education.lms.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.lms.domain.Exercise;
import vn.com.pps.education.lms.dto.AddExerciseQuestionRequest;
import vn.com.pps.education.lms.dto.CreateExamQuestionRequest;
import vn.com.pps.education.lms.dto.ExerciseSectionRequest;
import vn.com.pps.education.lms.dto.ExerciseSectionResponse;
import vn.com.pps.education.lms.dto.ForcedListeningImportResponse;
import vn.com.pps.education.lms.dto.QuestionChoiceRequest;
import vn.com.pps.education.lms.dto.QuestionResponse;
import vn.com.pps.education.lms.repository.ExerciseQuestionRepository;
import vn.com.pps.education.lms.repository.ExerciseRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * V226 (Nghe ép, đã xác nhận với người dùng 2026-11-10) — import Excel RIÊNG cho Bài "Nghe ép" (không dùng
 * chung file mẫu import câu hỏi): 1 sheet, mỗi dòng 1 câu, cột "Bài tập con" (+ "Mức đạt %") để tự tạo bài tập
 * con và gắn câu vào đúng bài. Import vào Bài Nghe ép ĐÃ TẠO còn DRAFT. Dạng câu: TRAC_NGHIEM (1 đáp án),
 * NHIEU_DAP_AN (từ 2 đáp án, ngăn bằng "|"), DIEN_TU (đáp án chữ, nhiều phương án ngăn bằng "/"). All-or-nothing:
 * có lỗi ở bất kỳ dòng nào thì không tạo gì. Tạo câu/gắn câu đi qua đúng ExamQuestionService/ExerciseService
 * (cùng kiểm tổng điểm, DRAFT...) nên không có luồng song song.
 *
 * shortcut: chỉ đáp án A-F và 3 dạng trên, mức đạt chỉ áp cho bài tập con MỚI (bài trùng tên đã có thì giữ mức
 * cũ), nâng cấp khi giáo viên cần thêm dạng khác.
 */
@Service
public class ForcedListeningImportService {

    private static final Map<String, String> HEADERS = new HashMap<>();
    private static final List<String> LETTERS = List.of("A", "B", "C", "D", "E", "F");
    private static final List<String> KINDS = List.of("TRAC_NGHIEM", "NHIEU_DAP_AN", "DIEN_TU");

    static {
        HEADERS.put("bai tap con", "section");
        HEADERS.put("muc dat", "threshold");
        HEADERS.put("muc dat %", "threshold");
        HEADERS.put("loai cau hoi", "kind");
        HEADERS.put("noi dung", "content");
        HEADERS.put("dap an dung", "correct");
        HEADERS.put("url audio", "audio");
        HEADERS.put("tapescript", "tapescript");
        HEADERS.put("tape script", "tapescript");
        HEADERS.put("loi thoai", "tapescript");
        HEADERS.put("diem", "points");
        HEADERS.put("giai thich", "explanation");
        for (String l : LETTERS) {
            HEADERS.put("dap an " + l.toLowerCase(Locale.ROOT), "choice" + l);
        }
    }

    private record ParsedRow(int number, String section, BigDecimal threshold, String kind, String content, List<String> choices,
                             String correct, String audio, String tapescript, BigDecimal points, String explanation) {
    }

    private final ExerciseRepository exerciseRepository;
    private final ExerciseQuestionRepository exerciseQuestionRepository;
    private final ForcedListeningService forcedListeningService;
    private final ExamQuestionService examQuestionService;
    private final ExerciseService exerciseService;

    public ForcedListeningImportService(ExerciseRepository exerciseRepository, ExerciseQuestionRepository exerciseQuestionRepository,
                                        ForcedListeningService forcedListeningService, ExamQuestionService examQuestionService,
                                        ExerciseService exerciseService) {
        this.exerciseRepository = exerciseRepository;
        this.exerciseQuestionRepository = exerciseQuestionRepository;
        this.forcedListeningService = forcedListeningService;
        this.examQuestionService = examQuestionService;
        this.exerciseService = exerciseService;
    }

    @Transactional
    public ForcedListeningImportResponse importExcel(Long exerciseId, MultipartFile file, Long actorUserId) {
        Exercise exercise = exerciseRepository.findById(exerciseId)
                .orElseThrow(() -> new ResourceNotFoundException("error.exerciseAttempt.exerciseNotFound", new Object[]{exerciseId},
                        "Không tìm thấy đề id=" + exerciseId));
        // Kiểm Bài là Nghe ép (ném lỗi nếu không) rồi kiểm còn DRAFT.
        List<ExerciseSectionResponse> existingSections = forcedListeningService.listSections(exerciseId);
        if (exercise.getStatus() != Exercise.Status.DRAFT) {
            throw new IllegalArgumentException("Đề này đã Publish — chỉ import được khi còn Nháp (DRAFT).");
        }

        List<String> errors = new ArrayList<>();
        List<ParsedRow> rows = parse(file, errors);
        if (rows.isEmpty() && errors.isEmpty()) {
            errors.add("File không có dòng câu hỏi nào.");
        }
        if (!errors.isEmpty()) {
            return new ForcedListeningImportResponse(0, 0, errors);
        }

        Map<String, Long> sectionIdByTitle = new HashMap<>();
        for (ExerciseSectionResponse s : existingSections) {
            sectionIdByTitle.put(s.title().trim().toLowerCase(Locale.ROOT), s.id());
        }
        int createdSections = 0;
        int order = (int) exerciseQuestionRepository.countByExerciseId(exerciseId);
        Map<Integer, String> groupKeys = computeGroupKeys(exerciseId, rows);

        for (ParsedRow r : rows) {
            String titleKey = r.section().toLowerCase(Locale.ROOT);
            Long sectionId = sectionIdByTitle.get(titleKey);
            if (sectionId == null) {
                sectionId = forcedListeningService.createSection(exerciseId, new ExerciseSectionRequest(r.section(), null, r.threshold())).id();
                sectionIdByTitle.put(titleKey, sectionId);
                createdSections++;
            }
            QuestionResponse q = examQuestionService.createQuestion(exercise.getExam().getId(), toQuestionRequest(r, groupKeys.get(r.number())), actorUserId);
            exerciseService.addQuestion(exerciseId, new AddExerciseQuestionRequest(q.id(), ++order, r.points(), sectionId), actorUserId);
        }
        return new ForcedListeningImportResponse(createdSections, rows.size(), List.of());
    }

    /** Dòng LIÊN TIẾP cùng bài tập con + cùng URL Audio (từ 2 dòng) gộp 1 groupKey để học sinh thấy 1 audio dùng chung. */
    private static Map<Integer, String> computeGroupKeys(Long exerciseId, List<ParsedRow> rows) {
        Map<Integer, String> keys = new HashMap<>();
        int run = 0;
        int i = 0;
        while (i < rows.size()) {
            int j = i + 1;
            String audio = rows.get(i).audio();
            while (audio != null && j < rows.size() && audio.equals(rows.get(j).audio())
                    && rows.get(j).section().equalsIgnoreCase(rows.get(i).section())) {
                j++;
            }
            if (j - i >= 2) {
                run++;
                for (int k = i; k < j; k++) {
                    keys.put(rows.get(k).number(), "ne-" + exerciseId + "-" + run);
                }
            }
            i = j;
        }
        return keys;
    }

    private static CreateExamQuestionRequest toQuestionRequest(ParsedRow r, String groupKey) {
        boolean fillIn = r.kind().equals("DIEN_TU");
        List<QuestionChoiceRequest> choices = null;
        if (!fillIn) {
            List<String> correct = splitLetters(r.correct());
            choices = new ArrayList<>();
            int order = 0;
            for (int i = 0; i < r.choices().size(); i++) {
                if (r.choices().get(i) != null) {
                    choices.add(new QuestionChoiceRequest(LETTERS.get(i), r.choices().get(i), null, correct.contains(LETTERS.get(i)), ++order));
                }
            }
        }
        String type = fillIn ? "FILL_IN_BLANK" : r.kind().equals("NHIEU_DAP_AN") ? "MULTIPLE_ANSWER" : "MULTIPLE_CHOICE";
        return new CreateExamQuestionRequest(type, "LISTENING", "MEDIUM", r.content(), r.audio(), null, null, r.explanation(),
                fillIn ? r.correct() : null, r.points(), null, choices, null, groupKey, r.tapescript());
    }

    private static List<String> splitLetters(String correct) {
        return List.of(correct.toUpperCase(Locale.ROOT).split("[|,\\s]+"));
    }

    private List<ParsedRow> parse(MultipartFile file, List<String> errors) {
        List<ParsedRow> rows = new ArrayList<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            Map<String, Integer> columns = new HashMap<>();
            if (headerRow != null) {
                for (Cell cell : headerRow) {
                    String key = HEADERS.get(QuestionImportFieldAliases.normalize(formatter.formatCellValue(cell)));
                    if (key != null) {
                        columns.putIfAbsent(key, cell.getColumnIndex());
                    }
                }
            }
            Map<String, String> required = Map.of("section", "Bài tập con", "kind", "Loại câu hỏi", "content", "Nội dung", "correct", "Đáp án đúng");
            required.forEach((key, label) -> {
                if (!columns.containsKey(key)) {
                    errors.add("Thiếu cột bắt buộc: " + label);
                }
            });
            if (!errors.isEmpty()) {
                return rows;
            }
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                ParsedRow parsed = row == null ? null : parseRow(r + 1, row, columns, formatter, errors);
                if (parsed != null) {
                    rows.add(parsed);
                }
            }
        } catch (IOException | RuntimeException e) {
            errors.add("Không đọc được file Excel (.xlsx): " + e.getMessage());
        }
        return rows;
    }

    private ParsedRow parseRow(int number, Row row, Map<String, Integer> columns, DataFormatter formatter, List<String> errors) {
        String section = cell(row, columns, "section", formatter);
        String kind = cell(row, columns, "kind", formatter);
        String content = cell(row, columns, "content", formatter);
        if (section == null && kind == null && content == null) {
            return null; // dòng trống
        }
        int before = errors.size();
        String prefix = "Dòng " + number + ": ";
        if (section == null) errors.add(prefix + "thiếu Bài tập con.");
        if (content == null) errors.add(prefix + "thiếu Nội dung.");
        kind = kind == null ? "" : kind.toUpperCase(Locale.ROOT);
        if (!KINDS.contains(kind)) {
            errors.add(prefix + "Loại câu hỏi phải là TRAC_NGHIEM, NHIEU_DAP_AN hoặc DIEN_TU.");
        }
        String correct = cell(row, columns, "correct", formatter);
        if (correct == null) errors.add(prefix + "thiếu Đáp án đúng.");

        List<String> choices = new ArrayList<>();
        for (String l : LETTERS) {
            choices.add(cell(row, columns, "choice" + l, formatter));
        }
        if (correct != null && !kind.isEmpty() && !kind.equals("DIEN_TU")) {
            checkChoices(prefix, kind, correct, choices, errors);
        }
        String pointsText = cell(row, columns, "points", formatter);
        String thresholdText = cell(row, columns, "threshold", formatter);
        BigDecimal points = decimal(pointsText, BigDecimal.ONE, BigDecimal.valueOf(1000));
        BigDecimal threshold = decimal(thresholdText, null, BigDecimal.valueOf(100));
        if (points == null) errors.add(prefix + "Điểm phải là số lớn hơn 0.");
        if (thresholdText != null && threshold == null) errors.add(prefix + "Mức đạt phải là số lớn hơn 0 và không quá 100.");
        if (errors.size() > before) {
            return null;
        }
        return new ParsedRow(number, section, threshold, kind, content, choices, kind.equals("DIEN_TU") ? correct : correct.toUpperCase(Locale.ROOT),
                cell(row, columns, "audio", formatter), cell(row, columns, "tapescript", formatter), points, cell(row, columns, "explanation", formatter));
    }

    private static void checkChoices(String prefix, String kind, String correct, List<String> choices, List<String> errors) {
        if (choices.stream().filter(c -> c != null).count() < 2) {
            errors.add(prefix + "cần ít nhất 2 đáp án (A, B...).");
        }
        List<String> letters = splitLetters(correct);
        boolean valid = letters.stream().allMatch(l -> LETTERS.contains(l) && choices.get(LETTERS.indexOf(l)) != null);
        if (!valid) {
            errors.add(prefix + "Đáp án đúng phải là chữ cái của đáp án đã điền (vd B hoặc B|D).");
        } else if (kind.equals("TRAC_NGHIEM") && letters.size() != 1) {
            errors.add(prefix + "TRAC_NGHIEM chỉ có 1 đáp án đúng.");
        } else if (kind.equals("NHIEU_DAP_AN") && letters.size() < 2) {
            errors.add(prefix + "NHIEU_DAP_AN cần từ 2 đáp án đúng (vd B|D).");
        }
    }

    /** Ô trống thì dùng {@code ifBlank}; không phải số, ≤ 0 hoặc vượt {@code max} thì null. */
    private static BigDecimal decimal(String text, BigDecimal ifBlank, BigDecimal max) {
        if (text == null) {
            return ifBlank;
        }
        try {
            BigDecimal v = new BigDecimal(text.replace(',', '.'));
            return v.signum() > 0 && v.compareTo(max) <= 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String cell(Row row, Map<String, Integer> columns, String key, DataFormatter formatter) {
        Integer index = columns.get(key);
        if (index == null) {
            return null;
        }
        String value = formatter.formatCellValue(row.getCell(index)).trim();
        return value.isEmpty() ? null : value;
    }
}
