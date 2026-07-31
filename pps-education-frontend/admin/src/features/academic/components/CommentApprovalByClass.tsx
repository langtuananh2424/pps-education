import React, { useEffect, useState } from "react";
import { Check, CheckCheck, Flag, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { ClassResponse, StudentCommentResponse, decideComments, listClassEnrollments, listClasses } from "../api";
import Badge from "@/components/ui/Badge";
import Card from "@/components/ui/Card";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import { useDialog } from "@/components/ui/DialogProvider";

const commentTypeLabels: Record<StudentCommentResponse["commentType"], string> = { DAILY: "Hàng ngày", MID_TERM: "Giữa kỳ", END_TERM: "Cuối kỳ" };
const attitudeLabels: Record<NonNullable<StudentCommentResponse["attitude"]>, string> = {
  POOR: "Kém",
  WEAK: "Yếu",
  AVERAGE: "Trung bình",
  ABOVE_AVERAGE: "Trung bình khá",
  FAIR: "Khá",
  GOOD: "Tốt"
};

interface CommentApprovalByClassProps {
  items: StudentCommentResponse[];
  loading: boolean;
  onDecided: () => void;
}

/**
 * UC-22: hàng chờ duyệt nhóm theo lớp, hiển thị dạng bảng cùng bố cục cột với màn Giáo viên nhập (UC-21)
 * — thay cho danh sách tên rời rạc + panel chi tiết riêng trước đây (đã xác nhận với người dùng 2026-07-29:
 * hiển thị từng tên rời rạc không ổn khi số lượng nhiều). Duyệt/Từ chối làm trực tiếp ngay tại dòng.
 */
export default function CommentApprovalByClass({ items, loading, onDecided }: CommentApprovalByClassProps) {
  const [classesById, setClassesById] = useState<Record<number, ClassResponse>>({});
  const [studentCodeByClassAndStudent, setStudentCodeByClassAndStudent] = useState<Record<string, string>>({});
  const [decidingId, setDecidingId] = useState<number | null>(null);
  const [decidingAllClassId, setDecidingAllClassId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { promptDialog } = useDialog();

  useEffect(() => {
    listClasses()
      .then((classes) => setClassesById(Object.fromEntries(classes.map((c) => [c.id, c]))))
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    const classIds = Array.from(new Set(items.map((it) => it.classId)));
    if (classIds.length === 0) return;
    Promise.allSettled(classIds.map((classId) => listClassEnrollments(classId).then((enrollments) => ({ classId, enrollments }))))
      .then((results) => {
        const map: Record<string, string> = {};
        results.forEach((r) => {
          if (r.status !== "fulfilled") return;
          r.value.enrollments.forEach((en) => {
            map[`${r.value.classId}-${en.studentId}`] = en.studentCode;
          });
        });
        setStudentCodeByClassAndStudent((prev) => ({ ...prev, ...map }));
      })
      .catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [items.map((it) => it.classId).join(",")]);

  const handleDecide = async (comment: StudentCommentResponse, decision: "APPROVED" | "REJECTED") => {
    let reason: string | undefined;
    if (decision === "REJECTED") {
      reason = (await promptDialog("Lý do từ chối (bắt buộc — giáo viên sẽ dựa vào đây để sửa lại):", { required: true, multiline: true })) ?? undefined;
      if (!reason?.trim()) return;
    }
    setDecidingId(comment.id);
    setError(null);
    try {
      await decideComments([comment.id], decision, reason?.trim());
      onDecided();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Duyệt nhận xét thất bại.");
    } finally {
      setDecidingId(null);
    }
  };

  const handleDecideAllClass = async (classId: number, classItems: StudentCommentResponse[]) => {
    setDecidingAllClassId(classId);
    setError(null);
    try {
      await decideComments(classItems.map((cm) => cm.id), "APPROVED");
      onDecided();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Duyệt tất cả thất bại.");
    } finally {
      setDecidingAllClassId(null);
    }
  };

  if (loading) return <p className="text-xs text-slate-500 p-4">Đang tải...</p>;
  if (items.length === 0) return <p className="text-xs text-slate-400 italic text-center py-6">Chưa có nhận xét nào chờ duyệt.</p>;

  const classIdsInOrder = Array.from(new Set(items.map((it) => it.classId))).sort((a, b) => {
    const nameA = classesById[a]?.name ?? "";
    const nameB = classesById[b]?.name ?? "";
    return nameA.localeCompare(nameB);
  });

  return (
    <div className="space-y-4">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
      {classIdsInOrder.map((classId) => {
        const cls = classesById[classId];
        const classItems = items.filter((it) => it.classId === classId);
        // Phân theo buổi (commentDate) trong từng lớp — nhiều yêu cầu chờ duyệt của cùng lớp thường
        // thuộc nhiều buổi khác nhau, gộp chung 1 bảng không biết dòng nào của ngày nào (đã xác nhận
        // với người dùng 2026-07-29).
        const datesInOrder = Array.from(new Set(classItems.map((it) => it.commentDate))).sort();
        return (
          <Card key={classId} padded={false} className="overflow-hidden">
            <div className="px-5 py-3 border-b border-slate-100 bg-slate-50 flex items-center justify-between flex-wrap gap-2">
              <span className="text-xs font-bold text-slate-700 font-display">
                {cls ? `${cls.name} (${cls.classCode})` : `Lớp #${classId}`}
              </span>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => handleDecideAllClass(classId, classItems)}
                  disabled={decidingAllClassId === classId}
                  className="flex items-center gap-1.5 px-3 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white text-[11px] font-bold rounded-lg disabled:opacity-50"
                >
                  <CheckCheck className="w-3.5 h-3.5" />
                  {decidingAllClassId === classId ? "Đang duyệt..." : "Duyệt tất cả"}
                </button>
                <Badge variant="warning">{classItems.length} chờ duyệt</Badge>
              </div>
            </div>
            {datesInOrder.map((date) => {
              const dateItems = classItems.filter((it) => it.commentDate === date);
              const weekday = new Date(date).toLocaleDateString("vi-VN", { weekday: "long" }).replace(/^./, (c) => c.toUpperCase());
              return (
                <div key={date} className="border-b border-slate-100 last:border-b-0">
                  <div className="px-5 py-2 bg-slate-50/60 flex items-center gap-2 flex-wrap">
                    <span className="text-[11px] font-bold text-slate-600">
                      Buổi {date} ({weekday})
                    </span>
                    <span className="text-[10px] text-slate-400">{dateItems.length} học sinh</span>
                    {/* lessonContent giống nhau cho cả buổi (class_sessions.lesson_content) — chỉ cần lấy từ dòng đầu, 2026-07-30. */}
                    {dateItems[0]?.lessonContent && (
                      <span className="text-[10px] text-amber-700 font-semibold">· Bài học hôm nay: {dateItems[0].lessonContent}</span>
                    )}
                  </div>
                  <TableContainer className="rounded-none border-0">
                    <thead>
                      <tr>
                        <Th className="min-w-[110px]">Mã ID</Th>
                        <Th>Họ và tên</Th>
                        <Th>Loại</Th>
                        <Th>Thái độ học tập</Th>
                        <Th>BTVN Ngữ pháp buổi trước</Th>
                        <Th>BTVN Nghe-nói buổi trước</Th>
                        <Th>Nhận xét học sinh</Th>
                        <Th>BTVN Ngữ pháp buổi sau</Th>
                        <Th>BTVN Video ôn tập buổi sau</Th>
                        <Th>Ghi chú</Th>
                        <Th>Hành động</Th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {dateItems.map((cm) => (
                        <tr key={cm.id} className="hover:bg-slate-50/40">
                          <Td className="font-mono font-bold text-slate-500">{studentCodeByClassAndStudent[`${cm.classId}-${cm.studentId}`] ?? "—"}</Td>
                          <Td className="font-bold text-slate-900 whitespace-nowrap">
                            {cm.studentFullName}
                            {cm.isWarning && <Flag className="w-3 h-3 text-rose-500 inline ml-1.5" />}
                          </Td>
                          <Td>
                            <Badge variant="info">{commentTypeLabels[cm.commentType]}</Badge>
                          </Td>
                          <Td className="min-w-[110px]">{cm.attitude ? attitudeLabels[cm.attitude] : "—"}</Td>
                          <Td className="min-w-[130px]">{cm.homeworkPreviousScore || "—"}</Td>
                          <Td className="min-w-[130px]">{cm.homeworkPreviousSpeakingScore || "—"}</Td>
                          <Td className="min-w-[260px] whitespace-pre-wrap">{cm.content}</Td>
                          <Td className="min-w-[180px]">{cm.homeworkNext || cm.homeworkNextExerciseTitle || "—"}</Td>
                          <Td className="min-w-[180px]">{cm.homeworkNextReviewVideoSetTitle || "—"}</Td>
                          <Td className="min-w-[120px]">{cm.note || "—"}</Td>
                          <Td className="min-w-[160px] whitespace-nowrap">
                            <div className="flex gap-1.5">
                              <button
                                onClick={() => handleDecide(cm, "REJECTED")}
                                disabled={decidingId === cm.id}
                                className="px-2 py-1 text-rose-600 hover:bg-rose-50 border border-rose-200 text-[11px] font-bold rounded-lg disabled:opacity-50"
                              >
                                <X className="w-3 h-3 inline mr-0.5" />
                                Từ chối
                              </button>
                              <button
                                onClick={() => handleDecide(cm, "APPROVED")}
                                disabled={decidingId === cm.id}
                                className="px-2 py-1 bg-emerald-600 hover:bg-emerald-700 text-white text-[11px] font-bold rounded-lg disabled:opacity-50"
                              >
                                <Check className="w-3 h-3 inline mr-0.5" />
                                {decidingId === cm.id ? "..." : "Duyệt"}
                              </button>
                            </div>
                          </Td>
                        </tr>
                      ))}
                    </tbody>
                  </TableContainer>
                </div>
              );
            })}
          </Card>
        );
      })}
    </div>
  );
}
