package vn.com.pps.education.service;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.domain.Book;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.CurriculumSubTopic;
import vn.com.pps.education.domain.CurriculumUnit;
import vn.com.pps.education.domain.Exam;
import vn.com.pps.education.domain.ImportJob;
import vn.com.pps.education.dto.BookCatalogImportResponse;
import vn.com.pps.education.dto.CreateBookRequest;
import vn.com.pps.education.dto.CreateExamRequest;
import vn.com.pps.education.dto.CreateExerciseRequest;
import vn.com.pps.education.dto.CreateSubTopicRequest;
import vn.com.pps.education.dto.CreateUnitRequest;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.BookRepository;
import vn.com.pps.education.repository.CurriculumRepository;
import vn.com.pps.education.repository.CurriculumSubTopicRepository;
import vn.com.pps.education.repository.CurriculumUnitRepository;
import vn.com.pps.education.repository.ExamRepository;
import vn.com.pps.education.repository.ExerciseRepository;
import vn.com.pps.education.repository.ImportJobRepository;
import vn.com.pps.education.repository.UserRepository;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * UC-72: Import Excel nhanh mục lục Sách/Unit/Sub Topic/Lesson (=
 * {@link Exam})/Bài (= {@link vn.com.pps.education.domain.Exercise}) của
 * Kho đề (FR-LMS-10) — bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-09-13. Xem docs/uc/phan-he-07-lms-portal.md (UC-72) +
 * docs/sdd-groups/09-lms-and-portal.md (mục k-bis).
 *
 * Việc GHI Sách/Unit/Sub Topic/Đề/Bài ủy quyền hoàn toàn cho
 * {@link CurriculumService#addBook}/{@code addUnit}/{@code addSubTopic},
 * {@link ExamService#createExam}, {@link ExerciseService#createExercise}
 * (điểm ghi DUY NHẤT, dùng chung với thao tác tạo tay ở màn Kho đề —
 * không lặp lại logic tạo entity ở đây, xem .claude/rules/solid.md mục D).
 * Service này CHỈ lo cơ chế import (đọc file, forward-fill, tra cứu
 * idempotent theo khóa tự nhiên, gộp lỗi từng dòng).
 *
 * Định dạng file (7 cột theo thứ tự, dòng 1 = tiêu đề, dữ liệu từ dòng 2):
 * A=Tên sách, B=Tên Unit, C=Tên Sub Topic, D=Mã Lesson, E=Loại giáo viên
 * (VIETNAMESE/FOREIGN, TÙY CHỌN), F=Mã exercise, G=Tên exercise. Mỗi dòng
 * = 1 Bài; 4 cột A-D để trống nghĩa là LẶP LẠI giá trị dòng liền trước
 * (merged cell khi xuất từ Excel) — xem forward-fill trong
 * importCatalog(). F, G luôn bắt buộc mỗi dòng.
 *
 * Cột E (bổ sung 2026-09-13, đã xác nhận với người dùng — thực tế 1 sách
 * thường xen kẽ Lesson lẻ do Giáo viên Việt Nam dạy/Lesson chẵn do Giáo
 * viên nước ngoài dạy, KHÁC hẳn nhau trong CÙNG 1 file, không thể áp 1
 * giá trị chung cho cả file như thiết kế ban đầu): forward-fill THEO
 * LESSON giống cột D — để trống ở các dòng Bài tiếp theo trong CÙNG 1
 * Lesson nghĩa là dùng lại giá trị đã khai ở dòng đầu Lesson đó. Để trống
 * HOÀN TOÀN cả cột (mọi dòng) thì dùng {@code teacherType} (tham số mặc
 * định của cả lần import, xem importCatalog()) cho MỌI Lesson — giữ
 * tương thích ngược với file chỉ có 6 cột cũ.
 *
 * Idempotent: Sách/Unit/Sub Topic tra theo (cha, title) trong phạm vi
 * curriculum đã chọn; Đề theo exams.code = Mã Lesson nguyên văn; Bài theo
 * exercises.code = Mã exercise nguyên văn — Đề/Bài đã tồn tại được TÁI SỬ
 * DỤNG nguyên vẹn, KHÔNG ghi đè teacherType/examType/exerciseType/
 * totalPoints đã có (tránh phá câu hỏi/dữ liệu đã soạn nếu import lại
 * cùng file).
 */
@Service
public class BookCatalogImportService {

    private static final int HEADER_ROW_INDEX = 0;
    private static final int FIRST_DATA_ROW_INDEX = 1;
    private static final int COLUMN_COUNT = 7;

    private final ImportJobRepository importJobRepository;
    private final UserRepository userRepository;
    private final CurriculumRepository curriculumRepository;
    private final BookRepository bookRepository;
    private final CurriculumUnitRepository curriculumUnitRepository;
    private final CurriculumSubTopicRepository curriculumSubTopicRepository;
    private final ExamRepository examRepository;
    private final ExerciseRepository exerciseRepository;
    private final CurriculumService curriculumService;
    private final ExamService examService;
    private final ExerciseService exerciseService;

    public BookCatalogImportService(ImportJobRepository importJobRepository,
                                     UserRepository userRepository,
                                     CurriculumRepository curriculumRepository,
                                     BookRepository bookRepository,
                                     CurriculumUnitRepository curriculumUnitRepository,
                                     CurriculumSubTopicRepository curriculumSubTopicRepository,
                                     ExamRepository examRepository,
                                     ExerciseRepository exerciseRepository,
                                     CurriculumService curriculumService,
                                     ExamService examService,
                                     ExerciseService exerciseService) {
        this.importJobRepository = importJobRepository;
        this.userRepository = userRepository;
        this.curriculumRepository = curriculumRepository;
        this.bookRepository = bookRepository;
        this.curriculumUnitRepository = curriculumUnitRepository;
        this.curriculumSubTopicRepository = curriculumSubTopicRepository;
        this.examRepository = examRepository;
        this.exerciseRepository = exerciseRepository;
        this.curriculumService = curriculumService;
        this.examService = examService;
        this.exerciseService = exerciseService;
    }

    /**
     * Main Flow bước 1-4. A1/A2: lỗi từng dòng, không chặn dòng khác. A3:
     * file hỏng hoàn toàn -> FAILED ngay. {@code teacherType} chỉ là giá
     * trị MẶC ĐỊNH khi cột E (Loại giáo viên) trong file để trống hoàn
     * toàn — cột E trong file mới là nguồn chính, cho phép xen kẽ
     * VIETNAMESE/FOREIGN theo từng Lesson (xem Javadoc lớp). {@code examType}
     * áp dụng cho MỌI Đề mới tạo; {@code exerciseType}/{@code totalPoints}
     * áp dụng cho MỌI Bài mới tạo — Đề/Bài đã tồn tại theo đúng code giữ
     * nguyên giá trị cũ (xem Javadoc lớp).
     */
    @Transactional
    public BookCatalogImportResponse importCatalog(Long curriculumId, MultipartFile file, String teacherType,
                                                     String examType, String exerciseType, BigDecimal totalPoints,
                                                     Long actorUserId) {
        Curriculum curriculum = curriculumRepository.findById(curriculumId)
                .orElseThrow(() -> new ResourceNotFoundException("error.bookCatalogImport.curriculumNotFound",
                        new Object[]{curriculumId}, "Không tìm thấy khung chương trình id=" + curriculumId));
        if (!userRepository.existsById(actorUserId)) {
            throw new ResourceNotFoundException("error.bookCatalogImport.userNotFound",
                    new Object[]{actorUserId}, "Không tìm thấy user id=" + actorUserId);
        }
        // Validate sớm 3 giá trị enum mặc định — lỗi cấu hình chung cả file, không phải lỗi 1 dòng.
        Exam.TeacherType.valueOf(teacherType);
        Exam.ExamType.valueOf(examType);
        vn.com.pps.education.domain.Exercise.ExerciseType.valueOf(exerciseType);

        ImportJob job = new ImportJob();
        job.setImportType(ImportJob.ImportType.CURRICULUM_CATALOG);
        job.setSourceFileName(file.getOriginalFilename() == null ? "unnamed.xlsx" : file.getOriginalFilename());
        job.setSourceFileUrl("in-memory://" + job.getSourceFileName());
        job.setUploadedBy(userRepository.getReferenceById(actorUserId));
        job.setStatus(ImportJob.Status.PROCESSING);
        job.setStartedAt(OffsetDateTime.now());

        List<Map<String, Object>> errors = new ArrayList<>();
        try (InputStream inputStream = file.getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getRow(HEADER_ROW_INDEX) == null) {
                return failJob(job, "File rỗng hoặc thiếu dòng tiêu đề.");
            }

            // Cache trong phạm vi 1 lần import — tránh query lặp lại DB cho các dòng cùng 1 Sách/Unit/
            // Sub Topic/Lesson (thường rất nhiều dòng liên tiếp cùng 1 Lesson).
            Map<String, Book> bookCache = new HashMap<>();
            Map<String, CurriculumUnit> unitCache = new HashMap<>();
            Map<String, CurriculumSubTopic> subTopicCache = new HashMap<>();
            Map<String, Exam> examCache = new HashMap<>();

            // Forward-fill: giá trị NGUYÊN VĂN gần nhất của từng cột phân cấp (mirror merged cell Excel).
            String lastBookName = null;
            String lastUnitName = null;
            String lastSubTopicName = null;
            String lastLessonCode = null;
            // Khác 4 cột trên (bắt buộc, không có gì để "mặc định") — cột Loại giáo viên là TÙY CHỌN,
            // khởi tạo sẵn = teacherType (tham số mặc định cả lần import) nên file không có cột này
            // (hoặc để trống mọi dòng) vẫn chạy đúng như thiết kế cũ (tương thích ngược).
            String lastTeacherType = teacherType;

            DataFormatter formatter = new DataFormatter();
            int totalRows = 0;
            int successRows = 0;
            for (int rowIndex = FIRST_DATA_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row, formatter)) {
                    continue;
                }
                totalRows++;
                int rowNumber = rowIndex + 1;

                String rawBookName = cell(row, formatter, 0);
                String rawUnitName = cell(row, formatter, 1);
                String rawSubTopicName = cell(row, formatter, 2);
                String rawLessonCode = cell(row, formatter, 3);
                String rawTeacherType = cell(row, formatter, 4);
                String exerciseCode = cell(row, formatter, 5);
                String exerciseName = cell(row, formatter, 6);

                // Forward-fill cập nhật NGAY khi dòng có giá trị mới, bất kể dòng này có lỗi ở cột khác
                // hay không — merged cell là sự thật vật lý của file nguồn, không phụ thuộc dòng có
                // valid hay không.
                if (!isBlank(rawBookName)) {
                    lastBookName = rawBookName.trim();
                }
                if (!isBlank(rawUnitName)) {
                    lastUnitName = rawUnitName.trim();
                }
                if (!isBlank(rawSubTopicName)) {
                    lastSubTopicName = rawSubTopicName.trim();
                }
                if (!isBlank(rawLessonCode)) {
                    lastLessonCode = rawLessonCode.trim();
                    // Sang Lesson MỚI (cột D vừa có giá trị, không phải dòng lặp lại) -> reset về mặc
                    // định của cả lần import TRƯỚC KHI áp giá trị riêng của chính dòng này (nếu có) —
                    // nếu không reset, 1 Lesson để trống hoàn toàn cột E sẽ bị "dính" nhầm teacherType
                    // của Lesson liền trước đó (VD L2=FOREIGN rồi L3 để trống phải là mặc định
                    // VIETNAMESE, không phải kế thừa FOREIGN của L2) — bug thật phát hiện khi viết test
                    // importCatalog_boSung_appliesDifferentTeacherTypePerLessonFromColumnE.
                    lastTeacherType = teacherType;
                }
                // Chỉ forward-fill khi giá trị HỢP LỆ — token sai (VD gõ nhầm "VN") bị bắt lỗi riêng
                // cho ĐÚNG dòng đó bên dưới (trong try), không được phép "làm hỏng" giá trị kế thừa
                // cho các dòng Bài tiếp theo cùng Lesson.
                if (!isBlank(rawTeacherType) && isValidTeacherType(rawTeacherType)) {
                    lastTeacherType = rawTeacherType.trim().toUpperCase(Locale.ROOT);
                }

                try {
                    // A2: chưa có gì để forward-fill.
                    if (isBlank(lastBookName)) {
                        throw new IllegalArgumentException("Thiếu Tên sách và chưa có dòng trước đó để dùng lại.");
                    }
                    if (isBlank(lastUnitName)) {
                        throw new IllegalArgumentException("Thiếu Tên Unit và chưa có dòng trước đó để dùng lại.");
                    }
                    if (isBlank(lastSubTopicName)) {
                        throw new IllegalArgumentException("Thiếu Tên Sub Topic và chưa có dòng trước đó để dùng lại.");
                    }
                    if (isBlank(lastLessonCode)) {
                        throw new IllegalArgumentException("Thiếu Mã Lesson và chưa có dòng trước đó để dùng lại.");
                    }
                    if (!isBlank(rawTeacherType) && !isValidTeacherType(rawTeacherType)) {
                        throw new IllegalArgumentException("Loại giáo viên không hợp lệ: '" + rawTeacherType
                                + "' — chỉ chấp nhận VIETNAMESE/FOREIGN, hoặc để trống để dùng lại giá trị của Lesson này/giá trị mặc định.");
                    }
                    // A1: Mã exercise/Tên exercise luôn bắt buộc mỗi dòng, không forward-fill.
                    if (isBlank(exerciseCode)) {
                        throw new IllegalArgumentException("Thiếu Mã exercise.");
                    }
                    if (isBlank(exerciseName)) {
                        throw new IllegalArgumentException("Thiếu Tên exercise.");
                    }

                    Book book = resolveBook(bookCache, curriculum, lastBookName);
                    CurriculumUnit unit = resolveUnit(unitCache, book, lastUnitName);
                    CurriculumSubTopic subTopic = resolveSubTopic(subTopicCache, unit, lastSubTopicName);
                    Exam exam = resolveExam(examCache, curriculum, subTopic, lastLessonCode, lastTeacherType, examType, actorUserId);
                    resolveExercise(exam, exerciseCode.trim(), exerciseName.trim(), exerciseType, totalPoints, actorUserId);

                    successRows++;
                } catch (RuntimeException ex) {
                    errors.add(rowError(rowNumber, ex.getMessage()));
                }
            }

            job.setTotalRows(totalRows);
            job.setSuccessRows(successRows);
            job.setFailedRows(errors.size());
            job.setErrorSummary(errors);
            job.setStatus(errors.isEmpty() ? ImportJob.Status.COMPLETED : ImportJob.Status.PARTIAL_SUCCESS);
            job.setFinishedAt(OffsetDateTime.now());
            job = importJobRepository.save(job);
            return toResponse(job);
        } catch (IOException | RuntimeException ex) {
            // A3 — file sai định dạng/hỏng hoàn toàn.
            return failJob(job, "File sai định dạng Excel (.xlsx) hoặc không đọc được: " + ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public BookCatalogImportResponse getJob(Long id) {
        ImportJob job = importJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.bookCatalogImport.jobNotFound",
                        new Object[]{id}, "Không tìm thấy import job id=" + id));
        return toResponse(job);
    }

    // ===================== Tra cứu idempotent + tạo mới (ủy quyền cho Service gốc) =====================

    private Book resolveBook(Map<String, Book> cache, Curriculum curriculum, String title) {
        return cache.computeIfAbsent(title, t -> bookRepository.findByCurriculumIdAndTitle(curriculum.getId(), t)
                .orElseGet(() -> {
                    Long bookId = curriculumService.addBook(curriculum.getId(), new CreateBookRequest(t, null)).id();
                    return bookRepository.getReferenceById(bookId);
                }));
    }

    private CurriculumUnit resolveUnit(Map<String, CurriculumUnit> cache, Book book, String title) {
        String key = book.getId() + "|" + title;
        return cache.computeIfAbsent(key, k -> curriculumUnitRepository.findByBookIdAndTitle(book.getId(), title)
                .orElseGet(() -> {
                    Long unitId = curriculumService.addUnit(book.getId(), new CreateUnitRequest(title, null)).id();
                    return curriculumUnitRepository.getReferenceById(unitId);
                }));
    }

    private CurriculumSubTopic resolveSubTopic(Map<String, CurriculumSubTopic> cache, CurriculumUnit unit, String title) {
        String key = unit.getId() + "|" + title;
        return cache.computeIfAbsent(key, k -> curriculumSubTopicRepository.findByUnitIdAndTitle(unit.getId(), title)
                .orElseGet(() -> {
                    Long subTopicId = curriculumService.addSubTopic(unit.getId(), new CreateSubTopicRequest(title, null)).id();
                    return curriculumSubTopicRepository.getReferenceById(subTopicId);
                }));
    }

    /** Đề (Lesson) đã tồn tại đúng code -> TÁI SỬ DỤNG nguyên vẹn, không ghi đè teacherType/examType/subTopic. */
    private Exam resolveExam(Map<String, Exam> cache, Curriculum curriculum, CurriculumSubTopic subTopic,
                              String code, String teacherType, String examType, Long actorUserId) {
        return cache.computeIfAbsent(code, c -> examRepository.findByCode(c)
                .orElseGet(() -> {
                    CreateExamRequest request = new CreateExamRequest(
                            c, c, curriculum.getId(), teacherType, examType, subTopic.getId());
                    Long examId = examService.createExam(request, actorUserId).id();
                    return examRepository.findByIdAndDeletedAtIsNull(examId).orElseThrow();
                }));
    }

    /** Bài đã tồn tại đúng code -> TÁI SỬ DỤNG nguyên vẹn, không ghi đè exerciseType/totalPoints. */
    private void resolveExercise(Exam exam, String code, String name, String exerciseType,
                                  BigDecimal totalPoints, Long actorUserId) {
        if (exerciseRepository.findByCode(code).isPresent()) {
            return;
        }
        CreateExerciseRequest request = new CreateExerciseRequest(
                code, name, exam.getId(), null, exerciseType, totalPoints, null, true, null, true);
        exerciseService.createExercise(request, actorUserId);
    }

    // ===================== Helpers =====================

    private String cell(Row row, DataFormatter formatter, int index) {
        var c = row.getCell(index);
        return c == null ? null : formatter.formatCellValue(c).trim();
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        for (int i = 0; i < COLUMN_COUNT; i++) {
            String value = cell(row, formatter, i);
            if (!isBlank(value)) {
                return false;
            }
        }
        return true;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isValidTeacherType(String raw) {
        try {
            Exam.TeacherType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private Map<String, Object> rowError(int rowNumber, String reason) {
        Map<String, Object> error = new HashMap<>();
        error.put("row", rowNumber);
        error.put("reason", reason);
        return error;
    }

    /** A3: file sai định dạng hoàn toàn — không tạo bản ghi nào, đánh dấu FAILED ngay. */
    private BookCatalogImportResponse failJob(ImportJob job, String reason) {
        job.setStatus(ImportJob.Status.FAILED);
        job.setErrorSummary(List.of(rowError(0, reason)));
        job.setFinishedAt(OffsetDateTime.now());
        job = importJobRepository.save(job);
        return toResponse(job);
    }

    private BookCatalogImportResponse toResponse(ImportJob job) {
        return new BookCatalogImportResponse(
                job.getId(), job.getSourceFileName(), job.getTotalRows(), job.getSuccessRows(),
                job.getFailedRows(), job.getStatus().name(), job.getErrorSummary());
    }
}
