import React, { useEffect, useRef, useState } from "react";
import { Download, Send, UploadCloud } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { downloadBlob } from "@/lib/xlsxTemplate";
import { useApp } from "@/context/AppContext";
import {
  ClassEnrollmentResponse,
  ClassSessionResponse,
  DailyCommentImportResponse,
  StudentCommentResponse,
  downloadDailyCommentTemplate,
  importDailyComments,
  listClassEnrollments,
  listClassSessions,
  listComments,
  listTodaySessions,
  submitComments,
  updateComment,
  updateLessonContent,
  writeComment
} from "../api";
import { ExerciseAssignmentResponse, ReviewVideoSetResponse, listAssignmentsForClass, listReviewVideoSetsByClass } from "@/features/lms/api";
import { useEligibleClasses } from "../hooks/useEligibleClasses";
import NotificationBanner from "@/features/student/components/NotificationBanner";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import CommentHistoryList from "./CommentHistoryList";
import Select from "@/components/ui/Select";

const statusLabels: Record<StudentCommentResponse["status"], string> = { DRAFT: "Nháp", PENDING: "Chờ duyệt", APPROVED: "Đã duyệt", REJECTED: "Bị từ chối" };
const readOnlyFieldClass = "w-full bg-emerald-50/60 border border-emerald-200 text-xs p-2 rounded-lg text-slate-700 min-h-[34px]";

/** BTVN ngữ pháp buổi sau: OFFLINE (chữ tự do, homeworkNext) hoặc ONLINE (chọn 1 đề đã giao lớp, tự chấm ra %) — V55. */
type GrammarMode = "OFFLINE" | "ONLINE";

interface Row {
  studentId: number;
  studentFullName: string;
  studentCode: string;
  attitude: "" | NonNullable<StudentCommentResponse["attitude"]>;
  homeworkPreviousScore: string;
  homeworkPreviousSpeakingScore: string;
  content: string;
  grammarMode: GrammarMode;
  homeworkNext: string;
  homeworkNextExerciseAssignmentId: number | "";
  homeworkNextReviewVideoSetId: number | "";
  note: string;
}

const EMPTY_ROW_HOMEWORK: Pick<Row, "grammarMode" | "homeworkNext" | "homeworkNextExerciseAssignmentId" | "homeworkNextReviewVideoSetId"> = {
  grammarMode: "OFFLINE",
  homeworkNext: "",
  homeworkNextExerciseAssignmentId: "",
  homeworkNextReviewVideoSetId: ""
};

/** Dòng chưa có dữ liệu gì (kể cả từ Excel import) — an toàn để tự điền lại từ nhận xét DRAFT/REJECTED đã có mà không đè lên nội dung giáo viên đang gõ dở. */
const isRowBlank = (r: Row) =>
  !r.content.trim() &&
  !r.attitude &&
  !r.homeworkPreviousScore.trim() &&
  !r.homeworkPreviousSpeakingScore.trim() &&
  !r.homeworkNext.trim() &&
  r.homeworkNextExerciseAssignmentId === "" &&
  r.homeworkNextReviewVideoSetId === "" &&
  !r.note.trim();

const attitudeLabels: Record<NonNullable<StudentCommentResponse["attitude"]>, string> = {
  POOR: "Kém",
  WEAK: "Yếu",
  AVERAGE: "Trung bình",
  ABOVE_AVERAGE: "Trung bình khá",
  FAIR: "Khá",
  GOOD: "Tốt"
};

