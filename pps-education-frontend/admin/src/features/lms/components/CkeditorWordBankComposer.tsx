import React, { useRef, useState } from "react";
import { CKEditor } from "@ckeditor/ckeditor5-react";
import {
  ClassicEditor,
  Essentials,
  Paragraph,
  Bold,
  Italic,
  Underline,
  Strikethrough,
  Subscript,
  Superscript,
  Table,
  TableToolbar,
  TableProperties,
  TableCellProperties,
  TableColumnResize,
  Alignment,
  FontSize,
  FontFamily,
  PasteFromOffice,
  Heading,
  List,
  ListProperties,
  TodoList,
  Indent,
  IndentBlock,
  Link,
  Image,
  ImageInsertViaUrl,
  ImageUpload,
  ImageToolbar,
  ImageCaption,
  ImageStyle,
  ImageResize,
  ImageResizeHandles,
  ImageTextAlternative,
  BlockQuote,
  RemoveFormat,
  SpecialCharacters,
  SpecialCharactersEssentials,
  Highlight,
  HorizontalLine,
  ShowBlocks,
  type Editor
} from "ckeditor5";
import "ckeditor5/ckeditor5.css";
import "ckeditor5/ckeditor5-content.css";
import { Eye, EyeOff, ListOrdered, Wand2 } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import Button from "@/components/ui/Button";
import FloatingError from "@/components/ui/FloatingError";
import Select from "@/components/ui/Select";
import {
  QuestionDifficulty,
  QuestionResponse,
  createExamCkeditorChoiceQuestion,
  createExamCkeditorChunkQuestion,
  createExamCkeditorEssayQuestion,
  createExamCkeditorGroupQuestions,
  createExamCkeditorMultipartQuestions,
  createExamCkeditorWordBankQuestion,
  updateCkeditorQuestion,
  updateExamCkeditorQuestion
} from "../api";
import InlineChoiceWidgetPlugin from "../ckeditor/InlineChoiceWidgetPlugin";
import GridWidgetPlugin from "../ckeditor/GridWidgetPlugin";
import BlankWidgetPlugin, { collectBlanksInDocumentOrder } from "../ckeditor/BlankWidgetPlugin";
import WordBankWidgetPlugin from "../ckeditor/WordBankWidgetPlugin";
import ChoiceWidgetPlugin from "../ckeditor/ChoiceWidgetPlugin";
import ChunkWidgetPlugin, { collectChunksInDocumentOrder } from "../ckeditor/ChunkWidgetPlugin";
import AudioWidgetPlugin from "../ckeditor/AudioWidgetPlugin";
import JustifyCleanupPlugin from "../ckeditor/JustifyCleanupPlugin";
import QuestionWidgetsMenuPlugin from "../ckeditor/QuestionWidgetsMenuPlugin";
import { PpsImageUploadAdapterPlugin } from "../ckeditor/mediaUpload";
import CkeditorHtmlPreview from "../ckeditor/CkeditorHtmlPreview";
import {
  buildCkeditorEditHtml,
  countCkeditorParts,
  extractBlanksInDocumentOrder,
  extractChoiceGroupsInDocumentOrder,
  extractChunkGroupsInDocumentOrder,
  extractGridsInDocumentOrder,
  extractInlineChoicesInDocumentOrder,
  getCorrectChunkOrder
} from "../ckeditor/blankParser";
import "../ckeditor/ckeditor.css";

const inputClass = "w-full bg-white border border-slate-200 text-sm px-3.5 py-2 rounded-lg focus:outline-none focus:ring-1 focus:ring-brand-red";
const labelClass = "block font-bold text-slate-700 mb-1 uppercase tracking-wider text-[12px]";

const INITIAL_DATA = "";
// Placeholder thật của CKEditor (chữ mờ, không nằm trong dữ liệu) — trước đây câu hướng dẫn này là
// nội dung thật nên bị lưu vào đề nếu GV quên xoá.
const PLACEHOLDER = 'Nhập đề bài, rồi bấm "Chèn câu hỏi ▾" (góc phải thanh công cụ) để chèn Ô trống/Nhóm lựa chọn..., hoặc gõ/dán ___ để tạo ô trống.';

