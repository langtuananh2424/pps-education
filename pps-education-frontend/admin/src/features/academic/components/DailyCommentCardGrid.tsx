import React from "react";
import { AlertTriangle, History, Sparkles } from "lucide-react";
import { useTranslation } from "react-i18next";
import Select from "@/components/ui/Select";
import StudentNameLink from "@/features/reports/components/StudentNameLink";
import { AttendanceMarkResponse, CommentAiDraftWarning, CommentAttitude, StudentCommentResponse } from "../api";
import { commentSimilarity } from "../lib/commentSimilarity";

const ATTITUDES: CommentAttitude[] = ["WEAK", "AVERAGE", "FAIR", "GOOD", "EXCELLENT"];
/** Mirror ngưỡng mặc định app.ai-comment-draft.similarity-threshold (BE). */
const SIMILARITY_WARN_THRESHOLD = 0.5;

export interface DailyCommentCardRow {
  studentId: number;
  studentFullName: string;
  studentCode: string;
  attitude: "" | CommentAttitude;
  content: string;
  note: string;
}

interface Props {
  rows: DailyCommentCardRow[];
  history: StudentCommentResponse[];
  attendanceByStudent: Record<number, AttendanceMarkResponse["status"]>;
  /** Nhận xét các buổi TRƯỚC của từng học sinh trong lớp (mới nhất trước) — để giáo viên tự đối chiếu trùng lặp. */
  previousByStudent: Record<number, StudentCommentResponse[]>;
  /** Cảnh báo của trợ lý AI (UC-74) cho các dòng vừa được áp dụng từ bản nháp. */
  aiWarningsByStudent: Record<number, CommentAiDraftWarning[]>;
  aiAppliedStudentIds: Set<number>;
  highlightedStudentId: number | null;
  onUpdateRow: (studentId: number, patch: Partial<Pick<DailyCommentCardRow, "attitude" | "content" | "note">>) => void;
}

/**
 * Dạng xem THẺ của Nhận xét học viên (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-28) — cùng
 * dữ liệu và cùng rào khoá dòng với dạng Bảng (DailyCommentPanel), chỉ tập trung vào 3 ô viết tay
 * (Thái độ/Nhận xét/Ghi chú). Điểm/BTVN vẫn nhập ở dạng Bảng. Mỗi thẻ hiện kèm nhận xét buổi trước của
 * học sinh để giáo viên thấy ngay có lặp lại câu chữ không, thay vì phải mở hồ sơ từng bạn.
 */
