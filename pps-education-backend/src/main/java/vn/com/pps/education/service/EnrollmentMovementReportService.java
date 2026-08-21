package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.common.ExcelExportHelper;
import vn.com.pps.education.domain.AcademicTerm;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.dto.EnrollmentMovementClassRow;
import vn.com.pps.education.dto.EnrollmentMovementGridColumn;
import vn.com.pps.education.dto.EnrollmentMovementGridResponse;
import vn.com.pps.education.dto.EnrollmentMovementGridRow;
import vn.com.pps.education.dto.EnrollmentMovementStatsResponse;
import vn.com.pps.education.dto.EnrollmentMovementTrendPoint;
import vn.com.pps.education.dto.EnrollmentMovementTrendResponse;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.AcademicTermRepository;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-69: Thống kê biến động học sinh các lớp theo kỳ (FR-ACA-09, bổ sung
 * ngoài SDD gốc, đã xác nhận với người dùng 2026-08-11). Xem
 * docs/uc/phan-he-06-hoc-thuat.md.
 * <p>
 * Thuần đọc/báo cáo — dữ liệu TÍNH RA (derived) từ {@code class_enrollments}
 * lọc theo [startDate, endDate], giống cách "Hồ sơ lớp/học sinh theo kỳ" đã
 * mô tả ở docs/sdd-groups/06-hoc-thuat.md mục c) — không có bảng snapshot
 * riêng.
 * <p>
 * Mở rộng ngoài SDD gốc (xác nhận với người dùng 2026-08-20): ngoài "theo
 * kỳ" (gắn 1 {@code academic_terms}) giờ còn xem được "theo tháng"/"theo
 * năm" — {@link #getStatsForRange}/{@link #getTrendForRange} tổng quát hoá
 * cùng logic tính toán (computeRow/sumTotals) cho BẤT KỲ khoảng ngày nào
 * thuộc 1 điểm trường, không còn bắt buộc phải khớp đúng 1 academic_term.
 * "theo kỳ" (getStats/getTrend) giữ nguyên, chỉ là 1 lớp mỏng gọi lại
 * getStatsForRange/getTrendForRange với khoảng ngày của kỳ đó.
 */
@Service
public class EnrollmentMovementReportService {

    private final AcademicTermRepository academicTermRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final SiteRepository siteRepository;
    private final SiteManagerRepository siteManagerRepository;

    public EnrollmentMovementReportService(AcademicTermRepository academicTermRepository,
                                            SchoolClassRepository schoolClassRepository,
                                            ClassEnrollmentRepository classEnrollmentRepository,
                                            SiteRepository siteRepository,
                                            SiteManagerRepository siteManagerRepository) {
        this.academicTermRepository = academicTermRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.siteRepository = siteRepository;
        this.siteManagerRepository = siteManagerRepository;
    }

    @Transactional(readOnly = true)
    public EnrollmentMovementStatsResponse getStats(Long academicTermId, Long classId, Long actorUserId) {
        AcademicTerm term = getTermInScope(academicTermId, actorUserId);
        return getStatsForRange(term.getSite().getId(), term.getStartDate(), term.getEndDate(), classId,
                actorUserId, "TERM", term.getId(), term.getName());
    }

    @Transactional(readOnly = true)
    public EnrollmentMovementStatsResponse getStatsForRange(Long siteId, LocalDate start, LocalDate end, Long classId,
                                                              Long actorUserId, String periodType, Long academicTermId,
                                                              String periodLabel) {
        Site site = getSiteInScope(siteId, actorUserId);
        List<SchoolClass> classes = resolveClasses(site, classId);

        List<EnrollmentMovementClassRow> rows = classes.stream()
                .map(sc -> computeRow(sc, start, end))
                .toList();

        return new EnrollmentMovementStatsResponse(
                periodType, academicTermId, periodLabel, start, end, site.getId(), site.getName(), rows, sumTotals(rows));
    }

