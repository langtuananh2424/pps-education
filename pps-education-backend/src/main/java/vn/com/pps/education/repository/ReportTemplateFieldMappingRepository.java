package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReportTemplateFieldMapping;

import java.util.List;

public interface ReportTemplateFieldMappingRepository extends JpaRepository<ReportTemplateFieldMapping, Long> {

    List<ReportTemplateFieldMapping> findByTemplateIdOrderById(Long templateId);

    void deleteByTemplateId(Long templateId);
}
