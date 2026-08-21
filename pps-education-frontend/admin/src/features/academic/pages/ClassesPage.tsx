import React, { useEffect, useState } from "react";
import { ArrowRightLeft, CalendarRange, GraduationCap } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { UserRole } from "@/types";
import { ClassResponse, listClassTeachers, listClasses } from "../api";
import { SiteResponse, listSites } from "@/features/facility/api";
import ClassListPanel from "../components/ClassListPanel";
import ClassDetailPanel from "../components/ClassDetailPanel";
import ClassFormModal from "../components/ClassFormModal";
import AcademicTermManagerModal from "../components/AcademicTermManagerModal";
import AcademicYearManagerModal from "../components/AcademicYearManagerModal";
import ClassPromotionModal from "../components/ClassPromotionModal";
import Button from "@/components/ui/Button";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";

/** Các vai trò "quản trị lớp" thật sự — vẫn dùng màn xếp/tạo lớp đầy đủ (search+list+Thêm lớp) ở đây. */
const CLASS_ADMIN_ROLES: UserRole[] = [UserRole.HEAD_ACADEMIC, UserRole.SYS_ADMIN, UserRole.SITE_MANAGER];

export default function ClassesPage() {
  const { t } = useTranslation("academic-classes");
  const { selectedCampusId, hasPermission, currentUser, selectedClassId: globalClassId } = useApp();
  const canManage = hasPermission("academic.class.manage");
  // GV thuần (không kiêm vai trò quản trị nào ở trên) không cần màn xếp/tạo lớp — chỉ xem/thao tác
  // đúng lớp đang chọn ở Header (giống 6 màn Sổ điểm/Điểm danh/Giao đề/Nhận xét/Kho bài giảng khác),
  // không cần lặp lại việc chọn lớp lần nữa ở đây.
  const isClassAdmin = (currentUser?.roleCodes ?? []).some((r) => CLASS_ADMIN_ROLES.includes(r as UserRole));
  const [classes, setClasses] = useState<ClassResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [termsOpen, setTermsOpen] = useState(false);
  const [academicYearsOpen, setAcademicYearsOpen] = useState(false);
  const [promotionOpen, setPromotionOpen] = useState(false);
  const [academicYearFilter, setAcademicYearFilter] = useState("");
  const [sites, setSites] = useState<SiteResponse[]>([]);
  const { message: toastMessage, showToast } = useToast();
  const effectiveSelectedId = isClassAdmin ? selectedId : globalClassId;
  const selectedSite = selectedCampusId !== "ALL" ? sites.find((s) => s.id === Number(selectedCampusId)) ?? null : null;

  useEffect(() => {
    listSites().then(setSites).catch(() => undefined);
  }, []);

  /**
   * UC-18 Precondition: GV chỉ xếp/xem lớp mình được phân công dạy (class_teachers),
   * KHÔNG phải mọi lớp ở site mình được đăng ký dạy (site_teachers). GET /api/classes
   * hiện chỉ lọc theo site_teachers (coarse hơn) nên phải lọc thêm ở FE cho tài khoản
   * không có academic.class.manage — cùng gốc rễ với fix ở GradesPage (Sổ điểm).
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
          canManage || !currentUser
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

  useEffect(load, [selectedCampusId, academicYearFilter, canManage, currentUser]);

  const selectedClass = classes.find((c) => c.id === effectiveSelectedId) ?? null;

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4 flex items-start justify-between gap-3 flex-wrap">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("classesPage.title")}</h1>
          <p className="text-xs text-slate-500 mt-1">{t("classesPage.description")}</p>
        </div>
        {canManage && (
          <div className="flex items-center gap-2 flex-wrap">
            <Button
              size="sm"
              variant="secondary"
              onClick={() => setTermsOpen(true)}
              disabled={!selectedSite}
              title={selectedSite ? undefined : t("classesPage.manageTermsDisabledTitle")}
            >
              <CalendarRange className="w-3.5 h-3.5" />
              {t("classesPage.manageTermsButton")}
            </Button>
            <Button size="sm" variant="secondary" onClick={() => setAcademicYearsOpen(true)}>
              <GraduationCap className="w-3.5 h-3.5" />
              {t("classesPage.manageAcademicYearsButton")}
            </Button>
            <Button size="sm" variant="secondary" onClick={() => setPromotionOpen(true)}>
              <ArrowRightLeft className="w-3.5 h-3.5" />
              {t("classesPage.promoteButton")}
            </Button>
          </div>
        )}
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className={`grid grid-cols-1 ${isClassAdmin ? "lg:grid-cols-5" : ""} gap-6`}>
        {isClassAdmin && (
          <ClassListPanel
            classes={classes.filter((c) => !query.trim() || c.name.toLowerCase().includes(query.toLowerCase()) || c.classCode.toLowerCase().includes(query.toLowerCase()))}
            loading={loading}
            selectedId={selectedId}
            onSelect={setSelectedId}
            onCreate={() => setCreateOpen(true)}
            query={query}
            onQueryChange={setQuery}
            canManage={canManage}
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
              <p className="text-xs text-slate-400 mt-1">
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

      {termsOpen && selectedSite && <AcademicTermManagerModal siteId={selectedSite.id} siteName={selectedSite.name} onClose={() => setTermsOpen(false)} />}

      {academicYearsOpen && <AcademicYearManagerModal onClose={() => setAcademicYearsOpen(false)} />}

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
