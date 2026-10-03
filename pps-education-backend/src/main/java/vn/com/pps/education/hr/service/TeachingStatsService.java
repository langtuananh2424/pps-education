package vn.com.pps.education.hr.service;

import vn.com.pps.education.academic.service.SessionReportTrackingService;
import vn.com.pps.education.permission.service.DataScopeService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.common.ExcelExportHelper;
import vn.com.pps.education.hr.domain.Employee;
import vn.com.pps.education.facility.domain.Site;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.academic.dto.SessionReportStatusRow;
import vn.com.pps.education.hr.dto.TeacherTeachingStatsRow;
import vn.com.pps.education.hr.dto.TeachingStatsResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.academic.repository.ClassSessionRepository;
import vn.com.pps.education.hr.repository.EmployeeRepository;
import vn.com.pps.education.facility.repository.SiteRepository;
import vn.com.pps.education.auth.repository.UserRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Thống kê giảng dạy theo giáo viên" (V203 — bổ sung ngoài SDD gốc, xác nhận với người dùng
 * 2026-09-30, quyền report.teacher-stats.view): với mỗi giáo viên trong khoảng ngày chọn — số lớp,
 * số buổi đã xếp/đã diễn ra/bị huỷ, số tiết đã dạy, số lần nhận lớp đúng giờ/trễ và số buổi đã tới giờ
 * mà không nhận lớp. Tính trực tiếp từ class_sessions/session_periods/class_session_check_ins (không
 * có bảng snapshot riêng), cùng dáng báo cáo dẫn xuất như ActualPeriodsReportService.
 *
 * V209 (xác nhận với người dùng 2026-10-02): số tiết tách theo vai trò trong buổi — GV chính / GV phụ /
 * CM — kèm cột vai trò; xem TeacherTeachingStatsRow.
 *
 * Phạm vi dữ liệu theo DataScopeService#resolveAllowedSiteIds; siteId = null là mọi điểm trường trong
 * phạm vi. "Đã diễn ra" = buổi không huỷ/không dời và đã tới giờ bắt đầu (giờ Việt Nam).
 */
