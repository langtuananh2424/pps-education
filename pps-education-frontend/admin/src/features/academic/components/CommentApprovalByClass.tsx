import React, { useEffect, useState } from "react";
import { Edit3, Check, CheckCheck, Flag, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import {
  ClassResponse,
  ClassSessionResponse,
  StudentCommentResponse,
  decideComments,
  listClassEnrollments,
  listClassSessions,
  listClasses,
  updatePendingCommentContent
} from "../api";
import Badge from "@/components/ui/Badge";
import Card from "@/components/ui/Card";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import { useDialog } from "@/components/ui/DialogProvider";
import NotificationBanner from "@/features/student/components/NotificationBanner";
import { toLocaleTag } from "@/lib/i18nFormat";

// Đồng bộ đúng bố cục/tên cột với form Giáo viên điền & gửi (DailyCommentPanel.tsx) — bổ sung ngoài
// SDD gốc, đã xác nhận với người dùng 2026-08-06. Nhãn 2 kênh BTVN ăn theo "Loại giáo viên" của buổi
// (ClassSession.teacherType) — mirror đúng key shared.grammarChannel/shared.videoChannel (i18n) ở DailyCommentPanel.
type TeacherType = "VIETNAMESE" | "FOREIGN";

interface CommentApprovalByClassProps {
  items: StudentCommentResponse[];
  loading: boolean;
  onDecided: () => void;
}

/**
 * UC-22: hàng chờ duyệt nhóm theo lớp, hiển thị dạng bảng cùng bố cục cột với màn Giáo viên nhập (UC-21)
 * — thay cho danh sách tên rời rạc + panel chi tiết riêng trước đây (đã xác nhận với người dùng 2026-07-29:
 * hiển thị từng tên rời rạc không ổn khi số lượng nhiều). Duyệt/Từ chối làm trực tiếp ngay tại dòng.
 */
export default function CommentApprovalByClass({ items, loading, onDecided }: CommentApprovalByClassProps) {
  const { t, i18n } = useTranslation("academic-comments");
  const [classesById, setClassesById] = useState<Record<number, ClassResponse>>({});
  // "Loại giáo viên" của từng buổi (ClassSession.teacherType) — để đổi nhãn 2 kênh BTVN đúng như
  // DailyCommentPanel (Ngữ pháp/Bài nghe, Từ Vựng (TKN)/Clip phản xạ), 2026-08-06.
  const [sessionsById, setSessionsById] = useState<Record<number, ClassSessionResponse>>({});
  const [studentCodeByClassAndStudent, setStudentCodeByClassAndStudent] = useState<Record<string, string>>({});
  const [decidingId, setDecidingId] = useState<number | null>(null);
  const [decidingAllClassId, setDecidingAllClassId] = useState<number | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editingContent, setEditingContent] = useState("");
  const [savingEditId, setSavingEditId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [decidedMessage, setDecidedMessage] = useState<string | null>(null);
  const { promptDialog } = useDialog();

  useEffect(() => {
    listClasses()
      .then((classes) => setClassesById(Object.fromEntries(classes.map((c) => [c.id, c]))))
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    const classIds = Array.from(new Set(items.map((it) => it.classId)));
    if (classIds.length === 0) return;
    Promise.allSettled(classIds.map((classId) => listClassEnrollments(classId).then((enrollments) => ({ classId, enrollments }))))
      .then((results) => {
        const map: Record<string, string> = {};
        results.forEach((r) => {
          if (r.status !== "fulfilled") return;
          r.value.enrollments.forEach((en) => {
            map[`${r.value.classId}-${en.studentId}`] = en.studentCode;
          });
        });
        setStudentCodeByClassAndStudent((prev) => ({ ...prev, ...map }));
      })
      .catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [items.map((it) => it.classId).join(",")]);

  useEffect(() => {
    const classIds = Array.from(new Set(items.map((it) => it.classId)));
    if (classIds.length === 0) return;
    Promise.allSettled(classIds.map((classId) => listClassSessions(classId)))
      .then((results) => {
        const map: Record<number, ClassSessionResponse> = {};
        results.forEach((r) => {
          if (r.status !== "fulfilled") return;
          r.value.forEach((s) => {
            map[s.id] = s;
          });
        });
        setSessionsById((prev) => ({ ...prev, ...map }));
      })
      .catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [items.map((it) => it.classId).join(",")]);

  const handleDecide = async (comment: StudentCommentResponse, decision: "APPROVED" | "REJECTED") => {
    let reason: string | undefined;
    if (decision === "REJECTED") {
      reason = (await promptDialog(t("approvalByClass.rejectReasonPrompt"), { required: true, multiline: true })) ?? undefined;
      if (!reason?.trim()) return;
    }
    setDecidingId(comment.id);
    setError(null);
    try {
      await decideComments([comment.id], decision, reason?.trim());
      setDecidedMessage(
        decision === "APPROVED"
          ? t("approvalByClass.decidedApproved", { name: comment.studentFullName })
          : t("approvalByClass.decidedRejected", { name: comment.studentFullName })
      );
      onDecided();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("approvalByClass.errors.decideFailed"));
    } finally {
      setDecidingId(null);
    }
  };

  const handleDecideAllClass = async (classId: number, classItems: StudentCommentResponse[]) => {
    setDecidingAllClassId(classId);
    setError(null);
    try {
      await decideComments(classItems.map((cm) => cm.id), "APPROVED");
      setDecidedMessage(
        t("approvalByClass.decidedAll", {
          count: classItems.length,
          className: classesById[classId]?.name ?? `#${classId}`
        })
      );
      onDecided();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("approvalByClass.errors.decideAllFailed"));
    } finally {
      setDecidingAllClassId(null);
    }
  };

  const handleStartEdit = (cm: StudentCommentResponse) => {
    setEditingId(cm.id);
    setEditingContent(cm.content);
  };

  const handleSaveEdit = async (id: number) => {
    if (!editingContent.trim()) {
      setError(t("approvalByClass.errors.emptyContent"));
      return;
    }
    setSavingEditId(id);
    setError(null);
    try {
      await updatePendingCommentContent(id, { content: editingContent.trim() });
      setEditingId(null);
      onDecided();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("approvalByClass.errors.saveEditFailed"));
    } finally {
      setSavingEditId(null);
    }
  };

  if (loading) return <p className="text-xs text-slate-500 p-4">{t("approvalByClass.loading")}</p>;
  if (items.length === 0) return <p className="text-xs text-slate-400 italic text-center py-6">{t("approvalByClass.empty")}</p>;

  const classIdsInOrder = Array.from(new Set(items.map((it) => it.classId))).sort((a, b) => {
    const nameA = classesById[a]?.name ?? "";
    const nameB = classesById[b]?.name ?? "";
    return nameA.localeCompare(nameB);
  });

  return (
    <div className="space-y-4">
      <NotificationBanner message={decidedMessage} onClose={() => setDecidedMessage(null)} />
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
      {classIdsInOrder.map((classId) => {
        const cls = classesById[classId];
        const classItems = items.filter((it) => it.classId === classId);
        // Phân theo buổi (commentDate) trong từng lớp — nhiều yêu cầu chờ duyệt của cùng lớp thường
        // thuộc nhiều buổi khác nhau, gộp chung 1 bảng không biết dòng nào của ngày nào (đã xác nhận
        // với người dùng 2026-07-29).
        const datesInOrder = Array.from(new Set(classItems.map((it) => it.commentDate))).sort();
        return (
          <Card key={classId} padded={false} className="overflow-hidden">
            <div className="px-5 py-3 border-b border-slate-100 bg-slate-50 flex items-center justify-between flex-wrap gap-2">
              <span className="text-xs font-bold text-slate-700 font-display">
                {cls ? `${cls.name} (${cls.classCode})` : t("approvalByClass.classFallback", { id: classId })}
              </span>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => handleDecideAllClass(classId, classItems)}
                  disabled={decidingAllClassId === classId}
                  className="flex items-center gap-1.5 px-3 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white text-[11px] font-bold rounded-lg disabled:opacity-50"
                >
                  <CheckCheck className="w-3.5 h-3.5" />
                  {decidingAllClassId === classId ? t("approvalByClass.decidingAll") : t("approvalByClass.decideAllButton")}
                </button>
                <Badge variant="warning">{t("approvalByClass.pendingBadge", { count: classItems.length })}</Badge>
              </div>
            </div>
            {datesInOrder.map((date) => {
              const dateItems = classItems.filter((it) => it.commentDate === date);
              const weekday = new Date(date).toLocaleDateString(toLocaleTag(i18n.language), { weekday: "long" }).replace(/^./, (c) => c.toUpperCase());
              // Cả buổi chung 1 Loại giáo viên (ClassSession.teacherType) — lấy từ dòng đầu, mirror cách lessonContent đang lấy.
              const sessionId = dateItems[0]?.classSessionId;
              const sessionTeacherType = (sessionId != null ? sessionsById[sessionId]?.teacherType : null) ?? null;
              const grammarLabel = sessionTeacherType ? t(`shared.grammarChannel.${sessionTeacherType}`) : t("shared.grammarChannelFallback");
              const videoLabel = sessionTeacherType ? t(`shared.videoChannel.${sessionTeacherType}`) : t("shared.videoChannelFallback");
              // V130 — mirror DailyCommentPanel.tsx: buổi teacherType=VIETNAMESE tách "Offline" thành Reading/Writing
              // (header 3 cấp), nhãn Online đổi ngắn TV+NP/TKN (chỉ đổi nhãn, field/chức năng giữ nguyên).
              const isVietnamese = sessionTeacherType === "VIETNAMESE";
              const onlineGrammarLabel = isVietnamese ? t("dailyCommentPanel.columns.onlineGrammarShort") : grammarLabel;
              const onlineVideoLabel = isVietnamese ? t("dailyCommentPanel.columns.onlineVideoShort") : videoLabel;
              return (
                <div key={date} className="border-b border-slate-100 last:border-b-0">
                  <div className="px-5 py-2 bg-slate-50/60 flex items-center gap-2 flex-wrap">
                    <span className="text-[11px] font-bold text-slate-600">
                      {t("approvalByClass.sessionLabel", { date, weekday })}
                    </span>
                    <span className="text-[10px] text-slate-400">{t("approvalByClass.studentCount", { count: dateItems.length })}</span>
                    {/* lessonContent giống nhau cho cả buổi (class_sessions.lesson_content) — chỉ cần lấy từ dòng đầu, 2026-07-30. */}
                    {dateItems[0]?.lessonContent && (
                      <span className="text-[10px] text-amber-700 font-semibold">
                        {t("approvalByClass.lessonContentPrefix", { content: dateItems[0].lessonContent })}
                      </span>
                    )}
                  </div>
                  <TableContainer className="rounded-none border-0">
                    <thead>
                      {/* Border rõ giữa các cột/dòng header (bổ sung ngoài SDD gốc, đã xác nhận với
                          người dùng 2026-08-06) — Th mặc định không có border. */}
                      <tr className="border-b border-slate-300 [&>th]:text-center">
                        <Th rowSpan={isVietnamese ? 3 : 2} className="min-w-[110px] border-r border-slate-300">{t("approvalByClass.columns.studentCode")}</Th>
                        <Th rowSpan={isVietnamese ? 3 : 2} className="border-r border-slate-300">{t("approvalByClass.columns.fullName")}</Th>
                        <Th rowSpan={isVietnamese ? 3 : 2} className="border-r border-slate-300">{t("approvalByClass.columns.type")}</Th>
                        <Th rowSpan={isVietnamese ? 3 : 2} className="border-r border-slate-300">{t("approvalByClass.columns.dateOfBirth")}</Th>
                        <Th colSpan={isVietnamese ? 6 : 3} className="text-center border-r border-slate-300">{t("approvalByClass.columns.homeworkPrevious")}</Th>
                        <Th colSpan={isVietnamese ? 6 : 3} className="text-center border-r border-slate-300">{t("dailyCommentPanel.columns.homeworkNextGroup")}</Th>
                        <Th rowSpan={isVietnamese ? 3 : 2} className="border-r border-slate-300">{t("approvalByClass.columns.dueDate")}</Th>
                        <Th rowSpan={isVietnamese ? 3 : 2} className="border-r border-slate-300">{t("approvalByClass.columns.attitude")}</Th>
                        <Th rowSpan={isVietnamese ? 3 : 2} className="border-r border-slate-300">{t("approvalByClass.columns.studentComment")}</Th>
                        <Th rowSpan={isVietnamese ? 3 : 2} className="border-r border-slate-300">{t("approvalByClass.columns.note")}</Th>
                        <Th rowSpan={isVietnamese ? 3 : 2}>{t("approvalByClass.columns.action")}</Th>
                      </tr>
                      {isVietnamese ? (
                        <>
                          <tr className="border-b border-slate-300 [&>th]:text-center">
                            <Th colSpan={2} className="border-r border-slate-300 text-center">{t("approvalByClass.columns.offline")}</Th>
                            <Th colSpan={4} className="border-r border-slate-300 text-center">{t("dailyCommentPanel.columns.online")}</Th>
                            <Th colSpan={2} className="border-r border-slate-300 text-center">{t("approvalByClass.columns.offline")}</Th>
                            <Th colSpan={4} className="border-r border-slate-300 text-center">{t("dailyCommentPanel.columns.online")}</Th>
                          </tr>
                          <tr className="border-b border-slate-300 [&>th]:text-center">
                            <Th className="border-r border-slate-300 text-center">{t("dailyCommentPanel.columns.reading")}</Th>
                            <Th className="border-r border-slate-300 text-center">{t("dailyCommentPanel.columns.writing")}</Th>
                            <Th className="border-r border-slate-300 text-center">{t("dailyCommentPanel.columns.reading")}</Th>
                            <Th className="border-r border-slate-300 text-center">{t("dailyCommentPanel.columns.writing")}</Th>
                            <Th className="border-r border-slate-300 text-center">{onlineGrammarLabel}</Th>
                            <Th className="border-r border-slate-300 text-center">{onlineVideoLabel}</Th>
                            <Th className="border-r border-slate-300 text-center">{t("dailyCommentPanel.columns.reading")}</Th>
                            <Th className="border-r border-slate-300 text-center">{t("dailyCommentPanel.columns.writing")}</Th>
                            <Th className="border-r border-slate-300 text-center">{t("dailyCommentPanel.columns.reading")}</Th>
                            <Th className="border-r border-slate-300 text-center">{t("dailyCommentPanel.columns.writing")}</Th>
                            <Th className="border-r border-slate-300 text-center">{onlineGrammarLabel}</Th>
                            <Th className="border-r border-slate-300 text-center">{onlineVideoLabel}</Th>
                          </tr>
                        </>
                      ) : (
                        <tr className="border-b border-slate-300 [&>th]:text-center">
                          <Th className="border-r border-slate-300 text-center">{t("approvalByClass.columns.offline")}</Th>
                          <Th className="border-r border-slate-300 text-center">{grammarLabel}</Th>
                          <Th className="border-r border-slate-300 text-center">{videoLabel}</Th>
                          <Th className="border-r border-slate-300 text-center">{t("approvalByClass.columns.offline")}</Th>
                          <Th className="border-r border-slate-300 text-center">{grammarLabel}</Th>
                          <Th className="border-r border-slate-300 text-center">{videoLabel}</Th>
                        </tr>
                      )}
                    </thead>
                    <tbody className="divide-y divide-slate-300">
                      {dateItems.map((cm) => (
                        <tr key={cm.id} className="hover:bg-slate-50/40">
                          <Td className="font-mono font-bold text-slate-500 border-r border-slate-300">{studentCodeByClassAndStudent[`${cm.classId}-${cm.studentId}`] ?? "—"}</Td>
                          <Td className="font-bold text-slate-900 whitespace-nowrap border-r border-slate-300">
                            {cm.studentFullName}
                            {cm.isWarning && <Flag className="w-3 h-3 text-rose-500 inline ml-1.5" />}
                          </Td>
                          <Td className="border-r border-slate-300">
                            <Badge variant="info">{t(`shared.commentType.${cm.commentType}`)}</Badge>
                          </Td>
                          <Td className="whitespace-nowrap text-slate-500 border-r border-slate-300">{cm.studentDateOfBirth ?? "—"}</Td>
                          {isVietnamese ? (
                            <>
                              <Td className="min-w-[110px] border-r border-slate-300">{cm.homeworkPreviousReadingScore || "—"}</Td>
                              <Td className="min-w-[110px] border-r border-slate-300">{cm.homeworkPreviousWritingScore || "—"}</Td>
                            </>
                          ) : (
                            <Td className="min-w-[110px] border-r border-slate-300">{cm.homeworkPreviousOfflineText || "—"}</Td>
                          )}
                          {isVietnamese && (
                            <>
                              {/* V137 — "BTVN buổi trước - Online - Reading/Writing": chỉ hiện % TỰ ĐỘNG, mirror cột {onlineGrammarLabel}. */}
                              <Td className="min-w-[130px] border-r border-slate-300">{cm.readingPreviousProgress || "—"}</Td>
                              <Td className="min-w-[130px] border-r border-slate-300">{cm.writingPreviousProgress || "—"}</Td>
                            </>
                          )}
                          <Td className="min-w-[130px] border-r border-slate-300">{cm.homeworkPreviousScore || "—"}</Td>
                          <Td className="min-w-[130px] border-r border-slate-300">{cm.homeworkPreviousSpeakingScore || "—"}</Td>
                          {isVietnamese ? (
                            <>
                              <Td className="min-w-[140px] border-r border-slate-300">{cm.homeworkNextReading || "—"}</Td>
                              <Td className="min-w-[140px] border-r border-slate-300">{cm.homeworkNextWriting || "—"}</Td>
                              {/* V137 — "BTVN - Online - Reading/Writing" (giao buổi sau). */}
                              <Td className="min-w-[180px] border-r border-slate-300">{cm.homeworkNextReadingExerciseTitle || "—"}</Td>
                              <Td className="min-w-[180px] border-r border-slate-300">{cm.homeworkNextWritingExerciseTitle || "—"}</Td>
                            </>
                          ) : (
                            <Td className="min-w-[160px] border-r border-slate-300">{cm.homeworkNext || "—"}</Td>
                          )}
                          <Td className="min-w-[180px] border-r border-slate-300">{cm.homeworkNextExerciseTitle || "—"}</Td>
                          <Td className="min-w-[180px] border-r border-slate-300">{cm.homeworkNextReviewVideoSetTitle || "—"}</Td>
                          <Td className="min-w-[120px] whitespace-nowrap border-r border-slate-300">
                            {cm.homeworkNextDueAt ? new Date(cm.homeworkNextDueAt).toLocaleString(toLocaleTag(i18n.language), { dateStyle: "short", timeStyle: "short" }) : "—"}
                          </Td>
                          <Td className="min-w-[110px] border-r border-slate-300">{cm.attitude ? t(`shared.attitude.${cm.attitude}`) : "—"}</Td>
                          <Td className="min-w-[260px] border-r border-slate-300">
                            {editingId === cm.id ? (
                              <div className="space-y-2">
                                <textarea
                                  value={editingContent}
                                  onChange={(e) => setEditingContent(e.target.value)}
                                  rows={4}
                                  className="w-full bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none focus:ring-1 focus:ring-brand-red"
                                />
                                <div className="flex justify-end gap-1.5">
                                  <button
                                    onClick={() => setEditingId(null)}
                                    className="px-2 py-1 text-slate-500 hover:bg-slate-100 text-[10px] font-bold rounded-lg"
                                  >
                                    {t("approvalByClass.cancel")}
                                  </button>
                                  <button
                                    onClick={() => handleSaveEdit(cm.id)}
                                    disabled={savingEditId === cm.id}
                                    className="px-2 py-1 bg-brand-red hover:bg-red-700 text-white text-[10px] font-bold rounded-lg disabled:opacity-50"
                                  >
                                    {savingEditId === cm.id ? t("approvalByClass.savingEdit") : t("approvalByClass.saveEdit")}
                                  </button>
                                </div>
                              </div>
                            ) : (
                              <div className="whitespace-pre-wrap">{cm.content}</div>
                            )}
                          </Td>
                          <Td className="min-w-[120px] border-r border-slate-300">{cm.note || "—"}</Td>
                          <Td className="min-w-[230px] whitespace-nowrap border-r border-slate-300">
                            <div className="flex gap-1.5 flex-nowrap">
                              <button
                                onClick={() => handleStartEdit(cm)}
                                disabled={decidingId === cm.id || editingId === cm.id}
                                className="px-2 py-1 text-slate-600 hover:bg-slate-100 border border-slate-200 text-[11px] font-bold rounded-lg disabled:opacity-50"
                              >
                                <Edit3 className="w-3 h-3 inline mr-0.5" />
                                {t("approvalByClass.actionEdit")}
                              </button>
                              <button
                                onClick={() => handleDecide(cm, "REJECTED")}
                                disabled={decidingId === cm.id || editingId === cm.id}
                                className="px-2 py-1 text-rose-600 hover:bg-rose-50 border border-rose-200 text-[11px] font-bold rounded-lg disabled:opacity-50"
                              >
                                <X className="w-3 h-3 inline mr-0.5" />
                                {t("approvalByClass.actionReject")}
                              </button>
                              <button
                                onClick={() => handleDecide(cm, "APPROVED")}
                                disabled={decidingId === cm.id || editingId === cm.id}
                                className="px-2 py-1 bg-emerald-600 hover:bg-emerald-700 text-white text-[11px] font-bold rounded-lg disabled:opacity-50"
                              >
                                <Check className="w-3 h-3 inline mr-0.5" />
                                {decidingId === cm.id ? t("approvalByClass.deciding") : t("approvalByClass.actionApprove")}
                              </button>
                            </div>
                          </Td>
                        </tr>
                      ))}
                    </tbody>
                  </TableContainer>
                </div>
              );
            })}
          </Card>
        );
      })}
    </div>
  );
}
