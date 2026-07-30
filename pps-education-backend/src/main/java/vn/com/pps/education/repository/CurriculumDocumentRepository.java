package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.CurriculumDocument;

import java.util.List;

public interface CurriculumDocumentRepository extends JpaRepository<CurriculumDocument, Long> {

    /** Staff (lms.document.view) — xem mọi status để quản lý. */
    List<CurriculumDocument> findByCurriculumIdOrderByDisplayOrder(Long curriculumId);

    /** Học sinh — chỉ PUBLISHED, theo danh sách curriculum của các lớp đang ghi danh ACTIVE. */
    List<CurriculumDocument> findByCurriculumIdInAndStatusOrderByDisplayOrder(
            List<Long> curriculumIds, CurriculumDocument.Status status);
}
