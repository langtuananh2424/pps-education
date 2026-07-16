package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.SiteTeacher;

import java.util.List;

public interface SiteTeacherRepository extends JpaRepository<SiteTeacher, Long> {
    boolean existsBySiteIdAndTeacherIdAndAssignedToIsNull(Long siteId, Long teacherId);

    /** Danh sách site đang active của 1 giáo viên — dùng để lọc API liệt kê theo site được gán. */
    List<SiteTeacher> findByTeacherIdAndAssignedToIsNull(Long teacherId);

    List<SiteTeacher> findBySiteIdAndAssignedToIsNull(Long siteId);
}
