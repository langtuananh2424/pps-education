import React, { useEffect, useRef, useState } from "react";
import { BarChart3, Link2, MessageCircle, Music, Plus, Upload, Video } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { CurriculumResponse, CurriculumSubjectResponse, ClassEnrollmentResponse, listClassEnrollments, listCurriculums, listCurriculumSubjects } from "@/features/academic/api";
import { useEligibleClasses } from "@/features/academic/hooks/useEligibleClasses";
import {
  AddReviewVideoRequest,
  CreateReviewVideoSetRequest,
  ReviewVideoResponse,
  ReviewVideoSetResponse,
  ReviewVideoSetStatsResponse,
  ReviewVideoSetStatus,
  ReviewVideoSourceType,
  ReviewVideoType,
  UpdateReviewVideoSetRequest,
  addReviewVideo,
  createReviewVideoSet,
  getReviewVideoSetStats,
  listReviewVideoSetsByClass,
  listReviewVideoSetsByCurriculum,
  listReviewVideos,
  updateReviewVideoSet,
  uploadMedia
} from "../api";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Modal from "@/components/ui/Modal";
import FileUploadField from "@/components/ui/FileUploadField";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";

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

interface ContentSourceValue {
  sourceType: ReviewVideoSourceType;
  fileUrl: string;
  fileSizeBytes?: number;
  durationSeconds: number | null;
}

