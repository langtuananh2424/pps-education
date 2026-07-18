import React from "react";
import { mockCampuses, mockExpenses, mockInvoices } from "@/data/mockData";
import Card from "@/components/ui/Card";

export default function ReportsPage() {
  const totalPaid = mockInvoices.filter((i) => i.status === "PAID").reduce((sum, item) => sum + item.finalAmount, 0);
  const totalExp = mockExpenses.reduce((sum, item) => sum + item.amount, 0);

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Quản Lý Tài Chính & Sổ Cái Kế Toán</h1>
        <p className="text-xs text-slate-500 mt-1">Báo cáo dòng tiền tổng hợp & phân cấp theo cơ sở (UC-32).</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 text-center">
        <Card>
          <span className="text-[10px] text-slate-400 font-bold block uppercase tracking-wider">Tổng học phí đã thu</span>
          <span className="text-2xl font-bold text-emerald-600 font-display mt-2 block">{totalPaid.toLocaleString("vi-VN")} đ</span>
        </Card>
        <Card>
          <span className="text-[10px] text-slate-400 font-bold block uppercase tracking-wider">Tổng chi phí vận hành</span>
          <span className="text-2xl font-bold text-rose-600 font-display mt-2 block font-mono">{totalExp.toLocaleString("vi-VN")} đ</span>
        </Card>
        <Card>
          <span className="text-[10px] text-slate-400 font-bold block uppercase tracking-wider">Thặng dư / Lợi nhuận</span>
          <span className="text-2xl font-bold text-slate-900 font-display mt-2 block">{(totalPaid - totalExp).toLocaleString("vi-VN")} đ</span>
        </Card>
      </div>

      <Card>
        <h3 className="text-sm font-bold text-slate-800 font-display mb-4">Biểu đồ thặng dư phân cấp theo cơ sở</h3>

        <div className="space-y-5">
          {mockCampuses.map((camp) => {
            const shareIn = camp.type === "SELF_OPERATED" ? totalPaid * 0.45 : totalPaid * 0.15;
            const shareOut = camp.type === "SELF_OPERATED" ? totalExp * 0.4 : totalExp * 0.1;
            const balance = shareIn - shareOut;

            return (
              <div key={camp.id} className="space-y-2">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-xs font-semibold">
                  <span className="text-slate-800">{camp.name}</span>
                  <span className={`font-mono ${balance > 0 ? "text-emerald-600" : "text-rose-600"}`}>Thặng dư: {balance.toLocaleString("vi-VN")} đ</span>
                </div>

                <div className="flex w-full bg-slate-100 h-6 rounded-lg overflow-hidden text-[10px] font-bold text-white text-center">
                  <div style={{ width: `${(shareIn / (shareIn + shareOut)) * 100}%` }} className="bg-brand-gradient h-full flex items-center justify-center whitespace-nowrap px-1">
                    Thu: {shareIn.toLocaleString("vi-VN")} đ
                  </div>
                  <div style={{ width: `${(shareOut / (shareIn + shareOut)) * 100}%` }} className="bg-slate-400 h-full flex items-center justify-center whitespace-nowrap px-1">
                    Chi: {shareOut.toLocaleString("vi-VN")} đ
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </Card>
    </div>
  );
}
