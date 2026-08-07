package vn.com.pps.education.service.notification;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.NotificationDelivery;
import vn.com.pps.education.domain.User;

/**
 * Kênh EMAIL — gửi qua SMTP (spring.mail.*, cấu hình qua biến môi trường
 * MAIL_HOST/MAIL_USERNAME/MAIL_PASSWORD). Nếu SMTP chưa cấu hình/không kết
 * nối được, send() throw exception — NotificationDispatchService tự đánh
 * dấu FAILED + lên lịch retry (đúng state machine SDD), không crash app.
 *
 * NotificationType có mẫu đã xác nhận trong {@link NotificationEmailTemplateService}
 * (Phụ huynh/Giáo viên/Quản lý điểm trường, 2026-08-07) thì gửi HTML theo đúng
 * mẫu đó (bỏ qua title/content vốn dành cho in-app); type chưa có mẫu thì giữ
 * hành vi cũ — gửi plain text từ title/content của Notification. Khoá
 * "studentName"/"className" trong {@code notification.metadata} (nếu nơi gọi
 * notify() có truyền) được chèn vào mẫu để định danh đúng học viên/lớp trên
 * dữ liệu thật — bổ sung ngoài mẫu gốc, đã xác nhận với người dùng 2026-08-07.
 */
@Component
public class EmailNotificationSender implements NotificationChannelSender {

    private final JavaMailSender javaMailSender;
    private final NotificationEmailTemplateService templateService;
    private final String fromAddress;

    public EmailNotificationSender(JavaMailSender javaMailSender,
                                    NotificationEmailTemplateService templateService,
                                    @Value("${app.mail.from-address}") String fromAddress) {
        this.javaMailSender = javaMailSender;
        this.templateService = templateService;
        this.fromAddress = fromAddress;
    }

    @Override
    public NotificationDelivery.Channel channel() {
        return NotificationDelivery.Channel.EMAIL;
    }

    @Override
    public boolean send(NotificationDelivery delivery, Notification notification, User recipient) throws Exception {
        if (recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            return false;
        }
        var metadata = notification.getMetadata();
        String studentName = metadata == null ? null : asString(metadata.get("studentName"));
        String className = metadata == null ? null : asString(metadata.get("className"));
        var template = templateService.renderFor(notification.getNotificationType(), studentName, className);
        if (template.isPresent()) {
            sendHtml(recipient.getEmail(), template.get().subject(), template.get().htmlBody());
        } else {
            sendPlainText(recipient.getEmail(), notification.getTitle(), notification.getContent());
        }

        delivery.setRecipientAddress(recipient.getEmail());
        delivery.setProvider("SMTP");
        return true;
    }

    private void sendHtml(String to, String subject, String htmlBody) throws Exception {
        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        javaMailSender.send(message);
    }

    private void sendPlainText(String to, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);
        javaMailSender.send(message);
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