/** Nguồn nội dung dùng chung cho form Tạo bộ mới và modal Video — Video cho chọn Tải file lên hoặc Dán link YouTube, Audio chỉ Tải file lên. Cả 3 nguồn đều bắt buộc tự dò durationSeconds trước khi cho submit (API yêu cầu, BE không tự dò). */
function ContentSourceField({ value, onChange }: { value: ContentSourceValue; onChange: (v: ContentSourceValue) => void }) {
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

/**
 * UC-23/UC-23a: Kho Video Ôn tập — chỉ Giáo viên được phân công dạy đúng lớp/khung chương trình mới
 * tạo/sửa/xem thống kê được (BE tự chặn theo class_teachers, không qua permission).
 *
 * Chọn lớp cho trang này dùng state RIÊNG của trang (localClassId), KHÔNG dùng selectedClassId dùng
 * chung ở Header — đã xác nhận với người dùng 2026-07-27: Header chỉ hiện pill "Lớp" cho tài khoản
 * có phân công thật (Giáo viên đứng lớp/SITE_MANAGER thật); tài khoản chỉ có quyền quản trị rộng
 * (Super Admin/HEAD_ACADEMIC) không thấy pill đó nên cần tự chọn lớp ngay trong trang, giống hệt
 * cách chọn khung chương trình ở tab bên cạnh.
 */
export default function LecturesPage() {
  const [scopeType, setScopeType] = useState<"CLASS" | "CURRICULUM">("CLASS");
  const { classes } = useEligibleClasses();
  const [localClassId, setLocalClassId] = useState<number | null>(null);
  const [curriculums, setCurriculums] = useState<CurriculumResponse[]>([]);
  const [selectedCurriculumId, setSelectedCurriculumId] = useState<number | null>(null);

  const [videoSets, setVideoSets] = useState<ReviewVideoSetResponse[]>([]);
  const [loadingSets, setLoadingSets] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [showCreateForm, setShowCreateForm] = useState(false);
  const [editingSet, setEditingSet] = useState<ReviewVideoSetResponse | null>(null);
  const [videosSet, setVideosSet] = useState<ReviewVideoSetResponse | null>(null);
  const [statsSet, setStatsSet] = useState<ReviewVideoSetResponse | null>(null);
  const { message: toastMessage, showToast } = useToast();

  const selectedClass = classes.find((c) => c.id === localClassId) ?? null;
  const effectiveCurriculumId = scopeType === "CLASS" ? selectedClass?.curriculumId ?? null : selectedCurriculumId;

  useEffect(() => {
    listCurriculums().then(setCurriculums).catch(() => undefined);
  }, []);

  const loadSets = () => {
    if (scopeType === "CLASS" && !localClassId) {
      setVideoSets([]);
      return;
    }
    if (scopeType === "CURRICULUM" && !selectedCurriculumId) {
      setVideoSets([]);
      return;
    }
    setLoadingSets(true);
    setError(null);
    const request = scopeType === "CLASS" ? listReviewVideoSetsByClass(localClassId!) : listReviewVideoSetsByCurriculum(selectedCurriculumId!);
    request
      .then(setVideoSets)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách bộ video ôn tập."))
      .finally(() => setLoadingSets(false));
  };

  useEffect(loadSets, [scopeType, localClassId, selectedCurriculumId]);

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Tài Liệu & Khảo Thí LMS (E-Learning)</h1>
        <p className="text-xs text-slate-500 mt-1">
          Kho Video Ôn tập (UC-23/23a) — 2 loại: "Video từ kết nối" (ôn từ vựng buổi học) và "Video phản xạ" (hỏi-đáp luyện nói). Mỗi bộ gồm
          nhiều video/audio; học sinh xem sau khi bộ được công bố, hệ thống tự theo dõi % đã xem thật.
        </p>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <Card className="space-y-3">
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex bg-slate-100 rounded-lg p-1 text-xs font-semibold">
            <button
              type="button"
              onClick={() => {
                setScopeType("CLASS");
                setSelectedCurriculumId(null);
              }}
              className={`px-3 py-1.5 rounded-md transition-all ${scopeType === "CLASS" ? "bg-white shadow-sm text-slate-800" : "text-slate-500"}`}
            >
              Theo lớp cụ thể
            </button>
            <button
              type="button"
              onClick={() => setScopeType("CURRICULUM")}
              className={`px-3 py-1.5 rounded-md transition-all ${scopeType === "CURRICULUM" ? "bg-white shadow-sm text-slate-800" : "text-slate-500"}`}
            >
              Theo khung chương trình (dùng chung)
            </button>
          </div>

          {scopeType === "CLASS" ? (
            <select value={localClassId ?? ""} onChange={(e) => setLocalClassId(e.target.value ? Number(e.target.value) : null)} className={`${inputClass} w-64`}>
              <option value="">-- Chọn lớp --</option>
              {classes.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.classCode} — {c.name}
                </option>
              ))}
            </select>
          ) : (
            <select value={selectedCurriculumId ?? ""} onChange={(e) => setSelectedCurriculumId(e.target.value ? Number(e.target.value) : null)} className={`${inputClass} w-64`}>
              <option value="">-- Chọn khung chương trình --</option>
              {curriculums.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.code} — {c.name}
                </option>
              ))}
            </select>
          )}

          <Button
            variant="primary"
            disabled={scopeType === "CLASS" ? !localClassId : !selectedCurriculumId}
            onClick={() => setShowCreateForm(true)}
            className="ml-auto"
          >
            <Plus className="w-4 h-4" />
            <span>Tạo bộ video mới</span>
          </Button>
        </div>
        <p className="text-[10px] text-slate-400 italic">
          Chỉ Giáo viên được phân công dạy đúng lớp/khung chương trình mới tạo/sửa được — tài khoản khác chọn được để xem nhưng thao tác sẽ bị hệ
          thống chặn.
        </p>
      </Card>

      {loadingSets ? (
        <p className="text-xs text-slate-500">Đang tải...</p>
      ) : !localClassId && !selectedCurriculumId ? (
        <p className="text-xs text-slate-400 italic text-center py-10">Chọn lớp hoặc khung chương trình ở trên để xem kho video ôn tập.</p>
      ) : videoSets.length === 0 ? (
        <p className="text-xs text-slate-400 italic text-center py-10">Chưa có bộ video ôn tập nào trong phạm vi này.</p>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {videoSets.map((set) => (
            <Card key={set.id} className="flex flex-col justify-between">
              <div className="space-y-2">
                <div className="flex items-center justify-between gap-2">
                  <span className="p-2 rounded-lg bg-amber-50 text-brand-orange shrink-0">{videoTypeIcons[set.videoType]}</span>
                  <Badge variant={statusVariants[set.status]}>{statusLabels[set.status]}</Badge>
                </div>
                <div>
                  <h4 className="text-xs font-bold text-slate-800 leading-normal">{set.title}</h4>
                  <span className="text-[10px] text-slate-400 font-mono mt-0.5 block">{set.code}</span>
                </div>
                <p className="text-[11px] text-slate-500">{videoTypeLabels[set.videoType]}</p>
              </div>
              <div className="border-t border-slate-100 pt-3 mt-3 flex items-center gap-2 flex-wrap">
                <Button size="sm" variant="secondary" onClick={() => setVideosSet(set)}>
                  <Upload className="w-3.5 h-3.5" /> Video
                </Button>
                <Button size="sm" variant="secondary" onClick={() => setEditingSet(set)}>
                  Sửa
                </Button>
                <Button size="sm" variant="secondary" onClick={() => setStatsSet(set)}>
                  <BarChart3 className="w-3.5 h-3.5" /> Thống kê
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      {showCreateForm && (
        <CreateSetModal
          scopeType={scopeType}
          classId={localClassId}
          curriculumId={selectedCurriculumId}
          curriculumIdForSubjects={effectiveCurriculumId}
          onClose={() => setShowCreateForm(false)}
          onCreated={() => {
            setShowCreateForm(false);
            loadSets();
            showToast("Đã tạo bộ video ôn tập thành công!");
          }}
        />
      )}

      {editingSet && (
        <EditSetModal
          set={editingSet}
          curriculumIdForSubjects={editingSet.curriculumId ?? classes.find((c) => c.id === editingSet.classId)?.curriculumId ?? null}
          onClose={() => setEditingSet(null)}
          onSaved={() => {
            setEditingSet(null);
            loadSets();
            showToast("Đã lưu bộ video ôn tập thành công!");
          }}
        />
      )}

      {videosSet && <VideosModal set={videosSet} onClose={() => setVideosSet(null)} />}

      {statsSet && <StatsModal set={statsSet} classes={classes} onClose={() => setStatsSet(null)} />}

      <Toast message={toastMessage} />
    </div>
  );
}

function CreateSetModal({
  scopeType,
  classId,
  curriculumId,
  curriculumIdForSubjects,
  onClose,
  onCreated
}: {
  scopeType: "CLASS" | "CURRICULUM";
  classId: number | null;
  curriculumId: number | null;
  curriculumIdForSubjects: number | null;
  onClose: () => void;
  onCreated: () => void;
}) {
  const [subjects, setSubjects] = useState<CurriculumSubjectResponse[]>([]);
  const [form, setForm] = useState({ code: "", title: "", videoType: "CONNECTION" as ReviewVideoType, subjectId: "", displayOrder: "" });
  const [content, setContent] = useState<ContentSourceValue>({ sourceType: "R2_VIDEO", fileUrl: "", durationSeconds: null });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (curriculumIdForSubjects) listCurriculumSubjects(curriculumIdForSubjects).then(setSubjects).catch(() => undefined);
  }, [curriculumIdForSubjects]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.code.trim() || !form.title.trim() || !content.fileUrl.trim() || !content.durationSeconds) {
      setError("Vui lòng điền mã, tiêu đề, video/audio và chờ dò xong thời lượng.");
      return;
    }
    setSubmitting(true);
    setError(null);
    let createdSetId: number | null = null;
    try {
      const setRequest: CreateReviewVideoSetRequest = {
        code: form.code.trim(),
        title: form.title.trim(),
        videoType: form.videoType,
        classId: scopeType === "CLASS" ? classId ?? undefined : undefined,
        curriculumId: scopeType === "CURRICULUM" ? curriculumId ?? undefined : undefined,
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
        displayOrder: 0
      };
      await addReviewVideo(set.id, videoRequest);
      onCreated();
    } catch (err) {
      setError(
        createdSetId
          ? "Đã tạo bộ nhưng gắn video/audio thất bại — mở lại bộ này, bấm \"Video\" để thêm lại."
          : err instanceof ApiError
            ? err.message
            : "Tạo bộ video ôn tập thất bại."
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title="Tạo bộ video ôn tập mới (UC-23)" size="lg">
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
        <ContentSourceField value={content} onChange={setContent} />
        <div className="grid grid-cols-2 gap-3 items-end">
          <div>
            <label className={labelClass}>Học phần (tùy chọn)</label>
            <select value={form.subjectId} onChange={(e) => setForm({ ...form, subjectId: e.target.value })} className={inputClass}>
              <option value="">-- Không gán --</option>
              {subjects.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
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
  curriculumIdForSubjects,
  onClose,
  onSaved
}: {
  set: ReviewVideoSetResponse;
  curriculumIdForSubjects: number | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [subjects, setSubjects] = useState<CurriculumSubjectResponse[]>([]);
  const [form, setForm] = useState({
    title: set.title,
    subjectId: set.subjectId ? String(set.subjectId) : "",
    displayOrder: String(set.displayOrder),
    status: set.status
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (curriculumIdForSubjects) listCurriculumSubjects(curriculumIdForSubjects).then(setSubjects).catch(() => undefined);
  }, [curriculumIdForSubjects]);

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
        subjectId: form.subjectId ? Number(form.subjectId) : undefined,
        displayOrder: form.displayOrder ? Number(form.displayOrder) : undefined,
        status: form.status
      };
      await updateReviewVideoSet(set.id, request);
      onSaved();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Cập nhật bộ video ôn tập thất bại — có thể bạn không được phân công giảng dạy lớp/khung này.");
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
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Học phần (tùy chọn)</label>
            <select value={form.subjectId} onChange={(e) => setForm({ ...form, subjectId: e.target.value })} className={inputClass}>
              <option value="">-- Không gán --</option>
              {subjects.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className={labelClass}>Trạng thái</label>
            <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value as ReviewVideoSetStatus })} className={inputClass}>
              {Object.entries(statusLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div>
          <label className={labelClass}>Thứ tự</label>
          <input type="number" value={form.displayOrder} onChange={(e) => setForm({ ...form, displayOrder: e.target.value })} className={inputClass} />
        </div>
        <p className="text-[10px] text-slate-400 italic">
          Chuyển trạng thái sang "Đã công bố" để học sinh xem được (chỉ set 1 lần thời điểm công bố). Chuyển "Đã gỡ" (ARCHIVED) để gỡ khỏi kho —
          không xoá hẳn bản ghi.
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

function VideosModal({ set, onClose }: { set: ReviewVideoSetResponse; onClose: () => void }) {
  const [videos, setVideos] = useState<ReviewVideoResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showAddForm, setShowAddForm] = useState(false);
  const [title, setTitle] = useState("");
  const [content, setContent] = useState<ContentSourceValue>({ sourceType: "R2_VIDEO", fileUrl: "", durationSeconds: null });
  const [submitting, setSubmitting] = useState(false);
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
        displayOrder: videos.length
      });
      setTitle("");
      setContent({ sourceType: "R2_VIDEO", fileUrl: "", durationSeconds: null });
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
    <Modal open onClose={onClose} title={`Video — ${set.title}`} size="lg">
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
                </p>
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
    </Modal>
  );
}

