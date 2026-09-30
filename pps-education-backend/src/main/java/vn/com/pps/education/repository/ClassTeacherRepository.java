package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.ClassTeacher;

import java.util.List;
import java.util.Optional;

public interface ClassTeacherRepository extends JpaRepository<ClassTeacher, Long> {
    List<ClassTeacher> findBySchoolClassId(Long classId);
    Optional<ClassTeacher> findBySchoolClassIdAndTeacherRoleAndSubjectIdIsNullAndAssignedToIsNull(
            Long classId, ClassTeacher.TeacherRole teacherRole);

    /**
     * UC-48/UC-56/UC-57 (bổ sung ngoài SDD gốc, xác nhận 2026-08-13):
     * giáo viên chính (PRIMARY) đang active của lớp theo đúng loại giáo
     * viên (VIETNAMESE/FOREIGN) — dùng để tự động derive giáo viên phụ
     * trách buổi học thay vì nhập tay.
     */
    Optional<ClassTeacher> findBySchoolClassIdAndTeacherRoleAndTeacherTypeAndSubjectIdIsNullAndAssignedToIsNull(
            Long classId, ClassTeacher.TeacherRole teacherRole, ClassSession.TeacherType teacherType);
    boolean existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(Long classId, Long teacherId);

    /** UC-62 — GV xem hàng chờ phúc khảo của (các) lớp mình đang phụ trách. */
    List<ClassTeacher> findByTeacherIdAndAssignedToIsNull(Long teacherId);

    /** UC-23: GV được coi là "phụ trách khung chương trình" nếu đang dạy ít nhất 1 lớp dùng khung đó. */
    boolean existsBySchoolClass_CurriculumIdAndTeacherIdAndAssignedToIsNull(Long curriculumId, Long teacherId);

    /** Chỉ giáo viên ĐANG phụ trách (không lấy cả giáo viên cũ đã thôi phụ trách) — dùng khi báo thông báo. */
    List<ClassTeacher> findBySchoolClassIdAndAssignedToIsNull(Long classId);

    /**
     * V203 (Hồ sơ giáo viên, bổ sung ngoài SDD gốc, xác nhận 2026-09-30) — số lớp chưa kết thúc
     * (PLANNED/OPEN_ENROLLMENT/IN_PROGRESS, chưa xoá) mỗi giáo viên đang phụ trách.
     */
    @Query("""
            SELECT ct.teacher.id AS teacherUserId, COUNT(DISTINCT ct.schoolClass.id) AS classCount
            FROM ClassTeacher ct
            WHERE ct.assignedTo IS NULL
              AND ct.schoolClass.deletedAt IS NULL
              AND ct.schoolClass.status IN (
                  vn.com.pps.education.domain.SchoolClass.Status.PLANNED,
                  vn.com.pps.education.domain.SchoolClass.Status.OPEN_ENROLLMENT,
                  vn.com.pps.education.domain.SchoolClass.Status.IN_PROGRESS
              )
            GROUP BY ct.teacher.id
            """)
    List<TeacherActiveClassCount> countActiveClassesByTeacher();

    interface TeacherActiveClassCount {
        Long getTeacherUserId();
        Long getClassCount();
    }
}
