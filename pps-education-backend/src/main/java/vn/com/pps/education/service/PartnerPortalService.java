package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AttendanceMark;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.GradeEntry;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.StudentComment;
import vn.com.pps.education.domain.TeachingPlan;
import vn.com.pps.education.dto.GradeEntryResponse;
import vn.com.pps.education.dto.PartnerAttendanceSummaryResponse;
import vn.com.pps.education.dto.PartnerSiteResponse;
import vn.com.pps.education.dto.StudentCommentResponse;
import vn.com.pps.education.dto.TeachingPlanResponse;
import vn.com.pps.education.exception.NotAuthorizedForPortalAccessException;
import vn.com.pps.education.repository.AttendanceMarkRepository;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.GradeEntryRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.StudentCommentRepository;
import vn.com.pps.education.repository.TeachingPlanRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * UC-29: Xem báo cáo Portal trường liên kết (FR-LMS-08). Xem
 * docs/uc/phan-he-07-lms-portal.md.
 *
 * SDD không có bảng riêng cho "Đại diện trường liên kết được gán vào 1
 * điểm trường" — tái dùng site_managers (giống UC-36 site_managers dùng
 * cho Quản lý điểm trường), phân biệt qua cột role_type=PARTNER_REP
 * (migration V22 — đã xác nhận với user không có bảng thay thế nào khác
 * trong SDD trước khi tái dùng, xem .claude/rules/business-fidelity.md).
 * Nhờ role_type, 1 site có thể có đồng thời 1 SITE_MANAGER active + 1
 * PARTNER_REP active (unique index cũ vốn chỉ cho 1 người/site).
 *
 * Main Flow bước 3 (xuất PDF/Excel) CHƯA triển khai — cần thêm dependency
 * tạo file (Apache POI/iText...), chưa có sẵn trong pom.xml, không tự ý
 * thêm dependency mới khi chưa xác nhận với user. Main Flow bước 4 (gửi
 * phản hồi) thuộc UC-38 (Phân hệ 10), ngoài phạm vi UC-29.
 *
 * getApprovedComments (bổ sung ngoài SRS gốc, xác nhận 2026-07-16): UC-29
 * gốc không liệt kê "nhận xét" — chỉ trả về nhận xét đã APPROVED (UC-21/22),
 * chỉ xem, không có endpoint sửa/xóa (đúng Postcondition "không có quyền
 * chỉnh sửa hồ sơ học sinh hoặc dữ liệu học thuật").
 */
@Service
public class PartnerPortalService {

    private final SiteManagerRepository siteManagerRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final AttendanceMarkRepository attendanceMarkRepository;
    private final GradeEntryRepository gradeEntryRepository;
    private final TeachingPlanRepository teachingPlanRepository;
    private final StudentCommentRepository studentCommentRepository;

    public PartnerPortalService(SiteManagerRepository siteManagerRepository,
                                 SchoolClassRepository schoolClassRepository,
                                 ClassEnrollmentRepository classEnrollmentRepository,
                                 AttendanceMarkRepository attendanceMarkRepository,
                                 GradeEntryRepository gradeEntryRepository,
                                 TeachingPlanRepository teachingPlanRepository,
                                 StudentCommentRepository studentCommentRepository) {
        this.siteManagerRepository = siteManagerRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.attendanceMarkRepository = attendanceMarkRepository;
        this.gradeEntryRepository = gradeEntryRepository;
        this.teachingPlanRepository = teachingPlanRepository;
        this.studentCommentRepository = studentCommentRepository;
    }

    /** Main Flow bước 1: xác định điểm trường liên kết đang phụ trách. */
    @Transactional(readOnly = true)
    public PartnerSiteResponse getMySite(Long actorUserId) {
        Site site = requirePartnerSite(actorUserId);
        return new PartnerSiteResponse(site.getId(), site.getCode(), site.getName());
    }

    /**
     * Main Flow bước 2: chuyên cần của học sinh trường mình. A1 (chưa có
     * dữ liệu) thể hiện tự nhiên qua danh sách rỗng — không cần nhánh riêng.
     */
    @Transactional(readOnly = true)
    public List<PartnerAttendanceSummaryResponse> getAttendanceSummary(Long actorUserId) {
        Site site = requirePartnerSite(actorUserId);
        List<SchoolClass> classes = schoolClassRepository.findBySiteIdAndDeletedAtIsNull(site.getId());

        return classes.stream()
                .flatMap(schoolClass -> classEnrollmentRepository
                        .findBySchoolClassIdAndStatus(schoolClass.getId(), ClassEnrollment.Status.ACTIVE).stream()
                        .map(enrollment -> summarize(schoolClass, enrollment)))
                .filter(summary -> summary.totalMarks() > 0)
                .toList();
    }

    /** Main Flow bước 2: kết quả học tập — điểm đã duyệt (UC-20 V44 — chỉ OFFICIAL) của học sinh trường mình. */
    @Transactional(readOnly = true)
    public List<GradeEntryResponse> getPublishedGrades(Long actorUserId) {
        Site site = requirePartnerSite(actorUserId);
        return gradeEntryRepository.findByStatusInAndSiteId(List.of(GradeEntry.Status.OFFICIAL), site.getId())
                .stream().map(this::toResponse).toList();
    }

    /** Main Flow bước 2: kế hoạch giảng dạy do Giáo viên điền (UC-28), đã gửi và cho phép hiển thị Portal trường liên kết. */
    @Transactional(readOnly = true)
    public List<TeachingPlanResponse> getTeachingPlans(Long actorUserId) {
        Site site = requirePartnerSite(actorUserId);
        return teachingPlanRepository.findBySiteIdAndStatusAndVisibleToPartnerTrue(site.getId(), TeachingPlan.Status.PUBLISHED)
                .stream().map(this::toResponse).toList();
    }

    /**
     * Main Flow bước 2 (bổ sung ngoài SRS gốc, xác nhận 2026-07-16): nhận
     * xét học sinh đã duyệt (APPROVED, UC-21/22) của trường mình — chỉ
     * xem, không có quyền sửa/xóa (Postcondition UC-29).
     */
    @Transactional(readOnly = true)
    public List<StudentCommentResponse> getApprovedComments(Long actorUserId) {
        Site site = requirePartnerSite(actorUserId);
        return studentCommentRepository.findByStatusAndSiteId(StudentComment.Status.APPROVED, site.getId())
                .stream().map(this::toResponse).toList();
    }

    // ===================== Helpers =====================

    private Site requirePartnerSite(Long actorUserId) {
        List<SiteManager> assignments = siteManagerRepository
                .findByUserIdAndRoleTypeAndAssignedToIsNull(actorUserId, SiteManager.RoleType.PARTNER_REP);
        SiteManager assignment = assignments.stream()
                .filter(sm -> sm.getSite().getSiteType() == Site.SiteType.PARTNER)
                .findFirst()
                .orElseThrow(() -> new NotAuthorizedForPortalAccessException(
                        "error.notAuthorizedForPortalAccess.noPartnerSiteAssignment", new Object[]{},
                        "Tài khoản của bạn chưa được gán vào điểm trường liên kết (loại PARTNER) nào."));
        return assignment.getSite();
    }

    private PartnerAttendanceSummaryResponse summarize(SchoolClass schoolClass, ClassEnrollment enrollment) {
        List<AttendanceMark> marks = attendanceMarkRepository
                .findByStudentIdAndClassId(enrollment.getStudent().getId(), schoolClass.getId());
        long present = marks.stream().filter(m -> m.getStatus() == AttendanceMark.Status.PRESENT).count();
        long absent = marks.stream().filter(m -> m.getStatus() == AttendanceMark.Status.ABSENT).count();
        long excused = marks.stream().filter(m -> m.getStatus() == AttendanceMark.Status.EXCUSED).count();
        long late = marks.stream().filter(m -> m.getStatus() == AttendanceMark.Status.LATE).count();
        long earlyLeave = marks.stream().filter(m -> m.getStatus() == AttendanceMark.Status.EARLY_LEAVE).count();
        long total = marks.size();
        // Sửa đổi nghiệp vụ 2026-08-04 (đã xác nhận với người dùng, xem UC-15): LATE tính là đi học
        // đầy đủ trong tỷ lệ chuyên cần, không trừ điểm như trước đây (chỉ đếm PRESENT).
        BigDecimal rate = total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(present + late).multiply(new BigDecimal("100"))
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        return new PartnerAttendanceSummaryResponse(
                enrollment.getStudent().getId(), enrollment.getStudent().getUser().getFullName(),
                schoolClass.getId(), schoolClass.getName(), present, absent, excused, late, earlyLeave, total, rate);
    }

    private GradeEntryResponse toResponse(GradeEntry e) {
        return new GradeEntryResponse(
                e.getId(), e.getSchoolClass().getId(), e.getStudent().getId(), e.getStudent().getUser().getFullName(),
                e.getStudent().getStudentCode(), e.getGradeComponent().getId(), e.getAcademicTerm().getId(),
                e.getAcademicYear() == null ? null : e.getAcademicYear().getId(),
                e.getAcademicYear() == null ? null : e.getAcademicYear().getCode(),
                e.getEvaluationType().name(),
                e.getScore(), e.isAbsenceFlag(), e.getTeacherNote(), e.getStatus().name(), e.getEnteredBy().getId(),
                e.getPublishedBy() == null ? null : e.getPublishedBy().getId(), e.getPublishedAt(), e.getFinalizedAt());
    }

    private TeachingPlanResponse toResponse(TeachingPlan p) {
        return new TeachingPlanResponse(
                p.getId(), p.getSchoolClass().getId(), p.getTeacher().getId(), p.getPlanType().name(),
                p.getAcademicYear() == null ? null : p.getAcademicYear().getId(),
                p.getAcademicYear() == null ? null : p.getAcademicYear().getCode(),
                p.getWeekNumber(), p.getWeekStartDate(), p.getWeekEndDate(),
                p.getSummary(), p.getObjectives(), p.getStatus().name(), p.isVisibleToPartner(), p.getPublishedAt());
    }

    private StudentCommentResponse toResponse(StudentComment c) {
        return new StudentCommentResponse(
                c.getId(), c.getStudent().getId(), c.getStudent().getUser().getFullName(), c.getStudent().getDateOfBirth(),
                c.getSchoolClass().getId(), c.getTeacher().getId(), c.getCommentType().name(),
                c.getClassSession().getId(),
                c.getAcademicYear() == null ? null : c.getAcademicYear().getId(),
                c.getAcademicYear() == null ? null : c.getAcademicYear().getCode(),
                c.getCommentDate(), c.getContent(), c.getStructuredContent(), c.getSeverity().name(), c.isWarning(),
                c.getStatus().name(), c.getSubmittedAt(), c.getApprovedAt(),
                c.getApprovedBy() == null ? null : c.getApprovedBy().getId(), c.getVisibleToParentAt(), c.getRejectionReason(),
                c.getAttitude() == null ? null : c.getAttitude().name(), c.getHomeworkPreviousScore(),
                c.getHomeworkPreviousSpeakingScore(),
                // V130: homeworkPreviousReadingScore/WritingScore — Portal trường liên kết chưa cần hiển thị, để trống.
                null, null,
                // Portal trường liên kết chưa cần hiển thị chi tiết BTVN online (UC-21 mở rộng, V55) — để trống, bổ sung khi có yêu cầu.
                // 13 field null: homeworkNextExerciseAssignmentId/Title, homeworkNextReviewVideoAssignmentId/Title,
                // homeworkNextDueAt, pendingHomeworkNextExerciseId/Title (V127), pendingHomeworkNextReviewVideoSetId/Title
                // (V127), pendingHomeworkNextDueDate (V127), grammarPreviousProgress, videoPreviousProgress,
                // homeworkPreviousOfflineText.
                c.getHomeworkNext(),
                // V130: homeworkNextReading/Writing — để trống, mirror ghi chú trên.
                null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, c.getNote(),
                c.getClassSession().getLessonContent());
    }
}
