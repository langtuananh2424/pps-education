import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
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
          <span className="text-xs font-bold text-slate-800 font-display block">{title}</span>
          <p className="text-xs text-slate-400 truncate mt-0.5">{subtitle}</p>
        </div>
      </div>
      {action && (
        <button onClick={action.onClick} className="text-xs font-bold text-brand-red shrink-0 hover:underline">
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
      <p className="text-xs font-bold text-slate-600">{title}</p>
      {description && <p className="text-xs text-slate-400 mt-1 max-w-xs">{description}</p>}
    </div>
  );
}

/** Xu hướng hoàn thành theo tuần — SVG vẽ tay (repo không có thư viện chart), area+line+điểm cuối nhấn mạnh. */
function WeeklyTrendChart({ points, thisWeekLabel }: { points: TeacherWeekPoint[]; thisWeekLabel: string }) {
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
            {c.isCurrentWeek ? thisWeekLabel : c.weekLabel}
          </span>
        ))}
      </div>
    </div>
  );
}

type AssignmentStatusKey = "overdue" | "done" | "dueSoon" | "open";

function assignmentStatusKey(a: TeacherAssignmentRow): AssignmentStatusKey {
  const overdue = !!a.dueAt && new Date(a.dueAt) < new Date() && a.completionPercent < 100;
  if (overdue) return "overdue";
  if (a.completionPercent >= 100) return "done";
  if (a.dueAt) {
    const daysLeft = (new Date(a.dueAt).getTime() - Date.now()) / 86_400_000;
    if (daysLeft <= 2) return "dueSoon";
  }
  return "open";
}

const assignmentStatusVariant: Record<AssignmentStatusKey, BadgeVariant> = {
  overdue: "danger",
  done: "success",
  dueSoon: "warning",
  open: "info"
};

type ClassNoteKey = "good" | "fair" | "needsImprovement";

function classNoteKey(completionPercent: number): ClassNoteKey {
  if (completionPercent >= 85) return "good";
  if (completionPercent >= 70) return "fair";
  return "needsImprovement";
}

const classNoteVariant: Record<ClassNoteKey, BadgeVariant> = {
  good: "success",
  fair: "warning",
  needsImprovement: "danger"
};

const classBarColor: Record<BadgeVariant, string> = {
  success: "bg-emerald-500",
  warning: "bg-amber-500",
  danger: "bg-rose-500",
  info: "bg-sky-500",
  neutral: "bg-slate-400",
  brand: "bg-brand-gradient"
};

type SessionStatusKey = "done" | "ongoing" | "upcoming";

/** So thời gian thật (giờ hiện tại vs startTime/endTime buổi học) — không suy đoán, tính trực tiếp từ dữ liệu đã có. */
function sessionStatusKey(s: ClassSessionResponse): SessionStatusKey {
  const today = new Date().toISOString().slice(0, 10);
  const start = new Date(`${today}T${s.startTime}`);
  const end = new Date(`${today}T${s.endTime}`);
  const now = new Date();
  if (now > end) return "done";
  if (now >= start) return "ongoing";
  return "upcoming";
}

const sessionStatusVariant: Record<SessionStatusKey, BadgeVariant> = {
  done: "neutral",
  ongoing: "success",
  upcoming: "info"
};

/**
 * Dashboard Giáo viên — bổ sung ngoài SDD gốc (không có UC/FR riêng đặc tả), map dữ liệu thật
 * từ các API tự-phục-vụ đã có sẵn (xem useTeacherDashboardData.ts). Cố tình hạn chế số màu icon
 * (chỉ hàng KPI có màu, còn lại icon trung tính) và tránh số liệu trùng lặp — theo phản hồi
 * người dùng 2026-08-07. 2 khối "điểm trung bình theo lớp" và "HS chưa xem video" vẫn loại khỏi
 * v1 vì chưa có nguồn dữ liệu đủ tin cậy — không tự suy diễn số liệu (business-fidelity.md).
 */
