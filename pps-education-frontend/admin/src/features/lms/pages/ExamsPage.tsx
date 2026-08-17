import React, { useEffect, useMemo, useState } from "react";
import { ArrowLeft, BookOpen, GraduationCap } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { ClassResponse } from "@/features/academic/api";
import {
  PendingGradingClassSummaryResponse,
  ReviewVideoQuestionResponse,
  ReviewVideoResponse,
  ReviewVideoSetResponse,
  ReviewVideoSubmissionResponse,
  listPendingGradingClasses,
  listReviewVideoQuestions,
  listReviewVideoSetAssignedClasses,
  listReviewVideoSets,
  listReviewVideoSubmissionsForClass,
  listReviewVideoSubmissionsForGrading,
  listReviewVideos
} from "../api";
import ReviewVideoGradingPanel from "../components/ReviewVideoGradingPanel";
import CountBadge from "@/components/ui/CountBadge";
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

interface StudentGroup {
  studentId: number;
  studentFullName: string;
  submissions: ReviewVideoSubmissionResponse[];
}

/** Gom theo học sinh — mỗi học sinh 1 dòng bên trái, chọn 1 học sinh sẽ hiện TOÀN BỘ câu đã nộp của học
    sinh đó bên phải. `sortKey` quyết định thứ tự câu hỏi bên trong 1 học sinh (theo video → mốc thời gian). */
function buildStudentGroups(submissions: ReviewVideoSubmissionResponse[], sortKey: (s: ReviewVideoSubmissionResponse) => number): StudentGroup[] {
  const byStudent = new Map<number, StudentGroup>();
  for (const s of submissions) {
    let group = byStudent.get(s.studentId);
    if (!group) {
      group = { studentId: s.studentId, studentFullName: s.studentFullName, submissions: [] };
      byStudent.set(s.studentId, group);
    }
    group.submissions.push(s);
  }
  const groups = Array.from(byStudent.values());
  groups.forEach((g) => g.submissions.sort((a, b) => sortKey(a) - sortKey(b)));
  groups.sort((a, b) => a.studentFullName.localeCompare(b.studentFullName, "vi"));
  return groups;
}

type ViewMode = "queue" | "legacy";

/**
 * UC-23b Main Flow bước 3-4: giáo viên chọn 1 Bộ Video phản xạ + 1 lớp đã gán bộ đó, nghe và chấm điểm
 * từng bài audio học sinh đã nộp (attempt mới nhất mỗi câu hỏi/học sinh).
 *
 * Bổ sung ngoài SDD gốc, xác nhận 2026-08-17 — trước đây bắt chọn Bộ→Lớp mới thấy bài chờ chấm, không
 * biết trước lớp nào đang tồn đọng. Nay mặc định vào thẳng "Hàng chờ chấm bài" (mode="queue"): landing
 * liệt kê các lớp giáo viên đang đứng lớp thật còn bài chưa chấm (kèm badge số bài), bấm vào 1 lớp xem
 * GỘP mọi Bộ REFLEX đã gán cho lớp đó trong 1 danh sách học sinh — không cần tự chọn lại từng Bộ. Đường
 * cũ (chọn Bộ→Lớp) vẫn giữ nguyên logic/API, chuyển thành tab phụ "Xem theo Bộ + Lớp" (mode="legacy") để
 * tra cứu lại lớp đã chấm hết (không còn ở landing/badge) hoặc lớp chung khung chương trình nhưng không
 * trực tiếp đứng lớp.
 */
