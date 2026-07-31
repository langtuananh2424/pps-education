import React, { useEffect, useState } from "react";
import { Plus, Wrench } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import {
  CreateEquipmentRequest,
  CreateRoomRequest,
  EquipmentResponse,
  EquipmentType,
  RoomResponse,
  RoomStatus,
  RoomType,
  SiteResponse,
  createEquipment,
  createRoom,
  listEquipmentByRoom,
  listRoomsBySite,
  listSites,
  updateEquipmentStatus,
  updateRoom
} from "../api";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Modal from "@/components/ui/Modal";
import { cn } from "@/lib/cn";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import Select from "@/components/ui/Select";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

const roomTypeLabels: Record<RoomType, string> = { THEORY: "Lý thuyết", COMPUTER: "Phòng máy", LAB: "Lab/Thực hành", OTHER: "Khác" };
const roomStatusLabels: Record<RoomStatus, string> = { AVAILABLE: "Khả dụng", MAINTENANCE: "Đang bảo trì", DISABLED: "Ngừng sử dụng" };
const roomStatusVariants: Record<RoomStatus, BadgeVariant> = { AVAILABLE: "success", MAINTENANCE: "warning", DISABLED: "neutral" };

const equipmentTypeLabels: Record<EquipmentType, string> = { PROJECTOR: "Máy chiếu", SPEAKER: "Loa", MIC: "Micro", COMPUTER: "Máy tính", OTHER: "Khác" };
const equipmentStatusLabels: Record<string, string> = { AVAILABLE: "Khả dụng", IN_USE: "Đang dùng", MAINTENANCE: "Bảo trì", BROKEN: "Hỏng" };
const equipmentStatusVariants: Record<string, BadgeVariant> = { AVAILABLE: "success", IN_USE: "info", MAINTENANCE: "warning", BROKEN: "danger" };