@Service
public class TeachingStatsService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ClassSessionRepository classSessionRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final SiteRepository siteRepository;
    private final DataScopeService dataScopeService;
    private final SessionReportTrackingService sessionReportTrackingService;
    private final Clock clock;

    public TeachingStatsService(ClassSessionRepository classSessionRepository,
                                UserRepository userRepository,
                                EmployeeRepository employeeRepository,
                                SiteRepository siteRepository,
                                DataScopeService dataScopeService,
                                SessionReportTrackingService sessionReportTrackingService,
                                Clock clock) {
        this.classSessionRepository = classSessionRepository;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.siteRepository = siteRepository;
        this.dataScopeService = dataScopeService;
        this.sessionReportTrackingService = sessionReportTrackingService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TeachingStatsResponse getStats(Long siteId, LocalDate fromDate, LocalDate toDate, Long actorUserId) {
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("Từ ngày phải trước hoặc bằng Đến ngày.");
        }
        String siteName = null;
        if (siteId != null) {
            Site site = siteRepository.findById(siteId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm trường id=" + siteId));
            siteName = site.getName();
        }
        List<Long> allowedSiteIds = dataScopeService.resolveAllowedSiteIds(actorUserId);
        boolean restrictSites = allowedSiteIds != null;
        List<Long> siteIdsForQuery = allowedSiteIds == null || allowedSiteIds.isEmpty() ? List.of(-1L) : allowedSiteIds;
        long siteFilter = siteId == null ? 0L : siteId;
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), APP_ZONE);

        Map<Long, ClassSessionRepository.TeacherSessionStats> sessionStatsByTeacher = new HashMap<>();
        for (ClassSessionRepository.TeacherSessionStats row : classSessionRepository.aggregateTeacherSessionStats(
                fromDate, toDate, now, siteFilter, restrictSites, siteIdsForQuery)) {
            sessionStatsByTeacher.put(row.getTeacherUserId(), row);
        }
        Map<Long, Long> periodsByTeacher = new HashMap<>();
        for (ClassSessionRepository.TeacherPeriodCount row : classSessionRepository.countTaughtPeriodsByTeacher(
                fromDate, toDate, now, siteFilter, restrictSites, siteIdsForQuery)) {
            periodsByTeacher.put(row.getTeacherUserId(), row.getPeriodCount());
        }
        // V209 — vai trò giáo viên phụ / CM của buổi học: [buổi phụ, tiết phụ, buổi CM, tiết CM].
        Map<Long, long[]> supportByTeacher = new HashMap<>();
        for (ClassSessionRepository.TeacherSupportRoleStats row : classSessionRepository.aggregateSupportRoleStats(
                fromDate, toDate, now, siteFilter, restrictSites, siteIdsForQuery)) {
            long[] v = supportByTeacher.computeIfAbsent(row.getTeacherUserId(), k -> new long[4]);
            int offset = "CM".equals(row.getRole()) ? 2 : 0;
            v[offset] += row.getScheduledSessions();
            v[offset + 1] += row.getPeriodCount();
        }

        // V207 — nộp báo cáo (gửi duyệt nhận xét) theo giáo viên; bỏ qua khi khoảng ngày quá dài.
        boolean withReports = ChronoUnit.DAYS.between(fromDate, toDate) < SessionReportTrackingService.MAX_RANGE_DAYS;
        Map<Long, int[]> reportCountsByTeacher = new HashMap<>();
        if (withReports) {
            for (SessionReportStatusRow r : sessionReportTrackingService.computeRows(siteId, fromDate, toDate, actorUserId)) {
                int[] counts = reportCountsByTeacher.computeIfAbsent(r.teacherUserId(), k -> new int[3]);
                switch (r.submitState()) {
                    case "ON_TIME" -> counts[0]++;
                    case "LATE" -> counts[1]++;
                    case "MISSING" -> counts[2]++;
                    default -> { }
                }
            }
        }

        Set<Long> teacherIds = new HashSet<>(sessionStatsByTeacher.keySet());
        teacherIds.addAll(periodsByTeacher.keySet());
        teacherIds.addAll(supportByTeacher.keySet());
        Map<Long, String> nameById = new HashMap<>();
        for (User user : userRepository.findAllById(teacherIds)) {
            nameById.put(user.getId(), user.getFullName());
        }
        Map<Long, String> codeById = new HashMap<>();
        for (Employee employee : employeeRepository.findByUserIdIn(teacherIds)) {
            codeById.put(employee.getUser().getId(), employee.getEmployeeCode());
        }

        List<TeacherTeachingStatsRow> rows = new ArrayList<>();
        for (Long teacherId : teacherIds) {
            ClassSessionRepository.TeacherSessionStats s = sessionStatsByTeacher.get(teacherId);
            long primaryPeriods = periodsByTeacher.getOrDefault(teacherId, 0L);
            long[] support = supportByTeacher.getOrDefault(teacherId, new long[4]);
            List<String> roles = new ArrayList<>();
            if ((s != null && s.getScheduledSessions() > 0) || primaryPeriods > 0) {
                roles.add("PRIMARY");
            }
            if (support[0] > 0) {
                roles.add("ASSISTANT");
            }
            if (support[2] > 0) {
                roles.add("CM");
            }
            rows.add(buildRow(teacherId, nameById.getOrDefault(teacherId, "—"), codeById.get(teacherId), roles,
                    s == null ? 0 : s.getClassCount(),
                    s == null ? 0 : s.getScheduledSessions(),
                    s == null ? 0 : s.getHeldSessions(),
                    s == null ? 0 : s.getCancelledSessions(),
                    primaryPeriods, support[1], support[3],
                    s == null ? 0 : s.getOnTimeCheckIns(),
                    s == null ? 0 : s.getLateCheckIns(),
                    s == null ? 0 : s.getMissingCheckIns(),
                    withReports ? reportCountsByTeacher.getOrDefault(teacherId, new int[3]) : null));
        }
        rows.sort(Comparator.comparing(TeacherTeachingStatsRow::teacherName, String.CASE_INSENSITIVE_ORDER));

        // Số lớp ở dòng tổng là tổng theo từng giáo viên (1 lớp nhiều giáo viên được đếm nhiều lần).
        TeacherTeachingStatsRow totals = buildRow(null, "Tổng cộng", null, List.of(),
                rows.stream().mapToLong(TeacherTeachingStatsRow::classCount).sum(),
                rows.stream().mapToLong(TeacherTeachingStatsRow::scheduledSessions).sum(),
                rows.stream().mapToLong(TeacherTeachingStatsRow::heldSessions).sum(),
                rows.stream().mapToLong(TeacherTeachingStatsRow::cancelledSessions).sum(),
                rows.stream().mapToLong(TeacherTeachingStatsRow::taughtPeriods).sum(),
                rows.stream().mapToLong(TeacherTeachingStatsRow::assistantPeriods).sum(),
                rows.stream().mapToLong(TeacherTeachingStatsRow::cmPeriods).sum(),
                rows.stream().mapToLong(TeacherTeachingStatsRow::onTimeCheckIns).sum(),
                rows.stream().mapToLong(TeacherTeachingStatsRow::lateCheckIns).sum(),
                rows.stream().mapToLong(TeacherTeachingStatsRow::missingCheckIns).sum(),
                withReports ? new int[]{
                        rows.stream().mapToInt(r -> r.reportOnTimeCount() == null ? 0 : r.reportOnTimeCount()).sum(),
                        rows.stream().mapToInt(r -> r.reportLateCount() == null ? 0 : r.reportLateCount()).sum(),
                        rows.stream().mapToInt(r -> r.reportMissingCount() == null ? 0 : r.reportMissingCount()).sum()} : null);

        return new TeachingStatsResponse(fromDate, toDate, siteId, siteName, rows, totals);
    }

    @Transactional(readOnly = true)
    public byte[] exportStatsExcel(Long siteId, LocalDate fromDate, LocalDate toDate, Long actorUserId) {
        TeachingStatsResponse stats = getStats(siteId, fromDate, toDate, actorUserId);
        List<String> headers = List.of("Mã nhân sự", "Giáo viên", "Vai trò", "Số lớp", "Buổi đã xếp", "Buổi đã diễn ra",
                "Buổi bị huỷ", "Tiết GV chính", "Tiết GV phụ", "Tiết CM", "Tổng tiết", "Nhận lớp đúng giờ", "Nhận lớp trễ", "Không nhận lớp",
                "Tỷ lệ đúng giờ (%)", "Báo cáo đúng hạn", "Báo cáo nộp muộn", "Báo cáo chưa nộp");
        List<List<Object>> rows = new ArrayList<>();
        for (TeacherTeachingStatsRow r : stats.teachers()) {
            rows.add(toExcelRow(r));
        }
        rows.add(toExcelRow(stats.totals()));
        List<String> notes = List.of(
                "Khoảng thời gian: " + stats.fromDate() + " - " + stats.toDate(),
                "Điểm trường: " + (stats.siteName() == null ? "Tất cả điểm trường trong phạm vi" : stats.siteName()),
                "Buổi đã xếp: không tính buổi đã dời (buổi dời sang được tính riêng).",
                "Buổi đã diễn ra: buổi không huỷ/không dời và đã tới giờ bắt đầu.",
                "Vai trò: vai trò của giáo viên trong các buổi học của khoảng thời gian (GV chính / GV phụ / CM).",
                "Tiết GV chính: tiết có giáo viên riêng tính cho giáo viên đó, còn lại tính cho giáo viên chính của buổi.",
                "Tiết GV phụ / Tiết CM: mọi tiết của buổi đã diễn ra mà giáo viên được xếp làm GV phụ / CM.",
                "Số lớp, buổi, nhận lớp và báo cáo chỉ tính theo vai trò GV chính.",
                "Không nhận lớp: buổi đã diễn ra nhưng giáo viên chưa bấm Nhận lớp.",
                "Tỷ lệ đúng giờ = Nhận lớp đúng giờ / Buổi đã diễn ra.",
                "Báo cáo = gửi duyệt nhận xét của buổi, so với hạn nộp trong Cài đặt hệ thống (để trống nếu khoảng ngày quá "
                        + SessionReportTrackingService.MAX_RANGE_DAYS + " ngày).");
        return ExcelExportHelper.buildWorkbook("Giảng dạy theo GV", headers, rows, notes);
    }

    private static List<Object> toExcelRow(TeacherTeachingStatsRow r) {
        List<Object> row = new ArrayList<>();
        row.add(r.employeeCode());
        row.add(r.teacherName());
        row.add(String.join(", ", r.roles().stream().map(TeachingStatsService::roleLabel).toList()));
        row.add(r.classCount());
        row.add(r.scheduledSessions());
        row.add(r.heldSessions());
        row.add(r.cancelledSessions());
        row.add(r.taughtPeriods());
        row.add(r.assistantPeriods());
        row.add(r.cmPeriods());
        row.add(r.totalPeriods());
        row.add(r.onTimeCheckIns());
        row.add(r.lateCheckIns());
        row.add(r.missingCheckIns());
        row.add(r.onTimeRate());
        row.add(r.reportOnTimeCount());
        row.add(r.reportLateCount());
        row.add(r.reportMissingCount());
        return row;
    }

    private static String roleLabel(String role) {
        return switch (role) {
            case "PRIMARY" -> "GV chính";
            case "ASSISTANT" -> "GV phụ";
            case "CM" -> "CM";
            default -> role;
        };
    }

    private static TeacherTeachingStatsRow buildRow(Long teacherUserId, String teacherName, String employeeCode,
                                                    List<String> roles,
                                                    long classCount, long scheduled, long held, long cancelled,
                                                    long primaryPeriods, long assistantPeriods, long cmPeriods,
                                                    long onTime, long late, long missing,
                                                    int[] reportCounts) {
        Double onTimeRate = held == 0 ? null : Math.round(onTime * 1000.0 / held) / 10.0;
        return new TeacherTeachingStatsRow(teacherUserId, teacherName, employeeCode, roles, classCount, scheduled, held,
                cancelled, primaryPeriods, assistantPeriods, cmPeriods, primaryPeriods + assistantPeriods + cmPeriods,
                onTime, late, missing, onTimeRate,
                reportCounts == null ? null : reportCounts[0],
                reportCounts == null ? null : reportCounts[1],
                reportCounts == null ? null : reportCounts[2]);
    }
}
