import React, { useEffect, useRef, useState } from "react";
import { Bell, BookOpen, Check, CheckCircle2, ChevronRight, Clock, Filter, Link2, MessageCircle, Play, Video } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { formatDateTimeHm } from "@/lib/format";
import {
  AssignedExerciseResponse,
  ReviewVideoResponse,
  getMyLatestReviewVideoSubmission,
  getReviewVideoProgress,
  listMyAssignedExercises,
  listMyReviewVideoAssignments,
  listReviewVideoQuestions,
  listReviewVideoSetsByClass,
  listReviewVideos
} from "../api";
import TakeExerciseModal from "./TakeExerciseModal";
import ReviewVideoTaskModal from "./ReviewVideoTaskModal";
import Pagination from "@/components/ui/Pagination";

const PAGE_SIZE = 10;

interface AssignmentsTabProps {
  classId: number;
}

const attemptStatusLabels: Record<string, { label: string; className: string }> = {
  IN_PROGRESS: { label: "Đang làm dở", className: "bg-gold/10 text-gold" },
  AUTO_GRADED: { label: "Đã nộp — chờ chấm tự luận/nói", className: "bg-sky text-teal-deep" },
  FULLY_GRADED: { label: "Đã có điểm", className: "bg-teal/10 text-teal-deep" }
};

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
export default function AssignmentsTab({ classId }: AssignmentsTabProps) {
  const [exercises, setExercises] = useState<AssignedExerciseResponse[]>([]);
  const [reviewItems, setReviewItems] = useState<ReviewVideoHomeworkItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filterStatus, setFilterStatus] = useState<FilterStatus>("ALL");
  const [filterType, setFilterType] = useState<FilterType>("ALL");
  const [takingExercise, setTakingExercise] = useState<AssignedExerciseResponse | null>(null);
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
        const dueAtBySetId = new Map(assignments.map((a) => [a.reviewVideoSetId, a.dueAt]));
        const perSet = await Promise.all(
          sets.map(async (set) => {
            const videos = await listReviewVideos(set.id);
            return videos.map(
              (video) => ({ video, videoType: set.videoType, setTitle: set.title, dueAt: dueAtBySetId.get(set.id) }) as ReviewVideoHomeworkItem
            );
          })
        );
        const flat = perSet.flat();
        const reflexItems = flat.filter((x) => x.videoType === "REFLEX");
        const reflexStatsList = await Promise.all(
          reflexItems.map(async (x) => {
            const questions = await listReviewVideoQuestions(x.video.id).catch(() => []);
            const submissions = await Promise.all(questions.map((q) => getMyLatestReviewVideoSubmission(q.id).catch(() => undefined)));
            return {
              totalQuestions: questions.length,
              answeredQuestions: submissions.filter((s) => s != null).length
            };
          })
        );
        const statsByVideoId = new Map(reflexItems.map((x, i) => [x.video.id, reflexStatsList[i]]));

        // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — CONNECTION giờ đọc lại tiến
        // độ đã lưu qua GET progress mới, mirror đúng cách REFLEX đọc reflexStats ở trên.
        const connectionItems = flat.filter((x) => x.videoType === "CONNECTION");
        const connectionProgressList = await Promise.all(connectionItems.map((x) => getReviewVideoProgress(x.video.id).catch(() => undefined)));
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
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách bài tập."))
      .finally(() => setLoading(false));
  };

  useEffect(load, [classId]);

  if (loading) return <p className="text-sm text-muted font-bold">Đang tải...</p>;

  const pendingCount =
    exercises.filter(isExercisePending).length +
    reviewItems.filter((x) => isReflexAnswerable(x) && !isReflexFullyAnswered(x)).length +
    reviewItems.filter((x) => isConnectionAnswerable(x) && !isConnectionCompleted(x)).length;
  const gradedCount =
    exercises.filter((e) => !isExercisePending(e)).length +
    reviewItems.filter((x) => isReflexAnswerable(x) && isReflexFullyAnswered(x)).length +
    reviewItems.filter((x) => isConnectionAnswerable(x) && isConnectionCompleted(x)).length;

  const filteredExercises = exercises.filter((e) => {
    if (filterType === "VIDEO") return false;
    if (filterStatus === "PENDING") return isExercisePending(e);
    if (filterStatus === "GRADED") return !isExercisePending(e);
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
    ...filteredExercises.map((item) => ({ type: "exercise" as const, key: `ex-${item.assignmentId}`, item })),
    ...filteredReviewItems.map((item) => ({ type: "video" as const, key: `rv-${item.video.id}`, item }))
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
              <span className="px-2.5 py-0.5 rounded-full bg-amber-100 text-amber-700 text-[10px] font-black uppercase tracking-wider">Thông Báo BTVN</span>
              <h3 className="text-base md:text-lg font-black font-display mt-0.5 text-amber-900">
                Bạn có <span className="underline decoration-wavy underline-offset-4">{pendingCount} bài tập về nhà</span> chưa hoàn thành!
              </h3>
              <p className="text-xs text-amber-800/80 font-semibold mt-0.5">Hãy làm bài sớm trước hạn nộp để duy trì kết quả học tập tốt nhé.</p>
            </div>
          </div>
          <button
            onClick={() => setFilterStatus("PENDING")}
            className="px-5 py-2.5 bg-amber-500 hover:bg-amber-600 text-white font-extrabold text-xs rounded-xl shadow-sm transition-all shrink-0 cursor-pointer"
          >
            Làm bài ngay
          </button>
        </div>
      ) : (
        <div className="p-4 bg-emerald-50 border border-emerald-200 rounded-2xl text-emerald-800 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <CheckCircle2 size={20} className="text-emerald-600" />
            <span className="text-xs font-black">Tuyệt vời! Bạn đã hoàn thành tất cả bài tập về nhà được giao.</span>
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
              Tất cả bài tập ({exercises.length + reviewItems.length})
            </button>
            <button
              onClick={() => setFilterStatus("PENDING")}
              className={`shrink-0 snap-start px-4 py-2 rounded-xl text-xs font-black transition-all cursor-pointer flex items-center gap-1.5 ${
                filterStatus === "PENDING" ? "bg-orange-500 text-white shadow-sm" : "bg-slate-100 hover:bg-slate-200 text-muted"
              }`}
            >
              <Clock size={14} /> Cần hoàn thành ({pendingCount})
            </button>
            <button
              onClick={() => setFilterStatus("GRADED")}
              className={`shrink-0 snap-start px-4 py-2 rounded-xl text-xs font-black transition-all cursor-pointer flex items-center gap-1.5 ${
                filterStatus === "GRADED" ? "bg-teal text-white shadow-sm" : "bg-slate-100 hover:bg-slate-200 text-muted"
              }`}
            >
              <CheckCircle2 size={14} /> Đã nộp &amp; Đã chấm ({gradedCount})
            </button>
          </div>

          <div className="relative shrink-0" ref={filterTypeRef}>
            <button
              type="button"
              onClick={() => setFilterTypeOpen((v) => !v)}
              aria-label="Lọc theo loại bài"
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
                aria-label="Chọn loại bài"
                className="absolute right-0 top-full mt-2 z-30 w-56 bg-white border border-line rounded-2xl shadow-lg p-1.5 space-y-0.5"
              >
                {(
                  [
                    ["ALL", "Tất cả loại bài", null] as const,
                    ["EXERCISE", `Bài ngữ pháp (${exercises.length})`, BookOpen] as const,
                    ["VIDEO", `Video Kết nối - Phản xạ (${reviewItems.length})`, Video] as const
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
        <p className="text-xs text-muted font-bold italic text-center py-10">Không có bài tập nào trong mục này.</p>
      ) : (
        <>
          <div className="space-y-4">
            {pageItems.map((entry) =>
              entry.type === "exercise" ? (
                <ExerciseCard key={entry.key} item={entry.item} onOpen={() => setTakingExercise(entry.item)} />
              ) : (
                <ReviewVideoCard key={entry.key} item={entry.item} onOpen={() => setOpenReviewItem(entry.item)} />
              )
            )}
          </div>
          <Pagination page={page} pageSize={PAGE_SIZE} totalElements={feedItems.length} itemLabel="bài" onPageChange={setPage} />
        </>
      )}

      {takingExercise && (
        <TakeExerciseModal
          item={takingExercise}
          // Mở đề = BE đã tạo attempt ngay (started_at = NOW), kể cả khi đóng chưa nộp — luôn báo load()
          // để danh sách bên ngoài cập nhật đúng myLatestAttemptId/Status.
          onClose={() => {
            setTakingExercise(null);
            load();
          }}
          // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — trước đây chỉ load() lại
          // danh sách, KHÔNG đóng modal: sau khi bấm "Đã hiểu" ở popup kết quả, modal làm bài (đã
          // chuyển sang chỉ xem) vẫn còn hiện phía sau, trông như tự mở thêm 1 modal. Đóng luôn về
          // màn danh sách — học sinh vẫn xem lại được bình thường qua "Xem lại bài đã làm".
          onFinished={() => {
            setTakingExercise(null);
            load();
          }}
        />
      )}

      {openReviewItem && (
        <ReviewVideoTaskModal
          video={openReviewItem.video}
          videoType={openReviewItem.videoType}
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

function ExerciseCard({ item, onOpen }: { item: AssignedExerciseResponse; onOpen: () => void }) {
  const isOverdue = item.dueAt != null && new Date(item.dueAt) < new Date();
  const retake = needsRetake(item);
  const attemptMeta = retake ? null : item.myLatestAttemptStatus ? attemptStatusLabels[item.myLatestAttemptStatus] : null;
  const isFullyGraded = item.myLatestAttemptStatus === "FULLY_GRADED";
  const pending = isExercisePending(item);

  const actionLabel = retake
    ? "Làm lại bài"
    : item.myLatestAttemptStatus == null
      ? "Làm bài ngay"
      : item.myLatestAttemptStatus === "IN_PROGRESS"
        ? "Tiếp tục làm bài"
        : isFullyGraded
          ? "Xem lại bài đã làm"
          : "Xem lại bài đã nộp";

  return (
    <div
      className={`p-5 bg-white border rounded-2xl transition-all shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4 ${
        pending ? "border-orange-200 bg-orange-50/20" : "border-line/80"
      }`}
    >
      <div className="space-y-2 flex-1 min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <span className="px-2.5 py-0.5 rounded-lg bg-teal/10 text-teal border border-teal/20 text-[11px] font-black">{item.exerciseCode}</span>
          <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted text-[11px] font-bold">{item.className}</span>
          {retake ? (
            <span className="px-2.5 py-0.5 rounded-lg bg-coral/10 text-coral border border-coral/20 text-[11px] font-black flex items-center gap-1">
              <Clock size={12} /> Chưa đạt {item.myLatestPercentage != null ? `(${item.myLatestPercentage}%)` : ""} — cần làm lại
            </span>
          ) : attemptMeta ? (
            <span className={`px-2.5 py-0.5 rounded-lg text-[11px] font-black flex items-center gap-1 ${attemptMeta.className}`}>
              <CheckCircle2 size={12} /> {attemptMeta.label}
            </span>
          ) : (
            <span
              className={`px-2.5 py-0.5 rounded-lg border text-[11px] font-black flex items-center gap-1 ${
                isOverdue ? "bg-coral/10 text-coral border-coral/20" : "bg-amber-100 text-amber-800 border-amber-300"
              }`}
            >
              <Clock size={12} /> {isOverdue ? "Đã quá hạn — chưa làm" : `Hạn nộp: ${item.dueAt ? formatDateTimeHm(item.dueAt) : "Không giới hạn"}`}
            </span>
          )}
        </div>

        <h3 className="text-base font-black text-ink font-display truncate">{item.title}</h3>

        {item.myLatestTotalScore != null && (
          <p className="text-xs text-muted font-bold">
            Điểm: <span className="text-teal-deep font-black">{item.myLatestTotalScore}</span>
          </p>
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

function ReviewVideoCard({ item, onOpen }: { item: ReviewVideoHomeworkItem; onOpen: () => void }) {
  const { video, videoType, setTitle, reflexStats, dueAt } = item;
  const isConnection = videoType === "CONNECTION";
  const answerable = isConnection ? isConnectionAnswerable(item) : isReflexAnswerable(item);
  const fullyAnswered = isConnection ? isConnectionCompleted(item) : isReflexFullyAnswered(item);
  const isOverdue = dueAt != null && new Date(dueAt) < new Date();

  let statusBadge: React.ReactNode;
  if (isConnection) {
    statusBadge = !item.connectionStats ? (
      <span className="px-2.5 py-0.5 rounded-lg bg-sky text-teal-deep text-[11px] font-black flex items-center gap-1">
        <Play size={12} /> Xem để ôn tập
      </span>
    ) : fullyAnswered ? (
      <span className="px-2.5 py-0.5 rounded-lg bg-teal/10 text-teal-deep text-[11px] font-black flex items-center gap-1">
        <CheckCircle2 size={12} /> Đã đạt {item.connectionStats.viewCount}/{item.connectionStats.requiredViewCount} lượt
      </span>
    ) : (
      <span className="px-2.5 py-0.5 rounded-lg bg-amber-100 text-amber-800 border border-amber-300 text-[11px] font-black flex items-center gap-1">
        <Clock size={12} /> Đã đạt {item.connectionStats.viewCount}/{item.connectionStats.requiredViewCount} lượt
      </span>
    );
  } else if (!answerable) {
    statusBadge = (
      <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted text-[11px] font-black flex items-center gap-1">
        <Clock size={12} /> Chưa có câu hỏi
      </span>
    );
  } else if (fullyAnswered) {
    statusBadge = (
      <span className="px-2.5 py-0.5 rounded-lg bg-teal/10 text-teal-deep text-[11px] font-black flex items-center gap-1">
        <CheckCircle2 size={12} /> Đã nộp bài
      </span>
    );
  } else {
    statusBadge = (
      <span className="px-2.5 py-0.5 rounded-lg bg-amber-100 text-amber-800 border border-amber-300 text-[11px] font-black flex items-center gap-1">
        <Clock size={12} /> Đã nộp {reflexStats!.answeredQuestions}/{reflexStats!.totalQuestions} câu
      </span>
    );
  }

  const pending = answerable && !fullyAnswered;
  const actionLabel = isConnection ? "Xem video" : !answerable ? "Xem video" : fullyAnswered ? "Xem bài đã nộp" : "Trả lời câu hỏi";

  return (
    <div className={`p-5 bg-white border rounded-2xl transition-all shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4 ${pending ? "border-orange-200 bg-orange-50/20" : "border-line/80"}`}>
      <div className="space-y-2 flex-1 min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <span className="px-2.5 py-0.5 rounded-lg bg-teal/10 text-teal border border-teal/20 text-[11px] font-black flex items-center gap-1">
            {isConnection ? <Link2 size={12} /> : <MessageCircle size={12} />} {isConnection ? "Video từ kết nối" : "Video phản xạ"}
          </span>
          <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted text-[11px] font-bold truncate max-w-[200px]">{setTitle}</span>
          {statusBadge}
          {dueAt && (
            <span
              className={`px-2.5 py-0.5 rounded-lg border text-[11px] font-black flex items-center gap-1 ${
                isOverdue ? "bg-coral/10 text-coral border-coral/20" : "bg-amber-100 text-amber-800 border-amber-300"
              }`}
            >
              <Clock size={12} /> {isOverdue ? "Đã quá hạn nộp" : `Hạn nộp: ${formatDateTimeHm(dueAt)}`}
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