export default function TeacherDashboard() {
  const { t } = useTranslation("dashboard");
  const { currentUser, setSelectedClassId } = useApp();
  const { data, loading } = useTeacherDashboardData();
  const navigate = useNavigate();

  const goToClass = (classId: number) => {
    setSelectedClassId(classId);
    navigate("/academic/classes");
  };
  // Điều hướng thẳng vào đúng Bài/Lô đang xem thay vì màn danh sách chung — khớp route chi tiết đã có
  // sẵn ở HomeworkStatsPage.tsx (/academic/homework-stats/:assignmentId hoặc /batch/:homeworkBatchId).
  const goToAssignmentDetail = (a: TeacherAssignmentRow) => {
    setSelectedClassId(a.classId);
    navigate(
      a.homeworkBatchId != null && a.batchMembers != null
        ? `/academic/homework-stats/batch/${a.homeworkBatchId}`
        : `/academic/homework-stats/${a.assignmentId}`
    );
  };

  const firstName = currentUser?.fullName?.split(" ").slice(-1)[0] ?? "";

  const [assignmentFilter, setAssignmentFilter] = useState<"all" | "pending" | "done">("all");
  const filteredAssignments = data.recentAssignments.filter((a) => {
    if (assignmentFilter === "all") return true;
    const key = assignmentStatusKey(a);
    if (assignmentFilter === "pending") return key === "overdue" || key === "dueSoon";
    return key === "done";
  });

  // Xu hướng tuần: chỉ hiện mũi tên tăng/giảm khi có ≥2 tuần dữ liệu thật để so sánh — không bịa khi thiếu dữ liệu.
  const trendPoints = data.weeklyCompletionTrend;
  const completionTrend =
    trendPoints.length >= 2
      ? (() => {
          const diff = trendPoints[trendPoints.length - 1].avgCompletionPercent - trendPoints[trendPoints.length - 2].avgCompletionPercent;
          if (diff === 0) return undefined;
          return {
            value: t("teacher.kpi.trendVsLastWeek", { diff: `${diff > 0 ? "+" : ""}${diff}` }),
            direction: (diff > 0 ? "up" : "down") as "up" | "down"
          };
        })()
      : undefined;

  return (
    <div className="space-y-6">
      {/* Banner gradient — 1 điểm màu đậm duy nhất của trang, phần còn lại giữ trung tính để không bị loãng/màu mè dàn trải */}
      <div className="relative overflow-hidden rounded-2xl bg-brand-gradient text-white shadow-glow p-6 flex items-end justify-between gap-4 flex-wrap">
        <div className="absolute -right-8 -top-12 w-48 h-48 rounded-full bg-white/10 blur-2xl pointer-events-none" />
        <div className="absolute right-20 -bottom-10 w-28 h-28 rounded-full bg-white/10 blur-xl pointer-events-none" />
        <div className="relative z-10">
          <span className="inline-flex items-center gap-1.5 text-xs font-bold text-white bg-white/15 border border-white/25 rounded-full px-2.5 py-1 mb-3">
            <Sparkles className="w-3 h-3" />
            {t("teacher.banner.badge")}
          </span>
          <h1 className="text-xl font-bold font-display tracking-tight">
            {t("teacher.banner.greeting", { name: currentUser?.fullName ?? firstName })}
          </h1>
          <p className="text-xs text-white/80 mt-1.5 max-w-md">{t("teacher.banner.subtitle")}</p>
        </div>
        <div className="relative z-10 flex items-center gap-2 flex-wrap">
          <button
            onClick={() => navigate("/academic/homework-stats")}
            className="text-xs font-bold text-white bg-white/15 hover:bg-white/25 border border-white/25 rounded-xl px-4 py-2.5 flex items-center gap-1.5 transition-all backdrop-blur-sm"
          >
            <ClipboardList className="w-4 h-4" />
            {t("teacher.banner.viewHomeworkButton")}
          </button>
          <button
            onClick={() => navigate("/lms/exercises")}
            className="text-xs font-bold text-brand-red bg-white hover:bg-white/90 rounded-xl px-4 py-2.5 flex items-center gap-1.5 transition-all shadow-md"
          >
            <PlusCircle className="w-4 h-4" />
            {t("teacher.banner.createHomeworkButton")}
          </button>
        </div>
      </div>

      {/* Hàng KPI — luôn 1 dòng 5 cột, tự co giãn theo màn hình thay vì wrap. Chỉ "Hoàn thành" mang gradient brand (chỉ số tổng kết); Chờ chấm/Quá hạn chuyển màu cảnh báo khi >0; còn lại trung tính */}
      <div className="grid grid-cols-4 gap-2 sm:gap-3 md:gap-4">
        {(
          [
            { icon: School, label: t("teacher.kpi.classes"), value: String(data.totalClasses), tone: "slate", hint: t("teacher.kpi.classesHint") },
            {
              icon: PencilLine,
              label: t("teacher.kpi.pendingGrading"),
              value: String(data.pendingGradingCount),
              tone: data.pendingGradingCount > 0 ? "warning" : "slate",
              hint: t("teacher.kpi.pendingGradingHint", { writing: data.pendingWritingGradingCount, speaking: data.pendingSpeakingGradingCount })
            },
            {
              icon: AlarmClock,
              label: t("teacher.kpi.overdue"),
              value: String(data.overdueAssignmentCount),
              tone: data.overdueAssignmentCount > 0 ? "danger" : "slate",
              hint: data.overdueAssignmentCount > 0 ? t("teacher.kpi.overdueHintSome") : t("teacher.kpi.overdueHintNone")
            },
            {
              icon: TrendingUp,
              label: t("teacher.kpi.completion"),
              value: `${data.avgCompletionPercent}%`,
              tone: "brand",
              trend: completionTrend,
              hint: completionTrend ? undefined : t("teacher.kpi.completionHint")
            }
          ] as React.ComponentProps<typeof StatCard>[]
        ).map((card, i) => (
          <StatCard key={i} {...card} />
        ))}
      </div>

      {/* Việc cần làm + Lịch hôm nay */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
        <Card padded={false} className="overflow-hidden">
          <SectionHead
            icon={ClipboardList}
            title={t("teacher.todo.title")}
            subtitle={t("teacher.todo.subtitle")}
            action={{ label: t("teacher.todo.viewAll"), onClick: () => navigate("/academic/homework-stats") }}
          />
          <div className="divide-y divide-slate-100">
            {data.pendingGradingCount > 0 && (
              <div className="p-4 flex items-start gap-3">
                <span className="w-2 h-2 rounded-full bg-rose-500 mt-2 shrink-0" />
                <div className="min-w-0">
                  <p className="text-xs font-bold text-slate-800">{t("teacher.todo.pendingGrading", { count: data.pendingGradingCount })}</p>
                  <p className="text-xs text-slate-400 mt-0.5">{t("teacher.todo.pendingGradingDesc")}</p>
                </div>
              </div>
            )}
            {data.incompleteAssignments.map((a) => {
              const overdue = !!a.dueAt && new Date(a.dueAt) < new Date();
              return (
                <button
                  key={a.assignmentId}
                  onClick={() => goToAssignmentDetail(a)}
                  className="w-full p-4 flex items-start gap-3 text-left hover:bg-slate-50"
                >
                  <span className="w-2 h-2 rounded-full bg-amber-500 mt-2 shrink-0" />
                  <div className="min-w-0 flex-1">
                    <p className="text-xs font-bold text-slate-800 truncate">{a.exerciseTitle}</p>
                    <p className="text-xs text-slate-400 mt-0.5">
                      {t("teacher.todo.classLabel", { className: a.className, count: a.totalStudents - a.completedCount })}
                    </p>
                  </div>
                  <Badge variant={overdue ? "danger" : "warning"} className="shrink-0">
                    {overdue ? t("teacher.assignmentStatus.overdue") : t("teacher.todo.needsFollowUp")}
                  </Badge>
                </button>
              );
            })}
            {data.pendingGradingCount === 0 && data.incompleteAssignments.length === 0 && !loading && (
              <InlineEmpty icon={CheckCircle2} title={t("teacher.todo.emptyTitle")} description={t("teacher.todo.emptyDescription")} tone="good" />
            )}
          </div>
        </Card>

        <Card padded={false} className="overflow-hidden">
          <SectionHead
            icon={CalendarClock}
            title={t("teacher.todaySchedule.title")}
            subtitle={t("teacher.todaySchedule.subtitle")}
            action={{ label: t("teacher.todaySchedule.viewSchedule"), onClick: () => navigate("/schedule/my-timetable") }}
          />
          <div className="p-4">
            {data.todaySessions.map((s, idx) => {
              const statusKey = sessionStatusKey(s);
              const studentCount = data.myClasses.find((c) => c.classId === s.classId)?.studentCount;
              return (
                <div key={s.id} className="relative flex gap-3 pb-5 last:pb-0">
                  {idx < data.todaySessions.length - 1 && <span className="absolute left-[5px] top-3 bottom-0 w-px bg-slate-150" />}
                  <span className={`w-2.5 h-2.5 rounded-full mt-1.5 shrink-0 ${statusKey === "ongoing" ? "bg-emerald-500 ring-4 ring-emerald-50" : "bg-brand-orange ring-4 ring-orange-50"}`} />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-xs font-bold text-slate-800 font-mono shrink-0">{s.startTime}</span>
                      <p className="text-xs font-bold text-slate-800">{t("teacher.todaySchedule.classLabel", { className: s.className })}</p>
                      <Badge variant={sessionStatusVariant[statusKey]}>{t(`teacher.sessionStatus.${statusKey}`)}</Badge>
                    </div>
                    <p className="text-xs text-slate-400 mt-1">
                      {s.roomName ?? t("teacher.todaySchedule.noRoom")}
                      {studentCount != null && t("teacher.todaySchedule.studentCountSuffix", { count: studentCount })}
                    </p>
                  </div>
                  <button
                    onClick={() => {
                      setSelectedClassId(s.classId);
                      navigate(`/student/attendance?classId=${s.classId}&sessionId=${s.id}`);
                    }}
                    className="text-xs font-bold text-slate-600 bg-slate-100 hover:bg-slate-200 rounded-lg px-3 py-1.5 shrink-0 self-start"
                  >
                    {t("teacher.todaySchedule.checkInButton")}
                  </button>
                </div>
              );
            })}
            {data.todaySessions.length === 0 && !loading && <InlineEmpty icon={CalendarClock} title={t("teacher.todaySchedule.emptyTitle")} />}
          </div>
        </Card>
      </div>

      {/* Bài tập gần đây + Lớp của tôi — chung 1 hàng */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
      <Card padded={false} className="overflow-hidden">
        <SectionHead
          icon={ClipboardList}
          title={t("teacher.recentAssignments.title")}
          subtitle={t("teacher.recentAssignments.subtitle")}
          action={{ label: t("teacher.recentAssignments.viewAll"), onClick: () => navigate("/academic/homework-stats") }}
        />
        {data.recentAssignments.length > 0 && (
          <div className="px-5 pt-3.5 flex items-center gap-1.5">
            {(
              [
                ["all", t("teacher.recentAssignments.filterAll")],
                ["pending", t("teacher.recentAssignments.filterPending")],
                ["done", t("teacher.recentAssignments.filterDone")]
              ] as const
            ).map(([key, label]) => (
              <button
                key={key}
                onClick={() => setAssignmentFilter(key)}
                className={`text-xs font-bold px-3 py-1.5 rounded-full transition-all ${
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
                <Th>{t("teacher.recentAssignments.columns.title")}</Th>
                <Th>{t("teacher.recentAssignments.columns.class")}</Th>
                <Th>{t("teacher.recentAssignments.columns.completed")}</Th>
                <Th>{t("teacher.recentAssignments.columns.rate")}</Th>
                <Th>{t("teacher.recentAssignments.columns.status")}</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredAssignments.map((a) => {
                const statusKey = assignmentStatusKey(a);
                return (
                  <tr key={a.assignmentId} onClick={() => goToAssignmentDetail(a)} className="cursor-pointer hover:bg-slate-50">
                    <Td className="font-bold text-xs text-slate-800">{a.exerciseTitle}</Td>
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
                      <Badge variant={assignmentStatusVariant[statusKey]}>{t(`teacher.assignmentStatus.${statusKey}`)}</Badge>
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
              title={data.recentAssignments.length === 0 ? t("teacher.recentAssignments.emptyNone") : t("teacher.recentAssignments.emptyFiltered")}
            />
          )
        )}
      </Card>

      {/* Lớp của tôi */}
      <Card padded={false} className="overflow-hidden">
        <SectionHead
          icon={School}
          title={t("teacher.myClasses.title")}
          subtitle={`${data.totalClasses}${t("teacher.myClasses.subtitleSuffix")}`}
          action={{ label: t("teacher.myClasses.viewAll"), onClick: () => navigate("/academic/classes") }}
        />
        {data.myClasses.length > 0 ? (
          <TableContainer className="border-0 rounded-none">
            <thead>
              <tr>
                <Th>{t("teacher.myClasses.columns.class")}</Th>
                <Th>{t("teacher.myClasses.columns.size")}</Th>
                <Th>{t("teacher.myClasses.columns.completionRate")}</Th>
                <Th>{t("teacher.myClasses.columns.overdueAssignments")}</Th>
                <Th>{t("teacher.myClasses.columns.rating")}</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.myClasses.map((c) => {
                const noteKey = classNoteKey(c.completionPercent);
                return (
                  <tr key={c.classId} onClick={() => goToClass(c.classId)} className="cursor-pointer hover:bg-slate-50">
                    <Td className="font-bold text-xs text-slate-800">{c.className}</Td>
                    <Td className="font-mono">{c.studentCount} {t("teacher.myClasses.studentsUnit")}</Td>
                    <Td>
                      <div className="flex items-center gap-2">
                        <div className="w-32 h-1.5 rounded-full bg-slate-100 overflow-hidden shrink-0">
                          <div className={`h-full rounded-full ${classBarColor[classNoteVariant[noteKey]]}`} style={{ width: `${c.completionPercent}%` }} />
                        </div>
                        <span className="font-mono font-bold text-slate-700">{c.completionPercent}%</span>
                      </div>
                    </Td>
                    <Td className="font-mono">{c.overdueCount > 0 ? c.overdueCount : "—"}</Td>
                    <Td>
                      <Badge variant={classNoteVariant[noteKey]}>{t(`teacher.classNote.${noteKey}`)}</Badge>
                    </Td>
                  </tr>
                );
              })}
            </tbody>
          </TableContainer>
        ) : (
          !loading && <InlineEmpty icon={School} title={t("teacher.myClasses.emptyTitle")} />
        )}
      </Card>
      </div>

      {/* Xu hướng — full-width để biểu đồ có chiều ngang thoáng hơn thay vì bó trong nửa trang */}
      <Card padded={false} className="overflow-hidden">
        <SectionHead
          icon={TrendingUp}
          title={t("teacher.trend.title")}
          subtitle={
            trendPoints.length > 0
              ? t("teacher.trend.subtitleWithData", { count: trendPoints.length })
              : t("teacher.trend.subtitleEmpty")
          }
        />
        <div className="p-6">
          {trendPoints.length > 0 ? (
            <WeeklyTrendChart points={trendPoints} thisWeekLabel={t("teacher.trend.thisWeek")} />
          ) : (
            !loading && <InlineEmpty icon={TrendingUp} title={t("teacher.trend.emptyTitle")} description={t("teacher.trend.emptyDescription")} />
          )}
        </div>
      </Card>
    </div>
  );
}