/** UC-23a Main Flow bước 4: ma trận học sinh × video — ghép studentId trong StatsCell với tên/mã học sinh qua listClassEnrollments (BE không trả tên, chỉ trả id). */
function StatsModal({
  set,
  classes,
  onClose
}: {
  set: ReviewVideoSetResponse;
  classes: { id: number; classCode: string; name: string; curriculumId: number }[];
  onClose: () => void;
}) {
  const classesInCurriculum = set.classId == null ? classes.filter((c) => c.curriculumId === set.curriculumId) : [];
  const [classId, setClassId] = useState<number | null>(set.classId ?? classesInCurriculum[0]?.id ?? null);
  const [stats, setStats] = useState<ReviewVideoSetStatsResponse | null>(null);
  const [enrollments, setEnrollments] = useState<ClassEnrollmentResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!classId) return;
    setLoading(true);
    setError(null);
    Promise.all([getReviewVideoSetStats(set.id, set.classId == null ? classId : undefined), listClassEnrollments(classId)])
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

        {set.classId == null && (
          <div>
            <label className={labelClass}>Xem thống kê theo lớp *</label>
            <select value={classId ?? ""} onChange={(e) => setClassId(e.target.value ? Number(e.target.value) : null)} className={`${inputClass} w-64`}>
              <option value="">-- Chọn lớp --</option>
              {classesInCurriculum.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.classCode} — {c.name}
                </option>
              ))}
            </select>
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
                      return (
                        <td key={v.videoId} className={`text-center p-2 border border-slate-200 ${completed ? "bg-emerald-50 text-emerald-700 font-bold" : "text-slate-500"}`}>
                          {percent}%
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