/** UC-37: Quản lý phòng học + thiết bị dạy học (FR-FAC-04/FR-FAC-02) — cả 2 đã có sẵn API thật. */
export default function RoomsPage() {
  const { selectedCampusId } = useApp();
  const [sites, setSites] = useState<SiteResponse[]>([]);
  const [rooms, setRooms] = useState<RoomResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [equipmentRoom, setEquipmentRoom] = useState<RoomResponse | null>(null);
  const { message: toastMessage, showToast } = useToast();

  const siteName = (id: number) => sites.find((s) => s.id === id)?.name ?? `Site #${id}`;

  /** Chọn đúng 1 điểm trường ở header (selectedCampusId khác "ALL") thì chỉ tải phòng của site đó — "Tất cả" mới gộp mọi site. */
  const load = () => {
    setLoading(true);
    setError(null);
    listSites()
      .then(async (allSites) => {
        setSites(allSites);
        if (selectedCampusId !== "ALL") {
          const rooms = await listRoomsBySite(Number(selectedCampusId)).catch(() => [] as RoomResponse[]);
          setRooms(rooms);
          return;
        }
        const roomsBySite = await Promise.all(allSites.map((s) => listRoomsBySite(s.id).catch(() => [] as RoomResponse[])));
        setRooms(roomsBySite.flat());
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách phòng học."))
      .finally(() => setLoading(false));
  };

  useEffect(load, [selectedCampusId]);

  const handleToggleFlexibility = async (room: RoomResponse) => {
    setError(null);
    try {
      const updated = await updateRoom(room.id, {
        name: room.name,
        capacity: room.capacity,
        flexible: !room.flexible,
        managedByCenter: room.managedByCenter,
        status: room.status as RoomStatus,
        notes: room.notes ?? undefined
      });
      setRooms((prev) => prev.map((r) => (r.id === room.id ? updated : r)));
      showToast("Đã cập nhật cấu hình phòng học thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Cập nhật cấu hình phòng học thất bại.");
    }
  };

  if (loading) return <p className="text-xs text-slate-500">Đang tải...</p>;

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Quản Lý Điểm Trường, Phòng Học & Đối Tác</h1>
        <p className="text-xs text-slate-500 mt-1">Quản lý phòng học và thiết bị dạy học.</p>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="flex items-center justify-between flex-wrap gap-2">
        <h3 className="text-sm font-bold text-slate-800 font-display">Sơ đồ phòng học cơ sở</h3>
        <div className="flex items-center gap-3">
          <span className="text-xs text-slate-400 italic">Nhấp 'Linh hoạt' để loại trừ phòng khỏi ràng buộc kiểm tra trùng giờ dạy.</span>
          <Button size="sm" variant="primary" onClick={() => setShowCreateForm(true)}>
            <Plus className="w-3.5 h-3.5" />
            Thêm phòng
          </Button>
        </div>
      </div>

      {rooms.length === 0 ? (
        <p className="text-xs text-slate-400 italic text-center py-10">Chưa có phòng học nào được khai báo.</p>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5">
          {rooms.map((room) => {
            const status = room.status as RoomStatus;
            return (
              <Card key={room.id} className="flex flex-col justify-between">
                <div>
                  <div className="flex items-start justify-between gap-2 border-b pb-2">
                    <span className="text-xs font-bold text-slate-900 truncate">{room.name || room.code}</span>
                    <Badge variant={roomStatusVariants[status] ?? "neutral"}>{roomStatusLabels[status] ?? status}</Badge>
                  </div>

                  <div className="text-[11px] text-slate-500 space-y-1.5 mt-3">
                    <p>• Cơ sở: {siteName(room.siteId)}</p>
                    <p>• Sức chứa: {room.capacity} học sinh</p>
                    <p>• Loại phòng: {roomTypeLabels[room.roomType as RoomType] ?? room.roomType}</p>
                  </div>
                </div>

                <div className="border-t border-slate-100 pt-3 flex items-center justify-between gap-2">
                  <button onClick={() => setEquipmentRoom(room)} className="text-[10px] font-bold text-slate-500 hover:text-brand-orange flex items-center gap-1">
                    <Wrench className="w-3 h-3" /> Thiết bị
                  </button>
                  <button
                    onClick={() => handleToggleFlexibility(room)}
                    className={cn("px-3 py-1 rounded text-[10px] font-bold transition-all", room.flexible ? "bg-brand-gradient text-white shadow-soft" : "bg-slate-100 text-slate-600 hover:bg-slate-200")}
                  >
                    {room.flexible ? "Linh hoạt (BẬT)" : "Mặc định (TẮT)"}
                  </button>
                </div>
              </Card>
            );
          })}
        </div>
      )}

      {showCreateForm && (
        <CreateRoomModal
          sites={sites}
          defaultSiteId={selectedCampusId !== "ALL" ? selectedCampusId : undefined}
          onClose={() => setShowCreateForm(false)}
          onCreated={(room) => {
            setRooms((prev) => (selectedCampusId === "ALL" || room.siteId === Number(selectedCampusId) ? [...prev, room] : prev));
            setShowCreateForm(false);
            showToast("Đã tạo phòng học thành công!");
          }}
        />
      )}

      {equipmentRoom && <EquipmentModal room={equipmentRoom} onClose={() => setEquipmentRoom(null)} />}

      <Toast message={toastMessage} />
    </div>
  );
}

function CreateRoomModal({
  sites,
  defaultSiteId,
  onClose,
  onCreated
}: {
  sites: SiteResponse[];
  defaultSiteId?: string;
  onClose: () => void;
  onCreated: (room: RoomResponse) => void;
}) {
  const [form, setForm] = useState({
    siteId: defaultSiteId ?? (sites[0]?.id ? String(sites[0].id) : ""),
    code: "",
    name: "",
    roomType: "THEORY" as RoomType,
    capacity: "25",
    flexible: false,
    managedByCenter: true
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.siteId || !form.code.trim() || !form.capacity) {
      setError("Vui lòng điền đủ điểm trường, mã phòng và sức chứa.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: CreateRoomRequest = {
        siteId: Number(form.siteId),
        code: form.code.trim(),
        name: form.name.trim() || undefined,
        roomType: form.roomType,
        capacity: Number(form.capacity),
        flexible: form.flexible,
        managedByCenter: form.managedByCenter
      };
      const created = await createRoom(request);
      onCreated(created);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Tạo phòng học thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title="Thêm phòng học mới" size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Điểm trường *</label>
            <Select value={form.siteId} onChange={(e) => setForm({ ...form, siteId: e.target.value })} className={inputClass}>
              {sites.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className={labelClass}>Mã phòng *</label>
            <input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} placeholder="VD: 201" className={inputClass} required />
          </div>
        </div>
        <div>
          <label className={labelClass}>Tên phòng</label>
          <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="VD: Phòng 201 (Lý thuyết)" className={inputClass} />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Loại phòng *</label>
            <Select value={form.roomType} onChange={(e) => setForm({ ...form, roomType: e.target.value as RoomType })} className={inputClass}>
              {Object.entries(roomTypeLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className={labelClass}>Sức chứa *</label>
            <input type="number" min={1} value={form.capacity} onChange={(e) => setForm({ ...form, capacity: e.target.value })} className={inputClass} required />
          </div>
        </div>
        <div className="flex gap-4">
          <label className="flex items-center gap-1.5 text-xs font-semibold text-slate-600">
            <input type="checkbox" checked={form.flexible} onChange={(e) => setForm({ ...form, flexible: e.target.checked })} />
            Phòng linh hoạt (loại khỏi kiểm tra trùng giờ)
          </label>
          <label className="flex items-center gap-1.5 text-xs font-semibold text-slate-600">
            <input type="checkbox" checked={form.managedByCenter} onChange={(e) => setForm({ ...form, managedByCenter: e.target.checked })} />
            Trung tâm quản lý phòng này
          </label>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? "Đang lưu..." : "Tạo phòng học"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function EquipmentModal({ room, onClose }: { room: RoomResponse; onClose: () => void }) {
  const [equipment, setEquipment] = useState<EquipmentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showAddForm, setShowAddForm] = useState(false);
  const [form, setForm] = useState({ code: "", name: "", equipmentType: "PROJECTOR" as EquipmentType });
  const [submitting, setSubmitting] = useState(false);
  const { message: toastMessage, showToast } = useToast();

  const load = () => {
    setLoading(true);
    setError(null);
    listEquipmentByRoom(room.id)
      .then(setEquipment)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được thiết bị."))
      .finally(() => setLoading(false));
  };

  useEffect(load, [room.id]);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.code.trim() || !form.name.trim()) {
      setError("Vui lòng điền mã và tên thiết bị.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: CreateEquipmentRequest = { roomId: room.id, code: form.code.trim(), name: form.name.trim(), equipmentType: form.equipmentType };
      await createEquipment(request);
      setForm({ code: "", name: "", equipmentType: "PROJECTOR" });
      setShowAddForm(false);
      load();
      showToast("Đã thêm thiết bị thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Thêm thiết bị thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleBroken = async (item: EquipmentResponse) => {
    setError(null);
    try {
      await updateEquipmentStatus(item.id, item.status === "BROKEN" ? "AVAILABLE" : "BROKEN");
      load();
      showToast("Đã cập nhật trạng thái thiết bị thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Cập nhật trạng thái thiết bị thất bại.");
    }
  };

  return (
    <Modal open onClose={onClose} title={`Thiết bị — ${room.name || room.code}`} size="lg">
      <div className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        {loading ? (
          <p className="text-xs text-slate-500">Đang tải...</p>
        ) : equipment.length === 0 ? (
          <p className="text-xs text-slate-400 italic">Phòng này chưa có thiết bị nào.</p>
        ) : (
          <div className="space-y-2">
            {equipment.map((item) => (
              <div key={item.id} className="border border-slate-200 rounded-lg p-3 flex items-center justify-between gap-2">
                <div>
                  <span className="text-xs font-bold text-slate-800 block">{item.name}</span>
                  <span className="text-[10px] text-slate-400">{item.code} · {equipmentTypeLabels[item.equipmentType]}</span>
                </div>
                <div className="flex items-center gap-2">
                  <Badge variant={equipmentStatusVariants[item.status] ?? "neutral"}>{equipmentStatusLabels[item.status] ?? item.status}</Badge>
                  <Button size="sm" variant="secondary" onClick={() => handleToggleBroken(item)}>
                    {item.status === "BROKEN" ? "Khôi phục" : "Báo hỏng"}
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}

        {showAddForm ? (
          <form onSubmit={handleAdd} className="border-t border-slate-100 pt-4 space-y-3">
            <div className="grid grid-cols-3 gap-3">
              <div>
                <label className={labelClass}>Mã thiết bị *</label>
                <input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} className={inputClass} required />
              </div>
              <div className="col-span-2">
                <label className={labelClass}>Tên thiết bị *</label>
                <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className={inputClass} required />
              </div>
            </div>
            <div>
              <label className={labelClass}>Loại thiết bị *</label>
              <Select value={form.equipmentType} onChange={(e) => setForm({ ...form, equipmentType: e.target.value as EquipmentType })} className={inputClass}>
                {Object.entries(equipmentTypeLabels).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </Select>
            </div>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="secondary" onClick={() => setShowAddForm(false)}>
                Hủy
              </Button>
              <Button type="submit" variant="primary" disabled={submitting}>
                {submitting ? "Đang lưu..." : "Thêm thiết bị"}
              </Button>
            </div>
          </form>
        ) : (
          <Button variant="secondary" onClick={() => setShowAddForm(true)}>
            <Plus className="w-4 h-4" /> Thêm thiết bị
          </Button>
        )}
      </div>

      <Toast message={toastMessage} />
    </Modal>
  );
}
