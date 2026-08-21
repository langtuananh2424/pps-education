import React, { useEffect, useState } from "react";
import { Flag, History, Save, Send } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { StudentCommentResponse, submitComments, updateComment } from "../api";
import CommentVersionHistoryModal from "./CommentVersionHistoryModal";
import {
  ExerciseAssignmentResponse,
  ExerciseResponse,
  ReviewVideoAssignmentResponse,
  ReviewVideoSetResponse,
  listAssignmentsForClass,
  listPublishedExercisesForClass,
  listReviewVideoAssignmentsForClass,
  listReviewVideoSetsByClass
} from "@/features/lms/api";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import Select from "@/components/ui/Select";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import { toLocaleTag } from "@/lib/i18nFormat";

type GrammarMode = "OFFLINE" | "ONLINE";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none";
const statusVariants: Record<StudentCommentResponse["status"], "success" | "warning" | "danger" | "neutral"> = {
  DRAFT: "neutral",
  PENDING: "warning",
  APPROVED: "success",
  REJECTED: "danger"
};

interface CommentHistoryListProps {
  classId: number;
  history: StudentCommentResponse[];
  onChanged: () => void;
  showStudentName?: boolean;
  /** "table": hiện dạng bảng cùng bố cục cột với màn nhập trực tiếp (UC-21 DAILY) — mặc định "card" (dùng cho UC-21 MID_TERM/END_TERM, danh sách 1 học sinh). */
  layout?: "card" | "table";
  /** Chỉ cần khi layout="table" — StudentCommentResponse không có studentCode, lấy từ danh sách enrollment ở component cha. */
  studentCodeById?: Record<number, string>;
  /** Chỉ cần khi layout="table" — nhãn 2 kênh BTVN theo Loại giáo viên của buổi, ăn theo state đã tính sẵn ở DailyCommentPanel (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06). */
  grammarLabel?: string;
  videoLabel?: string;
}

