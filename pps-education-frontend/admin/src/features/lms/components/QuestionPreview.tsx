import React, { useState } from "react";
import { useTranslation } from "react-i18next";
import { ExerciseQuestionResponse } from "../api";

/**
 * Tách ra từ ExerciseStudentPreviewModal.tsx (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-10-05) — "QuestionPreview" render 1 câu hỏi ĐÚNG như học sinh sẽ tương tác (bấm chọn đáp án,
 * kéo/chạm sắp xếp câu, điền hộp từ vựng...), không phụ thuộc gì vào exerciseId/danh sách câu hỏi
 * (khác các block "grid" gộp nhiều câu còn lại ở ExerciseStudentPreviewModal.tsx, vẫn ở nguyên đó vì
 * cần groupKey/referencePassage/audioUrl dùng chung của cả nhóm). Dùng chung ở 2 nơi:
 * ExerciseStudentPreviewModal.tsx (xem trước cả Bài, dữ liệu thật từ API) và QuestionEditorForm.tsx
 * (xem trước SỐNG ngay khi đang soạn tay, dữ liệu dựng tạm từ state form, chưa lưu DB) — để GV thấy
 * đúng hình dạng câu hỏi sẽ hiện ra cho học sinh TRƯỚC KHI bấm Lưu, không cần đoán.
 */
export const CHOICE_TYPES = new Set(["MULTIPLE_CHOICE", "MULTIPLE_ANSWER", "TRUE_FALSE"]);

/** Bổ sung 2026-08-28 (mirror ExerciseStudentPreviewModal.tsx#chunkArray) — chia mảng thành các hàng cố định `size` phần tử, dùng để dựng bảng hộp từ vựng. */
function chunkArray<T>(items: T[], size: number): T[][] {
  const rows: T[][] = [];
  for (let i = 0; i < items.length; i += size) {
    rows.push(items.slice(i, i + size));
  }
  return rows;
}

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

