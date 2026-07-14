import React, { useState } from "react";
import { ClassroomRoom } from "@/types";
import { mockCampuses, mockClassroomRooms } from "@/data/mockData";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import { cn } from "@/lib/cn";

export default function RoomsPage() {
  const [rooms, setRooms] = useState<ClassroomRoom[]>(mockClassroomRooms);

  const handleToggleFlexibility = (roomId: string) => {
    const room = rooms.find((r) => r.id === roomId);
    setRooms((prev) => prev.map((rm) => (rm.id === roomId ? { ...rm, isFlexible: !rm.isFlexible } : rm)));
    alert(
      `Đã cập nhật cấu hình phòng học!\n- Phòng được đổi tính chất sang ${!room?.isFlexible ? "LINH HOẠT" : "CỐ ĐỊNH"}.\n- Phòng linh hoạt sẽ tự động loại trừ khỏi cảnh báo trùng lịch xếp lớp học vụ (FR-FAC-03).`
    );
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Quản Lý Điểm Trường, Phòng Học & Đối Tác</h1>
        <p className="text-xs text-slate-500 mt-1">Quản lý ca thiết bị phòng học và cấu hình phòng linh hoạt (UC-37).</p>
      </div>

      <div className="flex items-center justify-between">
        <h3 className="text-sm font-bold text-slate-800 font-display">Sơ đồ phòng học cơ sở</h3>
        <span className="text-xs text-slate-400 italic">Nhấp 'Linh hoạt' để loại trừ phòng khỏi ràng buộc kiểm tra trùng giờ dạy.</span>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5">
        {rooms.map((room) => {
          const camp = mockCampuses.find((c) => c.id === room.campusId);
          return (
            <Card key={room.id} className="flex flex-col justify-between">
              <div>
                <div className="flex items-start justify-between gap-2 border-b pb-2">
                  <span className="text-xs font-bold text-slate-900 truncate">{room.roomName}</span>
                  <Badge variant={room.status === "AVAILABLE" ? "success" : "neutral"}>{room.status}</Badge>
                </div>

                <div className="text-[11px] text-slate-500 space-y-1.5 mt-3">
                  <p>• Cơ sở: {camp?.name}</p>
                  <p>• Sức chứa: {room.capacity} học sinh</p>
                  <p>• Loại phòng: {room.type}</p>
                </div>
              </div>

              <div className="border-t border-slate-100 pt-3 flex items-center justify-between">
                <span className="text-[10px] text-slate-400 font-bold block uppercase tracking-wider">Lịch hoạt (Flexible)</span>
                <button
                  onClick={() => handleToggleFlexibility(room.id)}
                  className={cn("px-3 py-1 rounded text-[10px] font-bold transition-all", room.isFlexible ? "bg-brand-gradient text-white shadow-soft" : "bg-slate-100 text-slate-600 hover:bg-slate-200")}
                >
                  {room.isFlexible ? "Linh hoạt (BẬT)" : "Mặc định (TẮT)"}
                </button>
              </div>
            </Card>
          );
        })}
      </div>
    </div>
  );
}
