import React, { useEffect, useState } from "react";
import { Bell, CheckCircle2, ChevronRight, Clock, Link2, MessageCircle, Play } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import {
  AssignedExerciseResponse,
  ReviewVideoResponse,
  ReviewVideoSubmissionResponse,
  getMyReviewVideoSubmission,
  listMyAssignedExercises,
  listReviewVideoSetsByClass,
  listReviewVideos
} from "../api";
import TakeExerciseModal from "./TakeExerciseModal";
import ReviewVideoTaskModal from "./ReviewVideoTaskModal";

interface AssignmentsTabProps {
  classId: number;
}

const attemptStatusLabels: Record<string, { label: string; className: string }> = {
  IN_PROGRESS: { label: "Đang làm dở", className: "bg-gold/10 text-gold" },
  AUTO_GRADED: { label: "Đã nộp — chờ chấm tự luận/nói", className: "bg-sky text-teal-deep" },
  FULLY_GRADED: { label: "Đã có điểm", className: "bg-teal/10 text-teal-deep" }
};

function isExercisePending(item: AssignedExerciseResponse): boolean {
  return item.myLatestAttemptStatus == null || item.myLatestAttemptStatus === "IN_PROGRESS";
}

type FilterStatus = "ALL" | "PENDING" | "GRADED";

interface ReviewVideoHomeworkItem {
  video: ReviewVideoResponse;
  videoType: "CONNECTION" | "REFLEX";
  setTitle: string;
  submission?: ReviewVideoSubmissionResponse;
}

/**
 * UC-40 (bài tập ngữ pháp, Giáo viên Việt Nam giao) + UC-23a/UC-23b (Video từ kết nối/phản xạ,
 * Giáo viên nước ngoài giao) — gộp chung vào 1 tab "Bài tập về nhà (BTVN)" ở sidebar Portal, theo
 * đúng thực tế nghiệp vụ 2 nhóm giáo viên giao 2 loại bài khác nhau nhưng học sinh cần thấy chung 1
 * danh sách để không bỏ sót (đã xác nhận với người dùng 2026-07-27) — "Kho Video Ôn tập" trước đây
 * nằm trong "E-Learning & LMS" đã CHUYỂN HẲN sang đây, không hiển thị lại ở LMS nữa.
 *
 * Video từ kết nối (CONNECTION): "đạt" = xem ≥ 80% (ReviewVideoService.COMPLETION_THRESHOLD), theo
 * dõi qua PUT progress. Video phản xạ (REFLEX): "đạt" = đã nộp audio trả lời (UC-23b), giáo viên
 * chấm điểm sau. LƯU Ý: backend chưa có API đọc lại tiến độ xem đã lưu cho CONNECTION (chỉ có PUT
 * report, không có GET) nên KHÔNG thể hiện đúng trạng thái "đã xem xong" trong danh sách này — các
 * mục CONNECTION vì vậy không tính vào bộ đếm "Cần hoàn thành"/"Đã chấm", chỉ hiện ở tab "Tất cả".
 */