/**
 * Tab "Soạn tự do (CKEditor5)" ở CreateAndAssignExerciseModal.tsx — soạn giữ layout/bảng/ảnh tự do
 * (không ép về text thường như 2 tab còn lại), hệ thống TỰ SUY RA loại câu hỏi từ widget GV dùng,
 * không có dropdown "loại câu hỏi" riêng (đã xác nhận với người dùng):
 * - CÓ Ô trống (không Nhóm lựa chọn) → QuestionType.WORD_BANK, content giữ NGUYÊN HTML (xem
 *   CkeditorWordBankParser.java — Portal học sinh cần tầng hiển thị HTML riêng, chưa xây).
 * - CÓ ĐÚNG 1 Nhóm lựa chọn (không Ô trống) → MULTIPLE_CHOICE/MULTIPLE_ANSWER (suy theo số lựa
 *   chọn đúng), widget bị BÓC khỏi content, chuyển thành choices[] chuẩn — tái dùng 100% UI/chấm
 *   điểm Học viên hiện có (xem CkeditorChoiceParser.java — đã xác nhận với người dùng khi quyết
 *   định hướng này, tránh hiện lựa chọn lặp 2 lần).
 * - CÓ ĐÚNG 1 Nhóm khối sắp xếp (không Ô trống/Nhóm lựa chọn) → SENTENCE_BUILDING, widget bị BÓC
 *   khỏi content, chuyển thành structuredContent.chunks[]/chunkOrder[] chuẩn — tái dùng 100% UI/
 *   chấm điểm Học viên hiện có (xem CkeditorChunkParser.java — cùng lý do với Nhóm lựa chọn).
 * - Trộn ≥2 trong 3 loại trên, hoặc >1 nhóm cùng loại, trong CÙNG 1 câu → chặn Lưu, báo tách thành
 *   nhiều câu (1 Question chỉ mang đúng 1 QuestionType — xem handleSubmit).
 * - Widget "Audio" dùng ĐƯỢC CHUNG với cả 3 loại trên (không loại trừ nhau) — backend tự trích URL
 *   + set skill=LISTENING, bóc khỏi content (tránh phát lặp 2 trình phát).
 * - CÓ 1 Bảng lưới, HOẶC các Lựa chọn trong câu → 1 lần tạo sinh NHIỀU câu trắc nghiệm cùng nhóm
 *   (mỗi dòng lưới / mỗi ô chọn 1 câu, chung đoạn văn) — đúng dữ liệu GridQuestionBuilder/
 *   ClozeQuestionBuilder (xem CkeditorGroupParser.java). "Điểm" lúc này là điểm của MỖI câu con.
 * - Ô trống nhận nhiều đáp án đúng, cách nhau bằng "/" (backend chấm sẵn theo quy ước này).
 */
