import React, { useEffect, useRef, useState } from "react";
import { AlignLeft, Blocks, Check, CheckSquare, ChevronDown, ClipboardList, Eye, FileText, Headphones, Image as ImageIcon, ImagePlus, Images, ListChecks, ListOrdered, Mic, PenLine, Shuffle, SplitSquareHorizontal, ToggleLeft, Volume2, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import Button from "@/components/ui/Button";
import FileUploadField from "@/components/ui/FileUploadField";
import {
  CreateExamQuestionRequest,
  CreateQuestionRequest,
  ExerciseQuestionResponse,
  KeyGrammarStructureResponse,
  QuestionChoiceRequest,
  QuestionDifficulty,
  QuestionResponse,
  QuestionType,
  createExamQuestion,
  createQuestion,
  listExamQuestionKeyGrammarOptions,
  listQuestionKeyGrammarOptions,
  updateExamQuestion,
  updateQuestion,
  uploadMedia
} from "../api";
import Select from "@/components/ui/Select";
import FloatingError from "@/components/ui/FloatingError";
import { notifyError } from "@/components/ui/FloatingBanner";
import { QuestionPreview } from "./QuestionPreview";

const inputClass = "w-full bg-white border border-slate-200 text-sm px-3.5 py-2 rounded-lg focus:outline-none focus:ring-1 focus:ring-brand-red";
const labelClass = "block font-bold text-slate-700 mb-1 uppercase tracking-wider text-[12px]";

/**
 * 12 loại — 5 loại gốc (theo bản thiết kế tham chiếu + Điền từ, bổ sung 2026-07-28 sau khi backend
 * thêm tự chấm FILL_IN_BLANK — V54) + 4 loại V78 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-08-04, dựa trên 1 đề tiếng Anh mẫu người dùng cung cấp — 5 dạng bài GV Việt Nam + "Nghe & nộp
 * audio" GV nước ngoài) + 1 loại V143 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 —
 * "Bài nghe" chọn đáp án bằng hình) + 2 loại bổ sung 2026-08-26 (đã xác nhận với người dùng — TÁCH
 * RIÊNG khỏi WORD_BANK/SENTENCE_BUILDING gốc thay vì thêm field tùy chọn vào chung 1 kind, để 2 kind
 * cũ giữ nguyên y hệt hành vi trước giờ): "WORD_BANK_PICTURE" (điền từ có ảnh + hộp từ vựng thật, khớp
 * Ex.3 "Look at the pictures and write the correct words from the box") và "LETTER_SCRAMBLE" (sắp xếp
 * chữ cái thành từ, có ảnh minh họa, khớp Ex.1 "Rearrange the letters...") + 1 loại bổ sung 2026-08-28
 * (đã xác nhận với người dùng): "FILL_IN_BLANK_PICTURE" (điền từ có ảnh minh họa RIÊNG cho mỗi câu —
 * khớp dạng "Complete each sentence with this/that/these/those" có 1 tấm hình khác nhau cho từng câu,
 * đáp án tự do không dùng khung từ dùng chung nên KHÔNG map vào WORD_BANK/WORD_BANK_PICTURE). "Trắc
 * nghiệm Voice"/"Chọn từ trong câu"/"Nghe & nộp audio"/"Nghe chọn hình"/"WORD_BANK_PICTURE"/
 * "LETTER_SCRAMBLE"/"FILL_IN_BLANK_PICTURE" KHÔNG phải giá trị enum riêng ở backend — là
 * MULTIPLE_CHOICE/SPEAKING/WORD_BANK/SENTENCE_BUILDING/FILL_IN_BLANK + skill/imageUrl tương ứng (kind
 * ảo chỉ tồn tại ở FE để hiện UI phù hợp, xem toKind()).
 */
export type UiQuestionKind =
  | "MULTIPLE_CHOICE"
  | "VOICE_MULTIPLE_CHOICE"
  | "VOICE_PICTURE_CHOICE"
  | "INLINE_CHOICE"
  | "MULTIPLE_ANSWER"
  | "TRUE_FALSE"
  | "FILL_IN_BLANK"
  | "FILL_IN_BLANK_PICTURE"
  | "WORD_BANK"
  | "WORD_BANK_PICTURE"
  | "WORD_BANK_PASSAGE"
  | "LISTENING_FORM_COMPLETION"
  | "SENTENCE_BUILDING"
  | "LETTER_SCRAMBLE"
  | "ESSAY"
  | "SPEAKING"
  | "LISTENING_AUDIO_SUBMISSION"
  | "LISTENING_FILL_IN_BLANK";

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-05 — cây "Loại cha → Kiểu con" cho
 * kind-picker (chỉ ở form soạn tay, KHÔNG đụng Excel/Word import — xem QuestionImportService).
 * Mỗi loại cha chỉ hiện nếu còn >=1 kind con khớp `visibleKinds` (đã lọc theo Nhóm kỹ năng từ
 * skillCategoryKinds.ts) — xem render phía dưới. MULTIPLE_ANSWER/TRUE_FALSE (gắn lại 2026-10-05,
 * trước đó không tạo mới được ở form này dù backend Question.QuestionType vẫn còn 2 giá trị này)
 * gộp vào nhóm Trắc nghiệm.
 */
const KIND_GROUPS: { labelKey: string; kinds: UiQuestionKind[] }[] = [
  { labelKey: "kindGroup.multipleChoice", kinds: ["MULTIPLE_CHOICE", "VOICE_MULTIPLE_CHOICE", "VOICE_PICTURE_CHOICE", "INLINE_CHOICE", "MULTIPLE_ANSWER", "TRUE_FALSE"] },
  { labelKey: "kindGroup.fillInBlank", kinds: ["FILL_IN_BLANK", "FILL_IN_BLANK_PICTURE", "LISTENING_FILL_IN_BLANK"] },
  { labelKey: "kindGroup.wordBank", kinds: ["WORD_BANK", "WORD_BANK_PICTURE", "WORD_BANK_PASSAGE", "LISTENING_FORM_COMPLETION"] },
  { labelKey: "kindGroup.sentenceBuilding", kinds: ["SENTENCE_BUILDING", "LETTER_SCRAMBLE"] },
  { labelKey: "kindGroup.essay", kinds: ["ESSAY"] },
  { labelKey: "kindGroup.speaking", kinds: ["SPEAKING", "LISTENING_AUDIO_SUBMISSION"] }
];

const kindMeta: Record<UiQuestionKind, { icon: typeof CheckSquare; activeClass: string; iconClass: string }> = {
  MULTIPLE_CHOICE: { icon: CheckSquare, activeClass: "bg-emerald-50 border-emerald-400 text-emerald-800 ring-1 ring-emerald-300", iconClass: "text-emerald-600" },
  VOICE_MULTIPLE_CHOICE: { icon: Volume2, activeClass: "bg-blue-50 border-blue-400 text-blue-800 ring-1 ring-blue-300", iconClass: "text-blue-600" },
  VOICE_PICTURE_CHOICE: { icon: ImageIcon, activeClass: "bg-indigo-50 border-indigo-400 text-indigo-800 ring-1 ring-indigo-300", iconClass: "text-indigo-600" },
  INLINE_CHOICE: { icon: SplitSquareHorizontal, activeClass: "bg-teal-50 border-teal-400 text-teal-800 ring-1 ring-teal-300", iconClass: "text-teal-600" },
  MULTIPLE_ANSWER: { icon: ListChecks, activeClass: "bg-lime-50 border-lime-400 text-lime-800 ring-1 ring-lime-300", iconClass: "text-lime-600" },
  TRUE_FALSE: { icon: ToggleLeft, activeClass: "bg-emerald-50 border-emerald-500 text-emerald-900 ring-1 ring-emerald-400", iconClass: "text-emerald-700" },
  FILL_IN_BLANK: { icon: PenLine, activeClass: "bg-amber-50 border-amber-400 text-amber-800 ring-1 ring-amber-300", iconClass: "text-amber-600" },
  FILL_IN_BLANK_PICTURE: { icon: ImagePlus, activeClass: "bg-amber-50 border-amber-500 text-amber-900 ring-1 ring-amber-400", iconClass: "text-amber-700" },
  WORD_BANK: { icon: Blocks, activeClass: "bg-orange-50 border-orange-400 text-orange-800 ring-1 ring-orange-300", iconClass: "text-orange-600" },
  WORD_BANK_PICTURE: { icon: Images, activeClass: "bg-orange-50 border-orange-500 text-orange-900 ring-1 ring-orange-400", iconClass: "text-orange-700" },
  WORD_BANK_PASSAGE: { icon: AlignLeft, activeClass: "bg-orange-50 border-orange-600 text-orange-950 ring-1 ring-orange-500", iconClass: "text-orange-800" },
  LISTENING_FORM_COMPLETION: { icon: ClipboardList, activeClass: "bg-violet-50 border-violet-500 text-violet-900 ring-1 ring-violet-400", iconClass: "text-violet-700" },
  SENTENCE_BUILDING: { icon: ListOrdered, activeClass: "bg-cyan-50 border-cyan-400 text-cyan-800 ring-1 ring-cyan-300", iconClass: "text-cyan-600" },
  LETTER_SCRAMBLE: { icon: Shuffle, activeClass: "bg-cyan-50 border-cyan-500 text-cyan-900 ring-1 ring-cyan-400", iconClass: "text-cyan-700" },
  ESSAY: { icon: FileText, activeClass: "bg-purple-50 border-purple-400 text-purple-800 ring-1 ring-purple-300", iconClass: "text-purple-600" },
  SPEAKING: { icon: Mic, activeClass: "bg-rose-50 border-rose-400 text-rose-800 ring-1 ring-rose-300", iconClass: "text-rose-600" },
  LISTENING_AUDIO_SUBMISSION: { icon: Headphones, activeClass: "bg-sky-50 border-sky-400 text-sky-800 ring-1 ring-sky-300", iconClass: "text-sky-600" },
  LISTENING_FILL_IN_BLANK: { icon: PenLine, activeClass: "bg-violet-50 border-violet-400 text-violet-800 ring-1 ring-violet-300", iconClass: "text-violet-600" }
};

/** V143 — style riêng cho khối audio theo từng kind cần audio (thay chuỗi ternary lồng nhau trước đây). */
const AUDIO_SECTION_STYLE: Partial<Record<UiQuestionKind, { box: string; title: string; icon: typeof Volume2; icon2: string }>> = {
  VOICE_MULTIPLE_CHOICE: { box: "bg-blue-50/40 border-blue-200", title: "text-blue-900", icon: Volume2, icon2: "text-blue-600" },
  VOICE_PICTURE_CHOICE: { box: "bg-indigo-50/40 border-indigo-200", title: "text-indigo-900", icon: ImageIcon, icon2: "text-indigo-600" },
  LISTENING_FILL_IN_BLANK: { box: "bg-violet-50/40 border-violet-200", title: "text-violet-900", icon: PenLine, icon2: "text-violet-600" },
  LISTENING_AUDIO_SUBMISSION: { box: "bg-sky-50/40 border-sky-200", title: "text-sky-900", icon: Headphones, icon2: "text-sky-600" },
  LISTENING_FORM_COMPLETION: { box: "bg-violet-50/40 border-violet-200", title: "text-violet-900", icon: ClipboardList, icon2: "text-violet-600" }
};

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-05 — pane "Xem trước" dùng ĐÚNG 1 ví dụ
 * tĩnh cố định cho mỗi kind (không dựng lại từ state form đang gõ như bản trước) — GV chỉ cần hình
 * dung hình dạng/kiểu tương tác của loại câu hỏi đang chọn, không cần preview phản ánh đúng nội dung
 * thật đang soạn dở. Build bằng QuestionPreview (component dùng chung với ExerciseStudentPreviewModal.tsx)
 * nên luôn khớp đúng cách học sinh thật sự nhìn thấy.
 */
function mockChoices(labels: string[]): ExerciseQuestionResponse["choices"] {
  return labels.map((content, i) => ({ id: i, choiceLabel: String.fromCharCode(65 + i), content, imageUrl: null, displayOrder: i + 1 }));
}
const MOCK_BASE: Omit<ExerciseQuestionResponse, "questionType" | "questionContent" | "choices" | "skill" | "audioUrl" | "referencePassage" | "structuredContent" | "imageUrl"> = {
  id: 0,
  exerciseId: 0,
  questionId: 0,
  displayOrder: 1,
  points: 1,
  groupKey: null
};
const MOCK_PREVIEW_QUESTIONS: Record<UiQuestionKind, ExerciseQuestionResponse> = {
  MULTIPLE_CHOICE: { ...MOCK_BASE, questionType: "MULTIPLE_CHOICE", questionContent: "What is the capital of Vietnam?", choices: mockChoices(["Hà Nội", "Hồ Chí Minh", "Đà Nẵng", "Huế"]), skill: null, audioUrl: null, referencePassage: null, structuredContent: null, imageUrl: null },
  VOICE_MULTIPLE_CHOICE: { ...MOCK_BASE, questionType: "MULTIPLE_CHOICE", questionContent: "Listen to the audio and choose the correct answer.", choices: mockChoices(["A library", "A hospital", "A supermarket", "A school"]), skill: "LISTENING", audioUrl: null, referencePassage: null, structuredContent: null, imageUrl: null },
  VOICE_PICTURE_CHOICE: { ...MOCK_BASE, questionType: "MULTIPLE_CHOICE", questionContent: "Listen to the audio and choose the correct picture.", choices: mockChoices(["🖼 Hình 1", "🖼 Hình 2", "🖼 Hình 3", "🖼 Hình 4"]), skill: "LISTENING", audioUrl: null, referencePassage: null, structuredContent: null, imageUrl: null },
  INLINE_CHOICE: { ...MOCK_BASE, questionType: "MULTIPLE_CHOICE", questionContent: "She ___ to school every day.", choices: mockChoices(["goes", "go"]), skill: null, audioUrl: null, referencePassage: null, structuredContent: null, imageUrl: null },
  MULTIPLE_ANSWER: { ...MOCK_BASE, questionType: "MULTIPLE_ANSWER", questionContent: "Which of the following are fruits? (chọn tất cả đáp án đúng)", choices: mockChoices(["Apple", "Carrot", "Banana", "Potato"]), skill: null, audioUrl: null, referencePassage: null, structuredContent: null, imageUrl: null },
  TRUE_FALSE: { ...MOCK_BASE, questionType: "TRUE_FALSE", questionContent: "The Earth is flat.", choices: mockChoices(["Đúng", "Sai"]), skill: null, audioUrl: null, referencePassage: null, structuredContent: null, imageUrl: null },
  FILL_IN_BLANK: { ...MOCK_BASE, questionType: "FILL_IN_BLANK", questionContent: "The cat sat on the ___.", choices: [], skill: null, audioUrl: null, referencePassage: null, structuredContent: null, imageUrl: null },
  FILL_IN_BLANK_PICTURE: { ...MOCK_BASE, questionType: "FILL_IN_BLANK", questionContent: "This is a picture of a ___.", choices: [], skill: null, audioUrl: null, referencePassage: null, structuredContent: null, imageUrl: null },
  LISTENING_FILL_IN_BLANK: { ...MOCK_BASE, questionType: "FILL_IN_BLANK", questionContent: "Listen to the audio and fill in the blank: I have two ___.", choices: [], skill: "LISTENING", audioUrl: null, referencePassage: null, structuredContent: null, imageUrl: null },
  WORD_BANK: { ...MOCK_BASE, questionType: "WORD_BANK", questionContent: "I ___ to school ___ bus every day.", choices: [], skill: null, audioUrl: null, referencePassage: null, structuredContent: { blanks: ["go", "by"] }, imageUrl: null },
  WORD_BANK_PICTURE: { ...MOCK_BASE, questionType: "WORD_BANK", questionContent: "Look at the picture and write the correct word: This is a ___.", choices: [], skill: null, audioUrl: null, referencePassage: null, structuredContent: { blanks: ["cat"], wordBankOptions: ["cat", "dog", "bird"] }, imageUrl: null },
  // Gõ tay tự do — minh hoạ đúng tính năng "cho phép chỗ trống để trống" (VD mạo từ không bắt buộc).
  WORD_BANK_PASSAGE: { ...MOCK_BASE, questionType: "WORD_BANK", questionContent: "She is ___ doctor. I like ___ music she plays.", choices: [], skill: null, audioUrl: null, referencePassage: null, structuredContent: { blanks: ["a", ""], inputMode: "text" }, imageUrl: null },
  // format="form" — minh hoạ **chữ đậm** + 1 ô nhận nhiều đáp án cách nhau "/".
  LISTENING_FORM_COMPLETION: {
    ...MOCK_BASE,
    questionType: "WORD_BANK",
    questionContent: "**Hotel Booking Form**\nGuest name: John ___\nCheck-in time: ___",
    choices: [],
    skill: "LISTENING",
    audioUrl: null,
    referencePassage: "Nghe audio rồi điền thông tin vào phiếu dưới đây.",
    structuredContent: { blanks: ["Smith", "9:30/nine thirty"], inputMode: "text", format: "form" },
    imageUrl: null
  },
  SENTENCE_BUILDING: { ...MOCK_BASE, questionType: "SENTENCE_BUILDING", questionContent: "Sắp xếp các khối từ dưới đây thành câu hoàn chỉnh:", choices: [], skill: null, audioUrl: null, referencePassage: null, structuredContent: { chunks: ["This", "is", "a", "pen"] }, imageUrl: null },
  LETTER_SCRAMBLE: { ...MOCK_BASE, questionType: "SENTENCE_BUILDING", questionContent: "Sắp xếp các chữ cái dưới đây thành từ đúng:", choices: [], skill: null, audioUrl: null, referencePassage: null, structuredContent: { chunks: ["c", "a", "t"] }, imageUrl: null },
  ESSAY: { ...MOCK_BASE, questionType: "ESSAY", questionContent: "Write an essay about your favorite hobby (150–200 words).", choices: [], skill: null, audioUrl: null, referencePassage: null, structuredContent: null, imageUrl: null },
  SPEAKING: { ...MOCK_BASE, questionType: "SPEAKING", questionContent: "Read the following sentence aloud: \"The weather today is sunny and warm.\"", choices: [], skill: "SPEAKING", audioUrl: null, referencePassage: null, structuredContent: null, imageUrl: null },
  LISTENING_AUDIO_SUBMISSION: { ...MOCK_BASE, questionType: "SPEAKING", questionContent: "Listen to the question and record your answer.", choices: [], skill: "LISTENING", audioUrl: null, referencePassage: null, structuredContent: null, imageUrl: null }
};

/**
 * V143 — audio TÙY CHỌN cho WORD_BANK/SENTENCE_BUILDING (khác khối audio bắt buộc ở AUDIO_SECTION_STYLE
 * — không đổi kind/questionType, chỉ đính kèm thêm nếu GV thật sự tải file lên).
 */
function OptionalAudioFields({
  audioUrl,
  setAudioUrl,
  transcript,
  setTranscript,
  label,
  /** Bổ sung 2026-09-25 (đã xác nhận với người dùng) — chỉ hiện ở kind THẬT SỰ dùng field này cho mục
   * đích khác ngoài phát âm khi không có audio (hiện tại chỉ FILL_IN_BLANK): giải thích thêm rằng "Tiêu
   * đề / Đoạn văn tham chiếu" hiển thị phía trên cả nhóm câu hỏi trong bài làm của học sinh. */
  transcriptHint
}: {
  audioUrl: string;
  setAudioUrl: (v: string) => void;
  transcript: string;
  setTranscript: (v: string) => void;
  label: string;
  transcriptHint?: string;
}) {
  const { t } = useTranslation("lms-question-authoring");
  // Bổ sung 2026-09-25 (đã xác nhận với người dùng) — field này dùng chung cho 2 mục đích khác hẳn
  // nhau tùy có audio hay không (referencePassage): có audio = transcript đọc trong file, hiển thị
  // cho GV kiểm tra khi chấm; KHÔNG có audio (đa số trường hợp thực tế ở FILL_IN_BLANK) = tiêu đề/đoạn
  // văn tham chiếu hiển thị phía trên cả nhóm câu hỏi (xem GridQuestionBuilder import). Trước đây nhãn
  // luôn cố định "Ghi chú phát âm/Transcript" khiến GV hiểu lầm khi dùng cho trường hợp thứ 2 — đổi
  // nhãn/placeholder SỐNG theo audioUrl để khớp đúng ý nghĩa thật đang lưu.
  const hasAudio = !!audioUrl.trim();
  return (
    <div className="bg-white/70 p-3 rounded-lg border border-slate-200 space-y-2">
      <span className="text-[13px] font-bold text-slate-500 uppercase tracking-wider">{label}</span>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        <div>
          <label className="block font-bold text-slate-600 mb-1 text-[13px] uppercase">{t("common.audioFileLabel")}</label>
          <FileUploadField value={audioUrl} onChange={setAudioUrl} onUpload={(file) => uploadMedia(file, "LMS_QUESTION")} accept="audio/*" placeholder={t("common.chooseAudioFile")} />
        </div>
        <div>
          <label className="block font-bold text-slate-600 mb-1 text-[13px] uppercase">
            {hasAudio ? t("common.transcriptLabel") : t("questionEditorForm.referencePassageLabel")}
          </label>
          {/* Fix bug thật (2026-09-08, đã xác nhận với người dùng) — <input> 1 dòng xoá sạch \n khi dán
              transcript nhiều dòng, mirror ListeningGroupBuilder.tsx đã sửa cùng đợt. */}
          <textarea
            value={transcript}
            onChange={(e) => setTranscript(e.target.value)}
            placeholder={hasAudio ? t("common.transcriptPlaceholder") : t("questionEditorForm.referencePassagePlaceholder")}
            rows={3}
            className={inputClass}
          />
          {!hasAudio && transcriptHint && <p className="text-[13px] text-slate-400 mt-1">{transcriptHint}</p>}
        </div>
      </div>
    </div>
  );
}

function toKind(question?: QuestionResponse): UiQuestionKind {
  if (!question) return "MULTIPLE_CHOICE";
  // Gắn lại 2026-10-05 — kiểm TRƯỚC nhánh MULTIPLE_CHOICE/choices.length===2 ở dưới, nếu không
  // TRUE_FALSE (luôn đúng 2 lựa chọn) sẽ bị nhận nhầm thành INLINE_CHOICE.
  if (question.questionType === "MULTIPLE_ANSWER") return "MULTIPLE_ANSWER";
  if (question.questionType === "TRUE_FALSE") return "TRUE_FALSE";
  if (question.questionType === "ESSAY") return "ESSAY";
  if (question.questionType === "SPEAKING") return question.skill === "LISTENING" ? "LISTENING_AUDIO_SUBMISSION" : "SPEAKING";
  if (question.questionType === "FILL_IN_BLANK") return question.skill === "LISTENING" ? "LISTENING_FILL_IN_BLANK" : question.imageUrl ? "FILL_IN_BLANK_PICTURE" : "FILL_IN_BLANK";
  if (question.questionType === "WORD_BANK") {
    if (question.structuredContent?.format === "form") return "LISTENING_FORM_COMPLETION";
    if (question.structuredContent?.inputMode === "text") return "WORD_BANK_PASSAGE";
    return question.imageUrl ? "WORD_BANK_PICTURE" : "WORD_BANK";
  }
  if (question.questionType === "SENTENCE_BUILDING") return question.imageUrl ? "LETTER_SCRAMBLE" : "SENTENCE_BUILDING";
  if (question.questionType === "MULTIPLE_CHOICE" && question.skill !== "LISTENING" && question.choices?.length === 2) return "INLINE_CHOICE";
  if (question.questionType === "MULTIPLE_CHOICE" && question.skill === "LISTENING" && question.choices?.some((c) => c.imageUrl)) return "VOICE_PICTURE_CHOICE";
  return question.skill === "LISTENING" ? "VOICE_MULTIPLE_CHOICE" : "MULTIPLE_CHOICE";
}

interface QuestionEditorFormProps {
  /** Generic legacy mode cho trang quản lý ngân hàng độc lập. */
  questionBankId?: number;
  /** V75: Teacher flow theo Đề — backend tự resolve ngân hàng nội bộ. */
  examId?: number;
  /** Truyền vào để chuyển form sang chế độ Sửa — loại câu hỏi không sửa được sau khi tạo. */
  existingQuestion?: QuestionResponse;
  /**
   * V77 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04) — giới hạn loại câu hỏi được
   * chọn, dùng khi Đề thuộc Giáo viên nước ngoài (chỉ cho soạn "Trắc nghiệm Voice" = audio bài nghe).
   * Bỏ trống = mọi loại (mặc định, dành cho GV Việt Nam).
   */
  allowedKinds?: UiQuestionKind[];
  onCreated: (question: QuestionResponse) => void;
  onCancel: () => void;
}

/** UC-40 Main Flow bước 1: soạn/sửa câu hỏi theo Đề (examId) hoặc generic legacy bank (questionBankId). */
export default function QuestionEditorForm({ questionBankId, examId, existingQuestion, allowedKinds, onCreated, onCancel }: QuestionEditorFormProps) {
  const { t } = useTranslation("lms-question-authoring");
  const isEditing = !!existingQuestion;
  const visibleKinds = allowedKinds ?? (Object.keys(kindMeta) as UiQuestionKind[]);
  const [kind, setKind] = useState<UiQuestionKind>(
    existingQuestion ? toKind(existingQuestion) : visibleKinds[0] ?? "MULTIPLE_CHOICE"
  );
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-05 — kind-picker đổi sang dạng accordion
  // (chỉ 1 Loại cha mở tại 1 thời điểm) để đỡ tốn diện tích, mặc định THU GỌN hết khi mở form mới.
  // Khi sửa câu hỏi có sẵn, mở sẵn đúng Loại cha chứa `kind` hiện tại để GV thấy nút đang chọn.
  const [expandedGroupKey, setExpandedGroupKey] = useState<string | null>(
    existingQuestion ? KIND_GROUPS.find((g) => g.kinds.includes(kind))?.labelKey ?? null : null
  );
  const [content, setContent] = useState(existingQuestion?.content ?? "");
  const [difficulty, setDifficulty] = useState<QuestionDifficulty>(existingQuestion?.difficulty ?? "MEDIUM");
  const [explanation, setExplanation] = useState(existingQuestion?.explanation ?? "");

  // Trắc nghiệm / Trắc nghiệm Voice / Chọn từ trong câu: 4 lựa chọn (2 với Chọn từ trong câu), 1 đáp án đúng.
  const [options, setOptions] = useState<string[]>(
    existingQuestion?.choices?.length
      ? existingQuestion.choices.map((c) => c.content)
      : toKind(existingQuestion) === "INLINE_CHOICE"
        ? ["", ""]
        : toKind(existingQuestion) === "TRUE_FALSE"
          ? [t("questionEditorForm.trueFalseTrueLabel"), t("questionEditorForm.trueFalseFalseLabel")]
          : ["", "", "", ""]
  );
  const [correctIndex, setCorrectIndex] = useState<number>(existingQuestion?.choices?.findIndex((c) => c.isCorrect) ?? 0);
  // Gắn lại 2026-10-05 — riêng cho MULTIPLE_ANSWER (cho phép NHIỀU đáp án đúng, khác correctIndex ở
  // trên chỉ giữ ĐÚNG 1 chỉ số cho mọi kind trắc nghiệm khác).
  const [correctIndices, setCorrectIndices] = useState<Set<number>>(
    new Set(
      existingQuestion?.choices?.length
        ? existingQuestion.choices.map((c, i) => (c.isCorrect ? i : -1)).filter((i) => i >= 0)
        : [0, 1]
    )
  );

  // V143: Nghe chọn hình — 1 ảnh riêng cho mỗi lựa chọn (song song với `options`, dùng làm chú thích
  // tuỳ chọn khi ở kind này). Rỗng/không dùng ở mọi kind khác.
  const [choiceImageUrls, setChoiceImageUrls] = useState<string[]>(
    existingQuestion?.choices?.length ? existingQuestion.choices.map((c) => c.imageUrl ?? "") : ["", "", "", ""]
  );

  // Trắc nghiệm Voice / Nghe & nộp audio: file audio + transcript (referencePassage).
  const [audioUrl, setAudioUrl] = useState(existingQuestion?.audioUrl ?? "");
  const [transcript, setTranscript] = useState(existingQuestion?.referencePassage ?? "");

  // Điền từ: BE so khớp case-insensitive + trim khi tự chấm (V54). Hỗ trợ NHIỀU đáp án đúng cho
  // cùng 1 chỗ trống, phân tách bằng dấu "/" (VD "go/goes") — bổ sung ngoài SDD gốc, đã xác nhận
  // với người dùng 2026-09-17. Cố tình KHÔNG dùng "|" vì đã mang nghĩa khác (danh sách theo thứ tự
  // cho nhiều chỗ trống ở WORD_BANK/SENTENCE_BUILDING).
  const [correctAnswerText, setCorrectAnswerText] = useState(existingQuestion?.correctAnswerText ?? "");

  // Tự luận: ảnh/tài liệu scan đề bài (imageUrl).
  const [imageUrl, setImageUrl] = useState(existingQuestion?.imageUrl ?? "");

  // Speaking: từ khóa/âm vị trọng điểm (dùng chung field referencePassage với Voice — 2 loại không cùng hiện 1 lúc).
  const [phoneticKeywords, setPhoneticKeywords] = useState(existingQuestion?.referencePassage ?? "");

  // V78: Điền từ - Hộp từ vựng — đáp án đúng theo ĐÚNG thứ tự chỗ trống trong content.
  const [wordBankBlanks, setWordBankBlanks] = useState<string[]>(
    existingQuestion?.structuredContent?.blanks?.length ? existingQuestion.structuredContent.blanks : ["", ""]
  );
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — hộp từ vựng TÁCH RIÊNG khỏi
  // wordBankBlanks (đáp án đúng), cho phép thêm từ nhiễu (VD hộp có 7 từ nhưng chỉ dùng hết 5 chỗ
  // trống). Để trống = giữ hành vi cũ (hộp từ = chính tập đáp án).
  const [wordBankOptions, setWordBankOptions] = useState<string[]>(existingQuestion?.structuredContent?.wordBankOptions ?? []);
  // V78: Sắp xếp câu — khối từ/cụm theo ĐÚNG thứ tự câu hoàn chỉnh, FE học sinh sẽ xáo trộn lúc hiển thị.
  const [sentenceChunks, setSentenceChunks] = useState<string[]>(
    existingQuestion?.structuredContent?.chunks?.length ? existingQuestion.structuredContent.chunks : ["", "", ""]
  );

  /**
   * Key Grammar (filter 2, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22) — gắn vào
   * CHÍNH câu hỏi (set 1 lần, dùng chung cho mọi lượt giao Bài chứa câu hỏi này sau này), KHÔNG phải
   * theo Bài hay lượt giao — xem Javadoc Question#keyGrammar. Chỉ hiện khi đang SỬA (existingQuestion,
   * options cần questionId có sẵn) VÀ kind="ESSAY". Chọn tay tối đa 3 mã, cùng 1 kiểu UI cho mọi Khối
   * 6-8 (Khối 9 không có từ điển, options trả về rỗng).
   */
  const [keyGrammarIds, setKeyGrammarIds] = useState<string[]>(existingQuestion?.keyGrammar ?? []);
  const [keyGrammarOptions, setKeyGrammarOptions] = useState<KeyGrammarStructureResponse[]>([]);
  const [loadingKeyGrammarOptions, setLoadingKeyGrammarOptions] = useState(false);

  useEffect(() => {
    if (kind !== "ESSAY" || !existingQuestion) {
      setKeyGrammarOptions([]);
      return;
    }
    setLoadingKeyGrammarOptions(true);
    const request = examId
      ? listExamQuestionKeyGrammarOptions(examId, existingQuestion.id)
      : listQuestionKeyGrammarOptions(existingQuestion.id);
    request
      .then(setKeyGrammarOptions)
      .catch(() => setKeyGrammarOptions([]))
      .finally(() => setLoadingKeyGrammarOptions(false));
  }, [kind, examId, existingQuestion]);

  const toggleKeyGrammarId = (id: string) => {
    setKeyGrammarIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : prev.length >= 3 ? prev : [...prev, id]
    );
  };

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Dòng báo lỗi nằm đầu form, trong khi nút Lưu ở cuối modal (form dài, phải cuộn) — không tự cuộn
  // thì GV bấm Lưu thất bại mà không thấy lỗi, tưởng đã lưu (QA 2026-09-30). Lỗi câu bị khoá dùng popup riêng.
  // block "center" thay vì "nearest"/"start" — căn mép trên thì bị header sticky của Modal che mất.
  const errorRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (error) errorRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [error]);

  /** Chuyển loại: reset số lựa chọn cho đúng (INLINE_CHOICE/TRUE_FALSE=2, MULTIPLE_CHOICE/VOICE/MULTIPLE_ANSWER=4 mặc định) khi tạo mới. */
  const handleSelectKind = (value: UiQuestionKind) => {
    setKind(value);
    if (isEditing) return;
    // Gắn lại 2026-10-05 — TRUE_FALSE luôn đúng 2 lựa chọn CỐ ĐỊNH "Đúng"/"Sai", không cho sửa chữ.
    if (value === "TRUE_FALSE") {
      setOptions([t("questionEditorForm.trueFalseTrueLabel"), t("questionEditorForm.trueFalseFalseLabel")]);
      setChoiceImageUrls(["", ""]);
      setCorrectIndex(0);
    } else if (value === "INLINE_CHOICE" && options.length !== 2) {
      setOptions(["", ""]);
      setChoiceImageUrls(["", ""]);
      setCorrectIndex(0);
    } else if ((value === "MULTIPLE_CHOICE" || value === "VOICE_MULTIPLE_CHOICE" || value === "VOICE_PICTURE_CHOICE" || value === "MULTIPLE_ANSWER") && options.length !== 4) {
      setOptions(["", "", "", ""]);
      setChoiceImageUrls(["", "", "", ""]);
      setCorrectIndex(0);
      setCorrectIndices(new Set([0, 1]));
    }
  };

  /**
   * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) — Trắc nghiệm/Trắc nghiệm Voice mặc
   * định 4 đáp án nhưng cho thêm/bớt (đề nghe có thể cần nhiều hơn 4 lựa chọn) — KHÔNG áp dụng cho
   * INLINE_CHOICE (cơ chế chọn từ trong câu luôn đúng 2 lựa chọn, không đổi được). Backend đã nhận số
   * lượng choices tuỳ ý từ trước (không có ràng buộc @Size) nên chỉ cần mở khoá ở FE.
   */
  const MAX_CHOICES = 8;
  const handleAddOption = () => {
    if (options.length >= MAX_CHOICES) return;
    setOptions((prev) => [...prev, ""]);
    setChoiceImageUrls((prev) => [...prev, ""]);
  };
  const handleRemoveOption = (idx: number) => {
    if (options.length <= 2) return;
    setOptions((prev) => prev.filter((_, i) => i !== idx));
    setChoiceImageUrls((prev) => prev.filter((_, i) => i !== idx));
    setCorrectIndex((prev) => (prev === idx ? 0 : prev > idx ? prev - 1 : prev));
    setCorrectIndices((prev) => {
      const next = new Set<number>();
      prev.forEach((i) => {
        if (i < idx) next.add(i);
        else if (i > idx) next.add(i - 1);
      });
      return next;
    });
  };
  /** Gắn lại 2026-10-05 — toggle 1 lựa chọn đúng/sai, riêng cho MULTIPLE_ANSWER (cho phép NHIỀU đáp án đúng cùng lúc, khác correctIndex chỉ giữ 1). */
  const toggleCorrectIndex = (idx: number) => {
    setCorrectIndices((prev) => {
      const next = new Set(prev);
      if (next.has(idx)) next.delete(idx);
      else next.add(idx);
      return next;
    });
  };

  const isVoiceOrListeningAudio =
    kind === "VOICE_MULTIPLE_CHOICE" || kind === "VOICE_PICTURE_CHOICE" || kind === "LISTENING_AUDIO_SUBMISSION" || kind === "LISTENING_FILL_IN_BLANK" || kind === "LISTENING_FORM_COMPLETION";
  /**
   * V143 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23) — Điền từ - Hộp từ vựng/Sắp
   * xếp câu cho phép KÈM audio nghe TÙY CHỌN (khác isVoiceOrListeningAudio ở trên — audio KHÔNG bắt
   * buộc, không đổi questionType/kind riêng, chỉ set thêm skill=LISTENING lúc lưu nếu có audio).
   */
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-10 — fix khoảng trống thật: câu FILL_IN_BLANK
  // thường (VD tạo ra từ nhóm DIEN_TU_NHOM import Excel không điền "URL Audio") không có CÁCH NÀO gắn
  // audio/transcript sau khi tạo — form sửa chỉ hiện khối audio bắt buộc (isVoiceOrListeningAudio) cho
  // kind "Nghe điền từ" (LISTENING_FILL_IN_BLANK), còn "Điền từ" thường thì không có field nào cả. Thêm
  // FILL_IN_BLANK vào supportsOptionalAudio (mirror WORD_BANK) — audio TÙY CHỌN, có thì tự gắn
  // skill=LISTENING lúc lưu (giống các kind khác), không đổi questionType/kind hiển thị lúc đang sửa.
  const supportsOptionalAudio = kind === "WORD_BANK" || kind === "WORD_BANK_PICTURE" || kind === "SENTENCE_BUILDING" || kind === "LETTER_SCRAMBLE" || kind === "FILL_IN_BLANK";
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — ảnh minh họa CHỈ có ở các kind riêng
  // WORD_BANK_PICTURE/LETTER_SCRAMBLE (khớp Ex.3/Ex.1 đề mẫu) — WORD_BANK/SENTENCE_BUILDING gốc GIỮ
  // NGUYÊN không đổi, không có field này (đã xác nhận với người dùng: tách kind riêng, không gộp thêm
  // field tùy chọn vào chung 2 kind cũ). FILL_IN_BLANK_PICTURE bổ sung 2026-08-28 theo đúng pattern này
  // — mỗi câu FILL_IN_BLANK có ảnh minh họa RIÊNG (khác WORD_BANK_PICTURE dùng 1 ảnh chung đánh số).
  const supportsImage = kind === "ESSAY" || kind === "WORD_BANK_PICTURE" || kind === "WORD_BANK_PASSAGE" || kind === "LETTER_SCRAMBLE" || kind === "FILL_IN_BLANK_PICTURE";
  // Gắn lại 2026-10-05 — MULTIPLE_ANSWER/TRUE_FALSE dùng chung cơ chế choices[] với MULTIPLE_CHOICE
  // (backend không ràng buộc số lượng isCorrect theo questionType, xem Javadoc Question.java), chỉ
  // khác ở cách đánh dấu đáp án đúng (nhiều đáp án vs đúng 1) — xem isMultiAnswer bên dưới.
  const isChoiceKind = kind === "MULTIPLE_CHOICE" || kind === "VOICE_MULTIPLE_CHOICE" || kind === "VOICE_PICTURE_CHOICE" || kind === "INLINE_CHOICE" || kind === "MULTIPLE_ANSWER" || kind === "TRUE_FALSE";
  const isMultiAnswer = kind === "MULTIPLE_ANSWER";
  // Dùng để build CreateQuestionRequest.questionType khi tạo mới VÀ để dựng câu hỏi mẫu cho pane xem
  // trước sống (xem previewQuestion bên dưới) — tách khỏi handleSubmit để không tính lại 2 lần.
  const resolvedQuestionType: QuestionType =
    kind === "VOICE_MULTIPLE_CHOICE" || kind === "VOICE_PICTURE_CHOICE" || kind === "INLINE_CHOICE"
      ? "MULTIPLE_CHOICE"
      : kind === "LISTENING_AUDIO_SUBMISSION"
        ? "SPEAKING"
        : kind === "LISTENING_FILL_IN_BLANK" || kind === "FILL_IN_BLANK_PICTURE"
          ? "FILL_IN_BLANK"
          : kind === "WORD_BANK_PICTURE" || kind === "WORD_BANK_PASSAGE" || kind === "LISTENING_FORM_COMPLETION"
            ? "WORD_BANK"
            : kind === "LETTER_SCRAMBLE"
              ? "SENTENCE_BUILDING"
              : kind;

  // Pane "Xem trước" dùng đúng 1 ví dụ mẫu cố định của `kind` đang chọn — xem MOCK_PREVIEW_QUESTIONS.
  const previewQuestion = MOCK_PREVIEW_QUESTIONS[kind];

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!content.trim()) {
      setError(t("questionEditorForm.errors.contentRequired"));
      return;
    }

    let choices: QuestionChoiceRequest[] | undefined;
    if (isChoiceKind) {
      if (kind === "VOICE_PICTURE_CHOICE") {
        if (choiceImageUrls.some((u) => !u.trim())) {
          setError(t("questionEditorForm.errors.choiceImagesRequired", { count: options.length }));
          return;
        }
      } else if (kind !== "TRUE_FALSE" && options.some((o) => !o.trim())) {
        setError(t("questionEditorForm.errors.fillAllOptions", { count: options.length }));
        return;
      }
      if (isMultiAnswer && correctIndices.size === 0) {
        setError(t("questionEditorForm.errors.multipleAnswerMinCorrect"));
        return;
      }
      choices = options.map((content_, i) => ({
        choiceLabel: String.fromCharCode(65 + i),
        // V143: ở dạng chọn hình, chú thích là TÙY CHỌN (content vẫn @NotBlank ở backend) — mặc định
        // dùng chính nhãn chữ cái (A/B/C/D) khi GV để trống, chỉ đóng vai trò alt-text.
        content: kind === "VOICE_PICTURE_CHOICE" ? content_.trim() || String.fromCharCode(65 + i) : content_.trim(),
        imageUrl: kind === "VOICE_PICTURE_CHOICE" ? choiceImageUrls[i]?.trim() || undefined : undefined,
        isCorrect: isMultiAnswer ? correctIndices.has(i) : i === correctIndex,
        displayOrder: i + 1
      }));
    }
    if (isVoiceOrListeningAudio && !audioUrl.trim()) {
      setError(
        kind === "VOICE_MULTIPLE_CHOICE"
          ? t("questionEditorForm.errors.audioRequiredVoice")
          : kind === "VOICE_PICTURE_CHOICE"
            ? t("questionEditorForm.errors.audioRequiredVoicePicture")
            : kind === "LISTENING_FILL_IN_BLANK"
              ? t("questionEditorForm.errors.audioRequiredListeningFillInBlank")
              : kind === "LISTENING_FORM_COMPLETION"
                ? t("questionEditorForm.errors.audioRequiredListeningFormCompletion")
                : t("questionEditorForm.errors.audioRequiredListeningSubmission")
      );
      return;
    }
    if ((kind === "FILL_IN_BLANK" || kind === "LISTENING_FILL_IN_BLANK" || kind === "FILL_IN_BLANK_PICTURE") && !correctAnswerText.trim()) {
      setError(t("questionEditorForm.errors.correctAnswerRequired"));
      return;
    }
    if ((kind === "WORD_BANK" || kind === "WORD_BANK_PICTURE") && wordBankBlanks.some((b) => !b.trim())) {
      setError(t("questionEditorForm.errors.wordBankBlanksRequired"));
      return;
    }
    // Gắn lại 2026-10-05 (đã xác nhận với người dùng) — WORD_BANK_PASSAGE/LISTENING_FORM_COMPLETION
    // CHO PHÉP chỗ trống để trống (VD mạo từ a/an/the/Ø không bắt buộc — xem Javadoc backend
    // QuestionImportService#mapToRequest, nhánh DIEN_TU_DOAN_VAN) — chỉ cần KHÔNG được để TRỐNG HẾT.
    if ((kind === "WORD_BANK_PASSAGE" || kind === "LISTENING_FORM_COMPLETION") && wordBankBlanks.every((b) => !b.trim())) {
      setError(t("questionEditorForm.errors.wordBankPassageBlanksRequired"));
      return;
    }
    if ((kind === "SENTENCE_BUILDING" || kind === "LETTER_SCRAMBLE") && sentenceChunks.filter((c) => c.trim()).length < 2) {
      setError(t("questionEditorForm.errors.sentenceBuildingMinChunks"));
      return;
    }

    const trimmedWordBankOptions = wordBankOptions.map((o) => o.trim()).filter(Boolean);
    // Fix bug thật (2026-10-03, đã xác nhận với người dùng) — sửa câu WORD_BANK (chỉ đổi điểm cũng bị) ghi đè
    // structuredContent chỉ còn { blanks }, làm MẤT inputMode="text"/format="form" (câu "Nghe điền phiếu thông
    // tin" và "Điền từ - Đoạn văn" import Excel) nên câu bị chuyển thành "Điền từ - Hộp từ vựng" dạng dropdown.
    // Khi sửa phải giữ nguyên các key form không hiển thị (inputMode, format, wordBox...), chỉ ghi đè key đang sửa.
    const preservedWordBankContent = isEditing && existingQuestion?.structuredContent ? { ...existingQuestion.structuredContent } : {};
    if ((kind === "WORD_BANK_PICTURE" || kind === "WORD_BANK_PASSAGE") && trimmedWordBankOptions.length === 0) {
      delete preservedWordBankContent.wordBankOptions;
    }
    // Gắn lại 2026-10-05 (đã xác nhận với người dùng) — WORD_BANK_PASSAGE/LISTENING_FORM_COMPLETION
    // CHO PHÉP chỗ trống rỗng (không filter/require như WORD_BANK/WORD_BANK_PICTURE), chỉ trim khoảng
    // trắng thừa. inputMode="text" cho cả 2 (gõ tay); format="form" CHỈ riêng LISTENING_FORM_COMPLETION.
    const structuredContent =
      kind === "WORD_BANK" || kind === "WORD_BANK_PICTURE" || kind === "WORD_BANK_PASSAGE" || kind === "LISTENING_FORM_COMPLETION"
        ? {
            ...preservedWordBankContent,
            blanks: wordBankBlanks.map((b) => b.trim()),
            ...((kind === "WORD_BANK_PICTURE" || kind === "WORD_BANK_PASSAGE") && trimmedWordBankOptions.length > 0 ? { wordBankOptions: trimmedWordBankOptions } : {}),
            ...(kind === "WORD_BANK_PASSAGE" || kind === "LISTENING_FORM_COMPLETION" ? { inputMode: "text" as const } : {}),
            ...(kind === "LISTENING_FORM_COMPLETION" ? { format: "form" as const } : {})
          }
        : kind === "SENTENCE_BUILDING" || kind === "LETTER_SCRAMBLE"
          ? { ...preservedWordBankContent, chunks: sentenceChunks.filter((c) => c.trim()).map((c) => c.trim()) }
          : // Các kind không có ô sửa structuredContent (Điền từ trong nhóm có wordBox, trắc nghiệm...) phải GỬI LẠI
            // nguyên giá trị cũ khi sửa, vì backend ghi đè nguyên cột theo request (null = xóa sạch).
            isEditing
            ? (existingQuestion?.structuredContent ?? undefined)
            : undefined;

    setSubmitting(true);
    try {
      let result: QuestionResponse;
      // V143: audio ở WORD_BANK/SENTENCE_BUILDING là TÙY CHỌN — chỉ set khi GV thật sự gắn file.
      const includeAudio = isVoiceOrListeningAudio || (supportsOptionalAudio && !!audioUrl.trim());
      const resolvedSkill = isVoiceOrListeningAudio || (supportsOptionalAudio && !!audioUrl.trim()) ? "LISTENING" : kind === "SPEAKING" ? "SPEAKING" : undefined;
      if (isEditing && existingQuestion) {
        // Fix bug thật (2026-10-03, đã xác nhận với người dùng) — backend (QuestionBankService#updateResolvedQuestion)
        // ghi đè TOÀN BỘ field theo request, nên field nào form không hiển thị cho kind này mà không gửi lại thì bị
        // xóa sạch mỗi lần sửa+lưu (kể cả chỉ đổi điểm): tags, ảnh/audio đề bài, đoạn văn tham chiếu của trắc nghiệm,
        // structuredContent (VD wordBox). Với mọi field form KHÔNG sửa được ở kind này → gửi lại giá trị đang có.
        const updateRequest = {
          content: content.trim(),
          audioUrl: includeAudio ? audioUrl.trim() || undefined : supportsOptionalAudio ? undefined : existingQuestion.audioUrl ?? undefined,
          imageUrl: supportsImage ? imageUrl.trim() || undefined : existingQuestion.imageUrl ?? undefined,
          tags: existingQuestion.tags ?? undefined,
          // Fix bug thật (2026-09-25, đã xác nhận với người dùng) — trước đây gate theo `includeAudio`
          // (chỉ true khi THẬT SỰ có audioUrl), nên FILL_IN_BLANK/WORD_BANK/SENTENCE_BUILDING/
          // LETTER_SCRAMBLE không audio mà có referencePassage (dùng làm tiêu đề/đoạn văn cho cả nhóm —
          // xem GridQuestionBuilder import) bị XÓA SẠCH referencePassage mỗi lần sửa+lưu câu hỏi đầu
          // nhóm qua form này, dù ô "Ghi chú phát âm/Transcript" trên UI vẫn đang hiện đúng nội dung.
          // Phải giữ transcript bất cứ khi nào field này còn hiện trên form (isVoiceOrListeningAudio
          // hoặc supportsOptionalAudio), không phụ thuộc audio có được gắn hay không.
          referencePassage:
            isVoiceOrListeningAudio || supportsOptionalAudio
              ? transcript.trim() || undefined
              : kind === "SPEAKING"
                ? phoneticKeywords.trim() || undefined
                : existingQuestion.referencePassage ?? undefined,
          explanation: explanation.trim() || undefined,
          correctAnswerText:
            kind === "FILL_IN_BLANK" || kind === "LISTENING_FILL_IN_BLANK" || kind === "FILL_IN_BLANK_PICTURE" ? correctAnswerText.trim() || undefined : undefined,
          structuredContent,
          choices,
          keyGrammarIds: kind === "ESSAY" ? (keyGrammarIds.length > 0 ? keyGrammarIds : null) : null
        };
        result = examId
          ? await updateExamQuestion(examId, existingQuestion.id, updateRequest)
          : await updateQuestion(existingQuestion.id, updateRequest);
      } else {
        const request: CreateExamQuestionRequest = {
          questionType: resolvedQuestionType,
          skill: resolvedSkill,
          difficulty,
          content: content.trim(),
          audioUrl: includeAudio ? audioUrl.trim() || undefined : undefined,
          imageUrl: supportsImage ? imageUrl.trim() || undefined : undefined,
          // Xem giải thích ở nhánh update phía trên — cùng 1 lỗi, cùng cách fix.
          referencePassage:
            isVoiceOrListeningAudio || supportsOptionalAudio
              ? transcript.trim() || undefined
              : kind === "SPEAKING"
                ? phoneticKeywords.trim() || undefined
                : undefined,
          explanation: explanation.trim() || undefined,
          correctAnswerText:
            kind === "FILL_IN_BLANK" || kind === "LISTENING_FILL_IN_BLANK" || kind === "FILL_IN_BLANK_PICTURE" ? correctAnswerText.trim() || undefined : undefined,
          structuredContent,
          choices
        };
        if (examId) {
          result = await createExamQuestion(examId, request);
        } else if (questionBankId) {
          const legacyRequest: CreateQuestionRequest = { ...request, questionBankId };
          result = await createQuestion(legacyRequest);
        } else {
          throw new Error(t("common.missingExamOrBankContext"));
        }
      }
      onCreated(result);
    } catch (err) {
      // QuestionLockedException trả 422 (GlobalExceptionHandler) — 400 là lỗi validate thường, không phải bị khoá.
      // Chỉ bắn banner nổi (không set error) — tránh báo trùng 2 chỗ.
      if (err instanceof ApiError && err.status === 422 && isEditing) {
        notifyError(t("questionEditorForm.errors.lockedAfterSubmission"));
      } else {
        setError(err instanceof ApiError ? err.message : t("questionEditorForm.errors.saveFailed"));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4 text-sm">
      <FloatingError message={error} onClose={() => setError(null)} />

      <div className="space-y-3">
        <label className={labelClass}>{t("questionEditorForm.kindLabel")}{isEditing && <span className="text-slate-400 font-normal"> {t("questionEditorForm.kindLockedHint")}</span>}</label>
        {/*
         * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-05 — cây "Loại cha → Kiểu con"
         * (KIND_GROUPS) dạng ACCORDION: bấm tiêu đề Loại cha mới xổ ra Kiểu con (chỉ 1 nhóm mở tại 1
         * thời điểm, xem expandedGroupKey), đỡ tốn diện tích hơn hẳn hiện hết mọi nhóm cùng lúc như
         * bản đầu. Loại cha nào không còn kiểu con nào khớp `visibleKinds` (đã lọc theo Nhóm kỹ năng ở
         * skillCategoryKinds.ts) thì ẩn hẳn.
         */}
        {KIND_GROUPS.map((group) => {
          const groupKinds = group.kinds.filter((k) => visibleKinds.includes(k));
          if (groupKinds.length === 0) return null;
          const isExpanded = expandedGroupKey === group.labelKey;
          const hasActiveKind = groupKinds.includes(kind);
          return (
            <div key={group.labelKey} className="border border-slate-200 rounded-xl overflow-hidden">
              <button
                type="button"
                onClick={() => setExpandedGroupKey(isExpanded ? null : group.labelKey)}
                className={`w-full flex items-center justify-between gap-2 px-3 py-2 text-left transition-colors ${
                  hasActiveKind ? "bg-brand-red/5" : "bg-slate-50 hover:bg-slate-100"
                }`}
              >
                <span className="text-[11px] font-extrabold uppercase tracking-wider text-slate-500 flex items-center gap-1.5">
                  {t(group.labelKey)}
                  {hasActiveKind && !isExpanded && (
                    <span className="normal-case font-bold text-brand-red">— {t(`questionKind.${kind}`)}</span>
                  )}
                </span>
                <ChevronDown className={`w-4 h-4 text-slate-400 shrink-0 transition-transform ${isExpanded ? "rotate-180" : ""}`} />
              </button>
              {isExpanded && (
                <div className="p-2.5 pt-2 border-t border-slate-200">
                  {/*
                   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-05 — fix bug thật phát
                   * hiện khi test ở "Soạn & giao đề" (panel hẹp hơn hẳn trang Ngân hàng câu hỏi): lưới
                   * cột CỐ ĐỊNH (trước đây grid-cols-4) làm nhóm 5 kiểu con (VD Trắc nghiệm sau khi gắn
                   * lại MULTIPLE_ANSWER/TRUE_FALSE) rớt 1 nút xuống dòng riêng, trống 3 ô — vỡ bố cục.
                   * Đổi sang flex-wrap + mỗi nút tự co giãn (flex-grow trong khoảng min/max-width) để
                   * LUÔN lấp đầy hết chiều rộng khung chứa dù đang ở panel hẹp hay trang rộng, không
                   * phụ thuộc đếm số lượng.
                   */}
                  <div className="flex flex-wrap gap-2">
                    {groupKinds.map((value) => {
                      const meta = kindMeta[value];
                      const Icon = meta.icon;
                      const active = kind === value;
                      return (
                        <button
                          key={value}
                          type="button"
                          disabled={isEditing}
                          onClick={() => handleSelectKind(value)}
                          className={`flex-1 basis-[110px] min-w-[110px] max-w-[180px] p-2.5 rounded-xl border font-bold flex flex-col items-center gap-1 transition-all disabled:cursor-not-allowed disabled:opacity-60 ${
                            active ? `${meta.activeClass} shadow-xs` : "bg-white border-slate-200 text-slate-600 hover:bg-slate-50"
                          }`}
                        >
                          <Icon className={`w-4 h-4 ${active ? "" : meta.iconClass}`} />
                          <span>{t(`questionKind.${value}`)}</span>
                        </button>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/*
       * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-05 — pane "Xem trước" dùng 1 ví dụ
       * TĨNH cố định theo `kind` đang chọn (MOCK_PREVIEW_QUESTIONS), KHÔNG dựng lại từ nội dung GV
       * đang gõ (đã thử bản SỐNG trước đó — GV phản hồi không cần, ô trống hiện "chưa nhập" nhìn như
       * lỗi). Tái dùng QuestionPreview (component dùng chung với ExerciseStudentPreviewModal.tsx) nên
       * hình dạng/kiểu tương tác luôn khớp đúng màn học sinh thật. Đặt NGAY sau kind-picker (thay vì
       * cuối form như bản đầu) — đã xác nhận với người dùng 2026-10-05: cuối form phải lướt hết các
       * field riêng theo loại mới thấy, dễ bị bỏ qua. Vì nội dung preview không phụ thuộc field nào
       * bên dưới (tĩnh theo kind), hiện ngay khi chọn xong loại là hợp lý nhất, không cần lướt.
       */}
      <div className="bg-slate-50/60 p-4 rounded-xl border border-slate-200 space-y-2">
        <div className="flex items-center gap-1.5 text-slate-500 font-bold uppercase tracking-wider text-[13px]">
          <Eye className="w-4 h-4" />
          <span>{t("questionEditorForm.previewSectionTitle")}</span>
        </div>
        <p className="text-[13px] text-slate-400">{t("questionEditorForm.previewSectionHint")}</p>
        {/* key={kind} — ép remount khi đổi loại, tránh state nội bộ (dropdown đã chọn, khối đã xếp...)
            của ví dụ loại CŨ còn sót lại khi hiện ví dụ loại MỚI (xem Javadoc WordBankPreview). */}
        <QuestionPreview key={kind} question={previewQuestion} displayNumber={1} revealAnswers />
      </div>

      {/* <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label className={labelClass}>{t("questionEditorForm.difficultyLabel")}</label>
          <Select value={difficulty} onChange={(e) => setDifficulty(e.target.value as QuestionDifficulty)} disabled={isEditing} className={`${inputClass} disabled:opacity-60 font-bold`}>
            {(["EASY", "MEDIUM", "HARD"] as QuestionDifficulty[]).map((value) => (
              <option key={value} value={value}>
                {t(`difficulty.${value}`)}
              </option>
            ))}
          </Select>
        </div>
      </div> */}

      <div>
        <label className={labelClass}>{t("questionEditorForm.contentLabel")}</label>
        <textarea
          required
          rows={3}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder={t("questionEditorForm.contentPlaceholder")}
          className={inputClass}
        />
      </div>

      {kind === "ESSAY" && !isEditing && (
        <p className="text-[12px] text-slate-400 italic">{t("questionEditorForm.keyGrammar.saveFirstHint")}</p>
      )}

      {kind === "ESSAY" && isEditing && (
        <div className="border-2 border-amber-200 bg-amber-50/60 rounded-xl p-3.5 space-y-2.5">
          <div className="flex items-center gap-2">
            <span className="text-[13px] font-extrabold uppercase tracking-wide text-slate-800">{t("questionEditorForm.keyGrammar.title")}</span>
            <span className="text-[12px] font-extrabold text-white bg-emerald-600 px-2 py-0.5 rounded-full">{t("questionEditorForm.keyGrammar.newBadge")}</span>
          </div>
          <p className="text-sm text-slate-600 leading-relaxed">{t("questionEditorForm.keyGrammar.description")}</p>
          {loadingKeyGrammarOptions ? (
            <p className="text-sm text-slate-500">{t("common.loading")}</p>
          ) : keyGrammarOptions.length === 0 ? (
            <p className="text-sm text-slate-400 italic">{t("questionEditorForm.keyGrammar.noOptions")}</p>
          ) : (
            <>
              <div className="flex flex-col gap-1.5 max-h-52 overflow-y-auto pr-1">
                {keyGrammarOptions.map((opt) => {
                  const checked = keyGrammarIds.includes(opt.id);
                  const disabled = !checked && keyGrammarIds.length >= 3;
                  return (
                    <label
                      key={opt.id}
                      className={`flex items-center gap-2.5 px-3 py-2 rounded-lg border text-sm font-semibold cursor-pointer ${
                        checked
                          ? "border-emerald-300 bg-emerald-50 text-slate-800"
                          : disabled
                            ? "border-slate-200 bg-slate-50 text-slate-300 cursor-not-allowed"
                            : "border-slate-200 bg-white text-slate-700 hover:bg-slate-50"
                      }`}
                    >
                      <input type="checkbox" checked={checked} disabled={disabled} onChange={() => toggleKeyGrammarId(opt.id)} className="shrink-0" />
                      <span>
                        {opt.name}
                        {opt.base && <span className="text-slate-400 font-normal"> ({t("questionEditorForm.keyGrammar.baseLabel")})</span>}
                      </span>
                    </label>
                  );
                })}
              </div>
              <p className="text-[13px] text-slate-500">{t("questionEditorForm.keyGrammar.selectedCount", { count: keyGrammarIds.length })}</p>
            </>
          )}
          <div className="bg-white border border-amber-200 rounded-lg px-3 py-2 text-[13px] leading-relaxed text-amber-900">
            {t("questionEditorForm.keyGrammar.consequenceNote")}
          </div>
        </div>
      )}

      {isVoiceOrListeningAudio && (
        <div className={`p-4 rounded-xl border space-y-3 ${AUDIO_SECTION_STYLE[kind]?.box ?? "bg-sky-50/40 border-sky-200"}`}>
          <div className={`flex items-center gap-1 font-bold uppercase tracking-wider text-[13px] ${AUDIO_SECTION_STYLE[kind]?.title ?? "text-sky-900"}`}>
            {(() => {
              const Icon = AUDIO_SECTION_STYLE[kind]?.icon ?? Headphones;
              return <Icon className={`w-4 h-4 ${AUDIO_SECTION_STYLE[kind]?.icon2 ?? "text-sky-600"}`} />;
            })()}
            <span>{t("questionEditorForm.audioSectionTitle")}</span>
          </div>
          {kind === "LISTENING_AUDIO_SUBMISSION" && (
            <p className="text-[13px] text-slate-400">{t("questionEditorForm.listeningAudioSubmissionHint")}</p>
          )}
          {kind === "LISTENING_FILL_IN_BLANK" && (
            <p className="text-[13px] text-slate-400">{t("questionEditorForm.listeningFillInBlankHint")}</p>
          )}
          {kind === "VOICE_PICTURE_CHOICE" && (
            <p className="text-[13px] text-slate-400">{t("questionEditorForm.voicePictureChoiceHint")}</p>
          )}
          {kind === "LISTENING_FORM_COMPLETION" && (
            <p className="text-[13px] text-slate-400">{t("questionEditorForm.listeningFormCompletionAudioHint")}</p>
          )}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div>
              <label className="block font-bold text-slate-600 mb-1 text-[13px] uppercase">{t("common.audioFileLabel")}</label>
              <FileUploadField value={audioUrl} onChange={setAudioUrl} onUpload={(file) => uploadMedia(file, "LMS_QUESTION")} accept="audio/*" placeholder={t("common.chooseAudioFile")} />
            </div>
            <div>
              {/* Nghe điền phiếu thông tin (bổ sung 2026-10-05, đã xác nhận với người dùng) — field này
                  tái dùng làm dòng HƯỚNG DẪN hiện phía trên phiếu (mirror cột Transcript ở Excel import),
                  KHÔNG phải ghi chú phát âm — đổi nhãn/placeholder riêng cho đúng ý nghĩa. */}
              <label className="block font-bold text-slate-600 mb-1 text-[13px] uppercase">
                {kind === "LISTENING_FORM_COMPLETION" ? t("questionEditorForm.listeningFormCompletionInstructionLabel") : t("common.transcriptLabel")}
              </label>
              {/* Fix bug thật (2026-09-08, đã xác nhận với người dùng) — <input> 1 dòng xoá sạch \n khi
                  dán transcript nhiều dòng, mirror ListeningGroupBuilder.tsx đã sửa cùng đợt. */}
              <textarea
                value={transcript}
                onChange={(e) => setTranscript(e.target.value)}
                placeholder={kind === "LISTENING_FORM_COMPLETION" ? t("questionEditorForm.listeningFormCompletionInstructionPlaceholder") : t("common.transcriptPlaceholder")}
                rows={3}
                className={inputClass}
              />
            </div>
          </div>
        </div>
      )}

      {isChoiceKind && (
        <div className="space-y-2 bg-slate-50 p-4 rounded-xl border border-slate-200">
          <div className="flex items-center justify-between mb-1">
            <span className="font-bold text-slate-700 uppercase tracking-wider text-[13px]">{t("questionEditorForm.choicesSectionTitle", { count: options.length })}</span>
            <span className="text-[13px] text-slate-400 font-bold">{isMultiAnswer ? t("questionEditorForm.multipleAnswerHint") : t("questionEditorForm.choicesHint")}</span>
          </div>
          {kind === "INLINE_CHOICE" && <p className="text-[13px] text-slate-400">{t("questionEditorForm.inlineChoiceHint")}</p>}
          {kind === "VOICE_PICTURE_CHOICE" && <p className="text-[13px] text-slate-400">{t("questionEditorForm.picChoiceHint")}</p>}
          {kind === "TRUE_FALSE" && <p className="text-[13px] text-slate-400">{t("questionEditorForm.trueFalseHint")}</p>}
          <div className="space-y-2">
            {options.map((opt, idx) =>
              kind === "VOICE_PICTURE_CHOICE" ? (
                <div key={idx} className="flex items-start gap-2 bg-white p-2.5 rounded-lg border border-slate-200">
                  <button
                    type="button"
                    onClick={() => setCorrectIndex(idx)}
                    className={`w-6 h-6 rounded-full border flex items-center justify-center font-bold shrink-0 text-[10px] transition-all mt-1 ${
                      correctIndex === idx ? "bg-emerald-500 border-emerald-500 text-white" : "bg-white border-slate-300 text-slate-400 hover:border-slate-400"
                    }`}
                  >
                    {correctIndex === idx ? <Check className="w-3.5 h-3.5 stroke-[3]" /> : String.fromCharCode(65 + idx)}
                  </button>
                  <div className="flex-1 space-y-1.5">
                    <FileUploadField
                      value={choiceImageUrls[idx] ?? ""}
                      onChange={(v) => setChoiceImageUrls((prev) => prev.map((u, i) => (i === idx ? v : u)))}
                      onUpload={(file) => uploadMedia(file, "LMS_QUESTION")}
                      accept="image/*"
                      placeholder={t("questionEditorForm.choiceImagePlaceholder", { letter: String.fromCharCode(65 + idx) })}
                    />
                    <input
                      value={opt}
                      onChange={(e) => setOptions((prev) => prev.map((o, i) => (i === idx ? e.target.value : o)))}
                      placeholder={t("questionEditorForm.choiceCaptionPlaceholder")}
                      className={`w-full ${inputClass}`}
                    />
                  </div>
                  {options.length > 2 && (
                    <button type="button" onClick={() => handleRemoveOption(idx)} className="text-slate-400 hover:text-rose-600 shrink-0 mt-1">
                      <X className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              ) : (
                <div key={idx} className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => (isMultiAnswer ? toggleCorrectIndex(idx) : setCorrectIndex(idx))}
                    className={`w-6 h-6 border flex items-center justify-center font-bold shrink-0 text-[10px] transition-all ${isMultiAnswer ? "rounded-md" : "rounded-full"} ${
                      (isMultiAnswer ? correctIndices.has(idx) : correctIndex === idx) ? "bg-emerald-500 border-emerald-500 text-white" : "bg-white border-slate-300 text-slate-400 hover:border-slate-400"
                    }`}
                  >
                    {(isMultiAnswer ? correctIndices.has(idx) : correctIndex === idx) ? <Check className="w-3.5 h-3.5 stroke-[3]" /> : String.fromCharCode(65 + idx)}
                  </button>
                  <input
                    required
                    disabled={kind === "TRUE_FALSE"}
                    value={opt}
                    onChange={(e) => setOptions((prev) => prev.map((o, i) => (i === idx ? e.target.value : o)))}
                    placeholder={t("common.answerOptionPlaceholder", { letter: String.fromCharCode(65 + idx) })}
                    className={`flex-1 ${inputClass} disabled:bg-slate-100 disabled:text-slate-500`}
                  />
                  {kind !== "INLINE_CHOICE" && kind !== "TRUE_FALSE" && options.length > 2 && (
                    <button type="button" onClick={() => handleRemoveOption(idx)} className="text-slate-400 hover:text-rose-600 shrink-0">
                      <X className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              )
            )}
          </div>
          {kind !== "INLINE_CHOICE" && kind !== "TRUE_FALSE" && options.length < MAX_CHOICES && (
            <Button type="button" variant="secondary" size="sm" onClick={handleAddOption}>
              {t("questionEditorForm.addOption")}
            </Button>
          )}
        </div>
      )}

      {kind === "WORD_BANK" && (
        <div className="bg-orange-50/40 p-4 rounded-xl border border-orange-200 space-y-3">
          <div className="text-orange-950 font-bold uppercase tracking-wider text-[13px] flex items-center gap-1">
            <Blocks className="w-4 h-4 text-orange-600" />
            <span>{t("questionEditorForm.wordBankSectionTitle")}</span>
          </div>
          {existingQuestion?.structuredContent?.format === "form" ? (
            <p className="text-[13px] text-amber-700 bg-amber-50 border border-amber-200 rounded-lg p-2">{t("questionEditorForm.formCompletionEditNote")}</p>
          ) : (
            <p className="text-[13px] text-slate-400">{t("questionEditorForm.wordBankHint")}</p>
          )}
          {/*
           * Bổ sung 2026-08-28 (đã xác nhận với người dùng) — cảnh báo CHỈ đặt ở WORD_BANK/
           * WORD_BANK_PICTURE (không đặt ở FILL_IN_BLANK) vì chỉ 2 kind này cho phép nhồi NHIỀU chỗ
           * trống/câu vào chung 1 Question (structuredContent.blanks) — đúng thứ đã gây lỗi thật (chấm
           * all-or-nothing + số câu dính nhau) khớp toàn bộ buổi debug trước đó. FILL_IN_BLANK chỉ có
           * đúng 1 correctAnswerText nên không có nguy cơ này, không cần cảnh báo.
           */}
          <p className="text-[13px] text-amber-700 bg-amber-50 border border-amber-200 rounded-lg p-2">{t("questionEditorForm.wordBankMultiSentenceWarning")}</p>
          {/* <OptionalAudioFields
            audioUrl={audioUrl}
            setAudioUrl={setAudioUrl}
            transcript={transcript}
            setTranscript={setTranscript}
            label={t("questionEditorForm.optionalAudioLabel")}
          /> */}
          <div className="space-y-2">
            {wordBankBlanks.map((b, idx) => (
              <div key={idx} className="flex items-center gap-2">
                <span className="text-[12px] font-bold text-slate-500 w-20 shrink-0">{t("questionEditorForm.blankLabel", { index: idx + 1 })}</span>
                <input
                  required
                  value={b}
                  onChange={(e) => setWordBankBlanks((prev) => prev.map((x, i) => (i === idx ? e.target.value : x)))}
                  placeholder={t("questionEditorForm.blankPlaceholder")}
                  className={`flex-1 ${inputClass}`}
                />
                {wordBankBlanks.length > 1 && (
                  <button
                    type="button"
                    onClick={() => setWordBankBlanks((prev) => prev.filter((_, i) => i !== idx))}
                    className="text-slate-400 hover:text-rose-600 shrink-0"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                )}
              </div>
            ))}
          </div>
          <Button type="button" variant="secondary" size="sm" onClick={() => setWordBankBlanks((prev) => [...prev, ""])}>
            {t("questionEditorForm.addBlank")}
          </Button>
        </div>
      )}

      {/*
       * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — kind RIÊNG, TÁCH HẲN khỏi
       * WORD_BANK ở trên (không gộp field tùy chọn vào chung 1 kind): khớp Ex.3 "Look at the pictures
       * and write the correct words from the box" — có ảnh minh họa + hộp từ vựng hiển thị cho học
       * sinh TÁCH RIÊNG khỏi đáp án đúng (cho phép từ nhiễu).
       */}
      {kind === "WORD_BANK_PICTURE" && (
        <div className="bg-orange-50/40 p-4 rounded-xl border border-orange-200 space-y-3">
          <div className="text-orange-950 font-bold uppercase tracking-wider text-[13px] flex items-center gap-1">
            <Images className="w-4 h-4 text-orange-700" />
            <span>{t("questionEditorForm.wordBankPictureSectionTitle")}</span>
          </div>
          <p className="text-[13px] text-slate-400">{t("questionEditorForm.wordBankPictureHint")}</p>
          <p className="text-[13px] text-amber-700 bg-amber-50 border border-amber-200 rounded-lg p-2">{t("questionEditorForm.wordBankMultiSentenceWarning")}</p>
          <div>
            <label className="block font-bold text-slate-600 mb-1 text-[13px] uppercase">{t("questionEditorForm.wordBankImageLabel")}</label>
            <FileUploadField
              value={imageUrl}
              onChange={setImageUrl}
              onUpload={(file) => uploadMedia(file, "LMS_QUESTION")}
              accept="image/*"
              placeholder={t("questionEditorForm.wordBankImagePlaceholder")}
            />
          </div>
          {/* <OptionalAudioFields
            audioUrl={audioUrl}
            setAudioUrl={setAudioUrl}
            transcript={transcript}
            setTranscript={setTranscript}
            label={t("questionEditorForm.optionalAudioLabel")}
          /> */}
          <div className="space-y-2">
            {wordBankBlanks.map((b, idx) => (
              <div key={idx} className="flex items-center gap-2">
                <span className="text-[12px] font-bold text-slate-500 w-20 shrink-0">{t("questionEditorForm.blankLabel", { index: idx + 1 })}</span>
                <input
                  required
                  value={b}
                  onChange={(e) => setWordBankBlanks((prev) => prev.map((x, i) => (i === idx ? e.target.value : x)))}
                  placeholder={t("questionEditorForm.blankPlaceholder")}
                  className={`flex-1 ${inputClass}`}
                />
                {wordBankBlanks.length > 1 && (
                  <button
                    type="button"
                    onClick={() => setWordBankBlanks((prev) => prev.filter((_, i) => i !== idx))}
                    className="text-slate-400 hover:text-rose-600 shrink-0"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                )}
              </div>
            ))}
          </div>
          <Button type="button" variant="secondary" size="sm" onClick={() => setWordBankBlanks((prev) => [...prev, ""])}>
            {t("questionEditorForm.addBlank")}
          </Button>

          <div className="pt-2 border-t border-orange-200 space-y-2">
            <p className="text-[13px] text-slate-400">{t("questionEditorForm.wordBankOptionsHint")}</p>
            {wordBankOptions.map((o, idx) => (
              <div key={idx} className="flex items-center gap-2">
                <input
                  value={o}
                  onChange={(e) => setWordBankOptions((prev) => prev.map((x, i) => (i === idx ? e.target.value : x)))}
                  placeholder={t("questionEditorForm.wordBankOptionPlaceholder")}
                  className={`flex-1 ${inputClass}`}
                />
                <button
                  type="button"
                  onClick={() => setWordBankOptions((prev) => prev.filter((_, i) => i !== idx))}
                  className="text-slate-400 hover:text-rose-600 shrink-0"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </div>
            ))}
            <Button type="button" variant="secondary" size="sm" onClick={() => setWordBankOptions((prev) => [...prev, ""])}>
              {t("questionEditorForm.addWordBankOption")}
            </Button>
          </div>
        </div>
      )}

      {/*
       * Gắn lại 2026-10-05 (đã xác nhận với người dùng — trước đó chỉ tạo được qua Excel import, kind
       * DIEN_TU_DOAN_VAN, xem Javadoc QuestionImportService) — "Đọc điền từ - Đoạn văn liền mạch": gõ
       * tay tự do thay vì chọn dropdown (structuredContent.inputMode="text"), CHO PHÉP 1 vài chỗ trống
       * để TRỐNG (VD bài điền mạo từ a/an/the/Ø — chỗ không cần mạo từ thì để trống ô, không gõ "Ø").
       */}
      {kind === "WORD_BANK_PASSAGE" && (
        <div className="bg-orange-50/40 p-4 rounded-xl border border-orange-200 space-y-3">
          <div className="text-orange-950 font-bold uppercase tracking-wider text-[13px] flex items-center gap-1">
            <AlignLeft className="w-4 h-4 text-orange-700" />
            <span>{t("questionEditorForm.wordBankPassageSectionTitle")}</span>
          </div>
          <p className="text-[13px] text-slate-400">{t("questionEditorForm.wordBankPassageHint")}</p>
          <p className="text-[13px] text-amber-700 bg-amber-50 border border-amber-200 rounded-lg p-2">{t("questionEditorForm.wordBankPassageAllowEmptyHint")}</p>
          <div>
            <label className="block font-bold text-slate-600 mb-1 text-[13px] uppercase">{t("questionEditorForm.wordBankImageLabel")}</label>
            <FileUploadField
              value={imageUrl}
              onChange={setImageUrl}
              onUpload={(file) => uploadMedia(file, "LMS_QUESTION")}
              accept="image/*"
              placeholder={t("questionEditorForm.wordBankImagePlaceholder")}
            />
          </div>
          <div className="space-y-2">
            {wordBankBlanks.map((b, idx) => (
              <div key={idx} className="flex items-center gap-2">
                <span className="text-[12px] font-bold text-slate-500 w-20 shrink-0">{t("questionEditorForm.blankLabel", { index: idx + 1 })}</span>
                <input
                  value={b}
                  onChange={(e) => setWordBankBlanks((prev) => prev.map((x, i) => (i === idx ? e.target.value : x)))}
                  placeholder={t("questionEditorForm.wordBankPassageBlankPlaceholder")}
                  className={`flex-1 ${inputClass}`}
                />
                {wordBankBlanks.length > 1 && (
                  <button
                    type="button"
                    onClick={() => setWordBankBlanks((prev) => prev.filter((_, i) => i !== idx))}
                    className="text-slate-400 hover:text-rose-600 shrink-0"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                )}
              </div>
            ))}
          </div>
          <Button type="button" variant="secondary" size="sm" onClick={() => setWordBankBlanks((prev) => [...prev, ""])}>
            {t("questionEditorForm.addBlank")}
          </Button>

          <div className="pt-2 border-t border-orange-200 space-y-2">
            <p className="text-[13px] text-slate-400">{t("questionEditorForm.wordBankOptionsHint")}</p>
            {wordBankOptions.map((o, idx) => (
              <div key={idx} className="flex items-center gap-2">
                <input
                  value={o}
                  onChange={(e) => setWordBankOptions((prev) => prev.map((x, i) => (i === idx ? e.target.value : x)))}
                  placeholder={t("questionEditorForm.wordBankOptionPlaceholder")}
                  className={`flex-1 ${inputClass}`}
                />
                <button
                  type="button"
                  onClick={() => setWordBankOptions((prev) => prev.filter((_, i) => i !== idx))}
                  className="text-slate-400 hover:text-rose-600 shrink-0"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </div>
            ))}
            <Button type="button" variant="secondary" size="sm" onClick={() => setWordBankOptions((prev) => [...prev, ""])}>
              {t("questionEditorForm.addWordBankOption")}
            </Button>
          </div>
        </div>
      )}

      {/*
       * Gắn lại 2026-10-05 (đã xác nhận với người dùng — trước đó chỉ tạo được qua Excel import, kind
       * NGHE_PHIEU_THONG_TIN, xem Javadoc QuestionImportService#KIND_FORM_COMPLETION) — "Nghe điền
       * phiếu thông tin": audio bắt buộc (xử lý chung ở khối isVoiceOrListeningAudio phía trên), mỗi ô
       * gõ tay, CHO PHÉP nhiều đáp án/ô cách nhau "/" (VD "9:30/nine thirty"), dùng **chữ** để in đậm
       * trong nội dung — chuẩn hoá số/giờ khi chấm là việc của backend (FormAnswerMatcher), FE không xử lý.
       */}
      {kind === "LISTENING_FORM_COMPLETION" && (
        <div className="bg-violet-50/40 p-4 rounded-xl border border-violet-200 space-y-3">
          <div className="text-violet-950 font-bold uppercase tracking-wider text-[13px] flex items-center gap-1">
            <ClipboardList className="w-4 h-4 text-violet-700" />
            <span>{t("questionEditorForm.listeningFormCompletionSectionTitle")}</span>
          </div>
          <p className="text-[13px] text-slate-400">{t("questionEditorForm.listeningFormCompletionHint")}</p>
          <div className="space-y-2">
            {wordBankBlanks.map((b, idx) => (
              <div key={idx} className="flex items-center gap-2">
                <span className="text-[12px] font-bold text-slate-500 w-20 shrink-0">{t("questionEditorForm.blankLabel", { index: idx + 1 })}</span>
                <input
                  value={b}
                  onChange={(e) => setWordBankBlanks((prev) => prev.map((x, i) => (i === idx ? e.target.value : x)))}
                  placeholder={t("questionEditorForm.listeningFormCompletionBlankPlaceholder")}
                  className={`flex-1 ${inputClass}`}
                />
                {wordBankBlanks.length > 1 && (
                  <button
                    type="button"
                    onClick={() => setWordBankBlanks((prev) => prev.filter((_, i) => i !== idx))}
                    className="text-slate-400 hover:text-rose-600 shrink-0"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                )}
              </div>
            ))}
          </div>
          <Button type="button" variant="secondary" size="sm" onClick={() => setWordBankBlanks((prev) => [...prev, ""])}>
            {t("questionEditorForm.addBlank")}
          </Button>
        </div>
      )}

      {kind === "SENTENCE_BUILDING" && (
        <div className="bg-cyan-50/40 p-4 rounded-xl border border-cyan-200 space-y-3">
          <div className="text-cyan-950 font-bold uppercase tracking-wider text-[13px] flex items-center gap-1">
            <ListOrdered className="w-4 h-4 text-cyan-600" />
            <span>{t("questionEditorForm.sentenceBuildingSectionTitle")}</span>
          </div>
          <p className="text-[13px] text-slate-400">{t("questionEditorForm.sentenceBuildingHint")}</p>
          {/* <OptionalAudioFields
            audioUrl={audioUrl}
            setAudioUrl={setAudioUrl}
            transcript={transcript}
            setTranscript={setTranscript}
            label={t("questionEditorForm.optionalAudioLabel")}
          /> */}
          <div className="space-y-2">
            {sentenceChunks.map((c, idx) => (
              <div key={idx} className="flex items-center gap-2">
                <span className="text-[12px] font-bold text-slate-500 w-12 shrink-0">#{idx + 1}</span>
                <input
                  value={c}
                  onChange={(e) => setSentenceChunks((prev) => prev.map((x, i) => (i === idx ? e.target.value : x)))}
                  placeholder={t("questionEditorForm.chunkPlaceholder")}
                  className={`flex-1 ${inputClass}`}
                />
                {sentenceChunks.length > 1 && (
                  <button
                    type="button"
                    onClick={() => setSentenceChunks((prev) => prev.filter((_, i) => i !== idx))}
                    className="text-slate-400 hover:text-rose-600 shrink-0"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                )}
              </div>
            ))}
          </div>
          <Button type="button" variant="secondary" size="sm" onClick={() => setSentenceChunks((prev) => [...prev, ""])}>
            {t("questionEditorForm.addChunk")}
          </Button>
        </div>
      )}

      {/*
       * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — kind RIÊNG, TÁCH HẲN khỏi
       * SENTENCE_BUILDING ở trên: khớp Ex.1 "Rearrange the letters to make correct words under each
       * picture" — mỗi khối là 1 CHỮ CÁI (thay vì 1 từ/cụm), có ảnh minh họa cho từng mục.
       */}
      {kind === "LETTER_SCRAMBLE" && (
        <div className="bg-cyan-50/40 p-4 rounded-xl border border-cyan-200 space-y-3">
          <div className="text-cyan-950 font-bold uppercase tracking-wider text-[13px] flex items-center gap-1">
            <Shuffle className="w-4 h-4 text-cyan-700" />
            <span>{t("questionEditorForm.letterScrambleSectionTitle")}</span>
          </div>
          <p className="text-[13px] text-slate-400">{t("questionEditorForm.letterScrambleHint")}</p>
          <div>
            <label className="block font-bold text-slate-600 mb-1 text-[13px] uppercase">{t("questionEditorForm.letterScrambleImageLabel")}</label>
            <FileUploadField
              value={imageUrl}
              onChange={setImageUrl}
              onUpload={(file) => uploadMedia(file, "LMS_QUESTION")}
              accept="image/*"
              placeholder={t("questionEditorForm.letterScrambleImagePlaceholder")}
            />
          </div>
          {/* <OptionalAudioFields
            audioUrl={audioUrl}
            setAudioUrl={setAudioUrl}
            transcript={transcript}
            setTranscript={setTranscript}
            label={t("questionEditorForm.optionalAudioLabel")}
          /> */}
          <div className="space-y-2">
            {sentenceChunks.map((c, idx) => (
              <div key={idx} className="flex items-center gap-2">
                <span className="text-[12px] font-bold text-slate-500 w-12 shrink-0">#{idx + 1}</span>
                <input
                  value={c}
                  onChange={(e) => setSentenceChunks((prev) => prev.map((x, i) => (i === idx ? e.target.value : x)))}
                  placeholder={t("questionEditorForm.letterChunkPlaceholder")}
                  className={`flex-1 ${inputClass}`}
                />
                {sentenceChunks.length > 1 && (
                  <button
                    type="button"
                    onClick={() => setSentenceChunks((prev) => prev.filter((_, i) => i !== idx))}
                    className="text-slate-400 hover:text-rose-600 shrink-0"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                )}
              </div>
            ))}
          </div>
          <Button type="button" variant="secondary" size="sm" onClick={() => setSentenceChunks((prev) => [...prev, ""])}>
            {t("questionEditorForm.addLetterChunk")}
          </Button>
        </div>
      )}

      {(kind === "FILL_IN_BLANK" || kind === "LISTENING_FILL_IN_BLANK" || kind === "FILL_IN_BLANK_PICTURE") && (
        <div className="bg-amber-50/40 p-4 rounded-xl border border-amber-200 space-y-3">
          <div className="text-amber-950 font-bold uppercase tracking-wider text-[13px] flex items-center gap-1">
            <PenLine className="w-4 h-4 text-amber-600" />
            <span>{t("questionEditorForm.fillInBlankSectionTitle")}</span>
          </div>
          {/*
           * Bổ sung 2026-08-28 (đã xác nhận với người dùng) — khớp dạng "Complete each sentence with
           * this/that/these/those" (mỗi câu 1 ảnh minh họa RIÊNG, đáp án tự do có thể lặp lại giữa các
           * câu — khác WORD_BANK_PICTURE dùng 1 ảnh chung + khung từ dùng 1 lần).
           */}
          {kind === "FILL_IN_BLANK_PICTURE" && (
            <div>
              <label className="block font-bold text-slate-600 mb-1 text-[13px] uppercase">{t("questionEditorForm.fillInBlankImageLabel")}</label>
              <FileUploadField
                value={imageUrl}
                onChange={setImageUrl}
                onUpload={(file) => uploadMedia(file, "LMS_QUESTION")}
                accept="image/*"
                placeholder={t("questionEditorForm.fillInBlankImagePlaceholder")}
              />
            </div>
          )}
          {kind === "FILL_IN_BLANK" && (
            <OptionalAudioFields
              audioUrl={audioUrl}
              setAudioUrl={setAudioUrl}
              transcript={transcript}
              setTranscript={setTranscript}
              label={t("questionEditorForm.optionalAudioLabel")}
              transcriptHint={t("questionEditorForm.optionalAudioTranscriptFillInBlankHint")}
            />
          )}
          <div>
            <label className="block font-bold text-slate-600 mb-1 text-[13px] uppercase">{t("questionEditorForm.correctAnswerLabel")}</label>
            <input
              required
              value={correctAnswerText}
              onChange={(e) => setCorrectAnswerText(e.target.value)}
              placeholder={t("questionEditorForm.correctAnswerPlaceholder")}
              className={inputClass}
            />
            <p className="text-[13px] text-slate-400 mt-1">{t("questionEditorForm.correctAnswerHint")}</p>
          </div>
        </div>
      )}

      {kind === "ESSAY" && (
        <div className="bg-purple-50/40 p-4 rounded-xl border border-purple-200 space-y-3">
          <div className="text-purple-950 font-bold uppercase tracking-wider text-[13px] flex items-center gap-1">
            <FileText className="w-4 h-4 text-purple-600" />
            <span>{t("questionEditorForm.essaySectionTitle")}</span>
          </div>
          <div>
            <label className="block font-bold text-slate-600 mb-1 text-[13px] uppercase">{t("questionEditorForm.essayImageLabel")}</label>
            <FileUploadField
              value={imageUrl}
              onChange={setImageUrl}
              onUpload={(file) => uploadMedia(file, "LMS_QUESTION")}
              accept="image/*,.pdf,application/pdf"
              placeholder={t("questionEditorForm.essayImagePlaceholder")}
            />
          </div>
        </div>
      )}

      {kind === "SPEAKING" && (
        <div className="bg-rose-50/40 p-4 rounded-xl border border-rose-200 space-y-3">
          <div className="text-rose-950 font-bold uppercase tracking-wider text-[13px] flex items-center gap-1">
            <Mic className="w-4 h-4 text-rose-600" />
            <span>{t("questionEditorForm.speakingSectionTitle")}</span>
          </div>
          <div>
            <label className="block font-bold text-slate-600 mb-1 text-[13px] uppercase">{t("questionEditorForm.speakingKeywordsLabel")}</label>
            <input
              required
              value={phoneticKeywords}
              onChange={(e) => setPhoneticKeywords(e.target.value)}
              placeholder={t("questionEditorForm.speakingKeywordsPlaceholder")}
              className={inputClass}
            />
          </div>
        </div>
      )}

      <div>
        <label className={labelClass}>{t("questionEditorForm.explanationLabel")}</label>
        <textarea
          rows={2}
          value={explanation}
          onChange={(e) => setExplanation(e.target.value)}
          placeholder={t("questionEditorForm.explanationPlaceholder")}
          className={inputClass}
        />
      </div>

      <div className="flex items-center justify-end gap-2 pt-4 border-t border-slate-200">
        <Button type="button" variant="secondary" onClick={onCancel}>
          {t("common.cancel")}
        </Button>
        <Button type="submit" variant="primary" disabled={submitting}>
          {submitting ? t("common.saving") : isEditing ? t("questionEditorForm.submitUpdate") : t("questionEditorForm.submitCreate")}
        </Button>
      </div>
    </form>
  );
}
