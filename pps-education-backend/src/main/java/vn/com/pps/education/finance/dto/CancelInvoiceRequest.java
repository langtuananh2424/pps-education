package vn.com.pps.education.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** UC-30 bổ sung (V210): Kế toán hủy hóa đơn phát hành sai — bắt buộc nêu lý do để đối soát. */
public record CancelInvoiceRequest(
        @NotBlank @Size(max = 500) String reason
) {}
