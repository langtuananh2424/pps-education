import React, { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Check, CheckCircle2, Play, ShieldAlert, X } from "lucide-react";
import { friendlyApiErrorMessage } from "@/lib/apiClient";
import {
  ConnectionAnswerResult,
  ReviewVideoConnectionQuestionResponse,
  ReviewVideoResponse,
  getReviewVideoProgress,
  listReviewVideoConnectionQuestionsForSession,
  reportReviewVideoProgress,
  startReviewVideoWatchSession,
  submitReviewVideoConnectionAnswers
} from "../api";
import { extractYouTubeVideoId, loadYouTubeIframeApi } from "../lib/youtubePlayer";
import { useLockBodyScroll } from "@/components/ui/useLockBodyScroll";

const SEEK_TOLERANCE_SECONDS = 2;
const PROGRESS_REPORT_INTERVAL_SECONDS = 5;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12 — khóa video hoàn toàn (không pause/tua
 * được), mirror `buildLockedYouTubeEmbedSrc` của ReflexVideoTaskPage: CHỈ dùng `controls=0` để ẩn thanh
 * điều khiển gốc — không thêm `fs=0&modestbranding=1&playsinline=1` (đã thử ở Reflex, khiến request iframe
 * bị trình duyệt hủy giữa chừng).
 */
function buildLockedYouTubeEmbedSrc(videoId: string): string {
  return `https://www.youtube.com/embed/${videoId}?enablejsapi=1&rel=0&disablekb=1&controls=0`;
}

/**
 * Theo dõi % đã xem THẬT (mốc giây cao nhất từng xem qua TRONG lượt xem hiện tại, chặn tua tới) — CHỈ
 * dùng cho CONNECTION. V59: mỗi lần mở video là 1 "lượt xem" (watchSessionId) riêng — phải startWatchSession
 * lấy sessionId TRƯỚC khi report, không còn ghi thẳng vào watermark suốt đời như cũ.
 */
