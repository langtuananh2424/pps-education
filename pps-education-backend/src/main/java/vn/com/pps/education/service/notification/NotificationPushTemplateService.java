package vn.com.pps.education.service.notification;

import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.Notification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Mẫu push notification riêng theo NotificationType — bổ sung ngoài SDD gốc,
 * đã xác nhận với người dùng 2026-09-12: nội dung lưu trong
 * {@code notifications.content} khá kỹ thuật/robotic khi đọc trên điện
 * thoại (VD chèn thẳng tên enum trạng thái điểm danh), nên tách 1 bản diễn
 * đạt thân thiện hơn riêng cho kênh PUSH, vẫn giữ đầy đủ dữ liệu động qua
 * {@code notifications.metadata} (JSONB) — không đọc lại DB, chỉ đọc key
 * do từng call site notify() đã đưa sẵn vào metadata.
 *
 * Dùng {@link Function} thay vì cú pháp "{placeholder}" — khớp phong cách
 * hiện tại (content string được ghép bằng "+"/.formatted() ngay tại
 * Service), đủ linh hoạt xử lý phần điều kiện (VD "kèm lý do nếu có") mà
 * không cần viết thêm 1 mini template engine.
 *
 * Type không có renderer đăng ký → {@link #renderFor} trả về
 * {@code Optional.empty()} — {@link PushNotificationSender} tự fallback
 * dùng title/content thô của Notification (hành vi cũ), giống hệt cách
 * {@link NotificationEmailTemplateService} xử lý type chưa có mẫu.
 */
@Component
public class NotificationPushTemplateService {

    public record PushTemplate(String title, String body) {}

    private final Map<Notification.NotificationType, Function<Map<String, Object>, PushTemplate>> renderers =
            new EnumMap<>(Notification.NotificationType.class);

    public NotificationPushTemplateService() {
        registerRenderers();
    }

    public Optional<PushTemplate> renderFor(Notification.NotificationType type, Map<String, Object> metadata) {
        if (type == null) {
            return Optional.empty();
        }
        Function<Map<String, Object>, PushTemplate> renderer = renderers.get(type);
        if (renderer == null) {
            return Optional.empty();
        }
        Map<String, Object> safeMetadata = metadata == null ? Map.of() : metadata;
        try {
            return Optional.ofNullable(renderer.apply(safeMetadata));
        } catch (RuntimeException ex) {
            // Thiếu key cần thiết trong metadata (call site chưa cập nhật) -> fallback về
            // title/content thô thay vì để push rỗng/lỗi.
            return Optional.empty();
        }
    }

    private void registerRenderers() {
        renderers.put(Notification.NotificationType.ATTENDANCE_ABSENT, m -> new PushTemplate(
                str(m, "studentName") + " vắng học không phép",
                "Buổi %s (%s-%s), lớp %s: %s vắng không phép.".formatted(
                        fmtDate(m, "sessionDate"), fmtTime(m, "startTime"), fmtTime(m, "endTime"),
                        str(m, "className"), str(m, "studentName"))));

        renderers.put(Notification.NotificationType.ATTENDANCE_LATE, m -> new PushTemplate(
                str(m, "studentName") + " đi học muộn",
                "Buổi %s (%s-%s), lớp %s: %s đến muộn.".formatted(
                        fmtDate(m, "sessionDate"), fmtTime(m, "startTime"), fmtTime(m, "endTime"),
                        str(m, "className"), str(m, "studentName"))));

        renderers.put(Notification.NotificationType.ATTENDANCE_EXCUSED, m -> new PushTemplate(
                str(m, "studentName") + " nghỉ có phép",
                "Buổi %s, lớp %s: %s nghỉ có phép.".formatted(
                        fmtDate(m, "sessionDate"), str(m, "className"), str(m, "studentName"))));

        renderers.put(Notification.NotificationType.ATTENDANCE_EARLY_LEAVE, m -> new PushTemplate(
                str(m, "studentName") + " về sớm",
                "Buổi %s (%s-%s), lớp %s: %s về sớm.".formatted(
                        fmtDate(m, "sessionDate"), fmtTime(m, "startTime"), fmtTime(m, "endTime"),
                        str(m, "className"), str(m, "studentName"))));

        renderers.put(Notification.NotificationType.ATTENDANCE_PRESENT, m -> new PushTemplate(
                str(m, "studentName") + " có mặt đầy đủ",
                "Buổi %s, lớp %s: %s có mặt đầy đủ.".formatted(
                        fmtDate(m, "sessionDate"), str(m, "className"), str(m, "studentName"))));

        // OTHER dùng chung cho 2 nghiệp vụ khác nhau (giao bài mới / khóa tài khoản) — phân biệt
        // qua key đặc trưng có trong metadata, giống cách GRADE_PUBLISHED/GRADE_REJECTED phân biệt
        // thành phần vs tổng kết kỳ.
        renderers.put(Notification.NotificationType.OTHER, m -> {
            if (m.containsKey("username")) {
                return new PushTemplate(
                        "Tài khoản " + str(m, "username") + " bị khóa",
                        "Khóa %s phút sau %s lần sai liên tiếp từ IP %s.".formatted(
                                str(m, "lockDurationMinutes"), str(m, "maxFailedAttempts"), str(m, "ipAddress")));
            }
            String dueSuffix = m.get("dueAt") != null ? " Hạn nộp " + fmtDateTime(m, "dueAt") + "." : "";
            return new PushTemplate(
                    "Bài tập mới: " + str(m, "assignmentLabel"),
                    "Lớp %s vừa được giao %s.%s".formatted(str(m, "className"), str(m, "assignmentLabel"), dueSuffix));
        });

        renderers.put(Notification.NotificationType.STUDENT_ATTITUDE_ALERT, m -> new PushTemplate(
                str(m, "studentName") + ": thái độ học tập cần lưu ý",
                "Buổi %s, lớp %s: %s có thái độ học tập %s.".formatted(
                        fmtDate(m, "commentDate"), str(m, "className"), str(m, "studentName"), str(m, "attitudeLabel"))));

        renderers.put(Notification.NotificationType.STUDENT_ATTITUDE_ESCALATION_PENDING_APPROVAL, m -> new PushTemplate(
                "Cảnh báo thái độ chờ duyệt",
                "Học sinh %s (lớp %s) có thái độ học tập yếu/trung bình liên tục %s buổi — cần bạn duyệt trước khi gửi phụ huynh.".formatted(
                        str(m, "studentName"), str(m, "className"), str(m, "streakCount"))));

        renderers.put(Notification.NotificationType.STUDENT_ATTITUDE_ESCALATION, m -> new PushTemplate(
                "Cảnh báo thái độ học tập liên tục",
                "%s (lớp %s) có thái độ học tập yếu/trung bình liên tục %s buổi. Kính mong Quý Phụ huynh quan tâm, đồng hành cùng con.".formatted(
                        str(m, "studentName"), str(m, "className"), str(m, "streakCount"))));

        renderers.put(Notification.NotificationType.HOMEWORK_MISS_REMINDER, m -> new PushTemplate(
                "Nhắc bài tập: " + str(m, "studentName"),
                "%s (lớp %s) đã thiếu %s liên tục %s buổi — nhắc con hoàn thành nhé!".formatted(
                        str(m, "studentName"), str(m, "className"), str(m, "channelLabel"), str(m, "count"))));

        renderers.put(Notification.NotificationType.HOMEWORK_MISS_WARNING, m -> new PushTemplate(
                "Cảnh báo bài tập: " + str(m, "studentName"),
                "%s (lớp %s) đã thiếu %s liên tục %s buổi — cần nhắc nhở gấp.".formatted(
                        str(m, "studentName"), str(m, "className"), str(m, "channelLabel"), str(m, "count"))));

        renderers.put(Notification.NotificationType.HOMEWORK_MISS_PARENT_MEETING_INVITE, m -> new PushTemplate(
                "Thư mời trao đổi về " + str(m, "studentName"),
                "%s đã thiếu %s liên tục %s buổi. Kính mời phụ huynh sắp xếp gặp trao đổi với giáo viên/quản lý điểm trường.".formatted(
                        str(m, "studentName"), str(m, "channelLabel"), str(m, "count"))));

        renderers.put(Notification.NotificationType.HOMEWORK_MISS_REMINDER_NON_CONSECUTIVE, m -> new PushTemplate(
                "Nhắc bài tập: " + str(m, "studentName"),
                "%s (lớp %s) đã thiếu %s %s buổi trong kỳ (không liên tục) — nhắc con hoàn thành đầy đủ nhé.".formatted(
                        str(m, "studentName"), str(m, "className"), str(m, "channelLabel"), str(m, "count"))));

        renderers.put(Notification.NotificationType.HOMEWORK_DUE_SOON_REMINDER, m -> new PushTemplate(
                "Sắp hết hạn: " + str(m, "assignmentLabel"),
                "Con %s (lớp %s) chưa hoàn thành, hạn nộp %s.".formatted(
                        str(m, "studentName"), str(m, "className"), fmtDateTime(m, "dueAt"))));

        renderers.put(Notification.NotificationType.HOMEWORK_DEADLINE_SUMMARY, m -> new PushTemplate(
                "Hết hạn %s — lớp %s".formatted(str(m, "assignmentLabel"), str(m, "className")),
                "Tỷ lệ hoàn thành %s/%s học sinh (%s%%). Xem chi tiết trong hệ thống.".formatted(
                        str(m, "completedCount"), str(m, "total"), str(m, "ratePercent"))));

        renderers.put(Notification.NotificationType.GRADE_PUBLISHED, m -> {
            if (m.containsKey("componentName")) {
                return new PushTemplate(
                        "Điểm " + str(m, "componentName") + " đã công bố",
                        "%s (lớp %s): %s/%s điểm.".formatted(
                                str(m, "studentName"), str(m, "className"), str(m, "score"), str(m, "maxScore")));
            }
            String levelSuffix = m.get("level") != null ? " Level " + str(m, "level") + "." : "";
            return new PushTemplate(
                    "Điểm tổng kết " + str(m, "termName") + " đã công bố",
                    "%s (lớp %s) — %s.%s".formatted(
                            str(m, "studentName"), str(m, "className"), str(m, "evaluationType"), levelSuffix));
        });

        renderers.put(Notification.NotificationType.GRADE_REJECTED, m -> {
            String reasonSuffix = optStr(m, "reason").map(r -> " Lý do: " + r + ".").orElse("");
            if (m.containsKey("componentName")) {
                return new PushTemplate(
                        "Điểm " + str(m, "componentName") + " bị từ chối",
                        "Điểm của %s (lớp %s) bị từ chối.%s".formatted(
                                str(m, "studentName"), str(m, "className"), reasonSuffix));
            }
            return new PushTemplate(
                    "Điểm tổng kết bị từ chối",
                    "Điểm tổng kết %s của %s (lớp %s) bị từ chối.%s".formatted(
                            str(m, "termName"), str(m, "studentName"), str(m, "className"), reasonSuffix));
        });

        renderers.put(Notification.NotificationType.HOMEWORK_MEETING_INVITE_PENDING_APPROVAL, m -> new PushTemplate(
                str(m, "count") + " thư mời phụ huynh chờ duyệt",
                "Lớp %s có thư mời phụ huynh (học sinh %s) đang chờ bạn duyệt.".formatted(
                        str(m, "className"), str(m, "studentName"))));

        renderers.put(Notification.NotificationType.COMMENT_PENDING_APPROVAL, m -> new PushTemplate(
                str(m, "commentCount") + " nhận xét chờ duyệt",
                "Lớp %s có %s nhận xét học sinh mới cần bạn duyệt.".formatted(
                        str(m, "className"), str(m, "commentCount"))));

        renderers.put(Notification.NotificationType.COMMENT_REJECTED, m -> {
            String reasonSuffix = optStr(m, "reason").map(r -> " Lý do: " + r + ".").orElse("");
            return new PushTemplate(
                    "Nhận xét bị từ chối",
                    "Nhận xét cho %s (lớp %s, ngày %s) đã bị từ chối.%s".formatted(
                            str(m, "studentName"), str(m, "className"), fmtDate(m, "commentDate"), reasonSuffix));
        });

        renderers.put(Notification.NotificationType.EXAM_INTEGRITY_VIOLATION, m -> new PushTemplate(
                "Học sinh vi phạm khi làm bài",
                "%s đã rời màn hình %s lần khi làm %s — hệ thống đã tự nộp bài.".formatted(
                        str(m, "studentName"), str(m, "violationCount"), str(m, "attemptLabel"))));

        renderers.put(Notification.NotificationType.EXAM_INTEGRITY_VIOLATION_PARENT, m -> new PushTemplate(
                "Con vi phạm quy định khi làm bài",
                "%s đã rời màn hình %s lần khi làm bài — nhắc con tập trung làm bài nhé.".formatted(
                        str(m, "studentName"), str(m, "violationCount"))));

        renderers.put(Notification.NotificationType.INVOICE_DUE, m -> new PushTemplate(
                "Hóa đơn học phí mới",
                "Hóa đơn %s: %s, hạn thanh toán %s.".formatted(
                        str(m, "invoiceNumber"), fmtMoney(m, "amount"), fmtDate(m, "dueDate"))));

        renderers.put(Notification.NotificationType.LEAVE_REQUEST_STATUS, m -> {
            if (m.containsKey("resultLabel")) {
                return new PushTemplate(
                        "Đơn từ đã " + str(m, "resultLabel"),
                        "Đơn #%s (%s, %s→%s) đã %s.".formatted(
                                str(m, "leaveId"), str(m, "leaveType"), fmtDate(m, "startDate"),
                                fmtDate(m, "endDate"), str(m, "resultLabel")));
            }
            return new PushTemplate(
                    "Đơn từ chờ duyệt",
                    "Đơn #%s (%s) của %s đang chờ bạn duyệt.".formatted(
                            str(m, "leaveId"), str(m, "leaveType"), str(m, "employeeName")));
        });

        renderers.put(Notification.NotificationType.PARTNER_FEEDBACK, m -> {
            if (m.containsKey("resolutionNotes")) {
                return new PushTemplate(
                        "Phản hồi của bạn đã được xử lý",
                        "Phản hồi gửi ngày %s đã được xử lý: %s".formatted(
                                fmtDate(m, "createdDate"), str(m, "resolutionNotes")));
            }
            return new PushTemplate(
                    "Phản hồi mới từ " + str(m, "siteName"),
                    "%s vừa gửi phản hồi (%s, ưu tiên %s).".formatted(
                            str(m, "siteName"), str(m, "feedbackType"), str(m, "priority")));
        });

        renderers.put(Notification.NotificationType.SYSTEM_ANNOUNCEMENT, m -> new PushTemplate(
                "Hợp đồng " + str(m, "contractNumber") + " đã chấm dứt",
                "Điểm trường %s còn %s lớp đang hoạt động cần xử lý.".formatted(
                        str(m, "siteName"), str(m, "activeClassCount"))));

        renderers.put(Notification.NotificationType.TASK_ASSIGNED, m -> {
            String action = str(m, "action");
            String taskTitle = str(m, "taskTitle");
            return switch (action) {
                case "REASSIGNED" -> new PushTemplate(
                        "Việc được giao lại: " + taskTitle,
                        "\"%s\" đã được giao lại cho bạn.".formatted(taskTitle));
                case "CANCELLED" -> new PushTemplate(
                        "Việc đã hủy: " + taskTitle,
                        "\"%s\" đã bị hủy.%s".formatted(taskTitle,
                                optStr(m, "reason").map(r -> " Lý do: " + r + ".").orElse("")));
                case "REJECTED" -> new PushTemplate(
                        "Việc bị trả lại: " + taskTitle,
                        "\"%s\" đã bị người giao việc từ chối, cần tiếp tục xử lý.".formatted(taskTitle));
                case "DUE_SOON" -> new PushTemplate(
                        "Sắp đến hạn: " + taskTitle,
                        "\"%s\" sẽ đến hạn lúc %s.".formatted(taskTitle, fmtDateTime(m, "dueAt")));
                default -> new PushTemplate(
                        "Việc mới: " + taskTitle,
                        "Bạn được giao \"%s\"%s.".formatted(taskTitle,
                                m.get("dueAt") != null ? ", hạn " + fmtDateTime(m, "dueAt") : ""));
            };
        });

        renderers.put(Notification.NotificationType.TASK_COMMENT, m -> {
            String taskTitle = str(m, "taskTitle");
            String action = str(m, "action");
            return switch (action) {
                case "ASSIGNER_COMMENTED" -> new PushTemplate(
                        "Có phản hồi mới: " + taskTitle,
                        "\"%s\" có phản hồi mới từ người giao việc.".formatted(taskTitle));
                case "REVIEW_NEEDED" -> new PushTemplate(
                        "Công việc cần bạn xem xét",
                        "\"%s\" (người thực hiện: %s) — trạng thái hiện tại: %s.".formatted(
                                taskTitle, str(m, "assigneeName"), str(m, "status")));
                case "PROGRESS_UPDATE" -> new PushTemplate(
                        "Có phản hồi mới trong công việc",
                        "\"%s\" (người thực hiện: %s) — trạng thái hiện tại: %s.".formatted(
                                taskTitle, str(m, "assigneeName"), str(m, "status")));
                default -> new PushTemplate(
                        "Phản hồi mới: " + taskTitle,
                        "\"%s\" có phản hồi mới từ %s.".formatted(taskTitle, str(m, "actorName")));
            };
        });
    }

    private String str(Map<String, Object> m, String key) {
        Object value = m.get(key);
        return value == null ? "" : value.toString();
    }

    private Optional<String> optStr(Map<String, Object> m, String key) {
        Object value = m.get(key);
        return value == null || value.toString().isBlank() ? Optional.empty() : Optional.of(value.toString());
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private String fmtDate(Map<String, Object> m, String key) {
        Object value = m.get(key);
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDate d) {
            return d.format(DATE_FMT);
        }
        if (value instanceof OffsetDateTime dt) {
            return dt.format(DATE_FMT);
        }
        return value.toString();
    }

    private String fmtTime(Map<String, Object> m, String key) {
        Object value = m.get(key);
        if (value == null) {
            return "";
        }
        if (value instanceof LocalTime t) {
            return t.format(TIME_FMT);
        }
        return value.toString();
    }

    private String fmtDateTime(Map<String, Object> m, String key) {
        Object value = m.get(key);
        if (value == null) {
            return "";
        }
        if (value instanceof OffsetDateTime dt) {
            return dt.format(DATETIME_FMT);
        }
        return value.toString();
    }

    private String fmtMoney(Map<String, Object> m, String key) {
        Object value = m.get(key);
        if (value == null) {
            return "";
        }
        try {
            java.math.BigDecimal amount = new java.math.BigDecimal(value.toString());
            return String.format("%,d", amount.longValueExact()).replace(",", ".") + "đ";
        } catch (NumberFormatException | ArithmeticException ex) {
            return value.toString();
        }
    }
}