/** UC-21 Main Flow (nhánh DAILY): viết nhận xét hàng ngày theo buổi học — cùng khuôn thao tác với Điểm danh nhanh. */
export default function DailyCommentPanel() {
  const { selectedClassId } = useApp();
  const { classes } = useEligibleClasses();
  const [sessions, setSessions] = useState<ClassSessionResponse[]>([]);
  const [selectedSessionId, setSelectedSessionId] = useState<number | null>(null);
  const [rows, setRows] = useState<Row[]>([]);
  const [loadingRows, setLoadingRows] = useState(false);
  const [history, setHistory] = useState<StudentCommentResponse[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [sending, setSending] = useState(false);
  const [notification, setNotification] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [downloadingTemplate, setDownloadingTemplate] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState<DailyCommentImportResponse | null>(null);
  const [grammarOptions, setGrammarOptions] = useState<ExerciseAssignmentResponse[]>([]);
  const [videoOptions, setVideoOptions] = useState<ReviewVideoSetResponse[]>([]);
  const fileInputRef = useRef<HTMLInputElement>(null);
  // "Bài học hôm nay" (2026-07-29, chuyển từ Điểm danh sang đây) — bắt buộc điền trước khi Gửi nhận
  // xét DAILY (backend chặn 422 nếu trống), nên đặt ngay đầu màn hình để giáo viên điền trước tiên.
  const [lessonContentInput, setLessonContentInput] = useState("");
  const [savingLessonContent, setSavingLessonContent] = useState(false);

  const selectedClass = classes.find((c) => c.id === selectedClassId) ?? null;
  const selectedSession = sessions.find((s) => s.id === selectedSessionId) ?? null;
  /** UC-21 áp cùng nguyên tắc "đến giờ học mới nhận xét" như UC-15 (Điểm danh) — chặn chọn buổi tương lai. */
  const selectableSessions = sessions.filter((s) => new Date(`${s.sessionDate}T${s.startTime}`) <= new Date());

  useEffect(() => {
    setSelectedSessionId(null);
    setRows([]);
    setGrammarOptions([]);
    setVideoOptions([]);
    if (!selectedClassId) {
      setSessions([]);
      return;
    }
    listClassSessions(selectedClassId)
      .then(setSessions)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách buổi học."));
    // UC-48 (2026-07-29): tự chọn buổi hôm nay khi vào tab, đỡ GV phải tự tìm trong dropdown — chỉ tự
    // chọn nếu buổi đã tới giờ bắt đầu (đúng nguyên tắc "đến giờ học mới nhận xét" ở dưới), buổi hôm
    // nay chưa bắt đầu hoặc không có buổi nào thì báo rõ, để GV tự chọn buổi khác nếu cần.
    listTodaySessions(selectedClassId)
      .then((todaySessions) => {
        const started = todaySessions.find((s) => new Date(`${s.sessionDate}T${s.startTime}`) <= new Date());
        if (started) {
          setSelectedSessionId(started.id);
        } else if (todaySessions.length > 0) {
          setNotification(`📅 Buổi học hôm nay (${todaySessions[0].startTime}–${todaySessions[0].endTime}) chưa bắt đầu — chưa thể nhận xét, có thể tự chọn buổi khác ở trên.`);
          setTimeout(() => setNotification(null), 6000);
        } else {
          setNotification("📅 Hôm nay không có buổi học nào của lớp này — vui lòng tự chọn buổi ở trên.");
          setTimeout(() => setNotification(null), 6000);
        }
      })
      .catch(() => undefined);
    // BTVN ngữ pháp ONLINE chỉ chọn được từ đề ĐÃ giao lớp này (status=ACTIVE, API tự lọc sẵn);
    // BTVN Video Ôn tập chỉ chọn được bộ đã CÔNG BỐ (PUBLISHED) — khớp đúng điều kiện buildTemplate ở BE.
    listAssignmentsForClass(selectedClassId).then(setGrammarOptions).catch(() => undefined);
    listReviewVideoSetsByClass(selectedClassId)
      .then((sets) => setVideoOptions(sets.filter((s) => s.status === "PUBLISHED")))
      .catch(() => undefined);
  }, [selectedClassId]);

  useEffect(() => {
    setLessonContentInput(selectedSession?.lessonContent ?? "");
  }, [selectedSession?.id, selectedSession?.lessonContent]);

  useEffect(() => {
    if (!selectedClassId || !selectedSessionId) {
      setRows([]);
      setHistory([]);
      return;
    }
    setLoadingRows(true);
    setError(null);
    listClassEnrollments(selectedClassId)
      .then((enrollments: ClassEnrollmentResponse[]) => {
        // Sắp theo mã học viên — BE chưa có ORDER BY cố định ở đây (findBySchoolClassId), nên tự
        // sắp ở FE để bảng không đổi thứ tự lung tung mỗi lần tải lại (đã báo BE bổ sung ORDER BY
        // để file mẫu Excel tải về cũng khớp đúng thứ tự này).
        const active = enrollments
          .filter((en) => en.status === "ACTIVE")
          .sort((a, b) => a.studentCode.localeCompare(b.studentCode));
        setRows(
          active.map((en) => ({
            studentId: en.studentId,
            studentFullName: en.studentFullName,
            studentCode: en.studentCode,
            attitude: "",
            homeworkPreviousScore: "",
            homeworkPreviousSpeakingScore: "",
            content: "",
            ...EMPTY_ROW_HOMEWORK,
            note: ""
          }))
        );
        return loadHistory(selectedClassId, selectedSessionId, active.map((en) => en.studentId));
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách học sinh."))
      .finally(() => setLoadingRows(false));
  }, [selectedClassId, selectedSessionId]);

  const loadHistory = async (classId: number, sessionId: number, studentIds: number[]) => {
    setLoadingHistory(true);
    try {
      const results = await Promise.allSettled(studentIds.map((studentId) => listComments(classId, studentId)));
      const all = results.flatMap((r) => (r.status === "fulfilled" ? r.value : []));
      const filtered = all
        .filter((c) => c.commentType === "DAILY" && c.classSessionId === sessionId)
        .sort((a, b) => (a.studentFullName > b.studentFullName ? 1 : -1));
      setHistory(filtered);
      // Nhận xét DRAFT/REJECTED (nhập tay chưa gửi hoặc nhập từ Excel) — điền vào ô nhập trên màn hình để
      // giáo viên xem/sửa tiếp trước khi bấm "Gửi nhận xét", KHÔNG khoá read-only như PENDING/APPROVED.
      // Chỉ điền vào dòng còn trống để không đè lên nội dung đang gõ dở (đã xác nhận với người dùng 2026-07-29).
      setRows((prev) =>
        prev.map((r) => {
          const draft = filtered.find((h) => h.studentId === r.studentId && (h.status === "DRAFT" || h.status === "REJECTED"));
          if (!draft || !isRowBlank(r)) return r;
          return {
            ...r,
            attitude: draft.attitude ?? "",
            homeworkPreviousScore: draft.homeworkPreviousScore ?? "",
            homeworkPreviousSpeakingScore: draft.homeworkPreviousSpeakingScore ?? "",
            content: draft.content ?? "",
            grammarMode: draft.homeworkNextExerciseAssignmentId != null ? "ONLINE" : "OFFLINE",
            homeworkNext: draft.homeworkNext ?? "",
            homeworkNextExerciseAssignmentId: draft.homeworkNextExerciseAssignmentId ?? "",
            homeworkNextReviewVideoSetId: draft.homeworkNextReviewVideoSetId ?? "",
            note: draft.note ?? ""
          };
        })
      );
    } finally {
      setLoadingHistory(false);
    }
  };

  const handleSaveLessonContent = async () => {
    if (!selectedSessionId || !lessonContentInput.trim()) return;
    setSavingLessonContent(true);
    setError(null);
    try {
      const updated = await updateLessonContent(selectedSessionId, lessonContentInput.trim());
      setSessions((prev) => prev.map((s) => (s.id === selectedSessionId ? { ...s, lessonContent: updated.lessonContent } : s)));
      setNotification("✅ Đã lưu Bài học hôm nay.");
      setTimeout(() => setNotification(null), 4000);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Lưu Bài học hôm nay thất bại.");
    } finally {
      setSavingLessonContent(false);
    }
  };

  const handleSend = async () => {
    if (!selectedClassId || !selectedSession) return;
    const filled = rows.filter((r) => r.content.trim());
    if (filled.length === 0) {
      setError("Vui lòng nhập nhận xét cho ít nhất 1 học sinh.");
      return;
    }
    setSending(true);
    setError(null);
    try {
      const created = await Promise.allSettled(
        filled.map((r) => {
          const payload = {
            content: r.content.trim(),
            severity: "NORMAL" as const,
            isWarning: false,
            attitude: r.attitude || undefined,
            homeworkPreviousScore: r.homeworkPreviousScore.trim() || undefined,
            homeworkPreviousSpeakingScore: r.homeworkPreviousSpeakingScore.trim() || undefined,
            homeworkNext: r.grammarMode === "OFFLINE" ? r.homeworkNext.trim() || undefined : undefined,
            homeworkNextExerciseAssignmentId: r.grammarMode === "ONLINE" && r.homeworkNextExerciseAssignmentId !== "" ? r.homeworkNextExerciseAssignmentId : undefined,
            homeworkNextReviewVideoSetId: r.homeworkNextReviewVideoSetId !== "" ? r.homeworkNextReviewVideoSetId : undefined,
            note: r.note.trim() || undefined
          };
          // Dòng này đã có nhận xét DRAFT/REJECTED (gõ tay lưu dở hoặc nhập từ Excel) — SỬA bản ghi đã
          // có qua updateComment(), không tạo mới qua writeComment() (tránh sinh 2 bản ghi trùng cùng
          // 1 buổi+học sinh — đúng bug 500 đã gặp trước đây, backend hiện chưa tự chặn trùng ở writeComment()).
          const existing = history.find((h) => h.studentId === r.studentId && (h.status === "DRAFT" || h.status === "REJECTED"));
          return existing
            ? updateComment(existing.id, payload)
            : writeComment(selectedClassId, {
                studentId: r.studentId,
                commentType: "DAILY",
                classSessionId: selectedSession.id,
                commentDate: selectedSession.sessionDate,
                ...payload
              });
        })
      );
      const succeededIds = created.filter((r): r is PromiseFulfilledResult<Awaited<ReturnType<typeof writeComment>>> => r.status === "fulfilled").map((r) => r.value.id);
      const failedCount = created.length - succeededIds.length;

      // UC-21 (2026-07-29, BE PR #113 khôi phục DRAFT cho DAILY): writeComment() giờ chỉ lưu DRAFT —
      // phải gọi thêm submitComments() mới thật sự chuyển sang PENDING (chờ Quản lý điểm trường duyệt).
      // Nút "Gửi nhận xét" ở đây gộp cả 2 bước (ghi nháp + gửi duyệt) trong 1 lần bấm, không tách riêng
      // bước "Lưu nháp" — nếu bước gửi thất bại (hiếm), dữ liệu vẫn an toàn ở DRAFT, có thể "Nộp duyệt"
      // lại ở "Lịch sử nhận xét" bên dưới.
      let submitFailedMessage: string | null = null;
      if (succeededIds.length > 0) {
        try {
          await submitComments(selectedClassId, succeededIds);
        } catch (err) {
          submitFailedMessage = err instanceof ApiError ? err.message : "Gửi duyệt thất bại.";
        }
      }

      let message = submitFailedMessage
        ? `⚠️ Đã lưu nháp ${succeededIds.length} nhận xét nhưng gửi duyệt thất bại: ${submitFailedMessage} — vào "Lịch sử nhận xét" bên dưới bấm "Nộp duyệt" để thử lại.`
        : `🔔 Đã gửi nhận xét ${succeededIds.length} học sinh lên Quản lý điểm trường rà soát duyệt.`;
      if (failedCount > 0) message += `\n- ${failedCount} học sinh bị lỗi khi ghi nhận xét, thử lại sau.`;
      setNotification(message);
      setTimeout(() => setNotification(null), 6000);
      setRows((prev) =>
        prev.map((r) =>
          r.content.trim() ? { ...r, attitude: "", homeworkPreviousScore: "", homeworkPreviousSpeakingScore: "", content: "", ...EMPTY_ROW_HOMEWORK, note: "" } : r
        )
      );
      await loadHistory(selectedClassId, selectedSession.id, rows.map((r) => r.studentId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Gửi nhận xét thất bại.");
    } finally {
      setSending(false);
    }
  };

  /** UC-21 (2026-07-29): học sinh chỉ nhận xét DAILY được 1 lần/buổi — bấm vào dòng đã có nhận xét thì báo rõ thay vì im lặng khoá ô. */
  const notifyAlreadySent = (r: Row, sent: StudentCommentResponse) => {
    setNotification(`⚠️ Học sinh ${r.studentFullName} đã có nhận xét cho buổi này rồi (trạng thái: ${statusLabels[sent.status]}) — xem/sửa ở "Lịch sử nhận xét" bên dưới.`);
    setTimeout(() => setNotification(null), 6000);
  };

  const handleDownloadTemplate = async () => {
    if (!selectedSessionId) return;
    setDownloadingTemplate(true);
    setError(null);
    try {
      const blob = await downloadDailyCommentTemplate(selectedSessionId);
      downloadBlob(blob, `mau-nhan-xet-buoi-${selectedSessionId}.xlsx`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Tải file mẫu thất bại.");
    } finally {
      setDownloadingTemplate(false);
    }
  };

  const handleImportFile = async (file: File | null) => {
    if (!file || !selectedClassId || !selectedSessionId) return;
    if (!file.name.toLowerCase().endsWith(".xlsx")) {
      setError("Chỉ hỗ trợ file .xlsx.");
      return;
    }
    setImporting(true);
    setError(null);
    setImportResult(null);
    try {
      const res = await importDailyComments(selectedSessionId, file);
      setImportResult(res);
      if (res.successRows > 0) {
        const active = rows.map((r) => r.studentId);
        await loadHistory(selectedClassId, selectedSessionId, active);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Nhập từ Excel thất bại.");
    } finally {
      setImporting(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  return (
    <div className="space-y-4">
      <NotificationBanner message={notification} onClose={() => setNotification(null)} />
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-slate-50">
          <div>
            <span className="text-xs font-bold text-slate-700 font-display">Nhận xét hàng ngày theo buổi học</span>
            <p className="text-[10px] text-slate-400 mt-0.5">
              {selectedClass ? `${selectedClass.name} (${selectedClass.classCode})` : "Chưa chọn lớp — chọn ở góc trên bên phải (Header) để bắt đầu."}
            </p>
          </div>
          {selectedClass && (
            <Select
              value={selectedSessionId ?? ""}
              onChange={(e) => setSelectedSessionId(e.target.value ? Number(e.target.value) : null)}
              className="bg-white border text-[10px] font-bold text-slate-700 px-2 py-1 rounded focus:outline-none"
            >
              <option value="">-- Chọn buổi học --</option>
              {selectableSessions.map((s) => (
                <option key={s.id} value={s.id}>
                  Buổi {s.sessionNumber} — {s.sessionDate} ({s.startTime}–{s.endTime})
                </option>
              ))}
            </Select>
          )}
        </div>

        {selectedSessionId && (
          <div className="px-5 py-3 border-b border-slate-100 bg-amber-50/50 flex flex-wrap items-center gap-2">
            <label className="text-[11px] font-bold text-slate-600 shrink-0">Bài học hôm nay *</label>
            <input
              value={lessonContentInput}
              onChange={(e) => setLessonContentInput(e.target.value)}
              placeholder="VD: Unit 3 - Free time activities"
              className="flex-1 min-w-[220px] bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
            />
            <button
              type="button"
              onClick={handleSaveLessonContent}
              disabled={savingLessonContent || !lessonContentInput.trim() || lessonContentInput.trim() === (selectedSession?.lessonContent ?? "")}
              className="px-3 py-2 bg-slate-800 hover:bg-slate-900 text-white text-[11px] font-bold rounded-lg disabled:opacity-40"
            >
              {savingLessonContent ? "Đang lưu..." : "Lưu"}
            </button>
            {!selectedSession?.lessonContent && (
              <p className="w-full text-[10px] text-amber-700 italic">
                Chưa điền bài học hôm nay — bắt buộc điền trước khi Gửi nhận xét (buổi chưa điền sẽ bị từ chối khi gửi duyệt).
              </p>
            )}
          </div>
        )}

        {selectedSessionId && (
          <div className="px-5 py-3 border-b border-slate-100 bg-slate-50/60 flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={handleDownloadTemplate}
              disabled={downloadingTemplate}
              className="flex items-center gap-1.5 border border-dashed border-slate-300 rounded-lg px-3 py-2 text-[11px] font-semibold text-slate-600 hover:bg-white disabled:opacity-50"
            >
              <Download className="w-3.5 h-3.5" />
              {downloadingTemplate ? "Đang tải..." : "Tải mẫu Excel (điền sẵn điểm danh + nhận xét hiện có)"}
            </button>
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={importing}
              className="flex items-center gap-1.5 border-2 border-dashed border-slate-200 rounded-lg px-3 py-2 text-[11px] font-semibold text-slate-600 hover:border-brand-orange hover:bg-orange-50/30 disabled:opacity-50"
            >
              <UploadCloud className="w-3.5 h-3.5 text-brand-orange" />
              {importing ? "Đang nhập..." : "Nhập từ Excel (.xlsx)"}
            </button>
            <input ref={fileInputRef} type="file" accept=".xlsx" className="hidden" onChange={(e) => handleImportFile(e.target.files?.[0] ?? null)} />

            {importResult && (
              <div className="w-full flex flex-wrap items-center gap-2 text-[11px] mt-1">
                <span className="bg-slate-100 border border-slate-200 text-slate-700 font-semibold px-2 py-1 rounded-lg">
                  Tổng: {importResult.totalRows ?? "—"}
                </span>
                <span className="bg-emerald-50 border border-emerald-100 text-emerald-600 font-semibold px-2 py-1 rounded-lg">
                  Thành công: {importResult.successRows}
                </span>
                <span className="bg-rose-50 border border-rose-100 text-rose-600 font-semibold px-2 py-1 rounded-lg">
                  Lỗi: {importResult.failedRows}
                </span>
                {importResult.errorSummary.length > 0 && (
                  <div className="w-full border border-rose-100 rounded-lg overflow-hidden mt-1">
                    <div className="max-h-40 overflow-y-auto divide-y divide-slate-100">
                      {importResult.errorSummary.map((e, i) => (
                        <div key={i} className="px-3 py-1.5 flex gap-2 bg-white">
                          <span className="font-mono font-bold text-slate-400 shrink-0">Dòng {e.row}</span>
                          <span className="text-slate-600">{e.reason}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        <TableContainer className="rounded-none border-0">
          <thead>
            <tr>
              <Th className="min-w-[120px]">Mã ID</Th>
              <Th>Họ và tên</Th>
              <Th>Thái độ học tập</Th>
              <Th>BTVN Ngữ pháp buổi trước</Th>
              <Th>BTVN Nghe-nói buổi trước</Th>
              <Th>Nhận xét học sinh *</Th>
              <Th>BTVN Ngữ pháp buổi sau</Th>
              <Th>BTVN Video ôn tập buổi sau</Th>
              <Th>Ghi chú</Th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {!selectedSessionId ? (
              <tr>
                <td colSpan={9} className="px-6 py-12 text-center text-xs text-slate-400 italic">
                  {selectedClass ? "Chọn buổi học ở trên để tải danh sách học sinh." : "Chọn 1 lớp ở Header (góc trên bên phải)."}
                </td>
              </tr>
            ) : loadingRows ? (
              <tr>
                <td colSpan={9} className="px-6 py-12 text-center text-xs text-slate-400">
                  Đang tải...
                </td>
              </tr>
            ) : rows.length === 0 ? (
              <tr>
                <td colSpan={9} className="px-6 py-12 text-center text-xs text-slate-400 italic">
                  Không tìm thấy học sinh nào thuộc lớp học này.
                </td>
              </tr>
            ) : (
              rows.map((r) => {
                const updateRow = (patch: Partial<Row>) =>
                  setRows((prev) => prev.map((row) => (row.studentId === r.studentId ? { ...row, ...patch } : row)));
                // Học sinh này đã CÓ nhận xét DAILY cho đúng buổi đang chọn (gửi tay hoặc nhập Excel).
                // Chỉ khoá read-only khi đã PENDING/APPROVED (không còn sửa được ở đây nữa — backend chỉ
                // cho sửa khi DRAFT/REJECTED) — DRAFT/REJECTED vẫn hiện ô nhập bình thường (đã điền sẵn
                // dữ liệu ở loadHistory) để giáo viên xem/sửa tiếp trước khi bấm "Gửi nhận xét" (đã xác
                // nhận với người dùng 2026-07-29).
                const sent = history.find((h) => h.studentId === r.studentId);
                const locked = !!sent && (sent.status === "PENDING" || sent.status === "APPROVED");
                return (
                  <tr
                    key={r.studentId}
                    onClick={locked ? () => notifyAlreadySent(r, sent) : undefined}
                    className={`transition-colors ${locked ? "bg-emerald-50/20 cursor-pointer hover:bg-emerald-50/40" : "hover:bg-slate-50/40"}`}
                  >
                    <Td className="font-mono font-bold text-slate-500">{r.studentCode}</Td>
                    <Td className="font-bold text-slate-900 whitespace-nowrap">{r.studentFullName}</Td>
                    <Td className="min-w-[130px]">
                      {locked ? (
                        <div className={readOnlyFieldClass}>{sent!.attitude ? attitudeLabels[sent!.attitude!] : "—"}</div>
                      ) : (
                        <Select
                          value={r.attitude}
                          onChange={(e) => updateRow({ attitude: e.target.value as Row["attitude"] })}
                          className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                        >
                          <option value="">-- Chưa chọn --</option>
                          {Object.entries(attitudeLabels).map(([value, label]) => (
                            <option key={value} value={value}>
                              {label}
                            </option>
                          ))}
                        </Select>
                      )}
                    </Td>
                    <Td className="min-w-[150px]">
                      {locked ? (
                        <div className={readOnlyFieldClass}>{sent!.homeworkPreviousScore || "—"}</div>
                      ) : (
                        <input
                          value={r.homeworkPreviousScore}
                          onChange={(e) => updateRow({ homeworkPreviousScore: e.target.value })}
                          placeholder="VD: Đã thực hiện 90%"
                          className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                        />
                      )}
                    </Td>
                    <Td className="min-w-[150px]">
                      {locked ? (
                        <div className={readOnlyFieldClass}>{sent!.homeworkPreviousSpeakingScore || "—"}</div>
                      ) : (
                        <input
                          value={r.homeworkPreviousSpeakingScore}
                          onChange={(e) => updateRow({ homeworkPreviousSpeakingScore: e.target.value })}
                          placeholder="VD: Đã thực hiện 85%"
                          className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                        />
                      )}
                    </Td>
                    <Td className="min-w-[320px]">
                      {locked ? (
                        <div className={`${readOnlyFieldClass} whitespace-pre-wrap`}>{sent!.content}</div>
                      ) : (
                        <textarea
                          value={r.content}
                          onChange={(e) => updateRow({ content: e.target.value })}
                          placeholder="Viết nhận xét cho học sinh này..."
                          rows={2}
                          className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                        />
                      )}
                    </Td>
                    <Td className="min-w-[220px]">
                      {locked ? (
                        <div className={readOnlyFieldClass}>{sent!.homeworkNext || sent!.homeworkNextExerciseTitle || "—"}</div>
                      ) : (
                        <>
                          <div className="flex gap-1 mb-1.5">
                            <button
                              type="button"
                              onClick={() => updateRow({ grammarMode: "OFFLINE", homeworkNextExerciseAssignmentId: "" })}
                              className={`flex-1 text-[10px] font-bold py-1.5 rounded-lg border ${r.grammarMode === "OFFLINE" ? "bg-brand-orange border-brand-orange text-white" : "bg-slate-50 border-slate-200 text-slate-500"
                                }`}
                            >
                              Offline
                            </button>
                            <button
                              type="button"
                              onClick={() => updateRow({ grammarMode: "ONLINE", homeworkNext: "" })}
                              className={`flex-1 text-[10px] font-bold py-1.5 rounded-lg border ${r.grammarMode === "ONLINE" ? "bg-brand-orange border-brand-orange text-white" : "bg-slate-50 border-slate-200 text-slate-500"
                                }`}
                            >
                              Online
                            </button>
                          </div>
                          {r.grammarMode === "OFFLINE" ? (
                            <input
                              value={r.homeworkNext}
                              onChange={(e) => updateRow({ homeworkNext: e.target.value })}
                              placeholder="VD: Unit 2 trang 10"
                              className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                            />
                          ) : (
                            <Select
                              value={r.homeworkNextExerciseAssignmentId}
                              onChange={(e) => updateRow({ homeworkNextExerciseAssignmentId: e.target.value ? Number(e.target.value) : "" })}
                              className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                            >
                              <option value="">-- Chọn đề đã giao lớp --</option>
                              {grammarOptions.map((a) => (
                                <option key={a.id} value={a.id}>
                                  {a.exerciseTitle} ({a.exerciseCode})
                                </option>
                              ))}
                            </Select>
                          )}
                        </>
                      )}
                    </Td>
                    <Td className="min-w-[200px]">
                      {locked ? (
                        <div className={readOnlyFieldClass}>{sent!.homeworkNextReviewVideoSetTitle || "—"}</div>
                      ) : (
                        <Select
                          value={r.homeworkNextReviewVideoSetId}
                          onChange={(e) => updateRow({ homeworkNextReviewVideoSetId: e.target.value ? Number(e.target.value) : "" })}
                          className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                        >
                          <option value="">-- Không giao --</option>
                          {videoOptions.map((s) => (
                            <option key={s.id} value={s.id}>
                              {s.title} ({s.code})
                            </option>
                          ))}
                        </Select>
                      )}
                    </Td>
                    <Td className="min-w-[140px]">
                      {locked ? (
                        <div className={readOnlyFieldClass}>{sent!.note || "—"}</div>
                      ) : (
                        <input
                          value={r.note}
                          onChange={(e) => updateRow({ note: e.target.value })}
                          className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                        />
                      )}
                    </Td>
                  </tr>
                );
              })
            )}
          </tbody>
        </TableContainer>

        {selectedSessionId && rows.length > 0 && (
          <div className="px-6 py-4 bg-slate-50 border-t flex justify-end">
            {/* Không còn dòng nào có nội dung để gửi (VD cả lớp đã Gửi nhận xét xong, mọi dòng đều
                khoá/rỗng) — tự disable thay vì để bấm được rồi báo lỗi "chưa nhập gì" (2026-07-30). */}
            <button
              onClick={handleSend}
              disabled={sending || !rows.some((r) => r.content.trim())}
              className="bg-brand-orange hover:bg-brand-orange/90 text-white font-semibold text-xs px-4 py-2 rounded-lg flex items-center gap-1.5 shadow-soft transition-all disabled:opacity-50"
            >
              <Send className="w-4 h-4 text-white" />
              <span>{sending ? "Đang gửi..." : rows.some((r) => r.content.trim()) ? "Gửi nhận xét" : "Đã gửi hết nhận xét buổi này"}</span>
            </button>
          </div>
        )}

        {selectedClassId && selectedSessionId && (
          <div className="px-6 py-4 border-t border-slate-100 space-y-2">
            <span className="text-[10px] font-bold uppercase text-slate-500">Lịch sử nhận xét buổi này</span>
            {loadingHistory ? (
              <p className="text-xs text-slate-400">Đang tải...</p>
            ) : (
              <CommentHistoryList
                classId={selectedClassId}
                history={history}
                onChanged={() => loadHistory(selectedClassId, selectedSessionId, rows.map((r) => r.studentId))}
                layout="table"
                studentCodeById={Object.fromEntries(rows.map((r) => [r.studentId, r.studentCode]))}
              />
            )}
          </div>
        )}
      </div>
    </div>
  );
}
