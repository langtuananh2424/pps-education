package vn.com.pps.education.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.report.domain.ReportTemplateFieldMapping;

import java.util.List;

public interface ReportTemplateFieldMappingRepository extends JpaRepository<ReportTemplateFieldMapping, Long> {

    List<ReportTemplateFieldMapping> findByTemplateIdOrderById(Long templateId);

    void deleteByTemplateId(Long templateId);
}
