import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { AlertTriangle, CalendarCheck, ClipboardCheck, FileCheck2, GraduationCap, UserRound, Users } from "lucide-react";
import { useTranslation } from "react-i18next";
import { useApp } from "@/context/AppContext";
import { ApiError } from "@/lib/apiClient";
import Card from "@/components/ui/Card";
import StatCard from "@/components/ui/StatCard";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import { listClasses, listPendingComments, listUnpublishedGrades } from "@/features/academic/api";
import { listPendingLeaveRequestsForApprover } from "@/features/hrm/api";
import { toISODate } from "@/lib/calendarDates";
import {
  AcademicDashboardResponse,
  DashboardCheckInState,
  getAcademicDashboard,
  getSessionReportTracking
} from "@/features/academic/oversightApi";

const CHECK_IN_BADGES: Record<DashboardCheckInState, BadgeVariant> = {
  ON_TIME: "success",
  LATE: "warning",
  MISSING: "danger",
  NOT_STARTED: "neutral",
  CANCELLED: "neutral"
};

/** V207 — số báo cáo buổi học đang có vấn đề trong 7 ngày gần nhất (quá hạn ở bất kỳ khâu nào). */
interface ReportCounts {
  missing: number;
  approvalOverdue: number;
  resubmitOverdue: number;
}

interface PendingCounts {
  grades: number;
  comments: number;
  leaves: number;
}

/**
 * Dashboard Trưởng phòng đào tạo (V203 — bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-09-30), thay
 * dữ liệu mẫu cũ bằng số liệu thật: lớp/học sinh/giáo viên đang hoạt động, tỷ lệ có mặt 30 ngày, buổi học
 * hôm nay kèm trạng thái nhận lớp, giáo viên cần chú ý và các việc chờ duyệt. Lọc theo điểm trường trên Header.
 */