    /**
     * Bổ sung 2026-08-11 (theo yêu cầu người dùng — biểu đồ đường + so sánh
     * giữa các kỳ theo tháng): chia [startDate, endDate] của kỳ thành từng
     * tháng lịch (tháng đầu/cuối có thể bị cắt ngắn theo đúng ranh giới
     * kỳ), mỗi điểm là sĩ số TÍNH RA tại đúng ngày cuối đoạn (giống
     * closingHeadcount của getStats) cộng số biến động phát sinh TRONG
     * đúng tháng đó — cùng cơ chế derived-query, không thêm bảng snapshot.
     */
    @Transactional(readOnly = true)
    public EnrollmentMovementTrendResponse getTrend(Long academicTermId, Long classId, Long actorUserId) {
        AcademicTerm term = getTermInScope(academicTermId, actorUserId);
        return getTrendForRange(term.getSite().getId(), term.getStartDate(), term.getEndDate(), classId,
                actorUserId, "TERM", term.getId(), term.getName());
    }

    @Transactional(readOnly = true)
    public EnrollmentMovementTrendResponse getTrendForRange(Long siteId, LocalDate start, LocalDate end, Long classId,
                                                              Long actorUserId, String periodType, Long academicTermId,
                                                              String periodLabel) {
        Site site = getSiteInScope(siteId, actorUserId);
        List<SchoolClass> classes = resolveClasses(site, classId);

        List<EnrollmentMovementTrendPoint> points = new ArrayList<>();
        LocalDate cursor = start;
        int monthIndex = 1;
        while (!cursor.isAfter(end)) {
            LocalDate periodStart = cursor;
            LocalDate monthEnd = cursor.withDayOfMonth(cursor.lengthOfMonth());
            LocalDate periodEnd = monthEnd.isAfter(end) ? end : monthEnd;

            EnrollmentMovementClassRow aggregate = sumTotals(classes.stream()
                    .map(sc -> computeRow(sc, periodStart, periodEnd))
                    .toList());
            // headcount cuối đoạn dùng closingHeadcount đã tính sẵn trong aggregate (asOf periodEnd).
            points.add(new EnrollmentMovementTrendPoint(monthIndex, periodStart, periodEnd,
                    aggregate.closingHeadcount(), aggregate.newEnrollments(), aggregate.withdrawnCount(),
                    aggregate.transferredCount(), aggregate.completedCount()));

            monthIndex++;
            cursor = periodEnd.plusDays(1);
        }

        return new EnrollmentMovementTrendResponse(
                periodType, academicTermId, periodLabel, start, end, site.getId(), site.getName(), points);
    }

    /**
     * Lưới tổng quan "biến động học sinh" (bổ sung ngoài SDD gốc, xác nhận
     * với người dùng 2026-08-20) — hàng đầu là các tháng/kỳ/năm (theo
     * periodType), cột đầu là từng lớp, mỗi ô là sĩ số cuối đoạn
     * (closingHeadcount) của đúng lớp đó tại đúng cột đó. periodType=MONTH
     * cần thêm year (mặc định năm hiện tại) để biết hiển thị 12 tháng của
     * năm nào; periodType=YEAR hiển thị 6 năm gần nhất (năm hiện tại và 5
     * năm trước); periodType=TERM hiển thị TẤT CẢ kỳ học của điểm trường,
     * sắp theo startDate tăng dần.
     */
    @Transactional(readOnly = true)
    public EnrollmentMovementGridResponse getGrid(Long siteId, String periodType, Integer year, Long classId, Long actorUserId) {
        Site site = getSiteInScope(siteId, actorUserId);
        List<SchoolClass> classes = resolveClasses(site, classId);
        List<EnrollmentMovementGridColumn> columns = buildGridColumns(periodType, year, site);

        List<EnrollmentMovementGridRow> rows = classes.stream()
                .map(sc -> {
                    Map<String, Integer> headcountByColumnKey = new LinkedHashMap<>();
                    for (EnrollmentMovementGridColumn column : columns) {
                        headcountByColumnKey.put(column.key(), computeRow(sc, column.startDate(), column.endDate()).closingHeadcount());
                    }
                    return new EnrollmentMovementGridRow(sc.getId(), sc.getClassCode(), sc.getName(), headcountByColumnKey);
                })
                .toList();

        return new EnrollmentMovementGridResponse(periodType, site.getId(), site.getName(), columns, rows);
    }

