package vn.com.pps.education.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.ChangeHistoryItemResponse;
import vn.com.pps.education.repository.ClassEnrollmentHistoryRepository;
import vn.com.pps.education.repository.ClassHistoryRepository;
import vn.com.pps.education.repository.ClassSessionHistoryRepository;
import vn.com.pps.education.repository.ClassTeacherHistoryRepository;
import vn.com.pps.education.repository.EmployeeHistoryRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentHistoryRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Trang "Lịch sử thay đổi dữ liệu" của Trưởng phòng đào tạo (V203 — bổ sung ngoài SDD gốc, xác
 * nhận với người dùng 2026-09-30): gộp lịch sử lớp, buổi học (lịch học), ghi danh, giáo viên phụ
 * trách lớp, học sinh và hồ sơ giáo viên thành 1 dòng thời gian — chỉ ĐỌC các bảng *_history mà
 * ClassService/ClassSessionService/StudentService/EmployeeService đã ghi sẵn, không ghi thêm gì.
 *
 * Phạm vi dữ liệu theo DataScopeService#resolveAllowedSiteIds: phạm vi hẹp hơn ALL chỉ thấy lớp/học
 * sinh thuộc điểm trường được gán và không thấy lịch sử nhân sự (nhân sự không gắn điểm trường).
 * Không có hrm.employee.view thì chỉ thấy nhân sự là giáo viên và bị ẩn số CCCD trong snapshot.
 */
