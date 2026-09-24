import React, { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  AlertCircle,
  BookOpen,
  CalendarClock,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Clock,
  FileText,
  Headphones,
  LucideIcon,
  Mic,
  PenLine,
  Video
} from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { formatDate, formatDateTimeHm } from "@/lib/format";
import { HomeworkProgressResponse, HomeworkSkillItemResponse, listHomeworkProgress } from "../api";
import Breadcrumb, { BreadcrumbItem } from "@/components/ui/Breadcrumb";

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — trước đây mọi % (kể cả dưới ngưỡng
 * đạt, VD 45%) đều hiện y hệt màu xanh + dấu tick, dễ hiểu nhầm là đã ổn. Giờ tách rõ: "Chưa làm bài"/
 * "Đang chờ chấm" (progress không phải số %, `passed` luôn null lúc này) giữ kiểu chữ thường màu trung
 * tính/xanh dương; đã có % thì bắt buộc có `passed` kèm theo (BE tính sẵn theo đúng ngưỡng đạt của
 * từng loại BTVN), hiện thành pill nổi bật xanh lá "Đạt" hoặc đỏ "Chưa đạt" kèm %.
 */
function ProgressBadge({ progress, passed }: { progress: string | null; passed: boolean | null }) {
  const { t } = useTranslation("portal-exercises");
  if (!progress) return null;
  if (passed == null) {
    const isWaiting = progress === "Đang chờ chấm";
    return (
      <p className={`text-sm font-bold mt-0.5 flex items-center gap-1.5 ${isWaiting ? "text-sky-700" : "text-muted"}`}>
        <Clock size={14} aria-hidden="true" /> {progress}
      </p>
    );
  }
  return (
    <span
      className={`inline-flex items-center gap-1.5 mt-1 px-3 py-1.5 rounded-xl text-sm font-black ${
        passed ? "bg-emerald-100 text-emerald-800 border border-emerald-300" : "bg-rose-100 text-rose-800 border border-rose-300"
      }`}
    >
      {passed ? <CheckCircle2 size={15} aria-hidden="true" /> : <AlertCircle size={15} aria-hidden="true" />}
      {progress} — {passed ? t("parentHomework.passed") : t("parentHomework.notPassed")}
    </span>
  );
}

type Kind = "grammar" | "reading" | "writing" | "video";
type SubmissionStatus = "PENDING" | "SUBMITTED" | "GRADED";
type FilterStatus = "ALL" | "PENDING" | "GRADED" | "OVERDUE";
type SkillKey = "VOCAB_GRAMMAR" | "LISTENING" | "READING" | "WRITING" | "CONNECTION" | "REFLEX";

/** Mirror thứ tự SKILL_ORDER bên AssignmentsTab.tsx (Học sinh) — Bài tập trước, Video sau. */
const SKILL_ORDER: SkillKey[] = ["VOCAB_GRAMMAR", "LISTENING", "READING", "WRITING", "CONNECTION", "REFLEX"];

const SKILL_ICON: Record<SkillKey, LucideIcon> = {
  VOCAB_GRAMMAR: BookOpen,
  LISTENING: Headphones,
  READING: FileText,
  WRITING: PenLine,
  CONNECTION: Video,
  REFLEX: Mic
};

function skillLabel(t: (key: string) => string, key: SkillKey): string {
  if (key === "CONNECTION") return t("assignments.video.connectionType");
  if (key === "REFLEX") return t("assignments.video.reflexType");
  return t(`assignments.batch.skillLabel.${key}`);
}

/** Khoá nhóm dùng khi 1 thẻ không gắn Unit nào (unitTitle null/rỗng) — luôn xếp cuối. */
const UNASSIGNED_UNIT_KEY = "__unassigned__";

/** Cấp điều hướng đang xem — mirror NavView bên AssignmentsTab.tsx (Học sinh), bỏ cấp phân trang vì danh sách phụ huynh xem thường ngắn. */
type NavView = { level: "units" } | { level: "skills"; unitKey: string } | { level: "list"; unitKey: string; skillKey: SkillKey };

