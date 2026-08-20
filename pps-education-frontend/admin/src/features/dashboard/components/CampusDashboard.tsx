import React from "react";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { AlertTriangle, ArrowRight, ClipboardCheck, FileWarning, GraduationCap, Inbox, School, Wallet } from "lucide-react";
import Card from "@/components/ui/Card";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import StatCard from "@/components/ui/StatCard";
import TableContainer, { Th, Td } from "@/components/ui/TableContainer";
import { PartnerFeedbackPriority, PartnerFeedbackStatus } from "@/features/facility/api";
import { useSiteManagerDashboardData } from "../hooks/useSiteManagerDashboardData";

/** Header khối — theo đúng pattern đã dùng ở TeacherDashboard.tsx cho đồng bộ trực quan giữa các dashboard. */
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

function InlineEmpty({ icon: Icon, title, description }: { icon: React.ComponentType<{ className?: string }>; title: string; description?: string }) {
  return (
    <div className="flex flex-col items-center justify-center text-center py-8 px-4">
      <div className="w-10 h-10 rounded-full flex items-center justify-center mb-2.5 bg-slate-100 text-slate-400">
        <Icon className="w-5 h-5" />
      </div>
      <p className="text-xs font-bold text-slate-600">{title}</p>
      {description && <p className="text-xs text-slate-400 mt-1 max-w-xs">{description}</p>}
    </div>
  );
}

const feedbackPriorityVariant: Record<PartnerFeedbackPriority, BadgeVariant> = {
  URGENT: "danger",
  HIGH: "warning",
  NORMAL: "info",
  LOW: "neutral"
};

const feedbackStatusVariant: Record<PartnerFeedbackStatus, BadgeVariant> = {
  NEW: "danger",
  IN_PROGRESS: "warning",
  RESOLVED: "success",
  CLOSED: "neutral"
};

/**
 * Dashboard Quản lý điểm trường — map dữ liệu thật từ các API tự-phục-vụ đã có sẵn (xem
 * useSiteManagerDashboardData.ts). Trước đây toàn bộ 4 KPI + hòm thư đều dùng mockCampuses/
 * mockFeedbackTickets — đã thay bằng dữ liệu thật, phân tích + xác nhận với người dùng 2026-08-08.
 * Ô "Công nợ học phí cơ sở" giữ lại vị trí nhưng để trạng thái "chưa có dữ liệu" vì backend chưa có
 * endpoint báo cáo Thu/Chi/Công nợ theo điểm trường (FR-FIN-04) — không tự suy diễn số liệu
 * (business-fidelity.md), đã báo lại gap này cho người dùng thay vì âm thầm bỏ qua.
 */
