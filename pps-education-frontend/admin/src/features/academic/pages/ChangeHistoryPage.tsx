import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { ArrowRight, Filter, History, Search, Users } from "lucide-react";
import { useApp } from "@/context/AppContext";
import { ApiError } from "@/lib/apiClient";
import { toISODate } from "@/lib/calendarDates";
import { formatDateTime, toLocaleTag } from "@/lib/i18nFormat";
import Card from "@/components/ui/Card";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Select from "@/components/ui/Select";
import DatePicker from "@/components/ui/DatePicker";
import TableContainer, { Th, Td } from "@/components/ui/TableContainer";
import EmptyState from "@/components/ui/EmptyState";
import Pagination from "@/components/ui/Pagination";
import type { Page } from "@/types";
import { ChangeHistoryEntityType, ChangeHistoryItem, searchChangeHistory } from "../oversightApi";
import FloatingError from "@/components/ui/FloatingError";

const ENTITY_TYPES: ChangeHistoryEntityType[] = [
  "CLASS",
  "CLASS_SESSION",
  "CLASS_ENROLLMENT",
  "CLASS_TEACHER",
  "STUDENT",
  "EMPLOYEE",
  "SESSION_REPORT"
];

const ENTITY_BADGES: Record<ChangeHistoryEntityType, BadgeVariant> = {
  CLASS: "brand",
  CLASS_SESSION: "info",
  CLASS_ENROLLMENT: "success",
  CLASS_TEACHER: "warning",
  STUDENT: "neutral",
  EMPLOYEE: "danger",
  SESSION_REPORT: "info"
};

/** Trường đã thể hiện ở cột "Đối tượng" — không lặp lại trong nội dung thay đổi. */
const HIDDEN_FIELDS: Partial<Record<ChangeHistoryEntityType, string[]>> = {
  CLASS_ENROLLMENT: ["studentId"],
  CLASS_TEACHER: ["teacherUserId"]
};

function daysAgoIso(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return toISODate(d);
}

interface FieldChange {
  key: string;
  previous?: unknown;
  current: unknown;
  changed: boolean;
}

/**
 * Snapshot trong bảng lịch sử chỉ ghi 1 phần trường (tuỳ thao tác) — so với snapshot liền trước của cùng
 * đối tượng để hiện "cũ → mới"; lần tạo mới hoặc chưa có bản ghi trước thì hiện giá trị hiện tại.
 */
function computeChanges(item: ChangeHistoryItem): { changes: FieldChange[]; nothingChanged: boolean } {
  const hidden = new Set(HIDDEN_FIELDS[item.entityType] ?? []);
  const current = item.details ?? {};
  const previous = item.previousDetails;
  const all: FieldChange[] = Object.entries(current)
    .filter(([key]) => !hidden.has(key))
    .map(([key, value]) => {
      const hasPrevious = previous != null && key in previous;
      const changed = !hasPrevious || String(previous?.[key]) !== String(value);
      return { key, previous: hasPrevious ? previous?.[key] : undefined, current: value, changed };
    });
  if (item.action === "CREATED" || previous == null) {
    return { changes: all.map((c) => ({ ...c, previous: undefined })), nothingChanged: false };
  }
  const changedOnly = all.filter((c) => c.changed);
  return changedOnly.length > 0 ? { changes: changedOnly, nothingChanged: false } : { changes: all, nothingChanged: true };
}

/**
 * Trang "Lịch sử thay đổi dữ liệu" (V203 — bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-09-30):
 * Trưởng phòng đào tạo xem ai đã tạo/sửa lớp, lịch học, ghi danh, giáo viên phụ trách, hồ sơ học sinh và
 * giáo viên. Lọc theo điểm trường/lớp đang chọn trên Header (quy ước chung các trang báo cáo).
 */
