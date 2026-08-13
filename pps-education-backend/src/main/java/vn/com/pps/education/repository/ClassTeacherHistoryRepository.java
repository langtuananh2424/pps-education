package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ClassTeacherHistory;

import java.util.List;

public interface ClassTeacherHistoryRepository extends JpaRepository<ClassTeacherHistory, Long> {
    List<ClassTeacherHistory> findByClassTeacherIdOrderByCreatedAtDesc(Long classTeacherId);

    /**
     * UC-18 (bổ sung ngoài SDD gốc, xác nhận 2026-08-13): lịch sử thay đổi
     * giáo viên phụ trách của CẢ LỚP — gộp lịch sử của mọi phân công
     * (class_teacher) từng/đang gắn với lớp này, không chỉ 1 phân công cụ
     * thể như findByClassTeacherIdOrderByCreatedAtDesc.
     */
    List<ClassTeacherHistory> findByClassTeacher_SchoolClass_IdOrderByCreatedAtDesc(Long classId);
}
