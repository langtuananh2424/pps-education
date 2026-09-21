import React, { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { AlertTriangle, CheckCircle2, Lightbulb, Loader2, Lock, Mic, Pause, PenLine, Play, RotateCcw, ShieldAlert, Square } from "lucide-react";
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
 *
 * SPIKE (2026-08-25, đang test lại trên iPhone 8 Plus thật, xác nhận với người dùng) — thiếu
 * `playsinline=1` khiến iOS Safari/WebKit hiện card "Xem trên YouTube" (không phát inline được) thay vì
 * phát video trong trang. Lần thử trước gộp CẢ 3 tham số cùng lúc nên không rõ tham số nào gây huỷ
 * request — thử LẠI RIÊNG `playsinline=1` (không kèm modestbranding/fs) để cô lập nguyên nhân. Nếu vẫn
 * gây huỷ request, quay lại đúng dòng cũ (bỏ `&playsinline=1`).
 */
function buildLockedYouTubeEmbedSrc(videoId: string, locked: boolean): string {
  // `locked=false` (phiên XEM LẠI thuần, mọi câu đã đạt) — trả lại controls gốc YouTube (có timestamp/
  // thanh tua) để học sinh tự do xem lại, KHÔNG cần đủ 3 tham số đã bị chặn network trước đó (xem ghi
  // chú phía trên) vì chỉ đổi mỗi `controls`, không thêm modestbranding/fs/playsinline.
  return `https://www.youtube.com/embed/${videoId}?enablejsapi=1&rel=0&disablekb=1&controls=${locked ? 0 : 1}&playsinline=1`;
}

/** Trạng thái của 1 câu hỏi suy ra từ tiến trình đã lưu — quyết định UI nào hiện ở câu đang mở (writing/speaking) hay bỏ qua khi video chạy qua (passed). */
function stageForProgress(p: ReflexQuestionProgressResponse | undefined): "writing" | "speaking" | "passed" {
  if (p?.questionPassed) return "passed";
  if (p?.writingPassed) return "speaking";
  return "writing";
}

/**
 * V178 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-16) — AI bọc phần lỗi ngữ pháp/từ vựng
 * bằng markup thuần `{{err}}...{{/err}}` (BE không có sanitizer/markdown nào nên KHÔNG dùng
 * dangerouslySetInnerHTML — tự regex-split ra text node thường vs span bôi đỏ/gạch chân). Dùng chung cho
 * CẢ speakingTranscript (V178) VÀ writingMarkedAnswer (V181, bổ sung 2026-09-16) để 2 bước hiển thị lỗi
 * đồng nhất 1 kiểu.
 *
 * V185 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-16) — thêm nhận diện ký hiệu `[?]` mà
 * prompt Speaking (V183) yêu cầu AI dùng khi 1 từ/cụm từ nghe không rõ đến mức không tự tin — TRƯỚC ĐÂY
 * AI vẫn phải "đoán sát âm thanh nhất" rồi bọc {{err}}, nhưng thực tế đoán sai vẫn hiện ra như 1 từ chắc
 * chắn, gây hiểu lầm. Nay AI KHÔNG đoán nữa, chỉ để lại `[?]` — FE hiện thành 1 dấu `?` kèm chú thích
 * "không rõ từ".
 *
 * V190 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-16) — fix bug thật: V185 dùng thuộc
 * tính `title` chuẩn HTML cho chú thích, nhưng trình duyệt chỉ hiện sau khi DI CHUỘT đứng yên khá lâu
 * VÀ hoàn toàn KHÔNG hoạt động trên máy tính bảng/điện thoại (không có con trỏ chuột để "hover") — đa số
 * học sinh dùng thiết bị cảm ứng nên gần như không bao giờ thấy được chú thích. Thay bằng tooltip tự
 * dựng ({@link UnclearMarker}), bật/tắt qua chạm (`onClick`) VÀ vẫn giữ hover cho máy tính bàn.
 */
const ERROR_MARKUP = /\{\{err\}\}([\s\S]*?)\{\{\/err\}\}/g;
const UNCLEAR_MARKER = "[?]";

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-18 — chú thích tiếng Việt ngắn cho từng tiêu
 * chí chấm Speaking, hiện kèm dưới tên tiêu chí (nguyên văn tiếng Anh AI trả về — xem
 * ReflexSpeakingContentAiGradingService — không đổi được) để học sinh chưa quen thuật ngữ IELTS Speaking
 * vẫn hiểu ý nghĩa. Danh sách 4 tiêu chí CỐ ĐỊNH theo đúng prompt chấm hiện tại — nếu BE đổi/thêm tiêu
 * chí mới, tiêu chí lạ sẽ đơn giản không có phụ đề (fallback an toàn, không lỗi).
 */
const SPEAKING_CRITERIA_SUBTITLES_VI: Record<string, string> = {
  "Fluency and Coherence": "Độ trôi chảy & mạch lạc",
  "Lexical Resource": "Vốn từ vựng",
  "Grammatical Range and Accuracy": "Ngữ pháp & độ chính xác",
  Pronunciation: "Phát âm chuẩn xác",
  // Bộ tiêu chí Speaking v2 (Khối 7 Cambridge / Khối 6) — xem ReflexV2AiGradingService.
  "Grammar and Vocabulary": "Ngữ pháp & từ vựng",
  "Discourse Management": "Quản lý diễn ngôn"
};

/**
 * Luồng chấm v2 (2026-09-21) có thể gắn chú thích tiếng Việt trong ngoặc sau tên tiêu chí, VD
 * "Pronunciation (tham khảo)" hoặc "Grammatical Range and Accuracy (từ bước viết)" — bỏ phần ngoặc khi tra phụ đề.
 */
function criterionSubtitle(criterion: string): string | undefined {
  return SPEAKING_CRITERIA_SUBTITLES_VI[criterion.replace(/\s*\([^)]*\)\s*$/, "")];
}

/** Xem ghi chú V190 ở trên — thay `title` HTML (không hoạt động trên cảm ứng) bằng tooltip tự dựng. */
function UnclearMarker({ tooltip }: { tooltip: string }) {
  const [open, setOpen] = useState(false);
  return (
    <span
      className="relative inline-block cursor-help font-extrabold text-amber-600 underline decoration-dotted decoration-2 underline-offset-2"
      onClick={(e) => {
        e.stopPropagation();
        setOpen((o) => !o);
      }}
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
    >
      ?
      {open && (
        <span className="absolute z-10 left-1/2 -translate-x-1/2 bottom-full mb-1 whitespace-nowrap rounded-md bg-ink text-white text-[11px] font-medium px-2 py-1 normal-case">
          {tooltip}
        </span>
      )}
    </span>
  );
}

function renderUnclearMarkers(text: string, keyPrefix: string, tooltip: string): React.ReactNode[] {
  const segments = text.split(UNCLEAR_MARKER);
  const nodes: React.ReactNode[] = [];
  segments.forEach((segment, idx) => {
    if (segment) nodes.push(<React.Fragment key={`${keyPrefix}-t${idx}`}>{segment}</React.Fragment>);
    if (idx < segments.length - 1) {
      nodes.push(<UnclearMarker key={`${keyPrefix}-q${idx}`} tooltip={tooltip} />);
    }
  });
  return nodes;
}

function renderHighlightedErrors(text: string, unclearTooltip: string): React.ReactNode[] {
  const parts: React.ReactNode[] = [];
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  let key = 0;
  ERROR_MARKUP.lastIndex = 0;
  while ((match = ERROR_MARKUP.exec(text)) !== null) {
    if (match.index > lastIndex) {
      parts.push(...renderUnclearMarkers(text.slice(lastIndex, match.index), `plain-${key}`, unclearTooltip));
    }
    parts.push(
      // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-18 — đổi từ gạch chân sang "chip" bo
      // tròn viền đỏ nhạt (dễ nhận biết hơn trên câu dài, đặc biệt trong khung transcript mới).
      <span key={`err-${key}`} className="inline-block rounded-md border border-rose-300 bg-rose-50 px-1 font-semibold text-rose-600">
        {renderUnclearMarkers(match[1], `errinner-${key}`, unclearTooltip)}
      </span>
    );
    key++;
    lastIndex = ERROR_MARKUP.lastIndex;
  }
  if (lastIndex < text.length) {
    parts.push(...renderUnclearMarkers(text.slice(lastIndex), `tail-${key}`, unclearTooltip));
  }
  return parts;
}

/**
 * Ghi âm trực tiếp qua microphone (MediaRecorder API) — 1 instance dùng chung cho cả trang, vì chỉ 1
 * câu được ghi âm tại 1 thời điểm (câu đang mở khoá — video tạm dừng chờ trong lúc ghi/chấm).
 *
 * SPIKE (2026-08-25, xác nhận với người dùng, phản hồi thật khi test trên iPhone) — fix bug UX thật:
 * TRƯỚC ĐÂY mỗi câu hỏi (video 6 câu → 6 lần) gọi `getUserMedia()` MỚI HOÀN TOÀN, và `recorder.onstop`
 * chủ động dừng hẳn track sau mỗi câu — khiến trình duyệt (đặc biệt WebKit/Edge trên iOS) xin lại quyền
 * micro liên tục suốt bài, rất khó chịu. SỬA: giữ nguyên 1 `MediaStream` duy nhất cho CẢ PHIÊN làm bài
 * (`ensureStream` chỉ gọi `getUserMedia()` nếu chưa có stream còn sống), tái dùng cho mọi câu — chỉ dừng
 * hẳn khi rời trang (cleanup effect khi unmount).
 */
/**
 * V184 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-16) — fix constraint audio (V183)
 * không đủ: 1 lượt ghi âm sau đó vẫn bị AI (kể cả Whisper, độc lập hoàn toàn với Gemini) nghe sai hẳn
 * nội dung — đo được mean volume -30.2dB (nhỏ hơn hẳn các lượt trước). Nguyên nhân gốc là học sinh nói
 * quá nhỏ, KHÔNG phải do encode/model — không có xử lý code nào "phục hồi" được giọng nói chưa từng đủ
 * to. Ngưỡng RMS dưới đây ước lượng từ chính 2 mẫu lỗi thật đã đo (không phải số liệu chuẩn hoá y khoa/
 * audio-engineering), có thể cần tinh chỉnh lại sau khi thu thập thêm dữ liệu thật.
 */
