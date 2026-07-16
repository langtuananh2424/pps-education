import React, { useEffect, useState } from "react";
import { Users } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { listStudentParents, listStudents, ParentResponse, ParentStudentResponse } from "../api";
import ParentListPanel from "../components/ParentListPanel";
import ParentDetailPanel from "../components/ParentDetailPanel";
import ParentCreateModal from "../components/ParentCreateModal";

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
 * Backend chưa có "GET /api/parents" liệt kê toàn bộ phụ huynh — chỉ có
 * `GET /api/students/{id}/parents` theo từng học sinh. Trang này tổng hợp
 * (client-side) từ toàn bộ học sinh để dựng danh sách phụ huynh, giống
 * cách RoleMembersPanel đã tổng hợp thành viên vai trò.
 */
async function aggregateParents(): Promise<ParentAggregate[]> {
  const students = await listStudents();
  const perStudent = await Promise.all(
    students.map((s) =>
      listStudentParents(s.id).then((links) => ({ student: s, links })).catch(() => ({ student: s, links: [] as ParentStudentResponse[] }))
    )
  );

  const map = new Map<number, ParentAggregate>();
  for (const { student, links } of perStudent) {
    for (const link of links) {
      const existing = map.get(link.parentId);
      const child: ParentAggregateChild = {
        studentId: student.id,
        studentName: student.fullName,
        studentCode: student.studentCode,
        parentStudentId: link.id,
        relationship: link.relationship,
        isPrimaryContact: link.isPrimaryContact,
        isFinancialResponsible: link.isFinancialResponsible
      };
      if (existing) {
        existing.children.push(child);
      } else {
        map.set(link.parentId, { parentId: link.parentId, parentFullName: link.parentFullName, children: [child] });
      }
    }
  }
  return Array.from(map.values()).sort((a, b) => a.parentFullName.localeCompare(b.parentFullName));
}

export default function ParentsPage() {
  const [parents, setParents] = useState<ParentAggregate[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [profileCache, setProfileCache] = useState<Map<number, ParentResponse>>(new Map());

  const load = () => {
    setLoading(true);
    setError(null);
    aggregateParents()
      .then(setParents)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách phụ huynh."))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  // Phụ huynh mới tạo, CHƯA liên kết học sinh nào sẽ không xuất hiện trong
  // aggregateParents() (vốn chỉ dựng từ liên kết student→parent). Bù lại
  // bằng các hồ sơ đã cache trong phiên làm việc này nhưng chưa có trong
  // danh sách tổng hợp — hiển thị với 0 con em cho tới khi được liên kết.
  const orphanParents: ParentAggregate[] = Array.from(profileCache.values())
    .filter((profile) => !parents.some((p) => p.parentId === profile.id))
    .map((profile) => ({ parentId: profile.id, parentFullName: profile.fullName, children: [] }));
  const displayParents = [...parents, ...orphanParents].sort((a, b) => a.parentFullName.localeCompare(b.parentFullName));

  const selectedParent = displayParents.find((p) => p.parentId === selectedId) ?? null;

  const cacheProfile = (profile: ParentResponse) => {
    setProfileCache((prev) => new Map(prev).set(profile.id, profile));
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Quản lý phụ huynh (UC-13)</h1>
        <p className="text-xs text-slate-500 mt-1">
          Danh sách phụ huynh tổng hợp từ liên kết con em — khởi tạo hồ sơ độc lập, liên kết/gỡ liên kết học sinh.
        </p>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        <ParentListPanel
          parents={displayParents}
          loading={loading}
          selectedId={selectedId}
          onSelect={setSelectedId}
          onCreate={() => setCreateOpen(true)}
          query={query}
          onQueryChange={setQuery}
        />

        {selectedParent ? (
          <ParentDetailPanel
            parent={selectedParent}
            cachedProfile={profileCache.get(selectedParent.parentId) ?? null}
            onChanged={load}
            onProfileCached={cacheProfile}
          />
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
            cacheProfile(parent);
            setSelectedId(parent.id);
            load();
          }}
        />
      )}
    </div>
  );
}