    private List<EnrollmentMovementGridColumn> buildGridColumns(String periodType, Integer year, Site site) {
        if ("MONTH".equals(periodType)) {
            int y = year != null ? year : LocalDate.now().getYear();
            List<EnrollmentMovementGridColumn> columns = new ArrayList<>();
            for (int m = 1; m <= 12; m++) {
                LocalDate start = LocalDate.of(y, m, 1);
                LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
                columns.add(new EnrollmentMovementGridColumn(String.format("%04d-%02d", y, m), "T" + m, start, end));
            }
            return columns;
        }
        if ("YEAR".equals(periodType)) {
            int currentYear = LocalDate.now().getYear();
            List<EnrollmentMovementGridColumn> columns = new ArrayList<>();
            for (int y = currentYear - 5; y <= currentYear; y++) {
                columns.add(new EnrollmentMovementGridColumn(String.valueOf(y), String.valueOf(y),
                        LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31)));
            }
            return columns;
        }
        // TERM — tất cả kỳ học của điểm trường, sắp tăng dần theo startDate để đọc trái->phải theo thời gian.
        return academicTermRepository.findBySiteIdOrderByStartDateDesc(site.getId()).stream()
                .sorted(Comparator.comparing(AcademicTerm::getStartDate))
                .map(t -> new EnrollmentMovementGridColumn(String.valueOf(t.getId()), t.getName(), t.getStartDate(), t.getEndDate()))
                .toList();
    }

    private AcademicTerm getTermInScope(Long academicTermId, Long actorUserId) {
        AcademicTerm term = academicTermRepository.findById(academicTermId)
                .orElseThrow(() -> new ResourceNotFoundException("error.enrollmentMovementReport.termNotFound",
                        new Object[]{academicTermId}, "Không tìm thấy kỳ học id=" + academicTermId));
        requireSiteScope(term.getSite().getId(), actorUserId);
        return term;
    }

    private Site getSiteInScope(Long siteId, Long actorUserId) {
        Site site = siteRepository.findById(siteId)
                .orElseThrow(() -> new ResourceNotFoundException("error.enrollmentMovementReport.siteNotFound",
                        new Object[]{siteId}, "Không tìm thấy điểm trường id=" + siteId));
        requireSiteScope(siteId, actorUserId);
        return site;
    }

    private List<SchoolClass> resolveClasses(Site site, Long classId) {
        if (classId != null) {
            SchoolClass schoolClass = schoolClassRepository.findByIdAndDeletedAtIsNull(classId)
                    .orElseThrow(() -> new ResourceNotFoundException("error.enrollmentMovementReport.classNotFound",
                            new Object[]{classId}, "Không tìm thấy lớp học id=" + classId));
            if (!schoolClass.getSite().getId().equals(site.getId())) {
                throw new ResourceNotFoundException("error.enrollmentMovementReport.classNotInSite",
                        new Object[]{classId, site.getId()},
                        "Lớp id=" + classId + " không thuộc điểm trường id=" + site.getId() + ".");
            }
            return List.of(schoolClass);
        }
        return schoolClassRepository.findBySiteIdAndDeletedAtIsNull(site.getId());
    }

    @Transactional(readOnly = true)
    public byte[] exportStatsExcel(Long academicTermId, Long classId, Long actorUserId) {
        EnrollmentMovementStatsResponse stats = getStats(academicTermId, classId, actorUserId);
        return exportStatsExcel(stats);
    }

    @Transactional(readOnly = true)
    public byte[] exportStatsExcelForRange(Long siteId, LocalDate start, LocalDate end, Long classId, Long actorUserId,
                                            String periodType, String periodLabel) {
        EnrollmentMovementStatsResponse stats = getStatsForRange(siteId, start, end, classId, actorUserId,
                periodType, null, periodLabel);
        return exportStatsExcel(stats);
    }

    private byte[] exportStatsExcel(EnrollmentMovementStatsResponse stats) {
        List<String> headers = List.of("Mã lớp", "Tên lớp", "Sĩ số đầu kỳ", "Nhập học mới",
                "Nghỉ/rút", "Chuyển lớp", "Hoàn thành", "Sĩ số cuối kỳ");
        List<List<Object>> rows = stats.classes().stream()
                .map(r -> List.<Object>of(r.classCode(), r.className(), r.openingHeadcount(), r.newEnrollments(),
                        r.withdrawnCount(), r.transferredCount(), r.completedCount(), r.closingHeadcount()))
                .toList();

        EnrollmentMovementClassRow totals = stats.totals();
        List<String> notes = List.of(
                "Kỳ: " + stats.periodLabel() + " (" + stats.startDate() + " - " + stats.endDate() + ")",
                "Điểm trường: " + stats.siteName(),
                "Tổng sĩ số đầu kỳ: " + totals.openingHeadcount(),
                "Tổng nhập học mới: " + totals.newEnrollments(),
                "Tổng nghỉ/rút: " + totals.withdrawnCount(),
                "Tổng chuyển lớp: " + totals.transferredCount(),
                "Tổng hoàn thành: " + totals.completedCount(),
                "Tổng sĩ số cuối kỳ: " + totals.closingHeadcount());

        return ExcelExportHelper.buildWorkbook("Biến động học sinh", headers, rows, notes);
    }

    // ===================== Helpers =====================

    private EnrollmentMovementClassRow computeRow(SchoolClass schoolClass, LocalDate start, LocalDate end) {
        Long classId = schoolClass.getId();
        int opening = classEnrollmentRepository.findActiveAsOf(classId, start).size();
        int closing = classEnrollmentRepository.findActiveAsOf(classId, end).size();
        int newEnrollments = (int) classEnrollmentRepository.countBySchoolClassIdAndEnrolledDateBetween(classId, start, end);
        int withdrawn = (int) classEnrollmentRepository.countBySchoolClassIdAndStatusAndWithdrawnDateBetween(
                classId, ClassEnrollment.Status.WITHDRAWN, start, end);
        int transferred = (int) classEnrollmentRepository.countBySchoolClassIdAndStatusAndWithdrawnDateBetween(
                classId, ClassEnrollment.Status.TRANSFERRED, start, end);
        int completed = (int) classEnrollmentRepository.countBySchoolClassIdAndStatusAndWithdrawnDateBetween(
                classId, ClassEnrollment.Status.COMPLETED, start, end);

        return new EnrollmentMovementClassRow(classId, schoolClass.getClassCode(), schoolClass.getName(),
                opening, newEnrollments, withdrawn, transferred, completed, closing);
    }

    private EnrollmentMovementClassRow sumTotals(List<EnrollmentMovementClassRow> rows) {
        int opening = 0, newEnrollments = 0, withdrawn = 0, transferred = 0, completed = 0, closing = 0;
        for (EnrollmentMovementClassRow row : rows) {
            opening += row.openingHeadcount();
            newEnrollments += row.newEnrollments();
            withdrawn += row.withdrawnCount();
            transferred += row.transferredCount();
            completed += row.completedCount();
            closing += row.closingHeadcount();
        }
        return new EnrollmentMovementClassRow(null, "TONG", "Tổng cộng",
                opening, newEnrollments, withdrawn, transferred, completed, closing);
    }

    /**
     * report.enrollment-stats.view (V114) cấp cho SYS_ADMIN/HEAD_ACADEMIC/SITE_MANAGER
     * (giống pattern V111). Actor có 1 row site_managers (SITE_MANAGER) bị giới hạn
     * đúng (các) điểm trường mình phụ trách; SYS_ADMIN/HEAD_ACADEMIC không có row
     * site_managers nên không bị giới hạn — mirror ClassService#resolveAllowedSiteIds
     * nhưng chỉ dùng 1 permission duy nhất theo đúng V111 đã thiết lập cho các báo cáo
     * cùng nhóm (report.daily-comment.view/report.grade.view/report.student-progress.view).
     */
    private void requireSiteScope(Long siteId, Long actorUserId) {
        List<SiteManager> managedSites = siteManagerRepository
                .findByUserIdAndRoleTypeAndAssignedToIsNull(actorUserId, SiteManager.RoleType.SITE_MANAGER);
        if (managedSites.isEmpty()) {
            return;
        }
        boolean managesThisSite = managedSites.stream().anyMatch(sm -> sm.getSite().getId().equals(siteId));
        if (!managesThisSite) {
            throw new NotSiteManagerForSiteException(
                    "error.notSiteManagerForSite.notInCharge", new Object[]{}, "Bạn không phụ trách điểm trường này.");
        }
    }
}
