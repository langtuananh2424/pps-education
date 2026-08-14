import React, { useEffect, useState } from "react";
import { AlertCircle, Calendar, CheckCircle2, FileSpreadsheet, RotateCcw, XCircle } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { formatHm } from "@/lib/format";
import { AttendanceMarkResponse, ClassSessionResponse, listMyAttendance, listMySessions } from "../api";
import ScheduleFilterBar from "./ScheduleFilterBar";
import { inDateRange, useScheduleDateFilter } from "../hooks/useScheduleDateFilter";

const teacherTypeLabels: Record<string, string> = { VIETNAMESE: "GV Việt Nam", FOREIGN: "GV nước ngoài" };

const ATTENDANCE_META: Record<string, { label: string; icon: React.ReactNode; bg: string }> = {
  PRESENT: { label: "Đi học", icon: <CheckCircle2 className="text-teal" size={18} />, bg: "bg-teal/5 border-teal/10" },
  LATE: { label: "Đi muộn", icon: <AlertCircle className="text-gold" size={18} />, bg: "bg-gold/5 border-gold/10" },
  ABSENT: { label: "Vắng mặt", icon: <XCircle className="text-coral" size={18} />, bg: "bg-coral/5 border-coral/10" },
  EXCUSED: { label: "Vắng có phép", icon: <AlertCircle className="text-muted" size={18} />, bg: "bg-slate-100 border-slate-200" }
};

/**
 * BE chỉ tự set CANCELLED/RESCHEDULED (xem ClassSessionService) — KHÔNG tự chuyển SCHEDULED ->
 * COMPLETED khi buổi đã qua giờ. Vì vậy phải tự tính "Đã học/Đang diễn ra/Sắp diễn ra" ở FE bằng
 * cách so ngày giờ buổi với thời điểm hiện tại, để phân biệt trực quan buổi đã qua và buổi sắp tới
 * (2026-07-31, theo yêu cầu người dùng — trước đây buổi đã học và sắp học hiện giống hệt nhau).
 */
function getSessionStatusBadge(s: ClassSessionResponse): { label: string; icon: React.ReactNode; className: string } {
  if (s.status === "CANCELLED") {
    return { label: "Đã hủy", icon: <XCircle size={12} />, className: "bg-coral/10 text-coral border-coral/20" };
  }
  if (s.status === "RESCHEDULED") {
    return { label: "Đã dời lịch", icon: <AlertCircle size={12} />, className: "bg-slate-100 text-muted border-slate-200" };
  }
  const now = new Date();
  const start = new Date(`${s.sessionDate}T${s.startTime}`);
  const end = new Date(`${s.sessionDate}T${s.endTime}`);
  if (now > end) {
    return { label: "Đã học", icon: <CheckCircle2 size={12} />, className: "bg-teal/10 text-teal-deep border-teal/20" };
  }
  if (now >= start && now <= end) {
    return { label: "Đang diễn ra", icon: <AlertCircle size={12} />, className: "bg-gold/10 text-gold border-gold/20" };
  }
  return { label: "Sắp diễn ra", icon: <Calendar size={12} />, className: "bg-sky text-teal-deep border-teal/20" };
}

/**
 * Đẩy buổi sắp diễn ra gần nhất lên đầu danh sách (theo yêu cầu người dùng, 2026-07-31) — nhóm
 * buổi chưa kết thúc (kể cả đang diễn ra) xếp trước, gần nhất trước; nhóm buổi đã qua xếp sau,
 * mới qua nhất trước, để vẫn xem được lịch sử ngay bên dưới thay vì phải cuộn hết danh sách.
 */
function sortSessionsForDisplay(sessions: ClassSessionResponse[]): ClassSessionResponse[] {
  const now = Date.now();
  return [...sessions]
    .map((s) => ({ s, end: new Date(`${s.sessionDate}T${s.endTime}`).getTime() }))
    .sort((a, b) => {
      const aUpcoming = a.end >= now;
      const bUpcoming = b.end >= now;
      if (aUpcoming !== bUpcoming) return aUpcoming ? -1 : 1;
      return aUpcoming ? a.end - b.end : b.end - a.end;
    })
    .map((x) => x.s);
}

