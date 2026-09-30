import React, { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { ChevronLeft, ChevronRight, IdCard, Search, ShieldCheck } from "lucide-react";
import { useApp } from "@/context/AppContext";
import { ApiError } from "@/lib/apiClient";
import { toISODate } from "@/lib/calendarDates";
import { toLocaleTag } from "@/lib/i18nFormat";
import Card from "@/components/ui/Card";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import Tabs from "@/components/ui/Tabs";
import Avatar from "@/components/ui/Avatar";
import TableContainer, { Th, Td } from "@/components/ui/TableContainer";
import EmptyState from "@/components/ui/EmptyState";
import { getEmployeeTeachingSessions } from "@/features/hrm/api";
import type { ClassSessionResponse } from "../api";
import {
  TeacherProfileDetail,
  TeacherProfileSummary,
  getTeacherProfile,
  listTeacherProfiles
} from "../oversightApi";

const STATUS_BADGES: Record<TeacherProfileSummary["status"], BadgeVariant> = {
  ACTIVE: "success",
  ON_LEAVE: "warning",
  TERMINATED: "neutral"
};

const SCHEDULE_WINDOW_DAYS = 14;

type DetailTab = "info" | "qualifications" | "commendations" | "classes" | "schedule";

function addDays(iso: string, days: number): string {
  const d = new Date(`${iso}T00:00:00`);
  d.setDate(d.getDate() + days);
  return toISODate(d);
}

/**
 * Trang "Hồ sơ giáo viên" (V203 — bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-09-30, quyền
 * hrm.teacher.view): Trưởng phòng đào tạo xem thông tin công việc, bằng cấp, khen thưởng/kỷ luật, lớp phụ
 * trách và lịch dạy của giáo viên — không lộ CCCD/ngân hàng/hợp đồng/lương như trang Hồ sơ cán bộ.
 */
export default function TeacherProfilesPage() {
  const { t, i18n } = useTranslation("academic-oversight");
  const { selectedCampusId, hasPermission } = useApp();
  const canViewSchedule = hasPermission("hrm.employee-schedule.view");

  const [queryInput, setQueryInput] = useState("");
  const [query, setQuery] = useState("");
  const [teachers, setTeachers] = useState<TeacherProfileSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<TeacherProfileDetail | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<DetailTab>("info");

  const [scheduleFrom, setScheduleFrom] = useState<string>(toISODate(new Date()));
  const [sessions, setSessions] = useState<ClassSessionResponse[]>([]);
  const [loadingSessions, setLoadingSessions] = useState(false);

  const formatDate = (value: string | null) =>
    value ? new Date(`${value}T00:00:00`).toLocaleDateString(toLocaleTag(i18n.language)) : "—";

  useEffect(() => {
    const timer = setTimeout(() => setQuery(queryInput), 300);
    return () => clearTimeout(timer);
  }, [queryInput]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    listTeacherProfiles({ query, siteId: selectedCampusId !== "ALL" ? Number(selectedCampusId) : undefined })
      .then((list) => {
        if (!cancelled) setTeachers(list);
      })
      .catch((err) => {
        if (cancelled) return;
        setTeachers([]);
        setError(err instanceof ApiError ? err.message : t("teacherProfiles.loadFailed"));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [query, selectedCampusId, t]);

  useEffect(() => {
    if (selectedId == null) {
      setDetail(null);
      return;
    }
    setDetail(null);
    setDetailError(null);
    setActiveTab("info");
    setScheduleFrom(toISODate(new Date()));
    getTeacherProfile(selectedId)
      .then(setDetail)
      .catch((err) => setDetailError(err instanceof ApiError ? err.message : t("teacherProfiles.detailLoadFailed")));
  }, [selectedId, t]);

  const scheduleTo = useMemo(() => addDays(scheduleFrom, SCHEDULE_WINDOW_DAYS - 1), [scheduleFrom]);

  useEffect(() => {
    if (selectedId == null || activeTab !== "schedule" || !canViewSchedule) return;
    setLoadingSessions(true);
    getEmployeeTeachingSessions(selectedId, scheduleFrom, scheduleTo)
      .then((list) => setSessions([...list].sort((a, b) => `${a.sessionDate}${a.startTime}`.localeCompare(`${b.sessionDate}${b.startTime}`))))
      .catch(() => setSessions([]))
      .finally(() => setLoadingSessions(false));
  }, [selectedId, activeTab, canViewSchedule, scheduleFrom, scheduleTo]);

  const tabs = [
    { id: "info", label: t("teacherProfiles.tabs.info") },
    { id: "qualifications", label: t("teacherProfiles.tabs.qualifications") },
    { id: "commendations", label: t("teacherProfiles.tabs.commendations") },
    { id: "classes", label: t("teacherProfiles.tabs.classes") },
    { id: "schedule", label: t("teacherProfiles.tabs.schedule") }
  ];

  const renderInfo = (d: TeacherProfileDetail) => {
    const p = d.profile;
    const rows: [string, React.ReactNode][] = [
      [t("teacherProfiles.info.employeeCode"), p.employeeCode],
      [t("teacherProfiles.info.email"), p.email ?? "—"],
      [t("teacherProfiles.info.phone"), p.phone ?? "—"],
      [t("teacherProfiles.info.position"), p.positionName ?? "—"],
      [t("teacherProfiles.info.department"), p.departmentName ?? "—"],
      [t("teacherProfiles.info.hireDate"), formatDate(p.hireDate)],
      [t("teacherProfiles.info.sites"), p.siteNames.length ? p.siteNames.join(", ") : "—"],
      [t("teacherProfiles.info.status"), <Badge variant={STATUS_BADGES[p.status]}>{t(`teacherProfiles.status.${p.status}`)}</Badge>]
    ];
    return (
      <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-3 text-sm">
        {rows.map(([label, value]) => (
          <div key={label}>
            <dt className="text-[11px] text-slate-500">{label}</dt>
            <dd className="font-medium text-slate-800 mt-0.5">{value}</dd>
          </div>
        ))}
      </dl>
    );
  };

  const renderQualifications = (d: TeacherProfileDetail) =>
    d.qualifications.length === 0 ? (
      <EmptyState icon={IdCard} title={t("teacherProfiles.noQualifications")} />
    ) : (
      <TableContainer>
        <thead>
          <tr>
            <Th>{t("teacherProfiles.qualificationColumns.type")}</Th>
            <Th>{t("teacherProfiles.qualificationColumns.title")}</Th>
            <Th>{t("teacherProfiles.qualificationColumns.issuer")}</Th>
            <Th>{t("teacherProfiles.qualificationColumns.issuedDate")}</Th>
            <Th>{t("teacherProfiles.qualificationColumns.expiryDate")}</Th>
            <Th>{t("teacherProfiles.qualificationColumns.file")}</Th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {d.qualifications.map((q) => (
            <tr key={q.id}>
              <Td>{t(`teacherProfiles.qualificationTypes.${q.qualificationType}`)}</Td>
              <Td className="font-medium">{q.title}</Td>
              <Td>{q.issuer ?? "—"}</Td>
              <Td>{formatDate(q.issuedDate)}</Td>
              <Td>{formatDate(q.expiryDate)}</Td>
              <Td>
                {q.fileUrl ? (
                  <a href={q.fileUrl} target="_blank" rel="noreferrer" className="text-brand-red font-semibold hover:underline">
                    {t("teacherProfiles.viewFile")}
                  </a>
                ) : "—"}
              </Td>
            </tr>
          ))}
        </tbody>
      </TableContainer>
    );

  const renderCommendations = (d: TeacherProfileDetail) =>
    d.commendations.length === 0 ? (
      <EmptyState icon={IdCard} title={t("teacherProfiles.noCommendations")} />
    ) : (
      <TableContainer>
        <thead>
          <tr>
            <Th>{t("teacherProfiles.commendationColumns.type")}</Th>
            <Th>{t("teacherProfiles.commendationColumns.date")}</Th>
            <Th>{t("teacherProfiles.commendationColumns.title")}</Th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {d.commendations.map((c) => (
            <tr key={c.id}>
              <Td>
                <Badge variant={c.recordType === "COMMENDATION" ? "success" : "danger"}>
                  {t(`teacherProfiles.recordTypes.${c.recordType}`)}
                </Badge>
              </Td>
              <Td>{formatDate(c.recordDate)}</Td>
              <Td>{c.title}</Td>
            </tr>
          ))}
        </tbody>
      </TableContainer>
    );

  const renderClasses = (d: TeacherProfileDetail) =>
    d.classes.length === 0 ? (
      <EmptyState icon={IdCard} title={t("teacherProfiles.noClasses")} />
    ) : (
      <TableContainer>
        <thead>
          <tr>
            <Th>{t("teacherProfiles.classColumns.class")}</Th>
            <Th>{t("teacherProfiles.classColumns.site")}</Th>
            <Th>{t("teacherProfiles.classColumns.role")}</Th>
            <Th>{t("teacherProfiles.classColumns.from")}</Th>
            <Th>{t("teacherProfiles.classColumns.status")}</Th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {d.classes.map((c) => (
            <tr key={`${c.classId}-${c.teacherRole}-${c.teacherType ?? ""}`}>
              <Td>
                <p className="font-medium text-slate-800">{c.className}</p>
                <p className="text-[11px] text-slate-400">{c.classCode}</p>
              </Td>
              <Td>{c.siteName}</Td>
              <Td>
                {t(`values.${c.teacherRole}`, { defaultValue: c.teacherRole })}
                {c.teacherType && <span className="text-slate-400"> · {t(`values.${c.teacherType}`, { defaultValue: c.teacherType })}</span>}
              </Td>
              <Td>{formatDate(c.assignedFrom)}</Td>
              <Td>{t(`values.${c.classStatus}`, { defaultValue: c.classStatus })}</Td>
            </tr>
          ))}
        </tbody>
      </TableContainer>
    );

  const renderSchedule = () => {
    if (!canViewSchedule) {
      return <p className="text-xs text-slate-500 bg-slate-50 border border-slate-200 p-4 rounded-lg">{t("teacherProfiles.noSchedulePermission")}</p>;
    }
    return (
      <div className="space-y-3">
        <div className="flex items-center justify-between gap-2 flex-wrap">
          <Button size="sm" variant="secondary" onClick={() => setScheduleFrom(addDays(scheduleFrom, -SCHEDULE_WINDOW_DAYS))}>
            <ChevronLeft className="w-3.5 h-3.5" /> {t("teacherProfiles.previousWeeks")}
          </Button>
          <span className="text-xs font-semibold text-slate-600">
            {t("teacherProfiles.scheduleRange", { from: formatDate(scheduleFrom), to: formatDate(scheduleTo) })}
          </span>
          <Button size="sm" variant="secondary" onClick={() => setScheduleFrom(addDays(scheduleFrom, SCHEDULE_WINDOW_DAYS))}>
            {t("teacherProfiles.nextWeeks")} <ChevronRight className="w-3.5 h-3.5" />
          </Button>
        </div>
        {loadingSessions ? (
          <div className="py-10 text-center text-slate-300"><IdCard className="w-10 h-10 mx-auto animate-pulse" /></div>
        ) : sessions.length === 0 ? (
          <EmptyState icon={IdCard} title={t("teacherProfiles.noSessions")} />
        ) : (
          <TableContainer>
            <thead>
              <tr>
                <Th>{t("teacherProfiles.scheduleColumns.date")}</Th>
                <Th>{t("teacherProfiles.scheduleColumns.time")}</Th>
                <Th>{t("teacherProfiles.scheduleColumns.class")}</Th>
                <Th>{t("teacherProfiles.scheduleColumns.room")}</Th>
                <Th>{t("teacherProfiles.scheduleColumns.status")}</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {sessions.map((s) => (
                <tr key={s.id}>
                  <Td className="whitespace-nowrap">{formatDate(s.sessionDate)}</Td>
                  <Td className="whitespace-nowrap">{s.startTime.slice(0, 5)} – {s.endTime.slice(0, 5)}</Td>
                  <Td className="font-medium">{s.className}</Td>
                  <Td>{s.roomName ?? "—"}</Td>
                  <Td>{t(`values.${s.status}`, { defaultValue: s.status })}</Td>
                </tr>
              ))}
            </tbody>
          </TableContainer>
        )}
      </div>
    );
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("teacherProfiles.title")}</h1>
        <p className="text-xs text-slate-500 mt-1">{t("teacherProfiles.description")}</p>
        <p className="flex items-center gap-1.5 text-[11px] text-slate-400 mt-2">
          <ShieldCheck className="w-3.5 h-3.5" /> {t("teacherProfiles.privacyNote")}
        </p>
      </div>

      <div className="relative max-w-md">
        <Search className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
        <input
          value={queryInput}
          onChange={(e) => setQueryInput(e.target.value)}
          placeholder={t("teacherProfiles.searchPlaceholder")}
          className="w-full border border-slate-300 rounded-lg text-sm py-2 pl-8 pr-2 bg-white focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
        />
      </div>

      {error && <div className="bg-rose-50 border border-rose-200/80 rounded-xl p-4 text-rose-700 text-sm">{error}</div>}

      <Card padded={false} className="overflow-hidden">
        {loading && teachers.length === 0 ? (
          <div className="py-16 text-center text-slate-300"><IdCard className="w-12 h-12 mx-auto animate-pulse" /></div>
        ) : teachers.length === 0 ? (
          <EmptyState icon={IdCard} title={t("teacherProfiles.empty")} />
        ) : (
          <TableContainer className="rounded-none border-0">
            <thead>
              <tr>
                <Th>{t("teacherProfiles.columns.teacher")}</Th>
                <Th>{t("teacherProfiles.columns.contact")}</Th>
                <Th>{t("teacherProfiles.columns.sites")}</Th>
                <Th>{t("teacherProfiles.columns.position")}</Th>
                <Th className="text-right">{t("teacherProfiles.columns.activeClasses")}</Th>
                <Th>{t("teacherProfiles.columns.status")}</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {teachers.map((p) => (
                <tr key={p.employeeId} onClick={() => setSelectedId(p.employeeId)} className="cursor-pointer hover:bg-slate-50">
                  <Td>
                    <div className="flex items-center gap-2.5">
                      {p.portraitUrl ? (
                        <img src={p.portraitUrl} alt={p.fullName} className="w-8 h-8 rounded-full object-cover shrink-0" />
                      ) : (
                        <Avatar name={p.fullName} size="sm" />
                      )}
                      <div>
                        <p className="font-semibold text-slate-800">{p.fullName}</p>
                        <p className="text-[11px] text-slate-400">{p.employeeCode}</p>
                      </div>
                    </div>
                  </Td>
                  <Td>
                    <p>{p.email ?? "—"}</p>
                    <p className="text-[11px] text-slate-400">{p.phone ?? ""}</p>
                  </Td>
                  <Td>{p.siteNames.length ? p.siteNames.join(", ") : "—"}</Td>
                  <Td>{p.positionName ?? "—"}</Td>
                  <Td className="text-right font-semibold">{p.activeClassCount}</Td>
                  <Td>
                    <Badge variant={STATUS_BADGES[p.status]}>{t(`teacherProfiles.status.${p.status}`)}</Badge>
                  </Td>
                </tr>
              ))}
            </tbody>
          </TableContainer>
        )}
      </Card>

      <Modal
        open={selectedId != null}
        onClose={() => setSelectedId(null)}
        title={detail?.profile.fullName ?? t("teacherProfiles.title")}
        description={detail ? `${detail.profile.employeeCode}${detail.profile.positionName ? ` · ${detail.profile.positionName}` : ""}` : undefined}
        size="lg"
      >
        {detailError ? (
          <div className="bg-rose-50 border border-rose-200/80 rounded-xl p-4 text-rose-700 text-sm">{detailError}</div>
        ) : !detail ? (
          <div className="py-12 text-center text-slate-300"><IdCard className="w-10 h-10 mx-auto animate-pulse" /></div>
        ) : (
          <div className="space-y-4">
            <Tabs items={tabs} activeId={activeTab} onChange={(id) => setActiveTab(id as DetailTab)} />
            {activeTab === "info" && renderInfo(detail)}
            {activeTab === "qualifications" && renderQualifications(detail)}
            {activeTab === "commendations" && renderCommendations(detail)}
            {activeTab === "classes" && renderClasses(detail)}
            {activeTab === "schedule" && renderSchedule()}
          </div>
        )}
      </Modal>
    </div>
  );
}
