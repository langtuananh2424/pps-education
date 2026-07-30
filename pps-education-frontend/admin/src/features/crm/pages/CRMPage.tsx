import React, { useState } from "react";
import { Lead, Student } from "@/types";
import { mockLeads, mockStudents } from "@/data/mockData";
import Tabs from "@/components/ui/Tabs";
import UnderDevelopment from "@/components/ui/UnderDevelopment";
// Cả LeadsPanel (UC-33) và ExcelImportPanel (UC-35) tạm ẩn theo yêu cầu người dùng (2026-07-23)
// — sẽ phát triển tiếp ở giai đoạn sau, không xoá component, chỉ chưa hiển thị.
// import LeadsPanel from "../components/LeadsPanel";
// import ExcelImportPanel from "../components/ExcelImportPanel";

export default function CRMPage() {
  const [activeSubTab, setActiveSubTab] = useState<"leads" | "import">("leads");
  const [leads, setLeads] = useState<Lead[]>(mockLeads);
  const [students, setStudents] = useState<Student[]>(mockStudents);

  const handleAddCallLog = (leadId: string, text: string, status: Lead["status"]) => {
    const timeString = new Date().toISOString().replace("T", " ").substring(0, 16);
    setLeads((prev) =>
      prev.map((l) => (l.id === leadId ? { ...l, history: [...l.history, `${timeString}: ${text}`], status } : l))
    );
  };

  const handleConvertToStudent = (leadId: string) => {
    const lead = leads.find((l) => l.id === leadId);
    if (!lead) return;

    setLeads((prev) => prev.map((l) => (l.id === leadId ? { ...l, status: "WON" as const } : l)));

    const newStudent: Student = {
      id: `STU-00${students.length + 1}`,
      fullName: lead.fullName,
      birthDate: "2018-01-01",
      avatarUrl: "https://images.unsplash.com/photo-1544717305-2782549b5136?auto=format&fit=crop&w=150&h=150&q=80",
      phone: lead.phone,
      parentName: "Phụ huynh tự động",
      parentPhone: lead.phone,
      campusId: "CAMP-01",
      classIds: ["CLS-01"],
      status: "STUDYING",
      history: [`2026-07-10: Hồ sơ được tự động đồng bộ hóa từ Lead CRM (${lead.id}) theo luồng nghiệp vụ UC-34.`]
    };

    setStudents((prev) => [newStudent, ...prev]);
    alert(
      `Chúc mừng! Đã thực hiện transaction chuyển đổi thành công:\n- Lead '${lead.fullName}' đổi trạng thái sang WON.\n- Đã tự động tạo mới hồ sơ học sinh '${lead.fullName}' trong Phân hệ Quản lý Học sinh.`
    );
  };

  const handleBatchImport = () => {
    const batchNewStudents: Student[] = [
      {
        id: "STU-101",
        fullName: "Phạm Minh Hoàng",
        birthDate: "2017-04-12",
        phone: "0399123456",
        parentName: "Phạm Xuân Tấn",
        parentPhone: "0915123456",
        campusId: "CAMP-03",
        classIds: ["CLS-02"],
        status: "STUDYING",
        history: ["Import hàng loạt bằng file Excel từ TH Nghĩa Tân."]
      },
      {
        id: "STU-102",
        fullName: "Hoàng Ngọc Lan",
        birthDate: "2017-08-22",
        phone: "0399234567",
        parentName: "Hoàng Lê Minh",
        parentPhone: "0915234567",
        campusId: "CAMP-03",
        classIds: ["CLS-02"],
        status: "STUDYING",
        history: ["Import hàng loạt bằng file Excel từ TH Nghĩa Tân."]
      }
    ];
    setStudents((prev) => [...batchNewStudents, ...prev]);
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 border-b border-slate-200 pb-4">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Tuyển Sinh & Quản Lý Khách Hàng (CRM)</h1>
          <p className="text-xs text-slate-500 mt-1">
            Ghi nhận thông tin Lead đa kênh, chăm sóc khách hàng và đồng bộ tự động hóa thông tin nhập học chính thức.
          </p>
        </div>

        <Tabs
          items={[
            { id: "leads", label: "Data Khách hàng & Leads" },
            { id: "import", label: "Nhập Học Theo Lô - Excel" }
          ]}
          activeId={activeSubTab}
          onChange={(id) => setActiveSubTab(id as "leads" | "import")}
          className="self-start"
        />
      </div>

      {activeSubTab === "leads" ? (
        <UnderDevelopment title="Data Khách hàng & Leads" />
      ) : (
        <UnderDevelopment title="Nhập Học Theo Lô - Excel" />
      )}
    </div>
  );
}
