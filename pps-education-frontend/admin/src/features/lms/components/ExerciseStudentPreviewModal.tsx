import React, { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import Modal from "@/components/ui/Modal";
import { renderRichText } from "@/lib/richText";
import { ExerciseQuestionResponse, ExerciseResponse, listExerciseQuestions } from "../api";
import {
  CHOICE_TYPES,
  ChoiceButtons,
  QuestionImages,
  QuestionPreview,
  SentenceBuildingPreview,
  SpeakingInputPreview,
  WordBankPreview,
  useChoiceSelection
} from "./QuestionPreview";

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
function parsePassageParagraphs(text: string): { name: string | null; content: string; isTitle: boolean; isSectionBreak: boolean }[] {
  const withTurnBreaks = text.replace(/\n(?=\s*(?:[^\n:]{1,40}:\s|\d+\.\s))/g, "\n\n");
  const rawParagraphs = withTurnBreaks
    .split(/\n\s*\n/)
    .map((para) => para.replace(/\s*\n\s*/g, " ").trim())
    .filter(Boolean);
  // Xet TIEU DE truoc khi thu tach "Ten: noi dung" -- tieu de tu than co the chua dau ":" (VD
  // "COLLECTING: A VALUABLE ACTIVITY OR JUST A HOBBY?"), tach theo ":" truoc se lam mat 1 nua tieu de.
  return rawParagraphs.map((para, i) => {
    const isTitle = i === 0 && rawParagraphs.length > 1 && para.length <= 100 && !/[.!?]\s+[A-Z]/.test(para);
    if (isTitle) return { name: null, content: para, isTitle: true, isSectionBreak: false };
    // Bổ sung 2026-10-17 (đã xác nhận với người dùng, mirror TakeExerciseModal.tsx bên app user — ảnh
    // chụp đoạn "SCHOOL FACILITIES" đứng GIỮA referencePassage không in đậm vì isTitle CHỈ áp dụng đoạn
    // ĐẦU TIÊN) — đoạn VIẾT HOA TOÀN BỘ, ngắn, không chứa ":" ở BẤT KỲ vị trí nào đều coi là tiêu đề PHỤ
    // — in đậm + có đường kẻ ngăn cách phía trên để tách rõ với nhóm nội dung ngay trước đó.
    const isAllCapsHeading = i > 0 && para.length <= 100 && /[A-Za-z]/.test(para) && para === para.toUpperCase() && !para.includes(":");
    if (isAllCapsHeading) return { name: null, content: para, isTitle: false, isSectionBreak: true };
    const match = para.match(/^([^:\n]{1,40}):\s*([\s\S]+)$/);
    return match
      ? { name: match[1].trim(), content: match[2].trim(), isTitle: false, isSectionBreak: false }
      : { name: null, content: para, isTitle: false, isSectionBreak: false };
  });
}

type RenderBlock =
  | { type: "single"; question: ExerciseQuestionResponse }
  | {
      type: "grid";
      groupKey: string;
      referencePassage: string | null;
      audioUrl: string | null;
      // Bổ sung 2026-10-19 (đã xác nhận với người dùng — ANH_DIEN_TU) — ảnh DÙNG CHUNG cho cả nhóm
      // (mirror referencePassage/audioUrl ở trên), khác imageUrl RIÊNG từng câu ở DIEN_TU_NHOM (xem
      // isSharedImageGroup bên dưới).
      imageUrl: string | null;
      wordBox: string[] | null;
      instruction: string | null;
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
        imageUrl: q.imageUrl,
        wordBox: q.structuredContent?.wordBox ?? null,
        instruction: q.structuredContent?.instruction ?? null,
        questions: [q]
      });
    } else {
      blocks.push({ type: "single", question: q });
    }
  }
  return blocks;
}

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-20 (V218 — "Số thứ tự hiển thị ghi đè") —
 * TRƯỚC ĐÂY hàm này tự đếm tuần tự thuần túy từ 1 theo VỊ TRÍ mảng, bỏ qua hẳn
 * `exercise_questions.display_order` THẬT đã lưu trong DB (dù `questions` lấy từ listExerciseQuestions,
 * API thật) — bug có sẵn TRƯỚC KHI có tính năng ghi đè: panel xem trước luôn hiện số 1,2,3... dù học
 * sinh làm bài thật (TakeExerciseModal.tsx) lại hiện ĐÚNG displayOrder đã lưu, 2 nơi lệch nhau bất kỳ
 * khi nào displayOrder không trùng khít vị trí mảng (VD GV ghi đè số hiển thị, hoặc sắp xếp lại câu hỏi
 * theo cách khác không liền mạch từ 1). Sửa: đọc THẲNG displayOrder của câu ĐẦU TIÊN mỗi khối — khớp
 * đúng 1 nguồn dữ liệu DUY NHẤT với màn học sinh làm bài thật, không tính toán lại gì thêm.
 */
