import React from "react";
import UnderDevelopment from "@/components/ui/UnderDevelopment";
// 100% mock data (mockStudentExamSubmissions) — tạm ẩn theo yêu cầu người dùng (2026-07-27), sẽ
// phát triển tiếp ở giai đoạn sau, không xoá component cũ (GradingWorkshop).
// import { useState } from "react";
// import { StudentExamSubmission } from "@/types";
// import { mockStudentExamSubmissions } from "@/data/mockData";
// import Card from "@/components/ui/Card";
// import Badge from "@/components/ui/Badge";
// import { cn } from "@/lib/cn";
// import GradingWorkshop from "../components/GradingWorkshop";

export default function ExamsPage() {
  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Tài Liệu & Khảo Thí LMS (E-Learning)</h1>
        <p className="text-sm text-slate-500 mt-1">Chấm bài thủ công kỹ năng Nghe-Nói.</p>
      </div>

      <UnderDevelopment title="Hàng chờ chấm bài" />
    </div>
  );
}
