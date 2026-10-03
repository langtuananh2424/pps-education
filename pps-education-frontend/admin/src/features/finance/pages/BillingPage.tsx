import React, { useState } from "react";
import { useTranslation } from "react-i18next";
import Tabs from "@/components/ui/Tabs";
import InvoicesTab from "../components/InvoicesTab";
import TuitionPlansTab from "../components/TuitionPlansTab";
import ScholarshipsTab from "../components/ScholarshipsTab";

type TabId = "invoices" | "plans" | "scholarships";

/**
 * Thu phí & hóa đơn (UC-30 phía Kế toán): tra cứu hóa đơn + ghi nhận thanh toán thủ công, gói học phí gán
 * cho lớp, học bổng/miễn giảm. Thay cho bản mock cũ (đã ẩn bằng UnderDevelopment từ 2026-09).
 */
export default function BillingPage() {
  const { t } = useTranslation("finance");
  const [activeTab, setActiveTab] = useState<TabId>("invoices");

  const tabs: { id: TabId; label: string }[] = [
    { id: "invoices", label: t("billing.tabInvoices") },
    { id: "plans", label: t("billing.tabPlans") },
    { id: "scholarships", label: t("billing.tabScholarships") }
  ];

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("billing.title")}</h1>
        <p className="text-sm text-slate-500 mt-1">{t("billing.description")}</p>
      </div>
      <Tabs items={tabs} activeId={activeTab} onChange={(id) => setActiveTab(id as TabId)} />
      {activeTab === "invoices" && <InvoicesTab />}
      {activeTab === "plans" && <TuitionPlansTab />}
      {activeTab === "scholarships" && <ScholarshipsTab />}
    </div>
  );
}