/** UC-21 A1: nhận xét bị Quản lý điểm trường từ chối (kèm lý do) — Giáo viên sửa lại nội dung rồi gửi lại thẳng lên hàng chờ duyệt. */
export default function CommentHistoryList({
  classId,
  history,
  onChanged,
  showStudentName,
  layout = "card",
  studentCodeById,
  grammarLabel,
  videoLabel
}: CommentHistoryListProps) {
  const { t, i18n } = useTranslation("academic-comments");
  const resolvedGrammarLabel = grammarLabel ?? t("shared.grammarChannelFallback");
  const resolvedVideoLabel = videoLabel ?? t("shared.videoChannelFallback");
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editContent, setEditContent] = useState("");
  const [editSeverity, setEditSeverity] = useState<NonNullable<StudentCommentResponse["severity"]>>("NORMAL");
  const [editIsWarning, setEditIsWarning] = useState(false);
  const [editAttitude, setEditAttitude] = useState<"" | NonNullable<StudentCommentResponse["attitude"]>>("");
  const [editHomeworkPreviousScore, setEditHomeworkPreviousScore] = useState("");
  const [editHomeworkPreviousSpeakingScore, setEditHomeworkPreviousSpeakingScore] = useState("");
  const [editGrammarMode, setEditGrammarMode] = useState<GrammarMode>("OFFLINE");
  const [editHomeworkNext, setEditHomeworkNext] = useState("");
  /** V65: id của Exercise NGUỒN đã Publish (không phải id bản giao) — chọn từ grammarOptions. */
  const [editHomeworkNextExerciseId, setEditHomeworkNextExerciseId] = useState<number | "">("");
  const [editHomeworkNextReviewVideoSetId, setEditHomeworkNextReviewVideoSetId] = useState<number | "">("");
  const [editNote, setEditNote] = useState("");
  const [saving, setSaving] = useState(false);
  const [submittingId, setSubmittingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  /** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-19) — version history kiểu Google Sheets. */
  const [historyFor, setHistoryFor] = useState<StudentCommentResponse | null>(null);
  /** V65: nguồn khả dụng cho dropdown — Exercise đã Publish (không phải bản giao). */
  const [grammarOptions, setGrammarOptions] = useState<ExerciseResponse[]>([]);
  const [videoOptions, setVideoOptions] = useState<ReviewVideoSetResponse[]>([]);
  /** V65: bản giao ACTIVE hiện có của lớp — chỉ dùng để tra ngược "comment đã lưu chọn nguồn nào" khi bắt đầu sửa. */
  const [grammarAssignments, setGrammarAssignments] = useState<ExerciseAssignmentResponse[]>([]);
  const [videoAssignments, setVideoAssignments] = useState<ReviewVideoAssignmentResponse[]>([]);
  const { message: toastMessage, showToast } = useToast();

  useEffect(() => {
    listPublishedExercisesForClass(classId).then(setGrammarOptions).catch(() => undefined);
    listReviewVideoSetsByClass(classId)
      .then((sets) => setVideoOptions(sets.filter((s) => s.status === "PUBLISHED")))
      .catch(() => undefined);
    listAssignmentsForClass(classId).then(setGrammarAssignments).catch(() => undefined);
    listReviewVideoAssignmentsForClass(classId).then(setVideoAssignments).catch(() => undefined);
  }, [classId]);

  const startEdit = (h: StudentCommentResponse) => {
    setEditingId(h.id);
    setEditContent(h.content);
    setEditSeverity(h.severity ?? "NORMAL");
    setEditIsWarning(h.isWarning);
    setEditAttitude(h.attitude ?? "");
    setEditHomeworkPreviousScore(h.homeworkPreviousScore ?? "");
    setEditHomeworkPreviousSpeakingScore(h.homeworkPreviousSpeakingScore ?? "");
    // V127: pendingHomeworkNext* (id NGUỒN, chưa Gửi) ưu tiên trước — chỉ dòng REJECTED chưa sửa gì kể
    // từ lần Gửi trước mới cần fallback tra ngược qua grammarAssignments/videoAssignments (bản giao lần
    // đó vẫn còn hiệu lực, xem StudentCommentResponse.homeworkNextExerciseAssignmentId).
    setEditGrammarMode(h.pendingHomeworkNextExerciseId != null || h.homeworkNextExerciseAssignmentId != null ? "ONLINE" : "OFFLINE");
    setEditHomeworkNext(h.homeworkNext ?? "");
    setEditHomeworkNextExerciseId(
      h.pendingHomeworkNextExerciseId ??
        (h.homeworkNextExerciseAssignmentId != null
          ? grammarAssignments.find((a) => a.id === h.homeworkNextExerciseAssignmentId)?.exerciseId ?? ""
          : "")
    );
    setEditHomeworkNextReviewVideoSetId(
      h.pendingHomeworkNextReviewVideoSetId ??
        (h.homeworkNextReviewVideoAssignmentId != null
          ? videoAssignments.find((a) => a.id === h.homeworkNextReviewVideoAssignmentId)?.reviewVideoSetId ?? ""
          : "")
    );
    setEditNote(h.note ?? "");
    setError(null);
  };

  const handleSubmitDraft = async (commentId: number) => {
    setSubmittingId(commentId);
    setError(null);
    try {
      await submitComments(classId, [commentId]);
      onChanged();
      showToast(t("historyList.toasts.submitted"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("historyList.errors.submitFailed"));
    } finally {
      setSubmittingId(null);
    }
  };

  const handleResend = async (commentId: number) => {
    if (!editContent.trim()) {
      setError(t("historyList.errors.emptyContent"));
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await updateComment(commentId, {
        content: editContent.trim(),
        severity: editSeverity,
        isWarning: editIsWarning,
        attitude: editAttitude || undefined,
        homeworkPreviousScore: editHomeworkPreviousScore.trim() || undefined,
        homeworkPreviousSpeakingScore: editHomeworkPreviousSpeakingScore.trim() || undefined,
        homeworkNext: editGrammarMode === "OFFLINE" ? editHomeworkNext.trim() || undefined : undefined,
        homeworkNextExerciseId: editGrammarMode === "ONLINE" && editHomeworkNextExerciseId !== "" ? editHomeworkNextExerciseId : undefined,
        homeworkNextReviewVideoSetId: editHomeworkNextReviewVideoSetId !== "" ? editHomeworkNextReviewVideoSetId : undefined,
        note: editNote.trim() || undefined
      });
      // UC-21 (2026-07-29, BE PR #113 khôi phục DRAFT cho DAILY): updateComment() giờ luôn giữ ở DRAFT,
      // dùng chung 100% với MID_TERM/END_TERM — mọi commentType đều cần gọi thêm submitComments() để
      // chuyển DRAFT → PENDING.
      await submitComments(classId, [commentId]);
      setEditingId(null);
      onChanged();
      showToast(t("historyList.toasts.resent"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("historyList.errors.resendFailed"));
    } finally {
      setSaving(false);
    }
  };

  if (history.length === 0) {
    return <p className="text-xs text-slate-400 italic">{t("historyList.empty")}</p>;
  }

  const renderEditForm = (h: StudentCommentResponse) => (
    <div className="space-y-2 pt-1">
      {h.commentType === "DAILY" && (
                <div className="grid grid-cols-3 gap-2">
                  <Select value={editAttitude} onChange={(e) => setEditAttitude(e.target.value as typeof editAttitude)} className={inputClass}>
                    <option value="">{t("historyList.placeholders.attitude")}</option>
                    {(["WEAK", "AVERAGE", "FAIR", "GOOD", "EXCELLENT"] as const).map((value) => (
                      <option key={value} value={value}>
                        {t(`shared.attitude.${value}`)}
                      </option>
                    ))}
                  </Select>
                  <input
                    value={editHomeworkPreviousScore}
                    onChange={(e) => setEditHomeworkPreviousScore(e.target.value)}
                    placeholder={t("historyList.placeholders.homeworkPreviousScore")}
                    className={inputClass}
                  />
                  <input
                    value={editHomeworkPreviousSpeakingScore}
                    onChange={(e) => setEditHomeworkPreviousSpeakingScore(e.target.value)}
                    placeholder={t("historyList.placeholders.homeworkPreviousSpeakingScore")}
                    className={inputClass}
                  />
                </div>
              )}
              <textarea value={editContent} onChange={(e) => setEditContent(e.target.value)} rows={3} className={inputClass} />
              {h.commentType === "DAILY" && (
                <>
                  <div className="space-y-1">
                    <div className="flex gap-1">
                      <button
                        type="button"
                        onClick={() => {
                          setEditGrammarMode("OFFLINE");
                          setEditHomeworkNextExerciseId("");
                        }}
                        className={`flex-1 text-[10px] font-bold py-1.5 rounded-lg border ${
                          editGrammarMode === "OFFLINE" ? "bg-brand-orange border-brand-orange text-white" : "bg-slate-50 border-slate-200 text-slate-500"
                        }`}
                      >
                        {t("historyList.grammarModeOfflineButton")}
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setEditGrammarMode("ONLINE");
                          setEditHomeworkNext("");
                        }}
                        className={`flex-1 text-[10px] font-bold py-1.5 rounded-lg border ${
                          editGrammarMode === "ONLINE" ? "bg-brand-orange border-brand-orange text-white" : "bg-slate-50 border-slate-200 text-slate-500"
                        }`}
                      >
                        {t("historyList.grammarModeOnlineButton")}
                      </button>
                    </div>
                    {editGrammarMode === "OFFLINE" ? (
                      <input value={editHomeworkNext} onChange={(e) => setEditHomeworkNext(e.target.value)} placeholder={t("historyList.placeholders.homeworkNextOffline")} className={inputClass} />
                    ) : (
                      <Select
                        value={editHomeworkNextExerciseId}
                        onChange={(e) => setEditHomeworkNextExerciseId(e.target.value ? Number(e.target.value) : "")}
                        className={inputClass}
                      >
                        <option value="">{t("historyList.placeholders.chooseExercise")}</option>
                        {grammarOptions.map((ex) => (
                          <option key={ex.id} value={ex.id}>
                            {ex.examCode} - {ex.title}
                          </option>
                        ))}
                      </Select>
                    )}
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    <Select
                      value={editHomeworkNextReviewVideoSetId}
                      onChange={(e) => setEditHomeworkNextReviewVideoSetId(e.target.value ? Number(e.target.value) : "")}
                      className={inputClass}
                    >
                      <option value="">{t("historyList.placeholders.noVideoAssign")}</option>
                      {videoOptions.map((s) => (
                        <option key={s.id} value={s.id}>
                          {s.title} ({s.code})
                        </option>
                      ))}
                    </Select>
                    <input value={editNote} onChange={(e) => setEditNote(e.target.value)} placeholder={t("historyList.placeholders.note")} className={inputClass} />
                  </div>
                </>
              )}
              <div className="grid grid-cols-2 gap-2">
                <Select value={editSeverity} onChange={(e) => setEditSeverity(e.target.value as NonNullable<StudentCommentResponse["severity"]>)} className={inputClass}>
                  <option value="POSITIVE">{t("historyList.severity.POSITIVE")}</option>
                  <option value="NORMAL">{t("historyList.severity.NORMAL")}</option>
                  <option value="CONCERN">{t("historyList.severity.CONCERN")}</option>
                  <option value="WARNING">{t("historyList.severity.WARNING")}</option>
                </Select>
                <label className="flex items-center gap-1.5 text-[10px] font-semibold text-slate-600">
                  <input type="checkbox" checked={editIsWarning} onChange={(e) => setEditIsWarning(e.target.checked)} />
                  <Flag className="w-3 h-3 text-rose-500" />
                  {t("historyList.specialWarningLabel")}
                </label>
              </div>
              <div className="flex gap-2">
                <Button type="button" size="sm" variant="secondary" onClick={() => setEditingId(null)}>
                  {t("historyList.cancel")}
                </Button>
                <Button type="button" size="sm" variant="primary" onClick={() => handleResend(h.id)} disabled={saving}>
                  <Send className="w-3.5 h-3.5" />
                  {saving ? t("historyList.sending") : t("historyList.sendAgain")}
                </Button>
              </div>
            </div>
  );

  const renderReadOnlyExtras = (h: StudentCommentResponse) => (
    <>
      {h.commentType === "DAILY" &&
        (h.attitude || h.homeworkPreviousScore || h.homeworkPreviousSpeakingScore || h.grammarPreviousProgress || h.videoPreviousProgress) && (
          <p className="text-slate-500">
            {h.attitude && t("historyList.readOnlyExtras.attitude", { value: t(`shared.attitude.${h.attitude}`) })}
            {h.attitude && h.homeworkPreviousScore && " · "}
            {h.homeworkPreviousScore && t("historyList.readOnlyExtras.homeworkPreviousScore", { value: h.homeworkPreviousScore })}
            {(h.attitude || h.homeworkPreviousScore) && h.homeworkPreviousSpeakingScore && " · "}
            {h.homeworkPreviousSpeakingScore && t("historyList.readOnlyExtras.homeworkPreviousSpeakingScore", { value: h.homeworkPreviousSpeakingScore })}
            {(h.attitude || h.homeworkPreviousScore || h.homeworkPreviousSpeakingScore) && (h.grammarPreviousProgress || h.videoPreviousProgress) && " · "}
            {h.grammarPreviousProgress && t("historyList.readOnlyExtras.grammarPreviousProgress", { value: h.grammarPreviousProgress })}
            {h.grammarPreviousProgress && h.videoPreviousProgress && " · "}
            {h.videoPreviousProgress && t("historyList.readOnlyExtras.videoPreviousProgress", { value: h.videoPreviousProgress })}
          </p>
        )}
      <p className="text-slate-700">{h.content}</p>
      {h.commentType === "DAILY" && (h.homeworkNext || h.homeworkNextExerciseTitle || h.homeworkNextReviewVideoSetTitle || h.note) && (
        <p className="text-slate-500">
          {h.homeworkNext && t("historyList.readOnlyExtras.homeworkNextOffline", { value: h.homeworkNext })}
          {h.homeworkNextExerciseTitle && t("historyList.readOnlyExtras.homeworkNextOnline", { value: h.homeworkNextExerciseTitle })}
          {(h.homeworkNext || h.homeworkNextExerciseTitle) && h.homeworkNextReviewVideoSetTitle && " · "}
          {h.homeworkNextReviewVideoSetTitle && t("historyList.readOnlyExtras.homeworkNextVideo", { value: h.homeworkNextReviewVideoSetTitle })}
          {(h.homeworkNext || h.homeworkNextExerciseTitle || h.homeworkNextReviewVideoSetTitle) && h.note && " · "}
          {h.note && t("historyList.readOnlyExtras.note", { value: h.note })}
        </p>
      )}
      {h.status === "REJECTED" && h.rejectionReason && (
        <p className="text-rose-500">{t("historyList.rejectionReason", { reason: h.rejectionReason })}</p>
      )}
    </>
  );

  const renderActions = (h: StudentCommentResponse) => (
    <>
      {/* layout="table" (DAILY, DailyCommentPanel) đã gộp Ghi+Gửi vào đúng 1 nút "Gửi nhận xét" ở màn
          nhập trực tiếp (kể cả dòng đang DRAFT) — nút "Nộp duyệt" riêng ở đây thành thừa/dễ gây nộp 2
          lần, chỉ giữ cho layout="card" (MID_TERM/END_TERM, PeriodicCommentPanel — CommentForm chỉ lưu
          nháp, "Nộp duyệt" ở đây là cách DUY NHẤT để gửi duyệt) — đã xác nhận với người dùng 2026-07-30. */}
      {h.status === "DRAFT" && layout !== "table" && (
        <Button size="sm" variant="secondary" onClick={() => handleSubmitDraft(h.id)} disabled={submittingId === h.id}>
          <Save className="w-3.5 h-3.5" />
          {submittingId === h.id ? t("historyList.submitting") : t("historyList.submitDraft")}
        </Button>
      )}
      {h.status === "REJECTED" && (
        <Button size="sm" variant="secondary" onClick={() => startEdit(h)}>
          {t("historyList.editAndResend")}
        </Button>
      )}
      <button
        type="button"
        onClick={() => setHistoryFor(h)}
        title={t("historyList.versionHistoryTitle")}
        className="inline-flex items-center gap-1 text-[10px] font-bold text-slate-400 hover:text-slate-600"
      >
        <History className="w-3.5 h-3.5" />
        {t("historyList.versionHistoryLabel")}
      </button>
    </>
  );

  if (layout === "table") {
    // Đồng bộ đúng bố cục/tên cột với bảng nhập trực tiếp ở trên + 2 màn Quản lý điểm trường (bổ sung
    // ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06) — "BTVN buổi trước"/"buổi sau" đều tách 3
    // kênh Offline/Bài/Video, thêm cột Hạn nộp bài, border rõ + căn giữa header.
    const COLUMN_COUNT = 14;
    return (
      <div className="space-y-2">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        <TableContainer>
          <thead>
            <tr className="border-b border-slate-300 [&>th]:text-center">
              <Th rowSpan={2} className="min-w-[120px] border-r border-slate-300">{t("historyList.columns.studentCode")}</Th>
              <Th rowSpan={2} className="border-r border-slate-300">{t("historyList.columns.fullName")}</Th>
              <Th rowSpan={2} className="border-r border-slate-300">{t("historyList.columns.dateOfBirth")}</Th>
              <Th colSpan={3} className="text-center border-r border-slate-300">{t("historyList.columns.homeworkPrevious")}</Th>
              <Th rowSpan={2} className="border-r border-slate-300">{t("historyList.columns.homeworkOffline")}</Th>
              <Th colSpan={2} className="text-center border-r border-slate-300">{t("historyList.columns.homeworkOnline")}</Th>
              <Th rowSpan={2} className="border-r border-slate-300">{t("historyList.columns.dueDate")}</Th>
              <Th rowSpan={2} className="border-r border-slate-300">{t("historyList.columns.attitude")}</Th>
              <Th rowSpan={2} className="border-r border-slate-300">{t("historyList.columns.studentComment")}</Th>
              <Th rowSpan={2} className="border-r border-slate-300">{t("historyList.columns.note")}</Th>
              <Th rowSpan={2}>{t("historyList.columns.status")}</Th>
            </tr>
            <tr className="border-b border-slate-300 [&>th]:text-center">
              <Th className="border-r border-slate-300 text-center">{t("historyList.columns.offline")}</Th>
              <Th className="border-r border-slate-300 text-center">{resolvedGrammarLabel}</Th>
              <Th className="border-r border-slate-300 text-center">{resolvedVideoLabel}</Th>
              <Th className="border-r border-slate-300 text-center">{resolvedGrammarLabel}</Th>
              <Th className="border-r border-slate-300 text-center">{resolvedVideoLabel}</Th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-300">
            {history.map((h) => (
              <React.Fragment key={h.id}>
                <tr className="hover:bg-slate-50/40">
                  <Td className="font-mono font-bold text-slate-500 border-r border-slate-300">{studentCodeById?.[h.studentId] ?? "—"}</Td>
                  <Td className="font-bold text-slate-900 whitespace-nowrap border-r border-slate-300">{h.studentFullName}</Td>
                  <Td className="whitespace-nowrap text-slate-500 border-r border-slate-300">{h.studentDateOfBirth ?? "—"}</Td>
                  <Td className="min-w-[110px] border-r border-slate-300">{h.homeworkPreviousOfflineText || "—"}</Td>
                  <Td className="min-w-[130px] border-r border-slate-300">{h.homeworkPreviousScore || "—"}</Td>
                  <Td className="min-w-[130px] border-r border-slate-300">{h.homeworkPreviousSpeakingScore || "—"}</Td>
                  <Td className="min-w-[160px] border-r border-slate-300">{h.homeworkNext || "—"}</Td>
                  <Td className="min-w-[180px] border-r border-slate-300">{h.homeworkNextExerciseTitle || "—"}</Td>
                  <Td className="min-w-[180px] border-r border-slate-300">{h.homeworkNextReviewVideoSetTitle || "—"}</Td>
                  <Td className="min-w-[120px] whitespace-nowrap border-r border-slate-300">
                    {h.homeworkNextDueAt ? new Date(h.homeworkNextDueAt).toLocaleString(toLocaleTag(i18n.language), { dateStyle: "short", timeStyle: "short" }) : "—"}
                  </Td>
                  <Td className="min-w-[110px] border-r border-slate-300">{h.attitude ? t(`shared.attitude.${h.attitude}`) : "—"}</Td>
                  <Td className="min-w-[260px] whitespace-pre-wrap border-r border-slate-300">{h.content}</Td>
                  <Td className="min-w-[120px] border-r border-slate-300">{h.note || "—"}</Td>
                  <Td className="min-w-[150px] whitespace-nowrap space-y-1.5">
                    <Badge variant={statusVariants[h.status]}>{t(`shared.status.${h.status}`)}</Badge>
                    <div>{renderActions(h)}</div>
                  </Td>
                </tr>
                {h.status === "REJECTED" && h.rejectionReason && editingId !== h.id && (
                  <tr>
                    <td colSpan={COLUMN_COUNT} className="px-4 pb-2 -mt-2 text-[11px] text-rose-500">
                      {t("historyList.rejectionReason", { reason: h.rejectionReason })}
                    </td>
                  </tr>
                )}
                {editingId === h.id && (
                  <tr>
                    <td colSpan={COLUMN_COUNT} className="px-4 pb-3 bg-slate-50/60">
                      {renderEditForm(h)}
                    </td>
                  </tr>
                )}
              </React.Fragment>
            ))}
          </tbody>
        </TableContainer>
        <Toast message={toastMessage} />
        {historyFor && (
          <CommentVersionHistoryModal commentId={historyFor.id} studentFullName={historyFor.studentFullName} onClose={() => setHistoryFor(null)} />
        )}
      </div>
    );
  }

  return (
    <div className="space-y-2">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
      {history.map((h) => (
        <div key={h.id} className="border border-slate-200 rounded-lg p-2.5 text-[11px] space-y-1.5">
          <div className="flex items-center justify-between gap-2">
            {showStudentName && <span className="font-bold text-slate-800">{h.studentFullName}</span>}
            <span className="text-slate-400">{h.commentDate}</span>
            <Badge variant={statusVariants[h.status]}>{t(`shared.status.${h.status}`)}</Badge>
          </div>
          {editingId === h.id ? (
            renderEditForm(h)
          ) : (
            <>
              {renderReadOnlyExtras(h)}
              {renderActions(h)}
            </>
          )}
        </div>
      ))}

      <Toast message={toastMessage} />
      {historyFor && (
        <CommentVersionHistoryModal commentId={historyFor.id} studentFullName={historyFor.studentFullName} onClose={() => setHistoryFor(null)} />
      )}
    </div>
  );
}
