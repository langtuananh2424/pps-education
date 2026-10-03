package vn.com.pps.education.academic.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.academic.domain.ClassHistory;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClassHistoryRepository extends JpaRepository<ClassHistory, Long> {
    List<ClassHistory> findBySchoolClassIdOrderByCreatedAtDesc(Long schoolClassId);

    /** Bản ghi lịch sử liền trước của cùng lớp — để hiển thị "giá trị cũ → mới" (V203). */
    Optional<ClassHistory> findFirstBySchoolClassIdAndIdLessThanOrderByIdDesc(Long schoolClassId, Long id);

    /**
     * V203 (bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-09-30) — gộp 6 bảng lịch sử
     * (lớp, buổi học, ghi danh, giáo viên phụ trách lớp, học sinh, nhân sự) thành 1 dòng thời gian
     * cho trang "Lịch sử thay đổi dữ liệu". site_id của học sinh = primary_site_id; nhân sự không
     * gắn điểm trường (site_id NULL).
     *
     * V207 (xác nhận với người dùng 2026-10-01) — thêm SESSION_REPORT: các mốc nộp/duyệt báo cáo buổi học
     * dựng từ approval_flows của nhận xét (STUDENT_COMMENT): lượt gửi duyệt gom theo người gửi + phút,
     * lượt duyệt/từ chối gom theo người duyệt + quyết định + phút (history_id âm để không trùng id mốc gửi).
     * owner_user_id = giáo viên phụ trách buổi — dùng cho quy tắc phòng ban (thấy mốc của buổi có giáo viên
     * thuộc phòng mình kể cả khi người duyệt là Quản lý điểm trường ngoài phòng).
     */
    String CHANGE_HISTORY_UNION = """
            SELECT 'CLASS' AS entity_type, h.id AS history_id, c.id AS entity_id,
                   c.id AS class_id, c.name AS class_name, c.class_code AS class_code, c.site_id AS site_id,
                   CAST(NULL AS BIGINT) AS student_id, CAST(NULL AS VARCHAR) AS subject_name,
                   CAST(NULL AS VARCHAR) AS subject_code, CAST(NULL AS VARCHAR) AS session_date,
                   h.action AS action, CAST(h.details AS TEXT) AS details, h.changed_by AS changed_by_id,
                   h.created_at AS created_at, FALSE AS is_teacher, CAST(NULL AS BIGINT) AS owner_user_id
            FROM classes_history h JOIN classes c ON c.id = h.class_id
            UNION ALL
            SELECT 'CLASS_SESSION', h.id, cs.id, c.id, c.name, c.class_code, c.site_id,
                   CAST(NULL AS BIGINT), CAST(NULL AS VARCHAR), CAST(NULL AS VARCHAR),
                   TO_CHAR(cs.session_date, 'YYYY-MM-DD'),
                   h.action, CAST(h.details AS TEXT), h.changed_by, h.created_at, FALSE, CAST(NULL AS BIGINT)
            FROM class_sessions_history h
            JOIN class_sessions cs ON cs.id = h.class_session_id
            JOIN classes c ON c.id = cs.class_id
            UNION ALL
            SELECT 'CLASS_ENROLLMENT', h.id, ce.id, c.id, c.name, c.class_code, c.site_id,
                   s.id, su.full_name, s.student_code, CAST(NULL AS VARCHAR),
                   h.action, CAST(h.details AS TEXT), h.changed_by, h.created_at, FALSE, CAST(NULL AS BIGINT)
            FROM class_enrollments_history h
            JOIN class_enrollments ce ON ce.id = h.class_enrollment_id
            JOIN classes c ON c.id = ce.class_id
            JOIN students s ON s.id = ce.student_id
            JOIN users su ON su.id = s.user_id
            UNION ALL
            SELECT 'CLASS_TEACHER', h.id, ct.id, c.id, c.name, c.class_code, c.site_id,
                   CAST(NULL AS BIGINT), tu.full_name, CAST(NULL AS VARCHAR), CAST(NULL AS VARCHAR),
                   h.action, CAST(h.details AS TEXT), h.changed_by, h.created_at, FALSE, CAST(NULL AS BIGINT)
            FROM class_teachers_history h
            JOIN class_teachers ct ON ct.id = h.class_teacher_id
            JOIN classes c ON c.id = ct.class_id
            JOIN users tu ON tu.id = ct.teacher_user_id
            UNION ALL
            SELECT 'STUDENT', h.id, s.id, CAST(NULL AS BIGINT), CAST(NULL AS VARCHAR), CAST(NULL AS VARCHAR),
                   s.primary_site_id, s.id, su.full_name, s.student_code, CAST(NULL AS VARCHAR),
                   h.action, CAST(h.details AS TEXT), h.changed_by, h.created_at, FALSE, CAST(NULL AS BIGINT)
            FROM students_history h
            JOIN students s ON s.id = h.student_id
            JOIN users su ON su.id = s.user_id
            UNION ALL
            SELECT 'EMPLOYEE', h.id, e.id, CAST(NULL AS BIGINT), CAST(NULL AS VARCHAR), CAST(NULL AS VARCHAR),
                   CAST(NULL AS BIGINT), CAST(NULL AS BIGINT), eu.full_name, e.employee_code, CAST(NULL AS VARCHAR),
                   h.action, CAST(h.details AS TEXT), h.changed_by, h.created_at, e.employee_type = 'TEACHER',
                   CAST(NULL AS BIGINT)
            FROM employees_history h
            JOIN employees e ON e.id = h.employee_id
            JOIN users eu ON eu.id = e.user_id
            UNION ALL
            SELECT 'SESSION_REPORT', MIN(af.id), cs.id, c.id, c.name, c.class_code, c.site_id,
                   CAST(NULL AS BIGINT), pt.full_name, CAST(NULL AS VARCHAR), TO_CHAR(cs.session_date, 'YYYY-MM-DD'),
                   'SUBMITTED', CAST(json_build_object('commentCount', COUNT(*)) AS TEXT),
                   af.submitted_by, MIN(af.submitted_at), FALSE, cs.primary_teacher_id
            FROM approval_flows af
            JOIN student_comments sc ON sc.id = af.entity_id
            JOIN class_sessions cs ON cs.id = sc.class_session_id
            JOIN classes c ON c.id = cs.class_id
            JOIN users pt ON pt.id = cs.primary_teacher_id
            WHERE af.entity_type = 'STUDENT_COMMENT'
            GROUP BY cs.id, c.id, pt.full_name, af.submitted_by, DATE_TRUNC('minute', af.submitted_at)
            UNION ALL
            SELECT 'SESSION_REPORT', -MIN(af.id), cs.id, c.id, c.name, c.class_code, c.site_id,
                   CAST(NULL AS BIGINT), pt.full_name, CAST(NULL AS VARCHAR), TO_CHAR(cs.session_date, 'YYYY-MM-DD'),
                   af.decision, CAST(json_build_object('commentCount', COUNT(*), 'reason', MAX(af.comment)) AS TEXT),
                   af.approver_id, MIN(af.decided_at), FALSE, cs.primary_teacher_id
            FROM approval_flows af
            JOIN student_comments sc ON sc.id = af.entity_id
            JOIN class_sessions cs ON cs.id = sc.class_session_id
            JOIN classes c ON c.id = cs.class_id
            JOIN users pt ON pt.id = cs.primary_teacher_id
            WHERE af.entity_type = 'STUDENT_COMMENT' AND af.decided_at IS NOT NULL
              AND af.approver_id IS NOT NULL AND af.decision IS NOT NULL
            GROUP BY cs.id, c.id, pt.full_name, af.approver_id, af.decision, DATE_TRUNC('minute', af.decided_at)
            """;

    /**
     * Tham số "không lọc" dùng giá trị trung tính thay cho NULL (entityType/keyword = '', siteId/
     * classId/studentId = 0) — tránh lỗi Postgres không suy được kiểu tham số NULL trong native query.
     * siteId lọc lớp/học sinh theo điểm trường nhưng vẫn giữ nhân sự (không gắn điểm trường).
     * restrictSites = phạm vi dữ liệu hẹp hơn ALL — chỉ thấy dữ liệu của các điểm trường trong siteIds.
     * restrictChangedBy (V206) = chỉ thấy thay đổi do các tài khoản trong changedByIds thực hiện
     * (người xem + nhân sự thuộc phòng ban người xem làm trưởng phòng).
     */
    String CHANGE_HISTORY_FILTER = """
            FROM (""" + CHANGE_HISTORY_UNION + """
            ) x
            JOIN users u ON u.id = x.changed_by_id
            WHERE (:entityType = '' OR x.entity_type = :entityType)
              AND x.created_at >= :fromTs AND x.created_at < :toTs
              AND (:siteId = 0 OR x.site_id = :siteId OR x.entity_type = 'EMPLOYEE')
              AND (:classId = 0 OR x.class_id = :classId)
              AND (:studentId = 0 OR x.student_id = :studentId)
              AND (:restrictSites = FALSE OR x.site_id IN (:siteIds))
              AND (x.entity_type <> 'EMPLOYEE' OR (:includeEmployees = TRUE AND (:teacherOnly = FALSE OR x.is_teacher = TRUE)))
              AND (:restrictChangedBy = FALSE OR x.changed_by_id IN (:changedByIds) OR x.owner_user_id IN (:changedByIds))
              AND (:keyword = '' OR LOWER(CONCAT_WS(' ', x.class_name, x.class_code, x.subject_name, x.subject_code, u.full_name)) LIKE :keyword)
            """;

    @Query(value = """
            SELECT x.entity_type AS entityType, x.history_id AS historyId, x.entity_id AS entityId,
                   x.class_id AS classId, x.class_name AS className, x.class_code AS classCode,
                   x.student_id AS studentId, x.subject_name AS subjectName, x.subject_code AS subjectCode,
                   x.session_date AS sessionDate, x.action AS action, x.details AS details,
                   x.changed_by_id AS changedById, u.full_name AS changedByName,
                   CAST(EXTRACT(EPOCH FROM x.created_at) * 1000 AS BIGINT) AS createdAtMillis
            """ + CHANGE_HISTORY_FILTER + """
            ORDER BY x.created_at DESC, x.history_id DESC
            """,
            countQuery = "SELECT COUNT(*) " + CHANGE_HISTORY_FILTER,
            nativeQuery = true)
    Page<ChangeHistoryRow> searchChangeHistory(@Param("entityType") String entityType,
                                               @Param("fromTs") OffsetDateTime fromTs,
                                               @Param("toTs") OffsetDateTime toTs,
                                               @Param("siteId") long siteId,
                                               @Param("classId") long classId,
                                               @Param("studentId") long studentId,
                                               @Param("restrictSites") boolean restrictSites,
                                               @Param("siteIds") Collection<Long> siteIds,
                                               @Param("includeEmployees") boolean includeEmployees,
                                               @Param("teacherOnly") boolean teacherOnly,
                                               @Param("restrictChangedBy") boolean restrictChangedBy,
                                               @Param("changedByIds") Collection<Long> changedByIds,
                                               @Param("keyword") String keyword,
                                               Pageable pageable);

    interface ChangeHistoryRow {
        String getEntityType();
        Long getHistoryId();
        Long getEntityId();
        Long getClassId();
        String getClassName();
        String getClassCode();
        Long getStudentId();
        String getSubjectName();
        String getSubjectCode();
        String getSessionDate();
        String getAction();
        String getDetails();
        Long getChangedById();
        String getChangedByName();
        Long getCreatedAtMillis();
    }
}
