package vn.com.pps.education.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassHistory;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.repository.ClassHistoryRepository;
import vn.com.pps.education.repository.SchoolClassRepository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service tự động hóa chuyển đổi trạng thái Lớp học theo vòng đời (SchoolClass.Status).
 * - PLANNED / OPEN_ENROLLMENT ➔ IN_PROGRESS khi ngày hiện tại >= startDate
 * - IN_PROGRESS ➔ COMPLETED khi ngày hiện tại > endDate (nếu có endDate)
 */
@Service
public class ClassStatusSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(ClassStatusSchedulerService.class);

    private final SchoolClassRepository schoolClassRepository;
    private final ClassHistoryRepository classHistoryRepository;

    public ClassStatusSchedulerService(SchoolClassRepository schoolClassRepository,
                                       ClassHistoryRepository classHistoryRepository) {
        this.schoolClassRepository = schoolClassRepository;
        this.classHistoryRepository = classHistoryRepository;
    }

    /** Run nightly at 01:00 AM. */
    @Scheduled(cron = "${app.class-status.cron:0 0 1 * * *}")
    @Transactional
    public void processClassStatusTransitions() {
        LocalDate today = LocalDate.now();
        List<SchoolClass> activeClasses = schoolClassRepository.findAll().stream()
                .filter(c -> c.getDeletedAt() == null)
                .toList();

        int transitionCount = 0;
        for (SchoolClass schoolClass : activeClasses) {
            SchoolClass.Status currentStatus = schoolClass.getStatus();
            SchoolClass.Status newStatus = null;

            // 1. PLANNED hoặc OPEN_ENROLLMENT ➔ IN_PROGRESS khi đến ngày bắt đầu
            if ((currentStatus == SchoolClass.Status.PLANNED || currentStatus == SchoolClass.Status.OPEN_ENROLLMENT)
                    && schoolClass.getStartDate() != null
                    && !today.isBefore(schoolClass.getStartDate())) {
                newStatus = SchoolClass.Status.IN_PROGRESS;
            }
            // 2. IN_PROGRESS ➔ COMPLETED khi qua ngày kết thúc
            else if (currentStatus == SchoolClass.Status.IN_PROGRESS
                    && schoolClass.getEndDate() != null
                    && today.isAfter(schoolClass.getEndDate())) {
                newStatus = SchoolClass.Status.COMPLETED;
            }

            if (newStatus != null) {
                log.info("Chuyển trạng thái lớp học id={} ({}) từ {} sang {}",
                        schoolClass.getId(), schoolClass.getName(), currentStatus, newStatus);
                schoolClass.setStatus(newStatus);
                schoolClassRepository.save(schoolClass);

                // Ghi nhận lịch sử chuyển trạng thái tự động
                ClassHistory history = new ClassHistory();
                history.setSchoolClass(schoolClass);
                history.setChangedBy(schoolClass.getCreatedBy());
                history.setAction(ClassHistory.Action.UPDATED);
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("classCode", schoolClass.getClassCode());
                details.put("name", schoolClass.getName());
                details.put("status", newStatus.name());
                details.put("autoTransition", true);
                history.setDetails(details);
                classHistoryRepository.save(history);

                transitionCount++;
            }
        }

        if (transitionCount > 0) {
            log.info("Đã cập nhật trạng thái tự động cho {} lớp học.", transitionCount);
        }
    }
}
