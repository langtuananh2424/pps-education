import React, { useEffect, useState } from "react";
import { Users } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { EmployeeResponse, listEmployees } from "../api";
import EmployeeListPanel from "../components/EmployeeListPanel";
import EmployeeDetailPanel from "../components/EmployeeDetailPanel";
import EmployeeFormModal from "../components/EmployeeFormModal";

export default function ProfilesPage() {
  const [employees, setEmployees] = useState<EmployeeResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

  const load = () => {
    setLoading(true);
    setError(null);
    listEmployees(query)
      .then((res) => {
        setEmployees(res);
        if (selectedId == null && res.length > 0) setSelectedId(res[0].id);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách nhân sự."))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const selectedEmployee = employees.find((e) => e.id === selectedId) ?? null;

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Quản lý hồ sơ nhân sự (UC-08)</h1>
        <p className="text-xs text-slate-500 mt-1">
          Lưu hồ sơ cán bộ, bằng cấp/chứng chỉ, hợp đồng lao động, khen thưởng/kỷ luật — khởi tạo kèm tài khoản đăng nhập.
        </p>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        <EmployeeListPanel
          employees={employees}
          loading={loading}
          selectedId={selectedId}
          onSelect={setSelectedId}
          onCreate={() => setCreateOpen(true)}
          query={query}
          onQueryChange={setQuery}
          onSearch={load}
        />

        {selectedEmployee ? (
          <EmployeeDetailPanel employee={selectedEmployee} onChanged={load} />
        ) : (
          <div className="lg:col-span-3 bg-white rounded-xl border border-slate-200 shadow-soft flex flex-col items-center justify-center p-12 text-center text-slate-400 space-y-3">
            <Users className="w-12 h-12 text-slate-300" />
            <div>
              <h3 className="text-sm font-bold text-slate-700">Chưa chọn nhân sự nào</h3>
              <p className="text-xs text-slate-400 mt-1">Chọn 1 nhân sự bên trái hoặc thêm mới để xem/sửa hồ sơ.</p>
            </div>
          </div>
        )}
      </div>

      {createOpen && (
        <EmployeeFormModal
          onClose={() => setCreateOpen(false)}
          onCreated={(id) => {
            setCreateOpen(false);
            setSelectedId(id);
            load();
          }}
        />
      )}
    </div>
  );
}
