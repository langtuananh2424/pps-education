import React, { useState } from "react";
import { Blocks, Check, CheckSquare, FileText, Headphones, ListOrdered, Mic, PenLine, SplitSquareHorizontal, Volume2, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import Button from "@/components/ui/Button";
import FileUploadField from "@/components/ui/FileUploadField";
import { CreateQuestionRequest, QuestionChoiceRequest, QuestionDifficulty, QuestionResponse, QuestionType, createQuestion, updateQuestion, uploadMedia } from "../api";
import Select from "@/components/ui/Select";

const inputClass = "w-full bg-white border border-slate-200 text-xs px-3.5 py-2 rounded-lg focus:outline-none focus:ring-1 focus:ring-brand-red";
const labelClass = "block font-bold text-slate-700 mb-1 uppercase tracking-wider text-[10px]";

/**
 * 9 loại — 5 loại gốc (theo bản thiết kế tham chiếu + Điền từ, bổ sung 2026-07-28 sau khi backend
 * thêm tự chấm FILL_IN_BLANK — V54) + 4 loại mới (V78, bổ sung ngoài SDD gốc, đã xác nhận với người
 * dùng 2026-08-04, dựa trên 1 đề tiếng Anh mẫu người dùng cung cấp — 5 dạng bài GV Việt Nam +
 * "Nghe & nộp audio" GV nước ngoài). "Trắc nghiệm Voice"/"Chọn từ trong câu"/"Nghe & nộp audio"
 * KHÔNG phải giá trị enum riêng ở backend — là MULTIPLE_CHOICE/SPEAKING + skill tương ứng (kind ảo
 * chỉ tồn tại ở FE để hiện UI phù hợp).
 */
type UiQuestionKind =
  | "MULTIPLE_CHOICE"
  | "VOICE_MULTIPLE_CHOICE"
  | "INLINE_CHOICE"
  | "FILL_IN_BLANK"
  | "WORD_BANK"
  | "SENTENCE_BUILDING"
  | "ESSAY"
  | "SPEAKING"
  | "LISTENING_AUDIO_SUBMISSION";

const kindMeta: Record<UiQuestionKind, { label: string; icon: typeof CheckSquare; activeClass: string; iconClass: string }> = {
  MULTIPLE_CHOICE: { label: "Trắc nghiệm", icon: CheckSquare, activeClass: "bg-emerald-50 border-emerald-400 text-emerald-800 ring-1 ring-emerald-300", iconClass: "text-emerald-600" },
  VOICE_MULTIPLE_CHOICE: { label: "Trắc nghiệm Voice", icon: Volume2, activeClass: "bg-blue-50 border-blue-400 text-blue-800 ring-1 ring-blue-300", iconClass: "text-blue-600" },
  INLINE_CHOICE: { label: "Chọn từ trong câu", icon: SplitSquareHorizontal, activeClass: "bg-teal-50 border-teal-400 text-teal-800 ring-1 ring-teal-300", iconClass: "text-teal-600" },
  FILL_IN_BLANK: { label: "Điền từ", icon: PenLine, activeClass: "bg-amber-50 border-amber-400 text-amber-800 ring-1 ring-amber-300", iconClass: "text-amber-600" },
  WORD_BANK: { label: "Điền từ - Hộp từ vựng", icon: Blocks, activeClass: "bg-orange-50 border-orange-400 text-orange-800 ring-1 ring-orange-300", iconClass: "text-orange-600" },
  SENTENCE_BUILDING: { label: "Sắp xếp câu", icon: ListOrdered, activeClass: "bg-cyan-50 border-cyan-400 text-cyan-800 ring-1 ring-cyan-300", iconClass: "text-cyan-600" },
  ESSAY: { label: "Tự luận file/ảnh", icon: FileText, activeClass: "bg-purple-50 border-purple-400 text-purple-800 ring-1 ring-purple-300", iconClass: "text-purple-600" },
  SPEAKING: { label: "Speaking oral", icon: Mic, activeClass: "bg-rose-50 border-rose-400 text-rose-800 ring-1 ring-rose-300", iconClass: "text-rose-600" },
  LISTENING_AUDIO_SUBMISSION: { label: "Nghe & nộp audio", icon: Headphones, activeClass: "bg-sky-50 border-sky-400 text-sky-800 ring-1 ring-sky-300", iconClass: "text-sky-600" }
};

const difficultyLabels: Record<QuestionDifficulty, string> = { EASY: "Dễ (Easy)", MEDIUM: "Trung bình (Medium)", HARD: "Khó (Hard)" };

function toKind(question?: QuestionResponse): UiQuestionKind {
  if (!question) return "MULTIPLE_CHOICE";
  if (question.questionType === "ESSAY") return "ESSAY";
  if (question.questionType === "SPEAKING") return question.skill === "LISTENING" ? "LISTENING_AUDIO_SUBMISSION" : "SPEAKING";
  if (question.questionType === "FILL_IN_BLANK") return "FILL_IN_BLANK";
  if (question.questionType === "WORD_BANK") return "WORD_BANK";
  if (question.questionType === "SENTENCE_BUILDING") return "SENTENCE_BUILDING";
  if (question.questionType === "MULTIPLE_CHOICE" && question.skill !== "LISTENING" && question.choices?.length === 2) return "INLINE_CHOICE";
  return question.skill === "LISTENING" ? "VOICE_MULTIPLE_CHOICE" : "MULTIPLE_CHOICE";
}

interface QuestionEditorFormProps {
  questionBankId: number;
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

/** UC-40 Main Flow bước 1: soạn hoặc sửa 1 câu hỏi trong ngân hàng — 5 loại theo thiết kế tham chiếu + Điền từ. */
export default function QuestionEditorForm({ questionBankId, existingQuestion, allowedKinds, onCreated, onCancel }: QuestionEditorFormProps) {
  const isEditing = !!existingQuestion;
  const visibleKinds = allowedKinds ?? (Object.keys(kindMeta) as UiQuestionKind[]);
  const [kind, setKind] = useState<UiQuestionKind>(
    existingQuestion ? toKind(existingQuestion) : visibleKinds[0] ?? "MULTIPLE_CHOICE"
  );
  const [content, setContent] = useState(existingQuestion?.content ?? "");
  const [difficulty, setDifficulty] = useState<QuestionDifficulty>(existingQuestion?.difficulty ?? "MEDIUM");
  const [explanation, setExplanation] = useState(existingQuestion?.explanation ?? "");

  // Trắc nghiệm / Trắc nghiệm Voice / Chọn từ trong câu: 4 lựa chọn (2 với Chọn từ trong câu), 1 đáp án đúng.
  const [options, setOptions] = useState<string[]>(
    existingQuestion?.choices?.length ? existingQuestion.choices.map((c) => c.content) : toKind(existingQuestion) === "INLINE_CHOICE" ? ["", ""] : ["", "", "", ""]
  );
  const [correctIndex, setCorrectIndex] = useState<number>(existingQuestion?.choices?.findIndex((c) => c.isCorrect) ?? 0);

  // Trắc nghiệm Voice / Nghe & nộp audio: file audio + transcript (referencePassage).
  const [audioUrl, setAudioUrl] = useState(existingQuestion?.audioUrl ?? "");
  const [transcript, setTranscript] = useState(existingQuestion?.referencePassage ?? "");

  // Điền từ: đáp án đúng duy nhất, BE so khớp case-insensitive + trim khi tự chấm (V54).
  const [correctAnswerText, setCorrectAnswerText] = useState(existingQuestion?.correctAnswerText ?? "");

  // Tự luận: ảnh/tài liệu scan đề bài (imageUrl).
  const [imageUrl, setImageUrl] = useState(existingQuestion?.imageUrl ?? "");

  // Speaking: từ khóa/âm vị trọng điểm (dùng chung field referencePassage với Voice — 2 loại không cùng hiện 1 lúc).
  const [phoneticKeywords, setPhoneticKeywords] = useState(existingQuestion?.referencePassage ?? "");

  // V78: Điền từ - Hộp từ vựng — đáp án đúng theo ĐÚNG thứ tự chỗ trống trong content.
  const [wordBankBlanks, setWordBankBlanks] = useState<string[]>(
    existingQuestion?.structuredContent?.blanks?.length ? existingQuestion.structuredContent.blanks : ["", ""]
  );
  // V78: Sắp xếp câu — khối từ/cụm theo ĐÚNG thứ tự câu hoàn chỉnh, FE học sinh sẽ xáo trộn lúc hiển thị.
  const [sentenceChunks, setSentenceChunks] = useState<string[]>(
    existingQuestion?.structuredContent?.chunks?.length ? existingQuestion.structuredContent.chunks : ["", "", ""]
  );

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /** Chuyển loại: reset số lựa chọn cho đúng (INLINE_CHOICE=2, MULTIPLE_CHOICE/VOICE=4) khi tạo mới. */
  const handleSelectKind = (value: UiQuestionKind) => {
    setKind(value);
    if (isEditing) return;
    if (value === "INLINE_CHOICE" && options.length !== 2) {
      setOptions(["", ""]);
      setCorrectIndex(0);
    } else if ((value === "MULTIPLE_CHOICE" || value === "VOICE_MULTIPLE_CHOICE") && options.length !== 4) {
      setOptions(["", "", "", ""]);
      setCorrectIndex(0);
    }
  };

  const isVoiceOrListeningAudio = kind === "VOICE_MULTIPLE_CHOICE" || kind === "LISTENING_AUDIO_SUBMISSION";

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!content.trim()) {
      setError("Vui lòng nhập nội dung câu hỏi.");
      return;
    }

    const isChoiceKind = kind === "MULTIPLE_CHOICE" || kind === "VOICE_MULTIPLE_CHOICE" || kind === "INLINE_CHOICE";
    let choices: QuestionChoiceRequest[] | undefined;
    if (isChoiceKind) {
      if (options.some((o) => !o.trim())) {
        setError(`Vui lòng điền đủ ${options.length} đáp án.`);
        return;
      }
      choices = options.map((content_, i) => ({ choiceLabel: String.fromCharCode(65 + i), content: content_.trim(), isCorrect: i === correctIndex, displayOrder: i + 1 }));
    }
    if (isVoiceOrListeningAudio && !audioUrl.trim()) {
      setError(kind === "VOICE_MULTIPLE_CHOICE" ? "Trắc nghiệm Voice cần có URL audio mẫu." : "Nghe & nộp audio cần có URL audio bài nghe.");
      return;
    }
    if (kind === "FILL_IN_BLANK" && !correctAnswerText.trim()) {
      setError("Điền từ cần có đáp án đúng để hệ thống tự chấm.");
      return;
    }
    if (kind === "WORD_BANK" && wordBankBlanks.some((b) => !b.trim())) {
      setError("Điền từ - Hộp từ vựng cần điền đủ đáp án đúng cho mọi chỗ trống.");
      return;
    }
    if (kind === "SENTENCE_BUILDING" && sentenceChunks.filter((c) => c.trim()).length < 2) {
      setError("Sắp xếp câu cần ít nhất 2 khối từ/cụm theo đúng thứ tự câu hoàn chỉnh.");
      return;
    }

    const structuredContent =
      kind === "WORD_BANK"
        ? { blanks: wordBankBlanks.map((b) => b.trim()) }
        : kind === "SENTENCE_BUILDING"
          ? { chunks: sentenceChunks.filter((c) => c.trim()).map((c) => c.trim()) }
          : undefined;

    setSubmitting(true);
    try {
      let result: QuestionResponse;
      if (isEditing && existingQuestion) {
        result = await updateQuestion(existingQuestion.id, {
          content: content.trim(),
          audioUrl: isVoiceOrListeningAudio ? audioUrl.trim() || undefined : undefined,
          imageUrl: kind === "ESSAY" ? imageUrl.trim() || undefined : undefined,
          referencePassage: isVoiceOrListeningAudio ? transcript.trim() || undefined : kind === "SPEAKING" ? phoneticKeywords.trim() || undefined : undefined,
          explanation: explanation.trim() || undefined,
          correctAnswerText: kind === "FILL_IN_BLANK" ? correctAnswerText.trim() || undefined : undefined,
          structuredContent,
          choices
        });
      } else {
        const questionType: QuestionType =
          kind === "VOICE_MULTIPLE_CHOICE" || kind === "INLINE_CHOICE" ? "MULTIPLE_CHOICE" : kind === "LISTENING_AUDIO_SUBMISSION" ? "SPEAKING" : kind;
        const request: CreateQuestionRequest = {
          questionBankId,
          questionType,
          skill: isVoiceOrListeningAudio ? "LISTENING" : kind === "SPEAKING" ? "SPEAKING" : undefined,
          difficulty,
          content: content.trim(),
          audioUrl: isVoiceOrListeningAudio ? audioUrl.trim() || undefined : undefined,
          imageUrl: kind === "ESSAY" ? imageUrl.trim() || undefined : undefined,
          referencePassage: isVoiceOrListeningAudio ? transcript.trim() || undefined : kind === "SPEAKING" ? phoneticKeywords.trim() || undefined : undefined,
          explanation: explanation.trim() || undefined,
          correctAnswerText: kind === "FILL_IN_BLANK" ? correctAnswerText.trim() || undefined : undefined,
          structuredContent,
          choices
        };
        result = await createQuestion(request);
      }
      onCreated(result);
    } catch (err) {
      if (err instanceof ApiError && (err.status === 409 || err.status === 400) && isEditing) {
        setError("Câu hỏi này đã có học viên nộp bài — không sửa được nữa, hãy soạn câu hỏi mới thay thế.");
      } else {
        setError(err instanceof ApiError ? err.message : "Lưu câu hỏi thất bại.");
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4 text-xs">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div>
        <label className={labelClass}>Loại câu hỏi tiếng Anh *{isEditing && <span className="text-slate-400 font-normal"> (không sửa được sau khi tạo)</span>}</label>
        <div className="grid grid-cols-2 md:grid-cols-5 gap-2">
          {(Object.entries(kindMeta) as [UiQuestionKind, (typeof kindMeta)[UiQuestionKind]][])
            .filter(([value]) => visibleKinds.includes(value))
            .map(([value, meta]) => {
            const Icon = meta.icon;
            const active = kind === value;
            return (
              <button
                key={value}
                type="button"
                disabled={isEditing}
                onClick={() => handleSelectKind(value)}
                className={`p-2.5 rounded-xl border font-bold flex flex-col items-center gap-1 transition-all disabled:cursor-not-allowed disabled:opacity-60 ${
                  active ? `${meta.activeClass} shadow-xs` : "bg-white border-slate-200 text-slate-600 hover:bg-slate-50"
                }`}
              >
                <Icon className={`w-4 h-4 ${active ? "" : meta.iconClass}`} />
                <span>{meta.label}</span>
              </button>
            );
          })}
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label className={labelClass}>Độ khó khảo thí *</label>
          <Select value={difficulty} onChange={(e) => setDifficulty(e.target.value as QuestionDifficulty)} disabled={isEditing} className={`${inputClass} disabled:opacity-60 font-bold`}>
            {Object.entries(difficultyLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </div>
      </div>

      <div>
        <label className={labelClass}>Nội dung câu hỏi / câu lệnh chỉ dẫn *</label>
        <textarea
          required
          rows={3}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="Nhập chi tiết đề bài viết luận, câu hỏi điền từ vào chỗ trống, hoặc văn bản đọc..."
          className={inputClass}
        />
      </div>

      {(kind === "MULTIPLE_CHOICE" || kind === "VOICE_MULTIPLE_CHOICE" || kind === "INLINE_CHOICE") && (
        <div className="space-y-2 bg-slate-50 p-4 rounded-xl border border-slate-200">
          <div className="flex items-center justify-between mb-1">
            <span className="font-bold text-slate-700 uppercase tracking-wider text-[9px]">Thiết lập {options.length} đáp án & chọn 1 câu trả lời đúng</span>
            <span className="text-[9px] text-slate-400 font-bold">Hãy nhấp nút check để chọn đáp án đúng</span>
          </div>
          {kind === "INLINE_CHOICE" && (
            <p className="text-[9px] text-slate-400">
              Viết câu đầy đủ ở "Nội dung câu hỏi" bên trên, đánh dấu vị trí cần chọn bằng dấu ___ (VD: "We/Us saw ___ yesterday." → viết
              "___ saw him yesterday."), rồi nhập 2 lựa chọn bên dưới.
            </p>
          )}
          <div className="space-y-2">
            {options.map((opt, idx) => (
              <div key={idx} className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => setCorrectIndex(idx)}
                  className={`w-6 h-6 rounded-full border flex items-center justify-center font-bold shrink-0 text-[10px] transition-all ${
                    correctIndex === idx ? "bg-emerald-500 border-emerald-500 text-white" : "bg-white border-slate-300 text-slate-400 hover:border-slate-400"
                  }`}
                >
                  {correctIndex === idx ? <Check className="w-3.5 h-3.5 stroke-[3]" /> : String.fromCharCode(65 + idx)}
                </button>
                <input
                  required
                  value={opt}
                  onChange={(e) => setOptions((prev) => prev.map((o, i) => (i === idx ? e.target.value : o)))}
                  placeholder={`Đáp án ${String.fromCharCode(65 + idx)}...`}
                  className={`flex-1 ${inputClass}`}
                />
              </div>
            ))}
          </div>
        </div>
      )}

      {isVoiceOrListeningAudio && (
        <div className={`p-4 rounded-xl border space-y-3 ${kind === "VOICE_MULTIPLE_CHOICE" ? "bg-blue-50/40 border-blue-200" : "bg-sky-50/40 border-sky-200"}`}>
          <div className={`flex items-center gap-1 font-bold uppercase tracking-wider text-[9px] ${kind === "VOICE_MULTIPLE_CHOICE" ? "text-blue-900" : "text-sky-900"}`}>
            {kind === "VOICE_MULTIPLE_CHOICE" ? <Volume2 className="w-4 h-4 text-blue-600" /> : <Headphones className="w-4 h-4 text-sky-600" />}
            <span>Cấu hình file âm thanh / Transcript</span>
          </div>
          {kind === "LISTENING_AUDIO_SUBMISSION" && (
            <p className="text-[9px] text-slate-400">
              Học sinh sẽ nghe file audio này rồi tự ghi âm nộp lại câu trả lời (chấm tay ở "Hàng chờ chấm bài") — khác Trắc nghiệm Voice
              (học sinh chỉ chọn đáp án có sẵn).
            </p>
          )}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div>
              <label className="block font-bold text-slate-600 mb-1 text-[9px] uppercase">File Audio bài nghe *</label>
              <FileUploadField value={audioUrl} onChange={setAudioUrl} onUpload={(file) => uploadMedia(file, "LMS_QUESTION")} accept="audio/*" placeholder="Chọn file audio..." />
            </div>
            <div>
              <label className="block font-bold text-slate-600 mb-1 text-[9px] uppercase">Ghi chú phát âm / Transcript</label>
              <input value={transcript} onChange={(e) => setTranscript(e.target.value)} placeholder="Nội dung đọc trong tệp audio giúp kiểm tra..." className={inputClass} />
            </div>
          </div>
        </div>
      )}

      {kind === "WORD_BANK" && (
        <div className="bg-orange-50/40 p-4 rounded-xl border border-orange-200 space-y-3">
          <div className="text-orange-950 font-bold uppercase tracking-wider text-[9px] flex items-center gap-1">
            <Blocks className="w-4 h-4 text-orange-600" />
            <span>Hộp từ vựng — đáp án đúng theo thứ tự chỗ trống</span>
          </div>
          <p className="text-[9px] text-slate-400">
            Viết đoạn văn có chỗ trống ở "Nội dung câu hỏi" bên trên, đánh dấu mỗi chỗ trống bằng dấu ___ theo ĐÚNG thứ tự các từ bên dưới —
            học sinh chọn qua dropdown, mỗi từ chỉ dùng đúng 1 lần.
          </p>
          <div className="space-y-2">
            {wordBankBlanks.map((b, idx) => (
              <div key={idx} className="flex items-center gap-2">
                <span className="text-[10px] font-bold text-slate-500 w-20 shrink-0">Chỗ trống {idx + 1}</span>
                <input
                  required
                  value={b}
                  onChange={(e) => setWordBankBlanks((prev) => prev.map((x, i) => (i === idx ? e.target.value : x)))}
                  placeholder="VD: went"
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
            + Thêm chỗ trống
          </Button>
        </div>
      )}

      {kind === "SENTENCE_BUILDING" && (
        <div className="bg-cyan-50/40 p-4 rounded-xl border border-cyan-200 space-y-3">
          <div className="text-cyan-950 font-bold uppercase tracking-wider text-[9px] flex items-center gap-1">
            <ListOrdered className="w-4 h-4 text-cyan-600" />
            <span>Khối từ/cụm — theo ĐÚNG thứ tự câu hoàn chỉnh</span>
          </div>
          <p className="text-[9px] text-slate-400">
            Nhập các khối từ/cụm theo ĐÚNG thứ tự tạo thành câu hoàn chỉnh — học sinh sẽ thấy các khối này bị xáo trộn, chọn lại theo đúng
            thứ tự để hệ thống tự chấm (so khớp chính xác thứ tự).
          </p>
          <div className="space-y-2">
            {sentenceChunks.map((c, idx) => (
              <div key={idx} className="flex items-center gap-2">
                <span className="text-[10px] font-bold text-slate-500 w-12 shrink-0">#{idx + 1}</span>
                <input
                  value={c}
                  onChange={(e) => setSentenceChunks((prev) => prev.map((x, i) => (i === idx ? e.target.value : x)))}
                  placeholder="VD: This is"
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
            + Thêm khối
          </Button>
        </div>
      )}

      {kind === "FILL_IN_BLANK" && (
        <div className="bg-amber-50/40 p-4 rounded-xl border border-amber-200 space-y-3">
          <div className="text-amber-950 font-bold uppercase tracking-wider text-[9px] flex items-center gap-1">
            <PenLine className="w-4 h-4 text-amber-600" />
            <span>Đáp án đúng (hệ thống tự chấm)</span>
          </div>
          <div>
            <label className="block font-bold text-slate-600 mb-1 text-[9px] uppercase">Đáp án đúng *</label>
            <input
              required
              value={correctAnswerText}
              onChange={(e) => setCorrectAnswerText(e.target.value)}
              placeholder="VD: went"
              className={inputClass}
            />
            <p className="text-[9px] text-slate-400 mt-1">
              So khớp không phân biệt hoa/thường, tự bỏ khoảng trắng thừa đầu-cuối khi chấm — chỉ nhận đúng 1 đáp án.
            </p>
          </div>
        </div>
      )}

      {kind === "ESSAY" && (
        <div className="bg-purple-50/40 p-4 rounded-xl border border-purple-200 space-y-3">
          <div className="text-purple-950 font-bold uppercase tracking-wider text-[9px] flex items-center gap-1">
            <FileText className="w-4 h-4 text-purple-600" />
            <span>Đính kèm hình ảnh / File scan đề bài</span>
          </div>
          <div>
            <label className="block font-bold text-slate-600 mb-1 text-[9px] uppercase">Hình ảnh/Tài liệu scan mẫu</label>
            <FileUploadField
              value={imageUrl}
              onChange={setImageUrl}
              onUpload={(file) => uploadMedia(file, "LMS_QUESTION")}
              accept="image/*,.pdf,application/pdf"
              placeholder="Chọn ảnh hoặc file PDF..."
            />
          </div>
        </div>
      )}

      {kind === "SPEAKING" && (
        <div className="bg-rose-50/40 p-4 rounded-xl border border-rose-200 space-y-3">
          <div className="text-rose-950 font-bold uppercase tracking-wider text-[9px] flex items-center gap-1">
            <Mic className="w-4 h-4 text-rose-600" />
            <span>Bộ phân tích phát âm tiếng Anh</span>
          </div>
          <div>
            <label className="block font-bold text-slate-600 mb-1 text-[9px] uppercase">Các âm vị / Từ vựng trọng điểm cần học viên phát âm chính xác *</label>
            <input
              required
              value={phoneticKeywords}
              onChange={(e) => setPhoneticKeywords(e.target.value)}
              placeholder="Ví dụ: Target pronunciation sounds: 'enthusiasm', 'literature', 'variety'..."
              className={inputClass}
            />
          </div>
        </div>
      )}

      <div>
        <label className={labelClass}>Đáp án giải thích / Tiêu chuẩn thang điểm học thuật (Rubrics)</label>
        <textarea
          rows={2}
          value={explanation}
          onChange={(e) => setExplanation(e.target.value)}
          placeholder="Ghi rõ lời giải chi tiết (cho trắc nghiệm) hoặc thang điểm chấm chi tiết (cho tự luận/speaking)..."
          className={inputClass}
        />
      </div>

      <div className="flex items-center justify-end gap-2 pt-4 border-t border-slate-200">
        <Button type="button" variant="secondary" onClick={onCancel}>
          Hủy bỏ
        </Button>
        <Button type="submit" variant="primary" disabled={submitting}>
          {submitting ? "Đang lưu..." : isEditing ? "Lưu thay đổi" : "Tạo câu hỏi"}
        </Button>
      </div>
    </form>
  );
}
