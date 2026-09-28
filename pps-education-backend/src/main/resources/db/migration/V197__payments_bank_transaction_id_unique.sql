-- Bổ sung ngoài SDD gốc (rà soát bảo mật 2026-09-28, đã xác nhận với người dùng) - UC-30 webhook ngân
-- hàng: cùng 1 giao dịch ngân hàng (bank_transaction_id) chỉ được ghi nhận 1 lần, chống cộng tiền trùng
-- khi ngân hàng gửi lại webhook hoặc bị replay. Partial index: thanh toán thủ công (A2) không có
-- bank_transaction_id (NULL) nên không bị ràng buộc. Service đã kiểm tra trước (InvoiceService.
-- confirmBankWebhook) - index là chốt chặn cuối cho trường hợp 2 request đồng thời.
CREATE UNIQUE INDEX uq_payments_bank_transaction_id
    ON payments (bank_transaction_id)
    WHERE bank_transaction_id IS NOT NULL;
