package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AcademicTerm;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.dto.ActualPeriodsClassRow;
import vn.com.pps.education.dto.ActualPeriodsGridColumn;
import vn.com.pps.education.dto.ActualPeriodsGridResponse;
import vn.com.pps.education.dto.ActualPeriodsGridRow;
import vn.com.pps.education.dto.ActualPeriodsStatsResponse;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.AcademicTermRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.SessionPeriodRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Báo cáo "Số tiết thực tế theo lớp" (bổ sung ngoài SDD gốc, xác nhận với
 * người dùng 2026-08-20) — xem Javadoc ActualPeriodsStatsResponse. Cùng dáng
 * site-scoped derived-report như EnrollmentMovementReportService (không có
 * bảng snapshot riêng, tính trực tiếp từ session_periods/class_sessions).
 */
@Service
public class ActualPeriodsReportService {

    private final SiteRepository siteRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final SessionPeriodRepository sessionPeriodRepository;
    private final SiteManagerRepository siteManagerRepository;
    private final AcademicTermRepository academicTermRepository;

    public ActualPeriodsReportService(SiteRepository siteRepository, SchoolClassRepository schoolClassRepository,
                                       SessionPeriodRepository sessionPeriodRepository,
                                       SiteManagerRepository siteManagerRepository,
                                       AcademicTermRepository academicTermRepository) {
        this.siteRepository = siteRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.sessionPeriodRepository = sessionPeriodRepository;
        this.siteManagerRepository = siteManagerRepository;
        this.academicTermRepository = academicTermRepository;
    }

    @Transactional(readOnly = true)
    public ActualPeriodsStatsResponse getStats(Long siteId, LocalDate start, LocalDate end, Long classId,
                                                Long actorUserId, String periodType, String periodLabel) {
        Site site = getSiteInScope(siteId, actorUserId);
        List<SchoolClass> classes = resolveClasses(site, classId);

        Map<Long, Long> periodCountByClassId = new HashMap<>();
        for (SessionPeriodRepository.ClassActualPeriodCount row : sessionPeriodRepository.countActualPeriodsBySite(siteId, start, end, classId)) {
            periodCountByClassId.put(row.getClassId(), row.getPeriodCount());
        }

        List<ActualPeriodsClassRow> rows = classes.stream()
                .map(sc -> new ActualPeriodsClassRow(sc.getId(), sc.getClassCode(), sc.getName(),
                        periodCountByClassId.getOrDefault(sc.getId(), 0L)))
                .toList();

        long total = rows.stream().mapToLong(ActualPeriodsClassRow::actualPeriods).sum();

        return new ActualPeriodsStatsResponse(periodType, periodLabel, start, end, site.getId(), site.getName(), rows, total);
    }

    /**
     * Lưới tổng quan "số tiết thực tế" (bổ sung ngoài SDD gốc, xác nhận với
     * người dùng 2026-08-20) — hàng đầu là các tháng/kỳ/năm (theo
     * periodType), cột đầu là từng lớp, mỗi ô là số tiết thực tế của đúng
     * lớp đó trong đúng cột đó. Cùng quy ước cột như
     * EnrollmentMovementReportService#buildGridColumns (MONTH cần thêm year,
     * YEAR hiển thị 6 năm gần nhất, TERM hiển thị tất cả kỳ học của site).
     */
    @Transactional(readOnly = true)
    public ActualPeriodsGridResponse getGrid(Long siteId, String periodType, Integer year, Long classId, Long actorUserId) {
        Site site = getSiteInScope(siteId, actorUserId);
        List<SchoolClass> classes = resolveClasses(site, classId);
        List<ActualPeriodsGridColumn> columns = buildGridColumns(periodType, year, site);

        List<ActualPeriodsGridRow> rows = classes.stream()
                .map(sc -> {
                    Map<String, Long> actualPeriodsByColumnKey = new LinkedHashMap<>();
                    for (ActualPeriodsGridColumn column : columns) {
                        long count = sessionPeriodRepository.countActualPeriodsBySite(siteId, column.startDate(), column.endDate(), sc.getId())
                                .stream()
                                .findFirst()
                                .map(SessionPeriodRepository.ClassActualPeriodCount::getPeriodCount)
                                .orElse(0L);
                        actualPeriodsByColumnKey.put(column.key(), count);
                    }
                    return new ActualPeriodsGridRow(sc.getId(), sc.getClassCode(), sc.getName(), actualPeriodsByColumnKey);
                })
                .toList();

        return new ActualPeriodsGridResponse(periodType, site.getId(), site.getName(), columns, rows);
    }

    private List<ActualPeriodsGridColumn> buildGridColumns(String periodType, Integer year, Site site) {
        if ("MONTH".equals(periodType)) {
            int y = year != null ? year : LocalDate.now().getYear();
            List<ActualPeriodsGridColumn> columns = new ArrayList<>();
            for (int m = 1; m <= 12; m++) {
                LocalDate start = LocalDate.of(y, m, 1);
                LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
                columns.add(new ActualPeriodsGridColumn(String.format("%04d-%02d", y, m), "T" + m, start, end));
            }
            return columns;
        }
        if ("YEAR".equals(periodType)) {
            int currentYear = LocalDate.now().getYear();
            List<ActualPeriodsGridColumn> columns = new ArrayList<>();
            for (int y = currentYear - 5; y <= currentYear; y++) {
                columns.add(new ActualPeriodsGridColumn(String.valueOf(y), String.valueOf(y),
                        LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31)));
            }
            return columns;
        }
        // TERM — tất cả kỳ học của điểm trường, sắp tăng dần theo startDate để đọc trái->phải theo thời gian.
        return academicTermRepository.findBySiteIdOrderByStartDateDesc(site.getId()).stream()
                .sorted(Comparator.comparing(AcademicTerm::getStartDate))
                .map(t -> new ActualPeriodsGridColumn(String.valueOf(t.getId()), t.getName(), t.getStartDate(), t.getEndDate()))
                .toList();
    }

    private Site getSiteInScope(Long siteId, Long actorUserId) {
        Site site = siteRepository.findById(siteId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm trường id=" + siteId));
        requireSiteScope(siteId, actorUserId);
        return site;
    }

    private List<SchoolClass> resolveClasses(Site site, Long classId) {
        if (classId != null) {
            SchoolClass schoolClass = schoolClassRepository.findByIdAndDeletedAtIsNull(classId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học id=" + classId));
            if (!schoolClass.getSite().getId().equals(site.getId())) {
                throw new ResourceNotFoundException(
                        "Lớp id=" + classId + " không thuộc điểm trường id=" + site.getId() + ".");
            }
            return List.of(schoolClass);
        }
        return schoolClassRepository.findBySiteIdAndDeletedAtIsNull(site.getId());
    }

    /** Cùng quy tắc phân quyền report.actual-periods.view như report.enrollment-stats.view — xem EnrollmentMovementReportService#requireSiteScope. */
    private void requireSiteScope(Long siteId, Long actorUserId) {
        List<SiteManager> managedSites = siteManagerRepository
                .findByUserIdAndRoleTypeAndAssignedToIsNull(actorUserId, SiteManager.RoleType.SITE_MANAGER);
        if (managedSites.isEmpty()) {
            return;
        }
        boolean managesThisSite = managedSites.stream().anyMatch(sm -> sm.getSite().getId().equals(siteId));
        if (!managesThisSite) {
            throw new NotSiteManagerForSiteException("Bạn không phụ trách điểm trường này.");
        }
    }
}
