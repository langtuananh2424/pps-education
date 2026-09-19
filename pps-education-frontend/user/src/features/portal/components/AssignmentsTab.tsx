import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  AlertCircle,
  Bell,
  BookOpen,
  CalendarDays,
  CheckCircle2,
  ChevronRight,
  Clock,
  FileText,
  GraduationCap,
  Headphones,
  Layers,
  Link2,
  Lock,
  MessageCircle,
  Mic,
  PenLine,
  Play,
  Rocket,
  Sparkles,
  Star,
  Video,
  X
} from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { formatDate, formatDateTimeHm } from "@/lib/format";
import {
  AssignedExerciseResponse,
  MyReviewVideoAssignmentResponse,
  ReviewVideoResponse,
  getReviewVideoProgress,
  listMyAssignedExercises,
  listMyReflexProgress,
  listMyReviewVideoAssignments,
  listReviewVideoQuestions,
  listReviewVideoSetsByClass,
  listReviewVideos
} from "../api";
import TakeExerciseModal from "./TakeExerciseModal";
import BatchTakeExerciseModal, { batchGroupTitle } from "./BatchTakeExerciseModal";
import ReviewVideoTaskModal from "./ReviewVideoTaskModal";
import ReflexVideoTaskPage from "../pages/ReflexVideoTaskPage";
import Pagination from "@/components/ui/Pagination";
import Breadcrumb, { BreadcrumbItem } from "@/components/ui/Breadcrumb";

const PAGE_SIZE = 10;

interface AssignmentsTabProps {
  classId: number;
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — bấm link "Bài ngữ pháp/nghe"/
   * "Video TKN/PX" ở tab Quá trình học tập (DailyLearningProgressTab) nhảy sang đây, PortalPage set 2
   * prop này để tự nhảy trang (nếu cần) + cuộn tới + nổi viền đúng card đó — KHÔNG tự mở modal làm bài
   * (theo yêu cầu người dùng, chỉ cần định vị bằng mắt). Chỉ dùng 1 LẦN rồi phải gọi onAutoOpenHandled
   * để PortalPage clear về null, tránh chạy lại mỗi khi load() chạy lại (VD sau khi đóng modal khác).
   */
  autoOpenExerciseAssignmentId?: number | null;
  autoOpenReviewVideoAssignmentId?: number | null;
  onAutoOpenHandled?: () => void;
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — báo số "Cần hoàn thành" lên
   * PortalPage để hiện badge cảnh báo trên mục "Bài tập về nhà (BTVN)" ở sidebar, kể cả khi đang xem
   * tab khác (component này unmount khi rời tab — PortalPage tự giữ lại giá trị lần tính gần nhất).
   */
  onPendingCountChange?: (count: number) => void;
}

/** PATTERN — map nhãn theo t() live (không còn Record tĩnh ở module scope) vì cần đổi ngôn ngữ theo i18next. */
function attemptStatusMeta(t: (key: string) => string, status: string): { label: string; className: string } | null {
  switch (status) {
    case "IN_PROGRESS":
      return { label: t("assignments.attemptStatus.inProgress"), className: "bg-gold/10 text-gold" };
    case "AUTO_GRADED":
      return { label: t("assignments.attemptStatus.autoGraded"), className: "bg-sky text-teal-deep" };
    case "FULLY_GRADED":
      return { label: t("assignments.attemptStatus.fullyGraded"), className: "bg-teal/10 text-teal-deep" };
    default:
      return null;
  }
}

/** FULLY_GRADED nhưng dưới ngưỡng đạt — "trượt" nói chung, chưa phân biệt còn làm lại được hay không (xem needsRetake/failedNoMoreRetakes bên dưới). */
function failedGraded(item: AssignedExerciseResponse): boolean {
  return item.myLatestAttemptStatus === "FULLY_GRADED" && item.myLatestPassed === false;
}

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05 (backend V89
 * `ExerciseAttemptService#applyPassOutcome`) — BTVN đã chấm xong (FULLY_GRADED)
 * nhưng dưới ngưỡng đạt (`myLatestPassed === false`) vẫn tính là "cần hoàn
 * thành" (chưa xong thật sự), không phải "đã nộp & đã chấm" — bản giao vẫn
 * ACTIVE ở backend đúng tinh thần này, FE trước đây chưa đồng bộ theo.
 *
 * Bổ sung 2026-09-13 (fix bug thật, đã xác nhận với người dùng) — CHỈ còn đúng khi thật sự CÒN làm lại
 * được (`item.canStartNewAttempt` — BE đã tính đủ allowRetake/maxAttempts/hạn nộp). Trước đây bất kỳ
 * bài "chưa đạt" nào cũng bị coi "cần làm lại" dù đã hết lượt hoặc đề không cho làm lại — hiện nhầm
 * badge "cần làm lại" (không còn gì để làm) và tính nhầm vào đếm/tab "Cần hoàn thành" + banner nhắc nhở
 * ở trang chủ. Trường hợp trượt hẳn không còn lượt → xem failedNoMoreRetakes bên dưới, coi như ĐÃ XONG.
 */
function needsRetake(item: AssignedExerciseResponse): boolean {
  return failedGraded(item) && item.canStartNewAttempt;
}

/** Mirror needsRetake — trượt nhưng KHÔNG còn làm lại được nữa (hết lượt/đề không cho làm lại) — coi như đã xong, chỉ còn xem lại. */
function failedNoMoreRetakes(item: AssignedExerciseResponse): boolean {
  return failedGraded(item) && !item.canStartNewAttempt;
}

function isExercisePending(item: AssignedExerciseResponse): boolean {
  return item.myLatestAttemptStatus == null || item.myLatestAttemptStatus === "IN_PROGRESS" || needsRetake(item);
}

/** V150 — 1 Lô "Cần hoàn thành" khi CÒN ÍT NHẤT 1 Bài trong đó pending (mirror isExercisePending, áp dụng cho cả nhóm thay vì 1 Bài đơn). */
function isBatchPending(items: AssignedExerciseResponse[]): boolean {
  return items.some(isExercisePending);
}

/**
 * V152 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — 1 Bài "quá hạn" khi còn pending
 * (chưa hoàn thành xong) VÀ đã qua dueAt — dùng cho tab lọc mới "Bài tập quá hạn" (khác badge quá hạn
 * đã có sẵn trên từng thẻ, cái đó chỉ hiện thị chứ không lọc được thành danh sách riêng).
 */
function isExerciseOverduePending(item: AssignedExerciseResponse): boolean {
  return isExercisePending(item) && item.dueAt != null && new Date(item.dueAt) < new Date();
}

/** V152 — mirror isExerciseOverduePending, áp dụng cho cả nhóm Lô (dùng chung dueAt của Bài đại diện, mirror BatchExerciseCard). */
function isBatchOverduePending(items: AssignedExerciseResponse[]): boolean {
  return isBatchPending(items) && items[0].dueAt != null && new Date(items[0].dueAt) < new Date();
}

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 (chỉnh lại cùng ngày) — tập con hẹp hơn
 * isExercisePending, dùng RIÊNG cho khóa: CHỈ "chưa làm bao giờ" (myLatestAttemptStatus null) hoặc "đang
 * làm dở, chưa nộp" (IN_PROGRESS). Bài ĐÃ nộp và CÓ KẾT QUẢ rồi (kể cả trượt còn lượt làm lại —
 * needsRetake) thì KHÔNG thuộc diện bị khóa dù quá hạn — người dùng xác nhận rõ "làm rồi có kết quả thì
 * hết hạn không tính vào case khóa", khác hẳn isExercisePending (dùng cho đếm "Cần hoàn thành"/tab lọc,
 * vẫn tính cả needsRetake là pending, không đổi).
 */
function isExerciseLockable(item: AssignedExerciseResponse): boolean {
  return item.myLatestAttemptStatus == null || item.myLatestAttemptStatus === "IN_PROGRESS";
}

