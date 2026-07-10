package vn.com.pps.education.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

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
}
