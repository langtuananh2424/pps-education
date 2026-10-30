package vn.com.pps.education.system.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.com.pps.education.auth.domain.User;

import java.time.OffsetDateTime;

/**
 * Cấu hình bộ chấm phát âm PPS Hybrid v2 (V225): luôn đúng MỘT dòng (id = 1). {@code overrides} chỉ chứa các tham số khác mặc định,
 * dạng phẳng {@code {"word.ending_penalty": 2.0}}; rỗng nghĩa là dùng mặc định của đặc tả (xem {@code PpsConfig}).
 */
@Getter
@Setter
@Entity
@Table(name = "pps_scoring_config")
public class PpsScoringConfig {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id = SINGLETON_ID;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode overrides;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
