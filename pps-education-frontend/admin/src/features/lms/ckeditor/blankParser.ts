import type { QuestionResponse, QuestionType } from "../api";

/**
 * Đọc HTML do CKEditor5 xuất ra để (1) validate TRƯỚC khi lưu (chặn nếu còn ô trống "chưa cấu
 * hình") và (2) dựng "Xem trước (học sinh)" ngay trong Admin — mirror CÁCH backend thật sẽ đọc
 * (CkeditorWordBankParser.java, Jsoup): ô trống theo ĐÚNG THỨ TỰ TÀI LIỆU (không phải theo
 * blankId), `data-answer` chỉ tồn tại khi widget đã được cấu hình (xem BlankWidgetPlugin.ts).
 */
/** Nhận diện content/referencePassage là HTML đầy đủ soạn qua tab CKEditor5 (có thẻ mở) thay vì
 * chữ thường cũ (tách "___", cú pháp `**đậm**`) — dùng ở QuestionPreview.tsx/
 * ExerciseStudentPreviewModal.tsx để chọn đúng cách render. */
export function isCkeditorHtml(text: string | null | undefined): boolean {
  return !!text && /<[a-z][\s\S]*>/i.test(text);
}

/** Rút gọn content thành 1 dòng chữ thường cho các chỗ chỉ cần xem lướt (danh sách câu của Bài...) — bóc hết thẻ HTML nếu là CKE, không hiện thẻ thô. */
export function summarizeQuestionContent(content: string, maxLength = 120): string {
  const plain = isCkeditorHtml(content) ? content.replace(/<[^>]*>/g, " ").replace(/\s+/g, " ").trim() : content;
  return plain.length > maxLength ? plain.slice(0, maxLength) + "…" : plain;
}

export interface ParsedBlank {
  position: number; // 0-based, = index trong structuredContent.blanks thật ở backend
  blankId: string;
  answer: string;
  /** false = widget chưa từng được GV gán đáp án (kể cả để trống có chủ đích) — chặn Lưu. */
  answered: boolean;
}

export function extractBlanksInDocumentOrder(html: string): ParsedBlank[] {
  const container = document.createElement("div");
  container.innerHTML = html;
  const nodes = Array.from(container.querySelectorAll<HTMLElement>(".q-blank"));
  return nodes.map((node, position) => ({
    position,
    blankId: node.getAttribute("data-blank-id") || `(thiếu id #${position})`,
    answer: node.getAttribute("data-answer") || "",
    answered: node.hasAttribute("data-answer")
  }));
}

/** Xoá "data-answer"/"data-correct" trước khi hiện cho pane "Xem trước (học sinh)" — mirror việc
 * backend không bao giờ trả đáp án đúng cho học viên thật trước khi nộp bài. */
export function stripAnswersForStudentView(html: string): string {
  const container = document.createElement("div");
  container.innerHTML = html;
  container.querySelectorAll(".q-blank").forEach((node) => node.removeAttribute("data-answer"));
  container.querySelectorAll(".q-choice-option").forEach((node) => {
    node.removeAttribute("data-correct");
    node.classList.remove("q-choice-correct");
  });
  container.querySelectorAll(".q-inline-choice, .q-grid-row").forEach((node) => node.removeAttribute("data-correct-index"));
  return container.innerHTML;
}

/** 1 ô trống có thể nhận NHIỀU đáp án đúng, cách nhau bằng "/" (VD "2 dollars/two dollars") — backend
 * chấm đúng khi khớp ÍT NHẤT 1 phương án (ExerciseAttemptService#blankMatches). */
export function splitAcceptedAnswers(answer: string): string[] {
  return answer.split("/").map((a) => a.trim()).filter(Boolean);
}

export function blankAnswerMatches(correct: string, given: string): boolean {
  const normalized = given.trim().toLowerCase();
  const accepted = splitAcceptedAnswers(correct);
  if (accepted.length === 0) return normalized === "";
  return accepted.some((a) => a.toLowerCase() === normalized);
}

export interface ParsedInlineChoice {
  options: string[];
  /** -1 = chưa chọn đáp án đúng. */
  correctIndex: number;
}

/** Mỗi "Lựa chọn trong câu" thành 1 câu trắc nghiệm riêng khi tạo — mirror CkeditorGroupParser.java. */
export function extractInlineChoicesInDocumentOrder(html: string): ParsedInlineChoice[] {
  const container = document.createElement("div");
  container.innerHTML = html;
  return Array.from(container.querySelectorAll<HTMLElement>(".q-inline-choice")).map((node) => {
    const raw = node.getAttribute("data-correct-index");
    return {
      options: (node.getAttribute("data-options") || "").split("|").filter(Boolean),
      correctIndex: raw != null && raw !== "" ? Number(raw) : -1
    };
  });
}

export interface ParsedGrid {
  options: string[];
  rows: { text: string; correctIndex: number }[];
}

