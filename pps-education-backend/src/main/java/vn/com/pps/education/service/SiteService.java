package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.PartnerSchoolInfo;
import vn.com.pps.education.domain.PartnerSchoolInfoHistory;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteHistory;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AssignSiteManagerRequest;
import vn.com.pps.education.dto.CreateSiteRequest;
import vn.com.pps.education.dto.PartnerSchoolInfoRequest;
import vn.com.pps.education.dto.PartnerSchoolInfoResponse;
import vn.com.pps.education.dto.SiteResponse;
import vn.com.pps.education.dto.UpdateSiteRequest;
import vn.com.pps.education.exception.DuplicateSiteCodeException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.PartnerSchoolInfoHistoryRepository;
import vn.com.pps.education.repository.PartnerSchoolInfoRepository;
import vn.com.pps.education.repository.SiteHistoryRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
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
 */
@Service
public class SiteService {

    private final SiteRepository siteRepository;
    private final PartnerSchoolInfoRepository partnerSchoolInfoRepository;
    private final PartnerSchoolInfoHistoryRepository partnerSchoolInfoHistoryRepository;
    private final SiteManagerRepository siteManagerRepository;
    private final SiteHistoryRepository siteHistoryRepository;
    private final UserRepository userRepository;

    public SiteService(SiteRepository siteRepository,
                        PartnerSchoolInfoRepository partnerSchoolInfoRepository,
                        PartnerSchoolInfoHistoryRepository partnerSchoolInfoHistoryRepository,
                        SiteManagerRepository siteManagerRepository,
                        SiteHistoryRepository siteHistoryRepository,
                        UserRepository userRepository) {
        this.siteRepository = siteRepository;
        this.partnerSchoolInfoRepository = partnerSchoolInfoRepository;
        this.partnerSchoolInfoHistoryRepository = partnerSchoolInfoHistoryRepository;
        this.siteManagerRepository = siteManagerRepository;
        this.siteHistoryRepository = siteHistoryRepository;
        this.userRepository = userRepository;
    }

    /** Main Flow bước 1-5: tạo điểm trường mới, tùy chọn gán luôn Quản lý điểm trường (bước 4). */
    @Transactional
    public SiteResponse createSite(CreateSiteRequest request, Long actorUserId) {
        if (siteRepository.existsByCode(request.code())) {
            throw new DuplicateSiteCodeException("Mã điểm trường đã tồn tại: " + request.code());
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
        User actor = getUserOrThrow(actorUserId);

        if (siteType == Site.SiteType.PARTNER) {
            if (request.partnerInfo() != null) {
                savePartnerInfo(site, request.partnerInfo(), actor);
            }
        } else {
            partnerSchoolInfoRepository.findBySiteId(site.getId()).ifPresent(partnerSchoolInfoRepository::delete);
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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm trường id=" + id));
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + id));
    }

    private SiteResponse toResponse(Site site) {
        PartnerSchoolInfoResponse partnerInfo = partnerSchoolInfoRepository.findBySiteId(site.getId())
                .map(info -> new PartnerSchoolInfoResponse(info.getContactPersonName(), info.getContactPersonTitle(),
                        info.getContactPhone(), info.getContactEmail(), info.getAdditionalInfo()))
                .orElse(null);

        Optional<SiteManager> currentManager = siteManagerRepository
                .findBySiteIdAndRoleTypeAndAssignedToIsNull(site.getId(), SiteManager.RoleType.SITE_MANAGER)
                .stream().findFirst();

        return new SiteResponse(site.getId(), site.getCode(), site.getName(), site.getSiteType().name(),
                site.getAddress(), site.getDistrict(), site.getPhone(), site.getStatus().name(), partnerInfo,
                currentManager.map(sm -> sm.getUser().getId()).orElse(null),
                currentManager.map(sm -> sm.getUser().getFullName()).orElse(null));
    }
}
