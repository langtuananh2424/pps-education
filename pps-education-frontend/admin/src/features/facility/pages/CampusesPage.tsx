import React from "react";
import { Building2, MapPin, Sliders } from "lucide-react";
import { mockCampuses } from "@/data/mockData";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";

export default function CampusesPage() {
  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Quản Lý Điểm Trường, Phòng Học & Đối Tác</h1>
        <p className="text-xs text-slate-500 mt-1">Khai báo cơ sở tự vận hành và hợp đồng đối tác trường liên kết (UC-36/36b).</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {mockCampuses.map((camp) => (
          <Card key={camp.id} className="flex flex-col justify-between">
            <div className="space-y-3.5">
              <div className="flex items-start gap-3 border-b pb-3">
                <div className="p-2.5 rounded-lg bg-slate-900 text-brand-orange shrink-0">
                  <Building2 className="w-6 h-6" />
                </div>
                <div>
                  <h4 className="text-xs font-bold text-slate-900">{camp.name}</h4>
                  <span className="text-[10px] text-slate-400 font-mono mt-0.5 block">Mã cơ sở: {camp.id}</span>
                </div>
              </div>

              <div className="space-y-2 text-xs text-slate-600">
                <p className="flex items-center gap-1.5">
                  <MapPin className="w-4 h-4 text-slate-400 shrink-0" />
                  <span>Địa chỉ: {camp.address}</span>
                </p>
                <p className="flex items-center gap-1.5">
                  <Building2 className="w-4 h-4 text-slate-400 shrink-0" />
                  <span>Hạng mục: {camp.type === "SELF_OPERATED" ? "Cơ sở Tự vận hành" : "Trường liên kết giáo dục"}</span>
                </p>
                <p className="flex items-center gap-1.5">
                  <Sliders className="w-4 h-4 text-slate-400 shrink-0" />
                  <span>Phụ trách chính: {camp.managerName}</span>
                </p>
              </div>

              {camp.type === "AFFILIATED" && (
                <div className="bg-slate-50 p-3 rounded-lg border border-slate-100 space-y-1.5 text-[11px] leading-relaxed">
                  <div className="flex items-center justify-between font-semibold">
                    <span className="text-slate-500">Người đại diện liên hệ:</span>
                    <span className="text-slate-800">{camp.contactPerson}</span>
                  </div>

                  <div className="flex items-center justify-between font-semibold">
                    <span className="text-slate-500">Trạng thái hợp đồng hợp tác:</span>
                    <Badge variant={camp.contractStatus === "ACTIVE" ? "success" : "warning"}>{camp.contractStatus === "ACTIVE" ? "ĐANG HIỆU LỰC" : "ĐANG CHỜ GIA HẠN"}</Badge>
                  </div>

                  <div className="flex items-center justify-between text-slate-500">
                    <span>Thời hạn hết hạn hợp đồng:</span>
                    <span className="font-mono font-bold text-slate-700">{camp.contractExpiryDate}</span>
                  </div>
                </div>
              )}
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}
