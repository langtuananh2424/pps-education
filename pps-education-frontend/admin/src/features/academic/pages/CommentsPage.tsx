import React, { useState } from "react";
import { CommentEntry } from "@/types";
import { mockCommentEntries } from "@/data/mockData";
import Card from "@/components/ui/Card";
import CommentForm from "../components/CommentForm";
import CommentApprovalQueue from "../components/CommentApprovalQueue";

export default function CommentsPage() {
  const [commentEntries, setCommentEntries] = useState<CommentEntry[]>(mockCommentEntries);

  const handleWriteComment = (type: CommentEntry["type"], content: string, isWarning: boolean) => {
    const newComment: CommentEntry = {
      id: `CMT-${Date.now().toString().substring(10)}`,
      studentId: "STU-002",
      studentName: "Trần Mai Chi",
      classId: "CLS-02",
      className: "Lớp Liên Kết - Nghĩa Tân 3A1",
      type,
      content,
      isWarning,
      status: "PENDING",
      createdAt: new Date().toISOString().substring(0, 10)
    };
    setCommentEntries((prev) => [newComment, ...prev]);
    alert("Đã gửi nhận xét lên hàng chờ phê duyệt của Ban quản lý điểm trường (UC-21)!");
  };

  const handleApproveComment = (id: string, status: "APPROVED" | "REJECTED") => {
    setCommentEntries((prev) => prev.map((cm) => (cm.id === id ? { ...cm, status } : cm)));
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Học Thuật & Quy Chuẩn Đào Tạo (Academic)</h1>
        <p className="text-xs text-slate-500 mt-1">Viết và phê duyệt nhận xét định kỳ học viên (UC-21/22).</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card>
          <h3 className="text-xs font-bold text-slate-400 block uppercase tracking-wider font-display border-b border-slate-100 pb-2 mb-4">Viết Nhận Xét Học Viên (TEACHER - UC-21)</h3>
          <CommentForm onSubmit={handleWriteComment} />
        </Card>

        <Card padded={false} className="lg:col-span-2 overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100 bg-slate-50">
            <span className="text-xs font-bold text-slate-700 font-display">Phê duyệt Nhận xét Học viên (UC-22)</span>
          </div>
          <CommentApprovalQueue comments={commentEntries} onApprove={handleApproveComment} />
        </Card>
      </div>
    </div>
  );
}
