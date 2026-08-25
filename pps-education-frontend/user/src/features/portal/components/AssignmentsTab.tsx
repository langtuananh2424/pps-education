import React, { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { AlertCircle, Bell, BookOpen, CalendarDays, Check, CheckCircle2, ChevronRight, Clock, Filter, GraduationCap, Link2, MessageCircle, Play, Video } from "lucide-react";
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

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05 (backend V89
 * `ExerciseAttemptService#applyPassOutcome`) — BTVN đã chấm xong (FULLY_GRADED)
 * nhưng dưới ngưỡng đạt (`myLatestPassed === false`) vẫn tính là "cần hoàn
 * thành" (chưa xong thật sự), không phải "đã nộp & đã chấm" — bản giao vẫn
 * ACTIVE ở backend đúng tinh thần này, FE trước đây chưa đồng bộ theo.
 */
function needsRetake(item: AssignedExerciseResponse): boolean {
  return item.myLatestAttemptStatus === "FULLY_GRADED" && item.myLatestPassed === false;
}

function isExercisePending(item: AssignedExerciseResponse): boolean {
  return item.myLatestAttemptStatus == null || item.myLatestAttemptStatus === "IN_PROGRESS" || needsRetake(item);
}

/** V150 — 1 Lô "Cần hoàn thành" khi CÒN ÍT NHẤT 1 Bài trong đó pending (mirror isExercisePending, áp dụng cho cả nhóm thay vì 1 Bài đơn). */
function isBatchPending(items: AssignedExerciseResponse[]): boolean {
  return items.some(isExercisePending);
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

type FilterStatus = "ALL" | "PENDING" | "GRADED";
type FilterType = "ALL" | "EXERCISE" | "VIDEO";

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
  /** V123 — GV Việt Nam/nước ngoài phụ trách bộ này (ReviewVideoSetResponse.teacherType, luôn có). */
  teacherType: "VIETNAMESE" | "FOREIGN";
  /** V123 — ngày buổi học GV đã giao BTVN này — undefined nếu chỉ nằm trong Kho hoặc bản giao TRƯỚC V123. */
  sessionDate?: string | null;
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
  const [filterStatus, setFilterStatus] = useState<FilterStatus>("ALL");
  const [filterType, setFilterType] = useState<FilterType>("ALL");
  const [takingExercise, setTakingExercise] = useState<AssignedExerciseResponse | null>(null);
  /** V150 — 1 Lô đang được làm liên tục (N thẻ cùng homeworkBatchId, xem groupExercisesByBatch/BatchTakeExerciseModal). */
  const [takingBatch, setTakingBatch] = useState<AssignedExerciseResponse[] | null>(null);
  const [openReviewItem, setOpenReviewItem] = useState<ReviewVideoHomeworkItem | null>(null);
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — danh sách BTVN có thể dài (nhiều
  // Bài ngữ pháp + Video ôn tập cộng lại), phân trang để tránh cuộn quá nhiều. Về trang 1 mỗi khi đổi
  // bộ lọc trạng thái/loại bài.
  const [page, setPage] = useState(0);
  // Bổ sung ngoài SDD gốc — reset về trang 1 khi đổi bộ lọc. PHẢI đặt trước early-return `if (loading)`
  // bên dưới (Rules of Hooks: mọi hook phải gọi VÔ ĐIỀU KIỆN, không được đặt sau early-return — trước
  // đây đặt sai chỗ, hook bị bỏ qua lúc loading=true rồi lại gọi khi loading=false, gây lỗi "Rendered
  // more hooks than during the previous render").
  useEffect(() => setPage(0), [filterStatus, filterType]);
  // Dropdown icon lọc "loại bài" thay cho hàng nút riêng — đỡ chiếm thêm 1 hàng, gộp chung hàng với
  // 3 nút trạng thái, dùng chung cho mọi kích thước màn hình (theo yêu cầu người dùng, 2026-08-01).
  const [filterTypeOpen, setFilterTypeOpen] = useState(false);
  const filterTypeRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!filterTypeOpen) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (filterTypeRef.current && !filterTypeRef.current.contains(e.target as Node)) setFilterTypeOpen(false);
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [filterTypeOpen]);

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
                sessionDate: a.sessionDate
              }))
            : [{ set, dueAt: undefined as string | undefined, assignmentId: undefined as number | undefined, sessionDate: undefined as string | null | undefined }];
        });
        // videos của cùng 1 bộ giống hệt nhau dù giao lặp lại nhiều lần — cache theo setId để không gọi
        // lại API listReviewVideos thừa cho mỗi bản giao trùng bộ.
        const videosBySetId = new Map<number, Promise<ReviewVideoResponse[]>>();
        const videosOf = (setId: number) => {
          if (!videosBySetId.has(setId)) videosBySetId.set(setId, listReviewVideos(setId));
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
                  teacherType: g.set.teacherType,
                  sessionDate: g.sessionDate
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
    setFilterType("ALL");
    let key: string | null = null;
    if (autoOpenExerciseAssignmentId != null) {
      const match = exercises.find((e) => e.assignmentId === autoOpenExerciseAssignmentId);
      // V150 — Bài thuộc 1 Lô giờ hiện gộp thành 1 thẻ "exb-<batchId>" (xem groupExercisesByBatch),
      // không còn thẻ "ex-<assignmentId>" riêng cho từng Bài trong lô.
      if (match) key = match.homeworkBatchId != null ? `exb-${match.homeworkBatchId}` : `ex-${match.assignmentId}`;
    } else if (autoOpenReviewVideoAssignmentId != null) {
      const assignment = videoAssignments.find((a) => a.assignmentId === autoOpenReviewVideoAssignmentId);
      // 1 bộ có thể gồm nhiều video — chưa có khái niệm "đúng video nào" ứng với 1 lần giao (giao theo
      // cả BỘ), nên focus vào video ĐẦU của ĐÚNG bản giao này (khớp assignmentId, không chỉ setId — 1
      // bộ có thể có nhiều bản giao cùng lúc, xem fix 2026-08-12 ở load()).
      const match = assignment ? reviewItems.find((x) => x.assignmentId === assignment.assignmentId) : undefined;
      if (match) key = `rv-${match.assignmentId ?? "lib"}-${match.video.id}`;
    }
    setPendingHighlightKey(key);
    onAutoOpenHandled?.();
    // onAutoOpenHandled cố tình không đưa vào deps — PortalPage truyền hàm inline (đổi identity mỗi
    // render cha), đưa vào đây sẽ khiến effect chạy lại thừa mỗi khi cha re-render vì lý do khác, dù
    // giá trị autoOpen*AssignmentId chưa đổi.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading, autoOpenExerciseAssignmentId, autoOpenReviewVideoAssignmentId, exercises, reviewItems, videoAssignments]);

  useEffect(() => {
    if (loading || !pendingHighlightKey) return;
    // Tính lại feedItems "ALL/ALL" (khớp đúng bộ lọc vừa reset ở effect trên) để tìm đúng vị trí/trang.
    const allKeys = [
      ...singleExercises.map((e) => `ex-${e.assignmentId}`),
      ...batchGroups.map((items) => `exb-${items[0].homeworkBatchId}`),
      ...reviewItems.map((x) => `rv-${x.assignmentId ?? "lib"}-${x.video.id}`)
    ];
    const idx = allKeys.indexOf(pendingHighlightKey);
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
  }, [loading, pendingHighlightKey, page, exercises, reviewItems]);

  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — báo pendingCount lên PortalPage
  // (badge sidebar). Đặt TRƯỚC early-return `if (loading)` bên dưới (Rules of Hooks — cùng lý do đã
  // ghi chú ở các effect khác trong file này).
  useEffect(() => {
    if (loading) return;
    const count =
      singleExercises.filter(isExercisePending).length +
      batchGroups.filter(isBatchPending).length +
      reviewItems.filter((x) => isReflexAnswerable(x) && !isReflexFullyAnswered(x)).length +
      reviewItems.filter((x) => isConnectionAnswerable(x) && !isConnectionCompleted(x)).length;
    onPendingCountChange?.(count);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading, exercises, reviewItems]);

  if (loading) return <p className="text-sm text-muted font-bold">{t("assignments.loading")}</p>;

  const exerciseCardCount = singleExercises.length + batchGroups.length;
  const pendingCount =
    singleExercises.filter(isExercisePending).length +
    batchGroups.filter(isBatchPending).length +
    reviewItems.filter((x) => isReflexAnswerable(x) && !isReflexFullyAnswered(x)).length +
    reviewItems.filter((x) => isConnectionAnswerable(x) && !isConnectionCompleted(x)).length;
  const gradedCount =
    singleExercises.filter((e) => !isExercisePending(e)).length +
    batchGroups.filter((items) => !isBatchPending(items)).length +
    reviewItems.filter((x) => isReflexAnswerable(x) && isReflexFullyAnswered(x)).length +
    reviewItems.filter((x) => isConnectionAnswerable(x) && isConnectionCompleted(x)).length;

  const filteredSingleExercises = singleExercises.filter((e) => {
    if (filterType === "VIDEO") return false;
    if (filterStatus === "PENDING") return isExercisePending(e);
    if (filterStatus === "GRADED") return !isExercisePending(e);
    return true;
  });
  const filteredBatchGroups = batchGroups.filter((items) => {
    if (filterType === "VIDEO") return false;
    const pending = isBatchPending(items);
    if (filterStatus === "PENDING") return pending;
    if (filterStatus === "GRADED") return !pending;
    return true;
  });
  const filteredReviewItems = reviewItems.filter((x) => {
    if (filterType === "EXERCISE") return false;
    if (filterStatus === "ALL") return true;
    if (x.videoType === "CONNECTION") {
      if (!isConnectionAnswerable(x)) return false; // API tiến độ lỗi/chưa tải xong — chưa tham gia lọc
      return filterStatus === "PENDING" ? !isConnectionCompleted(x) : isConnectionCompleted(x);
    }
    if (!isReflexAnswerable(x)) return false; // REFLEX chưa có câu hỏi — chưa tham gia lọc
    if (filterStatus === "PENDING") return !isReflexFullyAnswered(x);
    return isReflexFullyAnswered(x);
  });

  // Gộp 2 danh sách (Bài ngữ pháp + Video ôn tập) thành 1 để phân trang chung — đúng tinh thần "1
  // danh sách BTVN duy nhất" đã gộp ở tab này (xem Javadoc đầu file), không tách trang riêng từng loại.
  const feedItems = [
    ...filteredSingleExercises.map((item) => ({ type: "exercise" as const, key: `ex-${item.assignmentId}`, item })),
    ...filteredBatchGroups.map((items) => ({ type: "exerciseBatch" as const, key: `exb-${items[0].homeworkBatchId}`, items })),
    ...filteredReviewItems.map((item) => ({ type: "video" as const, key: `rv-${item.assignmentId ?? "lib"}-${item.video.id}`, item }))
  ];
  const pageItems = feedItems.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

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
            onClick={() => setFilterStatus("PENDING")}
            className="px-5 py-2.5 bg-amber-500 hover:bg-amber-600 text-white font-extrabold text-xs rounded-xl shadow-sm transition-all shrink-0 cursor-pointer"
          >
            {t("assignments.banner.actionButton")}
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

      <div className="space-y-2.5 border-b border-line pb-3">
        {/* 1 hàng — 3 nút trạng thái tự cuộn ngang kiểu carousel, cuối hàng là icon dropdown lọc
            "loại bài" — dùng chung cho mọi kích thước màn hình (theo yêu cầu người dùng, 2026-08-01;
            trước đó desktop có 1 khối riêng 2 hàng nút đầy đủ, nay bỏ để đồng nhất với mobile). */}
        <div className="flex items-center gap-2">
          <div className="flex-1 min-w-0 flex items-center gap-2 overflow-x-auto scrollbar-hide snap-x snap-proximity">
            <button
              onClick={() => setFilterStatus("ALL")}
              className={`shrink-0 snap-start px-4 py-2 rounded-xl text-xs font-black transition-all cursor-pointer ${
                filterStatus === "ALL" ? "bg-teal text-white shadow-sm" : "bg-slate-100 hover:bg-slate-200 text-muted"
              }`}
            >
              {t("assignments.filters.all", { count: exerciseCardCount + reviewItems.length })}
            </button>
            <button
              onClick={() => setFilterStatus("PENDING")}
              className={`shrink-0 snap-start px-4 py-2 rounded-xl text-xs font-black transition-all cursor-pointer flex items-center gap-1.5 ${
                filterStatus === "PENDING" ? "bg-orange-500 text-white shadow-sm" : "bg-slate-100 hover:bg-slate-200 text-muted"
              }`}
            >
              <Clock size={14} /> {t("assignments.filters.pending", { count: pendingCount })}
            </button>
            <button
              onClick={() => setFilterStatus("GRADED")}
              className={`shrink-0 snap-start px-4 py-2 rounded-xl text-xs font-black transition-all cursor-pointer flex items-center gap-1.5 ${
                filterStatus === "GRADED" ? "bg-teal text-white shadow-sm" : "bg-slate-100 hover:bg-slate-200 text-muted"
              }`}
            >
              <CheckCircle2 size={14} /> {t("assignments.filters.graded", { count: gradedCount })}
            </button>
          </div>

          <div className="relative shrink-0" ref={filterTypeRef}>
            <button
              type="button"
              onClick={() => setFilterTypeOpen((v) => !v)}
              aria-label={t("assignments.filters.typeAriaLabel")}
              aria-haspopup="dialog"
              aria-expanded={filterTypeOpen}
              className={`w-10 h-10 rounded-xl border flex items-center justify-center transition-all cursor-pointer ${
                filterType !== "ALL" ? "bg-ink text-white border-ink" : "bg-white border-line text-muted hover:bg-slate-50"
              }`}
            >
              <Filter size={16} aria-hidden="true" />
            </button>

            {filterTypeOpen && (
              <div
                role="dialog"
                aria-label={t("assignments.filters.typeDialogAriaLabel")}
                className="absolute right-0 top-full mt-2 z-30 w-56 bg-white border border-line rounded-2xl shadow-lg p-1.5 space-y-0.5"
              >
                {(
                  [
                    ["ALL", t("assignments.filters.typeAll"), null] as const,
                    ["EXERCISE", t("assignments.filters.typeExercise", { count: exerciseCardCount }), BookOpen] as const,
                    ["VIDEO", t("assignments.filters.typeVideo", { count: reviewItems.length }), Video] as const
                  ]
                ).map(([value, label, Icon]) => (
                  <button
                    key={value}
                    type="button"
                    onClick={() => {
                      setFilterType(value);
                      setFilterTypeOpen(false);
                    }}
                    className={`w-full flex items-center justify-between gap-2 px-3 py-2 rounded-xl text-xs font-bold text-left transition-colors ${
                      filterType === value ? "bg-ink/5 text-ink font-black" : "text-muted hover:bg-slate-50"
                    }`}
                  >
                    <span className="flex items-center gap-1.5">
                      {Icon && <Icon size={13} aria-hidden="true" />} {label}
                    </span>
                    {filterType === value && <Check size={14} className="shrink-0" aria-hidden="true" />}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      {feedItems.length === 0 ? (
        <p className="text-xs text-muted font-bold italic text-center py-10">{t("assignments.empty")}</p>
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
          <Pagination page={page} pageSize={PAGE_SIZE} totalElements={feedItems.length} itemLabel={t("assignments.itemLabel")} onPageChange={setPage} />
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
  const attemptMeta = retake ? null : item.myLatestAttemptStatus ? attemptStatusMeta(t, item.myLatestAttemptStatus) : null;
  const isFullyGraded = item.myLatestAttemptStatus === "FULLY_GRADED";
  const pending = isExercisePending(item);

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
      : item.myLatestAttemptStatus === "IN_PROGRESS"
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
        <div className="flex flex-wrap items-center gap-2">
          <span className="px-2.5 py-0.5 rounded-lg bg-teal/10 text-teal border border-teal/20 text-[11px] font-black">{item.exerciseCode}</span>
          <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted text-[11px] font-bold">{item.className}</span>
          {retake ? (
            <span className="px-2.5 py-0.5 rounded-lg bg-coral/10 text-coral border border-coral/20 text-[11px] font-black flex items-center gap-1">
              <Clock size={12} />{" "}
              {t("assignments.exercise.needsRetake", { percent: item.myLatestPercentage != null ? `(${item.myLatestPercentage}%)` : "" })}
            </span>
          ) : attemptMeta ? (
            <span className={`px-2.5 py-0.5 rounded-lg text-[11px] font-black flex items-center gap-1 ${attemptMeta.className}`}>
              <CheckCircle2 size={12} /> {attemptMeta.label}
            </span>
          ) : null}
          {/* Fix 2026-08-12: hạn nộp/quá hạn phải hiện độc lập với badge trạng thái attempt ở trên —
              trước đây nằm chung 1 chuỗi if/else nên bài "Đang làm dở"/"Cần làm lại" (retake) không
              bao giờ lộ ra badge quá hạn dù isOverdue=true. Chỉ hiện khi bài còn "pending" (chưa nộp
              xong hẳn) — bài đã AUTO_GRADED/FULLY_GRADED(đạt) thì hạn nộp không còn ý nghĩa. */}
          {pending && (
            <span
              className={`px-2.5 py-0.5 rounded-lg border text-[11px] font-black flex items-center gap-1 ${
                isOverdue ? "bg-coral/10 text-coral border-coral/20" : "bg-amber-100 text-amber-800 border-amber-300"
              }`}
            >
              <Clock size={12} />
              {isOverdue ? t("assignments.exercise.overduePrefix") : t("assignments.exercise.duePrefix")}
              {item.dueAt ? formatDateTimeHm(item.dueAt, i18n.language) : t("assignments.exercise.noDeadline")}
            </span>
          )}
          {/* V123, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-14 — GV Việt Nam/nước
              ngoài phụ trách (đọc thật từ Exam.teacherType) + buổi đã giao BTVN này, hiện cho MỌI
              trạng thái (không chỉ khi còn "pending" như hạn nộp ở trên) theo yêu cầu người dùng. */}
          <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted border border-line text-[11px] font-black flex items-center gap-1">
            <GraduationCap size={12} />{" "}
            {item.teacherType === "VIETNAMESE" ? t("assignments.exercise.teacherVietnamese") : t("assignments.exercise.teacherForeign")}
          </span>
          {item.sessionDate && (
            <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted border border-line text-[11px] font-black flex items-center gap-1">
              <CalendarDays size={12} /> {t("assignments.exercise.assignedInSession", { date: formatDate(item.sessionDate, i18n.language) })}
            </span>
          )}
        </div>

        <h3 className="text-base font-black text-ink font-display truncate">{item.title}</h3>

        {item.myLatestTotalScore != null && (
          // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — trước đây chỉ hiện "Điểm: X"
          // chữ nhỏ màu xám, không thấy % lẫn đạt/chưa đạt ở đây (dù data đã có sẵn qua
          // myLatestPercentage/myLatestPassed) — gộp cả 3 vào 1 pill nổi bật, đổi màu theo kết quả để
          // học sinh/phụ huynh (đọc qua ParentHomeworkProgressTab) nhận ra ngay không cần đọc kỹ.
          <div
            className={`inline-flex flex-wrap items-center gap-1.5 px-3 py-1.5 rounded-xl border text-xs font-bold ${
              item.myLatestPassed === true
                ? "bg-emerald-50 border-emerald-200 text-emerald-800"
                : item.myLatestPassed === false
                  ? "bg-rose-50 border-rose-200 text-rose-800"
                  : "bg-slate-50 border-slate-200 text-slate-700"
            }`}
          >
            <span>
              {t("assignments.exercise.scoreLabel")}
              <span className="font-black">{item.myLatestTotalScore}</span>
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
        <button
          onClick={onOpen}
          className={`w-full md:w-auto flex items-center justify-center gap-1.5 px-5 py-2.5 font-extrabold text-xs rounded-xl shadow-sm transition-all cursor-pointer ${
            isFullyGraded && !retake ? "bg-slate-100 hover:bg-slate-200 text-ink border border-line" : "bg-teal hover:bg-teal-deep text-white"
          }`}
        >
          {actionLabel} <ChevronRight size={14} />
        </button>
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
  const anyInProgress = items.some((it) => it.myLatestAttemptStatus === "IN_PROGRESS");
  const allFullyGraded = items.every((it) => it.myLatestAttemptStatus === "FULLY_GRADED");
  const noneStarted = items.every((it) => it.myLatestAttemptStatus == null);
  const attemptMeta = anyRetake || noneStarted ? null : anyInProgress ? attemptStatusMeta(t, "IN_PROGRESS") : allFullyGraded ? attemptStatusMeta(t, "FULLY_GRADED") : attemptStatusMeta(t, "AUTO_GRADED");

  const totalScore = items.reduce((sum, it) => sum + (it.myLatestTotalScore ?? 0), 0);
  const totalPoints = items.reduce((sum, it) => sum + (it.exerciseTotalPoints ?? 0), 0);
  const percentage = totalPoints > 0 ? Math.round((totalScore / totalPoints) * 10000) / 100 : null;
  const passed = allFullyGraded && percentage != null ? percentage >= 70 : null;

  const actionLabel = noneStarted
    ? t("assignments.exercise.action.start")
    : anyInProgress
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
        <div className="flex flex-wrap items-center gap-2">
          <span className="px-2.5 py-0.5 rounded-lg bg-teal/10 text-teal border border-teal/20 text-[11px] font-black">
            {t("assignments.batch.countSuffix", { count: items.length })}
          </span>
          <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted text-[11px] font-bold">{first.className}</span>
          {anyRetake ? (
            <span className="px-2.5 py-0.5 rounded-lg bg-coral/10 text-coral border border-coral/20 text-[11px] font-black flex items-center gap-1">
              <Clock size={12} /> {t("assignments.exercise.needsRetake", { percent: percentage != null ? `(${percentage}%)` : "" })}
            </span>
          ) : attemptMeta ? (
            <span className={`px-2.5 py-0.5 rounded-lg text-[11px] font-black flex items-center gap-1 ${attemptMeta.className}`}>
              <CheckCircle2 size={12} /> {attemptMeta.label}
            </span>
          ) : null}
          {pending && (
            <span
              className={`px-2.5 py-0.5 rounded-lg border text-[11px] font-black flex items-center gap-1 ${
                isOverdue ? "bg-coral/10 text-coral border-coral/20" : "bg-amber-100 text-amber-800 border-amber-300"
              }`}
            >
              <Clock size={12} />
              {isOverdue ? t("assignments.exercise.overduePrefix") : t("assignments.exercise.duePrefix")}
              {first.dueAt ? formatDateTimeHm(first.dueAt, i18n.language) : t("assignments.exercise.noDeadline")}
            </span>
          )}
          <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted border border-line text-[11px] font-black flex items-center gap-1">
            <GraduationCap size={12} />{" "}
            {first.teacherType === "VIETNAMESE" ? t("assignments.exercise.teacherVietnamese") : t("assignments.exercise.teacherForeign")}
          </span>
          {first.sessionDate && (
            <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted border border-line text-[11px] font-black flex items-center gap-1">
              <CalendarDays size={12} /> {t("assignments.exercise.assignedInSession", { date: formatDate(first.sessionDate, i18n.language) })}
            </span>
          )}
        </div>

        <h3 className="text-base font-black text-ink font-display truncate">{groupTitle}</h3>

        {!noneStarted && (
          <div
            className={`inline-flex flex-wrap items-center gap-1.5 px-3 py-1.5 rounded-xl border text-xs font-bold ${
              passed === true
                ? "bg-emerald-50 border-emerald-200 text-emerald-800"
                : passed === false
                  ? "bg-rose-50 border-rose-200 text-rose-800"
                  : "bg-slate-50 border-slate-200 text-slate-700"
            }`}
          >
            <span>
              {t("assignments.exercise.scoreLabel")}
              <span className="font-black">{totalScore}</span>
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
        <button
          onClick={onOpen}
          className={`w-full md:w-auto flex items-center justify-center gap-1.5 px-5 py-2.5 font-extrabold text-xs rounded-xl shadow-sm transition-all cursor-pointer ${
            allFullyGraded && !anyRetake ? "bg-slate-100 hover:bg-slate-200 text-ink border border-line" : "bg-teal hover:bg-teal-deep text-white"
          }`}
        >
          {actionLabel} <ChevronRight size={14} />
        </button>
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
      <span className="px-2.5 py-0.5 rounded-lg bg-sky text-teal-deep text-[11px] font-black flex items-center gap-1">
        <Play size={12} /> {t("assignments.video.watchToReview")}
      </span>
    ) : fullyAnswered ? (
      <span className="px-2.5 py-0.5 rounded-lg bg-teal/10 text-teal-deep text-[11px] font-black flex items-center gap-1">
        <CheckCircle2 size={12} />{" "}
        {t("assignments.video.achievedViews", { count: item.connectionStats.viewCount, required: item.connectionStats.requiredViewCount })}
      </span>
    ) : (
      <span className="px-2.5 py-0.5 rounded-lg bg-amber-100 text-amber-800 border border-amber-300 text-[11px] font-black flex items-center gap-1">
        <Clock size={12} />{" "}
        {t("assignments.video.achievedViews", { count: item.connectionStats.viewCount, required: item.connectionStats.requiredViewCount })}
      </span>
    );
  } else if (!answerable) {
    statusBadge = (
      <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted text-[11px] font-black flex items-center gap-1">
        <Clock size={12} /> {t("assignments.video.noQuestions")}
      </span>
    );
  } else if (fullyAnswered) {
    statusBadge = (
      <span className="px-2.5 py-0.5 rounded-lg bg-teal/10 text-teal-deep text-[11px] font-black flex items-center gap-1">
        <CheckCircle2 size={12} /> {t("assignments.video.submitted")}
      </span>
    );
  } else {
    statusBadge = (
      <span className="px-2.5 py-0.5 rounded-lg bg-amber-100 text-amber-800 border border-amber-300 text-[11px] font-black flex items-center gap-1">
        <Clock size={12} /> {t("assignments.video.answeredCount", { answered: reflexStats!.answeredQuestions, total: reflexStats!.totalQuestions })}
      </span>
    );
  }

  const pending = answerable && !fullyAnswered;
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
        <div className="flex flex-wrap items-center gap-2">
          <span className="px-2.5 py-0.5 rounded-lg bg-teal/10 text-teal border border-teal/20 text-[11px] font-black flex items-center gap-1">
            {isConnection ? <Link2 size={12} /> : <MessageCircle size={12} />}{" "}
            {isConnection ? t("assignments.video.connectionType") : t("assignments.video.reflexType")}
          </span>
          <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted text-[11px] font-bold truncate max-w-[200px]">{setTitle}</span>
          {statusBadge}
          {dueAt && (
            <span
              className={`px-2.5 py-0.5 rounded-lg border text-[11px] font-black flex items-center gap-1 ${
                isOverdue ? "bg-coral/10 text-coral border-coral/20" : "bg-amber-100 text-amber-800 border-amber-300"
              }`}
            >
              <Clock size={12} />
              {isOverdue ? t("assignments.exercise.overduePrefix") : t("assignments.exercise.duePrefix")}
              {formatDateTimeHm(dueAt, i18n.language)}
            </span>
          )}
          {/* V123, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-14 — GV Việt Nam/nước
              ngoài phụ trách (đọc thật từ ReviewVideoSet.teacherType) + buổi đã giao BTVN này, hiện
              cho MỌI trạng thái theo yêu cầu người dùng. teacherType luôn có (thuộc tính của Bộ, kể
              cả video chỉ nằm trong Kho chưa được giao) — chỉ sessionDate cần bản giao thật. */}
          <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted border border-line text-[11px] font-black flex items-center gap-1">
            <GraduationCap size={12} />{" "}
            {item.teacherType === "VIETNAMESE" ? t("assignments.exercise.teacherVietnamese") : t("assignments.exercise.teacherForeign")}
          </span>
          {item.sessionDate && (
            <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted border border-line text-[11px] font-black flex items-center gap-1">
              <CalendarDays size={12} /> {t("assignments.exercise.assignedInSession", { date: formatDate(item.sessionDate, i18n.language) })}
            </span>
          )}
        </div>
        <h3 className="text-base font-black text-ink font-display truncate">{video.title}</h3>
      </div>

      <div className="shrink-0">
        <button
          onClick={onOpen}
          className={`w-full md:w-auto flex items-center justify-center gap-1.5 px-5 py-2.5 font-extrabold text-xs rounded-xl shadow-sm transition-all cursor-pointer ${
            fullyAnswered ? "bg-slate-100 hover:bg-slate-200 text-ink border border-line" : "bg-teal hover:bg-teal-deep text-white"
          }`}
        >
          {actionLabel} <ChevronRight size={14} />
        </button>
      </div>
    </div>
  );
}
