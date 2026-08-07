import React, { useState } from "react";
import { Download, FileText } from "lucide-react";
import Card from "@/components/ui/Card";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import Select from "@/components/ui/Select";
import { useDialog } from "@/components/ui/DialogProvider";

const teachingPlans = [
  { week: "Tuần 1", topic: "Alphabet & Phonetics Basic", hours: "4 Giờ", objectives: "Nhận biết âm đơn, bảng chữ cái" },
  { week: "Tuần 2", topic: "Greetings & Self Introduction", hours: "4 Giờ", objectives: "Chào hỏi và giới thiệu tên tuổi" },
  { week: "Tuần 3", topic: "My Classroom Objects", hours: "4 Giờ", objectives: "Nhận dạng dụng cụ học tập cơ bản" },
  { week: "Tuần 4", topic: "Colors & Shapes Around Me", hours: "4 Giờ", objectives: "Học từ vựng màu sắc, hình học" }
];

const stats: Record<string, { name: string; gpa: string; attendance: string; totalStuds: number; classes: number }> = {
  "CAMP-03": { name: "Tiểu Học Nghĩa Tân", gpa: "8.6", attendance: "98.2%", totalStuds: 45, classes: 2 },
  "CAMP-04": { name: "Mầm Non Hoa Hồng", gpa: "9.1", attendance: "97.4%", totalStuds: 30, classes: 1 }
};

export default function PartnerPortalPage() {
  const [partnerFilter, setPartnerFilter] = useState("CAMP-03");
  const activeStats = stats[partnerFilter] || stats["CAMP-03"];
  const { alertDialog } = useDialog();

  const handleDownloadReport = async () => {
    await alertDialog(
      `Đang khởi tạo tệp tin PDF Báo cáo liên kết định kỳ cho ${activeStats.name}...\n- Tổng số học sinh: ${activeStats.totalStuds} em\n- Điểm số trung bình: ${activeStats.gpa}\n- Tỷ lệ chuyên cần: ${activeStats.attendance}\nXuất file thành công!`
    );
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 border-b border-slate-200 pb-4">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Cổng Tra Cứu Đối Tác Liên Kết (Partner Portal)</h1>
          <p className="text-xs text-slate-500 mt-1">
            Giao diện dành riêng cho Đại diện Ban giám hiệu trường liên kết phối hợp xem kế hoạch giảng dạy định kỳ và báo cáo chất lượng.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <span className="text-xs font-semibold text-slate-500">Đại diện đơn vị:</span>
          <Select value={partnerFilter} onChange={(e) => setPartnerFilter(e.target.value)} className="bg-white border text-xs font-bold text-slate-700 px-3 py-1.5 rounded-lg focus:outline-none">
            <option value="CAMP-03">Tiểu học Nghĩa Tân (TH Nghĩa Tân)</option>
            <option value="CAMP-04">Mầm non Hoa Hồng (MN Hoa Hồng)</option>
          </Select>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
        <Card>
          <span className="text-[10px] text-slate-400 font-bold block uppercase tracking-wider">Tổng số học sinh</span>
          <div className="flex items-baseline gap-2 mt-2">
            <span className="text-2xl font-bold text-slate-900 font-display">{activeStats.totalStuds}</span>
            <span className="text-xs text-slate-400">học viên</span>
          </div>
        </Card>

        <Card>
          <span className="text-[10px] text-slate-400 font-bold block uppercase tracking-wider">Số lớp triển khai</span>
          <div className="flex items-baseline gap-2 mt-2">
            <span className="text-2xl font-bold text-slate-900 font-display">{activeStats.classes}</span>
            <span className="text-xs text-slate-400">lớp</span>
          </div>
        </Card>

        <Card>
          <span className="text-[10px] text-slate-400 font-bold block uppercase tracking-wider">Điểm TB học phần (GPA)</span>
          <div className="flex items-baseline gap-2 mt-2">
            <span className="text-2xl font-bold text-brand-orange font-mono">{activeStats.gpa}</span>
            <span className="text-xs text-slate-400">/ 10.0</span>
          </div>
        </Card>

        <Card>
          <span className="text-[10px] text-slate-400 font-bold block uppercase tracking-wider">Tỷ lệ Chuyên cần</span>
          <div className="flex items-baseline gap-2 mt-2">
            <span className="text-2xl font-bold text-emerald-600 font-display">{activeStats.attendance}</span>
            <span className="text-xs text-emerald-500 font-semibold">Tốt</span>
          </div>
        </Card>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card padded={false} className="lg:col-span-2 overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 flex items-center justify-between">
            <span className="text-xs font-bold text-slate-700 font-display">Kế hoạch & Đề cương giảng dạy</span>
            <span className="text-[10px] bg-slate-200 text-slate-600 px-2 py-0.5 rounded font-bold font-mono">Syllabus Approved</span>
          </div>

          <TableContainer className="rounded-none border-0">
            <thead>
              <tr>
                <Th>Thời gian</Th>
                <Th>Chủ đề bài học</Th>
                <Th>Thời lượng</Th>
                <Th>Mục tiêu đầu ra</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 font-medium">
              {teachingPlans.map((plan, idx) => (
                <tr key={idx} className="hover:bg-slate-50/40 transition-colors">
                  <Td className="font-bold text-slate-500">{plan.week}</Td>
                  <Td className="font-bold text-slate-900">{plan.topic}</Td>
                  <Td className="font-mono">{plan.hours}</Td>
                  <Td>{plan.objectives}</Td>
                </tr>
              ))}
            </tbody>
          </TableContainer>
        </Card>

        <Card className="space-y-4">
          <h3 className="text-xs font-bold text-slate-400 block uppercase tracking-wider font-display border-b border-slate-100 pb-2">Báo cáo hợp tác giáo dục</h3>
          <p className="text-xs text-slate-500 leading-relaxed">
            Hệ thống tự động biên soạn báo cáo phân tích định kỳ gồm: cơ cấu điểm số, tỷ lệ chuyên cần chuyên sâu để nhà trường kịp thời nắm bắt hiệu quả học tập.
          </p>

          <div className="bg-slate-50 p-3.5 rounded-lg border border-slate-100 text-xs font-medium space-y-3">
            <div className="flex items-center gap-2">
              <FileText className="w-5 h-5 text-brand-orange shrink-0" />
              <div>
                <span className="font-bold text-slate-800 block">Bao_Cao_Nghia_Tan_Q1.pdf</span>
                <span className="text-[10px] text-slate-400 font-normal">Tổng hợp bởi Phòng Đào tạo PPS Việt Nam</span>
              </div>
            </div>

            <button onClick={handleDownloadReport} className="w-full bg-brand-gradient hover:opacity-95 text-white font-semibold text-xs py-2 rounded-lg flex items-center justify-center gap-1.5 shadow-glow">
              <Download className="w-3.5 h-3.5 text-white" />
              Xuất báo cáo PDF
            </button>
          </div>
        </Card>
      </div>
    </div>
  );
}
