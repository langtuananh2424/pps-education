import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  AlarmClock,
  CalendarClock,
  CheckCircle2,
  ClipboardList,
  GraduationCap,
  PencilLine,
  PlusCircle,
  School,
  Sparkles,
  TrendingUp
} from "lucide-react";
import { useApp } from "@/context/AppContext";
import Card from "@/components/ui/Card";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import StatCard from "@/components/ui/StatCard";
import TableContainer, { Th, Td } from "@/components/ui/TableContainer";
import { ClassSessionResponse } from "@/features/academic/api";
import { TeacherAssignmentRow, TeacherWeekPoint, useTeacherDashboardData } from "../hooks/useTeacherDashboardData";

/** Header khối — icon mảnh trung tính (không chip màu) + link hành động, giữ màu sắc chỉ dành riêng cho hàng KPI. */
function SectionHead({
  icon: Icon,
  title,
  subtitle,
  action
}: {
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  subtitle: string;
  action?: { label: string; onClick: () => void };
}) {
  return (
    <div className="px-5 py-4 border-b border-slate-100 bg-slate-50/70 flex items-center justify-between gap-3">
      <div className="flex items-center gap-3 min-w-0">
        <Icon className="w-[18px] h-[18px] text-slate-400 shrink-0" />
        <div className="min-w-0">
          <span className="text-sm font-bold text-slate-800 font-display block">{title}</span>
          <p className="text-sm text-slate-400 truncate mt-0.5">{subtitle}</p>
        </div>
      </div>
      {action && (
        <button onClick={action.onClick} className="text-sm font-bold text-brand-red shrink-0 hover:underline">
          {action.label}
        </button>
      )}
    </div>
  );
}

/** Empty state gọn — dùng riêng cho từng widget nhỏ trong dashboard thay vì EmptyState dùng chung (vốn cho cả trang trống). */
function InlineEmpty({
  icon: Icon,
  title,
  description,
  tone = "neutral"
}: {
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  description?: string;
  tone?: "neutral" | "good";
}) {
  return (
    <div className="flex flex-col items-center justify-center text-center py-8 px-4">
      <div className={`w-10 h-10 rounded-full flex items-center justify-center mb-2.5 ${tone === "good" ? "bg-emerald-50 text-emerald-500" : "bg-slate-100 text-slate-400"}`}>
        <Icon className="w-5 h-5" />
      </div>
      <p className="text-sm font-bold text-slate-600">{title}</p>
      {description && <p className="text-sm text-slate-400 mt-1 max-w-xs">{description}</p>}
    </div>
  );
}

/** Xu hướng hoàn thành theo tuần — SVG vẽ tay (repo không có thư viện chart), area+line+điểm cuối nhấn mạnh. */
function WeeklyTrendChart({ points }: { points: TeacherWeekPoint[] }) {
  const W = 960;
  const H = 180;
  const padX = 24;
  const padY = 18;
  const innerH = H - padY * 2;
  const stepX = points.length > 1 ? (W - padX * 2) / (points.length - 1) : 0;
  const coords = points.map((p, i) => ({
    x: points.length > 1 ? padX + stepX * i : W / 2,
    y: H - padY - (Math.min(p.avgCompletionPercent, 100) / 100) * innerH,
    ...p
  }));
  const linePath = coords.map((c, i) => `${i === 0 ? "M" : "L"} ${c.x.toFixed(1)} ${c.y.toFixed(1)}`).join(" ");
  const areaPath = `${linePath} L ${coords[coords.length - 1].x.toFixed(1)} ${H - padY} L ${coords[0].x.toFixed(1)} ${H - padY} Z`;

  return (
    <div>
      <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" className="w-full h-48">
        <defs>
          <linearGradient id="teacherTrendFill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#ea580c" stopOpacity="0.18" />
            <stop offset="100%" stopColor="#ea580c" stopOpacity="0" />
          </linearGradient>
        </defs>
        {[0, 50, 100].map((g) => {
          const y = H - padY - (g / 100) * innerH;
          return <line key={g} x1={padX} y1={y} x2={W - padX} y2={y} stroke="#f1f5f9" strokeWidth="1" />;
        })}
        {coords.length > 1 && <path d={areaPath} fill="url(#teacherTrendFill)" stroke="none" />}
        {coords.length > 1 && <path d={linePath} fill="none" stroke="#ea580c" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />}
        {coords.map((c, i) => (
          <circle
            key={c.weekLabel + i}
            cx={c.x}
            cy={c.y}
            r={i === coords.length - 1 ? 5 : 3}
            fill={i === coords.length - 1 ? "#ea580c" : "#ffffff"}
            stroke="#ea580c"
            strokeWidth="2"
          />
        ))}
      </svg>
      <div className="flex justify-between px-1 mt-1">
        {coords.map((c, i) => (
          <span key={c.weekLabel + i} className="text-[9px] font-bold text-slate-400" style={{ flex: coords.length > 1 ? "0 0 auto" : undefined }}>
            {c.weekLabel}
          </span>
        ))}
      </div>
    </div>
  );
}

