import React, { useEffect, useState } from "react";
import { Plus } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { UserRole } from "@/types";
import {
  ClassEnrollmentResponse,
  ClassResponse,
  CreateGradeComponentRequest,
  CreateGradePeriodRequest,
  GradeComponentResponse,
  GradePeriodResponse,
  addGradeComponent,
  createGradePeriod,
  getClass,
  listClassEnrollments,
  listClassTeachers,
  listClasses,
  listGradeComponents,
  listGradePeriods,
  listUnpublishedGrades
} from "../api";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import GradeSheetTable from "../components/GradeSheetTable";
import GradeExcelImportPanel from "../components/GradeExcelImportPanel";
import GradePublishDetail from "../components/GradePublishDetail";
import GradePublishGroupList, { GradePublishGroup } from "../components/GradePublishGroupList";

const inputClass = "bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none";

export default function GradesPage() {
  const { hasPermission, currentUser } = useApp();
  const canManage = hasPermission("academic.grade.manage");
  // Cờ riêng cho việc lọc DANH SÁCH LỚP — không dùng canManage (academic.grade.manage) vì quyền đó
  // nghĩa thật là "Cấu hình sổ điểm" (tạo đầu điểm/kỳ điểm) và backend cấp luôn cho TEACHER, không
  // phải cờ "được xem mọi lớp". academic.class.manage mới đúng là quyền quản trị lớp học rộng (chỉ
  // HEAD_ACADEMIC/Admin), khớp UC-19 Precondition ("Trưởng phòng đào tạo... quyền academic.grade.manage"
  // chỉ mở rộng cho vai trò quản lý, không áp dụng cho GV thường dù họ cũng có permission code này).
  // Hàng chờ duyệt (UC-20) chỉ có ý nghĩa với Quản lý điểm trường — API tự scope theo site được gán,
  // ẩn hẳn khối này với tài khoản khác để đỡ hiện 1 panel rỗng không liên quan.
  const isSiteManager = currentUser?.roleCodes?.includes(UserRole.SITE_MANAGER) ?? false;
  // Quản lý điểm trường không dạy lớp nào (không có mặt trong class_teachers) nên cũng cần thấy TOÀN
  // BỘ lớp thuộc site mình quản lý (GET /api/classes đã tự scope theo site_managers, xem commit
  // 183222e) để chọn xem lại sổ điểm bất kỳ lớp nào — không chỉ riêng những lớp đang "chờ công bố".
  const canSeeAllClasses = hasPermission("academic.class.manage") || isSiteManager;

  const [classes, setClasses] = useState<ClassResponse[]>([]);
  const [selectedClassId, setSelectedClassId] = useState<number | null>(null);
  const [enrollments, setEnrollments] = useState<ClassEnrollmentResponse[]>([]);
  const [gradePeriods, setGradePeriods] = useState<GradePeriodResponse[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [gradeComponents, setGradeComponents] = useState<GradeComponentResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [showPeriodForm, setShowPeriodForm] = useState(false);
  const [showComponentForm, setShowComponentForm] = useState(false);
  const [sheetVersion, setSheetVersion] = useState(0);

  const [pendingGroups, setPendingGroups] = useState<GradePublishGroup[]>([]);
  const [loadingPendingGroups, setLoadingPendingGroups] = useState(true);
  const [selectedPendingClassId, setSelectedPendingClassId] = useState<number | null>(null);

  const selectedClass = classes.find((c) => c.id === selectedClassId) ?? null;
  const selectedPendingGroup = pendingGroups.find((g) => g.classId === selectedPendingClassId) ?? null;

  /**
   * UC-19 Precondition: "Giáo viên được phân công giảng dạy lớp cần nhập điểm (UC-18)"
   * — nghĩa là theo class_teachers (từng lớp cụ thể), KHÔNG phải theo site_teachers
   * (mọi lớp ở site GV được đăng ký). GET /api/classes hiện chỉ lọc theo site_teachers
   * (coarse hơn UC-19 yêu cầu) nên phải lọc thêm ở FE cho tài khoản không có
   * academic.class.manage (GV thường) — HEAD_ACADEMIC/Admin (canSeeAllClasses) vẫn
   * thấy đủ theo đúng phần mở rộng quyền đã xác nhận.
   */
  useEffect(() => {
    listClasses()
      .then(async (allClasses) => {
        if (canSeeAllClasses || !currentUser) {
          setClasses(allClasses);
          return;
        }
        const teacherLists = await Promise.all(allClasses.map((c) => listClassTeachers(c.id).catch(() => [])));
        setClasses(allClasses.filter((_, i) => teacherLists[i].some((t) => t.teacherUserId === currentUser.id)));
      })
      .catch(() => undefined);
  }, [canSeeAllClasses, currentUser]);

  /**
   * V39/UC-20: tra tên lớp qua GET /api/classes/{id} (đơn lẻ, không lọc site) thay vì
   * tìm trong state `classes` (từ GET /api/classes danh sách) — danh sách đó hiện chỉ
   * lọc theo site_teachers, BỎ SÓT site_managers (gap backend, đã báo lại riêng), nên
   * với tài khoản Quản lý điểm trường không đồng thời là giáo viên, `classes` luôn rỗng.
   */
  const loadPendingGroups = () => {
    setLoadingPendingGroups(true);
    listUnpublishedGrades()
      .then(async (entries) => {
        const countByClass = new Map<number, number>();
        entries.forEach((e) => countByClass.set(e.classId, (countByClass.get(e.classId) ?? 0) + 1));
        const groups = await Promise.all(
          [...countByClass.entries()].map(async ([classId, pendingCount]) => {
            const cls = await getClass(classId).catch(() => null);
            const teacherName = await listClassTeachers(classId)
              .then((teachers) => teachers.find((t) => t.teacherRole === "PRIMARY")?.teacherFullName ?? teachers[0]?.teacherFullName ?? null)
              .catch(() => null);
            return { classId, classCode: cls?.classCode ?? String(classId), className: cls?.name ?? "", teacherName, pendingCount };
          })
        );
        setPendingGroups(groups);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách lớp chờ công bố."))
      .finally(() => setLoadingPendingGroups(false));
  };

  useEffect(() => {
    if (isSiteManager) loadPendingGroups();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isSiteManager]);

  useEffect(() => {
    if (!selectedClassId) return;
    listClassEnrollments(selectedClassId).then(setEnrollments).catch(() => undefined);
  }, [selectedClassId]);

  useEffect(() => {
    setSelectedPeriodId(null);
    setGradeComponents([]);
    if (!selectedClass) return;
    listGradePeriods(selectedClass.curriculumId).then(setGradePeriods).catch(() => undefined);
  }, [selectedClass?.curriculumId]);

  useEffect(() => {
    setGradeComponents([]);
    if (!selectedPeriodId) return;
    listGradeComponents(selectedPeriodId)
      .then(setGradeComponents)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được đầu điểm."));
  }, [selectedPeriodId]);

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Sổ điểm hệ thống (UC-19/20)</h1>
        <p className="text-xs text-slate-500 mt-1">
          Giáo viên nhập điểm theo lớp, tự sửa được trong hạn chỉnh sửa. Quản lý điểm trường công bố cho Phụ huynh xem — không còn bước
          duyệt/từ chối.
        </p>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {isSiteManager && (
        <div className="space-y-4">
          <h2 className="text-xs font-bold text-slate-400 uppercase tracking-wider font-display border-b border-slate-200 pb-2">
            Công bố điểm học phần (UC-20)
          </h2>
          <GradePublishDetail
            classId={selectedPendingClassId}
            classLabel={selectedPendingGroup ? `${selectedPendingGroup.classCode} — ${selectedPendingGroup.className}` : null}
            teacherName={selectedPendingGroup?.teacherName ?? null}
            onPublished={() => {
              setSelectedPendingClassId(null);
              loadPendingGroups();
            }}
          />
          <Card padded={false} className="overflow-hidden">
            <div className="px-5 py-4 border-b border-slate-100 bg-slate-50">
              <span className="text-xs font-bold text-slate-700 font-display">Danh sách lớp chờ công bố ({pendingGroups.length})</span>
            </div>
            <GradePublishGroupList
              groups={pendingGroups}
              loading={loadingPendingGroups}
              selectedClassId={selectedPendingClassId}
              onSelect={setSelectedPendingClassId}
            />
          </Card>

          <h2 className="text-xs font-bold text-slate-400 uppercase tracking-wider font-display border-b border-slate-200 pb-2 pt-2">
            Xem lại sổ điểm theo lớp (đã công bố)
          </h2>
          <Card padded={false} className="overflow-hidden">
            <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 grid grid-cols-1 sm:grid-cols-2 gap-2">
              <select value={selectedClassId ?? ""} onChange={(e) => setSelectedClassId(e.target.value ? Number(e.target.value) : null)} className={inputClass}>
                <option value="">-- Chọn lớp --</option>
                {classes.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.classCode} — {c.name}
                  </option>
                ))}
              </select>
              <select
                value={selectedPeriodId ?? ""}
                onChange={(e) => setSelectedPeriodId(e.target.value ? Number(e.target.value) : null)}
                disabled={!selectedClassId}
                className={`${inputClass} disabled:opacity-50`}
              >
                <option value="">-- Chọn kỳ điểm --</option>
                {gradePeriods.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </div>
            {selectedClassId && selectedPeriodId ? (
              gradeComponents.length === 0 ? (
                <p className="text-xs text-slate-400 italic p-6 text-center">Kỳ điểm này chưa có đầu điểm nào được cấu hình.</p>
              ) : (
                <GradeSheetTable
                  key={`view-${selectedClassId}-${selectedPeriodId}`}
                  classId={selectedClassId}
                  gradePeriodId={selectedPeriodId}
                  components={gradeComponents}
                  enrollments={enrollments}
                  readOnly
                />
              )
            ) : (
              <p className="text-xs text-slate-400 italic p-6 text-center">Chọn lớp → kỳ điểm để xem lại điểm (Nháp + Đã công bố).</p>
            )}
          </Card>
        </div>
      )}

      {!isSiteManager && (
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card padded={false} className="lg:col-span-3 overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 space-y-3">
            <div className="flex items-center justify-between flex-wrap gap-2">
              <span className="text-xs font-bold text-slate-700 font-display">Bảng nhập điểm (UC-19)</span>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
              <select value={selectedClassId ?? ""} onChange={(e) => setSelectedClassId(e.target.value ? Number(e.target.value) : null)} className={inputClass}>
                <option value="">-- Chọn lớp --</option>
                {classes.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.classCode} — {c.name}
                  </option>
                ))}
              </select>
              <select
                value={selectedPeriodId ?? ""}
                onChange={(e) => setSelectedPeriodId(e.target.value ? Number(e.target.value) : null)}
                disabled={!selectedClassId}
                className={`${inputClass} disabled:opacity-50`}
              >
                <option value="">-- Chọn kỳ điểm --</option>
                {gradePeriods.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </div>
            {canManage && selectedClass && (
              <div className="flex gap-2">
                <Button type="button" size="sm" variant="secondary" onClick={() => setShowPeriodForm((v) => !v)}>
                  <Plus className="w-3.5 h-3.5" />
                  Thêm kỳ điểm
                </Button>
                {selectedPeriodId && (
                  <Button type="button" size="sm" variant="secondary" onClick={() => setShowComponentForm((v) => !v)}>
                    <Plus className="w-3.5 h-3.5" />
                    Thêm đầu điểm
                  </Button>
                )}
              </div>
            )}
            {showPeriodForm && selectedClass && (
              <CreatePeriodForm
                curriculumId={selectedClass.curriculumId}
                onDone={(p) => {
                  setGradePeriods((prev) => [...prev, p]);
                  setShowPeriodForm(false);
                }}
                onCancel={() => setShowPeriodForm(false)}
              />
            )}
            {showComponentForm && selectedPeriodId && (
              <CreateComponentForm
                gradePeriodId={selectedPeriodId}
                onDone={(c) => {
                  setGradeComponents((prev) => [...prev, c]);
                  setShowComponentForm(false);
                }}
                onCancel={() => setShowComponentForm(false)}
              />
            )}
          </div>

          {selectedClassId && selectedPeriodId ? (
            gradeComponents.length === 0 ? (
              <p className="text-xs text-slate-400 italic p-6 text-center">
                Kỳ điểm này chưa có đầu điểm nào được cấu hình{canManage ? " — dùng nút \"Thêm đầu điểm\" ở trên." : "."}
              </p>
            ) : (
              <GradeSheetTable
                key={`${selectedClassId}-${selectedPeriodId}-${sheetVersion}`}
                classId={selectedClassId}
                gradePeriodId={selectedPeriodId}
                components={gradeComponents}
                enrollments={enrollments}
              />
            )
          ) : (
            <p className="text-xs text-slate-400 italic p-6 text-center">Chọn lớp → kỳ điểm để bắt đầu nhập điểm.</p>
          )}
        </Card>
      </div>
      )}

      {!isSiteManager && selectedClassId && selectedPeriodId && gradeComponents.length > 0 && (
        <GradeExcelImportPanel classId={selectedClassId} gradePeriodId={selectedPeriodId} onImported={() => setSheetVersion((v) => v + 1)} />
      )}
    </div>
  );
}

