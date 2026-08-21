package vn.com.pps.education.service;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.domain.ImportJob;
import vn.com.pps.education.domain.ReviewVideo;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AddReviewVideoConnectionQuestionRequest;
import vn.com.pps.education.dto.AddReviewVideoQuestionRequest;
import vn.com.pps.education.dto.ConnectionChoiceRequest;
import vn.com.pps.education.dto.ReviewVideoConnectionQuestionResponse;
import vn.com.pps.education.dto.ReviewVideoQuestionImportResponse;
import vn.com.pps.education.dto.ReviewVideoQuestionResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ImportJobRepository;
import vn.com.pps.education.repository.ReviewVideoRepository;
import vn.com.pps.education.repository.UserRepository;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * UC-23 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng) — soạn hàng
 * loạt câu hỏi Kho Video Ôn tập bằng file mẫu Excel (.xlsx), mirror
 * {@link QuestionImportService} bên Kho đề (UC-40) nhưng KHÔNG cần interface
 * đa định dạng như {@link QuestionRowParser} vì chỉ hỗ trợ đúng 1 định dạng
 * (đã xác nhận với người dùng — không cần .docx) — đọc file trực tiếp bằng
 * Apache POI trong chính Service này.
 *
 * Việc GHI 1 câu hỏi vào DB (validate loại video, chồng lấn khoảng ghi âm,
 * quyền sở hữu bộ video) ủy quyền hoàn toàn cho
 * {@link ReviewVideoService#addQuestion}/{@link ReviewVideoService#addConnectionQuestion}
 * — điểm ghi DUY NHẤT, dùng chung với form soạn tay ở FE (không lặp lại
 * logic, xem .claude/rules/solid.md mục D).
 */
@Service
public class ReviewVideoQuestionImportService {

    private static final int HEADER_ROW_INDEX = 0;
    private static final int FIRST_DATA_ROW_INDEX = 1;
    private static final List<String> CONNECTION_CHOICE_LETTERS = List.of("A", "B", "C", "D", "E");

    private final ImportJobRepository importJobRepository;
    private final ReviewVideoRepository reviewVideoRepository;
    private final UserRepository userRepository;
    private final ReviewVideoService reviewVideoService;

    public ReviewVideoQuestionImportService(ImportJobRepository importJobRepository,
                                             ReviewVideoRepository reviewVideoRepository,
                                             UserRepository userRepository,
                                             ReviewVideoService reviewVideoService) {
        this.importJobRepository = importJobRepository;
        this.reviewVideoRepository = reviewVideoRepository;
        this.userRepository = userRepository;
        this.reviewVideoService = reviewVideoService;
    }

    /** Import câu hỏi ghi âm theo mốc thời gian cho 1 video REFLEX. A2: lỗi từng dòng không chặn dòng khác. A3: file hỏng → job FAILED ngay. */
    @Transactional
    public ReviewVideoQuestionImportResponse importReflexQuestions(Long videoId, MultipartFile file, Long actorUserId) {
        ReviewVideo video = reviewVideoRepository.findById(videoId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reviewVideoQuestionImport.videoNotFound",
                        new Object[]{videoId}, "Không tìm thấy video id=" + videoId));
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reviewVideoQuestionImport.userNotFound",
                        new Object[]{actorUserId}, "Không tìm thấy tài khoản id=" + actorUserId));
        requireXlsx(file);

        ImportJob job = createJob(file, actor, ImportJob.ImportType.REVIEW_VIDEO_QUESTIONS);
        try {
            List<ReflexRow> rows;
            try (InputStream inputStream = file.getInputStream()) {
                rows = parseReflexRows(inputStream);
            }

            List<Map<String, Object>> errors = new ArrayList<>();
            List<Map<String, Object>> created = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                ReflexRow row = rows.get(i);
                try {
                    AddReviewVideoQuestionRequest request = toReflexRequest(row, i);
                    ReviewVideoQuestionResponse response = reviewVideoService.addQuestion(video.getId(), request, actorUserId);
                    created.add(summary(response.id(), reflexSummary(response)));
                } catch (RuntimeException ex) {
                    errors.add(rowError(row.rowNumber(), ex.getMessage()));
                }
            }
            return finishJob(job, rows.size(), created, errors);
        } catch (IOException | RuntimeException ex) {
            return failJob(job, "File sai định dạng hoặc không đọc được: " + ex.getMessage());
        }
    }

    /** Import câu hỏi trắc nghiệm tự chấm cho 1 video CONNECTION. A2: lỗi từng dòng không chặn dòng khác. A3: file hỏng → job FAILED ngay. */
    @Transactional
    public ReviewVideoQuestionImportResponse importConnectionQuestions(Long videoId, MultipartFile file, Long actorUserId) {
        ReviewVideo video = reviewVideoRepository.findById(videoId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reviewVideoQuestionImport.videoNotFound",
                        new Object[]{videoId}, "Không tìm thấy video id=" + videoId));
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reviewVideoQuestionImport.userNotFound",
                        new Object[]{actorUserId}, "Không tìm thấy tài khoản id=" + actorUserId));
        requireXlsx(file);

        ImportJob job = createJob(file, actor, ImportJob.ImportType.REVIEW_VIDEO_CONNECTION_QUESTIONS);
        try {
            List<ConnectionRow> rows;
            try (InputStream inputStream = file.getInputStream()) {
                rows = parseConnectionRows(inputStream);
            }

            List<Map<String, Object>> errors = new ArrayList<>();
            List<Map<String, Object>> created = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                ConnectionRow row = rows.get(i);
                try {
                    AddReviewVideoConnectionQuestionRequest request = toConnectionRequest(row, i);
                    ReviewVideoConnectionQuestionResponse response = reviewVideoService.addConnectionQuestion(video.getId(), request, actorUserId);
                    created.add(summary(response.id(), response.prompt()));
                } catch (RuntimeException ex) {
                    errors.add(rowError(row.rowNumber(), ex.getMessage()));
                }
            }
            return finishJob(job, rows.size(), created, errors);
        } catch (IOException | RuntimeException ex) {
            return failJob(job, "File sai định dạng hoặc không đọc được: " + ex.getMessage());
        }
    }

    // ===================== Map dòng thô -> request =====================

    private AddReviewVideoQuestionRequest toReflexRequest(ReflexRow row, int index) {
        if (isBlank(row.timestampSeconds())) {
            throw new IllegalArgumentException("Thiếu \"Mốc thời gian (giây)\".");
        }
        if (isBlank(row.maxRecordingSeconds())) {
            throw new IllegalArgumentException("Thiếu \"Thời lượng ghi âm tối đa (giây)\".");
        }
        int timestampSeconds = parsePositiveInt(row.timestampSeconds(), "Mốc thời gian (giây)", true);
        int maxRecordingSeconds = parsePositiveInt(row.maxRecordingSeconds(), "Thời lượng ghi âm tối đa (giây)", false);
        Integer maxAttempts = isBlank(row.maxAttempts()) ? null : parsePositiveInt(row.maxAttempts(), "Số lượt nộp tối đa", false);
        Integer displayOrder = isBlank(row.displayOrder()) ? index : parsePositiveInt(row.displayOrder(), "Thứ tự hiển thị", true);
        return new AddReviewVideoQuestionRequest(timestampSeconds, blankToNull(row.prompt()), maxRecordingSeconds, maxAttempts, displayOrder);
    }

    private AddReviewVideoConnectionQuestionRequest toConnectionRequest(ConnectionRow row, int index) {
        if (isBlank(row.prompt())) {
            throw new IllegalArgumentException("Thiếu \"Nội dung câu hỏi\".");
        }
        List<String> contents = row.choices();
        List<Integer> filledIndexes = new ArrayList<>();
        for (int i = 0; i < contents.size(); i++) {
            if (!isBlank(contents.get(i))) {
                filledIndexes.add(i);
            }
        }
        if (filledIndexes.size() < 2) {
            throw new IllegalArgumentException("Câu trắc nghiệm cần ít nhất 2 đáp án (Đáp án A/B/C/D/E).");
        }
        String correctLetter = isBlank(row.correctAnswer()) ? "" : row.correctAnswer().trim().toUpperCase(Locale.ROOT);
        int correctIndex = CONNECTION_CHOICE_LETTERS.indexOf(correctLetter);
        if (correctIndex < 0 || !filledIndexes.contains(correctIndex)) {
            throw new IllegalArgumentException(
                    "\"Đáp án đúng\" phải là 1 chữ cái khớp với 1 đáp án có nội dung (đang có: '" + row.correctAnswer() + "').");
        }

        List<ConnectionChoiceRequest> choices = new ArrayList<>();
        int order = 1;
        for (int i : filledIndexes) {
            String label = CONNECTION_CHOICE_LETTERS.get(i);
            choices.add(new ConnectionChoiceRequest(label, contents.get(i).trim(), i == correctIndex, order++));
        }
        Integer displayOrder = isBlank(row.displayOrder()) ? index : parsePositiveInt(row.displayOrder(), "Thứ tự hiển thị", true);
        return new AddReviewVideoConnectionQuestionRequest(row.prompt().trim(), displayOrder, choices);
    }

    private int parsePositiveInt(String raw, String fieldLabel, boolean allowZero) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 0 || (!allowZero && value == 0)) {
                throw new NumberFormatException("out of range");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("\"" + fieldLabel + "\" không hợp lệ: '" + raw + "'.");
        }
    }

    private String reflexSummary(ReviewVideoQuestionResponse response) {
        int m = response.timestampSeconds() / 60;
        int s = response.timestampSeconds() % 60;
        return "Mốc " + m + ":" + String.format("%02d", s) + (response.prompt() != null ? " — " + response.prompt() : "");
    }

    // ===================== Đọc file Excel =====================

    private void requireXlsx(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("Chỉ hỗ trợ file .xlsx.");
        }
    }

    private record ReflexRow(int rowNumber, String timestampSeconds, String prompt, String maxRecordingSeconds, String maxAttempts, String displayOrder) {}

    private record ConnectionRow(int rowNumber, String prompt, List<String> choices, String correctAnswer, String displayOrder) {}

    private List<ReflexRow> parseReflexRows(InputStream inputStream) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            Row headerRow = sheet.getRow(HEADER_ROW_INDEX);
            if (headerRow == null) {
                throw new IllegalArgumentException("File rỗng hoặc thiếu dòng tiêu đề (header).");
            }
            Map<String, Integer> columns = resolveColumns(headerRow, formatter, Map.of(
                    "mốc thời gian (giây)", "timestampSeconds",
                    "nội dung câu hỏi", "prompt",
                    "thời lượng ghi âm tối đa (giây)", "maxRecordingSeconds",
                    "số lượt nộp tối đa", "maxAttempts",
                    "thứ tự hiển thị", "displayOrder"
            ));
            if (!columns.containsKey("timestampSeconds") || !columns.containsKey("maxRecordingSeconds")) {
                throw new IllegalArgumentException(
                        "Thiếu cột bắt buộc: \"Mốc thời gian (giây)\" hoặc \"Thời lượng ghi âm tối đa (giây)\".");
            }

            List<ReflexRow> rows = new ArrayList<>();
            for (int r = FIRST_DATA_ROW_INDEX; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row, formatter, columns)) {
                    continue;
                }
                rows.add(new ReflexRow(
                        r + 1,
                        cell(row, formatter, columns, "timestampSeconds"),
                        cell(row, formatter, columns, "prompt"),
                        cell(row, formatter, columns, "maxRecordingSeconds"),
                        cell(row, formatter, columns, "maxAttempts"),
                        cell(row, formatter, columns, "displayOrder")
                ));
            }
            return rows;
        } catch (IOException ex) {
            throw new IllegalArgumentException("File sai định dạng Excel (.xlsx): " + ex.getMessage());
        }
    }

    private List<ConnectionRow> parseConnectionRows(InputStream inputStream) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            Row headerRow = sheet.getRow(HEADER_ROW_INDEX);
            if (headerRow == null) {
                throw new IllegalArgumentException("File rỗng hoặc thiếu dòng tiêu đề (header).");
            }
            requireNoExtraChoiceColumn(headerRow, formatter);
            Map<String, Integer> columns = resolveColumns(headerRow, formatter, Map.of(
                    "nội dung câu hỏi", "prompt",
                    "đáp án a", "choiceA",
                    "đáp án b", "choiceB",
                    "đáp án c", "choiceC",
                    "đáp án d", "choiceD",
                    "đáp án e", "choiceE",
                    "đáp án đúng", "correctAnswer",
                    "thứ tự hiển thị", "displayOrder"
            ));
            if (!columns.containsKey("prompt") || !columns.containsKey("correctAnswer")) {
                throw new IllegalArgumentException("Thiếu cột bắt buộc: \"Nội dung câu hỏi\" hoặc \"Đáp án đúng\".");
            }

            List<ConnectionRow> rows = new ArrayList<>();
            for (int r = FIRST_DATA_ROW_INDEX; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row, formatter, columns)) {
                    continue;
                }
                List<String> choices = List.of(
                        cell(row, formatter, columns, "choiceA") == null ? "" : cell(row, formatter, columns, "choiceA"),
                        cell(row, formatter, columns, "choiceB") == null ? "" : cell(row, formatter, columns, "choiceB"),
                        cell(row, formatter, columns, "choiceC") == null ? "" : cell(row, formatter, columns, "choiceC"),
                        cell(row, formatter, columns, "choiceD") == null ? "" : cell(row, formatter, columns, "choiceD"),
                        cell(row, formatter, columns, "choiceE") == null ? "" : cell(row, formatter, columns, "choiceE")
                );
                rows.add(new ConnectionRow(
                        r + 1,
                        cell(row, formatter, columns, "prompt"),
                        choices,
                        cell(row, formatter, columns, "correctAnswer"),
                        cell(row, formatter, columns, "displayOrder")
                ));
            }
            return rows;
        } catch (IOException ex) {
            throw new IllegalArgumentException("File sai định dạng Excel (.xlsx): " + ex.getMessage());
        }
    }

    /**
     * Chỉ hỗ trợ tối đa 5 đáp án (A-E) — khớp giới hạn có sẵn của form nhập tay
     * (ConnectionQuizBuilder ở FE, nút "+ Thêm lựa chọn" tự khoá ở 5). Cột
     * "Đáp án F" trở lên KHÔNG bị âm thầm bỏ qua như header lạ khác — báo lỗi
     * rõ ngay từ đầu file để giáo viên biết cần xoá bớt đáp án, tránh mất dữ
     * liệu không báo (đã xác nhận với người dùng).
     */
    private void requireNoExtraChoiceColumn(Row headerRow, DataFormatter formatter) {
        for (int col = 0; col < headerRow.getLastCellNum(); col++) {
            var c = headerRow.getCell(col);
            if (c == null) {
                continue;
            }
            String header = formatter.formatCellValue(c).trim();
            String normalized = header.toLowerCase(Locale.ROOT);
            if (normalized.matches("đáp án [a-z]") && !CONNECTION_CHOICE_LETTERS.contains(normalized.substring(7).toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException(
                        "Cột \"" + header + "\" không hợp lệ — chỉ hỗ trợ tối đa 5 đáp án (Đáp án A đến Đáp án E).");
            }
        }
    }

    /** Đọc header (dòng 1), khớp KHÔNG phân biệt hoa/thường/khoảng trắng thừa với {@code headerToField} — bỏ qua header lạ không nhận diện được. */
    private Map<String, Integer> resolveColumns(Row headerRow, DataFormatter formatter, Map<String, String> headerToField) {
        Map<String, Integer> fieldToColumn = new HashMap<>();
        for (int col = 0; col < headerRow.getLastCellNum(); col++) {
            var c = headerRow.getCell(col);
            if (c == null) {
                continue;
            }
            String header = formatter.formatCellValue(c).trim().toLowerCase(Locale.ROOT);
            String field = headerToField.get(header);
            if (field != null) {
                fieldToColumn.putIfAbsent(field, col);
            }
        }
        return fieldToColumn;
    }

    private String cell(Row row, DataFormatter formatter, Map<String, Integer> columns, String field) {
        Integer col = columns.get(field);
        if (col == null) {
            return null;
        }
        var c = row.getCell(col);
        if (c == null) {
            return null;
        }
        String value = formatter.formatCellValue(c).trim();
        return value.isEmpty() ? null : value;
    }

    private boolean isBlankRow(Row row, DataFormatter formatter, Map<String, Integer> columns) {
        for (Integer col : columns.values()) {
            var c = row.getCell(col);
            if (c != null && !formatter.formatCellValue(c).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ===================== ImportJob helpers =====================

    private ImportJob createJob(MultipartFile file, User actor, ImportJob.ImportType type) {
        ImportJob job = new ImportJob();
        job.setImportType(type);
        job.setSourceFileName(file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename());
        job.setSourceFileUrl("in-memory://" + job.getSourceFileName());
        job.setUploadedBy(actor);
        job.setStatus(ImportJob.Status.PROCESSING);
        job.setStartedAt(OffsetDateTime.now());
        return importJobRepository.save(job);
    }

    private ReviewVideoQuestionImportResponse finishJob(ImportJob job, int totalRows, List<Map<String, Object>> created, List<Map<String, Object>> errors) {
        job.setTotalRows(totalRows);
        job.setSuccessRows(created.size());
        job.setFailedRows(errors.size());
        job.setErrorSummary(errors);
        Map<String, Object> resultDetails = new LinkedHashMap<>();
        resultDetails.put("createdQuestions", created);
        job.setResultDetails(resultDetails);
        job.setStatus(errors.isEmpty() ? ImportJob.Status.COMPLETED : ImportJob.Status.PARTIAL_SUCCESS);
        job.setFinishedAt(OffsetDateTime.now());
        job = importJobRepository.save(job);
        return toResponse(job);
    }

    private ReviewVideoQuestionImportResponse failJob(ImportJob job, String reason) {
        job.setStatus(ImportJob.Status.FAILED);
        job.setErrorSummary(List.of(rowError(0, reason)));
        job.setFinishedAt(OffsetDateTime.now());
        job = importJobRepository.save(job);
        return toResponse(job);
    }

    private Map<String, Object> rowError(int rowNumber, String reason) {
        Map<String, Object> error = new HashMap<>();
        error.put("row", rowNumber);
        error.put("reason", reason);
        return error;
    }

    private Map<String, Object> summary(Long id, String text) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("summary", text);
        return map;
    }

    @SuppressWarnings("unchecked")
    private ReviewVideoQuestionImportResponse toResponse(ImportJob job) {
        List<ReviewVideoQuestionImportResponse.ImportedQuestion> createdQuestions = new ArrayList<>();
        if (job.getResultDetails() != null && job.getResultDetails().get("createdQuestions") instanceof List<?> raw) {
            for (Object item : raw) {
                Map<String, Object> map = (Map<String, Object>) item;
                Long id = ((Number) map.get("id")).longValue();
                String summaryText = (String) map.get("summary");
                createdQuestions.add(new ReviewVideoQuestionImportResponse.ImportedQuestion(id, summaryText));
            }
        }
        return new ReviewVideoQuestionImportResponse(job.getId(), job.getSourceFileName(), job.getTotalRows(), job.getSuccessRows(),
                job.getFailedRows(), job.getStatus().name(), job.getErrorSummary(), createdQuestions);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
