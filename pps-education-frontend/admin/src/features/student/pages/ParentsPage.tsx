import React, { useEffect, useState } from "react";
import { Users } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import ImportExcelButton from "@/components/ui/ImportExcelButton";
import { importParents, listStudentParents, listStudents, ParentStudentResponse, searchParents } from "../api";
import ParentListPanel from "../components/ParentListPanel";
import ParentDetailPanel from "../components/ParentDetailPanel";
import ParentCreateModal from "../components/ParentCreateModal";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";

const PARENT_IMPORT_HEADERS = ["Họ và tên phụ huynh *", "Số điện thoại *", "Quan hệ (Cha/Mẹ/Người giám hộ/Khác)", "Mã học sinh *", "Là người liên hệ chính (Có/Không)", "Chịu trách nhiệm tài chính (Có/Không)"];
const PARENT_IMPORT_SAMPLE = ["Nguyễn Văn B", "0901234567", "Cha", "HS-0001", "Có", "Có"];

export interface ParentAggregateChild {
  studentId: number;
  studentName: string;
  studentCode: string;
  parentStudentId: number;
  relationship: ParentStudentResponse["relationship"];
  isPrimaryContact: boolean;
  isFinancialResponsible: boolean;
}

export interface ParentAggregate {
  parentId: number;
  parentFullName: string;
  children: ParentAggregateChild[];
}

/**
 * `GET /api/parents` (từ PR#39) trả về hồ sơ phụ huynh nhưng KHÔNG kèm
 * con em đã liên kết — và cũng chưa có chiều ngược "phụ huynh này có
 * những học sinh nào". Vẫn phải dựng map con em bằng cách quét toàn bộ
 * học sinh + liên kết của từng em (giống RoleMembersPanel).
 */
async function buildChildrenMap(): Promise<Map<number, ParentAggregateChild[]>> {
  const students = await listStudents();
  const perStudent = await Promise.all(
    students.map((s) =>
      listStudentParents(s.id).then((links) => ({ student: s, links })).catch(() => ({ student: s, links: [] as ParentStudentResponse[] }))
    )
  );

  const map = new Map<number, ParentAggregateChild[]>();
  for (const { student, links } of perStudent) {
    for (const link of links) {
      const child: ParentAggregateChild = {
        studentId: student.id,
        studentName: student.fullName,
        studentCode: student.studentCode,
        parentStudentId: link.id,
        relationship: link.relationship,
        isPrimaryContact: link.isPrimaryContact,
        isFinancialResponsible: link.isFinancialResponsible
      };
      const existing = map.get(link.parentId);
      if (existing) existing.push(child);
      else map.set(link.parentId, [child]);
    }
  }
  return map;
}

export default function ParentsPage() {
  const [parents, setParents] = useState<ParentAggregate[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const { message: toastMessage, showToast } = useToast();

  const load = () => {
    setLoading(true);
    setError(null);
    Promise.all([searchParents(), buildChildrenMap()])
      .then(([profiles, childrenMap]) => {
        const aggregates = profiles
          .map((p) => ({ parentId: p.id, parentFullName: p.fullName, children: childrenMap.get(p.id) ?? [] }))
          .sort((a, b) => a.parentFullName.localeCompare(b.parentFullName));
        setParents(aggregates);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách phụ huynh."))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const selectedParent = parents.find((p) => p.parentId === selectedId) ?? null;

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4 flex flex-col sm:flex-row sm:items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Quản lý phụ huynh (UC-13)</h1>
          <p className="text-xs text-slate-500 mt-1">
            Toàn bộ hồ sơ phụ huynh (kể cả chưa liên kết con em) — khởi tạo hồ sơ độc lập, liên kết/gỡ liên kết học sinh.
          </p>
        </div>
        <ImportExcelButton
          title="Nhập phụ huynh theo lô (UC-50)"
          templateFileName="mau-import-phu-huynh.xlsx"
          templateHeaders={PARENT_IMPORT_HEADERS}
          templateSampleRow={PARENT_IMPORT_SAMPLE}
          uploadFn={importParents}
          onImported={load}
        />
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        <ParentListPanel
          parents={parents}
          loading={loading}
          selectedId={selectedId}
          onSelect={setSelectedId}
          onCreate={() => setCreateOpen(true)}
          query={query}
          onQueryChange={setQuery}
        />

        {selectedParent ? (
          <ParentDetailPanel parent={selectedParent} onChanged={load} />
        ) : (
          <div className="lg:col-span-3 bg-white rounded-xl border border-slate-200 shadow-soft flex flex-col items-center justify-center p-12 text-center text-slate-400 space-y-3">
            <Users className="w-12 h-12 text-slate-300" />
            <div>
              <h3 className="text-sm font-bold text-slate-700">Chưa chọn phụ huynh nào</h3>
              <p className="text-xs text-slate-400 mt-1">Chọn 1 phụ huynh bên trái hoặc thêm mới.</p>
            </div>
          </div>
        )}
      </div>

      {createOpen && (
        <ParentCreateModal
          onClose={() => setCreateOpen(false)}
          onCreated={(parent) => {
            setCreateOpen(false);
            setSelectedId(parent.id);
            load();
            showToast("Đã tạo hồ sơ phụ huynh thành công!");
          }}
        />
      )}

      <Toast message={toastMessage} />
    </div>
  );
}
