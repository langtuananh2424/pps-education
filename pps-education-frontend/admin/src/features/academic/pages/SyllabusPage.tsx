import React, { useEffect, useState } from "react";
import { BookOpen } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { CurriculumResponse, listCurriculums } from "../api";
import Card from "@/components/ui/Card";
import CurriculumListPanel from "../components/CurriculumListPanel";
import CurriculumDetailPanel from "../components/CurriculumDetailPanel";
import CurriculumFormModal from "../components/CurriculumFormModal";
import CurriculumApprovalPanel from "../components/CurriculumApprovalPanel";

export default function SyllabusPage() {
  const [curriculums, setCurriculums] = useState<CurriculumResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

  const load = () => {
    setLoading(true);
    setError(null);
    listCurriculums()
      .then((res) => {
        setCurriculums(res);
        if (selectedId == null && res.length > 0) setSelectedId(res[0].id);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách khung chương trình."))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const selectedCurriculum = curriculums.find((c) => c.id === selectedId) ?? null;

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Khung chương trình (UC-16/16b/17)</h1>
        <p className="text-xs text-slate-500 mt-1">
          Quản lý khung chương trình chuẩn, học phần, và bản tùy biến theo điểm trường liên kết.
        </p>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        <CurriculumListPanel
          curriculums={curriculums}
          loading={loading}
          selectedId={selectedId}
          onSelect={setSelectedId}
          onCreate={() => setCreateOpen(true)}
          query={query}
          onQueryChange={setQuery}
        />

        {selectedCurriculum ? (
          <CurriculumDetailPanel curriculum={selectedCurriculum} onChanged={load} />
        ) : (
          <div className="lg:col-span-3 bg-white rounded-xl border border-slate-200 shadow-soft flex flex-col items-center justify-center p-12 text-center text-slate-400 space-y-3">
            <BookOpen className="w-12 h-12 text-slate-300" />
            <div>
              <h3 className="text-sm font-bold text-slate-700">Chưa chọn khung chương trình nào</h3>
              <p className="text-xs text-slate-400 mt-1">Chọn 1 khung bên trái hoặc thêm mới.</p>
            </div>
          </div>
        )}
      </div>

      <Card className="space-y-4">
        <h3 className="text-xs font-bold text-slate-400 block uppercase tracking-wider font-display border-b border-slate-100 pb-2">
          Duyệt tùy biến khung chương trình (UC-17)
        </h3>
        <CurriculumApprovalPanel />
      </Card>

      {createOpen && (
        <CurriculumFormModal
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
