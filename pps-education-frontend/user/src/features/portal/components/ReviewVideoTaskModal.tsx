import React, { useEffect, useRef, useState } from "react";
import { CheckCircle2, Clock, History, Mic, Square, Upload, X } from "lucide-react";
import { friendlyApiErrorMessage } from "@/lib/apiClient";
import {
  ReviewVideoQuestionResponse,
  ReviewVideoResponse,
  ReviewVideoSubmissionResponse,
  getMyLatestReviewVideoSubmission,
  listMyReviewVideoSubmissionHistory,
  listReviewVideoQuestions,
  reportReviewVideoProgress,
  startReviewVideoWatchSession,
  submitReviewVideoQuestionAudio,
  uploadMedia
} from "../api";

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

function formatTimestamp(totalSeconds: number): string {
  const m = Math.floor(totalSeconds / 60);
  const s = totalSeconds % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
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
 * Theo dõi % đã xem THẬT (mốc giây cao nhất từng xem qua TRONG lượt xem hiện tại, chặn tua tới) — CHỈ
 * dùng cho CONNECTION. V59: mỗi lần mở video là 1 "lượt xem" (watchSessionId) riêng — phải startWatchSession
 * lấy sessionId TRƯỚC khi report, không còn ghi thẳng vào watermark suốt đời như cũ.
 */
function useYouTubeWatchProgress(video: ReviewVideoResponse | null, watchSessionId: number | null, onProgress: (r: Awaited<ReturnType<typeof reportReviewVideoProgress>>) => void) {
  const iframeId = "homework-review-video-frame";
  const [watchedPercent, setWatchedPercent] = useState(0);
  const playerRef = useRef<any>(null);
  const maxWatchedSecondsRef = useRef(0);
  const lastReportedSecondsRef = useRef(0);
  const pollRef = useRef<number | null>(null);

  const reportProgress = (force: boolean) => {
    if (!video || watchSessionId == null) return;
    const watched = maxWatchedSecondsRef.current;
    if (!force && watched - lastReportedSecondsRef.current < PROGRESS_REPORT_INTERVAL_SECONDS) return;
    lastReportedSecondsRef.current = watched;
    reportReviewVideoProgress(video.id, watchSessionId, Math.round(watched)).then(onProgress).catch(() => undefined);
  };

  useEffect(() => {
    maxWatchedSecondsRef.current = 0;
    lastReportedSecondsRef.current = 0;
    setWatchedPercent(0);
    const youTubeVideoId = video && video.sourceType === "YOUTUBE_URL" ? extractYouTubeVideoId(video.fileUrl) : null;
    if (!youTubeVideoId || watchSessionId == null) return;

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
  }, [video?.id, video?.sourceType, video?.fileUrl, watchSessionId]);

  return { iframeId, watchedPercent };
}

function useMediaElementWatchProgress(video: ReviewVideoResponse | null, watchSessionId: number | null, onProgress: (r: Awaited<ReturnType<typeof reportReviewVideoProgress>>) => void) {
  const mediaRef = useRef<HTMLMediaElement | null>(null);
  const [watchedPercent, setWatchedPercent] = useState(0);
  const maxWatchedSecondsRef = useRef(0);
  const lastReportedSecondsRef = useRef(0);

  const reportProgress = (force: boolean) => {
    if (!video || watchSessionId == null) return;
    const watched = maxWatchedSecondsRef.current;
    if (!force && watched - lastReportedSecondsRef.current < PROGRESS_REPORT_INTERVAL_SECONDS) return;
    lastReportedSecondsRef.current = watched;
    reportReviewVideoProgress(video.id, watchSessionId, Math.round(watched)).then(onProgress).catch(() => undefined);
  };

  useEffect(() => {
    maxWatchedSecondsRef.current = 0;
    lastReportedSecondsRef.current = 0;
    setWatchedPercent(0);
    const isNativeMedia = video && (video.sourceType === "R2_VIDEO" || video.sourceType === "R2_AUDIO");
    if (!isNativeMedia || watchSessionId == null) return;

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
  }, [video?.id, video?.sourceType, video?.fileUrl, watchSessionId]);

  return { mediaRef, watchedPercent };
}

/**
 * Player YouTube CHỈ để tua tới mốc thời gian khi bấm vào câu hỏi REFLEX — không theo dõi/báo tiến độ
 * xem (REFLEX không có khái niệm "đạt" theo % xem như CONNECTION). Dùng chung cùng cơ chế nạp iframe
 * API với useYouTubeWatchProgress, nhưng chỉ tạo player để gọi seekTo, không đăng ký onStateChange.
 */
function useYouTubeSeekPlayer(iframeId: string, enabled: boolean) {
  const playerRef = useRef<any>(null);
  const readyRef = useRef(false);

  useEffect(() => {
    readyRef.current = false;
    if (!enabled) return;
    let cancelled = false;
    loadYouTubeIframeApi().then(() => {
      if (cancelled) return;
      const YT = (window as any).YT;
      playerRef.current = new YT.Player(iframeId, {
        events: {
          onReady: () => {
            readyRef.current = true;
          }
        }
      });
    });
    return () => {
      cancelled = true;
      readyRef.current = false;
      try {
        playerRef.current?.destroy?.();
      } catch {
        // Iframe cũ có thể đã bị gỡ khỏi DOM — bỏ qua lỗi cleanup.
      }
      playerRef.current = null;
    };
  }, [iframeId, enabled]);

  const seekTo = (seconds: number) => {
    if (!readyRef.current) return;
    playerRef.current?.seekTo?.(seconds, true);
    playerRef.current?.playVideo?.();
  };

  return { seekTo };
}

/** Ghi âm trực tiếp qua microphone trình duyệt (MediaRecorder API) — dùng cho nộp audio trả lời REFLEX (UC-23b). maxSeconds: tự dừng khi chạm giới hạn thời lượng riêng của câu hỏi. */
function useAudioRecorder(maxSeconds: number) {
  const [recording, setRecording] = useState(false);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [audioBlob, setAudioBlob] = useState<Blob | null>(null);
  const [error, setError] = useState<string | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const streamRef = useRef<MediaStream | null>(null);
  const timerRef = useRef<number | null>(null);

  const stop = () => {
    recorderRef.current?.stop();
    setRecording(false);
    if (timerRef.current) {
      window.clearInterval(timerRef.current);
      timerRef.current = null;
    }
  };

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
      setElapsedSeconds(0);
      const startedAt = Date.now();
      timerRef.current = window.setInterval(() => {
        const elapsed = Math.round((Date.now() - startedAt) / 1000);
        setElapsedSeconds(elapsed);
        if (elapsed >= maxSeconds) stop();
      }, 500);
    } catch {
      setError("Không truy cập được microphone — kiểm tra quyền trình duyệt rồi thử lại.");
    }
  };

  const reset = () => setAudioBlob(null);

  useEffect(
    () => () => {
      streamRef.current?.getTracks().forEach((t) => t.stop());
      if (timerRef.current) window.clearInterval(timerRef.current);
    },
    []
  );

  return { recording, elapsedSeconds, audioBlob, error, start, stop, reset };
}

