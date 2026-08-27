import { useRef, useState } from "react";
import { Mic, Square, Play, Send, Loader2, AlertTriangle } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { gradeSpeakingTest, SpeakingAiGradingTestProvider, SpeakingAiGradingTestResponse } from "../api";

/**
 * SPIKE/TEST riêng (2026-08-22, đã xác nhận với người dùng) — trang test độc lập, KHÔNG thuộc luồng
 * Video phản xạ thật (UC-23b) — chỉ để thử pipeline "ghi voice -> Speech-to-Text -> Claude chấm nội
 * dung/ngữ pháp" trước khi quyết định có thay hẳn luồng chấm thủ công hiện có hay không. Rubric chấm
 * ở BE hiện là PLACEHOLDER (xem SpeakingAiGradingTestService.PLACEHOLDER_RUBRIC) — kết quả ở đây CHỈ
 * để kiểm tra kỹ thuật, không phản ánh đúng thang điểm thật sẽ dùng sau này.
 *
 * Không thêm vào menu điều hướng (AppShell) — cố tình "ẩn", chỉ truy cập qua URL trực tiếp
 * (/dev-tools/speaking-grading-test) theo đúng tinh thần "trang test riêng" người dùng yêu cầu.
 */
export default function SpeakingGradingTestPage() {
  const [recording, setRecording] = useState(false);
  const [audioBlob, setAudioBlob] = useState<Blob | null>(null);
  const [audioPreviewUrl, setAudioPreviewUrl] = useState<string | null>(null);
  const [writingText, setWritingText] = useState("");
  const [provider, setProvider] = useState<SpeakingAiGradingTestProvider>("GEMINI");
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<SpeakingAiGradingTestResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);

  const handleStartRecording = async () => {
    setError(null);
    setResult(null);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const recorder = new MediaRecorder(stream);
      chunksRef.current = [];
      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data);
      };
      recorder.onstop = () => {
        const blob = new Blob(chunksRef.current, { type: "audio/webm" });
        setAudioBlob(blob);
        setAudioPreviewUrl(URL.createObjectURL(blob));
        stream.getTracks().forEach((t) => t.stop());
      };
      recorder.start();
      mediaRecorderRef.current = recorder;
      setRecording(true);
    } catch {
      setError("Không truy cập được microphone — kiểm tra quyền truy cập trình duyệt.");
    }
  };

  const handleStopRecording = () => {
    mediaRecorderRef.current?.stop();
    setRecording(false);
  };

  const handleSubmit = async () => {
    if (!audioBlob) return;
    setSubmitting(true);
    setError(null);
    setResult(null);
    try {
      const res = await gradeSpeakingTest(audioBlob, writingText, provider);
      setResult(res);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Có lỗi xảy ra, thử lại sau.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="max-w-3xl mx-auto p-6 space-y-5">
      <div>
        <h1 className="text-lg font-bold font-display text-slate-900">
          [TEST] AI chấm Speaking — Video phản xạ V2
        </h1>
        <p className="text-xs text-slate-500 mt-1">
          Trang test riêng, chưa gắn vào luồng thật. Rubric chấm hiện là <b>placeholder</b> — chỉ dùng để kiểm
          tra kỹ thuật (ghi voice → Speech-to-Text → Claude chấm nội dung/ngữ pháp), không phản ánh thang điểm
          chính thức.
        </p>
      </div>

      <div className="bg-white border border-slate-200 rounded-xl p-5 space-y-4">
        <div>
          <label className="text-xs font-bold uppercase text-slate-500 block mb-1.5">
            1. Bài viết trước khi ghi âm (tùy chọn — mô phỏng bước "viết trước" mới)
          </label>
          <textarea
            value={writingText}
            onChange={(e) => setWritingText(e.target.value)}
            rows={3}
            placeholder="VD: In the video, the woman is asking for directions to the train station..."
            className="w-full bg-slate-50 border border-slate-200 text-sm p-2.5 rounded-lg focus:outline-none"
          />
        </div>

        <div>
          <label className="text-xs font-bold uppercase text-slate-500 block mb-1.5">2. Nhà cung cấp AI</label>
          <select
            value={provider}
            onChange={(e) => setProvider(e.target.value as SpeakingAiGradingTestProvider)}
            className="w-full sm:w-auto bg-slate-50 border border-slate-200 text-sm p-2.5 rounded-lg focus:outline-none"
          >
            <option value="AUTO">Tự động (ưu tiên OpenAI + Claude nếu đủ key)</option>
            <option value="GEMINI">Gemini (free tier — STT + chấm bài đều qua Gemini)</option>
            <option value="OPENAI_CLAUDE">OpenAI Whisper + Claude (cần billing cả 2 bên)</option>
          </select>
        </div>

        <div>
          <label className="text-xs font-bold uppercase text-slate-500 block mb-1.5">3. Ghi âm câu trả lời</label>
          <div className="flex items-center gap-2">
            {!recording ? (
              <button
                type="button"
                onClick={handleStartRecording}
                className="flex items-center gap-1.5 bg-rose-600 hover:bg-rose-700 text-white text-xs font-bold px-3 py-2 rounded-lg"
              >
                <Mic className="w-4 h-4" /> Bắt đầu ghi âm
              </button>
            ) : (
              <button
                type="button"
                onClick={handleStopRecording}
                className="flex items-center gap-1.5 bg-slate-800 hover:bg-slate-900 text-white text-xs font-bold px-3 py-2 rounded-lg animate-pulse"
              >
                <Square className="w-4 h-4" /> Dừng ghi âm
              </button>
            )}
            {audioPreviewUrl && (
              <audio controls src={audioPreviewUrl} className="h-9">
                <Play className="w-4 h-4" />
              </audio>
            )}
          </div>
        </div>

        <button
          type="button"
          onClick={handleSubmit}
          disabled={!audioBlob || submitting}
          className="flex items-center gap-1.5 bg-brand-orange hover:bg-brand-orange/90 text-white text-xs font-bold px-4 py-2.5 rounded-lg disabled:opacity-40"
        >
          {submitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
          {submitting ? "Đang xử lý (STT + AI chấm)..." : "Gửi để AI chấm"}
        </button>
      </div>

      {error && (
        <div className="flex items-start gap-2 bg-rose-50 border border-rose-100 text-rose-700 text-xs p-3 rounded-lg">
          <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
          <span>{error}</span>
        </div>
      )}

      {result && (
        <div className="bg-white border border-slate-200 rounded-xl p-5 space-y-4">
          <h2 className="text-sm font-bold text-slate-900">Kết quả</h2>

          <div>
            <span className="text-[10px] font-bold uppercase text-slate-400 block mb-1">
              Transcript (Speech-to-Text)
            </span>
            <p className="text-sm text-slate-700 bg-slate-50 border border-slate-100 rounded-lg p-2.5 whitespace-pre-wrap">
              {result.transcript || "(rỗng — không nhận dạng được giọng nói)"}
            </p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <span className="text-[10px] font-bold uppercase text-slate-400 block mb-1">Điểm nội dung (AI)</span>
              <p className="text-2xl font-bold text-brand-orange">
                {result.contentScorePercent ?? "—"}
                {result.contentScorePercent != null && "%"}
              </p>
              <p className="text-xs text-slate-600 mt-1">{result.contentFeedback}</p>
            </div>
            <div>
              <span className="text-[10px] font-bold uppercase text-slate-400 block mb-1">
                Điểm ngữ pháp bài viết (AI)
              </span>
              <p className="text-2xl font-bold text-brand-orange">
                {result.grammarScorePercent ?? "—"}
                {result.grammarScorePercent != null && "%"}
              </p>
              <p className="text-xs text-slate-600 mt-1">{result.grammarFeedback ?? "(chưa nhập bài viết trước)"}</p>
            </div>
          </div>

          <div>
            <span className="text-[10px] font-bold uppercase text-slate-400 block mb-1">Audio đã lưu (R2)</span>
            <a href={result.audioUrl} target="_blank" rel="noreferrer" className="text-xs text-blue-600 underline break-all">
              {result.audioUrl}
            </a>
          </div>
        </div>
      )}
    </div>
  );
}
