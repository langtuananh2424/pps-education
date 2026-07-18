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

    interface GeoPoint {
        Double getLatitude();
        Double getLongitude();
    }
}
