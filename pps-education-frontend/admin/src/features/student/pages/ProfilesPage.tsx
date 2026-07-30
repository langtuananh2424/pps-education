import React, { useEffect, useState } from "react";
import { GraduationCap } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import ImportExcelButton from "@/components/ui/ImportExcelButton";
import { downloadStudentImportTemplate, exportStudentAccounts, importStudents, listStudents, StudentResponse } from "../api";
import StudentListPanel from "../components/StudentListPanel";
import StudentDetailPanel from "../components/StudentDetailPanel";
import StudentFormModal from "../components/StudentFormModal";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";

export default function ProfilesPage() {
  const { selectedCampusId } = useApp();
  const [students, setStudents] = useState<StudentResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const { message: toastMessage, showToast } = useToast();

  const load = () => {
    setLoading(true);
    setError(null);
    listStudents(query, selectedCampusId !== "ALL" ? Number(selectedCampusId) : undefined)
      .then((res) => {
        setStudents(res);
        if (selectedId == null && res.length > 0) setSelectedId(res[0].id);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách học sinh."))
      .finally(() => setLoading(false));
  };

  useEffect(load, [selectedCampusId]);

  const selectedStudent = students.find((s) => s.id === selectedId) ?? null;

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4 flex flex-col sm:flex-row sm:items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Quản lý hồ sơ học sinh (UC-13)</h1>
          <p className="text-xs text-slate-500 mt-1">
            Lưu hồ sơ học sinh, liên kết phụ huynh, theo dõi chuyển lớp/điểm trường và trạng thái học tập — khởi tạo kèm tài khoản đăng nhập.
          </p>
        </div>
        <ImportExcelButton
          title="Nhập học sinh theo lô (UC-35)"
          templateFileName="mau-import-hoc-sinh.xlsx"
          fetchTemplate={downloadStudentImportTemplate}
          uploadFn={importStudents}
          exportAccounts={exportStudentAccounts}
          accountsExportFileName="tai-khoan-hoc-sinh.xlsx"
          onImported={load}
        />
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        <StudentListPanel
          students={students}
          loading={loading}
          selectedId={selectedId}
          onSelect={setSelectedId}
          onCreate={() => setCreateOpen(true)}
          query={query}
          onQueryChange={setQuery}
          onSearch={load}
        />

        {selectedStudent ? (
          <StudentDetailPanel student={selectedStudent} onChanged={load} />
        ) : (
          <div className="lg:col-span-3 bg-white rounded-xl border border-slate-200 shadow-soft flex flex-col items-center justify-center p-12 text-center text-slate-400 space-y-3">
            <GraduationCap className="w-12 h-12 text-slate-300" />
            <div>
              <h3 className="text-sm font-bold text-slate-700">Chưa chọn học sinh nào</h3>
              <p className="text-xs text-slate-400 mt-1">Chọn 1 học sinh bên trái hoặc thêm mới để xem/sửa hồ sơ.</p>
            </div>
          </div>
        )}
      </div>

      {createOpen && (
        <StudentFormModal
          onClose={() => setCreateOpen(false)}
          onCreated={(id) => {
            setCreateOpen(false);
            setSelectedId(id);
            load();
            showToast("Đã tạo hồ sơ học sinh thành công!");
          }}
        />
      )}

      <Toast message={toastMessage} />
    </div>
  );
}