function assignmentStatus(a: TeacherAssignmentRow): { label: string; variant: BadgeVariant } {
  const overdue = !!a.dueAt && new Date(a.dueAt) < new Date() && a.completionPercent < 100;
  if (overdue) return { label: "Quá hạn", variant: "danger" };
  if (a.completionPercent >= 100) return { label: "Hoàn thành", variant: "success" };
  if (a.dueAt) {
    const daysLeft = (new Date(a.dueAt).getTime() - Date.now()) / 86_400_000;
    if (daysLeft <= 2) return { label: "Sắp hết hạn", variant: "warning" };
  }
  return { label: "Đang mở", variant: "info" };
}

function classNote(completionPercent: number): { label: string; variant: BadgeVariant } {
  if (completionPercent >= 85) return { label: "Tốt", variant: "success" };
  if (completionPercent >= 70) return { label: "Khá", variant: "warning" };
  return { label: "Cần cải thiện", variant: "danger" };
}

const classBarColor: Record<BadgeVariant, string> = {
  success: "bg-emerald-500",
  warning: "bg-amber-500",
  danger: "bg-rose-500",
  info: "bg-sky-500",
  neutral: "bg-slate-400",
  brand: "bg-brand-gradient"
};

/** So thời gian thật (giờ hiện tại vs startTime/endTime buổi học) — không suy đoán, tính trực tiếp từ dữ liệu đã có. */
function sessionStatus(s: ClassSessionResponse): { label: string; variant: BadgeVariant } {
  const today = new Date().toISOString().slice(0, 10);
  const start = new Date(`${today}T${s.startTime}`);
  const end = new Date(`${today}T${s.endTime}`);
  const now = new Date();
  if (now > end) return { label: "Đã xong", variant: "neutral" };
  if (now >= start) return { label: "Đang diễn ra", variant: "success" };
  return { label: "Sắp diễn ra", variant: "info" };
}

/**
 * Dashboard Giáo viên — bổ sung ngoài SDD gốc (không có UC/FR riêng đặc tả), map dữ liệu thật
 * từ các API tự-phục-vụ đã có sẵn (xem useTeacherDashboardData.ts). Cố tình hạn chế số màu icon
 * (chỉ hàng KPI có màu, còn lại icon trung tính) và tránh số liệu trùng lặp — theo phản hồi
 * người dùng 2026-08-07. 2 khối "điểm trung bình theo lớp" và "HS chưa xem video" vẫn loại khỏi
 * v1 vì chưa có nguồn dữ liệu đủ tin cậy — không tự suy diễn số liệu (business-fidelity.md).
 */
