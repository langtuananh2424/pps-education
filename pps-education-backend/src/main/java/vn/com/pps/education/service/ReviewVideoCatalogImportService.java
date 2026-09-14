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
import vn.com.pps.education.domain.ImportJob;
import vn.com.pps.education.domain.ReviewVideo;
import vn.com.pps.education.domain.ReviewVideoSet;
import vn.com.pps.education.dto.AddReviewVideoRequest;
import vn.com.pps.education.dto.CreateReviewVideoSetRequest;
import vn.com.pps.education.dto.ReviewVideoCatalogImportResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.BookRepository;
import vn.com.pps.education.repository.CurriculumRepository;
import vn.com.pps.education.repository.CurriculumSubTopicRepository;
import vn.com.pps.education.repository.CurriculumUnitRepository;
import vn.com.pps.education.repository.ImportJobRepository;
import vn.com.pps.education.repository.ReviewVideoRepository;
import vn.com.pps.education.repository.ReviewVideoSetRepository;
import vn.com.pps.education.repository.UserRepository;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * UC-73 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-14) —
 * Import Excel nhanh "bộ" video ôn tập (review_video_sets) + video
 * (review_videos, link YouTube) vào Kho Video Ôn tập (UC-23), mirror
 * {@link BookCatalogImportService} (UC-72). Xem
 * docs/uc/phan-he-07-lms-portal.md (UC-73).
 *
 * Việc GHI Bộ/Video ủy quyền hoàn toàn cho
 * {@link ReviewVideoService#createSet}/{@code addVideo} (điểm ghi DUY NHẤT,
 * dùng chung với thao tác tạo tay ở màn Kho Video — không lặp lại logic
 * tạo entity ở đây, xem .claude/rules/solid.md mục D). Service này CHỈ lo
 * cơ chế import (đọc file, forward-fill, tra cứu idempotent theo khóa tự
 * nhiên, dò thời lượng YouTube, gộp lỗi từng dòng).
 *
 * Định dạng file (9 cột theo thứ tự, dòng 1 = tiêu đề, dữ liệu từ dòng 2):
 * A=Mã bộ, B=Loại video (TKN=CONNECTION/PXA=REFLEX), C=Mã khung chương
 * trình, D=Loại giáo viên (GVVN=VIETNAMESE/GVNN=FOREIGN), E=Tên sách,
 * F=Mã Unit, G=Mã subtopic, H=Tiêu đề video, I=Link video (YouTube). Mỗi
 * dòng = 1 Video; 7 cột A-G để trống nghĩa là LẶP LẠI giá trị dòng liền
 * trước (merged cell khi xuất từ Excel, mirror UC-72) — H/I luôn bắt buộc
 * mỗi dòng. Đổi sang 1 Mã bộ MỚI (cột A vừa có giá trị khác) reset hết
 * B-G về trống — bắt buộc khai lại đủ 6 cột này ở dòng ĐẦU của Bộ mới,
 * không kế thừa nhầm từ Bộ liền trước.
 *
 * Tra Sách/Unit/Sub Topic theo ĐÚNG title đã tạo sẵn trong mục lục sách
 * (khác BookCatalogImportService — KHÔNG tự tạo mới nếu chưa có, báo lỗi
 * ngay vì đây là dữ liệu THAM CHIẾU tới cấu trúc đã có sẵn, không phải
 * dựng mới cấu trúc).
 *
 * Idempotent: Bộ tra theo review_video_sets.code = Mã bộ nguyên văn (V175,
 * code nay UNIQUE) — Bộ đã tồn tại được TÁI SỬ DỤNG nguyên vẹn, KHÔNG ghi
 * đè videoType/teacherType/curriculum/subTopic đã có. Video tra theo
 * (setId, fileUrl) — link đã tồn tại trong CHÍNH bộ đó thì BỎ QUA (không
 * tạo trùng khi import lại cùng file), khác Mã bộ/Mã exercise (video
 * không có cột code riêng).
 */
@Service
public class ReviewVideoCatalogImportService {

    private static final int HEADER_ROW_INDEX = 0;
    private static final int FIRST_DATA_ROW_INDEX = 1;
    private static final int COLUMN_COUNT = 9;

    private final ImportJobRepository importJobRepository;
    private final UserRepository userRepository;
    private final CurriculumRepository curriculumRepository;
    private final BookRepository bookRepository;
    private final CurriculumUnitRepository curriculumUnitRepository;
    private final CurriculumSubTopicRepository curriculumSubTopicRepository;
    private final ReviewVideoSetRepository reviewVideoSetRepository;
    private final ReviewVideoRepository reviewVideoRepository;
    private final ReviewVideoService reviewVideoService;
    private final YouTubeDurationService youTubeDurationService;

