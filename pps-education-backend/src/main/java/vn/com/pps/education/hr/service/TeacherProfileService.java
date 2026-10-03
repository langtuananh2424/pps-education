package vn.com.pps.education.hr.service;

import vn.com.pps.education.permission.service.DataScopeService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.academic.domain.ClassTeacher;
import vn.com.pps.education.hr.domain.Employee;
import vn.com.pps.education.academic.domain.SchoolClass;
import vn.com.pps.education.facility.domain.SiteTeacher;
import vn.com.pps.education.hr.dto.QualificationResponse;
import vn.com.pps.education.hr.dto.TeacherClassAssignmentItem;
import vn.com.pps.education.hr.dto.TeacherCommendationItem;
import vn.com.pps.education.hr.dto.TeacherProfileDetailResponse;
import vn.com.pps.education.hr.dto.TeacherProfileSummaryResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.academic.repository.ClassTeacherRepository;
import vn.com.pps.education.hr.repository.CommendationRepository;
import vn.com.pps.education.hr.repository.EmployeeRepository;
import vn.com.pps.education.hr.repository.QualificationRepository;
import vn.com.pps.education.facility.repository.SiteTeacherRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Trang "Hồ sơ giáo viên" cho Trưởng phòng đào tạo (V203 — bổ sung ngoài SDD gốc, xác nhận với
 * người dùng 2026-09-30, quyền hrm.teacher.view): xem thông tin công việc, bằng cấp, khen thưởng/kỷ
 * luật và các lớp đang phụ trách của GIÁO VIÊN (employee_type = TEACHER). Tách khỏi UC-08 (Quản lý hồ
 * sơ nhân sự, hrm.employee.view) để không lộ CCCD, ngân hàng, mã số thuế, BHXH, hợp đồng/lương.
 *
 * Phạm vi dữ liệu hẹp hơn ALL (DataScopeService#resolveAllowedSiteIds) chỉ thấy giáo viên được gán
 * vào điểm trường mình phụ trách (site_teachers).
 */
@Service
public class TeacherProfileService {

    private static final Set<SchoolClass.Status> ACTIVE_CLASS_STATUSES =
            Set.of(SchoolClass.Status.PLANNED, SchoolClass.Status.OPEN_ENROLLMENT, SchoolClass.Status.IN_PROGRESS);

    private final EmployeeRepository employeeRepository;
    private final QualificationRepository qualificationRepository;
    private final CommendationRepository commendationRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final SiteTeacherRepository siteTeacherRepository;
    private final DataScopeService dataScopeService;

    public TeacherProfileService(EmployeeRepository employeeRepository,
                                 QualificationRepository qualificationRepository,
                                 CommendationRepository commendationRepository,
                                 ClassTeacherRepository classTeacherRepository,
                                 SiteTeacherRepository siteTeacherRepository,
                                 DataScopeService dataScopeService) {
        this.employeeRepository = employeeRepository;
        this.qualificationRepository = qualificationRepository;
        this.commendationRepository = commendationRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.siteTeacherRepository = siteTeacherRepository;
        this.dataScopeService = dataScopeService;
    }

    /** Danh sách giáo viên; query khớp tên/mã/email, siteId lọc giáo viên được gán vào điểm trường đó. */
    @Transactional(readOnly = true)
    public List<TeacherProfileSummaryResponse> search(String query, Long siteId, Long actorUserId) {
        Map<Long, List<SiteTeacher>> siteAssignmentsByUserId = activeSiteAssignmentsByUserId();
        Map<Long, Long> activeClassCountByUserId = new HashMap<>();
        for (ClassTeacherRepository.TeacherActiveClassCount row : classTeacherRepository.countActiveClassesByTeacher()) {
            activeClassCountByUserId.put(row.getTeacherUserId(), row.getClassCount());
        }
        List<Long> allowedSiteIds = dataScopeService.resolveAllowedSiteIds(actorUserId);
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        return employeeRepository.findActiveByEmployeeType(Employee.EmployeeType.TEACHER).stream()
                .filter(e -> isVisible(e, siteAssignmentsByUserId, allowedSiteIds))
                .filter(e -> siteId == null || assignedSiteIds(e, siteAssignmentsByUserId).contains(siteId))
                .filter(e -> normalizedQuery.isEmpty() || matches(e, normalizedQuery))
                .map(e -> toSummary(e, siteAssignmentsByUserId, activeClassCountByUserId.getOrDefault(e.getUser().getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public TeacherProfileDetailResponse getDetail(Long employeeId, Long actorUserId) {
        Employee employee = employeeRepository.findByIdAndDeletedAtIsNull(employeeId)
                .filter(e -> e.getEmployeeType() == Employee.EmployeeType.TEACHER)
                .orElseThrow(() -> notFound(employeeId));
        Map<Long, List<SiteTeacher>> siteAssignmentsByUserId = activeSiteAssignmentsByUserId();
        if (!isVisible(employee, siteAssignmentsByUserId, dataScopeService.resolveAllowedSiteIds(actorUserId))) {
            throw notFound(employeeId);
        }

        List<TeacherClassAssignmentItem> classes = classTeacherRepository.findByTeacherIdAndAssignedToIsNull(employee.getUser().getId())
                .stream()
                .filter(ct -> ct.getSchoolClass().getDeletedAt() == null)
                .sorted(Comparator.comparing((ClassTeacher ct) -> ct.getSchoolClass().getName()))
                .map(this::toClassItem)
                .toList();
        long activeClassCount = classes.stream()
                .filter(c -> ACTIVE_CLASS_STATUSES.contains(SchoolClass.Status.valueOf(c.classStatus())))
                .map(TeacherClassAssignmentItem::classId)
                .distinct()
                .count();

        List<QualificationResponse> qualifications = qualificationRepository.findByEmployeeId(employee.getId()).stream()
                .map(q -> new QualificationResponse(q.getId(), employee.getId(), q.getQualificationType().name(), q.getTitle(),
                        q.getIssuer(), q.getIssuedDate(), q.getExpiryDate(), q.getFileUrl()))
                .toList();
        List<TeacherCommendationItem> commendations = commendationRepository.findByEmployeeId(employee.getId()).stream()
                .sorted(Comparator.comparing(c -> c.getRecordDate(), Comparator.nullsLast(Comparator.reverseOrder())))
                .map(c -> new TeacherCommendationItem(c.getId(), c.getRecordType().name(), c.getRecordDate(), c.getTitle()))
                .toList();

        return new TeacherProfileDetailResponse(
                toSummary(employee, siteAssignmentsByUserId, activeClassCount), qualifications, commendations, classes);
    }

    private Map<Long, List<SiteTeacher>> activeSiteAssignmentsByUserId() {
        Map<Long, List<SiteTeacher>> result = new HashMap<>();
        for (SiteTeacher st : siteTeacherRepository.findByAssignedToIsNull()) {
            result.computeIfAbsent(st.getTeacher().getId(), k -> new ArrayList<>()).add(st);
        }
        return result;
    }

    private static List<Long> assignedSiteIds(Employee e, Map<Long, List<SiteTeacher>> siteAssignmentsByUserId) {
        return siteAssignmentsByUserId.getOrDefault(e.getUser().getId(), List.of()).stream()
                .map(st -> st.getSite().getId()).toList();
    }

    private static boolean isVisible(Employee e, Map<Long, List<SiteTeacher>> siteAssignmentsByUserId, List<Long> allowedSiteIds) {
        return allowedSiteIds == null
                || assignedSiteIds(e, siteAssignmentsByUserId).stream().anyMatch(allowedSiteIds::contains);
    }

    private static boolean matches(Employee e, String normalizedQuery) {
        return contains(e.getUser().getFullName(), normalizedQuery)
                || contains(e.getEmployeeCode(), normalizedQuery)
                || contains(e.getUser().getEmail(), normalizedQuery);
    }

    private static boolean contains(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private TeacherProfileSummaryResponse toSummary(Employee e, Map<Long, List<SiteTeacher>> siteAssignmentsByUserId,
                                                    long activeClassCount) {
        List<String> siteNames = siteAssignmentsByUserId.getOrDefault(e.getUser().getId(), List.of()).stream()
                .map(st -> st.getSite().getName()).distinct().sorted().toList();
        return new TeacherProfileSummaryResponse(
                e.getId(),
                e.getUser().getId(),
                e.getEmployeeCode(),
                e.getUser().getFullName(),
                e.getUser().getEmail(),
                e.getUser().getPhone(),
                e.getPortraitUrl(),
                e.getPosition() == null ? null : e.getPosition().getName(),
                e.getDepartment() == null ? null : e.getDepartment().getName(),
                e.getStatus().name(),
                e.getHireDate(),
                siteNames,
                activeClassCount);
    }

    private TeacherClassAssignmentItem toClassItem(ClassTeacher ct) {
        SchoolClass sc = ct.getSchoolClass();
        return new TeacherClassAssignmentItem(
                sc.getId(), sc.getClassCode(), sc.getName(), sc.getSite().getName(), sc.getStatus().name(),
                ct.getTeacherRole().name(),
                ct.getTeacherType() == null ? null : ct.getTeacherType().name(),
                ct.getAssignedFrom());
    }

    private static ResourceNotFoundException notFound(Long employeeId) {
        return new ResourceNotFoundException("error.teacherProfile.notFound", new Object[]{employeeId},
                "Không tìm thấy giáo viên id=" + employeeId);
    }
}