const QUIET_RMS_THRESHOLD = 0.035;
/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-18 — fix bug thật: mỗi mẫu RMS lấy mỗi 500ms
 * (xem `timerRef` interval trong `start()`), nên 1 lượt ghi RẤT NGẮN (VD 1 giây) chỉ có 1-2 mẫu — trung
 * bình dựa trên quá ít mẫu dễ trúng đúng khoảnh khắc im lặng (VD đầu câu, giữa 2 từ) rồi báo "hơi nhỏ"
 * dù học sinh nói to bình thường ở phần còn lại. Yêu cầu tối thiểu 4 mẫu (~2 giây) mới đánh giá âm
 * lượng — ghi ngắn hơn thì bỏ qua cảnh báo này (để cổng chặn nội dung "quá ngắn" phía AI chấm — xem
 * ReflexSpeakingContentAiGradingService — báo đúng lý do thay vì lẫn với cảnh báo âm lượng gây hiểu lầm).
 */
const MIN_VOLUME_SAMPLES_FOR_QUIET_CHECK = 4;

function useAudioRecorder() {
  const { t } = useTranslation("portal-exercises");
  const [recording, setRecording] = useState(false);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [maxSeconds, setMaxSeconds] = useState(0);
  const [audioBlob, setAudioBlob] = useState<Blob | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [quietWarning, setQuietWarning] = useState(false);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const streamRef = useRef<MediaStream | null>(null);
  const timerRef = useRef<number | null>(null);
  const audioCtxRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const volumeSumRef = useRef(0);
  const volumeSamplesRef = useRef(0);

  const closeVolumeMeter = () => {
    analyserRef.current = null;
    if (audioCtxRef.current) {
      audioCtxRef.current.close().catch(() => {});
      audioCtxRef.current = null;
    }
  };

  const stop = () => {
    recorderRef.current?.stop();
    setRecording(false);
    if (timerRef.current) {
      window.clearInterval(timerRef.current);
      timerRef.current = null;
    }
    const avgRms = volumeSamplesRef.current > 0 ? volumeSumRef.current / volumeSamplesRef.current : 0;
    setQuietWarning(volumeSamplesRef.current >= MIN_VOLUME_SAMPLES_FOR_QUIET_CHECK && avgRms > 0 && avgRms < QUIET_RMS_THRESHOLD);
    closeVolumeMeter();
  };

  /**
   * Trả lại stream micro đang còn sống nếu có, chỉ xin quyền lại khi chưa từng xin hoặc track đã bị dừng.
   *
   * V183 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-16) — fix bug thật phát hiện qua test AI
   * chấm Speaking: để trống `audio: true` khiến trình duyệt tự bật `noiseSuppression`/`autoGainControl`
   * mặc định, có lúc xử lý quá tay làm mất gần hết dải tần số thấp của giọng nói thật (xác nhận qua
   * spectrogram — mất hẳn dải <2000Hz ở 1 số lượt ghi) — khiến AI (kể cả Whisper, không liên quan gì tới
   * Gemini) nghe sai nội dung nhiều hơn hẳn so với các lượt ghi giữ được dải tần thấp. Tắt hẳn 2 xử lý
   * này, chỉ giữ `echoCancellation` (không ảnh hưởng dải tần giọng, chỉ chống hú loa-mic).
   */
  const ensureStream = async (): Promise<MediaStream> => {
    const existing = streamRef.current;
    if (existing && existing.getAudioTracks().some((tr) => tr.readyState === "live")) {
      return existing;
    }
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: { echoCancellation: true, noiseSuppression: false, autoGainControl: false }
    });
    streamRef.current = stream;
    return stream;
  };

  const start = async (limitSeconds: number) => {
    setError(null);
    setQuietWarning(false);
    try {
      const stream = await ensureStream();
      chunksRef.current = [];
      const recorder = new MediaRecorder(stream);
      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data);
      };
      recorder.onstop = () => {
        setAudioBlob(new Blob(chunksRef.current, { type: recorder.mimeType || "audio/webm" }));
        // KHÔNG dừng track ở đây nữa — giữ nguyên stream để tái dùng cho câu tiếp theo (xem ghi chú
        // trên hàm), tránh xin quyền mic lại liên tục.
      };
      recorder.start();
      recorderRef.current = recorder;
      setMaxSeconds(limitSeconds);
      setRecording(true);
      setElapsedSeconds(0);

      // V184 — đo mức âm lượng trong lúc ghi (KHÔNG ảnh hưởng chất lượng audio thật lưu lại, chỉ đọc
      // song song qua AnalyserNode) để cảnh báo học sinh nói quá nhỏ ngay sau khi dừng ghi.
      volumeSumRef.current = 0;
      volumeSamplesRef.current = 0;
      try {
        const AudioContextCtor = window.AudioContext || (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
        if (AudioContextCtor) {
          const audioCtx = new AudioContextCtor();
          const source = audioCtx.createMediaStreamSource(stream);
          const analyser = audioCtx.createAnalyser();
          analyser.fftSize = 2048;
          source.connect(analyser);
          audioCtxRef.current = audioCtx;
          analyserRef.current = analyser;
        }
      } catch {
        // Môi trường không hỗ trợ AnalyserNode (hiếm) — bỏ qua cảnh báo âm lượng, không chặn ghi âm.
      }

      const startedAt = Date.now();
      timerRef.current = window.setInterval(() => {
        const elapsed = Math.round((Date.now() - startedAt) / 1000);
        setElapsedSeconds(elapsed);
        const analyser = analyserRef.current;
        if (analyser) {
          const data = new Float32Array(analyser.fftSize);
          analyser.getFloatTimeDomainData(data);
          let sumSquares = 0;
          for (let i = 0; i < data.length; i++) sumSquares += data[i] * data[i];
          volumeSumRef.current += Math.sqrt(sumSquares / data.length);
          volumeSamplesRef.current += 1;
        }
        if (elapsed >= limitSeconds) stop();
      }, 500);
    } catch {
      setError(t("reflexVideoTask.micAccessError"));
    }
  };

  const reset = () => {
    setAudioBlob(null);
    setQuietWarning(false);
  };

  useEffect(
    () => () => {
      streamRef.current?.getTracks().forEach((t) => t.stop());
      if (timerRef.current) window.clearInterval(timerRef.current);
      closeVolumeMeter();
    },
    []
  );

  return { recording, elapsedSeconds, maxSeconds, audioBlob, error, quietWarning, start, stop, reset, ensureStream };
}

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25 — lớp phủ bấm play/pause tự dựng đè lên
 * video/audio đang khoá (video/audio bản thân luôn giữ `pointer-events-none`, không lộ ra bất kỳ điều
 * khiển gốc nào — KHÔNG tách được play/pause riêng khỏi seek bar của control gốc YouTube/HTML5). Chỉ
 * bấm được (`canToggle`) khi video đang ở 1 trong 2 trạng thái ĐƯỢC PHÉP chạy (chạy tự do giữa 2 câu,
 * hoặc đang cho nghe lại câu hỏi chờ ghi âm) — bấm lúc đó CHỈ toggle play/pause, không có cách nào tua.
 */
function ManualPauseOverlay({
  canToggle,
  paused,
  onToggle,
  labelPlay,
  labelPause
}: {
  canToggle: boolean;
  paused: boolean;
  onToggle: () => void;
  labelPlay: string;
  labelPause: string;
}) {
  return (
    <button
      type="button"
      onClick={onToggle}
      disabled={!canToggle}
      aria-label={paused ? labelPlay : labelPause}
      className={`absolute inset-0 flex items-center justify-center bg-transparent ${canToggle ? "group cursor-pointer" : "cursor-default"}`}
    >
      {canToggle && (
        <span className="opacity-0 group-hover:opacity-100 group-focus-visible:opacity-100 group-active:opacity-100 transition-opacity bg-ink/60 rounded-full p-4">
          {paused ? <Play size={28} className="text-white" /> : <Pause size={28} className="text-white" />}
        </span>
      )}
    </button>
  );
}

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-18 — SPIKE lần 2, đổi hẳn kiểu dáng sau khi
 * người dùng xem 3 mẫu Word Art và chọn phong cách mềm mại hơn (không phải "Sticker Nổi" viền đen dày ở
 * bản trước) — viền màu mảnh + nền trắng, pill nhãn đè nhẹ lên mép trên card, nghiêng nhẹ như 2 nét chữ
 * V chụm vào nhau (`tiltClass` truyền từ nơi gọi: bên trái nghiêng trái, bên phải nghiêng phải), rung lắc
 * (`animate-wiggle`, khai báo ở index.css) khi di chuột qua rồi tự về lại đúng góc nghiêng tĩnh ban đầu.
 */
