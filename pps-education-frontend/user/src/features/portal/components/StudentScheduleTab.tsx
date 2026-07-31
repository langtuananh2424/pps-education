import React, { useEffect, useState } from "react";
import { AlertCircle, Calendar, CheckCircle2, FileSpreadsheet, XCircle } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { AttendanceMarkResponse, ClassSessionResponse, listMyAttendance, listMySessions } from "../api";

const teacherTypeLabels: Record<string, string> = { VIETNAMESE: "GV Việt Nam", FOREIGN: "GV nước ngoài" };

const ATTENDANCE_META: Record<string, { label: string; icon: React.ReactNode; bg: string }> = {
  PRESENT: { label: "Đi học", icon: <CheckCircle2 className="text-teal" size={18} />, bg: "bg-teal/5 border-teal/10" },
  LATE: { label: "Đi muộn", icon: <AlertCircle className="text-gold" size={18} />, bg: "bg-gold/5 border-gold/10" },
  ABSENT: { label: "Vắng mặt", icon: <XCircle className="text-coral" size={18} />, bg: "bg-coral/5 border-coral/10" },
  EXCUSED: { label: "Vắng có phép", icon: <AlertCircle className="text-muted" size={18} />, bg: "bg-slate-100 border-slate-200" }
};

interface StudentScheduleTabProps {
  classId: number;
}

/**
 * UC-59 + UC-64 (2026-07-29): Học sinh tự xem lịch học VÀ chuyên cần của chính mình — self-service,
 * khớp UI với ScheduleTab.tsx (dành cho Phụ huynh xem theo con+lớp cụ thể). Trước UC-64, tab này CHỈ
 * có lịch học (chưa có API tự xem điểm danh) — nay đã có GET /students/me/classes/{classId}/attendance,
 * bổ sung đủ phần "& Chuyên cần" như đúng tên tab.
 *
 * Lọc theo classId (lớp đang chọn ở sidebar "Lớp đang học") — GIỐNG mọi tab khác trên trang Portal
 * (Trang chủ/E-Learning/Khảo thí đều nhận classId). Trước đây gọi listMySessions() không kèm
 * classId nên gộp lịch của MỌI lớp học sinh ghi danh, gây lệch với Admin xem theo đúng 1 lớp cụ thể
 * (VD học sinh ghi danh 2 lớp, 1 lớp chưa xếp buổi học nào — Admin thấy đúng 0 buổi nhưng tab này lại
 * hiện lịch của lớp còn lại, ngỡ là lịch của lớp đang chọn) — đã xác nhận sửa với người dùng.
 */
