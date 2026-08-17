import React, { useEffect, useRef, useState } from "react";
import { BarChart3, Check, ClipboardList, Layers, Link2, MessageCircle, Music, Pencil, Plus, Users, Video, X } from "lucide-react";
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
const contentKindLabels: Record<ContentKind, string> = { VIDEO: "Video", AUDIO: "Audio" };

const videoTypeLabels: Record<ReviewVideoType, string> = { CONNECTION: "Video từ kết nối", REFLEX: "Video phản xạ" };
const videoTypeIcons: Record<ReviewVideoType, React.ReactNode> = {
  CONNECTION: <Link2 className="w-4 h-4" />,
  REFLEX: <MessageCircle className="w-4 h-4" />
};

const statusLabels: Record<ReviewVideoSetStatus, string> = { DRAFT: "Nháp", PUBLISHED: "Đã công bố", ARCHIVED: "Đã gỡ" };
const statusVariants: Record<ReviewVideoSetStatus, BadgeVariant> = { DRAFT: "neutral", PUBLISHED: "success", ARCHIVED: "danger" };

/** Đọc thời lượng (giây) của file video/audio NGAY TRÊN TRÌNH DUYỆT trước khi upload — API bắt buộc durationSeconds, backend không tự dò. */
function detectMediaDurationFromFile(file: File, kind: ContentKind): Promise<number> {
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
      reject(new Error("Không đọc được thời lượng file — thử chọn lại file khác."));
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
            else setDetectError("Không đọc được thời lượng — kiểm tra lại link YouTube.");
          },
          onError: () => {
            setDetecting(false);
            setDetectError("Link YouTube không hợp lệ hoặc video bị chặn nhúng.");
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
  return (
    <div className="grid grid-cols-2 gap-3 bg-sky-50/60 border border-sky-100 rounded-lg p-3">
      <div>
        <label className={labelClass}>Ngưỡng % pass điểm trắc nghiệm (mặc định 80)</label>
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
        <label className={labelClass}>Số lượt xem bắt buộc — câu hỏi chia đều qua các lượt (mặc định 1)</label>
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
  const [draft, setDraft] = useState<PendingReflexQuestion>(EMPTY_PENDING_QUESTION);
  const [draftError, setDraftError] = useState<string | null>(null);

  const handleAddDraft = () => {
    if (!draft.timestampSeconds || !draft.maxRecordingSeconds) {
      setDraftError("Vui lòng điền mốc thời gian và thời lượng ghi âm tối đa.");
      return;
    }
    setDraftError(null);
    onChange([...value, draft]);
    setDraft(EMPTY_PENDING_QUESTION);
  };

  const handleRemove = (index: number) => onChange(value.filter((_, i) => i !== index));

  return (
    <div className="space-y-2 bg-amber-50/40 border border-amber-200 rounded-lg p-3">
      <label className={labelClass}>Câu hỏi (mốc thời gian) — ít nhất 1 câu *</label>

      {value.length > 0 && (
        <div className="space-y-1.5">
          {value.map((q, i) => (
            <div key={i} className="flex items-center justify-between gap-2 bg-white border border-slate-200 rounded-lg p-2 text-[11px]">
              <span className="text-slate-700">
                Câu {i + 1} · Mốc {Math.floor(Number(q.timestampSeconds) / 60)}:{String(Number(q.timestampSeconds) % 60).padStart(2, "0")} · Ghi âm tối đa{" "}
                {q.maxRecordingSeconds}s · {q.maxAttempts ? `Tối đa ${q.maxAttempts} lượt nộp` : "Không giới hạn lượt nộp"}
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
          placeholder="Mốc giây *"
          className={inputClass}
        />
        <input
          type="number"
          min={1}
          value={draft.maxRecordingSeconds}
          onChange={(e) => setDraft({ ...draft, maxRecordingSeconds: e.target.value })}
          placeholder="Ghi âm tối đa (s) *"
          className={inputClass}
        />
        <input
          type="number"
          min={1}
          value={draft.maxAttempts}
          onChange={(e) => setDraft({ ...draft, maxAttempts: e.target.value })}
          placeholder="Số lượt nộp lại (bỏ trống = không giới hạn)"
          className={inputClass}
        />
      </div>
      <div className="flex gap-2">
        <input
          value={draft.prompt}
          onChange={(e) => setDraft({ ...draft, prompt: e.target.value })}
          placeholder="Nội dung câu hỏi (tuỳ chọn)"
          className={`${inputClass} flex-1`}
        />
        <Button type="button" variant="secondary" size="sm" onClick={handleAddDraft}>
          <Plus className="w-3.5 h-3.5" /> Thêm câu
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
      setDraftError("Vui lòng nhập nội dung câu hỏi.");
      return;
    }
    if (choices.some((c) => !c.content.trim())) {
      setDraftError("Vui lòng điền đủ nội dung mọi lựa chọn.");
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
      <label className={labelClass}>Câu hỏi trắc nghiệm (2-5 lựa chọn) — ít nhất 1 câu, bắt buộc *</label>

      {value.length > 0 && (
        <div className="space-y-1.5">
          {value.map((q, i) => (
            <div key={i} className="flex items-center justify-between gap-2 bg-white border border-slate-200 rounded-lg p-2 text-[11px]">
              <span className="text-slate-700">
                Câu {i + 1} · {q.prompt} · {q.choices.length} lựa chọn
              </span>
              <button type="button" onClick={() => handleRemoveQuestion(i)} className="text-rose-500 hover:text-rose-700 shrink-0">
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          ))}
        </div>
      )}

      {draftError && <p className="text-[11px] text-rose-600 font-semibold">{draftError}</p>}

      <input value={prompt} onChange={(e) => setPrompt(e.target.value)} placeholder="Nội dung câu hỏi *" className={inputClass} />

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
              placeholder={`Đáp án ${String.fromCharCode(65 + idx)}...`}
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
            <Plus className="w-3.5 h-3.5" /> Thêm lựa chọn
          </Button>
        )}
        <Button type="button" variant="secondary" size="sm" onClick={handleAddQuestion}>
          <Plus className="w-3.5 h-3.5" /> Thêm câu hỏi
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
  const contentKind: ContentKind = value.sourceType === "R2_AUDIO" ? "AUDIO" : "VIDEO";
  const videoSourceMode: "upload" | "youtube" = value.sourceType === "YOUTUBE_URL" ? "youtube" : "upload";
  const [youtubeUrlInput, setYoutubeUrlInput] = useState(value.sourceType === "YOUTUBE_URL" ? value.fileUrl : "");
  const { containerId, detect, detecting, detectError } = useYouTubeDurationProbe();

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
        <label className={labelClass}>Loại nội dung *</label>
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
              {contentKindLabels[k]}
            </button>
          ))}
        </div>
      </div>

      <div>
        <label className={labelClass}>{contentKind === "VIDEO" ? "Video *" : "Audio *"}</label>
        {contentKind === "VIDEO" && (
          <div className="flex gap-1.5 mb-1.5">
            <button
              type="button"
              onClick={() => updateValue({ sourceType: "R2_VIDEO", fileUrl: "", fileSizeBytes: undefined, durationSeconds: null })}
              className={`text-[10px] font-bold px-2.5 py-1 rounded-full ${videoSourceMode === "upload" ? "bg-brand-orange text-white" : "bg-slate-100 text-slate-500"}`}
            >
              Tải file lên
            </button>
            <button
              type="button"
              onClick={() => updateValue({ sourceType: "YOUTUBE_URL", fileUrl: "", durationSeconds: null })}
              className={`text-[10px] font-bold px-2.5 py-1 rounded-full ${videoSourceMode === "youtube" ? "bg-brand-orange text-white" : "bg-slate-100 text-slate-500"}`}
            >
              Dán link YouTube
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
                {detecting ? "Đang dò..." : "Dò thời lượng"}
              </Button>
            </div>
            {detectError && <p className="text-[10px] text-rose-600 font-semibold">{detectError}</p>}
          </div>
        ) : (
          <FileUploadField
            value={value.fileUrl}
            onChange={(url) => updateValue({ fileUrl: url })}
            onUpload={async (file) => {
              const durationSeconds = await detectMediaDurationFromFile(file, contentKind);
              updateValue({ durationSeconds });
              return uploadMedia(file, "REVIEW_VIDEO");
            }}
            onFileSize={(bytes) => updateValue({ fileSizeBytes: bytes })}
            accept={contentKind === "VIDEO" ? "video/*" : "audio/*"}
            placeholder={contentKind === "VIDEO" ? "Chọn file video..." : "Chọn file audio..."}
          />
        )}
      </div>

      <p className="text-[10px] text-slate-400">
        Thời lượng đã dò: <span className="font-bold text-slate-600">{value.durationSeconds ? `${value.durationSeconds} giây (${Math.round(value.durationSeconds / 60)} phút)` : "— chưa có —"}</span>
      </p>
    </div>
  );
}

const reviewVideoTeacherTypeLabels: Record<ReviewVideoTeacherType, string> = { VIETNAMESE: "Giáo viên Việt Nam", FOREIGN: "Giáo viên nước ngoài" };

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
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách bộ video ôn tập."))
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
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Kho Video Ôn tập</h1>
          <p className="text-xs text-slate-500 mt-1">
            2 loại: "Video từ kết nối" (ôn từ vựng buổi học) và "Video phản xạ" (hỏi-đáp luyện nói). Mỗi Bộ gồm nhiều video/audio,
            gán khung chương trình CHỈ để lọc/tìm kiếm — gán tường minh cho (các) lớp cụ thể mới là điều kiện hiển thị. Công bố chỉ
            đánh dấu Bộ đủ điều kiện dùng làm nguồn — Giáo viên chọn Bộ đã công bố làm "BTVN buổi sau" ở Nhận xét học viên mới thật
            sự giao cho lớp xem, hệ thống tự theo dõi % đã xem thật.
          </p>
        </div>
        <Button variant="primary" size="sm" onClick={() => setShowCreateForm(true)}>
          <Plus className="w-3.5 h-3.5" />
          Tạo Bộ video mới
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
              <option value="">Tất cả khung chương trình</option>
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
              <option value="">Tất cả loại giáo viên</option>
              {(Object.keys(reviewVideoTeacherTypeLabels) as ReviewVideoTeacherType[]).map((t) => (
                <option key={t} value={t}>
                  {reviewVideoTeacherTypeLabels[t]}
                </option>
              ))}
            </Select>
          </div>

          {loadingSets ? (
            <p className="text-xs text-slate-500 p-6 text-center">Đang tải...</p>
          ) : videoSets.length === 0 ? (
            <div className="flex flex-col items-center justify-center p-12 text-center text-slate-400 space-y-3">
              <Layers className="w-12 h-12 text-slate-300" />
              <p className="text-xs text-slate-400">Chưa có Bộ video nào{curriculumFilter ? " trong khung chương trình này" : ""}.</p>
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
                      <Badge variant={statusVariants[set.status]}>{statusLabels[set.status]}</Badge>
                    </div>
                    <p className="text-[10px] text-slate-400 mt-0.5 font-mono">{set.code} · {set.curriculumCode}</p>
                    <p className="text-[10px] text-slate-400 mt-0.5">
                      {reviewVideoTeacherTypeLabels[set.teacherType]} · {videoTypeLabels[set.videoType]}
                    </p>
                  </button>
                ))}
              </div>
              <Pagination
                page={page}
                pageSize={pageSize}
                totalElements={videoSets.length}
                itemLabel="Bộ video"
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
              <p className="text-xs text-slate-400">Chọn 1 Bộ video bên trái để xem chi tiết, hoặc tạo Bộ mới.</p>
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
            loadSets();
            setSelectedSetId(set.id);
            showToast("Đã tạo Bộ video thành công!");
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
          <Badge variant={statusVariants[set.status]}>{statusLabels[set.status]}</Badge>
        </div>
        <div className="flex items-center justify-between flex-wrap gap-2">
          <button
            onClick={() => setAssignClassOpen(true)}
            className="flex items-center gap-1.5 text-[11px] font-bold text-brand-red hover:underline"
          >
            <Users className="w-3.5 h-3.5" />
            {assignedClassCount == null ? "Đã gán ... lớp" : `Đã gán ${assignedClassCount} lớp`} — quản lý
          </button>
          <div className="flex items-center gap-2 flex-wrap">
            <Button size="sm" variant="secondary" onClick={() => setEditingSet(true)}>
              Sửa
            </Button>
            <Button size="sm" variant="secondary" onClick={() => setStatsOpen(true)}>
              <BarChart3 className="w-3.5 h-3.5" /> Thống kê
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
            showToast("Đã lưu bộ video ôn tập thành công!");
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
  const { classes } = useEligibleClasses();
  const [assignedIds, setAssignedIds] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(true);
  const [pendingId, setPendingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listReviewVideoSetAssignedClasses(setId)
      .then((cls) => setAssignedIds(new Set(cls.map((c) => c.id))))
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách lớp đã gán."))
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
      setError(err instanceof ApiError ? err.message : "Cập nhật gán lớp thất bại.");
    } finally {
      setPendingId(null);
    }
  };

  return (
    <Modal open onClose={onClose} title="Gán Bộ video cho lớp" size="md">
      <p className="text-[11px] text-slate-500 mb-3">
        Bộ chỉ hiển thị cho học sinh của các lớp đã gán ở đây — vẫn cần Giáo viên chọn bộ này làm "BTVN buổi sau" ở Nhận xét học
        viên mới thật sự giao cho học sinh xem.
      </p>
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg mb-3">{error}</div>}
      {loading ? (
        <p className="text-xs text-slate-500 p-3 text-center">Đang tải...</p>
      ) : classes.length === 0 ? (
        <p className="text-xs text-slate-400 italic p-3 text-center">Không có lớp nào để gán.</p>
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
              {pendingId === c.id && <span className="text-[10px] text-slate-400">Đang lưu...</span>}
            </label>
          ))}
        </div>
      )}
      <div className="flex justify-end pt-3">
        <Button type="button" variant="secondary" size="sm" onClick={onClose}>
          <X className="w-3.5 h-3.5" />
          Đóng
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
      setError("Vui lòng điền mã, tiêu đề, chọn Khung chương trình, Loại giáo viên, video/audio và chờ dò xong thời lượng.");
      return;
    }
    if (questionSourceMode === "manual" && form.videoType === "REFLEX" && pendingQuestions.length === 0) {
      setError("Video phản xạ cần ít nhất 1 câu hỏi — dùng \"Thêm câu\" ở khung câu hỏi bên dưới.");
      return;
    }
    if (questionSourceMode === "manual" && form.videoType === "CONNECTION" && pendingConnectionQuestions.length === 0) {
      setError("Video kết nối bắt buộc có ít nhất 1 câu hỏi trắc nghiệm — dùng \"Thêm câu hỏi\" ở khung câu hỏi bên dưới.");
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
          ? "Đã tạo bộ và video nhưng gắn câu hỏi thất bại — mở lại bộ này, bấm \"Video\" → \"Quản lý câu hỏi\" để thêm lại."
          : createdSetId
            ? "Đã tạo bộ nhưng gắn video/audio thất bại — mở lại bộ này, bấm \"Video\" để thêm lại."
            : err instanceof ApiError
              ? err.message
              : "Tạo bộ video ôn tập thất bại."
      );
    } finally {
      setSubmitting(false);
    }
  };

  if (stage === "import" && createdSetForImport && createdVideoIdForImport) {
    return (
      <Modal open onClose={() => onCreated(createdSetForImport)} title="Nhập câu hỏi từ Excel" size="lg">
        <div className="space-y-4">
          <p className="text-xs text-slate-500">
            Đã tạo bộ &amp; video — tải file mẫu, điền câu hỏi rồi nhập lại bên dưới. Bấm "Xong" để đóng khi hoàn tất
            (có thể mở lại bộ này sau để nhập tiếp nếu cần).
          </p>
          <ReviewVideoQuestionImportPanel
            videoId={createdVideoIdForImport}
            videoType={form.videoType}
            onImported={() => undefined}
          />
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="primary" onClick={() => onCreated(createdSetForImport)}>
              Xong
            </Button>
          </div>
        </div>
      </Modal>
    );
  }

  return (
    <Modal open onClose={onClose} title="Tạo bộ video ôn tập mới" size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Mã bộ *</label>
            <input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} placeholder="VD: RV-U1-CONN-01" className={inputClass} required />
          </div>
          <div>
            <label className={labelClass}>Loại video *</label>
            <div className="flex gap-1.5">
              {(Object.keys(videoTypeLabels) as ReviewVideoType[]).map((t) => (
                <button
                  key={t}
                  type="button"
                  onClick={() => setForm({ ...form, videoType: t })}
                  className={`flex-1 text-xs font-bold py-2.5 rounded-lg border ${
                    form.videoType === t ? "bg-brand-orange border-brand-orange text-white" : "bg-slate-50 border-slate-200 text-slate-500"
                  }`}
                >
                  {videoTypeLabels[t]}
                </button>
              ))}
            </div>
          </div>
        </div>
        <div>
          <label className={labelClass}>Tiêu đề *</label>
          <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="VD: Unit 1: Greetings and Introduction" className={inputClass} required />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Khung chương trình * (chỉ dùng lọc/tìm kiếm)</label>
            <Select
              value={form.curriculumId}
              onChange={(e) => setForm({ ...form, curriculumId: e.target.value ? Number(e.target.value) : "" })}
              className={inputClass}
            >
              <option value="">-- Chọn khung chương trình --</option>
              {curriculums.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.code} — {c.name}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className={labelClass}>Loại giáo viên * (dùng lọc khi giao bài)</label>
            <Select value={form.teacherType} onChange={(e) => setForm({ ...form, teacherType: e.target.value as ReviewVideoTeacherType | "" })} className={inputClass}>
              <option value="">-- Chọn loại giáo viên --</option>
              {(Object.keys(reviewVideoTeacherTypeLabels) as ReviewVideoTeacherType[]).map((t) => (
                <option key={t} value={t}>
                  {reviewVideoTeacherTypeLabels[t]}
                </option>
              ))}
            </Select>
          </div>
        </div>
        <ContentSourceField value={content} onChange={setContent} />
        {form.videoType === "CONNECTION" && <ConnectionThresholdFields value={connSettings} onChange={setConnSettings} />}

        <div>
          <label className={labelClass}>Nguồn câu hỏi</label>
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
                {m === "manual" ? "Nhập tay" : "Nhập Excel"}
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
            Tạo bộ + video trước — sau khi tạo xong, màn nhập câu hỏi từ Excel sẽ mở ra ngay (dùng videoId thật vừa tạo).
          </p>
        )}
        <div className="grid grid-cols-2 gap-3 items-end">
          <div>
            <label className={labelClass}>Học phần (tùy chọn)</label>
            <Select value={form.subjectId} onChange={(e) => setForm({ ...form, subjectId: e.target.value })} className={inputClass}>
              <option value="">-- Không gán --</option>
              {subjects.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className={labelClass}>Thứ tự</label>
            <input type="number" value={form.displayOrder} onChange={(e) => setForm({ ...form, displayOrder: e.target.value })} className={inputClass} />
          </div>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? "Đang lưu..." : "Tạo bộ video"}
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
      setError("Vui lòng điền tiêu đề.");
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
      setError(err instanceof ApiError ? err.message : "Cập nhật bộ video ôn tập thất bại — có thể bạn không được phân công giảng dạy lớp thuộc khung chương trình này.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={`Sửa bộ video: ${set.code}`} size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        <div>
          <label className={labelClass}>Tiêu đề *</label>
          <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} className={inputClass} required />
        </div>
        <div>
          <label className={labelClass}>Loại giáo viên * (dùng lọc khi giao bài)</label>
          <Select value={form.teacherType} onChange={(e) => setForm({ ...form, teacherType: e.target.value as ReviewVideoTeacherType })} className={inputClass}>
            {(Object.keys(reviewVideoTeacherTypeLabels) as ReviewVideoTeacherType[]).map((t) => (
              <option key={t} value={t}>
                {reviewVideoTeacherTypeLabels[t]}
              </option>
            ))}
          </Select>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Học phần (tùy chọn)</label>
            <Select value={form.subjectId} onChange={(e) => setForm({ ...form, subjectId: e.target.value })} className={inputClass}>
              <option value="">-- Không gán --</option>
              {subjects.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className={labelClass}>Trạng thái</label>
            <Select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value as ReviewVideoSetStatus })} className={inputClass}>
              {Object.entries(statusLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </div>
        </div>
        <div>
          <label className={labelClass}>Thứ tự</label>
          <input type="number" value={form.displayOrder} onChange={(e) => setForm({ ...form, displayOrder: e.target.value })} className={inputClass} />
        </div>
        <p className="text-[10px] text-slate-400 italic">
          Mã Bộ ({set.code}) và Khung chương trình ({set.curriculumCode}) không sửa được sau khi tạo. Chuyển trạng thái sang "Đã
          công bố" để đủ điều kiện dùng làm nguồn (chỉ set 1 lần thời điểm công bố) — học sinh CHƯA xem được ngay, chỉ xem sau khi
          Giáo viên chọn bộ này làm "BTVN buổi sau" ở Nhận xét học viên. Chuyển "Đã gỡ" (ARCHIVED) để gỡ khỏi kho — không xoá hẳn
          bản ghi.
        </p>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? "Đang lưu..." : "Lưu thay đổi"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

/** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12 — hiện danh sách video TRỰC TIẾP trong panel chi tiết bộ (trước đây là VideosModal, ẩn sau nút "Video" + modal riêng). */
function VideoListSection({ set }: { set: ReviewVideoSetResponse }) {
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
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách video."))
      .finally(() => setLoading(false));
  };

  useEffect(load, [set.id]);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !content.fileUrl.trim() || !content.durationSeconds) {
      setError("Vui lòng điền tiêu đề, video/audio và chờ dò xong thời lượng.");
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
      showToast("Đã thêm video thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Thêm video thất bại — có thể bạn không được phân công giảng dạy lớp/khung này.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="px-5 py-4 border-b border-slate-100 space-y-4">
      <p className="text-xs font-bold text-slate-700 uppercase tracking-wide">Video</p>
      <div className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        {loading ? (
          <p className="text-xs text-slate-500">Đang tải...</p>
        ) : videos.length === 0 ? (
          <p className="text-xs text-slate-400 italic">Bộ này chưa có video nào.</p>
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
                <p className="text-slate-400 break-all">{v.fileUrl}</p>
                <p className="text-slate-400">
                  {v.fileSizeBytes ? `${(v.fileSizeBytes / 1024 / 1024).toFixed(1)} MB` : "—"} · {Math.round(v.durationSeconds / 60)} phút
                  {set.videoType === "CONNECTION" && ` · Ngưỡng pass ${v.completionThresholdPercent}% · Chia đều qua ${v.requiredViewCount} lượt`}
                </p>
                {(set.videoType === "REFLEX" || set.videoType === "CONNECTION") && (
                  <button
                    type="button"
                    onClick={() => setExpandedVideoId(expandedVideoId === v.id ? null : v.id)}
                    className="text-brand-red font-bold hover:underline"
                  >
                    {expandedVideoId === v.id ? "Đóng câu hỏi" : "Quản lý câu hỏi"}
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
              <label className={labelClass}>Tiêu đề *</label>
              <input value={title} onChange={(e) => setTitle(e.target.value)} className={inputClass} required />
            </div>
            <ContentSourceField value={content} onChange={setContent} />
            {set.videoType === "CONNECTION" && <ConnectionThresholdFields value={connSettings} onChange={setConnSettings} />}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="secondary" onClick={() => setShowAddForm(false)}>
                Hủy
              </Button>
              <Button type="submit" variant="primary" disabled={submitting}>
                {submitting ? "Đang lưu..." : "Thêm video"}
              </Button>
            </div>
          </form>
        ) : (
          <Button variant="secondary" onClick={() => setShowAddForm(true)}>
            <Plus className="w-4 h-4" /> Thêm video
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
  return (
    <>
      <div className="grid grid-cols-3 gap-2">
        <input
          type="number"
          min={0}
          value={value.timestampSeconds}
          onChange={(e) => onChange({ ...value, timestampSeconds: e.target.value })}
          placeholder="Mốc giây *"
          className={inputClass}
        />
        <input
          type="number"
          min={1}
          value={value.maxRecordingSeconds}
          onChange={(e) => onChange({ ...value, maxRecordingSeconds: e.target.value })}
          placeholder="Ghi âm tối đa (s) *"
          className={inputClass}
        />
        <input
          type="number"
          min={1}
          value={value.maxAttempts}
          onChange={(e) => onChange({ ...value, maxAttempts: e.target.value })}
          placeholder="Số lượt nộp lại (để trống = không giới hạn)"
          className={inputClass}
        />
      </div>
      <textarea
        value={value.prompt}
        onChange={(e) => onChange({ ...value, prompt: e.target.value })}
        placeholder="Nội dung câu hỏi (tuỳ chọn)"
        rows={2}
        className={inputClass}
      />
    </>
  );
}

/** UC-23b (V57): quản lý câu hỏi gắn mốc thời gian của 1 video REFLEX — mỗi câu tự có thời lượng ghi âm/số lần nộp lại riêng. */
function VideoQuestionsPanel({ videoId }: { videoId: number }) {
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
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách câu hỏi."))
      .finally(() => setLoading(false));
  };

  useEffect(load, [videoId]);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.timestampSeconds || !form.maxRecordingSeconds) {
      setError("Vui lòng điền mốc thời gian và thời lượng ghi âm tối đa.");
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
      setError(err instanceof ApiError ? err.message : "Thêm câu hỏi thất bại.");
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
      setEditError("Vui lòng điền mốc thời gian và thời lượng ghi âm tối đa.");
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
      setEditError(err instanceof ApiError ? err.message : "Sửa câu hỏi thất bại.");
    } finally {
      setEditSubmitting(false);
    }
  };

  return (
    <div className="border-t border-slate-100 mt-2 pt-2 space-y-2">
      {error && <div className="text-[11px] text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{error}</div>}
      {loading ? (
        <p className="text-slate-400">Đang tải câu hỏi...</p>
      ) : questions.length === 0 ? (
        <p className="text-slate-400 italic">Chưa có câu hỏi nào — học sinh sẽ không nộp được bài cho tới khi có ít nhất 1 câu.</p>
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
                    Hủy
                  </Button>
                  <Button type="submit" variant="primary" size="sm" disabled={editSubmitting}>
                    {editSubmitting ? "Đang lưu..." : "Lưu câu hỏi"}
                  </Button>
                </div>
              </form>
            ) : (
              <div key={q.id} className="bg-slate-50 border border-slate-200 rounded-lg p-2">
                <div className="flex items-start justify-between gap-2">
                  <p className="font-bold text-slate-700">
                    Câu {i + 1} · Mốc {Math.floor(q.timestampSeconds / 60)}:{String(q.timestampSeconds % 60).padStart(2, "0")} · Ghi âm tối đa {q.maxRecordingSeconds}s ·{" "}
                    {q.maxAttempts != null ? `Tối đa ${q.maxAttempts} lượt nộp` : "Không giới hạn lượt nộp"}
                  </p>
                  <button
                    type="button"
                    onClick={() => startEdit(q)}
                    className="shrink-0 text-slate-400 hover:text-brand-red transition-colors"
                    title="Sửa câu hỏi"
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
              Hủy
            </Button>
            <Button type="submit" variant="primary" size="sm" disabled={submitting}>
              {submitting ? "Đang lưu..." : "Thêm câu hỏi"}
            </Button>
          </div>
        </form>
      ) : showImportPanel ? (
        <div className="bg-white border border-slate-200 rounded-lg p-2.5 space-y-2">
          <div className="flex items-center justify-between">
            <p className="text-[11px] font-bold text-slate-600">Nhập câu hỏi từ Excel</p>
            <Button type="button" variant="secondary" size="sm" onClick={() => setShowImportPanel(false)}>
              Đóng
            </Button>
          </div>
          <ReviewVideoQuestionImportPanel videoId={videoId} videoType="REFLEX" onImported={() => { setShowImportPanel(false); load(); }} />
        </div>
      ) : (
        <div className="flex gap-2">
          <Button type="button" variant="secondary" size="sm" onClick={() => setShowAddForm(true)}>
            <Plus className="w-3.5 h-3.5" /> Thêm câu hỏi
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={() => setShowImportPanel(true)}>
            Nhập Excel
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
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách câu hỏi."))
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
      setError("Vui lòng điền nội dung câu hỏi và mọi lựa chọn.");
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
      setError(err instanceof ApiError ? err.message : "Thêm câu hỏi thất bại.");
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
      setEditError("Vui lòng điền nội dung câu hỏi và mọi đáp án.");
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
      setEditError(err instanceof ApiError ? err.message : "Sửa câu hỏi thất bại.");
    } finally {
      setEditSubmitting(false);
    }
  };

  return (
    <div className="border-t border-slate-100 mt-2 pt-2 space-y-2">
      {error && <div className="text-[11px] text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{error}</div>}
      {loading ? (
        <p className="text-slate-400">Đang tải câu hỏi...</p>
      ) : questions.length === 0 ? (
        <p className="text-slate-400 italic">Chưa có câu hỏi nào — bộ này KHÔNG Publish được tới khi có ít nhất 1 câu.</p>
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
                <input value={editPrompt} onChange={(e) => setEditPrompt(e.target.value)} placeholder="Nội dung câu hỏi *" className={inputClass} />
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
                        placeholder={`Đáp án ${String.fromCharCode(65 + idx)}...`}
                        className={`flex-1 ${inputClass}`}
                      />
                    </div>
                  ))}
                </div>
                <p className="text-[10px] text-slate-400 italic">Không đổi được số lượng đáp án khi sửa — tạo câu hỏi mới nếu cần thêm/bớt.</p>
                <div className="flex justify-end gap-2">
                  <Button type="button" variant="secondary" size="sm" onClick={() => setEditingQuestionId(null)}>
                    Hủy
                  </Button>
                  <Button type="submit" variant="primary" size="sm" disabled={editSubmitting}>
                    {editSubmitting ? "Đang lưu..." : "Lưu câu hỏi"}
                  </Button>
                </div>
              </form>
            ) : (
              <div key={q.id} className="bg-slate-50 border border-slate-200 rounded-lg p-2">
                <div className="flex items-start justify-between gap-2">
                  <p className="font-bold text-slate-700">Câu {i + 1} · {q.prompt}</p>
                  <button
                    type="button"
                    onClick={() => startEdit(q)}
                    className="shrink-0 text-slate-400 hover:text-brand-red transition-colors"
                    title="Sửa câu hỏi"
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
          <input value={prompt} onChange={(e) => setPrompt(e.target.value)} placeholder="Nội dung câu hỏi *" className={inputClass} />
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
                  placeholder={`Đáp án ${String.fromCharCode(65 + idx)}...`}
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
                <Plus className="w-3.5 h-3.5" /> Thêm lựa chọn
              </Button>
            ) : <span />}
            <div className="flex gap-2">
              <Button type="button" variant="secondary" size="sm" onClick={() => setShowAddForm(false)}>
                Hủy
              </Button>
              <Button type="submit" variant="primary" size="sm" disabled={submitting}>
                {submitting ? "Đang lưu..." : "Thêm câu hỏi"}
              </Button>
            </div>
          </div>
        </form>
      ) : showImportPanel ? (
        <div className="bg-white border border-slate-200 rounded-lg p-2.5 space-y-2">
          <div className="flex items-center justify-between">
            <p className="text-[11px] font-bold text-slate-600">Nhập câu hỏi từ Excel</p>
            <Button type="button" variant="secondary" size="sm" onClick={() => setShowImportPanel(false)}>
              Đóng
            </Button>
          </div>
          <ReviewVideoQuestionImportPanel videoId={videoId} videoType="CONNECTION" onImported={() => { setShowImportPanel(false); load(); }} />
        </div>
      ) : (
        <div className="flex gap-2">
          <Button type="button" variant="secondary" size="sm" onClick={() => setShowAddForm(true)}>
            <Plus className="w-3.5 h-3.5" /> Thêm câu hỏi
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={() => setShowImportPanel(true)}>
            Nhập Excel
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
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách lớp đã gán."));
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
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được thống kê."))
      .finally(() => setLoading(false));
  }, [set.id, classId]);

  return (
    <Modal open onClose={onClose} title={`Thống kê — ${set.title}`} size="lg">
      <div className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        {assignedClasses.length === 0 ? (
          <p className="text-xs text-slate-400 italic">Bộ này chưa được gán cho lớp nào — dùng "Đã gán ... lớp" ở chi tiết Bộ để gán trước.</p>
        ) : (
          <div>
            <label className={labelClass}>Xem thống kê theo lớp *</label>
            <Select value={classId ?? ""} onChange={(e) => setClassId(e.target.value ? Number(e.target.value) : null)} className={`${inputClass} w-64`}>
              <option value="">-- Chọn lớp --</option>
              {assignedClasses.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.classCode} — {c.name}
                </option>
              ))}
            </Select>
          </div>
        )}

        {loading ? (
          <p className="text-xs text-slate-500">Đang tải...</p>
        ) : !stats || enrollments.length === 0 ? (
          <p className="text-xs text-slate-400 italic">Chưa có dữ liệu để hiển thị.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-xs border-collapse">
              <thead>
                <tr className="bg-slate-50">
                  <th className="text-left p-2 border border-slate-200 sticky left-0 bg-slate-50">Học sinh</th>
                  {stats.videos.map((v) => (
                    <th key={v.videoId} className="text-center p-2 border border-slate-200 font-semibold whitespace-nowrap">
                      {v.title}
                      <span className="block text-[10px] font-normal text-slate-400">Cần đạt {v.requiredViewCount} lượt</span>
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
                          {percent}%<span className="block text-[10px] font-normal">{viewCount}/{v.requiredViewCount} lượt</span>
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