export default function ExamsPage() {
  const { message: toastMessage, showToast } = useToast();
  const [mode, setMode] = useState<ViewMode>("queue");
  const [error, setError] = useState<string | null>(null);

  // ===================== Mặc định: Hàng chờ chấm (landing theo lớp + gộp nhiều Bộ) =====================
  const [landingClasses, setLandingClasses] = useState<PendingGradingClassSummaryResponse[]>([]);
  const [loadingLanding, setLoadingLanding] = useState(true);
  const [queueClass, setQueueClass] = useState<PendingGradingClassSummaryResponse | null>(null);
  const [queueSubmissions, setQueueSubmissions] = useState<ReviewVideoSubmissionResponse[]>([]);
  const [loadingQueueSubmissions, setLoadingQueueSubmissions] = useState(false);
  const [queueSelectedStudentId, setQueueSelectedStudentId] = useState<number | null>(null);

  const loadLanding = () => {
    setLoadingLanding(true);
    setError(null);
    listPendingGradingClasses()
      .then(setLandingClasses)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách lớp cần chấm."))
      .finally(() => setLoadingLanding(false));
  };

  useEffect(() => {
    if (mode === "queue") loadLanding();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode]);

  useEffect(() => {
    if (!queueClass) {
      setQueueSubmissions([]);
      return;
    }
    setLoadingQueueSubmissions(true);
    setError(null);
    setQueueSelectedStudentId(null);
    listReviewVideoSubmissionsForClass(queueClass.classId)
      .then(setQueueSubmissions)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách bài nộp."))
      .finally(() => setLoadingQueueSubmissions(false));
  }, [queueClass]);

  const queueStudentGroups = useMemo(
    () => buildStudentGroups(queueSubmissions, (s) => (s.reviewVideoDisplayOrder ?? 0) * 100000 + (s.timestampSeconds ?? 0)),
    [queueSubmissions]
  );
  const queueSelectedGroup = queueStudentGroups.find((g) => g.studentId === queueSelectedStudentId) ?? null;

  const handleQueueSaved = (updated: ReviewVideoSubmissionResponse) => {
    setQueueSubmissions((prev) => prev.map((s) => (s.id === updated.id ? updated : s)));
    showToast("Đã lưu kết quả chấm!");
  };

  const backToLanding = () => {
    setQueueClass(null);
    loadLanding();
  };

  // ===================== Tab phụ: Xem theo Bộ + Lớp (logic/API giữ nguyên như trước 2026-08-17) =====================
  const [sets, setSets] = useState<ReviewVideoSetResponse[]>([]);
  const [loadingSets, setLoadingSets] = useState(false);
  const [setId, setSetId] = useState<number | null>(null);

  const [assignedClasses, setAssignedClasses] = useState<ClassResponse[]>([]);
  const [classId, setClassId] = useState<number | null>(null);

  const [legacySubmissions, setLegacySubmissions] = useState<ReviewVideoSubmissionResponse[]>([]);
  const [questionInfoById, setQuestionInfoById] = useState<Record<number, QuestionInfo>>({});
  const [loadingLegacy, setLoadingLegacy] = useState(false);
  const [legacySelectedStudentId, setLegacySelectedStudentId] = useState<number | null>(null);

  const reflexSets = useMemo(() => sets.filter((s) => s.videoType === "REFLEX"), [sets]);

  useEffect(() => {
    if (mode !== "legacy" || sets.length > 0) return;
    setLoadingSets(true);
    listReviewVideoSets()
      .then(setSets)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách Bộ Video phản xạ."))
      .finally(() => setLoadingSets(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode]);

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
    setLoadingLegacy(true);
    setError(null);
    setLegacySelectedStudentId(null);
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
        setLegacySubmissions(submissionRes);
        const map: Record<number, QuestionInfo> = {};
        videoQuestionGroups.forEach(({ video, questions }: { video: ReviewVideoResponse; questions: ReviewVideoQuestionResponse[] }) => {
          questions.forEach((q) => {
            map[q.id] = { prompt: q.prompt, videoTitle: video.title, videoDisplayOrder: video.displayOrder, timestampSeconds: q.timestampSeconds };
          });
        });
        setQuestionInfoById(map);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách bài nộp."))
      .finally(() => setLoadingLegacy(false));
  }, [setId, classId]);

  const legacyStudentGroups = useMemo(
    () =>
      buildStudentGroups(legacySubmissions, (s) => {
        const info = questionInfoById[s.reviewVideoQuestionId];
        return info ? info.videoDisplayOrder * 100000 + info.timestampSeconds : 0;
      }),
    [legacySubmissions, questionInfoById]
  );
  const legacySelectedGroup = legacyStudentGroups.find((g) => g.studentId === legacySelectedStudentId) ?? null;

  const handleLegacySaved = (updated: ReviewVideoSubmissionResponse) => {
    setLegacySubmissions((prev) => prev.map((s) => (s.id === updated.id ? updated : s)));
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

        <div className="flex items-center gap-1 mt-4">
          <button
            type="button"
            onClick={() => {
              setError(null);
              setMode("queue");
            }}
            className={`px-3.5 py-2 text-xs font-bold rounded-lg transition-colors cursor-pointer ${
              mode === "queue" ? "bg-brand-orange text-white shadow-soft" : "text-slate-500 hover:bg-slate-100"
            }`}
          >
            Hàng chờ chấm bài
          </button>
          <button
            type="button"
            onClick={() => {
              setError(null);
              setMode("legacy");
            }}
            className={`px-3.5 py-2 text-xs font-bold rounded-lg transition-colors cursor-pointer ${
              mode === "legacy" ? "bg-brand-orange text-white shadow-soft" : "text-slate-500 hover:bg-slate-100"
            }`}
          >
            Xem theo Bộ + Lớp
          </button>
        </div>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {mode === "queue" ? (
        !queueClass ? (
          loadingLanding ? (
            <p className="text-xs text-slate-500">Đang tải danh sách lớp cần chấm...</p>
          ) : landingClasses.length === 0 ? (
            <div className="h-40 border border-dashed rounded-xl flex flex-col items-center justify-center text-slate-400 text-xs italic gap-1.5 text-center p-4">
              <GraduationCap className="w-6 h-6 text-slate-300" />
              <span>Không có lớp nào đang chờ chấm bài Video phản xạ.</span>
            </div>
          ) : (
            <div className="space-y-2 max-w-xl">
              {landingClasses.map((c) => (
                <button
                  key={c.classId}
                  type="button"
                  onClick={() => setQueueClass(c)}
                  className="w-full flex items-center gap-3 p-3.5 rounded-xl border border-slate-200 bg-white hover:border-brand-orange/40 hover:bg-orange-50/40 transition-colors text-left cursor-pointer"
                >
                  <GraduationCap className="w-4 h-4 text-brand-orange shrink-0" />
                  <span className="text-xs font-bold text-slate-800 flex-1">
                    {c.classCode} — {c.className}
                  </span>
                  <CountBadge count={c.pendingSubmissionCount} />
                </button>
              ))}
            </div>
          )
        ) : (
          <div className="space-y-4">
            <button
              type="button"
              onClick={backToLanding}
              className="flex items-center gap-1.5 text-xs font-bold text-slate-500 hover:text-slate-800 cursor-pointer"
            >
              <ArrowLeft className="w-3.5 h-3.5" /> Danh sách lớp
            </button>
            <h2 className="text-sm font-bold text-slate-800">
              {queueClass.classCode} — {queueClass.className}
            </h2>

            {loadingQueueSubmissions ? (
              <p className="text-xs text-slate-500">Đang tải hàng chờ chấm bài...</p>
            ) : (
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
                <div className="space-y-2 max-h-[36rem] overflow-y-auto pr-1">
                  {queueStudentGroups.length === 0 ? (
                    <div className="h-32 border border-dashed rounded-xl flex items-center justify-center text-slate-400 text-xs italic">
                      <BookOpen className="w-4 h-4 mr-1.5" /> Chưa có bài nộp nào cho lớp này.
                    </div>
                  ) : (
                    queueStudentGroups.map((g) => {
                      const gradedCount = g.submissions.filter((s) => s.score != null).length;
                      const allGraded = gradedCount === g.submissions.length;
                      return (
                        <button
                          key={g.studentId}
                          type="button"
                          onClick={() => setQueueSelectedStudentId(g.studentId)}
                          className={`w-full text-left p-3 rounded-lg border transition-colors ${
                            queueSelectedStudentId === g.studentId ? "border-brand-orange bg-orange-50" : "border-slate-200 bg-white hover:border-slate-300"
                          }`}
                        >
                          <div className="flex items-center justify-between gap-2">
                            <span className="text-xs font-bold text-slate-800">{g.studentFullName}</span>
                            <span
                              className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
                                allGraded ? "bg-emerald-50 text-emerald-600" : "bg-amber-50 text-amber-600"
                              }`}
                            >
                              {gradedCount}/{g.submissions.length} đã chấm
                            </span>
                          </div>
                        </button>
                      );
                    })
                  )}
                </div>

                <div className="space-y-4 max-h-[36rem] overflow-y-auto pr-1">
                  {!queueSelectedGroup ? (
                    <div className="h-64 border border-dashed rounded-xl flex flex-col items-center justify-center text-slate-400 text-xs italic gap-1.5 text-center p-4">
                      <BookOpen className="w-6 h-6 text-slate-300" />
                      <span>Chọn 1 học sinh trong danh sách bên cạnh để nghe và chấm từng câu.</span>
                    </div>
                  ) : (
                    <>
                      <h3 className="text-xs font-bold text-slate-800">{queueSelectedGroup.studentFullName} — {queueSelectedGroup.submissions.length} câu đã nộp</h3>
                      {queueSelectedGroup.submissions.map((s) => (
                        <div key={s.id} className="border border-slate-200 rounded-xl p-3.5">
                          <ReviewVideoGradingPanel
                            submission={s}
                            questionPrompt={s.questionPrompt ?? null}
                            videoTitle={[s.reviewVideoSetTitle, s.reviewVideoTitle].filter(Boolean).join(" — ") || null}
                            onSaved={handleQueueSaved}
                          />
                        </div>
                      ))}
                    </>
                  )}
                </div>
              </div>
            )}
          </div>
        )
      ) : (
        <div className="space-y-6">
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

          {!setId ? (
            <p className="text-xs text-slate-400 italic">Chọn 1 Bộ Video phản xạ để bắt đầu chấm bài.</p>
          ) : loadingLegacy ? (
            <p className="text-xs text-slate-500">Đang tải hàng chờ chấm bài...</p>
          ) : (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
              <div className="space-y-2 max-h-[36rem] overflow-y-auto pr-1">
                {legacyStudentGroups.length === 0 ? (
                  <div className="h-32 border border-dashed rounded-xl flex items-center justify-center text-slate-400 text-xs italic">
                    <BookOpen className="w-4 h-4 mr-1.5" /> Chưa có bài nộp nào cho lớp này.
                  </div>
                ) : (
                  legacyStudentGroups.map((g) => {
                    const gradedCount = g.submissions.filter((s) => s.score != null).length;
                    const allGraded = gradedCount === g.submissions.length;
                    return (
                      <button
                        key={g.studentId}
                        type="button"
                        onClick={() => setLegacySelectedStudentId(g.studentId)}
                        className={`w-full text-left p-3 rounded-lg border transition-colors ${
                          legacySelectedStudentId === g.studentId ? "border-brand-orange bg-orange-50" : "border-slate-200 bg-white hover:border-slate-300"
                        }`}
                      >
                        <div className="flex items-center justify-between gap-2">
                          <span className="text-xs font-bold text-slate-800">{g.studentFullName}</span>
                          <span
                            className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
                              allGraded ? "bg-emerald-50 text-emerald-600" : "bg-amber-50 text-amber-600"
                            }`}
                          >
                            {gradedCount}/{g.submissions.length} đã chấm
                          </span>
                        </div>
                      </button>
                    );
                  })
                )}
              </div>

              <div className="space-y-4 max-h-[36rem] overflow-y-auto pr-1">
                {!legacySelectedGroup ? (
                  <div className="h-64 border border-dashed rounded-xl flex flex-col items-center justify-center text-slate-400 text-xs italic gap-1.5 text-center p-4">
                    <BookOpen className="w-6 h-6 text-slate-300" />
                    <span>Chọn 1 học sinh trong danh sách bên cạnh để nghe và chấm từng câu.</span>
                  </div>
                ) : (
                  <>
                    <h3 className="text-xs font-bold text-slate-800">{legacySelectedGroup.studentFullName} — {legacySelectedGroup.submissions.length} câu đã nộp</h3>
                    {legacySelectedGroup.submissions.map((s) => {
                      const info = questionInfoById[s.reviewVideoQuestionId];
                      return (
                        <div key={s.id} className="border border-slate-200 rounded-xl p-3.5">
                          <ReviewVideoGradingPanel
                            submission={s}
                            questionPrompt={info?.prompt ?? null}
                            videoTitle={info?.videoTitle ?? null}
                            onSaved={handleLegacySaved}
                          />
                        </div>
                      );
                    })}
                  </>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      <Toast message={toastMessage} />
    </div>
  );
}
