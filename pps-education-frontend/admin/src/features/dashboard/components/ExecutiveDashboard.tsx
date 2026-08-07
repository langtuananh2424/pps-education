import React from "react";
import { useNavigate } from "react-router-dom";
import { AlertTriangle, ArrowRight, Calendar } from "lucide-react";
import { mockLeads } from "@/data/mockData";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";

interface ExecutiveDashboardProps {
  totalPaid: number;
  totalExpenses: number;
  profit: number;
  activeStudentsCount: number;
  campusesCount: number;
}

const revenueSeries = [
  { month: "T12/25", in: 120, out: 95 },
  { month: "T1/26", in: 150, out: 100 },
  { month: "T2/26", in: 95, out: 90 },
  { month: "T3/26", in: 180, out: 110 },
  { month: "T4/26", in: 210, out: 125 },
  { month: "T5/26", in: 160, out: 115 },
  { month: "T6/26", in: 240, out: 140 }
];

export default function ExecutiveDashboard({ totalPaid, totalExpenses, profit, activeStudentsCount, campusesCount }: ExecutiveDashboardProps) {
  const navigate = useNavigate();
  const chartData = [...revenueSeries, { month: "Tháng này", in: totalPaid / 1_000_000, out: totalExpenses / 1_000_000 }];
  const maxVal = 260;

  const funnel = [
    { stage: "Leads mới nhận", count: mockLeads.length, percentage: 100, color: "bg-slate-800" },
    { stage: "Đã liên hệ cuộc gọi", count: mockLeads.filter((l) => l.status !== "NEW").length, percentage: 80, color: "bg-brand-orange" },
    { stage: "Đủ điều kiện (Qualified)", count: mockLeads.filter((l) => ["QUALIFIED", "WON"].includes(l.status)).length, percentage: 60, color: "bg-brand-yellow" },
    { stage: "Chuyển khoản thành công (Won)", count: mockLeads.filter((l) => l.status === "WON").length, percentage: 33, color: "bg-brand-red animate-pulse" }
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Báo cáo Tổng hợp Vận hành & Doanh nghiệp</h1>
          <p className="text-sm text-slate-500 mt-1">Phân tích tài chính, nhân sự và hiệu suất tuyển sinh trên toàn chuỗi PPS Education.</p>
        </div>
        <div className="flex items-center gap-2 text-sm font-medium text-slate-600 bg-white border border-slate-200 px-3 py-2 rounded-lg">
          <Calendar className="w-4 h-4 text-brand-orange" />
          <span>Kỳ báo cáo: Tháng 7/2026</span>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card className="relative overflow-hidden">
          <span className="text-sm text-slate-400 font-bold block uppercase tracking-wider font-display">Doanh thu thực tế</span>
          <div className="flex items-baseline gap-1.5 mt-2">
            <span className="text-2xl font-bold text-slate-900 font-display">{totalPaid.toLocaleString("vi-VN")}</span>
            <span className="text-sm font-semibold text-slate-500">VND</span>
          </div>
          <div className="flex items-center gap-1 mt-2 text-sm font-semibold text-emerald-600">
            <span>+14.2% so với tháng trước</span>
          </div>
        </Card>

        <Card className="relative overflow-hidden">
          <span className="text-sm text-slate-400 font-bold block uppercase tracking-wider font-display">Chi phí vận hành</span>
          <div className="flex items-baseline gap-1.5 mt-2">
            <span className="text-2xl font-bold text-slate-900 font-display">{totalExpenses.toLocaleString("vi-VN")}</span>
            <span className="text-sm font-semibold text-slate-500">VND</span>
          </div>
          <div className="flex items-center gap-1 mt-2 text-sm font-semibold text-slate-500">
            <span>Bao gồm lương & hạ tầng CDN</span>
          </div>
        </Card>

        <Card className="relative overflow-hidden">
          <span className="text-sm text-slate-400 font-bold block uppercase tracking-wider font-display">Tỷ suất lợi nhuận</span>
          <div className="flex items-baseline gap-1.5 mt-2">
            <span className="text-2xl font-bold text-slate-900 font-display">
              {profit > 0 ? `+${((profit / totalPaid) * 100).toFixed(1)}%` : "Chờ cập nhật"}
            </span>
          </div>
          <div className="flex items-center gap-1 mt-2 text-sm font-semibold text-brand-orange">
            <span>Đạt chỉ tiêu kế hoạch năm</span>
          </div>
        </Card>

        <Card className="relative overflow-hidden">
          <span className="text-sm text-slate-400 font-bold block uppercase tracking-wider font-display">Học viên hoạt động</span>
          <div className="flex items-baseline gap-1.5 mt-2">
            <span className="text-2xl font-bold text-slate-900 font-display">{activeStudentsCount}</span>
            <span className="text-sm font-medium text-slate-500">học viên</span>
          </div>
          <div className="flex items-center gap-1 mt-2 text-sm font-semibold text-slate-500">
            <span>Phân bổ trên {campusesCount} điểm trường</span>
          </div>
        </Card>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card className="lg:col-span-2 flex flex-col justify-between">
          <div>
            <h3 className="text-sm font-bold text-slate-800 font-display">Thống kê Thu Chi Định Kỳ</h3>
            <p className="text-sm text-slate-400 mt-0.5">Biểu đồ so sánh dòng tiền thu học phí và chi phí vận hành hàng tháng</p>
          </div>

          <div className="h-60 w-full mt-4 flex items-end justify-between relative px-2 border-b border-slate-100 pb-1 overflow-x-auto">
            {chartData.map((data, index) => {
              const inHeight = (data.in / maxVal) * 100;
              const outHeight = (data.out / maxVal) * 100;
              return (
                <div key={index} className="flex flex-col items-center gap-1 z-10 min-w-[10%]">
                  <div className="flex gap-1.5 h-44 items-end w-full justify-center">
                    <div
                      style={{ height: `${inHeight}%` }}
                      className="w-3 sm:w-4 bg-brand-gradient rounded-t-xs transition-all duration-500 cursor-pointer hover:opacity-90 relative group"
                    >
                      <div className="absolute bottom-full mb-1 left-1/2 -translate-x-1/2 bg-slate-900 text-white text-[9px] px-1.5 py-0.5 rounded opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none whitespace-nowrap">
                        {data.in.toFixed(0)}M VND
                      </div>
                    </div>
                    <div
                      style={{ height: `${outHeight}%` }}
                      className="w-3 sm:w-4 bg-slate-300 rounded-t-xs transition-all duration-500 cursor-pointer hover:bg-slate-400 relative group"
                    >
                      <div className="absolute bottom-full mb-1 left-1/2 -translate-x-1/2 bg-slate-900 text-white text-[9px] px-1.5 py-0.5 rounded opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none whitespace-nowrap">
                        {data.out.toFixed(0)}M VND
                      </div>
                    </div>
                  </div>
                  <span className="text-sm font-semibold text-slate-500 mt-1 font-sans whitespace-nowrap">{data.month}</span>
                </div>
              );
            })}
          </div>

          <div className="flex items-center gap-5 mt-4 text-sm font-medium justify-center border-t border-slate-100 pt-3">
            <div className="flex items-center gap-1.5">
              <span className="w-2.5 h-2.5 rounded-full bg-brand-gradient" />
              <span className="text-slate-600">Học phí thực thu (M VND)</span>
            </div>
            <div className="flex items-center gap-1.5">
              <span className="w-2.5 h-2.5 rounded-full bg-slate-300" />
              <span className="text-slate-600">Chi phí vận hành (M VND)</span>
            </div>
          </div>
        </Card>

        <Card className="flex flex-col justify-between">
          <div>
            <h3 className="text-sm font-bold text-slate-800 font-display">Phễu Chuyển Đổi Tuyển Sinh (CRM)</h3>
            <p className="text-sm text-slate-400 mt-0.5">Thống kê lượng lead tư vấn qua các bước trong tuần này</p>
          </div>

          <div className="space-y-3.5 my-4">
            {funnel.map((f) => (
              <div key={f.stage} className="space-y-1">
                <div className="flex items-center justify-between text-sm font-semibold">
                  <span className="text-slate-600">{f.stage}</span>
                  <span className="text-slate-900 font-mono">{f.count} học sinh</span>
                </div>
                <div className="w-full bg-slate-100 h-2.5 rounded-full overflow-hidden">
                  <div style={{ width: `${f.percentage}%` }} className={`h-full rounded-full ${f.color}`} />
                </div>
              </div>
            ))}
          </div>

          <Button variant="secondary" onClick={() => navigate("/crm/leads")} className="w-full">
            <span>Xem chi tiết danh sách Leads</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </Button>
        </Card>
      </div>

      <div className="bg-brand-gradient/5 border border-brand-orange/20 rounded-xl p-4 flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div className="flex items-start gap-3">
          <AlertTriangle className="w-5 h-5 text-brand-orange mt-0.5 shrink-0" />
          <div>
            <h4 className="text-sm font-bold text-slate-800 font-display">Thông báo Gia hạn Hợp tác Trường liên kết</h4>
            <p className="text-sm text-slate-600 mt-1">
              Hợp đồng liên kết với <strong>Trường THCS Lê Quý Đôn</strong> sắp đến ngày gia hạn <strong>(31/08/2026)</strong>. Hệ
              thống tự động khóa xếp lịch nếu quá hạn.
            </p>
          </div>
        </div>
        <Button variant="secondary" onClick={() => navigate("/facility/campuses")} className="whitespace-nowrap">
          Quản lý hợp đồng đối tác
        </Button>
      </div>
    </div>
  );
}
