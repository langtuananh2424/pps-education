-- =====================================================================
-- V170: Bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-09-12.
--
-- Yeu cau khach hang: "Thu moi phu huynh toi lam viec" (canh bao mien
-- HOMEWORK_MISS_PARENT_MEETING_INVITE, khi hoc sinh thieu bai lien tuc 4
-- buoi) phai duoc Quan ly diem truong DUYET truoc khi gui xuong Phu huynh
-- -- 3 loai canh bao con lai (nhac 2 buoi/canh bao 3 buoi/nhac khong lien
-- tuc trong ky) van gui thang nhu cu, khong doi.
--
-- KHONG tai su dung approval_flows vi cot submitted_by cua bang do la
-- NOT NULL (bat buoc co nguoi nop) -- thu moi nay do he thong TU SINH RA
-- khi dem streak cham moc 4, khong co "nguoi nop" thuc su. Dung bang rieng
-- voi status cua chinh no.
-- =====================================================================

CREATE TABLE homework_parent_meeting_invites (
    id                BIGSERIAL PRIMARY KEY,
    student_id        BIGINT NOT NULL REFERENCES students(id),
    school_class_id   BIGINT NOT NULL REFERENCES school_classes(id),
    channel_label     VARCHAR(50) NOT NULL,
    miss_count        INT NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decided_by        BIGINT REFERENCES users(id),
    decided_at        TIMESTAMPTZ,
    rejection_reason  TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hw_meeting_invite_status ON homework_parent_meeting_invites(status);
CREATE INDEX idx_hw_meeting_invite_class ON homework_parent_meeting_invites(school_class_id);
