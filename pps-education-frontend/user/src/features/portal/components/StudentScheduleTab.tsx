import React, { useEffect, useState } from "react";
import { Calendar } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { ClassSessionResponse, listMySessions } from "../api";

/** UC-59: Học sinh tự xem lịch học của chính mình — self-service, khác ScheduleTab.tsx (dành cho Phụ huynh xem theo con+lớp cụ thể, có gộp cả Attendance mà Học sinh chưa self-service được). */
export default function StudentScheduleTab() {
  const [schedule, setSchedule] = useState<ClassSessionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    listMySessions()
      .then((res) => setSchedule([...res].sort((a, b) => `${a.sessionDate}T${a.startTime}`.localeCompare(`${b.sessionDate}T${b.startTime}`))))
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được lịch học."))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p className="text-sm text-muted font-bold">Đang tải...</p>;

  return (
    <div className="space-y-6">
      {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

      <div className="bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-4">
        <h2 className="text-xl font-extrabold text-ink flex items-center gap-2">
          <Calendar className="text-teal" /> Lịch học của tôi
        </h2>
        <p className="text-xs text-muted font-bold">Tổng hợp mọi buổi học qua tất cả các lớp bạn đang ghi danh.</p>
        <div className="space-y-4 max-h-[560px] overflow-y-auto pr-1">
          {schedule.map((s) => (
            <div
              key={s.id}
              className="border border-line/80 p-5 rounded-[20px] flex flex-col md:flex-row justify-between items-start md:items-center gap-4 bg-sky-2"
            >
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <span className="px-3 py-1 bg-teal border border-teal-deep/30 text-white text-xs font-extrabold rounded-full">
                    {new Date(s.sessionDate).toLocaleDateString("vi-VN", { weekday: "long" }).replace(/^./, (c) => c.toUpperCase())}
                  </span>
                  <span className="text-xs text-muted font-bold">
                    {s.sessionDate} · {s.startTime}–{s.endTime}
                  </span>
                </div>
                <p className="text-xs text-muted font-bold">
                  Giáo viên: <span className="font-extrabold text-ink">{s.primaryTeacherName ?? "—"}</span>
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
    </div>
  );
}
