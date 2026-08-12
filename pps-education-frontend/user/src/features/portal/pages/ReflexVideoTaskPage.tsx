import React, { useEffect, useRef, useState } from "react";
import { CheckCircle2, Lock, Mic, Play, RotateCcw, ShieldAlert } from "lucide-react";
import { friendlyApiErrorMessage } from "@/lib/apiClient";
import {
  IntegrityEventInput,
  ReviewVideoQuestionResponse,
  ReviewVideoResponse,
  ReviewVideoSubmissionResponse,
  getMyLatestReviewVideoSubmission,
  listReviewVideoQuestions,
  submitReviewVideoQuestionAudio,
  uploadMedia
} from "../api";
import { useIntegrityMonitor } from "../hooks/useIntegrityMonitor";
import { extractYouTubeVideoId, formatTimestamp, loadYouTubeIframeApi } from "../lib/youtubePlayer";

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-11 (sau 2 vòng test thực tế) — CHỈ dùng
 * `controls=0` để ẩn thanh điều khiển gốc của YouTube. ĐÃ THỬ thêm `fs=0&modestbranding=1&playsinline=1`
 * hai lần (1 lần trước khi sửa bug double-init YT.Player, 1 lần SAU khi đã sửa bug đó) — cả 2 lần đều
 * khiến request iframe bị trình duyệt hủy giữa chừng (Network tab: status trống, đỏ, 0B), trong khi chỉ
 * riêng `controls=0` thì ổn định. KHÔNG thêm lại 3 tham số này trừ khi tìm ra cách khác đã kiểm chứng —
 * việc "khóa" video không phụ thuộc chúng (xem overlay chặn click + auto-resume-on-pause bên dưới).
 */
function buildLockedYouTubeEmbedSrc(videoId: string): string {
  return `https://www.youtube.com/embed/${videoId}?enablejsapi=1&rel=0&disablekb=1&controls=0`;
}

/**
 * Ghi âm trực tiếp qua microphone (MediaRecorder API) — 1 instance dùng chung cho cả trang (không phải
 * mỗi câu hỏi 1 recorder như bản popup cũ), vì chỉ 1 câu được ghi âm tại 1 thời điểm (theo mốc thời
 * gian, không cho học sinh tự chọn). maxSeconds truyền vào lúc start() vì mỗi câu hỏi có giới hạn riêng.
 */
