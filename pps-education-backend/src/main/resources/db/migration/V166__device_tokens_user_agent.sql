-- Bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-09-07: dedupe device_tokens theo them
-- user_agent, khong chi dua vao device_id.
--
-- Ly do: device_id la UUID sinh + luu localStorage phia client, nen XOA APP CAI LAI se lam mat
-- localStorage -> sinh device_id moi hoan toan -> token cu cua CUNG 1 dien thoai khong con cach nao
-- nhan ra de don, tich luy dan (thuc te da co user tich 7 token active, gay tran cot
-- recipient_address VARCHAR(500) -> delivery ket PENDING -> job nen gui lai push moi phut vo han).
--
-- user_agent doc truc tiep tu HTTP header phia server nen KHONG bi anh huong boi viec xoa app/
-- localStorage - dung lam dau hieu nhan dien "van la thiet bi do" ben vung hon device_id.
ALTER TABLE device_tokens
    ADD COLUMN user_agent VARCHAR(500) NULL;

CREATE INDEX idx_device_tokens_user_agent ON device_tokens(user_id, user_agent) WHERE user_agent IS NOT NULL;
