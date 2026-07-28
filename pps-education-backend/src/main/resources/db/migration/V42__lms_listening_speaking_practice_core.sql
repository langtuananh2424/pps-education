-- =====================================================================
-- V42: PHAN HE 7 - LUYEN NGHE - NOI (UC-26, FR-LMS-04).
-- BO SUNG NGOAI SDD GOC - da xac nhan voi nguoi dung.
--
-- UC-26 da co dac ta day du (Main Flow 3 che do: Nghe/Chep chinh
-- ta/Noi) nhung SDD chua tung co bang nao cho tinh nang nay. Domain
-- rieng biet, KHONG dung chung bang exercises/student_answers (da xac
-- nhan voi nguoi dung) - to chuc theo curriculum, tu luyen khong
-- deadline. Cham "Noi" luon chuyen hang cho cham thu cong rieng, KHONG
-- tich hop ASR/so khop phat am tu dong bang dich vu ben thu 3 (khac 1
-- chut so voi cau chu goc UC-26 "so bo tu dong" - xem ghi chu sua UC-26
-- trong docs/uc/phan-he-07-lms-portal.md).
-- =====================================================================

CREATE TABLE listening_practice_items (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    curriculum_id       BIGINT NOT NULL REFERENCES curriculums(id),
    title               VARCHAR(500) NOT NULL,
    mode                VARCHAR(20) NOT NULL, -- LISTENING / DICTATION / SPEAKING
    audio_url           VARCHAR(1000) NULL, -- NULL = FE tu doc bang Web Speech API trinh duyet
    script_text         TEXT NOT NULL, -- highlight/so khop chinh ta/mau chuan phat am
    difficulty          VARCHAR(20) NULL, -- EASY / MEDIUM / HARD
    display_order       INT NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT / PUBLISHED / ARCHIVED
    created_by          BIGINT NOT NULL REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_listening_practice_items_curriculum ON listening_practice_items(curriculum_id);

CREATE TABLE listening_practice_attempts (
    id                          BIGSERIAL PRIMARY KEY,
    uuid                        UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    practice_item_id            BIGINT NOT NULL REFERENCES listening_practice_items(id),
    student_id                  BIGINT NOT NULL REFERENCES students(id),
    attempt_number               INT NOT NULL DEFAULT 1, -- tu luyen, khong gioi han luot (khac UC-24)
    started_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at                 TIMESTAMPTZ NULL,
    paused_position_seconds     INT NULL, -- A1: tam dung giua chung, luu vi tri de tiep tuc
    dictation_answer_text       TEXT NULL,
    dictation_score             DECIMAL(5,2) NULL, -- so khop tu dong voi script_text
    audio_answer_url            VARCHAR(1000) NULL, -- che do Noi
    status                      VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS', -- IN_PROGRESS / SUBMITTED / GRADED
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_listening_practice_attempts_student ON listening_practice_attempts(student_id);
CREATE INDEX idx_listening_practice_attempts_item ON listening_practice_attempts(practice_item_id);

CREATE TABLE listening_practice_gradings (
    id                      BIGSERIAL PRIMARY KEY,
    practice_attempt_id     BIGINT UNIQUE NOT NULL REFERENCES listening_practice_attempts(id), -- 1 attempt toi da 1 lan cham
    grader_user_id          BIGINT NOT NULL REFERENCES users(id),
    score                   DECIMAL(5,2) NOT NULL,
    max_score               DECIMAL(5,2) NOT NULL,
    feedback                TEXT NULL,
    graded_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Permission rieng cho quan ly noi dung luyen nghe-noi (khong tao them
-- permission cham diem moi - cham "Noi" tai dung lms.grading.manage da
-- co san, mo ta goc "Cham bai thu cong" du tong quat, dung ngu nghia).
INSERT INTO permissions (code, name, module, description) VALUES
('lms.listening-practice.manage', 'Quản lý bài luyện nghe-nói', 'LMS', 'UC-26');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'lms.listening-practice.manage' AND r.code IN ('TEACHER');
