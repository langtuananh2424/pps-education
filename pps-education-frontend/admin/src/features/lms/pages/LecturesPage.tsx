import React, { useEffect, useRef, useState } from "react";
import { BarChart3, Check, ClipboardList, Layers, Link2, MessageCircle, Music, Pencil, Plus, Users, Video, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { ClassResponse, CurriculumResponse, CurriculumSubjectResponse, ClassEnrollmentResponse, listClassEnrollments, listCurriculums, listCurriculumSubjects } from "@/features/academic/api";
import { useEligibleClasses } from "@/features/academic/hooks/useEligibleClasses";
import {
  AddReviewVideoRequest,
  ConnectionChoiceRequest,
  CreateReviewVideoSetRequest,
  ReviewVideoConnectionQuestionResponse,
  ReviewVideoQuestionResponse,
  ReviewVideoResponse,
  ReviewVideoSetResponse,
  ReviewVideoSetStatsResponse,
  ReviewVideoSetStatus,
  ReviewVideoSourceType,
  ReviewVideoTeacherType,
  ReviewVideoType,
  UpdateConnectionChoiceRequest,
  UpdateReviewVideoSetRequest,
  addReviewVideo,
  addReviewVideoConnectionQuestion,
  addReviewVideoQuestion,
  assignReviewVideoSetToClass,
  createReviewVideoSet,
  getReviewVideoSetStats,
  listReviewVideoConnectionQuestions,
  listReviewVideoQuestions,
  listReviewVideoSetAssignedClasses,
  listReviewVideoSets,
  listReviewVideos,
  unassignReviewVideoSetFromClass,
  updateReviewVideoConnectionQuestion,
  updateReviewVideoQuestion,
  updateReviewVideoSet,
  uploadMedia
} from "../api";
import Button from "@/components/ui/Button";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Modal from "@/components/ui/Modal";
import FileUploadField from "@/components/ui/FileUploadField";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import Select from "@/components/ui/Select";
import Pagination from "@/components/ui/Pagination";
import ReviewVideoQuestionImportPanel from "../components/ReviewVideoQuestionImportPanel";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

const CONTENT_KINDS = ["VIDEO", "AUDIO"] as const;
type ContentKind = (typeof CONTENT_KINDS)[number];

const VIDEO_TYPES: ReviewVideoType[] = ["CONNECTION", "REFLEX"];
const videoTypeIcons: Record<ReviewVideoType, React.ReactNode> = {
  CONNECTION: <Link2 className="w-4 h-4" />,
  REFLEX: <MessageCircle className="w-4 h-4" />
};

const TEACHER_TYPES: ReviewVideoTeacherType[] = ["VIETNAMESE", "FOREIGN"];
const SET_STATUSES: ReviewVideoSetStatus[] = ["DRAFT", "PUBLISHED", "ARCHIVED"];
const statusVariants: Record<ReviewVideoSetStatus, BadgeVariant> = { DRAFT: "neutral", PUBLISHED: "success", ARCHIVED: "danger" };

/**
 * Nhãn dịch qua i18next namespace "lms-review-video" (key `lectures.labels.*`) — dùng các hàm
 * `xLabel(t, value)` thay vì tra map tĩnh cũ, vì nhãn giờ phải đổi theo ngôn ngữ đang chọn.
 */
function contentKindLabel(t: (key: string) => string, kind: ContentKind): string {
  return t(`lectures.labels.contentKind.${kind}`);
}
function videoTypeLabel(t: (key: string) => string, type: ReviewVideoType): string {
  return t(`lectures.labels.videoType.${type}`);
}
function teacherTypeLabel(t: (key: string) => string, type: ReviewVideoTeacherType): string {
  return t(`lectures.labels.teacherType.${type}`);
}
function setStatusLabel(t: (key: string) => string, status: ReviewVideoSetStatus): string {
  return t(`lectures.labels.status.${status}`);
}

/** Đọc thời lượng (giây) của file video/audio NGAY TRÊN TRÌNH DUYỆT trước khi upload — API bắt buộc durationSeconds, backend không tự dò. */
function detectMediaDurationFromFile(file: File, kind: ContentKind, errorMessage: string): Promise<number> {
  return new Promise((resolve, reject) => {
    const el = document.createElement(kind === "VIDEO" ? "video" : "audio");
    el.preload = "metadata";
    const objectUrl = URL.createObjectURL(file);
    el.onloadedmetadata = () => {
      URL.revokeObjectURL(objectUrl);
      resolve(Math.round(el.duration));
    };
    el.onerror = () => {
      URL.revokeObjectURL(objectUrl);
      reject(new Error(errorMessage));
    };
    el.src = objectUrl;
  });
}

let youTubeIframeApiPromise: Promise<void> | null = null;

/** Nạp script YouTube IFrame API (chỉ 1 lần cho cả trang) — tái dùng cùng cơ chế loadYouTubeIframeApi đã có ở Portal Học sinh (LmsTab.tsx), viết riêng ở đây vì 2 app FE tách biệt, không import chéo được. */
function loadYouTubeIframeApi(): Promise<void> {
  const w = window as any;
  if (w.YT?.Player) return Promise.resolve();
  if (youTubeIframeApiPromise) return youTubeIframeApiPromise;
  youTubeIframeApiPromise = new Promise((resolve) => {
    const previousCallback = w.onYouTubeIframeAPIReady;
    w.onYouTubeIframeAPIReady = () => {
      previousCallback?.();
      resolve();
    };
    const script = document.createElement("script");
    script.src = "https://www.youtube.com/iframe_api";
    document.head.appendChild(script);
  });
  return youTubeIframeApiPromise;
}

function extractYouTubeVideoId(url: string): string | null {
  try {
    const parsed = new URL(url);
    const host = parsed.hostname.replace(/^www\./, "").replace(/^m\./, "");
    if (host === "youtu.be") return parsed.pathname.slice(1);
    if (host === "youtube.com") {
      if (parsed.pathname === "/watch") return parsed.searchParams.get("v");
      if (parsed.pathname.startsWith("/embed/")) return parsed.pathname.slice("/embed/".length);
      if (parsed.pathname.startsWith("/shorts/")) return parsed.pathname.slice("/shorts/".length);
    }
    return null;
  } catch {
    return null;
  }
}

/** Dò thời lượng video YouTube bằng 1 player ẩn (không autoplay, không hiển thị) — chỉ dùng onReady lấy getDuration() rồi hủy ngay. */
function useYouTubeDurationProbe() {
  const { t } = useTranslation("lms-review-video");
  const containerId = useRef(`yt-duration-probe-${Math.random().toString(36).slice(2)}`).current;
  const playerRef = useRef<any>(null);
  const [detecting, setDetecting] = useState(false);
  const [detectError, setDetectError] = useState<string | null>(null);

  const detect = (videoId: string, onDetected: (durationSeconds: number) => void) => {
    setDetecting(true);
    setDetectError(null);
    loadYouTubeIframeApi().then(() => {
      const YT = (window as any).YT;
      try {
        playerRef.current?.destroy?.();
      } catch {
        // ignore
      }
      playerRef.current = new YT.Player(containerId, {
        videoId,
        events: {
          onReady: (e: any) => {
            const duration = Math.round(e.target.getDuration());
            setDetecting(false);
            if (duration > 0) onDetected(duration);
            else setDetectError(t("lectures.mediaErrors.youtubeDurationFailed"));
          },
          onError: () => {
            setDetecting(false);
            setDetectError(t("lectures.mediaErrors.youtubeInvalidLink"));
          }
        }
      });
    });
  };

  return { containerId, detect, detecting, detectError };
}

interface ConnectionThresholdValue {
  completionThresholdPercent: string;
  requiredViewCount: string;
}

/**
 * UC-23a — chỉ có ý nghĩa với video CONNECTION. Bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-08-11 — completionThresholdPercent đổi ý nghĩa từ "ngưỡng % xem/lượt" sang "ngưỡng % pass"
 * điểm trắc nghiệm tổng (tổng câu đúng/tổng N câu hỏi, gộp mọi lượt) — xem đủ video luôn bắt buộc
 * 100% (cố định, không còn cấu hình được). N câu hỏi (soạn ở ConnectionQuizBuilder) được chia đều
 * ngẫu nhiên riêng theo từng học sinh qua đúng số "lượt đạt tối thiểu" bên dưới (M). Để trống dùng
 * mặc định BE (80%/1 lượt).
 */
function ConnectionThresholdFields({ value, onChange }: { value: ConnectionThresholdValue; onChange: (v: ConnectionThresholdValue) => void }) {
  const { t } = useTranslation("lms-review-video");
  return (
    <div className="grid grid-cols-2 gap-3 bg-sky-50/60 border border-sky-100 rounded-lg p-3">
      <div>
        <label className={labelClass}>{t("lectures.connectionThreshold.passPercentLabel")}</label>
        <input
          type="number"
          min={1}
          max={100}
          value={value.completionThresholdPercent}
          onChange={(e) => onChange({ ...value, completionThresholdPercent: e.target.value })}
          placeholder="80"
          className={inputClass}
        />
      </div>
      <div>
        <label className={labelClass}>{t("lectures.connectionThreshold.requiredViewsLabel")}</label>
        <input
          type="number"
          min={1}
          value={value.requiredViewCount}
          onChange={(e) => onChange({ ...value, requiredViewCount: e.target.value })}
          placeholder="1"
          className={inputClass}
        />
      </div>
    </div>
  );
}

export interface PendingReflexQuestion {
  timestampSeconds: string;
  prompt: string;
  maxRecordingSeconds: string;
  maxAttempts: string;
}

export const EMPTY_PENDING_QUESTION: PendingReflexQuestion = { timestampSeconds: "", prompt: "", maxRecordingSeconds: "60", maxAttempts: "" };

/**
 * UC-23b (V57) — soạn sẵn danh sách câu hỏi REFLEX ngay trong form tạo bộ video mới (không phải tạo
 * xong rồi mở lại "Video" → "Quản lý câu hỏi" mới thêm được câu đầu tiên, đã xác nhận với người dùng
 * 2026-07-29). Chỉ giữ ở state client — thật sự gọi addReviewVideoQuestion sau khi tạo xong Set+Video.
 * Export (V77): tái dùng ở CreateAndAssignExerciseModal.tsx cho nhánh "Video phản xạ" (Đề FOREIGN).
 */
export function ReflexQuestionsBuilder({ value, onChange }: { value: PendingReflexQuestion[]; onChange: (v: PendingReflexQuestion[]) => void }) {
  const { t } = useTranslation("lms-review-video");
  const [draft, setDraft] = useState<PendingReflexQuestion>(EMPTY_PENDING_QUESTION);
  const [draftError, setDraftError] = useState<string | null>(null);

  const handleAddDraft = () => {
    if (!draft.timestampSeconds || !draft.maxRecordingSeconds) {
      setDraftError(t("lectures.common.timestampAndDurationRequired"));
      return;
    }
    setDraftError(null);
    onChange([...value, draft]);
    setDraft(EMPTY_PENDING_QUESTION);
  };

  const handleRemove = (index: number) => onChange(value.filter((_, i) => i !== index));

  return (
    <div className="space-y-2 bg-amber-50/40 border border-amber-200 rounded-lg p-3">
      <label className={labelClass}>{t("lectures.reflexBuilder.label")}</label>

      {value.length > 0 && (
        <div className="space-y-1.5">
          {value.map((q, i) => (
            <div key={i} className="flex items-center justify-between gap-2 bg-white border border-slate-200 rounded-lg p-2 text-[11px]">
              <span className="text-slate-700">
                {t("lectures.reflexBuilder.itemSummary", {
                  index: i + 1,
                  minutes: Math.floor(Number(q.timestampSeconds) / 60),
                  seconds: String(Number(q.timestampSeconds) % 60).padStart(2, "0"),
                  maxRecording: q.maxRecordingSeconds,
                  attempts: q.maxAttempts
                    ? t("lectures.reflexBuilder.maxAttemptsLabel", { count: q.maxAttempts })
                    : t("lectures.reflexBuilder.unlimitedAttempts")
                })}
                {q.prompt && <span className="block text-slate-500 mt-0.5">{q.prompt}</span>}
              </span>
              <button type="button" onClick={() => handleRemove(i)} className="text-rose-500 hover:text-rose-700 shrink-0">
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          ))}
        </div>
      )}

      {draftError && <p className="text-[11px] text-rose-600 font-semibold">{draftError}</p>}

      <div className="grid grid-cols-3 gap-2">
        <input
          type="number"
          min={0}
          value={draft.timestampSeconds}
          onChange={(e) => setDraft({ ...draft, timestampSeconds: e.target.value })}
          placeholder={t("lectures.reflexBuilder.timestampPlaceholder")}
          className={inputClass}
        />
        <input
          type="number"
          min={1}
          value={draft.maxRecordingSeconds}
          onChange={(e) => setDraft({ ...draft, maxRecordingSeconds: e.target.value })}
          placeholder={t("lectures.reflexBuilder.maxRecordingPlaceholder")}
          className={inputClass}
        />
        <input
          type="number"
          min={1}
          value={draft.maxAttempts}
          onChange={(e) => setDraft({ ...draft, maxAttempts: e.target.value })}
          placeholder={t("lectures.reflexBuilder.maxAttemptsPlaceholder")}
          className={inputClass}
        />
      </div>
      <div className="flex gap-2">
        <input
          value={draft.prompt}
          onChange={(e) => setDraft({ ...draft, prompt: e.target.value })}
          placeholder={t("lectures.reflexBuilder.promptPlaceholder")}
          className={`${inputClass} flex-1`}
        />
        <Button type="button" variant="secondary" size="sm" onClick={handleAddDraft}>
          <Plus className="w-3.5 h-3.5" /> {t("lectures.reflexBuilder.addButton")}
        </Button>
      </div>
    </div>
  );
}

interface PendingConnectionChoice {
  content: string;
  isCorrect: boolean;
}

interface PendingConnectionQuestion {
  prompt: string;
  choices: PendingConnectionChoice[];
}

const EMPTY_CONNECTION_CHOICES: PendingConnectionChoice[] = [
  { content: "", isCorrect: true },
  { content: "", isCorrect: false }
];

/**
 * V76 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04) —
 * soạn sẵn danh sách câu hỏi trắc nghiệm CONNECTION ngay trong form tạo bộ
 * video mới, mirror ReflexQuestionsBuilder. Mỗi câu 2-5 lựa chọn tự chấm,
 * thêm/bớt lựa chọn ĐỘNG (khác QuestionEditorForm cố định 4 lựa chọn).
 */
function ConnectionQuizBuilder({ value, onChange }: { value: PendingConnectionQuestion[]; onChange: (v: PendingConnectionQuestion[]) => void }) {
  const { t } = useTranslation("lms-review-video");
  const [prompt, setPrompt] = useState("");
  const [choices, setChoices] = useState<PendingConnectionChoice[]>(EMPTY_CONNECTION_CHOICES);
  const [draftError, setDraftError] = useState<string | null>(null);

  const handleChoiceContentChange = (idx: number, content: string) =>
    setChoices((prev) => prev.map((c, i) => (i === idx ? { ...c, content } : c)));

  const handleSetCorrect = (idx: number) => setChoices((prev) => prev.map((c, i) => ({ ...c, isCorrect: i === idx })));

  const handleAddChoice = () => {
    if (choices.length >= 5) return;
    setChoices((prev) => [...prev, { content: "", isCorrect: false }]);
  };

  const handleRemoveChoice = (idx: number) => {
    if (choices.length <= 2) return;
    setChoices((prev) => {
      const next = prev.filter((_, i) => i !== idx);
      return next.some((c) => c.isCorrect) ? next : next.map((c, i) => (i === 0 ? { ...c, isCorrect: true } : c));
    });
  };

  const handleAddQuestion = () => {
    if (!prompt.trim()) {
      setDraftError(t("lectures.connectionBuilder.promptRequired"));
      return;
    }
    if (choices.some((c) => !c.content.trim())) {
      setDraftError(t("lectures.connectionBuilder.choicesRequired"));
      return;
    }
    setDraftError(null);
    onChange([...value, { prompt, choices }]);
    setPrompt("");
    setChoices(EMPTY_CONNECTION_CHOICES);
  };

  const handleRemoveQuestion = (index: number) => onChange(value.filter((_, i) => i !== index));

  return (
    <div className="space-y-2 bg-emerald-50/40 border border-emerald-200 rounded-lg p-3">
      <label className={labelClass}>{t("lectures.connectionBuilder.label")}</label>

      {value.length > 0 && (
        <div className="space-y-1.5">
          {value.map((q, i) => (
            <div key={i} className="flex items-center justify-between gap-2 bg-white border border-slate-200 rounded-lg p-2 text-[11px]">
              <span className="text-slate-700">
                {t("lectures.connectionBuilder.itemSummary", { index: i + 1, prompt: q.prompt, count: q.choices.length })}
              </span>
              <button type="button" onClick={() => handleRemoveQuestion(i)} className="text-rose-500 hover:text-rose-700 shrink-0">
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          ))}
        </div>
      )}

      {draftError && <p className="text-[11px] text-rose-600 font-semibold">{draftError}</p>}

      <input value={prompt} onChange={(e) => setPrompt(e.target.value)} placeholder={t("lectures.common.promptPlaceholder")} className={inputClass} />

      <div className="space-y-1.5">
        {choices.map((c, idx) => (
          <div key={idx} className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => handleSetCorrect(idx)}
              className={`w-6 h-6 rounded-full border flex items-center justify-center font-bold shrink-0 text-[10px] transition-all ${
                c.isCorrect ? "bg-emerald-500 border-emerald-500 text-white" : "bg-white border-slate-300 text-slate-400 hover:border-slate-400"
              }`}
            >
              {c.isCorrect ? <Check className="w-3.5 h-3.5 stroke-[3]" /> : String.fromCharCode(65 + idx)}
            </button>
            <input
              value={c.content}
              onChange={(e) => handleChoiceContentChange(idx, e.target.value)}
              placeholder={t("lectures.common.choicePlaceholder", { letter: String.fromCharCode(65 + idx) })}
              className={`flex-1 ${inputClass}`}
            />
            {choices.length > 2 && (
              <button type="button" onClick={() => handleRemoveChoice(idx)} className="text-rose-400 hover:text-rose-600 shrink-0">
                <X className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
        ))}
      </div>

      <div className="flex gap-2">
        {choices.length < 5 && (
          <Button type="button" variant="ghost" size="sm" onClick={handleAddChoice}>
            <Plus className="w-3.5 h-3.5" /> {t("lectures.common.addChoice")}
          </Button>
        )}
        <Button type="button" variant="secondary" size="sm" onClick={handleAddQuestion}>
          <Plus className="w-3.5 h-3.5" /> {t("lectures.common.addQuestion")}
        </Button>
      </div>
    </div>
  );
}

/** Chuyển PendingConnectionQuestion (state client) sang ConnectionChoiceRequest[] để gọi API — mirror pattern build choices của QuestionEditorForm. */
function toConnectionChoiceRequests(choices: PendingConnectionChoice[]): ConnectionChoiceRequest[] {
  return choices.map((c, i) => ({
    choiceLabel: String.fromCharCode(65 + i),
    content: c.content,
    isCorrect: c.isCorrect,
    displayOrder: i + 1
  }));
}

export interface ContentSourceValue {
  sourceType: ReviewVideoSourceType;
  fileUrl: string;
  fileSizeBytes?: number;
  durationSeconds: number | null;
}

/**
 * Nguồn nội dung dùng chung cho form Tạo bộ mới và modal Video — Video cho chọn Tải file lên hoặc
 * Dán link YouTube, Audio chỉ Tải file lên. Cả 3 nguồn đều bắt buộc tự dò durationSeconds trước khi
 * cho submit (API yêu cầu, BE không tự dò). Export (V77): tái dùng ở CreateAndAssignExerciseModal.tsx.
 */
export function ContentSourceField({ value, onChange }: { value: ContentSourceValue; onChange: (v: ContentSourceValue) => void }) {
  const { t } = useTranslation("lms-review-video");
  const contentKind: ContentKind = value.sourceType === "R2_AUDIO" ? "AUDIO" : "VIDEO";
  const videoSourceMode: "upload" | "youtube" = value.sourceType === "YOUTUBE_URL" ? "youtube" : "upload";
  const [youtubeUrlInput, setYoutubeUrlInput] = useState(value.sourceType === "YOUTUBE_URL" ? value.fileUrl : "");
  const { containerId, detect, detecting, detectError } = useYouTubeDurationProbe();
  /** Preview để GV tự kiểm tra link/file vừa nhập đúng nội dung trước khi lưu (không dùng player ẩn dò thời lượng ở trên). */
  const previewYoutubeVideoId = videoSourceMode === "youtube" ? extractYouTubeVideoId(youtubeUrlInput.trim()) : null;

  /**
   * FileUploadField gọi 3 callback RIÊNG BIỆT cho cùng 1 lần chọn file (onUpload dò duration ->
   * onChange url -> onFileSize bytes), mỗi callback thường chạy trước khi React kịp re-render nên
   * đóng gói `value` prop lúc đó vẫn còn CŨ — nếu mỗi callback tự merge "{...value, ...}" thì 2
   * update sau sẽ ghi đè mất update trước (VD durationSeconds vừa dò được bị mất khi onChange(url)
   * chạy tiếp). Dùng 1 ref làm nguồn merge duy nhất, luôn phản ánh giá trị mới nhất đã gửi lên, để
   * tránh mất dữ liệu do timing này.
   */
  const pendingRef = useRef<ContentSourceValue>(value);
  useEffect(() => {
    pendingRef.current = value;
  }, [value]);
  const updateValue = (patch: Partial<ContentSourceValue>) => {
    pendingRef.current = { ...pendingRef.current, ...patch };
    onChange(pendingRef.current);
  };

  const handleKindChange = (kind: ContentKind) => {
    updateValue({ sourceType: kind === "AUDIO" ? "R2_AUDIO" : "R2_VIDEO", fileUrl: "", fileSizeBytes: undefined, durationSeconds: null });
  };

  const handleDetectYouTubeDuration = () => {
    const videoId = extractYouTubeVideoId(youtubeUrlInput.trim());
    if (!videoId) {
      updateValue({ sourceType: "YOUTUBE_URL", fileUrl: youtubeUrlInput.trim(), durationSeconds: null });
      return;
    }
    updateValue({ sourceType: "YOUTUBE_URL", fileUrl: youtubeUrlInput.trim(), durationSeconds: null });
    detect(videoId, (durationSeconds) => updateValue({ sourceType: "YOUTUBE_URL", fileUrl: youtubeUrlInput.trim(), durationSeconds }));
  };

  return (
    <div className="space-y-2">
      <div id={containerId} style={{ position: "absolute", width: 1, height: 1, overflow: "hidden", left: -9999, top: -9999 }} />
      <div>
        <label className={labelClass}>{t("lectures.contentSource.kindLabel")}</label>
        <div className="flex gap-1.5">
          {CONTENT_KINDS.map((k) => (
            <button
              key={k}
              type="button"
              onClick={() => handleKindChange(k)}
              className={`flex-1 text-xs font-bold py-2.5 rounded-lg border ${
                contentKind === k ? "bg-brand-orange border-brand-orange text-white" : "bg-slate-50 border-slate-200 text-slate-500"
              }`}
            >
              {contentKindLabel(t, k)}
            </button>
          ))}
        </div>
      </div>

      <div>
        <label className={labelClass}>{contentKind === "VIDEO" ? t("lectures.contentSource.videoLabel") : t("lectures.contentSource.audioLabel")}</label>
        {contentKind === "VIDEO" && (
          <div className="flex gap-1.5 mb-1.5">
            <button
              type="button"
              onClick={() => updateValue({ sourceType: "R2_VIDEO", fileUrl: "", fileSizeBytes: undefined, durationSeconds: null })}
              className={`text-[10px] font-bold px-2.5 py-1 rounded-full ${videoSourceMode === "upload" ? "bg-brand-orange text-white" : "bg-slate-100 text-slate-500"}`}
            >
              {t("lectures.contentSource.uploadFile")}
            </button>
            <button
              type="button"
              onClick={() => updateValue({ sourceType: "YOUTUBE_URL", fileUrl: "", durationSeconds: null })}
              className={`text-[10px] font-bold px-2.5 py-1 rounded-full ${videoSourceMode === "youtube" ? "bg-brand-orange text-white" : "bg-slate-100 text-slate-500"}`}
            >
              {t("lectures.contentSource.pasteYoutube")}
            </button>
          </div>
        )}

        {contentKind === "VIDEO" && videoSourceMode === "youtube" ? (
          <div className="space-y-1.5">
            <div className="flex gap-1.5">
              <input
                value={youtubeUrlInput}
                onChange={(e) => setYoutubeUrlInput(e.target.value)}
                onBlur={handleDetectYouTubeDuration}
                className={inputClass}
                placeholder="https://www.youtube.com/watch?v=..."
              />
              <Button type="button" variant="secondary" size="sm" onClick={handleDetectYouTubeDuration} disabled={detecting || !youtubeUrlInput.trim()}>
                {detecting ? t("lectures.contentSource.detecting") : t("lectures.contentSource.detectDuration")}
              </Button>
            </div>
            {detectError && <p className="text-[10px] text-rose-600 font-semibold">{detectError}</p>}
            {previewYoutubeVideoId && (
              <div className="rounded-lg overflow-hidden border border-slate-200 bg-black aspect-video max-w-sm">
                <iframe
                  key={previewYoutubeVideoId}
                  src={`https://www.youtube.com/embed/${previewYoutubeVideoId}`}
                  title="Xem trước video"
                  className="w-full h-full"
                  allow="accelerometer; encrypted-media; gyroscope; picture-in-picture"
                  allowFullScreen
                />
              </div>
            )}
          </div>
        ) : (
          <FileUploadField
            value={value.fileUrl}
            onChange={(url) => updateValue({ fileUrl: url })}
            onUpload={async (file) => {
              const durationSeconds = await detectMediaDurationFromFile(file, contentKind, t("lectures.mediaErrors.durationDetectFailed"));
              updateValue({ durationSeconds });
              return uploadMedia(file, "REVIEW_VIDEO");
            }}
            onFileSize={(bytes) => updateValue({ fileSizeBytes: bytes })}
            accept={contentKind === "VIDEO" ? "video/*" : "audio/*"}
            placeholder={contentKind === "VIDEO" ? t("lectures.contentSource.chooseVideoFile") : t("lectures.contentSource.chooseAudioFile")}
          />
        )}
        {contentKind === "VIDEO" && videoSourceMode === "upload" && value.fileUrl && (
          <video src={value.fileUrl} controls className="mt-1.5 w-full max-w-sm max-h-56 rounded-lg border border-slate-200 bg-black" />
        )}
        {contentKind === "AUDIO" && value.fileUrl && <audio src={value.fileUrl} controls className="mt-1.5 w-full" />}
      </div>

      <p className="text-[10px] text-slate-400">
        {t("lectures.contentSource.durationDetectedLabel")}{" "}
        <span className="font-bold text-slate-600">
          {value.durationSeconds
            ? t("lectures.contentSource.durationValue", { seconds: value.durationSeconds, minutes: Math.round(value.durationSeconds / 60) })
            : t("lectures.contentSource.durationNone")}
        </span>
      </p>
    </div>
  );
}

/**
 * UC-23/UC-23a: Kho Video Ôn tập — V98 (bổ sung ngoài SDD gốc, đã xác
 * nhận với người dùng 2026-08-06): đổi bố cục giống hệt "Kho đề" (xem
 * ExerciseAssignPage.tsx) — danh sách bên trái lọc theo khung chương
 * trình + loại giáo viên (curriculum CHỈ dùng lọc/tìm kiếm), chi tiết 1
 * Bộ bên phải kèm "Quản lý lớp đã gán" (gán tường minh nhiều lớp, điều
 * kiện hiển thị DUY NHẤT cho học sinh — thay hẳn 2 chế độ "lớp cụ thể"/
 * "khung chương trình dùng chung" cũ).
 *
 * 2 lớp kiểm soát (giữ nguyên từ V63): vào được trang này cần quyền
 * lms.review-video.create/update/view (mặc định chỉ TEACHER); tạo/sửa 1
 * Bộ vẫn cần tài khoản dạy 1 lớp thuộc đúng khung chương trình đó
 * (requireAssignedTeacherForCurriculum); gán/gỡ lớp cần dạy ĐÚNG lớp đó
 * (requireAssignedTeacher) + quyền lms.review-video.assign.
 */
export default function LecturesPage() {
  const { t } = useTranslation("lms-review-video");
  const [curriculums, setCurriculums] = useState<CurriculumResponse[]>([]);
  const [curriculumFilter, setCurriculumFilter] = useState<number | null>(null);
  const [teacherTypeFilter, setTeacherTypeFilter] = useState<ReviewVideoTeacherType | null>(null);
  const [videoSets, setVideoSets] = useState<ReviewVideoSetResponse[]>([]);
  const [loadingSets, setLoadingSets] = useState(false);
  const [selectedSetId, setSelectedSetId] = useState<number | null>(null);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { message: toastMessage, showToast } = useToast();

  useEffect(() => {
    listCurriculums().then(setCurriculums).catch(() => undefined);
  }, []);

  const loadSets = () => {
    setLoadingSets(true);
    setError(null);
    listReviewVideoSets(curriculumFilter ?? undefined, teacherTypeFilter ?? undefined)
      .then((res) => {
        setVideoSets(res);
        if (!res.some((s) => s.id === selectedSetId)) setSelectedSetId(res[0]?.id ?? null);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("lectures.page.loadSetsFailed")))
      .finally(() => setLoadingSets(false));
  };

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(loadSets, [curriculumFilter, teacherTypeFilter]);

  const selectedSet = videoSets.find((s) => s.id === selectedSetId) ?? null;

  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  useEffect(() => setPage(0), [videoSets]);
  const pageSets = videoSets.slice(page * pageSize, (page + 1) * pageSize);

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4 flex items-start justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("lectures.page.title")}</h1>
          <p className="text-xs text-slate-500 mt-1">{t("lectures.page.subtitle")}</p>
        </div>
        <Button variant="primary" size="sm" onClick={() => setShowCreateForm(true)}>
          <Plus className="w-3.5 h-3.5" />
          {t("lectures.page.createButton")}
        </Button>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
        <div className="lg:col-span-5 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden flex flex-col">
          <div className="px-4 py-3 border-b border-slate-100 bg-slate-50 space-y-2">
            <Select
              value={curriculumFilter ?? ""}
              onChange={(e) => setCurriculumFilter(e.target.value ? Number(e.target.value) : null)}
              className={inputClass}
            >
              <option value="">{t("lectures.filter.allCurriculums")}</option>
              {curriculums.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.code} — {c.name}
                </option>
              ))}
            </Select>
            <Select
              value={teacherTypeFilter ?? ""}
              onChange={(e) => setTeacherTypeFilter(e.target.value ? (e.target.value as ReviewVideoTeacherType) : null)}
              className={inputClass}
            >
              <option value="">{t("lectures.filter.allTeacherTypes")}</option>
              {TEACHER_TYPES.map((tt) => (
                <option key={tt} value={tt}>
                  {teacherTypeLabel(t, tt)}
                </option>
              ))}
            </Select>
          </div>

          {loadingSets ? (
            <p className="text-xs text-slate-500 p-6 text-center">{t("lectures.common.loading")}</p>
          ) : videoSets.length === 0 ? (
            <div className="flex flex-col items-center justify-center p-12 text-center text-slate-400 space-y-3">
              <Layers className="w-12 h-12 text-slate-300" />
              <p className="text-xs text-slate-400">{curriculumFilter ? t("lectures.list.emptyInCurriculum") : t("lectures.list.empty")}</p>
            </div>
          ) : (
            <>
              <div className="divide-y divide-slate-100 overflow-y-auto">
                {pageSets.map((set) => (
                  <button
                    key={set.id}
                    onClick={() => setSelectedSetId(set.id)}
                    className={`w-full text-left px-4 py-3 hover:bg-slate-50/60 ${selectedSetId === set.id ? "bg-brand-red/5 border-l-2 border-brand-red" : ""}`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-xs font-bold text-slate-800 flex items-center gap-1.5">
                        <span className="text-brand-orange">{videoTypeIcons[set.videoType]}</span>
                        {set.title}
                      </p>
                      <Badge variant={statusVariants[set.status]}>{setStatusLabel(t, set.status)}</Badge>
                    </div>
                    <p className="text-[10px] text-slate-400 mt-0.5 font-mono">{set.code} · {set.curriculumCode}</p>
                    <p className="text-[10px] text-slate-400 mt-0.5">
                      {teacherTypeLabel(t, set.teacherType)} · {videoTypeLabel(t, set.videoType)}
                    </p>
                  </button>
                ))}
              </div>
              <Pagination
                page={page}
                pageSize={pageSize}
                totalElements={videoSets.length}
                itemLabel={t("lectures.list.itemLabel")}
                onPageChange={setPage}
                onPageSizeChange={(size) => {
                  setPageSize(size);
                  setPage(0);
                }}
              />
            </>
          )}
        </div>

        <div className="lg:col-span-7">
          {!selectedSet ? (
            <div className="bg-white rounded-xl border border-slate-200 shadow-soft flex flex-col items-center justify-center p-12 text-center text-slate-400 space-y-3">
              <ClipboardList className="w-12 h-12 text-slate-300" />
              <p className="text-xs text-slate-400">{t("lectures.detail.selectPrompt")}</p>
            </div>
          ) : (
            <SetDetailPanel
              set={selectedSet}
              showToast={showToast}
              onUpdated={(updated) => setVideoSets((prev) => prev.map((s) => (s.id === updated.id ? updated : s)))}
            />
          )}
        </div>
      </div>

      {showCreateForm && (
        <CreateSetModal
          curriculums={curriculums}
          onClose={() => setShowCreateForm(false)}
          onCreated={(set) => {
            setShowCreateForm(false);
            loadSets();
            setSelectedSetId(set.id);
            showToast(t("lectures.toast.setCreated"));
          }}
        />
      )}

      <Toast message={toastMessage} />
    </div>
  );
}

