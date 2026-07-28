-- =====================================================================
-- V52: PHAN HE 7 - KHO VIDEO ON TAP (UC-23/UC-23a, FR-LMS-01)
-- Tai cau truc tu "Kho bai giang" (lessons/lesson_materials/lessons_history,
-- V16) - da xac nhan voi nguoi dung 2026-07-27: chi con video/audio (bo
-- PDF/Word/Slide/Image), phan loai theo video_type (CONNECTION/REFLEX),
-- them theo doi tien do xem tung hoc sinh + thong ke cho giao vien.
--
-- XAC NHAN: lessons/lesson_materials/lessons_history hien co 0 dong du
-- lieu tai thoi diem viet migration nay (kiem tra qua DB dev) - chon
-- DROP + CREATE lai tu dau thay vi ALTER hang loat vi thay doi cau truc
-- qua lon (bo nhieu cot, doi kieu phan loai, gop 2 khai niem material_type/
-- nguon upload thanh 1 enum source_type). Khong sua V16 (quy uoc
-- migration append-only).
-- =====================================================================

DROP TABLE IF EXISTS lessons_history;
DROP TABLE IF EXISTS lesson_materials;
DROP TABLE IF EXISTS lessons;

-- a) review_video_sets -- "Bo" video on tap (chung theo curriculum hoac rieng theo class)
CREATE TABLE review_video_sets (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    code                VARCHAR(50) NOT NULL,
    title               VARCHAR(500) NOT NULL,
    video_type          VARCHAR(20) NOT NULL, -- CONNECTION (video tu ket noi) / REFLEX (video phan xa)
    curriculum_id       BIGINT NULL REFERENCES curriculums(id),
    class_id            BIGINT NULL REFERENCES classes(id),
    subject_id          BIGINT NULL REFERENCES curriculum_subjects(id),
    display_order       INT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT / PUBLISHED / ARCHIVED
    published_at        TIMESTAMPTZ NULL,
    created_by          BIGINT NOT NULL REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_review_video_set_scope CHECK (
        (curriculum_id IS NOT NULL AND class_id IS NULL) OR
        (curriculum_id IS NULL AND class_id IS NOT NULL)
    )
);
CREATE INDEX idx_review_video_sets_curriculum ON review_video_sets(curriculum_id);
CREATE INDEX idx_review_video_sets_class ON review_video_sets(class_id);

-- b) review_videos -- Tung video/audio trong 1 bo (khong history, sua tao ban ghi moi)
CREATE TABLE review_videos (
    id                  BIGSERIAL PRIMARY KEY,
    review_video_set_id BIGINT NOT NULL REFERENCES review_video_sets(id),
    title               VARCHAR(300) NOT NULL,
    source_type         VARCHAR(20) NOT NULL, -- YOUTUBE_URL / R2_VIDEO / R2_AUDIO
    file_url            VARCHAR(1000) NOT NULL,
    file_size_bytes     BIGINT NULL, -- NULL voi YOUTUBE_URL
    duration_seconds    INT NOT NULL, -- bat buoc voi ca 3 nguon - FE tu doc truoc khi tao (xem docs/uc)
    display_order       INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_review_videos_set ON review_videos(review_video_set_id);

-- c) review_video_sets_history -- JSONB diff-log, giu nguyen pattern cu (lessons_history)
CREATE TABLE review_video_sets_history (
    id                   BIGSERIAL PRIMARY KEY,
    review_video_set_id  BIGINT NOT NULL REFERENCES review_video_sets(id),
    changed_by           BIGINT NOT NULL REFERENCES users(id),
    action                VARCHAR(20) NOT NULL,
    details               JSONB NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_review_video_sets_history_set ON review_video_sets_history(review_video_set_id);

-- d) review_video_progress -- MOI HOAN TOAN: theo doi tien do xem tung hoc sinh/video
-- (chua tung ton tai co che nao truoc day - xac nhan qua grep toan backend).
-- watched_seconds la moc giay CAO NHAT tung dat (khong giam khi hoc sinh tua toi),
-- is_completed tinh lai server-side moi lan cap nhat = watched_seconds >= duration_seconds*0.8.
CREATE TABLE review_video_progress (
    id                BIGSERIAL PRIMARY KEY,
    review_video_id   BIGINT NOT NULL REFERENCES review_videos(id),
    student_id        BIGINT NOT NULL REFERENCES students(id),
    watched_seconds   INT NOT NULL DEFAULT 0,
    is_completed      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (review_video_id, student_id)
);
CREATE INDEX idx_review_video_progress_video ON review_video_progress(review_video_id);
CREATE INDEX idx_review_video_progress_student ON review_video_progress(student_id);
