import React, { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import Modal from "@/components/ui/Modal";
import { ExerciseQuestionResponse, ExerciseResponse, listExerciseQuestions } from "../api";

const CHOICE_TYPES = new Set(["MULTIPLE_CHOICE", "MULTIPLE_ANSWER", "TRUE_FALSE"]);

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-03 — fix bug thật: đáp án ảnh
 * (VOICE_PICTURE_CHOICE) khi soạn để trống chú thích thì hệ thống tự điền content = đúng chữ cái nhãn
 * (VD content="A" cho choiceLabel="A", xem ListeningGroupBuilder.tsx/QuestionEditorForm.tsx) — hiện ra
 * nhìn như lặp "A. A". Ẩn phần content khi nó trùng hệt choiceLabel (không phân biệt hoa/thường, đã
 * trim) hoặc rỗng, chỉ còn lại chữ cái nhãn — không lặp.
 */
function hasMeaningfulCaption(choiceLabel: string, content: string): boolean {
  const trimmed = content.trim();
  return trimmed.length > 0 && trimmed.toUpperCase() !== choiceLabel.trim().toUpperCase();
}

/** Bổ sung 2026-08-28 — chia mảng thành các hàng cố định `size` phần tử, dùng để dựng bảng hộp từ vựng (wordBox). */
function chunkArray<T>(items: T[], size: number): T[][] {
  const rows: T[][] = [];
  for (let i = 0; i < items.length; i += size) {
    rows.push(items.slice(i, i + size));
  }
  return rows;
}

type RenderBlock =
  | { type: "single"; question: ExerciseQuestionResponse }
  | {
      type: "grid";
      groupKey: string;
      referencePassage: string | null;
      audioUrl: string | null;
      wordBox: string[] | null;
      questions: ExerciseQuestionResponse[];
    };

/** Mirror portal/src/features/portal/components/TakeExerciseModal.tsx#groupQuestionsByGroupKey — cùng quy tắc gộp câu hỏi "Đọc hiểu — lưới"/"1 audio nhiều câu" mà học sinh thấy. */
function groupQuestionsByGroupKey(questions: ExerciseQuestionResponse[]): RenderBlock[] {
  const blocks: RenderBlock[] = [];
  for (const q of questions) {
    const last = blocks[blocks.length - 1];
    if (q.groupKey && last && last.type === "grid" && last.groupKey === q.groupKey) {
      last.questions.push(q);
      continue;
    }
    if (q.groupKey) {
      blocks.push({
        type: "grid",
        groupKey: q.groupKey,
        referencePassage: q.referencePassage,
        audioUrl: q.audioUrl,
        wordBox: q.structuredContent?.wordBox ?? null,
        questions: [q]
      });
    } else {
      blocks.push({ type: "single", question: q });
    }
  }
  return blocks;
}

function computeStartNumbers(blocks: RenderBlock[]): number[] {
  const starts: number[] = [];
  let n = 1;
  for (const b of blocks) {
    starts.push(n);
    n += b.type === "grid" ? b.questions.length : 1;
  }
  return starts;
}

interface ExerciseStudentPreviewModalProps {
  exercise: ExerciseResponse;
  onClose: () => void;
}

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-03 — GV yêu cầu xem trước đề ĐÚNG NHƯ
 * học sinh sẽ tương tác (bấm chọn đáp án, kéo/chạm sắp xếp câu, điền hộp từ vựng...) TRƯỚC KHI giao
 * bài, không cần đợi giao xong rồi mới biết. Khác hẳn ExercisePreviewModal (tô sáng đáp án đúng, chỉ
 * đọc) — modal này chỉ dùng endpoint listExerciseQuestions (KHÔNG có choices[].isCorrect, xem Javadoc
 * ExerciseQuestionResponse ở BE) nên không thể lộ đáp án dù có bug, và không gọi bất kỳ API tạo lượt
 * làm bài/lưu câu trả lời nào — mọi lựa chọn chỉ tồn tại trong state cục bộ của trình duyệt GV.
 *
 * KHÔNG tái dùng trực tiếp TakeExerciseModal (app `user`, tách repo/build riêng khỏi app `admin` —
 * không có workspace chung để import chéo) — các khối câu hỏi tương tác ở dưới được viết lại tối giản,
 * bám sát đúng loại câu hỏi/kiểu tương tác của bản gốc, chấp nhận có thể lệch nhau về sau nếu
 * TakeExerciseModal đổi UI mà không cập nhật lại bản này.
 */
export default function ExerciseStudentPreviewModal({ exercise, onClose }: ExerciseStudentPreviewModalProps) {
  const { t } = useTranslation("lms-question-authoring");
  const [questions, setQuestions] = useState<ExerciseQuestionResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    listExerciseQuestions(exercise.id)
      .then((qs) => setQuestions([...qs].sort((a, b) => a.displayOrder - b.displayOrder)))
      .finally(() => setLoading(false));
  }, [exercise.id]);

  const blocks = useMemo(() => groupQuestionsByGroupKey(questions), [questions]);
  const startNumbers = useMemo(() => computeStartNumbers(blocks), [blocks]);

  return (
    <Modal
      open
      onClose={onClose}
      title={t("studentPreviewModal.modalTitle", { title: exercise.title })}
      description={t("studentPreviewModal.modalDescription")}
      size="xl"
    >
      <div className="mb-4 text-[11px] font-bold text-amber-700 bg-amber-50 border border-amber-200 rounded-xl p-3">
        {t("studentPreviewModal.disclaimer")}
      </div>

      {loading ? (
        <p className="text-xs text-slate-500 text-center py-6">{t("exercisePreviewModal.loading")}</p>
      ) : questions.length === 0 ? (
        <p className="text-xs text-slate-400 italic text-center py-6">{t("exercisePreviewModal.empty")}</p>
      ) : (
        <div className="space-y-5 max-h-[70vh] overflow-y-auto pr-1">
          {blocks.map((block, i) =>
            block.type === "grid" ? (
              <GridQuestionGroupPreview key={block.groupKey} block={block} startNumber={startNumbers[i]} />
            ) : (
              <QuestionPreview key={block.question.id} question={block.question} displayNumber={startNumbers[i]} />
            )
          )}
        </div>
      )}
    </Modal>
  );
}

