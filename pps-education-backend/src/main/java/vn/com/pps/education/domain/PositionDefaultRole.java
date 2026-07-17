package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Bảng position_default_roles — role mặc định tự động gán khi 1 nhân sự mang chức vụ tương ứng (FR-HRM-06/UC-52). */
@Getter
@Setter
@Entity
@Table(name = "position_default_roles", uniqueConstraints = @UniqueConstraint(columnNames = {"position_id", "role_id"}))
public class PositionDefaultRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "position_id", nullable = false)
    private Position position;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;
}
