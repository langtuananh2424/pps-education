package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ClassSessionHistory;

import java.util.Optional;

public interface ClassSessionHistoryRepository extends JpaRepository<ClassSessionHistory, Long> {

    /** Bản ghi lịch sử liền trước của cùng buổi học — để hiển thị "giá trị cũ → mới" (V203). */
    Optional<ClassSessionHistory> findFirstByClassSessionIdAndIdLessThanOrderByIdDesc(Long classSessionId, Long id);
}
