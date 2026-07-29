import React, { useEffect, useState } from "react";
import { BookOpen, CheckCircle2, Clock, Video } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { HomeworkProgressResponse, listHomeworkProgress } from "../api";

interface ParentHomeworkProgressTabProps {
  studentId: number;
  classId: number;
}

/** UC-64 (2026-07-29) — Cổng phụ huynh xem tiến độ BTVN đã giao cho con (chỉ xem, không phải giao diện làm bài — con tự làm ở Portal Học sinh). */
export default function ParentHomeworkProgressTab({ studentId, classId }: ParentHomeworkProgressTabProps) {
  const [items, setItems] = useState<HomeworkProgressResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    listHomeworkProgress(studentId, classId)
      .then(setItems)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được tiến độ bài tập về nhà."))
      .finally(() => setLoading(false));
  }, [studentId, classId]);

  if (loading) return <p className="text-sm text-muted font-bold">Đang tải...</p>;

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-lg font-black text-ink font-display">Tiến độ Bài tập về nhà</h2>
        <p className="text-xs text-muted font-bold mt-0.5">Theo dõi bài tập giáo viên đã giao cho con theo từng buổi học — chỉ xem, không thao tác làm bài ở đây.</p>
      </div>

      {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

      {items.length === 0 ? (
        <p className="text-xs text-muted font-bold italic text-center py-10">Chưa có bài tập nào được giao.</p>
      ) : (
        <div className="space-y-3">
          {items.map((item) => (
            <div key={item.commentId} className="bg-white border border-line/80 rounded-2xl p-4 space-y-3">
              <div className="flex items-center gap-1.5 text-[10px] font-extrabold text-muted uppercase">
                <Clock size={11} aria-hidden="true" /> {item.commentDate}
              </div>

              {(item.grammarTitle || item.grammarOfflineText) && (
                <div className="flex items-start gap-2.5 p-3 bg-sky-2 rounded-xl border border-teal/20">
                  <BookOpen size={16} className="text-teal-deep shrink-0 mt-0.5" aria-hidden="true" />
                  <div className="min-w-0">
                    <p className="text-[10px] font-extrabold text-teal-deep uppercase">Ngữ pháp</p>
                    <p className="text-xs font-bold text-ink truncate">{item.grammarTitle ?? item.grammarOfflineText}</p>
                    {item.grammarProgress && (
                      <p className="text-[11px] font-bold text-emerald-700 mt-0.5 flex items-center gap-1">
                        <CheckCircle2 size={11} aria-hidden="true" /> {item.grammarProgress}
                      </p>
                    )}
                  </div>
                </div>
              )}

              {item.videoTitle && (
                <div className="flex items-start gap-2.5 p-3 bg-amber-50/60 rounded-xl border border-amber-200">
                  <Video size={16} className="text-amber-700 shrink-0 mt-0.5" aria-hidden="true" />
                  <div className="min-w-0">
                    <p className="text-[10px] font-extrabold text-amber-800 uppercase">Video ôn tập</p>
                    <p className="text-xs font-bold text-ink truncate">{item.videoTitle}</p>
                    {item.videoProgress && (
                      <p className="text-[11px] font-bold text-emerald-700 mt-0.5 flex items-center gap-1">
                        <CheckCircle2 size={11} aria-hidden="true" /> {item.videoProgress}
                      </p>
                    )}
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
