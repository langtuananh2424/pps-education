package vn.com.pps.education.service;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.ImportJob;
import vn.com.pps.education.domain.Room;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.ClassScheduleImportResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ImportJobRepository;
import vn.com.pps.education.repository.RoomRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.UserRepository;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-57: Nhập lịch học qua Excel (FR-ACA-05, bổ sung ngoài SDD gốc, đã
 * xác nhận với người dùng). Xem docs/uc/phan-he-06-hoc-thuat.md.
 *
 * Service riêng (không nhét vào ClassSessionService) theo SRP — cùng lý
 * do GradeImportService tách khỏi GradeService.
 *
 * Định dạng file .xlsx: dòng 1 = header, dữ liệu từ dòng 2 — cột tự định
 * nghĩa (không có mẫu trong SRS/SDD, giống tiền lệ UC-35/50/51): A=Ngày
 * (dd/MM/yyyy), B=Giờ bắt đầu (HH:mm), C=Giờ kết thúc (HH:mm), D=Loại
 * buổi (để trống = REGULAR), E=Username giáo viên phụ trách (bắt buộc),
 * F=Mã phòng (tùy chọn, tra theo đúng điểm trường của lớp).
 *
 * Transaction: 1 @Transactional bọc toàn bộ vòng lặp, giống hệt pattern
 * GradeImportService/ParentBatchImportService — 1 dòng lỗi (thiếu GV,
 * sai định dạng ngày/giờ, trùng phòng) không chặn dòng khác, ghi vào
 * error_summary. Dùng lại ImportJob.ImportType.TEACHING_SCHEDULE
 * (placeholder có sẵn từ V1, lần đầu được dùng thật — cột import_type
 * VARCHAR(50) tự do, không cần migration).
 *
 * Tạo từng buổi: gọi lại đúng ClassSessionService#createSessionForImport
 * (package-private, cùng package service) để tái dùng NGUYÊN VẸN logic
 * room-conflict-check + sinh session_periods của UC-48/UC-56 — không
 * viết lại. Method đó KHÔNG @Transactional (xem Javadoc ở đó) để tránh
 * Spring đánh dấu rollbackOnly cho transaction bao ngoài này khi 1 dòng
 * ném RoomConflictException/IllegalArgumentException rồi bị bắt lại ở
 * đây (nested @Transactional khác bean + catch ở caller vẫn có thể bị
 * Spring âm thầm rollback cả giao dịch nếu method đó có @Transactional).
 */
@Service
public class ClassScheduleImportService {

    private static final int HEADER_ROW_INDEX = 0;
    private static final int FIRST_DATA_ROW_INDEX = 1;
    private static final int COLUMN_COUNT = 6;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final ImportJobRepository importJobRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final ClassSessionService classSessionService;

    public ClassScheduleImportService(ImportJobRepository importJobRepository,
                                       SchoolClassRepository schoolClassRepository,
                                       UserRepository userRepository,
                                       RoomRepository roomRepository,
                                       ClassSessionService classSessionService) {
        this.importJobRepository = importJobRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.classSessionService = classSessionService;
    }

