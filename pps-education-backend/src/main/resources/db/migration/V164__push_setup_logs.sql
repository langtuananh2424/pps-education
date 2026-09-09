-- Bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-09-07: ghi lai ket qua tung lan chay
-- setupPushNotifications() phia client (thanh cong/tung buoc that bai + ly do) - luong dang ky
-- push hien tai la fire-and-forget, nuot loi hoan toan phia FE, khong co cach nao debug qua SQL
-- neu khong co Safari Web Inspector (phat hien qua debug thuc te loi push tren iOS khong co Mac
-- de remote-debug). Bang ghi don, khong sua/xoa - dung de tra cuu debug, khong phai nghiep vu.
CREATE TABLE push_setup_logs (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES users(id),
    status         VARCHAR(30) NOT NULL, -- REGISTERED / UNSUPPORTED / NEEDS_IOS_SHORTCUT / PERMISSION_DENIED / NOT_CONFIGURED / ERROR
    error_message  TEXT NULL,
    platform       VARCHAR(20) NULL,
    user_agent     VARCHAR(500) NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_push_setup_logs_user ON push_setup_logs(user_id, created_at DESC);
