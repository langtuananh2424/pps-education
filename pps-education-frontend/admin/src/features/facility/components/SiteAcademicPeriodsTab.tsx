import { useState } from "react";
import { CalendarRange, GraduationCap } from "lucide-react";
import Button from "@/components/ui/Button";
import AcademicTermManagerModal from "@/features/academic/components/AcademicTermManagerModal";
import AcademicYearManagerModal from "@/features/academic/components/AcademicYearManagerModal";

/**
 * "Học kỳ & Năm học" — di dời từ trang "Quản lý lớp học" sang đây (bổ
 * sung ngoài SDD gốc, xác nhận với người dùng 2026-08-19), tái dùng
 * nguyên vẹn 2 modal cũ (AcademicTermManagerModal theo site,
 * AcademicYearManagerModal dùng chung toàn hệ thống).
 */
export default function SiteAcademicPeriodsTab({ siteId, siteName }: { siteId: number; siteName: string }) {
  const [termsOpen, setTermsOpen] = useState(false);
  const [yearsOpen, setYearsOpen] = useState(false);

  return (
    <div className="space-y-4">
      <div className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-1">
        <span className="text-[10px] uppercase font-bold text-slate-500">Học kỳ (theo điểm trường này)</span>
        <p className="text-xs text-slate-500">Quản lý danh sách kỳ học (mã, tên, khoảng ngày) riêng cho {siteName}.</p>
      </div>
      <Button size="sm" variant="secondary" onClick={() => setTermsOpen(true)}>
        <CalendarRange className="w-3.5 h-3.5" />
        Quản lý học kỳ
      </Button>

      <div className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-1">
        <span className="text-[10px] uppercase font-bold text-slate-500">Năm học (dùng chung toàn hệ thống)</span>
        <p className="text-xs text-slate-500">Danh mục năm học dùng chung cho mọi điểm trường (lớp/điểm/nhận xét đều tham chiếu).</p>
      </div>
      <Button size="sm" variant="secondary" onClick={() => setYearsOpen(true)}>
        <GraduationCap className="w-3.5 h-3.5" />
        Quản lý năm học
      </Button>

      {termsOpen && <AcademicTermManagerModal siteId={siteId} siteName={siteName} onClose={() => setTermsOpen(false)} />}
      {yearsOpen && <AcademicYearManagerModal onClose={() => setYearsOpen(false)} />}
    </div>
  );
}
