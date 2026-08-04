import React, { useState } from "react";
import { Check, CheckSquare, FileText, Mic, PenLine, Volume2 } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import Button from "@/components/ui/Button";
import FileUploadField from "@/components/ui/FileUploadField";
import { CreateExamQuestionRequest, CreateQuestionRequest, QuestionChoiceRequest, QuestionDifficulty, QuestionResponse, QuestionType, createExamQuestion, createQuestion, updateExamQuestion, updateQuestion, uploadMedia } from "../api";
import Select from "@/components/ui/Select";

const inputClass = "w-full bg-white border border-slate-200 text-xs px-3.5 py-2 rounded-lg focus:outline-none focus:ring-1 focus:ring-brand-red";
const labelClass = "block font-bold text-slate-700 mb-1 uppercase tracking-wider text-[10px]";

/**
 * 5 loại (4 loại gốc theo bản thiết kế tham chiếu + Điền từ) — "Điền từ" bổ
 * sung 2026-07-28 sau khi backend thêm tự chấm FILL_IN_BLANK (V54, so khớp
 * case-insensitive + trim), trước đó chưa có UI vì chỉ chấm tay thủ công,
 * đã xác nhận với người dùng. "Trắc nghiệm Voice" không phải 1 giá trị enum
 * riêng ở backend — là MULTIPLE_CHOICE + skill=LISTENING (bắt buộc kèm audioUrl).
 */
type UiQuestionKind = "MULTIPLE_CHOICE" | "VOICE_MULTIPLE_CHOICE" | "FILL_IN_BLANK" | "ESSAY" | "SPEAKING";

const kindMeta: Record<UiQuestionKind, { label: string; icon: typeof CheckSquare; activeClass: string; iconClass: string }> = {
  MULTIPLE_CHOICE: { label: "Trắc nghiệm", icon: CheckSquare, activeClass: "bg-emerald-50 border-emerald-400 text-emerald-800 ring-1 ring-emerald-300", iconClass: "text-emerald-600" },
  VOICE_MULTIPLE_CHOICE: { label: "Trắc nghiệm Voice", icon: Volume2, activeClass: "bg-blue-50 border-blue-400 text-blue-800 ring-1 ring-blue-300", iconClass: "text-blue-600" },
  FILL_IN_BLANK: { label: "Điền từ", icon: PenLine, activeClass: "bg-amber-50 border-amber-400 text-amber-800 ring-1 ring-amber-300", iconClass: "text-amber-600" },
  ESSAY: { label: "Tự luận file/ảnh", icon: FileText, activeClass: "bg-purple-50 border-purple-400 text-purple-800 ring-1 ring-purple-300", iconClass: "text-purple-600" },
  SPEAKING: { label: "Speaking oral", icon: Mic, activeClass: "bg-rose-50 border-rose-400 text-rose-800 ring-1 ring-rose-300", iconClass: "text-rose-600" }
};

const difficultyLabels: Record<QuestionDifficulty, string> = { EASY: "Dễ (Easy)", MEDIUM: "Trung bình (Medium)", HARD: "Khó (Hard)" };

function toKind(question?: QuestionResponse): UiQuestionKind {
  if (!question) return "MULTIPLE_CHOICE";
  if (question.questionType === "ESSAY") return "ESSAY";
  if (question.questionType === "SPEAKING") return "SPEAKING";
  if (question.questionType === "FILL_IN_BLANK") return "FILL_IN_BLANK";
  return question.skill === "LISTENING" ? "VOICE_MULTIPLE_CHOICE" : "MULTIPLE_CHOICE";
}

interface QuestionEditorFormProps {
  /** Generic legacy mode cho trang quản lý ngân hàng độc lập. */
  questionBankId?: number;
  /** V75: Teacher flow theo Đề — backend tự resolve ngân hàng nội bộ. */
  examId?: number;
  /** Truyền vào để chuyển form sang chế độ Sửa — loại câu hỏi không sửa được sau khi tạo. */
  existingQuestion?: QuestionResponse;
  onCreated: (question: QuestionResponse) => void;
  onCancel: () => void;
}