function ChoiceButtons({
  choices,
  selected,
  onToggle
}: {
  choices: ExerciseQuestionResponse["choices"];
  selected: Set<number>;
  onToggle: (choiceId: number) => void;
}) {
  if (choices.some((c) => c.imageUrl)) {
    // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-03 — fix bug thật (mirror
    // TakeExerciseModal bên app user): số cột lưới khớp đúng số đáp án (tối đa 4/hàng) thay vì
    // grid-cols-2 cố định gây lệch hàng khi có 3 đáp án; ảnh dùng khung nền trắng cố định +
    // object-contain thay vì object-cover phóng to/cắt ảnh để lấp ô lớn, tránh vỡ hình.
    const gridColsClass = choices.length >= 4 ? "grid-cols-4" : choices.length === 3 ? "grid-cols-3" : "grid-cols-2";
    return (
      <div className={`grid gap-2 ${gridColsClass}`}>
        {choices.map((c) => (
          <button
            key={c.id}
            type="button"
            onClick={() => onToggle(c.id)}
            className={`text-left rounded-xl border-2 overflow-hidden transition-colors ${
              selected.has(c.id) ? "border-emerald-500 bg-emerald-50" : "border-slate-200 bg-slate-50 hover:bg-slate-100"
            }`}
          >
            <div className="w-full aspect-[4/5] bg-white flex items-center justify-center overflow-hidden">
              <img src={c.imageUrl ?? undefined} alt={c.content} className="max-w-full max-h-full object-contain" />
            </div>
            <span className="flex items-center gap-1 px-2 py-1.5 text-[11px] font-bold text-slate-700">
              <span className="text-slate-400 mr-1">{c.choiceLabel}.</span>
              {hasMeaningfulCaption(c.choiceLabel, c.content) && c.content}
            </span>
          </button>
        ))}
      </div>
    );
  }
  return (
    <div className="space-y-1.5">
      {choices.map((c) => (
        <button
          key={c.id}
          type="button"
          onClick={() => onToggle(c.id)}
          className={`w-full text-left text-xs font-bold px-3 py-2.5 rounded-xl border transition-colors ${
            selected.has(c.id) ? "border-emerald-500 bg-emerald-50 text-emerald-800" : "border-slate-200 bg-slate-50 hover:bg-slate-100 text-slate-700"
          }`}
        >
          <span className="text-slate-400 mr-1.5">{c.choiceLabel}.</span>
          {c.content}
        </button>
      ))}
    </div>
  );
}

function useChoiceSelection(isMulti: boolean) {
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const toggle = (choiceId: number) => {
    setSelected((prev) => {
      const next = new Set(isMulti ? prev : []);
      if (next.has(choiceId)) next.delete(choiceId);
      else next.add(choiceId);
      return next;
    });
  };
  return { selected, toggle };
}

