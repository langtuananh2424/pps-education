package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.Task;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    Optional<Task> findByTaskCode(String taskCode);

    long countByTaskCodeStartingWith(String prefix);

    List<Task> findByCreatedByIdOrderByIdDesc(Long createdByUserId);

    /** TaskSchedulerService — cron nightly đặt OVERDUE (SDD). */
    @Query("""
            SELECT t FROM Task t
            WHERE t.status IN :openStatuses
            AND t.dueAt IS NOT NULL AND t.dueAt < :now
            """)
    List<Task> findOverdue(@Param("now") OffsetDateTime now, @Param("openStatuses") List<Task.Status> openStatuses);

    /** TaskSchedulerService — UC-07 A1: sắp đến hạn, chưa hoàn thành. */
    @Query("""
            SELECT t FROM Task t
            WHERE t.status IN :openStatuses
            AND t.dueAt IS NOT NULL AND t.dueAt BETWEEN :now AND :threshold
            """)
    List<Task> findDueSoon(@Param("now") OffsetDateTime now, @Param("threshold") OffsetDateTime threshold,
                            @Param("openStatuses") List<Task.Status> openStatuses);
}
