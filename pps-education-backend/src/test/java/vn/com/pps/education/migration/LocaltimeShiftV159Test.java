package vn.com.pps.education.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Chứng minh phép +8h của V159__fix_localtime_8h_shift.sql là AN TOÀN với
 * dữ liệu `TIME` do bug `hibernate.jdbc.time_zone` sinh ra (lệch −8h), kể
 * cả ràng buộc `CHECK (end_time > start_time)` của class_sessions.
 *
 * <p>Không chạy lại migration (Flyway chỉ chạy 1 lần) — test tái hiện đúng
 * phép UPDATE trên 1 bảng tạm có cùng CHECK, với các ca biên:
 * <ul>
 *   <li>Nhóm A — buổi có giờ dự kiến ≥ 08:00 → raw (−8h) nằm trong
 *       [00:00, 16:00).</li>
 *   <li>Nhóm B — buổi có giờ dự kiến &lt; 08:00 → raw (−8h) wrap lên
 *       [16:00, 24:00).</li>
 * </ul>
 * Cả 2 nhóm sau khi +8h (Postgres tự wrap mod 24h) phải quay về ĐÚNG giờ
 * dự kiến và giữ end &gt; start. Ca "chỉ end wrap" (start∈[08,16),
 * end∈[16,24)) KHÔNG THỂ tồn tại vì nó tương đương end_dự_kiến &lt;
 * start_dự_kiến — đã bị chính CHECK này chặn từ lúc insert.
 */
@Transactional
class LocaltimeShiftV159Test extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void v159_plus8h_returnsBuggyRawTimesToIntended_andKeepsCheckConstraint() {
        jdbc.execute("""
                CREATE TEMP TABLE _v159_probe (
                    label       TEXT PRIMARY KEY,
                    start_time  TIME NOT NULL,
                    end_time    TIME NOT NULL,
                    CONSTRAINT chk_probe_time CHECK (end_time > start_time)
                ) ON COMMIT DROP
                """);

        // label | raw start (−8h) | raw end (−8h)          -> giờ dự kiến
        insert("A_afternoon", "07:00", "09:00");            // 15:00–17:00
        insert("A_class_at_8am", "00:00", "01:30");         // 08:00–09:30
        insert("A_late_evening", "14:00", "15:30");         // 22:00–23:30
        insert("B_dawn", "22:00", "23:00");                 // 06:00–07:00 (raw đã wrap)
        insert("B_period1", "23:00", "23:45");              // 07:00–07:45 (Tiết 1)

        assertThatCode(() -> jdbc.update(
                "UPDATE _v159_probe SET start_time = start_time + INTERVAL '8 hours', "
                        + "end_time = end_time + INTERVAL '8 hours'"))
                .as("phép +8h không được vi phạm CHECK (end_time > start_time)")
                .doesNotThrowAnyException();

        Map<String, String[]> expected = Map.of(
                "A_afternoon", new String[]{"15:00", "17:00"},
                "A_class_at_8am", new String[]{"08:00", "09:30"},
                "A_late_evening", new String[]{"22:00", "23:30"},
                "B_dawn", new String[]{"06:00", "07:00"},
                "B_period1", new String[]{"07:00", "07:45"});

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT label, start_time, end_time FROM _v159_probe");
        assertThat(rows).hasSize(5);
        for (Map<String, Object> row : rows) {
            String label = (String) row.get("label");
            String[] want = expected.get(label);
            LocalTime start = ((java.sql.Time) row.get("start_time")).toLocalTime();
            LocalTime end = ((java.sql.Time) row.get("end_time")).toLocalTime();
            assertThat(start).as(label + " start").isEqualTo(LocalTime.parse(want[0]));
            assertThat(end).as(label + " end").isEqualTo(LocalTime.parse(want[1]));
            assertThat(end).as(label + " end > start").isAfter(start);
        }
    }

    @Test
    void v159_guardPlaceholderFalse_isNoOp() {
        jdbc.execute("CREATE TEMP TABLE _v159_guard (start_time TIME NOT NULL) ON COMMIT DROP");
        jdbc.update("INSERT INTO _v159_guard VALUES (TIME '07:00')");

        // Tái hiện đúng dạng câu lệnh trong V159 khi placeholder = 'false'
        int affected = jdbc.update(
                "UPDATE _v159_guard SET start_time = start_time + INTERVAL '8 hours' "
                        + "WHERE lower('false') = 'true'");

        assertThat(affected).as("guard 'false' -> WHERE false -> 0 dòng").isZero();
        assertThat(jdbc.queryForObject("SELECT start_time FROM _v159_guard", java.sql.Time.class).toLocalTime())
                .isEqualTo(LocalTime.of(7, 0));
    }

    private void insert(String label, String rawStart, String rawEnd) {
        jdbc.update("INSERT INTO _v159_probe (label, start_time, end_time) VALUES (?, ?::time, ?::time)",
                label, rawStart, rawEnd);
    }
}
