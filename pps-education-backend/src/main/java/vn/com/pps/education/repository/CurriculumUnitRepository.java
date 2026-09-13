package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.CurriculumUnit;

import java.util.List;
import java.util.Optional;

public interface CurriculumUnitRepository extends JpaRepository<CurriculumUnit, Long> {
    List<CurriculumUnit> findByBookIdOrderByDisplayOrder(Long bookId);

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — gate "Xóa Sách" (xem CurriculumService#deleteBook): còn Unit thì không cho xóa. */
    boolean existsByBookId(Long bookId);

    /** UC-72 (import Excel) — tra cứu idempotent theo (book, title), không dựa vào UNIQUE DB (không có). */
    Optional<CurriculumUnit> findByBookIdAndTitle(Long bookId, String title);
}
