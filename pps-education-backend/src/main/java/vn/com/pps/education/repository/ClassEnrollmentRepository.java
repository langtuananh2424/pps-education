package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.ClassEnrollment;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClassEnrollmentRepository extends JpaRepository<ClassEnrollment, Long> {

    List<ClassEnrollment> findBySchoolClassId(Long classId);

    List<ClassEnrollment> findBySchoolClassIdAndStatus(Long classId, ClassEnrollment.Status status);

    List<ClassEnrollment> findByStudentId(Long studentId);

    Optional<ClassEnrollment> findBySchoolClassIdAndStudentIdAndStatus(
            Long classId, Long studentId, ClassEnrollment.Status status);

    long countBySchoolClassIdAndStatus(Long classId, ClassEnrollment.Status status);

    List<ClassEnrollment> findByStudentIdAndStatus(Long studentId, ClassEnrollment.Status status);

    /** UC-23a/UC-60: HS gọi thẳng theo curriculumId (không qua 1 lớp cụ thể) — kiểm tra có học lớp nào thuộc khung này không. */
    boolean existsByStudentIdAndSchoolClass_CurriculumIdAndStatus(Long studentId, Long curriculumId, ClassEnrollment.Status status);

    /** Đã TỪNG ghi danh lớp này hay chưa — bất kể status (ACTIVE/WITHDRAWN/TRANSFERRED/COMPLETED). Dùng cho self-service HS tự xem dữ liệu lớp cũ sau khi chuyển lớp. */
    boolean existsByStudentIdAndSchoolClassId(Long studentId, Long schoolClassId);

    /**
     * V95 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng) — học sinh
     * đang "có mặt" tại lớp vào đúng {@code asOfDate}: đã ghi danh trước/
     * đúng ngày đó, và chưa nghỉ (hoặc nghỉ sau ngày đó). Dùng để chốt
     * danh sách học sinh của 1 {@link vn.com.pps.education.domain.GradeComponentSetup}
     * theo {@code rosterAsOfDate} — không phụ thuộc cột status snapshot
     * hiện tại (1 HS có thể đã WITHDRAWN ở thời điểm gọi API nhưng vẫn
     * "có mặt" tại thời điểm asOfDate trong quá khứ).
     */
    @Query("SELECT ce FROM ClassEnrollment ce WHERE ce.schoolClass.id = :classId "
            + "AND ce.enrolledDate <= :asOfDate "
            + "AND (ce.withdrawnDate IS NULL OR ce.withdrawnDate >= :asOfDate) "
            + "ORDER BY ce.student.id")
    List<ClassEnrollment> findActiveAsOf(@Param("classId") Long classId, @Param("asOfDate") LocalDate asOfDate);

    /** UC-69: học sinh nhập học mới trong kỳ [start, end] — đếm theo enrolledDate, bất kể status hiện tại. */
    long countBySchoolClassIdAndEnrolledDateBetween(Long classId, LocalDate start, LocalDate end);

    /** UC-69: học sinh nghỉ/rút (WITHDRAWN) / chuyển lớp (TRANSFERRED) / hoàn thành (COMPLETED) trong kỳ [start, end] — theo withdrawnDate. */
    long countBySchoolClassIdAndStatusAndWithdrawnDateBetween(Long classId, ClassEnrollment.Status status, LocalDate start, LocalDate end);

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng — dùng cho
     * StudentProfileService (API tổng hợp hồ sơ học tập, FR-REP-04): JOIN
     * FETCH lớp/điểm trường/năm học ngay trong 1 truy vấn để tránh
     * lazy-load N+1 khi duyệt qua toàn bộ lớp đã học của 1 học sinh.
     */
    @Query("""
            SELECT ce FROM ClassEnrollment ce
            JOIN FETCH ce.schoolClass sc
            JOIN FETCH sc.site
            LEFT JOIN FETCH sc.academicYear
            WHERE ce.student.id = :studentId
            ORDER BY ce.enrolledDate DESC
            """)
    List<ClassEnrollment> findByStudentIdWithClassContext(@Param("studentId") Long studentId);
}