function useYouTubeWatchProgress(
  video: ReviewVideoResponse | null,
  watchSessionId: number | null,
  started: boolean,
  onProgress: (r: Awaited<ReturnType<typeof reportReviewVideoProgress>>) => void
) {
  const iframeId = "homework-review-video-frame";
  const [watchedPercent, setWatchedPercent] = useState(0);
  const playerRef = useRef<any>(null);
  const playerReadyRef = useRef(false);
  const maxWatchedSecondsRef = useRef(0);
  const lastReportedSecondsRef = useRef(0);
  const pollRef = useRef<number | null>(null);
  /** Bản ref đồng bộ của `started`/đã kết thúc — đọc trong callback YouTube IFrame API (đóng gói 1 lần lúc tạo Player). */
  const startedRef = useRef(started);
  startedRef.current = started;
  const endedRef = useRef(false);
  const watchSessionIdRef = useRef(watchSessionId);
  watchSessionIdRef.current = watchSessionId;

  const reportProgress = (force: boolean) => {
    const sessionId = watchSessionIdRef.current;
    if (!video || sessionId == null) return;
    const watched = maxWatchedSecondsRef.current;
    if (!force && watched - lastReportedSecondsRef.current < PROGRESS_REPORT_INTERVAL_SECONDS) return;
    lastReportedSecondsRef.current = watched;
    reportReviewVideoProgress(video.id, sessionId, Math.round(watched)).then(onProgress).catch(() => undefined);
  };
  /** Callback bên trong YouTube IFrame API (đóng gói 1 LẦN lúc tạo Player, không theo watchSessionId nữa
      — xem lý do ở effect tạo Player bên dưới) luôn cần đọc bản `reportProgress` MỚI NHẤT (đóng đúng
      watchSessionId hiện tại qua watchSessionIdRef) — mirror handleTimeUpdateRef của ReflexVideoTaskPage. */
  const reportProgressRef = useRef(reportProgress);
  reportProgressRef.current = reportProgress;

  /**
   * Tạo YT.Player 1 LẦN duy nhất theo VIDEO (KHÔNG theo watchSessionId) — bổ sung ngoài SDD gốc, đã xác
   * nhận với người dùng 2026-08-12. `player.destroy()` gỡ HẲN <iframe> khỏi DOM; nếu effect này còn phụ
   * thuộc watchSessionId (đổi mỗi khi mở lượt xem MỚI để tự động sang lượt kế tiếp sau khi nộp câu hỏi —
   * xem startNextSession ở component), mỗi lần đổi lượt sẽ hủy rồi cố tạo lại Player trên 1 iframe ĐÃ BỊ
   * GỠ KHỎI DOM → thất bại êm, video không phát lại được. Chuyển "lượt xem mới" (effect kế tiếp) sang chỉ
   * seekTo(0)+playVideo() trên CÙNG 1 Player, không đụng vòng đời Player.
   */
  useEffect(() => {
    const youTubeVideoId = video && video.sourceType === "YOUTUBE_URL" ? extractYouTubeVideoId(video.fileUrl) : null;
    if (!youTubeVideoId) return;

    let cancelled = false;
    loadYouTubeIframeApi().then(() => {
      if (cancelled) return;
      const YT = (window as any).YT;
      playerRef.current = new YT.Player(iframeId, {
        events: {
          // Chưa tự playVideo() nếu học sinh chưa bấm "Bắt đầu xem" — chờ handleStart gọi, tránh trình
          // duyệt chặn autoplay có tiếng (lệnh không nằm trong 1 user gesture thật).
          onReady: (e: any) => {
            playerReadyRef.current = true;
            if (startedRef.current) e.target.playVideo();
          },
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
                reportProgressRef.current(false);
              }, 500);
            } else {
              if (pollRef.current) {
                window.clearInterval(pollRef.current);
                pollRef.current = null;
              }
              if (event.data === YT.PlayerState.ENDED) {
                endedRef.current = true;
                // getCurrentTime() lúc ENDED thường KHÔNG chạm đúng getDuration() (làm tròn xuống, VD
                // 99% thay vì 100%) — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12, ép về
                // đúng 100% giống hệt handleEnded của native media (useMediaElementWatchProgress), tránh
                // sessionQualified (watchedPercent >= 100) không bao giờ đúng dù video đã xem hết.
                const duration = playerRef.current?.getDuration?.();
                if (duration) maxWatchedSecondsRef.current = duration;
                setWatchedPercent(100);
              }
              reportProgressRef.current(true);
              if (event.data === YT.PlayerState.PAUSED && startedRef.current && !endedRef.current) {
                // Khóa: chặn tạm dừng ngoài ý muốn — tự phát lại ngay (mirror ReflexVideoTaskPage).
                playerRef.current?.playVideo?.();
              }
            }
          }
        }
      });
    });

    return () => {
      cancelled = true;
      if (pollRef.current) window.clearInterval(pollRef.current);
      reportProgressRef.current(true);
      try {
        playerRef.current?.destroy?.();
      } catch {
        // Iframe cũ có thể đã bị gỡ khỏi DOM — bỏ qua lỗi cleanup.
      }
      playerRef.current = null;
      playerReadyRef.current = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [video?.id, video?.sourceType, video?.fileUrl]);

  // Học sinh vừa bấm "Bắt đầu xem" SAU KHI Player đã sẵn sàng (onReady lỡ chạy trước với startedRef=false).
  useEffect(() => {
    if (started) playerRef.current?.playVideo?.();
  }, [started]);

  /**
   * Mỗi lượt xem MỚI (watchSessionId đổi — kể cả lượt 2, 3... tự mở ngay sau khi nộp xong câu hỏi của
   * lượt trước, xem startNextSession) — reset bộ đếm % xem về 0 + tự tua video về đầu + tự phát lại nếu
   * đã "Bắt đầu xem" (không cần học sinh bấm lại). Bổ sung ngoài SDD gốc, đã xác nhận 2026-08-12.
   */
  useEffect(() => {
    maxWatchedSecondsRef.current = 0;
    lastReportedSecondsRef.current = 0;
    endedRef.current = false;
    setWatchedPercent(0);
    if (watchSessionId == null || !playerReadyRef.current) return;
    playerRef.current?.seekTo?.(0, true);
    if (startedRef.current) playerRef.current?.playVideo?.();
  }, [watchSessionId]);

  return { iframeId, watchedPercent };
}

