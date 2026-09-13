package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.Book;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {
    List<Book> findByCurriculumIdOrderByDisplayOrder(Long curriculumId);

    /** UC-72 (import Excel) — tra cứu idempotent theo (curriculum, title), không dựa vào UNIQUE DB (không có). */
    Optional<Book> findByCurriculumIdAndTitle(Long curriculumId, String title);
}