@Service
public class ChangeHistoryService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> ENTITY_TYPES =
            Set.of("CLASS", "CLASS_SESSION", "CLASS_ENROLLMENT", "CLASS_TEACHER", "STUDENT", "EMPLOYEE");
    private static final int MAX_PAGE_SIZE = 100;
    /** Snapshot nhân sự chứa số CCCD — chỉ người có quyền xem toàn bộ hồ sơ nhân sự mới thấy. */
    private static final Set<String> RESTRICTED_EMPLOYEE_KEYS = Set.of("idCardNumber");
    /** Trường trong snapshot là khoá tới users — FE cần tên để hiển thị. */
    private static final Set<String> USER_REFERENCE_KEYS = Set.of("teacherUserId", "primaryTeacherId");
    private static final String SITE_REFERENCE_KEY = "primarySiteId";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ClassHistoryRepository classHistoryRepository;
    private final ClassSessionHistoryRepository classSessionHistoryRepository;
    private final ClassEnrollmentHistoryRepository classEnrollmentHistoryRepository;
    private final ClassTeacherHistoryRepository classTeacherHistoryRepository;
    private final StudentHistoryRepository studentHistoryRepository;
    private final EmployeeHistoryRepository employeeHistoryRepository;
    private final UserRepository userRepository;
    private final SiteRepository siteRepository;
    private final DataScopeService dataScopeService;
    private final PermissionEvaluationService permissionEvaluationService;
    private final ObjectMapper objectMapper;

    public ChangeHistoryService(ClassHistoryRepository classHistoryRepository,
                                ClassSessionHistoryRepository classSessionHistoryRepository,
                                ClassEnrollmentHistoryRepository classEnrollmentHistoryRepository,
                                ClassTeacherHistoryRepository classTeacherHistoryRepository,
                                StudentHistoryRepository studentHistoryRepository,
                                EmployeeHistoryRepository employeeHistoryRepository,
                                UserRepository userRepository,
                                SiteRepository siteRepository,
                                DataScopeService dataScopeService,
                                PermissionEvaluationService permissionEvaluationService,
                                ObjectMapper objectMapper) {
        this.classHistoryRepository = classHistoryRepository;
        this.classSessionHistoryRepository = classSessionHistoryRepository;
        this.classEnrollmentHistoryRepository = classEnrollmentHistoryRepository;
        this.classTeacherHistoryRepository = classTeacherHistoryRepository;
        this.studentHistoryRepository = studentHistoryRepository;
        this.employeeHistoryRepository = employeeHistoryRepository;
        this.userRepository = userRepository;
        this.siteRepository = siteRepository;
        this.dataScopeService = dataScopeService;
        this.permissionEvaluationService = permissionEvaluationService;
        this.objectMapper = objectMapper;
    }

    /**
     * Tìm lịch sử thay đổi, mới nhất trước. Mọi tham số lọc đều tuỳ chọn; fromDate/toDate tính theo
     * ngày giờ Việt Nam, toDate tính trọn ngày. keyword khớp tên/mã lớp, tên/mã học sinh hoặc giáo
     * viên, tên người thay đổi.
     */
    @Transactional(readOnly = true)
    public Page<ChangeHistoryItemResponse> search(String entityType, LocalDate fromDate, LocalDate toDate,
                                                  Long siteId, Long classId, Long studentId, String keyword,
                                                  int page, int size, Long actorUserId) {
        String normalizedType = entityType == null || entityType.isBlank() ? "" : entityType.trim().toUpperCase(Locale.ROOT);
        if (!normalizedType.isEmpty() && !ENTITY_TYPES.contains(normalizedType)) {
            throw new IllegalArgumentException("Loại dữ liệu không hợp lệ: " + entityType);
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("Từ ngày phải trước hoặc bằng Đến ngày.");
        }

        List<Long> allowedSiteIds = dataScopeService.resolveAllowedSiteIds(actorUserId);
        boolean restrictSites = allowedSiteIds != null;
        List<Long> siteIdsForQuery = allowedSiteIds == null || allowedSiteIds.isEmpty() ? List.of(-1L) : allowedSiteIds;
        boolean canViewAllEmployees = permissionEvaluationService.hasPermission(actorUserId, "hrm.employee.view");

        OffsetDateTime fromTs = (fromDate != null ? fromDate : LocalDate.of(2000, 1, 1))
                .atStartOfDay(APP_ZONE).toOffsetDateTime();
        OffsetDateTime toTs = (toDate != null ? toDate.plusDays(1) : LocalDate.of(9999, 1, 1))
                .atStartOfDay(APP_ZONE).toOffsetDateTime();
        String keywordPattern = keyword == null || keyword.isBlank()
                ? "" : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<ClassHistoryRepository.ChangeHistoryRow> rows = classHistoryRepository.searchChangeHistory(
                normalizedType, fromTs, toTs,
                siteId == null ? 0L : siteId,
                classId == null ? 0L : classId,
                studentId == null ? 0L : studentId,
                restrictSites, siteIdsForQuery,
                !restrictSites, !canViewAllEmployees,
                keywordPattern,
                PageRequest.of(Math.max(page, 0), pageSize));

        List<RowWithSnapshots> withSnapshots = rows.getContent().stream()
                .map(row -> new RowWithSnapshots(row,
                        sanitize(row.getEntityType(), parseDetails(row.getDetails()), canViewAllEmployees),
                        sanitize(row.getEntityType(), findPreviousDetails(row), canViewAllEmployees)))
                .toList();
        Map<String, String> labels = resolveValueLabels(withSnapshots);

        List<ChangeHistoryItemResponse> content = withSnapshots.stream()
                .map(item -> toResponse(item, labels))
                .toList();
        return new PageImpl<>(content, rows.getPageable(), rows.getTotalElements());
    }

    private record RowWithSnapshots(ClassHistoryRepository.ChangeHistoryRow row,
                                    Map<String, Object> details, Map<String, Object> previousDetails) {
    }

    private Map<String, Object> findPreviousDetails(ClassHistoryRepository.ChangeHistoryRow row) {
        Long entityId = row.getEntityId();
        Long historyId = row.getHistoryId();
        return switch (row.getEntityType()) {
            case "CLASS" -> classHistoryRepository.findFirstBySchoolClassIdAndIdLessThanOrderByIdDesc(entityId, historyId)
                    .map(h -> h.getDetails()).orElse(null);
            case "CLASS_SESSION" -> classSessionHistoryRepository.findFirstByClassSessionIdAndIdLessThanOrderByIdDesc(entityId, historyId)
                    .map(h -> h.getDetails()).orElse(null);
            case "CLASS_ENROLLMENT" -> classEnrollmentHistoryRepository.findFirstByClassEnrollmentIdAndIdLessThanOrderByIdDesc(entityId, historyId)
                    .map(h -> h.getDetails()).orElse(null);
            case "CLASS_TEACHER" -> classTeacherHistoryRepository.findFirstByClassTeacherIdAndIdLessThanOrderByIdDesc(entityId, historyId)
                    .map(h -> h.getDetails()).orElse(null);
            case "STUDENT" -> studentHistoryRepository.findFirstByStudentIdAndIdLessThanOrderByIdDesc(entityId, historyId)
                    .map(h -> h.getDetails()).orElse(null);
            case "EMPLOYEE" -> employeeHistoryRepository.findFirstByEmployeeIdAndIdLessThanOrderByIdDesc(entityId, historyId)
                    .map(h -> h.getDetails()).orElse(null);
            default -> null;
        };
    }

    private Map<String, Object> parseDetails(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private Map<String, Object> sanitize(String entityType, Map<String, Object> details, boolean canViewAllEmployees) {
        if (details == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>(details);
        if ("EMPLOYEE".equals(entityType) && !canViewAllEmployees) {
            RESTRICTED_EMPLOYEE_KEYS.forEach(copy::remove);
        }
        return copy;
    }

    /** Tra tên cho các giá trị là khoá tham chiếu (giáo viên, điểm trường) trong cả snapshot hiện tại và trước đó. */
    private Map<String, String> resolveValueLabels(List<RowWithSnapshots> items) {
        Set<Long> userIds = new HashSet<>();
        Set<Long> siteIds = new HashSet<>();
        for (RowWithSnapshots item : items) {
            for (Map<String, Object> snapshot : Arrays.asList(item.details(), item.previousDetails())) {
                if (snapshot == null) {
                    continue;
                }
                USER_REFERENCE_KEYS.forEach(key -> addId(userIds, snapshot.get(key)));
                addId(siteIds, snapshot.get(SITE_REFERENCE_KEY));
            }
        }
        Map<String, String> labels = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User user : userRepository.findAllById(userIds)) {
                USER_REFERENCE_KEYS.forEach(key -> labels.put(key + ":" + user.getId(), user.getFullName()));
            }
        }
        if (!siteIds.isEmpty()) {
            for (Site site : siteRepository.findAllById(siteIds)) {
                labels.put(SITE_REFERENCE_KEY + ":" + site.getId(), site.getName());
            }
        }
        return labels;
    }

    private static void addId(Set<Long> target, Object value) {
        if (value instanceof Number number) {
            target.add(number.longValue());
        }
    }

    private ChangeHistoryItemResponse toResponse(RowWithSnapshots item, Map<String, String> allLabels) {
        ClassHistoryRepository.ChangeHistoryRow row = item.row();
        Map<String, String> itemLabels = new HashMap<>();
        for (Map<String, Object> snapshot : List.of(
                item.details() == null ? Map.<String, Object>of() : item.details(),
                item.previousDetails() == null ? Map.<String, Object>of() : item.previousDetails())) {
            snapshot.forEach((key, value) -> {
                String labelKey = key + ":" + value;
                if (value instanceof Number && allLabels.containsKey(labelKey)) {
                    itemLabels.put(labelKey, allLabels.get(labelKey));
                }
            });
        }
        return new ChangeHistoryItemResponse(
                row.getEntityType() + "-" + row.getHistoryId(),
                row.getEntityType(),
                row.getEntityId(),
                row.getAction(),
                row.getClassId(),
                row.getClassName(),
                row.getClassCode(),
                row.getStudentId(),
                row.getSubjectName(),
                row.getSubjectCode(),
                row.getSessionDate() == null ? null : LocalDate.parse(row.getSessionDate()),
                item.details(),
                item.previousDetails(),
                itemLabels,
                row.getChangedById(),
                row.getChangedByName(),
                OffsetDateTime.ofInstant(Instant.ofEpochMilli(row.getCreatedAtMillis()), APP_ZONE));
    }
}
