import React, { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Save, UserCheck } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import {
  AttendanceMarkResponse,
  ClassResponse,
  ClassSessionResponse,
  EnterAttendanceMarkRequest,
  getAttendanceSession,
  listClassEnrollments,
  listClassTeachers,
  listClasses,
  listClassSessions,
  markAttendance,
  submitAttendance
} from "@/features/academic/api";
import NotificationBanner from "../components/NotificationBanner";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";

type SimpleStatus = "PRESENT" | "ABSENT" | "LATE";

interface Row {
  studentId: number;
  studentFullName: string;
  studentCode: string;
  status: SimpleStatus;
}

function toSimpleStatus(status: EnterAttendanceMarkRequest["status"]): SimpleStatus {
  if (status === "PRESENT") return "PRESENT";
  if (status === "LATE" || status === "EARLY_LEAVE") return "LATE";
  return "ABSENT";
}

export default function AttendancePage() {
  const { hasPermission, currentUser } = useApp();
  // UC-15 Precondition: Tác nhân chỉ là "Giáo viên được phân công giảng dạy tiết đó" — không dùng
  // student.manage làm cờ bypass (quyền đó nghĩa thật là "Quản lý hồ sơ học sinh", backend cấp rộng
  // cho TEACHER, không liên quan phạm vi lớp điểm danh). Chỉ academic.class.manage (Admin/HEAD_ACADEMIC) mới thấy hết.
  const canSeeAllClasses = hasPermission("academic.class.manage");
  const [searchParams, setSearchParams] = useSearchParams();
  const classIdParam = searchParams.get("classId");
  const sessionIdParam = searchParams.get("sessionId");
  const selectedClassId = classIdParam ? Number(classIdParam) : null;
  const selectedSessionId = sessionIdParam ? Number(sessionIdParam) : null;

  const [classes, setClasses] = useState<ClassResponse[]>([]);
  const [sessions, setSessions] = useState<ClassSessionResponse[]>([]);
  const [rows, setRows] = useState<Row[]>([]);
  const [attendanceMode, setAttendanceMode] = useState<"SESSION_LEVEL" | "PERIOD_LEVEL">("SESSION_LEVEL");
  const [sessionStatus, setSessionStatus] = useState<string | null>(null);
  const [loadingRows, setLoadingRows] = useState(false);
  const [saving, setSaving] = useState(false);
  const [notification, setNotification] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const selectedClass = classes.find((c) => c.id === selectedClassId) ?? null;
  const locked = sessionStatus === "SUBMITTED" || sessionStatus === "LOCKED";

  /** UC-15 Precondition: GV chỉ điểm danh lớp mình được phân công dạy (class_teachers) — cùng gốc rễ với fix ở GradesPage/ClassesPage. */
  useEffect(() => {
    listClasses()
      .then(async (res) => {
        if (canSeeAllClasses || !currentUser) {
          setClasses(res);
          return;
        }
        const teacherLists = await Promise.all(res.map((c) => listClassTeachers(c.id).catch(() => [])));
        setClasses(res.filter((_, i) => teacherLists[i].some((t) => t.teacherUserId === currentUser.id)));
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách lớp học."));
  }, [canSeeAllClasses, currentUser]);

  useEffect(() => {
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
      setSessionStatus(null);
      return;
    }
    setLoadingRows(true);
    setError(null);
    getAttendanceSession(selectedSessionId)
      .then((session) => {
        setSessionStatus(session.status);
        setAttendanceMode(session.mode);
        setRows(session.marks.map((m: AttendanceMarkResponse) => ({ studentId: m.studentId, studentFullName: m.studentFullName, studentCode: m.studentCode, status: toSimpleStatus(m.status) })));
      })
      .catch((err) => {
        if (err instanceof ApiError && err.status === 404) {
          setSessionStatus(null);
          return listClassEnrollments(selectedClassId).then((enrollments) => {
            setRows(
              enrollments
                .filter((en) => en.status === "ACTIVE")
                .map((en) => ({ studentId: en.studentId, studentFullName: en.studentFullName, studentCode: en.studentCode, status: "PRESENT" as const }))
            );
          });
        }
        setError(err instanceof ApiError ? err.message : "Không tải được dữ liệu điểm danh.");
      })
      .finally(() => setLoadingRows(false));
  }, [selectedClassId, selectedSessionId]);

  const pickClass = (id: number) => setSearchParams({ classId: String(id) });
  const pickSession = (id: string) => {
    if (!id) {
      setSearchParams({ classId: String(selectedClassId) });
      return;
    }
    setSearchParams({ classId: String(selectedClassId), sessionId: id });
  };

  const handleSaveAttendance = async () => {
    if (!selectedSessionId || rows.length === 0) return;
    setSaving(true);
    setError(null);
    try {
      await markAttendance(selectedSessionId, {
        mode: attendanceMode,
        marks: rows.map((r) => ({ studentId: r.studentId, status: r.status }))
      });
      const result = await submitAttendance(selectedSessionId);
      setSessionStatus(result.status);

      const absentStudents = rows.filter((r) => r.status === "ABSENT");
      const lateStudents = rows.filter((r) => r.status === "LATE");
      let message = "🔔 BÁO CÁO CHUYÊN CẦN:\n- Hệ thống đã lưu điểm danh lớp học.\n";
      if (absentStudents.length > 0) {
        message += `- ĐÃ TỰ ĐỘNG GỬI thông báo khẩn qua SMS & Zalo tới phụ huynh học sinh vắng mặt: ${absentStudents.map((s) => s.studentFullName).join(", ")}.\n`;
      }
      if (lateStudents.length > 0) {
        message += `- Đã gửi tin nhắn đi muộn tới phụ huynh các em: ${lateStudents.map((s) => s.studentFullName).join(", ")}.\n`;
      }
      if (absentStudents.length === 0 && lateStudents.length === 0) {
        message = "✅ Điểm danh thành công! Toàn bộ học sinh trong lớp đã có mặt đầy đủ.";
      }
      setNotification(message);
      setTimeout(() => setNotification(null), 6000);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Lưu điểm danh thất bại.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Điểm Danh Chuyên Cần (SMS)</h1>
        <p className="text-xs text-slate-500 mt-1">Giảng viên lưu chuyên cần, vắng học tự động cảnh báo phụ huynh (UC-15).</p>
      </div>

      <NotificationBanner message={notification} onClose={() => setNotification(null)} />
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-slate-50">
            <div>
              <span className="text-xs font-bold text-slate-700 font-display">Điểm danh Chuyên cần đầu giờ (UC-15)</span>
              <p className="text-[10px] text-slate-400 mt-0.5">
                {selectedClass ? `${selectedClass.name} (${selectedClass.classCode})` : "Chọn lớp bên phải để bắt đầu điểm danh."}
              </p>
            </div>
            <div className="flex items-center gap-2">
              {selectedClass && (
                <select
                  value={selectedSessionId ?? ""}
                  onChange={(e) => pickSession(e.target.value)}
                  className="bg-white border text-[10px] font-bold text-slate-700 px-2 py-1 rounded focus:outline-none"
                >
                  <option value="">-- Chọn buổi học --</option>
                  {sessions.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.sessionDate} ({s.startTime}–{s.endTime})
                    </option>
                  ))}
                </select>
              )}
              <select
                value={attendanceMode}
                onChange={(e) => setAttendanceMode(e.target.value as "SESSION_LEVEL" | "PERIOD_LEVEL")}
                disabled={locked}
                className="bg-white border text-[10px] font-bold text-slate-700 px-2 py-1 rounded focus:outline-none disabled:opacity-50"
              >
                <option value="SESSION_LEVEL">Mức cả Buổi (SESSION)</option>
                <option value="PERIOD_LEVEL">Mức từng Tiết (PERIOD)</option>
              </select>
            </div>
          </div>

          {locked && (
            <div className="px-5 py-2.5 bg-emerald-50 border-b border-emerald-100 text-emerald-700 text-[11px] font-semibold">
              Điểm danh buổi này đã ở trạng thái {sessionStatus} — không sửa được nữa.
            </div>
          )}

          <TableContainer className="rounded-none border-0">
            <thead>
              <tr>
                <Th>Mã ID</Th>
                <Th>Họ và tên</Th>
                <Th className="text-center">Có mặt</Th>
                <Th className="text-center">Vắng mặt (ABSENT)</Th>
                <Th className="text-center">Đi trễ (LATE)</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {!selectedSessionId ? (
                <tr>
                  <td colSpan={5} className="px-6 py-12 text-center text-xs text-slate-400 italic">
                    {selectedClass ? "Chọn buổi học ở trên để tải danh sách học sinh." : "Chọn 1 lớp ở bảng bên phải."}
                  </td>
                </tr>
              ) : loadingRows ? (
                <tr>
                  <td colSpan={5} className="px-6 py-12 text-center text-xs text-slate-400">
                    Đang tải...
                  </td>
                </tr>
              ) : rows.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-6 py-12 text-center text-xs text-slate-400 italic">
                    Không tìm thấy học sinh nào thuộc lớp học này.
                  </td>
                </tr>
              ) : (
                rows.map((stud) => (
                  <tr key={stud.studentId} className="hover:bg-slate-50/40 transition-colors">
                    <Td className="font-mono font-bold text-slate-500">{stud.studentCode}</Td>
                    <Td className="font-bold text-slate-900">{stud.studentFullName}</Td>
                    {(["PRESENT", "ABSENT", "LATE"] as const).map((statusOption) => (
                      <Td key={statusOption} className="text-center">
                        <input
                          type="radio"
                          name={`att-${stud.studentId}`}
                          checked={stud.status === statusOption}
                          disabled={locked}
                          onChange={() => setRows((prev) => prev.map((r) => (r.studentId === stud.studentId ? { ...r, status: statusOption } : r)))}
                          className={`h-4 w-4 border-slate-300 disabled:opacity-50 ${
                            statusOption === "PRESENT"
                              ? "text-emerald-600 focus:ring-emerald-500"
                              : statusOption === "ABSENT"
                                ? "text-rose-600 focus:ring-rose-500"
                                : "text-amber-500 focus:ring-amber-500"
                          }`}
                        />
                      </Td>
                    ))}
                  </tr>
                ))
              )}
            </tbody>
          </TableContainer>

          {!locked && (
            <div className="px-6 py-4 bg-slate-50 border-t flex justify-end">
              <button
                onClick={handleSaveAttendance}
                disabled={!selectedSessionId || rows.length === 0 || saving}
                className="bg-brand-orange hover:bg-brand-orange/90 text-white font-semibold text-xs px-4 py-2 rounded-lg flex items-center gap-1.5 shadow-soft transition-all disabled:opacity-50"
              >
                <Save className="w-4 h-4 text-white" />
                <span>{saving ? "Đang lưu..." : "Xác nhận & Lưu điểm danh (UC-15)"}</span>
              </button>
            </div>
          )}
        </div>

        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-soft space-y-4 self-start">
          <h3 className="text-xs font-bold text-slate-400 block uppercase tracking-wider font-display border-b border-slate-100 pb-2">Chọn lớp điểm danh</h3>
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
