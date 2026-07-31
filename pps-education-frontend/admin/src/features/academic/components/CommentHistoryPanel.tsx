import React, { useEffect, useState } from "react";
import { ChevronDown, ChevronRight } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { ClassResponse, StudentCommentResponse, listClassEnrollments, listComments } from "../api";
import { useEligibleClasses } from "../hooks/useEligibleClasses";
import Badge from "@/components/ui/Badge";
import Card from "@/components/ui/Card";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import Select from "@/components/ui/Select";
import DatePicker from "@/components/ui/DatePicker";

const commentTypeLabels: Record<StudentCommentResponse["commentType"], string> = { DAILY: "Hàng ngày", MID_TERM: "Giữa kỳ", END_TERM: "Cuối kỳ" };

/**
 * UC-22 bổ sung (2026-07-31, theo yêu cầu người dùng): Quản lý điểm trường xem lại LỊCH SỬ nhận xét
 * đã quyết định (APPROVED/REJECTED), PHÂN THEO LỚP — trước đây "Nhận xét học viên" chỉ có hàng chờ
 * duyệt (listPendingComments), duyệt/từ chối xong là mất dấu, không tra cứu lại được.
 *
 * Backend CHƯA có endpoint "toàn bộ nhận xét của 1 site/lớp" (chỉ có GET /classes/{id}/comments?
 * studentId= theo TỪNG học sinh) — gom bằng N+1 giống pattern CommentApprovalByClass/ParentsPage:
 * dùng useEligibleClasses() để lấy đúng các lớp Quản lý điểm trường phụ trách (đã tự scope theo site),
 * với MỖI lớp liệt kê học sinh ACTIVE rồi gọi listComments cho từng em, lọc còn APPROVED/REJECTED
 * (DRAFT/PENDING không phải "lịch sử", đã có ở tab Chờ duyệt), gom nhóm hiển thị theo lớp.
 *
 * "Ngày duyệt" chỉ có giá trị với nhận xét ĐÃ DUYỆT — BE chỉ set approvedAt ở nhánh APPROVED (xem
 * StudentCommentService.decideComments), REJECTED không có mốc thời gian quyết định nào được trả về
 * qua API nên hiện "—" (khác NGÀY BUỔI HỌC ở cột đầu — commentDate — luôn có với mọi dòng).
 */
