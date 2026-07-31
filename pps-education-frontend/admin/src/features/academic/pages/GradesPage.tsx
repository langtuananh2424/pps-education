import React, { useEffect, useState } from "react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { UserRole } from "@/types";
import {
  ClassEnrollmentResponse,
  GradeAppealResponse,
  GradeComponentResponse,
  GradePeriodResponse,
  acceptGradeAppeal,
  getClass,
  listClassEnrollments,
  listClassTeachers,
  listGradeComponents,
  listGradePeriods,
  listPendingGradeAppeals,
  listUnpublishedGrades
} from "../api";
import { useEligibleClasses } from "../hooks/useEligibleClasses";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import ClassGradeSheetPanel from "../components/ClassGradeSheetPanel";
import GradeSheetTable from "../components/GradeSheetTable";
import GradePublishDetail from "../components/GradePublishDetail";
import GradePublishGroupList, { GradePublishGroup } from "../components/GradePublishGroupList";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import Select from "@/components/ui/Select";

const inputClass = "bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none";

export default function GradesPage() {
  const { hasPermission, currentUser, selectedClassId } = useApp();
  const canManage = hasPermission("academic.grade.manage");
  // Hàng chờ duyệt (UC-20) chỉ có ý nghĩa với Quản lý điểm trường — API tự scope theo site được gán,
  // ẩn hẳn khối này với tài khoản khác để đỡ hiện 1 panel rỗng không liên quan.
  const isSiteManager = currentUser?.roleCodes?.includes(UserRole.SITE_MANAGER) ?? false;
  // Danh sách lớp + lớp đang chọn giờ dùng chung toàn cục (chọn 1 lần ở Header, cạnh Điểm trường)
  // thay vì mỗi trang tự có dropdown/state riêng — xem useEligibleClasses cho đúng quy tắc phân quyền
  // theo lớp (UC-19 Precondition: GV chỉ thấy lớp mình dạy; SITE_MANAGER thấy hết lớp thuộc site).
  const { classes } = useEligibleClasses();
  const [enrollments, setEnrollments] = useState<ClassEnrollmentResponse[]>([]);
  const [gradePeriods, setGradePeriods] = useState<GradePeriodResponse[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [gradeComponents, setGradeComponents] = useState<GradeComponentResponse[]>([]);
  const [error, setError] = useState<string | null>(null);

  const [pendingGroups, setPendingGroups] = useState<GradePublishGroup[]>([]);
  const [loadingPendingGroups, setLoadingPendingGroups] = useState(true);
  const [selectedPendingClassId, setSelectedPendingClassId] = useState<number | null>(null);

  const [pendingAppeals, setPendingAppeals] = useState<GradeAppealResponse[]>([]);
  const [loadingAppeals, setLoadingAppeals] = useState(true);
  const [acceptingAppealId, setAcceptingAppealId] = useState<number | null>(null);
  const { message: toastMessage, showToast } = useToast();

  const selectedClass = classes.find((c) => c.id === selectedClassId) ?? null;
  const selectedPendingGroup = pendingGroups.find((g) => g.classId === selectedPendingClassId) ?? null;

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

  /** UC-62: hàng chờ phúc khảo của (các) lớp Giáo viên đang phụ trách — không có ý nghĩa với Quản lý điểm trường (không dạy lớp nào). */
  const loadPendingAppeals = () => {
    setLoadingAppeals(true);
    listPendingGradeAppeals()
      .then(setPendingAppeals)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được hàng chờ phúc khảo."))
      .finally(() => setLoadingAppeals(false));
  };

  useEffect(() => {
    if (!isSiteManager) loadPendingAppeals();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isSiteManager]);

  const handleAcceptAppeal = async (id: number) => {
    setAcceptingAppealId(id);
    setError(null);
    try {
      await acceptGradeAppeal(id);
      loadPendingAppeals();
      showToast("Đã tiếp nhận yêu cầu phúc khảo thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Tiếp nhận yêu cầu phúc khảo thất bại.");
    } finally {
      setAcceptingAppealId(null);
    }
  };

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
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Sổ điểm hệ thống</h1>
        <p className="text-xs text-slate-500 mt-1">
          Giáo viên nhập điểm theo lớp, sửa/xoá tự do khi còn Nháp. Quản lý điểm trường công bố dự kiến cho Phụ huynh xem — sau đó chỉ sửa
          được qua yêu cầu phúc khảo trong hạn quy định, hết hạn tự khoá Chính thức.
        </p>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {isSiteManager && (
        <div className="space-y-4">
          <h2 className="text-xs font-bold text-slate-400 uppercase tracking-wider font-display border-b border-slate-200 pb-2">
            Công bố điểm học phần
          </h2>
          <GradePublishDetail
            classId={selectedPendingClassId}
            classLabel={selectedPendingGroup ? `${selectedPendingGroup.classCode} — ${selectedPendingGroup.className}` : null}
            teacherName={selectedPendingGroup?.teacherName ?? null}
            onPublished={() => {
              setSelectedPendingClassId(null);
              loadPendingGroups();
              showToast("Đã công bố điểm thành công!");
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
            <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 grid grid-cols-1 sm:grid-cols-2 gap-2 items-center">
              <span className="text-xs font-bold text-slate-700">
                {selectedClass ? `Lớp: ${selectedClass.classCode} — ${selectedClass.name}` : "Chưa chọn lớp — chọn ở góc trên bên phải (Header)"}
              </span>
              <Select
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
              </Select>
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
              <p className="text-xs text-slate-400 italic p-6 text-center">Chọn lớp (Header) → kỳ điểm để xem lại điểm (Nháp + Đã công bố).</p>
            )}
          </Card>
        </div>
      )}

      {!isSiteManager && (pendingAppeals.length > 0 || loadingAppeals) && (
        <Card padded={false} className="overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100 bg-slate-50">
            <span className="text-xs font-bold text-slate-700 font-display">Hàng chờ phúc khảo</span>
            <p className="text-[10px] text-slate-400 mt-0.5">
              Học sinh/Phụ huynh gửi yêu cầu — tiếp nhận rồi mới sửa được điểm của đúng học sinh đó trong bảng nhập điểm bên dưới.
            </p>
          </div>
          {loadingAppeals ? (
            <p className="text-xs text-slate-500 p-4">Đang tải...</p>
          ) : (
            <div className="divide-y divide-slate-100">
              {pendingAppeals.map((a) => (
                <div key={a.id} className="px-4 py-3 flex items-center justify-between gap-3 flex-wrap">
                  <div className="min-w-0">
                    <span className="text-xs font-bold text-slate-800 block">{a.studentFullName}</span>
                    <span className="text-[10px] text-slate-400 block mt-0.5">
                      {a.reason || "(không ghi lý do)"} · {new Date(a.createdAt).toLocaleString("vi-VN")}
                    </span>
                  </div>
                  <Button size="sm" variant="primary" disabled={acceptingAppealId === a.id} onClick={() => handleAcceptAppeal(a.id)}>
                    {acceptingAppealId === a.id ? "Đang tiếp nhận..." : "Tiếp nhận"}
                  </Button>
                </div>
              ))}
            </div>
          )}
        </Card>
      )}

      {!isSiteManager && (
        <Card padded={false} className="overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100 bg-slate-50">
            <span className="text-xs font-bold text-slate-700 font-display">
              {selectedClass ? `Lớp: ${selectedClass.classCode} — ${selectedClass.name}` : "Chưa chọn lớp — chọn ở góc trên bên phải (Header)"}
            </span>
          </div>
          <div className="p-5">
            {selectedClassId && selectedClass ? (
              <ClassGradeSheetPanel classId={selectedClassId} curriculumId={selectedClass.curriculumId} />
            ) : (
              <p className="text-xs text-slate-400 italic p-6 text-center">Chọn lớp ở Header để bắt đầu nhập điểm.</p>
            )}
          </div>
        </Card>
      )}

      <Toast message={toastMessage} />
    </div>
  );
}
