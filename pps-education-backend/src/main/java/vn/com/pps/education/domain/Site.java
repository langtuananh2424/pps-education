package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.util.UUID;

/**
 * Bảng sites (SDD > Cơ sở vật chất > a, migration V2). Entity tối thiểu —
 * chỉ đủ cho quan hệ FK từ attendance_records (UC-09). Chưa có CRUD/UC nào
 * được giao cho Phân hệ 10 (Cơ sở vật chất) trong phạm vi hiện tại, nên
 * không thêm Controller/Service ở đây. Cột geo_location (PostGIS
 * GEOGRAPHY) không map qua JPA để tránh thêm dependency hibernate-spatial —
 * kiểm tra bán kính GPS thực hiện bằng native query trong
 * SiteRepository (xem AttendanceMethodValidator GPS).
 */
@Getter
@Setter
@Entity
@Table(name = "sites")
public class Site extends BaseAuditEntity {

    public enum SiteType { OWNED, PARTNER }

    public enum Status { ACTIVE, INACTIVE, PENDING }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "site_type", nullable = false, length = 20)
    private SiteType siteType;

    @Column(length = 500)
    private String address;

    @Column(length = 100)
    private String district;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;
}