export default function TeacherDashboard() {
  const { currentUser, setSelectedClassId } = useApp();
  const { data, loading } = useTeacherDashboardData();
  const navigate = useNavigate();

  const goToClass = (classId: number) => {
    setSelectedClassId(classId);
    navigate("/academic/classes");
  };
  const goToHomeworkStats = (classId: number) => {
    setSelectedClassId(classId);
    navigate("/academic/homework-stats");
  };

  const firstName = currentUser?.fullName?.split(" ").slice(-1)[0] ?? "";

  const [assignmentFilter, setAssignmentFilter] = useState<"all" | "pending" | "done">("all");
  const filteredAssignments = data.recentAssignments.filter((a) => {
    if (assignmentFilter === "all") return true;
    const label = assignmentStatus(a).label;
    if (assignmentFilter === "pending") return label === "Quá hạn" || label === "Sắp hết hạn";
    return label === "Hoàn thành";
  });

  // Xu hướng tuần: chỉ hiện mũi tên tăng/giảm khi có ≥2 tuần dữ liệu thật để so sánh — không bịa khi thiếu dữ liệu.
  const trendPoints = data.weeklyCompletionTrend;
  const completionTrend =
    trendPoints.length >= 2
      ? (() => {
          const diff = trendPoints[trendPoints.length - 1].avgCompletionPercent - trendPoints[trendPoints.length - 2].avgCompletionPercent;
          if (diff === 0) return undefined;
          return { value: `${diff > 0 ? "+" : ""}${diff}% so với tuần trước`, direction: (diff > 0 ? "up" : "down") as "up" | "down" };
        })()
      : undefined;

  return (
    <div className="space-y-6">
      {/* Banner gradient — 1 điểm màu đậm duy nhất của trang, phần còn lại giữ trung tính để không bị loãng/màu mè dàn trải */}
      <div className="relative overflow-hidden rounded-2xl bg-brand-gradient text-white shadow-glow p-6 flex items-end justify-between gap-4 flex-wrap">
        <div className="absolute -right-8 -top-12 w-48 h-48 rounded-full bg-white/10 blur-2xl pointer-events-none" />
        <div className="absolute right-20 -bottom-10 w-28 h-28 rounded-full bg-white/10 blur-xl pointer-events-none" />
        <div className="relative z-10">
          <span className="inline-flex items-center gap-1.5 text-sm font-bold text-white bg-white/15 border border-white/25 rounded-full px-2.5 py-1 mb-3">
            <Sparkles className="w-3 h-3" />
            PPS VIETNAM · LMS Giáo viên
          </span>
          <h1 className="text-2xl md:text-3xl font-bold font-display tracking-tight">
            Xin chào, {currentUser?.fullName ?? firstName} 👋
          </h1>
          <p className="text-sm text-white/80 mt-1.5 max-w-md">Tổng quan hoạt động giảng dạy, việc cần làm và tiến độ bài tập của học sinh hôm nay.</p>
        </div>
        <div className="relative z-10 flex items-center gap-2 flex-wrap">
          <button
            onClick={() => navigate("/academic/homework-stats")}
            className="text-sm font-bold text-white bg-white/15 hover:bg-white/25 border border-white/25 rounded-xl px-4 py-2.5 flex items-center gap-1.5 transition-all backdrop-blur-sm"
          >
            <ClipboardList className="w-4 h-4" />
            Xem bài tập
          </button>
          <button
            onClick={() => navigate("/lms/exercises")}
            className="text-sm font-bold text-brand-red bg-white hover:bg-white/90 rounded-xl px-4 py-2.5 flex items-center gap-1.5 transition-all shadow-md"
          >
            <PlusCircle className="w-4 h-4" />
            Tạo bài tập
          </button>
        </div>
      </div>

      {/* KPI row — chỉ "Hoàn thành" mang gradient brand (chỉ số tổng kết); Chờ chấm/Quá hạn chuyển màu cảnh báo khi >0; còn lại trung tính */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
        <StatCard icon={GraduationCap} label="Học sinh" value={String(data.totalStudents)} tone="slate" hint={`Qua ${data.totalClasses} lớp`} />
        <StatCard icon={School} label="Lớp" value={String(data.totalClasses)} tone="slate" hint="Đang phụ trách" />
        <StatCard
          icon={PencilLine}
          label="Chờ chấm"
          value={String(data.pendingGradingCount)}
          tone={data.pendingGradingCount > 0 ? "warning" : "slate"}
          hint={`${data.pendingWritingGradingCount} bài + ${data.pendingSpeakingGradingCount} lượt nói`}
        />
        <StatCard
          icon={AlarmClock}
          label="Quá hạn"
          value={String(data.overdueAssignmentCount)}
          tone={data.overdueAssignmentCount > 0 ? "danger" : "slate"}
          hint={data.overdueAssignmentCount > 0 ? "Cần xử lý sớm" : "Không có bài quá hạn"}
        />
        <StatCard icon={TrendingUp} label="Hoàn thành" value={`${data.avgCompletionPercent}%`} tone="brand" trend={completionTrend} hint={completionTrend ? undefined : "Trung bình các bài đang mở"} />
      </div>

      {/* Việc cần làm + Lịch hôm nay */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card padded={false} className="overflow-hidden">
          <SectionHead icon={ClipboardList} title="Việc cần làm" subtitle="Ưu tiên xử lý hôm nay" action={{ label: "Xem tất cả →", onClick: () => navigate("/academic/homework-stats") }} />
          <div className="divide-y divide-slate-100">
            {data.pendingGradingCount > 0 && (
              <div className="p-4 flex items-start gap-3">
                <span className="w-2 h-2 rounded-full bg-rose-500 mt-2 shrink-0" />
                <div className="min-w-0">
                  <p className="text-sm font-bold text-slate-800">{data.pendingGradingCount} bài chờ chấm tay</p>
                  <p className="text-sm text-slate-400 mt-0.5">Essay/Speaking (BTVN) và Luyện Nói — chưa có trang chấm để mở trực tiếp từ đây.</p>
                </div>
              </div>
            )}
            {data.incompleteAssignments.map((a) => {
              const overdue = !!a.dueAt && new Date(a.dueAt) < new Date();
              return (
                <button
                  key={a.assignmentId}
                  onClick={() => goToHomeworkStats(a.classId)}
                  className="w-full p-4 flex items-start gap-3 text-left hover:bg-slate-50"
                >
                  <span className="w-2 h-2 rounded-full bg-amber-500 mt-2 shrink-0" />
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-bold text-slate-800 truncate">{a.exerciseTitle}</p>
                    <p className="text-sm text-slate-400 mt-0.5">
                      Lớp {a.className} · {a.totalStudents - a.completedCount} HS chưa nộp
                    </p>
                  </div>
                  <Badge variant={overdue ? "danger" : "warning"} className="shrink-0">
                    {overdue ? "Quá hạn" : "Cần theo dõi"}
                  </Badge>
                </button>
              );
            })}
            {data.pendingGradingCount === 0 && data.incompleteAssignments.length === 0 && !loading && (
              <InlineEmpty icon={CheckCircle2} title="Không có việc cần xử lý" description="Mọi bài tập đang mở đều đã được nộp đủ." tone="good" />
            )}
          </div>
        </Card>

        <Card padded={false} className="overflow-hidden">
          <SectionHead icon={CalendarClock} title="Lịch hôm nay" subtitle="Tổng hợp buổi dạy hôm nay qua mọi lớp" action={{ label: "Xem lịch →", onClick: () => navigate("/schedule/my-timetable") }} />
          <div className="p-4">
            {data.todaySessions.map((s, idx) => {
              const status = sessionStatus(s);
              const studentCount = data.myClasses.find((c) => c.classId === s.classId)?.studentCount;
              return (
                <div key={s.id} className="relative flex gap-3 pb-5 last:pb-0">
                  {idx < data.todaySessions.length - 1 && <span className="absolute left-[5px] top-3 bottom-0 w-px bg-slate-150" />}
                  <span className={`w-2.5 h-2.5 rounded-full mt-1.5 shrink-0 ${status.label === "Đang diễn ra" ? "bg-emerald-500 ring-4 ring-emerald-50" : "bg-brand-orange ring-4 ring-orange-50"}`} />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-sm font-bold text-slate-800 font-mono shrink-0">{s.startTime}</span>
                      <p className="text-sm font-bold text-slate-800">Lớp {s.className}</p>
                      <Badge variant={status.variant}>{status.label}</Badge>
                    </div>
                    <p className="text-sm text-slate-400 mt-1">
                      {s.roomName ?? "Chưa gán phòng"}
                      {studentCount != null && ` · ${studentCount} học sinh`}
                    </p>
                  </div>
                  <button
                    onClick={() => {
                      setSelectedClassId(s.classId);
                      navigate("/student/attendance");
                    }}
                    className="text-sm font-bold text-slate-600 bg-slate-100 hover:bg-slate-200 rounded-lg px-3 py-1.5 shrink-0 self-start"
                  >
                    Điểm danh
                  </button>
                </div>
              );
            })}
            {data.todaySessions.length === 0 && !loading && <InlineEmpty icon={CalendarClock} title="Không có buổi dạy hôm nay" />}
          </div>
        </Card>
      </div>

      {/* Bài tập gần đây */}
      <Card padded={false} className="overflow-hidden">
        <SectionHead icon={ClipboardList} title="Bài tập gần đây" subtitle="8 bài giao gần nhất, qua mọi lớp đang dạy" action={{ label: "Xem tất cả →", onClick: () => navigate("/academic/homework-stats") }} />
        {data.recentAssignments.length > 0 && (
          <div className="px-5 pt-3.5 flex items-center gap-1.5">
            {(
              [
                ["all", "Tất cả"],
                ["pending", "Cần xử lý"],
                ["done", "Đã hoàn thành"]
              ] as const
            ).map(([key, label]) => (
              <button
                key={key}
                onClick={() => setAssignmentFilter(key)}
                className={`text-sm font-bold px-3 py-1.5 rounded-full transition-all ${
                  assignmentFilter === key ? "bg-slate-800 text-white" : "bg-slate-100 text-slate-500 hover:bg-slate-200"
                }`}
              >
                {label}
              </button>
            ))}
          </div>
        )}
        {filteredAssignments.length > 0 ? (
          <TableContainer className="border-0 rounded-none mt-3.5">
            <thead>
              <tr>
                <Th>Bài tập</Th>
                <Th>Lớp</Th>
                <Th>Đã làm</Th>
                <Th>Tỷ lệ</Th>
                <Th>Trạng thái</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredAssignments.map((a) => {
                const status = assignmentStatus(a);
                return (
                  <tr key={a.assignmentId} onClick={() => goToHomeworkStats(a.classId)} className="cursor-pointer hover:bg-slate-50">
                    <Td className="font-bold text-sm text-slate-800">{a.exerciseTitle}</Td>
                    <Td>{a.className}</Td>
                    <Td className="font-mono">
                      {a.completedCount}/{a.totalStudents}
                    </Td>
                    <Td>
                      <div className="flex items-center gap-2">
                        <div className="w-20 h-1.5 rounded-full bg-slate-100 overflow-hidden shrink-0">
                          <div className="h-full bg-brand-gradient rounded-full" style={{ width: `${a.completionPercent}%` }} />
                        </div>
                        <span className="font-mono font-bold text-slate-700">{a.completionPercent}%</span>
                      </div>
                    </Td>
                    <Td>
                      <Badge variant={status.variant}>{status.label}</Badge>
                    </Td>
                  </tr>
                );
              })}
            </tbody>
          </TableContainer>
        ) : (
          !loading && (
            <InlineEmpty
              icon={ClipboardList}
              title={data.recentAssignments.length === 0 ? "Chưa có bài tập nào được giao" : "Không có bài nào khớp bộ lọc"}
            />
          )
        )}
      </Card>

      {/* Lớp của tôi — full-width để bảng không bị bó hẹp còn nửa trang */}
      <Card padded={false} className="overflow-hidden">
        <SectionHead
          icon={School}
          title="Lớp của tôi"
          subtitle={`${data.totalClasses} lớp đang giảng dạy · bấm vào 1 lớp để xem sâu`}
          action={{ label: "Xem tất cả →", onClick: () => navigate("/academic/classes") }}
        />
        {data.myClasses.length > 0 ? (
          <TableContainer className="border-0 rounded-none">
            <thead>
              <tr>
                <Th>Lớp</Th>
                <Th>Sĩ số</Th>
                <Th>Tỷ lệ hoàn thành</Th>
                <Th>Bài quá hạn</Th>
                <Th>Đánh giá</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.myClasses.map((c) => {
                const note = classNote(c.completionPercent);
                return (
                  <tr key={c.classId} onClick={() => goToClass(c.classId)} className="cursor-pointer hover:bg-slate-50">
                    <Td className="font-bold text-sm text-slate-800">{c.className}</Td>
                    <Td className="font-mono">{c.studentCount} HS</Td>
                    <Td>
                      <div className="flex items-center gap-2">
                        <div className="w-32 h-1.5 rounded-full bg-slate-100 overflow-hidden shrink-0">
                          <div className={`h-full rounded-full ${classBarColor[note.variant]}`} style={{ width: `${c.completionPercent}%` }} />
                        </div>
                        <span className="font-mono font-bold text-slate-700">{c.completionPercent}%</span>
                      </div>
                    </Td>
                    <Td className="font-mono">{c.overdueCount > 0 ? c.overdueCount : "—"}</Td>
                    <Td>
                      <Badge variant={note.variant}>{note.label}</Badge>
                    </Td>
                  </tr>
                );
              })}
            </tbody>
          </TableContainer>
        ) : (
          !loading && <InlineEmpty icon={School} title="Chưa được phân công lớp nào" />
        )}
      </Card>

      {/* Xu hướng — full-width để biểu đồ có chiều ngang thoáng hơn thay vì bó trong nửa trang */}
      <Card padded={false} className="overflow-hidden">
        <SectionHead
          icon={TrendingUp}
          title="Tỷ lệ hoàn thành BTVN theo tuần"
          subtitle={trendPoints.length > 0 ? `${trendPoints.length} tuần gần đây · gộp thật từ dữ liệu bài tập` : 'Gộp thật từ dữ liệu bài tập — thay cho "hoạt động học tập" chung chung'}
        />
        <div className="p-6">
          {trendPoints.length > 0 ? (
            <WeeklyTrendChart points={trendPoints} />
          ) : (
            !loading && <InlineEmpty icon={TrendingUp} title="Chưa đủ dữ liệu để vẽ xu hướng" description="Cần ít nhất 1 bài tập đã giao để tính tỷ lệ hoàn thành theo tuần." />
          )}
        </div>
      </Card>
    </div>
  );
}
