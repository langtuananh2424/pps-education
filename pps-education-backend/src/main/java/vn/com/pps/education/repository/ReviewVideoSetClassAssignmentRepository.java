package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReviewVideoSetClassAssignment;

import java.util.List;
import java.util.Optional;

public interface ReviewVideoSetClassAssignmentRepository extends JpaRepository<ReviewVideoSetClassAssignment, Long> {
    boolean existsByReviewVideoSetIdAndSchoolClassId(Long reviewVideoSetId, Long classId);

    Optional<ReviewVideoSetClassAssignment> findByReviewVideoSetIdAndSchoolClassId(Long reviewVideoSetId, Long classId);

    List<ReviewVideoSetClassAssignment> findByReviewVideoSetId(Long reviewVideoSetId);
}
