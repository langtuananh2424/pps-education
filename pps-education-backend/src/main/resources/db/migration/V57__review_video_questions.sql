-- =====================================================================
-- V57: VIDEO PHAN XA (REFLEX) - CHUYEN SANG MO HINH CAU HOI THEO TIMESTAMP
-- Bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-07-28: thay the
-- hoan toan thiet ke UC-23b cu ("1 video = 1 audio duy nhat", ghi de khi
-- nop lai) bang mo hinh nhieu cau hoi trong 1 video, moi cau hoi co
-- thoi luong ghi am rieng + gioi han so lan nop lai rieng, nop lai GIU
-- LICH SU (khong ghi de). Chuyen HET video REFLEX hien co sang mo hinh
-- moi - khong giu song song 2 co che.
-- =====================================================================

-- Cau hoi theo timestamp trong 1 video REFLEX
CREATE TABLE review_video_questions (
    id                      BIGSERIAL PRIMARY KEY,
    review_video_id         BIGINT NOT NULL REFERENCES review_videos(id),
    timestamp_seconds       INT NOT NULL,
    prompt                  VARCHAR(500) NULL,
    max_recording_seconds   INT NOT NULL,
    max_attempts            INT NULL, -- NULL = khong gioi han so lan nop lai
    display_order           INT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_review_video_questions_video ON review_video_questions(review_video_id);

-- Bai nop theo cau hoi, GIU LICH SU (khac review_video_submissions cu - moi lan nop la 1 dong moi)
CREATE TABLE review_video_question_submissions (
    id                          BIGSERIAL PRIMARY KEY,
    review_video_question_id   BIGINT NOT NULL REFERENCES review_video_questions(id),
    student_id                  BIGINT NOT NULL REFERENCES students(id),
    attempt_number               INT NOT NULL,
    audio_url                    VARCHAR(1000) NOT NULL,
    submitted_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    score                          DECIMAL(5,2) NULL, -- NULL = chua cham
    max_score                      DECIMAL(5,2) NULL,
    feedback                        TEXT NULL,
    graded_by                       BIGINT NULL REFERENCES users(id),
    graded_at                       TIMESTAMPTZ NULL,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (review_video_question_id, student_id, attempt_number)
);
CREATE INDEX idx_review_video_qsub_question ON review_video_question_submissions(review_video_question_id);
CREATE INDEX idx_review_video_qsub_student ON review_video_question_submissions(student_id);

-- Migrate du lieu: moi video REFLEX hien co -> 1 cau hoi mac dinh phu ca
-- video (timestamp=0, max_recording_seconds=duration_seconds cua chinh
-- video do vi co che cu khong gioi han thoi gian ghi am, khong gioi han
-- so lan nop lai giong hanh vi "nop lai ghi de" cu).
INSERT INTO review_video_questions (review_video_id, timestamp_seconds, max_recording_seconds, max_attempts, display_order)
SELECT rv.id, 0, rv.duration_seconds, NULL, 0
FROM review_videos rv
JOIN review_video_sets rvs ON rvs.id = rv.review_video_set_id
WHERE rvs.video_type = 'REFLEX';

-- Migrate submission cu (1 ban ghi/video) -> attempt_number=1 cua cau hoi mac dinh vua tao
INSERT INTO review_video_question_submissions
    (review_video_question_id, student_id, attempt_number, audio_url, submitted_at, score, max_score, feedback, graded_by, graded_at)
SELECT q.id, s.student_id, 1, s.audio_url, s.submitted_at, s.score, s.max_score, s.feedback, s.graded_by, s.graded_at
FROM review_video_submissions s
JOIN review_video_questions q ON q.review_video_id = s.review_video_id;

DROP TABLE review_video_submissions;
