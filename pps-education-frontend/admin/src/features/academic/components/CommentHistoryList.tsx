import React, { useEffect, useState } from "react";
import { Flag, Save, Send } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { StudentCommentResponse, submitComments, updateComment } from "../api";
import {
  ExerciseAssignmentResponse,
  ExerciseResponse,
  ReviewVideoAssignmentResponse,
  ReviewVideoSetResponse,
  listAssignmentsForClass,
  listPublishedExercisesForClass,
  listReviewVideoAssignmentsForClass,
  listReviewVideoSetsByClass
} from "@/features/lms/api";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import Select from "@/components/ui/Select";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";

type GrammarMode = "OFFLINE" | "ONLINE";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none";
/** Thang thái độ chốt lại 2026-08-12 (StudentComment.Attitude). */
const attitudeLabels: Record<NonNullable<StudentCommentResponse["attitude"]>, string> = {
  WEAK: "Yếu",
  AVERAGE: "Trung bình",
  FAIR: "Khá",
  GOOD: "Tốt",
  EXCELLENT: "Xuất sắc"
};
const statusLabels: Record<StudentCommentResponse["status"], string> = { DRAFT: "Nháp", PENDING: "Chờ duyệt", APPROVED: "Đã duyệt", REJECTED: "Bị từ chối" };
const statusVariants: Record<StudentCommentResponse["status"], "success" | "warning" | "danger" | "neutral"> = {
  DRAFT: "neutral",
  PENDING: "warning",
  APPROVED: "success",
  REJECTED: "danger"
};

interface CommentHistoryListProps {
  classId: number;
  history: StudentCommentResponse[];
  onChanged: () => void;
  showStudentName?: boolean;
  /** "table": hiện dạng bảng cùng bố cục cột với màn nhập trực tiếp (UC-21 DAILY) — mặc định "card" (dùng cho UC-21 MID_TERM/END_TERM, danh sách 1 học sinh). */
  layout?: "card" | "table";
  /** Chỉ cần khi layout="table" — StudentCommentResponse không có studentCode, lấy từ danh sách enrollment ở component cha. */
  studentCodeById?: Record<number, string>;
  /** Chỉ cần khi layout="table" — nhãn 2 kênh BTVN theo Loại giáo viên của buổi, ăn theo state đã tính sẵn ở DailyCommentPanel (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06). */
  grammarLabel?: string;
  videoLabel?: string;
}

