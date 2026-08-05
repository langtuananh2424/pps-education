package vn.com.pps.education.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.transaction.TestTransaction;
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
 *
 * V71 (2026-08-03) thêm PROPAGATION_REQUIRES_NEW vào deliverToClass để chặn race
 * condition tạo trùng bản giao. Giao dịch lồng dùng connection riêng → không thấy
 * dữ liệu chưa commit từ test @Transactional → FK fail. Helper commitCurrentTransaction()
 * dùng TestTransaction để commit giữa chừng (sau setUp, trước code gọi deliverToClass).
 *
 * V90+ (2026-08-05) thêm @DirtiesContext(classMode=AFTER_CLASS) để fix test data
 * carryover khi singleton container chia sẻ giữa test classes — được committed từ
 * test trước lọt sang test sau, gây duplicate key violations. Spring reload
 * application context sau mỗi test class → fresh DB state.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
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

    /**
     * V71 workaround: commit dữ liệu hiện tại (FK parental được tạo ở setUp sẽ commit),
     * rồi tiếp tục giao dịch mới cho phần còn lại của test. Dùng trước khi gọi code
     * sử dụng PROPAGATION_REQUIRES_NEW (deliverToClass, ...) — giao dịch lồng sẽ thấy
     * được dữ liệu đã commit từ setupFixture nên không bị FK constraint fail.
     *
     * Tại cuối test, Spring tự rollback giao dịch cuối cùng (mặc định @Transactional),
     * nên không có dữ liệu thực bị lưu vào DB test (như bình thường).
     */
    protected void commitCurrentTransactionAndStartNew() {
        if (TestTransaction.isActive()) {
            TestTransaction.flagForCommit();
            TestTransaction.end();
            TestTransaction.start();
        }
    }
}