export default function DailyCommentCardGrid({
  rows,
  history,
  attendanceByStudent,
  previousByStudent,
  aiWarningsByStudent,
  aiAppliedStudentIds,
  highlightedStudentId,
  onUpdateRow
}: Props) {
  const { t } = useTranslation("academic-comments");
  return (
    <div className="grid gap-3 p-4 sm:grid-cols-2 xl:grid-cols-3 max-h-[65vh] overflow-y-auto">
      {rows.map((r) => {
        const sent = history.find((h) => h.studentId === r.studentId);
        const sentLocked = !!sent && (sent.status === "PENDING" || sent.status === "APPROVED");
        const attendanceStatus = attendanceByStudent[r.studentId];
        const isAbsentLocked = attendanceStatus === "ABSENT" || attendanceStatus === "EXCUSED";
        const locked = sentLocked || isAbsentLocked;
        const content = locked ? sent?.content ?? "" : r.content;
        const previous = previousByStudent[r.studentId] ?? [];
        const latestPrevious = previous[0];
        const previousSimilarity = latestPrevious && content.trim() ? commentSimilarity(content, latestPrevious.content) : 0;
        const aiWarnings = aiWarningsByStudent[r.studentId] ?? [];
        return (
          <div
            key={r.studentId}
            id={`daily-comment-card-${r.studentId}`}
            className={`rounded-xl border p-3 flex flex-col gap-2 bg-white shadow-soft ${
              highlightedStudentId === r.studentId
                ? "border-amber-400 ring-2 ring-amber-200"
                : isAbsentLocked
                  ? "border-red-200 bg-red-50/40"
                  : sentLocked
                    ? "border-emerald-200 bg-emerald-50/30"
                    : "border-slate-200"
            }`}
          >
            <div className="flex items-start justify-between gap-2">
              <div className="min-w-0">
                <StudentNameLink studentId={r.studentId} name={r.studentFullName} className="font-bold text-sm text-slate-900 hover:text-brand-red hover:underline text-left" />
                <div className="text-[10px] font-mono text-slate-400">{r.studentCode}</div>
              </div>
              <div className="flex flex-wrap justify-end gap-1 shrink-0">
                {aiAppliedStudentIds.has(r.studentId) && !locked && (
                  <span className="inline-flex items-center gap-0.5 text-[9px] font-bold uppercase bg-violet-50 text-violet-600 border border-violet-100 px-1.5 py-0.5 rounded">
                    <Sparkles className="w-3 h-3" />
                    {t("dailyCommentPanel.cardView.aiBadge")}
                  </span>
                )}
                {isAbsentLocked ? (
                  <span className="text-[9px] font-bold uppercase bg-red-100 text-red-600 px-1.5 py-0.5 rounded">
                    {t(`shared.attendanceStatus.${attendanceStatus}`, { defaultValue: attendanceStatus })}
                  </span>
                ) : sent ? (
                  <span className="text-[9px] font-bold uppercase bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded">
                    {t(`dailyCommentPanel.cardView.status.${sent.status}`)}
                  </span>
                ) : null}
              </div>
            </div>

            {isAbsentLocked && (
              <p className="text-[10px] font-bold text-red-600">{t("dailyCommentPanel.cardView.absentHint")}</p>
            )}

            {sent?.status === "REJECTED" && sent.rejectionReason && (
              <p className="text-[10px] font-bold text-rose-600">{t("dailyCommentPanel.rejectionReasonHint", { reason: sent.rejectionReason })}</p>
            )}

            <label className="text-[10px] font-bold uppercase text-slate-400">{t("dailyCommentPanel.columns.attitude")}</label>
            {locked ? (
              <div className="text-xs text-slate-700">{sent?.attitude ? t(`shared.attitudeWithPercent.${sent.attitude}`) : "—"}</div>
            ) : (
              <Select
                value={r.attitude}
                onChange={(e) => onUpdateRow(r.studentId, { attitude: e.target.value as DailyCommentCardRow["attitude"] })}
                className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
              >
                <option value="">{t("dailyCommentPanel.attitudePlaceholder")}</option>
                {ATTITUDES.map((value) => (
                  <option key={value} value={value}>
                    {t(`shared.attitudeWithPercent.${value}`)}
                  </option>
                ))}
              </Select>
            )}

            <label className="text-[10px] font-bold uppercase text-slate-400">{t("dailyCommentPanel.columns.studentComment")}</label>
            {locked ? (
              <div className="text-xs text-slate-700 whitespace-pre-wrap min-h-[3rem]">{content || "—"}</div>
            ) : (
              <textarea
                value={r.content}
                onChange={(e) => onUpdateRow(r.studentId, { content: e.target.value })}
                placeholder={t("dailyCommentPanel.contentPlaceholder")}
                rows={4}
                className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none resize-y"
              />
            )}

            {!locked && aiWarnings.length > 0 && (
              <ul className="space-y-0.5">
                {aiWarnings.map((w, i) => (
                  <li key={i} className="flex items-start gap-1 text-[10px] text-amber-700">
                    <AlertTriangle className="w-3 h-3 mt-0.5 shrink-0" />
                    {w.message}
                  </li>
                ))}
              </ul>
            )}

            <label className="text-[10px] font-bold uppercase text-slate-400">{t("dailyCommentPanel.columns.note")}</label>
            {locked ? (
              <div className="text-xs text-slate-700">{sent?.note || "—"}</div>
            ) : (
              <input
                value={r.note}
                onChange={(e) => onUpdateRow(r.studentId, { note: e.target.value })}
                className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
              />
            )}

            <div className="mt-auto border-t border-slate-100 pt-2">
              <div className="flex items-center justify-between gap-2 text-[10px] font-bold uppercase text-slate-400">
                <span className="flex items-center gap-1">
                  <History className="w-3 h-3" />
                  {t("dailyCommentPanel.cardView.previousComment")}
                </span>
                {previousSimilarity >= SIMILARITY_WARN_THRESHOLD && (
                  <span className="normal-case text-amber-600">
                    {t("dailyCommentPanel.cardView.similarToPrevious", { percent: Math.round(previousSimilarity * 100) })}
                  </span>
                )}
              </div>
              {latestPrevious ? (
                <p className="text-[11px] text-slate-500 mt-1 line-clamp-3" title={latestPrevious.content}>
                  <span className="font-semibold">{latestPrevious.commentDate}: </span>
                  {latestPrevious.content}
                </p>
              ) : (
                <p className="text-[11px] text-slate-400 italic mt-1">{t("dailyCommentPanel.cardView.noPreviousComment")}</p>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
