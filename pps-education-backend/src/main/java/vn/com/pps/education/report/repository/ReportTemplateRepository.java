package vn.com.pps.education.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.report.domain.ReportTemplate;

import java.util.List;
import java.util.Optional;

public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, Long> {

    List<ReportTemplate> findByActiveTrueOrderByNameAsc();

    List<ReportTemplate> findByTemplateTypeAndActiveTrueOrderByNameAsc(ReportTemplate.TemplateType templateType);

    Optional<ReportTemplate> findByIdAndActiveTrue(Long id);
}
