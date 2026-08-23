import React, { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { CheckCircle2, Loader2, Lock, Mic, Play, RotateCcw, ShieldAlert } from "lucide-react";
import { friendlyApiErrorMessage } from "@/lib/apiClient";
import {
  ReflexQuestionProgressResponse,
  ReviewVideoQuestionResponse,
  ReviewVideoResponse,
  listMyReflexProgress,
  listReviewVideoQuestions,
  submitReflexSpokenAnswer,
  submitReflexWrittenAnswer,
  uploadMedia
} from "../api";
import { useIntegrityMonitor } from "../hooks/useIntegrityMonitor";
import { extractYouTubeVideoId, formatTimestamp, loadYouTubeIframeApi } from "../lib/youtubePlayer";
import MonitoringBadge from "../components/MonitoringBadge";

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

/** Trạng thái của 1 câu hỏi suy ra từ tiến trình đã lưu — quyết định UI nào hiện ở câu đang mở (writing/speaking) hay bỏ qua khi video chạy qua (passed). */
function stageForProgress(p: ReflexQuestionProgressResponse | undefined): "writing" | "speaking" | "passed" {
  if (p?.questionPassed) return "passed";
  if (p?.writingPassed) return "speaking";
  return "writing";
}

/**
 * Ghi âm trực tiếp qua microphone (MediaRecorder API) — 1 instance dùng chung cho cả trang, vì chỉ 1
 * câu được ghi âm tại 1 thời điểm (câu đang mở khoá — video tạm dừng chờ trong lúc ghi/chấm).
 */
function useAudioRecorder() {
  const { t } = useTranslation("portal-exercises");
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
      setError(t("reflexVideoTask.micAccessError"));
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
  /**
   * V128/V129 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — lần giao ACTIVE cụ thể
   * đang mở trang này (BE nay yêu cầu bắt buộc để chấm điểm đúng lần giao, không tự đoán). undefined
   * khi mở từ mục chỉ nằm trong Kho (chưa có bản giao nào) — các thao tác nộp bài sẽ báo lỗi.
   */
  assignmentId: number | undefined;
  onClose: () => void;
}

/**
 * UC-23b V2 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22) — "Video phản xạ": video
 * khóa hoàn toàn (không pause/tua tay), TỰ ĐỘNG tạm dừng đúng mốc thời gian mỗi câu hỏi và mở khoá luồng
 * TUẦN TỰ: viết câu trả lời trước → AI chấm ngữ pháp ngay (>=70% mới đạt) → đạt thì mở khoá ghi âm nói
 * lại → AI chấm nội dung ngay (>=70% mới đạt) → đạt thì video tự chạy tiếp tới câu kế. Không giới hạn số
 * lần thử lại ở mỗi bước — nộp lại chỉ chấm lại, không mất lượt. Đây là luồng THAY THẾ hoàn toàn luồng cũ
 * "ghi âm theo mốc, nộp cả loạt cuối video, GV chấm tay" (vẫn còn nguyên trong ReviewVideoService, không
 * xoá, chỉ không còn dùng cho video REFLEX theo luồng mới — xem Javadoc ReflexSequentialGradingService).
 */
export default function ReflexVideoTaskPage({ video, assignmentId, onClose }: ReflexVideoTaskPageProps) {
  const { t } = useTranslation("portal-exercises");
  const isYouTube = video.sourceType === "YOUTUBE_URL";
  const youTubeVideoId = isYouTube ? extractYouTubeVideoId(video.fileUrl) : null;
  const iframeId = "reflex-locked-video-frame";

  const [questions, setQuestions] = useState<ReviewVideoQuestionResponse[]>([]);
  const [loadingQuestions, setLoadingQuestions] = useState(true);
  const [questionsError, setQuestionsError] = useState<string | null>(null);
  const [progress, setProgress] = useState<Record<number, ReflexQuestionProgressResponse | undefined>>({});
  const progressRef = useRef(progress);
  useEffect(() => {
    progressRef.current = progress;
  }, [progress]);

  useEffect(() => {
    setLoadingQuestions(true);
    setQuestionsError(null);
    listReviewVideoQuestions(video.id)
      .then(async (qs) => {
        const sorted = qs.slice().sort((a, b) => a.timestampSeconds - b.timestampSeconds);
        setQuestions(sorted);
        const saved = assignmentId == null ? [] : await listMyReflexProgress(assignmentId).catch(() => []);
        setProgress(Object.fromEntries(saved.map((p) => [p.questionId, p])));
      })
      .catch((err) => setQuestionsError(friendlyApiErrorMessage(err, t("reflexVideoTask.questionsLoadError"))))
      .finally(() => setLoadingQuestions(false));
  }, [video.id, assignmentId]);

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
  const startedRef = useRef(false);
  useEffect(() => {
    startedRef.current = started;
  }, [started]);
  const [videoEnded, setVideoEnded] = useState(false);
  const videoEndedRef = useRef(false);
  useEffect(() => {
    videoEndedRef.current = videoEnded;
  }, [videoEnded]);

  /** Câu hỏi đang mở khoá gate video (video đang tạm dừng chờ) — null nghĩa là video đang chạy tự do. */
  const [activeQuestionId, setActiveQuestionId] = useState<number | null>(null);
  const activeQuestionIdRef = useRef<number | null>(null);
  useEffect(() => {
    activeQuestionIdRef.current = activeQuestionId;
  }, [activeQuestionId]);
  const triggeredQuestionIdsRef = useRef<Set<number>>(new Set());
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — cho phép bấm lại 1 câu ĐÃ ĐẠT trong
   * danh sách bên dưới để xem lại kết quả (trước đây các câu đã đạt chỉ hiện dòng tóm tắt, không xem lại
   * được câu trả lời/nhận xét). Tách riêng khỏi `activeQuestionId` (câu THẬT đang mở khoá video chờ làm)
   * để không phá luồng làm bài thật đang dang dở — panel HIỂN THỊ ưu tiên câu đang xem lại (nếu có), còn
   * các handler nộp bài (handleSubmitWriting/handleSubmitSpeaking...) vẫn luôn thao tác trên câu THẬT.
   */
  const [reviewQuestionId, setReviewQuestionId] = useState<number | null>(null);

  const [answerDraft, setAnswerDraft] = useState("");
  const [writingSubmitting, setWritingSubmitting] = useState(false);
  const [writingError, setWritingError] = useState<string | null>(null);
  const [speakingSubmitting, setSpeakingSubmitting] = useState(false);
  const [speakingError, setSpeakingError] = useState<string | null>(null);
  const [speakingPassedPopup, setSpeakingPassedPopup] = useState<{ scorePercent: number | null; feedback: string | null } | null>(null);
  const [writingPassedPopup, setWritingPassedPopup] = useState<{ scorePercent: number | null; feedback: string | null } | null>(null);
  const recorder = useAudioRecorder();

  const allQuestionsPassed = questions.length > 0 && questions.every((q) => progress[q.id]?.questionPassed);

  const {
    violationCount,
    isMonitoringActive,
    justViolated
  } = useIntegrityMonitor({
    // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — luồng mới nộp NGAY từng bước (không
    // còn "nộp cả loạt cuối video" để đệm sự kiện giám sát gửi kèm như luồng cũ), và endpoint chấm AI mới
    // (submitReflexWrittenAnswer/submitReflexSpokenAnswer) chưa có tham số integrityEvents — CHỈ dùng hook
    // này để CẢNH BÁO SỐNG cho học sinh (toast/badge, vẫn có tác dụng răn đe), KHÔNG gửi lên BE lần này.
    enabled: started && !allQuestionsPassed,
    onFullscreenExit: () => {
      if (document.fullscreenEnabled) document.documentElement.requestFullscreen().catch(() => undefined);
    }
  });

  const pauseVideo = () => {
    if (isYouTube) youTubePlayerRef.current?.pauseVideo?.();
    else mediaRef.current?.pause?.();
  };
  const resumeVideo = () => {
    if (isYouTube) youTubePlayerRef.current?.playVideo?.();
    else mediaRef.current?.play?.().catch(() => undefined);
  };
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — fix bug thật phát hiện lúc test luồng
   * "nhảy thẳng" mới: player YouTube iframe (dùng src tĩnh, không autoplay) KHÔNG tự vẽ khung hình khi chỉ
   * gọi seekTo() lúc đang ở trạng thái "chưa phát" (unstarted) — màn hình đứng im MÀU ĐEN hoàn toàn, học
   * sinh tưởng video lỗi. Video HTML5 gốc (R2_VIDEO/R2_AUDIO) thì set currentTime là đủ (trình duyệt tự
   * vẽ khung hình mới ngay cả khi chưa play()). Fix riêng cho YouTube: tắt tiếng, seek, phát 1 nhịp cực
   * ngắn RỒI dừng ngay — ép player vẽ đúng khung hình tại mốc mà KHÔNG tạo cảm giác "phát liên tục" (vẫn
   * đúng tinh thần "nhảy thẳng, không phát đoạn giữa" người dùng đã chọn).
   */
  const seekTo = (seconds: number) => {
    if (isYouTube) {
      const player = youTubePlayerRef.current;
      if (!player) return;
      player.mute?.();
      player.seekTo?.(seconds, true);
      player.playVideo?.();
      window.setTimeout(() => {
        player.pauseVideo?.();
        player.unMute?.();
      }, 300);
    } else if (mediaRef.current) {
      mediaRef.current.currentTime = seconds;
    }
  };

  const activateQuestion = (q: ReviewVideoQuestionResponse) => {
    setActiveQuestionId(q.id);
    pauseVideo();
    const p = progressRef.current[q.id];
    setAnswerDraft(p?.answerText ?? "");
    setWritingError(null);
    setSpeakingError(null);
    recorder.reset();
  };

  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — fix bug thật (feedback người dùng
   * thực tế, đã làm rõ qua nhiều vòng trao đổi): ĐOẠN ĐẦU video (trước mốc câu 1) vẫn PHÁT BÌNH THƯỜNG
   * liên tục như cũ (học sinh cần nghe/xem phần dẫn đầu) — video tự dừng đúng mốc câu 1 nhờ
   * `handleTimeUpdate` bên dưới (KHÔNG đổi). Chỉ áp dụng NHẢY THẲNG (seek, không phát đoạn giữa) cho các
   * lần CHUYỂN CÂU sau đó (1 câu đã đạt cả 2 bước → câu kế tiếp) — xem handleContinueAfterSpeakingPass.
   */
  const jumpToNextPendingQuestion = () => {
    const next = questions.find((q) => stageForProgress(progressRef.current[q.id]) !== "passed");
    if (next) {
      seekTo(next.timestampSeconds);
      activateQuestion(next);
    }
  };

  const handleTimeUpdate = (currentSeconds: number) => {
    if (videoEndedRef.current || activeQuestionIdRef.current != null) return;
    for (const q of questions) {
      if (currentSeconds >= q.timestampSeconds && !triggeredQuestionIdsRef.current.has(q.id)) {
        triggeredQuestionIdsRef.current.add(q.id);
        if (stageForProgress(progressRef.current[q.id]) === "passed") continue;
        activateQuestion(q);
        break;
      }
    }
  };
  /**
   * Callback trong YouTube IFrame API (poll interval) được tạo 1 LẦN trong useEffect, không nắm được
   * bản mới nhất của handleTimeUpdate ở mỗi lần render (đóng gói lúc effect chạy) — dùng ref để interval
   * luôn gọi đúng phiên bản mới nhất.
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
      // Best-effort chặn tạm dừng ngoài ý muốn (VD phím media cứng trên bàn phím) — CHỈ khi không phải
      // đang tạm dừng có chủ đích để mở khoá 1 câu hỏi (activeQuestionIdRef != null).
      if (started && !media.ended && activeQuestionIdRef.current == null) media.play?.().catch(() => undefined);
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
    // — nếu đưa vào, effect sẽ chạy lại NGAY khi fetch câu hỏi xong, gỡ rồi gắn lại listener không cần thiết.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isYouTube, video.id, started]);

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
                const time = youTubePlayerRef.current?.getCurrentTime?.();
                if (time != null) handleTimeUpdateRef.current(time);
              }, 250);
            } else if (e.data === YT.PlayerState.PAUSED) {
              // Best-effort chặn tạm dừng ngoài ý muốn — CHỈ khi không phải đang chủ động dừng để mở khoá câu hỏi.
              if (startedRef.current && !videoEndedRef.current && activeQuestionIdRef.current == null) {
                youTubePlayerRef.current?.playVideo?.();
              }
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
    // `questions` KHÔNG đưa vào deps — xem ghi chú tương tự ở nhánh native media.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isYouTube, youTubeVideoId]);

  const activeQuestion = questions.find((q) => q.id === activeQuestionId) ?? null;

  /** Câu đang HIỂN THỊ trong panel — ưu tiên câu đang xem lại (reviewQuestionId), không thì câu thật đang mở khoá. */
  const displayQuestionId = reviewQuestionId ?? activeQuestionId;
  const displayQuestion = questions.find((q) => q.id === displayQuestionId) ?? null;
  const displayProgress = displayQuestionId != null ? progress[displayQuestionId] : undefined;
  const displayStage = stageForProgress(displayProgress);
  const isReviewing = reviewQuestionId != null;

  const handleReviewQuestion = (q: ReviewVideoQuestionResponse) => {
    // Không cho mở xem lại khi đang ghi âm/đang chờ chấm câu THẬT — tránh học sinh tưởng đã dừng ghi âm.
    if (recorder.recording || writingSubmitting || speakingSubmitting) return;
    setReviewQuestionId(q.id);
  };
  const handleCloseReview = () => setReviewQuestionId(null);

  const handleSubmitWriting = async () => {
    if (!activeQuestion || !answerDraft.trim()) return;
    if (assignmentId == null) {
      setWritingError(t("reflexVideoTask.notAssignedError"));
      return;
    }
    setWritingSubmitting(true);
    setWritingError(null);
    try {
      const response = await submitReflexWrittenAnswer(activeQuestion.id, assignmentId, answerDraft.trim());
      setProgress((prev) => ({ ...prev, [activeQuestion.id]: response }));
      // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — trước đây đạt bước viết là tự
      // động bật ghi âm ngay, học sinh không kịp chuẩn bị. Nay hiện popup báo đạt + điểm/nhận xét, học
      // sinh tự bấm "Bắt đầu ghi âm" khi sẵn sàng (xem handleStartRecordingAfterWritingPass).
      if (stageForProgress(response) === "speaking") {
        setWritingPassedPopup({ scorePercent: response.writingScorePercent, feedback: response.writingFeedback });
      }
    } catch (err) {
      setWritingError(friendlyApiErrorMessage(err, t("reflexVideoTask.submitError")));
    } finally {
      setWritingSubmitting(false);
    }
  };

  const handleStartRecordingAfterWritingPass = () => {
    if (!activeQuestion) return;
    setWritingPassedPopup(null);
    recorder.reset();
    recorder.start(activeQuestion.maxRecordingSeconds);
  };

  const handleStartRecording = () => {
    if (!activeQuestion) return;
    recorder.reset();
    recorder.start(activeQuestion.maxRecordingSeconds);
  };

  const handleSubmitSpeaking = async (blob: Blob) => {
    if (!activeQuestion) return;
    if (assignmentId == null) {
      setSpeakingError(t("reflexVideoTask.notAssignedError"));
      return;
    }
    setSpeakingSubmitting(true);
    setSpeakingError(null);
    try {
      const file = new File([blob], "reflex-answer.webm", { type: blob.type || "audio/webm" });
      const { url } = await uploadMedia(file, "REVIEW_VIDEO_SUBMISSION");
      const response = await submitReflexSpokenAnswer(activeQuestion.id, assignmentId, url);
      setProgress((prev) => ({ ...prev, [activeQuestion.id]: response }));
      // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — fix bug thật: trước đây pass thì
      // đóng câu ngay + video chạy tiếp lập tức, học sinh không kịp thấy điểm/nhận xét bước nói (khác
      // bước viết vốn hiện nhận xét ngay trong khung). Nay hiện popup "Đạt" kèm điểm + nhận xét, chỉ
      // đóng câu hỏi/chạy tiếp video khi học sinh bấm "Tiếp tục" (xem handleContinueAfterSpeakingPass).
      if (response.questionPassed) {
        setSpeakingPassedPopup({ scorePercent: response.speakingScorePercent, feedback: response.speakingFeedback });
      }
    } catch (err) {
      setSpeakingError(friendlyApiErrorMessage(err, t("reflexVideoTask.submitError")));
    } finally {
      setSpeakingSubmitting(false);
    }
  };

  const handleContinueAfterSpeakingPass = () => {
    setSpeakingPassedPopup(null);
    setActiveQuestionId(null);
    jumpToNextPendingQuestion();
  };

  // Ghi âm dừng (hết giờ hoặc học sinh bấm dừng) → tự động nộp ngay, không cần bấm thêm nút "Nộp bài".
  useEffect(() => {
    if (!recorder.audioBlob) return;
    const blob = recorder.audioBlob;
    recorder.reset();
    handleSubmitSpeaking(blob);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recorder.audioBlob]);

  const handleRetrySpeaking = () => {
    if (!activeQuestion) return;
    setSpeakingError(null);
    recorder.reset();
    recorder.start(activeQuestion.maxRecordingSeconds);
  };

  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-11 (sau khi test thực tế) — xin quyền
   * micro NGAY tại màn hình "Bắt đầu làm bài" (trước khi bật giám sát/fullscreen/phát video). Nếu để
   * trình duyệt tự hỏi quyền SAU khi đã vào fullscreen + đang giám sát, popup xin quyền sẽ làm mất
   * fullscreen/focus và bị hệ thống hiểu nhầm là học sinh "thoát ra ngoài" — ghi nhận vi phạm oan.
   */
  const handleStart = async () => {
    setMicError(null);
    setRequestingMic(true);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      stream.getTracks().forEach((t) => t.stop());
    } catch {
      setMicError(t("reflexVideoTask.micPermissionError"));
      setRequestingMic(false);
      return;
    }
    setRequestingMic(false);
    setStarted(true);
    // Đoạn đầu video (trước mốc câu 1) phát bình thường — xem ghi chú ở jumpToNextPendingQuestion.
    resumeVideo();
  };

  const handleExit = () => {
    if (started && !videoEnded && !allQuestionsPassed) {
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
          <span className="text-xs font-black">{t("monitoring.violationToast")}</span>
        </div>
      )}

      {/*
       * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — submitReflexWrittenAnswer/
       * submitReflexSpokenAnswer gọi Gemini đồng bộ NGAY trong request (thực đo ~10-20s, riêng phần nói
       * còn phải tải audio lên R2 trước) — trước đây chỉ có dòng chữ nhỏ "Đang chấm..." bên trong khung
       * câu hỏi, dễ bị hiểu nhầm hệ thống treo. Mirror overlay đã thêm ở TakeExerciseModal.
       */}
      {(writingSubmitting || speakingSubmitting) && (
        <div className="fixed inset-0 bg-white/90 backdrop-blur-sm flex items-center justify-center z-[125]">
          <div className="flex flex-col items-center gap-3 text-center px-6">
            <Loader2 size={36} className="text-teal animate-spin" />
            <p className="text-sm font-extrabold text-ink">{t("reflexVideoTask.gradingOverlay.title")}</p>
            <p className="text-xs font-bold text-muted max-w-xs">
              {writingSubmitting ? t("reflexVideoTask.gradingOverlay.writingDescription") : t("reflexVideoTask.gradingOverlay.speakingDescription")}
            </p>
          </div>
        </div>
      )}

      {!started && (
        <div className="fixed inset-0 bg-ink/70 backdrop-blur-sm z-[120] flex items-center justify-center p-4">
          <div className="bg-white rounded-[20px] max-w-sm w-full shadow-2xl p-6 space-y-4 text-center">
            <h3 className="text-lg font-extrabold text-ink">{video.title}</h3>
            <p className="text-xs font-bold text-muted">{t("reflexVideoTask.startScreen.description")}</p>
            {micError && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-xl text-left">{micError}</div>}
            <button
              onClick={handleStart}
              disabled={requestingMic || loadingQuestions}
              className="w-full flex items-center justify-center gap-1.5 px-4 py-3 bg-teal hover:bg-teal-deep text-white rounded-xl text-sm font-extrabold disabled:opacity-50"
            >
              <Play size={16} /> {requestingMic ? t("reflexVideoTask.startScreen.requestingMic") : t("reflexVideoTask.startScreen.startButton")}
            </button>
            <button onClick={handleExit} className="w-full px-4 py-2 text-xs font-extrabold text-muted hover:text-ink">
              {t("reflexVideoTask.startScreen.exitButton")}
            </button>
          </div>
        </div>
      )}

      {writingPassedPopup && (
        <div className="fixed inset-0 bg-ink/60 z-[130] flex items-center justify-center p-4">
          <div className="bg-white rounded-[20px] max-w-sm w-full shadow-2xl p-6 space-y-4 text-center">
            <CheckCircle2 size={36} className="text-emerald-600 mx-auto" />
            <h3 className="text-base font-extrabold text-ink">
              {t("reflexVideoTask.writingStage.passedPopup.heading")}
              {writingPassedPopup.scorePercent != null &&
                ` — ${t("reflexVideoTask.writingStage.scoreLabel", { score: writingPassedPopup.scorePercent })}`}
            </h3>
            {writingPassedPopup.feedback && (
              <p className="text-xs font-medium text-muted text-left normal-case whitespace-pre-line max-h-48 overflow-y-auto">
                {writingPassedPopup.feedback}
              </p>
            )}
            <button
              onClick={handleStartRecordingAfterWritingPass}
              className="w-full flex items-center justify-center gap-1.5 px-4 py-2.5 bg-teal hover:bg-teal-deep text-white rounded-xl text-xs sm:text-sm font-extrabold"
            >
              <Mic size={14} /> {t("reflexVideoTask.writingStage.passedPopup.startRecordingButton")}
            </button>
          </div>
        </div>
      )}

      {speakingPassedPopup && (
        <div className="fixed inset-0 bg-ink/60 z-[130] flex items-center justify-center p-4">
          <div className="bg-white rounded-[20px] max-w-sm w-full shadow-2xl p-6 space-y-4 text-center">
            <CheckCircle2 size={36} className="text-emerald-600 mx-auto" />
            <h3 className="text-base font-extrabold text-ink">
              {t("reflexVideoTask.speakingStage.passedPopup.heading")}
              {speakingPassedPopup.scorePercent != null &&
                ` — ${t("reflexVideoTask.speakingStage.scoreLabel", { score: speakingPassedPopup.scorePercent })}`}
            </h3>
            {speakingPassedPopup.feedback && (
              <p className="text-xs font-medium text-muted text-left normal-case whitespace-pre-line max-h-48 overflow-y-auto">
                {speakingPassedPopup.feedback}
              </p>
            )}
            <button
              onClick={handleContinueAfterSpeakingPass}
              className="w-full px-4 py-2.5 bg-teal hover:bg-teal-deep text-white rounded-xl text-xs sm:text-sm font-extrabold"
            >
              {t("reflexVideoTask.speakingStage.passedPopup.continueButton")}
            </button>
          </div>
        </div>
      )}

      {showExitConfirm && (
        <div className="fixed inset-0 bg-ink/60 z-[130] flex items-center justify-center p-4">
          <div className="bg-white rounded-[20px] max-w-sm w-full shadow-2xl p-6 space-y-4 text-center">
            <ShieldAlert size={36} className="text-amber-600 mx-auto" />
            <h3 className="text-base font-extrabold text-ink">{t("reflexVideoTask.exitConfirm.title")}</h3>
            <p className="text-xs font-bold text-muted">{t("reflexVideoTask.exitConfirm.description")}</p>
            <div className="flex gap-2">
              <button
                onClick={() => setShowExitConfirm(false)}
                className="flex-1 px-4 py-2.5 bg-white hover:bg-slate-100 border border-line rounded-xl text-xs font-extrabold text-ink"
              >
                {t("reflexVideoTask.exitConfirm.stay")}
              </button>
              <button
                onClick={() => {
                  setShowExitConfirm(false);
                  onClose();
                }}
                className="flex-1 px-4 py-2.5 bg-rose-600 hover:bg-rose-700 text-white rounded-xl text-xs font-extrabold"
              >
                {t("reflexVideoTask.exitConfirm.stillExit")}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="max-w-2xl lg:max-w-3xl w-full mx-auto p-4 sm:p-6 space-y-4 flex-1">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <span className="text-[10px] font-extrabold uppercase text-teal-deep tracking-wide">{t("reflexVideoTask.badge")}</span>
            <h3 className="text-lg sm:text-xl lg:text-2xl font-extrabold text-ink truncate">{video.title}</h3>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            {isMonitoringActive && <MonitoringBadge violationCount={violationCount} exitNote={t("monitoring.reflexExitNote")} />}
            <button
              onClick={handleExit}
              className="shrink-0 px-3 py-1.5 sm:px-4 sm:py-2 rounded-full bg-rose-50 hover:bg-rose-100 text-rose-600 border border-rose-200 text-xs sm:text-sm font-extrabold transition-colors"
            >
              {t("reflexVideoTask.exitButton")}
            </button>
          </div>
        </div>

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

        {displayQuestion && (
          <div className={`bg-white border-2 rounded-[16px] p-4 sm:p-5 space-y-3 shadow-lg ${isReviewing ? "border-line" : "border-teal"}`}>
            <div className="flex items-center justify-between gap-2 text-[10px] sm:text-[11px] font-extrabold text-teal-deep uppercase tracking-wide">
              <span className="flex items-center gap-1.5">
                {t("reflexVideoTask.question.label", { index: questions.findIndex((q) => q.id === displayQuestion.id) + 1 })}
                <span className="px-1.5 py-0.5 rounded-md bg-sky-2 text-teal-deep normal-case font-bold">{formatTimestamp(displayQuestion.timestampSeconds)}</span>
              </span>
              {isReviewing ? (
                <button onClick={handleCloseReview} className="flex items-center gap-1 text-muted normal-case hover:text-ink">
                  {t("reflexVideoTask.reviewBadge")} · {t("reflexVideoTask.closeReviewButton")}
                </button>
              ) : (
                <span className="text-muted normal-case">
                  {displayStage === "writing"
                    ? t("reflexVideoTask.writingStage.title")
                    : t("reflexVideoTask.speakingStage.title")}
                </span>
              )}
            </div>
            {displayQuestion.prompt && <p className="text-sm sm:text-base lg:text-lg font-bold text-ink">{displayQuestion.prompt}</p>}

            {displayStage === "writing" ? (
              <div className="space-y-2">
                <textarea
                  value={answerDraft}
                  onChange={(e) => setAnswerDraft(e.target.value)}
                  disabled={writingSubmitting}
                  rows={4}
                  placeholder={t("reflexVideoTask.writingStage.placeholder")}
                  className="w-full rounded-xl border border-line p-3 text-sm font-medium text-ink disabled:opacity-60"
                />
                {displayProgress?.writingFeedback && (
                  <div
                    className={`text-xs font-bold p-2.5 rounded-xl border ${
                      displayProgress.writingPassed ? "bg-emerald-50 border-emerald-200 text-emerald-700" : "bg-amber-50 border-amber-200 text-amber-700"
                    }`}
                  >
                    <p>
                      {displayProgress.writingPassed
                        ? t("reflexVideoTask.writingStage.passedFeedbackTitle")
                        : t("reflexVideoTask.writingStage.failedFeedbackTitle")}
                      {displayProgress.writingScorePercent != null &&
                        ` — ${t("reflexVideoTask.writingStage.scoreLabel", { score: displayProgress.writingScorePercent })}`}
                    </p>
                    <p className="font-medium mt-1 normal-case whitespace-pre-line">{displayProgress.writingFeedback}</p>
                  </div>
                )}
                {/*
                 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — nộp sai từ lần thứ 3
                 * trở đi: hiện gợi ý câu trả lời đã sửa lỗi (AI CHỈ sửa lỗi trong câu học sinh viết, giữ
                 * nguyên cấu trúc/ý gốc — không phải câu mẫu tự bịa, xem systemPrompt ở
                 * ReflexWritingGrammarAiGradingService). Học sinh có thể tự chọn/copy đoạn text, hoặc bấm
                 * nút điền thẳng vào ô trả lời cho tiện.
                 */}
                {!displayProgress?.writingPassed && (displayProgress?.writingAttemptCount ?? 0) >= 3 && displayProgress?.writingCorrectedAnswer && (
                  <div className="text-xs font-bold p-2.5 rounded-xl border bg-sky-2 border-teal/20 text-teal-deep space-y-1.5">
                    <p className="uppercase text-[10px] tracking-wide">{t("reflexVideoTask.writingStage.suggestionTitle")}</p>
                    <p className="font-medium normal-case text-ink select-all">{displayProgress.writingCorrectedAnswer}</p>
                    <button
                      type="button"
                      onClick={() => setAnswerDraft(displayProgress.writingCorrectedAnswer ?? "")}
                      className="mt-1 px-3 py-1.5 bg-white hover:bg-slate-100 border border-line rounded-lg text-[11px] font-extrabold text-ink"
                    >
                      {t("reflexVideoTask.writingStage.useSuggestionButton")}
                    </button>
                  </div>
                )}
                {writingError && <p className="text-xs font-bold text-rose-600">{writingError}</p>}
                <div className="flex items-center justify-between gap-2">
                  <span className="text-[11px] font-bold text-muted">
                    {t("reflexVideoTask.writingStage.attemptCount", { count: displayProgress?.writingAttemptCount ?? 0 })}
                  </span>
                  <button
                    onClick={handleSubmitWriting}
                    disabled={writingSubmitting || !answerDraft.trim()}
                    className="px-4 py-2.5 bg-teal hover:bg-teal-deep text-white rounded-xl text-xs sm:text-sm font-extrabold disabled:opacity-50"
                  >
                    {writingSubmitting ? t("reflexVideoTask.writingStage.submitting") : t("reflexVideoTask.writingStage.submitButton")}
                  </button>
                </div>
              </div>
            ) : (
              // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — đã qua bước viết (đang ở
              // bước nói, hoặc đang xem lại câu đã đạt): LUÔN hiện lại câu trả lời viết (đọc-only) để học
              // sinh dựa vào đó khi nói lại, thay vì mất hẳn khỏi màn hình như trước.
              <div className="rounded-xl border border-line bg-sky-2/40 p-3 space-y-1">
                <p className="text-[10px] font-extrabold uppercase text-teal-deep tracking-wide">{t("reflexVideoTask.writingStage.yourAnswerLabel")}</p>
                <p className="text-sm font-medium text-ink whitespace-pre-line">{displayProgress?.answerText}</p>
                {displayProgress?.writingScorePercent != null && (
                  <p className="text-[11px] font-bold text-teal-deep">
                    {t("reflexVideoTask.writingStage.scoreLabel", { score: displayProgress.writingScorePercent })}
                  </p>
                )}
              </div>
            )}

            {displayStage !== "writing" && (
              <div className="space-y-2">
                {!isReviewing && <p className="text-xs font-bold text-muted">{t("reflexVideoTask.speakingStage.instructions")}</p>}
                {recorder.recording ? (
                  <div className="flex items-center gap-1.5 px-3.5 py-2 bg-coral text-white rounded-xl text-xs font-extrabold animate-pulse w-fit">
                    <Mic size={13} /> {t("reflexVideoTask.speakingStage.recording", { elapsed: recorder.elapsedSeconds, max: recorder.maxSeconds })}
                  </div>
                ) : speakingSubmitting ? (
                  <p className="text-xs font-bold text-teal-deep">{t("reflexVideoTask.speakingStage.grading")}</p>
                ) : !displayProgress?.speakingFeedback ? (
                  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — trước đây tự động bật
                  // ghi âm ngay khi mở bước nói (kể cả khi mở lại câu đã đạt viết từ trước) — nay học sinh
                  // phải chủ động bấm mới ghi âm (mirror nút trong popup "Đạt bước viết").
                  <button
                    onClick={handleStartRecording}
                    className="flex items-center gap-1.5 px-3.5 py-2 bg-teal hover:bg-teal-deep text-white rounded-xl text-xs font-extrabold w-fit"
                  >
                    <Mic size={13} /> {t("reflexVideoTask.speakingStage.startRecordingButton")}
                  </button>
                ) : null}
                {recorder.error && <p className="text-xs font-bold text-rose-600">{recorder.error}</p>}
                {speakingError && <p className="text-xs font-bold text-rose-600">{speakingError}</p>}
                {displayProgress?.speakingFeedback && !recorder.recording && !speakingSubmitting && (
                  <div
                    className={`text-xs font-bold p-2.5 rounded-xl border ${
                      displayProgress.speakingPassed ? "bg-emerald-50 border-emerald-200 text-emerald-700" : "bg-amber-50 border-amber-200 text-amber-700"
                    }`}
                  >
                    <p>
                      {displayProgress.speakingPassed
                        ? t("reflexVideoTask.speakingStage.passedFeedbackTitle")
                        : t("reflexVideoTask.speakingStage.failedFeedbackTitle")}
                      {displayProgress.speakingScorePercent != null &&
                        ` — ${t("reflexVideoTask.speakingStage.scoreLabel", { score: displayProgress.speakingScorePercent })}`}
                    </p>
                    <p className="font-medium mt-1 normal-case whitespace-pre-line">{displayProgress.speakingFeedback}</p>
                    {!displayProgress.speakingPassed && !isReviewing && (
                      <button
                        onClick={handleRetrySpeaking}
                        className="mt-2 flex items-center gap-1.5 px-3 py-1.5 bg-white hover:bg-slate-100 border border-line rounded-lg text-[11px] font-extrabold text-ink"
                      >
                        <RotateCcw size={12} /> {t("reflexVideoTask.speakingStage.retryButton")}
                      </button>
                    )}
                  </div>
                )}
                {!isReviewing && (
                  <p className="text-[11px] font-bold text-muted">
                    {t("reflexVideoTask.speakingStage.attemptCount", { count: displayProgress?.speakingAttemptCount ?? 0 })}
                  </p>
                )}
              </div>
            )}
          </div>
        )}

        {loadingQuestions ? (
          <p className="text-xs text-muted font-bold">{t("reflexVideoTask.loadingQuestions")}</p>
        ) : questions.length === 0 ? (
          <p className="text-xs text-muted font-bold italic">{t("reflexVideoTask.noQuestions")}</p>
        ) : (
          <div className="space-y-2">
            {questions.map((q, i) => {
              const stage = stageForProgress(progress[q.id]);
              const isDisplayed = q.id === displayQuestionId;
              // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — câu ĐÃ ĐẠT bấm được để xem
              // lại kết quả (đáp án đã viết + nhận xét viết/nói) — trước đây chỉ hiện dòng tóm tắt tĩnh.
              const clickable = stage === "passed" && !isDisplayed;
              return (
                <div
                  key={q.id}
                  onClick={clickable ? () => handleReviewQuestion(q) : undefined}
                  className={`flex items-center justify-between gap-2 rounded-xl px-3.5 py-2.5 border text-xs sm:text-sm font-bold transition-opacity ${
                    isDisplayed
                      ? "opacity-0 h-0 p-0 border-0 overflow-hidden"
                      : stage === "passed"
                        ? "bg-emerald-50 border-emerald-100 text-emerald-700 cursor-pointer hover:bg-emerald-100"
                        : "bg-sky-2 border-teal/10 text-muted opacity-60"
                  }`}
                >
                  <span className="flex items-center gap-1.5">
                    {t("reflexVideoTask.question.label", { index: i + 1 })}
                    <span className="px-1.5 py-0.5 rounded-md bg-white/70 normal-case font-bold">{formatTimestamp(q.timestampSeconds)}</span>
                  </span>
                  {stage === "passed" ? (
                    <span className="flex items-center gap-1"><CheckCircle2 size={13} /> {t("reflexVideoTask.question.passed")}</span>
                  ) : (
                    <span>{t("reflexVideoTask.question.locked")}</span>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {(videoEnded || allQuestionsPassed) && !questionsError && !activeQuestion && (
        <div className="sticky bottom-0 bg-white border-t border-line p-4">
          <div className="max-w-2xl lg:max-w-3xl w-full mx-auto space-y-3">
            <div className="flex items-center justify-center gap-2 px-4 py-3 bg-emerald-50 border border-emerald-200 rounded-xl text-emerald-700 text-sm sm:text-base font-extrabold">
              <CheckCircle2 size={16} /> {t("reflexVideoTask.submittedSuccess")}
            </div>
            <button onClick={onClose} className="w-full px-4 py-2.5 bg-sky-2 hover:bg-sky text-ink rounded-xl text-xs sm:text-sm font-extrabold">
              {t("reflexVideoTask.backToList")}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
