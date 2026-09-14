package vn.com.pps.education.dto;

import java.util.List;
import java.util.Map;

/**
 * UC-73: Import Excel hàng loạt "bộ" video ôn tập (review_video_sets) +
 * video (review_videos) vào Kho Video Ôn tập. Xem
 * docs/uc/phan-he-07-lms-portal.md (UC-73).
 */
public record ReviewVideoCatalogImportResponse(
        Long id,
        String sourceFileName,
        Integer totalRows,
        int successRows,
        int failedRows,
        String status,
        List<Map<String, Object>> errorSummary
) {}
