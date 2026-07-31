import React, { useEffect, useState } from "react";
import { Building2 } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { listSites, SiteResponse } from "../api";
import SiteListPanel from "../components/SiteListPanel";
import SiteDetailPanel from "../components/SiteDetailPanel";
import SiteFormModal from "../components/SiteFormModal";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";

export default function CampusesPage() {
  const [sites, setSites] = useState<SiteResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const { message: toastMessage, showToast } = useToast();

  const load = () => {
    setLoading(true);
    setError(null);
    listSites()
      .then((res) => {
        setSites(res);
        if (selectedId == null && res.length > 0) setSelectedId(res[0].id);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách điểm trường."))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const selectedSite = sites.find((s) => s.id === selectedId) ?? null;

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Quản lý Điểm trường & Hợp đồng</h1>
        <p className="text-xs text-slate-500 mt-1">
          Khai báo cơ sở tự vận hành và trường liên kết, gán Quản lý điểm trường, quản lý hợp đồng hợp tác.
        </p>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        <SiteListPanel sites={sites} loading={loading} selectedId={selectedId} onSelect={setSelectedId} onCreate={() => setCreateOpen(true)} />

        {selectedSite ? (
          <SiteDetailPanel site={selectedSite} onChanged={load} />
        ) : (
          <div className="lg:col-span-3 bg-white rounded-xl border border-slate-200 shadow-soft flex flex-col items-center justify-center p-12 text-center text-slate-400 space-y-3">
            <Building2 className="w-12 h-12 text-slate-300" />
            <div>
              <h3 className="text-sm font-bold text-slate-700">Chưa chọn điểm trường nào</h3>
              <p className="text-xs text-slate-400 mt-1">Chọn 1 điểm trường bên trái hoặc thêm mới để xem/sửa.</p>
            </div>
          </div>
        )}
      </div>

      {createOpen && (
        <SiteFormModal
          onClose={() => setCreateOpen(false)}
          onCreated={(id) => {
            setCreateOpen(false);
            setSelectedId(id);
            load();
            showToast("Đã tạo điểm trường thành công!");
          }}
        />
      )}

      <Toast message={toastMessage} />
    </div>
  );
}
