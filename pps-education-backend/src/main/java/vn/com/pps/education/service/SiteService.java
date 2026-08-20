package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.PartnerSchoolInfo;
import vn.com.pps.education.domain.PartnerSchoolInfoHistory;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteHistory;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.SiteTeacher;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AssignSiteManagerRequest;
import vn.com.pps.education.dto.AssignSiteTeacherRequest;
import vn.com.pps.education.dto.CreateSiteRequest;
import vn.com.pps.education.dto.PartnerSchoolInfoRequest;
import vn.com.pps.education.dto.PartnerSchoolInfoResponse;
import vn.com.pps.education.dto.SiteResponse;
import vn.com.pps.education.dto.SiteTeacherResponse;
import vn.com.pps.education.dto.UpdateSiteRequest;
import vn.com.pps.education.exception.DuplicateSiteCodeException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.SiteTeacherAlreadyAssignedException;
import vn.com.pps.education.repository.PartnerSchoolInfoHistoryRepository;
import vn.com.pps.education.repository.PartnerSchoolInfoRepository;
import vn.com.pps.education.repository.SiteHistoryRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.SiteTeacherRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * UC-36: Quản lý điểm trường (FR-FAC-01). Xem
 * docs/uc/phan-he-10-co-so-vat-chat.md.
 *
 * "Trạng thái hợp đồng hợp tác" (Main Flow bước 3) thuộc UC-36b — ngoài
 * phạm vi Service này, chỉ chạm partner_school_info (thông tin liên hệ).
 *
 * latitude/longitude (Main Flow bước 2, bổ sung — xác nhận 2026-07-17): trước
 * đây sites.geo_location (SDD Nhóm 3, cột phục vụ UC-09 A2 xác thực bán kính
 * GPS) không có đường ghi nào qua API, chỉ set được bằng SQL thủ công/test —
 * mọi điểm trường tạo qua API thật sẽ luôn NULL, khiến chấm công GPS luôn bị
 * từ chối. Nay cho phép set/cập nhật qua createSite/updateSite.
 */
@Service
public class SiteService {

    private final SiteRepository siteRepository;
    private final PartnerSchoolInfoRepository partnerSchoolInfoRepository;
    private final PartnerSchoolInfoHistoryRepository partnerSchoolInfoHistoryRepository;
    private final SiteManagerRepository siteManagerRepository;
    private final SiteTeacherRepository siteTeacherRepository;
    private final SiteHistoryRepository siteHistoryRepository;
    private final UserRepository userRepository;

    public SiteService(SiteRepository siteRepository,
                        PartnerSchoolInfoRepository partnerSchoolInfoRepository,
                        PartnerSchoolInfoHistoryRepository partnerSchoolInfoHistoryRepository,
                        SiteManagerRepository siteManagerRepository,
                        SiteTeacherRepository siteTeacherRepository,
                        SiteHistoryRepository siteHistoryRepository,
                        UserRepository userRepository) {
        this.siteRepository = siteRepository;
        this.partnerSchoolInfoRepository = partnerSchoolInfoRepository;
        this.partnerSchoolInfoHistoryRepository = partnerSchoolInfoHistoryRepository;
        this.siteManagerRepository = siteManagerRepository;
        this.siteTeacherRepository = siteTeacherRepository;
        this.siteHistoryRepository = siteHistoryRepository;
        this.userRepository = userRepository;
    }

