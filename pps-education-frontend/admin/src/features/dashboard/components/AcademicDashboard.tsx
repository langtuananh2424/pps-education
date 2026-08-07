import React from "react";
import { Award, AlertTriangle, BookOpen } from "lucide-react";
import { Classroom } from "@/types";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";

interface AcademicDashboardProps {
  classrooms: Classroom[];
}

const gradeDistribution = [
  { grade: "Dưới 5.0", pct: 5, color: "bg-slate-300" },
  { grade: "5.0 - 6.5", pct: 15, color: "bg-slate-400" },
  { grade: "6.5 - 8.0", pct: 45, color: "bg-brand-orange" },
  { grade: "8.0 - 9.0", pct: 25, color: "bg-brand-yellow" },
  { grade: "9.0 - 10", pct: 10, color: "bg-brand-red animate-pulse" }
];

export default function AcademicDashboard({ classrooms }: AcademicDashboardProps) {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Cổng Điều Hành Học Thuật & Đào Tạo</h1>
        <p className="text-sm text-slate-500 mt-1">
          Theo dõi phân công lớp học, tiến độ chấm thi trực tuyến và phê duyệt nhận xét định kỳ.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <Card className="flex items-center gap-4">
          <div className="p-3 rounded-lg bg-indigo-50 text-indigo-600">
            <BookOpen className="w-6 h-6" />
          </div>
          <div>
            <span className="text-sm text-slate-400 font-bold block uppercase tracking-wider font-display">Khóa học hiện hành</span>
            <span className="text-xl font-bold text-slate-800 font-display">{classrooms.length} lớp học</span>
          </div>
        </Card>

        <Card className="flex items-center gap-4">
          <div className="p-3 rounded-lg bg-emerald-50 text-emerald-600">
            <Award className="w-6 h-6" />
          </div>
          <div>
            <span className="text-sm text-slate-400 font-bold block uppercase tracking-wider font-display">Tỷ lệ chuyên cần trung bình</span>
            <span className="text-xl font-bold text-emerald-600 font-display">94.8%</span>
          </div>
        </Card>

        <Card className="flex items-center gap-4">
          <div className="p-3 rounded-lg bg-rose-50 text-rose-600">
            <AlertTriangle className="w-6 h-6" />
          </div>
          <div>
            <span className="text-sm text-slate-400 font-bold block uppercase tracking-wider font-display">Nhận xét chưa duyệt</span>
            <span className="text-xl font-bold text-rose-600 font-display">2 nhận xét</span>
          </div>
        </Card>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card>
          <h3 className="text-sm font-bold text-slate-800 font-display mb-3">Lịch phân công Giảng dạy & Phòng học</h3>
          <div className="divide-y divide-slate-100">
            {classrooms.map((cls) => (
              <div key={cls.id} className="py-3 flex items-center justify-between first:pt-0 last:pb-0">
                <div>
                  <span className="text-sm font-bold text-slate-800">{cls.name}</span>
                  <div className="flex items-center gap-1.5 text-sm text-slate-500 mt-1">
                    <span>Phòng: {cls.roomName}</span>
                    <span>•</span>
                    <span>Lịch: {cls.schedule}</span>
                  </div>
                </div>
                <Badge variant={cls.type === "AFFILIATED" ? "warning" : "info"}>
                  {cls.type === "AFFILIATED" ? "Liên kết trường" : "Tại trung tâm"}
                </Badge>
              </div>
            ))}
          </div>
        </Card>

        <Card className="flex flex-col justify-between">
          <div>
            <h3 className="text-sm font-bold text-slate-800 font-display">Biểu Đồ Phân Phối Điểm Số</h3>
            <p className="text-sm text-slate-400 mt-0.5">Tỷ lệ điểm trung bình học phần học kỳ hiện tại</p>
          </div>

          <div className="h-44 w-full flex items-end justify-between px-6 mt-4 border-b border-slate-100 pb-1">
            {gradeDistribution.map((bar) => (
              <div key={bar.grade} className="flex flex-col items-center gap-1 w-[15%]">
                <div className="h-32 w-full flex items-end justify-center">
                  <div
                    style={{ height: `${bar.pct * 2.5}px` }}
                    className={`w-5 sm:w-8 ${bar.color} rounded-t-xs relative group`}
                  >
                    <div className="absolute bottom-full mb-1 left-1/2 -translate-x-1/2 bg-slate-900 text-white text-[9px] px-1.5 py-0.5 rounded opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap">
                      {bar.pct}% học sinh
                    </div>
                  </div>
                </div>
                <span className="text-[9px] font-bold text-slate-500 whitespace-nowrap">{bar.grade}</span>
              </div>
            ))}
          </div>
        </Card>
      </div>
    </div>
  );
}
