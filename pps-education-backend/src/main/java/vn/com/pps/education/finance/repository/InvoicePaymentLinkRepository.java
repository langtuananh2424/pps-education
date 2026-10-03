package vn.com.pps.education.finance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.finance.domain.InvoicePaymentLink;

import java.util.List;
import java.util.Optional;

public interface InvoicePaymentLinkRepository extends JpaRepository<InvoicePaymentLink, Long> {

    /** Webhook payOS đối chiếu hóa đơn qua order_code. */
    Optional<InvoicePaymentLink> findByProviderAndOrderCode(String provider, Long orderCode);

    List<InvoicePaymentLink> findByInvoiceIdAndStatus(Long invoiceId, InvoicePaymentLink.Status status);
}
