package vn.com.pps.education.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base cho integration test chạm DB thật qua Testcontainers (postgis/postgis:16-3.4,
 * khớp image dùng ở backend-ci.yml) — không dùng H2 (xem .claude/rules/testing.md).
 * Flyway chạy migration thật trên container khi Spring context khởi động.
 */
@SpringBootTest
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:16-3.4")
                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("pps_education_test")
                    .withUsername("pps_app")
                    .withPassword("changeme");
}