export default function CkeditorWordBankComposer({
  examId,
  skillCategoryLabel,
  existingQuestion,
  onCreated,
  onCreatedMany,
  onCancel
}: {
  /** Bắt buộc khi TẠO mới; khi SỬA: có = sửa theo Đề, không có = sửa ở Ngân hàng câu hỏi. */
  examId?: number;
  /** Nhãn Nhóm kỹ năng của Bài (Reading/Writing/...) — chỉ hiện đọc, KHÔNG sửa (đã gán ở bước tạo Bài). */
  skillCategoryLabel: string;
  /** Có = chế độ SỬA câu đã tạo bằng CKE (giữ nguyên loại câu — xem buildCkeditorEditHtml). */
  existingQuestion?: QuestionResponse;
  /** Khi sửa: nhận câu hỏi đã cập nhật. */
  onCreated: (question: QuestionResponse) => void;
  /** Bảng lưới / Lựa chọn trong câu tạo nhiều câu trong 1 lần — gắn cả loạt vào Bài. */
  onCreatedMany?: (questions: QuestionResponse[]) => void;
  onCancel: () => void;
}) {
  const isEditing = !!existingQuestion;
  const [html, setHtml] = useState(() => (existingQuestion ? buildCkeditorEditHtml(existingQuestion) : INITIAL_DATA));
  const editorRef = useRef<Editor | null>(null);
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng — fix bug thật: "Áp dụng" trước đây quét lại
  // collectBlanksInDocumentOrder(editor) MỘT LẦN NỮA bên trong lệnh (ApplyBulkAnswersCommand), có
  // thể trả về thứ tự/số lượng LỆCH so với lúc mở panel (người dùng báo chỉ 4/10 ô được gán dù đã
  // gõ đủ 10). Giữ NGUYÊN tham chiếu element đã quét lúc mở panel, áp thẳng vào đúng các element đó
  // khi bấm Áp dụng — không quét lại lần 2, loại bỏ khả năng lệch thứ tự.
  const bulkBlankElementsRef = useRef<ReturnType<typeof collectBlanksInDocumentOrder>>([]);
  const [difficulty, setDifficulty] = useState<QuestionDifficulty>("MEDIUM");
  const [points, setPoints] = useState(existingQuestion?.defaultPoints != null ? String(existingQuestion.defaultPoints) : "1");
  const [explanation, setExplanation] = useState(existingQuestion?.explanation ?? "");
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-11-15 — nhãn số hiển thị TUỲ CHỌN, dùng
  // khi 1 câu "chiếm" nhiều ô đánh số liền nhau trong đề gốc (VD IELTS "Questions 14 and 15 —
  // Choose TWO letters A-E"). Thuần hiển thị — không ảnh hưởng thứ tự câu tiếp theo trong Bài.
  const [displayNumberLabel, setDisplayNumberLabel] = useState("");
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-11-15 — câu "Tự luận" (ESSAY, chấm AI)
  // KHÔNG cần widget đáp án nào (không có Ô trống/Nhóm lựa chọn/...), ngược hẳn validate mặc định
  // của handleSubmit (đòi hỏi ÍT NHẤT 1 widget) — GV bật cờ này để báo rõ ý định, tránh nhầm giữa
  // "quên chèn widget" và "cố ý không cần widget vì đây là câu Tự luận".
  const [isEssayMode, setIsEssayMode] = useState(existingQuestion?.questionType === "ESSAY");
  const [showPreview, setShowPreview] = useState(false);
  const [bulkPanelOpen, setBulkPanelOpen] = useState(false);
  const [bulkAnswers, setBulkAnswers] = useState<string[]>([]);
  // "Gán thứ tự đúng" (Khối sắp xếp) — kéo-thả 1 bản nháp RIÊNG (không đụng thứ tự HIỂN THỊ thật
  // trong bài, chỉ set attribute "order" khi Áp dụng) — mirror pattern bulkBlankElementsRef ở trên.
  const [chunkOrderPanelOpen, setChunkOrderPanelOpen] = useState(false);
  const [chunkOrderDraft, setChunkOrderDraft] = useState<{ chunkId: string; text: string; order: number }[]>([]);
  const draggedChunkIndexRef = useRef<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const blanks = extractBlanksInDocumentOrder(html);
  const unconfiguredCount = blanks.filter((b) => !b.answered).length;
  const choiceGroups = extractChoiceGroupsInDocumentOrder(html);
  const chunkGroups = extractChunkGroupsInDocumentOrder(html);
  const inlineChoices = extractInlineChoicesInDocumentOrder(html);
  const grids = extractGridsInDocumentOrder(html);
  // Số câu con sẽ sinh ra nếu trang đang chứa Bảng lưới / Lựa chọn trong câu (0 = tạo 1 câu như thường).
  const subQuestionCount = inlineChoices.length + grids.reduce((sum, g) => sum + g.rows.length, 0);
  // ≥2 phần ngăn bằng đường kẻ ngang → bài nhiều phần (mỗi phần 1 loại, học sinh thấy chung 1 khung).
  const partCount = isEditing ? 0 : countCkeditorParts(html);

  function openChunkOrderPanel() {
    const editor = editorRef.current;
    if (!editor || chunkGroups.length === 0) return;
    const group = chunkGroups[0];
    const correctOrder = getCorrectChunkOrder(group);
    setChunkOrderDraft(
      group.chunks.map((chunk) => ({ chunkId: chunk.chunkId, text: chunk.text, order: correctOrder.indexOf(chunk.chunkId) + 1 }))
    );
    setChunkOrderPanelOpen(true);
  }

  function applyChunkOrderPanel() {
    const editor = editorRef.current;
    if (!editor) return;
    const updates: Record<string, number> = {};
    chunkOrderDraft.forEach((c) => (updates[c.chunkId] = c.order));
    const elements = collectChunksInDocumentOrder(editor);
    editor.model.change((writer) => {
      elements.forEach((chunk) => {
        if (chunk.chunkId in updates) writer.setAttribute("order", updates[chunk.chunkId], chunk.element);
      });
    });
    setHtml(editor.getData());
    setChunkOrderPanelOpen(false);
  }

  // Kéo-thả để sắp lại "thứ tự đúng" — vị trí SAU khi thả quyết định luôn giá trị order (gán lại
  // tuần tự 1..N theo vị trí mới). CHỈ đổi thứ tự trong panel cấu hình này — KHÔNG đụng thứ tự
  // HIỂN THỊ thật trong bài (vẫn giữ nguyên như GV đã gõ).
  function handleChunkDrop(dropIndex: number) {
    const draggedIndex = draggedChunkIndexRef.current;
    draggedChunkIndexRef.current = null;
    if (draggedIndex === null || draggedIndex === dropIndex) return;
    setChunkOrderDraft((prev) => {
      const next = [...prev];
      const [moved] = next.splice(draggedIndex, 1);
      next.splice(dropIndex, 0, moved);
      return next.map((c, i) => ({ ...c, order: i + 1 }));
    });
  }

  function openBulkPanel() {
    const editor = editorRef.current;
    if (!editor) return;
    const current = collectBlanksInDocumentOrder(editor);
    bulkBlankElementsRef.current = current;
    setBulkAnswers(current.map((b) => b.answer));
    setBulkPanelOpen(true);
  }

  function applyBulkPanel() {
    const editor = editorRef.current;
    if (!editor) return;
    const elements = bulkBlankElementsRef.current;
    editor.model.change((writer) => {
      elements.forEach((blank, index) => {
        if (index >= bulkAnswers.length) return;
        writer.setAttribute("answer", bulkAnswers[index].trim(), blank.element);
        writer.setAttribute("answered", true, blank.element);
      });
    });
    setHtml(editor.getData());
    setBulkPanelOpen(false);
  }

  const submitUpdate = async (question: QuestionResponse) => {
    setSubmitting(true);
    try {
      const request = {
        html,
        explanation: explanation.trim() || undefined,
        defaultPoints: points ? Number(points) : undefined
      };
      const result = examId != null
        ? await updateExamCkeditorQuestion(examId, question.id, request)
        : await updateCkeditorQuestion(question.id, request);
      onCreated(result);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Lưu câu hỏi thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleSubmit = async () => {
    setError(null);
    const hasBlanks = blanks.length > 0;
    const hasChoiceGroups = choiceGroups.length > 0;
    const hasChunkGroups = chunkGroups.length > 0;
    const hasGrids = grids.length > 0;
    const hasInlineChoices = inlineChoices.length > 0;
    const kindCount = [hasBlanks, hasChoiceGroups, hasChunkGroups, hasGrids, hasInlineChoices].filter(Boolean).length;

    if (isEssayMode) {
      if (kindCount > 0) {
        setError("Câu Tự luận không được có Ô trống/Nhóm lựa chọn/Khối sắp xếp/Bảng lưới/Lựa chọn trong câu — xoá các widget đó hoặc bỏ đánh dấu \"Tự luận\" nếu câu này thật ra cần đáp án tự động.");
        return;
      }
      if (existingQuestion) {
        await submitUpdate(existingQuestion);
        return;
      }
      if (examId == null) return;
      setSubmitting(true);
      try {
        const result = await createExamCkeditorEssayQuestion(examId, {
          html,
          difficulty,
          defaultPoints: points ? Number(points) : undefined,
          explanation: explanation.trim() || undefined,
          displayNumberLabel: displayNumberLabel.trim() || undefined
        });
        onCreated(result);
        editorRef.current?.setData(INITIAL_DATA);
        setHtml(INITIAL_DATA);
        setExplanation("");
        setDisplayNumberLabel("");
      } catch (err) {
        setError(err instanceof ApiError ? err.message : "Tạo câu hỏi thất bại.");
      } finally {
        setSubmitting(false);
      }
      return;
    }

    if (partCount >= 2) {
      if (unconfiguredCount > 0) {
        setError(`Còn ${unconfiguredCount} ô trống chưa gán đáp án — chọn ô trống rồi bấm "Gán đáp án ô trống".`);
        return;
      }
      if (examId == null) return;
      setSubmitting(true);
      try {
        onCreatedMany?.(
          await createExamCkeditorMultipartQuestions(examId, {
            html,
            difficulty,
            defaultPoints: points ? Number(points) : undefined,
            explanation: explanation.trim() || undefined
          })
        );
        editorRef.current?.setData(INITIAL_DATA);
        setHtml(INITIAL_DATA);
        setExplanation("");
      } catch (err) {
        setError(err instanceof ApiError ? err.message : "Tạo câu hỏi thất bại.");
      } finally {
        setSubmitting(false);
      }
      return;
    }

    if (kindCount === 0) {
      setError("Chưa có Ô trống, Nhóm lựa chọn, Khối sắp xếp (cần ít nhất 2 khối liền nhau trong cùng 1 dòng), Bảng lưới hay Lựa chọn trong câu nào — dùng các nút tương ứng trên thanh công cụ, gõ/dán ___ để tạo ô trống, hoặc tick \"Câu Tự luận\" nếu đây là câu không cần đáp án tự động.");
      return;
    }
    if (kindCount > 1) {
      setError("Không trộn nhiều loại (Ô trống / Nhóm lựa chọn / Khối sắp xếp / Bảng lưới / Lựa chọn trong câu) trong cùng 1 lần tạo — tách riêng (tạo phần này xong, soạn tiếp phần kia).");
      return;
    }
    if (hasGrids) {
      if (grids.length > 1) {
        setError("Mỗi lần tạo chỉ hỗ trợ ĐÚNG 1 Bảng lưới — tách riêng nếu cần nhiều bảng.");
        return;
      }
      const missingRow = grids[0].rows.findIndex((r) => r.correctIndex < 0 || !r.text);
      if (missingRow >= 0) {
        setError(`Dòng ${missingRow + 1} của Bảng lưới chưa có nội dung hoặc chưa chọn đáp án đúng — đặt con trỏ vào dòng rồi bấm "Chọn đáp án dòng".`);
        return;
      }
    }
    if (hasChoiceGroups && choiceGroups.length > 1) {
      setError("Mỗi câu chỉ hỗ trợ ĐÚNG 1 Nhóm lựa chọn — tách thành nhiều câu riêng nếu cần nhiều nhóm.");
      return;
    }
    if (hasChunkGroups && chunkGroups.length > 1) {
      setError("Mỗi câu chỉ hỗ trợ ĐÚNG 1 nhóm Khối sắp xếp (các khối cùng 1 dòng) — tách thành nhiều câu riêng nếu cần nhiều nhóm.");
      return;
    }
    if (hasBlanks && unconfiguredCount > 0) {
      setError(`Còn ${unconfiguredCount} ô trống chưa gán đáp án — chọn ô trống rồi bấm "Gán đáp án ô trống" (để trống có chủ đích cũng phải xác nhận qua nút này).`);
      return;
    }
    if (hasChoiceGroups) {
      const group = choiceGroups[0];
      if (group.options.length < 2) {
        setError("Nhóm lựa chọn cần ít nhất 2 lựa chọn.");
        return;
      }
      if (!group.options.some((o) => o.isCorrect)) {
        setError("Chưa đánh dấu lựa chọn nào là đáp án đúng — chọn 1 lựa chọn rồi bấm \"Đánh dấu đúng/sai\".");
        return;
      }
    }

    if (existingQuestion) {
      if (hasGrids || hasInlineChoices) {
        setError("Khi sửa không chuyển được câu này thành Bảng lưới/Lựa chọn trong câu (tạo ra nhiều câu) — hãy tạo câu mới.");
        return;
      }
      await submitUpdate(existingQuestion);
      return;
    }
    if (examId == null) return;

    setSubmitting(true);
    try {
      const request = {
        html,
        difficulty,
        defaultPoints: points ? Number(points) : undefined,
        explanation: explanation.trim() || undefined,
        displayNumberLabel: displayNumberLabel.trim() || undefined
      };
      if (hasGrids || hasInlineChoices) {
        onCreatedMany?.(await createExamCkeditorGroupQuestions(examId, request));
      } else {
        const result = hasChoiceGroups
          ? await createExamCkeditorChoiceQuestion(examId, request)
          : hasChunkGroups
            ? await createExamCkeditorChunkQuestion(examId, request)
            : await createExamCkeditorWordBankQuestion(examId, request);
        onCreated(result);
      }
      editorRef.current?.setData(INITIAL_DATA);
      setHtml(INITIAL_DATA);
      setExplanation("");
      setDisplayNumberLabel("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Tạo câu hỏi thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-3 text-sm">
      <FloatingError message={error} onClose={() => setError(null)} />

      {isEditing && (
        <div className="text-[13px] text-slate-600 bg-slate-50 border border-slate-200 rounded-lg px-3 py-2">
          Đang sửa câu #{existingQuestion!.id} — loại <b>{existingQuestion!.questionType}</b> (giữ nguyên loại khi sửa; muốn đổi loại hãy tạo câu mới).
        </div>
      )}

      <div className={`grid grid-cols-1 gap-3 ${isEditing ? "sm:grid-cols-2" : "sm:grid-cols-4"}`}>
        <div>
          <label className={labelClass}>Nhóm kỹ năng</label>
          <div className="bg-slate-100 border border-slate-200 text-sm px-3.5 py-2 rounded-lg text-slate-500">{skillCategoryLabel}</div>
        </div>
        {!isEditing && (
        <div>
          <label className={labelClass}>Độ khó</label>
          <Select value={difficulty} onChange={(e) => setDifficulty(e.target.value as QuestionDifficulty)} className={`${inputClass} font-bold`}>
            <option value="EASY">Dễ</option>
            <option value="MEDIUM">Trung bình</option>
            <option value="HARD">Khó</option>
          </Select>
        </div>
        )}
        <div>
          <label className={labelClass}>
            {partCount >= 2
              ? "Điểm mỗi câu (mỗi phần / mỗi chỗ chọn)"
              : subQuestionCount > 0
                ? `Điểm mỗi câu con (× ${subQuestionCount} câu = ${(Number(points) || 0) * subQuestionCount})`
                : "Điểm câu này"}
          </label>
          <input type="number" min={0} step="0.5" value={points} onChange={(e) => setPoints(e.target.value)} className={inputClass} />
        </div>
        {!isEditing && (
        <div>
          <label className={labelClass} title='VD "14-15" khi câu này chiếm 2 ô đánh số liền nhau trong đề gốc (IELTS "Questions 14 and 15..."). Để trống = hiện số thường.'>
            Nhãn số hiển thị (tuỳ chọn)
          </label>
          <input
            value={displayNumberLabel}
            onChange={(e) => setDisplayNumberLabel(e.target.value)}
            placeholder="VD: 14-15"
            maxLength={20}
            className={inputClass}
          />
        </div>
        )}
      </div>

      {!isEditing && (
      <label className="flex items-center gap-2 text-[13px] font-semibold text-slate-600 cursor-pointer select-none">
        <input
          type="checkbox"
          checked={isEssayMode}
          onChange={(e) => setIsEssayMode(e.target.checked)}
          className="h-4 w-4 rounded border-slate-300 accent-orange-500"
        />
        Câu Tự luận (ESSAY, chấm AI) — không cần Ô trống/Nhóm lựa chọn/..., chỉ cần gõ đề bài bên dưới
      </label>
      )}

      {showPreview ? (
        <CkeditorHtmlPreview authoredHtml={html} />
      ) : (
        <CKEditor
          editor={ClassicEditor}
          data={html}
          onReady={(editor) => {
            editorRef.current = editor;
          }}
          onChange={(_evt, editor) => setHtml(editor.getData())}
          config={{
            licenseKey: "GPL",
            placeholder: PLACEHOLDER,
            plugins: [
              Essentials, Paragraph, Bold, Italic, Underline, Alignment, FontSize, FontFamily,
              Table, TableToolbar, TableProperties, TableCellProperties, TableColumnResize, PasteFromOffice,
              Heading, List, ListProperties, TodoList, Indent, IndentBlock, Link,
              Image, ImageInsertViaUrl, ImageUpload, ImageToolbar, ImageCaption, ImageStyle, ImageResize, ImageResizeHandles, ImageTextAlternative,
              BlockQuote, RemoveFormat, SpecialCharacters, SpecialCharactersEssentials,
              Highlight, HorizontalLine, ShowBlocks,
              BlankWidgetPlugin, WordBankWidgetPlugin, ChoiceWidgetPlugin, ChunkWidgetPlugin, AudioWidgetPlugin,
              InlineChoiceWidgetPlugin, GridWidgetPlugin, JustifyCleanupPlugin, QuestionWidgetsMenuPlugin
            ],
            extraPlugins: [PpsImageUploadAdapterPlugin],
            // Sửa lại ngoài SDD gốc, đã xác nhận với người dùng — đã thử rải thẳng 17 nút widget riêng
            // của hệ thống + `shouldNotGroupWhenFull: true` cho tự xuống dòng, nhưng người dùng phản
            // hồi vẫn rối (có dòng chỉ 1 nút lẻ loi do ngắt dòng cứng "-"). Gộp hết 17 nút đó vào 1
            // dropdown DUY NHẤT "insertQuestionWidget" (xem QuestionWidgetsMenuPlugin.ts) — GV bấm mở
            // khi cần, đóng lại thì các nút widget ẩn gọn. GIỮ `shouldNotGroupWhenFull: true` ở toolbar
            // CHÍNH (khác panel con bên trong dropdown) — nếu không, CKEditor5 tự gom bớt nút cuối
            // (kể cả "Chèn câu hỏi") vào menu "..." riêng của nó khi modal hẹp, bắt GV bấm "..." rồi
            // mới bấm "Chèn câu hỏi" — 2 lần bấm thừa, đúng lỗi người dùng báo lại. Dùng
            // `shouldNotGroupWhenFull` thì toolbar chính tự XUỐNG DÒNG thay vì ẩn bớt nút, nên "Chèn
            // câu hỏi" luôn hiện thẳng, bấm 1 lần là xổ ra panel.
            toolbar: {
              shouldNotGroupWhenFull: true,
              items: [
                "undo", "redo", "|", "heading", "|", "fontSize", "fontFamily", "|",
                "bold", "italic", "underline", "removeFormat", "|",
                "alignment", "bulletedList", "numberedList", "todoList", "outdent", "indent", "|",
                "link", "insertImageViaUrl", "uploadImage", "insertTable", "blockQuote", "horizontalLine", "|",
                "highlight", "specialCharacters", "showBlocks", "|",
                "insertQuestionWidget"
              ]
            },
            table: { contentToolbar: ["tableRow", "tableColumn", "mergeTableCells", "|", "tableProperties", "tableCellProperties"] },
            fontSize: { options: [10, 12, 14, "default", 18, 20, 24, 28, 32], supportAllValues: true },
            alignment: { options: ["left", "center", "right", "justify"] },
            image: { toolbar: ["imageStyle:inline", "imageStyle:block", "imageStyle:side", "|", "toggleImageCaption", "imageTextAlternative", "|", "resizeImage"] },
            list: { properties: { styles: true, startIndex: true, reversed: true } }
          }}
        />
      )}

      {/* Bổ sung ngoài SDD gốc, đã xác nhận với người dùng — cả cụm đếm/2 nút đặt NGAY DƯỚI đoạn
          văn/editor (không còn ở trên như bản đầu) để GV vừa nhìn đề vừa điền đáp án cho tiện,
          khớp mockup UX. */}
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-1.5 text-[12px] font-bold uppercase tracking-wider text-slate-500">
          {isEssayMode
            ? "Chế độ Tự luận (ESSAY) — không cần widget đáp án"
            : <>
                {partCount >= 2 && <span className="text-orange-600">{partCount} phần (chung 1 khung) ·</span>}
                {blanks.length} ô trống · {choiceGroups.length} nhóm lựa chọn · {chunkGroups.length} nhóm sắp xếp · {grids.length} bảng lưới · {inlineChoices.length} lựa chọn trong câu
                {unconfiguredCount > 0 && <span className="text-rose-600">· {unconfiguredCount} chưa gán đáp án</span>}
              </>}
        </div>
        <div className="flex items-center gap-2">
          {chunkGroups.length > 0 && (
            <button
              type="button"
              onClick={openChunkOrderPanel}
              className="flex items-center gap-1.5 text-[13px] font-bold text-slate-600 border border-slate-200 rounded-lg px-2.5 py-1.5 hover:border-brand-red hover:text-brand-red hover:bg-brand-red/5 transition-colors"
            >
              <ListOrdered className="w-3.5 h-3.5" /> Gán thứ tự đúng
            </button>
          )}
          <button
            type="button"
            onClick={openBulkPanel}
            className="flex items-center gap-1.5 text-[13px] font-bold text-slate-600 border border-slate-200 rounded-lg px-2.5 py-1.5 hover:border-brand-red hover:text-brand-red hover:bg-brand-red/5 transition-colors"
          >
            <Wand2 className="w-3.5 h-3.5" /> Gán nhanh tất cả đáp án
          </button>
          <button
            type="button"
            onClick={() => setShowPreview((v) => !v)}
            className={`flex items-center gap-1.5 text-[13px] font-bold rounded-lg px-2.5 py-1.5 border transition-colors ${
              showPreview ? "border-brand-red text-brand-red bg-brand-red/5" : "border-slate-200 text-slate-600 hover:border-brand-red hover:text-brand-red hover:bg-brand-red/5"
            }`}
          >
            {showPreview ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
            {showPreview ? "Đóng xem trước" : "Xem trước (học sinh)"}
          </button>
        </div>
      </div>

      {bulkPanelOpen && (
        <div className="bg-slate-50 border border-slate-200 rounded-xl p-3 space-y-2">
          <p className="text-[12px] text-slate-500">
            Nhập đáp án theo đúng thứ tự xuất hiện ({bulkAnswers.length} ô) — ô để trống có chủ đích vẫn để ô nhập rỗng, không phải lỗi. Một ô có nhiều đáp án đúng thì cách nhau bằng dấu "/" (VD: 2 dollars/two dollars).
          </p>
          {/* Dải ngang các "pill" đánh số thay cho danh sách dọc từng dòng — gọn hơn hẳn khi có
              7-10+ ô trống, màu đổi SỐNG theo đang gõ (xanh = có đáp án, xám = để trống có chủ đích
              — Áp dụng luôn coi MỌI ô trong panel này là đã cấu hình, answered=true, kể cả để trống). */}
          <div className="flex flex-wrap gap-2">
            {bulkAnswers.map((a, i) => (
              <div
                key={i}
                className={`flex items-center gap-1.5 pl-1 pr-2 py-1 rounded-full border text-sm font-bold ${
                  a.trim() ? "border-emerald-300 bg-emerald-50 text-emerald-800" : "border-slate-300 bg-slate-100 text-slate-500"
                }`}
              >
                <span
                  className={`w-5 h-5 shrink-0 rounded-full flex items-center justify-center text-[11px] font-extrabold text-white ${
                    a.trim() ? "bg-emerald-500" : "bg-slate-400"
                  }`}
                >
                  {i + 1}
                </span>
                <input
                  value={a}
                  onChange={(e) => setBulkAnswers((prev) => prev.map((x, idx) => (idx === i ? e.target.value : x)))}
                  placeholder="(trống)"
                  style={{ width: `${Math.max(8, a.length + 2)}ch` }}
                  className="bg-transparent text-sm font-bold focus:outline-none placeholder:font-normal placeholder:italic"
                />
              </div>
            ))}
          </div>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" size="sm" onClick={() => setBulkPanelOpen(false)}>Hủy</Button>
            <Button type="button" variant="primary" size="sm" onClick={applyBulkPanel}>Áp dụng</Button>
          </div>
        </div>
      )}

      {chunkOrderPanelOpen && (
        <div className="bg-slate-50 border border-slate-200 rounded-xl p-3 space-y-2">
          <p className="text-[12px] text-slate-500">
            Kéo-thả các khối về ĐÚNG thứ tự của câu hoàn chỉnh (trái → phải). Thứ tự hiển thị cho học sinh vẫn giữ nguyên như bạn đã gõ trong đề — không cần chỉnh nếu 2 thứ tự giống nhau.
          </p>
          <div className="flex flex-wrap gap-2">
            {chunkOrderDraft.map((c, i) => (
              <div
                key={c.chunkId}
                draggable
                onDragStart={() => {
                  draggedChunkIndexRef.current = i;
                }}
                onDragOver={(e) => e.preventDefault()}
                onDrop={() => handleChunkDrop(i)}
                className="flex items-center gap-1.5 pl-1 pr-3 py-1 rounded-full border border-indigo-300 bg-indigo-50 text-indigo-800 text-sm font-bold cursor-grab active:cursor-grabbing select-none"
              >
                <span className="w-5 h-5 shrink-0 rounded-full flex items-center justify-center text-[11px] font-extrabold text-white bg-indigo-500">{i + 1}</span>
                {c.text}
              </div>
            ))}
          </div>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" size="sm" onClick={() => setChunkOrderPanelOpen(false)}>Hủy</Button>
            <Button type="button" variant="primary" size="sm" onClick={applyChunkOrderPanel}>Áp dụng</Button>
          </div>
        </div>
      )}

      <div>
        <label className={labelClass}>Giải thích (tùy chọn)</label>
        <textarea value={explanation} onChange={(e) => setExplanation(e.target.value)} rows={2} className={inputClass} />
      </div>

      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel} disabled={submitting}>Hủy</Button>
        <Button type="button" variant="primary" size="sm" onClick={handleSubmit} disabled={submitting}>
          {isEditing ? (submitting ? "Đang lưu..." : "Lưu thay đổi") : submitting ? "Đang tạo..." : "Tạo câu hỏi"}
        </Button>
      </div>
    </div>
  );
}
