package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.SitePeriodTemplate;

import java.util.List;
import java.util.Optional;

public interface SitePeriodTemplateRepository extends JpaRepository<SitePeriodTemplate, Long> {

    /** ORDER BY period_number CHỈ — day_part là STRING nên sắp theo alphabet nếu order by trực tiếp (AFTERNOON<EVENING<MORNING), sai thứ tự hiển thị Sáng-Chiều-Tối; Service tự sắp lại theo DayPart.ordinal(). */
    List<SitePeriodTemplate> findBySiteIdAndDeletedAtIsNullOrderByPeriodNumberAsc(Long siteId);

    Optional<SitePeriodTemplate> findBySiteIdAndDayPartAndPeriodNumberAndDeletedAtIsNull(
            Long siteId, SitePeriodTemplate.DayPart dayPart, int periodNumber);

    boolean existsBySiteIdAndDayPartAndPeriodNumberAndDeletedAtIsNull(
            Long siteId, SitePeriodTemplate.DayPart dayPart, int periodNumber);
}