function QuestionPreview({ question, displayNumber }: { question: ExerciseQuestionResponse; displayNumber: number }) {
  const { t } = useTranslation("lms-question-authoring");
  const isChoice = CHOICE_TYPES.has(question.questionType) && question.choices.length > 0;
  const { selected, toggle } = useChoiceSelection(question.questionType === "MULTIPLE_ANSWER");

  return (
    <div className="border border-slate-200 rounded-[16px] p-4 sm:p-5 space-y-3">
      <div className="flex items-start justify-between gap-3">
        <p className="text-sm font-bold text-slate-800">
          <span className="block text-slate-400 text-xs uppercase tracking-wider mb-1">
            {t("studentPreviewModal.questionNumberPrefix", { number: displayNumber })}
          </span>
          <span className="whitespace-pre-line">{question.questionContent}</span>
        </p>
        <span className="text-[10px] text-slate-400 font-bold shrink-0">{t("studentPreviewModal.pointsSuffix", { points: question.points })}</span>
      </div>

      {question.skill === "LISTENING" && question.audioUrl && (
        // eslint-disable-next-line jsx-a11y/media-has-caption
        <audio controls src={question.audioUrl} className="w-full" />
      )}

      {question.imageUrl && <img src={question.imageUrl} alt="" className="w-full max-w-sm rounded-xl border border-slate-200" />}

      {isChoice ? (
        <ChoiceButtons choices={question.choices} selected={selected} onToggle={toggle} />
      ) : question.questionType === "SPEAKING" ? (
        <SpeakingInputPreview />
      ) : question.questionType === "WORD_BANK" && question.structuredContent?.blanks ? (
        <WordBankPreview content={question.questionContent} wordPool={question.structuredContent.wordBankOptions ?? question.structuredContent.blanks} />
      ) : question.questionType === "SENTENCE_BUILDING" && question.structuredContent?.chunks ? (
        <SentenceBuildingPreview chunkPool={question.structuredContent.chunks} />
      ) : (
        <textarea
          rows={question.questionType === "FILL_IN_BLANK" ? 1 : 3}
          placeholder={t("studentPreviewModal.answerPlaceholder")}
          className="w-full bg-slate-50 border border-slate-200 text-xs p-3 rounded-xl focus:outline-none"
        />
      )}
    </div>
  );
}

function SpeakingInputPreview() {
  const { t } = useTranslation("lms-question-authoring");
  return (
    <div className="space-y-1">
      <p className="text-[10px] text-slate-400 font-bold uppercase">{t("studentPreviewModal.recordAnswerLabel")}</p>
      <input
        type="file"
        accept="audio/*"
        disabled
        className="text-xs font-bold text-slate-400 file:mr-2 file:px-3 file:py-1.5 file:rounded-lg file:border-0 file:bg-slate-200 file:text-slate-500 file:text-xs file:font-extrabold opacity-70"
      />
      <p className="text-[10px] text-slate-400 italic">{t("studentPreviewModal.previewInputDisabledNote")}</p>
    </div>
  );
}

/** Mirror TakeExerciseModal#WordBankBlock (không lưu lại lựa chọn, chỉ để GV thử thao tác chọn). */
function WordBankPreview({ content, wordPool }: { content: string; wordPool: string[] }) {
  const { t } = useTranslation("lms-question-authoring");
  const parts = content.split("___");
  const blankCount = parts.length - 1;
  const [selections, setSelections] = useState<string[]>(new Array(blankCount).fill(""));

  const handleSelect = (idx: number, value: string) => {
    setSelections((prev) => prev.map((s, i) => (i === idx ? value : s)));
  };

  return (
    <div className="flex flex-wrap items-center gap-x-1.5 gap-y-2 text-sm font-bold text-slate-800 leading-8">
      {parts.map((part, idx) => (
        <React.Fragment key={idx}>
          {part && <span>{part}</span>}
          {idx < blankCount && (
            <select
              value={selections[idx]}
              onChange={(e) => handleSelect(idx, e.target.value)}
              className="bg-slate-50 border border-slate-200 text-xs font-bold px-2 py-1.5 rounded-lg focus:outline-none"
            >
              <option value="">{t("studentPreviewModal.wordBankChoosePlaceholder")}</option>
              {wordPool
                .filter((w) => w === selections[idx] || !selections.includes(w))
                .map((w, wIdx) => (
                  <option key={`${w}-${wIdx}`} value={w}>
                    {w}
                  </option>
                ))}
            </select>
          )}
        </React.Fragment>
      ))}
    </div>
  );
}