function computeStartNumbers(blocks: RenderBlock[]): number[] {
  return blocks.map((b) => (b.type === "grid" ? b.questions[0].displayOrder : b.question.displayOrder));
}

/**
 * Bổ sung 2026-10-29 (đã xác nhận với người dùng) — khi nhiều "phần" khác nhau (VD IELTS Listening
 * Section 1: Q1-6 "điền phiếu thông tin" + Q7-8 "chọn 2 đáp án") được gộp CHUNG 1 groupKey vì cùng 1
 * URL Audio (xem backend QuestionImportService#AUDIO_GROUPABLE_KINDS), khối chỉ hiện ĐÚNG 1 "Hướng dẫn"
 * duy nhất ở đầu (của câu đầu tiên) — Hướng dẫn của các phần sau trong CÙNG khối bị "nuốt" mất hoàn
 * toàn, học sinh không biết câu 7-8 đổi sang dạng bài khác. Tính lại ranh giới: câu nào có
 * structuredContent.instruction KHÁC với Hướng dẫn gần nhất đã hiện (kể cả Hướng dẫn ở đầu khối) thì
 * hiện lại 1 dòng Hướng dẫn mới ngay trước câu đó — chỉ audio là DÙNG CHUNG 1 LẦN, còn Hướng dẫn vẫn
 * tách đúng theo cột "Hướng dẫn" của từng dòng Excel gốc.
 */
