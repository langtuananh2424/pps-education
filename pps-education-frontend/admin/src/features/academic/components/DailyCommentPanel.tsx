import React, { useEffect, useState } from "react";
import { Send, UserCheck } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import {
  ClassEnrollmentResponse,
  ClassResponse,
  ClassSessionResponse,
  StudentCommentResponse,
  listClassEnrollments,
  listClassTeachers,
  listClasses,
  listClassSessions,
  listComments,
  writeComment,
  submitComments
} from "../api";
import NotificationBanner from "@/features/student/components/NotificationBanner";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import CommentHistoryList from "./CommentHistoryList";

interface Row {
  studentId: number;
  studentFullName: string;
  studentCode: string;
  content: string;
}

/** UC-21 Main Flow (nhánh DAILY): viết nhận xét hàng ngày theo buổi học — cùng khuôn thao tác với Điểm danh nhanh. */
export default function DailyCommentPanel() {
  const { hasPermission, currentUser } = useApp();
  const canManage = hasPermission("academic.class.manage");
  const [classes, setClasses] = useState<ClassResponse[]>([]);
  const [selectedClassId, setSelectedClassId] = useState<number | null>(null);
  const [sessions, setSessions] = useState<ClassSessionResponse[]>([]);
  const [selectedSessionId, setSelectedSessionId] = useState<number | null>(null);
  const [rows, setRows] = useState<Row[]>([]);
  const [loadingRows, setLoadingRows] = useState(false);
  const [history, setHistory] = useState<StudentCommentResponse[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [sending, setSending] = useState(false);
  const [notification, setNotification] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const selectedClass = classes.find((c) => c.id === selectedClassId) ?? null;
  const selectedSession = sessions.find((s) => s.id === selectedSessionId) ?? null;
  /** UC-21 áp cùng nguyên tắc "đến giờ học mới nhận xét" như UC-15 (Điểm danh) — chặn chọn buổi tương lai. */
  const selectableSessions = sessions.filter((s) => new Date(`${s.sessionDate}T${s.startTime}`) <= new Date());

  /** UC-21 Precondition: GV chỉ nhận xét lớp mình được phân công dạy (class_teachers) — cùng gốc rễ với fix ở GradesPage/ClassesPage/AttendancePage. */
  useEffect(() => {
    listClasses()
      .then(async (res) => {
        if (canManage || !currentUser) {
          setClasses(res);
          return;
        }
        const teacherLists = await Promise.all(res.map((c) => listClassTeachers(c.id).catch(() => [])));
        setClasses(res.filter((_, i) => teacherLists[i].some((t) => t.teacherUserId === currentUser.id)));
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách lớp học."));
  }, [canManage, currentUser]);

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
        const active = enrollments.filter((en) => en.status === "ACTIVE");
        setRows(active.map((en) => ({ studentId: en.studentId, studentFullName: en.studentFullName, studentCode: en.studentCode, content: "" })));
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

  const pickClass = (id: number) => setSelectedClassId(id);

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
            isWarning: false
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
      setRows((prev) => prev.map((r) => (r.content.trim() ? { ...r, content: "" } : r)));
      await loadHistory(selectedClassId, selectedSession.id, rows.map((r) => r.studentId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Gửi nhận xét thất bại.");
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="space-y-4">
      <NotificationBanner message={notification} onClose={() => setNotification(null)} />
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-slate-50">
            <div>
              <span className="text-xs font-bold text-slate-700 font-display">Nhận xét hàng ngày theo buổi học (UC-21)</span>
              <p className="text-[10px] text-slate-400 mt-0.5">
                {selectedClass ? `${selectedClass.name} (${selectedClass.classCode})` : "Chọn lớp bên phải để bắt đầu."}
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

          <TableContainer className="rounded-none border-0">
            <thead>
              <tr>
                <Th>Mã ID</Th>
                <Th>Họ và tên</Th>
                <Th>Nhận xét của giáo viên</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {!selectedSessionId ? (
                <tr>
                  <td colSpan={3} className="px-6 py-12 text-center text-xs text-slate-400 italic">
                    {selectedClass ? "Chọn buổi học ở trên để tải danh sách học sinh." : "Chọn 1 lớp ở bảng bên phải."}
                  </td>
                </tr>
              ) : loadingRows ? (
                <tr>
                  <td colSpan={3} className="px-6 py-12 text-center text-xs text-slate-400">
                    Đang tải...
                  </td>
                </tr>
              ) : rows.length === 0 ? (
                <tr>
                  <td colSpan={3} className="px-6 py-12 text-center text-xs text-slate-400 italic">
                    Không tìm thấy học sinh nào thuộc lớp học này.
                  </td>
                </tr>
              ) : (
                rows.map((r) => (
                  <tr key={r.studentId} className="hover:bg-slate-50/40 transition-colors">
                    <Td className="font-mono font-bold text-slate-500">{r.studentCode}</Td>
                    <Td className="font-bold text-slate-900 whitespace-nowrap">{r.studentFullName}</Td>
                    <Td>
                      <textarea
                        value={r.content}
                        onChange={(e) => setRows((prev) => prev.map((row) => (row.studentId === r.studentId ? { ...row, content: e.target.value } : row)))}
                        placeholder="Viết nhận xét cho học sinh này..."
                        rows={2}
                        className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                      />
                    </Td>
                  </tr>
                ))
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

        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-soft space-y-4 self-start">
          <h3 className="text-xs font-bold text-slate-400 block uppercase tracking-wider font-display border-b border-slate-100 pb-2">Chọn lớp nhận xét</h3>
          <div className="space-y-2">
            {classes.map((cls) => (
              <button
                key={cls.id}
                onClick={() => pickClass(cls.id)}
                className={`w-full p-3 rounded-lg text-left text-xs font-semibold flex items-center justify-between transition-all border ${
                  selectedClassId === cls.id ? "bg-brand-orange border-brand-orange text-white shadow-sm" : "bg-slate-50 hover:bg-slate-100/60 border-slate-100 text-slate-600"
                }`}
              >
                <div>
                  <span className={`font-bold block ${selectedClassId === cls.id ? "text-white" : "text-slate-800"}`}>{cls.name}</span>
                  <span className={`text-[10px] block font-normal mt-0.5 ${selectedClassId === cls.id ? "text-white/80" : "text-slate-400"}`}>{cls.classCode} · {cls.siteName}</span>
                </div>
                <UserCheck className={`w-4 h-4 shrink-0 ${selectedClassId === cls.id ? "text-white" : "text-brand-orange"}`} />
              </button>
            ))}
            {classes.length === 0 && <p className="text-xs text-slate-400 italic">Chưa có lớp học nào.</p>}
          </div>
        </div>
      </div>
    </div>
  );
}
