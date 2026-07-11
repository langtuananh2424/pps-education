package vn.com.pps.education.service.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.NotificationDelivery;
import vn.com.pps.education.domain.User;

/**
 * Kênh EMAIL — gửi qua SMTP (spring.mail.*, cấu hình qua biến môi trường
 * MAIL_HOST/MAIL_USERNAME/MAIL_PASSWORD). Nếu SMTP chưa cấu hình/không kết
 * nối được, send() throw exception — NotificationDispatchService tự đánh
 * dấu FAILED + lên lịch retry (đúng state machine SDD), không crash app.
 */
@Component
public class EmailNotificationSender implements NotificationChannelSender {

    private final JavaMailSender javaMailSender;
    private final String fromAddress;

    public EmailNotificationSender(JavaMailSender javaMailSender,
                                    @Value("${app.mail.from-address}") String fromAddress) {
        this.javaMailSender = javaMailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public NotificationDelivery.Channel channel() {
        return NotificationDelivery.Channel.EMAIL;
    }

    @Override
    public boolean send(NotificationDelivery delivery, Notification notification, User recipient) {
        if (recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            return false;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipient.getEmail());
        message.setSubject(notification.getTitle());
        message.setText(notification.getContent());
        javaMailSender.send(message);

        delivery.setRecipientAddress(recipient.getEmail());
        delivery.setProvider("SMTP");
        return true;
    }
}
