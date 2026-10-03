package vn.com.pps.education.academic.service;

import vn.com.pps.education.hr.service.TeachingStatsService;
import vn.com.pps.education.permission.service.DataScopeService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.academic.dto.AcademicDashboardResponse;
import vn.com.pps.education.academic.dto.AcademicDashboardSessionItem;
import vn.com.pps.education.hr.dto.TeacherTeachingStatsRow;
import vn.com.pps.education.hr.dto.TeachingStatsResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.academic.repository.AttendanceMarkRepository;
import vn.com.pps.education.academic.repository.ClassSessionRepository;
import vn.com.pps.education.academic.repository.SchoolClassRepository;
import vn.com.pps.education.facility.repository.SiteRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard Trưởng phòng đào tạo (V203 — bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-09-30):
 * số lớp/học sinh/giáo viên đang hoạt động, tỷ lệ chuyên cần 30 ngày, buổi học hôm nay kèm trạng thái
 * nhận lớp và các giáo viên cần chú ý (nhận lớp trễ/không nhận lớp trong 7 ngày). Phạm vi dữ liệu
 * theo DataScopeService#resolveAllowedSiteIds.
 */
@Service
public class AcademicDashboardService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int ATTENDANCE_WINDOW_DAYS = 30;
    private static final int TEACHER_ALERT_WINDOW_DAYS = 7;
    private static final int TEACHER_ALERT_LIMIT = 5;

    private final SchoolClassRepository schoolClassRepository;
    private final AttendanceMarkRepository attendanceMarkRepository;
    private final ClassSessionRepository classSessionRepository;
    private final SiteRepository siteRepository;
    private final TeachingStatsService teachingStatsService;
    private final DataScopeService dataScopeService;
    private final Clock clock;

    public AcademicDashboardService(SchoolClassRepository schoolClassRepository,
                                    AttendanceMarkRepository attendanceMarkRepository,
                                    ClassSessionRepository classSessionRepository,
                                    SiteRepository siteRepository,
                                    TeachingStatsService teachingStatsService,
                                    DataScopeService dataScopeService,
                                    Clock clock) {
        this.schoolClassRepository = schoolClassRepository;
        this.attendanceMarkRepository = attendanceMarkRepository;
        this.classSessionRepository = classSessionRepository;
        this.siteRepository = siteRepository;
        this.teachingStatsService = teachingStatsService;
        this.dataScopeService = dataScopeService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AcademicDashboardResponse getOverview(Long siteId, Long actorUserId) {
        String siteName = siteId == null ? null : siteRepository.findById(siteId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm trường id=" + siteId))
                .getName();
        List<Long> allowedSiteIds = dataScopeService.resolveAllowedSiteIds(actorUserId);
        boolean restrictSites = allowedSiteIds != null;
        List<Long> siteIdsForQuery = allowedSiteIds == null || allowedSiteIds.isEmpty() ? List.of(-1L) : allowedSiteIds;
        long siteFilter = siteId == null ? 0L : siteId;
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), APP_ZONE);
        LocalDate today = now.toLocalDate();

        SchoolClassRepository.AcademicOverviewCounts counts =
                schoolClassRepository.countAcademicOverview(siteFilter, restrictSites, siteIdsForQuery);

        LocalDate attendanceFrom = today.minusDays(ATTENDANCE_WINDOW_DAYS - 1L);
        Map<String, Long> marksByStatus = new HashMap<>();
        for (AttendanceMarkRepository.AttendanceStatusCount row : attendanceMarkRepository.countMarksByStatus(
                attendanceFrom, today, siteFilter, restrictSites, siteIdsForQuery)) {
            marksByStatus.put(row.getStatus(), row.getMarkCount());
        }
        long present = marksByStatus.getOrDefault("PRESENT", 0L);
        long late = marksByStatus.getOrDefault("LATE", 0L);
        long earlyLeave = marksByStatus.getOrDefault("EARLY_LEAVE", 0L);
        long excused = marksByStatus.getOrDefault("EXCUSED", 0L);
        long absent = marksByStatus.getOrDefault("ABSENT", 0L);
        long totalMarks = marksByStatus.values().stream().mapToLong(Long::longValue).sum();
        Double attendanceRate = totalMarks == 0 ? null
                : Math.round((present + late + earlyLeave) * 1000.0 / totalMarks) / 10.0;

        List<AcademicDashboardSessionItem> todaySessions = classSessionRepository
                .findDailySessionOverview(today, now, siteFilter, restrictSites, siteIdsForQuery).stream()
                .map(s -> new AcademicDashboardSessionItem(s.getSessionId(), s.getClassId(), s.getClassName(),
                        s.getClassCode(), s.getSiteName(), s.getTeacherName(), s.getStartTime(), s.getEndTime(),
                        checkInState(s)))
                .toList();

        TeachingStatsResponse recentStats = teachingStatsService.getStats(
                siteId, today.minusDays(TEACHER_ALERT_WINDOW_DAYS - 1L), today, actorUserId);
        List<TeacherTeachingStatsRow> teacherAlerts = recentStats.teachers().stream()
                .filter(r -> r.lateCheckIns() + r.missingCheckIns() > 0)
                .sorted(Comparator.comparingLong((TeacherTeachingStatsRow r) -> r.missingCheckIns() + r.lateCheckIns()).reversed())
                .limit(TEACHER_ALERT_LIMIT)
                .toList();

        return new AcademicDashboardResponse(siteId, siteName,
                counts.getPlannedClasses(), counts.getOpenEnrollmentClasses(), counts.getInProgressClasses(),
                counts.getActiveStudents(), counts.getActiveTeachers(),
                attendanceFrom, today, totalMarks, present, late, earlyLeave, excused, absent, attendanceRate,
                today, todaySessions, teacherAlerts);
    }

    private static String checkInState(ClassSessionRepository.DailySessionOverview s) {
        if ("CANCELLED".equals(s.getStatus())) {
            return "CANCELLED";
        }
        if (s.getCheckInStatus() != null) {
            return s.getCheckInStatus();
        }
        return Boolean.TRUE.equals(s.getStarted()) ? "MISSING" : "NOT_STARTED";
    }
}
