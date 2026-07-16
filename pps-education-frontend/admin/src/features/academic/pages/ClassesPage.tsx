import React, { useEffect, useState } from "react";
import { GraduationCap } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { ClassResponse, listClasses } from "../api";
import ClassListPanel from "../components/ClassListPanel";
import ClassDetailPanel from "../components/ClassDetailPanel";
import ClassFormModal from "../components/ClassFormModal";

export default function ClassesPage() {
  const { selectedCampusId } = useApp();
  const [classes, setClasses] = useState<ClassResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

  const load = () => {
    setLoading(true);
    setError(null);
    listClasses({ siteId: selectedCampusId !== "ALL" ? Number(selectedCampusId) : undefined })
      .then((res) => {
        setClasses(res);
        if (selectedId == null && res.length > 0) setSelectedId(res[0].id);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách lớp học."))
      .finally(() => setLoading(false));
  };

  useEffect(load, [selectedCampusId]);

  const selectedClass = classes.find((c) => c.id === selectedId) ?? null;

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Quản lý lớp học (UC-18)</h1>
        <p className="text-xs text-slate-500 mt-1">
          Xếp lớp & gán khóa học, điều phối giáo viên, ghi danh học sinh, xếp buổi học và điểm danh. Lọc theo điểm trường ở dropdown trên đầu trang.
        </p>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        <ClassListPanel
          classes={classes.filter((c) => !query.trim() || c.name.toLowerCase().includes(query.toLowerCase()) || c.classCode.toLowerCase().includes(query.toLowerCase()))}
          loading={loading}
          selectedId={selectedId}
          onSelect={setSelectedId}
          onCreate={() => setCreateOpen(true)}
          query={query}
          onQueryChange={setQuery}
        />

        {selectedClass ? (
          <ClassDetailPanel schoolClass={selectedClass} onChanged={load} />
        ) : (
          <div className="lg:col-span-3 bg-white rounded-xl border border-slate-200 shadow-soft flex flex-col items-center justify-center p-12 text-center text-slate-400 space-y-3">
            <GraduationCap className="w-12 h-12 text-slate-300" />
            <div>
              <h3 className="text-sm font-bold text-slate-700">Chưa chọn lớp học nào</h3>
              <p className="text-xs text-slate-400 mt-1">Chọn 1 lớp bên trái hoặc thêm mới.</p>
            </div>
          </div>
        )}
      </div>

      {createOpen && (
        <ClassFormModal
          onClose={() => setCreateOpen(false)}
          onCreated={(created) => {
            setCreateOpen(false);
            setSelectedId(created.id);
            load();
          }}
        />
      )}
    </div>
  );
}