interface StudentScheduleTabProps {
  classId: number;
  /** Site của lớp đang chọn — dùng để nạp danh sách học kỳ (GET /academic-terms?siteId=...) cho bộ lọc. */
  siteId: number | null;
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
export default function StudentScheduleTab({ classId, siteId }: StudentScheduleTabProps) {
  const [schedule, setSchedule] = useState<ClassSessionResponse[]>([]);
  const [attendance, setAttendance] = useState<AttendanceMarkResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const dateFilter = useScheduleDateFilter(siteId);

  useEffect(() => {
    setLoading(true);
    setError(null);
    Promise.all([listMySessions(undefined, undefined, classId), listMyAttendance(classId)])
      .then(([sc, at]) => {
        setSchedule(sortSessionsForDisplay(sc));
        setAttendance(at);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được lịch học/chuyên cần."))
      .finally(() => setLoading(false));
  }, [classId]);

  // Tra ngược classSessionId -> buổi học cụ thể (ngày/giờ/số buổi) để hiện rõ trong "Nhật ký chuyên
  // cần" — trước đây chỉ hiện mỗi trạng thái (Đi học/Vắng mặt), không rõ của buổi nào (2026-07-31).
  // Dựng từ schedule ĐẦY ĐỦ (chưa lọc) để luôn tra được đúng ngày buổi học của mọi điểm danh.
  const sessionById = new Map(schedule.map((s) => [s.id, s]));

  // Bổ sung 2026-08-12: lọc theo khoảng thời gian/học kỳ (client-side, dữ liệu đã tải hết 1 lần —
  // xem useScheduleDateFilter.ts). schedule/attendance đã lọc chỉ dùng để hiển thị + tính 4 card,
  // KHÔNG dùng để tra sessionById (giữ nguyên bản đầy đủ ở trên).
  const filteredSchedule = schedule.filter((s) => inDateRange(s.sessionDate, dateFilter.fromDate, dateFilter.toDate));
  const filteredAttendance = attendance.filter((a) => {
    const session = sessionById.get(a.classSessionId);
    return session != null && inDateRange(session.sessionDate, dateFilter.fromDate, dateFilter.toDate);
  });

  const total = filteredAttendance.length;
  const present = filteredAttendance.filter((a) => a.status === "PRESENT").length;
  const late = filteredAttendance.filter((a) => a.status === "LATE").length;
  const absent = filteredAttendance.filter((a) => a.status === "ABSENT").length;
  const excusedCount = attendance.filter((a) => a.status === "EXCUSED").length;

  // Đi trễ vẫn tính là đi học (nghiệp vụ xác nhận 2026-08-04) — cộng nguyên late vào rate, không nhân 0.5.
  const rate = total > 0 ? (((present + late) / total) * 100).toFixed(0) : "0";

  const sortedAttendance = [...filteredAttendance].sort((a, b) => {
    const sa = sessionById.get(a.classSessionId);
    const sb = sessionById.get(b.classSessionId);
    if (!sa || !sb) return 0;
    return `${sb.sessionDate}T${sb.startTime}`.localeCompare(`${sa.sessionDate}T${sa.startTime}`);
  });

  if (loading) return <p className="text-sm text-muted font-bold">Đang tải...</p>;

  return (
    <div className="space-y-6">
      {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-6">
        <div className="bg-white border border-line/80 p-5 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-2">
          <span className="text-[10px] font-extrabold text-muted uppercase tracking-wider block">Tỷ lệ đi học</span>
          <p className="text-2xl font-extrabold text-teal">{rate}% {total > 0 && (<span className="pl-3 text-lg text-muted">{(present + late)} / {total} buổi</span>)}</p>
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
          <p className="text-2xl font-extrabold text-coral">{absent + excusedCount} buổi {excusedCount > 0 && (<span className="text-lg text-muted">({excusedCount} có phép)</span>)}</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-4">
          {/* "Xem tất cả" đặt ngang hàng tiêu đề (theo yêu cầu người dùng 2026-08-12) — trước đây
              đứng chung hàng với 2 nút lọc bên dưới, trên mobile hàng đó dễ xuống dòng 3 tầng (Học
              kỳ/Từ→Đến rồi tới Xem tất cả), giờ tách hẳn lên hàng tiêu đề cho gọn. */}
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 className="text-xl font-extrabold text-ink flex items-center gap-2">
              <Calendar className="text-teal" /> Lịch buổi học
            </h2>
            {dateFilter.isActive && (
              <button
                type="button"
                onClick={dateFilter.reset}
                className="flex items-center gap-1 text-xs font-extrabold text-teal-deep hover:underline shrink-0"
              >
                <RotateCcw size={13} /> Xem tất cả
              </button>
            )}
          </div>
          <ScheduleFilterBar
            fromDate={dateFilter.fromDate}
            toDate={dateFilter.toDate}
            onDateRangeChange={dateFilter.setDateRange}
            terms={dateFilter.terms}
            selectedTermId={dateFilter.selectedTermId}
            onSelectTerm={dateFilter.selectTerm}
          />
          <div className="space-y-4 max-h-[480px] overflow-y-auto pr-1">
            {filteredSchedule.map((s) => {
              const badge = getSessionStatusBadge(s);
              return (
              <div
                key={s.id}
                className="relative border border-line/80 p-5 rounded-[20px] flex flex-col md:flex-row justify-between items-start md:items-center gap-4 bg-sky-2"
              >
                {/* Badge tuyệt đối (absolute) không chiếm chỗ trong flow trên mobile, nên KHÔNG cần chừa
                    padding-phải cho cả khối — pill "Thứ ..." đã tự xuống dòng riêng do flex-wrap (không
                    đủ chỗ đứng cùng hàng với "Buổi N · ngày · giờ"), badge nổi đúng ngang hàng pill mà
                    không đụng nhau vì pill ngắn, nằm bên trái. Thêm padding sẽ chỉ vô cớ bóp hẹp dòng
                    "Buổi N..." (đã ở dưới, ngoài tầm badge) khiến nó vỡ thêm 1 dòng nữa (bug đã gặp). */}
                <div className="space-y-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="px-3 py-1 bg-teal border border-teal-deep/30 text-white text-xs font-extrabold rounded-full">
                      {new Date(s.sessionDate).toLocaleDateString("vi-VN", { weekday: "long" }).replace(/^./, (c) => c.toUpperCase())}
                    </span>
                    <span className="text-xs text-muted font-bold">
                      Buổi {s.sessionNumber} · {s.sessionDate} · {formatHm(s.startTime)}–{formatHm(s.endTime)}
                    </span>
                    {/* Mobile: neo cố định góc trên-phải của thẻ (theo yêu cầu người dùng, 2026-07-31).
                        Desktop (md+) trả về static, nằm đúng vị trí cũ trong hàng cùng nhãn thứ/ngày giờ. */}
                    <span
                      className={`absolute top-3 right-3 md:static md:top-auto md:right-auto inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[10px] font-extrabold border ${badge.className}`}
                    >
                      {badge.icon} {badge.label}
                    </span>
                  </div>
                  <p className="text-xs text-muted font-bold">
                    Giáo viên:
                    {s.teacherType && <span className="text-[10px] text-teal-deep font-bold"> ({teacherTypeLabels[s.teacherType]})</span>}
                  </p>
                  {s.status === "CANCELLED" && s.cancellationReason && (
                    <span className="text-[10px] font-extrabold text-coral">Lý do hủy: {s.cancellationReason}</span>
                  )}
                </div>
                <div className="bg-white border border-line/80 px-4 py-2 rounded-xl text-center shadow-sm w-full md:w-auto shrink-0">
                  <span className="text-[10px] font-extrabold text-muted uppercase block">Phòng học</span>
                  <span className="text-sm font-extrabold text-teal-deep">{s.roomName ?? "—"}</span>
                </div>
              </div>
              );
            })}
            {filteredSchedule.length === 0 && (
              <p className="text-xs text-muted font-bold italic">
                {dateFilter.isActive ? "Không có buổi học nào trong khoảng đã lọc." : "Chưa có buổi học nào."}
              </p>
            )}
          </div>
        </div>

        <div className="lg:col-span-1 bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] max-h-[560px] overflow-y-auto space-y-4">
          <h3 className="text-lg font-extrabold text-ink flex items-center gap-2">
            <FileSpreadsheet className="text-teal" /> Nhật ký chuyên cần
          </h3>
          <div className="space-y-4">
            {sortedAttendance.map((a) => {
              const meta = ATTENDANCE_META[a.status] ?? ATTENDANCE_META.PRESENT;
              const session = sessionById.get(a.classSessionId);
              return (
                <div key={a.id} className={`p-4 rounded-xl border ${meta.bg} shadow-sm space-y-2`}>
                  <div className="flex justify-between items-center">
                    <div className="flex items-center gap-1">
                      {meta.icon}
                      <span className="text-xs font-extrabold text-ink">{meta.label}</span>
                    </div>
                    {a.minutesLate ? <span className="text-[10px] text-muted font-bold">Muộn {a.minutesLate} phút</span> : null}
                  </div>
                  <p className="text-[11px] text-muted font-bold">
                    {session ? `Buổi ${session.sessionNumber} · ${session.sessionDate} · ${formatHm(session.startTime)}–${formatHm(session.endTime)}` : "Không rõ buổi học"}
                  </p>
                  {a.absenceReason && <p className="text-[11px] italic text-muted font-bold border-t border-line/50 pt-1 mt-1">* Lý do: {a.absenceReason}</p>}
                </div>
              );
            })}
            {filteredAttendance.length === 0 && (
              <p className="text-xs text-muted font-bold italic">
                {dateFilter.isActive ? "Không có dữ liệu chuyên cần trong khoảng đã lọc." : "Chưa có dữ liệu chuyên cần."}
              </p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
