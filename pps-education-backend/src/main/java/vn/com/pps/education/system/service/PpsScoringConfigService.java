package vn.com.pps.education.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.auth.repository.UserRepository;
import vn.com.pps.education.common.pronunciation.PpsConfig;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.lms.service.PpsConfigHolder;
import vn.com.pps.education.system.domain.PpsScoringConfig;
import vn.com.pps.education.system.domain.PpsScoringConfigHistory;
import vn.com.pps.education.system.dto.PpsScoringConfigHistoryResponse;
import vn.com.pps.education.system.dto.PpsScoringConfigResponse;
import vn.com.pps.education.system.repository.PpsScoringConfigHistoryRepository;
import vn.com.pps.education.system.repository.PpsScoringConfigRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trang chỉnh tham số bộ chấm phát âm PPS Hybrid v2 (V225). Giá trị mặc định, khoảng cho phép và việc kiểm tra nằm ở {@link PpsConfig}
 * (cùng bảng tham số với lab); DB chỉ giữ các tham số KHÁC mặc định. Lưu xong thì {@link PpsConfigHolder} đổi ngay; bài đang chấm dở
 * vẫn dùng bản cũ (gán nguyên tử). Nhiều bản backend chạy song song thì bản kia đọc lại DB sau tối đa {@code app.pps-config.refresh-ms}.
 *
 * <p>Cấu hình hỏng trong DB (vd sau này đổi khoảng cho phép) không bao giờ làm backend không khởi động được: ghi cảnh báo và dùng mặc định.
 */
@Service
public class PpsScoringConfigService {

    private static final Logger log = LoggerFactory.getLogger(PpsScoringConfigService.class);
    private static final TypeReference<Map<String, Double>> FLAT = new TypeReference<>() {
    };

    private final PpsScoringConfigRepository repository;
    private final PpsScoringConfigHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final PpsConfigHolder holder;
    private final ObjectMapper objectMapper;

    /** Mốc cập nhật của bản đang nạp trong bộ nhớ; khác mốc trong DB thì nạp lại. Làm tròn tới mili giây vì DB lưu ít chữ số hơn Java. */
    private volatile Instant loadedStamp;

