package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.com.pps.education.domain.ReportTemplatePublishedField;

import java.util.List;

@Repository
public interface ReportTemplatePublishedFieldRepository extends JpaRepository<ReportTemplatePublishedField, Long> {

    List<ReportTemplatePublishedField> findByActiveTrueOrderByTemplateTypeAscDisplayOrderAscIdAsc();
}
