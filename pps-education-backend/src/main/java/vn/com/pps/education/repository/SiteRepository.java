package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.Site;

import java.util.Optional;

public interface SiteRepository extends JpaRepository<Site, Long> {

    boolean existsByCode(String code);

    // ST_DWithin trên geography tự tính theo mét, không cần đổi đơn vị thủ công.
    // gps_location không map qua JPA (xem Site.java) nên phải dùng native query.
    @Query(value = """
            SELECT ST_DWithin(
                s.geo_location,
                ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                :radiusMeters
            )
            FROM sites s
            WHERE s.id = :siteId
            """, nativeQuery = true)
    Boolean isWithinRadius(@Param("siteId") Long siteId, @Param("latitude") double latitude,
                            @Param("longitude") double longitude, @Param("radiusMeters") double radiusMeters);

    // geo_location không map qua JPA (xem Site.java) -- ghi qua native query,
    // cùng idiom với AttendanceRecordRepository#updateGpsLocation.
    @Modifying
    @Query(value = """
            UPDATE sites
            SET geo_location = ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
            WHERE id = :id
            """, nativeQuery = true)
    void updateGeoLocation(@Param("id") Long id, @Param("latitude") double latitude,
                            @Param("longitude") double longitude);

    @Query(value = """
            SELECT ST_Y(geo_location::geometry) AS latitude, ST_X(geo_location::geometry) AS longitude
            FROM sites WHERE id = :id
            """, nativeQuery = true)
    Optional<GeoPoint> findGeoLocation(@Param("id") Long id);

    // UC-09 bổ sung ngoài Main Flow gốc (tự nhận diện điểm chấm công theo GPS,
    // xác nhận với người dùng 2026-08-17) -- điểm trường ACTIVE gần nhất trong
    // bán kính attendance.gps_radius_meters quanh vị trí hiện tại.
    @Query(value = """
            SELECT s.id AS id, s.name AS name,
                   ST_Distance(
                       s.geo_location,
                       ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
                   ) AS distanceMeters
            FROM sites s
            WHERE s.status = 'ACTIVE'
              AND s.geo_location IS NOT NULL
              AND ST_DWithin(
                  s.geo_location,
                  ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                  :radiusMeters
              )
            ORDER BY distanceMeters ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<NearestSite> findNearestWithinRadius(@Param("latitude") double latitude,
                                                    @Param("longitude") double longitude,
                                                    @Param("radiusMeters") double radiusMeters);

    interface GeoPoint {
        Double getLatitude();
        Double getLongitude();
    }

    interface NearestSite {
        Long getId();
        String getName();
        Double getDistanceMeters();
    }
}