/** UC-40 Main Flow bước 1: soạn/sửa câu hỏi theo Exam hoặc generic legacy bank. */
export default function QuestionEditorForm({ questionBankId, examId, existingQuestion, onCreated, onCancel }: QuestionEditorFormProps) {
  const isEditing = !!existingQuestion;
  const [kind, setKind] = useState<UiQuestionKind>(toKind(existingQuestion));
  const [content, setContent] = useState(existingQuestion?.content ?? "");
  const [difficulty, setDifficulty] = useState<QuestionDifficulty>(existingQuestion?.difficulty ?? "MEDIUM");
  const [explanation, setExplanation] = useState(existingQuestion?.explanation ?? "");

  // Trắc nghiệm / Trắc nghiệm Voice: đúng 4 lựa chọn, 1 đáp án đúng.
  const [options, setOptions] = useState<string[]>(existingQuestion?.choices?.length ? existingQuestion.choices.map((c) => c.content) : ["", "", "", ""]);
  const [correctIndex, setCorrectIndex] = useState<number>(existingQuestion?.choices?.findIndex((c) => c.isCorrect) ?? 0);

  // Trắc nghiệm Voice: file audio + transcript (referencePassage).
  const [audioUrl, setAudioUrl] = useState(existingQuestion?.audioUrl ?? "");
  const [transcript, setTranscript] = useState(existingQuestion?.referencePassage ?? "");

  // Điền từ: đáp án đúng duy nhất, BE so khớp case-insensitive + trim khi tự chấm (V54).
  const [correctAnswerText, setCorrectAnswerText] = useState(existingQuestion?.correctAnswerText ?? "");

  // Tự luận: ảnh/tài liệu scan đề bài (imageUrl).
  const [imageUrl, setImageUrl] = useState(existingQuestion?.imageUrl ?? "");

  // Speaking: từ khóa/âm vị trọng điểm (dùng chung field referencePassage với Voice — 2 loại không cùng hiện 1 lúc).
  const [phoneticKeywords, setPhoneticKeywords] = useState(existingQuestion?.referencePassage ?? "");

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!content.trim()) {
      setError("Vui lòng nhập nội dung câu hỏi.");
      return;
    }

    const isChoiceKind = kind === "MULTIPLE_CHOICE" || kind === "VOICE_MULTIPLE_CHOICE";
    let choices: QuestionChoiceRequest[] | undefined;
    if (isChoiceKind) {
      if (options.some((o) => !o.trim())) {
        setError("Vui lòng điền đủ 4 đáp án.");
        return;
      }
      choices = options.map((content_, i) => ({ choiceLabel: String.fromCharCode(65 + i), content: content_.trim(), isCorrect: i === correctIndex, displayOrder: i + 1 }));
    }
    if (kind === "VOICE_MULTIPLE_CHOICE" && !audioUrl.trim()) {
      setError("Trắc nghiệm Voice cần có URL audio mẫu.");
      return;
    }
    if (kind === "FILL_IN_BLANK" && !correctAnswerText.trim()) {
      setError("Điền từ cần có đáp án đúng để hệ thống tự chấm.");
      return;
    }

    setSubmitting(true);
    try {
      let result: QuestionResponse;
      if (isEditing && existingQuestion) {
        const updateRequest = {
          content: content.trim(),
          audioUrl: kind === "VOICE_MULTIPLE_CHOICE" ? audioUrl.trim() || undefined : undefined,
          imageUrl: kind === "ESSAY" ? imageUrl.trim() || undefined : undefined,
          referencePassage: kind === "VOICE_MULTIPLE_CHOICE" ? transcript.trim() || undefined : kind === "SPEAKING" ? phoneticKeywords.trim() || undefined : undefined,
          explanation: explanation.trim() || undefined,
          correctAnswerText: kind === "FILL_IN_BLANK" ? correctAnswerText.trim() || undefined : undefined,
          choices
        };
        result = examId
          ? await updateExamQuestion(examId, existingQuestion.id, updateRequest)
          : await updateQuestion(existingQuestion.id, updateRequest);
      } else {
        const questionType: QuestionType = kind === "VOICE_MULTIPLE_CHOICE" ? "MULTIPLE_CHOICE" : kind;
        const request: CreateExamQuestionRequest = {
          questionType,
          skill: kind === "VOICE_MULTIPLE_CHOICE" ? "LISTENING" : kind === "SPEAKING" ? "SPEAKING" : undefined,
          difficulty,
          content: content.trim(),
          audioUrl: kind === "VOICE_MULTIPLE_CHOICE" ? audioUrl.trim() || undefined : undefined,
          imageUrl: kind === "ESSAY" ? imageUrl.trim() || undefined : undefined,
          referencePassage: kind === "VOICE_MULTIPLE_CHOICE" ? transcript.trim() || undefined : kind === "SPEAKING" ? phoneticKeywords.trim() || undefined : undefined,
          explanation: explanation.trim() || undefined,
          correctAnswerText: kind === "FILL_IN_BLANK" ? correctAnswerText.trim() || undefined : undefined,
          choices
        };
        if (examId) {
          result = await createExamQuestion(examId, request);
        } else if (questionBankId) {
          const legacyRequest: CreateQuestionRequest = { ...request, questionBankId };
          result = await createQuestion(legacyRequest);
        } else {
          throw new Error("Thiếu ngữ cảnh Đề/Ngân hàng câu hỏi.");
        }
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
          {(Object.entries(kindMeta) as [UiQuestionKind, (typeof kindMeta)[UiQuestionKind]][]).map(([value, meta]) => {
            const Icon = meta.icon;
            const active = kind === value;
            return (
              <button
                key={value}
                type="button"
                disabled={isEditing}
                onClick={() => setKind(value)}
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

      {(kind === "MULTIPLE_CHOICE" || kind === "VOICE_MULTIPLE_CHOICE") && (
        <div className="space-y-2 bg-slate-50 p-4 rounded-xl border border-slate-200">
          <div className="flex items-center justify-between mb-1">
            <span className="font-bold text-slate-700 uppercase tracking-wider text-[9px]">Thiết lập 4 đáp án & chọn 1 câu trả lời đúng</span>
            <span className="text-[9px] text-slate-400 font-bold">Hãy nhấp nút check để chọn đáp án đúng</span>
          </div>
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

      {kind === "VOICE_MULTIPLE_CHOICE" && (
        <div className="bg-blue-50/40 p-4 rounded-xl border border-blue-200 space-y-3">
          <div className="flex items-center gap-1 text-blue-900 font-bold uppercase tracking-wider text-[9px]">
            <Volume2 className="w-4 h-4 text-blue-600" />
            <span>Cấu hình file âm thanh / Transcript</span>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div>
              <label className="block font-bold text-slate-600 mb-1 text-[9px] uppercase">File Audio mẫu *</label>
              <FileUploadField value={audioUrl} onChange={setAudioUrl} onUpload={(file) => uploadMedia(file, "LMS_QUESTION")} accept="audio/*" placeholder="Chọn file audio..." />
            </div>
            <div>
              <label className="block font-bold text-slate-600 mb-1 text-[9px] uppercase">Ghi chú phát âm / Transcript</label>
              <input value={transcript} onChange={(e) => setTranscript(e.target.value)} placeholder="Nội dung đọc trong tệp audio giúp kiểm tra..." className={inputClass} />
            </div>
          </div>
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
