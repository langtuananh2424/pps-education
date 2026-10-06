import React, { useEffect, useRef, useState } from "react";
import { AlertTriangle, Bot, ChevronDown, ChevronUp, Loader2, Mic, Paperclip, RefreshCw, Save, Sparkles, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import AiChatComposer, { AiChatComposerHandle, AiComposerAudio, formatSeconds } from "@/components/ai/AiChatComposer";
import { ApiError } from "@/lib/apiClient";
import Select from "@/components/ui/Select";
import {
  AiJob,
  GradeEvaluationResultResponse,
  TermCommentAiDraftResult,
  applyTermCommentAiDraft,
  reviseTermCommentAiDraft,
  startTermCommentAiDraft,
  termCommentAiDraftJobPath,
  waitForAiJob
} from "../api";

type ChatMessageInput =
  | { role: "teacher"; text: string; audioUrl?: string }
  | { role: "assistant"; text: string; draft?: TermCommentAiDraftResult; error?: boolean };
type ChatMessage = ChatMessageInput & { id: number };

export interface TermCommentAiSetupOption {
  setupId: number;
  evaluationType: "MID_TERM" | "END_TERM";
  label: string;
}

interface Props {
  open: boolean;
  onClose: () => void;
  classId: number;
  /** Các setup Giữa kỳ/Cuối kỳ của kỳ học đang chọn có đầu điểm — sidebar soạn cho 1 setup tại 1 thời điểm. */
  setups: TermCommentAiSetupOption[];
  /** Ghi xong vào sổ điểm — màn hình cha tải lại bảng điểm của đúng setup đó. */
  onApplied: (setupId: number, results: GradeEvaluationResultResponse[]) => void;
  /** Báo trạng thái chạy nền ra ngoài — nút nổi (AiAssistantFab) hiện vòng xoay khi sidebar đã đóng. */
  onBusyChange?: (busy: boolean) => void;
}

/** UC-76 A7 — dòng đã có Nhận xét trong sổ điểm không được chọn sẵn, giáo viên tự chọn nếu muốn ghi đè. */
const defaultSelection = (draft: TermCommentAiDraftResult) =>
  new Set(draft.rows.filter((r) => r.content && !r.existingComment).map((r) => r.studentId));

/**
 * UC-76: Trợ lý AI soạn nháp Nhận xét Giữa kỳ/Cuối kỳ (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-05;
 * chuyển sang sidebar trò chuyện như trợ lý UC-74 ngày 2026-10-06). Giáo viên bấm "Soạn cả lớp" (thuần từ điểm) hoặc
 * nói/gõ ý chung, rồi trò chuyện để sửa ("An viết ngắn lại", "nhấn mạnh phần nói"). Bản nháp có nút gợi ý thao tác: Lưu
 * vào sổ điểm (các dòng đã chọn, trạng thái giữ nguyên) / Viết lại cho đa dạng hơn. Không có nút Gửi duyệt ở đây.
 */
export default function TermCommentAiAssistantSidebar({ open, onClose, classId, setups, onApplied, onBusyChange }: Props) {
  const { t } = useTranslation("academic-grades");
  const [setupId, setSetupId] = useState<number | null>(setups[0]?.setupId ?? null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState<TermCommentAiDraftResult | null>(null);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [busy, setBusy] = useState(false);
  const [saving, setSaving] = useState(false);
  const [showTranscript, setShowTranscript] = useState(false);
  const nextId = useRef(1);
  /** Tăng mỗi lần đổi setup — kết quả job của setup cũ về muộn thì bỏ. */
  const runToken = useRef(0);
  const composerRef = useRef<AiChatComposerHandle>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  const push = (message: ChatMessageInput) => setMessages((prev) => [...prev, { ...message, id: nextId.current++ }]);

  // Setup đang chọn không còn (đổi kỳ học) → chọn setup đầu tiên.
  useEffect(() => {
    if (!setups.some((s) => s.setupId === setupId)) setSetupId(setups[0]?.setupId ?? null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [setups]);

  // Đổi setup = cuộc trò chuyện mới (bản nháp gắn với đúng sổ điểm Giữa kỳ hoặc Cuối kỳ đó).
  useEffect(() => {
    runToken.current += 1;
    setMessages([]);
    setDraft(null);
    setSelected(new Set());
    setBusy(false);
    composerRef.current?.setText("");
  }, [setupId, classId]);

  useEffect(() => {
    onBusyChange?.(busy);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [busy]);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, busy]);

  const runJob = async (start: () => Promise<AiJob<TermCommentAiDraftResult>>, keepSelection = false) => {
    const token = ++runToken.current;
    setBusy(true);
    try {
      const job = await waitForAiJob(await start(), termCommentAiDraftJobPath);
      if (token !== runToken.current) return;
      if (job.status === "DONE" && job.result) {
        const result = job.result;
        setDraft(result);
        setSelected((prev) => {
          if (!keepSelection) return defaultSelection(result);
          const withContent = new Set(result.rows.filter((r) => r.content).map((r) => r.studentId));
          return new Set([...prev].filter((id) => withContent.has(id)));
        });
        push({ role: "assistant", text: result.assistantMessage, draft: result });
      } else {
        push({ role: "assistant", text: job.errorMessage ?? t("aiAssistant.errors.timeout"), error: true });
      }
    } catch (err) {
      if (token !== runToken.current) return;
      push({ role: "assistant", text: err instanceof ApiError ? err.message : t("aiAssistant.errors.generic"), error: true });
    } finally {
      if (token === runToken.current) setBusy(false);
    }
  };

  const currentRows = (d: TermCommentAiDraftResult) => d.rows.map((r) => ({ studentId: r.studentId, content: r.content }));

  const history = () =>
    messages
      .filter((m) => !(m.role === "assistant" && m.error))
      .slice(-6)
      .map((m) => ({ role: m.role, text: m.text }));

  const handleStart = () => {
    if (!setupId || busy) return;
    const id = setupId;
    push({ role: "teacher", text: t("aiAssistant.suggestions.draftAll") });
    void runJob(() => startTermCommentAiDraft(classId, id, null, ""));
  };

  const handleSend = (audio: AiComposerAudio | null, text: string): boolean => {
    if (!setupId || busy) return false;
    const id = setupId;
    const label = audio ? text || t("aiAssistant.audioMessage", { duration: audio.seconds ? formatSeconds(audio.seconds) : "?" }) : text;
    push({ role: "teacher", text: label, audioUrl: audio?.url });
    const d = draft;
    if (!d) {
      void runJob(() => startTermCommentAiDraft(classId, id, audio?.blob ?? null, text));
    } else {
      void runJob(
        () => reviseTermCommentAiDraft(classId, id, { mode: "INSTRUCTION", currentRows: currentRows(d), history: history() }, audio?.blob ?? null, text),
        true
      );
    }
    return true;
  };

  const handleRewriteAll = () => {
    if (!setupId || !draft || busy) return;
    const id = setupId;
    const d = draft;
    push({ role: "teacher", text: t("aiAssistant.actions.rewrite") });
    void runJob(() => reviseTermCommentAiDraft(classId, id, { mode: "REWRITE_ALL", currentRows: currentRows(d) }), true);
  };

  const handleSave = async () => {
    if (!setupId || !draft) return;
    const rows = draft.rows
      .filter((r) => selected.has(r.studentId) && r.content)
      .map((r) => ({ studentId: r.studentId, comment: r.content as string, aiDraftContent: r.content }));
    if (rows.length === 0) return;
    setSaving(true);
    try {
      const saved = await applyTermCommentAiDraft(classId, setupId, rows);
      onApplied(setupId, saved);
      push({ role: "assistant", text: t("aiAssistant.savedMessage", { count: saved.length }) });
    } catch (err) {
      push({ role: "assistant", text: err instanceof ApiError ? err.message : t("aiAssistant.errors.save"), error: true });
    } finally {
      setSaving(false);
    }
  };

  const toggle = (studentId: number) =>
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(studentId)) next.delete(studentId);
      else next.add(studentId);
      return next;
    });

  if (!open) return null;

  const latestDraftMessageId = [...messages].reverse().find((m) => m.role === "assistant" && m.draft)?.id;
  const currentSetup = setups.find((s) => s.setupId === setupId);

  return (
    <div className="fixed inset-y-0 right-0 z-40 w-full sm:w-[560px] xl:w-[640px] bg-white border-l border-slate-200 shadow-2xl flex flex-col">
      <div className="px-4 py-3 border-b border-slate-100 flex items-center justify-between gap-2 bg-slate-50">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-1.5 text-base font-bold text-slate-800">
            <Bot className="w-5 h-5 text-violet-600" />
            {t("aiAssistant.title")}
          </div>
          {setups.length > 1 ? (
            <Select
              value={setupId ?? ""}
              onChange={(e) => setSetupId(Number(e.target.value))}
              disabled={busy || saving}
              className="mt-1 text-sm py-1"
            >
              {setups.map((s) => (
                <option key={s.setupId} value={s.setupId}>
                  {s.label}
                </option>
              ))}
            </Select>
          ) : (
            <p className="text-sm text-slate-400 truncate">{currentSetup?.label ?? t("aiAssistant.noSetup")}</p>
          )}
        </div>
        <button type="button" onClick={onClose} className="p-1.5 rounded-lg hover:bg-slate-200 text-slate-500" aria-label={t("aiAssistant.close")}>
          <X className="w-4 h-4" />
        </button>
      </div>

      <div ref={scrollRef} className="flex-1 overflow-y-auto px-4 py-3 space-y-3">
        <div className="text-sm text-slate-600 bg-violet-50 border border-violet-100 rounded-lg p-3 space-y-1.5">
          <p>{t("aiAssistant.intro")}</p>
          <p className="text-sm text-violet-700">{t("aiAssistant.permissionNote")}</p>
        </div>

        {messages.length === 0 && setupId && (
          <div className="flex flex-wrap gap-2">
            <button type="button" onClick={handleStart} className="flex items-center gap-1.5 text-[13px] font-semibold bg-violet-600 text-white hover:bg-violet-700 rounded-full px-3 py-1.5">
              <Sparkles className="w-3.5 h-3.5" />
              {t("aiAssistant.suggestions.draftAll")}
            </button>
            <button type="button" onClick={() => composerRef.current?.startRecording()} className="flex items-center gap-1.5 text-[13px] font-semibold border border-violet-200 text-violet-700 bg-white hover:bg-violet-50 rounded-full px-3 py-1.5">
              <Mic className="w-3.5 h-3.5" />
              {t("aiAssistant.suggestions.record")}
            </button>
            <button type="button" onClick={() => composerRef.current?.openFilePicker()} className="flex items-center gap-1.5 text-[13px] font-semibold border border-violet-200 text-violet-700 bg-white hover:bg-violet-50 rounded-full px-3 py-1.5">
              <Paperclip className="w-3.5 h-3.5" />
              {t("aiAssistant.suggestions.upload")}
            </button>
          </div>
        )}

        {messages.map((m) =>
          m.role === "teacher" ? (
            <div key={m.id} className="flex justify-end">
              <div className="max-w-[85%] bg-brand-orange/10 border border-brand-orange/20 text-sm text-slate-800 rounded-2xl rounded-br-sm px-3 py-2 space-y-1.5">
                <p className="whitespace-pre-wrap">{m.text}</p>
                {m.audioUrl && <audio controls src={m.audioUrl} className="w-full h-8" />}
              </div>
            </div>
          ) : (
            <div key={m.id} className="flex justify-start">
              <div className={`max-w-[95%] w-full text-sm rounded-2xl rounded-bl-sm px-3 py-2 space-y-2 border ${m.error ? "bg-rose-50 border-rose-100 text-rose-700" : "bg-slate-50 border-slate-200 text-slate-700"}`}>
                <p className="whitespace-pre-wrap">{m.text}</p>
                {m.draft && (
                  <DraftCard
                    draft={m.draft}
                    active={m.id === latestDraftMessageId}
                    selected={selected}
                    onToggle={toggle}
                    busy={busy}
                    saving={saving}
                    showTranscript={showTranscript && m.id === latestDraftMessageId}
                    onToggleTranscript={() => setShowTranscript((v) => !v)}
                    onSave={handleSave}
                    onRewrite={handleRewriteAll}
                    onQuickInstruction={(text) => composerRef.current?.setText(text)}
                  />
                )}
              </div>
            </div>
          )
        )}

        {busy && (
          <div className="flex items-center gap-2 text-sm text-slate-500">
            <Loader2 className="w-3.5 h-3.5 animate-spin" />
            {t("aiAssistant.working")}
          </div>
        )}
      </div>

      <AiChatComposer
        ref={composerRef}
        disabled={!setupId}
        busy={busy || saving}
        placeholder={draft ? t("aiAssistant.inputPlaceholderRevise") : t("aiAssistant.inputPlaceholder")}
        onSend={handleSend}
      />
    </div>
  );
}