interface ChannelStyle {
  icon: LucideIcon;
  bg: string;
  border: string;
  iconColor: string;
  labelColor: string;
}

const CHANNEL_STYLE: Record<Kind, ChannelStyle> = {
  grammar: { icon: BookOpen, bg: "bg-sky-2", border: "border-teal/20", iconColor: "text-teal-deep", labelColor: "text-teal-deep" },
  reading: { icon: FileText, bg: "bg-indigo-50/60", border: "border-indigo-200", iconColor: "text-indigo-700", labelColor: "text-indigo-800" },
  writing: { icon: PenLine, bg: "bg-violet-50/60", border: "border-violet-200", iconColor: "text-violet-700", labelColor: "text-violet-800" },
  video: { icon: Video, bg: "bg-amber-50/60", border: "border-amber-200", iconColor: "text-amber-700", labelColor: "text-amber-800" }
};

/**
 * 1 thẻ BTVN (Ngữ pháp/Đọc/Viết/Video) — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22.
 * "Tách phẳng" (flatten) mỗi kênh của mỗi buổi thành 1 thẻ riêng (mirror cách Portal Học sinh hiện mỗi
 * Bài/Lô/Video là 1 thẻ) thay vì gộp cả buổi vào 1 khối như bản trước — bản trước chỉ hiện %, nhìn
 * "na ná" tab Quá trình học tập; giờ thêm đủ badge loại bài/GV phụ trách/hạn nộp/quá hạn giống hệt thẻ
 * bên Học sinh (chỉ bỏ nút hành động — Phụ huynh chỉ xem, không thao tác làm bài, UC-64).
 */
interface HomeworkCard {
  key: string;
  kind: Kind;
  skillKey: SkillKey;
  unitTitle: string | null;
  typeLabel: string;
  teacherLabel: string;
  title: string;
  progress: string | null;
  passed: boolean | null;
  dueAt: string | null;
  sessionDate: string;
  items: HomeworkSkillItemResponse[];
  status: SubmissionStatus;
  overdue: boolean;
}

interface UnitGroup {
  unitKey: string;
  unitLabel: string;
  cards: HomeworkCard[];
  skills: { skillKey: SkillKey; cards: HomeworkCard[] }[];
}

/** Nhóm thẻ (đã lọc theo tab trạng thái) theo Unit rồi theo Kỹ năng — mirror buildUnitGroups bên AssignmentsTab.tsx (Học sinh), bản rút gọn vì chỉ cần xem, không cần phân trang/thao tác. */
function buildUnitGroups(cards: HomeworkCard[], t: (key: string) => string): UnitGroup[] {
  const map = new Map<string, { label: string; cards: HomeworkCard[] }>();
  for (const card of cards) {
    const unitTitle = card.unitTitle?.trim();
    const unitKey = unitTitle || UNASSIGNED_UNIT_KEY;
    const group = map.get(unitKey) ?? { label: unitTitle || t("assignments.nav.uncategorizedUnit"), cards: [] };
    group.cards.push(card);
    map.set(unitKey, group);
  }
  return [...map.entries()]
    .sort(([a], [b]) => {
      if (a === UNASSIGNED_UNIT_KEY) return 1;
      if (b === UNASSIGNED_UNIT_KEY) return -1;
      return a.localeCompare(b, "vi", { numeric: true, sensitivity: "base" });
    })
    .map(([unitKey, group]) => ({
      unitKey,
      unitLabel: group.label,
      cards: group.cards,
      skills: SKILL_ORDER.map((skillKey) => ({ skillKey, cards: group.cards.filter((c) => c.skillKey === skillKey) })).filter(
        (s) => s.cards.length > 0
      )
    }));
}

function submissionStatus(progress: string | null, passed: boolean | null): SubmissionStatus {
  if (!progress || progress === "Chưa làm bài") return "PENDING";
  if (passed == null) return "SUBMITTED";
  return "GRADED";
}