export default function CampusDashboard() {
  const { t } = useTranslation("dashboard");
  const navigate = useNavigate();
  const { data, loading } = useSiteManagerDashboardData();

  const goToFeedback = () => navigate("/facility/feedback");

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("campus.title")}</h1>
        <p className="text-xs text-slate-500 mt-1">{t("campus.subtitle")}</p>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
        <StatCard
          icon={Inbox}
          label={t("campus.kpi.newFeedback")}
          value={String(data.newFeedbackCount)}
          tone={data.newFeedbackCount > 0 ? "warning" : "slate"}
          hint={t("campus.kpi.newFeedbackHint", { count: data.totalFeedbackCount })}
        />
        <StatCard
          icon={School}
          label={t("campus.kpi.activeContracts")}
          value={String(data.activeContractsCount)}
          tone="slate"
          hint={t("campus.kpi.activeContractsHint", { count: data.mySites.length })}
        />
        <StatCard
          icon={AlertTriangle}
          label={t("campus.kpi.attitudeWarning")}
          value={String(data.pendingWarningCommentCount)}
          tone={data.pendingWarningCommentCount > 0 ? "danger" : "slate"}
          hint={t("campus.kpi.attitudeWarningHint")}
        />
        <StatCard
          icon={ClipboardCheck}
          label={t("campus.kpi.pendingApproval")}
          value={String(data.pendingGradeCount + data.pendingLeaveCount)}
          tone={data.pendingGradeCount + data.pendingLeaveCount > 0 ? "warning" : "slate"}
          hint={t("campus.kpi.pendingApprovalHint", { grades: data.pendingGradeCount, leaves: data.pendingLeaveCount })}
        />
        <StatCard icon={Wallet} label={t("campus.kpi.tuitionDebt")} value="—" tone="slate" hint={t("campus.kpi.tuitionDebtHint")} />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
        <Card padded={false} className="overflow-hidden">
          <SectionHead icon={ClipboardCheck} title={t("campus.pendingApproval.title")} subtitle={t("campus.pendingApproval.subtitle")} />
          <div className="divide-y divide-slate-100">
            {data.pendingGradeCount > 0 && (
              <div className="p-4 flex items-start gap-3">
                <span className="w-2 h-2 rounded-full bg-amber-500 mt-2 shrink-0" />
                <div className="min-w-0">
                  <p className="text-xs font-bold text-slate-800">{t("campus.pendingApproval.pendingGrades", { count: data.pendingGradeCount })}</p>
                  <p className="text-xs text-slate-400 mt-0.5">{t("campus.pendingApproval.pendingGradesDesc")}</p>
                </div>
              </div>
            )}
            {data.pendingLeaveRequests.map((r) => (
              <div key={r.id} className="p-4 flex items-start gap-3">
                <span className="w-2 h-2 rounded-full bg-sky-500 mt-2 shrink-0" />
                <div className="min-w-0 flex-1">
                  <p className="text-xs font-bold text-slate-800 truncate">
                    {t("campus.pendingApproval.leaveDaysLabel", { name: r.employeeFullName, days: r.totalDays })}
                  </p>
                  <p className="text-xs text-slate-400 mt-0.5">
                    {r.startDate} → {r.endDate} {r.departmentName ? `· ${r.departmentName}` : ""}
                  </p>
                </div>
              </div>
            ))}
            {data.pendingGradeCount === 0 && data.pendingLeaveRequests.length === 0 && !loading && (
              <InlineEmpty icon={ClipboardCheck} title={t("campus.pendingApproval.emptyTitle")} description={t("campus.pendingApproval.emptyDescription")} />
            )}
          </div>
        </Card>

        <Card padded={false} className="overflow-hidden">
          <SectionHead icon={FileWarning} title={t("campus.expiringContracts.title")} subtitle={t("campus.expiringContracts.subtitle")} />
          <div className="divide-y divide-slate-100">
            {data.expiringContracts.map((c) => (
              <div key={c.contractId} className="p-4 flex items-start gap-3">
                <span className="w-2 h-2 rounded-full bg-rose-500 mt-2 shrink-0" />
                <div className="min-w-0 flex-1">
                  <p className="text-xs font-bold text-slate-800 truncate">
                    {c.siteName} · {c.contractNumber}
                  </p>
                  <p className="text-xs text-slate-400 mt-0.5">{t("campus.expiringContracts.expiresOn", { date: c.endDate })}</p>
                </div>
              </div>
            ))}
            {data.expiringContracts.length === 0 && !loading && (
              <InlineEmpty icon={FileWarning} title={t("campus.expiringContracts.emptyTitle")} />
            )}
          </div>
        </Card>
      </div>

      <Card padded={false} className="overflow-hidden">
        <SectionHead
          icon={GraduationCap}
          title={t("campus.activeClasses.title")}
          subtitle={t("campus.activeClasses.subtitle", {
            classes: data.totalActiveClasses,
            students: data.totalStudents,
            percent: data.avgCompletionPercent
          })}
          action={{ label: t("campus.activeClasses.viewClasses"), onClick: () => navigate("/academic/classes") }}
        />
        {data.myClasses.length > 0 ? (
          <TableContainer className="border-0 rounded-none">
            <thead>
              <tr>
                <Th>{t("campus.activeClasses.columns.class")}</Th>
                <Th>{t("campus.activeClasses.columns.site")}</Th>
                <Th>{t("campus.activeClasses.columns.size")}</Th>
                <Th>{t("campus.activeClasses.columns.completion")}</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.myClasses.map((c) => (
                <tr key={c.classId}>
                  <Td className="font-bold text-xs text-slate-800">{c.className}</Td>
                  <Td>{c.siteName}</Td>
                  <Td className="font-mono">{c.studentCount}</Td>
                  <Td>
                    <div className="flex items-center gap-2">
                      <div className="w-20 h-1.5 rounded-full bg-slate-100 overflow-hidden shrink-0">
                        <div className="h-full bg-brand-gradient rounded-full" style={{ width: `${c.completionPercent}%` }} />
                      </div>
                      <span className="font-mono font-bold text-slate-700">{c.completionPercent}%</span>
                    </div>
                  </Td>
                </tr>
              ))}
            </tbody>
          </TableContainer>
        ) : (
          !loading && <InlineEmpty icon={GraduationCap} title={t("campus.activeClasses.emptyTitle")} />
        )}
      </Card>

      <Card>
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-bold text-slate-800 font-display">{t("campus.feedbackInbox.title")}</h3>
          <button onClick={goToFeedback} className="text-xs text-brand-orange hover:text-brand-red font-bold">
            {t("campus.feedbackInbox.viewAll")}
          </button>
        </div>
        {data.feedbackInbox.length > 0 ? (
          <div className="space-y-4">
            {data.feedbackInbox.map((tkt) => (
              <div
                key={tkt.id}
                className="p-3.5 bg-slate-50 rounded-lg border border-slate-100 flex flex-col md:flex-row md:items-center justify-between gap-4"
              >
                <div className="space-y-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-xs font-bold text-slate-800">{tkt.siteName}</span>
                    <Badge variant={feedbackPriorityVariant[tkt.priority]}>{t(`campus.feedbackPriority.${tkt.priority}`)}</Badge>
                  </div>
                  <p className="text-xs text-slate-600 line-clamp-2">{tkt.content}</p>
                </div>

                <div className="flex items-center gap-3 shrink-0">
                  <Badge variant={feedbackStatusVariant[tkt.status]}>{t(`campus.feedbackStatus.${tkt.status}`)}</Badge>
                  <button onClick={goToFeedback} className="p-1 rounded bg-white hover:bg-slate-100 text-slate-500 border border-slate-200">
                    <ArrowRight className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            ))}
          </div>
        ) : (
          !loading && <InlineEmpty icon={Inbox} title={t("campus.feedbackInbox.emptyTitle")} />
        )}
      </Card>
    </div>
  );
}
