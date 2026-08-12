import React, { useEffect, useMemo, useState } from "react";
import { BookOpen } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { ClassResponse } from "@/features/academic/api";
import {
  ReviewVideoQuestionResponse,
  ReviewVideoResponse,
  ReviewVideoSetResponse,
  ReviewVideoSubmissionResponse,
  listReviewVideoQuestions,
  listReviewVideoSetAssignedClasses,
  listReviewVideoSets,
  listReviewVideoSubmissionsForGrading,
  listReviewVideos
} from "../api";
import ReviewVideoGradingPanel from "../components/ReviewVideoGradingPanel";
import Badge from "@/components/ui/Badge";
import Select from "@/components/ui/Select";
import Toast from "@/components/ui/Toast";
import { useToast } from "@/lib/useToast";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

interface QuestionInfo {
  prompt: string | null;
  videoTitle: string;
  videoDisplayOrder: number;
  timestampSeconds: number;
}

/**
 * UC-23b Main Flow bước 3-4: giáo viên chọn 1 Bộ Video phản xạ + 1 lớp đã gán bộ đó, nghe và chấm điểm
 * từng bài audio học sinh đã nộp (attempt mới nhất mỗi câu hỏi/học sinh). Chọn Bộ→Lớp mirror đúng pattern
 * đã có ở StatsModal (LecturesPage.tsx) — dùng lại listReviewVideoSets/listReviewVideoSetAssignedClasses.
 */