function buildCards(rows: HomeworkProgressResponse[], t: (key: string, opts?: Record<string, unknown>) => string): HomeworkCard[] {
  const cards: HomeworkCard[] = [];
  for (const row of rows) {
    if (row.grammarTitle) {
      const isForeign = row.grammarSkillCategory === "LISTENING";
      cards.push({
        key: `grammar-${row.commentId}`,
        kind: "grammar",
        skillKey: isForeign ? "LISTENING" : "VOCAB_GRAMMAR",
        unitTitle: row.grammarUnitTitle,
        typeLabel: isForeign ? t("assignments.batch.skillLabel.LISTENING") : t("assignments.batch.skillLabel.VOCAB_GRAMMAR"),
        teacherLabel: isForeign ? t("assignments.exercise.teacherForeign") : t("assignments.exercise.teacherVietnamese"),
        title: row.grammarTitle,
        progress: row.grammarProgress,
        passed: row.grammarPassed,
        dueAt: row.grammarDueAt,
        sessionDate: row.commentDate,
        items: row.grammarItems,
        status: submissionStatus(row.grammarProgress, row.grammarPassed),
        overdue: row.grammarDueAt != null && new Date(row.grammarDueAt) < new Date()
      });
    }
    if (row.readingTitle) {
      cards.push({
        key: `reading-${row.commentId}`,
        kind: "reading",
        skillKey: "READING",
        unitTitle: row.readingUnitTitle,
        typeLabel: t("assignments.batch.skillLabel.READING"),
        teacherLabel: t("assignments.exercise.teacherVietnamese"),
        title: row.readingTitle,
        progress: row.readingProgress,
        passed: row.readingPassed,
        dueAt: row.readingDueAt,
        sessionDate: row.commentDate,
        items: row.readingItems,
        status: submissionStatus(row.readingProgress, row.readingPassed),
        overdue: row.readingDueAt != null && new Date(row.readingDueAt) < new Date()
      });
    }
    if (row.writingTitle) {
      cards.push({
        key: `writing-${row.commentId}`,
        kind: "writing",
        skillKey: "WRITING",
        unitTitle: row.writingUnitTitle,
        typeLabel: t("assignments.batch.skillLabel.WRITING"),
        teacherLabel: t("assignments.exercise.teacherVietnamese"),
        title: row.writingTitle,
        progress: row.writingProgress,
        passed: row.writingPassed,
        dueAt: row.writingDueAt,
        sessionDate: row.commentDate,
        items: row.writingItems,
        status: submissionStatus(row.writingProgress, row.writingPassed),
        overdue: row.writingDueAt != null && new Date(row.writingDueAt) < new Date()
      });
    }
    if (row.videoTitle) {
      const isForeign = row.videoTeacherType === "FOREIGN";
      cards.push({
        key: `video-${row.commentId}`,
        kind: "video",
        skillKey: row.videoType === "CONNECTION" ? "CONNECTION" : "REFLEX",
        unitTitle: row.videoUnitTitle,
        typeLabel: row.videoType === "REFLEX" ? t("assignments.video.reflexType") : t("assignments.video.connectionType"),
        teacherLabel: isForeign ? t("assignments.exercise.teacherForeign") : t("assignments.exercise.teacherVietnamese"),
        title: row.videoTitle,
        progress: row.videoProgress,
        passed: row.videoPassed,
        dueAt: row.videoDueAt,
        sessionDate: row.commentDate,
        items: [],
        status: submissionStatus(row.videoProgress, row.videoPassed),
        overdue: row.videoDueAt != null && new Date(row.videoDueAt) < new Date()
      });
    }
  }
  return cards;
}