function useAudioRecorder() {
  const [recording, setRecording] = useState(false);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [maxSeconds, setMaxSeconds] = useState(0);
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

  const start = async (limitSeconds: number) => {
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
      setMaxSeconds(limitSeconds);
      setRecording(true);
      setElapsedSeconds(0);
      const startedAt = Date.now();
      timerRef.current = window.setInterval(() => {
        const elapsed = Math.round((Date.now() - startedAt) / 1000);
        setElapsedSeconds(elapsed);
        if (elapsed >= limitSeconds) stop();
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

  return { recording, elapsedSeconds, maxSeconds, audioBlob, error, start, stop, reset };
}

interface ReflexVideoTaskPageProps {
  video: ReviewVideoResponse;
  onClose: () => void;
}

/**
 * UC-23b (REFLEX) — trang riêng (không phải popup, kể từ 2026-08-11 theo yêu cầu người dùng) để đảm bảo
 * đúng tính chất "phản xạ": video khóa hoàn toàn (không pause/tua), ghi âm TỰ ĐỘNG kích hoạt đúng mốc
 * thời gian mỗi câu hỏi (học sinh không tự bấm ghi âm được), và bản ghi chỉ giữ tạm ở bộ nhớ trình
 * duyệt cho tới khi bấm "Nộp bài" mới đẩy lên backend — "Làm lại" xóa sạch bản ghi tạm, không tính lượt.
 */
export default function ReflexVideoTaskPage({ video, onClose }: ReflexVideoTaskPageProps) {
  const isYouTube = video.sourceType === "YOUTUBE_URL";
  const youTubeVideoId = isYouTube ? extractYouTubeVideoId(video.fileUrl) : null;
  const iframeId = "reflex-locked-video-frame";

  const [questions, setQuestions] = useState<ReviewVideoQuestionResponse[]>([]);
  const [loadingQuestions, setLoadingQuestions] = useState(true);
  const [questionsError, setQuestionsError] = useState<string | null>(null);
  /** Bài đã nộp gần nhất cho từng câu — tải 1 lần khi vào trang, dùng để biết attemptsUsed/lịch sử. */
  const [latestSubmissions, setLatestSubmissions] = useState<Record<number, ReviewVideoSubmissionResponse | undefined>>({});

  useEffect(() => {
    setLoadingQuestions(true);
    setQuestionsError(null);
    listReviewVideoQuestions(video.id)
      .then(async (qs) => {
        const sorted = qs.slice().sort((a, b) => a.timestampSeconds - b.timestampSeconds);
        setQuestions(sorted);
        const entries = await Promise.all(
          sorted.map(async (q) => [q.id, await getMyLatestReviewVideoSubmission(q.id).catch(() => undefined)] as const)
        );
        setLatestSubmissions(Object.fromEntries(entries));
      })
      .catch((err) => setQuestionsError(friendlyApiErrorMessage(err, "Không tải được danh sách câu hỏi.")))
      .finally(() => setLoadingQuestions(false));
  }, [video.id]);

  const mediaRef = useRef<HTMLMediaElement | null>(null);
  const youTubePlayerRef = useRef<any>(null);
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-11 (sau khi test thực tế) — KHÔNG được tự
   * gọi `play()`/`requestFullscreen()` trong useEffect lúc mount: trình duyệt chặn autoplay có tiếng nếu
   * lệnh play() không nằm trong 1 sự kiện click trực tiếp của người dùng (silent NotAllowedError — video
   * đứng im ở khung đen), và fullscreen tự vào từ effect cũng dễ bị trình duyệt tự thoát ra ngay khi có
   * tương tác nhỏ (VD click vào iframe) vì không được tính là "user gesture" thật. Giải pháp: chặn màn
   * hình "Bắt đầu làm bài" — bấm nút mới thật sự play()/requestFullscreen() NGAY trong handler click đó.
   */
  const [started, setStarted] = useState(false);
  const [requestingMic, setRequestingMic] = useState(false);
  const [micError, setMicError] = useState<string | null>(null);
  const [showExitConfirm, setShowExitConfirm] = useState(false);
  /** Bản ref đồng bộ của `started` — dùng trong callback YouTube IFrame API (đóng gói 1 lần lúc tạo
      Player, không re-render theo state) để tránh đọc closure cũ. */
  const startedRef = useRef(false);
  useEffect(() => {
    startedRef.current = started;
  }, [started]);
  const [videoEnded, setVideoEnded] = useState(false);
  const videoEndedRef = useRef(false);
  useEffect(() => {
    videoEndedRef.current = videoEnded;
  }, [videoEnded]);
  const [draftBlobs, setDraftBlobs] = useState<Record<number, Blob>>({});
  const [activeRecordingQuestionId, setActiveRecordingQuestionId] = useState<number | null>(null);
  const [lastTriggeredQuestionId, setLastTriggeredQuestionId] = useState<number | null>(null);
  const triggeredQuestionIdsRef = useRef<Set<number>>(new Set());
  const recorder = useAudioRecorder();

  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);
  const submittedRef = useRef(false);
  useEffect(() => {
    submittedRef.current = submitted;
  }, [submitted]);

  const {
    violationCount,
    isMonitoringActive,
    justViolated,
    flush: flushIntegrityEvents
  } = useIntegrityMonitor({
    // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-11 (sau khi test thực tế) — tắt giám sát
    // ngay khi bấm "Nộp bài": bài đã hoàn tất, không còn gì để giám sát, và bấm nút "Nộp bài"/thao tác
    // ngay sau đó (tải file lên/đóng khung xác nhận nộp) đang bị hiểu nhầm thành 1 lần "thoát ra ngoài".
    enabled: started && !submitting && !submitted,
    onFullscreenExit: () => {
      if (document.fullscreenEnabled) document.documentElement.requestFullscreen().catch(() => undefined);
    }
  });

  const attemptsUsed = (questionId: number) => latestSubmissions[questionId]?.attemptNumber ?? 0;
  const attemptsExhausted = (question: ReviewVideoQuestionResponse) =>
    question.maxAttempts != null && attemptsUsed(question.id) >= question.maxAttempts;

  useEffect(() => {
    if (!recorder.audioBlob || activeRecordingQuestionId == null) return;
    const questionId = activeRecordingQuestionId;
    setDraftBlobs((prev) => ({ ...prev, [questionId]: recorder.audioBlob as Blob }));
    setActiveRecordingQuestionId(null);
    recorder.reset();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recorder.audioBlob]);

  const handleTimeUpdate = (currentSeconds: number) => {
    if (submitted || videoEnded) return;
    for (const q of questions) {
      if (currentSeconds >= q.timestampSeconds && !triggeredQuestionIdsRef.current.has(q.id)) {
        triggeredQuestionIdsRef.current.add(q.id);
        setLastTriggeredQuestionId(q.id);
        if (!attemptsExhausted(q) && !recorder.recording) {
          setActiveRecordingQuestionId(q.id);
          recorder.start(q.maxRecordingSeconds);
        }
      }
    }
  };
  /**
   * Callback trong YouTube IFrame API (poll interval) được tạo 1 LẦN trong useEffect, không nắm được
   * bản mới nhất của handleTimeUpdate ở mỗi lần render (đóng gói lúc effect chạy) — dùng ref để interval
   * luôn gọi đúng phiên bản mới nhất, tránh bug "recorder.recording/submitted/videoEnded" bị đứng ở giá
   * trị cũ (VD ghi âm chồng nhiều câu cùng lúc do check recorder.recording sai — bug đã gặp ở bản popup cũ).
   */
  const handleTimeUpdateRef = useRef(handleTimeUpdate);
  handleTimeUpdateRef.current = handleTimeUpdate;

  // ---- Native <video>/<audio> (R2_VIDEO/R2_AUDIO) — khóa control, tự phát, tự bám mốc thời gian. ----
  useEffect(() => {
    if (isYouTube) return;
    const media = mediaRef.current;
    if (!media) return;
    const onTimeUpdate = () => handleTimeUpdateRef.current(media.currentTime);
    const onEnded = () => setVideoEnded(true);
    const onPause = () => {
      // Best-effort chặn tạm dừng ngoài ý muốn (VD phím media cứng trên bàn phím) — không có API nào
      // chặn được thật 100%, chỉ cố phát lại ngay nếu đã bắt đầu, chưa hết video/chưa nộp bài. KHÔNG
      // gọi play() trước khi started=true — media cố tình đứng yên ở màn hình "Bắt đầu làm bài".
      if (started && !media.ended && !submitted) media.play?.().catch(() => undefined);
    };
    media.addEventListener("timeupdate", onTimeUpdate);
    media.addEventListener("ended", onEnded);
    media.addEventListener("pause", onPause);
    return () => {
      media.removeEventListener("timeupdate", onTimeUpdate);
      media.removeEventListener("ended", onEnded);
      media.removeEventListener("pause", onPause);
    };
    // `questions` KHÔNG đưa vào deps dù được đọc gián tiếp qua handleTimeUpdateRef (luôn là bản mới nhất)
    // — nếu đưa vào, effect sẽ chạy lại NGAY khi fetch câu hỏi xong (questions: [] → [...]), gỡ rồi gắn
    // lại listener liên tục không cần thiết.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isYouTube, video.id, submitted, started]);

  // ---- YouTube — ẩn control (controls=0/disablekb=1/fs=0) + overlay chặn click + poll thời gian phát. ----
  useEffect(() => {
    if (!isYouTube || !youTubeVideoId) return;
    let cancelled = false;
    let pollId: number | null = null;
    loadYouTubeIframeApi().then(() => {
      if (cancelled) return;
      const YT = (window as any).YT;
      youTubePlayerRef.current = new YT.Player(iframeId, {
        events: {
          // Không tự playVideo() ở đây trừ khi người dùng ĐÃ bấm "Bắt đầu làm bài" trước khi player kịp
          // sẵn sàng (API load chậm) — bình thường chờ handleStart gọi, nếu không trình duyệt chặn
          // autoplay có tiếng vì lệnh không nằm trong 1 user gesture thật.
          onReady: (e: any) => {
            if (startedRef.current) e.target.playVideo();
          },
          onStateChange: (e: any) => {
            if (e.data === YT.PlayerState.ENDED) {
              setVideoEnded(true);
              if (pollId) window.clearInterval(pollId);
            } else if (e.data === YT.PlayerState.PLAYING) {
              if (pollId) window.clearInterval(pollId);
              pollId = window.setInterval(() => {
                const t = youTubePlayerRef.current?.getCurrentTime?.();
                if (t != null) handleTimeUpdateRef.current(t);
              }, 250);
            } else if (e.data === YT.PlayerState.PAUSED) {
              // Best-effort chặn tạm dừng ngoài ý muốn — xem ghi chú tương tự ở nhánh native media.
              if (startedRef.current && !videoEndedRef.current && !submittedRef.current) youTubePlayerRef.current?.playVideo?.();
            }
          }
        }
      });
    });
    return () => {
      cancelled = true;
      if (pollId) window.clearInterval(pollId);
      try {
        youTubePlayerRef.current?.destroy?.();
      } catch {
        // Iframe cũ có thể đã bị gỡ khỏi DOM — bỏ qua lỗi cleanup.
      }
      youTubePlayerRef.current = null;
    };
    // `questions` KHÔNG đưa vào deps — nếu đưa vào, effect này chạy lại NGAY khi fetch câu hỏi xong
    // (questions: [] → mảng thật ngay lúc mount), hủy rồi tạo lại YT.Player trong lúc request iframe lần
    // đầu còn đang load dở → trình duyệt hủy request đó (bug đã gặp khi test thực tế 2026-08-11: Network
    // tab hiện status trống/đỏ/0B). handleTimeUpdateRef ở trên luôn đọc `questions` mới nhất nên không mất
    // dữ liệu gì khi bỏ khỏi deps.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isYouTube, youTubeVideoId]);

  const hasDrafts = Object.keys(draftBlobs).length > 0;

  const handleRetry = () => {
    triggeredQuestionIdsRef.current.clear();
    setDraftBlobs({});
    setActiveRecordingQuestionId(null);
    setLastTriggeredQuestionId(null);
    setVideoEnded(false);
    setSubmitError(null);
    if (recorder.recording) recorder.stop();
    recorder.reset();
    if (isYouTube) {
      youTubePlayerRef.current?.seekTo?.(0, true);
      youTubePlayerRef.current?.playVideo?.();
    } else if (mediaRef.current) {
      mediaRef.current.currentTime = 0;
      mediaRef.current.play?.().catch(() => undefined);
    }
  };

  const handleSubmit = async () => {
    if (!hasDrafts) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      const events = flushIntegrityEvents();
      let firstRequest = true;
      const updated: Record<number, ReviewVideoSubmissionResponse> = {};
      for (const [questionIdStr, blob] of Object.entries(draftBlobs)) {
        const questionId = Number(questionIdStr);
        const file = new File([blob], "reflex-answer.webm", { type: blob.type || "audio/webm" });
        const { url } = await uploadMedia(file, "REVIEW_VIDEO_SUBMISSION");
        const response = await submitReviewVideoQuestionAudio(
          questionId,
          url,
          firstRequest && events.length > 0 ? { events } : undefined
        );
        firstRequest = false;
        updated[questionId] = response;
      }
      setLatestSubmissions((prev) => ({ ...prev, ...updated }));
      setDraftBlobs({});
      setSubmitted(true);
    } catch (err) {
      setSubmitError(friendlyApiErrorMessage(err, "Nộp bài thất bại — thử lại."));
    } finally {
      setSubmitting(false);
    }
  };

  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-11 (sau khi test thực tế) — xin quyền
   * micro NGAY tại màn hình "Bắt đầu làm bài" (trước khi bật giám sát/fullscreen/phát video). Nếu để
   * trình duyệt tự hỏi quyền SAU khi đã vào fullscreen + đang giám sát (lúc auto-record kích hoạt lần
   * đầu), popup xin quyền của trình duyệt sẽ làm mất fullscreen/focus và bị hệ thống hiểu nhầm là học
   * sinh "thoát ra ngoài" — ghi nhận vi phạm oan.
   */
  const handleStart = async () => {
    setMicError(null);
    setRequestingMic(true);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      stream.getTracks().forEach((t) => t.stop());
    } catch {
      setMicError("Cần cấp quyền micro để làm bài — vui lòng cho phép truy cập microphone (biểu tượng khóa trên thanh địa chỉ) rồi bấm lại.");
      setRequestingMic(false);
      return;
    }
    setRequestingMic(false);
    setStarted(true);
    if (isYouTube) {
      youTubePlayerRef.current?.playVideo?.();
    } else {
      mediaRef.current?.play?.().catch(() => undefined);
    }
  };

  const handleExit = () => {
    if (hasDrafts && !submitted) {
      setShowExitConfirm(true);
      return;
    }
    onClose();
  };

  return (
    <div className="fixed inset-0 bg-white z-[100] flex flex-col overflow-y-auto">
      {justViolated && (
        <div
          key={violationCount}
          role="alert"
          className="fixed top-6 left-1/2 -translate-x-1/2 z-[110] flex items-center gap-2 bg-rose-600 text-white pl-3 pr-4 py-2.5 rounded-2xl shadow-xl animate-alert-pop"
        >
          <ShieldAlert size={18} className="shrink-0" />
          <span className="text-xs font-black">Đã ghi nhận: bạn vừa thoát ra ngoài khi đang làm bài!</span>
        </div>
      )}

      {!started && (
        <div className="fixed inset-0 bg-ink/70 backdrop-blur-sm z-[120] flex items-center justify-center p-4">
          <div className="bg-white rounded-[20px] max-w-sm w-full shadow-2xl p-6 space-y-4 text-center">
            <h3 className="text-lg font-extrabold text-ink">{video.title}</h3>
            <p className="text-xs font-bold text-muted">
              Video sẽ tự phát toàn màn hình — không tua/tạm dừng được. Đến đúng mốc thời gian, hệ thống sẽ tự động ghi âm
              câu trả lời của bạn.
            </p>
            {micError && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-xl text-left">{micError}</div>}
            <button
              onClick={handleStart}
              disabled={requestingMic}
              className="w-full flex items-center justify-center gap-1.5 px-4 py-3 bg-teal hover:bg-teal-deep text-white rounded-xl text-sm font-extrabold disabled:opacity-50"
            >
              <Play size={16} /> {requestingMic ? "Đang xin quyền micro..." : "Bắt đầu làm bài"}
            </button>
            <button onClick={handleExit} className="w-full px-4 py-2 text-xs font-extrabold text-muted hover:text-ink">
              Thoát
            </button>
          </div>
        </div>
      )}

      {showExitConfirm && (
        <div className="fixed inset-0 bg-ink/60 z-[130] flex items-center justify-center p-4">
          <div className="bg-white rounded-[20px] max-w-sm w-full shadow-2xl p-6 space-y-4 text-center">
            <h3 className="text-base font-extrabold text-ink">Thoát khi chưa nộp bài?</h3>
            <p className="text-xs font-bold text-muted">Bạn có bản ghi âm chưa nộp — thoát ra sẽ mất toàn bộ bản ghi này.</p>
            <div className="flex gap-2">
              <button
                onClick={() => setShowExitConfirm(false)}
                className="flex-1 px-4 py-2.5 bg-white hover:bg-slate-100 border border-line rounded-xl text-xs font-extrabold text-ink"
              >
                Ở lại làm bài
              </button>
              <button
                onClick={() => {
                  setShowExitConfirm(false);
                  onClose();
                }}
                className="flex-1 px-4 py-2.5 bg-rose-600 hover:bg-rose-700 text-white rounded-xl text-xs font-extrabold"
              >
                Vẫn thoát
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="max-w-2xl w-full mx-auto p-6 space-y-4 flex-1">
        <div className="flex items-start justify-between gap-3">
          <div>
            <span className="text-[10px] font-extrabold uppercase text-teal-deep tracking-wide">Video phản xạ</span>
            <h3 className="text-lg font-extrabold text-ink">{video.title}</h3>
          </div>
          <button
            onClick={handleExit}
            className="px-3 py-1.5 rounded-full bg-sky-2 hover:bg-sky text-ink text-xs font-extrabold transition-colors"
          >
            Thoát
          </button>
        </div>

        {isMonitoringActive && (
          <div className="flex items-center gap-1.5 bg-amber-50 border border-amber-100 rounded-xl px-3 py-2">
            <ShieldAlert size={13} className="text-amber-600 shrink-0" />
            <span className="text-[11px] font-bold text-amber-800">
              Đang giám sát quá trình làm bài — thoát ra ngoài (đổi tab/thu nhỏ/thoát toàn màn hình) sẽ được ghi nhận.
            </span>
          </div>
        )}

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
            <div className="w-full h-full flex items-center justify-center p-6">
              <audio key={video.id} ref={mediaRef as React.RefObject<HTMLAudioElement>} src={video.fileUrl} tabIndex={-1} className="w-full pointer-events-none" />
              <Lock size={28} className="text-white/60 absolute" />
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

        {questionsError && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{questionsError}</div>}
        {submitError && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{submitError}</div>}

        {loadingQuestions ? (
          <p className="text-xs text-muted font-bold">Đang tải câu hỏi...</p>
        ) : questions.length === 0 ? (
          <p className="text-xs text-muted font-bold italic">Video này chưa có câu hỏi nào.</p>
        ) : (
          <div className="space-y-3">
            {questions.map((q, i) => {
              const isActive = activeRecordingQuestionId === q.id;
              const hasDraft = draftBlobs[q.id] != null;
              const alreadySubmitted = submitted && latestSubmissions[q.id] != null;
              const wasTriggered = triggeredQuestionIdsRef.current.has(q.id) || lastTriggeredQuestionId === q.id;
              const exhausted = attemptsExhausted(q);
              return (
                <div
                  key={q.id}
                  className={`bg-sky-2 border rounded-[14px] p-4 space-y-2 transition-opacity ${
                    wasTriggered ? "border-teal/20 opacity-100" : "border-teal/10 opacity-50"
                  }`}
                >
                  <div className="flex items-center justify-between gap-2 text-[10px] font-extrabold text-teal-deep uppercase tracking-wide">
                    <span className="flex items-center gap-1.5">
                      Câu hỏi {i + 1}
                      <span className="px-1.5 py-0.5 rounded-md bg-white/70 text-teal-deep normal-case font-bold">{formatTimestamp(q.timestampSeconds)}</span>
                    </span>
                    {q.maxAttempts != null && <span className="text-muted normal-case">{attemptsUsed(q.id)}/{q.maxAttempts} lượt nộp</span>}
                  </div>
                  {q.prompt && <p className="text-sm font-bold text-ink">{q.prompt}</p>}

                  {isActive ? (
                    <div className="flex items-center gap-1.5 px-3.5 py-2 bg-coral text-white rounded-xl text-xs font-extrabold animate-pulse w-fit">
                      <Mic size={13} /> Đang ghi âm ({recorder.elapsedSeconds}s/{recorder.maxSeconds}s)
                    </div>
                  ) : hasDraft ? (
                    <p className="text-[11px] font-bold text-emerald-700">✓ Đã ghi âm — nộp bài để lưu lại.</p>
                  ) : exhausted ? (
                    <p className="text-[10px] font-extrabold text-rose-600 uppercase">Đã hết lượt nộp lại cho câu hỏi này.</p>
                  ) : alreadySubmitted ? (
                    <p className="text-[11px] font-bold text-emerald-700">✓ Đã nộp.</p>
                  ) : wasTriggered ? (
                    <p className="text-[11px] font-bold text-muted">Không ghi âm được lúc này.</p>
                  ) : (
                    <p className="text-[11px] font-bold text-muted">Sẽ tự động ghi âm khi video chạy tới mốc thời gian này.</p>
                  )}
                  {recorder.error && isActive && <p className="text-[11px] font-bold text-rose-600">{recorder.error}</p>}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {videoEnded && !questionsError && (
        <div className="sticky bottom-0 bg-white border-t border-line p-4">
          <div className="max-w-2xl w-full mx-auto flex gap-3">
            {submitted ? (
              <div className="flex-1 flex items-center justify-center gap-2 px-4 py-3 bg-emerald-50 border border-emerald-200 rounded-xl text-emerald-700 text-sm font-extrabold">
                <CheckCircle2 size={16} /> Đã nộp bài thành công!
              </div>
            ) : (
              <>
                <button
                  onClick={handleRetry}
                  disabled={submitting}
                  className="flex-1 flex items-center justify-center gap-1.5 px-4 py-3 bg-white hover:bg-slate-100 border border-line rounded-xl text-sm font-extrabold text-ink disabled:opacity-50"
                >
                  <RotateCcw size={15} /> Làm lại
                </button>
                <button
                  onClick={handleSubmit}
                  disabled={submitting || !hasDrafts}
                  className="flex-1 px-4 py-3 bg-teal hover:bg-teal-deep text-white rounded-xl text-sm font-extrabold disabled:opacity-50"
                >
                  {submitting ? "Đang nộp..." : "Nộp bài"}
                </button>
              </>
            )}
          </div>
          {submitted && (
            <div className="max-w-2xl w-full mx-auto mt-3">
              <button onClick={onClose} className="w-full px-4 py-2.5 bg-sky-2 hover:bg-sky text-ink rounded-xl text-xs font-extrabold">
                Quay lại danh sách bài tập
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
