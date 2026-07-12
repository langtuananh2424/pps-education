package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.TeachingPlan;

import java.util.List;

public interface TeachingPlanRepository extends JpaRepository<TeachingPlan, Long> {
    List<TeachingPlan> findBySchoolClassIdOrderByIdDesc(Long classId);

    List<TeachingPlan> findBySchoolClassIdAndStatusAndVisibleToPartnerTrueOrderByIdDesc(Long classId, TeachingPlan.Status status);

    /** UC-29 Main Flow bước 2: kế hoạch giảng dạy hiển thị cho Portal trường liên kết, phạm vi cả điểm trường. */
    @Query("""
            SELECT p FROM TeachingPlan p
            WHERE p.schoolClass.site.id = :siteId
            AND p.status = :status
            AND p.visibleToPartner = true
            ORDER BY p.id DESC
            """)
    List<TeachingPlan> findBySiteIdAndStatusAndVisibleToPartnerTrue(@Param("siteId") Long siteId, @Param("status") TeachingPlan.Status status);
}