function attachSectionInstructions(
  questions: ExerciseQuestionResponse[],
  blockInstruction: string | null
): { question: ExerciseQuestionResponse; sectionInstruction: string | null }[] {
  let lastInstruction = blockInstruction;
  return questions.map((question) => {
    const instruction = question.structuredContent?.instruction ?? null;
    const sectionInstruction = instruction != null && instruction !== lastInstruction ? instruction : null;
    if (sectionInstruction != null) lastInstruction = sectionInstruction;
    return { question, sectionInstruction };
  });
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
  // Bổ sung 2026-10-19 (đã xác nhận với người dùng — ANH_TRAC_NGHIEM/ANH_DIEN_TU): phân biệt với
  // isDistinctPictureMatchGrid bên dưới — ở ĐÂY cả nhóm dùng CHUNG ĐÚNG 1 ảnh (mirror
  // referencePassage/audioUrl, hiện 1 LẦN DUY NHẤT ở đầu khối), bất kể questionType, khác DIEN_TU_NHOM
  // nơi MỖI câu có 1 ảnh KHÁC NHAU (hiện lặp lại trong lưới nhỏ bên dưới).
  const isSharedImageGroup =
    block.imageUrl != null && block.questions.length >= 2 && block.questions.every((q) => q.imageUrl === block.imageUrl);
  const isDistinctPictureMatchGrid =
    !isSharedImageGroup &&
    block.questions.length >= 2 &&
    block.questions.every((q) => q.imageUrl && q.questionType === "FILL_IN_BLANK" && isBlankOnlyContent(q.questionContent));
  return (
    <div className="border border-slate-200 rounded-[16px] p-4 sm:p-5 space-y-3">
      {/* V3 2026-09-04 — xem Javadoc parsePassageParagraphs: mỗi đoạn hiện tên nhân vật thành dòng tiêu
          đề in đậm riêng (khớp đúng hình thức đề giấy gốc), nội dung đoạn tự ngắt dòng theo khung. */}
      {block.referencePassage && (
        <div className="bg-slate-50 rounded-xl p-3 space-y-2">
          {parsePassageParagraphs(block.referencePassage).map((p, i) => (
            <div key={i} className={p.isSectionBreak ? "pt-2 mt-1 border-t border-slate-200" : undefined}>
              {p.name && <p className="text-sm font-black text-slate-800">{renderRichText(p.name)}</p>}
              {p.isTitle || p.isSectionBreak ? (
                <p className="text-sm font-black text-slate-800 text-center">{renderRichText(p.content)}</p>
              ) : (
                <p className="text-sm text-slate-600 whitespace-pre-line">{renderRichText(p.content)}</p>
              )}
            </div>
          ))}
        </div>
      )}
      {/*
       * Bổ sung 2026-10-19 (đã xác nhận với người dùng — ANH_TRAC_NGHIEM/ANH_DIEN_TU) — ảnh DÙNG CHUNG
       * hiện 1 LẦN DUY NHẤT ở đây (mirror block.referencePassage ở trên), KHÔNG lặp lại trong từng dòng
       * câu hỏi bên dưới (xem prop hideImage ở GridQuestionRowPreview).
       *
       * Fix bug thật 2026-10-30 (đã xác nhận với người dùng qua ảnh chụp, so sánh đề giấy gốc) — trước
       * đây hiện SAU "Hướng dẫn"/hộp từ vựng/audio — sai thứ tự so với đề giấy gốc (ảnh/biểu đồ tham
       * chiếu luôn đứng NGAY SAU tiêu đề Ex., TRƯỚC câu lệnh "A. Complete..."). Chuyển lên đây, ngay sau
       * đoạn văn tham chiếu — mirror đúng vị trí referencePassage.
       */}
      {isSharedImageGroup && block.imageUrl && <QuestionImages imageUrl={block.imageUrl} large />}

      {/*
       * Fix bug thật 2026-10-29 (đã xác nhận với người dùng qua ảnh chụp, so sánh đề giấy gốc) — "Hướng
       * dẫn" trước đây hiện TRƯỚC đoạn văn tham chiếu (referencePassage) — sai thứ tự so với đề giấy gốc
       * (đoạn văn đọc hiểu đứng TRƯỚC, "Hướng dẫn"/câu lệnh câu hỏi đứng NGAY TRÊN câu 1, SAU đoạn văn).
       * Chuyển xuống đây (sau đoạn văn/ảnh tham chiếu, trước hộp từ vựng/câu hỏi) để khớp đúng bố cục sách.
       */}
      {block.instruction && <p className="text-sm sm:text-base italic font-bold text-slate-500 whitespace-pre-line">{renderRichText(block.instruction)}</p>}

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
       * — bài "Match the words with their correct picture" (DIEN_TU_NHOM, mỗi câu 1 ảnh KHÁC NHAU) đúng
       * hình thức sách in là LƯỚI ảnh nhỏ gọn, ô điền ngay dưới mỗi ảnh — không liệt kê dọc từng câu 1 dòng.
       */}
      {isDistinctPictureMatchGrid ? (
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
          {attachSectionInstructions(block.questions, block.instruction).map(({ question: q, sectionInstruction }, qIndex) => {
            // Fix bug thật 2026-10-29 (đã xác nhận với người dùng qua ảnh chụp) — câu WORD_BANK tự hiện
            // "Hướng dẫn" riêng qua prop `instruction` của WordBankPreview bên dưới (xem GridQuestionRowPreview),
            // nếu CŨNG hiện thêm đoạn <p> sectionInstruction ở đây thì "Hướng dẫn" bị lặp 2 LẦN cho câu
            // WORD_BANK (1 lần ở đây, 1 lần trong WordBankPreview) — kể cả khi sectionInstruction đã null
            // (trùng block.instruction) vẫn còn 1 lần lặp từ WordBankPreview vì nó đọc THẲNG
            // question.structuredContent.instruction, không biết gì về việc block đã hiện rồi. Truyền
            // sectionInstruction xuống làm NGUỒN DUY NHẤT cho WordBankPreview#instruction (xem
            // GridQuestionRowPreview) thay vì đọc lại structuredContent.instruction — tự động null khi
            // trùng Hướng dẫn đã hiện (ở block hoặc section trước), không cần đoạn <p> riêng nữa.
            const isWordBankRow = q.questionType === "WORD_BANK" && !!q.structuredContent?.blanks;
            return (
              <React.Fragment key={q.id}>
                {sectionInstruction && !isWordBankRow && (
                  <p className="pt-2 text-sm sm:text-base italic font-bold text-slate-500 whitespace-pre-line">{renderRichText(sectionInstruction)}</p>
                )}
                <GridQuestionRowPreview
                  question={q}
                  displayNumber={startNumber + qIndex}
                  hideImage={isSharedImageGroup}
                  sectionInstruction={sectionInstruction}
                />
              </React.Fragment>
            );
          })}
        </div>
      )}
    </div>
  );
}

