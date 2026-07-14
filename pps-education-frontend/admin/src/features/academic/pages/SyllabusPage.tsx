import React, { useState } from "react";
import { CheckCircle, Info } from "lucide-react";
import { mockCourses } from "@/data/mockData";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";

export default function SyllabusPage() {
  const [syllabusStatus, setSyllabusStatus] = useState<"ORIGINAL" | "PENDING_CUSTOM" | "APPROVED_CUSTOM">("ORIGINAL");

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Học Thuật & Quy Chuẩn Đào Tạo (Academic)</h1>
        <p className="text-xs text-slate-500 mt-1">Quy định khung chương trình học và đề xuất tùy biến cho trường liên kết (UC-16/16b/17).</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-4">
          <h3 className="text-sm font-bold text-slate-800 font-display">Thiết lập cấu trúc điểm & khung giáo trình (UC-16)</h3>

          <Card className="space-y-4">
            <div className="flex items-center justify-between border-b pb-2">
              <span className="text-xs font-bold text-slate-800 font-display">Giáo trình gốc: PPS Junior Starter</span>
              <span className="text-[10px] bg-slate-900 text-white px-2 py-0.5 rounded font-mono font-bold">Chuẩn Quốc Tế</span>
            </div>

            <div className="text-xs text-slate-600 space-y-2 leading-relaxed">
              <p>
                • <strong>Cấp độ:</strong> Junior Starter J1A (Dành cho lứa tuổi 6-8 tuổi)
              </p>
              <p>
                • <strong>Công thức tính điểm trung bình học phần (Average):</strong> <code>(Giữa kỳ × 50%) + (Cuối kỳ × 50%)</code>
              </p>
              <p>
                • <strong>Chuẩn đầu ra cam kết:</strong> Đạt chứng chỉ Cambridge Starters khi kết thúc 4 khóa.
              </p>
            </div>

            <div className="bg-slate-50 p-4 rounded-lg border border-slate-100 space-y-2">
              <span className="text-[10px] font-bold text-slate-400 block uppercase font-mono">Đề xuất tùy biến cho Trường liên kết (UC-16b)</span>
              {syllabusStatus === "ORIGINAL" && (
                <div className="space-y-3">
                  <p className="text-[11px] text-slate-500">
                    Cơ sở liên kết Nghĩa Tân đề xuất tích hợp thêm học liệu kỹ năng Nói phản xạ, sửa tỷ lệ tính điểm giữa kỳ còn 40% và nói 20%.
                  </p>
                  <Button variant="dark" size="sm" onClick={() => setSyllabusStatus("PENDING_CUSTOM")}>
                    Đề xuất tùy biến khung lên phòng Đào tạo
                  </Button>
                </div>
              )}

              {syllabusStatus === "PENDING_CUSTOM" && (
                <div className="space-y-3">
                  <div className="flex items-center gap-1.5 text-xs font-semibold text-amber-600">
                    <Info className="w-4 h-4" />
                    <span>Đang chờ duyệt tùy biến chương trình bởi Trưởng phòng đào tạo (UC-17)</span>
                  </div>
                  <Button variant="primary" size="sm" onClick={() => setSyllabusStatus("APPROVED_CUSTOM")}>
                    Duyệt phê duyệt tùy biến (HEAD_ACADEMIC action)
                  </Button>
                </div>
              )}

              {syllabusStatus === "APPROVED_CUSTOM" && (
                <div className="p-3 bg-emerald-50 border border-emerald-100 text-emerald-800 rounded-lg text-xs font-semibold flex items-center gap-2">
                  <CheckCircle className="w-5 h-5 text-emerald-600 shrink-0" />
                  <span>Đã phê duyệt tùy biến chương trình! Áp dụng chính thức cho Điểm trường Nghĩa Tân.</span>
                </div>
              )}
            </div>
          </Card>
        </div>

        <Card>
          <h3 className="text-xs font-bold text-slate-400 block uppercase tracking-wider font-display border-b border-slate-100 pb-2 mb-4">Danh sách giáo trình đào tạo PPS</h3>
          <div className="space-y-2.5">
            {mockCourses.map((crs) => (
              <div key={crs.id} className="p-3 bg-slate-50 border rounded-lg flex items-center justify-between">
                <div>
                  <span className="text-xs font-bold text-slate-800 block">{crs.name}</span>
                  <span className="text-[10px] text-slate-400 block font-mono font-semibold mt-0.5">{crs.code}</span>
                </div>
              </div>
            ))}
          </div>
        </Card>
      </div>
    </div>
  );
}
