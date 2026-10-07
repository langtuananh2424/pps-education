import React, { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import Modal from "@/components/ui/Modal";
import { ExerciseQuestionResponse, ExerciseResponse, listExerciseQuestions } from "../api";
import { CHOICE_TYPES, ChoiceButtons, QuestionImages, QuestionPreview, SpeakingInputPreview, useChoiceSelection } from "./QuestionPreview";

/**
 * Bổ sung 2026-09-24 (đã xác nhận với người dùng, sửa bug thật) — lưới ảnh "Match the words with their
 * correct picture" chỉ hiện ảnh + số thứ tự, BỎ nội dung câu. Trước đây áp cho MỌI nhóm FILL_IN_BLANK có
 * ảnh, kể cả bài có câu thật (VD "Ex. 3: ___ is my pen here.") → học sinh mất nội dung câu. Chỉ coi là
 * lưới ảnh khi nội dung mỗi câu KHÔNG có chữ thật (chỉ số thứ tự + chỗ trống, VD "1. ___").
 */
function isBlankOnlyContent(content: string | null | undefined): boolean {
  return (content ?? "").replace(/[\d.)_\s]/g, "") === "";
}

/** Bổ sung 2026-08-28 — chia mảng thành các hàng cố định `size` phần tử, dùng để dựng bảng hộp từ vựng (wordBox). */
function chunkArray<T>(items: T[], size: number): T[][] {
  const rows: T[][] = [];
  for (let i = 0; i < items.length; i += size) {
    rows.push(items.slice(i, i + size));
  }
  return rows;
}

/**
 * V3 (bổ sung 2026-09-04, đã xác nhận với người dùng — mirror TakeExerciseModal.tsx bên app user vì
 * không import chéo được) — chỉ coi dòng trống (2+ \n liên tiếp) là ranh giới đoạn văn thật (VD 3 đoạn
 * Tom/Max/Anna của "Bài đọc hiểu — Lưới", GridQuestionBuilder nối bằng "\n\n" theo dạng "Tên: nội
 * dung") — giữ lại làm dòng trống hiển thị, MỌI \n đơn lẻ còn lại (rác copy-paste Word/PDF) gộp thành
 * khoảng trắng; đồng thời tách riêng tên nhân vật khỏi nội dung để hiện thành dòng tiêu đề in đậm
 * riêng, khớp đúng hình thức đề giấy gốc — đoạn không khớp mẫu "Tên: nội dung" thì hiện nguyên văn.
 *
 * V4 (fix bug thật 2026-09-08, đã xác nhận với người dùng qua ảnh chụp, mirror TakeExerciseModal.tsx
 * bên app user) — transcript bài Nghe dạng hội thoại dán từ trang web/PDF thường CHỈ có 1 \n giữa mỗi
 * lượt nói (không có dòng trống thật) — chèn thêm 1 dòng trống ẢO trước mỗi dòng bắt đầu bằng
 * "Tên:"/"N." TRƯỚC khi split.
 */
/**
 * V4 2026-09-13 (đã xác nhận với người dùng) — đoạn ĐẦU TIÊN đứng riêng (tách bởi dòng trống), không
 * khớp dạng "Nhãn: nội dung", và đủ ngắn/không có dấu chấm câu giữa đoạn (không phải câu văn thường) thì
 * coi là TIÊU ĐỀ bài đọc (VD "THE BENEFITS OF GARDENING") -- in đậm giống hệt "Nhãn:" thay vì hiện như
 * một đoạn văn thường.
 */