function GridQuestionRowPreview({
  question,
  displayNumber,
  hideImage,
  sectionInstruction
}: {
  question: ExerciseQuestionResponse;
  displayNumber: number;
  /** Bổ sung 2026-10-19 (đã xác nhận với người dùng — ANH_DIEN_TU) — true khi ảnh của câu này đã hiện
   * 1 LẦN DUY NHẤT ở đầu khối nhóm (block.imageUrl, xem GridQuestionGroupPreview), không lặp lại ở đây. */
  hideImage?: boolean;
  /** Bổ sung 2026-10-29 (đã xác nhận với người dùng) — "Hướng dẫn" ĐÃ qua dedup ở GridQuestionGroupPreview
   * (null nếu trùng Hướng dẫn đã hiện ở block hoặc câu liền trước) — dùng làm nguồn DUY NHẤT cho
   * WordBankPreview#instruction bên dưới, xem Javadoc ở chỗ gọi component này. */
  sectionInstruction?: string | null;
}) {
  const { t } = useTranslation("lms-question-authoring");
  const isChoiceRow = CHOICE_TYPES.has(question.questionType) && question.choices.length > 0;
  const isFillInBlankRow = question.questionType === "FILL_IN_BLANK";
  const isSpeakingRow = question.questionType === "SPEAKING";
  // Fix bug thật (người dùng báo "thiếu block chọn từ/chỗ lưu đáp án" ở màn Xem trước dạng học sinh)
  // — mirror fix ở TakeExerciseModal.tsx#GridQuestionGroup bên app user: khối lưới (grid, có groupKey)
  // trước đây CHỈ rẽ nhánh isChoiceRow/isFillInBlankRow/isSpeakingRow, bỏ sót hẳn WORD_BANK/
  // SENTENCE_BUILDING (đã có sẵn ở QuestionPreview#câu ĐƠN LẺ) — câu "Sắp xếp từ"/"Điền hộp từ vựng"
  // nằm trong 1 nhóm (VD "Ex. 3" gộp Part A sắp xếp câu + Part B điền từ) không hiện khối chọn từ nào.
  const isWordBankRow = question.questionType === "WORD_BANK" && !!question.structuredContent?.blanks;
  const isSentenceBuildingRow = question.questionType === "SENTENCE_BUILDING" && !!question.structuredContent?.chunks;
  const { selected, toggle } = useChoiceSelection(question.questionType === "MULTIPLE_ANSWER");
  // Bổ sung 2026-10-19 (đã xác nhận với người dùng — ANH_DIEN_TU) — khi ảnh đã hiện dùng chung ở đầu
  // khối (hideImage=true) VÀ nội dung chỉ là "___" (GV không gõ số nữa, số thứ tự đã hiện qua
  // displayNumber) — ẩn luôn phần nội dung lặp, chỉ còn "{displayNumber}." gọn gàng.
  // WordBankPreview bên dưới tự render questionContent làm chính văn bản tương tác (mirror
  // QuestionPreview#WORD_BANK câu đơn lẻ) — hiện lại nguyên văn ở đây sẽ bị lặp cả đoạn 2 lần.
  const hideContentText = isWordBankRow || (hideImage && isBlankOnlyContent(question.questionContent));

  return (
    <div className="py-2.5 space-y-2">
      <span className="text-sm font-normal text-slate-800">
        {displayNumber}.{!hideContentText && <> {renderRichText(question.questionContent)}</>}
      </span>

      {!hideImage && question.imageUrl && <QuestionImages imageUrl={question.imageUrl} />}

      {isChoiceRow && <ChoiceButtons choices={question.choices} selected={selected} onToggle={toggle} />}

      {isFillInBlankRow && (
        <input
          placeholder={t("studentPreviewModal.answerPlaceholder")}
          className="w-full bg-slate-50 border border-slate-200 text-sm p-2.5 rounded-xl focus:outline-none"
        />
      )}

      {isSpeakingRow && <SpeakingInputPreview />}

      {isWordBankRow && (
        <WordBankPreview
          content={question.questionContent}
          wordPool={
            question.structuredContent!.inputMode === "text"
              ? (question.structuredContent!.wordBankOptions ?? [])
              : (question.structuredContent!.wordBankOptions ?? question.structuredContent!.blanks!)
          }
          inputMode={question.structuredContent!.inputMode}
          formMode={question.structuredContent!.format === "form"}
          instruction={sectionInstruction ?? (question.structuredContent!.format === "form" ? question.referencePassage : null)}
        />
      )}

      {isSentenceBuildingRow && (
        <SentenceBuildingPreview chunkPool={question.structuredContent!.chunks!} chunkOrder={question.structuredContent!.chunkOrder} />
      )}
    </div>
  );
}
