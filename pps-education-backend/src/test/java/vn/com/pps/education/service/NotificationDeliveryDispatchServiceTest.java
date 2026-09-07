package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.NotificationDelivery;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.repository.NotificationDeliveryRepository;
import vn.com.pps.education.service.notification.NotificationChannelSender;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Xử lý 1 notification_delivery trong transaction riêng — bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng 2026-09-07. Unit test thuần (mock repo + sender), không chạm DB.
 */
class NotificationDeliveryDispatchServiceTest {

    private static final int MAX_RETRY = 3;
    private static final long RETRY_INTERVAL_MIN = 5;

    private NotificationDeliveryRepository deliveryRepository;
    private NotificationChannelSender pushSender;
    private NotificationDeliveryDispatchService service;

    @BeforeEach
    void setUp() {
        deliveryRepository = mock(NotificationDeliveryRepository.class);
        pushSender = mock(NotificationChannelSender.class);
        when(pushSender.channel()).thenReturn(NotificationDelivery.Channel.PUSH);
        service = new NotificationDeliveryDispatchService(
                deliveryRepository, List.of(pushSender), MAX_RETRY, RETRY_INTERVAL_MIN);
    }

    @Test
    void dispatch_marksSent_whenSenderReturnsTrue() throws Exception {
        NotificationDelivery delivery = pushDelivery(1L);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
        when(pushSender.send(any(), any(), any())).thenReturn(true);

        service.dispatch(1L);

        assertThat(delivery.getDeliveryStatus()).isEqualTo(NotificationDelivery.DeliveryStatus.SENT);
        assertThat(delivery.getSentAt()).isNotNull();
        assertThat(delivery.getRetryCount()).isZero();
        verify(deliveryRepository).save(delivery);
    }

    @Test
    void dispatch_marksFailedAndSchedulesRetry_whenSenderReturnsFalse() throws Exception {
        NotificationDelivery delivery = pushDelivery(2L);
        when(deliveryRepository.findById(2L)).thenReturn(Optional.of(delivery));
        when(pushSender.send(any(), any(), any())).thenReturn(false);

        service.dispatch(2L);

        assertThat(delivery.getDeliveryStatus()).isEqualTo(NotificationDelivery.DeliveryStatus.FAILED);
        assertThat(delivery.getRetryCount()).isEqualTo(1);
        assertThat(delivery.getNextRetryAt()).isNotNull();
        assertThat(delivery.getErrorMessage()).contains("sender trả về false");
        verify(deliveryRepository).save(delivery);
    }

    @Test
    void dispatch_marksFailedWithMessage_whenSenderThrows() throws Exception {
        NotificationDelivery delivery = pushDelivery(3L);
        when(deliveryRepository.findById(3L)).thenReturn(Optional.of(delivery));
        when(pushSender.send(any(), any(), any())).thenThrow(new IllegalStateException("Firebase chưa cấu hình"));

        service.dispatch(3L);

        assertThat(delivery.getDeliveryStatus()).isEqualTo(NotificationDelivery.DeliveryStatus.FAILED);
        assertThat(delivery.getErrorMessage()).isEqualTo("Firebase chưa cấu hình");
        assertThat(delivery.getRetryCount()).isEqualTo(1);
        assertThat(delivery.getNextRetryAt()).isNotNull();
    }

    @Test
    void dispatch_stopsSchedulingRetry_whenMaxRetryReached() throws Exception {
        NotificationDelivery delivery = pushDelivery(4L);
        delivery.setRetryCount(MAX_RETRY - 1); // lần này là lần retry cuối
        when(deliveryRepository.findById(4L)).thenReturn(Optional.of(delivery));
        when(pushSender.send(any(), any(), any())).thenReturn(false);

        service.dispatch(4L);

        assertThat(delivery.getRetryCount()).isEqualTo(MAX_RETRY);
        assertThat(delivery.getNextRetryAt()).as("hết lượt retry -> không lên lịch nữa").isNull();
    }

    @Test
    void dispatch_marksFailed_whenNoSenderRegisteredForChannel() throws Exception {
        NotificationDelivery delivery = pushDelivery(5L);
        delivery.setChannel(NotificationDelivery.Channel.EMAIL); // list sender chỉ có PUSH
        when(deliveryRepository.findById(5L)).thenReturn(Optional.of(delivery));

        service.dispatch(5L);

        assertThat(delivery.getDeliveryStatus()).isEqualTo(NotificationDelivery.DeliveryStatus.FAILED);
        assertThat(delivery.getErrorMessage()).contains("Không có sender");
        verify(pushSender, never()).send(any(), any(), any());
        verify(deliveryRepository).save(delivery);
    }

    @Test
    void dispatch_noOp_whenDeliveryNotFound() throws Exception {
        when(deliveryRepository.findById(99L)).thenReturn(Optional.empty());

        service.dispatch(99L);

        verify(deliveryRepository, never()).save(any());
        verify(pushSender, never()).send(any(), any(), any());
    }

    private static NotificationDelivery pushDelivery(long id) {
        User recipient = new User();
        recipient.setId(id * 10);
        Notification notification = new Notification();
        notification.setId(id * 100);
        notification.setRecipientUser(recipient);
        notification.setTitle("Tiêu đề");
        notification.setContent("Nội dung");

        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setId(id);
        delivery.setChannel(NotificationDelivery.Channel.PUSH);
        delivery.setDeliveryStatus(NotificationDelivery.DeliveryStatus.PENDING);
        delivery.setNotification(notification);
        return delivery;
    }
}
