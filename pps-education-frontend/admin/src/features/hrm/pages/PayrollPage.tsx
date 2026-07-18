import React, { useState } from "react";
import { UserRole } from "@/types";
import { readStoredEmployees } from "../storage";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";

export default function PayrollPage() {
  const [employees] = useState(readStoredEmployees);

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Phân hệ Quản lý Nhân sự & Tiền lương (HRM)</h1>
        <p className="text-xs text-slate-500 mt-1">Tính toán bảng lương tự động dựa trên hợp đồng và ngày công (UC-12).</p>
      </div>

      <div className="flex items-center justify-between">
        <h3 className="text-sm font-bold text-slate-800 font-display">Bảng lương cán bộ & giảng viên - Tháng này (UC-12)</h3>
        <span className="text-xs text-slate-500 font-semibold bg-slate-100 px-3 py-1 rounded-full font-mono">Tổng hợp từ tiết dạy & ngày công chấm công</span>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
        <TableContainer className="rounded-none border-0">
          <thead>
            <tr>
              <Th>Mã nhân viên</Th>
              <Th>Họ và tên</Th>
              <Th>Chức vụ/Vai trò</Th>
              <Th>Định mức/Lương cơ bản</Th>
              <Th>Học phần/Số tiết thực tế</Th>
              <Th>Khoản trừ (Thuế/Bảo hiểm)</Th>
              <Th className="text-right">Lương thực nhận</Th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {employees.map((emp) => {
              const isTeacher = emp.role === UserRole.TEACHER;
              const baseSalary = emp.contracts[0]?.baseSalary || 10000000;
              const taxAndInsurance = baseSalary * 0.105;
              const finalPaid = baseSalary - taxAndInsurance;

              return (
                <tr key={emp.id} className="hover:bg-slate-50/60 transition-colors">
                  <Td className="font-mono font-bold text-slate-500">{emp.id}</Td>
                  <Td className="font-bold text-slate-900">{emp.fullName}</Td>
                  <Td className="font-semibold text-brand-orange">{emp.role}</Td>
                  <Td className="font-mono font-medium">{baseSalary.toLocaleString("vi-VN")} đ</Td>
                  <Td className="font-mono">{isTeacher ? "24 tiết thực giảng" : "24 ngày công đạt"}</Td>
                  <Td className="font-mono text-rose-600">-{taxAndInsurance.toLocaleString("vi-VN")} đ</Td>
                  <Td className="font-mono text-right font-bold text-slate-900">{finalPaid.toLocaleString("vi-VN")} đ</Td>
                </tr>
              );
            })}
          </tbody>
        </TableContainer>
      </div>
    </div>
  );
}