function SetDetailPanel({
  set,
  showToast,
  onUpdated
}: {
  set: ReviewVideoSetResponse;
  showToast: (msg: string) => void;
  onUpdated: (set: ReviewVideoSetResponse) => void;
}) {
  const { t } = useTranslation("lms-review-video");
  const [editingSet, setEditingSet] = useState(false);
  const [statsOpen, setStatsOpen] = useState(false);
  const [assignClassOpen, setAssignClassOpen] = useState(false);
  const [assignedClassCount, setAssignedClassCount] = useState<number | null>(null);

  const loadAssignedClassCount = () => {
    listReviewVideoSetAssignedClasses(set.id).then((cls) => setAssignedClassCount(cls.length)).catch(() => undefined);
  };

  useEffect(() => {
    loadAssignedClassCount();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [set.id]);

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
      <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 space-y-3">
        <div className="flex items-start justify-between gap-2">
          <div>
            <p className="text-sm font-bold text-slate-800">{set.title}</p>
            <p className="text-[10px] text-slate-400 font-mono mt-0.5">{set.code} · {set.curriculumCode}</p>
          </div>
          <Badge variant={statusVariants[set.status]}>{setStatusLabel(t, set.status)}</Badge>
        </div>
        <div className="flex items-center justify-between flex-wrap gap-2">
          <button
            onClick={() => setAssignClassOpen(true)}
            className="flex items-center gap-1.5 text-[11px] font-bold text-brand-red hover:underline"
          >
            <Users className="w-3.5 h-3.5" />
            {assignedClassCount == null ? t("lectures.detail.assignedClassesPending") : t("lectures.detail.assignedClassesCount", { count: assignedClassCount })}
            {t("lectures.detail.manageSuffix")}
          </button>
          <div className="flex items-center gap-2 flex-wrap">
            <Button size="sm" variant="secondary" onClick={() => setEditingSet(true)}>
              {t("lectures.detail.editButton")}
            </Button>
            <Button size="sm" variant="secondary" onClick={() => setStatsOpen(true)}>
              <BarChart3 className="w-3.5 h-3.5" /> {t("lectures.detail.statsButton")}
            </Button>
          </div>
        </div>
      </div>

      {/* Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12 — danh sách video hiện thẳng
          ra ngoài panel (trước đây ẩn sau nút "Video" + modal riêng), không cần bấm mở modal mới thấy. */}
      <VideoListSection set={set} />

      {editingSet && (
        <EditSetModal
          set={set}
          onClose={() => setEditingSet(false)}
          onSaved={(updated) => {
            setEditingSet(false);
            onUpdated(updated);
            showToast(t("lectures.toast.setSaved"));
          }}
        />
      )}

      {statsOpen && <StatsModal set={set} onClose={() => setStatsOpen(false)} />}

      {assignClassOpen && (
        <AssignClassModal
          setId={set.id}
          onClose={() => {
            setAssignClassOpen(false);
            loadAssignedClassCount();
          }}
        />
      )}
    </div>
  );
}