export default function ChangeHistoryPage() {
  const { t, i18n } = useTranslation("academic-oversight");
  const { selectedCampusId, selectedClassId, hasPermission } = useApp();
  const canView = hasPermission("academic.change-history.view");
  // V206 — không có quyền xem toàn bộ thì backend chỉ trả thay đổi của mình + nhân sự phòng ban mình làm trưởng phòng.
  const departmentScoped = !hasPermission("academic.change-history.view-all");

  const [entityType, setEntityType] = useState<ChangeHistoryEntityType | "">("");
  const [fromDate, setFromDate] = useState<string>(daysAgoIso(30));
  const [toDate, setToDate] = useState<string>(toISODate(new Date()));
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);

  const [data, setData] = useState<Page<ChangeHistoryItem> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Gõ xong 400ms mới tìm, tránh gọi API theo từng phím.
  useEffect(() => {
    const timer = setTimeout(() => setKeyword(keywordInput), 400);
    return () => clearTimeout(timer);
  }, [keywordInput]);

  useEffect(() => {
    setPage(0);
  }, [entityType, fromDate, toDate, keyword, selectedCampusId, selectedClassId, pageSize]);

  useEffect(() => {
    if (!canView) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    searchChangeHistory({
      entityType,
      fromDate: fromDate || undefined,
      toDate: toDate || undefined,
      siteId: selectedCampusId !== "ALL" ? Number(selectedCampusId) : undefined,
      classId: selectedClassId ?? undefined,
      keyword,
      page,
      size: pageSize
    })
      .then((res) => {
        if (!cancelled) setData(res);
      })
      .catch((err) => {
        if (cancelled) return;
        setData(null);
        setError(err instanceof ApiError ? err.message : t("changeHistory.loadFailed"));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [canView, entityType, fromDate, toDate, keyword, selectedCampusId, selectedClassId, page, pageSize, t]);

  const formatValue = (item: ChangeHistoryItem, key: string, value: unknown): string => {
    if (value === null || value === undefined || value === "" || value === "null") return "—";
    const label = item.valueLabels[`${key}:${value}`];
    if (label) return label;
    const text = String(value);
    if (/^\d{4}-\d{2}-\d{2}$/.test(text)) {
      return new Date(`${text}T00:00:00`).toLocaleDateString(toLocaleTag(i18n.language));
    }
    if (/^\d{2}:\d{2}(:\d{2})?$/.test(text)) return text.slice(0, 5);
    return t(`values.${text}`, { defaultValue: text });
  };

  const renderSubject = (item: ChangeHistoryItem) => {
    const classLabel = item.className ? `${item.className}${item.classCode ? ` (${item.classCode})` : ""}` : null;
    const personLabel = item.subjectName ? `${item.subjectName}${item.subjectCode ? ` (${item.subjectCode})` : ""}` : null;
    switch (item.entityType) {
      case "CLASS":
        return <span className="font-medium text-slate-800">{classLabel}</span>;
      case "CLASS_SESSION":
      case "SESSION_REPORT":
        return (
          <div>
            <p className="font-medium text-slate-800">{classLabel}</p>
            {item.sessionDate && (
              <p className="text-[13px] text-slate-500">
                {t("changeHistory.sessionOf", {
                  date: new Date(`${item.sessionDate}T00:00:00`).toLocaleDateString(toLocaleTag(i18n.language))
                })}
              </p>
            )}
          </div>
        );
      case "CLASS_ENROLLMENT":
      case "CLASS_TEACHER":
        return (
          <div>
            <p className="font-medium text-slate-800">{personLabel}</p>
            <p className="text-[13px] text-slate-500">{classLabel}</p>
          </div>
        );
      default:
        return <span className="font-medium text-slate-800">{personLabel}</span>;
    }
  };

  /** V207 — mốc nộp/duyệt báo cáo: số nhận xét, lý do từ chối, đúng hạn/muộn so với hạn của khâu đó. */
  const renderSessionReportEvent = (item: ChangeHistoryItem) => {
    const d = item.details ?? {};
    const formatAt = (value: unknown) =>
      typeof value === "string" ? new Date(value).toLocaleString(toLocaleTag(i18n.language), { hour: "2-digit", minute: "2-digit", day: "2-digit", month: "2-digit" }) : "—";
    const lateMinutes = typeof d.lateMinutes === "number" ? d.lateMinutes : 0;
    const duration = lateMinutes < 60
      ? t("sessionReports.minutes", { count: lateMinutes })
      : lateMinutes < 1440
        ? t("sessionReports.hours", { hours: Math.floor(lateMinutes / 60), minutes: lateMinutes % 60 })
        : t("sessionReports.days", { days: Math.floor(lateMinutes / 1440), hours: Math.floor((lateMinutes % 1440) / 60) });
    return (
      <div className="space-y-0.5 text-[13px] leading-5">
        <p>
          <span className="text-slate-500">{t("fields.commentCount")}: </span>
          <span className="font-semibold text-slate-700">{String(d.commentCount ?? "—")}</span>
        </p>
        {typeof d.reason === "string" && d.reason && (
          <p>
            <span className="text-slate-500">{t("fields.reason")}: </span>
            <span className="text-slate-700">{d.reason}</span>
          </p>
        )}
        {d.timeliness === "LATE" && (
          <p className="text-amber-700">{t("sessionReportEvent.late", { duration, deadline: formatAt(d.deadline) })}</p>
        )}
        {d.timeliness === "ON_TIME" && (
          <p className="text-emerald-700">{t("sessionReportEvent.onTime", { deadline: formatAt(d.deadline) })}</p>
        )}
      </div>
    );
  };

  const renderChanges = (item: ChangeHistoryItem) => {
    if (item.entityType === "SESSION_REPORT") return renderSessionReportEvent(item);
    const { changes, nothingChanged } = computeChanges(item);
    if (changes.length === 0) return <span className="text-slate-400">—</span>;
    return (
      <div className="space-y-0.5">
        {nothingChanged && <p className="text-[13px] italic text-slate-400">{t("changeHistory.unchanged")}</p>}
        {changes.map((c) => (
          <p key={c.key} className="text-[13px] leading-5">
            <span className="text-slate-500">{t(`fields.${c.key}`, { defaultValue: c.key })}: </span>
            {c.previous !== undefined && c.changed && (
              <>
                <span className="text-slate-400 line-through">{formatValue(item, c.key, c.previous)}</span>
                <ArrowRight className="inline w-3 h-3 mx-1 text-slate-400" />
              </>
            )}
            <span className="font-semibold text-slate-700">{formatValue(item, c.key, c.current)}</span>
          </p>
        ))}
      </div>
    );
  };

  const resetFilters = () => {
    setEntityType("");
    setFromDate(daysAgoIso(30));
    setToDate(toISODate(new Date()));
    setKeywordInput("");
  };

  if (!canView) {
    return (
      <div className="text-sm text-slate-500 bg-slate-50 border border-slate-200 p-4 rounded-lg">
        {t("changeHistory.noPermission")}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("changeHistory.title")}</h1>
        <p className="text-sm text-slate-500 mt-1">{t("changeHistory.description")}</p>
        {departmentScoped && (
          <p className="flex items-center gap-1.5 text-[13px] text-slate-400 mt-2">
            <Users className="w-3.5 h-3.5" /> {t("changeHistory.departmentScopeHint")}
          </p>
        )}
      </div>

      <Card>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3 items-end">
          <div>
            <label className="block text-sm text-slate-500 mb-1">{t("changeHistory.filters.entityType")}</label>
            <Select
              value={entityType}
              onChange={(e) => setEntityType(e.target.value as ChangeHistoryEntityType | "")}
              className="w-full border border-slate-300 rounded-lg text-sm p-2 focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
            >
              <option value="">{t("changeHistory.filters.allTypes")}</option>
              {ENTITY_TYPES.map((type) => (
                <option key={type} value={type}>{t(`entityTypes.${type}`)}</option>
              ))}
            </Select>
          </div>
          <div>
            <label className="block text-sm text-slate-500 mb-1">{t("changeHistory.filters.fromDate")}</label>
            <DatePicker value={fromDate} onChange={setFromDate} max={toDate || undefined} />
          </div>
          <div>
            <label className="block text-sm text-slate-500 mb-1">{t("changeHistory.filters.toDate")}</label>
            <DatePicker value={toDate} onChange={setToDate} min={fromDate || undefined} />
          </div>
          <div className="flex items-center gap-2">
            <div className="relative flex-1">
              <Search className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                value={keywordInput}
                onChange={(e) => setKeywordInput(e.target.value)}
                placeholder={t("changeHistory.filters.keyword")}
                className="w-full border border-slate-300 rounded-lg text-sm py-2 pl-8 pr-2 focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
              />
            </div>
            <Button variant="ghost" onClick={resetFilters}>{t("changeHistory.filters.reset")}</Button>
          </div>
        </div>
        {(selectedCampusId !== "ALL" || selectedClassId) && (
          <p className="flex items-center gap-1.5 text-[13px] text-slate-500 mt-3">
            <Filter className="w-3 h-3" /> {t("changeHistory.scopeHint")}
          </p>
        )}
      </Card>

      <FloatingError message={error} onClose={() => setError(null)} />

      <Card padded={false} className="overflow-hidden">
        {loading && !data ? (
          <div className="text-center py-16 text-slate-400">
            <History className="w-12 h-12 mx-auto text-slate-200 mb-3 animate-pulse" />
          </div>
        ) : !data || data.content.length === 0 ? (
          <EmptyState icon={History} title={t("changeHistory.empty")} />
        ) : (
          <>
            <TableContainer className={loading ? "rounded-none border-0 opacity-60" : "rounded-none border-0"}>
              <thead>
                <tr>
                  <Th>{t("changeHistory.columns.time")}</Th>
                  <Th>{t("changeHistory.columns.changedBy")}</Th>
                  <Th>{t("changeHistory.columns.type")}</Th>
                  <Th>{t("changeHistory.columns.subject")}</Th>
                  <Th>{t("changeHistory.columns.action")}</Th>
                  <Th>{t("changeHistory.columns.changes")}</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.content.map((item) => (
                  <tr key={item.id} className="align-top hover:bg-slate-50/50">
                    <Td className="whitespace-nowrap text-slate-500">{formatDateTime(item.changedAt, i18n.language)}</Td>
                    <Td className="whitespace-nowrap font-medium">{item.changedByName}</Td>
                    <Td>
                      <Badge variant={ENTITY_BADGES[item.entityType]}>{t(`entityTypes.${item.entityType}`)}</Badge>
                    </Td>
                    <Td>{renderSubject(item)}</Td>
                    <Td className="whitespace-nowrap">
                      <Badge
                        variant={
                          item.action === "CREATED" || item.action === "APPROVED"
                            ? "success"
                            : item.action === "REJECTED"
                              ? "danger"
                              : "info"
                        }
                      >
                        {t(`actions.${item.action}`)}
                      </Badge>
                    </Td>
                    <Td className="min-w-[260px]">{renderChanges(item)}</Td>
                  </tr>
                ))}
              </tbody>
            </TableContainer>
            <Pagination
              page={page}
              pageSize={pageSize}
              totalElements={data.totalElements}
              itemLabel={t("changeHistory.itemLabel")}
              onPageChange={setPage}
              onPageSizeChange={setPageSize}
            />
          </>
        )}
      </Card>
    </div>
  );
}
