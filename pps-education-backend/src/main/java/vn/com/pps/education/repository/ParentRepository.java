package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.Parent;

import java.util.List;
import java.util.Optional;

public interface ParentRepository extends JpaRepository<Parent, Long> {
    Optional<Parent> findByUserId(Long userId);

    @Query("""
            SELECT p FROM Parent p JOIN p.user u
            ORDER BY u.fullName
            """)
    List<Parent> findAllOrderByUserFullName();

    // :query luôn non-null/non-blank ở đây (Service tự tách nhánh) — tránh lỗi
    // Postgres không suy được kiểu tham số NULL lồng trong LOWER/CONCAT (bytea).
    @Query("""
            SELECT p FROM Parent p JOIN p.user u
            WHERE LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%'))
                 OR LOWER(u.phone) LIKE LOWER(CONCAT('%', :query, '%'))
            ORDER BY u.fullName
            """)
    List<Parent> searchByQuery(@Param("query") String query);
}
