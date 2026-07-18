package vn.com.pps.education.service.notification;

import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.NotificationDelivery;
import vn.com.pps.education.domain.User;

/**
 * 1 kênh gửi thông báo (Open/Closed, xem .claude/rules/solid.md — cùng
 * pattern với AttendanceMethodValidator). Thêm kênh mới (SMS/Zalo/Push) =
 * thêm 1 class implement interface này, không sửa
 * NotificationDispatchService hay các sender khác.
 */
public interface NotificationChannelSender {

    NotificationDelivery.Channel channel();

    /**
     * Thử gửi 1 delivery qua kênh này. Trả về true nếu gửi thành công (job
     * gọi sẽ tự cập nhật status/sentAt) — throw exception nếu gửi lỗi, job
     * gọi sẽ tự đánh dấu FAILED + lên lịch retry, sender không tự quản lý
     * retry_count/next_retry_at.
     */
    boolean send(NotificationDelivery delivery, Notification notification, User recipient) throws Exception;
}