function HomeworkCardView({
  card,
  expanded,
  onToggleExpand,
  language,
  highlighted
}: {
  card: HomeworkCard;
  expanded: boolean;
  onToggleExpand: () => void;
  language: string;
  /** Plan link hoá thông báo (2026-09-22) — bấm thông báo/link nhảy tới đúng thẻ này, viền nổi tạm ~2.5s mirror khối "Ghi chú ngoài giờ". */
  highlighted?: boolean;
}) {
  const { t } = useTranslation("portal-exercises");
  const style = CHANNEL_STYLE[card.kind];
  const Icon = style.icon;
  const statusBadge =
    card.status === "PENDING"
      ? { label: t("assignments.filters.labelPending"), className: "bg-slate-100 text-slate-700 border-slate-200" }
      : card.status === "SUBMITTED"
        ? { label: t("assignments.video.submitted"), className: "bg-sky text-teal-deep border-teal/20" }
        : { label: t("assignments.filters.labelGraded"), className: "bg-teal/10 text-teal-deep border-teal/20" };

  return (
    <div className={`p-5 rounded-2xl border ${style.bg} space-y-2.5 transition-all ${highlighted ? "border-teal ring-2 ring-teal/40" : style.border}`}>
      <div className="flex items-center gap-2 flex-wrap text-[13px] font-black">
        <span className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-lg border ${style.iconColor} ${style.border} bg-white/70`}>
          <Icon size={14} aria-hidden="true" /> {card.typeLabel}
        </span>
        <span className={`px-2.5 py-0.5 rounded-lg border ${statusBadge.className}`}>{statusBadge.label}</span>
        {card.overdue && (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-lg border bg-coral/10 text-coral border-coral/20">
            <AlertCircle size={14} aria-hidden="true" />
            {t("assignments.exercise.overduePrefix")}
            {formatDateTimeHm(card.dueAt!, language)}
          </span>
        )}
        <span className="px-2.5 py-0.5 rounded-lg border bg-white/70 text-muted border-line/60">{card.teacherLabel}</span>
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-lg border bg-white/70 text-muted border-line/60">
          <CalendarClock size={14} aria-hidden="true" />
          {t("assignments.exercise.assignedInSession", { date: formatDate(card.sessionDate, language) })}
        </span>
      </div>

      <h3 className="text-xl font-black text-ink font-display truncate">{card.title}</h3>
      <ProgressBadge progress={card.progress} passed={card.passed} />

      {card.items.length > 1 && (
        <>
          <button
            type="button"
            onClick={onToggleExpand}
            className={`flex items-center gap-1.5 text-sm font-black hover:underline ${style.labelColor}`}
          >
            {expanded ? <ChevronUp size={14} aria-hidden="true" /> : <ChevronDown size={14} aria-hidden="true" />}
            {expanded ? t("parentHomework.hideDetails") : t("parentHomework.viewDetails", { count: card.items.length })}
          </button>
          {expanded && (
            <div className="space-y-2 border-t border-line/50 pt-2.5">
              {card.items.map((it) => (
                <div key={it.exerciseAssignmentId} className="rounded-xl bg-white/70 border border-line/40 px-3 py-2">
                  <p className="text-sm font-bold text-ink truncate">{it.title}</p>
                  <ProgressBadge progress={it.progress} passed={it.passed} />
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}

/**
 * Thẻ điều hướng Unit/Kỹ năng — bản rút gọn của NavCard bên AssignmentsTab.tsx (Học sinh): giữ đúng ý
 * "biết ngay còn cần hoàn thành/quá hạn hay đã xong" qua badge góc trên, bỏ phần preview 2 tên bài +
 * tông màu xoay vòng (không cốt lõi cho bản chỉ-xem).
 */
function NavCard({ title, icon: Icon, cards, onOpen }: { title: string; icon: LucideIcon; cards: HomeworkCard[]; onOpen: () => void }) {
  const { t } = useTranslation("portal-exercises");
  const overdueCount = cards.filter((c) => c.status === "PENDING" && c.overdue).length;
  const pendingCount = cards.filter((c) => c.status === "PENDING" && !c.overdue).length;
  return (
    <button
      type="button"
      onClick={onOpen}
      className="text-left p-5 rounded-2xl bg-white border border-line/80 hover:border-teal/50 hover:shadow-md transition-all space-y-2.5"
    >
      <div className="flex items-center justify-between gap-2">
        <div className="w-12 h-12 rounded-xl bg-teal/10 text-teal-deep flex items-center justify-center shrink-0">
          <Icon size={24} aria-hidden="true" />
        </div>
        {overdueCount > 0 ? (
          <span className="px-2.5 py-1 rounded-full bg-coral/10 text-coral border border-coral/20 text-xs font-black flex items-center gap-1 whitespace-nowrap">
            <AlertCircle size={12} aria-hidden="true" /> {t("assignments.nav.overdueBadge")}
          </span>
        ) : pendingCount > 0 ? (
          <span className="px-2.5 py-1 rounded-full bg-amber-50 text-amber-700 border border-amber-200 text-xs font-black flex items-center gap-1 whitespace-nowrap">
            <Clock size={12} aria-hidden="true" /> {t("assignments.nav.pendingBadge", { count: pendingCount })}
          </span>
        ) : (
          <span className="px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 text-xs font-black flex items-center gap-1 whitespace-nowrap">
            <CheckCircle2 size={12} aria-hidden="true" /> {t("assignments.nav.doneBadge")}
          </span>
        )}
      </div>
      <h3 className="text-xl font-black text-ink font-display truncate">{title}</h3>
      <p className="text-sm text-muted font-bold">{t("assignments.nav.itemCount", { count: cards.length })}</p>
    </button>
  );
}

interface ParentHomeworkProgressTabProps {
  studentId: number;
  classId: number;
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — bấm link "Bài ngữ pháp/nghe"/"Video
   * TKN/PX" ở tab Quá trình học tập (DailyLearningProgressTab) nhảy sang đây, PortalPage set commentId
   * của đúng buổi đó. Phụ huynh chỉ XEM (không có màn làm bài riêng như học sinh — UC-64), nên "nhảy
   * tới bài làm" ở đây nghĩa là cuộn tới + highlight tạm đúng thẻ tương ứng thay vì mở modal.
   */
  highlightCommentId?: number | null;
  /**
   * Plan link hoá thông báo (2026-09-22): bấm thông báo BTVN sắp/quá hạn ở quả chuông — cùng cơ chế
   * cuộn + nổi viền như highlightCommentId nhưng khớp theo id bản giao (grammarAssignmentId/
   * videoAssignmentId của từng dòng) vì thông báo không biết commentId. Ưu tiên: commentId > exercise > video.
   */
  highlightExerciseAssignmentId?: number | null;
  highlightReviewVideoAssignmentId?: number | null;
  onHighlightHandled?: () => void;
}

/** UC-64 (2026-07-29) — Cổng phụ huynh xem tiến độ BTVN đã giao cho con (chỉ xem, không phải giao diện làm bài — con tự làm ở Portal Học sinh). */
export default function ParentHomeworkProgressTab({
  studentId,
  classId,
  highlightCommentId,
  highlightExerciseAssignmentId,
  highlightReviewVideoAssignmentId,
  onHighlightHandled
}: ParentHomeworkProgressTabProps) {
  const { t, i18n } = useTranslation("portal-exercises");
  const [rows, setRows] = useState<HomeworkProgressResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  /** Đích đang cuộn tới + nổi viền tạm ~2.5s — `row-{commentId}` (khối "Ghi chú ngoài giờ") hoặc `card.key`
   *  (thẻ BTVN thường, VD "grammar-123"). null = không có gì đang nổi bật. */
  const [highlightedKey, setHighlightedKey] = useState<string | null>(null);
  const [expandedCards, setExpandedCards] = useState<Set<string>>(new Set());
  const [filterStatus, setFilterStatus] = useState<FilterStatus>("ALL");
  const toggleCard = (key: string) =>
    setExpandedCards((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });

  useEffect(() => {
    setLoading(true);
    setError(null);
    listHomeworkProgress(studentId, classId)
      .then(setRows)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("parentHomework.loadError")))
      .finally(() => setLoading(false));
  }, [studentId, classId]);

  const cards = useMemo(() => buildCards(rows, t), [rows, t]);
  const offlineRows = rows.filter((r) => r.grammarOfflineText || r.readingOfflineText || r.writingOfflineText);

  const tabAllCount = cards.length;
  const tabPendingCount = cards.filter((c) => c.status === "PENDING" && !c.overdue).length;
  const tabGradedCount = cards.filter((c) => c.status !== "PENDING").length;
  const tabOverdueCount = cards.filter((c) => c.status === "PENDING" && c.overdue).length;

  const filteredCards = cards.filter((c) => {
    if (filterStatus === "PENDING") return c.status === "PENDING" && !c.overdue;
    if (filterStatus === "GRADED") return c.status !== "PENDING";
    if (filterStatus === "OVERDUE") return c.status === "PENDING" && c.overdue;
    return true;
  });

  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22 — điều hướng Unit → Kỹ năng giống
  // Portal Học sinh (mirror NavView bên AssignmentsTab.tsx), chỉ khác ở đây là "chỉ xem". Nhóm luôn
  // TRÊN filteredCards (đã qua tab trạng thái) để số đếm ở thẻ Unit/Kỹ năng khớp đúng tab đang chọn.
  const unitGroups = useMemo(() => buildUnitGroups(filteredCards, t), [filteredCards, t]);
  const [navView, setNavView] = useState<NavView>({ level: "units" });
  // Đổi tab trạng thái mà đang đứng sâu trong 1 Unit/Kỹ năng dễ rơi vào nhánh rỗng (VD lọc "Quá hạn"
  // nhưng Unit đang xem không có bài nào quá hạn) — về lại cấp Unit cho chắc, mirror hành vi bấm
  // breadcrumb gốc bên AssignmentsTab.
  useEffect(() => setNavView({ level: "units" }), [filterStatus]);
  const currentUnit = navView.level !== "units" ? unitGroups.find((u) => u.unitKey === navView.unitKey) : undefined;
  const currentSkill = navView.level === "list" ? currentUnit?.skills.find((s) => s.skillKey === navView.skillKey) : undefined;

  /**
   * Bước 1/2 — suy ra đích cần cuộn tới từ commentId (link từ tab Quá trình học tập, chỉ khớp khối "Ghi
   * chú ngoài giờ" — buổi có commentId nhưng không giao bài online thì không có thẻ BTVN nào) hoặc id
   * bản giao (Plan link hoá thông báo, 2026-09-22 — thông báo BTVN sắp/quá hạn không biết commentId, chỉ
   * biết grammarAssignmentId/readingAssignmentId/writingAssignmentId/videoAssignmentId). Ép filter "Tất
   * cả" + mở đúng Unit→Kỹ năng chứa thẻ (thẻ chỉ có trong DOM khi navView đang ở đúng cấp "list") rồi mới
   * lưu đích vào highlightedKey — cuộn thật sự nằm ở effect bước 2 vì setNavView chưa render kịp ở đây.
   */
  useEffect(() => {
    if (loading) return;
    if (highlightCommentId != null) {
      setFilterStatus("ALL");
      setNavView({ level: "units" });
      setHighlightedKey(`row-${highlightCommentId}`);
      return;
    }
    if (highlightExerciseAssignmentId == null && highlightReviewVideoAssignmentId == null) return;
    const row = rows.find(
      (r) =>
        (highlightExerciseAssignmentId != null &&
          (r.grammarAssignmentId === highlightExerciseAssignmentId ||
            r.readingAssignmentId === highlightExerciseAssignmentId ||
            r.writingAssignmentId === highlightExerciseAssignmentId)) ||
        (highlightReviewVideoAssignmentId != null && r.videoAssignmentId === highlightReviewVideoAssignmentId)
    );
    // Chưa khớp dòng nào (rows của lớp cũ còn hiện lúc đổi con/lớp, hoặc chưa tải xong) — giữ nguyên
    // pending (không gọi onHighlightHandled) để effect này thử lại khi rows đổi.
    if (!row) return;
    const kind: Kind | null =
      highlightExerciseAssignmentId != null
        ? row.grammarAssignmentId === highlightExerciseAssignmentId
          ? "grammar"
          : row.readingAssignmentId === highlightExerciseAssignmentId
            ? "reading"
            : row.writingAssignmentId === highlightExerciseAssignmentId
              ? "writing"
              : null
        : "video";
    const card = kind != null ? cards.find((c) => c.key === `${kind}-${row.commentId}`) : undefined;
    if (!card) return;
    setFilterStatus("ALL");
    setNavView({ level: "list", unitKey: card.unitTitle?.trim() || UNASSIGNED_UNIT_KEY, skillKey: card.skillKey });
    setHighlightedKey(card.key);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading, highlightCommentId, highlightExerciseAssignmentId, highlightReviewVideoAssignmentId, rows, cards]);

  // Bước 2/2 — cuộn + xoá nổi bật sau ~2.5s, chạy lại mỗi khi navView/filterStatus đổi (đợi DOM render
  // đúng thẻ/khối theo điều hướng effect trên vừa set) cho tới khi phần tử đích thực sự xuất hiện.
  useEffect(() => {
    if (!highlightedKey) return;
    const domId = highlightedKey.startsWith("row-") ? highlightedKey.slice(4) : highlightedKey;
    const el = document.getElementById(`parent-homework-comment-${domId}`);
    if (!el) return;
    el.scrollIntoView({ behavior: "smooth", block: "center" });
    onHighlightHandled?.();
    const timer = setTimeout(() => setHighlightedKey(null), 2500);
    return () => clearTimeout(timer);
  }, [highlightedKey, navView, filterStatus, onHighlightHandled]);

  if (loading) return <p className="text-sm text-muted font-bold">{t("parentHomework.loading")}</p>;

  const tabs: { key: FilterStatus; label: string; count: number }[] = [
    { key: "ALL", label: t("assignments.filters.labelAll"), count: tabAllCount },
    { key: "PENDING", label: t("assignments.filters.labelPending"), count: tabPendingCount },
    { key: "GRADED", label: t("assignments.filters.labelGraded"), count: tabGradedCount },
    { key: "OVERDUE", label: t("assignments.filters.labelOverdue"), count: tabOverdueCount }
  ];

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-xl font-black text-ink font-display">{t("parentHomework.title")}</h2>
        <p className="text-sm text-muted font-bold mt-0.5">{t("parentHomework.description")}</p>
      </div>

      {error && <div className="text-sm font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

      {cards.length === 0 && offlineRows.length === 0 ? (
        <p className="text-sm text-muted font-bold italic text-center py-10">{t("parentHomework.empty")}</p>
      ) : (
        <>
          <div className="flex flex-wrap gap-2">
            {tabs.map((tab) => (
              <button
                key={tab.key}
                type="button"
                onClick={() => setFilterStatus(tab.key)}
                className={`px-4 py-2 rounded-xl text-sm font-bold border transition-colors ${
                  filterStatus === tab.key ? "bg-teal text-white border-teal-deep" : "bg-white text-ink border-line/80 hover:bg-sky-2"
                }`}
              >
                {tab.label} ({tab.count})
              </button>
            ))}
          </div>

          <Breadcrumb
            items={(() => {
              const root: BreadcrumbItem = {
                label: t("assignments.nav.breadcrumbRoot"),
                onClick: navView.level !== "units" ? () => setNavView({ level: "units" }) : undefined
              };
              if (navView.level === "units") return [root];
              const unitLabel = currentUnit?.unitLabel ?? (navView.unitKey === UNASSIGNED_UNIT_KEY ? t("assignments.nav.uncategorizedUnit") : navView.unitKey);
              if (navView.level === "skills") return [root, { label: unitLabel }];
              return [
                root,
                { label: unitLabel, onClick: () => setNavView({ level: "skills", unitKey: navView.unitKey }) },
                { label: skillLabel(t, navView.skillKey) }
              ];
            })()}
            className="px-3.5 py-1.5 rounded-full bg-white border border-line/80 w-fit max-w-full"
          />

          {navView.level === "units" ? (
            unitGroups.length === 0 ? (
              <p className="text-sm text-muted font-bold italic text-center py-10">{t("assignments.nav.emptyUnitGroup")}</p>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {unitGroups.map((unit) => (
                  <NavCard key={unit.unitKey} title={unit.unitLabel} icon={BookOpen} cards={unit.cards} onOpen={() => setNavView({ level: "skills", unitKey: unit.unitKey })} />
                ))}
              </div>
            )
          ) : !currentUnit ? (
            <div className="text-center py-10 space-y-3">
              <p className="text-sm text-muted font-bold italic">{t("assignments.nav.emptySkillGroup")}</p>
              <button
                type="button"
                onClick={() => setNavView({ level: "units" })}
                className="px-4 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-ink text-sm font-black transition-colors"
              >
                {t("assignments.nav.backToUnits")}
              </button>
            </div>
          ) : navView.level === "skills" ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
              {currentUnit.skills.map((skill) => (
                <NavCard
                  key={skill.skillKey}
                  title={skillLabel(t, skill.skillKey)}
                  icon={SKILL_ICON[skill.skillKey]}
                  cards={skill.cards}
                  onOpen={() => setNavView({ level: "list", unitKey: currentUnit.unitKey, skillKey: skill.skillKey })}
                />
              ))}
            </div>
          ) : !currentSkill ? (
            <div className="text-center py-10 space-y-3">
              <p className="text-sm text-muted font-bold italic">{t("assignments.nav.emptyAssignmentGroup")}</p>
              <button
                type="button"
                onClick={() => setNavView({ level: "skills", unitKey: navView.unitKey })}
                className="px-4 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-ink text-sm font-black transition-colors"
              >
                {t("assignments.nav.backToSkills")}
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              {currentSkill.cards.map((card) => (
                <div key={card.key} id={`parent-homework-comment-${card.key}`}>
                  <HomeworkCardView
                    card={card}
                    expanded={expandedCards.has(card.key)}
                    onToggleExpand={() => toggleCard(card.key)}
                    language={i18n.language}
                    highlighted={highlightedKey === card.key}
                  />
                </div>
              ))}
            </div>
          )}

          {filterStatus === "ALL" && navView.level === "units" && offlineRows.length > 0 && (
            <div className="space-y-3 pt-2 border-t border-line/60">
              {offlineRows.map((row) => (
                <div
                  key={`offline-${row.commentId}`}
                  id={`parent-homework-comment-${row.commentId}`}
                  className={`bg-white border rounded-2xl p-4 space-y-2 transition-all ${
                    highlightedKey === `row-${row.commentId}` ? "border-teal ring-2 ring-teal/40" : "border-line/80"
                  }`}
                >
                  <div className="flex items-center gap-1.5 text-xs font-black text-muted uppercase">
                    <Clock size={13} aria-hidden="true" /> {row.commentDate}
                  </div>
                  {row.grammarOfflineText && (
                    <p className="text-sm font-bold text-ink">
                      <span className="text-teal-deep uppercase">{t("parentHomework.grammarLabel")}: </span>
                      {row.grammarOfflineText}
                    </p>
                  )}
                  {row.readingOfflineText && (
                    <p className="text-sm font-bold text-ink">
                      <span className="text-indigo-800 uppercase">{t("parentHomework.readingLabel")}: </span>
                      {row.readingOfflineText}
                    </p>
                  )}
                  {row.writingOfflineText && (
                    <p className="text-sm font-bold text-ink">
                      <span className="text-violet-800 uppercase">{t("parentHomework.writingLabel")}: </span>
                      {row.writingOfflineText}
                    </p>
                  )}
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}