/** Mỗi dòng của "Bảng lưới" thành 1 câu trắc nghiệm riêng, dùng chung bộ đáp án — mirror CkeditorGroupParser.java. */
export function extractGridsInDocumentOrder(html: string): ParsedGrid[] {
  const container = document.createElement("div");
  container.innerHTML = html;
  return Array.from(container.querySelectorAll<HTMLElement>(".q-grid")).map((grid) => ({
    options: (grid.getAttribute("data-options") || "").split("|").filter(Boolean),
    rows: Array.from(grid.querySelectorAll<HTMLElement>(".q-grid-row")).map((row) => {
      const raw = row.getAttribute("data-correct-index");
      return { text: row.textContent?.trim() || "", correctIndex: raw != null && raw !== "" ? Number(raw) : -1 };
    })
  }));
}

export interface ParsedChoiceOption {
  optionId: string;
  text: string;
  /** Giữ nguyên HTML bên trong lựa chọn (có thể chứa ảnh) — `text` (textContent) làm rụng mất ảnh. */
  html: string;
  isCorrect: boolean;
}

export interface ParsedChoiceGroup {
  groupId: string;
  /** Chèn qua nút "Chèn Đúng/Sai" riêng (bổ sung 2026-11-15) — map sang QuestionType.TRUE_FALSE ở
   * backend thay vì suy luận MULTIPLE_CHOICE/MULTIPLE_ANSWER như nhóm thường. */
  trueFalse: boolean;
  options: ParsedChoiceOption[];
}

/**
 * Suy ra kiểu chấm TỪ CHÍNH NỘI DUNG widget: đúng 1 lựa chọn đánh dấu đúng → MULTIPLE_CHOICE
 * (radio); từ 2 lựa chọn đúng trở lên → MULTIPLE_ANSWER (checkbox) — mirror CkeditorChoiceParser.java.
 */
export function extractChoiceGroupsInDocumentOrder(html: string): ParsedChoiceGroup[] {
  const container = document.createElement("div");
  container.innerHTML = html;
  const groups = Array.from(container.querySelectorAll<HTMLElement>(".q-choice-group"));
  return groups.map((group) => ({
    groupId: group.getAttribute("data-group-id") || "",
    trueFalse: group.getAttribute("data-true-false") === "true",
    options: Array.from(group.querySelectorAll<HTMLElement>(".q-choice-option")).map((option) => ({
      optionId: option.getAttribute("data-option-id") || "",
      text: option.textContent?.trim() || "",
      html: option.innerHTML,
      isCorrect: option.getAttribute("data-correct") === "true"
    }))
  }));
}

export interface ParsedChunk {
  chunkId: string;
  text: string;
  order: number | null;
}

export interface ParsedChunkGroup {
  groupKey: string;
  /** Thứ tự HIỂN THỊ cho học sinh — LUÔN giữ y nguyên đúng thứ tự GV gõ trong đề bài, không random. */
  chunks: ParsedChunk[];
}

/**
 * Nhóm các khối "q-chunk" nằm chung 1 câu/dòng (cùng thẻ khối cha gần nhất: p/li/td) thành 1 nhóm
 * cần sắp xếp — mirror CkeditorChunkParser.java (backend).
 */
export function extractChunkGroupsInDocumentOrder(html: string): ParsedChunkGroup[] {
  const container = document.createElement("div");
  container.innerHTML = html;
  const nodes = Array.from(container.querySelectorAll<HTMLElement>(".q-chunk"));

  const groups: ParsedChunkGroup[] = [];
  let currentParent: Element | null = null;
  let currentChunks: ParsedChunk[] = [];
  let groupIndex = 0;

  const flush = () => {
    if (currentChunks.length >= 2) {
      groups.push({ groupKey: `chunkgrp${groupIndex++}`, chunks: currentChunks });
    }
    currentChunks = [];
  };

  nodes.forEach((node) => {
    const parent = node.closest("p, li, td, th, div");
    if (parent !== currentParent) {
      flush();
      currentParent = parent;
    }
    const orderAttr = node.getAttribute("data-order");
    currentChunks.push({
      chunkId: node.getAttribute("data-chunk-id") || "",
      text: node.textContent?.trim() || "",
      order: orderAttr ? Number(orderAttr) : null
    });
  });
  flush();

  return groups;
}

/**
 * Thứ tự dùng để CHẤM ĐIỂM — khác thứ tự HIỂN THỊ (`group.chunks`) ở chỗ có thể đã được GV cấu
 * hình riêng qua panel "Gán thứ tự đúng" (field `order` trên từng khối). Nếu GV CHƯA cấu hình
 * (mọi khối đều `order: null`) thì mặc định thứ tự đúng = thứ tự hiển thị luôn.
 */
export function getCorrectChunkOrder(group: ParsedChunkGroup): string[] {
  const hasCustomOrder = group.chunks.some((c) => c.order !== null);
  if (!hasCustomOrder) return group.chunks.map((c) => c.chunkId);
  return [...group.chunks].sort((a, b) => (a.order ?? 0) - (b.order ?? 0)).map((c) => c.chunkId);
}