function useMediaElementWatchProgress(
  video: ReviewVideoResponse | null,
  watchSessionId: number | null,
  started: boolean,
  onProgress: (r: Awaited<ReturnType<typeof reportReviewVideoProgress>>) => void
) {
  const mediaRef = useRef<HTMLMediaElement | null>(null);
  const [watchedPercent, setWatchedPercent] = useState(0);
  const maxWatchedSecondsRef = useRef(0);
  const lastReportedSecondsRef = useRef(0);
  const startedRef = useRef(started);
  startedRef.current = started;

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

    // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12 — mỗi khi mở lượt xem MỚI (kể cả lượt
    // 2, 3... tự mở ngay sau khi nộp xong câu hỏi lượt trước, xem startNextSession), tự tua về đầu + tự
    // phát lại nếu đã "Bắt đầu xem" (không cần học sinh bấm lại nút Start).
    media.currentTime = 0;
    if (startedRef.current) media.play?.().catch(() => undefined);

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
    const handlePause = () => {
      reportProgress(true);
      // Khóa: chặn tạm dừng ngoài ý muốn — tự phát lại ngay nếu đã bắt đầu và video chưa kết thúc.
      if (startedRef.current && !media.ended) media.play?.().catch(() => undefined);
    };

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

interface ReviewVideoTaskModalProps {
  video: ReviewVideoResponse;
  /**
   * V128/V129 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — lần giao ACTIVE cụ thể
   * đang mở modal này (BE nay yêu cầu bắt buộc để ghi tiến độ đúng lần giao, không tự đoán). undefined
   * khi mở từ mục chỉ nằm trong Kho (chưa có bản giao nào) — mirror ReflexVideoTaskPage.
   */
  assignmentId: number | undefined;
  onClose: () => void;
}

/**
 * UC-23a (CONNECTION — xem video, theo dõi từng LƯỢT xem, V59). Mở từ tab "Bài tập về nhà" (đã gộp Kho
 * Video Ôn tập vào đây theo yêu cầu người dùng 2026-07-27 — không còn hiển thị riêng ở E-Learning & LMS).
 *
 * REFLEX (UC-23b) đã tách sang trang riêng `ReflexVideoTaskPage` (2026-08-11) — không còn dùng modal
 * này nữa (video khóa hoàn toàn + ghi âm tự động theo mốc thời gian không phù hợp dạng popup dễ
 * bấm-ra-ngoài-là-mất bản ghi nháp).
 */
export default function ReviewVideoTaskModal({ video, assignmentId, onClose }: ReviewVideoTaskModalProps) {
  useLockBodyScroll(true);
  const { t } = useTranslation("portal-exercises");
  const isYouTube = video.sourceType === "YOUTUBE_URL";
  const youTubeVideoId = isYouTube ? extractYouTubeVideoId(video.fileUrl) : null;

  // V59: phải mở 1 "lượt xem" mới TRƯỚC khi báo tiến độ.
  const [watchSessionId, setWatchSessionId] = useState<number | null>(null);
  const [progressSummary, setProgressSummary] = useState<{ viewCount: number; requiredViewCount: number; completed: boolean } | null>(null);

  // V76: câu hỏi trắc nghiệm cuối mỗi lượt xem — bắt buộc trả lời khớp ĐÚNG lượt vừa mở
  // (watchSessionId) mới tính vào viewCount. Reset khi mở lượt xem mới, giống watchSessionId/progressSummary.
  const [connectionQuestions, setConnectionQuestions] = useState<ReviewVideoConnectionQuestionResponse[]>([]);
  const [loadingConnectionQuestions, setLoadingConnectionQuestions] = useState(true);
  const [connectionQuestionsError, setConnectionQuestionsError] = useState<string | null>(null);
  const [selectedAnswers, setSelectedAnswers] = useState<Record<number, number>>({});
  const [quizResult, setQuizResult] = useState<ConnectionAnswerResult[] | null>(null);
  const [submittingQuiz, setSubmittingQuiz] = useState(false);
  const [quizError, setQuizError] = useState<string | null>(null);

  /**
   * Guard bằng ref (không phải chỉ dựa vào dependency array) — startReviewVideoWatchSession là POST tạo
   * bản ghi thật (không idempotent), React 18 StrictMode tự double-invoke useEffect ở môi trường dev sẽ
   * gọi 2 lần gần như cùng lúc nếu không chặn, tạo 2 lượt xem (watchSessionId) khác nhau cho 1 lần mở
   * video — watchSessionId đổi 2 lần khiến YT.Player bị hủy giữa chừng rồi tạo lại (xem ghi chú tương tự
   * ở ReflexVideoTaskPage), làm video không tải được khi thoát ra rồi mở lại (đã xác nhận với người dùng
   * 2026-08-12, mirror đúng cách chặn ở TakeExerciseModal). */
  const openedRef = useRef(false);
  useEffect(() => {
    if (openedRef.current) return;
    openedRef.current = true;
    setWatchSessionId(null);
    setProgressSummary(null);
    setSelectedAnswers({});
    setQuizResult(null);
    setQuizError(null);
    if (assignmentId == null) return;
    startReviewVideoWatchSession(video.id, assignmentId)
      .then((r) => setWatchSessionId(r.sessionId))
      .catch(() => undefined);
    // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — đọc lại tiến độ ĐÃ LƯU ngay khi
    // mở modal (trước đây "Tổng số lượt đã đạt" chỉ hiện SAU khi có report sống trong phiên đang mở,
    // nên mở lần đầu luôn thấy trống dù đã có tiến độ từ trước).
    getReviewVideoProgress(video.id, assignmentId)
      .then((p) => setProgressSummary({ viewCount: p.viewCount, requiredViewCount: p.requiredViewCount, completed: p.completed }))
      .catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [video.id, assignmentId]);

  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12 — chặn màn hình "Bắt đầu xem" (giống
   * ReflexVideoTaskPage): fullscreen/play video CHỈ thật sự gọi trong handleStart (user gesture trực
   * tiếp), KHÔNG tự động lúc mount — tránh trình duyệt chặn autoplay có tiếng (NotAllowedError).
   */
  const [started, setStarted] = useState(false);
  const [showExitConfirm, setShowExitConfirm] = useState(false);

  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12 — vào toàn màn hình NGAY khi bấm
   * "Bắt đầu xem", best-effort tự vào lại nếu học sinh thoát fullscreen (Esc/vuốt) giữa chừng. Không
   * hỗ trợ trên iOS Safari (`document.fullscreenEnabled === false`) — tự bỏ qua êm. Thoát fullscreen
   * thật khi đóng trang (cleanup).
   */
  useEffect(() => {
    if (!started) return;
    if (document.fullscreenEnabled) {
      document.documentElement.requestFullscreen().catch(() => undefined);
    }
    const handleFullscreenChange = () => {
      if (document.fullscreenEnabled && !document.fullscreenElement) {
        document.documentElement.requestFullscreen().catch(() => undefined);
      }
    };
    document.addEventListener("fullscreenchange", handleFullscreenChange);
    return () => {
      document.removeEventListener("fullscreenchange", handleFullscreenChange);
      if (document.fullscreenElement) document.exitFullscreen().catch(() => undefined);
    };
  }, [started]);

  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-11 — phụ thuộc watchSessionId (không phải
  // video.id) vì mỗi lượt xem giờ chỉ nhận ĐÚNG nhóm câu hỏi đã gán riêng cho lượt đó (chia đều ngẫu
  // nhiên theo từng học sinh), không còn là toàn bộ ngân hàng câu hỏi của video như trước.
  useEffect(() => {
    if (watchSessionId == null) return;
    setLoadingConnectionQuestions(true);
    setConnectionQuestionsError(null);
    listReviewVideoConnectionQuestionsForSession(watchSessionId)
      .then((qs) => setConnectionQuestions(qs.slice().sort((a, b) => a.displayOrder - b.displayOrder)))
      .catch((err) => setConnectionQuestionsError(friendlyApiErrorMessage(err, t("reviewVideoTask.questionsLoadError"))))
      .finally(() => setLoadingConnectionQuestions(false));
  }, [watchSessionId]);

  const handleProgress = (r: { viewCount: number; requiredViewCount: number; completed: boolean }) =>
    setProgressSummary({ viewCount: r.viewCount, requiredViewCount: r.requiredViewCount, completed: r.completed });

  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12 — mở NGAY 1 "lượt xem" mới sau khi nộp
   * xong câu hỏi lượt trước (nếu CHƯA đủ số lượt yêu cầu): video tự tua về đầu + tự phát lại (xem effect
   * theo watchSessionId trong 2 hook useYouTubeWatchProgress/useMediaElementWatchProgress), bộ đếm
   * "Tổng số lượt đã đạt" đã tăng lên (handleProgress ở handleSubmitConnectionQuiz gọi trước đó) — học
   * sinh không cần tự đóng/mở lại trang để sang lượt kế tiếp.
   */
  const startNextSession = () => {
    if (assignmentId == null) return;
    setQuizPopupOpen(false);
    setSelectedAnswers({});
    setQuizResult(null);
    setQuizError(null);
    startReviewVideoWatchSession(video.id, assignmentId)
      .then((r) => setWatchSessionId(r.sessionId))
      .catch(() => undefined);
  };

  const handleSubmitConnectionQuiz = async () => {
    if (!watchSessionId || connectionQuestions.some((q) => selectedAnswers[q.id] == null)) return;
    setSubmittingQuiz(true);
    setQuizError(null);
    try {
      const answers = connectionQuestions.map((q) => ({ questionId: q.id, selectedChoiceId: selectedAnswers[q.id] }));
      const result = await submitReviewVideoConnectionAnswers(watchSessionId, answers);
      setQuizResult(result.results);
      handleProgress(result.progress);
      if (!result.progress.completed) startNextSession();
    } catch (err) {
      setQuizError(friendlyApiErrorMessage(err, t("reviewVideoTask.submitQuizError")));
    } finally {
      setSubmittingQuiz(false);
    }
  };

  const { iframeId, watchedPercent: youTubeWatchedPercent } = useYouTubeWatchProgress(isYouTube ? video : null, watchSessionId, started, handleProgress);
  const { mediaRef, watchedPercent: mediaWatchedPercent } = useMediaElementWatchProgress(!isYouTube ? video : null, watchSessionId, started, handleProgress);
  const watchedPercent = isYouTube ? youTubeWatchedPercent : mediaWatchedPercent;
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-11 — Video từ kết nối giờ LUÔN yêu cầu
  // xem HẾT (100%, cố định) mới được làm câu hỏi; completionThresholdPercent đổi ý nghĩa thành "ngưỡng
  // % pass" điểm trắc nghiệm (không còn liên quan gì tới việc xem đủ hay chưa) — xem ReviewVideoService.
  const sessionQualified = watchedPercent >= 100;

  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — câu hỏi cuối lượt xem hiện dạng
  // popup đè lên (thay vì nằm ở cuối, phải cuộn xuống mới thấy) — tự bật NGAY khi lượt xem vừa đạt
  // ngưỡng (không bật lại nếu học sinh tự đóng, tránh làm phiền). Đóng popup không mất tiến độ — vẫn
  // còn banner nhắc trong luồng chính để mở lại trả lời sau.
  const [quizPopupOpen, setQuizPopupOpen] = useState(false);
  useEffect(() => {
    if (sessionQualified) setQuizPopupOpen(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionQualified]);

  const handleStart = () => {
    setStarted(true);
    if (!isYouTube) mediaRef.current?.play?.().catch(() => undefined);
    // YouTube: playVideo() được gọi trong useYouTubeWatchProgress (effect theo dõi `started`).
  };

  /** Cảnh báo khi thoát giữa chừng (đã bắt đầu xem nhưng lượt này chưa nộp xong câu hỏi) — mirror ReflexVideoTaskPage. */
  const handleExit = () => {
    if (started && !quizResult) {
      setShowExitConfirm(true);
      return;
    }
    onClose();
  };

  return (
    <div className="fixed inset-0 bg-white z-[100] flex flex-col overflow-y-auto">
      {/* Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12 — chặn màn hình "Bắt đầu xem"
          (mirror ReflexVideoTaskPage): bấm nút mới thật sự play()/requestFullscreen() ngay trong
          handler click, tránh trình duyệt chặn autoplay có tiếng. */}
      {!started && (
        <div className="fixed inset-0 bg-ink/70 backdrop-blur-sm z-[120] flex items-center justify-center p-4">
          <div className="bg-white rounded-[20px] max-w-sm w-full shadow-2xl p-5 sm:p-6 space-y-4 text-center">
            <h3 className="text-lg font-extrabold text-ink">{video.title}</h3>
            <p className="text-xs font-bold text-muted">{t("reviewVideoTask.startScreen.description")}</p>
            <button
              onClick={handleStart}
              className="w-full flex items-center justify-center gap-1.5 px-4 py-3 bg-teal hover:bg-teal-deep text-white rounded-xl text-sm font-extrabold"
            >
              <Play size={16} /> {t("reviewVideoTask.startScreen.startButton")}
            </button>
            <button onClick={handleExit} className="w-full px-4 py-2 text-xs font-extrabold text-muted hover:text-ink">
              {t("reviewVideoTask.startScreen.exitButton")}
            </button>
          </div>
        </div>
      )}

      {showExitConfirm && (
        <div className="fixed inset-0 bg-ink/60 z-[130] flex items-center justify-center p-4">
          <div className="bg-white rounded-[20px] max-w-sm w-full shadow-2xl p-5 sm:p-6 space-y-4 text-center">
            <ShieldAlert size={36} className="text-amber-600 mx-auto" />
            <h3 className="text-base font-extrabold text-ink">{t("reviewVideoTask.exitConfirm.title")}</h3>
            <p className="text-xs font-bold text-muted">{t("reviewVideoTask.exitConfirm.description")}</p>
            <div className="flex flex-col sm:flex-row gap-2">
              <button
                onClick={() => setShowExitConfirm(false)}
                className="flex-1 px-4 py-2.5 bg-white hover:bg-slate-100 border border-line rounded-xl text-xs font-extrabold text-ink"
              >
                {t("reviewVideoTask.exitConfirm.stay")}
              </button>
              <button
                onClick={() => {
                  setShowExitConfirm(false);
                  onClose();
                }}
                className="flex-1 px-4 py-2.5 bg-rose-600 hover:bg-rose-700 text-white rounded-xl text-xs font-extrabold"
              >
                {t("reviewVideoTask.exitConfirm.stillExit")}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — câu hỏi cuối lượt xem hiện
          dạng popup đè lên video/modal, thay vì nằm cuối luồng phải cuộn mới thấy. Đóng được (không
          mất tiến độ xem) — banner ở luồng chính cho mở lại. */}
      {quizPopupOpen && !quizResult && (
        <div
          className="fixed inset-0 bg-ink/60 z-[105] flex items-center justify-center p-4"
          onClick={(e) => {
            e.stopPropagation();
            setQuizPopupOpen(false);
          }}
        >
          <div
            className="bg-white rounded-[20px] max-w-lg w-full max-h-[85vh] overflow-y-auto shadow-2xl p-4 sm:p-6 space-y-3"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-start justify-between gap-3">
              <p className="text-xs font-extrabold text-emerald-700 uppercase tracking-wide pt-1">{t("reviewVideoTask.quizPopup.heading")}</p>
              <button
                onClick={() => setQuizPopupOpen(false)}
                className="w-7 h-7 shrink-0 rounded-full bg-sky-2 hover:bg-sky flex items-center justify-center text-ink transition-colors"
                aria-label={t("reviewVideoTask.quizPopup.closeAriaLabel")}
              >
                <X size={14} />
              </button>
            </div>
            {connectionQuestionsError && (
              <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-xl">{connectionQuestionsError}</div>
            )}
            {quizError && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-xl">{quizError}</div>}
            {loadingConnectionQuestions ? (
              <p className="text-xs text-muted font-bold">{t("reviewVideoTask.quizPopup.loadingQuestions")}</p>
            ) : connectionQuestions.length === 0 ? (
              <p className="text-xs text-muted font-bold italic">{t("reviewVideoTask.quizPopup.noQuestions")}</p>
            ) : (
              <>
                {/* Popup này chỉ hiện khi CHƯA nộp (quizResult == null — xem điều kiện render ở trên) nên không cần nhánh hiển thị kết quả đúng/sai ở đây. */}
                {connectionQuestions.map((q, i) => (
                  <div key={q.id} className="space-y-2">
                    <p className="text-sm sm:text-base font-bold text-ink">{t("reviewVideoTask.quizPopup.questionLabel", { index: i + 1, prompt: q.prompt })}</p>
                    <div className="space-y-1.5">
                      {q.choices.map((c) => {
                        const picked = selectedAnswers[q.id] === c.id;
                        return (
                          <button
                            key={c.id}
                            type="button"
                            onClick={() => setSelectedAnswers((prev) => ({ ...prev, [q.id]: c.id }))}
                            className={`w-full flex items-center gap-2 text-left px-3 py-2 sm:py-2.5 rounded-xl border text-xs sm:text-sm font-bold transition-colors ${
                              picked ? "bg-teal text-white border-teal" : "bg-white border-line text-ink hover:border-teal/50"
                            }`}
                          >
                            <span className="flex-1">{c.choiceLabel}. {c.content}</span>
                            {picked && <Check size={14} className="shrink-0" />}
                          </button>
                        );
                      })}
                    </div>
                  </div>
                ))}

                <button
                  type="button"
                  onClick={handleSubmitConnectionQuiz}
                  disabled={submittingQuiz || connectionQuestions.some((q) => selectedAnswers[q.id] == null)}
                  className="w-full px-3 py-2.5 sm:py-3 bg-teal hover:bg-teal-deep text-white rounded-xl text-xs sm:text-sm font-extrabold disabled:opacity-50"
                >
                  {submittingQuiz ? t("reviewVideoTask.quizPopup.submitting") : t("reviewVideoTask.quizPopup.submitButton")}
                </button>
              </>
            )}
          </div>
        </div>
      )}

      <div className="max-w-4xl w-full mx-auto p-4 sm:p-6 space-y-3 sm:space-y-4 flex-1">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0 flex-1">
            <span className="text-[10px] font-extrabold uppercase text-teal-deep tracking-wide">{t("reviewVideoTask.badge")}</span>
            <h3 className="text-lg sm:text-xl lg:text-2xl font-extrabold text-ink truncate">{video.title}</h3>
          </div>
          <button
            onClick={handleExit}
            // Làm nổi bật (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12) — đồng bộ màu
            // sắc với nút Đóng của TakeExerciseModal/ReflexVideoTaskPage.
            className="shrink-0 px-3 py-1.5 sm:px-4 sm:py-2 rounded-full bg-rose-50 hover:bg-rose-100 text-rose-600 border border-rose-200 text-xs sm:text-sm font-extrabold transition-colors"
          >
            {t("reviewVideoTask.exitButton")}
          </button>
        </div>

        {/* Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12 — khóa hoàn toàn không cho
            pause/tua (mirror ReflexVideoTaskPage): ẩn controls gốc + chặn tương tác chuột/bàn phím, chỉ
            cho tự phát theo mốc thời gian đã xem tối đa (chặn tua tới ở tầng theo dõi % xem phía trên). */}
        <div className="relative aspect-video w-full rounded-[12px] overflow-hidden bg-ink" onContextMenu={(e) => e.preventDefault()}>
          {isYouTube && youTubeVideoId ? (
            <>
              <iframe
                key={video.id}
                id={iframeId}
                src={buildLockedYouTubeEmbedSrc(youTubeVideoId)}
                title={video.title}
                className="w-full h-full pointer-events-none"
                allow="autoplay; encrypted-media"
              />
              {/* Chặn hẳn tương tác chuột lên iframe (disablekb/controls=0 của YouTube không chặn được double-click/kéo) — API vẫn gọi được vì không đi qua overlay này. */}
              <div className="absolute inset-0" />
            </>
          ) : video.sourceType === "R2_AUDIO" ? (
            <div className="w-full h-full flex items-center justify-center p-4 sm:p-6">
              <audio key={video.id} ref={mediaRef as React.RefObject<HTMLAudioElement>} src={video.fileUrl} tabIndex={-1} className="w-full pointer-events-none" />
            </div>
          ) : (
            <video
              key={video.id}
              ref={mediaRef as React.RefObject<HTMLVideoElement>}
              src={video.fileUrl}
              tabIndex={-1}
              className="w-full h-full pointer-events-none"
              playsInline
            />
          )}
        </div>

        <div className="bg-sky-2 border border-teal/20 rounded-[14px] p-4 space-y-3">
          <p className="text-[10px] font-extrabold text-teal-deep uppercase tracking-wide">{t("reviewVideoTask.progress.heading")}</p>
          <div className="space-y-1">
            <div className="flex items-center justify-between text-[10px] font-extrabold text-teal-deep">
              <span>{t("reviewVideoTask.progress.watchedMax")}</span>
              <span>{watchedPercent}%</span>
            </div>
            <div className="h-1.5 w-full rounded-full bg-white overflow-hidden">
              <div
                className={`h-full rounded-full transition-all ${sessionQualified ? "bg-emerald-500" : "bg-teal-deep"}`}
                style={{ width: `${watchedPercent}%` }}
              />
            </div>
            <p className={`text-[10px] font-extrabold ${sessionQualified ? "text-emerald-600" : "text-amber-600"}`}>
              {sessionQualified ? t("reviewVideoTask.progress.fullyWatched") : t("reviewVideoTask.progress.notFullyWatched")}
            </p>
          </div>
          <div className="pt-2 border-t border-teal/20 flex items-center justify-between">
            <span className="text-[10px] font-extrabold text-teal-deep uppercase">{t("reviewVideoTask.progress.totalCompleted")}</span>
            <span className={`text-xs font-black ${progressSummary?.completed ? "text-emerald-600" : "text-ink"}`}>
              {progressSummary ? `${progressSummary.viewCount}/${progressSummary.requiredViewCount}` : `—/${video.requiredViewCount}`}{" "}
              {t("reviewVideoTask.progress.countSuffix")}
              {progressSummary?.completed && " ✓"}
            </span>
          </div>
        </div>

        {/* V76: câu hỏi trắc nghiệm cuối lượt xem — SAU KHI nộp xong, chỉ còn lại xác nhận gọn trong luồng chính (popup đã tự đóng vì quizResult != null). */}
        {quizResult && (
          <div className="flex items-center gap-1.5 text-xs font-extrabold text-emerald-700 bg-emerald-50 border border-emerald-200 p-3 rounded-[14px]">
            <CheckCircle2 size={14} className="shrink-0" />
            {t("reviewVideoTask.progress.submittedSummary", {
              correct: quizResult.filter((r) => r.correct).length,
              total: quizResult.length
            })}
          </div>
        )}

        {/* Banner nhắc mở lại popup — chỉ hiện khi đã đạt ngưỡng, chưa trả lời, VÀ học sinh vừa tự đóng popup. */}
        {sessionQualified && !quizResult && !quizPopupOpen && (
          <button
            type="button"
            onClick={() => setQuizPopupOpen(true)}
            className="w-full flex items-center justify-between gap-2 bg-emerald-50 border border-emerald-200 rounded-[14px] p-4 text-left hover:bg-emerald-100 transition-colors"
          >
            <span className="text-xs font-extrabold text-emerald-700">{t("reviewVideoTask.progress.answerNowBanner")}</span>
            <span className="text-xs font-black text-emerald-700 shrink-0">{t("reviewVideoTask.progress.answerNowButton")}</span>
          </button>
        )}
      </div>
    </div>
  );
}
