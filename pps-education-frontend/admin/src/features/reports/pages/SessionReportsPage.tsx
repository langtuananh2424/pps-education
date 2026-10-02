import React, { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { Download, FileCheck2, History, Info } from "lucide-react";
import { useApp } from "@/context/AppContext";
import { ApiError } from "@/lib/apiClient";
import { toISODate } from "@/lib/calendarDates";
import { toLocaleTag } from "@/lib/i18nFormat";
import { downloadBlob } from "@/lib/xlsxTemplate";
import { useToast } from "@/lib/useToast";
import { cn } from "@/lib/cn";
import Toast from "@/components/ui/Toast";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Modal from "@/components/ui/Modal";
import Select from "@/components/ui/Select";
import Tabs from "@/components/ui/Tabs";
import DatePicker from "@/components/ui/DatePicker";
import TableContainer, { Th, Td } from "@/components/ui/TableContainer";
import EmptyState from "@/components/ui/EmptyState";
import {
  SessionReportFlowState,
  SessionReportStatusRow,
  SessionReportSubmitState,
  SessionReportTimelineEvent,
  SessionReportTrackingResponse,
  exportSessionReportTracking,
  getSessionReportTimeline,
  getSessionReportTracking
} from "@/features/academic/oversightApi";

type TabId = "sessions" | "teachers" | "approvers";
type RowFilter = "all" | "problems" | "missing" | "approvalOverdue" | "resubmitOverdue";

const STATE_BADGES: Record<SessionReportSubmitState | SessionReportFlowState, BadgeVariant> = {
  NOT_DUE: "neutral",
  NONE: "neutral",
  WAITING: "info",
  ON_TIME: "success",
  LATE: "warning",
  MISSING: "danger",
  OVERDUE: "danger"
};

function daysAgoIso(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return toISODate(d);
}

function hasProblem(r: SessionReportStatusRow): boolean {
  return (
    r.submitState === "LATE" ||
    r.submitState === "MISSING" ||
    r.approvalState === "LATE" ||
    r.approvalState === "OVERDUE" ||
    r.resubmitState === "LATE" ||
    r.resubmitState === "OVERDUE"
  );
}

/**
 * Trang "Tình hình nộp & duyệt báo cáo" (V207 — bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-10-01):
 * báo cáo buổi học = giáo viên gửi duyệt nhận xét của buổi. Theo dõi 3 khâu (giáo viên nộp / Quản lý điểm
 * trường duyệt / giáo viên gửi lại khi bị từ chối) theo hạn trong Cài đặt hệ thống; tổng hợp theo giáo viên và
 * theo người duyệt; dòng thời gian từng buổi. ?sessionId= (từ thông báo) mở sẵn dòng thời gian của buổi đó.
 */
export default function SessionReportsPage() {
  const { t, i18n } = useTranslation("academic-oversight");
  const { selectedCampusId } = useApp();
  const [searchParams, setSearchParams] = useSearchParams();
  const siteId = selectedCampusId !== "ALL" ? Number(selectedCampusId) : undefined;

  const [fromDate, setFromDate] = useState<string>(daysAgoIso(6));
  const [toDate, setToDate] = useState<string>(toISODate(new Date()));
  const [tab, setTab] = useState<TabId>("sessions");
  const [rowFilter, setRowFilter] = useState<RowFilter>("all");
  const [data, setData] = useState<SessionReportTrackingResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const { message: toastMsg, showToast } = useToast();

  const [timelineSession, setTimelineSession] = useState<{ sessionId: number; className: string } | null>(null);
  const [timeline, setTimeline] = useState<SessionReportTimelineEvent[] | null>(null);
  const [timelineError, setTimelineError] = useState<string | null>(null);

  const locale = toLocaleTag(i18n.language);
  const formatDateTime = (value: string | null) =>
    value ? new Date(value).toLocaleString(locale, { hour: "2-digit", minute: "2-digit", day: "2-digit", month: "2-digit" }) : "—";
  const formatDay = (value: string) => new Date(`${value}T00:00:00`).toLocaleDateString(locale, { day: "2-digit", month: "2-digit", year: "numeric" });
  const formatDuration = (minutes: number) => {
    if (minutes < 60) return t("sessionReports.minutes", { count: minutes });
    if (minutes < 60 * 24) return t("sessionReports.hours", { hours: Math.floor(minutes / 60), minutes: minutes % 60 });
    return t("sessionReports.days", { days: Math.floor(minutes / 1440), hours: Math.floor((minutes % 1440) / 60) });
  };

  useEffect(() => {
    if (!fromDate || !toDate) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    getSessionReportTracking({ siteId, fromDate, toDate })
      .then((res) => {
        if (!cancelled) setData(res);
      })
      .catch((err) => {
        if (cancelled) return;
        setData(null);
        setError(err instanceof ApiError ? err.message : t("sessionReports.loadFailed"));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [siteId, fromDate, toDate, t]);

  // Mở dòng thời gian từ thông báo (?sessionId=) — không cần biết ngày của buổi.
  useEffect(() => {
    const sessionIdParam = searchParams.get("sessionId");
    if (sessionIdParam) {
      setTimelineSession({ sessionId: Number(sessionIdParam), className: "" });
    }
  }, [searchParams]);

  useEffect(() => {
    if (!timelineSession) return;
    setTimeline(null);
    setTimelineError(null);
    getSessionReportTimeline(timelineSession.sessionId)
      .then(setTimeline)
      .catch((err) => setTimelineError(err instanceof ApiError ? err.message : t("sessionReports.timelineLoadFailed")));
  }, [timelineSession, t]);

  const closeTimeline = () => {
    setTimelineSession(null);
    if (searchParams.has("sessionId")) {
      searchParams.delete("sessionId");
      setSearchParams(searchParams, { replace: true });
    }
  };

  const sessions = data?.sessions ?? [];
  const filteredSessions = useMemo(() => {
    switch (rowFilter) {
      case "problems":
        return sessions.filter(hasProblem);
      case "missing":
        return sessions.filter((r) => r.submitState === "MISSING");
      case "approvalOverdue":
        return sessions.filter((r) => r.approvalState === "OVERDUE");
      case "resubmitOverdue":
        return sessions.filter((r) => r.resubmitState === "OVERDUE");
      default:
        return sessions;
    }
  }, [sessions, rowFilter]);

  const summary = useMemo(
    () => ({
      sessions: sessions.length,
      onTime: sessions.filter((r) => r.submitState === "ON_TIME").length,
      late: sessions.filter((r) => r.submitState === "LATE").length,
      missing: sessions.filter((r) => r.submitState === "MISSING").length,
      approvalLate: sessions.filter((r) => r.approvalState === "LATE" || r.approvalState === "OVERDUE").length,
      resubmitLate: sessions.filter((r) => r.resubmitState === "LATE" || r.resubmitState === "OVERDUE").length
    }),
    [sessions]
  );

  const handleExport = async () => {
    setExporting(true);
    try {
      const blob = await exportSessionReportTracking({ siteId, fromDate, toDate });
      downloadBlob(blob, `nop-duyet-bao-cao-${fromDate}-${toDate}.xlsx`);
      showToast(t("sessionReports.exportSuccess"));
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : t("sessionReports.exportFailed"));
    } finally {
      setExporting(false);
    }
  };

  const stateBadge = (state: SessionReportSubmitState | SessionReportFlowState, lateMinutes: number) => (
    <div className="space-y-0.5">
      <Badge variant={STATE_BADGES[state]}>{t(`sessionReports.states.${state}`)}</Badge>
      {lateMinutes > 0 && (state === "LATE" || state === "OVERDUE" || state === "MISSING") && (
        <p className="text-[12px] text-rose-600">{t("sessionReports.lateBy", { duration: formatDuration(lateMinutes) })}</p>
      )}
    </div>
  );

  const summaryTiles: { key: keyof typeof summary; tone: string }[] = [
    { key: "sessions", tone: "text-slate-800" },
    { key: "onTime", tone: "text-emerald-600" },
    { key: "late", tone: "text-amber-600" },
    { key: "missing", tone: "text-rose-600" },
    { key: "approvalLate", tone: "text-amber-600" },
    { key: "resubmitLate", tone: "text-amber-600" }
  ];

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4 flex items-start justify-between gap-3 flex-wrap">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("sessionReports.title")}</h1>
          <p className="text-sm text-slate-500 mt-1 max-w-3xl">{t("sessionReports.description")}</p>
        </div>
        <Button variant="primary" onClick={handleExport} disabled={exporting || loading || sessions.length === 0}>
          <Download className="w-3.5 h-3.5" /> {exporting ? t("sessionReports.exporting") : t("sessionReports.export")}
        </Button>
      </div>

      <Card>
        <div className="flex flex-wrap items-end gap-3">
          <div className="w-[180px]">
            <label className="block text-sm text-slate-500 mb-1">{t("sessionReports.fromDate")}</label>
            <DatePicker value={fromDate} onChange={setFromDate} max={toDate || undefined} />
          </div>
          <div className="w-[180px]">
            <label className="block text-sm text-slate-500 mb-1">{t("sessionReports.toDate")}</label>
            <DatePicker value={toDate} onChange={setToDate} min={fromDate || undefined} />
          </div>
          <div className="w-[220px]">
            <Select
              value={rowFilter}
              onChange={(e) => setRowFilter(e.target.value as RowFilter)}
              className="w-full border border-slate-300 rounded-lg text-sm p-2 focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
            >
              {(["all", "problems", "missing", "approvalOverdue", "resubmitOverdue"] as RowFilter[]).map((f) => (
                <option key={f} value={f}>{t(`sessionReports.filters.${f}`)}</option>
              ))}
            </Select>
          </div>
          <p className="text-sm text-slate-500 pb-2">{data?.siteName ?? t("sessionReports.allSites")}</p>
        </div>
        {data && (
          <p className="flex items-start gap-1.5 text-[13px] text-slate-500 mt-3">
            <Info className="w-3 h-3 mt-0.5 shrink-0" />
            {t("sessionReports.deadlines", {
              submitHours: data.submitDeadlineHours,
              approvalHours: data.approvalDeadlineHours,
              resubmitHours: data.resubmitDeadlineHours
            })}
          </p>
        )}
      </Card>

      {error && <div className="bg-rose-50 border border-rose-200/80 rounded-xl p-4 text-rose-700 text-sm">{error}</div>}

      <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-6 gap-3">
        {summaryTiles.map(({ key, tone }) => (
          <Card key={key} padded={false} className="p-4">
            <p className="text-[13px] text-slate-500">{t(`sessionReports.summary.${key}`)}</p>
            <p className={cn("text-2xl font-bold font-display mt-1", tone)}>{data ? summary[key] : "—"}</p>
          </Card>
        ))}
      </div>

      <Tabs
        items={(["sessions", "teachers", "approvers"] as TabId[]).map((id) => ({ id, label: t(`sessionReports.tabs.${id}`) }))}
        activeId={tab}
        onChange={(id) => setTab(id as TabId)}
      />

      <Card padded={false} className="overflow-hidden">
        {loading && !data ? (
          <div className="py-16 text-center text-slate-300"><FileCheck2 className="w-12 h-12 mx-auto animate-pulse" /></div>
        ) : !data || sessions.length === 0 ? (
          <EmptyState icon={FileCheck2} title={t("sessionReports.empty")} />
        ) : tab === "sessions" ? (
          <TableContainer className={cn("rounded-none border-0", loading && "opacity-60")}>
            <thead>
              <tr>
                <Th>{t("sessionReports.columns.session")}</Th>
                <Th>{t("sessionReports.columns.teacher")}</Th>
                <Th>{t("sessionReports.columns.submit")}</Th>
                <Th>{t("sessionReports.columns.approval")}</Th>
                <Th>{t("sessionReports.columns.resubmit")}</Th>
                <Th />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredSessions.map((r) => (
                <tr key={r.sessionId} className="align-top hover:bg-slate-50/50">
                  <Td>
                    <p className="font-semibold text-slate-800">{r.className}</p>
                    <p className="text-[13px] text-slate-500">
                      {formatDay(r.sessionDate)} · {r.startTime.slice(0, 5)}–{r.endTime.slice(0, 5)} · {r.siteName}
                    </p>
                  </Td>
                  <Td className="whitespace-nowrap">{r.teacherName}</Td>
                  <Td>
                    {stateBadge(r.submitState, r.submitLateMinutes)}
                    <p className="text-[12px] text-slate-400 mt-0.5">
                      {r.firstSubmittedAt
                        ? formatDateTime(r.firstSubmittedAt)
                        : t("sessionReports.deadlineAt", { deadline: formatDateTime(r.submitDeadline) })}
                    </p>
                  </Td>
                  <Td>
                    {stateBadge(r.approvalState, r.approvalLateMinutes)}
                    {r.approverNames.length > 0 && <p className="text-[12px] text-slate-400 mt-0.5">{r.approverNames.join(", ")}</p>}
                  </Td>
                  <Td>
                    {stateBadge(r.resubmitState, r.resubmitLateMinutes)}
                    {r.rejectionCount > 0 && (
                      <p className="text-[12px] text-slate-400 mt-0.5">{t("sessionReports.rejectedTimes", { count: r.rejectionCount })}</p>
                    )}
                  </Td>
                  <Td className="text-right">
                    <button
                      onClick={() => setTimelineSession({ sessionId: r.sessionId, className: r.className })}
                      className="inline-flex items-center gap-1 text-[13px] font-semibold text-brand-red hover:underline whitespace-nowrap"
                    >
                      <History className="w-3.5 h-3.5" /> {t("sessionReports.viewTimeline")}
                    </button>
                  </Td>
                </tr>
              ))}
            </tbody>
          </TableContainer>
        ) : tab === "teachers" ? (
          <TableContainer className="rounded-none border-0">
            <thead>
              <tr>
                <Th>{t("sessionReports.columns.teacher")}</Th>
                <Th className="text-right">{t("sessionReports.columns.sessions")}</Th>
                <Th className="text-right">{t("sessionReports.columns.onTime")}</Th>
                <Th className="text-right">{t("sessionReports.columns.late")}</Th>
                <Th className="text-right">{t("sessionReports.columns.missing")}</Th>
                <Th className="text-right">{t("sessionReports.columns.rejections")}</Th>
                <Th className="text-right">{t("sessionReports.columns.resubmitLate")}</Th>
                <Th className="text-right">{t("sessionReports.columns.onTimeRate")}</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.teachers.map((r) => (
                <tr key={r.teacherUserId}>
                  <Td className="font-semibold text-slate-800">{r.teacherName}</Td>
                  <Td className="text-right">{r.sessionCount}</Td>
                  <Td className="text-right text-emerald-600">{r.onTimeCount}</Td>
                  <Td className={cn("text-right", r.lateCount > 0 && "text-amber-600")}>{r.lateCount}</Td>
                  <Td className={cn("text-right", r.missingCount > 0 && "text-rose-600")}>{r.missingCount}</Td>
                  <Td className="text-right">{r.rejectionCount}</Td>
                  <Td className={cn("text-right", r.resubmitLateCount > 0 && "text-amber-600")}>{r.resubmitLateCount}</Td>
                  <Td className="text-right font-semibold">{r.onTimeRate == null ? "—" : `${r.onTimeRate}%`}</Td>
                </tr>
              ))}
            </tbody>
          </TableContainer>
        ) : data.approvers.length === 0 ? (
          <EmptyState icon={FileCheck2} title={t("sessionReports.empty")} />
        ) : (
          <TableContainer className="rounded-none border-0">
            <thead>
              <tr>
                <Th>{t("sessionReports.columns.approver")}</Th>
                <Th className="text-right">{t("sessionReports.columns.decided")}</Th>
                <Th className="text-right">{t("sessionReports.columns.approvalLate")}</Th>
                <Th className="text-right">{t("sessionReports.columns.rejected")}</Th>
                <Th className="text-right">{t("sessionReports.columns.avgWait")}</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.approvers.map((r) => (
                <tr key={r.approverUserId}>
                  <Td className="font-semibold text-slate-800">{r.approverName}</Td>
                  <Td className="text-right">{r.decidedSessionCount}</Td>
                  <Td className={cn("text-right", r.lateSessionCount > 0 && "text-amber-600")}>{r.lateSessionCount}</Td>
                  <Td className="text-right">{r.rejectedSessionCount}</Td>
                  <Td className="text-right">{r.averageWaitMinutes == null ? "—" : formatDuration(r.averageWaitMinutes)}</Td>
                </tr>
              ))}
            </tbody>
          </TableContainer>
        )}
      </Card>

      <Modal
        open={timelineSession != null}
        onClose={closeTimeline}
        title={t("sessionReports.timelineTitle", { className: timelineSession?.className || "" })}
        size="lg"
      >
        {timelineError ? (
          <div className="bg-rose-50 border border-rose-200/80 rounded-xl p-4 text-rose-700 text-sm">{timelineError}</div>
        ) : timeline == null ? (
          <div className="py-10 text-center text-slate-300"><History className="w-10 h-10 mx-auto animate-pulse" /></div>
        ) : timeline.length === 0 ? (
          <EmptyState icon={History} title={t("sessionReports.timelineEmpty")} />
        ) : (
          <ol className="relative border-l-2 border-slate-200 ml-2 space-y-4">
            {timeline.map((ev, index) => (
              <li key={`${ev.type}-${ev.at}-${index}`} className="ml-4">
                <span
                  className={cn(
                    "absolute -left-[7px] mt-1 w-3 h-3 rounded-full border-2 border-white",
                    ev.type === "APPROVED" ? "bg-emerald-500" : ev.type === "REJECTED" ? "bg-rose-500" : "bg-sky-500"
                  )}
                />
                <p className="text-sm font-semibold text-slate-800">
                  {t(`sessionReports.events.${ev.type}`, { actor: ev.actorName ?? "—", count: ev.commentCount })}
                </p>
                <p className="text-[13px] text-slate-500">{formatDateTime(ev.at)}</p>
                {ev.timeliness && (
                  <p className={cn("text-[13px]", ev.timeliness === "LATE" ? "text-amber-700" : "text-emerald-700")}>
                    {ev.timeliness === "LATE"
                      ? t("sessionReportEvent.late", { duration: formatDuration(ev.lateMinutes), deadline: formatDateTime(ev.deadline) })
                      : t("sessionReportEvent.onTime", { deadline: formatDateTime(ev.deadline) })}
                  </p>
                )}
                {ev.reason && <p className="text-[13px] text-slate-600 italic">{t("sessionReports.reason", { reason: ev.reason })}</p>}
              </li>
            ))}
          </ol>
        )}
      </Modal>

      <Toast message={toastMsg} />
    </div>
  );
}