/** Mirror isExerciseLockable, áp dụng cho cả nhóm Lô — Lô có thể khóa khi CÒN ÍT NHẤT 1 Bài trong đó lockable. */
function isBatchLockable(items: AssignedExerciseResponse[]): boolean {
  return items.some(isExerciseLockable);
}

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — quá hạn chỉ có nghĩa THẬT SỰ hết cửa
 * thao tác khi (a) Bài thuộc diện lockable (xem isExerciseLockable) VÀ (b) bản giao KHÔNG cho nộp muộn
 * (lateSubmissionAllowed=false), HOẶC có cho nộp muộn nhưng đã set 1 hạn chót cụ thể
 * (lateSubmissionDeadline) và hạn đó cũng đã qua. Quá hạn nhưng vẫn cho nộp muộn KHÔNG GIỚI HẠN
 * (lateSubmissionAllowed=true, lateSubmissionDeadline=null) thì học sinh còn làm được bình thường —
 * không được coi là khóa. Dùng để đổi nút hành động ("Làm bài ngay"/"Tiếp tục làm bài"/"Xem lại bài đã
 * làm") thành "Đã khóa" (không bấm được nữa) thay vì mời bấm vào rồi mới báo lỗi hết hạn bên trong modal.
 * Mirror ExerciseAssignment#isPastEffectiveDeadline (backend).
 */
function isExerciseLocked(item: AssignedExerciseResponse): boolean {
  if (!isExerciseLockable(item)) return false;
  if (item.dueAt == null || new Date(item.dueAt) >= new Date()) return false;
  if (!item.lateSubmissionAllowed) return true;
  return item.lateSubmissionDeadline != null && new Date(item.lateSubmissionDeadline) < new Date();
}

/** Mirror isExerciseLocked, áp dụng cho cả nhóm Lô (dùng chung dueAt/lateSubmissionAllowed/lateSubmissionDeadline của Bài đại diện, nhưng "lockable" tính theo CẢ LÔ — mirror isBatchLockable). */
function isBatchLocked(items: AssignedExerciseResponse[]): boolean {
  if (!isBatchLockable(items)) return false;
  const first = items[0];
  if (first.dueAt == null || new Date(first.dueAt) >= new Date()) return false;
  if (!first.lateSubmissionAllowed) return true;
  return first.lateSubmissionDeadline != null && new Date(first.lateSubmissionDeadline) < new Date();
}

/**
 * V152 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — tách BIỆT HẲN 2 tab "Cần hoàn
 * thành"/"Bài tập quá hạn" (trước đó chồng nhau — bài quá hạn vẫn tính vào "Cần hoàn thành", gây hiểu
 * nhầm học sinh tưởng còn làm được). "Cần hoàn thành" giờ CHỈ còn Bài pending mà CHƯA quá hạn (còn
 * thao tác được thật sự) — Bài pending đã quá hạn chuyển HẲN sang tab "Bài tập quá hạn", không hiện
 * trùng ở đây nữa. Không đổi `isExercisePending` gốc (vẫn dùng cho style thẻ/badge "Cần làm lại" — thẻ
 * quá hạn vẫn cần hiện đúng các badge đó, chỉ khác chỗ nó không còn được ĐẾM/LỌC vào tab pending nữa).
 */
function isExerciseActionablePending(item: AssignedExerciseResponse): boolean {
  return isExercisePending(item) && !isExerciseOverduePending(item);
}

/** V152 — mirror isExerciseActionablePending, áp dụng cho cả nhóm Lô. */
function isBatchActionablePending(items: AssignedExerciseResponse[]): boolean {
  return isBatchPending(items) && !isBatchOverduePending(items);
}

/**
 * V150 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — gom N thẻ BTVN cùng
 * homeworkBatchId thành 1 nhóm hiển thị 1 thẻ duy nhất (xem BatchExerciseCard) — khớp đúng cách
 * backend giao TOÀN BỘ Bài cùng kỹ năng trong 1 Lesson cùng lúc (HomeworkSkillBatchService). Bài lẻ
 * (homeworkBatchId null) giữ nguyên hiển thị từng thẻ riêng như cũ. Sort ổn định theo exerciseId để
 * thứ tự Bài trong 1 lần làm liên tục không đổi giữa các lần load().
 */
function groupExercisesByBatch(exercises: AssignedExerciseResponse[]): {
  singles: AssignedExerciseResponse[];
  batches: AssignedExerciseResponse[][];
} {
  const singles: AssignedExerciseResponse[] = [];
  const byBatchId = new Map<number, AssignedExerciseResponse[]>();
  for (const item of exercises) {
    if (item.homeworkBatchId == null) {
      singles.push(item);
      continue;
    }
    const list = byBatchId.get(item.homeworkBatchId) ?? [];
    list.push(item);
    byBatchId.set(item.homeworkBatchId, list);
  }
  const batches = [...byBatchId.values()].map((list) => list.slice().sort((a, b) => a.exerciseId - b.exerciseId));
  return { singles, batches };
}

/** Video REFLEX chưa có câu hỏi nào (giáo viên chưa soạn xong) — chưa có gì để tính "hoàn thành". */
function isReflexAnswerable(item: ReviewVideoHomeworkItem): boolean {
  return item.videoType === "REFLEX" && !!item.reflexStats && item.reflexStats.totalQuestions > 0;
}

/** Ngưỡng % số câu đã trả lời để tính REFLEX "đạt" — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 (trước đây yêu cầu đủ 100%). */
const REFLEX_PASS_THRESHOLD_PERCENT = 80;

/** "Hoàn thành" (V57, sửa 2026-08-06) = trả lời đủ ngưỡng % câu hỏi trong video (mặc định 80%, không cần đủ 100%) — REFLEX không qua khâu giáo viên chấm điểm nữa (đã xác nhận với người dùng 2026-07-29), đạt ngưỡng là xong. */
function isReflexFullyAnswered(item: ReviewVideoHomeworkItem): boolean {
  if (!item.reflexStats || item.reflexStats.totalQuestions <= 0) return false;
  return (item.reflexStats.answeredQuestions / item.reflexStats.totalQuestions) * 100 >= REFLEX_PASS_THRESHOLD_PERCENT;
}

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — CONNECTION giờ tính được vào bộ
 * đếm Cần hoàn thành/Đã nộp nhờ API GET tiến độ mới (getReviewVideoProgress), trước đây không có
 * API đọc lại nên phải loại hẳn khỏi mọi bộ lọc trạng thái.
 */
function isConnectionAnswerable(item: ReviewVideoHomeworkItem): boolean {
  return item.videoType === "CONNECTION" && !!item.connectionStats;
}

function isConnectionCompleted(item: ReviewVideoHomeworkItem): boolean {
  return !!item.connectionStats?.completed;
}

/** V152 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — mirror isExerciseOverduePending, áp dụng cho Video ôn tập (REFLEX/CONNECTION). */
function isVideoOverduePending(item: ReviewVideoHomeworkItem): boolean {
  if (item.dueAt == null || new Date(item.dueAt) >= new Date()) return false;
  if (item.videoType === "CONNECTION") return isConnectionAnswerable(item) && !isConnectionCompleted(item);
  return isReflexAnswerable(item) && !isReflexFullyAnswered(item);
}

/** V152 — mirror isExerciseActionablePending, áp dụng cho Video ôn tập (tách biệt "Cần hoàn thành"/"Bài tập quá hạn"). */
function isVideoActionablePending(item: ReviewVideoHomeworkItem): boolean {
  if (isVideoOverduePending(item)) return false;
  if (item.videoType === "CONNECTION") return isConnectionAnswerable(item) && !isConnectionCompleted(item);
  return isReflexAnswerable(item) && !isReflexFullyAnswered(item);
}

/** Mirror isExerciseLocked, áp dụng cho Video ôn tập — xem Javadoc isExerciseLocked. */
function isVideoLocked(item: ReviewVideoHomeworkItem): boolean {
  if (!isVideoOverduePending(item)) return false;
  if (item.lateSubmissionAllowed !== true) return true;
  return item.lateSubmissionDeadline != null && new Date(item.lateSubmissionDeadline) < new Date();
}

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — khi Bài/Lô/Video đã quá hạn nộp GỐC
 * nhưng CHƯA bị khóa (isExerciseLocked/isBatchLocked/isVideoLocked = false vì GV đã bật "cho phép nộp
 * muộn"), card vẫn mời "Tiếp tục làm bài"/"Trả lời câu hỏi" — nếu chỉ hiện badge "Đã quá hạn nộp" như
 * cũ sẽ gây hiểu lầm là bug (tưởng phải khóa mà chưa khóa). Trả về key i18n + hạn cụ thể (nếu có) để
 * hiện thêm 1 badge phụ màu khác giải thích rõ vẫn còn nộp muộn được, null nếu không cần hiện (chưa quá
 * hạn, hoặc đã khóa hẳn).
 */
function lateSubmissionHint(
  overduePending: boolean,
  locked: boolean,
  lateSubmissionAllowed: boolean | undefined,
  lateSubmissionDeadline: string | null | undefined
): { key: "assignments.exercise.lateSubmissionUntil" | "assignments.exercise.lateSubmissionUnlimited"; date?: string } | null {
  if (!overduePending || locked || !lateSubmissionAllowed) return null;
  if (lateSubmissionDeadline != null) return { key: "assignments.exercise.lateSubmissionUntil", date: lateSubmissionDeadline };
  return { key: "assignments.exercise.lateSubmissionUnlimited" };
}

type FilterStatus = "ALL" | "PENDING" | "GRADED" | "OVERDUE";
/**
 * V153 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — phân loại theo kỹ năng thật
 * (skillCategory) thay vì gộp chung mọi Bài không-phải-video vào 1 nhãn "Bài ngữ pháp" như trước (sai khi
 * lớp có cả Bài Nghe — xem AssignedExerciseResponse.skillCategory). OTHER_EXERCISE dành cho Bài chưa gắn
 * kỹ năng nào (skillCategory null, thường là bài tự luyện cũ trước khi có trường này). CONNECTION/REFLEX
 * là 2 loại Video ôn tập.
 *
 * Bổ sung 2026-09-19 (đã xác nhận với người dùng) — trước đây là dropdown lọc "loại bài", nay thành 1 CẤP
 * điều hướng riêng Unit → Kỹ năng → danh sách bài (xem NavView), nên dropdown cũ đã bỏ.
 */
type SkillKey = "VOCAB_GRAMMAR" | "READING" | "WRITING" | "LISTENING" | "OTHER_EXERCISE" | "CONNECTION" | "REFLEX";

/** Thứ tự hiển thị thẻ Kỹ năng: Bài tập trước (Ngữ pháp → Nghe → Reading → Writing → Khác), Video sau. */
const SKILL_ORDER: SkillKey[] = ["VOCAB_GRAMMAR", "LISTENING", "READING", "WRITING", "OTHER_EXERCISE", "CONNECTION", "REFLEX"];

function skillLabel(t: (key: string) => string, skill: SkillKey): string {
  if (skill === "OTHER_EXERCISE") return t("assignments.filters.typeOther");
  if (skill === "CONNECTION") return t("assignments.video.connectionType");
  if (skill === "REFLEX") return t("assignments.video.reflexType");
  return t(`assignments.batch.skillLabel.${skill}`);
}

interface ReviewVideoHomeworkItem {
  video: ReviewVideoResponse;
  videoType: "CONNECTION" | "REFLEX";
  setTitle: string;
  /** REFLEX only (V57 — video giờ có nhiều câu hỏi, mỗi câu tự nộp riêng) — dùng tính trạng thái tổng hợp cho cả video. */
  reflexStats?: { totalQuestions: number; answeredQuestions: number };
  /** CONNECTION only, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — đọc từ GET progress mới, undefined nếu API lỗi (loại khỏi bộ đếm, không hiện sai). */
  connectionStats?: { viewCount: number; requiredViewCount: number; completed: boolean };
  /** Hạn nộp của bộ (nếu bộ này đang được giao "BTVN buổi sau" ACTIVE cho lớp) — undefined nếu chỉ nằm trong Kho, không phải BTVN đang giao. */
  dueAt?: string;
  /**
   * id bản giao (`ReviewVideoAssignment`) nguồn của dueAt trên — undefined nếu chỉ nằm trong Kho
   * (không đang được giao). Fix 2026-08-12: 1 bộ video có thể bị giao LẶP LẠI ở nhiều buổi khác nhau
   * (mỗi buổi = 1 ReviewVideoAssignment/hạn nộp riêng, đúng V65) — cần assignmentId để tách thành
   * NHIỀU card thay vì gộp mất chỉ còn 1 hạn nộp (xem load() bên dưới).
   */
  assignmentId?: number;
  /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — mirror dueAt/assignmentId ở trên: undefined nếu chỉ nằm trong Kho (không phải BTVN đang giao). Xem isVideoLocked. */
  lateSubmissionAllowed?: boolean;
  /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — hạn chót nộp muộn cụ thể (null/undefined = không giới hạn). Xem isVideoLocked. */
  lateSubmissionDeadline?: string | null;
  /** V123 — GV Việt Nam/nước ngoài phụ trách bộ này (ReviewVideoSetResponse.teacherType, luôn có). */
  teacherType: "VIETNAMESE" | "FOREIGN";
  /** V123 — ngày buổi học GV đã giao BTVN này — undefined nếu chỉ nằm trong Kho hoặc bản giao TRƯỚC V123. */
  sessionDate?: string | null;
  /** Bổ sung 2026-09-04 — Unit/SubTopic chứa Bộ này (ReviewVideoSetResponse.unitTitle/subTopicTitle), xem ghi chú ở đó. */
  unitTitle?: string | null;
  subTopicTitle?: string | null;
}

/**
 * Điều hướng phân cấp Unit → Kỹ năng → danh sách bài ở màn BTVN (bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng 2026-09-18/19) — thay danh sách phẳng cũ. Ban đầu định làm Unit → Lesson → Bài tập, nhưng dữ
 * liệu hiện tại KHÔNG có field nào biểu diễn đúng "Lesson" cho Video ôn tập [chỉ Bài tập ngữ pháp/đọc/
 * viết/nghe có examTitle, Video chỉ có Unit/SubTopic] — cần đổi schema (thêm cột lesson_number + sửa 2
 * form Admin) mới làm đúng được, nên người dùng chọn cấp giữa là KỸ NĂNG (Ngữ pháp/Nghe/Reading/Writing/
 * Video kết nối/Video phản xạ — trước đây là dropdown lọc "loại bài"), dữ liệu đã có sẵn, kèm breadcrumb.
 */
type NavView =
  | { level: "units" }
  | { level: "skills"; unitKey: string }
  | { level: "assignments"; unitKey: string; skillKey: SkillKey };

/** Khoá nhóm dùng khi Bài/Video không gắn Unit (unitTitle null/rỗng) — luôn xếp cuối, xem buildUnitGroups. */
const UNASSIGNED_GROUP_KEY = "__unassigned__";

type FeedEntry =
  | { type: "exercise"; key: string; item: AssignedExerciseResponse }
  | { type: "exerciseBatch"; key: string; items: AssignedExerciseResponse[] }
  | { type: "video"; key: string; item: ReviewVideoHomeworkItem };

function entryUnitTitle(entry: FeedEntry): string | null | undefined {
  if (entry.type === "exercise") return entry.item.unitTitle;
  if (entry.type === "exerciseBatch") return entry.items[0].unitTitle;
  return entry.item.unitTitle;
}

/** Khoá kỹ năng của 1 entry: Bài lẻ/Lô theo skillCategory (null → OTHER_EXERCISE), Video theo videoType. */
function entrySkillKey(entry: FeedEntry): SkillKey {
  if (entry.type === "exercise") return entry.item.skillCategory ?? "OTHER_EXERCISE";
  if (entry.type === "exerciseBatch") return entry.items[0].skillCategory ?? "OTHER_EXERCISE";
  return entry.item.videoType;
}

function isEntryPending(entry: FeedEntry): boolean {
  if (entry.type === "exercise") return isExerciseActionablePending(entry.item);
  if (entry.type === "exerciseBatch") return isBatchActionablePending(entry.items);
  return isVideoActionablePending(entry.item);
}

/** Mirror isEntryPending — dùng tính số đếm "Đã nộp & Đã chấm" theo đúng phạm vi breadcrumb (xem
 * scopeEntries trong AssignmentsTab), khớp đúng logic gradedCount toàn lớp đã có sẵn. */
function isEntryGraded(entry: FeedEntry): boolean {
  if (entry.type === "exercise") return !isExercisePending(entry.item);
  if (entry.type === "exerciseBatch") return !isBatchPending(entry.items);
  const video = entry.item;
  if (video.videoType === "CONNECTION") return isConnectionAnswerable(video) && isConnectionCompleted(video);
  return isReflexAnswerable(video) && isReflexFullyAnswered(video);
}

/** Mirror isEntryPending — dùng tính số đếm "Bài tập quá hạn" theo đúng phạm vi breadcrumb. */
function isEntryOverduePending(entry: FeedEntry): boolean {
  if (entry.type === "exercise") return isExerciseOverduePending(entry.item);
  if (entry.type === "exerciseBatch") return isBatchOverduePending(entry.items);
  return isVideoOverduePending(entry.item);
}

/** So sánh tự nhiên (nhận biết số trong chuỗi, VD "Unit 2" < "Unit 10") — dùng sắp xếp thẻ Unit theo
 * đúng thứ tự số thay vì so sánh chuỗi thuần (sẽ ra "Unit 10" trước "Unit 2"). */
function naturalCompare(a: string, b: string): number {
  return a.localeCompare(b, "vi", { numeric: true, sensitivity: "base" });
}

interface SkillGroup {
  skillKey: SkillKey;
  entries: FeedEntry[];
}

interface UnitGroup {
  unitKey: string;
  unitLabel: string;
  /** Toàn bộ entries của Unit (mọi kỹ năng gộp lại) — dùng đếm tổng/cần hoàn thành ở UnitCard. */
  entries: FeedEntry[];
  /** Các kỹ năng CÓ bài trong Unit này, theo thứ tự SKILL_ORDER. */
  skills: SkillGroup[];
}

/**
 * Nhóm feedItems (đã lọc theo filterStatus, đã sort theo hạn nộp — xem AssignmentsTab) theo Unit rồi theo
 * Kỹ năng. Unit nhóm bằng chuỗi `unitTitle` sẵn có trên từng entry — nhánh Bài tập ngữ pháp/đọc/viết/nghe
 * chưa có id số riêng cho Unit ở FE (chỉ có chuỗi tên, xem AssignedExerciseResponse) nên phải nhóm theo
 * tên, không phải id. Giữ nguyên thứ tự entries đúng thứ tự đã sort ở feedItems (hạn nộp sớm nhất lên đầu).
 */
function buildUnitGroups(entries: FeedEntry[], t: (key: string) => string): UnitGroup[] {
  const unitMap = new Map<string, { label: string; entries: FeedEntry[] }>();
  for (const entry of entries) {
    const unitTitle = entryUnitTitle(entry)?.trim();
    const unitKey = unitTitle || UNASSIGNED_GROUP_KEY;
    let unit = unitMap.get(unitKey);
    if (!unit) {
      unit = { label: unitTitle || t("assignments.nav.uncategorizedUnit"), entries: [] };
      unitMap.set(unitKey, unit);
    }
    unit.entries.push(entry);
  }
  return [...unitMap.entries()]
    .sort(([aKey], [bKey]) => {
      if (aKey === UNASSIGNED_GROUP_KEY) return 1;
      if (bKey === UNASSIGNED_GROUP_KEY) return -1;
      return naturalCompare(aKey, bKey);
    })
    .map(([unitKey, unit]) => ({
      unitKey,
      unitLabel: unit.label,
      entries: unit.entries,
      skills: SKILL_ORDER.map((skillKey) => ({ skillKey, entries: unit.entries.filter((e) => entrySkillKey(e) === skillKey) })).filter(
        (group) => group.entries.length > 0
      )
    }));
}

/**
 * UC-40 (bài tập ngữ pháp, Giáo viên Việt Nam giao) + UC-23a/UC-23b (Video từ kết nối/phản xạ,
 * Giáo viên nước ngoài giao) — gộp chung vào 1 tab "Bài tập về nhà (BTVN)" ở sidebar Portal, theo
 * đúng thực tế nghiệp vụ 2 nhóm giáo viên giao 2 loại bài khác nhau nhưng học sinh cần thấy chung 1
 * danh sách để không bỏ sót (đã xác nhận với người dùng 2026-07-27) — "Kho Video Ôn tập" trước đây
 * nằm trong "E-Learning & LMS" đã CHUYỂN HẲN sang đây, không hiển thị lại ở LMS nữa.
 *
 * Video từ kết nối (CONNECTION): "đạt" = xem ≥ ngưỡng % cấu hình theo lượt (UC-23a). Video phản xạ
 * (REFLEX): "đạt" = đã nộp đủ audio trả lời cho mọi câu hỏi (UC-23b) — KHÔNG qua khâu giáo viên chấm
 * điểm (đã bỏ theo yêu cầu người dùng 2026-07-29, nộp bài là hoàn thành ngay). LƯU Ý: backend chưa có
 * API đọc lại tiến độ xem đã lưu cho CONNECTION (chỉ có PUT report, không có GET) nên KHÔNG thể hiện
 * đúng trạng thái "đã xem xong" trong danh sách này — các mục CONNECTION vì vậy không tính vào bộ đếm
 * "Cần hoàn thành"/"Đã nộp", chỉ hiện ở tab "Tất cả".
 */
export default function AssignmentsTab({
  classId,
  autoOpenExerciseAssignmentId,
  autoOpenReviewVideoAssignmentId,
  onAutoOpenHandled,
  onPendingCountChange
}: AssignmentsTabProps) {
  const { t } = useTranslation("portal-exercises");
  const [exercises, setExercises] = useState<AssignedExerciseResponse[]>([]);
  const { singles: singleExercises, batches: batchGroups } = groupExercisesByBatch(exercises);
  const [reviewItems, setReviewItems] = useState<ReviewVideoHomeworkItem[]>([]);
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — giữ lại danh sách bản giao Video Ôn
  // tập thô (có assignmentId + reviewVideoSetId) để auto-mở đúng video theo assignmentId nhảy từ tab
  // Quá trình học tập sang (xem effect "auto-open" bên dưới) — trước đây chỉ dùng inline trong load()
  // để tính dueAtBySetId rồi bỏ, không giữ lại được.
  const [videoAssignments, setVideoAssignments] = useState<MyReviewVideoAssignmentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — mặc định mở tab "Cần hoàn thành"
  // thay vì "Tất cả bài tập", để học sinh vào BTVN thấy ngay việc còn phải làm thay vì phải tự bấm lọc.
  const [filterStatus, setFilterStatus] = useState<FilterStatus>("PENDING");
  /** V166 — cho đóng banner cảnh báo quá hạn, chỉ trong phiên xem hiện tại (không lưu lại). */
  const [overdueBannerDismissed, setOverdueBannerDismissed] = useState(false);
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-18 — cấp điều hướng đang xem (Unit/
  // Kỹ năng/danh sách bài), xem NavView. Mặc định mở ở cấp "units" (chọn Unit trước tiên).
  const [navView, setNavView] = useState<NavView>({ level: "units" });
  const [takingExercise, setTakingExercise] = useState<AssignedExerciseResponse | null>(null);
  /** V150 — 1 Lô đang được làm liên tục (N thẻ cùng homeworkBatchId, xem groupExercisesByBatch/BatchTakeExerciseModal). */
  const [takingBatch, setTakingBatch] = useState<AssignedExerciseResponse[] | null>(null);
  const [openReviewItem, setOpenReviewItem] = useState<ReviewVideoHomeworkItem | null>(null);
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — danh sách BTVN có thể dài (nhiều
  // Bài ngữ pháp + Video ôn tập cộng lại), phân trang để tránh cuộn quá nhiều. Về trang 1 mỗi khi đổi
  // bộ lọc trạng thái hoặc đổi cấp điều hướng.
  const [page, setPage] = useState(0);
  // Bổ sung ngoài SDD gốc — reset về trang 1 khi đổi bộ lọc (hoặc đổi cấp điều hướng Unit/Kỹ năng, bổ
  // sung 2026-09-18 — xem navView). PHẢI đặt trước early-return `if (loading)` bên dưới (Rules of Hooks:
  // mọi hook phải gọi VÔ ĐIỀU KIỆN, không được đặt sau early-return — trước đây đặt sai chỗ, hook bị bỏ
  // qua lúc loading=true rồi lại gọi khi loading=false, gây lỗi "Rendered more hooks than during the
  // previous render").
  useEffect(() => setPage(0), [filterStatus, navView]);

  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-18 — toàn bộ khối đếm/lọc/gộp feedItems +
  // nhóm theo Unit/Kỹ năng (buildUnitGroups) được TÍNH TRƯỚC early-return `if (loading)` bên dưới (dù chỉ
  // dùng để render SAU return đó) — bắt buộc vì effect "auto-open" (highlight/cuộn tới đúng thẻ, xem bên
  // dưới) cần đọc `skillEntries` để tính đúng trang, mà mọi hook (useEffect) phải khai báo TRƯỚC
  // early-return theo Rules of Hooks (xem ghi chú ở effect reset trang phía trên).
  const exerciseCardCount = singleExercises.length + batchGroups.length;
  const pendingCount =
    singleExercises.filter(isExerciseActionablePending).length +
    batchGroups.filter(isBatchActionablePending).length +
    reviewItems.filter(isVideoActionablePending).length;
  const gradedCount =
    singleExercises.filter((e) => !isExercisePending(e)).length +
    batchGroups.filter((items) => !isBatchPending(items)).length +
    reviewItems.filter((x) => isReflexAnswerable(x) && isReflexFullyAnswered(x)).length +
    reviewItems.filter((x) => isConnectionAnswerable(x) && isConnectionCompleted(x)).length;
  /** V152 — đếm cho tab lọc mới "Bài tập quá hạn" (khác pendingCount: chỉ tính phần ĐÃ qua hạn nộp trong số đang pending). */
  const overdueCount =
    singleExercises.filter(isExerciseOverduePending).length +
    batchGroups.filter(isBatchOverduePending).length +
    reviewItems.filter(isVideoOverduePending).length;

  const filteredSingleExercises = singleExercises.filter((e) => {
    if (filterStatus === "PENDING") return isExerciseActionablePending(e);
    if (filterStatus === "GRADED") return !isExercisePending(e);
    if (filterStatus === "OVERDUE") return isExerciseOverduePending(e);
    return true;
  });
  const filteredBatchGroups = batchGroups.filter((items) => {
    if (filterStatus === "PENDING") return isBatchActionablePending(items);
    if (filterStatus === "GRADED") return !isBatchPending(items);
    if (filterStatus === "OVERDUE") return isBatchOverduePending(items);
    return true;
  });
  const filteredReviewItems = reviewItems.filter((x) => {
    if (filterStatus === "ALL") return true;
    if (filterStatus === "OVERDUE") return isVideoOverduePending(x);
    if (filterStatus === "PENDING") return isVideoActionablePending(x);
    if (x.videoType === "CONNECTION") {
      if (!isConnectionAnswerable(x)) return false; // API tiến độ lỗi/chưa tải xong — chưa tham gia lọc
      return isConnectionCompleted(x); // còn lại đúng nhánh GRADED
    }
    if (!isReflexAnswerable(x)) return false; // REFLEX chưa có câu hỏi — chưa tham gia lọc
    return isReflexFullyAnswered(x); // còn lại đúng nhánh GRADED
  });

  // Gộp 2 danh sách (Bài ngữ pháp + Video ôn tập) thành 1 để phân trang chung — đúng tinh thần "1
  // danh sách BTVN duy nhất" đã gộp ở tab này (xem Javadoc đầu file), không tách trang riêng từng loại.
  const feedItems: FeedEntry[] = [
    ...filteredSingleExercises.map((item) => ({ type: "exercise" as const, key: `ex-${item.assignmentId}`, item })),
    ...filteredBatchGroups.map((items) => ({ type: "exerciseBatch" as const, key: `exb-${items[0].homeworkBatchId}`, items })),
    ...filteredReviewItems.map((item) => ({ type: "video" as const, key: `rv-${item.assignmentId ?? "lib"}-${item.video.id}`, item }))
  ];
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — sắp xếp hạn nộp sớm nhất lên đầu
  // (bài quá hạn có hạn ở quá khứ nên tự nhiên nổi lên trên cùng), giúp học sinh biết bài nào cần ưu
  // tiên làm trước thay vì thứ tự cố định "Bài ngữ pháp trước, Video sau" như cũ. Bài không có hạn nộp
  // (dueAt null — chỉ nằm trong Kho, không phải BTVN đang giao) xếp CUỐI vì không có gì để ưu tiên.
  const dueAtMillisOf = (row: FeedEntry): number => {
    const dueAt = row.type === "exercise" ? row.item.dueAt : row.type === "exerciseBatch" ? row.items[0].dueAt : row.item.dueAt;
    return dueAt == null ? Number.POSITIVE_INFINITY : new Date(dueAt).getTime();
  };
  feedItems.sort((a, b) => dueAtMillisOf(a) - dueAtMillisOf(b));

  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-18 — nhóm feedItems (đã lọc theo
  // filterStatus, đã sort theo hạn nộp) thành cây Unit → Kỹ năng (xem buildUnitGroups) để đổi màn BTVN từ
  // danh sách phẳng sang điều hướng phân cấp kiểu "như sách". `navView` quyết định đang xem cấp nào;
  // `skillEntries` (rỗng nếu chưa chọn tới cấp "assignments") là nguồn DUY NHẤT cho phân trang cấp lá —
  // cũng được effect "auto-open" bên dưới dùng lại để tính đúng trang cần nhảy tới, tránh trùng lặp logic
  // lọc/sort với 1 bản riêng (dễ lệch nhau).
  const unitGroups = buildUnitGroups(feedItems, t);
  const currentUnit = navView.level !== "units" ? unitGroups.find((u) => u.unitKey === navView.unitKey) : undefined;
  const currentSkill = navView.level === "assignments" ? currentUnit?.skills.find((s) => s.skillKey === navView.skillKey) : undefined;
  const skillEntries = currentSkill?.entries ?? [];
  const pageItems = skillEntries.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-19 (fix bug thật) — số đếm hiển thị TRÊN 4
  // nút tab trạng thái (Tất cả/Cần hoàn thành/Đã chấm/Quá hạn) phải phản ánh ĐÚNG phạm vi breadcrumb
  // đang đứng (toàn lớp / 1 Unit / 1 Kỹ năng) — trước đây dùng thẳng exerciseCardCount/pendingCount/
  // gradedCount/overdueCount ở trên (CHỦ Ý giữ nguyên = toàn lớp, dùng cho banner đầu trang + badge
  // sidebar), khiến đứng trong 1 Unit chỉ có vài bài mà tab vẫn hiện số đếm của CẢ LỚP, gây hiểu lầm. Tính
  // lại từ `allEntries` (KHÔNG lọc theo filterStatus — để biết trước bấm tab nào cũng ra đúng số) rồi thu
  // hẹp theo scope hiện tại (scopeEntries).
  const allEntries: FeedEntry[] = [
    ...singleExercises.map((item) => ({ type: "exercise" as const, key: `ex-${item.assignmentId}`, item })),
    ...batchGroups.map((items) => ({ type: "exerciseBatch" as const, key: `exb-${items[0].homeworkBatchId}`, items })),
    ...reviewItems.map((item) => ({ type: "video" as const, key: `rv-${item.assignmentId ?? "lib"}-${item.video.id}`, item }))
  ];
  const scopeEntries = allEntries.filter((entry) => {
    if (navView.level === "units") return true;
    if ((entryUnitTitle(entry)?.trim() || UNASSIGNED_GROUP_KEY) !== navView.unitKey) return false;
    return navView.level === "skills" || entrySkillKey(entry) === navView.skillKey;
  });
  const tabAllCount = scopeEntries.length;
  const tabPendingCount = scopeEntries.filter(isEntryPending).length;
  const tabGradedCount = scopeEntries.filter(isEntryGraded).length;
  const tabOverdueCount = scopeEntries.filter(isEntryOverduePending).length;

  const load = () => {
    setLoading(true);
    setError(null);
    Promise.all([
      listMyAssignedExercises(classId),
      Promise.all([listReviewVideoSetsByClass(classId), listMyReviewVideoAssignments(classId).catch(() => [])]).then(async ([sets, assignments]) => {
        setVideoAssignments(assignments);
        // Fix 2026-08-12: trước đây gộp theo reviewVideoSetId qua 1 Map (key trùng bị ghi đè) — 1 bộ
        // giao lại ở nhiều buổi khác nhau (nhiều ReviewVideoAssignment ACTIVE cùng setId, đúng V65) chỉ
        // còn thấy ĐÚNG 1 hạn nộp, mất hẳn (các) bản giao khác không hiện thành card nào cả. Giờ tách 1
        // "nhóm" / 1 bản giao ACTIVE của bộ đó; bộ KHÔNG đang được giao (chỉ nằm trong Kho) vẫn giữ 1
        // nhóm duy nhất, dueAt/assignmentId để undefined như cũ.
        const assignmentsBySetId = new Map<number, MyReviewVideoAssignmentResponse[]>();
        assignments.forEach((a) => {
          const list = assignmentsBySetId.get(a.reviewVideoSetId) ?? [];
          list.push(a);
          assignmentsBySetId.set(a.reviewVideoSetId, list);
        });
        const groups = sets.flatMap((set) => {
          const setAssignments = assignmentsBySetId.get(set.id);
          return setAssignments && setAssignments.length > 0
            ? setAssignments.map((a) => ({
                set,
                dueAt: a.dueAt as string | undefined,
                assignmentId: a.assignmentId as number | undefined,
                sessionDate: a.sessionDate,
                lateSubmissionAllowed: a.lateSubmissionAllowed as boolean | undefined,
                lateSubmissionDeadline: a.lateSubmissionDeadline as string | null | undefined
              }))
            : [
                {
                  set,
                  dueAt: undefined as string | undefined,
                  assignmentId: undefined as number | undefined,
                  sessionDate: undefined as string | null | undefined,
                  lateSubmissionAllowed: undefined as boolean | undefined,
                  lateSubmissionDeadline: undefined as string | null | undefined
                }
              ];
        });
        // videos của cùng 1 bộ giống hệt nhau dù giao lặp lại nhiều lần — cache theo setId để không gọi
        // lại API listReviewVideos thừa cho mỗi bản giao trùng bộ.
        // Bổ sung 2026-08-24 (sửa bug thật) — bọc .catch(() => []) giống mọi lệnh gọi khác trong hàm
        // này: listByClass() vẫn trả về bộ chỉ còn bản giao CANCELLED (chủ ý, để HS thấy lịch sử, xem
        // Javadoc ReviewVideoService#listByClass), nhưng resolveStudentAccess() chỉ cho xem video khi
        // có bản giao ACTIVE — không bọc catch khiến 1 bộ đã CANCELLED làm sập TOÀN BỘ tab BTVN (kể cả
        // Bài ngữ pháp/Reading/Writing đã tải thành công), vì gọi trong Promise.all không bắt lỗi riêng.
        const videosBySetId = new Map<number, Promise<ReviewVideoResponse[]>>();
        const videosOf = (setId: number) => {
          if (!videosBySetId.has(setId)) videosBySetId.set(setId, listReviewVideos(setId).catch(() => []));
          return videosBySetId.get(setId)!;
        };
        const perGroup = await Promise.all(
          groups.map(async (g) => {
            const videos = await videosOf(g.set.id);
            return videos.map(
              (video) =>
                ({
                  video,
                  videoType: g.set.videoType,
                  setTitle: g.set.title,
                  dueAt: g.dueAt,
                  assignmentId: g.assignmentId,
                  lateSubmissionAllowed: g.lateSubmissionAllowed,
                  lateSubmissionDeadline: g.lateSubmissionDeadline,
                  teacherType: g.set.teacherType,
                  sessionDate: g.sessionDate,
                  unitTitle: g.set.unitTitle,
                  subTopicTitle: g.set.subTopicTitle
                }) as ReviewVideoHomeworkItem
            );
          })
        );
        const flat = perGroup.flat();
        const reflexItems = flat.filter((x) => x.videoType === "REFLEX");
        const reflexStatsList = await Promise.all(
          reflexItems.map(async (x) => {
            // V128/V129 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — tiến độ nộp bài
            // nay chấm riêng theo TỪNG lần giao (assignmentId), không còn 1 rollup chung cho cả video.
            // Mục chỉ nằm trong Kho (chưa có bản giao ACTIVE nào, assignmentId undefined) không có lần
            // giao nào để tra — coi như chưa có câu hỏi (loại khỏi bộ đếm Cần hoàn thành/Đã nộp qua
            // isReflexAnswerable), mirror đúng hành vi cũ (API vốn đã 404 cho video chưa được giao).
            if (x.assignmentId == null) return { totalQuestions: 0, answeredQuestions: 0 };
            const questions = await listReviewVideoQuestions(x.video.id).catch(() => []);
            // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — fix bug thật: dòng này vẫn
            // đọc `getMyLatestReviewVideoSubmission` (API của luồng CŨ "ghi âm theo mốc, nộp cả loạt cuối
            // video") — luồng REFLEX từ V139 đã chuyển hẳn sang `ReflexSequentialGradingService`/bảng
            // `reflex_question_progress`, không còn tạo submission kiểu cũ nữa nên API cũ luôn trả về
            // rỗng, khiến "Đã nộp 0/5 câu" dù học sinh đã làm/đạt hết qua luồng mới. Đổi sang đọc đúng
            // tiến độ mới qua `listMyReflexProgress` (cùng API ReflexVideoTaskPage đang dùng); "đã nộp"
            // tính theo số câu ĐÃ ĐẠT (`questionPassed`) — khớp ngưỡng REFLEX_PASS_THRESHOLD_PERCENT bên
            // dưới (REFLEX không qua khâu GV chấm, đạt ngưỡng % câu ĐÃ ĐẠT là xong, không phải % câu đã
            // nộp bất kể đúng/sai).
            const progressList = await listMyReflexProgress(x.assignmentId).catch(() => []);
            return {
              totalQuestions: questions.length,
              answeredQuestions: progressList.filter((p) => p.questionPassed).length
            };
          })
        );
        const statsByVideoId = new Map(reflexItems.map((x, i) => [x.video.id, reflexStatsList[i]]));

        // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — CONNECTION giờ đọc lại tiến
        // độ đã lưu qua GET progress mới, mirror đúng cách REFLEX đọc reflexStats ở trên. V128/V129:
        // cũng cần assignmentId — mục chỉ nằm trong Kho bỏ qua, giữ connectionStats undefined.
        const connectionItems = flat.filter((x) => x.videoType === "CONNECTION");
        const connectionProgressList = await Promise.all(
          connectionItems.map((x) => (x.assignmentId == null ? Promise.resolve(undefined) : getReviewVideoProgress(x.video.id, x.assignmentId).catch(() => undefined)))
        );
        const connectionStatsByVideoId = new Map(
          connectionItems.map((x, i) => [x.video.id, connectionProgressList[i]] as const)
        );

        return flat.map((x) => {
          const p = connectionStatsByVideoId.get(x.video.id);
          return {
            ...x,
            reflexStats: statsByVideoId.get(x.video.id),
            connectionStats: p ? { viewCount: p.viewCount, requiredViewCount: p.requiredViewCount, completed: p.completed } : undefined
          };
        });
      })
    ])
      .then(([exerciseRes, reviewRes]) => {
        setExercises(exerciseRes);
        setReviewItems(reviewRes);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("assignments.loadError")))
      .finally(() => setLoading(false));
  };

  useEffect(load, [classId]);

  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — nhảy từ tab Quá trình học tập sang
  // KHÔNG mở modal làm bài luôn (yêu cầu ban đầu), chỉ cần focus/cuộn tới + nổi viền đúng card đó để
  // nhận biết (giống hệt cách ParentHomeworkProgressTab highlight). 2 bước tách effect: (1) xác định
  // "key" card cần tới (ex-<assignmentId>/rv-<videoId>) NGAY khi có id truyền vào — đồng thời reset bộ
  // lọc trạng thái/loại về ALL để đảm bảo card không bị bộ lọc hiện tại ẩn mất; (2) khi đã có key, tính
  // đúng TRANG chứa card đó (feedItems phụ thuộc filter, nên tính lại ở effect (2) sau khi filter đã về
  // ALL), nhảy trang nếu cần rồi mới cuộn — phải tách 2 bước vì đổi trang cần 1 lượt render lại để
  // pageItems chứa đúng card trước khi tìm được phần tử DOM. Cả 2 PHẢI đặt trước early-return
  // `if (loading)` bên dưới (Rules of Hooks — bài học từ lỗi "Rendered more hooks than during the
  // previous render" gặp trước đây ở chính file này).
  const [pendingHighlightKey, setPendingHighlightKey] = useState<string | null>(null);
  const [highlightKey, setHighlightKey] = useState<string | null>(null);

  useEffect(() => {
    if (loading) return;
    if (autoOpenExerciseAssignmentId == null && autoOpenReviewVideoAssignmentId == null) return;
    setFilterStatus("ALL");
    let key: string | null = null;
    let unitTitle: string | null | undefined;
    let skillKey: SkillKey = "OTHER_EXERCISE";
    if (autoOpenExerciseAssignmentId != null) {
      const match = exercises.find((e) => e.assignmentId === autoOpenExerciseAssignmentId);
      // V150 — Bài thuộc 1 Lô giờ hiện gộp thành 1 thẻ "exb-<batchId>" (xem groupExercisesByBatch),
      // không còn thẻ "ex-<assignmentId>" riêng cho từng Bài trong lô.
      if (match) {
        key = match.homeworkBatchId != null ? `exb-${match.homeworkBatchId}` : `ex-${match.assignmentId}`;
        unitTitle = match.unitTitle;
        skillKey = match.skillCategory ?? "OTHER_EXERCISE";
      }
    } else if (autoOpenReviewVideoAssignmentId != null) {
      const assignment = videoAssignments.find((a) => a.assignmentId === autoOpenReviewVideoAssignmentId);
      // 1 bộ có thể gồm nhiều video — chưa có khái niệm "đúng video nào" ứng với 1 lần giao (giao theo
      // cả BỘ), nên focus vào video ĐẦU của ĐÚNG bản giao này (khớp assignmentId, không chỉ setId — 1
      // bộ có thể có nhiều bản giao cùng lúc, xem fix 2026-08-12 ở load()).
      const match = assignment ? reviewItems.find((x) => x.assignmentId === assignment.assignmentId) : undefined;
      if (match) {
        key = `rv-${match.assignmentId ?? "lib"}-${match.video.id}`;
        unitTitle = match.unitTitle;
        skillKey = match.videoType;
      }
    }
    setPendingHighlightKey(key);
    // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-18 — màn BTVN giờ điều hướng phân cấp
    // Unit → Kỹ năng (xem NavView), phải tự nhảy thẳng tới đúng cấp "assignments" của Unit + Kỹ năng chứa
    // thẻ cần focus, nếu không thẻ vẫn tồn tại trong dữ liệu nhưng học sinh sẽ không thấy vì đang đứng ở
    // màn chọn Unit/Kỹ năng khác.
    if (key) {
      setNavView({ level: "assignments", unitKey: unitTitle?.trim() || UNASSIGNED_GROUP_KEY, skillKey });
    }
    onAutoOpenHandled?.();
    // onAutoOpenHandled cố tình không đưa vào deps — PortalPage truyền hàm inline (đổi identity mỗi
    // render cha), đưa vào đây sẽ khiến effect chạy lại thừa mỗi khi cha re-render vì lý do khác, dù
    // giá trị autoOpen*AssignmentId chưa đổi.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading, autoOpenExerciseAssignmentId, autoOpenReviewVideoAssignmentId, exercises, reviewItems, videoAssignments]);

  useEffect(() => {
    if (loading || !pendingHighlightKey) return;
    // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-18 — chờ effect trên set xong navView
    // đúng cấp "assignments" (Unit + Kỹ năng chứa thẻ cần focus) rồi mới tìm vị trí/trang trong
    // `skillEntries` (đã lọc+sort đúng bộ lọc "ALL" vừa reset ở effect trên, dùng LẠI nguyên xi mảng dùng
    // để phân trang thật — tránh dựng 1 bản `allKeys` riêng dễ lệch thứ tự với cách feedItems được sort
    // theo hạn nộp). Nếu chưa tới cấp này, skillEntries rỗng, effect tự chạy lại khi navView đổi.
    if (navView.level !== "assignments") return;
    const idx = skillEntries.findIndex((entry) => entry.key === pendingHighlightKey);
    if (idx === -1) {
      setPendingHighlightKey(null);
      return;
    }
    const targetPage = Math.floor(idx / PAGE_SIZE);
    if (page !== targetPage) {
      setPage(targetPage);
      return; // đợi effect chạy lại sau khi page đổi (render lại pageItems đúng trang trước đã)
    }
    const el = document.getElementById(`assignment-card-${pendingHighlightKey}`);
    if (!el) return; // card chưa kịp render (VD vừa đổi filter) — effect tự chạy lại khi deps đổi
    el.scrollIntoView({ behavior: "smooth", block: "center" });
    setHighlightKey(pendingHighlightKey);
    setPendingHighlightKey(null);
    const timer = setTimeout(() => setHighlightKey(null), 2500);
    return () => clearTimeout(timer);
  }, [loading, pendingHighlightKey, page, navView, skillEntries]);

  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — báo pendingCount lên PortalPage
  // (badge sidebar). Đặt TRƯỚC early-return `if (loading)` bên dưới (Rules of Hooks — cùng lý do đã
  // ghi chú ở các effect khác trong file này).
  useEffect(() => {
    if (loading) return;
    // V152 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — mirror đúng pendingCount ở
    // dưới (đã tách Bài quá hạn ra khỏi "Cần hoàn thành") — badge sidebar không nên cộng luôn cả phần
    // quá hạn (khoá, không còn thao tác được) vào cùng con số "cần làm" nữa.
    const count =
      singleExercises.filter(isExerciseActionablePending).length +
      batchGroups.filter(isBatchActionablePending).length +
      reviewItems.filter(isVideoActionablePending).length;
    onPendingCountChange?.(count);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading, exercises, reviewItems]);

  if (loading) return <p className="text-sm text-muted font-bold">{t("assignments.loading")}</p>;

  return (
    <div className="space-y-6">
      {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

      {pendingCount > 0 ? (
        <div className="p-5 bg-amber-50 border border-amber-200 rounded-2xl text-amber-900 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="w-12 h-12 rounded-xl bg-amber-100 flex items-center justify-center shrink-0">
              <Bell size={24} className="text-amber-600" />
            </div>
            <div>
              <span className="px-2.5 py-0.5 rounded-full bg-amber-100 text-amber-700 text-[10px] font-black uppercase tracking-wider">
                {t("assignments.banner.label")}
              </span>
              <h3 className="text-base md:text-lg font-black font-display mt-0.5 text-amber-900">
                {t("assignments.banner.titlePrefix")}{" "}
                <span className=" decoration-wavy underline-offset-4">{t("assignments.banner.titleCount", { count: pendingCount })}</span>{" "}
                {t("assignments.banner.titleSuffix")}
              </h3>
              <p className="text-xs text-amber-800/80 font-semibold mt-0.5">{t("assignments.banner.description")}</p>
            </div>
          </div>
          <button
            onClick={() => {
              setFilterStatus("PENDING");
              setNavView({ level: "units" });
            }}
            className="px-5 py-2.5 bg-amber-500 hover:bg-amber-600 text-white font-extrabold text-xs rounded-xl shadow-sm transition-all shrink-0 cursor-pointer"
          >
            {t("assignments.banner.actionButton")}
          </button>
        </div>
      ) : overdueCount > 0 && !overdueBannerDismissed ? (
        // V166 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-07) — fix bug thật: Bài quá
        // hạn (chưa hoàn thành) bị loại khỏi pendingCount từ V152 (tách riêng khỏi "Cần hoàn thành",
        // xem ghi chú V152 ở trên) khiến banner "Tuyệt vời, hoàn thành hết rồi" (màu xanh) vẫn hiện dù
        // học sinh còn nguyên N bài quá hạn chưa làm — dễ gây hiểu lầm đã xong hết. Thêm nhánh cảnh báo
        // đỏ (mirror tab lọc "Bài tập quá hạn" cùng màu) khi còn bài quá hạn, chỉ hiện banner xanh khi
        // THẬT SỰ không còn gì (pendingCount=0 VÀ overdueCount=0). Cho đóng lại (nút X) — chỉ ẩn trong
        // phiên xem hiện tại, không lưu lại (mở lại/tải lại trang thì hiện lại nếu vẫn còn quá hạn).
        // Kiểu gradient hồng → đỏ/cam (2026-09-19, theo mẫu người dùng đưa): nền hồng nhạt, ô icon vuông
        // gradient kèm huy hiệu đồng hồ, nhãn hồng nhạt, nút gradient. Không có nút "Nhắn giáo viên" như
        // ảnh mẫu vì hệ thống chưa có chức năng nhắn tin cho giáo viên.
        <div className="relative p-5 bg-gradient-to-r from-rose-50 via-rose-50/80 to-pink-100/60 border-1 border-rose-200 shadow-[0_8px_30px_rgba(244,63,94,0.15)] rounded-3xl flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <button
            onClick={() => setOverdueBannerDismissed(true)}
            aria-label={t("assignments.overdueBanner.dismiss")}
            className="absolute top-3 right-3 w-7 h-7 rounded-full flex items-center justify-center text-slate-400 hover:text-rose-600 hover:bg-rose-100 transition-colors cursor-pointer"
          >
            <X size={16} />
          </button>
          <div className="flex items-center gap-4 pr-8 sm:pr-0">
            <div className="hidden sm:flex w-12 h-12 rounded-full bg-coral items-center justify-center shrink-0 shadow-sm">
              <AlertCircle size={24} className="text-white" />
            </div>
            <div>
              <span className="inline-flex items-center gap-1.5 pl-2 pr-2.5 py-0.5 rounded-full bg-rose-100 text-rose-700 text-[10px] font-black uppercase tracking-wider">
                {/* Chấm đỏ nhấp nháy kiểu chấm thông báo, nằm trái và ngang hàng với chữ (2026-09-19, theo yêu cầu người dùng) — thu hút chú ý vào cảnh báo quá hạn. */}
                <span className="relative flex h-2.5 w-2.5 shrink-0" aria-hidden="true">
                  <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-rose-400 opacity-80" />
                  <span className="relative inline-flex h-2.5 w-2.5 animate-blink-dot rounded-full bg-rose-500" />
                </span>
                {t("assignments.overdueBanner.label")}
              </span>
              <h3 className="text-base md:text-lg font-black font-display mt-1 text-ink">
                {t("assignments.overdueBanner.titlePrefix")}{" "}
                <span className="text-rose-600">{t("assignments.overdueBanner.titleCount", { count: overdueCount })}</span>{" "}
                {t("assignments.overdueBanner.titleSuffix")}
              </h3>
              <p className="text-xs text-slate-600 font-semibold mt-0.5">{t("assignments.overdueBanner.description")}</p>
            </div>
          </div>

          <button
            onClick={() => {
              setFilterStatus("OVERDUE");
              setNavView({ level: "units" });
            }}
            className="w-full sm:w-auto sm:mr-8 px-5 py-2.5 bg-coral hover:opacity-90 text-white font-extrabold text-xs sm:text-sm rounded-xl shadow-sm transition-all shrink-0 cursor-pointer self-stretch sm:self-center"
          >
            {t("assignments.overdueBanner.actionButton")}
          </button>
        </div>
      ) : (
        <div className="p-4 bg-emerald-50 border border-emerald-200 rounded-2xl text-emerald-800 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <CheckCircle2 size={20} className="text-emerald-600" />
            <span className="text-xs font-black">{t("assignments.allDone")}</span>
          </div>
        </div>
      )}

      <Breadcrumb
        items={((): BreadcrumbItem[] => {
          // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-18 — bấm breadcrumb gốc "Bài tập
          // về nhà" (quay hẳn về cấp chọn Unit) thì reset luôn bộ lọc trạng thái về mặc định ("Cần hoàn
          // thành"), tránh tình huống đang lọc "Đã chấm" sâu trong 1 Unit rồi lùi về thấy danh sách Unit
          // bị thu hẹp theo đúng filter cũ, gây hiểu lầm mất bài. CHỈ áp dụng cho breadcrumb gốc — bấm
          // vào tên Unit (quay về cấp Kỹ năng trong CÙNG Unit) không reset, vì filter vẫn còn ý nghĩa.
          const root: BreadcrumbItem = {
            label: t("assignments.nav.breadcrumbRoot"),
            onClick:
              navView.level !== "units"
                ? () => {
                    setNavView({ level: "units" });
                    setFilterStatus("PENDING");
                  }
                : undefined
          };
          if (navView.level === "units") return [root];
          // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-19 (fix bug thật) — trước đây khi
          // đổi filter khiến Unit đang chọn KHÔNG còn bài nào khớp (currentUnit = undefined), breadcrumb
          // rơi thẳng về nhãn "Chưa phân loại Unit" dù Unit đó có tên thật (VD "UNIT 1: HOBBIES") — gây
          // hiểu lầm đang đứng nhầm Unit. `navView.unitKey` CHÍNH LÀ tên Unit thật (chỉ là
          // "__unassigned__" khi thật sự chưa gắn Unit nào, xem buildUnitGroups) nên dùng lại chuỗi đó
          // làm nhãn dự phòng thay vì suy diễn sai. Kỹ năng thì luôn có nhãn thật (skillLabel).
          const unitLabel = currentUnit?.unitLabel ?? (navView.unitKey === UNASSIGNED_GROUP_KEY ? t("assignments.nav.uncategorizedUnit") : navView.unitKey);
          if (navView.level === "skills") return [root, { label: unitLabel }];
          return [
            root,
            { label: unitLabel, onClick: () => setNavView({ level: "skills", unitKey: navView.unitKey }) },
            { label: skillLabel(t, navView.skillKey) }
          ];
        })()}
        // Viên thuốc kính mờ + kéo sát hàng tab bên dưới (mb-3 ghi đè khoảng cách space-y-6 của khối cha) —
        // 2026-09-19, theo yêu cầu người dùng, để breadcrumb không "lơ lửng" cách xa tab.
        className="w-fit max-w-full px-4 py-2 rounded-full bg-white/70 backdrop-blur-md border border-white/80 shadow-sm mb-3"
      />

      {/* Hàng tab trạng thái tự cuộn ngang kiểu carousel, dùng chung mọi kích thước màn hình (yêu cầu
          2026-08-01). Dropdown lọc "loại bài" cũ đã bỏ 2026-09-19 — thay bằng cấp điều hướng Kỹ năng (xem
          NavView). Kiểu "glass" pill + chip số đếm (2026-09-19, đã xác nhận với người dùng, thử ở khu vực
          BTVN trước). Tab "Quá hạn" (V152, 2026-08-25) tách riêng khỏi "Cần hoàn thành". */}
      <div className="flex items-center gap-2.5 overflow-x-auto scrollbar-hide snap-x snap-proximity py-1 px-1">
        {(
          [
            { status: "ALL", label: t("assignments.filters.labelAll"), count: tabAllCount, icon: null, active: "bg-teal text-white" },
            { status: "PENDING", label: t("assignments.filters.labelPending"), count: tabPendingCount, icon: Clock, active: "bg-orange-500 text-white" },
            { status: "GRADED", label: t("assignments.filters.labelGraded"), count: tabGradedCount, icon: CheckCircle2, active: "bg-teal text-white" },
            { status: "OVERDUE", label: t("assignments.filters.labelOverdue"), count: tabOverdueCount, icon: AlertCircle, active: "bg-coral text-white" }
          ] as const
        ).map((tab) => {
          const isActive = filterStatus === tab.status;
          const TabIcon = tab.icon;
          return (
            <button
              key={tab.status}
              onClick={() => setFilterStatus(tab.status)}
              className={`shrink-0 snap-start flex items-center gap-1.5 px-4 py-2 rounded-xl text-sm font-black transition-all cursor-pointer ${
                isActive
                  ? `${tab.active} shadow-md`
                  : "bg-white/70 backdrop-blur-md border border-white/80 text-ink shadow-sm hover:bg-white/90"
              }`}
            >
              {TabIcon && <TabIcon size={14} />}
              {tab.label} ({tab.count})
            </button>
          );
        })}
      </div>

      {navView.level === "units" ? (
        unitGroups.length === 0 ? (
          <p className="text-sm text-muted font-bold italic text-center py-10">{t("assignments.nav.emptyUnitGroup")}</p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {unitGroups.map((unit, index) => (
              <UnitCard key={unit.unitKey} unit={unit} index={index} onOpen={() => setNavView({ level: "skills", unitKey: unit.unitKey })} />
            ))}
          </div>
        )
      ) : !currentUnit ? (
        <div className="text-center py-10 space-y-3">
          <p className="text-sm text-muted font-bold italic">{t("assignments.nav.emptySkillGroup")}</p>
          <button
            onClick={() => setNavView({ level: "units" })}
            className="px-4 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-ink text-sm font-black transition-colors cursor-pointer"
          >
            {t("assignments.nav.backToUnits")}
          </button>
        </div>
      ) : navView.level === "skills" ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {currentUnit.skills.map((skill) => (
            <SkillCard
              key={skill.skillKey}
              skill={skill}
              onOpen={() => setNavView({ level: "assignments", unitKey: currentUnit.unitKey, skillKey: skill.skillKey })}
            />
          ))}
        </div>
      ) : !currentSkill ? (
        <div className="text-center py-10 space-y-3">
          <p className="text-sm text-muted font-bold italic">{t("assignments.nav.emptyAssignmentGroup")}</p>
          <button
            onClick={() => setNavView({ level: "skills", unitKey: navView.unitKey })}
            className="px-4 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-ink text-sm font-black transition-colors cursor-pointer"
          >
            {t("assignments.nav.backToSkills")}
          </button>
        </div>
      ) : (
        <>
          <div className="space-y-4">
            {pageItems.map((entry) => {
              if (entry.type === "exercise") {
                return (
                  <ExerciseCard
                    key={entry.key}
                    item={entry.item}
                    onOpen={() => setTakingExercise(entry.item)}
                    domId={`assignment-card-${entry.key}`}
                    highlighted={highlightKey === entry.key}
                  />
                );
              }
              if (entry.type === "exerciseBatch") {
                return (
                  <BatchExerciseCard
                    key={entry.key}
                    items={entry.items}
                    onOpen={() => setTakingBatch(entry.items)}
                    domId={`assignment-card-${entry.key}`}
                    highlighted={highlightKey === entry.key}
                  />
                );
              }
              return (
                <ReviewVideoCard
                  key={entry.key}
                  item={entry.item}
                  onOpen={() => setOpenReviewItem(entry.item)}
                  domId={`assignment-card-${entry.key}`}
                  highlighted={highlightKey === entry.key}
                />
              );
            })}
          </div>
          <Pagination page={page} pageSize={PAGE_SIZE} totalElements={skillEntries.length} itemLabel={t("assignments.itemLabel")} onPageChange={setPage} />
        </>
      )}

      {takingExercise && (
        <TakeExerciseModal
          item={takingExercise}
          // Mở đề = BE đã tạo attempt ngay (started_at = NOW), kể cả khi đóng chưa nộp — luôn báo load()
          // để danh sách bên ngoài cập nhật đúng myLatestAttemptId/Status. Bao gồm cả lúc đóng SAU khi
          // đã nộp bài (không còn đóng sớm ngay lúc tắt popup kết quả nữa — xem TakeExerciseModal, giữ
          // modal mở để học sinh đọc hết nhận xét chấm AI/GV chi tiết trước khi tự bấm X/Thoát).
          onClose={() => {
            setTakingExercise(null);
            load();
          }}
        />
      )}

      {takingBatch && (
        <BatchTakeExerciseModal
          items={takingBatch}
          onClose={() => {
            setTakingBatch(null);
            load();
          }}
        />
      )}

      {/* REFLEX (UC-23b) chuyển sang trang riêng từ 2026-08-11 (không phải popup — video khóa hoàn
          toàn + ghi âm tự động theo mốc thời gian, cần toàn màn hình để giám sát chặt). CONNECTION
          (UC-23a) vẫn giữ dạng popup như cũ. */}
      {openReviewItem && openReviewItem.videoType === "REFLEX" && (
        <ReflexVideoTaskPage
          video={openReviewItem.video}
          assignmentId={openReviewItem.assignmentId}
          onClose={() => {
            setOpenReviewItem(null);
            load();
          }}
        />
      )}
      {openReviewItem && openReviewItem.videoType === "CONNECTION" && (
        <ReviewVideoTaskModal
          video={openReviewItem.video}
          assignmentId={openReviewItem.assignmentId}
          // Chỉ tải lại danh sách khi ĐÓNG popup (không phải mỗi lần nộp 1 câu) — trước đây gọi load()
          // ngay sau khi nộp khiến cả tab set loading=true và unmount luôn cả popup đang mở, nhìn như
          // trang bị tải lại giữa chừng. Trạng thái "vừa nộp" giờ tự hiện ngay trong popup (justSubmitted).
          onClose={() => {
            setOpenReviewItem(null);
            load();
          }}
        />
      )}
    </div>
  );
}

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-19 — thẻ điều hướng kiểu "glass" dùng chung cho
 * cấp Unit và cấp Kỹ năng (xem NavView): nền pastel gradient bán trong suốt + blur, ô icon màu đặc, huy hiệu
 * trạng thái, 2 dòng tên bài đầu tiên, số bài + hạn gần nhất, nút mũi tên tròn. Thử ở khu vực BTVN trước.
 * Đếm/hạn tính trên CHÍNH bộ feedItems đã lọc theo filterStatus hiện tại (entries), không phải toàn lớp.
 */
interface NavTint {
  card: string;
  iconBg: string;
}

const NAV_TINTS: NavTint[] = [
  { card: "from-teal/15 via-white/70 to-white/50", iconBg: "from-teal to-teal-deep" },
  { card: "from-coral/15 via-white/70 to-white/50", iconBg: "from-coral to-plum" },
  { card: "from-gold/20 via-white/70 to-white/50", iconBg: "from-gold to-coral" },
  { card: "from-plum/15 via-white/70 to-white/50", iconBg: "from-plum to-teal-deep" }
];

/** Xoay theo INDEX của Unit (ổn định giữa các lần render, không random để tránh đổi màu mỗi lần re-render). */
const UNIT_CARD_ICONS: (typeof BookOpen)[] = [BookOpen, Rocket, Star, Sparkles];

/** Icon + tông màu theo từng Kỹ năng (chỉ số = vị trí trong NAV_TINTS). */
const SKILL_CARD_STYLE: Record<SkillKey, { tint: number; icon: typeof BookOpen }> = {
  VOCAB_GRAMMAR: { tint: 0, icon: BookOpen },
  LISTENING: { tint: 1, icon: Headphones },
  READING: { tint: 2, icon: FileText },
  WRITING: { tint: 3, icon: PenLine },
  OTHER_EXERCISE: { tint: 0, icon: Layers },
  CONNECTION: { tint: 2, icon: Video },
  REFLEX: { tint: 1, icon: Mic }
};

function entryDueAt(entry: FeedEntry): string | null | undefined {
  if (entry.type === "exercise") return entry.item.dueAt;
  if (entry.type === "exerciseBatch") return entry.items[0].dueAt;
  return entry.item.dueAt;
}

function entryTitle(t: (key: string) => string, entry: FeedEntry): string {
  if (entry.type === "exercise") return entry.item.title;
  if (entry.type === "exerciseBatch") return batchGroupTitle(t, entry.items);
  return entry.item.video.title;
}

function NavCard({
  title,
  icon: Icon,
  tint,
  entries,
  onOpen
}: {
  title: string;
  icon: typeof BookOpen;
  tint: NavTint;
  entries: FeedEntry[];
  onOpen: () => void;
}) {
  const { t, i18n } = useTranslation("portal-exercises");
  const overdueCount = entries.filter(isEntryOverduePending).length;
  const pendingCount = entries.filter(isEntryPending).length;
  const earliestDue = entries
    .map(entryDueAt)
    .filter((d): d is string => d != null)
    .sort((a, b) => new Date(a).getTime() - new Date(b).getTime())[0];
  const previewTitles = entries.slice(0, 2).map((entry) => entryTitle(t, entry));

  return (
    <button
      type="button"
      onClick={onOpen}
      className={`group text-left p-5 rounded-3xl bg-gradient-to-br ${tint.card} backdrop-blur-md border border-white/80 shadow-[0_8px_30px_rgba(30,42,69,0.06)] hover:border-teal/50 hover:ring-4 hover:ring-teal/10 hover:shadow-[0_12px_36px_rgba(30,42,69,0.12)] hover:-translate-y-0.5 transition-all cursor-pointer flex flex-col gap-3`}
    >
      <div className="flex items-start justify-between gap-3">
        <div className={`w-14 h-14 rounded-2xl bg-gradient-to-br ${tint.iconBg} text-white flex items-center justify-center shrink-0 shadow-md`}>
          <Icon size={26} />
        </div>
        {overdueCount > 0 ? (
          <span className="px-2.5 py-1 rounded-full bg-rose-50/90 border border-rose-200 text-rose-600 text-xs font-black flex items-center gap-1 whitespace-nowrap">
            <AlertCircle size={12} /> {t("assignments.nav.overdueBadge")}
          </span>
        ) : pendingCount > 0 ? (
          <span className="px-2.5 py-1 rounded-full bg-amber-50/90 border border-amber-200 text-amber-700 text-xs font-black flex items-center gap-1 whitespace-nowrap">
            <Clock size={12} /> {t("assignments.nav.pendingBadge", { count: pendingCount })}
          </span>
        ) : (
          <span className="px-2.5 py-1 rounded-full bg-emerald-50/90 border border-emerald-200 text-emerald-700 text-xs font-black flex items-center gap-1 whitespace-nowrap">
            <CheckCircle2 size={12} /> {t("assignments.nav.doneBadge")}
          </span>
        )}
      </div>

      <h3 className="text-xl font-black text-ink font-display truncate">{title}</h3>

      <ul className="space-y-1 min-h-[2.75rem]">
        {previewTitles.map((name, i) => (
          <li key={i} className="flex items-center gap-2 text-sm font-semibold text-slate-600 min-w-0">
            <span className="w-1.5 h-1.5 rounded-full bg-muted/40 shrink-0" />
            <span className="truncate">{name}</span>
          </li>
        ))}
      </ul>

      <div className="flex items-center justify-between gap-2 mt-auto pt-3 border-t border-white/70">
        <span className="text-sm font-bold text-slate-600 truncate">
          {t("assignments.nav.itemCount", { count: entries.length })}
          {earliestDue ? ` • ${formatDate(earliestDue, i18n.language)}` : ""}
        </span>
        <span className="w-9 h-9 rounded-full bg-white/80 border border-white flex items-center justify-center text-muted shrink-0 group-hover:bg-teal group-hover:border-teal group-hover:text-white group-hover:shadow-md transition-colors">
          <ChevronRight size={16} />
        </span>
      </div>
    </button>
  );
}

function UnitCard({ unit, index, onOpen }: { unit: UnitGroup; index: number; onOpen: () => void }) {
  return (
    <NavCard
      title={unit.unitLabel}
      icon={UNIT_CARD_ICONS[index % UNIT_CARD_ICONS.length]}
      tint={NAV_TINTS[index % NAV_TINTS.length]}
      entries={unit.entries}
      onOpen={onOpen}
    />
  );
}

function SkillCard({ skill, onOpen }: { skill: SkillGroup; onOpen: () => void }) {
  const { t } = useTranslation("portal-exercises");
  const style = SKILL_CARD_STYLE[skill.skillKey];
  return <NavCard title={skillLabel(t, skill.skillKey)} icon={style.icon} tint={NAV_TINTS[style.tint]} entries={skill.entries} onOpen={onOpen} />;
}

function ExerciseCard({
  item,
  onOpen,
  domId,
  highlighted
}: {
  item: AssignedExerciseResponse;
  onOpen: () => void;
  domId?: string;
  highlighted?: boolean;
}) {
  const { t, i18n } = useTranslation("portal-exercises");
  const isOverdue = item.dueAt != null && new Date(item.dueAt) < new Date();
  const retake = needsRetake(item);
  const noMoreRetakes = failedNoMoreRetakes(item);
  const attemptMeta = retake || noMoreRetakes ? null : item.myLatestAttemptStatus ? attemptStatusMeta(t, item.myLatestAttemptStatus) : null;
  const isFullyGraded = item.myLatestAttemptStatus === "FULLY_GRADED";
  const pending = isExercisePending(item);
  /**
   * V152 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — lượt CÒN IN_PROGRESS (chưa
   * nộp) nhưng đã quá hạn nộp và bản giao không cho nộp muộn — TakeExerciseModal sẽ tự khoá thành chỉ
   * xem (xem overdueLockedInProgress ở đó), nên nhãn nút ở đây phải khớp ("Xem lại" thay vì "Tiếp tục").
   */
  const overdueLockedInProgress = isOverdue && !item.lateSubmissionAllowed && item.myLatestAttemptStatus === "IN_PROGRESS";
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — quá hạn + không cho nộp muộn +
   * CHƯA hoàn thành xong (isExerciseLocked) thì hiện hẳn "Đã khóa" ngay ngoài danh sách, không còn mời
   * bấm "Làm bài ngay"/"Tiếp tục làm bài"/"Xem lại bài đã làm" nữa (trước đây bấm vào vẫn mở modal ở
   * chế độ chỉ-xem hoặc báo lỗi hết hạn bên trong — gây hiểu lầm còn thao tác được).
   */
  const locked = isExerciseLocked(item);
  const lateHint = lateSubmissionHint(isOverdue && isExerciseLockable(item), locked, item.lateSubmissionAllowed, item.lateSubmissionDeadline);

  /**
   * V148 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23) — CHỦ Ý chỉ còn 2 nhánh ở màn
   * danh sách: CHƯA làm bao giờ ("Làm bài ngay") hay ĐÃ làm ("Xem bài đã làm"/"Tiếp tục làm bài" nếu
   * đang IN_PROGRESS) — bấm vào LUÔN chỉ mở xem/tiếp tục đúng lượt gần nhất, không còn tự động phán
   * đoán "cần làm lại"/"còn lượt làm thêm" ở đây (dễ nhầm học sinh nghĩ đang xem lại nhưng thực ra vừa
   * âm thầm tạo lượt mới). Muốn làm lượt MỚI phải mở bài rồi bấm nút "Làm lại" tường minh bên trong
   * modal (xem TakeExerciseModal#handleRetake), tự ẩn khi hết lượt/quá hạn nộp.
   */
  const actionLabel =
    item.myLatestAttemptStatus == null
      ? t("assignments.exercise.action.start")
      : item.myLatestAttemptStatus === "IN_PROGRESS" && !overdueLockedInProgress
        ? t("assignments.exercise.action.continue")
        : t("assignments.exercise.action.reviewGraded");

  return (
    <div
      id={domId}
      className={`p-5 bg-white border rounded-2xl transition-all shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4 ${
        highlighted ? "border-teal ring-2 ring-teal/40" : pending ? "border-orange-200 bg-orange-50/20" : "border-line/80"
      }`}
    >
      <div className="space-y-2 flex-1 min-w-0">
        <div className="flex flex-nowrap items-center gap-2 overflow-x-auto scrollbar-hide">
          <span className="px-2.5 py-0.5 rounded-lg bg-teal/10 text-teal border border-teal/20 text-[13px] font-black shrink-0">{item.exerciseCode}</span>
          <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted text-[13px] font-bold shrink-0 whitespace-nowrap">{item.className}</span>
          {retake ? (
            <span className="px-2.5 py-0.5 rounded-lg bg-coral/10 text-coral border border-coral/20 text-[13px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap">
              <Clock size={12} />{" "}
              {t("assignments.exercise.needsRetake", { percent: item.myLatestPercentage != null ? `(${item.myLatestPercentage}%)` : "" })}
            </span>
          ) : noMoreRetakes ? (
            <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted border border-line text-[13px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap">
              <AlertCircle size={12} />{" "}
              {t("assignments.exercise.failedNoMoreRetakes", { percent: item.myLatestPercentage != null ? `(${item.myLatestPercentage}%)` : "" })}
            </span>
          ) : attemptMeta ? (
            <span className={`px-2.5 py-0.5 rounded-lg text-[13px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap ${attemptMeta.className}`}>
              <CheckCircle2 size={12} /> {attemptMeta.label}
            </span>
          ) : null}
          {/* Fix 2026-08-12: hạn nộp/quá hạn phải hiện độc lập với badge trạng thái attempt ở trên —
              trước đây nằm chung 1 chuỗi if/else nên bài "Đang làm dở"/"Cần làm lại" (retake) không
              bao giờ lộ ra badge quá hạn dù isOverdue=true. Chỉ hiện khi bài còn "pending" (chưa nộp
              xong hẳn) — bài đã AUTO_GRADED/FULLY_GRADED(đạt) thì hạn nộp không còn ý nghĩa. */}
          {pending && (
            <span
              className={`px-2.5 py-0.5 rounded-lg border text-[13px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap ${
                isOverdue ? "bg-coral/10 text-coral border-coral/20" : "bg-amber-100 text-amber-800 border-amber-300"
              }`}
            >
              <Clock size={12} />
              {isOverdue ? t("assignments.exercise.overduePrefix") : t("assignments.exercise.duePrefix")}
              {item.dueAt ? formatDateTimeHm(item.dueAt, i18n.language) : t("assignments.exercise.noDeadline")}
            </span>
          )}
          {lateHint && (
            <span className="px-2.5 py-0.5 rounded-lg border text-[13px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap bg-teal/10 text-teal border-teal/20">
              <CheckCircle2 size={12} />
              {lateHint.date ? t(lateHint.key, { date: formatDateTimeHm(lateHint.date, i18n.language) }) : t(lateHint.key)}
            </span>
          )}
          {/* V123, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-14 — GV Việt Nam/nước
              ngoài phụ trách (đọc thật từ Exam.teacherType) + buổi đã giao BTVN này, hiện cho MỌI
              trạng thái (không chỉ khi còn "pending" như hạn nộp ở trên) theo yêu cầu người dùng. */}
          <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted border border-line text-[13px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap">
            <GraduationCap size={12} />{" "}
            {item.teacherType === "VIETNAMESE" ? t("assignments.exercise.teacherVietnamese") : t("assignments.exercise.teacherForeign")}
          </span>
          {item.sessionDate && (
            <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted border border-line text-[13px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap">
              <CalendarDays size={12} /> {t("assignments.exercise.assignedInSession", { date: formatDate(item.sessionDate, i18n.language) })}
            </span>
          )}
        </div>

        <h3 className="text-xl font-black text-ink font-display truncate">{item.title}</h3>
        {/* Bổ sung 2026-09-04 (đã xác nhận với người dùng) — xem ghi chú ở BatchExerciseCard: hiện Unit/
            SubTopic chứa Lesson (examTitle) của Bài này để phân biệt các Lesson trùng tên. */}
        {(item.unitTitle || item.subTopicTitle) && (
          <p className="text-[13px] font-bold text-muted truncate">
            {item.examTitle} · {[item.unitTitle, item.subTopicTitle].filter(Boolean).join(" · ")}
          </p>
        )}

        {item.myLatestTotalScore != null && (
          // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25 — hiện "Hoàn thành: X/Y (Z%)"
          // (điểm đạt được/tổng điểm tối đa của Bài, mỗi câu tự chấm thường 1 điểm, kèm % để dễ so sánh
          // nhanh) thay vì "Điểm: X (Y%)" cũ. Đổi màu pill theo đạt/chưa đạt để học sinh/phụ huynh (đọc
          // qua ParentHomeworkProgressTab) nhận ra ngay không cần đọc kỹ.
          <div
            className={`inline-flex flex-wrap items-center gap-1.5 px-3 py-1.5 rounded-xl border text-sm font-bold ${
              item.myLatestPassed === true
                ? "bg-emerald-50 border-emerald-200 text-emerald-800"
                : item.myLatestPassed === false
                  ? "bg-rose-50 border-rose-200 text-rose-800"
                  : "bg-slate-50 border-slate-200 text-slate-700"
            }`}
          >
            <span>
              {t("assignments.exercise.completedLabel")}
              <span className="font-black">
                {item.myLatestTotalScore}/{item.exerciseTotalPoints}
              </span>
            </span>
            {item.myLatestPercentage != null && <span className="font-black">({item.myLatestPercentage}%)</span>}
            {item.myLatestPassed != null && (
              <span className="flex items-center gap-1 font-black">
                {item.myLatestPassed ? <CheckCircle2 size={13} aria-hidden="true" /> : <AlertCircle size={13} aria-hidden="true" />}
                {item.myLatestPassed ? t("assignments.exercise.passed") : t("assignments.exercise.notPassed")}
              </span>
            )}
          </div>
        )}
      </div>

      <div className="shrink-0">
        {locked ? (
          <span className="w-full md:w-auto flex items-center justify-center gap-1.5 px-5 py-2.5 font-extrabold text-sm rounded-xl bg-slate-100 text-muted border border-line cursor-not-allowed">
            <Lock size={14} /> {t("assignments.lockedAction")}
          </span>
        ) : (
          <button
            onClick={onOpen}
            className={`w-full md:w-auto flex items-center justify-center gap-1.5 px-5 py-2.5 font-extrabold text-sm rounded-xl shadow-sm transition-all cursor-pointer ${
              isFullyGraded && !retake ? "bg-slate-100 hover:bg-slate-200 text-ink border border-line" : "bg-teal hover:bg-teal-deep text-white"
            }`}
          >
            {actionLabel} <ChevronRight size={14} />
          </button>
        )}
      </div>
    </div>
  );
}

/**
 * V150 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — 1 thẻ gộp cho N Bài cùng
 * homeworkBatchId (thay vì N thẻ ExerciseCard riêng biệt như trước) — bấm 1 lần mở BatchTakeExerciseModal
 * làm liên tục cả N Bài. Mirror bố cục/màu sắc ExerciseCard, chỉ khác chỗ mọi thông tin (trạng thái/
 * điểm/hạn nộp) được CỘNG DỒN từ items thay vì đọc thẳng 1 item.
 */
function BatchExerciseCard({
  items,
  onOpen,
  domId,
  highlighted
}: {
  items: AssignedExerciseResponse[];
  onOpen: () => void;
  domId?: string;
  highlighted?: boolean;
}) {
  const { t, i18n } = useTranslation("portal-exercises");
  const first = items[0];
  const groupTitle = batchGroupTitle(t, items);
  const isOverdue = first.dueAt != null && new Date(first.dueAt) < new Date();
  const pending = isBatchPending(items);
  const anyRetake = items.some(needsRetake);
  /** Bổ sung 2026-09-13 (fix bug thật) — mirror ExerciseCard#noMoreRetakes cho cả Lô: còn ít nhất 1 Bài
   * trượt hẳn (hết lượt/không cho làm lại) NHƯNG không còn Bài nào khác trong Lô còn làm lại được nữa
   * (anyRetake=false) — hiện badge "đã hết lượt" thay vì im lặng rơi vào attemptMeta chung chung. */
  const anyFailedNoMoreRetakes = !anyRetake && items.some(failedNoMoreRetakes);
  const anyInProgress = items.some((it) => it.myLatestAttemptStatus === "IN_PROGRESS");
  const allFullyGraded = items.every((it) => it.myLatestAttemptStatus === "FULLY_GRADED");
  const noneStarted = items.every((it) => it.myLatestAttemptStatus == null);
  const attemptMeta =
    anyRetake || anyFailedNoMoreRetakes || noneStarted
      ? null
      : anyInProgress
        ? attemptStatusMeta(t, "IN_PROGRESS")
        : allFullyGraded
          ? attemptStatusMeta(t, "FULLY_GRADED")
          : attemptStatusMeta(t, "AUTO_GRADED");
  /** V152 — mirror ExerciseCard#overdueLockedInProgress, áp dụng cho cả Lô (dùng chung dueAt/lateSubmissionAllowed của Bài đại diện). */
  const overdueLockedInProgress = isOverdue && !first.lateSubmissionAllowed && anyInProgress;
  /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — mirror ExerciseCard#locked. */
  const locked = isBatchLocked(items);
  const lateHint = lateSubmissionHint(isOverdue && isBatchLockable(items), locked, first.lateSubmissionAllowed, first.lateSubmissionDeadline);

  const totalScore = items.reduce((sum, it) => sum + (it.myLatestTotalScore ?? 0), 0);
  const totalPoints = items.reduce((sum, it) => sum + (it.exerciseTotalPoints ?? 0), 0);
  const percentage = totalPoints > 0 ? Math.round((totalScore / totalPoints) * 10000) / 100 : null;
  const passed = allFullyGraded && percentage != null ? percentage >= 70 : null;

  const actionLabel = noneStarted
    ? t("assignments.exercise.action.start")
    : anyInProgress && !overdueLockedInProgress
      ? t("assignments.exercise.action.continue")
      : t("assignments.exercise.action.reviewGraded");

  return (
    <div
      id={domId}
      className={`p-5 bg-white border rounded-2xl transition-all shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4 ${
        highlighted ? "border-teal ring-2 ring-teal/40" : pending ? "border-orange-200 bg-orange-50/20" : "border-line/80"
      }`}
    >
      <div className="space-y-2 flex-1 min-w-0">
        <div className="flex flex-nowrap items-center gap-2 overflow-x-auto scrollbar-hide">
          <span className="px-2.5 py-0.5 rounded-lg bg-teal/10 text-teal border border-teal/20 text-[13px] font-black shrink-0 whitespace-nowrap">
            {t("assignments.batch.countSuffix", { count: items.length })}
          </span>
          <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted text-[13px] font-bold shrink-0 whitespace-nowrap">{first.className}</span>
          {anyRetake ? (
            <span className="px-2.5 py-0.5 rounded-lg bg-coral/10 text-coral border border-coral/20 text-[13px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap">
              <Clock size={12} /> {t("assignments.exercise.needsRetake", { percent: percentage != null ? `(${percentage}%)` : "" })}
            </span>
          ) : anyFailedNoMoreRetakes ? (
            <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted border border-line text-[13px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap">
              <AlertCircle size={12} /> {t("assignments.exercise.failedNoMoreRetakes", { percent: percentage != null ? `(${percentage}%)` : "" })}
            </span>
          ) : attemptMeta ? (
            <span className={`px-2.5 py-0.5 rounded-lg text-[13px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap ${attemptMeta.className}`}>
              <CheckCircle2 size={12} /> {attemptMeta.label}
            </span>
          ) : null}
          {pending && (
            <span
              className={`px-2.5 py-0.5 rounded-lg border text-[13px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap ${
                isOverdue ? "bg-coral/10 text-coral border-coral/20" : "bg-amber-100 text-amber-800 border-amber-300"
              }`}
            >
              <Clock size={12} />
              {isOverdue ? t("assignments.exercise.overduePrefix") : t("assignments.exercise.duePrefix")}
              {first.dueAt ? formatDateTimeHm(first.dueAt, i18n.language) : t("assignments.exercise.noDeadline")}
            </span>
          )}
          {lateHint && (
            <span className="px-2.5 py-0.5 rounded-lg border text-[13px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap bg-teal/10 text-teal border-teal/20">
              <CheckCircle2 size={12} />
              {lateHint.date ? t(lateHint.key, { date: formatDateTimeHm(lateHint.date, i18n.language) }) : t(lateHint.key)}
            </span>
          )}
          <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted border border-line text-[13px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap">
            <GraduationCap size={12} />{" "}
            {first.teacherType === "VIETNAMESE" ? t("assignments.exercise.teacherVietnamese") : t("assignments.exercise.teacherForeign")}
          </span>
          {first.sessionDate && (
            <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted border border-line text-[13px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap">
              <CalendarDays size={12} /> {t("assignments.exercise.assignedInSession", { date: formatDate(first.sessionDate, i18n.language) })}
            </span>
          )}
        </div>

        <h3 className="text-xl font-black text-ink font-display truncate">{groupTitle}</h3>
        {/* Bổ sung 2026-09-04 (đã xác nhận với người dùng) — fix bug thật: Lesson đánh số lặp lại (Lesson
            1, 2, 3...) giữa các Unit/SubTopic khác nhau, học sinh dễ nhầm lẫn khi chỉ thấy examTitle
            trên thẻ (mirror ExerciseAssignPage.tsx bên admin). */}
        {(first.unitTitle || first.subTopicTitle) && (
          <p className="text-[13px] font-bold text-muted truncate">{[first.unitTitle, first.subTopicTitle].filter(Boolean).join(" · ")}</p>
        )}

        {!noneStarted && (
          <div
            className={`inline-flex flex-wrap items-center gap-1.5 px-3 py-1.5 rounded-xl border text-sm font-bold ${
              passed === true
                ? "bg-emerald-50 border-emerald-200 text-emerald-800"
                : passed === false
                  ? "bg-rose-50 border-rose-200 text-rose-800"
                  : "bg-slate-50 border-slate-200 text-slate-700"
            }`}
          >
            <span>
              {t("assignments.exercise.completedLabel")}
              <span className="font-black">
                {totalScore}/{totalPoints}
              </span>
            </span>
            {percentage != null && <span className="font-black">({percentage}%)</span>}
            {passed != null && (
              <span className="flex items-center gap-1 font-black">
                {passed ? <CheckCircle2 size={13} aria-hidden="true" /> : <AlertCircle size={13} aria-hidden="true" />}
                {passed ? t("assignments.exercise.passed") : t("assignments.exercise.notPassed")}
              </span>
            )}
          </div>
        )}
      </div>

      <div className="shrink-0">
        {locked ? (
          <span className="w-full md:w-auto flex items-center justify-center gap-1.5 px-5 py-2.5 font-extrabold text-sm rounded-xl bg-slate-100 text-muted border border-line cursor-not-allowed">
            <Lock size={14} /> {t("assignments.lockedAction")}
          </span>
        ) : (
          <button
            onClick={onOpen}
            className={`w-full md:w-auto flex items-center justify-center gap-1.5 px-5 py-2.5 font-extrabold text-sm rounded-xl shadow-sm transition-all cursor-pointer ${
              allFullyGraded && !anyRetake ? "bg-slate-100 hover:bg-slate-200 text-ink border border-line" : "bg-teal hover:bg-teal-deep text-white"
            }`}
          >
            {actionLabel} <ChevronRight size={14} />
          </button>
        )}
      </div>
    </div>
  );
}

function ReviewVideoCard({
  item,
  onOpen,
  domId,
  highlighted
}: {
  item: ReviewVideoHomeworkItem;
  onOpen: () => void;
  domId?: string;
  highlighted?: boolean;
}) {
  const { t, i18n } = useTranslation("portal-exercises");
  const { video, videoType, setTitle, reflexStats, dueAt } = item;
  const isConnection = videoType === "CONNECTION";
  const answerable = isConnection ? isConnectionAnswerable(item) : isReflexAnswerable(item);
  const fullyAnswered = isConnection ? isConnectionCompleted(item) : isReflexFullyAnswered(item);
  const isOverdue = dueAt != null && new Date(dueAt) < new Date();

  let statusBadge: React.ReactNode;
  if (isConnection) {
    statusBadge = !item.connectionStats ? (
      <span className="px-2.5 py-0.5 rounded-lg bg-sky text-teal-deep text-[11px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap">
        <Play size={12} /> {t("assignments.video.watchToReview")}
      </span>
    ) : fullyAnswered ? (
      <span className="px-2.5 py-0.5 rounded-lg bg-teal/10 text-teal-deep text-[11px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap">
        <CheckCircle2 size={12} />{" "}
        {t("assignments.video.achievedViews", { count: item.connectionStats.viewCount, required: item.connectionStats.requiredViewCount })}
      </span>
    ) : (
      <span className="px-2.5 py-0.5 rounded-lg bg-amber-100 text-amber-800 border border-amber-300 text-[11px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap">
        <Clock size={12} />{" "}
        {t("assignments.video.achievedViews", { count: item.connectionStats.viewCount, required: item.connectionStats.requiredViewCount })}
      </span>
    );
  } else if (!answerable) {
    statusBadge = (
      <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted text-[11px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap">
        <Clock size={12} /> {t("assignments.video.noQuestions")}
      </span>
    );
  } else if (fullyAnswered) {
    statusBadge = (
      <span className="px-2.5 py-0.5 rounded-lg bg-teal/10 text-teal-deep text-[11px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap">
        <CheckCircle2 size={12} /> {t("assignments.video.submitted")}
      </span>
    );
  } else {
    statusBadge = (
      <span className="px-2.5 py-0.5 rounded-lg bg-amber-100 text-amber-800 border border-amber-300 text-[11px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap">
        <Clock size={12} /> {t("assignments.video.answeredCount", { answered: reflexStats!.answeredQuestions, total: reflexStats!.totalQuestions })}
      </span>
    );
  }

  const pending = answerable && !fullyAnswered;
  /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — mirror ExerciseCard#locked. */
  const locked = isVideoLocked(item);
  const lateHint = lateSubmissionHint(isOverdue && pending, locked, item.lateSubmissionAllowed, item.lateSubmissionDeadline);
  const actionLabel = isConnection
    ? t("assignments.video.action.watch")
    : !answerable
      ? t("assignments.video.action.watch")
      : fullyAnswered
        ? t("assignments.video.action.reviewSubmitted")
        : t("assignments.video.action.answer");

  return (
    <div
      id={domId}
      className={`p-5 bg-white border rounded-2xl transition-all shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4 ${
        highlighted ? "border-teal ring-2 ring-teal/40" : pending ? "border-orange-200 bg-orange-50/20" : "border-line/80"
      }`}
    >
      <div className="space-y-2 flex-1 min-w-0">
        <div className="flex flex-nowrap items-center gap-2 overflow-x-auto scrollbar-hide">
          <span className="px-2.5 py-0.5 rounded-lg bg-teal/10 text-teal border border-teal/20 text-[13px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap">
            {isConnection ? <Link2 size={12} /> : <MessageCircle size={12} />}{" "}
            {isConnection ? t("assignments.video.connectionType") : t("assignments.video.reflexType")}
          </span>
          {/* <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted text-[11px] font-bold truncate max-w-[200px] shrink-0">{setTitle}</span> */}
          {statusBadge}
          {dueAt && (
            <span
              className={`px-2.5 py-0.5 rounded-lg border text-[13px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap ${
                isOverdue ? "bg-coral/10 text-coral border-coral/20" : "bg-amber-100 text-amber-800 border-amber-300"
              }`}
            >
              <Clock size={12} />
              {isOverdue ? t("assignments.exercise.overduePrefix") : t("assignments.exercise.duePrefix")}
              {formatDateTimeHm(dueAt, i18n.language)}
            </span>
          )}
          {lateHint && (
            <span className="px-2.5 py-0.5 rounded-lg border text-[13px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap bg-teal/10 text-teal border-teal/20">
              <CheckCircle2 size={12} />
              {lateHint.date ? t(lateHint.key, { date: formatDateTimeHm(lateHint.date, i18n.language) }) : t(lateHint.key)}
            </span>
          )}
          {/* V123, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-14 — GV Việt Nam/nước
              ngoài phụ trách (đọc thật từ ReviewVideoSet.teacherType) + buổi đã giao BTVN này, hiện
              cho MỌI trạng thái theo yêu cầu người dùng. teacherType luôn có (thuộc tính của Bộ, kể
              cả video chỉ nằm trong Kho chưa được giao) — chỉ sessionDate cần bản giao thật. */}
          <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted border border-line text-[13px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap">
            <GraduationCap size={12} />{" "}
            {item.teacherType === "VIETNAMESE" ? t("assignments.exercise.teacherVietnamese") : t("assignments.exercise.teacherForeign")}
          </span>
          {item.sessionDate && (
            <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted border border-line text-[13px] font-black flex items-center gap-1 shrink-0 whitespace-nowrap">
              <CalendarDays size={12} /> {t("assignments.exercise.assignedInSession", { date: formatDate(item.sessionDate, i18n.language) })}
            </span>
          )}
        </div>
        <h3 className="text-xl font-black text-ink font-display truncate">{video.title}</h3>
        {/* Bổ sung 2026-09-04 (đã xác nhận với người dùng) — mirror ExerciseCard/BatchExerciseCard: hiện
            Unit/SubTopic để phân biệt Bộ video trùng tên giữa các Unit/SubTopic khác nhau. */}
        {(item.unitTitle || item.subTopicTitle) && (
          <p className="text-[13px] font-bold text-muted truncate">{[item.unitTitle, item.subTopicTitle].filter(Boolean).join(" · ")}</p>
        )}
      </div>

      <div className="shrink-0">
        {locked ? (
          <span className="w-full md:w-auto flex items-center justify-center gap-1.5 px-5 py-2.5 font-extrabold text-sm rounded-xl bg-slate-100 text-muted border border-line cursor-not-allowed">
            <Lock size={14} /> {t("assignments.lockedAction")}
          </span>
        ) : (
          <button
            onClick={onOpen}
            className={`w-full md:w-auto flex items-center justify-center gap-1.5 px-5 py-2.5 font-extrabold text-sm rounded-xl shadow-sm transition-all cursor-pointer ${
              fullyAnswered ? "bg-slate-100 hover:bg-slate-200 text-ink border border-line" : "bg-teal hover:bg-teal-deep text-white"
            }`}
          >
            {actionLabel} <ChevronRight size={14} />
          </button>
        )}
      </div>
    </div>
  );
}
