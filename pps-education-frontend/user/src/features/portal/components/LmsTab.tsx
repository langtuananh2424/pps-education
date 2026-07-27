import React, { useEffect, useRef, useState } from "react";
import { Download, FileText, Library, Link2, MessageCircle, Play } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import {
  CurriculumDocumentResponse,
  ReviewVideoResponse,
  ReviewVideoSetResponse,
  getPortalClass,
  listMyDocuments,
  listReviewVideoSetsByClass,
  listReviewVideos,
  reportReviewVideoProgress
} from "../api";
import ComingSoon from "./ComingSoon";

const documentTypeLabels: Record<CurriculumDocumentResponse["documentType"], string> = {
  VIDEO: "Video",
  PDF: "PDF",
  AUDIO: "Audio",
  SLIDE: "Slide",
  IMAGE: "Ảnh",
  OTHER: "Khác"
};

const videoTypeLabels: Record<ReviewVideoSetResponse["videoType"], string> = {
  CONNECTION: "Video từ kết nối",
  REFLEX: "Video phản xạ"
};
const videoTypeIcons: Record<ReviewVideoSetResponse["videoType"], React.ReactNode> = {
  CONNECTION: <Link2 size={16} />,
  REFLEX: <MessageCircle size={16} />
};

/** Ngưỡng % xem tối thiểu để tính là "đạt" — khớp ReviewVideoService.COMPLETION_THRESHOLD (0.8) ở backend. */
const WATCH_PASS_THRESHOLD_PERCENT = 80;

/** Sai số cho phép (giây) trước khi coi là hành vi tua tới — tránh false positive do độ trễ polling. */
const SEEK_TOLERANCE_SECONDS = 2;

/** Chỉ báo tiến độ lên server tối đa 1 lần mỗi khoảng này (giây thời gian thực) — tránh spam API mỗi lần poll/timeupdate. */
const PROGRESS_REPORT_INTERVAL_SECONDS = 5;

/** Nhận diện link YouTube (watch/youtu.be/shorts/embed) và trả về videoId, null nếu không phải YouTube. */
function extractYouTubeVideoId(url: string): string | null {
  try {
    const parsed = new URL(url);
    const host = parsed.hostname.replace(/^www\./, "").replace(/^m\./, "");
    if (host === "youtu.be") {
      return parsed.pathname.slice(1);
    }
    if (host === "youtube.com") {
      if (parsed.pathname === "/watch") {
        return parsed.searchParams.get("v");
      }
      if (parsed.pathname.startsWith("/embed/")) {
        return parsed.pathname.slice("/embed/".length);
      }
      if (parsed.pathname.startsWith("/shorts/")) {
        return parsed.pathname.slice("/shorts/".length);
      }
    }
    return null;
  } catch {
    return null;
  }
}

/**
 * enablejsapi=1 để nhận sự kiện onStateChange qua YouTube IFrame Player API — dùng đo % xem thật.
 * disablekb=1 chặn tua bằng phím mũi tên/Home/End; việc kéo thanh tua bằng chuột được chặn ở
 * useYouTubeWatchProgress (phát hiện nhảy thời gian về phía trước rồi seek ngược lại).
 */
function buildYouTubeEmbedSrc(videoId: string): string {
  return `https://www.youtube.com/embed/${videoId}?enablejsapi=1&rel=0&disablekb=1`;
}

/** Ảnh thumbnail công khai YouTube cung cấp sẵn theo videoId, không cần gọi API riêng. */
function getYouTubeThumbnailUrl(videoId: string): string {
  return `https://img.youtube.com/vi/${videoId}/hqdefault.jpg`;
}

let youTubeIframeApiPromise: Promise<void> | null = null;

/** Nạp script chính thức của YouTube (chỉ 1 lần cho cả trang) để có window.YT.Player điều khiển video đã nhúng. */
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

/** Video đang mở ở khung phát bên phải — reviewVideoId dùng để báo tiến độ lên server (UC-23a). */
interface ActiveVideo {
  reviewVideoId: number;
  title: string;
  sourceType: ReviewVideoResponse["sourceType"];
  fileUrl: string;
  durationSeconds: number;
}

