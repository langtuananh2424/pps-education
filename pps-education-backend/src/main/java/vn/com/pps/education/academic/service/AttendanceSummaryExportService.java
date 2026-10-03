package vn.com.pps.education.academic.service;

import vn.com.pps.education.permission.service.DataScopeService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.common.ExcelExportHelper;
import vn.com.pps.education.academic.domain.ClassEnrollment;
import vn.com.pps.education.academic.domain.SchoolClass;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.academic.repository.AttendanceMarkRepository;
import vn.com.pps.education.academic.repository.ClassEnrollmentRepository;
import vn.com.pps.education.academic.repository.SchoolClassRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Xuất Excel "Tổng hợp chuyên cần" của 1 lớp (V203 — bổ sung ngoài SDD gốc, xác nhận với người dùng
 * 2026-09-30): mỗi học sinh từng ghi danh vào lớp 1 dòng — số lượt được điểm danh, có mặt, đi muộn,
 * về sớm, vắng có phép, vắng không phép và tỷ lệ có mặt trong khoảng ngày chọn. Chỉ tính buổi không
 * huỷ/không dời và đã có điểm danh (attendance_marks), không tự suy vắng cho buổi chưa điểm danh.
 *
 * Phạm vi dữ liệu theo DataScopeService#resolveAllowedSiteIds — lớp ngoài phạm vi coi như không tồn tại.
 */
@Service
public class AttendanceSummaryExportService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Map<ClassEnrollment.Status, String> ENROLLMENT_STATUS_LABELS = Map.of(
            ClassEnrollment.Status.ACTIVE, "Đang học",
            ClassEnrollment.Status.WITHDRAWN, "Đã rút",
            ClassEnrollment.Status.TRANSFERRED, "Đã chuyển lớp",
            ClassEnrollment.Status.COMPLETED, "Hoàn thành");

    private final SchoolClassRepository schoolClassRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final AttendanceMarkRepository attendanceMarkRepository;
    private final DataScopeService dataScopeService;
    private final Clock clock;

    public AttendanceSummaryExportService(SchoolClassRepository schoolClassRepository,
                                          ClassEnrollmentRepository classEnrollmentRepository,
                                          AttendanceMarkRepository attendanceMarkRepository,
                                          DataScopeService dataScopeService,
                                          Clock clock) {
        this.schoolClassRepository = schoolClassRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.attendanceMarkRepository = attendanceMarkRepository;
        this.dataScopeService = dataScopeService;
        this.clock = clock;
    }

    /** fromDate mặc định = ngày bắt đầu lớp, toDate mặc định = hôm nay. */
    @Transactional(readOnly = true)
    public byte[] exportClassSummary(Long classId, LocalDate fromDate, LocalDate toDate, Long actorUserId) {
        SchoolClass schoolClass = schoolClassRepository.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> classNotFound(classId));
        List<Long> allowedSiteIds = dataScopeService.resolveAllowedSiteIds(actorUserId);
        if (allowedSiteIds != null && !allowedSiteIds.contains(schoolClass.getSite().getId())) {
            throw classNotFound(classId);
        }
        LocalDate from = fromDate != null ? fromDate : schoolClass.getStartDate();
        LocalDate to = toDate != null ? toDate : LocalDate.ofInstant(clock.instant(), APP_ZONE);
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Từ ngày phải trước hoặc bằng Đến ngày.");
        }

        Map<Long, AttendanceMarkRepository.StudentAttendanceSummary> summaryByStudent = new HashMap<>();
        for (AttendanceMarkRepository.StudentAttendanceSummary row
                : attendanceMarkRepository.summarizeByStudentForClass(classId, from, to)) {
            summaryByStudent.put(row.getStudentId(), row);
        }
        // 1 học sinh có thể ghi danh lại nhiều lần vào cùng lớp — giữ lần ghi danh gần nhất.
        Map<Long, ClassEnrollment> latestEnrollmentByStudent = new LinkedHashMap<>();
        classEnrollmentRepository.findBySchoolClassId(classId).stream()
                .sorted(Comparator.comparing(ClassEnrollment::getEnrolledDate))
                .forEach(e -> latestEnrollmentByStudent.put(e.getStudent().getId(), e));

        List<String> headers = List.of("Mã học sinh", "Họ và tên", "Trạng thái trong lớp", "Số lượt điểm danh",
                "Có mặt", "Đi muộn", "Về sớm", "Vắng có phép", "Vắng không phép", "Tỷ lệ có mặt (%)");
        List<List<Object>> rows = new ArrayList<>();
        latestEnrollmentByStudent.values().stream()
                .sorted(Comparator.comparing(e -> e.getStudent().getUser().getFullName()))
                .forEach(e -> {
                    AttendanceMarkRepository.StudentAttendanceSummary s = summaryByStudent.get(e.getStudent().getId());
                    long total = s == null ? 0 : s.getTotalMarks();
                    long present = s == null ? 0 : s.getPresentCount();
                    long late = s == null ? 0 : s.getLateCount();
                    long earlyLeave = s == null ? 0 : s.getEarlyLeaveCount();
                    List<Object> row = new ArrayList<>();
                    row.add(e.getStudent().getStudentCode());
                    row.add(e.getStudent().getUser().getFullName());
                    row.add(ENROLLMENT_STATUS_LABELS.getOrDefault(e.getStatus(), e.getStatus().name()));
                    row.add(total);
                    row.add(present);
                    row.add(late);
                    row.add(earlyLeave);
                    row.add(s == null ? 0 : s.getExcusedCount());
                    row.add(s == null ? 0 : s.getAbsentCount());
                    row.add(total == 0 ? null : Math.round((present + late + earlyLeave) * 1000.0 / total) / 10.0);
                    rows.add(row);
                });

        long sessionCount = attendanceMarkRepository.countAttendanceSessionsForClass(classId, from, to);
        List<String> notes = List.of(
                "Lớp: " + schoolClass.getName() + " (" + schoolClass.getClassCode() + ") — " + schoolClass.getSite().getName(),
                "Khoảng thời gian: " + from + " - " + to,
                "Số buổi đã điểm danh: " + sessionCount + " (không tính buổi đã huỷ/đã dời)",
                "Tỷ lệ có mặt = (Có mặt + Đi muộn + Về sớm) / Số lượt điểm danh.");
        return ExcelExportHelper.buildWorkbook("Tổng hợp chuyên cần", headers, rows, notes);
    }

    private static ResourceNotFoundException classNotFound(Long classId) {
        return new ResourceNotFoundException("error.class.notFound", new Object[]{classId},
                "Không tìm thấy lớp học id=" + classId);
    }
}
