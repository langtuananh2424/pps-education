package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.common.ExcelExportHelper;
import vn.com.pps.education.domain.AcademicTerm;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.dto.EnrollmentMovementClassRow;
import vn.com.pps.education.dto.EnrollmentMovementStatsResponse;
import vn.com.pps.education.dto.EnrollmentMovementTrendPoint;
import vn.com.pps.education.dto.EnrollmentMovementTrendResponse;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.AcademicTermRepository;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.SiteManagerRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * UC-69: Thống kê biến động học sinh các lớp theo kỳ (FR-ACA-09, bổ sung
 * ngoài SDD gốc, đã xác nhận với người dùng 2026-08-11). Xem
 * docs/uc/phan-he-06-hoc-thuat.md.
 * <p>
 * Thuần đọc/báo cáo — dữ liệu TÍNH RA (derived) từ {@code class_enrollments}
 * lọc theo [startDate, endDate] của {@code academic_terms} (kỳ luôn gắn 1
 * điểm trường), giống cách "Hồ sơ lớp/học sinh theo kỳ" đã mô tả ở
 * docs/sdd-groups/06-hoc-thuat.md mục c) — không có bảng snapshot riêng.
 */
@Service
public class EnrollmentMovementReportService {

    private static final String PERM_ENROLLMENT_STATS_VIEW = "report.enrollment-stats.view";

    private final AcademicTermRepository academicTermRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final SiteManagerRepository siteManagerRepository;
    private final PermissionEvaluationService permissionEvaluationService;

    public EnrollmentMovementReportService(AcademicTermRepository academicTermRepository,
                                            SchoolClassRepository schoolClassRepository,
                                            ClassEnrollmentRepository classEnrollmentRepository,
                                            SiteManagerRepository siteManagerRepository,
                                            PermissionEvaluationService permissionEvaluationService) {
        this.academicTermRepository = academicTermRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.siteManagerRepository = siteManagerRepository;
        this.permissionEvaluationService = permissionEvaluationService;
    }

    @Transactional(readOnly = true)
    public EnrollmentMovementStatsResponse getStats(Long academicTermId, Long classId, Long actorUserId) {
        AcademicTerm term = getTermInScope(academicTermId, actorUserId);
        List<SchoolClass> classes = resolveClasses(term, classId);

        List<EnrollmentMovementClassRow> rows = classes.stream()
                .map(sc -> computeRow(sc, term.getStartDate(), term.getEndDate()))
                .toList();

        return new EnrollmentMovementStatsResponse(
                term.getId(), term.getName(), term.getStartDate(), term.getEndDate(),
                term.getSite().getId(), term.getSite().getName(), rows, sumTotals(rows));
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
        List<SchoolClass> classes = resolveClasses(term, classId);

        List<EnrollmentMovementTrendPoint> points = new ArrayList<>();
        LocalDate cursor = term.getStartDate();
        int monthIndex = 1;
        while (!cursor.isAfter(term.getEndDate())) {
            LocalDate periodStart = cursor;
            LocalDate monthEnd = cursor.withDayOfMonth(cursor.lengthOfMonth());
            LocalDate periodEnd = monthEnd.isAfter(term.getEndDate()) ? term.getEndDate() : monthEnd;

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

        return new EnrollmentMovementTrendResponse(term.getId(), term.getName(), term.getStartDate(), term.getEndDate(),
                term.getSite().getId(), term.getSite().getName(), points);
    }

    private AcademicTerm getTermInScope(Long academicTermId, Long actorUserId) {
        AcademicTerm term = academicTermRepository.findById(academicTermId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ học id=" + academicTermId));
        requireSiteScope(term.getSite().getId(), actorUserId);
        return term;
    }

    private List<SchoolClass> resolveClasses(AcademicTerm term, Long classId) {
        if (classId != null) {
            SchoolClass schoolClass = schoolClassRepository.findByIdAndDeletedAtIsNull(classId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học id=" + classId));
            if (!schoolClass.getSite().getId().equals(term.getSite().getId())) {
                throw new ResourceNotFoundException(
                        "Lớp id=" + classId + " không thuộc điểm trường của kỳ học id=" + term.getId() + ".");
            }
            return List.of(schoolClass);
        }
        return schoolClassRepository.findBySiteIdAndDeletedAtIsNull(term.getSite().getId());
    }

    @Transactional(readOnly = true)
    public byte[] exportStatsExcel(Long academicTermId, Long classId, Long actorUserId) {
        EnrollmentMovementStatsResponse stats = getStats(academicTermId, classId, actorUserId);

        List<String> headers = List.of("Mã lớp", "Tên lớp", "Sĩ số đầu kỳ", "Nhập học mới",
                "Nghỉ/rút", "Chuyển lớp", "Hoàn thành", "Sĩ số cuối kỳ");
        List<List<Object>> rows = stats.classes().stream()
                .map(r -> List.<Object>of(r.classCode(), r.className(), r.openingHeadcount(), r.newEnrollments(),
                        r.withdrawnCount(), r.transferredCount(), r.completedCount(), r.closingHeadcount()))
                .toList();

        EnrollmentMovementClassRow totals = stats.totals();
        List<String> notes = List.of(
                "Kỳ: " + stats.academicTermName() + " (" + stats.startDate() + " - " + stats.endDate() + ")",
                "Điểm trường: " + stats.siteName(),
                "Tổng sĩ số đầu kỳ: " + totals.openingHeadcount(),
                "Tổng nhập học mới: " + totals.newEnrollments(),
                "Tổng nghỉ/rút: " + totals.withdrawnCount(),
                "Tổng chuyển lớp: " + totals.transferredCount(),
                "Tổng hoàn thành: " + totals.completedCount(),
                "Tổng sĩ số cuối kỳ: " + totals.closingHeadcount());

        return ExcelExportHelper.buildWorkbook("Biến động học sinh theo kỳ", headers, rows, notes);
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
                    "Tài khoản id=" + actorUserId + " không phụ trách điểm trường id=" + siteId + ".");
        }
    }
}