    /** Main Flow bước 1-5: tạo điểm trường mới, tùy chọn gán luôn Quản lý điểm trường (bước 4). */
    @Transactional
    public SiteResponse createSite(CreateSiteRequest request, Long actorUserId) {
        if (siteRepository.existsByCode(request.code())) {
            throw new DuplicateSiteCodeException("error.duplicateSiteCode.default", new Object[]{request.code()},
                    "Mã điểm trường đã tồn tại: " + request.code());
        }
        Site.SiteType siteType = parseSiteType(request.siteType());
        requirePartnerInfoOnlyForPartner(siteType, request.partnerInfo());

        Site site = new Site();
        site.setCode(request.code());
        site.setName(request.name());
        site.setSiteType(siteType);
        site.setAddress(request.address());
        site.setDistrict(request.district());
        site.setPhone(request.phone());
        site = siteRepository.save(site);
        updateGeoLocationIfPresent(site, request.latitude(), request.longitude());
        User actor = getUserOrThrow(actorUserId);

        if (siteType == Site.SiteType.PARTNER && request.partnerInfo() != null) {
            savePartnerInfo(site, request.partnerInfo(), actor);
        }

        writeSiteHistory(site, actor, SiteHistory.Action.CREATED, Map.of(
                "code", site.getCode(), "name", site.getName(), "siteType", site.getSiteType().name()));

        if (request.managerUserId() != null) {
            assignManagerInternal(site, request.managerUserId(), actor);
        }

        return toResponse(site);
    }

    /** Main Flow bước 1-3 (chỉnh sửa điểm trường hiện có). */
    @Transactional
    public SiteResponse updateSite(Long siteId, UpdateSiteRequest request, Long actorUserId) {
        Site site = getSiteOrThrow(siteId);
        Site.SiteType siteType = parseSiteType(request.siteType());
        requirePartnerInfoOnlyForPartner(siteType, request.partnerInfo());

        site.setName(request.name());
        site.setSiteType(siteType);
        site.setAddress(request.address());
        site.setDistrict(request.district());
        site.setPhone(request.phone());
        if (request.status() != null) {
            site.setStatus(parseStatus(request.status()));
        }
        site = siteRepository.save(site);
        updateGeoLocationIfPresent(site, request.latitude(), request.longitude());
        User actor = getUserOrThrow(actorUserId);

        if (siteType == Site.SiteType.PARTNER) {
            if (request.partnerInfo() != null) {
                savePartnerInfo(site, request.partnerInfo(), actor);
            }
        } else {
            partnerSchoolInfoRepository.findBySiteId(site.getId()).ifPresent(info -> {
                // Xóa history trước - FK partner_school_info_history -> partner_school_info
                // chặn xóa cha nếu còn history con tham chiếu (V27, thêm sau khi bảng này
                // đã có sẵn hành vi hard-delete từ UC-36 gốc).
                partnerSchoolInfoHistoryRepository.deleteByPartnerSchoolInfoId(info.getId());
                partnerSchoolInfoRepository.delete(info);
            });
        }

        writeSiteHistory(site, actor, SiteHistory.Action.UPDATED, Map.of(
                "name", site.getName(), "siteType", site.getSiteType().name(), "status", site.getStatus().name()));

        return toResponse(site);
    }

    /** A1 — Đổi Quản lý điểm trường phụ trách. */
    @Transactional
    public SiteResponse assignManager(Long siteId, AssignSiteManagerRequest request, Long actorUserId) {
        Site site = getSiteOrThrow(siteId);
        User actor = getUserOrThrow(actorUserId);
        assignManagerInternal(site, request.managerUserId(), actor);
        return toResponse(site);
    }

    @Transactional(readOnly = true)
    public SiteResponse getSite(Long siteId) {
        return toResponse(getSiteOrThrow(siteId));
    }

    @Transactional(readOnly = true)
    public List<SiteResponse> listSites() {
        return siteRepository.findAll().stream().map(this::toResponse).toList();
    }

    /**
     * Gán giáo viên (nhân viên) vào điểm trường — quan hệ N-N, không giới hạn
     * số lượng như site_managers (khác migration V35 comment). Bổ sung ngoài
     * SDD gốc, đã xác nhận với người dùng (không có UC/bảng nào đặc tả trước
     * quan hệ này — xem docs/sdd-groups/03-co-so-vat-chat-and-diem-truong.md).
     */
    @Transactional
    public SiteTeacherResponse assignTeacher(Long siteId, AssignSiteTeacherRequest request, Long actorUserId) {
        Site site = getSiteOrThrow(siteId);
        User teacher = getUserOrThrow(request.teacherUserId());
        User actor = getUserOrThrow(actorUserId);
        if (siteTeacherRepository.existsBySiteIdAndTeacherIdAndAssignedToIsNull(site.getId(), teacher.getId())) {
            throw new SiteTeacherAlreadyAssignedException("error.siteTeacherAlreadyAssigned.default", new Object[]{},
                    "Giáo viên này đã được gán vào điểm trường này rồi.");
        }

        SiteTeacher assignment = new SiteTeacher();
        assignment.setSite(site);
        assignment.setTeacher(teacher);
        assignment.setAssignedFrom(request.assignedFrom());
        assignment.setAssignedBy(actor);
        assignment.setNotes(request.notes());
        assignment = siteTeacherRepository.save(assignment);

        writeSiteHistory(site, actor, SiteHistory.Action.TEACHER_ASSIGNED,
                Map.of("teacherUserId", teacher.getId()));
        return toResponse(assignment);
    }