export function ChoiceButtons({
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
            <div className="w-full aspect-[4/3] bg-white flex items-center justify-center overflow-hidden">
              <img src={c.imageUrl ?? undefined} alt={c.content} className="max-w-full max-h-full object-contain" />
            </div>
            <span className="flex items-center gap-1 px-2 py-1.5 text-[13px] font-bold text-slate-700">
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
          className={`w-full text-left text-sm font-bold px-3 py-2.5 rounded-xl border transition-colors ${
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

export function useChoiceSelection(isMulti: boolean) {
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

/**
 * Bổ sung 2026-09-17 (fix bug thật, người dùng báo mất ảnh sau khi import bài "Match the words with the
 * correct pictures") — DIEN_TU_HOP_TU_VUNG_ANH lưu NHIỀU URL ảnh nối bằng "|" trong CHUNG 1 cột imageUrl
 * (tối đa 10 ảnh/10 chỗ trống, xem QuestionImportService#mapToRequest) — gán thẳng cả string nối "|" vào
 * 1 <img src> duy nhất là 1 URL sai định dạng, ảnh không tải được nên không hiện ảnh nào. Tách thành danh
 * sách, hiện dạng lưới có số thứ tự khi có NHIỀU ảnh; các loại câu hỏi khác chỉ có 1 URL vẫn hiện như cũ.
 */
export function QuestionImages({ imageUrl }: { imageUrl: string }) {
  const urls = imageUrl
    .split("|")
    .map((u) => u.trim())
    .filter(Boolean);
  if (urls.length <= 1) {
    // Bổ sung 2026-09-18 — fix bug thật (người dùng báo ảnh "vỡ"/mờ): ảnh câu hỏi thường là icon/clipart
    // nhỏ (VD ~176x284px), max-w-sm (384px) phóng to quá xa kích thước gốc gây vỡ pixel khi w-full kéo
    // hết chiều rộng modal. Giảm mốc phóng tối đa xuống gần kích thước gốc hơn (mirror max-w-[240px] đã
    // dùng ở TakeExerciseModal.tsx app học sinh thật).
    return <img src={urls[0] ?? imageUrl} alt="" className="w-full max-w-[240px] rounded-xl border border-slate-200" />;
  }
  return (
    <div className="grid grid-cols-5 gap-2">
      {urls.map((url, i) => (
        <div key={i} className="space-y-1">
          <img src={url} alt="" className="w-full aspect-square object-contain rounded-lg border border-slate-200 bg-white" />
          <p className="text-center text-[12px] font-bold text-slate-400">{i + 1}</p>
        </div>
      ))}
    </div>
  );
}

export function SpeakingInputPreview() {
  const { t } = useTranslation("lms-question-authoring");
  return (
    <div className="space-y-1">
      <p className="text-[12px] text-slate-400 font-bold uppercase">{t("studentPreviewModal.recordAnswerLabel")}</p>
      <input
        type="file"
        accept="audio/*"
        disabled
        className="text-sm font-bold text-slate-400 file:mr-2 file:px-3 file:py-1.5 file:rounded-lg file:border-0 file:bg-slate-200 file:text-slate-500 file:text-sm file:font-extrabold opacity-70"
      />
      <p className="text-[12px] text-slate-400 italic">{t("studentPreviewModal.previewInputDisabledNote")}</p>
    </div>
  );
}

/** Hiện **chữ đậm** trong phiếu thông tin (chỉ cú pháp ** **, không phải Markdown đầy đủ). */
function renderFormBold(text: string): React.ReactNode {
  return text.split(/(\*\*[^*]+\*\*)/g).map((seg, i) =>
    seg.startsWith("**") && seg.endsWith("**") && seg.length > 4 ? <strong key={i}>{seg.slice(2, -2)}</strong> : seg
  );
}

/**
 * Mirror TakeExerciseModal#WordBankBlock (không lưu lại lựa chọn, chỉ để GV thử thao tác chọn).
 * inputMode="text" (bổ sung 2026-09-17, đã xác nhận với người dùng — DIEN_TU_DOAN_VAN ở BE): mỗi chỗ
 * trống đổi thành <input> gõ tay thay vì <select> — xem đúng lý do KHÔNG fallback wordPool=blanks
 * cho chế độ gõ tay ở TakeExerciseModal#WordBankBlock (tránh lộ sẵn đáp án trong hộp tham khảo).
 */
function WordBankPreview({
  content,
  wordPool,
  inputMode = "select",
  formMode = false,
  instruction = null,
  prefillAnswers
}: {
  content: string;
  wordPool: string[];
  inputMode?: "select" | "text";
  formMode?: boolean;
  instruction?: string | null;
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-05 — CHỈ dùng cho pane "Ví dụ minh họa"
   * ở QuestionEditorForm.tsx (dữ liệu giả cố định, không phải câu hỏi thật nên lộ ra không sao). Màn
   * "Xem trước (dạng học sinh)" thật (ExerciseStudentPreviewModal.tsx) KHÔNG truyền prop này — vẫn giữ
   * nguyên hành vi cũ, dropdown/ô nhập luôn bắt đầu TRỐNG để không lộ đáp án đúng cho GV xem trước.
   * Thiếu field này (undefined) = hành vi cũ.
   */
  prefillAnswers?: string[];
}) {
  const { t } = useTranslation("lms-question-authoring");
  // Bổ sung 2026-09-17, đã xác nhận với người dùng — content của bài đọc dạng "đoạn văn" (VD
  // DIEN_TU_DOAN_VAN) thường có dòng TIÊU ĐỀ đứng riêng (tách bởi \n\n) trước phần thân có "___", mirror
  // ĐÚNG cách nhận diện tiêu đề đã dùng cho referencePassage (xem parsePassageParagraphs) — tách tiêu đề
  // ra hiện in đậm+căn giữa riêng, phần thân còn lại mới đem tách theo "___" như cũ.
  const firstBreak = content.indexOf("\n\n");
  const titleCandidate = firstBreak === -1 ? "" : content.slice(0, firstBreak).trim();
  const isTitle = titleCandidate.length > 0 && titleCandidate.length <= 100 && !titleCandidate.includes("___") && !/[.!?]\s+[A-Z]/.test(titleCandidate);
  const title = isTitle ? titleCandidate : null;
  const body = isTitle ? content.slice(firstBreak + 2).trimStart() : content;
  const parts = body.split("___");
  const blankCount = parts.length - 1;
  const [selections, setSelections] = useState<string[]>(
    prefillAnswers && prefillAnswers.length === blankCount ? prefillAnswers : new Array(blankCount).fill("")
  );
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-05 — fix bug thật phát hiện qua phản hồi
   * người dùng: khi KHÔNG set riêng "hộp từ tham khảo" (wordBankOptions), wordPool tự rơi về ĐÚNG
   * `blanks` theo ĐÚNG thứ tự chỗ trống — hộp từ hiện ra đọc trái-qua-phải là ra ngay đáp án, không cần
   * hiểu gì cũng điền đúng hết. Xáo trộn thứ tự HIỂN THỊ trong hộp (mirror SentenceBuildingPreview) —
   * không đổi `wordPool` gốc dùng cho option/chấm, chỉ đổi thứ tự lúc vẽ bảng.
   */
  const shuffledWordPool = React.useMemo(() => {
    const arr = [...wordPool];
    for (let i = arr.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [arr[i], arr[j]] = [arr[j], arr[i]];
    }
    return arr;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSelect = (idx: number, value: string) => {
    setSelections((prev) => prev.map((s, i) => (i === idx ? value : s)));
  };

  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-09 — fix bug hiển thị thật: container
  // "flex flex-wrap" trước đây coi mỗi đoạn văn bản (<span>) là 1 flex item RIÊNG, khiến trình duyệt
  // xuống dòng theo TỪNG item thay vì cho chữ chảy liên tục như 1 đoạn văn bình thường (đoạn dài bị đẩy
  // xuống dòng riêng, dropdown lại đứng tách biệt dòng kế tiếp — không giống đề gốc). Đổi sang flow chữ
  // tự nhiên: <p> khối văn bản bình thường, <select>/<input> là inline-block xen giữa chữ, để trình
  // duyệt tự ngắt dòng theo TỪNG TỪ như văn bản thật.
  return (
    <div className="space-y-2">
      {instruction && <p className="text-xs italic text-slate-500">{instruction}</p>}
      {title && <p className="text-sm font-black text-slate-800 text-center">{title}</p>}
      {/*
       * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-05 — hộp từ hiện NGAY TRÊN câu hỏi
       * (khớp đúng đề giấy gốc GV gửi: "Ex.1 Choose the correct word in the box" → bảng từ → MỚI tới
       * các câu đánh số), không phải nằm dưới như trước. Hiện cho CẢ 2 chế độ (dropdown lẫn gõ tay) —
       * bản thân ô dropdown (có mũi tên, đã chọn sẵn 1 từ) với ô input trống đã đủ khác biệt khi nhìn,
       * không cần ẩn hộp để phân biệt. Dạng BẢNG (chunkArray 4 từ/hàng, viền ô) khớp ĐÚNG quy ước "hộp
       * từ vựng" đã có sẵn trong app (xem GridQuestionGroupPreview#wordBox ở ExerciseStudentPreviewModal.tsx,
       * dùng cho "Nhiều câu điền từ"/DIEN_TU_NHOM).
       */}
      {wordPool.length > 0 && (
        <div className="space-y-1">
          <p className="text-[12px] font-bold text-slate-400 uppercase tracking-wider">{t("studentPreviewModal.wordBankBoxLabel")}</p>
          <table className="w-full border-collapse text-sm text-slate-800">
            <tbody>
              {chunkArray(shuffledWordPool, 4).map((row, ri) => (
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
        </div>
      )}
      <p className={`text-sm ${formMode ? "font-medium whitespace-pre-line" : "font-bold"} text-slate-800 leading-8`}>
        {parts.map((part, idx) => (
          <React.Fragment key={idx}>
            {formMode ? renderFormBold(part) : part}
            {idx < blankCount &&
              (inputMode === "text" ? (
                <input
                  type="text"
                  value={selections[idx]}
                  onChange={(e) => handleSelect(idx, e.target.value)}
                  className="bg-slate-50 border border-slate-200 text-sm font-bold px-2 py-1 mx-1 rounded-lg align-middle focus:outline-none w-24"
                />
              ) : (
                <select
                  value={selections[idx]}
                  onChange={(e) => handleSelect(idx, e.target.value)}
                  className="bg-slate-50 border border-slate-200 text-sm font-bold px-2 py-1 mx-1 rounded-lg align-middle focus:outline-none"
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
              ))}
          </React.Fragment>
        ))}
      </p>
    </div>
  );
}

/** Mirror TakeExerciseModal#SentenceBuildingBlock (chạm từng khối theo thứ tự, không lưu lại). */
function SentenceBuildingPreview({ chunkPool }: { chunkPool: string[] }) {
  const { t } = useTranslation("lms-question-authoring");
  const shuffled = React.useMemo(() => {
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
        {built.length === 0 && <span className="text-[13px] text-slate-400 italic px-1">{t("studentPreviewModal.sentenceBuildingInstructions")}</span>}
        {built.map((text, position) => (
          <button
            key={position}
            type="button"
            onClick={() => setUsedIndices((prev) => prev.filter((_, i) => i !== position))}
            className="px-2.5 py-1 rounded-lg bg-emerald-50 border border-emerald-400 text-sm font-bold text-emerald-700"
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
            className="px-2.5 py-1 rounded-lg bg-white border border-slate-200 text-sm font-bold text-slate-700 hover:bg-slate-50"
          >
            {s.text}
          </button>
        ))}
      </div>
    </div>
  );
}

export function QuestionPreview({
  question,
  displayNumber,
  revealAnswers = false
}: {
  question: ExerciseQuestionResponse;
  displayNumber: number;
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-05 — CHỈ bật true ở pane "Ví dụ minh họa"
   * (QuestionEditorForm.tsx, dữ liệu giả cố định MOCK_PREVIEW_QUESTIONS). ExerciseStudentPreviewModal.tsx
   * (màn "Xem trước dạng học sinh" THẬT) không truyền prop này — mặc định false, giữ nguyên hành vi
   * không lộ đáp án đúng cho GV xem trước trước khi giao bài.
   */
  revealAnswers?: boolean;
}) {
  const { t } = useTranslation("lms-question-authoring");
  const isChoice = CHOICE_TYPES.has(question.questionType) && question.choices.length > 0;
  const { selected, toggle } = useChoiceSelection(question.questionType === "MULTIPLE_ANSWER");

  return (
    <div className="border border-slate-200 rounded-[16px] p-4 sm:p-5 space-y-3">
      <div className="flex items-start justify-between gap-3">
        {/*
         * Fix bug thật 2026-09-17 (đã xác nhận với người dùng qua ảnh chụp, mirror TakeExerciseModal.tsx
         * bên app user) — WORD_BANK dùng questionContent làm CHÍNH VĂN BẢN TƯƠNG TÁC (WordBankPreview
         * render ngay bên dưới với <select>/<input> thật), hiện lại nguyên văn ở đây gây lặp cả đoạn 2
         * lần. Ẩn span nội dung cho riêng WORD_BANK, chỉ giữ số thứ tự.
         */}
        <p className="text-sm font-bold text-slate-800">
          <span className="block text-slate-400 text-sm uppercase tracking-wider mb-1">
            {t("studentPreviewModal.questionNumberPrefix", { number: displayNumber })}
          </span>
          {question.questionType !== "WORD_BANK" && <span className="whitespace-pre-line">{question.questionContent}</span>}
        </p>
        <span className="text-[12px] text-slate-400 font-bold shrink-0">{t("studentPreviewModal.pointsSuffix", { points: question.points })}</span>
      </div>

      {question.skill === "LISTENING" && question.audioUrl && (
        // eslint-disable-next-line jsx-a11y/media-has-caption
        <audio controls src={question.audioUrl} className="w-full" />
      )}

      {question.imageUrl && <QuestionImages imageUrl={question.imageUrl} />}

      {isChoice ? (
        <ChoiceButtons choices={question.choices} selected={selected} onToggle={toggle} />
      ) : question.questionType === "SPEAKING" ? (
        <SpeakingInputPreview />
      ) : question.questionType === "WORD_BANK" && question.structuredContent?.blanks ? (
        <WordBankPreview
          content={question.questionContent}
          wordPool={
            question.structuredContent.inputMode === "text"
              ? (question.structuredContent.wordBankOptions ?? [])
              : (question.structuredContent.wordBankOptions ?? question.structuredContent.blanks)
          }
          inputMode={question.structuredContent.inputMode}
          formMode={question.structuredContent.format === "form"}
          instruction={question.structuredContent.format === "form" ? question.referencePassage : null}
          prefillAnswers={revealAnswers ? question.structuredContent.blanks : undefined}
        />
      ) : question.questionType === "SENTENCE_BUILDING" && question.structuredContent?.chunks ? (
        <SentenceBuildingPreview chunkPool={question.structuredContent.chunks} />
      ) : (
        <textarea
          rows={question.questionType === "FILL_IN_BLANK" ? 1 : 3}
          placeholder={t("studentPreviewModal.answerPlaceholder")}
          className="w-full bg-slate-50 border border-slate-200 text-sm p-3 rounded-xl focus:outline-none"
        />
      )}
    </div>
  );
}