/**
 * Theo dõi % đã xem THẬT của video YouTube đang mở, dùng mốc giây cao nhất từng xem qua (không
 * tính theo thời điểm tua tới) để tránh học sinh tua nhanh tới cuối coi như đã xem. Định kỳ báo
 * tiến độ lên server qua PUT /api/review-videos/{id}/progress (BE tự lấy max(cũ, mới), không giảm)
 * — giữ lại được kể cả khi F5, khác bản cũ chỉ lưu tạm trong state React.
 */
function useYouTubeWatchProgress(video: ActiveVideo | null) {
  const iframeId = "lms-active-video-frame";
  const [watchedPercent, setWatchedPercent] = useState(0);
  const playerRef = useRef<any>(null);
  const maxWatchedSecondsRef = useRef(0);
  const lastReportedSecondsRef = useRef(0);
  const pollRef = useRef<number | null>(null);
  const videoRef = useRef(video);
  videoRef.current = video;

  const reportProgress = (force: boolean) => {
    const current = videoRef.current;
    if (!current || current.reviewVideoId <= 0) return;
    const watched = maxWatchedSecondsRef.current;
    if (!force && watched - lastReportedSecondsRef.current < PROGRESS_REPORT_INTERVAL_SECONDS) return;
    lastReportedSecondsRef.current = watched;
    reportReviewVideoProgress(current.reviewVideoId, Math.round(watched)).catch(() => undefined);
  };

  useEffect(() => {
    maxWatchedSecondsRef.current = 0;
    lastReportedSecondsRef.current = 0;
    setWatchedPercent(0);
    const youTubeVideoId = video && video.sourceType === "YOUTUBE_URL" ? extractYouTubeVideoId(video.fileUrl) : null;
    if (!youTubeVideoId) return;

    let cancelled = false;
    loadYouTubeIframeApi().then(() => {
      if (cancelled) return;
      const YT = (window as any).YT;
      playerRef.current = new YT.Player(iframeId, {
        events: {
          onStateChange: (event: any) => {
            if (event.data === YT.PlayerState.PLAYING) {
              if (pollRef.current) window.clearInterval(pollRef.current);
              pollRef.current = window.setInterval(() => {
                const player = playerRef.current;
                const duration = player?.getDuration?.();
                const current = player?.getCurrentTime?.();
                if (!duration || current == null) return;
                const allowedMax = maxWatchedSecondsRef.current + SEEK_TOLERANCE_SECONDS;
                if (current > allowedMax) {
                  player.seekTo(maxWatchedSecondsRef.current, true);
                  return;
                }
                maxWatchedSecondsRef.current = Math.max(maxWatchedSecondsRef.current, current);
                setWatchedPercent(Math.min(100, Math.round((maxWatchedSecondsRef.current / duration) * 100)));
                reportProgress(false);
              }, 500);
            } else {
              if (pollRef.current) {
                window.clearInterval(pollRef.current);
                pollRef.current = null;
              }
              reportProgress(true);
            }
          }
        }
      });
    });

    return () => {
      cancelled = true;
      if (pollRef.current) window.clearInterval(pollRef.current);
      reportProgress(true);
      try {
        playerRef.current?.destroy?.();
      } catch {
        // Iframe cũ có thể đã bị gỡ khỏi DOM khi chuyển sang video khác — bỏ qua lỗi cleanup.
      }
      playerRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [video?.reviewVideoId, video?.sourceType, video?.fileUrl]);

  return { iframeId, watchedPercent };
}

/**
 * Bản song song của useYouTubeWatchProgress cho video/audio tải thẳng lên R2 (R2_VIDEO/R2_AUDIO)
 * — dùng sự kiện timeupdate/ended của thẻ <video>/<audio> gốc thay vì YouTube IFrame Player API
 * (2 cơ chế độc lập, không tái dùng chung được vì API hoàn toàn khác nhau).
 */
function useMediaElementWatchProgress(video: ActiveVideo | null) {
  const mediaRef = useRef<HTMLMediaElement | null>(null);
  const [watchedPercent, setWatchedPercent] = useState(0);
  const maxWatchedSecondsRef = useRef(0);
  const lastReportedSecondsRef = useRef(0);
  const videoRef = useRef(video);
  videoRef.current = video;

  const reportProgress = (force: boolean) => {
    const current = videoRef.current;
    if (!current || current.reviewVideoId <= 0) return;
    const watched = maxWatchedSecondsRef.current;
    if (!force && watched - lastReportedSecondsRef.current < PROGRESS_REPORT_INTERVAL_SECONDS) return;
    lastReportedSecondsRef.current = watched;
    reportReviewVideoProgress(current.reviewVideoId, Math.round(watched)).catch(() => undefined);
  };

  useEffect(() => {
    maxWatchedSecondsRef.current = 0;
    lastReportedSecondsRef.current = 0;
    setWatchedPercent(0);
    const isNativeMedia = video && (video.sourceType === "R2_VIDEO" || video.sourceType === "R2_AUDIO");
    if (!isNativeMedia) return;

    const media = mediaRef.current;
    if (!media) return;
    const duration = video!.durationSeconds;

    const handleTimeUpdate = () => {
      const current = media.currentTime;
      const allowedMax = maxWatchedSecondsRef.current + SEEK_TOLERANCE_SECONDS;
      if (current > allowedMax) {
        media.currentTime = maxWatchedSecondsRef.current;
        return;
      }
      maxWatchedSecondsRef.current = Math.max(maxWatchedSecondsRef.current, current);
      if (duration > 0) setWatchedPercent(Math.min(100, Math.round((maxWatchedSecondsRef.current / duration) * 100)));
      reportProgress(false);
    };
    const handleEnded = () => {
      maxWatchedSecondsRef.current = duration;
      setWatchedPercent(100);
      reportProgress(true);
    };
    const handlePause = () => reportProgress(true);

    media.addEventListener("timeupdate", handleTimeUpdate);
    media.addEventListener("ended", handleEnded);
    media.addEventListener("pause", handlePause);
    return () => {
      media.removeEventListener("timeupdate", handleTimeUpdate);
      media.removeEventListener("ended", handleEnded);
      media.removeEventListener("pause", handlePause);
      reportProgress(true);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [video?.reviewVideoId, video?.sourceType, video?.fileUrl]);

  return { mediaRef, watchedPercent };
}

interface LmsTabProps {
  classId: number;
}

export default function LmsTab({ classId }: LmsTabProps) {
  const [videoSets, setVideoSets] = useState<ReviewVideoSetResponse[]>([]);
  const [documents, setDocuments] = useState<CurriculumDocumentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [openSetId, setOpenSetId] = useState<number | null>(null);
  const [videos, setVideos] = useState<ReviewVideoResponse[]>([]);
  const [activeVideo, setActiveVideo] = useState<ActiveVideo | null>(null);

  const isYouTube = activeVideo?.sourceType === "YOUTUBE_URL";
  const { iframeId, watchedPercent: youTubeWatchedPercent } = useYouTubeWatchProgress(isYouTube ? activeVideo : null);
  const { mediaRef, watchedPercent: mediaWatchedPercent } = useMediaElementWatchProgress(!isYouTube ? activeVideo : null);
  const watchedPercent = isYouTube ? youTubeWatchedPercent : mediaWatchedPercent;
  const youTubeVideoId = isYouTube ? extractYouTubeVideoId(activeVideo!.fileUrl) : null;

  useEffect(() => {
    setLoading(true);
    Promise.all([
      listReviewVideoSetsByClass(classId).then((sets) => sets.sort((a, b) => a.displayOrder - b.displayOrder)),
      getPortalClass(classId).then((cls) => listMyDocuments(cls.curriculumId))
    ])
      .then(([setRes, documentRes]) => {
        setVideoSets(setRes);
        setDocuments(documentRes);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được kho video ôn tập."))
      .finally(() => setLoading(false));
  }, [classId]);

  const handleOpenSet = (setId: number) => {
    if (openSetId === setId) {
      setOpenSetId(null);
      return;
    }
    setOpenSetId(setId);
    listReviewVideos(setId)
      .then(setVideos)
      .catch(() => setVideos([]));
  };

  if (loading) return <p className="text-sm text-muted font-bold">Đang tải...</p>;

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
      <div className="lg:col-span-2 space-y-6">
      {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

      <div className="bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-4">
        <h2 className="text-xl font-extrabold text-ink flex items-center gap-2">
          <Play className="text-teal" /> Kho Video Ôn tập
        </h2>
        {videoSets.length === 0 ? (
          <p className="text-xs text-muted font-bold italic">Lớp này chưa có bộ video ôn tập nào được công bố.</p>
        ) : (
          <div className="space-y-3">
            {videoSets.map((set) => (
              <div key={set.id} className="border border-line/80 rounded-[16px] overflow-hidden">
                <button
                  onClick={() => handleOpenSet(set.id)}
                  className="w-full flex items-center gap-4 p-4 bg-sky-2 hover:bg-sky text-left"
                >
                  <div className="w-10 h-10 rounded-full bg-teal/10 border border-teal/20 flex items-center justify-center text-teal shrink-0">
                    {videoTypeIcons[set.videoType]}
                  </div>
                  <div className="flex-1">
                    <p className="font-extrabold text-ink text-sm">{set.title}</p>
                    <p className="text-[10px] text-muted font-bold">{videoTypeLabels[set.videoType]}</p>
                  </div>
                </button>
                {openSetId === set.id && (
                  <div className="p-4 space-y-2 bg-white">
                    {videos.length === 0 ? (
                      <p className="text-xs text-muted font-bold italic">Bộ này chưa có video nào.</p>
                    ) : (
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                        {videos.map((v) => {
                          const youtubeVideoId = v.sourceType === "YOUTUBE_URL" ? extractYouTubeVideoId(v.fileUrl) : null;
                          const youtubeThumbnailUrl = youtubeVideoId ? getYouTubeThumbnailUrl(youtubeVideoId) : null;
                          return (
                            <button
                              key={v.id}
                              type="button"
                              onClick={() =>
                                setActiveVideo({
                                  reviewVideoId: v.id,
                                  title: v.title,
                                  sourceType: v.sourceType,
                                  fileUrl: v.fileUrl,
                                  durationSeconds: v.durationSeconds
                                })
                              }
                              className="flex flex-col rounded-[16px] border border-line/80 bg-sky-2 hover:bg-sky transition-colors overflow-hidden text-left"
                            >
                              <div className="relative w-full h-24 bg-teal/10">
                                {youtubeThumbnailUrl && <img src={youtubeThumbnailUrl} alt="" className="w-full h-24 object-cover" />}
                                <div className="absolute inset-0 flex items-center justify-center bg-ink/10">
                                  <div className="w-9 h-9 rounded-full bg-white/90 flex items-center justify-center text-teal-deep shadow">
                                    <Play size={16} className="ml-0.5" />
                                  </div>
                                </div>
                              </div>
                              <div className="p-3 flex-1 min-w-0 space-y-1">
                                <p className="font-extrabold text-ink text-sm truncate">{v.title}</p>
                                <p className="text-[10px] text-muted font-bold">{Math.round(v.durationSeconds / 60)} phút</p>
                              </div>
                            </button>
                          );
                        })}
                      </div>
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-4">
        <h2 className="text-xl font-extrabold text-ink flex items-center gap-2">
          <Library className="text-teal" /> Tài liệu tham khảo
        </h2>
        <p className="text-xs text-muted font-bold">Tài liệu chung theo khung chương trình để tự học thêm — không thuộc bộ video nào cụ thể.</p>
        {documents.length === 0 ? (
          <p className="text-xs text-muted font-bold italic">Chưa có tài liệu tham khảo nào.</p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {documents.map((doc) => {
              const youtubeVideoId = doc.documentType === "VIDEO" ? extractYouTubeVideoId(doc.fileUrl) : null;
              const thumbnailUrl = doc.coverImageUrl ?? (youtubeVideoId ? getYouTubeThumbnailUrl(youtubeVideoId) : null);
              const cardBody = (
                <>
                  <div className="relative w-full h-28 bg-teal/10 flex items-center justify-center text-teal overflow-hidden">
                    {thumbnailUrl ? <img src={thumbnailUrl} alt="" className="w-full h-28 object-cover" /> : <FileText size={28} />}
                    {youtubeVideoId && (
                      <div className="absolute inset-0 flex items-center justify-center bg-ink/10">
                        <div className="w-9 h-9 rounded-full bg-white/90 flex items-center justify-center text-teal-deep shadow">
                          <Play size={16} className="ml-0.5" />
                        </div>
                      </div>
                    )}
                  </div>
                  <div className="p-3.5 flex-1 min-w-0 space-y-1">
                    <p className="font-extrabold text-ink text-sm truncate">{doc.title}</p>
                    {doc.description && <p className="text-[10px] text-muted font-bold line-clamp-2">{doc.description}</p>}
                  </div>
                  <div className="px-3.5 pb-3 flex items-center justify-between">
                    <span className="text-[10px] font-extrabold text-teal-deep uppercase">{documentTypeLabels[doc.documentType]}</span>
                  </div>
                </>
              );

              if (youtubeVideoId) {
                return (
                  <button
                    key={doc.id}
                    type="button"
                    onClick={() =>
                      setActiveVideo({ reviewVideoId: -doc.id, title: doc.title, sourceType: "YOUTUBE_URL", fileUrl: doc.fileUrl, durationSeconds: 0 })
                    }
                    className="flex flex-col rounded-[16px] border border-line/80 bg-sky-2 hover:bg-sky transition-colors overflow-hidden text-left"
                  >
                    {cardBody}
                  </button>
                );
              }
              return (
                <a
                  key={doc.id}
                  href={doc.fileUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="flex flex-col rounded-[16px] border border-line/80 bg-sky-2 hover:bg-sky transition-colors overflow-hidden"
                >
                  {cardBody}
                </a>
              );
            })}
          </div>
        )}
      </div>

      <ComingSoon
        title="Luyện Nghe-Nói & Đổi xu thưởng (UC-26)"
        description="Đang trong quá trình phát triển"
      />
      </div>

      <div className="lg:sticky lg:top-6 bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-4">
        {activeVideo ? (
          <>
            <h3 className="text-lg font-extrabold text-ink">{activeVideo.title}</h3>
            <div className="aspect-video w-full rounded-[12px] overflow-hidden bg-ink">
              {isYouTube && youTubeVideoId ? (
                <iframe
                  key={activeVideo.reviewVideoId}
                  id={iframeId}
                  src={buildYouTubeEmbedSrc(youTubeVideoId)}
                  title={activeVideo.title}
                  className="w-full h-full"
                  allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                  allowFullScreen
                />
              ) : activeVideo.sourceType === "R2_AUDIO" ? (
                <div className="w-full h-full flex items-center justify-center p-6">
                  <audio key={activeVideo.reviewVideoId} ref={mediaRef as React.RefObject<HTMLAudioElement>} src={activeVideo.fileUrl} controls className="w-full" />
                </div>
              ) : (
                <video
                  key={activeVideo.reviewVideoId}
                  ref={mediaRef as React.RefObject<HTMLVideoElement>}
                  src={activeVideo.fileUrl}
                  controls
                  controlsList="nodownload"
                  className="w-full h-full"
                />
              )}
            </div>
            {activeVideo.reviewVideoId > 0 && (
              <div className="bg-sky-2 border border-teal/20 rounded-[14px] p-4 space-y-2">
                <p className="text-[10px] font-extrabold text-teal-deep uppercase tracking-wide">Đang học bài</p>
                <div className="space-y-1">
                  <div className="flex items-center justify-between text-[10px] font-extrabold text-teal-deep">
                    <span>Đã xem tối đa</span>
                    <span>{watchedPercent}%</span>
                  </div>
                  <div className="h-1.5 w-full rounded-full bg-white overflow-hidden">
                    <div
                      className={`h-full rounded-full transition-all ${watchedPercent >= WATCH_PASS_THRESHOLD_PERCENT ? "bg-emerald-500" : "bg-teal-deep"}`}
                      style={{ width: `${watchedPercent}%` }}
                    />
                  </div>
                  <p className={`text-[10px] font-extrabold ${watchedPercent >= WATCH_PASS_THRESHOLD_PERCENT ? "text-emerald-600" : "text-amber-600"}`}>
                    {watchedPercent >= WATCH_PASS_THRESHOLD_PERCENT
                      ? `✓ Đạt yêu cầu ôn tập (đã xem ≥ ${WATCH_PASS_THRESHOLD_PERCENT}%)`
                      : `Cần xem ít nhất ${WATCH_PASS_THRESHOLD_PERCENT}% để đạt yêu cầu ôn tập`}
                  </p>
                </div>
              </div>
            )}
          </>
        ) : (
          <div className="flex flex-col items-center justify-center text-center py-10 gap-3">
            <Play size={40} className="text-line" />
            <p className="font-extrabold text-ink text-sm">Chưa có video nào được mở</p>
            <p className="text-xs text-muted font-bold">Hãy bấm vào một video ở kho để bắt đầu hành trình tự học nha!</p>
          </div>
        )}
      </div>
    </div>
  );
}