export default function AcademicDashboard() {
  const { t } = useTranslation("dashboard");
  const { t: tReports } = useTranslation("academic-oversight");
  const { selectedCampusId, hasPermission } = useApp();
  const canViewReports = hasPermission("report.session-report.view");
  const siteId = selectedCampusId !== "ALL" ? Number(selectedCampusId) : undefined;

  const [data, setData] = useState<AcademicDashboardResponse | null>(null);
  const [pending, setPending] = useState<PendingCounts | null>(null);
  const [reports, setReports] = useState<ReportCounts | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setError(null);
    getAcademicDashboard(siteId)
      .then((res) => {
        if (!cancelled) setData(res);
      })
      .catch((err) => {
        if (cancelled) return;
        setData(null);
        setError(err instanceof ApiError ? err.message : t("academic.loadFailed"));
      });

    // Việc chờ duyệt dùng lại các API hàng chờ sẵn có; lọc theo lớp thuộc điểm trường đang chọn.
    Promise.all([
      listUnpublishedGrades().catch(() => []),
      listPendingComments().catch(() => []),
      listPendingLeaveRequestsForApprover().catch(() => []),
      siteId ? listClasses({ siteId }).catch(() => []) : Promise.resolve(null)
    ]).then(([grades, comments, leaves, siteClasses]) => {
      if (cancelled) return;
      const classIds = siteClasses ? new Set(siteClasses.map((c) => c.id)) : null;
      setPending({
        grades: classIds ? grades.filter((g) => classIds.has(g.classId)).length : grades.length,
        comments: classIds ? comments.filter((c) => classIds.has(c.classId)).length : comments.length,
        leaves: leaves.length
      });
    });

    if (canViewReports) {
      const today = new Date();
      const weekAgo = new Date();
      weekAgo.setDate(today.getDate() - 6);
      getSessionReportTracking({ siteId, fromDate: toISODate(weekAgo), toDate: toISODate(today) })
        .then((res) => {
          if (cancelled) return;
          setReports({
            missing: res.sessions.filter((r) => r.submitState === "MISSING").length,
            approvalOverdue: res.sessions.filter((r) => r.approvalState === "OVERDUE").length,
            resubmitOverdue: res.sessions.filter((r) => r.resubmitState === "OVERDUE").length
          });
        })
        .catch(() => {
          if (!cancelled) setReports(null);
        });
    }

    return () => {
      cancelled = true;
    };
  }, [siteId, t, canViewReports]);

  const sessions = data?.todaySessions ?? [];
  const countState = (state: DashboardCheckInState) => sessions.filter((s) => s.checkInState === state).length;
  const activeSessionCount = sessions.filter((s) => s.checkInState !== "CANCELLED").length;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("academic.title")}</h1>
        <p className="text-xs text-slate-500 mt-1">{t("academic.subtitle")}</p>
      </div>

      {error && <div className="bg-rose-50 border border-rose-200/80 rounded-xl p-4 text-rose-700 text-sm">{error}</div>}

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <StatCard
          icon={GraduationCap}
          label={t("academic.inProgressClasses")}
          value={data ? String(data.inProgressClasses) : "—"}
          hint={data ? t("academic.upcomingClasses", { open: data.openEnrollmentClasses, planned: data.plannedClasses }) : undefined}
        />
        <StatCard icon={Users} tone="slate" label={t("academic.activeStudents")} value={data ? String(data.activeStudents) : "—"} />
        <StatCard icon={UserRound} tone="slate" label={t("academic.activeTeachers")} value={data ? String(data.activeTeachers) : "—"} />
        <StatCard
          icon={ClipboardCheck}
          tone={data?.attendanceRate != null && data.attendanceRate < 85 ? "warning" : "slate"}
          label={t("academic.attendanceRate")}
          value={data?.attendanceRate != null ? `${data.attendanceRate}%` : t("academic.noAttendance")}
          hint={data && data.attendanceTotalMarks > 0
            ? t("academic.attendanceHint", { absent: data.attendanceAbsentCount, excused: data.attendanceExcusedCount })
            : undefined}
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card className="lg:col-span-2">
          <div className="flex items-start justify-between gap-3 mb-3">
            <div>
              <h3 className="text-sm font-bold text-slate-800 font-display">{t("academic.todayTitle")}</h3>
              <p className="text-[11px] text-slate-500 mt-0.5">
                {t("academic.todaySummary", {
                  total: activeSessionCount,
                  onTime: countState("ON_TIME"),
                  late: countState("LATE"),
                  missing: countState("MISSING")
                })}
              </p>
            </div>
            <CalendarCheck className="w-5 h-5 text-slate-300" />
          </div>
          {sessions.length === 0 ? (
            <p className="text-xs text-slate-400 py-6 text-center">{t("academic.noSessionsToday")}</p>
          ) : (
            <div className="divide-y divide-slate-100 max-h-[360px] overflow-y-auto pr-1">
              {sessions.map((s) => (
                <div key={s.sessionId} className="py-2.5 flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-xs font-bold text-slate-800 truncate">
                      {s.startTime}–{s.endTime} · {s.className}
                    </p>
                    <p className="text-[11px] text-slate-500 truncate">{s.teacherName} · {s.siteName}</p>
                  </div>
                  <Badge variant={CHECK_IN_BADGES[s.checkInState]}>{t(`academic.checkInStates.${s.checkInState}`)}</Badge>
                </div>
              ))}
            </div>
          )}
        </Card>

        <div className="space-y-6">
          {canViewReports && (
            <Card>
              <div className="flex items-start justify-between gap-2">
                <h3 className="text-sm font-bold text-slate-800 font-display">{tReports("sessionReports.dashboardTitle")}</h3>
                <FileCheck2 className="w-5 h-5 text-slate-300 shrink-0" />
              </div>
              <p className={`text-xs mt-2 ${reports && reports.missing + reports.approvalOverdue + reports.resubmitOverdue > 0 ? "text-rose-600 font-semibold" : "text-slate-500"}`}>
                {!reports
                  ? "—"
                  : reports.missing + reports.approvalOverdue + reports.resubmitOverdue === 0
                    ? tReports("sessionReports.dashboardNone")
                    : tReports("sessionReports.dashboardSummary", { ...reports })}
              </p>
              <Link to="/reports/session-reports" className="inline-block text-[11px] font-semibold text-brand-red hover:underline mt-3">
                {tReports("sessionReports.dashboardView")}
              </Link>
            </Card>
          )}

          <Card>
            <h3 className="text-sm font-bold text-slate-800 font-display mb-3">{t("academic.pendingTitle")}</h3>
            <div className="space-y-2">
              {[
                { label: t("academic.pendingGrades"), value: pending?.grades, to: "/academic/grades" },
                { label: t("academic.pendingComments"), value: pending?.comments, to: "/academic/comments" },
                { label: t("academic.pendingLeaves"), value: pending?.leaves, to: "/hrm/leaves" }
              ].map((row) => (
                <Link key={row.to} to={row.to} className="flex items-center justify-between rounded-lg px-3 py-2 bg-slate-50 hover:bg-slate-100 transition-colors">
                  <span className="text-xs text-slate-600">{row.label}</span>
                  <span className={`text-sm font-bold ${row.value ? "text-brand-red" : "text-slate-400"}`}>{row.value ?? "—"}</span>
                </Link>
              ))}
            </div>
          </Card>

          <Card>
            <div className="flex items-start justify-between gap-2 mb-3">
              <div>
                <h3 className="text-sm font-bold text-slate-800 font-display">{t("academic.teacherAlertsTitle")}</h3>
                <p className="text-[11px] text-slate-500 mt-0.5">{t("academic.teacherAlertsHint")}</p>
              </div>
              <AlertTriangle className="w-5 h-5 text-amber-400 shrink-0" />
            </div>
            {!data || data.teacherAlerts.length === 0 ? (
              <p className="text-xs text-slate-400 py-3">{t("academic.noTeacherAlerts")}</p>
            ) : (
              <div className="divide-y divide-slate-100">
                {data.teacherAlerts.map((r) => (
                  <div key={r.teacherUserId ?? r.teacherName} className="py-2">
                    <p className="text-xs font-semibold text-slate-800">{r.teacherName}</p>
                    <p className="text-[11px] text-slate-500">
                      {t("academic.teacherAlertLine", { late: r.lateCheckIns, missing: r.missingCheckIns, held: r.heldSessions })}
                    </p>
                  </div>
                ))}
              </div>
            )}
            <Link to="/reports/teaching-stats" className="inline-block text-[11px] font-semibold text-brand-red hover:underline mt-3">
              {t("academic.viewTeachingStats")}
            </Link>
          </Card>
        </div>
      </div>
    </div>
  );
}