function ScoreSticker({
  icon,
  label,
  percent,
  tone,
  tiltClass
}: {
  icon: React.ReactNode;
  label: string;
  percent: number | null;
  tone: "pass" | "fail" | "pending";
  tiltClass: string;
}) {
  const border = tone === "pass" ? "border-teal" : tone === "fail" ? "border-coral" : "border-line";
  const text = tone === "pass" ? "text-teal-deep" : tone === "fail" ? "text-coral" : "text-muted";
  return (
    // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-18 — thu nhỏ hẳn trên di động (mặc định,
    // dưới `sm:`) — sticker luôn `absolute` ghim góc phải trên (xem nơi gọi) ở MỌI kích thước màn hình,
    // nên phải đủ nhỏ để không đè lên dòng "CÂU HỎI n" bên trái kể cả ở điện thoại nhỏ nhất (320px, đã
    // test — 68px/thẻ vẫn hụt vài px, giảm xuống 58px mới đủ an toàn).
    <div className={`${tiltClass} wiggle-on-hover shrink-0 cursor-default`}>
      <span
        className={`relative z-10 -mb-1.5 ml-2 flex w-fit items-center gap-1 rounded-full border-2 ${border} bg-white px-1.5 py-0.5 text-[8px] font-extrabold uppercase ${text} whitespace-nowrap sm:-mb-2 sm:ml-3 sm:px-2.5 sm:py-1 sm:text-[10px]`}
      >
        {icon} {label}
      </span>
      <div
        className={`flex w-[58px] items-center justify-center rounded-xl border-2 ${border} bg-white pt-2 pb-1 shadow-md sm:w-[114px] sm:rounded-2xl sm:pt-4 sm:pb-2.5`}
      >
        <span className={`font-display text-base font-extrabold sm:text-4xl ${text}`}>
          {percent != null ? percent : "—"}
          {percent != null && <span className="align-top text-[9px] sm:text-lg">%</span>}
        </span>
      </div>
    </div>
  );
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
        // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — fix bug thật: bấm "Xem bài đã
        // nộp" trên 1 bài REFLEX đã hoàn thành hết vẫn hiện màn hình "Bắt đầu làm bài" (xin quyền
        // micro, sẵn sàng giám sát...) y hệt lúc làm bài thật — vô lý cho việc chỉ XEM LẠI kết quả. Nếu
        // mọi câu đã đạt sẵn ngay từ lúc tải trang, bỏ qua thẳng màn hình bắt đầu (không xin quyền
        // micro/không bật giám sát — `useIntegrityMonitor` đã tự tắt khi `allQuestionsPassed`), học sinh
        // vào thẳng được trang để bấm xem lại từng câu (tính năng "Đang chờ bạn"/xem lại đã có sẵn).
        if (sorted.length > 0 && sorted.every((q) => saved.find((p) => p.questionId === q.id)?.questionPassed)) {
          setStarted(true);
          setReviewOnlyMode(true);
        }
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
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — fix bug thật: đã bật `started=true`
   * tự động cho phiên XEM LẠI thuần (mọi câu đã đạt), nhưng KHÔNG có bước nào từng gọi seek/play cho
   * video trong phiên đó (xem playFromResumePoint — không làm gì nếu không còn câu nào đang chờ) — YouTube
   * iframe đứng nguyên ở trạng thái "chưa từng phát", chỉ hiện card thumbnail/branding mặc định (không
   * timestamp, không tua được), khóa video (controls=0 + overlay chặn click) vốn dành cho lúc LÀM BÀI
   * THẬT giờ chỉ khiến video trông như hỏng. Khi xem lại thuần: mở khóa control gốc YouTube/native +
   * bỏ overlay chặn click — không còn lý do gì phải khóa video khi đã hoàn thành hết.
   */
  const [reviewOnlyMode, setReviewOnlyMode] = useState(false);
  const reviewOnlyModeRef = useRef(false);
  useEffect(() => {
    reviewOnlyModeRef.current = reviewOnlyMode;
  }, [reviewOnlyMode]);
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

  /**
   * Câu hỏi đang mở khoá gate video (video đang tạm dừng chờ) — null nghĩa là video đang chạy tự do.
   *
   * Bổ sung 2026-09-04 (đã xác nhận với người dùng) — fix bug thật (báo qua test trên điện thoại: video
   * không dừng theo mốc, chạy liền mạch): TRƯỚC ĐÂY đồng bộ ref qua useEffect (chạy SAU khi React commit,
   * lệch 1 nhịp render) — activateQuestion() gọi setActiveQuestionId(q.id) RỒI pauseVideo() ngay trong
   * cùng lượt thực thi, nhưng sự kiện "pause"/PAUSED trình duyệt bắn ra đôi khi tới TRƯỚC khi effect kịp
   * chạy, khiến 2 guard "chặn tạm dừng ngoài ý muốn" (native onPause/YouTube PAUSED bên dưới) đọc phải
   * activeQuestionIdRef.current còn là giá trị CŨ (null) — hiểu nhầm "video đang chạy tự do bị dừng
   * ngoài ý muốn" nên tự ép chạy lại NGAY, huỷ luôn pause vừa gọi. Race này thắng/thua tuỳ engine/thiết
   * bị nên trước đây test không phát hiện ra. Sửa đúng mirror 3 cờ khác trong file này (awaitingNextMark/
   * userPaused/isReviewingVideo) — gán ref ĐỒNG BỘ NGAY LẬP TỨC qua wrapper, không chờ useEffect.
   */
  const [activeQuestionId, setActiveQuestionIdState] = useState<number | null>(null);
  const activeQuestionIdRef = useRef<number | null>(null);
  const setActiveQuestionId = (value: number | null) => {
    activeQuestionIdRef.current = value;
    setActiveQuestionIdState(value);
  };
  const triggeredQuestionIdsRef = useRef<Set<number>>(new Set());
  /**
   * V149 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23, mô tả lại lần 2 sau khi làm sai
   * lần 1 — xem Javadoc firstPendingQuestionIndex) — true trong lúc video đang CHỦ Ý chạy tiếp để học
   * sinh nghe lại câu hỏi trong khi ghi âm (giữa lúc đạt bước viết và chạm mốc câu KẾ TIẾP) — dùng để
   * handleTimeUpdate biết cần theo dõi mốc dừng tiếp, và để 2 guard "chặn tạm dừng ngoài ý muốn"
   * (native onPause/YouTube PAUSED) biết đây vẫn là lúc PHẢI đang chạy (ép chạy lại nếu học sinh cố tình
   * bấm tạm dừng), phân biệt với lúc video đứng yên có chủ đích ở bước viết.
   */
  const awaitingNextMarkRef = useRef(false);
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25 — bản mirror dạng state (không chỉ
   * ref) của `awaitingNextMarkRef`, CHỈ để tính điều kiện HIỂN THỊ nút play/pause tự dựng bên dưới (JSX
   * đọc state qua render bình thường, không đọc được ref) — mọi logic điều khiển video thật vẫn dùng
   * đúng `awaitingNextMarkRef` (đồng bộ tức thời, không lệch 1 nhịp render như state). Luôn gọi
   * `setAwaitingNextMark` (set CẢ 2) thay vì gán thẳng `awaitingNextMarkRef.current`.
   */
  const [awaitingNextMark, setAwaitingNextMarkState] = useState(false);
  const setAwaitingNextMark = (value: boolean) => {
    awaitingNextMarkRef.current = value;
    setAwaitingNextMarkState(value);
  };
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25 — học sinh được phép tự bấm play/pause
   * (nút tự dựng, xem overlay video bên dưới) trong lúc video đang chạy tự do (giữa 2 câu) hoặc đang cho
   * nghe lại câu hỏi (`awaitingNextMark=true`) — KHÔNG cho tua (không có seek bar nào lộ ra). Cờ này để
   * 2 guard "chặn tạm dừng ngoài ý muốn" (native onPause/YouTube PAUSED) phân biệt "học sinh CHỦ Ý bấm
   * pause" (giữ nguyên trạng thái dừng) với "bị dừng ngoài ý muốn" (VD phím media cứng — ép chạy lại).
   */
  const userPausedRef = useRef(false);
  const [userPaused, setUserPausedState] = useState(false);
  const setUserPaused = (value: boolean) => {
    userPausedRef.current = value;
    setUserPausedState(value);
  };
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — cho phép bấm lại 1 câu ĐÃ ĐẠT trong
   * danh sách bên dưới để xem lại kết quả (trước đây các câu đã đạt chỉ hiện dòng tóm tắt, không xem lại
   * được câu trả lời/nhận xét). Tách riêng khỏi `activeQuestionId` (câu THẬT đang mở khoá video chờ làm)
   * để không phá luồng làm bài thật đang dang dở — panel HIỂN THỊ ưu tiên câu đang xem lại (nếu có), còn
   * các handler nộp bài (handleSubmitWriting/handleSubmitSpeaking...) vẫn luôn thao tác trên câu THẬT.
   */
  const [reviewQuestionId, setReviewQuestionId] = useState<number | null>(null);
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25 — cho phép học sinh bấm vào 1 câu ĐÃ MỞ
   * (đang làm dở HOẶC đã đạt, không phải "Chưa mở") để TUA video lùi về đúng mốc câu đó và PHÁT LẠI nghe/
   * xem — trước đây (2026-08-23) cố tình KHÔNG seek video khi đang xem lại giữa phiên làm bài thật để
   * tránh làm sai vị trí tiếp tục sau đó; nay hỗ trợ đúng yêu cầu "nghe lại câu hỏi" bằng cách NHỚ LẠI vị
   * trí thật trước khi tua đi (`realPositionBeforeReviewRef`), rồi KHÔI PHỤC đúng vị trí + đúng trạng thái
   * chạy/dừng thật khi đóng xem lại (xem handleReplayQuestion/handleCloseReview). Trong lúc đang xem lại
   * (`isReviewingVideoRef=true`), `handleTimeUpdate`/2 guard "chặn tạm dừng ngoài ý muốn" đều bỏ qua
   * hoàn toàn (video xem lại được tua/phát/dừng tự do, không đụng gì tới cơ chế mốc thật).
   */
  const isReviewingVideoRef = useRef(false);
  const [isReviewingVideo, setIsReviewingVideoState] = useState(false);
  const setIsReviewingVideo = (value: boolean) => {
    isReviewingVideoRef.current = value;
    setIsReviewingVideoState(value);
  };
  const realPositionBeforeReviewRef = useRef<number | null>(null);

  const [answerDraft, setAnswerDraft] = useState("");
  const [writingSubmitting, setWritingSubmitting] = useState(false);
  const [writingError, setWritingError] = useState<string | null>(null);
  const [speakingSubmitting, setSpeakingSubmitting] = useState(false);
  const [speakingError, setSpeakingError] = useState<string | null>(null);
  const [speakingPassedPopup, setSpeakingPassedPopup] = useState<{ scorePercent: number | null; feedback: string | null } | null>(null);
  const [writingPassedPopup, setWritingPassedPopup] = useState<{ scorePercent: number | null } | null>(null);
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
  const seekTo = (seconds: number) => {
    if (isYouTube) youTubePlayerRef.current?.seekTo?.(seconds, true);
    else if (mediaRef.current) mediaRef.current.currentTime = seconds;
  };
  /** Vị trí phát THẬT hiện tại (giây) — dùng để nhớ lại trước khi tua đi "nghe lại", xem isReviewingVideoRef. */
  const getCurrentPlaybackTime = (): number => {
    if (isYouTube) return youTubePlayerRef.current?.getCurrentTime?.() ?? 0;
    return mediaRef.current?.currentTime ?? 0;
  };

  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25 — cho phép học sinh bấm play/pause
   * (nút tự dựng đè lên video, KHÔNG dùng thanh điều khiển gốc YouTube/HTML5 vì thanh đó luôn kèm sẵn
   * seek bar không tách rời được) trong lúc video đang chạy TỰ DO (giữa 2 câu, activeQuestionId==null)
   * hoặc đang cho NGHE LẠI câu hỏi chờ ghi âm (awaitingNextMark=true), hoặc đang XEM LẠI 1 đoạn cũ theo
   * yêu cầu (isReviewingVideo=true, xem handleReplayQuestion) — bấm khi đang ở bước VIẾT (video đứng yên
   * có chủ đích, chưa qua bước nghe lại) thì bỏ qua — không có gì để play/pause lúc đó.
   */
  const canToggleManualPause = activeQuestionId == null || awaitingNextMark || isReviewingVideo;
  /**
   * SPIKE (2026-08-25, xác nhận với người dùng, phát hiện thật khi test) — fix bug thật: đóng popup
   * "Đạt bước nói" (nút "Tiếp tục", xem handleContinueAfterSpeakingPass) lộ NGAY ManualPauseOverlay ở
   * đúng vị trí trên video (activeQuestionId=null → canToggleManualPause=true ngay lập tức) — trên mobile
   * đôi khi 1 cú chạm bị "chạm xuyên" (click-through) sang phần tử vừa lộ ra ngay dưới ngón tay, tự bấm
   * tạm dừng NGAY sau khi vừa resumeVideo(), làm video đứng im ngay từ đầu đoạn nghe lại chuyển câu mà
   * học sinh không hề chủ ý bấm gì. Khoá tạm nút play/pause tự dựng trong ít mili-giây ngay sau khi
   * chuyển câu để tránh nhận nhầm cú chạm đó.
   */
  const suppressManualToggleUntilRef = useRef(0);
  const handleToggleManualPause = () => {
    if (!canToggleManualPause || Date.now() < suppressManualToggleUntilRef.current) return;
    if (userPausedRef.current) {
      setUserPaused(false);
      resumeVideo();
    } else {
      setUserPaused(true);
      pauseVideo();
    }
  };

  /**
   * V149 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23, mô tả lại lần 2 — SỬA lại thiết
   * kế V145/lần mô tả đầu bị hiểu sai) — mỗi câu hỏi mở bảng NGAY khi video chạm ĐÚNG mốc CỦA CHÍNH NÓ
   * (không phải mốc câu kế tiếp): bảng VIẾT mở, video đứng yên.
   *
   * SPIKE (2026-08-25, xác nhận với người dùng — ĐẢO NGƯỢC 1 phần quyết định V149) — TRƯỚC ĐÂY đạt bước
   * viết thì video CHẠY TIẾP để "nghe lại câu hỏi" trong lúc chuẩn bị nói, chỉ đứng yên thật sự ở bước
   * VIẾT. Người dùng yêu cầu đổi lại: video đứng yên XUYÊN SUỐT cả bước viết LẪN bước nói, CHỈ chạy tiếp
   * khi đã đạt CẢ 2 bước (xem handleContinueAfterSpeakingPass) — bỏ hẳn cơ chế "nghe lại câu hỏi trong
   * lúc ghi âm", `awaitingNextMark` không còn bao giờ bật true nữa (giữ lại biến/hạ tầng liên quan cho
   * các luồng khác — xem lại nếu cần dọn hẳn sau này).
   */
  const activateQuestion = (q: ReviewVideoQuestionResponse) => {
    setActiveQuestionId(q.id);
    setUserPaused(false);
    setAwaitingNextMark(false);
    pauseVideo();
    const p = progressRef.current[q.id];
    setAnswerDraft(p?.answerText ?? "");
    setWritingError(null);
    setSpeakingError(null);
    recorder.reset();
  };

  const firstPendingQuestionIndex = () => questions.findIndex((q) => stageForProgress(progressRef.current[q.id]) !== "passed");

  const handleTimeUpdate = (currentSeconds: number) => {
    if (videoEndedRef.current || isReviewingVideoRef.current) return;
    const idx = firstPendingQuestionIndex();
    if (idx === -1) return;
    const current = questions[idx];
    if (activeQuestionIdRef.current != null) {
      // Câu THẬT đang mở bảng — chỉ còn việc theo dõi mốc câu KẾ TIẾP để tự dừng lúc đang cho nghe lại
      // trong lúc chuẩn bị nói (awaitingNextMarkRef=true, xem activateQuestion/handleContinueAfterWritingPass).
      // Bước viết (awaitingNextMarkRef=false) thì video đứng yên, không cần theo dõi gì thêm.
      //
      // Bổ sung 2026-09-04 (đã xác nhận với người dùng, báo qua test điện thoại thật — bảng viết mở
      // đúng nhưng video vẫn tự chạy tiếp) — fix bug thật: lệnh pauseVideo() gọi 1 LẦN DUY NHẤT trong
      // activateQuestion() có thể bị YouTube IFrame API trên di động BỎ LỠ/xử lý trễ qua postMessage
      // (không có cách nào chờ xác nhận lệnh đã tới nơi) — video thực tế vẫn chạy dù state app đã đúng
      // (activeQuestionId đã set, bảng đã mở). Poll đang chạy mỗi 250ms nên GỌI LẠI pauseVideo() ở MỌI
      // tick trong lúc lẽ ra phải đứng yên (bước viết) — vô hại khi video đã thực sự dừng (gọi lại
      // pauseVideo() trên video đang pause không có tác dụng phụ), nhưng đảm bảo tối đa ~250ms sau khi
      // lệnh đầu bị lỡ thì lệnh kế tiếp sẽ bắt lại được, thay vì phó mặc đúng 1 lần duy nhất.
      if (!awaitingNextMarkRef.current) {
        pauseVideo();
        return;
      }
      const next = questions[idx + 1];
      if (next && currentSeconds >= next.timestampSeconds) {
        setAwaitingNextMark(false);
        pauseVideo();
      }
      return;
    }
    if (currentSeconds >= current.timestampSeconds && !triggeredQuestionIdsRef.current.has(current.id)) {
      triggeredQuestionIdsRef.current.add(current.id);
      activateQuestion(current);
    }
  };
  /**
   * Callback trong YouTube IFrame API (poll interval) được tạo 1 LẦN trong useEffect, không nắm được
   * bản mới nhất của handleTimeUpdate ở mỗi lần render (đóng gói lúc effect chạy) — dùng ref để interval
   * luôn gọi đúng phiên bản mới nhất.
   */
  const handleTimeUpdateRef = useRef(handleTimeUpdate);
  handleTimeUpdateRef.current = handleTimeUpdate;

  /**
   * Video kết thúc TỰ NHIÊN — chỉ có ý nghĩa khi câu đang dở là câu CUỐI (không có mốc kế tiếp để dừng
   * theo).
   *
   * SPIKE (2026-08-25, xác nhận với người dùng, phát hiện thật khi test) — fix bug thật: TRƯỚC ĐÂY set
   * `videoEnded=true` VÔ ĐIỀU KIỆN ngay khi nhận sự kiện `ENDED`, kể cả khi câu đang dở KHÔNG phải câu
   * cuối — YouTube IFrame API đôi khi bắn `ENDED` SỚM ngoài ý muốn (gặp khi video bị seek liên tục, VD
   * tính năng nghe lại/tua về mốc cũ), dù video CHƯA thật sự phát hết. `videoEnded=true` sai lúc đó làm
   * hiện nhầm banner "đã hoàn thành tất cả câu hỏi" (điều kiện JSX `videoEnded || allQuestionsPassed`)
   * dù học sinh còn nhiều câu chưa làm. Sửa: CHỈ tin `ENDED` là thật khi câu đang dở đúng là câu cuối
   * (hoặc không còn câu nào dở) — nếu không, coi là tín hiệu giả, thử phát lại thay vì đóng băng luôn.
   */
  const handleVideoNaturallyEnded = () => {
    if (isReviewingVideoRef.current) return; // đang xem lại 1 đoạn cũ, không phải video kết thúc thật.
    const idx = firstPendingQuestionIndex();
    const isLastPendingQuestion = idx === -1 || !questions[idx + 1];
    if (!isLastPendingQuestion) {
      // ENDED giả — còn câu chưa phải câu cuối, video thật ra chưa hết. Chỉ tự phát lại nếu lúc đó video
      // ĐANG LẼ RA phải chạy (giữa 2 câu, hoặc đang cho nghe lại) — không đụng vào lúc video đứng yên có
      // chủ đích ở bước viết.
      if (activeQuestionIdRef.current == null || awaitingNextMarkRef.current) resumeVideo();
      return;
    }
    setVideoEnded(true);
    if (activeQuestionIdRef.current != null) return;
    if (idx !== -1) activateQuestion(questions[idx]);
  };
  const handleVideoNaturallyEndedRef = useRef(handleVideoNaturallyEnded);
  handleVideoNaturallyEndedRef.current = handleVideoNaturallyEnded;

  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — fix bug thật: học sinh thoát ra giữa
   * chừng rồi vào lại (đã đạt sẵn 1+ câu từ phiên trước) — bấm "Bắt đầu làm bài" phải NHẢY THẲNG (seek)
   * tới đúng mốc câu đang làm dở, không phát lại từ đầu qua các câu đã đạt. Câu đang dở là câu ĐẦU TIÊN
   * (phiên hoàn toàn mới, chưa đạt câu nào) thì KHÔNG seek — phát bình thường từ đầu như luồng gốc.
   */
  const playFromResumePoint = () => {
    const idx = firstPendingQuestionIndex();
    // Đã đạt hết mọi câu (VD đang chỉ XEM LẠI kết quả bài đã hoàn thành, xem `started` tự bật ở effect
    // tải câu hỏi/tiến độ) — không còn câu nào cần phát/mở, không tự chạy video (tránh phát tiếng ngoài
    // ý muốn khi học sinh chỉ vào xem lại, không phải làm bài).
    if (idx === -1) return;
    if (idx > 0) seekTo(questions[idx].timestampSeconds);
    setUserPaused(false);
    resumeVideo();
  };
  const playFromResumePointRef = useRef(playFromResumePoint);
  playFromResumePointRef.current = playFromResumePoint;

  // ---- Native <video>/<audio> (R2_VIDEO/R2_AUDIO) — khóa control, tự phát, tự bám mốc thời gian. ----
  useEffect(() => {
    if (isYouTube) return;
    const media = mediaRef.current;
    if (!media) return;
    const onTimeUpdate = () => handleTimeUpdateRef.current(media.currentTime);
    const onEnded = () => handleVideoNaturallyEndedRef.current();
    const onPause = () => {
      // Best-effort chặn tạm dừng ngoài ý muốn (VD phím media cứng trên bàn phím) — ép chạy lại khi
      // KHÔNG có câu nào đang mở (activeQuestionIdRef == null, video được chạy tự do), HOẶC đang mở
      // nhưng ở bước NGHE LẠI câu hỏi (awaitingNextMarkRef=true, video PHẢI đang chạy — xem V149) — chỉ
      // thật sự cho đứng yên ở bước VIẾT (awaitingNextMarkRef=false) hoặc lúc XEM LẠI thuần. Bổ sung
      // 2026-08-25 — cũng KHÔNG ép chạy lại khi học sinh CHỦ Ý bấm nút pause tự dựng (userPausedRef=true,
      // xem overlay video) — phân biệt với dừng ngoài ý muốn — HOẶC đang xem lại 1 đoạn cũ
      // (isReviewingVideoRef=true, video xem lại được tự do play/pause, không ép gì cả).
      if (
        started &&
        !media.ended &&
        !reviewOnlyModeRef.current &&
        !userPausedRef.current &&
        !isReviewingVideoRef.current &&
        (activeQuestionIdRef.current == null || awaitingNextMarkRef.current)
      ) {
        media.play?.().catch(() => undefined);
      }
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
          onReady: () => {
            // Nếu học sinh đã bấm "Bắt đầu" trước khi player YouTube sẵn sàng (hiếm, do tải chậm).
            if (startedRef.current) playFromResumePointRef.current();
          },
          onStateChange: (e: any) => {
            if (e.data === YT.PlayerState.ENDED) {
              handleVideoNaturallyEndedRef.current();
              if (pollId) window.clearInterval(pollId);
            } else if (e.data === YT.PlayerState.PLAYING) {
              if (pollId) window.clearInterval(pollId);
              pollId = window.setInterval(() => {
                const time = youTubePlayerRef.current?.getCurrentTime?.();
                if (time != null) handleTimeUpdateRef.current(time);
              }, 250);
            } else if (e.data === YT.PlayerState.PAUSED) {
              // Best-effort chặn tạm dừng ngoài ý muốn — mirror đúng điều kiện ở nhánh native onPause
              // phía trên (activeQuestionIdRef==null HOẶC đang ở bước nghe lại awaitingNextMarkRef=true),
              // trừ khi học sinh CHỦ Ý bấm nút pause tự dựng (userPausedRef=true) hoặc đang xem lại 1
              // đoạn cũ (isReviewingVideoRef=true).
              if (
                startedRef.current &&
                !videoEndedRef.current &&
                !reviewOnlyModeRef.current &&
                !userPausedRef.current &&
                !isReviewingVideoRef.current &&
                (activeQuestionIdRef.current == null || awaitingNextMarkRef.current)
              ) {
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

  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-18 — TRƯỚC ĐÂY khi qua bước Nói, bài viết
   * bước 1 vẫn hiện cố định phía trên phần ghi âm (đọc-only) — học sinh nhìn thấy nguyên văn bài viết
   * trong lúc nói lại, mất tác dụng "phản xạ" (không tự nói mà dựa hẳn vào bài viết). Sửa: tách 2 bước
   * thành 2 tab đổi qua lại — mặc định LUÔN mở tab "Nói" (không lộ bài viết), học sinh phải chủ động bấm
   * qua tab "Viết" mới xem lại được. Reset về tab "Nói" mỗi khi đổi câu đang hiển thị.
   */
  const [answerViewTab, setAnswerViewTab] = useState<"writing" | "speaking">("speaking");
  useEffect(() => {
    setAnswerViewTab("speaking");
  }, [displayQuestionId]);

  const handleReviewQuestion = (q: ReviewVideoQuestionResponse) => {
    // Không cho mở xem lại khi đang ghi âm/đang chờ chấm câu THẬT — tránh học sinh tưởng đã dừng ghi âm.
    if (recorder.recording || writingSubmitting || speakingSubmitting) return;
    setReviewQuestionId(q.id);
    if (reviewOnlyMode) {
      // Phiên XEM LẠI THUẦN (mọi câu đã đạt) — video đã mở khoá hoàn toàn, tua tự do không ảnh hưởng gì.
      seekTo(q.timestampSeconds);
      return;
    }
    // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25 — GIỮA phiên làm bài thật còn dang dở:
    // tua video lùi về mốc câu bấm vào để "nghe lại", nhưng phải NHỚ LẠI vị trí thật trước khi tua đi
    // (chỉ nhớ lần ĐẦU bấm — nếu đang xem lại rồi mà bấm sang câu khác, giữ nguyên vị trí thật đã nhớ từ
    // lần đầu, không ghi đè bằng vị trí xem lại) để khôi phục đúng khi đóng (xem handleCloseReview).
    if (!isReviewingVideoRef.current) {
      realPositionBeforeReviewRef.current = getCurrentPlaybackTime();
    }
    setIsReviewingVideo(true);
    setUserPaused(false);
    seekTo(q.timestampSeconds);
    resumeVideo();
  };
  const handleCloseReview = () => {
    setReviewQuestionId(null);
    if (!isReviewingVideoRef.current) return;
    setIsReviewingVideo(false);
    const realPosition = realPositionBeforeReviewRef.current;
    realPositionBeforeReviewRef.current = null;
    if (realPosition != null) seekTo(realPosition);
    // Khôi phục đúng trạng thái chạy/dừng THẬT tại vị trí vừa quay lại: đang ở bước VIẾT (có câu THẬT
    // đang mở, chưa qua nghe lại) thì phải đứng yên; ngược lại (chạy tự do giữa 2 câu, hoặc đang ở bước
    // nghe lại chờ ghi âm) thì phát tiếp.
    if (activeQuestionIdRef.current != null && !awaitingNextMarkRef.current) {
      pauseVideo();
    } else {
      resumeVideo();
    }
  };

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
      // sinh tự bấm "Tiếp tục" khi sẵn sàng (xem handleContinueAfterWritingPass) — video chạy tiếp cho
      // nghe lại câu hỏi, còn GHI ÂM THẬT phải bấm riêng nút "Bắt đầu ghi âm" trong panel (handleStartRecording).
      if (stageForProgress(response) === "speaking") {
        setWritingPassedPopup({ scorePercent: response.writingScorePercent });
      }
    } catch (err) {
      setWritingError(friendlyApiErrorMessage(err, t("reflexVideoTask.submitError")));
    } finally {
      setWritingSubmitting(false);
    }
  };

  /**
   * V149 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23) — CHỦ Ý KHÔNG bắt đầu ghi âm
   * ngay ở đây nữa (khác lần sửa trước) — chỉ đóng popup. Ghi âm THẬT tách hẳn ra nút riêng trong panel
   * (xem handleStartRecording), bấm lúc nào cũng được.
   *
   * SPIKE (2026-08-25, xác nhận với người dùng — đảo ngược 1 phần V149) — TRƯỚC ĐÂY cho video chạy tiếp
   * ở đây để "nghe lại câu hỏi" trong lúc chuẩn bị nói. Nay video đứng yên xuyên suốt bước nói luôn (xem
   * activateQuestion) — KHÔNG resume ở đây nữa, chỉ đóng popup, video chỉ chạy tiếp sau khi đạt CẢ bước
   * nói (xem handleContinueAfterSpeakingPass).
   */
  const handleContinueAfterWritingPass = () => {
    setWritingPassedPopup(null);
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
    setUserPaused(false);
    // Khoá tạm nút play/pause tự dựng — xem ghi chú ở suppressManualToggleUntilRef/canToggleManualPause
    // (tránh cú chạm đóng popup này "chạm xuyên" luôn sang nút vừa lộ ra, tự bấm tạm dừng lại ngay).
    suppressManualToggleUntilRef.current = Date.now() + 600;
    // SPIKE (2026-08-25) — video đứng yên xuyên suốt cả bước viết lẫn nói (xem activateQuestion), nên
    // tới đây video vẫn đang đứng ĐÚNG tại mốc câu vừa đạt xong — chạy tiếp từ đây tới mốc câu KẾ TIẾP.
    // activeQuestionId=null nên handleTimeUpdate sẽ tự mở bảng VIẾT câu kế tiếp ngay khi chạm đúng mốc.
    resumeVideo();
  };

  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — TRƯỚC ĐÂY ghi âm dừng (hết giờ hoặc
  // học sinh bấm dừng) là tự động nộp NGAY, không có bước xem lại/xác nhận. Nay chỉ dừng ghi âm rồi hiện
  // lại bản ghi (nghe thử) + nút "Nộp bài" riêng — học sinh chủ động bấm mới thật sự gửi đi chấm (mirror
  // đúng luồng review-trước-khi-nộp của TakeExerciseModal cho câu SPEAKING dạng upload file).
  const audioPreviewUrl = useMemo(() => (recorder.audioBlob ? URL.createObjectURL(recorder.audioBlob) : null), [recorder.audioBlob]);
  useEffect(() => {
    return () => {
      if (audioPreviewUrl) URL.revokeObjectURL(audioPreviewUrl);
    };
  }, [audioPreviewUrl]);

  const handleConfirmSubmitSpeaking = () => {
    if (!recorder.audioBlob) return;
    const blob = recorder.audioBlob;
    recorder.reset();
    handleSubmitSpeaking(blob);
  };

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
  /**
   * SPIKE (2026-08-25, xác nhận với người dùng, test thật trên iPhone 8 Plus) — fix bug thật trên iOS
   * Safari/WebKit: `await getUserMedia()` TRƯỚC lệnh phát video làm mất "user activation" (cử chỉ
   * người dùng) của cú tap ban đầu ngay khi có 1 `await` xen giữa — nghiêm ngặt hơn hẳn Android/desktop.
   * `playVideo()` gọi qua postMessage tới iframe YouTube (khác origin) sau khi mất user activation bị
   * âm thầm bỏ qua — video đứng nguyên ở card "Xem trên YouTube", bấm vào cũng không phản hồi (do
   * `pointer-events-none` khoá click lên iframe khi đang làm bài thật, xem JSX).
   *
   * Sửa: gọi `playFromResumePoint()` NGAY, đồng bộ, TRƯỚC await getUserMedia() — giữ đúng trong cùng
   * tick với sự kiện click. KHÔNG dời `setStarted(true)` lên trước await (giữ nguyên vị trí SAU khi xin
   * mic xong như cũ) vì effect ép fullscreen trong useIntegrityMonitor phụ thuộc `started` — đổi thứ tự
   * đó sẽ tái phát đúng bug đã fix ngày 2026-08-11 (popup xin quyền mic hiện SAU khi đã vào fullscreen
   * làm trình duyệt tự thoát fullscreen, bị tính nhầm vi phạm) trên Android/desktop.
   */
  const handleStart = async () => {
    setMicError(null);
    playFromResumePoint();
    setRequestingMic(true);
    try {
      // Dùng CHUNG stream với useAudioRecorder (ensureStream) thay vì tự gọi getUserMedia() + dừng
      // ngay — xem ghi chú UX ở useAudioRecorder: giữ nguyên đúng 1 stream cho cả phiên, tránh xin
      // quyền mic thêm 1 lần nữa ở màn hình này rồi lại xin lại cho câu đầu tiên.
      await recorder.ensureStream();
    } catch {
      // Mic bị từ chối — rollback: dừng video vừa phát ở trên, không bật `started` (giữ nguyên hành vi
      // "chưa bắt đầu" như trước khi có patch này).
      pauseVideo();
      setMicError(t("reflexVideoTask.micPermissionError"));
      setRequestingMic(false);
      return;
    }
    setRequestingMic(false);
    setStarted(true);
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
      {/* Bổ sung 2026-09-04 (fix bug thật, đã xác nhận với người dùng) — canh giữa theo ĐÚNG khung nội
          dung (max-w-2xl lg:max-w-3xl mx-auto, khớp đúng cột bên dưới) thay vì canh giữa theo cả
          viewport trình duyệt — 2 khung khác chiều rộng nên trước đây nhìn lệch hẳn sang trái so với
          tiêu đề/khung video phía trên (mirror đúng pattern đã sửa ở TakeExerciseModal.tsx, bị bỏ sót
          chưa áp dụng cho màn Video phản xạ này). */}
      {justViolated && (
        <div className="fixed top-16 sm:top-20 inset-x-0 z-[110] px-4 sm:px-6 flex justify-center">
          <div className="max-w-2xl lg:max-w-3xl w-full flex justify-center">
            <div key={violationCount} role="alert" className="flex items-center gap-2 bg-rose-600 text-white pl-3 pr-4 py-2.5 rounded-2xl shadow-xl animate-alert-pop-centered max-w-full">
              <ShieldAlert size={18} className="shrink-0" />
              <span className="text-xs font-black">{t("monitoring.violationToast")}</span>
            </div>
          </div>
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

      {/*
       * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — fix bug thật: trước đây màn "Bắt
       * đầu làm bài" hiện NGAY khi mount (started mặc định false), rồi effect tải câu hỏi/tiến độ xong
       * mới kịp tự setStarted(true) cho trường hợp XEM LẠI bài đã hoàn thành — tạo hiệu ứng popup "Bắt
       * đầu làm bài" chớp nhoáng rồi biến mất, nhìn như bị lỗi/khựng. Trong lúc CÒN đang tải
       * (loadingQuestions), chưa biết chắc có cần màn bắt đầu hay không — hiện overlay tải trung tính
       * thay vì màn bắt đầu, tránh chớp nhoáng sai màn hình.
       */}
      {!started && loadingQuestions && (
        <div className="fixed inset-0 bg-ink/70 backdrop-blur-sm z-[120] flex items-center justify-center p-4">
          <Loader2 size={32} className="text-white animate-spin" />
        </div>
      )}

      {!started && !loadingQuestions && (
        <div className="fixed inset-0 bg-ink/70 backdrop-blur-sm z-[120] flex items-center justify-center p-4">
          <div className="bg-white rounded-[20px] max-w-sm w-full shadow-2xl p-6 space-y-4 text-center">
            <h3 className="text-lg font-extrabold text-ink">{video.title}</h3>
            <p className="text-xs font-bold text-muted">{t("reflexVideoTask.startScreen.description")}</p>
            {micError && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-xl text-left">{micError}</div>}
            <button
              onClick={handleStart}
              disabled={requestingMic}
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
            <button
              onClick={handleContinueAfterWritingPass}
              className="w-full flex items-center justify-center gap-1.5 px-4 py-2.5 bg-teal hover:bg-teal-deep text-white rounded-xl text-xs sm:text-sm font-extrabold"
            >
              {t("reflexVideoTask.writingStage.passedPopup.continueButton")}
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
            <span className="text-[13px] font-extrabold uppercase text-teal-deep tracking-wide">{t("reflexVideoTask.badge")}</span>
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
                src={buildLockedYouTubeEmbedSrc(youTubeVideoId, !reviewOnlyMode)}
                title={video.title}
                className={`w-full h-full ${reviewOnlyMode ? "" : "pointer-events-none"}`}
                allow="autoplay; encrypted-media"
              />
              {!reviewOnlyMode && <ManualPauseOverlay canToggle={canToggleManualPause} paused={userPaused} onToggle={handleToggleManualPause} labelPlay={t("reflexVideoTask.playButton")} labelPause={t("reflexVideoTask.pauseButton")} />}
            </>
          ) : video.sourceType === "R2_AUDIO" ? (
            <div className="w-full h-full flex items-center justify-center p-6">
              <audio
                key={video.id}
                ref={mediaRef as React.RefObject<HTMLAudioElement>}
                src={video.fileUrl}
                tabIndex={-1}
                controls={reviewOnlyMode}
                className={`w-full ${reviewOnlyMode ? "" : "pointer-events-none"}`}
              />
              {!reviewOnlyMode && (
                canToggleManualPause ? (
                  <ManualPauseOverlay canToggle paused={userPaused} onToggle={handleToggleManualPause} labelPlay={t("reflexVideoTask.playButton")} labelPause={t("reflexVideoTask.pauseButton")} />
                ) : (
                  <Lock size={28} className="text-white/60 absolute" />
                )
              )}
            </div>
          ) : (
            <>
              <video
                key={video.id}
                ref={mediaRef as React.RefObject<HTMLVideoElement>}
                src={video.fileUrl}
                tabIndex={-1}
                controls={reviewOnlyMode}
                className={`w-full h-full ${reviewOnlyMode ? "" : "pointer-events-none"}`}
                playsInline
              />
              {!reviewOnlyMode && <ManualPauseOverlay canToggle={canToggleManualPause} paused={userPaused} onToggle={handleToggleManualPause} labelPlay={t("reflexVideoTask.playButton")} labelPause={t("reflexVideoTask.pauseButton")} />}
            </>
          )}
        </div>

        {/*
         * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25 — hiện tổng thời lượng video +
         * các mốc câu hỏi (chấm tròn trên 1 thanh timeline theo đúng tỉ lệ thời gian) để học sinh biết
         * video dài bao lâu và còn bao nhiêu mốc phía trước. Mốc "đã mở" (đang làm dở hoặc đã đạt) bấm
         * được để tua lùi nghe lại (tái dùng đúng handleReviewQuestion/handleCloseReview đã có — KHÔNG
         * tự thêm cách tua mới, mốc "Chưa mở" không bấm được, giữ đúng nguyên tắc "không cho tua").
         */}
        {video.durationSeconds > 0 && (
          <div className="space-y-1.5">
            <div className="flex items-center justify-between text-[13px] font-bold text-muted">
              <span>{t("reflexVideoTask.durationLabel", { duration: formatTimestamp(video.durationSeconds) })}</span>
              {questions.length > 0 && <span>{t("reflexVideoTask.timelineLabel")}</span>}
            </div>
            {questions.length > 0 && (
              <div className="relative h-2 rounded-full bg-slate-100 mx-1.5">
                {questions.map((q, i) => {
                  const stage = stageForProgress(progress[q.id]);
                  const opened = q.id === activeQuestionId || progress[q.id] !== undefined;
                  const percent = Math.min(100, Math.max(0, (q.timestampSeconds / video.durationSeconds) * 100));
                  return (
                    <button
                      key={q.id}
                      type="button"
                      onClick={opened ? () => handleReviewQuestion(q) : undefined}
                      disabled={!opened || recorder.recording || writingSubmitting || speakingSubmitting}
                      title={`${t("reflexVideoTask.question.label", { index: i + 1 })} · ${formatTimestamp(q.timestampSeconds)}`}
                      style={{ left: `${percent}%` }}
                      className={`absolute top-1/2 -translate-y-1/2 -translate-x-1/2 w-3 h-3 rounded-full border-2 border-white shadow transition-transform ${
                        stage === "passed" ? "bg-emerald-500" : q.id === activeQuestionId ? "bg-teal" : "bg-slate-300"
                      } ${opened ? "cursor-pointer hover:scale-125" : "cursor-default"}`}
                    />
                  );
                })}
              </div>
            )}
          </div>
        )}

        {questionsError && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{questionsError}</div>}

        {displayQuestion && (
          <div className={`relative bg-white border-2 rounded-[16px] p-4 sm:p-5 space-y-3 shadow-lg ${isReviewing ? "border-line" : "border-teal"}`}>
            <div className="flex items-center justify-between gap-2 text-[10px] sm:text-[11px] font-extrabold text-teal-deep uppercase tracking-wide">
              <span className="text-sm flex items-center gap-1.5 flex-wrap">
                {t("reflexVideoTask.question.label", { index: questions.findIndex((q) => q.id === displayQuestion.id) + 1 })}
                <span className="px-1.5 py-0.5 rounded-md bg-sky-2 text-teal-deep normal-case font-bold">{formatTimestamp(displayQuestion.timestampSeconds)}</span>
                {/* Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25 — tua video lùi về đúng mốc câu ĐANG HIỂN THỊ (dù là câu THẬT đang làm dở hay câu đang xem lại) để nghe/xem lại, xem handleReviewQuestion. */}
                <button
                  type="button"
                  onClick={() => handleReviewQuestion(displayQuestion)}
                  disabled={recorder.recording || writingSubmitting || speakingSubmitting}
                  title={t("reflexVideoTask.replayButton")}
                  className="flex items-center justify-center w-5 h-5 rounded-full bg-sky-2 text-teal-deep hover:bg-teal hover:text-white disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                >
                  <RotateCcw size={11} />
                </button>
                {/*
                 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-18 — nút "Đóng xem lại" TRƯỚC
                 * ĐÂY nằm bên phải hàng này (cùng chỗ sticker điểm giờ đang ghim `absolute`) — bị đè lên
                 * nhau khi vừa xem lại 1 câu đã đạt vừa hiện sticker (2 điều kiện luôn đi cùng nhau, xem
                 * isReviewing). Dời sang cụm bên TRÁI (cạnh nút nghe lại) — không còn chung chỗ với góc
                 * sticker nữa dù cửa sổ rộng hay hẹp.
                 */}
                {isReviewing && (
                  <button onClick={handleCloseReview} className="flex items-center gap-1 text-muted normal-case hover:text-ink">
                    {t("reflexVideoTask.reviewBadge")} · {t("reflexVideoTask.closeReviewButton")}
                  </button>
                )}
              </span>
              {!isReviewing && displayStage === "writing" && <span className="text-sm text-muted normal-case">{t("reflexVideoTask.writingStage.title")}</span>}
            </div>

            {/*
             * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-18 — SPIKE lần 4: bản trước chỉ
             * ghim `absolute` từ `sm:` (640px) trở lên, dưới mốc đó rơi về hàng riêng full-width
             * (`justify-end`) — vẫn còn đúng lỗi khoảng trống ban đầu ở MỌI màn hẹp hơn 640px (điện thoại
             * lớn/tablet dọc, xem ảnh người dùng báo lại ở khổ ~495px). Từ khi sticker đã thu nhỏ hẳn cho
             * mobile (58px/thẻ, xem ScoreSticker), tổng bề ngang 2 sticker đủ nhỏ để KHÔNG cần chờ tới
             * `sm:` mới ghim được nữa — ghim `absolute` NGAY Ở MỌI KÍCH THƯỚC màn hình, chỉ đổi kích
             * thước sticker theo breakpoint (không đổi cách định vị nữa).
             *
             * SPIKE bổ sung (người dùng báo lại lần 2 — prompt câu hỏi bị đè, khác dòng label vì prompt
             * chạy FULL-WIDTH nên thực sự chạm tới vùng sticker trên màn hẹp) — `pr-24 sm:pr-40` cũ TÍNH
             * SAI, nhỏ hơn hẳn bề ngang thật của 2 sticker: mobile 2×58px+gap-2(8px)+lề phải right-3(12px)
             * = 136px (pr-24 chỉ 96px); desktop 2×114px+gap-5(20px)+lề phải right-4(16px) = 264px (sm:pr-40
             * chỉ 160px). Tính lại đúng + chừa dư ra 1 chút: `pr-40` (160px) và `sm:pr-72` (288px).
             */}
            {displayStage !== "writing" && (
              <div className="absolute top-3 right-3 sm:top-4 sm:right-4 flex gap-2 sm:gap-5 z-10">
                <ScoreSticker
                  icon={<PenLine size={10} />}
                  label={t("reflexVideoTask.writingStage.tabLabel")}
                  percent={displayProgress?.writingScorePercent ?? null}
                  tone="pass"
                  tiltClass="-rotate-6"
                />
                <ScoreSticker
                  icon={<Mic size={10} />}
                  label={t("reflexVideoTask.speakingStage.tabLabel")}
                  percent={displayProgress?.speakingScorePercent ?? null}
                  tone={displayProgress?.speakingScorePercent == null ? "pending" : displayProgress?.speakingPassed ? "pass" : "fail"}
                  tiltClass="rotate-6"
                />
              </div>
            )}

            {displayQuestion.prompt && (
              <p className={`text-sm sm:text-base lg:text-lg font-bold text-ink ${displayStage !== "writing" ? "pr-40 sm:pr-72" : ""}`}>
                {displayQuestion.prompt}
              </p>
            )}

            {/*
             * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-18 — đã qua bước Viết (đang ở
             * bước Nói, hoặc đang xem lại câu đã đạt cả 2 bước): thay vì hiện nối tiếp cả bài viết lẫn
             * phần ghi âm, tách thành 2 tab bấm chuyển qua lại (xem answerViewTab) — tab "Nói" mở mặc
             * định, không lộ bài viết; học sinh chủ động bấm tab "Viết" mới xem lại được.
             */}
            {displayStage !== "writing" && (
              <div className="flex items-center gap-1.5 border-b border-line">
                <button
                  type="button"
                  onClick={() => setAnswerViewTab("writing")}
                  className={`px-3 py-2 text-xs sm:text-sm font-extrabold border-b-2 -mb-px transition-colors ${
                    answerViewTab === "writing" ? "border-teal text-teal-deep" : "border-transparent text-muted hover:text-ink"
                  }`}
                >
                  {t("reflexVideoTask.writingStage.title")}
                </button>
                <button
                  type="button"
                  onClick={() => setAnswerViewTab("speaking")}
                  className={`px-3 py-2 text-xs sm:text-sm font-extrabold border-b-2 -mb-px transition-colors ${
                    answerViewTab === "speaking" ? "border-teal text-teal-deep" : "border-transparent text-muted hover:text-ink"
                  }`}
                >
                  {t("reflexVideoTask.speakingStage.title")}
                </button>
              </div>
            )}

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
                {/*
                 * V181 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-16) — bỏ feedback văn
                 * xuôi dài dòng, hiện lại CHÍNH câu trả lời của học sinh với phần lỗi bôi đỏ/gạch chân
                 * (writingMarkedAnswer) — đồng nhất cách hiển thị với bước Speaking.
                 *
                 * V184 (2026-09-16, phát hiện qua test thật trên staging, xác nhận với người dùng) — V181
                 * làm học sinh KHÔNG biết vì sao điểm thấp khi Cổng chặn (VD "Quá ngắn") kích hoạt mà
                 * không có lỗi ngữ pháp nào để bôi đỏ (vì thực sự không sai) — im lặng hoàn toàn, rất khó
                 * hiểu hướng sửa (UX tệ). writingFeedback giờ tái dùng để chứa gateNote (lý do cổng chặn,
                 * xem ReflexWritingGrammarAiGradingService) HOẶC thông báo AI chấm lỗi — hiện RIÊNG thành
                 * 1 ô cảnh báo có icon, tách khỏi đoạn markedAnswer, để học sinh thấy rõ NGAY hướng sửa
                 * thay vì phải tự suy đoán từ 1 câu không bị bôi đỏ gì.
                 */}
                {(displayProgress?.writingMarkedAnswer || displayProgress?.writingFeedback) && (
                  <div
                    className={`text-sm font-bold p-3 rounded-xl border ${
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
                    {displayProgress.writingMarkedAnswer && (
                      <p className="font-medium mt-1.5 normal-case whitespace-pre-line text-base leading-relaxed">
                        {renderHighlightedErrors(displayProgress.writingMarkedAnswer, t("reflexVideoTask.speakingStage.unclearWordTooltip"))}
                      </p>
                    )}
                    {displayProgress.writingFeedback && (
                      <div className="mt-1.5 flex items-start gap-1.5 rounded-lg bg-white/70 border border-current/20 p-2">
                        <AlertTriangle size={14} className="shrink-0 mt-0.5" />
                        <p className="font-medium normal-case whitespace-pre-line text-[13px] leading-relaxed">{displayProgress.writingFeedback}</p>
                      </div>
                    )}
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
                  <span className="text-[13px] font-bold text-muted">
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
            ) : answerViewTab === "writing" ? (
              // Tab "Viết" — xem lại đúng bài viết bước 1 (đọc-only) + điểm, chỉ hiện khi học sinh chủ
              // động bấm qua tab này (xem ghi chú answerViewTab ở trên).
              <div className="rounded-xl border border-line bg-sky-2/40 p-3 space-y-1">
                <p className="text-[13px] font-extrabold uppercase text-teal-deep tracking-wide">{t("reflexVideoTask.writingStage.yourAnswerLabel")}</p>
                <p className="text-sm font-medium text-ink whitespace-pre-line">{displayProgress?.answerText}</p>
                {displayProgress?.writingScorePercent != null && (
                  <p className="text-[13px] font-bold text-teal-deep">
                    {t("reflexVideoTask.writingStage.scoreLabel", { score: displayProgress.writingScorePercent })}
                  </p>
                )}
              </div>
            ) : null}

            {displayStage !== "writing" && answerViewTab === "speaking" && (
              <div className="space-y-2">
                {!isReviewing && <p className="text-xs font-bold text-muted">{t("reflexVideoTask.speakingStage.instructions")}</p>}
                {recorder.recording ? (
                  <div className="flex items-center gap-2 flex-wrap">
                    <div className="flex items-center gap-1.5 px-3.5 py-2 bg-coral text-white rounded-xl text-xs font-extrabold animate-pulse w-fit">
                      <Mic size={13} /> {t("reflexVideoTask.speakingStage.recording", { elapsed: recorder.elapsedSeconds, max: recorder.maxSeconds })}
                    </div>
                    {/* Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — trước đây phải chờ
                        chạy hết đủ maxRecordingSeconds mới tự dừng; giờ học sinh ghi xong sớm có thể chủ
                        động dừng ngay. Nút này CHỈ dừng ghi âm — KHÔNG tự nộp (xem khối preview bên dưới,
                        nút "Nộp bài" riêng mới thật sự gửi đi chấm). */}
                    <button
                      onClick={recorder.stop}
                      className="flex items-center gap-1.5 px-3.5 py-2 bg-white hover:bg-slate-100 border border-line rounded-xl text-xs font-extrabold text-ink w-fit"
                    >
                      <Square size={13} /> {t("reflexVideoTask.speakingStage.stopRecordingButton")}
                    </button>
                  </div>
                ) : speakingSubmitting ? (
                  <p className="text-xs font-bold text-teal-deep">{t("reflexVideoTask.speakingStage.grading")}</p>
                ) : recorder.audioBlob ? (
                  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — ghi âm đã dừng (tự hết
                  // giờ hoặc bấm dừng tay) nhưng CHƯA gửi chấm: cho nghe lại + chủ động bấm "Nộp bài" mới
                  // thật sự gửi, hoặc "Ghi âm lại" nếu chưa ưng ý (tái dùng handleRetrySpeaking — reset +
                  // start lại, cùng hành vi với nút "Ghi âm lại" sau khi chấm rớt).
                  <div className="space-y-2">
                    {audioPreviewUrl && (
                      /* eslint-disable-next-line jsx-a11y/media-has-caption */
                      <audio controls src={audioPreviewUrl} className="w-full" />
                    )}
                    {/* V184 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-16) — cảnh báo
                        SỚM ngay sau khi dừng ghi nếu âm lượng trung bình quá nhỏ, thay vì để học sinh
                        nộp bài rồi mới biết AI chấm sai vì không nghe rõ được giọng. Chỉ cảnh báo, KHÔNG
                        chặn nộp — môi trường mic/phòng ồn khác nhau, không nên chặn cứng theo 1 ngưỡng. */}
                    {recorder.quietWarning && (
                      <p className="text-xs font-bold text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
                        {t("reflexVideoTask.speakingStage.quietWarning")}
                      </p>
                    )}
                    <div className="flex items-center gap-2 flex-wrap">
                      <button
                        onClick={handleRetrySpeaking}
                        className="flex items-center gap-1.5 px-3.5 py-2 bg-white hover:bg-slate-100 border border-line rounded-xl text-xs font-extrabold text-ink w-fit"
                      >
                        <RotateCcw size={13} /> {t("reflexVideoTask.speakingStage.retryButton")}
                      </button>
                      <button
                        onClick={handleConfirmSubmitSpeaking}
                        className="flex items-center gap-1.5 px-3.5 py-2 bg-teal hover:bg-teal-deep text-white rounded-xl text-xs font-extrabold w-fit"
                      >
                        <CheckCircle2 size={13} /> {t("reflexVideoTask.speakingStage.submitButton")}
                      </button>
                    </div>
                  </div>
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
                {/*
                 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-18 — thiết kế lại toàn bộ
                 * khung kết quả chấm nói theo mẫu người dùng chọn: icon + tiêu đề tách riêng, khung
                 * trắng bo góc cho từng khối con (audio/tiêu chí/transcript) thay vì dồn hết vào 1 khối
                 * màu phẳng, tiêu chí có thanh tiến trình + phụ đề tiếng Việt, nhận xét AI tách thành ô
                 * "mẹo" riêng có icon bóng đèn, hàng dưới cùng hiện nút Ghi âm lại + ngưỡng đạt thật của
                 * hệ thống (70% — xem ReflexSequentialGradingService, KHÔNG phải 75% như 1 vài app luyện
                 * IELTS khác). CHƯA làm nút "Xem câu trả lời mẫu theo band điểm" có trong ảnh mẫu — tính
                 * năng này cần BE sinh/lưu câu trả lời mẫu theo thang điểm mới (chưa có sẵn, ngoài phạm
                 * vi 1 lần chỉnh UI) — xem trao đổi lại với người dùng nếu muốn làm tiếp.
                 */}
                {displayProgress?.speakingFeedback && !recorder.recording && !speakingSubmitting && (
                  <div
                    className={`rounded-2xl border p-4 space-y-3 ${
                      displayProgress.speakingPassed ? "bg-emerald-50/50 border-emerald-200" : "bg-amber-50/50 border-amber-200"
                    }`}
                  >
                    <div className="flex items-center gap-2">
                      {displayProgress.speakingPassed ? (
                        <CheckCircle2 size={20} className="text-emerald-600 shrink-0" />
                      ) : (
                        <AlertTriangle size={20} className="text-amber-600 shrink-0" />
                      )}
                      <p className={`text-sm font-extrabold normal-case ${displayProgress.speakingPassed ? "text-emerald-700" : "text-amber-700"}`}>
                        {displayProgress.speakingPassed
                          ? t("reflexVideoTask.speakingStage.passedFeedbackTitle")
                          : t("reflexVideoTask.speakingStage.failedFeedbackTitle")}
                        {displayProgress.speakingScorePercent != null &&
                          ` — ${t("reflexVideoTask.speakingStage.scoreLabel", { score: displayProgress.speakingScorePercent })}`}
                      </p>
                    </div>

                    {/* Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-08 — cho nghe lại chính
                        audio đã ghi âm kèm đọc nhận xét AI, dữ liệu audioUrl đã có sẵn từ trước nhưng
                        chưa được hiện ra (mirror pattern TakeExerciseModal.tsx cho câu SPEAKING). */}
                    {displayProgress.audioUrl && (
                      <div className="rounded-xl border border-line bg-white p-3 space-y-2">
                        <p className="text-[11px] font-extrabold uppercase tracking-wide text-muted normal-case">
                          {t("reflexVideoTask.speakingStage.listenBackLabel")}
                        </p>
                        {/* eslint-disable-next-line jsx-a11y/media-has-caption */}
                        <audio controls src={displayProgress.audioUrl} className="w-full" />
                      </div>
                    )}

                    {/* Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-16, đổi giao diện
                        2026-09-18 — % từng tiêu chí. SPIKE cùng ngày (phản hồi thật trên điện thoại) —
                        dạng lưới thẻ (border/subtitle/thanh tiến trình) cho 4 tiêu chí xếp DỌC hết cỡ
                        trên màn hẹp (grid-cols-1 do không đủ chỗ 2 cột), chiếm quá nhiều chiều cao khi
                        cuộn — quay lại dạng DANH SÁCH TEXT gọn cho mobile (mặc định, `sm:hidden`), vẫn
                        giữ dạng lưới thẻ trực quan cho tablet/desktop (`hidden sm:grid`, đủ rộng để chia
                        2 cột không bị dồn dọc). */}
                    {displayProgress.speakingCriteriaScores && displayProgress.speakingCriteriaScores.length > 0 && (
                      <div className="space-y-2">
                        <p className="text-[11px] font-extrabold uppercase tracking-wide text-muted normal-case">
                          {t("reflexVideoTask.speakingStage.criteriaScoresLabel")}
                        </p>
                        <ul className="sm:hidden divide-y divide-line rounded-xl border border-line bg-white">
                          {displayProgress.speakingCriteriaScores.map((item, idx) => (
                            <li key={idx} className="flex items-center justify-between gap-2 px-3 py-2 text-[13px] font-medium normal-case">
                              <span className="text-ink">
                                {item.criterion}
                                {criterionSubtitle(item.criterion) && (
                                  <span className="text-muted"> · {criterionSubtitle(item.criterion)}</span>
                                )}
                              </span>
                              <span className="font-extrabold text-ink shrink-0">{item.percent}%</span>
                            </li>
                          ))}
                        </ul>
                        <div className="hidden sm:grid grid-cols-2 gap-2">
                          {displayProgress.speakingCriteriaScores.map((item, idx) => (
                            <div key={idx} className="rounded-xl border border-line bg-white p-2.5 space-y-1.5">
                              <div>
                                <p className="text-[13px] font-extrabold text-ink normal-case">{item.criterion}</p>
                                {criterionSubtitle(item.criterion) && (
                                  <p className="text-[11px] font-medium text-muted normal-case">{criterionSubtitle(item.criterion)}</p>
                                )}
                              </div>
                              <div className="flex items-center gap-2">
                                <div className="flex-1 h-1.5 rounded-full bg-slate-100 overflow-hidden">
                                  <div
                                    className={`h-full rounded-full ${item.percent >= 70 ? "bg-emerald-500" : "bg-gradient-to-r from-gold to-coral"}`}
                                    style={{ width: `${Math.min(100, Math.max(0, item.percent))}%` }}
                                  />
                                </div>
                                <span className="text-[12px] font-extrabold text-ink shrink-0">{item.percent}%</span>
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-16, đổi giao diện
                        2026-09-18 — transcript bọc trong khung trắng riêng, lỗi hiện dạng chip viền đỏ
                        (xem renderHighlightedErrors) thay vì chỉ gạch chân. */}
                    {displayProgress.speakingTranscript && (
                      <div className="space-y-1.5">
                        <p className="text-[11px] font-extrabold uppercase tracking-wide text-muted normal-case">
                          {t("reflexVideoTask.speakingStage.transcriptLabel")}
                        </p>
                        <div className="rounded-xl border border-line bg-white p-3">
                          <p className="normal-case whitespace-pre-line text-[13px] leading-relaxed text-ink">
                            {renderHighlightedErrors(displayProgress.speakingTranscript, t("reflexVideoTask.speakingStage.unclearWordTooltip"))}
                          </p>
                        </div>
                      </div>
                    )}

                    {/* Nhận xét AI dạng văn xuôi — tách thành ô "mẹo" riêng (icon bóng đèn) thay vì nằm
                        lẫn trong khối màu phẳng như bản cũ, dễ phân biệt với các khối dữ liệu ở trên. */}
                    <div className="flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-100/60 p-3">
                      <Lightbulb size={16} className="text-amber-600 shrink-0 mt-0.5" />
                      <p className="font-medium normal-case whitespace-pre-line text-[13px] leading-relaxed text-amber-800">
                        {displayProgress.speakingFeedback}
                      </p>
                    </div>

                    <div className="flex flex-wrap items-center justify-between gap-2 pt-1 border-t border-line/60">
                      {!displayProgress.speakingPassed && !isReviewing ? (
                        <button
                          onClick={handleRetrySpeaking}
                          className="flex items-center gap-1.5 px-3.5 py-2 bg-coral hover:bg-coral/90 text-white rounded-xl text-xs font-extrabold"
                        >
                          <RotateCcw size={13} /> {t("reflexVideoTask.speakingStage.retryButton")}
                        </button>
                      ) : (
                        <span />
                      )}
                      <span className="text-[12px] font-bold text-muted normal-case">
                        {t("reflexVideoTask.speakingStage.passThresholdNote", { threshold: 70 })}
                      </span>
                    </div>
                  </div>
                )}
                {!isReviewing && (
                  <p className="text-[13px] font-bold text-muted">
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
              //
              // Bug thật phát hiện qua test (2026-08-23): đang xem lại 1 câu đã đạt (Q3) thì câu THẬT đang
              // chờ làm (VD Q4, activeQuestionId) vẫn hiện trong danh sách này (vì isDisplayed so với câu
              // ĐANG XEM, không phải activeQuestionId) — nhưng hiện y hệt 1 câu "Chưa mở" bình thường, học
              // sinh tưởng bị khoá, bấm không được. Nhận diện riêng case này: hiện nổi bật + bấm được để
              // đóng xem lại và quay về đúng câu đang chờ.
              const isRealActiveHiddenByReview = q.id === activeQuestionId && !isDisplayed;
              const clickable = (stage === "passed" && !isDisplayed) || isRealActiveHiddenByReview;
              return (
                <div
                  key={q.id}
                  onClick={clickable ? (isRealActiveHiddenByReview ? handleCloseReview : () => handleReviewQuestion(q)) : undefined}
                  className={`flex items-center justify-between gap-2 rounded-xl px-3.5 py-2.5 border text-xs sm:text-sm font-bold transition-opacity ${
                    isDisplayed
                      ? "opacity-0 h-0 p-0 border-0 overflow-hidden"
                      : isRealActiveHiddenByReview
                        ? "bg-teal/10 border-teal/40 text-teal-deep cursor-pointer hover:bg-teal/20"
                        : stage === "passed"
                          ? "bg-emerald-50 border-emerald-100 text-emerald-700 cursor-pointer hover:bg-emerald-100"
                          : "bg-sky-2 border-teal/10 text-muted opacity-60"
                  }`}
                >
                  <span className="flex items-center gap-1.5">
                    {t("reflexVideoTask.question.label", { index: i + 1 })}
                    <span className="px-1.5 py-0.5 rounded-md bg-white/70 normal-case font-bold">{formatTimestamp(q.timestampSeconds)}</span>
                  </span>
                  {isRealActiveHiddenByReview ? (
                    <span>{t("reflexVideoTask.question.waitingForYou")}</span>
                  ) : stage === "passed" ? (
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
