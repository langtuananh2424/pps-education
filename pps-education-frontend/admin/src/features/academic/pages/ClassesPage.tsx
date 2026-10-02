import React, { useEffect, useState } from "react";
import { ArrowRightLeft, GraduationCap } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { ClassResponse, listClassTeachers, listClasses } from "../api";
import ClassListPanel from "../components/ClassListPanel";
import ClassDetailPanel from "../components/ClassDetailPanel";
import ClassFormModal from "../components/ClassFormModal";
import ClassPromotionModal from "../components/ClassPromotionModal";
import Button from "@/components/ui/Button";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";

export default function ClassesPage() {
  const { t } = useTranslation("academic-classes");
  const { selectedCampusId, hasPermission, currentUser, selectedClassId: globalClassId } = useApp();
  // V202 — nút theo quyền, phạm vi theo roles.data_scope (không đoán theo tên vai trò nữa).
  const canCreateClass = hasPermission("academic.class.create");
  const canPromote = hasPermission("academic.class.promote");
  // Phạm vi "Chỉ lớp mình dạy" (CLASS): chỉ thấy lớp đứng tên giáo viên và thao tác đúng lớp đang chọn ở
  // Header, không cần danh sách lớp bên trái. Phạm vi rộng hơn (điểm trường/tất cả) hoặc có quyền "Xem mọi
  // lớp" thì dùng màn danh sách đầy đủ — backend đã tự giới hạn theo điểm trường (ClassService.resolveAllowedSiteIds).
  const isClassScoped = currentUser?.dataScope === "CLASS" && !hasPermission("academic.class.view-all");
  const canSeeAllClasses = !isClassScoped;
  const isClassAdmin = !isClassScoped;
  const [classes, setClasses] = useState<ClassResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [promotionOpen, setPromotionOpen] = useState(false);
  const [academicYearFilter, setAcademicYearFilter] = useState("");
  const { message: toastMessage, showToast } = useToast();
  const effectiveSelectedId = isClassAdmin ? selectedId : globalClassId;

  /**
   * UC-18 Precondition: GV chỉ xếp/xem lớp mình được phân công dạy (class_teachers),
   * KHÔNG phải mọi lớp ở site mình được đăng ký dạy (site_teachers). GET /api/classes
   * hiện chỉ lọc theo site_teachers (coarse hơn) nên phải lọc thêm ở FE cho tài khoản
   * có phạm vi "Chỉ lớp mình dạy" (V202) — cùng gốc rễ với fix ở GradesPage (Sổ điểm).
   */
  const load = () => {
    setLoading(true);
    setError(null);
    listClasses({
      siteId: selectedCampusId !== "ALL" ? Number(selectedCampusId) : undefined,
      academicYearId: academicYearFilter ? Number(academicYearFilter) : undefined
    })
      .then(async (res) => {
        const filtered =
          canSeeAllClasses || !currentUser
            ? res
            : await Promise.all(res.map((c) => listClassTeachers(c.id).catch(() => []))).then((teacherLists) =>
                res.filter((_, i) => teacherLists[i].some((t) => t.teacherUserId === currentUser.id && !t.assignedTo))
              );
        setClasses(filtered);
        if (isClassAdmin && selectedId == null && filtered.length > 0) setSelectedId(filtered[0].id);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("classesPage.loadError")))
      .finally(() => setLoading(false));
  };

  useEffect(load, [selectedCampusId, academicYearFilter, canSeeAllClasses, currentUser]);

  const selectedClass = classes.find((c) => c.id === effectiveSelectedId) ?? null;

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4 flex items-start justify-between gap-3 flex-wrap">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("classesPage.title")}</h1>
          <p className="text-sm text-slate-500 mt-1">{t("classesPage.description")}</p>
        </div>
        {canPromote && (
          <div className="flex items-center gap-2 flex-wrap">
            <Button size="sm" variant="secondary" onClick={() => setPromotionOpen(true)}>
              <ArrowRightLeft className="w-3.5 h-3.5" />
              {t("classesPage.promoteButton")}
            </Button>
          </div>
        )}
      </div>

      {error && <div className="text-sm text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className={`grid grid-cols-1 ${isClassAdmin ? "lg:grid-cols-5" : ""} gap-6`}>
        {isClassAdmin && (
          <ClassListPanel
            classes={classes
              .filter((c) => !query.trim() || c.name.toLowerCase().includes(query.toLowerCase()) || c.classCode.toLowerCase().includes(query.toLowerCase()))
              // Lớp "Đã hủy" đẩy xuống cuối danh sách, không xen giữa các lớp còn hoạt động.
              .sort((a, b) => Number(a.status === "CANCELLED") - Number(b.status === "CANCELLED"))}
            loading={loading}
            selectedId={selectedId}
            onSelect={setSelectedId}
            onCreate={() => setCreateOpen(true)}
            query={query}
            onQueryChange={setQuery}
            canManage={canCreateClass}
            academicYearFilter={academicYearFilter}
            onAcademicYearFilterChange={setAcademicYearFilter}
          />
        )}

        {selectedClass ? (
          <ClassDetailPanel schoolClass={selectedClass} onChanged={load} />
        ) : (
          <div className="lg:col-span-3 bg-white rounded-xl border border-slate-200 shadow-soft flex flex-col items-center justify-center p-12 text-center text-slate-400 space-y-3">
            <GraduationCap className="w-12 h-12 text-slate-300" />
            <div>
              <h3 className="text-sm font-bold text-slate-700">{t("classesPage.emptyTitle")}</h3>
              <p className="text-sm text-slate-400 mt-1">
                {isClassAdmin ? t("classesPage.emptyDescriptionAdmin") : t("classesPage.emptyDescriptionOther")}
              </p>
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
            showToast(t("classesPage.createSuccess"));
          }}
        />
      )}

      {promotionOpen && (
        <ClassPromotionModal
          classes={classes}
          onClose={() => setPromotionOpen(false)}
          onPromoted={(created) => {
            setPromotionOpen(false);
            setSelectedId(created.id);
            load();
            showToast(t("classesPage.promoteSuccess"));
          }}
        />
      )}

      <Toast message={toastMessage} />
    </div>
  );
}
