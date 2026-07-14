import React from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { ArrowRight, Laptop, Lock } from "lucide-react";
import Button from "@/components/ui/Button";

export default function AccessDeniedPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const label = (location.state as { label?: string } | null)?.label;

  return (
    <div className="bg-white p-8 rounded-xl border border-slate-200 shadow-soft max-w-2xl mx-auto text-center space-y-6 mt-12 animate-in fade-in duration-200">
      <div className="w-16 h-16 bg-rose-50 rounded-full flex items-center justify-center text-brand-red mx-auto shadow-sm">
        <Lock className="w-8 h-8" />
      </div>

      <div className="space-y-2">
        <h2 className="text-lg font-bold text-slate-900 font-display">Truy cập bị hạn chế (Role Security Guard)</h2>
        <p className="text-xs text-slate-500 leading-relaxed max-w-md mx-auto">
          Vai trò hiện tại của bạn không được cấp mã quyền tương ứng để thao tác tại phân hệ{" "}
          {label ? <strong>“{label}”</strong> : "này"}. Đây là cơ chế kiểm soát truy cập dựa trên vai trò (RBAC) được cấu hình
          nghiêm ngặt theo tài liệu SRS.
        </p>
      </div>

      <div className="p-4 bg-slate-50 rounded-lg border text-left space-y-2">
        <div className="flex items-center gap-1.5 text-xs font-bold text-slate-800">
          <Laptop className="w-4 h-4 text-brand-orange" />
          <span>Làm thế nào để mở khóa?</span>
        </div>
        <p className="text-[11px] text-slate-600 leading-relaxed">
          Nhấp chọn menu <strong>"Xem thử với vai trò"</strong> ở thanh tiêu đề phía trên bên phải màn hình và đổi sang vai trò
          quản lý tương ứng (như <strong>Quản trị viên</strong> hoặc <strong>Ban giám đốc</strong>) để ngay lập tức trải nghiệm
          phân hệ này mà không cần đăng nhập lại.
        </p>
      </div>

      <Button variant="dark" onClick={() => navigate("/dashboard")} className="mx-auto">
        <span>Quay lại Dashboard chính</span>
        <ArrowRight className="w-4 h-4 text-brand-yellow" />
      </Button>
    </div>
  );
}
