import React, { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { CheckCircle2, GraduationCap, HelpCircle, KeyRound, Loader2, Lock, PartyPopper, RotateCcw, ShieldAlert, Sparkles, X, XCircle } from "lucide-react";
import { friendlyApiErrorMessage } from "@/lib/apiClient";
import {
  AssignedExerciseResponse,
  ExerciseAttemptResponse,
  ExerciseMetaResponse,
  ExerciseQuestionChoiceResponse,
  ExerciseQuestionResponse,
  ListeningHintResponse,
  ListeningPlayProgressResponse,
  StudentAnswerResponse,
  getAttempt,
  getExercise,
  getListeningHint,
  listExerciseQuestions,
  listAnswers,
  recordIntegrityEvents,
  recordListeningPlay,
  revealAndCloseAttempt,
  saveAnswer,
  startAttempt,
  submitAttempt,
  uploadMedia
} from "../api";
import { useIntegrityMonitor } from "../hooks/useIntegrityMonitor";
import MonitoringBadge from "./MonitoringBadge";
import { ScoreSticker } from "./ScoreSticker";
import { useCountdown, formatRemaining } from "@/components/ui/useCountdown";
import { useLockBodyScroll } from "@/components/ui/useLockBodyScroll";

interface TakeExerciseModalProps {
  item: AssignedExerciseResponse;
  onClose: () => void;
}

const CHOICE_TYPES = new Set(["MULTIPLE_CHOICE", "MULTIPLE_ANSWER", "TRUE_FALSE"]);

/**
 * V182 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-16, PILOT Khối 7 IELTS) — rubric
 * Writing "v3" đánh dấu lỗi ngay trong bài viết bằng markup thuần `{{mã|đoạn văn bản}}` — 5 loại lỗi
 * (sp/gr/wd/pu chính tả/ngữ pháp/từ vựng/dấu câu) × 2 mức độ (hậu tố 1=nhẹ/vàng, 2=nặng/đỏ), cộng `ok`
 * cho chỗ dùng đúng/tốt (xanh) — xem WritingAiGradingService. BE không có sanitizer/markdown nào nên
 * KHÔNG dùng dangerouslySetInnerHTML — tự regex-split ra text node thường vs span tô màu.
 *
 * V196 (2026-09-22, bổ sung ngoài SDD gốc, Key Grammar filter 2) — thêm mã `kg` (dùng ĐÚNG cấu trúc
 * Key Grammar được giao — xanh, nhãn "KG", THAY cho `ok` ở đúng chỗ đó theo đặc tả
 * {@code 00_DAC_TA_GIAO_NHAN.md} §5). Bug đã sửa: quên thêm `kg` vào regex khi mới làm — token in ra
 * thô `{{kg|...}}` không được tô màu, phát hiện qua verify UI thật.
 */
const MARKED_ESSAY_TOKEN = /\{\{(ok|kg|sp1|sp2|gr1|gr2|wd1|wd2|pu1|pu2)\|([\s\S]*?)\}\}/g;

function markedEssayTokenClassName(code: string): string {
  if (code === "ok" || code === "kg") {
    return "text-emerald-700 underline decoration-emerald-400 decoration-2 underline-offset-2 font-semibold";
  }
  return code.endsWith("2")
    ? "text-red-600 underline decoration-red-500 decoration-2 underline-offset-2 font-semibold"
    : "text-amber-700 underline decoration-amber-400 decoration-2 underline-offset-2 font-semibold";
}

/**
 * Bổ sung 2026-09-22 (phản hồi người dùng khi xem thử qua UI thật) — màu một mình không đủ để học sinh
 * hiểu vì sao 1 chỗ bị tô: (1) không giải thích vàng/đỏ nghĩa là gì, (2) cả 4 loại lỗi (chính tả/ngữ
 * pháp/từ vựng/dấu câu) đang tô CÙNG màu theo mức độ nặng nhẹ, không phân biệt được LOẠI lỗi. Thêm nhãn
 * viết tắt ngay cạnh mỗi chỗ tô — mirror đúng thiết kế {@code data-tag}/{@code .mk::after} của gói rubric
 * tham chiếu ({@code bo-cham-writing-K6-K9/tham-khao/index.html}) — kèm chú giải màu ở
 * {@link MarkedEssayLegend} ngay dưới bài làm.
 */
function markedEssayTagLabel(code: string, t: (key: string) => string): string {
  if (code === "kg") return t("takeExercise.question.markedEssayTagKeyGrammar");
  if (code === "ok") return t("takeExercise.question.markedEssayTagOk");
  if (code.startsWith("sp")) return t("takeExercise.question.markedEssayTagSpelling");
  if (code.startsWith("gr")) return t("takeExercise.question.markedEssayTagGrammar");
  if (code.startsWith("wd")) return t("takeExercise.question.markedEssayTagWordChoice");
  return t("takeExercise.question.markedEssayTagPunctuation");
}

function renderMarkedEssay(text: string, t: (key: string) => string): React.ReactNode[] {
  const parts: React.ReactNode[] = [];
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  let key = 0;
  MARKED_ESSAY_TOKEN.lastIndex = 0;
  while ((match = MARKED_ESSAY_TOKEN.exec(text)) !== null) {
    if (match.index > lastIndex) {
      parts.push(<React.Fragment key={key++}>{text.slice(lastIndex, match.index)}</React.Fragment>);
    }
    const code = match[1];
    parts.push(
      <span key={key++} className={markedEssayTokenClassName(code)}>
        {match[2]}
        <sup className="ml-0.5 align-super text-[9px] font-black not-italic tracking-wide no-underline">
          {markedEssayTagLabel(code, t)}
        </sup>
      </span>
    );
    lastIndex = MARKED_ESSAY_TOKEN.lastIndex;
  }
  if (lastIndex < text.length) {
    parts.push(<React.Fragment key={key++}>{text.slice(lastIndex)}</React.Fragment>);
  }
  return parts;
}

/**
 * Chú giải màu + nhãn viết tắt cho {@link renderMarkedEssay} — xem Javadoc hàm đó.
 *
 * V2 (2026-09-22, phản hồi người dùng khi xem thử qua UI thật lần 2 — "chữ to lên, giải thích rõ ràng
 * ra") — chữ trước đó quá nhỏ (10.5px) và dồn hết vào 1 dòng flex-wrap khó đọc. Tách 2 dòng riêng (màu
 * sắc / viết tắt loại lỗi), mỗi viết tắt tách thành 1 khối riêng thay vì nối chuỗi bằng dấu "·".
 */
function MarkedEssayLegend({ t }: { t: (key: string) => string }) {
  return (
    <div className="space-y-1.5 rounded-xl bg-white/60 px-3 py-2 text-[12.5px] font-bold normal-case text-ink-soft">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
        <span className="text-[11px] font-extrabold uppercase tracking-wide text-muted">
          {t("takeExercise.question.markedEssayLegendTitle")}
        </span>
        <span className="inline-flex items-center gap-1.5 text-emerald-700">
          <span className="inline-block h-2.5 w-2.5 rounded-full bg-emerald-500" />
          {t("takeExercise.question.markedEssayLegendOk")}
        </span>
        <span className="inline-flex items-center gap-1.5 text-amber-700">
          <span className="inline-block h-2.5 w-2.5 rounded-full bg-amber-500" />
          {t("takeExercise.question.markedEssayLegendMinor")}
        </span>
        <span className="inline-flex items-center gap-1.5 text-red-600">
          <span className="inline-block h-2.5 w-2.5 rounded-full bg-red-500" />
          {t("takeExercise.question.markedEssayLegendMajor")}
        </span>
      </div>
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1.5">
        <span className="text-[11px] font-extrabold uppercase tracking-wide text-muted">
          {t("takeExercise.question.markedEssayLegendAbbrevIntro")}
        </span>
        {[
          "markedEssayLegendAbbrevSpelling",
          "markedEssayLegendAbbrevGrammar",
          "markedEssayLegendAbbrevWordChoice",
          "markedEssayLegendAbbrevPunctuation"
        ].map((key) => (
          <span key={key} className="rounded-lg border border-line/60 bg-white px-2 py-0.5 text-ink">
            {t(`takeExercise.question.${key}`)}
          </span>
        ))}
        {/*
         * V196 (bổ sung ngoài SDD gốc, Key Grammar filter 2) — "KG" không phải loại LỖI như 4 mã trên,
         * nên tách viền xanh riêng thay vì border xám mặc định — phản hồi người dùng: xem bài chấm thật
         * thấy nhãn "KG" trong bài nhưng không có giải thích ở đây, không biết là gì.
         */}
        <span className="rounded-lg border border-emerald-300 bg-emerald-50 px-2 py-0.5 text-emerald-800">
          {t("takeExercise.question.markedEssayLegendAbbrevKeyGrammar")}
        </span>
      </div>
    </div>
  );
}

/**
 * Bổ sung 2026-09-13 (fix bug thật, đã xác nhận với người dùng) — điều kiện "còn làm lại được" ĐÚNG
 * NGAY TRONG PHIÊN đang mở, không chỉ dựa vào {@code item.canStartNewAttempt} (cờ BE tính SẴN lúc tải
 * danh sách BTVN, đứng yên suốt phiên modal đang mở). 2 field tĩnh của {@code meta}
 * (allowRetake/maxAttempts, không đổi trong phiên) + {@code attempt.attemptNumber} (SỐNG, cập nhật
 * đúng sau mỗi lần bấm "Làm lại" trong cùng phiên) mirror lại ĐÚNG rào phía BE
 * (ExerciseAttemptService#startAttempt kiểm tra allowRetake + attemptNumber vs maxAttempts) — bù đúng
 * phần {@code item.canStartNewAttempt} bị "đứng hình" không theo kịp khi bấm "Làm lại" nhiều lần liên
 * tiếp trong 1 Lô (BatchTakeExerciseModal) mà
 * không đóng modal ra vào lại: trước đây 1 Bài trong Lô không cho làm lại (allowRetake=false) hoặc đã
 * hết lượt (maxAttempts) vẫn bị coi "còn làm lại được" ở lần bấm ĐẦU (vì lúc mở Lô, myAttempts rỗng nên
 * canStartNewAttempt luôn true bất kể allowRetake) — bấm "Làm lại cả Lô" gọi startAttempt() cho CẢ Lô
 * cùng lúc, Bài đó bị BE từ chối (RetakeNotAllowedException) giữa chừng.
 *
 * {@code item.canStartNewAttempt} vẫn giữ vai trò chặn các điều kiện KHÔNG đổi trong phiên (assignment
 * còn ACTIVE, đã tới availableFrom, hạn nộp/cho phép nộp muộn, đã "Xem đáp án & đóng lượt" từ phiên
 * TRƯỚC) — chỉ bổ sung thêm 2 điều kiện allowRetake/maxAttempts tính lại tươi mỗi lần gọi.
 */
export function canRetakeExercise(item: AssignedExerciseResponse, meta: ExerciseMetaResponse, attempt: ExerciseAttemptResponse): boolean {
  return item.canStartNewAttempt && meta.allowRetake && (meta.maxAttempts == null || attempt.attemptNumber < meta.maxAttempts);
}

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-03 — số cột lưới đáp án dạng ảnh (V143)
 * khớp đúng số đáp án để luôn nằm gọn 1 hàng (đề giấy gốc không bao giờ quá 4 đáp án/câu), thay vì
 * grid-cols-2 cố định trước đây làm đề 3 đáp án bị lệch (2 ô hàng 1, 1 ô lẻ hàng 2).
 */
function imageChoiceGridColsClass(choiceCount: number): string {
  if (choiceCount >= 4) return "grid-cols-4";
  if (choiceCount === 3) return "grid-cols-3";
  return "grid-cols-2";
}

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-03 — fix bug thật: đáp án ảnh
 * (VOICE_PICTURE_CHOICE) khi soạn để trống chú thích thì Admin tự điền content = đúng chữ cái nhãn (VD
 * content="A" cho choiceLabel="A", xem ListeningGroupBuilder.tsx/QuestionEditorForm.tsx bên admin) —
 * hiện ra nhìn như lặp "A. A". Ẩn phần content khi nó trùng hệt choiceLabel (không phân biệt hoa/
 * thường, đã trim) hoặc rỗng, chỉ còn lại chữ cái nhãn — không lặp.
 */
function hasMeaningfulChoiceCaption(choiceLabel: string, content: string): boolean {
  const trimmed = content.trim();
  return trimmed.length > 0 && trimmed.toUpperCase() !== choiceLabel.trim().toUpperCase();
}

/**
 * Bổ sung 2026-09-17 (fix bug thật, mirror ExerciseStudentPreviewModal.tsx bên admin) —
 * DIEN_TU_HOP_TU_VUNG_ANH lưu NHIỀU URL ảnh nối bằng "|" trong CHUNG 1 cột imageUrl (tối đa 10 ảnh/10
 * chỗ trống, xem QuestionImportService#mapToRequest) — gán thẳng cả string nối "|" vào 1 <img src> duy
 * nhất là 1 URL sai định dạng, ảnh không tải được nên học sinh không thấy ảnh nào. Tách thành danh sách,
 * hiện dạng lưới có số thứ tự khi có NHIỀU ảnh; các loại câu hỏi khác chỉ có 1 URL vẫn hiện như cũ.
 */
function QuestionImages({ imageUrl, className }: { imageUrl: string; className: string }) {
  const urls = imageUrl
    .split("|")
    .map((u) => u.trim())
    .filter(Boolean);
  if (urls.length <= 1) {
    return <img src={urls[0] ?? imageUrl} alt="" className={className} />;
  }
  return (
    <div className="grid grid-cols-5 gap-2">
      {urls.map((url, i) => (
        <div key={i} className="space-y-1">
          <img src={url} alt="" className="w-full aspect-square object-contain rounded-lg border border-line/60 bg-white" />
          <p className="text-center text-[10px] font-bold text-muted">{i + 1}</p>
        </div>
      ))}
    </div>
  );
}

const SEEK_TOLERANCE_SECONDS = 1;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — fix bug thật: học sinh kéo thanh tua
 * (native `<audio controls>`) thẳng tới gần cuối để giả vờ "nghe hết" qua mặt bộ đếm playCount (mở khoá
 * gợi ý transcript) mà không thực sự nghe. Mirror đúng kỹ thuật chặn tua đã dùng ở ReviewVideoTaskModal
 * (video kết nối, SEEK_TOLERANCE_SECONDS) — theo dõi mốc xa nhất ĐÃ THỰC SỰ phát qua (không phải mốc đã
 * tua tới), currentTime nhảy vượt mốc đó (trừ dung sai nhỏ do trình duyệt tự làm tròn) thì kéo lại đúng
 * mốc. Chỉ chặn tua TỚI — vẫn tua LÙI nghe lại thoải mái (không cản trở nghe lại đoạn đã qua).
 */
function useSeekLockedAudio(audioRef: React.RefObject<HTMLAudioElement | null>, resetKey: string | undefined) {
  const maxPlayedRef = useRef(0);
  useEffect(() => {
    maxPlayedRef.current = 0;
    const media = audioRef.current;
    if (!media) return;
    const handleTimeUpdate = () => {
      const current = media.currentTime;
      const allowedMax = maxPlayedRef.current + SEEK_TOLERANCE_SECONDS;
      if (current > allowedMax) {
        media.currentTime = maxPlayedRef.current;
        return;
      }
      maxPlayedRef.current = Math.max(maxPlayedRef.current, current);
    };
    media.addEventListener("timeupdate", handleTimeUpdate);
    return () => media.removeEventListener("timeupdate", handleTimeUpdate);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resetKey]);
}

/** Khớp ListeningHintService#listeningKeyOf (BE) — nhóm "1 audio nhiều câu" dùng chung groupKey, câu đơn dùng key riêng theo chính nó. */
function listeningKeyOf(q: ExerciseQuestionResponse): string {
  return q.groupKey ?? `Q${q.questionId}`;
}

/**
 * V78 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04) — dạng "Đọc hiểu — lưới": nhiều
 * câu MULTIPLE_CHOICE liên tiếp cùng groupKey gộp hiển thị chung 1 referencePassage + 1 bảng câu hỏi,
 * thay vì lặp lại đoạn văn ở mỗi câu. Chỉ gộp các câu LIÊN TIẾP nhau (đúng thứ tự displayOrder).
 */
export type RenderBlock =
  | { type: "single"; question: ExerciseQuestionResponse }
  | { type: "grid"; groupKey: string; referencePassage: string | null; audioUrl: string | null; wordBox: string[] | null; questions: ExerciseQuestionResponse[] };

/** Bổ sung 2026-08-28 — chia mảng thành các hàng cố định `size` phần tử, dùng để dựng bảng hộp từ vựng (wordBox). */
function chunkArray<T>(items: T[], size: number): T[][] {
  const rows: T[][] = [];
  for (let i = 0; i < items.length; i += size) {
    rows.push(items.slice(i, i + size));
  }
  return rows;
}

/**
 * V2 (bổ sung 2026-09-04, đã xác nhận với người dùng — SỬA LẠI quyết định 2026-09-03) — bản 2026-09-03
 * bỏ hẳn `whitespace-pre-wrap` để tránh dấu xuống dòng "cứng" rác copy từ Word/PDF (1 dòng đơn lẻ giữa
 * câu) làm đoạn văn ngắt dòng sớm lệch trái — nhưng làm HỎNG LUÔN ranh giới đoạn văn THẬT của dạng "Bài
 * đọc hiểu — Lưới" (GridQuestionBuilder nối nhiều đoạn — 1 đoạn/nhân vật, dạng "Tên: nội dung" — bằng
 * "\n\n", xem GridQuestionBuilder.tsx): mọi \n bị trình duyệt collapse thành khoảng trắng như nhau, 3
 * đoạn Tom/Max/Anna dính liền thành 1 khối, không còn phân biệt được.
 *
 * V3 (bổ sung 2026-09-04, đã xác nhận với người dùng — nâng cấp tiếp V2 cùng ngày) — người dùng muốn
 * hiển thị ĐÚNG hình thức đề giấy gốc: tên nhân vật là 1 dòng tiêu đề in đậm riêng, KHÔNG dính liền
 * "Tên: nội dung" trong cùng 1 dòng chữ thường. Parse mỗi đoạn (đã tách ranh giới ở trên) theo đúng mẫu
 * "Tên: nội dung" mà GridQuestionBuilder tạo ra (chỉ referencePassage của khối GRID mới có dạng này —
 * hàm này CHỈ dùng trong GridQuestionGroup, không dùng cho referencePassage của câu đơn lẻ/ESSAY/audio
 * ở QuestionBlock) — đoạn nào không khớp mẫu (dữ liệu cũ/khác) thì hiện nguyên văn, không có tên riêng.
 *
 * V4 (fix bug thật 2026-09-08, đã xác nhận với người dùng qua ảnh chụp) — transcript bài Nghe dạng hội
 * thoại (VD "A: ...\nB: ...", ListeningGroupBuilder) dán từ trang web/PDF thường CHỈ có 1 \n giữa mỗi
 * lượt nói (không có dòng trống thật — nguồn dựng bằng nhiều <p> riêng, mỗi đoạn chỉ cách nhau 1 \n khi
 * copy ra text thô), khiến split(/\n\s*\n/) coi cả transcript là 1 đoạn DUY NHẤT rồi gộp hết thành 1
 * dòng dài — mất hẳn ranh giới lượt hội thoại. Chèn thêm 1 dòng trống ẢO trước mỗi dòng bắt đầu bằng
 * "Tên:" hoặc "N." (số thứ tự câu hỏi trong transcript) TRƯỚC khi split — nhận diện được ranh giới ngay
 * cả khi nguồn không có dòng trống thật, không ảnh hưởng trường hợp GridQuestionBuilder cũ (đã có sẵn
 * "\n\n" thật, chèn thêm không đổi kết quả split).
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

export function groupQuestionsByGroupKey(questions: ExerciseQuestionResponse[]): RenderBlock[] {
  const blocks: RenderBlock[] = [];
  for (const q of questions) {
    const last = blocks[blocks.length - 1];
    if (q.groupKey && last && last.type === "grid" && last.groupKey === q.groupKey) {
      last.questions.push(q);
      continue;
    }
    if (q.groupKey) {
      // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — nhóm "1 audio nhiều câu" (GV
      // nước ngoài, xem ListeningGroupBuilder) cùng dùng chung audioUrl như referencePassage: chỉ cần
      // lấy từ câu hỏi đầu tiên của nhóm.
      // wordBox (bổ sung 2026-08-28, đã xác nhận với người dùng) — hộp từ vựng THAM KHẢO tĩnh dùng
      // chung cho nhóm FILL_IN_BLANK (FillInBlankGroupBuilder), cùng quy ước lấy từ câu đầu nhóm.
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

/**
 * UC-24/A4, UC-27/A2: đáp án đúng (correctChoiceIds/correctAnswerText/correctStructuredContent) đã
 * thật sự lộ ra chưa — dùng chung cho mọi loại câu hỏi thay vì kiểm tra riêng lẻ isCorrect (field đó
 * luôn có giá trị ngay khi tự chấm xong, không phụ thuộc gate làm lại). Câu tự luận/Nói (ESSAY/
 * SPEAKING) không có 3 field trên, dựa vào explanation (luôn hiện khi revealAnswer=true, không bị
 * gate làm lại — xem ExerciseAttemptService.toResponse).
 */
function isAnswerRevealed(answer: StudentAnswerResponse): boolean {
  return (
    answer.correctChoiceIds != null ||
    answer.correctAnswerText != null ||
    answer.correctStructuredContent != null ||
    (!answer.isAutoGradable && answer.explanation != null)
  );
}

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-17 — FILL_IN_BLANK giờ chấp nhận NHIỀU
 * đáp án đúng cho cùng 1 chỗ trống, lưu phân tách bằng dấu "/" trong correctAnswerText (xem
 * ExerciseAttemptService#parseAcceptedAnswers). Hiện TẤT CẢ phương án khi reveal cho học sinh
 * (không chỉ phương án đầu tiên), format lại khoảng trắng quanh dấu "/" cho dễ đọc.
 */
function formatCorrectAnswerText(correctAnswerText: string | null | undefined): string {
  if (!correctAnswerText) {
    return "—";
  }
  return correctAnswerText
    .split("/")
    .map((s) => s.trim())
    .filter((s) => s.length > 0)
    .join(" / ");
}

/**
 * V177 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-15) — UC-24/UC-27 A2: câu đã được
 * mang nguyên nội dung từ lượt làm TRƯỚC (đã đúng) sang lượt "Làm lại" hiện tại (xem
 * ExerciseAttemptService#startAttempt) — phải hiện dạng chỉ xem/khoá, không cho sửa, độc lập với
 * readOnly toàn-attempt (câu này khoá NGAY CẢ KHI attempt đang IN_PROGRESS).
 */
export function isLockedCarriedOver(answer: StudentAnswerResponse | undefined): boolean {
  return answer?.carriedOverFromPreviousAttempt === true;
}

/** V177 — chỉ báo nhỏ cạnh câu đã khoá vì carry-forward, khác hẳn LockedAnswerBanner (đó là "chưa được XEM đáp án"). */
function CarriedOverBadge() {
  const { t } = useTranslation("portal-exercises");
  return (
    <span className="inline-flex items-center gap-1 text-[13px] font-extrabold text-teal-deep bg-teal/10 border border-teal/20 px-2 py-0.5 rounded-full">
      <Lock size={10} /> {t("takeExercise.carriedOverBadge")}
    </span>
  );
}

/**
 * UC-24/UC-27: màn "Làm bài" thật — mở/tiếp tục lượt làm, trả lời từng câu, nộp bài.
 * Luôn ưu tiên tiếp tục/xem lại attempt đã có (item.myLatestAttemptId) qua getAttempt —
 * chỉ startAttempt khi CHƯA có attempt nào, tránh vô tình tạo thêm lượt làm mới lúc đang
 * còn 1 lượt IN_PROGRESS (backend không tự resume, startAttempt luôn tạo attempt mới).
 */
export default function TakeExerciseModal({ item, onClose }: TakeExerciseModalProps) {
  useLockBodyScroll(true);
  const { t } = useTranslation("portal-exercises");
  const [attempt, setAttempt] = useState<ExerciseAttemptResponse | null>(null);
  const [questions, setQuestions] = useState<ExerciseQuestionResponse[]>([]);
  const [answersByQuestion, setAnswersByQuestion] = useState<Map<number, StudentAnswerResponse>>(new Map());
  const [textDraft, setTextDraft] = useState<Record<number, string>>({});
  const [savingQuestionId, setSavingQuestionId] = useState<number | null>(null);
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — gợi ý tapescript câu hỏi Nghe, mở
  // khóa sau khi nghe HẾT audio đủ số lần cấu hình. Key theo listeningKeyOf (groupKey nếu có, không
  // thì "Q"+questionId) — khớp cách backend gộp bộ đếm cho nhóm "1 audio nhiều câu" (xem
  // ListeningHintService, ListeningGroupBuilder ở Admin).
  const [listeningProgress, setListeningProgress] = useState<Map<string, ListeningPlayProgressResponse>>(new Map());
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Thay window.confirm() bằng popup nội tuyến khớp giao diện app — không có Modal dùng chung ở
  // app này (chỉ 1 chỗ dùng), nên làm bước xác nhận ngay trong modal đang mở thay vì Modal lồng Modal.
  const [confirmingSubmit, setConfirmingSubmit] = useState(false);
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — bài bị hệ thống dừng ép do vượt
  // ngưỡng vi phạm (khác banner "justViolated" nhỏ — đây là cảnh báo mạnh, chặn tương tác cho tới
  // khi học sinh bấm "Đã hiểu"). Kết quả đã được ghi nhận ở server ngay lúc dừng — chỉ cần tải lại
  // attempt để cập nhật trạng thái readOnly, học sinh làm lại qua nút "Làm lại" ở màn danh sách đề.
  const [stoppedByViolation, setStoppedByViolation] = useState(false);
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — popup kết quả hiện đúng 1 lần
  // ngay sau khi bấm "Nộp bài" (không hiện lại khi mở xem lại 1 lượt đã nộp từ trước — đó là lý do
  // tách riêng justSubmitted thay vì suy từ attempt.status). exerciseMeta cho biết còn bao nhiêu
  // lượt làm lại (allowRetake/maxAttempts) để hiện đúng câu "bạn còn N lần để làm lại".
  const [justSubmitted, setJustSubmitted] = useState(false);
  const [exerciseMeta, setExerciseMeta] = useState<ExerciseMetaResponse | null>(null);
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12 — bấm "Đóng" khi đang có lượt làm
  // IN_PROGRESS phải hỏi lại (dễ đóng nhầm khi đang giám sát chống gian lận) — không hỏi khi chỉ đang
  // xem lại 1 lượt đã chấm (không có gì để "thoát dở dang").
  const [confirmingClose, setConfirmingClose] = useState(false);
  /**
   * V152 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — hỏi xác nhận trước khi TỰ
   * NGUYỆN đóng lượt sớm (không hoàn tác được) — xem handleRevealAndClose.
   */
  const [confirmingRevealClose, setConfirmingRevealClose] = useState(false);
  const [revealClosing, setRevealClosing] = useState(false);
  /**
   * V152 — khi bấm "Xem đáp án & đóng lượt" thành công TRONG PHIÊN modal đang mở, `item` (prop truyền
   * từ danh sách cha) chưa kịp tải lại nên `item.canStartNewAttempt` vẫn còn giá trị CŨ (true) — cờ cục
   * bộ này ghi đè ngay để ẩn nút "Xem đáp án"/"Làm lại" đúng lúc mà không cần chờ đóng modal rồi mở lại.
   */
  const [justClosedEarly, setJustClosedEarly] = useState(false);

  /**
   * V152 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — UC-24 A1: lượt làm CÒN
   * IN_PROGRESS (chưa nộp) nhưng đã quá hạn nộp và bản giao không cho nộp muộn — khoá HẲN thành chỉ
   * xem, không cho gõ tiếp/nộp bài nữa (mirror rào đã thêm ở BE saveAnswer). Trước đây chỉ readOnly khi
   * attempt đã nộp xong (status khác IN_PROGRESS), lượt dang dở quá hạn vẫn cho sửa vô thời hạn dù
   * không bao giờ nộp nổi (submitAttempt luôn 422 quá hạn).
   */
  const overdueLockedInProgress =
    attempt?.status === "IN_PROGRESS" && item.dueAt != null && !item.lateSubmissionAllowed && new Date(item.dueAt).getTime() < Date.now();
  const readOnly = attempt != null && (attempt.status !== "IN_PROGRESS" || overdueLockedInProgress);
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12 — tách riêng khỏi readOnly (readOnly
  // vẫn false khi attempt == null, vốn đúng cho việc khoá ô nhập vì chưa render câu hỏi nào cả, nhưng
  // KHÔNG được dùng để quyết định hiện nút "Nộp bài": trước đây !readOnly cũng đúng khi attempt == null
  // (VD load lỗi/hết lượt) nên vẫn hiện nhầm nút "Nộp bài" dù chẳng có lượt IN_PROGRESS nào để nộp.
  const hasActiveAttempt = attempt != null && attempt.status === "IN_PROGRESS" && !overdueLockedInProgress;
  /**
   * UC-24/A4, UC-27/A2: đề có giới hạn số lần làm lại (exerciseMeta.maxAttempts khác NULL) — số lượt
   * CÒN LẠI trước khi đáp án được mở khóa (mirror công thức BE: revealAnswer khi attemptNumber >=
   * maxAttempts). null = đề không giới hạn số lần làm lại HOẶC exerciseMeta chưa tải xong — không
   * hiện thông báo khóa đáp án cho tới khi có dữ liệu chắc chắn.
   */
  const attemptsRemainingBeforeAnswer =
    exerciseMeta?.maxAttempts != null && attempt != null ? Math.max(0, exerciseMeta.maxAttempts - attempt.attemptNumber) : null;
  /**
   * V152 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — UC-24/A4, UC-27/A2: lượt làm
   * gần nhất đã ĐẠT nhưng đáp án vẫn còn bị khoá (còn lượt làm lại, chưa tới lượt cuối) — cho học sinh
   * tự nguyện dừng lại NGAY để xem đáp án luôn, đổi lại mất quyền làm lại thêm (xem handleRevealAndClose).
   * Chỉ có ý nghĩa khi maxAttempts hữu hạn (đáp án vốn hiện ngay nếu không giới hạn lượt, không có gì
   * để "mở sớm") và còn thật sự làm lại được. Bổ sung 2026-09-13 (fix bug thật) — dùng canRetakeExercise
   * thay vì chỉ item.canStartNewAttempt (xem Javadoc hàm đó): không có ý nghĩa "từ bỏ lượt làm lại để
   * xem đáp án sớm" nếu thật ra đã hết lượt/không cho làm lại rồi.
   */
  const canRevealAndClose =
    !justClosedEarly &&
    attempt != null &&
    attempt.status === "FULLY_GRADED" &&
    attempt.passed === true &&
    exerciseMeta != null &&
    exerciseMeta.maxAttempts != null &&
    canRetakeExercise(item, exerciseMeta, attempt);

  const loadAnswers = (attemptId: number) => {
    listAnswers(attemptId)
      .then((res) => setAnswersByQuestion(new Map(res.map((a) => [a.questionId, a]))))
      .catch(() => undefined);
  };

  /**
   * Guard bằng ref (không phải chỉ dựa vào dependency array) — startAttempt là POST tạo
   * bản ghi thật (không idempotent), React 18 StrictMode tự double-invoke useEffect ở môi
   * trường dev sẽ gọi 2 lần gần như cùng lúc nếu không chặn, tạo 2 attempt trùng cho 1 lần
   * mở đề (đã tự bắt được lỗi này khi verify — xem DB exercise_attempts trùng attempt_number).
   */
  const openedRef = useRef(false);

  useEffect(() => {
    if (openedRef.current) return;
    openedRef.current = true;
    setLoading(true);
    setError(null);

    const load = async () => {
      // Cần exerciseMeta (maxAttempts) TRƯỚC khi quyết định có mở lượt mới hay không (đảo thứ tự so
      // với trước — trước đây gọi song song, không chờ được).
      const meta = await getExercise(item.exerciseId);
      setExerciseMeta(meta);

      // V148 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23) — CHỦ Ý bỏ hẳn logic tự
      // động mở lượt MỚI khi lượt gần nhất chưa đạt/còn chờ chấm (trước đây tự startAttempt() ngay lúc
      // mở modal, gây 2 vấn đề: (1) học sinh bấm vào thẻ BTVN tưởng "xem lại" nhưng bị âm thầm tạo lượt
      // mới, (2) nếu đã quá hạn nộp thì bị chặn 422 ngay lúc mở, không xem lại được kết quả cũ). Giờ mở
      // modal LUÔN chỉ xem/tiếp tục đúng lượt gần nhất (myLatestAttemptId) — muốn làm lượt mới phải bấm
      // nút "Làm lại" tường minh trong modal (xem handleRetake), chỉ hiện khi item.canStartNewAttempt.
      const attemptRes: ExerciseAttemptResponse =
        item.myLatestAttemptId == null ? await startAttempt(item.exerciseId, item.assignmentId) : await getAttempt(item.myLatestAttemptId);

      const questionRes = await listExerciseQuestions(item.exerciseId);
      setAttempt(attemptRes);
      setQuestions([...questionRes].sort((a, b) => a.displayOrder - b.displayOrder));
      loadAnswers(attemptRes.id);
    };

    load()
      .catch((err) => setError(friendlyApiErrorMessage(err, t("takeExercise.loadError"))))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [item.exerciseId, item.myLatestAttemptId]);

  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — xem Javadoc useIntegrityMonitor.
  const attemptId = attempt?.id;
  const { violationCount, isMonitoringActive, justViolated, suppressForFilePicker } = useIntegrityMonitor({
    enabled: !readOnly && attemptId != null && !stoppedByViolation,
    autoFlushIntervalMs: 20000,
    onFlush: (events) => {
      if (attemptId == null) return;
      recordIntegrityEvents(attemptId, { events })
        .then((res) => {
          if (res.attemptStopped) {
            setStoppedByViolation(true);
            getAttempt(attemptId).then((updated) => setAttempt(updated)).catch(() => undefined);
          }
        })
        .catch(() => undefined);
    }
  });

  const handleChoiceAnswer = async (questionId: number, choiceIds: number[]) => {
    if (!attempt || readOnly) return;
    setSavingQuestionId(questionId);
    setError(null);
    try {
      const res = await saveAnswer(attempt.id, { questionId, selectedChoiceIds: choiceIds });
      setAnswersByQuestion((prev) => new Map(prev).set(questionId, res));
    } catch (err) {
      setError(friendlyApiErrorMessage(err, t("takeExercise.saveAnswerError")));
    } finally {
      setSavingQuestionId(null);
    }
  };

  /** V78 — WORD_BANK/SENTENCE_BUILDING: lưu ngay khi học sinh chọn đủ (không chờ blur như văn bản tự do). */
  const handleStructuredAnswer = async (questionId: number, values: string[]) => {
    if (!attempt || readOnly) return;
    setSavingQuestionId(questionId);
    setError(null);
    try {
      const res = await saveAnswer(attempt.id, { questionId, structuredAnswer: values });
      setAnswersByQuestion((prev) => new Map(prev).set(questionId, res));
    } catch (err) {
      setError(friendlyApiErrorMessage(err, t("takeExercise.saveAnswerError")));
    } finally {
      setSavingQuestionId(null);
    }
  };

  /**
   * V78 — SPEAKING (Speaking oral gốc lẫn "Nghe & nộp audio" mới): học sinh upload file audio ghi âm
   * câu trả lời, lưu URL qua saveAnswer.audioAnswerUrl — vá gap cũ (Portal trước đây không có UI nộp
   * audio thật cho SPEAKING dù backend đã hỗ trợ).
   */
  const handleAudioAnswer = async (questionId: number, file: File) => {
    if (!attempt || readOnly) return;
    setSavingQuestionId(questionId);
    setError(null);
    try {
      const { url } = await uploadMedia(file, "EXERCISE_ANSWER_SUBMISSION");
      const res = await saveAnswer(attempt.id, { questionId, audioAnswerUrl: url });
      setAnswersByQuestion((prev) => new Map(prev).set(questionId, res));
    } catch (err) {
      setError(friendlyApiErrorMessage(err, t("takeExercise.submitAudioError")));
    } finally {
      setSavingQuestionId(null);
    }
  };

  /** Gọi khi audio của 1 câu hỏi Nghe phát tới cuối (sự kiện `ended`) — im lặng bỏ qua lỗi mạng, không chặn học sinh nghe/làm bài tiếp. */
  const handleListeningEnded = async (q: ExerciseQuestionResponse) => {
    if (!attempt || readOnly) return;
    try {
      const res = await recordListeningPlay(attempt.id, q.questionId);
      setListeningProgress((prev) => new Map(prev).set(listeningKeyOf(q), res));
    } catch {
      // Không hiện lỗi — nghe lại vẫn hoạt động bình thường, chỉ là chưa ghi được lượt này.
    }
  };

  const handleTextBlur = async (questionId: number) => {
    if (!attempt || readOnly) return;
    const text = textDraft[questionId];
    if (text === undefined) return;
    setSavingQuestionId(questionId);
    setError(null);
    try {
      const res = await saveAnswer(attempt.id, { questionId, answerText: text });
      setAnswersByQuestion((prev) => new Map(prev).set(questionId, res));
    } catch (err) {
      setError(friendlyApiErrorMessage(err, t("takeExercise.saveAnswerError")));
    } finally {
      setSavingQuestionId(null);
    }
  };

  const handleSubmit = async () => {
    if (!attempt) return;
    setSubmitting(true);
    setError(null);
    try {
      const updated = await submitAttempt(attempt.id);
      setAttempt(updated);
      loadAnswers(updated.id);
      setJustSubmitted(true);
    } catch (err) {
      setError(friendlyApiErrorMessage(err, t("takeExercise.submitError")));
    } finally {
      setSubmitting(false);
    }
  };

  /**
   * V148 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23) — mở lượt làm MỚI tường minh,
   * chỉ bấm được khi đang xem 1 lượt cũ (readOnly) và canRetakeExercise() còn true (chưa hết lượt,
   * chưa quá hạn nộp — xem ExerciseAttemptService#toAssignedResponse). Thay hẳn cho logic tự động mở
   * lượt mới lúc vào modal đã bỏ ở load() — học sinh phải chủ động bấm mới tạo lượt mới.
   *
   * V177 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-15) — KHÔNG còn tự xoá trắng
   * answersByQuestion: lượt mới do BE tạo đã có sẵn các câu ĐÃ ĐÚNG ở lượt trước (carry-forward, xem
   * ExerciseAttemptService#startAttempt) — loadAnswers(fresh.id) sẽ tải đúng các câu đó (kèm cờ
   * carriedOverFromPreviousAttempt để khoá lại, xem isLockedCarriedOver), chỉ còn câu sai/chưa trả lời
   * là thật sự trống.
   */
  const handleRetake = async () => {
    setError(null);
    setLoading(true);
    try {
      const fresh = await startAttempt(item.exerciseId, item.assignmentId);
      setAttempt(fresh);
      setTextDraft({});
      setJustSubmitted(false);
      loadAnswers(fresh.id);
    } catch (err) {
      setError(friendlyApiErrorMessage(err, t("takeExercise.loadError")));
    } finally {
      setLoading(false);
    }
  };

  /**
   * V152 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — xem Javadoc canRevealAndClose
   * ở trên. Gọi sau khi học sinh đã xác nhận ở popup cảnh báo (không hoàn tác được) — tải lại đáp án
   * ngay để hiện đầy đủ (BE giờ trả revealAnswer=true cho lượt này), giữ modal đang mở nguyên vị trí.
   */
  const handleRevealAndClose = async () => {
    if (!attempt) return;
    setRevealClosing(true);
    setError(null);
    try {
      await revealAndCloseAttempt(attempt.id);
      setJustClosedEarly(true);
      loadAnswers(attempt.id);
    } catch (err) {
      setError(friendlyApiErrorMessage(err, t("takeExercise.loadError")));
    } finally {
      setRevealClosing(false);
    }
  };

  /**
   * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-22) — thời gian làm bài tính từ lúc mở
   * bài (attempt.startedAt), KHÁC hạn nộp (dueAt, xem AssignmentsTab.tsx — đã bỏ đếm ngược ở đó theo
   * yêu cầu người dùng). exerciseMeta.timeLimitMinutes NULL = không giới hạn, giữ nguyên hành vi cũ.
   * Chỉ tính/đếm khi còn lượt IN_PROGRESS — xem lại 1 lượt đã nộp thì không cần đếm ngược nữa.
   */
  const timeLimitDeadlineIso = useMemo(() => {
    if (!hasActiveAttempt || !attempt || exerciseMeta?.timeLimitMinutes == null) return null;
    return new Date(new Date(attempt.startedAt).getTime() + exerciseMeta.timeLimitMinutes * 60_000).toISOString();
  }, [hasActiveAttempt, attempt, exerciseMeta?.timeLimitMinutes]);
  const { remainingMs: timeLimitRemainingMs } = useCountdown(timeLimitDeadlineIso);
  const timeLimitTotalMs = exerciseMeta?.timeLimitMinutes != null ? exerciseMeta.timeLimitMinutes * 60_000 : null;
  // Cảnh báo ở 10% thời gian cuối, tối thiểu 1 phút (đề rất ngắn vẫn có đủ thời gian đọc cảnh báo).
  const timeLimitWarning =
    timeLimitRemainingMs != null && timeLimitTotalMs != null && timeLimitRemainingMs <= Math.max(60_000, timeLimitTotalMs * 0.1);

  /** Hết giờ — đã xác nhận với người dùng 2026-08-22: tự động nộp bài (dùng phần đã làm dở). Guard bằng
   * ref tránh gọi handleSubmit() lặp lại khi remainingMs dao động quanh 0 do tick không chính xác tuyệt đối. */
  const autoSubmitRef = useRef(false);
  useEffect(() => {
    if (hasActiveAttempt && timeLimitRemainingMs != null && timeLimitRemainingMs <= 0 && !autoSubmitRef.current) {
      autoSubmitRef.current = true;
      handleSubmit();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hasActiveAttempt, timeLimitRemainingMs]);

  return (
    // Lớp phủ toàn màn hình (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12) — thay cho
    // popup căn giữa cũ, mirror đúng pattern ReviewVideoTaskModal (fixed inset-0 bg-white z-[100]) để
    // đồng nhất mọi dạng bài tập (trắc nghiệm/điền từ/nghe/nói/sắp xếp câu) và tận dụng hết chiều cao
    // màn hình trên mobile lẫn desktop, không bị bó hẹp trong khung max-h-[85vh] như trước.
    <div className="fixed inset-0 bg-white z-[100] flex flex-col">
      {/* Popup cảnh báo tức thời — hiện ngay lúc phát hiện vi phạm mới, tự mờ dần sau ~3.5s, khác banner
          tĩnh bên dưới (chỉ đổi số đếm, học sinh dễ không để ý). Neo "fixed" ở gốc màn hình để luôn nổi
          trên cùng bất kể đang cuộn tới đâu bên trong nội dung đề.
          Bổ sung 2026-09-04 (fix bug thật, đã xác nhận với người dùng) — canh giữa theo ĐÚNG khung nội dung
          header (max-w-2xl lg:max-w-3xl mx-auto) thay vì canh giữa theo cả viewport trình duyệt — 2 khung
          khác chiều rộng nên trước đây nhìn lệch hẳn sang trái so với tiêu đề/badge phía trên. */}
      {justViolated && !stoppedByViolation && (
        <div className="fixed top-16 sm:top-20 inset-x-0 z-[110] px-4 sm:px-6 flex justify-center">
          <div className="max-w-2xl lg:max-w-3xl w-full flex justify-center">
            <div key={violationCount} role="alert" className="flex items-center gap-2 bg-rose-600 text-white pl-3 pr-4 py-2.5 rounded-2xl shadow-xl animate-alert-pop-centered max-w-full">
              <ShieldAlert size={18} className="shrink-0" />
              <span className="text-xs font-black">{t("monitoring.violationToast")}</span>
            </div>
          </div>
        </div>
      )}

      {/* Cảnh báo mạnh — chặn tương tác, khác hẳn toast nhỏ ở trên — hiện đúng 1 lần khi vừa bị dừng ép. */}
      {stoppedByViolation && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center p-4 z-[120]">
          <div className="bg-white rounded-[20px] w-full max-w-md p-6 space-y-4 text-center shadow-xl">
            <ShieldAlert size={40} className="text-rose-600 mx-auto" />
            <h3 className="text-base font-black text-ink">{t("takeExercise.stoppedByViolation.title")}</h3>
            <p className="text-xs font-bold text-muted leading-relaxed">{t("takeExercise.stoppedByViolation.description")}</p>
            <button
              onClick={() => {
                setStoppedByViolation(false);
                onClose();
              }}
              className="text-xs font-extrabold text-white bg-teal px-5 py-2.5 rounded-xl"
            >
              {t("takeExercise.stoppedByViolation.understood")}
            </button>
          </div>
        </div>
      )}

      {/*
       * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — submitAttempt() có thể gọi AI
       * chấm bài Writing đồng bộ (Gemini, thực đo ~10-20s) NGAY trong request nộp bài — trước đây chỉ
       * disable nút "Nộp bài" đổi chữ "Đang nộp...", học sinh không biết có đang chờ AI chấm hay hệ
       * thống bị treo. Lớp phủ toàn màn hình rõ ràng hơn, đặt tên đúng việc đang chờ.
       */}
      {submitting && (
        <div className="fixed inset-0 bg-white/90 backdrop-blur-sm flex items-center justify-center z-[125]">
          <div className="flex flex-col items-center gap-3 text-center px-6">
            <Loader2 size={36} className="text-teal animate-spin" />
            <p className="text-sm font-extrabold text-ink">{t("takeExercise.gradingOverlay.title")}</p>
            <p className="text-xs font-bold text-muted max-w-xs">{t("takeExercise.gradingOverlay.description")}</p>
          </div>
        </div>
      )}

      {/* Popup kết quả sau khi nộp bài — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06.
          Chỉ hiện đúng 1 lần ngay sau khi bấm "Nộp bài" (justSubmitted), không hiện lại khi mở xem
          lại 1 lượt đã nộp từ trước. */}
      {justSubmitted && attempt && (
        <SubmitResultPopup
          attempt={attempt}
          exerciseTitle={item.title}
          exerciseMeta={exerciseMeta}
          hasFeedback={[...answersByQuestion.values()].some((a) => !!a.gradingFeedback)}
          // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — fix bug thật: gọi onFinished()
          // ở đây (như trước) đóng LUÔN modal + load() lại danh sách cha, đưa học sinh bay thẳng về màn
          // danh sách NGAY khi vừa đóng popup — chưa kịp đọc hộp "Nhận xét bài làm" chi tiết vừa thêm
          // (nằm NGAY BÊN DƯỚI popup này, trong modal đang mở). CHỈ đóng popup, giữ nguyên modal đang mở
          // để học sinh đọc nhận xét — đóng modal thật sự (kèm load() làm mới danh sách) dời qua nút
          // X/Thoát như luồng "xem lại 1 lượt đã nộp" bình thường.
          onClose={() => setJustSubmitted(false)}
        />
      )}

      <div className="border-b border-line/60 shrink-0">
        {/* Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 (fix bug thật) — trước đây luôn
            1 hàng ngang (flex items-center) dù màn hình hẹp, nên khi vừa hiện đủ 2 nút hành động ("Xem
            đáp án & đóng lượt" + "Làm lại") vừa có tiêu đề dài, tổng bề rộng vượt màn hình mobile khiến
            khối tiêu đề (min-w-0, được phép co) bị bóp gần như về 0 — nhìn như tiêu đề bị nút đè lên.
            Giờ xuống dòng (flex-col) dưới breakpoint sm, tiêu đề 1 hàng riêng + hàng nút riêng bên dưới,
            hàng nút cũng tự xuống dòng tiếp (flex-wrap) nếu 2 nút + nút đóng vẫn không đủ chỗ. */}
        <div className="max-w-2xl lg:max-w-3xl w-full mx-auto px-4 sm:px-6 py-3 sm:py-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="min-w-0">
            <h3 className="text-lg sm:text-xl lg:text-2xl font-extrabold text-ink truncate">{item.title}</h3>
            {/* Bổ sung 2026-09-04 (đã xác nhận với người dùng) — mirror AssignmentsTab.tsx/
                BatchTakeExerciseModal.tsx: hiện Lesson + Unit/SubTopic để phân biệt Lesson trùng tên. */}
            {(item.unitTitle || item.subTopicTitle) && (
              <p className="text-sm font-bold text-muted truncate">
                {item.examTitle} · {[item.unitTitle, item.subTopicTitle].filter(Boolean).join(" · ")}
              </p>
            )}
            {attempt && (
              <p className="text-[10px] sm:text-xs text-muted font-bold mt-0.5">
                {t("takeExercise.attemptNumber", { number: attempt.attemptNumber })} ·{" "}
                {attempt.status === "IN_PROGRESS"
                  ? t("takeExercise.status.inProgress")
                  : attempt.status === "FULLY_GRADED"
                    ? t("takeExercise.status.fullyGraded")
                    : t("takeExercise.status.submittedPendingGrading")}
                {attempt.totalScore != null && t("takeExercise.scoreSuffix", { score: attempt.totalScore })}
              </p>
            )}
          </div>
          <div className="flex items-center gap-2 flex-wrap justify-end shrink-0">
            {/* Chip ghim góc (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12) — thay cho
                banner amber căng hết chiều rộng trước đây, gọn lại thành 1 badge nhỏ ngay cạnh nút Đóng,
                bấm/hover mới hiện đủ dòng cảnh báo. */}
            {isMonitoringActive && <MonitoringBadge violationCount={violationCount} />}
            {/* V152 — nút "Xem đáp án & đóng lượt" tường minh, chỉ hiện khi lượt gần nhất ĐÃ ĐẠT nhưng
                đáp án còn bị khoá vì còn lượt làm lại — xem canRevealAndClose/handleRevealAndClose. */}
            {canRevealAndClose && (
              <button
                onClick={() => setConfirmingRevealClose(true)}
                disabled={revealClosing}
                className="shrink-0 flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-amber-50 hover:bg-amber-100 text-amber-800 border border-amber-200 text-xs font-extrabold transition-colors disabled:opacity-60"
              >
                <KeyRound size={14} /> {t("takeExercise.revealAndClose.button")}
              </button>
            )}
            {/* V148 — nút "Làm lại" tường minh, chỉ hiện khi đang xem 1 lượt cũ (readOnly) và còn lượt.
                Bổ sung 2026-09-13 (fix bug thật) — dùng canRetakeExercise (item.canStartNewAttempt +
                allowRetake/maxAttempts tính TƯƠI, xem Javadoc hàm đó) thay vì chỉ item.canStartNewAttempt
                (cờ đứng yên từ lúc tải danh sách, không theo kịp khi vừa làm lại nhiều lần liên tiếp
                trong cùng phiên) — xem handleRetake. */}
            {readOnly && attempt && exerciseMeta && canRetakeExercise(item, exerciseMeta, attempt) && !justClosedEarly && (
              <button
                onClick={handleRetake}
                disabled={loading}
                className="shrink-0 flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-teal/10 hover:bg-teal/20 text-teal-deep border border-teal/20 text-xs font-extrabold transition-colors disabled:opacity-60"
              >
                <RotateCcw size={14} /> {t("assignments.exercise.action.retake")}
              </button>
            )}
            <button
              onClick={() => (hasActiveAttempt ? setConfirmingClose(true) : onClose())}
              aria-label={t("takeExercise.closeAriaLabel")}
              // Làm nổi bật (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12) — trước đây
              // chỉ là link chữ mờ dễ bỏ sót, giờ là nút tròn viền đỏ nhạt, dễ nhận biết hành động thoát.
              className="shrink-0 flex items-center justify-center w-9 h-9 sm:w-10 sm:h-10 rounded-full bg-rose-50 hover:bg-rose-100 text-rose-600 border border-rose-200 transition-colors"
            >
              <X size={18} className="sm:w-5 sm:h-5" />
            </button>
          </div>
        </div>
      </div>

      {timeLimitDeadlineIso && timeLimitRemainingMs != null && (
        <div
          className={`shrink-0 px-4 sm:px-6 py-2 text-center text-xs font-extrabold tabular-nums ${
            timeLimitWarning ? "bg-rose-50 text-rose-700 border-b border-rose-100" : "bg-amber-50 text-amber-800 border-b border-amber-100"
          }`}
        >
          {t("takeExercise.timeLimit.remainingPrefix")}
          {formatRemaining(timeLimitRemainingMs, t)}
          {timeLimitWarning && ` — ${t("takeExercise.timeLimit.warning")}`}
        </div>
      )}

      {/* V152 — giải thích vì sao bài đang dở (IN_PROGRESS) bỗng thành chỉ-xem — xem overdueLockedInProgress. */}
      {overdueLockedInProgress && (
        <div className="shrink-0 px-4 sm:px-6 py-2 text-center text-xs font-extrabold bg-coral/10 text-coral border-b border-coral/20">
          {t("takeExercise.overdueLocked.banner")}
        </div>
      )}

      {/* V152 — xác nhận trước khi TỰ NGUYỆN đóng lượt sớm để xem đáp án (không hoàn tác được). */}
      {confirmingRevealClose && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center p-4 z-[120]">
          <div className="bg-white rounded-[20px] w-full max-w-sm p-6 space-y-4 text-center shadow-xl">
            <KeyRound size={36} className="text-amber-600 mx-auto" />
            <h3 className="text-base font-black text-ink">{t("takeExercise.revealAndClose.confirmTitle")}</h3>
            <p className="text-xs font-bold text-muted leading-relaxed">{t("takeExercise.revealAndClose.confirmDescription")}</p>
            <div className="flex flex-col sm:flex-row gap-2">
              <button
                onClick={() => setConfirmingRevealClose(false)}
                className="flex-1 px-4 py-2.5 bg-white hover:bg-slate-100 border border-line rounded-xl text-xs font-extrabold text-ink"
              >
                {t("takeExercise.revealAndClose.cancel")}
              </button>
              <button
                onClick={() => {
                  setConfirmingRevealClose(false);
                  handleRevealAndClose();
                }}
                disabled={revealClosing}
                className="flex-1 px-4 py-2.5 bg-amber-600 hover:bg-amber-700 text-white rounded-xl text-xs font-extrabold disabled:opacity-60"
              >
                {revealClosing ? t("takeExercise.revealAndClose.closing") : t("takeExercise.revealAndClose.confirmButton")}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Cảnh báo trước khi đóng (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12) — chỉ
          hỏi khi đang có lượt IN_PROGRESS (dễ đóng nhầm lúc đang bị giám sát chống gian lận); xem lại
          1 lượt đã chấm thì đóng thẳng, không cần hỏi. */}
      {confirmingClose && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center p-4 z-[120]">
          <div className="bg-white rounded-[20px] w-full max-w-sm p-6 space-y-4 text-center shadow-xl">
            <ShieldAlert size={36} className="text-amber-600 mx-auto" />
            <h3 className="text-base font-black text-ink">{t("takeExercise.confirmClose.title")}</h3>
            <p className="text-xs font-bold text-muted leading-relaxed">{t("takeExercise.confirmClose.description")}</p>
            <div className="flex flex-col sm:flex-row gap-2">
              <button
                onClick={() => setConfirmingClose(false)}
                className="flex-1 px-4 py-2.5 bg-white hover:bg-slate-100 border border-line rounded-xl text-xs font-extrabold text-ink"
              >
                {t("takeExercise.confirmClose.stay")}
              </button>
              <button
                onClick={() => {
                  setConfirmingClose(false);
                  onClose();
                }}
                className="flex-1 px-4 py-2.5 bg-rose-600 hover:bg-rose-700 text-white rounded-xl text-xs font-extrabold"
              >
                {t("takeExercise.confirmClose.stillClose")}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="flex-1 overflow-y-auto">
        <div className="max-w-2xl lg:max-w-3xl w-full mx-auto p-4 sm:p-6 space-y-5">
          {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

          {loading ? (
            <p className="text-xs text-muted font-bold flex items-center gap-2">
              <Loader2 size={14} className="animate-spin" /> {t("takeExercise.loadingExam")}
            </p>
          ) : questions.length === 0 ? (
            <p className="text-xs text-muted font-bold italic">{t("takeExercise.noQuestions")}</p>
          ) : (
            groupQuestionsByGroupKey(questions).map((block) =>
              block.type === "grid" ? (
                <GridQuestionGroup
                  key={block.groupKey}
                  block={block}
                  answersByQuestion={answersByQuestion}
                  readOnly={readOnly}
                  savingQuestionId={savingQuestionId}
                  attemptsRemainingBeforeAnswer={attemptsRemainingBeforeAnswer}
                  onChoiceToggle={handleChoiceAnswer}
                  textDraft={textDraft}
                  onTextChange={(questionId, v) => setTextDraft((prev) => ({ ...prev, [questionId]: v }))}
                  onTextBlur={handleTextBlur}
                  onAudioUpload={handleAudioAnswer}
                  onFilePickerOpen={suppressForFilePicker}
                  attemptId={attempt?.id}
                  listeningProgress={listeningProgress}
                  onListeningEnded={handleListeningEnded}
                />
              ) : (
                <QuestionBlock
                  key={block.question.id}
                  question={block.question}
                  answer={answersByQuestion.get(block.question.questionId)}
                  readOnly={readOnly || isLockedCarriedOver(answersByQuestion.get(block.question.questionId))}
                  saving={savingQuestionId === block.question.questionId}
                  attemptsRemainingBeforeAnswer={attemptsRemainingBeforeAnswer}
                  textValue={textDraft[block.question.questionId]}
                  onTextChange={(v) => setTextDraft((prev) => ({ ...prev, [block.question.questionId]: v }))}
                  onTextBlur={() => handleTextBlur(block.question.questionId)}
                  onChoiceToggle={(choiceIds) => handleChoiceAnswer(block.question.questionId, choiceIds)}
                  onStructuredAnswer={(values) => handleStructuredAnswer(block.question.questionId, values)}
                  onAudioUpload={(file) => handleAudioAnswer(block.question.questionId, file)}
                  onFilePickerOpen={suppressForFilePicker}
                  attemptId={attempt?.id}
                  listeningProgress={listeningProgress}
                  onListeningEnded={handleListeningEnded}
                />
              )
            )
          )}
        </div>
      </div>

      {hasActiveAttempt && confirmingSubmit && (
        <div className="border-t border-line/60 bg-amber-50 shrink-0">
          <div className="max-w-2xl lg:max-w-3xl w-full mx-auto px-4 sm:px-6 py-3 flex flex-wrap items-center justify-between gap-2">
            <span className="text-xs font-bold text-amber-800">{t("takeExercise.confirmSubmit.message")}</span>
            <div className="flex gap-2 shrink-0">
              <button
                onClick={() => setConfirmingSubmit(false)}
                className="text-xs font-extrabold text-slate-600 bg-white border border-slate-200 px-4 py-2 rounded-xl"
              >
                {t("takeExercise.confirmSubmit.cancel")}
              </button>
              <button
                onClick={() => {
                  setConfirmingSubmit(false);
                  handleSubmit();
                }}
                disabled={submitting}
                className="text-xs font-extrabold text-white bg-teal px-4 py-2 rounded-xl disabled:opacity-50"
              >
                {submitting ? t("takeExercise.submitting") : t("takeExercise.confirmSubmit.confirmButton")}
              </button>
            </div>
          </div>
        </div>
      )}

      {hasActiveAttempt && !confirmingSubmit && (
        <div className="border-t border-line/60 shrink-0">
          <div className="max-w-2xl lg:max-w-3xl w-full mx-auto px-4 sm:px-6 py-3 sm:py-4 flex justify-end">
            <button
              onClick={() => setConfirmingSubmit(true)}
              disabled={submitting || loading}
              className="text-sm sm:text-base font-extrabold text-white bg-teal px-5 sm:px-6 py-2.5 sm:py-3 rounded-xl disabled:opacity-50"
            >
              {submitting ? t("takeExercise.submitting") : t("takeExercise.submitButton")}
            </button>
          </div>
        </div>
      )}

      {/* Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12 — không còn lượt IN_PROGRESS nào
          để nộp (đang xem lại lượt đã chấm, hoặc hết lượt làm lại) thì thanh dưới cùng phải là "Thoát",
          không phải "Nộp bài" — trước đây thiếu nhánh này nên khi hết lượt vẫn hiện nhầm "Nộp bài". */}
      {!hasActiveAttempt && !loading && (
        <div className="border-t border-line/60 shrink-0">
          <div className="max-w-2xl lg:max-w-3xl w-full mx-auto px-4 sm:px-6 py-3 sm:py-4 flex justify-end">
            <button onClick={onClose} className="text-xs font-extrabold text-white bg-slate-600 hover:bg-slate-700 px-5 py-2.5 rounded-xl">
              {t("takeExercise.exitButton")}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — popup kết quả ngay sau khi nộp bài
 * (mirror tinh thần popup "Bài làm đã bị dừng" ở trên). 3 nhánh nội dung:
 * - Chưa chấm xong hết (còn tự luận/nói chờ GV chấm, status AUTO_GRADED): chỉ báo đã nộp, chưa có
 *   kết luận đạt/không đạt.
 * - FULLY_GRADED + đạt (passed=true): chúc mừng.
 * - FULLY_GRADED + chưa đạt (passed=false): báo % + ngưỡng cần đạt + số lượt còn lại để làm lại
 *   (suy từ exerciseMeta.allowRetake/maxAttempts — null maxAttempts = không giới hạn lượt).
 */
function SubmitResultPopup({
  attempt,
  exerciseTitle,
  exerciseMeta,
  hasFeedback,
  onClose
}: {
  attempt: ExerciseAttemptResponse;
  exerciseTitle: string;
  exerciseMeta: ExerciseMetaResponse | null;
  /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — có ít nhất 1 câu tự luận/nói đã có nhận xét chấm (tay/AI), gợi ý học sinh cuộn xuống xem chi tiết thay vì chỉ thấy % tổng. */
  hasFeedback: boolean;
  onClose: () => void;
}) {
  const { t } = useTranslation("portal-exercises");
  const fullyGraded = attempt.status === "FULLY_GRADED";
  const passed = fullyGraded ? attempt.passed : null;

  let remainingText: string | null = null;
  if (fullyGraded && passed === false && exerciseMeta) {
    if (!exerciseMeta.allowRetake) {
      remainingText = t("takeExercise.resultPopup.retakeNotAllowed");
    } else if (exerciseMeta.maxAttempts == null) {
      remainingText = t("takeExercise.resultPopup.retakeAnytime");
    } else {
      const remaining = Math.max(0, exerciseMeta.maxAttempts - attempt.attemptNumber);
      remainingText =
        remaining > 0
          ? t("takeExercise.resultPopup.retakeRemaining", { count: remaining })
          : t("takeExercise.resultPopup.retakeExhausted");
    }
  }

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center p-4 z-[110]">
      <div className="bg-white rounded-[20px] w-full max-w-md p-6 space-y-4 text-center shadow-xl">
        {!fullyGraded ? (
          <>
            <CheckCircle2 size={40} className="text-teal mx-auto" />
            <h3 className="text-base font-black text-ink">{t("takeExercise.resultPopup.submittedTitle", { title: exerciseTitle })}</h3>
            <p className="text-xs font-bold text-muted leading-relaxed">{t("takeExercise.resultPopup.submittedDescription")}</p>
          </>
        ) : passed ? (
          <>
            <PartyPopper size={40} className="text-teal mx-auto" />
            <h3 className="text-base font-black text-ink">{t("takeExercise.resultPopup.passedTitle", { title: exerciseTitle })}</h3>
            <p className="text-xs font-bold text-teal-deep leading-relaxed">
              {t("takeExercise.resultPopup.passedDescription", { percentage: attempt.percentage ?? "—" })}
            </p>
          </>
        ) : (
          <>
            <RotateCcw size={40} className="text-coral mx-auto" />
            <h3 className="text-base font-black text-ink">{t("takeExercise.resultPopup.failedTitle", { title: exerciseTitle })}</h3>
            <p className="text-xs font-bold text-coral leading-relaxed">
              {t("takeExercise.resultPopup.failedDescription", {
                percentage: attempt.percentage ?? "—",
                threshold: exerciseMeta ? t("takeExercise.resultPopup.thresholdSuffix", { percent: exerciseMeta.passThresholdPercent }) : ""
              })}
            </p>
            {remainingText && <p className="text-xs font-bold text-muted leading-relaxed">{remainingText}</p>}
          </>
        )}
        {hasFeedback && <p className="text-[11px] text-muted font-bold italic">{t("takeExercise.resultPopup.seeFeedbackHint")}</p>}
        <button onClick={onClose} className="text-xs font-extrabold text-white bg-teal px-5 py-2.5 rounded-xl">
          {t("takeExercise.resultPopup.understood")}
        </button>
      </div>
    </div>
  );
}

export function QuestionBlock({
  question,
  displayNumber,
  answer,
  readOnly,
  saving,
  attemptsRemainingBeforeAnswer,
  textValue,
  onTextChange,
  onTextBlur,
  onChoiceToggle,
  onStructuredAnswer,
  onAudioUpload,
  onFilePickerOpen,
  attemptId,
  listeningProgress,
  onListeningEnded
}: {
  question: ExerciseQuestionResponse;
  /** V150 — số thứ tự hiển thị override (dùng khi ghép nhiều Bài vào 1 màn liên tục, xem BatchTakeExerciseModal) — mặc định question.displayOrder như cũ. */
  displayNumber?: number;
  answer: StudentAnswerResponse | undefined;
  readOnly: boolean;
  saving: boolean;
  attemptsRemainingBeforeAnswer: number | null;
  textValue: string | undefined;
  onTextChange: (v: string) => void;
  onTextBlur: () => void;
  onChoiceToggle: (choiceIds: number[]) => void;
  onStructuredAnswer: (values: string[]) => void;
  onAudioUpload: (file: File) => void;
  onFilePickerOpen: () => void;
  attemptId: number | undefined;
  listeningProgress: Map<string, ListeningPlayProgressResponse>;
  onListeningEnded: (q: ExerciseQuestionResponse) => void;
}) {
  const { t } = useTranslation("portal-exercises");
  const isChoiceQuestion = CHOICE_TYPES.has(question.questionType) && question.choices.length > 0;
  const isFillInBlank = question.questionType === "FILL_IN_BLANK";
  const isMultiSelect = question.questionType === "MULTIPLE_ANSWER";
  const selected = new Set(answer?.selectedChoiceIds ?? []);
  const correctIds = new Set(answer?.correctChoiceIds ?? []);
  /**
   * "Đã nộp bài + showCorrectAnswers=true" — BE chỉ điền 1 trong các field này khi điều kiện đó
   * đúng (xem Javadoc StudentAnswerResponse), nên chỉ cần kiểm tra correctChoiceIds cho câu trắc
   * nghiệm là KHÔNG đủ: câu Điền từ không có choices (correctChoiceIds luôn null) nên trước đây
   * không bao giờ hiện đáp án/giải thích dù đã tự chấm xong — sửa lại dùng chung 1 điều kiện cho
   * mọi loại câu hỏi.
   */
  const showFeedback = answer != null && isAnswerRevealed(answer);
  // UC-24/A4, UC-27/A2: câu tự chấm đã có kết quả (isCorrect) nhưng đáp án chưa lộ — do đề còn
  // giới hạn số lần làm lại và đây chưa phải lượt cuối cùng. Không áp dụng cho ESSAY/SPEAKING.
  const answerLockedByRetake = answer != null && answer.isAutoGradable && answer.isCorrect != null && !showFeedback;
  /**
   * V169 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-05) — học sinh CHƯA TỪNG động vào
   * câu này (không chọn/không gõ lần nào, kể cả để trắng) thì BE không có dòng student_answers thật —
   * listAnswers() giờ bù 1 "câu trả lời rỗng" tạm (KHÔNG lưu DB) để vẫn lộ đáp án đúng khi đủ điều
   * kiện (xem Javadoc ExerciseAttemptService#listAnswers), nhưng KHÔNG set isCorrect (giữ null, khác
   * true/false của câu đã tự chấm thật) — dùng đúng tín hiệu này để phân biệt "chưa trả lời" khỏi "trả
   * lời đúng/sai", tránh tô xanh đáp án đúng trông y hệt "học sinh đã chọn đúng".
   */
  const notAnswered = showFeedback && answer!.isAutoGradable && answer!.isCorrect == null;

  const toggleChoice = (choiceId: number) => {
    if (readOnly || saving) return;
    if (isMultiSelect) {
      const next = new Set(selected);
      if (next.has(choiceId)) next.delete(choiceId);
      else next.add(choiceId);
      onChoiceToggle([...next]);
    } else {
      onChoiceToggle([choiceId]);
    }
  };

  return (
    <div className="border border-line/60 rounded-[16px] p-4 sm:p-5 lg:p-6 space-y-3 lg:space-y-4">
      <div className="flex items-start justify-between gap-3">
        {/*
         * Bổ sung 2026-08-28 (đã xác nhận với người dùng) — tách số thứ tự câu ra dòng riêng khỏi nội
         * dung: dạng WORD_BANK nhiều câu con thường tự đánh số "1. 2. 3..." ngay trong nội dung, để
         * chung 1 dòng với số thứ tự câu gây nhìn nhầm thành 2 số dính nhau (VD "1. 1. Tom is...").
         *
         * Fix bug thật 2026-09-17 (đã xác nhận với người dùng qua ảnh chụp) — WORD_BANK dùng
         * questionContent làm CHÍNH VĂN BẢN TƯƠNG TÁC (đoạn văn/câu có "___", WordBankBlock render
         * ngay bên dưới với <select>/<input> thật) chứ không phải 1 câu hỏi ngắn tách biệt như mọi
         * loại khác — hiện lại y nguyên ở đây thành ra lặp NGUYÊN VĂN cả đoạn 2 lần (1 lần thô không
         * tương tác, 1 lần có ô điền thật). Ẩn hẳn span nội dung cho riêng WORD_BANK, chỉ giữ số thứ tự.
         */}
        <p className="text-sm sm:text-base lg:text-lg font-bold text-ink">
          <span className="block text-muted text-xs sm:text-sm uppercase tracking-wider mb-1">
            {t("takeExercise.question.numberPrefix", { number: displayNumber ?? question.displayOrder })}
          </span>
          {question.questionType !== "WORD_BANK" && (
            <span className="text-sm sm:text-sm lg:text-base whitespace-pre-line">{question.questionContent}</span>
          )}
        </p>
        <div className="flex items-center gap-2 shrink-0">
          {question.skill === "LISTENING" && question.audioUrl && attemptId != null && (
            <ListeningHintButton
              attemptId={attemptId}
              questionId={question.questionId}
              progress={listeningProgress.get(listeningKeyOf(question))}
              readOnly={readOnly}
            />
          )}
          <span className="text-[10px] sm:text-xs text-muted font-bold">{t("takeExercise.question.pointsSuffix", { points: question.points })}</span>
        </div>
      </div>

      {isLockedCarriedOver(answer) && <CarriedOverBadge />}

      <ListeningAudioBlock question={question} onEnded={() => onListeningEnded(question)} />

      {/* Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — ảnh minh họa câu hỏi (ESSAY/WORD_BANK/SENTENCE_BUILDING), trước đây soạn có ảnh nhưng học sinh không thấy vì DTO chưa trả field này. */}
      {question.imageUrl && <QuestionImages imageUrl={question.imageUrl} className="w-full max-w-[240px] rounded-xl border border-line/60" />}

      {isChoiceQuestion && question.choices.some((c) => c.imageUrl) ? (
        // V143 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23) — Listening "chọn đáp án
        // bằng hình": mỗi lựa chọn là 1 ảnh, hiện dạng lưới bấm-chọn thay vì dòng chữ. Logic chọn/lưu
        // đáp án dùng chung y hệt nhánh chữ bên dưới (toggleChoice/selected/correctIds).
        // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-03 — fix bug thật: lưới cố định
        // grid-cols-2 khiến đề 3 đáp án bị lệch hàng (2 ô hàng 1, 1 ô lẻ hàng 2) thay vì 1 hàng như đề
        // giấy gốc — giờ số cột khớp đúng số đáp án (tối đa 4/hàng). object-cover trước đây phóng to/cắt
        // ảnh gốc để lấp đầy ô aspect-square rất lớn (khi chỉ 2 cột) gây vỡ hình — đổi sang khung nền
        // trắng cố định + object-contain để ảnh luôn hiển thị nguyên vẹn, không bị kéo giãn/cắt xén.
        <div className={`grid gap-2 lg:gap-3 ${imageChoiceGridColsClass(question.choices.length)}`}>
          {question.choices.map((c) => {
            const isSelected = selected.has(c.id);
            const isCorrectChoice = correctIds.has(c.id);
            let stateClass = "border-line/70 bg-sky-2 hover:bg-sky";
            let labelClass = "text-muted";
            // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-08 — trước khi nộp, lựa chọn
            // ĐANG chọn chỉ có viền/nền teal nhạt (bg-teal/10), quá mờ để nhận ra ngay đã chọn đáp án
            // nào — mirror đúng màu đậm + icon check đã dùng ở trắc nghiệm Video từ kết nối
            // (ReviewVideoTaskModal.tsx: "picked ? bg-teal text-white border-teal").
            if (showFeedback) {
              if (isCorrectChoice) stateClass = "border-teal bg-teal/10";
              else if (isSelected) stateClass = "border-coral bg-coral/10";
            } else if (isSelected) {
              stateClass = "border-teal bg-teal text-white";
              labelClass = "text-white/80";
            }
            return (
              <button
                key={c.id}
                type="button"
                disabled={readOnly || saving}
                onClick={() => toggleChoice(c.id)}
                className={`relative text-left rounded-xl border-2 overflow-hidden transition-colors ${stateClass} disabled:cursor-default`}
              >
                <div className="w-full aspect-[4/3] bg-white flex items-center justify-center overflow-hidden">
                  <img src={c.imageUrl ?? undefined} alt={c.content} className="max-w-full max-h-full object-contain" />
                </div>
                <span className="flex items-center justify-between gap-1 px-2 py-1.5 text-[11px] sm:text-xs font-bold">
                  <span>
                    <span className={`${labelClass} mr-1`}>{c.choiceLabel}.</span>
                    {hasMeaningfulChoiceCaption(c.choiceLabel, c.content) && c.content}
                  </span>
                  {showFeedback && isCorrectChoice && <CheckCircle2 size={14} className="text-teal-deep shrink-0" />}
                  {showFeedback && !isCorrectChoice && isSelected && <XCircle size={14} className="text-coral shrink-0" />}
                  {!showFeedback && isSelected && <CheckCircle2 size={14} className="text-white shrink-0" />}
                </span>
              </button>
            );
          })}
        </div>
      ) : isChoiceQuestion ? (
        <div className="space-y-2 lg:space-y-2.5">
          {question.choices.map((c) => {
            const isSelected = selected.has(c.id);
            const isCorrectChoice = correctIds.has(c.id);
            let stateClass = "border-line/70 bg-sky-2 hover:bg-sky";
            let labelClass = "text-muted";
            if (showFeedback) {
              if (isCorrectChoice) stateClass = "border-teal bg-teal/10";
              else if (isSelected) stateClass = "border-coral bg-coral/10";
            } else if (isSelected) {
              stateClass = "border-teal bg-teal text-white";
              labelClass = "text-white/80";
            }
            return (
              <button
                key={c.id}
                type="button"
                disabled={readOnly || saving}
                onClick={() => toggleChoice(c.id)}
                className={`w-full text-left text-xs sm:text-sm lg:text-base font-bold px-3 py-2.5 sm:px-4 sm:py-3 rounded-xl border transition-colors flex items-center justify-between gap-2 ${stateClass} disabled:cursor-default`}
              >
                <span>
                  <span className={`${labelClass} mr-1.5`}>{c.choiceLabel}.</span>
                  {c.content}
                </span>
                {showFeedback && isCorrectChoice && <CheckCircle2 size={14} className="text-teal-deep shrink-0" />}
                {showFeedback && !isCorrectChoice && isSelected && <XCircle size={14} className="text-coral shrink-0" />}
                {!showFeedback && isSelected && <CheckCircle2 size={14} className="text-white shrink-0" />}
              </button>
            );
          })}
        </div>
      ) : question.questionType === "SPEAKING" ? (
        <div className="space-y-2">
          <div className="space-y-1">
            <p className="text-[10px] text-muted font-bold uppercase">{t("takeExercise.question.recordAnswerLabel")}</p>
            <input
              type="file"
              accept="audio/*"
              disabled={readOnly || saving}
              onClick={onFilePickerOpen}
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) onAudioUpload(file);
                e.target.value = "";
              }}
              className="text-xs font-bold text-ink file:mr-2 file:px-3 file:py-1.5 file:rounded-lg file:border-0 file:bg-teal file:text-white file:text-xs file:font-extrabold disabled:opacity-70"
            />
            {answer?.audioAnswerUrl && (
              // eslint-disable-next-line jsx-a11y/media-has-caption
              <audio controls src={answer.audioAnswerUrl} className="w-full mt-1" />
            )}
          </div>
          <p className="text-[10px] text-muted italic">{t("takeExercise.question.manualGradingNote")}</p>
        </div>
      ) : question.questionType === "WORD_BANK" && question.structuredContent?.blanks ? (
        <WordBankBlock
          content={question.questionContent}
          // Bổ sung 2026-09-17, đã xác nhận với người dùng — inputMode="text" (gõ tay) KHÔNG fallback
          // hộp từ = blanks (đáp án đúng) như dropdown vẫn làm: dropdown BẮT BUỘC chọn từ pool đó nên
          // không phải "lộ đáp án" (vẫn phải tự ghép đúng vị trí), còn ô gõ tay mà hiện sẵn đúng đáp án
          // thành hộp tham khảo thì mất hẳn ý nghĩa "tự nhớ từ" — chỉ hiện hộp từ khi GV chủ động điền
          // cột Transcript.
          wordPool={
            question.structuredContent.inputMode === "text"
              ? (question.structuredContent.wordBankOptions ?? [])
              : (question.structuredContent.wordBankOptions ?? question.structuredContent.blanks)
          }
          inputMode={question.structuredContent.inputMode}
          initialAnswer={answer?.structuredAnswer ?? undefined}
          readOnly={readOnly}
          saving={saving}
          onChange={onStructuredAnswer}
        />
      ) : question.questionType === "SENTENCE_BUILDING" && question.structuredContent?.chunks ? (
        <SentenceBuildingBlock chunkPool={question.structuredContent.chunks} readOnly={readOnly} saving={saving} onChange={onStructuredAnswer} />
      ) : (
        <div className="space-y-2">
          <textarea
            value={textValue ?? answer?.answerText ?? ""}
            onChange={(e) => onTextChange(e.target.value)}
            onBlur={onTextBlur}
            disabled={readOnly || saving}
            rows={isFillInBlank ? 1 : 3}
            placeholder={t("takeExercise.question.answerPlaceholder")}
            className="w-full bg-sky-2 border border-line/70 text-xs sm:text-sm lg:text-base p-3 sm:p-4 rounded-xl focus:outline-none disabled:opacity-70"
          />
          {isFillInBlank && showFeedback && (
            <div className={`flex items-center gap-1.5 text-xs font-bold ${notAnswered ? "text-coral" : answer?.isCorrect ? "text-teal-deep" : "text-coral"}`}>
              {notAnswered ? <HelpCircle size={14} /> : answer?.isCorrect ? <CheckCircle2 size={14} /> : <XCircle size={14} />}
              {notAnswered
                ? t("takeExercise.question.notAnsweredPrefix", { answer: formatCorrectAnswerText(answer?.correctAnswerText) })
                : answer?.isCorrect
                  ? t("takeExercise.question.correct")
                  : t("takeExercise.question.correctAnswerPrefix", { answer: formatCorrectAnswerText(answer?.correctAnswerText) })}
            </div>
          )}
        </div>
      )}

      {isChoiceQuestion && showFeedback && notAnswered && (
        <p className="text-xs font-bold text-coral flex items-center gap-1.5">
          <HelpCircle size={14} /> {t("takeExercise.question.notAnsweredChoice")}
        </p>
      )}

      {(question.questionType === "WORD_BANK" || question.questionType === "SENTENCE_BUILDING") && showFeedback && (
        <div className={`flex items-center gap-1.5 text-xs font-bold ${notAnswered ? "text-coral" : answer?.isCorrect ? "text-teal-deep" : "text-coral"}`}>
          {notAnswered ? <HelpCircle size={14} /> : answer?.isCorrect ? <CheckCircle2 size={14} /> : <XCircle size={14} />}
          {notAnswered
            ? t("takeExercise.question.notAnsweredPrefix", {
                answer:
                  (question.questionType === "WORD_BANK" ? answer?.correctStructuredContent?.blanks : answer?.correctStructuredContent?.chunks)?.join(
                    " — "
                  ) ?? "—"
              })
            : answer?.isCorrect
              ? t("takeExercise.question.correct")
              : t("takeExercise.question.correctAnswerPrefix", {
                  answer:
                    (question.questionType === "WORD_BANK" ? answer?.correctStructuredContent?.blanks : answer?.correctStructuredContent?.chunks)?.join(
                      " — "
                    ) ?? "—"
                })}
        </div>
      )}

      {answer?.explanation && showFeedback && (
        <p className="text-[11px] text-muted font-bold italic border-t border-line/50 pt-2">
          {t("takeExercise.question.explanationPrefix", { text: answer.explanation })}
        </p>
      )}

      {/*
       * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — điểm/nhận xét câu tự luận/nói
       * (ESSAY/SPEAKING) đã chấm (tay hoặc AI). KHÔNG phụ thuộc showFeedback (đó là cấu hình riêng cho
       * việc lộ ĐÁP ÁN ĐÚNG) — nhận xét bài của chính học sinh luôn hiện ngay khi có, để trả lời "vì
       * sao đạt/không đạt" thay vì chỉ thấy % tổng ở popup kết quả.
       */}
      {(answer?.gradingFeedback || answer?.gradingMarkedAnswer) && (
        <div className="text-sm font-bold p-3 rounded-xl border bg-sky-2 border-teal/20 space-y-1.5">
          <div className="flex items-center justify-between gap-2 flex-wrap">
            <span className="text-teal-deep uppercase text-base tracking-wide">{t("takeExercise.question.gradingFeedbackTitle")}</span>
            {/*
             * Bổ sung 2026-09-23 (đã xác nhận với người dùng) — đổi badge text "AI CHẤM · X/Y ĐIỂM" sang
             * ScoreSticker (% tròn, cùng style với badge "Viết"/"Nói" ở màn Video phản xạ) để đồng bộ
             * ngôn ngữ hình ảnh chấm điểm toàn Portal. gradingMaxScore > 0 mới tính được % — bài chưa có
             * điểm số (chỉ có gradingFeedback text, rubric cũ) thì vẫn giữ text nguồn chấm như cũ.
             */}
            {answer.gradingScore != null && answer.gradingMaxScore != null && answer.gradingMaxScore > 0 ? (
              <ScoreSticker
                icon={answer.gradingSource === "AI" ? <Sparkles size={10} /> : <GraduationCap size={10} />}
                label={answer.gradingSource === "AI" ? t("takeExercise.question.gradedByAiShort") : t("takeExercise.question.gradedByTeacherShort")}
                percent={Math.round((answer.gradingScore / answer.gradingMaxScore) * 100)}
                tone="pass"
                tiltClass="rotate-3"
              />
            ) : (
              <span className="text-[10px] text-muted font-black uppercase">
                {answer.gradingSource === "AI" ? t("takeExercise.question.gradedByAi") : t("takeExercise.question.gradedByTeacher")}
              </span>
            )}
          </div>
          {/*
           * V182 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-16, PILOT Khối 7 IELTS) —
           * rubric v3: hiện lại CHÍNH bài viết của học sinh với lỗi tô màu/gạch chân (gradingMarkedAnswer)
           * + % từng tiêu chí (gradingCriteriaScores), thay cho chỉ 1 đoạn feedback dài như trước. Rubric
           * cũ (chưa lên v3) không có 2 field này — vẫn hiện gradingFeedback dạng văn bản như cũ.
           */}
          {answer.gradingMarkedAnswer && (
            <div className="space-y-1.5">
              <p className="text-[11px] font-extrabold uppercase tracking-wide normal-case">
                {t("takeExercise.question.markedEssayTitle")}
              </p>
              <p className="normal-case whitespace-pre-line text-[13px] leading-relaxed font-medium text-ink">
                {renderMarkedEssay(answer.gradingMarkedAnswer, t)}
              </p>
              <MarkedEssayLegend t={t} />
            </div>
          )}
          {answer.gradingCriteriaScores && answer.gradingCriteriaScores.length > 0 && (
            <div className="flex flex-wrap gap-x-3 gap-y-1 text-[11px] font-bold normal-case text-teal-deep">
              {answer.gradingCriteriaScores.map((c) => (
                <span key={c.criterion}>
                  {c.criterion}: {c.percent}%
                </span>
              ))}
            </div>
          )}
          {answer.gradingFeedback && (
            <p className="font-medium text-ink normal-case whitespace-pre-line">{answer.gradingFeedback}</p>
          )}
          {/*
           * V196 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22) — Key Grammar (filter 2):
           * dải riêng, khác màu tuỳ Đạt/Chưa đạt/unparsed. status="unparsed" (hiếm — model bỏ sót dòng
           * kết luận) không hiện dải, chỉ ảnh hưởng nội bộ (không áp trần), tránh làm học sinh hoang mang.
           */}
          {answer.gradingKeyGrammar && answer.gradingKeyGrammar.status !== "unparsed" && (
            <div
              className={`rounded-lg border px-3 py-2 space-y-1 normal-case ${
                answer.gradingKeyGrammar.redoRequired
                  ? "bg-rose-50 border-rose-200 text-rose-900"
                  : "bg-emerald-50 border-emerald-200 text-emerald-900"
              }`}
            >
              <div className="flex items-center gap-2 flex-wrap text-[11px] font-black uppercase tracking-wide">
                <span>{t("takeExercise.question.keyGrammarTitle")}</span>
                <span>
                  {answer.gradingKeyGrammar.redoRequired
                    ? t("takeExercise.question.keyGrammarFail")
                    : t("takeExercise.question.keyGrammarPass")}
                </span>
                <span className="font-bold normal-case">
                  {t("takeExercise.question.keyGrammarCountSuffix", {
                    correct: answer.gradingKeyGrammar.correct,
                    attempts: answer.gradingKeyGrammar.attempts
                  })}
                </span>
              </div>
              {answer.gradingKeyGrammar.note && <p className="font-medium">{answer.gradingKeyGrammar.note}</p>}
              {answer.gradingKeyGrammar.redoRequired && (
                <p className="font-black">{t("takeExercise.question.keyGrammarRedoRequired")}</p>
              )}
            </div>
          )}
        </div>
      )}

      {answerLockedByRetake && <LockedAnswerBanner attemptsRemainingBeforeAnswer={attemptsRemainingBeforeAnswer} />}
    </div>
  );
}

/** UC-24/A4, UC-27/A2: nút "Xem đáp án" luôn hiện — chỉ khóa (disabled) khi backend chưa lộ đáp án vì còn lượt làm lại. */
function LockedAnswerBanner({ attemptsRemainingBeforeAnswer }: { attemptsRemainingBeforeAnswer: number | null }) {
  const { t } = useTranslation("portal-exercises");
  return (
    <div className="flex items-center justify-between gap-2 text-xs font-bold text-amber-800 bg-amber-50 border border-amber-100 rounded-xl px-3 py-2">
      <span>
        {attemptsRemainingBeforeAnswer != null && attemptsRemainingBeforeAnswer > 0
          ? t("takeExercise.locked.remaining", { count: attemptsRemainingBeforeAnswer })
          : t("takeExercise.locked.lastAttemptOnly")}
      </span>
      <button type="button" disabled className="flex items-center gap-1 text-[11px] font-extrabold text-amber-700 bg-amber-100 px-2.5 py-1 rounded-lg opacity-70 cursor-not-allowed">
        <Lock size={11} /> {t("takeExercise.locked.viewAnswer")}
      </button>
    </div>
  );
}

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — audio dùng chung cho MỌI câu hỏi
 * Nghe (skill=LISTENING, không riêng SPEAKING), tách khỏi nút "?" gợi ý (nút gợi ý giờ đặt ở hàng
 * tiêu đề câu hỏi, xem ListeningHintButton). Trước đây "Trắc nghiệm Voice"/"Nghe điền từ" đơn lẻ
 * (không nhóm) hoàn toàn không phát audio cho học sinh (audio chỉ được render trong nhánh SPEAKING)
 * — sửa cùng đợt vì cùng nằm trong luồng "Nghe" đang kiểm tra/xử lý, audio giờ luôn hiện 1 lần ở đây
 * bất kể questionType, KHÔNG lặp lại trong nhánh SPEAKING nữa.
 */
function ListeningAudioBlock({ question, onEnded }: { question: ExerciseQuestionResponse; onEnded: () => void }) {
  const { t } = useTranslation("portal-exercises");
  const audioRef = useRef<HTMLAudioElement>(null);
  useSeekLockedAudio(audioRef, question.audioUrl ?? undefined);
  if (question.skill !== "LISTENING" || !question.audioUrl) return null;
  return (
    <div className="space-y-1.5">
      <p className="text-[10px] text-muted font-bold uppercase">{t("takeExercise.listening.audioLabel")}</p>
      {/* eslint-disable-next-line jsx-a11y/media-has-caption */}
      <audio ref={audioRef} controls src={question.audioUrl} className="w-full" onEnded={onEnded} />
    </div>
  );
}

/**
 * Icon "?" gợi ý tapescript — khóa cho tới khi nghe hết audio đủ số lần cấu hình
 * (progress.hintUnlocked). Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06: đặt ở góc
 * phải hàng tiêu đề câu hỏi (song song với câu hỏi, không nằm dưới audio nữa), hiện dạng tooltip khi
 * di chuột vào thay vì nút bấm có nhãn chữ.
 */
/**
 * V144 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — THAY THẾ thiết kế V79 ban đầu) —
 * gợi ý CHỈ còn transcript (script hội thoại của audio) — bỏ hẳn phần lộ đáp án đúng/giải thích, người
 * dùng phản hồi thực tế: lộ đáp án trực tiếp là không ổn, học viên cần tự tư duy chọn đáp án sau khi
 * đọc lại lời thoại. Đổi tương tác từ HOVER (tooltip thoáng qua) sang BẤM MỞ/ĐÓNG popup thật — mỗi lần
 * mở gọi lại API (không cache kết quả cũ) để backend ghi đúng 1 "lượt xem" cho thống kê GV mỗi lần đóng
 * rồi mở lại (xem Javadoc ListeningHintService#getHint). Vẫn giữ nguyên luồng khoá theo playCount (nghe
 * đủ ngưỡng lần mới mở khoá được popup).
 *
 * V172 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-08) — thử đổi sang sidebar trượt từ
 * phải nhưng người dùng thấy không ổn, ĐÃ REVERT lại đúng popup này (chỉ giữ lại 2 tinh chỉnh trước đó:
 * rộng hơn `w-[28rem]` và chữ transcript in đậm).
 */
function ListeningHintButton({
  attemptId,
  questionId,
  progress,
  readOnly
}: {
  attemptId: number;
  questionId: number;
  progress: ListeningPlayProgressResponse | undefined;
  readOnly: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [hint, setHint] = useState<ListeningHintResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — panel gợi ý trước đây `position:
  // absolute` nằm bên trong vùng `overflow-y-auto` của modal làm bài (bao toàn bộ danh sách câu hỏi)
  // nên câu hỏi ở gần cuối bị cắt mất phần tooltip tràn ra ngoài đáy vùng cuộn, không cách nào xem
  // được. Render qua Portal ra document.body (mirror admin/src/components/ui/Select.tsx — cùng vấn đề
  // dropdown bị overflow cha cắt), tự tính toạ độ `position: fixed` theo bounding rect của nút "?",
  // tự lật lên trên khi không đủ chỗ bên dưới viewport.
  const [placement, setPlacement] = useState<{ top?: number; bottom?: number; right: number; flipped: boolean } | null>(null);
  const triggerRef = useRef<HTMLDivElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const { t } = useTranslation("portal-exercises");

  const threshold = progress?.hintUnlockThreshold ?? 3;
  const unlocked = progress?.hintUnlocked ?? false;
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — hiện thêm số lượt nghe hết CÒN
  // THIẾU (không chỉ báo cố định "cần đủ N lần") để học sinh biết mình đã nghe được bao nhiêu, còn
  // thiếu bao nhiêu lần nữa mới mở khóa.
  const playCount = progress?.playCount ?? 0;
  const remaining = Math.max(0, threshold - playCount);

  const updatePlacement = () => {
    const el = triggerRef.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    // Chỉ ước lượng chiều cao để QUYẾT ĐỊNH có lật hướng hay không — vị trí thật dùng CSS `bottom`
    // khi lật (neo theo mép trên của nút) nên không cần đúng tuyệt đối chiều cao nội dung thật.
    const estimatedHeight = unlocked ? 160 : 70;
    const spaceBelow = window.innerHeight - r.bottom;
    const flipped = spaceBelow < estimatedHeight && r.top > spaceBelow;
    const right = Math.max(8, window.innerWidth - r.right);
    setPlacement(flipped ? { bottom: window.innerHeight - r.top + 6, right, flipped } : { top: r.bottom + 6, right, flipped });
  };

  useLayoutEffect(() => {
    if (open) updatePlacement();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, unlocked]);

  useEffect(() => {
    if (!open) return;
    const reposition = () => updatePlacement();
    window.addEventListener("scroll", reposition, true);
    window.addEventListener("resize", reposition);
    return () => {
      window.removeEventListener("scroll", reposition, true);
      window.removeEventListener("resize", reposition);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  // Bấm ra ngoài popup (nút "?" HOẶC nội dung popup, cả 2 đều nằm ngoài luồng DOM của nhau vì popup
  // render qua Portal) thì tự đóng — thay cho onMouseLeave cũ (đã bỏ tương tác hover).
  useEffect(() => {
    if (!open) return;
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as Node;
      if (triggerRef.current?.contains(target) || panelRef.current?.contains(target)) return;
      setOpen(false);
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  const ensureLoaded = async () => {
    if (!unlocked || loading) return;
    setLoading(true);
    setError(null);
    try {
      setHint(await getListeningHint(attemptId, questionId));
    } catch (err) {
      setError(friendlyApiErrorMessage(err, t("takeExercise.hintError")));
    } finally {
      setLoading(false);
    }
  };

  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-08 — fix bug thật: trước đây CHỈ gọi
  // API lúc BẤM mở (handleToggle). Nếu học sinh mở popup dạng "chưa đủ lượt" rồi nghe xong đủ ngưỡng
  // NGAY TRONG LÚC popup còn đang mở (không đóng/mở lại) — progress.hintUnlocked lật true nhưng
  // transcript không tự tải, phải đóng ra bấm "?" lại mới thấy. Effect này theo dõi cả `open` lẫn
  // `unlocked`, tự gọi lại API ngay khi 1 trong 2 chuyển true trong lúc cái còn lại đã true sẵn —
  // thay hẳn cho lệnh gọi trong handleToggle (tránh gọi trùng 2 lần cho cùng 1 lần mở).
  useEffect(() => {
    if (open && unlocked) ensureLoaded();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, unlocked]);

  const handleToggle = () => {
    if (readOnly) return;
    setOpen((prev) => !prev);
  };

  return (
    <div ref={triggerRef} className="relative shrink-0">
      <button
        type="button"
        disabled={readOnly}
        onClick={handleToggle}
        aria-label={t("takeExercise.listening.hintAriaLabel")}
        className="w-5 h-5 rounded-full border border-teal/50 bg-teal/10 text-teal-deep flex items-center justify-center disabled:opacity-60"
      >
        <HelpCircle size={12} />
      </button>
      {open &&
        placement &&
        createPortal(
          !unlocked ? (
            // Tooltip dạng bong bóng thoại — mũi nhọn trỏ về phía nút "?" (lên nếu tooltip nằm dưới,
            // xuống nếu bị lật lên trên), màu theo đúng bảng màu hệ thống (teal-deep).
            <div ref={panelRef} style={{ position: "fixed", top: placement.top, bottom: placement.bottom, right: placement.right }} className="z-[200]">
              <div className={`absolute right-3 w-3 h-3 bg-teal-deep rotate-45 ${placement.flipped ? "-bottom-1.5" : "-top-1.5"}`} />
              <div className="relative bg-teal-deep text-white text-[11px] font-bold rounded-lg px-3 py-2 max-w-[220px] shadow-lg">
                {t("takeExercise.listening.locked", { playCount, threshold, remaining })}
              </div>
            </div>
          ) : (
            // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-08 — transcript dài (nhiều đoạn
            // hội thoại) trên điện thoại tràn quá chiều cao màn hình, không vuốt/cuộn được (panel trước
            // đây cao theo đúng nội dung, không giới hạn). Giới hạn chiều cao (~nửa màn hình trên mobile,
            // rộng hơn trên desktop) + tách riêng phần tiêu đề CỐ ĐỊNH (nút đóng luôn bấm được) khỏi phần
            // nội dung CUỘN ĐƯỢC RIÊNG bên trong.
            <div
              ref={panelRef}
              style={{ position: "fixed", top: placement.top, bottom: placement.bottom, right: placement.right }}
              className="z-[200] w-[28rem] max-w-[90vw] max-h-[50vh] sm:max-h-[70vh] flex flex-col text-left text-xs bg-white border border-line/60 rounded-xl shadow-lg overflow-hidden"
            >
              <div className="flex items-center justify-between gap-2 p-3 border-b border-line/40 shrink-0">
                <span className="font-bold text-[10px] uppercase tracking-wide text-muted">{t("takeExercise.listening.transcriptLabel")}</span>
                <button type="button" onClick={() => setOpen(false)} aria-label={t("takeExercise.closeAriaLabel")} className="text-muted hover:text-ink shrink-0">
                  <X size={13} />
                </button>
              </div>
              <div className="flex-1 overflow-y-auto p-3 space-y-1.5">
                {loading ? (
                  <p className="font-bold text-muted flex items-center gap-1.5">
                    <Loader2 size={12} className="animate-spin" /> {t("takeExercise.listening.loadingHint")}
                  </p>
                ) : error ? (
                  <p className="font-bold text-coral">{error}</p>
                ) : hint?.transcript ? (
                  <p className="text-sm font-bold text-ink whitespace-pre-line">{hint.transcript}</p>
                ) : null}
              </div>
            </div>
          ),
          document.body
        )}
    </div>
  );
}

/**
 * V78 — Điền từ - Hộp từ vựng: content chứa marker "___" theo đúng số chỗ trống, mỗi dropdown liệt kê từ CÒN LẠI (chưa chọn ở chỗ trống khác).
 *
 * inputMode="text" (bổ sung 2026-09-17, đã xác nhận với người dùng — DIEN_TU_DOAN_VAN ở BE): mỗi chỗ
 * trống đổi thành <input> gõ tay thay vì <select> — wordPool khi đó CHỈ hiện thành hộp từ vựng tham
 * khảo TĨNH phía trên đoạn văn (không phải lựa chọn để bấm), vẫn chấm case-insensitive+trim giống hệt
 * dropdown (xem ExerciseAttemptService#structuredAnswerMatches — không đổi gì ở BE). Vì gõ tay không có
 * "commit" rời rạc như chọn dropdown, chỉ gọi onChange (lưu) khi rời khỏi ô (blur) VÀ đã điền đủ mọi ô —
 * tránh lưu liên tục theo từng phím gõ dở dang.
 */
function WordBankBlock({
  content,
  wordPool,
  inputMode = "select",
  initialAnswer,
  readOnly,
  saving,
  onChange
}: {
  content: string;
  wordPool: string[];
  inputMode?: "select" | "text";
  initialAnswer: string[] | undefined;
  readOnly: boolean;
  saving: boolean;
  onChange: (values: string[]) => void;
}) {
  const { t } = useTranslation("portal-exercises");
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
    initialAnswer && initialAnswer.length === blankCount ? initialAnswer : new Array(blankCount).fill("")
  );

  const handleSelect = (idx: number, value: string) => {
    const next = selections.map((s, i) => (i === idx ? value : s));
    setSelections(next);
    if (next.every((s) => s)) onChange(next);
  };

  const handleTypeChange = (idx: number, value: string) => {
    setSelections((prev) => prev.map((s, i) => (i === idx ? value : s)));
  };

  const handleTypeBlur = () => {
    if (selections.every((s) => s.trim())) onChange(selections.map((s) => s.trim()));
  };

  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-09 — fix bug hiển thị thật: container
  // "flex flex-wrap" trước đây coi mỗi đoạn văn bản (<span>) là 1 flex item RIÊNG, khiến trình duyệt
  // xuống dòng theo TỪNG item thay vì cho chữ chảy liên tục như 1 đoạn văn bình thường (đoạn dài bị đẩy
  // xuống dòng riêng, dropdown lại đứng tách biệt dòng kế tiếp — không giống đề gốc). Đổi sang flow chữ
  // tự nhiên: <p> khối văn bản bình thường, <select>/<input> là inline-block xen giữa chữ, để trình
  // duyệt tự ngắt dòng theo TỪNG TỪ như văn bản thật (mirror ExerciseStudentPreviewModal#WordBankPreview).
  return (
    <div className="space-y-2">
      {title && <p className="text-sm sm:text-base lg:text-lg font-bold text-ink text-center">{title}</p>}
      <p className="text-sm sm:text-base lg:text-lg font-bold text-ink leading-8 lg:leading-10">
        {parts.map((part, idx) => (
          <React.Fragment key={idx}>
            {part}
            {idx < blankCount &&
              (inputMode === "text" ? (
                <input
                  type="text"
                  value={selections[idx]}
                  disabled={readOnly || saving}
                  onChange={(e) => handleTypeChange(idx, e.target.value)}
                  onBlur={handleTypeBlur}
                  className="bg-sky-2 border border-line/70 text-sm sm:text-sm lg:text-base font-bold px-2 py-1 mx-1 sm:px-3 sm:py-2 rounded-lg align-middle focus:outline-none disabled:opacity-70 w-28 sm:w-36"
                />
              ) : (
                <select
                  value={selections[idx]}
                  disabled={readOnly || saving}
                  onChange={(e) => handleSelect(idx, e.target.value)}
                  className="bg-sky-2 border border-line/70 text-sm sm:text-sm lg:text-base font-bold px-2 py-1 mx-1 sm:px-3 sm:py-2 rounded-lg align-middle focus:outline-none disabled:opacity-70"
                >
                  <option value="">{t("takeExercise.wordBank.choosePlaceholder")}</option>
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
      {inputMode === "text" && wordPool.length > 0 && (
        <div className="flex flex-wrap gap-1.5 bg-sky-2/60 border border-line/60 rounded-lg p-2.5">
          {wordPool.map((w, wIdx) => (
            <span key={`${w}-${wIdx}`} className="text-xs sm:text-sm font-bold text-ink bg-surface border border-line/70 rounded-md px-2 py-1">
              {w}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

/**
 * V78 — Sắp xếp câu: học sinh CHẠM từng khối theo thứ tự muốn chọn (thay cho kéo-thả vật lý — ổn định
 * hơn trên thiết bị cảm ứng, không cần thư viện DnD, cùng kết quả tự động chấm chính xác thứ tự).
 * Dùng index vào mảng đã xáo trộn (không dùng giá trị chuỗi) để xử lý đúng cả khi có khối trùng nội dung.
 * Giới hạn đã biết: không khôi phục lại lựa chọn cũ khi mở lại đề (initialAnswer không dùng để seed lại
 * usedIndices vì không có cách map ngược đáng tin cậy khi có khối trùng nội dung) — học sinh chọn lại
 * từ đầu, submit sẽ ghi đè đúng answer mới.
 */
function SentenceBuildingBlock({
  chunkPool,
  readOnly,
  saving,
  onChange
}: {
  chunkPool: string[];
  readOnly: boolean;
  saving: boolean;
  onChange: (values: string[]) => void;
}) {
  const { t } = useTranslation("portal-exercises");
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

  const addChunk = (idx: number) => {
    if (readOnly || saving) return;
    const next = [...usedIndices, idx];
    setUsedIndices(next);
    if (next.length === chunkPool.length) {
      onChange(next.map((i) => shuffled.find((s) => s.idx === i)?.text ?? ""));
    }
  };
  const removeChunk = (position: number) => {
    if (readOnly || saving) return;
    setUsedIndices((prev) => prev.filter((_, i) => i !== position));
  };

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-1.5 min-h-[38px] p-2 bg-sky-2 rounded-xl border border-dashed border-line/70">
        {built.length === 0 && <span className="text-[11px] text-muted italic px-1">{t("takeExercise.sentenceBuilding.instructions")}</span>}
        {built.map((text, position) => (
          <button
            key={position}
            type="button"
            disabled={readOnly || saving}
            onClick={() => removeChunk(position)}
            className="px-2.5 py-1 rounded-lg bg-teal/10 border border-teal text-xs font-bold text-teal-deep disabled:opacity-70"
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
            disabled={readOnly || saving}
            onClick={() => addChunk(s.idx)}
            className="px-2.5 py-1 rounded-lg bg-white border border-line/70 text-xs font-bold text-ink hover:bg-sky disabled:opacity-70"
          >
            {s.text}
          </button>
        ))}
      </div>
    </div>
  );
}

/**
 * V78 — "Đọc hiểu — lưới": 1 đoạn văn dùng chung + bảng N câu × 3 cột đáp án (radio từng dòng).
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — cũng dùng cho nhóm "1 audio nhiều
 * câu" của GV nước ngoài (ListeningGroupBuilder): phát audio dùng chung ở đầu khối, mỗi câu con rẽ
 * nhánh theo questionType — MULTIPLE_CHOICE giữ nguyên dãy nút chọn đáp án như cũ, FILL_IN_BLANK
 * thêm ô nhập text tự chấm, SPEAKING thêm control nộp audio (chấm tay).
 */
export function GridQuestionGroup({
  block,
  startNumber,
  answersByQuestion,
  readOnly,
  savingQuestionId,
  attemptsRemainingBeforeAnswer,
  onChoiceToggle,
  textDraft,
  onTextChange,
  onTextBlur,
  onAudioUpload,
  onFilePickerOpen,
  attemptId,
  listeningProgress,
  onListeningEnded
}: {
  block: Extract<RenderBlock, { type: "grid" }>;
  /** V150 — số thứ tự câu ĐẦU TIÊN của nhóm (dùng khi ghép nhiều Bài, xem QuestionBlock#displayNumber) — mặc định q.displayOrder như cũ khi không truyền. */
  startNumber?: number;
  answersByQuestion: Map<number, StudentAnswerResponse>;
  readOnly: boolean;
  savingQuestionId: number | null;
  attemptsRemainingBeforeAnswer: number | null;
  onChoiceToggle: (questionId: number, choiceIds: number[]) => void;
  textDraft: Record<number, string>;
  onTextChange: (questionId: number, value: string) => void;
  onTextBlur: (questionId: number) => void;
  onAudioUpload: (questionId: number, file: File) => void;
  onFilePickerOpen: () => void;
  attemptId: number | undefined;
  listeningProgress: Map<string, ListeningPlayProgressResponse>;
  onListeningEnded: (q: ExerciseQuestionResponse) => void;
}) {
  const { t } = useTranslation("portal-exercises");
  const audioRef = useRef<HTMLAudioElement>(null);
  useSeekLockedAudio(audioRef, block.audioUrl ?? undefined);
  // UC-24/A4, UC-27/A2: mọi câu trong 1 nhóm lưới đều thuộc cùng 1 lượt làm — chỉ cần 1 banner khóa chung.
  const anyLockedByRetake = block.questions.some((q) => {
    const a = answersByQuestion.get(q.questionId);
    return a != null && a.isAutoGradable && a.isCorrect != null && !isAnswerRevealed(a);
  });
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-18 — bài "Match the words with their
  // correct picture" (DIEN_TU_NHOM, mỗi câu 1 ảnh riêng + điền từ tự do) đúng hình thức sách in là
  // LƯỚI ảnh nhỏ gọn nhiều cột, ô điền ngay dưới mỗi ảnh — không phải liệt kê dọc từng câu 1 dòng như
  // layout mặc định bên dưới (ảnh bị phóng to 240px/dòng gây vỡ pixel với ảnh gốc nhỏ, lại dài lê thê
  // 10 dòng thay vì 1 khối gọn). Chỉ áp dụng khi CẢ NHÓM đều là FILL_IN_BLANK có ảnh (không đụng các
  // nhóm khác — nghe điền từ, đọc hiểu lưới trắc nghiệm... vẫn giữ nguyên layout liệt kê dọc cũ).
  const isPictureMatchGrid = block.questions.length >= 2 && block.questions.every((q) => q.imageUrl && q.questionType === "FILL_IN_BLANK");
  return (
    <div className="border border-line/60 rounded-[16px] p-4 sm:p-5 lg:p-6 space-y-3 lg:space-y-4">
      {/* V3 2026-09-04 — xem Javadoc parsePassageParagraphs: mỗi đoạn hiện tên nhân vật thành dòng tiêu
          đề in đậm riêng (khớp đúng hình thức đề giấy gốc), nội dung đoạn tự ngắt dòng theo khung. */}
      {block.referencePassage && (
        <div className="bg-sky-2 rounded-xl p-3 sm:p-4 space-y-2.5 sm:space-y-3">
          {parsePassageParagraphs(block.referencePassage).map((p, i) => (
            <div key={i}>
              {p.name && <p className="text-xs sm:text-sm lg:text-base font-black text-ink">{p.name}</p>}
              {p.isTitle ? (
                <p className="text-xs sm:text-sm lg:text-base font-black text-ink text-center">{p.content}</p>
              ) : (
                <p className="text-xs sm:text-sm lg:text-base text-ink whitespace-pre-line">{p.content}</p>
              )}
            </div>
          ))}
        </div>
      )}
      {/*
       * Bổ sung 2026-08-28 (đã xác nhận với người dùng) — hộp từ vựng THAM KHẢO tĩnh, khớp hình thức
       * "khung từ" trong đề giấy gốc (Ex.1 "Choose the correct word from the word box below") — chỉ để
       * học sinh nhìn tham khảo, KHÔNG bấm chọn được (mỗi câu bên dưới vẫn tự gõ đáp án + tự chấm riêng).
       * Dựng bằng <table> (viền nối liền giữa các ô) thay vì lưới ô rời để giống ĐÚNG bảng trong đề
       * giấy gốc, không chỉ là "các thẻ từ" rải rác.
       */}
      {block.wordBox && block.wordBox.length > 0 && (
        <table className="w-full border-collapse text-xs sm:text-sm lg:text-base text-ink">
          <tbody>
            {chunkArray(block.wordBox, 4).map((row, ri) => (
              <tr key={ri}>
                {row.map((w, ci) => (
                  <td key={ci} className="border border-line/60 text-center font-bold px-2 py-2 sm:px-3 sm:py-2.5">
                    {w}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {block.audioUrl && (
        <div className="flex items-center gap-2">
          {/* eslint-disable-next-line jsx-a11y/media-has-caption */}
          <audio ref={audioRef} controls src={block.audioUrl} className="w-full flex-1" onEnded={() => onListeningEnded(block.questions[0])} />
          {/*
           * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — fix bug thật: trước đây MỖI
           * câu trong nhóm đều có riêng 1 nút "?" — nhưng cả nhóm dùng CHUNG 1 audio + 1 bộ đếm lượt
           * nghe (listeningKeyOf theo groupKey) nên bấm câu nào cũng ra ĐÚNG 1 gợi ý y hệt nhau, thừa
           * và gây rối. Chỉ còn 1 nút "?" duy nhất đặt cạnh audio dùng chung cho cả nhóm.
           */}
          {attemptId != null && block.questions[0]?.skill === "LISTENING" && (
            <ListeningHintButton
              attemptId={attemptId}
              questionId={block.questions[0].questionId}
              progress={listeningProgress.get(listeningKeyOf(block.questions[0]))}
              readOnly={readOnly}
            />
          )}
        </div>
      )}
      {isPictureMatchGrid ? (
        <div className="grid grid-cols-3 sm:grid-cols-5 gap-3 sm:gap-4">
          {block.questions.map((q, qIndex) => {
            const answer = answersByQuestion.get(q.questionId);
            const showFeedback = answer != null && isAnswerRevealed(answer);
            const notAnswered = showFeedback && answer!.isAutoGradable && answer!.isCorrect == null;
            const saving = savingQuestionId === q.questionId;
            const rowReadOnly = readOnly || isLockedCarriedOver(answer);
            const displayNum = startNumber != null ? startNumber + qIndex : q.displayOrder;
            return (
              <div key={q.id} className="space-y-1.5">
                <img
                  src={q.imageUrl ?? undefined}
                  alt=""
                  className="w-full aspect-square object-contain rounded-xl border border-line/60 bg-white"
                />
                <p className="text-center text-[10px] sm:text-xs font-bold text-muted">{displayNum}</p>
                <input
                  value={textDraft[q.questionId] ?? answer?.answerText ?? ""}
                  onChange={(e) => onTextChange(q.questionId, e.target.value)}
                  onBlur={() => onTextBlur(q.questionId)}
                  disabled={rowReadOnly || saving}
                  placeholder={t("takeExercise.question.answerPlaceholder")}
                  className="w-full bg-sky-2 border border-line/70 text-xs sm:text-sm p-2 rounded-xl text-center focus:outline-none disabled:opacity-70"
                />
                {isLockedCarriedOver(answer) && <CarriedOverBadge />}
                {showFeedback && (
                  <div
                    className={`flex items-center justify-center gap-1 text-[10px] sm:text-xs font-bold text-center ${
                      notAnswered ? "text-coral" : answer?.isCorrect ? "text-teal-deep" : "text-coral"
                    }`}
                  >
                    {notAnswered ? <HelpCircle size={12} /> : answer?.isCorrect ? <CheckCircle2 size={12} /> : <XCircle size={12} />}
                    <span className="truncate">
                      {notAnswered || !answer?.isCorrect ? formatCorrectAnswerText(answer?.correctAnswerText) : t("takeExercise.question.correct")}
                    </span>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      ) : (
      <div className="divide-y divide-line/50">
        {block.questions.map((q, qIndex) => {
          const answer = answersByQuestion.get(q.questionId);
          const selected = new Set(answer?.selectedChoiceIds ?? []);
          const correctIds = new Set(answer?.correctChoiceIds ?? []);
          const showFeedback = answer != null && isAnswerRevealed(answer);
          // V169 — mirror QuestionBlock#notAnswered, xem Javadoc ở đó.
          const notAnswered = showFeedback && answer!.isAutoGradable && answer!.isCorrect == null;
          const saving = savingQuestionId === q.questionId;
          const isChoiceRow = CHOICE_TYPES.has(q.questionType) && q.choices.length > 0;
          const isFillInBlankRow = q.questionType === "FILL_IN_BLANK";
          const isSpeakingRow = q.questionType === "SPEAKING";
          // V177 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-15) — khoá TỪNG câu riêng
          // trong nhóm theo carry-forward, khác `readOnly` (áp cho cả nhóm/cả attempt) — 1 nhóm có thể
          // có câu đã đúng (khoá) lẫn câu sai (còn sửa được) cùng lúc.
          const rowReadOnly = readOnly || isLockedCarriedOver(answer);
          return (
            <div key={q.id} className="py-2.5 lg:py-3.5 space-y-2">
              <div className="flex items-center gap-2 flex-wrap">
                <span className="text-sm sm:text-sm lg:text-base font-bold text-ink flex-1 min-w-[160px]">
                  {startNumber != null ? startNumber + qIndex : q.displayOrder}. {q.questionContent}
                </span>
                {isLockedCarriedOver(answer) && <CarriedOverBadge />}
              </div>

              {/*
               * Bổ sung 2026-08-28 (đã xác nhận với người dùng) — ảnh minh họa RIÊNG từng câu trong 1
               * nhóm (VD FillInBlankGroupBuilder cho dạng "Complete each sentence with this/that/these/
               * those", mỗi câu 1 ảnh khác nhau) — trước đây chỉ câu đơn lẻ (không thuộc nhóm) mới hiện
               * ảnh, khối "grid" này thiếu hẳn nên ảnh bị lưu nhưng học sinh không thấy.
               */}
              {q.imageUrl && <QuestionImages imageUrl={q.imageUrl} className="w-full max-w-[240px] rounded-xl border border-line/60" />}

              {/*
               * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — fix bug thật: trước đây
               * mỗi đáp án chỉ hiện 1 ô vuông nhỏ ghi CHỮ CÁI (A/B/C/D), không hề hiện nội dung — hợp lý
               * với thiết kế gốc "Đọc hiểu — lưới" (đáp án A/B/C là 3 đoạn văn CỐ ĐỊNH đã hiện sẵn ở
               * trên), nhưng khối này bị TÁI SỬ DỤNG cho ListeningGroupBuilder (mỗi câu có bộ đáp án
               * VĂN BẢN RIÊNG, không cố định) — học sinh không thấy nội dung đáp án nào để chọn. Đổi
               * sang hiện đầy đủ nội dung/ảnh từng đáp án, mirror đúng khối isChoiceQuestion (câu đơn).
               */}
              {isChoiceRow && (
                <div className="space-y-1.5">
                  {q.choices.some((c) => c.imageUrl) ? (
                    // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-03 — cùng fix bug số cột
                    // cố định/ảnh vỡ như nhánh isChoiceQuestion (câu đơn) ở QuestionBlock, xem chú thích ở đó.
                    <div className={`grid gap-2 ${imageChoiceGridColsClass(q.choices.length)}`}>
                      {q.choices.map((c) => {
                        const isSelected = selected.has(c.id);
                        const isCorrectChoice = correctIds.has(c.id);
                        let stateClass = "border-line/70 bg-sky-2 hover:bg-sky";
                        let labelClass = "text-muted";
                        if (showFeedback) {
                          if (isCorrectChoice) stateClass = "border-teal bg-teal/10";
                          else if (isSelected) stateClass = "border-coral bg-coral/10";
                        } else if (isSelected) {
                          stateClass = "border-teal bg-teal text-white";
                          labelClass = "text-white/80";
                        }
                        return (
                          <button
                            key={c.id}
                            type="button"
                            disabled={rowReadOnly || saving}
                            onClick={() => onChoiceToggle(q.questionId, [c.id])}
                            className={`relative text-left rounded-xl border-2 overflow-hidden transition-colors ${stateClass} disabled:cursor-default`}
                          >
                            <div className="w-full aspect-[4/3] bg-white flex items-center justify-center overflow-hidden">
                              <img src={c.imageUrl ?? undefined} alt={c.content} className="max-w-full max-h-full object-contain" />
                            </div>
                            <span className="flex items-center justify-between gap-1 px-2 py-1.5 text-[11px] font-bold">
                              <span>
                                <span className={`${labelClass} mr-1`}>{c.choiceLabel}.</span>
                                {hasMeaningfulChoiceCaption(c.choiceLabel, c.content) && c.content}
                              </span>
                              {showFeedback && isCorrectChoice && <CheckCircle2 size={14} className="text-teal-deep shrink-0" />}
                              {showFeedback && !isCorrectChoice && isSelected && <XCircle size={14} className="text-coral shrink-0" />}
                              {!showFeedback && isSelected && <CheckCircle2 size={14} className="text-white shrink-0" />}
                            </span>
                          </button>
                        );
                      })}
                    </div>
                  ) : (
                    q.choices.map((c) => {
                      const isSelected = selected.has(c.id);
                      const isCorrectChoice = correctIds.has(c.id);
                      let stateClass = "border-line/70 bg-sky-2 hover:bg-sky";
                      let labelClass = "text-muted";
                      if (showFeedback) {
                        if (isCorrectChoice) stateClass = "border-teal bg-teal/10";
                        else if (isSelected) stateClass = "border-coral bg-coral/10";
                      } else if (isSelected) {
                        stateClass = "border-teal bg-teal text-white";
                        labelClass = "text-white/80";
                      }
                      return (
                        <button
                          key={c.id}
                          type="button"
                          disabled={rowReadOnly || saving}
                          onClick={() => onChoiceToggle(q.questionId, [c.id])}
                          className={`w-full text-left text-xs sm:text-sm font-bold px-3 py-2 rounded-xl border transition-colors flex items-center justify-between gap-2 ${stateClass} disabled:cursor-default`}
                        >
                          <span>
                            <span className={`${labelClass} mr-1.5`}>{c.choiceLabel}.</span>
                            {c.content}
                          </span>
                          {showFeedback && isCorrectChoice && <CheckCircle2 size={14} className="text-teal-deep shrink-0" />}
                          {showFeedback && !isCorrectChoice && isSelected && <XCircle size={14} className="text-coral shrink-0" />}
                          {!showFeedback && isSelected && <CheckCircle2 size={14} className="text-white shrink-0" />}
                        </button>
                      );
                    })
                  )}
                </div>
              )}

              {isChoiceRow && showFeedback && notAnswered && (
                <p className="text-xs font-bold text-coral flex items-center gap-1.5">
                  <HelpCircle size={14} /> {t("takeExercise.question.notAnsweredChoice")}
                </p>
              )}

              {isFillInBlankRow && (
                <div className="space-y-1">
                  <input
                    value={textDraft[q.questionId] ?? answer?.answerText ?? ""}
                    onChange={(e) => onTextChange(q.questionId, e.target.value)}
                    onBlur={() => onTextBlur(q.questionId)}
                    disabled={rowReadOnly || saving}
                    placeholder={t("takeExercise.question.answerPlaceholder")}
                    className="w-full bg-sky-2 border border-line/70 text-xs sm:text-sm lg:text-base p-2.5 sm:p-3 rounded-xl focus:outline-none disabled:opacity-70"
                  />
                  {showFeedback && (
                    <div className={`flex items-center gap-1.5 text-xs font-bold ${notAnswered ? "text-coral" : answer?.isCorrect ? "text-teal-deep" : "text-coral"}`}>
                      {notAnswered ? <HelpCircle size={14} /> : answer?.isCorrect ? <CheckCircle2 size={14} /> : <XCircle size={14} />}
                      {notAnswered
                        ? t("takeExercise.question.notAnsweredPrefix", { answer: formatCorrectAnswerText(answer?.correctAnswerText) })
                        : answer?.isCorrect
                          ? t("takeExercise.question.correct")
                          : t("takeExercise.question.correctAnswerPrefix", { answer: formatCorrectAnswerText(answer?.correctAnswerText) })}
                    </div>
                  )}
                </div>
              )}

              {isSpeakingRow && (
                <div className="space-y-1">
                  <input
                    type="file"
                    accept="audio/*"
                    disabled={rowReadOnly || saving}
                    onClick={onFilePickerOpen}
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (file) onAudioUpload(q.questionId, file);
                      e.target.value = "";
                    }}
                    className="text-xs font-bold text-ink file:mr-2 file:px-3 file:py-1.5 file:rounded-lg file:border-0 file:bg-teal file:text-white file:text-xs file:font-extrabold disabled:opacity-70"
                  />
                  {answer?.audioAnswerUrl && (
                    // eslint-disable-next-line jsx-a11y/media-has-caption
                    <audio controls src={answer.audioAnswerUrl} className="w-full mt-1" />
                  )}
                  <p className="text-[10px] text-muted italic">{t("takeExercise.question.manualGradingNote")}</p>
                </div>
              )}
            </div>
          );
        })}
      </div>
      )}

      {anyLockedByRetake && <LockedAnswerBanner attemptsRemainingBeforeAnswer={attemptsRemainingBeforeAnswer} />}
    </div>
  );
}
