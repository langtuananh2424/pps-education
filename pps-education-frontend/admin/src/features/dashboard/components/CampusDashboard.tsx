import React from "react";
import { useNavigate } from "react-router-dom";
import { ArrowRight } from "lucide-react";
import { mockCampuses, mockFeedbackTickets } from "@/data/mockData";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";

interface CampusDashboardProps {
  totalOutstanding: number;
}

export default function CampusDashboard({ totalOutstanding }: CampusDashboardProps) {
  const navigate = useNavigate();

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Quản Lý Vận Hành Cơ Sở & Điểm Trường</h1>
        <p className="text-xs text-slate-500 mt-1">
          Giám sát phản hồi đối tác trường liên kết, quản lý ca làm việc giáo viên và rà soát phòng linh hoạt.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <Card padded className="p-4">
          <span className="text-[10px] text-slate-400 font-bold block uppercase tracking-wider font-display">Ý kiến đối tác mới</span>
          <div className="text-2xl font-bold text-brand-red font-display mt-1">
            {mockFeedbackTickets.filter((f) => f.status === "NEW").length}
          </div>
        </Card>
        <Card padded className="p-4">
          <span className="text-[10px] text-slate-400 font-bold block uppercase tracking-wider font-display">
            Hợp đồng liên kết hoạt động
          </span>
          <div className="text-2xl font-bold text-slate-800 font-display">{mockCampuses.filter((c) => c.type === "AFFILIATED").length}</div>
        </Card>
        <Card padded className="p-4">
          <span className="text-[10px] text-slate-400 font-bold block uppercase tracking-wider font-display">Công nợ học phí cơ sở</span>
          <div className="text-2xl font-bold text-amber-600 font-display mt-1">
            {totalOutstanding.toLocaleString("vi-VN")} <span className="text-xs">đ</span>
          </div>
        </Card>
        <Card padded className="p-4">
          <span className="text-[10px] text-slate-400 font-bold block uppercase tracking-wider font-display">Ý thức cảnh báo (Warning)</span>
          <div className="text-2xl font-bold text-slate-800 font-display">1 học viên</div>
        </Card>
      </div>

      <Card>
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-bold text-slate-800 font-display">Hòm thư phản hồi đối tác liên kết mới nhất (UC-39)</h3>
          <button onClick={() => navigate("/facility/feedback")} className="text-xs text-brand-orange hover:text-brand-red font-bold">
            Xem tất cả phản hồi
          </button>
        </div>
        <div className="space-y-4">
          {mockFeedbackTickets.map((tkt) => (
            <div
              key={tkt.id}
              className="p-3.5 bg-slate-50 rounded-lg border border-slate-100 flex flex-col md:flex-row md:items-center justify-between gap-4"
            >
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold text-slate-800">{tkt.campusName}</span>
                  <Badge variant={tkt.priority === "HIGH" ? "danger" : "warning"}>
                    {tkt.priority === "HIGH" ? "Khẩn cấp" : "Trung bình"}
                  </Badge>
                </div>
                <p className="text-xs text-slate-600 line-clamp-2">{tkt.content}</p>
              </div>

              <div className="flex items-center gap-3">
                <Badge variant={tkt.status === "NEW" ? "danger" : tkt.status === "IN_PROGRESS" ? "warning" : "success"}>
                  {tkt.status === "NEW" ? "Mới tiếp nhận" : tkt.status === "IN_PROGRESS" ? "Đang xử lý" : "Đã xử lý"}
                </Badge>
                <button
                  onClick={() => navigate("/facility/feedback")}
                  className="p-1 rounded bg-white hover:bg-slate-100 text-slate-500 border border-slate-200"
                >
                  <ArrowRight className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}
