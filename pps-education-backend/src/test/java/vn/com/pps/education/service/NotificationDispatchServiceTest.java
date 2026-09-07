package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.com.pps.education.domain.NotificationDelivery;
import vn.com.pps.education.repository.NotificationDeliveryRepository;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Vòng quét background job — mỗi phút gom notification_deliveries tới hạn rồi giao từng cái cho
 * {@link NotificationDeliveryDispatchService#dispatch(Long)} (transaction riêng). Unit test thuần
 * (mock repo + mock dispatcher).
 */
class NotificationDispatchServiceTest {

    private NotificationDeliveryRepository deliveryRepository;
    private NotificationDeliveryDispatchService deliveryDispatchService;
    private NotificationDispatchService service;

    @BeforeEach
    void setUp() {
        deliveryRepository = mock(NotificationDeliveryRepository.class);
        deliveryDispatchService = mock(NotificationDeliveryDispatchService.class);
        service = new NotificationDispatchService(deliveryRepository, deliveryDispatchService);
    }

    @Test
    void processDueDeliveries_dispatchesEveryPendingAndDueFailedDelivery() {
        when(deliveryRepository.findByDeliveryStatus(NotificationDelivery.DeliveryStatus.PENDING))
                .thenReturn(List.of(delivery(1L), delivery(2L)));
        when(deliveryRepository.findByDeliveryStatusAndNextRetryAtLessThanEqual(
                eq(NotificationDelivery.DeliveryStatus.FAILED), any(OffsetDateTime.class)))
                .thenReturn(List.of(delivery(3L)));

        service.processDueDeliveries();

        ArgumentCaptor<Long> ids = ArgumentCaptor.forClass(Long.class);
        verify(deliveryDispatchService, times(3)).dispatch(ids.capture());
        assertThat(ids.getAllValues()).containsExactly(1L, 2L, 3L);
    }

    @Test
    void processDueDeliveries_continuesWithRemaining_whenOneDispatchThrows() {
        when(deliveryRepository.findByDeliveryStatus(NotificationDelivery.DeliveryStatus.PENDING))
                .thenReturn(List.of(delivery(1L), delivery(2L), delivery(3L)));
        when(deliveryRepository.findByDeliveryStatusAndNextRetryAtLessThanEqual(
                eq(NotificationDelivery.DeliveryStatus.FAILED), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        doThrow(new RuntimeException("DB rớt kết nối lúc commit")).when(deliveryDispatchService).dispatch(2L);
        doNothing().when(deliveryDispatchService).dispatch(1L);
        doNothing().when(deliveryDispatchService).dispatch(3L);

        assertThatCode(() -> service.processDueDeliveries()).doesNotThrowAnyException();

        verify(deliveryDispatchService).dispatch(1L);
        verify(deliveryDispatchService).dispatch(2L);
        verify(deliveryDispatchService).dispatch(3L); // KHÔNG bị delivery #2 chặn lại
    }

    private static NotificationDelivery delivery(long id) {
        NotificationDelivery d = new NotificationDelivery();
        d.setId(id);
        d.setChannel(NotificationDelivery.Channel.PUSH);
        d.setDeliveryStatus(NotificationDelivery.DeliveryStatus.PENDING);
        return d;
    }
}
