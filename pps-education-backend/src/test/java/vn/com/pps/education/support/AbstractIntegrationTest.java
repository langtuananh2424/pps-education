package vn.com.pps.education.support;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.TestTransaction;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Base cho integration test chạm DB thật qua Testcontainers (postgis/postgis:16-3.4,
 * khớp image dùng ở backend-ci.yml) — không dùng H2 (xem .claude/rules/testing.md).
 * Flyway chạy migration thật trên container khi Spring context khởi động.
 *
 * Container KHÔNG dùng @Container/@Testcontainers — annotation đó khiến JUnit5
 * dừng container sau afterAll của MỖI class kế thừa, dù field static này chỉ có
 * 1 instance dùng chung cho tất cả class con (bug lộ ra khi có ≥2 class test kế
 * thừa AbstractIntegrationTest — class chạy sau bị "Connection refused" vì
 * container đã bị class chạy trước dừng). Tự start 1 lần trong static block,
 * để JVM/Ryuk dọn dẹp khi test suite kết thúc — đúng pattern "singleton container"
 * khuyến nghị của Testcontainers khi chia sẻ 1 container cho nhiều test class.
 *
 * V71 (2026-08-03) thêm PROPAGATION_REQUIRES_NEW vào deliverToClass để chặn race
 * condition tạo trùng bản giao. Giao dịch lồng dùng connection riêng → không thấy
 * dữ liệu chưa commit từ test @Transactional → FK fail. Helper commitCurrentTransaction()
 * dùng TestTransaction để commit giữa chừng (sau setUp, trước code gọi deliverToClass).
 *
 * V90+ (2026-08-05) từng thêm @DirtiesContext(classMode=AFTER_CLASS) định fix test data
 * carryover khi singleton container chia sẻ giữa test classes — KHÔNG hiệu quả: annotation
 * này chỉ buộc Spring nạp lại ApplicationContext (bean mới), không đụng gì tới dữ liệu
 * trong container Postgres (schema/rows vẫn nguyên). Đã bỏ annotation này.
 *
 * Nguyên nhân thật sự của carryover: mọi test @Transactional bình thường tự rollback nên
 * không để lại gì — CHỈ những test gọi commitCurrentTransactionAndStartNew() (V71 workaround
 * cho PROPAGATION_REQUIRES_NEW) mới thật sự COMMIT fixture (curriculum/exam/student code...)
 * vĩnh viễn vào container dùng chung, rò rỉ sang mọi test class chạy sau (tái hiện ngày
 * 2026-08-05 khi chạy `mvn test` toàn bộ 850 test — DuplicateCurriculumCode/duplicate key ở
 * hàng loạt test class không liên quan). Fix: TRUNCATE toàn bộ bảng nghiệp vụ ngay sau các
 * test đó (xem cleanupCommittedDataIfAny) thay vì trông chờ DirtiesContext.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:16-3.4")
                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("pps_education_test")
                    .withUsername("pps_app")
                    .withPassword("changeme");

    static {
        POSTGRES.start();
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private boolean committedDuringTest = false;

    /**
     * Bảng dữ liệu tham chiếu do CHÍNH Flyway migration seed bằng INSERT ... VALUES literal
     * (roles/permissions/role_permissions: V4 + rải rác mọi V*__*_permission*.sql; system_settings:
     * V14/V23/V25; skills: V37; expense_categories: V25; lead_sources: V26) — không phải dữ liệu
     * do test tạo ra. Không thể chỉ "loại khỏi câu lệnh TRUNCATE": system_settings.updated_by
     * REFERENCES users(id) — TRUNCATE users CASCADE vẫn kéo theo system_settings dù không nêu tên
     * nó, vì CASCADE tự tìm MỌI bảng có FK trỏ tới bảng đang truncate, bất kể có liệt kê hay không
     * (phát hiện thực tế 2026-08-05: seed bị xóa sạch → toàn bộ test phía sau lỗi "Thiếu cấu hình
     * system_settings"). Giải pháp: chụp snapshot đúng 1 lần trước khi TRUNCATE lần đầu, phục hồi
     * lại bằng INSERT nếu bảng bị cascade xóa mất. (Các bảng khác có vẻ "seed" như
     * exams/exam_class_assignments/question_banks/review_video_question_submissions/
     * user_permission_overrides thực chất là INSERT...SELECT backfill từ 1 bảng khác — rỗng trên
     * DB test mới tinh, an toàn để TRUNCATE, không cần snapshot.)
     */
    private static final Set<String> SEED_REFERENCE_TABLES = Set.of(
            "roles", "permissions", "role_permissions", "system_settings", "skills",
            "expense_categories", "lead_sources");

    /** Snapshot 1 lần duy nhất cho cả JVM (static) — key = tên bảng, value = list các row (column -> value). */
    private static final Map<String, List<Map<String, Object>>> SEED_SNAPSHOT = new ConcurrentHashMap<>();

    /**
     * V71 workaround: commit dữ liệu hiện tại (FK parental được tạo ở setUp sẽ commit),
     * rồi tiếp tục giao dịch mới cho phần còn lại của test. Dùng trước khi gọi code
     * sử dụng PROPAGATION_REQUIRES_NEW (deliverToClass, ...) — giao dịch lồng sẽ thấy
     * được dữ liệu đã commit từ setupFixture nên không bị FK constraint fail.
     *
     * Phần commit này KHÔNG tự rollback được nữa (đã ra khỏi transaction ban đầu) — đánh
     * dấu committedDuringTest để cleanupCommittedDataIfAny() dọn lại sau khi test xong,
     * tránh rò rỉ sang test class khác dùng chung container (xem Javadoc lớp).
     */
    protected void commitCurrentTransactionAndStartNew() {
        if (TestTransaction.isActive()) {
            TestTransaction.flagForCommit();
            TestTransaction.end();
            committedDuringTest = true;
            TestTransaction.start();
        }
    }

    /**
     * Chạy khi test này đã thật sự COMMIT dữ liệu — qua commitCurrentTransactionAndStartNew
     * (cờ committedDuringTest), HOẶC vì bản thân test chạy propagation NOT_SUPPORTED/không có
     * giao dịch test nào bao ngoài (TestTransaction.isActive()=false ngay từ đầu @AfterEach —
     * phát hiện thực tế 2026-08-06: ExerciseAuthoringTest.deliverToClass_V70_boSung_... dùng
     * @Transactional(NOT_SUPPORTED) để REQUIRES_NEW nhìn thấy fixture, khiến MỌI câu lệnh của
     * test đó auto-commit thẳng vào container dùng chung, nhưng committedDuringTest vẫn false
     * vì test không gọi commitCurrentTransactionAndStartNew() — dọn dẹp bị bỏ qua, rò rỉ sang
     * mọi test class chạy sau, gây duplicate key ở uq_exercise_assignments_active_target và
     * uq_review_video_assignments_active_target). Đa số test @Transactional bình thường (không
     * rơi vào 1 trong 2 trường hợp trên) rollback mặc định là đủ, không cần TRUNCATE (tốn thời
     * gian) sau MỌI test. Phải end() giao dịch còn dở (nếu có) TRƯỚC khi TRUNCATE — TRUNCATE
     * trong PostgreSQL vẫn transactional, nếu chạy trong giao dịch mà Spring rollback sau đó thì
     * lệnh dọn dẹp bị rollback theo, coi như không làm gì.
     */
    @AfterEach
    void cleanupCommittedDataIfAny() {
        if (!committedDuringTest && TestTransaction.isActive()) {
            return;
        }
        committedDuringTest = false;
        if (TestTransaction.isActive()) {
            TestTransaction.end();
        }
        captureSeedSnapshotOnce();
        // spatial_ref_sys (bảng hệ thống PostGIS chứa định nghĩa SRID, VD 4326 cho GPS UC-09) bị
        // liệt kê vào đây vì nó cũng nằm trong schema "public" như mọi bảng nghiệp vụ — TRUNCATE
        // CASCADE xóa sạch, làm mọi test PostGIS chạy SAU trên cùng container dùng chung lỗi
        // "Cannot find SRID (4326) in spatial_ref_sys" (phát hiện 2026-08-08, không phải lỗi image
        // postgis/postgis:16-3.4 thiếu SRID — image luôn có sẵn, đã verify). KHÔNG dùng
        // SEED_REFERENCE_TABLES (danh sách đó có cơ chế restore-from-snapshot riêng cho dữ liệu
        // nghiệp vụ do Flyway seed) — spatial_ref_sys là dữ liệu hệ thống, không bao giờ được đụng tới.
        List<String> tables = jdbcTemplate.queryForList(
                "select tablename from pg_tables where schemaname = 'public' and tablename not in ('flyway_schema_history', 'spatial_ref_sys')",
                String.class);
        tables.removeAll(SEED_REFERENCE_TABLES);
        if (!tables.isEmpty()) {
            jdbcTemplate.execute("TRUNCATE TABLE " + String.join(", ", tables) + " CASCADE");
        }
        restoreSeedTablesWipedByCascade();
    }

    /**
     * Chụp toàn bộ nội dung SEED_REFERENCE_TABLES đúng 1 lần cho cả JVM — thời điểm gọi lần đầu
     * (test đầu tiên dùng commitCurrentTransactionAndStartNew) đảm bảo seed vẫn nguyên vẹn, vì mọi
     * test @Transactional bình thường trước đó tự rollback, không đụng gì tới các bảng này.
     */
    private void captureSeedSnapshotOnce() {
        if (!SEED_SNAPSHOT.isEmpty()) {
            return;
        }
        synchronized (SEED_SNAPSHOT) {
            if (!SEED_SNAPSHOT.isEmpty()) {
                return;
            }
            for (String table : SEED_REFERENCE_TABLES) {
                SEED_SNAPSHOT.put(table, jdbcTemplate.queryForList("select * from " + table));
            }
        }
    }

    /**
     * TRUNCATE ... CASCADE ở trên có thể kéo theo SEED_REFERENCE_TABLES dù không nêu tên (VD
     * system_settings.updated_by REFERENCES users(id) — xem Javadoc SEED_REFERENCE_TABLES) — kiểm
     * tra lại, bảng nào rỗng bất thường thì phục hồi từ snapshot. Không cần đồng bộ lại sequence:
     * các bảng này chỉ do Flyway ghi (roles/permissions/system_settings/skills/... không có Service
     * nào trong code nghiệp vụ tự thêm dòng mới), nên không có INSERT nào sau đó cần nextval() mới.
     */
    private void restoreSeedTablesWipedByCascade() {
        for (String table : SEED_REFERENCE_TABLES) {
            List<Map<String, Object>> snapshotRows = SEED_SNAPSHOT.get(table);
            if (snapshotRows == null || snapshotRows.isEmpty()) {
                continue;
            }
            Long currentCount = jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
            if (currentCount != null && currentCount > 0) {
                continue;
            }
            for (Map<String, Object> row : snapshotRows) {
                String columns = String.join(", ", row.keySet());
                String placeholders = row.keySet().stream().map(k -> "?").collect(Collectors.joining(", "));
                jdbcTemplate.update("INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")", row.values().toArray());
            }
        }
    }
}