    public PpsScoringConfigService(PpsScoringConfigRepository repository, PpsScoringConfigHistoryRepository historyRepository,
                                   UserRepository userRepository, PpsConfigHolder holder, ObjectMapper objectMapper) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.holder = holder;
        this.objectMapper = objectMapper;
    }

    // ---------- nạp vào bộ chấm ----------

    @EventListener(ApplicationReadyEvent.class)
    public void loadAtStartup() {
        reload();
    }

    @Scheduled(fixedDelayString = "${app.pps-config.refresh-ms:60000}", initialDelayString = "${app.pps-config.refresh-ms:60000}")
    public void refresh() {
        reload();
    }

    /** Nạp cấu hình trong DB vào bộ chấm nếu khác bản đang dùng. Mọi lỗi chỉ ghi log để bộ chấm tiếp tục chạy bằng bản hiện có. */
    void reload() {
        try {
            PpsScoringConfig row = repository.findById(PpsScoringConfig.SINGLETON_ID).orElse(null);
            if (row == null || stamp(row.getUpdatedAt()).equals(loadedStamp)) {
                return;
            }
            holder.set(parse(row.getOverrides()));
            loadedStamp = stamp(row.getUpdatedAt());
            log.info("PpsScoringConfigService: đã nạp cấu hình chấm phát âm ({} tham số khác mặc định).", flat(row.getOverrides()).size());
        } catch (RuntimeException e) {
            log.warn("PpsScoringConfigService: không nạp được cấu hình chấm phát âm, giữ bản đang dùng. {}", e.toString());
        }
    }

    // ---------- API ----------

    @Transactional(readOnly = true)
    public PpsScoringConfigResponse get() {
        PpsScoringConfig row = row();
        return toResponse(row, parse(row.getOverrides()));
    }

    /**
     * Lưu TOÀN BỘ cấu hình mong muốn (tham số vắng mặt trở về mặc định). Sai khoảng, sai khoá hoặc trọng số không cộng bằng 1 thì
     * {@link IllegalArgumentException} (HTTP 400, kèm tên tham số sai) và không lưu gì.
     */
    @Transactional
    public PpsScoringConfigResponse save(Map<String, Object> raw, String note, Long actorUserId) {
        PpsConfig next = PpsConfig.validate(raw, PpsConfig.defaults());
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.systemSetting.accountNotFound",
                        new Object[]{actorUserId}, "Không tìm thấy tài khoản id=" + actorUserId));
        PpsScoringConfig row = row();
        Map<String, Double> before = flat(row.getOverrides());
        Map<String, Double> after = next.changed();
        if (!before.equals(after)) {
            PpsScoringConfigHistory history = new PpsScoringConfigHistory();
            history.setPrevious(objectMapper.valueToTree(before));
            history.setOverrides(objectMapper.valueToTree(after));
            history.setChangedBy(actor);
            history.setNote(note == null || note.isBlank() ? null : note.trim());
            historyRepository.save(history);

            row.setOverrides(objectMapper.valueToTree(after));
            row.setUpdatedBy(actor);
            row.setUpdatedAt(OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS));
            row = repository.save(row);
        }
        applyAfterCommit(next, row.getUpdatedAt());
        return toResponse(row, next);
    }

    /** Khôi phục toàn bộ về mặc định của đặc tả (ghi vào lịch sử như một lần lưu). */
    @Transactional
    public PpsScoringConfigResponse reset(String note, Long actorUserId) {
        return save(Map.of(), note == null || note.isBlank() ? "Khôi phục mặc định" : note, actorUserId);
    }

    /** Cấu hình đang dùng dạng lồng nhau, bọc trong {@code config}: nhập lại được bằng {@link #save}. */
    @Transactional(readOnly = true)
    public Map<String, Object> export() {
        PpsScoringConfig row = row();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("config", parse(row.getOverrides()).nested());
        out.put("exportedAt", OffsetDateTime.now().toString());
        return out;
    }

    @Transactional(readOnly = true)
    public List<PpsScoringConfigHistoryResponse> history() {
        return historyRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(h -> new PpsScoringConfigHistoryResponse(h.getId(), flat(h.getPrevious()), flat(h.getOverrides()),
                        h.getChangedBy().getFullName(), h.getNote(), h.getCreatedAt()))
                .toList();
    }

    // ---------- nội bộ ----------

    private PpsScoringConfig row() {
        return repository.findById(PpsScoringConfig.SINGLETON_ID).orElseGet(() -> {
            PpsScoringConfig created = new PpsScoringConfig();
            created.setOverrides(objectMapper.createObjectNode());
            return created;
        });
    }

    /** Chỉ đổi bộ chấm sau khi giao dịch đã chốt, để lưu thất bại giữa chừng không để lại cấu hình chưa được lưu trong bộ nhớ. */
    private void applyAfterCommit(PpsConfig config, OffsetDateTime updatedAt) {
        Runnable apply = () -> {
            holder.set(config);
            loadedStamp = stamp(updatedAt);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    apply.run();
                }
            });
        } else {
            apply.run();
        }
    }

    private static Instant stamp(OffsetDateTime t) {
        return t.toInstant().truncatedTo(ChronoUnit.MILLIS);
    }

    private Map<String, Double> flat(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            return new LinkedHashMap<>();
        }
        return objectMapper.convertValue(node, FLAT);
    }

    /** Cấu hình hỏng thì dùng mặc định thay vì làm hỏng cả trang hoặc bộ chấm. */
    private PpsConfig parse(JsonNode overrides) {
        try {
            return PpsConfig.validate(new LinkedHashMap<String, Object>(flat(overrides)), PpsConfig.defaults());
        } catch (RuntimeException e) {
            log.warn("PpsScoringConfigService: cấu hình lưu trong DB không hợp lệ, dùng mặc định. {}", e.getMessage());
            return PpsConfig.defaults();
        }
    }

    private PpsScoringConfigResponse toResponse(PpsScoringConfig row, PpsConfig config) {
        Map<String, Double> changed = config.changed();
        List<PpsScoringConfigResponse.Field> fields = new ArrayList<>();
        for (PpsConfig.Field f : PpsConfig.FIELDS) {
            fields.add(new PpsScoringConfigResponse.Field(f.path(), f.group(), f.label(), f.help(), f.min(), f.max(), f.step(),
                    f.defaultValue(), config.get(f.path()), changed.containsKey(f.path())));
        }
        return new PpsScoringConfigResponse(fields, changed, PpsConfig.weightGroups(),
                row.getUpdatedBy() == null ? null : row.getUpdatedBy().getFullName(), row.getUpdatedAt());
    }
}
