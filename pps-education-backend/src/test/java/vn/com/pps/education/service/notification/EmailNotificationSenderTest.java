package vn.com.pps.education.service.notification;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.NotificationDelivery;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.repository.SiteManagerRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kênh EMAIL — Hotline trong mẫu gửi Phụ huynh lấy theo số điện thoại Quản
 * lý điểm trường của lớp, fallback về Hotline chung (env). Unit test thuần
 * (mock JavaMailSender + SiteManagerRepository), không chạm DB/SMTP.
 */
class EmailNotificationSenderTest {

    private static final String ENV_HOTLINE = "1900 0000";
    private static final String SITE_MANAGER_PHONE = "0912 345 678";

    private JavaMailSender javaMailSender;
    private SiteManagerRepository siteManagerRepository;
    private EmailNotificationSender sender;

    @BeforeEach
    void setUp() {
        javaMailSender = mock(JavaMailSender.class);
        when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        siteManagerRepository = mock(SiteManagerRepository.class);
        sender = new EmailNotificationSender(javaMailSender,
                new NotificationEmailTemplateService(ENV_HOTLINE, "support@pps.vn", "pps.vn"),
                siteManagerRepository, "no-reply@pps.vn");
    }

    @Test
    void send_parentTemplate_usesSiteManagerPhoneOfClassSite() throws Exception {
        when(siteManagerRepository.findActiveSiteManagerPhonesByClassId(10L)).thenReturn(List.of(SITE_MANAGER_PHONE));

        String body = sendAndCaptureBody(Notification.NotificationType.ATTENDANCE_ABSENT, Map.of("classId", 10L));

        assertThat(body).contains(SITE_MANAGER_PHONE).doesNotContain(ENV_HOTLINE);
    }

    @Test
    void send_parentTemplate_readsSchoolClassIdKey() throws Exception {
        when(siteManagerRepository.findActiveSiteManagerPhonesByClassId(11L)).thenReturn(List.of(SITE_MANAGER_PHONE));

        String body = sendAndCaptureBody(Notification.NotificationType.EXAM_INTEGRITY_VIOLATION_PARENT,
                Map.of("schoolClassId", 11L));

        assertThat(body).contains(SITE_MANAGER_PHONE).doesNotContain(ENV_HOTLINE);
    }

    @Test
    void send_parentTemplate_fallsBackToEnvHotline_whenSiteHasNoManagerPhone() throws Exception {
        when(siteManagerRepository.findActiveSiteManagerPhonesByClassId(10L)).thenReturn(List.of());

        String body = sendAndCaptureBody(Notification.NotificationType.HOMEWORK_MISS_PARENT_MEETING_INVITE,
                Map.of("classId", 10L));

        assertThat(body).contains(ENV_HOTLINE).doesNotContain("{hotline}");
    }

    @Test
    void send_parentTemplate_fallsBackToEnvHotline_whenMetadataHasNoClass() throws Exception {
        String body = sendAndCaptureBody(Notification.NotificationType.ATTENDANCE_ABSENT, new HashMap<>());

        assertThat(body).contains(ENV_HOTLINE);
        verify(siteManagerRepository, never()).findActiveSiteManagerPhonesByClassId(anyLong());
    }

    @Test
    void send_teacherTemplate_alwaysUsesEnvHotline() throws Exception {
        String body = sendAndCaptureBody(Notification.NotificationType.EXAM_INTEGRITY_VIOLATION, Map.of("classId", 10L));

        assertThat(body).contains(ENV_HOTLINE);
        verify(siteManagerRepository, never()).findActiveSiteManagerPhonesByClassId(anyLong());
    }

    private String sendAndCaptureBody(Notification.NotificationType type, Map<String, Object> metadata) throws Exception {
        Notification notification = new Notification();
        notification.setNotificationType(type);
        notification.setMetadata(metadata);
        User recipient = new User();
        recipient.setEmail("parent@example.com");

        assertThat(sender.send(new NotificationDelivery(), notification, recipient)).isTrue();

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());
        return (String) captor.getValue().getContent();
    }
}