    /** Kết thúc phân công (đóng assigned_to), giữ lịch sử — không xóa cứng (giống site_managers). */
    @Transactional
    public void removeTeacherFromSite(Long siteId, Long siteTeacherId, Long actorUserId) {
        Site site = getSiteOrThrow(siteId);
        SiteTeacher assignment = siteTeacherRepository.findById(siteTeacherId)
                .filter(st -> st.getSite().getId().equals(siteId))
                .orElseThrow(() -> new ResourceNotFoundException("error.site.teacherAssignmentNotFound", new Object[]{siteTeacherId}, "Không tìm thấy phân công giáo viên id=" + siteTeacherId));
        User actor = getUserOrThrow(actorUserId);

        assignment.setAssignedTo(LocalDate.now());
        siteTeacherRepository.save(assignment);

        writeSiteHistory(site, actor, SiteHistory.Action.TEACHER_REMOVED,
                Map.of("teacherUserId", assignment.getTeacher().getId()));
    }

    @Transactional(readOnly = true)
    public List<SiteTeacherResponse> listTeachers(Long siteId) {
        getSiteOrThrow(siteId);
        return siteTeacherRepository.findBySiteIdAndAssignedToIsNull(siteId).stream().map(this::toResponse).toList();
    }

    // ===================== Helpers =====================

    /** Đóng phân công cũ (nếu có) và mở phân công mới — site_managers là nguồn chân lý cho row-level scope, cập nhật ngay khi lưu. */
    private void assignManagerInternal(Site site, Long newManagerUserId, User actor) {
        User newManager = getUserOrThrow(newManagerUserId);
        Optional<SiteManager> current = siteManagerRepository
                .findBySiteIdAndRoleTypeAndAssignedToIsNull(site.getId(), SiteManager.RoleType.SITE_MANAGER)
                .stream().findFirst();

        Long previousManagerId = current.map(sm -> sm.getUser().getId()).orElse(null);
        current.ifPresent(sm -> {
            sm.setAssignedTo(LocalDate.now());
            // saveAndFlush bắt buộc: nếu chỉ save(), Hibernate flush INSERT bản ghi mới
            // TRƯỚC UPDATE bản cũ (thứ tự flush mặc định), vi phạm tạm thời
            // idx_site_managers_active (site_id, role_type) WHERE assigned_to IS NULL —
            // lỗi thật gặp ở UC-41 (student_answer_grading), lặp lại pattern đó ở đây.
            siteManagerRepository.saveAndFlush(sm);
        });

        SiteManager assignment = new SiteManager();
        assignment.setSite(site);
        assignment.setUser(newManager);
        assignment.setRoleType(SiteManager.RoleType.SITE_MANAGER);
        assignment.setAssignedFrom(LocalDate.now());
        assignment.setAssignedBy(actor);
        siteManagerRepository.save(assignment);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("previousManagerUserId", previousManagerId);
        details.put("newManagerUserId", newManager.getId());
        writeSiteHistory(site, actor, SiteHistory.Action.MANAGER_CHANGED, details);
    }

