import React, { useEffect, useState } from "react";
import { Pencil, Plus, Wrench } from "lucide-react";
import { useTranslation } from "react-i18next";
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
  UpdateRoomRequest,
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

const ROOM_TYPES: RoomType[] = ["THEORY", "COMPUTER", "LAB", "OTHER"];
const ROOM_STATUSES: RoomStatus[] = ["AVAILABLE", "MAINTENANCE", "DISABLED"];
const roomStatusVariants: Record<RoomStatus, BadgeVariant> = { AVAILABLE: "success", MAINTENANCE: "warning", DISABLED: "neutral" };

const EQUIPMENT_TYPES: EquipmentType[] = ["PROJECTOR", "SPEAKER", "MIC", "COMPUTER", "OTHER"];
const equipmentStatusVariants: Record<string, BadgeVariant> = { AVAILABLE: "success", IN_USE: "info", MAINTENANCE: "warning", BROKEN: "danger" };

/** UC-37: Quản lý phòng học + thiết bị dạy học (FR-FAC-04/FR-FAC-02) — cả 2 đã có sẵn API thật. */
export default function RoomsPage() {
  const { t } = useTranslation("facility");
  const { selectedCampusId } = useApp();
  const [sites, setSites] = useState<SiteResponse[]>([]);
  const [rooms, setRooms] = useState<RoomResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [equipmentRoom, setEquipmentRoom] = useState<RoomResponse | null>(null);
  const [editingRoom, setEditingRoom] = useState<RoomResponse | null>(null);
  const { message: toastMessage, showToast } = useToast();

  const siteName = (id: number) => sites.find((s) => s.id === id)?.name ?? t("roomsPage.siteFallback", { id });

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
      .catch((err) => setError(err instanceof ApiError ? err.message : t("roomsPage.loadError")))
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
      showToast(t("roomsPage.toggleFlexibleSuccess"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("roomsPage.toggleFlexibleError"));
    }
  };

  if (loading) return <p className="text-xs text-slate-500">{t("roomsPage.loading")}</p>;

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("roomsPage.title")}</h1>
        <p className="text-xs text-slate-500 mt-1">{t("roomsPage.description")}</p>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="flex items-center justify-between flex-wrap gap-2">
        <h3 className="text-sm font-bold text-slate-800 font-display">{t("roomsPage.sectionTitle")}</h3>
        <div className="flex items-center gap-3">
          <span className="text-xs text-slate-400 italic">{t("roomsPage.flexibleHint")}</span>
          <Button size="sm" variant="primary" onClick={() => setShowCreateForm(true)}>
            <Plus className="w-3.5 h-3.5" />
            {t("roomsPage.addRoomButton")}
          </Button>
        </div>
      </div>

      {rooms.length === 0 ? (
        <p className="text-xs text-slate-400 italic text-center py-10">{t("roomsPage.empty")}</p>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5">
          {rooms.map((room) => {
            const status = room.status as RoomStatus;
            return (
              <Card key={room.id} className="flex flex-col justify-between">
                <div>
                  <div className="flex items-start justify-between gap-2 border-b pb-2">
                    <span className="text-xs font-bold text-slate-900 truncate">{room.name || room.code}</span>
                    <Badge variant={roomStatusVariants[status] ?? "neutral"}>{t(`roomStatus.${status}`, status)}</Badge>
                  </div>

                  <div className="text-[11px] text-slate-500 space-y-1.5 mt-3">
                    <p>{t("roomsPage.siteLabel", { name: siteName(room.siteId) })}</p>
                    <p>{t("roomsPage.capacityLabel", { count: room.capacity })}</p>
                    <p>{t("roomsPage.roomTypeLabel", { type: t(`roomType.${room.roomType}`, room.roomType) })}</p>
                  </div>
                </div>

                <div className="border-t border-slate-100 pt-3 flex items-center justify-between gap-2 flex-wrap">
                  <div className="flex items-center gap-2.5">
                    <button onClick={() => setEditingRoom(room)} className="text-[10px] font-bold text-slate-500 hover:text-brand-orange flex items-center gap-1">
                      <Pencil className="w-3 h-3" /> {t("roomsPage.editButton")}
                    </button>
                    <button onClick={() => setEquipmentRoom(room)} className="text-[10px] font-bold text-slate-500 hover:text-brand-orange flex items-center gap-1">
                      <Wrench className="w-3 h-3" /> {t("roomsPage.equipmentButton")}
                    </button>
                  </div>
                  <button
                    onClick={() => handleToggleFlexibility(room)}
                    className={cn("px-3 py-1 rounded text-[10px] font-bold transition-all", room.flexible ? "bg-brand-gradient text-white shadow-soft" : "bg-slate-100 text-slate-600 hover:bg-slate-200")}
                  >
                    {room.flexible ? t("roomsPage.flexibleOn") : t("roomsPage.flexibleOff")}
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
            showToast(t("roomsPage.createdToast"));
          }}
        />
      )}

      {equipmentRoom && <EquipmentModal room={equipmentRoom} onClose={() => setEquipmentRoom(null)} />}

      {editingRoom && (
        <EditRoomModal
          room={editingRoom}
          onClose={() => setEditingRoom(null)}
          onUpdated={(updated) => {
            setRooms((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
            setEditingRoom(null);
            showToast(t("roomsPage.updatedToast"));
          }}
        />
      )}

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
  const { t } = useTranslation("facility");
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
      setError(t("roomsPage.createModal.validationError"));
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
      setError(err instanceof ApiError ? err.message : t("roomsPage.createModal.createError"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={t("roomsPage.createModal.title")} size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>{t("roomsPage.createModal.siteLabel")}</label>
            <Select value={form.siteId} onChange={(e) => setForm({ ...form, siteId: e.target.value })} className={inputClass}>
              {sites.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className={labelClass}>{t("roomsPage.createModal.codeLabel")}</label>
            <input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} placeholder={t("roomsPage.createModal.codePlaceholder")} className={inputClass} required />
          </div>
        </div>
        <div>
          <label className={labelClass}>{t("roomsPage.createModal.nameLabel")}</label>
          <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder={t("roomsPage.createModal.namePlaceholder")} className={inputClass} />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>{t("roomsPage.createModal.roomTypeLabel")}</label>
            <Select value={form.roomType} onChange={(e) => setForm({ ...form, roomType: e.target.value as RoomType })} className={inputClass}>
              {ROOM_TYPES.map((value) => (
                <option key={value} value={value}>
                  {t(`roomType.${value}`)}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className={labelClass}>{t("roomsPage.createModal.capacityLabel")}</label>
            <input type="number" min={1} value={form.capacity} onChange={(e) => setForm({ ...form, capacity: e.target.value })} className={inputClass} required />
          </div>
        </div>
        <div className="flex gap-4">
          <label className="flex items-center gap-1.5 text-xs font-semibold text-slate-600">
            <input type="checkbox" checked={form.flexible} onChange={(e) => setForm({ ...form, flexible: e.target.checked })} />
            {t("roomsPage.createModal.flexibleCheckbox")}
          </label>
          <label className="flex items-center gap-1.5 text-xs font-semibold text-slate-600">
            <input type="checkbox" checked={form.managedByCenter} onChange={(e) => setForm({ ...form, managedByCenter: e.target.checked })} />
            {t("roomsPage.createModal.managedByCenterCheckbox")}
          </label>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t("roomsPage.createModal.cancel")}
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? t("roomsPage.createModal.saving") : t("roomsPage.createModal.createButton")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function EditRoomModal({ room, onClose, onUpdated }: { room: RoomResponse; onClose: () => void; onUpdated: (room: RoomResponse) => void }) {
  const { t } = useTranslation("facility");
  const [form, setForm] = useState({
    name: room.name,
    capacity: String(room.capacity),
    flexible: room.flexible,
    managedByCenter: room.managedByCenter,
    status: room.status as RoomStatus,
    notes: room.notes ?? ""
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.capacity) {
      setError(t("roomsPage.editModal.validationError"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: UpdateRoomRequest = {
        name: form.name.trim() || undefined,
        capacity: Number(form.capacity),
        flexible: form.flexible,
        managedByCenter: form.managedByCenter,
        status: form.status,
        notes: form.notes.trim() || undefined
      };
      const updated = await updateRoom(room.id, request);
      onUpdated(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("roomsPage.editModal.saveError"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={t("roomsPage.editModal.title", { code: room.code })} size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>{t("roomsPage.editModal.nameLabel")}</label>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder={t("roomsPage.editModal.namePlaceholder")} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>{t("roomsPage.editModal.capacityLabel")}</label>
            <input type="number" min={1} value={form.capacity} onChange={(e) => setForm({ ...form, capacity: e.target.value })} className={inputClass} required />
          </div>
        </div>
        <div>
          <label className={labelClass}>{t("roomsPage.editModal.statusLabel")}</label>
          <Select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value as RoomStatus })} className={inputClass}>
            {ROOM_STATUSES.map((value) => (
              <option key={value} value={value}>
                {t(`roomStatus.${value}`)}
              </option>
            ))}
          </Select>
        </div>
        <div>
          <label className={labelClass}>{t("roomsPage.editModal.notesLabel")}</label>
          <textarea
            value={form.notes}
            onChange={(e) => setForm({ ...form, notes: e.target.value })}
            rows={2}
            className={inputClass}
            placeholder={t("roomsPage.editModal.notesPlaceholder")}
          />
        </div>
        <div className="flex gap-4">
          <label className="flex items-center gap-1.5 text-xs font-semibold text-slate-600">
            <input type="checkbox" checked={form.flexible} onChange={(e) => setForm({ ...form, flexible: e.target.checked })} />
            {t("roomsPage.editModal.flexibleCheckbox")}
          </label>
          <label className="flex items-center gap-1.5 text-xs font-semibold text-slate-600">
            <input type="checkbox" checked={form.managedByCenter} onChange={(e) => setForm({ ...form, managedByCenter: e.target.checked })} />
            {t("roomsPage.editModal.managedByCenterCheckbox")}
          </label>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t("roomsPage.editModal.cancel")}
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? t("roomsPage.editModal.saving") : t("roomsPage.editModal.saveButton")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function EquipmentModal({ room, onClose }: { room: RoomResponse; onClose: () => void }) {
  const { t } = useTranslation("facility");
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
      .catch((err) => setError(err instanceof ApiError ? err.message : t("roomsPage.equipmentModal.loadError")))
      .finally(() => setLoading(false));
  };

  useEffect(load, [room.id]);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.code.trim() || !form.name.trim()) {
      setError(t("roomsPage.equipmentModal.validationError"));
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
      showToast(t("roomsPage.equipmentModal.addedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("roomsPage.equipmentModal.addError"));
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleBroken = async (item: EquipmentResponse) => {
    setError(null);
    try {
      await updateEquipmentStatus(item.id, item.status === "BROKEN" ? "AVAILABLE" : "BROKEN");
      load();
      showToast(t("roomsPage.equipmentModal.toggleBrokenToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("roomsPage.equipmentModal.toggleBrokenError"));
    }
  };

  return (
    <Modal open onClose={onClose} title={t("roomsPage.equipmentModal.title", { room: room.name || room.code })} size="lg">
      <div className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        {loading ? (
          <p className="text-xs text-slate-500">{t("roomsPage.equipmentModal.loading")}</p>
        ) : equipment.length === 0 ? (
          <p className="text-xs text-slate-400 italic">{t("roomsPage.equipmentModal.empty")}</p>
        ) : (
          <div className="space-y-2">
            {equipment.map((item) => (
              <div key={item.id} className="border border-slate-200 rounded-lg p-3 flex items-center justify-between gap-2">
                <div>
                  <span className="text-xs font-bold text-slate-800 block">{item.name}</span>
                  <span className="text-[10px] text-slate-400">{item.code} · {t(`equipmentType.${item.equipmentType}`)}</span>
                </div>
                <div className="flex items-center gap-2">
                  <Badge variant={equipmentStatusVariants[item.status] ?? "neutral"}>{t(`equipmentStatus.${item.status}`, item.status)}</Badge>
                  <Button size="sm" variant="secondary" onClick={() => handleToggleBroken(item)}>
                    {item.status === "BROKEN" ? t("roomsPage.equipmentModal.restoreButton") : t("roomsPage.equipmentModal.reportBrokenButton")}
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
                <label className={labelClass}>{t("roomsPage.equipmentModal.codeLabel")}</label>
                <input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} className={inputClass} required />
              </div>
              <div className="col-span-2">
                <label className={labelClass}>{t("roomsPage.equipmentModal.nameLabel")}</label>
                <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className={inputClass} required />
              </div>
            </div>
            <div>
              <label className={labelClass}>{t("roomsPage.equipmentModal.typeLabel")}</label>
              <Select value={form.equipmentType} onChange={(e) => setForm({ ...form, equipmentType: e.target.value as EquipmentType })} className={inputClass}>
                {EQUIPMENT_TYPES.map((value) => (
                  <option key={value} value={value}>
                    {t(`equipmentType.${value}`)}
                  </option>
                ))}
              </Select>
            </div>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="secondary" onClick={() => setShowAddForm(false)}>
                {t("roomsPage.equipmentModal.cancel")}
              </Button>
              <Button type="submit" variant="primary" disabled={submitting}>
                {submitting ? t("roomsPage.equipmentModal.saving") : t("roomsPage.equipmentModal.addButton")}
              </Button>
            </div>
          </form>
        ) : (
          <Button variant="secondary" onClick={() => setShowAddForm(true)}>
            <Plus className="w-4 h-4" /> {t("roomsPage.equipmentModal.addButton")}
          </Button>
        )}
      </div>

      <Toast message={toastMessage} />
    </Modal>
  );
}
