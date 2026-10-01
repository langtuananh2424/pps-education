import React, { forwardRef, useEffect, useImperativeHandle, useRef, useState } from "react";
import { Mic, Paperclip, Send, Square, Trash2 } from "lucide-react";
import { useTranslation } from "react-i18next";

/** Mỗi lần gửi audio tối đa 5 phút (UC-74 A3, dùng chung cho mọi trợ lý AI — đã xác nhận với người dùng 2026-09-28). */
export const MAX_AUDIO_SECONDS = 300;

export interface AiComposerAudio {
  blob: Blob;
  url: string;
  seconds: number | null;
}

export interface AiChatComposerHandle {
  startRecording: () => void;
  openFilePicker: () => void;
  /** Điền sẵn nội dung ô nhập (VD chip gợi ý câu lệnh) để người dùng sửa rồi mới gửi. */
  setText: (text: string) => void;
}

interface Props {
  disabled?: boolean;
  busy?: boolean;
  placeholder: string;
  /** Gửi audio (nếu có) + chữ; trả `false` để giữ nguyên nội dung ô nhập (VD chưa đủ điều kiện gửi). */
  onSend: (audio: AiComposerAudio | null, text: string) => boolean | void;
}

export function formatSeconds(total: number): string {
  const m = Math.floor(total / 60);
  const s = Math.floor(total % 60);
  return `${m}:${String(s).padStart(2, "0")}`;
}

function readAudioDuration(url: string): Promise<number | null> {
  return new Promise((resolve) => {
    const audio = new Audio();
    audio.preload = "metadata";
    audio.onloadedmetadata = () => resolve(Number.isFinite(audio.duration) ? audio.duration : null);
    audio.onerror = () => resolve(null);
    audio.src = url;
  });
}

/**
 * Ô soạn tin của sidebar trợ lý AI (dùng chung: trợ lý soạn nháp nhận xét UC-74 của Giáo viên, trợ lý duyệt
 * UC-75 của Quản lý điểm trường) — ghi âm (tự dừng ở 5:00), tải file audio (chặn file dài hơn 5 phút), gõ chữ,
 * Enter để gửi. Màn hình cha quyết định gửi đi đâu qua `onSend`; chip gợi ý gọi được ghi âm/chọn file qua ref.
 */
