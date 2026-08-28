import { UiQuestionKind } from "./components/QuestionEditorForm";

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — bảng mapping loại câu hỏi ↔ Nhóm kỹ
 * năng (Exercise.skillCategory), dùng để khóa/rút gọn kind-picker + tab Excel/Word ở "Soạn Bài mới"
 * theo đúng Nhóm kỹ năng đã chọn ở bước 1 (ExerciseInfoStep), tránh chọn nhầm Nhóm kỹ năng không khớp
 * loại câu hỏi thật sự soạn/import vào. Nguồn chân lý DUY NHẤT — dùng chung ở cả ExerciseInfoStep (giới
 * hạn option dropdown theo teacherType) lẫn ExerciseQuestionsStep (giới hạn composeSubMode/allowedKinds).
 *
 * READING/LISTENING(compose="listeningGroup") không có "kinds" riêng ở đây vì Cloze/Grid/ListeningGroup
 * là composite builder (ClozeQuestionBuilder/GridQuestionBuilder/ListeningGroupBuilder) — không đi qua
 * kind-picker của QuestionEditorForm, chỉ cần biết MODE nào được bật.
 */
export type ComposeSubMode = "single" | "grid" | "cloze" | "listeningGroup" | "fillInBlankGroup";

export type VietnameseSkillCategory = "VOCAB_GRAMMAR" | "READING" | "WRITING";

/**
 * Bổ sung 2026-08-28 (đã xác nhận với người dùng — "Cách B") — VOCAB_GRAMMAR có thêm "fillInBlankGroup"
 * (FillInBlankGroupBuilder): soạn nhiều câu FILL_IN_BLANK cùng lúc, mỗi câu tự có "Câu N."/điểm riêng,
 * dùng khi 1 Ex. có nhiều câu (VD "Ex.1 Choose the correct word...", "Ex.3 Complete each sentence
 * with this/that/these/those...") thay vì gộp hết vào 1 Question WORD_BANK nhiều chỗ trống.
 */
export const VIETNAMESE_SKILL_MODES: Record<VietnameseSkillCategory, ComposeSubMode[]> = {
  VOCAB_GRAMMAR: ["single", "fillInBlankGroup"],
  READING: ["cloze", "grid"],
  WRITING: ["single"]
};

/** Chỉ VOCAB_GRAMMAR/WRITING có kind-picker "single" — READING luôn đi qua composite (Cloze/Grid). */
export const VIETNAMESE_SKILL_KINDS: Record<"VOCAB_GRAMMAR" | "WRITING", UiQuestionKind[]> = {
  VOCAB_GRAMMAR: [
    "MULTIPLE_CHOICE",
    "VOICE_MULTIPLE_CHOICE",
    "INLINE_CHOICE",
    "FILL_IN_BLANK",
    "FILL_IN_BLANK_PICTURE",
    "WORD_BANK",
    "WORD_BANK_PICTURE",
    "SENTENCE_BUILDING",
    "LETTER_SCRAMBLE"
  ],
  WRITING: ["ESSAY"]
};

/** GV nước ngoài chỉ có đúng 1 Nhóm kỹ năng hợp lệ — LISTENING — y hệt allowedKinds hiện có, không đổi. */
export const FOREIGN_LISTENING_MODES: ComposeSubMode[] = ["single", "listeningGroup"];
export const FOREIGN_LISTENING_KINDS: UiQuestionKind[] = ["VOICE_MULTIPLE_CHOICE", "LISTENING_AUDIO_SUBMISSION", "LISTENING_FILL_IN_BLANK"];