    private void savePartnerInfo(Site site, PartnerSchoolInfoRequest request, User actor) {
        boolean isNew = partnerSchoolInfoRepository.findBySiteId(site.getId()).isEmpty();
        PartnerSchoolInfo info = partnerSchoolInfoRepository.findBySiteId(site.getId()).orElseGet(() -> {
            PartnerSchoolInfo created = new PartnerSchoolInfo();
            created.setSite(site);
            return created;
        });
        info.setContactPersonName(request.contactPersonName());
        info.setContactPersonTitle(request.contactPersonTitle());
        info.setContactPhone(request.contactPhone());
        info.setContactEmail(request.contactEmail());
        info.setAdditionalInfo(request.additionalInfo());
        info = partnerSchoolInfoRepository.save(info);

        PartnerSchoolInfoHistory history = new PartnerSchoolInfoHistory();
        history.setPartnerSchoolInfo(info);
        history.setChangedBy(actor);
        history.setAction(isNew ? PartnerSchoolInfoHistory.Action.CREATED : PartnerSchoolInfoHistory.Action.UPDATED);
        history.setDetails(Map.of("contactPersonName", String.valueOf(info.getContactPersonName())));
        partnerSchoolInfoHistoryRepository.save(history);
    }

    /** latitude/longitude tùy chọn -- chỉ ghi geo_location khi cả 2 cùng có giá trị, giữ nguyên nếu không truyền. */
    private void updateGeoLocationIfPresent(Site site, Double latitude, Double longitude) {
        if (latitude != null && longitude != null) {
            siteRepository.updateGeoLocation(site.getId(), latitude, longitude);
        }
    }

    private void requirePartnerInfoOnlyForPartner(Site.SiteType siteType, PartnerSchoolInfoRequest partnerInfo) {
        if (siteType == Site.SiteType.OWNED && partnerInfo != null) {
            throw new IllegalArgumentException("Điểm trường Loại hình Cơ sở tự vận hành (OWNED) không có thông tin liên hệ trường liên kết.");
        }
    }

    private Site.SiteType parseSiteType(String value) {
        try {
            return Site.SiteType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Loại hình điểm trường không hợp lệ: " + value);
        }
    }

    private Site.Status parseStatus(String value) {
        try {
            return Site.Status.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Trạng thái điểm trường không hợp lệ: " + value);
        }
    }

    private void writeSiteHistory(Site site, User actor, SiteHistory.Action action, Map<String, Object> details) {
        SiteHistory history = new SiteHistory();
        history.setSite(site);
        history.setChangedBy(actor);
        history.setAction(action);
        history.setDetails(details);
        siteHistoryRepository.save(history);
    }

    private Site getSiteOrThrow(Long id) {
        return siteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.site.notFoundById", new Object[]{id}, "Không tìm thấy điểm trường id=" + id));
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.site.accountNotFound", new Object[]{id}, "Không tìm thấy tài khoản id=" + id));
    }

    private SiteResponse toResponse(Site site) {
        PartnerSchoolInfoResponse partnerInfo = partnerSchoolInfoRepository.findBySiteId(site.getId())
                .map(info -> new PartnerSchoolInfoResponse(info.getContactPersonName(), info.getContactPersonTitle(),
                        info.getContactPhone(), info.getContactEmail(), info.getAdditionalInfo()))
                .orElse(null);

        Optional<SiteManager> currentManager = siteManagerRepository
                .findBySiteIdAndRoleTypeAndAssignedToIsNull(site.getId(), SiteManager.RoleType.SITE_MANAGER)
                .stream().findFirst();

        SiteRepository.GeoPoint geoPoint = siteRepository.findGeoLocation(site.getId()).orElse(null);

        return new SiteResponse(site.getId(), site.getCode(), site.getName(), site.getSiteType().name(),
                site.getAddress(), site.getDistrict(), site.getPhone(), site.getStatus().name(), partnerInfo,
                currentManager.map(sm -> sm.getUser().getId()).orElse(null),
                currentManager.map(sm -> sm.getUser().getFullName()).orElse(null),
                geoPoint == null ? null : geoPoint.getLatitude(),
                geoPoint == null ? null : geoPoint.getLongitude());
    }

    private SiteTeacherResponse toResponse(SiteTeacher st) {
        return new SiteTeacherResponse(st.getId(), st.getSite().getId(), st.getTeacher().getId(),
                st.getTeacher().getFullName(), st.getAssignedFrom(), st.getAssignedTo(), st.getNotes());
    }
}
