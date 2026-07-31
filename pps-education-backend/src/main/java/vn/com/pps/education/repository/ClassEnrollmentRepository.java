package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ClassEnrollment;

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
}