function DraftCard({
  draft,
  active,
  selected,
  onToggle,
  busy,
  saving,
  showTranscript,
  onToggleTranscript,
  onSave,
  onRewrite,
  onQuickInstruction
}: {
  draft: TermCommentAiDraftResult;
  active: boolean;
  selected: Set<number>;
  onToggle: (studentId: number) => void;
  busy: boolean;
  saving: boolean;
  showTranscript: boolean;
  onToggleTranscript: () => void;
  onSave: () => void;
  onRewrite: () => void;
  onQuickInstruction: (text: string) => void;
}) {
  const { t } = useTranslation("academic-grades");
  const selectedCount = draft.rows.filter((r) => r.content && selected.has(r.studentId)).length;
  return (
    <div className={`space-y-2 ${active ? "" : "opacity-60"}`}>
      <ul className="space-y-1.5 max-h-[28rem] overflow-y-auto pr-1">
        {draft.rows.map((r) => (
          <li key={r.studentId} className={`bg-white border rounded-lg p-2 space-y-1 ${active && selected.has(r.studentId) ? "border-violet-300" : "border-slate-200"}`}>
            <label className="flex items-start gap-2 cursor-pointer">
              {active && (
                <input
                  type="checkbox"
                  className="mt-1"
                  checked={selected.has(r.studentId)}
                  disabled={!r.content || busy || saving}
                  onChange={() => onToggle(r.studentId)}
                />
              )}
              <span className="flex-1 min-w-0 space-y-1">
                <span className="block font-bold text-slate-800">{r.studentFullName}</span>
                <span className="block text-[12px] text-slate-400">{r.scoreSummary}</span>
                {r.existingComment && (
                  <span className="block text-[12px] text-slate-500 italic">
                    {t("aiAssistant.existingComment")}: {r.existingComment}
                  </span>
                )}
                <span className="block text-slate-600 whitespace-pre-wrap">{r.content || "—"}</span>
              </span>
            </label>
            {r.warnings.map((w, i) => (
              <p key={i} className="flex items-start gap-1 text-sm text-amber-700">
                <AlertTriangle className="w-3 h-3 mt-0.5 shrink-0" />
                {w.message}
              </p>
            ))}
          </li>
        ))}
      </ul>

      {draft.skippedStudents.length > 0 && (
        <p className="text-sm text-slate-400">
          {t("aiAssistant.skipped", { names: draft.skippedStudents.map((s) => `${s.studentFullName} (${s.reason})`).join(", ") })}
        </p>
      )}

      {draft.transcript && active && (
        <div>
          <button type="button" onClick={onToggleTranscript} className="flex items-center gap-1 text-sm font-bold text-slate-500 hover:text-slate-700">
            {showTranscript ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
            {t("aiAssistant.transcriptToggle")}
          </button>
          {showTranscript && <p className="mt-1 text-[13px] text-slate-500 bg-white border border-slate-100 rounded p-2 whitespace-pre-wrap">{draft.transcript}</p>}
        </div>
      )}

      {active && (
        <div className="space-y-1.5 pt-1">
          <div className="flex flex-wrap gap-1.5">
            <button type="button" onClick={onSave} disabled={busy || saving || selectedCount === 0} className="flex items-center gap-1 text-[13px] font-semibold bg-brand-orange text-white hover:bg-brand-orange/90 rounded-full px-3 py-1.5 disabled:opacity-50">
              {saving ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Save className="w-3.5 h-3.5" />}
              {t("aiAssistant.actions.save", { count: selectedCount })}
            </button>
            <button type="button" onClick={onRewrite} disabled={busy || saving} className="flex items-center gap-1 text-[13px] font-semibold bg-white border border-violet-200 text-violet-700 hover:bg-violet-50 rounded-full px-3 py-1.5 disabled:opacity-50">
              <RefreshCw className="w-3.5 h-3.5" />
              {t("aiAssistant.actions.rewrite")}
            </button>
          </div>
          <div className="flex flex-wrap gap-1.5">
            {(["shorter", "warmer", "focusWeak"] as const).map((key) => (
              <button
                key={key}
                type="button"
                onClick={() => onQuickInstruction(t(`aiAssistant.quickInstructions.${key}`))}
                disabled={busy}
                className="text-sm text-slate-500 border border-dashed border-slate-300 rounded-full px-2.5 py-1 hover:bg-white disabled:opacity-50"
              >
                {t(`aiAssistant.quickInstructionLabels.${key}`)}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