/** "1 Bộ video sẽ gán được cho nhiều lớp" (V98, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06) — mirror AssignClassModal của Kho đề, toggle gán/gỡ tức thì từng lớp. */
function AssignClassModal({ setId, onClose }: { setId: number; onClose: () => void }) {
  const { t } = useTranslation("lms-review-video");
  const { classes } = useEligibleClasses();
  const [assignedIds, setAssignedIds] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(true);
  const [pendingId, setPendingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listReviewVideoSetAssignedClasses(setId)
      .then((cls) => setAssignedIds(new Set(cls.map((c) => c.id))))
      .catch((err) => setError(err instanceof ApiError ? err.message : t("lectures.assignClass.loadFailed")))
      .finally(() => setLoading(false));
  }, [setId]);

  const toggle = async (classId: number) => {
    setError(null);
    setPendingId(classId);
    try {
      if (assignedIds.has(classId)) {
        await unassignReviewVideoSetFromClass(setId, classId);
        setAssignedIds((prev) => {
          const next = new Set(prev);
          next.delete(classId);
          return next;
        });
      } else {
        await assignReviewVideoSetToClass(setId, classId);
        setAssignedIds((prev) => new Set(prev).add(classId));
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("lectures.assignClass.updateFailed"));
    } finally {
      setPendingId(null);
    }
  };

  return (
    <Modal open onClose={onClose} title={t("lectures.assignClass.title")} size="md">
      <p className="text-[11px] text-slate-500 mb-3">{t("lectures.assignClass.description")}</p>
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg mb-3">{error}</div>}
      {loading ? (
        <p className="text-xs text-slate-500 p-3 text-center">{t("lectures.common.loading")}</p>
      ) : classes.length === 0 ? (
        <p className="text-xs text-slate-400 italic p-3 text-center">{t("lectures.assignClass.empty")}</p>
      ) : (
        <div className="border border-slate-200 rounded-lg divide-y divide-slate-100 max-h-72 overflow-y-auto">
          {classes.map((c) => (
            <label key={c.id} className="flex items-center gap-2 px-3 py-2 text-xs cursor-pointer hover:bg-slate-50">
              <input
                type="checkbox"
                checked={assignedIds.has(c.id)}
                disabled={pendingId === c.id}
                onChange={() => toggle(c.id)}
              />
              <span className="flex-1">{c.classCode} — {c.name}</span>
              {pendingId === c.id && <span className="text-[10px] text-slate-400">{t("lectures.common.saving")}</span>}
            </label>
          ))}
        </div>
      )}
      <div className="flex justify-end pt-3">
        <Button type="button" variant="secondary" size="sm" onClick={onClose}>
          <X className="w-3.5 h-3.5" />
          {t("lectures.common.close")}
        </Button>
      </div>
    </Modal>
  );
}