    public ReviewVideoCatalogImportService(ImportJobRepository importJobRepository,
                                            UserRepository userRepository,
                                            CurriculumRepository curriculumRepository,
                                            BookRepository bookRepository,
                                            CurriculumUnitRepository curriculumUnitRepository,
                                            CurriculumSubTopicRepository curriculumSubTopicRepository,
                                            ReviewVideoSetRepository reviewVideoSetRepository,
                                            ReviewVideoRepository reviewVideoRepository,
                                            ReviewVideoService reviewVideoService,
                                            YouTubeDurationService youTubeDurationService) {
        this.importJobRepository = importJobRepository;
        this.userRepository = userRepository;
        this.curriculumRepository = curriculumRepository;
        this.bookRepository = bookRepository;
        this.curriculumUnitRepository = curriculumUnitRepository;
        this.curriculumSubTopicRepository = curriculumSubTopicRepository;
        this.reviewVideoSetRepository = reviewVideoSetRepository;
        this.reviewVideoRepository = reviewVideoRepository;
        this.reviewVideoService = reviewVideoService;
        this.youTubeDurationService = youTubeDurationService;
    }

    @Transactional
    public ReviewVideoCatalogImportResponse importCatalog(MultipartFile file, Long actorUserId) {
        if (!userRepository.existsById(actorUserId)) {
            throw new ResourceNotFoundException("error.reviewVideoCatalogImport.userNotFound",
                    new Object[]{actorUserId}, "Không tìm thấy user id=" + actorUserId);
        }

        ImportJob job = new ImportJob();
        job.setImportType(ImportJob.ImportType.REVIEW_VIDEO_CATALOG);
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

            Map<String, Curriculum> curriculumCache = new HashMap<>();
            Map<String, Book> bookCache = new HashMap<>();
            Map<String, CurriculumUnit> unitCache = new HashMap<>();
            Map<String, CurriculumSubTopic> subTopicCache = new HashMap<>();
            Map<String, ReviewVideoSet> setCache = new HashMap<>();
            // Theo dõi trong phạm vi 1 lần import: displayOrder tiếp theo + các fileUrl đã có của mỗi
            // Bộ (khởi tạo từ DB lúc chạm Bộ đó lần đầu trong lần import này) — tránh query lặp lại DB
            // cho các dòng cùng 1 Bộ (thường rất nhiều dòng liên tiếp cùng 1 Bộ), và tránh tạo trùng
            // video khi import lại cùng file (xem Javadoc lớp).
            Map<Long, Integer> nextDisplayOrderBySetId = new HashMap<>();
            Map<Long, Set<String>> existingFileUrlsBySetId = new HashMap<>();

            String lastSetCode = null;
            String lastVideoType = null;
            String lastCurriculumCode = null;
            String lastTeacherType = null;
            String lastBookTitle = null;
            String lastUnitTitle = null;
            String lastSubTopicTitle = null;

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

                String rawSetCode = cell(row, formatter, 0);
                String rawVideoType = cell(row, formatter, 1);
                String rawCurriculumCode = cell(row, formatter, 2);
                String rawTeacherType = cell(row, formatter, 3);
                String rawBookTitle = cell(row, formatter, 4);
                String rawUnitTitle = cell(row, formatter, 5);
                String rawSubTopicTitle = cell(row, formatter, 6);
                String videoTitle = cell(row, formatter, 7);
                String videoLink = cell(row, formatter, 8);

                // Sang Bộ MỚI (cột A vừa có giá trị KHÁC, không phải dòng lặp lại) -> reset hết B-G, bắt
                // buộc khai lại đủ ở dòng đầu Bộ mới — không kế thừa nhầm từ Bộ liền trước (mirror lý do
                // reset teacherType/title mỗi Lesson mới ở BookCatalogImportService).
                if (!isBlank(rawSetCode) && !rawSetCode.trim().equals(lastSetCode)) {
                    lastVideoType = null;
                    lastCurriculumCode = null;
                    lastTeacherType = null;
                    lastBookTitle = null;
                    lastUnitTitle = null;
                    lastSubTopicTitle = null;
                }
                if (!isBlank(rawSetCode)) {
                    lastSetCode = rawSetCode.trim();
                }
                if (!isBlank(rawVideoType)) {
                    lastVideoType = rawVideoType.trim();
                }
                if (!isBlank(rawCurriculumCode)) {
                    lastCurriculumCode = rawCurriculumCode.trim();
                }
                if (!isBlank(rawTeacherType)) {
                    lastTeacherType = rawTeacherType.trim();
                }
                if (!isBlank(rawBookTitle)) {
                    lastBookTitle = rawBookTitle.trim();
                }
                if (!isBlank(rawUnitTitle)) {
                    lastUnitTitle = rawUnitTitle.trim();
                }
                if (!isBlank(rawSubTopicTitle)) {
                    lastSubTopicTitle = rawSubTopicTitle.trim();
                }

                try {
                    if (isBlank(lastSetCode)) {
                        throw new IllegalArgumentException("Thiếu Mã bộ và chưa có dòng trước đó để dùng lại.");
                    }
                    ReviewVideoSet.VideoType videoType = parseVideoType(lastVideoType);
                    ReviewVideoSet.TeacherType teacherType = parseTeacherType(lastTeacherType);
                    if (isBlank(lastCurriculumCode)) {
                        throw new IllegalArgumentException("Thiếu Mã khung chương trình và chưa có dòng trước đó để dùng lại.");
                    }
                    if (isBlank(lastBookTitle)) {
                        throw new IllegalArgumentException("Thiếu Tên sách và chưa có dòng trước đó để dùng lại.");
                    }
                    if (isBlank(lastUnitTitle)) {
                        throw new IllegalArgumentException("Thiếu Mã Unit và chưa có dòng trước đó để dùng lại.");
                    }
                    if (isBlank(lastSubTopicTitle)) {
                        throw new IllegalArgumentException("Thiếu Mã subtopic và chưa có dòng trước đó để dùng lại.");
                    }
                    if (isBlank(videoTitle)) {
                        throw new IllegalArgumentException("Thiếu Tiêu đề video.");
                    }
                    if (isBlank(videoLink)) {
                        throw new IllegalArgumentException("Thiếu Link video.");
                    }
                    String videoId = youTubeDurationService.extractVideoId(videoLink.trim());
                    if (videoId == null) {
                        throw new IllegalArgumentException(
                                "Link video không hợp lệ (không nhận diện được dạng link YouTube): '" + videoLink.trim() + "'.");
                    }

                    Curriculum curriculum = resolveCurriculum(curriculumCache, lastCurriculumCode);
                    Book book = resolveBook(bookCache, curriculum, lastBookTitle);
                    CurriculumUnit unit = resolveUnit(unitCache, book, lastUnitTitle);
                    CurriculumSubTopic subTopic = resolveSubTopic(subTopicCache, unit, lastSubTopicTitle);
                    ReviewVideoSet set = resolveSet(setCache, curriculum, subTopic, lastSetCode, videoType, teacherType, actorUserId);

                    Set<String> existingFileUrls = existingFileUrlsBySetId.computeIfAbsent(set.getId(),
                            id -> new HashSet<>(reviewVideoRepository.findByReviewVideoSetIdOrderByDisplayOrder(id)
                                    .stream().map(ReviewVideo::getFileUrl).toList()));
                    if (!existingFileUrls.contains(videoLink.trim())) {
                        int displayOrder = nextDisplayOrderBySetId.computeIfAbsent(set.getId(),
                                id -> reviewVideoRepository.findByReviewVideoSetIdOrderByDisplayOrder(id).size());
                        int durationSeconds = youTubeDurationService.getDurationSeconds(videoId);
                        AddReviewVideoRequest request = new AddReviewVideoRequest(
                                "YOUTUBE_URL", videoTitle.trim(), videoLink.trim(), null, durationSeconds,
                                displayOrder, null, null, null);
                        reviewVideoService.addVideo(set.getId(), request, actorUserId);
                        existingFileUrls.add(videoLink.trim());
                        nextDisplayOrderBySetId.put(set.getId(), displayOrder + 1);
                    }

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
            return failJob(job, "File sai định dạng Excel (.xlsx) hoặc không đọc được: " + ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ReviewVideoCatalogImportResponse getJob(Long id) {
        ImportJob job = importJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.reviewVideoCatalogImport.jobNotFound",
                        new Object[]{id}, "Không tìm thấy import job id=" + id));
        return toResponse(job);
    }

    // ===================== Tra cứu =====================

    private Curriculum resolveCurriculum(Map<String, Curriculum> cache, String code) {
        return cache.computeIfAbsent(code, c -> curriculumRepository.findByCode(c)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khung chương trình mã '" + c + "'.")));
    }

    private Book resolveBook(Map<String, Book> cache, Curriculum curriculum, String title) {
        String key = curriculum.getId() + "|" + title;
        return cache.computeIfAbsent(key, k -> bookRepository.findByCurriculumIdAndTitle(curriculum.getId(), title)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy Sách '" + title + "' trong khung chương trình mã '" + curriculum.getCode() + "'.")));
    }

    private CurriculumUnit resolveUnit(Map<String, CurriculumUnit> cache, Book book, String title) {
        String key = book.getId() + "|" + title;
        return cache.computeIfAbsent(key, k -> curriculumUnitRepository.findByBookIdAndTitle(book.getId(), title)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy Unit '" + title + "' trong Sách '" + book.getTitle() + "'.")));
    }

    private CurriculumSubTopic resolveSubTopic(Map<String, CurriculumSubTopic> cache, CurriculumUnit unit, String title) {
        String key = unit.getId() + "|" + title;
        return cache.computeIfAbsent(key, k -> curriculumSubTopicRepository.findByUnitIdAndTitle(unit.getId(), title)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy Sub Topic '" + title + "' trong Unit '" + unit.getTitle() + "'.")));
    }

    /** Bộ đã tồn tại đúng code -> TÁI SỬ DỤNG nguyên vẹn, không ghi đè videoType/teacherType/curriculum/subTopic (xem Javadoc lớp). */
    private ReviewVideoSet resolveSet(Map<String, ReviewVideoSet> cache, Curriculum curriculum, CurriculumSubTopic subTopic,
                                       String code, ReviewVideoSet.VideoType videoType, ReviewVideoSet.TeacherType teacherType,
                                       Long actorUserId) {
        return cache.computeIfAbsent(code, c -> reviewVideoSetRepository.findByCode(c)
                .orElseGet(() -> {
                    CreateReviewVideoSetRequest request = new CreateReviewVideoSetRequest(
                            c, c, videoType.name(), curriculum.getId(), teacherType.name(), null, null, subTopic.getId());
                    Long setId = reviewVideoService.createSet(request, actorUserId).id();
                    return reviewVideoSetRepository.findByIdAndDeletedAtIsNull(setId).orElseThrow();
                }));
    }

    // ===================== Helpers =====================

    private ReviewVideoSet.VideoType parseVideoType(String raw) {
        if (isBlank(raw)) {
            throw new IllegalArgumentException("Thiếu Loại video và chưa có dòng trước đó để dùng lại.");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "TKN" -> ReviewVideoSet.VideoType.CONNECTION;
            case "PXA" -> ReviewVideoSet.VideoType.REFLEX;
            default -> throw new IllegalArgumentException(
                    "Loại video không hợp lệ: '" + raw + "' — chỉ chấp nhận TKN (Video từ kết nối)/PXA (Video phản xạ).");
        };
    }

    private ReviewVideoSet.TeacherType parseTeacherType(String raw) {
        if (isBlank(raw)) {
            throw new IllegalArgumentException("Thiếu Loại giáo viên và chưa có dòng trước đó để dùng lại.");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "GVVN" -> ReviewVideoSet.TeacherType.VIETNAMESE;
            case "GVNN" -> ReviewVideoSet.TeacherType.FOREIGN;
            default -> throw new IllegalArgumentException(
                    "Loại giáo viên không hợp lệ: '" + raw + "' — chỉ chấp nhận GVVN (Giáo viên Việt Nam)/GVNN (Giáo viên nước ngoài).");
        };
    }

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

    private Map<String, Object> rowError(int rowNumber, String reason) {
        Map<String, Object> error = new HashMap<>();
        error.put("row", rowNumber);
        error.put("reason", reason);
        return error;
    }

    private ReviewVideoCatalogImportResponse failJob(ImportJob job, String reason) {
        job.setStatus(ImportJob.Status.FAILED);
        job.setErrorSummary(List.of(rowError(0, reason)));
        job.setFinishedAt(OffsetDateTime.now());
        job = importJobRepository.save(job);
        return toResponse(job);
    }

    private ReviewVideoCatalogImportResponse toResponse(ImportJob job) {
        return new ReviewVideoCatalogImportResponse(
                job.getId(), job.getSourceFileName(), job.getTotalRows(), job.getSuccessRows(),
                job.getFailedRows(), job.getStatus().name(), job.getErrorSummary());
    }
}