/** Số phần của bài nhiều phần — đếm theo đường kẻ ngang (hr cấp ngoài cùng), mirror QuestionBankService#splitCkeditorParts. */
export function countCkeditorParts(html: string): number {
  const body = new DOMParser().parseFromString(html, "text/html").body;
  let parts = 0;
  let current = false;
  for (const node of Array.from(body.childNodes)) {
    if (node.nodeName === "HR") {
      if (current) parts++;
      current = false;
    } else if ((node.textContent ?? "").trim() || (node instanceof Element && (node.matches("img, audio") || node.querySelector("img, audio")))) {
      current = true;
    }
  }
  return current ? parts + 1 : parts;
}

/** Mirror QuestionBankService.DEFAULT_CHUNK_INSTRUCTION — content backend gán cho câu "Khối sắp xếp" soạn bằng CKE không có câu dẫn. */
const CKEDITOR_DEFAULT_CHUNK_INSTRUCTION = "Sắp xếp các khối từ dưới đây thành câu hoàn chỉnh:";

const CKEDITOR_EDITABLE_TYPES = new Set<QuestionType>([
  "WORD_BANK", "ESSAY", "MULTIPLE_CHOICE", "MULTIPLE_ANSWER", "TRUE_FALSE", "SENTENCE_BUILDING"
]);

/**
 * Câu hỏi tạo bằng khung CKEditor5 (content là HTML) → mở lại bằng CKE khi sửa thay vì form cũ (form
 * cũ sẽ hiện HTML thô). Câu thuộc nhóm (Bảng lưới/Lựa chọn trong câu — có groupKey) vẫn sửa từng câu
 * bằng form cũ vì 1 câu con không dựng lại được cả bảng.
 */
export function isCkeditorAuthoredQuestion(q: QuestionResponse): boolean {
  // Câu con Bảng lưới/Lựa chọn trong câu có content chữ thường ("(1)", nội dung dòng) nên tự rơi về form
  // cũ; còn các phần của bài nhiều phần (có groupKey nhưng content HTML) vẫn sửa bằng CKE.
  if (!CKEDITOR_EDITABLE_TYPES.has(q.questionType)) return false;
  if (q.questionType === "SENTENCE_BUILDING" && q.content === CKEDITOR_DEFAULT_CHUNK_INSTRUCTION) return true;
  return isCkeditorHtml(q.content);
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

/**
 * Dựng lại HTML có widget từ câu hỏi đã lưu — ngược với các parser backend: Nhóm lựa chọn/Khối sắp
 * xếp/Audio đã bị BÓC khỏi content lúc tạo (sang choices[]/structuredContent/audioUrl) nên phải ghép
 * lại; Ô trống/Hộp từ vựng/Tự luận giữ nguyên HTML trong content.
 */
export function buildCkeditorEditHtml(q: QuestionResponse): string {
  const audio = q.audioUrl ? `<audio controls="controls" src="${escapeHtml(q.audioUrl)}"></audio>` : "";
  switch (q.questionType) {
    case "MULTIPLE_CHOICE":
    case "MULTIPLE_ANSWER":
    case "TRUE_FALSE": {
      const options = [...q.choices]
        .sort((a, b) => a.displayOrder - b.displayOrder)
        .map((c, i) => {
          // Backend gán nhãn chữ cái làm content khi lựa chọn CHỈ có ảnh (CkeditorChoiceParser) — không hiện lại nhãn đó.
          const imageOnly = !!c.imageUrl && c.content === String.fromCharCode(65 + i);
          const text = imageOnly ? "" : escapeHtml(c.content);
          const img = c.imageUrl ? `<img src="${escapeHtml(c.imageUrl)}">` : "";
          return `<label class="q-choice-option" data-correct="${c.isCorrect ? "true" : "false"}">${text}${img}</label>`;
        })
        .join("");
      const trueFalseAttr = q.questionType === "TRUE_FALSE" ? ' data-true-false="true"' : "";
      return `${audio}${q.content}<div class="q-choice-group"${trueFalseAttr}>${options}</div>`;
    }
    case "SENTENCE_BUILDING": {
      const correct = q.structuredContent?.chunks ?? [];
      const display = q.structuredContent?.chunkOrder?.length ? q.structuredContent.chunkOrder : correct;
      const used = new Set<number>();
      const spans = display
        .map((text) => {
          // Khối trùng chữ (VD 2 chữ "e" khi xáo chữ cái) hoán đổi được cho nhau — lấy vị trí đúng đầu tiên chưa dùng.
          const index = correct.findIndex((c, i) => !used.has(i) && c === text);
          if (index >= 0) used.add(index);
          const orderAttr = index >= 0 ? ` data-order="${index + 1}"` : "";
          return `<span class="q-chunk"${orderAttr}>${escapeHtml(text)}</span>`;
        })
        .join(" ");
      const stem = q.content === CKEDITOR_DEFAULT_CHUNK_INSTRUCTION ? "" : q.content;
      return `${audio}${stem}<p>${spans}</p>`;
    }
    default:
      return `${audio}${q.content}`;
  }
}