export default function StudentScheduleTab({ classId }: StudentScheduleTabProps) {
  const [schedule, setSchedule] = useState<ClassSessionResponse[]>([]);
  const [attendance, setAttendance] = useState<AttendanceMarkResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    Promise.all([listMySessions(undefined, undefined, classId), listMyAttendance(classId)])
      .then(([sc, at]) => {
        setSchedule([...sc].sort((a, b) => `${a.sessionDate}T${a.startTime}`.localeCompare(`${b.sessionDate}T${b.startTime}`)));
        setAttendance(at);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được lịch học/chuyên cần."))
      .finally(() => setLoading(false));
  }, [classId]);

  const total = attendance.length;
  const present = attendance.filter((a) => a.status === "PRESENT").length;
  const late = attendance.filter((a) => a.status === "LATE").length;
  const absent = attendance.filter((a) => a.status === "ABSENT").length;
  const rate = total > 0 ? (((present + late * 0.5) / total) * 100).toFixed(0) : "0";

  if (loading) return <p className="text-sm text-muted font-bold">Đang tải...</p>;

  return (
    <div className="space-y-6">
      {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-6">
        <div className="bg-white border border-line/80 p-5 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-2">
          <span className="text-[10px] font-extrabold text-muted uppercase tracking-wider block">Tỷ lệ đi học</span>
          <p className="text-2xl font-extrabold text-teal">{rate}%</p>
          <div className="w-full bg-sky h-1.5 rounded-full overflow-hidden border border-line/40">
            <div className="bg-teal h-full" style={{ width: `${rate}%` }} />
          </div>
        </div>
        <div className="bg-white border border-line/80 p-5 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-1">
          <span className="text-[10px] font-extrabold text-muted uppercase tracking-wider block">Đúng giờ</span>
          <p className="text-2xl font-extrabold text-teal-deep">{present} buổi</p>
        </div>
        <div className="bg-white border border-line/80 p-5 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-1">
          <span className="text-[10px] font-extrabold text-muted uppercase tracking-wider block">Đi muộn</span>
          <p className="text-2xl font-extrabold text-gold">{late} buổi</p>
        </div>
        <div className="bg-white border border-line/80 p-5 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-1">
          <span className="text-[10px] font-extrabold text-muted uppercase tracking-wider block">Vắng mặt</span>
          <p className="text-2xl font-extrabold text-coral">{absent} buổi</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-4">
          <h2 className="text-xl font-extrabold text-ink flex items-center gap-2">
            <Calendar className="text-teal" /> Lịch buổi học
          </h2>
          <div className="space-y-4 max-h-[480px] overflow-y-auto pr-1">
            {schedule.map((s) => (
              <div
                key={s.id}
                className="border border-line/80 p-5 rounded-[20px] flex flex-col md:flex-row justify-between items-start md:items-center gap-4 bg-sky-2"
              >
                <div className="space-y-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="px-3 py-1 bg-teal border border-teal-deep/30 text-white text-xs font-extrabold rounded-full">
                      {new Date(s.sessionDate).toLocaleDateString("vi-VN", { weekday: "long" }).replace(/^./, (c) => c.toUpperCase())}
                    </span>
                    <span className="text-xs text-muted font-bold">
                      Buổi {s.sessionNumber} · {s.sessionDate} · {s.startTime}–{s.endTime}
                    </span>
                  </div>
                  <p className="text-xs text-muted font-bold">
                    Giáo viên: <span className="font-extrabold text-ink">{s.primaryTeacherName ?? "—"}</span>
                    {s.teacherType && <span className="text-[10px] text-teal-deep font-bold"> ({teacherTypeLabels[s.teacherType]})</span>}
                  </p>
                  {s.status !== "SCHEDULED" && (
                    <span className="text-[10px] font-extrabold text-coral uppercase">
                      {s.status === "CANCELLED" ? `Đã hủy${s.cancellationReason ? `: ${s.cancellationReason}` : ""}` : s.status}
                    </span>
                  )}
                </div>
                <div className="bg-white border border-line/80 px-4 py-2 rounded-xl text-center shadow-sm w-full md:w-auto shrink-0">
                  <span className="text-[10px] font-extrabold text-muted uppercase block">Phòng học</span>
                  <span className="text-sm font-extrabold text-teal-deep">{s.roomName ?? "—"}</span>
                </div>
              </div>
            ))}
            {schedule.length === 0 && <p className="text-xs text-muted font-bold italic">Chưa có buổi học nào.</p>}
          </div>
        </div>

        <div className="lg:col-span-1 bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] max-h-[560px] overflow-y-auto space-y-4">
          <h3 className="text-lg font-extrabold text-ink flex items-center gap-2">
            <FileSpreadsheet className="text-teal" /> Nhật ký chuyên cần
          </h3>
          <div className="space-y-4">
            {attendance.map((a) => {
              const meta = ATTENDANCE_META[a.status] ?? ATTENDANCE_META.PRESENT;
              return (
                <div key={a.id} className={`p-4 rounded-xl border ${meta.bg} shadow-sm space-y-2`}>
                  <div className="flex justify-between items-center">
                    <div className="flex items-center gap-1">
                      {meta.icon}
                      <span className="text-xs font-extrabold text-ink">{meta.label}</span>
                    </div>
                    {a.minutesLate ? <span className="text-[10px] text-muted font-bold">Muộn {a.minutesLate} phút</span> : null}
                  </div>
                  {a.absenceReason && <p className="text-[11px] italic text-muted font-bold border-t border-line/50 pt-1 mt-1">* Lý do: {a.absenceReason}</p>}
                </div>
              );
            })}
            {attendance.length === 0 && <p className="text-xs text-muted font-bold italic">Chưa có dữ liệu chuyên cần.</p>}
          </div>
        </div>
      </div>
    </div>
  );
}