const AiChatComposer = forwardRef<AiChatComposerHandle, Props>(function AiChatComposer({ disabled = false, busy = false, placeholder, onSend }, ref) {
  const { t } = useTranslation("academic-comments");
  const [input, setInput] = useState("");
  const [pendingAudio, setPendingAudio] = useState<AiComposerAudio | null>(null);
  const [recording, setRecording] = useState(false);
  const [recordSeconds, setRecordSeconds] = useState(0);
  const [localError, setLocalError] = useState<string | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const timerRef = useRef<number | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const stopRecording = (discard = false) => {
    if (timerRef.current) {
      window.clearInterval(timerRef.current);
      timerRef.current = null;
    }
    const recorder = recorderRef.current;
    if (recorder && recorder.state !== "inactive") {
      if (discard) recorder.onstop = null;
      recorder.stop();
      recorder.stream.getTracks().forEach((track) => track.stop());
    }
    recorderRef.current = null;
    setRecording(false);
  };

  useEffect(() => () => stopRecording(true), []);

  const startRecording = async () => {
    if (recording || disabled) return;
    setLocalError(null);
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === "undefined") {
      setLocalError(t("dailyCommentPanel.aiAssistant.errors.recordingUnsupported"));
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mimeType = MediaRecorder.isTypeSupported("audio/webm;codecs=opus") ? "audio/webm;codecs=opus" : undefined;
      const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);
      chunksRef.current = [];
      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data);
      };
      const startedAt = Date.now();
      recorder.onstop = () => {
        const blob = new Blob(chunksRef.current, { type: (recorder.mimeType || "audio/webm").split(";")[0] });
        setPendingAudio({ blob, url: URL.createObjectURL(blob), seconds: Math.min(MAX_AUDIO_SECONDS, (Date.now() - startedAt) / 1000) });
      };
      recorder.start(1000);
      recorderRef.current = recorder;
      setRecordSeconds(0);
      setRecording(true);
      timerRef.current = window.setInterval(() => {
        const elapsed = Math.floor((Date.now() - startedAt) / 1000);
        setRecordSeconds(elapsed);
        if (elapsed >= MAX_AUDIO_SECONDS) stopRecording();
      }, 250);
    } catch {
      setLocalError(t("dailyCommentPanel.aiAssistant.errors.microphoneDenied"));
    }
  };

  const handlePickFile = async (file: File | null) => {
    if (fileInputRef.current) fileInputRef.current.value = "";
    if (!file) return;
    setLocalError(null);
    if (!file.type.startsWith("audio/")) {
      setLocalError(t("dailyCommentPanel.aiAssistant.errors.notAudio"));
      return;
    }
    const url = URL.createObjectURL(file);
    const seconds = await readAudioDuration(url);
    if (seconds !== null && seconds > MAX_AUDIO_SECONDS + 1) {
      URL.revokeObjectURL(url);
      setLocalError(t("dailyCommentPanel.aiAssistant.errors.tooLong"));
      return;
    }
    setPendingAudio({ blob: file, url, seconds });
  };

  useImperativeHandle(ref, () => ({
    startRecording: () => void startRecording(),
    openFilePicker: () => fileInputRef.current?.click(),
    setText: (text: string) => setInput(text)
  }));

  const handleSend = () => {
    if (disabled || busy || recording) return;
    const text = input.trim();
    if (!pendingAudio && !text) return;
    if (onSend(pendingAudio, text) === false) return;
    setPendingAudio(null);
    setInput("");
  };

  return (
    <div className="border-t border-slate-100 p-3 space-y-2">
      {localError && <p className="text-[13px] text-rose-600">{localError}</p>}
      {recording && (
        <div className="flex items-center justify-between gap-2 bg-rose-50 border border-rose-100 rounded-lg px-3 py-2">
          <span className="flex items-center gap-2 text-sm font-semibold text-rose-600">
            <span className="w-2 h-2 rounded-full bg-rose-500 animate-pulse" />
            {t("dailyCommentPanel.aiAssistant.recording", { elapsed: formatSeconds(recordSeconds), max: formatSeconds(MAX_AUDIO_SECONDS) })}
          </span>
          <button type="button" onClick={() => stopRecording()} className="flex items-center gap-1 text-[13px] font-bold text-rose-700 hover:underline">
            <Square className="w-3 h-3" />
            {t("dailyCommentPanel.aiAssistant.stopRecording")}
          </button>
        </div>
      )}
      {pendingAudio && (
        <div className="flex items-center gap-2 bg-slate-50 border border-slate-200 rounded-lg px-2 py-1.5">
          <audio controls src={pendingAudio.url} className="flex-1 h-8" />
          <button type="button" onClick={() => setPendingAudio(null)} className="p-1 text-slate-400 hover:text-rose-600" aria-label={t("dailyCommentPanel.aiAssistant.removeAudio")}>
            <Trash2 className="w-4 h-4" />
          </button>
        </div>
      )}
      <div className="flex items-end gap-2">
        <button
          type="button"
          onClick={recording ? () => stopRecording() : () => void startRecording()}
          disabled={disabled || busy || !!pendingAudio}
          className={`p-2 rounded-lg border ${recording ? "border-rose-300 bg-rose-50 text-rose-600" : "border-slate-200 text-slate-600 hover:bg-slate-50"} disabled:opacity-40`}
          title={t("dailyCommentPanel.aiAssistant.suggestions.record")}
        >
          {recording ? <Square className="w-4 h-4" /> : <Mic className="w-4 h-4" />}
        </button>
        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          disabled={disabled || busy || recording || !!pendingAudio}
          className="p-2 rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:opacity-40"
          title={t("dailyCommentPanel.aiAssistant.suggestions.upload")}
        >
          <Paperclip className="w-4 h-4" />
        </button>
        <input ref={fileInputRef} type="file" accept="audio/*" className="hidden" onChange={(e) => void handlePickFile(e.target.files?.[0] ?? null)} />
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              handleSend();
            }
          }}
          disabled={disabled}
          rows={2}
          placeholder={placeholder}
          className="flex-1 bg-slate-50 border border-slate-200 text-sm p-2 rounded-lg focus:outline-none resize-none"
        />
        <button
          type="button"
          onClick={handleSend}
          disabled={disabled || busy || recording || (!pendingAudio && !input.trim())}
          className="p-2 rounded-lg bg-brand-orange text-white hover:bg-brand-orange/90 disabled:opacity-40"
          title={t("dailyCommentPanel.aiAssistant.send")}
        >
          <Send className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
});

export default AiChatComposer;
