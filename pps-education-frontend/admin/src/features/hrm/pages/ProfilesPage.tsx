import React, { useEffect, useState } from "react";
import { Users } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import ImportExcelButton from "@/components/ui/ImportExcelButton";
import { EmployeeResponse, exportEmployeeAccounts, importEmployees, listEmployees } from "../api";
import EmployeeListPanel from "../components/EmployeeListPanel";
import EmployeeDetailPanel from "../components/EmployeeDetailPanel";
import EmployeeFormModal from "../components/EmployeeFormModal";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";

export default function ProfilesPage() {
  const { t } = useTranslation("hrm-employees");
  const [employees, setEmployees] = useState<EmployeeResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const { message: toastMessage, showToast } = useToast();

  const load = () => {
    setLoading(true);
    setError(null);
    listEmployees(query)
      .then((res) => {
        setEmployees(res);
        if (selectedId == null && res.length > 0) setSelectedId(res[0].id);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("profilesPage.loadError")))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const selectedEmployee = employees.find((e) => e.id === selectedId) ?? null;

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4 flex flex-col sm:flex-row sm:items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("profilesPage.title")}</h1>
          <p className="text-xs text-slate-500 mt-1">{t("profilesPage.description")}</p>
        </div>
        <ImportExcelButton
          title={t("profilesPage.importTitle")}
          templateFileName={t("profilesPage.importTemplateFileName")}
          templateHeaders={t("profilesPage.importHeaders", { returnObjects: true }) as string[]}
          templateSampleRow={t("profilesPage.importSampleRow", { returnObjects: true }) as string[]}
          uploadFn={importEmployees}
          exportAccounts={exportEmployeeAccounts}
          accountsExportFileName={t("profilesPage.importAccountsFileName")}
          onImported={load}
        />
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
              <h3 className="text-sm font-bold text-slate-700">{t("profilesPage.emptyTitle")}</h3>
              <p className="text-xs text-slate-400 mt-1">{t("profilesPage.emptyDescription")}</p>
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
            showToast(t("profilesPage.createdToast"));
          }}
        />
      )}

      <Toast message={toastMessage} />
    </div>
  );
}
