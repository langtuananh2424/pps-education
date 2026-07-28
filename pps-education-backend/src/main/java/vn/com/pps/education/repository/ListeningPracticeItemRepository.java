package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ListeningPracticeItem;

import java.util.List;

public interface ListeningPracticeItemRepository extends JpaRepository<ListeningPracticeItem, Long> {

    /** Staff (lms.listening-practice.manage) — xem mọi status để quản lý. */
    List<ListeningPracticeItem> findByCurriculumIdOrderByDisplayOrder(Long curriculumId);

    /** Học sinh — chỉ PUBLISHED, theo danh sách curriculum của các lớp đang ghi danh ACTIVE, lọc theo mode tùy chọn. */
    List<ListeningPracticeItem> findByCurriculumIdInAndStatusOrderByDisplayOrder(
            List<Long> curriculumIds, ListeningPracticeItem.Status status);

    List<ListeningPracticeItem> findByCurriculumIdInAndStatusAndModeOrderByDisplayOrder(
            List<Long> curriculumIds, ListeningPracticeItem.Status status, ListeningPracticeItem.Mode mode);
}
