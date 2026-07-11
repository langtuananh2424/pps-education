package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ParentStudent;

import java.util.List;
import java.util.Optional;

public interface ParentStudentRepository extends JpaRepository<ParentStudent, Long> {

    List<ParentStudent> findByStudentId(Long studentId);

    Optional<ParentStudent> findByParentIdAndStudentId(Long parentId, Long studentId);
}