export default function CommentHistoryPanel() {
  const { classes, loading: loadingClasses } = useEligibleClasses();
  const [comments, setComments] = useState<StudentCommentResponse[]>([]);
  const [studentCodeByStudent, setStudentCodeByStudent] = useState<Record<number, string>>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<"ALL" | "APPROVED" | "REJECTED">("ALL");
  const [classFilter, setClassFilter] = useState<number | "ALL">("ALL");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");

  useEffect(() => {
    if (classes.length === 0) {
      setComments([]);
      return;
    }
    setLoading(true);
    setError(null);
    Promise.all(
      classes.map((cls) =>
        listClassEnrollments(cls.id)
          .then((enrollments) => {
            const active = enrollments.filter((en) => en.status === "ACTIVE");
            return Promise.allSettled(active.map((en) => listComments(cls.id, en.studentId))).then((results) => ({
              comments: results.flatMap((r) => (r.status === "fulfilled" ? r.value : [])),
              studentCodeByStudent: Object.fromEntries(active.map((en) => [en.studentId, en.studentCode]))
            }));
          })
          .catch(() => ({ comments: [] as StudentCommentResponse[], studentCodeByStudent: {} as Record<number, string> }))
      )
    )
      .then((perClass) => {
        const allComments = perClass.flatMap((x) => x.comments).filter((c) => c.status === "APPROVED" || c.status === "REJECTED");
        const codeMap: Record<number, string> = {};
        perClass.forEach((x) => Object.assign(codeMap, x.studentCodeByStudent));
        setComments(allComments);
        setStudentCodeByStudent(codeMap);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được lịch sử nhận xét."))
      .finally(() => setLoading(false));
  }, [classes]);

  const filtered = comments.filter((c) => {
    if (statusFilter !== "ALL" && c.status !== statusFilter) return false;
    if (classFilter !== "ALL" && c.classId !== classFilter) return false;
    if (dateFrom && c.commentDate < dateFrom) return false;
    if (dateTo && c.commentDate > dateTo) return false;
    return true;
  });

  const classesById: Record<number, ClassResponse> = Object.fromEntries(classes.map((c) => [c.id, c]));
  const classIdsInOrder = Array.from(new Set(filtered.map((c) => c.classId))).sort((a, b) =>
    (classesById[a]?.name ?? "").localeCompare(classesById[b]?.name ?? "")
  );

  const formatDecidedAt = (cm: StudentCommentResponse) => (cm.approvedAt ? new Date(cm.approvedAt).toLocaleString("vi-VN") : "—");

  if (loadingClasses) return <p className="text-xs text-slate-500 p-4">Đang tải danh sách lớp...</p>;
  if (classes.length === 0) return <p className="text-xs text-slate-400 italic text-center py-10">Bạn chưa phụ trách lớp nào.</p>;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <Select
          value={classFilter}
          onChange={(e) => setClassFilter(e.target.value === "ALL" ? "ALL" : Number(e.target.value))}
          className="bg-white border border-slate-200 text-xs px-2.5 py-2 rounded-lg focus:outline-none"
        >
          <option value="ALL">Tất cả lớp</option>
          {classes.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name} ({c.classCode})
            </option>
          ))}
        </Select>
        <Select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as typeof statusFilter)}
          className="bg-white border border-slate-200 text-xs px-2.5 py-2 rounded-lg focus:outline-none"
        >
          <option value="ALL">Tất cả trạng thái</option>
          <option value="APPROVED">Đã duyệt</option>
          <option value="REJECTED">Bị từ chối</option>
        </Select>
        <span className="text-[10px] uppercase font-bold text-slate-400">Từ ngày:</span>
        <div className="w-36">
          <DatePicker value={dateFrom} onChange={setDateFrom} max={dateTo || undefined} />
        </div>
        <span className="text-xs text-slate-400">đến</span>
        <div className="w-36">
          <DatePicker value={dateTo} onChange={setDateTo} min={dateFrom || undefined} />
        </div>
        {(dateFrom || dateTo || statusFilter !== "ALL" || classFilter !== "ALL") && (
          <button
            onClick={() => {
              setDateFrom("");
              setDateTo("");
              setStatusFilter("ALL");
              setClassFilter("ALL");
            }}
            className="text-[11px] font-semibold text-brand-red hover:underline"
          >
            Xóa lọc
          </button>
        )}
        <span className="text-[11px] text-slate-400 ml-auto">
          {filtered.length}/{comments.length} nhận xét
        </span>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {loading ? (
        <p className="text-xs text-slate-500 p-4">Đang tải...</p>
      ) : filtered.length === 0 ? (
        <p className="text-xs text-slate-400 italic text-center py-10">
          {comments.length === 0 ? "Chưa có nhận xét nào đã duyệt/từ chối." : "Không có nhận xét nào khớp bộ lọc."}
        </p>
      ) : (
        <div className="space-y-4">
          {classIdsInOrder.map((classId) => {
            const cls = classesById[classId];
            const classComments = filtered
              .filter((c) => c.classId === classId)
              .sort((a, b) => `${b.commentDate}T${b.approvedAt ?? ""}`.localeCompare(`${a.commentDate}T${a.approvedAt ?? ""}`));
            return (
              <Card key={classId} padded={false} className="overflow-hidden">
                <div className="px-5 py-3 border-b border-slate-100 bg-slate-50 flex items-center justify-between flex-wrap gap-2">
                  <span className="text-xs font-bold text-slate-700 font-display">{cls ? `${cls.name} (${cls.classCode})` : `Lớp #${classId}`}</span>
                  <Badge variant="neutral">{classComments.length} nhận xét</Badge>
                </div>
                {/* Phân theo buổi (commentDate) trong từng lớp, dạng dropdown thu gọn — cùng cách nhóm
                    ở tab Chờ duyệt (CommentApprovalByClass) nhưng gập lại mặc định cho đỡ tốn diện
                    tích, bấm vào tiêu đề buổi mới xổ ra bảng chi tiết. */}
                {Array.from(new Set(classComments.map((c) => c.commentDate))).map((date) => (
                  <SessionGroup
                    key={date}
                    date={date}
                    items={classComments.filter((c) => c.commentDate === date)}
                    studentCodeByStudent={studentCodeByStudent}
                    formatDecidedAt={formatDecidedAt}
                  />
                ))}
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}

/** 1 buổi học trong 1 lớp — gập/mở độc lập, mặc định gập để danh sách nhiều buổi không chiếm hết màn hình. */
function SessionGroup({
  date,
  items,
  studentCodeByStudent,
  formatDecidedAt
}: {
  date: string;
  items: StudentCommentResponse[];
  studentCodeByStudent: Record<number, string>;
  formatDecidedAt: (cm: StudentCommentResponse) => string;
}) {
  const [expanded, setExpanded] = useState(false);
  const weekday = new Date(date).toLocaleDateString("vi-VN", { weekday: "long" }).replace(/^./, (c) => c.toUpperCase());

  return (
    <div className="border-b border-slate-100 last:border-b-0">
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        className="w-full px-5 py-2 bg-slate-50/60 hover:bg-slate-100/60 flex items-center gap-2 flex-wrap transition-colors"
      >
        {expanded ? <ChevronDown className="w-3.5 h-3.5 text-slate-400 shrink-0" /> : <ChevronRight className="w-3.5 h-3.5 text-slate-400 shrink-0" />}
        <span className="text-[11px] font-bold text-slate-600">
          Buổi {date} ({weekday})
        </span>
        <span className="text-[10px] text-slate-400">{items.length} nhận xét</span>
      </button>
      {expanded && (
        <TableContainer className="rounded-none border-0">
          <thead>
            <tr>
              <Th className="min-w-[110px]">Mã ID</Th>
              <Th>Họ và tên</Th>
              <Th>Ngày sinh</Th>
              <Th>Loại</Th>
              <Th className="min-w-[260px]">Nhận xét</Th>
              <Th>Trạng thái</Th>
              <Th className="min-w-[140px]">Ngày duyệt</Th>
              <Th className="min-w-[180px]">Lý do từ chối</Th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {items.map((cm) => (
              <tr key={cm.id} className="hover:bg-slate-50/40">
                <Td className="font-mono font-bold text-slate-500">{studentCodeByStudent[cm.studentId] ?? "—"}</Td>
                <Td className="font-bold text-slate-900 whitespace-nowrap">{cm.studentFullName}</Td>
                <Td className="whitespace-nowrap text-slate-500">{cm.studentDateOfBirth ?? "—"}</Td>
                <Td>
                  <Badge variant="info">{commentTypeLabels[cm.commentType]}</Badge>
                </Td>
                <Td className="min-w-[260px] whitespace-pre-wrap">{cm.content}</Td>
                <Td>
                  <Badge variant={cm.status === "APPROVED" ? "success" : "danger"}>{cm.status === "APPROVED" ? "Đã duyệt" : "Bị từ chối"}</Badge>
                </Td>
                <Td className="whitespace-nowrap">{formatDecidedAt(cm)}</Td>
                <Td className="min-w-[180px] text-rose-600">{cm.rejectionReason || "—"}</Td>
              </tr>
            ))}
          </tbody>
        </TableContainer>
      )}
    </div>
  );
}
