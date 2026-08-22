import React, { useEffect, useMemo, useState } from "react";
import { History, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { StudentCommentHistoryResponse, listStudentCommentHistoryForSession } from "../api";
import Badge from "@/components/ui/Badge";
import { toLocaleTag } from "@/lib/i18nFormat";

const statusVariants: Record<StudentCommentHistoryResponse["details"]["status"], "success" | "warning" | "danger" | "neutral"> = {
  DRAFT: "neutral",
  PENDING: "warning",
  APPROVED: "success",
  REJECTED: "danger"
};

/**
 * Ghim 3 cột đầu (Mã HV/Họ và tên/Ngày sinh, 2026-08-19, mirror y hệt STICKY_COL_STYLE của
 * DailyCommentPanel.tsx) khi cuộn ngang bảng rộng — cần width CỐ ĐỊNH (không chỉ min-w) để tính đúng
 * left offset cộng dồn cho position:sticky; table-layout auto (mặc định) vẫn co giãn cột theo nội dung
 * nếu chỉ đặt width thường, phải ép cứng width/minWidth/maxWidth bằng nhau trực tiếp trên từng ô.
 */
const STICKY_COL_WIDTHS = [110, 160, 100];
const STICKY_COL_LEFT = [0, STICKY_COL_WIDTHS[0], STICKY_COL_WIDTHS[0] + STICKY_COL_WIDTHS[1]];
const STICKY_COL_STYLE: React.CSSProperties[] = STICKY_COL_WIDTHS.map((w, i) => ({
  width: w,
  minWidth: w,
  maxWidth: w,
  left: STICKY_COL_LEFT[i]
}));

/**
 * 1 lần bấm "Lưu nháp"/"Gửi nhận xét" ở màn nhập trực tiếp thực chất là N request riêng biệt (1/học
 * sinh, xem DailyCommentPanel#saveFilledRows) — không có sẵn 1 batch id dùng chung. Gộp thành "1 phiên
 * bản" bằng khoảng cách thời gian: các bản ghi liên tiếp (đã sort mới→cũ) cách nhau dưới ngưỡng này coi
 * là cùng 1 đợt lưu. 12s đủ rộng cho ~50 request tuần tự qua mạng chậm, đủ hẹp để không gộp nhầm 2 lần
 * bấm "Lưu nháp" cách nhau vài chục giây thành 1.
 */
const BURST_GAP_MS = 12_000;

interface VersionBucket {
  key: string;
  timestamp: string;
  entries: StudentCommentHistoryResponse[];
  studentIds: Set<number>;
  actors: string[];
}

function buildBuckets(history: StudentCommentHistoryResponse[]): VersionBucket[] {
  const buckets: VersionBucket[] = [];
  for (const entry of history) {
    const last = buckets[buckets.length - 1];
    const gap = last ? new Date(last.timestamp).getTime() - new Date(entry.createdAt).getTime() : Infinity;
    if (last && gap <= BURST_GAP_MS) {
      last.entries.push(entry);
      last.studentIds.add(entry.studentId);
      if (!last.actors.includes(entry.changedByName)) last.actors.push(entry.changedByName);
    } else {
      buckets.push({
        key: entry.id.toString(),
        timestamp: entry.createdAt,
        entries: [entry],
        studentIds: new Set([entry.studentId]),
        actors: [entry.changedByName]
      });
    }
  }
  return buckets;
}

/** Trạng thái mọi học sinh trong roster TẠI ĐÚNG thời điểm 1 phiên bản — bản ghi mới nhất <= mốc đó của từng học sinh (không chỉ những em được sửa NGAY trong phiên bản này, giữ nguyên bản ghi cũ nhất của em chưa bị đụng tới, mirror đúng cách Google Sheets hiện TOÀN BỘ sheet ở mỗi version chứ không chỉ phần vừa đổi). */
function reconstructAsOf(history: StudentCommentHistoryResponse[], asOfIso: string): Map<number, StudentCommentHistoryResponse> {
  const asOf = new Date(asOfIso).getTime();
  const byStudent = new Map<number, StudentCommentHistoryResponse>();
  for (const entry of history) {
    if (new Date(entry.createdAt).getTime() > asOf) continue;
    const current = byStudent.get(entry.studentId);
    if (!current || new Date(entry.createdAt) > new Date(current.createdAt)) {
      byStudent.set(entry.studentId, entry);
    }
  }
  return byStudent;
}

interface SessionVersionHistoryModalProps {
  classSessionId: number;
  students: { studentId: number; studentCode: string; studentFullName: string; studentDateOfBirth: string | null }[];
  /** Nhãn 2 kênh BTVN theo Loại giáo viên của buổi — ăn theo state đã tính sẵn ở DailyCommentPanel, khớp đúng cột header bảng chính. */
  grammarLabel: string;
  videoLabel: string;
  /** V130 — buổi teacherType=VIETNAMESE tách "Offline" thành Reading/Writing (mirror DailyCommentPanel.tsx). */
  isVietnamese?: boolean;
  onClose: () => void;
}

/**
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-19) — version history kiểu Google Sheets
 * cho CẢ BUỔI: 1 nút duy nhất (khác CommentVersionHistoryModal — xem theo TỪNG học sinh riêng lẻ), bên
 * trái là timeline các "phiên bản" (mỗi phiên bản = 1 đợt Lưu nháp/Gửi/Duyệt/Từ chối), bên phải tái
 * dựng TOÀN BỘ bảng (mọi học sinh) đúng như lúc đó — học sinh vừa bị đụng tới trong phiên bản đang xem
 * được tô sáng (mirror "Highlight changes" của Google Sheets). Bộ cột khớp Y HỆT bảng nhập trực tiếp
 * (Mã học viên/Họ và tên/Ngày sinh/BTVN buổi trước×3/BTVN offline/BTVN online×2/Hạn nộp bài/Thái độ học
 * tập/Nhận xét học sinh/Ghi chú) + thêm cột Trạng thái (không có ở bảng chính, nhưng cần thiết ở đây vì
 * đang xem lịch sử NHIỀU trạng thái khác nhau theo thời gian).
 */
export default function SessionVersionHistoryModal({ classSessionId, students, grammarLabel, videoLabel, isVietnamese = false, onClose }: SessionVersionHistoryModalProps) {
  const { t, i18n } = useTranslation("academic-comments");
  const [history, setHistory] = useState<StudentCommentHistoryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedBucketKey, setSelectedBucketKey] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    listStudentCommentHistoryForSession(classSessionId)
      .then((res) => {
        setHistory(res);
        const buckets = buildBuckets(res);
        setSelectedBucketKey(buckets[0]?.key ?? null);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("sessionVersionHistoryModal.loadError")))
      .finally(() => setLoading(false));
  }, [classSessionId]);

  const buckets = useMemo(() => buildBuckets(history), [history]);
  const selectedBucket = buckets.find((b) => b.key === selectedBucketKey) ?? buckets[0] ?? null;
  const stateAsOf = selectedBucket ? reconstructAsOf(history, selectedBucket.timestamp) : new Map<number, StudentCommentHistoryResponse>();

  const thClass = "bg-white text-[10px] font-bold uppercase text-slate-400 text-left px-2 py-2 border-b-2 border-r border-slate-200";

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="fixed inset-0 bg-slate-900/50 backdrop-blur-xs" onClick={onClose} />
      <div className="relative w-full max-w-6xl h-[85vh] bg-white rounded-2xl border border-slate-200 shadow-xl flex flex-col overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 flex items-start justify-between gap-3 shrink-0">
          <div className="flex items-center gap-2">
            <History className="w-4 h-4 text-slate-500 shrink-0" />
            <div>
              <h3 className="text-sm font-bold font-display text-slate-900">{t("sessionVersionHistoryModal.title")}</h3>
              <p className="text-[11px] text-slate-500 mt-0.5">{t("sessionVersionHistoryModal.subtitle")}</p>
            </div>
          </div>
          <button type="button" onClick={onClose} className="text-slate-400 hover:text-slate-600 shrink-0">
            <X className="w-4 h-4" />
          </button>
        </div>

        {error && <div className="mx-5 mt-3 text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg shrink-0">{error}</div>}

        {loading ? (
          <p className="p-5 text-xs text-slate-400">{t("sessionVersionHistoryModal.loading")}</p>
        ) : buckets.length === 0 ? (
          <p className="p-5 text-xs text-slate-400 italic">{t("sessionVersionHistoryModal.empty")}</p>
        ) : (
          <div className="flex-1 flex min-h-0">
            {/* Timeline bên trái — mirror layout "Version history" của Google Sheets. */}
            <div className="w-64 shrink-0 border-r border-slate-100 overflow-y-auto py-2">
              {buckets.map((b, index) => {
                const selected = selectedBucket?.key === b.key;
                return (
                  <button
                    key={b.key}
                    type="button"
                    onClick={() => setSelectedBucketKey(b.key)}
                    className={`w-full text-left px-4 py-2.5 border-l-2 ${
                      selected ? "border-teal bg-teal/5" : "border-transparent hover:bg-slate-50"
                    }`}
                  >
                    <div className="flex items-center gap-1.5">
                      <span className="text-[11px] font-bold text-slate-800">
                        {new Date(b.timestamp).toLocaleString(toLocaleTag(i18n.language), { dateStyle: "short", timeStyle: "short" })}
                      </span>
                      {index === 0 && (
                        <span className="px-1.5 py-0.5 rounded bg-teal/10 text-teal-deep text-[9px] font-black uppercase tracking-wide shrink-0">{t("sessionVersionHistoryModal.latestBadge")}</span>
                      )}
                    </div>
                    <p className="text-[10px] text-slate-400 mt-0.5 truncate">{b.actors.join(", ")}</p>
                    <p className="text-[10px] text-slate-400">{t("sessionVersionHistoryModal.studentsCount", { count: b.studentIds.size })}</p>
                  </button>
                );
              })}
            </div>
            <div className="flex-1 overflow-auto p-0 mx-4">
              <table className="text-[11px] text-left border-separate border-spacing-0">
                <thead className="sticky top-0 z-20 bg-white shadow-sm">
                  <tr className="[&>th]:text-center">
                    <th rowSpan={isVietnamese ? 3 : 2} style={STICKY_COL_STYLE[0]} className={`${thClass} sticky left-0 z-30`}>
                      {t("sessionVersionHistoryModal.columns.studentCode")}
                    </th>
                    <th rowSpan={isVietnamese ? 3 : 2} style={STICKY_COL_STYLE[1]} className={`${thClass} sticky z-30`}>
                      {t("sessionVersionHistoryModal.columns.fullName")}
                    </th>
                    <th rowSpan={isVietnamese ? 3 : 2} style={STICKY_COL_STYLE[2]} className={`${thClass} sticky z-30`}>
                      {t("sessionVersionHistoryModal.columns.dateOfBirth")}
                    </th>
                    <th colSpan={isVietnamese ? 6 : 3} className={thClass}>
                      {t("sessionVersionHistoryModal.columns.homeworkPrevious")}
                    </th>
                    <th colSpan={isVietnamese ? 6 : 3} className={thClass}>
                      {t("dailyCommentPanel.columns.homeworkNextGroup")}
                    </th>
                    <th rowSpan={isVietnamese ? 3 : 2} className={`${thClass} min-w-[120px]`}>
                      {t("sessionVersionHistoryModal.columns.dueDate")}
                    </th>
                    <th rowSpan={isVietnamese ? 3 : 2} className={`${thClass} min-w-[110px]`}>
                      {t("sessionVersionHistoryModal.columns.attitude")}
                    </th>
                    <th rowSpan={isVietnamese ? 3 : 2} className={`${thClass} min-w-[260px]`}>
                      {t("sessionVersionHistoryModal.columns.studentComment")}
                    </th>
                    <th rowSpan={isVietnamese ? 3 : 2} className={`${thClass} min-w-[140px]`}>
                      {t("sessionVersionHistoryModal.columns.note")}
                    </th>
                    <th rowSpan={isVietnamese ? 3 : 2} className={`${thClass} min-w-[110px] border-r-0`}>
                      {t("sessionVersionHistoryModal.columns.status")}
                    </th>
                  </tr>
                  {isVietnamese ? (
                    <>
                      <tr className="[&>th]:text-center">
                        <th className={thClass} colSpan={2}>{t("sessionVersionHistoryModal.columns.offline")}</th>
                        <th className={thClass} colSpan={4}>{t("dailyCommentPanel.columns.online")}</th>
                        <th className={thClass} colSpan={2}>{t("sessionVersionHistoryModal.columns.offline")}</th>
                        <th className={thClass} colSpan={4}>{t("dailyCommentPanel.columns.online")}</th>
                      </tr>
                      <tr className="[&>th]:text-center">
                        <th className={`${thClass} min-w-[90px]`}>{t("dailyCommentPanel.columns.reading")}</th>
                        <th className={`${thClass} min-w-[90px]`}>{t("dailyCommentPanel.columns.writing")}</th>
                        <th className={`${thClass} min-w-[90px]`}>{t("dailyCommentPanel.columns.reading")}</th>
                        <th className={`${thClass} min-w-[90px]`}>{t("dailyCommentPanel.columns.writing")}</th>
                        <th className={`${thClass} min-w-[110px]`}>{t("dailyCommentPanel.columns.onlineGrammarShort")}</th>
                        <th className={`${thClass} min-w-[110px]`}>{t("dailyCommentPanel.columns.onlineVideoShort")}</th>
                        <th className={`${thClass} min-w-[120px]`}>{t("dailyCommentPanel.columns.reading")}</th>
                        <th className={`${thClass} min-w-[120px]`}>{t("dailyCommentPanel.columns.writing")}</th>
                        <th className={`${thClass} min-w-[120px]`}>{t("dailyCommentPanel.columns.reading")}</th>
                        <th className={`${thClass} min-w-[120px]`}>{t("dailyCommentPanel.columns.writing")}</th>
                        <th className={`${thClass} min-w-[110px]`}>{t("dailyCommentPanel.columns.onlineGrammarShort")}</th>
                        <th className={`${thClass} min-w-[110px]`}>{t("dailyCommentPanel.columns.onlineVideoShort")}</th>
                      </tr>
                    </>
                  ) : (
                    <tr className="[&>th]:text-center">
                      <th className={`${thClass} min-w-[100px]`}>{t("sessionVersionHistoryModal.columns.offline")}</th>
                      <th className={`${thClass} min-w-[110px]`}>{grammarLabel}</th>
                      <th className={`${thClass} min-w-[110px]`}>{videoLabel}</th>
                      <th className={`${thClass} min-w-[160px]`}>{grammarLabel}</th>
                      <th className={`${thClass} min-w-[160px]`}>{videoLabel}</th>
                    </tr>
                  )}
                </thead>
                <tbody>
                  {students.map((s) => {
                    const entry = stateAsOf.get(s.studentId);
                    const touchedNow = !!selectedBucket && selectedBucket.studentIds.has(s.studentId);
                    const d = entry?.details;
                    const rowBg = touchedNow ? "bg-amber-50" : undefined;
                    const tdClass = `px-2 py-2 border-b border-r border-slate-100 ${rowBg ?? ""}`;
                    const stickyTdClass = `px-2 py-2 border-b border-r border-slate-100 sticky z-10 ${touchedNow ? "bg-amber-50" : "bg-white"}`;
                    return (
                      <tr key={s.studentId}>
                        <td style={STICKY_COL_STYLE[0]} className={`${stickyTdClass} font-mono font-bold text-slate-500 whitespace-nowrap`}>
                          {s.studentCode}
                        </td>
                        <td style={STICKY_COL_STYLE[1]} className={`${stickyTdClass} font-bold text-slate-800 whitespace-nowrap`}>
                          {s.studentFullName}
                        </td>
                        <td style={STICKY_COL_STYLE[2]} className={`${stickyTdClass} whitespace-nowrap text-slate-500`}>{s.studentDateOfBirth ?? "—"}</td>
                        {isVietnamese ? (
                          <>
                            <td className={`${tdClass} text-slate-500`}>{d?.homeworkPreviousReadingScore || "—"}</td>
                            <td className={`${tdClass} text-slate-500`}>{d?.homeworkPreviousWritingScore || "—"}</td>
                          </>
                        ) : (
                          <td className={`${tdClass} text-slate-500`}>{d?.homeworkPreviousScore || "—"}</td>
                        )}
                        {isVietnamese && (
                          <>
                            {/* V137 — "BTVN buổi trước - Online - Reading/Writing": chỉ % TỰ ĐỘNG, mirror cột grammarPreviousProgress. */}
                            <td className={`${tdClass} text-slate-500`}>{d?.readingPreviousProgress || "—"}</td>
                            <td className={`${tdClass} text-slate-500`}>{d?.writingPreviousProgress || "—"}</td>
                          </>
                        )}
                        <td className={`${tdClass} text-slate-500`}>{d?.grammarPreviousProgress || "—"}</td>
                        <td className={`${tdClass} text-slate-500`}>{d?.videoPreviousProgress || d?.homeworkPreviousSpeakingScore || "—"}</td>
                        {isVietnamese ? (
                          <>
                            <td className={`${tdClass} text-slate-500`}>{d?.homeworkNextReading || "—"}</td>
                            <td className={`${tdClass} text-slate-500`}>{d?.homeworkNextWriting || "—"}</td>
                            {/* V137 — "BTVN - Online - Reading/Writing" (giao buổi sau). */}
                            <td className={`${tdClass} text-slate-500`}>{d?.homeworkNextReadingExerciseTitle || "—"}</td>
                            <td className={`${tdClass} text-slate-500`}>{d?.homeworkNextWritingExerciseTitle || "—"}</td>
                          </>
                        ) : (
                          <td className={`${tdClass} text-slate-500`}>{d?.homeworkNext || "—"}</td>
                        )}
                        <td className={`${tdClass} text-slate-500`}>{d?.homeworkNextExerciseTitle || "—"}</td>
                        <td className={`${tdClass} text-slate-500`}>{d?.homeworkNextReviewVideoSetTitle || "—"}</td>
                        <td className={`${tdClass} whitespace-nowrap text-slate-500`}>
                          {d?.homeworkNextDueAt ? new Date(d.homeworkNextDueAt).toLocaleString(toLocaleTag(i18n.language), { dateStyle: "short", timeStyle: "short" }) : "—"}
                        </td>
                        <td className={`${tdClass} text-slate-500`}>{d?.attitude ? t(`shared.attitude.${d.attitude}`) : "—"}</td>
                        <td className={`${tdClass} max-w-[260px] whitespace-pre-wrap text-slate-700`}>{d?.content || "—"}</td>
                        <td className={`${tdClass} text-slate-500`}>{d?.note || "—"}</td>
                        <td className={`px-2 py-2 border-b border-slate-100 ${rowBg ?? ""}`}>
                          {d ? <Badge variant={statusVariants[d.status]}>{t(`shared.status.${d.status}`)}</Badge> : "—"}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
