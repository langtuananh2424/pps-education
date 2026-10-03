package vn.com.pps.education.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.academic.domain.ClassTeacherHistory;

import java.util.List;
import java.util.Optional;

public interface ClassTeacherHistoryRepository extends JpaRepository<ClassTeacherHistory, Long> {
    List<ClassTeacherHistory> findByClassTeacherIdOrderByCreatedAtDesc(Long classTeacherId);

    /**
     * UC-18 (bổ sung ngoài SDD gốc, xác nhận 2026-08-13): lịch sử thay đổi
     * giáo viên phụ trách của CẢ LỚP — gộp lịch sử của mọi phân công
     * (class_teacher) từng/đang gắn với lớp này, không chỉ 1 phân công cụ
     * thể như findByClassTeacherIdOrderByCreatedAtDesc.
     */
    List<ClassTeacherHistory> findByClassTeacher_SchoolClass_IdOrderByCreatedAtDesc(Long classId);

    /** Bản ghi lịch sử liền trước của cùng phân công giáo viên — để hiển thị "giá trị cũ → mới" (V203). */
    Optional<ClassTeacherHistory> findFirstByClassTeacherIdAndIdLessThanOrderByIdDesc(Long classTeacherId, Long id);
}
