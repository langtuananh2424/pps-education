import React, { useEffect, useRef, useState } from "react";
import { CheckCircle2, Mic, Square, Upload, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import {
  ReviewVideoResponse,
  ReviewVideoSubmissionResponse,
  getMyReviewVideoSubmission,
  reportReviewVideoProgress,
  submitReviewVideoAudio,
  uploadMedia
} from "../api";

/** Ngưỡng % xem tối thiểu để tính là "đạt" (chỉ áp dụng cho CONNECTION) — khớp ReviewVideoService.COMPLETION_THRESHOLD (0.8). */
const WATCH_PASS_THRESHOLD_PERCENT = 80;
const SEEK_TOLERANCE_SECONDS = 2;
const PROGRESS_REPORT_INTERVAL_SECONDS = 5;

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

function buildYouTubeEmbedSrc(videoId: string): string {
  return `https://www.youtube.com/embed/${videoId}?enablejsapi=1&rel=0&disablekb=1`;
}

let youTubeIframeApiPromise: Promise<void> | null = null;

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

/**
 * Theo dõi % đã xem THẬT (mốc giây cao nhất từng xem qua, chặn tua tới) — CHỈ dùng cho CONNECTION
 * (video ôn tập thụ động, "đạt" = xem ≥ 80%). REFLEX không dùng hook này vì "đạt" của REFLEX là nộp
 * được audio trả lời, không phải xem hết video hỏi-đáp.
 */
function useYouTubeWatchProgress(video: ReviewVideoResponse | null) {
  const iframeId = "homework-review-video-frame";
  const [watchedPercent, setWatchedPercent] = useState(0);
  const playerRef = useRef<any>(null);
  const maxWatchedSecondsRef = useRef(0);
  const lastReportedSecondsRef = useRef(0);
  const pollRef = useRef<number | null>(null);

  const reportProgress = (force: boolean) => {
    if (!video) return;
    const watched = maxWatchedSecondsRef.current;
    if (!force && watched - lastReportedSecondsRef.current < PROGRESS_REPORT_INTERVAL_SECONDS) return;
    lastReportedSecondsRef.current = watched;
    reportReviewVideoProgress(video.id, Math.round(watched)).catch(() => undefined);
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
        // Iframe cũ có thể đã bị gỡ khỏi DOM — bỏ qua lỗi cleanup.
      }
      playerRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [video?.id, video?.sourceType, video?.fileUrl]);

  return { iframeId, watchedPercent };
}

function useMediaElementWatchProgress(video: ReviewVideoResponse | null) {
  const mediaRef = useRef<HTMLMediaElement | null>(null);
  const [watchedPercent, setWatchedPercent] = useState(0);
  const maxWatchedSecondsRef = useRef(0);
  const lastReportedSecondsRef = useRef(0);

  const reportProgress = (force: boolean) => {
    if (!video) return;
    const watched = maxWatchedSecondsRef.current;
    if (!force && watched - lastReportedSecondsRef.current < PROGRESS_REPORT_INTERVAL_SECONDS) return;
    lastReportedSecondsRef.current = watched;
    reportReviewVideoProgress(video.id, Math.round(watched)).catch(() => undefined);
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
  }, [video?.id, video?.sourceType, video?.fileUrl]);

  return { mediaRef, watchedPercent };
}

/** Ghi âm trực tiếp qua microphone trình duyệt (MediaRecorder API) — dùng cho nộp audio trả lời REFLEX (UC-23b). */
function useAudioRecorder() {
  const [recording, setRecording] = useState(false);
  const [audioBlob, setAudioBlob] = useState<Blob | null>(null);
  const [error, setError] = useState<string | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const streamRef = useRef<MediaStream | null>(null);

  const start = async () => {
    setError(null);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      streamRef.current = stream;
      chunksRef.current = [];
      const recorder = new MediaRecorder(stream);
      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data);
      };
      recorder.onstop = () => {
        setAudioBlob(new Blob(chunksRef.current, { type: recorder.mimeType || "audio/webm" }));
        streamRef.current?.getTracks().forEach((t) => t.stop());
        streamRef.current = null;
      };
      recorder.start();
      recorderRef.current = recorder;
      setRecording(true);
    } catch {
      setError("Không truy cập được microphone — kiểm tra quyền trình duyệt rồi thử lại.");
    }
  };

  const stop = () => {
    recorderRef.current?.stop();
    setRecording(false);
  };

  const reset = () => setAudioBlob(null);

  useEffect(
    () => () => {
      streamRef.current?.getTracks().forEach((t) => t.stop());
    },
    []
  );

  return { recording, audioBlob, error, start, stop, reset };
}

