package vn.com.pps.education.lms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.lms.domain.ReviewVideo;

import java.util.List;

public interface ReviewVideoRepository extends JpaRepository<ReviewVideo, Long> {
    List<ReviewVideo> findByReviewVideoSetIdOrderByDisplayOrder(Long reviewVideoSetId);
}
