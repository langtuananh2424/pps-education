import React, { useEffect, useRef, useState } from "react";
import { AlertTriangle, Bot, ChevronDown, ChevronUp, Loader2, Mic, Paperclip, RefreshCw, Save, Send, Square, Table2, Trash2, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import {
  CommentAiDraftJob,
  CommentAiDraftResult,
  CommentAiDraftRow,
  reviseCommentAiDraft,
  startCommentAiDraft,
  waitForCommentAiDraftJob
} from "../api";

/** UC-74 A3 — mỗi lần gửi tối đa 5 phút (đã xác nhận với người dùng 2026-09-28). */
const MAX_AUDIO_SECONDS = 300;

type ChatMessageInput =
  | { role: "teacher"; text: string; audioUrl?: string }
  | { role: "assistant"; text: string; draft?: CommentAiDraftResult; error?: boolean };
type ChatMessage = ChatMessageInput & { id: number };

interface PendingAudio {
  blob: Blob;
  url: string;
  seconds: number | null;
}

interface Props {
  open: boolean;
  onClose: () => void;
  classSessionId: number | null;
  sessionLabel: string;
  /** Buổi GVNN — tạm thời không hỗ trợ (UC-74 A1). */
  isForeignSession: boolean;
  /**
   * Điền Thái độ + Nhận xét của bản nháp vào form (chỉ các dòng chưa khoá) — trả về số dòng đã điền.
   * Trợ lý không có đường nào khác để ghi dữ liệu.
   */
  onApply: (draft: CommentAiDraftResult) => number;
  /** Điền vào form rồi gọi đúng "Lưu nháp" của UC-21 — quyền lưu cao nhất của trợ lý là DRAFT. */
  onApplyAndSaveDraft: (draft: CommentAiDraftResult) => Promise<string>;
  savingDraft: boolean;
}

function formatSeconds(total: number): string {
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
 * UC-74: Trợ lý AI soạn nháp nhận xét (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-28) —
 * sidebar trò chuyện: giáo viên ghi âm/tải audio nhận xét (≤ 5 phút) hoặc gõ yêu cầu, trợ lý trả bản
 * nháp Thái độ + Nhận xét kèm các nút gợi ý thao tác (Áp dụng vào bảng / Lưu nháp / Viết lại) để không
 * phải ra lệnh bằng chữ. Không có nút Gửi duyệt ở đây — Gửi duyệt vẫn là bước giáo viên tự làm ở bảng.
 */
export default function CommentAiAssistantSidebar({
  open,
  onClose,
  classSessionId,
  sessionLabel,
  isForeignSession,
  onApply,
  onApplyAndSaveDraft,
  savingDraft
}: Props) {
  const { t } = useTranslation("academic-comments");
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState<CommentAiDraftResult | null>(null);
  const [input, setInput] = useState("");
  const [pendingAudio, setPendingAudio] = useState<PendingAudio | null>(null);
  const [recording, setRecording] = useState(false);
  const [recordSeconds, setRecordSeconds] = useState(0);
  const [busy, setBusy] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);
  const [showTranscript, setShowTranscript] = useState(false);
  const nextId = useRef(1);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const timerRef = useRef<number | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  const push = (message: ChatMessageInput) => setMessages((prev) => [...prev, { ...message, id: nextId.current++ }]);

  // Đổi buổi học = cuộc trò chuyện mới (bản nháp gắn với danh sách học sinh của đúng buổi đó).
  useEffect(() => {
    abortRef.current?.abort();
    stopRecording(true);
    setMessages([]);
    setDraft(null);
    setPendingAudio(null);
    setInput("");
    setBusy(false);
    setLocalError(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [classSessionId]);

  useEffect(() => () => abortRef.current?.abort(), []);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, busy]);

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

  const startRecording = async () => {
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

  const runJob = async (start: () => Promise<CommentAiDraftJob>) => {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setBusy(true);
    try {
      const job = await waitForCommentAiDraftJob(await start(), controller.signal);
      if (controller.signal.aborted) return;
      if (job.status === "DONE" && job.result) {
        setDraft(job.result);
        push({ role: "assistant", text: job.result.assistantMessage, draft: job.result });
      } else if (job.status === "FAILED") {
        push({ role: "assistant", text: job.errorMessage ?? t("dailyCommentPanel.aiAssistant.errors.generic"), error: true });
      } else {
        push({ role: "assistant", text: t("dailyCommentPanel.aiAssistant.errors.timeout"), error: true });
      }
    } catch (err) {
      if (controller.signal.aborted) return;
      push({ role: "assistant", text: err instanceof ApiError ? err.message : t("dailyCommentPanel.aiAssistant.errors.generic"), error: true });
    } finally {
      if (abortRef.current === controller) setBusy(false);
    }
  };

  const historyForRequest = () =>
    messages
      .filter((m) => !(m.role === "assistant" && m.error))
      .slice(-6)
      .map((m) => ({ role: m.role, text: m.text }));

  const currentRows = (d: CommentAiDraftResult) =>
    d.rows.map((r) => ({ studentId: r.studentId, attitude: r.attitude, content: r.content }));

  const handleSend = () => {
    if (!classSessionId || busy || recording) return;
    const text = input.trim();
    if (pendingAudio) {
      const audio = pendingAudio;
      push({ role: "teacher", text: text || t("dailyCommentPanel.aiAssistant.audioMessage", { duration: audio.seconds ? formatSeconds(audio.seconds) : "?" }), audioUrl: audio.url });
      setPendingAudio(null);
      setInput("");
      void runJob(() => startCommentAiDraft(classSessionId, audio.blob, text));
      return;
    }
    if (!text) return;
    push({ role: "teacher", text });
    setInput("");
    const d = draft;
    if (!d) {
      void runJob(() => startCommentAiDraft(classSessionId, null, text));
    } else {
      void runJob(() =>
        reviseCommentAiDraft(classSessionId, {
          mode: "INSTRUCTION",
          instruction: text,
          transcript: d.transcript,
          extraction: d.extraction,
          currentRows: currentRows(d),
          history: historyForRequest()
        })
      );
    }
  };

  const handleRewriteAll = () => {
    if (!classSessionId || !draft || busy) return;
    const d = draft;
    push({ role: "teacher", text: t("dailyCommentPanel.aiAssistant.actions.rewrite") });
    void runJob(() =>
      reviseCommentAiDraft(classSessionId, { mode: "REWRITE_ALL", transcript: d.transcript, extraction: d.extraction, currentRows: currentRows(d) })
    );
  };

  const handleQuickInstruction = (text: string) => {
    setInput(text);
  };

  const handleApply = () => {
    if (!draft) return;
    const count = onApply(draft);
    push({ role: "assistant", text: t("dailyCommentPanel.aiAssistant.appliedMessage", { count }) });
  };

  const handleSaveDraft = async () => {
    if (!draft) return;
    const message = await onApplyAndSaveDraft(draft);
    push({ role: "assistant", text: message });
  };

  if (!open) return null;

  const latestDraftMessageId = [...messages].reverse().find((m) => m.role === "assistant" && m.draft)?.id;

  return (
    <div className="fixed inset-y-0 right-0 z-40 w-full sm:w-[420px] bg-white border-l border-slate-200 shadow-2xl flex flex-col">
      <div className="px-4 py-3 border-b border-slate-100 flex items-center justify-between gap-2 bg-slate-50">
        <div className="min-w-0">
          <div className="flex items-center gap-1.5 text-sm font-bold text-slate-800">
            <Bot className="w-4 h-4 text-violet-600" />
            {t("dailyCommentPanel.aiAssistant.title")}
          </div>
          <p className="text-[10px] text-slate-400 truncate">{sessionLabel || t("dailyCommentPanel.aiAssistant.noSession")}</p>
        </div>
        <button type="button" onClick={onClose} className="p-1.5 rounded-lg hover:bg-slate-200 text-slate-500" aria-label={t("dailyCommentPanel.aiAssistant.close")}>
          <X className="w-4 h-4" />
        </button>
      </div>

      <div ref={scrollRef} className="flex-1 overflow-y-auto px-4 py-3 space-y-3">
        <div className="text-xs text-slate-600 bg-violet-50 border border-violet-100 rounded-lg p-3 space-y-1.5">
          <p>{t("dailyCommentPanel.aiAssistant.intro")}</p>
          <p className="text-[10px] text-violet-700">{t("dailyCommentPanel.aiAssistant.permissionNote")}</p>
        </div>

        {isForeignSession ? (
          <p className="text-xs text-amber-700 bg-amber-50 border border-amber-100 rounded-lg p-3">{t("dailyCommentPanel.aiAssistant.foreignSession")}</p>
        ) : (
          messages.length === 0 &&
          classSessionId && (
            <div className="flex flex-wrap gap-2">
              <button type="button" onClick={startRecording} disabled={recording} className="flex items-center gap-1.5 text-[11px] font-semibold border border-violet-200 text-violet-700 bg-white hover:bg-violet-50 rounded-full px-3 py-1.5">
                <Mic className="w-3.5 h-3.5" />
                {t("dailyCommentPanel.aiAssistant.suggestions.record")}
              </button>
              <button type="button" onClick={() => fileInputRef.current?.click()} className="flex items-center gap-1.5 text-[11px] font-semibold border border-violet-200 text-violet-700 bg-white hover:bg-violet-50 rounded-full px-3 py-1.5">
                <Paperclip className="w-3.5 h-3.5" />
                {t("dailyCommentPanel.aiAssistant.suggestions.upload")}
              </button>
            </div>
          )
        )}

        {messages.map((m) =>
          m.role === "teacher" ? (
            <div key={m.id} className="flex justify-end">
              <div className="max-w-[85%] bg-brand-orange/10 border border-brand-orange/20 text-xs text-slate-800 rounded-2xl rounded-br-sm px-3 py-2 space-y-1.5">
                <p className="whitespace-pre-wrap">{m.text}</p>
                {m.audioUrl && <audio controls src={m.audioUrl} className="w-full h-8" />}
              </div>
            </div>
          ) : (
            <div key={m.id} className="flex justify-start">
              <div className={`max-w-[95%] w-full text-xs rounded-2xl rounded-bl-sm px-3 py-2 space-y-2 border ${m.error ? "bg-rose-50 border-rose-100 text-rose-700" : "bg-slate-50 border-slate-200 text-slate-700"}`}>
                <p className="whitespace-pre-wrap">{m.text}</p>
                {m.draft && (
                  <DraftCard
                    draft={m.draft}
                    active={m.id === latestDraftMessageId}
                    busy={busy}
                    savingDraft={savingDraft}
                    showTranscript={showTranscript && m.id === latestDraftMessageId}
                    onToggleTranscript={() => setShowTranscript((v) => !v)}
                    onApply={handleApply}
                    onSaveDraft={handleSaveDraft}
                    onRewrite={handleRewriteAll}
                    onQuickInstruction={handleQuickInstruction}
                  />
                )}
              </div>
            </div>
          )
        )}

        {busy && (
          <div className="flex items-center gap-2 text-xs text-slate-500">
            <Loader2 className="w-3.5 h-3.5 animate-spin" />
            {t("dailyCommentPanel.aiAssistant.working")}
          </div>
        )}
      </div>

      <div className="border-t border-slate-100 p-3 space-y-2">
        {localError && <p className="text-[11px] text-rose-600">{localError}</p>}
        {recording && (
          <div className="flex items-center justify-between gap-2 bg-rose-50 border border-rose-100 rounded-lg px-3 py-2">
            <span className="flex items-center gap-2 text-xs font-semibold text-rose-600">
              <span className="w-2 h-2 rounded-full bg-rose-500 animate-pulse" />
              {t("dailyCommentPanel.aiAssistant.recording", { elapsed: formatSeconds(recordSeconds), max: formatSeconds(MAX_AUDIO_SECONDS) })}
            </span>
            <button type="button" onClick={() => stopRecording()} className="flex items-center gap-1 text-[11px] font-bold text-rose-700 hover:underline">
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
            onClick={recording ? () => stopRecording() : startRecording}
            disabled={!classSessionId || isForeignSession || busy || !!pendingAudio}
            className={`p-2 rounded-lg border ${recording ? "border-rose-300 bg-rose-50 text-rose-600" : "border-slate-200 text-slate-600 hover:bg-slate-50"} disabled:opacity-40`}
            title={t("dailyCommentPanel.aiAssistant.suggestions.record")}
          >
            {recording ? <Square className="w-4 h-4" /> : <Mic className="w-4 h-4" />}
          </button>
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={!classSessionId || isForeignSession || busy || recording || !!pendingAudio}
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
            disabled={!classSessionId || isForeignSession}
            rows={2}
            placeholder={draft ? t("dailyCommentPanel.aiAssistant.inputPlaceholderRevise") : t("dailyCommentPanel.aiAssistant.inputPlaceholder")}
            className="flex-1 bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none resize-none"
          />
          <button
            type="button"
            onClick={handleSend}
            disabled={!classSessionId || isForeignSession || busy || recording || (!pendingAudio && !input.trim())}
            className="p-2 rounded-lg bg-brand-orange text-white hover:bg-brand-orange/90 disabled:opacity-40"
            title={t("dailyCommentPanel.aiAssistant.send")}
          >
            <Send className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
}

const ATTITUDE_BADGE: Record<string, string> = {
  WEAK: "bg-rose-50 text-rose-600 border-rose-100",
  AVERAGE: "bg-amber-50 text-amber-700 border-amber-100",
  FAIR: "bg-sky-50 text-sky-700 border-sky-100",
  GOOD: "bg-emerald-50 text-emerald-700 border-emerald-100",
  EXCELLENT: "bg-violet-50 text-violet-700 border-violet-100"
};

function DraftCard({
  draft,
  active,
  busy,
  savingDraft,
  showTranscript,
  onToggleTranscript,
  onApply,
  onSaveDraft,
  onRewrite,
  onQuickInstruction
}: {
  draft: CommentAiDraftResult;
  active: boolean;
  busy: boolean;
  savingDraft: boolean;
  showTranscript: boolean;
  onToggleTranscript: () => void;
  onApply: () => void;
  onSaveDraft: () => void;
  onRewrite: () => void;
  onQuickInstruction: (text: string) => void;
}) {
  const { t } = useTranslation("academic-comments");
  const nameById = new Map(draft.rows.map((r) => [r.studentId, r.studentFullName]));
  return (
    <div className={`space-y-2 ${active ? "" : "opacity-60"}`}>
      <ul className="space-y-1.5 max-h-80 overflow-y-auto pr-1">
        {draft.rows.map((r: CommentAiDraftRow) => (
          <li key={r.studentId} className="bg-white border border-slate-200 rounded-lg p-2 space-y-1">
            <div className="flex flex-wrap items-center gap-1">
              <span className="font-bold text-slate-800">{r.studentFullName}</span>
              {r.source === "INDIVIDUAL" && (
                <span className="text-[9px] font-bold uppercase bg-brand-orange/10 text-brand-orange px-1.5 py-0.5 rounded">{t("dailyCommentPanel.aiAssistant.individualBadge")}</span>
              )}
              {r.attitude ? (
                <span className={`text-[9px] font-bold border px-1.5 py-0.5 rounded ${ATTITUDE_BADGE[r.attitude] ?? ""}`}>{t(`shared.attitudeWithPercent.${r.attitude}`)}</span>
              ) : (
                <span className="text-[9px] text-slate-400 italic">{t("dailyCommentPanel.aiAssistant.noAttitude")}</span>
              )}
            </div>
            <p className="text-slate-600 whitespace-pre-wrap">{r.content || "—"}</p>
            {r.warnings.map((w, i) => (
              <p key={i} className="flex items-start gap-1 text-[10px] text-amber-700">
                <AlertTriangle className="w-3 h-3 mt-0.5 shrink-0" />
                {w.message}
              </p>
            ))}
          </li>
        ))}
      </ul>

      {draft.unmatchedMentions.length > 0 && (
        <div className="bg-amber-50 border border-amber-100 rounded-lg p-2 space-y-1">
          <p className="text-[10px] font-bold uppercase text-amber-700">{t("dailyCommentPanel.aiAssistant.unmatchedTitle")}</p>
          {draft.unmatchedMentions.map((u, i) => (
            <p key={i} className="text-[11px] text-amber-800">
              “{u.quote}”
              {u.candidateStudentIds.length > 0 &&
                ` — ${t("dailyCommentPanel.aiAssistant.unmatchedCandidates", { names: u.candidateStudentIds.map((id) => nameById.get(id) ?? `#${id}`).join(", ") })}`}
            </p>
          ))}
        </div>
      )}

      {draft.skippedStudents.length > 0 && (
        <p className="text-[10px] text-slate-400">
          {t("dailyCommentPanel.aiAssistant.skipped", { names: draft.skippedStudents.map((s) => `${s.studentFullName} (${s.reason})`).join(", ") })}
        </p>
      )}

      {draft.transcript && active && (
        <div>
          <button type="button" onClick={onToggleTranscript} className="flex items-center gap-1 text-[10px] font-bold text-slate-500 hover:text-slate-700">
            {showTranscript ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
            {t("dailyCommentPanel.aiAssistant.transcriptToggle")}
          </button>
          {showTranscript && <p className="mt-1 text-[11px] text-slate-500 bg-white border border-slate-100 rounded p-2 whitespace-pre-wrap">{draft.transcript}</p>}
        </div>
      )}

      {active && (
        <div className="space-y-1.5 pt-1">
          <div className="flex flex-wrap gap-1.5">
            <button type="button" onClick={onApply} disabled={busy || savingDraft} className="flex items-center gap-1 text-[11px] font-semibold bg-white border border-slate-300 text-slate-700 hover:bg-slate-100 rounded-full px-3 py-1.5 disabled:opacity-50">
              <Table2 className="w-3.5 h-3.5" />
              {t("dailyCommentPanel.aiAssistant.actions.apply")}
            </button>
            <button type="button" onClick={onSaveDraft} disabled={busy || savingDraft} className="flex items-center gap-1 text-[11px] font-semibold bg-brand-orange text-white hover:bg-brand-orange/90 rounded-full px-3 py-1.5 disabled:opacity-50">
              <Save className="w-3.5 h-3.5" />
              {savingDraft ? t("dailyCommentPanel.savingDraft") : t("dailyCommentPanel.aiAssistant.actions.saveDraft")}
            </button>
            <button type="button" onClick={onRewrite} disabled={busy || !draft.extraction} className="flex items-center gap-1 text-[11px] font-semibold bg-white border border-violet-200 text-violet-700 hover:bg-violet-50 rounded-full px-3 py-1.5 disabled:opacity-50">
              <RefreshCw className="w-3.5 h-3.5" />
              {t("dailyCommentPanel.aiAssistant.actions.rewrite")}
            </button>
          </div>
          <div className="flex flex-wrap gap-1.5">
            {(["shorter", "warmer", "changeAttitude"] as const).map((key) => (
              <button
                key={key}
                type="button"
                onClick={() => onQuickInstruction(t(`dailyCommentPanel.aiAssistant.quickInstructions.${key}`))}
                disabled={busy}
                className="text-[10px] text-slate-500 border border-dashed border-slate-300 rounded-full px-2.5 py-1 hover:bg-white disabled:opacity-50"
              >
                {t(`dailyCommentPanel.aiAssistant.quickInstructionLabels.${key}`)}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
