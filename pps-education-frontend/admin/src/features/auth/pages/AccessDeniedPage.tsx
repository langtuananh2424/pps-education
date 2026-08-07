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
        <p className="text-sm text-slate-500 leading-relaxed max-w-md mx-auto">
          Vai trò hiện tại của bạn không được cấp mã quyền tương ứng để thao tác tại phân hệ{" "}
          {label ? <strong>“{label}”</strong> : "này"}. Đây là cơ chế kiểm soát truy cập dựa trên vai trò (RBAC) được cấu hình
          nghiêm ngặt theo tài liệu SRS.
        </p>
      </div>

      <div className="p-4 bg-slate-50 rounded-lg border text-left space-y-2">
        <div className="flex items-center gap-1.5 text-sm font-bold text-slate-800">
          <Laptop className="w-4 h-4 text-brand-orange" />
          <span>Làm thế nào để mở khóa?</span>
        </div>
        <p className="text-sm text-slate-600 leading-relaxed">
          Liên hệ <strong>Quản trị viên hệ thống</strong> để được cấp thêm quyền phù hợp (qua màn "Nhóm vai trò" hoặc
          "Tùy chỉnh tài khoản" trong Quản trị hệ thống), sau đó đăng nhập lại để áp dụng quyền mới.
        </p>
      </div>

      <Button variant="dark" onClick={() => navigate("/dashboard")} className="mx-auto">
        <span>Quay lại Dashboard chính</span>
        <ArrowRight className="w-4 h-4 text-brand-yellow" />
      </Button>
    </div>
  );
}
