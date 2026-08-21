import React, { useEffect, useState } from "react";
import { HelpCircle, Send } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import {
  PartnerFeedbackResponse,
  SiteResponse,
  closePartnerFeedback,
  listPartnerFeedbacksForMySites,
  listSites,
  resolvePartnerFeedback,
  startProcessingPartnerFeedback
} from "../api";
import Card from "@/components/ui/Card";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import { cn } from "@/lib/cn";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import Pagination from "@/components/ui/Pagination";

const priorityVariants: Record<PartnerFeedbackResponse["priority"], BadgeVariant> = {
  URGENT: "danger",
  HIGH: "danger",
  NORMAL: "warning",
  LOW: "neutral"
};

const statusVariants: Record<PartnerFeedbackResponse["status"], BadgeVariant> = {
  NEW: "danger",
  IN_PROGRESS: "warning",
  RESOLVED: "success",
  CLOSED: "neutral"
};

/** UC-38/39: kênh Quản lý điểm trường xử lý ý kiến phản hồi từ trường liên kết (FR-FAC-05). Chỉ thấy/xử lý được phản hồi của site mình phụ trách (BE tự chặn qua site_managers). */
export default function FeedbackPage() {
  const { t } = useTranslation("facility");
  const [sites, setSites] = useState<SiteResponse[]>([]);
  const [tickets, setTickets] = useState<PartnerFeedbackResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [resolutionText, setResolutionText] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const { message: toastMessage, showToast } = useToast();

  const load = () => {
    setLoading(true);
    setError(null);
    Promise.all([listSites(), listPartnerFeedbacksForMySites()])
      .then(([siteRes, ticketRes]) => {
        setSites(siteRes);
        setTickets(ticketRes);
        setPage(0);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("feedbackPage.loadError")))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const siteName = (siteId: number) => sites.find((s) => s.id === siteId)?.name ?? t("feedbackPage.siteFallback", { id: siteId });
  const selectedTicket = tickets.find((t) => t.id === selectedId) ?? null;

  // Ticket tích lũy theo thời gian giống Audit Log, backend GET /partner-feedbacks chưa hỗ trợ phân
  // trang — phân trang phía client. CHỈ reset về trang 1 khi tải lại toàn bộ danh sách (load()), không
  // reset khi 1 ticket đổi trạng thái tại chỗ (runAction) — tránh giật về trang 1 giữa lúc đang xử lý.
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const pageTickets = tickets.slice(page * pageSize, (page + 1) * pageSize);

  const runAction = async (action: () => Promise<PartnerFeedbackResponse>, successMessage: string) => {
    setSubmitting(true);
    setError(null);
    try {
      const updated = await action();
      setTickets((prev) => prev.map((tkt) => (tkt.id === updated.id ? updated : tkt)));
      showToast(successMessage);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("feedbackPage.updateError"));
    } finally {
      setSubmitting(false);
    }
  };

  const handleResolve = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedTicket || !resolutionText.trim()) return;
    runAction(() => resolvePartnerFeedback(selectedTicket.id, resolutionText.trim()), t("feedbackPage.resolvedToast")).then(() => setResolutionText(""));
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("feedbackPage.title")}</h1>
        <p className="text-xs text-slate-500 mt-1">{t("feedbackPage.description")}</p>
      </div>

      {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card padded={false} className="lg:col-span-2 overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100 bg-slate-50">
            <span className="text-xs font-bold text-slate-700 font-display">{t("feedbackPage.sectionTitle")}</span>
          </div>

          {loading ? (
            <p className="text-xs text-slate-500 font-medium p-5">{t("feedbackPage.loading")}</p>
          ) : tickets.length === 0 ? (
            <p className="text-xs text-slate-400 italic p-5">
              {t("feedbackPage.empty")}
            </p>
          ) : (
            <div className="divide-y divide-slate-100 max-h-[500px] overflow-y-auto">
              {pageTickets.map((tkt) => (
                <div
                  key={tkt.id}
                  onClick={() => setSelectedId(tkt.id)}
                  className={cn(
                    "p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-4 hover:bg-slate-50/40 transition-colors cursor-pointer",
                    selectedId === tkt.id && "bg-slate-50"
                  )}
                >
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <h4 className="text-xs font-bold text-slate-900">{siteName(tkt.siteId)}</h4>
                      <Badge variant={priorityVariants[tkt.priority]}>{tkt.priority}</Badge>
                      <Badge variant="neutral">{t(`feedbackType.${tkt.feedbackType}`)}</Badge>
                    </div>
                    <span className="text-[11px] text-slate-500 font-medium block">{t("feedbackPage.senderLabel", { id: tkt.submittedBy })}</span>
                    <p className="text-[11px] text-slate-500 line-clamp-2 mt-1">{t("feedbackPage.contentLabel", { content: tkt.content })}</p>
                  </div>

                  <Badge variant={statusVariants[tkt.status]} className="shrink-0 self-start sm:self-center">
                    {t(`feedbackStatus.${tkt.status}`)}
                  </Badge>
                </div>
              ))}
            </div>
          )}
          {!loading && tickets.length > 0 && (
            <Pagination
              page={page}
              pageSize={pageSize}
              totalElements={tickets.length}
              itemLabel={t("feedbackPage.itemLabel")}
              onPageChange={setPage}
              onPageSizeChange={(size) => {
                setPageSize(size);
                setPage(0);
              }}
            />
          )}
        </Card>

        <Card>
          {selectedTicket ? (
            <div className="space-y-4">
              <div className="border-b pb-2.5 flex items-center justify-between">
                <div>
                  <span className="text-[10px] font-mono font-bold text-slate-400">{t("feedbackPage.ticketPrefix", { id: selectedTicket.id })}</span>
                  <h3 className="text-xs font-bold text-slate-800 truncate max-w-[150px]">{siteName(selectedTicket.siteId)}</h3>
                </div>
                <button onClick={() => setSelectedId(null)} className="text-xs text-slate-400 hover:text-slate-800">
                  {t("feedbackPage.close")}
                </button>
              </div>

              <div className="space-y-1.5">
                <span className="text-[10px] uppercase font-bold tracking-wider text-slate-400 block font-display">{t("feedbackPage.contentTitle")}</span>
                <p className="text-[11px] text-slate-600 bg-slate-50 p-2.5 rounded-lg border leading-relaxed">{selectedTicket.content}</p>
              </div>

              {selectedTicket.resolutionNotes && (
                <div className="space-y-1.5">
                  <span className="text-[10px] uppercase font-bold tracking-wider text-slate-400 block font-display">{t("feedbackPage.resolutionTitle")}</span>
                  <p className="text-[11px] text-emerald-700 bg-emerald-50 p-2.5 rounded-lg border border-emerald-100 leading-relaxed">{selectedTicket.resolutionNotes}</p>
                </div>
              )}

              {selectedTicket.status === "NEW" && (
                <button
                  disabled={submitting}
                  onClick={() => runAction(() => startProcessingPartnerFeedback(selectedTicket.id), t("feedbackPage.startedProcessingToast"))}
                  className="w-full bg-brand-gradient hover:opacity-95 text-white font-semibold text-xs py-2 rounded-lg disabled:opacity-50"
                >
                  {submitting ? t("feedbackPage.processing") : t("feedbackPage.startProcessingButton")}
                </button>
              )}

              {selectedTicket.status === "IN_PROGRESS" && (
                <form onSubmit={handleResolve} className="space-y-3.5 pt-1">
                  <div className="space-y-1">
                    <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">{t("feedbackPage.resolutionLabel")}</label>
                    <textarea
                      required
                      placeholder={t("feedbackPage.resolutionPlaceholder")}
                      value={resolutionText}
                      onChange={(e) => setResolutionText(e.target.value)}
                      rows={3}
                      className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
                    />
                  </div>

                  <button
                    type="submit"
                    disabled={submitting}
                    className="w-full bg-brand-gradient hover:opacity-95 text-white font-semibold text-xs py-2 rounded-lg flex items-center justify-center gap-1.5 shadow-glow disabled:opacity-50"
                  >
                    <Send className="w-3.5 h-3.5 text-brand-yellow shrink-0" />
                    {submitting ? t("feedbackPage.sending") : t("feedbackPage.confirmResolveButton")}
                  </button>
                </form>
              )}

              {selectedTicket.status === "RESOLVED" && (
                <button
                  disabled={submitting}
                  onClick={() => runAction(() => closePartnerFeedback(selectedTicket.id), t("feedbackPage.closedToast"))}
                  className="w-full bg-brand-gradient hover:opacity-95 text-white font-semibold text-xs py-2 rounded-lg disabled:opacity-50"
                >
                  {submitting ? t("feedbackPage.closingButton") : t("feedbackPage.closeTicketButton")}
                </button>
              )}

              {selectedTicket.status === "CLOSED" && <p className="text-[11px] text-slate-400 italic text-center pt-1">{t("feedbackPage.ticketClosedNote")}</p>}
            </div>
          ) : (
            <div className="h-64 border border-dashed rounded-xl flex flex-col items-center justify-center text-slate-400 text-xs italic gap-1.5 text-center p-4">
              <HelpCircle className="w-6 h-6 text-slate-300 animate-bounce" />
              <span>{t("feedbackPage.emptySelectionHint")}</span>
            </div>
          )}
        </Card>
      </div>

      <Toast message={toastMessage} />
    </div>
  );
}