function CreateSetModal({
  curriculums,
  onClose,
  onCreated
}: {
  curriculums: CurriculumResponse[];
  onClose: () => void;
  onCreated: (set: ReviewVideoSetResponse) => void;
}) {
  const { t } = useTranslation("lms-review-video");
  const [subjects, setSubjects] = useState<CurriculumSubjectResponse[]>([]);
  const [form, setForm] = useState<{
    code: string;
    title: string;
    videoType: ReviewVideoType;
    curriculumId: number | "";
    teacherType: ReviewVideoTeacherType | "";
    subjectId: string;
    displayOrder: string;
  }>({
    code: "",
    title: "",
    videoType: "CONNECTION",
    curriculumId: curriculums[0]?.id ?? "",
    teacherType: "",
    subjectId: "",
    displayOrder: ""
  });
  const [content, setContent] = useState<ContentSourceValue>({ sourceType: "R2_VIDEO", fileUrl: "", durationSeconds: null });
  const [connSettings, setConnSettings] = useState<ConnectionThresholdValue>({ completionThresholdPercent: "", requiredViewCount: "" });
  const [pendingQuestions, setPendingQuestions] = useState<PendingReflexQuestion[]>([]);
  const [pendingConnectionQuestions, setPendingConnectionQuestions] = useState<PendingConnectionQuestion[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng — cho phép nhập câu hỏi hàng loạt bằng Excel ngay
  // lúc tạo bộ mới: video chưa có id thật lúc soạn form nên KHÔNG parse Excel phía client — chỉ chuyển
  // sang bước "import" (videoId thật) sau khi tạo xong bộ+video, dùng lại nguyên ReviewVideoQuestionImportPanel.
  const [questionSourceMode, setQuestionSourceMode] = useState<"manual" | "excel">("manual");
  const [stage, setStage] = useState<"form" | "import">("form");
  const [createdSetForImport, setCreatedSetForImport] = useState<ReviewVideoSetResponse | null>(null);
  const [createdVideoIdForImport, setCreatedVideoIdForImport] = useState<number | null>(null);

  useEffect(() => {
    if (form.curriculumId) listCurriculumSubjects(Number(form.curriculumId)).then(setSubjects).catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [form.curriculumId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.code.trim() || !form.title.trim() || !form.curriculumId || !form.teacherType || !content.fileUrl.trim() || !content.durationSeconds) {
      setError(t("lectures.createSet.errors.requiredFields"));
      return;
    }
    if (questionSourceMode === "manual" && form.videoType === "REFLEX" && pendingQuestions.length === 0) {
      setError(t("lectures.createSet.errors.reflexQuestionRequired"));
      return;
    }
    if (questionSourceMode === "manual" && form.videoType === "CONNECTION" && pendingConnectionQuestions.length === 0) {
      setError(t("lectures.createSet.errors.connectionQuestionRequired"));
      return;
    }
    setSubmitting(true);
    setError(null);
    let createdSetId: number | null = null;
    let createdVideoId: number | null = null;
    try {
      const setRequest: CreateReviewVideoSetRequest = {
        code: form.code.trim(),
        title: form.title.trim(),
        videoType: form.videoType,
        curriculumId: Number(form.curriculumId),
        teacherType: form.teacherType,
        subjectId: form.subjectId ? Number(form.subjectId) : undefined,
        displayOrder: form.displayOrder ? Number(form.displayOrder) : undefined
      };
      const set = await createReviewVideoSet(setRequest);
      createdSetId = set.id;
      const videoRequest: AddReviewVideoRequest = {
        sourceType: content.sourceType,
        title: form.title.trim(),
        fileUrl: content.fileUrl.trim(),
        fileSizeBytes: content.fileSizeBytes,
        durationSeconds: content.durationSeconds,
        displayOrder: 0,
        completionThresholdPercent: form.videoType === "CONNECTION" && connSettings.completionThresholdPercent ? Number(connSettings.completionThresholdPercent) : undefined,
        requiredViewCount: form.videoType === "CONNECTION" && connSettings.requiredViewCount ? Number(connSettings.requiredViewCount) : undefined
      };
      const video = await addReviewVideo(set.id, videoRequest);
      createdVideoId = video.id;
      if (questionSourceMode === "excel") {
        // Chuyển sang bước "import" ngay trong modal — video đã có id thật, dùng nguyên
        // ReviewVideoQuestionImportPanel để tải file/xem kết quả trước khi đóng modal.
        setCreatedSetForImport(set);
        setCreatedVideoIdForImport(video.id);
        setStage("import");
        setSubmitting(false);
        return;
      }
      if (form.videoType === "REFLEX") {
        for (let i = 0; i < pendingQuestions.length; i++) {
          const q = pendingQuestions[i];
          await addReviewVideoQuestion(video.id, {
            timestampSeconds: Number(q.timestampSeconds),
            prompt: q.prompt.trim() || undefined,
            maxRecordingSeconds: Number(q.maxRecordingSeconds),
            maxAttempts: q.maxAttempts ? Number(q.maxAttempts) : undefined,
            displayOrder: i
          });
        }
      }
      if (form.videoType === "CONNECTION") {
        for (let i = 0; i < pendingConnectionQuestions.length; i++) {
          const q = pendingConnectionQuestions[i];
          await addReviewVideoConnectionQuestion(video.id, {
            prompt: q.prompt.trim(),
            displayOrder: i,
            choices: toConnectionChoiceRequests(q.choices)
          });
        }
      }
      onCreated(set);
    } catch (err) {
      setError(
        createdVideoId
          ? t("lectures.createSet.errors.questionsFailedAfterVideo")
          : createdSetId
            ? t("lectures.createSet.errors.videoFailedAfterSet")
            : err instanceof ApiError
              ? err.message
              : t("lectures.createSet.errors.createFailed")
      );
    } finally {
      setSubmitting(false);
    }
  };

  if (stage === "import" && createdSetForImport && createdVideoIdForImport) {
    return (
      <Modal open onClose={() => onCreated(createdSetForImport)} title={t("lectures.createSet.importStage.title")} size="lg">
        <div className="space-y-4">
          <p className="text-xs text-slate-500">{t("lectures.createSet.importStage.description")}</p>
          <ReviewVideoQuestionImportPanel
            videoId={createdVideoIdForImport}
            videoType={form.videoType}
            onImported={() => undefined}
          />
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="primary" onClick={() => onCreated(createdSetForImport)}>
              {t("lectures.createSet.importStage.done")}
            </Button>
          </div>
        </div>
      </Modal>
    );
  }

  return (
    <Modal open onClose={onClose} title={t("lectures.createSet.title")} size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>{t("lectures.createSet.fields.code")}</label>
            <input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} placeholder={t("lectures.createSet.fields.codePlaceholder")} className={inputClass} required />
          </div>
          <div>
            <label className={labelClass}>{t("lectures.createSet.fields.videoType")}</label>
            <div className="flex gap-1.5">
              {VIDEO_TYPES.map((vt) => (
                <button
                  key={vt}
                  type="button"
                  onClick={() => setForm({ ...form, videoType: vt })}
                  className={`flex-1 text-xs font-bold py-2.5 rounded-lg border ${
                    form.videoType === vt ? "bg-brand-orange border-brand-orange text-white" : "bg-slate-50 border-slate-200 text-slate-500"
                  }`}
                >
                  {videoTypeLabel(t, vt)}
                </button>
              ))}
            </div>
          </div>
        </div>
        <div>
          <label className={labelClass}>{t("lectures.createSet.fields.title")}</label>
          <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder={t("lectures.createSet.fields.titlePlaceholder")} className={inputClass} required />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>{t("lectures.createSet.fields.curriculum")}</label>
            <Select
              value={form.curriculumId}
              onChange={(e) => setForm({ ...form, curriculumId: e.target.value ? Number(e.target.value) : "" })}
              className={inputClass}
            >
              <option value="">{t("lectures.createSet.fields.curriculumPlaceholder")}</option>
              {curriculums.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.code} — {c.name}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className={labelClass}>{t("lectures.createSet.fields.teacherType")}</label>
            <Select value={form.teacherType} onChange={(e) => setForm({ ...form, teacherType: e.target.value as ReviewVideoTeacherType | "" })} className={inputClass}>
              <option value="">{t("lectures.createSet.fields.teacherTypePlaceholder")}</option>
              {TEACHER_TYPES.map((tt) => (
                <option key={tt} value={tt}>
                  {teacherTypeLabel(t, tt)}
                </option>
              ))}
            </Select>
          </div>
        </div>
        <ContentSourceField value={content} onChange={setContent} />
        {form.videoType === "CONNECTION" && <ConnectionThresholdFields value={connSettings} onChange={setConnSettings} />}

        <div>
          <label className={labelClass}>{t("lectures.createSet.questionSource.label")}</label>
          <div className="flex items-center gap-1.5 bg-slate-100 p-1 rounded-lg w-fit">
            {(["manual", "excel"] as const).map((m) => (
              <button
                key={m}
                type="button"
                onClick={() => setQuestionSourceMode(m)}
                className={`text-[11px] font-bold px-3 py-1.5 rounded-md transition-all ${
                  questionSourceMode === m ? "bg-white text-brand-red shadow-xs" : "text-slate-500 hover:text-slate-700"
                }`}
              >
                {m === "manual" ? t("lectures.createSet.questionSource.manual") : t("lectures.createSet.questionSource.excel")}
              </button>
            ))}
          </div>
        </div>

        {questionSourceMode === "manual" && form.videoType === "CONNECTION" && (
          <ConnectionQuizBuilder value={pendingConnectionQuestions} onChange={setPendingConnectionQuestions} />
        )}
        {questionSourceMode === "manual" && form.videoType === "REFLEX" && (
          <ReflexQuestionsBuilder value={pendingQuestions} onChange={setPendingQuestions} />
        )}
        {questionSourceMode === "excel" && (
          <p className="text-[11px] text-slate-500 bg-slate-50 border border-slate-200 rounded-lg p-2.5">
            {t("lectures.createSet.excelHint")}
          </p>
        )}
        <div className="grid grid-cols-2 gap-3 items-end">
          <div>
            <label className={labelClass}>{t("lectures.createSet.fields.subject")}</label>
            <Select value={form.subjectId} onChange={(e) => setForm({ ...form, subjectId: e.target.value })} className={inputClass}>
              <option value="">{t("lectures.createSet.fields.subjectPlaceholder")}</option>
              {subjects.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className={labelClass}>{t("lectures.createSet.fields.displayOrder")}</label>
            <input type="number" value={form.displayOrder} onChange={(e) => setForm({ ...form, displayOrder: e.target.value })} className={inputClass} />
          </div>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t("lectures.common.cancel")}
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? t("lectures.common.saving") : t("lectures.createSet.submit")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function EditSetModal({
  set,
  onClose,
  onSaved
}: {
  set: ReviewVideoSetResponse;
  onClose: () => void;
  onSaved: (set: ReviewVideoSetResponse) => void;
}) {
  const { t } = useTranslation("lms-review-video");
  const [subjects, setSubjects] = useState<CurriculumSubjectResponse[]>([]);
  const [form, setForm] = useState({
    title: set.title,
    teacherType: set.teacherType,
    subjectId: set.subjectId ? String(set.subjectId) : "",
    displayOrder: String(set.displayOrder),
    status: set.status
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listCurriculumSubjects(set.curriculumId).then(setSubjects).catch(() => undefined);
  }, [set.curriculumId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.title.trim()) {
      setError(t("lectures.editSet.errors.titleRequired"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: UpdateReviewVideoSetRequest = {
        title: form.title.trim(),
        teacherType: form.teacherType,
        subjectId: form.subjectId ? Number(form.subjectId) : undefined,
        displayOrder: form.displayOrder ? Number(form.displayOrder) : undefined,
        status: form.status
      };
      const updated = await updateReviewVideoSet(set.id, request);
      onSaved(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("lectures.editSet.errors.updateFailed"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={t("lectures.editSet.title", { code: set.code })} size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        <div>
          <label className={labelClass}>{t("lectures.editSet.fields.title")}</label>
          <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} className={inputClass} required />
        </div>
        <div>
          <label className={labelClass}>{t("lectures.editSet.fields.teacherType")}</label>
          <Select value={form.teacherType} onChange={(e) => setForm({ ...form, teacherType: e.target.value as ReviewVideoTeacherType })} className={inputClass}>
            {TEACHER_TYPES.map((tt) => (
              <option key={tt} value={tt}>
                {teacherTypeLabel(t, tt)}
              </option>
            ))}
          </Select>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>{t("lectures.editSet.fields.subject")}</label>
            <Select value={form.subjectId} onChange={(e) => setForm({ ...form, subjectId: e.target.value })} className={inputClass}>
              <option value="">{t("lectures.editSet.fields.subjectPlaceholder")}</option>
              {subjects.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className={labelClass}>{t("lectures.editSet.fields.status")}</label>
            <Select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value as ReviewVideoSetStatus })} className={inputClass}>
              {SET_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {setStatusLabel(t, s)}
                </option>
              ))}
            </Select>
          </div>
        </div>
        <div>
          <label className={labelClass}>{t("lectures.editSet.fields.displayOrder")}</label>
          <input type="number" value={form.displayOrder} onChange={(e) => setForm({ ...form, displayOrder: e.target.value })} className={inputClass} />
        </div>
        <p className="text-[10px] text-slate-400 italic">
          {t("lectures.editSet.hint", { code: set.code, curriculumCode: set.curriculumCode })}
        </p>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t("lectures.common.cancel")}
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? t("lectures.common.saving") : t("lectures.editSet.submit")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

/** Preview video/audio ĐÃ LƯU trong card — trước đây chỉ hiện link text, GV phải tự copy mở tab khác mới xem lại được. */
function VideoPreviewCell({ sourceType, fileUrl, title }: { sourceType: ReviewVideoSourceType; fileUrl: string; title: string }) {
  if (sourceType === "YOUTUBE_URL") {
    const videoId = extractYouTubeVideoId(fileUrl);
    if (videoId) {
      return (
        <div className="rounded-lg overflow-hidden border border-slate-200 bg-black aspect-video max-w-xs">
          <iframe
            src={`https://www.youtube.com/embed/${videoId}`}
            title={title}
            className="w-full h-full"
            allow="accelerometer; encrypted-media; gyroscope; picture-in-picture"
            allowFullScreen
          />
        </div>
      );
    }
    return (
      <a href={fileUrl} target="_blank" rel="noreferrer" className="text-brand-orange break-all hover:underline block">
        {fileUrl}
      </a>
    );
  }
  if (sourceType === "R2_AUDIO") {
    return <audio src={fileUrl} controls className="w-full" />;
  }
  return <video src={fileUrl} controls className="w-full max-w-xs max-h-48 rounded-lg border border-slate-200 bg-black" />;
}

/** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12 — hiện danh sách video TRỰC TIẾP trong panel chi tiết bộ (trước đây là VideosModal, ẩn sau nút "Video" + modal riêng). */
function VideoListSection({ set }: { set: ReviewVideoSetResponse }) {
  const { t } = useTranslation("lms-review-video");
  const [videos, setVideos] = useState<ReviewVideoResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showAddForm, setShowAddForm] = useState(false);
  const [title, setTitle] = useState("");
  const [content, setContent] = useState<ContentSourceValue>({ sourceType: "R2_VIDEO", fileUrl: "", durationSeconds: null });
  const [connSettings, setConnSettings] = useState<ConnectionThresholdValue>({ completionThresholdPercent: "", requiredViewCount: "" });
  const [submitting, setSubmitting] = useState(false);
  const [expandedVideoId, setExpandedVideoId] = useState<number | null>(null);
  const { message: toastMessage, showToast } = useToast();

  const load = () => {
    setLoading(true);
    setError(null);
    listReviewVideos(set.id)
      .then(setVideos)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("lectures.videoList.errors.loadFailed")))
      .finally(() => setLoading(false));
  };

  useEffect(load, [set.id]);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !content.fileUrl.trim() || !content.durationSeconds) {
      setError(t("lectures.videoList.errors.requiredFields"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await addReviewVideo(set.id, {
        sourceType: content.sourceType,
        title: title.trim(),
        fileUrl: content.fileUrl.trim(),
        fileSizeBytes: content.fileSizeBytes,
        durationSeconds: content.durationSeconds,
        displayOrder: videos.length,
        completionThresholdPercent: set.videoType === "CONNECTION" && connSettings.completionThresholdPercent ? Number(connSettings.completionThresholdPercent) : undefined,
        requiredViewCount: set.videoType === "CONNECTION" && connSettings.requiredViewCount ? Number(connSettings.requiredViewCount) : undefined
      });
      setTitle("");
      setContent({ sourceType: "R2_VIDEO", fileUrl: "", durationSeconds: null });
      setConnSettings({ completionThresholdPercent: "", requiredViewCount: "" });
      setShowAddForm(false);
      load();
      showToast(t("lectures.toast.videoAdded"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("lectures.videoList.errors.addFailed"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="px-5 py-4 border-b border-slate-100 space-y-4">
      <p className="text-xs font-bold text-slate-700 uppercase tracking-wide">{t("lectures.videoList.heading")}</p>
      <div className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        {loading ? (
          <p className="text-xs text-slate-500">{t("lectures.common.loading")}</p>
        ) : videos.length === 0 ? (
          <p className="text-xs text-slate-400 italic">{t("lectures.videoList.empty")}</p>
        ) : (
          <div className="space-y-2">
            {videos.map((v) => (
              <div key={v.id} className="border border-slate-200 rounded-lg p-3 text-xs space-y-1">
                <div className="flex items-center justify-between gap-2">
                  <span className="font-bold text-slate-800 flex items-center gap-1.5">
                    {v.sourceType === "R2_AUDIO" ? <Music className="w-3.5 h-3.5" /> : <Video className="w-3.5 h-3.5" />}
                    {v.title}
                  </span>
                  <Badge variant="info">{v.sourceType}</Badge>
                </div>
                <VideoPreviewCell sourceType={v.sourceType} fileUrl={v.fileUrl} title={v.title} />
                <p className="text-slate-400">
                  {v.fileSizeBytes ? `${(v.fileSizeBytes / 1024 / 1024).toFixed(1)} MB` : t("lectures.videoList.sizeUnknown")} ·{" "}
                  {t("lectures.videoList.durationMinutes", { minutes: Math.round(v.durationSeconds / 60) })}
                  {set.videoType === "CONNECTION" &&
                    t("lectures.videoList.connectionMeta", { percent: v.completionThresholdPercent, count: v.requiredViewCount })}
                </p>
                {(set.videoType === "REFLEX" || set.videoType === "CONNECTION") && (
                  <button
                    type="button"
                    onClick={() => setExpandedVideoId(expandedVideoId === v.id ? null : v.id)}
                    className="text-brand-red font-bold hover:underline"
                  >
                    {expandedVideoId === v.id ? t("lectures.videoList.closeQuestions") : t("lectures.videoList.manageQuestions")}
                  </button>
                )}
                {set.videoType === "REFLEX" && expandedVideoId === v.id && <VideoQuestionsPanel videoId={v.id} />}
                {set.videoType === "CONNECTION" && expandedVideoId === v.id && <VideoMcqQuestionsPanel videoId={v.id} />}
              </div>
            ))}
          </div>
        )}

        {showAddForm ? (
          <form onSubmit={handleAdd} className="border-t border-slate-100 pt-4 space-y-3">
            <div>
              <label className={labelClass}>{t("lectures.videoList.fields.title")}</label>
              <input value={title} onChange={(e) => setTitle(e.target.value)} className={inputClass} required />
            </div>
            <ContentSourceField value={content} onChange={setContent} />
            {set.videoType === "CONNECTION" && <ConnectionThresholdFields value={connSettings} onChange={setConnSettings} />}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="secondary" onClick={() => setShowAddForm(false)}>
                {t("lectures.common.cancel")}
              </Button>
              <Button type="submit" variant="primary" disabled={submitting}>
                {submitting ? t("lectures.common.saving") : t("lectures.videoList.addButton")}
              </Button>
            </div>
          </form>
        ) : (
          <Button variant="secondary" onClick={() => setShowAddForm(true)}>
            <Plus className="w-4 h-4" /> {t("lectures.videoList.addButton")}
          </Button>
        )}
      </div>

      <Toast message={toastMessage} />
    </div>
  );
}

type ReflexQuestionFormValue = { timestampSeconds: string; prompt: string; maxRecordingSeconds: string; maxAttempts: string };

/** Dùng chung cho form "Thêm câu hỏi" VÀ form "Sửa câu hỏi" REFLEX — tránh lặp 2 lần y hệt nhau. */
function ReflexQuestionFields({ value, onChange }: { value: ReflexQuestionFormValue; onChange: (v: ReflexQuestionFormValue) => void }) {
  const { t } = useTranslation("lms-review-video");
  return (
    <>
      <div className="grid grid-cols-3 gap-2">
        <input
          type="number"
          min={0}
          value={value.timestampSeconds}
          onChange={(e) => onChange({ ...value, timestampSeconds: e.target.value })}
          placeholder={t("lectures.reflexFields.timestampPlaceholder")}
          className={inputClass}
        />
        <input
          type="number"
          min={1}
          value={value.maxRecordingSeconds}
          onChange={(e) => onChange({ ...value, maxRecordingSeconds: e.target.value })}
          placeholder={t("lectures.reflexFields.maxRecordingPlaceholder")}
          className={inputClass}
        />
        <input
          type="number"
          min={1}
          value={value.maxAttempts}
          onChange={(e) => onChange({ ...value, maxAttempts: e.target.value })}
          placeholder={t("lectures.reflexFields.maxAttemptsPlaceholder")}
          className={inputClass}
        />
      </div>
      <textarea
        value={value.prompt}
        onChange={(e) => onChange({ ...value, prompt: e.target.value })}
        placeholder={t("lectures.reflexFields.promptPlaceholder")}
        rows={2}
        className={inputClass}
      />
    </>
  );
}

/** UC-23b (V57): quản lý câu hỏi gắn mốc thời gian của 1 video REFLEX — mỗi câu tự có thời lượng ghi âm/số lần nộp lại riêng. */
function VideoQuestionsPanel({ videoId }: { videoId: number }) {
  const { t } = useTranslation("lms-review-video");
  const [questions, setQuestions] = useState<ReviewVideoQuestionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showAddForm, setShowAddForm] = useState(false);
  const [showImportPanel, setShowImportPanel] = useState(false);
  const [form, setForm] = useState<ReflexQuestionFormValue>({ timestampSeconds: "", prompt: "", maxRecordingSeconds: "60", maxAttempts: "" });
  const [submitting, setSubmitting] = useState(false);

  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12 — sửa câu hỏi đã có (trước đây chỉ thêm mới được).
  const [editingQuestionId, setEditingQuestionId] = useState<number | null>(null);
  const [editForm, setEditForm] = useState<ReflexQuestionFormValue>({ timestampSeconds: "", prompt: "", maxRecordingSeconds: "60", maxAttempts: "" });
  const [editSubmitting, setEditSubmitting] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  const load = () => {
    setLoading(true);
    setError(null);
    listReviewVideoQuestions(videoId)
      .then((qs) => setQuestions(qs.slice().sort((a, b) => a.displayOrder - b.displayOrder)))
      .catch((err) => setError(err instanceof ApiError ? err.message : t("lectures.common.loadQuestionsFailed")))
      .finally(() => setLoading(false));
  };

  useEffect(load, [videoId]);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.timestampSeconds || !form.maxRecordingSeconds) {
      setError(t("lectures.common.timestampAndDurationRequired"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await addReviewVideoQuestion(videoId, {
        timestampSeconds: Number(form.timestampSeconds),
        prompt: form.prompt.trim() || undefined,
        maxRecordingSeconds: Number(form.maxRecordingSeconds),
        maxAttempts: form.maxAttempts ? Number(form.maxAttempts) : undefined,
        displayOrder: questions.length
      });
      setForm({ timestampSeconds: "", prompt: "", maxRecordingSeconds: "60", maxAttempts: "" });
      setShowAddForm(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("lectures.common.addQuestionFailed"));
    } finally {
      setSubmitting(false);
    }
  };

  const startEdit = (q: ReviewVideoQuestionResponse) => {
    setShowAddForm(false);
    setEditError(null);
    setEditForm({
      timestampSeconds: String(q.timestampSeconds),
      prompt: q.prompt ?? "",
      maxRecordingSeconds: String(q.maxRecordingSeconds),
      maxAttempts: q.maxAttempts != null ? String(q.maxAttempts) : ""
    });
    setEditingQuestionId(q.id);
  };

  const handleUpdate = async (e: React.FormEvent, questionId: number, displayOrder: number) => {
    e.preventDefault();
    if (!editForm.timestampSeconds || !editForm.maxRecordingSeconds) {
      setEditError(t("lectures.common.timestampAndDurationRequired"));
      return;
    }
    setEditSubmitting(true);
    setEditError(null);
    try {
      await updateReviewVideoQuestion(questionId, {
        timestampSeconds: Number(editForm.timestampSeconds),
        prompt: editForm.prompt.trim() || undefined,
        maxRecordingSeconds: Number(editForm.maxRecordingSeconds),
        maxAttempts: editForm.maxAttempts ? Number(editForm.maxAttempts) : undefined,
        displayOrder
      });
      setEditingQuestionId(null);
      load();
    } catch (err) {
      setEditError(err instanceof ApiError ? err.message : t("lectures.common.editQuestionFailed"));
    } finally {
      setEditSubmitting(false);
    }
  };

  return (
    <div className="border-t border-slate-100 mt-2 pt-2 space-y-2">
      {error && <div className="text-[11px] text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{error}</div>}
      {loading ? (
        <p className="text-slate-400">{t("lectures.common.loadingQuestions")}</p>
      ) : questions.length === 0 ? (
        <p className="text-slate-400 italic">{t("lectures.reflexQuestions.empty")}</p>
      ) : (
        <div className="space-y-1.5">
          {questions.map((q, i) =>
            editingQuestionId === q.id ? (
              <form
                key={q.id}
                onSubmit={(e) => handleUpdate(e, q.id, q.displayOrder)}
                className="bg-white border border-brand-red/30 rounded-lg p-2.5 space-y-2"
              >
                {editError && <div className="text-[11px] text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{editError}</div>}
                <ReflexQuestionFields value={editForm} onChange={setEditForm} />
                <div className="flex justify-end gap-2">
                  <Button type="button" variant="secondary" size="sm" onClick={() => setEditingQuestionId(null)}>
                    {t("lectures.common.cancel")}
                  </Button>
                  <Button type="submit" variant="primary" size="sm" disabled={editSubmitting}>
                    {editSubmitting ? t("lectures.common.saving") : t("lectures.common.save")}
                  </Button>
                </div>
              </form>
            ) : (
              <div key={q.id} className="bg-slate-50 border border-slate-200 rounded-lg p-2">
                <div className="flex items-start justify-between gap-2">
                  <p className="font-bold text-slate-700">
                    {t("lectures.reflexQuestions.itemSummary", {
                      index: i + 1,
                      minutes: Math.floor(q.timestampSeconds / 60),
                      seconds: String(q.timestampSeconds % 60).padStart(2, "0"),
                      maxRecording: q.maxRecordingSeconds
                    })}{" "}
                    {q.maxAttempts != null
                      ? t("lectures.reflexQuestions.maxAttemptsLabel", { count: q.maxAttempts })
                      : t("lectures.reflexQuestions.unlimitedAttempts")}
                  </p>
                  <button
                    type="button"
                    onClick={() => startEdit(q)}
                    className="shrink-0 text-slate-400 hover:text-brand-red transition-colors"
                    title={t("lectures.common.editQuestionTooltip")}
                  >
                    <Pencil className="w-3.5 h-3.5" />
                  </button>
                </div>
                {q.prompt && <p className="text-slate-500 mt-0.5">{q.prompt}</p>}
              </div>
            )
          )}
        </div>
      )}

      {showAddForm ? (
        <form onSubmit={handleAdd} className="bg-white border border-slate-200 rounded-lg p-2.5 space-y-2">
          <ReflexQuestionFields value={form} onChange={setForm} />
          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" size="sm" onClick={() => setShowAddForm(false)}>
              {t("lectures.common.cancel")}
            </Button>
            <Button type="submit" variant="primary" size="sm" disabled={submitting}>
              {submitting ? t("lectures.common.saving") : t("lectures.common.addQuestion")}
            </Button>
          </div>
        </form>
      ) : showImportPanel ? (
        <div className="bg-white border border-slate-200 rounded-lg p-2.5 space-y-2">
          <div className="flex items-center justify-between">
            <p className="text-[11px] font-bold text-slate-600">{t("lectures.common.importFromExcelTitle")}</p>
            <Button type="button" variant="secondary" size="sm" onClick={() => setShowImportPanel(false)}>
              {t("lectures.common.close")}
            </Button>
          </div>
          <ReviewVideoQuestionImportPanel videoId={videoId} videoType="REFLEX" onImported={() => { setShowImportPanel(false); load(); }} />
        </div>
      ) : (
        <div className="flex gap-2">
          <Button type="button" variant="secondary" size="sm" onClick={() => setShowAddForm(true)}>
            <Plus className="w-3.5 h-3.5" /> {t("lectures.common.addQuestion")}
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={() => setShowImportPanel(true)}>
            {t("lectures.common.importExcel")}
          </Button>
        </div>
      )}
    </div>
  );
}

/** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12 — sửa 1 đáp án đã có (khác PendingConnectionChoice — LUÔN mang choiceId, không thêm/bớt số lượng được). */
interface EditingConnectionChoice {
  choiceId: number;
  content: string;
  isCorrect: boolean;
}

/**
 * V76 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04) —
 * quản lý câu hỏi trắc nghiệm tự chấm của 1 video CONNECTION, mirror
 * VideoQuestionsPanel (REFLEX). Bắt buộc ≥ 1 câu trước khi Publish được cả bộ.
 */
function VideoMcqQuestionsPanel({ videoId }: { videoId: number }) {
  const { t } = useTranslation("lms-review-video");
  const [questions, setQuestions] = useState<ReviewVideoConnectionQuestionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showAddForm, setShowAddForm] = useState(false);
  const [showImportPanel, setShowImportPanel] = useState(false);
  const [prompt, setPrompt] = useState("");
  const [choices, setChoices] = useState<PendingConnectionChoice[]>(EMPTY_CONNECTION_CHOICES);
  const [submitting, setSubmitting] = useState(false);

  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12 — sửa câu hỏi đã có (trước đây chỉ
  // thêm mới được). KHÔNG cho thêm/bớt số lượng đáp án khi sửa (xem UpdateConnectionChoiceRequest ở BE).
  const [editingQuestionId, setEditingQuestionId] = useState<number | null>(null);
  const [editPrompt, setEditPrompt] = useState("");
  const [editChoices, setEditChoices] = useState<EditingConnectionChoice[]>([]);
  const [editSubmitting, setEditSubmitting] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  const load = () => {
    setLoading(true);
    setError(null);
    listReviewVideoConnectionQuestions(videoId)
      .then((qs) => setQuestions(qs.slice().sort((a, b) => a.displayOrder - b.displayOrder)))
      .catch((err) => setError(err instanceof ApiError ? err.message : t("lectures.common.loadQuestionsFailed")))
      .finally(() => setLoading(false));
  };

  useEffect(load, [videoId]);

  const handleSetCorrect = (idx: number) => setChoices((prev) => prev.map((c, i) => ({ ...c, isCorrect: i === idx })));
  const handleAddChoice = () => choices.length < 5 && setChoices((prev) => [...prev, { content: "", isCorrect: false }]);
  const handleRemoveChoice = (idx: number) => {
    if (choices.length <= 2) return;
    setChoices((prev) => {
      const next = prev.filter((_, i) => i !== idx);
      return next.some((c) => c.isCorrect) ? next : next.map((c, i) => (i === 0 ? { ...c, isCorrect: true } : c));
    });
  };

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!prompt.trim() || choices.some((c) => !c.content.trim())) {
      setError(t("lectures.connectionQuestions.promptAndChoicesRequired"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await addReviewVideoConnectionQuestion(videoId, {
        prompt: prompt.trim(),
        displayOrder: questions.length,
        choices: toConnectionChoiceRequests(choices)
      });
      setPrompt("");
      setChoices(EMPTY_CONNECTION_CHOICES);
      setShowAddForm(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("lectures.common.addQuestionFailed"));
    } finally {
      setSubmitting(false);
    }
  };

  const startEdit = (q: ReviewVideoConnectionQuestionResponse) => {
    setShowAddForm(false);
    setEditError(null);
    setEditPrompt(q.prompt);
    setEditChoices(q.choices.map((c) => ({ choiceId: c.id, content: c.content, isCorrect: !!c.isCorrect })));
    setEditingQuestionId(q.id);
  };

  const handleUpdate = async (e: React.FormEvent, questionId: number, displayOrder: number) => {
    e.preventDefault();
    if (!editPrompt.trim() || editChoices.some((c) => !c.content.trim())) {
      setEditError(t("lectures.connectionQuestions.promptAndAnswersRequired"));
      return;
    }
    setEditSubmitting(true);
    setEditError(null);
    try {
      const request: { prompt: string; displayOrder: number; choices: UpdateConnectionChoiceRequest[] } = {
        prompt: editPrompt.trim(),
        displayOrder,
        choices: editChoices.map((c) => ({ choiceId: c.choiceId, content: c.content.trim(), isCorrect: c.isCorrect }))
      };
      await updateReviewVideoConnectionQuestion(questionId, request);
      setEditingQuestionId(null);
      load();
    } catch (err) {
      setEditError(err instanceof ApiError ? err.message : t("lectures.common.editQuestionFailed"));
    } finally {
      setEditSubmitting(false);
    }
  };

  return (
    <div className="border-t border-slate-100 mt-2 pt-2 space-y-2">
      {error && <div className="text-[11px] text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{error}</div>}
      {loading ? (
        <p className="text-slate-400">{t("lectures.common.loadingQuestions")}</p>
      ) : questions.length === 0 ? (
        <p className="text-slate-400 italic">{t("lectures.connectionQuestions.empty")}</p>
      ) : (
        <div className="space-y-1.5">
          {questions.map((q, i) =>
            editingQuestionId === q.id ? (
              <form
                key={q.id}
                onSubmit={(e) => handleUpdate(e, q.id, q.displayOrder)}
                className="bg-white border border-brand-red/30 rounded-lg p-2.5 space-y-2"
              >
                {editError && <div className="text-[11px] text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{editError}</div>}
                <input value={editPrompt} onChange={(e) => setEditPrompt(e.target.value)} placeholder={t("lectures.common.promptPlaceholder")} className={inputClass} />
                <div className="space-y-1.5">
                  {editChoices.map((c, idx) => (
                    <div key={c.choiceId} className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={() => setEditChoices((prev) => prev.map((x, i) => ({ ...x, isCorrect: i === idx })))}
                        className={`w-6 h-6 rounded-full border flex items-center justify-center font-bold shrink-0 text-[10px] transition-all ${
                          c.isCorrect ? "bg-emerald-500 border-emerald-500 text-white" : "bg-white border-slate-300 text-slate-400 hover:border-slate-400"
                        }`}
                      >
                        {c.isCorrect ? <Check className="w-3.5 h-3.5 stroke-[3]" /> : String.fromCharCode(65 + idx)}
                      </button>
                      <input
                        value={c.content}
                        onChange={(e) => setEditChoices((prev) => prev.map((x, i) => (i === idx ? { ...x, content: e.target.value } : x)))}
                        placeholder={t("lectures.common.choicePlaceholder", { letter: String.fromCharCode(65 + idx) })}
                        className={`flex-1 ${inputClass}`}
                      />
                    </div>
                  ))}
                </div>
                <p className="text-[10px] text-slate-400 italic">{t("lectures.connectionQuestions.choiceUnchangeableHint")}</p>
                <div className="flex justify-end gap-2">
                  <Button type="button" variant="secondary" size="sm" onClick={() => setEditingQuestionId(null)}>
                    {t("lectures.common.cancel")}
                  </Button>
                  <Button type="submit" variant="primary" size="sm" disabled={editSubmitting}>
                    {editSubmitting ? t("lectures.common.saving") : t("lectures.common.save")}
                  </Button>
                </div>
              </form>
            ) : (
              <div key={q.id} className="bg-slate-50 border border-slate-200 rounded-lg p-2">
                <div className="flex items-start justify-between gap-2">
                  <p className="font-bold text-slate-700">{t("lectures.connectionQuestions.itemSummary", { index: i + 1, prompt: q.prompt })}</p>
                  <button
                    type="button"
                    onClick={() => startEdit(q)}
                    className="shrink-0 text-slate-400 hover:text-brand-red transition-colors"
                    title={t("lectures.common.editQuestionTooltip")}
                  >
                    <Pencil className="w-3.5 h-3.5" />
                  </button>
                </div>
                <div className="mt-1 space-y-0.5">
                  {q.choices.map((c) => (
                    <p key={c.id} className={c.isCorrect ? "text-emerald-600 font-semibold" : "text-slate-500"}>
                      {c.choiceLabel}. {c.content} {c.isCorrect ? "✓" : ""}
                    </p>
                  ))}
                </div>
              </div>
            )
          )}
        </div>
      )}

      {showAddForm ? (
        <form onSubmit={handleAdd} className="bg-white border border-slate-200 rounded-lg p-2.5 space-y-2">
          <input value={prompt} onChange={(e) => setPrompt(e.target.value)} placeholder={t("lectures.common.promptPlaceholder")} className={inputClass} />
          <div className="space-y-1.5">
            {choices.map((c, idx) => (
              <div key={idx} className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => handleSetCorrect(idx)}
                  className={`w-6 h-6 rounded-full border flex items-center justify-center font-bold shrink-0 text-[10px] transition-all ${
                    c.isCorrect ? "bg-emerald-500 border-emerald-500 text-white" : "bg-white border-slate-300 text-slate-400 hover:border-slate-400"
                  }`}
                >
                  {c.isCorrect ? <Check className="w-3.5 h-3.5 stroke-[3]" /> : String.fromCharCode(65 + idx)}
                </button>
                <input
                  value={c.content}
                  onChange={(e) => setChoices((prev) => prev.map((x, i) => (i === idx ? { ...x, content: e.target.value } : x)))}
                  placeholder={t("lectures.common.choicePlaceholder", { letter: String.fromCharCode(65 + idx) })}
                  className={`flex-1 ${inputClass}`}
                />
                {choices.length > 2 && (
                  <button type="button" onClick={() => handleRemoveChoice(idx)} className="text-rose-400 hover:text-rose-600 shrink-0">
                    <X className="w-3.5 h-3.5" />
                  </button>
                )}
              </div>
            ))}
          </div>
          <div className="flex justify-between items-center gap-2">
            {choices.length < 5 ? (
              <Button type="button" variant="ghost" size="sm" onClick={handleAddChoice}>
                <Plus className="w-3.5 h-3.5" /> {t("lectures.common.addChoice")}
              </Button>
            ) : <span />}
            <div className="flex gap-2">
              <Button type="button" variant="secondary" size="sm" onClick={() => setShowAddForm(false)}>
                {t("lectures.common.cancel")}
              </Button>
              <Button type="submit" variant="primary" size="sm" disabled={submitting}>
                {submitting ? t("lectures.common.saving") : t("lectures.common.addQuestion")}
              </Button>
            </div>
          </div>
        </form>
      ) : showImportPanel ? (
        <div className="bg-white border border-slate-200 rounded-lg p-2.5 space-y-2">
          <div className="flex items-center justify-between">
            <p className="text-[11px] font-bold text-slate-600">{t("lectures.common.importFromExcelTitle")}</p>
            <Button type="button" variant="secondary" size="sm" onClick={() => setShowImportPanel(false)}>
              {t("lectures.common.close")}
            </Button>
          </div>
          <ReviewVideoQuestionImportPanel videoId={videoId} videoType="CONNECTION" onImported={() => { setShowImportPanel(false); load(); }} />
        </div>
      ) : (
        <div className="flex gap-2">
          <Button type="button" variant="secondary" size="sm" onClick={() => setShowAddForm(true)}>
            <Plus className="w-3.5 h-3.5" /> {t("lectures.common.addQuestion")}
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={() => setShowImportPanel(true)}>
            {t("lectures.common.importExcel")}
          </Button>
        </div>
      )}
    </div>
  );
}

/** UC-23a Main Flow bước 4: ma trận học sinh × video — ghép studentId trong StatsCell với tên/mã học sinh qua listClassEnrollments (BE không trả tên, chỉ trả id). */
/** V98: bộ không còn "classId cố định" — luôn chọn 1 trong các lớp ĐÃ GÁN tường minh cho bộ (xem AssignClassModal), tải qua listReviewVideoSetAssignedClasses. */
function StatsModal({
  set,
  onClose
}: {
  set: ReviewVideoSetResponse;
  onClose: () => void;
}) {
  const { t } = useTranslation("lms-review-video");
  const [assignedClasses, setAssignedClasses] = useState<ClassResponse[]>([]);
  const [classId, setClassId] = useState<number | null>(null);
  const [stats, setStats] = useState<ReviewVideoSetStatsResponse | null>(null);
  const [enrollments, setEnrollments] = useState<ClassEnrollmentResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listReviewVideoSetAssignedClasses(set.id)
      .then((cls) => {
        setAssignedClasses(cls);
        setClassId(cls[0]?.id ?? null);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("lectures.stats.loadAssignedFailed")));
  }, [set.id]);

  useEffect(() => {
    if (!classId) return;
    setLoading(true);
    setError(null);
    Promise.all([getReviewVideoSetStats(set.id, classId), listClassEnrollments(classId)])
      .then(([statsRes, enrollmentRes]) => {
        setStats(statsRes);
        setEnrollments(enrollmentRes.filter((e) => e.status === "ACTIVE"));
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("lectures.stats.loadStatsFailed")))
      .finally(() => setLoading(false));
  }, [set.id, classId]);

  return (
    <Modal open onClose={onClose} title={t("lectures.stats.title", { title: set.title })} size="lg">
      <div className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        {assignedClasses.length === 0 ? (
          <p className="text-xs text-slate-400 italic">{t("lectures.stats.notAssigned")}</p>
        ) : (
          <div>
            <label className={labelClass}>{t("lectures.stats.selectClassLabel")}</label>
            <Select value={classId ?? ""} onChange={(e) => setClassId(e.target.value ? Number(e.target.value) : null)} className={`${inputClass} w-64`}>
              <option value="">{t("lectures.stats.selectClassPlaceholder")}</option>
              {assignedClasses.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.classCode} — {c.name}
                </option>
              ))}
            </Select>
          </div>
        )}

        {loading ? (
          <p className="text-xs text-slate-500">{t("lectures.common.loading")}</p>
        ) : !stats || enrollments.length === 0 ? (
          <p className="text-xs text-slate-400 italic">{t("lectures.stats.empty")}</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-xs border-collapse">
              <thead>
                <tr className="bg-slate-50">
                  <th className="text-left p-2 border border-slate-200 sticky left-0 bg-slate-50">{t("lectures.stats.studentColumn")}</th>
                  {stats.videos.map((v) => (
                    <th key={v.videoId} className="text-center p-2 border border-slate-200 font-semibold whitespace-nowrap">
                      {v.title}
                      <span className="block text-[10px] font-normal text-slate-400">{t("lectures.stats.requiredViewsLabel", { count: v.requiredViewCount })}</span>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {enrollments.map((enr) => (
                  <tr key={enr.studentId}>
                    <td className="p-2 border border-slate-200 font-semibold sticky left-0 bg-white whitespace-nowrap">
                      {enr.studentFullName} <span className="text-slate-400 font-mono text-[10px]">({enr.studentCode})</span>
                    </td>
                    {stats.videos.map((v) => {
                      const cell = stats.cells.find((c) => c.studentId === enr.studentId && c.videoId === v.videoId);
                      const percent = cell?.watchedPercent ?? 0;
                      const completed = cell?.completed ?? false;
                      const viewCount = cell?.viewCount ?? 0;
                      return (
                        <td key={v.videoId} className={`text-center p-2 border border-slate-200 ${completed ? "bg-emerald-50 text-emerald-700 font-bold" : "text-slate-500"}`}>
                          {percent}%
                          <span className="block text-[10px] font-normal">{t("lectures.stats.viewsFraction", { viewCount, required: v.requiredViewCount })}</span>
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </Modal>
  );
}

