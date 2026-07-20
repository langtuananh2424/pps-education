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
 * UC-20 (mở rộng, bổ sung ngoài SDD gốc, đã xác nhận với người dùng): tự
 * động công bố (DRAFT → PUBLISHED) mọi grade_entries/grade_period_results
 * còn DRAFT của 1 (lớp, kỳ đánh giá) nếu đã quá hạn X ngày kể từ lần đầu
 * nhập điểm (grade_period_edit_windows.first_entered_at, cùng
 * system_settings.academic.grade_edit_window_days đang dùng cho hạn
 * chỉnh sửa ở GradeService) — song song với công bố thủ công
 * (GradeService#publishGrades), không thay thế. Nếu Quản lý điểm trường
 * đã công bố tay trước đó, job này không làm gì thêm (chỉ tác động các
 * bản ghi còn DRAFT).
 *
 * publishedBy để NULL cho các bản ghi tự động công bố (khác công bố thủ
 * công luôn có publishedBy) — tự thân đã là tín hiệu phân biệt "công bố
 * tự động" mà không cần cột/trạng thái mới. Không ghi grade_entries_history
 * cho hành động này vì changed_by ở đó NOT NULL (yêu cầu 1 User thật) —
 * không có actor con người tương ứng; publishedAt/publishedBy=null trên
 * chính bản ghi đã đủ làm audit trail cho hành động này.
 */
@Service
public class GradeSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(GradeSchedulerService.class);

    private final GradePeriodEditWindowRepository gradePeriodEditWindowRepository;
    private final GradeEntryRepository gradeEntryRepository;
    private final GradePeriodResultRepository gradePeriodResultRepository;
    private final AcademicSettingsService academicSettingsService;

    public GradeSchedulerService(GradePeriodEditWindowRepository gradePeriodEditWindowRepository,
                                  GradeEntryRepository gradeEntryRepository,
                                  GradePeriodResultRepository gradePeriodResultRepository,
                                  AcademicSettingsService academicSettingsService) {
        this.gradePeriodEditWindowRepository = gradePeriodEditWindowRepository;
        this.gradeEntryRepository = gradeEntryRepository;
        this.gradePeriodResultRepository = gradePeriodResultRepository;
        this.academicSettingsService = academicSettingsService;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void autoPublishExpiredGrades() {
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
                entry.setStatus(GradeEntry.Status.PUBLISHED);
                entry.setPublishedAt(now);
            }
            gradeEntryRepository.saveAll(draftEntries);
            publishedEntries += draftEntries.size();

            List<GradePeriodResult> draftResults = gradePeriodResultRepository
                    .findBySchoolClassIdAndGradePeriodIdAndStatus(classId, gradePeriodId, GradePeriodResult.Status.DRAFT);
            for (GradePeriodResult result : draftResults) {
                result.setStatus(GradePeriodResult.Status.PUBLISHED);
                result.setPublishedAt(now);
            }
            gradePeriodResultRepository.saveAll(draftResults);
            publishedResults += draftResults.size();
        }

        if (publishedEntries > 0 || publishedResults > 0) {
            log.info("GradeSchedulerService: tự động công bố {} grade_entries + {} grade_period_results "
                            + "quá hạn {} ngày (qua {} lớp/kỳ đánh giá).",
                    publishedEntries, publishedResults, days, expiredWindows.size());
        }
    }
}