/** Mirror TakeExerciseModal#SentenceBuildingBlock (chạm từng khối theo thứ tự, không lưu lại). */
function SentenceBuildingPreview({ chunkPool }: { chunkPool: string[] }) {
  const { t } = useTranslation("lms-question-authoring");
  const shuffled = useMemo(() => {
    const arr = chunkPool.map((text, idx) => ({ text, idx }));
    for (let i = arr.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [arr[i], arr[j]] = [arr[j], arr[i]];
    }
    return arr;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const [usedIndices, setUsedIndices] = useState<number[]>([]);

  const built = usedIndices.map((i) => shuffled.find((s) => s.idx === i)?.text ?? "");
  const available = shuffled.filter((s) => !usedIndices.includes(s.idx));

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-1.5 min-h-[38px] p-2 bg-slate-50 rounded-xl border border-dashed border-slate-200">
        {built.length === 0 && <span className="text-[11px] text-slate-400 italic px-1">{t("studentPreviewModal.sentenceBuildingInstructions")}</span>}
        {built.map((text, position) => (
          <button
            key={position}
            type="button"
            onClick={() => setUsedIndices((prev) => prev.filter((_, i) => i !== position))}
            className="px-2.5 py-1 rounded-lg bg-emerald-50 border border-emerald-400 text-xs font-bold text-emerald-700"
          >
            {text}
          </button>
        ))}
      </div>
      <div className="flex flex-wrap gap-1.5">
        {available.map((s) => (
          <button
            key={s.idx}
            type="button"
            onClick={() => setUsedIndices((prev) => [...prev, s.idx])}
            className="px-2.5 py-1 rounded-lg bg-white border border-slate-200 text-xs font-bold text-slate-700 hover:bg-slate-50"
          >
            {s.text}
          </button>
        ))}
      </div>
    </div>
  );
}

/** Mirror TakeExerciseModal#GridQuestionGroup — "Đọc hiểu — lưới" / "1 audio nhiều câu". */
function GridQuestionGroupPreview({ block, startNumber }: { block: Extract<RenderBlock, { type: "grid" }>; startNumber: number }) {
  return (
    <div className="border border-slate-200 rounded-[16px] p-4 sm:p-5 space-y-3">
      {/* Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-03 — CHỦ Ý không dùng whitespace-pre-wrap (khác TakeExerciseModal gốc): dữ liệu referencePassage thường có sẵn dấu xuống dòng "cứng" copy từ Word/PDF gốc, giữ nguyên khiến đoạn văn ngắt dòng sớm theo dữ liệu thay vì trải đều hết chiều rộng khung, nhìn lệch hẳn sang trái. Để trình duyệt tự ngắt dòng theo chiều rộng thật (mirror ExercisePreviewModal đã làm đúng cách này). */}
      {block.referencePassage && <p className="text-xs text-slate-600 bg-slate-50 rounded-xl p-3">{block.referencePassage}</p>}

      {block.wordBox && block.wordBox.length > 0 && (
        <table className="w-full border-collapse text-xs text-slate-800">
          <tbody>
            {chunkArray(block.wordBox, 4).map((row, ri) => (
              <tr key={ri}>
                {row.map((w, ci) => (
                  <td key={ci} className="border border-slate-200 text-center font-bold px-2 py-2">
                    {w}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {block.audioUrl && (
        // eslint-disable-next-line jsx-a11y/media-has-caption
        <audio controls src={block.audioUrl} className="w-full" />
      )}

      <div className="divide-y divide-slate-100">
        {block.questions.map((q, qIndex) => (
          <GridQuestionRowPreview key={q.id} question={q} displayNumber={startNumber + qIndex} />
        ))}
      </div>
    </div>
  );
}

function GridQuestionRowPreview({ question, displayNumber }: { question: ExerciseQuestionResponse; displayNumber: number }) {
  const { t } = useTranslation("lms-question-authoring");
  const isChoiceRow = CHOICE_TYPES.has(question.questionType) && question.choices.length > 0;
  const isFillInBlankRow = question.questionType === "FILL_IN_BLANK";
  const isSpeakingRow = question.questionType === "SPEAKING";
  const { selected, toggle } = useChoiceSelection(question.questionType === "MULTIPLE_ANSWER");

  return (
    <div className="py-2.5 space-y-2">
      <span className="text-xs font-bold text-slate-800">
        {displayNumber}. {question.questionContent}
      </span>

      {question.imageUrl && <img src={question.imageUrl} alt="" className="w-full max-w-sm rounded-xl border border-slate-200" />}

      {isChoiceRow && <ChoiceButtons choices={question.choices} selected={selected} onToggle={toggle} />}

      {isFillInBlankRow && (
        <input
          placeholder={t("studentPreviewModal.answerPlaceholder")}
          className="w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-xl focus:outline-none"
        />
      )}

      {isSpeakingRow && <SpeakingInputPreview />}
    </div>
  );
}
