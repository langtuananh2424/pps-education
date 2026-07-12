package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.Scholarship;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ScholarshipRepository extends JpaRepository<Scholarship, Long> {

    Optional<Scholarship> findByCode(String code);

    List<Scholarship> findByStudentIdAndStatus(Long studentId, Scholarship.Status status);

    default List<Scholarship> findActiveForStudentOn(Long studentId, LocalDate date) {
        return findByStudentIdAndStatus(studentId, Scholarship.Status.ACTIVE).stream()
                .filter(s -> (s.getValidFrom() == null || !s.getValidFrom().isAfter(date))
                        && (s.getValidTo() == null || !s.getValidTo().isBefore(date)))
                .toList();
    }
}
