import React, { useEffect, useState } from "react";
import { ChevronDown, ChevronRight } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { ClassResponse, ClassSessionResponse, StudentCommentResponse, listClassEnrollments, listClassSessions, listCommentsForClass } from "../api";
import { useEligibleClasses } from "../hooks/useEligibleClasses";
import Badge from "@/components/ui/Badge";
import Card from "@/components/ui/Card";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import Select from "@/components/ui/Select";
import DatePicker from "@/components/ui/DatePicker";
import { formatDateTime, toLocaleTag } from "@/lib/i18nFormat";

// Đồng bộ đúng bố cục/tên cột với form Giáo viên điền & gửi (DailyCommentPanel.tsx) — bổ sung ngoài
// SDD gốc, đã xác nhận với người dùng 2026-08-06. Nhãn 2 kênh BTVN ăn theo "Loại giáo viên" của buổi
// (ClassSession.teacherType) — mirror đúng key shared.grammarChannel/shared.videoChannel (i18n) ở DailyCommentPanel.
type TeacherType = "VIETNAMESE" | "FOREIGN";

/**
 * UC-22 bổ sung (2026-07-31, theo yêu cầu người dùng): Quản lý điểm trường xem lại LỊCH SỬ nhận xét
 * đã quyết định (APPROVED/REJECTED), PHÂN THEO LỚP — trước đây "Nhận xét học viên" chỉ có hàng chờ
 * duyệt (listPendingComments), duyệt/từ chối xong là mất dấu, không tra cứu lại được.
 *
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) — trước đây gom bằng N×M request
 * (N lớp × M học sinh/lớp, GET /classes/{id}/comments?studentId= theo TỪNG học sinh) do BE chưa có
 * endpoint "toàn bộ nhận xét của 1 lớp". Giờ dùng listCommentsForClass(classId) — 1 request/LỚP (N
 * request, không còn nhân thêm số học sinh) — dùng useEligibleClasses() để lấy đúng các lớp Quản lý
 * điểm trường phụ trách (đã tự scope theo site), lọc còn APPROVED/REJECTED (DRAFT/PENDING không phải
 * "lịch sử", đã có ở tab Chờ duyệt), gom nhóm hiển thị theo lớp.
 *
 * "Ngày duyệt" chỉ có giá trị với nhận xét ĐÃ DUYỆT — BE chỉ set approvedAt ở nhánh APPROVED (xem
 * StudentCommentService.decideComments), REJECTED không có mốc thời gian quyết định nào được trả về
 * qua API nên hiện "—" (khác NGÀY BUỔI HỌC ở cột đầu — commentDate — luôn có với mọi dòng).
 */