export default function AssignmentsTab({ classId }: AssignmentsTabProps) {
  const [exercises, setExercises] = useState<AssignedExerciseResponse[]>([]);
  const [reviewItems, setReviewItems] = useState<ReviewVideoHomeworkItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filterStatus, setFilterStatus] = useState<FilterStatus>("ALL");
  const [takingExercise, setTakingExercise] = useState<AssignedExerciseResponse | null>(null);
  const [openReviewItem, setOpenReviewItem] = useState<ReviewVideoHomeworkItem | null>(null);

  const load = () => {
    setLoading(true);
    setError(null);
    Promise.all([
      listMyAssignedExercises(classId),
      listReviewVideoSetsByClass(classId).then(async (sets) => {
        const perSet = await Promise.all(
          sets.map(async (set) => {
            const videos = await listReviewVideos(set.id);
            return videos.map((video) => ({ video, videoType: set.videoType, setTitle: set.title }) as ReviewVideoHomeworkItem);
          })
        );
        const flat = perSet.flat();
        const reflexItems = flat.filter((x) => x.videoType === "REFLEX");
        const submissions = await Promise.all(reflexItems.map((x) => getMyReviewVideoSubmission(x.video.id).catch(() => undefined)));
        const submissionByVideoId = new Map(reflexItems.map((x, i) => [x.video.id, submissions[i]]));
        return flat.map((x) => ({ ...x, submission: submissionByVideoId.get(x.video.id) }));
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

  const pendingCount = exercises.filter(isExercisePending).length + reviewItems.filter((x) => x.videoType === "REFLEX" && !x.submission).length;
  const gradedCount = exercises.filter((e) => !isExercisePending(e)).length + reviewItems.filter((x) => x.videoType === "REFLEX" && x.submission).length;

  const filteredExercises = exercises.filter((e) => {
    if (filterStatus === "PENDING") return isExercisePending(e);
    if (filterStatus === "GRADED") return !isExercisePending(e);
    return true;
  });
  const filteredReviewItems = reviewItems.filter((x) => {
    if (filterStatus === "ALL") return true;
    if (x.videoType === "CONNECTION") return false; // chưa có API đọc lại tiến độ — không tham gia lọc Pending/Graded
    if (filterStatus === "PENDING") return !x.submission;
    return !!x.submission;
  });

  return (
    <div className="space-y-6">
      {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

      {pendingCount > 0 ? (
        <div className="p-5 bg-gradient-to-r from-orange-500 via-amber-500 to-coral rounded-2xl text-white shadow-md flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="w-12 h-12 rounded-xl bg-white/20 backdrop-blur-md flex items-center justify-center shrink-0">
              <Bell size={24} className="text-white" />
            </div>
            <div>
              <span className="px-2.5 py-0.5 rounded-full bg-white/25 text-[10px] font-black uppercase tracking-wider">Thông Báo BTVN</span>
              <h3 className="text-base md:text-lg font-black font-display mt-0.5">
                Bạn có <span className="underline decoration-wavy underline-offset-4">{pendingCount} bài tập về nhà</span> chưa hoàn thành!
              </h3>
              <p className="text-xs text-white/90 font-semibold mt-0.5">Hãy làm bài sớm trước hạn nộp để duy trì kết quả học tập tốt nhé.</p>
            </div>
          </div>
          <button
            onClick={() => setFilterStatus("PENDING")}
            className="px-5 py-2.5 bg-white text-orange-600 hover:bg-slate-100 font-extrabold text-xs rounded-xl shadow-sm transition-all shrink-0 cursor-pointer"
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

      <div className="flex flex-wrap items-center gap-2 border-b border-line pb-3">
        <button
          onClick={() => setFilterStatus("ALL")}
          className={`px-4 py-2 rounded-xl text-xs font-black transition-all cursor-pointer ${
            filterStatus === "ALL" ? "bg-teal text-white shadow-sm" : "bg-slate-100 hover:bg-slate-200 text-muted"
          }`}
        >
          Tất cả bài tập ({exercises.length + reviewItems.length})
        </button>
        <button
          onClick={() => setFilterStatus("PENDING")}
          className={`px-4 py-2 rounded-xl text-xs font-black transition-all cursor-pointer flex items-center gap-1.5 ${
            filterStatus === "PENDING" ? "bg-orange-500 text-white shadow-sm" : "bg-slate-100 hover:bg-slate-200 text-muted"
          }`}
        >
          <Clock size={14} /> Cần hoàn thành ({pendingCount})
        </button>
        <button
          onClick={() => setFilterStatus("GRADED")}
          className={`px-4 py-2 rounded-xl text-xs font-black transition-all cursor-pointer flex items-center gap-1.5 ${
            filterStatus === "GRADED" ? "bg-teal text-white shadow-sm" : "bg-slate-100 hover:bg-slate-200 text-muted"
          }`}
        >
          <CheckCircle2 size={14} /> Đã nộp &amp; Đã chấm ({gradedCount})
        </button>
      </div>

      {filteredExercises.length === 0 && filteredReviewItems.length === 0 ? (
        <p className="text-xs text-muted font-bold italic text-center py-10">Không có bài tập nào trong mục này.</p>
      ) : (
        <div className="space-y-4">
          {filteredExercises.map((item) => (
            <ExerciseCard key={`ex-${item.assignmentId}`} item={item} onOpen={() => setTakingExercise(item)} />
          ))}
          {filteredReviewItems.map((item) => (
            <ReviewVideoCard key={`rv-${item.video.id}`} item={item} onOpen={() => setOpenReviewItem(item)} />
          ))}
        </div>
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
          onFinished={load}
        />
      )}

      {openReviewItem && (
        <ReviewVideoTaskModal
          video={openReviewItem.video}
          videoType={openReviewItem.videoType}
          onClose={() => setOpenReviewItem(null)}
          onSubmitted={load}
        />
      )}
    </div>
  );
}

function ExerciseCard({ item, onOpen }: { item: AssignedExerciseResponse; onOpen: () => void }) {
  const isOverdue = item.dueAt != null && new Date(item.dueAt) < new Date();
  const attemptMeta = item.myLatestAttemptStatus ? attemptStatusLabels[item.myLatestAttemptStatus] : null;
  const isFullyGraded = item.myLatestAttemptStatus === "FULLY_GRADED";
  const pending = isExercisePending(item);

  const actionLabel =
    item.myLatestAttemptStatus == null
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
          {attemptMeta ? (
            <span className={`px-2.5 py-0.5 rounded-lg text-[11px] font-black flex items-center gap-1 ${attemptMeta.className}`}>
              <CheckCircle2 size={12} /> {attemptMeta.label}
            </span>
          ) : (
            <span
              className={`px-2.5 py-0.5 rounded-lg border text-[11px] font-black flex items-center gap-1 ${
                isOverdue ? "bg-coral/10 text-coral border-coral/20" : "bg-amber-100 text-amber-800 border-amber-300"
              }`}
            >
              <Clock size={12} /> {isOverdue ? "Đã quá hạn — chưa làm" : `Hạn nộp: ${item.dueAt ? new Date(item.dueAt).toLocaleString("vi-VN") : "Không giới hạn"}`}
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
            isFullyGraded ? "bg-slate-100 hover:bg-slate-200 text-ink border border-line" : "bg-teal hover:bg-teal-deep text-white"
          }`}
        >
          {actionLabel} <ChevronRight size={14} />
        </button>
      </div>
    </div>
  );
}

function ReviewVideoCard({ item, onOpen }: { item: ReviewVideoHomeworkItem; onOpen: () => void }) {
  const { video, videoType, setTitle, submission } = item;
  const isConnection = videoType === "CONNECTION";
  const graded = submission?.score != null;

  let statusBadge: React.ReactNode;
  if (isConnection) {
    statusBadge = (
      <span className="px-2.5 py-0.5 rounded-lg bg-sky text-teal-deep text-[11px] font-black flex items-center gap-1">
        <Play size={12} /> Xem để ôn tập
      </span>
    );
  } else if (graded) {
    statusBadge = (
      <span className="px-2.5 py-0.5 rounded-lg bg-teal/10 text-teal-deep text-[11px] font-black flex items-center gap-1">
        <CheckCircle2 size={12} /> Điểm: {submission!.score}/{submission!.maxScore}
      </span>
    );
  } else if (submission) {
    statusBadge = (
      <span className="px-2.5 py-0.5 rounded-lg bg-sky text-teal-deep text-[11px] font-black flex items-center gap-1">
        <CheckCircle2 size={12} /> Đã nộp — chờ chấm
      </span>
    );
  } else {
    statusBadge = (
      <span className="px-2.5 py-0.5 rounded-lg bg-amber-100 text-amber-800 border border-amber-300 text-[11px] font-black flex items-center gap-1">
        <Clock size={12} /> Chưa nộp bài
      </span>
    );
  }

  const pending = !isConnection && !submission;
  const actionLabel = isConnection ? "Xem video" : submission ? "Xem bài đã nộp" : "Ghi âm trả lời";

  return (
    <div className={`p-5 bg-white border rounded-2xl transition-all shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4 ${pending ? "border-orange-200 bg-orange-50/20" : "border-line/80"}`}>
      <div className="space-y-2 flex-1 min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <span className="px-2.5 py-0.5 rounded-lg bg-teal/10 text-teal border border-teal/20 text-[11px] font-black flex items-center gap-1">
            {isConnection ? <Link2 size={12} /> : <MessageCircle size={12} />} {isConnection ? "Video từ kết nối" : "Video phản xạ"}
          </span>
          <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 text-muted text-[11px] font-bold truncate max-w-[200px]">{setTitle}</span>
          {statusBadge}
        </div>
        <h3 className="text-base font-black text-ink font-display truncate">{video.title}</h3>
      </div>

      <div className="shrink-0">
        <button
          onClick={onOpen}
          className={`w-full md:w-auto flex items-center justify-center gap-1.5 px-5 py-2.5 font-extrabold text-xs rounded-xl shadow-sm transition-all cursor-pointer ${
            graded ? "bg-slate-100 hover:bg-slate-200 text-ink border border-line" : "bg-teal hover:bg-teal-deep text-white"
          }`}
        >
          {actionLabel} <ChevronRight size={14} />
        </button>
      </div>
    </div>
  );
}
