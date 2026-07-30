import React from "react";
import UnderDevelopment from "@/components/ui/UnderDevelopment";
// 100% mock data (mockInvoices, webhook giả lập qua alert()) — tạm ẩn theo yêu cầu người dùng
// (2026-07-23), sẽ phát triển tiếp ở giai đoạn sau, không xoá component cũ (InvoiceQrPanel).
// import { useState } from "react";
// import { QrCode } from "lucide-react";
// import { Invoice } from "@/types";
// import { mockInvoices } from "@/data/mockData";
// import Card from "@/components/ui/Card";
// import Badge from "@/components/ui/Badge";
// import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
// import InvoiceQrPanel from "../components/InvoiceQrPanel";

export default function BillingPage() {
  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Quản Lý Tài Chính & Sổ Cái Kế Toán</h1>
        <p className="text-xs text-slate-500 mt-1">Xuất hóa đơn học phí, quét mã QR gạch nợ tự động trực tuyến.</p>
      </div>

      <UnderDevelopment title="Thu phí & hóa đơn" />
    </div>
  );
}