function parsePassageParagraphs(text: string): { name: string | null; content: string; isTitle: boolean }[] {
  const withTurnBreaks = text.replace(/\n(?=\s*(?:[^\n:]{1,40}:\s|\d+\.\s))/g, "\n\n");
  const rawParagraphs = withTurnBreaks
    .split(/\n\s*\n/)
    .map((para) => para.replace(/\s*\n\s*/g, " ").trim())
    .filter(Boolean);
  // Xet TIEU DE truoc khi thu tach "Ten: noi dung" -- tieu de tu than co the chua dau ":" (VD
  // "COLLECTING: A VALUABLE ACTIVITY OR JUST A HOBBY?"), tach theo ":" truoc se lam mat 1 nua tieu de.
  return rawParagraphs.map((para, i) => {
    const isTitle = i === 0 && rawParagraphs.length > 1 && para.length <= 100 && !/[.!?]\s+[A-Z]/.test(para);
    if (isTitle) return { name: null, content: para, isTitle: true };
    const match = para.match(/^([^:\n]{1,40}):\s*([\s\S]+)$/);
    return match ? { name: match[1].trim(), content: match[2].trim(), isTitle: false } : { name: null, content: para, isTitle: false };
  });
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
      <div className="mb-4 text-[13px] font-bold text-amber-700 bg-amber-50 border border-amber-200 rounded-xl p-3">
        {t("studentPreviewModal.disclaimer")}
      </div>

      {loading ? (
        <p className="text-sm text-slate-500 text-center py-6">{t("exercisePreviewModal.loading")}</p>
      ) : questions.length === 0 ? (
        <p className="text-sm text-slate-400 italic text-center py-6">{t("exercisePreviewModal.empty")}</p>
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

/** Mirror TakeExerciseModal#GridQuestionGroup — "Đọc hiểu — lưới" / "1 audio nhiều câu". */
function GridQuestionGroupPreview({ block, startNumber }: { block: Extract<RenderBlock, { type: "grid" }>; startNumber: number }) {
  const { t } = useTranslation("lms-question-authoring");
  return (
    <div className="border border-slate-200 rounded-[16px] p-4 sm:p-5 space-y-3">
      {/* V3 2026-09-04 — xem Javadoc parsePassageParagraphs: mỗi đoạn hiện tên nhân vật thành dòng tiêu
          đề in đậm riêng (khớp đúng hình thức đề giấy gốc), nội dung đoạn tự ngắt dòng theo khung. */}
      {block.referencePassage && (
        <div className="bg-slate-50 rounded-xl p-3 space-y-2">
          {parsePassageParagraphs(block.referencePassage).map((p, i) => (
            <div key={i}>
              {p.name && <p className="text-sm font-black text-slate-800">{p.name}</p>}
              {p.isTitle ? (
                <p className="text-sm font-black text-slate-800 text-center">{p.content}</p>
              ) : (
                <p className="text-sm text-slate-600 whitespace-pre-line">{p.content}</p>
              )}
            </div>
          ))}
        </div>
      )}

      {block.wordBox && block.wordBox.length > 0 && (
        <table className="w-full border-collapse text-sm text-slate-800">
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

      {/*
       * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-18, mirror TakeExerciseModal#GridQuestionGroup
       * — bài "Match the words with their correct picture" (DIEN_TU_NHOM, mỗi câu 1 ảnh riêng) đúng hình
       * thức sách in là LƯỚI ảnh nhỏ gọn, ô điền ngay dưới mỗi ảnh — không liệt kê dọc từng câu 1 dòng.
       */}
      {block.questions.length >= 2 && block.questions.every((q) => q.imageUrl && q.questionType === "FILL_IN_BLANK" && isBlankOnlyContent(q.questionContent)) ? (
        <div className="grid grid-cols-3 sm:grid-cols-5 gap-3">
          {block.questions.map((q, qIndex) => (
            <div key={q.id} className="space-y-1.5">
              <img src={q.imageUrl ?? undefined} alt="" className="w-full aspect-square object-contain rounded-xl border border-slate-200 bg-white" />
              <p className="text-center text-[12px] font-bold text-slate-400">{startNumber + qIndex}</p>
              <input
                placeholder={t("studentPreviewModal.answerPlaceholder")}
                className="w-full bg-slate-50 border border-slate-200 text-sm p-2 rounded-xl text-center focus:outline-none"
              />
            </div>
          ))}
        </div>
      ) : (
        <div className="divide-y divide-slate-100">
          {block.questions.map((q, qIndex) => (
            <GridQuestionRowPreview key={q.id} question={q} displayNumber={startNumber + qIndex} />
          ))}
        </div>
      )}
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
      <span className="text-sm font-bold text-slate-800">
        {displayNumber}. {question.questionContent}
      </span>

      {question.imageUrl && <QuestionImages imageUrl={question.imageUrl} />}

      {isChoiceRow && <ChoiceButtons choices={question.choices} selected={selected} onToggle={toggle} />}

      {isFillInBlankRow && (
        <input
          placeholder={t("studentPreviewModal.answerPlaceholder")}
          className="w-full bg-slate-50 border border-slate-200 text-sm p-2.5 rounded-xl focus:outline-none"
        />
      )}

      {isSpeakingRow && <SpeakingInputPreview />}
    </div>
  );
}