export default function CommentHistoryPanel() {
  const { t, i18n } = useTranslation("academic-comments");
  const { classes, loading: loadingClasses } = useEligibleClasses();
  const [comments, setComments] = useState<StudentCommentResponse[]>([]);
  const [studentCodeByStudent, setStudentCodeByStudent] = useState<Record<number, string>>({});
  // "Loại giáo viên" của từng buổi (ClassSession.teacherType) — để đổi nhãn 2 kênh BTVN đúng như
  // DailyCommentPanel/CommentApprovalByClass (Ngữ pháp/Bài nghe, Từ Vựng (TKN)/Clip phản xạ), 2026-08-06.
  const [sessionsById, setSessionsById] = useState<Record<number, ClassSessionResponse>>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<"ALL" | "APPROVED" | "REJECTED">("ALL");
  const [classFilter, setClassFilter] = useState<number | "ALL">("ALL");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");

  useEffect(() => {
    if (classes.length === 0) {
      setComments([]);
      return;
    }
    setLoading(true);
    setError(null);
    Promise.all(
      classes.map((cls) =>
        Promise.all([listClassEnrollments(cls.id), listCommentsForClass(cls.id)])
          .then(([enrollments, allComments]) => {
            const active = enrollments.filter((en) => en.status === "ACTIVE");
            const activeIds = new Set(active.map((en) => en.studentId));
            return {
              comments: allComments.filter((c) => activeIds.has(c.studentId)),
              studentCodeByStudent: Object.fromEntries(active.map((en) => [en.studentId, en.studentCode]))
            };
          })
          .catch(() => ({ comments: [] as StudentCommentResponse[], studentCodeByStudent: {} as Record<number, string> }))
      )
    )
      .then((perClass) => {
        const allComments = perClass.flatMap((x) => x.comments).filter((c) => c.status === "APPROVED" || c.status === "REJECTED");
        const codeMap: Record<number, string> = {};
        perClass.forEach((x) => Object.assign(codeMap, x.studentCodeByStudent));
        setComments(allComments);
        setStudentCodeByStudent(codeMap);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("historyPanel.errors.loadFailed")))
      .finally(() => setLoading(false));
  }, [classes]);

  useEffect(() => {
    if (classes.length === 0) return;
    Promise.allSettled(classes.map((cls) => listClassSessions(cls.id)))
      .then((results) => {
        const map: Record<number, ClassSessionResponse> = {};
        results.forEach((r) => {
          if (r.status !== "fulfilled") return;
          r.value.forEach((s) => {
            map[s.id] = s;
          });
        });
        setSessionsById(map);
      })
      .catch(() => undefined);
  }, [classes]);

  const filtered = comments.filter((c) => {
    if (statusFilter !== "ALL" && c.status !== statusFilter) return false;
    if (classFilter !== "ALL" && c.classId !== classFilter) return false;
    if (dateFrom && c.commentDate < dateFrom) return false;
    if (dateTo && c.commentDate > dateTo) return false;
    return true;
  });

  const classesById: Record<number, ClassResponse> = Object.fromEntries(classes.map((c) => [c.id, c]));
  const classIdsInOrder = Array.from(new Set(filtered.map((c) => c.classId))).sort((a, b) =>
    (classesById[a]?.name ?? "").localeCompare(classesById[b]?.name ?? "")
  );

  const formatDecidedAt = (cm: StudentCommentResponse) => (cm.approvedAt ? formatDateTime(cm.approvedAt, i18n.language) : "—");

  if (loadingClasses) return <p className="text-xs text-slate-500 p-4">{t("historyPanel.loadingClasses")}</p>;
  if (classes.length === 0) return <p className="text-xs text-slate-400 italic text-center py-10">{t("historyPanel.noClasses")}</p>;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <Select
          value={classFilter}
          onChange={(e) => setClassFilter(e.target.value === "ALL" ? "ALL" : Number(e.target.value))}
          className="bg-white border border-slate-200 text-xs px-2.5 py-2 rounded-lg focus:outline-none"
        >
          <option value="ALL">{t("historyPanel.allClasses")}</option>
          {classes.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name} ({c.classCode})
            </option>
          ))}
        </Select>
        <Select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as typeof statusFilter)}
          className="bg-white border border-slate-200 text-xs px-2.5 py-2 rounded-lg focus:outline-none"
        >
          <option value="ALL">{t("historyPanel.allStatuses")}</option>
          <option value="APPROVED">{t("shared.status.APPROVED")}</option>
          <option value="REJECTED">{t("shared.status.REJECTED")}</option>
        </Select>
        <span className="text-[10px] uppercase font-bold text-slate-400">{t("historyPanel.fromDateLabel")}</span>
        <div className="w-36">
          <DatePicker value={dateFrom} onChange={setDateFrom} max={dateTo || undefined} />
        </div>
        <span className="text-xs text-slate-400">{t("historyPanel.toDateConnector")}</span>
        <div className="w-36">
          <DatePicker value={dateTo} onChange={setDateTo} min={dateFrom || undefined} />
        </div>
        {(dateFrom || dateTo || statusFilter !== "ALL" || classFilter !== "ALL") && (
          <button
            onClick={() => {
              setDateFrom("");
              setDateTo("");
              setStatusFilter("ALL");
              setClassFilter("ALL");
            }}
            className="text-[11px] font-semibold text-brand-red hover:underline"
          >
            {t("historyPanel.clearFilter")}
          </button>
        )}
        <span className="text-[11px] text-slate-400 ml-auto">
          {t("historyPanel.countSummary", { filtered: filtered.length, total: comments.length })}
        </span>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {loading ? (
        <p className="text-xs text-slate-500 p-4">{t("historyPanel.loading")}</p>
      ) : filtered.length === 0 ? (
        <p className="text-xs text-slate-400 italic text-center py-10">
          {comments.length === 0 ? t("historyPanel.emptyNoHistory") : t("historyPanel.emptyNoMatch")}
        </p>
      ) : (
        <div className="space-y-4">
          {classIdsInOrder.map((classId) => {
            const cls = classesById[classId];
            const classComments = filtered
              .filter((c) => c.classId === classId)
              .sort((a, b) => `${b.commentDate}T${b.approvedAt ?? ""}`.localeCompare(`${a.commentDate}T${a.approvedAt ?? ""}`));
            return (
              <Card key={classId} padded={false} className="overflow-hidden">
                <div className="px-5 py-3 border-b border-slate-100 bg-slate-50 flex items-center justify-between flex-wrap gap-2">
                  <span className="text-xs font-bold text-slate-700 font-display">
                    {cls ? `${cls.name} (${cls.classCode})` : t("historyPanel.classFallback", { id: classId })}
                  </span>
                  <Badge variant="neutral">{t("historyPanel.commentCountBadge", { count: classComments.length })}</Badge>
                </div>
                {/* Phân theo buổi (commentDate) trong từng lớp, dạng dropdown thu gọn — cùng cách nhóm
                    ở tab Chờ duyệt (CommentApprovalByClass) nhưng gập lại mặc định cho đỡ tốn diện
                    tích, bấm vào tiêu đề buổi mới xổ ra bảng chi tiết. */}
                {Array.from(new Set(classComments.map((c) => c.commentDate))).map((date) => (
                  <SessionGroup
                    key={date}
                    date={date}
                    items={classComments.filter((c) => c.commentDate === date)}
                    studentCodeByStudent={studentCodeByStudent}
                    sessionsById={sessionsById}
                    formatDecidedAt={formatDecidedAt}
                  />
                ))}
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}

/** 1 buổi học trong 1 lớp — gập/mở độc lập, mặc định gập để danh sách nhiều buổi không chiếm hết màn hình. */
function SessionGroup({
  date,
  items,
  studentCodeByStudent,
  sessionsById,
  formatDecidedAt
}: {
  date: string;
  items: StudentCommentResponse[];
  studentCodeByStudent: Record<number, string>;
  sessionsById: Record<number, ClassSessionResponse>;
  formatDecidedAt: (cm: StudentCommentResponse) => string;
}) {
  const { t, i18n } = useTranslation("academic-comments");
  const [expanded, setExpanded] = useState(false);
  const weekday = new Date(date).toLocaleDateString(toLocaleTag(i18n.language), { weekday: "long" }).replace(/^./, (c) => c.toUpperCase());
  // Cả buổi chung 1 Loại giáo viên (ClassSession.teacherType) — lấy từ dòng đầu, mirror CommentApprovalByClass.
  const sessionId = items[0]?.classSessionId;
  const sessionTeacherType = (sessionId != null ? sessionsById[sessionId]?.teacherType : null) ?? null;
  const grammarLabel = sessionTeacherType ? t(`shared.grammarChannel.${sessionTeacherType}`) : t("shared.grammarChannelFallback");
  const videoLabel = sessionTeacherType ? t(`shared.videoChannel.${sessionTeacherType}`) : t("shared.videoChannelFallback");

  return (
    <div className="border-b border-slate-100 last:border-b-0">
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        className="w-full px-5 py-2 bg-slate-50/60 hover:bg-slate-100/60 flex items-center gap-2 flex-wrap transition-colors"
      >
        {expanded ? <ChevronDown className="w-3.5 h-3.5 text-slate-400 shrink-0" /> : <ChevronRight className="w-3.5 h-3.5 text-slate-400 shrink-0" />}
        <span className="text-[11px] font-bold text-slate-600">{t("historyPanel.sessionLabel", { date, weekday })}</span>
        <span className="text-[10px] text-slate-400">{t("historyPanel.sessionItemCount", { count: items.length })}</span>
      </button>
      {expanded && (
        <TableContainer className="rounded-none border-0">
          <thead>
            {/* Border rõ giữa các cột/dòng header (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
                2026-08-06) — Th mặc định không có border. */}
            <tr className="border-b border-slate-300 [&>th]:text-center">
              <Th rowSpan={2} className="min-w-[110px] border-r border-slate-300">{t("historyPanel.columns.studentCode")}</Th>
              <Th rowSpan={2} className="border-r border-slate-300">{t("historyPanel.columns.fullName")}</Th>
              <Th rowSpan={2} className="border-r border-slate-300">{t("historyPanel.columns.type")}</Th>
              <Th rowSpan={2} className="border-r border-slate-300">{t("historyPanel.columns.dateOfBirth")}</Th>
              <Th colSpan={3} className="text-center border-r border-slate-300">{t("historyPanel.columns.homeworkPrevious")}</Th>
              <Th rowSpan={2} className="border-r border-slate-300">{t("historyPanel.columns.homeworkOffline")}</Th>
              <Th colSpan={2} className="text-center border-r border-slate-300">{t("historyPanel.columns.homeworkOnline")}</Th>
              <Th rowSpan={2} className="border-r border-slate-300">{t("historyPanel.columns.dueDate")}</Th>
              <Th rowSpan={2} className="border-r border-slate-300">{t("historyPanel.columns.attitude")}</Th>
              <Th rowSpan={2} className="min-w-[260px] border-r border-slate-300">{t("historyPanel.columns.comment")}</Th>
              <Th rowSpan={2} className="border-r border-slate-300">{t("historyPanel.columns.note")}</Th>
              <Th rowSpan={2} className="border-r border-slate-300">{t("historyPanel.columns.status")}</Th>
              <Th rowSpan={2} className="min-w-[140px] border-r border-slate-300">{t("historyPanel.columns.decidedAt")}</Th>
              <Th rowSpan={2} className="min-w-[180px]">{t("historyPanel.columns.rejectionReason")}</Th>
            </tr>
            <tr className="border-b border-slate-300 [&>th]:text-center">
              <Th className="border-r border-slate-300 text-center">{t("historyPanel.columns.offline")}</Th>
              <Th className="border-r border-slate-300 text-center">{grammarLabel}</Th>
              <Th className="border-r border-slate-300 text-center">{videoLabel}</Th>
              <Th className="border-r border-slate-300 text-center">{grammarLabel}</Th>
              <Th className="border-r border-slate-300 text-center">{videoLabel}</Th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-300">
            {items.map((cm) => (
              <tr key={cm.id} className="hover:bg-slate-50/40">
                <Td className="font-mono font-bold text-slate-500 border-r border-slate-300">{studentCodeByStudent[cm.studentId] ?? "—"}</Td>
                <Td className="font-bold text-slate-900 whitespace-nowrap border-r border-slate-300">{cm.studentFullName}</Td>
                <Td className="border-r border-slate-300">
                  <Badge variant="info">{t(`shared.commentType.${cm.commentType}`)}</Badge>
                </Td>
                <Td className="whitespace-nowrap text-slate-500 border-r border-slate-300">{cm.studentDateOfBirth ?? "—"}</Td>
                <Td className="min-w-[110px] border-r border-slate-300">{cm.homeworkPreviousOfflineText || "—"}</Td>
                <Td className="min-w-[130px] border-r border-slate-300">{cm.homeworkPreviousScore || "—"}</Td>
                <Td className="min-w-[130px] border-r border-slate-300">{cm.homeworkPreviousSpeakingScore || "—"}</Td>
                <Td className="min-w-[160px] border-r border-slate-300">{cm.homeworkNext || "—"}</Td>
                <Td className="min-w-[180px] border-r border-slate-300">{cm.homeworkNextExerciseTitle || "—"}</Td>
                <Td className="min-w-[180px] border-r border-slate-300">{cm.homeworkNextReviewVideoSetTitle || "—"}</Td>
                <Td className="min-w-[120px] whitespace-nowrap border-r border-slate-300">
                  {cm.homeworkNextDueAt ? new Date(cm.homeworkNextDueAt).toLocaleString(toLocaleTag(i18n.language), { dateStyle: "short", timeStyle: "short" }) : "—"}
                </Td>
                <Td className="min-w-[110px] border-r border-slate-300">{cm.attitude ? t(`shared.attitude.${cm.attitude}`) : "—"}</Td>
                <Td className="min-w-[260px] whitespace-pre-wrap border-r border-slate-300">{cm.content}</Td>
                <Td className="min-w-[120px] border-r border-slate-300">{cm.note || "—"}</Td>
                <Td className="border-r border-slate-300">
                  <Badge variant={cm.status === "APPROVED" ? "success" : "danger"}>
                    {cm.status === "APPROVED" ? t("shared.status.APPROVED") : t("shared.status.REJECTED")}
                  </Badge>
                </Td>
                <Td className="whitespace-nowrap border-r border-slate-300">{formatDecidedAt(cm)}</Td>
                <Td className="min-w-[180px] text-rose-600 border-r border-slate-300">{cm.rejectionReason || "—"}</Td>
              </tr>
            ))}
          </tbody>
        </TableContainer>
      )}
    </div>
  );
}