/** UC-21 A1: nhận xét bị Quản lý điểm trường từ chối (kèm lý do) — Giáo viên sửa lại nội dung rồi gửi lại thẳng lên hàng chờ duyệt. */
export default function CommentHistoryList({
  classId,
  history,
  onChanged,
  showStudentName,
  layout = "card",
  studentCodeById,
  grammarLabel = "Bài",
  videoLabel = "Video"
}: CommentHistoryListProps) {
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editContent, setEditContent] = useState("");
  const [editSeverity, setEditSeverity] = useState<NonNullable<StudentCommentResponse["severity"]>>("NORMAL");
  const [editIsWarning, setEditIsWarning] = useState(false);
  const [editAttitude, setEditAttitude] = useState<"" | NonNullable<StudentCommentResponse["attitude"]>>("");
  const [editHomeworkPreviousScore, setEditHomeworkPreviousScore] = useState("");
  const [editHomeworkPreviousSpeakingScore, setEditHomeworkPreviousSpeakingScore] = useState("");
  const [editGrammarMode, setEditGrammarMode] = useState<GrammarMode>("OFFLINE");
  const [editHomeworkNext, setEditHomeworkNext] = useState("");
  /** V65: id của Exercise NGUỒN đã Publish (không phải id bản giao) — chọn từ grammarOptions. */
  const [editHomeworkNextExerciseId, setEditHomeworkNextExerciseId] = useState<number | "">("");
  const [editHomeworkNextReviewVideoSetId, setEditHomeworkNextReviewVideoSetId] = useState<number | "">("");
  const [editNote, setEditNote] = useState("");
  const [saving, setSaving] = useState(false);
  const [submittingId, setSubmittingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  /** V65: nguồn khả dụng cho dropdown — Exercise đã Publish (không phải bản giao). */
  const [grammarOptions, setGrammarOptions] = useState<ExerciseResponse[]>([]);
  const [videoOptions, setVideoOptions] = useState<ReviewVideoSetResponse[]>([]);
  /** V65: bản giao ACTIVE hiện có của lớp — chỉ dùng để tra ngược "comment đã lưu chọn nguồn nào" khi bắt đầu sửa. */
  const [grammarAssignments, setGrammarAssignments] = useState<ExerciseAssignmentResponse[]>([]);
  const [videoAssignments, setVideoAssignments] = useState<ReviewVideoAssignmentResponse[]>([]);
  const { message: toastMessage, showToast } = useToast();

  useEffect(() => {
    listPublishedExercisesForClass(classId).then(setGrammarOptions).catch(() => undefined);
    listReviewVideoSetsByClass(classId)
      .then((sets) => setVideoOptions(sets.filter((s) => s.status === "PUBLISHED")))
      .catch(() => undefined);
    listAssignmentsForClass(classId).then(setGrammarAssignments).catch(() => undefined);
    listReviewVideoAssignmentsForClass(classId).then(setVideoAssignments).catch(() => undefined);
  }, [classId]);

  const startEdit = (h: StudentCommentResponse) => {
    setEditingId(h.id);
    setEditContent(h.content);
    setEditSeverity(h.severity ?? "NORMAL");
    setEditIsWarning(h.isWarning);
    setEditAttitude(h.attitude ?? "");
    setEditHomeworkPreviousScore(h.homeworkPreviousScore ?? "");
    setEditHomeworkPreviousSpeakingScore(h.homeworkPreviousSpeakingScore ?? "");
    setEditGrammarMode(h.homeworkNextExerciseAssignmentId != null ? "ONLINE" : "OFFLINE");
    setEditHomeworkNext(h.homeworkNext ?? "");
    // V65: response chỉ trả id bản giao — tra ngược qua grammarAssignments/videoAssignments để lấy đúng id nguồn.
    setEditHomeworkNextExerciseId(
      h.homeworkNextExerciseAssignmentId != null
        ? grammarAssignments.find((a) => a.id === h.homeworkNextExerciseAssignmentId)?.exerciseId ?? ""
        : ""
    );
    setEditHomeworkNextReviewVideoSetId(
      h.homeworkNextReviewVideoAssignmentId != null
        ? videoAssignments.find((a) => a.id === h.homeworkNextReviewVideoAssignmentId)?.reviewVideoSetId ?? ""
        : ""
    );
    setEditNote(h.note ?? "");
    setError(null);
  };

  const handleSubmitDraft = async (commentId: number) => {
    setSubmittingId(commentId);
    setError(null);
    try {
      await submitComments(classId, [commentId]);
      onChanged();
      showToast("Đã nộp duyệt nhận xét thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Nộp nhận xét thất bại.");
    } finally {
      setSubmittingId(null);
    }
  };

  const handleResend = async (commentId: number) => {
    if (!editContent.trim()) {
      setError("Vui lòng nhập nội dung nhận xét.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await updateComment(commentId, {
        content: editContent.trim(),
        severity: editSeverity,
        isWarning: editIsWarning,
        attitude: editAttitude || undefined,
        homeworkPreviousScore: editHomeworkPreviousScore.trim() || undefined,
        homeworkPreviousSpeakingScore: editHomeworkPreviousSpeakingScore.trim() || undefined,
        homeworkNext: editGrammarMode === "OFFLINE" ? editHomeworkNext.trim() || undefined : undefined,
        homeworkNextExerciseId: editGrammarMode === "ONLINE" && editHomeworkNextExerciseId !== "" ? editHomeworkNextExerciseId : undefined,
        homeworkNextReviewVideoSetId: editHomeworkNextReviewVideoSetId !== "" ? editHomeworkNextReviewVideoSetId : undefined,
        note: editNote.trim() || undefined
      });
      // UC-21 (2026-07-29, BE PR #113 khôi phục DRAFT cho DAILY): updateComment() giờ luôn giữ ở DRAFT,
      // dùng chung 100% với MID_TERM/END_TERM — mọi commentType đều cần gọi thêm submitComments() để
      // chuyển DRAFT → PENDING.
      await submitComments(classId, [commentId]);
      setEditingId(null);
      onChanged();
      showToast("Đã gửi lại nhận xét thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Gửi lại nhận xét thất bại.");
    } finally {
      setSaving(false);
    }
  };

  if (history.length === 0) {
    return <p className="text-xs text-slate-400 italic">Chưa có nhận xét nào.</p>;
  }

  const renderEditForm = (h: StudentCommentResponse) => (
    <div className="space-y-2 pt-1">
      {h.commentType === "DAILY" && (
                <div className="grid grid-cols-3 gap-2">
                  <Select value={editAttitude} onChange={(e) => setEditAttitude(e.target.value as typeof editAttitude)} className={inputClass}>
                    <option value="">-- Thái độ học tập --</option>
                    {Object.entries(attitudeLabels).map(([value, label]) => (
                      <option key={value} value={value}>
                        {label}
                      </option>
                    ))}
                  </Select>
                  <input
                    value={editHomeworkPreviousScore}
                    onChange={(e) => setEditHomeworkPreviousScore(e.target.value)}
                    placeholder="BTVN Ngữ pháp buổi trước"
                    className={inputClass}
                  />
                  <input
                    value={editHomeworkPreviousSpeakingScore}
                    onChange={(e) => setEditHomeworkPreviousSpeakingScore(e.target.value)}
                    placeholder="BTVN Nghe-nói buổi trước"
                    className={inputClass}
                  />
                </div>
              )}
              <textarea value={editContent} onChange={(e) => setEditContent(e.target.value)} rows={3} className={inputClass} />
              {h.commentType === "DAILY" && (
                <>
                  <div className="space-y-1">
                    <div className="flex gap-1">
                      <button
                        type="button"
                        onClick={() => {
                          setEditGrammarMode("OFFLINE");
                          setEditHomeworkNextExerciseId("");
                        }}
                        className={`flex-1 text-[10px] font-bold py-1.5 rounded-lg border ${
                          editGrammarMode === "OFFLINE" ? "bg-brand-orange border-brand-orange text-white" : "bg-slate-50 border-slate-200 text-slate-500"
                        }`}
                      >
                        BTVN Ngữ pháp: Offline
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setEditGrammarMode("ONLINE");
                          setEditHomeworkNext("");
                        }}
                        className={`flex-1 text-[10px] font-bold py-1.5 rounded-lg border ${
                          editGrammarMode === "ONLINE" ? "bg-brand-orange border-brand-orange text-white" : "bg-slate-50 border-slate-200 text-slate-500"
                        }`}
                      >
                        Online
                      </button>
                    </div>
                    {editGrammarMode === "OFFLINE" ? (
                      <input value={editHomeworkNext} onChange={(e) => setEditHomeworkNext(e.target.value)} placeholder="VD: Unit 2 trang 10" className={inputClass} />
                    ) : (
                      <Select
                        value={editHomeworkNextExerciseId}
                        onChange={(e) => setEditHomeworkNextExerciseId(e.target.value ? Number(e.target.value) : "")}
                        className={inputClass}
                      >
                        <option value="">-- Chọn đề đã Publish --</option>
                        {grammarOptions.map((ex) => (
                          <option key={ex.id} value={ex.id}>
                            {ex.examCode} - {ex.title}
                          </option>
                        ))}
                      </Select>
                    )}
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    <Select
                      value={editHomeworkNextReviewVideoSetId}
                      onChange={(e) => setEditHomeworkNextReviewVideoSetId(e.target.value ? Number(e.target.value) : "")}
                      className={inputClass}
                    >
                      <option value="">-- Không giao Video ôn tập --</option>
                      {videoOptions.map((s) => (
                        <option key={s.id} value={s.id}>
                          {s.title} ({s.code})
                        </option>
                      ))}
                    </Select>
                    <input value={editNote} onChange={(e) => setEditNote(e.target.value)} placeholder="Ghi chú" className={inputClass} />
                  </div>
                </>
              )}
              <div className="grid grid-cols-2 gap-2">
                <Select value={editSeverity} onChange={(e) => setEditSeverity(e.target.value as NonNullable<StudentCommentResponse["severity"]>)} className={inputClass}>
                  <option value="POSITIVE">Tích cực</option>
                  <option value="NORMAL">Bình thường</option>
                  <option value="CONCERN">Cần lưu ý</option>
                  <option value="WARNING">Cảnh báo</option>
                </Select>
                <label className="flex items-center gap-1.5 text-[10px] font-semibold text-slate-600">
                  <input type="checkbox" checked={editIsWarning} onChange={(e) => setEditIsWarning(e.target.checked)} />
                  <Flag className="w-3 h-3 text-rose-500" />
                  Cảnh báo đặc biệt
                </label>
              </div>
              <div className="flex gap-2">
                <Button type="button" size="sm" variant="secondary" onClick={() => setEditingId(null)}>
                  Hủy
                </Button>
                <Button type="button" size="sm" variant="primary" onClick={() => handleResend(h.id)} disabled={saving}>
                  <Send className="w-3.5 h-3.5" />
                  {saving ? "Đang gửi..." : "Gửi lại"}
                </Button>
              </div>
            </div>
  );

  const renderReadOnlyExtras = (h: StudentCommentResponse) => (
    <>
      {h.commentType === "DAILY" &&
        (h.attitude || h.homeworkPreviousScore || h.homeworkPreviousSpeakingScore || h.grammarPreviousProgress || h.videoPreviousProgress) && (
          <p className="text-slate-500">
            {h.attitude && `Thái độ: ${attitudeLabels[h.attitude]}`}
            {h.attitude && h.homeworkPreviousScore && " · "}
            {h.homeworkPreviousScore && `BTVN Ngữ pháp buổi trước: ${h.homeworkPreviousScore}`}
            {(h.attitude || h.homeworkPreviousScore) && h.homeworkPreviousSpeakingScore && " · "}
            {h.homeworkPreviousSpeakingScore && `BTVN Nghe-nói buổi trước: ${h.homeworkPreviousSpeakingScore}`}
            {(h.attitude || h.homeworkPreviousScore || h.homeworkPreviousSpeakingScore) && (h.grammarPreviousProgress || h.videoPreviousProgress) && " · "}
            {h.grammarPreviousProgress && `% Ngữ pháp (tự động): ${h.grammarPreviousProgress}`}
            {h.grammarPreviousProgress && h.videoPreviousProgress && " · "}
            {h.videoPreviousProgress && `% Video (tự động): ${h.videoPreviousProgress}`}
          </p>
        )}
      <p className="text-slate-700">{h.content}</p>
      {h.commentType === "DAILY" && (h.homeworkNext || h.homeworkNextExerciseTitle || h.homeworkNextReviewVideoSetTitle || h.note) && (
        <p className="text-slate-500">
          {h.homeworkNext && `BTVN Ngữ pháp buổi sau (offline): ${h.homeworkNext}`}
          {h.homeworkNextExerciseTitle && `BTVN Ngữ pháp buổi sau (online): ${h.homeworkNextExerciseTitle}`}
          {(h.homeworkNext || h.homeworkNextExerciseTitle) && h.homeworkNextReviewVideoSetTitle && " · "}
          {h.homeworkNextReviewVideoSetTitle && `Video ôn tập buổi sau: ${h.homeworkNextReviewVideoSetTitle}`}
          {(h.homeworkNext || h.homeworkNextExerciseTitle || h.homeworkNextReviewVideoSetTitle) && h.note && " · "}
          {h.note && `Ghi chú: ${h.note}`}
        </p>
      )}
      {h.status === "REJECTED" && h.rejectionReason && <p className="text-rose-500">Lý do từ chối: {h.rejectionReason}</p>}
    </>
  );

  const renderActions = (h: StudentCommentResponse) => (
    <>
      {/* layout="table" (DAILY, DailyCommentPanel) đã gộp Ghi+Gửi vào đúng 1 nút "Gửi nhận xét" ở màn
          nhập trực tiếp (kể cả dòng đang DRAFT) — nút "Nộp duyệt" riêng ở đây thành thừa/dễ gây nộp 2
          lần, chỉ giữ cho layout="card" (MID_TERM/END_TERM, PeriodicCommentPanel — CommentForm chỉ lưu
          nháp, "Nộp duyệt" ở đây là cách DUY NHẤT để gửi duyệt) — đã xác nhận với người dùng 2026-07-30. */}
      {h.status === "DRAFT" && layout !== "table" && (
        <Button size="sm" variant="secondary" onClick={() => handleSubmitDraft(h.id)} disabled={submittingId === h.id}>
          <Save className="w-3.5 h-3.5" />
          {submittingId === h.id ? "Đang nộp..." : "Nộp duyệt"}
        </Button>
      )}
      {h.status === "REJECTED" && (
        <Button size="sm" variant="secondary" onClick={() => startEdit(h)}>
          Sửa & Gửi lại
        </Button>
      )}
    </>
  );

  if (layout === "table") {
    // Đồng bộ đúng bố cục/tên cột với bảng nhập trực tiếp ở trên + 2 màn Quản lý điểm trường (bổ sung
    // ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06) — "BTVN buổi trước"/"buổi sau" đều tách 3
    // kênh Offline/Bài/Video, thêm cột Hạn nộp bài, border rõ + căn giữa header.
    const COLUMN_COUNT = 14;
    return (
      <div className="space-y-2">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        <TableContainer>
          <thead>
            <tr className="border-b border-slate-300 [&>th]:text-center">
              <Th rowSpan={2} className="min-w-[120px] border-r border-slate-300">Mã học viên</Th>
              <Th rowSpan={2} className="border-r border-slate-300">Họ và tên</Th>
              <Th rowSpan={2} className="border-r border-slate-300">Ngày sinh</Th>
              <Th colSpan={3} className="text-center border-r border-slate-300">BTVN buổi trước</Th>
              <Th rowSpan={2} className="border-r border-slate-300">BTVN offline</Th>
              <Th colSpan={2} className="text-center border-r border-slate-300">BTVN online</Th>
              <Th rowSpan={2} className="border-r border-slate-300">Hạn nộp bài</Th>
              <Th rowSpan={2} className="border-r border-slate-300">Thái độ học tập</Th>
              <Th rowSpan={2} className="border-r border-slate-300">Nhận xét học sinh</Th>
              <Th rowSpan={2} className="border-r border-slate-300">Ghi chú</Th>
              <Th rowSpan={2}>Trạng thái</Th>
            </tr>
            <tr className="border-b border-slate-300 [&>th]:text-center">
              <Th className="border-r border-slate-300 text-center">Offline</Th>
              <Th className="border-r border-slate-300 text-center">{grammarLabel}</Th>
              <Th className="border-r border-slate-300 text-center">{videoLabel}</Th>
              <Th className="border-r border-slate-300 text-center">{grammarLabel}</Th>
              <Th className="border-r border-slate-300 text-center">{videoLabel}</Th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-300">
            {history.map((h) => (
              <React.Fragment key={h.id}>
                <tr className="hover:bg-slate-50/40">
                  <Td className="font-mono font-bold text-slate-500 border-r border-slate-300">{studentCodeById?.[h.studentId] ?? "—"}</Td>
                  <Td className="font-bold text-slate-900 whitespace-nowrap border-r border-slate-300">{h.studentFullName}</Td>
                  <Td className="whitespace-nowrap text-slate-500 border-r border-slate-300">{h.studentDateOfBirth ?? "—"}</Td>
                  <Td className="min-w-[110px] border-r border-slate-300">{h.homeworkPreviousOfflineText || "—"}</Td>
                  <Td className="min-w-[130px] border-r border-slate-300">{h.homeworkPreviousScore || "—"}</Td>
                  <Td className="min-w-[130px] border-r border-slate-300">{h.homeworkPreviousSpeakingScore || "—"}</Td>
                  <Td className="min-w-[160px] border-r border-slate-300">{h.homeworkNext || "—"}</Td>
                  <Td className="min-w-[180px] border-r border-slate-300">{h.homeworkNextExerciseTitle || "—"}</Td>
                  <Td className="min-w-[180px] border-r border-slate-300">{h.homeworkNextReviewVideoSetTitle || "—"}</Td>
                  <Td className="min-w-[120px] whitespace-nowrap border-r border-slate-300">
                    {h.homeworkNextDueAt ? new Date(h.homeworkNextDueAt).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" }) : "—"}
                  </Td>
                  <Td className="min-w-[110px] border-r border-slate-300">{h.attitude ? attitudeLabels[h.attitude] : "—"}</Td>
                  <Td className="min-w-[260px] whitespace-pre-wrap border-r border-slate-300">{h.content}</Td>
                  <Td className="min-w-[120px] border-r border-slate-300">{h.note || "—"}</Td>
                  <Td className="min-w-[150px] whitespace-nowrap space-y-1.5">
                    <Badge variant={statusVariants[h.status]}>{statusLabels[h.status]}</Badge>
                    <div>{renderActions(h)}</div>
                  </Td>
                </tr>
                {h.status === "REJECTED" && h.rejectionReason && editingId !== h.id && (
                  <tr>
                    <td colSpan={COLUMN_COUNT} className="px-4 pb-2 -mt-2 text-[11px] text-rose-500">
                      Lý do từ chối: {h.rejectionReason}
                    </td>
                  </tr>
                )}
                {editingId === h.id && (
                  <tr>
                    <td colSpan={COLUMN_COUNT} className="px-4 pb-3 bg-slate-50/60">
                      {renderEditForm(h)}
                    </td>
                  </tr>
                )}
              </React.Fragment>
            ))}
          </tbody>
        </TableContainer>
        <Toast message={toastMessage} />
      </div>
    );
  }

  return (
    <div className="space-y-2">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
      {history.map((h) => (
        <div key={h.id} className="border border-slate-200 rounded-lg p-2.5 text-[11px] space-y-1.5">
          <div className="flex items-center justify-between gap-2">
            {showStudentName && <span className="font-bold text-slate-800">{h.studentFullName}</span>}
            <span className="text-slate-400">{h.commentDate}</span>
            <Badge variant={statusVariants[h.status]}>{statusLabels[h.status]}</Badge>
          </div>
          {editingId === h.id ? (
            renderEditForm(h)
          ) : (
            <>
              {renderReadOnlyExtras(h)}
              {renderActions(h)}
            </>
          )}
        </div>
      ))}

      <Toast message={toastMessage} />
    </div>
  );
}