interface ReviewVideoTaskModalProps {
  video: ReviewVideoResponse;
  videoType: "CONNECTION" | "REFLEX";
  onClose: () => void;
  onSubmitted?: () => void;
}

/**
 * UC-23a (CONNECTION — xem video, theo dõi % xem) + UC-23b (REFLEX — nghe câu hỏi rồi ghi âm/tải
 * file audio trả lời, nộp lên chấm điểm). Mở từ tab "Bài tập về nhà" (đã gộp Kho Video Ôn tập vào
 * đây theo yêu cầu người dùng 2026-07-27 — không còn hiển thị riêng ở E-Learning & LMS).
 */
export default function ReviewVideoTaskModal({ video, videoType, onClose, onSubmitted }: ReviewVideoTaskModalProps) {
  const isYouTube = video.sourceType === "YOUTUBE_URL";
  const { iframeId, watchedPercent: youTubeWatchedPercent } = useYouTubeWatchProgress(videoType === "CONNECTION" && isYouTube ? video : null);
  const { mediaRef, watchedPercent: mediaWatchedPercent } = useMediaElementWatchProgress(videoType === "CONNECTION" && !isYouTube ? video : null);
  const watchedPercent = isYouTube ? youTubeWatchedPercent : mediaWatchedPercent;
  const youTubeVideoId = isYouTube ? extractYouTubeVideoId(video.fileUrl) : null;

  const [submission, setSubmission] = useState<ReviewVideoSubmissionResponse | undefined>(undefined);
  const [loadingSubmission, setLoadingSubmission] = useState(videoType === "REFLEX");
  const [pickedFile, setPickedFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const recorder = useAudioRecorder();

  useEffect(() => {
    if (videoType !== "REFLEX") return;
    setLoadingSubmission(true);
    getMyReviewVideoSubmission(video.id)
      .then(setSubmission)
      .catch(() => setSubmission(undefined))
      .finally(() => setLoadingSubmission(false));
  }, [video.id, videoType]);

  const answerBlob = recorder.audioBlob ?? pickedFile;
  const answerPreviewUrl = answerBlob ? URL.createObjectURL(answerBlob) : null;
  useEffect(() => () => { if (answerPreviewUrl) URL.revokeObjectURL(answerPreviewUrl); }, [answerPreviewUrl]);

  const handleDiscardDraft = () => {
    recorder.reset();
    setPickedFile(null);
  };

  const handleSubmitAnswer = async () => {
    if (!answerBlob) return;
    setSubmitting(true);
    setError(null);
    try {
      const file = answerBlob instanceof File ? answerBlob : new File([answerBlob], "reflex-answer.webm", { type: answerBlob.type || "audio/webm" });
      const { url } = await uploadMedia(file, "REVIEW_VIDEO_SUBMISSION");
      const updated = await submitReviewVideoAudio(video.id, url);
      setSubmission(updated);
      handleDiscardDraft();
      onSubmitted?.();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Nộp bài thất bại — thử lại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-ink/40 backdrop-blur-sm z-[100] flex items-center justify-center p-4" onClick={onClose}>
      <div className="bg-white rounded-[24px] max-w-2xl w-full max-h-[92vh] overflow-y-auto shadow-2xl p-6 space-y-4" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-start justify-between gap-3">
          <div>
            <span className="text-[10px] font-extrabold uppercase text-teal-deep tracking-wide">
              {videoType === "CONNECTION" ? "Video từ kết nối" : "Video phản xạ"}
            </span>
            <h3 className="text-lg font-extrabold text-ink">{video.title}</h3>
          </div>
          <button onClick={onClose} className="w-8 h-8 shrink-0 rounded-full bg-sky-2 hover:bg-sky flex items-center justify-center text-ink transition-colors" aria-label="Đóng">
            <X size={16} />
          </button>
        </div>

        <div className="aspect-video w-full rounded-[12px] overflow-hidden bg-ink">
          {isYouTube && youTubeVideoId ? (
            <iframe
              key={video.id}
              id={iframeId}
              src={buildYouTubeEmbedSrc(youTubeVideoId)}
              title={video.title}
              className="w-full h-full"
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
              allowFullScreen
            />
          ) : video.sourceType === "R2_AUDIO" ? (
            <div className="w-full h-full flex items-center justify-center p-6">
              <audio key={video.id} ref={mediaRef as React.RefObject<HTMLAudioElement>} src={video.fileUrl} controls className="w-full" />
            </div>
          ) : (
            <video key={video.id} ref={mediaRef as React.RefObject<HTMLVideoElement>} src={video.fileUrl} controls controlsList="nodownload" className="w-full h-full" />
          )}
        </div>

        {videoType === "CONNECTION" && (
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

        {videoType === "REFLEX" && (
          <div className="space-y-3">
            {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}
            {recorder.error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{recorder.error}</div>}

            {loadingSubmission ? (
              <p className="text-xs text-muted font-bold">Đang tải bài nộp...</p>
            ) : (
              <div className="bg-sky-2 border border-teal/20 rounded-[14px] p-4 space-y-3">
                <p className="text-[10px] font-extrabold text-teal-deep uppercase tracking-wide">Bài trả lời của bạn</p>

                {submission && (
                  <div className="space-y-2">
                    <audio src={submission.audioUrl} controls className="w-full" />
                    <p className="text-[10px] text-muted font-bold">Đã nộp lúc {new Date(submission.submittedAt).toLocaleString("vi-VN")}</p>
                    {submission.score != null ? (
                      <div className="flex items-center gap-2">
                        <CheckCircle2 size={14} className="text-emerald-600" />
                        <span className="text-sm font-black text-emerald-700">
                          Điểm: {submission.score}/{submission.maxScore}
                        </span>
                      </div>
                    ) : (
                      <span className="inline-block text-[10px] font-extrabold uppercase px-2.5 py-1 rounded-full bg-gold/10 text-gold">Chờ giáo viên chấm</span>
                    )}
                    {submission.feedback && <p className="text-xs text-ink/90 italic bg-white rounded-xl border border-line/60 p-2.5">"{submission.feedback}"</p>}
                  </div>
                )}

                {answerBlob ? (
                  <div className="space-y-2 pt-2 border-t border-teal/20">
                    <p className="text-[10px] font-extrabold text-teal-deep uppercase">Bản ghi mới — nghe lại trước khi nộp</p>
                    {answerPreviewUrl && <audio src={answerPreviewUrl} controls className="w-full" />}
                    <div className="flex gap-2">
                      <button
                        onClick={handleDiscardDraft}
                        className="flex-1 px-3 py-2 bg-white hover:bg-slate-100 border border-line rounded-xl text-xs font-bold text-ink"
                      >
                        Ghi lại
                      </button>
                      <button
                        onClick={handleSubmitAnswer}
                        disabled={submitting}
                        className="flex-1 flex items-center justify-center gap-1.5 px-3 py-2 bg-teal hover:bg-teal-deep text-white rounded-xl text-xs font-extrabold disabled:opacity-50"
                      >
                        {submitting ? "Đang nộp..." : submission ? "Nộp lại bài" : "Nộp bài"}
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="flex flex-wrap gap-2 pt-2 border-t border-teal/20">
                    {recorder.recording ? (
                      <button
                        onClick={recorder.stop}
                        className="flex items-center gap-1.5 px-3.5 py-2 bg-coral text-white rounded-xl text-xs font-extrabold animate-pulse"
                      >
                        <Square size={13} /> Dừng ghi âm
                      </button>
                    ) : (
                      <button
                        onClick={recorder.start}
                        className="flex items-center gap-1.5 px-3.5 py-2 bg-teal hover:bg-teal-deep text-white rounded-xl text-xs font-extrabold"
                      >
                        <Mic size={13} /> Ghi âm trả lời
                      </button>
                    )}
                    <label className="flex items-center gap-1.5 px-3.5 py-2 bg-white hover:bg-slate-100 border border-line rounded-xl text-xs font-bold text-ink cursor-pointer">
                      <Upload size={13} className="text-teal" /> Tải file ghi âm lên
                      <input
                        type="file"
                        accept="audio/*"
                        className="hidden"
                        onChange={(e) => {
                          const file = e.target.files?.[0];
                          e.target.value = "";
                          if (file) setPickedFile(file);
                        }}
                      />
                    </label>
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