function CreatePeriodForm({ curriculumId, onDone, onCancel }: { curriculumId: number; onDone: (p: GradePeriodResponse) => void; onCancel: () => void }) {
  const [form, setForm] = useState({ code: "MID_1", name: "", weightInFinal: "" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name.trim() || !form.weightInFinal) {
      setError("Vui lòng điền đủ tên và trọng số.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: CreateGradePeriodRequest = { code: form.code, name: form.name.trim(), weightInFinal: Number(form.weightInFinal) };
      const created = await createGradePeriod(curriculumId, request);
      onDone(created);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Tạo kỳ điểm thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-white border border-slate-200 rounded-lg p-3 space-y-2">
      {error && <div className="text-[11px] text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{error}</div>}
      <div className="grid grid-cols-3 gap-2">
        <select value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} className={inputClass}>
          <option value="MID_1">MID_1 — Giữa kỳ 1</option>
          <option value="END_1">END_1 — Cuối kỳ 1</option>
          <option value="MID_2">MID_2 — Giữa kỳ 2</option>
          <option value="END_2">END_2 — Cuối kỳ 2</option>
          <option value="OTHER">OTHER — Khác</option>
        </select>
        <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Tên kỳ điểm" className={inputClass} />
        <input type="number" step="0.01" value={form.weightInFinal} onChange={(e) => setForm({ ...form, weightInFinal: e.target.value })} placeholder="Trọng số" className={inputClass} />
      </div>
      <div className="flex gap-2">
        <Button type="button" size="sm" variant="secondary" onClick={onCancel}>
          Hủy
        </Button>
        <Button type="submit" size="sm" variant="primary" disabled={submitting}>
          {submitting ? "Đang lưu..." : "Tạo kỳ điểm"}
        </Button>
      </div>
    </form>
  );
}

function CreateComponentForm({ gradePeriodId, onDone, onCancel }: { gradePeriodId: number; onDone: (c: GradeComponentResponse) => void; onCancel: () => void }) {
  const [form, setForm] = useState({ code: "OTHER", name: "", maxScore: "10" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name.trim()) {
      setError("Vui lòng điền tên đầu điểm.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: CreateGradeComponentRequest = {
        code: form.code,
        name: form.name.trim(),
        maxScore: form.maxScore ? Number(form.maxScore) : undefined
      };
      const created = await addGradeComponent(gradePeriodId, request);
      onDone(created);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Tạo đầu điểm thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-white border border-slate-200 rounded-lg p-3 space-y-2">
      {error && <div className="text-[11px] text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{error}</div>}
      <div className="grid grid-cols-3 gap-2">
        <select value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} className={inputClass}>
          <option value="SPEAKING">Speaking</option>
          <option value="WRITING">Writing</option>
          <option value="LISTENING">Listening</option>
          <option value="READING">Reading</option>
          <option value="GRAMMAR">Grammar</option>
          <option value="PROJECT">Project</option>
          <option value="OTHER">Khác</option>
        </select>
        <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Tên đầu điểm" className={inputClass} />
        <input type="number" step="0.01" value={form.maxScore} onChange={(e) => setForm({ ...form, maxScore: e.target.value })} placeholder="Điểm tối đa" className={inputClass} />
      </div>
      <div className="flex gap-2">
        <Button type="button" size="sm" variant="secondary" onClick={onCancel}>
          Hủy
        </Button>
        <Button type="submit" size="sm" variant="primary" disabled={submitting}>
          {submitting ? "Đang lưu..." : "Tạo đầu điểm"}
        </Button>
      </div>
    </form>
  );
}