export default function ExamsPage() {
  const { message: toastMessage, showToast } = useToast();

  const [sets, setSets] = useState<ReviewVideoSetResponse[]>([]);
  const [loadingSets, setLoadingSets] = useState(true);
  const [setId, setSetId] = useState<number | null>(null);

  const [assignedClasses, setAssignedClasses] = useState<ClassResponse[]>([]);
  const [classId, setClassId] = useState<number | null>(null);

  const [submissions, setSubmissions] = useState<ReviewVideoSubmissionResponse[]>([]);
  const [questionInfoById, setQuestionInfoById] = useState<Record<number, QuestionInfo>>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedStudentId, setSelectedStudentId] = useState<number | null>(null);

  const reflexSets = useMemo(() => sets.filter((s) => s.videoType === "REFLEX"), [sets]);

  useEffect(() => {
    setLoadingSets(true);
    listReviewVideoSets()
      .then(setSets)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách Bộ Video phản xạ."))
      .finally(() => setLoadingSets(false));
  }, []);

  useEffect(() => {
    if (!setId) {
      setAssignedClasses([]);
      setClassId(null);
      return;
    }
    listReviewVideoSetAssignedClasses(setId)
      .then((cls) => {
        setAssignedClasses(cls);
        setClassId(cls[0]?.id ?? null);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách lớp đã gán."));
  }, [setId]);

  useEffect(() => {
    if (!setId || !classId) return;
    setLoading(true);
    setError(null);
    setSelectedStudentId(null);
    Promise.all([
      listReviewVideoSubmissionsForGrading(setId, classId),
      listReviewVideos(setId).then((videos) =>
        Promise.all(
          videos.map((video) =>
            listReviewVideoQuestions(video.id).then((questions) => ({ video, questions }))
          )
        )
      )
    ])
      .then(([submissionRes, videoQuestionGroups]) => {
        setSubmissions(submissionRes);
        const map: Record<number, QuestionInfo> = {};
        videoQuestionGroups.forEach(({ video, questions }: { video: ReviewVideoResponse; questions: ReviewVideoQuestionResponse[] }) => {
          questions.forEach((q) => {
            map[q.id] = { prompt: q.prompt, videoTitle: video.title, videoDisplayOrder: video.displayOrder, timestampSeconds: q.timestampSeconds };
          });
        });
        setQuestionInfoById(map);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách bài nộp."))
      .finally(() => setLoading(false));
  }, [setId, classId]);

  interface StudentGroup {
    studentId: number;
    studentFullName: string;
    submissions: ReviewVideoSubmissionResponse[];
  }

  /** Gom theo học sinh — mỗi học sinh 1 dòng bên trái, chọn 1 học sinh sẽ hiện TOÀN BỘ câu đã nộp của
      học sinh đó bên phải (thay vì chọn từng bài audio 1 như trước). Sắp theo video → mốc thời gian câu hỏi. */
  const studentGroups = useMemo<StudentGroup[]>(() => {
    const byStudent = new Map<number, StudentGroup>();
    for (const s of submissions) {
      let group = byStudent.get(s.studentId);
      if (!group) {
        group = { studentId: s.studentId, studentFullName: s.studentFullName, submissions: [] };
        byStudent.set(s.studentId, group);
      }
      group.submissions.push(s);
    }
    const sortKey = (s: ReviewVideoSubmissionResponse) => {
      const info = questionInfoById[s.reviewVideoQuestionId];
      return info ? info.videoDisplayOrder * 100000 + info.timestampSeconds : 0;
    };
    const groups = Array.from(byStudent.values());
    groups.forEach((g) => g.submissions.sort((a, b) => sortKey(a) - sortKey(b)));
    groups.sort((a, b) => a.studentFullName.localeCompare(b.studentFullName, "vi"));
    return groups;
  }, [submissions, questionInfoById]);

  const selectedGroup = studentGroups.find((g) => g.studentId === selectedStudentId) ?? null;

  const handleSaved = (updated: ReviewVideoSubmissionResponse) => {
    setSubmissions((prev) => prev.map((s) => (s.id === updated.id ? updated : s)));
    showToast("Đã lưu kết quả chấm!");
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Tài Liệu & Khảo Thí LMS (E-Learning)</h1>
        <p className="text-xs text-slate-500 mt-1">Chấm bài Video phản xạ (UC-23b) — nghe audio học sinh trả lời, cho điểm và nhận xét.</p>
        <p className="text-[10px] text-slate-400 mt-1 italic">
          Chấm câu tự luận/nói của Bài tập (Exercise) do giáo viên Việt Nam giao (UC-24/27) chưa triển khai ở đây — sẽ bổ sung riêng.
        </p>
      </div>

      <div className="flex flex-wrap gap-4">
        <div className="w-72">
          <label className={labelClass}>Bộ Video phản xạ *</label>
          <Select
            value={setId ?? ""}
            onChange={(e) => setSetId(e.target.value ? Number(e.target.value) : null)}
            className={inputClass}
            disabled={loadingSets}
          >
            <option value="">-- Chọn bộ --</option>
            {reflexSets.map((s) => (
              <option key={s.id} value={s.id}>
                {s.code} — {s.title}
              </option>
            ))}
          </Select>
        </div>

        {setId && (
          <div className="w-64">
            <label className={labelClass}>Lớp *</label>
            {assignedClasses.length === 0 ? (
              <p className="text-xs text-slate-400 italic pt-2">Bộ này chưa được gán cho lớp nào.</p>
            ) : (
              <Select value={classId ?? ""} onChange={(e) => setClassId(e.target.value ? Number(e.target.value) : null)} className={inputClass}>
                <option value="">-- Chọn lớp --</option>
                {assignedClasses.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.classCode} — {c.name}
                  </option>
                ))}
              </Select>
            )}
          </div>
        )}
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {!setId ? (
        <p className="text-xs text-slate-400 italic">Chọn 1 Bộ Video phản xạ để bắt đầu chấm bài.</p>
      ) : loading ? (
        <p className="text-xs text-slate-500">Đang tải hàng chờ chấm bài...</p>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
          <div className="space-y-2 max-h-[36rem] overflow-y-auto pr-1">
            {studentGroups.length === 0 ? (
              <div className="h-32 border border-dashed rounded-xl flex items-center justify-center text-slate-400 text-xs italic">
                <BookOpen className="w-4 h-4 mr-1.5" /> Chưa có bài nộp nào cho lớp này.
              </div>
            ) : (
              studentGroups.map((g) => {
                const gradedCount = g.submissions.filter((s) => s.score != null).length;
                const allGraded = gradedCount === g.submissions.length;
                return (
                  <button
                    key={g.studentId}
                    type="button"
                    onClick={() => setSelectedStudentId(g.studentId)}
                    className={`w-full text-left p-3 rounded-lg border transition-colors ${
                      selectedStudentId === g.studentId ? "border-brand-orange bg-orange-50" : "border-slate-200 bg-white hover:border-slate-300"
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-xs font-bold text-slate-800">{g.studentFullName}</span>
                      <Badge variant={allGraded ? "success" : "warning"}>
                        {gradedCount}/{g.submissions.length} đã chấm
                      </Badge>
                    </div>
                  </button>
                );
              })
            )}
          </div>

          <div className="space-y-4 max-h-[36rem] overflow-y-auto pr-1">
            {!selectedGroup ? (
              <div className="h-64 border border-dashed rounded-xl flex flex-col items-center justify-center text-slate-400 text-xs italic gap-1.5 text-center p-4">
                <BookOpen className="w-6 h-6 text-slate-300" />
                <span>Chọn 1 học sinh trong danh sách bên cạnh để nghe và chấm từng câu.</span>
              </div>
            ) : (
              <>
                <h3 className="text-xs font-bold text-slate-800">{selectedGroup.studentFullName} — {selectedGroup.submissions.length} câu đã nộp</h3>
                {selectedGroup.submissions.map((s) => {
                  const info = questionInfoById[s.reviewVideoQuestionId];
                  return (
                    <div key={s.id} className="border border-slate-200 rounded-xl p-3.5">
                      <ReviewVideoGradingPanel
                        submission={s}
                        questionPrompt={info?.prompt ?? null}
                        videoTitle={info?.videoTitle ?? null}
                        onSaved={handleSaved}
                      />
                    </div>
                  );
                })}
              </>
            )}
          </div>
        </div>
      )}

      <Toast message={toastMessage} />
    </div>
  );
}