    /** Main Flow: tạo import_job PROCESSING, xử lý từng dòng. A2: lỗi từng dòng ghi vào error_summary, không chặn dòng khác. A3: file sai định dạng hoàn toàn → FAILED ngay. */
    @Transactional
    public ClassScheduleImportResponse importSchedule(Long classId, MultipartFile file, Long actorUserId) {
        SchoolClass schoolClass = schoolClassRepository.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học id=" + classId));
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));
        Long siteId = schoolClass.getSite().getId();

        ImportJob job = createJob(file, actor);
        try (InputStream inputStream = file.getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getRow(HEADER_ROW_INDEX) == null) {
                return failJob(job, "File rỗng hoặc thiếu dòng tiêu đề.");
            }

            DataFormatter formatter = new DataFormatter();
            List<Map<String, Object>> errors = new ArrayList<>();
            int totalRows = 0;
            int successRows = 0;
            for (int rowIndex = FIRST_DATA_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row, formatter)) {
                    continue;
                }
                totalRows++;
                try {
                    importRow(row, formatter, classId, siteId, actorUserId);
                    successRows++;
                } catch (RuntimeException ex) {
                    errors.add(rowError(rowIndex + 1, ex.getMessage()));
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
            return failJob(job, "File sai định dạng Excel (.xlsx): " + ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ClassScheduleImportResponse getJob(Long id) {
        ImportJob job = importJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy import job id=" + id));
        return toResponse(job);
    }

    // ===================== Helpers =====================

    /** A2: 1 dòng lỗi (thiếu GV, sai định dạng ngày/giờ, trùng phòng) không chặn dòng khác. */
    private void importRow(Row row, DataFormatter formatter, Long classId, Long siteId, Long actorUserId) {
        String dateText = cell(row, formatter, 0);
        String startTimeText = cell(row, formatter, 1);
        String endTimeText = cell(row, formatter, 2);
        String sessionTypeText = cell(row, formatter, 3);
        String teacherUsername = cell(row, formatter, 4);
        String roomCode = cell(row, formatter, 5);

        if (dateText == null || dateText.isBlank()) {
            throw new IllegalArgumentException("Thiếu ngày (cột A).");
        }
        if (startTimeText == null || startTimeText.isBlank()) {
            throw new IllegalArgumentException("Thiếu giờ bắt đầu (cột B).");
        }
        if (endTimeText == null || endTimeText.isBlank()) {
            throw new IllegalArgumentException("Thiếu giờ kết thúc (cột C).");
        }
        if (teacherUsername == null || teacherUsername.isBlank()) {
            throw new IllegalArgumentException("Thiếu username giáo viên phụ trách (cột E).");
        }

        LocalDate sessionDate = parseDate(dateText);
        LocalTime startTime = parseTime(startTimeText, "giờ bắt đầu");
        LocalTime endTime = parseTime(endTimeText, "giờ kết thúc");
        String sessionType = (sessionTypeText == null || sessionTypeText.isBlank())
                ? ClassSession.SessionType.REGULAR.name() : parseSessionType(sessionTypeText.trim());

        User teacher = userRepository.findByUsername(teacherUsername.trim())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giáo viên username=" + teacherUsername.trim()));

        Long roomId = null;
        if (roomCode != null && !roomCode.isBlank()) {
            Room room = roomRepository.findBySiteIdAndCode(siteId, roomCode.trim())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Không tìm thấy phòng học mã=" + roomCode.trim() + " tại điểm trường của lớp."));
            roomId = room.getId();
        }

        classSessionService.createSessionForImport(
                classId, sessionDate, startTime, endTime, roomId, teacher.getId(), sessionType, actorUserId);
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.trim(), DATE_FORMAT);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Ngày không hợp lệ (cần dd/MM/yyyy): " + text);
        }
    }

    private LocalTime parseTime(String text, String label) {
        try {
            return LocalTime.parse(text.trim(), TIME_FORMAT);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Giờ không hợp lệ (cần HH:mm) ở " + label + ": " + text);
        }
    }

    private String parseSessionType(String text) {
        try {
            return ClassSession.SessionType.valueOf(text.toUpperCase()).name();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Loại buổi không hợp lệ (cần REGULAR/MAKEUP/EXAM/SPECIAL): " + text);
        }
    }

    private String cell(Row row, DataFormatter formatter, int index) {
        var cell = row.getCell(index);
        return cell == null ? null : formatter.formatCellValue(cell).trim();
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        for (int i = 0; i < COLUMN_COUNT; i++) {
            String value = cell(row, formatter, i);
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private ImportJob createJob(MultipartFile file, User actor) {
        ImportJob job = new ImportJob();
        job.setImportType(ImportJob.ImportType.TEACHING_SCHEDULE);
        job.setSourceFileName(file.getOriginalFilename() == null ? "unnamed.xlsx" : file.getOriginalFilename());
        job.setSourceFileUrl("in-memory://" + job.getSourceFileName());
        job.setUploadedBy(actor);
        job.setStatus(ImportJob.Status.PROCESSING);
        job.setStartedAt(OffsetDateTime.now());
        return importJobRepository.save(job);
    }

    private Map<String, Object> rowError(int rowNumber, String reason) {
        Map<String, Object> error = new HashMap<>();
        error.put("row", rowNumber);
        error.put("reason", reason);
        return error;
    }

    /** A3: file sai định dạng hoàn toàn — không xử lý dòng nào, đánh dấu FAILED ngay. */
    private ClassScheduleImportResponse failJob(ImportJob job, String reason) {
        job.setStatus(ImportJob.Status.FAILED);
        job.setErrorSummary(List.of(rowError(0, reason)));
        job.setFinishedAt(OffsetDateTime.now());
        job = importJobRepository.save(job);
        return toResponse(job);
    }

    private ClassScheduleImportResponse toResponse(ImportJob job) {
        return new ClassScheduleImportResponse(
                job.getId(), job.getSourceFileName(), job.getTotalRows(), job.getSuccessRows(),
                job.getFailedRows(), job.getStatus().name(), job.getErrorSummary());
    }
}
