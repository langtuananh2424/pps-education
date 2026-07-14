import React, { useState } from "react";
import { Plus, Save } from "lucide-react";
import { Expense } from "@/types";
import { mockCampuses, mockExpenses } from "@/data/mockData";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";

export default function ExpensesPage() {
  const [expenses, setExpenses] = useState<Expense[]>(mockExpenses);
  const [showForm, setShowForm] = useState(false);
  const [category, setCategory] = useState<Expense["category"]>("SALARY");
  const [amount, setAmount] = useState("");
  const [description, setDescription] = useState("");
  const [campusId, setCampusId] = useState("CAMP-01");

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!amount || !description) return;

    const newExp: Expense = {
      id: `EXP-00${expenses.length + 1}`,
      category,
      amount: parseFloat(amount),
      description,
      campusId,
      createdAt: new Date().toISOString().substring(0, 10)
    };

    setExpenses((prev) => [newExp, ...prev]);
    setAmount("");
    setDescription("");
    setShowForm(false);
    alert("Đã ghi nhận khoản chi chi phí vận hành cơ sở vào sổ cái kế toán thành công (UC-31)!");
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Quản Lý Tài Chính & Sổ Cái Kế Toán</h1>
        <p className="text-xs text-slate-500 mt-1">Theo dõi thu chi phân cấp cơ sở (UC-31).</p>
      </div>

      <div className="flex items-center justify-between">
        <h3 className="text-sm font-bold text-slate-800 font-display">Chi phí vận hành và hạ tầng PPS (UC-31)</h3>
        <Button variant="primary" onClick={() => setShowForm(!showForm)}>
          <Plus className="w-4 h-4" />
          <span>Ghi nhận chi phí vận hành</span>
        </Button>
      </div>

      {showForm && (
        <form onSubmit={handleSubmit} className="bg-slate-50 p-4 rounded-xl border border-slate-200 grid grid-cols-1 md:grid-cols-4 gap-4 animate-in fade-in duration-150">
          <div className="space-y-1">
            <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Hạng mục chi phí</label>
            <select value={category} onChange={(e) => setCategory(e.target.value as Expense["category"])} className="w-full bg-white border border-slate-200 text-xs px-2.5 py-1.5 rounded-lg focus:outline-none">
              <option value="SALARY">Chi lương nhân viên / giảng viên</option>
              <option value="RENT">Chi thuê mặt bằng cơ sở</option>
              <option value="LICENSE">Bản quyền công nghệ / E-Learning</option>
              <option value="CDN_INFRA">Hạ tầng máy chủ CDN video</option>
              <option value="OTHER">Chi phí văn phòng phẩm hành chính</option>
            </select>
          </div>

          <div className="space-y-1">
            <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Mức tiền chi (VND)</label>
            <input
              type="number"
              required
              placeholder="Ví dụ: 12500000"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              className="w-full bg-white border border-slate-200 text-xs px-3 py-1.5 rounded-lg focus:outline-none font-mono"
            />
          </div>

          <div className="space-y-1">
            <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Chi cho cơ sở</label>
            <select value={campusId} onChange={(e) => setCampusId(e.target.value)} className="w-full bg-white border border-slate-200 text-xs px-2.5 py-1.5 rounded-lg focus:outline-none">
              {mockCampuses.map((camp) => (
                <option key={camp.id} value={camp.id}>
                  {camp.name}
                </option>
              ))}
            </select>
          </div>

          <div className="md:col-span-3 space-y-1">
            <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Mô tả tác vụ cụ thể</label>
            <input
              type="text"
              required
              placeholder="Nhập thông tin hóa đơn, biên lai chi tiền..."
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="w-full bg-white border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
            />
          </div>

          <div className="space-y-1 flex items-end">
            <button type="submit" className="w-full bg-slate-900 hover:bg-slate-800 text-white font-semibold text-xs py-2 px-3 rounded-lg flex items-center justify-center gap-1">
              <Save className="w-4 h-4 text-brand-yellow" />
              Xác nhận Chi tiền
            </button>
          </div>
        </form>
      )}

      <Card padded={false} className="overflow-hidden">
        <TableContainer className="rounded-none border-0">
          <thead>
            <tr>
              <Th>Mã số chi</Th>
              <Th>Thời gian</Th>
              <Th>Hạng mục chi</Th>
              <Th>Nội dung chi tiết</Th>
              <Th>Phân bổ điểm trường</Th>
              <Th className="text-right">Số tiền</Th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {expenses.map((exp) => (
              <tr key={exp.id} className="hover:bg-slate-50/60 transition-colors">
                <Td className="font-mono font-bold text-slate-500">{exp.id}</Td>
                <Td className="font-mono">{exp.createdAt}</Td>
                <Td className="font-bold text-brand-red">{exp.category}</Td>
                <Td className="font-medium">{exp.description}</Td>
                <Td>{exp.campusId ? mockCampuses.find((c) => c.id === exp.campusId)?.name || exp.campusId : "Toàn hệ thống dùng chung"}</Td>
                <Td className="font-mono font-bold text-right">-{exp.amount.toLocaleString("vi-VN")} đ</Td>
              </tr>
            ))}
          </tbody>
        </TableContainer>
      </Card>
    </div>
  );
}
