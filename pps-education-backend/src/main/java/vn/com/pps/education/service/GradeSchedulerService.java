package vn.com.pps.education.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.GradeEntry;
import vn.com.pps.education.domain.GradePeriodEditWindow;
import vn.com.pps.education.domain.GradePeriodResult;
import vn.com.pps.education.repository.GradeEntryRepository;
import vn.com.pps.education.repository.GradePeriodEditWindowRepository;
import vn.com.pps.education.repository.GradePeriodResultRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * UC-20/UC-62 (mở rộng, bổ sung ngoài SDD gốc, đã xác nhận với người
 * dùng): 2 job đêm tách biệt, chạy giờ khác nhau để không chồng nhau.
 *
 * <p>{@link #autoPublishProvisionalExpiredGrades()} (UC-20 A3): tự động
 * công bố dự kiến (DRAFT → PROVISIONAL_PUBLISHED) mọi grade_entries/
 * grade_period_results còn DRAFT của 1 (lớp, kỳ đánh giá) nếu đã quá hạn
 * X ngày kể từ lần đầu nhập điểm (grade_period_edit_windows.first_entered_at,
 * system_settings.academic.grade_edit_window_days) — song song với công
 * bố thủ công (GradeService#publishGrades), không thay thế.
 *
 * <p>{@link #autoFinalizeExpiredAppealWindow()} (UC-62 A3, mới ở V43): tự
 * động chuyển Chính thức (→ OFFICIAL) mọi bản ghi còn PROVISIONAL_PUBLISHED
 * hoặc APPEAL đã quá hạn Y ngày kể từ publishedAt
 * (system_settings.academic.grade_appeal_window_days) — chạy BẤT KỂ bản
 * ghi có đang phúc khảo dở dang hay không (đã xác nhận với người dùng),
 * không gửi thông báo gì thêm (không có trong yêu cầu).
 *
 * <p>publishedBy để NULL cho các bản ghi tự động công bố dự kiến (khác
 * công bố thủ công luôn có publishedBy) — tự thân đã là tín hiệu phân
 * biệt "công bố tự động" mà không cần cột/trạng thái mới. Không ghi
 * grade_entries_history cho hành động này vì changed_by ở đó NOT NULL
 * (yêu cầu 1 User thật) — không có actor con người tương ứng;
 * publishedAt/publishedBy=null trên chính bản ghi đã đủ làm audit trail.
 *
 * <p>Thông báo Phụ huynh (bổ sung ngoài SDD gốc, đã xác nhận với người
 * dùng): sau khi công bố dự kiến, tái dùng đúng
 * GradeService#notifyParentsGradeEntryPublished/notifyParentsPeriodResultPublished
 * (không viết lại logic gửi thông báo) — triggeredByUserId=null vì
 * không có actor con người, khớp quy ước publishedBy=null ở trên.
 */
@Service
public class GradeSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(GradeSchedulerService.class);

    private final GradePeriodEditWindowRepository gradePeriodEditWindowRepository;
    private final GradeEntryRepository gradeEntryRepository;
    private final GradePeriodResultRepository gradePeriodResultRepository;
    private final AcademicSettingsService academicSettingsService;
    private final GradeService gradeService;

    public GradeSchedulerService(GradePeriodEditWindowRepository gradePeriodEditWindowRepository,
                                  GradeEntryRepository gradeEntryRepository,
                                  GradePeriodResultRepository gradePeriodResultRepository,
                                  AcademicSettingsService academicSettingsService,
                                  GradeService gradeService) {
        this.gradePeriodEditWindowRepository = gradePeriodEditWindowRepository;
        this.gradeEntryRepository = gradeEntryRepository;
        this.gradePeriodResultRepository = gradePeriodResultRepository;
        this.academicSettingsService = academicSettingsService;
        this.gradeService = gradeService;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void autoPublishProvisionalExpiredGrades() {
        int days = academicSettingsService.gradeEditWindowDays();
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(days);
        List<GradePeriodEditWindow> expiredWindows = gradePeriodEditWindowRepository.findByFirstEnteredAtBefore(cutoff);

        int publishedEntries = 0;
        int publishedResults = 0;
        OffsetDateTime now = OffsetDateTime.now();
        for (GradePeriodEditWindow window : expiredWindows) {
            Long classId = window.getSchoolClass().getId();
            Long gradePeriodId = window.getGradePeriod().getId();

            List<GradeEntry> draftEntries = gradeEntryRepository
                    .findBySchoolClassIdAndGradePeriodIdAndStatus(classId, gradePeriodId, GradeEntry.Status.DRAFT);
            for (GradeEntry entry : draftEntries) {
                entry.setStatus(GradeEntry.Status.PROVISIONAL_PUBLISHED);
                entry.setPublishedAt(now);
            }
            List<GradeEntry> savedEntries = gradeEntryRepository.saveAll(draftEntries);
            savedEntries.forEach(entry -> gradeService.notifyParentsGradeEntryPublished(entry, null));
            publishedEntries += savedEntries.size();

            List<GradePeriodResult> draftResults = gradePeriodResultRepository
                    .findBySchoolClassIdAndGradePeriodIdAndStatus(classId, gradePeriodId, GradePeriodResult.Status.DRAFT);
            for (GradePeriodResult result : draftResults) {
                result.setStatus(GradePeriodResult.Status.PROVISIONAL_PUBLISHED);
                result.setPublishedAt(now);
            }
            List<GradePeriodResult> savedResults = gradePeriodResultRepository.saveAll(draftResults);
            savedResults.forEach(result -> gradeService.notifyParentsPeriodResultPublished(result, null));
            publishedResults += savedResults.size();
        }

        if (publishedEntries > 0 || publishedResults > 0) {
            log.info("GradeSchedulerService: tự động công bố dự kiến {} grade_entries + {} grade_period_results "
                            + "quá hạn {} ngày (qua {} lớp/kỳ đánh giá).",
                    publishedEntries, publishedResults, days, expiredWindows.size());
        }
    }

    /** UC-62 A3 — chạy lệch giờ so với autoPublishProvisionalExpiredGrades để không chồng nhau. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void autoFinalizeExpiredAppealWindow() {
        int days = academicSettingsService.gradeAppealWindowDays();
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(days);
        List<GradeEntry.Status> entryStatuses = List.of(GradeEntry.Status.PROVISIONAL_PUBLISHED, GradeEntry.Status.APPEAL);
        List<GradePeriodResult.Status> resultStatuses =
                List.of(GradePeriodResult.Status.PROVISIONAL_PUBLISHED, GradePeriodResult.Status.APPEAL);

        OffsetDateTime now = OffsetDateTime.now();
        List<GradeEntry> expiredEntries = gradeEntryRepository.findByStatusInAndPublishedAtBefore(entryStatuses, cutoff);
        expiredEntries.forEach(entry -> {
            entry.setStatus(GradeEntry.Status.OFFICIAL);
            entry.setFinalizedAt(now);
        });
        gradeEntryRepository.saveAll(expiredEntries);

        List<GradePeriodResult> expiredResults = gradePeriodResultRepository.findByStatusInAndPublishedAtBefore(resultStatuses, cutoff);
        expiredResults.forEach(result -> {
            result.setStatus(GradePeriodResult.Status.OFFICIAL);
            result.setFinalizedAt(now);
        });
        gradePeriodResultRepository.saveAll(expiredResults);

        if (!expiredEntries.isEmpty() || !expiredResults.isEmpty()) {
            log.info("GradeSchedulerService: tự động chuyển Chính thức {} grade_entries + {} grade_period_results "
                            + "quá hạn phúc khảo {} ngày.",
                    expiredEntries.size(), expiredResults.size(), days);
        }
    }
}
