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
  writeComment,
  submitComments
} from "../api";
import { useEligibleClasses } from "../hooks/useEligibleClasses";
import NotificationBanner from "@/features/student/components/NotificationBanner";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import CommentHistoryList from "./CommentHistoryList";

interface Row {
  studentId: number;
  studentFullName: string;
  studentCode: string;
  attitude: "" | NonNullable<StudentCommentResponse["attitude"]>;
  homeworkPreviousScore: string;
  content: string;
  homeworkNext: string;
  note: string;
}

const attitudeLabels: Record<NonNullable<StudentCommentResponse["attitude"]>, string> = {
  POOR: "Kém",
  AVERAGE: "Trung bình",
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
  const fileInputRef = useRef<HTMLInputElement>(null);

  const selectedClass = classes.find((c) => c.id === selectedClassId) ?? null;
  const selectedSession = sessions.find((s) => s.id === selectedSessionId) ?? null;
  /** UC-21 áp cùng nguyên tắc "đến giờ học mới nhận xét" như UC-15 (Điểm danh) — chặn chọn buổi tương lai. */
  const selectableSessions = sessions.filter((s) => new Date(`${s.sessionDate}T${s.startTime}`) <= new Date());

  useEffect(() => {
    setSelectedSessionId(null);
    setRows([]);
    if (!selectedClassId) {
      setSessions([]);
      return;
    }
    listClassSessions(selectedClassId)
      .then(setSessions)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách buổi học."));
  }, [selectedClassId]);

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
            content: "",
            homeworkNext: "",
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
      setHistory(
        all
          .filter((c) => c.commentType === "DAILY" && c.classSessionId === sessionId)
          .sort((a, b) => (a.studentFullName > b.studentFullName ? 1 : -1))
      );
    } finally {
      setLoadingHistory(false);
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
        filled.map((r) =>
          writeComment(selectedClassId, {
            studentId: r.studentId,
            commentType: "DAILY",
            classSessionId: selectedSession.id,
            commentDate: selectedSession.sessionDate,
            content: r.content.trim(),
            severity: "NORMAL",
            isWarning: false,
            attitude: r.attitude || undefined,
            homeworkPreviousScore: r.homeworkPreviousScore.trim() || undefined,
            homeworkNext: r.homeworkNext.trim() || undefined,
            note: r.note.trim() || undefined
          })
        )
      );
      const succeededIds = created.filter((r): r is PromiseFulfilledResult<Awaited<ReturnType<typeof writeComment>>> => r.status === "fulfilled").map((r) => r.value.id);
      const failedCount = created.length - succeededIds.length;

      if (succeededIds.length > 0) {
        await submitComments(selectedClassId, succeededIds);
      }

      let message = `🔔 Đã gửi nhận xét ${succeededIds.length} học sinh lên Quản lý điểm trường rà soát duyệt.`;
      if (failedCount > 0) message += `\n- ${failedCount} học sinh bị lỗi khi ghi nhận xét, thử lại sau.`;
      setNotification(message);
      setTimeout(() => setNotification(null), 6000);
      setRows((prev) =>
        prev.map((r) => (r.content.trim() ? { ...r, attitude: "", homeworkPreviousScore: "", content: "", homeworkNext: "", note: "" } : r))
      );
      await loadHistory(selectedClassId, selectedSession.id, rows.map((r) => r.studentId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Gửi nhận xét thất bại.");
    } finally {
      setSending(false);
    }
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
              <span className="text-xs font-bold text-slate-700 font-display">Nhận xét hàng ngày theo buổi học (UC-21)</span>
              <p className="text-[10px] text-slate-400 mt-0.5">
                {selectedClass ? `${selectedClass.name} (${selectedClass.classCode})` : "Chưa chọn lớp — chọn ở góc trên bên phải (Header) để bắt đầu."}
              </p>
            </div>
            {selectedClass && (
              <select
                value={selectedSessionId ?? ""}
                onChange={(e) => setSelectedSessionId(e.target.value ? Number(e.target.value) : null)}
                className="bg-white border text-[10px] font-bold text-slate-700 px-2 py-1 rounded focus:outline-none"
              >
                <option value="">-- Chọn buổi học --</option>
                {selectableSessions.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.sessionDate} ({s.startTime}–{s.endTime})
                  </option>
                ))}
              </select>
            )}
          </div>

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
                <Th>Mã ID</Th>
                <Th>Họ và tên</Th>
                <Th>Thái độ học tập</Th>
                <Th>BTVN buổi trước</Th>
                <Th>Nhận xét học sinh *</Th>
                <Th>BTVN buổi sau</Th>
                <Th>Ghi chú</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {!selectedSessionId ? (
                <tr>
                  <td colSpan={7} className="px-6 py-12 text-center text-xs text-slate-400 italic">
                    {selectedClass ? "Chọn buổi học ở trên để tải danh sách học sinh." : "Chọn 1 lớp ở Header (góc trên bên phải)."}
                  </td>
                </tr>
              ) : loadingRows ? (
                <tr>
                  <td colSpan={7} className="px-6 py-12 text-center text-xs text-slate-400">
                    Đang tải...
                  </td>
                </tr>
              ) : rows.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-6 py-12 text-center text-xs text-slate-400 italic">
                    Không tìm thấy học sinh nào thuộc lớp học này.
                  </td>
                </tr>
              ) : (
                rows.map((r) => {
                  const updateRow = (patch: Partial<Row>) =>
                    setRows((prev) => prev.map((row) => (row.studentId === r.studentId ? { ...row, ...patch } : row)));
                  return (
                    <tr key={r.studentId} className="hover:bg-slate-50/40 transition-colors">
                      <Td className="font-mono font-bold text-slate-500">{r.studentCode}</Td>
                      <Td className="font-bold text-slate-900 whitespace-nowrap">{r.studentFullName}</Td>
                      <Td>
                        <select
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
                        </select>
                      </Td>
                      <Td>
                        <input
                          value={r.homeworkPreviousScore}
                          onChange={(e) => updateRow({ homeworkPreviousScore: e.target.value })}
                          placeholder="VD: Đã làm đủ"
                          className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                        />
                      </Td>
                      <Td>
                        <textarea
                          value={r.content}
                          onChange={(e) => updateRow({ content: e.target.value })}
                          placeholder="Viết nhận xét cho học sinh này..."
                          rows={2}
                          className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                        />
                      </Td>
                      <Td>
                        <input
                          value={r.homeworkNext}
                          onChange={(e) => updateRow({ homeworkNext: e.target.value })}
                          placeholder="VD: Unit 2 trang 10"
                          className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                        />
                      </Td>
                      <Td>
                        <input
                          value={r.note}
                          onChange={(e) => updateRow({ note: e.target.value })}
                          className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                        />
                      </Td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </TableContainer>

          {selectedSessionId && rows.length > 0 && (
            <div className="px-6 py-4 bg-slate-50 border-t flex justify-end">
              <button
                onClick={handleSend}
                disabled={sending}
                className="bg-brand-orange hover:bg-brand-orange/90 text-white font-semibold text-xs px-4 py-2 rounded-lg flex items-center gap-1.5 shadow-soft transition-all disabled:opacity-50"
              >
                <Send className="w-4 h-4 text-white" />
                <span>{sending ? "Đang gửi..." : "Gửi nhận xét (UC-21)"}</span>
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
                  showStudentName
                />
              )}
            </div>
          )}
        </div>
    </div>
  );
}