interface ReviewVideoTaskModalProps {
  video: ReviewVideoResponse;
  videoType: "CONNECTION" | "REFLEX";
  onClose: () => void;
  onSubmitted?: () => void;
}

/**
 * UC-23a (CONNECTION — xem video, theo dõi từng LƯỢT xem, V59) + UC-23b (REFLEX — nhiều câu hỏi gắn
 * mốc thời gian trong 1 video, mỗi câu ghi âm/nộp riêng, V57). Mở từ tab "Bài tập về nhà" (đã gộp Kho
 * Video Ôn tập vào đây theo yêu cầu người dùng 2026-07-27 — không còn hiển thị riêng ở E-Learning & LMS).
 */
export default function ReviewVideoTaskModal({ video, videoType, onClose, onSubmitted }: ReviewVideoTaskModalProps) {
  const isYouTube = video.sourceType === "YOUTUBE_URL";
  const youTubeVideoId = isYouTube ? extractYouTubeVideoId(video.fileUrl) : null;

  // V59: phải mở 1 "lượt xem" mới TRƯỚC khi báo tiến độ — chỉ áp dụng CONNECTION.
  const [watchSessionId, setWatchSessionId] = useState<number | null>(null);
  const [progressSummary, setProgressSummary] = useState<{ viewCount: number; requiredViewCount: number; completed: boolean } | null>(null);

  useEffect(() => {
    if (videoType !== "CONNECTION") return;
    setWatchSessionId(null);
    setProgressSummary(null);
    startReviewVideoWatchSession(video.id)
      .then((r) => setWatchSessionId(r.sessionId))
      .catch(() => undefined);
  }, [video.id, videoType]);

  const handleProgress = (r: { viewCount: number; requiredViewCount: number; completed: boolean }) =>
    setProgressSummary({ viewCount: r.viewCount, requiredViewCount: r.requiredViewCount, completed: r.completed });

  const { iframeId, watchedPercent: youTubeWatchedPercent } = useYouTubeWatchProgress(
    videoType === "CONNECTION" && isYouTube ? video : null,
    watchSessionId,
    handleProgress
  );
  const { mediaRef, watchedPercent: mediaWatchedPercent } = useMediaElementWatchProgress(
    videoType === "CONNECTION" && !isYouTube ? video : null,
    watchSessionId,
    handleProgress
  );
  const watchedPercent = isYouTube ? youTubeWatchedPercent : mediaWatchedPercent;
  const sessionQualified = watchedPercent >= video.completionThresholdPercent;

  const [questions, setQuestions] = useState<ReviewVideoQuestionResponse[]>([]);
  const [loadingQuestions, setLoadingQuestions] = useState(videoType === "REFLEX");
  const [questionsError, setQuestionsError] = useState<string | null>(null);
  const reflexMediaRef = useRef<HTMLMediaElement | null>(null);

  useEffect(() => {
    if (videoType !== "REFLEX") return;
    setLoadingQuestions(true);
    setQuestionsError(null);
    listReviewVideoQuestions(video.id)
      .then((qs) => setQuestions(qs.slice().sort((a, b) => a.displayOrder - b.displayOrder)))
      .catch((err) => setQuestionsError(friendlyApiErrorMessage(err, "Không tải được danh sách câu hỏi.")))
      .finally(() => setLoadingQuestions(false));
  }, [video.id, videoType]);

  const { seekTo: seekYouTubeReflex } = useYouTubeSeekPlayer(iframeId, videoType === "REFLEX" && isYouTube);

  const handleSeekReflexMedia = (timestampSeconds: number) => {
    if (isYouTube) {
      seekYouTubeReflex(timestampSeconds);
    } else if (reflexMediaRef.current) {
      reflexMediaRef.current.currentTime = timestampSeconds;
      reflexMediaRef.current.play?.().catch(() => undefined);
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
              <audio
                key={video.id}
                ref={(videoType === "CONNECTION" ? mediaRef : reflexMediaRef) as React.RefObject<HTMLAudioElement>}
                src={video.fileUrl}
                controls
                className="w-full"
              />
            </div>
          ) : (
            <video
              key={video.id}
              ref={(videoType === "CONNECTION" ? mediaRef : reflexMediaRef) as React.RefObject<HTMLVideoElement>}
              src={video.fileUrl}
              controls
              controlsList="nodownload"
              className="w-full h-full"
            />
          )}
        </div>

        {videoType === "CONNECTION" && (
          <div className="bg-sky-2 border border-teal/20 rounded-[14px] p-4 space-y-3">
            <p className="text-[10px] font-extrabold text-teal-deep uppercase tracking-wide">Lượt xem hiện tại</p>
            <div className="space-y-1">
              <div className="flex items-center justify-between text-[10px] font-extrabold text-teal-deep">
                <span>Đã xem tối đa</span>
                <span>{watchedPercent}%</span>
              </div>
              <div className="h-1.5 w-full rounded-full bg-white overflow-hidden">
                <div
                  className={`h-full rounded-full transition-all ${sessionQualified ? "bg-emerald-500" : "bg-teal-deep"}`}
                  style={{ width: `${watchedPercent}%` }}
                />
              </div>
              <p className={`text-[10px] font-extrabold ${sessionQualified ? "text-emerald-600" : "text-amber-600"}`}>
                {sessionQualified
                  ? `✓ Lượt này đã đạt (đã xem ≥ ${video.completionThresholdPercent}%)`
                  : `Cần xem ít nhất ${video.completionThresholdPercent}% trong lượt này để được tính "đạt"`}
              </p>
            </div>
            <div className="pt-2 border-t border-teal/20 flex items-center justify-between">
              <span className="text-[10px] font-extrabold text-teal-deep uppercase">Tổng số lượt đã đạt</span>
              <span className={`text-xs font-black ${progressSummary?.completed ? "text-emerald-600" : "text-ink"}`}>
                {progressSummary ? `${progressSummary.viewCount}/${progressSummary.requiredViewCount}` : `—/${video.requiredViewCount}`} lượt
                {progressSummary?.completed && " ✓"}
              </span>
            </div>
          </div>
        )}

        {videoType === "REFLEX" && (
          <div className="space-y-3">
            {questionsError && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{questionsError}</div>}
            {loadingQuestions ? (
              <p className="text-xs text-muted font-bold">Đang tải câu hỏi...</p>
            ) : questions.length === 0 ? (
              <p className="text-xs text-muted font-bold italic">Video này chưa có câu hỏi nào.</p>
            ) : (
              questions.map((q, i) => <ReflexQuestionCard key={q.id} index={i + 1} question={q} onSeek={handleSeekReflexMedia} />)
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function ReflexQuestionCard({
  index,
  question,
  onSeek
}: {
  index: number;
  question: ReviewVideoQuestionResponse;
  onSeek: (timestampSeconds: number) => void;
}) {
  const [submission, setSubmission] = useState<ReviewVideoSubmissionResponse | undefined>(undefined);
  const [loadingSubmission, setLoadingSubmission] = useState(true);
  const [history, setHistory] = useState<ReviewVideoSubmissionResponse[] | null>(null);
  const [showHistory, setShowHistory] = useState(false);
  const [pickedFile, setPickedFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [justSubmitted, setJustSubmitted] = useState(false);
  const recorder = useAudioRecorder(question.maxRecordingSeconds);

  useEffect(() => {
    if (!justSubmitted) return;
    const timer = window.setTimeout(() => setJustSubmitted(false), 3000);
    return () => window.clearTimeout(timer);
  }, [justSubmitted]);

  useEffect(() => {
    setLoadingSubmission(true);
    getMyLatestReviewVideoSubmission(question.id)
      .then(setSubmission)
      .catch(() => setSubmission(undefined))
      .finally(() => setLoadingSubmission(false));
  }, [question.id]);

  const attemptsUsed = submission?.attemptNumber ?? 0;
  const attemptsExhausted = question.maxAttempts != null && attemptsUsed >= question.maxAttempts;

  const answerBlob = recorder.audioBlob ?? pickedFile;
  const answerPreviewUrl = answerBlob ? URL.createObjectURL(answerBlob) : null;
  useEffect(() => () => { if (answerPreviewUrl) URL.revokeObjectURL(answerPreviewUrl); }, [answerPreviewUrl]);

  const handleDiscardDraft = () => {
    recorder.reset();
    setPickedFile(null);
  };

  const handleToggleHistory = () => {
    if (!showHistory && history == null) {
      listMyReviewVideoSubmissionHistory(question.id).then(setHistory).catch(() => setHistory([]));
    }
    setShowHistory((v) => !v);
  };

  const handleSubmitAnswer = async () => {
    if (!answerBlob) return;
    setSubmitting(true);
    setError(null);
    try {
      const file = answerBlob instanceof File ? answerBlob : new File([answerBlob], "reflex-answer.webm", { type: answerBlob.type || "audio/webm" });
      const { url } = await uploadMedia(file, "REVIEW_VIDEO_SUBMISSION");
      const updated = await submitReviewVideoQuestionAudio(question.id, url);
      setSubmission(updated);
      setHistory(null);
      setShowHistory(false);
      handleDiscardDraft();
      setJustSubmitted(true);
    } catch (err) {
      setError(friendlyApiErrorMessage(err, "Nộp bài thất bại — thử lại."));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="bg-sky-2 border border-teal/20 rounded-[14px] p-4 space-y-3">
      <button
        type="button"
        onClick={() => onSeek(question.timestampSeconds)}
        title="Bấm để tua video/audio tới mốc câu hỏi này"
        className="w-full flex items-start justify-between gap-2 text-left cursor-pointer group"
      >
        <div className="flex items-center gap-1.5 text-[10px] font-extrabold text-teal-deep uppercase tracking-wide">
          <span>Câu hỏi {index}</span>
          <span className="flex items-center gap-1 px-1.5 py-0.5 rounded-md bg-white/70 group-hover:bg-white text-teal-deep normal-case font-bold transition-colors">
            <Clock size={11} /> {formatTimestamp(question.timestampSeconds)}
          </span>
        </div>
        {question.maxAttempts != null && (
          <span className="text-[10px] font-bold text-muted shrink-0">
            {attemptsUsed}/{question.maxAttempts} lượt nộp
          </span>
        )}
      </button>

      {question.prompt && <p className="text-sm font-bold text-ink">{question.prompt}</p>}

      {justSubmitted && (
        <div className="flex items-center gap-1.5 text-xs font-extrabold text-emerald-700 bg-emerald-50 border border-emerald-200 p-2.5 rounded-xl animate-in fade-in duration-150">
          <CheckCircle2 size={14} className="text-emerald-600 shrink-0" /> Đã nộp bài thành công!
        </div>
      )}

      {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-xl">{error}</div>}
      {recorder.error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-xl">{recorder.error}</div>}

      {loadingSubmission ? (
        <p className="text-xs text-muted font-bold">Đang tải bài nộp...</p>
      ) : (
        <div className="space-y-3">
          {submission && (
            <div className="space-y-2">
              <p className="text-[10px] text-muted font-bold">Bài trả lời gần nhất (lượt {submission.attemptNumber})</p>
              <audio src={submission.audioUrl} controls className="w-full" />
              <p className="text-[10px] text-muted font-bold">Đã nộp lúc {new Date(submission.submittedAt).toLocaleString("vi-VN")}</p>
              <div className="flex items-center gap-2">
                <CheckCircle2 size={14} className="text-emerald-600" />
                <span className="text-sm font-black text-emerald-700">Đã nộp bài</span>
              </div>

              {attemptsUsed > 1 && (
                <button type="button" onClick={handleToggleHistory} className="flex items-center gap-1 text-[10px] font-extrabold text-teal-deep hover:underline">
                  <History size={11} /> {showHistory ? "Ẩn lịch sử các lần nộp trước" : `Xem ${attemptsUsed - 1} lần nộp trước`}
                </button>
              )}
              {showHistory && history && (
                <div className="space-y-2 pl-3 border-l-2 border-teal/30">
                  {history
                    .filter((h) => h.id !== submission.id)
                    .map((h) => (
                      <div key={h.id} className="space-y-1">
                        <p className="text-[10px] text-muted font-bold">
                          Lượt {h.attemptNumber} — {new Date(h.submittedAt).toLocaleString("vi-VN")}
                        </p>
                        <audio src={h.audioUrl} controls className="w-full h-8" />
                      </div>
                    ))}
                </div>
              )}
            </div>
          )}

          {attemptsExhausted ? (
            <p className="text-[10px] font-extrabold text-rose-600 uppercase pt-2 border-t border-teal/20">Đã hết lượt nộp lại cho câu hỏi này.</p>
          ) : answerBlob ? (
            <div className="space-y-2 pt-2 border-t border-teal/20">
              <p className="text-[10px] font-extrabold text-teal-deep uppercase">Bản ghi mới — nghe lại trước khi nộp</p>
              {answerPreviewUrl && <audio src={answerPreviewUrl} controls className="w-full" />}
              <div className="flex gap-2">
                <button onClick={handleDiscardDraft} className="flex-1 px-3 py-2 bg-white hover:bg-slate-100 border border-line rounded-xl text-xs font-bold text-ink">
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
            <div className="flex flex-wrap items-center gap-2 pt-2 border-t border-teal/20">
              {recorder.recording ? (
                <button onClick={recorder.stop} className="flex items-center gap-1.5 px-3.5 py-2 bg-coral text-white rounded-xl text-xs font-extrabold animate-pulse">
                  <Square size={13} /> Dừng ({recorder.elapsedSeconds}s/{question.maxRecordingSeconds}s)
                </button>
              ) : (
                <button onClick={recorder.start} className="flex items-center gap-1.5 px-3.5 py-2 bg-teal hover:bg-teal-deep text-white rounded-xl text-xs font-extrabold">
                  <Mic size={13} /> Ghi âm trả lời (tối đa {question.maxRecordingSeconds}s)
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
  );
}
